# What no standard defines

This library builds what Unicode defines and leaves alone what it does not. The
line matters more than it sounds, because a gap and a decision look identical
from the outside: both are things the API will not do for you. This file says
which is which.

Where something is half defined, the standardized half is here and the rest is
named below rather than guessed at.

## Country dialling codes

Not implemented, and not planned.

CLDR used to carry `telephoneCodeData`. It was deprecated in CLDR 34 and the
data removed, on the grounds that phone numbering changes far faster than CLDR
releases, with a pointer to libphonenumber. It is absent from `release-48-2`;
you can check the supplemental directory.

The underlying standard is ITU-T E.164, published as an Operational Bulletin
rather than as machine-readable data.

libphonenumber itself is Apache-2.0, so licence compatibility is not the
obstacle. Three other things are. Its metadata releases every week or two
against CLDR's twice a year, which is a different release model for the whole
project. Its validation is regular expressions evaluated against
`java.util.regex`, and Kotlin's `Regex` delegates to a different engine per
target, so "pure common Kotlin" would mean a behaviour surface that is not
identical across Android, iOS, JS and Native. And the roughly 240-entry dialling
table is one attribute of a 400 KB file whose interesting parts are parsing,
validation by number type, as-you-type formatting, geocoding and carrier
lookup.

A `kotlinx-locale-phone-*` domain would be a separate library decision rather
than an extension of this one. If you want it today,
[luca992/libphonenumber-kotlin](https://github.com/luca992/libphonenumber-kotlin)
and [bayo-code/kphonenumber](https://github.com/bayo-code/kphonenumber) exist.

## Choosing the unit for a relative time

`RelativeTimeFormatSource` takes the value and the unit from you. It will write
`in 90 minutes` or `in 2 hours`, and it will not decide which of those you
meant.

Nobody standardizes that. CLDR carries the wording for a given value and unit
and says nothing about when to switch units. `Intl.RelativeTimeFormat` takes the
unit from the caller. So does ICU's `RelativeDateTimeFormatter`. A chat app and
a changelog want different thresholds, so a ladder shipped here would look like
Unicode's opinion while being ours.

What the library does carry is the part that is standardized and easy to get
wrong by hand: the wording, the literal forms for the two days either side of
today, and the plural rules that pick among the four Czech forms of "N days".

## The default precision of compact notation

Implemented, with a value this library chose.

UTS #35 says the significant digits are "typically" two or three and then that
APIs may override. Typically is not a specification, and that latitude is why
two implementations of the same document disagree about the same number.

This library rounds half-even to whichever is the more precise of zero fraction
digits and two significant digits, which is what ICU and `Intl.NumberFormat`
both do, and holds it there with ICU goldens. The reasoning is in
`kotlinx-locale-number-core/README.md`.

## The rounding mode

Half to even, everywhere. LDML does not say. ICU and `java.text.DecimalFormat`
default to half-even; ECMA-402 rounds half away from zero. So `0.125` formatted
as a percentage with no fraction digits is `12%` here and `13%` in a browser.
Both are defensible; this one is written down.

## Ordinal forms for languages CLDR gives no rules for

Implemented, and the gap is smaller than it looks.

CLDR ships a `digits-ordinal` rule set for roughly forty locales. German, Czech,
Slovak, Croatian, Hungarian and Icelandic are not among them. They inherit
root's rule, which appends a full stop, and `1.` is the correct German and Czech
ordinal rather than a fallback that happens to look plausible.

What will not help is the ordinal plural category, which is single-valued in all
six of those languages. English is the outlier where it decides everything.

## Gendered and case-inflected ordinals

Not implemented.

CLDR ships them, thirty-two rule sets for Russian alone, and UTS #35 Part 3 says
plainly that it supplies no data for choosing between them. Exposing them would
hand a caller a decision nothing in the data can answer. If you need `1ª` rather
than `1º`, format the number and append the suffix yourself.

## Assembling text as the user types

The library hands out `NumberSymbols` and you assemble.

An amount field that formats while someone types has to preserve states a
formatter would normalise away: a trailing `5.`, a typed `1.50` that must not
collapse to `1.5`. It cannot round trip through `format`, because the round trip
destroys the information the caret position depends on. What it needs is the
locale's separators and digits, which is what `numberSymbols(locale)` returns,
and which is the same thing ICU's public `DecimalFormatSymbols` is for.

The other standardized answer is `formatToParts`, which hands back the pieces of
a formatted number rather than the string. That is a reasonable follow-up and is
not built yet.

## A currency code that does not resolve

`Currency.forCodeOrNull` returns null and `CurrencyAmount` requires a
`Currency`, and there is no wrapper type for an unresolvable code.

The standards already separate the cases, differently from a single `UNKNOWN`
entry. An absent field means there is no currency, not an unknown one. A
malformed code is an error: ECMA-402's `IsWellFormedCurrencyCode` throws a
`RangeError` for anything that is not three ASCII letters. A well-formed but
unassigned code formats with the code itself as its symbol and two fraction
digits, which is what ICU and `Intl` both do.

`XXX` is not the marker for any of those. CLDR defines it as "no currency", with
localized names in every locale, so it is a value you can hold rather than a
parse failure.

Carrying the withdrawn ISO 4217 codes shrinks the third case to almost nothing.
A code that still will not resolve after that is a wrong payload, which is an
error to handle rather than a value to render.

## Time zone naming at a past instant

The zone name API takes a style and an offset. It does not take an instant and
work out whether the zone was on daylight time then.

kotlinx-datetime 0.8.0 exposes `TimeZone.offsetAt` and nothing else: no
`inDaylightTime`, no standard offset, no transition list. Inferring the answer
is possible and this library does not, because the inference would be invisible
at the call site. Pass `STANDARD_LONG` or `DAYLIGHT_LONG` and you know which one
you got.

Only the current metazone is carried for the same reason. The full history with
its date ranges is what naming a zone at a past instant needs.

## Zone name parsing

Not implemented. UTS #35 defines it as eight steps of longest match across four
separate name spaces, and this library formats rather than parses dates.

## ISO 8601 zone formats

Not implemented, deliberately. `UtcOffset.toString()` already produces `+05:30`,
and the datetime pattern engine declines the `Z`, `X` and `x` field letters. A
second spelling of something kotlinx-datetime already does would be the wrong
kind of completeness.

## Flag emoji beyond countries

`Country.flagEmoji` covers the 249 ISO 3166-1 codes this library models.

Subdivision flags exist as UTS #51 tag sequences and there are exactly three of
them: England, Scotland and Wales. Their names live in a CLDR directory the
sparse checkout does not fetch. Three constants do not pay for a subdivision
concept, a new data path and a narrowing story.

Emoji unrelated to the entities this library models are out of scope entirely.
