# Plan: Stop waiting for CI when the pull request conflicts with main

- **Issue**: #241 (Agent loop: stop at once when the pull request conflicts with main instead of waiting for CI)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

While `next.sh` (and so `run.sh`) waits for **CI passed**, it reads the pull request's merge state
before the wait and on every poll. When the pull request conflicts with `main` (GitHub starts no
`pull_request` workflow then, so the wait could only time out), the CI wait ends at once: the loop
runs the update with `main` (`update-main.sh`, recorded as the step `update with main`) and goes on
with CI on the new head. When it was already updated in this round, or the update cannot resolve
the conflict, the loop stops with `stopping: <branch> conflicts with main`, commented on the pull
request like the other stops, so the `develop-issue` Monitor and the notifications fire within a
poll instead of after `AGENT_CI_TIMEOUT` minutes. The `develop-issue` skill no longer re-arms an
expired Monitor blindly: it reads the end of `run.log` and says when the run is still only waiting.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Agent scripts (`scripts/agent/`) | Bash 3.2 compatible (`lib.sh` header: no negative array indices, guarded empty-array expansions); Python helpers use the standard library only and run with `uv run --no-project python` (`py` in `lib.sh`); each helper has a `test_<name>.py` unittest found by `mise run agent:test`; milestones (`milestone`), step progress (`step_begin` / `step_end`) and stops (`stop` in `next.sh`) as the existing steps do them |
| Claude Code skill (`.claude/skills/develop-issue/SKILL.md`) | Headless agents cannot edit `.claude/`: this package is done by hand (it comes back "not done" from the agent loop) |
| Docs | `docs/agentic-development.md` in the plain style of the existing text; `docs/coding-convention/repository-code-comments.md` (a comment only for what the code cannot show); `repository-versioning-and-releases.md` (minor bump) |

## Design

### The merge state

GitHub reports two fields on a pull request (`gh pr view <pr> --json mergeable,mergeStateStatus`):
`mergeable` (`MERGEABLE`, `CONFLICTING`, `UNKNOWN`) and `mergeStateStatus` (`CLEAN`, `BLOCKED`,
`BEHIND`, `DIRTY`, `UNSTABLE`, `HAS_HOOKS`, `UNKNOWN`, `DRAFT`). The agent pull requests are drafts,
so the conflict test does not rely on `mergeStateStatus` alone: a conflict is
`mergeable == CONFLICTING` **or** `mergeStateStatus == DIRTY`. Every other state waits, including:

- `UNKNOWN` (GitHub computes mergeability lazily after a push; the next poll reads it again);
- `BLOCKED`, `UNSTABLE`, `CLEAN`, `HAS_HOOKS`, `DRAFT` (CI runs or has run);
- `BEHIND`: decided to **keep waiting**, against the issue's wish to stop. GitHub does start the
  `pull_request` workflow for a pull request that is only behind (`.github/workflows/ci.yml` runs
  on every `pull_request`; only a conflicting one has no merge ref to run on), so the wait ends by
  itself, and `next.sh` already updates a branch that is behind before each review round and before
  finalising. Stopping on `BEHIND` would stop the loop every time `main` moves. See "Open
  questions".

The decision is a small, tested Python helper, `scripts/agent/merge_state.py`:

```
merge_state.py wait-step <updated>    the merge state JSON on stdin; prints wait, update or stop
```

`<updated>` is `1` when an `update-main` step was already posted in the current round, `0`
otherwise. Output: `wait` (no conflict), `update` (conflict, not updated in this round), `stop`
(conflict, already updated in this round). Missing or unparsable fields count as no conflict
(`wait`), so a GitHub hiccup never stops a run.

### next.sh

- A helper `updated_this_round` with the test `next_step` uses today (an `update-main` marker whose
  round is the current number of reviews: the `awk` over `pr_steps`), now shared by the `review |
  finalise` case and the new one.
- `next_step`: when `planned_step` returns `wait` (CI pending, also the wait for CI on an approved
  head), it reads `gh pr view "$pr" --json mergeable,mergeStateStatus` and passes it to
  `merge_state.py wait-step`:
  - `wait` → `wait` as today;
  - `update` → a new step `update-main-conflict`;
  - `stop` → `stop <branch> conflicts with <base> again after the update with <base> in this round.
    Merge <base> into it by hand (/merge-main-into-branch), then add the agent label and run the
    agent loop again.`
  Because `next_step` runs on every 30-second poll, the state is checked before the wait and on
  every poll.
