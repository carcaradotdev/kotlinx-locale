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
import dev.carcara.kotlinx.locale.datetime.cldr.*
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

Artifacts are named `kotlinx-locale[-<domain>]-<layer>`. Every domain has the
same three layers, and the translated text — the part that is big — lives only
in the bottom one.

| Directory | Artifact | What it contains |
| --- | --- | --- |
| `locale-core/` | `dev.carcara:kotlinx-locale-core` | The `Locale` type: tag parsing, normalization, system locale detection, the fallback chain, and the `LocaleDataSource` contract every data source answers. Depends on nothing. |
| `locale-platform/` | `dev.carcara:kotlinx-locale-platform` | What the host can say about locales before any domain is involved: whether it exposes locale data, and which locales it enumerates. |
| `locale-types/` | `dev.carcara:kotlinx-locale-types` | The generated locale catalog: one enum per language, so `Pt.BR` names a locale the compiler checks instead of a string that fails at runtime. Optional. |
| `country-types/` | `dev.carcara:kotlinx-locale-country-types` | The `Country` enum: 249 ISO 3166-1 entries carrying their alpha-3 and numeric codes. Generated, and nothing else. |
| `country-core/` | `dev.carcara:kotlinx-locale-country-core` | `alpha2`, the `for*` lookups, and `CountryNameSource` with the total operations and the fallback composer over it. |
| `country-cldr-format/` | `dev.carcara:kotlinx-locale-country-cldr-format` | The reader for the CLDR name records, without the records. What a generated table binds to. |
| `country-platform/` | `dev.carcara:kotlinx-locale-country-platform` | `PlatformCountry`: country names from `java.util.Locale`, `Intl.DisplayNames` or `NSLocale`. Ships no tables. |
| `country-cldr/` | `dev.carcara:kotlinx-locale-country-cldr` | `CldrCountry` and the CLDR name tables behind it, plus `Country.displayName`. |
| `currency-types/` | `dev.carcara:kotlinx-locale-currency-types` | The `Currency` enum (active ISO 4217 codes, ISO minor units, CLDR fraction and cash-rounding behavior) and the country-to-currency map. |
| `currency-core/` | `dev.carcara:kotlinx-locale-currency-core` | `code`, `minorUnitDigits`, the ISO/CLDR scale conversions, the `for*` lookups, `CurrencyAmount` and its arithmetic, and the `CurrencyNameSource` and `CurrencyFormatSource` contracts. |
| `currency-cldr-format/` | `dev.carcara:kotlinx-locale-currency-cldr-format` | The reader for the CLDR symbol, name and number records, plus the pattern-based formatter and parser. |
| `currency-platform/` | `dev.carcara:kotlinx-locale-currency-platform` | `PlatformCurrency`: symbols, names and number formatting from `NumberFormat`, `Intl.NumberFormat` or `NSNumberFormatter`. Ships no tables. |
| `currency-cldr/` | `dev.carcara:kotlinx-locale-currency-cldr` | `CldrCurrency`, the CLDR symbol and name tables, the pattern-based number formatter and parser, plus `Currency.symbol`, `Currency.displayName` and `CurrencyAmount.format`. |
| `datetime-core/` | `dev.carcara:kotlinx-locale-datetime-core` | `FormatStyle`, `TextStyle` and the `DateTimeFormatSource` contract. The only module that depends on kotlinx-datetime. |
| `datetime-cldr-format/` | `dev.carcara:kotlinx-locale-datetime-cldr-format` | The reader for the CLDR pattern records, plus the pattern parser and formatter. |
| `datetime-platform/` | `dev.carcara:kotlinx-locale-datetime-platform` | `PlatformDateTime`: the four lengths and the calendar names from `DateTimeFormatter`, `Intl.DateTimeFormat` or `NSDateFormatter`. Ships no tables. |
| `datetime-cldr/` | `dev.carcara:kotlinx-locale-datetime-cldr` | `CldrDateTime`, the CLDR pattern data, parser and formatter, plus `LocalDate.format` and friends. |
| `conformance/` | `dev.carcara:kotlinx-locale-conformance` | The ICU fixtures and the suite that runs any source through them, at an exact tier for CLDR-backed sources and a behavioural tier for platform ones. For test source sets. |
| `codegen-api/` | `dev.carcara:kotlinx-locale-codegen` | The emitters and the bundle reader: the half of code generation that a build can run. Parses no XML and clones nothing, so it is safe on a build classpath. |
| `cldr-data/` | `dev.carcara:kotlinx-locale-cldr-data` | CLDR resolved into one compact record per locale, versioned by the release it came from. What a build reads instead of cloning CLDR. |
| `gradle-plugin/` | `dev.carcara:kotlinx-locale-gradle-plugin` | The `dev.carcara.kotlinx-locale` plugin: generates a data set narrowed to the locales a build declares. |

A `-cldr` module is one implementation of its domain's contract, not the only
possible one. Which one answers is visible in the import rather than inferred
from the dependency graph, so a build can compose two of them or swap one out
by changing an import.

```kotlin
import dev.carcara.kotlinx.locale.country.*
import dev.carcara.kotlinx.locale.country.cldr.*
import dev.carcara.kotlinx.locale.currency.*
import dev.carcara.kotlinx.locale.currency.cldr.*

Country.forAlpha3("BRA")                      // Country.BR
Country.BR.displayName(Locale.forLanguageTag("fr"))   // Brésil

val price = CurrencyAmount(Currency.EUR, 123456)      // 1234.56 in minor units
price.format(Locale.forLanguageTag("de"))             // 1.234,56 €
price.format(Locale.forLanguageTag("en"))             // €1,234.56
Country.BR.currency                                   // Currency.BRL
```

