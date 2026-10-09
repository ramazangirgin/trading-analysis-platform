"""Tests of alerts.py: versions, ranges, grouping, maturity, candidates and the Renovate rules.

    uv run --no-project python -m unittest discover -s .claude/skills/dependabot-fix -p 'test_*.py'
    (mise run skill:test)

No network: the alerts are fixtures shaped like the GitHub API's, taken from the open alerts of
2026-10-08, and the versions are lists with the release dates seen on Maven Central that day.
"""

import datetime
import unittest

import alerts

TODAY = datetime.date(2026, 10, 8)


def alert(number, package, vulnerable_range, patched, severity="high", ecosystem="maven", ghsa=None):
    """One alert as the GitHub API returns it."""
    return {
        "number": number,
        "html_url": f"https://github.com/o/r/security/dependabot/{number}",
        "dependency": {
            "package": {"ecosystem": ecosystem, "name": package},
            "manifest_path": "settings.gradle.kts" if ecosystem == "maven" else "artifact/ta-runner/uv.lock",
            "scope": None,
        },
        "security_advisory": {"ghsa_id": ghsa or f"GHSA-{number:04d}", "cve_id": None, "severity": severity},
        "security_vulnerability": {
            "vulnerable_version_range": vulnerable_range,
            "first_patched_version": {"identifier": patched} if patched else None,
        },
    }


CORE2 = "com.fasterxml.jackson.core:jackson-core"
DATABIND2 = "com.fasterxml.jackson.core:jackson-databind"
DATABIND3 = "tools.jackson.core:jackson-databind"
KOTLIN = "org.jetbrains.kotlin:kotlin-gradle-plugin"

RAW = [
    alert(4, CORE2, "< 2.15.0", "2.15.0"),
    alert(48, CORE2, ">= 2.8.0, <= 2.18.10", "2.18.11"),
    alert(42, CORE2, ">= 2.19.0, <= 2.21.6", "2.21.7"),
    alert(43, CORE2, ">= 2.22.0, <= 2.22.2", "2.22.3"),
    alert(28, DATABIND2, ">= 2.19.0, < 2.21.6", "2.21.6", severity="medium"),
    alert(29, DATABIND2, ">= 2.22.0, < 2.22.2", "2.22.2"),
    alert(26, DATABIND3, ">= 3.0.0, < 3.1.6", "3.1.6"),
    alert(37, DATABIND3, ">= 3.0.0, <= 3.1.6", "3.1.7"),
    alert(19, "org.bouncycastle:bcprov-jdk18on", "< 1.85", "1.85", severity="critical"),
    alert(6, "org.bouncycastle:bcpkix-jdk18on", ">= 1.49, < 1.84", "1.84", severity="medium"),
    alert(15, KOTLIN, "< 2.4.20-Beta1", "2.4.20-Beta1", severity="medium"),
    alert(1, "cryptography", ">= 44.0.0, < 50.0.0", "50.0.0", ecosystem="pip"),
]


def groups():
    return {g["id"]: g for g in alerts.group_alerts([alerts.normalise(a) for a in RAW])}


def dates(*pairs):
    return [(version, datetime.date.fromisoformat(day)) for version, day in pairs]


JACKSON2_VERSIONS = dates(
    ("2.18.10", "2026-08-14"),
    ("2.18.11", "2026-09-21"),
    ("2.21.6", "2026-08-14"),
    ("2.21.7", "2026-09-21"),
    ("2.22.1", "2026-07-07"),
    ("2.22.2", "2026-08-16"),
    ("2.22.3", "2026-09-21"),
    ("2.23.0-rc1", "2026-09-30"),
)
JACKSON3_VERSIONS = dates(
    ("3.1.5", "2026-07-08"),
    ("3.1.6", "2026-08-14"),
    ("3.1.7", "2026-09-22"),
    ("3.2.2", "2026-08-14"),
    ("3.2.3", "2026-09-22"),
)


