# Plan: splitting the API from the data

A proposal for restructuring the library into pure API interfaces, pure type
layers and swappable data implementations, one set per domain, plus a Gradle
plugin that generates a narrowed implementation from a locale set the user
declares.

Nothing here is committed to. Read it, mark what is wrong, and the phases at the
end can be re-cut around whatever changes.

## What the measurements say

Measured by the probes in `tools/`, gzipped over the minified Kotlin/JS bundle.
The right-hand column is what phase 3 committed as a ceiling:

| scenario | gzip | budget |
| --- | ---: | ---: |
| `Locale` alone, the floor | 13.9 KB | 18 KB |
| country codes and lookups | 14.6 KB | 20 KB |
| currency codes, unit maths and `CurrencyAmount` | 23.3 KB | 30 KB |
| datetime, full | 112.6 KB | 130 KB |
| currency, full | 329.0 KB | 370 KB |
| country, full | 416.7 KB | 460 KB |
| everything | 823.6 KB | 900 KB |

The earlier figures from the PR 11 probe were 24.4 KB, 33.3 KB, 115.8 KB,
748.5 KB, 427.8 KB and 845.1 KB for the same rows. The codes-only rows came
down because `-core` no longer drags a data module, and the currency row came
down a lot because a currency-only consumer no longer pays for country names.

Three facts drive the whole design.

**The code is already small.** Country is 81 KB of code and 1125 KB of text.
Strip the text and it is lighter than kotlinx-datetime. There is no size problem
in what we wrote, only in what we ship alongside it.

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

Layers repeated per domain, with one shared root.

```
kotlinx-locale-core                  Locale, tag parsing, fallback chain, LocaleDataSource
kotlinx-locale-types                 LocaleRef, one generated enum per language

kotlinx-locale-country-core          CountryNameSource, lookups, typed overloads
kotlinx-locale-country-types         Country enum
kotlinx-locale-country-cldr          CldrCountry + payloads + convenience extensions
kotlinx-locale-country-platform      later

kotlinx-locale-currency-core         CurrencyNameSource, CurrencyFormatSource, CurrencyAmount,
                                     lookups, unit math, typed overloads
kotlinx-locale-currency-types        Currency enum, country to currency map
kotlinx-locale-currency-cldr         CldrCurrency + payloads + convenience extensions
kotlinx-locale-currency-platform     later

kotlinx-locale-datetime-core         DateTimeFormatSource, FormatStyle, TextStyle
kotlinx-locale-datetime-cldr         CldrDateTime + payloads + convenience extensions
kotlinx-locale-datetime-platform     later
```

The naming rule is mechanical: `kotlinx-locale[-<domain>]-<layer>`, with the
domain segment omitted for the locale domain itself because it is the root. That
costs renaming today's `kotlinx-locale` to `kotlinx-locale-core`, which is free
at 0.1.0-SNAPSHOT and buys a listing where every artifact says what layer it is.

Ten published modules now, thirteen once platform lands. Within a domain,
`-core` depends on `-types` for the enum, and `-cldr` depends on `-core` alone.
See the note below on where hand-written code that mentions the enums belongs.

Across domains: `currency-types` depends on `country-types` for the country to
currency map, and `currency-core` on `country-core`. Nothing else crosses.

### Which layers are swappable, and what that costs

The point of the layering is that everything above `-core` can come from
somewhere other than Maven. `-core` is the fixed contract; `-types`, `-cldr` and
`-platform` are things that satisfy it:

| layer | hand written | who can supply it |
| --- | --- | --- |
| `-core` | yes | us, only |
| `-types` | no, fully generated | us, or the Gradle plugin narrowed to a config |
| `-cldr` | no, fully generated | us, or the Gradle plugin narrowed to a config |
| `-platform` | yes | us, per target |

A consumer depends on one supplier per swappable layer, never two. Taking the
plugin's `-types` means not taking ours, the same way taking `-platform` means
not taking `-cldr`.

This is why the `-core` *interfaces* stay keyed by string codes rather than by
`Country` and `Currency`. A narrowed `-types` must not change the interface, or
a `-cldr` compiled against the full enum could not satisfy it. String keys make
the contract independent of whichever entry set is in play, even though `-core`
also holds typed overloads over those same interfaces.

### Where hand-written, enum-dependent code lives

`CurrencyAmount`, the `forCode` and `forNumericCode` lookups, `isoToCldrUnits`
and the typed overloads are hand-written, and they mention `Currency`. That
combination needs a home, and the question only arises because decision 4 lets
the plugin narrow `-types`:

- **If the plugin narrows only `-cldr`**, `-types` is free to hold hand-written
  code and this whole question disappears. Three layers per domain, no
  `-model`, nothing further to decide.
