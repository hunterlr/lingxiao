# =====================================================================
# LingXiao - restart the backend on Windows
# =====================================================================

$ErrorActionPreference = 'Stop'

& (Join-Path $PSScriptRoot 'stop.ps1')
& (Join-Path $PSScriptRoot 'start.ps1')
