---
name: renovate-update
description: Run a dependency update of this repository end to end without asking at every step - request a Renovate run, tick every mature update on the dependency dashboard, wait for Renovate's pull requests and their CI, and fix a red CI on the update branch (adapt the code, align conflicting versions, or hold one dependency back) until it is green, then hand the pull request over for review and merge. Use when the user asks to update the dependencies, run Renovate, or fix the Renovate pull request, e.g. "/renovate-update", "/renovate-update 103" or "update all dependencies".
---

# Renovate update, end to end

The hosted Renovate app ([`.github/renovate.json5`](../../../.github/renovate.json5), README
"Dependency updates") puts every update in one pull request (`renovate/all`), plus one for lock file
maintenance (`renovate/lock-file-maintenance`). This skill drives it to a green pull request: it
asks for a run through the dependency dashboard issue, waits for the pull requests, and fixes them
until CI passes. It runs **without approval gates** up to a green pull request. It never merges on
its own (see 6).

Input (`$ARGUMENTS`): nothing (a full run from the dashboard), or the number of an open Renovate pull
request (skip to 3 and only fix that one).

The helper [`dashboard.sh`](dashboard.sh) does the parts that must be exact. Run it from the
repository root as `.claude/skills/renovate-update/dashboard.sh <command>`:

| Command | What it does |
|---|---|
| `show` | the dashboard's checkboxes, by section |
| `tick [--dry-run]` | tick every checkbox that brings mature updates in, plus "run again" |
| `wait [minutes]` | wait until Renovate has processed the ticked checkboxes (default 20), then list its pull requests |
| `prs` | Renovate's open pull requests |
| `wait-ci <pr>` | wait for the CI run on the pull request's head; exit 1 when it failed, with the failed jobs |

Start `wait` and `wait-ci` with the Bash tool's `run_in_background` and do not poll: you are
notified when they end.

## Ground rules

- **Mature only.** `minimumReleaseAge` (14 days) and `internalChecksFilter: "strict"` keep younger
  releases off the branch; the dashboard lists them under "Pending Status Checks". Never tick
  those, never lower `minimumReleaseAge`, and never pin a dependency to a release younger than 14
  days (check the release date before choosing a version by hand).
- **Never rebase a branch someone has pushed to.** Once the skill commits on `renovate/all`,
  Renovate stops updating it; ticking its "rebase" checkbox recreates it from scratch and drops
  the fixes. `tick` already leaves it alone; do not tick it by hand either.
- **Green by fixing, never by weakening.** No skipped or deleted tests, no disabled checks, no
  edits to `.github/workflows/`, no new suppressions to silence a new lint rule's findings (fix
  the code; a justified suppression follows the in-place convention, with a reason), no
  `--no-verify`, no force push of a branch Renovate alone owns.
- **Lock files are regenerated, not hand-edited**, with the tool that owns them:
  `artifact/frontend/with-node.sh pnpm install`, `e2e/with-node.sh pnpm install`,
  `cd artifact/ta-runner && uv lock`.
- **Never merge, approve or use auto-merge** (see 6). The ruleset needs a code owner's review: never
  approve the pull request with the user's login.
- Tools run with mise: `~/.local/bin/mise run <task>` (mise is not on PATH).

## 0. Preflight

```sh
gh auth status
git status --short
.claude/skills/renovate-update/dashboard.sh show
.claude/skills/renovate-update/dashboard.sh prs
```

Stop if `gh` is not logged in. The working tree must be clean, since 4 switches branches: if it is
not, say which files are in the way and stop; never stash or discard them. Remember the branch you
started on, to switch back to it at the end.

Print the dashboard: what is awaiting schedule, open (with its pull request), pending (not yet
mature), errored, rate-limited or ignored. When the input was a pull request, go to 3.

## 1. Request the run

```sh
.claude/skills/renovate-update/dashboard.sh tick
```

It ticks, in one edit of the dashboard issue: "awaiting schedule" (the Monday schedule also holds a
manual run), "rate-limited", "pending approval", "errored" (retry), "rebase" of a branch with only
Renovate's own commits (so an open update pull request takes the newer mature releases), and "run
again". It prints each checkbox it ticked or left.

If it ticked nothing, a run is still pending: go to 2 and wait for it. If a branch was left because
it has other commits and the dashboard shows new mature updates for it, say so in the summary: they
come after this pull request is merged (Renovate then opens a new one).

## 2. Wait for Renovate

`dashboard.sh wait` in the background. Renovate unticks every checkbox when it has processed them,
usually within a few minutes. On a timeout, say so: the run may be queued at Mend, or the repository
may be in silent mode (the job log on developer.mend.io shows `"mode":"silent"`). Then stop.

Afterwards list the open Renovate pull requests. With none, there is nothing to update: say what is
still pending on the dashboard (and when it matures) and stop.

## 3. Wait for CI

For every open Renovate pull request, start `dashboard.sh wait-ci <pr>` in the background (all of
them at once). Green: go to 6 for it. Red: go to 4. Read the pull request body too: it lists every
update (from → to) with release notes, and Renovate's notes (the TradingAgents one below).

When the pull request is `BEHIND` main (`gh pr view <pr> --json mergeStateStatus`) and has only
Renovate's commits, tick its rebase checkbox with `tick` and go back to 2. With other commits, see 5.

## 4. Fix a red CI (at most 3 attempts per pull request)

