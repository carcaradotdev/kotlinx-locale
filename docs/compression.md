# How the generated data is compressed

The tables ship as Kotlin source, because a Kotlin Multiplatform library has no
portable way to ship resources. Every byte of CLDR is a character in a string
literal, so the only lever is which characters are chosen.

Two questions decide that, and they cut across each other.

## What a platform charges for a character

| platform | stores a literal as | cost |
| --- | --- | --- |
| Android, JVM | modified UTF-8 | 1 byte under U+0080, 2 under U+0800, 3 above |
| Kotlin/JS, Wasm | UTF-8 | the same |
| Kotlin/Native | UTF-16 | 2 bytes, every character alike |

## Whether the artifact arrives compressed

A jar, an aar, a dex and a native binary do not. Every character is a character
installed, so compressing the records pays. A Kotlin/JS bundle is gzipped in
transit, and gzip cannot compress what is already compressed, so pre-compressing
there trades a smaller file for a larger download.

Android and Kotlin/JS answer the first question the same way and the second
differently, so neither question alone gives the right answer for both.

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

This runs for every platform. It shrinks the data and it helps gzip, because
removing the keys lets a compressor match value text across records it could not
reach before.

## Step two: compress what is left, where compressing pays

DEFLATE per record, not per table. Per table compresses about a quarter better
and is the wrong trade: a lookup would inflate 1122 locales to read one.

Compressed bytes cannot be a `ByteArray` literal. `byteArrayOf` compiles to code
rather than to a constant, and 11,000 elements already exceed the JVM's 64 KB
method limit. So the bytes ride in a string, and how many bits each character
carries is the next question.

| packing | bits per character | UTF-8 | UTF-16 |
| --- | ---: | ---: | ---: |
| base64 | 6 | 1.33x | 2.67x |
| ascii7 | 7 | 1.14x | 2.29x |
| latin1 | 8 | 1.50x | 2.00x |
| bmp15 | 15 | 1.60x | 1.07x |

`ascii7` keeps every character below U+0080, where UTF-8 charges one byte, and
needs 8/7 as many characters. `bmp15` puts every character above U+0800, where
UTF-8 charges three bytes but UTF-16 still charges two, and halves the character
count.

So three source sets, one per answer:

| source set | targets | form |
| --- | --- | --- |
| `utf8PlainMain` | js, wasmJs | keys elided, records readable |
| `utf8DeflatedMain` | jvm, android | keys elided, deflated, seven bits per character |
| `utf16Main` | every native target, wasmWasi | keys elided, deflated, fifteen bits per character |

wasmWasi sits with the native targets rather than with the other two Wasm ones.
Kotlin/Wasm-JS compiles a string literal to an imported extern whose field name
is the text itself, so it pays UTF-8; wasmWasi stores its own strings and charges
UTF-16 for anything outside Latin-1. Measured on a linked binary: fifteen bits a
character beats seven there by 6.6%, and loses to it on wasmJs by 39%.

The registry is an `expect` in `commonMain` with an `actual` in each, and the
unpacking is an `expect` beside it. Everything after unpacking is shared,
including the RFC 1951 inflate, which is common Kotlin with no `expect`/`actual`
in it, so every target runs the same reader.

## Result

| all generated data | Android, JVM | Kotlin/JS, Wasm | Kotlin/Native |
| --- | ---: | ---: | ---: |
| 0.1.0 | 12,840 KB | 12,840 KB | 19,624 KB |
| now | 3,911 KB | 10,610 KB | 4,134 KB |

The Kotlin/JS column is the largest because nothing there is deflated, and that
is the point: it is the column that gets compressed again on the way out. What
the consumer downloads is in `docs/size.md`, and every scenario there is smaller
than it was before any of this work: `everything` 911.0 to 793.3 KB,
`language-full` 1179.3 to 892.4, `timezone-cities` 885.8 to 668.7,
`currency-plurals` 811.2 to 647.9.

## What it costs

Reading a record on a deflating target means unpacking and inflating it, about 13
microseconds, and then it is cached. Per record rather than per table is what
keeps that cheap, and it lowers memory too: the registry used to materialise all
1122 strings at class init and now holds only the locales that were asked for.

## What stays readable everywhere

`pluralRuleSets` and `ordinalRuleSets` are read as `ruleSets[id]` by rule id
rather than by locale tag, so they never pass the lookup that inflates. They are
seven kilobytes between them and would save about one.

`numberSymbols`, `numberPatterns`, `pluralRuleIndex` and `ordinalRuleIndex` hold
records of two to six characters, where the length header and the DEFLATE block
header cost more than the record. `ordinalRuleIndex` grows 260% if compressed.

## Options measured and dropped

| | why not |
| --- | --- |
| pooling whole values | 18% smaller uncompressed, 1.3% larger gzipped |
| ordering records by script | 14% off gzip, 2% off brotli, so a window artifact |
| a subword dictionary | 8.3% worse than doing nothing under brotli |
| `byteArrayOf` literals | will not compile past about 12 KB |
| one packing everywhere | costs Native 3.1 MB, or the web 21% of its wire size |
| deflating for Kotlin/JS | up to 89% larger gzipped, because gzip cannot compress twice |
