# The divergence ledger

Where this library and ICU give different answers, and why.

These files are read by `:conformance-icu:test` and written by
`./gradlew :conformance-icu:updateLedger`. They sit outside every source set on
purpose, so nothing here can reach a compilation or a test binary.

## What is in here and what is not

`docs/standards.md` describes four kinds of divergence. Three of them are
derivable from the ICU4J jar, so they are counted in `counts.tsv` rather than
listed:

| Kind | Derived how |
| --- | --- |
| `SNAPSHOT_SKEW` | ICU was built from a different CLDR release and the value moved between them |
| `BUNDLE_FALLBACK` | ICU has no bundle for the locale and answered from an ancestor |
| `ICU_PRUNED` | CLDR has data and ICU shipped root's value instead |

Writing those out would be thousands of rows nobody reads. What is worth
noticing is the number moving, which is what `counts.tsv` pins.

The two that are listed one row at a time are the ones only a person can
explain:

| Kind | Meaning |
| --- | --- |
| `DEFECT` | ICU is wrong. The note says how, ideally with an upstream link |
| `DELIBERATE` | This library answers differently on purpose. The note names the `docs/boundaries.md` entry |

A `DEFECT` or `DELIBERATE` row with an empty note fails at write time, so
`updateLedger` cannot produce an unexplained row.

## The three rules

Enforced by `:conformance-icu:test`:

1. A divergence that is not in the ledger fails the build. This is what makes
   the ledger a test rather than a report.
2. A ledgered divergence that no longer reproduces fails too. Without this the
   file silently accumulates rows that stopped being true, and a reader cannot
   tell which ones still matter.
3. A pinned count that moves fails. A classifier that excuses more cases than it
   used to is how a real bug stops being reported, and the count is the only
   thing that notices.

## When these files change

Two cases, and both are meant to produce a diff someone reads:

- **An ICU or CLDR version bump.** Most of the file will move. That is correct
  and expected; the diff is the record of what the new release changed.
- **A deliberate change to what this library answers.** One or two rows move,
  and the note should say why.

Run `./gradlew :conformance-icu:updateLedger`, then read the diff before
committing it. Notes on rows that are still present are carried over
automatically, so regenerating does not lose the explanations.

## First run

There are no `.tsv` files here yet. The first `updateLedger` writes them, and
every divergence it finds arrives unexplained. Working through that output once,
classifying each row and writing its note, is what turns this directory from a
list of failures into a description of two libraries that mostly agree.
