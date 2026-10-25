# Plan: Agentic flow: update with main as a headless step, smaller work package runs

- **Issue**: #244 (Agentic flow: cut tokens per issue (orchestration, work package size, update with main, quiet tool output, smaller docs))
- **Plan**: 2 of 3 for this issue; depends on: `244-quiet-output-and-stats` (plan 1: both change `scripts/agent/lib.sh`, `scripts/agent/prompts/implement.md`, `mise.toml` and `docs/agentic-development.md`; its `agent:stats` measures this plan's effect)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The update with `main` stops running in the `develop-issue` session's context. A new step script,
`scripts/agent/update-main.sh`, merges `main` into a pull request's branch: conflicts that are only
the version lines are resolved by a script with no agent, other conflicts by a headless agent in a
fresh context, recorded like every other step (progress comment, usage, PR step marker). The review
→ fix loop (`next.sh`) runs it before each review round and before finalising when the branch is
behind, so conflicts stay small and the pull request reaches "ready for review" already up to date.
A work package agent no longer grows one session past 200k: it stops at a turn limit and a fresh
agent continues from the commits; the retries start fresh too, instead of resuming the large
session. `plan-branch.sh` warns about a work package that names more files than the cap. The skills
(`develop-issue`, `merge-main-into-branch`, `plan-from-issue`) follow, by hand (see WP7).

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Agent scripts (`scripts/agent/`) | `docs/agentic-development.md` (one script per step, the pull request is the state: one comment with an `agent-step` marker per step, progress in `progress.tsv` via `step_begin`/`substep_begin`, milestones `agent: >> …`, the lock `run.pid`, scripts push, agents never push); Bash 3.2 compatible; Python helpers standard library only, `unittest` tests in `scripts/agent/test_*.py` (`mise run agent:test`) |
| Agent prompts (`scripts/agent/prompts/`) | the style of the existing prompts; `tools.md` appended by `prompt` |
| mise tasks | every task with its `description`, in the "Agentic development" group |
| Claude Code skills (`.claude/skills/`) | edited by the developer by hand: headless agents may not edit files under `.claude/` (WP7) |
| Docs | `docs/agentic-development.md`, `README.md` "Agentic development" |

## Design

### `scripts/agent/merge_versions.py`: version-only conflicts

`merge_versions.py resolve <file>...` rewrites the conflict hunks of the version files
(`gradle.properties`, `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`;
the list `scripts/version.sh` keeps in `GRADLE`, `FRONTEND`, `RUNNER`) in place. A hunk
(`<<<<<<<` … `=======` … `>>>>>>>`, also with a `|||||||` base section for `diff3`/`zdiff3`) is
resolvable when every line of both sides is that file's version line (`version=…`,
`  "version": "…",`, `__version__ = "…"`); it is replaced by `main`'s side (the `MERGE_HEAD` side,
after `=======`). A file with a hunk that is not resolvable is left unchanged and printed; the exit
status is 1 when any file was left. `merge_versions.py is-version-file <file>` exits 0 for the three
paths. The version is bumped again afterwards (`scripts/version.sh`), as today.

### `scripts/agent/update-main.sh <pr | plan branch> [--bump major|minor|patch]`

A step script like `review.sh`: `use_state`, `lock_run`, `step_begin "update with main"` (the name
`progress.py` already knows, `kind_of` matches `^update`), `use_pr_base` when there is a pull
request. It works on any open pull request of a plan branch (draft or not, any label) and on a plan
branch without one. `--bump` defaults to the plan's "Version bump" line (`- **Version bump**: minor`),
`minor` when it has none.

1. `checkout_branch <branch>`. Stop (`die`) when the local branch is ahead of its upstream with a
   merge of `origin/<base>` that was never pushed (an earlier unresolved run): "finish it by hand
   (`/merge-main-into-branch`) or drop it with `git reset --keep @{u}`".
2. `git merge-base --is-ancestor origin/<base> HEAD`: up to date → milestone `<branch>: up to date
   with <base>`, `step_end "up to date"`, exit 0 (no comment, no push).
3. `git merge --no-ff --no-commit origin/<base>`; the conflicted files from
   `git diff --name-only --diff-filter=U`.
