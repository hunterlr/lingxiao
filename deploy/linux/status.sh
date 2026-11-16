#!/usr/bin/env bash
# =====================================================================
# LingXiao - show backend status on Linux (pid-file mode)
# =====================================================================
set -euo pipefail

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="${APP_HOME}/run/lingxiao.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "LingXiao: stopped"
  exit 3
fi

PID="$(cat "$PID_FILE" 2>/dev/null || true)"
if [[ -n "${PID}" ]] && kill -0 "$PID" 2>/dev/null; then
  echo "LingXiao: running (pid ${PID})"
  exit 0
fi

echo "LingXiao: dead (stale pid file)"
exit 1
