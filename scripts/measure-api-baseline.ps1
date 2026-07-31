param(
    [string]$BaseUrl = "http://127.0.0.1:8080/api",
    [string]$Username = "technician",
    [string]$Password = "demo123",
    [int]$ChatWarmupIterations = 1,
    [int]$ChatIterations = 5,
    [int]$DiagnosisWarmupIterations = 1,
    [int]$DiagnosisIterations = 5,
    [string[]]$Questions = @(
        "How to control litchi anthracnose during the rainy season?",
        "How to distinguish downy blight from anthracnose?",
        "What should Guiwei litchi pay attention to during flowering and fruiting?",
        "How should fruit borer be monitored during peak periods?",
        "What is the management priority after continuous rainfall?"
    ),
    [string]$WarmupQuestion = "Summarize the key points of litchi disease management.",
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
    } finally {
        $stopwatch.Stop()
    }

    return [ordered]@{
        durationMs = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 2)
        response = $result
    }
}

function Invoke-TimedMultipartRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [hashtable]$Headers,
        [Parameter(Mandatory = $true)]
        [string]$FilePath
    )

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        Add-Type -AssemblyName System.Net.Http
        $handler = New-Object System.Net.Http.HttpClientHandler
        $client = New-Object System.Net.Http.HttpClient($handler)
        $content = New-Object System.Net.Http.MultipartFormDataContent

        if ($Headers) {
            foreach ($key in $Headers.Keys) {
                $client.DefaultRequestHeaders.Remove($key) | Out-Null
                [void]$client.DefaultRequestHeaders.TryAddWithoutValidation($key, [string]$Headers[$key])
            }
        }

        $fileBytes = [System.IO.File]::ReadAllBytes($FilePath)
        $fileContent = New-Object System.Net.Http.ByteArrayContent -ArgumentList (, $fileBytes)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("image/jpeg")
        $content.Add($fileContent, "file", [System.IO.Path]::GetFileName($FilePath))

        $response = $client.PostAsync($Uri, $content).GetAwaiter().GetResult()
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "Multipart request failed with status $([int]$response.StatusCode): $raw"
        }
        $result = $raw | ConvertFrom-Json
    } finally {
        $stopwatch.Stop()
    }

    return [ordered]@{
        durationMs = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 2)
        response = $result
    }
}

function Get-MetricSummary {
    param(
        [Parameter(Mandatory = $true)]
        [double[]]$Durations,
        [Parameter(Mandatory = $true)]
        [double]$ThresholdMs
    )

    $average = [Math]::Round((($Durations | Measure-Object -Average).Average), 2)
    $minimum = [Math]::Round((($Durations | Measure-Object -Minimum).Minimum), 2)
    $maximum = [Math]::Round((($Durations | Measure-Object -Maximum).Maximum), 2)

    return [ordered]@{
        count = $Durations.Count
        avgMs = $average
        minMs = $minimum
        maxMs = $maximum
        thresholdMs = $ThresholdMs
        passed = $average -le $ThresholdMs
    }
}

if (-not (Test-Path -LiteralPath $SampleImage)) {
    throw "Sample image not found: $SampleImage"
}

$health = Wait-ForHealth -Uri "$BaseUrl/health"

Write-Host "[1/3] Logging in and fetching token..."
$loginResult = Invoke-TimedJsonRequest -Uri "$BaseUrl/auth/login" -Method Post -Body @{
    username = $Username
    password = $Password
}
$token = $loginResult.response.token
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "Login did not return a token."
}

$headers = @{
    Authorization = "Bearer $token"
}

Write-Host "[2/3] Measuring chat API..."
$chatDurations = New-Object System.Collections.Generic.List[double]
$chatSamples = New-Object System.Collections.Generic.List[object]
for ($i = 1; $i -le $ChatWarmupIterations; $i++) {
    $warmupSessionId = "benchmark-chat-warmup-$runId"
    [void](Invoke-TimedJsonRequest -Uri "$BaseUrl/chats" -Method Post -Headers $headers -Body @{
        sessionId = $warmupSessionId
        question = $WarmupQuestion
        useKnowledgeGraph = $true
        useVectorSearch = $true
    })
}
for ($i = 1; $i -le $ChatIterations; $i++) {
    $sessionId = "benchmark-chat-$runId"
    $question = $Questions[($i - 1) % $Questions.Count]
    $result = Invoke-TimedJsonRequest -Uri "$BaseUrl/chats" -Method Post -Headers $headers -Body @{
        sessionId = $sessionId
        question = $question
        useKnowledgeGraph = $true
        useVectorSearch = $true
    }
    $chatDurations.Add([double]$result.durationMs)
    $chatSamples.Add([ordered]@{
        iteration = $i
        question = $question
        durationMs = $result.durationMs
        answerPreview = [string]($result.response.answer)
    })
}

