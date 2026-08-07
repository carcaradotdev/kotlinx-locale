/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.carcara.kotlinx.locale.codegen

import java.io.File

/**
 * `CldrTimeZone`-shaped binding: the source object plus the naming extensions.
 *
 * [metadata] is inlined as a constant rather than emitted as a table because it
 * is locale-independent, so there is nothing per-locale to key it by and nothing
 * for narrowing to drop.
 */
public fun emitTimeZoneBinding(outputRoot: File, spec: BindingSpec, metadata: String, numberObject: String?, hasNames: Boolean) {
    val file = outputRoot.packageFile(spec.packageName, "TimeZoneNames.kt")
    val names = if (hasNames) "timeZoneNamesRegistry" else "emptyMap()"
    val numbers = numberObject ?: "null"
    val imports = buildList {
        addAll(
            listOf(
                "dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi",
                "dev.carcara.kotlinx.locale.Locale",
                "dev.carcara.kotlinx.locale.timezone.TimeZoneNameSource",
                "dev.carcara.kotlinx.locale.timezone.TimeZoneNameStyle",
                "dev.carcara.kotlinx.locale.timezone.cldr.runtime.PayloadTimeZoneNames",
                "dev.carcara.kotlinx.locale.timezone.cldr.runtime.TimeZoneMetadata",
                "dev.carcara.kotlinx.locale.timezone.displayName",
                "${spec.registryPackage}.timeZoneFormatsRegistry",
                "kotlinx.datetime.TimeZone",
                "kotlinx.datetime.UtcOffset",
            ),
        )
        if (hasNames) add("${spec.registryPackage}.timeZoneNamesRegistry")
    }

    file.writeText(
        preamble(spec, imports, "@file:OptIn(InternalKotlinxLocaleApi::class)") + """
        |
        |/**
        | * Which metazone each zone uses, which region it is in, and which regions
        | * have only one zone.
        | *
        | * A constant rather than a table: none of it varies by language, so there is
        | * nothing to key it by and nothing for narrowing to drop.
        | */
        |@InternalKotlinxLocaleApi
        |internal val timeZoneMetadata: TimeZoneMetadata = TimeZoneMetadata(
        |    "${kotlinEscape(metadata)}",
        |)
        |
        |/**
        | * The time zone names this build carries.
        | *
        | * The composition lives in `kotlinx-locale-timezone-cldr-runtime`; all this
        | * object contributes is the tables.
        | */
        |public object ${spec.objectName} : TimeZoneNameSource by PayloadTimeZoneNames(
        |    timeZoneFormatsRegistry,
        |    $names,
        |    emptyMap(),
        |    timeZoneMetadata,
        |    $numbers,
        |) {
        |
        |    /**
        |     * The tables themselves, for the exemplar cities layer.
        |     *
        |     * `kotlinx-locale-timezone-cldr-cities` composes a source with the same
        |     * formats and names plus its own city table, rather than carrying a
        |     * second copy of the first two.
        |     */
        |    @InternalKotlinxLocaleApi
        |    public val formatRecords: Map<String, String> get() = timeZoneFormatsRegistry
        |
        |    @InternalKotlinxLocaleApi
        |    public val nameRecords: Map<String, String> get() = $names
        |
        |    @InternalKotlinxLocaleApi
        |    public val metadata: TimeZoneMetadata get() = timeZoneMetadata
        |}
        |
        |/**
        | * This zone's name in [style] for [locale].
        | *
        | * [offset] is what the offset styles print and what tells the standard and
        | * daylight forms apart. A caller that already knows which form it wants
        | * should pass the style and leave the offset out.
        | *
        | * Falls back through the ladder UTS #35 prescribes: a missing name degrades
        | * to the localized GMT format, and that to the tzdb identifier.
        | */
        |public fun TimeZone.displayName(
        |    style: TimeZoneNameStyle = TimeZoneNameStyle.GENERIC_LONG,
        |    offset: UtcOffset? = null,
        |    locale: Locale = Locale.current,
        |): String = ${spec.objectName}.displayName(this, style, offset, locale)
        |
        |/**
        | * This offset written the way [locale] writes it: `GMT-08:00`.
        | *
        | * Locale data rather than a fixed string. The word, the bracket style, the
        | * zero form and the digits all vary, so several locales write `UTC−08:00`
        | * and Arabic writes its own digits.
        | */
        |public fun UtcOffset.displayName(
        |    locale: Locale = Locale.current,
        |    short: Boolean = false,
        |): String = ${spec.objectName}.displayName(
        |    TimeZone.UTC,
        |    if (short) TimeZoneNameStyle.OFFSET_SHORT else TimeZoneNameStyle.OFFSET_LONG,
        |    this,
        |    locale,
        |)
        |
        """.trimMargin(),
    )
    println("[codegen] emitted ${spec.objectName} to $file")
}

/**
 * The exemplar cities layer: the same source with the city table wired in.
 *
 * A second object rather than a second table on the first, because the cities
 * are the largest zone table and ship in their own artifact. A consumer who
 * takes only the names gets the generic location format's own fallback, which is
 * the one the specification prescribes.
 */
public fun emitTimeZoneCitiesBinding(outputRoot: File, spec: BindingSpec, timeZoneObject: String) {
    val file = outputRoot.packageFile(spec.packageName, "TimeZoneCities.kt")
    file.writeText(
        preamble(
            spec,
            listOf(
                "dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi",
                "dev.carcara.kotlinx.locale.Locale",
                "dev.carcara.kotlinx.locale.timezone.TimeZoneNameSource",
                "dev.carcara.kotlinx.locale.timezone.TimeZoneNameStyle",
                "dev.carcara.kotlinx.locale.timezone.cldr.runtime.PayloadTimeZoneNames",
                "dev.carcara.kotlinx.locale.timezone.displayName",
                "dev.carcara.kotlinx.locale.timezone.exemplarCity",
                "${spec.registryPackage}.timeZoneCitiesRegistry",
                "kotlinx.datetime.TimeZone",
                "kotlinx.datetime.UtcOffset",
            ),
            "@file:OptIn(InternalKotlinxLocaleApi::class)",
        ) + """
        |
        |/**
        | * The zone names of `$timeZoneObject` with the exemplar cities added.
        | *
        | * The cities are what turn the generic location format from `Los Angeles
        | * Time`, derived from the identifier, into the locale's own spelling of the
        | * city. Composed from that object's tables rather than carrying a second
        | * copy of them.
        | */
        |public object ${spec.objectName} : TimeZoneNameSource by PayloadTimeZoneNames(
        |    $timeZoneObject.formatRecords,
        |    $timeZoneObject.nameRecords,
        |    timeZoneCitiesRegistry,
        |    $timeZoneObject.metadata,
        |)
        |
        |/** The localized city that stands for this zone; falls back to the identifier's last part. */
        |public fun TimeZone.exemplarCity(locale: Locale = Locale.current): String =
        |    ${spec.objectName}.exemplarCity(this, locale)
        |
        |/** This zone's name in [style], with the exemplar cities available to the location format. */
        |public fun TimeZone.localizedName(
        |    style: TimeZoneNameStyle = TimeZoneNameStyle.GENERIC_LONG,
        |    offset: UtcOffset? = null,
        |    locale: Locale = Locale.current,
        |): String = ${spec.objectName}.displayName(this, style, offset, locale)
        |
        """.trimMargin(),
    )
    println("[codegen] emitted ${spec.objectName} to $file")
}
