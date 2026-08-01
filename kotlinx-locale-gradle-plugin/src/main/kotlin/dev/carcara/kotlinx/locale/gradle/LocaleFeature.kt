package dev.carcara.kotlinx.locale.gradle

import dev.carcara.kotlinx.locale.codegen.GeneratedBinding
import dev.carcara.kotlinx.locale.codegen.GeneratedTable

/**
 * One thing a build can ask to be generated, and everything generating it takes.
 *
 * A feature declares the closure of tables it needs rather than pointing at
 * other features it depends on. The distinction matters. With dependency edges,
 * `number.compact` and `number.plurals` are two switches and one of the four
 * combinations produces a source set that compiles and then picks the wrong
 * plural form, because selecting a compact pattern is a plural selection over
 * the divided value. With closures that combination cannot be written down:
 * asking for compact emits the plural table because compact's table set contains
 * it.
 *
 * So a flag never changes what an API call means. It decides which locales and
 * which tables reach the generated source set. Asking for something not enabled
 * is a compile error, which is the right failure; what must never happen is a
 * configuration that compiles and answers wrongly.
 *
 * [dslName] is carried here so a failure can quote what a user would type rather
 * than the enum constant.
 */
enum class LocaleFeature(val dslName: String, val tables: Set<GeneratedTable>, val bindings: Set<GeneratedBinding>) {

    COUNTRY_NAMES(
        dslName = "country.names",
        tables = setOf(GeneratedTable.COUNTRY_NAMES),
        bindings = setOf(GeneratedBinding.COUNTRY),
    ),

    CURRENCY_NAMES(
        dslName = "currency.names",
        tables = setOf(GeneratedTable.CURRENCY_NAMES),
        bindings = setOf(GeneratedBinding.CURRENCY),
    ),

    /** Includes the name table: a pattern substitutes the symbol into itself. */
    CURRENCY_FORMATS(
        dslName = "currency.formats",
        tables = setOf(GeneratedTable.CURRENCY_NAMES, GeneratedTable.CURRENCY_FORMATS),
        bindings = setOf(GeneratedBinding.CURRENCY),
    ),

    DATETIME_PATTERNS(
        dslName = "datetime.patterns",
        tables = setOf(GeneratedTable.DATE_TIME),
        bindings = setOf(GeneratedBinding.DATE_TIME),
    ),

    /**
     * Includes the pattern table: matching a skeleton scores against the
     * locale's standard date and time patterns, and rendering the winner needs
     * its month and weekday names.
     */
    DATETIME_SKELETONS(
        dslName = "datetime.skeletons",
        tables = setOf(GeneratedTable.DATE_TIME, GeneratedTable.SKELETONS),
        bindings = setOf(GeneratedBinding.DATE_TIME, GeneratedBinding.SKELETONS),
    ),

    /**
     * Stand-alone month, weekday and quarter names.
     *
     * Includes the pattern table it reads the format names from, since a
     * stand-alone table stores only the differences.
     */
    DATETIME_STANDALONE(
        dslName = "datetime.standalone",
        tables = setOf(GeneratedTable.DATE_TIME, GeneratedTable.DATE_TIME_STANDALONE),
        bindings = setOf(GeneratedBinding.DATE_TIME),
    ),

    /**
     * Relative wording: `3 days ago`, `yesterday`.
     *
     * Includes the number and plural tables, because the wording picks a plural
     * form and renders its count in the locale's own digits.
     */
    DATETIME_RELATIVE_TIME(
        dslName = "datetime.relativeTime",
        tables = setOf(GeneratedTable.RELATIVE_TIME, GeneratedTable.NUMBER, GeneratedTable.PLURALS),
        bindings = setOf(GeneratedBinding.RELATIVE_TIME, GeneratedBinding.NUMBER),
    ),

    /**
     * Language, script and region names, and `Locale.displayName`.
     *
     * One feature rather than three, because the display name algorithm reaches
     * all of them: naming `sr-Cyrl-BA` needs the language, the script and the
     * region, and a build that had only the first would compose a name with two
     * raw subtags in it.
     */
    LANGUAGE_NAMES(
        dslName = "language.names",
        tables = setOf(GeneratedTable.LANGUAGE_NAMES),
        bindings = setOf(GeneratedBinding.LANGUAGE),
    ),

    /**
     * Number symbols and the plain decimal and percent patterns.
     *
     * Includes the plural table, because the source object that carries the
     * formats carries the plural rules too and a compact call through it would
     * otherwise pick the wrong pattern.
     */
    NUMBER_FORMATS(
        dslName = "number.formats",
        tables = setOf(GeneratedTable.NUMBER, GeneratedTable.PLURALS),
        bindings = setOf(GeneratedBinding.NUMBER),
    ),

    /**
     * Compact notation: `1.2K`, `1.2 thousand`.
     *
     * Includes the format and plural tables. Compact patterns are keyed by
     * plural category, so selecting one is a plural selection over the divided
     * value, and there is no reading under which compact without plurals is a
     * choice someone meant to make.
     */
    NUMBER_COMPACT(
        dslName = "number.compact",
        tables = setOf(GeneratedTable.NUMBER, GeneratedTable.NUMBER_COMPACT, GeneratedTable.PLURALS),
        bindings = setOf(GeneratedBinding.NUMBER),
    ),

    /**
     * CLDR plural rules on their own, for a caller choosing between translated
     * strings rather than formatting a number.
     */
    NUMBER_PLURALS(
        dslName = "number.plurals",
        tables = setOf(GeneratedTable.NUMBER, GeneratedTable.PLURALS),
        bindings = setOf(GeneratedBinding.NUMBER),
    ),

    /**
     * Ordinal forms: `1st`, `1.`, `1º`.
     *
     * Includes the plural table, because eight of CLDR's ordinal rule closures
     * select their suffix by ordinal plural category. English is the obvious
     * one: its rule is literally `$(ordinal,one{st}two{nd}few{rd}other{th})$`.
     */
    NUMBER_ORDINALS(
        dslName = "number.ordinals",
        tables = setOf(GeneratedTable.NUMBER, GeneratedTable.ORDINALS, GeneratedTable.PLURALS),
        bindings = setOf(GeneratedBinding.NUMBER),
    ),

    /**
     * Compact money: `$1.2M`.
     *
     * Includes the currency name and pattern tables it formats through, and the
     * plural table its own patterns are keyed by.
     */
    CURRENCY_COMPACT(
        dslName = "currency.compact",
        tables = setOf(
            GeneratedTable.CURRENCY_NAMES,
            GeneratedTable.CURRENCY_FORMATS,
            GeneratedTable.CURRENCY_COMPACT,
            GeneratedTable.NUMBER,
            GeneratedTable.PLURALS,
        ),
        bindings = setOf(GeneratedBinding.CURRENCY, GeneratedBinding.NUMBER),
    ),
    ;

    companion object {

        /** Every table some feature can ask for, which should be every table there is. */
        internal val REACHABLE_TABLES: Set<GeneratedTable> = entries.flatMap(LocaleFeature::tables).toSet()
    }
}