class VersionTest(unittest.TestCase):
    def test_orders_releases_and_prereleases(self):
        ordered = ["2.4.0", "2.4.20-Beta1", "2.4.20-RC1", "2.4.20", "4.2.0-M2", "4.2.0"]
        self.assertEqual(sorted(reversed(ordered), key=alerts.version_key), ordered)
        self.assertLess(alerts.version_key("1.85"), alerts.version_key("1.85.2"))
        self.assertLess(alerts.version_key("1.85.2"), alerts.version_key("1.86"))
        self.assertLess(alerts.version_key("2.22.3"), alerts.version_key("2.22.10"))
        self.assertEqual(alerts.compare("1.86", "1.86.0"), 0)
        self.assertLess(alerts.version_key("3.0.0-rc4"), alerts.version_key("3.0.0"))
        self.assertLess(alerts.version_key("50.0.0"), alerts.version_key("50.0.0.post1"))

    def test_prereleases(self):
        for version in ("2.4.20-Beta1", "4.2.0-M2", "3.0.0-rc4", "1.0-SNAPSHOT", "50.0.0rc1", "1.0.0a1", "2.0.0-alpha.3"):
            self.assertTrue(alerts.is_prerelease(version), version)
        for version in ("1.85.2", "2.22.3", "7.8.0.202609011348-r", "33.0-jre", "50.0.0.post1"):
            self.assertFalse(alerts.is_prerelease(version), version)

    def test_ranges(self):
        self.assertTrue(alerts.in_range("2.21.6", ">= 2.19.0, <= 2.21.6"))
        self.assertFalse(alerts.in_range("2.21.7", ">= 2.19.0, <= 2.21.6"))
        self.assertFalse(alerts.in_range("2.18.11", ">= 2.19.0, <= 2.21.6"))
        self.assertTrue(alerts.in_range("1.84", "< 1.85"))
        self.assertFalse(alerts.in_range("1.85.2", "< 1.85"))
        self.assertTrue(alerts.in_range("2.4.0", "< 2.4.20-Beta1"))
        self.assertTrue(alerts.in_range("1.2.3", "= 1.2.3"))
        with self.assertRaises(ValueError):
            alerts.in_range("1.0", "~> 1.0")


class GroupTest(unittest.TestCase):
    def test_groups_release_families(self):
        self.assertEqual(
            sorted(groups()),
            [
                "maven:bouncycastle",
                "maven:jackson2",
                "maven:jackson3",
                "maven:" + KOTLIN,
                "pip:cryptography",
            ],
        )
        bouncy = groups()["maven:bouncycastle"]
        self.assertEqual(bouncy["packages"], ["org.bouncycastle:bcpkix-jdk18on", "org.bouncycastle:bcprov-jdk18on"])
        self.assertEqual(bouncy["severity"], "critical")

    def test_minimum_per_line(self):
        jackson2 = groups()["maven:jackson2"]
        self.assertEqual(jackson2["alert_numbers"], [4, 28, 29, 42, 43, 48])
        self.assertEqual(
            jackson2["minimum_per_line"],
            {"2.15": "2.15.0", "2.18": "2.18.11", "2.21": "2.21.7", "2.22": "2.22.3"},
        )
        self.assertEqual(jackson2["severity"], "high")

    def test_most_severe_group_first(self):
        ordered = alerts.group_alerts([alerts.normalise(a) for a in RAW])
        self.assertEqual(ordered[0]["id"], "maven:bouncycastle")

    def test_select(self):
        normalised = [alerts.normalise(a) for a in RAW]
        self.assertEqual([a["number"] for a in alerts.select(normalised, "pip")], [1])
        self.assertEqual([a["number"] for a in alerts.select(normalised, None, [4, 15])], [4, 15])


class MaturityTest(unittest.TestCase):
    def test_mature_after_the_minimum_age(self):
        by_version = {v["version"]: v for v in alerts.dated(JACKSON3_VERSIONS, TODAY, 14)}
        self.assertTrue(by_version["3.1.7"]["mature"])
        self.assertEqual(by_version["3.2.3"]["eligible_from"], "2026-10-06")
        early = {v["version"]: v for v in alerts.dated(JACKSON3_VERSIONS, datetime.date(2026, 10, 5), 14)}
        self.assertFalse(early["3.2.3"]["mature"])
        self.assertTrue(early["3.1.6"]["mature"])

    def test_sorted_by_date_and_undated_versions_never_mature(self):
        result = alerts.dated(dates(("2.0", "2026-01-01")) + [("2.1", None)], TODAY, 14)
        self.assertEqual([v["version"] for v in result], ["2.1", "2.0"])
        self.assertFalse(result[0]["mature"])


