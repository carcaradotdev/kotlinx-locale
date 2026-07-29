# Plan: splitting the API from the data

A proposal for restructuring the library into pure API interfaces, pure type
layers and swappable data implementations, one set per domain, plus a Gradle
plugin that generates a narrowed implementation from a locale set the user
declares.

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
kotlinx-locale-country-cldr          CldrCountryNames + payloads
kotlinx-locale-country-platform      later

kotlinx-locale-currency-core         CurrencyNameSource, CurrencyFormatSource, CurrencyAmount,
                                     lookups, unit math, typed overloads
kotlinx-locale-currency-types        Currency enum, country to currency map
kotlinx-locale-currency-cldr         CldrCurrencyNames, CldrCurrencyFormats + payloads
kotlinx-locale-currency-platform     later

kotlinx-locale-datetime-core         DateTimeFormatSource, FormatStyle, TextStyle
kotlinx-locale-datetime-cldr         CldrDateTimeFormats + payloads
kotlinx-locale-datetime-platform     later
```

The naming rule is mechanical: `kotlinx-locale[-<domain>]-<layer>`, with the
domain segment omitted for the locale domain itself because it is the root. That
costs renaming today's `kotlinx-locale` to `kotlinx-locale-core`, which is free
at 0.1.0-SNAPSHOT and buys a listing where every artifact says what layer it is.

Eleven modules now, fourteen once platform lands. Within a domain, `-core`
depends on `-types` for the enum, and `-cldr` depends on `-core` alone. See the
note below on where hand-written code that mentions the enums belongs, which is
what decides whether this is eleven modules or thirteen.

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
combination needs a home, and the question only arises if decision 4 says the
plugin may narrow `-types`:

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
they belong in `datetime-core` next to the interface that uses them. See open
decision 3 if you want them broken out anyway.

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

The source is a parameter, always. The remaining choice is which side of the
call it sits on.

**A. The domain object is the receiver.**

```kotlin
Country.BR.displayName(locale, CldrCountryNames)
CldrCurrencyFormats.let { amount.format(locale, CurrencySymbolStyle.SYMBOL, false, false, it) }
```

Reads from the thing you have. Gets awkward fast once an operation already takes
four arguments, because the source lands last and far from the verb.

**B. The source is the receiver.** Recommended.

```kotlin
CldrCountryNames.displayName(Country.BR, locale)
CldrCurrencyNames.symbol(Currency.BRL, locale)
CldrCurrencyFormats.format(amount, locale, CurrencySymbolStyle.SYMBOL, accounting = false, cash = false)
CldrDateTimeFormats.formatDate(date, FormatStyle.LONG, locale)
CldrCountryNames.countryForName("Brasil", locale)
```

Every data-backed operation becomes a method on a source, and the source is the
first thing you read. It also gives the type layer a clean job: `*-core`
declares the interface keyed by string codes, and `*-types` adds typed overloads
as extensions on that interface.

```kotlin
// country-core
public interface CountryNameSource : LocaleDataSource {
    public fun displayName(alpha2: String, locale: Locale): String?
    public fun countryForName(name: String, locale: Locale): String?
}

// country-types
public enum class Country(public val alpha3: String, public val numericCode: Int) { /* ... */ }

public fun CountryNameSource.displayName(country: Country, locale: Locale): String =
    displayName(country.alpha2, locale) ?: country.alpha2

public fun CountryNameSource.countryForName(name: String, locale: Locale): Country? =
    countryForName(name, locale)?.let(Country::forAlpha2OrNull)

// country-cldr
public object CldrCountryNames : CountryNameSource { /* generated tables */ }
```

Core never learns the enums exist, types never learns an implementation exists,
and a platform source implements core alone.

**D. A context parameter**, which keeps today's call site without binding an
implementation:

```kotlin
// country-core, written once
context(source: CountryNameSource)
public fun Country.displayName(locale: Locale): String =
    source.countryNameOrNull(alpha2, locale) ?: alpha2

