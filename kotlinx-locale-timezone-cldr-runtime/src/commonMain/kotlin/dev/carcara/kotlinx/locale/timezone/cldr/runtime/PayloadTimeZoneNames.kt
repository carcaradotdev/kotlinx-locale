@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.timezone.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.internal.ENTRY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.KEY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.resolvedRecord
import dev.carcara.kotlinx.locale.internal.sparseRecordValue
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf
import dev.carcara.kotlinx.locale.number.NumberFormatSource
import dev.carcara.kotlinx.locale.number.NumberSymbols
import dev.carcara.kotlinx.locale.timezone.TimeZoneNameSource
import dev.carcara.kotlinx.locale.timezone.TimeZoneNameStyle
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset

/** The parent tag plus three keyed fields: cities, per-zone names, per-metazone names. */
private const val NAME_FIELD_COUNT = 4

/**
 * The nine format strings CLDR gives a locale for naming zones.
 *
 * `hourFormat` arrives as one string with a `;` in it and is split at generation
 * time, so the runtime never parses it.
 */
@InternalKotlinxLocaleApi
public class TimeZoneFormats(record: String) {

    private val fields = record.split(FIELD_SEPARATOR)

    /** `+HH:mm`, the pattern for a positive offset. */
    public val hourPositive: String = fields[0]
    public val hourNegative: String = fields[1]

    /** `GMT{0}`, or a locale's own word for it. */
    public val gmtFormat: String = fields[2]

    /** What a zero offset reads as: `GMT`, or `UTC` in some locales. */
    public val gmtZeroFormat: String = fields[3]

    /** What an unknown offset reads as. */
    public val gmtUnknownFormat: String = fields[4]

    /** `{0} Time`, or `časové pásmo {0}`. */
    public val regionFormat: String = fields[5]
    public val regionStandard: String = fields[6]
    public val regionDaylight: String = fields[7]

    /** `{1} ({0})`, for a partial location name. */
    public val fallbackFormat: String = fields[8]
}

/**
 * A [TimeZoneNameSource] over the generated tables.
 *
 * The name tables are optional so that a build can take the localized GMT format
 * on its own, which is nine short strings per locale against several hundred
 * kilobytes for the names. A missing table is not a broken state: every style
 * falls back to the GMT format, which is the ladder UTS #35 prescribes.
 */
