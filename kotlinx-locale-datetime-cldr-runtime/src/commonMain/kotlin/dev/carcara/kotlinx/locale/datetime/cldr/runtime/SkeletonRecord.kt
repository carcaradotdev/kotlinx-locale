@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.datetime.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.internal.ENTRY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.KEY_SEPARATOR

/**
 * The skeleton tables for one locale.
 *
 * Three payloads rather than one because they dedupe on very different scales.
 * Nearly every locale writes its own `availableFormats`, so those are the bulk
 * of the table; three distinct sets of append formats cover all 1122 locales,
 * because almost none of them overrides root's.
 *
 * Public under the internal-API marker so the conformance suite can compare
 * resolved tables and matched patterns directly, the way it already does for
 * [DateTimeRecord].
 */
@InternalKotlinxLocaleApi
public class SkeletonRecord(formats: String, appendFormats: String, names: String) {

    /** CLDR skeleton id to pattern, e.g. `yMMMd` to `d 'de' MMM 'de' y`. */
    public val availableFormats: List<Pair<String, String>> = formats
        .split(ENTRY_SEPARATOR)
        .mapNotNull { entry ->
            val separator = entry.indexOf(KEY_SEPARATOR)
            if (separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
        }

    private val appendFormatByField: List<String> = appendFormats.split(ENTRY_SEPARATOR)

    private val nameFields: List<String> = names.split(FIELD_SEPARATOR)

    private val fieldNameByField: List<String> = nameFields[0].split(ENTRY_SEPARATOR)

    /** Quarter names, index 0 being the first quarter. */
    public val quartersWide: List<String> = nameFields[1].split(ENTRY_SEPARATOR)
    public val quartersAbbr: List<String> = nameFields[2].split(ENTRY_SEPARATOR)

    /**
     * The stand-alone quarter names, falling back to the format ones.
     *
     * Read positionally from the end of the record, so a record written before
     * these existed decodes with the fallback rather than failing.
     */
    public val quartersStandaloneWide: List<String> = standaloneOr(5, quartersWide)
    public val quartersStandaloneAbbr: List<String> = standaloneOr(6, quartersAbbr)

    private fun standaloneOr(field: Int, format: List<String>): List<String> =
        nameFields.getOrNull(field)?.takeIf(String::isNotEmpty)?.split(ENTRY_SEPARATOR) ?: format

    /** The wide quarter at [index], in the stand-alone form when [standalone]. */
    public fun quarterWide(index: Int, standalone: Boolean): String = if (standalone) quartersStandaloneWide[index] else quartersWide[index]

    /** The abbreviated quarter at [index], in the stand-alone form when [standalone]. */
    public fun quarterAbbr(index: Int, standalone: Boolean): String = if (standalone) quartersStandaloneAbbr[index] else quartersAbbr[index]

    private val hourCycle: List<String> = nameFields[3].split(ENTRY_SEPARATOR)

    /** What the `j` skeleton letter resolves to here: one of `h H k K`. */
    public val preferredHourChar: Char = hourCycle[0].firstOrNull() ?: 'H'

    /** What `C` resolves to; a trailing `b` or `B` names the day period letter. */
    public val firstAllowedHourFormat: String = hourCycle.getOrNull(1)?.ifEmpty { null } ?: preferredHourChar.toString()

    /**
     * The `atTime` date-time glue, in FULL, LONG, MEDIUM, SHORT order.
     *
     * A skeleton spanning a date and a time is joined with this rather than with
     * the standard glue [DateTimeRecord] carries, which is why `en` reads
     * "July 27, 2026 at 3:05 PM" here and "July 27, 2026, 3:05 PM" from the
     * style-based API.
     */
    public val glueAtTimeFormats: List<String> = nameFields[4].split(ENTRY_SEPARATOR)

    /** How this locale writes "and also this field", or "" where it declares none. */
    internal fun appendFormat(field: Int): String = appendFormatByField.getOrElse(field) { "" }

    /** This locale's name for a field, which is what an append format's `{2}` writes. */
    internal fun fieldName(field: Int): String = fieldNameByField.getOrElse(field) { "" }
}
