# =====================================================================
# LingXiao - show backend status on Windows
# =====================================================================

$ErrorActionPreference = 'Stop'

$appHome = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $appHome 'run\lingxiao.pid'

if (-not (Test-Path $pidFile)) {
    Write-Host 'LingXiao: stopped'
    exit 3
}

$rawPid = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
$proc = if ($rawPid) { Get-Process -Id ([int]$rawPid) -ErrorAction SilentlyContinue } else { $null }
if ($proc) {
    Write-Host "LingXiao: running (pid $rawPid)"
    exit 0
}

Write-Host 'LingXiao: dead (stale pid file)'
exit 1
