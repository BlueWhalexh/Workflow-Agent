package com.myworkflow.agent.backend.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import com.myworkflow.agent.backend.run.AgentWorker;
import com.myworkflow.agent.backend.run.AgentWorkerRequest;
import com.myworkflow.agent.backend.run.AgentWorkerResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
        ArtifactControllerTest.ArtifactControllerTestConfig.class
    },
    properties = {
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-artifact-controller-test"
    }
)
@AutoConfigureMockMvc
class ArtifactControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void listsAndReadsRunArtifactsWithoutExposingAbsolutePaths() throws Exception {
    String workspaceId = createWorkspace();
    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "生成 artifact",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    pollRun(runId, "SUCCEEDED");

    MvcResult listResult = mockMvc.perform(get("/v1/agent-runs/{runId}/artifacts", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].artifactId").isString())
        .andExpect(jsonPath("$.data[0].runId").value(runId))
        .andExpect(jsonPath("$.data[0].artifactRef").value(".agent-runs/%s/artifact.json".formatted(runId)))
        .andExpect(jsonPath("$.data[0].absolutePath").doesNotExist())
        .andExpect(jsonPath("$.data[1].kind").value("RAW_PROVIDER"))
        .andExpect(jsonPath("$.data[1].redactionStatus").value("REDACTED"))
        .andReturn();

    String artifactId = JsonPath.read(listResult.getResponse().getContentAsString(), "$.data[0].artifactId");
    String rawArtifactId = JsonPath.read(listResult.getResponse().getContentAsString(), "$.data[1].artifactId");

    mockMvc.perform(get("/v1/artifacts/{artifactId}", artifactId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.artifactRef").value(".agent-runs/%s/artifact.json".formatted(runId)))
        .andExpect(jsonPath("$.data.content").value("{\"ok\":true}"))
        .andExpect(jsonPath("$.data.absolutePath").doesNotExist());

    MvcResult rawRead = mockMvc.perform(get("/v1/artifacts/{artifactId}", rawArtifactId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.redactionStatus").value("REDACTED"))
        .andReturn();
    String rawBody = rawRead.getResponse().getContentAsString();
    assertThat(rawBody).contains("[REDACTED]");
    assertThat(rawBody).doesNotContain("secret-token");
  }

  private String createWorkspace() throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Artifact Workspace",
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
  static class ArtifactControllerTestConfig {

    @Bean
    @Primary
    AgentWorker agentWorker() {
      return (request) -> {
        try {
          Path artifact = Path.of(request.workspaceRoot(), ".agent-runs", request.runId(), "artifact.json");
          Path raw = Path.of(
              request.workspaceRoot(),
              ".agent-runs",
              request.runId(),
              "raw-provider",
              "response.json"
          );
          Files.createDirectories(artifact.getParent());
          Files.createDirectories(raw.getParent());
          Files.writeString(artifact, "{\"ok\":true}");
          Files.writeString(raw, "{\"authorization\":\"[REDACTED]\"}");
        } catch (Exception exception) {
          throw new IllegalStateException("failed to write fake artifact", exception);
        }
        return new AgentWorkerResponse(
            "agent-backend-response.v1",
            request.runId(),
            "SUCCEEDED",
            "answer",
            "Artifact answer",
            false,
            false,
            List.of(
                ".agent-runs/%s/artifact.json".formatted(request.runId()),
                ".agent-runs/%s/raw-provider/response.json".formatted(request.runId())
            ),
            false,
            List.of()
        );
      };
    }
  }
}
