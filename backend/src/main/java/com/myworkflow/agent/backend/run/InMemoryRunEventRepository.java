package com.myworkflow.agent.backend.run;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!jdbc")
public class InMemoryRunEventRepository implements RunEventRepository {

  private final List<RunEventRecord> events = new ArrayList<>();

  @Override
  public synchronized RunEventRecord append(
      String runId,
      String eventType,
      AgentRunStatus status,
      String message,
      Instant createdAt
  ) {
    RunEventRecord event = new RunEventRecord(
        "evt_" + UUID.randomUUID().toString().replace("-", ""),
        runId,
        eventType,
        status,
        message,
        createdAt
    );
    events.add(event);
    return event;
  }

  @Override
  public synchronized List<RunEventRecord> findByRunId(String runId) {
    return events.stream()
        .filter((event) -> event.runId().equals(runId))
        .toList();
  }
}
