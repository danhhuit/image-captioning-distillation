param(
    [string]$BackendRoot = "",
    [switch]$SkipAndroidBuild
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path -LiteralPath (
    Join-Path $PSScriptRoot ".."
)).Path

if ([string]::IsNullOrWhiteSpace($BackendRoot)) {
    $BackendRoot = Join-Path (Split-Path $RepoRoot -Parent) "backend"
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$Command,
        [Parameter(Mandatory = $true)]
        [string]$FailureMessage
    )
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw $FailureMessage
    }
}

Push-Location $RepoRoot
try {
    Write-Host "1/6 Git diff hygiene" -ForegroundColor Cyan
    Invoke-Checked -Command {
        git diff --check
    } -FailureMessage "git diff --check failed."

    $trackedSensitive = @(
        git ls-files |
            Where-Object {
                $_ -match '(^|/)\.env$' -or
                $_ -match '\.(jks|keystore)$' -or
                $_ -match '(^|/)(hs_err_pid|replay_pid).*\.log$'
            }
    )
    if ($trackedSensitive.Count -gt 0) {
        throw (
            "Sensitive/generated files are tracked:`n" +
            ($trackedSensitive -join "`n")
        )
    }

    Write-Host "2/6 Git LFS configuration" -ForegroundColor Cyan
    Invoke-Checked -Command {
        git lfs version
    } -FailureMessage "Git LFS is not installed."
    $lfsAttribute = git check-attr filter -- (
        "app/src/main/assets/v2/t1_s1/encoder_int8.onnx"
    )
    if ($lfsAttribute -notmatch "filter: lfs") {
        throw "ONNX files are not configured for Git LFS."
    }

    Write-Host "3/6 V2 model integrity" -ForegroundColor Cyan
    $registryPath = Join-Path $RepoRoot (
        "app/src/main/assets/v2/phase7j_android_model_registry.json"
    )
    $registry = Get-Content -LiteralPath $registryPath -Raw |
        ConvertFrom-Json
    foreach ($pair in $registry.pairs) {
        $pairDirectory = $pair.pair.ToLowerInvariant().Replace("-", "_")
        foreach ($componentName in @("encoder", "decoder")) {
            $component = $pair.$componentName
            $modelPath = Join-Path $RepoRoot (
                "app/src/main/assets/v2/{0}/{1}_int8.onnx" -f
                $pairDirectory,
                $componentName
            )
            $model = Get-Item -LiteralPath $modelPath
            if ($model.Length -ne [int64]$component.size_bytes) {
                throw "Model size mismatch: $modelPath"
            }
            $digest = (
                Get-FileHash -LiteralPath $modelPath -Algorithm SHA256
            ).Hash.ToLowerInvariant()
            if ($digest -ne $component.sha256) {
                throw "Model SHA-256 mismatch: $modelPath"
            }
        }
    }

    Write-Host "4/6 Android checks" -ForegroundColor Cyan
    if ($SkipAndroidBuild) {
        Write-Warning "Android build was skipped explicitly."
    } else {
        Invoke-Checked -Command {
            & ".\gradlew.bat" `
                :app:testDebugUnitTest `
                :app:lintDebug `
                :app:assembleDebug
        } -FailureMessage "Android test/lint/build gate failed."
    }

    Write-Host "5/6 Backend syntax and Compose" -ForegroundColor Cyan
    if (-not (Test-Path -LiteralPath $BackendRoot -PathType Container)) {
        throw "Backend companion not found: $BackendRoot"
    }
    Push-Location $BackendRoot
    try {
        Invoke-Checked -Command {
            python -m compileall -q app
        } -FailureMessage "Backend Python syntax check failed."

        $composeArgs = @("compose")
        if (-not (Test-Path -LiteralPath ".env")) {
            $composeArgs += @("--env-file", ".env.example")
        }
        $composeArgs += @("config", "--quiet")
        Invoke-Checked -Command {
            docker @composeArgs
        } -FailureMessage "Docker Compose validation failed."
    } finally {
        Pop-Location
    }

    Write-Host "6/6 Final status" -ForegroundColor Cyan
    git status --short
    Write-Host (
        "PRE-PUSH CHECK PASSED. Review the status above before staging."
    ) -ForegroundColor Green
} finally {
    Pop-Location
}
