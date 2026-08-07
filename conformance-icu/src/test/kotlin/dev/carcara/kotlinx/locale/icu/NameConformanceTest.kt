package dev.carcara.kotlinx.locale.icu

import at.asitplus.testballoon.matrix.matrixSuite
import com.ibm.icu.text.LocaleDisplayNames
import com.ibm.icu.util.ULocale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.alpha2
import dev.carcara.kotlinx.locale.country.cldr.CldrCountry
import dev.carcara.kotlinx.locale.currency.Currency
import dev.carcara.kotlinx.locale.currency.cldr.CldrCurrency
import dev.carcara.kotlinx.locale.currency.code
import dev.carcara.kotlinx.locale.language.LanguageNameStyle
import dev.carcara.kotlinx.locale.language.cldr.CldrLanguage
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * The four name tables, held to ICU across every locale this library ships.
 *
 * The committed goldens cover thirty locales because a golden wide enough for
 * eleven hundred is megabytes of Kotlin in every native test binary. This runs
 * on the JVM, calls ICU directly and stores nothing, so breadth costs runtime
 * instead of bytes. Between them: the goldens prove the answer is the same on
 * twenty-four targets, and this proves the answer is right.
 *
 * Language, script and region names are the reason this module exists. They are
 * the largest table the library ships, 4.7 MB of source, and until now they had
 * no oracle at all: a generator that misread one field would have shipped it to
 * eleven hundred locales with nothing to say otherwise.
 */
val NameConformanceTest by matrixSuite {

    val tags = CldrCountry.supportedLocales
        .map { it.toLanguageTag() }
        .filter(IcuHarness::icuCarries)
        .sorted()

    test("ICU4J on the classpath is the pinned release") {
        IcuHarness.assertIcuMatchesThePin()
    }

    test("the comparison set is the whole shipped catalogue") {
        // ICU carries fewer locales than CLDR publishes, so some shrinkage is
        // expected. A collapse is not, and this is what tells the two apart.
        assertTrue(
            tags.size > 500,
            "only ${tags.size} locales are comparable against ICU, which suggests the " +
                "availability filter is wrong rather than that ICU shrank",
        )
    }

    test("country names agree with ICU") {
        val comparison = DomainComparison("country-names")
        for (tag in tags) {
            val locale = IcuHarness.locale(tag)
            val icu = LocaleDisplayNames.getInstance(IcuHarness.uLocale(tag))
            for (country in Country.entries) {
                val ours = CldrCountry.countryNameOrNull(country.alpha2, locale) ?: continue
                val theirs = icu.regionDisplayName(country.alpha2)
                comparison.compare(tag, country.alpha2, ours, theirs) {
                    classifyRegion(tag, country.alpha2, ours, theirs)
                }
            }
        }
        comparison.settle(minimumCompared = 100_000)
    }

    test("currency names and symbols agree with ICU") {
        val comparison = DomainComparison("currency-names")
        for (tag in tags) {
            val locale = IcuHarness.locale(tag)
            val uLocale = IcuHarness.uLocale(tag)
            for (currency in Currency.entries) {
                val icuCurrency = com.ibm.icu.util.Currency.getInstance(currency.code) ?: continue

                CldrCurrency.currencyNameOrNull(currency.code, locale)?.let { ours ->
                    val theirs = icuCurrency.getName(uLocale, com.ibm.icu.util.Currency.LONG_NAME, null)
                    comparison.compare(tag, "${currency.code}/name", ours, theirs) {
                        classifyCurrency(tag, currency.code)
                    }
                }
                CldrCurrency.currencySymbolOrNull(currency.code, locale)?.let { ours ->
                    val theirs = icuCurrency.getName(uLocale, com.ibm.icu.util.Currency.SYMBOL_NAME, null)
                    comparison.compare(tag, "${currency.code}/symbol", ours, theirs) {
                        classifyCurrency(tag, currency.code)
                    }
                }
            }
        }
        comparison.settle(minimumCompared = 100_000)
    }

    test("language, script and region display names agree with ICU") {
        val comparison = DomainComparison("display-names")
        // The languages worth naming are the ones the library has a locale for,
        // which is also the set a consumer can ask about.
        val subjects = tags.map { it.substringBefore('-') }.distinct().sorted()
        for (tag in tags) {
            val locale = IcuHarness.locale(tag)
            val icu = LocaleDisplayNames.getInstance(IcuHarness.uLocale(tag))
            for (language in subjects) {
                // STANDARD, because that is what ICU's `languageDisplayName`
                // answers. The SHORT forms are a separate CLDR alt and would be
                // compared against ICU's own short variant or not at all.
                val ours = CldrLanguage.languageNameOrNull(language, LanguageNameStyle.STANDARD, locale) ?: continue
                val theirs = icu.languageDisplayName(language)
                comparison.compare(tag, "lang/$language", ours, theirs) {
                    classifyDisplayName(tag, language, ours, theirs)
                }
            }
            for (script in SCRIPTS) {
                val ours = CldrLanguage.scriptNameOrNull(script, locale) ?: continue
                val theirs = icu.scriptDisplayName(script)
                comparison.compare(tag, "script/$script", ours, theirs) {
                    classifyDisplayName(tag, script, ours, theirs)
                }
            }
        }
        comparison.settle(minimumCompared = 100_000)
    }
}

