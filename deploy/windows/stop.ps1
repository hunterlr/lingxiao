# =====================================================================
# LingXiao - stop the backend on Windows
# =====================================================================

$ErrorActionPreference = 'Stop'

$appHome = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $appHome 'run\lingxiao.pid'

if (-not (Test-Path $pidFile)) {
    Write-Host 'LingXiao is not running (no pid file)'
    exit 0
}

$rawPid = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $rawPid) {
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    Write-Host 'LingXiao is not running (empty pid file removed)'
    exit 0
}

$proc = Get-Process -Id ([int]$rawPid) -ErrorAction SilentlyContinue
if (-not $proc) {
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    Write-Host 'LingXiao is not running (stale pid file removed)'
    exit 0
}

Write-Host "Stopping LingXiao (pid $rawPid) ..."
Stop-Process -Id ([int]$rawPid) -ErrorAction SilentlyContinue

for ($i = 0; $i -lt 30; $i++) {
    $alive = Get-Process -Id ([int]$rawPid) -ErrorAction SilentlyContinue
    if (-not $alive) {
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
        Write-Host 'Stopped'
        exit 0
    }
    Start-Sleep -Seconds 1
}

Write-Host 'Graceful shutdown timed out, forcing ...' -ForegroundColor Yellow
Stop-Process -Id ([int]$rawPid) -Force -ErrorAction SilentlyContinue
Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
Write-Host 'Stopped (forced)'
