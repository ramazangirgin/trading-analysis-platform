# Plan: merge-main-into-branch skill

- **Issue**: #136 (Claude Code skill: merge-main-into-branch, merge main and resolve conflicts with the plan in mind)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

A developer (or `/develop-issue`) brings a branch that fell behind `main` up to date with
`/merge-main-into-branch`: it merges `origin/main` into the current branch in one merge commit,
resolves the conflicts itself from what the branch is meant to do (its commits, `main`'s commits on
the same lines, and the plan when the branch has one), and records the resolution in a second commit
of its own, so a reviewer sees exactly how each conflict was resolved (`git show <resolution
commit>`). It never rewrites history and never force-pushes. When a conflict cannot be resolved with
confidence it stops and names the files and the reason instead of guessing. `/develop-issue` step 6
uses it instead of a rebase, so the run no longer stops at the first conflict outside the version
files. A branch updated this way is merged with a merge commit (`gh pr merge --merge`), since GitHub
cannot rebase-merge it.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Claude Code skills (`.claude/skills/`) | One folder per skill with a `SKILL.md` (front matter `name` and `description` with the trigger phrases, then numbered steps), in the plain style of `pr-ready` and `develop-issue`; ground rules first (never merge, never discard work, commits in the repository's style with the session's attribution line, tools through `~/.local/bin/mise`). No helper script, so no `skill:test` change. |
| Git hooks | docs/coding-convention/repository-git-hooks.md: skipping a hook (`git commit --no-verify`) is allowed, CI stays the authority. The skill skips it for the merge commit only, and says why. |
| Versioning | docs/coding-convention/repository-versioning-and-releases.md: version files are changed only by `scripts/version.sh` (`mise run version:bump`), the bump is a commit of its own, `Bump version to <version>`. |
| Docs | Root `README.md` and `docs/agentic-development.md`, English, plain style. |

## Design

### The skill: `.claude/skills/merge-main-into-branch/SKILL.md`

Input (`$ARGUMENTS`): nothing (the current branch). Options: `--bump major|minor|patch` (the bump
when `main`'s version caught up; default: the plan's "Version bump" when there is a plan, else
`minor`), `--no-push` (commit, do not push: for trying it out on a local branch).

Ground rules: never rebase, squash, reset or force-push a pushed branch; never merge the pull
request; never discard uncommitted work; never merge another branch than `main`; commits in the
repository's style with the session's attribution line.

Steps:

0. **Preflight**: `gh auth status`, `git status --short` (must be empty: name the files and stop),
   `git branch --show-current` (refuse `main` and a detached HEAD). The plan: the branch
   `plan/<n>-<slug>` has `.plans/<n>-<slug>.md`; another branch has none (`git diff --name-only
   origin/main...HEAD -- .plans/` may still name one: use it).
1. **Merge**: `git fetch origin`; `git merge-base --is-ancestor origin/main HEAD` → already up to
   date: say so and stop. Otherwise `git merge --no-ff --no-commit origin/main`.
   - **No conflict**: commit as `Merge main into <branch>`; go to 4.
   - **Conflicts**: the conflicted files are `git diff --name-only --diff-filter=U`. Stage them as
     they are, markers included (`git add <files>`), and commit with `git commit --no-verify -m
     "Merge main into <branch>"` and a body listing the conflicted files. `--no-verify` for this
     commit only: the hook would reject the markers (Prettier, ESLint, ruff, Checkstyle on a
     conflicted file), and the commit is meant to show them. Every later commit runs the hook.
2. **Resolve**, per conflicted file and hunk, with this context:
   - the branch's change: `git log --oneline <base>..HEAD^1 -- <file>` and
     `git diff <base> HEAD^1 -- <file>` (`<base>` = `git merge-base HEAD^1 HEAD^2`);
   - `main`'s change on the same lines: `git log --oneline <base>..HEAD^2 -- <file>` and
     `git diff <base> HEAD^2 -- <file>`, the commit messages read for the why;
   - the plan's work package that names the file, its steps and design, when there is a plan.

   Rule: keep `main`'s updates, and make the branch's planned changes (and its other relevant
   changes) still apply on top of them; adapt the branch's code to `main`'s renames, moved code and
   changed signatures, also outside the conflicted hunks when the merge left them inconsistent (a
   call to a method `main` renamed). The version files (`gradle.properties`,
   `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`): take `main`'s
   side, nothing else.

   **When it cannot be resolved with confidence** (the two sides change the same behaviour in
   incompatible ways and the plan does not decide it): resolve nothing more, commit nothing, list
   each such file with the two changes and why they clash, and ask the developer which way to go.
   Run from `/develop-issue` (no questions after the plan), stop and report instead. The merge
   commit stays local and unpushed; the skill says how to continue (resolve, then step 3) or to drop
   the merge (`git merge --abort` is no longer possible after the commit: `git reset --keep @{u}`,
   the developer's call, never the skill's).
3. **Check and commit the resolution**: no marker left (`git diff --check HEAD`, and
   `git grep -nE '^(<<<<<<<|=======|>>>>>>>)( |$)'` on the conflicted files), `mise run check`,
   plus the tests of the parts the conflicts touched (`mise run build` for the backend,
   `mise run runner-test` for ta-runner). Fix what fails as part of the resolution. Commit as
   `Resolve merge conflicts with main`, through the hook, with a body of one line per file: how it
   was resolved and why (`<file>: kept main's <x>; re-applied <y> from the branch (plan WP2)`).
