import { type ApiFetch, ApiClientError } from "../../shared/api/envelope.js";
import { sanitizeForPublicUi } from "../../shared/safety/public-fields.js";
import type { RunEventView } from "./run-api.js";

type BackendRunEventResponse = {
  eventId: string;
  runId: string;
  eventType: string;
  status: string;
  message: string;
  createdAt: string;
};

export type StreamRunEventsOptions = {
  lastEventId?: string;
  onEvent?: (event: RunEventView) => void | Promise<void>;
};

export async function streamRunEvents(
  fetcher: ApiFetch,
  runId: string,
  options: StreamRunEventsOptions = {},
): Promise<RunEventView[]> {
  const response = await fetcher(`/v1/agent-runs/${encodeURIComponent(runId)}/events/stream`, {
    credentials: "include",
    headers: streamHeaders(options.lastEventId),
  });

  if (!response.ok || !response.body) {
    throw new ApiClientError({
      code: `HTTP_${response.status}`,
      message: `HTTP ${response.status} ${response.statusText}`.trim(),
      retryable: response.status >= 500,
    });
  }

  const events: RunEventView[] = [];
  for await (const payload of readSseData(response.body)) {
    const event = toRunEventView(JSON.parse(payload) as BackendRunEventResponse);
    events.push(event);
    await options.onEvent?.(event);
  }
  return events;
}

function streamHeaders(lastEventId?: string): HeadersInit {
  const headers: Record<string, string> = {
    Accept: "text/event-stream",
  };
  if (lastEventId?.trim()) {
    headers["Last-Event-ID"] = lastEventId.trim();
  }
  return headers;
}

async function* readSseData(stream: ReadableStream<Uint8Array>): AsyncGenerator<string> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    let eventBoundary = buffer.indexOf("\n\n");
    while (eventBoundary >= 0) {
      const rawEvent = buffer.slice(0, eventBoundary);
      buffer = buffer.slice(eventBoundary + 2);
      const data = dataFromRawEvent(rawEvent);
      if (data) {
        yield data;
      }
      eventBoundary = buffer.indexOf("\n\n");
    }
  }

  buffer += decoder.decode();
  const trailingData = dataFromRawEvent(buffer);
  if (trailingData) {
    yield trailingData;
  }
}

function dataFromRawEvent(rawEvent: string): string | null {
  const dataLines = rawEvent
    .split(/\r?\n/)
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice("data:".length));
  return dataLines.length > 0 ? dataLines.join("\n") : null;
}

function toRunEventView(event: BackendRunEventResponse): RunEventView {
  const publicEvent = sanitizeForPublicUi(event) as BackendRunEventResponse;
  return {
    eventId: publicEvent.eventId,
    runId: publicEvent.runId,
    eventType: publicEvent.eventType,
    status: publicEvent.status,
    message: publicEvent.message,
    createdAt: publicEvent.createdAt,
  };
}
