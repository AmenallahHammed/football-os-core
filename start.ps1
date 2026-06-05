$ErrorActionPreference = "Stop"

Write-Host "This script restarts the local infrastructure containers for the Monday demo." -ForegroundColor Yellow
Write-Host "It does not run the Spring Boot apps or frontend for you." -ForegroundColor Yellow
$confirmation = Read-Host "Continue? Type YES to proceed"
if ($confirmation -ne "YES") {
  Write-Host "Aborted by user." -ForegroundColor Yellow
  exit 1
}

Write-Host "[1/3] Stopping existing infrastructure..." -ForegroundColor Cyan
docker compose down

Write-Host "[2/3] Starting infrastructure in detached mode..." -ForegroundColor Cyan
docker compose up -d

Write-Host "[3/3] Infrastructure started:" -ForegroundColor Green
Write-Host "- Keycloak:            http://localhost:8180"
Write-Host "- OPA mock:            http://localhost:8181/v1/data/fos/allow"
Write-Host "- PostgreSQL:          localhost:5432"
Write-Host "- MongoDB:             localhost:27017"
Write-Host "- Redis:               localhost:6379"
Write-Host "- Kafka:               localhost:9092"
Write-Host "- MinIO API:           http://localhost:9000"
Write-Host "- MinIO Console:       http://localhost:9001"
Write-Host "- OnlyOffice:          http://localhost:8084"
Write-Host ""
Write-Host "Next run these from the repository root:" -ForegroundColor Cyan
Write-Host "  mvn -pl fos-governance-service -am -DskipTests install"
Write-Host "  mvn ""-Dspring-boot.run.arguments=--spring.profiles.active=dev"" -f fos-governance-service/pom.xml spring-boot:run"
Write-Host "  mvn -pl fos-workspace-service -am -DskipTests install"
Write-Host "  mvn ""-Dspring-boot.run.arguments=--spring.profiles.active=dev"" -f fos-workspace-service/pom.xml spring-boot:run"
Write-Host "  mvn -pl fos-gateway -am -DskipTests install"
Write-Host "  mvn ""-Dspring-boot.run.arguments=--spring.profiles.active=dev"" -f fos-gateway/pom.xml spring-boot:run"
Write-Host "  cd fos-workspace-frontend; npm start"
