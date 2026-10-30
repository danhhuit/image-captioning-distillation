param(
    [string]$DeviceSerial = "",
    [switch]$AllowEmulator,
    [ValidateRange(1, 20)]
    [int]$WarmupRuns = 5,
    [ValidateRange(10, 100)]
    [int]$MeasuredRuns = 30,
    [string]$BenchmarkImagePath = "",
    [string]$DeploymentPackage = "D:\KLCN\output_v2\f30k\KLCN_V2_F30K_T3S3_Deployment_Lock\mobile_package",
    [string]$ResultRoot = "D:\KLCN\output_codex\04_thuc_nghiem\KLCN_V2_F30K\android_benchmark_results"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$testAssetDirectory = Join-Path $repositoryRoot "app\src\androidTest\assets\f30k_t3s3_benchmark"
$expectedPackageRoot = (Resolve-Path -LiteralPath $DeploymentPackage).Path
$expectedRepositoryRoot = (Resolve-Path -LiteralPath $repositoryRoot).Path

if (-not $expectedRepositoryRoot.StartsWith("D:\KLCN\src", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Repository root ngoai pham vi du kien: $expectedRepositoryRoot"
}
if (-not $expectedPackageRoot.StartsWith("D:\KLCN\output_v2\f30k", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Deployment package ngoai pham vi du kien: $expectedPackageRoot"
}

$artifacts = @(
    @{
        Name = "f30k_t3s3_seed42_encoder_fp32.onnx"
        Sha256 = "1ff7e46d053142bd1cccf0e02e9ffe0c6e38495274e4dc60aa5f059da03ae984"
    },
    @{
        Name = "f30k_t3s3_seed42_decoder_dynamic_int8.onnx"
        Sha256 = "ece2a7b788529e515467f28188b6e6eca0c680fb11c04104b683c21973ea2973"
    },
    @{
        Name = "f30k_vocabulary_minfreq5.json"
        Sha256 = "c94727d24d24130126ae7c88d9537714b88ff3dde12ac741f9cd4a0757287a83"
    },
    @{
        Name = "KLCN_V2_F30K_T3S3_MOBILE_CONFIG.json"
        Sha256 = "44e3b5b4adc8eb7c4a65cf9e1a82bffb3159d67746827c17c05e224c2429857d"
    }
)

foreach ($artifact in $artifacts) {
    $path = Join-Path $expectedPackageRoot $artifact.Name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Thieu artifact: $path"
    }
    $actualHash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $artifact.Sha256) {
        throw "SHA-256 mismatch: $($artifact.Name)"
    }
}

$adbCandidates = @(
    (Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue),
    (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe")
) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) } | Select-Object -First 1
if (-not $adbCandidates) {
    throw "Khong tim thay adb. Hay cai Android SDK Platform-Tools."
}
$adb = $adbCandidates
$adbPrefix = @()
if ($DeviceSerial) {
    $adbPrefix = @("-s", $DeviceSerial)
}

$deviceLines = & $adb @adbPrefix devices
$connected = @(
    $deviceLines |
        Select-Object -Skip 1 |
        Where-Object { $_ -match "\tdevice$" } |
        ForEach-Object { ($_ -split "\t")[0] }
)
if ($DeviceSerial) {
    if ($DeviceSerial -notin $connected) {
        throw "Khong tim thay thiet bi $DeviceSerial trong trang thai device."
    }
} elseif ($connected.Count -ne 1) {
    throw "Can dung mot thiet bi vat ly, hoac truyen -DeviceSerial. Hien co: $($connected -join ', ')"
} else {
    $DeviceSerial = $connected[0]
    $adbPrefix = @("-s", $DeviceSerial)
}

