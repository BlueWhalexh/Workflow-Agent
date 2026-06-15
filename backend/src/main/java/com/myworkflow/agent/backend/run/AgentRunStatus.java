package com.myworkflow.agent.backend.run;

public enum AgentRunStatus {
  CREATED,
  QUEUED,
  RUNNING,
  WAITING_CONFIRMATION,
  WAITING_APPROVAL,
  SUCCEEDED,
  SUCCEEDED_WITH_WARNINGS,
  FAILED,
  CANCELED
}
