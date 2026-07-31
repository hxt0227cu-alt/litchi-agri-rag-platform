param(
    [string]$BaseUrl = "http://127.0.0.1:8080/api",
    [string]$Username = "technician",
    [string]$Password = "demo123",
    [int]$DurationMinutes = 30,
    [int]$IntervalSeconds = 15,
    [double]$AvailabilityTargetPct = 99,
    [string[]]$Questions = @(
        "How to control litchi anthracnose during the rainy season?",
        "What is the management priority after continuous rainfall?",
        "How should fruit borer be monitored during peak periods?",
        "How can technicians distinguish major litchi leaf diseases?"
    ),
    [string]$SampleImage = "",
    [string]$OutputRoot = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($SampleImage)) {
    $SampleImage = Join-Path $workspaceRoot "frontend\public\demo\anthracnose-demo.jpg"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $workspaceRoot "reports\validation"
}

if (-not (Test-Path -LiteralPath $SampleImage)) {
    throw "Sample image not found: $SampleImage"
}

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$reportDir = Join-Path $OutputRoot $runId
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

function Wait-ForHealth {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [int]$RetryCount = 20,
        [int]$DelaySeconds = 2
    )

    for ($i = 0; $i -lt $RetryCount; $i++) {
        try {
            return Invoke-RestMethod -Uri $Uri -Method Get -TimeoutSec 5
        } catch {
            Start-Sleep -Seconds $DelaySeconds
        }
    }

    throw "Health check did not become ready: $Uri"
}

function Invoke-TimedGetRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri
    )

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $result = Invoke-RestMethod -Uri $Uri -Method Get -TimeoutSec 10
        return [ordered]@{
            success = $true
            durationMs = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 2)
            response = $result
            error = $null
        }
    } catch {
        return [ordered]@{
            success = $false
            durationMs = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 2)
            response = $null
            error = $_.Exception.Message
        }
    } finally {
        $stopwatch.Stop()
    }
}

function Invoke-TimedJsonRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [Parameter(Mandatory = $true)]
        [string]$Method,
        [hashtable]$Headers,
        $Body
    )

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $result = Invoke-RestMethod -Uri $Uri -Method $Method -Headers $Headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 10)
        return [ordered]@{
            success = $true
            durationMs = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 2)
            response = $result
            error = $null
        }
    } catch {
        return [ordered]@{
            success = $false
            durationMs = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 2)
            response = $null
            error = $_.Exception.Message
        }
    } finally {
        $stopwatch.Stop()
    }
}

function Invoke-TimedMultipartRequest {
    param(
        [Parameter(Mandatory = $true)]
        [System.Net.Http.HttpClient]$Client,
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [hashtable]$Headers,
        [Parameter(Mandatory = $true)]
        [string]$FilePath
    )

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $content = New-Object System.Net.Http.MultipartFormDataContent

        if ($Headers) {
            foreach ($key in $Headers.Keys) {
                $Client.DefaultRequestHeaders.Remove($key) | Out-Null
                [void]$Client.DefaultRequestHeaders.TryAddWithoutValidation($key, [string]$Headers[$key])
            }
        }

        $fileBytes = [System.IO.File]::ReadAllBytes($FilePath)
        $fileContent = New-Object System.Net.Http.ByteArrayContent -ArgumentList (, $fileBytes)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("image/jpeg")
        $content.Add($fileContent, "file", [System.IO.Path]::GetFileName($FilePath))

        $response = $Client.PostAsync($Uri, $content).GetAwaiter().GetResult()
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "Multipart request failed with status $([int]$response.StatusCode): $raw"
        }

        return [ordered]@{
            success = $true
            durationMs = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 2)
            response = $raw | ConvertFrom-Json
            error = $null
        }
    } catch {
        return [ordered]@{
            success = $false
            durationMs = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 2)
            response = $null
            error = $_.Exception.Message
        }
    } finally {
        $stopwatch.Stop()
    }
}

function Get-DurationSummary {
    param(
        [double[]]$Durations
    )

    if (-not $Durations -or $Durations.Count -eq 0) {
        return [ordered]@{
            avgMs = 0
            minMs = 0
            maxMs = 0
        }
    }

    return [ordered]@{
        avgMs = [Math]::Round((($Durations | Measure-Object -Average).Average), 2)
        minMs = [Math]::Round((($Durations | Measure-Object -Minimum).Minimum), 2)
        maxMs = [Math]::Round((($Durations | Measure-Object -Maximum).Maximum), 2)
    }
}