- **If the plugin may narrow `-types`**, then `-types` has to be nothing but
  generator output, because any hand-written line in it would have to live
  inside the emitter as a verbatim copy and the copy would drift. The
  hand-written code then goes either into `-core` or into a layer of its own.

For that second branch, `-core` is the simpler answer and is what this plan now
assumes. `currency-core` holds the interfaces, `CurrencySymbolStyle`,
`CurrencyAmount`, the lookups and the typed overloads, and it depends on
`currency-types` for the enum. `-types` needs nothing back, so there is no
cycle.

An earlier draft put this in a separate `-model` layer on the grounds that
`-core` must not know the enums exist. That reason does not survive scrutiny.
The property actually worth protecting is that any `-cldr` links against any
`-types`, and what protects it is not the module boundary but a rule:

**hand-written code may reference the enum type and its members, never a
specific entry.** No `Currency.USD` outside generated data. A build that
narrowed `USD` away would otherwise fail to link. The current sources already
obey it (the only occurrence in the tree is inside a doc comment) and a test can
keep them honest.

With that rule in force, merging into `-core` costs one thing and one thing
only: a module implementing a source inherits a dependency on the enum it may
not use. That is roughly 9 KB gzipped for currency, and in practice anyone
implementing `CurrencyNameSource` is also consuming `Currency`, so the dependency
is not wasted. Two fewer modules is worth more than the purity.

Keep `-model` as a separate layer only if third parties outside this repository
are expected to implement sources against a minimal contract, or if "core is
pure interfaces" is a rule you want to hold regardless. Both are legitimate, and
the layout above changes by two modules either way.

### The risk to check either way

Whichever home it gets, this code ships precompiled from Maven and binds to
whichever `-types` is present, which for a narrowed build is generated source in
the user's own project rather than our artifact. On the JVM that is ordinary
classpath resolution. For Kotlin/Native and JS klibs, a precompiled klib records
the module its dependencies came from, and whether it will accept a same-package,
same-FQN substitute is something to prove with a spike before phase 2 rather
than assume.

If the spike fails, the fallback is to keep the hand-written portion as a
template resource inside the codegen artifact, so the shipped module is
generated from the same template the plugin uses and there is still exactly one
copy.

### What splitting the enum does to it

Either way, today's enums split in two. `-types` gets the enum with its
generated constructor properties and nothing else, and every behaviour that is a
member today becomes an extension:

```kotlin
// currency-types, generated
public enum class Currency(
    public val numericCode: Int,
    public val defaultFractionDigits: Int,
    public val cldrFractionDigits: Int,
    /* ... */
) { AED(784, 2, 2, /* ... */), AFN(971, 2, 0, /* ... */), /* ... */ }

// currency-core, hand written
public val Currency.code: String get() = name
public val Currency.minorUnitDigits: Int get() = defaultFractionDigits
public fun Currency.isoToCldrUnits(minorUnits: Long): Long { /* ... */ }
public fun Currency.Companion.forCode(code: String): Currency { /* ... */ }
```

The emitter then only ever writes data, never logic, which is the property that
keeps the shipped module and the plugin output honest.

`kotlinx-locale-datetime-types` does not appear because datetime has no
generated enum. Its only type candidates,
`FormatStyle` and `TextStyle`, are not generated enum lists. They are seven
hand-written constants that appear in the `DateTimeFormatSource` signatures, so
they belong in `datetime-core` next to the interface that uses them (decision 3).

### Why per domain, all the way down

I first proposed one merged `types` artifact on the grounds that splitting it
was worth about 7 KB. That number was measured against the 748 KB full build,
where it is noise. Measured against the build the split exists to enable, it is
not:

| minimal build | today | merged types would add |
| --- | ---: | ---: |
| Kotlin/JS floor plus `Locale` | 17.4 KB | |
| plus the `Country` enum | 24.4 KB | |
| plus the `Currency` enum and `CurrencyAmount` | 33.3 KB | |

A country-only consumer would carry roughly 9 KB of unused `Currency`, and a
datetime-only consumer would carry roughly 16 KB of unused `Country` and
`Currency`, against a 17 KB floor. In core mode, where the entire point is being
small, that is 30 to 60 percent of the budget spent on enums nobody called.

The same argument settles the cores, and more sharply. A merged core would have
to declare `DateTimeFormatSource`, whose signatures mention `LocalDate`,
`LocalTime` and `LocalDateTime`, which drags `kotlinx-datetime` into every
consumer. That is 23.7 KB gzipped onto a 24.4 KB country build. Per-domain cores
confine the kotlinx-datetime dependency to the one domain that needs it.

### No umbrella artifacts and no defaults

There is no `kotlinx-locale-country` aggregating everything, and no
implementation-bound convenience overload. A consumer declares the layers it
wants:

