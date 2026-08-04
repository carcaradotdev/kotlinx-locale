# API reference

Every function, property and parameter a consumer calls, with its Kotlin
signature, what it does, and an example.

This is the surface you write. The source interfaces behind it, the objects that
carry the tables and the pattern machinery that renders them are all public, but
nothing here requires naming them: each domain ships extensions that resolve to
the right source on their own. The one case where you do name them is combining
the host's data with the bundled tables, which the
[README](README.md#using-the-hosts-data-instead-of-ours) covers.

```kotlin
// kotlinx-locale-core
import dev.carcara.kotlinx.locale.Locale

// kotlinx-locale-types, optional
import dev.carcara.kotlinx.locale.catalog.*

// kotlinx-locale-datetime-core and -cldr-full
import dev.carcara.kotlinx.locale.datetime.*
import dev.carcara.kotlinx.locale.datetime.cldr.*

// kotlinx-locale-datetime-cldr-skeletons, optional
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.*

// kotlinx-locale-country-types, -core and -cldr-full
import dev.carcara.kotlinx.locale.country.*
import dev.carcara.kotlinx.locale.country.cldr.*

// kotlinx-locale-currency-types, -core and -cldr-full
import dev.carcara.kotlinx.locale.currency.*
import dev.carcara.kotlinx.locale.currency.cldr.*
```

Swapping `.cldr` for `.platform` in any of those imports moves that domain onto
the host's data. The function names and signatures are the same, which is the
whole point of the package split.

All date and time examples are real output for 2026-07-27, a Monday, at
15:05:09.

## Contents

- [Where the tables come from](#where-the-tables-come-from)
- [Locale](#locale)
- [The locale catalog](#the-locale-catalog)
- [Dates and times](#dates-and-times)
- [Skeleton formatting](#skeleton-formatting)
- [Date and time intervals](#date-and-time-intervals)
- [Week data](#week-data)
- [Duration patterns](#duration-patterns)
- [Duration units](#duration-units)
- [Country](#country)
- [Currency](#currency)
- [Numbers](#numbers)
- [Languages](#languages)
- [Relative time](#relative-time)
- [Time zones](#time-zones)
- [Country.flagEmoji](#countryflagemoji)
- [Person names](#person-names)
- [Phone numbers](#phone-numbers)
- [Serialization](#serialization)
- [Gradle plugin](#gradle-plugin)
- [Errors, guarantees and versions](#errors-guarantees-and-versions)

Which standard each of these implements, with a link to the primary source, is
in [docs/standards.md](docs/standards.md).
## Where the tables come from

Every domain is three layers, and only the last one differs between builds:

| layer | what it holds | example |
| --- | --- | --- |
| `-core` | the types and the contract, no data and no algorithm | `PersonName`, `WeekInfo`, `DurationStyle` |
| `-cldr-runtime` | the algorithm, with the table as a constructor argument | the pattern engine, the skeleton matcher, the name formatter |
| the tables | the data | |

The tables reach you one of three ways, and the first two present the same
functions with the same signatures:

- **The `-cldr-*` artifacts.** Every locale CLDR has, ready to use. This is what
  the imports above show and what every example in this file was run against.
- **The Gradle plugin.** You name the locales you ship and it generates the
  tables and the entry points into your own package, so a narrowed build depends
  on `-core` and `-cldr-runtime` and takes no `-cldr-*` table artifact at all.
  This is not a reimplementation: one emitter writes both, which is why
  `date.format("yMMMd", locale)` means the same thing either way. The heading of
  each section below names the flag that generates it.
- **The `-platform` artifacts.** The host's own data, nothing bundled. Fewer
  domains, and the answers are the host's rather than CLDR's. See the
  [README](README.md#using-the-hosts-data-instead-of-ours).

So an artifact named below is where the tables are, not where the function is
defined. Reading `kotlinx-locale-datetime-cldr-full` as the only way to call
`weekInfo` is the one misreading this file invites, and the plugin flag beside it
is there to head that off.

## Locale

```kotlin
public class Locale {
    public val language: String
    public val script: String?
    public val region: String?
    public val variant: String?
    public fun toLanguageTag(): String
}
```

An immutable locale identifier with four normalized parts. There is no public
constructor; build one with `Locale.of` or `Locale.forLanguageTag`.

| Property | Example | Normalization |
| --- | --- | --- |
| `language` | `"pt"` | lowercase, 2 to 8 letters, required |
| `script` | `"Cyrl"` | title case, 4 letters |
| `region` | `"BR"` | uppercase, 2 letters or 3 digits |
| `variant` | `"valencia"` | lowercase |

Two locales are equal when all four parts are equal, so `Locale` works as a map
key. `toString()` returns `toLanguageTag()`.

```kotlin
val locale = Locale.forLanguageTag("sr-Cyrl-BA")
locale.language        // "sr"
locale.script          // "Cyrl"
locale.region          // "BA"
locale.variant         // null
locale.toLanguageTag() // "sr-Cyrl-BA"
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
three-letter script or a one-letter language.

```kotlin
Locale.of("en")
Locale.of("en", region = "GB")
Locale.of("sr", script = "Cyrl", region = "BA")
Locale.of("EN", "latn", "gb").toLanguageTag()   // "en-Latn-GB"
Locale.of("x")                                  // throws IllegalArgumentException
```

### Locale.forLanguageTag and Locale.forLanguageTagOrNull

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

The system locale, read from the host and parsed with the rules above. When the
platform exposes nothing (Wasm-WASI) or reports something unparseable, you get
`Locale.of("en")`, so this never throws and never returns an unusable value. The
per-platform sources are listed in the
[README](README.md#localecurrent).

`Locale.current` is the default argument on every locale-taking function except
the style-based date and time ones.

```kotlin
Country.BR.displayName()                    // in the system locale
price.format()                              // in the system locale
date.format(FormatStyle.LONG, Locale.current)   // datetime asks explicitly
```

### What happens for a locale with no data

You never have to pick from a supported set. Formatting accepts any `Locale` and
resolves to the most specific match it has, trying in this order:

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

Names go one step further than root: a country or currency with no name anywhere
in the chain comes back as its ISO code.

Regional inheritance is baked into the data at generation time, so `en-AU`
behaves like British English rather than American English even though the list
above never mentions `en-001`.

## The locale catalog

`kotlinx-locale-types` is optional and carries no translations. It generates one
enum per CLDR language, so a locale can be named rather than spelled.

```kotlin
public interface LocaleRef {
    public val tag: String
}

public fun LocaleRef.toLocale(): Locale
```

```kotlin
Locale.forLanguageTag("pt-BRA")   // compiles, throws at runtime
PT.BR.toLocale()                  // cannot be misspelled, autocompletes
PT.BR.tag                         // "pt-BR"
PT.entries                        // every pt-* locale CLDR ships
```

Always two levels, `LANGUAGE.REST`: `PT.BR`, `ZH.HANS_CN`, `CA.ES_VALENCIA`. The
bare language is the companion, so `PT` is `pt` the way `PT.BR` is `pt-BR`. Where
the two names collide the region still wins the member slot, so `PT.PT` is
`pt-PT`. The three CLDR macroregions are not valid Kotlin identifiers and take
their English region names: `AR.WORLD` for `ar-001`, `EN.EUROPE` for `en-150`
and `ES.LATIN_AMERICA` for `es-419`.

Its reason to exist is the Gradle plugin, whose configuration is a locale set: a
typo there does not throw, it quietly generates data for one locale fewer than
intended. Nothing requires it in application code, and `Locale.forLanguageTag`
stays the zero-cost path for tags built at runtime.

## Dates and times

From `kotlinx-locale-datetime-cldr-full`, or with `datetime { patterns = true }`.
Stand-alone month and weekday names are `datetime { standalone = true }`.

### FormatStyle

```kotlin
public enum class FormatStyle { FULL, LONG, MEDIUM, SHORT }
```

The four CLDR standard lengths.

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

Widths for month and weekday names.

| Value | Example (en, July) |
| --- | --- |
| `FULL` | `July` |
| `ABBREVIATED` | `Jul` |
| `NARROW` | `J` |

Narrow names are not unique within a locale (January, June and July are all `J`
in English), so they suit column headers rather than parsing or lookup.

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

CLDR's FULL and LONG time patterns end in a time-zone name. A `LocalTime` has no
zone, so those fields are dropped along with the whitespace and brackets around
them, which is why FULL, LONG and MEDIUM look identical in many locales here.

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

The localized month name, from CLDR's "format" context, the one meant for use
inside a sentence or a formatted date. In languages with grammatical case this
is the inflected form: Russian July is `июля`, the genitive that belongs in
"27 июля", not the nominative `июль`.

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

You never write a day period yourself. It matters because the standard patterns
`LocalTime.format` uses contain them, and in some locales that changes the
output across the day.

Traditional Chinese is the locale family whose standard time patterns use the
flexible day period, so its short times read:

| Time | zh-Hant SHORT | Period |
| --- | --- | --- |
| 00:00 | 午夜12:00 | midnight, exact time only |
| 02:05 | 凌晨2:05 | night |
| 06:05 | 清晨6:05 | early morning |
| 09:05 | 上午9:05 | morning |
| 12:05 | 中午12:05 | midday |
| 15:05 | 下午3:05 | afternoon |
| 20:05 | 晚上8:05 | evening |

Boundaries are locale-specific. Night runs 21:00 to 24:00 in `en` but 22:00 to
04:00 in `ru`, wrapping past midnight. Locales that use the noon-and-midnight
variant get `12:00 noon` in `en`, while German has a name for midnight but none
for noon, so 12:00 stays `PM`. A period the locale has no name for falls back to
AM or PM, so something is always produced.

### Numbering systems

Each locale carries the digits of its default numbering system, and every number
the formatter writes goes through them. Latin-digit locales are unaffected.

```kotlin
date.format(FormatStyle.LONG, Locale.forLanguageTag("ar-EG"))  // ٢٧ يوليو ٢٠٢٦
date.format(FormatStyle.SHORT, Locale.forLanguageTag("fa"))    // ۲۰۲۶/۷/۲۷
date.format(FormatStyle.SHORT, Locale.forLanguageTag("bn"))    // ২৭/৭/২৬
```

One consequence of CLDR 48 that surprises people: plain `ar` defaults to Latin
digits. The Arabic-Indic digits above come from `ar-EG` and other regional
Arabic locales.

## Skeleton formatting

From `kotlinx-locale-datetime-cldr-skeletons`, which is opt in, or with
`datetime { skeletons = true }`. Instead of
picking one of four lengths, you name the fields you want and the locale decides
how to arrange them.

### LocalDate.format, LocalTime.format and LocalDateTime.format by skeleton

```kotlin
public fun LocalDate.format(skeleton: String, locale: Locale = Locale.current): String
public fun LocalTime.format(skeleton: String, locale: Locale = Locale.current): String
public fun LocalDateTime.format(skeleton: String, locale: Locale = Locale.current): String
```

Unlike the style-based overloads, these default the locale to `Locale.current`.

```kotlin
date.format("yMMMd", Locale.forLanguageTag("pt-BR"))  // "27 de jul. de 2026"
date.format("yMMMd", Locale.forLanguageTag("ja"))     // "2026年7月27日"
date.format("MMMEd", Locale.forLanguageTag("en"))     // "Mon, Jul 27"

time.format("jm", Locale.forLanguageTag("en"))        // "3:05 PM", U+202F before PM
time.format("jm", Locale.forLanguageTag("en-GB"))     // "15:05"
```

The letters are CLDR's:

| Letter | Field | Letter | Field |
| --- | --- | --- | --- |
| `y` | year | `h`, `H` | hour |
| `M` | month | `m` | minute |
| `d` | day | `s` | second |
| `E` | weekday | `a`, `b`, `B` | day period |
| `Q` | quarter | `G` | era |

Repeat a letter to ask for a width, so `MMM` is an abbreviated month name and
`MMMM` a full one. `j` asks for whichever hour the locale prefers with the day
period that goes with it, `J` for the hour with no day period, and `C` for the
locale's first allowed hour format.

A skeleton spanning both a date and a time joins the halves with CLDR's `atTime`
glue rather than the standard glue the style-based API uses, so `en` reads
"at 3:05 PM" here and ", 3:05 PM" there.

Time zones, week numbers and fractional seconds are out of scope, because a
`LocalDate` carries no zone and week numbering needs data this library does not
ship. A skeleton naming one of those is refused rather than answered a field
short, and the call falls back to ISO 8601.

### skeletonPatternOrNull

```kotlin
public fun skeletonPatternOrNull(skeleton: String, locale: Locale = Locale.current): String?
```

The pattern the locale uses for that skeleton, or null when the skeleton names a
field that cannot be rendered. Worth having on its own, because the pattern
drives kotlinx-datetime's `DateTimeFormat`, which is how a skeleton buys
locale-aware parsing and not only formatting.

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

## Date and time intervals

From `kotlinx-locale-datetime-cldr-intervals`, or with `datetime { intervals = true }`.

```kotlin
public fun intervalFormat(start: LocalDate, end: LocalDate, skeleton: String, locale: Locale = Locale.current): String
public fun intervalFormat(start: LocalTime, end: LocalTime, skeleton: String, locale: Locale = Locale.current): String
public fun intervalFormat(start: LocalDateTime, end: LocalDateTime, skeleton: String, locale: Locale = Locale.current): String
```

A range is not two formatted dates with a separator between them. The parts both
ends share are written once, and where the ends first differ decides which
pattern the locale uses.

```kotlin
val en = Locale.forLanguageTag("en")
intervalFormat(LocalDate(2026, 7, 22), LocalDate(2026, 7, 22), "yMMMd", en) // "Jul 22, 2026"
intervalFormat(LocalDate(2026, 7, 18), LocalDate(2026, 7, 22), "yMMMd", en) // "Jul 18 – 22, 2026"
intervalFormat(LocalDate(2026, 5, 18), LocalDate(2026, 7, 22), "yMMMd", en) // "May 18 – Jul 22, 2026"
intervalFormat(LocalDate(2025, 5, 18), LocalDate(2026, 7, 22), "yMMMd", en) // "May 18, 2025 – Jul 22, 2026"
```

`skeleton` names the fields the way [Skeleton formatting](#skeleton-formatting)
does. Two values equal in every field the skeleton names format once rather than
twice with a separator. The values are formatted in the order given: a later
start is not an error and is not swapped, because several locales write their
fallback with the arguments reversed.

Falls back to the locale's own `{0} – {1}` over two whole formats, then to ISO
8601's `<start>/<end>`.

## Week data

From `kotlinx-locale-datetime-cldr-full`, or with `datetime { patterns = true }`.

```kotlin
public class WeekInfo {
    public val firstDayOfWeek: DayOfWeek
    public val minimalDaysInFirstWeek: Int
    public val weekend: Set<DayOfWeek>
}

public fun weekInfo(locale: Locale = Locale.current): WeekInfo
public fun weekInfoForRegion(regionCode: String): WeekInfo
```

Keyed by territory rather than by language: Portugal starts the week on Sunday
whether the screen is in Portuguese or English.

```kotlin
weekInfo(Locale.forLanguageTag("en-GB")).firstDayOfWeek // MONDAY
weekInfo(Locale.forLanguageTag("en-US")).firstDayOfWeek // SUNDAY
weekInfoForRegion("PT").firstDayOfWeek                  // SUNDAY
weekInfoForRegion("PT").minimalDaysInFirstWeek          // 4
weekInfoForRegion("AF").weekend                         // [THURSDAY, FRIDAY]
weekInfoForRegion("IR").weekend                         // [FRIDAY]
```

The two fields vary independently, so neither implies the other. A locale that
names no region is maximised through likely subtags, so `en` answers for the
United States. Both calls fall back to the world default: Monday, one day, and a
Saturday to Sunday weekend.

`weekInfoForRegion` takes an ISO 3166-1 alpha-2 code, for a caller who has a
country rather than a locale.

## Duration patterns

From `kotlinx-locale-datetime-cldr-full`, or with `datetime { patterns = true }`.

```kotlin
public enum class DurationStyle { HOUR_MINUTE, HOUR_MINUTE_SECOND, MINUTE_SECOND }

public fun durationPattern(style: DurationStyle, locale: Locale = Locale.current): String
```

```kotlin
durationPattern(DurationStyle.MINUTE_SECOND)                              // "m:ss"
durationPattern(DurationStyle.MINUTE_SECOND, Locale.forLanguageTag("fi")) // "m.ss"
```

A pattern rather than a formatted string, because whether ninety seconds reads
as `1:30` or `0:01:30` is the caller's decision and CLDR does not answer it.

Expect almost no variation: across every locale in the release only Finnish and
Danish differ from root. Falls back to root's `h:mm`, `h:mm:ss` and `m:ss`.

For `2 hours` rather than `2:00`, see [duration units](#duration-units) below.

## Duration units

From `kotlinx-locale-datetime-cldr-durations`, or with
`datetime { durationUnits = true }`.

```kotlin
public enum class DurationUnit {
    CENTURY, DECADE, YEAR, QUARTER, MONTH, WEEK, DAY, NIGHT,
    HOUR, MINUTE, SECOND, MILLISECOND, MICROSECOND, NANOSECOND,
}

public enum class UnitWidth { LONG, SHORT, NARROW }

public fun durationFormat(
    value: Long,
    unit: DurationUnit,
    width: UnitWidth = UnitWidth.LONG,
    locale: Locale = Locale.current,
): String

public fun durationFormat(
    value: Double,
    fractionDigits: Int,
    unit: DurationUnit,
    width: UnitWidth = UnitWidth.LONG,
    locale: Locale = Locale.current,
): String

public fun durationFormat(
    value: Decimal,
    unit: DurationUnit,
    width: UnitWidth = UnitWidth.LONG,
    locale: Locale = Locale.current,
): String

public fun durationUnitName(
    unit: DurationUnit,
    width: UnitWidth = UnitWidth.LONG,
    locale: Locale = Locale.current,
): String
```

The measurement form of a quantity of time, where `durationPattern` above is the
clock reading. This is what ICU spells
`NumberFormatter.unit(MeasureUnit.MINUTE).unitWidth(NARROW)`.

```kotlin
val en = Locale.forLanguageTag("en")

durationFormat(2, DurationUnit.HOUR, locale = en)                     // 2 hours
durationFormat(2, DurationUnit.HOUR, UnitWidth.SHORT, en)             // 2 hr
durationFormat(2, DurationUnit.HOUR, UnitWidth.NARROW, en)            // 2h
durationFormat(90, DurationUnit.MINUTE, UnitWidth.NARROW, en)         // 90m
durationFormat(1.5, fractionDigits = 1, DurationUnit.HOUR, locale = en)  // 1.5 hours
```

Real output for 2, one of the eight values the conformance goldens check:

| Locale | `HOUR` long | `HOUR` short | `HOUR` narrow | `MINUTE` narrow |
| --- | --- | --- | --- | --- |
| en | 2 hours | 2 hr | 2h | 2m |
| de | 2 Stunden | 2 Std. | 2h | 2 Min. |
| fr | 2 heures | 2 h | 2h | 2min |
| es | 2 horas | 2 h | 2h | 2min |
| ru | 2 часа | 2 ч | 2 ч | 2 мин |
| ja | 2 時間 | 2 時間 | 2h | 2m |

The widths are wording rather than abbreviation, so a locale is free to make two
of them the same and many do: German and Japanese both write the narrow hour as
`2h`, and Russian writes the same `2 ч` at short and narrow. The separator is the
locale's own. French joins its long hours with U+00A0 and its short ones with
U+202F, the narrow no-break space it also groups digits with.

The count is rendered through the number formatter rather than pasted in, so it
picks up the locale's digits and grouping. The plural form is chosen from the
number as it will be printed, which is why the entry points take either a `Long`
or a value with an explicit digit count: in Czech `1 hodina` and `1,0 hodiny` are
the same quantity and different forms.

Which unit to use is yours to pick. Nothing here turns ninety minutes into an
hour and a half, for the same reason [relative time](#relative-time) does not
choose between `in 90 minutes` and `in 2 hours`.

CLDR carries wording for 681 of the 1121 locales. The rest fall back to English,
which is also what ICU does for them. Two of CLDR's `duration-` units are left
out: `duration-fortnight` reaches twelve locales and `duration-day-person`
sixteen.

## Country

From `kotlinx-locale-country-cldr-full`, or with `country { names = true }`.

```kotlin
public enum class Country {
    AD, AE, /* 249 entries */ ;
    public val alpha3: String
    public val numericCode: Int
}

public val Country.alpha2: String
```

The 249 officially assigned ISO 3166-1 countries, keyed by alpha-2 code, so
`Country.BR` is Brazil and works exhaustively in `when`. `alpha2` is the same
string as `name`, given a domain name so call sites do not read as reflection.

CLDR-only region codes are excluded: no macroregions (`419`, `EU`), no
exceptionally reserved codes (`AC`, `IC`, `TA`) and no user-assigned codes
(`XK`).

```kotlin
Country.US.alpha2        // "US"
Country.US.alpha3        // "USA"
Country.US.numericCode   // 840
Country.entries.size     // 249
```

### Country lookups

```kotlin
public fun Country.Companion.forAlpha2(code: String): Country
public fun Country.Companion.forAlpha2OrNull(code: String): Country?
public fun Country.Companion.forAlpha3(code: String): Country
public fun Country.Companion.forAlpha3OrNull(code: String): Country?
public fun Country.Companion.forNumericCode(code: Int): Country
public fun Country.Companion.forNumericCodeOrNull(code: Int): Country?
public fun Country.Companion.forLocaleOrNull(locale: Locale = Locale.current): Country?
public fun Country.Companion.forDisplayNameOrNull(name: String, locale: Locale = Locale.current): Country?
```

Every representation converts to every other. Code lookups are case-insensitive.
The `OrNull` variants return null on unknown input; the plain variants throw
`IllegalArgumentException`.

`forLocaleOrNull` reads the locale's region subtag and needs no locale data.
`forDisplayNameOrNull` matches a localized name case-insensitively, ignoring
surrounding whitespace. Some locales give two countries the same name, so it
returns a country carrying exactly the requested name rather than necessarily
the one that produced it.

```kotlin
Country.forAlpha2("br")            // Country.BR
Country.forAlpha3("DEU")           // Country.DE
Country.forNumericCode(392)        // Country.JP
Country.forAlpha2OrNull("XX")      // null
Country.forAlpha2("XX")            // throws IllegalArgumentException

Country.forLocaleOrNull(Locale.forLanguageTag("pt-BR"))   // Country.BR
Country.forLocaleOrNull(Locale.forLanguageTag("pt"))      // null, no region

Country.forDisplayNameOrNull("United States")                               // Country.US
Country.forDisplayNameOrNull("Estados Unidos", Locale.forLanguageTag("pt")) // Country.US
```

### Country.displayName

```kotlin
public fun Country.displayName(locale: Locale = Locale.current): String
```

The localized country name, resolved through the locale's inheritance chain
including CLDR `parentLocales` overrides. Falls back to the alpha-2 code when
there is no name anywhere in the chain, which is what CLDR root already does.

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

## Currency

From `kotlinx-locale-currency-cldr-full`, or with
`currency { names = true; formats = true }`. Compact money is
`currency { compact = true }`.

```kotlin
public enum class Currency {
    AED, AFN, /* 178 entries */ ;
    public val numericCode: Int
    public val defaultFractionDigits: Int
    public val cldrFractionDigits: Int
    public val cldrRoundingIncrement: Int
    public val cldrCashFractionDigits: Int
    public val cldrCashRoundingIncrement: Int
}

public val Currency.code: String
public val Currency.minorUnitDigits: Int
```

The 178 active ISO 4217 currencies, keyed by alphabetic code. The set includes
the fund codes (`USN`, `CLF`), the precious metals (`XAU`, `XPT`) and the
special codes (`XXX`, `XDR`), matching the coverage of `java.util.Currency`.

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

`code` is the ISO 4217 alphabetic code, the same string as `name`.
`minorUnitDigits` is the fraction scale of `CurrencyAmount.minorUnits`.

```kotlin
Currency.USD.code               // "USD"
Currency.BHD.minorUnitDigits    // 3
Currency.XAU.minorUnitDigits    // 0, ISO lists N.A.
```

### Currency.isoToCldrUnits and Currency.cldrToIsoUnits

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
locale lookups read CLDR's legal-tender data.

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

The country-to-currency map from CLDR's legal-tender data, preferred first.
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
    public fun toDecimalString(): String
}
```

A monetary amount as a currency plus a `Long` count of ISO minor units: cents
for USD, fils for BHD, whole yen for JPY.

`majorUnits` is the whole-currency part truncated toward zero. `minorPart` is
the sub-unit remainder carrying the amount's sign, so -1250 USD minor units
gives -12 and -50. `toDecimalString` writes the plain ISO decimal with `.`,
which is what serialization wants; `toString` prefixes it with the code.

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

### CurrencyAmount.of, parse and parseOrNull

```kotlin
public fun CurrencyAmount.Companion.of(
    currency: Currency,
    majorUnits: Long,
    minorPart: Int = 0,
): CurrencyAmount

public fun CurrencyAmount.Companion.parse(currency: Currency, text: String): CurrencyAmount
public fun CurrencyAmount.Companion.parseOrNull(currency: Currency, text: String): CurrencyAmount?
```

`of` builds an amount from major units and a signed sub-unit part, throwing when
`minorPart` exceeds the currency's range or its sign conflicts with `majorUnits`.

`parse` reads a plain ISO decimal string: an optional `-`, digits, and at most
`minorUnitDigits` fraction digits after `.`. It is the inverse of
`toDecimalString`, not of `format`. Use
[`parseFormatted`](#currencyamountparseformatted-and-parseformattedornull) for a
string a human saw.

```kotlin
CurrencyAmount.of(Currency.USD, 12, 50)     // 12.50
CurrencyAmount.of(Currency.USD, -12, -50)   // -12.50
CurrencyAmount.of(Currency.USD, 12, 500)    // throws, out of range
CurrencyAmount.of(Currency.JPY, 500)        // 500, no minor units

CurrencyAmount.parse(Currency.USD, "12.5")           // 12.50
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

When CLDR provides an `alphaNextToNumber` pattern variant it is used
automatically whenever the character next to the number would be a letter, which
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
accounting parentheses. `format` output round trips for every bundled locale.

Note that the `.platform` package offers only `parseFormattedOrNull`, with no
throwing variant, because a miss is the expected outcome on most targets.

## Numbers

From `kotlinx-locale-number-cldr-full`, or with `number { formats = true }`.
Compact notation, plural categories and ordinals are `number { compact = true }`,
`number { plurals = true }` and `number { ordinals = true }`.

### numberFormat

```kotlin
numberFormat(1234567L, EN)                     // "1,234,567"
numberFormat(1234567L, DE)                     // "1.234.567"
numberFormat(1234567L, CS)                     // "1 234 567", with a no-break space
numberFormat(1000L, PL)                        // "1000", because pl groups from five digits
numberFormat(3.14159, fractionDigits = 2, EN)  // "3.14"
numberFormat(Decimal.parse("1.50"), EN)        // "1.5"
```

Overloaded for `Long`, `Double` and `Decimal`. `Decimal` is the exact type the
formatters take: a `Long` of unscaled units plus a scale. The digit count for a
`Double` is required rather than inferred, because the targets do not agree on
how many digits a `Double` has and a formatter that guessed would print
different text on each.

Compact notation is the same call with a `notation`:

```kotlin
numberFormat(1200L, EN, notation = NumberNotation.COMPACT_SHORT)   // "1.2K"
numberFormat(12345L, EN, notation = NumberNotation.COMPACT_SHORT)  // "12K"
numberFormat(999999L, EN, notation = NumberNotation.COMPACT_SHORT) // "1M"
numberFormat(1200L, EN, notation = NumberNotation.COMPACT_LONG)    // "1.2 thousand"
```

The compact default precision is two significant digits or none, whichever keeps
more. UTS #35 leaves that open, so this library pins it and holds it with ICU
goldens. See `kotlinx-locale-number-core/README.md`.

### numberFormatPercent and numberFormatPercentValue

```kotlin
numberFormatPercent(Decimal.parse("0.125"), EN, fractionDigits = 1)      // "12.5%"
numberFormatPercent(Decimal.parse("0.125"), CS, fractionDigits = 1)      // "12,5 %"
numberFormatPercent(Decimal.parse("0.125"), TR, fractionDigits = 1)      // "%12,5"
numberFormatPercentValue(Decimal.parse("12.5"), EN, fractionDigits = 1)  // "12.5%"
```

Two functions because the two readings both have standing and getting it wrong
is a hundredfold error. `numberFormatPercent` takes a fraction and multiplies,
which is what a `%` in a CLDR pattern means. `numberFormatPercentValue` takes a
value that is already a percentage.

The placement is the locale's: Czech and German put a no-break space before the
sign, Turkish puts the sign in front.

### pluralCategory

```kotlin
pluralCategory(1L, CS)                              // ONE
pluralCategory(3L, CS)                              // FEW
pluralCategory(10L, CS)                             // OTHER
pluralCategory(Decimal.parse("1.0"), 1, CS)         // MANY
```

The fraction digit count is required for a `Decimal` and not for a `Long`. Czech
puts every value written with a fraction digit in `many`, so the category is a
property of how the number will be printed rather than of the number.

### numberOrdinal

```kotlin
numberOrdinal(1L, EN)    // "1st"
numberOrdinal(21L, EN)   // "21st"
numberOrdinal(1L, DE)    // "1."
numberOrdinal(2L, CS)    // "2."
```

### numberSymbols and numberParseOrNull

```kotlin
val symbols = numberSymbols(CS)
symbols.decimal                 // ","
symbols.group                   // "\u00A0"
symbols.minimumGroupingDigits   // 1
symbols.digits                  // ["0", "1", ... "9"]
```

```kotlin
numberParseOrNull("1.50", EN)          // Decimal 1.50, scale 2
numberParseOrNull("1.234,5", DE)       // Decimal 1234.5
numberParseOrNull("not a number", EN)  // null
```

`numberSymbols` is for building something this library does not format. An
amount field that formats while someone types cannot round trip through
`numberFormat`, because that would normalise away the half-finished states the
caret depends on. The parse keeps the digits it was given, so a `Decimal` read
back from `1.50` has scale 2 and the plural rules see two visible digits.

## Languages

From `kotlinx-locale-language-cldr-full`, or with `language { names = true }`.

### Locale.displayName and Locale.nativeDisplayName

```kotlin
Locale.of("de").displayName(EN)                    // "German"
Locale.of("de").displayName(PT)                    // "alemão"
Locale.of("cs").nativeDisplayName                  // "čeština"
Locale.forLanguageTag("en-GB").displayName(EN)     // "British English"
Locale.forLanguageTag("en-GB").displayName(EN, LanguageDisplay.STANDARD)
                                                   // "English (United Kingdom)"
Locale.forLanguageTag("sr-Cyrl-BA").displayName(EN)
                                                   // "Serbian (Cyrillic, Bosnia & Herzegovina)"
```

`nativeDisplayName` is the same call with the target and the display locale
equal, which is where CLDR keeps a native name.

CLDR stores these as the language writes them in running text, which is lower
case in many. Capitalizing for a picker row is a separate call; see
`Capitalization`.

### Locale.scriptName and Locale.regionName

```kotlin
EN.scriptName("Latn")   // "Latin"
EN.regionName("419")    // "Latin America"
```

Wider than `Country.displayName`: this answers for the macro-regions a locale
identifier can carry.

## Relative time

From `kotlinx-locale-datetime-cldr-relative`, or with `datetime { relativeTime = true }`.

### relativeTimeFormat

```kotlin
relativeTimeFormat(-1L, RelativeTimeUnit.DAY, locale = EN)   // "yesterday"
relativeTimeFormat(-3L, RelativeTimeUnit.DAY, locale = EN)   // "3 days ago"
relativeTimeFormat(-1L, RelativeTimeUnit.DAY, locale = CS)   // "včera"
relativeTimeFormat(-3L, RelativeTimeUnit.DAY, locale = CS)   // "před 3 dny"
relativeTimeFormat(10L, RelativeTimeUnit.DAY, locale = CS)   // "za 10 dní"
relativeTimeFormat(-1L, RelativeTimeUnit.DAY, numbering = RelativeTimeNumbering.ALWAYS, locale = EN)
                                                          // "1 day ago"
```

You choose the unit. Whether ninety minutes reads as `in 90 minutes` or
`in 2 hours` is not standardized by CLDR, ECMA-402 or ICU, all of which take the
unit from the caller, so this library does not decide it either.

## Time zones

From `kotlinx-locale-timezone-cldr-full`, or with `timezone { formats = true; names = true }`; exemplar cities are `timezone { exemplarCities = true }`.

### TimeZone.displayName

```kotlin
val la = TimeZone.of("America/Los_Angeles")
la.displayName(TimeZoneNameStyle.GENERIC_LONG, locale = EN)    // "Pacific Time"
la.displayName(TimeZoneNameStyle.STANDARD_LONG, locale = EN)   // "Pacific Standard Time"
la.displayName(TimeZoneNameStyle.STANDARD_SHORT, locale = EN)  // "PST"
```

The offset is a separate argument from the style, so a caller who knows which
form it wants never has to supply an instant.

### UtcOffset.displayName

```kotlin
UtcOffset(hours = -8).displayName(EN)                // "GMT-08:00"
UtcOffset(hours = -8).displayName(EN, short = true)  // "GMT-8"
UtcOffset(hours = 0).displayName(EN)                 // "GMT"
```

Locale data rather than a fixed string. The word, the bracket style, the zero
form and the digits all vary.

### TimeZone.exemplarCity

```kotlin
TimeZone.of("Europe/Prague").exemplarCity(CS)   // "Praha"
TimeZone.of("Asia/Dubai").exemplarCity(CS)      // "Dubaj"
```

From `kotlinx-locale-timezone-cldr-cities`, which is opt in. Without it the
generic location format uses the identifier's own last part, which is the
fallback UTS #35 prescribes.

## Country.flagEmoji

```kotlin
Country.BR.flagEmoji   // 🇧🇷
```

Derived from the alpha-2 code rather than looked up, and checked at generation
time against the RGI flag sequences of UTS #51, so it carries no table and needs
no nullable form.

## Person names

From `kotlinx-locale-personname-cldr-full`, or with `personName { formats = true }`.

```kotlin
public class PersonName(
    given: String? = null, given2: String? = null,
    surname: String? = null, surname2: String? = null,
    title: String? = null, generation: String? = null, credentials: String? = null,
    givenInformal: String? = null, surnamePrefix: String? = null, surnameCore: String? = null,
    locale: Locale? = null,
    preferredOrder: PersonNameOrder = PersonNameOrder.DEFAULT,
)

public fun personNameFormat(
    name: PersonName,
    length: PersonNameLength = PersonNameLength.DEFAULT,
    usage: PersonNameUsage = PersonNameUsage.REFERRING,
    formality: PersonNameFormality = PersonNameFormality.DEFAULT,
    order: PersonNameOrder = PersonNameOrder.DEFAULT,
    locale: Locale = Locale.current,
): String

public fun personNameOrder(nameLocale: Locale?, locale: Locale = Locale.current): PersonNameOrder
```

```kotlin
val name = PersonName(given = "Iris", surname = "Adler")
personNameFormat(name)                                  // "Iris Adler"
personNameFormat(name, usage = PersonNameUsage.MONOGRAM) // "I"
personNameFormat(name, order = PersonNameOrder.SORTING)  // "Adler, Iris"

// The length decides how many letters a monogram has. English defaults to
// medium and informal, which is one; long gives the given name and the surname.
personNameFormat(name, length = PersonNameLength.LONG, usage = PersonNameUsage.MONOGRAM) // "IA"
```

The order is not a property of the name or of the reader but of the pair. Pass
the name's own locale to get it right:

```kotlin
val hu = Locale.forLanguageTag("hu")
val hungarian = PersonName(given = "Iris", surname = "Adler", locale = hu)
personNameOrder(hu, hu)                            // SURNAME_FIRST
personNameOrder(hu, Locale.forLanguageTag("en"))   // GIVEN_FIRST
```

Initials are a usage rather than a separate call, matching how CLDR models them,
and `length` then decides how many letters. They are taken by grapheme cluster
rather than by character, so a Bengali or Devanagari conjunct stays whole.

Every part is optional. A name with only one part is written out in full rather
than reduced to an initial or to nothing.

Three fields cannot be derived and must be supplied if the locale asks for them:
`givenInformal` (the `Bob` that stands in for `Robert`), and `surnamePrefix` with
`surnameCore` (the `van den` and `Hul` of `van den Hul`).

Falls back to the given name and surname joined by a space.

## Phone numbers

From `kotlinx-locale-phone-metadata-full`. The plugin has no flag for this one:
the metadata is keyed by territory rather than by locale, so naming three
locales would narrow nothing.

```kotlin
val number = "020 7123 4567".toPhoneNumberOrNull(Country.GB) ?: return
number.isValid()                              // true
number.typeOf()                               // FIXED_LINE
number.format(PhoneNumberFormat.E164)         // +442071234567
number.format(PhoneNumberFormat.NATIONAL)     // 020 7123 4567
number.format(PhoneNumberFormat.INTERNATIONAL)// +44 20 7123 4567
number.format(PhoneNumberFormat.RFC3966)      // tel:+44-20-7123-4567
number.region                                 // Country.GB
```

The number carries its own territory, so the country comes back with it rather
than needing a second call. `region` is a [Country] and is `null` for the plans
ISO 3166-1 does not list; `regionCode` is the raw code and answers `AC` for
Ascension Island where `region` cannot. Use `phoneRegionOrNull(text)` to detect
without building a number, and `phoneRegionCandidates(text)` for a bare national
number, which names no country and so gets a list rather than a guess.

Keyed by [Country] rather than by `Locale`, which is the one place this library
takes a country where everything else takes a locale. A number is valid or it is
not, and it groups the way its own territory groups it, whoever is reading.

Parsing accepts what people type: spaces, dashes, brackets, a leading `+`, the
territory's international dialling prefix, and an extension after `ext`, `x` or
`#`. Use `PhoneNumbers.parse` rather than `toPhoneNumberOrNull` when you want to
know why something failed:

```kotlin
when (val result = PhoneNumbers.parse(input, Country.BR)) {
    is PhoneParseResult.Parsed -> save(result.number)
    is PhoneParseResult.Failed -> when (result.reason) {
        PhoneParseFailure.TOO_SHORT -> Unit          // still typing
        else -> showError()
    }
}
```

Storage is E.164 and nothing else: it is the only form that identifies a number
without also saying where it is being dialled from.

### Formatting as the user types

```kotlin
val formatter = Country.US.asYouType()
formatter.append('2')       // "2"
formatter.append("015")     // "201-5"
formatter.append("550123")  // "(201) 555-0123"
formatter.removeLast()      // "(201) 555-012"
```

An object with `append` and `removeLast` rather than a function over the whole
prefix, because choosing a grouping means walking the territory's format rules
and a field that reformatted from scratch would do that once per keystroke.

The grouping can change as digits arrive, as it does above when the fourth digit
picks a different rule from the tenth. That is inherent: a rule is chosen from
what has been typed, and more digits can pick a different one. What does not
change is the digits themselves, which is the invariant a text field depends on
and the one the tests assert across every territory.

Two things it does not do. It does not place the caret: that mapping depends on
the editor rather than on the number, so `digitsBefore(offset)` gives you the
half that belongs here and the rest is yours. And unlike parsing, validation and
the three finished formats, its output is not held to libphonenumber
character-for-character. Those have a conformance fixture over every territory;
this has the digit-preservation invariant and the territory's own rules.

### Storing one

```kotlin
object AppPhone : PhoneNumberE164Serializer(PhoneNumbers)

@Serializable
class Contact(@Serializable(with = AppPhone::class) val phone: PhoneNumber)
```

There is no default serializer, for the same reason there is none for a currency
amount: the four written forms are not four spellings of one thing. E.164
identifies a number anywhere and is the one to store. The national form does not
identify one at all, which is why `PhoneNumberNationalSerializer` alone takes the
country to read it against. The international and RFC 3966 forms do identify a
number but carry the grouping of the libphonenumber release that wrote them, so a
column of them stops comparing equal to itself after an upgrade.

`LenientPhoneNumberSerializer` reads any of the four and writes E.164, for the
boundary where you do not control the producer. `PhoneNumberPartsSerializer`
writes `{"callingCode":44,"nationalNumber":"2071234567"}` and is the only one
needing no metadata at all, which also makes it the only one that cannot be
wrong about a territory a later release reassigns.

### Where the data comes from

Google's libphonenumber, pinned to a release tag in `codegen/Repos.kt`, over
ITU-T E.164's numbering plans. Not CLDR, which deprecated its own dialling data
in CLDR 34 and pointed at libphonenumber.

The whole domain is pure common Kotlin, including the pattern matching that
validation is. That is deliberate and it is checked: libphonenumber's patterns
use a bounded subset of regular expressions, this library evaluates that subset
itself rather than delegating to a per-target engine, and generation fails
naming the pattern if a release ever steps outside it. `docs/boundaries.md`
has the argument in full.

## Serialization

Three artifacts, one per domain, each depending on its own `-core` and on
`kotlinx-serialization-core`. They are separate so that a build serializing
nothing carries no serialization runtime, and separate from each other so that
serializing a country does not put the `Currency` enum on the classpath.

```kotlin
// kotlinx-locale-serialization
import dev.carcara.kotlinx.locale.serialization.*

// kotlinx-locale-country-serialization
import dev.carcara.kotlinx.locale.country.serialization.*

// kotlinx-locale-currency-serialization
import dev.carcara.kotlinx.locale.currency.serialization.*
```

Every serializer is a `public object` implementing `KSerializer<T>`, so any of
them can be named in `@Serializable(with = ...)`, passed to `encodeToString`
directly, registered as `contextual` in a `SerializersModule`, or wrapped by
`ListSerializer` and `MapSerializer`.

Bad input throws `SerializationException`, which is what a format expects a
serializer to throw. Nothing here throws `IllegalArgumentException`, even where
the underlying lookup would.

### LocaleTagSerializer

```kotlin
public object LocaleTagSerializer : KSerializer<Locale>
```

A `Locale` as its canonical BCP 47 tag. Writing goes through
`Locale.toLanguageTag`, so subtag case is normalized whatever the instance was
built from. Reading goes through `Locale.forLanguageTagOrNull` and inherits its
leniency: POSIX identifiers parse, and anything after a singleton subtag is
ignored.

```kotlin
Json.encodeToString(LocaleTagSerializer, Locale.of("pt", region = "BR"))  // "pt-BR"
Json.encodeToString(LocaleTagSerializer, Locale.of("PT", region = "br"))  // "pt-BR"

Json.decodeFromString(LocaleTagSerializer, "\"pt_BR.UTF-8@latin\"")   // pt-BR
Json.decodeFromString(LocaleTagSerializer, "\"en-US-u-ca-buddhist\"") // en-US
Json.decodeFromString(LocaleTagSerializer, "\"\"")                    // throws
```

### Country serializers

```kotlin
public object CountryAlpha2Serializer : KSerializer<Country>
public object CountryAlpha3Serializer : KSerializer<Country>
public object CountryNumericCodeSerializer : KSerializer<Country>
public object CountryLenientCodeSerializer : KSerializer<Country>
```

One per ISO 3166-1 code space, plus a lenient reader over all three. The three
named ones are exact in both directions and reject the other spellings, which is
what makes them worth naming: a field declared alpha-3 fails on the day a
producer starts sending alpha-2. All of them read case-insensitively.

`CountryAlpha2Serializer` produces what the serialization plugin already
produces for an unannotated `Country` property, since the entry names are the
alpha-2 codes. Naming it states the contract rather than changing the output. It
also gives `Country` a serializer as a root object on Kotlin/JS and
Kotlin/Native, where an enum the plugin never saw declared has none of its own.

```kotlin
Json.encodeToString(CountryAlpha2Serializer, Country.US)       // "US"
Json.encodeToString(CountryAlpha3Serializer, Country.US)       // "USA"
Json.encodeToString(CountryNumericCodeSerializer, Country.US)  // 840

Json.decodeFromString(CountryAlpha3Serializer, "\"usa\"")      // Country.US
Json.decodeFromString(CountryAlpha3Serializer, "\"US\"")       // throws
```

### CountryLenientCodeSerializer

Reads any of the three codes from one string field and writes alpha-2. The code
spaces do not overlap. Two letters, three letters, digits: a string belongs to
exactly one of them, so nothing has to be guessed. Zero-padded numeric codes
are the printed form of the standard and are accepted.

```kotlin
Json.decodeFromString(CountryLenientCodeSerializer, "\"US\"")   // Country.US
Json.decodeFromString(CountryLenientCodeSerializer, "\"USA\"")  // Country.US
Json.decodeFromString(CountryLenientCodeSerializer, "\"840\"")  // Country.US
Json.decodeFromString(CountryLenientCodeSerializer, "\"004\"")  // Country.AF

Json.encodeToString(CountryLenientCodeSerializer, Country.US)   // "US", always
```

It reads the numeric code from a string. A JSON number `840` is a different
token, and a `Decoder` has to commit to `decodeString` or `decodeInt` before it
can see which is coming, so a bare number needs either a forgiving format or the
serializer for the type the field actually holds:

```kotlin
Json.decodeFromString(CountryLenientCodeSerializer, "840")                        // throws
Json { isLenient = true }.decodeFromString(CountryLenientCodeSerializer, "840")   // Country.US
Json.decodeFromString(CountryNumericCodeSerializer, "840")                        // Country.US
```

### Currency serializers

```kotlin
public object CurrencyCodeSerializer : KSerializer<Currency>
public object CurrencyNumericCodeSerializer : KSerializer<Currency>
public object CurrencyLenientCodeSerializer : KSerializer<Currency>
```

The same three shapes for ISO 4217. `CurrencyCodeSerializer` writes the
alphabetic code and is total. `CurrencyLenientCodeSerializer` reads either code
from one string field, with the same numeric-as-string rule as its country
counterpart, and writes the alphabetic code.

`CurrencyNumericCodeSerializer` is the one serializer here that can refuse to
write. `Currency.numericCode` is documented as `-1` where ISO assigns no number,
and writing such a currency throws rather than emitting a sentinel that could
never be read back. All 178 currencies in the bundled data have a numeric code
today, so nothing reaches that guard, but a numeric code is not something ISO
promises every entry. `CurrencyCodeSerializer` has no such edge.

```kotlin
Json.encodeToString(CurrencyCodeSerializer, Currency.USD)         // "USD"
Json.encodeToString(CurrencyNumericCodeSerializer, Currency.USD)  // 840

Json.decodeFromString(CurrencyLenientCodeSerializer, "\"978\"")   // Currency.EUR
Json.decodeFromString(CurrencyLenientCodeSerializer, "\"eur\"")   // Currency.EUR
```

### CurrencyAmount serializers

```kotlin
public object CurrencyAmountMinorUnitsSerializer : KSerializer<CurrencyAmount>
public object CurrencyAmountDecimalSerializer : KSerializer<CurrencyAmount>
public object CurrencyAmountCodeAndDecimalSerializer : KSerializer<CurrencyAmount>
```

Three forms, and none of them is the default. Each one names what it writes:

```kotlin
val price = CurrencyAmount(Currency.USD, 1234_56)

Json.encodeToString(CurrencyAmountMinorUnitsSerializer, price)
// {"currency":"USD","minorUnits":123456}

Json.encodeToString(CurrencyAmountDecimalSerializer, price)
// {"currency":"USD","amount":"1234.56"}

Json.encodeToString(CurrencyAmountCodeAndDecimalSerializer, price)
// "USD 1234.56"
```

`minorUnits` is the state the class holds, exact and free of parsing, but the
scale is not in the payload: `123456` is $1,234.56 only because the `Currency`
enum says USD has two minor units. The decimal string puts the scale in the
payload instead, so a stored amount still means what it meant if a reader was
built against different ISO data. The combined string carries both parts in one
scalar, which is what fits a map key, a query parameter or a single column, and
it is what `CurrencyAmount.toString` writes, so a value copied out of a log
reads back in.

The `currency` field of the two object forms is written and read by
`CurrencyCodeSerializer`. Both object forms accept their fields in either order.

Amounts parse as strictly as `CurrencyAmount.parse`: an optional `-`, digits,
and at most `minorUnitDigits` fraction digits after `.`. Excess digits fail
rather than round.

```kotlin
Json.encodeToString(CurrencyAmountDecimalSerializer, CurrencyAmount(Currency.JPY, 500))
// {"currency":"JPY","amount":"500"}
Json.encodeToString(CurrencyAmountDecimalSerializer, CurrencyAmount(Currency.BHD, 1234))
// {"currency":"BHD","amount":"1.234"}
Json.encodeToString(CurrencyAmountCodeAndDecimalSerializer, CurrencyAmount(Currency.USD, -1250))
// "USD -12.50"

Json.decodeFromString(CurrencyAmountCodeAndDecimalSerializer, "\"JPY 5.5\"")  // throws
```

None of these touches `Locale`, and the module depends on no CLDR data. The
locale-aware form of an amount is
[`CurrencyAmount.format`](#currencyamountformat), and it is not a wire format:
it cannot be read back without knowing which locale wrote it, and CLDR moves
separators between releases. `"USD 1,234.56"` throws here on purpose.

## Gradle plugin

Plugin id `dev.carcara.kotlinx-locale`, extension `kotlinxLocale`. It generates
a locale data set narrowed to what a build declares, implementing the same
interfaces the shipped modules do, so call sites do not change.

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

| Member | What it does |
| --- | --- |
| `locales(vararg LocaleRef)` | Adds locales by reference, the form the compiler checks. |
| `locales(vararg String)` | Adds locales by tag, for a set read from a file or built at configuration time. |
| `fallback(LocaleRef)` or `fallback(String)` | The locale that answers for anything not generated. Required, and required to be one of `locales`. |
| `packageName` | The package the generated sources go into. |
| `objectPrefix` | The prefix on the generated objects, so `Generated` yields `GeneratedCountryNames`. Configurable because a project may want a narrow default set and a full one behind a lazy load. |

Asking for nothing at all fails the build rather than generating an empty source
set. Prefer the `LocaleRef` overloads: a typo in a tag does not throw, it quietly
generates data for one locale fewer than intended, and this is a build script so
nothing fails at runtime either.

### Feature flags

Every flag is off by default. A flag declares the whole set of tables generating
it needs rather than pointing at other flags, so turning one on can write a table
another flag also names. That is what makes a half-configured source set
impossible to ask for: `datetime { skeletons = true }` cannot produce a matcher
with no patterns to match against, because the patterns are part of what
`skeletons` means.

A flag never changes what a call does. It decides which locales and which tables
reach the generated source. Calling an entry point no flag enabled fails to
compile, which is the failure worth having; a call that compiles and answers
wrongly is not.

The last column is the artifact a narrowed build declares in place of
`-cldr-full`. Where two flags name the same one, declaring it once covers both.

| Flag | What it generates | Runtime artifact |
| --- | --- | --- |
| `country { names }` | [`Country.displayName`](#countrydisplayname) | `kotlinx-locale-country-cldr-runtime` |
| `currency { names }` | [`Currency.symbol` and `Currency.displayName`](#currencysymbol-and-currencydisplayname) | `kotlinx-locale-currency-cldr-runtime` |
| `currency { formats }` | [`CurrencyAmount.format`](#currencyamountformat) and [`parseFormatted`](#currencyamountparseformatted-and-parseformattedornull). Writes the symbol table too, since a pattern substitutes the symbol into itself. | `kotlinx-locale-currency-cldr-runtime` |
| `currency { compact }` | `$1.2M`, through `notation` on `format`. Writes the symbols, the patterns and the plural rules its own patterns are keyed by. | `kotlinx-locale-currency-cldr-runtime` |
| `datetime { patterns }` | [`format` by `FormatStyle`](#localdateformat), [month](#monthdisplayname) and [weekday](#dayofweekdisplayname) names, [day periods](#day-periods), [`weekInfo`](#week-data), [`durationPattern`](#duration-patterns) | `kotlinx-locale-datetime-cldr-runtime` |
| `datetime { skeletons }` | [`format` by skeleton](#skeleton-formatting) and [`skeletonPatternOrNull`](#skeletonpatternornull). Writes the pattern tables too. Worth asking for deliberately: across all locales these are the larger half of the datetime data. | `kotlinx-locale-datetime-cldr-runtime` |
| `datetime { intervals }` | [`intervalFormat`](#date-and-time-intervals). Writes the skeleton tables too, since an interval is a split of the pattern the matcher picks. | `kotlinx-locale-datetime-cldr-runtime` |
| `datetime { standalone }` | `TextStyle.STANDALONE` month, weekday and quarter names. Twelve thousand characters across every locale, because the table holds only where a locale differs from its format names. | `kotlinx-locale-datetime-cldr-runtime` |
| `datetime { relativeTime }` | [`relativeTimeFormat`](#relativetimeformat). Writes the plural rules that choose the wording and the number tables that render its count. | `kotlinx-locale-datetime-cldr-runtime` |
| `datetime { durationUnits }` | [`durationFormat` and `durationUnitName`](#duration-units), the `2 hours` form rather than the `h:mm` of `durationPattern`. Writes the plural and number tables alongside. | `kotlinx-locale-datetime-cldr-runtime` |
| `language { names }` | [`Locale.displayName`, `nativeDisplayName`](#localedisplayname-and-localenativedisplayname), [`scriptName` and `regionName`](#localescriptname-and-localeregionname). The largest table in the library, and the one this plugin pays for most. | `kotlinx-locale-language-cldr-runtime` |
| `number { formats }` | [`numberFormat`](#numberformat), [`numberFormatPercent`](#numberformatpercent-and-numberformatpercentvalue), [`numberSymbols` and `numberParseOrNull`](#numbersymbols-and-numberparseornull) | `kotlinx-locale-number-cldr-runtime` |
| `number { compact }` | `1.2K` and `1.2 thousand`, through `notation`. Writes the plural rules its patterns are keyed by, so compact cannot pick the wrong form. | `kotlinx-locale-number-cldr-runtime` |
| `number { plurals }` | [`pluralCategory`](#pluralcategory), for choosing between translated strings. Carried whole rather than narrowed: four kilobytes covers every locale in CLDR. | `kotlinx-locale-number-cldr-runtime` |
| `number { ordinals }` | [`numberOrdinal`](#numberordinal). Writes the plural rules eight of the rule sets read. | `kotlinx-locale-number-cldr-runtime` |
| `timezone { formats }` | [`UtcOffset.displayName`](#utcoffsetdisplayname), the localized GMT format every other zone style degrades to | `kotlinx-locale-timezone-cldr-runtime` |
| `timezone { names }` | [`TimeZone.displayName`](#timezonedisplayname). Writes the format table, which every name falls back to. | `kotlinx-locale-timezone-cldr-runtime` |
| `timezone { exemplarCities }` | [`TimeZone.exemplarCity`](#timezoneexemplarcity) and the generic location format. The largest zone table by a wide margin; without it the location format uses the identifier's last part, which is the fallback UTS #35 prescribes. | `kotlinx-locale-timezone-cldr-runtime` |
| `personName { formats }` | [`personNameFormat` and `personNameOrder`](#person-names) | `kotlinx-locale-personname-cldr-runtime` |

Some flags bring another domain's entry points with them, because the two share a
source object. Any `timezone` flag writes the number binding, since the GMT
offset is rendered in the locale's own digits, so a build that asked only for
zone names also gets `numberFormat`. That is a larger generated source set, never
a different answer.

There is no `phone` flag. The metadata is keyed by territory rather than by
locale, so naming three locales would narrow nothing; take
`kotlinx-locale-phone-metadata-full` directly. See
[Phone numbers](#phone-numbers).

Narrowing only ever touches locale data. `Country.forAlpha2("br")` and
`Currency.forCode("jpy")` keep working whatever was generated, because an app
that displays three currencies can still be handed an arbitrary code by a
payment API.

## Errors, guarantees and versions

`format`, `displayName` and `symbol` never throw for any `Locale`. An unknown
locale falls back along the chain in
[what happens for a locale with no data](#what-happens-for-a-locale-with-no-data),
and names additionally fall back to the ISO code.

The throwing entry points are `Locale.of`, `Locale.forLanguageTag`, the
non-`OrNull` code lookups on `Country` and `Currency`, `CurrencyAmount.of`,
`CurrencyAmount.parse`, `CurrencyAmount.parseFormatted`, and `CurrencyAmount`
arithmetic or comparison across two different currencies. All of them throw
`IllegalArgumentException`, and every lookup that can fail has a non-throwing
alternative.

All types are immutable and safe to share between threads. Formatting allocates
its working state per call and touches no global mutable data.

The bundled data comes from CLDR `release-48-2` plus, for currency identity
(numeric codes and ISO minor units), the official ISO 4217 list one published
2026-01-01. Test fixtures and the ISO 4217 numeric cross-check come from ICU
`release-78.3`. Regeneration instructions are in the
[README](README.md#where-the-data-comes-from).
