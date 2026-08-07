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

/** `CldrPersonName`-shaped binding: the source object plus the entry points. */
public fun emitPersonNameBinding(outputRoot: File, spec: BindingSpec, graphemeBreak: String, wordBreakMid: String) {
    val file = outputRoot.packageFile(spec.packageName, "PersonNameFormat.kt")
    file.writeText(
        preamble(
            spec,
            listOf(
                "dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi",
                "dev.carcara.kotlinx.locale.Locale",
                "dev.carcara.kotlinx.locale.personname.PersonName",
                "dev.carcara.kotlinx.locale.personname.PersonNameFormality",
                "dev.carcara.kotlinx.locale.personname.PersonNameLength",
                "dev.carcara.kotlinx.locale.personname.PersonNameOrder",
                "dev.carcara.kotlinx.locale.personname.PersonNameSource",
                "dev.carcara.kotlinx.locale.personname.PersonNameUsage",
                "dev.carcara.kotlinx.locale.internal.GraphemeClusters",
                "dev.carcara.kotlinx.locale.internal.WordBreaks",
                "dev.carcara.kotlinx.locale.personname.cldr.runtime.PayloadPersonNames",
                "dev.carcara.kotlinx.locale.personname.format",
                "dev.carcara.kotlinx.locale.personname.order",
                "${spec.registryPackage}.personNamesRegistry",
            ),
            fileAnnotation = "@file:OptIn(InternalKotlinxLocaleApi::class)",
        ) + """
        |
        |/**
        | * The person name patterns this build carries.
        | *
        | * The formatting lives in `kotlinx-locale-personname-cldr-runtime`; all this
        | * object contributes is the table.
        | */
        |/**
        | * Where one written character ends, per UAX #29.
        | *
        | * Installed here because an initial and a monogram are the first cluster of
        | * a field, and in several scripts that is more than one code point. A
        | * constant rather than a table keyed by anything: it is a property of the
        | * characters, not of a locale.
        | */
        |@InternalKotlinxLocaleApi
        |internal val graphemeBreakTable: String = "${kotlinEscape(graphemeBreak)}"
        |
        |/**
        | * The punctuation that stays inside a word, per UAX #29 rules WB6 and WB7.
        | *
        | * Installed beside the cluster table and for the same reason: an initial is
        | * taken from the first cluster of each word, so where the words end is half
        | * the question. Catalan's `Gal·la` is one word and one initial.
        | */
        |@InternalKotlinxLocaleApi
        |internal val wordBreakMidTable: String = "${kotlinEscape(wordBreakMid)}"
        |
        |public object ${spec.objectName} : PersonNameSource by PayloadPersonNames(personNamesRegistry) {
        |    init {
        |        GraphemeClusters.install(graphemeBreakTable)
        |        WordBreaks.install(wordBreakMidTable)
        |    }
        |}
        |
        |/**
        | * [name] written the way [locale] writes a name from [PersonName.locale].
        | *
        | * ```
        | * val name = PersonName(given = "Iris", surname = "Adler")
        | * personNameFormat(name)                                  // "Iris Adler"
        | * personNameFormat(name, usage = PersonNameUsage.MONOGRAM) // "IA"
        | * ```
        | *
        | * The order is not a property of the name or of the reader but of the pair,
        | * so a Hungarian name is written surname first in Hungarian and given first
        | * in English. Pass [PersonName.locale] to get that right; pass [order] to
        | * override it.
        | *
        | * Initials are [PersonNameUsage.MONOGRAM] rather than a separate call,
        | * because CLDR models them as one of three usages beside referring and
        | * addressing, and [length] then decides how many letters.
        | *
        | * Falls back to the given name and surname joined by a space.
        | */
        |public fun personNameFormat(
        |    name: PersonName,
        |    length: PersonNameLength = PersonNameLength.DEFAULT,
        |    usage: PersonNameUsage = PersonNameUsage.REFERRING,
        |    formality: PersonNameFormality = PersonNameFormality.DEFAULT,
        |    order: PersonNameOrder = PersonNameOrder.DEFAULT,
        |    locale: Locale = Locale.current,
        |): String = ${spec.objectName}.format(name, length, usage, formality, order, locale)
        |
        |/**
        | * Which of given-first or surname-first [locale] uses for a name from
        | * [nameLocale].
        | *
        | * A different question from [personNameFormat] rather than the same one in
        | * another form: this answers what the convention is, without needing a name
        | * to apply it to. Given first when [locale] is not in this build.
        | */
        |public fun personNameOrder(
        |    nameLocale: Locale?,
        |    locale: Locale = Locale.current,
        |): PersonNameOrder = ${spec.objectName}.order(nameLocale, locale)
        |
        """.trimMargin(),
    )
    println("[codegen] emitted ${spec.objectName} to $file")
}