```kotlin
implementation("dev.carcara:kotlinx-locale-country-core:$version")
implementation("dev.carcara:kotlinx-locale-country-cldr:$version")
```

and passes the source at the call site. Which implementation answers is visible
in the source file, not inferred from the dependency graph. The cost is a longer
dependency block and a longer call, and the benefit is that there is exactly one
way to read any given line.

This also removes the failure mode where two implementations on one classpath
produce colliding imports, since nothing is imported implicitly.

## How a call reaches its data

Settled: **generated types carry only their per-entry data as constructor
properties, and everything else about them is an extension, in every layer.**
The implementation module declares the convenience extension over its own
source, in its own package.

```kotlin
// country-types (generated)     package dev.carcara.kotlinx.locale.country
public enum class Country(public val alpha3: String, public val numericCode: Int) {
    AD("AND", 20), AE("ARE", 784), /* ... */ ;
    public companion object
}

// country-core                  package dev.carcara.kotlinx.locale.country
public val Country.alpha2: String get() = name
public fun Country.Companion.forAlpha2(code: String): Country = /* ... */

public interface CountryNameSource : LocaleDataSource {
    public fun countryNameOrNull(alpha2: String, locale: Locale): String?
}

public fun CountryNameSource.displayName(country: Country, locale: Locale): String =
    countryNameOrNull(country.alpha2, locale) ?: country.alpha2

// country-cldr                  package dev.carcara.kotlinx.locale.country.cldr
public object CldrCountry : CountryNameSource { /* generated tables */ }

public fun Country.displayName(locale: Locale): String = CldrCountry.displayName(this, locale)
```

The consumer writes what they write today:

```kotlin
import dev.carcara.kotlinx.locale.country.*
import dev.carcara.kotlinx.locale.country.cldr.*

Country.BR.alpha3                 // types
Country.forAlpha2("br")           // core
Country.BR.displayName(locale)    // cldr
```

Nothing at the call site says which layer answered, which is the point. Swapping
CLDR for the platform sources is a dependency change plus replacing `.cldr` with
`.platform` in one import.

Three properties fall out of the rule:

- **Layer moves are free.** Members and extensions are called identically, so a
  declaration can move between artifacts later without touching a call site.
  Packaging becomes a deployment decision instead of an API one.
- **The emitter only ever writes data**, never logic, which is what keeps the
  shipped `-cldr` module and the plugin output from drifting.
- **The explicit form always exists.** Every convenience extension is one line
  over a public source object, so composition and test fakes work without any
  special support.

### Implementations get their own package

`-types` and `-core` share the base package. Implementation modules do not, and
this was measured rather than assumed.

A spike put `country-cldr` and `country-platform` both declaring
`Country.displayName(Locale)` in `dev.carcara.kotlinx.locale.country`, with an
app depending on both. It **compiled with no error and no warning, and resolved
to whichever came first on the classpath**. Not an ambiguity error, a silent
wrong answer. Distinct packages remove the hazard and let both coexist, which
composition needs anyway.

Verified alongside it: split packages across modules do work. `-types` and
`-core` contributing to one package, including companion extensions, compiles on
JVM, JS and Native. That is what makes the single base import above possible.

### The rejected alternatives, for the record

**A runtime registry** would let `displayName` stay a member of the enum: a
global provider that a data module installs on load. It cannot work. On JS, Wasm
and Native there is no hook that runs code for a linked but unreferenced module,
because that is precisely what dead code elimination deletes. `ServiceLoader`
covers JVM and Android only, so the API would silently work on one half of the
targets and silently return nothing on the other. Auto-registration and tree
shaking are the same trade-off seen from two sides: the reason a country-only
build is 24 KB and not 428 KB is that unreferenced code disappears.

**A context parameter** on the source works and was verified on Kotlin 2.4.0
with no compiler flag, but it requires every call site to sit inside a `with`
scope or propagate a context, which is ceremony the extension rule avoids
entirely.

**Passing the source at every call** (`CldrCountry.displayName(Country.BR,
locale)`) remains available and is what composition and test fakes use. It is
just not the shape ordinary code has to write.

## The interfaces have to be operation-shaped

This is the part that decides whether the platform layer is possible at all, and
it costs nothing to honour now versus a rewrite later.

The tempting shape mirrors our tables:

```kotlin
public fun dateFormatPattern(style: FormatStyle, locale: Locale): String?  // no
```

No platform can implement that. `Intl.DateTimeFormat` does not hand out CLDR
patterns, it formats. `NSDateFormatter` will derive a pattern from a template
but not the localized standard patterns. So the interface sits at the level of
the operation the user asked for, not the table CLDR happens to store:

