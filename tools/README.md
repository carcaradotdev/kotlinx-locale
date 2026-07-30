# Size probes

Twelve Kotlin/JS applications, one per dependency set worth knowing the price of.
Each declares a gzipped ceiling for its minified bundle, and `checkSize` fails
the build when it goes over.

```sh
./gradlew sizeReport      # build every probe and print the table
./gradlew checkSize       # just the budgets, which is what CI runs
```

The budgets exist to turn a dependency mistake into a build failure rather than
into 400 KB nobody notices. If `country-types` grew an edge into
`country-cldr`, the `country-codes` probe would jump from roughly 15 KB to
roughly 420 KB and say so by name.

## Where the numbers live

[`docs/size.md`](../docs/size.md) holds the current table, including the
platform against CLDR comparison, and is generated:

```sh
./gradlew updateSizeDoc    # regenerate it
./gradlew checkSizeDoc     # fails when it no longer matches, and runs in check
```

It is generated because the numbers were worth quoting in the prose, and four
hand-copied tables would have disagreed with each other within a release. The
check compares scenario names exactly, so a probe added without regenerating
fails the build, and compares the figures within 10%, since a bundle moves a
little with the toolchain and a byte-exact check would only teach people to
regenerate without reading.

Two rows read oddly and are worth knowing about before you compare the wrong
pair. `currency-platform` lands below `currency-codes` because that probe calls
`Country.currency` and therefore carries the country-to-currency table and the
`Country` enum, which the platform probe never touches. And what is left in
`country-platform` is mostly the 249-entry `Country` enum, which is why entity
narrowing, not more platform work, is what would move that number next.

Kotlin/JS is the only target with dead code elimination, so it is the only one
where "what does this artifact actually cost" has an answer a build can check.
The same module boundaries are what buy the saving on JVM, Android and Native,
where nothing is eliminated and the dependency block is the only lever there is.

## Reading a probe

A probe is a single `@JsExport` function that touches the API of the set being
measured. The export is what marks it as a dead-code-elimination root: without
it the compiler deletes the whole thing and the bundle measures nothing, which
is a mistake worth knowing about, because an empty probe still builds and still
passes its budget.

If you change a probe, check the number moves the way you expect before you
change its budget. A budget raised to make a build green is a budget that has
stopped measuring anything.

## Raising a budget

Find out which of the two things happened first:

- **the data grew** — a CLDR upgrade adding locales is a legitimate reason, and
  the new number is the new normal;
- **the graph grew** — an artifact started pulling something it should not, and
  the fix is the dependency, not the budget.

`checkSize` prints the raw and gzipped sizes for both, and warns when a probe
uses under half its budget, since a ceiling that loose measures nothing either.
