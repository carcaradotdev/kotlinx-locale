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
#
# kotlinx-locale-types is here for the plugin rather than for the sample: the
# plugin exposes the catalog so a build script can write locales(PT.BR), and
# resolving the plugin from mavenLocal resolves what it exposes.
./gradlew $(for m in core types country-types country-core country-cldr-runtime \
                     currency-types currency-core currency-cldr-runtime \
                     number-core number-cldr-runtime \
                     datetime-core datetime-cldr-runtime; do
  printf ':kotlinx-locale-%s:publishKotlinMultiplatformPublicationToMavenLocal ' "$m"
  printf ':kotlinx-locale-%s:publishJvmPublicationToMavenLocal ' "$m"
done)

./gradlew -p samples/narrowed build
```

## What it shows

The dependency block is missing two things. There is no `-cldr-full` artifact:
the contract comes from `-core`, the lookup and formatting code from
`-cldr-runtime`, and the records are generated into this build. That is where
most of the saving comes from, since `-cldr-runtime` and `-cldr-full` are the
same engine and only one of them brings 1121 locales along. There is also no
`kotlinx-locale-types`, because `catalog = true` generates the three enums this
build can name instead of the 322 that artifact carries.

`build/generated/kotlinx-locale/` holds 306 KB of Kotlin across 59 files, of
which 205 KB is the tables and 3.3 KB the catalog. The tables in the five shipped
modules it replaces come to 6699 KB, so this is roughly a thirty-third of the
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
- `PT.BR` comes from `com.example.locale.catalog` rather than from the published
  catalog, and names one of the three locales this build generated. A language it
  did not declare does not compile.
- `Country.forAlpha2("br")` and `Currency.forCode("jpy")` still work, because
  this build narrowed its locales and its catalog but not its entry sets.

## What it deliberately does not do

There is no `country { entries(...) }` or `currency { entries(...) }` here, and
the last test above is why. Those flags generate the `Country` and `Currency`
enums with the entries you name and drop the shipped artifacts from the
classpath, which takes the territory and currency name tables down with them. It
is the largest saving the plugin can make in these two domains.

It is also the one that changes what the build can represent rather than only
what it carries. An unlisted locale answers in the fallback; an unlisted country
has no fallback, and `Country.forAlpha2OrNull("DE")` returns null once DE is out
of the enum. This sample resolves codes it did not choose, so it keeps both enums
whole. A build whose country list is a fixed set of shipping destinations is the
one that should narrow them.

One detail worth keeping: the expected value for a formatted BRL amount contains
U+00A0, because that is what CLDR puts between the symbol and the number in
pt-BR. Writing it out beats normalizing it away, since a change there would be a
real change in what a user sees.
