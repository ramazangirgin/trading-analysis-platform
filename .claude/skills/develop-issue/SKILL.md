---
name: develop-issue
description: Take a GitHub issue of this repository through the whole agentic development flow (a folder of its own checked out from main, plan, plan branch and draft pull request reviewed on GitHub or in the session, developer agent, CI, review → fix rounds, finalise, update with main) in one run. It asks the developer only to approve the plan (and, when a plan branch exists already, whether to use that plan instead of writing one), runs every other step without asking, keeps a progress comment on the issue (each step's start, end, duration, tokens and result), and sends a notification when the pull request is ready for the developer's review. It never touches the current checkout and never merges unless asked. Use when the user asks to develop, implement or run the agents on an issue end to end, e.g. "/develop-issue 61", "/develop-issue https://github.com/<owner>/<repo>/issues/61" or "develop issue #61".
---

# Develop an issue

The whole [agentic development flow](../../../docs/agentic-development.md) for one issue, driven
from this Claude Code session. The developer approves the plan; every later step runs without
asking, and the run ends with a notification that the pull request is ready for the developer's
review. It adds no new behaviour of its own beyond a run summary comment on the pull request
(step 7): planning is the
[`plan-from-issue`](../plan-from-issue/SKILL.md) skill, invoked from here, and every other step runs
one of the scripts in [`scripts/agent/`](../../../scripts/agent) that `mise run agent:run` would run
in one go. So the result is the same pull request, with the same state comments, and
`mise run agent:next` can take over at any point.

Input (`$ARGUMENTS`): an issue number (`61`, `#61`) or an issue link
(`https://github.com/<owner>/<repo>/issues/61`). Ask for it if none was given. A link to another
repository than this one (`gh repo view --json nameWithOwner`) is an error: stop and say so.

## One approval: the plan

The plan (step 2) is the only step that waits for the developer: it is pushed as a draft pull
request labelled `plan`, which the developer reviews there or in this session. When a plan branch
exists already, the developer is first asked whether to use that plan, skipping the writing, or to
write it again; either way the approval follows. Everything after the
approval (implementing, CI fixes, reviews, fix rounds, finalising, the update with `main`) runs
without asking. Before each step, say in one or two lines what it does and the command, then run
it; after it, report what it did.

The run stops without asking, and notifies the developer (see [Notifications](#notifications)),
when:

- a step fails: show the error and the end of its output. Never retry on your own;
- the loop stops (`stopping: <reason>`, step 5);
- the merge of `main` has a conflict that cannot be resolved with confidence (step 6);
- the pull request is ready for review (step 7).

Merging is never part of the run. It is the developer's decision; merge only when the developer asks
for it in this session (step 8).

## The run and its progress

After the approval the whole loop is one script, `scripts/agent/run.sh`, which implements the
plan and runs the CI waits and the review → fix rounds to the pull request being ready for review
or to a stop. This session starts it once and does not drive its steps: every model turn here
re-reads the whole context, so it is woken only when it has something to do.

1. **Start it detached**, in the issue's folder: `scripts/agent/detach.sh run.sh plan/<n>-<slug>`.
   It survives this session, resumes where it stopped when started again, and refuses a second run
   on the same branch. It prints the pid and `.git/agent/<n>-<slug>/run.log`; say
   `tail -f <run.log>` once, for the developer.
2. **Arm one Monitor** (`timeout_ms` 3600000; re-arm it only if it expires with the run still
   active) on
   `cd <dir> && scripts/agent/status.sh --follow plan/<n>-<slug> | grep --line-buffered -E '>> (failed:|stopping:|#[0-9]+ is ready for review)'`.
   Nothing else wakes the session: no per-milestone monitor, no polling of CI, no `sleep` loops.
3. **The developer sees every milestone without this session**: the `agent-progress` mod (status
   line, pane and a toast per milestone). Say how to turn them on once, after the start (see
   [the walkthrough](../../../docs/agentic-development.md#progress-while-it-runs)). Do not repeat
   the milestones in the session.
4. **When the Monitor reports a line**, stop it (`TaskStop`) and read only the end of the log:
   `tail -n 40 <run.log>`, never a whole output file or log. `ready for review` goes on with
   step 6; `failed:` and `stopping:` end the run (see below).
5. **On a failure** (`>> failed: …`), show `tail -n 40` of `run.log`. Each agent's full stream is
   `.git/agent/<n>-<slug>/<step>.jsonl` (`wp-WP1.jsonl`, `review-1.jsonl`, `fix-R1-2.jsonl`,
   `fix-ci-1.jsonl`); read its end only when asked.

When the developer asks for the status, run `cd <dir> && scripts/agent/status.sh <pr | plan branch>`
(`mise run agent:status`) and summarise it. Do not poll otherwise.

## Progress on the issue

The issue keeps one progress comment per plan branch (see
[the walkthrough](../../../docs/agentic-development.md#progress-on-the-issue)): every step with its
start, end, duration, tokens and result, the iterations, and the slow, repeated or failed steps. The
step scripts record their own steps and update it themselves. This session records the steps it
runs on its own, in the issue's folder, with `scripts/agent/progress.sh`. Each call updates the
comment and prints its link:

| Step | Before it | After it |
|---|---|---|
| 2. Plan, until approved | `plan_start=$(date +%s)`; keep the number | on the approval, per plan: `progress.sh begin plan/<n>-<slug> plan --at <plan_start>`, then `progress.sh end plan/<n>-<slug> plan "approved (<k> change rounds)"` |
| 6. Update with `main` | `progress.sh begin <pr> "update with main"` | `progress.sh end <pr> "update with main" "<merged, N conflicts resolved, bumped to X.Y.Z \| unresolved conflict in <file>>"` |

Steps are named as the scripts name theirs, so the comment counts them with the scripts' own
(`CI <short sha>`, not `CI wait`). A failed `progress.sh` call never stops the run: say so in one
line and go on. Do not record anything else (no tool calls, no reports of this session).

## The issue's own folder

The issue is developed in a folder of its own (step 1), never in the checkout this session started
in. Other sessions may work in that checkout or develop other issues at the same time: a branch
switch or a stray file there breaks their runs. So from step 1 on:

- run every command of this skill in the issue's folder. The Bash tool goes back to the session's
  directory after each call, so start every call with `cd <dir> &&`, and give the Read, Edit and
  Write tools absolute paths under `<dir>`;
- never switch branches, commit, write, pull or clean in the session's own checkout. Reading it is
  fine.

## Notifications

The developer is likely away while the agents run, so every stop ends with a notification: the
`PushNotification` tool (load it with `ToolSearch`, `select:PushNotification`), one line under 200
characters that leads with what to act on:

- ready: `#<n> PR #<pr> ready for your review: <approved by the review agent | N open findings>, CI passed`;
- stopped: `#<n> stopped at <step>: <reason>, PR #<pr>`.

Send one notification per stop, never for routine progress. Print the same message, with the pull
request link, and the issue's folder in the session as well.

## 0. Where the issue stands

```sh
gh auth status
gh issue view <n> --json number,title,state,url
git ls-remote --heads origin "plan/<n>-*"
gh pr list --state all --limit 200 --json number,headRefName,state,isDraft,labels,url \
  --jq '[.[] | select(.headRefName | startswith("plan/<n>-"))]'
```

Stop if the issue is closed, or if `gh` is not logged in.

Print one line per plan branch: the branch, its pull request, and where it stands (no pull request
yet; draft labelled `plan`: the plan is under review; draft labelled `agent`; ready for review;
merged). Then pick where to start after step 1: step 2 when there is no plan branch; step 2 with
[an existing plan](#an-existing-plan) for an unmerged plan branch whose plan is not implemented yet
(an open draft labelled `plan`, or no pull request at all); step 4 for an open draft labelled
`agent`; step 6 for a pull request ready for review. Say where you are starting and why.

## 1. A folder of its own

The folder is `../<this checkout's folder>-<n>`, next to the main checkout, as a Git worktree of
the same repository, checked out from `origin/main`. A worktree shares the repository and its agent
state (`.git/agent/`, see the walkthrough), so `mise run agent:next` and a later `/develop-issue
<n>` find the run from any of them.

```sh
main=$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")
dir="$(dirname "$main")/$(basename "$main")-<n>"
git worktree list --porcelain | grep -qx "worktree $dir" && echo "reuse $dir"
```

- **The folder is a worktree of this repository already** (an earlier run of the issue): reuse it.
  `cd "$dir" && git status --short` must be empty. If it is not, say which files are in the way and
  stop: never stash or discard them yourself.
- **It does not exist**: create it, then install what the scripts and the Git hooks need:

  ```sh
  git fetch origin
  git worktree add --detach "$dir" origin/main
  cd "$dir" && mise trust && mise run setup
  ```

- **It exists but is not a worktree of this repository**: say so and stop.

Say which folder the run uses. From here on, every command runs in it (see
[The issue's own folder](#the-issues-own-folder)). For a pull request already under way, the
scripts switch the folder to its branch themselves; step 6 switches to it when it updates the
branch.

## 2. Plan (the approval)

### An existing plan

When step 0 found plan branches whose plans are not implemented yet, show each plan in full (read
it from its branch: `git show origin/plan/<n>-<slug>:.plans/<n>-<slug>.md`) with its pull request
link, or "no pull request yet". Then ask with `AskUserQuestion`, header `Plan`, whether to skip
writing the plan: **Use the existing plan (Recommended)**, **Write it again** and **Stop here**.

- **Use the existing plan**: skip the `plan-from-issue` skill and go straight to the approval
  question below. Its review loop works on the existing branch. A branch without a pull request has
  no pull request comments: take the developer's answers in the session only, and do not open one;
  `implement.sh` opens it after the approval.
- **Write it again**: run the `plan-from-issue` skill's sections 1 to 3 (it writes the plan anew,
  using the existing plan and its pull request comments as input), then commit the new plan on the
  existing branch and push it, instead of its section 4: `plan-branch.sh` fails on a branch that
  exists already. Then ask the approval question below.
- **Stop here**: nothing changes; `/develop-issue <n>` asks again.

Take `plan_start` when this question is asked. On the approval, record the plan step as
`"approved (existing plan, <k> change rounds)"` or `"approved (written again, <k> change rounds)"`
([Progress on the issue](#progress-on-the-issue)), and continue with step 3.

### A new plan

Invoke the [`plan-from-issue`](../plan-from-issue/SKILL.md) skill with the Skill tool
(`plan-from-issue`, argument: the issue number), and follow it through its section 5, in the
issue's folder. It writes `<dir>/.plans/<n>-<slug>.md` (several plans when the issue does not fit
one pull request), shows them, pushes each with `scripts/agent/plan-branch.sh` (a branch and a draft
pull request labelled `plan`, linked on the issue), and reviews them with the developer. Leave out
its section 6 ("Tell the user what is next").

### The approval

Show the plan(s) in full with the pull request link(s) (unless just shown for
[an existing plan](#an-existing-plan)), say that the developer can comment there
(inline on the plan's lines, or as a comment) or answer here, and ask with `AskUserQuestion`, header
`Plan`, the options **Approve the plan**, **Change it** (with the comments here, on the pull
request, or both) and **Stop here**. On **Change it**, or an answer in "Other", run the skill's
review loop: collect the terminal answer and `scripts/agent/plan-comments.sh <pr>`, change the plan
on its branch and push, answer every comment, then ask again, until it is approved. Its open
questions must be answered in the plan before it can be approved. This is the last question of the
run: say so in the question ("after this, the run goes on to the pull request without asking").
Take `plan_start` before the skill starts and count the **Change it** rounds (see
[Progress on the issue](#progress-on-the-issue)). On **Stop here**, the plan pull request stays open
for review, and `/develop-issue <n>` continues it.

## 3. After the approval

Record the plan step on each plan branch ([Progress on the issue](#progress-on-the-issue)), print
the comment's link, and continue with step 4. Before it, make sure the plan branch is pushed with
every change of the review (`git status --short` empty, nothing ahead of `origin/plan/<n>-<slug>`),
and switch the folder back to a detached `origin/main` (`git switch --detach origin/main`): the
scripts check the branch out themselves.

For a split issue, steps 4 to 7 run once per plan, in the order of their dependencies, all in the
same folder. A plan that depends on another one waits until that one is merged: run the plans that
do not wait, then stop with their pull requests ready for review, and say which plan comes next
once they are merged (`/develop-issue <n>` continues there). Say which plan you are on in every
report ("plan 2 of 3: plan/<n>-<slug>").

## 4. Run the loop

Command: `scripts/agent/detach.sh run.sh plan/<n>-<slug>`, in the issue's folder, followed as in
[The run and its progress](#the-run-and-its-progress). `run.sh` is `mise run agent:run`: it
implements the plan (`implement.sh`: the developer agent, `AGENT_MODEL`, Sonnet by default,
`deepseek-v4-pro` on the DeepSeek endpoint, implements it with tests, runs `mise run check`, bumps
the version and commits; the plan's draft pull request becomes the implementation's: new title and
body, the label `plan` replaced by `agent`, still a **draft**), waits for CI, and runs
`fix-ci.sh`, `review.sh`, `fix.sh` and `finalise.sh` as `next.sh` decides. Expect an hour or more.
Mention the settings that apply when the developer has set any (`AGENT_*` in the environment).

The agents run headless and may not edit files under `.claude/` (the skills): a plan item there
comes back "not done". Say so at the end; it is the developer's to do by hand, never a reason to
reword the docs around it.

## 5. The loop stops

The run ends in one of three ways, seen in the Monitor's line and the end of `run.log`:

- **ready for review**: go to step 6;
- **`failed: …`**: a step failed. Show the error and the end of the log, notify, and stop. Never
  retry on your own; `/develop-issue <n>` or `mise run agent:run` resumes;
- **`stopping: <reason>`** (over `AGENT_MAX_CI_FIXES`, `AGENT_MAX_TOKENS`, a CI timeout, a fix-ci that
  pushed nothing): `next.sh` has commented the reason on the pull request and removed the `agent`
  label already. Notify and stop. When the only CI failure is the *Version* job's "is not higher
  than", or the branch is `BEHIND` `main`, run step 6's update instead and start the run again.

The pull request is still the plan's (labelled `plan`) only if the implementation has not started:
go back to step 2's review loop.

## 6. Bring it up to date with `main` (only when needed)

Check: `git fetch origin` and `gh pr view <pr> --json mergeStateStatus,headRefName`. When the branch
is behind `main`, update it in the issue's folder, recorded as the step `update with main`
([Progress on the issue](#progress-on-the-issue)). Switch the folder to the pull request's branch
(`git switch <branch> && git merge --ff-only origin/<branch>`), then merge `main` into it with the
[`merge-main-into-branch`](../merge-main-into-branch/SKILL.md) skill (`Skill`,
`merge-main-into-branch`, arguments `--bump <minor|major|patch> --no-questions`): it merges
`origin/main` (never a rebase), commits the merge with its conflict markers, resolves the conflicts
with the plan in mind in a commit of its own, bumps the version again when `main`'s has caught up,
runs `mise run check` and pushes (a plain push, no force). A conflict it cannot resolve with
confidence makes it stop without pushing: then stop and notify, with the files and the reason it
gives.

Pick the bump from the plan's "Version bump". After the update, wait for CI in the background without model turns:
`gh pr checks <pr> --watch --interval 30` after a `sleep 30` (the first seconds after a push have no
checks), as one background Bash call. A red CI: add the `agent` label again, turn the pull request
back into a draft (`gh pr ready <pr> --undo`) and start the run again (step 4), which fixes it.

## 7. Ready for the developer's review

The pull request is ready for review, up to date with `main`, and **CI passed** is green on its
head. Print the final comment's essentials: whether the review agent approved the latest commit,
the open and declined findings, and the usage. Point at the pull request, at the issue's folder (to
try the change there) and at
[the walkthrough's step 6](../../../docs/agentic-development.md#6-review-the-pull-request).

Then, always and before the notification, post the run summary on the pull request, so the
reviewer finds this session's view next to the agents' comments:
`gh pr comment <pr> --body-file <file>`, the file in the session's scratchpad. It starts with
`### Run summary (develop-issue session)` and is written for the reviewer, not for this session
("the plan was approved", not "you approved"):

- where it stands: CI passed on `<short sha>`, up to date with `main`, not merged;
- the review outcome: every finding with its priority and what its fix changed, or why it was
  declined; and when the final comment says the review agent did not approve the latest commit,
  why (for example: the last fix came after the last round) and whether this session read it;
- **worth checking**: what this session saw and the agents did not settle: a gap that a fix left,
  a "not done or done differently" item, a file written by hand instead of generated, tests that
  only CI ran, and the checks this session ran itself (which tests, on which commit, the result);
- the steps run, in order, and the ones skipped;
- the progress comment's link, with its total time, tokens and cost;
- what can come next: another agent round or the merge, whether the merge closes the issue, and
  `/develop-issue <n>` to continue.

Print the comment's link, then notify (see [Notifications](#notifications)) and end with the summary
(step 9).

Say what the developer can ask for next in this session: another agent round, or the merge.

- **Another agent round** (only when asked): ask first whether to change the plan on the branch,
  since the review agent reads the plan, not the developer's own comments. Then run
  `gh pr ready <pr> --undo && gh pr edit <pr> --add-label agent` and start the run again (step 4)
  with `AGENT_MAX_ROUNDS` raised by one, exported for the run.

## 8. Merge (only when the developer asks)

Merging is the developer's decision and never part of the run. Only when the developer asks for it
in this session, and **CI passed** is green and the branch is up to date, run:

```sh
git fetch origin
git rev-list --merges origin/main..origin/<branch>   # empty: --rebase; else --merge
gh pr merge <pr> --rebase --delete-branch
```

A branch that `main` was merged into (step 6) is merged with `--merge` instead of `--rebase`:
GitHub cannot rebase-merge it, since replaying the branch's own commits on `main` runs into the
conflicts the merge commit resolved.

If GitHub refuses it (no code owner's approval yet, CI not green), show why and stop. Never retry
with `--admin` and never use `--auto`.

After the merge, in the issue's folder (`main` is checked out in the main checkout, so the folder
goes back to a detached `origin/main`):

```sh
git fetch origin && git switch --detach origin/main
git branch -D plan/<n>-<slug>
```

Say that CI on `main` releases the new version, and whether the issue closes (`Closes #<n>`) or
stays open for the next plan (`Part of #<n>`). For a split issue, continue with the next plan at
step 4. After the issue's last plan is merged, remove the folder from the main checkout's
repository: `git worktree remove "$dir"` (it refuses a folder with changes: then say so and leave
it).

## 9. Summary

At the end, or when stopping, print:

- the issue, every plan branch and pull request with where it stands;
- the issue's folder;
- the run summary comment on the pull request (step 7), when there is one;
- the steps run in this session, and the ones skipped;
- the progress comment on the issue (`scripts/agent/progress.sh post <pr>` brings it up to date and
  prints its link), with the total time, tokens and cost from it, and its "Worth a look" lines;
- the command that continues from here: `/develop-issue <n>`, or `mise run agent:next <branch>`
  (in the issue's folder) for the loop alone.