```kotlin
// datetime-core
public enum class FormatStyle { FULL, LONG, MEDIUM, SHORT }
public enum class TextStyle { FULL, ABBREVIATED, NARROW }

public interface DateTimeFormatSource : LocaleDataSource {
    public fun formatDateOrNull(date: LocalDate, style: FormatStyle, locale: Locale): String?
    public fun formatTimeOrNull(time: LocalTime, style: FormatStyle, locale: Locale): String?
    public fun formatDateTimeOrNull(
        dateTime: LocalDateTime,
        dateStyle: FormatStyle,
        timeStyle: FormatStyle,
        locale: Locale,
    ): String?
    public fun monthNameOrNull(month: Int, style: TextStyle, locale: Locale): String?
    public fun dayOfWeekNameOrNull(isoDayNumber: Int, style: TextStyle, locale: Locale): String?
}

// currency-core
public interface CurrencyFormatSource : LocaleDataSource {
    public fun formatOrNull(
        minorUnits: Long,
        currencyCode: String,
        locale: Locale,
        style: CurrencySymbolStyle,
        accounting: Boolean,
        cash: Boolean,
    ): String?
    public fun parseToMinorUnitsOrNull(text: String, currencyCode: String, locale: Locale): Long?
}
```

The pattern parser and the number formatter live in `*-cldr`, not in core. The
shared root keeps `Locale`, tag parsing, the fallback chain (`dataLookupTags`,
which every implementation needs) and the one thing every source must answer:

```kotlin
// kotlinx-locale-core
public interface LocaleDataSource {
    public val supportedLocales: Set<Locale>
}
```

which also replaces `Locale.availableLocales`. That list is generated data and
does not belong in the shared root; it belongs to whichever source is installed.

`CurrencySymbolStyle` is referenced by `CurrencyFormatSource`, so by the same
rule that puts `FormatStyle` in `datetime-core` it belongs in `currency-core`
rather than `currency-types`.

## What lives in each layer

Applying the extension rule to today's API, domain by domain.

### country

`country-types`, generated, package `...locale.country`

- `Country` with `alpha3` and `numericCode` as constructor properties
- an empty `public companion object` so the lookups can attach to it

`country-core`, hand written, same package

- `val Country.alpha2 get() = name`
- `Country.Companion.forAlpha2`, `forAlpha3`, `forNumericCode` and their
  `OrNull` forms
- `Country.Companion.forLocaleOrNull`, which reads the region subtag and needs
  no data
- `CountryNameSource`, `CountryNameSource.displayName(Country, Locale)` and
  `CountryNameSource.countryForDisplayNameOrNull(String, Locale)`

The reverse lookup is an extension over the one-way interface rather than a
second interface method. A partial `countryCodeForName` cannot express "no name,
so it fell back to the code", which is the semantics the current API has, and
every platform source would have to reimplement that rule to stay compatible.
Scanning the entries costs what it costs today.

`country-cldr`, generated tables plus a hand-written binding, package
`...locale.country.cldr`

- `CldrCountry : CountryNameSource`
- `Country.displayName(locale)` and `Country.Companion.forDisplayNameOrNull`

### currency

`currency-types`, generated, package `...locale.currency`

- `Currency` with `numericCode`, `defaultFractionDigits`, `cldrFractionDigits`,
  `cldrRoundingIncrement`, `cldrCashFractionDigits` and
  `cldrCashRoundingIncrement` as constructor properties, plus a companion
- the country to currency map, which is generated CLDR supplemental data

`currency-core`, hand written, same package

- `val Currency.code get() = name` and `val Currency.minorUnitDigits`
- `Currency.isoToCldrUnits`, `Currency.cldrToIsoUnits`
- `Currency.Companion.forCode`, `forNumericCode`, `forCountryOrNull`,
  `forLocaleOrNull` and the `OrNull` forms
- `val Country.currencies` and `val Country.currency` over the generated map
- `CurrencyAmount` in full except `format` and `parseFormatted`, as a
  hand-written class with ordinary members
- `CurrencySymbolStyle`, referenced by the interface
- `CurrencyNameSource`, `CurrencyFormatSource` and the typed operations over
  them

`currency-cldr`, package `...locale.currency.cldr`

- `CldrCurrency : CurrencyNameSource, CurrencyFormatSource`
- `Currency.symbol`, `Currency.displayName`, `CurrencyAmount.format` and
  `CurrencyAmount.Companion.parseFormatted`

### datetime

`datetime-core`, hand written, package `...locale.datetime`

- `FormatStyle` and `TextStyle`, which are not generated
- `DateTimeFormatSource` and the typed operations over it

`datetime-cldr`, package `...locale.datetime.cldr`

- `CldrDateTime : DateTimeFormatSource`, holding the pattern parser and the
  formatter
- `LocalDate.format`, `LocalTime.format`, both `LocalDateTime.format` overloads,
  `Month.displayName` and `DayOfWeek.displayName`

There is no `datetime-types` because datetime has no generated enum.

### The arguable placements

