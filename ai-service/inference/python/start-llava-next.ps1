param(
    [string]$BindAddress = "0.0.0.0",
    [int]$Port = 8091,
    [string]$ModelId = "llava-hf/llava-v1.6-mistral-7b-hf",
    [string]$ModelVersion = "llava-next-v1.6-mistral-7b",
    [string]$Backend = "llava",
    [switch]$Reload
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$env:VISION_BACKEND = $Backend
$env:VISION_LLAVA_MODEL_ID = $ModelId
$env:VISION_LLAVA_MODEL_VERSION = $ModelVersion

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

Write-Host "Starting NutriMind LLaVA-NeXT inference service"
Write-Host "Backend preference: $env:VISION_BACKEND"
Write-Host "Model ID: $env:VISION_LLAVA_MODEL_ID"
Write-Host "Model version: $env:VISION_LLAVA_MODEL_VERSION"
Write-Host "URL: http://$BindAddress`:$Port"

$executable = $command[0]
$arguments = $command[1..($command.Length - 1)]
& $executable @arguments
