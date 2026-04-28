#!/usr/bin/env bash
# Exit on first error, undefined variable, or failed pipeline command.
set -euo pipefail

# Define the Maven module, display name, and expected port for this service.
MODULE="fos-gateway"
SERVICE_NAME="FOS Gateway"
PORT="8080"

# Verify the script is executed from repository root.
if [[ ! -f "pom.xml" || ! -d "fos-sdk" || ! -d "fos-gateway" || ! -d "fos-governance-service" || ! -d "fos-workspace-service" ]]; then
  echo "Error: run this script from the football-os-core repository root."
  echo "Tip: the current directory must contain pom.xml and all module folders."
  exit 1
fi

# Print a clear startup message for the developer.
echo "Starting ${SERVICE_NAME} on http://localhost:${PORT}"

# Run only this app module to avoid root-reactor ambiguity.
mvn "-Dspring-boot.run.arguments=--server.port=${PORT}" -pl "${MODULE}" spring-boot:run
