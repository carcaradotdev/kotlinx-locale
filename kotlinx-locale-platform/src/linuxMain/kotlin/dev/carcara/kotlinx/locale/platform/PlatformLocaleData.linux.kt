package dev.carcara.kotlinx.locale.platform

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi

@InternalKotlinxLocaleApi
public actual object PlatformLocaleData {

    public actual val isAvailable: Boolean = false

    public actual fun availableLocaleTags(): Set<String> = emptySet()
}
