Context: I am on Windows using Docker Desktop with a Maven-based Java project. My integration tests use Testcontainers. They are failing with this exact error output:
Could not find a valid Docker environment

NpipeSocketClientProviderStrategy: failed with exception BadRequestException
(Status 400: {"ID":"","Containers":0,"ServerVersion":"","Labels":
["com.docker.desktop.address=npipe://\\\\.\\pipe\\docker_cli"],...})

java.lang.IllegalStateException: Could not find a valid Docker environment.
    at org.testcontainers.dockerclient.DockerClientProviderStrategy.getFirstValidStrategy(...)
    at org.testcontainers.DockerClientFactory...
    at org.testcontainers.containers.GenericContainer.start(...)
Root cause confirmed from the error: The label com.docker.desktop.address=npipe://\\\\.\\pipe\\docker_cli proves Testcontainers is connecting to the CLI proxy pipe (docker_cli) instead of the real engine pipe (docker_engine). The proxy returns empty daemon metadata ("ID":"", "ServerVersion":"") which causes the HTTP 400 and the IllegalStateException.
My project modules:

fos-gateway — contains GatewayRateLimitTest (Testcontainers)
fos-governance-service — contains integration tests (Testcontainers)
fos-sdk — unit tests only, passes fine


What I need you to do, in strict order:
Step 1 — Confirm Docker engine pipe exists
Run in PowerShell:
powershellGet-ChildItem \\.\pipe\ | Where-Object { $_.Name -like "docker*" }
You should see both docker_engine and docker_cli listed. If docker_engine is missing, Docker Desktop is not fully started — tell me to restart Docker Desktop and wait until the system tray whale icon is fully stable, then repeat this step before continuing.
Step 2 — Confirm Docker daemon is healthy via the correct pipe
Run:
powershell$env:DOCKER_HOST = 'npipe:////./pipe/docker_engine'
docker info
Confirm the output has non-empty Server Version, ID, and Containers fields. If still empty or erroring, stop and show me the full output.
Step 3 — Clear all conflicting environment variables
Run each of these in PowerShell to wipe any bad overrides:
powershellRemove-Item Env:\DOCKER_CONTEXT -ErrorAction SilentlyContinue
Remove-Item Env:\TESTCONTAINERS_HOST_OVERRIDE -ErrorAction SilentlyContinue
Remove-Item Env:\TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE -ErrorAction SilentlyContinue
Step 4 — Reset Docker context to default
Run:
powershelldocker context use default
docker context ls
Confirm default has an asterisk * next to it.
Step 5 — Create a Testcontainers config file to hardcode the correct pipe
This permanently fixes the pipe selection for all future Maven runs without needing to set env vars manually every time.
Create the file ~/.testcontainers.properties (i.e. C:\Users\YOUR_USERNAME\.testcontainers.properties) with this exact content:
propertiesdocker.host=npipe:////./pipe/docker_engine
This tells Testcontainers to always use docker_engine and never fall through to docker_cli.
Step 6 — Run the smallest failing test to verify the fix
In the same PowerShell session (with DOCKER_HOST still set from Step 2), run:
powershellmvn -pl fos-gateway -Dtest=GatewayRateLimitTest test
If it passes, continue. If it fails, stop and paste the full stack trace — do not proceed.
Step 7 — Run governance integration tests
powershellmvn -pl fos-governance-service test
Step 8 — Run full gateway suite
powershellmvn -pl fos-gateway test
Step 9 — Run full repo suite
powershellmvn test
Step 10 — Final sanity check
Run:
powershelldocker info
docker-compose --env-file .env.example ps
Confirm Docker info still has healthy server metadata and all Compose services are still healthy.

Hard rules for the agent:

Do not skip or reorder any step.
Do not modify any source code, pom.xml, or docker-compose files.
If any command fails, stop immediately and show the full error output before doing anything else.
The only files you are allowed to create or modify are ~/.testcontainers.properties and PowerShell environment variables in the current session.