- The main loop, for `update-main-conflict`:
  - it does not write "CI passed" (the `review | finalise | fix-ci | update-main` reporting block
    stays as it is and does not include the new step); the open CI wait step ends with
    `step_end "conflicts with <base>"`, and a milestone `#<pr>: <branch> conflicts with <base>`
    is written (not filtered by `notify_milestone`, so it reaches ntfy and the mod);
  - the next-step milestone reads `#<pr>: next step: update with main`, as for `update-main`;
  - it runs `"$AGENT_DIR/update-main.sh" "$pr" || stop "<branch> conflicts with <base> and the update
    with <base> could not resolve it (see run.log). The merge stays local: finish it with
    /merge-main-into-branch or drop it with git reset --keep @{u}, then add the agent label and run
    the agent loop again."`. `update-main.sh` already resolves version-only conflicts by script
    (`merge_versions.py`) and any other conflict with a headless agent, bumps the version again,
    pushes and posts its `update-main` marker; its own failure prints `failed: update with main: …`
    first, then `stop` comments on the pull request, removes the `agent` label and prints
    `stopping: …`.
  - After a successful update the loop goes on: the new head is waited for (a new `CI <sha>` step),
    the CI-wait counter starts at 0 again (as after any non-wait step), and an approval of the old
    head carries over as it does today (`approved_head` follows `from=`/`head=` of the marker).
- The header comment's step table gets a line for the new case (CI pending and the pull request
  conflicts with the base: update with main once per round, else stop).
- `run.sh` needs no change: it hands the loop to `next.sh`; only its header comment is checked to
  still be right.

Why the run merges `main` itself for any conflict, not only the version files: this is what the loop
already does before each review round (`update-main.sh` with its agent for non-version conflicts),
so a conflict found during the CI wait is handled the same way, and a conflict the agent cannot
resolve still ends in `stopping:`. See "Open questions" for the stricter alternative.

### develop-issue skill (by hand)

- "The run and its progress", item 2: when the Monitor expires with the run still active, read
  `tail -n 5 <run.log>` before re-arming it; when the last lines are only
  `waiting for CI passed (N of 60 min)`, say so in one line (the pull request, the head, the minutes
  waited) and re-arm; otherwise re-arm silently as today.
- Step 5 "The loop stops": add the conflict stop to the `stopping:` reasons (`<branch> conflicts with
  main`: the loop's own update could not resolve it, or the branch conflicted again in the same
  round): notify and stop; the developer merges by hand (`/merge-main-into-branch`) or drops the
  local merge. The existing sentence on a red *Version* job or a `BEHIND` branch (run step 6's
  update, then start the run again) stays.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Merge state helper

- **Depends on**: none
- **Files**: `scripts/agent/merge_state.py` (new), `scripts/agent/test_merge_state.py` (new)
- **Steps**:
  - [ ] `merge_state.py` with the `wait-step <updated>` command as in the design (module docstring
        with the usage, like `merge_versions.py`; standard library only)
  - [ ] a function the test calls directly (state dict and updated flag → `wait | update | stop`)
- **Tests**: `test_merge_state.py`: `DIRTY` → `update` (not updated) and `stop` (updated);
  `mergeable` `CONFLICTING` with `mergeStateStatus` `UNKNOWN` or `DRAFT` → conflict; `BLOCKED`,
  `BEHIND`, `CLEAN`, `UNSTABLE`, `UNKNOWN` → `wait`; missing fields or empty input → `wait`; the
  command line (`main(argv)` with stdin) prints the same.

### WP2: next.sh: end the CI wait on a conflict

- **Depends on**: WP1
- **Files**: `scripts/agent/next.sh`, `scripts/agent/run.sh` (header comment only, if it needs a word)
- **Steps**:
  - [ ] `updated_this_round` helper, used by the existing `review | finalise` case
  - [ ] in `next_step`, the merge state check when the planned step is `wait`, mapped to `wait`,
        `update-main-conflict` or `stop …`
  - [ ] the main loop's handling of `update-main-conflict` (CI wait step ends with
        `conflicts with <base>`, milestone, `update-main.sh … || stop …`)
  - [ ] header comment: the new case in the step table
  - [ ] `--dry-run` prints the next step (`update with main` or `stopping: …`) without acting, as
        for the other steps
