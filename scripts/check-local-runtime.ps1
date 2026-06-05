$ErrorActionPreference = "Stop"

$requiredContainers = @(
  "fos-postgres",
  "fos-mongodb",
  "fos-redis",
  "fos-zookeeper",
  "fos-kafka",
  "fos-keycloak",
  "fos-minio",
  "fos-opa",
  "fos-onlyoffice"
)

$requiredPorts = @(5432, 6379, 8080, 8081, 8082, 8084, 8180, 8181, 9000, 9001, 9092, 27017)

$httpChecks = @(
  @{ Name = "Gateway health"; Url = "http://localhost:8080/actuator/health" },
  @{ Name = "Governance health"; Url = "http://localhost:8081/actuator/health" },
  @{ Name = "Workspace health"; Url = "http://localhost:8082/actuator/health" },
  @{ Name = "Keycloak"; Url = "http://localhost:8180" },
  @{ Name = "MinIO console"; Url = "http://localhost:9001" },
  @{ Name = "OnlyOffice api.js"; Url = "http://localhost:8084/web-apps/apps/api/documents/api.js" }
)

function Test-LocalPort {
  param([int]$Port)

  try {
    $tcpClient = [System.Net.Sockets.TcpClient]::new()
    $async = $tcpClient.BeginConnect("127.0.0.1", $Port, $null, $null)
    $connected = $async.AsyncWaitHandle.WaitOne(1500, $false)
    if (-not $connected) {
      $tcpClient.Close()
      return $false
    }
    $tcpClient.EndConnect($async)
    $tcpClient.Close()
    return $true
  } catch {
    return $false
  }
}

function Test-HttpEndpoint {
  param([string]$Url)

  try {
    $statusText = & curl.exe -s -o NUL -w "%{http_code}" $Url
    $statusCode = 0
    if (-not [int]::TryParse(($statusText | Out-String).Trim(), [ref]$statusCode)) {
      return @{ Ok = $false; Detail = "invalid status output: $statusText" }
    }

    if ($statusCode -ge 200 -and $statusCode -lt 500) {
      return @{ Ok = $true; Detail = $statusCode }
    }

    return @{ Ok = $false; Detail = $statusCode }
  } catch {
    return @{ Ok = $false; Detail = $_.Exception.Message }
  }
}

Write-Host "Checking Docker containers..." -ForegroundColor Cyan
$runningContainers = docker compose ps --format json | ConvertFrom-Json

foreach ($container in $requiredContainers) {
  $match = $runningContainers | Where-Object { $_.Name -eq $container -and $_.State -eq "running" }
  if ($null -eq $match) {
    Write-Host "[FAIL] Container not running: $container" -ForegroundColor Red
  } else {
    Write-Host "[ OK ] Container running: $container" -ForegroundColor Green
  }
}

Write-Host ""
Write-Host "Checking local ports..." -ForegroundColor Cyan
foreach ($port in $requiredPorts) {
  if (Test-LocalPort -Port $port) {
    Write-Host "[ OK ] Port open: $port" -ForegroundColor Green
  } else {
    Write-Host "[FAIL] Port closed: $port" -ForegroundColor Red
  }
}

Write-Host ""
Write-Host "Checking HTTP endpoints..." -ForegroundColor Cyan
foreach ($check in $httpChecks) {
  $result = Test-HttpEndpoint -Url $check.Url
  if ($result.Ok) {
    Write-Host "[ OK ] $($check.Name): $($check.Url) [$($result.Detail)]" -ForegroundColor Green
  } else {
    Write-Host "[FAIL] $($check.Name): $($check.Url) [$($result.Detail)]" -ForegroundColor Red
  }
}
