package dev.carcara.kotlinx.locale.codegen

/**
 * One per-locale payload section of the bundle: what it is called, how its
 * records reach data they do not declare, and whether narrowing may drop rows.
 *
 * [sparseFields] is the load-bearing one. Zero means a record is fully resolved
 * and can be copied anywhere. Anything else means the record holds only what its
 * own locale declared and defers the rest to the tag in field 0, so narrowing
 * has to keep the ancestors and a fallback has to be flattened rather than
 * copied. That number used to be a literal repeated at each call site in
 * [LocaleDataBundle.narrowTo]; declaring it next to the name is the only place
 * it can be checked once.
 */
public class BundleSection(
    public val name: String,
    public val sparseFields: Int = 0,
    /**
     * False for a table that is the same in every locale and has to survive
     * narrowing whole. Dropping rows from one of those does not save anything
     * worth having and turns a missing locale into wrong output rather than an
     * error.
     */
    public val narrowed: Boolean = true,
) {

    public val isSparse: Boolean get() = sparseFields > 0

    override fun toString(): String = name

    public companion object {

        public val ALL: List<BundleSection> = listOf(
            BundleSection("dateTime"),
            BundleSection("countryNames", sparseFields = 1),
            BundleSection("currencyFormats"),
            BundleSection("currencyNames", sparseFields = 2),
            BundleSection("currencyPluralNames", sparseFields = 3),
            BundleSection("skeletonFormats"),
            BundleSection("skeletonAppendFormats"),
            BundleSection("skeletonNames"),
            BundleSection("intervalFormats"),
            BundleSection("dateTimeStandalone"),
            BundleSection("localeDisplayNames", sparseFields = 4),
            BundleSection("relativeTime"),
            BundleSection("durationUnits"),
            BundleSection("personNames"),
            BundleSection("timeZoneFormats"),
            BundleSection("timeZoneNames", sparseFields = 3),
            BundleSection("timeZoneCities", sparseFields = 1),
            BundleSection("numberSymbols"),
            BundleSection("numberPatterns"),
            BundleSection("numberCompactShort"),
            BundleSection("numberCompactLong"),
            BundleSection("currencyCompactShort"),
            // Not narrowed. The plural tables are four kilobytes for every locale
            // in CLDR, so dropping rows saves nothing, and a build that asked for
            // a locale it did not generate would fall back to root's other-only
            // rules and produce grammatically wrong text with no error anywhere.
            BundleSection("pluralRuleSets", narrowed = false),
            BundleSection("pluralRuleIndex", narrowed = false),
            // The same reasoning: thirty-three rule closures cover every locale.
            BundleSection("ordinalRuleSets", narrowed = false),
            BundleSection("ordinalRuleIndex", narrowed = false),
        )

        public val BY_NAME: Map<String, BundleSection> = ALL.associateBy(BundleSection::name)
    }
}

/**
 * The names of the locale-independent tables: data that is the same for every
 * locale and so rides in the bundle once rather than once per tag.
 *
 * Nothing uses this yet. It exists because the alternative for such a table is
 * to key it by locale and repeat it 1121 times, and having the category named
 * makes that the obviously wrong choice rather than the path of least
 * resistance.
 */
public object BundleTables {

    /** Which metazone a zone uses, which region it is in, and the single-zone regions. */
    public const val TIME_ZONE_METADATA: String = "timeZoneMetadata"

    /**
     * The numbering plan of every territory libphonenumber describes.
     *
     * A table rather than a section because none of it varies by language. A
     * number is valid or it is not, and it formats the way its own territory
     * formats it, whoever is reading. Narrowing a build to three locales must
     * not narrow its phone metadata to three countries, which is exactly what a
     * section would do.
     */
    public const val PHONE_TERRITORIES: String = "phoneTerritories"

    /** The number formats, split out because only formatting reads them. */
    public const val PHONE_FORMATS: String = "phoneFormats"

    /**
     * Where each territory starts its week, and which days it rests.
     *
     * A table rather than a section for the same reason as the phone metadata:
     * none of it varies by language. Portugal starts the week on Monday whether
     * the screen is in Portuguese or English, so narrowing a build to three
     * locales must not narrow its week data to three territories.
     *
     * It carries a second field of languages that have no region of their own,
     * because a `Locale` need not name one and only likely subtags can take `en`
     * to the United States.
     */
    public const val WEEK_DATA: String = "weekData"

    /**
     * The UAX #29 grapheme cluster properties.
     *
     * A table rather than a section, and the least locale-dependent thing here:
     * where one written character ends is a property of the characters, not of
     * anybody's language. Narrowing a build to three locales must not narrow the
     * scripts it can count letters in.
     */
    public const val GRAPHEME_BREAK: String = "graphemeBreak"

    public val ALL: Set<String> = setOf(TIME_ZONE_METADATA, PHONE_TERRITORIES, PHONE_FORMATS, WEEK_DATA, GRAPHEME_BREAK)
}
