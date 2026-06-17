package com.myworkflow.agent.backend.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
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

  @Test
  void injectsSecretEnvironmentWithoutSerializingWorkerRequest() throws Exception {
    Path repoRoot = Path.of("..").toAbsolutePath().normalize();
    Path fakeWorker = Files.createTempFile("fake-local-ts-agent-worker-", ".mjs");
    Files.writeString(fakeWorker, """
        #!/usr/bin/env node
        let input = "";
        process.stdin.setEncoding("utf8");
        process.stdin.on("data", (chunk) => input += chunk);
        process.stdin.on("end", () => {
          const request = JSON.parse(input);
          if (process.env.PROVIDER_CREDENTIAL_API_KEY !== "test-local-worker-secret") {
            process.stderr.write("missing injected secret env");
            process.exit(2);
          }
          if (input.includes("test-local-worker-secret")) {
            process.stderr.write("secret leaked into worker request json");
            process.exit(3);
          }
          process.stdout.write(JSON.stringify({
            schemaVersion: "agent-backend-response.v1",
            runId: request.runId,
            status: "SUCCEEDED",
            outputKind: "answer",
            displayText: "secret env injected without request serialization",
            requiresConfirmation: false,
            requiresApproval: false,
            artifactRefs: [],
            wroteWorkspace: false,
            targetWorkspacePaths: []
          }));
        });
        """);
    fakeWorker.toFile().setExecutable(true);

    LocalTsAgentWorker worker = new LocalTsAgentWorker(
        new ObjectMapper(),
        fakeWorker.toString(),
        repoRoot,
        10_000
    );

    AgentWorkerResponse response = worker.run(new AgentWorkerRequest(
        "local-ts-worker-secret-injection",
        repoRoot.toString(),
        "测试 secret 注入",
        "llm-open-agent",
        false,
        false,
        Map.of(
            "provider", "mimo-real",
            "apiKeyEnvName", "PROVIDER_CREDENTIAL_API_KEY"
        )
    ), new AgentWorkerSecretInjection(Map.of(
        "PROVIDER_CREDENTIAL_API_KEY", "test-local-worker-secret"
    )));

    assertThat(response.schemaVersion()).isEqualTo("agent-backend-response.v1");
    assertThat(response.runId()).isEqualTo("local-ts-worker-secret-injection");
    assertThat(response.displayText()).isEqualTo("secret env injected without request serialization");
  }
}
