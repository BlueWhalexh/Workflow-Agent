package com.myworkflow.agent.backend.ops;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import com.myworkflow.agent.backend.config.BackendProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpsController {

  private static final String SERVICE_NAME = "my-workflow-agent-backend";

  private final BackendProperties properties;

  public OpsController(BackendProperties properties) {
    this.properties = properties;
  }

  @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<OpsStatusResponse> health() {
    return ApiEnvelope.ok(new OpsStatusResponse("ok", SERVICE_NAME));
  }

  @GetMapping(value = "/ready", produces = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<OpsStatusResponse> ready() {
    return ApiEnvelope.ok(new OpsStatusResponse("ready", SERVICE_NAME));
  }

  @GetMapping(value = "/v1/ops/auth-config", produces = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<AuthConfigResponse> authConfig() {
    return ApiEnvelope.ok(AuthConfigResponse.from(properties.oidc()));
  }

  public record OpsStatusResponse(
      String status,
      String service
  ) {
  }

  public record AuthConfigResponse(
      String mode,
      boolean discoveryEnabled,
      boolean issuerConfigured,
      boolean jwksUriConfigured,
      boolean audienceConfigured,
      String userIdClaim,
      String teamIdClaim,
      String displayNameClaim
  ) {
    static AuthConfigResponse from(BackendProperties.Oidc oidc) {
      return new AuthConfigResponse(
          oidc.mode(),
          oidc.discoveryEnabled(),
          oidc.issuerConfigured(),
          oidc.jwksUriConfigured(),
          oidc.audienceConfigured(),
          oidc.userIdClaim(),
          oidc.teamIdClaim(),
          oidc.displayNameClaim()
      );
    }
  }
}
