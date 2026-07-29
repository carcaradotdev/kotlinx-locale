package dev.carcara.kotlinx.locale.conformance

/**
 * How strictly a source is held to the ICU fixtures.
 *
 * The distinction is not strictness for its own sake. A CLDR-backed source is a
 * second encoding of the data ICU encodes, so any disagreement is a bug in one
 * of them and the suite should say so. A platform-backed source reads whatever
 * the host shipped, which moves with OS versions and cannot be pinned to a
 * fixture without the test becoming a report on the CI image.
 */
public enum class ConformanceTier {

    /**
     * Every name and symbol must equal ICU's, byte for byte once the no-break
     * space variants that point releases disagree on are normalized.
     *
     * For the sources compiled from CLDR, and for anything the Gradle plugin
     * generates from the same tables.
     */
    EXACT,

    /**
     * Answers must be well-shaped — non-blank, the right number of them, round
     * tripping where the API promises it — but need not match ICU.
     *
     * For platform sources, where the data belongs to the host.
     */
    BEHAVIOURAL,
}

/**
 * Normalizes the no-break space variants that ICU and CLDR point releases
 * disagree on: U+00A0 NO-BREAK SPACE and U+202F NARROW NO-BREAK SPACE.
 */
internal fun String.normalizedSpaces(): String = replace('\u00A0', ' ').replace('\u202F', ' ')
