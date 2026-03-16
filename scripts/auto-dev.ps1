param(
    [string]$TaskFile,
    [string]$ImplementCommand,
    [string]$ReportRoot,
    [string]$BaseUrl = "http://localhost:8080/api",
    [int]$HealthTimeoutSeconds = 240,
    [switch]$RequireRealDiagnosisModel,
    [switch]$SkipImplementCommand,
    [switch]$SkipFrontendBuild,
    [switch]$SkipBackendBuild,
    [switch]$SkipCompose,
    [switch]$SkipInit,
    [switch]$SkipSmoke,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$script:DefaultReportRoot = Join-Path $script:WorkspaceRoot "reports\auto-dev"
$script:ExcludedDirectoryNames = @(
    ".git",
    ".idea",
    ".vscode",
    "node_modules",
    "dist",
    "target",
    "build",
    "__pycache__",
    "datasets",
    "models",
    "reports"
)
$script:ExcludedFilePatterns = @(
    "*.log"
)

function Write-Step {
    param([string]$Message)

    $timestamp = Get-Date -Format "HH:mm:ss"
    Write-Host "[$timestamp] $Message"
}

function Resolve-RepoPath {
    param([string]$PathValue)

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $null
    }

    $fullPath = if ([System.IO.Path]::IsPathRooted($PathValue)) {
        $PathValue
    } else {
        Join-Path $script:WorkspaceRoot $PathValue
    }

    if (Test-Path -LiteralPath $fullPath) {
        return (Resolve-Path -LiteralPath $fullPath).Path
    }

    return [System.IO.Path]::GetFullPath($fullPath)
}

