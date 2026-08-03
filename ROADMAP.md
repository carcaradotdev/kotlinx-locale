# Roadmap

What is intended and not yet built.

A decision to stop is a boundary and lives in
[docs/boundaries.md](docs/boundaries.md). Everything here is the opposite: work
that should happen. An item leaves this file when it ships, or moves to the
boundaries file if the answer turns out to be no. Nothing here has a date.

## Bank account identifiers

Validate and format an IBAN, keyed by the `Country` that already ships.

- Standards: [ISO 13616-1][iso13616-1] structure, [ISO/IEC 7064][iso7064] MOD
  97-10 check digits, [ISO 13616-2][iso13616-2] naming SWIFT the registration
  authority.
- Data: the [SWIFT IBAN registry][swift-iban], per country.
- Prior art, all Apache 2.0 and all carrying the per-country table:
  [Apache Commons Validator][commons-validator],
  [iban4j][iban4j], [java-iban][java-iban].
- Approach: follow the phone domain rather than the CLDR ones. Pin a published
  version of the registry, parse it in `:codegen`, hold the result to one of the
  implementations above.
- Note: declined once on the grounds that the registry's licence forbade it.
  That was wrong and [docs/boundaries.md](docs/boundaries.md) keeps the
  correction. Commons Validator has been through the Apache Software
  Foundation's licensing review carrying this exact data.

## Postal address formats

Render an address in the order its own country writes it.

- No Unicode standard. [PRI #180][pri180] proposed it in 2011 and never landed;
  `addressformdata.xml` is absent from CLDR `release-48-2`.
- Data: [libaddressinput][libaddress], Apache 2.0 source and CC-BY 4.0 data,
  served from an [endpoint][libaddress-data] rather than tagged releases.
- Approach: vendor a dated snapshot and generate from it, the way ISO 4217 list
  one and the emoji sequences are already handled. Nothing upstream ships.
- Open question: authority. Every other table here traces to a standards body
  and this one would trace to a dataset Google maintains for its own forms. Say
  so in the module's documentation.

## Payment card numbers

Group a PAN for display, and mask it.

- [ISO/IEC 7812-1][iso7812] defines what the digits mean, not how to print them.
  Grouping is a scheme convention: 4-4-4-4 for most, 4-6-5 for American Express.
- Data: [credit-card-type][credit-card-type], MIT, carries a `gaps` array per
  brand which is exactly the grouping.
- Approach: same as phone numbers, which is the precedent that matters here.
  ITU-T E.164 does not define national grouping either; libphonenumber's
  metadata does, and this library already ships it. "Convention rather than
  standard" is not a reason this project declines data.
- Note: also declined once, on the grounds that no standard defines it. True,
  and not the point.

## Week numbering

The `w`, `W` and `F` pattern fields, and the numeric forms of `e` and `c`.

- Data already ships: `WeekInfo` carries the first day and the minimum days per
  territory, which is what these fields were missing.
- What is left is goldens. The turn of the year has enough edge cases that
  enabling them unchecked would be guessing.
- Where: `UNSUPPORTED_FIELD_LETTERS` in `codegen/.../Flatten.kt`.

## Close the conformance exclusions

Six locales are excluded by name from the person name suite, and each is a bug
rather than a boundary. They should close before anything is published.

- Catalan, Czech, Sardinian and Slovak: `{surname-prefix}` on a surname with no
  prefix. ICU answers with the whole surname; this answers null, so a pattern
  opening with that field reads as starting empty. Copying ICU's answer alone
  regresses Afrikaans, because the mononym rule has already moved a lone given
  name into the surname and it then prints twice. Both rules move together.
- Assamese and Telugu: one initial too many or too few in a field with no
  spaces in it. A word boundary question rather than a cluster one, since the
  clusters themselves now follow UAX #29.
- The reference for both is `PersonNamePattern.java`, now in the ICU checkout.
- Catalan, Czech, Sardinian and Slovak: which literal survives when the field
  between two of them is empty. The rule keeps the first separator and these
  patterns want the bracketing one.

The list is pinned by an assertion on its size so it cannot grow unnoticed. A
second exclusion, the eight locales whose words are not space-separated, is a
genuine boundary rather than a bug and is recorded as one.

The interval suite has no exclusions: all 905 locales it shares with ICU agree.

## Platform sources for the remaining targets

- `-platform` reads no locale data on Linux, Windows, Android Native or
  Wasm-WASI. The bundled tables answer on all of them.
- Windows and Linux both have locale facilities to read, so this is unbuilt
  rather than impossible.

[iso13616-1]: https://www.iso.org/standard/81090.html
[iso13616-2]: https://www.iso.org/standard/81091.html
[iso7064]: https://www.iso.org/standard/31531.html
[iso7812]: https://www.iso.org/standard/70484.html
[swift-iban]: https://www.swift.com/standards/data-standards/iban-international-bank-account-number
[commons-validator]: https://github.com/apache/commons-validator/blob/master/src/main/java/org/apache/commons/validator/routines/IBANValidator.java
[iban4j]: https://github.com/arturmkrtchyan/iban4j
[java-iban]: https://github.com/barend/java-iban
[credit-card-type]: https://github.com/braintree/credit-card-type
[pri180]: https://unicode.org/review/pri180
[libaddress]: https://github.com/google/libaddressinput
[libaddress-data]: https://chromium-i18n.appspot.com/ssl-address
