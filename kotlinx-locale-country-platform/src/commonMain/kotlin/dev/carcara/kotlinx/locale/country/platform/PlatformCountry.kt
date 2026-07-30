@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.country.platform

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.CountryNameSource
import dev.carcara.kotlinx.locale.country.countryForDisplayNameOrNull
import dev.carcara.kotlinx.locale.country.displayName
import dev.carcara.kotlinx.locale.platform.PlatformLocaleData

/** The one thing each target has to answer. Everything else is common code. */
internal expect fun platformCountryName(alpha2: String, localeTag: String): String?

/**
 * Country names from the host platform: `java.util.Locale` on JVM and Android,
 * `Intl.DisplayNames` on JS and Wasm/JS, `NSLocale` on Apple.
 *
 * The data is the host's, so it moves with OS and browser versions and will not
 * always agree with the bundled CLDR tables. That is the trade: nothing is
 * shipped, and in exchange the answers are whatever the device says.
 *
 * Linux, Windows, Android Native and WASI expose no locale data Kotlin can read,
 * so on those targets every lookup returns `null` and [supportedLocales] is
 * empty. That is not a failure mode to work around, it is the reason
 * `FallbackCountryNames` exists:
 *
 * ```
 * val names = FallbackCountryNames(primary = PlatformCountry, fallback = CldrCountry)
 * ```
 *
 * The composition also covers the narrower gaps: a platform that knows a locale
 * but not one country in it falls through for that country alone.
 */
public object PlatformCountry : CountryNameSource {

    /**
     * What the platform enumerates, which on JS and Wasm/JS is nothing at all.
     *
     * Empty does not mean unsupported. ECMA-402 will filter a list of locales you
     * already have but offers no way to ask for the list, so a source over `Intl`
     * answers every lookup while being unable to describe its coverage. Check
     * [isAvailable] to tell "no data on this target" from "cannot enumerate".
     */
    override val supportedLocales: Set<Locale> by lazy {
        PlatformLocaleData.availableLocaleTags()
            .mapNotNullTo(LinkedHashSet()) { Locale.forLanguageTagOrNull(it) }
    }

    /** False on the targets whose platform exposes no locale data at all. */
    public val isAvailable: Boolean
        get() = PlatformLocaleData.isAvailable

    /**
     * The platform's name for the country, or `null`.
     *
     * A platform that does not know the code tends to hand the code back rather
     * than admit it: `java.util.Locale` does exactly that. Passing that through
     * would be worse than useless, because the total operation in `-core` already
     * falls back to the code, and a composing source would take the echo for an
     * answer and never consult its fallback. So an answer equal to the code is
     * treated as a miss.
     */
    override fun countryNameOrNull(alpha2: String, locale: Locale): String? {
        val name = platformCountryName(alpha2, locale.toLanguageTag()) ?: return null
        return name.takeIf { it.isNotBlank() && !it.equals(alpha2, ignoreCase = true) }
    }
}

/**
 * The country name for [locale] from the platform; falls back to the alpha-2
 * code when the platform has none.
 *
 * The same signature `kotlinx-locale-country-cldr-full` declares, in a different
 * package, so switching between them is an import change and nothing else.
 */
public fun Country.displayName(locale: Locale = Locale.current): String = PlatformCountry.displayName(this, locale)

/** The country whose platform name in [locale] matches [name], or `null`. */
public fun Country.Companion.forDisplayNameOrNull(name: String, locale: Locale = Locale.current): Country? =
    PlatformCountry.countryForDisplayNameOrNull(name, locale)
