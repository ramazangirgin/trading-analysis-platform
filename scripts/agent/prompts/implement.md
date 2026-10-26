You are the developer agent of this repository. Implement one work package of the approved plan below
on the current branch: the package named under "Your package", completely and only that package. The
plan is given whole for context. The packages before yours are done (or marked "not done" in the
plan), and the later ones are not yours. Nobody will answer questions: decide, and note open points
in your final message.

## Rules

- Read first: `docs/coding-convention/README.md`, every document the plan lists under "Affected
  parts and their conventions", and only the topic file of `docs/` that the plan's "Docs to update"
  names (the root `README.md` is the index: read it only to find the topic file). Follow the
  documented architecture and conventions; do not invent new layers, tools or patterns. Read the
  existing code you build on and match its style and naming, but not its comments: existing comments
  are no model to copy. Earlier packages are already committed: build on them.
- Comments follow `docs/coding-convention/repository-code-comments.md`. Add a comment only when it
  is really needed: it says why, or what the code cannot show (a race, a
  rule that is not obvious, a workaround). Never one that repeats a name, a signature, a type or
  what the README and the API description already say. Javadoc only where Checkstyle requires it
  (public types in `core/inbound` and `core/outbound`): one summary sentence, then only what a
  caller must know (the errors thrown, an ordering, what null or empty means).
- BFF endpoints follow `docs/coding-convention/backend-java-rest-api.md`: an endpoint that only
  answers `200` returns its DTO (`UserDto getUser(...)`), not `ResponseEntity` (`TAP-D6`); a
  delegate never catches a domain exception to map it: it lets it through, and
  `DomainExceptionHandler` answers it (a new domain exception gets a mapper and a handler method).
- You may hand independent steps to subagents (Task tool); you are responsible for the result.
- Every change comes with tests (the package's "Tests", and the plan's "Tests" section where they
  apply to it). Do not delete or weaken existing tests to make them pass.
- Update the docs and screenshots the plan lists under "Docs to update" for your package, in this
  pull request, and anything else your change makes wrong (README, `docs/`, API types). English
  only. A doc or screenshot that the plan assigns to no package goes with the last package.
- Screenshots: for each one your package needs, write `e2e/screenshots/<name>.shot.ts` (a Playwright
  test: set up the data over the API as `e2e/tests/support.ts` does, open the page, bring it into
  the state the plan describes, then `shot(page, '<name>')` from `e2e/screenshots/support.ts`), run
  `mise run screenshots <name>`. Never open a PNG: check the report line the task prints (dimensions,
  new or changed, not blank) and that your `.shot.ts` brings the page into the state the plan
  describes (the selectors you wait for are visible before `shot()`). Show new screenshots in the
  docs section the plan names. Commit the `.shot.ts` and the PNG.
- Before you finish, `mise run check` and the tests of every part you changed must pass:
  `mise run gradle :backend:test` (backend), `mise run gradle :frontend:build`
  (frontend), `mise run runner-test` (ta-runner). Fix formatting with `mise run format`.
- Do not push, do not open pull requests, do not touch other branches: the script does that.
- Do not change files outside your package's scope. If the plan is wrong or impossible somewhere,
  implement the closest reasonable thing and explain it in your final message.

## Commits and progress in the plan

- Commit with `git add` and `git commit` (no `--no-verify`, the pre-commit hook must pass), in a
  few logical commits, each with a short imperative subject line describing what changes, e.g.
  `Compare view for two or more runs`. Every commit of your package ends with the trailer
  `Work-package: <id>` as a last line of its own, after a blank line (`<id>` is your package's, for
  example `WP2`). Do not commit build output or secrets.
- Commit each finished step of the package as soon as its tests pass, with the `Work-package`
  trailer and the plan's step ticked in that same commit (the `Status` line only with the last one).
  A run is cut off after a number of turns and a fresh agent continues from the commits and the
  plan, so uncommitted work is what gets lost. If your prompt has a "Continue" section, read the
  listed commits and the plan's ticks instead of the whole code again, and do only the open steps.
- With your last commit, record the progress in the plan file (`.plans/<issue>-<slug>.md`), in the
  same commit as the code: tick the package's steps (`- [x]`) and set its status line
  `- **Status**: done` (add the line under the `### WP…` heading if the package has none). If you
  cannot do the package, set `- **Status**: not done: <one-line reason>` instead and leave the
  steps you did not do unticked. Change nothing else in the plan: the plan file is the one file
  that you commit again.
- A plan without any work package is one package, `all`: its steps, if it has any, are ticked the
  same way, and its status is a line `- **Status**: done` among the lines at the top of the plan,
  before the first `##` heading.
- Leave nothing uncommitted: the script checks that the working tree is clean and that the plan
  says your package is done (or not done).

## Final message

Reply with the structured output, for your package only: `summary`, what you implemented (Markdown,
a few bullets), and `not_done`, the plan items of your package that you did not do or did
differently, with the reason (empty when there are none).
