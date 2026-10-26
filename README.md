# TradingAgents Platform

[![CI](https://github.com/ramazangirgin/trading-analysis-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/ramazangirgin/trading-analysis-platform/actions/workflows/ci.yml)

A web platform for running and managing [TradingAgents](https://github.com/TauricResearch/TradingAgents)
analyses without modifying its code: start an analysis from the browser, watch the agents work live,
then read the decision, the reports and the debates, and export them. The roadmap is the
[GitHub issues](https://github.com/ramazangirgin/trading-analysis-platform/issues);
[docs/event-protocol.md](docs/event-protocol.md) describes the runner contract.

![An analysis: agent pipeline and the final decision](docs/screenshots/run-decision.png)

| Artifact | Path | Stack |
|---|---|---|
| Platform (backend + UI, one jar) | `artifact/backend`, `artifact/frontend` | Java 25, Spring Boot 4.1 · Vue 3.5, Vite, Naive UI |
| Runner (installed into the TradingAgents runtime) | `artifact/ta-runner` | Python 3.12, uv, TradingAgents `v0.5.1` |
| Docker images and Compose setup | `deploy/`, `artifact/ta-runner/Dockerfile` | Temurin 25 JRE · PostgreSQL 18 · docker-socket-proxy |
| Server deployment | `deploy/server/` | Ansible over SSH |
| End-to-end tests (not shipped) | `e2e/` | Playwright, against the built jar |

Two ways to run it: on your machine with `mise run run` ([Quick start](#quick-start)), or with
Docker Compose, where each analysis runs in its own container and the data is kept in PostgreSQL
([Run with Docker Compose](docs/docker-compose.md)).

## Quick start

### 1. Prerequisites

- [mise](https://mise.jdx.dev/) — it installs the rest: Java 25 (Temurin) and
  [uv](https://docs.astral.sh/uv/), pinned in [`mise.toml`](mise.toml), into its own directory.
  Install it with `curl https://mise.run | sh` (or `brew install mise`).
- An API key for at least one LLM provider (OpenAI, Anthropic, Google, DeepSeek, xAI, Mistral,
  Groq, …), or a local [Ollama](https://ollama.com/), which needs no key.
- Docker or Podman (`mise run setup` installs one when there is none: Docker on Ubuntu or Debian,
  Podman on macOS with Homebrew; `PLATFORM_INSTALL_ENGINE=0` skips that). Under `mise run`, a
  running Podman machine's socket is set as `DOCKER_HOST` unless it is set already
  (`scripts/container-env.sh`); a Gradle daemon started before that needs `./gradlew --stop`. `mise run run` and
  `mise run dev` start a local PostgreSQL 18 container
  (`mise run db`; its data is in the volume `trading-analysis-platform-db`; `mise run db:stop` stops
  it, `mise run db:reset` deletes its data). To use your own PostgreSQL 18 server instead, set
  `PLATFORM_DB_HOST`, `PLATFORM_DB_PORT`, `PLATFORM_DB_NAME`, `PLATFORM_DB_USER` and
  `PLATFORM_DB_PASSWORD`; then no container is started if `PLATFORM_DB_HOST` is another host. For a
  server on `localhost` also set `PLATFORM_DB_CONTAINER=false`, else the container is started and
  needs the port free (Docker is still needed for the backend tests).
- Nothing else: Node.js and pnpm are downloaded by the build, Python 3.12 by uv.

### 2. Start the platform

```sh
git clone https://github.com/ramazangirgin/trading-analysis-platform.git
cd trading-analysis-platform
mise trust && mise install   # once: Java 25 and uv for this repository
mise run setup               # once: ta-runner (downloads TradingAgents), the UI packages, a container engine if missing
mise run run                 # builds the app if needed and serves it on :8080
```

Open **http://127.0.0.1:8080**. One process serves both the UI and the API; keep the terminal open
while you use it, and stop it with Ctrl+C. The first start takes a few minutes (Gradle's
dependencies, Node, building the UI and the backend); after that it starts in seconds, and after a
`git pull` it rebuilds only what changed. Analyses, presets and keys live on disk
([Where things live](docs/configuration.md#where-things-live)), so they survive restarts.

If the platform stops while an analysis runs (a crash, a `kill`), the analysis keeps going; the
restarted platform follows it again, or records its result if it finished meanwhile, and starts
queued analyses again. Ctrl+C in the terminal stops the running analyses too.

Changing the code instead? See [Development](docs/development.md) — it runs the UI with hot reload.

Next: [log in, add an API key and start an analysis](docs/user-guide.md#3-log-in).

## Documentation

| File | What it covers | Who reads it |
|---|---|---|
| [docs/user-guide.md](docs/user-guide.md) | Quick start steps 3 to 8: log in and users, API keys, starting an analysis, watching and sharing it, past analyses, reports and export, language | Users |
| [docs/docker-compose.md](docs/docker-compose.md) | Running the platform, PostgreSQL and the analyses as containers, data, security notes, upgrading between major versions | Operators |
| [docs/configuration.md](docs/configuration.md) | Where the platform keeps its data, and the LLM prices | Users, operators |
| [docs/login-and-backend.md](docs/login-and-backend.md) | The session login, password policy, lockout, public routes, users, roles, permissions and the endpoints behind them | Operators, developers |
| [docs/development.md](docs/development.md) | Dev servers, the `mise` tasks, coding conventions, before pushing, ta-runner, testing the Docker runner | Developers |
| [docs/ci-and-releases.md](docs/ci-and-releases.md) | The CI jobs and the rules on `main`, dependency updates, versioning and releases | Developers |
| [docs/agentic-development.md](docs/agentic-development.md) | Taking an issue to a reviewed pull request with agents | Developers |
| [docs/event-protocol.md](docs/event-protocol.md) | The contract between the backend and ta-runner | Developers |
| [docs/coding-convention/README.md](docs/coding-convention/README.md) | Where each kind of class, component or module belongs, and the rules the build checks | Developers |
| [deploy/server/README.md](deploy/server/README.md) | Setting up an Ubuntu server over SSH and deploying the Compose stack on it | Operators |

## Agentic development

An issue can be taken to a reviewed pull request by agents, with you deciding at two points:
approving the plan and merging the pull request. Everything runs on your machine with Claude Code
and your own Claude Code login: Sonnet as the developer agent, Opus as the review agent. Agents
never merge. Each step, what to check at each decision, how to stop, continue or redo a run, and
the settings: [docs/agentic-development.md](docs/agentic-development.md).

## License

Licensed under the [Apache License 2.0](LICENSE). Copyright 2026 Ramazan Girgin.

[TradingAgents](https://github.com/TauricResearch/TradingAgents), which `ta-runner` installs, is
also Apache-2.0-licensed and remains under its authors' copyright.
