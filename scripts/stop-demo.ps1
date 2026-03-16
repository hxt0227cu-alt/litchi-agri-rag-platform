$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$processFile = Join-Path $root 'reports\runtime\demo-processes.json'

if (-not (Test-Path $processFile)) {
    Write-Host '未找到运行中的演示进程记录。'
    exit 0
}

$processInfo = Get-Content -Path $processFile -Raw | ConvertFrom-Json
$ids = @($processInfo.diagnosisPid, $processInfo.backendPid, $processInfo.frontendPid) | Where-Object { $_ }

foreach ($id in $ids) {
    try {
        Stop-Process -Id $id -Force -ErrorAction Stop
        Write-Host "已停止进程 $id"
    } catch {
        Write-Warning "无法停止进程 $id，可能已经退出。"
    }
}

Remove-Item -Path $processFile -Force -ErrorAction SilentlyContinue
Write-Host '演示服务已停止。'
