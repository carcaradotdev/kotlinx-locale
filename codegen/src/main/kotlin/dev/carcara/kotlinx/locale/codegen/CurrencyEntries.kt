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

package dev.carcara.kotlinx.locale.codegen

/** A `yyyy-MM` withdrawal date as a proleptic Gregorian day number, at the end of that month. */
private fun epochDayOfWithdrawal(text: String): Int? {
    val parts = text.split('-')
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val month = parts.getOrNull(1)?.toIntOrNull() ?: 12
    val a = (14 - month) / 12
    val y = year + 4800 - a
    val m = month + 12 * a - 3
    return 28 + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045 - 2440588
}

/**
 * The currency entry set: ISO 4217 list one, plus the withdrawn codes of list
 * three, each carrying the window CLDR says it was legal tender in.
 *
 * One set rather than two, because a `CurrencyAmount` needs a `Currency` and a
 * settlement record older than its currency's withdrawal has to render. That is
 * also what ICU and `java.util.Currency` do: both carry historical codes and
 * answer "is it available" as a question about a date.
 *
 * List three carries no minor units, so a withdrawn code takes CLDR's fraction
 * data where CLDR has it and the default of two where it does not, which is the
 * same fallback a current code without an `<info>` entry takes.
 */
fun buildCurrencyEntries(
    active: Iso4217Data,
    withdrawn: List<Iso4217HistoricCurrency>,
    supplemental: SupplementalData,
): List<CurrencyEntry> {
    fun entryOf(code: String, numericCode: Int?, minorUnits: Int, name: String, withdrawnOn: String? = null): CurrencyEntry {
        val fractions = supplemental.currencyFractions[code] ?: supplemental.defaultFractions
        val tender = supplemental.currencyTender[code]
        return CurrencyEntry(
            code = code,
            numericCode = numericCode ?: -1,
            minorUnits = minorUnits,
            cldrDigits = fractions.digits,
            cldrRounding = fractions.rounding,
            cldrCashDigits = fractions.cashDigits,
            cldrCashRounding = fractions.cashRounding,
            englishName = name,
            tenderFrom = tender?.from ?: Int.MIN_VALUE,
            // CLDR has no window at all for eighteen of the withdrawn codes, so
            // the ISO withdrawal date stands in. Without it they would look
            // unbounded, which is to say current.
            tenderTo = tender?.to?.takeIf { it != Int.MAX_VALUE || withdrawnOn == null }
                ?: withdrawnOn?.let(::epochDayOfWithdrawal)
                ?: Int.MAX_VALUE,
            isTender = tender?.tender ?: true,
            isCurrent = withdrawnOn == null,
        )
    }

    val entries = LinkedHashMap<String, CurrencyEntry>()
    for (iso in active.currencies) {
        entries[iso.code] = entryOf(iso.code, iso.numericCode, iso.minorUnits, iso.name)
    }
    var added = 0
    for (historic in withdrawn) {
        // List one wins: a code in both is current, and the historic entry is an
        // earlier redenomination of the same code.
        if (historic.code in entries) continue
        // List three omits the minor units field entirely, so the digit count
        // comes from CLDR's currency fractions instead, which carries the same
        // information for the withdrawn codes and defaults to two. The JDK
        // parity test is what confirms the two agree.
        val fractions = supplemental.currencyFractions[historic.code] ?: supplemental.defaultFractions
        entries[historic.code] = entryOf(
            historic.code,
            historic.numericCode,
            fractions.digits,
            historic.name,
            withdrawnOn = historic.withdrawnOn,
        )
        added++
    }

    // ISO reuses a numeric code across generations of the same currency: 191 is
    // both the 1991 Croatian dinar and the kuna that replaced it, 8 is both leks.
    // So a number does not identify a code once the withdrawn set is in, and the
    // lookup by number resolves to the active entry where there is one. What must
    // never happen is two *active* codes sharing a number, because then there is
    // no rule that picks between them.
    val activeCodes = active.currencies.mapTo(HashSet(), Iso4217Currency::code)
    val byNumeric = LinkedHashMap<Int, MutableList<String>>()
    for (entry in entries.values) {
        if (entry.numericCode >= 0) byNumeric.getOrPut(entry.numericCode) { ArrayList() }.add(entry.code)
    }
    val activeCollisions = byNumeric.filterValues { codes -> codes.count { it in activeCodes } > 1 }
    check(activeCollisions.isEmpty()) {
        "two active ISO 4217 codes share a numeric code, so a lookup by number cannot answer: " +
            activeCollisions.entries.joinToString { "${it.key} is ${it.value.joinToString("/")}" }
    }
    val reused = byNumeric.count { it.value.size > 1 }

    println(
        "[codegen] currency entry set: ${active.currencies.size} active plus $added withdrawn " +
            "($reused numeric codes reused across generations, resolved to the active entry)",
    )
    return entries.values.sortedBy(CurrencyEntry::code)
}
