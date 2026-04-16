param(
    [switch]$SkipInfra
)

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$mavenWrapper = Join-Path $root "mvnw.cmd"

function Import-DotEnvFile {
    param(
        [string]$Path
    )

    if (-not (Test-Path $Path)) {
        return $false
    }

    foreach ($rawLine in Get-Content $Path) {
        $line = $rawLine.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
            continue
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -lt 1) {
            continue
        }

        $name = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim()

        $hasDoubleQuotes = $value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')
        $hasSingleQuotes = $value.Length -ge 2 -and $value.StartsWith("'") -and $value.EndsWith("'")
        if ($hasDoubleQuotes -or $hasSingleQuotes) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }

    return $true
}

function Resolve-EnvValue {
    param(
        [string]$Name,
        [string]$Default = $null
    )

    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $Default
    }

    return $value
}

function Set-EnvDefault {
    param(
        [string]$Name,
        [string]$Default
    )

    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($Name, "Process"))) {
        [Environment]::SetEnvironmentVariable($Name, $Default, "Process")
    }
}

function Assert-RequiredEnv {
    param(
        [string]$Name,
        [string]$Hint
    )

    $value = Resolve-EnvValue $Name
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing required environment variable '$Name'. $Hint"
    }

    return $value
}

function Test-TcpPort {
    param(
        [string]$HostName,
        [int]$Port
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(1000, $false)) {
            return $false
        }

        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Wait-ForTcpPort {
    param(
        [string]$HostName,
        [int]$Port,
        [string]$DisplayName,
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -HostName $HostName -Port $Port) {
            Write-Host ($DisplayName + " is ready at " + $HostName + ":" + $Port)
            return
        }

        Start-Sleep -Seconds 2
    }

    throw ($DisplayName + " did not become ready within " + $TimeoutSeconds + " seconds.")
}

function Wait-ForHttpReady {
    param (
        [string]$Uri,
        [string]$DisplayName,
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                Write-Host ($DisplayName + " is ready at " + $Uri)
                return
            }
        } catch {
        }

        Start-Sleep -Seconds 2
    }

    throw ($DisplayName + " did not become ready within " + $TimeoutSeconds + " seconds.")
}

$loadedEnvFiles = @()
foreach ($fileName in @(".env", ".env.local")) {
    $filePath = Join-Path $root $fileName
    if (Import-DotEnvFile -Path $filePath) {
        $loadedEnvFiles += $fileName
    }
}

Set-EnvDefault -Name "SPRING_PROFILES_ACTIVE" -Default "discovery"
Set-EnvDefault -Name "GATEWAY_PORT" -Default "8080"
Set-EnvDefault -Name "NACOS_SERVER_ADDR" -Default "127.0.0.1:8848"
Set-EnvDefault -Name "MYSQL_HOST" -Default "localhost"
Set-EnvDefault -Name "MYSQL_PORT" -Default "3307"
Set-EnvDefault -Name "MYSQL_USERNAME" -Default "root"
Set-EnvDefault -Name "APP_RAG_MILVUS_URI" -Default "http://localhost:19530"
Set-EnvDefault -Name "APP_VISION_ENGINE" -Default "mock"
Set-EnvDefault -Name "APP_VISION_PYTHON_BASE_URL" -Default "http://localhost:8091"
Set-EnvDefault -Name "APP_RAG_ENABLED" -Default "true"
Set-EnvDefault -Name "APP_RAG_QWEN_ENABLED" -Default "true"
Set-EnvDefault -Name "APP_BOOTSTRAP_FOOD_METADATA_ENABLED" -Default "true"
Set-EnvDefault -Name "APP_BOOTSTRAP_FOOD_CONCEPT_BANK_PATH" -Default (Join-Path $root "ai-service\model\retrieval_bank.json")

$mysqlPassword = Assert-RequiredEnv -Name "MYSQL_PASSWORD" -Hint "Copy .env.example to .env and set the password for the MySQL instance this project should use."
if ([string]::IsNullOrWhiteSpace((Resolve-EnvValue "MYSQL_ROOT_PASSWORD"))) {
    [Environment]::SetEnvironmentVariable("MYSQL_ROOT_PASSWORD", $mysqlPassword, "Process")
}

$jwtSecret = Assert-RequiredEnv -Name "APP_JWT_SECRET" -Hint "Copy .env.example to .env and set a JWT secret with at least 32 characters."
if (-not $jwtSecret.StartsWith("base64:") -and $jwtSecret.Length -lt 32) {
    throw "APP_JWT_SECRET must be at least 32 characters, or use a base64: prefixed secret."
}

if ($loadedEnvFiles.Count -gt 0) {
    Write-Host ("Loaded environment from: " + ($loadedEnvFiles -join ", "))
} else {
    Write-Host "No .env or .env.local file found. Using variables already present in the current shell."
}

if ([string]::IsNullOrWhiteSpace((Resolve-EnvValue "APP_RAG_QWEN_API_KEY"))) {
    Write-Host "APP_RAG_QWEN_API_KEY is not set. Meal-service will skip Milvus bootstrap and fall back to local retrieval."
}

Write-Host ("MySQL target: " + (Resolve-EnvValue "MYSQL_HOST") + ":" + (Resolve-EnvValue "MYSQL_PORT"))

