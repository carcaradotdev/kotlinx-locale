# kotlinx-locale

[![Kotlin](https://img.shields.io/badge/kotlin-2.4.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/carcaradotdev/kotlinx-locale/actions/workflows/ci.yml/badge.svg)](https://github.com/carcaradotdev/kotlinx-locale/actions/workflows/ci.yml)

Locale-aware dates, countries and currencies for Kotlin Multiplatform, written
entirely in common Kotlin.

Kotlin has no multiplatform locale API. There is no common `Locale` type, and
[kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime) deliberately
ships no locale data, so common code has no way to turn a date into
"27 de julho de 2026" or "2026年7月27日". This library fills that gap. The data
comes from Unicode's CLDR, compiled into Kotlin source by a generator, so the
same call returns the same string on JVM, Android, JS, Wasm and every Native
target. The host's own locale APIs are never involved unless you ask for them.

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

Countries and currencies work the same way, with their own imports:

```kotlin
import dev.carcara.kotlinx.locale.country.*
import dev.carcara.kotlinx.locale.country.cldr.*
import dev.carcara.kotlinx.locale.currency.*
import dev.carcara.kotlinx.locale.currency.cldr.*

Country.forAlpha3("BRA")                              // Country.BR
Country.BR.displayName(Locale.forLanguageTag("fr"))   // Brésil
Country.BR.currency                                   // Currency.BRL

val price = CurrencyAmount(Currency.EUR, 123456)      // 1234.56 in minor units
price.format(Locale.forLanguageTag("de"))             // 1.234,56 €
price.format(Locale.forLanguageTag("en"))             // €1,234.56
```

Every function, parameter and default is listed in [API.md](API.md).

## Contents

- [Features](#features)
- [Using it in your project](#using-it-in-your-project)
- [Modules](#modules)
- [Naming the fields instead of picking a length](#naming-the-fields-instead-of-picking-a-length)
- [Using the host's data instead of ours](#using-the-hosts-data-instead-of-ours)
- [Shipping only the locales you use](#shipping-only-the-locales-you-use)
- [Supported platforms](#supported-platforms)
- [Where the data comes from](#where-the-data-comes-from)
- [Building](#building)
- [Scope and limitations](#scope-and-limitations)
- [License](#license)

## Features

Dates and times:

- The four CLDR standard lengths (`FULL`, `LONG`, `MEDIUM`, `SHORT`) for dates,
  times and date-times across all 1121 CLDR locales.
- Skeleton formatting, where you name the fields and the locale arranges them:
  `date.format("yMMMd", ptBR)` is "27 de jul. de 2026" and the same call in `ja`
  is "2026年7月27日". The chosen pattern is available on its own, so it can drive
  kotlinx-datetime's `DateTimeFormat`. Opt in through a separate artifact.
- Localized month and weekday names in wide, abbreviated and narrow widths,
  taken from CLDR's "format" context, so Russian July is the genitive `июля`
  that belongs in a sentence rather than the nominative `июль`.
- Flexible day periods where a locale's standard patterns use them. zh-Hant
  times render as 凌晨2:05 at two in the morning, 下午3:05 in the afternoon and
  晚上8:05 in the evening.
- Native digit systems. ar-EG writes years as ٢٠٢٦, fa as ۲۰۲۶, bn as ২০২৬.

Countries:

- The 249 officially assigned ISO 3166-1 countries as an enum, carrying their
  alpha-2, alpha-3 and numeric codes.
- Localized names for every CLDR locale, plus reverse lookup by name.

Currencies:

- The 178 active ISO 4217 currencies as an enum, carrying both the ISO minor
  units and CLDR's formatting digits. The two disagree on purpose: the Albanian
  lek has 2 ISO minor units and formats with 0. Converters move values between
  the scales.
- Locale-aware formatting from CLDR patterns: `$1,234.56`, `1.234,56 €`,
  `₹1,23,456.78`, `‏١٬٢٣٤٫٥٦ ج.م.‏`, with accounting and cash variants. CHF
  cash rounds to 0.05.
- Formatted strings parse back to ISO minor units. `200 Ft` becomes 20000,
  because HUF prints without its two ISO decimals.
- A country-to-currency map from CLDR's legal-tender data.

Locales:

- A `Locale` type that parses BCP 47 tags and POSIX identifiers, with CLDR
  fallback. pt-XX falls back to pt, and an unknown language to CLDR root.
- `Locale.current` reads the system locale. This is the project's single
  expect/actual: one function per platform returns a raw tag, and everything
  else runs in commonMain.

## Using it in your project

The artifacts are not on Maven Central yet. Until they are, clone this
repository and publish to your local Maven repository:

```sh
./gradlew publishToMavenLocal
```

Then add `mavenLocal()` to the consuming build and depend on `0.1.0-SNAPSHOT`.
The coordinates below are the ones the artifacts will carry when they ship, so
nothing but the repository line changes later.

### Version catalog

Everything published, ready to paste into `gradle/libs.versions.toml`:

```toml
[versions]
kotlinx-locale = "0.1.0-SNAPSHOT"

[libraries]
# The Locale type and the LocaleDataSource contract. Everything depends on this.
locale-core = { module = "dev.carcara:kotlinx-locale-core", version.ref = "kotlinx-locale" }
# The generated locale catalog: PT.BR instead of "pt-BR". Optional.
locale-types = { module = "dev.carcara:kotlinx-locale-types", version.ref = "kotlinx-locale" }
# What the host can say about locales before any domain is involved.
locale-platform = { module = "dev.carcara:kotlinx-locale-platform", version.ref = "kotlinx-locale" }

# Country
locale-country-types = { module = "dev.carcara:kotlinx-locale-country-types", version.ref = "kotlinx-locale" }
locale-country-core = { module = "dev.carcara:kotlinx-locale-country-core", version.ref = "kotlinx-locale" }
locale-country-cldr-runtime = { module = "dev.carcara:kotlinx-locale-country-cldr-runtime", version.ref = "kotlinx-locale" }
locale-country-cldr-full = { module = "dev.carcara:kotlinx-locale-country-cldr-full", version.ref = "kotlinx-locale" }
locale-country-platform = { module = "dev.carcara:kotlinx-locale-country-platform", version.ref = "kotlinx-locale" }

# Currency
locale-currency-types = { module = "dev.carcara:kotlinx-locale-currency-types", version.ref = "kotlinx-locale" }
locale-currency-core = { module = "dev.carcara:kotlinx-locale-currency-core", version.ref = "kotlinx-locale" }
locale-currency-cldr-runtime = { module = "dev.carcara:kotlinx-locale-currency-cldr-runtime", version.ref = "kotlinx-locale" }
locale-currency-cldr-full = { module = "dev.carcara:kotlinx-locale-currency-cldr-full", version.ref = "kotlinx-locale" }
locale-currency-platform = { module = "dev.carcara:kotlinx-locale-currency-platform", version.ref = "kotlinx-locale" }

# Date and time
locale-datetime-core = { module = "dev.carcara:kotlinx-locale-datetime-core", version.ref = "kotlinx-locale" }
locale-datetime-cldr-runtime = { module = "dev.carcara:kotlinx-locale-datetime-cldr-runtime", version.ref = "kotlinx-locale" }
locale-datetime-cldr-full = { module = "dev.carcara:kotlinx-locale-datetime-cldr-full", version.ref = "kotlinx-locale" }
# Skeleton formatting, on top of -cldr-full. Opt in.
locale-datetime-cldr-skeletons = { module = "dev.carcara:kotlinx-locale-datetime-cldr-skeletons", version.ref = "kotlinx-locale" }
locale-datetime-platform = { module = "dev.carcara:kotlinx-locale-datetime-platform", version.ref = "kotlinx-locale" }

[bundles]
# Bundled CLDR data: the normal choice.
locale-country-cldr = ["locale-country-types", "locale-country-core", "locale-country-cldr-full"]
locale-currency-cldr = ["locale-currency-types", "locale-currency-core", "locale-currency-cldr-full"]
locale-datetime-cldr = ["locale-datetime-core", "locale-datetime-cldr-full"]
# The same, plus skeleton formatting.
locale-datetime-skeletons = ["locale-datetime-core", "locale-datetime-cldr-full", "locale-datetime-cldr-skeletons"]

# The host's data instead, shipping no tables.
locale-country-host = ["locale-country-types", "locale-country-core", "locale-country-platform"]
locale-currency-host = ["locale-currency-types", "locale-currency-core", "locale-currency-platform"]
locale-datetime-host = ["locale-datetime-core", "locale-datetime-platform"]

# For a build that generates its own narrowed data with the Gradle plugin.
locale-country-narrowed = ["locale-country-types", "locale-country-core", "locale-country-cldr-runtime"]
locale-currency-narrowed = ["locale-currency-types", "locale-currency-core", "locale-currency-cldr-runtime"]
locale-datetime-narrowed = ["locale-datetime-core", "locale-datetime-cldr-runtime"]

[plugins]
# Generates a data set narrowed to the locales a build declares.
kotlinx-locale = { id = "dev.carcara.kotlinx-locale", version.ref = "kotlinx-locale" }
```

### Gradle

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.bundles.locale.datetime.cldr)
        implementation(libs.bundles.locale.country.cldr)
        implementation(libs.bundles.locale.currency.cldr)
    }
}
```

Without a version catalog, the same thing written out:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        implementation("dev.carcara:kotlinx-locale-datetime-core:0.1.0-SNAPSHOT")
        implementation("dev.carcara:kotlinx-locale-datetime-cldr-full:0.1.0-SNAPSHOT")
    }
}
```

Each layer depends on the ones below it with `api`, so `-cldr-full` on its own
resolves the whole set. The bundles list all three because a dependency block
that names what it uses is easier to audit than one that relies on transitives.

There is no umbrella artifact. An artifact whose only job is to pull three
others is a second place for the dependency set to be wrong, and a catalog
bundle does the same job in the build where the versions already live.

### Maven

Only the JVM variant, since Maven cannot resolve Kotlin Multiplatform metadata:

```xml
<dependency>
    <groupId>dev.carcara</groupId>
    <artifactId>kotlinx-locale-datetime-cldr-full-jvm</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Kotlin and kotlinx-datetime versions

Built against Kotlin 2.4.0. The datetime modules expose kotlinx-datetime 0.8.0
as an `api` dependency, so `LocalDate` in your code and `LocalDate` in a format
call are the same type.

## Modules

Artifacts are named `kotlinx-locale[-<domain>]-<layer>`. Every domain has the
same layers, sorted along one axis: who supplies the data. The translated text,
which is the part that weighs anything, lives in exactly one of them.

```
kotlinx-locale-currency-types          generated enums
kotlinx-locale-currency-core           the contract
kotlinx-locale-currency-cldr-runtime   the engine, no data
kotlinx-locale-currency-cldr-full      the engine plus 1121 locales
kotlinx-locale-currency-platform       the host supplies it
```

Nothing at a call site says which layer answered, which is the point.
`Country.BR.alpha3` reads from `-types`, `Country.forAlpha3("BRA")` from `-core`
and `Country.BR.displayName(locale)` from `-cldr-full`, and all three are
written the same way. Generated types carry only their per-entry data;
everything else about them is an extension, so a declaration can move between
layers without touching a call site.

| Module | What it contains |
| --- | --- |
| `kotlinx-locale-core` | The `Locale` type: tag parsing, normalization, system locale detection, the fallback chain, and the `LocaleDataSource` contract every data source answers. Depends on nothing. |
| `kotlinx-locale-platform` | What the host can say about locales before any domain is involved: whether it exposes locale data, and which locales it enumerates. |
| `kotlinx-locale-types` | The generated locale catalog: one enum per language, so `PT.BR` names a locale the compiler checks instead of a string that fails at runtime. Optional. |
| `kotlinx-locale-country-types` | The `Country` enum: 249 ISO 3166-1 entries carrying their alpha-3 and numeric codes. Generated, and nothing else. |
| `kotlinx-locale-country-core` | `alpha2`, the `for*` lookups, and `CountryNameSource` with the total operations and the fallback composer over it. |
| `kotlinx-locale-country-cldr-runtime` | The country-name lookup over CLDR-shaped name records, and none of the records. The table is a constructor argument, which is what a narrowed build binds its own to. |
| `kotlinx-locale-country-cldr-full` | `-cldr-runtime` plus the CLDR name tables for all 1121 locales: `CldrCountry` and `Country.displayName`. |
| `kotlinx-locale-country-platform` | `PlatformCountry`: country names from `java.util.Locale`, `Intl.DisplayNames` or `NSLocale`. Ships no tables. |
| `kotlinx-locale-currency-types` | The `Currency` enum (active ISO 4217 codes, ISO minor units, CLDR fraction and cash-rounding behavior) and the country-to-currency map. |
| `kotlinx-locale-currency-core` | `code`, `minorUnitDigits`, the ISO/CLDR scale conversions, the `for*` lookups, `CurrencyAmount` and its arithmetic, and the `CurrencyNameSource` and `CurrencyFormatSource` contracts. |
| `kotlinx-locale-currency-cldr-runtime` | The symbol and name lookup plus the pattern-based number formatter and parser, over CLDR-shaped records it does not carry. |
| `kotlinx-locale-currency-cldr-full` | `-cldr-runtime` plus the CLDR symbol, name and number tables for all 1121 locales: `CldrCurrency`, `Currency.symbol`, `Currency.displayName` and `CurrencyAmount.format`. |
| `kotlinx-locale-currency-platform` | `PlatformCurrency`: symbols, names and number formatting from `NumberFormat`, `Intl.NumberFormat` or `NSNumberFormatter`. Ships no tables. |
| `kotlinx-locale-datetime-core` | `FormatStyle`, `TextStyle` and the `DateTimeFormatSource` contract. The only module that depends on kotlinx-datetime. |
| `kotlinx-locale-datetime-cldr-runtime` | The pattern parser and formatter plus the record lookup, over CLDR-shaped records it does not carry. |
| `kotlinx-locale-datetime-cldr-full` | `-cldr-runtime` plus the CLDR pattern data for all 1121 locales: `CldrDateTime`, `LocalDate.format` and friends. |
| `kotlinx-locale-datetime-cldr-skeletons` | `-cldr-full` plus the skeleton tables: `CldrDateTimeSkeletons` and `date.format("yMMMd", locale)`, where you name the fields and the locale decides their order. Opt in, at around 60 KB gzipped on top of `-cldr-full`. |
| `kotlinx-locale-datetime-platform` | `PlatformDateTime`: the four lengths and the calendar names from `DateTimeFormatter`, `Intl.DateTimeFormat` or `NSDateFormatter`. Ships no tables. |
| `kotlinx-locale-codegen-emitters` | The emitters and the bundle reader: the half of code generation a build can run. Parses no XML and clones nothing, so it is safe on a build classpath. |
| `kotlinx-locale-codegen-data` | CLDR resolved into one compact record per locale, versioned by the release it came from. What a build reads instead of cloning CLDR. |
| `kotlinx-locale-gradle-plugin` | The `dev.carcara.kotlinx-locale` plugin, which generates a data set narrowed to the locales a build declares. |

Anything named `kotlinx-locale-codegen-*` runs at build time and never belongs
on an application classpath.

Each published module lives in a directory of the same name, so a listing of
this repository's root reads like a listing on Maven Central. A directory
without the prefix publishes nothing: `conformance-test-suite/` holds the ICU
fixtures and the assertions this repo's own test source sets run, `codegen/` is
the extraction half of code generation that clones CLDR and ICU, `tools/` holds
the Kotlin/JS size probes, and `build-logic/` is the included build with the
convention plugins.

No artifact name is a prefix of another at a hyphen boundary, because Kotlin
Multiplatform already owns that suffix space: every module publishes one
artifact per target, so `-jvm` and `-iosarm64` sit beside the bare coordinate on
Maven Central. `settings.gradle.kts` enforces it at configuration time.

## Naming the fields instead of picking a length

`FormatStyle` offers four fixed lengths. A skeleton instead names the fields you
want, in no particular order, and the locale decides how to arrange them. This is
what `DateFormat.getBestDateTimePattern` gives an Android developer and
`setLocalizedDateFormatFromTemplate` an iOS one.

```kotlin
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.*

date.format("yMMMd", Locale.forLanguageTag("pt-BR"))  // "27 de jul. de 2026"
date.format("yMMMd", Locale.forLanguageTag("ja"))     // "2026年7月27日"
date.format("MMMEd", Locale.forLanguageTag("en"))     // "Mon, Jul 27"
```

The letters are CLDR's: `y` year, `M` month, `d` day, `E` weekday, `Q` quarter,
`h` and `H` hour, `m` minute, `s` second, `G` era. Repeating one asks for a
width, so `MMM` is an abbreviated month name and `MMMM` a full one. `j` asks for
whichever hour the locale prefers together with the day period that goes with
it, which is usually what you want:

```kotlin
time.format("jm", Locale.forLanguageTag("en"))     // "3:05 PM", with U+202F before PM
time.format("jm", Locale.forLanguageTag("en-GB"))  // "15:05"
```

The pattern is available on its own, not only the formatted string:

```kotlin
skeletonPatternOrNull("yMMMd", Locale.forLanguageTag("pt-BR"))  // "d 'de' MMM 'de' y"
skeletonPatternOrNull("yMd", Locale.forLanguageTag("pt-BR"))    // "dd/MM/y"
```

A numeric pattern composes with kotlinx-datetime today, which buys locale-aware
parsing off the same table:

```kotlin
LocalDate.Format { byUnicodePattern(skeletonPatternOrNull("yMd", ptBR)!!) }
```

A pattern naming a month or a weekday does not. `byUnicodePattern` rejects `MMM`
and `EEE` with "the directive is locale-dependent, but locales are not supported
in Kotlin", which is the gap this library fills on the formatting side and does
not yet fill on the parsing side. Formatting is one-way for anything with a name
in it.

Time zones, week numbers and fractional seconds are out of scope, because a
`LocalDate` carries no zone and week numbering needs data this library does not
ship, so a skeleton naming one of those is refused rather than answered a field
short.

The matcher is the algorithm from UTS #35 written in common Kotlin. Nothing
delegates to ICU at runtime; the agreement between the two is a test, held to
patterns generated from ICU4J across 859 locales and 109 skeletons on all eight
targets, plus CLDR's own datetime cases.

The tables live in their own artifact because they are around 210 KB of raw
payload against the 435 KB the whole of `-cldr-full` weighs, so folding them in
would make every consumer of ordinary date formatting pay for skeletons. The
matcher sits in `-cldr-runtime` instead, for the same reason the pattern
formatter already does: a build narrowed through the Gradle plugin generates its
own tables and still needs the algorithm.

## Using the host's data instead of ours

Every domain has a `-platform` layer that answers from the host rather than from
bundled tables: `java.util.Locale` and `java.time` on JVM and Android, `Intl` on
JS and Wasm/JS, Foundation on Apple. Nothing ships, and in exchange the answers
are whatever the device says.

```kotlin
import dev.carcara.kotlinx.locale.datetime.platform.*

date.format(FormatStyle.LONG, Locale.forLanguageTag("pt-BR"))
```

The same call as the CLDR version with a different import, which is what the
package split buys.

What it saves, measured by the Kotlin/JS probes in `tools/` making identical
calls against each layer:

| domain | platform | CLDR | saved |
| --- | ---: | ---: | ---: |
| datetime | 35.3 KB | 112.7 KB | 77.4 KB |
| currency | 20.7 KB | 329.4 KB | 308.7 KB |
| country | 20.2 KB | 416.9 KB | 396.7 KB |
| all three | 45.0 KB | 823.4 KB | 778.3 KB |

Gzipped over the minified bundle. Datetime saves the least because
kotlinx-datetime sits in both numbers and only the formatting moved.
[`docs/size.md`](docs/size.md) has the full table and is regenerated from the
build rather than typed.

Skeleton formatting is CLDR only, and that is a decision rather than a gap. The
hosts will format from a template, but none of them hands back the pattern it
chose, and half of what makes a skeleton useful is reusing that pattern for
parsing. A build that wants skeletons takes
`kotlinx-locale-datetime-cldr-skeletons`.

Two things to know before choosing it.

Platform sources are partial, deliberately. Linux, Windows, Android Native and
WASI expose no locale data Kotlin can read, so on those four every lookup
misses. Cash rounding is not a platform concept anywhere. Accounting formats
exist on `Intl` and Foundation but not in `java.text`. Currency parsing exists
only where it is exact, which is JVM and Android. A miss is the signal the
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
    locales(PT.BR, EN.US, JA)
    fallback(EN.US)
    packageName = "com.example.locale"

    country { names = true }
    currency { names = true; formats = true }
    datetime { patterns = true; skeletons = true }
}
```

`skeletons` implies `patterns`: matching a skeleton scores against the locale's
standard date and time patterns, and rendering the winner needs its month and
weekday names, so the two tables travel together.

The dependency block then takes `-core`, `-types` and `-cldr-runtime` and leaves
out `-cldr-full`, because the records come from the generator instead. Call
sites do not change: the generated source implements the same interfaces and
carries the same extensions, so `Country.BR.displayName(locale)` still reads the
same and only the import moves. `samples/narrowed/` is a working build that does
this, with 124 KB of generated Kotlin where the shipped tables are 3764 KB.

`fallback` is required, and required to be one of the generated locales. Ask a
three-locale build for `de` and it answers in the fallback rather than returning
nothing. That matters most for dates: a country or a currency can degrade to its
ISO code, but a date would surface as an ISO 8601 timestamp in the middle of a
translated screen.

Narrowing only ever touches locale data. `Country.forAlpha2("br")` and
`Currency.forCode("jpy")` keep working whatever you generated, because an app
that displays three currencies can still be handed an arbitrary code by a
payment API.

The plugin DSL is documented in full in [API.md](API.md#gradle-plugin).

## Supported platforms

### Kotlin targets

Every module publishes the same target set, which follows the
[Kotlin/Native tiers](https://kotlinlang.org/docs/native-target-support.html)
and matches what kotlinx-datetime publishes.

| Group | Targets |
| --- | --- |
| JVM | `jvm` (toolchain 21), Android (`compileSdk` 36, `minSdk` 21) |
| Web | `js` (Node.js), `wasmJs` (Node.js), `wasmWasi` (Node.js) |
| Native tier 1 | `macosArm64`, `iosArm64`, `iosSimulatorArm64` |
| Native tier 2 | `linuxX64`, `linuxArm64`, `watchosArm32`, `watchosArm64`, `watchosSimulatorArm64`, `tvosArm64`, `tvosSimulatorArm64` |
| Native tier 3 | `androidNativeArm32`, `androidNativeArm64`, `androidNativeX86`, `androidNativeX64`, `iosX64`, `mingwX64`, `watchosDeviceArm64` |
| Deprecated, still published | `macosX64`, `watchosX64`, `tvosX64` |

The last row is deprecated in Kotlin/Native but still published by
kotlinx-datetime (KT-78660), so dropping it here would strand consumers who
target it.

Only `Locale.current` and the `-platform` modules behave differently across that
list. Everything else is target-independent.

### Locale.current

| Platform | Source |
| --- | --- |
| JVM and Android | `java.util.Locale.getDefault()` |
| Apple platforms | `NSLocale.preferredLanguages`, then `NSLocale.currentLocale` |
| JS and Wasm-JS | `Intl.DateTimeFormat().resolvedOptions().locale` |
| Linux and Android Native | `LC_ALL`, `LC_TIME`, `LANG` |
| Windows | `GetUserDefaultLocaleName` |
| Wasm-WASI | nothing exposed, so `Locale.current` returns `en` |

### What each module answers, per target

🟢 the module answers from that target. 🟡 the lookup misses and the documented
fallback applies.

The bundled modules first, because they are the flat row: pure common Kotlin,
no expect/actual, the same answer on all 25 targets.

| Module | JVM, Android | Apple | JS, Wasm-JS | Linux, Windows, Android Native, Wasm-WASI |
| --- | :-: | :-: | :-: | :-: |
| `kotlinx-locale-core` | 🟢 | 🟢 | 🟢 | 🟢 |
| `kotlinx-locale-types` | 🟢 | 🟢 | 🟢 | 🟢 |
| `kotlinx-locale-country-cldr-full` | 🟢 | 🟢 | 🟢 | 🟢 |
| `kotlinx-locale-currency-cldr-full` | 🟢 | 🟢 | 🟢 | 🟢 |
| `kotlinx-locale-datetime-cldr-full` | 🟢 | 🟢 | 🟢 | 🟢 |
| `kotlinx-locale-datetime-cldr-skeletons` | 🟢 | 🟢 | 🟢 | 🟢 |

`Locale.current` is the one exception in `-core`. It reads a real tag everywhere
except Wasm-WASI, which exposes nothing and so returns `en`.

The `-platform` modules are where the gaps are, and they are not uniform:

| Module | Operation | JVM, Android | Apple | JS, Wasm-JS | Linux, Windows, Android Native, Wasm-WASI |
| --- | --- | :-: | :-: | :-: | :-: |
| `kotlinx-locale-platform` | `isAvailable` | 🟢 `true` | 🟢 `true` | 🟢 `true` | 🟡 `false` |
| | `availableLocaleTags()` | 🟢 full list | 🟢 full list | 🟡 empty | 🟡 empty |
| `kotlinx-locale-country-platform` | `countryNameOrNull` | 🟢 | 🟢 | 🟢 | 🟡 |
| `kotlinx-locale-currency-platform` | `currencySymbolOrNull` | 🟢 | 🟢 | 🟢 | 🟡 |
| | `currencyNameOrNull` | 🟢 | 🟢 | 🟢 | 🟡 |
| | `formatOrNull` | 🟢 | 🟢 | 🟢 | 🟡 |
| | `formatOrNull(accounting = true)` | 🟡 | 🟢 | 🟢 | 🟡 |
| | `formatOrNull(cash = true)` | 🟡 | 🟡 | 🟡 | 🟡 |
| | `parseToMinorUnitsOrNull` | 🟢 | 🟡 | 🟡 | 🟡 |
| `kotlinx-locale-datetime-platform` | `formatDateOrNull` | 🟢 | 🟢 | 🟢 | 🟡 |
| | `formatTimeOrNull` | 🟢 | 🟢 | 🟢 | 🟡 |
| | `formatDateTimeOrNull` | 🟢 | 🟢 | 🟢 | 🟡 |
| | `monthNameOrNull` | 🟢 | 🟢 | 🟢 | 🟡 |
| | `dayOfWeekNameOrNull` | 🟢 | 🟢 | 🟢 | 🟡 |
| | skeleton formatting | not offered | not offered | not offered | not offered |

A 🟡 never surfaces as a null to your code. The total extensions layered in
`-core` fall back: country and currency names degrade to the ISO code, and dates
to ISO 8601. What a miss costs you is the localization, which is why the
`Fallback*` composers exist, and why pairing a `-platform` module with a bundled
one turns every 🟡 above back into a real answer:

```kotlin
val names = FallbackCountryNames(primary = PlatformCountry, fallback = CldrCountry)
val formats = FallbackCurrencyFormats(primary = PlatformCurrency, fallback = CldrCurrency)
val dates = FallbackDateTimeFormats(primary = PlatformDateTime, fallback = CldrDateTime)
```

Skeleton formatting is the one row with no platform column at all. It is not
part of `kotlinx-locale-datetime-core`, so a `-platform` source cannot answer it
even in principle, for the reason given above: the hosts format from a template
but will not hand back the pattern they chose.

Each 🟡 above has a reason, and none of them is a stub waiting to be filled in.

Linux, Windows (`mingwX64`), Android Native and Wasm-WASI expose no locale data
Kotlin can read, so all four return `null` for everything and report
`isAvailable == false`. Reaching a system ICU from those targets would mean
linking against a library the platform does not guarantee is present, at a
version nothing pins.

The empty `availableLocaleTags()` on JS and Wasm-JS is not a gap in the runtime.
ECMA-402 offers `supportedLocalesOf` to filter a list you already have but
nothing to ask for the list, so a source over `Intl` answers every lookup while
being unable to describe its coverage. That is why `isAvailable` and
`availableLocaleTags()` are separate questions.

Cash rounding is not a platform concept anywhere. CLDR knows that CHF cash
rounds to 0.05, and no host formatter does, so `cash = true` misses on every
target.

Accounting formats exist on `Intl` (`currencySign`) and Foundation
(`NSNumberFormatterCurrencyAccountingStyle`) but not in `java.text`, so
`accounting = true` misses on JVM and Android.

Currency parsing is offered only where it is exact. JVM and Android parse
through `BigDecimal`. `Intl` has no parser at all. Foundation's
`numberFromString` returns an `NSNumber` backed by a `Double`, which would
quietly lose minor units on large amounts, so Apple reports a miss rather than
round-tripping money through a `Double`. For the same reason there is no
throwing `parseFormatted` in the platform package, only `parseFormattedOrNull`.

Finally, a host that does not know a code tends to hand the code back rather
than admit it, which `java.util.Locale` does. An answer equal to the requested
code is treated as a miss, because the total operation already falls back to the
code and a composing source would otherwise take the echo for an answer and
never consult its fallback.

## Where the data comes from

The `:codegen` module clones two official Unicode repositories into
`codegen/repos/` (gitignored, sparse, pinned to release tags).

[unicode-org/cldr](https://github.com/unicode-org/cldr) at `release-48-2` is the
source of truth. The generator parses the LDML files, resolves each locale's
inheritance chain (parentLocales rules, root aliases), and emits the flattened
result as encoded string constants into the datetime module, plus the locale tag
list into the base module. Identical payloads are deduplicated: 1121 locales
plus root collapse to 429 unique constants, around 500 KB of Kotlin source. The
same pipeline emits the `Country` and `Currency` enums, the localized
country and currency names, and the per-locale number-formatting data. Names are
stored sparsely, holding only what each locale's own file declares with the
parent chain walked at runtime, because flattening them would multiply the data
many times over.

[unicode-org/icu](https://github.com/unicode-org/icu) at `release-78.3` is used
only for verification. The generator extracts golden fixtures for 30 major
locales from ICU's resource bundles (datetime patterns and names, country
display names, currency symbols and names, number separators), and generated
`Icu*GoldenTest`s verify in each module's commonTest that the CLDR-derived
runtime data agrees with them on every platform. ICU encodes the same upstream
data through a completely different pipeline, so agreement is a strong check on
the parsers and the runtime resolution. ICU's full currency numeric-code table
is emitted as a fixture too, so the ISO 4217 cross-check also runs as a test
everywhere. On the JVM, additional parity tests compare the ISO country and
currency tables against the JDK's own data, a third independent source.

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

This compiles every target and runs the test suite on each platform the host can
execute: JVM, Android host tests, Node.js for JS and both Wasm targets, macOS,
and the iOS and watchOS simulators. Apple simulator tests skip themselves when
the matching runtime is not installed in Xcode.

The library modules share their target list and publishing setup through the
convention plugins in `build-logic/`, an included build rather than `buildSrc`
so that a change to one plugin only invalidates its consumers.

Formatting is enforced with [ktlint](https://pinterest.github.io/ktlint/);
generated sources are excluded by their `// GENERATED` header. Run
`./gradlew ktlintFormat` to fix style before committing, or
`./gradlew ktlintCheck` to verify.

Each library module records its public ABI under `<module>/api/`: one
`.klib.api` file covering every Kotlin/Native, JS and Wasm target, plus
`jvm/<module>.api` for the JVM bytecode.

```sh
./gradlew checkKotlinAbi   # compare the sources against the recorded ABI
./gradlew updateKotlinAbi  # rewrite it after a deliberate API change
```

Commit the rewritten files together with the code that changed them, so the diff
shows what the change does to the published surface. The check is not part of
`check`, so `./gradlew build` skips it: a complete comparison needs a klib for
every target and only a macOS host can build them all, so running it elsewhere
would compare a subset and still report success.

Other tasks worth knowing:

```sh
./gradlew sizeReport            # every artifact against its gzipped budget
./gradlew updateSizeDoc         # regenerate docs/size.md from that report
./gradlew checkLayeringRule     # hand-written code names no specific enum entry
./gradlew -p samples/narrowed build   # the plugin sample, against local artifacts
```

CI runs on every push to `main` and on pull requests: ktlint, the layering
check, plugin validation and a configuration-cache round trip; the size budgets;
the narrowed sample built against locally published artifacts; an ABI check on
macOS; and `./gradlew build` on Linux, macOS and Windows runners, which together
cover every target's tests a host can execute. Pull requests must be green on
all of it before merging.

## Scope and limitations

- Formatting uses each locale's gregorian calendar data. Non-gregorian calendars
  (Buddhist years in Thai, Japanese imperial eras) are not implemented, so Thai
  dates come out as "27 กรกฎาคม ค.ศ. 2026".
- CLDR's FULL and LONG time patterns end with a time-zone name. The formatted
  types carry no zone, so those fields are dropped and FULL and LONG times equal
  the MEDIUM ones in most locales.
- Date and time formatting only. Parsing "27 de julho de 2026" back into a
  `LocalDate` is not supported. Currency strings do parse back:
  `CurrencyAmount.parseFormatted` reads CLDR-formatted values like
  `R$ 1.234,56` or `200 Ft` into ISO minor units, expecting one number with one
  locale's separators rather than free-form text.
- Relative formatting ("yesterday") and interval formatting are not implemented.
  Skeletons are, in `kotlinx-locale-datetime-cldr-skeletons`; see
  [naming the fields](#naming-the-fields-instead-of-picking-a-length).
- The currency enum covers the active ISO 4217 set. Historic currencies (DEM,
  the pre-2005 TRL) are not included, and neither is compact notation (`$1.2K`)
  or plural-aware currency names (`¤¤¤` with a count).

## License

Apache License 2.0. See [LICENSE](LICENSE).

CLDR and ICU data is used under the
[Unicode License](https://www.unicode.org/license.txt).
