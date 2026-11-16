#!/usr/bin/env bash
# =====================================================================
# LingXiao - start the backend on Linux (simple pid-file mode)
# ---------------------------------------------------------------------
# Layout expected (the release package already matches it):
#   /opt/lingxiao/
#   ├─ lingxiao.jar
#   ├─ config/application-prod.yml
#   ├─ bin/start.sh
#   ├─ logs/
#   └─ run/
#
# For production prefer bin/lingxiao.service (systemd). This script is
# for quick trials or environments without systemd.
# =====================================================================
set -euo pipefail

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${APP_HOME}/lingxiao.jar"
PID_FILE="${APP_HOME}/run/lingxiao.pid"
LOG_DIR="${APP_HOME}/logs"
JAVA_BIN="${JAVA_BIN:-java}"
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m -XX:+UseG1GC}"
PROFILE="${SPRING_PROFILE:-prod}"

mkdir -p "$LOG_DIR" "$APP_HOME/run" "$APP_HOME/data/upload"

if [[ ! -f "$JAR" ]]; then
  echo "ERROR: jar not found: $JAR" >&2
  exit 1
fi

if [[ -f "$PID_FILE" ]]; then
  OLD_PID="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "${OLD_PID}" ]] && kill -0 "$OLD_PID" 2>/dev/null; then
    echo "LingXiao is already running (pid ${OLD_PID})"
    exit 0
  fi
  rm -f "$PID_FILE"
fi

# shellcheck disable=SC2086
nohup "$JAVA_BIN" $JAVA_OPTS \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=Asia/Shanghai \
  -jar "$JAR" \
  --spring.profiles.active="$PROFILE" \
  >> "$LOG_DIR/console.log" 2>&1 &

NEW_PID=$!
echo "$NEW_PID" > "$PID_FILE"
sleep 2

if kill -0 "$NEW_PID" 2>/dev/null; then
  echo "LingXiao started (pid ${NEW_PID}, profile ${PROFILE})"
  echo "log: $LOG_DIR/console.log"
else
  echo "ERROR: LingXiao exited during startup; last log lines:" >&2
  tail -n 40 "$LOG_DIR/console.log" >&2 || true
  rm -f "$PID_FILE"
  exit 1
fi
