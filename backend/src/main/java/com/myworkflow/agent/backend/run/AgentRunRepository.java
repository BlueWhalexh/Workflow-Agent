package com.myworkflow.agent.backend.run;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AgentRunRepository {

  void create(AgentRunRecord run, AgentJobRecord job);

  Optional<AgentRunRecord> findRun(String runId);

  List<AgentRunRecord> findRunsByWorkspaceId(String workspaceId);

  Optional<AgentJobRecord> findJob(String jobId);

  List<RunAttemptRecord> findAttempts(String runId);

  void markRunning(String runId, String jobId, String workerKind, Instant now);

  void cancel(String runId, Instant now);

  void retry(String runId, String jobId, String errorCode, Instant now);

  int failStaleRunningJobs(Instant staleBefore, String errorCode, Instant now);

  void complete(
      String runId,
      String jobId,
      String workerKind,
      AgentWorkerResponse response,
      AgentRunStatus runStatus,
      Instant now
  );

  void fail(String runId, String jobId, String workerKind, String errorCode, Instant now);
}
