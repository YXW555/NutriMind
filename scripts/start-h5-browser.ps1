param(
    [string]$BindHost = "127.0.0.1",
    [int]$Port = 5173,
    [switch]$OpenBrowser
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $projectRoot "frontend-app"
$hbuilderRoot = "D:\HBuilder\HBuilderX"
$hbuilderPlugins = Join-Path $hbuilderRoot "plugins"
$uniCmd = "D:\HBuilder\HBuilderX\plugins\uniapp-cli-vite\node_modules\.bin\uni.cmd"

if (-not (Test-Path $frontendRoot)) {
    throw "未找到前端目录: $frontendRoot"
}

if (-not (Test-Path $uniCmd)) {
    throw "未找到 uni H5 启动器: $uniCmd"
}

if (-not (Test-Path $hbuilderPlugins)) {
    throw "未找到 HBuilderX 插件目录: $hbuilderPlugins"
}

$existingProcessIds = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique

if ($existingProcessIds) {
    Write-Host "H5 浏览器开发服务已经在运行: http://$BindHost`:$Port" -ForegroundColor Green
    Write-Host "监听进程: $($existingProcessIds -join ', ')" -ForegroundColor DarkGray
    if ($OpenBrowser) {
        Start-Process "http://$BindHost`:$Port"
    }
    return
}

$command = @"
`$env:UNI_INPUT_DIR = '$frontendRoot'
`$env:VITE_ROOT_DIR = '$frontendRoot'
`$env:HX_APP_ROOT = '$hbuilderRoot'
`$env:UNI_HBUILDERX_PLUGINS = '$hbuilderPlugins'
`$env:RUN_BY_HBUILDERX = 'true'
`$env:HX_Version = '4.45'
& '$uniCmd' -p h5 --host $BindHost --port $Port
"@

Start-Process powershell `
    -WorkingDirectory $frontendRoot `
    -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", $command
    ) | Out-Null

Write-Host "已在新窗口启动 H5 浏览器开发服务: http://$BindHost`:$Port" -ForegroundColor Green

if ($OpenBrowser) {
    Start-Process "http://$BindHost`:$Port"
}