class CandidatesTest(unittest.TestCase):
    def test_jackson2_on_the_line_in_use(self):
        versions = alerts.dated(JACKSON2_VERSIONS, TODAY, 14)
        result = alerts.candidates(groups()["maven:jackson2"], versions, current="2.22.1")
        self.assertEqual(result["suggested"]["version"], "2.22.3")
        self.assertFalse(result["leaves_line"])
        self.assertEqual(result["waiting"], [])

    def test_jackson2_without_current_uses_the_highest_line(self):
        versions = alerts.dated(JACKSON2_VERSIONS, TODAY, 14)
        self.assertEqual(alerts.candidates(groups()["maven:jackson2"], versions)["suggested"]["version"], "2.22.3")

    def test_jackson3_stays_on_its_line(self):
        versions = alerts.dated(JACKSON3_VERSIONS, TODAY, 14)
        result = alerts.candidates(groups()["maven:jackson3"], versions, current="3.1.5")
        self.assertEqual(result["suggested"]["version"], "3.1.7")
        self.assertEqual(result["newest_same_major"]["version"], "3.2.3")

    def test_leaves_the_line_while_its_fix_is_not_mature(self):
        versions = alerts.dated(JACKSON3_VERSIONS, datetime.date(2026, 10, 1), 14)
        result = alerts.candidates(groups()["maven:jackson3"], versions, current="3.1.5")
        self.assertEqual(result["suggested"]["version"], "3.2.2")
        self.assertTrue(result["leaves_line"])
        self.assertEqual([v["version"] for v in result["waiting"]], ["3.1.7", "3.2.3"])

    def test_waiting_when_no_fix_is_mature(self):
        versions = alerts.dated(dates(("3.1.7", "2026-09-22")), datetime.date(2026, 10, 1), 14)
        result = alerts.candidates(groups()["maven:jackson3"], versions, current="3.1.5")
        self.assertIsNone(result["suggested"])
        self.assertEqual([(v["version"], v["eligible_from"]) for v in result["waiting"]], [("3.1.7", "2026-10-06")])

    def test_kotlin_needs_a_prerelease(self):
        versions = alerts.dated(
            dates(("2.4.0", "2026-06-01"), ("2.4.10", "2026-08-01"), ("2.4.20-Beta1", "2026-09-01")), TODAY, 14
        )
        result = alerts.candidates(groups()["maven:" + KOTLIN], versions, current="2.4.0")
        self.assertIsNone(result["suggested"])
        self.assertEqual([v["version"] for v in result["needs_prerelease"]], ["2.4.20-Beta1"])

    def test_needs_a_major(self):
        group = alerts.group_alerts([alerts.normalise(alert(1, "x:y", "< 2.0.0", "2.0.0"))])[0]
        versions = alerts.dated(dates(("1.9.0", "2026-01-01"), ("2.0.0", "2026-02-01")), TODAY, 14)
        result = alerts.candidates(group, versions, current="1.9.0")
        self.assertIsNone(result["suggested"])
        self.assertEqual([v["version"] for v in result["needs_major"]], ["2.0.0"])


class Json5Test(unittest.TestCase):
    def test_constructs(self):
        text = """{
          // a comment with "quotes"
          key: 'single "quoted"',  /* block */
          url: "https://example.org/a//b",
          list: [1, 2e3, true, null,],
          "escaped": "a\\\\b",
        }"""
        self.assertEqual(
            alerts.parse_json5(text),
            {"key": 'single "quoted"', "url": "https://example.org/a//b", "list": [1, 2000.0, True, None], "escaped": "a\\b"},
        )

    def test_reads_the_repository_config(self):
        config = alerts.load_renovate()
        self.assertEqual(config["minimumReleaseAge"], "14 days")
        self.assertEqual(alerts.min_age_days(config), 14)
        self.assertEqual(config["$schema"], "https://docs.renovatebot.com/renovate-schema.json")
        descriptions = [r.get("description") for r in config["packageRules"]]
        for expected in (
            "All dependencies: one pull request a week",
            "Node.js: even (LTS) majors only",
            "Java stays on 25",
            "TypeScript 6 compatibility package in the frontend (#105)",
        ):
            self.assertIn(expected, descriptions)


