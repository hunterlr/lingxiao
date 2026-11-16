#!/usr/bin/env bash
# =====================================================================
# LingXiao - restart the backend on Linux (pid-file mode)
# =====================================================================
set -euo pipefail

BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$BIN_DIR/stop.sh"
"$BIN_DIR/start.sh"
