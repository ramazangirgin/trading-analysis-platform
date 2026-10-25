# Plan: Deploy to analystock.app automatically on each new release

- **Issue**: #232 (Deploy to analystock.app automatically on each new release)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

When the `Release` workflow publishes a new release `vX.Y.Z`, a new `Deploy` workflow deploys that
tag to the Oracle VM behind https://analystock.app with the same playbook as
`mise run server:deploy -- -e public_domain=analystock.app -e acme_email=<mail>`, without anyone
running it from a laptop. The deploy runs in a `production` environment whose secrets hold the SSH
key, the host and its pinned host key, and which requires the developer's approval: each deploy
waits until the developer clicks *Approve and deploy*, then runs on its own. That one click departs
from the issue's "without any manual step"; it is the developer's choice (answer on the plan's
review). The deploy runs one at a time, is never cancelled halfway, fails
visibly, and ends by checking that the platform answers over HTTPS and reports the released version.
The same workflow can be started from the Actions tab for any released tag, which is also how a
failed deploy is re-run and how a release is rolled back.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| CI (`.github/workflows/`) | The style of `release.yml` and `ci.yml`: a header comment that says what the workflow does and why, least-privilege `permissions` (`contents: read` at the top, more only on the job that needs it, with a comment why), `concurrency` with `cancel-in-progress: false` for anything that must not stop halfway, actions pinned to the majors already used (`actions/checkout@v7`, `jdx/mise-action@v5`), tools from `mise.toml` (no ad-hoc installs). Values from `${{ }}` reach scripts through `env:`, never inlined into `run:`. Only the workflow's `GITHUB_TOKEN` is used besides the environment's secrets (repository-versioning-and-releases.md, "Why"). |
| Deploy (`deploy/server/`) | ansible-lint production profile (`mise run server:check`, docs/coding-convention/README.md "Deploy" row): named tasks, `changed_when`/`failed_when` on every probe, idempotent (a second deploy reports `changed=0`). Machine-specific files stay out of the repository; the workflow writes its inventory and key under `$RUNNER_TEMP`. `deploy.sh` and the playbook are reused unchanged in how they are called. |
| Versioning and releases | docs/coding-convention/repository-versioning-and-releases.md: tags only by CI; a release is skipped when it exists already (the deploy must not fire on a no-op release run); one version bump in this pull request. |
| Docs | repository-code-comments.md (workflow comments say why, not what); deploy/server/README.md is the manual for the server deployment. |

## Design

### How the deploy is triggered

A release is created by `release.yml` with the workflow's `GITHUB_TOKEN`. Events caused by that
token (`release: published`, `push` of the tag) start no other workflow, so `on: release` would
never fire. `workflow_run` after `Release` would fire on every Release run, including the many that
do nothing (Renovate merges, reruns of older CI runs, where a rerun of an old run would even roll the
server back). `workflow_dispatch` is the one event the `GITHUB_TOKEN` may start. So:

- **`.github/workflows/deploy.yml`** (`name: Deploy`) has a single trigger, `workflow_dispatch` with a
  required input `tag` (for example `v5.3.0`). Automatic and manual deploys take the same path.
- **`release.yml`** gets a last step, *Deploy the release*, that runs
  `gh workflow run deploy.yml --ref main -f tag="$TAG"` only when
  - this run created the release (`steps.plan.outputs.release == 'true'`), and
  - the tag is the latest release (`gh release view --json tagName -q .tagName` equals `$TAG`), so a
    late run for an older version never rolls the server back, and
  - `github.repository == 'ramazangirgin/trading-analysis-platform'`, so a fork's releases do not
    try to deploy (a fork has no `production` environment).

  The job's `permissions` gain `actions: write` (start the Deploy workflow), with a comment like the
  existing ones. The step prints the Deploy run's link (`gh run list --workflow deploy.yml -L 1`
  is racy; a plain message "started Deploy for $TAG, see the Actions tab" is enough).

If the dispatch itself fails, the Release run is red; rerunning Release then does nothing (the
release exists), so the README says to start Deploy by hand for that tag.

### The Deploy workflow

```
Release run ──gh workflow run deploy.yml -f tag=vX.Y.Z──▶ Deploy (environment production, one at a time)
                                                          checkout vX.Y.Z → mise (Ansible) →
                                                          key, known_hosts, inventory in $RUNNER_TEMP →
                                                          mise run server:deploy -- -i … -e app_ref=vX.Y.Z
                                                            -e public_domain=… -e acme_email=…
                                                          → the playbook checks health on the server,
                                                            the version (/actuator/info) and HTTPS from
                                                            the runner
```

