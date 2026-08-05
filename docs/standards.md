# Which standard each module implements

The standard is the source of truth. This library is one reading of it, and where
the two disagree the standard is right and this is a bug.

That is the reason this file exists. Every table here ships because somebody
specified it, and the link is the thing to check when an answer looks wrong, when
you are deciding whether a behaviour is a bug or a boundary, or when you want to
know what a module will do before depending on it.

Two companions. [boundaries.md](boundaries.md) records where this library stops,
which is usually somewhere short of where the standard does, and says whether
each stop is a gap or a decision. [../API.md](../API.md) describes what a
consumer calls. [../ROADMAP.md](../ROADMAP.md) lists what is intended and not
yet built.

## The specifications

Unicode publishes locale data as one document in parts. Most of this library is
an implementation of a few of them.

| Specification | What it defines | Where it is used |
| --- | --- | --- |
| [UTS #35 Part 1: Core][tr35-1] | Locale identifiers, likely subtags, the inheritance chain | `kotlinx-locale-core`, and the resolution every other module depends on |
| [UTS #35 Part 2: General][tr35-2] | Display names for languages, scripts, regions; duration patterns | `-language-*`, `durationPattern` |
| [UTS #35 Part 3: Numbers][tr35-3] | Number and currency formatting, plural and ordinal rules, compact notation | `-number-*`, `-currency-*` |
| [UTS #35 Part 4: Dates][tr35-4] | Date and time patterns, skeletons, interval formats, week data, time zone names | `-datetime-*`, `-timezone-*` |
| [UTS #35 Part 6: Supplemental][tr35-6] | Territory, currency and calendar metadata not tied to one locale | `Country`, `Currency`, `WeekInfo` |
| [UTS #35 Part 8: Person Names][tr35-8] | Name field order, patterns per length and formality, initials | `-personname-*` |
| [UTS #51: Emoji][tr51] | Emoji sequences, which is where flag emoji come from | `Country.flagEmoji` |

Outside Unicode:

| Standard | What it defines | Where it is used |
| --- | --- | --- |
| [ISO 3166-1][iso3166] | Country codes: alpha-2, alpha-3, numeric | `kotlinx-locale-country-types` |
| [ISO 4217][iso4217] | Currency codes, numeric codes and minor units | `kotlinx-locale-currency-types` |
| [ISO 8601-1:2019][iso8601] | Calendar dates, times, and the `<start>/<end>` interval form | the fallback when no locale data answers |
| [ITU-T E.164][e164] | International telephone numbering | `kotlinx-locale-phone-*` |
| [BCP 47][bcp47] | Language tags, which is the wire form of `Locale` | `Locale.forLanguageTag` |

## Module by module

Every published artifact, and what it is an implementation of. The `-runtime`
layers carry the algorithm and the `-full` layers the tables, so both rows point
at the same specification.

| Module | Standard |
| --- | --- |
| `kotlinx-locale-core` | [UTS #35 Part 1][tr35-1], [BCP 47][bcp47] |
| `kotlinx-locale-types` | [UTS #35 Part 1][tr35-1], the locale catalogue |
| `kotlinx-locale-platform` | none; reads the host's own locale |
| `kotlinx-locale-serialization` | [BCP 47][bcp47] |
| `kotlinx-locale-country-types` | [ISO 3166-1][iso3166], with CLDR's code mappings |
| `kotlinx-locale-country-core` | [ISO 3166-1][iso3166] |
| `kotlinx-locale-country-cldr-runtime` | [UTS #35 Part 2][tr35-2], territory display names |
| `kotlinx-locale-country-cldr-full` | [UTS #35 Part 2][tr35-2] |
| `kotlinx-locale-country-platform` | none; reads the host |
| `kotlinx-locale-country-serialization` | [ISO 3166-1][iso3166] |
| `kotlinx-locale-language-core` | [UTS #35 Part 2][tr35-2] |
| `kotlinx-locale-language-cldr-runtime` | [UTS #35 Part 2][tr35-2], the display name composition |
| `kotlinx-locale-language-cldr-full` | [UTS #35 Part 2][tr35-2] |
| `kotlinx-locale-number-core` | [UTS #35 Part 3][tr35-3] |
| `kotlinx-locale-number-cldr-runtime` | [UTS #35 Part 3][tr35-3], patterns, plural rules, RBNF ordinals |
| `kotlinx-locale-number-cldr-full` | [UTS #35 Part 3][tr35-3] |
| `kotlinx-locale-currency-types` | [ISO 4217][iso4217] |
| `kotlinx-locale-currency-core` | [ISO 4217][iso4217], [UTS #35 Part 3][tr35-3] |
| `kotlinx-locale-currency-cldr-runtime` | [UTS #35 Part 3][tr35-3], currency patterns and names |
| `kotlinx-locale-currency-cldr-full` | [UTS #35 Part 3][tr35-3] |
| `kotlinx-locale-currency-cldr-plurals` | [UTS #35 Part 3][tr35-3], count-keyed display names and the unit pattern that joins one to a number |
| `kotlinx-locale-currency-platform` | none; reads the host |
| `kotlinx-locale-currency-serialization` | [ISO 4217][iso4217] |
| `kotlinx-locale-datetime-core` | [UTS #35 Part 4][tr35-4], and [Part 6][tr35-6] for week data |
| `kotlinx-locale-datetime-cldr-runtime` | [UTS #35 Part 4][tr35-4], the pattern engine and skeleton matcher |
| `kotlinx-locale-datetime-cldr-full` | [UTS #35 Part 4][tr35-4], [Part 6][tr35-6] |
| `kotlinx-locale-datetime-cldr-skeletons` | [UTS #35 Part 4][tr35-4], `availableFormats` |
| `kotlinx-locale-datetime-cldr-relative` | [UTS #35 Part 2][tr35-2], `fields` and `relativeTime` |
| `kotlinx-locale-datetime-cldr-intervals` | [UTS #35 Part 4][tr35-4], `intervalFormats` |
| `kotlinx-locale-datetime-cldr-durations` | [UTS #35 Part 2][tr35-2], the `duration-` measurement units |
| `kotlinx-locale-datetime-platform` | none; reads the host |
| `kotlinx-locale-timezone-core` | [UTS #35 Part 4][tr35-4], zone names |
| `kotlinx-locale-timezone-cldr-runtime` | [UTS #35 Part 4][tr35-4], the metazone resolution |
| `kotlinx-locale-timezone-cldr-full` | [UTS #35 Part 4][tr35-4] |
| `kotlinx-locale-timezone-cldr-cities` | [UTS #35 Part 4][tr35-4], exemplar cities |
| `kotlinx-locale-personname-core` | [UTS #35 Part 8][tr35-8] |
| `kotlinx-locale-personname-cldr-runtime` | [UTS #35 Part 8][tr35-8], pattern selection and field modifiers |
| `kotlinx-locale-personname-cldr-full` | [UTS #35 Part 8][tr35-8] |
| `kotlinx-locale-phone-core` | [ITU-T E.164][e164] |
| `kotlinx-locale-phone-metadata-runtime` | [ITU-T E.164][e164], over libphonenumber's metadata |
| `kotlinx-locale-phone-metadata-full` | [ITU-T E.164][e164] |
| `kotlinx-locale-phone-serialization` | [ITU-T E.164][e164], and RFC 3966 for the `tel:` form |
| `kotlinx-locale-codegen-emitters` | none; build-time only |
| `kotlinx-locale-codegen-data` | none; build-time only |
| `kotlinx-locale-gradle-plugin` | none; build-time only |

## Where the data comes from, and which edition

Every number below is traceable to a file in this repository rather than typed
from memory. Bumping any of them is a deliberate commit.

| Source | Edition | Pinned in |
| --- | --- | --- |
| CLDR | `release-48-2` | `CLDR_REPO` in `codegen/.../Repos.kt` |
| ICU, used to check answers and never shipped | `release-78.3` | `ICU_REPO` in `codegen/.../Repos.kt`, mirrored by `icu4j` in `gradle/libs.versions.toml` |
| libphonenumber | `v9.0.19` | `PHONE_REPO` in `codegen/.../Repos.kt`, mirrored by `libphonenumber` in `gradle/libs.versions.toml` |
| ISO 4217 list one and list three | published 2026-01-01 | vendored at `codegen/src/main/resources/iso4217/` |
| Unicode Emoji | 17.0 | `EMOJI_VERSION` in `codegen/.../Repos.kt`, vendored at `codegen/src/main/resources/emoji/` |
| Unicode Character Database | 15.1.0 | `UCD_VERSION` in `codegen/.../Repos.kt`, vendored at `codegen/src/main/resources/ucd/` |

ICU is read two ways and shipped neither: parsed as data for its resource
bundles, and called as a library to generate the expected answers the conformance
tests compare against. It is not a dependency of any published artifact.

## How the claims are checked

A specification link is worth little on its own, so each domain is held to an
independent implementation of the same standard rather than to its own reading:

- CLDR's own test data, which ships in the same release as the tables and so
  carries no version skew, covers person names, date-time cases and plural
  samples.
- ICU answers the same questions from its own build for numbers, currencies,
  skeletons, time zones, week data and intervals.
- libphonenumber answers for phone numbers, including the inputs it rejects.

Where this library and the reference disagree, the conformance tests exclude the
affected locales by name and count them rather than loosening the comparison, so
the gap stays visible. The exclusions and their reasons are in the tests
themselves, each one pinned by an assertion on its size so that the list cannot
grow unnoticed. What remains is a decision to stop rather than an open bug, and
is written up in [boundaries.md](boundaries.md).

## Where ICU is not the answer

ICU is the reference for most of what is checked above, and it is not the
specification. Four kinds of difference come up, and each one is handled
differently.

**ICU is built from a different CLDR snapshot.** The pinned releases are CLDR
`release-48-2` and ICU `78.3`, which are close but not the same, so a name or a
pattern that moved between them shows up as a difference that has nothing to do
with formatting. Where a fixture can tell the two apart it records what ICU
formatted from and skips only the pairs that moved; where it cannot, the locale
is named in the test. Burmese is the current example: `release-48-2` gives it a
currency unit pattern of `{1} {0}`, putting the name before the number, and ICU
78.3 carries none and inherits root's `{0} {1}`. This library follows its pin.

**ICU resolves some locales to a different bundle.** ICU ships no data file for
`sr-Cyrl-ME` and answers it from a Latin-script bundle, so it writes
`dirham UAE` where CLDR's own `sr_Cyrl_ME.xml` writes `дирхам УАЕ`. That is a
locale-fallback difference rather than a data one, and it moves 173 of that
locale's 178 currency display names, plural or not.

**ICU prunes coverage where CLDR has data.** For a locale ICU holds at minimal
coverage it emits root's placeholders rather than the wording CLDR carries, and
for a few locales it ships no unit data at all. Following that would mean
shipping less than CLDR says, so this library does not; `resolveDurationUnits`
records the cases.

**ICU has defects.** ICU 78.3 cannot format a currency at
`UnitWidth.FULL_NAME` when that currency declares its own pattern in the locale,
which CLDR does once, for the Turkish lira in Turkish. The format routes through
the pattern modifier, whose switch over the widths throws instead of handling the
name width. Nothing here reproduces that: the generator leaves the pair out of
the fixture, records why, and fails the build if it ever stops being a handful.

[tr35-1]: https://www.unicode.org/reports/tr35/tr35.html
[tr35-2]: https://www.unicode.org/reports/tr35/tr35-general.html
[tr35-3]: https://www.unicode.org/reports/tr35/tr35-numbers.html
[tr35-4]: https://www.unicode.org/reports/tr35/tr35-dates.html
[tr35-6]: https://www.unicode.org/reports/tr35/tr35-info.html
[tr35-8]: https://www.unicode.org/reports/tr35/tr35-personNames.html
[tr51]: https://www.unicode.org/reports/tr51/
[iso3166]: https://www.iso.org/iso-3166-country-codes.html
[iso4217]: https://www.iso.org/iso-4217-currency-codes.html
[iso8601]: https://www.iso.org/standard/70907.html
[e164]: https://www.itu.int/rec/T-REC-E.164
[bcp47]: https://www.rfc-editor.org/info/bcp47
