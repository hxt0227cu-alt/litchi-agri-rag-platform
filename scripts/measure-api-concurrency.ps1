param(
    [string]$BaseUrl = "http://127.0.0.1:8080/api",
    [string]$Username = "technician",
    [string]$Password = "demo123",
    [int]$WarmupIterations = 2,
    [int]$Concurrency = 50,
    [int]$TotalRequests = 50,
    [int]$RequestTimeoutMs = 12000,
    [string[]]$Questions = @(
        "How to control litchi anthracnose during continuous rainfall?",
        "What should be checked first when litchi leaves develop dark lesions?",
        "How should growers manage fruit borer risk during peak periods?",
        "What is the priority after rain when disease pressure rises?",
        "How can technicians quickly distinguish major litchi leaf diseases?"
    ),
    [string]$OutputRoot = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
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

function Get-Percentile {
    param(
        [Parameter(Mandatory = $true)]
        [double[]]$Values,
        [Parameter(Mandatory = $true)]
        [double]$Percentile
    )

    if ($Values.Count -eq 0) {
        return 0
    }

    $sorted = $Values | Sort-Object
    $rank = [Math]::Ceiling(($Percentile / 100.0) * $sorted.Count)
    $index = [Math]::Max([Math]::Min($rank - 1, $sorted.Count - 1), 0)
    return [Math]::Round([double]$sorted[$index], 2)
}

function Get-DurationSummary {
    param(
        [Parameter(Mandatory = $true)]
        [double[]]$Durations
    )

    if ($Durations.Count -eq 0) {
        return [ordered]@{
            avgMs = 0
            minMs = 0
            p50Ms = 0
            p90Ms = 0
            p95Ms = 0
            maxMs = 0
        }
    }

    return [ordered]@{
        avgMs = [Math]::Round((($Durations | Measure-Object -Average).Average), 2)
        minMs = [Math]::Round((($Durations | Measure-Object -Minimum).Minimum), 2)
        p50Ms = Get-Percentile -Values $Durations -Percentile 50
        p90Ms = Get-Percentile -Values $Durations -Percentile 90
        p95Ms = Get-Percentile -Values $Durations -Percentile 95
        maxMs = [Math]::Round((($Durations | Measure-Object -Maximum).Maximum), 2)
    }
}

if (-not ("ChatLoadRunner" -as [type])) {
    Add-Type -Language CSharp -ReferencedAssemblies @("System.Net.Http.dll") -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

public class ChatLoadSample
{
    public int RequestIndex { get; set; }
    public string SessionId { get; set; }
    public string Question { get; set; }
    public double DurationMs { get; set; }
    public int StatusCode { get; set; }
    public bool Success { get; set; }
    public string Error { get; set; }
}

public static class ChatLoadRunner
{
    public static ChatLoadSample[] Run(string baseUrl, string token, string[] questions, int concurrency, int totalRequests, int timeoutMs)
    {
        var handler = new HttpClientHandler();
        var client = new HttpClient(handler);
        client.BaseAddress = new Uri(baseUrl.TrimEnd('/') + "/");
        client.Timeout = TimeSpan.FromMilliseconds(timeoutMs);
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        client.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));

        var gate = new SemaphoreSlim(concurrency, concurrency);
        var tasks = new List<Task<ChatLoadSample>>();
        for (var index = 0; index < totalRequests; index++)
        {
            var requestIndex = index + 1;
            var question = questions[index % questions.Length];
            tasks.Add(RunSingleAsync(client, gate, question, requestIndex));
        }

        Task.WaitAll(tasks.ToArray(), TimeSpan.FromMinutes(5));
        var results = tasks.Select(task => task.Result).OrderBy(sample => sample.RequestIndex).ToArray();

        gate.Dispose();
        client.Dispose();
        handler.Dispose();
        return results;
    }

    private static async Task<ChatLoadSample> RunSingleAsync(HttpClient client, SemaphoreSlim gate, string baseQuestion, int requestIndex)
    {
        await gate.WaitAsync().ConfigureAwait(false);

        var stopwatch = Stopwatch.StartNew();
        var sessionId = "load-chat-" + DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString() + "-" + requestIndex.ToString();
        var question = baseQuestion + " (load test " + requestIndex.ToString() + ")";

        try
        {
            var json = "{\"sessionId\":\"" + EscapeJson(sessionId)
                + "\",\"question\":\"" + EscapeJson(question)
                + "\",\"useKnowledgeGraph\":true,\"useVectorSearch\":true}";
            var content = new StringContent(json, Encoding.UTF8, "application/json");
            var response = await client.PostAsync("chats", content).ConfigureAwait(false);
            var body = await response.Content.ReadAsStringAsync().ConfigureAwait(false);

            content.Dispose();
            response.Dispose();
            stopwatch.Stop();

            return new ChatLoadSample
            {
                RequestIndex = requestIndex,
                SessionId = sessionId,
                Question = question,
                DurationMs = Math.Round(stopwatch.Elapsed.TotalMilliseconds, 2),
                StatusCode = (int)response.StatusCode,
                Success = response.IsSuccessStatusCode,
                Error = response.IsSuccessStatusCode ? null : Truncate(body)
            };
        }
        catch (Exception ex)
        {
            stopwatch.Stop();
            return new ChatLoadSample
            {
                RequestIndex = requestIndex,
                SessionId = sessionId,
                Question = question,
                DurationMs = Math.Round(stopwatch.Elapsed.TotalMilliseconds, 2),
                StatusCode = 0,
                Success = false,
                Error = Truncate(ex.Message)
            };
        }
        finally
        {
            gate.Release();
        }
    }

    private static string Truncate(string value)
    {
        if (string.IsNullOrEmpty(value))
        {
            return value;
        }

        return value.Length <= 300 ? value : value.Substring(0, 300);
    }

    private static string EscapeJson(string value)
    {
        return (value ?? string.Empty)
            .Replace("\\", "\\\\")
            .Replace("\"", "\\\"")
            .Replace("\r", "\\r")
            .Replace("\n", "\\n");
    }
}
"@
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

