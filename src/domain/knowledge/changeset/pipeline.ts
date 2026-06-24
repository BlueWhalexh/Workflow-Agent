import type { KnowledgeChangeset, NotePath } from "../../../runtime/core/types.js";
import {
  validateKnowledgeChangeset,
  type DeterministicRuleset
} from "../validator/ruleset-validator.js";
import type { KnowledgeChangesetRepository } from "./store.js";

export interface ProposeKnowledgeChangesetInput {
  repository: KnowledgeChangesetRepository;
  changeset: KnowledgeChangeset;
  ruleset: DeterministicRuleset;
  existingNotePaths: Iterable<NotePath>;
}

export async function proposeKnowledgeChangeset(
  input: ProposeKnowledgeChangesetInput
): Promise<KnowledgeChangeset> {
  const validatorReport = validateKnowledgeChangeset(input.changeset, {
    ruleset: input.ruleset,
    existingNotePaths: input.existingNotePaths
  });
  const next: KnowledgeChangeset = {
    ...input.changeset,
    status: validatorReport.passed ? "AWAITING_APPROVAL" : "REJECTED_BY_VALIDATOR",
    validatorReport
  };
  return input.repository.save(next);
}
