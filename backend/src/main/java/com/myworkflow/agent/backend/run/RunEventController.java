package com.myworkflow.agent.backend.run;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import com.myworkflow.agent.backend.workspace.WorkspaceForbiddenException;
import com.myworkflow.agent.backend.workspace.WorkspaceNotFoundException;
import java.time.Instant;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class RunEventController {

  private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration STREAM_POLL_INTERVAL = Duration.ofMillis(50);

  private final RunEventService runEventService;

  public RunEventController(RunEventService runEventService) {
    this.runEventService = runEventService;
  }

  @GetMapping("/v1/agent-runs/{runId}/events")
  public ApiEnvelope<List<RunEventResponse>> listRunEvents(@PathVariable String runId) {
    return ApiEnvelope.ok(runEventService.listRunEvents(runId).stream()
        .map(RunEventController::toResponse)
        .toList());
  }

  @GetMapping(
      path = "/v1/agent-runs/{runId}/events/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE
  )
  public ResponseEntity<SseEmitter> streamRunEvents(
      @PathVariable String runId,
      @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId
  ) {
    RunEventService.AuthorizedRunEventStream stream;
    try {
      stream = runEventService.authorizeRunEventStream(runId);
    } catch (WorkspaceForbiddenException exception) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    } catch (WorkspaceNotFoundException | AgentRunNotFoundException exception) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
    CompletableFuture.runAsync(() -> emitRunEvents(stream, emitter, normalizeLastEventId(lastEventId)));
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .body(emitter);
  }

  private void emitRunEvents(
      RunEventService.AuthorizedRunEventStream stream,
      SseEmitter emitter,
      String lastEventId
  ) {
    Set<String> emittedEventIds = new HashSet<>();
    List<RunEventRecord> initialEvents = runEventService.listAuthorizedRunEvents(stream);
    boolean replayCursorReached = lastEventId == null || initialEvents.stream()
        .noneMatch((event) -> event.eventId().equals(lastEventId));
    boolean useInitialEvents = true;
    long deadline = System.nanoTime() + STREAM_TIMEOUT.toNanos();
    try {
      while (System.nanoTime() < deadline) {
        boolean sawClosingEvent = false;
        List<RunEventRecord> events = useInitialEvents
            ? initialEvents
            : runEventService.listAuthorizedRunEvents(stream);
        useInitialEvents = false;
        for (RunEventRecord event : events) {
          if (isStreamClosingStatus(event.status())) {
            sawClosingEvent = true;
          }
          if (!replayCursorReached) {
            emittedEventIds.add(event.eventId());
            replayCursorReached = event.eventId().equals(lastEventId);
            continue;
          }
          if (emittedEventIds.add(event.eventId())) {
            emitter.send(SseEmitter.event()
                .id(event.eventId())
                .name(event.eventType())
                .data(toResponse(event)));
          }
        }
        if (sawClosingEvent) {
          emitter.complete();
          return;
        }
        Thread.sleep(STREAM_POLL_INTERVAL.toMillis());
      }
      emitter.complete();
    } catch (Exception exception) {
      emitter.completeWithError(exception);
    }
  }

  private static String normalizeLastEventId(String lastEventId) {
    if (lastEventId == null || lastEventId.isBlank()) {
      return null;
    }
    return lastEventId.trim();
  }

  private static boolean isStreamClosingStatus(AgentRunStatus status) {
    return status != AgentRunStatus.QUEUED && status != AgentRunStatus.RUNNING;
  }

  private static RunEventResponse toResponse(RunEventRecord event) {
    return new RunEventResponse(
        event.eventId(),
        event.runId(),
        event.eventType(),
        event.status(),
        event.message(),
        event.createdAt()
    );
  }

  public record RunEventResponse(
      String eventId,
      String runId,
      String eventType,
      AgentRunStatus status,
      String message,
      Instant createdAt
  ) {
  }
}
