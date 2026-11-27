#!/usr/bin/env bash
# =====================================================================
# LingXiao - control the portal (Nuxt 3 SSR) on Linux
# ---------------------------------------------------------------------
# Usage:
#   ./bin/portal.sh start|stop|restart|status
#
# Layout expected (the release package already matches it):
#   /opt/lingxiao/
#   ├─ portal/server/index.mjs    <- lx-ui-portal/.output
#   ├─ bin/portal.sh
#   ├─ logs/
#   └─ run/
#
# Environment (all optional, shown with defaults):
#   NODE_BIN              node
#   PORT                  3000
#   NUXT_BACKEND_BASE     http://127.0.0.1:8080
#   NUXT_PUBLIC_SITE_KEY  main
#   NUXT_PUBLIC_SITE_URL  http://127.0.0.1:3000
#
# For production prefer bin/lingxiao-portal.service (systemd). This
# script is for quick trials or environments without systemd.
# =====================================================================
set -euo pipefail

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORTAL_HOME="${APP_HOME}/portal"
ENTRY="${PORTAL_HOME}/server/index.mjs"
PID_FILE="${APP_HOME}/run/portal.pid"
PORT_FILE="${APP_HOME}/run/portal.port"
LOG_DIR="${APP_HOME}/logs"
NODE_BIN="${NODE_BIN:-node}"
PORT="${PORT:-3000}"

mkdir -p "$LOG_DIR" "$APP_HOME/run"

running_pid() {
  [[ -f "$PID_FILE" ]] || return 1
  local pid
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  [[ -n "${pid}" ]] && kill -0 "$pid" 2>/dev/null && echo "$pid"
}

start() {
  local pid
  if pid="$(running_pid)"; then
    echo "Portal is already running (pid ${pid})"
    return 0
  fi
  [[ -f "$ENTRY" ]] || { echo "ERROR: portal entry not found: $ENTRY" >&2; exit 1; }
  command -v "$NODE_BIN" >/dev/null 2>&1 || { echo "ERROR: node not found. Set NODE_BIN or add node to PATH." >&2; exit 1; }

  cd "$PORTAL_HOME"
  NODE_ENV=production \
  PORT="$PORT" \
  NUXT_BACKEND_BASE="${NUXT_BACKEND_BASE:-http://127.0.0.1:8080}" \
  NUXT_PUBLIC_SITE_KEY="${NUXT_PUBLIC_SITE_KEY:-main}" \
  NUXT_PUBLIC_SITE_URL="${NUXT_PUBLIC_SITE_URL:-http://127.0.0.1:3000}" \
  nohup "$NODE_BIN" "$ENTRY" >> "$LOG_DIR/portal.console.log" 2>&1 &

  local new_pid=$!
  echo "$new_pid" > "$PID_FILE"
  echo "$PORT" > "$PORT_FILE"
  sleep 2

  if kill -0 "$new_pid" 2>/dev/null; then
    echo "Portal started (pid ${new_pid}, port ${PORT})"
    echo "log: $LOG_DIR/portal.console.log"
  else
    echo "ERROR: portal exited during startup; last log lines:" >&2
    tail -n 40 "$LOG_DIR/portal.console.log" >&2 || true
    rm -f "$PID_FILE"
    exit 1
  fi
}

stop() {
  local pid
  if ! pid="$(running_pid)"; then
    echo "Portal is not running"
    rm -f "$PID_FILE"
    return 0
  fi
  kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 20); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done
  if kill -0 "$pid" 2>/dev/null; then
    echo "Portal did not stop in 20s, sending SIGKILL (pid ${pid})"
    kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$PID_FILE" "$PORT_FILE"
  echo "Portal stopped"
}

status() {
  local pid running_port
  if pid="$(running_pid)"; then
    running_port="$(cat "$PORT_FILE" 2>/dev/null || echo "$PORT")"
    echo "Portal is running (pid ${pid}, port ${running_port})"
  else
    echo "Portal is not running"
    exit 3
  fi
}

case "${1:-}" in
  start) start ;;
  stop) stop ;;
  restart) stop; start ;;
  status) status ;;
  *)
    echo "Usage: $0 start|stop|restart|status" >&2
    exit 2
    ;;
esac