if (-not $SkipInfra) {
    $dockerInfoOutput = & docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ("Docker Desktop is not running or the Linux engine is unavailable. Start Docker Desktop first, then retry. Details: " + ($dockerInfoOutput | Out-String).Trim())
    }

    docker compose -f (Join-Path $root "docker-compose.dev.yml") up -d
}

$mysqlHost = Resolve-EnvValue "MYSQL_HOST"
$mysqlPort = [int](Resolve-EnvValue "MYSQL_PORT")
$nacosServerAddr = Resolve-EnvValue "NACOS_SERVER_ADDR"
$nacosHost = $nacosServerAddr
$nacosPort = 8848
if ($nacosServerAddr.Contains(":")) {
    $nacosParts = $nacosServerAddr.Split(":", 2)
    $nacosHost = $nacosParts[0]
    $nacosPort = [int]$nacosParts[1]
}

Write-Host "Waiting for infrastructure to become ready..."
Wait-ForTcpPort -HostName $mysqlHost -Port $mysqlPort -DisplayName "MySQL"
Wait-ForTcpPort -HostName $nacosHost -Port $nacosPort -DisplayName "Nacos port"
Wait-ForTcpPort -HostName $nacosHost -Port 9848 -DisplayName "Nacos gRPC port 9848"
Wait-ForTcpPort -HostName $nacosHost -Port 9849 -DisplayName "Nacos gRPC port 9849"
Wait-ForHttpReady -Uri ("http://" + $nacosServerAddr + "/nacos/") -DisplayName "Nacos console"

$services = @(
    @{
        Name = "user-service"
        ExtraEnv = @{
            MYSQL_DB = "nutrimind_user"
        }
    },
    @{
        Name = "food-service"
        ExtraEnv = @{
            MYSQL_FOOD_DB = "nutrimind_food"
        }
    },
    @{
        Name = "meal-service"
        ExtraEnv = @{
            MYSQL_MEAL_DB = "nutrimind_meal"
        }
    },
    @{
        Name = "ai-service"
        ExtraEnv = @{}
    },
    @{
        Name = "gateway-service"
        ExtraEnv = @{}
    }
)

$sharedEnv = [ordered]@{
    SPRING_PROFILES_ACTIVE = (Resolve-EnvValue "SPRING_PROFILES_ACTIVE")
    GATEWAY_PORT = (Resolve-EnvValue "GATEWAY_PORT")
    NACOS_SERVER_ADDR = (Resolve-EnvValue "NACOS_SERVER_ADDR")
    MYSQL_HOST = (Resolve-EnvValue "MYSQL_HOST")
    MYSQL_PORT = (Resolve-EnvValue "MYSQL_PORT")
    MYSQL_USERNAME = (Resolve-EnvValue "MYSQL_USERNAME")
    MYSQL_PASSWORD = (Resolve-EnvValue "MYSQL_PASSWORD")
    APP_JWT_SECRET = (Resolve-EnvValue "APP_JWT_SECRET")
    APP_RAG_ENABLED = (Resolve-EnvValue "APP_RAG_ENABLED")
    APP_RAG_MILVUS_URI = (Resolve-EnvValue "APP_RAG_MILVUS_URI")
    APP_RAG_QWEN_ENABLED = (Resolve-EnvValue "APP_RAG_QWEN_ENABLED")
    APP_RAG_QWEN_API_KEY = (Resolve-EnvValue "APP_RAG_QWEN_API_KEY")
    APP_RAG_QWEN_BASE_URL = (Resolve-EnvValue "APP_RAG_QWEN_BASE_URL")
    APP_RAG_QWEN_CHAT_MODEL = (Resolve-EnvValue "APP_RAG_QWEN_CHAT_MODEL")
    APP_RAG_QWEN_TEMPERATURE = (Resolve-EnvValue "APP_RAG_QWEN_TEMPERATURE")
    APP_VISION_ENGINE = (Resolve-EnvValue "APP_VISION_ENGINE")
    APP_VISION_PYTHON_BASE_URL = (Resolve-EnvValue "APP_VISION_PYTHON_BASE_URL")
    APP_BOOTSTRAP_FOOD_METADATA_ENABLED = (Resolve-EnvValue "APP_BOOTSTRAP_FOOD_METADATA_ENABLED")
    APP_BOOTSTRAP_FOOD_CONCEPT_BANK_PATH = (Resolve-EnvValue "APP_BOOTSTRAP_FOOD_CONCEPT_BANK_PATH")
}

foreach ($service in $services) {
    $envCommands = @()

    foreach ($entry in $sharedEnv.GetEnumerator()) {
        $value = $entry.Value.Replace("'", "''")
        $envCommands += '$env:' + $entry.Key + '=''' + $value + ''''
    }

    foreach ($entry in $service.ExtraEnv.GetEnumerator()) {
        $value = $entry.Value.Replace("'", "''")
        $envCommands += '$env:' + $entry.Key + '=''' + $value + ''''
    }

    $commandLines = @(
        'Set-Location ''' + $root + ''''
        $envCommands
        '& ''' + $mavenWrapper + ''' -pl ' + $service.Name + ' spring-boot:run'
    )

    Start-Process powershell -ArgumentList '-NoExit', '-Command', ($commandLines -join '; ')
}

Write-Host "Discovery mode services are starting in separate PowerShell windows."
Write-Host "Nacos console: http://localhost:8848/nacos"
Write-Host ("Gateway endpoint: http://localhost:" + (Resolve-EnvValue "GATEWAY_PORT") + "/api/test/hello")