$deviceProduct = (& $adb @adbPrefix shell getprop ro.build.product).Trim()
$deviceHardware = (& $adb @adbPrefix shell getprop ro.hardware).Trim()
$emulatorMarkers = @("sdk_gphone", "emulator", "goldfish", "ranchu")
$isEmulator = [bool]($emulatorMarkers | Where-Object {
    $deviceProduct -like "*$_*" -or $deviceHardware -like "*$_*"
})
if ($isEmulator -and -not $AllowEmulator) {
    throw "Chi cho phep benchmark tren thiet bi Android vat ly."
}
if ($isEmulator) {
    Write-Warning "Dang chay EMULATOR SMOKE TEST. Khong dung latency/RAM/thermal nay trong bao cao khoa hoc."
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$resultDirectory = Join-Path $ResultRoot $timestamp
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null

try {
    New-Item -ItemType Directory -Path $testAssetDirectory -Force | Out-Null
    foreach ($artifact in $artifacts) {
        Copy-Item -LiteralPath (Join-Path $expectedPackageRoot $artifact.Name) -Destination $testAssetDirectory -Force
    }
    if ($BenchmarkImagePath) {
        $resolvedImage = (Resolve-Path -LiteralPath $BenchmarkImagePath).Path
        Copy-Item -LiteralPath $resolvedImage -Destination (Join-Path $testAssetDirectory "benchmark_image.jpg") -Force
    }

    Push-Location $repositoryRoot
    try {
        $androidStudioJbr = "D:\Android\Android Studio\jbr"
        if (Test-Path -LiteralPath (Join-Path $androidStudioJbr "bin\java.exe") -PathType Leaf) {
            $env:JAVA_HOME = $androidStudioJbr
            $env:Path = "$(Join-Path $androidStudioJbr 'bin');$env:Path"
        }
        & .\gradlew.bat :app:assembleRelease :app:assembleAndroidTest
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed: $LASTEXITCODE" }
    } finally {
        Pop-Location
    }

    $appApk = Join-Path $repositoryRoot "app\build\outputs\apk\release\app-release.apk"
    $testApk = Join-Path $repositoryRoot "app\build\outputs\apk\androidTest\release\app-release-androidTest.apk"
    if (-not (Test-Path -LiteralPath $appApk -PathType Leaf)) { throw "Thieu $appApk" }
    if (-not (Test-Path -LiteralPath $testApk -PathType Leaf)) { throw "Thieu $testApk" }

    & $adb @adbPrefix install -r $appApk
    if ($LASTEXITCODE -ne 0) { throw "Cai app release that bai." }
    & $adb @adbPrefix install -r $testApk
    if ($LASTEXITCODE -ne 0) { throw "Cai test APK that bai." }

    $instrumentationLog = Join-Path $resultDirectory "instrumentation.log"
    $allowEmulatorValue = if ($AllowEmulator) { "true" } else { "false" }
    $instrumentationOutput = & $adb @adbPrefix shell am instrument -w -r `
        -e class com.klcn.mobilecaptioning.Flickr30kT3S3AndroidBenchmarkTest `
        -e warmupRuns $WarmupRuns `
        -e measuredRuns $MeasuredRuns `
        -e allowEmulatorSmoke $allowEmulatorValue `
        com.klcn.mobilecaptioning.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $instrumentationOutput | Set-Content -LiteralPath $instrumentationLog -Encoding utf8
    $instrumentationOutput | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0 -or ($instrumentationOutput -join "`n") -notmatch "OK \(1 test\)") {
        throw "Instrumentation benchmark failed. Xem $instrumentationLog"
    }

    $resultFileName = if ($isEmulator) {
        "KLCN_V2_F30K_T3S3_ANDROID_EMULATOR_SMOKE.json"
    } else {
        "KLCN_V2_F30K_T3S3_ANDROID_PHYSICAL_BENCHMARK.json"
    }
    $remoteResult = "/sdcard/Android/data/com.klcn.mobilecaptioning/files/benchmarks/$resultFileName"
    $localResult = Join-Path $resultDirectory $resultFileName
    & $adb @adbPrefix pull $remoteResult $localResult
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $localResult -PathType Leaf)) {
        throw "Khong keo duoc ket qua benchmark. Xem marker trong $instrumentationLog"
    }

    $result = Get-Content -Raw -LiteralPath $localResult | ConvertFrom-Json
    $expectedStatus = if ($isEmulator) { "complete_emulator_smoke" } else { "complete_physical_device" }
    if ($result.status -ne $expectedStatus) {
        throw "Trang thai benchmark khong hop le: $($result.status)"
    }
    if ($result.scientific_boundary.held_out_test_reopened -ne $false) {
        throw "Vi pham test seal."
    }

    $manifest = [ordered]@{
        schema_version = "klcn_v2_f30k_t3s3_android_benchmark_manifest_v1"
        created_at_local = (Get-Date).ToString("o")
        device_serial = $DeviceSerial
        measurement_role = if ($isEmulator) { "emulator_smoke_only" } else { "physical_device_benchmark" }
        result_file = (Split-Path -Leaf $localResult)
        result_sha256 = (Get-FileHash -LiteralPath $localResult -Algorithm SHA256).Hash.ToLowerInvariant()
        instrumentation_log = (Split-Path -Leaf $instrumentationLog)
        instrumentation_log_sha256 = (Get-FileHash -LiteralPath $instrumentationLog -Algorithm SHA256).Hash.ToLowerInvariant()
        held_out_test_reopened = $false
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $resultDirectory "MANIFEST.json") -Encoding utf8

    Write-Host "Benchmark completed: $localResult"
    Write-Host "E2E p50 (ms): $($result.latency_ms.end_to_end.p50)"
    Write-Host "E2E p95 (ms): $($result.latency_ms.end_to_end.p95)"
    Write-Host "PSS delta (KiB): $($result.memory.pss_delta_kib)"
    Write-Host "Thermal before/after: $($result.device.thermal_status_before) / $($result.device.thermal_status_after)"
} finally {
    if (Test-Path -LiteralPath $testAssetDirectory -PathType Container) {
        $resolvedAssetDirectory = (Resolve-Path -LiteralPath $testAssetDirectory).Path
        $expectedAssetRoot = (Resolve-Path -LiteralPath (Join-Path $repositoryRoot "app\src\androidTest\assets")).Path
        if (-not $resolvedAssetDirectory.StartsWith($expectedAssetRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Tu choi don asset ngoai androidTest/assets: $resolvedAssetDirectory"
        }
        Remove-Item -LiteralPath $resolvedAssetDirectory -Recurse -Force
    }
}
