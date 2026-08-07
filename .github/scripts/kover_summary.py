#!/usr/bin/env python3
# Copyright 2026 Carcara.dev
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Turn Kover's XML reports into the tables a reviewer reads on the pull request.

Writes `coverage-summary.md` and, when running under Actions, sets the headline
numbers as step outputs so a later step can gate on them.

Nothing is truncated. Every module and every package appears, sorted worst
first. A coverage report that hides its tail is a report that answers "are we
roughly fine" and not "what is untested", and the second question is the one
worth asking.
"""
import os
import pathlib
import xml.etree.ElementTree as ElementTree

ROOT_REPORT = pathlib.Path("build/reports/kover/report.xml")

# Below these, the table says so. Deliberately not a build failure: a hard gate
# on a coverage number gets met by tests that execute lines without asserting
# anything, which buys the number and not the confidence.
LINE_FLOOR = 80.0
BRANCH_FLOOR = 65.0


def counters(node):
    out = {}
    for counter in node.findall("counter"):
        missed = int(counter.get("missed"))
        covered = int(counter.get("covered"))
        out[counter.get("type")] = (covered, covered + missed)
    return out


def percent(pair):
    covered, total = pair
    return 100.0 * covered / total if total else 100.0


def bar(value):
    filled = round(value / 10)
    return "█" * filled + "░" * (10 - filled)


def cell(pair):
    covered, total = pair
    return f"{covered}/{total}", f"{percent(pair):.1f}%"


def module_reports():
    """Every per-module report, keyed by the module directory that produced it.

    `./gradlew koverXmlReport` runs the task in each project as well as the root
    aggregate, so these exist already and cost nothing extra to read.
    """
    found = {}
    for path in sorted(pathlib.Path(".").glob("*/build/reports/kover/report.xml")):
        module = path.parts[0]
        found[module] = path
    return found


def main():
    if not ROOT_REPORT.is_file():
        raise SystemExit(f"no Kover report at {ROOT_REPORT}; run ./gradlew koverXmlReport")

    root = ElementTree.parse(ROOT_REPORT).getroot()
    total = counters(root)
    line = percent(total.get("LINE", (0, 0)))
    branch = percent(total.get("BRANCH", (0, 0)))

    out = [
        "<!-- kover-coverage -->",
        "## Coverage",
        "",
        f"`{bar(line)}` **{line:.1f}%** lines &nbsp; · &nbsp; **{branch:.1f}%** branches",
        "",
        "| | covered | total | % |",
        "| --- | ---: | ---: | ---: |",
    ]
    for label, key in (("Lines", "LINE"), ("Branches", "BRANCH"), ("Methods", "METHOD"), ("Classes", "CLASS")):
        pair = total.get(key, (0, 0))
        out.append(f"| {label} | {pair[0]} | {pair[1]} | {percent(pair):.1f}% |")

    warnings = []
    if line < LINE_FLOOR:
        warnings.append(f"line coverage {line:.1f}% is below the {LINE_FLOOR:.0f}% the project aims for")
    if branch < BRANCH_FLOOR:
        warnings.append(f"branch coverage {branch:.1f}% is below the {BRANCH_FLOOR:.0f}% the project aims for")
    if warnings:
        out += ["", "> [!WARNING]"] + [f"> {w}" for w in warnings]

    # Every module, worst first. A module with no measurable lines is listed too,
    # because "this module has nothing Kover can see" is itself worth knowing.
    modules = []
    for name, path in module_reports().items():
        c = counters(ElementTree.parse(path).getroot())
        lines_pair = c.get("LINE", (0, 0))
        modules.append((name, lines_pair, c.get("BRANCH", (0, 0)), c.get("METHOD", (0, 0))))
    modules.sort(key=lambda m: (percent(m[1]), m[0]))

    if modules:
        out += [
            "",
            f"### By module ({len(modules)})",
            "",
            "Each row counts only that module's **own** tests against its own code, "
            "which is not what the headline number measures. The `-core` and "
            "`-cldr-runtime` modules read low or zero here because their parsers and "
            "renderers are driven through the `-cldr-full` suites that own the data; "
            "the aggregate above counts that, a per-module report cannot. Read this "
            "table as which modules can be tested standing on their own, not as which "
            "code is untested.",
            "",
            "| module | lines | % | branches | % | methods | % |",
            "| --- | ---: | ---: | ---: | ---: | ---: | ---: |",
        ]
        for name, lines_pair, branch_pair, method_pair in modules:
            lc, lp = cell(lines_pair)
            bc, bp = cell(branch_pair)
            mc, mp = cell(method_pair)
            out.append(f"| `{name}` | {lc} | {lp} | {bc} | {bp} | {mc} | {mp} |")

    # Every package in the aggregate, worst first.
    packages = []
    for package in root.findall("package"):
        c = counters(package)
        if "LINE" not in c or c["LINE"][1] == 0:
            continue
        packages.append((package.get("name", "").replace("/", "."), c["LINE"], c.get("BRANCH", (0, 0))))
    packages.sort(key=lambda p: (percent(p[1]), p[0]))

    if packages:
        out += [
            "",
            f"### By package ({len(packages)})",
            "",
            "| package | lines | % | branches | % |",
            "| --- | ---: | ---: | ---: | ---: |",
        ]
        for name, lines_pair, branch_pair in packages:
            lc, lp = cell(lines_pair)
            bc, bp = cell(branch_pair)
            out.append(f"| `{name}` | {lc} | {lp} | {bc} | {bp} |")

    out += [
        "",
        "<sub>Hand-written code only: the 1003 generated table and catalog files are "
        "filtered out in `kotlinx-locale-coverage`. Measured on the `jvm` target, over "
        "sources shared by all of them.</sub>",
        "",
    ]

    pathlib.Path("coverage-summary.md").write_text("\n".join(out))
    print(f"lines {line:.1f}%  branches {branch:.1f}%  ({len(modules)} modules, {len(packages)} packages)")

    step_output = os.environ.get("GITHUB_OUTPUT")
    if step_output:
        with open(step_output, "a") as handle:
            handle.write(f"line={line:.1f}\n")
            handle.write(f"branch={branch:.1f}\n")


main()
