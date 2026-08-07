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

/** `CldrLanguage`-shaped binding: the source object plus the Locale extensions. */
public fun emitLanguageBinding(outputRoot: File, spec: BindingSpec) {
    val file = outputRoot.packageFile(spec.packageName, "LanguageNames.kt")
    file.writeText(
        preamble(
            spec,
            listOf(
                "dev.carcara.kotlinx.locale.Locale",
                "dev.carcara.kotlinx.locale.language.LanguageDisplay",
                "dev.carcara.kotlinx.locale.language.LanguageNameSource",
                "dev.carcara.kotlinx.locale.language.LanguageNameStyle",
                "dev.carcara.kotlinx.locale.language.cldr.runtime.PayloadLanguageNames",
                "dev.carcara.kotlinx.locale.language.displayName",
                "dev.carcara.kotlinx.locale.language.languageName",
                "dev.carcara.kotlinx.locale.language.nativeDisplayName",
                "dev.carcara.kotlinx.locale.language.regionName",
                "dev.carcara.kotlinx.locale.language.scriptName",
                "${spec.registryPackage}.localeDisplayNamesRegistry",
            ),
        ) + """
        |
        |/**
        | * The language, script and region names this build carries.
        | *
        | * The lookup and the display name algorithm live in
        | * `kotlinx-locale-language-cldr-runtime` and `-core`; all this object
        | * contributes is the table.
        | */
        |public object ${spec.objectName} : LanguageNameSource by PayloadLanguageNames(localeDisplayNamesRegistry)
        |
        |/**
        | * This locale's name written in [inLocale], e.g. `Brazilian Portuguese` for
        | * `pt-BR` in `en`.
        | *
        | * [display] chooses between CLDR's own dialect name and one composed from the
        | * language plus its subtags: `British English` against
        | * `English (United Kingdom)`.
        | */
        |public fun Locale.displayName(
        |    inLocale: Locale = Locale.current,
        |    display: LanguageDisplay = LanguageDisplay.DIALECT,
        |    style: LanguageNameStyle = LanguageNameStyle.STANDARD,
        |): String = ${spec.objectName}.displayName(this, inLocale, display, style)
        |
        |/**
        | * This locale's name in its own language: `日本語`, `čeština`, `português`.
        | *
        | * CLDR stores these as the language writes them, which is lower case in
        | * several. A picker row that wants `Čeština` is asking for a capitalization
        | * transform, which is a different question from what the name is.
        | */
        |public val Locale.nativeDisplayName: String get() = ${spec.objectName}.nativeDisplayName(this)
        |
        |/** The name of the language [subtag] in this locale; falls back to the subtag. */
        |public fun Locale.languageName(subtag: String, style: LanguageNameStyle = LanguageNameStyle.STANDARD): String =
        |    ${spec.objectName}.languageName(subtag, this, style)
        |
        |/** The name of the ISO 15924 script [code] in this locale, e.g. `Latin` for `Latn`. */
        |public fun Locale.scriptName(code: String): String = ${spec.objectName}.scriptName(code, this)
        |
        |/**
        | * The name of the region [code] in this locale.
        | *
        | * Wider than the country domain's: this answers for macro-regions too, so
        | * `419` reads `Latin America`.
        | */
        |public fun Locale.regionName(code: String): String = ${spec.objectName}.regionName(code, this)
        |
        """.trimMargin(),
    )
    println("[codegen] emitted ${spec.objectName} to $file")
}