function Get-SuccessRate {
    param(
        [int]$SuccessCount,
        [int]$TotalCount
    )

    if ($TotalCount -le 0) {
        return 0
    }

    return [Math]::Round(($SuccessCount / $TotalCount) * 100, 2)
}

$healthSnapshot = Wait-ForHealth -Uri "$BaseUrl/health"

Write-Host "[1/4] Logging in and fetching token..."
$loginResult = Invoke-TimedJsonRequest -Uri "$BaseUrl/auth/login" -Method Post -Body @{
    username = $Username
    password = $Password
}
if (-not $loginResult.success) {
    throw "Login failed: $($loginResult.error)"
}

$token = $loginResult.response.token
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "Login did not return a token."
}

$headers = @{
    Authorization = "Bearer $token"
}
$loginTime = Get-Date

$endAt = (Get-Date).AddMinutes($DurationMinutes)
$cycles = New-Object System.Collections.Generic.List[object]
$cycleNumber = 0

Add-Type -AssemblyName System.Net.Http
$multipartHandler = New-Object System.Net.Http.HttpClientHandler
$multipartClient = New-Object System.Net.Http.HttpClient($multipartHandler)
$multipartClient.Timeout = [TimeSpan]::FromSeconds(20)

try {
    Write-Host "[2/4] Starting stability loop for $DurationMinutes minute(s)..."
    while ((Get-Date) -lt $endAt) {
        $cycleNumber++
        $cycleStartedAt = Get-Date
        $question = $Questions[($cycleNumber - 1) % $Questions.Count]
        $sessionId = "stability-$runId-$cycleNumber"

        # 检测 Token 是否即将过期
        if (((Get-Date) - $loginTime).TotalMinutes -gt 25) {
            Write-Host "  Token 即将过期，重新登录..."
            $loginResult = Invoke-TimedJsonRequest -Uri "$BaseUrl/auth/login" -Method Post -Body @{
                username = $Username
                password = $Password
            }
            if (-not $loginResult.success) {
                throw "Re-login failed: $($loginResult.error)"
            }
            $token = $loginResult.response.token
            $headers = @{ Authorization = "Bearer $token" }
            $loginTime = Get-Date
        }

        $healthResult = Invoke-TimedGetRequest -Uri "$BaseUrl/health"
        $chatResult = Invoke-TimedJsonRequest -Uri "$BaseUrl/chat" -Method Post -Headers $headers -Body @{
            sessionId = $sessionId
            question = $question
            useKnowledgeGraph = $true
            useVectorSearch = $true
        }
        $diagnosisResult = Invoke-TimedMultipartRequest -Client $multipartClient -Uri "$BaseUrl/diagnosis" -Headers $headers -FilePath $SampleImage

        $cyclePassed = $healthResult.success -and $chatResult.success -and $diagnosisResult.success
        $cycles.Add([ordered]@{
            cycle = $cycleNumber
            startedAt = $cycleStartedAt.ToString("o")
            health = [ordered]@{
                success = $healthResult.success
                durationMs = $healthResult.durationMs
                status = if ($healthResult.response) { [string]$healthResult.response.status } else { "" }
                error = $healthResult.error
            }
            chat = [ordered]@{
                success = $chatResult.success
                durationMs = $chatResult.durationMs
                answerPreview = if ($chatResult.response) { [string]$chatResult.response.answer } else { "" }
                error = $chatResult.error
            }
            diagnosis = [ordered]@{
                success = $diagnosisResult.success
                durationMs = $diagnosisResult.durationMs
                engine = if ($diagnosisResult.response) { [string]$diagnosisResult.response.engine } else { "" }
                disease = if ($diagnosisResult.response) { [string]$diagnosisResult.response.disease } else { "" }
                error = $diagnosisResult.error
            }
            cyclePassed = $cyclePassed
        }) | Out-Null

        Write-Host ("  Cycle {0}: health={1}, chat={2}, diagnosis={3}" -f $cycleNumber, $healthResult.success, $chatResult.success, $diagnosisResult.success)

        $sleepFor = [Math]::Max(0, $IntervalSeconds - [int]((Get-Date) - $cycleStartedAt).TotalSeconds)
        if ($sleepFor -gt 0 -and (Get-Date) -lt $endAt) {
            Start-Sleep -Seconds $sleepFor
        }
    }
} finally {
    $multipartClient.Dispose()
    $multipartHandler.Dispose()
}

Write-Host "[3/4] Building report..."
$healthSuccessCount = @($cycles | Where-Object { $_.health.success }).Count
$chatSuccessCount = @($cycles | Where-Object { $_.chat.success }).Count
$diagnosisSuccessCount = @($cycles | Where-Object { $_.diagnosis.success }).Count
$cycleSuccessCount = @($cycles | Where-Object { $_.cyclePassed }).Count

