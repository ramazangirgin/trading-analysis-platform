---
name: renovate-fix
description: Fix the open Renovate pull requests of this repository whose CI is red, one by one - wait for each pull request's CI, read why it failed, match the failure to the update that caused it, and fix it on the update branch (adapt the code, bring the tools around it along, and only with the user's consent hold one dependency back) until it is green, keep it up to date with main, then hand it over for review and merge. Use when the user asks to fix the Renovate pull requests or their failing pipelines, e.g. "/renovate-fix", "/renovate-fix 153", "/renovate-fix 153 154" or "the Renovate PRs are failing, fix them". The renovate-update skill uses it after Renovate has opened its pull requests.
---

# Fix the Renovate pull requests

The hosted Renovate app ([`.github/renovate.json5`](../../../.github/renovate.json5), README
"Dependency updates") puts every update in one pull request (`renovate/all`), plus one for lock file
maintenance (`renovate/lock-file-maintenance`). This skill takes those pull requests **one by one**
to a green CI: it reads why CI failed, fixes it on the update branch and waits for CI again. It runs
**without approval gates** up to a green pull request, with one exception: holding a dependency
back, which it presents and asks about first (2.4.4). It never merges on its own (see 4).

It does not ask Renovate for new updates; [`renovate-update`](../renovate-update/SKILL.md) does
that, and then continues with this skill.

Input (`$ARGUMENTS`): nothing (every open Renovate pull request), or the numbers of open Renovate
pull requests (only those, in the given order).

The helper of `renovate-update`, [`dashboard.sh`](../renovate-update/dashboard.sh), does the parts
that must be exact. Run it from the repository root as
`.claude/skills/renovate-update/dashboard.sh <command>`:

| Command | What it does |
|---|---|
| `prs` | Renovate's open pull requests |
| `wait-ci <pr>` | wait for the CI run on the pull request's head, a status line every 30 seconds (each job's change, jobs done, still running); exit 1 when it failed, with the failed jobs |
| `request` | tick the "rebase" checkbox of every branch with only Renovate's commits (and what else brings mature updates in), then start the Renovate run workflow and wait for it |
| `wait [minutes]` | wait until Renovate has processed the ticked checkboxes, then list its pull requests |

Wait actively, so the user sees progress: start `wait-ci` and `wait` with the `Monitor` tool
(command `.claude/skills/renovate-update/dashboard.sh <command> 2>&1`, `timeout_ms` 1800000; re-arm
it if it expires before the command ends). Every line they print reaches you as an event; pass each
one on to the user as a short line (elapsed time, what is pending or running, what changed), and act
on the last one (CI's conclusion and failed jobs, or the pull requests).

## Ground rules

- **One pull request at a time.** Finish one (green, or given up after 3 attempts) before checking
  out the next. `renovate/all` goes first: lock file maintenance regenerates the same lock files
  and is redone by Renovate on top of `main` once the update is merged.
- **Mature only.** `minimumReleaseAge` (14 days) and `internalChecksFilter: "strict"` keep younger
  releases off the branch. Never lower `minimumReleaseAge`, and never pin a dependency to a release
  younger than 14 days (check the release date before choosing a version by hand).
- **Never rebase a branch someone has pushed to** through Renovate. Once the skill commits on
  `renovate/all`, Renovate stops updating it; ticking its "rebase" checkbox recreates it from
  scratch and drops the fixes. `request` already leaves it alone; do not tick it by hand either.
- **Green by fixing, never by weakening.** No skipped or deleted tests, no disabled checks, no
  edits to `.github/workflows/`, no new suppressions to silence a new lint rule's findings (fix
  the code; a justified suppression follows the in-place convention, with a reason), no
  `--no-verify`, no force push of a branch Renovate alone owns.
- **Lock files are regenerated, not hand-edited**, with the tool that owns them:
  `artifact/frontend/with-node.sh pnpm install`, `e2e/with-node.sh pnpm install`,
  `cd artifact/ta-runner && uv lock`.
