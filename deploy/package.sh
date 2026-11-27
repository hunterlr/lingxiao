#!/usr/bin/env bash
# =====================================================================
# LingXiao - assemble a release tarball on Linux / macOS
# ---------------------------------------------------------------------
# Prerequisites:
#   mvn -DskipTests package
#   cd lx-ui-admin && npm ci && npm run build
#   cd lx-ui-portal && npm ci && npm run build     (portal -> .output)
#
# Output:
#   release/lingxiao-<version>/
#   release/lingxiao-<version>.tar.gz
#
# This script only copies artifacts; it does not build them.
# =====================================================================
set -euo pipefail

VERSION="${1:-1.4.0}"
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$DEPLOY_DIR/.." && pwd)"
JAR="$REPO/lx-boot/target/lingxiao.jar"
DIST="$REPO/lx-ui-admin/dist"
PORTAL="$REPO/lx-ui-portal/.output"
RELEASE_ROOT="$REPO/release"
APP_DIR="$RELEASE_ROOT/lingxiao-$VERSION"

[[ -f "$JAR" ]] || { echo "ERROR: jar not found: $JAR (run mvn -DskipTests package)" >&2; exit 1; }
[[ -f "$DIST/index.html" ]] || { echo "ERROR: admin dist not found: $DIST (run npm run build in lx-ui-admin)" >&2; exit 1; }
[[ -f "$PORTAL/server/index.mjs" ]] || { echo "ERROR: portal output not found: $PORTAL (run npm run build in lx-ui-portal)" >&2; exit 1; }

rm -rf "$APP_DIR"
mkdir -p "$APP_DIR/bin" "$APP_DIR/logs" "$APP_DIR/data/upload" "$APP_DIR/run"

cp "$JAR" "$APP_DIR/lingxiao.jar"
cp -R "$DEPLOY_DIR/config" "$APP_DIR/config"
cp -R "$DEPLOY_DIR/nginx" "$APP_DIR/nginx"
cp -R "$DEPLOY_DIR/sql" "$APP_DIR/sql"
cp "$DEPLOY_DIR/README.md" "$APP_DIR/README.md"
cp -R "$DIST" "$APP_DIR/web"
cp -R "$PORTAL" "$APP_DIR/portal"

cp "$DEPLOY_DIR"/linux/*.sh "$APP_DIR/bin/"
cp "$DEPLOY_DIR"/windows/*.ps1 "$APP_DIR/bin/"
cp "$DEPLOY_DIR/linux/lingxiao.service" "$APP_DIR/lingxiao.service"
cp "$DEPLOY_DIR/linux/lingxiao-portal.service" "$APP_DIR/lingxiao-portal.service"
chmod +x "$APP_DIR"/bin/*.sh

# Normalize CRLF just in case the repo was checked out on Windows
sed -i 's/\r$//' "$APP_DIR"/bin/*.sh 2>/dev/null || true

TARBALL="$RELEASE_ROOT/lingxiao-$VERSION.tar.gz"
rm -f "$TARBALL"
tar -C "$RELEASE_ROOT" -czf "$TARBALL" "lingxiao-$VERSION"

echo "Release folder: $APP_DIR"
echo "Release tarball: $TARBALL"
echo
echo "Next steps:"
echo "  1. tar -xzf lingxiao-$VERSION.tar.gz -C /opt"
echo "  2. cd /opt/lingxiao-$VERSION && ./bin/start.sh"
echo "  3. Nginx: copy nginx/lingxiao.conf and point root to /opt/lingxiao-$VERSION/web"
echo "  4. Portal (Node 20+): ./bin/portal.sh start"
echo "     or systemd: cp lingxiao-portal.service /etc/systemd/system/ && systemctl enable --now lingxiao-portal"
