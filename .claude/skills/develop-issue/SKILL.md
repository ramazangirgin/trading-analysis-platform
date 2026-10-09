---
name: develop-issue
description: Take a GitHub issue of this repository through the whole agentic development flow (plan, plan branch, developer agent, CI, review → fix rounds, finalise, update with main) in one run. It asks the developer only to approve the plan, runs every other step without asking, and sends a notification when the pull request is ready for the developer's review. It never merges unless asked. Use when the user asks to develop, implement or run the agents on an issue end to end, e.g. "/develop-issue 61", "/develop-issue https://github.com/<owner>/<repo>/issues/61" or "develop issue #61".
---

# Develop an issue

The whole [agentic development flow](../../../docs/agentic-development.md) for one issue, driven
from this Claude Code session. The developer approves the plan; every later step runs without
asking, and the run ends with a notification that the pull request is ready for the developer's
review. It adds no new behaviour of its own: planning is the
[`plan-from-issue`](../plan-from-issue/SKILL.md) skill, invoked from here, and every other step runs
one of the scripts in [`scripts/agent/`](../../../scripts/agent) that `mise run agent:run` would run
in one go. So the result is the same pull request, with the same state comments, and
`mise run agent:next` can take over at any point.

Input (`$ARGUMENTS`): an issue number (`61`, `#61`) or an issue link
(`https://github.com/<owner>/<repo>/issues/61`). Ask for it if none was given. A link to another
repository than this one (`gh repo view --json nameWithOwner`) is an error: stop and say so.

## One approval: the plan

The plan (step 1) is the only step that waits for the developer. Everything after it (pushing the
plan branch, implementing, CI fixes, reviews, fix rounds, finalising, the update with `main`) runs
without asking. Before each step, say in one or two lines what it does and the command, then run
it; after it, report what it did.

