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

"""Turn Kover's XML report into a table a reviewer can read.

Writes `coverage-summary.md` and, when running under Actions, sets the same
numbers as step outputs so a later step can gate on them.

The per-module rows matter more than the total here. This library is one common
source set compiled twenty-five ways, and its risk is not spread evenly: the
parsers and matchers in the `-cldr-runtime` modules are where a bug hides, and
the `-types` modules are enums a generator wrote. One number averages those
together and hides both.
"""
import os
import pathlib
import xml.etree.ElementTree as ElementTree

REPORT = pathlib.Path("build/reports/kover/report.xml")

# Below this, the table says so rather than a job failing. A hard gate on a
# coverage number tends to be met by writing tests that execute lines without
# asserting anything, which is worse than the number being low and visible.
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


def main():
    if not REPORT.is_file():
        raise SystemExit(f"no Kover report at {REPORT}; run ./gradlew koverXmlReport")

    root = ElementTree.parse(REPORT).getroot()
    total = counters(root)
    line = percent(total.get("LINE", (0, 0)))
    branch = percent(total.get("BRANCH", (0, 0)))

    rows = []
    for package in root.findall("package"):
        name = package.get("name", "").replace("/", ".")
        c = counters(package)
        if "LINE" not in c or c["LINE"][1] == 0:
            continue
        rows.append((name, percent(c["LINE"]), c["LINE"], percent(c.get("BRANCH", (0, 0)))))
    rows.sort(key=lambda r: r[1])

    lines = [
        "<!-- kover-coverage -->",
        "## Coverage",
        "",
        f"`{bar(line)}` **{line:.1f}%** lines &nbsp; · &nbsp; **{branch:.1f}%** branches",
        "",
        "| | covered | total | % |",
        "| --- | ---: | ---: | ---: |",
    ]
    for label, key in (("Lines", "LINE"), ("Branches", "BRANCH"), ("Methods", "METHOD"), ("Classes", "CLASS")):
        covered, count = total.get(key, (0, 0))
        lines.append(f"| {label} | {covered} | {count} | {percent((covered, count)):.1f}% |")

    warnings = []
    if line < LINE_FLOOR:
        warnings.append(f"line coverage {line:.1f}% is below the {LINE_FLOOR:.0f}% the project aims for")
    if branch < BRANCH_FLOOR:
        warnings.append(f"branch coverage {branch:.1f}% is below the {BRANCH_FLOOR:.0f}% the project aims for")
    if warnings:
        lines += ["", "> [!WARNING]"] + [f"> {w}" for w in warnings]

    weakest = [r for r in rows if r[1] < 100.0][:10]
    if weakest:
        lines += [
            "",
            "<details><summary>Least covered packages</summary>",
            "",
            "| package | lines | % | branches |",
            "| --- | ---: | ---: | ---: |",
        ]
        for name, pct, (covered, count), branch_pct in weakest:
            lines.append(f"| `{name}` | {covered}/{count} | {pct:.1f}% | {branch_pct:.1f}% |")
        lines += ["", "</details>"]

    lines += [
        "",
        "<sub>Hand-written code only: the 1003 generated table and catalog files are "
        "filtered out in `kotlinx-locale-coverage`. Measured on the `jvm` target, over "
        "sources shared by all of them.</sub>",
        "",
    ]

    pathlib.Path("coverage-summary.md").write_text("\n".join(lines))
    print(f"lines {line:.1f}%  branches {branch:.1f}%")

    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a") as handle:
            handle.write(f"line={line:.1f}\n")
            handle.write(f"branch={branch:.1f}\n")


main()
