"""Helper of the dependabot-fix skill (SKILL.md next to it): the parts that must be exact.

    alerts.py alerts [--ecosystem <e>] [<number>...]
        the open Dependabot alerts, grouped by ecosystem and release family, with the highest
        severity and the minimum fixed version per release line
    alerts.py versions <ecosystem> <package> [--today YYYY-MM-DD]
        the released versions with their date, flagged prerelease and mature, and eligible_from
    alerts.py candidates <group-id> [--alerts <file>] [--current <version>] [--today YYYY-MM-DD]
        the versions that fix every alert of a group: the suggestion (newest mature, stable, on the
        current line or major), and the ones waiting, needing a pre-release or a major
    alerts.py renovate <ecosystem> <package> <version> [--current <version>]
        the rules of .github/renovate.json5 that touch the package, and whether the version breaks one
    alerts.py gradle-scan [--alerts <file>] [<group-id>...]
        every configuration of every Gradle project (root and included builds, build script
        classpaths too) that resolves a version in a vulnerable range of the Maven groups' alerts;
        an empty list when none does
    alerts.py after-merge [--merge <sha>] <number>...
        after the merge: per alert, fixed, or still open and why (the dependency submission on main
        has not run on the merge yet, or the graph still has a vulnerable version)

Every command prints JSON on stdout. "Mature" is Renovate's minimumReleaseAge, read from
.github/renovate.json5 on every call (14 days when it is not set): pnpm and uv enforce the same age.
<ecosystem> is Dependabot's name: maven, pip, npm. A Maven <package> is group:artifact.

Maven release dates come from the Last-Modified header of each version's .pom on Maven Central, not
from search.maven.org, whose index stops in mid-2025 (checked on 2026-10-08).

Standard library only: run with `uv run --no-project python`.
"""

import argparse
import datetime
import email.utils
import fnmatch
import json
import pathlib
import re
import subprocess
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ElementTree

REPO_ROOT = pathlib.Path(__file__).resolve().parents[3]
RENOVATE_CONFIG = REPO_ROOT / ".github" / "renovate.json5"
DEFAULT_MIN_AGE_DAYS = 14
# Only the newest versions get a date: one HEAD request each on Maven Central.
DATED_VERSIONS = 30

# Libraries released together under one version: one group, one target version.
FAMILIES = [
    ("maven", re.compile(r"com\.fasterxml\.jackson\.core:.+"), "jackson2"),
    ("maven", re.compile(r"tools\.jackson\.core:.+"), "jackson3"),
    ("maven", re.compile(r"org\.bouncycastle:.+-jdk18on"), "bouncycastle"),
    ("maven", re.compile(r"org\.apache\.tomcat\.embed:tomcat-embed-.+"), "tomcat"),
]

SEVERITY = ["low", "medium", "high", "critical"]


def die(message):
    print(f"alerts.py: {message}", file=sys.stderr)
    sys.exit(1)


# --- Versions and ranges ---------------------------------------------------------------------

# Qualifier rank: below 0 is a pre-release, 0 a release, above 0 a post-release.
PRERELEASE_QUALIFIERS = [
    (re.compile(r"(dev)\.?(\d*)"), -6),
    (re.compile(r"(alpha|a)[.-]?(\d*)"), -5),
    (re.compile(r"(beta|b)[.-]?(\d*)"), -4),
    (re.compile(r"(milestone|m)[.-]?(\d*)"), -3),
    (re.compile(r"(rc|cr|pre|preview|next|canary)[.-]?(\d*)"), -2),
    (re.compile(r"(snapshot)()"), -1),
]
RELEASE_QUALIFIERS = {"", "ga", "final", "release", "r", "jre", "android"}

VERSION = re.compile(r"v?(\d+(?:\.\d+)*)(.*)")


