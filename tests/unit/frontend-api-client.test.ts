import { describe, expect, test } from "vitest";
import { ApiClientError, requestApiJson, unwrapApiEnvelope } from "../../frontend/src/shared/api/envelope.js";

describe("frontend API envelope", () => {
  test("unwrapApiEnvelope returns typed data from a stable success envelope", () => {
    const workspace = unwrapApiEnvelope<{ workspaceId: string; name: string }>({
      schemaVersion: "java-backend-api.v1",
      ok: true,
      data: {
        workspaceId: "ws_123",
        name: "Agent Loop Core",
      },
    });

    expect(workspace).toEqual({
      workspaceId: "ws_123",
      name: "Agent Loop Core",
    });
  });

  test("unwrapApiEnvelope throws a normalized ApiClientError for backend failures", () => {
    expect(() =>
      unwrapApiEnvelope({
        schemaVersion: "java-backend-api.v1",
        ok: false,
        error: {
          code: "NOT_FOUND",
          message: "Resource not found",
          retryable: false,
        },
      }),
    ).toThrowError(
      new ApiClientError({
        code: "NOT_FOUND",
        message: "Resource not found",
        retryable: false,
      }),
    );
  });

  test("unwrapApiEnvelope rejects unexpected schema versions before data reaches components", () => {
    expect(() =>
      unwrapApiEnvelope({
        schemaVersion: "legacy-api.v0",
        ok: true,
        data: {
          workspaceId: "ws_123",
        },
      }),
    ).toThrowError(
      new ApiClientError({
        code: "UNSUPPORTED_SCHEMA",
        message: "Unsupported API schema: legacy-api.v0",
        retryable: false,
      }),
    );
  });

  test("unwrapApiEnvelope rejects success envelopes without data", () => {
    expect(() =>
      unwrapApiEnvelope({
        schemaVersion: "java-backend-api.v1",
        ok: true,
      }),
    ).toThrowError(
      new ApiClientError({
        code: "EMPTY_RESPONSE",
        message: "API success envelope did not include data",
        retryable: true,
      }),
    );
  });

  test("requestApiJson fetches a backend envelope and returns typed data", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    const fetcher = async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      return new Response(
        JSON.stringify({
          schemaVersion: "java-backend-api.v1",
          ok: true,
          data: {
            workspaceId: "ws_123",
          },
        }),
        {
          status: 200,
          headers: {
            "content-type": "application/json",
          },
        },
      );
    };

    const data = await requestApiJson<{ workspaceId: string }>(fetcher, "/v1/workspaces/ws_123");

    expect(data).toEqual({ workspaceId: "ws_123" });
    expect(calls).toEqual([
      {
        url: "/v1/workspaces/ws_123",
        init: {
          credentials: "include",
          headers: {
            Accept: "application/json",
          },
        },
      },
    ]);
  });

  test("requestApiJson attaches a backend CSRF token before mutating session requests", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    const fetcher = async (url: string, init?: RequestInit) => {
      calls.push({ url, init });

      if (url === "/v1/session/csrf") {
        return jsonEnvelope({
          token: "csrf_123",
          headerName: "X-CSRF-Token",
        });
      }

      return jsonEnvelope({
        runId: "run_123",
      });
    };

    const data = await requestApiJson<{ runId: string }>(fetcher, "/v1/workspaces/ws_123/agent-runs", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        userMessage: "整理当前页",
      }),
    });

    expect(data).toEqual({ runId: "run_123" });
    expect(calls).toEqual([
      {
        url: "/v1/session/csrf",
        init: {
          credentials: "include",
          headers: {
            Accept: "application/json",
          },
        },
      },
      {
        url: "/v1/workspaces/ws_123/agent-runs",
        init: {
          method: "POST",
          credentials: "include",
          headers: {
            Accept: "application/json",
            "Content-Type": "application/json",
            "X-CSRF-Token": "csrf_123",
          },
          body: JSON.stringify({
            userMessage: "整理当前页",
          }),
        },
      },
    ]);
  });

  test("requestApiJson normalizes non-envelope HTTP failures", async () => {
    const fetcher = async () =>
      new Response("not found", {
        status: 404,
        statusText: "Not Found",
      });

    await expect(requestApiJson(fetcher, "/missing")).rejects.toThrowError(
      new ApiClientError({
        code: "HTTP_404",
        message: "HTTP 404 Not Found",
        retryable: false,
      }),
    );
  });
});

function jsonEnvelope(data: unknown) {
  return Promise.resolve(
    new Response(
      JSON.stringify({
        schemaVersion: "java-backend-api.v1",
        ok: true,
        data,
      }),
      {
        status: 200,
        headers: {
          "content-type": "application/json",
        },
      },
    ),
  );
}