public class PayloadTimeZoneNames(
    private val formats: Map<String, String>,
    private val names: Map<String, String> = emptyMap(),
    private val cities: Map<String, String> = emptyMap(),
    private val metadata: TimeZoneMetadata = TimeZoneMetadata.Empty,
    private val numbers: NumberFormatSource? = null,
    /**
     * How a region code becomes a name for the generic location format.
     *
     * A lambda rather than a dependency, so this module needs no country table.
     * The default renders the code, which is the fallback UTS #35 prescribes:
     * `Hora de CU` where a build without country names cannot say Cuba.
     */
    private val regionName: (String, Locale) -> String? = { _, _ -> null },
) : TimeZoneNameSource {

    override val supportedLocales: Set<Locale> by lazy { supportedLocalesOf(formats) }

    override fun displayNameOrNull(zone: TimeZone, style: TimeZoneNameStyle, offset: UtcOffset?, locale: Locale): String? {
        val formatRecord = resolvedRecord(formats, locale) ?: return null
        val zoneFormats = TimeZoneFormats(formatRecord)
        val zoneId = metadata.cldrId(zone.id)

        return when (style) {
            TimeZoneNameStyle.OFFSET_LONG -> localizedGmt(zoneFormats, offset, locale, short = false)
            TimeZoneNameStyle.OFFSET_SHORT -> localizedGmt(zoneFormats, offset, locale, short = true)
            TimeZoneNameStyle.LOCATION -> locationName(zoneId, zoneFormats, offset, locale)
            else -> metazoneName(zoneId, style, locale)
        }
    }

    override fun exemplarCityOrNull(zone: TimeZone, locale: Locale): String? =
        sparseRecordValue(cities, locale, field = 1, fieldCount = 2, key = metadata.cldrId(zone.id))

    /**
     * The generic location format: the region's name where a region has one
     * zone, the city otherwise.
     *
     * Per UTS #35 a zone in a single-zone region, or one CLDR marks as its
     * region's primary, is named for the region rather than the city, which is
     * why Japan reads `Japan Time` and not `Tokyo Time`.
     */
    private fun locationName(zoneId: String, zoneFormats: TimeZoneFormats, offset: UtcOffset?, locale: Locale): String? {
        val region = metadata.regionOf(zoneId)
        // A zone with no location has no location name. UTS #35 sends `Etc/GMT+5`
        // and `UTC` to the offset format rather than inventing a place for them.
        if (region == null) return localizedGmt(zoneFormats, offset, locale, short = false)
        if (metadata.isSingleZoneRegion(region) || metadata.isPrimaryZone(zoneId)) {
            val name = regionName(region, locale) ?: region
            return zoneFormats.regionFormat.replace("{0}", name)
        }
        val city = sparseRecordValue(cities, locale, field = 1, fieldCount = 2, key = zoneId)
            ?: zoneId.substringAfterLast('/').replace('_', ' ')
        return zoneFormats.regionFormat.replace("{0}", city)
    }

    private fun metazoneName(zoneId: String, style: TimeZoneNameStyle, locale: Locale): String? {
        val key = styleKey(style) ?: return null
        // A zone's own override wins over its metazone's, which is what lets
        // one zone in a metazone carry a different name.
        sparseRecordValue(names, locale, field = 2, fieldCount = NAME_FIELD_COUNT, key = "$zoneId#$key")
            ?.let { return it.takeIf(String::isNotEmpty) }
        val metazone = metadata.metazoneOf(zoneId) ?: return null
        val name = sparseRecordValue(names, locale, field = 3, fieldCount = NAME_FIELD_COUNT, key = "$metazone#$key")
        if (name != null) return name.takeIf(String::isNotEmpty)

        // Per UTS #35 a generic name falls back to the standard one when the
        // zone does not observe daylight time, which is why Japan has no
        // separate generic form.
        if (style == TimeZoneNameStyle.GENERIC_LONG || style == TimeZoneNameStyle.GENERIC_SHORT) {
            val standard = if (style == TimeZoneNameStyle.GENERIC_LONG) {
                TimeZoneNameStyle.STANDARD_LONG
            } else {
                TimeZoneNameStyle.STANDARD_SHORT
            }
            return metazoneName(zoneId, standard, locale)
        }
        return null
    }

    private fun styleKey(style: TimeZoneNameStyle): String? = when (style) {
        TimeZoneNameStyle.GENERIC_LONG -> "lg"
        TimeZoneNameStyle.GENERIC_SHORT -> "sg"
        TimeZoneNameStyle.STANDARD_LONG -> "ls"
        TimeZoneNameStyle.STANDARD_SHORT -> "ss"
        TimeZoneNameStyle.DAYLIGHT_LONG -> "ld"
        TimeZoneNameStyle.DAYLIGHT_SHORT -> "sd"
        else -> null
    }

    /**
     * The localized GMT format.
     *
     * Locale data rather than a fixed string: the word, the bracket style, the
     * zero form and the digits all vary, so `GMT-08:00` in English is
     * `UTC−08:00` in several other locales.
     */
    private fun localizedGmt(zoneFormats: TimeZoneFormats, offset: UtcOffset?, locale: Locale, short: Boolean): String {
        if (offset == null) return zoneFormats.gmtUnknownFormat
        val seconds = offset.totalSeconds
        if (seconds == 0) return zoneFormats.gmtZeroFormat

        val symbols = numbers?.symbolsOrNull(locale) ?: NumberSymbols.Root
        val pattern = if (seconds < 0) zoneFormats.hourNegative else zoneFormats.hourPositive
        val magnitude = if (seconds < 0) -seconds else seconds
        val hours = magnitude / 3600
        val minutes = (magnitude % 3600) / 60
        val body = renderHourPattern(pattern, hours, minutes, symbols, short)
        return zoneFormats.gmtFormat.replace("{0}", body)
    }

    /**
     * Renders `+HH:mm` or `+H:mm`, dropping a zero minute field in the short
     * form the way the spec's `-8` example does.
     */
    private fun renderHourPattern(pattern: String, hours: Int, minutes: Int, symbols: NumberSymbols, short: Boolean): String =
        buildString(pattern.length + 4) {
            var index = 0
            while (index < pattern.length) {
                val ch = pattern[index]
                if (ch != 'H' && ch != 'm') {
                    // The minute separator goes with the minutes it introduces.
                    if (short && minutes == 0 && index + 1 < pattern.length && pattern[index + 1] == 'm') {
                        index++
                        continue
                    }
                    append(ch)
                    index++
                    continue
                }
                var run = 0
                while (index < pattern.length && pattern[index] == ch) {
                    run++
                    index++
                }
                // The short form ends at the dropped minutes rather than
                // resuming after them. Hebrew writes its negative hour format
                // as `-HH:mm` followed by a left-to-right mark, and that mark
                // goes with the field it was placed to protect.
                if (ch == 'm' && short && minutes == 0) break
                val value = if (ch == 'H') hours else minutes
                // The long form always writes two hour digits. A locale whose
                // `hourFormat` says `H` rather than `HH`, as Czech's does, is
                // describing its short form; the spec's own example of the long
                // one is `GMT-08:00`.
                val width = when {
                    ch != 'H' -> run
                    short -> 1
                    else -> maxOf(run, 2)
                }
                val digits = value.toString().padStart(width, '0')
                for (digit in digits) append(symbols.digits[digit - '0'])
            }
        }
}

