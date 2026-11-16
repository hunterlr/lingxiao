#!/usr/bin/env bash
# =====================================================================
# LingXiao - stop the backend on Linux (pid-file mode)
# =====================================================================
set -euo pipefail

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="${APP_HOME}/run/lingxiao.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "LingXiao is not running (no pid file)"
  exit 0
fi

PID="$(cat "$PID_FILE" 2>/dev/null || true)"
if [[ -z "${PID}" ]] || ! kill -0 "$PID" 2>/dev/null; then
  echo "LingXiao is not running (stale pid file removed)"
  rm -f "$PID_FILE"
  exit 0
fi

echo "Stopping LingXiao (pid ${PID}) ..."
kill "$PID" 2>/dev/null || true

for _ in $(seq 1 30); do
  if ! kill -0 "$PID" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "Stopped"
    exit 0
  fi
  sleep 1
done

echo "Graceful shutdown timed out, forcing ..."
kill -9 "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
echo "Stopped (forced)"
