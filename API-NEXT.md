# API surface after the split

What `API.md` becomes for a consumer who takes everything: core, types and
CLDR, the equivalent of what a single dependency gives them today.

The behaviour does not change. Every output table in `API.md` stays byte for
byte identical, because it is the same CLDR data going through the same
formatter. What changes is where declarations live and how a call names the
implementation answering it.

## The shape of the change

Today, data-backed operations are members of the domain types, and the
implementation is welded in:

```kotlin
Country.BR.displayName(locale)
```

After, they are methods on a source, and the source is a value you can see:

```kotlin
CldrCountry.displayName(Country.BR, locale)
```

That is the whole change, repeated across five entry points. Everything that
does not touch translated text keeps its current shape.

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

Three lines become nine. That is the price of the split and it is worth
acknowledging plainly. A version catalog bundle absorbs most of it:

```toml
[libraries]
locale-country-core  = { module = "dev.carcara:kotlinx-locale-country-core",  version.ref = "locale" }
locale-country-types = { module = "dev.carcara:kotlinx-locale-country-types", version.ref = "locale" }
locale-country-cldr  = { module = "dev.carcara:kotlinx-locale-country-cldr",  version.ref = "locale" }

[bundles]
locale-country = ["locale-country-core", "locale-country-types", "locale-country-cldr"]
```

```kotlin
implementation(libs.bundles.locale.country)
```

See open question 1 on whether we should also publish that bundle ourselves.

## One source object per domain

`*-cldr` ships one object per domain, implementing every interface that domain
declares:

```kotlin
public object CldrCountry : CountryNameSource
public object CldrCurrency : CurrencyNameSource, CurrencyFormatSource
public object CldrDateTime : DateTimeFormatSource
```

So a currency call site needs one name, not one per interface. The interfaces
stay separate so a platform source can implement naming without formatting, but
nothing forces that on a consumer.

## Migration at a glance

| Today | After |
| --- | --- |
| `Locale.of("en")` | unchanged |
| `Locale.forLanguageTag("pt-BR")` | unchanged |
| `Locale.current` | unchanged |
| `Locale.availableLocales` | `CldrCountry.supportedLocales` (per source) |
| `Country.forAlpha2("br")` | unchanged |
| `Country.US.alpha3` | unchanged |
| `Country.US.displayName(locale)` | `CldrCountry.displayName(Country.US, locale)` |
| `Country.forDisplayNameOrNull(name, locale)` | `CldrCountry.countryForName(name, locale)` |
| `Currency.forCode("usd")` | unchanged |
| `Currency.USD.minorUnitDigits` | unchanged |
| `Currency.ALL.isoToCldrUnits(12345)` | unchanged |
| `Country.US.currency` | unchanged |
| `Currency.USD.symbol(locale)` | `CldrCurrency.symbol(Currency.USD, locale)` |
| `Currency.USD.displayName(locale)` | `CldrCurrency.displayName(Currency.USD, locale)` |
| `CurrencyAmount.of(USD, 12, 50)` | unchanged |
| `amount.toDecimalString()` | unchanged |
| `amount + other` | unchanged |
| `amount.format(locale, style, accounting, cash)` | `CldrCurrency.format(amount, locale, style, accounting, cash)` |
| `CurrencyAmount.parseFormatted(cur, text, locale)` | `CldrCurrency.parse(text, cur, locale)` |
| `date.format(style, locale)` | `CldrDateTime.formatDate(date, style, locale)` |
| `time.format(style, locale)` | `CldrDateTime.formatTime(time, style, locale)` |
| `dateTime.format(dateStyle, timeStyle, locale)` | `CldrDateTime.formatDateTime(dateTime, dateStyle, timeStyle, locale)` |
| `month.displayName(style, locale)` | `CldrDateTime.monthName(month, style, locale)` |
| `dayOfWeek.displayName(style, locale)` | `CldrDateTime.dayOfWeekName(dayOfWeek, style, locale)` |
| `displayName(locale = Locale.current)` | no default, pass `Locale.current` yourself |

Twenty-four entries, seventeen of them unchanged.

## Locale

Unchanged, apart from one removal.

```kotlin
Locale.of("sr", script = "Cyrl", region = "BA")
Locale.forLanguageTag("pt-BR")
Locale.forLanguageTagOrNull("not a tag!")   // null
Locale.current
```

