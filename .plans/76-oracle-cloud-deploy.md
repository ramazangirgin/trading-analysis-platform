# Plan: Automated deployment to Oracle Cloud Always Free

- **Issue**: #76 (Automated deployment to Oracle Cloud Always Free with Terraform and Ansible)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md: a new
  feature; nothing in the app, its API, database or configuration changes)

## Goal

With an Oracle Cloud account, an OCI API key and a Tailscale auth key in place,
`mise run oracle:up && mise run oracle:deploy` creates an Always Free Ampere A1 VM and deploys the
Docker Compose setup (`deploy/docker-compose.yml`) on it, with no manual SSH. The UI is served at
`https://<hostname>.<tailnet>.ts.net` through `tailscale serve`, and no port but SSH is open on the
public IP (none after the optional SSH lockdown). Re-running `oracle:deploy` changes nothing when
nothing changed, and deploys another Git ref when one is given. The database is dumped daily on the
VM. `mise run oracle:destroy` removes it all. CI checks the Terraform and Ansible code without
cloud credentials, and builds the ta-runner image on arm64, so an arm64 break shows up before a
deploy.

The issue's reason for keeping the UI off the public internet still holds, although the login is
built now (#78, #85): HTTPS is not (README, "Exposing it beyond localhost"). `tailscale serve` gives
HTTPS on the tailnet.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| deploy (`deploy/oracle/`, new) | No convention document covers Terraform or Ansible yet. This plan sets the rules, and WP5 writes them into `docs/coding-convention/README.md`'s enforcement table: `terraform fmt` formatting, `terraform validate`, `ansible-lint` with the `production` profile, idempotent tasks (no `command`/`shell` without `creates:`, `changed_when:` or `when:`), pinned versions for every tool, provider, collection and image. Secrets are gitignored and come with a committed `*.example` (as `deploy/.env.example`). The existing Compose setup (`deploy/docker-compose.yml`, `deploy/.env.example`) stays as it is: the VM runs it unchanged. |
| mise (`mise.toml`) | Tools pinned in `[tools]` to a minor, like `uv = "0.12"` and `lefthook = "2.1"`, each with a comment. Releases at least 14 days old (`.github/renovate.json5`, `minimumReleaseAge`). Tasks with a `description` naming what they need. |
| CI (`.github/workflows/ci.yml`) | New jobs go into `ci-passed`'s `needs` and get a line in the header comment. Tools from `mise.toml` through `jdx/mise-action`. Actions pinned like the existing ones. No secrets or cloud credentials. |
| Renovate (`.github/renovate.json5`) | Every pinned dependency is tracked; the comment lists the managers in use. |
| Versioning | `docs/coding-convention/repository-versioning-and-releases.md`: `mise run version:bump minor`. |
| Docs | Root `README.md`, the plain style of the existing documents; a README of its own for the new part, as `build-logic/checkstyle-rules/README.md`. |

## Design

### Layout

```text
deploy/oracle/
  README.md                       one-time manual steps, up, deploy, upgrade, backup, teardown
  up.sh                           terraform init + apply with the retry loop (oracle:up)
  test-up.sh                      tests up.sh against a fake terraform (oracle:check, CI)
  terraform/
    versions.tf                   required_version, providers oracle/oci and hashicorp/local, pinned
    .terraform.lock.hcl           committed, hashes for linux_amd64, linux_arm64, darwin_arm64
    variables.tf, main.tf, network.tf, instance.tf, inventory.tf, outputs.tf
    inventory.ini.tftpl           template of the Ansible inventory
    terraform.tfvars.example      committed; terraform.tfvars is gitignored
  ansible/
    ansible.cfg                   inventory = inventory.ini, roles_path, ssh accept-new
    site.yml                      the playbook: base, docker, tailscale, app, backup
    group_vars/all.yml            the defaults below
    roles/{base,docker,tailscale,app,backup}/{tasks,handlers,templates,defaults}/...
```

The generated and secret files are gitignored in a `deploy/oracle/.gitignore`: `terraform.tfvars`,
`*.tfstate`, `*.tfstate.*`, `.terraform/`, `tfplan`, `ansible/inventory.ini`.

### Terraform (`deploy/oracle/terraform/`)

- **Providers**: `oracle/oci` and `hashicorp/local`, each pinned to one version (`= x.y.z`, the
  newest at least 14 days old) in `versions.tf`, with `.terraform.lock.hcl` committed
  (`terraform providers lock -platform=linux_amd64 -platform=linux_arm64 -platform=darwin_arm64`).
  Local state, gitignored. A remote backend stays out of scope (see below).
- **Variables** (`variables.tf`, every one described, `sensitive` where it applies): `tenancy_ocid`,
  `user_ocid`, `fingerprint`, `private_key_path`, `region`, `compartment_ocid` (defaults to the
  tenancy), `ssh_public_key_path`, `ssh_private_key_path` (written into the inventory),
  `availability_domain_index` (default 0), `ocpus` (4), `memory_in_gbs` (24),
  `boot_volume_size_in_gbs` (100), `instance_name` (`trading-analysis-platform`),
  `tailscale_hostname` (default `trading-analysis-platform`), `ssh_ingress_cidr` (`0.0.0.0/0`) and
  `ssh_public` (bool, default `true`). The variables check their input (`validation` blocks: 1 to
  4 OCPUs, 1 to 24 GB, 50 to 200 GB) so a value outside the Always Free limits fails at
  `terraform plan`, not on the bill.
- **Network** (`network.tf`): one VCN (`10.0.0.0/16`), one public subnet (`10.0.0.0/24`), an
  internet gateway, a route table with the default route through it, and a security list: all
  egress; ingress TCP 22 from `ssh_ingress_cidr` only when `ssh_public` is true (a `dynamic`
  block), plus ICMP type 3 code 4 (path MTU discovery, as in OCI's default list). Nothing else, so
  8080 and 443 are closed on the public IP whatever runs on the VM.
- **Instance** (`instance.tf`): `VM.Standard.A1.Flex` with `shape_config` from the variables, in the
  availability domain `availability_domain_index` of `oci_identity_availability_domains`. The image
  is the newest "Canonical Ubuntu" 24.04 image for that shape (`oci_core_images`, sorted by
  `TIMECREATED`), with `lifecycle { ignore_changes = [source_details[0].source_id] }` so a newer
  image does not replace the VM on the next `oracle:up`. A public IP on the VNIC; the SSH public key
  in `metadata.ssh_authorized_keys`.
- **Inventory** (`inventory.tf`): a `local_file` writes `../ansible/inventory.ini` (mode 0600) from
  `inventory.ini.tftpl`: one host `oracle`, `ansible_user=ubuntu`,
  `ansible_ssh_private_key_file`, `tailscale_hostname`, and `ansible_host` = the public IP while
  `ssh_public` is true, else `tailscale_hostname` (reached over the tailnet with MagicDNS).
- **Outputs** (`outputs.tf`): `public_ip`, `instance_id`, `ssh_command`, `ui_url_hint`
  (`https://<tailscale_hostname>.<your tailnet>.ts.net`).

### `oracle:up` and the retry loop (`deploy/oracle/up.sh`)

Bash, `set -euo pipefail`, like `deploy/smoke-test.sh`. It runs `terraform -chdir=… init -input=false`
once, then `terraform apply -input=false -auto-approve` in a loop. The output goes to the terminal
and to a temporary file. A failure whose output contains `Out of host capacity` (OCI's message for
A1 being sold out) is retried after `ORACLE_RETRY_INTERVAL` seconds (default 60), up to
`ORACLE_RETRY_ATTEMPTS` attempts (default 60, `0` for no limit), printing `attempt N of M`. Any
other failure stops at once with terraform's exit code. `TERRAFORM` (default `terraform`) names the
binary, so the test can put a fake one in. Extra arguments go to `apply` (`mise run oracle:up --
-var ocpus=2`). Applying without asking is on purpose: the loop cannot answer a prompt on every
attempt. The README says so and points to `terraform plan` (`mise run oracle:plan`) first.

### Ansible (`deploy/oracle/ansible/`)

The full `ansible` package (not `ansible-core`), so the collections it uses (`community.general`
for `ufw`, `community.docker` for `docker_compose_v2`) come with it, pinned through its version,
with no `requirements.yml`. Every task has a name, and every role is idempotent: a second run on an
unchanged VM reports `changed=0`. `site.yml` runs the roles in order, as root (`become: true`):

| Role | What |
|---|---|
| `base` | `apt` upgrade (dist) with cache; `unattended-upgrades` on (security updates, no automatic reboot); a swap file (`swap_size`, default 4G, `fallocate` with `creates:`, in `/etc/fstab`, `vm.swappiness=10`); the firewall. Oracle's Ubuntu images ship `netfilter-persistent` with their own iptables rules, which fight `ufw`: the role removes `iptables-persistent`/`netfilter-persistent` and their saved rules, then enables `ufw` with default deny incoming, allow outgoing, `allow 22/tcp` (only `in on tailscale0` when `ssh_over_tailnet_only`) and `allow in on tailscale0`. Docker publishes 8080 on 127.0.0.1 only (`deploy/docker-compose.yml`), so Docker's own iptables rules open nothing. |
| `docker` | Docker's apt repository for Ubuntu `noble`/arm64 (`deb822_repository` with Docker's signing key), `docker-ce`, `docker-ce-cli`, `containerd.io`, `docker-buildx-plugin`, `docker-compose-plugin`; the service enabled; the deploy user (`app_user`, default `platform`) created and added to the `docker` group. |
| `tailscale` | Tailscale's apt repository and package; `tailscaled` enabled. `tailscale up --auth-key=… --hostname={{ tailscale_hostname }}` only when `tailscale status --json` says the node is not logged in (`BackendState` is not `Running`), so the auth key is needed on the first deploy only, from the environment variable `TAILSCALE_AUTHKEY` (`lookup('env', …)`, `no_log: true`; a clear failure when it is needed and missing). Then `tailscale serve --bg --https=443 http://127.0.0.1:{{ platform_port }}`, only when `tailscale serve status --json` does not already show that mapping. |
| `app` | `mise` from its apt repository (mise.jdx.dev); the repository cloned or updated in `app_dir` (default `/opt/trading-analysis-platform`) at `app_ref` (default `main`) with `ansible.builtin.git` as `app_user`; `deploy/.env` written from a template, mode 0600: `PLATFORM_DATA` (`platform_data`, default `/srv/trading-analysis-platform`), `POSTGRES_PASSWORD` (generated once on the VM with `openssl rand -hex 32` into `/etc/trading-analysis-platform/postgres-password`, 0600, `creates:`, then read back with `slurp`; never leaves the VM), `PLATFORM_PORT`, `PLATFORM_ADMIN_USERNAME`, `DOCKER_SOCKET`, and the image tags. Then `mise trust`, `mise install` and `mise run docker-build` as `app_user` in `app_dir`, only when the checkout changed or an image is missing (`docker image inspect`). `community.docker.docker_compose_v2` (`state: present`, project `deploy/`), then `uri` on `http://127.0.0.1:{{ platform_port }}/actuator/health` until `UP` (retries, about 5 minutes). The last task prints the UI URL, and on a first start where to find the generated admin password (`docker compose -f deploy/docker-compose.yml logs platform`). |
| `backup` | `/usr/local/bin/trading-analysis-platform-backup` from a template: `docker compose … exec -T postgres pg_dump -U platform platform`, gzipped to `{{ platform_data }}/backups/platform-<date>.sql.gz` (0600, `app_user`), and dumps older than `backup_keep_days` (14) deleted. A daily cron entry (`ansible.builtin.cron`, `backup_time`, default 03:30) as `app_user`. Object Storage stays out of scope. |

`group_vars/all.yml` holds the defaults: `app_user`, `app_dir`, `app_repo`
(`https://github.com/ramazangirgin/trading-analysis-platform.git`), `app_ref`, `platform_data`,
`platform_port` (8080), `platform_admin_username` (`admin`), `swap_size`, `ssh_over_tailnet_only`
(`false`), `backup_keep_days`, `backup_time`. Each is overridden with `-e`
(`mise run oracle:deploy -- -e app_ref=v4.3.0`). No Ansible Vault: the only secret the controller
holds is the Tailscale auth key, and an environment variable keeps it out of every file.
`deploy/oracle/ansible/.env.example` (not read by anything; `cp` and `source` it, or use any secret
manager) shows `TAILSCALE_AUTHKEY=tskey-auth-…`.

### The optional SSH lockdown

After a first deploy, with Tailscale up: set `ssh_public = false` in `terraform.tfvars`, run
`mise run oracle:up` (the security list drops port 22, the inventory now points at the tailnet
hostname), then `mise run oracle:deploy -- -e ssh_over_tailnet_only=true` (ufw allows SSH on
`tailscale0` only). The README describes it, with the way back (`ssh_public = true`, `oracle:up`)
and the condition: the controller must be on the same tailnet.

### mise (`mise.toml`)

- `[tools]`: `terraform` (aqua backend), `ansible` (pypi backend: the full package) and
  `"pipx:ansible-lint"`, each pinned to a minor (or major for `ansible`, whose major pins
  `ansible-core`'s minor), at the newest release at least 14 days old, with a comment that they
  serve `deploy/oracle/` only. mise-action's cache keeps them in CI.
- Tasks:

  | Task | Runs |
  |---|---|
  | `oracle:plan` | `terraform -chdir=deploy/oracle/terraform init -input=false` and `plan` |
  | `oracle:up` | `deploy/oracle/up.sh` |
  | `oracle:deploy` | `ansible-playbook site.yml` in `deploy/oracle/ansible` (extra arguments passed on) |
  | `oracle:destroy` | `terraform -chdir=deploy/oracle/terraform destroy` (it asks before destroying) |
  | `oracle:check` | `terraform fmt -check -recursive deploy/oracle/terraform`, `init -backend=false` and `validate`, `ansible-lint` in `deploy/oracle/ansible`, `ansible-playbook --syntax-check site.yml` (with a throwaway inventory), `deploy/oracle/test-up.sh`. No credentials needed. |

`oracle:check` is not part of `mise run check`: `terraform init` downloads the OCI provider
(about 100 MB) and `check` stays the quick check. CI runs it in a job of its own.

### CI (`.github/workflows/ci.yml`)

- New job `oracle`, **Oracle deploy (Terraform, Ansible)**, on `ubuntu-24.04`, 10 minutes:
  checkout, `jdx/mise-action@v5`, a cache of `deploy/oracle/terraform/.terraform/providers` keyed on
  `.terraform.lock.hcl`, `mise run oracle:check`.
- New job `runner-image-arm64`, **ta-runner image (arm64)**, on `ubuntu-24.04-arm` (free for this
  public repository): builds `artifact/ta-runner` (`docker/build-push-action@v7`, `load: true`,
  `cache-from/to: type=gha,scope=ta-runner-arm64`) and runs the same lockdown checks as
  `runner-image` (`version`, `catalog`, the label). It does not build the platform image: the JRE
  image is multi-arch and the jar is plain Java. This is the issue's arm64 risk ("some TradingAgents
  Python dependencies may lack arm64 wheels") made a check, before anyone deploys.
- Both go into `ci-passed`'s `needs` and into the header comment.

### Renovate (`.github/renovate.json5`)

`config:recommended` already enables the `terraform` manager (the providers in `versions.tf` and
the lock file) and the `mise` manager (the new tools). The managers comment at the top gains
`terraform`. No new rule.

## Work packages

### WP1: Terraform and `oracle:up`

- **Depends on**: none
- **Files**: `deploy/oracle/terraform/*` (as in the layout, lock file included),
  `deploy/oracle/up.sh`, `deploy/oracle/test-up.sh`, `deploy/oracle/.gitignore`
- **Steps**:
  - [ ] Write `versions.tf`, `variables.tf` (with validations), `main.tf` (provider, availability
        domains, image data source), `network.tf`, `instance.tf`, `inventory.tf`,
        `inventory.ini.tftpl`, `outputs.tf`, `terraform.tfvars.example` (every variable, with a
        comment on where to find its value in the OCI console)
  - [ ] Generate and commit `.terraform.lock.hcl` for the three platforms
  - [ ] Write `up.sh` with the retry loop as designed
  - [ ] Write `test-up.sh`: a fake `terraform` (a script in a temporary directory, its behaviour set
        through a counter file) and `ORACLE_RETRY_INTERVAL=0`
- **Tests**: `test-up.sh` proves that: a first-time success runs `apply` once; two
  `Out of host capacity` failures then a success run it three times and exit 0; a different error
  exits at once with its code; `ORACLE_RETRY_ATTEMPTS=2` gives up after two capacity failures with
  a non-zero exit; extra arguments reach `apply`. `terraform fmt -check` and `terraform validate`
  pass.

### WP2: Ansible roles and playbook

- **Depends on**: none (the inventory format is fixed above)
- **Files**: `deploy/oracle/ansible/ansible.cfg`, `site.yml`, `group_vars/all.yml`, `.env.example`,
  `roles/base/**`, `roles/docker/**`, `roles/tailscale/**`, `roles/app/**`, `roles/backup/**`
- **Steps**:
  - [ ] The five roles as designed, with handlers for service restarts, each task named, no bare
        `command`/`shell` without `creates:`, `changed_when:` or `when:`
  - [ ] `site.yml` and `group_vars/all.yml`
  - [ ] `.env.example` with the Tailscale auth key
- **Tests**: `ansible-lint` (profile `production`) passes with no skips beyond those named and
  explained in `.ansible-lint`; `ansible-playbook --syntax-check site.yml` passes. Running the roles
  against a real VM (idempotency: a second run gives `changed=0`) needs an Oracle account and is
  the developer's acceptance step, described in the pull request.

### WP3: mise tools and tasks

- **Depends on**: WP1, WP2 (the tasks run their files)
- **Files**: `mise.toml`
- **Steps**:
  - [ ] Pin `terraform`, `ansible` and `pipx:ansible-lint` in `[tools]`
  - [ ] Add `oracle:plan`, `oracle:up`, `oracle:deploy`, `oracle:destroy`, `oracle:check`
- **Tests**: `mise run oracle:check` passes locally; `mise tasks` lists the five tasks.

### WP4: CI and Renovate

- **Depends on**: WP3
- **Files**: `.github/workflows/ci.yml`, `.github/renovate.json5`
- **Steps**:
  - [ ] Jobs `oracle` and `runner-image-arm64` as designed, in `ci-passed`'s `needs` and the header
        comment
  - [ ] The managers comment in `renovate.json5`
- **Tests**: CI on the pull request: both new jobs green, **CI passed** green.

### WP5: Docs and version

- **Depends on**: WP1, WP2, WP3, WP4 (it describes them)
- **Files**: `deploy/oracle/README.md`, `README.md`, `docs/coding-convention/README.md`,
  `gradle.properties`, `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`
- **Steps**:
  - [ ] `deploy/oracle/README.md` (see "Docs to update")
  - [ ] The root README and the convention README
  - [ ] `mise run version:bump minor`
- **Tests**: the links resolve (relative paths checked by hand); `scripts/version.sh check-sync`.

## Tests

- `mise run oracle:check`: Terraform formatting and validation, `ansible-lint`, the playbook's
  syntax, and `test-up.sh` for the retry loop. CI runs it in the `oracle` job.
- `runner-image-arm64`: the ta-runner image builds and passes its lockdown checks on arm64, the
  VM's architecture.
- `mise run check` and the existing CI jobs stay green (nothing in the app changes).
- The issue's acceptance criteria that need a real Oracle account (a fresh `oracle:up` +
  `oracle:deploy` reaching `https://<vm>.<tailnet>.ts.net`, a second `oracle:deploy` with
  `changed=0`, another `app_ref`, port 8080 closed from the internet, e.g. `nc -vz <public ip>
  8080` failing) cannot run in CI. The pull request lists them as the developer's manual check.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `deploy/oracle/README.md` (new) | What it builds (a diagram like the Compose one: tailnet → `tailscale serve` → 127.0.0.1:8080 → the Compose stack). One-time manual steps: the Oracle account and the home region choice (it cannot be changed; A1 capacity varies by region), upgrading to Pay As You Go to avoid idle reclamation of Always Free VMs (still free within the limits), the OCI API key (`~/.oci/`, fingerprint, OCIDs into `terraform.tfvars`), an SSH key, Tailscale: MagicDNS and HTTPS certificates on in the admin console, a reusable or one-off pre-approved auth key in `TAILSCALE_AUTHKEY`. Then `oracle:plan`, `oracle:up` (the retry loop and its variables; that it applies without asking), `oracle:deploy`, the first login (the generated admin password), upgrading (`-e app_ref=…`), backups (where, how long, restoring with `psql`), the SSH lockdown and its way back, `oracle:destroy`, troubleshooting (capacity, Tailscale not logged in, the build on the VM). The secrets list: what is gitignored, what stays on the VM. |
| Text | `README.md`: the artifact table | A row "Oracle Cloud deployment \| `deploy/oracle/` \| Terraform, Ansible, Tailscale". |
| Text | `README.md`, "Run with Docker Compose" | A pointer to `deploy/oracle/README.md` for a free cloud VM, and in the "Exposing it beyond localhost" note: over Tailscale, as the Oracle setup does. |
| Text | `README.md`, "Tasks" | The five `oracle:*` tasks. |
| Text | `README.md`, "Continuous integration" table | Rows for **Oracle deploy (Terraform, Ansible)** and **ta-runner image (arm64)**. |
| Text | `docs/coding-convention/README.md`, "How the conventions are enforced" | A row for the deploy part: `terraform fmt`/`validate` and `ansible-lint` (production profile), configured in `deploy/oracle/`, run by `mise run oracle:check` and the CI job `oracle`, no Git hook (the provider download is too slow). |
| Screenshot | none | The platform's pages do not change. |

## Out of scope

- Multi-arch images pushed to GHCR by the release workflow, so the VM pulls instead of building
  (issue: "Out of scope / later"). Once that exists, the `app` role's build step becomes a pull.
- A public URL and HTTPS outside the tailnet (e.g. Cloudflare Tunnel + Access).
- Remote Terraform state in OCI Object Storage, and backups to Object Storage (both "optional" in
  the issue): a follow-up issue if wanted.
- Running the playbook against a real VM in CI (it needs cloud credentials).
- The native service setup (#48).

## Open questions

None left: answered when the plan was approved (see the question with the plan).
