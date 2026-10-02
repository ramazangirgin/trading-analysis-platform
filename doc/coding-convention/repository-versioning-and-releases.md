# Repository: versioning and releases

The whole repository has one [Semantic Versioning](https://semver.org/) version, `MAJOR.MINOR.PATCH`.
Backend, frontend and ta-runner are built, tested and shipped together, so they always carry the same
version. Every pull request into `main` raises it, and every commit on `main` is a release: CI tags it
`vX.Y.Z` and publishes a GitHub Release, with no manual step (#58).

## Rules

- **One version, no `-SNAPSHOT`.** [`gradle.properties`](../../gradle.properties) holds it;
  `artifact/frontend/package.json` and `artifact/ta-runner/ta_runner/__init__.py` carry copies.
  Change it only with [`scripts/version.sh`](../../scripts/version.sh) (`mise run version:bump`),
  which writes all three.
- **Every pull request into `main` raises the version in its own diff:**

  | Bump | When |
  |---|---|
  | `major` | A breaking change: the REST API, the database without a migration, configuration, the ta-runner contract or the CLI |
  | `minor` | A new feature, or any other change (documentation and CI included) |
  | `patch` | Hotfixes only |

  ```sh
  mise run version:bump minor   # 0.3.0 -> 0.4.0 in every file
  mise run version              # prints the version
  ```

- **Tags are created by CI only**, as annotated `vX.Y.Z` tags, never by hand.
- Two open pull requests that raise to the same version cannot both merge: the second one must be
  updated with `main` (the ruleset already requires an up-to-date branch) and bumped again.

## Where it is checked and used

| Where | What |
|---|---|
| CI, **Version** job ([`ci.yml`](../../.github/workflows/ci.yml)) | Every run: the three files agree (`scripts/version.sh check-sync`). Pull requests into `main`: the version is higher than `main`'s and than the latest `v*` tag, and `v<version>` does not exist (`check-bump`). The job is in **CI passed**'s `needs`, so a pull request without a bump cannot merge. |
| Release ([`release.yml`](../../.github/workflows/release.yml)) | After **CI passed** on a push to `main`: tags the commit `vX.Y.Z` and creates a GitHub Release with notes generated from the merged pull requests (grouped by label, [`.github/release.yml`](../../.github/release.yml)) and that run's jar as `trading-analysis-platform-X.Y.Z.jar` with its SHA-256. A version that already has a release is skipped, so a rerun never releases twice. Only the workflow's `GITHUB_TOKEN` is used. |
| Backend | Spring Boot build info: `/actuator/info` (`build.version`) and the jar manifest (`Implementation-Version`). The jar itself has a fixed name, `platform.jar`, so nothing else depends on the version. |
| Frontend | Shown at the bottom of every page (`__APP_VERSION__`, from `package.json` at build time). |
| ta-runner | `ta-runner version` reports it as `runner_version`. `pyproject.toml` reads it from `__init__.py` (hatchling's dynamic version), so a bump changes neither `pyproject.toml` nor `uv.lock`, and the image keeps its cached dependency layer. |
| Docker images | Tagged with the version and `latest`, labelled `org.opencontainers.image.version` (`mise run docker-build`, CI). The Compose setup and the Docker runner default to `latest`; pinning a release is one line in `deploy/.env`. |

## Why this scheme

- **Bump in the pull request, release on merge**: the bump is part of the reviewed diff, so the
  author and the reviewer decide "is this breaking?", and the release needs nothing but a script and
  a workflow. Every commit on `main` traces to a tag, a release and a jar. The price: every pull
  request carries a one-line version change, and a documentation-only change also releases.
- **Not derived from Conventional Commits or labels at merge time** (semantic-release,
  release-please): the workflow would have to commit the new version to `main`, which the ruleset
  forbids without a bypass token, or the version would live only in tags and the files would lie.
- **Not released by hand, with `-SNAPSHOT` in between**: most commits on `main` would not be
  releases, and a manual step is easy to forget.
- **Not a version per part**: the parts ship together; separate versions would only add a
  compatibility matrix nobody tests.

Images are not published to a registry yet; the release workflow can push them to GHCR
(`packages: write`) when a server deployment needs them.
