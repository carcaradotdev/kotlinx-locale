# Where the line is

This library builds what a standard defines and leaves alone what it does not.
The line matters more than it sounds, because from the outside a gap and a
decision look identical: both are things the API will not do for you. This file
says which is which.

Mostly that standard is Unicode, and where something is half defined the
standardized half is built and the rest is named here rather than guessed at.
Not always, though. Phone numbering is ITU-T E.164 by way of libphonenumber, and
the entry below on dialling codes is here because this file once got that wrong:
it recorded "CLDR stopped shipping the data" as "no standard exists" and left a
whole domain unbuilt on the strength of it. The correction is kept rather than
deleted, because a scope document that only ever grows is one nobody trusts.

An entry here is a stopping point, not a to-do. Work that is intended and not
yet built lives in [the roadmap](../ROADMAP.md), and an entry moves between the
two files when the answer changes.

So an entry here says one of four things: this is standardized and built, and
here is the edge; this is half standardized, and here is the half we chose; this
is standardized and not built, and here is what stopped it, which is sometimes
the size of the data and sometimes its licence; or this is not standardized at
all, and here is why guessing would be worse than declining.

The third of those is why the file is not called anything with "unstandardized"
in it. Most of what is written down here is specified perfectly well. The
question each entry answers is where this library stops, not where Unicode
does.

## Country dialling codes

Implemented, in `kotlinx-locale-phone-*`.

An earlier version of this file listed them here as unstandardized. That was
wrong twice over, and the correction is worth keeping rather than quietly
deleting.

The first error was treating "CLDR stopped shipping the data" as "no standard
exists". CLDR did carry `telephoneCodeData` and did deprecate it in CLDR 34,
pointing at libphonenumber, and it is absent from `release-48-2`. None of that
says anything about whether a standard exists. The numbering plans are ITU-T
E.164, and libphonenumber is the machine-readable form of them that the industry
actually maintains and that every mobile platform already ships. It is
Apache-2.0. Declining to use it was a decision about effort, described as a
decision about standards.

The second error was the technical one, and it was the load-bearing argument.
libphonenumber validates with regular expressions; Kotlin's `Regex` delegates to
a different engine on every target; therefore, the reasoning went, a pure common
Kotlin port would validate the same number differently on Android and in a
browser. The premise is true and the conclusion does not follow, because it
assumes the patterns need a general engine. They do not. Across all 2292
patterns in the metadata the constructs are alternation, character classes with
ranges, `\d`, non-capturing groups, bounded repetition, the optional marker, and
an end anchor that appears only in the national-prefix rules. No backreferences,
no lookaround, no unbounded quantifiers, not one dot.

So the library evaluates that subset itself, and `:codegen` fails the build
naming the offending pattern if a libphonenumber release ever steps outside it.
The behaviour is identical on every target because nothing target-specific is
involved.

What remains true from the old entry is the release cadence: libphonenumber
ships every week or two against CLDR's twice a year. That is a reason to pin a
tag and say which one, which is what `PHONE_REPO` in `codegen/Repos.kt` does, and
bumping it is a deliberate commit like bumping CLDR.

## Phone number geocoding and carrier lookup

Not implemented yet, and the reason is size rather than standards.

libphonenumber's geocoding data is 11 MB across 30-odd languages and its carrier
data is 1.3 MB. Both are per-prefix maps that answer a different question from
the one this domain answers, and both would want to be their own artifacts with
their own opt-in. They are also the only parts of libphonenumber that are
locale-keyed, so they would follow the `-cldr-full` shape rather than the phone
domain's region-keyed one.

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

## A generic zone name for a place that stopped changing its clocks

`GENERIC_LONG` for Phoenix reads `Mountain Time` here and `Mountain Standard
Time` in ICU. Sao Paulo and Mexico City are the same.

