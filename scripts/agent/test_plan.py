"""Tests of plan.py: which work package runs next and whether a package is finished.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import unittest

import plan


def package(id, status=None, depends="none", steps=("x",), name=None):
    """One work package section of a plan."""
    lines = [f"### {id}: {name or 'name of ' + id}", ""]
    if status is not None:
        lines.append(f"- **Status**: {status}")
    lines += [f"- **Depends on**: {depends}", "- **Files**: a.txt", "- **Steps**:"]
    lines += [f"  - [{'x' if mark == 'x' else ' '}] step {i}" for i, mark in enumerate(steps)]
    lines += ["- **Tests**: none", ""]
    return "\n".join(lines)


def document(*packages, before=""):
    return "# Plan: test\n\n" + before + "\n## Work packages\n\n" + "\n".join(packages) + "\n## Tests\n\nText.\n"


def order(text):
    return [p.id for p in plan.ordered(plan.parse(text))]


def next_id(text):
    found = plan.next_package(plan.ordered(plan.parse(text)))
    return found.id if found else None


def by_id(text, id):
    return plan.find(plan.ordered(plan.parse(text)), id)


class OrderTest(unittest.TestCase):
    def test_keeps_document_order_without_dependencies(self):
        text = document(package("WP1"), package("WP2"), package("WP3"))
        self.assertEqual(order(text), ["WP1", "WP2", "WP3"])

    def test_puts_a_dependency_first(self):
        text = document(package("WP1", depends="WP2"), package("WP2"), package("WP3"))
        self.assertEqual(order(text), ["WP2", "WP1", "WP3"])

    def test_shape_of_the_spring_data_jpa_plan(self):
        text = document(
            package("WP1"),
            package("WP1b", depends="WP1"),
            package("WP1c", depends="WP1b"),
            package("WP2", depends="WP1, WP1c"),
            package("WP3", depends="WP2"),
            package("WP4", depends="WP3"),
            package("WP5", depends="WP1b, WP2–WP4 (the rules must pass on the migrated code)"),
        )
        self.assertEqual(order(text), ["WP1", "WP1b", "WP1c", "WP2", "WP3", "WP4", "WP5"])
        self.assertEqual(by_id(text, "WP5").depends, ["WP1b", "WP2", "WP3", "WP4"])

    def test_range_with_a_hyphen(self):
        text = document(package("WP1"), package("WP2"), package("WP3"), package("WP4", depends="WP1-WP3"))
        self.assertEqual(by_id(text, "WP4").depends, ["WP1", "WP2", "WP3"])

    def test_none_with_a_reason_and_a_parenthesised_reason(self):
        text = document(package("WP1", depends="none (nothing to wait for)"), package("WP2", depends="WP1 (the API)"))
        self.assertEqual(by_id(text, "WP1").depends, [])
        self.assertEqual(by_id(text, "WP2").depends, ["WP1"])

    def test_ignores_work_package_headings_in_code_fences(self):
        text = "# Plan: t\n\n## Design\n\n```markdown\n### WP9: example\n\n- **Status**: open\n```\n"
        text += "\n## Work packages\n\n" + package("WP1")
        self.assertEqual(order(text), ["WP1"])

    def test_unknown_id_names_the_line(self):
        text = document(package("WP1", depends="WP7"))
        with self.assertRaisesRegex(plan.PlanError, r"line \d+: unknown package WP7"):
            plan.parse(text)

    def test_unknown_id_in_a_range(self):
        text = document(package("WP1"), package("WP2", depends="WP1–WP7"))
        with self.assertRaisesRegex(plan.PlanError, "unknown package WP7"):
            plan.parse(text)

    def test_unreadable_depends_names_the_line(self):
        text = document(package("WP1", depends="the first one"))
        with self.assertRaisesRegex(plan.PlanError, r"line \d+: cannot read"):
            plan.parse(text)

    def test_cycle_names_the_lines(self):
        text = document(package("WP1", depends="WP2"), package("WP2", depends="WP1"))
        with self.assertRaisesRegex(plan.PlanError, r"cycle among WP1 \(line \d+\), WP2 \(line \d+\)"):
            plan.ordered(plan.parse(text))


class NextTest(unittest.TestCase):
    def test_first_open_package(self):
        text = document(package("WP1", "open"), package("WP2", "open", depends="WP1"))
        self.assertEqual(next_id(text), "WP1")

    def test_skips_done_and_not_done(self):
        text = document(
            package("WP1", "done"),
            package("WP2", "not done: no API", depends="WP1"),
            package("WP3", "open", depends="WP2"),
        )
        self.assertEqual(next_id(text), "WP3")

    def test_waits_for_an_open_dependency(self):
        text = document(package("WP1", "open", depends="WP2"), package("WP2", "open"), package("WP3", "open"))
        self.assertEqual(next_id(text), "WP2")

    def test_attempts_a_package_whose_dependency_is_not_done(self):
        text = document(package("WP1", "not done: blocked"), package("WP2", "open", depends="WP1"))
        self.assertEqual(next_id(text), "WP2")

    def test_nothing_left(self):
        text = document(package("WP1", "done"), package("WP2", "not done: x"))
        self.assertIsNone(next_id(text))

    def test_plan_without_status_lines_is_all_open(self):
        text = document(package("WP1"), package("WP2", depends="WP1"))
        self.assertEqual([p.status for p in plan.parse(text)], ["open", "open"])
        self.assertEqual(next_id(text), "WP1")

    def test_malformed_status_is_attempted_again(self):
        self.assertEqual(next_id(document(package("WP1", "finished"))), "WP1")


class WholePlanTest(unittest.TestCase):
    TEXT = "# Plan: t\n\n- **Issue**: #1\n{status}\n## Goal\n\nText.\n\n## Work packages\n\nNone.\n"

    def test_plan_without_work_packages_is_one_package(self):
        packages = plan.parse(self.TEXT.format(status=""))
        self.assertEqual([(p.id, p.status) for p in packages], [("all", "open")])
        self.assertEqual(next_id(self.TEXT.format(status="")), "all")

    def test_status_line_of_the_whole_plan(self):
        text = self.TEXT.format(status="- **Status**: done\n")
        self.assertIsNone(next_id(text))
        self.assertEqual(plan.problems(by_id(text, "all")), [])


class CheckTest(unittest.TestCase):
    def test_open_status(self):
        found = plan.problems(by_id(document(package("WP1", "open")), "WP1"))
        self.assertEqual(len(found), 1)
        self.assertIn("still `open`", found[0])

    def test_missing_status_line(self):
        found = plan.problems(by_id(document(package("WP1")), "WP1"))
        self.assertIn("no Status line", found[0])

    def test_done_with_an_unticked_step(self):
        text = document(package("WP1", "done", steps=("x", " ", "x")))
        found = plan.problems(by_id(text, "WP1"))
        self.assertIn("1 step(s) are not ticked", found[0])
        self.assertIn("step 1", found[1])

    def test_done_with_every_step_ticked(self):
        self.assertEqual(plan.problems(by_id(document(package("WP1", "done")), "WP1")), [])

    def test_not_done_may_leave_steps_unticked(self):
        text = document(package("WP1", "not done: no access", steps=(" ", " ")))
        self.assertEqual(plan.problems(by_id(text, "WP1")), [])

    def test_malformed_status(self):
        for status in ("finished", "not done", "not done:", "Done"):
            with self.subTest(status=status):
                found = plan.problems(by_id(document(package("WP1", status)), "WP1"))
                self.assertIn("malformed", found[0])

    def test_unknown_package(self):
        with self.assertRaises(plan.PlanError):
            plan.find(plan.parse(document(package("WP1"))), "WP9")


class ListingTest(unittest.TestCase):
    TEXT = document(
        package("WP1", "done", name="first"),
        package("WP2", "not done: the API is missing", depends="WP1", name="second"),
        package("WP3", "open", depends="WP2", name="third"),
    )

    def lines(self, command):
        return plan.lines_of(command, plan.ordered(plan.parse(self.TEXT)), [])

    def test_packages(self):
        self.assertEqual(self.lines("packages"), ["WP1\tdone\tfirst", "WP2\tnot done\tsecond", "WP3\topen\tthird"])

    def test_remaining(self):
        self.assertEqual(self.lines("remaining"), ["WP3\topen\tthird"])

    def test_not_done(self):
        self.assertEqual(self.lines("not-done"), ["- WP2: the API is missing"])

    def test_nothing_remaining_in_a_finished_plan(self):
        text = document(package("WP1", "done"))
        self.assertEqual(plan.lines_of("remaining", plan.ordered(plan.parse(text)), []), [])


if __name__ == "__main__":
    unittest.main()
