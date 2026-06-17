package com.myworkflow.agent.backend.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myworkflow.agent.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
class OpsIntegrationContractControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void integrationContractReturnsFrontendAndRuntimeReadinessWithoutSensitiveMaterial() throws Exception {
    MvcResult result = mockMvc.perform(get("/v1/ops/integration-contract"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.schemaVersion").value("java-backend-integration-contract.v1"))
        .andExpect(jsonPath("$.data.apiBasePath").value("/v1"))
        .andExpect(jsonPath("$.data.publicEnvelopeSchema").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.data.frontendRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/me')]").exists())
        .andExpect(jsonPath("$.data.frontendRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/workspaces')]").exists())
        .andExpect(jsonPath("$.data.frontendRequiredEndpoints[?(@.method == 'POST' && @.path == '/v1/workspaces/{workspaceId}/agent-runs')]").exists())
        .andExpect(jsonPath("$.data.frontendRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/agent-runs/{runId}/events/stream')]").exists())
        .andExpect(jsonPath("$.data.frontendRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/agent-runs/{runId}/artifacts')]").exists())
        .andExpect(jsonPath("$.data.frontendRequiredEndpoints[?(@.method == 'POST' && @.path == '/v1/agent-runs/{runId}/approvals')]").exists())
        .andExpect(jsonPath("$.data.frontendRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/workspaces/{workspaceId}/audit-events')]").exists())
        .andExpect(jsonPath("$.data.frontendRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/workspaces/{workspaceId}/provider-credentials')]").exists())
        .andExpect(jsonPath("$.data.frontendRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/ops/auth-config')]").exists())
        .andExpect(jsonPath("$.data.runtimeRequiredEndpoints[?(@.method == 'POST' && @.path == '/v1/workspaces/{workspaceId}/agent-runs')]").exists())
        .andExpect(jsonPath("$.data.runtimeRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/agent-runs/{runId}')]").exists())
        .andExpect(jsonPath("$.data.runtimeRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/agent-runs/{runId}/events')]").exists())
        .andExpect(jsonPath("$.data.runtimeRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/agent-runs/{runId}/artifacts')]").exists())
        .andExpect(jsonPath("$.data.runtimeRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/workspaces/{workspaceId}/remote-runners')]").exists())
        .andExpect(jsonPath("$.data.runtimeRequiredEndpoints[?(@.method == 'POST' && @.path == '/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/heartbeat')]").exists())
        .andExpect(jsonPath("$.data.runtimeRequiredEndpoints[?(@.method == 'POST' && @.path == '/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/lease')]").exists())
        .andExpect(jsonPath("$.data.capabilities.asyncAgentRuns").value(true))
        .andExpect(jsonPath("$.data.capabilities.sseRunEvents").value(true))
        .andExpect(jsonPath("$.data.capabilities.approvalBoundary").value(true))
        .andExpect(jsonPath("$.data.capabilities.artifactRegistry").value(true))
        .andExpect(jsonPath("$.data.capabilities.providerCredentialMetadata").value(true))
        .andExpect(jsonPath("$.data.capabilities.oidcJwtBearer").value(true))
        .andExpect(jsonPath("$.data.capabilities.oauthLoginSession").value(false))
        .andExpect(jsonPath("$.data.capabilities.tokenIntrospection").value(true))
        .andExpect(jsonPath("$.data.capabilities.externalDirectorySync").value(false))
        .andExpect(jsonPath("$.data.capabilities.productionSecretManager").value(false))
        .andExpect(jsonPath("$.data.capabilities.remoteRunnerDispatch").value(false))
        .andExpect(jsonPath("$.data.capabilities.multiNodeStreamFanout").value(false))
        .andReturn();

    String response = result.getResponse().getContentAsString();
    assertThat(response)
        .doesNotContain("accessToken")
        .doesNotContain("refreshToken")
        .doesNotContain("apiKey")
        .doesNotContain("apiKeySecretRef")
        .doesNotContain("Authorization")
        .doesNotContain("issuerUri")
        .doesNotContain("jwksUri")
        .doesNotContain("workspaceRoot")
        .doesNotContain("providerRuntime")
        .doesNotContain("rawProvider");
  }
}