- **Never merge, approve or use auto-merge** (see 4). The ruleset needs a code owner's review: never
  approve the pull request with the user's login.
- Tools run with mise: `~/.local/bin/mise run <task>` (mise is not on PATH).

## 0. Preflight

```sh
gh auth status
git status --short
.claude/skills/renovate-update/dashboard.sh prs
```

Stop if `gh` is not logged in. The working tree must be clean, since 2 switches branches: if it is
not, say which files are in the way and stop; never stash or discard them. Remember the branch you
started on, to switch back to it at the end.

Take the pull requests from the input, or else every open Renovate pull request, `renovate/all`
first. A number that is not an open pull request by Renovate: say so and leave it out. With none
left, say so and stop. Print the queue, then work through it from 1, one pull request at a time.

## 1. Wait for CI

Start `dashboard.sh wait-ci <pr>` with `Monitor`. Green: go to 4 for it. Red: go to 2. Read the
pull request body too: it lists every update (from → to) with release notes, and Renovate's notes
(the TradingAgents one below).

When the pull request is `BEHIND` main (`gh pr view <pr> --json mergeStateStatus`), see 3 first.
This is likely for the next pull request of the queue once the previous one was merged.

## 2. Fix a red CI (at most 3 attempts per pull request)

1. **Read the failure.** The failed jobs' log:
   `gh run view <run> --log-failed | tail -n 400`. Find which update caused it: match the failing
   module, error and stack trace to the updates in the body, and read that release's notes,
   changelog or migration guide (WebFetch) before changing anything. Tell the user in a few lines
   what failed and which update it points at.
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
      and, with the user's consent, open an issue for it, linked as in the next step.
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
   pull request is `BEHIND`, update it as in 3 first.
7. **Comment on the pull request** with one line per fix: the dependency, what failed, what
   changed (adapted, aligned, held back with its issue), and the commit.
8. Back to 1 for this pull request. After the third red attempt, stop fixing it: comment what was
   tried and what still fails, report it in the summary, and go on with the next pull request.

## 3. Keep it up to date with main

The ruleset needs the branch up to date with `main`.

- **Only Renovate's commits:** let Renovate rebase it. Run `dashboard.sh request` (it ticks the
  branch's "rebase" checkbox and starts the Renovate run), then `dashboard.sh wait` with `Monitor`,
  and go back to 1. On a timeout, print `wait`'s message and stop; the user continues with
  `/renovate-fix <pr>` once Renovate is done.
- **Other commits too** (fixes from 2):

  ```sh
  git fetch origin && git switch <branch> && git merge --ff-only origin/<branch>
  git rebase origin/main
  mise run check
  git push --force-with-lease origin HEAD:<branch>
  ```

  A conflict in `.github/renovate.json5`, a lock file or a version file of a dependency: resolve it
  by keeping `main`'s change and redoing this branch's on top (regenerate lock files). Any other
  conflict: stop and show it. Then wait for CI again (1).

## 4. Hand it over

For a pull request that is green, print what it updates, the fixes made (commits), what was held
back (with the issues). Then ask with `AskUserQuestion`: header `Merge #<pr>`, options
**No, I will merge it myself** (first) and **Merge it now**. Only on **Merge it now**, with
**CI passed** green and the branch up to date:

```sh
gh pr merge <pr> --rebase --delete-branch
```

If GitHub refuses (no code owner's approval yet, CI not green), show why and go on. Never retry
with `--admin`, never `--auto`. Then take the next pull request of the queue (1).

## 5. Summary

Switch back to the branch you started on (`git switch <branch>`; `git pull --ff-only` on `main`) and
print, for every pull request of the queue: its updates, its CI, the fixes with their commits, the
held-back dependencies with their issues, and whether it is merged, waiting for review or given up
(with what still fails). End with the command that continues: `/renovate-fix <pr>`.