The four `cldr*` integer fields and the country to currency map sit in
`-types` because they are per-entry generated data, which is what `-types` is
for. They come from CLDR supplemental data, so they carry a CLDR version, but
they are structural rather than linguistic: roughly 900 numbers and 250
mappings, a rounding error in size, and `isoToCldrUnits` needs them with no
locale in play. They stay there, and the `-types` artifacts are stamped with the
CLDR version they were generated from (decision 6).

Note that under the extension rule this placement is cheap to revisit. Moving
any of them later changes no call site.

## A generated locale catalog

The catalog is a generated list of the 1121 locale *identifiers* CLDR has data
for, expressed as Kotlin declarations instead of raw strings. It carries no
translations and no CLDR payloads. It is to locales what `Country` is to
countries: the type-safe way to name one.

Today, naming a locale means writing a string:

```kotlin
Locale.forLanguageTag("pt-BR")   // fine
Locale.forLanguageTag("pt-BRA")  // compiles, throws at runtime
Locale.forLanguageTag("pt_BR")   // compiles, throws at runtime
```

With the catalog:

```kotlin
Locale.forLanguageTag(Pt.BR.tag)  // cannot be misspelled, autocompletes
```

The reason it earns its own artifact is the Gradle plugin. Its configuration is
a locale set, and a typo there is worse than at runtime: the build succeeds and
silently generates data for one locale fewer than intended. Passing a
`LocaleRef` makes that unrepresentable, and it is also how a developer discovers
that `es-419` exists at all without going to read the CLDR release.

It is the compile-time counterpart of `LocaleDataSource.supportedLocales`, which
answers the same question at runtime for whichever source is installed. Neither
replaces the other: dynamic tags still go through `Locale.forLanguageTag`, which
stays the zero-cost path.

The structure is derivable from what codegen already has. A CLDR locale ID is
`language[-script][-region][-variant]`, and `Flattener.localeIds` is the full
list, so the tree falls out of parsing the identifiers. Measured over the 1121
ids we currently ship:

| | |
| --- | ---: |
| distinct languages | 322 |
| ids that are a bare language | 322 |
| ids with two subtags | 691 |
| ids with three subtags | 108 |
| languages carrying a script | 33 |
| ids with a variant | 4 |
| median ids per language | 2 |
| largest language (`en`) | 130 |

Two levels are enough. Nest by language and flatten whatever follows inside it,
so the 33 script-carrying languages do not force a third level and every
reference is `Language.Rest`:

```kotlin
Pt.BR          // pt-BR
Zh.HANS_CN     // zh-Hans-CN
Ca.ES_VALENCIA // ca-ES-valencia
Sr.CYRL_BA     // sr-Cyrl-BA
```

The bare language needs a member name of its own, since `Pt` is the container.
`Pt.TAG` or `Pt.BASE` rather than overloading the object.

Three subtags are not valid Kotlin identifiers: `001`, `150` and `419`. Rather
than backticking them, which produces JVM field names Java callers cannot
reference, name them from CLDR's own English region names, which codegen already
parses: `Ar.WORLD` for `ar-001`, `En.EUROPE` for `en-150`, `Es.LATIN_AMERICA`
for `es-419`.

Note the nesting must follow the *identifier* structure, not the CLDR parent
chain. Those differ: `en-150`'s parent is `en-001`, and `zh-Hant-MO`'s is
`zh-Hant-HK`. The parent chain stays runtime data inside `*-cldr`, where it
already lives.

### Two shapes, and where the catalog lives

**Enum per language, implementing a shared interface.** The whole generated
artifact is 322 files shaped like this one:

```kotlin
// kotlinx-locale-core, hand-written
public interface LocaleRef {
    public val tag: String
}

// kotlinx-locale-types, generated: one enum per language, 322 of them
public enum class Pt(override val tag: String) : LocaleRef {
    BASE("pt"),
    AO("pt-AO"),
    BR("pt-BR"),
    CH("pt-CH"),
    CV("pt-CV"),
    GQ("pt-GQ"),
    GW("pt-GW"),
    LU("pt-LU"),
    MO("pt-MO"),
    MZ("pt-MZ"),
    PT("pt-PT"),
    ST("pt-ST"),
    TL("pt-TL"),
    ;
}
```

Real type safety, so the plugin DSL can take `LocaleRef` and reject anything
else, and `Pt.entries` gives every Portuguese locale. Costs 322 enum classes and
1121 instances, though only the language you touch is loaded.

**Object per language, `const val` members.**

```kotlin
public object Pt {
    public const val BASE: String = "pt"
    public const val BR: String = "pt-BR"
    /* ... */
}
```

`const val` is inlined at every use site, so at runtime `Pt.BR` is the string
`"pt-BR"` and the object is never loaded. Zero cost, but the DSL parameter is
`String`, so a typo is caught by the plugin validating its configuration rather
than by the compiler.

