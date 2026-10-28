"""Run a command in its own process group under a time limit.

    timeout.py <seconds> <command...>

Exits with the command's status; 124 when the limit stopped it (TERM to the group, KILL after
10 seconds); 128 + n when the signal n (INT, TERM, HUP) was sent to this process, after it was
forwarded to the group. macOS has no timeout command, and the group takes the command's own
subprocesses (git-remote-https, an agent's tools) down with it.
"""

import os
import signal
import subprocess
import sys
import time

GRACE_SECONDS = 10
FORWARDED = (signal.SIGINT, signal.SIGTERM, signal.SIGHUP)


def kill_group(pgid, sig):
    try:
        os.killpg(pgid, sig)
    except ProcessLookupError:
        pass


def run(seconds, command):
    try:
        child = subprocess.Popen(command, start_new_session=True)
    except OSError as error:
        print(f"timeout.py: {command[0]}: {error.strerror}", file=sys.stderr)
        return 127 if isinstance(error, FileNotFoundError) else 126
    pgid = child.pid
    received = []

    def forward(signum, _frame):
        received.append(signum)
        kill_group(pgid, signum)

    for signum in FORWARDED:
        signal.signal(signum, forward)

    deadline = time.monotonic() + seconds
    killed_at = None
    timed_out = False
    while child.poll() is None:
        now = time.monotonic()
        if killed_at is None and (received or now >= deadline):
            timed_out = not received
            killed_at = now
            if timed_out:
                kill_group(pgid, signal.SIGTERM)
        if killed_at is not None and now - killed_at >= GRACE_SECONDS:
            break
        time.sleep(0.05)

    kill_group(pgid, signal.SIGKILL)
    child.wait()
    if received:
        return 128 + received[0]
    if timed_out:
        return 124
    return child.returncode if child.returncode >= 0 else 128 - child.returncode


def main(argv):
    if len(argv) < 3:
        print("usage: timeout.py <seconds> <command...>", file=sys.stderr)
        return 2
    try:
        seconds = float(argv[1])
    except ValueError:
        print(f"timeout.py: not a number of seconds: {argv[1]}", file=sys.stderr)
        return 2
    return run(seconds, argv[2:])


if __name__ == "__main__":
    sys.exit(main(sys.argv))
