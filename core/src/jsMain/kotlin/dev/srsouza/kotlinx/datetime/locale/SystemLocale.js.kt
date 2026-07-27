package dev.srsouza.kotlinx.datetime.locale

internal actual fun platformSystemLocaleTag(): String? = try {
    val locale: Any? = js("Intl.DateTimeFormat().resolvedOptions().locale")
    locale as? String
} catch (_: Throwable) {
    null
}