$healthDurations = @($cycles | Where-Object { $_.health.success } | ForEach-Object { [double]$_.health.durationMs })
$chatDurations = @($cycles | Where-Object { $_.chat.success } | ForEach-Object { [double]$_.chat.durationMs })
$diagnosisDurations = @($cycles | Where-Object { $_.diagnosis.success } | ForEach-Object { [double]$_.diagnosis.durationMs })

$availabilityPct = Get-SuccessRate -SuccessCount $cycleSuccessCount -TotalCount $cycles.Count
$report = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    baseUrl = $BaseUrl
    username = $Username
    durationMinutes = $DurationMinutes
    intervalSeconds = $IntervalSeconds
    availabilityTargetPct = $AvailabilityTargetPct
    healthSnapshot = $healthSnapshot
    login = [ordered]@{
        durationMs = $loginResult.durationMs
    }
    summary = [ordered]@{
        cycleCount = $cycles.Count
        cycleSuccessCount = $cycleSuccessCount
        cycleAvailabilityPct = $availabilityPct
        healthSuccessRatePct = Get-SuccessRate -SuccessCount $healthSuccessCount -TotalCount $cycles.Count
        chatSuccessRatePct = Get-SuccessRate -SuccessCount $chatSuccessCount -TotalCount $cycles.Count
        diagnosisSuccessRatePct = Get-SuccessRate -SuccessCount $diagnosisSuccessCount -TotalCount $cycles.Count
        passed = $availabilityPct -ge $AvailabilityTargetPct
        healthLatency = Get-DurationSummary -Durations $healthDurations
        chatLatency = Get-DurationSummary -Durations $chatDurations
        diagnosisLatency = Get-DurationSummary -Durations $diagnosisDurations
    }
    failures = [ordered]@{
        firstFive = @($cycles | Where-Object { -not $_.cyclePassed } | Select-Object -First 5)
    }
    samples = $cycles
}

$jsonPath = Join-Path $reportDir "stability-report.json"
$mdPath = Join-Path $reportDir "stability-report.md"
$report | ConvertTo-Json -Depth 10 | Set-Content -Path $jsonPath -Encoding UTF8

$markdown = @(
    "# Service Stability Report"
    ""
    "- Generated at: $($report.generatedAt)"
    "- Base URL: $BaseUrl"
    "- Username: $Username"
    "- Duration: $DurationMinutes minute(s)"
    "- Interval: $IntervalSeconds second(s)"
    "- Availability target: $AvailabilityTargetPct%"
    ""
    "## Summary"
    ""
    "- Cycle count: $($report.summary.cycleCount)"
    "- Successful cycles: $($report.summary.cycleSuccessCount)"
    "- Cycle availability: $($report.summary.cycleAvailabilityPct)%"
    "- Health success rate: $($report.summary.healthSuccessRatePct)%"
    "- Chat success rate: $($report.summary.chatSuccessRatePct)%"
    "- Diagnosis success rate: $($report.summary.diagnosisSuccessRatePct)%"
    "- Passed: $($report.summary.passed)"
    ""
    "## Latency"
    ""
    "- Health avg/min/max: $($report.summary.healthLatency.avgMs) / $($report.summary.healthLatency.minMs) / $($report.summary.healthLatency.maxMs) ms"
    "- Chat avg/min/max: $($report.summary.chatLatency.avgMs) / $($report.summary.chatLatency.minMs) / $($report.summary.chatLatency.maxMs) ms"
    "- Diagnosis avg/min/max: $($report.summary.diagnosisLatency.avgMs) / $($report.summary.diagnosisLatency.minMs) / $($report.summary.diagnosisLatency.maxMs) ms"
    ""
    "## Failure Samples"
    ""
)

if ($report.failures.firstFive.Count -eq 0) {
    $markdown += "- None"
} else {
    foreach ($failure in $report.failures.firstFive) {
        $markdown += "- Cycle $($failure.cycle): health=$($failure.health.error); chat=$($failure.chat.error); diagnosis=$($failure.diagnosis.error)"
    }
}

$markdown += ""
$markdown += "## Artifacts"
$markdown += ""
$markdown += "- JSON: $jsonPath"
$markdown -join [Environment]::NewLine | Set-Content -Path $mdPath -Encoding UTF8

Write-Host "[4/4] Done."
Write-Host "JSON report: $jsonPath"
Write-Host "Markdown report: $mdPath"