ICU is doing something reasonable: those zones no longer observe daylight
saving, so a generic name implies a switch that will not happen, and the standard
name is the more truthful answer. Reaching it means knowing whether a zone
observes daylight saving at all, which is a tzdb question, and this domain is
built not to ask tzdb anything. The API takes the style from the caller so that
the same call gives the same answer on every target, and a name that quietly
changed because the JDK image shipped a newer tzdb than the browser would undo
that.

Pass `STANDARD_LONG` when you want the standard name. The conformance fixture
records this divergence rather than asserting either behaviour.

## A zero offset

`OFFSET_LONG` at zero reads `GMT` here, and `UTC` in French. ICU writes
`GMT+00:00`.

UTS #35 gives every locale a `gmtZeroFormat` and says it is what a zero offset
reads as, so this follows the specification. ICU's `TimeZoneFormat` documents
that it writes the offset whatever the value. Both are defensible and this one is
written down.

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

## Formatting a duration

Standardized, and smaller than it sounds. CLDR's `durationUnit` gives three
patterns, `h:mm`, `h:mm:ss` and `m:ss`. Across all 1122 locale files in
release-48-2, only Finnish and Danish override them, both writing a full stop
where root writes a colon. That is the entire worldwide variation.

So `durationPattern` hands back a pattern rather than formatting anything. The
decision it will not make for you is which components to show: whether 3660
seconds reads as `1:01` or `1:01:00` is not answered by CLDR, ECMA-402 or ICU,
all of which take that from the caller. It is the same boundary as choosing the
unit for a relative time, above.

`durationFormat` is the other name and the other thing. It writes the `duration-*`
measurement units and their plural forms, which is where the genuinely
locale-varying duration data is, and it lives in
`kotlinx-locale-datetime-cldr-durations` rather than beside the pattern.

## Deriving a person's initials without word boundaries

Not implemented for eight locales, and the reason is data rather than a
standard. UTS #35 Part 8 says an initial is taken from each word of a field. In
Khmer, Lao, Burmese, Shan and the Chinese locales the words in a field are not
separated by spaces, so finding them needs the dictionary a word-break iterator
carries. Those dictionaries are larger than this whole domain.

Guessing would be worse than declining: an initial taken from the wrong place is
wrong in a way nobody can see without reading the script. The affected locales
are named in the conformance test and counted rather than silently skipped.

## The script a name is written in

Not implemented. UTS #35 Part 8 infers a name's locale from the script of its
characters when the caller supplies none, and this library asks for the locale
instead.

The reason is size. Inferring the script needs the Unicode Script property,
which is a couple of thousand ranges, larger than the person name tables it
would support. The two things the inference feeds are cheaper another way: which
space joins the parts is decided by comparing languages, and the re-locale rule
it also feeds is exercised by none of CLDR's 36,960 test cases.

## Diacritic-insensitive search

Not implemented, and an approximation would be worse than nothing.

The correct answer is the `search` collation type in UTS #35 Part 5, comparing
at the primary level of UTS #10. That is out of reach: it needs the default
collation table, the per-locale tailorings and normalization underneath, which
together are larger than everything this library currently ships.

The cheap version, decomposing and dropping the combining marks, is not the same
thing and is wrong in ways that matter. Whether a mark is decoration on a base
letter or part of a distinct letter is a per-language decision: in German ö is a
variant of o and should match it, in Swedish it is its own letter near the end
of the alphabet and should not, and Turkish keeps dotted and dotless i apart.
One global rule cannot serve all three, and shipping it under a locale-aware
name would invite callers to trust it for exactly the cases it gets wrong.

## Domestic bank account identifiers

Not implemented, and this one is a decision rather than a gap in the way the
IBAN entry turned out to be.

Sort codes, BLZ and the rest are in the ISO 13616 registry as a BBAN field
layout, which says how many digits each part has. It does not say how they are
written. Nothing in the registry says a British sort code is printed as three
pairs separated by hyphens; that is a convention people learned from cheque
books.

So a formatter here would be this library inventing a presentation and
presenting it as a standard, for every country separately. The IBAN itself is
different, because its grouping in fours is in the standard, and it is on the
[roadmap](../ROADMAP.md).