4. **No conflict**: commit `Merge <base> into <branch>` (through the hook, `AGENT_QUIET=1` for its
   output), then `mise run check` (quiet). Passes → step 7. Fails → the agent (step 6) with the
   task "adapt to `<base>`", commit subject `Adapt to <base> after the merge`.
5. **Conflicts**: run `merge_versions.py resolve` on the conflicted version files.
   - Every conflict resolved that way: `git add` them, commit the merge `Merge <base> into <branch>`
     with the body `Version files taken from <base>: <files>` (no markers were ever committed), step 7.
     No agent.
   - Otherwise: stage the conflicted files as they are (markers included; the version files already
     resolved) and commit the merge with `git commit --no-verify` and the body `Conflicts, resolved in
     the next commit:` plus one file per line, as `merge-main-into-branch` does (the script commits,
     not the agent; the agents' `--no-verify` stays denied). Then the agent (step 6).
6. **The agent**: `run_agent "$AGENT_MODEL" … "${AGENT_DEV_TOOLS[@]}" -- --json-schema <schema>`,
   result `update-main-<n>.json` (n = this step's run on the branch, so a later update does not
   overwrite it), `AGENT_STEP_LABEL="update with main"`. Prompt: `prompt update-main.md`, then the
   conflicted files (or the failed check's excerpt), and the plan when the branch has one. Schema:
   `resolved` (bool), `summary` (one line per file: how it was resolved and why), `reason` (why not,
   when `resolved` is false). After it the script checks: `resolved` is true, the working tree is
   clean, HEAD has a commit after the merge commit, and
   `git grep -nE '^(<<<<<<<|=======|>>>>>>>)( |$)'` over the conflicted files finds nothing.
   Anything else → `die "update with main: unresolved conflict in <files>: <reason>"`; nothing is
   pushed and the merge stays local (step 1 tells the next run).
7. **Version**: `scripts/version.sh check-bump origin/<base>` fails → `scripts/version.sh bump
   <bump>`, commit `Bump version to <version>`.
8. `push_branch` (one plain push: the merge, the resolution and the bump together). When there is a
   pull request: `post_step <pr> update-main <reviews so far> <body> <agent results…>` with
   `AGENT_STEP_MARKER="from=<head before> head=<head after>"`; the body says what was merged, the
   conflicts and how each was resolved (the agent's `summary`, or "version files taken from
   `<base>`"), and the version. Milestone `updated with <base>: <n> conflicts resolved
   (<k> by script), version <X.Y.Z>` (the `develop-issue` Monitor waits for it).

`scripts/agent/prompts/update-main.md` carries the rules of `merge-main-into-branch` section 2 and
3, for an agent that commits but never pushes: per file and hunk, read the branch's and `<base>`'s
commits and changes on it (`git log`/`git diff` from the merge base, as the skill lists them) and
the plan's work package naming the file; keep `<base>`'s updates and re-apply the branch's planned
changes on top, also outside the hunks where the merge left the code inconsistent; version files
are resolved already; generated files (`openapi.json`, `schema.d.ts`, lock files) are regenerated
from the resolved sources where the tools allow (`artifact/frontend/with-node.sh pnpm install`),
otherwise resolved so they match the sources, said in `summary`; then `git diff --check`, the marker
grep, `mise run check` and the tests of the parts the merge touched; commit `Resolve merge conflicts
with <base>` (body: one line per file) through the hook. A conflict the commits and the plan do not
decide: resolve nothing more, commit nothing, `resolved: false` with the two changes and why they
clash.

`mise.toml`: task `agent:update-main` (`scripts/agent/update-main.sh`). `detach.sh update-main.sh
<pr>` works as for the other scripts.

### `next.sh`: update before review and finalise

- Before running `review.sh` and before `finalise.sh`: when `origin/<base>` is not an ancestor of the
  pull request's head (after `git fetch origin <base> <branch>`) and no `update-main` marker with
  `round=<reviews so far>` exists yet, run `update-main.sh "$pr"` instead and go back to the loop
  (its push starts CI; the loop waits for it, and a red CI goes to `fix-ci` as usual). At most one
  update per round, so a busy `main` cannot keep the loop merging.
- **The approval survives the update**: `approved_head` (`agent_json.py approved-head`) follows the
  `update-main` markers after the approving review: when one has `from=<approved head>`, its `head`
  is approved instead, repeated along the chain. So an approved head that was only updated with
  `main` goes to finalise once CI passes on the new head, without another review or an empty fix
  round.
- The token limit (`tokens_used`) counts the update's tokens through its marker, like any step.

### `implement.sh`: a fresh agent instead of a long session

- Every work package run gets `--max-turns "$AGENT_WP_MAX_TURNS"` (new setting, default 40; the
  measured packages of #194 ran 25–30 turns at about 2M tokens, the 85-turn one 13.4M). Claude Code
  ends such a run with a result whose `subtype` is `error_max_turns` (the developer agent checks the
  field and `is_error` on a real result and fixture). `run_agent` gets a way to accept that result
  instead of dying (an environment variable `AGENT_ALLOW_MAX_TURNS=1` set by the caller, or an
  option), and returns normally; the caller reads the subtype with `agent_json.py get <result>
  subtype`.
- On a turn limit: milestone `<label>: turn limit reached, continuing in a fresh agent (<k> of
  <AGENT_WP_MAX_CONTINUATIONS>)` (new setting, default 3), and the next run of the same package
  starts **fresh**, never with `--resume`: the prompt is `implement.md`, the package header, a
  section "Continue: an earlier agent stopped at the turn limit" with the package's commits so far
  (`git log --format='%h %s' --grep "^Work-package: <id>$" <plan commit>..HEAD`), `git status
  --short` and `git diff --stat` of the uncommitted work, and the plan (its ticked steps show what is
  done). The existing "Resume: uncommitted work from an interrupted run" text is reused for the
  uncommitted part. Over the continuations: `die` as for an unfinished package. Results:
  `wp-<id>-<k>.json` (the first stays `wp-<id>.json`), all counted in the implement step's usage
  (the loop over `wp-*.json` already picks them up).
- The retry after `package_problems` and the final checks' retries start a fresh agent with the
  problems (the existing "no session" prompt form), instead of `--resume "$session"`, which re-read
  the whole session.
- `implement.md`: commit each finished step of the package as soon as its tests pass (with the
  `Work-package` trailer; the plan's steps ticked in that commit; the `Status` line only with the
  last one), so a cut at the turn limit loses little; a continuation reads the commits and the plan
  instead of the whole code again.

### Work package size

- `plan.py files <plan>`: one line per package, `<id>\t<number of files>`, counted from its
  `- **Files**:` line (and the lines continuing it up to the next `- **` item): every backtick span
  that looks like a path (contains `/` or `.`) counts once. `plan.py oversized <plan> <max>`: the
  packages with more than `<max>` files, `- <id>: <n> files (more than <max>)`, or nothing.
- `AGENT_MAX_WP_FILES` (new setting, default 8, files created or changed including tests).
- `plan-branch.sh` prints the oversized packages as a warning (`agent: WP3 names 12 files (more than
  8): consider splitting it`) and pushes anyway; the planning session shows it in the review.
- `implement.sh --dry-run` shows the file count per package next to its status.

### Docs and the skills

`docs/agentic-development.md` describes the update step, its place in the loop and the size cap;
the skills follow by hand (WP7): `develop-issue` no longer runs `merge-main-into-branch` itself,
`merge-main-into-branch` uses `merge_versions.py` for version-only conflicts, `plan-from-issue`
caps the package size. The orchestration session's model: `develop-issue` runs on Sonnet
(`model: claude-sonnet-5-5` in its frontmatter): after this plan its turns are routine (start the
run, wait on the Monitor, read the end of a log, write the run summary), and the plan writing stays
on Opus through `plan-from-issue`'s subagent.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Version-only conflict resolver

- **Status**: done
- **Depends on**: none
- **Files**: `scripts/agent/merge_versions.py` (new), `scripts/agent/test_merge_versions.py` (new)
- **Steps**:
  - [x] `merge_versions.py resolve` and `is-version-file` as in the design
- **Tests**: `test_merge_versions.py`: a conflicted `gradle.properties`, `package.json` and
  `__init__.py` with version-only hunks (two-way and `diff3` style) come out as `main`'s side; a
  `package.json` whose hunk also holds a dependency line is left unchanged and makes it exit 1; a
  file with two hunks, one resolvable and one not, is left whole; a file that is not a version file
  is refused.

### WP2: The update step script

- **Status**: not done: the by-hand run in a throwaway clone could not be done with the agent's allowed commands (no git init/clone/push); the script is only syntax-checked
- **Depends on**: WP1
- **Files**: `scripts/agent/update-main.sh` (new), `scripts/agent/prompts/update-main.md` (new),
  `mise.toml`
- **Steps**:
  - [x] `update-main.sh` as in the design (usage comment at the top, as the other step scripts)
  - [x] `prompts/update-main.md`
  - [x] task `agent:update-main`
  - [ ] check by hand in a throwaway clone (a local bare repository as `origin`, no GitHub: a plan
    branch without a pull request): up to date; a merge without conflicts; a merge whose only
    conflict is the version lines (resolved with no agent, bumped, pushed)
- **Tests**: `bash -n scripts/agent/update-main.sh`; the resolver's unit tests (WP1); the by-hand
  run above, described in the commit message. The agent path is exercised on the first real
  conflict.

### WP3: Update before review and finalise in the loop

- **Status**: done
- **Depends on**: WP2
- **Files**: `scripts/agent/next.sh`, `scripts/agent/agent_json.py`,
  `scripts/agent/test_agent_json.py`
- **Steps**:
  - [x] `next.sh` runs `update-main.sh` before a review or finalise when behind, once per round
  - [x] `approved-head` follows the `update-main` markers
  - [x] `next.sh --dry-run` prints `update with main` as the next step when it would run it
- **Tests**: `test_agent_json.py`: `approved-head` with an approving review followed by an
  `update-main` marker from that head gives the new head; a chain of two updates; an update from
  another head does not change it; an update before the approving review is ignored; `steps` lists
  the `update-main` marker with its round.

### WP4: Turn limit and fresh continuations in implement.sh

- **Status**: done
- **Depends on**: WP3
- **Files**: `scripts/agent/implement.sh`, `scripts/agent/lib.sh`,
  `scripts/agent/prompts/implement.md`, `scripts/agent/test_agent_json.py`
- **Steps**:
  - [x] `AGENT_WP_MAX_TURNS`, `AGENT_WP_MAX_CONTINUATIONS` in `lib.sh` (defaults, the variables list
    at the top)
  - [x] `run_agent` accepts a max-turns result when asked to
  - [x] `implement.sh`: `--max-turns`, the continuation loop, fresh retries for unfinished packages
    and final checks
  - [x] `implement.md`: commit each finished step
- **Tests**: `test_agent_json.py`: `get <result> subtype` on a max-turns result fixture, and
  `usage-marker` over `wp-WP1.json` plus `wp-WP1-2.json` sums both; `bash -n` on the scripts;
  `implement.sh --dry-run` on a plan branch still prints the packages.

### WP5: Work package size check

- **Status**: done
- **Depends on**: WP4
- **Files**: `scripts/agent/plan.py`, `scripts/agent/test_plan.py`, `scripts/agent/plan-branch.sh`,
  `scripts/agent/implement.sh`, `scripts/agent/lib.sh`
- **Steps**:
  - [x] `plan.py files` and `oversized`; the module docstring lists them
  - [x] `AGENT_MAX_WP_FILES` in `lib.sh`
  - [x] `plan-branch.sh` warns; `implement.sh --dry-run` shows the counts
- **Tests**: `test_plan.py`: a Files line with backticked paths over two lines counts each once; a
  backtick span that is no path (`open`) is not counted; a package without a Files line counts 0;
  `oversized` lists only the packages over the limit.

### WP6: Docs

- **Status**: done
- **Depends on**: WP1, WP2, WP3, WP4, WP5
- **Files**: `docs/agentic-development.md`, `README.md`
- **Steps**:
  - [x] the docs listed under "Docs to update"
  - [x] bump the version (`scripts/version.sh bump minor`), as the last package
- **Tests**: `mise run check`.

### WP7: The skills (by hand, not by the agents)

- **Status**: done (by hand, in the develop-issue session)
- **Depends on**: WP6
- **Files**: `.claude/skills/develop-issue/SKILL.md`, `.claude/skills/merge-main-into-branch/SKILL.md`,
  `.claude/skills/plan-from-issue/SKILL.md`
- **Steps**:
  - [x] `develop-issue`: frontmatter `model: claude-sonnet-5-5`; step 3 (scripts changed since the
    plan branch was cut) runs `scripts/agent/update-main.sh plan/<n>-<slug>` instead of the skill;
    step 5's "Version job / BEHIND" case and step 6 run `scripts/agent/detach.sh update-main.sh <pr>`
    with a Monitor on `>> (failed:|updated with|.*up to date with)`, then the background CI wait as
    today; the "Progress on the issue" table loses the update row (the script records it); the
    session never merges itself and never reads a diff or a whole log (`tail -n 40` only); for step 7
    it reads only the finalise comment (the newest agent comment), not every comment
  - [x] `merge-main-into-branch`: section 2's version files are resolved with
    `scripts/agent/merge_versions.py resolve` first (no reading of their history); a pointer that
    `scripts/agent/update-main.sh` is the unattended form; `develop-issue` no longer invokes it
  - [x] `plan-from-issue` section 3, "Work packages": a package creates or changes at most 8 files
    (tests included) and stays in one module or layer; split a larger one; `plan-branch.sh` warns
    about one over the cap, and the review shows the warning
- **Tests**: none (Markdown read by Claude Code); the first `/develop-issue` run after the merge
  shows the update step in the progress comment as a step of its own.

## Tests

- Unit (`mise run agent:test`, in `mise run check` and CI): `test_merge_versions.py` (new),
  `test_agent_json.py` (approval through updates, max-turns result), `test_plan.py` (file counts).
- By hand: WP2's throwaway-clone run of `update-main.sh`; `implement.sh --dry-run` and
  `next.sh --dry-run` on an existing plan branch.
- The issue's "done when" for this part: update with `main` is a recorded headless step and
  version-file-only conflicts need no agent (WP1–WP3); a work package past the turn limit continues
  in a fresh agent (WP4); plans cap package size (WP5, WP7); `docs/agentic-development.md` is
  updated (WP6). The token comparison after the change is the developer's (plan 1, "Out of scope").

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/agentic-development.md`, "The flow" (diagram and table) | an "Update with main" node before each review round and before finalise, run by `update-main.sh` when the branch is behind; the table's step 10 says the branch is usually up to date already |
| Text | `docs/agentic-development.md`, "Decision" | "One agent run per work package" gains the turn limit and the fresh continuations; a new bullet: the update with `main` is a step script with a fresh agent, version-only conflicts resolved by a script |
| Text | `docs/agentic-development.md`, walkthrough step 3 | the checklist: no package over `AGENT_MAX_WP_FILES` files (the warning of `plan-branch.sh`) |
| Text | `docs/agentic-development.md`, walkthrough step 8 and "From Claude Code, with one approval" | `mise run agent:update-main <pr>` (or `detach.sh update-main.sh <pr>`) for the unattended update; the `develop-issue` session no longer merges in its own context and runs on Sonnet; `/merge-main-into-branch` stays for doing it by hand |
| Text | `docs/agentic-development.md`, "Settings" | `AGENT_WP_MAX_TURNS`, `AGENT_WP_MAX_CONTINUATIONS`, `AGENT_MAX_WP_FILES` |
| Text | `README.md`, "Agentic development" | the update with `main` as part of the agents' loop (one line in the outline) |
| Screenshot | none | no page of the platform changes |

## Out of scope

- A context-size threshold for the work package agents: Claude Code can stop a headless run at a
  number of turns (`--max-turns`) but not at a context size, and a turn limit bounds the context
  well enough (see "Open questions").
- Merging `main` while CI waits or during the implementation (between work packages): the update
  before each review round and before finalising keeps conflicts small without extra CI runs.
- The quiet output and the stats: plan 1. The README split: plan 3 (`244-readme-topic-files`).

## Open questions

None open. Decided in review:

- Turn limit `AGENT_WP_MAX_TURNS` 40, and `AGENT_WP_MAX_CONTINUATIONS` 3 fresh continuations.
- Work package cap `AGENT_MAX_WP_FILES` 8 files (tests included), a warning only, not a refusal.
- `develop-issue` runs on Sonnet (`model: claude-sonnet-5-5`); the plan writing stays on Opus.