- `run-name: Deploy ${{ inputs.tag }}`.
- Top-level `permissions: contents: read` (checkout, `gh release view`); nothing else.
- One job, `deploy` (name *Deploy to production*), `runs-on: ubuntu-24.04`, `timeout-minutes: 90`
  (the image build on the arm64 VM runs inside Ansible's `async: 3600`),
  `environment: { name: production, url: "https://${{ vars.PUBLIC_DOMAIN }}" }`.
- **Approval**: the `production` environment has the developer as required reviewer. The job waits
  in *Waiting* until it is approved on the run's page (GitHub also notifies the reviewer); nothing
  of the job runs and no secret is handed out before that. `timeout-minutes` counts only from the
  approval; an unapproved deploy waits up to GitHub's limit of 30 days, then fails. A rejected
  deploy ends the run as failed and changes nothing on the server.
- `concurrency: { group: deploy-production, cancel-in-progress: false }`: deploys run one at a time
  and a running one is never cancelled. GitHub keeps one pending run per group and replaces it with
  a newer one, so of several releases queued behind a running deploy only the newest is deployed
  next; Release dispatches in release order (it runs one at a time itself), so the server always
  ends on the newest release. This also holds for runs waiting for approval: a deploy still waiting
  when a newer release is dispatched is cancelled and replaced by the newer one, so the developer
  approves only the newest. The header comment says so.
- Steps:
  1. **Check the tag**: `TAG` from `inputs.tag` via `env:`. It must match
     `^v[0-9]+\.[0-9]+\.[0-9]+$` and `gh release view "$TAG"` must succeed (only released tags are
     deployed, never a branch or an arbitrary commit). It also fails with a clear message when
     `vars.PUBLIC_DOMAIN`, `secrets.ACME_EMAIL`, `secrets.DEPLOY_HOST`, `secrets.DEPLOY_SSH_KEY` or
     `secrets.DEPLOY_KNOWN_HOSTS` is empty: without the domain and the e-mail the playbook would
     write a `deploy/.env` without the Caddy lines and HTTPS would break.
  2. `actions/checkout@v7` with `ref: ${{ inputs.tag }}`: the playbook of the deployed release, as a
     hand deploy from that tag would use.
  3. `jdx/mise-action@v5` (Ansible from that tag's `mise.toml`, as the *Server deploy (Ansible)*
     job of `ci.yml`).
  4. **SSH key, host key and inventory**: written under `$RUNNER_TEMP` from `env:` with `umask 077`
     and `printf '%s\n'`, never echoed and never with `set -x`:
     - `$RUNNER_TEMP/deploy-key` from `DEPLOY_SSH_KEY`;
     - `$RUNNER_TEMP/known_hosts` from `DEPLOY_KNOWN_HOSTS`;
     - `$RUNNER_TEMP/inventory.ini`, the shape of `inventory.ini.example`:
       `[server]` / `platform ansible_host=<DEPLOY_HOST> ansible_user=<vars.DEPLOY_USER or deploy>
       ansible_ssh_private_key_file=$RUNNER_TEMP/deploy-key`, plus `ansible_port=<vars.DEPLOY_PORT>`
       when that variable is set.
  5. **Deploy**: `mise run server:deploy -- -i "$RUNNER_TEMP/inventory.ini" -e app_ref="$TAG"
     -e public_domain="$PUBLIC_DOMAIN" -e acme_email="$ACME_EMAIL"`, with
     `ANSIBLE_SSH_ARGS="-o ControlMaster=auto -o ControlPersist=60s -o StrictHostKeyChecking=yes
     -o UserKnownHostsFile=$RUNNER_TEMP/known_hosts"` in its environment. That environment variable
     replaces `ssh_args` of `deploy/server/ansible.cfg` (which says `accept-new`) for this run only:
     the server's key must match the pinned one, an unknown or changed key stops the deploy.
     A failed task fails the step and the workflow; Ansible's own output says which task.
  6. **Summary** (`if: always()`): the tag, the result and the URL into `$GITHUB_STEP_SUMMARY`.
  7. **Remove the key** (`if: always()`): `rm -f` of the three files (the runner is thrown away
     anyway; this keeps a re-used self-hosted runner from keeping it, should one ever run this).
- No secret reaches the log: secrets are masked by GitHub, are only passed through `env:`, and the
  playbook's only task that prints the inventory host (*Show how to open the platform*) prints the
  masked value. `ACME_EMAIL` is a secret rather than a variable because the repository is public and
  a variable's value appears unmasked in the step's `env:` listing.

### The version check (in the playbook)

`/actuator/info` (`build.version`) is open on the server's `127.0.0.1:8080` but answers 404 through
Caddy (`deploy/Caddyfile`), and should stay so. So the version is checked where the health is,
on the server, by a new task of `roles/app/tasks/main.yml` after *Wait for the platform to be
healthy*:

- **Check the version the platform reports**: only `when: app_ref is match('^v[0-9]+\.[0-9]+\.[0-9]+$')`;
  `ansible.builtin.uri` of `http://127.0.0.1:{{ platform_port }}/actuator/info` with
  `return_content: true`, `changed_when: false`, failing with a message naming both versions when
  `json.build.version` is not `app_ref[1:]`.

Together with the existing *Wait for the platform to answer over HTTPS* (run on the controller,
here the runner, against `https://{{ public_domain }}/actuator/health`), a green deploy proves that
https://analystock.app answers and that the server runs the released version. A hand deploy of a
tag gets the same check. Rolling back to a tag from before this change runs that tag's playbook,
which has no version check; the HTTPS check still runs.

### One-time setup (documented, not automated)

In `deploy/server/README.md`, a new section *Automatic deploys on release* with the steps, using the
existing scripts:

1. A key for GitHub only, without a passphrase (the runner cannot type one):
   `DEPLOY_KEY=~/.ssh/platform-deploy-github deploy/server/setup-key.sh --no-passphrase`.
2. Add it to the deploy user next to the existing one: `deploy/server/setup-remote.sh <you>@<server>`
   with that key (`setup-server.sh` appends, it does not replace), or on the console. Revoking it is
   deleting its line from `/home/deploy/.ssh/authorized_keys`.
3. The host key to pin, from your own `known_hosts`, which your deploys already verified:
   `ssh-keygen -F <server>` (with a port: `ssh-keygen -F '[<server>]:<port>'`); compare its
   fingerprint with `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` on the server.
4. The environment and its values with `gh` (or Settings → Environments): create `production`,
   add yourself under *Required reviewers* (every deploy then waits for your approval; leave
   *Prevent self-review* off, since you start the releases yourself), restrict its deployment
   branches to `main`, then
   `gh secret set DEPLOY_SSH_KEY --env production < ~/.ssh/platform-deploy-github`,
   `gh secret set DEPLOY_HOST --env production`, `gh secret set DEPLOY_KNOWN_HOSTS --env production`,
   `gh secret set ACME_EMAIL --env production`, `gh variable set PUBLIC_DOMAIN --env production
   --body analystock.app`, and optionally `DEPLOY_USER`, `DEPLOY_PORT`.
5. SSH must reach the VM from GitHub's runners: the OCI security list must allow TCP 22 from
   `0.0.0.0/0` (GitHub-hosted runners have no fixed addresses; analystock.app's does). With
   key-only SSH on (`setup-remote.sh … --key-only-ssh`) that is the usual setup.

| Name | Kind | Value |
|---|---|---|
| `DEPLOY_SSH_KEY` | secret | The private key of step 1 |
| `DEPLOY_HOST` | secret | The server's IP address or DNS name |
| `DEPLOY_KNOWN_HOSTS` | secret | The server's host key line(s) of step 3 |
| `ACME_EMAIL` | secret | The Let's Encrypt contact address |
| `PUBLIC_DOMAIN` | variable | `analystock.app` |
| `DEPLOY_USER`, `DEPLOY_PORT` | variables, optional | `deploy` and 22 when unset |

The environment's secrets are only handed to jobs that name `environment: production` and run from
`main`, so a pull request's workflow cannot read them.

## Work packages

WP1, WP2 and WP3 have no dependency between them; WP4 documents all three.

### WP1: Version check in the playbook

- **Status**: done
- **Depends on**: none
- **Files**: `deploy/server/roles/app/tasks/main.yml`, `deploy/server/test-deploy.sh`
- **Steps**:
  - [x] Add *Check the version the platform reports* after *Wait for the platform to be healthy*, as
        in the Design.
  - [x] In `test-deploy.sh`, also push the second commit as tag `v$(scripts/version.sh)` (annotated
        or light, in the throwaway bare repository only) and deploy that tag instead of the branch
        `server-test-2` in the "third" deploy, so the end-to-end test runs the version check against
        a tag; the header list of what it checks names it. The first and second deploys stay on the
        branch (no check, `changed=0` unchanged).
- **Tests**: `mise run server:check` (ansible-lint production profile, syntax); CI's *Server deploy
  end-to-end* job (`mise run server:test`) deploys the tag and passes only when the reported version
  equals the tag.

### WP2: The Deploy workflow

- **Status**: done
- **Depends on**: none
- **Files**: `.github/workflows/deploy.yml` (new)
- **Steps**:
  - [x] Write the workflow as in the Design: header comment (what, why `workflow_dispatch` only, the
        approval of the `production` environment, the concurrency behaviour), `workflow_dispatch` with input `tag`, `run-name`, `permissions`,
        `concurrency`, the `production` environment, the steps 1 to 7.
- **Tests**: no workflow test harness exists. CI's *Server deploy (Ansible)* job runs because
  `.github/` changed (it checks the playbook, not the workflow). The proof is the first real run:
  after merging, the release of this pull request is deployed by it (see Tests).

### WP3: Release starts the deploy

- **Status**: done
- **Depends on**: none (WP2 must be in the same pull request, which it is)
- **Files**: `.github/workflows/release.yml`
- **Steps**:
  - [x] Add `actions: write` to the `release` job's `permissions`, with a one-line reason.
  - [x] Add the step *Deploy the release* after *GitHub Release*, with the three conditions of the
        Design, `TAG` through `env:`.
  - [x] Extend the header comment: a new release is then deployed by `deploy.yml`.
- **Tests**: as WP2: the first release after the merge.

### WP4: Docs and version

- **Status**: done
- **Depends on**: WP1, WP2, WP3
- **Files**: `deploy/server/README.md`, `README.md`,
  `docs/coding-convention/repository-versioning-and-releases.md`, `docs/agentic-development.md`,
  `gradle.properties`, `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`
- **Steps**:
  - [x] `deploy/server/README.md`: the new section *Automatic deploys on release* (how it is
        triggered, that each deploy waits for the required reviewer's approval and where to give
        it, the one-time setup and the table above, re-running a failed deploy and rolling
        back from the Actions tab or with `gh workflow run deploy.yml --ref main -f tag=vX.Y.Z`,
        and the rollback caveat below); a line in *Upgrading* pointing to it; *Secrets* table rows
        for the GitHub key and the environment; *Troubleshooting* entries for a host key mismatch
        (`Host key verification failed`: the server was recreated, update `DEPLOY_KNOWN_HOSTS`) and
        an SSH timeout from the runner (the security list).
  - [x] Rollback caveat in that section: migrations only go forward; an older release on a database a
        newer one migrated may refuse to start, then restore the dump taken before the upgrade
        (*Backups*).
  - [x] `README.md` *Versioning and releases*: after the release paragraph, one sentence that a new
        release is deployed to analystock.app by `deploy.yml`, linking the deploy README section.
  - [x] `repository-versioning-and-releases.md`: the *Release* row says it starts *Deploy* for a
        newly created latest release; a new *Deploy* row (`deploy.yml`: the tag to the server in the
        `production` environment, version checked at `/actuator/info`); the "Only the workflow's
        `GITHUB_TOKEN`" sentence notes that the deploy additionally uses the environment's secrets.
  - [x] `docs/agentic-development.md` step 10: the release is then deployed.
  - [x] `mise run version:bump minor`.
- **Tests**: `mise run check` (the version files in sync).

## Tests

- `mise run server:check` and CI's *Server deploy (Ansible)* and *Server deploy end-to-end* jobs: the
  playbook stays lint-clean and idempotent, and the version check passes on a tag deploy.
- `mise run check`: version sync and the rest of the quick check.
- The issue's "done when" can only be proven on the real server: set up the `production`
  environment (README steps) **before** merging this pull request; merging it raises the version,
  Release creates `vX.Y.Z` and starts Deploy, which waits for approval; the developer approves it,
  and the Deploy run ends green with the version check and the HTTPS check passed. Then start Deploy
  by hand with the previous tag and the new one again (approving each) to prove re-run and
  rollback. If the environment is not set up yet, that first Deploy run fails
  at *Check the tag* with a message naming the missing values, and is re-run by hand after the
  setup.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `deploy/server/README.md`, new section "Automatic deploys on release"; "Upgrading", "Secrets", "Troubleshooting" | Trigger, the approval each deploy waits for, one-time setup (key, host key, environment with its required reviewer, secrets and variables, SSH reachability), re-run and rollback, the migration caveat |
| Text | `README.md`, "Versioning and releases" | A new release is deployed automatically; link to the section above |
| Text | `docs/coding-convention/repository-versioning-and-releases.md`, "Where it is checked and used" and "Why" | Release starts Deploy; the Deploy row; the environment's secrets |
| Text | `docs/agentic-development.md`, step 10 | The release is deployed after it is published |
| Screenshot | none | No page of the UI changes |

## Out of scope

- The OCI security list for TCP 80, TCP 443 and UDP 443 (already set up), and changing the TCP 22
  rule (a manual check in the setup steps).
- A self-hosted runner, a VPN or the OCI Bastion service as the route to SSH: TCP 22 is open to
  GitHub-hosted runners.
- Publishing images to GHCR so the server stops building them: the deploy keeps building on the
  server as `server:deploy` does.
- Exposing `/actuator/info` through Caddy.

## Open questions

None. The developer's answers on the plan's review, recorded as decisions:

- **Required reviewer on `production`**: yes, the developer. Every deploy waits for one approval
  click (see Design, *Approval*); this departs from the issue's "no manual step" by choice.
- **SSH from GitHub-hosted runners**: TCP 22 is open to them on the OCI security list; no other
  route is needed.
- **`ACME_EMAIL`**: stays a secret.
