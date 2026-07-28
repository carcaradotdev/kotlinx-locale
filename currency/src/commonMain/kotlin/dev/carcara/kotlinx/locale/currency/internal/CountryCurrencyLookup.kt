package dev.carcara.kotlinx.locale.currency.internal

import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.internal.data.countryCurrencyCodes

private val currenciesByCountry: Map<String, List<Currency>> by lazy {
    countryCurrencyCodes.mapValues { (_, codes) ->
        codes.split(' ').mapNotNull(Currency::forCodeOrNull)
    }
}

internal fun currenciesOf(country: Country): List<Currency> =
    currenciesByCountry[country.alpha2].orEmpty()

internal fun primaryCurrencyOf(country: Country): Currency? =
    currenciesOf(country).firstOrNull()
