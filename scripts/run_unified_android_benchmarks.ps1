param(
    [string]$DeviceSerial = "emulator-5554",
    [switch]$AllowEmulator,
    [ValidateRange(1, 20)]
    [int]$WarmupRuns = 5,
    [ValidateRange(10, 100)]
    [int]$MeasuredRuns = 30,
    [string[]]$ModelIds = @(
        "T1_S1_SEQUENCE",
        "T1_S1",
        "T1_S1_HYBRID",
        "T2_S2",
        "T3_S3",
        "MSCOCO_CLIP_S1_LOGIT",
        "F30K_T1_S1_SEQUENCE",
        "F30K_T1_S1_DENSE_MAP",
        "F30K_T1_S1_HYBRID",
        "F30K_T2_S2_SEQUENCE",
        "F30K_T3_S3_EXPERIMENTAL"
    ),
    [string]$ResultRoot =
        "D:\KLCN\output_codex\04_thuc_nghiem\mobile_compute_cost\unified_android_results",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$adb = @(
    (Get-Command adb -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue),
    (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe")
) | Where-Object {
    $_ -and (Test-Path -LiteralPath $_ -PathType Leaf)
} | Select-Object -First 1
if (-not $adb) {
    throw "Không tìm thấy adb."
}

& $adb -s $DeviceSerial get-state | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Thiết bị $DeviceSerial chưa sẵn sàng."
}

if (-not $SkipBuild) {
    Push-Location $repositoryRoot
    try {
        & .\gradlew.bat --no-daemon :app:assembleRelease :app:assembleAndroidTest
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed: $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

$appApk = Join-Path $repositoryRoot "app\build\outputs\apk\release\app-release.apk"
$testApk = Join-Path $repositoryRoot `
    "app\build\outputs\apk\androidTest\release\app-release-androidTest.apk"
if (-not (Test-Path -LiteralPath $appApk -PathType Leaf)) {
    throw "Thiếu $appApk"
}
if (-not (Test-Path -LiteralPath $testApk -PathType Leaf)) {
    throw "Thiếu $testApk"
}

& $adb -s $DeviceSerial install -r $appApk
if ($LASTEXITCODE -ne 0) { throw "Cài APK ứng dụng thất bại." }
& $adb -s $DeviceSerial install -r $testApk
if ($LASTEXITCODE -ne 0) { throw "Cài APK kiểm thử thất bại." }

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$resultDirectory = Join-Path $ResultRoot $timestamp
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null
$allowEmulatorValue = if ($AllowEmulator) { "true" } else { "false" }
$rows = [System.Collections.Generic.List[object]]::new()
$failures = [System.Collections.Generic.List[string]]::new()

foreach ($modelId in $ModelIds) {
    Write-Host "Benchmarking $modelId ..."
    $logPath = Join-Path $resultDirectory "$modelId.instrumentation.log"
    $output = & $adb -s $DeviceSerial shell am instrument -w -r `
        -e class com.klcn.mobilecaptioning.UnifiedModelAndroidBenchmarkTest `
        -e modelId $modelId `
        -e warmupRuns $WarmupRuns `
        -e measuredRuns $MeasuredRuns `
        -e allowEmulatorSmoke $allowEmulatorValue `
        com.klcn.mobilecaptioning.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $output | Set-Content -LiteralPath $logPath -Encoding utf8
    if (($output -join "`n") -notmatch "OK \(1 test\)") {
        $failures.Add($modelId)
        Write-Warning "$modelId failed. Xem $logPath"
        continue
    }

    $fileName = "UNIFIED_BENCH_$modelId.json"
    $remotePath =
        "/sdcard/Android/data/com.klcn.mobilecaptioning/files/benchmarks/$fileName"
    $localPath = Join-Path $resultDirectory $fileName
    & $adb -s $DeviceSerial pull $remotePath $localPath | Out-Null
    if ($LASTEXITCODE -ne 0 -or
        -not (Test-Path -LiteralPath $localPath -PathType Leaf)) {
        $failures.Add($modelId)
        Write-Warning "Không kéo được JSON của $modelId."
        continue
    }

    $result = Get-Content -Raw -LiteralPath $localPath | ConvertFrom-Json
    $rows.Add([pscustomobject]@{
        ModelId = $result.model.model_id
        Dataset = $result.model.dataset
        Pair = $result.model.pair_id
        Student = $result.model.student
        Method = $result.model.kd_method
        Precision = $result.model.runtime_precision
        ParametersM = [math]::Round(
            [double]$result.model.student_parameters / 1000000.0,
            2
        )
        PackageMiB = [math]::Round(
            [double]$result.model.package_bytes / 1MB,
            2
        )
        ModelLoadMs = [math]::Round(
            [double]$result.latency_ms.model_load,
            2
        )
        PreprocessP50Ms = [math]::Round(
            [double]$result.latency_ms.preprocessing.p50,
            2
        )
        EncoderP50Ms = [math]::Round(
            [double]$result.latency_ms.encoder.p50,
            2
        )
        DecoderP50Ms = [math]::Round(
            [double]$result.latency_ms.beam3_decoder.p50,
            2
        )
        E2EMeanMs = [math]::Round(
            [double]$result.latency_ms.end_to_end.mean,
            2
        )
        E2EP50Ms = [math]::Round(
            [double]$result.latency_ms.end_to_end.p50,
            2
        )
        E2EP95Ms = [math]::Round(
            [double]$result.latency_ms.end_to_end.p95,
            2
        )
        PssDeltaKiB = [int]$result.memory.pss_delta_kib
        PssDeltaMiB = [math]::Round(
            [double]$result.memory.pss_delta_kib / 1024.0,
            2
        )
        TokenIdsIdentical = [bool]$result.repeatability.token_ids_identical
    })
}

$csvPath = Join-Path $resultDirectory "UNIFIED_ANDROID_BENCHMARK_SUMMARY.csv"
$jsonPath = Join-Path $resultDirectory "UNIFIED_ANDROID_BENCHMARK_SUMMARY.json"
$rows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding utf8
[ordered]@{
    schema_version = "unified_android_benchmark_summary_v1"
    created_at_local = (Get-Date).ToString("o")
    device_serial = $DeviceSerial
    emulator_allowed = [bool]$AllowEmulator
    warmup_runs = $WarmupRuns
    measured_runs = $MeasuredRuns
    successful_models = $rows.Count
    failed_models = @($failures)
    rows = @($rows)
} | ConvertTo-Json -Depth 8 |
    Set-Content -LiteralPath $jsonPath -Encoding utf8

Write-Host "Summary JSON: $jsonPath"
Write-Host "Summary CSV : $csvPath"
$rows | Format-Table -AutoSize
if ($failures.Count -gt 0) {
    throw "Benchmark thất bại: $($failures -join ', ')"
}
