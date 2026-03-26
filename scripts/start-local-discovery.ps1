param(
    [switch]$SkipInfra
)

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$mavenWrapper = Join-Path $root "mvnw.cmd"
$nacosAddr = "127.0.0.1:8848"

if (-not $SkipInfra) {
    docker compose -f (Join-Path $root "docker-compose.dev.yml") up -d
}

$services = @(
    @{
        Name = "user-service"
        Port = 8081
        ExtraEnv = @{
            MYSQL_DB = "nutrimind_user"
        }
    },
    @{
        Name = "food-service"
        Port = 8082
        ExtraEnv = @{
            MYSQL_FOOD_DB = "nutrimind_food"
        }
    },
    @{
        Name = "meal-service"
        Port = 8083
        ExtraEnv = @{
            MYSQL_MEAL_DB = "nutrimind_meal"
        }
    },
    @{
        Name = "gateway-service"
        Port = 8080
        ExtraEnv = @{}
    }
)

foreach ($service in $services) {
    $envCommands = @(
        '$env:SPRING_PROFILES_ACTIVE=''discovery'''
        '$env:NACOS_SERVER_ADDR=''' + $nacosAddr + ''''
        '$env:MYSQL_HOST=''localhost'''
        '$env:MYSQL_PORT=''3306'''
        '$env:MYSQL_USERNAME=''root'''
        '$env:MYSQL_PASSWORD=''123456'''
    )

    foreach ($entry in $service.ExtraEnv.GetEnumerator()) {
        $envCommands += '$env:' + $entry.Key + '=''' + $entry.Value + ''''
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
Write-Host "Gateway endpoint: http://localhost:8080/api/test/hello"
