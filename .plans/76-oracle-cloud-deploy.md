# Plan: Deploy to any server over SSH with Ansible

- **Issue**: #76 (Automated deployment to Oracle Cloud Always Free with Terraform and Ansible)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md: a new
  feature; nothing in the app, its API, database or configuration changes)

## Goal

Given a server with SSH access, `mise run server:deploy` sets it up and deploys the Docker Compose
setup (`deploy/docker-compose.yml`) on it, with no manual step on the server. The server can come
from any provider (a cloud VM, a home server). The only things to prepare are an SSH key pair, a
deploy user created once on the server, and an inventory file naming the server. Re-running `server:deploy` changes nothing when nothing
changed, and deploys another Git ref when one is given. The database is dumped daily on the
server. The UI stays on the server's 127.0.0.1 and is opened through an SSH tunnel, so no port but
SSH is open. CI checks the Ansible code, and builds the ta-runner image on arm64, so arm64 servers
are covered too.

**Scope change, decided with the developer after the first review round.** The issue asks for
Terraform creating an Oracle Cloud VM, and Tailscale to reach the UI. Both are left out: creating
the server is the provider's business (one console click, or any tool), and an SSH tunnel reaches
the UI without another account. What stays is the provider-independent part: setting up a server
over SSH. The login exists (#78, #85) but HTTPS does not, which is why the UI is not published on
a public port.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| deploy (`deploy/server/`, new) | No convention document covers Ansible yet. This plan sets the rules, and WP4 writes them into `docs/coding-convention/README.md`'s enforcement table: `ansible-lint` with the `production` profile, idempotent tasks (no `command`/`shell` without `creates:`, `changed_when:` or `when:`), pinned versions for every tool and collection. Machine-specific files are gitignored and come with a committed `*.example` (as `deploy/.env.example`). The existing Compose setup (`deploy/docker-compose.yml`, `deploy/.env.example`) stays as it is: the server runs it unchanged. |
| mise (`mise.toml`) | Tools pinned in `[tools]` to a minor, like `uv = "0.12"` and `lefthook = "2.1"`, each with a comment. Releases at least 14 days old (`.github/renovate.json5`, `minimumReleaseAge`). Tasks with a `description` naming what they need. |
| CI (`.github/workflows/ci.yml`) | New jobs go into `ci-passed`'s `needs` and get a line in the header comment. Tools from `mise.toml` through `jdx/mise-action`. Actions pinned like the existing ones. No secrets. |
| Renovate (`.github/renovate.json5`) | Every pinned dependency is tracked. |
| Versioning | `docs/coding-convention/repository-versioning-and-releases.md`: `mise run version:bump minor`. |
| Docs | Root `README.md`, the plain style of the existing documents; a README of its own for the new part, as `build-logic/checkstyle-rules/README.md`. |

## Design

### Layout

```text
deploy/server/
  README.md                 what the server needs, the inventory, deploy, the tunnel, upgrade,
                            backup and restore, troubleshooting
  .gitignore                inventory.ini
  ansible.cfg               inventory = inventory.ini, roles_path, ssh accept-new, pipelining
  .ansible-lint             production profile, offline
  inventory.ini.example     committed; inventory.ini is gitignored
  site.yml                  the playbook: base, docker, app, backup
  group_vars/all.yml        the defaults below
  roles/{base,docker,app,backup}/{tasks,handlers,templates,defaults}/...
```

### What the server needs

Ubuntu 24.04 (amd64 or arm64), with incoming SSH allowed, and 4 GB of memory at least (the image
build and the analysis containers; the swap file helps). The deploy logs in as a dedicated user,
`deploy`, with a key of its own and passwordless `sudo`. The README walks through setting it up
from scratch, step by step: on the developer's computer, `ssh-keygen` for a new key; on the
server, reached once over an existing SSH login or the provider's console, `adduser`, a
`/etc/sudoers.d/90-deploy` file checked with `visudo -c`, and the public key in
`/home/deploy/.ssh/authorized_keys` with the modes SSH requires; then a login test from the
developer's computer (`ssh … 'sudo -n true'`) before the first deploy. Turning off password logins
is an optional step. The playbook itself does not create the deploy user: it needs it to log in.

### The inventory

`deploy/server/inventory.ini.example`, copied to `inventory.ini` and edited: one group `server`
with one host, `ansible_host`, `ansible_user`, `ansible_ssh_private_key_file` and optionally
`ansible_port`. Another file is used with `mise run server:deploy -- -i <file>`.

### Ansible (`deploy/server/`)

The tools come from `mise.toml`: `pipx:ansible-core` and `pipx:ansible-lint`, each installed with
the full `ansible` package next to it (`uvx_args = "--with ansible~=13.0"`), so the collections the
roles use (`community.general` for `ufw`, `community.docker` for `docker_compose_v2`,
`ansible.posix`) come with it, with no `requirements.yml`. (The full package as the tool itself
does not expose `ansible-playbook`.) Every task has a name, and every role is idempotent: a second
run on an unchanged server reports `changed=0`. `site.yml` runs the roles in order, as root
(`become: true`), on the group `server`:

| Role | What |
|---|---|
| `base` | `apt` upgrade (dist) with cache on the first deploy, and later only with `upgrade_packages=true` (a marker in `/var/lib/trading-analysis-platform`), so a rerun changes nothing; `unattended-upgrades` on (security updates, no automatic reboot); a swap file (`swap_size`, default 4G, `fallocate` with `creates:`, in `/etc/fstab`, `vm.swappiness=10`); `ufw` with default deny incoming, allow outgoing, and the SSH port allowed (`ansible_port`, default 22, so a server with SSH on another port does not lock itself out). Docker publishes 8080 on 127.0.0.1 only (`deploy/docker-compose.yml`), so Docker's own iptables rules open nothing. |
| `docker` | Docker's apt repository for Ubuntu `noble` and the server's architecture (`deb822_repository` with Docker's signing key), `docker-ce`, `docker-ce-cli`, `containerd.io`, `docker-buildx-plugin`, `docker-compose-plugin`, log rotation in `daemon.json`; the service enabled; the deploy user (`app_user`, default `platform`) created and added to the `docker` group. |
| `app` | `mise` from its apt repository; the repository cloned or updated in `app_dir` (default `/opt/trading-analysis-platform`) at `app_ref` (default `main`) as `app_user`; `deploy/.env` written from a template, mode 0600: `PLATFORM_DATA` (`platform_data`, default `/srv/trading-analysis-platform`), `POSTGRES_PASSWORD` (generated once on the server with `openssl rand -hex 32` into `/etc/trading-analysis-platform/postgres-password`, 0600, `creates:`, then read back with `slurp`; never leaves the server), `PLATFORM_PORT`, `PLATFORM_ADMIN_USERNAME`, `DOCKER_SOCKET`, and the image tags. Then `mise trust`, `mise install java` (the build needs the JDK only) and `mise run docker-build` as `app_user`, only when the checkout changed or an image is missing. `community.docker.docker_compose_v2`, then a wait on `/actuator/health` until `UP`. The last task prints the SSH tunnel command for this server (from the inventory) and where to find the generated admin password. |
| `backup` | `/usr/local/bin/trading-analysis-platform-backup` from a template: `pg_dump` through `docker compose … exec -T postgres`, gzipped to `{{ platform_data }}/backups/platform-<date>.sql.gz` (0600, `app_user`), and dumps older than `backup_keep_days` (14) deleted. A daily cron entry (`backup_time`, default 03:30) as `app_user`. |

`group_vars/all.yml` holds the defaults: `app_user`, `app_dir`, `app_repo`
(`https://github.com/ramazangirgin/trading-analysis-platform.git`), `app_ref`, `platform_data`,
`platform_port` (8080), `platform_admin_username` (`admin`), `swap_size`, `backup_keep_days`,
`backup_time`. Each is overridden with `-e` (`mise run server:deploy -- -e app_ref=v4.4.0`). No
secret is needed on the controller beyond the SSH key.

### Opening the UI

`ssh -i <key> -N -L 8080:127.0.0.1:8080 <user>@<host>` on the controller, then
http://127.0.0.1:8080. The traffic goes through SSH, so it is encrypted. The README says how to
choose another local port, and that HTTPS and a public URL are out of scope.

### The setup scripts and the tests (`deploy/server/`)

Added with the developer after review round 2, so the README's setup steps are commands the
developer runs and the tests run too:

| Script | Runs on | Does |
|---|---|---|
| `setup-key.sh [--no-passphrase]` | the developer's computer | Step A: `~/.ssh/platform-deploy` (`DEPLOY_KEY`) unless it exists; prints the public key and the next step |
| `setup-server.sh '<public key>' [--key-only-ssh]` | the server, as root | Steps B and C: the SSH server when missing, the user `deploy` (`DEPLOY_USER`) without a password, `/etc/sudoers.d/90-deploy` checked with `visudo -c` before it is installed, the key in `authorized_keys` (700/600, owned by the user, added once). `--key-only-ssh` writes `sshd_config.d/01-keys-only.conf` (first in name order, so it wins), checks it with `sshd -t` and reloads. Idempotent. |
| `setup-remote.sh <you>@<server> [--key-only-ssh] [ssh options]` | the developer's computer | Steps B and C over an existing SSH login: copies `setup-server.sh` over and runs it with `sudo` (one reused connection, so one password prompt) |
| `setup-inventory.sh <server> [--port N] [--inventory FILE] [--force]` | the developer's computer | Steps D1 and D2: logs in as `deploy` with the key only and runs `sudo -n true`, then writes `inventory.ini` (kept unless `--force` or unchanged) |
| `test-setup.sh` (`mise run server:test-setup`) | the developer's computer or CI, with Docker or Podman | The four setup scripts against a throwaway Ubuntu 24.04 container (no systemd: `systemctl` is a no-op, `sshd` started by hand); checks the modes, a second run changing nothing, refused input, a sudo password caught, key-only SSH |
| `test-deploy.sh` (`mise run server:test`) | a throwaway machine only (refuses unless `CI=true` or `SERVER_TEST_THROWAWAY=1`) | The README end to end with that machine as the server, over SSH to 127.0.0.1: the setup scripts, a deploy of the checked-out commit from a local bare repository, a second deploy with `changed=0`, the UI through the tunnel, 8080 on 127.0.0.1 only and `ufw` allowing SSH only, a backup dump, a deploy of another commit that rebuilds (`built-commit`), key-only SSH |

### The container engine (`scripts/container-engine.sh`)

Asked for by the developer while adding the tests: `mise run setup` installs a container engine
when none works, since `run`, `dev`, the backend tests, the images and `server:test-setup` need one.
An engine that works (`docker info` or `podman info`) is left alone. Otherwise: Docker from the
distribution's packages on Ubuntu or Debian (`docker.io`, `docker-compose-v2`, `docker-buildx`, the
user in the `docker` group), Podman and a machine sharing the home folder on macOS with Homebrew,
and instructions elsewhere. `PLATFORM_INSTALL_ENGINE=0` skips the install; CI never installs. It
reports what to do when an installed engine does not work (daemon stopped, user not in the
group), and starts a stopped Podman machine. It never fails the setup.

### mise (`mise.toml`)

- `[tools]`: `"pipx:ansible-core"` and `"pipx:ansible-lint"`, each pinned to a minor with
  `uvx_args = "--with ansible~=13.0"`, with a comment that they serve `deploy/server/` only.
- Tasks:

  | Task | Runs |
  |---|---|
  | `server:deploy` | `ansible-playbook site.yml` in `deploy/server` (extra arguments passed on) |
  | `server:check` | `ansible-lint` and `ansible-playbook -i inventory.ini.example --syntax-check site.yml` in `deploy/server`. No server and no credentials needed. |
  | `server:test-setup` | `deploy/server/test-setup.sh` |
  | `server:test` | `deploy/server/test-deploy.sh` (throwaway machines only) |
  | `container-engine` | `scripts/container-engine.sh`; also the last step of `setup` |

`server:check` is not part of `mise run check`: installing the Ansible tools takes a while the
first time, and `check` stays the quick check. CI runs it in a job of its own.

### CI (`.github/workflows/ci.yml`)

- New job `server`, **Server deploy (Ansible)**, on `ubuntu-24.04`, 10 minutes: checkout,
  `jdx/mise-action@v5`, `mise run server:check`.
- New job `runner-image-arm64`, **ta-runner image (arm64)**, on `ubuntu-24.04-arm`: builds
  `artifact/ta-runner` and runs the same lockdown checks as `runner-image`, so an arm64 server
  can build the image (some TradingAgents dependencies might lack arm64 wheels).
- The `server` job also runs `mise run server:test-setup` (Docker is on the runner).
- New job `server-e2e`, **Server deploy end-to-end**, 60 minutes: `mise run server:test` on the
  runner, only when `deploy/`, `artifact/ta-runner/Dockerfile`, `mise.toml` or `ci.yml` changed
  against the pull request's base (or `main`, or the push's previous commit; always on demand). A
  first step works that out with `git diff`; without a change the job passes in seconds.