Write-Host "[3/3] Measuring diagnosis API..."
$diagnosisDurations = New-Object System.Collections.Generic.List[double]
$diagnosisSamples = New-Object System.Collections.Generic.List[object]
for ($i = 1; $i -le $DiagnosisWarmupIterations; $i++) {
    [void](Invoke-TimedMultipartRequest -Uri "$BaseUrl/diagnoses" -Headers $headers -FilePath $SampleImage)
}
for ($i = 1; $i -le $DiagnosisIterations; $i++) {
    $result = Invoke-TimedMultipartRequest -Uri "$BaseUrl/diagnoses" -Headers $headers -FilePath $SampleImage
    $diagnosisDurations.Add([double]$result.durationMs)
    $diagnosisSamples.Add([ordered]@{
        iteration = $i
        durationMs = $result.durationMs
        disease = [string]($result.response.diseaseName)
        confidence = $result.response.confidence
        engine = [string]($result.response.engine)
    })
}

$report = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    baseUrl = $BaseUrl
    username = $Username
    sampleImage = "frontend/public/demo/$([System.IO.Path]::GetFileName($SampleImage))"
    health = $health
    login = [ordered]@{
        durationMs = $loginResult.durationMs
    }
    chat = [ordered]@{
        warmupQuestion = $WarmupQuestion
        warmupCount = $ChatWarmupIterations
        summary = Get-MetricSummary -Durations $chatDurations.ToArray() -ThresholdMs 5000
        samples = $chatSamples.ToArray()
    }
    diagnosis = [ordered]@{
        warmupCount = $DiagnosisWarmupIterations
        summary = Get-MetricSummary -Durations $diagnosisDurations.ToArray() -ThresholdMs 3000
        samples = $diagnosisSamples.ToArray()
    }
}

$jsonPath = Join-Path $reportDir "baseline-report.json"
$mdPath = Join-Path $reportDir "baseline-report.md"
$report | ConvertTo-Json -Depth 10 | Set-Content -Path $jsonPath -Encoding UTF8

$markdown = @(
    "# API Baseline Report"
    ""
    "- Generated at: $($report.generatedAt)"
    "- Base URL: $BaseUrl"
    "- Username: $Username"
    "- Sample image: $($report.sampleImage)"
    "- Service status: $($report.health.status)"
    ""
    "## Login"
    ""
    "- Duration: $($report.login.durationMs) ms"
    ""
    "## Chat"
    ""
    "- Warmup question: $($report.chat.warmupQuestion)"
    "- Warmup count: $($report.chat.warmupCount)"
    "- Count: $($report.chat.summary.count)"
    "- Avg: $($report.chat.summary.avgMs) ms"
    "- Min: $($report.chat.summary.minMs) ms"
    "- Max: $($report.chat.summary.maxMs) ms"
    "- Threshold: $($report.chat.summary.thresholdMs) ms"
    "- Passed: $($report.chat.summary.passed)"
    ""
    "## Diagnosis"
    ""
    "- Warmup count: $($report.diagnosis.warmupCount)"
    "- Count: $($report.diagnosis.summary.count)"
    "- Avg: $($report.diagnosis.summary.avgMs) ms"
    "- Min: $($report.diagnosis.summary.minMs) ms"
    "- Max: $($report.diagnosis.summary.maxMs) ms"
    "- Threshold: $($report.diagnosis.summary.thresholdMs) ms"
    "- Passed: $($report.diagnosis.summary.passed)"
    ""
    "## Artifacts"
    ""
    "- JSON: baseline-report.json"
)
$markdown -join [Environment]::NewLine | Set-Content -Path $mdPath -Encoding UTF8

Write-Host ""
Write-Host "Done."
Write-Host "JSON report: $jsonPath"
Write-Host "Markdown report: $mdPath"
