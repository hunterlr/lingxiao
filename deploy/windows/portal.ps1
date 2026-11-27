# =====================================================================
# LingXiao - control the portal (Nuxt 3 SSR) on Windows
# ---------------------------------------------------------------------
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\bin\portal.ps1 start|stop|restart|status
#
# Layout expected (the release package already matches it):
#   C:\lingxiao\
#   |- portal\server\index.mjs    <- lx-ui-portal\.output
#   |- bin\portal.ps1
#   |- logs\
#   \- run\
#
# Environment (all optional, shown with defaults):
#   NODE_BIN              node
#   PORT                  3000
#   NUXT_BACKEND_BASE     http://127.0.0.1:8080
#   NUXT_PUBLIC_SITE_KEY  main
#   NUXT_PUBLIC_SITE_URL  http://127.0.0.1:3000
#
# Requires Node 20+ on PATH (or NODE_BIN pointing at node.exe).
# Note: the local variable is called $running on purpose - $pid collides
# with PowerShell's read-only automatic variable.
# =====================================================================

param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'restart', 'status')]
    [string]$Action = 'status'
)

$ErrorActionPreference = 'Stop'

$appHome = Split-Path -Parent $PSScriptRoot
$portalHome = Join-Path $appHome 'portal'
$entry = Join-Path $portalHome 'server\index.mjs'
$pidFile = Join-Path $appHome 'run\portal.pid'
$portFile = Join-Path $appHome 'run\portal.port'
$logDir = Join-Path $appHome 'logs'
$port = if ($env:PORT) { $env:PORT } else { '3000' }

New-Item -ItemType Directory -Force -Path $logDir, (Join-Path $appHome 'run') | Out-Null

function Get-RunningPid {
    if (-not (Test-Path $pidFile)) { return $null }
    $raw = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $raw) { return $null }
    $proc = Get-Process -Id ([int]$raw) -ErrorAction SilentlyContinue
    if ($proc) { return $proc.Id }
    return $null
}

function Start-Portal {
    $running = Get-RunningPid
    if ($running) {
        Write-Host "Portal is already running (pid $running)"
        return
    }
    if (-not (Test-Path $entry)) {
        Write-Host "ERROR: portal entry not found: $entry" -ForegroundColor Red
        exit 1
    }

    $node = if ($env:NODE_BIN) { $env:NODE_BIN } else { 'node' }
    if (-not (Get-Command $node -ErrorAction SilentlyContinue)) {
        Write-Host "ERROR: node not found. Install Node 20+ or set NODE_BIN." -ForegroundColor Red
        exit 1
    }

    $env:NODE_ENV = 'production'
    $env:PORT = $port
    if (-not $env:NUXT_BACKEND_BASE) { $env:NUXT_BACKEND_BASE = 'http://127.0.0.1:8080' }
    if (-not $env:NUXT_PUBLIC_SITE_KEY) { $env:NUXT_PUBLIC_SITE_KEY = 'main' }
    if (-not $env:NUXT_PUBLIC_SITE_URL) { $env:NUXT_PUBLIC_SITE_URL = 'http://127.0.0.1:3000' }

    $outLog = Join-Path $logDir 'portal.out.log'
    $errLog = Join-Path $logDir 'portal.err.log'

    $proc = Start-Process -FilePath $node `
        -ArgumentList @("`"$entry`"") `
        -WorkingDirectory $portalHome `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -PassThru -WindowStyle Hidden

    Set-Content -Path $pidFile -Value $proc.Id -Encoding ASCII
    Set-Content -Path $portFile -Value $port -Encoding ASCII
    Start-Sleep -Seconds 3

    if ($proc.HasExited) {
        Write-Host "ERROR: portal exited during startup (exit code $($proc.ExitCode))" -ForegroundColor Red
        if (Test-Path $errLog) {
            Write-Host '--- last lines of portal.err.log ---'
            Get-Content $errLog -Tail 40
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
        exit 1
    }

    Write-Host "Portal started (pid $($proc.Id), port $port)"
    Write-Host "log: $outLog"
}

function Stop-Portal {
    $running = Get-RunningPid
    if (-not $running) {
        Write-Host 'Portal is not running'
        Remove-Item $pidFile, $portFile -Force -ErrorAction SilentlyContinue
        return
    }
    Stop-Process -Id $running -ErrorAction SilentlyContinue
    for ($i = 0; $i -lt 20; $i++) {
        if (-not (Get-Process -Id $running -ErrorAction SilentlyContinue)) { break }
        Start-Sleep -Seconds 1
    }
    if (Get-Process -Id $running -ErrorAction SilentlyContinue) {
        Write-Host "Portal did not stop in 20s, forcing (pid $running)"
        Stop-Process -Id $running -Force -ErrorAction SilentlyContinue
    }
    Remove-Item $pidFile, $portFile -Force -ErrorAction SilentlyContinue
    Write-Host 'Portal stopped'
}

function Get-PortalStatus {
    $running = Get-RunningPid
    if ($running) {
        $runningPort = if (Test-Path $portFile) { (Get-Content $portFile | Select-Object -First 1) } else { $port }
        Write-Host "Portal is running (pid $running, port $runningPort)"
    } else {
        Write-Host 'Portal is not running'
        exit 3
    }
}

switch ($Action) {
    'start' { Start-Portal }
    'stop' { Stop-Portal }
    'restart' { Stop-Portal; Start-Portal }
    'status' { Get-PortalStatus }
}