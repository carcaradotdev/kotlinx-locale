# kotlinx-datetime-locale

Locale-aware formatting for [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime),
in pure common Kotlin. kotlinx-datetime deliberately ships no locale data; this library
fills that gap by bundling CLDR data as generated Kotlin code, so formatting behaves
identically on every platform with no platform locale APIs involved.

```kotlin
val date = LocalDate(2026, 7, 27)

date.format(FormatStyle.FULL, Locale.forLanguageTag("pt-BR"))
// "segunda-feira, 27 de julho de 2026"

date.format(FormatStyle.MEDIUM, Locale.forLanguageTag("ja"))
// "2026/07/27"

LocalDateTime(date, LocalTime(15, 5)).format(FormatStyle.SHORT, Locale.current)
// e.g. "7/27/26, 3:05 PM"

Month.JULY.displayName(TextStyle.FULL, Locale.forLanguageTag("fr"))
// "juillet"
```

## What you get

- `Locale` — parses BCP 47 and POSIX-flavored tags, with CLDR-style fallback
  (`pt-XX` falls back to `pt`, unknown languages to CLDR root).
- `LocalDate.format(style, locale)`, `LocalTime.format(style, locale)`,
  `LocalDateTime.format(dateStyle, timeStyle, locale)` — the four CLDR standard
  lengths (FULL, LONG, MEDIUM, SHORT), including locale digit systems
  (Arabic-Indic, Bengali, ...).
- `Month.displayName(style, locale)`, `DayOfWeek.displayName(style, locale)`.
- `Locale.current` — the system locale. This is the library's only
  expect/actual: each platform contributes one function returning its raw
  locale tag; everything else is common code.
- Data for all 1100+ CLDR locales, compiled in (about 500 KB of source).

## How the data is produced

The `:codegen` module clones the official (non-archived) Unicode repositories,
pinned to release tags:

- [unicode-org/cldr](https://github.com/unicode-org/cldr) `release-48-2` — the
  source of truth. Gregorian month/day/day-period names, era names, the four
  standard date/time patterns, glue patterns, numbering systems, and locale
  inheritance (`parentLocales`) are parsed from LDML, flattened through each
  locale's inheritance chain, deduplicated, and emitted as Kotlin into
  `core/src/commonMain/.../internal/data/`.
- [unicode-org/icu](https://github.com/unicode-org/icu) `release-78.3` — an
  independent encoding of the same data, used to generate golden test fixtures.
  `IcuGoldenTest` cross-checks the runtime data of 30 major locales against
  what ICU ships, in commonTest, on every platform.

Clones land in `codegen/repos/` (gitignored, sparse, blobless). To refresh:

```sh
./gradlew :codegen:generateLocaleData   # clones if needed, regenerates everything
```

Bump the pinned tags in `codegen/src/main/kotlin/.../Repos.kt` to move to a new
CLDR/ICU release, rerun the task, and review the diff of the generated files.

## Supported platforms

JVM, Android, JS (Node.js), Wasm (JS and WASI), and every Kotlin/Native target
published by kotlinx-datetime (iOS, macOS, watchOS, tvOS, Linux, Windows,
Android Native). System locale detection per platform:

| Platform | Source |
| --- | --- |
| JVM / Android | `java.util.Locale.getDefault()` |
| Apple | `NSLocale.preferredLanguages` / `currentLocale` |
| JS / Wasm-JS | `Intl.DateTimeFormat().resolvedOptions().locale` |
| Linux / Android Native | `LC_ALL` / `LC_TIME` / `LANG` |
| Windows | `GetUserDefaultLocaleName` |
| Wasm-WASI | none exposed; falls back to `en` |

## Building

```sh
./gradlew build
```

Runs all compilations plus the tests that can execute on the host
(JVM, Android host tests, JS/Wasm on Node.js, macOS and Apple simulators).

## Scope notes

- Formatting uses each locale's gregorian calendar data; non-gregorian
  calendars (Buddhist, Japanese imperial eras, ...) are out of scope for now.
- FULL/LONG time styles include a time-zone name in CLDR; since this library
  formats zone-less values, those fields are omitted from the output.
- Parsing localized text back into dates is not supported yet.
