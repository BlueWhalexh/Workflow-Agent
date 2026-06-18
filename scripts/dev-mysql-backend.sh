#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

BACKEND_PORT="${MY_WORKFLOW_BACKEND_PORT:-18081}"
MYSQL_HOST="${MY_WORKFLOW_MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MY_WORKFLOW_MYSQL_PORT:-3307}"
MYSQL_DATABASE="${MY_WORKFLOW_MYSQL_DATABASE:-my_workflow_agent}"
MYSQL_USER="${MY_WORKFLOW_MYSQL_USER:-my_workflow_agent}"
MYSQL_PASSWORD="${MY_WORKFLOW_MYSQL_PASSWORD:-my_workflow_agent_dev}"
DATA_ROOT="${MY_WORKFLOW_BACKEND_DATA_ROOT:-/private/tmp/my-workflow-agent-local-mysql-backend}"
MAVEN_BIN="${MAVEN:-/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn}"

if [[ ! -x "$MAVEN_BIN" ]]; then
  MAVEN_BIN="mvn"
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-jdbc}"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-$MYSQL_USER}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$MYSQL_PASSWORD}"
export SPRING_DATASOURCE_DRIVER_CLASS_NAME="${SPRING_DATASOURCE_DRIVER_CLASS_NAME:-com.mysql.cj.jdbc.Driver}"
export MY_WORKFLOW_BACKEND_DATA_ROOT="$DATA_ROOT"
export MY_WORKFLOW_AGENT_WORKER_REPO_ROOT="${MY_WORKFLOW_AGENT_WORKER_REPO_ROOT:-$ROOT_DIR}"

exec "$MAVEN_BIN" \
  -f "$ROOT_DIR/backend/pom.xml" \
  spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=${BACKEND_PORT}"
