param(
    [string]$BindAddress = "0.0.0.0",
    [int]$Port = 8091,
    [switch]$Reload
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$env:VISION_MODEL_BUNDLE = "food101_seed"
$env:VISION_BACKEND = "auto"

$command = @(
    "python",
    "-m",
    "uvicorn",
    "app.main:app",
    "--host", $BindAddress,
    "--port", "$Port"
)

if ($Reload.IsPresent) {
    $command += "--reload"
}

Write-Host "Starting NutriMind vision inference with model bundle: $env:VISION_MODEL_BUNDLE"
Write-Host "Backend preference: $env:VISION_BACKEND"
Write-Host "URL: http://$BindAddress`:$Port"

$executable = $command[0]
$arguments = $command[1..($command.Length - 1)]
& $executable @arguments