/** The scripts CLDR names in more than a handful of locales. */
private val SCRIPTS = listOf(
    "Arab", "Armn", "Beng", "Cyrl", "Deva", "Ethi", "Geor", "Grek", "Gujr", "Guru",
    "Hans", "Hant", "Hebr", "Hira", "Jpan", "Kana", "Khmr", "Knda", "Kore", "Laoo",
    "Latn", "Mlym", "Mymr", "Orya", "Sinh", "Taml", "Telu", "Thaa", "Thai", "Tibt",
)

/**
 * Which of the derivable divergences explains a region name, if any.
 *
 * `null` means none of them do, which sends the case to the ledger for a person
 * to look at. That is the intended default: an unexplained difference is the
 * thing this module exists to surface.
 */
private fun classifyRegion(tag: String, code: String, ours: String, icu: String): Divergence? {
    if (!IcuHarness.icuCarries(tag)) return Divergence.BUNDLE_FALLBACK
    // ICU handing back the code itself, or root's answer, is pruning rather
    // than disagreement.
    if (icu == code) return Divergence.ICU_PRUNED
    val root = LocaleDisplayNames.getInstance(ULocale.ROOT).regionDisplayName(code)
    if (icu == root && ours != root) return Divergence.ICU_PRUNED
    return null
}

private fun classifyCurrency(tag: String, code: String): Divergence? {
    if (!IcuHarness.icuCarries(tag)) return Divergence.BUNDLE_FALLBACK
    // ICU resolves a handful of locales to a bundle in a different script; the
    // clearest case is sr-Cyrl-ME, which ICU answers from a Latin bundle. The
    // test is whether ICU's own answer came from the locale that was asked for.
    val asked = IcuHarness.uLocale(tag)
    val answered = com.ibm.icu.util.Currency.getInstance(code)
        ?.getName(asked, com.ibm.icu.util.Currency.LONG_NAME, null)
    if (answered != null && asked.script.isNotEmpty()) {
        val bare = ULocale.forLanguageTag(tag.substringBefore('-'))
        val bareName = com.ibm.icu.util.Currency.getInstance(code)
            ?.getName(bare, com.ibm.icu.util.Currency.LONG_NAME, null)
        if (answered == bareName) return Divergence.BUNDLE_FALLBACK
    }
    return null
}

private fun classifyDisplayName(tag: String, subject: String, ours: String, icu: String): Divergence? {
    if (!IcuHarness.icuCarries(tag)) return Divergence.BUNDLE_FALLBACK
    if (icu == subject) return Divergence.ICU_PRUNED
    val root = LocaleDisplayNames.getInstance(ULocale.ROOT)
    val rootName = runCatching { root.languageDisplayName(subject) }.getOrNull()
        ?: runCatching { root.scriptDisplayName(subject) }.getOrNull()
    if (rootName != null && icu == rootName && ours != rootName) return Divergence.ICU_PRUNED
    return null
}
