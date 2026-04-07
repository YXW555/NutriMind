param(
    [string]$BundleName = "ai4food_subcategory",
    [string]$Architecture = "efficientnetv2",
    [string]$ModelName = "subcategory",
    [switch]$Force
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$aiServiceRoot = Resolve-Path (Join-Path $scriptDir "..\..")
$bundleDir = Join-Path $aiServiceRoot "model\$BundleName"
$metadataPath = Join-Path $bundleDir "metadata.json"
$templatePath = Join-Path $bundleDir "metadata.template.json"

$modelFileName = "{0}_{1}_model.hdf5" -f $Architecture, $ModelName
$classesFileName = "{0}_classes.txt" -f $ModelName

$modelUrl = "https://bidalab.eps.uam.es/static/AI4Food-NutritionDB/$modelFileName"
$classesUrl = "https://raw.githubusercontent.com/BiDAlab/AI4Food-NutritionDB/main/src/$classesFileName"

$env:HTTP_PROXY = ""
$env:HTTPS_PROXY = ""
$env:ALL_PROXY = ""
$env:GIT_HTTP_PROXY = ""
$env:GIT_HTTPS_PROXY = ""

if (-not (Test-Path $bundleDir)) {
    New-Item -ItemType Directory -Path $bundleDir | Out-Null
}

$modelPath = Join-Path $bundleDir $modelFileName
$classesPath = Join-Path $bundleDir $classesFileName

if ($Force.IsPresent -or -not (Test-Path $modelPath)) {
    Write-Host "Downloading official AI4Food model: $modelUrl"
    Invoke-WebRequest -Uri $modelUrl -OutFile $modelPath
}

if ($Force.IsPresent -or -not (Test-Path $classesPath)) {
    Write-Host "Downloading official class file: $classesUrl"
    Invoke-WebRequest -Uri $classesUrl -OutFile $classesPath
}

$metadata = if (Test-Path $templatePath) {
    Get-Content -Raw -Encoding UTF8 $templatePath | ConvertFrom-Json
} else {
    [pscustomobject]@{}
}

$metadata | Add-Member -NotePropertyName bundle_name -NotePropertyValue $BundleName -Force
$metadata | Add-Member -NotePropertyName model_version -NotePropertyValue ("ai4food-{0}-official-v1" -f $ModelName) -Force
$metadata | Add-Member -NotePropertyName status -NotePropertyValue "ready" -Force
$metadata | Add-Member -NotePropertyName task_type -NotePropertyValue "single-label-image-classification" -Force
$metadata | Add-Member -NotePropertyName backbone -NotePropertyValue ("AI4Food {0}" -f $Architecture) -Force
$metadata | Add-Member -NotePropertyName runtime -NotePropertyValue "tensorflow" -Force
$metadata | Add-Member -NotePropertyName classifier_adapter -NotePropertyValue "ai4food_official" -Force
$metadata | Add-Member -NotePropertyName export_format -NotePropertyValue "hdf5" -Force
$metadata | Add-Member -NotePropertyName image_size -NotePropertyValue 224 -Force
$metadata | Add-Member -NotePropertyName input_layout -NotePropertyValue "nhwc" -Force
$metadata | Add-Member -NotePropertyName preprocess -NotePropertyValue "zero_one" -Force
$metadata | Add-Member -NotePropertyName model_file -NotePropertyValue $modelFileName -Force
$metadata | Add-Member -NotePropertyName labels_file -NotePropertyValue $classesFileName -Force
$metadata | Add-Member -NotePropertyName source_repository -NotePropertyValue "BiDAlab/AI4Food-NutritionDB" -Force
$metadata | Add-Member -NotePropertyName source_model_url -NotePropertyValue $modelUrl -Force
$metadata | Add-Member -NotePropertyName source_classes_url -NotePropertyValue $classesUrl -Force

$metadata | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 $metadataPath

Write-Host "AI4Food bundle prepared:"
Write-Host "  Bundle dir: $bundleDir"
Write-Host "  Model file: $modelPath"
Write-Host "  Labels file: $classesPath"
Write-Host "  Metadata:   $metadataPath"
