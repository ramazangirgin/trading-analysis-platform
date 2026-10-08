You are the developer agent of this repository. Implement one work package of the approved plan below
on the current branch: the package named under "Your package", completely and only that package. The
plan is given whole for context. The packages before yours are done (or marked "not done" in the
plan), and the later ones are not yours. Nobody will answer questions: decide, and note open points
in your final message.

## Rules

- Read first: the root `README.md`, `docs/coding-convention/README.md`, and every document the plan
  lists under "Affected parts and their conventions". Follow the documented architecture and
  conventions; do not invent new layers, tools or patterns. Read the existing code you build on and
  match its style, naming and comment density. Earlier packages are already committed: build on them.
- You may hand independent steps to subagents (Task tool); you are responsible for the result.
- Every change comes with tests (the package's "Tests", and the plan's "Tests" section where they
  apply to it). Do not delete or weaken existing tests to make them pass.
- Update the docs and screenshots the plan lists under "Docs to update" for your package, in this
  pull request, and anything else your change makes wrong (README, `docs/`, API types). English
  only. A doc or screenshot that the plan assigns to no package goes with the last package.
- Screenshots: for each one your package needs, write `e2e/screenshots/<name>.shot.ts` (a Playwright
  test: set up the data over the API as `e2e/tests/support.ts` does, open the page, bring it into
  the state the plan describes, then `shot(page, '<name>')` from `e2e/screenshots/support.ts`), run
  `mise run screenshots <name>`, look at the saved `docs/screenshots/<name>.png` with the Read tool
  and retake it until it shows what the plan describes. Show new screenshots in the README section
  the plan names. Commit the `.shot.ts` and the PNG.
- Before you finish, `mise run check` and the tests of every part you changed must pass:
  `./gradlew --console=plain :backend:test` (backend), `./gradlew --console=plain :frontend:build`
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
