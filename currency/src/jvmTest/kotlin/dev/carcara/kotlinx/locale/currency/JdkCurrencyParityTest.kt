package dev.carcara.kotlinx.locale.currency

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cross-checks the generated ISO 4217 data against the JDK's currency table —
 * a third independent source next to the vendored ISO list and ICU. Codes the
 * JDK does not know (its data can lag recent ISO amendments) are skipped, but
 * only a handful of skips are tolerated.
 */
class JdkCurrencyParityTest {

    @Test
    fun numericCodesAndFractionDigitsMatchTheJdk() {
        var skipped = 0
        val mismatches = ArrayList<String>()
        for (currency in Currency.entries) {
            val jdk = try {
                java.util.Currency.getInstance(currency.code)
            } catch (e: IllegalArgumentException) {
                skipped++
                continue
            }
            if (jdk.numericCode != currency.numericCode) {
                mismatches.add("${currency.code}: numeric ${currency.numericCode} vs JDK ${jdk.numericCode}")
            }
            if (jdk.defaultFractionDigits != currency.defaultFractionDigits) {
                mismatches.add(
                    "${currency.code}: digits ${currency.defaultFractionDigits} " +
                        "vs JDK ${jdk.defaultFractionDigits}",
                )
            }
        }
        assertTrue(mismatches.isEmpty(), mismatches.joinToString("\n"))
        assertTrue(skipped <= 5, "JDK was missing $skipped currencies; its data may be stale")
    }
}
