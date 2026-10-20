# CLAUDE.md

A web platform that runs [TradingAgents](https://github.com/TauricResearch/TradingAgents) analyses:
a Spring Boot backend and a Vue UI shipped as one jar, and `ta-runner`, the Python process that is
the only code importing TradingAgents. [README.md](README.md) is the user and developer manual;
this file is the map an agent needs before reading code.

## Where things are

| Part | Path | Stack | Structure rules |
|---|---|---|---|
| Backend | `artifact/backend` | Java 25, Spring Boot 4.1, JPA, Flyway, PostgreSQL 18 | [backend-java-package-structure.md](docs/coding-convention/backend-java-package-structure.md) |
| Frontend | `artifact/frontend/src` | Vue 3.5, Vite, Naive UI, Pinia | [frontend-folder-structure.md](docs/coding-convention/frontend-folder-structure.md) |
| Runner | `artifact/ta-runner` | Python 3.12, uv (separate build, not in Gradle) | [ta-runner-python-package-structure.md](docs/coding-convention/ta-runner-python-package-structure.md) |
| End-to-end tests | `e2e/` | Playwright against the built jar, runner replaying a recording | |
| Deployment | `deploy/`, `deploy/server/` | Docker Compose, Ansible | `deploy/server/README.md` |
| Build logic, custom Checkstyle checks | `build-logic/` | Gradle convention plugins | `build-logic/checkstyle-rules/README.md` |
| Agent scripts and prompts | `scripts/agent/`, `.claude/skills/` | Bash, Python | [agentic-development.md](docs/agentic-development.md) |
| Implementation plans | `.plans/<issue>-<slug>.md` | | |

### Backend modules

A modular monolith. Gradle project `:backend:x:y` lives in `artifact/backend/x/y`; packages start
with `tr.girgin.backend.trading.analysis.platform`.

- `bff:api`: controllers, delegate interfaces, web DTOs. No logic.
- `bff:impl`: delegate implementations, DTO ↔ domain mappers (MapStruct), security.
- `orchestration`: use cases that span several domains, one package per feature.
- `domain:<d>:core` / `domain:<d>:adapter`: hexagonal domains `analysis`, `report`, `catalog`,
  `settings`, `identity`. Core holds model, `inbound` use cases, `outbound` ports, package-private
  services; adapter holds one package per port (persistence, runner, eventstore, ...).
- `library:mapper`, `library:persistence`: technical code only, no domain types.

Module dependencies are enforced by the classpath and by ArchUnit (`ArchitectureTest`,
`PersistenceArchitectureTest`). Each domain with persistence has its own schema, Flyway migrations
and a `*PersistenceConventionsTest`.

## Contracts between the parts

These cross language boundaries, so a code search on one side does not find the other side.

- **Backend ↔ ta-runner**: [docs/event-protocol.md](docs/event-protocol.md). The backend writes
  `spec.json`, launches `python -m ta_runner run`, reads JSONL events from stdout. Backend side:
  `domain/analysis/adapter` (`runner`, `eventline`, `eventstore`); runner side: `ta_runner/protocol/`.
  A change to it is a major version bump.
- **Backend ↔ frontend**: OpenAPI. `artifact/frontend/openapi.json` and the generated
  `artifact/frontend/src/shared/api/schema.d.ts` (never edit by hand) come from the running backend
  via `mise run api-types`; commit both. Values shared by domain, BFF and UI (permissions, ...)
  follow [repository-shared-types.md](docs/coding-convention/repository-shared-types.md).
- **Runner ↔ TradingAgents**: only `ta_runner/engine/compat.py` imports upstream.

## Commands

Everything runs through mise (`mise tasks` lists them), which pins Java and uv.

```sh
mise run check          # quick check before pushing: structure, lint, formatting, type-check, dependency declarations
mise run format         # fix Java (Spotless) and frontend/e2e (Prettier) formatting
mise run build          # every Gradle test and the jar; needs a container engine (Testcontainers)
mise run runner-test    # ta-runner: ruff, import-linter, deptry, pytest
mise run test           # build + runner-test
mise run e2e            # Playwright against the jar
mise run dev            # backend :8080 + Vite :5173
./gradlew :backend:domain:analysis:adapter:test --tests '*SomeTest'   # one module's tests
```

On macOS the engine is Podman: set
`DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')`
before Gradle tests, or Testcontainers finds no Docker.

## Rules that the build checks

- Placement and imports: ArchUnit (backend), eslint-plugin-boundaries (frontend), import-linter
  (runner). Read the structure document of the part before adding a class, component or module.
- Checkstyle with project rules `TAP-*` ([backend-java-checkstyle.md](docs/coding-convention/backend-java-checkstyle.md)),
  Palantir Java Format, Prettier.
- Persistence: Spring Data JPA only, no plain SQL ([backend-java-persistence.md](docs/coding-convention/backend-java-persistence.md));
  database naming ([backend-database-naming.md](docs/coding-convention/backend-database-naming.md)).
  Never edit a merged migration (Flyway checksums); add a new one.
- REST: [backend-java-rest-api.md](docs/coding-convention/backend-java-rest-api.md).
- Every module declares exactly the dependencies it uses (Gradle analysis, Knip, deptry).

## Rules that the build does not check

- No comment or Javadoc that restates the code. A comment says why, or what the code cannot show;
  Javadoc only where Checkstyle requires it, one sentence
  ([repository-code-comments.md](docs/coding-convention/repository-code-comments.md)).
- Every pull request into `main` raises the version: `mise run version:bump minor|major|patch`
  ([repository-versioning-and-releases.md](docs/coding-convention/repository-versioning-and-releases.md)).
- Never push to `main` or merge a pull request unless asked. Merge `main` into a branch, never
  rebase or force-push (`/merge-main-into-branch`).
- Don't run the backend against the shared dev database (`trading-analysis-platform-db`) from a
  branch with new migrations; use a throwaway PostgreSQL container.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
