# Plan: splitting the API from the data

A proposal for restructuring the library into a pure API core, a pure type
layer, and swappable data implementations, plus a Gradle plugin that generates a
narrowed implementation from a locale set the user declares.

Nothing here is committed to. Read it, mark what is wrong, and the phases at the
end can be re-cut around whatever changes.

## What the measurements say

From the probe in PR 11, gzipped, with the whole public API exported:

| scenario | gzip | CLDR share of the bundle |
| --- | ---: | ---: |
| `kotlinx-datetime` (third party) | 23.7 KB | 0% |
| country enum, codes and lookups only | 24.4 KB | 0% |
| currency enum, codes and lookups only (includes country) | 33.3 KB | 0% |
| country, full | 427.8 KB | 92% |
| currency, full | 748.5 KB | 92% |
| datetime, full | 115.8 KB | 83% |
| everything | 845.1 KB | 91% |

Three facts drive the whole design.

**The code is already small.** Country is 81 KB of code and 1125 KB of text.
Strip the text and it is lighter than kotlinx-datetime. There is no performance
or size problem in what we wrote, only in what we ship alongside it.

**The cut line is five entry points.** `Country.displayName`,
`Currency.symbol`, `Currency.displayName`, `CurrencyAmount.format`,
`CurrencyAmount.parseFormatted`, plus everything under datetime `format` and
`displayName`. Nothing else in the public API touches a translated string. Every
ISO field, every CLDR *numeric* field (fraction digits, rounding increments),
the country to currency map and all of `CurrencyAmount`'s arithmetic are free.

**Filtering locales is the bigger lever, and it composes.** Trimming the
registries to English alone takes the full API from 845 KB to 76 KB. Five
languages cost 91 KB. The first language is expensive, the next four are almost
free.

One caveat worth stating plainly: on JS, dead code elimination already delivers
the 24 KB figure today for anyone who never calls `displayName`. The split does
not create that saving on JS, it makes it reachable *while still calling
`displayName`*. On JVM, Android and Native there is no equivalent, so there the
module boundary is the only lever that exists.

## Target layout

```
kotlinx-locale                 core     Locale, tag parsing, fallback chain, source interfaces
kotlinx-locale-types           types    Country, Currency, CurrencyAmount, style enums
kotlinx-locale-country-cldr    data     country name payloads + CldrCountryNames
kotlinx-locale-currency-cldr   data     currency name, symbol and pattern payloads + sources
kotlinx-locale-datetime-cldr   data     datetime payloads + CldrDateTimeFormats
kotlinx-locale-country         sugar    types + country-cldr, today's ergonomics
kotlinx-locale-currency        sugar    types + currency-cldr + country
kotlinx-locale-datetime        sugar    types + datetime-cldr
kotlinx-locale-*-platform      data     later: Intl, java.util.Locale, NSLocale
```

Dependencies point one way only: `core <- types <- sugar`, and
`core <- *-cldr <- sugar`. No data module depends on `types`, and `types` does
not depend on any data module.

### Why data is split per domain but types is one artifact

The evidence, not symmetry. Country names are 389 KB gzipped, currency names
288 KB, datetime 64 KB, so keeping those in one artifact would force a currency
user to ship country names. Separating them is worth hundreds of kilobytes.

Separating the *enums* by domain is worth about 7 KB, and currency needs the
`Country` enum anyway for the country to currency map. One `types` artifact,
one version to align, one thing for the Gradle plugin to put on a build
classpath.

### Why the umbrella artifacts keep today's coordinates

`implementation("dev.carcara:kotlinx-locale-country")` should keep meaning
"give me countries with names that work". Someone who wants control reaches for
`kotlinx-locale-types` plus a source of their choosing. The default stays the
easy thing; the split is opt-in.

## The design decision this hinges on

Today `displayName` is a member of the `Country` enum, so the enum and the CLDR
tables must live in the same artifact. To split them, the call has to find its
data some other way. Three options.

**A. Runtime registry.** A global `LocaleData.provider` that the data module
installs. Keeps the member function, and every call site is unchanged. Costs a
mutable global, a failure mode where nothing is installed, and an initialization
order problem KMP has no clean answer for. Also defeats dead code elimination,
because the registry references every source that was linked.

