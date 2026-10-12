---
name: renovate-update
description: Run a dependency update of this repository end to end without asking at every step - trigger the Renovate run workflow (renovate-run.yml), tick every mature update on the dependency dashboard, wait for Renovate's pull requests, then fix them one by one with the renovate-fix skill (a red CI fixed on the update branch until it is green) and hand them over for review and merge. Use when the user asks to update the dependencies or run Renovate, e.g. "/renovate-update", "/renovate-update 103" or "update all dependencies".
---

# Renovate update, end to end

The hosted Renovate app ([`.github/renovate.json5`](../../../.github/renovate.json5), README
"Dependency updates") puts every update in one pull request (`renovate/all`), plus one for lock file
maintenance (`renovate/lock-file-maintenance`). This skill drives it to a green pull request: it
asks for a run through the dependency dashboard issue, waits for the pull requests, and hands them
to [`renovate-fix`](../renovate-fix/SKILL.md), which fixes them one by one until CI passes. Every
run starts with the *Renovate run* workflow
([`renovate-run.yml`](../../../.github/workflows/renovate-run.yml)). It runs **without approval
gates** up to a green pull request, with one exception: holding a dependency back, which it presents
and asks about first. It never merges on its own.

Input (`$ARGUMENTS`): nothing (a full run from the dashboard), or the number of an open Renovate pull
request (skip to 3 and only fix that one, the same as `/renovate-fix <pr>`).

The helper [`dashboard.sh`](dashboard.sh) does the parts that must be exact. Run it from the
repository root as `.claude/skills/renovate-update/dashboard.sh <command>`:

| Command | What it does |
|---|---|
| `show` | the dashboard's checkboxes, by section |
| `request [--dry-run]` | tick every checkbox that brings mature updates in, then start `renovate-run.yml` (it ticks "run again") and wait for it |
| `wait [minutes]` | wait until Renovate has processed the ticked checkboxes (default 25, a status line every 30 seconds), then list its pull requests; on a timeout, print what to check on developer.mend.io |
| `prs` | Renovate's open pull requests |
| `wait-ci <pr>` | (used by `renovate-fix`) wait for the CI run on the pull request's head, a status line every 30 seconds (each job's change, jobs done, still running); exit 1 when it failed, with the failed jobs |

Wait actively, so the user sees progress: start `wait` and `wait-ci` with the `Monitor` tool
(command `.claude/skills/renovate-update/dashboard.sh <command> 2>&1`, `timeout_ms` 1800000; re-arm
it if it expires before the command ends). Every line they print reaches you as an event; pass each
one on to the user as a short line (elapsed time, what is pending or running, what changed), and act
on the last one (the pull requests, or CI's conclusion and failed jobs).

## Ground rules

- **Mature only.** `minimumReleaseAge` (14 days) and `internalChecksFilter: "strict"` keep younger
  releases off the branch; the dashboard lists them under "Pending Status Checks". Never tick
  those and never lower `minimumReleaseAge`.
- **Never rebase a branch someone has pushed to.** Once a fix is committed on `renovate/all`,
  Renovate stops updating it; ticking its "rebase" checkbox recreates it from scratch and drops
  the fixes. `request` already leaves it alone; do not tick it by hand either.
- The rules for fixing a red pull request (green by fixing, never by weakening; lock files
  regenerated; never merge, approve or use auto-merge) are in [`renovate-fix`](../renovate-fix/SKILL.md).
- Tools run with mise: `~/.local/bin/mise run <task>` (mise is not on PATH).

## 0. Preflight

```sh
gh auth status
git status --short
.claude/skills/renovate-update/dashboard.sh show
.claude/skills/renovate-update/dashboard.sh prs
```

Stop if `gh` is not logged in. The working tree must be clean, since 3 switches branches: if it is
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

`dashboard.sh wait` with `Monitor`. Renovate unticks every checkbox when it has processed them.
Mend may take a request up within a minute, leave its job pending, or create no job for it at all;
only its job list on developer.mend.io shows which (a job from a request has the reason "requested").
Measured on 2026-10-07: the job stayed pending 11m42s and ran 1m54s, so Renovate edited the dashboard
13m36s after the request. `wait` gives it 25 minutes and prints a status line every 30 seconds:
the elapsed time, the ticked checkboxes, when the run was requested, and Renovate's last edit of the
dashboard (a new edit means its job is running). Requesting again
does not help a pending job.

On a timeout, do not request again: print `wait`'s message to the user and stop. It names the job list
on developer.mend.io and what to do there: with no new job, Mend dropped the request, so tick the
updates on that page (never one under "Edited/Blocked") and press "Create/Rebase"; with a job pending
or running, wait again, and if it stays pending, look for an older job still in progress ahead of it;
with `"mode":"silent"` in the latest job's log, turn silent mode off. The user continues with `/renovate-update` once the run is done.

Afterwards list the open Renovate pull requests. With none, there is nothing to update: say what is
still pending on the dashboard (and when it matures) and stop.

## 3. Fix the pull requests

Invoke the [`renovate-fix`](../renovate-fix/SKILL.md) skill (`Skill`, `renovate-fix`, with the
numbers of the open Renovate pull requests as arguments, `renovate/all` first) and follow it: it
waits for each pull request's CI, fixes a red one on its branch until it is green (holding a
dependency back only with the user's consent), keeps it up to date with `main` and hands it over
for review and merge, one pull request at a time.

## 4. Summary

Switch back to the branch you started on (`git switch <branch>`; `git pull --ff-only` on `main`) and
print:

- the dashboard edit (ticked and left checkboxes) and the Renovate run;
- every Renovate pull request, as `renovate-fix` summarises it: its updates, its CI, the fixes with
  their commits, the held-back dependencies with their issues, merged or waiting for review;
- what is still pending (not yet mature), with the dates it matures;
- the command that continues: `/renovate-fix <pr>`.
