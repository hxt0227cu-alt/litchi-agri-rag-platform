param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [switch]$RequireRealDiagnosisModel
)

$ErrorActionPreference = "Stop"

$workspaceRoot = Split-Path -Parent $PSScriptRoot
$docPath = Join-Path $PSScriptRoot "knowledge.md"
$imagePath = Join-Path $PSScriptRoot "anthracnose-demo.png"
$chatRequestPath = Join-Path $PSScriptRoot "chat-request.json"

$failures = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]
$cleanupTarget = $null

function Add-Failure([string]$message) {
    $script:failures.Add($message)
}

function Add-Warning([string]$message) {
    $script:warnings.Add($message)
}

try {
    $health = Invoke-RestMethod -Method Get -Uri "$BaseUrl/health"
    $init = Invoke-RestMethod -Method Post -Uri "$BaseUrl/system/init?scope=all"
    $upload = (& curl.exe -s -X POST -F "file=@$docPath;type=text/markdown" "$BaseUrl/document") | ConvertFrom-Json
    $cleanupTarget = $upload.id
    $documents = Invoke-RestMethod -Method Get -Uri "$BaseUrl/document"
    $chat = (& curl.exe -s -X POST -H "Content-Type: application/json; charset=utf-8" --data-binary "@$chatRequestPath" "$BaseUrl/chat") | ConvertFrom-Json
    $kg = Invoke-RestMethod -Method Get -Uri "$BaseUrl/kg/visualize?keyword=%E7%82%AD%E7%96%BD%E7%97%85"
    $diagnosis = (& curl.exe -s -X POST -F "file=@$imagePath;type=image/png" "$BaseUrl/diagnosis") | ConvertFrom-Json

    $serviceMap = @{}
    if ($health.services) {
        $health.services.PSObject.Properties | ForEach-Object {
            $serviceMap[$_.Name] = $_.Value
        }
    }

    foreach ($serviceName in @("neo4j", "milvus", "ollama")) {
        if ($serviceMap[$serviceName] -ne "connected") {
            Add-Failure("$serviceName service is not connected: $($serviceMap[$serviceName])")
        }
    }

    $diagnosisStatus = [string]($serviceMap["diagnosis"])
    if ([string]::IsNullOrWhiteSpace($diagnosisStatus)) {
        Add-Failure("diagnosis service status is missing from /health")
    } elseif ($diagnosisStatus -eq "unavailable") {
        Add-Failure("diagnosis service is unavailable")
    } elseif ($diagnosisStatus -ne "connected") {
        if ($RequireRealDiagnosisModel) {
            Add-Failure("diagnosis service is not using a real model: $diagnosisStatus")
        } else {
            Add-Warning("diagnosis service is degraded: $diagnosisStatus")
        }
    }

    if (-not $init.graphInitialized) {
        Add-Failure("knowledge graph initialization did not complete")
    }
    if (-not $init.vectorInitialized) {
        Add-Failure("vector store initialization did not complete")
    }

    if (-not $upload.indexed) {
        Add-Failure("uploaded document was not indexed")
    }
    if (($upload.chunkCount -as [int]) -lt 1) {
        Add-Failure("uploaded document produced no chunks")
    }

    $documentCount = @($documents).Count
    if ($documentCount -lt 1) {
        Add-Failure("document list is empty after upload")
    }

    $chatSourceCount = @($chat.sources).Count
    $chatEntityCount = @($chat.knowledgeGraph.entities).Count
    if ($chatSourceCount -lt 1) {
        Add-Failure("chat returned no document sources")
    }
    if ($chatEntityCount -lt 1) {
        Add-Warning("chat returned no knowledge graph entities")
    }
    if ([string]::IsNullOrWhiteSpace([string]$chat.answer)) {
        Add-Failure("chat answer is empty")
    }

    $kgNodeCount = @($kg.nodes).Count
    $kgEdgeCount = @($kg.edges).Count
    if ($kgNodeCount -lt 1) {
        Add-Failure("knowledge graph returned no nodes")
    }
    if ($kgEdgeCount -lt 1) {
        Add-Failure("knowledge graph returned no edges")
    }

    if ([string]::IsNullOrWhiteSpace([string]$diagnosis.disease)) {
        Add-Failure("diagnosis returned no disease")
    }
    if ($null -eq $diagnosis.confidence) {
        Add-Failure("diagnosis returned no confidence")
    }
    if ($diagnosis.demoMode) {
        if ($RequireRealDiagnosisModel) {
            Add-Failure("diagnosis result is still running in demo mode")
        } else {
            Add-Warning("diagnosis result is in demo mode")
        }
    }

    $result = [ordered]@{
        baseUrl = $BaseUrl
        overallStatus = if ($failures.Count -gt 0) { "failed" } elseif ($warnings.Count -gt 0) { "warning" } else { "passed" }
        requireRealDiagnosisModel = [bool]$RequireRealDiagnosisModel
        health = @{
            status = $health.status
            services = $serviceMap
        }
        init = @{
            scope = $init.scope
            graphInitialized = $init.graphInitialized
            vectorInitialized = $init.vectorInitialized
            message = $init.message
        }
        document = @{
            uploadedId = $upload.id
            indexed = $upload.indexed
            chunkCount = $upload.chunkCount
            totalDocuments = $documentCount
        }
        chat = @{
            sourceCount = $chatSourceCount
            knowledgeEntityCount = $chatEntityCount
            answerPreview = if ([string]$chat.answer -and $chat.answer.Length -gt 120) { $chat.answer.Substring(0, 120) } else { $chat.answer }
        }
        knowledgeGraph = @{
            nodeCount = $kgNodeCount
            edgeCount = $kgEdgeCount
        }
        diagnosis = @{
            disease = $diagnosis.disease
            confidence = $diagnosis.confidence
            engine = $diagnosis.engine
            demoMode = $diagnosis.demoMode
        }
        failures = @($failures)
        warnings = @($warnings)
    }

    $result | ConvertTo-Json -Depth 8

    if ($failures.Count -gt 0) {
        exit 1
    }
} finally {
    if ($cleanupTarget) {
        try {
            Invoke-RestMethod -Method Delete -Uri "$BaseUrl/document/$cleanupTarget" | Out-Null
        } catch {
            Write-Warning "Failed to delete smoke document $cleanupTarget"
        }
    }
}