def version_key(version):
    """A sort key for Maven, PEP 440 and npm versions: release numbers, then the qualifier."""
    match = VERSION.fullmatch(version.strip())
    if not match:
        return ((), 1, 0, version)
    release = [int(part) for part in match.group(1).split(".")]
    while len(release) > 1 and release[-1] == 0:
        release.pop()
    qualifier = match.group(2).lower().lstrip(".-_+")
    if qualifier in RELEASE_QUALIFIERS:
        return (tuple(release), 0, 0, "")
    for pattern, rank in PRERELEASE_QUALIFIERS:
        found = pattern.match(qualifier)
        if found:
            return (tuple(release), rank, int(found.group(2) or 0), qualifier)
    # A post-release (post1, sp1, a date) after "-" or ".", or a qualifier this table does not know.
    return (tuple(release), 1, 0, qualifier)


def is_prerelease(version):
    return version_key(version)[1] < 0


def major(version):
    release = version_key(version)[0]
    return release[0] if release else None


def line(version):
    """The release line: major.minor (2.22 for 2.22.3)."""
    match = VERSION.fullmatch(version.strip())
    if not match:
        return version
    parts = match.group(1).split(".")
    return ".".join((parts + ["0"])[:2])


def compare(a, b):
    ka, kb = version_key(a), version_key(b)
    return (ka > kb) - (ka < kb)


OPERATORS = {
    "<": lambda c: c < 0,
    "<=": lambda c: c <= 0,
    ">": lambda c: c > 0,
    ">=": lambda c: c >= 0,
    "=": lambda c: c == 0,
    "==": lambda c: c == 0,
}
CONDITION = re.compile(r"\s*(<=|>=|==|<|>|=)\s*(\S+)\s*")


def in_range(version, vulnerable_range):
    """True when the version is in GitHub's range syntax: ">= 2.19.0, <= 2.21.6", "< 1.85"."""
    for condition in vulnerable_range.split(","):
        match = CONDITION.fullmatch(condition)
        if not match:
            raise ValueError(f"unknown range: {vulnerable_range!r}")
        if not OPERATORS[match.group(1)](compare(version, match.group(2))):
            return False
    return True


# --- Alerts ----------------------------------------------------------------------------------


