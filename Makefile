# Developer entry points. Gradle needs a JDK 17+ to start (Java 25 is provisioned by it);
# ta-runner needs uv; the jar needs Java 25. Override any of them:
#   make dev JAVA_HOME=/path/to/jdk17+ UV=/path/to/uv     make run JAVA=/path/to/jdk25/bin/java
GRADLE ?= ./gradlew
UV ?= uv
JAVA ?= java
JAR := artifact/backend/build/libs/backend-0.0.1-SNAPSHOT.jar

.PHONY: help setup dev build test run runner-test clean

help: ## List the targets
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-12s %s\n", $$1, $$2}'

setup: ## Install ta-runner (TradingAgents + deps) and the frontend's packages
	cd artifact/ta-runner && $(UV) sync
	$(GRADLE) :frontend:pnpmInstall

dev: ## Backend on :8080 and Vite (hot reload) on :5173, together; open http://localhost:5173
	$(GRADLE) --parallel --console=plain :backend:bootRun :frontend:pnpmDev

build: ## Test everything and build the single jar (backend + UI)
	$(GRADLE) build

test: build runner-test ## All tests: backend, frontend, ta-runner

runner-test: ## ta-runner tests and lint
	cd artifact/ta-runner && $(UV) run ruff check . && $(UV) run pytest -q

run: ## Run the built jar from the repository root on http://127.0.0.1:8080
	@test -f $(JAR) || $(GRADLE) :backend:bootJar
	$(JAVA) -jar $(JAR)

clean: ## Remove build outputs
	$(GRADLE) clean
