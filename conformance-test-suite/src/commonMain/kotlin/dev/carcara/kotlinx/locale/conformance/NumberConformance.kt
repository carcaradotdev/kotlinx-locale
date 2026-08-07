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

package dev.carcara.kotlinx.locale.conformance

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.number.Decimal
import dev.carcara.kotlinx.locale.number.NumberFormatSource
import dev.carcara.kotlinx.locale.number.format
import dev.carcara.kotlinx.locale.number.symbols
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertNotNull
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * Runs a number source through the shape checks every implementation owes,
 * regardless of where its data came from.
 *
 * The comparisons against CLDR's plural samples and against ICU's formatting
 * are not here: they need goldens, and the goldens live in the module that owns
 * the tables they describe. `number-cldr-full` runs them as its own cases.
 */
public fun NumberFormatSource.assertNumbersAreWellShaped() {
    val english = Locale.of("en")
    assertTrue(supportedLocales.isNotEmpty(), "a CLDR-backed source is expected to enumerate its locales")

    for (locale in supportedLocales) {
        val symbols = symbols(locale)
        assertEquals(10, symbols.digits.size, "$locale does not carry ten digits")
        assertTrue(symbols.decimal.isNotEmpty(), "$locale has no decimal separator")
        assertTrue(symbols.minimumGroupingDigits >= 1, "$locale has an impossible minimumGroupingDigits")
        assertTrue(format(1234567L, locale).isNotBlank(), "$locale formatted nothing")
    }

    // A number round trips through its own locale, which is what makes the
    // separators the source hands out the same ones it prints.
    for (locale in listOf(english, Locale.of("de"), Locale.of("cs"), Locale.of("pl"))) {
        val original = Decimal.parse("1234.56")
        val formatted = format(original, locale, minimumFractionDigits = 2, maximumFractionDigits = 2)
        val parsed = assertNotNull(parseDecimalOrNull(formatted, locale), "$locale could not read back '$formatted'")
        assertEquals(0, original.compareTo(parsed), "$locale round trip: '$formatted' read back as $parsed")
    }
}
