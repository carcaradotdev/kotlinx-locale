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

"""How much of the shipped locale data is held to an oracle.

This is not line coverage and is not a second opinion on it. Formatting thirty
locales executes exactly the same lines as formatting eleven hundred, so Kover
reads the same either way. What differs is how much of the data was compared
against ICU or against CLDR's own test files, and that is the number this
measures.

Two sources, because the two tiers are measured differently:

  committed goldens   counted out of the fixture in the module that reads it
  live ICU            read from conformance/ledger/coverage.tsv, which
                      :conformance-icu writes when the ledger is regenerated

Writes `oracle-coverage.md` for the pull request comment.
"""
import pathlib
import re

ROOT = pathlib.Path(".")
LEDGER_COVERAGE = ROOT / "conformance/ledger/coverage.tsv"

# domain label, the registry that says what ships, the golden that says what is
# checked, and the ledger domain names that cover the same ground live.
DOMAINS = [
    (
        "datetime patterns and names",
        "kotlinx-locale-datetime-cldr-full/**/LocaleDataRegistry.kt",
        "kotlinx-locale-datetime-cldr-full/**/IcuGoldenData.kt",
        ["datetime-names", "datetime-patterns"],
    ),
    (
        "skeletons",
        "kotlinx-locale-datetime-cldr-skeletons/**/SkeletonFormatsRegistry.kt",
        "kotlinx-locale-datetime-cldr-skeletons/**/IcuSkeletonGoldenData.kt",
        ["skeletons"],
    ),
    (
        "intervals",
        "kotlinx-locale-datetime-cldr-intervals/**/IntervalFormatsRegistry.kt",
        "kotlinx-locale-datetime-cldr-intervals/**/IcuIntervalGoldenData.kt",
        ["intervals"],
    ),
    (
        "relative time",
        "kotlinx-locale-datetime-cldr-relative/**/RelativeTimeRegistry.kt",
        None,
        ["relative-time"],
    ),
    (
        "duration units",
        "kotlinx-locale-datetime-cldr-durations/**/DurationUnitsRegistry.kt",
        "kotlinx-locale-datetime-cldr-durations/**/IcuDurationUnitGoldenData.kt",
        ["duration-units"],
    ),
    (
        "country names",
        "kotlinx-locale-country-cldr-full/**/CountryNamesRegistry.kt",
        "kotlinx-locale-country-cldr-full/**/IcuCountryGoldenData.kt",
        ["country-names"],
    ),
    (
        "currency names and symbols",
        "kotlinx-locale-currency-cldr-full/**/CurrencyNamesRegistry.kt",
        "kotlinx-locale-currency-cldr-full/**/IcuCurrencyGoldenData.kt",
        ["currency-names"],
    ),
    (
        "currency formats",
        "kotlinx-locale-currency-cldr-full/**/CurrencyFormatsRegistry.kt",
        "kotlinx-locale-currency-cldr-full/**/IcuCurrencyFormatGoldenData.kt",
        ["currency-formats"],
    ),
    (
        "currency plural names",
        "kotlinx-locale-currency-cldr-plurals/**/CurrencyPluralNamesRegistry.kt",
        "kotlinx-locale-currency-cldr-plurals/**/IcuCurrencyPluralGoldenData.kt",
        ["currency-plural-names"],
    ),
    (
        "language, script and region names",
        "kotlinx-locale-language-cldr-full/**/LocaleDisplayNamesRegistry.kt",
        None,
        ["display-names"],
    ),
    (
        "number symbols and formatting",
        "kotlinx-locale-number-cldr-full/**/NumberSymbolsRegistry.kt",
        "kotlinx-locale-number-cldr-full/**/IcuNumberGoldenData.kt",
        ["number-symbols", "number-formats"],
    ),
    (
        "plural rules",
        "kotlinx-locale-number-cldr-full/**/PluralRuleIndexRegistry.kt",
        "kotlinx-locale-number-cldr-full/**/IcuPluralGoldenData.kt",
        ["plural-cardinal", "plural-ordinal"],
    ),
    (
        "time zone names",
        "kotlinx-locale-timezone-cldr-full/**/TimeZoneNamesRegistry.kt",
        "kotlinx-locale-timezone-cldr-cities/**/IcuTimeZoneGoldenData.kt",
        ["timezone-names"],
    ),
    (
        "exemplar cities",
        "kotlinx-locale-timezone-cldr-cities/**/TimeZoneCitiesRegistry.kt",
        None,
        ["exemplar-cities"],
    ),
]

