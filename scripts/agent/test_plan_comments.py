"""Tests of plan_comments.py: which comments on a plan pull request still wait for an answer.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import unittest

import plan_comments

REPLY = "Changed. <!-- plan-reply -->"


def comment(id, body, at, author="dev"):
    return {
        "databaseId": id,
        "path": ".plans/1-x.md",
        "line": 12,
        "author": {"login": author},
        "body": body,
        "createdAt": at,
    }


def threads(*items):
    """items: (resolved, [comments])"""
    nodes = [{"isResolved": resolved, "comments": {"nodes": comments}} for resolved, comments in items]
    return {"data": {"repository": {"pullRequest": {"reviewThreads": {"nodes": nodes}}}}}


def pr(comments=(), reviews=()):
    return {"comments": list(comments), "reviews": list(reviews)}


def pr_comment(body, at, author="dev", id="IC_x"):
    return {"id": id, "author": {"login": author}, "body": body, "createdAt": at}


def review(body, at, author="dev", id="PRR_x"):
    return {"id": id, "author": {"login": author}, "body": body, "submittedAt": at}


def reply_to(ids):
    return f"Changed. <!-- plan-reply to={ids} -->"


class UnansweredThreads(unittest.TestCase):
    def test_open_thread_is_listed_with_id_place_author_and_text(self):
        out = plan_comments.unanswered(threads((False, [comment(7, "Why this?", "2026-01-01T10:00:00Z")])), pr())
        self.assertEqual(len(out), 1)
        self.assertIn("comment 7", out[0])
        self.assertIn(".plans/1-x.md:12", out[0])
        self.assertIn("@dev", out[0])
        self.assertIn("Why this?", out[0])

    def test_resolved_thread_is_not_listed(self):
        t = threads((True, [comment(7, "Why this?", "2026-01-01T10:00:00Z")]))
        self.assertEqual(plan_comments.unanswered(t, pr()), [])

    def test_thread_whose_last_comment_is_a_reply_is_not_listed(self):
        t = threads(
            (False, [comment(7, "Why this?", "2026-01-01T10:00:00Z"), comment(8, REPLY, "2026-01-01T11:00:00Z", "bot")])
        )
        self.assertEqual(plan_comments.unanswered(t, pr()), [])

    def test_developer_comment_after_a_reply_is_listed(self):
        t = threads(
            (
                False,
                [
                    comment(7, "Why this?", "2026-01-01T10:00:00Z"),
                    comment(8, REPLY, "2026-01-01T11:00:00Z", "bot"),
                    comment(9, "Still unclear", "2026-01-01T12:00:00Z"),
                ],
            )
        )
        out = plan_comments.unanswered(t, pr())
        self.assertEqual(len(out), 1)
        self.assertIn("comment 9", out[0])

    def test_agent_step_comment_is_not_listed(self):
        t = threads((False, [comment(7, "<!-- agent-step step=review round=1 -->\nx", "2026-01-01T10:00:00Z", "bot")]))
        self.assertEqual(plan_comments.unanswered(t, pr()), [])


class UnansweredPullRequestComments(unittest.TestCase):
    def test_only_comments_and_reviews_named_by_a_reply_are_answered(self):
        p = pr(
            comments=[
                pr_comment("First", "2026-01-01T10:00:00Z", id="IC_1"),
                pr_comment(reply_to("IC_1,PRR_1"), "2026-01-01T11:00:00Z", "bot", id="IC_2"),
                pr_comment("Second", "2026-01-01T12:00:00Z", id="IC_3"),
            ],
            reviews=[review("Old review", "2026-01-01T09:00:00Z", id="PRR_1"), review("New review", "2026-01-01T13:00:00Z", id="PRR_2")],
        )
        out = plan_comments.unanswered(threads(), p)
        self.assertEqual(len(out), 2)
        self.assertIn("Second", out[0])
        self.assertIn("IC_3", out[0])
        self.assertIn("New review", out[1])
        self.assertIn("PRR_2", out[1])

    def test_a_comment_older_than_a_reply_that_does_not_name_it_is_still_listed(self):
        p = pr(
            comments=[
                pr_comment("Posted meanwhile", "2026-01-01T10:02:00Z", id="IC_1"),
                pr_comment(reply_to("IC_0"), "2026-01-01T10:03:00Z", "bot", id="IC_2"),
            ]
        )
        out = plan_comments.unanswered(threads(), p)
        self.assertEqual(len(out), 1)
        self.assertIn("Posted meanwhile", out[0])

    def test_a_reply_in_a_thread_does_not_answer_a_review_body(self):
        t = threads((False, [comment(7, "q", "2026-01-01T10:00:00Z"), comment(8, REPLY, "2026-01-01T12:00:00Z", "bot")]))
        p = pr(reviews=[review("Review body", "2026-01-01T09:00:00Z", id="PRR_1")])
        out = plan_comments.unanswered(t, p)
        self.assertEqual(len(out), 1)
        self.assertIn("Review body", out[0])

    def test_without_a_reply_every_comment_is_listed(self):
        p = pr(comments=[pr_comment("First", "2026-01-01T10:00:00Z")])
        self.assertEqual(len(plan_comments.unanswered(threads(), p)), 1)

    def test_agent_step_and_progress_comments_are_not_listed(self):
        p = pr(
            comments=[
                pr_comment("<!-- agent-step step=implement round=0 -->\nPushed", "2026-01-01T10:00:00Z", "bot"),
                pr_comment("<!-- agent-progress plan=1-x -->\ntable", "2026-01-01T10:05:00Z", "bot"),
            ]
        )
        self.assertEqual(plan_comments.unanswered(threads(), p), [])

    def test_empty_review_bodies_are_not_listed(self):
        p = pr(reviews=[review("", "2026-01-01T10:00:00Z"), review("  \n", "2026-01-01T10:01:00Z")])
        self.assertEqual(plan_comments.unanswered(threads(), p), [])


if __name__ == "__main__":
    unittest.main()
