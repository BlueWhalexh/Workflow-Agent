package com.myworkflow.agent.backend.run;

public class AgentWorkerException extends RuntimeException {

  public AgentWorkerException(String message) {
    super(message);
  }

  public AgentWorkerException(String message, Throwable cause) {
    super(message, cause);
  }
}
