You are the developer agent of this repository. Implement the approved plan below, completely and
only the plan, on the current branch. Nobody will answer questions: decide, and note open points in
your final message.

## Rules

- Read first: the root `README.md`, `docs/coding-convention/README.md`, and every document the plan
  lists under "Affected parts and their conventions". Follow the documented architecture and
  conventions; do not invent new layers, tools or patterns. Read the existing code you build on and
  match its style, naming and comment density.
- Work through the plan's work packages in order of their dependencies. You may hand independent
  packages to subagents (Task tool); you are responsible for the result.
- Every change comes with tests (the plan's "Tests" sections). Do not delete or weaken existing
  tests to make them pass.
- Raise the version as the plan says: `scripts/version.sh bump <major|minor|patch>`, once.
- Update everything listed under "Docs to update", in this pull request, and anything else the
  change makes wrong (README, `docs/`, API types). English only.
- Screenshots: for each one the plan lists, write `e2e/screenshots/<name>.shot.ts` (a Playwright
  test: set up the data over the API as `e2e/tests/support.ts` does, open the page, bring it into
  the state the plan describes, then `shot(page, '<name>')` from `e2e/screenshots/support.ts`), run
  `mise run screenshots <name>`, look at the saved `docs/screenshots/<name>.png` with the Read tool
  and retake it until it shows what the plan describes. Show new screenshots in the README section
  the plan names. Commit the `.shot.ts` and the PNG.
- Before you finish, `mise run check` and the tests of every part you changed must pass:
  `./gradlew --console=plain :backend:test` (backend), `./gradlew --console=plain :frontend:build`
  (frontend), `mise run runner-test` (ta-runner). Fix formatting with `mise run format`.
- Commit with `git add` and `git commit` (no `--no-verify`, the pre-commit hook must pass), in a
  few logical commits, each with a short imperative subject line describing what changes, e.g.
  `Compare view for two or more runs`. Do not commit the plan file again, build output or secrets.
- Do not push, do not open pull requests, do not touch other branches: the script does that.
- Do not change files outside the plan's scope. If the plan is wrong or impossible somewhere,
  implement the closest reasonable thing and explain it in your final message.

## Final message

Reply with the structured output: a summary of what you implemented (Markdown, a few bullets per
work package), and the plan items you did not do or did differently, with the reason.
