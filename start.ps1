$ErrorActionPreference = "Stop"

Write-Host "[1/4] Stopping existing stack..." -ForegroundColor Cyan
docker-compose down

Write-Host "[2/4] Building images (no cache)..." -ForegroundColor Cyan
docker-compose build --no-cache

Write-Host "[3/4] Starting stack in detached mode..." -ForegroundColor Cyan
docker-compose up -d

Write-Host "[4/4] Services started:" -ForegroundColor Green
Write-Host "- Gateway:             http://localhost:8080"
Write-Host "- Governance Service:  http://localhost:8081"
Write-Host "- Workspace Service:   http://localhost:8082"
Write-Host "- Keycloak:            http://localhost:8180"
Write-Host "- MinIO Console:       http://localhost:9001"
Write-Host "- OnlyOffice:          http://localhost:8090"