// call site
with(CldrCountry) { Country.BR.displayName(locale) }
```

Verified on Kotlin 2.4.0: no compiler flag, no experimental warning. The source
stays explicit and lexically scoped, the extension is declared once for every
implementation, and it propagates through application code as a context rather
than an argument. `API-NEXT.md` works through what it does to the surface.

**C. A runtime registry**, for the record, is the option that would let
`displayName` stay a member of the enum: a global `LocaleData.provider` that a
data module installs. Rejected. It costs a mutable global, a failure mode where
nothing is installed, an initialization order problem KMP has no clean answer
for, and it defeats dead code elimination because the registry holds a reference
to every source that was linked.

Under B, today's members and extensions all move onto sources. `displayName`,
`symbol`, `format`, `parseFormatted`, `Country.forDisplayNameOrNull`, and the
datetime `format` and `displayName` extensions. At 0.1.0-SNAPSHOT that is free
to do.

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

// currency-core
public interface CurrencyFormatSource : LocaleDataSource {
    public fun format(
        minorUnits: Long,
        currencyCode: String,
        locale: Locale,
        style: CurrencySymbolStyle,
        accounting: Boolean,
        cash: Boolean,
    ): String?
    public fun parse(text: String, currencyCode: String, locale: Locale): Long?
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

## What lives in each types module

`*-types` holds the generated enum entries, the facts that are true regardless
of language, and the typed overloads.

`country-types`

- `Country` with `alpha2`, `alpha3`, `numericCode`
- `forAlpha2`, `forAlpha3`, `forNumericCode` and their `OrNull` forms
- `forLocaleOrNull`, which reads the region subtag and needs no data
- typed overloads on `CountryNameSource`

`currency-types`

- `Currency` with `code`, `numericCode`, `defaultFractionDigits`,
  `minorUnitDigits`
- `cldrFractionDigits`, `cldrRoundingIncrement`, `cldrCashFractionDigits`,
  `cldrCashRoundingIncrement`
- `isoToCldrUnits`, `cldrToIsoUnits`
- `CurrencyAmount` in full except `format` and `parseFormatted`
- the country to currency map behind `Country.currencies` and `Country.currency`
- typed overloads on `CurrencyNameSource` and `CurrencyFormatSource`

The four `cldr*` integer fields and the country to currency map are the
arguable ones. They come from CLDR supplemental data, so they carry a CLDR
version, but they are structural rather than linguistic: roughly 900 numbers and
250 mappings, a rounding error in size, and `isoToCldrUnits` needs them with no
locale in play. Recommendation: keep them in types and stamp the types artifacts
with the CLDR version they were generated from. Open decision 4 if you disagree.

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

    override fun displayName(alpha2: String, locale: Locale): String? =
        primary.displayName(alpha2, locale) ?: fallback.displayName(alpha2, locale)

    override fun countryForName(name: String, locale: Locale): String? =
        primary.countryForName(name, locale) ?: fallback.countryForName(name, locale)
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

which the user then passes explicitly, exactly like `CldrCountryNames`. Swapping
a filtered build for the full one is a one-line change at the call site and a
dependency swap, with no hidden defaults to keep straight.

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
tests become a shared test module that runs against any source. Two tiers:
exact, for CLDR-backed sources (the shipped modules and anything the plugin
generates, which must match ICU byte for byte), and behavioural, for platform
sources, which can only be checked for shape, round-tripping and non-emptiness
because system data varies by OS version. Without this the platform layer is
unverifiable, so it is worth building before it is needed.

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
taking core, types and CLDR. The short version: twenty-four public entry points,
seventeen of them unchanged, and every output table in `API.md` stays byte for
byte identical because it is the same data through the same formatter.

## Open decisions

1. Source as receiver (option B), domain object as receiver (option A), or a
   context parameter (option D) that keeps today's call site. The prose above
   assumes B; D is the later suggestion and is the one worth taking if the
   ergonomics of today's call site matter, since it costs a `with` scope and
   nothing else. See "Keeping today's call site" in `API-NEXT.md`.
2. Does `Locale.current` survive as a default anywhere? Under "explicit
   everything" the locale would always be passed, and `Locale.current` stays
   available as a value you pass deliberately. This is a separate axis from the
   source dispatch and can go either way.
3. Do `FormatStyle` and `TextStyle` stay in `datetime-core`, or get their own
   `datetime-types` artifact? Keeping them in core means `*-core` never depends
   on `*-types` in any domain. Breaking them out means the layer names are
   uniform but `datetime-core` gains a dependency on `datetime-types`.
   Same question for `CurrencySymbolStyle` in `currency-core`.
4. May the Gradle plugin narrow `-types`, or only `-cldr`? Narrowing `-types`
   is what lets a build declare that `Currency.JPY` does not exist, and it
   forces `-types` to be nothing but generator output. Narrowing only `-cldr`
   leaves `-types` free to hold hand-written members and removes decision 5
   entirely. The rest of the layering hangs on this, so it is worth taking
   first.
5. If the plugin may narrow `-types`: does the hand-written, enum-dependent
   code (`CurrencyAmount`, the lookups, the unit math, the typed overloads) go
   into `-core`, or into a separate layer of its own? This plan assumes
   `-core`, on the grounds that the "any `-cldr` links against any `-types`"
   property is protected by a rule (never name a specific entry) rather than by
   a module boundary. A separate layer keeps `-core` to pure interfaces and
   costs two more modules.
6. Do the four `cldr*` integer fields and the country to currency map stay in
   `currency-types`, or move behind a source?
7. On a miss, does a source return null, consult a configured fallback locale,
   or throw? The interfaces above return null and let a composer decide, which
   makes fallback a plugin config value rather than a library policy.
8. Entity narrowing in the plugin: never, name-tables-only, or full? Decision 4
   sets the ceiling on this one.
9. Does `Locale.availableLocales` move to `LocaleDataSource.supportedLocales`,
   or disappear from the public API?
10. Locale catalog shape: enum per language implementing `LocaleRef`, or object
   per language holding `const val` tags? Recommended is the enum, on the
   grounds that its only stated use is plugin configuration where the cost is
   nil.
11. Does the locale catalog nest two levels (`Zh.HANS_CN`) or three
    (`Zh.Hans.CN`)? Two keeps every reference uniform and only 33 of 322
    languages would ever use the third.
12. Artifact names. `kotlinx-locale-country-cldr` reads naturally but sorts the
    domains together and the layers apart; `kotlinx-locale-cldr-country` groups
    by layer in a repository listing. Eleven to sixteen artifacts is enough
    for the choice to matter.

## Phases

Each phase ends green: all tests pass, one ABI dump update, one probe run
recorded.

**Phase 0.** Settle the open decisions. No code.

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
`kotlinx-locale-codegen` artifacts, then the Gradle plugin, then the
byte-identity test that pins them together.

**Phase 6, later. Platform sources.** JS over `Intl`, JVM and Android over
`java.util.Locale` and ICU4J, Apple over `NSLocale`. Composed with a bundled
fallback on targets with no system data.

Phases 1 through 3 pay for themselves immediately. Phase 5 is the largest single
piece of new work. Phase 6 is cheap once 1 through 4 exist, which is the point of
shaping the interfaces around operations now.