- All three go into `ci-passed`'s `needs` and into the header comment.

### Renovate (`.github/renovate.json5`)

The `mise` manager tracks the two tools. A custom regex manager tracks the `ansible~=` range in
`uvx_args`, which the mise manager does not read; `ansible-core` follows the `ansible` package by
hand (a package rule turns its own updates off and a note on the `ansible` update says so), since
each `ansible` release requires one `ansible-core` minor.

## Work packages

### WP1: Ansible roles and playbook

- **Status**: done
- **Depends on**: none
- **Files**: `deploy/server/ansible.cfg`, `.ansible-lint`, `.gitignore`, `inventory.ini.example`,
  `site.yml`, `group_vars/all.yml`, `roles/base/**`, `roles/docker/**`, `roles/app/**`,
  `roles/backup/**`
- **Steps**:
  - [x] The four roles as designed, with handlers for service restarts, each task named, no bare
        `command`/`shell` without `creates:`, `changed_when:` or `when:`
  - [x] `site.yml`, `group_vars/all.yml`, `inventory.ini.example`
- **Tests**: `ansible-lint` (profile `production`) passes with no skips; `ansible-playbook
  --syntax-check site.yml` passes. Running the playbook against a real server (a second run gives
  `changed=0`) is the developer's acceptance step, described in the pull request.

