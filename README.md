# kotlinx-datetime-locale

Locale-aware formatting for [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime),
written entirely in common Kotlin.

kotlinx-datetime gives you `LocalDate`, `LocalTime` and friends on every Kotlin
platform, but it deliberately ships no locale data: there is no way to turn a
date into "27 de julho de 2026" or "2026年7月27日". This library adds that. The
locale data comes from Unicode's CLDR, compiled into Kotlin source by a code
generator, so formatting produces identical output on JVM, Android, JS, Wasm
and every Native target. The platform's own locale APIs are never involved.

```kotlin
import dev.srsouza.kotlinx.datetime.locale.*
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

## Features

- Date, time and date-time formatting in the four CLDR standard lengths
  (`FULL`, `LONG`, `MEDIUM`, `SHORT`) for all 1121 CLDR locales.
- Localized month and day-of-week names in wide, abbreviated and narrow widths.
- Native digit systems: ar-EG formats years as ٢٠٢٦, fa as ۲۰۲۶, bn as ২০২৬.
- A `Locale` type that parses BCP 47 tags and POSIX identifiers, with CLDR
  fallback (pt-XX falls back to pt, unknown languages to CLDR root).
- `Locale.current` reads the system locale. This is the library's single
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
        implementation("dev.srsouza:kotlinx-datetime-locale:0.1.0-SNAPSHOT")
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
  the flattened result as encoded string constants under
  `core/src/commonMain/.../internal/data/`. Identical payloads are
  deduplicated: 1121 locales plus root collapse to 429 unique constants,
  around 500 KB of Kotlin source.
- [unicode-org/icu](https://github.com/unicode-org/icu) at `release-78.3`,
  used only for testing. The generator extracts patterns and names for 30
  major locales from ICU's resource bundles, and `IcuGoldenTest` verifies in
  commonTest that the CLDR-derived runtime data agrees with them. ICU encodes
  the same upstream data through a completely different pipeline, so agreement
  is a strong check on the parser and flattener.

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

The test suite includes exact-output tests for major locales, an
all-locales sweep that formats every style in every bundled locale, and the
ICU golden cross-check described above. All of it runs from commonTest on
every platform.

## Scope and limitations

- Formatting uses each locale's gregorian calendar data. Non-gregorian
  calendars (Buddhist years in Thai, Japanese imperial eras) are not
  implemented; Thai dates for example come out as "27 กรกฎาคม ค.ศ. 2026".
- CLDR's FULL and LONG time patterns end with a time-zone name. The formatted
  types carry no zone, so those fields are dropped and FULL/LONG times equal
  the MEDIUM ones in most locales.
- Formatting only. Parsing "27 de julho de 2026" back into a `LocalDate` is
  not supported.
- Skeleton-based patterns (`availableFormats`), relative formatting
  ("yesterday") and interval formatting are not implemented.
