Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$processFile = Join-Path $root 'reports\runtime\demo-processes.json'

if (-not (Test-Path $processFile)) {
    Write-Host '未找到运行中的演示进程记录。'
    exit 0
}

$processInfo = Get-Content -Path $processFile -Raw | ConvertFrom-Json
$ids = @($processInfo.diagnosisPid, $processInfo.backendPid, $processInfo.frontendPid) | Where-Object { $_ }

$whitelist = @('python', 'java', 'node', 'npm')

foreach ($id in $ids) {
    $proc = Get-Process -Id $id -ErrorAction SilentlyContinue
    if (-not $proc) {
        Write-Host "进程 $id 已不存在。"
        continue
    }

    if ($proc.ProcessName -notin $whitelist) {
        Write-Warning "进程 $id 名称为 '$($proc.ProcessName)'，不在白名单中，跳过。"
        continue
    }

    Write-Host "正在优雅停止进程 $id ($($proc.ProcessName))..."
    $closed = $proc.CloseMainWindow()
    if ($closed) {
        Start-Sleep -Seconds 5
        $proc.Refresh()
    }

    if (-not $proc.HasExited) {
        try {
            $proc.Kill()
            Write-Host "已强制停止进程 $id"
        } catch {
            Write-Warning "无法强制停止进程 $id，可能已经退出。"
        }
    } else {
        Write-Host "进程 $id 已优雅退出。"
    }
}

Remove-Item -Path $processFile -Force -ErrorAction SilentlyContinue
Write-Host '演示服务已停止。'
