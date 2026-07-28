# kotlinx-locale

Locale support for Kotlin Multiplatform, written entirely in common Kotlin.

Kotlin has no multiplatform locale API: there is no common `Locale` type, and
libraries like [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime)
deliberately ship no locale data, so there is no way to turn a date into
"27 de julho de 2026" or "2026年7月27日" from common code. This project fills
that gap. The locale data comes from Unicode's CLDR, compiled into Kotlin
source by a code generator, so results are identical on JVM, Android, JS,
Wasm and every Native target. The platform's own locale APIs are never
involved.

```kotlin
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.*
import kotlinx.datetime.*

val date = LocalDate(2026, 7, 27)

date.format(FormatStyle.FULL, Locale.forLanguageTag("pt-BR"))
// segunda-feira, 27 de julho de 2026

date.format(FormatStyle.MEDIUM, Locale.forLanguageTag("ja"))
// 2026/07/27

date.format(FormatStyle.LONG, Locale.forLanguageTag("ar-EG"))
// ٢٧ يوليو ٢٠٢٦

LocalDateTime(date, LocalTime(15, 5)).format(FormatStyle.SHORT, Locale.current)
// 7/27/26, 3:05 PM (on an en-US machine)
```

The full API, with every enum value and edge case, is documented in [API.md](API.md).

## Modules

| Module | Artifact | What it contains |
| --- | --- | --- |
| `locale/` | `dev.carcara:kotlinx-locale` | The `Locale` type: tag parsing, normalization, system locale detection, the list of CLDR locales, and the data-lookup infrastructure formatter modules build on. Depends on nothing. |
| `datetime/` | `dev.carcara:kotlinx-locale-datetime` | Extensions for kotlinx-datetime: date/time/date-time formatting in the four CLDR lengths, month and weekday names, per-locale digit systems. Carries the generated CLDR formatting data. |
| `country/` | `dev.carcara:kotlinx-locale-country` | The `Country` enum: all 249 ISO 3166-1 countries with alpha-2, alpha-3 and numeric codes, CLDR-localized display names, and conversion between every representation. |
| `currency/` | `dev.carcara:kotlinx-locale-currency` | The `Currency` enum (active ISO 4217: alphabetic and numeric codes, ISO minor units, CLDR fraction and cash-rounding behavior), the `CurrencyAmount` type, a CLDR currency formatter, mappers between the ISO and CLDR decimal scales, and country-to-currency mapping (depends on the country module). |

Each formatter module brings its own generated CLDR data and depends on the
base module, so you ship the data for what you use. More formatter modules
are planned on the same foundation.

```kotlin
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.currency.*

Country.forAlpha3("BRA")                      // Country.BR
Country.BR.displayName(Locale.forLanguageTag("fr"))   // Brésil

val price = CurrencyAmount(Currency.EUR, 123456)      // 1234.56 in minor units
price.format(Locale.forLanguageTag("de"))             // 1.234,56 €
price.format(Locale.forLanguageTag("en"))             // €1,234.56
Country.BR.currency                                   // Currency.BRL
```

## Features

- Date, time and date-time formatting in the four CLDR standard lengths
  (`FULL`, `LONG`, `MEDIUM`, `SHORT`) for all 1121 CLDR locales.
- Localized month and day-of-week names in wide, abbreviated and narrow widths.
- Flexible day periods where a locale's standard patterns use them: zh-Hant
  times render as 凌晨2:05, 下午3:05 and 晚上8:05 as the day progresses.
- Native digit systems: ar-EG formats years as ٢٠٢٦, fa as ۲۰۲۶, bn as ২০২৬.
- The ISO 3166-1 countries as an enum, with localized names for every CLDR
  locale and conversion between alpha-2, alpha-3, numeric code and name.
- The active ISO 4217 currencies as an enum with both the ISO decimals and
  CLDR's formatting decimals (they differ: the Albanian lek has 2 ISO minor
  units but formats with 0), plus mappers between the two scales.
- Locale-aware currency formatting from CLDR patterns: `$1,234.56`,
  `1.234,56 €`, `₹1,23,456.78`, `‏١٬٢٣٤٫٥٦ ج.م.‏`, with accounting and
  cash-rounding variants (CHF cash rounds to 0.05). Formatted strings also
  parse back to ISO minor units: `200 Ft` becomes 20000, because HUF prints
  without its two ISO decimals.
- A `Locale` type that parses BCP 47 tags and POSIX identifiers, with CLDR
  fallback (pt-XX falls back to pt, unknown languages to CLDR root).
- `Locale.current` reads the system locale. This is the project's single
  expect/actual: one function per platform returns a raw tag, and everything
  else runs in commonMain.

## Installation

The library is not published to a public repository yet. To try it, clone the
repo and publish to your local Maven repository:

```sh
./gradlew publishToMavenLocal
```

then depend on it from another project:

```kotlin
repositories { mavenLocal(); mavenCentral() }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation("dev.carcara:kotlinx-locale-datetime:0.1.0-SNAPSHOT")
        implementation("dev.carcara:kotlinx-locale-country:0.1.0-SNAPSHOT")
        implementation("dev.carcara:kotlinx-locale-currency:0.1.0-SNAPSHOT")
        // each pulls in dev.carcara:kotlinx-locale transitively; take only
        // the modules you need
    }
}
```

## Supported platforms

