# API reference

The API spans two modules and two packages:

```kotlin
// dev.carcara:kotlinx-locale
import dev.carcara.kotlinx.locale.Locale

// dev.carcara:kotlinx-locale-datetime
import dev.carcara.kotlinx.locale.datetime.*
```

The base module provides `Locale`. The datetime module adds `FormatStyle` and
`TextStyle` plus extension functions on the kotlinx-datetime types
`LocalDate`, `LocalTime`, `LocalDateTime`, `Month` and `DayOfWeek`. Depending
on `kotlinx-locale-datetime` pulls the base module in transitively. All
examples below are real output for the date 2026-07-27 (a Monday) and the
time 15:05:09.

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

### Locale.availableLocales

```kotlin
Locale.availableLocales.size   // 1121
```

Every locale with bundled CLDR data, sorted by tag. You do not have to pick
from this list: formatting accepts any `Locale` and falls back as described
next.

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
| ar-EG | ٣:٠٥:٠٩ م | ٣:٠٥:٠٩ م | ٣:٠٥:٠٩ م | ٣:٠٥ م |

Two things to know:

- CLDR's FULL and LONG time patterns end in a time-zone name (`zzzz`, `z`).
  A `LocalTime` has no zone, so the library drops those fields and the
  whitespace around them. That is why FULL, LONG and MEDIUM look identical in
  many locales here.
- Twelve-hour locales handle noon and midnight the CLDR way: 00:30 is
  `12:30 AM` and 12:30 is `12:30 PM` in `en`. The separator before AM/PM is
  U+202F (narrow no-break space), not a regular space, matching CLDR 48.

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

## Errors, guarantees and versions

`format` and `displayName` never throw for any `Locale`: an unknown locale
falls back along the chain in [Fallback resolution](#fallback-resolution) and
ends at CLDR root. The only throwing entry points are `Locale.of` and
`Locale.forLanguageTag`, both with `IllegalArgumentException`, and both have
non-throwing alternatives (`forLanguageTagOrNull`, or validating input
yourself).

All types are immutable and safe to share between threads. Formatting
allocates its working state per call and touches no global mutable data.

The bundled data comes from CLDR `release-48-2`. Test fixtures are extracted
from ICU `release-78.3`. Regeneration instructions are in the
[README](README.md#where-the-data-comes-from).
