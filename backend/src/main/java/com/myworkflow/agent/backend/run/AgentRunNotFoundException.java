package com.myworkflow.agent.backend.run;

public class AgentRunNotFoundException extends RuntimeException {

  public AgentRunNotFoundException(String runId) {
    super("Agent run not found: " + runId);
  }
}
