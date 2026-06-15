package com.myworkflow.agent.backend.providersecret;

import com.myworkflow.agent.backend.audit.AuditService;
import com.myworkflow.agent.backend.providersecret.ProviderCredentialRepository.ProviderCredentialMetadata;
import com.myworkflow.agent.backend.workspace.WorkspaceRecord;
import com.myworkflow.agent.backend.workspace.WorkspaceRole;
import com.myworkflow.agent.backend.workspace.WorkspaceService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("jdbc")
public class ProviderCredentialService {

  private static final String PROVIDER_CREDENTIAL_ID_PREFIX = "cred_";
  private static final Pattern SAFE_CREDENTIAL_REF_PATTERN =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
  private static final Pattern SAFE_ENV_NAME_PATTERN =
      Pattern.compile("[A-Z_][A-Z0-9_]{0,127}");
  private static final Pattern SECRET_REFERENCE_PATTERN =
      Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*://\\S{1,255}");
  private static final Set<String> SUPPORTED_SECRET_REFERENCE_SCHEMES =
      Set.of("env", "secret", "keychain", "file");
  private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
      "fake",
      "deepseek-fixture",
      "deepseek-real",
      "claude-code-fixture",
      "mimo-vllm-fixture",
      "mimo-real",
      "weak-relations-fixture",
      "timeout-fixture",
      "invalid-content-fixture"
  );

  private final WorkspaceService workspaceService;
  private final ProviderCredentialRepository repository;
  private final AuditService auditService;

  public ProviderCredentialService(
      WorkspaceService workspaceService,
      ProviderCredentialRepository repository,
      AuditService auditService
  ) {
    this.workspaceService = workspaceService;
    this.repository = repository;
    this.auditService = auditService;
  }

  public Optional<ProviderCredentialMetadata> resolveForWorkspace(String workspaceId, String credentialRef) {
    String normalizedRef = normalizeCredentialRef(credentialRef);
    WorkspaceRecord workspace = workspaceService.requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_EDITOR);
    return repository.findActiveByScope(workspace.teamId(), workspace.workspaceId(), normalizedRef);
  }

  public Optional<ProviderCredentialRuntimeDescriptor> resolveRuntimeDescriptorForWorkspace(
      String workspaceId,
      String credentialRef
  ) {
    return resolveForWorkspace(workspaceId, credentialRef)
        .map(ProviderCredentialService::toRuntimeDescriptor);
  }

  public ProviderCredentialPublicMetadata upsertWorkspaceCredential(
      String workspaceId,
      String credentialRef,
      String provider,
      String model,
      String baseUrl,
      String apiKeyEnvName
  ) {
    String normalizedRef = normalizeCredentialRef(credentialRef);
    WorkspaceRecord workspace = workspaceService.requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
    String normalizedProvider = requireSupportedProvider(provider);
    String normalizedEnvName = requireEnvName(apiKeyEnvName);
    ProviderCredentialMetadata saved = repository.save(new ProviderCredentialMetadata(
        providerCredentialId(workspace.workspaceId(), normalizedRef),
        normalizedRef,
        workspace.teamId(),
        workspace.workspaceId(),
        normalizedProvider,
        blankToNull(model),
        blankToNull(baseUrl),
        "env://" + normalizedEnvName,
        "ACTIVE"
    ));
    auditService.record(
        workspace.workspaceId(),
        null,
        "PROVIDER_CREDENTIAL_UPSERTED",
        "Provider credential metadata upserted: credentialRef=%s provider=%s scope=WORKSPACE"
            .formatted(saved.credentialRef(), saved.provider())
    );
    return toPublicMetadata(saved);
  }

  public List<ProviderCredentialPublicMetadata> listWorkspaceCredentials(String workspaceId) {
    WorkspaceRecord workspace = workspaceService.requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
    return repository.listByWorkspaceScope(workspace.teamId(), workspace.workspaceId()).stream()
        .map(ProviderCredentialService::toPublicMetadata)
        .toList();
  }

  private static ProviderCredentialRuntimeDescriptor toRuntimeDescriptor(
      ProviderCredentialMetadata credential
  ) {
    return new ProviderCredentialRuntimeDescriptor(
        credential.credentialRef(),
        requireSupportedProvider(credential.provider()),
        blankToNull(credential.model()),
        blankToNull(credential.baseUrl()),
        requireSecretReference(credential.apiKeySecretRef())
    );
  }

  private static ProviderCredentialPublicMetadata toPublicMetadata(
      ProviderCredentialMetadata credential
  ) {
    return new ProviderCredentialPublicMetadata(
        credential.credentialRef(),
        credential.workspaceId(),
        credential.workspaceId() == null ? "TEAM" : "WORKSPACE",
        credential.provider(),
        blankToNull(credential.model()),
        blankToNull(credential.baseUrl()),
        credential.status()
    );
  }

  private static String normalizeCredentialRef(String credentialRef) {
    if (credentialRef == null || credentialRef.isBlank()) {
      throw new IllegalArgumentException("Provider credential reference is required");
    }
    String normalizedRef = credentialRef.trim();
    if (!SAFE_CREDENTIAL_REF_PATTERN.matcher(normalizedRef).matches()) {
      throw new IllegalArgumentException("Invalid provider credential reference");
    }
    return normalizedRef;
  }

  private static String providerCredentialId(String workspaceId, String credentialRef) {
    String source = workspaceId + "\n" + credentialRef;
    return PROVIDER_CREDENTIAL_ID_PREFIX + UUID.nameUUIDFromBytes(
        source.getBytes(StandardCharsets.UTF_8)
    ).toString().replace("-", "");
  }

  private static String requireSupportedProvider(String provider) {
    String normalizedProvider = blankToNull(provider);
    if (normalizedProvider == null || !SUPPORTED_PROVIDERS.contains(normalizedProvider)) {
      throw new IllegalArgumentException("Unsupported provider credential provider");
    }
    return normalizedProvider;
  }

  private static String requireEnvName(String envName) {
    String normalizedEnvName = blankToNull(envName);
    if (normalizedEnvName == null || !SAFE_ENV_NAME_PATTERN.matcher(normalizedEnvName).matches()) {
      throw new IllegalArgumentException("apiKeyEnvName must be an environment variable name");
    }
    return normalizedEnvName;
  }

  private static String requireSecretReference(String secretReference) {
    String normalizedReference = blankToNull(secretReference);
    if (normalizedReference == null || !SECRET_REFERENCE_PATTERN.matcher(normalizedReference).matches()) {
      throw new IllegalArgumentException("apiKeySecretRef must be a secret reference");
    }
    int schemeEnd = normalizedReference.indexOf("://");
    String scheme = normalizedReference.substring(0, schemeEnd);
    if (!SUPPORTED_SECRET_REFERENCE_SCHEMES.contains(scheme)) {
      throw new IllegalArgumentException("apiKeySecretRef must use a supported secret reference scheme");
    }
    return normalizedReference;
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  public record ProviderCredentialRuntimeDescriptor(
      String credentialRef,
      String provider,
      String model,
      String baseUrl,
      String apiKeySecretRef
  ) {
  }

  public record ProviderCredentialPublicMetadata(
      String credentialRef,
      String workspaceId,
      String scope,
      String provider,
      String model,
      String baseUrl,
      String status
  ) {
  }
}
