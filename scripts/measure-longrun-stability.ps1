param(
    [string]$BaseUrl = "http://127.0.0.1:8088/api",
    [string]$Username = "technician",
    [string]$Password = "demo123",
    [int]$DurationMinutes = 360,
    [int]$IntervalSeconds = 60,
    [string]$OutputRoot = "C:\Users\hxt02\Desktop\hxt-bishe\reports\validation"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$reportDir = Join-Path $OutputRoot "longrun-$runId"
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

function Get-DurationSummary {
    param([double[]]$Durations)
    if (-not $Durations -or $Durations.Count -eq 0) {
        return [ordered]@{ avgMs = 0; minMs = 0; maxMs = 0 }
    }
    $sorted = $Durations | Sort-Object
    $p95 = $sorted[[Math]::Min($sorted.Count - 1, [int]([Math]::Ceiling($sorted.Count * 0.95) - 1))]
    return [ordered]@{
        avgMs = [Math]::Round(($Durations | Measure-Object -Average).Average, 2)
        minMs = [Math]::Round(($Durations | Measure-Object -Minimum).Minimum, 2)
        maxMs = [Math]::Round(($Durations | Measure-Object -Maximum).Maximum, 2)
        p95Ms = [Math]::Round([double]$p95, 2)
    }
}

function Invoke-TimedJson {
    param([string]$Uri, [string]$Method, [hashtable]$Headers, $Body)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $r = Invoke-RestMethod -Uri $Uri -Method $Method -Headers $Headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 10) -TimeoutSec 20
        return [ordered]@{ success = $true; durationMs = [Math]::Round($sw.Elapsed.TotalMilliseconds, 2); response = $r }
    } catch {
        return [ordered]@{ success = $false; durationMs = [Math]::Round($sw.Elapsed.TotalMilliseconds, 2); error = $_.Exception.Message }
    } finally { $sw.Stop() }
}

# login
$login = Invoke-TimedJson -Uri "$BaseUrl/auth/login" -Method Post -Headers @{} -Body @{ username = $Username; password = $Password }
if (-not $login.success) { throw "login failed: $($login.error)" }
$token = $login.response.token
$headers = @{ Authorization = "Bearer $token" }

$endAt = (Get-Date).AddMinutes($DurationMinutes)
$cycles = New-Object System.Collections.Generic.List[object]
$cycleNumber = 0
$healthD = New-Object System.Collections.Generic.List[double]
$chatD = New-Object System.Collections.Generic.List[double]

while ((Get-Date) -lt $endAt) {
    $cycleNumber++
    $h = Invoke-TimedJson -Uri "$BaseUrl/health" -Method Get -Headers $headers
    if ($h.success) { $healthD.Add([double]$h.durationMs) }
    $chatBody = @{
        sessionId = "longrun-$runId-$cycleNumber"
        question = "荔枝炭疽病在连续降雨期间如何防治？"
        useKnowledgeGraph = $true
        useVectorSearch = $true
    }
    $c = Invoke-TimedJson -Uri "$BaseUrl/chats" -Method Post -Headers $headers -Body $chatBody
    if ($c.success) { $chatD.Add([double]$c.durationMs) }
    $cyclePassed = $h.success -and $c.success
    $cycles.Add([ordered]@{
        cycle = $cycleNumber
        timestamp = (Get-Date).ToString("o")
        health = $h
        chat = $c
        passed = $cyclePassed
    }) | Out-Null
    Write-Host ("cycle {0}: health={1} chat={2}" -f $cycleNumber, $h.success, $c.success)
    Start-Sleep -Seconds $IntervalSeconds
}

$total = $cycles.Count
$passed = @($cycles | Where-Object { $_.passed }).Count
$avail = if ($total -gt 0) { [Math]::Round($passed / $total * 100, 4) } else { 0 }
$report = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    baseUrl = $BaseUrl
    durationMinutes = $DurationMinutes
    intervalSeconds = $IntervalSeconds
    summary = [ordered]@{
        cycleCount = $total
        passedCycles = $passed
        availabilityPct = $avail
        health = Get-DurationSummary -Durations ($healthD.ToArray())
        chat = Get-DurationSummary -Durations ($chatD.ToArray())
        passed = $avail -ge 99.0
    }
    failures = @($cycles | Where-Object { -not $_.passed } | Select-Object -First 5)
    samples = $cycles
}
$json = Join-Path $reportDir "longrun-stability.json"
$report | ConvertTo-Json -Depth 10 | Set-Content -Path $json -Encoding UTF8
Write-Host "JSON report: $json"
Write-Host ("availability={0}% cycles={1}/{2}" -f $avail, $passed, $total)
