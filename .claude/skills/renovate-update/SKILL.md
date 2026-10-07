---
name: renovate-update
description: Run a dependency update of this repository end to end without asking at every step - trigger the Renovate run workflow (renovate-run.yml), tick every mature update on the dependency dashboard, wait for Renovate's pull requests and their CI, and fix a red CI on the update branch (adapt the code, bring the tools around it along, and only with the user's consent hold one dependency back) until it is green, then hand the pull request over for review and merge. Use when the user asks to update the dependencies, run Renovate, or fix the Renovate pull request, e.g. "/renovate-update", "/renovate-update 103" or "update all dependencies".
---

# Renovate update, end to end

The hosted Renovate app ([`.github/renovate.json5`](../../../.github/renovate.json5), README
"Dependency updates") puts every update in one pull request (`renovate/all`), plus one for lock file
maintenance (`renovate/lock-file-maintenance`). This skill drives it to a green pull request: it
asks for a run through the dependency dashboard issue, waits for the pull requests, and fixes them
until CI passes. Every run starts with the *Renovate run* workflow
([`renovate-run.yml`](../../../.github/workflows/renovate-run.yml)). It runs **without approval gates** up to a green pull request, with one exception:
holding a dependency back, which it presents and asks about first (4.4). It never merges on its own
(see 6).

Input (`$ARGUMENTS`): nothing (a full run from the dashboard), or the number of an open Renovate pull
request (skip to 3 and only fix that one).

The helper [`dashboard.sh`](dashboard.sh) does the parts that must be exact. Run it from the
repository root as `.claude/skills/renovate-update/dashboard.sh <command>`:

| Command | What it does |
|---|---|
| `show` | the dashboard's checkboxes, by section |
| `request [--dry-run]` | tick every checkbox that brings mature updates in, then start `renovate-run.yml` (it ticks "run again") and wait for it |
| `wait [minutes]` | wait until Renovate has processed the ticked checkboxes (default 60, a progress line every 5), then list its pull requests; on a timeout, print what to check on developer.mend.io |
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
  the fixes. `request` already leaves it alone; do not tick it by hand either.
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

## 1. Trigger the Renovate run workflow

```sh
.claude/skills/renovate-update/dashboard.sh request
```

The run is always requested through `renovate-run.yml` (`gh workflow run renovate-run.yml`), never
by ticking "run again" with the user's login: the app takes up the workflow's edit within a minute,
while an edit made with the user's `gh` login waited for its next scheduled run, an hour or more.

`request` first ticks, in one edit of the dashboard issue, what the run should bring in: "awaiting
schedule" (the Monday schedule also holds a manual run), "rate-limited", "pending approval",
"errored" (retry), and "rebase" of a branch with only Renovate's own commits (so an open update
pull request takes the newer mature releases). A "run again" left ticked by an earlier request is
unticked in the same edit, since the workflow only ticks an unticked one. Then it starts the
workflow, waits for it, and prints each checkbox it ticked or left and the workflow run's link. If
the workflow fails, show its log (`gh run view <run> --log-failed`) and stop.

If a branch was left because
it has other commits and the dashboard shows new mature updates for it, say so in the summary: they
come after this pull request is merged (Renovate then opens a new one).

## 2. Wait for Renovate

`dashboard.sh wait` in the background. Renovate unticks every checkbox when it has processed them.
Mend runs a request from its own queue: within a minute at times, after an hour at others, and
nothing on GitHub shows which; requesting again does not move it up the queue. So `wait` gives it 60
minutes, and prints a progress line every 5 (the ticked checkboxes, Renovate's last edit of the
dashboard).

On a timeout, do not request again: print `wait`'s message to the user and stop. It names the job list
on developer.mend.io and what to do there: wait again while a job is queued or running; with no job
since the request, tick the updates on that page (never one under "Edited/Blocked") and press
"Create/Rebase", which starts a job on Mend's side; with `"mode":"silent"` in the latest job's log,
turn silent mode off. The user continues with `/renovate-update` once the run is done.

Afterwards list the open Renovate pull requests. With none, there is nothing to update: say what is
still pending on the dashboard (and when it matures) and stop.