def fetch_alerts():
    """Every open alert, from the GitHub API (one JSON object per line)."""
    out = subprocess.run(
        ["gh", "api", "--paginate", "repos/{owner}/{repo}/dependabot/alerts?state=open&per_page=100", "--jq", ".[]"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    return [json.loads(row) for row in out.splitlines() if row.strip()]


def normalise(alert):
    advisory = alert.get("security_advisory") or {}
    vulnerability = alert.get("security_vulnerability") or {}
    dependency = alert.get("dependency") or {}
    package = dependency.get("package") or {}
    patched = vulnerability.get("first_patched_version") or {}
    return {
        "number": alert["number"],
        "ecosystem": package.get("ecosystem"),
        "package": package.get("name"),
        "manifest": dependency.get("manifest_path"),
        "scope": dependency.get("scope"),
        "severity": advisory.get("severity") or vulnerability.get("severity"),
        "ghsa": advisory.get("ghsa_id"),
        "cve": advisory.get("cve_id"),
        "summary": advisory.get("summary"),
        "vulnerable_range": vulnerability.get("vulnerable_version_range"),
        "first_patched": patched.get("identifier"),
        "url": alert.get("html_url"),
    }


def family(ecosystem, package):
    for family_ecosystem, pattern, name in FAMILIES:
        if ecosystem == family_ecosystem and pattern.fullmatch(package):
            return name
    return package


def group_alerts(alerts):
    """Groups normalised alerts by ecosystem and release family, most severe group first."""
    groups = {}
    for alert in alerts:
        name = family(alert["ecosystem"], alert["package"])
        group = groups.setdefault(
            f"{alert['ecosystem']}:{name}",
            {"ecosystem": alert["ecosystem"], "family": name, "packages": set(), "manifests": set(), "alerts": []},
        )
        group["packages"].add(alert["package"])
        group["manifests"].add(alert["manifest"])
        group["alerts"].append(alert)
    result = []
    for group_id, group in groups.items():
        alerts_of_group = sorted(group["alerts"], key=lambda a: a["number"])
        minimum = {}
        for alert in alerts_of_group:
            patched = alert["first_patched"]
            if not patched:
                continue
            current = minimum.get(line(patched))
            if current is None or compare(patched, current) > 0:
                minimum[line(patched)] = patched
        result.append(
            {
                "id": group_id,
                "ecosystem": group["ecosystem"],
                "family": group["family"],
                "packages": sorted(group["packages"]),
                "manifests": sorted(m for m in group["manifests"] if m),
                "severity": max((a["severity"] for a in alerts_of_group), key=severity_rank),
                "alert_numbers": [a["number"] for a in alerts_of_group],
                "advisories": sorted({a["ghsa"] for a in alerts_of_group if a["ghsa"]}),
                "minimum_per_line": dict(sorted(minimum.items(), key=lambda item: version_key(item[0]))),
                "unpatched": [a["number"] for a in alerts_of_group if not a["first_patched"]],
                "alerts": alerts_of_group,
            }
        )
    return sorted(result, key=lambda g: (-severity_rank(g["severity"]), -len(g["alerts"]), g["id"]))


def severity_rank(severity):
    return SEVERITY.index(severity) if severity in SEVERITY else -1


def select(alerts, ecosystem=None, numbers=()):
    wanted = set(numbers)
    return [
        a
        for a in alerts
        if (ecosystem is None or a["ecosystem"] == ecosystem) and (not wanted or a["number"] in wanted)
    ]


# --- Versions and their dates ----------------------------------------------------------------


def http(url, method="GET"):
    request = urllib.request.Request(url, method=method, headers={"User-Agent": "dependabot-fix-skill"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.headers, response.read()


def fetch_versions(ecosystem, package):
    """[(version, date or None)] from the package's registry."""
    if ecosystem == "maven":
        group_id, artifact = package.split(":", 1)
        base = f"https://repo1.maven.org/maven2/{group_id.replace('.', '/')}/{artifact}"
        _, body = http(f"{base}/maven-metadata.xml")
        versions = [v.text for v in ElementTree.fromstring(body).iter("version") if v.text]
        newest = sorted(versions, key=version_key)[-DATED_VERSIONS:]
        result = []
        for version in newest:
            try:
                headers, _ = http(f"{base}/{version}/{artifact}-{version}.pom", method="HEAD")
                result.append((version, email.utils.parsedate_to_datetime(headers["Last-Modified"]).date()))
            except Exception:  # noqa: BLE001 - a version without a .pom keeps no date
                result.append((version, None))
        return result
    if ecosystem == "pip":
        _, body = http(f"https://pypi.org/pypi/{urllib.parse.quote(package)}/json")
        result = []
        for version, files in json.loads(body)["releases"].items():
            uploads = [f["upload_time_iso_8601"][:10] for f in files if not f.get("yanked")]
            if uploads:
                result.append((version, datetime.date.fromisoformat(min(uploads))))
        return result
    if ecosystem == "npm":
        _, body = http(f"https://registry.npmjs.org/{urllib.parse.quote(package, safe='@')}")
        times = json.loads(body).get("time", {})
        return [
            (version, datetime.date.fromisoformat(stamp[:10]))
            for version, stamp in times.items()
            if version not in ("created", "modified")
        ]
    die(f"no registry for the ecosystem {ecosystem!r} (maven, pip, npm)")


def dated(versions, today, min_age_days):
    """The versions, oldest first, flagged prerelease, mature and eligible_from."""
    result = []
    for version, date in sorted(versions, key=lambda item: version_key(item[0])):
        eligible = date + datetime.timedelta(days=min_age_days) if date else None
        result.append(
            {
                "version": version,
                "date": date.isoformat() if date else None,
                "prerelease": is_prerelease(version),
                "mature": bool(eligible and eligible <= today),
                "eligible_from": eligible.isoformat() if eligible else None,
            }
        )
    return sorted(result, key=lambda v: (v["date"] or "", version_key(v["version"])))


def candidates(group, versions, current=None):
    """The dated versions that fix every alert of the group, sorted into what to do with them.

    current: the version in use (from Gradle, uv or pnpm). Without it, the group's highest
    release line stands in for it.
    """
    alerts = group["alerts"]
    lines = list(group["minimum_per_line"].values())
    reference = current or (max(lines, key=version_key) if lines else None)
    floor = min((a["first_patched"] for a in alerts if a["first_patched"]), key=version_key, default=None)

    def fixes(version):
        if floor and compare(version, floor) < 0:
            return False
        return not any(a["vulnerable_range"] and in_range(version, a["vulnerable_range"]) for a in alerts)

    fixing = [v for v in versions if fixes(v["version"])]
    stable = [v for v in fixing if not v["prerelease"]]
    same_major = [v for v in stable if reference is None or major(v["version"]) == major(reference)]
    same_line = [v for v in same_major if reference is None or line(v["version"]) == line(reference)]

    def newest(entries):
        mature = [v for v in entries if v["mature"]]
        return max(mature, key=lambda v: version_key(v["version"])) if mature else None

    suggested = newest(same_line) or newest(same_major)
    result = {
        "group": group["id"],
        "current": current,
        "reference": reference,
        "alert_numbers": group["alert_numbers"],
        "suggested": suggested,
        "leaves_line": bool(suggested and reference and line(suggested["version"]) != line(reference)),
        "newest_same_major": newest(same_major),
        "waiting": [v for v in same_major if not v["mature"]],
        "needs_prerelease": [],
        "needs_major": [],
    }
    if not same_major:
        result["needs_major"] = [v for v in stable if v not in same_major]
        if not stable:
            result["needs_prerelease"] = [v for v in fixing if v["prerelease"]]
    if group["unpatched"]:
        result["unpatched"] = group["unpatched"]
    return result


# --- Renovate rules --------------------------------------------------------------------------


def parse_json5(text):
    """JSON5 as Renovate's config uses it: comments, unquoted keys, single quotes, trailing commas."""
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c in "\"'":
            j, chars = i + 1, []
            while j < n and text[j] != c:
                if text[j] == "\\":
                    chars.append(text[j : j + 2])
                    j += 2
                else:
                    chars.append(text[j])
                    j += 1
            raw = "".join(chars)
            if c == "'":
                raw = raw.replace("\\'", "'").replace('"', '\\"')
            out.append('"' + raw + '"')
            i = j + 1
        elif text.startswith("//", i):
            end = text.find("\n", i)
            i = n if end < 0 else end
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = n if end < 0 else end + 2
        elif (c.isalpha() or c in "_$") and not (out and out[-1][-1:].isdigit()):
            j = i
            while j < n and (text[j].isalnum() or text[j] in "_$"):
                j += 1
            word = text[i:j]
            out.append(word if word in ("true", "false", "null") else json.dumps(word))
            i = j
        elif c in "}]":
            while out and out[-1].isspace():
                out.pop()
            if out and out[-1] == ",":
                out.pop()
            out.append(c)
            i += 1
        else:
            out.append(c)
            i += 1
    return json.loads("".join(out))


def load_renovate(path=RENOVATE_CONFIG):
    return parse_json5(pathlib.Path(path).read_text(encoding="utf-8"))


def min_age_days(config):
    value = str(config.get("minimumReleaseAge", f"{DEFAULT_MIN_AGE_DAYS} days"))
    match = re.match(r"\s*(\d+)\s*(day|days|d)?\s*$", value)
    if not match:
        die(f"minimumReleaseAge {value!r}: only days are supported")
    return int(match.group(1))


def name_matches(patterns, name):
    """Renovate's matchPackageNames / matchDepNames: exact, glob, /regex/, and !negations."""
    positive = [p for p in patterns if not p.startswith("!")]
    negative = [p[1:] for p in patterns if p.startswith("!")]

    def one(pattern):
        if len(pattern) > 1 and pattern.startswith("/") and pattern.rstrip("i").endswith("/"):
            flags = re.IGNORECASE if pattern.endswith("i") else 0
            return re.search(pattern[1 : pattern.rstrip("i").rindex("/")], name, flags) is not None
        return fnmatch.fnmatchcase(name, pattern)

    return (not positive or any(one(p) for p in positive)) and not any(one(p) for p in negative)


def update_type(current, version):
    a, b = version_key(current)[0], version_key(version)[0]
    a, b = list(a) + [0, 0], list(b) + [0, 0]
    if a[0] != b[0]:
        return "major"
    return "minor" if a[1] != b[1] else "patch"


def allowed(spec, version):
    """Renovate's allowedVersions: a /regex/ or a range ("<4", ">=1.2 <2"); None when unknown."""
    if len(spec) > 1 and spec.startswith("/") and spec.endswith("/"):
        return re.search(spec[1:-1], version) is not None
    conditions = re.findall(r"(<=|>=|==|<|>|=)\s*([^\s,]+)", spec)
    if not conditions:
        return None
    return all(OPERATORS[op](compare(version, bound)) for op, bound in conditions)


def renovate_findings(config, package, version, current=None):
    """The rules of the config that touch the package, and what they mean for the version."""
    rules = []
    for rule in config.get("packageRules", []):
        names = rule.get("matchPackageNames", []) + rule.get("matchDepNames", [])
        if not names or names == ["*"] or not name_matches(names, package):
            continue
        effects = {k: rule[k] for k in ("enabled", "allowedVersions", "prBodyNotes") if k in rule}
        if not effects:
            continue
        finding = {"description": rule.get("description"), **effects}
        conditions = {k: v for k, v in rule.items() if k.startswith("match") and k not in ("matchPackageNames", "matchDepNames")}
        if conditions:
            finding["conditions"] = conditions
        types = rule.get("matchUpdateTypes")
        if types and current:
            kind = update_type(current, version)
            if kind not in types:
                continue
            finding["update_type"] = kind
        elif types:
            finding["update_type"] = "unknown: pass --current"
        if "allowedVersions" in rule:
            finding["allowed"] = allowed(rule["allowedVersions"], version)
        finding["breaks"] = rule.get("enabled") is False or finding.get("allowed") is False
        rules.append(finding)
    return {
        "package": package,
        "version": version,
        "minimumReleaseAge": config.get("minimumReleaseAge"),
        "min_age_days": min_age_days(config),
        "rules": rules,
        "breaks": any(r["breaks"] for r in rules),
        "prBodyNotes": [note for r in rules for note in r.get("prBodyNotes", [])],
    }


# --- Gradle: every configuration of every project --------------------------------------------

# GitHub's Automatic Dependency Submission runs Gradle on every push to main and submits the
# resolved graph of every configuration (test fixtures included), under settings.gradle.kts. A
# vulnerable version that any configuration still resolves keeps its alert open, so the check has to
# look at every one of them, not only the runtime classpath.

# The builds of the repository: the root build and the included builds. Each runs `dependencies`
# for every project, and `buildEnvironment` for its build script classpath (the plugins).
GRADLE_BUILDS = [".", "build-logic", "build-logic/checkstyle-rules"]

PROJECT_HEADER = re.compile(r"(Root project|Project) '([^']*)'.*")
CONFIGURATION_HEADER = re.compile(r"([A-Za-z][\w]*)(?: - (.*))?")
TREE_LINE = re.compile(r"[| ]*[+\\]--- (.+)")


def parse_dependency(text):
    """One tree entry ("g:n:1.0 -> 1.1 (*)") as (package, requested, resolved, flag); None for a
    project, a FAILED one, or an entry that is not a module."""
    flag = None
    flag_match = re.fullmatch(r"(.*?) \(([c*n])\)", text)
    if flag_match:
        text, flag = flag_match.groups()
    if text.startswith("project ") or text.endswith(" FAILED"):
        return None
    coordinates, _, replaced = text.partition(" -> ")
    parts = coordinates.split(":", 2)
    if len(parts) < 2:
        return None
    requested = parts[2] if len(parts) == 3 else None
    resolved = replaced or requested
    if not resolved:
        return None
    return f"{parts[0]}:{parts[1]}", requested, resolved.strip(), flag


def parse_gradle_report(text, build="."):
    """The output of `dependencies` and `buildEnvironment` (one or more projects) as records of
    the versions each configuration resolves. Constraints (c) and configurations that are not
    resolved (n) are left out: they show a declaration, not a resolved version."""
    records = []
    project = None
    configuration = None
    for raw in text.splitlines():
        line_text = raw.rstrip()
        header = PROJECT_HEADER.fullmatch(line_text)
        if header:
            project = ":" if header.group(1) == "Root project" else header.group(2)
            configuration = None
            continue
        entry = TREE_LINE.fullmatch(line_text)
        if entry:
            if configuration is None:
                continue
            parsed = parse_dependency(entry.group(1))
            if parsed is None:
                continue
            package, requested, resolved, flag = parsed
            if flag in ("c", "n"):
                continue
            records.append(
                {
                    "build": build,
                    "project": project or ":",
                    "configuration": configuration,
                    "package": package,
                    "requested": requested,
                    "resolved": resolved,
                }
            )
            continue
        configuration_header = CONFIGURATION_HEADER.fullmatch(line_text)
        if configuration_header and not raw.startswith(" "):
            description = configuration_header.group(2) or ""
            configuration = None if description.endswith("(n)") else configuration_header.group(1)
        elif not line_text:
            configuration = None
    return records


def vulnerable_records(groups, records):
    """Per group, the resolved versions that are in a vulnerable range of one of its alerts."""
    result = []
    for group in groups:
        found = {}
        for record in records:
            for alert in group["alerts"]:
                if record["package"] != alert["package"] or not alert["vulnerable_range"]:
                    continue
                try:
                    vulnerable = in_range(record["resolved"], alert["vulnerable_range"])
                except ValueError:
                    continue
                if vulnerable:
                    key = (record["build"], record["project"], record["configuration"], record["package"], record["resolved"])
                    found.setdefault(key, set()).add(alert["number"])
        if found:
            result.append(
                {
                    "id": group["id"],
                    "resolved": [
                        {
                            "build": build,
                            "project": project,
                            "configuration": configuration,
                            "package": package,
                            "version": version,
                            "alerts": sorted(numbers),
                        }
                        for (build, project, configuration, package, version), numbers in sorted(found.items())
                    ],
                }
            )
    return result


def gradle(build, *arguments):
    command = [str(REPO_ROOT / "gradlew"), "-q", "--console=plain"]
    if build != ".":
        command += ["-p", build]
    out = subprocess.run(command + list(arguments), cwd=REPO_ROOT, check=True, capture_output=True, text=True)
    return out.stdout


def gradle_records():
    """The resolved versions of every configuration of every project of every build."""
    records = []
    for build in GRADLE_BUILDS:
        projects = re.findall(r"Project '(:[^']+)'", gradle(build, "projects"))
        tasks = ["dependencies"] + [f"{project}:dependencies" for project in projects]
        records += parse_gradle_report(gradle(build, *tasks), build)
        environment = gradle(build, "buildEnvironment")
        records += parse_gradle_report(environment, build)
    return records


# --- After the merge -------------------------------------------------------------------------

SUBMISSION_WORKFLOW = "Automatic Dependency Submission"


def after_merge_status(alert, submission_current, graph_versions):
    """What happened to one alert after the merge.

    alert: the alert as the API returns it; submission_current: whether the dependency submission
    on main has run on the merge (or later); graph_versions: the versions of the alert's package in
    the dependency graph (the SBOM)."""
    number = alert["number"]
    state = alert.get("state")
    if state != "open":
        return {"number": number, "status": state}
    if not submission_current:
        return {"number": number, "status": "open: submission not run yet"}
    vulnerable_range = (alert.get("security_vulnerability") or {}).get("vulnerable_version_range") or ""
    still = sorted((v for v in graph_versions if vulnerable(v, vulnerable_range)), key=version_key)
    if still:
        return {"number": number, "status": "open: still in the graph", "versions": still}
    return {"number": number, "status": "open: not in the graph any more, Dependabot not updated yet"}


def vulnerable(version, vulnerable_range):
    """in_range, but False for a version it cannot read (the graph has "7.*.*" for Actions)."""
    try:
        return in_range(version, vulnerable_range)
    except ValueError:
        return False


def sbom_versions(sbom):
    """Package name -> versions in the dependency graph (gh api .../dependency-graph/sbom)."""
    versions = {}
    for package in sbom.get("sbom", {}).get("packages", []):
        name, version = package.get("name"), package.get("versionInfo")
        if name and version:
            versions.setdefault(name, set()).add(version)
    return versions


def gh_json(*arguments):
    out = subprocess.run(["gh", *arguments], check=True, capture_output=True, text=True).stdout
    return json.loads(out)


def submission_current(merge):
    """The latest dependency submission run on main, and whether it ran on the merge or later."""
    runs = gh_json(
        "run", "list", "--workflow", SUBMISSION_WORKFLOW, "--branch", "main", "--limit", "1",
        "--json", "headSha,status,conclusion,createdAt",
    )
    run = runs[0] if runs else None
    if run is None or run["status"] != "completed" or run["conclusion"] != "success":
        return run, False
    if merge is None:
        return run, True
    subprocess.run(["git", "fetch", "--quiet", "origin", "main"], cwd=REPO_ROOT, check=True)
    ancestor = subprocess.run(["git", "merge-base", "--is-ancestor", merge, run["headSha"]], cwd=REPO_ROOT)
    return run, ancestor.returncode == 0


# --- Command line ----------------------------------------------------------------------------


def today_arg(value):
    return datetime.date.fromisoformat(value) if value else datetime.date.today()


def print_json(value):
    json.dump(value, sys.stdout, indent=2, default=sorted)
    sys.stdout.write("\n")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    p = commands.add_parser("alerts")
    p.add_argument("--ecosystem")
    p.add_argument("numbers", nargs="*", type=int)
    p = commands.add_parser("versions")
    p.add_argument("ecosystem")
    p.add_argument("package")
    p.add_argument("--today")
    p = commands.add_parser("candidates")
    p.add_argument("group")
    p.add_argument("--alerts", help="a saved `alerts` output instead of reading the alerts again")
    p.add_argument("--current")
    p.add_argument("--today")
    p = commands.add_parser("renovate")
    p.add_argument("ecosystem")
    p.add_argument("package")
    p.add_argument("version")
    p.add_argument("--current")
    p = commands.add_parser("gradle-scan")
    p.add_argument("groups", nargs="*", help="group ids; all groups when none")
    p.add_argument("--alerts", help="a saved `alerts` output instead of reading the alerts again")
    p = commands.add_parser("after-merge")
    p.add_argument("numbers", nargs="+", type=int)
    p.add_argument("--merge", help="the merge commit; the submission must have run on it or later")
    args = parser.parse_args(argv)

    if args.command == "alerts":
        print_json(group_alerts(select([normalise(a) for a in fetch_alerts()], args.ecosystem, args.numbers)))
    elif args.command == "versions":
        age = min_age_days(load_renovate())
        print_json(dated(fetch_versions(args.ecosystem, args.package), today_arg(args.today), age))
    elif args.command == "candidates":
        if args.alerts:
            groups = json.loads(pathlib.Path(args.alerts).read_text(encoding="utf-8"))
        else:
            groups = group_alerts([normalise(a) for a in fetch_alerts()])
        group = next((g for g in groups if g["id"] == args.group), None)
        if group is None:
            die(f"no group {args.group!r}; the groups: {', '.join(g['id'] for g in groups)}")
        age = min_age_days(load_renovate())
        versions = dated(fetch_versions(group["ecosystem"], group["packages"][0]), today_arg(args.today), age)
        print_json(candidates(group, versions, args.current))
    elif args.command == "renovate":
        print_json(renovate_findings(load_renovate(), args.package, args.version, args.current))
    elif args.command == "gradle-scan":
        if args.alerts:
            groups = json.loads(pathlib.Path(args.alerts).read_text(encoding="utf-8"))
        else:
            groups = group_alerts([normalise(a) for a in fetch_alerts()])
        groups = [g for g in groups if g["ecosystem"] == "maven" and (not args.groups or g["id"] in args.groups)]
        print_json(vulnerable_records(groups, gradle_records()))
    elif args.command == "after-merge":
        run, current = submission_current(args.merge)
        graph = sbom_versions(gh_json("api", "repos/{owner}/{repo}/dependency-graph/sbom"))
        statuses = []
        for number in args.numbers:
            alert = gh_json("api", f"repos/{{owner}}/{{repo}}/dependabot/alerts/{number}")
            package = alert["dependency"]["package"]["name"]
            statuses.append({"package": package, **after_merge_status(alert, current, graph.get(package, set()))})
        print_json({"submission": run, "submission_current": current, "alerts": statuses})


if __name__ == "__main__":
    main()
