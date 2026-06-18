package com.myworkflow.agent.backend.run;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!jdbc")
public class InMemoryAgentRunRepository implements AgentRunRepository {

  private final Map<String, AgentRunRecord> runs = new LinkedHashMap<>();
  private final Map<String, AgentJobRecord> jobs = new LinkedHashMap<>();
  private final List<RunAttemptRecord> attempts = new ArrayList<>();

  @Override
  public synchronized void create(AgentRunRecord run, AgentJobRecord job) {
    runs.put(run.runId(), run);
    jobs.put(job.jobId(), job);
  }

  @Override
  public synchronized Optional<AgentRunRecord> findRun(String runId) {
    return Optional.ofNullable(runs.get(runId));
  }

  @Override
  public synchronized List<AgentRunRecord> findRunsByWorkspaceId(String workspaceId) {
    return runs.values().stream()
        .filter((run) -> run.workspaceId().equals(workspaceId))
        .sorted((left, right) -> {
          int updated = right.updatedAt().compareTo(left.updatedAt());
          return updated != 0 ? updated : right.runId().compareTo(left.runId());
        })
        .toList();
  }

  @Override
  public synchronized Optional<AgentJobRecord> findJob(String jobId) {
    return Optional.ofNullable(jobs.get(jobId));
  }

  @Override
  public synchronized List<RunAttemptRecord> findAttempts(String runId) {
    return attempts.stream()
        .filter((attempt) -> attempt.runId().equals(runId))
        .toList();
  }

  @Override
  public synchronized void markRunning(String runId, String jobId, String workerKind, Instant now) {
    if (isTerminalRun(runId)) {
      return;
    }
    runs.computeIfPresent(runId, (ignored, run) -> run.withStatus(AgentRunStatus.RUNNING, now));
    jobs.computeIfPresent(jobId, (ignored, job) -> job.running(workerKind, now));
    attempts.add(new RunAttemptRecord(
        attemptId(runId, jobId, jobs.get(jobId).attemptCount()),
        runId,
        jobId,
        workerKind,
        now,
        null,
        AgentJobStatus.RUNNING,
        null
    ));
  }

  @Override
  public synchronized void cancel(String runId, Instant now) {
    runs.computeIfPresent(runId, (ignored, run) -> isCancelable(run.status()) ? run.canceled(now) : run);
    jobs.replaceAll((ignored, job) -> {
      if (!job.runId().equals(runId) || !isCancelable(job.status())) {
        return job;
      }
      return job.completed(AgentJobStatus.CANCELED, now);
    });
    finishOpenAttempts(runId, AgentJobStatus.CANCELED, null, now);
  }

  @Override
  public synchronized void retry(String runId, String jobId, String errorCode, Instant now) {
    if (!isRetryableRun(runId)) {
      return;
    }
    runs.computeIfPresent(runId, (ignored, run) -> run.withStatus(AgentRunStatus.QUEUED, now));
    jobs.computeIfPresent(jobId, (ignored, job) -> job.retry(now));
    finishLatestAttempt(runId, jobId, AgentJobStatus.FAILED, errorCode, now);
  }

  @Override
  public synchronized int failStaleRunningJobs(Instant staleBefore, String errorCode, Instant now) {
    List<AgentJobRecord> staleJobs = jobs.values().stream()
        .filter((job) -> job.status() == AgentJobStatus.RUNNING)
        .filter((job) -> job.lockedUntil() != null && !job.lockedUntil().isAfter(staleBefore))
        .filter((job) -> !isTerminalRun(job.runId()))
        .toList();
    for (AgentJobRecord job : staleJobs) {
      runs.computeIfPresent(job.runId(), (ignored, run) -> run.failed(errorCode, now));
      jobs.computeIfPresent(job.jobId(), (ignored, current) -> current.completed(AgentJobStatus.FAILED, now));
      finishLatestAttempt(job.runId(), job.jobId(), AgentJobStatus.FAILED, errorCode, now);
    }
    return staleJobs.size();
  }

  @Override
  public synchronized void complete(
      String runId,
      String jobId,
      String workerKind,
      AgentWorkerResponse response,
      AgentRunStatus runStatus,
      Instant now
  ) {
    if (isTerminalRun(runId)) {
      return;
    }
    runs.computeIfPresent(runId, (ignored, run) -> run.completed(runStatus, response, now));
    jobs.computeIfPresent(jobId, (ignored, job) -> job.completed(AgentJobStatus.SUCCEEDED, now));
    finishLatestAttempt(runId, jobId, AgentJobStatus.SUCCEEDED, null, now);
  }

  @Override
  public synchronized void fail(String runId, String jobId, String workerKind, String errorCode, Instant now) {
    if (isTerminalRun(runId)) {
      return;
    }
    runs.computeIfPresent(runId, (ignored, run) -> run.failed(errorCode, now));
    jobs.computeIfPresent(jobId, (ignored, job) -> job.completed(AgentJobStatus.FAILED, now));
    finishLatestAttempt(runId, jobId, AgentJobStatus.FAILED, errorCode, now);
  }

  private void finishLatestAttempt(
      String runId,
      String jobId,
      AgentJobStatus status,
      String errorCode,
      Instant now
  ) {
    for (int index = attempts.size() - 1; index >= 0; index--) {
      RunAttemptRecord attempt = attempts.get(index);
      if (attempt.runId().equals(runId) && attempt.jobId().equals(jobId) && attempt.finishedAt() == null) {
        attempts.set(index, new RunAttemptRecord(
            attempt.attemptId(),
            runId,
            jobId,
            attempt.workerKind(),
            attempt.startedAt(),
            now,
            status,
            errorCode
        ));
        return;
      }
    }
  }

  private void finishOpenAttempts(
      String runId,
      AgentJobStatus status,
      String errorCode,
      Instant now
  ) {
    for (int index = 0; index < attempts.size(); index++) {
      RunAttemptRecord attempt = attempts.get(index);
      if (attempt.runId().equals(runId) && attempt.finishedAt() == null) {
        attempts.set(index, new RunAttemptRecord(
            attempt.attemptId(),
            runId,
            attempt.jobId(),
            attempt.workerKind(),
            attempt.startedAt(),
            now,
            status,
            errorCode
        ));
      }
    }
  }

  private boolean isTerminalRun(String runId) {
    return Optional.ofNullable(runs.get(runId))
        .map(AgentRunRecord::status)
        .map(InMemoryAgentRunRepository::isTerminal)
        .orElse(false);
  }

  private boolean isRetryableRun(String runId) {
    return Optional.ofNullable(runs.get(runId))
        .map(AgentRunRecord::status)
        .filter((status) -> status == AgentRunStatus.RUNNING)
        .isPresent();
  }

  private static boolean isCancelable(AgentRunStatus status) {
    return status == AgentRunStatus.CREATED
        || status == AgentRunStatus.QUEUED
        || status == AgentRunStatus.RUNNING;
  }

  private static boolean isCancelable(AgentJobStatus status) {
    return status == AgentJobStatus.QUEUED || status == AgentJobStatus.RUNNING;
  }

  private static boolean isTerminal(AgentRunStatus status) {
    return status == AgentRunStatus.SUCCEEDED
        || status == AgentRunStatus.SUCCEEDED_WITH_WARNINGS
        || status == AgentRunStatus.FAILED
        || status == AgentRunStatus.CANCELED;
  }

  private static String attemptId(String runId, String jobId, int attemptCount) {
    return "attempt_%s_%s_%d".formatted(runId, jobId, attemptCount);
  }
}
