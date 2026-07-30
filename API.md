# API reference

Each domain is three artifacts to depend on and two packages to import: the type
and the contract share a package, and the implementation gets its own so that two
of them can sit on one classpath without silently resolving by classpath order.
(`-cldr-full` pulls `-cldr-runtime`, the engine it supplies tables to, so the
dependency block names three and resolves four.)

```kotlin
// kotlinx-locale-core
import dev.carcara.kotlinx.locale.Locale

// kotlinx-locale-datetime-core and -cldr-full
import dev.carcara.kotlinx.locale.datetime.*
import dev.carcara.kotlinx.locale.datetime.cldr.*

// kotlinx-locale-country-types, -core and -cldr-full
import dev.carcara.kotlinx.locale.country.*
import dev.carcara.kotlinx.locale.country.cldr.*

// kotlinx-locale-currency-types, -core and -cldr-full
import dev.carcara.kotlinx.locale.currency.*
import dev.carcara.kotlinx.locale.currency.cldr.*
```

`kotlinx-locale-core` provides `Locale` and the `LocaleDataSource` contract.
Datetime adds `FormatStyle`, `TextStyle` and extension functions on the
kotlinx-datetime types `LocalDate`, `LocalTime`, `LocalDateTime`, `Month` and
`DayOfWeek`. Country adds the `Country` enum with the ISO 3166-1 codes and CLDR
display names. Currency adds the `Currency` enum (ISO 4217 codes and decimals
plus CLDR formatting behavior), the `CurrencyAmount` value type and the CLDR
currency formatter; it depends on country for country-to-currency mapping.

Generated types carry only their per-entry data. Everything else about them is
an extension, so `Country.BR.alpha3`, `Country.forAlpha3("BRA")` and
`Country.BR.displayName(locale)` are written identically even though they come
from three different artifacts. All datetime examples below are real output for
the date 2026-07-27 (a Monday) and the time 15:05:09.

## Locale

`Locale` is an immutable value type with four normalized parts:

| Property | Type | Example | Normalization |
| --- | --- | --- | --- |
| `language` | `String` | `"pt"` | lowercase, 2 to 8 letters |
| `script` | `String?` | `"Cyrl"` | title case, 4 letters |
| `region` | `String?` | `"BR"` | uppercase, 2 letters or 3 digits |
| `variant` | `String?` | `"valencia"` | lowercase |

Two locales are equal when all four parts are equal, so `Locale` works as a
map key. `toString()` returns the same value as `toLanguageTag()`.

### Creating a locale

```kotlin
// From parts. Case is normalized for you; language is required.
Locale.of("en")
Locale.of("en", region = "GB")
Locale.of("sr", script = "Cyrl", region = "BA")
Locale.of("EN", "latn", "gb").toLanguageTag()   // "en-Latn-GB"

// From a tag. Throws IllegalArgumentException when no language can be extracted.
Locale.forLanguageTag("pt-BR")

// Same parsing, but returns null instead of throwing.
Locale.forLanguageTagOrNull("not a tag!")       // null
```

`Locale.of` throws `IllegalArgumentException` when a part does not fit its
shape, for example a three-letter script or a one-letter language.

### Tag parsing rules

`forLanguageTag` is lenient on purpose, because its main job is digesting
whatever a platform reports. It accepts `-` or `_` as separators, cuts
everything after `.` or `@` (POSIX encoding and modifier suffixes), stops at
the first single-letter subtag (BCP 47 extensions), and maps the legacy
language codes `iw`, `in`, `ji`, `mo` and `tl` to their modern forms. Real
results:

| Input | `toLanguageTag()` |
| --- | --- |
| `pt-BR` | `pt-BR` |
| `PT_br.UTF-8@latin` | `pt-BR` |
| `sr-Cyrl-BA` | `sr-Cyrl-BA` |
| `en-US-u-ca-japanese` | `en-US` |
| `ca-ES-VALENCIA` | `ca-ES-valencia` |
| `in-ID` | `id-ID` |
| `iw` | `he` |

`forLanguageTagOrNull` returns null for `""`, `"C"`, `"POSIX"`, `"123"` and
anything else without a usable language subtag.

### Locale.current

```kotlin
val locale = Locale.current
```

