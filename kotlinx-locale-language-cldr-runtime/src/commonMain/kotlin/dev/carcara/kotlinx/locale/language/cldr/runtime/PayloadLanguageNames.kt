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

@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.language.cldr.runtime

import dev.carcara.kotlinx.locale.Capitalization
import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.internal.ENTRY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.resolvedRecord
import dev.carcara.kotlinx.locale.internal.sparseRecordValue
import dev.carcara.kotlinx.locale.internal.supportedLocalesOf
import dev.carcara.kotlinx.locale.language.LanguageNameSource
import dev.carcara.kotlinx.locale.language.LanguageNameStyle
import dev.carcara.kotlinx.locale.language.LanguageNameUsage
import dev.carcara.kotlinx.locale.language.LocaleDisplayPatterns
import dev.carcara.kotlinx.locale.titlecaseFirstWord

/** The parent tag plus four keyed fields: languages, scripts, territories, patterns. */
private const val FIELD_COUNT = 5

/** The capitalization bit field sits after the keyed fields, resolved rather than sparse. */
private const val CAPITALIZATION_FIELD = 5

/** Where each usage's two bits sit, matching the order the generator writes them in. */
private val USAGE_BIT_BASE = mapOf(
    LanguageNameUsage.LANGUAGE to 8,
    LanguageNameUsage.SCRIPT to 10,
    LanguageNameUsage.TERRITORY to 12,
)

/** The suffix a short-form entry is keyed under, so both spellings share one table. */
private const val SHORT_SUFFIX = "#short"

/**
 * A [LanguageNameSource] over a table of CLDR locale display name records.
 *
 * Each record is the parent tag, then only what this locale's own file declares:
 * language names, script names, territory names and the three composition
 * patterns. A lookup walks the parent chain for whatever the locale leaves out,
 * which is what keeps the table from repeating every English name under every
 * English variant.
 */
public class PayloadLanguageNames(private val records: Map<String, String>) : LanguageNameSource {

    override val supportedLocales: Set<Locale> by lazy { supportedLocalesOf(records) }

    override fun languageNameOrNull(subtag: String, style: LanguageNameStyle, locale: Locale): String? {
        val key = subtag.replace('-', '_')
        if (style == LanguageNameStyle.SHORT) {
            sparseRecordValue(records, locale, field = 1, fieldCount = FIELD_COUNT, key = key + SHORT_SUFFIX)
                ?.let { return it }
        }
        return sparseRecordValue(records, locale, field = 1, fieldCount = FIELD_COUNT, key = key)
    }

    override fun scriptNameOrNull(code: String, locale: Locale): String? =
        sparseRecordValue(records, locale, field = 2, fieldCount = FIELD_COUNT, key = code)

    override fun regionNameOrNull(code: String, locale: Locale): String? =
        sparseRecordValue(records, locale, field = 3, fieldCount = FIELD_COUNT, key = code)

    /**
     * The three patterns, walked up the chain together rather than one at a
     * time.
     *
     * Together, because a locale that overrides only its separator still means
     * its parent's bracket style, and reading each independently would be the
     * same answer for more work.
     */
    override fun capitalized(name: String, usage: LanguageNameUsage, capitalization: Capitalization, locale: Locale): String {
        if (name.isEmpty() || capitalization == Capitalization.MIDDLE_OF_SENTENCE) return name
        val record = resolvedRecord(records, locale) ?: return name
        val bits = record.split(FIELD_SEPARATOR).getOrNull(CAPITALIZATION_FIELD)?.toIntOrNull(16) ?: return name
        val base = USAGE_BIT_BASE.getValue(usage)
        val bit = base + if (capitalization == Capitalization.STANDALONE) 0 else 1
        if ((bits shr bit) and 1 == 0) return name
        return titlecaseFirstWord(name, locale.language)
    }

    override fun displayPatternsOrNull(locale: Locale): LocaleDisplayPatterns? {
        val pattern = sparseRecordValue(records, locale, field = 4, fieldCount = FIELD_COUNT, key = "p")
            ?: return null
        val parts = pattern.split(ENTRY_SEPARATOR)
        return LocaleDisplayPatterns(
            localePattern = parts.getOrNull(0)?.takeIf(String::isNotEmpty) ?: LocaleDisplayPatterns.Root.localePattern,
            localeSeparator = parts.getOrNull(1)?.takeIf(String::isNotEmpty)
                ?: LocaleDisplayPatterns.Root.localeSeparator,
            localeKeyTypePattern = parts.getOrNull(2)?.takeIf(String::isNotEmpty)
                ?: LocaleDisplayPatterns.Root.localeKeyTypePattern,
        )
    }

    public companion object
}
