package com.myworkflow.agent.backend.run;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
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
        AgentRunFailureControllerTest.AgentRunFailureControllerTestConfig.class
    },
    properties = {
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-run-failure-test"
    }
)
@AutoConfigureMockMvc
class AgentRunFailureControllerTest {

  private static final AlwaysFailingWorker WORKER = new AlwaysFailingWorker();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AgentRunRepository repository;

  @BeforeEach
  void resetWorker() {
    WORKER.reset();
  }

  @Test
  void workerFailureMarksRunFailedAfterMaxAttempts() throws Exception {
    String workspaceId = createWorkspace();
    MvcResult runResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "fail",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String runId = JsonPath.read(runResult.getResponse().getContentAsString(), "$.data.runId");

    pollRun(runId, "FAILED")
        .andExpect(jsonPath("$.data.errorCode").value("WORKER_FAILED"));
    assertThat(WORKER.calls()).isEqualTo(3);
    assertThat(repository.findAttempts(runId))
        .extracting(RunAttemptRecord::status)
        .containsExactly(AgentJobStatus.FAILED, AgentJobStatus.FAILED, AgentJobStatus.FAILED);
  }

  private String createWorkspace() throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Failure Workspace",
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
  static class AgentRunFailureControllerTestConfig {

    @Bean
    @Primary
    AgentWorker agentWorker() {
      return WORKER;
    }
  }

  private static final class AlwaysFailingWorker implements AgentWorker {

    private final AtomicInteger calls = new AtomicInteger();

    void reset() {
      calls.set(0);
    }

    int calls() {
      return calls.get();
    }

    @Override
    public AgentWorkerResponse run(AgentWorkerRequest request) {
      calls.incrementAndGet();
      throw new AgentWorkerException("intentional failure");
    }
  }
}
