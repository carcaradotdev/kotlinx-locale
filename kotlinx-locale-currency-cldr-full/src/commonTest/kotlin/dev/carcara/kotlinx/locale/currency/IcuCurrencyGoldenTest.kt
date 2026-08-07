@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.currency

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.conformance.icuCurrencyGoldenData
import dev.carcara.kotlinx.locale.test.assertEquals

/**
 * Cross-checks the number-formatting tables against ICU's independently encoded
 * resource bundles.
 *
 * Symbols and display names are checked by the shared conformance suite, which
 * any source can run. These are the raw tables behind the formatter, reachable
 * only from inside the module that owns them, and no source interface exposes
 * them because no platform could implement one that did.
 */
val IcuCurrencyGoldenTest by matrixSuite {

    fun String.normalized() = replace(' ', ' ').replace(' ', ' ')

    test("runtimeNumberDataMatchesIcu") {
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
