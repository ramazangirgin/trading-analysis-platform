# Agentic development

Agents take a GitHub issue to a reviewed pull request with a green CI. A developer decides at two
points only: approving the plan and merging the pull request (#65). The agents follow the
documented architecture and conventions; the plan they work from is a file in the repository that
the developer reviewed, so their work can be checked against it.

## The flow

```mermaid
flowchart TD
    issue(["GitHub issue"]) --> skill["Planning skill (Claude Code)<br/>/plan-from-issue #lt;issue#gt;"]
    skill --> plan["Development plan<br/>.plans/#lt;issue#gt;-#lt;slug#gt;.md<br/>alone on branch plan/#lt;issue#gt;-#lt;slug#gt;"]
    plan --> approvePlan{"Developer:<br/>plan right?"}
    approvePlan -- "no: edit the plan on its branch" --> plan
    approvePlan -- "yes: mise run agent:run" --> implement["Developer agent (Sonnet)<br/>implement with tests, mise run check,<br/>version bump, commit"]
    implement --> draft["Draft pull request<br/>Closes #issue, label agent"]
    draft --> ci{"CI passed?"}
    ci -- "red" --> fixCi["Developer agent (Sonnet)<br/>fix CI from the failed job's log<br/>(up to 3 attempts)"]
    fixCi --> ci
    ci -- "green" --> rounds{"Review rounds<br/>left? (2)"}
    rounds -- "yes" --> review["Review agent (Opus)<br/>diff against the plan and the conventions"]
    review --> findings{"Findings?"}
    findings -- "R1-1, R1-2, ...<br/>CRITICAL / MAJOR / MINOR" --> fix["Developer agent (Sonnet)<br/>per finding: one commit Address R1-n,<br/>or a reply declining it"]
    fix --> ci
    findings -- "none: approved" --> finalise
    rounds -- "no" --> finalise["Finalise<br/>summary, findings and outcomes, usage;<br/>PR out of draft: ready for review"]
    finalise --> reviewPr{"Developer:<br/>review the pull request"}
    reviewPr -- "more changes: commit by hand,<br/>or another agent round" --> ci
    reviewPr -- "approve, update with main, merge" --> main[("main")]
    main --> release(["Release vX.Y.Z, issue closed"])

    classDef developer fill:#fff3cd,stroke:#b8860b,color:#000
    classDef agent fill:#dbeafe,stroke:#1d4ed8,color:#000
    classDef github fill:#e5e7eb,stroke:#4b5563,color:#000
    class approvePlan,reviewPr developer
    class skill,implement,fixCi,review,fix agent
    class issue,plan,draft,ci,rounds,findings,finalise,main,release github
```

Yellow: the developer's two decisions. Blue: Claude Code (the planning skill in an interactive
session; the agents headless, with the developer's login). Grey: GitHub, CI and the scripts in
[`scripts/agent/`](../scripts/agent) that drive the loop and keep its state on the pull request.

| # | Step | Who | Output |
|---|---|---|---|
| 1 | Plan: read the issue and the documents of every part it touches; one plan per reviewable pull request | Claude Code skill `plan-from-issue` | `.plans/<issue>-<slug>.md` |
| 2 | One branch per plan, holding only that plan file | `scripts/agent/plan-branch.sh` (the skill runs it) | `plan/<issue>-<slug>` pushed, linked on the issue |
| 3 | Plan review: edit the plan on its branch, or approve it | Developer | — |
| 4 | Start the agents | Developer: `mise run agent:run plan/<issue>-<slug>` | — |
| 5 | Implement with tests, `mise run check`, version bump; push; open a **draft** pull request; fix a red CI | Developer agent: `implement.sh`, `fix-ci.sh` | Draft PR labelled `agent`, **CI passed** green |
| 6 | Review against the plan and the conventions: one inline comment per finding, with priority and possible solutions | Review agent: `review.sh` | PR review + summary comment |
| 7 | One commit per finding (`Address R1-3: …`), or a reply declining it; reply on each thread; push | Developer agent: `fix.sh` | Commits, thread replies |
| 8 | Second review → fix round (6–7 again) | Agents | — |
| 9 | Finalise: what the plan asked for, what was done, findings fixed / declined / open, usage; the PR leaves draft (ready for review), `agent` label removed | `finalise.sh` | PR ready for review |
| 10 | Review, bring up to date with `main`, merge; the release follows | Developer | Change on `main`, released |

Agents never merge and never push to `main`: the scripts push only to the plan branch, the agents
cannot run `git push` or `gh`, and `main`'s ruleset requires a code owner's approval and
**CI passed** for every merge anyway.

## Decision

- **Everything runs on the developer's machine, with Claude Code.** Planning, implementation,
  review and fixes all run in Claude Code with the developer's own Claude Code login: the same
  skills, `CLAUDE.md`, subagents and hooks for every step, no API key or other model endpoint to
  manage, and no secrets in GitHub. The agents run headless (`claude -p`); the scripts remove any
  `ANTHROPIC_API_KEY`, `ANTHROPIC_AUTH_TOKEN` or `ANTHROPIC_BASE_URL` from their environment, so the
  login is always what they use. GitHub is reached through the developer's `gh` login and Git
  credentials, so the developer's pushes start CI as usual.
- **Scripts, one per step.** [`scripts/agent/`](../scripts/agent) holds one script per step and
  `run.sh`, which runs them all; mise tasks start them. Each step can also be run, and debugged, on
  its own.
- **The loop follows CI.** After every push the loop waits for **CI passed** on the new head, so a
  review always sees a green head, and a red CI goes to the developer agent first.
- **The pull request is the state.** Every step leaves one comment with a hidden marker
  (`<!-- agent-step step=review round=1 tokens=… -->`). `next.sh` reads the next step from these
  markers and CI's result; nothing else is stored, so an interrupted run continues where it was.
  Comments by other accounts are ignored.
- **The plan file is merged with the code.** It stays in `.plans/` as the record of why the change
  looks the way it does.
- **Sonnet develops, Opus reviews.** The developer agent does most of the work (implementing,
  running the checks, fixing), where Sonnet is fast and good enough; the review is a single,
  judgement-heavy pass per round, where the stronger model finds more. Both are settings.
- **A fixed number of rounds**: two review → fix rounds, then the pull request goes to the
  developer, even when `MINOR` findings remain.

## Review findings

Each finding is one inline comment on the line concerned, or on the file when the line is not part
of the diff (a finding outside the diff goes into the review's body):

- an ID, `R<round>-<n>`, that the fix commit (`Address R1-3: <summary>`) and its reply refer to;
- a priority: `CRITICAL` (a bug, data loss, a security problem, a missing plan item: must be fixed),
  `MAJOR` (an edge case, a broken convention, a missing test: should be fixed), `MINOR` (optional);
- the problem, and one or more possible solutions;
- the other places with the same problem ("Same finding also at"), instead of a comment each.

The review checks the diff against the plan (missing items, out-of-plan changes), correctness,
the conventions of the parts it touches, tests and docs. The summary comment counts the findings
per priority and says whether another round is needed.

**A round with no findings approves the reviewed commit**: the review says "✅ Approved by the review
agent" with the commit, and the loop goes straight to finalising: no empty fix round, and no second
review of the same commit. The approval is a review comment, not a GitHub approval: the agents work
as the pull request's author, and GitHub does not let an author approve their own pull request. The
code owner's approval is still what the merge needs. The final comment says whether the review agent
approved the latest commit.

The developer agent handles one finding per run, most severe first. A fix becomes one commit
through the pre-commit hook; a finding it disagrees with gets a reply with the reason instead of a
change. A thread a developer resolves before the fix round is skipped.

## From issue to `main`: a walkthrough

What a developer does, from an issue to its change released on `main`. The agents do everything
between "start the agents" and "review the pull request".

### 0. Once per machine

```sh
mise run setup      # ta-runner, the frontend's packages and the Git hooks the agents commit through
claude              # then /login: the agents use this Claude Code login
gh auth login       # the scripts push, comment and open pull requests as you
```

### 1. The issue

The agents work from the plan, and the plan from the issue, so the issue needs a clear goal, steps
and "done when" (the shape of the existing issues). A vague issue gives a vague plan: sharpen the
issue first, it is cheaper than reviewing a wrong plan.

### 2. Plan it

In Claude Code, in this repository:

```text
/plan-from-issue 32
```

The skill ([`.claude/skills/plan-from-issue/SKILL.md`](../.claude/skills/plan-from-issue/SKILL.md))
reads the issue, the issues it links to and the documents of every part it touches, and writes
`.plans/<issue>-<slug>.md` from [`plan-template.md`](../.claude/skills/plan-from-issue/plan-template.md),
or several plans when the issue does not fit one pull request. Discuss it with Claude in the same
session until it is right; then the skill pushes one branch per plan
(`mise run agent:plan-branch .plans/<issue>-<slug>.md`) and links it on the issue.

### 3. Review the plan

This is the first of the two decisions that are yours. Read the plan on its branch and check:

- the goal matches the issue, and "Out of scope" leaves out what it should;
- each part names the right conventions, and the design puts the code where they say;
- every work package has its tests, and the tests prove the issue's "done when";
- the version bump (`minor`, `major` for a breaking change);
- "Docs to update" covers the README text for the feature and every screenshot: a new page gets
  one, a page whose look changes gets it retaken. "Later" is not an option: the review agent checks
  the docs and screenshots, and a missing one is a `MAJOR` finding;
- the open questions: answer them in the plan.

Change the plan on its branch (edit, commit, push) until you would accept a pull request that does
exactly that. The agents follow it literally, and the review agent checks the code against it.

### 4. Start the agents

```sh
mise run agent:run plan/32-compare-runs
```

From a clean working tree (the scripts switch to the plan branch). It runs steps 5–9 of the flow:
the developer agent (Sonnet) implements the plan with tests, checks it, bumps the version, pushes
and opens a **draft** pull request labelled `agent` (`Closes #<issue>`); after **CI passed**, the
review agent (Opus) reviews it, the developer agent fixes the findings, CI runs again, and once
more; then the pull request gets a final comment and is marked ready for review. It waits for CI
after every push, so it takes a while: leave the terminal open. Follow it on the pull request,
where every step leaves a comment.

The scripts run from a copy of `scripts/agent/` taken at start, so switching branches does not
change the running scripts or prompts. Each step can also be run on its own:

```sh
mise run agent:implement plan/32-compare-runs        # implement, push, draft pull request
scripts/agent/next.sh --dry-run plan/32-compare-runs # which step is next
mise run agent:next plan/32-compare-runs             # continue the loop to its end
scripts/agent/review.sh <pr>                         # one step: review, fix, fix-ci, finalise
```

`AGENT_BASE_BRANCH=<branch>` starts the plan branch from, and opens the pull request into, another
branch than `main` (a stacked change, or trying out a change to the agents themselves); later steps
use the pull request's base.

### 5. When a run stops early

- **Ctrl+C**, a closed laptop, a crash: start `mise run agent:run` again; it continues where it was
  (the state is on the pull request).
- **The loop gave up** (a comment "Agent loop stopped", the `agent` label removed, the pull request
  still a draft): CI stayed red after `AGENT_MAX_CI_FIXES` attempts, the developer agent found no
  fix, CI took longer than `AGENT_CI_TIMEOUT`, or the run went over `AGENT_MAX_TOKENS`. Fix the
  cause by hand on the plan branch (or not at all), then either continue by hand, or add the
  `agent` label again and run `mise run agent:next plan/<issue>-<slug>`.
- **To stop it yourself**: Ctrl+C, or remove the `agent` label (the loop does nothing on the pull
  request any more).

### 6. Review the pull request

The second decision that is yours. Start with the final comment ("Done: ready for review"): whether
the review agent approved the latest commit, the plan's work packages, the commits, every review
finding with its outcome, and the usage. Then:

- **Open and declined findings** first: an open one was not addressed (a fix that failed the
  pre-commit hook, or a round that did not run); a declined one has the developer agent's reason in
  its thread. Decide each.
- Read the diff as you would any pull request: against the plan, with the conventions in mind. The
  agents' review covered it twice, but it is not a substitute for yours.
- The docs: the README describes the feature, and the screenshots in `docs/screenshots/` show it
  (the pull request's *Files changed* shows the PNGs side by side with the old ones).
- Try it: `git switch plan/<issue>-<slug>` and `mise run dev` (or `mise run run`).

### 7. More changes

- **Small ones**: commit them yourself on the plan branch and push. CI runs as for any pull request.
- **Another agent round**: turn the pull request back into a draft, add the `agent` label and raise
  the round limit:

  ```sh
  gh pr ready <pr> --undo && gh pr edit <pr> --add-label agent
  AGENT_MAX_ROUNDS=3 mise run agent:next plan/<issue>-<slug>
  ```

  The review agent runs a third round and the developer agent fixes its findings. The agents do not
  read your own review comments; to steer them, change the plan on the branch first (the review
  agent reads it every round).
- **Wrong direction**: close the pull request, fix the plan (or the issue), and start again from
  step 2 with a new plan branch.

### 8. Bring it up to date with `main`

The `main` ruleset merges only a pull request whose branch is up to date with `main`, whose
**CI passed** is green, and that a code owner approved (see the root README). If `main` moved while
the agents worked:

```sh
git fetch origin && git switch plan/<issue>-<slug> && git pull
git rebase origin/main            # resolve conflicts, if any; mise run check
mise run version:bump minor       # only if main's version caught up with this branch's
git push --force-with-lease
```

The **Version** job fails when another pull request merged the same version first
([versioning and releases](coding-convention/repository-versioning-and-releases.md)): bump again. Wait for
**CI passed** on the new head.

### 9. Merge

Approve and merge it yourself; nothing merges on its own (auto-merge is off) and the agents never
merge:

```sh
gh pr merge <pr> --rebase --delete-branch
```

A pull request into another branch than `main` (`AGENT_BASE_BRANCH`) is retargeted to `main` once
the branch it stacks on has merged: `gh pr edit <pr> --base main`, then step 8.

### 10. After the merge

- CI runs on `main`; after **CI passed**, [`release.yml`](../.github/workflows/release.yml) tags
  the commit `vX.Y.Z` and publishes the GitHub Release with the jar.
- `Closes #<issue>` in the pull request closes the issue. A split issue stays open until the
  pull request of its last plan has merged.
- The plan stays in `.plans/` on `main`, next to the code it explains.
- Locally: `git switch main && git pull`, and delete the local plan branch
  (`git branch -D plan/<issue>-<slug>`).

## Documentation and screenshots

A change is documented in its own pull request: the plan lists the documents and screenshots it
affects ("Docs to update"), the developer agent updates them, and the review agent checks them
against the plan and on its own (a user-visible change without its description or screenshot is a
`MAJOR` finding).

The README's screenshots (`docs/screenshots/*.png`) are taken by Playwright, the way the end-to-end
tests run: the built jar with a throwaway home and ta-runner replaying a recording, in Google
Chrome, at 1440×900 in the dark theme. One `e2e/screenshots/<name>.shot.ts` per screenshot sets up
the data it needs over the API, opens the page and saves it with `shot(page, '<name>')`:

```sh
mise run screenshots             # every screenshot
mise run screenshots compare     # those whose file or title matches "compare"
```

`SCREENSHOT_DIR=<dir>` writes them elsewhere, to try one out. The agents look at the saved PNGs
with Claude Code's Read tool. Screenshots taken by hand from real runs stay until a change retakes
them.

## Settings

Environment variables of the scripts ([`lib.sh`](../scripts/agent/lib.sh)):

| Variable | Default | What |
|---|---|---|
| `AGENT_MODEL` | `claude-sonnet-5-5` | The developer agent's model (implement, fix-ci, fix) |
| `AGENT_REVIEW_MODEL` | `claude-opus-5-5` | The review agent's model |
| `AGENT_MAX_ROUNDS` | 2 | Review → fix rounds |
| `AGENT_MAX_CI_FIXES` | 3 | Attempts at a red CI, per pull request |
| `AGENT_MAX_TOKENS` | 50 000 000 | Tokens per pull request, cached input included |
| `AGENT_MAX_BUDGET_USD` | none | Per agent call (`claude --max-budget-usd`) |
| `AGENT_CI_TIMEOUT` | 60 | Minutes to wait for CI after a push |
| `AGENT_BASE_BRANCH` | `main` | Where plan branches start and pull requests go |

Costs in the final comment are Claude Code's own figures at API prices; with a subscription login
they show the usage, not a bill.

## Safety

- The agents run with an allow-list of tools (`--permission-mode dontAsk`): no `git push`, no
  `gh`, no skipping the Git hooks, no web search; the review agent only reads. The scripts do every
  push and every GitHub call.
- What the agents read is the reviewed plan, the code, CI's logs and their own review comments,
  not comments by other accounts.
- The agents run real commands (Gradle, pnpm, uv) on the developer's machine, like a developer
  would; run them only on plans you reviewed.

## Not done yet

- **Parallel work packages** each in their own `git worktree`: for now one developer agent works
  through the packages and may hand independent ones to subagents.
- **Sub-issues** for the plans of a split issue.
