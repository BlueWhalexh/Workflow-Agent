import { describe, expect, it } from "vitest";
import { internalTools, publiclyExposedToolNames } from "../../src/tools/internal-tool-registry.js";

describe("internal tool registry", () => {
  it("classifies workflow tools by risk", () => {
    expect(internalTools.map((tool) => tool.name)).toContain("workspace.scan");
    expect(internalTools.map((tool) => tool.name)).toContain("patch.validate");
    expect(internalTools.find((tool) => tool.name === "patch.publish")?.risk).toBe("WORKSPACE_WRITE");
  });

  it("does not expose unsafe workspace write tools publicly", () => {
    expect(publiclyExposedToolNames).toContain("workspace.scan");
    expect(publiclyExposedToolNames).not.toContain("patch.publish");
    expect(publiclyExposedToolNames).not.toContain("provider.rawEnvelope.write");
  });
});
