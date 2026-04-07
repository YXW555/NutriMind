param(
    [string]$PythonExe = "python",
    [string]$BindAddress = "0.0.0.0",
    [int]$Port = 8091,
    [switch]$Reload
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$bundleDirPath = Join-Path $scriptDir "..\..\model\ai4food_subcategory"
$legacyBundleDirPath = Join-Path $scriptDir "..\..\..\model\ai4food_subcategory"

$primaryHasModel = (Test-Path $bundleDirPath) -and (Get-ChildItem $bundleDirPath -File -Filter *.hdf5 -ErrorAction SilentlyContinue)
if (-not $primaryHasModel -and (Test-Path $legacyBundleDirPath)) {
    $bundleDirPath = $legacyBundleDirPath
}
$bundleDir = Resolve-Path $bundleDirPath
$metadataPath = Join-Path $bundleDir "metadata.json"
$templatePath = Join-Path $bundleDir "metadata.template.json"
$modelCandidates = @(
    (Join-Path $bundleDir "model.keras"),
    (Join-Path $bundleDir "model.h5"),
    (Join-Path $bundleDir "model.hdf5"),
    (Join-Path $bundleDir "saved_model"),
    (Join-Path $bundleDir "efficientnetv2_subcategory_model.hdf5")
)

if (-not (Test-Path $metadataPath) -and (Test-Path $templatePath)) {
    Write-Warning "metadata.json is missing. Copy model\\ai4food_subcategory\\metadata.template.json to metadata.json first."
}

if (-not ($modelCandidates | Where-Object { Test-Path $_ })) {
    throw "AI4Food model file is missing. Put model.keras / model.h5 / SavedModel under $bundleDir first."
}

$pythonVersion = & $PythonExe -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')"
if ($LASTEXITCODE -eq 0 -and ([version]$pythonVersion -lt [version]"3.10" -or [version]$pythonVersion -gt [version]"3.13")) {
    Write-Warning "TensorFlow pip on Windows is officially supported for Python 3.10 to 3.13. If install fails, switch to a 3.12 virtual environment."
}

$env:VISION_MODEL_BUNDLE = "ai4food_subcategory"
$env:VISION_BACKEND = "classifier"

$command = @(
    $PythonExe,
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
Write-Host "Bundle path: $bundleDir"
Write-Host ("URL: http://{0}:{1}" -f $BindAddress, $Port)

$executable = $command[0]
$arguments = $command[1..($command.Length - 1)]
& $executable @arguments
