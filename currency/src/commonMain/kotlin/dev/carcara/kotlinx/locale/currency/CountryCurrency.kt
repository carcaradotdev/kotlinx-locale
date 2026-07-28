package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.currency.internal.currenciesOf

/**
 * The current legal-tender currencies of this country per CLDR, preferred first.
 * Empty for countries without a universal currency (e.g. Antarctica).
 */
public val Country.currencies: List<Currency>
    get() = currenciesOf(this)

/** The primary legal-tender currency of this country per CLDR, or `null`. */
public val Country.currency: Currency?
    get() = currenciesOf(this).firstOrNull()
