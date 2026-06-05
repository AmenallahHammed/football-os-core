#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

docker compose up -d

echo ""
echo "Infrastructure is up. Start app services from the repository root in separate terminals:"
echo ""
echo "  ./run-governance.sh"
echo "  ./run-workspace.sh"
echo "  ./run-gateway.sh"
echo ""
echo "Then start the frontend:"
echo "  cd fos-workspace-frontend && npm start"