Reads the platform's locale tag and parses it with the rules above. When the
platform exposes nothing (Wasm-WASI) or reports something unparseable, you get
`Locale.of("en")` back, so `Locale.current` never throws and never returns an
unusable value. The per-platform sources are listed in the
[README](README.md#supported-platforms).

### LocaleDataSource.supportedLocales

```kotlin
CldrCountry.supportedLocales.size    // 1121
CldrCurrency.supportedLocales.size   // 1121
CldrDateTime.supportedLocales.size   // 1121
```

Which locales resolve is a property of the source that is installed, not of the
`Locale` type, so every source answers for itself. You do not have to pick from
the set: formatting accepts any `Locale` and falls back as described next.

### Fallback resolution

Formatting looks up data for the most specific matching candidate, in this
order:

1. `language-script-region-variant`
2. `language-script-region`
3. `language-region-variant`
4. `language-region`
5. `language-script`
6. `language-variant`
7. `language`
8. CLDR root

```kotlin
date.format(FormatStyle.MEDIUM, Locale.forLanguageTag("en-XX"))
// "Jul 27, 2026"        (no data for en-XX, falls back to en)

date.format(FormatStyle.SHORT, Locale.of("zz"))
// "2026-07-27"          (unknown language, root patterns are ISO-like)

date.format(FormatStyle.FULL, Locale.of("zz"))
// "2026 M07 27, Mon"    (root has placeholder names, not English ones)
```

Regional inheritance is already baked into the data at generation time, so
`en-AU` correctly behaves like British English rather than American English
even though the fallback list above never mentions `en-001`.

## FormatStyle

The four CLDR standard lengths, used by all three `format` functions:

| Value | en date | de date | ja date |
| --- | --- | --- | --- |
| `FormatStyle.FULL` | Monday, July 27, 2026 | Montag, 27. Juli 2026 | 2026年7月27日月曜日 |
| `FormatStyle.LONG` | July 27, 2026 | 27. Juli 2026 | 2026年7月27日 |
| `FormatStyle.MEDIUM` | Jul 27, 2026 | 27.07.2026 | 2026/07/27 |
| `FormatStyle.SHORT` | 7/27/26 | 27.07.26 | 2026/07/27 |

`FULL` usually includes the weekday. `SHORT` is fully numeric. Some locales
use the same pattern for adjacent lengths (Japanese medium and short dates are
both `2026/07/27`).

## TextStyle

Widths for month and weekday names:

| Value | Meaning | Example (en, July) |
| --- | --- | --- |
| `TextStyle.FULL` | wide name | `July` |
| `TextStyle.ABBREVIATED` | short name | `Jul` |
| `TextStyle.NARROW` | narrowest form, often one letter | `J` |

Narrow names are not unique within a locale (in English, January, June and
July are all `J`), so they suit column headers rather than parsing or lookup.

## LocalDate.format

```kotlin
fun LocalDate.format(style: FormatStyle, locale: Locale): String
```

Output for 2026-07-27 across a spread of locales:

| Locale | FULL | LONG | MEDIUM | SHORT |
| --- | --- | --- | --- | --- |
| en | Monday, July 27, 2026 | July 27, 2026 | Jul 27, 2026 | 7/27/26 |
| en-GB | Monday, 27 July 2026 | 27 July 2026 | 27 Jul 2026 | 27/07/2026 |
| de | Montag, 27. Juli 2026 | 27. Juli 2026 | 27.07.2026 | 27.07.26 |
| fr | lundi 27 juillet 2026 | 27 juillet 2026 | 27 juil. 2026 | 27/07/2026 |
| es | lunes, 27 de julio de 2026 | 27 de julio de 2026 | 27 jul 2026 | 27/7/26 |
| it | lunedì 27 luglio 2026 | 27 luglio 2026 | 27 lug 2026 | 27/07/26 |
| pt-BR | segunda-feira, 27 de julho de 2026 | 27 de julho de 2026 | 27 de jul. de 2026 | 27/07/2026 |
| pt-PT | segunda-feira, 27 de julho de 2026 | 27 de julho de 2026 | 27/07/2026 | 27/07/26 |
| nl | maandag 27 juli 2026 | 27 juli 2026 | 27 jul 2026 | 27-07-2026 |
| sv | måndag 27 juli 2026 | 27 juli 2026 | 27 juli 2026 | 2026-07-27 |
| pl | poniedziałek, 27 lipca 2026 | 27 lipca 2026 | 27 lip 2026 | 27.07.2026 |
| tr | 27 Temmuz 2026 Pazartesi | 27 Temmuz 2026 | 27 Tem 2026 | 27.07.2026 |
| ru | понедельник, 27 июля 2026 г. | 27 июля 2026 г. | 27 июл. 2026 г. | 27.07.2026 |
| ja | 2026年7月27日月曜日 | 2026年7月27日 | 2026/07/27 | 2026/07/27 |
| ko | 2026년 7월 27일 월요일 | 2026년 7월 27일 | 2026. 7. 27. | 26. 7. 27. |
| zh | 2026年7月27日星期一 | 2026年7月27日 | 2026年7月27日 | 2026/7/27 |
| hi | सोमवार, 27 जुलाई 2026 | 27 जुलाई 2026 | 27 जुल॰ 2026 | 27/7/26 |
| th | วันจันทร์ที่ 27 กรกฎาคม ค.ศ. 2026 | 27 กรกฎาคม ค.ศ. 2026 | 27 ก.ค. 2026 | 27/7/26 |
| fi | maanantaina 27. heinäkuuta 2026 | 27. heinäkuuta 2026 | 27.7.2026 | 27.7.2026 |
| ar-EG | الاثنين، ٢٧ يوليو ٢٠٢٦ | ٢٧ يوليو ٢٠٢٦ | ٢٧‏/٠٧‏/٢٠٢٦ | ٢٧‏/٧‏/٢٠٢٦ |
| fa | دوشنبه ۲۷ ژوئیهٔ ۲۰۲۶ | ۲۷ ژوئیهٔ ۲۰۲۶ | ۲۷ ژوئیه ۲۰۲۶ | ۲۰۲۶/۷/۲۷ |
| bn | সোমবার, ২৭ জুলাই, ২০২৬ | ২৭ জুলাই, ২০২৬ | ২৭ জুল, ২০২৬ | ২৭/৭/২৬ |

The Arabic short formats contain invisible right-to-left marks (U+200F)
between the numbers, exactly as CLDR specifies them.

Dates before the common era format with the era year: year 0 is 1 BCE, year
-1 is 2 BCE. The standard patterns of most locales do not include an era
field, so `LocalDate(0, 1, 1)` in `en` comes out as `January 1, 1` at LONG.

## LocalTime.format

```kotlin
fun LocalTime.format(style: FormatStyle, locale: Locale): String
```

Output for 15:05:09:

| Locale | FULL | LONG | MEDIUM | SHORT |
| --- | --- | --- | --- | --- |
| en | 3:05:09 PM | 3:05:09 PM | 3:05:09 PM | 3:05 PM |
| de | 15:05:09 | 15:05:09 | 15:05:09 | 15:05 |
| ja | 15時05分09秒 | 15:05:09 | 15:05:09 | 15:05 |
| ko | 오후 3시 5분 9초 | 오후 3시 5분 9초 | 오후 3:05:09 | 오후 3:05 |
| fi | 15.05.09 | 15.05.09 | 15.05.09 | 15.05 |
| zh-Hant | 下午3:05:09 | 下午3:05:09 | 下午3:05:09 | 下午3:05 |
| ar-EG | ٣:٠٥:٠٩ م | ٣:٠٥:٠٩ م | ٣:٠٥:٠٩ م | ٣:٠٥ م |

Three things to know:

- CLDR's FULL and LONG time patterns end in a time-zone name (`zzzz`, `z`).
  A `LocalTime` has no zone, so the library drops those fields and the
  whitespace around them, including the brackets in patterns like zh-Hant's
  `Bh:mm:ss [zzzz]`. That is why FULL, LONG and MEDIUM look identical in
  many locales here.
- Twelve-hour locales handle noon and midnight the CLDR way: 00:30 is
  `12:30 AM` and 12:30 is `12:30 PM` in `en`. The separator before AM/PM is
  U+202F (narrow no-break space), not a regular space, matching CLDR 48.
- The 下午 in the zh-Hant row is not a plain PM marker but a flexible
  [day period](#day-periods) that changes across the day.

## Day periods

CLDR time patterns can mark the part of day in three ways, and the formatter
implements all three pattern fields:

- `a` is plain AM/PM.
- `b` is AM/PM, except that exactly 00:00:00 and 12:00:00 use the locale's
  midnight and noon names when it has them: `12:00 noon` in `en`. German has
  a name for midnight but none for noon, so 12:00 stays `PM`. One second past
  the mark and the field is back to plain AM/PM.
- `B` is the flexible day period: the period picked by the locale's rules in
  CLDR's `dayPeriods.xml`, named things like `in the afternoon` (en),
  `abends` (de) or `晚上` (zh). Period boundaries are locale-specific:
  night runs 21:00 to 24:00 in `en` but 22:00 to 04:00 in `ru`, wrapping
  past midnight.

You never write these fields yourself; they matter because they occur in the
standard patterns that `LocalTime.format` uses. Traditional Chinese is the
locale family whose standard time patterns use `B` (`Bh:mm`), so its output
changes across the day:

| Time | zh-Hant SHORT | Period |
| --- | --- | --- |
| 00:00 | 午夜12:00 | midnight (exact time only) |
| 02:05 | 凌晨2:05 | night |
| 06:05 | 清晨6:05 | early morning |
| 09:05 | 上午9:05 | morning |
| 12:05 | 中午12:05 | midday |
| 15:05 | 下午3:05 | afternoon |
| 20:05 | 晚上8:05 | evening |

A day period the locale has no name for falls back to AM/PM, as UTS #35
specifies, so `B` and `b` always produce something. Names come from the
abbreviated format width, the same width the `a` field uses.

## LocalDateTime.format

```kotlin
fun LocalDateTime.format(dateStyle: FormatStyle, timeStyle: FormatStyle, locale: Locale): String
fun LocalDateTime.format(style: FormatStyle, locale: Locale): String  // same style for both
```

The date part and time part are formatted separately, then combined with the
locale's glue pattern (`{1}, {0}` in English, `{1} {0}` in Japanese):

| Locale | FULL | SHORT |
| --- | --- | --- |
| en | Monday, July 27, 2026, 3:05:09 PM | 7/27/26, 3:05 PM |
| en-GB | Monday, 27 July 2026, 15:05:09 | 27/07/2026, 15:05 |
| de | Montag, 27. Juli 2026, 15:05:09 | 27.07.26, 15:05 |
| ja | 2026年7月27日月曜日 15時05分09秒 | 2026/07/27 15:05 |
| pt-BR | segunda-feira, 27 de julho de 2026 15:05:09 | 27/07/2026 15:05 |

Mixing styles is common in interfaces that want a readable date with a compact
time:

```kotlin
dateTime.format(FormatStyle.LONG, FormatStyle.SHORT, Locale.forLanguageTag("en"))
// "July 27, 2026, 3:05 PM"

dateTime.format(FormatStyle.FULL, FormatStyle.SHORT, Locale.forLanguageTag("fr"))
// "lundi 27 juillet 2026, 15:05"
```

The glue pattern is chosen by the date style.

## Month.displayName

```kotlin
fun Month.displayName(style: TextStyle, locale: Locale): String
```

Names come from CLDR's "format" context, the one meant for use inside a
sentence or a formatted date. In languages with grammatical case this is the
inflected form: Russian July is `июля` (genitive), which is what belongs in
"27 июля", not the nominative `июль`.

| Locale | FULL | ABBREVIATED | NARROW |
| --- | --- | --- | --- |
| en | July | Jul | J |
| pt-BR | julho | jul. | J |
| fr | juillet | juil. | J |
| de | Juli | Juli | J |
| ru | июля | июл. | И |
| ja | 7月 | 7月 | 7 |
| zh | 七月 | 7月 | 7 |

## DayOfWeek.displayName

```kotlin
fun DayOfWeek.displayName(style: TextStyle, locale: Locale): String
```

| Locale | FULL | ABBREVIATED | NARROW |
| --- | --- | --- | --- |
| en | Monday | Mon | M |
| de | Montag | Mo. | M |
| pt-BR | segunda-feira | seg. | S |
| es | lunes | lun | L |
| zh | 星期一 | 周一 | 一 |
| ru | понедельник | пн | П |

## Numbering systems

Each locale carries the digits of its default numbering system, and every
number the formatter writes goes through them. Latin-digit locales are
unaffected. Locales with another default produce their own digits everywhere,
including in patterns, day numbers and years:

```kotlin
date.format(FormatStyle.LONG, Locale.forLanguageTag("ar-EG"))  // ٢٧ يوليو ٢٠٢٦
date.format(FormatStyle.SHORT, Locale.forLanguageTag("fa"))    // ۲۰۲۶/۷/۲۷
date.format(FormatStyle.SHORT, Locale.forLanguageTag("bn"))    // ২৭/৭/২৬
```

One consequence of CLDR 48 that surprises people: plain `ar` defaults to
Latin digits. The Arabic-Indic digits shown above come from `ar-EG` and other
regional Arabic locales.

## Country

```kotlin
// kotlinx-locale-country-types, -core and -cldr-full
import dev.carcara.kotlinx.locale.country.*
import dev.carcara.kotlinx.locale.country.cldr.*
```

An enum of the 249 officially assigned ISO 3166-1 countries, keyed by alpha-2
code, so `Country.BR` is Brazil and works in `when` exhaustively. CLDR-only
region codes are deliberately excluded: no macroregions (`419`, `EU`), no
exceptionally reserved codes (`AC`, `IC`, `TA`) and no user-assigned codes
(`XK`).

| Member | Example for `Country.US` |
| --- | --- |
| `alpha2` | `"US"` (same as `name`) |
| `alpha3` | `"USA"` |
| `numericCode` | `840` |
| `displayName(locale)` | `"United States"` |

### Mapping between representations

Every representation converts to every other. The `OrNull` variants return
null on unknown input; the plain variants throw `IllegalArgumentException`.
Code lookups are case-insensitive.

```kotlin
Country.forAlpha2("br")            // Country.BR
Country.forAlpha3("DEU")           // Country.DE
Country.forNumericCode(392)        // Country.JP
Country.forAlpha2OrNull("XX")      // null

Country.forLocaleOrNull(Locale.forLanguageTag("pt-BR"))   // Country.BR
Country.forLocaleOrNull(Locale.forLanguageTag("pt"))      // null (no region)

// Reverse lookup by localized name, case-insensitive.
Country.forDisplayNameOrNull("United States")                              // Country.US
Country.forDisplayNameOrNull("Estados Unidos", Locale.forLanguageTag("pt")) // Country.US
```

### Localized names

`displayName` resolves the CLDR territory name through the locale's
inheritance chain, including CLDR `parentLocales` overrides:

```kotlin
val us = Country.US
us.displayName(Locale.forLanguageTag("en"))     // United States
us.displayName(Locale.forLanguageTag("pt-BR"))  // Estados Unidos
us.displayName(Locale.forLanguageTag("ja"))     // アメリカ合衆国
us.displayName(Locale.forLanguageTag("zh"))     // 美国

// es-AR inherits from es-419, which renames some countries relative to es:
Country.CI.displayName(Locale.forLanguageTag("es"))     // Côte d’Ivoire
Country.CI.displayName(Locale.forLanguageTag("es-AR"))  // Costa de Marfil
```

CLDR root carries no country names, so a locale with no data anywhere in its
chain falls back to the alpha-2 code. `displayName` defaults its argument to
`Locale.current`, as do all locale-taking functions in these modules.

## Currency

```kotlin
// kotlinx-locale-currency-types, -core and -cldr-full
import dev.carcara.kotlinx.locale.currency.*
import dev.carcara.kotlinx.locale.currency.cldr.*
```

An enum of the 178 active ISO 4217 currencies, keyed by alphabetic code. The
set includes the fund codes (`USN`, `CLF`), the precious metals (`XAU`,
`XPT`) and the special codes (`XXX`, `XDR`), the same coverage as
`java.util.Currency`.

Each entry carries both what ISO defines and what CLDR does when formatting,
because the two intentionally disagree for some currencies:

| Member | Meaning | USD | JPY | BHD | ALL | XAU |
| --- | --- | --- | --- | --- | --- | --- |
| `numericCode` | ISO 4217 numeric code | 840 | 392 | 48 | 8 | 959 |
| `defaultFractionDigits` | ISO minor units, -1 when N.A. | 2 | 0 | 3 | 2 | -1 |
| `cldrFractionDigits` | digits CLDR formats | 2 | 0 | 3 | 0 | 2 |
| `minorUnitDigits` | digits of minor-unit amounts (ISO, or 0 when N.A.) | 2 | 0 | 3 | 2 | 0 |

The Albanian lek is the interesting column: ISO says two decimals, CLDR
formats none. The cash cases work the same way: `cldrCashFractionDigits` and
the rounding increments `cldrRoundingIncrement` and `cldrCashRoundingIncrement`
describe how CLDR rounds cash amounts. CHF cash rounds to 0.05, DKK to 0.50,
and AMD drops the decimals entirely.

### Converting between the ISO and CLDR scales

```kotlin
// ALL: ISO 2 decimals, CLDR 0. Rounding is half-even.
Currency.ALL.isoToCldrUnits(12345)   // 123   (123.45 lekë -> 123)
Currency.ALL.isoToCldrUnits(12350)   // 124   (tie: 123 is odd, round away)
Currency.ALL.cldrToIsoUnits(123)     // 12300

// Currencies where the scales agree pass values through unchanged.
Currency.USD.isoToCldrUnits(1234)    // 1234
```

### Lookups

```kotlin
Currency.forCode("usd")           // Currency.USD (case-insensitive)
Currency.forNumericCode(978)      // Currency.EUR
Currency.forCodeOrNull("ZZZ")     // null

// Country mapping, from CLDR's legal-tender data:
Currency.forCountryOrNull(Country.DE)                     // Currency.EUR
Currency.forLocaleOrNull(Locale.forLanguageTag("pt-BR"))  // Currency.BRL

Country.US.currency     // Currency.USD (extension property)
Country.PA.currencies   // [PAB, USD]   (multi-currency countries, preferred first)
Country.AQ.currency     // null         (Antarctica has no universal currency)
```

### Symbols and names

```kotlin
Currency.USD.symbol(Locale.forLanguageTag("en"))     // $
Currency.USD.symbol(Locale.forLanguageTag("pt-BR"))  // US$
Currency.JPY.symbol(Locale.forLanguageTag("ja"))     // ￥ (fullwidth; en uses ¥)
Currency.CHF.symbol(Locale.forLanguageTag("de-CH"))  // CHF (no symbol -> the code)

Currency.USD.displayName(Locale.forLanguageTag("en"))     // US Dollar
Currency.USD.displayName(Locale.forLanguageTag("pt-BR"))  // Dólar americano
Currency.EUR.displayName(Locale.forLanguageTag("es"))     // euro
```

Both resolve through the locale chain like country names and fall back to the
ISO code when CLDR has nothing.

## CurrencyAmount

```kotlin
public class CurrencyAmount(val currency: Currency, val minorUnits: Long)
```

A monetary amount as a currency plus a `Long` count of ISO minor units: cents
for USD, fils for BHD, whole yen for JPY. `CurrencyAmount(Currency.USD, 1234_56)`
is $1,234.56.

```kotlin
val price = CurrencyAmount.of(Currency.USD, 12, 50)   // 12.50
price.majorUnits            // 12
price.minorPart             // 50
price.toDecimalString()     // "12.50"
price.toString()            // "USD 12.50"

CurrencyAmount.parse(Currency.USD, "12.5")    // 12.50
CurrencyAmount.parseOrNull(Currency.USD, "12.345")  // null (too many decimals)

// Arithmetic stays within one currency; mixing currencies throws.
val total = price + CurrencyAmount(Currency.USD, 100)   // 13.50
-total                                                   // -13.50
price < total                                            // true
```

`toDecimalString` and `parse` speak plain ISO decimals (`-12.50`), useful for
serialization. Negative amounts carry the sign on both `majorUnits` and
`minorPart`.

### Parsing formatted strings

```kotlin
fun CurrencyAmount.Companion.parseFormattedOrNull(
    currency: Currency,
    text: String,
    locale: Locale = Locale.current,
): CurrencyAmount?   // parseFormatted throws instead
```

The reverse of `format`: reads a CLDR-formatted string with the locale's
separators, digits and currency symbol. The printed number is taken at face
value and scaled to ISO minor units, so CLDR's reduced formatting digits do
not distort the result:

```kotlin
val ptBR = Locale.forLanguageTag("pt-BR")
CurrencyAmount.parseFormatted(Currency.BRL, "R$ 1.234,56", ptBR).minorUnits  // 123456

// HUF formats with 0 decimals (CLDR) but has 2 ISO decimals:
val hu = Locale.forLanguageTag("hu")
CurrencyAmount.parseFormatted(Currency.HUF, "200 Ft", hu).minorUnits   // 20000
CurrencyAmount.parseFormatted(Currency.HUF, "200,50 Ft", hu).minorUnits // 20050

CurrencyAmount.parseFormatted(Currency.USD, "($1,234.56)", en).minorUnits  // -123456
CurrencyAmount.parseFormatted(Currency.EGP, "١٬٢٣٤٫٥٦", Locale.forLanguageTag("ar-EG"))
```

Parsing is lenient about placement (the currency may appear as its symbol,
ISO code or display name, anywhere or not at all, with any spacing) and
strict about content: leftover characters that are not digits or the locale's
separators fail the parse, as does a fraction ISO minor units cannot
represent (`"5.5"` for JPY). Negatives are recognized from the locale's minus
sign or accounting parentheses. `format` output round trips for every bundled
locale. The value comes back through the CLDR digit conversion, so a lossy
format like ALL's 0-digit rendering parses back to the printed value:
`"ALL 123"` gives 12300.

### Formatting

```kotlin
fun CurrencyAmount.format(
    locale: Locale = Locale.current,
    style: CurrencySymbolStyle = CurrencySymbolStyle.SYMBOL,  // or CODE
    accounting: Boolean = false,
    cash: Boolean = false,
): String
```

Real output for 123456 minor units (1,234.56) across locales:

| Locale | USD | EUR | JPY (1234) |
| --- | --- | --- | --- |
| en | $1,234.56 | €1,234.56 | ¥1,234 |
| de | 1.234,56 $ | 1.234,56 € | 1.234 ¥ |
| pt-BR | US$ 1.234,56 | € 1.234,56 | JP¥ 1.234 |
| de-CH | $ 1'234.56 | EUR 1'234.56 | ¥ 1'234 |
| fr | 1 234,56 $US | 1 234,56 € | 1 234 JPY |
| ar-EG | ‏١٬٢٣٤٫٥٦ US$ | ‏١٬٢٣٤٫٥٦ € | ‏١٬٢٣٤ JP¥ |

Symbols are locale-relative, exactly as CLDR sees the world: French writes
the US dollar as `$US`, Hindi writes yen as `JP¥`, and Swiss German uses the
plain code for the euro. The separator between an alphabetic symbol and the
number is U+00A0 (no-break space); French groups digits with U+202F. Grouping
follows the locale's pattern, including Indian lakh/crore grouping
(`₹1,23,456.78` in hi) and Spanish's minimum-grouping rule (`1000,00 €`, but
`10.000,00 €`).

The knobs:

```kotlin
val amount = CurrencyAmount(Currency.USD, -123456)
val en = Locale.forLanguageTag("en")

amount.format(en)                                       // -$1,234.56
amount.format(en, accounting = true)                    // ($1,234.56)
amount.format(en, style = CurrencySymbolStyle.CODE)     // -USD 1,234.56

// cash = true applies CLDR's cash digits and rounding increments:
CurrencyAmount(Currency.CHF, 1003).format(en, cash = true)   // CHF 10.05
CurrencyAmount(Currency.AMD, 12350).format(en, cash = true)  // AMD 124

// CLDR digits differ from ISO digits for some currencies (half-even):
CurrencyAmount(Currency.ALL, 12345).format(en)   // ALL 123
```

When CLDR provides an `alphaNextToNumber` pattern variant, it is used
automatically whenever the character next to the number would be a letter.
That is why `CHF 10.05` and `USD 1,234.56` get a space while `$1,234.56` does
not.

## Data sources

Every domain's data reaches you through an interface, and a `-cldr-full` module
is one implementation of it rather than the only possible one. Each convenience
extension above is a single line over a public source object, so the explicit
form is always there:

```kotlin
Country.BR.displayName(locale)                // convenience
CldrCountry.displayName(Country.BR, locale)   // exactly what it calls
```

That matters in two places the convenience form cannot serve.

**Testing without CLDR.** A test that needs `displayName` to return a known
string implements the interface instead of pinning a real CLDR value that a
data upgrade can change:

```kotlin
val fake = object : CountryNameSource {
    override val supportedLocales = setOf(Locale.of("en"))
    override fun countryNameOrNull(alpha2: String, locale: Locale) = "Testland"
}
fake.displayName(Country.BR, Locale.of("en"))   // Testland
```

**Composition.** The interfaces are partial — every lookup returns `null` where
the source has nothing — so a composing source can tell a miss from an answer:

```kotlin
val names = FallbackCountryNames(primary = MyOwnNames, fallback = CldrCountry)
names.displayName(Country.BR, locale)
```

There is one composer per interface: `FallbackCountryNames`,
`FallbackCurrencyNames`, `FallbackCurrencyFormats` and
`FallbackDateTimeFormats`. Each dispatches per lookup rather than per locale, so
a primary that knows a locale but not one entry within it still falls through
for that entry, and `supportedLocales` is the union of both.

The total operations `-core` layers over each interface supply the documented
fallback: the ISO code for country and currency names, and ISO 8601 for dates
and times, which is close to what CLDR root already prints.

**How the shipped sources are checked.** Both implementations of every contract
run through one conformance suite built from ICU fixtures. It lives in this
repository's `conformance-test-suite/` module and is not published: it exists so
that the CLDR source and the platform source of a domain are held to the same
assertions, not as a compliance kit for sources outside this build.

The suite runs at two tiers. `EXACT` is for the sources compiled from CLDR,
which are a second encoding of the data ICU encodes and must match it byte for
byte. `BEHAVIOURAL` is for the platform sources, whose data belongs to the host
and moves with OS versions; it checks that answers are well-shaped and round
trip, not what they say. Pinning platform data to a fixture would turn the test
into a report on the CI image.

### Choosing between the bundled and platform sources

Each domain ships two implementations of the same interfaces, and the extensions
above are the same names in different packages, so the choice is an import:

```kotlin
import dev.carcara.kotlinx.locale.country.cldr.displayName        // bundled tables
import dev.carcara.kotlinx.locale.country.platform.displayName    // the host
```

`-cldr-full` answers the same everywhere and costs what its tables weigh.
`-platform` ships nothing and answers whatever the device says, which means it
answers nothing at all on Linux, Windows, Android Native and Wasm-WASI, where no
locale data is reachable from Kotlin. Neither is the default; a `Fallback*`
composer is how you take the host's answer where there is one without giving up
a guaranteed answer:

```kotlin
val names = FallbackCountryNames(primary = PlatformCountry, fallback = CldrCountry)
```

What the choice costs is measured rather than argued: 20.2 KB against 416.9 KB
gzipped for country on Kotlin/JS, and 45.0 KB against 823.7 KB for all three
domains at once. [`docs/size.md`](docs/size.md) has the whole table.

## The locale catalog

`kotlinx-locale-types` is optional and carries no translations. It generates one
enum per language, so a locale can be named rather than spelled:

```kotlin
Locale.forLanguageTag("pt-BRA")   // compiles, throws at runtime
PT.BR.toLocale()                  // cannot be misspelled, autocompletes
```

Two levels, always `LANGUAGE.REST`: `PT.BR`, `ZH.HANS_CN`, `CA.ES_VALENCIA`. The
three CLDR macroregions are not valid Kotlin identifiers, so they take their
English region names: `AR.WORLD` for `ar-001`, `EN.EUROPE` for `en-150` and
`ES.LATIN_AMERICA` for `es-419`.

The bare language is the language itself, with no member name: `PT` is `pt`, the
way `PT.BR` is `pt-BR`. It comes from the enum's companion, which implements
`LocaleRef` too, so `PT.entries` covers the regions and `PT` covers the language
they sit under. Where the two names collide the region still wins the member
slot: `PT.PT` is `pt-PT`.

Its reason to exist is the Gradle plugin, whose configuration is a locale set: a
typo there does not throw, it quietly generates data for one locale fewer than
intended. Nothing requires it in application code, and `Locale.forLanguageTag`
stays the zero-cost path for tags built at runtime.

## Errors, guarantees and versions

`format`, `displayName` and `symbol` never throw for any `Locale`: an unknown
locale falls back along the chain in [Fallback resolution](#fallback-resolution)
and ends at CLDR root (names additionally fall back to the ISO code). The
throwing entry points are `Locale.of`, `Locale.forLanguageTag`, the non-`OrNull`
code lookups on `Country` and `Currency`, the `CurrencyAmount` parse and `of`
functions, and `CurrencyAmount` arithmetic across two different currencies.
All of them throw `IllegalArgumentException`, and every lookup that can fail
has a non-throwing alternative.

All types are immutable and safe to share between threads. Formatting
allocates its working state per call and touches no global mutable data.

The bundled data comes from CLDR `release-48-2` plus, for currency identity
(numeric codes and ISO minor units), the official ISO 4217 list one published
2026-01-01. Test fixtures and the ISO 4217 numeric cross-check come from ICU
`release-78.3`. Regeneration instructions are in the
[README](README.md#where-the-data-comes-from).