- **Tests**: the decision is covered by WP1's tests; `mise run check` (the agent tests) passes. By
  hand, once: `scripts/agent/next.sh --dry-run <pr>` on an open agent pull request prints
  `waiting for …` while it is mergeable.

### WP3: Docs

- **Depends on**: WP2
- **Files**: `docs/agentic-development.md`
- **Steps**:
  - [ ] "Decision", the bullet "Update with `main` is a step script with a fresh agent": `next.sh`
        also runs it when the pull request conflicts with `main` during a CI wait (GitHub runs no CI
        then), at most once per round
  - [ ] "The flow" table, steps 6 and 9 if they list when the update runs: add the conflict during
        the CI wait
  - [ ] "Progress while it runs" / the milestone list in "4. Start the agents": the milestone
        `<branch> conflicts with main` and that the CI wait step ends with `conflicts with main`
  - [ ] "Progress on the issue": a CI wait step's result can be `conflicts with main`
  - [ ] "5. When a run stops early", "The loop gave up": add the conflict stop (the update could not
        resolve it, or it conflicted again in the same round) and what to do (`/merge-main-into-branch`,
        or drop the local merge with `git reset --keep @{u}`, then the label and `agent:next`)
  - [ ] "From Claude Code, with one approval": the Monitor paragraph says the session reads the end
        of `run.log` when its Monitor expires and says when the run is still only waiting
- **Tests**: `mise run check` (the link check of `doc_links.py` in `agent:test`).

### WP4: develop-issue skill (by hand)

- **Depends on**: WP2
- **Files**: `.claude/skills/develop-issue/SKILL.md`
- **Steps**:
  - [ ] "The run and its progress", item 2: on an expired Monitor, `tail -n 5 <run.log>`, and one
        line when the run is still only waiting for CI, then re-arm
  - [ ] step 5: the conflict stop among the `stopping:` reasons, and what the session does (notify
        and stop)
- **Tests**: `mise run check` (`skill:test` and the link check). The agents cannot edit `.claude/`:
  this package comes back "not done" and is done by the developer before the merge.

The last package the agent implements (WP3) bumps the version: `mise run version:bump minor`.

## Tests

- Unit: `scripts/agent/test_merge_state.py` (run by `mise run agent:test`, part of `mise run check`
  and of CI's ta-runner job) proves the issue's state check: a `DIRTY` (or `CONFLICTING`) pull
  request ends the wait (update, or stop when already updated in the round), a `BLOCKED` one waits.
- No test drives `next.sh` end to end (it needs GitHub); the dry run on a live pull request is the
  manual check.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/agentic-development.md`: "Decision" (update with `main` bullet), "The flow" table, "4. Start the agents" (milestones), "Progress on the issue", "5. When a run stops early", "From Claude Code, with one approval" (Monitor) | as in WP3 |
| Text | `.claude/skills/develop-issue/SKILL.md`: "The run and its progress" item 2, "5. The loop stops" | as in WP4 (by hand) |
| Text | `scripts/agent/next.sh` header comment (the step table) | the conflict case |
| Screenshot | none | the change is in the agent scripts; no page of the app changes |

## Out of scope

- Stopping on `BEHIND` (see Design and "Open questions").
- A shell-level test harness for `next.sh` (faking `gh`): the decision is in the tested helper.
- Checking the merge state inside `implement.sh` or `update-main.sh`: they do not wait for CI.
- The `agent-progress` mod and `progress.py`: a CI wait ending with `conflicts with main` is shown
  as its result as it is; counting it separately in the "iterations" table is not needed.

## Open questions

- **`BEHIND`**: the issue asks to stop the wait on `BEHIND` too. This plan keeps waiting, because
  GitHub runs CI on a pull request that is only behind and the loop already updates it before each
  review and the finalise; stopping would hand the pull request back every time `main` moves.
  Confirm, or should `BEHIND` stop (or update) as well?
- **Non-version conflicts**: the issue's default is that only a version-file conflict is merged by
  the run and any other conflict stops. This plan runs `update-main.sh` for any conflict (version
  files by script, others by its headless agent, stopping when the agent cannot resolve them), as the
  loop already does before each review round. The stricter alternative: list the conflicted files
  first without touching the working tree (`git merge-tree --write-tree --name-only origin/main
  <head>`) and run the update only when all of them are version files (`merge_versions.py
  is-version-file`), stopping otherwise. Which one?
