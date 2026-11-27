# =====================================================================
# LingXiao - assemble a release folder on Windows
# ---------------------------------------------------------------------
# Prerequisites:
#   mvn -DskipTests package
#   cd lx-ui-admin; npm ci; npm run build
#   cd lx-ui-portal; npm ci; npm run build     (portal -> .output)
#
# Output:
#   release\lingxiao-<version>\
#   release\lingxiao-<version>.zip
#
# This script only copies artifacts; it does not build them.
# =====================================================================

param(
    [string]$Version = '1.4.0'
)

$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $repo 'lx-boot\target\lingxiao.jar'
$dist = Join-Path $repo 'lx-ui-admin\dist'
$portal = Join-Path $repo 'lx-ui-portal\.output'
$releaseRoot = Join-Path $repo 'release'
$appDir = Join-Path $releaseRoot "lingxiao-$Version"

if (-not (Test-Path $jar)) {
    throw "jar not found: $jar - run 'mvn -DskipTests package' first"
}
if (-not (Test-Path (Join-Path $dist 'index.html'))) {
    throw "admin dist not found: $dist - run 'npm run build' in lx-ui-admin first"
}
if (-not (Test-Path (Join-Path $portal 'server\index.mjs'))) {
    throw "portal output not found: $portal - run 'npm run build' in lx-ui-portal first"
}

if (Test-Path $appDir) {
    Remove-Item -Recurse -Force $appDir
}
New-Item -ItemType Directory -Force -Path $appDir | Out-Null

Copy-Item $jar (Join-Path $appDir 'lingxiao.jar')
Copy-Item (Join-Path $PSScriptRoot 'config') (Join-Path $appDir 'config') -Recurse
Copy-Item (Join-Path $PSScriptRoot 'nginx') (Join-Path $appDir 'nginx') -Recurse
Copy-Item (Join-Path $PSScriptRoot 'sql') (Join-Path $appDir 'sql') -Recurse
Copy-Item (Join-Path $PSScriptRoot 'README.md') (Join-Path $appDir 'README.md')
Copy-Item $dist (Join-Path $appDir 'web') -Recurse
Copy-Item $portal (Join-Path $appDir 'portal') -Recurse

$bin = Join-Path $appDir 'bin'
New-Item -ItemType Directory -Force -Path $bin | Out-Null
Get-ChildItem (Join-Path $PSScriptRoot 'linux') -Filter *.sh | Copy-Item -Destination $bin
Get-ChildItem (Join-Path $PSScriptRoot 'windows') -Filter *.ps1 | Copy-Item -Destination $bin
Copy-Item (Join-Path $PSScriptRoot 'linux\lingxiao.service') (Join-Path $appDir 'lingxiao.service')
Copy-Item (Join-Path $PSScriptRoot 'linux\lingxiao-portal.service') (Join-Path $appDir 'lingxiao-portal.service')

New-Item -ItemType Directory -Force -Path (Join-Path $appDir 'logs'), (Join-Path $appDir 'data\upload'), (Join-Path $appDir 'run') | Out-Null

$zip = Join-Path $releaseRoot "lingxiao-$Version.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path $appDir -DestinationPath $zip -CompressionLevel Optimal

Write-Host "Release folder: $appDir"
Write-Host "Release zip   : $zip"
Write-Host ''
Write-Host 'Next steps:'
Write-Host '  1. Copy the folder to the server (Linux: /opt/lingxiao)'
Write-Host '  2. Linux: chmod +x bin/*.sh && ./bin/start.sh'
Write-Host '  3. Windows: powershell -ExecutionPolicy Bypass -File .\bin\start.ps1'
Write-Host '  4. Nginx: copy nginx/lingxiao.conf and point root to /opt/lingxiao/web'
Write-Host '  5. Portal (Node 20+): ./bin/portal.sh start  (Linux) / .\bin\portal.ps1 start  (Windows)'
Write-Host '     or systemd: sudo cp lingxiao-portal.service /etc/systemd/system/ && sudo systemctl enable --now lingxiao-portal'