Write-Host "[2/3] Warming up chat API..."
for ($i = 1; $i -le $WarmupIterations; $i++) {
    [void](Invoke-TimedJsonRequest -Uri "$BaseUrl/chats" -Method Post -Headers $headers -Body @{
        sessionId = "concurrency-warmup-$runId-$i"
        question = "Summarize the key points of litchi disease management. (warmup $i)"
        useKnowledgeGraph = $true
        useVectorSearch = $true
    })
}

Write-Host "[3/3] Running concurrent chat load..."
$overallStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$samples = [ChatLoadRunner]::Run($BaseUrl, $token, $Questions, $Concurrency, $TotalRequests, $RequestTimeoutMs)
$overallStopwatch.Stop()

$durations = @($samples | ForEach-Object { [double]$_.DurationMs })
$successCount = @($samples | Where-Object { $_.Success }).Count
$failureSamples = @($samples | Where-Object { -not $_.Success })
$summary = Get-DurationSummary -Durations $durations
$throughput = if ($overallStopwatch.Elapsed.TotalSeconds -gt 0) {
    [Math]::Round($TotalRequests / $overallStopwatch.Elapsed.TotalSeconds, 2)
} else {
    0
}

$report = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    baseUrl = $BaseUrl
    username = $Username
    health = $health
    login = [ordered]@{
        durationMs = $loginResult.durationMs
    }
    chatLoad = [ordered]@{
        warmupCount = $WarmupIterations
        concurrency = $Concurrency
        totalRequests = $TotalRequests
        requestTimeoutMs = $RequestTimeoutMs
        totalDurationMs = [Math]::Round($overallStopwatch.Elapsed.TotalMilliseconds, 2)
        throughputRps = $throughput
        successCount = $successCount
        failureCount = $failureSamples.Count
        successRate = [Math]::Round(($successCount / [Math]::Max($TotalRequests, 1)) * 100, 2)
        passed = ($Concurrency -ge 50) -and ($successCount -eq $TotalRequests)
        summary = $summary
        failureSamples = @($failureSamples | Select-Object -First 5)
        samples = $samples
    }
}

$jsonPath = Join-Path $reportDir "concurrency-report.json"
$mdPath = Join-Path $reportDir "concurrency-report.md"
$report | ConvertTo-Json -Depth 10 | Set-Content -Path $jsonPath -Encoding UTF8

$markdown = @(
    "# API Concurrency Report"
    ""
    "- Generated at: $($report.generatedAt)"
    "- Base URL: $BaseUrl"
    "- Username: $Username"
    ""
    "## Chat Load"
    ""
    "- Warmup count: $($report.chatLoad.warmupCount)"
    "- Concurrency: $($report.chatLoad.concurrency)"
    "- Total requests: $($report.chatLoad.totalRequests)"
    "- Request timeout: $($report.chatLoad.requestTimeoutMs) ms"
    "- Total duration: $($report.chatLoad.totalDurationMs) ms"
    "- Throughput: $($report.chatLoad.throughputRps) req/s"
    "- Success count: $($report.chatLoad.successCount)"
    "- Failure count: $($report.chatLoad.failureCount)"
    "- Success rate: $($report.chatLoad.successRate)%"
    "- Avg: $($report.chatLoad.summary.avgMs) ms"
    "- Min: $($report.chatLoad.summary.minMs) ms"
    "- P50: $($report.chatLoad.summary.p50Ms) ms"
    "- P90: $($report.chatLoad.summary.p90Ms) ms"
    "- P95: $($report.chatLoad.summary.p95Ms) ms"
    "- Max: $($report.chatLoad.summary.maxMs) ms"
    "- Passed: $($report.chatLoad.passed)"
    ""
    "## Failure Samples"
    ""
)

if ($report.chatLoad.failureSamples.Count -eq 0) {
    $markdown += "- None"
} else {
    foreach ($sample in $report.chatLoad.failureSamples) {
        $markdown += "- Request #$($sample.RequestIndex): $($sample.Error)"
    }
}

$markdown += ""
$markdown += "## Artifacts"
$markdown += ""
$markdown += "- JSON: concurrency-report.json"
$markdown -join [Environment]::NewLine | Set-Content -Path $mdPath -Encoding UTF8

Write-Host ""
Write-Host "Done."
Write-Host "JSON report: $jsonPath"
Write-Host "Markdown report: $mdPath"
