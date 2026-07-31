param(
    [string]$BaseUrl = "http://127.0.0.1:8080/api",
    [string]$FarmerUsername = "farmer",
    [string]$FarmerPassword = "demo123",
    [string]$ShopUsername = "shopkeeper",
    [string]$ShopPassword = "demo123",
    [string]$TechnicianUsername = "technician",
    [string]$TechnicianPassword = "demo123",
    [string]$DiseaseTag = "",
    [string]$StageTag = "",
    [string]$Question = "What should be done first when fruit lesions keep expanding?",
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
    $OutputRoot = Join-Path $workspaceRoot "reports\rehearsal"
}
if ([string]::IsNullOrWhiteSpace($DiseaseTag)) {
    $DiseaseTag = "炭疽病"
}
if ([string]::IsNullOrWhiteSpace($StageTag)) {
    $StageTag = "雨季高湿"
}

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $OutputRoot "main-flow-$runId.json"

function Wait-ForHealth {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [int]$RetryCount = 30,
        [int]$DelaySeconds = 2
    )

    for ($i = 0; $i -lt $RetryCount; $i++) {
        try {
            return Invoke-RestMethod -Uri $Uri -Method Get -TimeoutSec 8
        } catch {
            Start-Sleep -Seconds $DelaySeconds
        }
    }

    throw "Health check did not become ready: $Uri"
}

function Invoke-JsonRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [Parameter(Mandatory = $true)]
        [string]$Method,
        [hashtable]$Headers,
        $Body
    )

    $invokeArgs = @{
        Uri         = $Uri
        Method      = $Method
        TimeoutSec  = 20
        ErrorAction = 'Stop'
    }

    if ($Headers) {
        $invokeArgs.Headers = $Headers
    }
    if ($null -ne $Body) {
        $invokeArgs.ContentType = 'application/json'
        $invokeArgs.Body = $Body | ConvertTo-Json -Depth 10
    }

    try {
        return Invoke-RestMethod @invokeArgs
    } catch {
        $responseBody = ""
        if ($_.Exception.Response) {
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($stream) {
                    using ($reader = New-Object System.IO.StreamReader($stream)) {
                        $responseBody = $reader.ReadToEnd()
                    }
                }
            } catch {
                $responseBody = ""
            }
        }
        throw "Request failed: $Method $Uri`n$responseBody"
    }
}

function Invoke-MultipartRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [hashtable]$Headers,
        [Parameter(Mandatory = $true)]
        [string]$FilePath
    )

    Add-Type -AssemblyName System.Net.Http
    $handler = New-Object System.Net.Http.HttpClientHandler
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(20)

    try {
        if ($Headers) {
            foreach ($key in $Headers.Keys) {
                $client.DefaultRequestHeaders.Remove($key) | Out-Null
                [void]$client.DefaultRequestHeaders.TryAddWithoutValidation($key, [string]$Headers[$key])
            }
        }

        $content = New-Object System.Net.Http.MultipartFormDataContent
        $fileBytes = [System.IO.File]::ReadAllBytes($FilePath)
        $fileContent = New-Object System.Net.Http.ByteArrayContent -ArgumentList (, $fileBytes)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("image/jpeg")
        $content.Add($fileContent, "file", [System.IO.Path]::GetFileName($FilePath))

        $response = $client.PostAsync($Uri, $content).GetAwaiter().GetResult()
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "Multipart request failed with status $([int]$response.StatusCode): $raw"
        }

        return $raw | ConvertFrom-Json
    } finally {
        $client.Dispose()
        $handler.Dispose()
    }
}

function Get-Token {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Username,
        [Parameter(Mandatory = $true)]
        [string]$Password
    )

    $response = Invoke-JsonRequest -Uri "$BaseUrl/auth/login" -Method Post -Body @{
        username = $Username
        password = $Password
    }

    if ([string]::IsNullOrWhiteSpace($response.token)) {
        throw "Login did not return a token for $Username."
    }

    return $response.token
}

if (-not (Test-Path -LiteralPath $SampleImage)) {
    throw "Sample image not found: $SampleImage"
}

$health = Wait-ForHealth -Uri "$BaseUrl/health"

