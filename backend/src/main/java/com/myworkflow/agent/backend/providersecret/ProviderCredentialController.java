package com.myworkflow.agent.backend.providersecret;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Profile("jdbc")
@RequestMapping(path = "/v1/workspaces/{workspaceId}/provider-credentials", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProviderCredentialController {

  private final ProviderCredentialService providerCredentialService;

  public ProviderCredentialController(ProviderCredentialService providerCredentialService) {
    this.providerCredentialService = providerCredentialService;
  }

  @GetMapping
  public ApiEnvelope<List<ProviderCredentialService.ProviderCredentialPublicMetadata>> listCredentials(
      @PathVariable String workspaceId
  ) {
    return ApiEnvelope.ok(providerCredentialService.listWorkspaceCredentials(workspaceId));
  }

  @PutMapping(path = "/{credentialRef}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<ProviderCredentialService.ProviderCredentialPublicMetadata> upsertCredential(
      @PathVariable String workspaceId,
      @PathVariable String credentialRef,
      @Valid @RequestBody UpsertProviderCredentialRequest request
  ) {
    rejectRawSecretAliases(request);
    return ApiEnvelope.ok(providerCredentialService.upsertWorkspaceCredential(
        workspaceId,
        credentialRef,
        request.provider(),
        request.model(),
        request.baseUrl(),
        request.apiKeyEnvName()
    ));
  }

  @PostMapping(path = "/{credentialRef}/disable")
  public ApiEnvelope<ProviderCredentialService.ProviderCredentialPublicMetadata> disableCredential(
      @PathVariable String workspaceId,
      @PathVariable String credentialRef
  ) {
    return ApiEnvelope.ok(providerCredentialService.disableWorkspaceCredential(workspaceId, credentialRef));
  }

  private static void rejectRawSecretAliases(UpsertProviderCredentialRequest request) {
    if (request.apiKey() != null
        || request.token() != null
        || request.authorization() != null
        || request.Authorization() != null
        || request.apiKeySecretRef() != null) {
      throw new IllegalArgumentException("Raw provider secrets are not accepted");
    }
  }

  public record UpsertProviderCredentialRequest(
      @NotBlank String provider,
      String model,
      String baseUrl,
      @NotBlank String apiKeyEnvName,
      String apiKey,
      String token,
      String authorization,
      String Authorization,
      String apiKeySecretRef
  ) {
  }
}
