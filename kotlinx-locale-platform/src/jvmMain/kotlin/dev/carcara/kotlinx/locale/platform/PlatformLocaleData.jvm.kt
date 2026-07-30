package dev.carcara.kotlinx.locale.platform

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi

@InternalKotlinxLocaleApi
public actual object PlatformLocaleData {

    public actual val isAvailable: Boolean = true

    public actual fun availableLocaleTags(): Set<String> = java.util.Locale.getAvailableLocales().mapNotNullTo(LinkedHashSet()) { locale ->
        locale.toLanguageTag().takeUnless { it == "und" }
    }
}
