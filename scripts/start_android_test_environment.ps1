param(
    [string]$DeviceSerial = "",
    [switch]$SkipAdbReverse
)

$ErrorActionPreference = "Stop"
$SourceRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$ProjectRoot = Split-Path -Parent $SourceRoot
$BackendRoot = Join-Path $ProjectRoot "backend"
$ApiPort = 8000

function Wait-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker is not installed or is not available in PATH."
    }

    docker info *> $null
    if ($LASTEXITCODE -eq 0) { return }

    $dockerDesktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (-not (Test-Path -LiteralPath $dockerDesktop)) {
        throw "Docker Desktop is not running. Start it and run this script again."
    }

    Write-Host "Starting Docker Desktop..." -ForegroundColor Yellow
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        Start-Sleep -Seconds 2
        docker info *> $null
        if ($LASTEXITCODE -eq 0) { return }
    }
    throw "Docker Desktop did not become ready within 120 seconds."
}

function Resolve-Adb {
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    $localProperties = Join-Path $SourceRoot "local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1
        if ($sdkLine) {
            $sdk = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
            $candidate = Join-Path $sdk "platform-tools\adb.exe"
            if (Test-Path -LiteralPath $candidate) { return $candidate }
        }
    }
    throw "adb was not found. Install Android SDK Platform Tools or add adb to PATH."
}

Wait-Docker

Write-Host "Starting PostgreSQL and Central API..." -ForegroundColor Cyan
Push-Location $BackendRoot
try {
    docker compose up -d
    if ($LASTEXITCODE -ne 0) { throw "docker compose up failed." }
} finally {
    Pop-Location
}

$health = $null
for ($attempt = 1; $attempt -le 30; $attempt++) {
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:$ApiPort/health" -TimeoutSec 3
        if ($health.status -eq "ok") { break }
    } catch {
        Start-Sleep -Seconds 2
    }
}
if ($null -eq $health -or $health.status -ne "ok") {
    throw "Central API did not become healthy within 60 seconds."
}
Write-Host "Central API is healthy." -ForegroundColor Green

$adb = Resolve-Adb
$deviceLines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' }
if ($DeviceSerial) {
    $deviceLines = $deviceLines | Where-Object { ($_ -split '\s+')[0] -eq $DeviceSerial }
    if (-not $deviceLines) { throw "Device '$DeviceSerial' is not connected in adb device state." }
}

if (-not $deviceLines) {
    Write-Warning "Backend is ready, but no Android device is connected. Start an emulator or connect USB, then rerun this script."
} else {
    foreach ($line in $deviceLines) {
        $serial = ($line -split '\s+')[0]
        if (-not $SkipAdbReverse) {
            & $adb -s $serial reverse "tcp:$ApiPort" "tcp:$ApiPort" | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "adb reverse failed for $serial." }
        }
        Write-Host "Android device ready: $serial" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "Test URLs:" -ForegroundColor Cyan
Write-Host "  Windows host    : http://127.0.0.1:$ApiPort/health"
Write-Host "  Android emulator: http://10.0.2.2:$ApiPort"
Write-Host "  USB + adb reverse: http://localhost:$ApiPort"
Write-Host ""
Write-Host "The login/register server is ready." -ForegroundColor Green