class RenovateTest(unittest.TestCase):
    config = alerts.load_renovate()

    def test_node_odd_major_is_not_allowed(self):
        result = alerts.renovate_findings(self.config, "node", "25.0.0")
        self.assertTrue(result["breaks"])
        self.assertEqual(result["rules"][0]["description"], "Node.js: even (LTS) majors only")
        self.assertFalse(alerts.renovate_findings(self.config, "node", "26.1.0")["breaks"])

    def test_java_major_is_disabled(self):
        result = alerts.renovate_findings(self.config, "java", "26", current="25")
        self.assertTrue(result["breaks"])
        self.assertIn("Java stays on 25", [r["description"] for r in result["rules"]])
        self.assertFalse(alerts.renovate_findings(self.config, "java", "25.0.5", current="25.0.4")["breaks"])

    def test_typescript6_notes(self):
        result = alerts.renovate_findings(self.config, "@typescript/typescript6", "6.0.4")
        self.assertFalse(result["breaks"])
        self.assertEqual(len(result["prBodyNotes"]), 1)
        self.assertIn("#105", result["prBodyNotes"][0])

    def test_unrelated_package(self):
        result = alerts.renovate_findings(self.config, "org.bouncycastle:bcprov-jdk18on", "1.86")
        self.assertEqual(result["rules"], [])
        self.assertFalse(result["breaks"])
        self.assertEqual(result["min_age_days"], 14)

    def test_name_patterns(self):
        self.assertTrue(alerts.name_matches(["/^@types//"], "@types/node"))
        self.assertTrue(alerts.name_matches(["org.apache.*"], "org.apache.tomcat"))
        self.assertFalse(alerts.name_matches(["*", "!node"], "node"))

    def test_allowed_ranges(self):
        self.assertTrue(alerts.allowed("<2.0", "1.9"))
        self.assertFalse(alerts.allowed(">=1.2 <2", "2.1"))
        self.assertIsNone(alerts.allowed("latest", "1.0"))


# `./gradlew -q dependencies :backend:library:persistence:dependencies` before #128: the test
# fixtures resolved Jackson 3.1.5, the main classpath 3.1.7.
GRADLE_REPORT = """
------------------------------------------------------------
Root project 'trading-analysis-platform'
------------------------------------------------------------

No configurations

------------------------------------------------------------
Project ':backend:library:persistence'
------------------------------------------------------------

compileClasspath - Compile classpath for source set 'main'.
+--- org.springframework.boot:spring-boot-dependencies:4.1.1
|    +--- tools.jackson.core:jackson-core:3.1.5 -> 3.1.7 (c)
|    \\--- tools.jackson.core:jackson-databind:3.1.5 -> 3.1.7 (c)
+--- tools.jackson:jackson-bom:3.1.7
+--- org.springframework.boot:spring-boot-jackson -> 4.1.1
|    \\--- tools.jackson.core:jackson-databind:3.1.5 -> 3.1.7
|         \\--- tools.jackson.core:jackson-core:3.1.7
\\--- project :backend:library:core

implementation - Implementation dependencies for the 'main' feature. (n)
+--- tools.jackson:jackson-bom:3.1.7 (n)
\\--- tools.jackson.core:jackson-databind:3.1.5 (n)

testFixturesCompileClasspath - Compile classpath for source set 'test fixtures'.
+--- org.springframework.boot:spring-boot-dependencies:4.1.1
|    \\--- tools.jackson.core:jackson-databind:3.1.5 (c)
+--- org.springframework.boot:spring-boot-jackson -> 4.1.1
|    \\--- tools.jackson.core:jackson-databind:3.1.5
|         \\--- tools.jackson.core:jackson-core:3.1.5
+--- org.example:missing:1.0 FAILED
\\--- org.springframework.boot:spring-boot-jackson -> 4.1.1 (*)

testFixturesRuntimeClasspath - Runtime classpath of source set 'test fixtures'.
No dependencies
"""


def resolved(records, configuration):
    return [(r["package"], r["resolved"]) for r in records if r["configuration"] == configuration]


class GradleScanTest(unittest.TestCase):
    def setUp(self):
        self.records = alerts.parse_gradle_report(GRADLE_REPORT)

    def test_replaced_versions_count_as_their_replacement(self):
        self.assertEqual(
            resolved(self.records, "compileClasspath"),
            [
                ("org.springframework.boot:spring-boot-dependencies", "4.1.1"),
                ("tools.jackson:jackson-bom", "3.1.7"),
                ("org.springframework.boot:spring-boot-jackson", "4.1.1"),
                (DATABIND3, "3.1.7"),
                ("tools.jackson.core:jackson-core", "3.1.7"),
            ],
        )

    def test_constraints_projects_failed_and_unresolved_configurations_are_left_out(self):
        self.assertEqual(resolved(self.records, "implementation"), [])
        self.assertEqual(
            resolved(self.records, "testFixturesCompileClasspath"),
            [
                ("org.springframework.boot:spring-boot-dependencies", "4.1.1"),
                ("org.springframework.boot:spring-boot-jackson", "4.1.1"),
                (DATABIND3, "3.1.5"),
                ("tools.jackson.core:jackson-core", "3.1.5"),
                ("org.springframework.boot:spring-boot-jackson", "4.1.1"),
            ],
        )
        self.assertEqual(resolved(self.records, "testFixturesRuntimeClasspath"), [])

    def test_project_paths(self):
        self.assertEqual({r["project"] for r in self.records}, {":backend:library:persistence"})
        self.assertEqual(alerts.parse_gradle_report("Root project 'x'\n\nclasspath\n\\--- a:b:1.0\n")[0]["project"], ":")

    def test_only_vulnerable_versions_are_reported(self):
        jackson3 = next(g for g in alerts.group_alerts([alerts.normalise(a) for a in RAW]) if g["id"] == "maven:jackson3")
        result = alerts.vulnerable_records([jackson3], self.records)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["id"], "maven:jackson3")
        self.assertEqual(
            [(r["configuration"], r["package"], r["version"], r["alerts"]) for r in result[0]["resolved"]],
            [("testFixturesCompileClasspath", DATABIND3, "3.1.5", [26, 37])],
        )

    def test_nothing_vulnerable(self):
        fixed = [r for r in self.records if r["configuration"] == "compileClasspath"]
        groups = alerts.group_alerts([alerts.normalise(a) for a in RAW])
        self.assertEqual(alerts.vulnerable_records(groups, fixed), [])


