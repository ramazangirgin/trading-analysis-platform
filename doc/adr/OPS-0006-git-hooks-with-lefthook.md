---
status: accepted
date: 2026-10-02
---

# OPS-0006 Git hooks with lefthook, pinned and installed through mise

## Context and Problem Statement

The package / folder structure of the backend, the frontend and ta-runner is enforced by ArchUnit,
ESLint and import-linter ([doc/coding-convention/](../coding-convention/README.md), #51). Until now
these checks ran only in Gradle and in CI, so a misplaced class was found minutes after a push. The
repository should reject it before a commit or push, with hooks every developer gets the same way.

How should the Git hooks be defined, installed and run in a repository that mixes Gradle (Java),
pnpm (Vue) and uv (Python)?

## Decision Drivers

- One hook definition for all three toolchains, checked into the repository.
- No extra runtime: the repository already pins its tools with mise (Java, uv), and Node.js / pnpm
  are downloaded by the Gradle build, not installed globally.
- Fast pre-commit: only staged files, jobs in parallel.
- Installation as part of the existing setup (`mise run setup`).

## Considered Options

- lefthook, pinned in `mise.toml`
- pre-commit (the Python framework)
- husky + lint-staged
- Plain shell scripts in a hooks folder (`core.hooksPath`)

## Decision Outcome

Chosen option: **lefthook, pinned in `mise.toml`**, because it is a single binary that mise already
knows how to install, it runs jobs in parallel on staged files with globs per sub-project, and it
needs neither Node.js nor Python to start.

- `lefthook.yml` at the repository root defines one hook, pre-commit: ESLint, Prettier, ruff and
  import-linter on staged files, a few seconds at most.
- No hook compiles or runs tests. The backend's ArchUnit rules need compiled classes and the
  frontend type-check the whole project, so they run in `mise run check` (a manual quick check
  before pushing, no tests), in the build and in CI. A pre-push hook running them was tried and
  dropped: tens of seconds on every push, from a cold Gradle daemon, for checks CI repeats anyway.
- `mise run hooks` installs them; `mise run setup` calls it. The installed hook runs lefthook
  through `mise exec`, so the tools from `mise.toml` (uv, Java) are on `PATH` in the hooks too.
- CI stays the authority and runs every check itself; hooks can be skipped with `--no-verify`.

### Consequences

- Good, because a misplaced frontend or ta-runner import fails within seconds, before it is
  committed, while commits and pushes stay fast.
- Bad, because a misplaced backend class is caught only by `mise run check`, the build or CI, not
  by a hook.
- Good, because the hook tool's version is pinned and updated like every other tool in `mise.toml`.
- Bad, because the hooks need `mise` on `PATH`; a Git client started without it (some GUI clients)
  cannot run them. Such commits are still checked by CI.
- Bad, because the frontend jobs need the Node.js that the Gradle build downloads
  (`artifact/frontend/with-node.sh` finds it), so `mise run setup` must have run once.
- Neutral, because CI installs lefthook along with the other mise tools without using it.

## Pros and Cons of the Options

### lefthook, pinned in `mise.toml`

- Good, because it is one Go binary, available as a mise tool.
- Good, because jobs run in parallel and can be limited to globs and sub-folders (`root`).
- Good, because `{staged_files}` / `{push_files}` hand only the relevant files to each tool.
- Bad, because it is one more tool to learn (small: one YAML file).

### pre-commit (the Python framework)

- Good, because it is widely used, with many ready-made hooks.
- Bad, because its hooks run in environments it manages itself (its own virtualenvs and Node.js
  installs), next to uv's and Gradle's: a third copy of each toolchain.
- Bad, because hooks for the project's own Gradle and pnpm tasks have to be written as `local` hooks
  anyway.

### husky + lint-staged

- Good, because it is the usual choice in JavaScript projects.
- Bad, because it needs Node.js and an npm package at the repository root, which this repository
  does not have: the frontend's Node.js lives inside its Gradle build.
- Bad, because it covers the Java and Python parts only through shell commands.

### Plain shell scripts (`core.hooksPath`)

- Good, because it needs no tool at all.
- Bad, because staged-file selection, parallel runs and skipping unaffected sub-projects would have
  to be written by hand, and every developer has to set `core.hooksPath` themselves.
