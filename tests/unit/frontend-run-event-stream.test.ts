import { describe, expect, test } from "vitest";
import { streamRunEvents } from "../../frontend/src/features/runs/run-event-stream.js";

describe("frontend run event stream adapter", () => {
  test("streamRunEvents reads public SSE events and forwards Last-Event-ID", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    const seenEvents: unknown[] = [];
    const fetcher = async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      return sseResponse([
        sseEvent({
          id: "evt_1",
          event: "RUNNING",
          data: {
            eventId: "evt_1",
            runId: "run_123",
            eventType: "RUNNING",
            status: "RUNNING",
            message: "Worker attempt running",
            createdAt: "2026-06-17T10:00:01Z",
            rawProviderPayload: "must-not-render",
          },
        }),
        sseEvent({
          id: "evt_2",
          event: "COMPLETED",
          data: {
            eventId: "evt_2",
            runId: "run_123",
            eventType: "COMPLETED",
            status: "SUCCEEDED",
            message: "Worker response recorded",
            createdAt: "2026-06-17T10:00:02Z",
            runtime: {
              workspaceRoot: "/Users/didi/private/workspace",
            },
          },
        }),
      ]);
    };

    const events = await streamRunEvents(fetcher, "run_123", {
      lastEventId: "evt_0",
      onEvent: (event) => {
        seenEvents.push(event);
      },
    });

    expect(events).toEqual([
      {
        eventId: "evt_1",
        runId: "run_123",
        eventType: "RUNNING",
        status: "RUNNING",
        message: "Worker attempt running",
        createdAt: "2026-06-17T10:00:01Z",
      },
      {
        eventId: "evt_2",
        runId: "run_123",
        eventType: "COMPLETED",
        status: "SUCCEEDED",
        message: "Worker response recorded",
        createdAt: "2026-06-17T10:00:02Z",
      },
    ]);
    expect(seenEvents).toEqual(events);
    expect(calls).toEqual([
      {
        url: "/v1/agent-runs/run_123/events/stream",
        init: {
          credentials: "include",
          headers: {
            Accept: "text/event-stream",
            "Last-Event-ID": "evt_0",
          },
        },
      },
    ]);
    expect(JSON.stringify(events)).not.toContain("rawProviderPayload");
    expect(JSON.stringify(events)).not.toContain("/Users/didi/private/workspace");
  });
});

function sseResponse(chunks: string[]) {
  return Promise.resolve(
    new Response(
      new ReadableStream({
        start(controller) {
          const encoder = new TextEncoder();
          for (const chunk of chunks) {
            controller.enqueue(encoder.encode(chunk));
          }
          controller.close();
        },
      }),
      {
        status: 200,
        headers: {
          "content-type": "text/event-stream",
        },
      },
    ),
  );
}

function sseEvent(input: { id: string; event: string; data: unknown }) {
  return `id:${input.id}\nevent:${input.event}\ndata:${JSON.stringify(input.data)}\n\n`;
}
