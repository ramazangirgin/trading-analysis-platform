---
name: merge-main-into-branch
description: Bring the current branch of this repository up to date with main by merging origin/main into it (never a rebase), and resolve the conflicts with what the branch is meant to do - its commits since the merge base, main's commits on the same lines, and its plan (.plans/<issue>-<slug>.md) when it has one. The merge is committed as it is, conflict markers included, and the resolution in a commit of its own, so a reviewer sees how each conflict was resolved; then the version is bumped when main caught up, and everything is pushed in one plain push. It stops and asks instead of guessing when a conflict cannot be resolved with confidence, and never force-pushes, rewrites history or merges the pull request. Use when the user asks to merge main into a branch, update a branch with main or resolve its conflicts with main, e.g. "/merge-main-into-branch", "/merge-main-into-branch --bump minor", "merge main into this branch" or "resolve the conflicts with main". develop-issue uses it to update a pull request with main.
---

# Merge `main` into the branch

A branch that fell behind `main` gets `main` merged into it, and the conflicts resolved here, with
what the branch is meant to do in mind. Unlike a rebase it rewrites nothing: the commits reviewers
already saw stay as they are, and each conflict is solved once, not once per replayed commit.

Two commits, always separate, never combined:

1. `Merge main into <branch>`: the merge as Git leaves it, **conflict markers included**;
2. `Resolve merge conflicts with main`: only the resolution, so `git show <resolution commit>` shows
   exactly how each conflict was resolved, and its body says why.

Then `Bump version to <version>` when `main`'s version caught up with the branch's, and one push.

Input (`$ARGUMENTS`): nothing (the current branch). Options:

- `--bump major|minor|patch`: the bump when `main`'s version caught up (default: the plan's
  "Version bump" when the branch has a plan, `minor` otherwise);
- `--no-push`: commit, but do not push (for trying it out on a local branch);
- `--no-questions`: never ask; stop and report instead (for a caller that runs unattended,
  `develop-issue`).

## Ground rules

- **Never rewrite history.** No rebase, squash, amend of a pushed commit, `reset` or force push;
  only `main` is merged in, never another branch.
- **Never merge the pull request**, approve it or turn on auto-merge.
- **Never discard work.** Never stash, reset or check out over uncommitted changes.
- **Never guess.** A conflict that the branch's commits, `main`'s commits and the plan do not decide
  is the developer's (see 2).
- **Commits in the repository's style**: an imperative subject, a body that says why, and the
  attribution line the session gives.
- Tools run with mise: `~/.local/bin/mise run <task>` (mise is not on PATH).

## 0. Preflight

```sh
gh auth status
git status --short
git branch --show-current
```

- **Uncommitted changes**: say which files and stop.
- **On `main`**, or on no branch (detached HEAD): stop; there is nothing to merge `main` into.
- **The plan**: a branch `plan/<n>-<slug>` has `.plans/<n>-<slug>.md`. Another branch may still
  carry one: `git diff --name-only origin/main...HEAD -- .plans/`. No plan is fine; the commits
  then say what the branch is meant to do.

## 1. Merge

```sh
git fetch origin
git merge-base --is-ancestor origin/main HEAD && echo "up to date"
```

Up to date: say so and stop. Otherwise:

```sh
git merge --no-ff --no-commit origin/main
git diff --name-only --diff-filter=U        # the conflicted files
```

- **No conflict**: `git commit -m "Merge main into <branch>"`, then go to 3 (the check) and 4.
- **Conflicts**: stage the conflicted files as they are, markers included, and commit the merge:

  ```sh
  git add <conflicted files>
  git commit --no-verify -m "Merge main into <branch>" -m "Conflicts, resolved in the next commit:
  <one conflicted file per line>"
  ```

  `--no-verify` skips the pre-commit hook for **this commit only**: the hook would reject the
  markers (Prettier, ESLint, ruff, Checkstyle on a conflicted file), and this commit is meant to
  show them. CI never runs on it (see 5). Every later commit goes through the hook.

## 2. Resolve

Take the files one by one, and in each file every conflicted hunk, with this context:

```sh
base=$(git merge-base HEAD^1 HEAD^2)
git log --format='%h %s%n%b' $base..HEAD^1 -- <file>    # the branch's commits on the file
git diff $base HEAD^1 -- <file>                         # the branch's change
git log --format='%h %s%n%b' $base..HEAD^2 -- <file>    # main's commits on the file, and why
git diff $base HEAD^2 -- <file>                         # main's change
```

and, when there is a plan, its design and the work package that names the file.

- **The rule**: keep `main`'s updates, and make the branch's planned changes (and its other changes
  that matter) still apply on top of them. Adapt the branch's code to what `main` renamed, moved or
  changed, also outside the conflicted hunks when the merge left them inconsistent (a call to a
  method `main` renamed, an import of a moved class, a test of changed behaviour).
- **The version files** (`gradle.properties`, `artifact/frontend/package.json`,
  `artifact/ta-runner/ta_runner/__init__.py`): take `main`'s side and nothing else; 4 bumps again.
- **Generated files** (`openapi.json`, the frontend's API types, lock files): regenerate them from
  the resolved sources (`mise run api-types`, the package manager), never merge them by hand.

**When a conflict cannot be resolved with confidence** (the two sides change the same behaviour in
incompatible ways, and the plan does not say which wins): resolve nothing more and commit nothing.
List each such file with the two changes and why they clash, and ask the developer which way to go;
with `--no-questions`, stop and report it instead. The merge commit stays local and unpushed, the
other files' resolutions stay in the working tree. Say how to go on: resolve the rest and continue
with 3, or drop the merge with `git reset --keep @{u}` (the merge commit is not pushed, so this
rewrites nothing others have). Dropping it is the developer's call, never this skill's.

## 3. Check and commit the resolution

```sh
git diff --check HEAD
git grep -nE '^(<<<<<<<|=======|>>>>>>>)( |$)' -- <conflicted files>
~/.local/bin/mise run check
```

Neither of the first two may print anything. Run the tests of the parts the merge touched as well
when the check does not cover them (`mise run build` for the backend, `mise run runner-test` for
ta-runner). A failure that the merge caused is part of the resolution: fix it, then commit through
the hook:

```sh
git add <resolved files>
git commit -m "Resolve merge conflicts with main" -m "<one line per file>"
```

One body line per file, how it was resolved and why:
`<file>: kept main's <change>; re-applied <the branch's change> on top (plan WP2)`.

A merge without conflicts that fails the check gets the same commit, its subject
`Adapt to main after the merge`.

## 4. Version

```sh
scripts/version.sh check-bump origin/main
```

When it fails ("is not higher than ... on origin/main"):

```sh
~/.local/bin/mise run version:bump <bump>
git commit -am "Bump version to <version>"
```

## 5. Push

```sh
git push                     # git push -u origin HEAD when the branch has no upstream yet
```

A plain push, never `--force` or `--force-with-lease`: the branch only grew. The merge, the
resolution and the bump go in one push, so CI runs on the resolved head only, never on a head with
conflict markers. Skip it with `--no-push`.

## 6. Report

- the commits (short sha and subject);
- one line per conflicted file: how it was resolved;
- the version, and whether it was bumped;
- that a branch with a merge commit is merged with a merge commit:
  `gh pr merge <pr> --merge --delete-branch`. GitHub cannot rebase-merge it: replaying the branch's
  own commits on `main` would run into the same conflicts again, without the resolution.