Recommendation: the enum form, in `kotlinx-locale-types`, depended on by the
Gradle plugin and by anyone who wants it in app code. Keeping it out of
`kotlinx-locale-core` means the runtime cost is opt-in and
`Locale.forLanguageTag` stays the zero-cost path for code that builds tags
dynamically.

The catalog is the cleanest case for a plugin-generated `-types`: it is one
hundred percent generated with no hand-written behaviour attached, so a build
configured for three locales gets a catalog with three entries and `Ja.BASE`
simply does not compile. The plugin DSL should accept `LocaleRef` and `String`
both, and validate strings at configuration time.

## Full mode

"Everything works" is `*-core + *-types + *-cldr` for each domain you use.
Behaviour identical to today apart from the call shape, same golden tests, same
numbers as the current probe run.

Later, `*-core + *-types + *-platform` is the same API with the system as the
data source. Where a target has no system data (Linux, Windows, wasm-wasi) a
platform source is composed with a bundled one. Kotlin cannot inherit from a
type parameter, so this is one small composer per interface rather than one
generic one:

```kotlin
public class FallbackCountryNames(
    private val primary: CountryNameSource,
    private val fallback: CountryNameSource,
) : CountryNameSource {
    override val supportedLocales: Set<Locale>
        get() = primary.supportedLocales + fallback.supportedLocales

    override fun countryNameOrNull(alpha2: String, locale: Locale): String? =
        primary.countryNameOrNull(alpha2, locale) ?: fallback.countryNameOrNull(alpha2, locale)
}
```

Four interfaces means four of these, each a handful of lines. Composition is
also how the plugin's configured fallback works, so they are worth building once
in phase 1, in the domain `*-core` modules.

There is a wrinkle to decide with them. A fallback keyed on "the primary
returned null" silently mixes sources, so a request for `ja` against an
English-only build quietly answers in English. That is usually what you want and
occasionally hides a bug. Making `supportedLocales` authoritative, and resolving
the locale against it before dispatching, keeps the behaviour explicit.

## The Gradle plugin

```kotlin
plugins {
    id("dev.carcara.kotlinx-locale") version "<matches the library>"
}

kotlinxLocale {
    locales(En.US, Pt.BR, Es.LATIN_AMERICA)
    fallback(En.US)                 // answers requests for anything not generated

    country { names() }
    currency { names(); symbols(); formats() }
    datetime { patterns(); monthNames(); dayNames(); dayPeriods(false) }

    // optional, type-safe because the plugin classpath has the types artifacts
    currencies(Currency.BRL, Currency.USD, Currency.EUR)
}
```

It consumes `*-core + *-types` exactly as you described: `LocaleRef`
to express the locale set, `Country` and `Currency` to express narrowing. Those
layers must stay free of anything with no business on a build classpath, which
is another reason `*-types` carries no dependency on a data module.

`locales("pt-BR")` stays available for tags built dynamically or read from a
file, validated at configuration time against the catalog so a typo fails the
build rather than silently generating nothing.

It emits into `build/generated/kotlinx-locale/`, wires that into the target
source set, and produces objects implementing the same core interfaces:

```kotlin
public object GeneratedCountryNames : CountryNameSource { /* ... */ }
```

plus the same convenience extensions `-cldr` ships, in its own package. Swapping
a filtered build for the full one is a dependency change and an import change,
with no edit to any call site.

The generated object name should be configurable, since a project may want more
than one (a filtered default and a full one behind a lazy load).

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

Both artifacts exist. The bundle is 2.8 MB of text: a header, the country and
currency entry lists, the country-to-currency map, and the four payload maps
keyed by canonical tag. `LocaleDataBundle.narrowTo(tags)` does the filtering,
and it keeps each locale's ancestors, which is not optional: a country-name
record holds only what that locale's own CLDR file declares and points at its
parent for the rest, so a build that kept `pt-BR` and dropped `pt` would resolve
almost nothing. `es-AR` is the case that catches a naive implementation, because
its parent is `es-419` rather than plain `es`.

### One generator, two consumers

The shipped `*-cldr` modules and the plugin must run the same emitters, or they
drift and "the split and definitions must be the same" stops being true.

This is now built and pinned. `generateSources(bundle, roots, packages)` in
`kotlinx-locale-codegen` is the single entry point; `:codegen` calls it with the
shipped roots after extracting the bundle from CLDR, and the plugin calls it
with roots under `build/generated/`. They differ in where output lands and which
package the registries take, never in what is written.

`BundleRoundTripTest` is the proof: it reads the published bundle, regenerates
every shipped source from it with no CLDR clone in sight, and compares byte for
byte. A failure means either the bundle is stale, or it cannot carry something
the sources need — which is the more interesting case, because the plugin would
then generate it wrong and nothing else would notice.

