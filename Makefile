# Use Bash for consistent scripting behavior in make recipes.
SHELL := /usr/bin/env bash

# Keep runtime logs and PID files in one local directory.
RUN_DIR := .run

# Mark command-like targets as phony so file names never shadow them.
.PHONY: gateway governance workspace all stop help

# Print help when no explicit target is given.
.DEFAULT_GOAL := help

# Start the Gateway service in the foreground.
gateway:
	@bash ./run-gateway.sh

# Start the Governance service in the foreground.
governance:
	@bash ./run-governance.sh

# Start the Workspace service in the foreground.
workspace:
	@bash ./run-workspace.sh

# Start all services in the background and save logs/PIDs.
all:
	@mkdir -p "$(RUN_DIR)"
	@echo "Starting FOS Governance Service in background (port 8081)..."
	@nohup bash ./run-governance.sh > "$(RUN_DIR)/governance.log" 2>&1 & echo $$! > "$(RUN_DIR)/governance.pid"
	@echo "Starting FOS Workspace Service in background (port 8082)..."
	@nohup bash ./run-workspace.sh > "$(RUN_DIR)/workspace.log" 2>&1 & echo $$! > "$(RUN_DIR)/workspace.pid"
	@echo "Starting FOS Gateway in background (port 8080)..."
	@nohup bash ./run-gateway.sh > "$(RUN_DIR)/gateway.log" 2>&1 & echo $$! > "$(RUN_DIR)/gateway.pid"
	@echo "All services started. Logs: $(RUN_DIR)/*.log"

# Stop all running Spring Boot services started locally.
stop:
	@echo "Stopping services from PID files (if present)..."
	@for pid_file in "$(RUN_DIR)"/*.pid; do \
		if [ -f "$$pid_file" ]; then \
			pid="$$(cat "$$pid_file")"; \
			if kill -0 "$$pid" >/dev/null 2>&1; then \
				echo "Stopping PID $$pid"; \
				kill "$$pid" >/dev/null 2>&1 || true; \
			fi; \
			rm -f "$$pid_file"; \
		fi; \
	done
	@echo "Stopping any remaining Spring Boot processes..."
	@PIDS="$$(pgrep -f 'com\.fos\.gateway\.GatewayApp|com\.fos\.governance\.GovernanceApp|com\.fos\.workspace\.WorkspaceApp|spring-boot:run' || true)"; \
	if [ -n "$$PIDS" ]; then \
		echo "Killing PIDs: $$PIDS"; \
		kill $$PIDS >/dev/null 2>&1 || true; \
	else \
		echo "No remaining Spring Boot processes found."; \
	fi
	@echo "Done."

# Show available local run commands for the team.
help:
	@echo "Local service commands:"
	@echo "  make gateway     Start gateway on port 8080 (foreground)"
	@echo "  make governance  Start governance on port 8081 (foreground)"
	@echo "  make workspace   Start workspace on port 8082 (foreground)"
	@echo "  make all         Start all three services in background"
	@echo "  make stop        Stop all Spring Boot service processes"
	@echo "  make help        Show this help message"
