# Coding conventions

How the code base is organised, written down once and checked by tools, so a reviewer does not have
to. Each document describes one topic for one part of the code base, and names the check that
enforces each rule.

| Document | Covers |
|---|---|
| [backend-java-package-structure.md](backend-java-package-structure.md) | Backend (Java): modules, packages, which class kind goes where |
| [frontend-folder-structure.md](frontend-folder-structure.md) | Frontend (Vue): `app` / `pages` / `features` / `shared`, what may import what |
| [ta-runner-python-package-structure.md](ta-runner-python-package-structure.md) | ta-runner (Python): sub-packages, layer order, the single upstream import point |
| [backend-java-checkstyle.md](backend-java-checkstyle.md) | Backend (Java): Checkstyle rules (naming, imports, size, bug-prone patterns, design, Javadoc) and how to suppress a finding |
| [backend-java-formatting.md](backend-java-formatting.md) | Backend (Java): formatting with Spotless and Palantir Java Format, why that formatter, how to fix a finding |
| [repository-versioning-and-releases.md](repository-versioning-and-releases.md) | Whole repository: one version, bumped in every pull request into `main`, tagged and released on merge |

The structure documents cover package / folder placement and the dependencies between packages.
The Checkstyle document covers the backend's naming, imports, size and coding rules; the formatting
document its layout. The frontend is formatted by Prettier (`artifact/frontend/.prettierrc.json`);
ta-runner has no formatter yet. Error handling and testing conventions will get documents of their
own.

## How the conventions are enforced

Every rule is checked in three places, by the same tool:

| Part | Tool | Configured in | Build / local run | Git hook |
|---|---|---|---|---|
| Backend | [ArchUnit](https://www.archunit.org/) | `artifact/backend/src/test/java/.../ArchitectureTest.java` | `./gradlew :backend:test`, part of `mise run build` and `mise run check` | pre-commit, when Java files are staged |
| Backend | [Checkstyle](https://checkstyle.org/) | `config/checkstyle/checkstyle.xml` | `checkstyleMain` / `checkstyleTest`, part of `mise run build` and `mise run check` | pre-commit, when Java files are staged |
| Backend | [Spotless](https://github.com/diffplug/spotless) with [Palantir Java Format](https://github.com/palantir/palantir-java-format) (formatting) | `build-logic/.../tradinganalysisplatform.java-library.gradle.kts` | `spotlessCheck`, part of `mise run build`, `mise run check` and `mise run format-check`; `mise run format` fixes | pre-commit, Java files changed since `HEAD` |
| Frontend | [Prettier](https://prettier.io/) (formatting) | `artifact/frontend/.prettierrc.json` | `pnpm lint`, part of `mise run build`, `mise run check` and `mise run format-check`; `mise run format` fixes | pre-commit, staged files |
| Frontend | [eslint-plugin-boundaries](https://www.jsboundaries.dev/) and `no-restricted-imports` | `artifact/frontend/eslint.config.js` | `pnpm lint`, part of `mise run build` (`:frontend:pnpmLint`) and `mise run check` | pre-commit |
| ta-runner | [import-linter](https://import-linter.readthedocs.io/) (`lint-imports`) and ruff `TID` | `artifact/ta-runner/pyproject.toml` | `mise run runner-test`, `mise run check` | pre-commit |

- **CI** runs all of them: the *Backend and frontend* job (`mise run format-check` for the formatting
  of every file, then `mise run build`) and the *ta-runner* job (`mise run runner-test`), see [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml). CI is
  the authority: a hook can be skipped, a failing CI check blocks the merge.
- **`mise run check`** runs every structure check, lint and the frontend type-check, but no tests:
  the quick check before pushing (seconds when Gradle is warm).
- **Git hooks** run them before a commit, for the staged files' part of the code base. They are configured in
  [`lefthook.yml`](../../lefthook.yml) ([lefthook](https://lefthook.dev/), pinned in `mise.toml`; why:
  [OPS-0006](../adr/OPS-0006-git-hooks-with-lefthook.md)).

## Git hooks

Install them once per clone:

```sh
mise run hooks    # or `mise run setup`, which installs them as well
```

| Hook | Runs | On |
|---|---|---|
| pre-commit (jobs in parallel) | ESLint + Prettier check on the staged files | staged files under `artifact/frontend/` |
| | ruff on the staged files; `lint-imports` | staged files under `artifact/ta-runner/` |
| | Spotless on the Java files changed since `HEAD`, Checkstyle on every backend module, and `ArchitectureTest` only (no other test), in one Gradle run | staged `.java` files under `artifact/backend/` |

A commit waits only for the parts it touches: docs-only commits run nothing, frontend and ta-runner
commits a few seconds. The backend job compiles the backend first (seconds with a warm Gradle
daemon, about half a minute from a cold one), and Gradle compiles the working tree: an unstaged edit
counts too. There is no pre-push hook; the frontend type-check runs in `mise run check`, the build
and CI.

The hooks run lefthook through `mise exec`, so `mise` must be on `PATH` (it is after installing mise
the usual way). The frontend jobs use the Node.js and pnpm the Gradle build downloads, so run
`mise run setup` once before the first commit.

- Run the hook by hand: `mise exec -- lefthook run pre-commit` (staged files) or
  `mise exec -- lefthook run pre-commit --all-files`.
- Skip once: `git commit --no-verify` (CI still checks), or
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
