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
import java.util.function.Function;
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
        AgentRunControllerTest.AgentRunControllerTestConfig.class
    },
    properties = {
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-run-controller-test"
    }
)
@AutoConfigureMockMvc
class AgentRunControllerTest {

  private static final ControlledAgentWorker WORKER = new ControlledAgentWorker();

  @Autowired
  private MockMvc mockMvc;

  @BeforeEach
  void resetWorker() {
    WORKER.reset();
  }

  @Test
  void postRunReturnsQueuedBeforeWorkerCompletesThenPollingReturnsAnswer() throws Exception {
    String workspaceId = createWorkspace("Async Answer");
    WORKER.blockUntilReleased();

    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "总结当前知识库",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.data.status").value("QUEUED"))
        .andExpect(jsonPath("$.data.displayText").doesNotExist())
        .andReturn();

    assertThat(WORKER.awaitStarted()).isTrue();
    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");

    WORKER.release();

    MvcResult completed = pollRun(runId, "SUCCEEDED");
    assertThat((String) JsonPath.read(completed.getResponse().getContentAsString(), "$.data.displayText"))
        .contains("Backend answer");
    assertThat((List<String>) JsonPath.read(completed.getResponse().getContentAsString(), "$.data.artifactRefs"))
        .contains(".agent-runs/%s/artifact.json".formatted(runId));
  }

  @Test
  void candidatePatchCompletesAsWaitingApprovalWithoutWorkspaceWrite() throws Exception {
    String workspaceId = createWorkspace("Async Candidate");
    WORKER.respondWith((request) -> new AgentWorkerResponse(
        "agent-backend-response.v1",
        request.runId(),
        "WAITING_APPROVAL",
        "candidate-patch",
        "Candidate patch needs approval.",
        false,
        true,
        List.of(".agent-runs/%s/artifact.json".formatted(request.runId())),
        false,
        List.of("knowledge-base/drafts/%s.md".formatted(request.runId()))
    ));

    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "准备候选补丁",
                  "mode": "llm-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();

    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    MvcResult completed = pollRun(runId, "WAITING_APPROVAL");
    String body = completed.getResponse().getContentAsString();
    assertThat((Boolean) JsonPath.read(body, "$.data.requiresApproval")).isTrue();
    assertThat((Boolean) JsonPath.read(body, "$.data.wroteWorkspace")).isFalse();
    assertThat((List<String>) JsonPath.read(body, "$.data.targetWorkspacePaths"))
        .contains("knowledge-base/drafts/%s.md".formatted(runId));
  }

  @Test
  void runRequestRejectsClientSuppliedWorkspaceRoot() throws Exception {
    String workspaceId = createWorkspace("Reject Workspace Root");

    mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "总结当前知识库",
                  "workspaceRoot": "/tmp/should-not-be-used"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
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
  static class AgentRunControllerTestConfig {

    @Bean
    @Primary
    AgentWorker agentWorker() {
      return WORKER;
    }
  }

  private static final class ControlledAgentWorker implements AgentWorker {

    private CountDownLatch started;
    private CountDownLatch release;
    private Function<AgentWorkerRequest, AgentWorkerResponse> responder;

    void reset() {
      started = new CountDownLatch(1);
      release = new CountDownLatch(0);
      responder = (request) -> new AgentWorkerResponse(
          "agent-backend-response.v1",
          request.runId(),
          "SUCCEEDED",
          "answer",
          "Backend answer for %s".formatted(request.userMessage()),
          false,
          false,
          List.of(".agent-runs/%s/artifact.json".formatted(request.runId())),
          false,
          List.of()
      );
    }

    void blockUntilReleased() {
      release = new CountDownLatch(1);
    }

    boolean awaitStarted() throws InterruptedException {
      return started.await(2, TimeUnit.SECONDS);
    }

    void release() {
      release.countDown();
    }

    void respondWith(Function<AgentWorkerRequest, AgentWorkerResponse> responder) {
      this.responder = responder;
    }

    @Override
    public AgentWorkerResponse run(AgentWorkerRequest request) {
      started.countDown();
      try {
        if (!release.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("test worker was not released");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("test worker interrupted", exception);
      }
      return responder.apply(request);
    }
  }
}
