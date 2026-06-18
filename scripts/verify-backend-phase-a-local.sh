#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_BIN="${MAVEN:-/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn}"
RUN_MYSQL_FRONTEND_SMOKE="${RUN_MYSQL_FRONTEND_SMOKE:-1}"

if [[ ! -x "$MAVEN_BIN" ]]; then
  MAVEN_BIN="mvn"
fi

cd "$ROOT_DIR"

echo "== Shell syntax =="
bash -n \
  scripts/dev-mysql-backend.sh \
  scripts/dev-frontend.sh \
  scripts/smoke-local-mysql-frontend-origin.sh \
  scripts/verify-backend-phase-a-local.sh

echo "== Docker compose config =="
docker compose -f docker-compose.local-mysql.yml config >/dev/null

echo "== Backend focused Phase A tests =="
"$MAVEN_BIN" -f backend/pom.xml test \
  -Dtest=OpsIntegrationContractControllerTest,MysqlBackendPhaseAReadinessTest,JdbcRuntimeHandoffControllerTest

echo "== Frontend focused tests =="
npm test -- \
  tests/unit/frontend-api-client.test.ts \
  tests/unit/frontend-run-api.test.ts \
  tests/unit/frontend-assistant-run-session.test.ts \
  tests/unit/frontend-workbench-bootstrap.test.ts

echo "== Typecheck =="
npm run typecheck

echo "== Frontend build =="
npm run frontend:build

if [[ "$RUN_MYSQL_FRONTEND_SMOKE" == "1" ]]; then
  echo "== Local MySQL frontend-origin smoke =="
  ./scripts/smoke-local-mysql-frontend-origin.sh
else
  echo "== Local MySQL frontend-origin smoke skipped =="
fi

echo "== Diff whitespace =="
git diff --check

echo "== Token scan =="
if rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}|ANTHROPIC_AUTH_TOKEN=tp-[A-Za-z0-9]{20,}" \
  scripts/verify-backend-phase-a-local.sh \
  scripts/smoke-local-mysql-frontend-origin.sh \
  docs/architecture/local-mysql-browser-e2e.md \
  docs/reports/runtime-work-item-execution-resume-delivery.md; then
  echo "Token-shaped value found" >&2
  exit 1
fi

echo "Backend Phase A local verification passed"