Nothing at the call site says which layer answered, which is the point:
`Country.BR.alpha3` reads from `-types`, `Country.forAlpha3("BRA")` from
`-core` and `Country.BR.displayName(locale)` from `-cldr`, and all three are
written the same way.

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
        // one domain, all three layers
        implementation("dev.carcara:kotlinx-locale-country-types:0.1.0-SNAPSHOT")
        implementation("dev.carcara:kotlinx-locale-country-core:0.1.0-SNAPSHOT")
        implementation("dev.carcara:kotlinx-locale-country-cldr:0.1.0-SNAPSHOT")
    }
}
```

Each layer pulls the ones below it transitively, so `-cldr` alone is enough in
practice; declaring all three keeps what you depend on visible. There is no
umbrella artifact, because an artifact whose only job is to pull three others
is a second place for the dependency set to be wrong. A version catalog bundle
does the same job in the build where the versions already live:

```toml
[bundles]
locale-country = ["locale-country-types", "locale-country-core", "locale-country-cldr"]
```

Dropping `-cldr` gives you the codes, the lookups and `CurrencyAmount` without
any translated text, which is the difference between roughly 25 KB and roughly
430 KB gzipped for country on Kotlin/JS.

## Using the platform's data instead of ours

Every domain also has a `-platform` layer that answers from the host rather than
from bundled tables: `java.util.Locale` and `java.time` on JVM and Android,
`Intl` on JS and Wasm/JS, Foundation on Apple. Nothing is shipped, and in
exchange the answers are whatever the device says.

```kotlin
import dev.carcara.kotlinx.locale.datetime.platform.*

date.format(FormatStyle.LONG, Locale.forLanguageTag("pt-BR"))
```

The same call as the CLDR version with a different import, which is what the
package split is for.

What that saves, from the Kotlin/JS probes in `tools/` making identical calls
against each layer: 20.2 KB against 416.9 KB for country, 20.7 KB against
329.4 KB for currency, 35.3 KB against 112.5 KB for datetime, and 45.0 KB
against 823.7 KB for all three at once. Datetime saves the least because
`kotlinx-datetime` is in both numbers and only the formatting moved.

Two things to know before choosing it.

Platform sources are partial, and deliberately so. Linux, Windows, Android
Native and WASI expose no locale data Kotlin can read, so on those four every
lookup misses. Cash rounding is not a platform concept anywhere. Accounting
formats exist on `Intl` and Foundation but not in `java.text`. Currency parsing
exists only where it is exact, which is JVM and Android. A miss is the signal the
`Fallback*` composers read:

```kotlin
val dates = FallbackDateTimeFormats(primary = PlatformDateTime, fallback = CldrDateTime)
```

Composition does not round trip across sources. Foundation writes `¥` for JPY in
`ja` where CLDR writes the fullwidth `￥`, so a string one produced is not
necessarily one the other parses. Formatting with the platform and parsing with
CLDR is not something the library promises.

## Shipping only the locales you use

Most applications need a handful of locales, not 1121. The Gradle plugin
generates the data set for the ones a build declares:

```kotlin
plugins {
    id("dev.carcara.kotlinx-locale") version "0.1.0-SNAPSHOT"
}

kotlinxLocale {
    locales(Pt.BR, En.US, Ja.BASE)
    fallback(En.US)
    packageName = "com.example.locale"

    country { names = true }
    currency { names = true; formats = true }
    datetime { patterns = true }
}
```

The dependency block then takes `-core`, `-types` and `-cldr-format` and leaves
out `-cldr`, because the records come from the generator instead. Call sites do
not change: the generated source implements the same interfaces and carries the
same extensions, so `Country.BR.displayName(locale)` still reads the same and
only the import moves. `samples/narrowed/` is a build that does this, with 124 KB
of generated Kotlin where the shipped tables are 3764 KB.

`fallback` is required, and required to be one of the generated locales. Ask a
three-locale build for `de` and it answers in the fallback rather than returning
nothing, which matters most for dates: a country or a currency can degrade to its
ISO code, but a date would surface as an ISO 8601 timestamp in the middle of a
translated screen.

Narrowing only ever touches locale data. `Country.forAlpha2("br")` and
`Currency.forCode("jpy")` keep working whatever you generated, because an app
that displays three currencies can still be handed an arbitrary code by a payment
API.

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

Each library module records its public ABI under `<module>/api/`: one
`.klib.api` file covering every Kotlin/Native, JS and Wasm target, plus
`jvm/<module>.api` for the JVM bytecode.

```sh
./gradlew checkKotlinAbi   # compare the sources against the recorded ABI
./gradlew updateKotlinAbi  # rewrite it after a deliberate API change
```

Commit the rewritten files together with the code that changed them, so the
diff shows what the change does to the published surface. The check is not
part of `check`, so `./gradlew build` skips it: a complete comparison needs a
klib for every target and only a macOS host can build them all, so running it
elsewhere would compare a subset and still report success.

CI (`.github/workflows/ci.yml`) runs on every push to `main` and on pull
requests: a ktlint check, an ABI check on macOS, plus `./gradlew build` on
Linux, macOS and Windows runners, which together cover every target's tests a
host can execute. Pull requests must be green on all of these checks before
merging.

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
