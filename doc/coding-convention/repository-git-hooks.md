# Repository: Git hooks

Git hooks reject a misplaced class, an unformatted file or a lint finding before it is committed,
instead of minutes later in CI. They run only the fast, static checks, and only for the part of the
code base a commit touches. CI stays the authority: it runs every check again, since a hook can be
skipped.

## Decision

The hooks are defined with [lefthook](https://lefthook.dev/), pinned in `mise.toml` like every
other tool, in one file at the repository root: [`lefthook.yml`](../../lefthook.yml).

- One definition covers all three toolchains: Gradle (Java), pnpm (Vue) and uv (Python).
- lefthook is a single binary that mise installs; it needs neither Node.js nor Python to start, and
  the repository has no Node.js at its root (the frontend's Node.js lives inside its Gradle build).
- Jobs run in parallel, each limited to its sub-project by a glob, and get only the staged files.
- There is only a pre-commit hook; no hook runs a test suite or the frontend type-check. Those run
  in `mise run check`, the build and CI.

## Installing

Once per clone:

```sh
mise run hooks    # or `mise run setup`, which installs them as well
```

The installed hook runs lefthook through `mise exec`, so the tools from `mise.toml` (uv, Java) are on
`PATH` inside the hooks too.

## What runs

| Hook | Runs | On |
|---|---|---|
| pre-commit (jobs in parallel) | ESLint + Prettier check on the staged files | staged files under `artifact/frontend/` |
| | ruff on the staged files; `lint-imports` | staged files under `artifact/ta-runner/` |
| | Spotless on the Java files changed since `HEAD`, Checkstyle on every backend module, and `ArchitectureTest` only (no other test), in one Gradle run | staged `.java` files under `artifact/backend/` |

A commit waits only for the parts it touches: docs-only commits run nothing, frontend and ta-runner
commits a few seconds. The backend job compiles the backend first (seconds with a warm Gradle
daemon, about half a minute from a cold one), and Gradle compiles the working tree: an unstaged edit
counts too.

## Using them

- Run the hook by hand: `mise exec -- lefthook run pre-commit` (staged files) or
  `mise exec -- lefthook run pre-commit --all-files`.
- Skip once: `git commit --no-verify` (CI still checks), or `LEFTHOOK=0 git ...`.
- Remove: `mise exec -- lefthook uninstall`.

## Limits

- The hooks need `mise` on `PATH`; a Git client started without it (some GUI clients) cannot run
  them. Such commits are still checked by CI.
- The frontend jobs use the Node.js and pnpm the Gradle build downloads
  (`artifact/frontend/with-node.sh` finds them), so run `mise run setup` once before the first
  commit.
- CI installs lefthook along with the other mise tools but does not use it.
