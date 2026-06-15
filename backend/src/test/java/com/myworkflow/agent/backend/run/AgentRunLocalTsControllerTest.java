package com.myworkflow.agent.backend.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import com.myworkflow.agent.backend.workspace.WorkspaceService;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = BackendApplication.class,
    properties = {
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-run-local-ts-test"
    }
)
@AutoConfigureMockMvc
class AgentRunLocalTsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private WorkspaceService workspaceService;

  @Test
  void javaApiRunsLocalTsWorkerAndPollsBackendEnvelope() throws Exception {
    String workspaceId = createWorkspace("Local TS Worker");
    Path repoRoot = Path.of("..").toAbsolutePath().normalize();
    TestWorkspaceCopier.copy(
        repoRoot.resolve("tests/fixtures/workspaces/basic-raw-mirror"),
        workspaceService.resolveContentPath(workspaceId, "")
    );

    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "总结当前知识库",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();

    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    MvcResult completed = pollRun(runId, "SUCCEEDED");
    String body = completed.getResponse().getContentAsString();
    assertThat((String) JsonPath.read(body, "$.data.outputKind")).isEqualTo("answer");
    assertThat((String) JsonPath.read(body, "$.data.displayText")).contains("Sources:");
    assertThat((Boolean) JsonPath.read(body, "$.data.wroteWorkspace")).isFalse();
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
    long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
    while (System.nanoTime() < deadline) {
      MvcResult result = mockMvc.perform(get("/v1/agent-runs/{runId}", runId))
          .andExpect(status().isOk())
          .andReturn();
      String status = JsonPath.read(result.getResponse().getContentAsString(), "$.data.status");
      if (expectedStatus.equals(status)) {
        return result;
      }
      lastError = new AssertionError("Expected status %s but was %s".formatted(expectedStatus, status));
      Thread.sleep(100);
    }
    throw lastError == null ? new AssertionError("Run was not found") : lastError;
  }
}
