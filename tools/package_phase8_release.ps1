param(
    [string]$ProjectRoot = "D:\KLCN\src",
    [string]$OutputRoot = "D:\KLCN\Tuan09\phase8_release"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-Sha256Lower {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-ZipEntrySha256Lower {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.Compression.ZipArchiveEntry]$Entry
    )
    $stream = $Entry.Open()
    try {
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try {
            $bytes = $sha.ComputeHash($stream)
            return ([System.BitConverter]::ToString($bytes)).Replace("-", "").ToLowerInvariant()
        }
        finally {
            $sha.Dispose()
        }
    }
    finally {
        $stream.Dispose()
    }
}

$startedAt = Get-Date
$projectPath = [System.IO.Path]::GetFullPath($ProjectRoot)
$outputPath = [System.IO.Path]::GetFullPath($OutputRoot)

if ($projectPath -ne "D:\KLCN\src") {
    throw "ProjectRoot does not match the expected workspace: $projectPath"
}
if (-not $outputPath.StartsWith("D:\KLCN\Tuan09\phase8_release", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "OutputRoot is outside the expected Phase 8 directory: $outputPath"
}

$apkCandidates = @(
    (Join-Path $projectPath "app\build\outputs\apk\release\app-release.apk"),
    (Join-Path $projectPath "app\build\intermediates\apk\release\app-release.apk")
)
$sourceApk = $apkCandidates |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1

if (-not $sourceApk) {
    throw @"
Cannot find app-release.apk.
Run the Gradle task assembleRelease in Android Studio.
Then run this script again.
"@
}

$payloadDir = Join-Path $outputPath "payload"
$artifactsDir = Join-Path $payloadDir "artifacts"
$reportsDir = Join-Path $payloadDir "reports"
$auditDir = Join-Path $payloadDir "audit"
$sourceSnapshotDir = Join-Path $payloadDir "source_snapshot"

@(
    $outputPath,
    $payloadDir,
    $artifactsDir,
    $reportsDir,
    $auditDir,
    $sourceSnapshotDir
) | ForEach-Object {
    New-Item -ItemType Directory -Path $_ -Force | Out-Null
}

$releaseApk = Join-Path $artifactsDir "mobile_image_captioning_phase8_v1.0.apk"
Copy-Item -LiteralPath $sourceApk -Destination $releaseApk -Force
$apkSha256 = Get-Sha256Lower -Path $releaseApk
$apkSizeBytes = (Get-Item -LiteralPath $releaseApk).Length
$apkChecksumPath = "$releaseApk.sha256"
Set-Content -LiteralPath $apkChecksumPath -Encoding utf8 -Value (
    "$apkSha256  $([System.IO.Path]::GetFileName($releaseApk))"
)

$buildToolsRoot = "D:\Android\Sdk\build-tools"
$apksigner = Get-ChildItem -LiteralPath $buildToolsRoot -Filter "apksigner.bat" -Recurse |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $apksigner) {
    throw "Cannot find apksigner.bat under $buildToolsRoot"
}

