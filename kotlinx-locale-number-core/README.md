# kotlinx-locale-number

Number formatting, plural rules and ordinals, across four artifacts:
`-number-core` for the value types and the contracts, `-number-cldr-runtime` for
the engine, `-number-cldr-full` for the tables, and the compact money table over
in `kotlinx-locale-currency-cldr-full`.

This file exists because the answer to "where is this defined" is not one
document. CLDR settles the data and the algorithms. It does not settle the names
of the options, and the two places that do are not part of Unicode. Anyone
reading this code and looking for the spec will find half of it and then wonder
where the other half went, so here is where each part comes from.

## What CLDR defines

| Claim | Where it is normative |
| --- | --- |
| The symbols, patterns and `minimumGroupingDigits` | `common/main/*.xml`, `<numbers>` |
| How `#,##0.00` turns a number into text | UTS #35 Part 3, [Number Format Patterns](https://www.unicode.org/reports/tr35/tr35-numbers.html#Number_Format_Patterns) |
| That a `%` in a pattern multiplies by 100 | the same section's character table: "Multiply by 100 and show as percentage" |
| Which compact pattern a magnitude selects, and that the plural category is taken from the divided value | UTS #35 Part 3, [Compact Number Formats](https://www.unicode.org/reports/tr35/tr35-numbers.html#Compact_Number_Formats), ten numbered steps |
| The plural operands `n i v w f t e c` and the rule grammar | UTS #35 Part 3, [Language Plural Rules](https://www.unicode.org/reports/tr35/tr35-numbers.html#Language_Plural_Rules) |
| The rules themselves | [`plurals.xml`](https://github.com/unicode-org/cldr/blob/release-48-2/common/supplemental/plurals.xml) and [`ordinals.xml`](https://github.com/unicode-org/cldr/blob/release-48-2/common/supplemental/ordinals.xml) |
| Ordinal forms, as rules rather than a table | `common/rbnf/*.xml`, ruleset `digits-ordinal` |
| The digits of each numbering system | `common/supplemental/numberingSystems.xml` |

Everything above is pinned at `release-48-2`, the same tag `codegen/Repos.kt`
clones, so every link goes to the file this library actually read.

## What CLDR does not define

The vocabulary of the options. Not the values, the names: notation, precision,
grouping strategy, sign display, rounding mode. None of them appear in the
specification.

That claim is checkable, which is why it is written this way. In
`docs/ldml/tr35-numbers.md` at `release-48-2`, the string `skeleton` occurs zero
times. So does `signDisplay`. So does `roundingMode`. Run it yourself against a
clone.

Those names come from ICU's
[`NumberFormatter`](https://unicode-org.github.io/icu/userguide/format_parse/numbers/)
and its
[number skeleton syntax](https://unicode-org.github.io/icu/userguide/format_parse/numbers/skeletons.html),
and the same names appear in ECMA-402's
[`Intl.NumberFormat`](https://tc39.es/ecma402/#numberformat-objects):

| This library | ICU | ECMA-402 |
| --- | --- | --- |
| `NumberNotation` | `Notation` | `notation` |
| `minimumFractionDigits` and the rest | `Precision` | the same names |
| `NumberGrouping` | `GroupingStrategy` | `useGrouping` |
| `SignDisplay` | `SignDisplay` | `signDisplay` |
| half-even rounding | `RoundingMode` | `roundingMode` |

## Why ICU settles what CLDR leaves open

ECMA-402 is a JavaScript standard, and a JavaScript standard cannot take a
breaking change. Every page ever written is a compatibility constraint, so a
decision it got wrong stays wrong and the specification ossifies around it. That
makes it useful as corroboration and a poor choice as an authority.

So where the two disagree about something LDML leaves open, this library follows
ICU. ICU is where the LDML committee's own reference implementation lives, and
this repository already cross-checks against ICU4J goldens that `:codegen`
extracts and commits under `conformance-test-suite/`. An answer settled that way
is pinned as a fixture rather than as a sentence in a document, which is the only
kind of agreement that fails a build when it stops being true.

## Where each default came from

**Compact precision.** UTS #35 says of a compact pattern whose value is the
special `0` that "the significant digits are adjusted for consistency, typically
to 2 or 3 digits, and the maximum fractional digits are set to 0", and then that
"APIs may, however, allow these default behaviors to be overridden." Typically is
not a specification, and that latitude is why two implementations of the same
document disagree about the same number.

This library rounds half-even to whichever is the more precise of zero fraction
digits and two significant digits. That is ICU's
`Precision.integer().withMinDigits(2)` and `Intl.NumberFormat`'s compact default,
so 1200 is `1.2K`, 12345 is `12K` and 123456 is `123K`. The goldens hold it
there.

**Re-selecting the magnitude after rounding.** 999999 prints as `1M` and not
`1000K`. The spec's ten steps do not describe this; ICU and `Intl` both do it.
This is the one place the algorithm departs from a literal reading of the
document, which is why it has goldens rather than only a comment.

**Rounding mode.** Half to even, everywhere, including the divide in
`rescaleFraction`. LDML says nothing about it. ICU's number formatter defaults to
half-even and so does `java.text.DecimalFormat`; ECMA-402 rounds half away from
zero, so `0.125` as a percentage is `12%` here and `13%` in a browser.

**Grouping in compact notation.** Compact raises the locale's
`minimumGroupingDigits` to two, so German writes 1000 as `1000` and 12000 as
`12.000` even though its own minimum is one. UTS #35 does not mention this; ICU's
compact notation defaults to `GroupingStrategy.MIN2` and `Intl.NumberFormat` does
the same.

**A negative value that rounds to zero.** It keeps its minus: -0.5 at no fraction
digits is `-0`. Both reference implementations do this, on the grounds that a
temperature of -0.4 shown to the nearest degree is still below freezing.
`SignDisplay.NEGATIVE` is the value to pass when you want `0` instead, and that
is the only thing that distinguishes it from `AUTO`.

**Percent scale.** The engine multiplies, because a `%` in a CLDR pattern does.
Both readings have standing: `Intl.NumberFormat` multiplies, ICU's newer
`NumberFormatter.unit(NoUnit.PERCENT)` does not. Guessing wrong is a silently
hundredfold wrong number in front of a reader, so the two entry points are named
for what they take rather than one being assumed: `numberFormatPercent` takes a
fraction, `numberFormatPercentValue` takes an already-scaled value.

**Plural rules interpreted rather than compiled.** Every cardinal and ordinal
condition in CLDR 48.2 is about four kilobytes for all 1122 locales, against an
evaluator of roughly a hundred and fifty lines. Compiled to Kotlin they would be
sixty-five functions plus a dispatch map holding references to all of them, so
dead-code elimination could drop none of it and the code would be larger for the
same reach. Keeping CLDR's own syntax also turns its `@integer` and `@decimal`
sample lists into a fixture that runs the shipped evaluator on every target,
which is how the `n` operand being read as textual rather than numeric was found.

**Ordinals from RBNF rather than from the ordinal plural category.** The category
is single-valued in Czech, German, Spanish, Croatian, Icelandic, Portuguese and
Slovak, so knowing it says nothing about the printed form. What prints `1st` and
`1.` is the `digits-ordinal` rule set, and the evaluator here implements the
bounded subset those rule sets use. Generation fails if a later CLDR release
introduces a construct outside that subset, because the alternative is a rule
that renders wrong in one language and is noticed by nobody.

## Sources

- UTS #35 Part 3, Numbers: <https://www.unicode.org/reports/tr35/tr35-numbers.html>
- The same document at the pinned tag: <https://github.com/unicode-org/cldr/blob/release-48-2/docs/ldml/tr35-numbers.md>
- ICU number formatting: <https://unicode-org.github.io/icu/userguide/format_parse/numbers/>
- ICU number skeletons: <https://unicode-org.github.io/icu/userguide/format_parse/numbers/skeletons.html>
- ICU rounding modes: <https://unicode-org.github.io/icu/userguide/format_parse/numbers/rounding-modes.html>
- ECMA-402 `Intl.NumberFormat`: <https://tc39.es/ecma402/#numberformat-objects>