1. **Read the failure.** The failed jobs' log:
   `gh run view <run> --log-failed | tail -n 400`. Find which update caused it: match the failing
   module, error and stack trace to the updates in the body, and read that release's notes,
   changelog or migration guide (WebFetch) before changing anything.
2. **Check out the branch.**
   ```sh
   git fetch origin
   git switch <branch> 2>/dev/null || git switch -c <branch> --track origin/<branch>
   git merge --ff-only origin/<branch>
   ```
   Renovate force-pushes its branches. If the local branch cannot fast-forward and
   `git log origin/<branch>..<branch>` is empty, recreate it (`git switch main`,
   `git branch -D <branch>`, then the `switch -c` above). If it holds unpushed commits, stop and show
   them.
3. **Reproduce** with the narrowest task for the failed job, before and after the fix:

   | CI job | Locally |
   |---|---|
   | Backend and frontend | `mise run format-check`, then `mise run build` (or the failing Gradle task, e.g. `./gradlew :backend:test --tests '<Test>'`) |
   | ta-runner | `mise run runner-test` |
   | End-to-end tests | `mise run e2e` |
   | Docker images and Compose smoke test | needs Docker; else reason from the log and let CI verify |
   | Version | should not fail here (update pull requests skip the bump): report it, do not bump |

4. **Fix**, choosing the first that applies:
   1. **Adapt the code** when the new release documents the change and the change stays contained:
      a renamed or removed API, a new deprecation turned error, new lint or type-check findings, a
      formatter's new output (`mise run format`), changed defaults in configuration, test
      expectations that follow a documented behaviour change.
   2. **Align conflicting versions** when two updates, or an update and a dependency kept back,
      do not fit each other (a peer dependency range, a Gradle plugin needing a newer Gradle, a BOM
      pinning an older library): move the other one to a compatible version, or take the highest
      mature version of the newer one that fits (`allowedVersions`, see iii).
   3. **Hold the dependency back** when adapting is a real migration (a major release with
      breaking changes across the code base, beyond a contained fix), the release is broken (an open
      upstream regression), or no compatible combination exists:
      - put its version back to `main`'s in every file Renovate changed for it, and regenerate the
        lock files;
      - add a rule at the end of `packageRules` in `.github/renovate.json5`, so the next runs do not
        bring it back:
        ```json5
        {
          // Held back: <one-line reason>. #<issue> tracks the update.
          description: "<dependency>: below <version>",
          matchPackageNames: ["<dependency>"],
          allowedVersions: "<<version>",
        },
        ```
        (`matchDepNames` for the custom managers, as the existing rules do);
      - open an issue for the update, labelled `dependencies`: the version, the failure (log
        excerpt), the release notes or migration guide, and the rule to remove when it is done
        (`gh issue create --label dependencies`).

      The rest of the update stays on the branch: hold back only what fails.
   - **TradingAgents** in the update: Renovate's note applies (run `uv lock` in
     `artifact/ta-runner/`, adapt `ta_runner/engine/compat.py` and the contract tests to the new
     release); hold it back (iii) only when the upstream change is too large for that.
5. **Check and commit.** `mise run check` plus the reproducing task, both green. One commit per
   fix, with an imperative subject naming the dependency, in the repository's style
   (`Adapt to <dependency> <version>: <what changed>`, `Hold back <dependency> at <version>:
   <reason>`), and the body saying why. The Git hooks run on commit; fix what they report.
6. **Push** without force: `git push origin HEAD:<branch>`. If main moved meanwhile and the
   pull request is `BEHIND`, rebase it as in 5 first.
7. **Comment on the pull request** with one line per fix: the dependency, what failed, what
   changed (adapted, aligned, held back with its issue), and the commit.
8. Back to 3 for this pull request. After the third red attempt, stop fixing it: comment what was
   tried and what still fails, and report it in the summary.

## 5. Keep it up to date with main

The ruleset needs the branch up to date with `main`. When it is `BEHIND` and has commits besides
Renovate's:

```sh
git fetch origin && git switch <branch> && git merge --ff-only origin/<branch>
git rebase origin/main
mise run check
git push --force-with-lease origin HEAD:<branch>
```

A conflict in `.github/renovate.json5`, a lock file or a version file of a dependency: resolve it by
keeping `main`'s change and redoing this branch's on top (regenerate lock files). Any other
conflict: stop and show it. Then wait for CI again (3).

## 6. Hand it over

For every pull request that is green, print what it updates, the fixes made (commits), what was held
back (with the issues), and what is still pending on the dashboard. Then ask with
`AskUserQuestion`: header `Merge #<pr>`, options **No, I will merge it myself** (first) and
**Merge it now**. Only on **Merge it now**, with **CI passed** green and the branch up to date:

```sh
gh pr merge <pr> --rebase --delete-branch
```

If GitHub refuses (no code owner's approval yet, CI not green), show why and stop. Never retry with
`--admin`, never `--auto`.

## 7. Summary

Switch back to the branch you started on (`git switch <branch>`; `git pull --ff-only` on `main`) and
print:

- the dashboard edit (ticked and left checkboxes) and the Renovate run;
- every Renovate pull request: its updates, its CI, the fixes with their commits, the held-back
  dependencies with their issues, merged or waiting for review;
- what is still pending (not yet mature), with the dates it matures;
- the command that continues: `/renovate-update <pr>`.
