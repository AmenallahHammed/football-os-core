#!/usr/bin/env bash
# Start the infra-only Docker stack for hybrid local development and print
# the native Maven commands for running the three Spring Boot app services.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

set -a
source .env.dev
set +a

docker compose -f docker-compose.infra.yml up -d

echo ""
echo "Infrastructure is up. Start app services in separate terminals:"
echo ""
echo "  cd fos-governance-service"
echo "  mvn \"-Dspring-boot.run.profiles=dev\" spring-boot:run"
echo ""
echo "  cd fos-workspace-service"
echo "  mvn \"-Dspring-boot.run.profiles=dev\" spring-boot:run"
echo ""
echo "  cd fos-gateway"
echo "  mvn \"-Dspring-boot.run.profiles=dev\" spring-boot:run"
