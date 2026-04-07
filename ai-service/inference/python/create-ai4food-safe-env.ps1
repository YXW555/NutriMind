param(
    [string]$CondaExe = "D:\miniconda\Scripts\conda.exe",
    [string]$EnvPath,
    [switch]$UseOfficialIndex,
    [switch]$ForceRecreate
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $scriptDir))
if (-not $EnvPath) {
    $EnvPath = Join-Path $repoRoot ".ai4food-safe-env"
}

if (-not (Test-Path $CondaExe)) {
    throw "Conda executable not found: $CondaExe"
}

$requirementsPath = Join-Path $scriptDir "requirements-ai4food-official-compat.txt"
$pythonExe = Join-Path $EnvPath "python.exe"

$env:PIP_NO_INDEX = ""
$env:HTTP_PROXY = ""
$env:HTTPS_PROXY = ""
$env:ALL_PROXY = ""
$env:GIT_HTTP_PROXY = ""
$env:GIT_HTTPS_PROXY = ""
$env:CONDA_NO_PLUGINS = "true"
$env:CONDA_OVERRIDE_CUDA = "0"
$env:PYTHONIOENCODING = "utf-8"
$env:PYTHONUTF8 = "1"

if ($ForceRecreate.IsPresent -and (Test-Path $EnvPath)) {
    Write-Host "Removing existing environment at $EnvPath"
    Remove-Item -Recurse -Force $EnvPath
}

if (-not (Test-Path $pythonExe)) {
    Write-Host "Creating conda environment at $EnvPath"
    & $CondaExe --no-plugins create -p $EnvPath python=3.10 pip -y
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create conda environment"
    }
}

$indexArgs = if ($UseOfficialIndex.IsPresent) {
    @("--index-url", "https://pypi.org/simple")
} else {
    @("-i", "https://pypi.tuna.tsinghua.edu.cn/simple")
}

Write-Host "Installing AI4Food official-compatible dependencies"
& $pythonExe -m pip install @indexArgs -r $requirementsPath
if ($LASTEXITCODE -ne 0) {
    if (-not $UseOfficialIndex.IsPresent) {
        Write-Warning "Mirror failed, retrying with the official PyPI index"
        & $pythonExe -m pip install --index-url https://pypi.org/simple -r $requirementsPath
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to install compatibility dependencies"
        }
    } else {
        throw "Failed to install compatibility dependencies"
    }
}

Write-Host "Environment created successfully."
Write-Host "Python executable: $pythonExe"
Write-Host "Start inference with:"
Write-Host "  .\start-ai4food-subcategory.ps1 -PythonExe `"$pythonExe`""
