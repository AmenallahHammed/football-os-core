#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

docker compose up -d

echo ""
echo "Infrastructure is up. Start app services from the repository root in separate terminals:"
echo ""
echo "  mvn -pl fos-governance-service -am spring-boot:run"
echo "  mvn -pl fos-workspace-service -am spring-boot:run"
echo "  mvn -pl fos-gateway -am spring-boot:run"
echo ""
echo "Then start the frontend:"
echo "  cd fos-workspace-frontend && npm start"
