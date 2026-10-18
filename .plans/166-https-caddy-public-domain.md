# Plan: HTTPS on the internet with Caddy for servers with a domain

- **Issue**: #166 (HTTPS on the internet with Caddy, the minimum: servers with a domain (analystock.app))
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

A server deployed with `deploy/server` and given a domain (`-e public_domain=analystock.app -e
acme_email=<mail>`) serves the platform on the internet over HTTPS: a Caddy container in front of
the platform gets and renews a Let's Encrypt certificate, redirects `http://` and `www.` to
`https://<domain>`, and hides every actuator endpoint but `/actuator/health`. The backend sees the
real client address behind the proxy, so the login rate limit stays per client, and its session and
CSRF cookies get the `Secure` flag over HTTPS. Without `public_domain` nothing changes: the server
stays tunnel-only, and local runs and the plain Compose run are as before.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| deploy (Compose) | `deploy/docker-compose.yml` stays unchanged; every image pinned to an exact version (Renovate's `docker-compose` manager tracks `deploy/docker-compose.public.yml`, its default file pattern matches it); machine-specific values only in the gitignored `deploy/.env`, documented in the committed `deploy/.env.example` |
| deploy (Ansible, `deploy/server`) | `docs/coding-convention/README.md` "Deploy": ansible-lint `production` profile, no rule skipped (named tasks, FQCN modules, idempotent tasks: no `command`/`shell` without `creates:`, `changed_when:` or `when:`), role-prefixed registered variables (`base_…`, `app_…`), defaults in `group_vars/all.yml` with a comment each; a second deploy reports `changed=0` |
| backend (`artifact/backend`, `bff/impl`) | `backend-java-package-structure.md`, `backend-java-checkstyle.md` (Javadoc), `backend-java-formatting.md` (Spotless); configuration in `artifact/backend/src/main/resources/application.properties` with a comment per setting; the Spring Boot HTTP tests live next to `AuthApiIntegrationTest` / `LoginLockoutIntegrationTest` |
| docs | Root `README.md`, `deploy/server/README.md`, in their plain style |

## Design

### Compose overlay and Caddy (`deploy/`)

- **`deploy/docker-compose.public.yml`**, used together with `docker-compose.yml` only when a domain
  is set (`docker compose -f docker-compose.yml -f docker-compose.public.yml …`). It adds one service:
  - `caddy`: `image: caddy:<exact 2.x version>` (the newest release at least 14 days old, as
    Renovate's `minimumReleaseAge`), `restart: unless-stopped`, ports `80:80`, `443:443` and
    `443:443/udp` (HTTP/3), environment `PLATFORM_DOMAIN` and `ACME_EMAIL` (both required with
    `${…:?set … in deploy/.env}`), volumes `./Caddyfile:/etc/caddy/Caddyfile:ro,z`,
    `${PLATFORM_DATA}/caddy/data:/data:z` (certificates and ACME account, kept when the container
    is recreated) and `${PLATFORM_DATA}/caddy/config:/config:z`, `depends_on: platform`
    (`service_started`), on the `backend` network so it reaches `platform:8080`.
  - `init-dirs` gets nothing new: Caddy runs as root in its image and Docker creates the bind-mount
    folders. (If the agent finds the folders missing on a fresh `PLATFORM_DATA`, add `/data/caddy`
    to the `mkdir -p` of `init-dirs` in the overlay by overriding its `entrypoint`; prefer the plain
    bind mount.)
  - The platform keeps publishing `127.0.0.1:${PLATFORM_PORT}:8080`, so the SSH tunnel still works on
    a public server.
- **`deploy/Caddyfile`**: the issue's config, with a global options block for the ACME email and a
  comment per block:

  ```
  {
      email {$ACME_EMAIL}
  }

  {$PLATFORM_DOMAIN} {
      @actuator path /actuator/*
      handle @actuator {
          @health path /actuator/health
          handle @health { reverse_proxy platform:8080 }
          respond 404
      }
      header Strict-Transport-Security "max-age=31536000"
      reverse_proxy platform:8080
  }

  www.{$PLATFORM_DOMAIN} {
      redir https://{$PLATFORM_DOMAIN}{uri} permanent
  }
  ```

  `/actuator/**` is `permitAll` in `SecurityConfig` and exposes `metrics` and `info`; only `health` is
  passed through. Caddy redirects HTTP to HTTPS by itself (automatic HTTPS). Its `reverse_proxy` sets
  `X-Forwarded-For` to the client address and `X-Forwarded-Proto: https`, and drops a client's own
  `X-Forwarded-*` headers (the client is not a trusted proxy of Caddy), so they cannot be spoofed
  through it.
- **`deploy/.env.example`**: a commented section "Public HTTPS (deploy/docker-compose.public.yml)"
  with `PLATFORM_DOMAIN=` and `ACME_EMAIL=`, what they do, that DNS must point at the machine and
  ports 80/443 be open first, and the `docker compose -f docker-compose.yml -f
  docker-compose.public.yml up -d` command. The `docker-compose.yml` header comment gets one line
  pointing at the overlay (the file's services and settings stay unchanged).

### Backend: the client address behind the proxy

- `application.properties`: `server.forward-headers-strategy=native`, with a comment: Tomcat's
  `RemoteIpValve` takes `X-Forwarded-For` and `X-Forwarded-Proto` only from an internal proxy address
  (Tomcat's default `internal-proxies`: 10/8, 172.16/12, 192.168/16, 169.254/16, 127/8 and the IPv6
  local ranges, which include the Compose network), so behind Caddy `getRemoteAddr()` is the client
  and `isSecure()` is true over HTTPS. Correct the comment above `server.address` ("requires HTTPS,
  which the platform does not have yet"): exposing it goes through the Caddy overlay.
- Nothing changes in `LoginRateLimitFilter`'s code: it keeps using `getRemoteAddr()`, which the valve
  now sets. Its Javadoc paragraph on the client address is rewritten: the address is
  `getRemoteAddr()`; behind a reverse proxy on an internal address (the Caddy overlay), Tomcat
  replaces it with the `X-Forwarded-For` client (`server.forward-headers-strategy=native`); from any
  other address `X-Forwarded-For` is ignored.
- The `Secure` flag needs no code: Tomcat marks the session cookie `Secure` when the request is
  secure, and `CookieCsrfTokenRepository` sets `Secure` from `request.isSecure()` unless told
  otherwise (`SecurityConfig.csrfTokenRepository()` only sets `SameSite` and the path). The tests
  below prove it.

### Server deploy (`deploy/server`)

- **`group_vars/all.yml`**: `public_domain: ""` (empty: tunnel only, as today) and `acme_email: ""`,
  with comments (the domain without `www.`; DNS and the provider's firewall first, see the README).
- **`site.yml`**: an assert in `pre_tasks` (after gathering facts) that `acme_email` is set when
  `public_domain` is, with a message naming both variables.
- **`base` role** (`roles/base/tasks/main.yml`), when `public_domain` is set, after "Allow SSH" and
  before the firewall is enabled:
  - ufw allows `80/tcp`, `443/tcp` and `443/udp` (`community.general.ufw`, a loop).
  - **Oracle Cloud's iptables rules**: `ansible.builtin.stat` of `/etc/iptables/rules.v4`; when it
    exists and has an `INPUT` `REJECT` line (read with `ansible.builtin.slurp` or a `lineinfile`
    in check mode; pick what keeps the task list short and lint-clean):
    - the live rules: `ansible.builtin.iptables` with `action: insert`, `chain: INPUT`,
      `protocol: tcp|udp`, `destination_port`, `ctstate: NEW`, `jump: ACCEPT`, one per port (the
      module checks for the rule first, so a rerun changes nothing; `insert` puts it first, ahead of
      the `REJECT`);
    - the saved rules: `ansible.builtin.lineinfile` in `/etc/iptables/rules.v4`,
      `insertbefore: '^-A INPUT -j REJECT'`, lines in the file's own style
      (`-A INPUT -p tcp -m state --state NEW -m tcp --dport 80 -j ACCEPT`, the same for 443/tcp and
      `-p udp … --dport 443`), so they survive a reboot.
    - **Never** `netfilter-persistent save` or `iptables-save` into that file: it would also save
      Docker's own chains, which Docker adds again at every start.
  - A comment says why: on Oracle Cloud Ubuntu images, the shipped rules end `INPUT` with a `REJECT`
    that comes before ufw's chains, so ufw alone opens nothing there.
  - Without `public_domain`, none of these tasks run (no rule removed either: going back from public
    to tunnel-only is not part of this issue, see "Out of scope").
- **`app` role**:
  - `templates/env.j2`: `PLATFORM_DOMAIN` and `ACME_EMAIL` when `public_domain` is set, and
    `COMPOSE_FILE=docker-compose.yml:docker-compose.public.yml`, so a plain `docker compose` run on
    the server in `deploy/` (and `-f`-less commands in the README) picks the overlay too. Without a
    domain the file is exactly as today (the second deploy of a tunnel-only server stays
    `changed=0`, and its `.env` is not rewritten).
  - "Start the Compose stack": `files` is `docker-compose.yml` plus `docker-compose.public.yml` when
    `public_domain` is set. The Caddyfile is in the checkout (`deploy/Caddyfile`) and mounted from
    there, so nothing is copied.
  - "Wait for the platform to be healthy" stays on `http://127.0.0.1:{{ platform_port }}`. When
    public, a second wait checks the site **from the controller** (`delegate_to: localhost`,
    `become: false`, `ansible.builtin.uri` on `https://{{ public_domain }}/actuator/health`, certificate
    validated, `until` status `UP`, about 5 minutes of retries for the first certificate). It proves
    DNS, the provider's firewall, the server's firewall and the certificate from outside, and its
    failure message names those four.
  - "Show how to open the platform": when public, the first lines are `https://<domain>` (the tunnel
    stays as a second way); otherwise unchanged.
- **`backup` role**: unchanged (it names `docker-compose.yml` explicitly and only touches postgres).

## Work packages

### WP1: Backend: forwarded headers from the proxy

- **Status**: done

- **Depends on**: none
- **Files**: `artifact/backend/src/main/resources/application.properties`,
  `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/config/LoginRateLimitFilter.java`
  (Javadoc only),
  `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/LoginLockoutIntegrationTest.java`,
  `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/AuthApiIntegrationTest.java`,
  `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/TestSession.java` (only
  if a header hook is needed)
- **Steps**:
  - [x] `server.forward-headers-strategy=native` with its comment; correct the `server.address`
        comment
  - [x] Rewrite the client-address paragraph of `LoginRateLimitFilter`'s Javadoc
  - [x] Let `TestSession` send extra headers on every request (e.g. a `withHeader(name, value)`
        returning the session), if the tests need it
- **Tests** (Spring Boot, `RANDOM_PORT`, the test client connects from 127.0.0.1, a trusted proxy):
  - `LoginLockoutIntegrationTest`: failed logins with `X-Forwarded-For: 203.0.113.1` up to the limit
    get 429 for that address, while a login with `X-Forwarded-For: 203.0.113.2` and the right
    password still answers 200 (one client does not block another behind the proxy); and the
    existing test without the header keeps passing.
  - `AuthApiIntegrationTest`: with `X-Forwarded-Proto: https`, a login's `Set-Cookie` headers for
    `JSESSIONID` and `XSRF-TOKEN` carry `Secure`; without the header, neither does (plain HTTP on
    127.0.0.1, the tunnel and local runs, keep working).

### WP2: Compose overlay and Caddyfile

- **Status**: done

- **Depends on**: none
- **Files**: `deploy/docker-compose.public.yml` (new), `deploy/Caddyfile` (new),
  `deploy/.env.example`, `deploy/docker-compose.yml` (header comment only)
- **Steps**:
  - [x] The overlay with the pinned `caddy` image, ports, environment, volumes, network
  - [x] The Caddyfile as in "Design"
  - [x] `.env.example`: the public section
- **Tests**:
  - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.public.yml config` with a
    sample `.env` (or `--env-file` from the scratch directory) succeeds, and fails with the
    `PLATFORM_DOMAIN`/`ACME_EMAIL` message when they are missing.
  - `docker run --rm -v "$PWD/deploy/Caddyfile:/etc/caddy/Caddyfile:ro" -e PLATFORM_DOMAIN=example.com
    -e ACME_EMAIL=a@example.com caddy:<version> caddy validate --config /etc/caddy/Caddyfile` passes.
  - Both are manual checks in the PR description (no CI job gets a certificate; see "Out of scope").

### WP3: Ansible: public domain, firewall, overlay, health over HTTPS

- **Status**: done

- **Depends on**: WP2 (the file names it starts)
- **Files**: `deploy/server/group_vars/all.yml`, `deploy/server/site.yml`,
  `deploy/server/roles/base/tasks/main.yml`, `deploy/server/roles/app/tasks/main.yml`,
  `deploy/server/roles/app/templates/env.j2`
- **Steps**:
  - [x] `public_domain`, `acme_email` and the assert
  - [x] `base`: ufw rules and the Oracle Cloud iptables rules, all only when `public_domain` is set
  - [x] `app`: `.env`, the compose files, the outside health wait, the final message
- **Tests**:
  - `mise run server:check` (ansible-lint production profile, syntax check) passes.
  - `mise run server:test` (CI job *Server deploy end-to-end*, tunnel-only) passes unchanged: the
    `.env`, the stack, the "ufw allows only 22/tcp" check and `changed=0` on the second deploy
    prove that nothing changes without `public_domain`.
  - The public mode is checked by hand on the Oracle Cloud VM (the issue's last step), not in CI.

### WP4: Docs

- **Depends on**: WP1, WP2, WP3
- **Files**: `deploy/server/README.md`, `README.md`
- **Steps**:
  - [ ] `deploy/server/README.md`: see "Docs to update"
  - [ ] `README.md`: see "Docs to update"
- **Tests**: none (docs only)

## Tests

- Backend: the two integration tests of WP1 (`./gradlew :backend:test`, part of CI's *Backend and
  frontend* job) prove "failed logins from one client don't block logins from another" behind the
  proxy, and that the cookies are `Secure` over HTTPS.
- Deploy: `mise run server:check` and the unchanged `mise run server:test` prove "without
  `public_domain`, nothing changes" and that the playbook stays idempotent.
- The public mode itself (certificate, redirects, `/actuator/metrics` 404, `/actuator/health`
  answering, reboot, `changed=0`, firewall 22/80/443 only) is the manual check on the Oracle Cloud VM
  after the merge, as the issue says; the PR description lists the commands for it
  (`curl -sI http://analystock.app`, `curl -sI https://www.analystock.app`,
  `curl -s -o /dev/null -w '%{http_code}' https://analystock.app/actuator/metrics`, `sudo ufw status`,
  `sudo iptables -L INPUT -n --line-numbers`).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `deploy/server/README.md`, intro and diagram | Two ways in: the SSH tunnel (default), or HTTPS on a domain (`public_domain`); a second diagram line `browser ──https──▶ server: Caddy :443 ──▶ platform` |
| Text | `deploy/server/README.md`, new section "HTTPS on a domain" (after "Open the platform") | Before going public: set a strong admin password (log in through the tunnel first and change the generated one); DNS: an `A` record for `@` and a `CNAME` for `www` to the server; the provider's firewall: on Oracle Cloud, ingress rules for TCP 80, TCP 443 and UDP 443 in the subnet's security list; the deploy command with `-e public_domain=… -e acme_email=…`; what the deploy does (ufw and Oracle's iptables rules, the overlay, the outside health check); that `/actuator/*` but `health` answers 404; that the certificates are in `platform_data/caddy/`; how to check it (the commands of "Tests") |
| Text | `deploy/server/README.md`, "What the server needs" | Incoming 80/443 (TCP, and UDP 443) only for a public domain |
| Text | `deploy/server/README.md`, roles table | `base`: the ports 80/443 when public, including the Oracle Cloud iptables rules; `app`: the Caddy overlay and the HTTPS health check when public |
| Text | `deploy/server/README.md`, "Settings" table | `public_domain` (empty: tunnel only) and `acme_email` |
| Text | `deploy/server/README.md`, "Troubleshooting" | The HTTPS health check fails: DNS not pointing at the server yet, the provider's firewall, Let's Encrypt rate limits (`docker compose … logs caddy`) |
| Text | `README.md`, "Docker Compose" notes, "Exposing it beyond localhost" | HTTPS now exists through the Caddy overlay (`deploy/docker-compose.public.yml`, `PLATFORM_DOMAIN`, `ACME_EMAIL`) and the server deploy's `public_domain`; a plain Compose run stays on 127.0.0.1 |
| Text | `README.md`, the server deployment table row / `server:tunnel` task line if they say "tunnel only" | Mention the HTTPS option where the README says the tunnel is the only way |
| Screenshot | none | No page of the UI changes |

## Out of scope

- A proxy for local runs (`mise run run`, `mise run dev`, the plain Compose run) and replacing Caddy
  with Traefik everywhere: #165.
- An end-to-end CI test of the public mode (a CI runner cannot get a real certificate): #165. Here
  `server:check` and the unchanged `server:test` must pass, and the manual check on the VM is the
  test.
- Going back from public to tunnel-only on the same server (closing 80/443 again, stopping Caddy):
  deploying without `public_domain` leaves Caddy's container and the firewall rules in place; the
  README says how to remove them by hand if needed.
- The deploy to the Oracle Cloud VM itself (`-e public_domain=analystock.app`) and the check from
  outside: done by the developer after the merge, as the issue's last step; the agents cannot reach
  the VM.
- Supporting Ubuntu 26.04 in `deploy/server` beyond what already works (the README says 24.04): if
  the VM needs a change there, it is a separate issue.
