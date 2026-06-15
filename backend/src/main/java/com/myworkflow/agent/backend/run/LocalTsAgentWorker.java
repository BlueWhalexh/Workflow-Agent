package com.myworkflow.agent.backend.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "my-workflow.backend.agent-worker.kind",
    havingValue = "local-ts",
    matchIfMissing = true
)
public class LocalTsAgentWorker implements AgentWorker {

  private final ObjectMapper objectMapper;
  private final String nodeCommand;
  private final Path repoRoot;
  private final long timeoutMs;

  @Autowired
  public LocalTsAgentWorker(
      ObjectMapper objectMapper,
      @Value("${my-workflow.backend.agent-worker.node-command:node}") String nodeCommand,
      @Value("${my-workflow.backend.agent-worker.repo-root:}") String repoRoot,
      @Value("${my-workflow.backend.agent-worker.timeout-ms:60000}") long timeoutMs
  ) {
    this(objectMapper, nodeCommand, resolveRepoRoot(repoRoot), timeoutMs);
  }

  LocalTsAgentWorker(ObjectMapper objectMapper, String nodeCommand, Path repoRoot, long timeoutMs) {
    this.objectMapper = objectMapper;
    this.nodeCommand = nodeCommand;
    this.repoRoot = repoRoot;
    this.timeoutMs = timeoutMs;
  }

  @Override
  public String workerKind() {
    return "LOCAL_TS_WORKER";
  }

  @Override
  public AgentWorkerResponse run(AgentWorkerRequest request) {
    Process process = startProcess();
    CompletableFuture<String> stdout = readAsync(process.getInputStream());
    CompletableFuture<String> stderr = readAsync(process.getErrorStream());
    try (OutputStream stdin = process.getOutputStream()) {
      objectMapper.writeValue(stdin, request);
    } catch (IOException exception) {
      process.destroyForcibly();
      throw new AgentWorkerException("Unable to write worker request", exception);
    }

    try {
      boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new AgentWorkerException("Agent worker timed out after " + timeoutMs + "ms");
      }
      String stdoutText = stdout.get(5, TimeUnit.SECONDS);
      stderr.get(5, TimeUnit.SECONDS);
      if (process.exitValue() != 0) {
        throw new AgentWorkerException("Agent worker process failed with exit code " + process.exitValue());
      }
      AgentWorkerResponse response = objectMapper.readValue(stdoutText, AgentWorkerResponse.class);
      if (!"agent-backend-response.v1".equals(response.schemaVersion())) {
        throw new AgentWorkerException("Agent worker returned unsupported schema version");
      }
      return response;
    } catch (AgentWorkerException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new AgentWorkerException("Unable to read worker response", exception);
    }
  }

  private Process startProcess() {
    List<String> command = List.of(
        nodeCommand,
        "--import",
        "tsx",
        "src/cli/backend-agent-worker.ts"
    );
    try {
      return new ProcessBuilder(command)
          .directory(repoRoot.toFile())
          .start();
    } catch (IOException exception) {
      throw new AgentWorkerException("Unable to start agent worker process", exception);
    }
  }

  private static CompletableFuture<String> readAsync(InputStream inputStream) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      } catch (IOException exception) {
        throw new AgentWorkerException("Unable to read worker stream", exception);
      }
    });
  }

  private static Path resolveRepoRoot(String configuredRepoRoot) {
    if (configuredRepoRoot != null && !configuredRepoRoot.isBlank()) {
      return Path.of(configuredRepoRoot).toAbsolutePath().normalize();
    }

    Path current = Path.of("").toAbsolutePath().normalize();
    if (isRepoRoot(current)) {
      return current;
    }
    Path parent = current.getParent();
    if (parent != null && isRepoRoot(parent)) {
      return parent;
    }
    throw new AgentWorkerException("Unable to resolve repository root for local TS worker");
  }

  private static boolean isRepoRoot(Path path) {
    return Files.exists(path.resolve("package.json"))
        && Files.exists(path.resolve("src/cli/backend-agent-worker.ts"));
  }
}