### WP2: mise tools and tasks

- **Status**: done
- **Depends on**: WP1
- **Files**: `mise.toml`
- **Steps**:
  - [x] Pin `pipx:ansible-core` and `pipx:ansible-lint` in `[tools]`
  - [x] Add `server:deploy` and `server:check`
- **Tests**: `mise run server:check` passes locally; `mise tasks` lists both tasks.

### WP3: CI and Renovate

- **Status**: done
- **Depends on**: WP2
- **Files**: `.github/workflows/ci.yml`, `.github/renovate.json5`
- **Steps**:
  - [x] Jobs `server` and `runner-image-arm64` as designed, in `ci-passed`'s `needs` and the header
        comment
  - [x] The custom manager and package rules for `ansible` and `ansible-core`
- **Tests**: CI on the pull request: both new jobs green, **CI passed** green.

### WP4: Docs and version

- **Status**: done
- **Depends on**: WP1, WP2, WP3
- **Files**: `deploy/server/README.md`, `README.md`, `docs/coding-convention/README.md`,
  `gradle.properties`, `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`
- **Steps**:
  - [x] `deploy/server/README.md` (see "Docs to update")
  - [x] The root README and the convention README
  - [x] `mise run version:bump minor`
- **Tests**: the links resolve; `scripts/version.sh check-sync`.

