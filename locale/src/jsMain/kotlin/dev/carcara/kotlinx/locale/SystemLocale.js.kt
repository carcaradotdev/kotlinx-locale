package dev.carcara.kotlinx.locale

internal actual fun platformSystemLocaleTag(): String? = try {
    val locale: Any? = js("Intl.DateTimeFormat().resolvedOptions().locale")
    locale as? String
} catch (_: Throwable) {
    null
}
