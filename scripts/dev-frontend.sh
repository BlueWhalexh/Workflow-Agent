#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

BACKEND_PORT="${MY_WORKFLOW_BACKEND_PORT:-18081}"
FRONTEND_PORT="${MY_WORKFLOW_FRONTEND_PORT:-5173}"

export MY_WORKFLOW_BACKEND_URL="${MY_WORKFLOW_BACKEND_URL:-http://127.0.0.1:${BACKEND_PORT}}"

cd "$ROOT_DIR"
exec npm run frontend:dev -- --port "$FRONTEND_PORT"
