@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.cldr.internal.data.currencyFormatsRegistry
import dev.carcara.kotlinx.locale.currency.cldr.runtime.CurrencyNumberFormat
import dev.carcara.kotlinx.locale.currency.cldr.runtime.currencyNumberFormatFor

/** The decoded number-format record for [locale] out of this module's own table. */
internal fun currencyFormatFor(locale: Locale): CurrencyNumberFormat = currencyNumberFormatFor(currencyFormatsRegistry, locale)
