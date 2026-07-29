package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.conformance.icuCurrencyGoldenData
import dev.carcara.kotlinx.locale.currency.cldr.internal.currencyFormatFor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-checks the number-formatting tables against ICU's independently encoded
 * resource bundles.
 *
 * Symbols and display names are checked by the shared conformance suite, which
 * any source can run. These are the raw tables behind the formatter, reachable
 * only from inside the module that owns them, and no source interface exposes
 * them because no platform could implement one that did.
 */
class IcuCurrencyGoldenTest {

    private fun String.normalized() = replace(' ', ' ').replace(' ', ' ')

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
