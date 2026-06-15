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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(
    classes = {
        BackendApplication.class,
        AgentRunCancelControllerTest.AgentRunCancelControllerTestConfig.class
    },
    properties = {
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-run-cancel-test"
    }
)
@AutoConfigureMockMvc
class AgentRunCancelControllerTest {

  private static final BlockingWorker WORKER = new BlockingWorker();

  @Autowired
  private MockMvc mockMvc;

  @BeforeEach
  void resetWorker() {
    WORKER.reset();
  }

  @Test
  void cancelKeepsRunCanceledWhenWorkerCompletesLate() throws Exception {
    String workspaceId = createWorkspace();
    MvcResult runResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "long running",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String runId = JsonPath.read(runResult.getResponse().getContentAsString(), "$.data.runId");
    assertThat(WORKER.awaitStarted()).isTrue();

    mockMvc.perform(post("/v1/agent-runs/{runId}/cancel", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELED"));

    WORKER.release();
    pollRun(runId, "CANCELED")
        .andExpect(jsonPath("$.data.outputKind").doesNotExist())
        .andExpect(jsonPath("$.data.wroteWorkspace").value(false));
  }

  private String createWorkspace() throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Cancel Workspace",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.workspaceId");
  }

  private ResultActions pollRun(String runId, String expectedStatus) throws Exception {
    AssertionError lastError = null;
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      MvcResult result = mockMvc.perform(get("/v1/agent-runs/{runId}", runId))
          .andExpect(status().isOk())
          .andReturn();
      String status = JsonPath.read(result.getResponse().getContentAsString(), "$.data.status");
      if (expectedStatus.equals(status)) {
        return mockMvc.perform(get("/v1/agent-runs/{runId}", runId))
            .andExpect(status().isOk());
      }
      lastError = new AssertionError("Expected status %s but was %s".formatted(expectedStatus, status));
      Thread.sleep(50);
    }
    throw lastError == null ? new AssertionError("Run was not found") : lastError;
  }

  @TestConfiguration
  static class AgentRunCancelControllerTestConfig {

    @Bean
    @Primary
    AgentWorker agentWorker() {
      return WORKER;
    }
  }

  private static final class BlockingWorker implements AgentWorker {

    private CountDownLatch started;
    private CountDownLatch release;

    void reset() {
      started = new CountDownLatch(1);
      release = new CountDownLatch(1);
    }

    boolean awaitStarted() throws InterruptedException {
      return started.await(2, TimeUnit.SECONDS);
    }

    void release() {
      release.countDown();
    }

    @Override
    public AgentWorkerResponse run(AgentWorkerRequest request) {
      started.countDown();
      try {
        if (!release.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("worker was not released");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("worker interrupted", exception);
      }
      return new AgentWorkerResponse(
          "agent-backend-response.v1",
          request.runId(),
          "SUCCEEDED",
          "answer",
          "late success",
          false,
          false,
          List.of(".agent-runs/%s/artifact.json".formatted(request.runId())),
          false,
          List.of()
      );
    }
  }
}