JVM, Android, JS (Node.js), Wasm (JS and WASI), and every Kotlin/Native target
that kotlinx-datetime publishes: iOS, macOS, watchOS, tvOS, Linux x64/arm64,
Windows (mingwX64) and Android Native. `Locale.current` reads:

| Platform | Source |
| --- | --- |
| JVM / Android | `java.util.Locale.getDefault()` |
| Apple platforms | `NSLocale.preferredLanguages`, then `NSLocale.currentLocale` |
| JS / Wasm-JS | `Intl.DateTimeFormat().resolvedOptions().locale` |
| Linux / Android Native | `LC_ALL`, `LC_TIME`, `LANG` |
| Windows | `GetUserDefaultLocaleName` |
| Wasm-WASI | nothing exposed; `Locale.current` returns `en` |

## Where the data comes from

The `:codegen` module clones two official Unicode repositories into
`codegen/repos/` (gitignored, sparse, pinned to release tags):

- [unicode-org/cldr](https://github.com/unicode-org/cldr) at `release-48-2`,
  the source of truth. The generator parses the LDML files, resolves each
  locale's inheritance chain (parentLocales rules, root aliases), and emits
  the flattened result as encoded string constants into the datetime module,
  plus the locale tag list into the base module. Identical payloads are
  deduplicated: 1121 locales plus root collapse to 429 unique constants,
  around 500 KB of Kotlin source. The same pipeline emits the `Country` and
  `Currency` enums, the localized country/currency names, and the per-locale
  number-formatting data into the country and currency modules. Names are
  stored sparsely (only what each locale's own file declares, with the parent
  chain walked at runtime) because flattening them would multiply the data
  many times over.
- [unicode-org/icu](https://github.com/unicode-org/icu) at `release-78.3`,
  used only for verification. The generator extracts golden fixtures for 30
  major locales from ICU's resource bundles (datetime patterns and names,
  country display names, currency symbols and names, number separators), and
  generated `Icu*GoldenTest`s verify in each module's commonTest that the
  CLDR-derived runtime data agrees with them on every platform. ICU encodes
  the same upstream data through a completely different pipeline, so agreement
  is a strong check on the parsers and the runtime resolution. ICU's full
  currency numeric-code table is emitted as a fixture too, so the ISO 4217
  cross-check also runs as a test everywhere. On the JVM, additional parity
  tests compare the ISO country and currency tables against the JDK's own
  data, a third independent source.

Currency identity (numeric codes and ISO minor units) is not in CLDR, so the
official ISO 4217 "list one" XML published by SIX is vendored as a snapshot at
`codegen/src/main/resources/iso4217/list-one.xml` (published 2026-01-01) and
parsed during generation. The country set is CLDR's `regular` region validity
list restricted to codes with an ISO alpha-3 and numeric assignment, which
excludes macroregions, exceptionally reserved codes (`AC`, `IC`) and
user-assigned codes (`XK`).

To regenerate after bumping the pinned tags in
`codegen/src/main/kotlin/.../Repos.kt`:

```sh
./gradlew :codegen:generateLocaleData
```

The task clones on first run and reuses the clones afterwards. Generated files
carry a `// GENERATED` header and are committed, so consumers of the library
never run the pipeline.

## Building

```sh
./gradlew build
```

This compiles every target and runs the test suite on each platform the host
can execute: JVM, Android host tests, Node.js for JS and both Wasm targets,
macOS, and the iOS/watchOS simulators. Apple simulator tests skip themselves
when the matching runtime is not installed in Xcode.

The library modules share their target list and publishing setup through the
`kotlinx-locale-multiplatform` convention plugin in `buildSrc/`.

Formatting is enforced with [ktlint](https://pinterest.github.io/ktlint/)
through the `org.jlleitschuh.gradle.ktlint` plugin; generated sources are
excluded by their `// GENERATED` header. Run `./gradlew ktlintFormat` to fix
style before committing, or `./gradlew ktlintCheck` to verify.

CI (`.github/workflows/ci.yml`) runs on every push to `main` and on pull
requests: a ktlint check plus `./gradlew build` on Linux, macOS and Windows
runners, which together cover every target's tests a host can execute. Pull
requests must be green on all of these checks before merging.

## Scope and limitations

- Formatting uses each locale's gregorian calendar data. Non-gregorian
  calendars (Buddhist years in Thai, Japanese imperial eras) are not
  implemented; Thai dates for example come out as "27 กรกฎาคม ค.ศ. 2026".
- CLDR's FULL and LONG time patterns end with a time-zone name. The formatted
  types carry no zone, so those fields are dropped and FULL/LONG times equal
  the MEDIUM ones in most locales.
- Date/time formatting only: parsing "27 de julho de 2026" back into a
  `LocalDate` is not supported. Currency strings do parse back:
  `CurrencyAmount.parseFormatted` reads CLDR-formatted values like
  `R$ 1.234,56` or `200 Ft` into ISO minor units. It expects one number with
  one locale's separators, not free-form text.
- Skeleton-based patterns (`availableFormats`), relative formatting
  ("yesterday") and interval formatting are not implemented.
- The currency enum covers the active ISO 4217 set; historic currencies (DEM,
  the pre-2005 TRL) are not included. Compact currency notation (`$1.2K`) and
  plural-aware currency names (`¤¤¤` with count) are not implemented.
