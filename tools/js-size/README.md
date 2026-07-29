# JS bundle size probe

Answers "how many bytes does this library add to a JavaScript or TypeScript
app?" for each module on its own and for any combination of them.

```
node scripts/js-size.mjs
```

```
scenario                           minified      gzip    brotli  gzip over baseline
---------------------------------  --------  --------  --------  ------------------
baseline (Kotlin/JS runtime only)     523 B     311 B     273 B                 n/a
locale                              50.8 KB   17.4 KB   14.6 KB            +17.1 KB
country                              1.19 MB  427.8 KB  252.5 KB           +427.5 KB
currency                             2.18 MB  748.5 KB  448.9 KB           +748.2 KB
kotlinx-datetime (third-party)      77.1 KB   23.7 KB   20.2 KB            +23.4 KB
datetime                           807.4 KB  115.8 KB   84.0 KB           +115.5 KB
all modules                          2.91 MB  845.1 KB  515.8 KB           +844.8 KB
```

## What the number means

The bulk of every module is CLDR data, and Kotlin/JS strips whatever a program
does not reach. A measurement is therefore only meaningful against a stated
usage, so this probe states the widest one: **every public declaration reachable
from JS**.

`src/probe/<module>/kotlin` holds one `@JsExport` facade per module that calls
every public declaration that module has: every property of every `Country`
and `Currency` entry, `displayName` over the full enum, every `FormatStyle` and
`TextStyle`, every factory and parser. Results feed the return value and inputs
arrive as parameters, so neither Kotlin's DCE nor terser can fold a call away
and drop the data behind it. The figure is an upper bound: a consumer who only
calls `Currency.forCode` ships less.

The bundle is a Kotlin/JS production link (DCE on) bundled by webpack in
production mode (terser). That is what a real app ships. `gzip` uses level 9 and
`brotli` quality 11, matching a static asset pipeline.

The **baseline** row is an empty `main()` with no library at all, so it measures
the Kotlin/JS runtime floor. Every other row pays it too, so the last column subtracts it and
leaves the part that is actually this library.

The **kotlinx-datetime** row is not one of our modules. The datetime module
exposes `LocalDate`, `LocalTime`, `LocalDateTime`, `Month` and `DayOfWeek` in
its signatures, so a consumer cannot avoid bundling them; the row measures that
dependency alone, so the datetime figure can be read as "ours plus this".

## Scenarios

Any comma-separated combination of `locale`, `country`, `currency`, `datetime`
and `kotlinx-datetime`, plus `all` and `none`:

```
node scripts/js-size.mjs currency               # a consumer who only uses currency
node scripts/js-size.mjs country,datetime       # two modules, one bundle
node scripts/js-size.mjs --json                 # byte counts for a CI budget check
node scripts/js-size.mjs --markdown             # a table to paste into a PR
```

Picking a module also picks the modules it exposes through `api` dependencies,
so `currency` implies `country` and `locale`, because their types are part of
its public surface. The bundles are left in `build/js-size/` so you can grep them
for what actually survived.

Gradle can also be driven directly:

```
./gradlew -p tools/js-size jsBrowserProductionWebpack -Pmodules=country,currency
```

## Keeping it honest

The facades are hand-written, so a new public declaration is not measured until
it is added to the matching `src/probe/<module>/kotlin` file. The script fails
when a scenario exports nothing, which catches a facade that dropped out of the
build, but it cannot catch a facade that fell behind the API. Check the facades
against `API.md` when the public surface changes.

## Why a separate build

This build is deliberately absent from the root `settings.gradle.kts`. It drags
in the webpack toolchain, which would otherwise land in the root
`kotlin-js-store/yarn.lock` and make every CI `./gradlew build` bundle a
multi-megabyte probe. It consumes the library through `includeBuild("../..")`,
so it always measures the working tree rather than a published artifact.
