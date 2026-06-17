package com.myworkflow.agent.backend.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myworkflow.agent.backend.runner.RemoteRunnerRecord;
import com.myworkflow.agent.backend.runner.RemoteRunnerRepository;
import com.myworkflow.agent.backend.runner.RemoteRunnerStatus;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class RemoteRunnerDispatchService {

  private static final String REQUIRED_BACKEND_RESPONSE_CAPABILITY = "agent-backend-response.v1";

  private final ObjectProvider<RemoteRunnerRepository> remoteRunnerRepositoryProvider;
  private final ObjectMapper objectMapper;
  private final long timeoutMs;
  private final String signatureSecret;
  private final Environment environment;

  public RemoteRunnerDispatchService(
      ObjectProvider<RemoteRunnerRepository> remoteRunnerRepositoryProvider,
      ObjectMapper objectMapper,
      @Value("${my-workflow.backend.agent-worker.remote-http.timeout-ms:60000}") long timeoutMs,
      @Value("${my-workflow.backend.agent-worker.remote-http.signature-secret:}") String signatureSecret,
      Environment environment
  ) {
    this.remoteRunnerRepositoryProvider = remoteRunnerRepositoryProvider;
    this.objectMapper = objectMapper;
    this.timeoutMs = timeoutMs;
    this.signatureSecret = signatureSecret;
    this.environment = environment;
  }

  public Optional<AgentWorker> resolveWorker(String workspaceId, String remoteRunnerRef) {
    String normalizedRef = normalizeOptionalRef(remoteRunnerRef);
    if (normalizedRef == null) {
      return Optional.empty();
    }
    RemoteRunnerRepository repository = remoteRunnerRepositoryProvider.getIfAvailable();
    if (repository == null) {
      throw new IllegalArgumentException("Remote runner dispatch requires JDBC remote runner registry");
    }
    RemoteRunnerRecord runner = repository.findByWorkspaceAndRunnerRef(workspaceId, normalizedRef)
        .orElseThrow(() -> new IllegalArgumentException("Remote runner not found"));
    requireOnlineRunner(runner);
    requireBackendResponseCapability(runner);
    return Optional.of(new RemoteHttpAgentWorker(
        objectMapper,
        runner.endpointUrl(),
        timeoutMs,
        signatureSecret,
        Arrays.asList(environment.getActiveProfiles())
    ));
  }

  private static void requireOnlineRunner(RemoteRunnerRecord runner) {
    if (runner.status() != RemoteRunnerStatus.ONLINE) {
      throw new IllegalArgumentException("Remote runner is not online");
    }
  }

  private static void requireBackendResponseCapability(RemoteRunnerRecord runner) {
    if (runner.capabilities() == null
        || !runner.capabilities().contains(REQUIRED_BACKEND_RESPONSE_CAPABILITY)) {
      throw new IllegalArgumentException("Remote runner does not support backend response dispatch");
    }
  }

  private static String normalizeOptionalRef(String remoteRunnerRef) {
    if (remoteRunnerRef == null || remoteRunnerRef.isBlank()) {
      return null;
    }
    return remoteRunnerRef.trim();
  }
}
