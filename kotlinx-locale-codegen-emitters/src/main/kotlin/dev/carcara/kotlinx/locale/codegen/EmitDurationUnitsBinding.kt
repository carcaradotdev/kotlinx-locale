package dev.carcara.kotlinx.locale.codegen

import java.io.File

/** `CldrDurationUnits`-shaped binding: the source object plus the wording extensions. */
public fun emitDurationUnitsBinding(outputRoot: File, spec: BindingSpec, numberObject: String) {
    val file = outputRoot.packageFile(spec.packageName, "DurationUnits.kt")
    file.writeText(
        preamble(
            spec,
            listOf(
                "dev.carcara.kotlinx.locale.Locale",
                "dev.carcara.kotlinx.locale.datetime.cldr.runtime.DurationUnit",
                "dev.carcara.kotlinx.locale.datetime.cldr.runtime.DurationUnitFormatSource",
                "dev.carcara.kotlinx.locale.datetime.cldr.runtime.PayloadDurationUnitFormats",
                "dev.carcara.kotlinx.locale.datetime.cldr.runtime.UnitWidth",
                "dev.carcara.kotlinx.locale.datetime.cldr.runtime.durationFormat",
                "dev.carcara.kotlinx.locale.datetime.cldr.runtime.durationUnitName",
                "dev.carcara.kotlinx.locale.number.Decimal",
                "${spec.registryPackage}.durationUnitsRegistry",
            ),
        ) + """
        |
        |/**
        | * The duration wording this build carries.
        | *
        | * The plural selection and the pattern substitution live in
        | * `kotlinx-locale-datetime-cldr-runtime`; all this object contributes is the
        | * table and the two sources it reads through.
        | */
        |public object ${spec.objectName} : DurationUnitFormatSource by PayloadDurationUnitFormats(
        |    durationUnitsRegistry,
        |    ${numberObject}Plurals,
        |    $numberObject,
        |)
        |
        |/**
        | * [value] many [unit]s written for [locale].
        | *
        | * ```
        | * durationFormat(2, DurationUnit.HOUR)                            // "2 hours"
        | * durationFormat(2, DurationUnit.HOUR, UnitWidth.SHORT)           // "2 hr"
        | * durationFormat(2, DurationUnit.HOUR, UnitWidth.NARROW)          // "2h"
        | * durationFormat(2, DurationUnit.HOUR, locale = de)               // "2 Stunden"
        | * ```
        | *
        | * This is the measurement form. [durationPattern][dev.carcara.kotlinx.locale.datetime.durationPattern]
        | * is the other one, and gives `h:mm` for a clock reading.
        | *
        | * The unit is yours to choose: nothing here turns ninety minutes into an
        | * hour and a half, for the same reason relative time does not.
        | */
        |public fun durationFormat(
        |    value: Long,
        |    unit: DurationUnit,
        |    width: UnitWidth = UnitWidth.LONG,
        |    locale: Locale = Locale.current,
        |): String = ${spec.objectName}.durationFormat(value, unit, width, locale)
        |
        |/**
        | * [value] many [unit]s at exactly [fractionDigits] digits.
        | *
        | * The digit count is required rather than read off the float, because the
        | * targets do not agree on how many digits a `Double` has and the plural
        | * category depends on how many are printed.
        | */
        |public fun durationFormat(
        |    value: Double,
        |    fractionDigits: Int,
        |    unit: DurationUnit,
        |    width: UnitWidth = UnitWidth.LONG,
        |    locale: Locale = Locale.current,
        |): String = ${spec.objectName}.durationFormat(value, fractionDigits, unit, width, locale)
        |
        |/** [value] many [unit]s, keeping the digits [value] carries. */
        |public fun durationFormat(
        |    value: Decimal,
        |    unit: DurationUnit,
        |    width: UnitWidth = UnitWidth.LONG,
        |    locale: Locale = Locale.current,
        |): String = ${spec.objectName}.durationFormat(value, unit, width, locale)
        |
        |/** The locale's own name for [unit]: `hours`, `Stunden`. */
        |public fun durationUnitName(
        |    unit: DurationUnit,
        |    width: UnitWidth = UnitWidth.LONG,
        |    locale: Locale = Locale.current,
        |): String = ${spec.objectName}.durationUnitName(unit, width, locale)
        |
        """.trimMargin(),
    )
    println("[codegen] emitted ${spec.objectName} to $file")
}
