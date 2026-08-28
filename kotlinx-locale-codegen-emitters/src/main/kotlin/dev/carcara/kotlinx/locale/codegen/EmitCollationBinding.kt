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

/** `CldrCollation`-shaped binding: the source object plus the comparator entry points. */
public fun emitCollationBinding(outputRoot: File, spec: BindingSpec) {
    val file = outputRoot.packageFile(spec.packageName, "Collation.kt")
    file.writeText(
        preamble(
            spec,
            listOf(
                "dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi",
                "dev.carcara.kotlinx.locale.Locale",
                "dev.carcara.kotlinx.locale.collation.CollationSource",
                "dev.carcara.kotlinx.locale.collation.CollationStrength",
                "dev.carcara.kotlinx.locale.collation.cldr.runtime.PayloadCollation",
                "dev.carcara.kotlinx.locale.internal.Normalization",
                "dev.carcara.kotlinx.locale.internal.resolvedRecord",
                "${spec.registryPackage}.collationRootRegistry",
                "${spec.registryPackage}.collationTailoringsRegistry",
                "${spec.registryPackage}.normalizationRegistry",
            ),
            fileAnnotation = "@file:OptIn(InternalKotlinxLocaleApi::class)",
        ) + """
        |
        |/**
        | * The collation order this build carries.
        | *
        | * The algorithm lives in `kotlinx-locale-collation-cldr-runtime`; all this
        | * object contributes is the root weight table, the normalisation data under
        | * it and one tailoring per locale.
        | *
        | * The root table and the normalisation data are installed once, on first
        | * use, because both are properties of the characters rather than of any
        | * locale. The tailorings are looked up per locale and cached, since folding
        | * one into the root is work a second call should not repeat.
        | */
        |public object ${spec.objectName} : CollationSource {
        |
        |    // Keyed on the delta rather than on the tag: two locales that tailor
        |    // the root the same way are the same table, and most of CLDR tailors
        |    // it not at all.
        |    private val tables = HashMap<String, PayloadCollation.Tailored>()
        |    private var installed = false
        |
        |    private fun install(locale: Locale): Boolean {
        |        if (installed) return true
        |        // Both registries carry one entry, under `root`, so any locale
        |        // resolves to it. Read through `resolvedRecord` all the same,
        |        // because that is what unpacks and inflates the record.
        |        val normalization = resolvedRecord(normalizationRegistry, locale) ?: return false
        |        val table = resolvedRecord(collationRootRegistry, locale) ?: return false
        |        Normalization.install(normalization)
        |        PayloadCollation.install(table)
        |        installed = true
        |        return true
        |    }
        |
        |    override fun comparatorOrNull(locale: Locale, strength: CollationStrength): Comparator<String>? {
        |        if (!install(locale)) return null
        |        val delta = resolvedRecord(collationTailoringsRegistry, locale) ?: return null
        |        return tables.getOrPut(delta) { PayloadCollation.tailored(delta) }.at(strength)
        |    }
        |}
        |
        |/**
        | * The order [locale] reads a list in.
        | *
        | * ```
        | * listOf("Zypern", "Ísland", "Österreich").sortedWith(collationComparator(de))
        | * // Ísland, Österreich, Zypern
        | * ```
        | *
        | * Comparing strings with `<` orders them by code point, which puts Ísland
        | * after Zimbabwe and every accented initial at the bottom of the list. This
        | * orders them the way a reader of [locale] expects, per UTS #10 and the
        | * per-locale tailorings of UTS #35 Part 5.
        | *
        | * [strength] says how much of a difference counts. The default orders a
        | * list, where no two entries should tie. [CollationStrength.PRIMARY] treats
        | * resume and résumé as one word, which is what a search box wants.
        | *
        | * Falls back to the root order, which is CLDR's default and still sorts far
        | * better than code point order, when this build carries no tailoring for
        | * [locale]. `resolvedRecord` does that fallback itself, so the natural
        | * order below is only reached by a build generated with no root at all.
        | */
        |public fun collationComparator(
        |    locale: Locale = Locale.current,
        |    strength: CollationStrength = CollationStrength.TERTIARY,
        |): Comparator<String> = ${spec.objectName}.comparatorOrNull(locale, strength) ?: naturalOrder()
        |
        """.trimMargin(),
    )
    println("[codegen] emitted ${spec.objectName} to $file")
}