**B. Explicit source parameter everywhere.**
`CldrCountryNames.displayName(Country.BR, locale)`. Honest, testable, no state.
Verbose enough that people will complain, and it churns every call site.

**C. Explicit source, with per-implementation sugar.** Recommended.

```kotlin
// core: string-keyed, so core has no dependency on the enums
public interface CountryNameSource : LocaleDataSource {
    public fun countryName(alpha2: String, locale: Locale): String?
    public fun countryCodeForName(name: String, locale: Locale): String?
}

// types: the type-safe layer, source passed in
public fun Country.displayName(locale: Locale, source: CountryNameSource): String =
    source.countryName(alpha2, locale) ?: alpha2

// country-cldr
public object CldrCountryNames : CountryNameSource { /* generated tables */ }

// kotlinx-locale-country: the umbrella binds the default
public fun Country.displayName(locale: Locale = Locale.current): String =
    displayName(locale, CldrCountryNames)
```

`Country.BR.displayName(locale)` still compiles and still means what it means.
Which implementation answers is decided by which artifact you depend on, at
compile time, with no runtime lookup and nothing for the linker to keep alive.
Swapping CLDR for the platform is a dependency change, not a code change.

The cost is that `displayName` stops being a member and becomes an extension,
so it needs an import. At 0.1.0-SNAPSHOT that is a free move. The same applies
to `Currency.symbol`, `Currency.displayName`, `Country.forDisplayNameOrNull`
(an extension on the companion), `CurrencyAmount.format` and
`CurrencyAmount.parseFormatted`.

If two implementations end up on one classpath the imports collide. That is a
loud, compile-time, fixable collision, which is the right failure mode.

## The interfaces have to be operation-shaped

This is the part that decides whether the platform module is possible at all,
and it is worth getting right before anything is written.

The tempting shape mirrors our tables:

```kotlin
public fun dateFormatPattern(style: FormatStyle, locale: Locale): String?  // no
```

No platform can implement that. `Intl.DateTimeFormat` does not hand out CLDR
patterns, it formats. `NSDateFormatter` will derive a pattern from a template
but not the localized standard patterns. So the interface has to sit at the
level of the operation the user asked for, not the table CLDR happens to store:

```kotlin
public interface DateTimeFormatSource : LocaleDataSource {
    public fun formatDate(date: LocalDate, style: FormatStyle, locale: Locale): String?
    public fun formatTime(time: LocalTime, style: FormatStyle, locale: Locale): String?
    public fun formatDateTime(
        dateTime: LocalDateTime,
        dateStyle: FormatStyle,
        timeStyle: FormatStyle,
        locale: Locale,
    ): String?
    public fun monthName(month: Int, style: TextStyle, locale: Locale): String?
    public fun dayOfWeekName(isoDayNumber: Int, style: TextStyle, locale: Locale): String?
}

public interface CurrencyFormatSource : LocaleDataSource {
    public fun formatCurrency(
        minorUnits: Long,
        currencyCode: String,
        locale: Locale,
        style: CurrencySymbolStyle,
        accounting: Boolean,
        cash: Boolean,
    ): String?
    public fun parseCurrency(text: String, currencyCode: String, locale: Locale): Long?
}
```

The pattern parser and the number formatter then live in `*-cldr`, not in core.
Core keeps `Locale`, tag parsing, the fallback chain (`dataLookupTags`, which
every implementation needs) and the interfaces. That is a few kilobytes.

`LocaleDataSource` carries the one thing every implementation must answer:

```kotlin
public interface LocaleDataSource {
    public val supportedLocales: Set<Locale>
}
```

which also replaces `Locale.availableLocales`. That list is generated data and
does not belong in core; it belongs to whichever source is installed.

## What stays on the enums

`types` holds the enum entries and the facts that are true regardless of
language:

- `Country`: `alpha2`, `alpha3`, `numericCode`
- `Currency`: `code`, `numericCode`, `defaultFractionDigits`, `minorUnitDigits`
- `Currency`: `cldrFractionDigits`, `cldrRoundingIncrement`,
  `cldrCashFractionDigits`, `cldrCashRoundingIncrement`
