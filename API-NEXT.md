# API surface after the split

What `API.md` becomes for a consumer who takes everything: core, types and
CLDR, the equivalent of what a single dependency gives them today.

The short version: **the call sites do not change.** Imports and dependencies
do. Every output table in `API.md` stays byte for byte identical, because it is
the same CLDR data going through the same formatter.

## The rule that makes it work

> Generated types carry only their per-entry data, as constructor properties.
> Everything else about them is an extension, in every layer.

Hand-written types keep normal members, because they have to: `equals`,
`hashCode` and `toString` on `CurrencyAmount` and `Locale` cannot be extensions.
The rule is about the generated enums, which are the things that move between
layers.

```kotlin
// country-types (generated)          package dev.carcara.kotlinx.locale.country
public enum class Country(public val alpha3: String, public val numericCode: Int) {
    AD("AND", 20), AE("ARE", 784), /* ... */ ;
    public companion object
}

// country-core (hand written)        package dev.carcara.kotlinx.locale.country
public val Country.alpha2: String get() = name
public fun Country.Companion.forAlpha2(code: String): Country = /* ... */
public fun Country.Companion.forAlpha2OrNull(code: String): Country? = /* ... */
public interface CountryNameSource : LocaleDataSource { /* ... */ }

// country-cldr                       package dev.carcara.kotlinx.locale.country.cldr
public object CldrCountry : CountryNameSource { /* generated tables */ }
public fun Country.displayName(locale: Locale): String = CldrCountry.displayName(this, locale)
```

Because members and extensions are called identically, a declaration can move
between layers later without touching a single call site. Packaging becomes a
deployment decision rather than an API one, which is worth a lot while the
design is still moving.

It also means the emitter only ever writes data, never logic. That is the
property that keeps the shipped `-cldr` module and the plugin's generated output
from drifting apart.

## Imports

```kotlin
// today
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.currency.*
import dev.carcara.kotlinx.locale.datetime.*
```

```kotlin
// after
import dev.carcara.kotlinx.locale.country.*
import dev.carcara.kotlinx.locale.country.cldr.*
import dev.carcara.kotlinx.locale.currency.*
import dev.carcara.kotlinx.locale.currency.cldr.*
import dev.carcara.kotlinx.locale.datetime.*
import dev.carcara.kotlinx.locale.datetime.cldr.*
```

One extra import per domain, naming which implementation answers. The IDE writes
it. Swapping to the platform sources later is a search and replace of `.cldr`
for `.platform` plus a dependency change, with no other edit.

### Why the implementation gets its own package

`-types` and `-core` share the base package, since there is exactly one of each
and they never collide.

Implementation modules do not, and this was measured rather than assumed. Two
modules declaring the same extension signature in the same package **compile
cleanly and one silently wins by classpath order**. A test project with
`country-cldr` and `country-platform` both declaring
`Country.displayName(Locale)` in `dev.carcara.kotlinx.locale.country` built with
no error and no warning, and resolved to whichever came first. That is the exact
failure mode this whole design exists to avoid, so the implementations get
distinct packages and the choice is made by an import you can read.

It also keeps composition possible. An app that wants the platform source with a
CLDR fallback needs both modules on the classpath, which distinct packages allow
and a single shared package would silently corrupt.

## Dependencies

```kotlin
// today
implementation("dev.carcara:kotlinx-locale-country:$v")
implementation("dev.carcara:kotlinx-locale-currency:$v")
implementation("dev.carcara:kotlinx-locale-datetime:$v")
```

```kotlin
// after, full mode
implementation("dev.carcara:kotlinx-locale-core:$v")
implementation("dev.carcara:kotlinx-locale-country-core:$v")
implementation("dev.carcara:kotlinx-locale-country-types:$v")
implementation("dev.carcara:kotlinx-locale-country-cldr:$v")
implementation("dev.carcara:kotlinx-locale-currency-core:$v")
implementation("dev.carcara:kotlinx-locale-currency-types:$v")
implementation("dev.carcara:kotlinx-locale-currency-cldr:$v")
implementation("dev.carcara:kotlinx-locale-datetime-core:$v")
implementation("dev.carcara:kotlinx-locale-datetime-cldr:$v")
```

