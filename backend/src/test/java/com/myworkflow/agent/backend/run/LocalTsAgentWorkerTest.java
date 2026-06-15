package com.myworkflow.agent.backend.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LocalTsAgentWorkerTest {

  @Test
  void invokesBackendAgentCliAndIgnoresSourcePayload() throws Exception {
    Path repoRoot = Path.of("..").toAbsolutePath().normalize();
    Path workspaceRoot = Files.createTempDirectory("local-ts-agent-worker-");
    TestWorkspaceCopier.copy(
        repoRoot.resolve("tests/fixtures/workspaces/basic-raw-mirror"),
        workspaceRoot
    );

    LocalTsAgentWorker worker = new LocalTsAgentWorker(
        new ObjectMapper(),
        "node",
        repoRoot,
        60_000
    );

    AgentWorkerResponse response = worker.run(new AgentWorkerRequest(
        "local-ts-worker-answer",
        workspaceRoot.toString(),
        "总结当前知识库",
        "deterministic-open-agent",
        false,
        false,
        null
    ));

    assertThat(response.schemaVersion()).isEqualTo("agent-backend-response.v1");
    assertThat(response.runId()).isEqualTo("local-ts-worker-answer");
    assertThat(response.status()).isEqualTo("SUCCEEDED");
    assertThat(response.outputKind()).isEqualTo("answer");
    assertThat(response.displayText()).contains("Sources:");
    assertThat(Arrays.stream(AgentWorkerResponse.class.getRecordComponents())
        .map(component -> component.getName()))
        .doesNotContain("source");
  }
}
