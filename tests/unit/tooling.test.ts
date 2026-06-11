import { describe, expect, it } from "vitest";
import { projectName } from "../../src/index.js";

describe("project tooling", () => {
  it("loads TypeScript source through Vitest", () => {
    expect(projectName).toBe("my-workflow-agent-loop-core");
  });
});