`Locale.availableLocales` goes away. It was a generated table of the locales
CLDR ships data for, which is a property of a data source rather than of the
`Locale` type, and it stops being true the moment a build narrows its locales.
It becomes:

```kotlin
CldrCountry.supportedLocales.size     // 1121
CldrDateTime.supportedLocales.size    // 1121
GeneratedCountry.supportedLocales     // whatever the plugin was configured for
```

Tag parsing rules, the fallback chain and `Locale.current`'s platform sources
are all untouched.

## The locale catalog

New, and optional. `kotlinx-locale-types` adds a generated reference for every
locale CLDR ships:

```kotlin
Locale.forLanguageTag(Pt.BR.tag)   // instead of Locale.forLanguageTag("pt-BR")
```

Nothing requires it. It exists so the Gradle plugin's configuration is
type-checked, and app code may use it for the same reason.

## Country

The enum keeps every member that is not a name:

```kotlin
Country.BR                              // unchanged, still exhaustive in `when`
Country.US.alpha2                       // "US"
Country.US.alpha3                       // "USA"
Country.US.numericCode                  // 840
Country.forAlpha2("br")                 // Country.BR
Country.forAlpha3("DEU")                // Country.DE
Country.forNumericCode(392)             // Country.JP
Country.forAlpha2OrNull("XX")           // null
Country.forLocaleOrNull(ptBR)           // Country.BR
```

The two name operations move:

```kotlin
// today
Country.US.displayName(Locale.forLanguageTag("pt-BR"))          // Estados Unidos
Country.forDisplayNameOrNull("Estados Unidos", ptLocale)        // Country.US

// after
CldrCountry.displayName(Country.US, Locale.forLanguageTag("pt-BR"))  // Estados Unidos
CldrCountry.countryForName("Estados Unidos", ptLocale)               // Country.US
```

## Currency

Same split. Everything numeric stays on the enum:

```kotlin
Currency.USD.numericCode                // 840
Currency.USD.defaultFractionDigits      // 2
Currency.ALL.cldrFractionDigits         // 0
Currency.CHF.cldrCashRoundingIncrement
Currency.USD.minorUnitDigits            // 2
Currency.ALL.isoToCldrUnits(12345)      // 123
Currency.ALL.cldrToIsoUnits(123)        // 12300
Currency.forCode("usd")                 // Currency.USD
Currency.forNumericCode(978)            // Currency.EUR
Currency.forCountryOrNull(Country.DE)   // Currency.EUR
Currency.forLocaleOrNull(ptBR)          // Currency.BRL
Country.US.currency                     // Currency.USD
Country.PA.currencies                   // [PAB, USD]
```

The text moves:

```kotlin
// today
Currency.USD.symbol(Locale.forLanguageTag("pt-BR"))       // US$
Currency.USD.displayName(Locale.forLanguageTag("en"))     // US Dollar

// after
CldrCurrency.symbol(Currency.USD, Locale.forLanguageTag("pt-BR"))     // US$
CldrCurrency.displayName(Currency.USD, Locale.forLanguageTag("en"))   // US Dollar
```

## CurrencyAmount

The value type is untouched. Construction, arithmetic, comparison, the ISO
decimal string and the ISO decimal parse all stay where they are, because none
of them reads a locale:

```kotlin
val price = CurrencyAmount.of(Currency.USD, 12, 50)
price.majorUnits                                    // 12
price.minorPart                                     // 50
price.toDecimalString()                             // "12.50"
price.toString()                                    // "USD 12.50"
CurrencyAmount.parse(Currency.USD, "12.5")          // 12.50
CurrencyAmount.parseOrNull(Currency.USD, "12.345")  // null
price + CurrencyAmount(Currency.USD, 100)           // 13.50
-price
price < total
```

The two locale-aware ones move onto the source:

```kotlin
// today
amount.format(en)                                    // -$1,234.56
amount.format(en, accounting = true)                 // ($1,234.56)
amount.format(en, style = CurrencySymbolStyle.CODE)  // -USD 1,234.56
CurrencyAmount(Currency.CHF, 1003).format(en, cash = true)  // CHF 10.05
CurrencyAmount.parseFormatted(Currency.BRL, "R$ 1.234,56", ptBR)

// after
CldrCurrency.format(amount, en)                                   // -$1,234.56
CldrCurrency.format(amount, en, accounting = true)                // ($1,234.56)
CldrCurrency.format(amount, en, style = CurrencySymbolStyle.CODE) // -USD 1,234.56
CldrCurrency.format(CurrencyAmount(Currency.CHF, 1003), en, cash = true)  // CHF 10.05
CldrCurrency.parse("R$ 1.234,56", Currency.BRL, ptBR)
```

