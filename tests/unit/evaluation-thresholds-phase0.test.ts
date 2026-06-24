import { describe, expect, it } from "vitest";
import { readFile } from "node:fs/promises";
import { join } from "node:path";

const thresholdsPath = join(process.cwd(), "docs/evidence/mvp-release-evaluation-thresholds.yaml");
const entropyPath = join(process.cwd(), "fixtures/demo-vault/_golden/entropy.json");

function metricBlocks(yaml: string): string[] {
  return yaml
    .split(/\n(?=  - id: )/g)
    .filter((block) => block.startsWith("  - id: "));
}

function metricBlock(yaml: string, id: string): string {
  const block = metricBlocks(yaml).find((candidate) => candidate.startsWith(`  - id: ${id}\n`));
  if (!block) {
    throw new Error(`Missing threshold metric ${id}`);
  }
  return block;
}

describe("Phase 0 evaluation thresholds schema lock", () => {
  it("locks 28 metric entries and moves engine-dependent baselines to TBD@P6", async () => {
    const yaml = await readFile(thresholdsPath, "utf8");
    const blocks = metricBlocks(yaml);

    expect(blocks).toHaveLength(28);
    expect(yaml).not.toContain("TBD@P0");
    expect(yaml).toContain("status: phase0-schema-locked");

    for (const block of blocks) {
      expect(block).toMatch(/\n    name: /);
      expect(block).toMatch(/\n    definition: /);
      expect(block).toMatch(/\n    measurement: /);
      expect(block).toMatch(/\n    threshold_type: /);
      expect(block).toMatch(/\n    blocker: /);
    }
  });

  it("fills non-engine baseline metrics from the Demo Vault fixture", async () => {
    const yaml = await readFile(thresholdsPath, "utf8");
    const entropy = JSON.parse(await readFile(entropyPath, "utf8")) as {
      preOrganization: { score: number };
    };

    expect(metricBlock(yaml, "O5")).toContain("pass_condition: 1.0");
    expect(metricBlock(yaml, "S2")).toContain("pass_condition: 0");
    expect(metricBlock(yaml, "S4")).toContain("pass_condition: 0");
    expect(metricBlock(yaml, "O7")).toContain(`pre_organization_score: ${entropy.preOrganization.score}`);
    expect(metricBlock(yaml, "O7")).toContain(`threshold: ${entropy.preOrganization.score / 2}`);
  });
});
