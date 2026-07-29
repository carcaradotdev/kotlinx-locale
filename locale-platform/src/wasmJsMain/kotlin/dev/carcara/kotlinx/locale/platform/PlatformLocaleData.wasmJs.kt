package dev.carcara.kotlinx.locale.platform

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi

@InternalKotlinxLocaleApi
public actual object PlatformLocaleData {

    public actual val isAvailable: Boolean = true

    /**
     * Empty, and not because the runtime is short of data. ECMA-402 offers
     * `supportedLocalesOf` to filter a list you already have but nothing to ask
     * for the list, so a source here answers lookups it cannot enumerate.
     */
    public actual fun availableLocaleTags(): Set<String> = emptySet()
}