### Narrowing: locales yes, entities carefully

Filtering locales is safe. Filtering countries and currencies is not, because an
app that only *displays* BRL may still receive an arbitrary currency code from a
payment API, and a generator that dropped it produces a silent wrong answer.

Recommendation: ship locale filtering first, and if entity narrowing lands, have
it narrow only the *name tables*, never the enums, so `Currency.forCode("JPY")`
keeps working and only its display name falls back.

## Verification

**A conformance suite, extracted and parameterized.** `kotlinx-locale-conformance`
holds the ICU fixtures and runs any source through them. Two tiers: exact, for
CLDR-backed sources (the shipped modules and anything the plugin generates,
which must match ICU byte for byte), and behavioural, for platform sources,
which can only be checked for shape, round-tripping and non-emptiness because
system data varies by OS version. Without this the platform layer is
unverifiable, so it is worth building before it is needed.

Two things the extraction settled that were not obvious from the outside:

- **The currency round trip is not the identity, and cannot be.** CLDR formats
  some currencies with fewer fraction digits than ISO gives them, so HUF prints
  `0.01` as `HUF 0` and there is no cent left to read back. What the suite
  asserts is the amount taken through CLDR's scale, which is what `format`
  documents it prints. A plain round-trip assertion looks right and fails on
  real data.
- **Not everything can be parameterized over a source.** The datetime fixtures
  hold CLDR's *patterns*, and the interface deliberately does not expose
  patterns, because no platform could implement one that did. So the suite
  compares month and weekday names against ICU and checks formatted output
  behaviourally, and the pattern tables stay cross-checked inside
  `datetime-cldr`, which can reach them. The same split applies to currency's
  number tables.

The suite is tested against a fake source that gets it wrong, so that "the
suite passes" means something.

**Size budgets in CI.** `scripts/js-size.mjs --json` already emits byte counts.
Add a ceiling per artifact so an accidental dependency from a types module into
a data module fails the build instead of quietly costing 400 KB. The per-domain
split makes these budgets meaningful, since each artifact can now be measured
alone.

**ABI.** `main` now runs `checkKotlinAbi`. This refactor rewrites the dumps
wholesale, so each phase should end with one deliberate `updateKotlinAbi` and a
reviewed diff, not a running battle with the check.

## The resulting API surface

`API-NEXT.md` works the change through `API.md` entry by entry, for a consumer
taking core, types and CLDR. The short version: twenty-six public entry points,
twenty-five of them unchanged, and every output table in `API.md` stays byte for
byte identical because it is the same data through the same formatter.

## Decisions

Phase 0 closed all twelve. Everything below is what the later phases assume.

1. **Implementations live in `...<domain>.cldr`.** The import reads in the order
   you think: pick the domain, then pick the backend. `...cldr.country` groups
   one backend across domains, which helps whoever is auditing that backend and
   nobody else. Migrating to a different backend is a search and replace either
   way.
2. **The `locale: Locale = Locale.current` defaults stay exactly where they are
   today.** Country and currency keep them, datetime keeps requiring an explicit
   locale, because that is what those APIs do now and the promise of this
   refactor is that call sites do not move. The implementation module declares
   the extension, so it can also supply the default. "Explicit everything" is a
   separate argument and it can be had later without a structural change.
3. **`FormatStyle`, `TextStyle` and `CurrencySymbolStyle` stay in their
   `-core`.** They appear in the source signatures, so a `-cldr` cannot compile
   without them; putting them in `-types` would make every `-core` depend on a
   `-types` to declare its own interfaces. They are also hand-written constants,
   and `-types` is generator output only (decision 4). There is no
   `datetime-types`.
4. **The plugin may narrow `-types`.** Being able to say that `Currency.JPY`
   does not exist in this build is the point of the layer, and it is what makes
   the locale catalog worth generating at all. The cost is that `-types` must be
   nothing but emitter output.
5. **The hand-written, enum-dependent code goes into `-core`.** `CurrencyAmount`,
   the lookups, the unit math and the typed overloads sit next to the interfaces.
   What protects "any `-cldr` links against any `-types`" is the rule that
   hand-written code never names a specific entry, not a module boundary. A test
   enforces the rule.
6. **The four `cldr*` integer fields and the country to currency map stay in
   `-types`.** They are per-entry generated data, `isoToCldrUnits` needs them
   with no locale in play, and they are roughly 900 numbers and 250 mappings.
   The `-types` artifacts carry the CLDR version they were generated from.
7. **A source returns null on a miss.** Fallback is a composer's job, so it stays
   a configuration value rather than a library policy. `-core` layers the total
   operation over the partial interface with the fallback the library already
   documents: the ISO code for country and currency, ISO 8601 for datetime.
