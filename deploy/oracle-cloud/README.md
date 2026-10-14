# Deployment to Oracle Cloud Always Free

Terraform creates an Always Free Ampere A1 VM in Oracle Cloud, Ansible installs Docker and Tailscale
on it and starts the [Docker Compose setup](../docker-compose.yml) unchanged. No manual SSH.

```
browser ──▶ https://<hostname>.<tailnet>.ts.net ──▶ tailscale serve ──▶ 127.0.0.1:8080 ──▶ Compose stack
            (your tailnet only)                      (on the VM)                           (platform, postgres,
                                                                                            socket proxy)
```

The UI is reachable from your tailnet only, with HTTPS from Tailscale. The VM's security list opens
SSH (TCP 22) and nothing else; after the [optional lockdown](#closing-ssh-to-the-internet) not even
that.

| Command | What |
|---|---|
| `mise run oracle-cloud:plan` | Show what `oracle-cloud:up` would create |
| `mise run oracle-cloud:up` | Create the VM and its network; writes `ansible/inventory.ini` |
| `mise run oracle-cloud:deploy` | Set up the VM and deploy the platform; run it again to change nothing, or with `-e` to change something |
| `mise run oracle-cloud:destroy` | Remove the VM and its network (asks first) |
| `mise run oracle-cloud:check` | Check the code without any credentials (what CI runs) |

## One-time steps

Done by hand, once:

1. **Oracle account.** Sign up at [oracle.com/cloud/free](https://www.oracle.com/cloud/free/). The
   **home region** you pick cannot be changed later, and the capacity of A1 shapes differs between
   regions: if one is always sold out, `oracle-cloud:up` keeps retrying (see below), but a quieter region
   would have saved you the wait.
2. **Stay on the Free Tier.** No upgrade is needed: everything here fits the Always Free limits
   (4 OCPUs, 24 GB memory, 200 GB of block storage in total, boot volumes included), and the
   variables refuse values above them. The price is that Oracle may reclaim an Always Free VM it
   finds idle, which this one often is between analyses (see
   [When Oracle reclaims the VM](#when-oracle-reclaims-the-vm)). Upgrading to Pay As You Go
   avoids that, but puts a card on file that anything outside the limits is charged to.
3. **OCI API key.** Profile menu > User settings > API keys > Add API key; download the private
   key to `~/.oci/` and keep the fingerprint. You also need the tenancy OCID and your user OCID.
4. **SSH key.** `ssh-keygen -t ed25519 -f ~/.ssh/oracle-cloud`.
5. **`terraform.tfvars`.** `cp deploy/oracle-cloud/terraform/terraform.tfvars.example
   deploy/oracle-cloud/terraform/terraform.tfvars` and fill it in; the example says where to find each
   value in the OCI console.
6. **Tailscale.** In the [admin console](https://login.tailscale.com/admin) turn on **MagicDNS**
   and **HTTPS Certificates** (DNS page), then create an auth key (Settings > Keys): one-off or
   reusable, pre-approved if your tailnet approves devices. The controller (your machine) joins
   the same tailnet only if you use the lockdown below.
7. **Auth key in the environment.** `export TAILSCALE_AUTHKEY=tskey-auth-…`, or `source` a copy of
   [`ansible/.env.example`](ansible/.env.example). It is needed on the first deploy only, and no
   file holds it.

Tools (`terraform`, `ansible`, `ansible-lint`) come from [`mise.toml`](../../mise.toml).

## Create the VM

```sh
mise run oracle-cloud:plan     # optional: what will be created
mise run oracle-cloud:up
```

`oracle-cloud:up` runs `terraform apply` **without asking**: the retry loop cannot answer a prompt on
every attempt. Read the plan first. When Oracle answers `Out of host capacity` (A1 is often sold
out), it waits and tries again; any other error stops it at once.

| Variable | Default | |
|---|---|---|
| `ORACLE_CLOUD_RETRY_INTERVAL` | `60` | Seconds between attempts |
| `ORACLE_CLOUD_RETRY_ATTEMPTS` | `60` | Attempts before giving up; `0` for no limit |
| `TERRAFORM` | `terraform` | The binary to run |

Arguments after `--` go to `terraform apply`: `mise run oracle-cloud:up -- -var ocpus=2`. The VM is
Ubuntu 24.04 on `VM.Standard.A1.Flex`; the state is local (`terraform/terraform.tfstate`, gitignored:
keep it, `oracle-cloud:destroy` needs it).

## Deploy

```sh
mise run oracle-cloud:deploy
```

The playbook ([`ansible/site.yml`](ansible/site.yml)) runs five roles as root:

| Role | What |
|---|---|
| `base` | Package upgrades, automatic security updates (no automatic reboot), a swap file, the `ufw` firewall (deny incoming, SSH allowed, everything on `tailscale0` allowed) |
| `docker` | Docker from its own apt repository, and the `platform` user in the `docker` group |
| `tailscale` | Tailscale, joined with `TAILSCALE_AUTHKEY` when the node is not logged in yet, then `tailscale serve` for HTTPS on 443 to `127.0.0.1:8080` |
| `app` | mise, the repository in `/opt/trading-analysis-platform` at `app_ref`, `deploy/.env` (the database password is generated on the VM and never leaves it), the Docker images built on the VM, `docker compose up`, and a wait for `/actuator/health` |
| `backup` | A daily `pg_dump` (see below) |

Every task is idempotent: a second `oracle-cloud:deploy` on an unchanged VM reports `changed=0`. The first
one takes several minutes (the images are built on the VM, on arm64).

At the end it prints the UI URL, `https://<hostname>.<your tailnet>.ts.net`. Open it from a machine
on the tailnet. **First login:** the user is `admin` (`platform_admin_username`); its password is
generated on the first start and logged once:

```sh
ssh -i ~/.ssh/oracle-cloud ubuntu@<public ip>     # the address is in the output of oracle-cloud:up
sudo -u platform docker compose -f /opt/trading-analysis-platform/deploy/docker-compose.yml logs platform
```

Then add an API key under **Settings** as in the [main README](../../README.md#4-add-an-api-key).

### Settings

Every default lives in [`ansible/group_vars/all.yml`](ansible/group_vars/all.yml) and is overridden
with `-e`:

```sh
mise run oracle-cloud:deploy -- -e app_ref=v4.3.0 -e platform_admin_username=me
```

| Variable | Default | |
|---|---|---|
| `app_ref` | `main` | Branch, tag or commit to deploy |
| `app_repo` | this repository | Git URL, e.g. a fork |
| `app_dir`, `app_user` | `/opt/trading-analysis-platform`, `platform` | Checkout and the user that runs it |
| `platform_data` | `/srv/trading-analysis-platform` | Database, run logs, reports and backups |
| `platform_port` | `8080` | The port on 127.0.0.1 |
| `platform_admin_username` | `admin` | The first admin |
| `swap_size` | `4G` | Made once; a later change does not resize it |
| `ssh_over_tailnet_only` | `false` | See the lockdown below |
| `backup_time`, `backup_keep_days` | `03:30`, `14` | Daily dump time (VM time) and retention |

## Upgrading

Deploy another ref; the images are rebuilt when the checkout changed, and the stack is brought up
again with its data:

```sh
mise run oracle-cloud:deploy -- -e app_ref=v4.4.0
```

Read the main README's upgrade notes for the versions in between first.

## Backups

The `backup` role installs `/usr/local/bin/trading-analysis-platform-backup` and a daily cron entry
(`backup_time`) for `platform`. It writes `pg_dump` output, gzipped, to
`/srv/trading-analysis-platform/backups/platform-<date>.sql.gz` (owner-only) and deletes dumps
older than `backup_keep_days`. The dumps are on the VM's boot volume: they survive a restart, not
`oracle-cloud:destroy`. Copy them off the VM to keep them (`scp`).

To restore, log in over SSH as `ubuntu` and run everything as `platform`: only that user is in the
`docker` group and can read `deploy/.env` (the database password). A dump copied off with `scp` goes
into the backups directory first (`sudo install -o platform -g platform -m 600 platform-<date>.sql.gz
/srv/trading-analysis-platform/backups/`). The database is never empty once the platform has started
(the schema is migrated, the admin exists), so stop the platform, recreate the database, then load the
dump:

```sh
COMPOSE="sudo -u platform docker compose -f /opt/trading-analysis-platform/deploy/docker-compose.yml"
$COMPOSE stop platform
$COMPOSE exec -T postgres psql -U platform -d postgres \
  -c 'DROP DATABASE platform' -c 'CREATE DATABASE platform OWNER platform'
sudo -u platform zcat /srv/trading-analysis-platform/backups/platform-<date>.sql.gz \
  | $COMPOSE exec -T postgres psql -U platform platform
$COMPOSE start platform
```

The dump holds the database only; API keys and run logs are in `platform_data/platform/`.

## Closing SSH to the internet

After a first deploy, with Tailscale up, the VM can drop its public SSH port. The controller must be
on the same tailnet.

1. In `terraform.tfvars` set `ssh_public = false`.
2. `mise run oracle-cloud:up`: the security list drops port 22, and the inventory now points at the
   tailnet hostname (`tailscale_hostname`, resolved by MagicDNS).
3. `mise run oracle-cloud:deploy -- -e ssh_over_tailnet_only=true`: `ufw` allows SSH on `tailscale0` only.

Way back: `ssh_public = true` and `mise run oracle-cloud:up`, then `mise run oracle-cloud:deploy` (with
`ssh_over_tailnet_only` left `false`). If Tailscale is down on the VM, step 2 locks you out: set
`ssh_public = true` again, or use the OCI console's serial console.

## Remove everything

```sh
mise run oracle-cloud:destroy
```

It lists what it removes and asks. The VM, its boot volume (the database and the backups with it)
and the network are gone; the Tailscale device stays in the admin console until you delete it there.

## When Oracle reclaims the VM

On the Free Tier, Oracle may reclaim an Always Free VM that stayed idle for 7 days (low CPU,
network and memory use); it sends an email first. The platform is mostly idle between analyses, so
expect this, and keep a recent dump off the VM ([Backups](#backups)) if the data matters.

- **The VM is stopped:** start it in the OCI console (Compute > Instances > Start). The data is
  on its boot volume, and the stack comes back by itself (`restart: unless-stopped`).
- **The VM is gone:** create it again. Delete the old device in the Tailscale admin console first,
  or the new one gets another name (`trading-analysis-platform-1`) and the URL changes. Then:

  ```sh
  export TAILSCALE_AUTHKEY=tskey-auth-…   # a new node logs in again
  mise run oracle-cloud:up                # Terraform sees the VM missing and creates a new one
  mise run oracle-cloud:deploy
  ```

  The new VM starts empty: a new admin password, no API keys, no analyses. Restore a dump you
  copied off as in [Backups](#backups) (`scp` it to the VM first; the restore recreates the
  database the new platform already filled), and enter the API keys again.
  The public IP changes, so SSH asks about the host key once.

## Secrets

| What | Where |
|---|---|
| `terraform.tfvars`, the state (`*.tfstate`), `ansible/inventory.ini` | On your machine, gitignored ([`.gitignore`](.gitignore)); the committed examples show the shape |
| The OCI API key, the SSH key | On your machine, outside the repository (`~/.oci/`, `~/.ssh/`) |
| `TAILSCALE_AUTHKEY` | The environment of your shell during the first deploy only |
| The database password | Generated on the VM in `/etc/trading-analysis-platform/postgres-password` (owner-only), written to `deploy/.env` there; never leaves the VM |
| API keys of the platform, the admin password | On the VM, under `platform_data` (see the main README) |

## Troubleshooting

- **`Out of host capacity` forever.** A1 is sold out in your region. Let the loop run (it can take
  hours), lower `ocpus` and `memory_in_gbs`, or try another availability domain
  (`availability_domain_index`).
- **`TAILSCALE_AUTHKEY` missing.** The deploy stops with a message when the node is not logged in and
  the variable is empty. Export it and run `oracle-cloud:deploy` again; a key that expired needs a new one.
- **The page does not open.** Is the controller's device on the tailnet, and are MagicDNS and HTTPS
  certificates on? On the VM: `tailscale status` and `tailscale serve status`.
- **The build on the VM fails or is slow.** It runs `mise run docker-build` on arm64; the
  [ta-runner image (arm64)](../../README.md#continuous-integration) CI job catches dependencies
  without arm64 wheels earlier. Retry the deploy; see `docker compose … logs` on the VM.
- **The health wait times out.** The first start takes a minute or more; read
  `docker compose -f deploy/docker-compose.yml logs platform` as above.
- **SSH asks about a host key.** The VM was recreated on the same address: remove the old line from
  `~/.ssh/known_hosts`.
