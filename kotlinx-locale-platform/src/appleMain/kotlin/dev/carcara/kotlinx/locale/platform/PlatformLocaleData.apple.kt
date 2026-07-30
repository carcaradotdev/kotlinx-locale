package dev.carcara.kotlinx.locale.platform

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import platform.Foundation.NSLocale
import platform.Foundation.availableLocaleIdentifiers

@InternalKotlinxLocaleApi
public actual object PlatformLocaleData {

    public actual val isAvailable: Boolean = true

    // Foundation hands back identifiers such as pt_BR, which Locale parses.
    public actual fun availableLocaleTags(): Set<String> =
        NSLocale.availableLocaleIdentifiers.mapNotNullTo(LinkedHashSet()) { it as? String }
}