8. **Entity narrowing, when it lands, narrows name tables only.** An app that
   only displays BRL can still be handed an arbitrary code by a payment API, so
   `Currency.forCode("JPY")` keeps working and only its display name degrades.
   Locale filtering ships first regardless.
9. **`Locale.availableLocales` becomes `LocaleDataSource.supportedLocales`.** It
   was a property of a data set masquerading as a property of the type, and it
   stops being true the moment a build narrows its locales. No new table is
   needed: each source already keys its registry by tag.
10. **The catalog is an enum per language implementing `LocaleRef`.** Its stated
    use is plugin configuration, where a `const val` gives the DSL a `String`
    parameter and pushes typo detection from the compiler to a validation pass.
    Only the language you touch is loaded.
11. **The catalog nests two levels.** `Zh.HANS_CN`, not `Zh.Hans.CN`. Only 33 of
    322 languages carry a script, and a uniform `Language.Rest` is worth more
    than saving four characters in a tenth of the cases. It lives in
    `...locale.catalog` rather than the base package, because 322 short names
    like `Pt` and `As` have no business arriving through a star import of
    `dev.carcara.kotlinx.locale`.
12. **Artifacts are `kotlinx-locale[-<domain>]-<layer>`.** Domain first. A
    repository listing that sorts all of country together matches how the
    dependency block is written, and the layer suffix is what you read to know
    what an artifact is.

## Phases

Each phase ends green: all tests pass, one ABI dump update, one probe run
recorded.

**Phase 0. Done.** The decisions above are settled. No code.

**Phase 1. Interfaces, inside the existing modules.** Introduce
`LocaleDataSource` and the operation-shaped source interfaces, move the pattern
parser and number formatter behind them, add the fallback composers. No artifact
changes yet, so the golden tests are the proof that nothing changed. Fold in the
`const val` to `val` change in the emitters here, which the probe measured at
16% off the minified bundle overall and 48% off datetime.

**Phase 2. Split each domain into its layers.** Eleven modules, the
call shape moves to source-as-receiver, and the old aggregate artifacts stop
being published. This is the breaking phase and it should land as one change
rather than a drip, so users migrate once. The catalog is generated here too,
since the plugin in phase 5 depends on it and it is a small addition to the
emitters that already have `localeIds`.

**Phase 3. Size budgets.** Per-artifact gzip ceilings in CI. This is the phase
where the table at the top becomes real for JVM, Android and Native rather than
only for JS.

**Phase 4. Conformance suite.** Extract the golden tests, parameterize over a
source, run the shipped implementations through it.

**Phase 5. Publishable generation.** `kotlinx-locale-cldr-data` and
`kotlinx-locale-codegen` are published, generation runs from the bundle alone,
and `BundleRoundTripTest` pins the two together. The Gradle plugin itself is
still to write, and phase 5 turned up what it costs.

The plan assumed the plugin emits "objects implementing the same core
interfaces" — a handful of lines binding a generated registry to
`CountryNameSource`. That is true for country and currency names. It is not true
for datetime or currency formatting, because the pattern parser, the number
formatter and the payload decoders live in `*-cldr` internals, and generated
code in a user's build cannot reach them. A narrowed datetime source needs the
whole formatter, which is around 300 lines, not a binding.

So the plugin needs one of two things first, and it is worth taking the decision
deliberately rather than discovering it halfway through:

1. **Move the decoders and formatters into `*-core`**, behind
   `@InternalKotlinxLocaleApi`, parameterized by the registry they read. One
   compiled copy, and generated code is genuinely a few lines. It costs the
   property that "the pattern parser and the number formatter live in `*-cldr`,
   not in core", and it moves roughly 700 lines across the boundary — which also
   moves them into every consumer of `-core`, including one that only wants
   codes.
2. **Ship them as templates in `kotlinx-locale-codegen`**, and generate the
   `*-cldr` sources from the same templates. This is the fallback the risk note
   above already anticipated, and it keeps exactly one copy of the source text
   rather than one copy of the compiled code. It costs turning working
   hand-written runtime into generated runtime.

Option 1 is better engineering and worse layering; option 2 is the reverse. The
size probes make the cost of option 1 measurable, which is the argument for
deciding it with a number rather than by taste: generate the formatter into a
codes-only probe and see what it adds.

Everything else phase 5 called for is in place, so whichever way this goes, the
plugin is a DSL, a task, and one call to `generateSources`.

**Phase 6, later. Platform sources.** JS over `Intl`, JVM and Android over
`java.util.Locale` and ICU4J, Apple over `NSLocale`. Composed with a bundled
fallback on targets with no system data.

Phases 1 through 3 pay for themselves immediately. Phase 5 is the largest single
piece of new work. Phase 6 is cheap once 1 through 4 exist, which is the point of
shaping the interfaces around operations now.
