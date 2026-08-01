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
- [Serializing these types](#serializing-these-types)
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
- Localized month and weekday names in wide, abbreviated and narrow widths, in
  both of CLDR's contexts. Russian July is the genitive `июля` inside a sentence
  and the nominative `июль` on a calendar header, and Croatian writes its
  stand-alone narrow months as numbers.
- Relative wording: `3 days ago`, `včera`, `za 10 dní`, with the plural rules
  that pick among a language's forms.
- Time zone names: `Pacific Standard Time`, `PT`, the localized GMT format, and
  the exemplar cities behind `Los Angeles Time`.
- Phone numbers over Google's libphonenumber: parsing what people actually type,
  validation by number type, the E.164, national, international and RFC 3966
  forms, and an as-you-type formatter for a text field. Every territory it
  describes, held to its own answers, in 76 KB.
- Flexible day periods where a locale's standard patterns use them. zh-Hant
  times render as 凌晨2:05 at two in the morning, 下午3:05 in the afternoon and
  晚上8:05 in the evening.
- Native digit systems. ar-EG writes years as ٢٠٢٦, fa as ۲۰۲۶, bn as ২০২৬.

Countries:

- The 249 officially assigned ISO 3166-1 countries as an enum, carrying their
  alpha-2, alpha-3 and numeric codes.
- Localized names for every CLDR locale, plus reverse lookup by name.
- The flag emoji, derived from the alpha-2 code and checked against the RGI
  sequences of UTS #51, so it carries no table.

Numbers:

- Grouped decimals, percentages and compact notation, each with the locale's own
  separators, digits and placement. Czech percentages read `12,5 %` with a
  no-break space, Turkish reads `%12,5`, and `1200` compacts to `1.2K`.
- CLDR plural rules for choosing among translated forms, cardinal and ordinal,
  selected from the number as it will be printed rather than from its value.
- Ordinal forms: `1st`, `1.`, `1º`.
- The raw symbol table, for building what this library does not format.

Languages:

- Language, script and region names in every CLDR locale, plus each language's
  name in itself, composed through the display name algorithm of UTS #35 Part 2.

Currencies:

- Both ISO 4217 lists as an enum, the 178 active codes and the withdrawn ones,
  so an amount denominated in a currency that no longer exists still renders.
- The 178 active ISO 4217 currencies carry both the ISO minor
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

Serialization:

- kotlinx.serialization strategies for `Locale`, `Country`, `Currency` and
  `CurrencyAmount`, in artifacts of their own. Nothing else in the library
  depends on them, so a build that serializes none of these types carries no
  serialization runtime.
- One serializer per representation instead of a blessed default. A country is
  written as alpha-2, alpha-3 or its numeric code because you named the one you
  meant, and an amount as an object, a decimal string or a single scalar.
- A lenient reader per code type that takes every spelling and writes the
  canonical one, which is what a field being migrated needs.

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
# kotlinx.serialization strategies for Locale. Optional, and depended on by nothing else.
locale-serialization = { module = "dev.carcara:kotlinx-locale-serialization", version.ref = "kotlinx-locale" }

# Country
locale-country-types = { module = "dev.carcara:kotlinx-locale-country-types", version.ref = "kotlinx-locale" }
locale-country-core = { module = "dev.carcara:kotlinx-locale-country-core", version.ref = "kotlinx-locale" }
locale-country-cldr-runtime = { module = "dev.carcara:kotlinx-locale-country-cldr-runtime", version.ref = "kotlinx-locale" }
locale-country-cldr-full = { module = "dev.carcara:kotlinx-locale-country-cldr-full", version.ref = "kotlinx-locale" }
locale-country-platform = { module = "dev.carcara:kotlinx-locale-country-platform", version.ref = "kotlinx-locale" }
locale-country-serialization = { module = "dev.carcara:kotlinx-locale-country-serialization", version.ref = "kotlinx-locale" }

# Language, script and region names
locale-language-core = { module = "dev.carcara:kotlinx-locale-language-core", version.ref = "kotlinx-locale" }
locale-language-cldr-runtime = { module = "dev.carcara:kotlinx-locale-language-cldr-runtime", version.ref = "kotlinx-locale" }
locale-language-cldr-full = { module = "dev.carcara:kotlinx-locale-language-cldr-full", version.ref = "kotlinx-locale" }

# Numbers, plurals and ordinals
locale-number-core = { module = "dev.carcara:kotlinx-locale-number-core", version.ref = "kotlinx-locale" }
locale-number-cldr-runtime = { module = "dev.carcara:kotlinx-locale-number-cldr-runtime", version.ref = "kotlinx-locale" }
locale-number-cldr-full = { module = "dev.carcara:kotlinx-locale-number-cldr-full", version.ref = "kotlinx-locale" }

# Currency
locale-currency-types = { module = "dev.carcara:kotlinx-locale-currency-types", version.ref = "kotlinx-locale" }
locale-currency-core = { module = "dev.carcara:kotlinx-locale-currency-core", version.ref = "kotlinx-locale" }
locale-currency-cldr-runtime = { module = "dev.carcara:kotlinx-locale-currency-cldr-runtime", version.ref = "kotlinx-locale" }
locale-currency-cldr-full = { module = "dev.carcara:kotlinx-locale-currency-cldr-full", version.ref = "kotlinx-locale" }
locale-currency-platform = { module = "dev.carcara:kotlinx-locale-currency-platform", version.ref = "kotlinx-locale" }
locale-currency-serialization = { module = "dev.carcara:kotlinx-locale-currency-serialization", version.ref = "kotlinx-locale" }

# Date and time
locale-datetime-core = { module = "dev.carcara:kotlinx-locale-datetime-core", version.ref = "kotlinx-locale" }
locale-datetime-cldr-runtime = { module = "dev.carcara:kotlinx-locale-datetime-cldr-runtime", version.ref = "kotlinx-locale" }
locale-datetime-cldr-full = { module = "dev.carcara:kotlinx-locale-datetime-cldr-full", version.ref = "kotlinx-locale" }
# Skeleton formatting, on top of -cldr-full. Opt in.
locale-datetime-cldr-skeletons = { module = "dev.carcara:kotlinx-locale-datetime-cldr-skeletons", version.ref = "kotlinx-locale" }
# Relative wording, on top of -cldr-runtime rather than -cldr-full. Opt in.
locale-datetime-cldr-relative = { module = "dev.carcara:kotlinx-locale-datetime-cldr-relative", version.ref = "kotlinx-locale" }
locale-datetime-platform = { module = "dev.carcara:kotlinx-locale-datetime-platform", version.ref = "kotlinx-locale" }

# Time zone names
locale-timezone-core = { module = "dev.carcara:kotlinx-locale-timezone-core", version.ref = "kotlinx-locale" }
locale-timezone-cldr-runtime = { module = "dev.carcara:kotlinx-locale-timezone-cldr-runtime", version.ref = "kotlinx-locale" }
locale-timezone-cldr-full = { module = "dev.carcara:kotlinx-locale-timezone-cldr-full", version.ref = "kotlinx-locale" }
# The exemplar cities, on top of -cldr-full. Opt in.
locale-timezone-cldr-cities = { module = "dev.carcara:kotlinx-locale-timezone-cldr-cities", version.ref = "kotlinx-locale" }

# Phone numbers. The data is Google's libphonenumber rather than CLDR.
locale-phone-core = { module = "dev.carcara:kotlinx-locale-phone-core", version.ref = "kotlinx-locale" }
locale-phone-metadata-runtime = { module = "dev.carcara:kotlinx-locale-phone-metadata-runtime", version.ref = "kotlinx-locale" }
locale-phone-metadata-full = { module = "dev.carcara:kotlinx-locale-phone-metadata-full", version.ref = "kotlinx-locale" }
locale-phone-serialization = { module = "dev.carcara:kotlinx-locale-phone-serialization", version.ref = "kotlinx-locale" }

[bundles]
# Bundled CLDR data: the normal choice.
locale-country-cldr = ["locale-country-types", "locale-country-core", "locale-country-cldr-full"]
locale-currency-cldr = ["locale-currency-types", "locale-currency-core", "locale-currency-cldr-full"]
locale-datetime-cldr = ["locale-datetime-core", "locale-datetime-cldr-full"]
# The same, plus skeleton formatting.
locale-datetime-skeletons = ["locale-datetime-core", "locale-datetime-cldr-full", "locale-datetime-cldr-skeletons"]
# Relative wording, which needs no date patterns.
locale-datetime-relative = ["locale-datetime-core", "locale-datetime-cldr-relative"]
locale-language-cldr = ["locale-language-core", "locale-language-cldr-full"]
locale-number-cldr = ["locale-number-core", "locale-number-cldr-full"]
# Zone names. The second adds the exemplar cities, which is the larger half.
locale-timezone-cldr = ["locale-timezone-core", "locale-timezone-cldr-full"]
locale-timezone-cities = ["locale-timezone-core", "locale-timezone-cldr-cities"]
locale-phone = ["locale-phone-core", "locale-phone-metadata-full"]

# The host's data instead, shipping no tables.
locale-country-host = ["locale-country-types", "locale-country-core", "locale-country-platform"]
locale-currency-host = ["locale-currency-types", "locale-currency-core", "locale-currency-platform"]
locale-datetime-host = ["locale-datetime-core", "locale-datetime-platform"]

# For a build that generates its own narrowed data with the Gradle plugin.
locale-country-narrowed = ["locale-country-types", "locale-country-core", "locale-country-cldr-runtime"]
locale-currency-narrowed = ["locale-currency-types", "locale-currency-core", "locale-currency-cldr-runtime"]
locale-datetime-narrowed = ["locale-datetime-core", "locale-datetime-cldr-runtime"]
locale-language-narrowed = ["locale-language-core", "locale-language-cldr-runtime"]
locale-number-narrowed = ["locale-number-core", "locale-number-cldr-runtime"]
locale-timezone-narrowed = ["locale-timezone-core", "locale-timezone-cldr-runtime"]
# There is no locale-phone-narrowed: the phone metadata is keyed by territory
# rather than by locale, so declaring three locales narrows nothing about it.
# Take locale-phone directly, at 76 KB for every territory in the world.

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

### Kotlin, kotlinx-datetime and kotlinx.serialization versions

Built against Kotlin 2.4.0. The datetime modules expose kotlinx-datetime 0.8.0
as an `api` dependency, so `LocalDate` in your code and `LocalDate` in a format
call are the same type. The `-serialization` modules expose
kotlinx-serialization-core 1.11.0 the same way, for the same reason: a
`KSerializer<Country>` you can name is one your own compile classpath has to
know about.

They need the runtime and nothing else. Their serializers are written by hand,
so the serialization compiler plugin is not applied to a single published source
file here. Apply it in your own build if you write `@Serializable` classes, as
you already would.

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
| `kotlinx-locale-serialization` | `LocaleTagSerializer`, which writes a `Locale` as its BCP 47 tag and reads one as leniently as `Locale.forLanguageTag` does. |
| `kotlinx-locale-country-types` | The `Country` enum: 249 ISO 3166-1 entries carrying their alpha-3 and numeric codes. Generated, and nothing else. |
| `kotlinx-locale-country-core` | `alpha2`, the `for*` lookups, and `CountryNameSource` with the total operations and the fallback composer over it. |
| `kotlinx-locale-country-cldr-runtime` | The country-name lookup over CLDR-shaped name records, and none of the records. The table is a constructor argument, which is what a narrowed build binds its own to. |
| `kotlinx-locale-country-cldr-full` | `-cldr-runtime` plus the CLDR name tables for all 1121 locales: `CldrCountry` and `Country.displayName`. |
| `kotlinx-locale-country-platform` | `PlatformCountry`: country names from `java.util.Locale`, `Intl.DisplayNames` or `NSLocale`. Ships no tables. |
| `kotlinx-locale-country-serialization` | One `Country` serializer per ISO 3166-1 code (alpha-2, alpha-3, numeric), plus a lenient reader that takes all three and writes alpha-2. |
| `kotlinx-locale-language-core` | `LanguageNameSource` and the locale display name algorithm of UTS #35 Part 2: how a language name and its unconsumed subtags compose into `Serbian (Cyrillic)`. |
| `kotlinx-locale-language-cldr-runtime` | The language, script and region name lookup over CLDR-shaped records it does not carry. |
| `kotlinx-locale-language-cldr-full` | `-cldr-runtime` plus the name tables: `CldrLanguage`, `Locale.displayName` and `Locale.nativeDisplayName`. The largest table in the library, which is the strongest argument for the Gradle plugin. |
| `kotlinx-locale-number-core` | `Decimal`, `NumberSymbols`, `PluralCategory`, `SignDisplay` and the number, plural and ordinal contracts. Its own README records where each part of the behaviour is defined, because CLDR settles the data and not the option names. |
| `kotlinx-locale-number-cldr-runtime` | The CLDR pattern engine, the compact algorithm, the plural rule evaluator and the ordinal rule evaluator. The currency domain formats through this one rather than through a copy. |
| `kotlinx-locale-number-cldr-full` | `-cldr-runtime` plus the symbol, pattern, compact, plural and ordinal tables: `CldrNumber`, `numberFormat`, `numberFormatPercent`, `numberOrdinal`, `numberSymbols` and `pluralCategory`. |
| `kotlinx-locale-currency-types` | The `Currency` enum (both ISO 4217 lists, ISO minor units, CLDR fraction and cash-rounding behavior, tender windows) and the country-to-currency map. |
| `kotlinx-locale-currency-core` | `code`, `minorUnitDigits`, the ISO/CLDR scale conversions, the `for*` lookups, `CurrencyAmount` and its arithmetic, and the `CurrencyNameSource` and `CurrencyFormatSource` contracts. |
| `kotlinx-locale-currency-cldr-runtime` | The symbol and name lookup plus the pattern-based number formatter and parser, over CLDR-shaped records it does not carry. |
| `kotlinx-locale-currency-cldr-full` | `-cldr-runtime` plus the CLDR symbol, name and number tables for all 1121 locales: `CldrCurrency`, `Currency.symbol`, `Currency.displayName` and `CurrencyAmount.format`. |
| `kotlinx-locale-currency-platform` | `PlatformCurrency`: symbols, names and number formatting from `NumberFormat`, `Intl.NumberFormat` or `NSNumberFormatter`. Ships no tables. |
| `kotlinx-locale-currency-serialization` | The `Currency` serializers (alphabetic code, numeric code, lenient) and the three `CurrencyAmount` forms. Locale-independent throughout, and so free of CLDR. |
| `kotlinx-locale-datetime-core` | `FormatStyle`, `TextStyle` and the `DateTimeFormatSource` contract. The only module that depends on kotlinx-datetime. |
| `kotlinx-locale-datetime-cldr-runtime` | The pattern parser and formatter plus the record lookup, over CLDR-shaped records it does not carry. |
| `kotlinx-locale-datetime-cldr-full` | `-cldr-runtime` plus the CLDR pattern data for all 1121 locales: `CldrDateTime`, `LocalDate.format` and friends. |
| `kotlinx-locale-datetime-cldr-skeletons` | `-cldr-full` plus the skeleton tables: `CldrDateTimeSkeletons` and `date.format("yMMMd", locale)`, where you name the fields and the locale decides their order. Opt in, at around 60 KB gzipped on top of `-cldr-full`. |
| `kotlinx-locale-datetime-cldr-relative` | `CldrRelativeTime` and `relativeTimeFormat`: `3 days ago` and `včera`, with the plural rules that pick among a language's forms. Its own artifact because it needs no date patterns. |
| `kotlinx-locale-datetime-platform` | `PlatformDateTime`: the four lengths and the calendar names from `DateTimeFormatter`, `Intl.DateTimeFormat` or `NSDateFormatter`. Ships no tables. |
| `kotlinx-locale-timezone-core` | `TimeZoneNameSource` and `TimeZoneNameStyle`: the forms UTS #35 Part 4 defines for naming a zone. |
| `kotlinx-locale-timezone-cldr-runtime` | The localized GMT format, metazone resolution and the naming ladder, over records it does not carry. |
| `kotlinx-locale-timezone-cldr-full` | `-cldr-runtime` plus the format and name tables: `CldrTimeZone`, `TimeZone.displayName` and `UtcOffset.displayName`. |
| `kotlinx-locale-timezone-cldr-cities` | `-cldr-full` plus the exemplar cities, for the generic location format. Opt in: this is the largest zone table, and without it the format falls back to the identifier's own last part, which is what the spec prescribes. |
| `kotlinx-locale-phone-core` | `PhoneNumber`, `PhoneNumberType`, `PhoneNumberFormat` and `PhoneNumberSource`. Keyed by country rather than by locale, because a number is valid or not whoever is reading it. |
| `kotlinx-locale-phone-metadata-runtime` | The parser, the validator, the formatters, the as-you-type formatter, and the bounded pattern matcher they all run on. |
| `kotlinx-locale-phone-metadata-full` | `-metadata-runtime` plus every territory libphonenumber describes: `PhoneNumbers`, `phoneNumberOrNull` and `Country.asYouType`. |
| `kotlinx-locale-phone-serialization` | One serializer per written form, a lenient one that reads all four, and a metadata-free one over the parts. No default: the forms carry different amounts of information. |
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

Where this behaviour is defined is worth saying, because it is two places. The
vocabulary is normative: the field letters of `yMMMd` are UTS #35's Date Field
Symbol Table and `availableFormats` is an LDML element. The matching algorithm
is not. How a missing field is weighed against a wrong width, and which of `M`
and `L` a locale's own pattern imposes, come from ICU's
`DateTimePatternGenerator`, which is why `:codegen` checks out ICU's source and
extracts goldens from it. The number domain has the same split for the same
reason, and `kotlinx-locale-number-core/README.md` sets out the argument for
following ICU where LDML is silent.


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

Read that CLDR column as the price of all 1121 locales, not the price of CLDR.
Most products ship a language picker with a handful of entries in it, and the
Gradle plugin generates the data for exactly those, against the same
`-cldr-runtime` engine the full artifact uses. The three-locale build in
`samples/narrowed/` generates 124 KB of Kotlin where the shipped tables are
3764 KB, roughly a thirtieth of the data, and formats identically for the
locales it kept because it is running the same code over a smaller table.

So the choice is not the two columns above. It is three:

| | what ships | answers | when it fits |
| --- | --- | --- | --- |
| `-platform` | nothing | whatever the host says, with the gaps below | you do not know the locale set, or you want it to track the device |
| `-cldr-full` | all 1121 locales | the same on every target | you need arbitrary locales at runtime |
| plugin plus `-cldr-runtime` | only the locales you named | the same on every target | you know the set at build time, which is most products |

[Shipping only the locales you use](#shipping-only-the-locales-you-use) covers
how to set the third one up.

Those 124 KB and 3764 KB are Kotlin source, counted from the sample. The size
probes in `tools/` do not cover a narrowed build, so there is no gzipped bundle
figure for it to sit beside the table above.

Skeleton formatting is CLDR only, and that is a decision rather than a gap. The
hosts will format from a template, but none of them hands back the pattern it
chose, and half of what makes a skeleton useful is reusing that pattern for
parsing. A build that wants skeletons takes
`kotlinx-locale-datetime-cldr-skeletons`.

Two things to know before choosing it.

Platform sources are partial, for two different reasons.

Locale data is not wired up on Linux, Windows, Android Native or Wasm-WASI yet,
so every lookup misses on those four. That is a gap rather than a verdict on the
platforms, and contributions are welcome.

The rest is the shape of the host APIs. Cash rounding is not a platform concept
anywhere. Accounting formats exist on `Intl` and Foundation but not in
`java.text`. Currency parsing exists only where it is exact, which is JVM and
Android.

Either way a miss is the signal the `Fallback*` composers read:

```kotlin
val dates = FallbackDateTimeFormats(primary = PlatformDateTime, fallback = CldrDateTime)
```

Composition does not round trip across sources. Foundation writes `¥` for JPY in
`ja` where CLDR writes the fullwidth `￥`, so a string one produced is not
necessarily one the other parses. Formatting with the platform and parsing with
CLDR is not something the library promises.

## Serializing these types

Three artifacts carry kotlinx.serialization strategies, one per domain, and
nothing else in the library depends on them:

```kotlin
import dev.carcara.kotlinx.locale.serialization.*
import dev.carcara.kotlinx.locale.country.serialization.*
import dev.carcara.kotlinx.locale.currency.serialization.*

@Serializable
data class Order(
    @Serializable(with = LocaleTagSerializer::class) val locale: Locale,
    @Serializable(with = CountryAlpha3Serializer::class) val shipTo: Country,
    @Serializable(with = CurrencyAmountMinorUnitsSerializer::class) val total: CurrencyAmount,
)

val order = Order(
    locale = Locale.forLanguageTag("pt-BR"),
    shipTo = Country.BR,
    total = CurrencyAmount(Currency.BRL, 1234_56),
)

Json.encodeToString(order)
// {"locale":"pt-BR","shipTo":"BRA","total":{"currency":"BRL","minorUnits":123456}}
```

`Country` and `Currency` need none of this to serialize at all. Their entry
names are the alpha-2 and the ISO 4217 alphabetic code, and the plugin writes an
enum as its entry name, so an unannotated property already produces `"BR"` and
`"BRL"`. What the named serializers add is a contract: `CountryAlpha3Serializer`
on a field says alpha-3 out loud, and it fails on the day something sends
`"BR"` instead of quietly accepting it.

### Naming the form, rather than picking a default

Every serializer says which representation it is, and there is no unnamed one to
fall into by accident:

| Type | Serializer | JSON |
| --- | --- | --- |
| `Locale` | `LocaleTagSerializer` | `"pt-BR"` |
| `Country` | `CountryAlpha2Serializer` | `"US"` |
| | `CountryAlpha3Serializer` | `"USA"` |
| | `CountryNumericCodeSerializer` | `840` |
| | `CountryLenientCodeSerializer` | reads all three, writes `"US"` |
| `Currency` | `CurrencyCodeSerializer` | `"USD"` |
| | `CurrencyNumericCodeSerializer` | `840` |
| | `CurrencyLenientCodeSerializer` | reads both, writes `"USD"` |
| `CurrencyAmount` | `CurrencyAmountMinorUnitsSerializer` | `{"currency":"USD","minorUnits":123456}` |
| | `CurrencyAmountDecimalSerializer` | `{"currency":"USD","amount":"1234.56"}` |
| | `CurrencyAmountCodeAndDecimalSerializer` | `"USD 1234.56"` |

The two object forms of an amount differ in where the scale lives. `minorUnits`
is exact and needs no parsing, but `123456` is $1,234.56 only because the
`Currency` enum says USD has two minor units, so both ends have to agree on the
ISO data. The decimal string puts the scale in the payload, which is what a row
that outlives a release wants. The combined string is the one to reach for when
the amount has to fit a single scalar: a map key, a query parameter, a column
you would rather not split in two.

### Reading a field that has more than one spelling

A country's three ISO code spaces do not overlap. Alpha-2 is two letters,
alpha-3 is three, numeric is digits, so one reader can take any of them and
still know which space it is in:

```kotlin
Json.decodeFromString(CountryLenientCodeSerializer, "\"US\"")   // Country.US
Json.decodeFromString(CountryLenientCodeSerializer, "\"USA\"")  // Country.US
Json.decodeFromString(CountryLenientCodeSerializer, "\"840\"")  // Country.US
Json.decodeFromString(CountryLenientCodeSerializer, "\"004\"")  // Country.AF
```

It writes alpha-2 whichever one it read, so the second time a row is written it
is canonical. That is the migration: point the field at this serializer, and the
old spellings drain out as rows are touched.

One limit is worth knowing before you rely on it. The lenient readers take the
numeric code as a *string*, `"840"`. A JSON number `840` is a different token,
and a `Decoder` has to commit to `decodeString` or `decodeInt` before it can see
which one is coming. The format-agnostic API these are written against offers
no way to peek. So a bare number needs one of two things:

```kotlin
// Tell the format to be forgiving; it hands the unquoted token over as text.
Json { isLenient = true }.decodeFromString(CountryLenientCodeSerializer, "840")

// Or, better, name the serializer for the type the field actually holds.
Json.decodeFromString(CountryNumericCodeSerializer, "840")
```

The second is the honest answer when the field is genuinely a number. The
declaration then says what the field holds, and read time has nothing left to
resolve.

### Amounts are never locale-formatted

None of the `CurrencyAmount` serializers touches `Locale`, and
`kotlinx-locale-currency-serialization` depends on no CLDR data. The two string
forms of an amount do different jobs. `toDecimalString` writes ASCII digits and
a `.` and nothing else; `format(locale)` writes what a person expects to read,
which on some locales means grouping separators, a symbol, Arabic-Indic digits
and a narrow no-break space.

Only the first can be a wire format. The second cannot be read back without
knowing which locale wrote it, and CLDR moves separators between releases, so an
amount stored under one release could come back a different number under the
next. `"USD 1,234.56"` is a `SerializationException` here, deliberately.

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
this, at [roughly a thirtieth of the data](#using-the-hosts-data-instead-of-ours)
for the three locales it declares.

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

Time zone names are the one place where that flat row meets something the
platform owns. Naming a zone is pure common Kotlin like everything else here,
because it works from the identifier and the tables. Constructing a
`kotlinx.datetime.TimeZone` is not: each target reads whichever copy of the IANA
time zone database it has, and Kotlin/JS under Node has no full one, so
`TimeZone.of("America/Los_Angeles")` throws there for an identifier every other
target accepts. That is a property of the runtime rather than of this library,
and it is why the zone tests skip where a zone cannot be built.

| Module | JVM, Android | Apple | JS, Wasm-JS | Linux, Windows, Android Native, Wasm-WASI |
| --- | :-: | :-: | :-: | :-: |
| `kotlinx-locale-core` | 🟢 | 🟢 | 🟢 | 🟢 |
| `kotlinx-locale-types` | 🟢 | 🟢 | 🟢 | 🟢 |
| `kotlinx-locale-country-cldr-full` | 🟢 | 🟢 | 🟢 | 🟢 |
| `kotlinx-locale-currency-cldr-full` | 🟢 | 🟢 | 🟢 | 🟢 |
| `kotlinx-locale-datetime-cldr-full` | 🟢 | 🟢 | 🟢 | 🟢 |
| `kotlinx-locale-datetime-cldr-skeletons` | 🟢 | 🟢 | 🟢 | 🟢 |
| `kotlinx-locale-serialization` | 🟢 | 🟢 | 🟢 | 🟢 |
| `kotlinx-locale-country-serialization` | 🟢 | 🟢 | 🟢 | 🟢 |
| `kotlinx-locale-currency-serialization` | 🟢 | 🟢 | 🟢 | 🟢 |

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

The 🟡s come from two different places, and the difference matters if you are
deciding whether to wait for one to change.

Linux, Windows (`mingwX64`), Android Native and Wasm-WASI have no locale data
wired up yet, so all four return `null` for everything and report
`isAvailable == false`. This is the one gap that is simply unbuilt rather than
decided. Windows and Linux in particular do have locale facilities to read, and
this library already reads a little of both for `Locale.current`, so extending
that to names, currencies and dates is work waiting to be done rather than a
wall. If you want one of these targets, that is a contribution worth having.

Everything else below is a property of the host APIs and will not change by
trying harder.

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
official ISO 4217 XML published by SIX is vendored as a snapshot and parsed
during generation: `codegen/src/main/resources/iso4217/list-one.xml` for the
active codes and `list-three.xml` for the withdrawn ones, both published
2026-01-01. List three omits the minor units field entirely, so a withdrawn
code takes CLDR's fraction data instead, and the JDK parity test is what
confirms the two agree for the codes the JDK knows.

Flag emoji are not CLDR either. The RGI flag sequences of UTS #51 are vendored
the same way at `codegen/src/main/resources/emoji/emoji-sequences.txt` from
Emoji 17.0, and used only to check at generation time that every country's
derived sequence is one Unicode recommends. Nothing from that file is compiled
into an artifact. The country set is CLDR's `regular` region validity
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
- Relative wording is implemented in `kotlinx-locale-datetime-cldr-relative`,
  but you choose the unit. Whether ninety minutes reads as "in 90 minutes" or
  "in 2 hours" is not standardized by CLDR, ECMA-402 or ICU, all of which take
  the unit from the caller. Interval formatting is not implemented.
- Plural-aware currency names (`¤¤¤` with a count) are not implemented, though
  the plural rules they need are.
- Time zone names do not take an instant. Pass the style you want, because
  kotlinx-datetime exposes no way to ask whether a zone is on daylight time and
  inferring it would be invisible at the call site. Zone name parsing is not
  implemented either, and ISO 8601 zone formats are kotlinx-datetime's job.
- Ordinals come in the plain form only. CLDR ships gendered and case-inflected
  rule sets, and UTS #35 says outright that it supplies no data for choosing
  between them.
- Phone numbers do not come with geocoding or carrier lookup. That is a size
  decision: libphonenumber's geocoding data is 11 MB and its carrier data 1.3 MB,
  and both are locale-keyed where the rest of that domain is region-keyed. The
  as-you-type formatter is also the one part of the domain not held to
  libphonenumber character-for-character; see
  [docs/not-standardized.md](docs/not-standardized.md), which records every
  boundary of this kind and why it sits where it does.
- The `-platform` modules do not read locale data on Linux, Windows, Android
  Native or Wasm-WASI yet. The bundled `-cldr-*` modules answer on all of them,
  so this only affects a build that chose the host's data; see
  [what each module answers](#what-each-module-answers-per-target).

## License

Apache License 2.0. See [LICENSE](LICENSE).

CLDR and ICU data is used under the
[Unicode License](https://www.unicode.org/license.txt).
