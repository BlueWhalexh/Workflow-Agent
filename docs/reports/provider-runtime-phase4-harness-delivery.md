# Provider Runtime Phase 4 Harness Delivery Report

## Delivered

- Added optional real provider smoke harness.
- Added `provider-smoke` CLI entry.
- DeepSeek real smoke is guarded by `--execute-real` plus required env.
- Claude Code real smoke reports blocked until SDK wiring exists.
- Missing env path returns `SKIPPED` and does not perform a real external call.

## Required Env For DeepSeek Real Smoke

- `DEEPSEEK_API_KEY`
- `DEEPSEEK_BASE_URL`
- `DEEPSEEK_MODEL`

## How To Run The Safe Skip Path

```bash
node --import tsx src/cli/provider-smoke.ts --provider deepseek-real-smoke
```

Expected result without env:

```json
{
  "provider": "deepseek-real-smoke",
  "status": "SKIPPED",
  "realExternalCall": false,
  "reason": "MISSING_ENV"
}
```

## Real External Call Boundary

Real external call remains unexecuted in this delivery. To run it later, the caller must explicitly provide env and pass:

```bash
node --import tsx src/cli/provider-smoke.ts --provider deepseek-real-smoke --execute-real
```

The normal test suite does not require network, credentials, or real provider availability.

## Verification

- `npm test -- tests/unit/provider-smoke.test.ts`
- `npm run typecheck`
- `git diff --check`

## Review Focus

- No secrets are written to code, fixtures, reports, or test snapshots.
- Safe skip path is deterministic and marks `realExternalCall: false`.
- Real smoke remains outside default tests.
