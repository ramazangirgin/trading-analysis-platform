# syntax=docker/dockerfile:1
# ta-runner image: the TradingAgents runtime (pinned by uv.lock) with ta-runner on top. The platform
# starts one short-lived container per analysis from it (PLAN.md section 4, option B).
#
#   docker build --build-arg VERSION="$(scripts/version.sh)" -t ta-runner:latest artifact/ta-runner
#
# Upstream publishes no image, only a Dockerfile on python:3.12-slim; this image uses the same base and
# installs the same pinned upstream release, leaving upstream's code untouched.
#
# Kept to one stage and few layers: writing image layers is what makes builds slow (a ~400 MB
# environment copied from a build stage would be written twice), so the environment is installed
# once, where it stays. uv is only mounted for the install, and TradingAgents comes as GitHub's
# archive of its tag, so neither uv nor git ends up as a layer.

FROM ghcr.io/astral-sh/uv:0.12 AS uv

FROM python:3.12-slim
LABEL org.opencontainers.image.title="ta-runner" \
      org.opencontainers.image.description="Runs one TradingAgents analysis and streams JSONL events" \
      org.opencontainers.image.source="https://github.com/ramazangirgin/trading-analysis-platform"
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never \
    UV_CACHE_DIR=/root/.cache/uv \
    UV_PROJECT_ENVIRONMENT=/opt/ta-runner/.venv
WORKDIR /opt/ta-runner
# Dependencies first, so a runner change does not reinstall TradingAgents. The cache mount keeps
# uv's downloads between builds (not in the image): after a uv.lock change only what changed is
# fetched again.
COPY pyproject.toml uv.lock ./
RUN --mount=type=bind,from=uv,source=/uv,target=/usr/local/bin/uv \
    --mount=type=cache,id=ta-runner-uv,target=/root/.cache/uv \
    uv sync --frozen --no-dev --no-install-project
COPY ta_runner ./ta_runner
# The runner itself, and its user. The data dir is upstream's default, ~/.tradingagents: the
# platform mounts its shared folder or volume there.
RUN --mount=type=bind,from=uv,source=/uv,target=/usr/local/bin/uv \
    --mount=type=cache,id=ta-runner-uv,target=/root/.cache/uv \
    uv sync --frozen --no-dev --no-editable \
    && useradd --uid 10001 --user-group --create-home --home-dir /home/runner runner \
    && mkdir -p /home/runner/.tradingagents \
    && chown runner:runner /home/runner/.tradingagents
# Everything else a run writes goes to /tmp, which the platform mounts as tmpfs over an otherwise
# read-only root filesystem.
ENV PATH=/opt/ta-runner/.venv/bin:$PATH \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HOME=/home/runner \
    XDG_CACHE_HOME=/tmp/cache \
    MPLCONFIGDIR=/tmp/matplotlib
VOLUME ["/home/runner/.tradingagents"]
USER runner
WORKDIR /home/runner
ENTRYPOINT ["ta-runner"]
CMD ["version"]
# The platform version (scripts/version.sh), passed by CI and `mise run docker-build`. Last, because a
# changed build argument invalidates every RUN layer after its ARG.
ARG VERSION=unknown
LABEL org.opencontainers.image.version="${VERSION}"
