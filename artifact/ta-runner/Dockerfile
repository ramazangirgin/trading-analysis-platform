# ta-runner image: the TradingAgents runtime (pinned by uv.lock) with ta-runner on top. The platform
# starts one short-lived container per analysis from it (PLAN.md section 4, option B).
#
#   docker build -t ta-runner:0.1.0 artifact/ta-runner
#
# Upstream publishes no image, only a Dockerfile on python:3.12-slim; this image uses the same base and
# installs the same pinned upstream release, leaving upstream's code untouched.

FROM ghcr.io/astral-sh/uv:0.12 AS uv

FROM python:3.12-slim AS build
COPY --from=uv /uv /usr/local/bin/uv
# git: uv installs TradingAgents from its pinned GitHub tag.
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never \
    UV_PROJECT_ENVIRONMENT=/opt/ta-runner/.venv
WORKDIR /opt/ta-runner
# Dependencies first, so a runner change does not reinstall TradingAgents.
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY ta_runner ./ta_runner
RUN uv sync --frozen --no-dev --no-editable

FROM python:3.12-slim
LABEL org.opencontainers.image.title="ta-runner" \
      org.opencontainers.image.description="Runs one TradingAgents analysis and streams JSONL events" \
      org.opencontainers.image.source="https://github.com/ramazangirgin/trading-analysis-platform"
RUN useradd --uid 10001 --user-group --create-home --home-dir /home/runner runner
COPY --from=build /opt/ta-runner/.venv /opt/ta-runner/.venv
# The data dir (reports, price cache, memory log) is upstream's default, ~/.tradingagents: the
# platform mounts its shared volume there. Everything else a run writes goes to /tmp, which the
# platform mounts as tmpfs over an otherwise read-only root filesystem.
ENV PATH=/opt/ta-runner/.venv/bin:$PATH \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HOME=/home/runner \
    XDG_CACHE_HOME=/tmp/cache \
    MPLCONFIGDIR=/tmp/matplotlib
RUN mkdir -p /home/runner/.tradingagents && chown runner:runner /home/runner/.tradingagents
VOLUME ["/home/runner/.tradingagents"]
USER runner
WORKDIR /home/runner
ENTRYPOINT ["ta-runner"]
CMD ["version"]
