# How the generated data is compressed

The tables ship as Kotlin source, because a Kotlin Multiplatform library has no
portable way to ship resources. Every byte of CLDR is a character in a string
literal, so the only lever is which characters are chosen.

All figures are against `origin/main`, in the unit each platform charges.

## What a platform charges for a character

| | how a literal is stored | cost |
| --- | --- | --- |
| Android, JVM | modified UTF-8 | 1 byte under U+0080, 2 under U+0800, 3 above |
| Kotlin/JS, Wasm | UTF-8 | the same |
| Kotlin/Native | UTF-16 | 2 bytes, every character alike |

Native is the expensive one: the same tables cost 12.8 MB on Android and 19.6 MB
on iOS, because Android gets a discount on ASCII and Native does not.

## Step one: take the keys out

A quarter of the data was key text. All 1122 locales repeated the same IANA zone
ids, ISO codes and BCP-47 subtags, and in `TimeZoneCities` the keys were more
than two thirds of the table.

Each field now stores one sorted key universe, written once, plus a pooled bitmap
saying which keys a locale has, then its values in key order. A record goes from

```
America/Sao_PauloSão PauloAmerica/BogotaBogotá
```

to a two-character pool index and two values.

## Step two: compress what is left

DEFLATE per record, not per table. Per table compresses about a quarter better
and is the wrong trade: a lookup would inflate 1122 locales to read one.

Compressed bytes cannot be a `ByteArray` literal. `byteArrayOf` compiles to code
rather than to a constant, and 11,000 elements already exceed the JVM's 64 KB
method limit. So the bytes ride in a string, and how many bits each character
carries is the whole question.

| packing | bits per character | UTF-8 | UTF-16 |
| --- | ---: | ---: | ---: |
| base64 | 6 | 1.33x | 2.67x |
| ascii7 | 7 | 1.14x | 2.29x |
| latin1 | 8 | 1.50x | 2.00x |
| bmp15 | 15 | 1.60x | 1.07x |

Two win, on opposite platforms. `ascii7` keeps every character below U+0080,
where UTF-8 charges one byte, and needs 8/7 as many characters. `bmp15` puts
every character above U+0800, where UTF-8 charges three bytes but UTF-16 still
charges two, and halves the character count.

So the generator writes both: `ascii7` into `utf8Main`, `bmp15` into `utf16Main`.
The registry is an `expect` in `commonMain` with an `actual` in each, and the
unpacking is `expect` beside it. Everything after unpacking is shared, including
the RFC 1951 inflate, which is written in common Kotlin because the standard
library has none and no platform library covers Wasm.

## Result

| table | Android | iOS |
| --- | ---: | ---: |
| TimeZoneNames | -85.0% | -90.1% |
| CurrencyPluralNames | -78.8% | -88.2% |
| TimeZoneCities | -77.0% | -85.7% |
| LocaleDisplayNames | -65.7% | -77.8% |
| CountryNames | -59.0% | -73.4% |
| CurrencyNames | -58.1% | -69.3% |

Across all generated data:

| | Android | iOS |
| --- | ---: | ---: |
| `origin/main` | 12.8 MB | 19.6 MB |
| now | 5.4 MB | 7.0 MB |
| | **-57.2%** | **-63.7%** |

Elision and compression are not alternatives. The keys sat between the values, so
removing them also lets DEFLATE match text across records it could not reach
before: elision is worth 24.5% uncompressed and 26.2% after compression.

## What it costs

Reading a record means unpacking and inflating it, about 13 microseconds, then it
is cached. Per record rather than per table is what keeps that cheap, and it
lowers memory too: the registry used to materialise all 1122 strings at class
init and now holds only the locales that were asked for.

## Options measured and dropped

| | why not |
| --- | --- |
| pooling whole values | 18% smaller uncompressed, 1.3% larger gzipped |
| ordering records by script | 14% off gzip, 2% off brotli, so a window artifact |
| a subword dictionary | 8.3% worse than doing nothing under brotli |
| `byteArrayOf` literals | will not compile past about 12 KB |
| one packing everywhere | costs iOS 3.1 MB, or the web 21% of its wire size |