- `CurrencyAmount` in full except `format` and `parseFormatted`
- `CurrencySymbolStyle`, `FormatStyle`, `TextStyle`
- the country to currency map behind `Country.currencies` and `Country.currency`

The four `cldr*` fields are the arguable ones. They come from CLDR supplemental
data, so they carry a CLDR version, but they are per-currency integers rather
than per-locale text: about 900 numbers, a rounding error in size, and
`isoToCldrUnits` needs them without any locale in play. Recommendation: keep
them on the enum and stamp the `types` artifact with the CLDR version it was
generated from. Open question below if you disagree.

## Full mode

"Everything works, no configuration" is `types + *-cldr`, which is what the
umbrella artifacts bundle. Behaviour identical to today, same golden tests, same
numbers as the current probe run.

Later, `types + *-platform` is the same API with the system as the data source,
and where a target has no system data (Linux, Windows, wasm-wasi) the platform
source is composed with a bundled one. Kotlin cannot inherit from a type
parameter, so this is one small composer per interface rather than one generic
one:

```kotlin
public class FallbackCountryNames(
    private val primary: CountryNameSource,
    private val fallback: CountryNameSource,
) : CountryNameSource {
    override val supportedLocales: Set<Locale>
        get() = primary.supportedLocales + fallback.supportedLocales

    override fun countryName(alpha2: String, locale: Locale): String? =
        primary.countryName(alpha2, locale) ?: fallback.countryName(alpha2, locale)

    override fun countryCodeForName(name: String, locale: Locale): String? =
        primary.countryCodeForName(name, locale) ?: fallback.countryCodeForName(name, locale)
}
```

Four interfaces means four of these, each a handful of lines. Composition is
also how the plugin's configured fallback works, so they are worth building once
in phase 1.

There is a wrinkle to decide with them: a fallback keyed on "the primary
returned null" silently mixes sources, so a request for `ja` against an
English-only build quietly answers in English. That is usually what you want
from a fallback and occasionally hides a bug. Making `supportedLocales`
authoritative, and resolving the locale against it before dispatching, keeps the
behaviour explicit.

## The Gradle plugin

```kotlin
plugins {
    id("dev.carcara.kotlinx-locale") version "<matches the library>"
}

kotlinxLocale {
    locales("en", "pt-BR", "es-419")
    fallback("en")                  // answers requests for anything not generated

    country { names() }
    currency { names(); symbols(); formats() }
    datetime { patterns(); monthNames(); dayNames(); dayPeriods(false) }

    // optional, type-safe because the plugin classpath has kotlinx-locale-types
    currencies(Currency.BRL, Currency.USD, Currency.EUR)
}
```

It consumes `core + types` exactly as you described: `Locale` to express the
locale set, `Country` and `Currency` to express narrowing. Both artifacts must
stay free of anything that has no business on a build classpath, which is
another reason `types` carries no data module dependency.

It emits into `build/generated/kotlinx-locale/`, wires that into the target
source set, and produces objects implementing the same core interfaces:

```kotlin
public object GeneratedCountryNames : CountryNameSource { /* ... */ }
```

so the user writes `Country.BR.displayName(locale, GeneratedCountryNames)`, or
depends on nothing else and lets the plugin also emit the one-argument sugar.

### Where the plugin gets its data

Today `:codegen` clones the CLDR and ICU git repositories. That cannot happen in
a user's build. The pipeline already produces the right intermediate:
`Flattener` resolves inheritance and `encode()` emits one compact string per
locale, which is exactly what a filtered generator needs.

Publish that intermediate as `dev.carcara:kotlinx-locale-cldr-data`, versioned
by CLDR release, and publish the emitters as `kotlinx-locale-codegen`. The
plugin resolves both from Maven. No network beyond dependency resolution, no
clone, offline-friendly, and the output is pinned to a CLDR version the user can
see in their lock file.

### One generator, two consumers

The shipped `*-cldr` modules and the plugin must run the same emitters, or they
drift and "the split and definitions must be the same" stops being true. The
test that keeps them honest: run the plugin configured for every locale and
every feature, and assert the output is byte-identical to the checked-in
`*-cldr` sources. If that passes, there is one code path by construction.