# A map entry is `put("tag"` on one line or `put(` with the key on the next.
ENTRY = re.compile(r"^\s*put\(\s*$|^\s*put\(\"", re.M)
# Some goldens are lists of constructor calls rather than maps.
CTOR = re.compile(r"^\s{4}\w+Golden\(", re.M)


def count_entries(pattern):
    if pattern is None:
        return None
    hits = list(ROOT.glob(pattern))
    if not hits:
        return None
    text = hits[0].read_text()
    entries = len(ENTRY.findall(text))
    if entries:
        return entries
    ctors = len(CTOR.findall(text))
    return ctors or None


def live_coverage():
    if not LEDGER_COVERAGE.is_file():
        return {}
    out = {}
    for line in LEDGER_COVERAGE.read_text().splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        domain, locales, _cases = line.split("\t")
        out[domain] = int(locales)
    return out


def bar(value):
    filled = round(value / 10)
    return "█" * filled + "░" * (10 - filled)


def main():
    live = live_coverage()
    rows = []
    for label, registry, golden, ledger_domains in DOMAINS:
        ships = count_entries(registry)
        if not ships:
            continue
        by_golden = count_entries(golden) or 0
        by_live = max((live.get(d, 0) for d in ledger_domains), default=0)
        best = max(by_golden, by_live)
        rows.append((label, ships, by_golden, by_live, 100.0 * best / ships))
    rows.sort(key=lambda r: r[4])

    out = [
        "<!-- oracle-coverage -->",
        "## Oracle coverage",
        "",
        "How much of the shipped locale data is compared against ICU or CLDR. "
        "**This is not the line coverage above and does not move with it**: "
        "formatting thirty locales runs the same code as formatting eleven "
        "hundred, so Kover reads the same either way. This is the number that "
        "says how much of the data anything actually checked.",
        "",
        "| domain | ships | committed golden | live ICU | checked |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]
    for label, ships, by_golden, by_live, pct in rows:
        golden_cell = str(by_golden) if by_golden else "0"
        live_cell = str(by_live) if by_live else "0"
        out.append(f"| {label} | {ships} | {golden_cell} | {live_cell} | `{bar(pct)}` {pct:.0f}% |")

    # Domains whose oracle is not counted in locales. Listed rather than forced
    # into the table, because a percentage in the wrong unit is a wrong number.
    out += [
        "",
        "| domain | oracle | breadth |",
        "| --- | --- | --- |",
        "| person names | CLDR `testData/personNameTest` | 36,960 cases, every locale CLDR names |",
        "| grapheme clusters | UCD `GraphemeBreakTest.txt` | every case Unicode publishes |",
        "| phone numbers | libphonenumber, mined from its own tests | its full metadata set |",
        "| currency minor units | live ICU `getDefaultFractionDigits` | every currency, once, since it does not vary by locale |",
    ]

    unchecked = [r for r in rows if r[4] == 0]
    thin = [r for r in rows if 0 < r[4] < 10]
    notes = []
    if unchecked:
        noun = "domain has" if len(unchecked) == 1 else "domains have"
        notes.append(f"{len(unchecked)} {noun} no oracle at all: " + ", ".join(r[0] for r in unchecked))
    if thin:
        notes.append(
            f"{len(thin)} {'is' if len(thin) == 1 else 'are'} checked below 10% of what they ship: " + ", ".join(r[0] for r in thin),
        )
    if notes:
        out += ["", "> [!WARNING]"] + [f"> {n}" for n in notes]

    out += [
        "",
        "<sub>A live ICU column of 0 means the domain has no comparison in "
        "`:conformance-icu` yet, not that it failed. Live figures come from "
        "`conformance/ledger/coverage.tsv`, which is regenerated by "
        "`./gradlew :conformance-icu:updateLedger`, and count locales ICU can "
        "answer for: it ships fewer than CLDR publishes, so the ceiling is "
        "905 rather than 1122.</sub>",
        "",
    ]

    pathlib.Path("oracle-coverage.md").write_text("\n".join(out))
    print(f"{len(rows)} domains, {len(unchecked)} with no oracle, {len(thin)} below 10%")


main()