def api_alert(state, vulnerable_range=">= 3.0.0, <= 3.1.6"):
    return {"number": 37, "state": state, "security_vulnerability": {"vulnerable_version_range": vulnerable_range}}


class AfterMergeTest(unittest.TestCase):
    def test_fixed_or_dismissed(self):
        self.assertEqual(alerts.after_merge_status(api_alert("fixed"), False, set()), {"number": 37, "status": "fixed"})
        self.assertEqual(alerts.after_merge_status(api_alert("dismissed"), True, set())["status"], "dismissed")

    def test_submission_not_run_yet(self):
        status = alerts.after_merge_status(api_alert("open"), False, {"3.1.5"})
        self.assertEqual(status["status"], "open: submission not run yet")

    def test_still_in_the_graph(self):
        status = alerts.after_merge_status(api_alert("open"), True, {"3.1.7", "3.1.5", "3.0.1"})
        self.assertEqual(status, {"number": 37, "status": "open: still in the graph", "versions": ["3.0.1", "3.1.5"]})

    def test_gone_from_the_graph(self):
        status = alerts.after_merge_status(api_alert("open"), True, {"3.1.7"})
        self.assertEqual(status["status"], "open: not in the graph any more")
        self.assertIn("wait", status["note"])

    def test_versions_it_cannot_read_are_skipped(self):
        status = alerts.after_merge_status(api_alert("open"), True, {"7.*.*", "3.1.7"})
        self.assertNotEqual(status["status"], "open: still in the graph")
        status = alerts.after_merge_status(api_alert("open"), True, {"7.*.*", "3.1.5"})
        self.assertEqual(status["versions"], ["3.1.5"])
        # A wildcard on the vulnerable line is not a version either (version_key reads it as 3-post).
        status = alerts.after_merge_status(api_alert("open"), True, {"3.*", "3.1.*", "3.1.7"})
        self.assertEqual(status["status"], "open: not in the graph any more")

    def test_real_sbom_names_match_the_alerts(self):
        # From `gh api repos/{owner}/{repo}/dependency-graph/sbom` on 2026-10-09: the names carry no
        # ecosystem prefix, so they match the alerts' package names as they are.
        sbom = {
            "sbom": {
                "packages": [
                    {"name": "cryptography", "versionInfo": "50.0.1"},
                    {"name": "cryptography", "versionInfo": "48.0.1"},
                    {"name": DATABIND3, "versionInfo": "3.1.5"},
                    {"name": DATABIND3, "versionInfo": "3.1.7"},
                    {"name": "actions/checkout", "versionInfo": "7.*.*"},
                ]
            }
        }
        graph = alerts.sbom_versions(sbom)
        status = alerts.after_merge_status(api_alert("open"), True, graph[DATABIND3])
        self.assertEqual(status["versions"], ["3.1.5"])
        self.assertEqual(graph["cryptography"], {"48.0.1", "50.0.1"})

    def test_sbom_versions(self):
        sbom = {
            "sbom": {
                "packages": [
                    {"name": DATABIND3, "versionInfo": "3.1.7"},
                    {"name": DATABIND3, "versionInfo": "3.1.5"},
                    {"name": "com.github.ramazangirgin/trading-analysis-platform"},
                ]
            }
        }
        self.assertEqual(alerts.sbom_versions(sbom), {DATABIND3: {"3.1.5", "3.1.7"}})


if __name__ == "__main__":
    unittest.main()
