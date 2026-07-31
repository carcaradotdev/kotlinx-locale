# API reference

Every public declaration, with its Kotlin signature, what it does, and an
example. Declarations are grouped by the artifact that ships them.

Each domain is three artifacts to depend on and two packages to import. The type
and the contract share a package; the implementation gets its own, so that two
implementations can sit on one classpath without silently resolving by classpath
order.

```kotlin
// kotlinx-locale-core
import dev.carcara.kotlinx.locale.*

// kotlinx-locale-types
import dev.carcara.kotlinx.locale.catalog.*

// kotlinx-locale-datetime-core, then one implementation
import dev.carcara.kotlinx.locale.datetime.*
import dev.carcara.kotlinx.locale.datetime.cldr.*       // or .datetime.platform.*

// kotlinx-locale-datetime-cldr-skeletons, for skeleton formatting
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.*

// kotlinx-locale-country-types and -core, then one implementation
import dev.carcara.kotlinx.locale.country.*
import dev.carcara.kotlinx.locale.country.cldr.*        // or .country.platform.*

// kotlinx-locale-currency-types and -core, then one implementation
import dev.carcara.kotlinx.locale.currency.*
import dev.carcara.kotlinx.locale.currency.cldr.*       // or .currency.platform.*
```

Generated types carry only their per-entry data. Everything else about them is
an extension, so `Country.BR.alpha3`, `Country.forAlpha3("BRA")` and
`Country.BR.displayName(locale)` are written identically even though they come
from three different artifacts.