/**
 * The locale-independent zone metadata: which metazone a zone belongs to, which
 * region it is in, and which regions have only one zone.
 *
 * Locale-independent because none of it varies by language, so it rides in the
 * bundle once rather than 1122 times.
 */
@InternalKotlinxLocaleApi
public class TimeZoneMetadata(encoded: String) {

    private val metazones = HashMap<String, String>()
    private val regions = HashMap<String, String>()
    private val singleZoneRegions = HashSet<String>()
    private val primaryZones = HashSet<String>()
    private val cldrIds = HashMap<String, String>()

    init {
        val blocks = encoded.split(FIELD_SEPARATOR)
        for (entry in blocks.getOrNull(0).orEmpty().split(ENTRY_SEPARATOR)) {
            val separator = entry.indexOf(KEY_SEPARATOR)
            if (separator > 0) metazones[entry.substring(0, separator)] = entry.substring(separator + 1)
        }
        for (entry in blocks.getOrNull(1).orEmpty().split(ENTRY_SEPARATOR)) {
            val separator = entry.indexOf(KEY_SEPARATOR)
            if (separator > 0) regions[entry.substring(0, separator)] = entry.substring(separator + 1)
        }
        blocks.getOrNull(2).orEmpty().split(ENTRY_SEPARATOR).filterTo(singleZoneRegions, String::isNotEmpty)
        blocks.getOrNull(3).orEmpty().split(ENTRY_SEPARATOR).filterTo(primaryZones, String::isNotEmpty)
        for (entry in blocks.getOrNull(4).orEmpty().split(ENTRY_SEPARATOR)) {
            val separator = entry.indexOf(KEY_SEPARATOR)
            if (separator > 0) cldrIds[entry.substring(0, separator)] = entry.substring(separator + 1)
        }
    }

    /**
     * [zoneId] under the identifier CLDR's tables use.
     *
     * CLDR keys a zone by the name it had when the entry was written, and a
     * platform reports the name it has now, so `Asia/Kolkata` has to become
     * `Asia/Calcutta` before any table will answer for it. Returns [zoneId]
     * unchanged for the zones that were never renamed, which is most of them.
     */
    public fun cldrId(zoneId: String): String = cldrIds[zoneId] ?: zoneId

    public fun metazoneOf(zoneId: String): String? = metazones[zoneId]

    public fun regionOf(zoneId: String): String? = regions[zoneId]

    public fun isSingleZoneRegion(region: String): Boolean = region in singleZoneRegions

    public fun isPrimaryZone(zoneId: String): Boolean = zoneId in primaryZones

    public companion object {
        public val Empty: TimeZoneMetadata = TimeZoneMetadata("")
    }
}
