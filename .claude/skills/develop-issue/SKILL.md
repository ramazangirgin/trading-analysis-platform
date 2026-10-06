---
name: develop-issue
description: Take a GitHub issue of this repository through the whole agentic development flow (plan, plan branch, developer agent, CI, review → fix rounds, finalise, update with main, merge), one step at a time, asking the developer to approve every step before it runs. Use when the user asks to develop, implement or run the agents on an issue end to end, e.g. "/develop-issue 61", "/develop-issue https://github.com/<owner>/<repo>/issues/61" or "develop issue #61 step by step".
---

# Develop an issue, step by step

The whole [agentic development flow](../../../docs/agentic-development.md) for one issue, driven
from this Claude Code session. Every step runs only after the developer approves it here. It adds
no new behaviour of its own: planning is the [`plan-from-issue`](../plan-from-issue/SKILL.md)
skill, invoked from here, and every other step runs one of the
scripts in [`scripts/agent/`](../../../scripts/agent) that `mise run agent:run` would run in one go.
So the result is the same pull request, with the same state comments, and `mise run agent:next`
can take over at any point.

Input (`$ARGUMENTS`): an issue number (`61`, `#61`) or an issue link
(`https://github.com/<owner>/<repo>/issues/61`). Ask for it if none was given. A link to another
repository than this one (`gh repo view --json nameWithOwner`) is an error: stop and say so.

## Approval gates

Every step marked **Gate** below is a gate:

1. First say in two or three lines what the step does, the exact command, and what it changes
   (files, branches, pushes, comments on GitHub, agent tokens, roughly how long it takes).
2. Then ask with `AskUserQuestion`: header `Step <n>`, the options **Run it** (first), **Skip**
   (only where skipping makes sense) and **Stop here**. Add step-specific options where listed.
3. Run the step only on **Run it**. An answer in "Other" is a change request: make the change (to
   the plan, the command, a setting), then ask again. **Stop here** ends the skill: print where it
   stopped and the command that continues from there.

An approval covers that one step only, never the next one or a later run of the same step. Steps
without a gate (reading, waiting for CI, printing state) run without asking. If a step fails, show
the error and the end of its output, and ask what to do (retry, fix it by hand, stop). Never retry
on your own.

Long-running scripts (the agents, CI): start them with the Bash tool's `run_in_background`. Do not
poll. When the completion notification arrives, read the end of the output and report it. Each
agent prints the path of its full stream (`$TMPDIR/agent-<pid>/<step>.jsonl`). Keep that path for
when something goes wrong.

## 0. Where the issue stands (no gate)

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

## 1. Plan and push the plan branches (the `plan-from-issue` skill)

Invoke the [`plan-from-issue`](../plan-from-issue/SKILL.md) skill with the Skill tool
(`plan-from-issue`, argument: the issue number), and follow it through its section 4. It reads the
issue and the documents of every part it touches, writes `.plans/<n>-<slug>.md` (several plans when
the issue does not fit one pull request), shows them and changes them until the developer agrees,
and then pushes one `plan/<n>-<slug>` branch per plan.

Its approvals are this skill's gates for the plan, asked the same way:

- **The plan**: show the plan(s) in full and ask with the options **Approve the plan**,
  **Change it** and **Stop here**. On **Change it**, or an answer in "Other", change the plan and
  ask again, until it is approved. Its open questions must be answered in the plan before it can be
  approved.
- **The push**: then gate `scripts/agent/plan-branch.sh .plans/<n>-<slug>.md` (every plan of the
  issue). It pushes the branch from `origin/main` with only the plan file, comments the branch link
  on the issue and removes the local file.

Leave out its section 5 ("Tell the user what is next"): instead of handing over to
`mise run agent:run`, this skill continues with step 3. Step 2 is the developer's look at the
pushed plan.

## 2. The plan on GitHub (no gate)

After the push, the plan is on GitHub, where the developer can still review and edit it. Offer
**Review it on GitHub first** as an extra option at the next gate: on that answer, print the
branch links and stop. The developer continues later with `/develop-issue <n>`, which picks up at
step 3.

For a split issue, steps 3 to 7 run once per plan, in the order of their dependencies. A plan that
depends on another one starts only after that one is merged (step 7). Say which plan you are on
in every gate ("plan 2 of 3: plan/<n>-<slug>").

## 3. Implement (Gate)

Command: `scripts/agent/implement.sh plan/<n>-<slug>` (`mise run agent:implement`), in the
background. The developer agent (`AGENT_MODEL`, Sonnet by default) implements the plan with tests,
runs `mise run check`, bumps the version and commits. The script pushes the branch and opens a
**draft** pull request labelled `agent`. Expect tens of minutes. Mention the settings that apply
when the developer has set any (`AGENT_*` in the environment).

When it finishes, print the pull request link and the "Summary" and "Not done or done differently"
sections of its body (`gh pr view <pr> --json body`).

