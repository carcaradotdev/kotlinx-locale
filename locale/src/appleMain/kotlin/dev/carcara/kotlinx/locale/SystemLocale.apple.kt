package dev.carcara.kotlinx.locale

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages

internal actual fun platformSystemLocaleTag(): String? =
    (NSLocale.preferredLanguages.firstOrNull() as? String)
        ?: NSLocale.currentLocale.localeIdentifier
