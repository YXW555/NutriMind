param(
    [switch]$StopInfra
)

$root = Resolve-Path (Join-Path $PSScriptRoot "..")

$servicePatterns = @(
    "-pl user-service spring-boot:run",
    "-pl food-service spring-boot:run",
    "-pl meal-service spring-boot:run",
    "-pl ai-service spring-boot:run",
    "-pl gateway-service spring-boot:run",
    "com.yxw.user.UserServiceApplication",
    "com.yxw.food.FoodServiceApplication",
    "com.yxw.meal.MealServiceApplication",
    "com.yxw.ai.AiServiceApplication",
    "com.yxw.gateway.GatewayServiceApplication"
)

$processes = Get-CimInstance Win32_Process | Where-Object {
    $commandLine = $_.CommandLine
    if ([string]::IsNullOrWhiteSpace($commandLine)) {
        return $false
    }

    foreach ($pattern in $servicePatterns) {
        if ($commandLine -like ("*" + $pattern + "*")) {
            return $true
        }
    }

    return $false
}

$stoppedIds = New-Object System.Collections.Generic.HashSet[int]
foreach ($process in $processes | Sort-Object ProcessId -Descending) {
    if ($stoppedIds.Contains([int]$process.ProcessId)) {
        continue
    }

    try {
        Stop-Process -Id $process.ProcessId -Force -ErrorAction Stop
        $stoppedIds.Add([int]$process.ProcessId) | Out-Null
        Write-Host ("Stopped PID " + $process.ProcessId + ": " + $process.Name)
    } catch {
        Write-Host ("Skip PID " + $process.ProcessId + ": " + $_.Exception.Message)
    }
}

if ($stoppedIds.Count -eq 0) {
    Write-Host "No local discovery service processes were found."
} else {
    Write-Host ("Stopped " + $stoppedIds.Count + " local service process(es).")
}

if ($StopInfra) {
    docker compose -f (Join-Path $root "docker-compose.dev.yml") stop
    Write-Host "Docker infrastructure has been stopped."
} else {
    Write-Host "Docker infrastructure is still running. Use -StopInfra if you want to stop MySQL, Redis, Milvus, and Nacos too."
}
