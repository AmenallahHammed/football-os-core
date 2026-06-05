#!/usr/bin/env bash
# Exit on first error, undefined variable, or failed pipeline command.
set -euo pipefail

# Define the Maven module, display name, and expected port for this service.
MODULE="fos-workspace-service"
SERVICE_NAME="FOS Workspace Service"
PORT="8082"

# Verify the script is executed from repository root.
if [[ ! -f "pom.xml" || ! -d "fos-sdk" || ! -d "fos-gateway" || ! -d "fos-governance-service" || ! -d "fos-workspace-service" ]]; then
  echo "Error: run this script from the football-os-core repository root."
  echo "Tip: the current directory must contain pom.xml and all module folders."
  exit 1
fi

# Print a clear startup message for the developer.
echo "Starting ${SERVICE_NAME} on http://localhost:${PORT}"

# Install the target module with its local dependencies so spring-boot:run
# resolves the latest SDK snapshots from the local Maven repository.
mvn -pl "${MODULE}" -am -DskipTests install
mvn "-Dspring-boot.run.arguments=--server.port=${PORT} --spring.profiles.active=dev" -f "${MODULE}/pom.xml" spring-boot:run