## 4. The review → fix loop (one gate per step)

Repeat until the pull request is ready for review:

1. Ask the loop for its next step (no gate): `scripts/agent/next.sh --dry-run <pr>`. It prints
   `next step: <step>`, a `waiting for` line, or `stopping: <reason>`.
2. **wait** (CI is still running on the head): no gate. In the background, run
   `gh pr checks <pr> --watch --required` and wait for its notification. Then go back to 1. After
   `AGENT_CI_TIMEOUT` minutes (60 by default) without a result, say so and ask: wait longer, or
   stop.
3. **fix-ci**, **review**, **fix**, **finalise**: gate it, then run the step script in the
   background: `scripts/agent/fix-ci.sh <pr>`, `review.sh <pr>`, `fix.sh <pr>` or
   `finalise.sh <pr>`. Before the gate, give what the step is based on:
   - fix-ci: the failing job and the last lines of its log (`gh pr checks <pr>`,
     `gh run view <run> --log-failed | tail -n 60`), and how many of the `AGENT_MAX_CI_FIXES`
     attempts (3) are used;
   - review: the round (`R<round>`) out of `AGENT_MAX_ROUNDS` (2), and the review model;
   - fix: the findings of the latest review, by priority, from its summary comment;
   - finalise: that it marks the pull request ready for review and removes the `agent` label.

   Offer one extra option at these gates: **Run the rest without asking**. On that answer, run
   `scripts/agent/next.sh <pr>` (`mise run agent:next`) in the background to the end of the loop,
   then continue with step 5.
4. After the step, report what it did. For a review: the findings with their IDs and priorities,
   or the approval. For a fix: one line per finding, fixed (with the commit) or declined (with the
   reason). For fix-ci: whether it pushed. A fix-ci that pushed nothing leaves CI red. Say so, and
   ask: another attempt, fix it by hand, or stop.
5. **stopping: <reason>** (over `AGENT_MAX_CI_FIXES` or `AGENT_MAX_TOKENS`): no step runs. Explain
   the reason. Ask whether to hand the pull request back: comment the reason on it and remove the
   `agent` label (`gh pr edit <pr> --remove-label agent`), as `next.sh` does when it stops. Then
   stop. **next step: none**: the pull request is not an open draft labelled `agent`. Go to step 5.

## 5. The developer's review (Gate)

Print the final comment's essentials: whether the review agent approved the latest commit, the
open and declined findings, and the usage. Point at the pull request and at
[the walkthrough's step 6](../../../docs/agentic-development.md#6-review-the-pull-request).

Then ask, with the options:

- **Reviewed: continue** to step 6;
- **Another agent round**: run
  `gh pr ready <pr> --undo && gh pr edit <pr> --add-label agent`, then go back to step 4 with
  `AGENT_MAX_ROUNDS` raised by one, exported for every script of the loop. Ask first whether to
  change the plan on the branch, since the review agent reads the plan, not the developer's own
  comments;
- **Stop here**: the developer reviews, tries and changes it by hand. `/develop-issue <n>`
  continues later.

## 6. Bring it up to date with `main` (Gate, only when needed)

Check (no gate): `git fetch origin` and
`gh pr view <pr> --json mergeStateStatus,headRefName`. When the branch is behind `main`, gate the
update. Say that it rewrites the branch (`--force-with-lease`) and starts CI again:

```sh
git switch plan/<n>-<slug> && git pull --ff-only
git rebase origin/main            # on conflicts: stop and show them, never resolve them unasked
mise run check
mise run version:bump <minor|major|patch>   # only if main's version has caught up with the branch's
git push --force-with-lease
```

Pick the bump from the plan's "Version bump", and check it with
`scripts/version.sh check-bump origin/main`. Then wait for **CI passed** on the new head, as in
step 4.2. A red CI goes back to step 4 (fix-ci), after adding the `agent` label again and turning
the pull request back into a draft.

## 7. Merge (Gate, never by default)

Merging is the developer's decision. Ask with **No, I will merge it myself** as the first option
and **Merge it now** as the second. Only on **Merge it now**, when **CI passed** is green and the
branch is up to date, run:

```sh
gh pr merge <pr> --rebase --delete-branch
```

If GitHub refuses it (no code owner's approval yet, CI not green), show why and stop. Never retry
with `--admin` and never use `--auto`.

After the merge (no gate): `git switch main && git pull --ff-only` and
`git branch -D plan/<n>-<slug>`. Say that CI on `main` releases the new version, and whether the
issue closes (`Closes #<n>`) or stays open for the next plan (`Part of #<n>`). For a split issue,
continue with the next plan at step 3.

## 8. Summary

At the end, or when stopping, print:

- the issue, every plan branch and pull request with where it stands;
- the steps run in this session, and the ones skipped;
- the command that continues from here: `/develop-issue <n>`, or `mise run agent:next <branch>`
  for the loop alone.
