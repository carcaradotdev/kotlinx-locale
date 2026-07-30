package dev.carcara.kotlinx.locale.country.platform

import platform.Foundation.NSLocale
import platform.Foundation.localizedStringForCountryCode

internal actual fun platformCountryName(alpha2: String, localeTag: String): String? =
    NSLocale(localeIdentifier = localeTag).localizedStringForCountryCode(alpha2)
