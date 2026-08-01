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
    ;

    companion object {

        /** Every table some feature can ask for, which should be every table there is. */
        internal val REACHABLE_TABLES: Set<GeneratedTable> = entries.flatMap(LocaleFeature::tables).toSet()
    }
}