### WP5: Setup scripts and their tests

- **Status**: done
- **Depends on**: WP1, WP2, WP3
- **Files**: `deploy/server/setup-key.sh`, `setup-server.sh`, `setup-remote.sh`,
  `setup-inventory.sh`, `test-setup.sh`, `test-deploy.sh`, `inventory.ini.example`, `mise.toml`,
  `.github/workflows/ci.yml`, `deploy/server/README.md`, `README.md`
- **Steps**:
  - [x] The four setup scripts, idempotent, each with `--help`
  - [x] `test-setup.sh` and `test-deploy.sh`, the tasks `server:test-setup` and `server:test`
  - [x] The `server` job runs `server:test-setup`; the job `server-e2e` runs `server:test` when the
        deployment changed
  - [x] The README's steps use the scripts, with the commands by hand below them; a Testing section
- **Tests**: `server:test-setup` and `server:test` themselves, in CI.

### WP6: A container engine in `mise run setup`

- **Status**: done
- **Depends on**: none
- **Files**: `scripts/container-engine.sh`, `mise.toml`, `README.md`
- **Steps**:
  - [x] `scripts/container-engine.sh` (check, install when none) and the task `container-engine`,
        run last by `setup`
  - [x] The README's prerequisites and task list
- **Tests**: `scripts/container-engine.sh check` reports the engine on the CI runners (the
  `server` job runs `server:test-setup`, which starts with it). The install paths need a machine
  without an engine and are the developer's manual check.

