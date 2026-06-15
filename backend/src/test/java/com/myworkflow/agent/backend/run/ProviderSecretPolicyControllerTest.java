package com.myworkflow.agent.backend.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = {
        BackendApplication.class,
        ProviderSecretPolicyControllerTest.ProviderSecretPolicyTestConfig.class
    },
    properties = {
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-provider-secret-policy-test",
        "my-workflow.backend.provider-runtime.refs.mimo-safe.provider=mimo-real",
        "my-workflow.backend.provider-runtime.refs.mimo-safe.api-key-env-name=MIMO_API_KEY",
        "my-workflow.backend.provider-runtime.refs.mimo-safe.model=mimo-v2.5",
        "my-workflow.backend.provider-runtime.refs.mimo-safe.base-url=https://token-plan-cn.xiaomimimo.com/v1",
        "my-workflow.backend.provider-runtime.refs.mimo-safe.timeout-ms=45000"
    }
)
@AutoConfigureMockMvc
class ProviderSecretPolicyControllerTest {

  private static final CapturingAgentWorker WORKER = new CapturingAgentWorker();

  @Autowired
  private MockMvc mockMvc;

  @BeforeEach
  void resetWorker() {
    WORKER.reset();
  }

  @Test
  void runRequestUsesProviderRuntimeReferenceWithoutSecretValue() throws Exception {
    String workspaceId = createWorkspace("Provider Runtime Ref");

    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "用安全 provider ref 总结当前知识库",
                  "mode": "llm-open-agent",
                  "providerRuntimeRef": "mimo-safe"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();

    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    pollRun(runId, "SUCCEEDED");
    Map<String, Object> providerRuntime = WORKER.lastProviderRuntime();
    assertThat(providerRuntime)
        .containsEntry("provider", "mimo-real")
        .containsEntry("apiKeyEnvName", "MIMO_API_KEY")
        .containsEntry("model", "mimo-v2.5")
        .containsEntry("baseUrl", "https://token-plan-cn.xiaomimimo.com/v1")
        .containsEntry("timeoutMs", 45_000);
    assertThat(providerRuntime).doesNotContainKeys("apiKey", "token", "authorization", "Authorization");
    assertThat(providerRuntime.toString()).doesNotContain("tp-raw-secret-should-not-pass");
  }

  @Test
  void runRequestRejectsDirectProviderRuntimeTokenPayloadWithoutEchoingToken() throws Exception {
    String workspaceId = createWorkspace("Reject Provider Token");

    MvcResult result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "不要让 token 进后端",
                  "mode": "llm-open-agent",
                  "providerRuntime": {
                    "provider": "mimo-real",
                    "apiKey": "tp-raw-secret-should-not-pass"
                  }
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
        .andReturn();

    assertThat(result.getResponse().getContentAsString()).doesNotContain("tp-raw-secret-should-not-pass");
    assertThat(WORKER.lastRequest()).isNull();
  }

  private String createWorkspace(String name) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "%s",
                  "defaultBranch": "main"
                }
                """.formatted(name)))
        .andExpect(status().isOk())
        .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.workspaceId");
  }

  private MvcResult pollRun(String runId, String expectedStatus) throws Exception {
    AssertionError lastError = null;
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      MvcResult result = mockMvc.perform(get("/v1/agent-runs/{runId}", runId))
          .andExpect(status().isOk())
          .andReturn();
      String status = JsonPath.read(result.getResponse().getContentAsString(), "$.data.status");
      if (expectedStatus.equals(status)) {
        return result;
      }
      lastError = new AssertionError("Expected status %s but was %s".formatted(expectedStatus, status));
      Thread.sleep(50);
    }
    throw lastError == null ? new AssertionError("Run was not found") : lastError;
  }

  @TestConfiguration
  static class ProviderSecretPolicyTestConfig {

    @Bean
    @Primary
    AgentWorker agentWorker() {
      return WORKER;
    }
  }

  private static final class CapturingAgentWorker implements AgentWorker {

    private final AtomicReference<AgentWorkerRequest> lastRequest = new AtomicReference<>();

    void reset() {
      lastRequest.set(null);
    }

    AgentWorkerRequest lastRequest() {
      return lastRequest.get();
    }

    Map<String, Object> lastProviderRuntime() {
      return lastRequest.get().providerRuntime();
    }

    @Override
    public AgentWorkerResponse run(AgentWorkerRequest request) {
      lastRequest.set(request);
      return new AgentWorkerResponse(
          "agent-backend-response.v1",
          request.runId(),
          "SUCCEEDED",
          "answer",
          "Provider ref accepted",
          false,
          false,
          List.of(),
          false,
          List.of()
      );
    }
  }
}
