"""What the developer said on a plan pull request that has no answer yet (plan-comments.sh).

    plan_comments.py <threads.json> <pr.json>

<threads.json> is the GraphQL answer for the pull request's review threads, <pr.json> the output of
`gh pr view --json comments,reviews`. Prints the open review threads whose last comment is not a
reply of the session, and the pull request comments and review bodies that no reply of the session
names (`<!-- plan-reply to=<id>,<id> -->`).
"""

import json
import re
import sys

REPLY_MARKER = "<!-- plan-reply"
REPLY_TARGETS = re.compile(r"<!-- plan-reply to=(\S+) -->")
IGNORED_MARKERS = ("<!-- agent-step", "<!-- agent-progress")


def is_reply(body):
    return REPLY_MARKER in body


def is_agent_comment(body):
    return any(marker in body for marker in IGNORED_MARKERS)


def login(author):
    return (author or {}).get("login") or "ghost"


def indent(text):
    return "\n".join("  " + line for line in text.strip().splitlines())


def thread_comments(threads):
    nodes = threads["data"]["repository"]["pullRequest"]["reviewThreads"]["nodes"]
    return [(t, t["comments"]["nodes"]) for t in nodes]


def unanswered_threads(threads):
    found = []
    for thread, comments in thread_comments(threads):
        if thread["isResolved"] or not comments:
            continue
        last = comments[-1]
        if is_reply(last["body"]) or is_agent_comment(last["body"]):
            continue
        line = last.get("line") or last.get("originalLine")
        where = f"{last['path']}:{line}" if line else last["path"]
        found.append(
            f"Review thread, reply to comment {last['databaseId']} ({where}), @{login(last['author'])}:\n"
            + indent(last["body"])
        )
    return found


def answered_ids(pr):
    ids = set()
    for comment in pr.get("comments", []):
        match = REPLY_TARGETS.search(comment["body"])
        if match:
            ids.update(match.group(1).split(","))
    return ids


def unanswered_pr_comments(pr):
    answered = answered_ids(pr)
    entries = [("Comment", c["createdAt"], c) for c in pr.get("comments", [])]
    entries += [("Review", r.get("submittedAt") or "", r) for r in pr.get("reviews", [])]
    found = []
    for kind, _, item in sorted(entries, key=lambda e: e[1]):
        body = item["body"]
        if not body.strip() or is_reply(body) or is_agent_comment(body) or item["id"] in answered:
            continue
        found.append(f"{kind} {item['id']} on the pull request, @{login(item.get('author'))}:\n" + indent(body))
    return found


def unanswered(threads, pr):
    return unanswered_threads(threads) + unanswered_pr_comments(pr)


def main(argv):
    with open(argv[1], encoding="utf-8") as f:
        threads = json.load(f)
    with open(argv[2], encoding="utf-8") as f:
        pr = json.load(f)
    print("\n\n".join(unanswered(threads, pr)))


if __name__ == "__main__":
    main(sys.argv)
