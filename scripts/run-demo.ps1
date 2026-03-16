param(
    [switch]$Build
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$runtimeDir = Join-Path $root 'reports\runtime'
$processFile = Join-Path $runtimeDir 'demo-processes.json'

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

function Wait-HttpReady {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url,
        [int]$RetryCount = 24,
        [int]$DelaySeconds = 2
    )

    for ($i = 0; $i -lt $RetryCount; $i++) {
        try {
            Invoke-RestMethod -Uri $Url -TimeoutSec 5 | Out-Null
            return $true
        } catch {
            Start-Sleep -Seconds $DelaySeconds
        }
    }

    return $false
}

if ($Build) {
    Write-Host '[1/4] 构建后端...'
    Push-Location (Join-Path $root 'backend')
    try {
        mvn -q -DskipTests package
    } finally {
        Pop-Location
    }

    Write-Host '[2/4] 构建前端...'
    Push-Location (Join-Path $root 'frontend')
    try {
        npm.cmd run build
    } finally {
        Pop-Location
    }
}

Write-Host '[3/4] 启动识别服务、后端和前端开发服务...'
$diagnosis = Start-Process -FilePath 'python' -ArgumentList '-u', (Join-Path $root 'diagnosis-service\server.py') -PassThru
$backend = Start-Process -FilePath 'java' -ArgumentList '-jar', (Join-Path $root 'backend\target\litchi-qa-platform-1.0.0.jar'), '--spring.profiles.active=dev' -PassThru
$frontend = Start-Process -FilePath 'npm.cmd' -ArgumentList 'run', 'dev', '--', '--host', '0.0.0.0' -WorkingDirectory (Join-Path $root 'frontend') -PassThru

$processInfo = [ordered]@{
    diagnosisPid = $diagnosis.Id
    backendPid = $backend.Id
    frontendPid = $frontend.Id
    startedAt = (Get-Date).ToString('s')
}
$processInfo | ConvertTo-Json | Set-Content -Path $processFile -Encoding UTF8

Write-Host '[4/4] 等待服务就绪并准备答辩样例...'
$backendReady = Wait-HttpReady -Url 'http://127.0.0.1:8080/api/health'
$diagnosisReady = Wait-HttpReady -Url 'http://127.0.0.1:8090/health'
$frontendReady = Wait-HttpReady -Url 'http://127.0.0.1:5173'

if ($backendReady) {
    try {
        Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/system/demo/bootstrap' -Method Post -TimeoutSec 10 | Out-Null
    } catch {
        Write-Warning '答辩样例初始化调用失败，但系统仍可使用自动导入的本地样例。'
    }
}

Write-Host ''
Write-Host '演示地址:'
Write-Host '  前端:   http://127.0.0.1:5173'
Write-Host '  后端:   http://127.0.0.1:8080/api/health'
Write-Host '  识别:   http://127.0.0.1:8090/health'
Write-Host ''
Write-Host '服务状态:'
Write-Host "  后端:   $(if ($backendReady) { '已就绪' } else { '未就绪' })"
Write-Host "  识别:   $(if ($diagnosisReady) { '已就绪' } else { '未就绪' })"
Write-Host "  前端:   $(if ($frontendReady) { '已就绪' } else { '未就绪' })"
Write-Host ''
Write-Host "进程信息已写入: $processFile"
Write-Host '结束演示后可运行 scripts\stop-demo.ps1 关闭服务。'