## 3. Wait for CI

For every open Renovate pull request, start `dashboard.sh wait-ci <pr>` in the background (all of
them at once). Green: go to 6 for it. Red: go to 4. Read the pull request body too: it lists every
update (from → to) with release notes, and Renovate's notes (the TradingAgents one below).

When the pull request is `BEHIND` main (`gh pr view <pr> --json mergeStateStatus`) and has only
Renovate's commits, tick its rebase checkbox with `request` and go back to 2. With other commits, see 5.

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

4. **Fix the integration.** The goal is the new version working, not a green CI at any price: a
   downgrade is the last resort, never the first answer to a failure. Work through these in order,
   and try each one for real (change it, run the reproducing task) before moving on:
   1. **Adapt the code** to the new release, guided by its notes and migration guide: a renamed or
      removed API, a new deprecation turned error, new lint or type-check findings, a formatter's
      new output (`mise run format`), changed defaults in configuration, test expectations that
      follow a documented behaviour change, a migration codemod the project provides. A migration
      that touches many files is still this step, as long as it is mechanical and the tests cover it.
   2. **Bring the neighbours along** when the update does not fit the tools around it (a plugin,
      a type checker, a linter, a BOM, a peer dependency range): look for a mature release of the
      neighbour that supports the new version (its changelog, its `peerDependencies`, its issues)
      and move it too; or move the update to the highest mature version that still fits.
   3. **Configure around it** when the project or the upstream issue documents a supported way: a
      compatibility flag, a configuration option, a compatibility package, the old and new version
      side by side (TypeScript 7's announcement documents `@typescript/typescript6` as `typescript`
      next to TypeScript 7 for tools that need the old API). Read the release announcement and the
      neighbours' upstream issues for it (`gh search issues --repo <owner>/<repo> "<dependency>
      <version>"`): maintainers often point at the supported setup there. No undocumented hacks
      (patched `node_modules`, copied sources, private entry points). A workaround like this leaves
      a follow-up (back to the plain setup once the neighbours catch up): present it in the summary
      and, with the user's consent, open an issue for it, linked as in 4.
   4. **Challenging: ask before holding back.** When none of these works within this run (the
      ecosystem has no support yet, the release is broken upstream, the migration needs design
      decisions or more than a contained change), stop fixing this dependency and present it with
      `AskUserQuestion` (header `Hold back`): the dependency and versions, the failure (a few log
      lines), what you tried in 1–3 and why each did not work, what would unblock it (a release, a
      decision), and the options **Hold back and open a follow-up issue** (first),
      **Hold back without an issue**, and **Keep trying** (say what you would try next). Fix the
      rest of the pull request meanwhile. Only after the answer:
      - add a rule at the end of `packageRules` in `.github/renovate.json5`, so the next runs do not
        bring it back:
        ```json5
        {
          // Held back: <one-line reason>.
          // Upgrade: https://github.com/<owner>/<repo>/issues/<issue> (remove this rule)
          description: "<dependency>: below <version> (#<issue>)",
          matchPackageNames: ["<dependency>"],
          allowedVersions: "<<version>",
        },
        ```
        (`matchDepNames` for the custom managers, as the existing rules do; `matchFileNames` when
        only one part of the repository fails, so the others still take the update);
      - with **Hold back and open a follow-up issue**: open it first, labelled `dependencies`,
        with the version, the failure (log excerpt), what was tried, what unblocks it, and the rule
        to remove when it is done (`gh issue create --label dependencies`). Then link it wherever
        the hold-back leaves a trace: the rule's comment (the issue's full URL) and description
        (`#<issue>`), and every other comment or TODO the fix leaves in the code, so whoever
        upgrades later finds all of them by the issue. Without an issue, the rule's comment says
        what to watch for instead (the release that unblocks it).

      The rest of the update stays on the branch: hold back only what fails, and only where it
      fails.
   - **TradingAgents** in the update: Renovate's note applies (run `uv lock` in
     `artifact/ta-runner/`, adapt `ta_runner/engine/compat.py` and the contract tests to the new
     release); it goes through 1–3 like any other, and only to 4 when the upstream change is too
     large for one pull request.
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
