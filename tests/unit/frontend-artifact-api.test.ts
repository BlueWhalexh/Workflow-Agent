import { describe, expect, test } from "vitest";
import {
  listRunArtifacts,
  readArtifact,
} from "../../frontend/src/features/artifacts/artifact-api.js";

describe("frontend artifact API adapter", () => {
  test("listRunArtifacts maps backend artifact refs without private fields", async () => {
    const fetcher = async (url: string) => {
      expect(url).toBe("/v1/agent-runs/run_123/artifacts");
      return jsonEnvelope([
        {
          artifactId: "art_1",
          runId: "run_123",
          artifactRef: ".agent-runs/run_123/report.md",
          kind: "REPORT",
          redactionStatus: "NOT_REQUIRED",
          contentType: "text/markdown",
          createdAt: "2026-06-17T10:00:00Z",
          absolutePath: "/Users/didi/private/report.md",
          apiKeySecretRef: "secret://must-not-render",
          source: {
            runtimePrivate: true,
          },
        },
      ]);
    };

    const artifacts = await listRunArtifacts(fetcher, "run_123");

    expect(artifacts).toEqual([
      {
        artifactId: "art_1",
        runId: "run_123",
        artifactRef: ".agent-runs/run_123/report.md",
        kind: "REPORT",
        redactionStatus: "NOT_REQUIRED",
        contentType: "text/markdown",
        createdAt: "2026-06-17T10:00:00Z",
      },
    ]);
    expect(JSON.stringify(artifacts)).not.toContain("/Users/didi/private");
    expect(JSON.stringify(artifacts)).not.toContain("secret://must-not-render");
    expect(JSON.stringify(artifacts)).not.toContain("runtimePrivate");
  });

  test("readArtifact maps safe content and redacts server absolute paths", async () => {
    const fetcher = async (url: string) => {
      expect(url).toBe("/v1/artifacts/art_1");
      return jsonEnvelope({
        artifactId: "art_1",
        runId: "run_123",
        artifactRef: ".agent-runs/run_123/report.md",
        kind: "REPORT",
        redactionStatus: "NOT_REQUIRED",
        contentType: "text/markdown",
        createdAt: "2026-06-17T10:00:00Z",
        content: "Report stored at /Users/didi/private/workspace/report.md",
        rawProviderPayload: {
          token: "must-not-render",
        },
      });
    };

    const artifact = await readArtifact(fetcher, "art_1");

    expect(artifact).toEqual({
      artifactId: "art_1",
      runId: "run_123",
      artifactRef: ".agent-runs/run_123/report.md",
      kind: "REPORT",
      redactionStatus: "NOT_REQUIRED",
      contentType: "text/markdown",
      createdAt: "2026-06-17T10:00:00Z",
      content: "Report stored at [redacted-path]",
    });
    expect(JSON.stringify(artifact)).not.toContain("/Users/didi/private");
    expect(JSON.stringify(artifact)).not.toContain("must-not-render");
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