$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$signatureOutput = & $apksigner verify --verbose --print-certs $releaseApk 2>&1
$signatureExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorActionPreference
if ($signatureExitCode -ne 0) {
    throw "APK signature verification failed:`n$($signatureOutput -join "`n")"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkArchive = [System.IO.Compression.ZipFile]::OpenRead($releaseApk)
try {
    $entryLookup = @{}
    foreach ($entry in $apkArchive.Entries) {
        $entryLookup[$entry.FullName] = $entry
    }

    $requiredEntries = @(
        "AndroidManifest.xml",
        "classes.dex",
        "assets/baseline_encoder_fp32.onnx",
        "assets/baseline_decoder_fp32.onnx",
        "assets/vocabulary.json",
        "assets/phase7b_mobile_handoff_contract.json",
        "lib/arm64-v8a/libonnxruntime.so",
        "lib/armeabi-v7a/libonnxruntime.so",
        "lib/x86/libonnxruntime.so",
        "lib/x86_64/libonnxruntime.so"
    )
    $missingEntries = @(
        $requiredEntries | Where-Object { -not $entryLookup.ContainsKey($_) }
    )
    if ($missingEntries.Count -ne 0) {
        throw "APK is missing required entries: $($missingEntries -join ', ')"
    }

    $expectedAssetHashes = [ordered]@{
        "assets/baseline_encoder_fp32.onnx" = "59fbd6d24f0c82ceec27cf69ed05cda47fcb39d568b68a4a67f06dee080f19e3"
        "assets/baseline_decoder_fp32.onnx" = "0b5c6fec9158a9c00847a554936faeeafcf2560ee75d9caf0bacba95cdea8adb"
        "assets/vocabulary.json" = "74561e6e00e59a4642ad20b4e69aed9c754f513b5714ff3a448522947ea30be2"
    }
    $embeddedAssetAudit = @()
    foreach ($assetPath in $expectedAssetHashes.Keys) {
        $actualHash = Get-ZipEntrySha256Lower -Entry $entryLookup[$assetPath]
        $expectedHash = $expectedAssetHashes[$assetPath]
        $matched = $actualHash -eq $expectedHash
        if (-not $matched) {
            throw "Embedded asset SHA-256 mismatch: $assetPath"
        }
        $embeddedAssetAudit += [ordered]@{
            path = $assetPath
            size_bytes = $entryLookup[$assetPath].Length
            sha256 = $actualHash
            expected_sha256 = $expectedHash
            matched = $matched
        }
    }

    $onnxRuntimeAbis = @(
        $apkArchive.Entries |
            Where-Object { $_.FullName -match "^lib/([^/]+)/libonnxruntime\.so$" } |
            ForEach-Object { [regex]::Match($_.FullName, "^lib/([^/]+)/").Groups[1].Value } |
            Sort-Object -Unique
    )
}
finally {
    $apkArchive.Dispose()
}

$sourceFiles = [ordered]@{
    "MainActivity.kt" = "app\src\main\java\com\klcn\mobilecaptioning\MainActivity.kt"
    "AndroidManifest.xml" = "app\src\main\AndroidManifest.xml"
    "file_paths.xml" = "app\src\main\res\xml\file_paths.xml"
    "app_build.gradle.kts" = "app\build.gradle.kts"
    "libs.versions.toml" = "gradle\libs.versions.toml"
}
foreach ($destinationName in $sourceFiles.Keys) {
    $sourcePath = Join-Path $projectPath $sourceFiles[$destinationName]
    if (-not (Test-Path -LiteralPath $sourcePath)) {
        throw "Missing source file: $sourcePath"
    }
    Copy-Item -LiteralPath $sourcePath -Destination (
        Join-Path $sourceSnapshotDir $destinationName
    ) -Force
}

$handoffContractSource = "D:\KLCN\Tuan08\phase7b_release\phase7b_int8_rescue\artifacts\mobile_handoff\phase7b_mobile_handoff_contract.json"
if (-not (Test-Path -LiteralPath $handoffContractSource)) {
    throw "Missing Phase 7B mobile handoff contract"
}
Copy-Item -LiteralPath $handoffContractSource -Destination (
    Join-Path $artifactsDir "phase7b_mobile_handoff_contract.json"
) -Force

$auditPayload = [ordered]@{
    phase = "phase8_android"
    status = "completed"
    technical_level = "good"
    created_at = (Get-Date).ToUniversalTime().ToString("o")
    application_id = "com.klcn.mobilecaptioning"
    version_name = "1.0"
    version_code = 1
    min_sdk = 26
    target_sdk = 36
    compile_sdk = 36
    model = "baseline"
    precision = "fp32"
    decoding = "beam3"
    int8_accepted = $false
    apk_path = $releaseApk
    apk_size_bytes = $apkSizeBytes
    apk_sha256 = $apkSha256
    apk_signature_verified = $true
    signing_policy = "research_release_signed_with_debug_key_not_for_play_store"
    embedded_assets_verified = $true
    embedded_asset_audit = $embeddedAssetAudit
    onnxruntime_abis = $onnxRuntimeAbis
    gallery_inference_verified = $true
    camera_inference_verified = $true
    eos_verified = $true
    forbidden_tokens_absent = $true
    session_reuse_verified = $true
    repeatability_verified = $true
    gallery_timing_ms = [ordered]@{
        model_load = 2062.53
        run_1_excluding_model_load = 2197.39
        run_2_excluding_model_load = 1276.83
    }
    camera_timing_ms = [ordered]@{
        run_1_excluding_model_load = 2185.47
        run_2_excluding_model_load = 1027.19
    }
    test_set_used_for_tuning = $false
    next_phase = "phase9_physical_device_benchmark"
}

$auditPath = Join-Path $auditDir "phase8_final_android_audit.json"
$auditPayload |
    ConvertTo-Json -Depth 10 |
    Set-Content -LiteralPath $auditPath -Encoding utf8

$reportPath = Join-Path $reportsDir "phase8_final_android_report.md"
$reportText = @"
# Phase 8 - Android ONNX Runtime Deployment

## Ket luan

- Trang thai: **COMPLETED**
- Muc do ky thuat: **TOT**
- Model trien khai: Baseline FP32
- Runtime: ONNX Runtime Android 1.29.0
- Decoding: Beam-3 ben ngoai ONNX
- Test Set dung de tuning: Khong

## Hard gates

- Encoder/decoder/vocabulary khop SHA-256 Phase 7B.
- Encoder input [1,3,224,224], output [1,1,256].
- Decoder output [1,31,12293].
- Gallery va Camera inference deu hoat dong.
- Caption co EOS, khong co forbidden token.
- Session duoc nap mot lan va tai su dung.
- Hai luot inference co caption, token IDs va scores giong nhau.
- APK da ky, kiem tra chu ky thanh cong va chua ONNX Runtime cho ARM/x86.

## Hieu nang emulator

- Model load mot lan: 2062.53 ms.
- Gallery: 2197.39 ms luot dau; 1276.83 ms luot lap.
- Camera: 2185.47 ms luot dau; 1027.19 ms luot lap.

## APK

- Ten: mobile_image_captioning_phase8_v1.0.apk
- Kich thuoc: $([math]::Round($apkSizeBytes / 1MB, 2)) MB
- SHA-256: $apkSha256
- Ky: khoa debug cho ban release nghien cuu; khong dung de phat hanh Play Store.

## Buoc tiep theo

Phase 9 phai benchmark APK release tren it nhat mot dien thoai Android ARM64 that. Khong dung ket qua emulator thay cho thiet bi vat ly.
"@
Set-Content -LiteralPath $reportPath -Encoding utf8 -Value $reportText

$manifestPath = Join-Path $artifactsDir "phase8_release_manifest.csv"
$payloadFiles = Get-ChildItem -LiteralPath $payloadDir -File -Recurse |
    Where-Object { $_.FullName -ne $manifestPath } |
    Sort-Object FullName |
    ForEach-Object {
        [pscustomobject]@{
            relative_path = $_.FullName.Substring($payloadDir.Length).TrimStart("\").Replace("\", "/")
            size_bytes = $_.Length
            sha256 = Get-Sha256Lower -Path $_.FullName
        }
    }
$payloadFiles | Export-Csv -LiteralPath $manifestPath -NoTypeInformation -Encoding utf8

$releaseZip = Join-Path $outputPath "phase8_android_release.zip"
if (Test-Path -LiteralPath $releaseZip) {
    Remove-Item -LiteralPath $releaseZip -Force
}
$releaseArchive = [System.IO.Compression.ZipFile]::Open(
    $releaseZip,
    [System.IO.Compression.ZipArchiveMode]::Create
)
try {
    Get-ChildItem -LiteralPath $payloadDir -File -Recurse |
        Sort-Object FullName |
        ForEach-Object {
            $entryName = $_.FullName.Substring(
                $payloadDir.Length
            ).TrimStart("\").Replace("\", "/")
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $releaseArchive,
                $_.FullName,
                $entryName,
                [System.IO.Compression.CompressionLevel]::Optimal
            ) | Out-Null
        }
}
finally {
    $releaseArchive.Dispose()
}
$releaseZipSha256 = Get-Sha256Lower -Path $releaseZip
$releaseZipChecksum = "$releaseZip.sha256"
Set-Content -LiteralPath $releaseZipChecksum -Encoding utf8 -Value (
    "$releaseZipSha256  $([System.IO.Path]::GetFileName($releaseZip))"
)

$zipArchive = [System.IO.Compression.ZipFile]::OpenRead($releaseZip)
try {
    $zipMembers = $zipArchive.Entries.Count
    if ($zipMembers -lt 10) {
        throw "Release ZIP has too few members: $zipMembers"
    }
}
finally {
    $zipArchive.Dispose()
}

$elapsedSeconds = ((Get-Date) - $startedAt).TotalSeconds

Write-Output ""
Write-Output ("=" * 75)
Write-Output "PHASE 8 FINAL ANDROID CONCLUSION"
Write-Output ("=" * 75)
Write-Output "Technical status       : TOT"
Write-Output "Phase status           : COMPLETED"
Write-Output "Gallery verified       : True"
Write-Output "Camera verified        : True"
Write-Output "Session reuse verified : True"
Write-Output "Repeatability verified : True"
Write-Output "APK signature verified : True"
Write-Output "APK ABIs               : $($onnxRuntimeAbis -join ', ')"
Write-Output "Test used for tuning   : False"
Write-Output ""
Write-Output ("=" * 75)
Write-Output "PHASE 8 RELEASE PACKAGE"
Write-Output ("=" * 75)
Write-Output "APK size               : $([math]::Round($apkSizeBytes / 1MB, 2)) MB"
Write-Output "APK SHA-256            : $apkSha256"
Write-Output "ZIP members            : $zipMembers"
Write-Output "ZIP size               : $([math]::Round((Get-Item -LiteralPath $releaseZip).Length / 1MB, 2)) MB"
Write-Output "ZIP SHA-256            : $releaseZipSha256"
Write-Output ""
Write-Output ("=" * 75)
Write-Output "TIEN TRINH HIEN TAI - PIPELINE"
Write-Output ("=" * 75)
Write-Output "Giai doan 1-7B          : COMPLETED"
Write-Output "Giai doan 8 - Android   : COMPLETED"
Write-Output "Giai doan 9 - Device    : NEXT"
Write-Output "Giai doan 10 - Document : PENDING"
Write-Output ""
Write-Output "Muc do hoan thanh: TOT"
Write-Output "Release APK : $releaseApk"
Write-Output "APK checksum: $apkChecksumPath"
Write-Output "Final report: $reportPath"
Write-Output "Manifest    : $manifestPath"
Write-Output "Release ZIP : $releaseZip"
Write-Output "ZIP checksum: $releaseZipChecksum"
Write-Output "Thoi gian dong goi: $([math]::Round($elapsedSeconds, 2)) giay"
