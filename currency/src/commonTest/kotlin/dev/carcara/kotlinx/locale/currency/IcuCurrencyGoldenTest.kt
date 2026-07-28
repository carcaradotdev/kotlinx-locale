package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.internal.currencyFormatFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-checks the CLDR-generated currency data — symbols, display names,
 * number separators and the standard currency pattern — against ICU's
 * independently encoded resource bundles.
 */
class IcuCurrencyGoldenTest {

    private fun String.normalized() = replace('\u00A0', ' ').replace('\u202F', ' ')

    @Test
    fun runtimeSymbolsAndNamesMatchIcu() {
        assertTrue(icuCurrencyGoldenData.size >= 25, "expected the full golden locale set")
        for (golden in icuCurrencyGoldenData) {
            val locale = Locale.forLanguageTag(golden.tag)
            for ((code, icuSymbol) in golden.symbols) {
                assertEquals(
                    icuSymbol.normalized(),
                    Currency.forCode(code).symbol(locale).normalized(),
                    "${golden.tag} $code symbol",
                )
            }
            for ((code, icuName) in golden.names) {
                assertEquals(
                    icuName.normalized(),
                    Currency.forCode(code).displayName(locale).normalized(),
                    "${golden.tag} $code name",
                )
            }
        }
    }

    @Test
    fun runtimeNumberDataMatchesIcu() {
        for (golden in icuCurrencyGoldenData) {
            val format = currencyFormatFor(Locale.forLanguageTag(golden.tag))
            golden.decimal?.let {
                assertEquals(it.normalized(), format.decimal.normalized(), "${golden.tag} decimal")
            }
            golden.group?.let {
                assertEquals(it.normalized(), format.group.normalized(), "${golden.tag} group")
            }
            golden.currencyPattern?.let {
                assertEquals(
                    it.normalized(),
                    format.standardPattern.normalized(),
                    "${golden.tag} currency pattern",
                )
            }
        }
    }
}
