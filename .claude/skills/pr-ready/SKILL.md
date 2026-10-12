---
name: pr-ready
description: Get a branch of this repository ready for review as a pull request into main - set up a fresh worktree so the Git hooks run, raise the version when the branch does not (every pull request into main must, Renovate's excepted), run the quick check, push, open the pull request (or update the existing one), bring it up to date with main, wait for CI and fix a red run until "CI passed" is green, then hand it over for review without merging. Use when the user asks to open, create, update or finish a pull request, or to get one green or ready, e.g. "/pr-ready", "/pr-ready 155", "open a PR for this" or "why is the PR failing". Other skills use it for their own pull requests (dependabot-fix, renovate-fix).
---

# Pull request, ready for review

`main` is protected (README, [versioning convention](../../../docs/coding-convention/repository-versioning-and-releases.md)):
a pull request, the required check **CI passed** with the branch up to date, and a code owner's
review. **Every pull request into `main` raises the version in its own diff**, a documentation or
skill change too; only Renovate's pull requests (`renovate/*` opened by `renovate[bot]`) are exempt.
This skill takes a branch to that state without asking at every step, and never merges on its own
(see 6).

Input (`$ARGUMENTS`): nothing (the current branch, its pull request if it has one), or the number of
an open pull request. Options, after it: `--bump major|minor|patch` (default `minor`), `--label
<name>` (repeatable, when opening), `--title "<title>"`, `--body-file <path>` (the caller's body
for a new pull request), `--no-fix` (stop at a red CI and report it, for a caller that fixes CI
its own way).

## Ground rules

- **Never merge, approve or use auto-merge** unless the user asks in this session (see 6). The
  author cannot approve their own pull request: say so rather than work around it.
- **Green by fixing, never by weakening.** No skipped or deleted tests, no disabled checks, no
  edits to `.github/workflows/` to get past a check, no `--no-verify`.
- **Never discard work.** Never stash, reset or check out over uncommitted changes; never force push
  without `--force-with-lease`; never force push a branch only Renovate has pushed to.
- **Commits in the repository's style**: an imperative subject, a body that says why, and the
  attribution line the session gives. The version bump is a commit of its own, `Bump version to
  <version>`.
- Tools run with mise: `~/.local/bin/mise run <task>` (mise is not on PATH).

## 0. Preflight

```sh
gh auth status
git status --short
git branch --show-current
```

- `gh` not logged in: stop.
- **Uncommitted changes**: the ones that belong to this change are committed first (one commit per
  topic); anything else in the way: say which files and stop.
- **On `main`** with commits that are not on `origin/main`: move them to a new branch named after
  the change (`git switch -c <slug>`, then `git branch -f main origin/main`); with none, there is
  nothing to open: stop.
- **A pull request number**: work on its head branch. If it is checked out in another worktree,
  work there (`git worktree list`); else switch to it in this one (`git switch <branch>` and
  `git merge --ff-only origin/<branch>`).
- **A worktree without the toolchain** (no `artifact/frontend/node_modules` or no
  `artifact/ta-runner/.venv`): run `mise trust && mise run setup` in it first, or the pre-commit
  hook fails with "Node.js, pnpm or the frontend's packages are missing".

## 1. Version bump

Skip it for Renovate's pull requests. Otherwise:

```sh
git fetch origin
scripts/version.sh check-bump origin/main
```

When it fails ("is not higher than ... on origin/main"), raise it in a commit of its own:

```sh
mise run version:bump <minor|major|patch>
git commit -am "Bump version to <version>"
```

`minor` by default, `major` for a breaking change, `patch` for hotfixes only (the convention's
table). A branch that already raises it, and is still above `main`, needs no second bump.

## 2. Check

`mise run check`, plus the tests of what changed when they need more than the check (`mise run
build` for the backend, `mise run runner-test` for ta-runner, `mise run e2e` for the UI flows).
Fix what fails before pushing; the Git hooks run on commit, fix what they report.

## 3. Push, then open or update the pull request

```sh
git push -u origin HEAD          # --force-with-lease only after a rebase (4)
gh pr view --json number,url,state 2>/dev/null
```

- **No pull request yet**: `gh pr create --base main` with the title (the first commit's subject,
  or `--title`) and a body (`--body-file`, or written here): `## Summary` with one bullet per change
  and why, `## Test plan` with what was run and what CI covers, the issue it closes or is part of
  (`Closes #<n>`, `Part of #<n>`), and the attribution line the session gives. Add `--label` ones.
- **A pull request already**: the push updates it. When the change moved past the body, edit it
  (`gh pr edit <pr> --body-file ...`) so the body still says what the pull request does.

## 4. Up to date with `main`

`gh pr view <pr> --json mergeStateStatus` is `BEHIND` (or `DIRTY`, a conflict):

```sh
git fetch origin
git rebase origin/main
scripts/version.sh check-bump origin/main || { mise run version:bump <bump>; git commit -am "Bump version to <version>"; }
mise run check
git push --force-with-lease
```

Conflicts only in the version files (`gradle.properties`, `artifact/frontend/package.json`,
`artifact/ta-runner/ta_runner/__init__.py`): take `main`'s side, continue the rebase, then bump
again in its own commit. Any other conflict: `git rebase --abort`, show the conflicting files and
stop; never resolve someone else's change on your own.

## 5. CI

Start `.claude/skills/renovate-update/dashboard.sh wait-ci <pr>` with the `Monitor` tool (command
with `2>&1`, `timeout_ms` 1800000; re-arm it if it expires). Pass each status line on to the user
as a short line, and act on the last one.

- **Green**: go to 6.
- **Red** with `--no-fix`: report the failed jobs and their log's essentials, and stop.
- **Red**: read the failed jobs' log (`gh run view <run> --log-failed | tail -n 400`), tell the
  user in a few lines what failed and why, fix it on the branch, run the narrowest local task that
  reproduces it, commit, push, and wait again. The usual ones:

  | Failed | Fix |
  |---|---|
  | Version: "is not higher than" | 1 (the branch never bumped, or `main` caught up: 4) |
  | Version: files out of sync | `mise run version:bump` raised them together; a hand edit missed one: redo it with the script |
  | Formatting (Spotless, Prettier) | `mise run format`, then `mise run format-check` |
  | Checkstyle, ESLint, ruff, dependency analysis | fix the code or the declaration the message names |
  | Tests | reproduce with the module's task, fix the code or the test the change broke |

  At most 3 attempts; then comment on the pull request what was tried and what still fails, and stop.

## 6. Hand over

Print the pull request, its CI (**CI passed** on the head), its merge state and what it still
waits for (`gh pr view <pr> --json mergeStateStatus,reviewDecision`): usually a code owner's
review. When the user opened it, say that their own approval does not count.

Merge only when the user asks in this session, with **CI passed** green and the branch up to date:

```sh
gh pr merge <pr> --rebase --delete-branch
```

If GitHub refuses (no approval, CI not green), show why and stop. Never `--admin`, never `--auto`.
A worktree made for the change can go after the merge: `git worktree remove <dir>` (it refuses one
with changes: then say so and leave it).