`format` keeps its named optional arguments for `style`, `accounting` and
`cash`, since those are behaviour switches rather than an implementation
choice. Only `locale` loses its default.

## Datetime

Datetime has no enum to split, so the change is purely that the extensions
become source methods:

```kotlin
// today
date.format(FormatStyle.LONG, ptBR)                       // 27 de julho de 2026
time.format(FormatStyle.SHORT, en)                        // 3:05 PM
dateTime.format(FormatStyle.LONG, FormatStyle.SHORT, en)  // July 27, 2026, 3:05 PM
dateTime.format(FormatStyle.FULL, en)
Month.JULY.displayName(TextStyle.FULL, ru)                // июля
DayOfWeek.MONDAY.displayName(TextStyle.ABBREVIATED, de)   // Mo.

// after
CldrDateTime.formatDate(date, FormatStyle.LONG, ptBR)
CldrDateTime.formatTime(time, FormatStyle.SHORT, en)
CldrDateTime.formatDateTime(dateTime, FormatStyle.LONG, FormatStyle.SHORT, en)
CldrDateTime.formatDateTime(dateTime, FormatStyle.FULL, en)
CldrDateTime.monthName(Month.JULY, TextStyle.FULL, ru)
CldrDateTime.dayOfWeekName(DayOfWeek.MONDAY, TextStyle.ABBREVIATED, de)
```

`FormatStyle` and `TextStyle` keep their meaning and move to
`kotlinx-locale-datetime-core`, next to the interface whose signatures use them.

Unlike country and currency, the datetime interface takes the kotlinx-datetime
types directly rather than primitives. There is no narrowing story for `Month`,
it is always twelve, so nothing is gained by keying it on `Int`.

## Nullability and fallback

This is the one place where the split shows through into semantics, and it
needs a decision.

Composition requires the interface to be able to say "I have nothing", or a
fallback source cannot know when to delegate. So the interface is nullable:

```kotlin
public interface CountryNameSource : LocaleDataSource {
    public fun countryNameOrNull(alpha2: String, locale: Locale): String?
}
```

But today `displayName` never returns null and never throws. That guarantee is
worth keeping, so `*-core` layers the total operation over the partial one, with
the same fallback the library already documents:

```kotlin
// country-core
public fun CountryNameSource.displayName(country: Country, locale: Locale): String =
    countryNameOrNull(country.alpha2, locale) ?: country.alpha2

// currency-core
public fun CurrencyNameSource.displayName(currency: Currency, locale: Locale): String =
    currencyNameOrNull(currency.code, locale) ?: currency.code
```

Those are exactly today's semantics: CLDR root carries no country names, so an
unmatched locale already falls back to the ISO code.

Datetime has no such natural fallback, since a date has no code to degrade to.
Today it never fails because CLDR root always has patterns, and root's patterns
are ISO-like (`2026-07-27` at SHORT). A narrowed source has no root. Options:

1. `formatDate` returns `String?`, and the caller decides. Honest, and a
   regression for the common case.
2. `formatDate` returns `String` and falls back to ISO 8601, which is close to
   what root produces today anyway.
3. `formatDate` returns `String` and the plugin requires a configured fallback
   locale, so a generated source is always total.

Option 3 is the one that keeps the guarantee intact for every source, at the
cost of making `fallback(...)` mandatory in the plugin rather than optional.
Open question 3.

## Keeping today's call site

Everything above assumes the source is the receiver. There are three ways to
keep `Country.BR.displayName(locale)` instead, and one of them costs almost
nothing.

### Context parameters, declared once in `-core`

```kotlin
// country-core, written once, works for every implementation
context(source: CountryNameSource)
public fun Country.displayName(locale: Locale): String =
    source.countryNameOrNull(alpha2, locale) ?: alpha2
```

```kotlin
with(CldrCountry) {
    Country.BR.displayName(locale)      // exactly today's call
}
```

The source is explicit and lexically visible, there is no global state, and
nothing is bound at the declaration, so the same extension serves CLDR, the
platform sources and anything the plugin generates.