## Tests

- `mise run server:check`: `ansible-lint` and the playbook's syntax. CI runs it in the `server`
  job.
- `mise run server:test-setup`: the setup scripts against a throwaway container, in the `server`
  job.
- `mise run server:test`: the deployment end to end on the runner, in the `server-e2e` job when the
  deployment changed. It checks the acceptance steps CI can: `changed=0` on a second deploy, another
  ref, the tunnel, 8080 on 127.0.0.1 only.
- `runner-image-arm64`: the ta-runner image builds and passes its lockdown checks on arm64.
- `mise run check` and the existing CI jobs stay green (nothing in the app changes).
- What only a real server shows (a provider's console, a fresh image without the runner's tools,
  port 8080 closed from outside, e.g. `nc -vz <host> 8080` failing) stays the developer's manual
  check, listed in the pull request.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `deploy/server/README.md` (new) | What it does (a diagram: your machine → SSH tunnel → 127.0.0.1:8080 on the server → the Compose stack). What the server needs. The steps, split into the developer's computer and the server: the key, a way in to the server (an SSH login or a console), the deploy user with passwordless `sudo` and the key, a login test, the inventory. Optional key-only SSH. `server:deploy`, the first login (the generated admin password), the tunnel, the settings, upgrading (`-e app_ref=…`), backups (where, how long, restoring as `platform`, into an emptied database), troubleshooting (sudo password, SSH port, the build on the server, the health wait, a changed host key). What is secret and where it is kept. |
| Text | `README.md`: the artifact table | A row "Server deployment \| `deploy/server/` \| Ansible over SSH". |
| Text | `README.md`, "Run with Docker Compose" | A pointer to `deploy/server/README.md` for setting up a server over SSH, and in the "Exposing it beyond localhost" note: the SSH tunnel. |
| Text | `README.md`, "Tasks" | `server:deploy`, `server:check`, `server:test-setup`, `server:test` and `container-engine`; `setup` installs an engine when there is none. |
| Text | `README.md`, "Prerequisites" | Docker or Podman: `mise run setup` installs one when there is none. |
| Text | `README.md`, "Continuous integration" table | Rows for **Server deploy (Ansible)** (with the setup scripts' test), **Server deploy end-to-end** (when it runs) and **ta-runner image (arm64)**. |
| Text | `deploy/server/README.md`, "Steps" and "Testing" | The steps with the scripts first and by hand below them; the three test commands, what CI runs when, and a throwaway VM for `server:test`. |
| Text | `docs/coding-convention/README.md`, "How the conventions are enforced" | A row for the deploy part: `ansible-lint` (production profile), configured in `deploy/server/`, run by `mise run server:check` and the CI job `server`, no Git hook. |
| Screenshot | none | The platform's pages do not change. |

## Out of scope

- Creating the server (Terraform, a cloud provider's API): the issue's Oracle Cloud part. The
  server comes from the provider's console or any other tool.
- Tailscale, HTTPS and a public URL (e.g. Cloudflare Tunnel + Access).
- Multi-arch images pushed to GHCR, so the server pulls instead of building.
- Backups to object storage.
- Running the playbook against a real server in CI.
- The native service setup (#48).
