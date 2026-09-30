"""CLI entry point.

    python -m ta_runner run --spec <run_dir>/spec.json --out <run_dir>
    python -m ta_runner run --spec-env TA_RUNNER_SPEC --out /tmp/run    (in a container)
    python -m ta_runner version
    python -m ta_runner catalog

stdout carries protocol lines only. Anything else that writes to stdout (upstream prints,
third-party libraries) is redirected to stderr, which the platform keeps as the raw run log.
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import signal
import sys
from pathlib import Path
from typing import TextIO

from . import PROTOCOL_VERSION, __version__
from .events import EventWriter, truncate
from .runner import EXIT_ERROR, StopRequested, execute
from .spec import RunSpec, SpecError


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="ta-runner")
    commands = parser.add_subparsers(dest="command", required=True)
    run_cmd = commands.add_parser("run", help="run one analysis and stream JSONL events")
    source = run_cmd.add_mutually_exclusive_group(required=True)
    source.add_argument("--spec", type=Path, help="spec.json file")
    source.add_argument(
        "--spec-env", metavar="NAME", help="environment variable holding the spec JSON"
    )
    run_cmd.add_argument("--out", type=Path, required=True, help="run directory (events.jsonl)")
    commands.add_parser("version", help="print runner and upstream versions as JSON")
    commands.add_parser("catalog", help="print providers, models and analysts as JSON")
    args = parser.parse_args(argv)

    if args.command == "version":
        return _version()
    if args.command == "catalog":
        return _catalog()
    return _run(args.spec, args.spec_env, args.out)


def _version() -> int:
    from .compat import upstream_version

    print(
        json.dumps(
            {
                "runner_version": __version__,
                "protocol_version": PROTOCOL_VERSION,
                "upstream_version": upstream_version(),
            }
        )
    )
    return 0


def _catalog() -> int:
    from .catalog import build_catalog

    print(json.dumps(build_catalog(), ensure_ascii=False))
    return 0


def _run(spec_path: Path | None, spec_env: str | None, out_dir: Path) -> int:
    protocol = _claim_stdout()
    _configure_logging()

    try:
        spec = RunSpec.from_env(spec_env) if spec_env else RunSpec.from_file(spec_path)
    except SpecError as exc:
        EventWriter("", protocol).emit(
            "run_finished", status="error", error_type="SpecError", error=str(exc)
        )
        return EXIT_ERROR

    out_dir.mkdir(parents=True, exist_ok=True)
    writer = EventWriter(spec.run_id, protocol, out_dir / "events.jsonl")
    logging.getLogger().addHandler(_EventLogHandler(writer))
    try:
        # Imported late: loading upstream takes seconds and must not delay a spec error.
        from .compat import UpstreamRun, upstream_version

        # Installed only now, so a stop always lands inside execute() and ends in run_finished.
        _install_stop_handler()
        return execute(spec, writer, UpstreamRun, upstream_version())
    finally:
        writer.close()


def _claim_stdout() -> TextIO:
    """Keep the real stdout for protocol lines and point fd 1 and sys.stdout at stderr."""
    protocol = os.fdopen(os.dup(1), "w", encoding="utf-8", buffering=1)
    sys.stdout.flush()
    os.dup2(2, 1)
    sys.stdout = sys.stderr
    return protocol


def _configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        stream=sys.stderr,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    for noisy in ("httpx", "httpx2", "httpcore", "urllib3", "openai", "anthropic"):
        logging.getLogger(noisy).setLevel(logging.WARNING)


def _install_stop_handler() -> None:
    def stop(signum: int, _frame: object) -> None:
        # A second signal falls through to the default action (terminate now).
        signal.signal(signum, signal.SIG_DFL)
        raise StopRequested()

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)


class _EventLogHandler(logging.Handler):
    """Forwards upstream logs (INFO+) and everyone's warnings as `log` events."""

    def __init__(self, writer: EventWriter) -> None:
        super().__init__(level=logging.INFO)
        self._writer = writer

    def emit(self, record: logging.LogRecord) -> None:
        if record.levelno < logging.WARNING and not record.name.startswith(
            ("tradingagents", "ta_runner")
        ):
            return
        try:
            self._writer.emit(
                "log",
                level=record.levelname,
                logger=record.name,
                message=truncate(record.getMessage(), 4_000),
            )
        except Exception:  # noqa: BLE001 - logging must never break the run
            self.handleError(record)


if __name__ == "__main__":
    sys.exit(main())
