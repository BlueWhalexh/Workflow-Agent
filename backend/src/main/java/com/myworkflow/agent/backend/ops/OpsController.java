package com.myworkflow.agent.backend.ops;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import com.myworkflow.agent.backend.config.BackendProperties;
import java.util.List;
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
    return ApiEnvelope.ok(AuthConfigResponse.from(properties));
  }

  @GetMapping(value = "/v1/ops/integration-contract", produces = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<IntegrationContractResponse> integrationContract() {
    return ApiEnvelope.ok(IntegrationContractResponse.current());
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
      boolean oauthIntrospectionConfigured,
      boolean oauthClientAuthConfigured,
      String userIdClaim,
      String teamIdClaim,
      String displayNameClaim
  ) {
    static AuthConfigResponse from(BackendProperties properties) {
      if (properties.oauthIntrospection().enabled()) {
        BackendProperties.OAuthIntrospection oauth = properties.oauthIntrospection();
        return new AuthConfigResponse(
            properties.authMode(),
            false,
            false,
            false,
            false,
            true,
            oauth.clientAuthConfigured(),
            oauth.userIdClaim(),
            oauth.teamIdClaim(),
            oauth.displayNameClaim()
        );
      }
      BackendProperties.Oidc oidc = properties.oidc();
      return new AuthConfigResponse(
          properties.authMode(),
          oidc.discoveryEnabled(),
          oidc.issuerConfigured(),
          oidc.jwksUriConfigured(),
          oidc.audienceConfigured(),
          false,
          false,
          oidc.userIdClaim(),
          oidc.teamIdClaim(),
          oidc.displayNameClaim()
      );
    }
  }

  public record IntegrationContractResponse(
      String schemaVersion,
      String apiBasePath,
      String publicEnvelopeSchema,
      String runEventStream,
      List<IntegrationEndpoint> frontendRequiredEndpoints,
      List<IntegrationEndpoint> runtimeRequiredEndpoints,
      IntegrationCapabilities capabilities
  ) {
    static IntegrationContractResponse current() {
      return new IntegrationContractResponse(
          "java-backend-integration-contract.v1",
          "/v1",
          "java-backend-api.v1",
          "text/event-stream with Last-Event-ID replay cursor; EOF is not terminal evidence",
          frontendEndpointContract(),
          runtimeEndpointContract(),
          IntegrationCapabilities.current()
      );
    }

    private static List<IntegrationEndpoint> frontendEndpointContract() {
      return List.of(
          new IntegrationEndpoint("GET", "/v1/me", "current principal"),
          new IntegrationEndpoint("GET", "/v1/teams", "current team discovery"),
          new IntegrationEndpoint("GET", "/v1/workspaces", "workspace listing"),
          new IntegrationEndpoint(
              "POST",
              "/v1/workspaces/{workspaceId}/agent-runs",
              "create async agent run; optional remoteRunnerRef dispatches to a registered online runner"
          ),
          new IntegrationEndpoint("GET", "/v1/agent-runs/{runId}", "poll run state"),
          new IntegrationEndpoint("GET", "/v1/agent-runs/{runId}/events", "durable run events"),
          new IntegrationEndpoint("GET", "/v1/agent-runs/{runId}/events/stream", "SSE run event stream"),
          new IntegrationEndpoint("GET", "/v1/agent-runs/{runId}/artifacts", "list run artifacts"),
          new IntegrationEndpoint("GET", "/v1/artifacts/{artifactId}", "read safe artifact text"),
          new IntegrationEndpoint("GET", "/v1/agent-runs/{runId}/approvals", "list run approvals"),
          new IntegrationEndpoint("POST", "/v1/agent-runs/{runId}/approvals", "submit approval decision"),
          new IntegrationEndpoint("POST", "/v1/agent-runs/{runId}/cancel", "cancel queued or running run"),
          new IntegrationEndpoint("GET", "/v1/workspaces/{workspaceId}/audit-events", "owner audit listing"),
          new IntegrationEndpoint("GET", "/v1/workspaces/{workspaceId}/provider-credentials", "owner credential metadata"),
          new IntegrationEndpoint("POST", "/v1/teams/{teamId}/directory-sync", "admin external directory snapshot sync"),
          new IntegrationEndpoint("GET", "/v1/ops/auth-config", "redacted auth diagnostics")
      );
    }

    private static List<IntegrationEndpoint> runtimeEndpointContract() {
      return List.of(
          new IntegrationEndpoint(
              "POST",
              "/v1/workspaces/{workspaceId}/agent-runs",
              "create async agent run; optional remoteRunnerRef dispatches to a registered online runner"
          ),
          new IntegrationEndpoint("GET", "/v1/agent-runs/{runId}", "poll mapped backend run response"),
          new IntegrationEndpoint("GET", "/v1/agent-runs/{runId}/events", "read durable lifecycle events"),
          new IntegrationEndpoint("GET", "/v1/agent-runs/{runId}/artifacts", "list backend artifact refs"),
          new IntegrationEndpoint("GET", "/v1/artifacts/{artifactId}", "read safe artifact content"),
          new IntegrationEndpoint("GET", "/v1/agent-runs/{runId}/approvals", "read approval requests"),
          new IntegrationEndpoint("POST", "/v1/agent-runs/{runId}/approvals", "submit approval decision"),
          new IntegrationEndpoint("GET", "/v1/workspaces/{workspaceId}/remote-runners", "list registered runners"),
          new IntegrationEndpoint("PUT", "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}", "register runner metadata"),
          new IntegrationEndpoint("POST", "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/heartbeat", "record runner heartbeat"),
          new IntegrationEndpoint("POST", "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/lease", "claim runner lease"),
          new IntegrationEndpoint("GET", "/v1/ops/auth-config", "redacted auth diagnostics")
      );
    }
  }

  public record IntegrationEndpoint(
      String method,
      String path,
      String purpose
  ) {
  }

  public record IntegrationCapabilities(
      boolean asyncAgentRuns,
      boolean sseRunEvents,
      boolean approvalBoundary,
      boolean artifactRegistry,
      boolean providerCredentialMetadata,
      boolean oidcJwtBearer,
      boolean oauthLoginSession,
      boolean tokenIntrospection,
      boolean externalDirectorySync,
      boolean productionSecretManager,
      boolean remoteRunnerDispatch,
      boolean multiNodeStreamFanout
  ) {
    static IntegrationCapabilities current() {
      return new IntegrationCapabilities(
          true,
          true,
          true,
          true,
          true,
          true,
          false,
          true,
          true,
          true,
          true,
          false
      );
    }
  }
}