Three lines become nine, and that is the real cost of the split. A version
catalog bundle absorbs it:

```toml
[bundles]
locale-country = ["locale-country-core", "locale-country-types", "locale-country-cldr"]
```

```kotlin
implementation(libs.bundles.locale.country)
```

We do not publish that bundle ourselves as a code-free aggregate artifact; see
decision 1 below.

## Migration at a glance

| Today | After |
| --- | --- |
| `Locale.of("en")` | unchanged |
| `Locale.forLanguageTag("pt-BR")` | unchanged |
| `Locale.current` | unchanged |
| `Locale.availableLocales` | `CldrCountry.supportedLocales` (per source) |
| `Country.BR` in a `when` | unchanged, still exhaustive |
| `Country.US.alpha2` / `alpha3` / `numericCode` | unchanged |
| `Country.forAlpha2("br")` | unchanged |
| `Country.forNumericCode(392)` | unchanged |
| `Country.forLocaleOrNull(locale)` | unchanged |
| `Country.US.displayName(locale)` | unchanged |
| `Country.forDisplayNameOrNull(name, locale)` | unchanged |
| `Currency.forCode("usd")` | unchanged |
| `Currency.USD.numericCode` / `minorUnitDigits` | unchanged |
| `Currency.ALL.isoToCldrUnits(12345)` | unchanged |
| `Currency.forCountryOrNull(Country.DE)` | unchanged |
| `Country.US.currency` / `Country.PA.currencies` | unchanged |
| `Currency.USD.symbol(locale)` | unchanged |
| `Currency.USD.displayName(locale)` | unchanged |
| `CurrencyAmount.of(USD, 12, 50)` | unchanged |
| `amount.toDecimalString()` / `+` / `-` / `<` | unchanged |
| `amount.format(locale, style, accounting, cash)` | unchanged |
| `CurrencyAmount.parseFormatted(cur, text, locale)` | unchanged |
| `date.format(style, locale)` | unchanged |
| `dateTime.format(dateStyle, timeStyle, locale)` | unchanged |
| `month.displayName(style, locale)` | unchanged |
| `dayOfWeek.displayName(style, locale)` | unchanged |

Twenty-six entries, twenty-five unchanged. The only source edit a consumer makes
is deleting `Locale.availableLocales`, plus the imports and dependencies above.

Today's default arguments survive too, because the implementation module that
declares the extension can also supply the default:

```kotlin
// country-cldr
public fun Country.displayName(locale: Locale = Locale.current): String =
    CldrCountry.displayName(this, locale)
```

The defaults stay exactly as they are today (decision 4), so no call site loses
an argument it was allowed to omit.

## What does change

### Locale.availableLocales

It goes away. It was a generated table of the locales CLDR ships data for, which
is a property of a data source rather than of the `Locale` type, and it stops
being true the moment a build narrows its locales.

```kotlin
CldrCountry.supportedLocales.size     // 1121
CldrDateTime.supportedLocales.size    // 1121
GeneratedCountry.supportedLocales     // whatever the plugin was configured for
```

### The explicit form is always available

Every convenience extension is one line over a source object, and the source is
public. That matters in two places the current API cannot serve.

**Testing without CLDR.** A test that needs `displayName` to return a known
string implements four lines of `CountryNameSource` instead of pinning a real
CLDR value that a data upgrade can change:

```kotlin
val fake = object : CountryNameSource {
    override val supportedLocales = setOf(Locale.of("en"))
    override fun countryNameOrNull(alpha2: String, locale: Locale) = "Testland"
}
fake.displayName(Country.BR, Locale.of("en"))   // "Testland"
```

