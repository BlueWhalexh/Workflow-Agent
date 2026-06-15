package com.myworkflow.agent.backend.run;

public interface AgentWorker {

  default String workerKind() {
    return "LOCAL_TS_WORKER";
  }

  AgentWorkerResponse run(AgentWorkerRequest request);
}
