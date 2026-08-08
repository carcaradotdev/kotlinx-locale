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

package dev.carcara.kotlinx.locale.currency.cldr.plurals

import at.asitplus.testballoon.matrix.matrixConfig
import at.asitplus.testballoon.matrix.matrixSuite
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testScope
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.cldr.displayName
import dev.carcara.kotlinx.locale.currency.cldr.runtime.pluralName
import dev.carcara.kotlinx.locale.number.PluralCategory
import dev.carcara.kotlinx.locale.test.assertEquals

/**
 * The five shapes CLDR's inheritance marker takes under a count-keyed currency
 * name, each pinned to what ICU answers for the same locale and currency.
 *
 * These are the cases a sweep of 966,540 names against ICU4J turned up, and each
 * one broke a different plausible reading of the rule. They are here rather than
 * in the golden fixture because the fixture covers sixty one locales and none of
 * these five is among them: the marker only changes an answer where a locale
 * overrides part of a currency's naming and leaves the rest to its parent, which
 * is a long tail rather than a headline locale.
 */
val CurrencyPluralInheritanceTest by matrixSuite(matrixConfig { testConfig = TestConfig.testScope(isEnabled = false) }) {

    val nn = Locale.forLanguageTag("nn")
    val enAu = Locale.forLanguageTag("en-AU")
    val es419 = Locale.forLanguageTag("es-419")
    val zhHantHk = Locale.forLanguageTag("zh-Hant-HK")

    fun name(currency: Currency, category: PluralCategory, locale: Locale) = CldrCurrencyPlurals.pluralName(currency, category, locale)

    test("aLocaleThatWritesOnlyMarkersInheritsItsParentsWholeTable") {
        // nn writes markers under both categories of the Aruban florin and its
        // own count-less `arubiske florinar`. It owns nothing, so `no`'s table
        // arrives whole, including the `-er` spelling nn does not otherwise use.
        assertEquals("arubisk florin", name(Currency.AWG, PluralCategory.ONE, nn))
        assertEquals("arubiske floriner", name(Currency.AWG, PluralCategory.OTHER, nn))
        assertEquals("arubiske florinar", Currency.AWG.displayName(nn))
    }

    test("aMarkerBesideARealNameResolvesInsideItsOwnLocale") {
        // nn writes a real `one` for the Colombian peso, so it owns that
        // currency, and its `other` marker reads nn's own count-less name rather
        // than the `colombianske pesos` its parent resolved to.
        assertEquals("kolombiansk peso", name(Currency.COP, PluralCategory.ONE, nn))
        assertEquals("kolombianske pesos", name(Currency.COP, PluralCategory.OTHER, nn))
    }

    test("arealSpellingAnywhereUpTheChainBeatsTheLateralStep") {
        // es-419 owns the tenge through a real `one`, but its `other` marker
        // still reads es's real `tengues kazajos` rather than falling laterally
        // to a display name.
        assertEquals("tenge kazajo", name(Currency.KZT, PluralCategory.ONE, es419))
        assertEquals("tengues kazajos", name(Currency.KZT, PluralCategory.OTHER, es419))
    }

    test("aCategoryAnAncestorSpellsIsNotLeftToTheOtherFallback") {
        // en-AU overrides only `other` for the kina. Its `one` has to reach
        // English's singular rather than en-AU's own plural, which is what a
        // table that dropped `one` for matching English's `other` would give.
        assertEquals("Papua New Guinean kina", name(Currency.PGK, PluralCategory.ONE, enAu))
        assertEquals("Papua New Guinean kinas", name(Currency.PGK, PluralCategory.OTHER, enAu))
    }

    test("aMarkerWithNoRealNameAnywhereFallsThroughToTheAskersDisplayName") {
        // Every locale in this chain writes a marker and no real name, so there
        // is no count-keyed data at all and the third step of UTS #35's chain
        // answers with zh-Hant-HK's own spelling rather than zh-Hant's.
        assertEquals("阿拉伯聯合酋長國迪爾汗", Currency.AED.displayName(zhHantHk))
        for (category in PluralCategory.entries) {
            assertEquals("阿拉伯聯合酋長國迪爾汗", name(Currency.AED, category, zhHantHk))
        }
    }
}
