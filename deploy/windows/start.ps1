# =====================================================================
# LingXiao - start the backend on Windows
# ---------------------------------------------------------------------
# Layout expected (the release package already matches it):
#   C:\lingxiao\
#   |- lingxiao.jar
#   |- config\application-prod.yml
#   |- bin\start.ps1
#   |- logs\
#   \- run\
#
# Run from an elevated PowerShell if the app directory is protected:
#   powershell -ExecutionPolicy Bypass -File .\bin\start.ps1
# =====================================================================

$ErrorActionPreference = 'Stop'

$appHome = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $appHome 'lingxiao.jar'
$pidFile = Join-Path $appHome 'run\lingxiao.pid'
$logDir = Join-Path $appHome 'logs'
$profile = if ($env:SPRING_PROFILE) { $env:SPRING_PROFILE } else { 'prod' }

New-Item -ItemType Directory -Force -Path $logDir, (Join-Path $appHome 'run'), (Join-Path $appHome 'data\upload') | Out-Null

if (-not (Test-Path $jar)) {
    Write-Host "ERROR: jar not found: $jar" -ForegroundColor Red
    exit 1
}

if (Test-Path $pidFile) {
    $oldPid = (Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($oldPid) {
        $old = Get-Process -Id ([int]$oldPid) -ErrorAction SilentlyContinue
        if ($old) {
            Write-Host "LingXiao is already running (pid $oldPid)"
            exit 0
        }
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

$java = 'java'
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $candidate) { $java = $candidate }
}
if (-not (Get-Command $java -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: java not found. Set JAVA_HOME or add java to PATH." -ForegroundColor Red
    exit 1
}

$outLog = Join-Path $logDir 'console.out.log'
$errLog = Join-Path $logDir 'console.err.log'
$argLine = "-Xms256m -Xmx512m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -jar `"$jar`" --spring.profiles.active=$profile"

$proc = Start-Process -FilePath $java `
    -ArgumentList $argLine `
    -WorkingDirectory $appHome `
    -RedirectStandardOutput $outLog `
    -RedirectStandardError $errLog `
    -PassThru -WindowStyle Hidden

Set-Content -Path $pidFile -Value $proc.Id -Encoding ASCII
Start-Sleep -Seconds 3

if ($proc.HasExited) {
    Write-Host "ERROR: LingXiao exited during startup (exit code $($proc.ExitCode))" -ForegroundColor Red
    if (Test-Path $errLog) {
        Write-Host '--- last lines of console.err.log ---'
        Get-Content $errLog -Tail 40
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    exit 1
}

Write-Host "LingXiao started (pid $($proc.Id), profile $profile)"
Write-Host "log: $outLog"
