param(
    [string]$PythonExe = "python",
    [switch]$UseOfficialIndex
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$requirements = Join-Path $scriptDir "requirements.txt"
$requirementsAi4Food = Join-Path $scriptDir "requirements-ai4food.txt"

$env:PIP_NO_INDEX = ""
$env:HTTP_PROXY = ""
$env:HTTPS_PROXY = ""
$env:ALL_PROXY = ""
$env:GIT_HTTP_PROXY = ""
$env:GIT_HTTPS_PROXY = ""

$indexArgs = if ($UseOfficialIndex.IsPresent) {
    @("--index-url", "https://pypi.org/simple")
} else {
    @("-i", "https://pypi.tuna.tsinghua.edu.cn/simple")
}

Write-Host "Installing base inference dependencies..."
& $PythonExe -m pip install @indexArgs -r $requirements
if ($LASTEXITCODE -ne 0) {
    throw "Failed to install requirements.txt"
}

Write-Host "Installing AI4Food dependencies..."
& $PythonExe -m pip install @indexArgs -r $requirementsAi4Food
if ($LASTEXITCODE -ne 0) {
    throw "Failed to install requirements-ai4food.txt"
}

Write-Host "Dependency installation completed."
