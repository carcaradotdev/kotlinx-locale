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
 * `CldrDateTimeIntervals`-shaped binding: the source object plus the interval
 * entry points.
 *
 * Composed over the skeleton object rather than over its tables. An interval is
 * a split of the pattern the skeleton matcher picks, so the two share one
 * matcher pool per locale instead of building two.
 */
public fun emitIntervalBinding(outputRoot: File, spec: BindingSpec, skeletonObject: String) {
    val file = outputRoot.packageFile(spec.packageName, "IntervalFormat.kt")
    val skeletonPackage = skeletonObject.substringBeforeLast('.')
    val skeletonName = skeletonObject.substringAfterLast('.')
    val imports = listOf(
        "dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi",
        "dev.carcara.kotlinx.locale.Locale",
        "dev.carcara.kotlinx.locale.datetime.cldr.runtime.IntervalFormatSource",
        "dev.carcara.kotlinx.locale.datetime.cldr.runtime.PayloadIntervalFormats",
        "kotlinx.datetime.LocalDate",
        "kotlinx.datetime.LocalDateTime",
        "kotlinx.datetime.LocalTime",
        "${spec.registryPackage}.intervalFormatsRegistry",
    ) + listOfNotNull(skeletonObject.takeIf { skeletonPackage != spec.packageName })

    file.writeText(
        preamble(spec, imports, fileAnnotation = "@file:OptIn(InternalKotlinxLocaleApi::class)") + """
        |
        |internal val intervalSource: PayloadIntervalFormats = PayloadIntervalFormats(
        |    intervalFormatsRegistry,
        |    $skeletonName.skeletons,
        |)
        |
        |/**
        | * The interval patterns this build carries.
        | *
        | * The splitting lives in `kotlinx-locale-datetime-cldr-runtime`; all this
        | * object contributes is the table, plus the matcher it borrows from
        | * [$skeletonName] rather than building a second one.
        | */
        |public object ${spec.objectName} : IntervalFormatSource by intervalSource
        |
        |/**
        | * The two dates as one interval, with the parts they share written once.
        | *
        | * ```
        | * intervalFormat(LocalDate(2026, 7, 18), LocalDate(2026, 7, 22), "yMMMd")
        | * // "Jul 18 – 22, 2026"
        | * intervalFormat(LocalDate(2026, 5, 18), LocalDate(2026, 7, 22), "yMMMd")
        | * // "May 18 – Jul 22, 2026"
        | * ```
        | *
        | * [skeleton] names the fields the way [LocalDate.format] does. When the two
        | * dates are equal in every field the skeleton names, the answer is that
        | * date formatted once rather than twice with a separator between.
        | *
        | * The two values are formatted in the order given. A later start is not an
        | * error and is not swapped, because several locales write the fallback with
        | * its arguments reversed and ordering is the data's business.
        | *
        | * The ladder is: the locale's pattern for this skeleton and this
        | * greatest-difference field; then the locale's own `intervalFormatFallback`
        | * over two whole formats, which is what CLDR prescribes when it declares no
        | * entry for the combination; then, only when this build carries no data for
        | * [locale] at all or the skeleton names a field it cannot render, the
        | * ISO 8601 interval form `2026-07-18/2026-07-22`.
        | *
        | * That last rung uses a solidus because ISO 8601-1:2019 clause 3.2.6 says an
        | * interval is `<start>/<end>`. It deliberately does not borrow the en dash
        | * from English: by the time it is reached there is no locale data, and
        | * guessing one locale's punctuation for an unknown one is worse than
        | * answering in the interchange format.
        | */
        |public fun intervalFormat(
        |    start: LocalDate,
        |    end: LocalDate,
        |    skeleton: String,
        |    locale: Locale = Locale.current,
        |): String = ${spec.objectName}.intervalFormatOrNull(start, end, skeleton, locale)
        |    ?: (start.toString() + "/" + end.toString())
        |
        |/** The two times as one interval; see the [LocalDate][intervalFormat] overload for the fallback ladder. */
        |public fun intervalFormat(
        |    start: LocalTime,
        |    end: LocalTime,
        |    skeleton: String,
        |    locale: Locale = Locale.current,
        |): String = ${spec.objectName}.intervalFormatOrNull(start, end, skeleton, locale)
        |    ?: (start.toString() + "/" + end.toString())
        |
        |/** The two date-times as one interval; see the [LocalDate][intervalFormat] overload for the fallback ladder. */
        |public fun intervalFormat(
        |    start: LocalDateTime,
        |    end: LocalDateTime,
        |    skeleton: String,
        |    locale: Locale = Locale.current,
        |): String = ${spec.objectName}.intervalFormatOrNull(start, end, skeleton, locale)
        |    ?: (start.toString() + "/" + end.toString())
        |
        """.trimMargin(),
    )
    println("[codegen] emitted ${spec.objectName} to $file")
}
