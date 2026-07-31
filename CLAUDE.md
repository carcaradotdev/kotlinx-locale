# CLAUDE.md

Locale support for Kotlin Multiplatform, written entirely in common Kotlin. The
data comes from CLDR, compiled into Kotlin source by the generator in `codegen/`.
Artifacts are named `kotlinx-locale[-<domain>]-<layer>`, and `settings.gradle.kts`
carries the authoritative list of what publishes.

## Editing README.md and API.md

These two files are what someone reads before they decide whether to use the
library, and they are the only description of the API outside the source. They
drift silently, because prose does not fail to compile.

What follows is process rather than style. None of it says how to phrase a
sentence; it says what to do before and after writing one.

### Load the humanizer skill

Invoke the `humanizer` skill before writing and apply it to the finished text.
Both files were rewritten under it and hold to it today: no em or en dashes,
sentence-case headings, no promotional vocabulary, no bold-header lists, no
rule-of-three padding.

The same applies to the PR description for a docs change, and to the commit
message.

The one deliberate exception is the platform coverage table in the README, which
uses 🟢 and 🟡 because a status matrix reads better with markers. That was asked
for explicitly. Do not extend emoji to the rest of either file.

### Read the reference projects first

The README follows the shape the kotlinx libraries use, so a reader arriving from
one of them already knows where to look:

- <https://github.com/Kotlin/kotlinx-datetime>
- <https://github.com/Kotlin/kotlinx.serialization>
- <https://github.com/Kotlin/kotlinx.coroutines>

Read them before restructuring anything. If a section here has no counterpart
there, that is worth a second look in both directions: it may be the thing this
library genuinely needs to explain, or it may be a section that should not exist.

### API.md documents what a consumer calls, not the ABI

The public ABI is larger than the API a user writes. Source interfaces, the
`Cldr*` and `Platform*` objects, `Payload*` classes, the record types and the
pattern machinery are all public, and none of them belong in API.md: the
extensions resolve to the right source on their own, so documenting both teaches
a reader to reach for the wrong one.

The test is whether a consumer types the name. If the only way to reach a
declaration is through an extension that already exists, it stays out.

The one exception is combining the host's data with the bundled tables, which
does require naming the source objects. That lives in the README, and API.md
points at it.

### Verify every claim against the source

Do not carry a sentence forward because it was already there. Rewrites of these
files have turned up a limitation the previous commit had already lifted, a
target count off by two, and a paragraph saying Windows exposes no locale data
sitting directly above a table saying Windows reads `GetUserDefaultLocaleName`.

Where the facts live:

| Claim | Source of truth |
| --- | --- |
| Published artifacts | the `published` list in `settings.gradle.kts` |
| Kotlin targets | `build-logic/src/main/kotlin/kotlinx-locale-multiplatform-base.gradle.kts` |
| Public signatures | the committed `<module>/api/` dumps, plus the Kotlin sources for default arguments |
| Sizes | `docs/size.md`, regenerated with `./gradlew updateSizeDoc` |
| CLDR, ICU and ISO 4217 versions | `codegen/src/main/kotlin/.../Repos.kt` and `gradle/libs.versions.toml` |
| Per-target platform behaviour | the `actual` declarations in each `-platform` module |

Numbers get copied from where they are generated, never typed from memory. If a
number cannot be traced to one of those, say what it is measured on or leave it
out. Source size and gzipped bundle size are different units and do not belong
in the same column.

### Check the mechanical things before opening a PR

These are cheap and catch real breakage:

- The version catalog in the README parses, and every accessor it tells a reader
  to write resolves. Copy the TOML into a throwaway Gradle build and touch each
  one. This has already caught a bundle alias that was a prefix of another.
- Every published artifact is mentioned somewhere in the README.
- Internal links and anchors resolve, in both directions between the two files.
- Code fences balance and every table has a consistent column count.
- No em or en dashes survived.

### When the API changes

A commit that changes the public surface updates API.md in the same commit, the
way it updates the `api/` dumps. A new artifact also lands in the README's module
table, its version catalog and any bundle it belongs to.
