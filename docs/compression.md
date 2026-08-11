# How the generated data is compressed

The tables ship as Kotlin source, because a Kotlin Multiplatform library has no
portable way to ship resources. So every byte of CLDR is a character in a string
literal, and the only lever is how those characters are chosen.

All figures below are against `origin/main`, in the unit each platform charges.

## What a platform charges for a character

| | how a literal is stored | cost |
| --- | --- | --- |
| Android, JVM | modified UTF-8 | 1 byte under U+0080, 2 under U+0800, 3 above |
| Kotlin/JS, Wasm | UTF-8 | the same |
| Kotlin/Native | UTF-16 | 2 bytes, every character alike |

Native is the expensive one. The same tables cost 12.8 MB on Android and 19.7 MB
on iOS, because Android gets a discount on ASCII and Native does not.

## Step one: take the keys out

A quarter of the data was key text. Every one of 1122 locales repeated the same
IANA zone ids, ISO codes and BCP-47 subtags. In `TimeZoneCities` the keys were
more than two thirds of the table.

So each field now stores one sorted key universe, written once, plus a pooled
bitmap saying which keys a locale has, then its values in key order. A record
goes from

```
America/Sao_PauloSão PauloAmerica/BogotaBogotá
```

to a two-character pool index and two values.

| | Android | iOS |
| --- | ---: | ---: |
| `origin/main` | 12.8 MB | 19.7 MB |
| keys elided | 10.4 MB | 14.9 MB |

## Step two: compress what is left

DEFLATE per record, not per table. Per table compresses about a quarter better
and is the wrong trade: a lookup would inflate eleven hundred locales to read
one, and an application that wants three locales would pay for all of them.

Compressed bytes cannot be a `ByteArray` literal. `byteArrayOf` compiles to code
rather than to a constant, and eleven thousand elements already exceed the JVM's
64 KB method limit. The bytes therefore ride in a string, and how many bits each
character carries is the whole question.

| packing | bits per character | UTF-8 | UTF-16 |
| --- | ---: | ---: | ---: |
| base64 | 6 | 1.33x | 2.67x |
| ascii7 | 7 | 1.14x | 2.29x |
| latin1 | 8 | 1.50x | 2.00x |
| bmp15 | 15 | 1.60x | 1.07x |

Two packings win, and they win on opposite platforms. `ascii7` keeps every
character below U+0080, where UTF-8 charges one byte, and needs 8/7 as many
characters. `bmp15` puts every character above U+0800, where UTF-8 charges three
bytes but UTF-16 still charges two, and halves the character count.

Neither is right everywhere, so the generator writes both: `ascii7` into
`utf8Main`, `bmp15` into `utf16Main`. The registry is an `expect` in common with
an `actual` in each, and the unpacking is `expect` beside it. Everything after
unpacking is shared.

## Result

Per table, against `origin/main`, each platform reading the packing chosen for
it:

| table | Android | iOS |
| --- | ---: | ---: |
| TimeZoneNames | -84.5% | -89.2% |
| TimeZoneCities | -75.6% | -83.8% |
| CurrencyPluralNames | -74.0% | -82.6% |
| LocaleDisplayNames | -64.3% | -75.3% |
| CurrencyNames | -61.4% | -73.1% |
| CountryNames | -58.6% | -72.7% |

Across all generated data:

| | Android | iOS |
| --- | ---: | ---: |
| `origin/main` | 12.8 MB | 19.7 MB |
| keys elided | 10.4 MB | 14.9 MB |
| keys elided and compressed | 2.9 MB | 2.6 MB |

Elision and compression are not alternatives. The keys sat between the values, so
removing them also lets DEFLATE match text across records it could not reach
before: elision is worth 24.5% uncompressed and 26.2% after compression.

## What it costs

Reading a record means unpacking and inflating it, about 13 microseconds with an
optimised zlib, then it is cached. Doing it per record rather than per table is
what keeps that cheap, and it lowers memory as well: the registry used to
materialise all 1122 strings at class init, and now holds only the locales that
were asked for.

Kotlin has no inflate in its common standard library, so there is one in
`kotlinx-locale-core`, written against RFC 1951 and checked against
`java.util.zip` over random input. One implementation in common code reaches
every target, including Wasm, where none of the platform libraries would.

## Options that were measured and dropped

| | why not |
| --- | --- |
| pooling whole values | 18% smaller uncompressed, 1.3% larger gzipped |
| ordering records by script | 14% off gzip, 2% off brotli, so mostly a DEFLATE window artifact |
| a subword dictionary | 8.3% worse than doing nothing under brotli |
| `byteArrayOf` literals | will not compile past about 12 KB |
| one packing everywhere | costs iOS 3.1 MB, or the web 21% of its wire size |
