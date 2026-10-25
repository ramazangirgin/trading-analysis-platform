# Plan: Split README.md into topic files

- **Issue**: #244 (Agentic flow: cut tokens per issue (orchestration, work package size, update with main, quiet tool output, smaller docs))
- **Plan**: 3 of 3 for this issue; depends on: `244-update-main-and-wp-size` (plan 2: both change `scripts/agent/prompts/implement.md`, `docs/agentic-development.md` and the README's "Agentic development" section)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

`README.md` (987 lines, about 70 KB, read 5–6 times per issue by the agents) becomes a short entry
point: what the platform is, the first two Quick start steps, and a table linking one topic file per
subject under `docs/`. The user manual, the Docker Compose setup, the configuration, the login and
backend reference, development, and CI and releases each live in a file of their own, so an agent
updating the docs of one feature opens one small file. The agents' prompts and `CLAUDE.md` point at
the topic files instead of the whole README. A test checks that every relative link and anchor in
`README.md` and `docs/` resolves, so the split breaks no link now or later. The content itself is
moved, not rewritten.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Docs (`README.md`, `docs/`) | the plain style of the existing documents; images as relative paths (`screenshots/<name>.png` from `docs/`); the coding conventions stay in `docs/coding-convention/` with their own `README.md` index (unchanged) |
| Agent prompts (`scripts/agent/prompts/`) | the style of the existing prompts |
| Agent scripts tests (`scripts/agent/`) | Python standard library, `unittest` in `scripts/agent/test_*.py`, run by `mise run agent:test` (in `mise run check` and CI) |
| e2e screenshots (`e2e/screenshots/`, `e2e/playwright.screenshots.config.ts`) | Prettier, `pnpm run type-check`; only the comments that say "README's screenshots" change |
| Claude Code skills (`.claude/skills/`) | edited by the developer by hand: headless agents may not edit files under `.claude/` (WP4) |

## Design

### The topic files

Moved from `README.md` as they are (headings shifted up one level so each file starts at `#`;
intra-file links rewritten), in this order of the current README:

| File | From the README (current headings) |
|---|---|
| `docs/user-guide.md` | Quick start "3. Log in" to "8. Reports and export", and "Language". The step numbers stay (`## 3. Log in`), and the file starts with one sentence that steps 1 and 2 are in the README's Quick start |
| `docs/docker-compose.md` | "Run with Docker Compose" with every "Upgrading to …" section |
| `docs/configuration.md` | "Where things live" and "LLM prices" |
| `docs/login-and-backend.md` | Development → "Login" (the session login, password change, disclaimer and the "The backend:" reference list) |
| `docs/development.md` | Development → "Dev servers", "Tasks", "Coding conventions", "Before pushing", "ta-runner", "Testing the Docker runner" |
| `docs/ci-and-releases.md` | Development → "Continuous integration", "Dependency updates", "Versioning and releases" |

The README's "Agentic development" section shrinks to its first paragraph and the link to
`docs/agentic-development.md`; its sample flow and the `develop-issue` paragraph are already
covered there (the developer agent checks that anything only the README says, such as the
`mise activate` note, is in `docs/agentic-development.md`, and moves it there if not).

### What stays in `README.md`

The title and CI badge, the introduction with the `run-decision.png` screenshot, the artifact table,
the "Two ways to run it" paragraph, Quick start "1. Prerequisites" and "2. Start the platform"
(ending with a link to `docs/user-guide.md#3-log-in` for the next steps), a new "Documentation"
section (a table: file, what it covers, who reads it, for every topic file above plus
`docs/agentic-development.md`, `docs/event-protocol.md`, `docs/coding-convention/README.md`,
`deploy/server/README.md`), a short "Agentic development" section, and "License". About 120 lines.

### Links

Every link that pointed into a moved section is rewritten to the topic file and its anchor:

- inside the README and the topic files (`(#3-log-in)`, `(#login)`, `(#where-things-live)`,
  `(#run-with-docker-compose)`, `(#llm-prices)`, `(#development)`, `(#6-watch-it-and-read-the-results)`, …);
- from other files: `deploy/server/README.md` (`../../README.md#3-log-in`, `#4-add-an-api-key`,
  `#continuous-integration`), `docs/agentic-development.md` (the root README for `main`'s ruleset,
  CI table), `CLAUDE.md`, and any other hit of `grep -rn 'README.md#' --exclude-dir=node_modules
  --exclude-dir=.git --exclude-dir=build .` or of a relative link to the root `README.md` outside
  `.plans/` (plans are history and stay as they are) and `.claude/` (WP4);
- links inside the moved text get one more `../` where they point out of `docs/` (`mise.toml` →
  `../mise.toml`, `artifact/...` → `../artifact/...`, `docs/screenshots/x.png` → `screenshots/x.png`,
  `docs/coding-convention/...` → `coding-convention/...`).

### Link check: `scripts/agent/doc_links.py`

`doc_links.py [<file>...]` (standard library; default: `README.md`, `CLAUDE.md`, `docs/**/*.md`,
`deploy/server/README.md`): for every Markdown link and image with a relative target, the target
file exists; for a target with `#anchor` (or a bare `#anchor`), the target file has a heading whose
GitHub slug is that anchor (lowercase, punctuation but `-` and `_` removed, spaces to `-`, a repeated
slug numbered `-1`, `-2`; headings inside code fences ignored). External links (`http`, `mailto`)
are not checked. It prints one line per broken link (`<file>:<line>: <target>: <why>`) and exits 1
when there is one. `test_doc_links.py` tests it on fixtures and runs it on the repository's own docs,
so `mise run agent:test` (and CI's job for it) fails on a broken link. It lives in `scripts/agent/`
because it guards the docs the agents edit, and runs where the other helper tests run.

### Prompts and `CLAUDE.md`

- `implement.md`: "Read first: the root `README.md`, …" becomes: `docs/coding-convention/README.md`,
  the documents the plan lists under "Affected parts and their conventions", and only the topic file
  of `docs/` that the plan's "Docs to update" names (the README is the index: read it only to find
  the topic file); "Show new screenshots in the README section the plan names" becomes "in the docs
  section the plan names"; "anything else your change makes wrong (README, `docs/`, API types)" stays.
- `review.md`: "Read the root `README.md`, …" the same way; "described in the README where users look
  for it" becomes "described in the topic file of `docs/` where users look for it (the README links
  it)"; the screenshot line says "shown in the docs".
- `fix.md`: "show it in the README where the finding says" becomes "in the docs where the finding
  says".
- `CLAUDE.md`: the first paragraph: `README.md` is the entry point and its "Documentation" table
  lists the topic files (user guide, Docker Compose, configuration, login and backend, development,
  CI and releases); the "Where things are" table gets a "Docs" row (`README.md`, `docs/`).
- `mise.toml` `screenshots` description, `e2e/screenshots/support.ts` and
  `e2e/playwright.screenshots.config.ts` comments: "the README's screenshots" becomes "the docs'
  screenshots".

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Move the sections into topic files

- **Status**: open
- **Depends on**: none
- **Files**: `README.md`, `docs/user-guide.md` (new), `docs/docker-compose.md` (new),
  `docs/configuration.md` (new), `docs/login-and-backend.md` (new), `docs/development.md` (new),
  `docs/ci-and-releases.md` (new), `docs/agentic-development.md`
- **Steps**:
  - [ ] move each section into its topic file as in the design, with its links rewritten
  - [ ] the README as in "What stays in README.md", with the "Documentation" table
  - [ ] the "Agentic development" section shortened; anything only it said moved into
    `docs/agentic-development.md`
- **Tests**: `git diff --stat` shows the README lines moved, not lost: the topic files together with
  the new README hold every paragraph of the old README (the developer agent compares the old
  README's non-heading lines against the new files, e.g. with `grep -F -x -f`, and lists any line
  that is gone on purpose in its summary); WP2's link check passes on the result.

### WP2: Link check and the links from elsewhere

- **Status**: open
- **Depends on**: WP1
- **Files**: `scripts/agent/doc_links.py` (new), `scripts/agent/test_doc_links.py` (new),
  `deploy/server/README.md`, `docs/agentic-development.md`, and the other files the grep in "Links"
  finds outside `.plans/` and `.claude/`
- **Steps**:
  - [ ] `doc_links.py` as in the design
  - [ ] every link into the old README sections rewritten; `doc_links.py` prints nothing
- **Tests**: `test_doc_links.py`: fixtures in a temporary folder: a good relative link, a missing
  file, a good and a missing anchor, a heading with punctuation and backticks, a repeated heading
  (`-1`), a heading inside a code fence (not an anchor), an image, an external link (skipped); and
  one test that runs the check on the repository's docs and expects no broken link.

### WP3: Prompts, CLAUDE.md, screenshot wording

- **Status**: open
- **Depends on**: WP1
- **Files**: `scripts/agent/prompts/implement.md`, `scripts/agent/prompts/review.md`,
  `scripts/agent/prompts/fix.md`, `CLAUDE.md`, `mise.toml`, `e2e/screenshots/support.ts`,
  `e2e/playwright.screenshots.config.ts`
- **Steps**:
  - [ ] the changes listed under "Prompts and CLAUDE.md"
  - [ ] bump the version (`scripts/version.sh bump minor`), as the last package
- **Tests**: `mise run check` (the e2e type-check and Prettier cover the two `.ts` files; the link
  check covers `CLAUDE.md`).

### WP4: The skills (by hand, not by the agents)

- **Status**: not done: manual: files under `.claude/` are edited by the developer by hand, since the headless agents may not edit them
- **Depends on**: WP3
- **Files**: `.claude/skills/plan-from-issue/SKILL.md`, `.claude/skills/plan-from-issue/plan-template.md`,
  `.claude/skills/dependabot-fix/SKILL.md`, `.claude/skills/renovate-fix/SKILL.md`,
  `.claude/skills/renovate-update/SKILL.md`, `.claude/skills/pr-ready/SKILL.md`
- **Steps**:
  - [ ] `plan-from-issue` section 2, "Always" row: `README.md` (the index) and the topic files of
    `docs/` the issue touches, not the whole manual; section 3, "Docs to update": "the root
    `README.md`: the section describing the feature" becomes "the topic file of `docs/` describing
    the feature (`README.md`'s Documentation table lists them)"; "the README shows every page"
    becomes "the docs show every page"
  - [ ] `plan-template.md`: the "Docs to update" table's example `README.md section "…"` becomes
    `docs/<topic>.md section "…"`
  - [ ] `dependabot-fix`, `renovate-fix`, `renovate-update`: `README "Dependency updates"` becomes
    `docs/ci-and-releases.md` "Dependency updates"; `pr-ready`: the README reference for `main`'s
    protection becomes `docs/ci-and-releases.md`
- **Tests**: none (Markdown read by Claude Code); `doc_links.py .claude/skills/*/SKILL.md` prints no
  broken link.

## Tests

- Unit: `test_doc_links.py` (new), in `mise run agent:test` and CI; it also checks the repository's
  own docs, so a later change that breaks a link fails CI.
- WP1's line comparison: no paragraph of the old README is lost.
- The issue's "done when" for this part: README.md is split into topic files (WP1), the agents read
  the topic file instead of the README (WP3, WP4). The token comparison after the change is the
  developer's (plan 1, "Out of scope"): run `mise run agent:stats <issue>` on the first issue after
  this plan and record it on #244.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md` | the short entry point and its "Documentation" table (WP1) |
| Text | `docs/user-guide.md`, `docs/docker-compose.md`, `docs/configuration.md`, `docs/login-and-backend.md`, `docs/development.md`, `docs/ci-and-releases.md` | new, moved from the README (WP1) |
| Text | `docs/agentic-development.md` | "Documentation and screenshots": the screenshots are shown in the topic files; links to the root README rewritten (WP1, WP2) |
| Text | `docs/development.md` (moved "Tasks" and "Continuous integration" in `docs/ci-and-releases.md`) | no new tasks; the CI table names the link check in the agent scripts' tests job |
| Text | `CLAUDE.md` | the README as the entry point, the "Docs" row (WP3) |
| Screenshot | none | the images move with their sections; no screenshot is retaken (the pages do not change) |

## Out of scope

- Rewriting or shortening the moved text: it moves as it is; shortening a topic file is a change of
  its own.
- The plans in `.plans/`: they are the record of past changes and keep their links to the old README.
- A link check for external URLs.

## Open questions

- The six topic files and their names (`user-guide`, `docker-compose`, `configuration`,
  `login-and-backend`, `development`, `ci-and-releases`): right cut? The "Login" section is mostly a
  backend reference (permissions, endpoints), hence `login-and-backend.md`.
- The link check is a new check (`scripts/agent/doc_links.py`, run through `mise run agent:test`):
  keep it there, or make it a task of its own (`docs:check`) in `mise run check` and a CI step?