$farmerToken = Get-Token -Username $FarmerUsername -Password $FarmerPassword
$shopToken = Get-Token -Username $ShopUsername -Password $ShopPassword
$technicianToken = Get-Token -Username $TechnicianUsername -Password $TechnicianPassword

$farmerHeaders = @{ Authorization = "Bearer $farmerToken" }
$shopHeaders = @{ Authorization = "Bearer $shopToken" }
$technicianHeaders = @{ Authorization = "Bearer $technicianToken" }

$overview = Invoke-JsonRequest -Uri "$BaseUrl/system/overview" -Method Get
$diagnosis = Invoke-MultipartRequest -Uri "$BaseUrl/diagnosis" -Headers $farmerHeaders -FilePath $SampleImage
$chat = Invoke-JsonRequest -Uri "$BaseUrl/chat" -Method Post -Headers $farmerHeaders -Body @{
    sessionId = "rehearsal-$runId"
    question = $Question
    useKnowledgeGraph = $true
    useVectorSearch = $true
}

$recommendationUri = "{0}/plans/recommendations?diseaseTag={1}&stageTag={2}" -f `
    $BaseUrl, `
    [System.Uri]::EscapeDataString($DiseaseTag), `
    [System.Uri]::EscapeDataString($StageTag)
$recommendations = Invoke-JsonRequest -Uri $recommendationUri -Method Get -Headers $farmerHeaders
if (-not $recommendations -or $recommendations.Count -eq 0) {
    throw "No recommendations were returned for $DiseaseTag / $StageTag."
}

$selectedPlan = @($recommendations | Where-Object { $_.diseaseTag -eq $DiseaseTag } | Select-Object -First 1)[0]
if (-not $selectedPlan) {
    $selectedPlan = @($recommendations | Select-Object -First 1)[0]
}
$consultation = Invoke-JsonRequest -Uri "$BaseUrl/consultations" -Method Post -Headers $farmerHeaders -Body @{
    planId = $selectedPlan.planId
    question = $Question
    reasonTags = $selectedPlan.reasonTags
}

$myConsultations = Invoke-JsonRequest -Uri "$BaseUrl/consultations/my" -Method Get -Headers $farmerHeaders
$inboxBefore = Invoke-JsonRequest -Uri "$BaseUrl/consultations/inbox" -Method Get -Headers $shopHeaders
$updatedConsultation = Invoke-JsonRequest -Uri "$BaseUrl/consultations/$($consultation.id)/status" -Method Post -Headers $shopHeaders -Body @{
    status = "contacted"
}
$trends = Invoke-JsonRequest -Uri "$BaseUrl/shop/trends" -Method Get -Headers $shopHeaders
$settings = Invoke-JsonRequest -Uri "$BaseUrl/system/settings" -Method Get -Headers $technicianHeaders

$report = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    baseUrl = $BaseUrl
    health = $health
    overview = [ordered]@{
        demoReady = $overview.demoReady
        activePlans = $overview.collaboration.activePlans
        consultationCount = $overview.collaboration.consultationCount
        pendingConsultations = $overview.collaboration.pendingConsultations
    }
    diagnosis = [ordered]@{
        disease = $diagnosis.disease
        confidence = $diagnosis.confidence
        engine = $diagnosis.engine
        demoMode = $diagnosis.demoMode
    }
    chat = [ordered]@{
        answerPreview = [string]$chat.answer
        sourceCount = @($chat.sources).Count
        entityCount = @($chat.knowledgeGraph.entities).Count
    }
    recommendation = [ordered]@{
        total = @($recommendations).Count
        selectedPlanId = $selectedPlan.planId
        selectedShop = $selectedPlan.shopName
        reasonTags = @($selectedPlan.reasonTags)
    }
    consultation = [ordered]@{
        createdId = $consultation.id
        myCount = @($myConsultations).Count
        inboxCountBeforeUpdate = @($inboxBefore).Count
        updatedStatus = $updatedConsultation.status
    }
    trends = @($trends | Select-Object -First 3)
    technician = [ordered]@{
        profile = $settings.environment.profile
        mysqlEnabled = $settings.storage.mysqlEnabled
        managedRoles = @($settings.platform.managedRoles)
    }
}

$report | ConvertTo-Json -Depth 10 | Set-Content -Path $reportPath -Encoding UTF8
Write-Host "Main flow rehearsal completed: $reportPath"