function Get-RelativeRepoPath {
    param([string]$PathValue)

    $rootUri = New-Object System.Uri(($script:WorkspaceRoot.TrimEnd("\") + "\"))
    $pathUri = New-Object System.Uri($PathValue)
    $relativeUri = $rootUri.MakeRelativeUri($pathUri)
    return ([System.Uri]::UnescapeDataString($relativeUri.ToString()) -replace "\\", "/")
}

function Resolve-CommandPath {
    param([string[]]$Candidates)

    foreach ($candidate in $Candidates) {
        $command = Get-Command $candidate -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($command) {
            return $command.Source
        }
    }

    throw "Unable to find any command from: $($Candidates -join ', ')"
}

function Get-BooleanOrDefault {
    param(
        $Value,
        [bool]$DefaultValue
    )

    if ($null -eq $Value) {
        return $DefaultValue
    }

    return [bool]$Value
}

function Get-DefaultTaskConfig {
    return [ordered]@{
        title = "Ad-hoc automation run"
        goal = "Run the local validation pipeline for the current workspace state."
        allowedPaths = @()
        implementCommand = ""
        baseUrl = $null
        requireRealDiagnosisModel = $null
        composeServices = @()
        checks = [ordered]@{
            frontendBuild = $true
            backendBuild = $true
            composeUp = $false
            systemInit = $false
            smokeTest = $false
        }
    }
}

function Read-TaskConfig {
    param([string]$TaskFilePath)

    $config = Get-DefaultTaskConfig
    if (-not $TaskFilePath) {
        return $config
    }

    $resolvedTaskPath = Resolve-RepoPath $TaskFilePath
    $raw = Get-Content -LiteralPath $resolvedTaskPath -Raw -Encoding UTF8
    $parsed = $raw | ConvertFrom-Json

    foreach ($key in @("title", "goal", "implementCommand", "baseUrl", "requireRealDiagnosisModel")) {
        if ($parsed.PSObject.Properties.Name -contains $key) {
            $config[$key] = $parsed.$key
        }
    }

    if ($parsed.PSObject.Properties.Name -contains "allowedPaths" -and $parsed.allowedPaths) {
        $config.allowedPaths = @($parsed.allowedPaths)
    }

    if ($parsed.PSObject.Properties.Name -contains "composeServices" -and $parsed.composeServices) {
        $config.composeServices = @($parsed.composeServices)
    }

    if ($parsed.PSObject.Properties.Name -contains "checks" -and $parsed.checks) {
        foreach ($checkName in @("frontendBuild", "backendBuild", "composeUp", "systemInit", "smokeTest")) {
            if ($parsed.checks.PSObject.Properties.Name -contains $checkName) {
                $config.checks[$checkName] = [bool]$parsed.checks.$checkName
            }
        }
    }

    return $config
}

function Test-SnapshotCandidate {
    param([System.IO.FileInfo]$File)

    $relativePath = Get-RelativeRepoPath $File.FullName
    $segments = $relativePath.Split("/")

    if ($segments.Length -gt 1) {
        foreach ($segment in $segments[0..($segments.Length - 2)]) {
            if ($script:ExcludedDirectoryNames -contains $segment) {
                return $false
            }
        }
    }

    foreach ($pattern in $script:ExcludedFilePatterns) {
        if ($File.Name -like $pattern) {
            return $false
        }
    }

    return $true
}

function Get-RepoSnapshot {
    param([string]$RootPath)

    $snapshot = @{}
    $pendingDirectories = New-Object System.Collections.Generic.Stack[string]
    $pendingDirectories.Push($RootPath)

    $files = New-Object System.Collections.Generic.List[System.IO.FileInfo]
    while ($pendingDirectories.Count -gt 0) {
        $currentDirectory = $pendingDirectories.Pop()
        foreach ($entry in Get-ChildItem -LiteralPath $currentDirectory -Force) {
            if ($entry.PSIsContainer) {
                if ($script:ExcludedDirectoryNames -contains $entry.Name) {
                    continue
                }

                $pendingDirectories.Push($entry.FullName)
                continue
            }

            $files.Add($entry)
        }
    }

    foreach ($file in $files) {
        if (-not (Test-SnapshotCandidate $file)) {
            continue
        }

        $relativePath = Get-RelativeRepoPath $file.FullName
        $snapshot[$relativePath] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    }

    return $snapshot
}

function Compare-RepoSnapshot {
    param(
        [hashtable]$Before,
        [hashtable]$After
    )

    $added = New-Object System.Collections.Generic.List[string]
    $modified = New-Object System.Collections.Generic.List[string]
    $deleted = New-Object System.Collections.Generic.List[string]

    foreach ($path in $After.Keys) {
        if (-not $Before.ContainsKey($path)) {
            $added.Add($path)
            continue
        }

        if ($After[$path] -ne $Before[$path]) {
            $modified.Add($path)
        }
    }

    foreach ($path in $Before.Keys) {
        if (-not $After.ContainsKey($path)) {
            $deleted.Add($path)
        }
    }

    return [ordered]@{
        added = @($added | Sort-Object)
        modified = @($modified | Sort-Object)
        deleted = @($deleted | Sort-Object)
    }
}

function Get-ScopeViolations {
    param(
        [string[]]$ChangedPaths,
        [string[]]$AllowedPaths
    )

    if (-not $AllowedPaths -or $AllowedPaths.Count -eq 0) {
        return @()
    }

    $normalizedAllowed = @(
        $AllowedPaths |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            ForEach-Object { ($_ -replace "\\", "/").Trim().TrimStart(".").TrimStart("/") } |
            Where-Object { $_ }
    )

    if ($normalizedAllowed.Count -eq 0) {
        return @()
    }

    $violations = New-Object System.Collections.Generic.List[string]
    foreach ($changedPath in $ChangedPaths) {
        $normalizedPath = ($changedPath -replace "\\", "/").Trim()
        $allowed = $false

        foreach ($prefix in $normalizedAllowed) {
            if ($normalizedPath -eq $prefix -or $normalizedPath.StartsWith("$prefix/")) {
                $allowed = $true
                break
            }
        }

        if (-not $allowed) {
            $violations.Add($normalizedPath)
        }
    }

    return @($violations | Sort-Object -Unique)
}

function Invoke-ExternalCommand {
    param(
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory,
        [string]$StdOutPath,
        [string]$StdErrPath
    )

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    Push-Location $WorkingDirectory
    try {
        & $FilePath @ArgumentList 1> $StdOutPath 2> $StdErrPath
        $exitCode = if ($null -ne $LASTEXITCODE) { [int]$LASTEXITCODE } elseif ($?) { 0 } else { 1 }
    } finally {
        Pop-Location
    }
    $stopwatch.Stop()

    if ($exitCode -ne 0) {
        throw "Command failed with exit code ${exitCode}: $FilePath $($ArgumentList -join ' ')"
    }

    return [ordered]@{
        command = @($FilePath) + $ArgumentList
        workingDirectory = $WorkingDirectory
        exitCode = $exitCode
        durationSeconds = [Math]::Round($stopwatch.Elapsed.TotalSeconds, 2)
        stdout = $StdOutPath
        stderr = $StdErrPath
    }
}

function Wait-ForHealthEndpoint {
    param(
        [string]$HealthUrl,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastError = $null

    while ((Get-Date) -lt $deadline) {
        try {
            return Invoke-RestMethod -Method Get -Uri $HealthUrl -TimeoutSec 10
        } catch {
            $lastError = $_.Exception.Message
            Start-Sleep -Seconds 5
        }
    }

    throw "Timed out waiting for $HealthUrl. Last error: $lastError"
}

function ConvertTo-PrettyJson {
    param($Value)

    return ($Value | ConvertTo-Json -Depth 10)
}

function Write-MarkdownReport {
    param(
        $Report,
        [string]$OutputPath
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Auto Dev Run")
    $lines.Add("")
    $lines.Add("- Run ID: $($Report.runId)")
    $lines.Add("- Overall status: $($Report.overallStatus)")
    $lines.Add("- Generated at: $($Report.generatedAt)")
    $lines.Add("- Workspace: $($Report.workspace)")
    $lines.Add("")
    $lines.Add("## Task")
    $lines.Add("")
    $lines.Add("- Title: $($Report.task.title)")
    $lines.Add("- Goal: $($Report.task.goal)")
    $lines.Add("- Task file: $($Report.task.path)")
    $lines.Add("- Allowed paths: $(([string[]]$Report.task.allowedPaths) -join ', ')")
    $lines.Add("- Implement command: $($Report.task.implementCommand)")
    $lines.Add("")
    $lines.Add("## Steps")
    $lines.Add("")

    foreach ($step in $Report.steps) {
        $lines.Add("- $($step.name): $($step.status) ($($step.durationSeconds)s)")
        if ($step.error) {
            $lines.Add("  Error: $($step.error)")
        }
    }

    $lines.Add("")
    $lines.Add("## Changes")
    $lines.Add("")
    $lines.Add("- Added: $(([string[]]$Report.changes.added) -join ', ')")
    $lines.Add("- Modified: $(([string[]]$Report.changes.modified) -join ', ')")
    $lines.Add("- Deleted: $(([string[]]$Report.changes.deleted) -join ', ')")
    $lines.Add("- Scope violations: $(([string[]]$Report.changes.scopeViolations) -join ', ')")
    $lines.Add("")
    $lines.Add("## Artifacts")
    $lines.Add("")
    $lines.Add("- JSON report: $($Report.artifacts.jsonReport)")
    $lines.Add("- Step logs: $($Report.artifacts.stepLogDirectory)")

    Set-Content -LiteralPath $OutputPath -Value ($lines -join [Environment]::NewLine) -Encoding UTF8
}

function Invoke-Step {
    param(
        [string]$Name,
        [bool]$Enabled,
        [scriptblock]$Action
    )

    $startedAt = Get-Date
    if (-not $Enabled) {
        return [ordered]@{
            name = $Name
            status = "skipped"
            startedAt = $startedAt.ToString("o")
            endedAt = $startedAt.ToString("o")
            durationSeconds = 0
            details = $null
            error = $null
        }
    }

    Write-Step $Name
    try {
        $details = & $Action
        $endedAt = Get-Date
        return [ordered]@{
            name = $Name
            status = "passed"
            startedAt = $startedAt.ToString("o")
            endedAt = $endedAt.ToString("o")
            durationSeconds = [Math]::Round(($endedAt - $startedAt).TotalSeconds, 2)
            details = $details
            error = $null
        }
    } catch {
        $endedAt = Get-Date
        return [ordered]@{
            name = $Name
            status = "failed"
            startedAt = $startedAt.ToString("o")
            endedAt = $endedAt.ToString("o")
            durationSeconds = [Math]::Round(($endedAt - $startedAt).TotalSeconds, 2)
            details = $null
            error = $_.Exception.Message
        }
    }
}

$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$resolvedReportRoot = if ($ReportRoot) { Resolve-RepoPath $ReportRoot } else { $script:DefaultReportRoot }
$runDirectory = Join-Path $resolvedReportRoot $runId
$stepLogDirectory = Join-Path $runDirectory "steps"
New-Item -ItemType Directory -Path $stepLogDirectory -Force | Out-Null

$resolvedTaskPath = $null
if ($TaskFile) {
    $resolvedTaskPath = Resolve-RepoPath $TaskFile
}

$taskConfig = Read-TaskConfig $TaskFile
$effectiveImplementCommand = if ($PSBoundParameters.ContainsKey("ImplementCommand")) { $ImplementCommand } else { [string]$taskConfig.implementCommand }
$effectiveBaseUrl = if ($PSBoundParameters.ContainsKey("BaseUrl")) { $BaseUrl } elseif ($taskConfig.baseUrl) { [string]$taskConfig.baseUrl } else { "http://localhost:8080/api" }
$effectiveRequireRealDiagnosisModel = if ($PSBoundParameters.ContainsKey("RequireRealDiagnosisModel")) {
    $RequireRealDiagnosisModel.IsPresent
} else {
    Get-BooleanOrDefault $taskConfig.requireRealDiagnosisModel $false
}

$runChecks = [ordered]@{
    implementCommand = (-not $SkipImplementCommand.IsPresent) -and (-not [string]::IsNullOrWhiteSpace($effectiveImplementCommand))
    frontendBuild = (Get-BooleanOrDefault $taskConfig.checks.frontendBuild $true) -and (-not $SkipFrontendBuild.IsPresent)
    backendBuild = (Get-BooleanOrDefault $taskConfig.checks.backendBuild $true) -and (-not $SkipBackendBuild.IsPresent)
    composeUp = (Get-BooleanOrDefault $taskConfig.checks.composeUp $false) -and (-not $SkipCompose.IsPresent)
    systemInit = (Get-BooleanOrDefault $taskConfig.checks.systemInit $false) -and (-not $SkipInit.IsPresent)
    smokeTest = (Get-BooleanOrDefault $taskConfig.checks.smokeTest $false) -and (-not $SkipSmoke.IsPresent)
}

$npmPath = if ((-not $DryRun.IsPresent) -and $runChecks.frontendBuild) { Resolve-CommandPath @("npm.cmd", "npm") } else { $null }
$mvnPath = if ((-not $DryRun.IsPresent) -and $runChecks.backendBuild) { Resolve-CommandPath @("mvn.cmd", "mvn") } else { $null }
$powershellPath = if ((-not $DryRun.IsPresent) -and ($runChecks.implementCommand -or $runChecks.smokeTest)) {
    Resolve-CommandPath @("powershell.exe", "pwsh.exe", "pwsh", "powershell")
} else {
    $null
}
$dockerPath = $null
if ((-not $DryRun.IsPresent) -and $runChecks.composeUp) {
    $dockerPath = Resolve-CommandPath @("docker.exe", "docker")
}

$frontendRoot = Join-Path $script:WorkspaceRoot "frontend"
$backendRoot = Join-Path $script:WorkspaceRoot "backend"
$smokeScriptPath = Join-Path $script:WorkspaceRoot ".smoke\run-smoke.ps1"
$mavenRepoRoot = Join-Path $script:WorkspaceRoot ".cache\m2\repository"

$snapshotBefore = Get-RepoSnapshot $script:WorkspaceRoot
$steps = New-Object System.Collections.Generic.List[object]
$pipelineFailed = $false
$healthPayload = $null
$initPayload = $null

try {
    if ($DryRun) {
        $steps.Add([ordered]@{
            name = "dry-run"
            status = "passed"
            startedAt = (Get-Date).ToString("o")
            endedAt = (Get-Date).ToString("o")
            durationSeconds = 0
            details = [ordered]@{
                message = "Dry run completed without executing commands."
            }
            error = $null
        })
    } else {
        $implementStep = Invoke-Step -Name "implement-command" -Enabled $runChecks.implementCommand -Action {
            $stdout = Join-Path $stepLogDirectory "implement-command.stdout.log"
            $stderr = Join-Path $stepLogDirectory "implement-command.stderr.log"
            Invoke-ExternalCommand -FilePath $powershellPath `
                -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $effectiveImplementCommand) `
                -WorkingDirectory $script:WorkspaceRoot `
                -StdOutPath $stdout `
                -StdErrPath $stderr
        }
        $steps.Add($implementStep)
        if ($implementStep.status -eq "failed") {
            throw $implementStep.error
        }

        $frontendStep = Invoke-Step -Name "frontend-build" -Enabled $runChecks.frontendBuild -Action {
            $stdout = Join-Path $stepLogDirectory "frontend-build.stdout.log"
            $stderr = Join-Path $stepLogDirectory "frontend-build.stderr.log"
            Invoke-ExternalCommand -FilePath $npmPath `
                -ArgumentList @("run", "build") `
                -WorkingDirectory $frontendRoot `
                -StdOutPath $stdout `
                -StdErrPath $stderr
        }
        $steps.Add($frontendStep)
        if ($frontendStep.status -eq "failed") {
            throw $frontendStep.error
        }

        $backendStep = Invoke-Step -Name "backend-build" -Enabled $runChecks.backendBuild -Action {
            $stdout = Join-Path $stepLogDirectory "backend-build.stdout.log"
            $stderr = Join-Path $stepLogDirectory "backend-build.stderr.log"
            New-Item -ItemType Directory -Path $mavenRepoRoot -Force | Out-Null
            Invoke-ExternalCommand -FilePath $mvnPath `
                -ArgumentList @("-Dmaven.repo.local=$mavenRepoRoot", "-q", "-DskipTests", "package") `
                -WorkingDirectory $backendRoot `
                -StdOutPath $stdout `
                -StdErrPath $stderr
        }
        $steps.Add($backendStep)
        if ($backendStep.status -eq "failed") {
            throw $backendStep.error
        }

        $composeStep = Invoke-Step -Name "compose-up" -Enabled $runChecks.composeUp -Action {
            $stdout = Join-Path $stepLogDirectory "compose-up.stdout.log"
            $stderr = Join-Path $stepLogDirectory "compose-up.stderr.log"
            $composeArguments = @("compose", "up", "-d", "--build")
            if ($taskConfig.composeServices -and $taskConfig.composeServices.Count -gt 0) {
                $composeArguments += @($taskConfig.composeServices)
            }

            $commandResult = Invoke-ExternalCommand -FilePath $dockerPath `
                -ArgumentList $composeArguments `
                -WorkingDirectory $script:WorkspaceRoot `
                -StdOutPath $stdout `
                -StdErrPath $stderr

            $healthPayload = Wait-ForHealthEndpoint -HealthUrl "$effectiveBaseUrl/health" -TimeoutSeconds $HealthTimeoutSeconds
            $healthPath = Join-Path $runDirectory "health.json"
            Set-Content -LiteralPath $healthPath -Value (ConvertTo-PrettyJson $healthPayload) -Encoding UTF8

            return [ordered]@{
                command = $commandResult.command
                health = $healthPayload
                healthReport = $healthPath
                stdout = $commandResult.stdout
                stderr = $commandResult.stderr
            }
        }
        $steps.Add($composeStep)
        if ($composeStep.status -eq "failed") {
            throw $composeStep.error
        }

        $initStep = Invoke-Step -Name "system-init" -Enabled $runChecks.systemInit -Action {
            $initPayload = Invoke-RestMethod -Method Post -Uri "$effectiveBaseUrl/system/init?scope=all" -TimeoutSec 60
            $initPath = Join-Path $runDirectory "init.json"
            Set-Content -LiteralPath $initPath -Value (ConvertTo-PrettyJson $initPayload) -Encoding UTF8
            return [ordered]@{
                response = $initPayload
                report = $initPath
            }
        }
        $steps.Add($initStep)
        if ($initStep.status -eq "failed") {
            throw $initStep.error
        }

        $smokeStep = Invoke-Step -Name "smoke-test" -Enabled $runChecks.smokeTest -Action {
            $stdout = Join-Path $stepLogDirectory "smoke-test.stdout.log"
            $stderr = Join-Path $stepLogDirectory "smoke-test.stderr.log"
            $smokeArguments = @(
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                $smokeScriptPath,
                "-BaseUrl",
                $effectiveBaseUrl
            )

            if ($effectiveRequireRealDiagnosisModel) {
                $smokeArguments += "-RequireRealDiagnosisModel"
            }

            Invoke-ExternalCommand -FilePath $powershellPath `
                -ArgumentList $smokeArguments `
                -WorkingDirectory $script:WorkspaceRoot `
                -StdOutPath $stdout `
                -StdErrPath $stderr
        }
        $steps.Add($smokeStep)
        if ($smokeStep.status -eq "failed") {
            throw $smokeStep.error
        }
    }
} catch {
    $pipelineFailed = $true
    Write-Warning $_.Exception.Message
}

$snapshotAfter = Get-RepoSnapshot $script:WorkspaceRoot
$changes = Compare-RepoSnapshot -Before $snapshotBefore -After $snapshotAfter
$allChangedPaths = @($changes.added + $changes.modified + $changes.deleted)
$scopeViolations = @(Get-ScopeViolations -ChangedPaths $allChangedPaths -AllowedPaths @($taskConfig.allowedPaths))
if ($scopeViolations.Count -gt 0) {
    $pipelineFailed = $true
}

$failedSteps = @($steps | Where-Object { $_.status -eq "failed" })
$overallStatus = if ($pipelineFailed) { "failed" } elseif ($failedSteps.Count -gt 0) { "failed" } else { "passed" }

$jsonReportPath = Join-Path $runDirectory "report.json"
$markdownReportPath = Join-Path $runDirectory "report.md"
$healthReportPath = if ($healthPayload) { Join-Path $runDirectory "health.json" } else { $null }
$initReportPath = if ($initPayload) { Join-Path $runDirectory "init.json" } else { $null }
$allowedPathList = [string[]]@($taskConfig.allowedPaths)
$stepResults = $steps.ToArray()
$addedPaths = [string[]]@($changes.added)
$modifiedPaths = [string[]]@($changes.modified)
$deletedPaths = [string[]]@($changes.deleted)
$scopeViolationList = [string[]]@($scopeViolations)

$taskReport = New-Object PSObject
$taskReport | Add-Member -NotePropertyName "path" -NotePropertyValue $resolvedTaskPath
$taskReport | Add-Member -NotePropertyName "title" -NotePropertyValue ([string]$taskConfig.title)
$taskReport | Add-Member -NotePropertyName "goal" -NotePropertyValue ([string]$taskConfig.goal)
$taskReport | Add-Member -NotePropertyName "allowedPaths" -NotePropertyValue $allowedPathList
$taskReport | Add-Member -NotePropertyName "implementCommand" -NotePropertyValue $effectiveImplementCommand

$configurationReport = New-Object PSObject
$configurationReport | Add-Member -NotePropertyName "baseUrl" -NotePropertyValue $effectiveBaseUrl
$configurationReport | Add-Member -NotePropertyName "requireRealDiagnosisModel" -NotePropertyValue $effectiveRequireRealDiagnosisModel
$configurationReport | Add-Member -NotePropertyName "checks" -NotePropertyValue $runChecks

$changesReport = New-Object PSObject
$changesReport | Add-Member -NotePropertyName "added" -NotePropertyValue $addedPaths
$changesReport | Add-Member -NotePropertyName "modified" -NotePropertyValue $modifiedPaths
$changesReport | Add-Member -NotePropertyName "deleted" -NotePropertyValue $deletedPaths
$changesReport | Add-Member -NotePropertyName "scopeViolations" -NotePropertyValue $scopeViolationList

$artifactsReport = New-Object PSObject
$artifactsReport | Add-Member -NotePropertyName "jsonReport" -NotePropertyValue $jsonReportPath
$artifactsReport | Add-Member -NotePropertyName "markdownReport" -NotePropertyValue $markdownReportPath
$artifactsReport | Add-Member -NotePropertyName "stepLogDirectory" -NotePropertyValue $stepLogDirectory
$artifactsReport | Add-Member -NotePropertyName "health" -NotePropertyValue $healthReportPath
$artifactsReport | Add-Member -NotePropertyName "init" -NotePropertyValue $initReportPath

$report = New-Object PSObject
$report | Add-Member -NotePropertyName "runId" -NotePropertyValue $runId
$report | Add-Member -NotePropertyName "overallStatus" -NotePropertyValue $overallStatus
$report | Add-Member -NotePropertyName "generatedAt" -NotePropertyValue ((Get-Date).ToString("o"))
$report | Add-Member -NotePropertyName "workspace" -NotePropertyValue $script:WorkspaceRoot
$report | Add-Member -NotePropertyName "task" -NotePropertyValue $taskReport
$report | Add-Member -NotePropertyName "configuration" -NotePropertyValue $configurationReport
$report | Add-Member -NotePropertyName "steps" -NotePropertyValue $stepResults
$report | Add-Member -NotePropertyName "changes" -NotePropertyValue $changesReport
$report | Add-Member -NotePropertyName "artifacts" -NotePropertyValue $artifactsReport

Set-Content -LiteralPath $jsonReportPath -Value (ConvertTo-PrettyJson $report) -Encoding UTF8
Write-MarkdownReport -Report $report -OutputPath $markdownReportPath
Copy-Item -LiteralPath $jsonReportPath -Destination (Join-Path $resolvedReportRoot "latest.json") -Force
Copy-Item -LiteralPath $markdownReportPath -Destination (Join-Path $resolvedReportRoot "latest.md") -Force

Write-Step "Report written to $markdownReportPath"

if ($overallStatus -eq "failed") {
    exit 1
}
