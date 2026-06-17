package com.myworkflow.agent.backend.run;

public interface AgentWorker {

  default String workerKind() {
    return "LOCAL_TS_WORKER";
  }

  default boolean supportsSecretInjection() {
    return false;
  }

  AgentWorkerResponse run(AgentWorkerRequest request);

  default AgentWorkerResponse run(AgentWorkerRequest request, AgentWorkerSecretInjection secretInjection) {
    if (secretInjection != null && !secretInjection.isEmpty()) {
      throw new AgentWorkerException("Agent worker does not support secret injection");
    }
    return run(request);
  }
}
