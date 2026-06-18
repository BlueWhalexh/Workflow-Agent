#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

BACKEND_PORT="${MY_WORKFLOW_BACKEND_PORT:-18081}"
FRONTEND_PORT="${MY_WORKFLOW_FRONTEND_PORT:-5173}"
MYSQL_PORT="${MY_WORKFLOW_MYSQL_PORT:-3308}"
LOG_DIR="${MY_WORKFLOW_SMOKE_LOG_DIR:-/private/tmp/my-workflow-agent-local-mysql-smoke}"
MAVEN_BIN="${MAVEN:-/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn}"

if [[ ! -x "$MAVEN_BIN" ]]; then
  MAVEN_BIN="mvn"
fi

mkdir -p "$LOG_DIR"

BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
  if [[ -n "$FRONTEND_PID" ]] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
    kill "$FRONTEND_PID" 2>/dev/null || true
    wait "$FRONTEND_PID" 2>/dev/null || true
  fi
  if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
  MY_WORKFLOW_MYSQL_PORT="$MYSQL_PORT" docker compose -f "$ROOT_DIR/docker-compose.local-mysql.yml" stop mysql >/dev/null 2>&1 || true
}
trap cleanup EXIT

json_field() {
  node -e '
const fs = require("fs");
const path = process.argv[1].split(".");
let value = JSON.parse(fs.readFileSync(0, "utf8"));
for (const key of path) value = value?.[key];
if (Array.isArray(value) || (value && typeof value === "object")) {
  console.log(JSON.stringify(value));
} else {
  console.log(value ?? "");
}
' "$1"
}

wait_for_url() {
  local url="$1"
  local label="$2"
  local deadline=$((SECONDS + 90))

  until curl -fsS "$url" >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      echo "Timed out waiting for $label at $url" >&2
      echo "--- backend log ---" >&2
      tail -80 "$LOG_DIR/backend.log" 2>/dev/null >&2 || true
      echo "--- frontend log ---" >&2
      tail -80 "$LOG_DIR/frontend.log" 2>/dev/null >&2 || true
      exit 1
    fi
    sleep 1
  done
}

post_json() {
  local url="$1"
  local body="$2"
  curl -fsS -X POST "$url" -H "Content-Type: application/json" -d "$body"
}

cd "$ROOT_DIR"

echo "Starting local MySQL on port $MYSQL_PORT"
MY_WORKFLOW_MYSQL_PORT="$MYSQL_PORT" docker compose -f docker-compose.local-mysql.yml up -d mysql >/dev/null

echo "Starting Java backend on port $BACKEND_PORT"
MY_WORKFLOW_MYSQL_PORT="$MYSQL_PORT" \
MY_WORKFLOW_BACKEND_PORT="$BACKEND_PORT" \
MAVEN="$MAVEN_BIN" \
./scripts/dev-mysql-backend.sh >"$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID="$!"
wait_for_url "http://127.0.0.1:$BACKEND_PORT/ready" "backend readiness"

echo "Starting Vite frontend on port $FRONTEND_PORT"
MY_WORKFLOW_BACKEND_PORT="$BACKEND_PORT" \
MY_WORKFLOW_FRONTEND_PORT="$FRONTEND_PORT" \
./scripts/dev-frontend.sh >"$LOG_DIR/frontend.log" 2>&1 &
FRONTEND_PID="$!"
wait_for_url "http://127.0.0.1:$FRONTEND_PORT/" "frontend"

API_ORIGIN="http://127.0.0.1:$FRONTEND_PORT"

CONTRACT_JSON="$(curl -fsS "$API_ORIGIN/v1/ops/integration-contract")"
CONTRACT_SCHEMA="$(printf '%s' "$CONTRACT_JSON" | json_field data.schemaVersion)"
if [[ "$CONTRACT_SCHEMA" != "java-backend-integration-contract.v1" ]]; then
  echo "Unexpected integration contract schema: $CONTRACT_SCHEMA" >&2
  exit 1
fi

