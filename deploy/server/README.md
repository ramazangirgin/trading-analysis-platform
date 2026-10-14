# Deployment to a server over SSH

Ansible sets up an Ubuntu server you can reach over SSH, from any provider (a cloud VM, a home
server), and starts the [Docker Compose setup](../docker-compose.yml) on it unchanged. By hand you
only make an SSH key, create a deploy user on the server once, and write an inventory file naming
the server ([Steps](#steps)); the rest is `mise run server:deploy`.

```
your machine ──ssh -L 8080──▶ server: 127.0.0.1:8080 ──▶ Compose stack
http://127.0.0.1:8080          (only SSH is open)          (platform, postgres, socket proxy)
```

The UI listens on the server's 127.0.0.1 only, and the firewall lets nothing but SSH in. You open
the UI through an SSH tunnel, which also encrypts it (the platform has a login but no HTTPS yet).

| Command | What |
|---|---|
| `mise run server:deploy` | Set up the server and deploy the platform; run it again to change nothing, or with `-e` to change something |
| `mise run server:check` | Check the code without a server (what CI runs) |

## What the server needs

- **Ubuntu 24.04**, amd64 or arm64: a cloud VM, a rented server or a machine at home.
- **4 GB of memory** or more: the images are built on the server, and every analysis runs in a
  container of its own (2 GB each). The deploy adds a 4 GB swap file.
- **Outgoing internet**, for the packages, the repository and the LLM providers.
- **Incoming SSH** (TCP 22, or the port you set) allowed by the provider's firewall or security
  group, if it has one. No other incoming port is needed.
- **A way in, once**, to create the deploy user below: an SSH login you already have (with a
  password or a key, as `root` or as a user who can `sudo`), or the provider's web or serial
  console, or a keyboard and screen on the machine.

## Steps

The deploy logs in as a user of its own, `deploy`, with a key made for it and passwordless `sudo`.
You set that user up once; after that everything goes through `mise run server:deploy`. `<server>`
below is the server's IP address or DNS name.

### With the scripts

Four commands, from the repository's folder on your computer. Each script says what it did and
what comes next, and is safe to run again; `--help` explains its options.

1. **Make the key** (A):

   ```sh
   deploy/server/setup-key.sh
   ```

   It makes `~/.ssh/platform-deploy` unless it exists (it asks for an optional passphrase), and
   prints the public key.
2. **Set up the deploy user on the server** (B and C), whichever way you can reach it:
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
3. **Test the login and write the inventory** (D1 and D2):

   ```sh
   deploy/server/setup-inventory.sh <server>              # --port N if SSH is not on 22
   ```

   It logs in as `deploy` with the key, checks that `sudo` needs no password, and writes
   `deploy/server/inventory.ini` (gitignored).
4. **Deploy** (D3):

   ```sh
   mise run server:deploy
   ```

   The first run takes several minutes. At the end it prints the tunnel command to open the UI
   ([Open the platform](#open-the-platform)).

Optional, once step 3 works: **key-only SSH** (passwords and `root` logins off; see
[below](#optional-key-only-ssh) first): `deploy/server/setup-remote.sh <you>@<server> --key-only-ssh`,
or `sudo bash setup-server.sh '<key>' --key-only-ssh` on the console.

### By hand

What the scripts do, step by step, if you want to see or do it yourself.

#### A. On your computer: make the key

1. Make a key pair for the deploy. Press Enter twice for no passphrase, or type one (then step D1
   asks for it, or add the key to your agent with `ssh-add ~/.ssh/platform-deploy`):

   ```sh
   ssh-keygen -t ed25519 -C "platform-deploy" -f ~/.ssh/platform-deploy
   ```

   This makes `~/.ssh/platform-deploy` (the private key: it never leaves your computer) and
   `~/.ssh/platform-deploy.pub` (the public key, which goes on the server).
2. Show the public key and copy the whole line (it starts with `ssh-ed25519`):

   ```sh
   cat ~/.ssh/platform-deploy.pub
   ```

#### B. On the server: get a shell, whichever way you can

- **You can log in over SSH already** (as `ubuntu`, `root` or any user who can `sudo`, with a
  password or a key): `ssh <you>@<server>`.
- **You cannot**: open the provider's web or serial console, or use the machine's own keyboard and
  screen, and log in there.
- **A home server without SSH**: install and start it first:

  ```sh
  sudo apt-get update && sudo apt-get install -y openssh-server
  sudo systemctl enable --now ssh
  ```

If you are logged in as `root`, leave out `sudo` in the commands below (or keep it: `root` may run
`sudo` too).

#### C. On the server: create the deploy user

1. Create the user, with no password (it logs in with the key only):

   ```sh
   sudo adduser --disabled-password --gecos "" deploy
   ```

2. Let it use `sudo` without a password: the deploy runs unattended and cannot type one. `visudo
   -c` checks the file, so a typo cannot break `sudo`:

   ```sh
   echo 'deploy ALL=(ALL) NOPASSWD:ALL' | sudo tee /etc/sudoers.d/90-deploy
   sudo chmod 440 /etc/sudoers.d/90-deploy
   sudo visudo -cf /etc/sudoers.d/90-deploy
   ```

3. Put your public key in its `authorized_keys`. Paste the line from A2 between the quotes:

   ```sh
   sudo install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
   echo 'ssh-ed25519 AAAA... platform-deploy' | sudo tee /home/deploy/.ssh/authorized_keys
   sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
   sudo chmod 600 /home/deploy/.ssh/authorized_keys
   ```

   SSH ignores the file if the folder or the file can be written by anyone but `deploy`, hence the
   modes.

4. Note the SSH port if it is not 22: `sudo ss -tlnp | grep sshd`.

You can log out of the server now. Everything else is done from your computer.

#### D. On your computer: test the login, then deploy

1. Log in as `deploy` with the new key, and check that `sudo` needs no password. The first time,
   SSH asks whether to trust the server's host key: answer `yes`.

   ```sh
   ssh -i ~/.ssh/platform-deploy deploy@<server> 'sudo -n true && echo "deploy user ok"'
   ```

   It must print `deploy user ok`. Add `-p <port>` if SSH is not on 22. If it does not, see
   [Troubleshooting](#troubleshooting) before going on.
2. Write the inventory, in this repository:

   ```sh
   cp deploy/server/inventory.ini.example deploy/server/inventory.ini
   ```

   Edit `deploy/server/inventory.ini` and set `ansible_host=` to `<server>`. The user (`deploy`)
   and the key (`~/.ssh/platform-deploy`) are already the example's; add `ansible_port=<port>` if
   SSH is not on 22. The file is gitignored.
3. Deploy:

   ```sh
   mise run server:deploy
   ```

   The first run takes several minutes. At the end it prints the tunnel command to open the UI
   ([Open the platform](#open-the-platform)).

The tools (`ansible-playbook`, `ansible-lint`) come from [`mise.toml`](../../mise.toml).

#### Optional: key-only SSH

Once D1 works, you can turn off password logins and direct `root` logins on the server, so only
keys get in. Keep the session you run this in open, and test a new login from another terminal
before you close it:

```sh
printf 'PasswordAuthentication no\nPermitRootLogin no\n' | sudo tee /etc/ssh/sshd_config.d/01-keys-only.conf
sudo sshd -t && sudo systemctl reload ssh
```

The name starts with `01-` on purpose: `sshd` keeps the first value it reads for a setting, and the
files in `sshd_config.d/` are read in name order, so a later one (such as the cloud images'
`50-cloud-init.conf`) cannot turn passwords back on. `sudo sshd -T | grep -i passwordauthentication`
shows the value in effect.

Do not do this if you log in as `root` or with a password yourself and have no key for that user.

## What the deploy does

The playbook ([`site.yml`](site.yml)) runs four roles as root:

| Role | What |
|---|---|
| `base` | A full package upgrade on the first deploy (later ones: `-e upgrade_packages=true`), automatic daily security updates (no automatic reboot), a swap file, the `ufw` firewall (deny incoming, the SSH port allowed) |
| `docker` | Docker from its own apt repository, and the `platform` user in the `docker` group |
| `app` | mise, the repository in `/opt/trading-analysis-platform` at `app_ref`, `deploy/.env` (the database password is generated on the server and never leaves it), the Docker images built on the server, `docker compose up`, and a wait for `/actuator/health` |
| `backup` | A daily `pg_dump` (see below) |

Every task is idempotent: a second `server:deploy` on an unchanged server reports `changed=0`. The
first one takes several minutes, since the images are built on the server.

## Open the platform

At the end the deploy prints the tunnel command for your server. Keep it running while you use the
UI:

```sh
ssh -i ~/.ssh/platform-deploy -N -L 8080:127.0.0.1:8080 deploy@<server>
```

Then open **http://127.0.0.1:8080**. If 8080 is taken on your machine, pick another local port:
`-L 18080:127.0.0.1:8080` and http://127.0.0.1:18080.

**First login:** the user is `admin` (`platform_admin_username`); its password is generated on the
first start and logged once. On the server:

```sh
sudo -u platform docker compose -f /opt/trading-analysis-platform/deploy/docker-compose.yml logs platform
```

The log keeps the password only until the platform container is recreated (an upgrade, a rebuilt
image or a changed `.env`) or the log rotates. Log in before you upgrade; if the password is gone,
set `PLATFORM_ADMIN_PASSWORD` or reset the admin as the [main README](../../README.md#3-log-in)
describes.

Then add an API key under **Settings** as in the [main README](../../README.md#4-add-an-api-key).

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
| `backup_time`, `backup_keep_days` | `03:30`, `14` | Daily dump time (the server's time) and retention |

Another inventory file (a second server, say) is used with `mise run server:deploy -- -i <file>`.

## Upgrading

Deploy another ref; the images are rebuilt when the checked-out commit differs from the one they were built from
(so rerunning after a failed build builds again), and the stack is brought up
again with its data:

```sh
mise run server:deploy -- -e app_ref=v4.4.0
```

Read the main README's upgrade notes for the versions in between first.

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
| `mise run server:test` | a throwaway machine only | The whole deployment with that machine as the server, over SSH to `127.0.0.1` ([`test-deploy.sh`](test-deploy.sh)): the setup scripts, a deploy of the checked-out commit, a second deploy with `changed=0`, the UI through the tunnel, only SSH open, a backup, a deploy of another commit that rebuilds, key-only SSH. 20 to 30 minutes. |

CI runs the first two on every push (job *Server deploy (Ansible)*), and `server:test` on a fresh
runner when the deployment changed (job *Server deploy end-to-end*: `deploy/`, the ta-runner
Dockerfile, `mise.toml` or the CI workflow).

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
| API keys of the platform, the admin password | On the server, under `platform_data` (see the main README) |

## Troubleshooting

- **`Permission denied (publickey)`.** The server did not accept the key. On the server, check
  that `/home/deploy/.ssh/authorized_keys` holds the whole `.pub` line from A2 on one line, and the
  modes and owner of C3 (`sudo ls -la /home/deploy/.ssh`). On your computer, check that you pass
  the private key (`-i ~/.ssh/platform-deploy`, not the `.pub`). `ssh -v …` shows which keys it
  tries.
- **`Connection timed out` or `Connection refused`.** Nothing answers on the SSH port: the
  provider's firewall or security group does not allow it, the address is wrong, or (on a home
  server) `ssh` is not running (`sudo systemctl status ssh`).
- **`sudo: a password is required` in D1, or `Missing sudo password` in the deploy.** The sudoers
  file of C2 is missing or wrong: `sudo cat /etc/sudoers.d/90-deploy` must show
  `deploy ALL=(ALL) NOPASSWD:ALL`, and `sudo visudo -cf /etc/sudoers.d/90-deploy` must say
  `parsed OK`.
- **SSH is not on port 22.** Set `ansible_port` in the inventory; the firewall allows that port.
- **The build on the server fails or is slow.** It runs `mise run docker-build` there. On arm64,
  the [ta-runner image (arm64)](../../README.md#continuous-integration) CI job catches
  dependencies without arm64 wheels earlier. Retry the deploy; see `docker compose … logs` on the
  server.
- **The health wait times out.** The first start takes a minute or more; read the platform's log
  as above.
- **The page does not open.** Is the tunnel still running, and does it point at the right local
  port?
- **SSH asks about a host key.** The server was recreated on the same address: remove the old line
  from `~/.ssh/known_hosts`.
