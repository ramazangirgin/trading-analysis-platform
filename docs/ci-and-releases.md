# CI and releases

## Continuous integration

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs on every push to `main`, on every pull
request, and on demand (*Run workflow* on the Actions tab). A branch without a pull request has no
CI until one is opened.

| Job | What |
|---|---|
| Changes | [`scripts/ci-changes.sh`](../scripts/ci-changes.sh) lists the files the change touches (against the merge base with the pull request's base branch) and decides which jobs run; its test is `mise run ci:test-changes`. A change that only touches `docs/`, `.plans/`, `README.md`, `CLAUDE.md`, `.claude/` or `scripts/agent/` skips the build, jar, end-to-end, image and runner-image jobs (the ta-runner job still runs when it touches `docs/`, `README.md`, `CLAUDE.md`, `scripts/agent/` or `.claude/skills/`, for the link check and the agent tests); *Server deploy (Ansible)* runs only when `deploy/`, `artifact/`, `mise.toml` or `.github/` changed; *Server deploy end-to-end* only for the files listed in its row. Any file not on that list counts as an application change. On demand and on `main`, everything runs: the release takes the jar of the run on `main` |
| Backend and frontend | One Gradle run, `./gradlew spotlessCheck :frontend:pnpmLint build`: the formatting of every Java and frontend file first (a formatting failure stops the build), then the build: Spotless, Checkstyle, every Gradle test (ArchUnit, the Docker runner against the runner's own Docker, every repository and Spring Boot test against PostgreSQL in a container (Testcontainers), the database naming and schema checks of every domain (uppercase names, explicit constraint and index names, everything in the domain's own schema, a conventions test in every domain with persistence), the custom Checkstyle checks with their 100% coverage gate and the project rules' fixtures), the dependency analysis (every module declares the libraries it uses and no others), frontend lint, tests and Knip (unused and unlisted packages) |
| Platform jar | `./gradlew :backend:bootJar` in a job of its own, so the platform image and the end-to-end tests start while the build job still runs its tests. A red build still fails **CI passed**, so that jar is never released |
| ta-runner | `mise run runner-test`: ruff, import-linter (package structure), deptry (declared and imported packages match) and pytest, upstream contract tests included; then `mise run agent:test`: the Python tests of the agent scripts ([`scripts/agent/`](../scripts/agent)), which include the link check of the Markdown documents (`scripts/agent/doc_links.py`: every relative link and anchor in `README.md`, `CLAUDE.md` and `docs/` resolves); then `mise run skill:test`: the Python tests of the Claude Code skills' helpers ([`.claude/skills/`](../.claude/skills)) |
| Version | The version is the same in every file; in a pull request into `main`, it is also higher than `main`'s and than the latest release tag, and not yet tagged (Renovate's update pull requests are exempt from the bump) |
| ta-runner image | Built beside the build job, then run under the platform's lockdown flags; the image that passed is handed to the platform image job |
| ta-runner image (arm64) | The same image built on an arm64 runner (`ubuntu-24.04-arm`) and run under the same lockdown flags, so a dependency without arm64 wheels shows up before anyone deploys to an arm64 server |
| Server deploy (Ansible) | `mise run server:check` without a server: `ansible-lint` (production profile) and the playbook's syntax; then `mise run server:test-setup`: the setup scripts against a throwaway Ubuntu 24.04 container ([`deploy/server/`](../deploy/server/README.md#testing)) |
| Server deploy end-to-end | Only when `deploy/`, `artifact/` (the deploy builds and starts the application, so an application change can break it), the CI workflow or `scripts/ci-changes.sh` changed (otherwise it is skipped): `mise run server:test`, with the runner as the server. The setup scripts, a deploy over SSH to `127.0.0.1`, a second deploy with `changed=0`, the UI through the SSH tunnel, only SSH open, a backup, a deploy of another commit that rebuilds, key-only SSH. The playbook builds the images on the runner as on a fresh server, the ta-runner image beside the Gradle build of the jar, then the platform image; no CI cache, so it shows a server's first build. About 6 minutes |
| End-to-end tests | The e2e formatting (Prettier), then the platform jar job's jar, on a throwaway PostgreSQL container, in Google Chrome, in parallel (3 workers; [`e2e/`](../e2e/), Playwright), with login on as the first admin: login, wrong credentials, redirect back, logout and the back button; change password (menu, wrong current password, weak new password); user management (create a user, first login with the one-time password, disable); new analysis, live run page and decision; reports and Markdown export; comparing two runs; settings (keys masked, presets). `ta-runner` replays a recording (`TA_RUNNER_REPLAY`, [`artifact/ta-runner/tests/fixtures/replay-run`](../artifact/ta-runner/tests/fixtures/replay-run)) instead of calling an LLM. Traces are uploaded when a test fails |
| Platform image and Compose smoke test | The platform image (GitHub's build cache) and the ta-runner image from the job above, then [`deploy/smoke-test.sh`](../deploy/smoke-test.sh): the Compose stack comes up, an analysis runs in its own container, and the data survives a database restart and `down`/`up` |

A last job, **CI passed**, succeeds only if every job succeeded or was skipped; a failed or cancelled
job fails it. New jobs go into its `needs` list, so
the rules on `main` never have to change. Those rules (the repository ruleset "main"):

- Changes reach `main` only through pull requests; no direct or force pushes, and `main` cannot be
  deleted.
- A pull request merges once **CI passed** is green, its branch is up to date with `main`, and a
  code owner has approved it ([`.github/CODEOWNERS`](../.github/CODEOWNERS): the admins).
- Admins can merge their own pull requests without an approval, and can merge one whose checks
  failed (GitHub's "bypass" option on the pull request). Nothing merges on its own: auto-merge is off.

Tools come from `mise.toml`, as locally. A newer push to the same pull request cancels the run in progress
(not on main). The smoke test also runs locally: `deploy/smoke-test.sh /absolute/path/for/data` after
`mise run docker-build`.

### Dependency updates

The hosted [Renovate](https://docs.renovatebot.com/) app ([`.github/renovate.json5`](../.github/renovate.json5))
keeps every pinned dependency current: the Gradle version catalog and wrapper, the pnpm packages of
`artifact/frontend/` and `e2e/`, Node.js and pnpm of the build, ta-runner's uv dependencies, the
TradingAgents release, `mise.toml`'s tools, the Dockerfiles, the Compose file and the workflows'
actions. On Monday mornings (Europe/Berlin) it opens **one pull request with every update**, majors
included ("Update all dependencies", branch `renovate/all`), plus one for lock file maintenance. It
only takes releases at least **14 days old**; younger ones wait as "pending" on the dashboard. Java
stays on 25, Python on 3.12, Node.js on LTS majors, and PostgreSQL gets no major updates. Renovate's
dependency dashboard issue lists everything it tracks. Nothing merges automatically: the update pull
request runs the full CI and is merged by an admin like any other. Update pull requests skip the
version bump and ship with the next release (see below).

The schedule also holds for a manual run: to get the update pull request before Monday, tick
"Create all awaiting schedule PRs at once" on the dashboard issue (that starts a run). The *Renovate
run* workflow ([`renovate-run.yml`](../.github/workflows/renovate-run.yml)), from the Actions tab or
`gh workflow run renovate-run.yml`, ticks the dashboard's "run again" checkbox: a run without the
schedule, for example to rebase the pull request after `main` moved.

When the update includes TradingAgents, CI fails until you finish it on the pull request's branch:
run `uv lock` in `artifact/ta-runner/` and commit `uv.lock`, and adapt `ta_runner/engine/compat.py`
and the contract tests to the new release.

The Claude Code skill `renovate-update`
([`.claude/skills/renovate-update/SKILL.md`](../.claude/skills/renovate-update/SKILL.md)) does all of
this from your machine: `/renovate-update` ticks every mature update on the dashboard (never the
pending ones) and requests a run, waits for the pull requests and their CI, and fixes a red CI on the
update branch: it adapts the code to the new release and moves the tools around it along. When that
is not possible yet, it shows you why and asks whether to hold the dependency back (an
`allowedVersions` rule) and open a follow-up issue, which the rule links. It stops at a green pull
request and merges only when you say so. The fixing is a skill of its own, `renovate-fix`
([`.claude/skills/renovate-fix/SKILL.md`](../.claude/skills/renovate-fix/SKILL.md)), which
`renovate-update` uses: `/renovate-fix` takes every open Renovate pull request one by one
(`renovate/all` first), and `/renovate-fix <pr>` only the given ones, without a new Renovate run.

Renovate only moves direct and pinned dependencies, so Dependabot alerts on transitive ones stay
open. The Claude Code skill `dependabot-fix`
([`.claude/skills/dependabot-fix/SKILL.md`](../.claude/skills/dependabot-fix/SKILL.md)) closes them:
`/dependabot-fix` takes all open alerts, `/dependabot-fix maven` (or `pip`, `npm`) one ecosystem, and
`/dependabot-fix 16 17 18` the given alert numbers. It groups the alerts by release family, finds
where each group comes from, picks the newest mature (at least 14 days old), stable version that fixes
every alert of the group and checks it against `renovate.json5`. After you approve the table it pins
the version on a branch, one commit per group, with a comment that names the advisories and says when
the pin can go (the parent release that brings the fix), and opens a pull request that lists the
alerts it closes. Groups that only a pre-release or a major fixes are reported, not applied. For
Gradle, GitHub's alerts come from Automatic Dependency Submission, which resolves **every**
configuration of every project (test fixtures and plugin classpaths included) on each push to
`main`; the skill checks the fix the same way (`alerts.py gradle-scan`) before it commits. The alerts
close once that submission has run on `main` after the merge, and `alerts.py after-merge` then
reports each one as fixed or says why it is still open. The helper's tests run with
`mise run skill:test`.

## Setting up the repository on GitHub

The rules above, and what the workflows and scripts expect from GitHub, are set by
[`scripts/github-setup.sh`](../scripts/github-setup.sh), run by an admin with `gh` logged in. It
creates what is missing and resets what drifted, so it is safe to run again; a new copy of the
repository needs it once:

```sh
mise run github:setup
```

| What | Setting |
|---|---|
| Merging | Merge commits, squash and rebase allowed; auto-merge off; branches are not deleted on merge |
| Ruleset "main" | The rules listed under [Continuous integration](#continuous-integration); the required check is **CI passed** from GitHub Actions, so a check of the same name from elsewhere does not count |
| Actions | The workflows' token is read-only unless a workflow asks for more (`release.yml` does), and cannot approve pull requests |
| Security | Dependabot alerts (for `dependabot-fix`), secret scanning with push protection, private vulnerability reporting |
| Labels | `agent` and `plan` (the agent scripts), and the labels [`.github/release.yml`](../.github/release.yml) sorts the release notes by |
| Environment `production` | Its required reviewer (you), deployments from `main` only, and the values of the `Deploy` workflow when given: `DEPLOY_KEY`, `DEPLOY_HOST`, `ACME_EMAIL`, `PUBLIC_DOMAIN` ([automatic deploys](../deploy/server/README.md#one-time-setup)) |

Two things it cannot do: install the [Renovate app](https://github.com/apps/renovate) on the
repository (once, from the app's page), and name the code owners: [`.github/CODEOWNERS`](../.github/CODEOWNERS)
must name the admins, and the script warns when it does not name you.

## Versioning and releases

The whole repository has one [Semantic Versioning](https://semver.org/) version, `MAJOR.MINOR.PATCH`:
the backend, the frontend and ta-runner always report the same one. It is set in
[`gradle.properties`](../gradle.properties); `artifact/frontend/package.json` and
`artifact/ta-runner/ta_runner/__init__.py` carry copies, which `mise run version:bump` keeps in step
and CI checks. The running app shows it at the bottom of every page and at `/actuator/info`; the
images carry it as a tag and as the `org.opencontainers.image.version` label.

Every pull request into `main` raises the version in its own diff, or the **Version** job fails:

```sh
mise run version:bump minor   # a new feature or any other change
mise run version:bump major   # a breaking change: the REST API, the database without a migration,
                              # configuration, the ta-runner contract or the CLI
mise run version:bump patch   # hotfixes only
```

Renovate's dependency update pull requests are the one exception: they skip the bump (the hosted
app cannot run the script) and ship with the next pull request that raises the version.

The Claude Code skill `pr-ready` ([`.claude/skills/pr-ready/SKILL.md`](../.claude/skills/pr-ready/SKILL.md))
takes a branch to a pull request ready for review: `/pr-ready` (the current branch) or
`/pr-ready <pr>` raises the version when the branch does not, runs `mise run check`, pushes, opens
or updates the pull request, brings it up to date with `main` and fixes a red CI until **CI passed**
is green. It merges only when you say so. `dependabot-fix`, `renovate-fix` and `develop-issue` use it
for their pull requests.

A branch that fell behind `main` with conflicts is brought up to date by the Claude Code skill
`merge-main-into-branch` ([`.claude/skills/merge-main-into-branch/SKILL.md`](../.claude/skills/merge-main-into-branch/SKILL.md)):
`/merge-main-into-branch` merges `origin/main` into the current branch (no rebase, no force push),
commits the merge as it is, conflict markers included, and resolves the conflicts in a second
commit of its own, from the branch's commits, `main`'s commits on the same lines and the branch's
plan; the reviewer sees each resolution with `git show`. It bumps the version again when `main`
caught up, and stops and asks when a conflict cannot be resolved with confidence. The agent loop
and `develop-issue` use its unattended form, `scripts/agent/update-main.sh`. A branch with such a merge commit is merged with
`gh pr merge <pr> --merge`: GitHub cannot rebase-merge it.

Every version raised on `main` is a release. After **CI passed** on `main`,
[`.github/workflows/release.yml`](../.github/workflows/release.yml) tags the commit `vX.Y.Z` and
publishes a [GitHub Release](https://github.com/ramazangirgin/trading-analysis-platform/releases)
with notes generated from the merged pull requests and the jar with its SHA-256 checksum. A new
latest release is then deployed to analystock.app by
[`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) once the reviewer approves it
([automatic deploys](../deploy/server/README.md#automatic-deploys-on-release)). Tags are
created by that workflow only, never by hand. Two pull requests that raise to the same version cannot
both merge: the second one must be updated with `main` and bumped again. The rules and why:
[versioning and releases](coding-convention/repository-versioning-and-releases.md).
