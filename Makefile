# Developer entry points. Gradle needs a JDK 17+ to start (Java 25 is provisioned by it);
# ta-runner needs uv (make setup installs it when missing); the jar needs Java 25. Override any of them:
#   make dev JAVA_HOME=/path/to/jdk17+ UV=/path/to/uv     make run JAVA=/path/to/jdk25/bin/java
GRADLE ?= ./gradlew
# uv: the one on PATH, else a usual install location (the standalone installer, cargo, pip --user on
# macOS), else where `make uv` installs it. Evaluated on each use, so a fresh install is picked up.
UV_CANDIDATES := $(HOME)/.local/bin/uv $(HOME)/.cargo/bin/uv $(wildcard $(HOME)/Library/Python/*/bin/uv)
UV ?= $(or $(shell command -v uv 2>/dev/null),$(firstword $(wildcard $(UV_CANDIDATES))),$(HOME)/.local/bin/uv)
UV_INSTALLER := https://astral.sh/uv/install.sh
JAVA ?= java
JAR := artifact/backend/build/libs/backend-0.0.1-SNAPSHOT.jar

.PHONY: help uv setup dev build test run runner-test clean

help: ## List the targets
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-12s %s\n", $$1, $$2}'

uv: ## Install uv to ~/.local/bin unless it is already there (https://docs.astral.sh/uv/)
	@if command -v "$(UV)" >/dev/null 2>&1; then \
		echo "uv: $(UV)"; \
	else \
		echo "uv not found; installing it to ~/.local/bin with $(UV_INSTALLER)"; \
		curl -LsSf $(UV_INSTALLER) | env UV_NO_MODIFY_PATH=1 sh; \
	fi

setup: uv ## Install uv if missing, ta-runner (TradingAgents + deps) and the frontend's packages
	cd artifact/ta-runner && $(UV) sync
	$(GRADLE) :frontend:pnpmInstall

dev: ## Backend on :8080 and Vite (hot reload) on :5173, together; open http://localhost:5173
	$(GRADLE) --parallel --console=plain :backend:bootRun :frontend:pnpmDev

build: ## Test everything and build the single jar (backend + UI)
	$(GRADLE) build

test: build runner-test ## All tests: backend, frontend, ta-runner

runner-test: uv ## ta-runner tests and lint
	cd artifact/ta-runner && $(UV) run ruff check . && $(UV) run pytest -q

run: ## Run the built jar from the repository root on http://127.0.0.1:8080
	@test -f $(JAR) || $(GRADLE) :backend:bootJar
	$(JAVA) -jar $(JAR)

clean: ## Remove build outputs
	$(GRADLE) clean