It also propagates, which is the dependency-injection story the split was
supposed to buy:

```kotlin
context(names: CountryNameSource)
fun row(country: Country, locale: Locale): String =
    "${country.alpha3}: ${country.displayName(locale)}"

class Screen(private val names: CountryNameSource) {
    fun label(country: Country) = with(names) { country.displayName(Locale.current) }
}
```

Verified against Kotlin 2.4.0: compiles and runs with no compiler flag and no
experimental warning, including the propagating and class-held forms above.

The cost is that the call only compiles inside a scope that provides the
source. `Country.BR.displayName(locale)` on its own is an error saying no
context argument was found. That is the feature, but it is a real change: you
cannot format from anywhere without first deciding where the source comes from.

### An extension shipped by each implementation module

```kotlin
// country-cldr
public fun Country.displayName(locale: Locale): String =
    CldrCountry.displayName(this, locale)
```

Zero ceremony at the call site, and the binding is explicit in the import
rather than at the call: `import ...country.cldr.displayName` versus
`import ...country.platform.displayName`. Two implementations on one classpath
collide at compile time, which forces the choice into the open.

This is the "sugar" that was rejected earlier, and the reason to look again is
that the import genuinely names the implementation. The reason it is still
second best is that it has to be written once per implementation module rather
than once in `-core`, so every new source repeats it and can drift.

### The consumer writes the one-liner

```kotlin
// in the application, not the library
fun Country.displayName(locale: Locale) = CldrCountry.displayName(this, locale)
```

The most explicit of the three, since the binding lives in code the consumer
owns, and the library ships nothing extra. Five lines per project for the five
entry points.

### Recommendation

Context parameters. They are the only option where the call is unchanged, the
source is visible at the call site's scope, and the declaration is written once
for every implementation. Adopt them and the migration table above collapses:
every row becomes "unchanged, inside a `with` scope", except the `format` and
`parse` entry points where the source is genuinely a formatter rather than a
name table and reading `CldrCurrency.format(amount, ...)` is arguably clearer
anyway.

The options do not compose. Shipping both context parameters and a plain
extension makes calls inside a `with` block ambiguous, so this is a pick-one.

## What does not change

- Every output table in `API.md`. Same data, same formatter, same bytes.
- Tag parsing, the fallback chain, `Locale.current` per platform.
- The CLDR and ISO 4217 versions and where they come from.
- Immutability and thread safety. Sources are stateless objects, so passing one
  around is free.
- Which entry points throw. `Locale.of`, `forLanguageTag`, the non-`OrNull`
  lookups, the `CurrencyAmount` parse and `of` functions, and cross-currency
  arithmetic. All still `IllegalArgumentException`.

## What gets better

Passing the source is not only a cost. It makes two things possible that are
awkward today.

**Testing without CLDR.** A test that needs `displayName` to return a known
string implements four lines of `CountryNameSource` instead of pinning a real
CLDR value that a data upgrade can change.

**One call site, several sources.** An app can hold the source as a field and
decide at startup, for example a narrowed generated source with the full CLDR
one lazily loaded behind it:

```kotlin
class Formatters(private val countries: CountryNameSource) {
    fun label(country: Country, locale: Locale) = countries.displayName(country, locale)
}
```

## Open questions on the surface

1. Do we publish aggregate artifacts purely as dependency shorthand, with no
   code in them, so `implementation("dev.carcara:kotlinx-locale-country-all")`
   pulls core, types and cldr? It is not the "sugar" that was rejected, since it
   binds no implementation into a call, but it is one more thing to publish.
2. Source object names. `CldrCountry`, `CldrCurrency`, `CldrDateTime` are short
   and say which data is behind them. The alternative is naming by interface
   (`CldrCountryNames`), which reads worse at a call site once one object
   implements two interfaces.
3. Datetime totality: nullable, ISO fallback, or a mandatory configured fallback
   in the plugin. See above.
4. Does `Locale` keep `current`, given it is the last implicit platform read in
   an otherwise explicit API? Keeping it is fine as long as it is a value the
   caller passes rather than a default the library applies.
5. Method naming on the sources. `CldrDateTime.formatDate(date, ...)` versus
   overloads all called `format`. Overloading works because the argument types
   differ, and it reads better; distinct names are easier to implement against
   without ambiguity errors.
