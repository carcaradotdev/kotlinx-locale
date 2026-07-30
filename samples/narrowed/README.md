# Narrowed locale data

A build that ships three locales instead of 1121.

It is a standalone Gradle build on purpose. It resolves kotlinx-locale from a
repository rather than through project dependencies, which is the only way to
find out whether the published artifacts work together rather than whether the
source tree does.

## Running it

The sample needs the plugin and the artifacts it generates against in a
repository it can see. From the repository root:

```sh
./gradlew \
  :kotlinx-locale-gradle-plugin:publishToMavenLocal \
  :kotlinx-locale-codegen-emitters:publishToMavenLocal \
  :kotlinx-locale-codegen-data:publishToMavenLocal

# The sample is JVM-only, so the JVM variants are all it needs. Publishing every
# target would take minutes and prove nothing extra here.
./gradlew $(for m in core types country-types country-core country-cldr-runtime \
                     currency-types currency-core currency-cldr-runtime \
                     datetime-core datetime-cldr-runtime; do
  printf ':kotlinx-locale-%s:publishKotlinMultiplatformPublicationToMavenLocal ' "$m"
  printf ':kotlinx-locale-%s:publishJvmPublicationToMavenLocal ' "$m"
done)

./gradlew -p samples/narrowed build
```

## What it shows

The dependency block is missing something: there is no `-cldr-full` artifact. The
contract comes from `-core`, the entry sets from `-types`, the lookup and
formatting code from `-cldr-runtime`, and the records themselves are generated
into this build. That is where the saving comes from: `-cldr-runtime` and
`-cldr-full` are the same engine, and only one of them brings 1121 locales along.

`build/generated/kotlinx-locale/` holds 124 KB of Kotlin across 20 files. The
shipped tables it replaces are 3764 KB, so this is roughly a thirtieth of the
data for a build that needs three locales.

The tests are the interesting part:

- `Country.BR.displayName(ptBr)` reads exactly as it does in a full build. The
  only difference is the import: `com.example.locale` here,
  `dev.carcara.kotlinx.locale.country.cldr` there.
- German was not generated, so it resolves through the configured fallback and
  answers in English rather than returning nothing. A full build would say
  "Brasilien".
- `supportedLocales` reports four locales, not 1121. The fourth is `pt`, kept
  because `pt-BR` inherits from it rather than because anyone asked for it.
- `Country.forAlpha2("br")` and `Currency.forCode("jpy")` still work. Narrowing
  touches locale data, never the entry sets, so a code arriving from a payment
  API still resolves and only its display name falls back.

One detail worth keeping: the expected value for a formatted BRL amount contains
U+00A0, because that is what CLDR puts between the symbol and the number in
pt-BR. Writing it out beats normalizing it away, since a change there would be a
real change in what a user sees.
