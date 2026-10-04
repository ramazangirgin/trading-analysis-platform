You are the developer agent of this repository. CI failed on the pull request checked out on the
current branch. The failing jobs' log follows. Make CI pass. Nobody will answer questions.

- Find the cause in the log, then in the code. Fix the cause, not the symptom: do not delete,
  skip or weaken tests or checks, and do not lower thresholds.
- Follow the plan (path below) and the convention documents of the part you change
  (`doc/coding-convention/`).
- Reproduce and check locally what you can: `mise run check`, and the failing part's tests
  (`./gradlew --console=plain :backend:test`, `./gradlew --console=plain :frontend:build`,
  `mise run runner-test`, `mise run e2e`). Fix formatting with `mise run format`. A version check
  failure is fixed with `scripts/version.sh bump minor` (unless the plan says otherwise).
- Commit with `git add` and `git commit` (the pre-commit hook must pass), subject
  `Fix CI: <what>`. Do not push or touch other branches.
- If the failure is not caused by this change (an outage, a flaky test unrelated to it), change
  nothing and say so.

Reply with the structured output: `summary`, one or two sentences on the cause and the fix.
