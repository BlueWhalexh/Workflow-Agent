package com.myworkflow.agent.backend.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import java.time.Duration;
import java.time.Instant;
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

@SpringBootTest(
    classes = {
        BackendApplication.class,
        RunEventControllerTest.RunEventControllerTestConfig.class
    },
    properties = {
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-run-event-test"
    }
)
@AutoConfigureMockMvc
class RunEventControllerTest {

  private static final StaleRecoveryWorker WORKER = new StaleRecoveryWorker();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AgentRunRepository repository;

  @BeforeEach
  void resetWorker() {
    WORKER.reset();
  }

  @Test
  void listsRunLifecycleEventsWithoutRuntimePrivateFields() throws Exception {
    String workspaceId = createWorkspace();
    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "event answer",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    pollRun(runId, "SUCCEEDED");

    MvcResult eventsResult = mockMvc.perform(get("/v1/agent-runs/{runId}/events", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].runId").value(runId))
        .andExpect(jsonPath("$.data[0].eventType").value("RUN_QUEUED"))
        .andExpect(jsonPath("$.data[0].status").value("QUEUED"))
        .andExpect(jsonPath("$.data[1].eventType").value("RUNNING"))
        .andExpect(jsonPath("$.data[1].status").value("RUNNING"))
        .andExpect(jsonPath("$.data[2].eventType").value("COMPLETED"))
        .andExpect(jsonPath("$.data[2].status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.data[0].absolutePath").doesNotExist())
        .andExpect(jsonPath("$.data[0].source").doesNotExist())
        .andReturn();

    String body = eventsResult.getResponse().getContentAsString();
    assertThat(body).doesNotContain("workspaceRoot");
    assertThat(body).doesNotContain("rawProvider");
    assertThat(body).doesNotContain("runtime");
  }

  @Test
  void doesNotAppendCompletedEventAfterRunWasRecoveredAsFailed() throws Exception {
    String workspaceId = createWorkspace();
    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "stale event",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    assertThat(WORKER.awaitStarted()).isTrue();

    Instant now = Instant.now();
    int recovered = repository.failStaleRunningJobs(now.plusSeconds(120), "STALE_LOCK", now.plusSeconds(121));
    assertThat(recovered).isEqualTo(1);

    WORKER.release();
    assertThat(WORKER.awaitReturned()).isTrue();
    pollRun(runId, "FAILED");

    assertNoCompletedEventAppears(runId);
  }

  @Test
  void streamsRunLifecycleEventsAsServerSentEventsWithoutRuntimePrivateFields() throws Exception {
    String workspaceId = createWorkspace();
    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "stream event",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    assertThat(WORKER.awaitStarted()).isTrue();

    MvcResult streamResult = mockMvc.perform(get("/v1/agent-runs/{runId}/events/stream", runId)
            .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted())
        .andReturn();

    WORKER.release();
    assertThat(WORKER.awaitReturned()).isTrue();
    pollRun(runId, "SUCCEEDED");
    streamResult.getAsyncResult(TimeUnit.SECONDS.toMillis(5));

    MvcResult dispatchedResult = mockMvc.perform(asyncDispatch(streamResult))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andReturn();

    String body = dispatchedResult.getResponse().getContentAsString();
    assertThat(body).contains("event:RUN_QUEUED");
    assertThat(body).contains("event:RUNNING");
    assertThat(body).contains("event:COMPLETED");
    assertThat(body).contains(runId);
    assertThat(body).doesNotContain("workspaceRoot");
    assertThat(body).doesNotContain("rawProvider");
    assertThat(body).doesNotContain("runtime");
    assertThat(body).doesNotContain("Authorization");
  }

  @Test
  void streamsOnlyEventsAfterLastEventIdWhenClientReconnects() throws Exception {
    String workspaceId = createWorkspace();
    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "stream event",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    assertThat(WORKER.awaitStarted()).isTrue();

    MvcResult initialEvents = mockMvc.perform(get("/v1/agent-runs/{runId}/events", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].eventType").value("RUN_QUEUED"))
        .andReturn();
    String consumedEventId = JsonPath.read(initialEvents.getResponse().getContentAsString(), "$.data[0].eventId");

    MvcResult streamResult = mockMvc.perform(get("/v1/agent-runs/{runId}/events/stream", runId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .header("Last-Event-ID", consumedEventId))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted())
        .andReturn();

    WORKER.release();
    assertThat(WORKER.awaitReturned()).isTrue();
    pollRun(runId, "SUCCEEDED");
    streamResult.getAsyncResult(TimeUnit.SECONDS.toMillis(5));

    MvcResult dispatchedResult = mockMvc.perform(asyncDispatch(streamResult))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andReturn();

    String body = dispatchedResult.getResponse().getContentAsString();
    assertThat(body).doesNotContain("event:RUN_QUEUED");
    assertThat(body).doesNotContain(consumedEventId);
    assertThat(body).contains("event:RUNNING");
    assertThat(body).contains("event:COMPLETED");
    assertThat(body).doesNotContain("workspaceRoot");
    assertThat(body).doesNotContain("rawProvider");
    assertThat(body).doesNotContain("Authorization");
  }

  @Test
  void streamsAllEventsWhenLastEventIdIsUnknown() throws Exception {
    String workspaceId = createWorkspace();
    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "stream event",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    assertThat(WORKER.awaitStarted()).isTrue();

    MvcResult streamResult = mockMvc.perform(get("/v1/agent-runs/{runId}/events/stream", runId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .header("Last-Event-ID", "evt_missing"))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted())
        .andReturn();

    WORKER.release();
    assertThat(WORKER.awaitReturned()).isTrue();
    pollRun(runId, "SUCCEEDED");
    streamResult.getAsyncResult(TimeUnit.SECONDS.toMillis(5));

    MvcResult dispatchedResult = mockMvc.perform(asyncDispatch(streamResult))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andReturn();

    String body = dispatchedResult.getResponse().getContentAsString();
    assertThat(body).contains("event:RUN_QUEUED");
    assertThat(body).contains("event:RUNNING");
    assertThat(body).contains("event:COMPLETED");
    assertThat(body).doesNotContain("workspaceRoot");
    assertThat(body).doesNotContain("rawProvider");
    assertThat(body).doesNotContain("Authorization");
  }

  @Test
  void streamsOnlyLaterEventsAfterMiddleLastEventIdOnCompletedRun() throws Exception {
    String workspaceId = createWorkspace();
    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "event answer",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    pollRun(runId, "SUCCEEDED");

    MvcResult eventsResult = mockMvc.perform(get("/v1/agent-runs/{runId}/events", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[1].eventType").value("RUNNING"))
        .andReturn();
    String runningEventId = JsonPath.read(eventsResult.getResponse().getContentAsString(), "$.data[1].eventId");

    MvcResult streamResult = mockMvc.perform(get("/v1/agent-runs/{runId}/events/stream", runId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .header("Last-Event-ID", runningEventId))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted())
        .andReturn();
    streamResult.getAsyncResult(TimeUnit.SECONDS.toMillis(5));

    MvcResult dispatchedResult = mockMvc.perform(asyncDispatch(streamResult))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andReturn();

    String body = dispatchedResult.getResponse().getContentAsString();
    assertThat(body).doesNotContain("event:RUN_QUEUED");
    assertThat(body).doesNotContain("event:RUNNING");
    assertThat(body).doesNotContain(runningEventId);
    assertThat(body).contains("event:COMPLETED");
    assertThat(body).doesNotContain("workspaceRoot");
    assertThat(body).doesNotContain("rawProvider");
    assertThat(body).doesNotContain("Authorization");
  }

  @Test
  void rejectsRunEventStreamWhenPrincipalCannotReadWorkspace() throws Exception {
    String workspaceId = createWorkspace();
    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "event answer",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    pollRun(runId, "SUCCEEDED");

    mockMvc.perform(get("/v1/agent-runs/{runId}/events/stream", runId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .header("X-Dev-User-Id", "user_without_workspace_access")
            .header("X-Dev-Team-Id", "team_dev"))
        .andExpect(status().isForbidden())
        .andExpect(request().asyncNotStarted());
  }

  @Test
  void rejectsMissingRunEventStreamBeforeStartingAsync() throws Exception {
    mockMvc.perform(get("/v1/agent-runs/run_missing/events/stream")
            .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isNotFound())
        .andExpect(request().asyncNotStarted());
  }

  @Test
  void keepsJsonErrorEnvelopeForRunEventListFailures() throws Exception {
    String workspaceId = createWorkspace();
    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "event answer",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    pollRun(runId, "SUCCEEDED");

    mockMvc.perform(get("/v1/agent-runs/{runId}/events", runId)
            .accept(MediaType.APPLICATION_JSON)
            .header("X-Dev-User-Id", "user_without_workspace_access")
            .header("X-Dev-Team-Id", "team_dev"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

    mockMvc.perform(get("/v1/agent-runs/run_missing/events")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("AGENT_RUN_NOT_FOUND"));
  }

  private String createWorkspace() throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Run Event Workspace",
                  "defaultBranch": "main"
                }
                """))
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
  static class RunEventControllerTestConfig {

    @Bean
    @Primary
    AgentWorker agentWorker() {
      return WORKER;
    }
  }

  private void assertNoCompletedEventAppears(String runId) throws Exception {
    long deadline = System.nanoTime() + Duration.ofMillis(500).toNanos();
    while (System.nanoTime() < deadline) {
      MvcResult eventsResult = mockMvc.perform(get("/v1/agent-runs/{runId}/events", runId))
          .andExpect(status().isOk())
          .andReturn();
      String body = eventsResult.getResponse().getContentAsString();
      assertThat(body).doesNotContain("\"eventType\":\"COMPLETED\"");
      Thread.sleep(50);
    }
  }

  private static final class StaleRecoveryWorker implements AgentWorker {

    private CountDownLatch started;
    private CountDownLatch release;
    private CountDownLatch returned;

    void reset() {
      started = new CountDownLatch(1);
      release = new CountDownLatch(1);
      returned = new CountDownLatch(1);
    }

    boolean awaitStarted() throws InterruptedException {
      return started.await(2, TimeUnit.SECONDS);
    }

    void release() {
      release.countDown();
    }

    boolean awaitReturned() throws InterruptedException {
      return returned.await(2, TimeUnit.SECONDS);
    }

    @Override
    public AgentWorkerResponse run(AgentWorkerRequest request) {
      if (!"stale event".equals(request.userMessage()) && !"stream event".equals(request.userMessage())) {
        return responseFor(request.runId(), "Event answer.");
      }
      started.countDown();
      try {
        if (!release.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("worker was not released");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("worker interrupted", exception);
      } finally {
        returned.countDown();
      }
      return responseFor(request.runId(), "Late success.");
    }

    private static AgentWorkerResponse responseFor(String runId, String displayText) {
      return new AgentWorkerResponse(
          "agent-backend-response.v1",
          runId,
          "SUCCEEDED",
          "answer",
          displayText,
          false,
          false,
          List.of(".agent-runs/%s/artifact.json".formatted(runId)),
          false,
          List.of()
      );
    }
  }
}