**Composition.** Platform first, bundled data behind it:

```kotlin
val names = FallbackCountryNames(PlatformCountry, CldrCountry)
names.displayName(Country.BR, locale)
```

Composed sources use the explicit form, since the convenience extension binds to
one implementation by definition.

### The locale catalog

New, and optional. `kotlinx-locale-types` adds a generated reference for every
locale CLDR ships, so the Gradle plugin's configuration is type-checked:

```kotlin
Locale.forLanguageTag(PT.BR.tag)
```

Nothing requires it in application code.

## Nullability and fallback

Composition requires the interface to be able to say "I have nothing", or a
fallback source cannot know when to delegate. So the interface is partial:

```kotlin
public interface CountryNameSource : LocaleDataSource {
    public fun countryNameOrNull(alpha2: String, locale: Locale): String?
}
```

and `-core` layers the total operation over it, with the fallback the library
already documents:

```kotlin
public fun CountryNameSource.displayName(country: Country, locale: Locale): String =
    countryNameOrNull(country.alpha2, locale) ?: country.alpha2
```

That is exactly today's semantics: CLDR root carries no country names, so an
unmatched locale already falls back to the ISO code. Currency does the same with
`currency.code`.

Datetime has no code to degrade to. Today it never fails because CLDR root
always has patterns, and root's patterns are ISO-like (`2026-07-27` at SHORT). A
narrowed source has no root, so it needs one of:

1. `formatDate` returns `String?` and the caller decides. Honest, and a
   regression for the common case.
2. `formatDate` returns `String` and falls back to ISO 8601, close to what root
   produces today.
3. The plugin requires a configured fallback locale, so every generated source
   is total.

We take 2 and 3 together (decision 3): the interface stays nullable so composers
work, `-core` layers a total operation with ISO 8601 behind it, and the plugin
makes `fallback(...)` mandatory so a generated source is total on its own terms
and never reaches the backstop.

## What does not change

- Every output table in `API.md`. Same data, same formatter, same bytes.
- Tag parsing, the fallback chain, `Locale.current` per platform.
- The CLDR and ISO 4217 versions and where they come from.
- Immutability and thread safety. Sources are stateless objects.
- Which entry points throw. `Locale.of`, `forLanguageTag`, the non-`OrNull`
  lookups, the `CurrencyAmount` parse and `of` functions, and cross-currency
  arithmetic. All still `IllegalArgumentException`.

## Costs of the extension rule

Three, none fatal, all worth knowing before committing.

**Java callers get static methods.** `CountryKt.displayName(Country.BR, locale)`
rather than `country.displayName(locale)`. A minor audience for a KMP library,
but a real regression for it.

**JS and TypeScript exports would be standalone functions** rather than methods
on the class, if `@JsExport` is ever added. Nothing is exported today.

**The generated enums need `public companion object`** so the lookups can be
companion extensions. A one-line emitter requirement, easy to forget.

## Decisions on the surface

1. **No aggregate artifacts.** A version catalog bundle already collapses the
   three lines, it lives in the consumer's build where the versions are, and it
   costs us nothing to publish or to keep consistent. An artifact that exists
   only to pull three others is a second place for the dependency set to be
   wrong.
2. **`CldrCountry`, `CldrCurrency`, `CldrDateTime`.** Short, and the prefix says
   which data answers.
3. **Datetime is total, with ISO 8601 as the backstop.** The source interface
   returns `String?` so a composer can tell a miss from an answer, `-core` layers
   a total operation over it that falls back to ISO 8601, and the plugin requires
   a configured fallback locale so a generated source never reaches the backstop
   in the first place. Today's CLDR root patterns are already ISO-like, so the
   backstop matches what a full build produces.
4. **The `Locale.current` defaults survive**, unchanged from today: country and
   currency have them, datetime does not. See decision 2 in `PLAN.md`.
5. **`...country.cldr`.** See decision 1 in `PLAN.md`.