### Narrowing: locales yes, entities carefully

Filtering locales is safe. Filtering countries and currencies is not, because an
app that only *displays* BRL may still receive an arbitrary currency code from a
payment API, and a generator that dropped it produces a silent wrong answer.

Recommendation: ship locale filtering first, and if entity narrowing lands, have
it narrow only the *name tables*, never the enums, so `Currency.forCode("JPY")`
keeps working and only its display name falls back.

## Verification

**A conformance suite, extracted and parameterized.** The existing ICU golden
tests become a shared test module that runs against any `LocaleDataSource`. Two
tiers: exact, for CLDR-backed sources (the shipped modules and anything the
plugin generates, which must match ICU byte for byte), and behavioural, for
platform sources, which can only be checked for shape, round-tripping and
non-emptiness because system data varies by OS version. Without this the
platform module is unverifiable, so it is worth building before it is needed.

**Size budgets in CI.** `scripts/js-size.mjs --json` already emits byte counts.
Add a ceiling per artifact so an accidental dependency from `types` into a data
module fails the build instead of quietly costing 400 KB.

**ABI.** `main` now runs `checkKotlinAbi`. This refactor rewrites the dumps
wholesale, so each phase should end with one deliberate `updateKotlinAbi` and a
reviewed diff, not a running battle with the check.

## Open decisions

1. Extension over member for `displayName`, `symbol`, `format` and
   `parseFormatted`. Recommended, and everything above assumes it. If members
   must stay, the only route is the runtime registry (option A) and its costs.
2. Do the four `cldr*` integer fields stay on `Currency`, or move behind a
   source?
3. One `types` artifact, or per-domain type artifacts? Recommendation is one,
   on the evidence that the split is worth about 7 KB.
4. Do the umbrella artifacts keep today's coordinates? Recommendation is yes.
5. On a miss, does a source return null, consult a configured fallback locale,
   or throw? The interfaces above return null and let a composer decide, which
   makes fallback a plugin config value rather than a library policy.
6. Entity narrowing in the plugin: never, name-tables-only, or full?
7. Does `Locale.availableLocales` move to `LocaleDataSource.supportedLocales`,
   or disappear from the public API?
8. Artifact names. `kotlinx-locale-country-cldr` is explicit but long;
   `kotlinx-locale-cldr-country` groups better in a repository listing.

## Phases

Each phase ends green: all tests pass, one ABI dump update, one probe run
recorded.

**Phase 0.** Settle the open decisions. No code.

**Phase 1. Core interfaces, no artifact changes.** Introduce
`LocaleDataSource` and the four operation-shaped interfaces inside the existing
modules. Move the pattern parser and number formatter behind them. Add the
fallback composer. The public API does not move yet, so the golden tests are the
proof that nothing changed. Fold in the `const val` to `val` change in the
emitters here, which the probe measured at 16% off the minified bundle overall
and 48% off datetime.

**Phase 2. Extract `types`.** Move the enums, `CurrencyAmount` and the style
enums into `kotlinx-locale-types`. Turn the five text-reaching entry points into
extensions. The current artifacts become umbrellas that bind the CLDR defaults.
Existing users see an import change and nothing else.

**Phase 3. Split the data artifacts.** `*-cldr` per domain. Add size budgets to
CI. This is the phase where the numbers in the table above become real for JVM,
Android and Native rather than only for JS.

**Phase 4. Conformance suite.** Extract the golden tests, parameterize over a
source, run the shipped implementations through it.

**Phase 5. Publishable generation.** `kotlinx-locale-cldr-data` and
`kotlinx-locale-codegen` artifacts, then the Gradle plugin, then the
byte-identity test that pins them together.

**Phase 6, later. Platform sources.** JS over `Intl`, JVM and Android over
`java.util.Locale` and ICU4J, Apple over `NSLocale`. Composed with a bundled
fallback on targets with no system data.

Phases 1 through 3 are the ones that pay for themselves immediately. Phase 5 is
the largest single piece of new work. Phase 6 is cheap once 1 through 4 exist,
which is the point of shaping the interfaces around operations now rather than
discovering the constraint later.
