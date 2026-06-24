#!/usr/bin/env bash
set -u

CONFIG_PATH="config/sidecar.local.yaml"

usage() {
  cat <<'USAGE'
Usage: scripts/dev-sidecar.sh [--config <path>] [--help]

Starts the Phase 0 dev sidecar skeleton.

Options:
  --config <path>  Config file path. Defaults to config/sidecar.local.yaml.
  --help           Print this help message.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --help|-h)
      usage
      exit 0
      ;;
    --config)
      if [ "$#" -lt 2 ]; then
        echo "Missing value for --config" >&2
        exit 1
      fi
      CONFIG_PATH="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [ ! -f "$CONFIG_PATH" ]; then
  echo "Config not found: $CONFIG_PATH" >&2
  exit 1
fi

PORT="$(sed -n 's/^[[:space:]]*port:[[:space:]]*//p' "$CONFIG_PATH" | head -n 1 | tr -d '"' | tr -d "'")"

case "$PORT" in
  ''|*[!0-9]*)
    echo "Invalid or missing port in config: $CONFIG_PATH" >&2
    exit 1
    ;;
esac

if sed -n 's/^[[:space:]]*stubPortOccupied:[[:space:]]*//p' "$CONFIG_PATH" | grep -q '^true$'; then
  echo "Port is already in use: $PORT" >&2
  exit 2
fi

if command -v nc >/dev/null 2>&1 && nc -z 127.0.0.1 "$PORT" >/dev/null 2>&1; then
  echo "Port is already in use: $PORT" >&2
  exit 2
fi

echo "Listening on http://127.0.0.1:$PORT"