The run stops without asking, and notifies the developer (see [Notifications](#notifications)),
when:

- a step fails: show the error and the end of its output. Never retry on your own;
- the loop stops (`stopping: <reason>`, step 4.5);
- the rebase on `main` conflicts in a file other than the version files (step 5);
- the pull request is ready for review (step 6).

Merging is never part of the run. It is the developer's decision; merge only when the developer asks
for it in this session (step 7).

Long-running scripts (the agents, CI): start them with the Bash tool's `run_in_background`. Do not
poll. When the completion notification arrives, read the end of the output and report it. Each
agent prints the path of its full stream (`$TMPDIR/agent-<pid>/<step>.jsonl`). Keep that path for
when something goes wrong.

## Notifications

The developer is likely away while the agents run, so every stop ends with a notification: the
`PushNotification` tool (load it with `ToolSearch`, `select:PushNotification`), one line under 200
characters that leads with what to act on:

- ready: `#<n> PR #<pr> ready for your review: <approved by the review agent | N open findings>, CI passed`;
- stopped: `#<n> stopped at <step>: <reason>, PR #<pr>`.

Send one notification per stop, never for routine progress. Print the same message, with the pull
request link, in the session as well.

## 0. Where the issue stands

```sh
gh auth status
gh issue view <n> --json number,title,state,url
git status --short
git ls-remote --heads origin "plan/<n>-*"
gh pr list --state all --limit 200 --json number,headRefName,state,isDraft,labels,url \
  --jq '[.[] | select(.headRefName | startswith("plan/<n>-"))]'
```

Stop if the issue is closed, or if `gh` is not logged in. The scripts need a clean working tree. If
it is not clean, say which files are in the way and stop. Never stash or discard them yourself.

Print one line per plan branch: the branch, its pull request, and where it stands (no pull request
yet; draft labelled `agent`; ready for review; merged). For a plan with an open draft labelled
`agent`, run `scripts/agent/next.sh --dry-run <branch>` and show its next step. Then start at the
first step that is not done: step 1 when there is no plan branch, step 3 for the first unmerged plan
without a pull request, step 4 for an open draft labelled `agent`, step 5 for a pull request ready
for review. Say where you are starting and why.

## 1. Plan (the approval)

Invoke the [`plan-from-issue`](../plan-from-issue/SKILL.md) skill with the Skill tool
(`plan-from-issue`, argument: the issue number), and follow it through its section 3. It reads the
issue and the documents of every part it touches, writes `.plans/<n>-<slug>.md` (several plans when
the issue does not fit one pull request), shows them and changes them until the developer agrees.

Show the plan(s) in full and ask with `AskUserQuestion`, header `Plan`, the options
**Approve the plan**, **Change it** and **Stop here**. On **Change it**, or an answer in "Other",
change the plan and ask again, until it is approved. Its open questions must be answered in the plan
before it can be approved. This is the last question of the run: say so in the question ("after
this, the run goes on to the pull request without asking").

## 2. Push the plan branches

Right after the approval, run `scripts/agent/plan-branch.sh .plans/<n>-<slug>.md` for every plan of
the issue (the `plan-from-issue` skill's section 4). It pushes the branch from `origin/main` with
only the plan file, comments the branch link on the issue and removes the local file. Leave out its
section 5 ("Tell the user what is next"): continue with step 3.

For a split issue, steps 3 to 6 run once per plan, in the order of their dependencies. A plan that
depends on another one waits until that one is merged: run the plans that do not wait, then stop
with their pull requests ready for review, and say which plan comes next once they are merged
(`/develop-issue <n>` continues there). Say which plan you are on in every report
("plan 2 of 3: plan/<n>-<slug>").

## 3. Implement

Command: `scripts/agent/implement.sh plan/<n>-<slug>` (`mise run agent:implement`), in the
background. The developer agent (`AGENT_MODEL`, Sonnet by default) implements the plan with tests,
runs `mise run check`, bumps the version and commits. The script pushes the branch and opens a
**draft** pull request labelled `agent`. Expect tens of minutes. Mention the settings that apply
when the developer has set any (`AGENT_*` in the environment).

When it finishes, print the pull request link and the "Summary" and "Not done or done differently"
sections of its body (`gh pr view <pr> --json body`).

## 4. The review → fix loop

Repeat until the pull request is ready for review:

1. Ask the loop for its next step: `scripts/agent/next.sh --dry-run <pr>`. It prints
   `next step: <step>`, a `waiting for` line, or `stopping: <reason>`.
2. **wait** (CI is still running on the head): in the background, run
   `sleep 30; gh pr checks <pr> --watch --interval 30` and wait for its notification, then go back
   to 1. Watch every check, not `--required` only: **CI passed** is reported only once the other
   jobs are done, so `--required` exits at once with "no required checks reported". The first
   seconds after a push have no checks at all, hence the `sleep`. After `AGENT_CI_TIMEOUT` minutes
   (60 by default) without a result, stop and notify.
3. **fix-ci**, **review**, **fix**, **finalise**: run the step script in the background:
   `scripts/agent/fix-ci.sh <pr>`, `review.sh <pr>`, `fix.sh <pr>` or `finalise.sh <pr>`. Before
   it, say what the step is based on:
   - fix-ci: the failing job and the last lines of its log (`gh pr checks <pr>`,
     `gh run view <run> --log-failed | tail -n 60`), and how many of the `AGENT_MAX_CI_FIXES`
     attempts (3) are used. First check whether `main` moved under the pull request
     (`git fetch origin`, `gh pr view <pr> --json mergeStateStatus`: `BEHIND`), or the only
     failure is the *Version* job's "is not higher than" (another pull request merged the same
     version). Then the developer agent is the wrong fix: it only commits on top, and the branch
     stays behind `main`. Run step 5's update instead of fix-ci, then go back to 1;
   - review: the round (`R<round>`) out of `AGENT_MAX_ROUNDS` (2), and the review model;
   - fix: the findings of the latest review, by priority, from its summary comment;
   - finalise: that it marks the pull request ready for review and removes the `agent` label.
4. After the step, report what it did. For a review: the findings with their IDs and priorities,
   or the approval. For a fix: one line per finding, fixed (with the commit) or declined (with the
   reason). For fix-ci: whether it pushed. A fix-ci that pushed nothing leaves CI red: go back to
   1, which runs the next attempt until `AGENT_MAX_CI_FIXES` stops the loop.
5. **stopping: <reason>** (over `AGENT_MAX_CI_FIXES` or `AGENT_MAX_TOKENS`): no step runs. Hand the
   pull request back as `next.sh` does when it stops: comment the reason on it and remove the
   `agent` label (`gh pr edit <pr> --remove-label agent`). Then stop and notify.
   **next step: none**: the pull request is not an open draft labelled `agent`. Go to step 5.

## 5. Bring it up to date with `main` (only when needed)

Check: `git fetch origin` and `gh pr view <pr> --json mergeStateStatus,headRefName`. When the branch
is behind `main`, update it. It rewrites the branch (`--force-with-lease`) and starts CI again:

```sh
git switch plan/<n>-<slug> && git pull --ff-only
git rebase origin/main            # conflicts: see below
mise run version:bump <minor|major|patch>   # only if main's version has caught up with the branch's
git commit -am "Bump version to <version>"  # its own commit
mise run check
git push --force-with-lease
```

Conflicts only in the three version files (`gradle.properties`, `artifact/frontend/package.json`,
`artifact/ta-runner/ta_runner/__init__.py`): take `main`'s side, continue the rebase, then bump in
a commit of its own. A conflict in any other file: `git rebase --abort`, show the conflicting
files, then stop and notify. Never resolve it on your own.

Pick the bump from the plan's "Version bump", and check it with
`scripts/version.sh check-bump origin/main`. Then wait for **CI passed** on the new head, as in
step 4.2. Run from step 4 (instead of a fix-ci), go back to step 4.1. Run on a pull request that is
already ready for review, a red CI goes back to step 4 (fix-ci): first add the `agent` label again
and turn the pull request back into a draft (`gh pr ready <pr> --undo`).

## 6. Ready for the developer's review

The pull request is ready for review, up to date with `main`, and **CI passed** is green on its
head. Print the final comment's essentials: whether the review agent approved the latest commit,
the open and declined findings, and the usage. Point at the pull request and at
[the walkthrough's step 6](../../../docs/agentic-development.md#6-review-the-pull-request). Then
notify (see [Notifications](#notifications)) and end with the summary (step 8).

Say what the developer can ask for next in this session: another agent round, or the merge.

- **Another agent round** (only when asked): ask first whether to change the plan on the branch,
  since the review agent reads the plan, not the developer's own comments. Then run
  `gh pr ready <pr> --undo && gh pr edit <pr> --add-label agent` and go back to step 4 with
  `AGENT_MAX_ROUNDS` raised by one, exported for every script of the loop.

## 7. Merge (only when the developer asks)

Merging is the developer's decision and never part of the run. Only when the developer asks for it
in this session, and **CI passed** is green and the branch is up to date, run:

```sh
gh pr merge <pr> --rebase --delete-branch
```

If GitHub refuses it (no code owner's approval yet, CI not green), show why and stop. Never retry
with `--admin` and never use `--auto`.

After the merge: `git switch main && git pull --ff-only` and `git branch -D plan/<n>-<slug>`. Say
that CI on `main` releases the new version, and whether the issue closes (`Closes #<n>`) or stays
open for the next plan (`Part of #<n>`). For a split issue, continue with the next plan at step 3.

## 8. Summary

At the end, or when stopping, print:

- the issue, every plan branch and pull request with where it stands;
- the steps run in this session, and the ones skipped;
- the command that continues from here: `/develop-issue <n>`, or `mise run agent:next <branch>`
  for the loop alone.