4. **Version**: `scripts/version.sh check-bump origin/main`; when it fails,
   `mise run version:bump <bump>` and `git commit -am "Bump version to <version>"`, a commit of its
   own (as `pr-ready` 1).
5. **Push**: `git push` (plain, never `--force`; `-u origin HEAD` when the branch has no upstream),
   unless `--no-push`. The merge, resolution and bump commits go in one push, so CI runs on the
   resolved head only, never on a head with markers.
6. **Report**: the commits (sha, subject), per conflicted file one line on how it was resolved, the
   version, and that a branch with a merge commit is merged with `gh pr merge <pr> --merge
   --delete-branch` (GitHub cannot rebase-merge it: the branch's own commits would conflict again).

### `/develop-issue` step 6 and step 8

- Step 6: switch the issue's folder to the pull request's branch (`git switch <branch>`,
  `git merge --ff-only origin/<branch>`), then invoke `merge-main-into-branch` with
  `--bump <the plan's bump>` instead of `pr-ready ... --no-fix`. A conflict it cannot resolve:
  stop and notify (instead of: any conflict outside the version files). After the push, wait for CI
  as in step 5.2; red goes to step 5 (fix-ci) as today. Step 5.3's "run step 6's update instead of
  fix-ci" stays, and no longer mentions `--force-with-lease`.
- Step 8: `gh pr merge <pr> --merge --delete-branch` when the branch has a merge commit
  (`git rev-list --merges origin/main..origin/<branch>` is not empty), `--rebase` otherwise.

### `pr-ready`

Section 4 keeps the rebase (Renovate and Dependabot branches, branches without reviewers yet), but
on a conflict outside the version files it points at `/merge-main-into-branch` instead of only
stopping. Section 6 merges with `--merge` when the branch has a merge commit, as `/develop-issue`
step 8.

## Work packages

The work packages change files under `.claude/` (all but WP3), which the developer agents cannot
write yet (#125). Implemented by hand in the interactive session.

### WP1: The skill

- **Depends on**: none
- **Files**: `.claude/skills/merge-main-into-branch/SKILL.md` (new)
- **Steps**:
  - [ ] Front matter: `name: merge-main-into-branch`, a description with the trigger phrases
        ("/merge-main-into-branch", "merge main into this branch", "update the branch with main",
        "resolve the conflicts with main")
  - [ ] Ground rules, steps 0 to 6 as in the design, with the exact commands and commit messages
  - [ ] The `--no-verify` decision and its reason, and that it applies to the merge commit only
  - [ ] Stopping on an unresolvable conflict: what is listed, what state the branch is left in,
        the `/develop-issue` case (stop, no question)
- **Tests**: a trial on a local throwaway branch (see Tests), with `--no-push`.

### WP2: `/develop-issue` and `pr-ready` use it

- **Depends on**: WP1
- **Files**: `.claude/skills/develop-issue/SKILL.md`, `.claude/skills/pr-ready/SKILL.md`
- **Steps**:
  - [ ] develop-issue step 6 as in the design; the stop list at "One approval: the plan" (a
        conflict the merge skill cannot resolve, instead of a rebase conflict outside the version
        files); step 5.3 wording; step 8's merge method; the description's "update with main"
        stays
  - [ ] pr-ready section 4 (point at the skill on a non-version conflict) and section 6 (merge
        method)
- **Tests**: read-through against the new skill: every reference names a step that exists.

### WP3: Docs and version

- **Depends on**: WP1
- **Files**: `README.md`, `docs/agentic-development.md`, `gradle.properties`,
  `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`
- **Steps**:
  - [ ] README: a paragraph on `merge-main-into-branch` after the `pr-ready` paragraph; the sample
        flow's step 4 merges `main` with the skill and merges the pull request with `--merge`
  - [ ] docs/agentic-development.md: walkthrough step 8 (update with the skill, two commits, no
        force push; the rebase stays an option for a branch without conflicts), step 9 (merge
        method), the `develop-issue` paragraph (the update with `main` resolves conflicts and
        stops only on one it cannot resolve)
  - [ ] `mise run version:bump minor` (3.8.0 → 3.9.0), commit `Bump version to 3.9.0`
- **Tests**: `mise run check`; CI's Version job.

## Tests

No code changes, so no automated tests. The skill is tried once by hand before the pull request:

1. A local branch `trial/136-merge` from an older `main` commit, with a change to a file that a
   later `main` commit changed on the same lines (e.g. a line of `docs/agentic-development.md`), and
   a version bump that conflicts with `main`'s.
2. `/merge-main-into-branch --no-push` on it: two commits (`Merge main into trial/136-merge` with
   markers in the conflicted files, `Resolve merge conflicts with main` without, its body naming each
   file), then `Bump version to …` when `check-bump` asks; `git show` of the resolution commit shows
   only the resolution.
3. Refusals: on `main`, with an uncommitted file, on an up-to-date branch.
4. Delete the trial branch.

`mise run check` and CI (Version job) on the pull request.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | README.md, "Versioning and releases" section after the `pr-ready` paragraph, and the "Agentic development" sample flow | The new skill; merging `main` instead of rebasing; `gh pr merge --merge` for a branch with a merge commit |
| Text | docs/agentic-development.md, "From Claude Code, with one approval: the plan", steps 8 and 9 of the walkthrough | As in WP3 |
| Screenshot | none | No page of the application changes |

## Out of scope

- Rebasing, squashing or any history rewrite; merging other branches than `main` (the issue).
- Letting the developer agents write under `.claude/` (#125).
- Changing `pr-ready` section 4 from a rebase to a merge: Renovate and Dependabot branches stay
  rebased.
