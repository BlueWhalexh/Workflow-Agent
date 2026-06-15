package com.myworkflow.agent.backend.run;

import java.time.Instant;
import java.util.List;

public interface RunEventRepository {

  RunEventRecord append(
      String runId,
      String eventType,
      AgentRunStatus status,
      String message,
      Instant createdAt
  );

  List<RunEventRecord> findByRunId(String runId);
}
