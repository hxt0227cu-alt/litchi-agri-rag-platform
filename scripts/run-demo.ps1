param(
    [switch]$Build
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$runtimeDir = Join-Path $root 'reports\runtime'
$processFile = Join-Path $runtimeDir 'demo-processes.json'

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

# PATH 可执行性检查
foreach ($cmd in @('python','java','npm')) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        throw "Required command '$cmd' was not found in PATH."
    }
}

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

$script:startedProcesses = New-Object System.Collections.Generic.List[System.Diagnostics.Process]

function Stop-StartedProcesses {
    foreach ($proc in $script:startedProcesses) {
        if ($proc -and -not $proc.HasExited) {
            try {
                $proc.Kill()
                $proc.WaitForExit(5000) | Out-Null
            } catch {
                Write-Warning "Failed to kill process $($proc.Id): $_"
            }
        }
    }
    $script:startedProcesses.Clear()
}

Register-EngineEvent PowerShell.Exiting -Action {
    Stop-StartedProcesses
} | Out-Null

try {
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

    $jarFile = Get-ChildItem -Path (Join-Path $root 'backend\target\*.jar') | Select-Object -First 1
    if (-not $jarFile) {
        throw "No JAR file found in backend\target. Please build the backend first."
    }

    $diagOut = Join-Path $runtimeDir 'diagnosis.out.log'
    $diagErr = Join-Path $runtimeDir 'diagnosis.err.log'
    $backOut = Join-Path $runtimeDir 'backend.out.log'
    $backErr = Join-Path $runtimeDir 'backend.err.log'
    $frontOut = Join-Path $runtimeDir 'frontend.out.log'
    $frontErr = Join-Path $runtimeDir 'frontend.err.log'

    $diagnosis = Start-Process -FilePath 'python' -ArgumentList '-u', (Join-Path $root 'diagnosis-service\server.py') -PassThru -RedirectStandardOutput $diagOut -RedirectStandardError $diagErr
    $backend = Start-Process -FilePath 'java' -ArgumentList '-jar', $jarFile.FullName, '--spring.profiles.active=dev' -PassThru -RedirectStandardOutput $backOut -RedirectStandardError $backErr
    $frontend = Start-Process -FilePath 'npm.cmd' -ArgumentList 'run', 'dev', '--', '--host', '0.0.0.0' -WorkingDirectory (Join-Path $root 'frontend') -PassThru -RedirectStandardOutput $frontOut -RedirectStandardError $frontErr

    $script:startedProcesses.Add($diagnosis) | Out-Null
    $script:startedProcesses.Add($backend) | Out-Null
    $script:startedProcesses.Add($frontend) | Out-Null

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
            $loginBody = @{
                username = 'technician'
                password = 'demo123'
            } | ConvertTo-Json
            $loginResult = Invoke-RestMethod `
                -Uri 'http://127.0.0.1:8080/api/auth/login' `
                -Method Post `
                -ContentType 'application/json' `
                -Body $loginBody `
                -TimeoutSec 10
            $headers = @{
                Authorization = "Bearer $($loginResult.token)"
            }
            Invoke-RestMethod `
                -Uri 'http://127.0.0.1:8080/api/system/demo/bootstrap' `
                -Method Post `
                -Headers $headers `
                -TimeoutSec 10 | Out-Null
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
    Write-Host '按 Ctrl+C 可结束演示并自动关闭服务。'

    while ($true) {
        Start-Sleep -Seconds 1
        if ($diagnosis.HasExited -or $backend.HasExited -or $frontend.HasExited) {
            Write-Warning 'One of the services has exited unexpectedly.'
            break
        }
    }
} finally {
    Stop-StartedProcesses
}
