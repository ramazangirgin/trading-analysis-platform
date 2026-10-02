# Coding conventions

How the code base is organised, written down once and checked by tools, so a reviewer does not have
to. Each document describes one topic for one part of the code base, and names the check that
enforces each rule.

| Document | Covers |
|---|---|
| [backend-java-package-structure.md](backend-java-package-structure.md) | Backend (Java): modules, packages, which class kind goes where |
| [frontend-folder-structure.md](frontend-folder-structure.md) | Frontend (Vue): `app` / `pages` / `features` / `shared`, what may import what |
| [ta-runner-python-package-structure.md](ta-runner-python-package-structure.md) | ta-runner (Python): sub-packages, layer order, the single upstream import point |

These documents cover package / folder placement and the dependencies between packages only.
Naming, formatting, error handling and testing conventions will get documents of their own.

## How the conventions are enforced

Every rule is checked in three places, by the same tool:

| Part | Tool | Configured in | Build / local run | Git hook |
|---|---|---|---|---|
| Backend | [ArchUnit](https://www.archunit.org/) | `artifact/backend/src/test/java/.../ArchitectureTest.java` | `./gradlew :backend:test`, part of `mise run build` | pre-push |
| Frontend | [eslint-plugin-boundaries](https://www.jsboundaries.dev/) and `no-restricted-imports` | `artifact/frontend/eslint.config.js` | `pnpm lint`, part of `mise run build` (`:frontend:pnpmLint`) | pre-commit (ESLint), pre-push (type-check) |
| ta-runner | [import-linter](https://import-linter.readthedocs.io/) (`lint-imports`) and ruff `TID` | `artifact/ta-runner/pyproject.toml` | `mise run runner-test` | pre-commit |

- **CI** runs all of them: the *Backend and frontend* job (`mise run build`) and the *ta-runner* job
  (`mise run runner-test`), see [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml). CI is
  the authority: a hook can be skipped, a failing CI check blocks the merge.
- **Git hooks** catch the same failures before a commit or push. They are configured in
  [`lefthook.yml`](../../lefthook.yml) ([lefthook](https://lefthook.dev/), pinned in `mise.toml`; why:
  [OPS-0006](../adr/OPS-0006-git-hooks-with-lefthook.md)).

## Git hooks

Install them once per clone:

```sh
mise run hooks    # or `mise run setup`, which installs them as well
```

| Hook | Runs | On |
|---|---|---|
| pre-commit (in parallel, staged files only) | ESLint + Prettier check; ruff + `lint-imports` | staged files under `artifact/frontend/`; staged files under `artifact/ta-runner/` |
| pre-push | `ArchitectureTest` (compiles the backend, too slow for every commit); the frontend type-check | pushed changes under `artifact/backend/`; under `artifact/frontend/` |

The hooks run lefthook through `mise exec`, so `mise` must be on `PATH` (it is after installing mise
the usual way). The frontend jobs use the Node.js and pnpm the Gradle build downloads, so run
`mise run setup` once before the first commit.

- Run a hook by hand: `mise exec -- lefthook run pre-commit` (staged files) or
  `mise exec -- lefthook run pre-push --all-files`.
- Skip once: `git commit --no-verify` / `git push --no-verify` (CI still checks), or
  `LEFTHOOK=0 git ...`.
- Remove: `mise exec -- lefthook uninstall`.

## Changing a convention

1. Open a pull request that changes the document, the check that enforces it, and the code that
   has to move, together. A rule that is only written down, or only enforced, is not a convention.
2. When the change is a decision with alternatives worth keeping (a new tool, a new layer, giving up
   a rule), record it as an ADR under [`doc/adr/`](../adr/) as well.
3. Before merging, show that the new or changed rule fails on a deliberately misplaced class,
   component or import. ArchUnit passes a rule that selects no class
   (`archRule.failOnEmptyShould=false`), and an ESLint or import-linter pattern that matches
   nothing is silent too, so a wrong pattern would otherwise go unnoticed.
