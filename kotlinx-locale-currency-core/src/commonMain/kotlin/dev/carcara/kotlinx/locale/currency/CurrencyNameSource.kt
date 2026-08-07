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

package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource

/**
 * A source of localized currency symbols and display names.
 *
 * Keyed by ISO 4217 alphabetic code rather than by [Currency] so that the
 * contract does not depend on which entry set is in play.
 */
public interface CurrencyNameSource : LocaleDataSource {

    /** The currency symbol for [locale], or `null` when this source has none. */
    public fun currencySymbolOrNull(currencyCode: String, locale: Locale): String?

    /**
     * The symbol for [locale] written in [style], or `null` when this source has
     * none in that style.
     *
     * A miss rather than the plain symbol, which is what makes the alternative
     * spellings compose. [FallbackCurrencyNames] asks each source in turn, so a
     * source that answered its plain symbol here would stop the chain before a
     * source that does carry the alternative was ever consulted, the same way an
     * echoed ISO code would. Falling back to [CurrencySymbolStyle.SYMBOL] is the
     * caller's step, and [symbol] takes it once the whole chain has been asked.
     *
     * Defaulted rather than abstract because this interface is implemented
     * outside this build: a new abstract method breaks every implementor, and
     * the platform sources have no alternative spellings to answer with anyway.
     */
    public fun currencySymbolOrNull(currencyCode: String, locale: Locale, style: CurrencySymbolStyle): String? =
        if (style == CurrencySymbolStyle.SYMBOL) currencySymbolOrNull(currencyCode, locale) else null

    /** The currency display name for [locale], or `null` when this source has none. */
    public fun currencyNameOrNull(currencyCode: String, locale: Locale): String?

    public companion object
}

/**
 * The symbol for [currency] in [locale], e.g. `US$` for USD in pt-BR; falls
 * back to the ISO code.
 *
 * An alternative [style] that this locale does not declare falls back to
 * [CurrencySymbolStyle.SYMBOL] before falling back to the code, which is the
 * order ICU resolves the same three spellings in.
 */
public fun CurrencyNameSource.symbol(currency: Currency, locale: Locale, style: CurrencySymbolStyle = CurrencySymbolStyle.SYMBOL): String {
    if (style == CurrencySymbolStyle.CODE) return currency.code
    return currencySymbolOrNull(currency.code, locale, style)
        ?: currencySymbolOrNull(currency.code, locale)
        ?: currency.code
}

/** The display name for [currency] in [locale]; falls back to the ISO code. */
public fun CurrencyNameSource.displayName(currency: Currency, locale: Locale): String =
    currencyNameOrNull(currency.code, locale) ?: currency.code

/**
 * Answers from [primary], and from [fallback] wherever primary has nothing.
 * Symbols and names are dispatched separately, so a primary carrying only
 * symbols composes with a source carrying only names.
 */
public class FallbackCurrencyNames(private val primary: CurrencyNameSource, private val fallback: CurrencyNameSource) :
    CurrencyNameSource {

    override val supportedLocales: Set<Locale>
        get() = primary.supportedLocales + fallback.supportedLocales

    override fun currencySymbolOrNull(currencyCode: String, locale: Locale): String? =
        primary.currencySymbolOrNull(currencyCode, locale) ?: fallback.currencySymbolOrNull(currencyCode, locale)

    override fun currencySymbolOrNull(currencyCode: String, locale: Locale, style: CurrencySymbolStyle): String? =
        primary.currencySymbolOrNull(currencyCode, locale, style)
            ?: fallback.currencySymbolOrNull(currencyCode, locale, style)

    override fun currencyNameOrNull(currencyCode: String, locale: Locale): String? =
        primary.currencyNameOrNull(currencyCode, locale) ?: fallback.currencyNameOrNull(currencyCode, locale)

    public companion object
}
