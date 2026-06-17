package com.myworkflow.agent.backend.providersecret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myworkflow.agent.backend.audit.AuditService;
import com.myworkflow.agent.backend.audit.InMemoryAuditRepository;
import com.myworkflow.agent.backend.config.BackendProperties;
import com.myworkflow.agent.backend.identity.BackendPrincipal;
import com.myworkflow.agent.backend.identity.PrincipalProvider;
import com.myworkflow.agent.backend.providersecret.ProviderCredentialRepository.ProviderCredentialMetadata;
import com.myworkflow.agent.backend.workspace.InMemoryWorkspaceRepository;
import com.myworkflow.agent.backend.workspace.WorkspaceForbiddenException;
import com.myworkflow.agent.backend.workspace.WorkspaceRecord;
import com.myworkflow.agent.backend.workspace.WorkspaceRepository;
import com.myworkflow.agent.backend.workspace.WorkspaceRole;
import com.myworkflow.agent.backend.workspace.WorkspaceService;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderCredentialServiceTest {

  @TempDir
  private Path dataRoot;

  @Test
  void resolvesActiveCredentialMetadataForWorkspaceEditorUsingWorkspaceScope() {
    MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
        new BackendPrincipal("owner-a", "team-a", "Owner A")
    );
    WorkspaceService workspaceService = workspaceServiceFor(principalProvider, new InMemoryWorkspaceRepository());
    WorkspaceRecord workspace = workspaceService.registerWorkspace("Team Knowledge", "main");
    workspaceService.grantMember(
        workspace.workspaceId(),
        "editor-a",
        workspace.teamId(),
        WorkspaceRole.WORKSPACE_EDITOR
    );
    CapturingProviderCredentialRepository credentialRepository = new CapturingProviderCredentialRepository();
    credentialRepository.save(new ProviderCredentialMetadata(
        "credential-team",
        "team-mimo",
        workspace.teamId(),
        null,
        "mimo-real",
        "mimo-v2.5",
        "https://token-plan-cn.xiaomimimo.com/v1",
        "secret://team-a/provider/mimo",
        "ACTIVE"
    ));
    credentialRepository.save(new ProviderCredentialMetadata(
        "credential-workspace",
        "workspace-mimo",
        workspace.teamId(),
        workspace.workspaceId(),
        "mimo-real",
        "mimo-v2.5",
        "https://token-plan-cn.xiaomimimo.com/v1",
        "secret://team-a/workspace/provider/mimo",
        "ACTIVE"
    ));
    ProviderCredentialService service = providerCredentialServiceFor(
        principalProvider,
        workspaceService,
        credentialRepository
    );

    principalProvider.setPrincipal(new BackendPrincipal("editor-a", workspace.teamId(), "Editor A"));

    assertThat(service.resolveForWorkspace(workspace.workspaceId(), " team-mimo "))
        .hasValueSatisfying(credential ->
            assertThat(credential.apiKeySecretRef()).isEqualTo("secret://team-a/provider/mimo"));
    assertThat(service.resolveForWorkspace(workspace.workspaceId(), "workspace-mimo"))
        .hasValueSatisfying(credential ->
            assertThat(credential.workspaceId()).isEqualTo(workspace.workspaceId()));
    assertThat(credentialRepository.lastLookupTeamId()).isEqualTo(workspace.teamId());
    assertThat(credentialRepository.lastLookupWorkspaceId()).isEqualTo(workspace.workspaceId());
  }

  @Test
  void resolvesSecretSafeRuntimeDescriptorForWorkspaceEditor() {
    MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
        new BackendPrincipal("owner-a", "team-a", "Owner A")
    );
    WorkspaceService workspaceService = workspaceServiceFor(principalProvider, new InMemoryWorkspaceRepository());
    WorkspaceRecord workspace = workspaceService.registerWorkspace("Team Knowledge", "main");
    workspaceService.grantMember(
        workspace.workspaceId(),
        "editor-a",
        workspace.teamId(),
        WorkspaceRole.WORKSPACE_EDITOR
    );
    CapturingProviderCredentialRepository credentialRepository = new CapturingProviderCredentialRepository();
    credentialRepository.save(new ProviderCredentialMetadata(
        "credential-workspace",
        "workspace-mimo",
        workspace.teamId(),
        workspace.workspaceId(),
        "mimo-real",
        "mimo-v2.5",
        "https://token-plan-cn.xiaomimimo.com/v1",
        "env://MIMO_API_KEY",
        "ACTIVE"
    ));
    ProviderCredentialService service = providerCredentialServiceFor(
        principalProvider,
        workspaceService,
        credentialRepository
    );

    principalProvider.setPrincipal(new BackendPrincipal("editor-a", workspace.teamId(), "Editor A"));

    assertThat(service.resolveRuntimeDescriptorForWorkspace(workspace.workspaceId(), "workspace-mimo"))
        .hasValueSatisfying(descriptor -> {
          assertThat(descriptor.credentialRef()).isEqualTo("workspace-mimo");
          assertThat(descriptor.provider()).isEqualTo("mimo-real");
          assertThat(descriptor.model()).isEqualTo("mimo-v2.5");
          assertThat(descriptor.baseUrl()).isEqualTo("https://token-plan-cn.xiaomimimo.com/v1");
          assertThat(descriptor.apiKeySecretRef()).isEqualTo("env://MIMO_API_KEY");
          assertThat(descriptor.toString())
              .doesNotContain("apiKey=", "token=", "authorization=", "plain-secret-value");
        });
    assertThat(credentialRepository.lastLookupTeamId()).isEqualTo(workspace.teamId());
    assertThat(credentialRepository.lastLookupWorkspaceId()).isEqualTo(workspace.workspaceId());
  }

  @Test
  void rejectsRuntimeDescriptorWithUnsupportedProviderOrPlainSecretRef() {
    MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
        new BackendPrincipal("owner-a", "team-a", "Owner A")
    );
    WorkspaceService workspaceService = workspaceServiceFor(principalProvider, new InMemoryWorkspaceRepository());
    WorkspaceRecord workspace = workspaceService.registerWorkspace("Team Knowledge", "main");
    CapturingProviderCredentialRepository credentialRepository = new CapturingProviderCredentialRepository();
    credentialRepository.save(new ProviderCredentialMetadata(
        "credential-unsupported",
        "unsupported-provider",
        workspace.teamId(),
        workspace.workspaceId(),
        "unknown-provider",
        "mimo-v2.5",
        "https://token-plan-cn.xiaomimimo.com/v1",
        "env://MIMO_API_KEY",
        "ACTIVE"
    ));
    credentialRepository.save(new ProviderCredentialMetadata(
        "credential-plain-secret",
        "plain-secret",
        workspace.teamId(),
        workspace.workspaceId(),
        "mimo-real",
        "mimo-v2.5",
        "https://token-plan-cn.xiaomimimo.com/v1",
        "plain-secret-value",
        "ACTIVE"
    ));
    ProviderCredentialService service = providerCredentialServiceFor(
        principalProvider,
        workspaceService,
        credentialRepository
    );

    assertThatThrownBy(() ->
        service.resolveRuntimeDescriptorForWorkspace(workspace.workspaceId(), "unsupported-provider"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported provider credential provider");
    assertThatThrownBy(() -> service.resolveRuntimeDescriptorForWorkspace(workspace.workspaceId(), "plain-secret"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("apiKeySecretRef must be a secret reference");
  }

  @Test
  void rejectsCredentialResolutionWithoutWorkspaceEditorRole() {
    MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
        new BackendPrincipal("owner-a", "team-a", "Owner A")
    );
    WorkspaceService workspaceService = workspaceServiceFor(principalProvider, new InMemoryWorkspaceRepository());
    WorkspaceRecord workspace = workspaceService.registerWorkspace("Team Knowledge", "main");
    workspaceService.grantMember(
        workspace.workspaceId(),
        "viewer-a",
        workspace.teamId(),
        WorkspaceRole.WORKSPACE_VIEWER
    );
    CapturingProviderCredentialRepository credentialRepository = new CapturingProviderCredentialRepository();
    ProviderCredentialService service = providerCredentialServiceFor(
        principalProvider,
        workspaceService,
        credentialRepository
    );

    principalProvider.setPrincipal(new BackendPrincipal("viewer-a", workspace.teamId(), "Viewer A"));

    assertThatThrownBy(() -> service.resolveForWorkspace(workspace.workspaceId(), "team-mimo"))
        .isInstanceOf(WorkspaceForbiddenException.class);

    principalProvider.setPrincipal(new BackendPrincipal("outsider", "team-b", "Outsider"));

    assertThatThrownBy(() -> service.resolveForWorkspace(workspace.workspaceId(), "team-mimo"))
        .isInstanceOf(WorkspaceForbiddenException.class);
    assertThat(credentialRepository.lookupCount()).isZero();
  }

  @Test
  void rejectsInvalidCredentialRefsBeforeRepositoryLookup() {
    MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
        new BackendPrincipal("owner-a", "team-a", "Owner A")
    );
    WorkspaceService workspaceService = workspaceServiceFor(principalProvider, new InMemoryWorkspaceRepository());
    WorkspaceRecord workspace = workspaceService.registerWorkspace("Team Knowledge", "main");
    CapturingProviderCredentialRepository credentialRepository = new CapturingProviderCredentialRepository();
    ProviderCredentialService service = providerCredentialServiceFor(
        principalProvider,
        workspaceService,
        credentialRepository
    );

    assertThatThrownBy(() -> service.resolveForWorkspace(workspace.workspaceId(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Provider credential reference is required");
    assertThatThrownBy(() -> service.resolveForWorkspace(workspace.workspaceId(), "../mimo"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid provider credential reference");
    assertThat(credentialRepository.lookupCount()).isZero();
  }

  @Test
  void ownerUpsertsAndListsWorkspaceCredentialPublicMetadataWithoutSecretReference() {
    MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
        new BackendPrincipal("owner-a", "team-a", "Owner A")
    );
    InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
    AuditService auditService = new AuditService(principalProvider, auditRepository);
    WorkspaceService workspaceService = workspaceServiceFor(
        principalProvider,
        new InMemoryWorkspaceRepository(),
        auditService
    );
    WorkspaceRecord workspace = workspaceService.registerWorkspace("Team Knowledge", "main");
    CapturingProviderCredentialRepository credentialRepository = new CapturingProviderCredentialRepository();
    ProviderCredentialService service = new ProviderCredentialService(
        workspaceService,
        credentialRepository,
        auditService
    );

    ProviderCredentialService.ProviderCredentialPublicMetadata created = service.upsertWorkspaceCredential(
        workspace.workspaceId(),
        " workspace-mimo ",
        "mimo-real",
        "mimo-v2.5",
        "https://token-plan-cn.xiaomimimo.com/v1",
        "MIMO_API_KEY"
    );

    assertThat(created.credentialRef()).isEqualTo("workspace-mimo");
    assertThat(created.workspaceId()).isEqualTo(workspace.workspaceId());
    assertThat(created.scope()).isEqualTo("WORKSPACE");
    assertThat(created.provider()).isEqualTo("mimo-real");
    assertThat(created.model()).isEqualTo("mimo-v2.5");
    assertThat(created.baseUrl()).isEqualTo("https://token-plan-cn.xiaomimimo.com/v1");
    assertThat(created.status()).isEqualTo("ACTIVE");
    assertThat(Arrays.stream(ProviderCredentialService.ProviderCredentialPublicMetadata.class.getRecordComponents())
        .map(component -> component.getName()))
        .doesNotContain("apiKeyEnvName", "apiKeySecretRef", "apiKey", "token", "authorization", "Authorization");

    assertThat(credentialRepository.findActiveByScope(workspace.teamId(), workspace.workspaceId(), "workspace-mimo"))
        .hasValueSatisfying(credential -> {
          assertThat(credential.teamId()).isEqualTo(workspace.teamId());
          assertThat(credential.workspaceId()).isEqualTo(workspace.workspaceId());
          assertThat(credential.apiKeySecretRef()).isEqualTo("env://MIMO_API_KEY");
        });

    assertThat(service.listWorkspaceCredentials(workspace.workspaceId()))
        .extracting(ProviderCredentialService.ProviderCredentialPublicMetadata::credentialRef)
        .containsExactly("workspace-mimo");

    ProviderCredentialService.ProviderCredentialPublicMetadata disabled = service.disableWorkspaceCredential(
        workspace.workspaceId(),
        " workspace-mimo "
    );

    assertThat(disabled.credentialRef()).isEqualTo("workspace-mimo");
    assertThat(disabled.workspaceId()).isEqualTo(workspace.workspaceId());
    assertThat(disabled.scope()).isEqualTo("WORKSPACE");
    assertThat(disabled.provider()).isEqualTo("mimo-real");
    assertThat(disabled.status()).isEqualTo("DISABLED");
    assertThat(credentialRepository.findActiveByScope(workspace.teamId(), workspace.workspaceId(), "workspace-mimo"))
        .isEmpty();
    assertThat(service.listWorkspaceCredentials(workspace.workspaceId()))
        .extracting(
            ProviderCredentialService.ProviderCredentialPublicMetadata::credentialRef,
            ProviderCredentialService.ProviderCredentialPublicMetadata::status
        )
        .containsExactly(org.assertj.core.api.Assertions.tuple("workspace-mimo", "DISABLED"));

    assertThat(auditRepository.findByWorkspaceId(workspace.workspaceId()))
        .filteredOn(event -> event.eventType().equals("PROVIDER_CREDENTIAL_UPSERTED"))
        .singleElement()
        .satisfies(event -> {
          assertThat(event.actorUserId()).isEqualTo("owner-a");
          assertThat(event.teamId()).isEqualTo(workspace.teamId());
          assertThat(event.message()).contains("workspace-mimo", "mimo-real");
          assertThat(event.message()).doesNotContain("MIMO_API_KEY", "env://", "apiKey", "token", "Authorization");
        });
    assertThat(auditRepository.findByWorkspaceId(workspace.workspaceId()))
        .filteredOn(event -> event.eventType().equals("PROVIDER_CREDENTIAL_DISABLED"))
        .singleElement()
        .satisfies(event -> {
          assertThat(event.actorUserId()).isEqualTo("owner-a");
          assertThat(event.teamId()).isEqualTo(workspace.teamId());
          assertThat(event.message()).contains("workspace-mimo");
          assertThat(event.message()).doesNotContain("MIMO_API_KEY", "env://", "apiKey", "token", "Authorization");
        });
  }

  @Test
  void rejectsPublicCredentialMetadataAccessWithoutWorkspaceOwnerRole() {
    MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
        new BackendPrincipal("owner-a", "team-a", "Owner A")
    );
    InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
    AuditService auditService = new AuditService(principalProvider, auditRepository);
    WorkspaceService workspaceService = workspaceServiceFor(
        principalProvider,
        new InMemoryWorkspaceRepository(),
        auditService
    );
    WorkspaceRecord workspace = workspaceService.registerWorkspace("Team Knowledge", "main");
    workspaceService.grantMember(
        workspace.workspaceId(),
        "viewer-a",
        workspace.teamId(),
        WorkspaceRole.WORKSPACE_VIEWER
    );
    CapturingProviderCredentialRepository credentialRepository = new CapturingProviderCredentialRepository();
    ProviderCredentialService service = new ProviderCredentialService(
        workspaceService,
        credentialRepository,
        auditService
    );

    principalProvider.setPrincipal(new BackendPrincipal("viewer-a", workspace.teamId(), "Viewer A"));

    assertThatThrownBy(() -> service.listWorkspaceCredentials(workspace.workspaceId()))
        .isInstanceOf(WorkspaceForbiddenException.class);
    assertThatThrownBy(() -> service.upsertWorkspaceCredential(
        workspace.workspaceId(),
        "workspace-mimo",
        "mimo-real",
        null,
        null,
        "MIMO_API_KEY"
    )).isInstanceOf(WorkspaceForbiddenException.class);
    assertThatThrownBy(() -> service.disableWorkspaceCredential(
        workspace.workspaceId(),
        "workspace-mimo"
    )).isInstanceOf(WorkspaceForbiddenException.class);
    assertThat(credentialRepository.savedCredentials()).isEmpty();
  }

  @Test
  void rejectsInvalidPublicCredentialInputsBeforeRepositorySave() {
    MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
        new BackendPrincipal("owner-a", "team-a", "Owner A")
    );
    InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
    AuditService auditService = new AuditService(principalProvider, auditRepository);
    WorkspaceService workspaceService = workspaceServiceFor(
        principalProvider,
        new InMemoryWorkspaceRepository(),
        auditService
    );
    WorkspaceRecord workspace = workspaceService.registerWorkspace("Team Knowledge", "main");
    CapturingProviderCredentialRepository credentialRepository = new CapturingProviderCredentialRepository();
    ProviderCredentialService service = new ProviderCredentialService(
        workspaceService,
        credentialRepository,
        auditService
    );

    assertThatThrownBy(() -> service.upsertWorkspaceCredential(
        workspace.workspaceId(),
        "../mimo",
        "mimo-real",
        null,
        null,
        "MIMO_API_KEY"
    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid provider credential reference");
    assertThatThrownBy(() -> service.upsertWorkspaceCredential(
        workspace.workspaceId(),
        "workspace-mimo",
        "mimo-real",
        null,
        null,
        "not-an-env-name"
    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("apiKeyEnvName must be an environment variable name");
    assertThatThrownBy(() -> service.upsertWorkspaceCredential(
        workspace.workspaceId(),
        "workspace-mimo",
        "unknown-provider",
        null,
        null,
        "MIMO_API_KEY"
    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported provider credential provider");
    assertThat(credentialRepository.savedCredentials()).isEmpty();
  }

  private ProviderCredentialService providerCredentialServiceFor(
      PrincipalProvider principalProvider,
      WorkspaceService workspaceService,
      ProviderCredentialRepository repository
  ) {
    return new ProviderCredentialService(
        workspaceService,
        repository,
        new AuditService(principalProvider, new InMemoryAuditRepository())
    );
  }

  private WorkspaceService workspaceServiceFor(
      PrincipalProvider principalProvider,
      WorkspaceRepository repository
  ) {
    return workspaceServiceFor(
        principalProvider,
        repository,
        new AuditService(principalProvider, new InMemoryAuditRepository())
    );
  }

  private WorkspaceService workspaceServiceFor(
      PrincipalProvider principalProvider,
      WorkspaceRepository repository,
      AuditService auditService
  ) {
    return new WorkspaceService(
        new BackendProperties(
            dataRoot.toString(),
            "owner-a",
            "team-a",
            "Owner A"
        ),
        principalProvider,
        repository,
        auditService
    );
  }

  private static final class CapturingProviderCredentialRepository extends ProviderCredentialRepository {

    private final Map<String, ProviderCredentialMetadata> credentials = new HashMap<>();
    private int lookupCount;
    private String lastLookupTeamId;
    private String lastLookupWorkspaceId;

    private CapturingProviderCredentialRepository() {
      super(null);
    }

    @Override
    public ProviderCredentialMetadata save(ProviderCredentialMetadata credential) {
      credentials.put(credential.id(), credential);
      return credential;
    }

    @Override
    public Optional<ProviderCredentialMetadata> findActiveByScope(
        String teamId,
        String workspaceId,
        String credentialRef
    ) {
      lookupCount++;
      lastLookupTeamId = teamId;
      lastLookupWorkspaceId = workspaceId;
      return credentials.values().stream()
          .filter(credential -> credential.teamId().equals(teamId))
          .filter(credential -> credential.credentialRef().equals(credentialRef))
          .filter(credential -> "ACTIVE".equals(credential.status()))
          .filter(credential -> credential.workspaceId() == null || credential.workspaceId().equals(workspaceId))
          .sorted(Comparator.comparing(
              credential -> workspaceId.equals(credential.workspaceId()) ? 0 : 1
          ))
          .findFirst();
    }

    @Override
    public List<ProviderCredentialMetadata> listByWorkspaceScope(String teamId, String workspaceId) {
      return credentials.values().stream()
          .filter(credential -> credential.teamId().equals(teamId))
          .filter(credential -> workspaceId.equals(credential.workspaceId()))
          .sorted(Comparator.comparing(ProviderCredentialMetadata::credentialRef))
          .toList();
    }

    @Override
    public Optional<ProviderCredentialMetadata> disableWorkspaceCredential(
        String teamId,
        String workspaceId,
        String credentialRef
    ) {
      return credentials.values().stream()
          .filter(credential -> credential.teamId().equals(teamId))
          .filter(credential -> workspaceId.equals(credential.workspaceId()))
          .filter(credential -> credential.credentialRef().equals(credentialRef))
          .findFirst()
          .map(credential -> {
            ProviderCredentialMetadata disabled = new ProviderCredentialMetadata(
                credential.id(),
                credential.credentialRef(),
                credential.teamId(),
                credential.workspaceId(),
                credential.provider(),
                credential.model(),
                credential.baseUrl(),
                credential.apiKeySecretRef(),
                "DISABLED"
            );
            credentials.put(disabled.id(), disabled);
            return disabled;
          });
    }

    private List<ProviderCredentialMetadata> savedCredentials() {
      return credentials.values().stream()
          .sorted(Comparator.comparing(ProviderCredentialMetadata::credentialRef))
          .toList();
    }

    private int lookupCount() {
      return lookupCount;
    }

    private String lastLookupTeamId() {
      return lastLookupTeamId;
    }

    private String lastLookupWorkspaceId() {
      return lastLookupWorkspaceId;
    }
  }

  private static final class MutablePrincipalProvider implements PrincipalProvider {

    private BackendPrincipal principal;

    private MutablePrincipalProvider(BackendPrincipal principal) {
      this.principal = principal;
    }

    @Override
    public BackendPrincipal currentPrincipal() {
      return principal;
    }

    private void setPrincipal(BackendPrincipal principal) {
      this.principal = principal;
    }
  }
}