WORKSPACE_NAME="Local MySQL Smoke $(date -u +%Y%m%dT%H%M%SZ)"
WORKSPACE_JSON="$(post_json "$API_ORIGIN/v1/workspaces" "{\"name\":\"$WORKSPACE_NAME\",\"defaultBranch\":\"main\"}")"
WORKSPACE_ID="$(printf '%s' "$WORKSPACE_JSON" | json_field data.workspaceId)"
if [[ -z "$WORKSPACE_ID" ]]; then
  echo "Workspace creation did not return workspaceId" >&2
  exit 1
fi

RUN_JSON="$(post_json "$API_ORIGIN/v1/workspaces/$WORKSPACE_ID/agent-runs" '{"userMessage":"本地 MySQL frontend-origin smoke 验证 recent run artifact preview","mode":"deterministic-open-agent","execute":false,"autoApprove":false}')"
RUN_ID="$(printf '%s' "$RUN_JSON" | json_field data.runId)"
if [[ -z "$RUN_ID" ]]; then
  echo "Run creation did not return runId" >&2
  exit 1
fi

STATUS=""
for _ in {1..60}; do
  RUN_JSON="$(curl -fsS "$API_ORIGIN/v1/agent-runs/$RUN_ID")"
  STATUS="$(printf '%s' "$RUN_JSON" | json_field data.status)"
  case "$STATUS" in
    SUCCEEDED|FAILED|CANCELLED|WAITING_APPROVAL) break ;;
  esac
  sleep 1
done

if [[ "$STATUS" != "SUCCEEDED" ]]; then
  echo "Expected run $RUN_ID to SUCCEED, got $STATUS" >&2
  exit 1
fi

EVENTS_JSON="$(curl -fsS "$API_ORIGIN/v1/agent-runs/$RUN_ID/events")"
RECENT_JSON="$(curl -fsS "$API_ORIGIN/v1/workspaces/$WORKSPACE_ID/agent-runs")"
ARTIFACTS_JSON="$(curl -fsS "$API_ORIGIN/v1/agent-runs/$RUN_ID/artifacts")"
ARTIFACT_ID="$(printf '%s' "$ARTIFACTS_JSON" | json_field data.0.artifactId)"
ARTIFACT_REF="$(printf '%s' "$ARTIFACTS_JSON" | json_field data.0.artifactRef)"

if [[ -z "$ARTIFACT_ID" || -z "$ARTIFACT_REF" ]]; then
  echo "Expected run artifact registry entry for $RUN_ID" >&2
  exit 1
fi

ARTIFACT_JSON="$(curl -fsS "$API_ORIGIN/v1/artifacts/$ARTIFACT_ID")"
FIRST_RECENT_RUN_ID="$(printf '%s' "$RECENT_JSON" | json_field data.0.runId)"
WROTE_WORKSPACE="$(printf '%s' "$RUN_JSON" | json_field data.wroteWorkspace)"

if [[ "$FIRST_RECENT_RUN_ID" != "$RUN_ID" ]]; then
  echo "Expected newest recent run $RUN_ID, got $FIRST_RECENT_RUN_ID" >&2
  exit 1
fi

if [[ "$WROTE_WORKSPACE" != "false" ]]; then
  echo "Expected wroteWorkspace=false, got $WROTE_WORKSPACE" >&2
  exit 1
fi

if printf '%s\n%s\n%s\n%s\n%s\n' "$RUN_JSON" "$EVENTS_JSON" "$RECENT_JSON" "$ARTIFACTS_JSON" "$ARTIFACT_JSON" \
  | rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}|ANTHROPIC_AUTH_TOKEN=tp-[A-Za-z0-9]{20,}" >/dev/null; then
  echo "Token-shaped value leaked into smoke API readback" >&2
  exit 1
fi

echo "Local MySQL frontend-origin smoke passed"
echo "workspaceId=$WORKSPACE_ID"
echo "runId=$RUN_ID"
echo "status=$STATUS"
echo "artifactRef=$ARTIFACT_REF"
echo "wroteWorkspace=$WROTE_WORKSPACE"
echo "logs=$LOG_DIR"