Datetime is the one domain with a fourth artifact. `-cldr-skeletons` adds
[skeleton formatting](#skeletonformatsource) on top of the style-based API, and
is opt in because its tables are the larger half of the datetime data.

All date and time examples below are real output for 2026-07-27, a Monday, at
15:05:09.

## Contents

- [kotlinx-locale-core](#kotlinx-locale-core)
- [kotlinx-locale-platform](#kotlinx-locale-platform)
- [kotlinx-locale-types](#kotlinx-locale-types)
- [Date and time](#date-and-time)
- [Country](#country)
- [Currency](#currency)
- [Composing and replacing sources](#composing-and-replacing-sources)
- [Gradle plugin](#gradle-plugin)
- [Errors, guarantees and versions](#errors-guarantees-and-versions)

## kotlinx-locale-core

Package `dev.carcara.kotlinx.locale`.

### Locale

```kotlin
public class Locale {
    public val language: String
    public val script: String?
    public val region: String?
    public val variant: String?
}
```

An immutable Unicode locale identifier with four normalized parts. There is no
public constructor; use `Locale.of` or `Locale.forLanguageTag`.

| Property | Example | Normalization |
| --- | --- | --- |
| `language` | `"pt"` | lowercase, 2 to 8 letters, required |
| `script` | `"Cyrl"` | title case, 4 letters |
| `region` | `"BR"` | uppercase, 2 letters or 3 digits |
| `variant` | `"valencia"` | lowercase |

Two locales are equal when all four parts are equal, so `Locale` works as a map
key. `hashCode` follows, and `toString()` returns `toLanguageTag()`.

```kotlin
val locale = Locale.forLanguageTag("sr-Cyrl-BA")
locale.language     // "sr"
locale.script       // "Cyrl"
locale.region       // "BA"
locale.variant      // null
```

### Locale.of

```kotlin
public fun Locale.Companion.of(
    language: String,
    script: String? = null,
    region: String? = null,
    variant: String? = null,
): Locale
```

Builds a locale from individual subtags, normalizing case. Throws
`IllegalArgumentException` when a part does not fit its shape, for example a
three-letter script or a one-letter language. Legacy language codes are mapped
to their modern forms here too.

```kotlin
Locale.of("en")
Locale.of("en", region = "GB")
Locale.of("sr", script = "Cyrl", region = "BA")
Locale.of("EN", "latn", "gb").toLanguageTag()   // "en-Latn-GB"
Locale.of("x")                                  // throws IllegalArgumentException
```

### Locale.forLanguageTag and forLanguageTagOrNull

```kotlin
public fun Locale.Companion.forLanguageTag(tag: String): Locale
public fun Locale.Companion.forLanguageTagOrNull(tag: String): Locale?
```

Parses a language tag. `forLanguageTag` throws `IllegalArgumentException` when
no language subtag can be extracted; `forLanguageTagOrNull` returns null.

Parsing is lenient on purpose, because its main job is digesting whatever a
platform reports. It accepts `-` or `_` as separators, cuts everything after `.`
or `@` (POSIX encoding and modifier suffixes), stops at the first single-letter
subtag (BCP 47 extensions), and maps the legacy codes `iw`, `in`, `ji`, `mo` and
`tl` to `he`, `id`, `yi`, `ro` and `fil`.

| Input | `toLanguageTag()` |
| --- | --- |
| `pt-BR` | `pt-BR` |
| `PT_br.UTF-8@latin` | `pt-BR` |
| `sr-Cyrl-BA` | `sr-Cyrl-BA` |
| `en-US-u-ca-japanese` | `en-US` |
| `ca-ES-VALENCIA` | `ca-ES-valencia` |
| `in-ID` | `id-ID` |
| `iw` | `he` |

```kotlin
Locale.forLanguageTag("pt-BR")
Locale.forLanguageTagOrNull("not a tag!")   // null
Locale.forLanguageTagOrNull("POSIX")        // null
Locale.forLanguageTagOrNull("123")          // null
```

### Locale.current

```kotlin
public val Locale.Companion.current: Locale
```

Reads the platform's locale tag and parses it with the rules above. When the
platform exposes nothing (Wasm-WASI) or reports something unparseable, you get
`Locale.of("en")`, so this never throws and never returns an unusable value. The
per-platform sources are listed in the
[README](README.md#supported-platforms).

```kotlin
val locale = Locale.current
```

### Locale.toLanguageTag

```kotlin
public fun Locale.toLanguageTag(): String
```

The canonical BCP 47 tag, with the four parts joined by hyphens and nulls
skipped.

```kotlin
Locale.of("zh", script = "Hans", region = "CN").toLanguageTag()   // "zh-Hans-CN"
```

### LocaleDataSource

```kotlin
public interface LocaleDataSource {
    public val supportedLocales: Set<Locale>
}
```

The root of every source interface. `supportedLocales` reports which locales the
source carries data for.

An empty set means the source cannot enumerate what it supports, not that it
supports nothing. A platform source is why the distinction exists: ECMA-402 will
filter a list of locales you already have but offers no way to ask for the list,
so a source over `Intl` answers any lookup while being unable to describe its
own coverage. Treat this as a report, not a precondition. Asking for a locale
outside the set is always allowed, and the documented fallbacks apply.

```kotlin
CldrCountry.supportedLocales.size    // 1121
CldrCurrency.supportedLocales.size   // 1121
CldrDateTime.supportedLocales.size   // 1121
PlatformCountry.supportedLocales     // [] on JS, where Intl cannot enumerate
```

### LocaleRef and toLocale

```kotlin
public interface LocaleRef {
    public val tag: String
}

public fun LocaleRef.toLocale(): Locale
```

A compile-time name for one locale, implemented by every enum in
`kotlinx-locale-types`. `toLocale()` parses the tag.

```kotlin
PT.BR.tag           // "pt-BR"
PT.BR.toLocale()    // Locale.forLanguageTag("pt-BR")
PT.tag              // "pt", the companion is the bare language
```

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
// "Jul 27, 2026"        no data for en-XX, falls back to en

date.format(FormatStyle.SHORT, Locale.of("zz"))
// "2026-07-27"          unknown language, root patterns are ISO-like

date.format(FormatStyle.FULL, Locale.of("zz"))
// "2026 M07 27, Mon"    root has placeholder names, not English ones
```

Regional inheritance is baked into the data at generation time, so `en-AU`
behaves like British English rather than American English even though the list
above never mentions `en-001`.

### Internal API

```kotlin
@RequiresOptIn public annotation class InternalKotlinxLocaleApi

@InternalKotlinxLocaleApi public fun Locale.dataLookupTags(): List<String>
```

`dataLookupTags` returns the candidate tags above, most specific first, without
`root`. It exists so the formatter modules can share one resolution rule.
Everything in `dev.carcara.kotlinx.locale.internal` carries the same annotation:
`FIELD_SEPARATOR`, `ENTRY_SEPARATOR`, `KEY_SEPARATOR`, `supportedLocalesOf`,
`resolvedRecord` and `sparseRecordValue`. None of it has compatibility
guarantees for general use.

## kotlinx-locale-platform

Package `dev.carcara.kotlinx.locale.platform`.

### PlatformLocaleData

```kotlin
@InternalKotlinxLocaleApi
public expect object PlatformLocaleData {
    public val isAvailable: Boolean
    public fun availableLocaleTags(): Set<String>
}
```

What the host can say about locales before any domain is involved. Two
questions, because the answers are independent: a target can have no locale data
at all (Linux, Windows, Android Native and WASI), and a target can have plenty
while being unable to list it (`Intl`).

Tags are raw platform identifiers, so they may use `_` rather than `-`;
`Locale.forLanguageTagOrNull` accepts both.

```kotlin
@OptIn(InternalKotlinxLocaleApi::class)
fun report() {
    if (PlatformLocaleData.isAvailable) {
        println(PlatformLocaleData.availableLocaleTags().size)   // 800+ on a JVM
    }
}
```

## kotlinx-locale-types

Package `dev.carcara.kotlinx.locale.catalog`.

322 generated enums, one per CLDR language, together carrying 799 locale
references. Each implements `LocaleRef`, and so does its companion.

```kotlin
public enum class PT(override val tag: String) : LocaleRef {
    AO("pt-AO"), BR("pt-BR"), CH("pt-CH"), /* ... */ ;
    public companion object : LocaleRef {
        override val tag: String = "pt"
    }
}
```

Always two levels, `LANGUAGE.REST`: `PT.BR`, `ZH.HANS_CN`, `CA.ES_VALENCIA`. The
bare language is the companion, so `PT` is `pt` the way `PT.BR` is `pt-BR`. Where
the two names collide the region still wins the member slot, so `PT.PT` is
`pt-PT`. The three CLDR macroregions are not valid Kotlin identifiers and take
their English region names: `AR.WORLD` for `ar-001`, `EN.EUROPE` for `en-150`
and `ES.LATIN_AMERICA` for `es-419`.

```kotlin
Locale.forLanguageTag("pt-BRA")   // compiles, throws at runtime
PT.BR.toLocale()                  // cannot be misspelled, autocompletes
PT.entries                        // every pt-* locale CLDR ships
```

The artifact carries no translations. Its reason to exist is the Gradle plugin,
whose configuration is a locale set: a typo there does not throw, it quietly
generates data for one locale fewer than intended. Nothing requires it in
application code, and `Locale.forLanguageTag` stays the zero-cost path for tags
built at runtime.

## Date and time

### FormatStyle

```kotlin
public enum class FormatStyle { FULL, LONG, MEDIUM, SHORT }
```

The four CLDR standard lengths, used by every `format` function.

| Value | en date | de date | ja date |
| --- | --- | --- | --- |
| `FULL` | Monday, July 27, 2026 | Montag, 27. Juli 2026 | 2026年7月27日月曜日 |
| `LONG` | July 27, 2026 | 27. Juli 2026 | 2026年7月27日 |
| `MEDIUM` | Jul 27, 2026 | 27.07.2026 | 2026/07/27 |
| `SHORT` | 7/27/26 | 27.07.26 | 2026/07/27 |

`FULL` usually includes the weekday and `SHORT` is fully numeric. Some locales
use one pattern for adjacent lengths, which is why Japanese medium and short
dates are both `2026/07/27`.

### TextStyle

```kotlin
public enum class TextStyle { FULL, ABBREVIATED, NARROW }
```

Widths for month and weekday names: wide, short, and the narrowest form.

| Value | Example (en, July) |
| --- | --- |
| `FULL` | `July` |
| `ABBREVIATED` | `Jul` |
| `NARROW` | `J` |

Narrow names are not unique within a locale (in English, January, June and July
are all `J`), so they suit column headers rather than parsing or lookup.

### DateTimeFormatSource

```kotlin
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
```

A source that renders dates, times and calendar names in a locale's conventions.
Every method returns null where the source has nothing, which is what lets a
composing source tell a miss from an answer.

Months are numbered 1 to 12 and days of week 1 (Monday) to 7, matching
`Month.number` and `DayOfWeek.isoDayNumber`.

The interface is shaped around the operation rather than around the tables CLDR
stores, because the platforms cannot supply tables: `Intl` does not hand out
CLDR patterns and `NSDateFormatter` derives patterns from templates instead of
exposing the localized standard ones. Both can format, so formatting is what the
contract asks for.

```kotlin
CldrDateTime.formatDateOrNull(date, FormatStyle.LONG, Locale.of("de"))  // "27. Juli 2026"
PlatformDateTime.formatDateOrNull(date, FormatStyle.LONG, Locale.of("de"))  // null on Linux
```

### DateTimeFormatSource.format

```kotlin
public fun DateTimeFormatSource.format(date: LocalDate, style: FormatStyle, locale: Locale): String
public fun DateTimeFormatSource.format(time: LocalTime, style: FormatStyle, locale: Locale): String
public fun DateTimeFormatSource.format(
    dateTime: LocalDateTime,
    dateStyle: FormatStyle,
    timeStyle: FormatStyle,
    locale: Locale,
): String
```

The total operations over the nullable lookups. A date has no code to degrade
to, so a source with nothing for this locale falls back to ISO 8601. That is
close to what CLDR root already produces (`2026-07-27` at SHORT), and a source
generated by the Gradle plugin never reaches it because the plugin requires a
fallback locale.

```kotlin
CldrDateTime.format(date, FormatStyle.LONG, Locale.of("fr"))   // "27 juillet 2026"
```

### DateTimeFormatSource.displayName

```kotlin
public fun DateTimeFormatSource.displayName(month: Month, style: TextStyle, locale: Locale): String
public fun DateTimeFormatSource.displayName(dayOfWeek: DayOfWeek, style: TextStyle, locale: Locale): String
```

The localized name, falling back to the English enum name when the source has
nothing.

```kotlin
CldrDateTime.displayName(Month.JULY, TextStyle.FULL, Locale.of("ru"))   // "июля"
```

### FallbackDateTimeFormats

```kotlin
public class FallbackDateTimeFormats(
    primary: DateTimeFormatSource,
    fallback: DateTimeFormatSource,
) : DateTimeFormatSource
```

Answers from `primary`, and from `fallback` wherever primary has nothing.
Dispatch is per lookup rather than per locale, and `supportedLocales` is the
union of both.

```kotlin
val dates = FallbackDateTimeFormats(primary = PlatformDateTime, fallback = CldrDateTime)
dates.format(date, FormatStyle.LONG, Locale.of("de"))
```

### CldrDateTime

```kotlin
public object CldrDateTime : DateTimeFormatSource
```

Ships in `kotlinx-locale-datetime-cldr-full`, package
`dev.carcara.kotlinx.locale.datetime.cldr`. The CLDR pattern data for all 1121
locales. The parser and formatter live in `-cldr-runtime`; this object
contributes the table.

```kotlin
CldrDateTime.supportedLocales.size   // 1121
```

### LocalDate.format

```kotlin
public fun LocalDate.format(style: FormatStyle, locale: Locale): String
```

Formats the date with the locale's standard date pattern of that length.

```kotlin
LocalDate(2026, 7, 27).format(FormatStyle.LONG, Locale.forLanguageTag("pt-BR"))
// "27 de julho de 2026"
```

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

The Arabic short formats contain invisible right-to-left marks (U+200F) between
the numbers, exactly as CLDR specifies them.

Dates before the common era format with the era year: year 0 is 1 BCE, year -1
is 2 BCE. Most locales' standard patterns have no era field, so `LocalDate(0, 1, 1)`
in `en` comes out as `January 1, 1` at LONG.

### LocalTime.format

```kotlin
public fun LocalTime.format(style: FormatStyle, locale: Locale): String
```

Formats the time with the locale's standard time pattern of that length.

```kotlin
LocalTime(15, 5, 9).format(FormatStyle.SHORT, Locale.forLanguageTag("ko"))
// "오후 3:05"
```

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

CLDR's FULL and LONG time patterns end in a time-zone name (`zzzz`, `z`). A
`LocalTime` has no zone, so the library drops those fields and the whitespace
around them, including the brackets in patterns like zh-Hant's `Bh:mm:ss [zzzz]`.
That is why FULL, LONG and MEDIUM look identical in many locales here.

Twelve-hour locales handle noon and midnight the CLDR way: 00:30 is `12:30 AM`
and 12:30 is `12:30 PM` in `en`. The separator before AM and PM is U+202F, a
narrow no-break space, not a regular space, matching CLDR 48.

The 下午 in the zh-Hant row is not a plain PM marker but a flexible
[day period](#day-periods) that changes across the day.

### LocalDateTime.format

```kotlin
public fun LocalDateTime.format(dateStyle: FormatStyle, timeStyle: FormatStyle, locale: Locale): String
public fun LocalDateTime.format(style: FormatStyle, locale: Locale): String
```

The date part and the time part are formatted separately, then joined with the
locale's glue pattern (`{1}, {0}` in English, `{1} {0}` in Japanese). The glue
pattern is chosen by the date style. The single-style overload uses one length
for both.

```kotlin
dateTime.format(FormatStyle.LONG, FormatStyle.SHORT, Locale.forLanguageTag("en"))
// "July 27, 2026, 3:05 PM"

dateTime.format(FormatStyle.FULL, FormatStyle.SHORT, Locale.forLanguageTag("fr"))
// "lundi 27 juillet 2026, 15:05"
```

| Locale | FULL | SHORT |
| --- | --- | --- |
| en | Monday, July 27, 2026, 3:05:09 PM | 7/27/26, 3:05 PM |
| en-GB | Monday, 27 July 2026, 15:05:09 | 27/07/2026, 15:05 |
| de | Montag, 27. Juli 2026, 15:05:09 | 27.07.26, 15:05 |
| ja | 2026年7月27日月曜日 15時05分09秒 | 2026/07/27 15:05 |
| pt-BR | segunda-feira, 27 de julho de 2026 15:05:09 | 27/07/2026 15:05 |

### Month.displayName

```kotlin
public fun Month.displayName(style: TextStyle, locale: Locale): String
```

Names come from CLDR's "format" context, the one meant for use inside a sentence
or a formatted date. In languages with grammatical case this is the inflected
form: Russian July is `июля`, the genitive that belongs in "27 июля", not the
nominative `июль`.

```kotlin
Month.JULY.displayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR"))   // "julho"
```

| Locale | FULL | ABBREVIATED | NARROW |
| --- | --- | --- | --- |
| en | July | Jul | J |
| pt-BR | julho | jul. | J |
| fr | juillet | juil. | J |
| de | Juli | Juli | J |
| ru | июля | июл. | И |
| ja | 7月 | 7月 | 7 |
| zh | 七月 | 7月 | 7 |

### DayOfWeek.displayName

```kotlin
public fun DayOfWeek.displayName(style: TextStyle, locale: Locale): String
```

The localized weekday name, in the same "format" context as months.

```kotlin
DayOfWeek.MONDAY.displayName(TextStyle.ABBREVIATED, Locale.forLanguageTag("de"))   // "Mo."
```

| Locale | FULL | ABBREVIATED | NARROW |
| --- | --- | --- | --- |
| en | Monday | Mon | M |
| de | Montag | Mo. | M |
| pt-BR | segunda-feira | seg. | S |
| es | lunes | lun | L |
| zh | 星期一 | 周一 | 一 |
| ru | понедельник | пн | П |

### Day periods

CLDR time patterns can mark the part of day in three ways, and the formatter
implements all three pattern fields.

`a` is plain AM and PM.

`b` is AM and PM, except that exactly 00:00:00 and 12:00:00 use the locale's
midnight and noon names when it has them, giving `12:00 noon` in `en`. German
has a name for midnight but none for noon, so 12:00 stays `PM`. One second past
the mark and the field is back to plain AM or PM.

`B` is the flexible day period: whatever the locale's rules in CLDR's
`dayPeriods.xml` pick, named things like `in the afternoon` (en), `abends` (de)
or `晚上` (zh). Boundaries are locale-specific. Night runs 21:00 to 24:00 in `en`
but 22:00 to 04:00 in `ru`, wrapping past midnight.

You never write these fields yourself. They matter because they occur in the
standard patterns `LocalTime.format` uses. Traditional Chinese is the locale
family whose standard time patterns use `B` (`Bh:mm`), so its output changes
across the day:

| Time | zh-Hant SHORT | Period |
| --- | --- | --- |
| 00:00 | 午夜12:00 | midnight, exact time only |
| 02:05 | 凌晨2:05 | night |
| 06:05 | 清晨6:05 | early morning |
| 09:05 | 上午9:05 | morning |
| 12:05 | 中午12:05 | midday |
| 15:05 | 下午3:05 | afternoon |
| 20:05 | 晚上8:05 | evening |

A day period the locale has no name for falls back to AM or PM, as UTS #35
specifies, so `B` and `b` always produce something. Names come from the
abbreviated format width, the same width the `a` field uses.

### Numbering systems

Each locale carries the digits of its default numbering system, and every number
the formatter writes goes through them. Latin-digit locales are unaffected.
Locales with another default produce their own digits everywhere, including in
patterns, day numbers and years.

```kotlin
date.format(FormatStyle.LONG, Locale.forLanguageTag("ar-EG"))  // ٢٧ يوليو ٢٠٢٦
date.format(FormatStyle.SHORT, Locale.forLanguageTag("fa"))    // ۲۰۲۶/۷/۲۷
date.format(FormatStyle.SHORT, Locale.forLanguageTag("bn"))    // ২৭/৭/২৬
```

One consequence of CLDR 48 that surprises people: plain `ar` defaults to Latin
digits. The Arabic-Indic digits above come from `ar-EG` and other regional
Arabic locales.

### PlatformDateTime

```kotlin
public object PlatformDateTime : DateTimeFormatSource {
    public val isAvailable: Boolean
}

public fun LocalDate.format(style: FormatStyle, locale: Locale): String
public fun LocalTime.format(style: FormatStyle, locale: Locale): String
public fun LocalDateTime.format(dateStyle: FormatStyle, timeStyle: FormatStyle, locale: Locale): String
public fun LocalDateTime.format(style: FormatStyle, locale: Locale): String
public fun Month.displayName(style: TextStyle, locale: Locale): String
public fun DayOfWeek.displayName(style: TextStyle, locale: Locale): String
```

Ships in `kotlinx-locale-datetime-platform`, package
`dev.carcara.kotlinx.locale.datetime.platform`. Dates, times and calendar names
from the host: `java.time.format.DateTimeFormatter` on JVM and Android,
`Intl.DateTimeFormat` on JS and Wasm/JS, `NSDateFormatter` on Apple. The six
extensions have the same signatures as the CLDR ones in a different package, so
switching is an import change and nothing else.

`isAvailable` is false on the targets whose platform exposes no locale data at
all: Linux, Windows, Android Native and WASI. On those, every lookup returns
null. Which operation answers on which target is tabulated in the
[README](README.md#what-each-module-answers-per-target).

Everything is formatted in UTC. A `LocalDate` carries no zone and every platform
formatter takes an instant plus a zone, so left to the host's zone a date would
render as the day before or after for anyone west or east of it. Fixing the zone
to UTC makes the printed fields the fields that were passed in.

```kotlin
import dev.carcara.kotlinx.locale.datetime.platform.*

date.format(FormatStyle.LONG, Locale.forLanguageTag("pt-BR"))
```

### kotlinx-locale-datetime-cldr-runtime

Package `dev.carcara.kotlinx.locale.datetime.cldr.runtime`. The engine that
reads CLDR-shaped records. A narrowed build binds its generated tables to these
types; an application using `-cldr-full` never names them.

```kotlin
public class PayloadDateTimeFormats(records: Map<String, String>) : DateTimeFormatSource
```

A `DateTimeFormatSource` over a table of encoded pattern records, keyed by
locale tag. Records are fully resolved rather than sparse, because a date
pattern is not something a locale inherits piecemeal, so a lookup is one map hit.

```kotlin
public class DateTimeRecord(record: String) {
    public val monthsWide: List<String>
    public val monthsAbbr: List<String>
    public val monthsNarrow: List<String>
    public val daysWide: List<String>
    public val daysAbbr: List<String>
    public val daysNarrow: List<String>
    public val am: String
    public val pm: String
    public val era0: String
    public val era1: String
    public val dateFormats: List<String>    // indexed by FormatStyle.ordinal
    public val timeFormats: List<String>
    public val glueFormats: List<String>
    public val digits: String
    public val dayPeriodNames: List<String>
    public val dayPeriodRules: List<DayPeriodRule>
    public fun dayPeriodName(code: Int): String?
}

public fun dateTimeRecordFor(records: Map<String, String>, locale: Locale): DateTimeRecord
```

One locale's decoded pattern data. `dateTimeRecordFor` resolves a locale against
a table and decodes the winning record.

```kotlin
public object DayPeriodCodes {
    public const val AM: Int = 0
    public const val PM: Int = 1
    public const val MIDNIGHT: Int = 2
    public const val NOON: Int = 3
}

public class DayPeriodRule(public val code: Int, public val start: Int, public val end: Int) {
    public val isPoint: Boolean
}
```

The day-period vocabulary. `start` and `end` are hours; `isPoint` is true for a
rule that matches one exact instant, which is how midnight and noon work.

```kotlin
public sealed interface PatternToken {
    public data class Literal(public val text: String) : PatternToken
    public data class Field(public val letter: Char, public val count: Int) : PatternToken
}

public fun parseDateTimePattern(pattern: String): List<PatternToken>
public fun List<PatternToken>.withoutZoneFields(): List<PatternToken>
public fun formatPattern(
    tokens: List<PatternToken>,
    data: DateTimeRecord,
    date: LocalDate?,
    time: LocalTime?,
    skeletons: SkeletonRecord? = null,
): String
```

The pattern machinery. `parseDateTimePattern` tokenizes a CLDR pattern,
`withoutZoneFields` strips the zone fields a `LocalTime` cannot fill along with
the surrounding whitespace and brackets, and `formatPattern` renders the tokens.
The optional `skeletons` argument supplies the quarter names, which only the
skeleton tables carry.

```kotlin
val record = dateTimeRecordFor(registry, Locale.of("de"))
val tokens = parseDateTimePattern(record.dateFormats[FormatStyle.LONG.ordinal])
formatPattern(tokens, record, date = LocalDate(2026, 7, 27), time = null)
// "27. Juli 2026"
```

### SkeletonFormatSource

```kotlin
public interface SkeletonFormatSource : LocaleDataSource {
    public fun skeletonPatternOrNull(skeleton: String, locale: Locale): String?
    public fun formatOrNull(date: LocalDate, skeleton: String, locale: Locale): String?
    public fun formatOrNull(time: LocalTime, skeleton: String, locale: Locale): String?
    public fun formatOrNull(dateTime: LocalDateTime, skeleton: String, locale: Locale): String?
}
```

Package `dev.carcara.kotlinx.locale.datetime.cldr.runtime`. A source that
formats by naming the fields wanted and letting the locale decide their order,
rather than by picking one of four fixed lengths.

This contract sits in `-cldr-runtime` rather than in `-cldr-core`, so no
`-platform` source can answer it. That is an asymmetry rather than an oversight:
the platforms will format from a template, but none of them hands back the
pattern it chose, and half of what makes a skeleton useful is reusing that
pattern.

`skeletonPatternOrNull` returns null when the build has no data for the locale
or the skeleton names a field that cannot be rendered.

```kotlin
CldrDateTimeSkeletons.skeletonPatternOrNull("yMd", Locale.forLanguageTag("pt-BR"))
// "dd/MM/y"
```

### SkeletonFormatSource.format

```kotlin
public fun SkeletonFormatSource.format(date: LocalDate, skeleton: String, locale: Locale): String
public fun SkeletonFormatSource.format(time: LocalTime, skeleton: String, locale: Locale): String
public fun SkeletonFormatSource.format(dateTime: LocalDateTime, skeleton: String, locale: Locale): String
```

The total operations, falling back to ISO 8601 the way the style-based overloads
do.

```kotlin
CldrDateTimeSkeletons.format(date, "yMMMd", Locale.forLanguageTag("ja"))   // "2026年7月27日"
```

### PayloadSkeletonFormats and SkeletonRecord

```kotlin
public class PayloadSkeletonFormats(
    skeletonFormats: Map<String, String>,
    skeletonAppendFormats: Map<String, String>,
    skeletonNames: Map<String, String>,
    dateTimeRecords: Map<String, String>,
) : SkeletonFormatSource

public class SkeletonRecord(formats: String, appendFormats: String, names: String) {
    public val availableFormats: List<String>
    public val glueAtTimeFormats: List<String>
    public val quartersWide: List<String>
    public val quartersAbbr: List<String>
    public val preferredHourChar: Char
    public val firstAllowedHourFormat: String
}
```

`PayloadSkeletonFormats` takes the datetime records as well as the skeleton
ones, because matching needs both: the candidate pool includes each locale's
four standard date and four standard time patterns, and rendering the winner
needs the month and weekday names those records carry. Matchers are built lazily
and kept, since building one sorts a locale's whole candidate pool and an
application tends to ask for the same locale repeatedly.

`preferredHourChar` and `firstAllowedHourFormat` are what `j` and `C` resolve
against. Both are computed at generation time, because resolving them from
CLDR's `timeData` needs likely-subtags expansion.

### CldrDateTimeSkeletons

```kotlin
public object CldrDateTimeSkeletons : SkeletonFormatSource
```

Ships in `kotlinx-locale-datetime-cldr-skeletons`, package
`dev.carcara.kotlinx.locale.datetime.cldr.skeletons`. The skeleton tables for
all 1121 locales. The matcher lives in `-cldr-runtime`, and the pattern table is
borrowed from `CldrDateTime` rather than carried twice.

A skeleton is written in the CLDR letters: `y` year, `M` month, `d` day, `E`
weekday, `Q` quarter, `h` and `H` hour, `m` minute, `s` second, `a`, `b` and `B`
day period, `G` era. Repeat a letter to ask for a width, so `MMM` is an
abbreviated month name and `MMMM` a full one. `j` asks for whichever hour the
locale prefers with the day period that goes with it, `J` for the hour with no
day period, and `C` for the locale's first allowed hour format.

Time zones, week numbers and fractional seconds are out of scope, so a skeleton
naming one of those is refused rather than answered a field short.

### LocalDate.format, LocalTime.format and LocalDateTime.format by skeleton

```kotlin
public fun LocalDate.format(skeleton: String, locale: Locale = Locale.current): String
public fun LocalTime.format(skeleton: String, locale: Locale = Locale.current): String
public fun LocalDateTime.format(skeleton: String, locale: Locale = Locale.current): String
```

Formats with the fields the skeleton names, arranged the way the locale arranges
them. Unlike the style-based overloads, these default the locale to
`Locale.current`.

A skeleton spanning both a date and a time joins the two halves with CLDR's
`atTime` glue rather than the standard glue the style-based API uses, so `en`
reads "at 3:05 PM" here and ", 3:05 PM" there.

```kotlin
date.format("yMMMd", Locale.forLanguageTag("pt-BR"))  // "27 de jul. de 2026"
date.format("yMMMd", Locale.forLanguageTag("ja"))     // "2026年7月27日"
date.format("MMMEd", Locale.forLanguageTag("en"))     // "Mon, Jul 27"

time.format("jm", Locale.forLanguageTag("en"))        // "3:05 PM", U+202F before PM
time.format("jm", Locale.forLanguageTag("en-GB"))     // "15:05"
```

### skeletonPatternOrNull

```kotlin
public fun skeletonPatternOrNull(skeleton: String, locale: Locale = Locale.current): String?
```

The pattern the locale uses for the skeleton, or null when the skeleton names a
field that cannot be rendered. Useful on its own, because the pattern drives
kotlinx-datetime's `DateTimeFormat`, which is how a skeleton buys locale-aware
parsing and not only formatting.

```kotlin
skeletonPatternOrNull("yMMMd", Locale.forLanguageTag("pt-BR"))  // "d 'de' MMM 'de' y"
skeletonPatternOrNull("yMd", Locale.forLanguageTag("pt-BR"))    // "dd/MM/y"

// A numeric pattern round trips through kotlinx-datetime:
LocalDate.Format { byUnicodePattern(skeletonPatternOrNull("yMd", ptBR)!!) }
```

A pattern naming a month or a weekday does not compose that way.
`byUnicodePattern` rejects `MMM` and `EEE` with "the directive is
locale-dependent, but locales are not supported in Kotlin". Formatting is
one-way for anything with a name in it.

## Country

### Country

```kotlin
public enum class Country(
    public val alpha3: String,
    public val numericCode: Int,
) {
    AD("AND", 20), AE("ARE", 784), /* 249 entries */ ;
    public companion object
}
```

Ships in `kotlinx-locale-country-types`, package
`dev.carcara.kotlinx.locale.country`. The 249 officially assigned ISO 3166-1
countries, keyed by alpha-2 code, so `Country.BR` is Brazil and works
exhaustively in `when`. CLDR-only region codes are excluded: no macroregions
(`419`, `EU`), no exceptionally reserved codes (`AC`, `IC`, `TA`) and no
user-assigned codes (`XK`).

| Member | Example for `Country.US` | Ships in |
| --- | --- | --- |
| `alpha2` | `"US"`, the same as `name` | `-core` |
| `alpha3` | `"USA"` | `-types` |
| `numericCode` | `840` | `-types` |
| `displayName(locale)` | `"United States"` | `-cldr-full` or `-platform` |

### Country.alpha2

```kotlin
public val Country.alpha2: String
```

The ISO 3166-1 alpha-2 code. The same string as `name`, given a domain name so
call sites do not read as reflection.

```kotlin
Country.US.alpha2   // "US"
```

### Country code lookups

```kotlin
public fun Country.Companion.forAlpha2(code: String): Country
public fun Country.Companion.forAlpha2OrNull(code: String): Country?
public fun Country.Companion.forAlpha3(code: String): Country
public fun Country.Companion.forAlpha3OrNull(code: String): Country?
public fun Country.Companion.forNumericCode(code: Int): Country
public fun Country.Companion.forNumericCodeOrNull(code: Int): Country?
```

Every representation converts to every other. Code lookups are case-insensitive.
The `OrNull` variants return null on unknown input; the plain variants throw
`IllegalArgumentException`.

```kotlin
Country.forAlpha2("br")            // Country.BR
Country.forAlpha3("DEU")           // Country.DE
Country.forNumericCode(392)        // Country.JP
Country.forAlpha2OrNull("XX")      // null
Country.forAlpha2("XX")            // throws IllegalArgumentException
```

### Country.forLocaleOrNull

```kotlin
public fun Country.Companion.forLocaleOrNull(locale: Locale = Locale.current): Country?
```

The country named by the locale's region subtag, or null when the locale has no
region. Needs no locale data.

```kotlin
Country.forLocaleOrNull(Locale.forLanguageTag("pt-BR"))   // Country.BR
Country.forLocaleOrNull(Locale.forLanguageTag("pt"))      // null
```

### CountryNameSource

```kotlin
public interface CountryNameSource : LocaleDataSource {
    public fun countryNameOrNull(alpha2: String, locale: Locale): String?
}
```

A source of localized country names. Keyed by alpha-2 code rather than by
`Country` so the contract does not depend on which entry set is in play: an
implementation compiled against the full enum still satisfies a build whose enum
was narrowed.

```kotlin
CldrCountry.countryNameOrNull("US", Locale.of("ja"))   // "アメリカ合衆国"
CldrCountry.countryNameOrNull("US", Locale.of("zz"))   // null, root has no names
```

### CountryNameSource.displayName

```kotlin
public fun CountryNameSource.displayName(country: Country, locale: Locale): String
```

The total operation over `countryNameOrNull`, falling back to the alpha-2 code,
which is what CLDR root already does.

```kotlin
CldrCountry.displayName(Country.BR, Locale.of("fr"))   // "Brésil"
```

### CountryNameSource.countryForDisplayNameOrNull

```kotlin
public fun CountryNameSource.countryForDisplayNameOrNull(name: String, locale: Locale): Country?
```

Reverse lookup by localized name, case-insensitive and ignoring surrounding
whitespace. Some locales give two countries the same name, so this returns a
country carrying exactly the requested name rather than necessarily the one that
produced it.

```kotlin
CldrCountry.countryForDisplayNameOrNull("Estados Unidos", Locale.of("pt"))   // Country.US
```

### FallbackCountryNames

```kotlin
public class FallbackCountryNames(
    primary: CountryNameSource,
    fallback: CountryNameSource,
) : CountryNameSource
```

Answers from `primary`, and from `fallback` wherever primary has nothing.
Dispatch is per lookup rather than per locale, so a primary that knows a locale
but not one country within it still falls through for that country.
`supportedLocales` is the union of both.

```kotlin
val names = FallbackCountryNames(primary = PlatformCountry, fallback = CldrCountry)
names.displayName(Country.BR, Locale.of("fr"))
```

### CldrCountry

```kotlin
public object CldrCountry : CountryNameSource

public fun Country.displayName(locale: Locale = Locale.current): String
public fun Country.Companion.forDisplayNameOrNull(name: String, locale: Locale = Locale.current): Country?
```

Ships in `kotlinx-locale-country-cldr-full`, package
`dev.carcara.kotlinx.locale.country.cldr`. The CLDR name tables for all 1121
locales, plus the two convenience extensions over them.

`displayName` resolves the territory name through the locale's inheritance
chain, including CLDR `parentLocales` overrides. CLDR root carries no country
names, so a locale with nothing anywhere in its chain falls back to the alpha-2
code.

```kotlin
val us = Country.US
us.displayName(Locale.forLanguageTag("en"))     // United States
us.displayName(Locale.forLanguageTag("pt-BR"))  // Estados Unidos
us.displayName(Locale.forLanguageTag("ja"))     // アメリカ合衆国
us.displayName(Locale.forLanguageTag("zh"))     // 美国

// es-AR inherits from es-419, which renames some countries relative to es:
Country.CI.displayName(Locale.forLanguageTag("es"))     // Côte d’Ivoire
Country.CI.displayName(Locale.forLanguageTag("es-AR"))  // Costa de Marfil

Country.forDisplayNameOrNull("United States")   // Country.US
```

### PlatformCountry

```kotlin
public object PlatformCountry : CountryNameSource {
    public val isAvailable: Boolean
}

public fun Country.displayName(locale: Locale = Locale.current): String
public fun Country.Companion.forDisplayNameOrNull(name: String, locale: Locale = Locale.current): Country?
```

Ships in `kotlinx-locale-country-platform`, package
`dev.carcara.kotlinx.locale.country.platform`. Country names from
`java.util.Locale` on JVM and Android, `Intl.DisplayNames` on JS and Wasm/JS,
`NSLocale` on Apple. The same two extension signatures as the CLDR version, in a
different package.

A platform that does not know a code tends to hand the code back rather than
admit it, which `java.util.Locale` does. An answer equal to the code is treated
as a miss, because the total operation in `-core` already falls back to the code
and a composing source would otherwise take the echo for an answer and never
consult its fallback.

Which targets answer at all is tabulated in the
[README](README.md#what-each-module-answers-per-target).

```kotlin
import dev.carcara.kotlinx.locale.country.platform.*

Country.BR.displayName(Locale.forLanguageTag("fr"))   // whatever the host says
PlatformCountry.isAvailable                           // false on Linux
```

### kotlinx-locale-country-cldr-runtime

```kotlin
public class PayloadCountryNames(records: Map<String, String>) : CountryNameSource
```

Package `dev.carcara.kotlinx.locale.country.cldr.runtime`. The name lookup over
CLDR-shaped records, carrying none of them. Records are sparse, holding only
what each locale's own file declares, with the parent chain walked at lookup
time.

## Currency

### Currency

```kotlin
public enum class Currency(
    public val numericCode: Int,
    public val defaultFractionDigits: Int,
    public val cldrFractionDigits: Int,
    public val cldrRoundingIncrement: Int,
    public val cldrCashFractionDigits: Int,
    public val cldrCashRoundingIncrement: Int,
) {
    /* 178 entries */ ;
    public companion object
}
```

Ships in `kotlinx-locale-currency-types`, package
`dev.carcara.kotlinx.locale.currency`. The 178 active ISO 4217 currencies, keyed
by alphabetic code. The set includes the fund codes (`USN`, `CLF`), the precious
metals (`XAU`, `XPT`) and the special codes (`XXX`, `XDR`), matching the
coverage of `java.util.Currency`.

Each entry carries both what ISO defines and what CLDR does when formatting,
because the two disagree on purpose for some currencies.

| Member | Meaning | USD | JPY | BHD | ALL | XAU |
| --- | --- | --- | --- | --- | --- | --- |
| `numericCode` | ISO 4217 numeric code, -1 when none | 840 | 392 | 48 | 8 | 959 |
| `defaultFractionDigits` | ISO minor units, -1 when N.A. | 2 | 0 | 3 | 2 | -1 |
| `cldrFractionDigits` | digits CLDR formats with | 2 | 0 | 3 | 0 | 2 |
| `minorUnitDigits` | digits of minor-unit amounts (ISO, or 0 when N.A.) | 2 | 0 | 3 | 2 | 0 |

The Albanian lek is the interesting column: ISO says two decimals, CLDR formats
none. `cldrRoundingIncrement`, `cldrCashFractionDigits` and
`cldrCashRoundingIncrement` describe how CLDR rounds, in units of the last
fraction digit, with 0 meaning no increment. CHF cash rounds to 0.05, DKK to
0.50, and AMD drops the decimals entirely.

### Currency.code and minorUnitDigits

```kotlin
public val Currency.code: String
public val Currency.minorUnitDigits: Int
```

`code` is the ISO 4217 alphabetic code, the same string as `name`.
`minorUnitDigits` is the fraction scale of `CurrencyAmount.minorUnits`:
`defaultFractionDigits`, or 0 where ISO defines no minor units.

```kotlin
Currency.USD.code               // "USD"
Currency.BHD.minorUnitDigits    // 3
Currency.XAU.minorUnitDigits    // 0, ISO lists N.A.
```

### Currency.isoToCldrUnits and cldrToIsoUnits

```kotlin
public fun Currency.isoToCldrUnits(minorUnits: Long): Long
public fun Currency.cldrToIsoUnits(cldrUnits: Long): Long
```

Converts between the two fraction scales, rounding half-even when CLDR uses
fewer digits than ISO. Currencies where the scales agree pass values through
unchanged.

```kotlin
Currency.ALL.isoToCldrUnits(12345)   // 123, 123.45 lekë becomes 123
Currency.ALL.isoToCldrUnits(12350)   // 124, half-even sends the tie to the even side
Currency.ALL.cldrToIsoUnits(123)     // 12300
Currency.USD.isoToCldrUnits(1234)    // 1234
```

### Currency lookups

```kotlin
public fun Currency.Companion.forCode(code: String): Currency
public fun Currency.Companion.forCodeOrNull(code: String): Currency?
public fun Currency.Companion.forNumericCode(code: Int): Currency
public fun Currency.Companion.forNumericCodeOrNull(code: Int): Currency?
public fun Currency.Companion.forCountryOrNull(country: Country): Currency?
public fun Currency.Companion.forLocaleOrNull(locale: Locale = Locale.current): Currency?
```

Code lookups are case-insensitive. The `OrNull` variants return null on unknown
input; the plain variants throw `IllegalArgumentException`. The country and
locale lookups read CLDR's legal-tender data and return the preferred currency.

```kotlin
Currency.forCode("usd")                                   // Currency.USD
Currency.forNumericCode(978)                              // Currency.EUR
Currency.forCodeOrNull("ZZZ")                             // null
Currency.forCountryOrNull(Country.DE)                     // Currency.EUR
Currency.forLocaleOrNull(Locale.forLanguageTag("pt-BR"))  // Currency.BRL
```

### Country.currency and Country.currencies

```kotlin
public val Country.currency: Currency?
public val Country.currencies: List<Currency>
```

The country-to-currency map, from CLDR's legal-tender data, preferred first.
`currencies` is empty for a country without a universal currency.

```kotlin
Country.US.currency     // Currency.USD
Country.PA.currencies   // [PAB, USD]
Country.AQ.currency     // null, Antarctica has no universal currency
```

### CurrencyAmount

```kotlin
public class CurrencyAmount(
    public val currency: Currency,
    public val minorUnits: Long,
) : Comparable<CurrencyAmount> {
    public val majorUnits: Long
    public val minorPart: Int
    public operator fun plus(other: CurrencyAmount): CurrencyAmount
    public operator fun minus(other: CurrencyAmount): CurrencyAmount
    public operator fun unaryMinus(): CurrencyAmount
    override fun compareTo(other: CurrencyAmount): Int
    public fun toDecimalString(): String
    override fun toString(): String
}
```

A monetary amount as a currency plus a `Long` count of ISO minor units: cents
for USD, fils for BHD, whole yen for JPY.

`majorUnits` is the whole-currency part truncated toward zero. `minorPart` is
the sub-unit remainder, carrying the amount's sign, so -1250 USD minor units
gives -12 and -50. `toDecimalString` writes the plain ISO decimal with `.` and
ISO minor-unit digits, which is what serialization wants. `toString` prefixes it
with the code.

Arithmetic and comparison stay within one currency. Mixing currencies throws
`IllegalArgumentException`.

```kotlin
val price = CurrencyAmount(Currency.USD, 1234_56)   // $1,234.56
price.majorUnits            // 1234
price.minorPart             // 56
price.toDecimalString()     // "1234.56"
price.toString()            // "USD 1234.56"

val total = price + CurrencyAmount(Currency.USD, 100)   // 1235.56
-total                                                   // -1235.56
price < total                                            // true
price + CurrencyAmount(Currency.EUR, 100)                // throws
```

### CurrencyAmount.of

```kotlin
public fun CurrencyAmount.Companion.of(
    currency: Currency,
    majorUnits: Long,
    minorPart: Int = 0,
): CurrencyAmount
```

Builds an amount from major units and a signed sub-unit part. Throws
`IllegalArgumentException` when `minorPart` exceeds the currency's minor-unit
range or its sign conflicts with `majorUnits`.

```kotlin
CurrencyAmount.of(Currency.USD, 12, 50)     // 12.50
CurrencyAmount.of(Currency.USD, -12, -50)   // -12.50
CurrencyAmount.of(Currency.USD, 12, 500)    // throws, out of range
CurrencyAmount.of(Currency.JPY, 500)        // 500, no minor units
```

### CurrencyAmount.parse and parseOrNull

```kotlin
public fun CurrencyAmount.Companion.parse(currency: Currency, text: String): CurrencyAmount
public fun CurrencyAmount.Companion.parseOrNull(currency: Currency, text: String): CurrencyAmount?
```

Reads a plain ISO decimal string: an optional `-`, digits, and at most
`minorUnitDigits` fraction digits after `.`. This is the inverse of
`toDecimalString`, not of `format`. `parseOrNull` returns null on malformed
input, excess fraction digits or overflow; `parse` throws
`IllegalArgumentException`.

```kotlin
CurrencyAmount.parse(Currency.USD, "12.5")           // 12.50
CurrencyAmount.parse(Currency.USD, "-12.50")         // -12.50
CurrencyAmount.parseOrNull(Currency.USD, "12.345")   // null, too many decimals
CurrencyAmount.parseOrNull(Currency.USD, "1,234")    // null, use parseFormatted
```

### CurrencySymbolStyle

```kotlin
public enum class CurrencySymbolStyle { SYMBOL, CODE }
```

How the currency is written inside a formatted amount. `SYMBOL` uses the
localized CLDR symbol (`$`, `€`, `US$` for USD in pt-BR); `CODE` uses the ISO
4217 alphabetic code.

### CurrencyNameSource

```kotlin
public interface CurrencyNameSource : LocaleDataSource {
    public fun currencySymbolOrNull(currencyCode: String, locale: Locale): String?
    public fun currencyNameOrNull(currencyCode: String, locale: Locale): String?
}

public fun CurrencyNameSource.symbol(currency: Currency, locale: Locale): String
public fun CurrencyNameSource.displayName(currency: Currency, locale: Locale): String
```

Localized currency symbols and display names, keyed by ISO code so the contract
does not depend on the entry set. The two total operations fall back to the ISO
code.

```kotlin
CldrCurrency.symbol(Currency.USD, Locale.of("en"))        // "$"
CldrCurrency.displayName(Currency.EUR, Locale.of("es"))   // "euro"
```

### CurrencyFormatSource

```kotlin
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

Renders and reads monetary amounts. Amounts cross the boundary as ISO minor
units plus an ISO 4217 code, so the contract stays independent of the entry set.
`formatOrNull` returns null when the source cannot render, including when it
does not recognize the code, since the code is what fixes the fraction scale.

The interface sits at the level of the operation rather than of the tables CLDR
happens to store, because no platform hands out number patterns: `Intl` and
`NSNumberFormatter` format, they do not describe.

```kotlin
CldrCurrency.formatOrNull(123456, "USD", Locale.of("de"), CurrencySymbolStyle.SYMBOL, false, false)
// "1.234,56 $"
```

### CurrencyFormatSource.format and parseFormattedOrNull

```kotlin
public fun CurrencyFormatSource.format(
    amount: CurrencyAmount,
    locale: Locale,
    style: CurrencySymbolStyle = CurrencySymbolStyle.SYMBOL,
    accounting: Boolean = false,
    cash: Boolean = false,
): String

public fun CurrencyFormatSource.parseFormattedOrNull(
    currency: Currency,
    text: String,
    locale: Locale,
): CurrencyAmount?
```

The total format operation falls back to `USD 12.50`, the ISO code and the plain
decimal, when the source cannot render the amount at all.

```kotlin
CldrCurrency.format(CurrencyAmount(Currency.EUR, 123456), Locale.of("de"))   // "1.234,56 €"
CldrCurrency.parseFormattedOrNull(Currency.EUR, "1.234,56 €", Locale.of("de"))
```

### FallbackCurrencyNames and FallbackCurrencyFormats

```kotlin
public class FallbackCurrencyNames(
    primary: CurrencyNameSource,
    fallback: CurrencyNameSource,
) : CurrencyNameSource

public class FallbackCurrencyFormats(
    primary: CurrencyFormatSource,
    fallback: CurrencyFormatSource,
) : CurrencyFormatSource
```

Answer from `primary`, and from `fallback` wherever primary has nothing. Symbols
and names dispatch separately, so a primary carrying only symbols composes with a
source carrying only names.

```kotlin
val formats = FallbackCurrencyFormats(primary = PlatformCurrency, fallback = CldrCurrency)
formats.format(CurrencyAmount(Currency.USD, -123456), Locale.of("en"), accounting = true)
// PlatformCurrency has no accounting on JVM, so CldrCurrency answers
```

### CldrCurrency

```kotlin
public object CldrCurrency : CurrencyNameSource, CurrencyFormatSource
```

Ships in `kotlinx-locale-currency-cldr-full`, package
`dev.carcara.kotlinx.locale.currency.cldr`. The CLDR symbol, name and number
tables for all 1121 locales.

### Currency.symbol and Currency.displayName

```kotlin
public fun Currency.symbol(locale: Locale = Locale.current): String
public fun Currency.displayName(locale: Locale = Locale.current): String
```

Both resolve through the locale chain like country names and fall back to the
ISO code when CLDR has nothing.

```kotlin
Currency.USD.symbol(Locale.forLanguageTag("en"))     // $
Currency.USD.symbol(Locale.forLanguageTag("pt-BR"))  // US$
Currency.JPY.symbol(Locale.forLanguageTag("ja"))     // ￥ fullwidth, en uses ¥
Currency.CHF.symbol(Locale.forLanguageTag("de-CH"))  // CHF, no symbol so the code

Currency.USD.displayName(Locale.forLanguageTag("en"))     // US Dollar
Currency.USD.displayName(Locale.forLanguageTag("pt-BR"))  // Dólar americano
Currency.EUR.displayName(Locale.forLanguageTag("es"))     // euro
```

### CurrencyAmount.format

```kotlin
public fun CurrencyAmount.format(
    locale: Locale = Locale.current,
    style: CurrencySymbolStyle = CurrencySymbolStyle.SYMBOL,
    accounting: Boolean = false,
    cash: Boolean = false,
): String
```

Formats the amount with the pattern and symbols of the locale. `accounting`
selects the accounting pattern, and `cash` applies CLDR's cash fraction digits
and cash rounding. The number of fraction digits shown is CLDR's, which can
differ from the ISO minor units.

```kotlin
val amount = CurrencyAmount(Currency.USD, -123456)
val en = Locale.forLanguageTag("en")

amount.format(en)                                       // -$1,234.56
amount.format(en, accounting = true)                    // ($1,234.56)
amount.format(en, style = CurrencySymbolStyle.CODE)     // -USD 1,234.56

CurrencyAmount(Currency.CHF, 1003).format(en, cash = true)   // CHF 10.05
CurrencyAmount(Currency.AMD, 12350).format(en, cash = true)  // AMD 124
CurrencyAmount(Currency.ALL, 12345).format(en)               // ALL 123
```

Real output for 123456 minor units, which is 1,234.56:

| Locale | USD | EUR | JPY (1234) |
| --- | --- | --- | --- |
| en | $1,234.56 | €1,234.56 | ¥1,234 |
| de | 1.234,56 $ | 1.234,56 € | 1.234 ¥ |
| pt-BR | US$ 1.234,56 | € 1.234,56 | JP¥ 1.234 |
| de-CH | $ 1'234.56 | EUR 1'234.56 | ¥ 1'234 |
| fr | 1 234,56 $US | 1 234,56 € | 1 234 JPY |
| ar-EG | ‏١٬٢٣٤٫٥٦ US$ | ‏١٬٢٣٤٫٥٦ € | ‏١٬٢٣٤ JP¥ |

Symbols are locale-relative, exactly as CLDR sees the world: French writes the
US dollar as `$US`, Hindi writes yen as `JP¥`, and Swiss German uses the plain
code for the euro. The separator between an alphabetic symbol and the number is
U+00A0, a no-break space; French groups digits with U+202F. Grouping follows the
locale's pattern, including Indian lakh and crore grouping (`₹1,23,456.78` in hi)
and Spanish's minimum-grouping rule, which gives `1000,00 €` but `10.000,00 €`.

When CLDR provides an `alphaNextToNumber` pattern variant, it is used
automatically whenever the character next to the number would be a letter. That
is why `CHF 10.05` and `USD 1,234.56` get a space while `$1,234.56` does not.

### CurrencyAmount.parseFormatted and parseFormattedOrNull

```kotlin
public fun CurrencyAmount.Companion.parseFormatted(
    currency: Currency,
    text: String,
    locale: Locale = Locale.current,
): CurrencyAmount

public fun CurrencyAmount.Companion.parseFormattedOrNull(
    currency: Currency,
    text: String,
    locale: Locale = Locale.current,
): CurrencyAmount?
```

The reverse of `format`: reads a CLDR-formatted string using the locale's
separators, digits and currency symbol. `parseFormatted` throws
`IllegalArgumentException` on invalid input; `parseFormattedOrNull` returns null.

The printed number is taken at face value and scaled to ISO minor units, so
CLDR's reduced formatting digits do not distort the result.

```kotlin
val ptBR = Locale.forLanguageTag("pt-BR")
CurrencyAmount.parseFormatted(Currency.BRL, "R$ 1.234,56", ptBR).minorUnits  // 123456

// HUF formats with 0 decimals but has 2 ISO decimals:
val hu = Locale.forLanguageTag("hu")
CurrencyAmount.parseFormatted(Currency.HUF, "200 Ft", hu).minorUnits     // 20000
CurrencyAmount.parseFormatted(Currency.HUF, "200,50 Ft", hu).minorUnits  // 20050

CurrencyAmount.parseFormatted(Currency.USD, "($1,234.56)", en).minorUnits   // -123456
CurrencyAmount.parseFormatted(Currency.EGP, "١٬٢٣٤٫٥٦", Locale.forLanguageTag("ar-EG"))
```

Parsing is lenient about placement and strict about content. The currency may
appear as its symbol, ISO code or display name, anywhere or not at all, with any
spacing. Leftover characters that are not digits or the locale's separators fail
the parse, as does a fraction ISO minor units cannot represent, such as `"5.5"`
for JPY. Negatives are recognized from the locale's minus sign or from
accounting parentheses. `format` output round trips for every bundled locale,
and the value comes back through the CLDR digit conversion, so a lossy format
like ALL's 0-digit rendering parses back to the printed value: `"ALL 123"` gives
12300.

### PlatformCurrency

```kotlin
public object PlatformCurrency : CurrencyNameSource, CurrencyFormatSource {
    public val isAvailable: Boolean
}

public fun Currency.symbol(locale: Locale = Locale.current): String
public fun Currency.displayName(locale: Locale = Locale.current): String
public fun CurrencyAmount.format(
    locale: Locale = Locale.current,
    style: CurrencySymbolStyle = CurrencySymbolStyle.SYMBOL,
    accounting: Boolean = false,
    cash: Boolean = false,
): String
public fun CurrencyAmount.Companion.parseFormattedOrNull(
    currency: Currency,
    text: String,
    locale: Locale = Locale.current,
): CurrencyAmount?
```

Ships in `kotlinx-locale-currency-platform`, package
`dev.carcara.kotlinx.locale.currency.platform`. Symbols, names and number
formatting from `java.util.Currency` and `NumberFormat` on JVM and Android,
`Intl.NumberFormat` on JS and Wasm/JS, `NSNumberFormatter` on Apple.

More partial than `PlatformCountry`, in three specific ways. Cash rounding is
not a platform concept, so `cash = true` always misses. Accounting exists on
`Intl` and Foundation but not in `java.text`, so on JVM and Android
`accounting = true` misses. Parsing is offered only where it is exact: JVM and
Android parse through `BigDecimal`, `Intl` has no parser, and Foundation's
returns a lossy `NSNumber`, so rather than round-trip money through a `Double`
this reports a miss.

There is no throwing `parseFormatted` here, unlike the CLDR package. A miss is
the expected outcome on most targets, so the API does not offer a form that
turns it into an exception. Which operation answers on which target is
tabulated in the [README](README.md#what-each-module-answers-per-target).

```kotlin
import dev.carcara.kotlinx.locale.currency.platform.*

CurrencyAmount(Currency.EUR, 123456).format(Locale.forLanguageTag("de"))
CurrencyAmount.parseFormattedOrNull(Currency.EUR, "1.234,56 €", Locale.of("de"))  // null on JS
```

### kotlinx-locale-currency-cldr-runtime

Package `dev.carcara.kotlinx.locale.currency.cldr.runtime`.

```kotlin
public class PayloadCurrencyNames(records: Map<String, String>) : CurrencyNameSource
public class PayloadCurrencyFormats(
    formatRecords: Map<String, String>,
    nameRecords: Map<String, String>,
) : CurrencyFormatSource
```

The lookup and the pattern-based formatter and parser, over tables passed in.
`PayloadCurrencyFormats` takes both because a pattern substitutes a symbol into
itself, so formatting needs the name table too.

```kotlin
public class CurrencyNumberFormat(record: String) {
    public val digits: String
    public val decimal: String
    public val group: String
    public val currencyDecimal: String
    public val currencyGroup: String
    public val minusSign: String
    public val minimumGroupingDigits: Int
    public val standardPattern: String
    public val standardAlphaPattern: String
    public val accountingPattern: String
    public val accountingAlphaPattern: String
}

public fun currencyNumberFormatFor(records: Map<String, String>, locale: Locale): CurrencyNumberFormat
```

One locale's decoded number-formatting data. The `Alpha` patterns are CLDR's
`alphaNextToNumber` variants, used when the character adjacent to the number
would be a letter.

### Internal API

Package `dev.carcara.kotlinx.locale.currency.internal`, all of it gated behind
`@InternalKotlinxLocaleApi` and carrying no compatibility guarantees:
`countryCurrencyCodes` (the generated country-to-currency table behind
`Country.currency`), `rescaleFraction` (the half-even scale conversion behind
`isoToCldrUnits`) and `roundToIncrement` (the cash-rounding step).

## Composing and replacing sources

Every convenience extension is one line over a public source object, so the
explicit form is always available:

```kotlin
Country.BR.displayName(locale)                // convenience
CldrCountry.displayName(Country.BR, locale)   // exactly what it calls
```

That matters in two places the convenience form cannot serve.

Testing without CLDR. A test that needs `displayName` to return a known string
implements the interface instead of pinning a real CLDR value that a data
upgrade can change:

```kotlin
val fake = object : CountryNameSource {
    override val supportedLocales = setOf(Locale.of("en"))
    override fun countryNameOrNull(alpha2: String, locale: Locale) = "Testland"
}
fake.displayName(Country.BR, Locale.of("en"))   // Testland
```

Composition. The interfaces are partial, with every lookup returning null where
the source has nothing, so a composing source can tell a miss from an answer:

```kotlin
val names = FallbackCountryNames(primary = MyOwnNames, fallback = CldrCountry)
names.displayName(Country.BR, locale)
```

There is one composer per interface: `FallbackCountryNames`,
`FallbackCurrencyNames`, `FallbackCurrencyFormats` and `FallbackDateTimeFormats`.

Choosing between the bundled and host sources is an import, because the
extensions are the same names in different packages:

```kotlin
import dev.carcara.kotlinx.locale.country.cldr.displayName        // bundled tables
import dev.carcara.kotlinx.locale.country.platform.displayName    // the host
```

`-cldr-full` answers the same everywhere and costs what its tables weigh.
`-platform` ships nothing and answers whatever the device says, which means it
answers nothing at all on Linux, Windows, Android Native and Wasm-WASI. Neither
is the default. A `Fallback*` composer is how you take the host's answer where
there is one without giving up a guaranteed answer. What the choice costs is
measured rather than argued, in [`docs/size.md`](docs/size.md).

Both implementations of every contract run through one conformance suite built
from ICU fixtures. It lives in `conformance-test-suite/` and is not published: it
exists so the CLDR source and the platform source of a domain are held to the
same assertions, not as a compliance kit for sources outside this build. The
suite runs at two tiers. `EXACT` is for the sources compiled from CLDR, which are
a second encoding of the data ICU encodes and must match it byte for byte.
`BEHAVIOURAL` is for the platform sources, whose data belongs to the host and
moves with OS versions, so it checks that answers are well-shaped and round trip,
not what they say. Pinning platform data to a fixture would turn the test into a
report on the CI image.

## Gradle plugin

Plugin id `dev.carcara.kotlinx-locale`, extension `kotlinxLocale`. It generates
a locale data set narrowed to what a build declares, implementing the same
interfaces the shipped `-cldr-full` modules do.

```kotlin
public abstract class KotlinxLocaleExtension {
    public abstract val locales: SetProperty<String>
    public abstract val fallbackLocale: Property<String>
    public abstract val packageName: Property<String>
    public abstract val objectPrefix: Property<String>

    public fun locales(vararg refs: LocaleRef)
    public fun locales(vararg tags: String)
    public fun fallback(ref: LocaleRef)
    public fun fallback(tag: String)

    public fun country(action: Action<CountryFeatures>)
    public fun currency(action: Action<CurrencyFeatures>)
    public fun datetime(action: Action<DateTimeFeatures>)
}

public abstract class CountryFeatures {
    public abstract val names: Property<Boolean>
}

public abstract class CurrencyFeatures {
    public abstract val names: Property<Boolean>
    public abstract val formats: Property<Boolean>
}

public abstract class DateTimeFeatures {
    public abstract val patterns: Property<Boolean>
    public abstract val skeletons: Property<Boolean>
}
```

| Member | What it does |
| --- | --- |
| `locales(vararg LocaleRef)` | Adds locales by reference, the form the compiler checks. |
| `locales(vararg String)` | Adds locales by tag, for a set read from a file or built at configuration time. |
| `fallback(...)` | The locale that answers for anything not generated. Required, and required to be one of `locales`. |
| `packageName` | The package the generated sources go into. |
| `objectPrefix` | The prefix on the generated source objects, so `Generated` yields `GeneratedCountryNames`. Configurable because a project may want a narrow default set and a full one behind a lazy load. |
| `country { names }` | Localized country names, behind `Country.displayName`. |
| `currency { names }` | Localized currency symbols and display names. |
| `currency { formats }` | Number patterns, for `CurrencyAmount.format` and `parseFormatted`. Implies `names`, because a pattern substitutes the symbol into itself. |
| `datetime { patterns }` | Date and time patterns plus month and weekday names. |
| `datetime { skeletons }` | Skeleton formatting and the pattern behind it. Implies `patterns`, because matching scores against the locale's standard patterns and rendering the winner needs its month and weekday names. Worth asking for deliberately: across all locales these are the larger half of the datetime data. |

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

Asking for nothing at all fails the build rather than generating an empty source
set. Prefer the `LocaleRef` overloads: a typo in a tag does not throw, it quietly
generates data for one locale fewer than intended, and this is a build script so
nothing fails at runtime either.

The generated objects satisfy the same interfaces as the shipped ones, so they
drop into the same composers and the same extensions:

```kotlin
val names = FallbackCountryNames(primary = GeneratedCountryNames, fallback = CldrCountry)
```

Narrowing only ever touches locale data. `Country.forAlpha2("br")` and
`Currency.forCode("jpy")` keep working whatever was generated.

## Errors, guarantees and versions

`format`, `displayName` and `symbol` never throw for any `Locale`. An unknown
locale falls back along the chain in
[Fallback resolution](#fallback-resolution) and ends at CLDR root, and names
additionally fall back to the ISO code.

The throwing entry points are `Locale.of`, `Locale.forLanguageTag`, the
non-`OrNull` code lookups on `Country` and `Currency`, `CurrencyAmount.of`,
`CurrencyAmount.parse`, `CurrencyAmount.parseFormatted`, and `CurrencyAmount`
arithmetic or comparison across two different currencies. All of them throw
`IllegalArgumentException`, and every lookup that can fail has a non-throwing
alternative.

All types are immutable and safe to share between threads. Sources are stateless
objects, and formatting allocates its working state per call and touches no
global mutable data.

The bundled data comes from CLDR `release-48-2` plus, for currency identity
(numeric codes and ISO minor units), the official ISO 4217 list one published
2026-01-01. Test fixtures and the ISO 4217 numeric cross-check come from ICU
`release-78.3`. Regeneration instructions are in the
[README](README.md#where-the-data-comes-from).
