# Deployment to a server over SSH

Ansible sets up an Ubuntu server you can reach over SSH, from any provider (a cloud VM, a home
server), and starts the [Docker Compose setup](../docker-compose.yml) on it unchanged. Once, three
scripts make an SSH key, set up a deploy user on the server and write an inventory file
([Steps](#steps)); the rest is `mise run server:deploy`.

```
your machine ──ssh -L 8080──▶ server: 127.0.0.1:8080 ──▶ Compose stack
http://127.0.0.1:8080          (only SSH is open)          (platform, postgres, socket proxy)
```

By default the UI listens on the server's 127.0.0.1 only, and the firewall lets nothing but SSH in.
You open the UI through an SSH tunnel, which also encrypts it. With a domain
(`-e public_domain=…`), Caddy also serves it over HTTPS on the internet
([HTTPS on a domain](#https-on-a-domain)):

```
browser ──https──▶ server: Caddy :443 ──▶ platform
```

| Command | What |
|---|---|
| `mise run server:deploy` | Set up the server and deploy the platform; run it again to change nothing, or with `-e` to change something |
| `mise run server:tunnel` | Open the UI from your computer: an SSH tunnel, then http://127.0.0.1:8080 |
| `mise run server:check` | Check the code without a server (what CI runs) |

## What the server needs

- **Ubuntu 24.04**, amd64 or arm64: a cloud VM, a rented server or a machine at home.
- **4 GB of memory** or more: the images are built on the server, and every analysis runs in a
  container of its own (2 GB each). The deploy adds a 4 GB swap file.
- **Outgoing internet**, for the packages, the repository and the LLM providers.
- **Incoming SSH** (TCP 22, or the port you set) allowed by the provider's firewall or security
  group, if it has one. No other incoming port is needed, except TCP 80, TCP 443 and UDP 443 for
  a [public domain](#https-on-a-domain).
- **A way in, once**, to set up the deploy user: an SSH login you already have (with a
  password or a key, as `root` or as a user who can `sudo`), or the provider's web or serial
  console, or a keyboard and screen on the machine.

## Steps

The deploy logs in as a user of its own, `deploy`, with a key made for it and passwordless `sudo`.
You set that user up once; after that everything goes through `mise run server:deploy`. `<server>`
below is the server's IP address or DNS name.

Four commands, from the repository's folder on your computer. Each script says what it did and
what comes next, and is safe to run again; `--help` explains its options.

1. **Make the key**:

   ```sh
   deploy/server/setup-key.sh
   ```

   It makes `~/.ssh/platform-deploy` unless it exists (it asks for an optional passphrase), and
   prints the public key.
2. **Set up the deploy user on the server**, whichever way you can reach it:
   - **You can log in over SSH** (as `root` or a user who can `sudo`; with a password or a key):

     ```sh
     deploy/server/setup-remote.sh <you>@<server>      # SSH options after it: -p 2222, -i ~/.ssh/other
     ```

     It copies [`setup-server.sh`](setup-server.sh) to the server and runs it with `sudo`, asking for
     your password once if you log in with one.
   - **You only have a console** (the provider's web or serial console, or a keyboard and screen):
     get `setup-server.sh` onto the server, then run it with the key line from step 1:

     ```sh
     curl -fsSLO https://raw.githubusercontent.com/ramazangirgin/trading-analysis-platform/main/deploy/server/setup-server.sh
     sudo bash setup-server.sh 'ssh-ed25519 AAAA... platform-deploy'
     ```

     Without internet on the server, paste the file into an editor there instead (`nano
     setup-server.sh`).

   It installs and starts the SSH server when there is none, creates `deploy` without a password,
   gives it passwordless `sudo` (checked with `visudo` first) and adds the key. It prints the SSH
   port.
3. **Test the login and write the inventory**:

   ```sh
   deploy/server/setup-inventory.sh <server>              # --port N if SSH is not on 22
   ```

   It logs in as `deploy` with the key, checks that `sudo` needs no password, and writes
   `deploy/server/inventory.ini` (gitignored).
4. **Deploy**:

   ```sh
   mise run server:deploy
   ```

   The first run takes several minutes, the image build on the server most of it. Then open the
   UI with `mise run server:tunnel` ([Open the platform](#open-the-platform)).

Optional, once step 3 works: **key-only SSH**, which turns password and `root` logins off for every
user: `deploy/server/setup-remote.sh <you>@<server> --key-only-ssh`, or `sudo bash setup-server.sh
'<key>' --key-only-ssh` on the console. Only if your own login uses a key too; keep your session
open until a new login works.

## What the deploy does

`mise run server:deploy` runs Ansible on your computer: it logs in to the server over SSH as
`deploy` and runs each task there, with `sudo`. Everything heavy happens on the server (the
packages, Docker, building the images, running the platform); your computer only needs Ansible,
which mise installs from [`mise.toml`](../../mise.toml) on first use, and sends the commands.

```
your computer: mise run server:deploy ──ssh deploy@<server>──▶ server: apt, Docker, git checkout,
               (ansible-playbook)       ◀── each task's result ──      mise run docker-build,
                                                                        docker compose up
```

The playbook ([`site.yml`](site.yml)) runs four roles as root:

| Role | What |
|---|---|
| `base` | A full package upgrade on the first deploy (later ones: `-e upgrade_packages=true`), automatic daily security updates (no automatic reboot), a swap file, the `ufw` firewall (deny incoming, the SSH port allowed; with `public_domain` also 80/tcp, 443/tcp and 443/udp, plus Oracle Cloud's iptables rules) |
| `docker` | Docker from its own apt repository, and the `platform` user in the `docker` group |
| `app` | mise, the repository in `/opt/trading-analysis-platform` at `app_ref`, `deploy/.env` (the database password is generated on the server and never leaves it), the Docker images built on the server, `docker compose up` (with the Caddy overlay when `public_domain` is set), and a wait for `/actuator/health` (with `public_domain`, also over HTTPS from your computer) |
| `backup` | A daily `pg_dump` (see below) |

Every task is idempotent: a second `server:deploy` on an unchanged server reports `changed=0`. The
first one takes several minutes, since the images are built on the server.

## Open the platform

The UI listens on the server's 127.0.0.1 only. From your computer:

```sh
mise run server:tunnel                    # 8080 taken here? mise run server:tunnel -- --port 18080
```

It opens an SSH tunnel to the server in `inventory.ini` and prints the address: open
**http://127.0.0.1:8080** in your browser. Keep it running while you use the UI; Ctrl+C closes it.

**First login:** the user is `admin` (`platform_admin_username`); its password is generated on the
first start and logged once. To read it from your computer:

```sh
ssh -i ~/.ssh/platform-deploy deploy@<server> \
  'sudo -u platform docker compose -f /opt/trading-analysis-platform/deploy/docker-compose.yml logs platform | grep "generated password"'
```

The log keeps the password only until the platform container is recreated (an upgrade, a rebuilt
image or a changed `.env`) or the log rotates. Log in before you upgrade; if the password is gone,
set `PLATFORM_ADMIN_PASSWORD` or reset the admin as the [user guide](../../docs/user-guide.md#3-log-in)
describes.

Then add an API key under **Settings** as in the [user guide](../../docs/user-guide.md#4-add-an-api-key).

### HTTPS on a domain

With a domain, the platform is also served on the internet: a Caddy container in front of it gets
and renews a Let's Encrypt certificate and redirects `http://` and `www.` to
`https://<domain>`. Without `public_domain` nothing of this happens.

Before going public:

1. **Set a strong admin password.** Log in through the tunnel first and change the generated one.
2. **DNS:** an `A` record for `@` and a `CNAME` for `www` pointing at the server.
3. **The provider's firewall:** allow incoming TCP 80, TCP 443 and UDP 443. On Oracle Cloud these
   are ingress rules in the subnet's security list.

Then deploy with the domain (without `www.`) and a contact address for Let's Encrypt:

```sh
mise run server:deploy -- -e public_domain=example.com -e acme_email=you@example.com
```

Besides the usual deploy, this:

- allows 80/tcp, 443/tcp and 443/udp in `ufw`, and on Oracle Cloud images also in iptables (the
  shipped rules end `INPUT` with a `REJECT` that comes before ufw's, so ufw alone opens nothing);
- writes `PLATFORM_DOMAIN`, `ACME_EMAIL` and `COMPOSE_FILE` to `deploy/.env`, so a plain `docker
  compose` in `deploy/` on the server includes the Caddy overlay
  ([`docker-compose.public.yml`](../docker-compose.public.yml));
- starts the stack with the overlay and waits until `https://<domain>/actuator/health` answers
  `UP` from your computer, which proves DNS, both firewalls and the certificate.

Of `/actuator/*`, only `health` is reachable through Caddy; the rest answers 404. The certificates
and the Let's Encrypt account are in `platform_data/caddy/`. The tunnel keeps working. To check:

```sh
curl -sI http://example.com                       # redirect to https
curl -sI https://www.example.com                  # redirect to https://example.com
curl -s -o /dev/null -w '%{http_code}' https://example.com/actuator/metrics   # 404
sudo ufw status
sudo iptables -L INPUT -n --line-numbers          # Oracle Cloud: the ACCEPT rules come before the REJECT
```

Deploying again without `public_domain` does not undo this: Caddy's container and the firewall
rules stay. To go back to tunnel-only, remove them by hand (`docker compose … down caddy`,
`ufw delete allow 80/tcp`, and so on, and the iptables lines on Oracle Cloud), and drop the three
lines from `deploy/.env`.

### Settings

Every default lives in [`group_vars/all.yml`](group_vars/all.yml) and is overridden with `-e`:

```sh
mise run server:deploy -- -e app_ref=v4.4.0 -e platform_admin_username=me
```

| Variable | Default | |
|---|---|---|
| `app_ref` | `main` | Branch, tag or commit to deploy |
| `app_repo` | this repository | Git URL, e.g. a fork |
| `app_dir`, `app_user` | `/opt/trading-analysis-platform`, `platform` | Checkout and the user that runs it |
| `platform_data` | `/srv/trading-analysis-platform` | Database, run logs, reports and backups |
| `platform_port` | `8080` | The port on the server's 127.0.0.1 |
| `platform_admin_username` | `admin` | The first admin |
| `swap_size` | `4G` | Made once; a later change does not resize it |
| `upgrade_packages` | `false` | `true`: upgrade every package again (`apt dist-upgrade`); the first deploy always does, security updates come daily anyway |
| `public_domain` | empty | A domain without `www.`: serve the platform on `https://<domain>` ([HTTPS on a domain](#https-on-a-domain)); empty: tunnel only |
| `acme_email` | empty | Contact address for Let's Encrypt; required with `public_domain` |
| `backup_time`, `backup_keep_days` | `03:30`, `14` | Daily dump time (the server's time) and retention |

Another inventory file (a second server, say) is used with `mise run server:deploy -- -i <file>`.

## Upgrading

Deploy another ref; the images are rebuilt when the checked-out commit differs from the one they were built from
(so rerunning after a failed build builds again), and the stack is brought up
again with its data:

```sh
mise run server:deploy -- -e app_ref=v4.4.0
```

Read the upgrade notes in [Docker Compose](../../docs/docker-compose.md) for the versions in between first.

## Backups

The `backup` role installs `/usr/local/bin/trading-analysis-platform-backup` and a daily cron entry
(`backup_time`) for `platform`. It writes `pg_dump` output, gzipped, to
`/srv/trading-analysis-platform/backups/platform-<date>.sql.gz` (owner-only) and deletes dumps
older than `backup_keep_days`. The dumps are on the server's disk: copy them off (`scp`) to keep
them when the server goes away.

To restore, log in over SSH and run everything as `platform`: only that user is in the `docker`
group and can read `deploy/.env` (the database password). A dump copied off with `scp` goes into
the backups directory first (`sudo install -o platform -g platform -m 600 platform-<date>.sql.gz
/srv/trading-analysis-platform/backups/`). The database is never empty once the platform has
started (the schema is migrated, the admin exists), so stop the platform, recreate the database,
then load the dump:

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

## Moving to another server

Deploy the new server as above, copy the latest dump over and restore it as in
[Backups](#backups), then enter the API keys again under **Settings**.

## Testing

| Command | Where | What |
|---|---|---|
| `mise run server:check` | anywhere | `ansible-lint` (production profile) and the playbook's syntax |
| `mise run server:test-setup` | your computer, with Docker or Podman | The setup scripts against a throwaway Ubuntu 24.04 container ([`test-setup.sh`](test-setup.sh)): key, deploy user, sudoers and modes, a second run that changes nothing, the login test, the inventory, a deploy user that needs a sudo password caught, key-only SSH. About a minute. |
| `mise run server:test` | a throwaway machine only | The whole deployment with that machine as the server, over SSH to `127.0.0.1` ([`test-deploy.sh`](test-deploy.sh)): the setup scripts, a deploy of the checked-out commit, a second deploy with `changed=0`, the UI through the tunnel, only SSH open, a backup, a deploy of another commit that rebuilds, key-only SSH. It skips the first deploy's package upgrade, which on a CI runner takes as long as the runner image has updates pending. 20 to 30 minutes. |

CI runs the first two when `deploy/`, `mise.toml` or `.github/` changed (job *Server deploy
(Ansible)*), and `server:test` on a fresh runner when the deployment changed (job *Server deploy
end-to-end*: `deploy/`, the ta-runner Dockerfile, the CI workflow or `scripts/ci-changes.sh`).

`server:test` changes the machine for good (users, sudoers, firewall, Docker, swap) and refuses to
start unless `CI=true` or `SERVER_TEST_THROWAWAY=1`. To run it yourself, use a VM you throw away
afterwards, for example with [Multipass](https://canonical.com/multipass):

```sh
multipass launch 24.04 --name platform-test --cpus 4 --memory 8G --disk 40G
multipass shell platform-test
# in the VM:
curl https://mise.run | sh && export PATH="$HOME/.local/bin:$PATH"
git clone https://github.com/ramazangirgin/trading-analysis-platform.git && cd trading-analysis-platform
mise trust && SERVER_TEST_THROWAWAY=1 mise run server:test
# afterwards, on your computer:
multipass delete --purge platform-test
```

## Secrets

| What | Where |
|---|---|
| `inventory.ini` | On your machine, gitignored ([`.gitignore`](.gitignore)); [`inventory.ini.example`](inventory.ini.example) shows the shape |
| The SSH key | On your machine, outside the repository (`~/.ssh/`) |
| The database password | Generated on the server in `/etc/trading-analysis-platform/postgres-password` (owner-only), written to `deploy/.env` there; never leaves the server |
| API keys of the platform, the admin password | On the server, under `platform_data` (see [Configuration](../../docs/configuration.md)) |

## Troubleshooting

- **mise fails to install `pipx:ansible-core` or `pipx:ansible-lint`** (on your computer, before
  any SSH). A dependency had no ready-made wheel for your machine and uv tried to compile it. For
  `cryptography` this is handled (`--no-build-package cryptography` in `mise.toml`: Intel Macs
  get 48.0.1, the last release with wheels for them). For another package, the error names it;
  `mise install` again after a `mise.toml` update, or tell us in an issue.
- **`Permission denied (publickey)`.** The server did not accept the key: run step 2 again with
  the key line from step 1 (it fixes the modes too). `ssh -v …` shows which keys it tries.
- **`Connection timed out` or `Connection refused`.** Nothing answers on the SSH port: the
  provider's firewall or security group does not allow it, the address is wrong, or (on a home
  server) `ssh` is not running (`sudo systemctl status ssh`).
- **`sudo asked for a password` in step 3, or `Missing sudo password` in the deploy.** The
  deploy user's sudoers file is missing or changed: run step 2 again.
- **SSH is not on port 22.** Set `ansible_port` in the inventory; the firewall allows that port.
- **The build on the server fails or is slow.** It runs `mise run docker-build` there. On arm64,
  the [ta-runner image (arm64)](../../docs/ci-and-releases.md#continuous-integration) CI job catches
  dependencies without arm64 wheels earlier. Retry the deploy; see `docker compose … logs` on the
  server.
- **The health wait times out.** The first start takes a minute or more; read the platform's log
  as above.
- **The page does not open.** Is `mise run server:tunnel` still running, without an error, and
  is the address the one it printed? On the server, `curl -s http://127.0.0.1:8080/actuator/health`
  must answer `{"status":"UP"}`.
- **The HTTPS health check fails** ("HTTPS on a domain"). The deploy waits about 5 minutes for
  `https://<domain>/actuator/health` from your computer. Check, in this order: DNS (`dig +short
  <domain>` must print the server's address), the provider's firewall (TCP 80, TCP 443, UDP 443),
  the server's (`sudo ufw status`), and Caddy's log, which shows certificate errors and Let's
  Encrypt rate limits: `sudo -u platform docker compose -f
  /opt/trading-analysis-platform/deploy/docker-compose.yml -f
  /opt/trading-analysis-platform/deploy/docker-compose.public.yml logs caddy`. Fix the cause and
  deploy again.
- **SSH asks about a host key.** The server was recreated on the same address: remove the old line
  from `~/.ssh/known_hosts`.
