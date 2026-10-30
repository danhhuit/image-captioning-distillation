param(
    [string]$DeviceSerial = "emulator-5554",
    [string]$ResultRoot = "D:\KLCN\output_codex\04_thuc_nghiem\KLCN_V2_F30K\android_app_integration_results"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not $repositoryRoot.StartsWith("D:\KLCN\src", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Repository root ngoai pham vi du kien: $repositoryRoot"
}

$adb = @(
    (Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue),
    (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
    "D:\Android\Sdk\platform-tools\adb.exe"
) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) } | Select-Object -First 1
if (-not $adb) {
    throw "Khong tim thay adb."
}

$deviceState = (& $adb -s $DeviceSerial get-state 2>$null).Trim()
if ($deviceState -ne "device") {
    throw "Thiet bi $DeviceSerial khong o trang thai device."
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$resultDirectory = Join-Path $ResultRoot $timestamp
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null

Push-Location $repositoryRoot
try {
    $androidStudioJbr = "D:\Android\Android Studio\jbr"
    if (Test-Path -LiteralPath (Join-Path $androidStudioJbr "bin\java.exe") -PathType Leaf) {
        $env:JAVA_HOME = $androidStudioJbr
        $env:Path = "$(Join-Path $androidStudioJbr 'bin');$env:Path"
    }
    & .\gradlew.bat :app:assembleRelease :app:assembleAndroidTest
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed: $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$appApk = Join-Path $repositoryRoot "app\build\outputs\apk\release\app-release.apk"
$testApk = Join-Path $repositoryRoot "app\build\outputs\apk\androidTest\release\app-release-androidTest.apk"
if (-not (Test-Path -LiteralPath $appApk -PathType Leaf)) { throw "Thieu $appApk" }
if (-not (Test-Path -LiteralPath $testApk -PathType Leaf)) { throw "Thieu $testApk" }

& $adb -s $DeviceSerial install -r $appApk
if ($LASTEXITCODE -ne 0) { throw "Cai app release that bai." }
& $adb -s $DeviceSerial install -r $testApk
if ($LASTEXITCODE -ne 0) { throw "Cai test APK that bai." }

$instrumentationLog = Join-Path $resultDirectory "instrumentation.log"
$instrumentationOutput = & $adb -s $DeviceSerial shell am instrument -w -r `
    -e class com.klcn.mobilecaptioning.Flickr30kExperimentalModelIntegrationTest `
    com.klcn.mobilecaptioning.test/androidx.test.runner.AndroidJUnitRunner 2>&1
$instrumentationOutput | Set-Content -LiteralPath $instrumentationLog -Encoding utf8
$instrumentationOutput | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0 -or ($instrumentationOutput -join "`n") -notmatch "OK \(1 test\)") {
    throw "Flickr30k production integration smoke test failed. Xem $instrumentationLog"
}

Write-Host "Flickr30k experimental app integration: PASSED"
Write-Host "Log: $instrumentationLog"
