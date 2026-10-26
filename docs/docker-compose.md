# Run with Docker Compose

For a server (or any machine with Docker or Podman): the platform, PostgreSQL and a socket proxy
run as containers, and every analysis runs in a container of its own that is removed afterwards.

```
browser ──▶ 127.0.0.1:8080 ──▶ platform ──▶ postgres
                                  │
                                  └──▶ docker-socket-proxy ──▶ Docker / Podman engine
                                                                  │  one per analysis
                                                                  ▼
                                                          ta-runner container
```

**Needs:** Docker with Compose v2, or Podman with `docker compose` pointed at its socket; and
[mise](https://mise.jdx.dev/) to build the images. To run it on a server instead of your own machine,
[`deploy/server/`](../deploy/server/README.md) sets up any Ubuntu server you can reach over SSH and
deploys this setup on it.

```sh
cp deploy/.env.example deploy/.env      # set PLATFORM_DATA and POSTGRES_PASSWORD
mise run docker-build                   # builds trading-analysis-platform and ta-runner, tagged latest and with the version
docker compose -f deploy/docker-compose.yml up -d
```

Open **http://127.0.0.1:8080** (or the `PLATFORM_PORT` you set), log in and add an API key under
**Settings**, as in the [user guide](user-guide.md#4-add-an-api-key). `.env` may set `PLATFORM_ADMIN_USERNAME` and
`PLATFORM_ADMIN_PASSWORD` for the first admin; without them the generated password is in
`docker compose -f deploy/docker-compose.yml logs platform`. It may also set
`PLATFORM_PASSWORD_MIN_LENGTH` (8 to 64, default 12). `docker compose -f
deploy/docker-compose.yml down` stops everything; `up -d` brings it back with all data.

**Data** stays on the machine, in the folder named by `PLATFORM_DATA` (an absolute path):

| Folder | What |
|---|---|
| `postgres/` | The database: analyses, presets and users. Survives restarts, `down` and image updates. |
| `platform/` | `secrets.env` (API keys, owner-only), `runs/<id>/` (events and runner logs), optional `prices.json` |
| `tradingagents/` | TradingAgents' reports, price cache and memory log, written by the analysis containers |

Back up the whole folder; for the database alone, `docker compose -f deploy/docker-compose.yml exec
postgres pg_dump -U platform platform > platform.sql`. Table and column names are uppercase and
stored quoted, so a hand query quotes them too: `SELECT * FROM "ANALYSIS"."ANALYSES";`.

**How analyses run:** the platform asks the engine, through docker-socket-proxy, for one
`ta-runner` container per analysis: read-only root filesystem, no Linux capabilities, a non-root
user, 2 GB of memory and 2 CPUs, and only the `tradingagents/` folder mounted. The proxy lets the
platform create, start, stop and remove containers and nothing else (no `exec`, images, volumes or
networks). If the platform restarts during an analysis, the container keeps running and the
platform picks it up again.

**Notes**

- **SELinux** (Fedora, RHEL, Podman machines): the Compose file labels its folders with `:z`, and
  the proxy runs without SELinux labelling so it can reach the socket.
- **Podman on macOS:** `PLATFORM_DATA` must be a folder the Podman machine can see. A machine
  created without `--volume` shares no macOS folders; use a path inside the machine (e.g.
  `/var/home/core/trading-analysis-platform`) or recreate it with `podman machine init --volume
  $HOME:$HOME`. `DOCKER_SOCKET` is the socket path inside the machine (`/var/run/docker.sock` on a
  rootful one).
- **Exposing it beyond localhost** needs HTTPS first. Login is always on (see [Login](login-and-backend.md#login)). The
  port is published on 127.0.0.1 only, and a plain Compose run stays that way. For a machine with a
  domain, [`deploy/docker-compose.public.yml`](../deploy/docker-compose.public.yml) adds a Caddy
  container that gets a Let's Encrypt certificate and serves the platform on 80/443: set
  `PLATFORM_DOMAIN` and `ACME_EMAIL` in `deploy/.env` and run `docker compose -f docker-compose.yml
  -f docker-compose.public.yml up -d`. The [server setup](../deploy/server/README.md) does this with
  `-e public_domain=…`, or you open the platform through an SSH tunnel instead.
- The first start takes about a minute (longer under Podman's VM on macOS).

## Upgrading to 5.0.0

`GET /api/analyses?allUsers=true` is gone: a client sends `scope=ALL` instead (without `scope` it
gets its own analyses, as `allUsers=true` would now). Existing analyses stay private. The migrations
give ADMIN the new permission `analysis:share` and add the built-in role PUBLISHER.

## Upgrading to 4.0.0

Login is always on and `PLATFORM_AUTH_ENABLED` is gone. An installation without users gets the first
admin on its next start (see [Quick start](user-guide.md#3-log-in)). Scripts that call the API need a session and
the CSRF header: `GET /api/auth/me` sets the `XSRF-TOKEN` cookie, `POST /api/auth/login` takes it
back in `X-XSRF-TOKEN`, and every later unsafe request sends the header too.

## Upgrading to 3.0.0

Each domain's tables now live in a database schema of its own (`"ANALYSIS"`, `"SETTINGS"`,
`"IDENTITY"`), each with its own migrations and history table, so **an existing database is reset,
not migrated**. Started on an old database, the platform would create the three schemas next to the
old tables in `public` and ignore them. Runs the platform started, presets and users are lost;
analyses that TradingAgents' CLI left in the data folder are imported again on startup. For Docker
Compose, reset as for 1.0.0:

```sh
docker compose -f deploy/docker-compose.yml down
# optional, to keep a copy: start only postgres, run the pg_dump above, then down again
rm -rf "$PLATFORM_DATA/postgres"        # the folder PLATFORM_DATA names in deploy/.env
docker compose -f deploy/docker-compose.yml up -d
```

For runs on your machine (`mise run run`), `mise run db:reset` empties the local database; the next
start is a fresh one. On your own PostgreSQL server, drop and recreate the database.

## Upgrading to 2.0.0

The database is migrated in place on the first start: enum columns become PostgreSQL enum types, an
analysis's analysts an array, and a role's permissions an array on the role (the `ROLE_PERMISSIONS`
table goes away). No data is lost, and nothing needs to be done. An older release cannot run on the
migrated database, so take a copy first if you may want to go back (the `pg_dump` above, with the
platform stopped).

## Upgrading to 1.0.0

PostgreSQL is now the platform's only database, and its schema was recreated, so **an existing
database is reset, not migrated**: the platform does not start on the old one (Flyway rejects its
history). Analyses that TradingAgents' CLI left in the data folder are imported again on startup;
runs the platform started, presets and users are lost. To reset:

```sh
docker compose -f deploy/docker-compose.yml down
# optional, to keep a copy: start only postgres, run the pg_dump above, then down again
rm -rf "$PLATFORM_DATA/postgres"        # the folder PLATFORM_DATA names in deploy/.env
docker compose -f deploy/docker-compose.yml up -d
```

For runs on your machine (`mise run run`) nothing is needed on a first start; a
`~/.tradingagents-platform/platform.db` file from an earlier version is no longer used and can be
deleted. `mise run db:reset` empties the local database later, whenever you want a fresh one.
