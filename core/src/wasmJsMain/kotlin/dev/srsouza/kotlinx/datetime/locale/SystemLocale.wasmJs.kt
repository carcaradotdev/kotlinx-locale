package dev.srsouza.kotlinx.datetime.locale

private fun intlLocaleTag(): String = js("Intl.DateTimeFormat().resolvedOptions().locale || ''")

internal actual fun platformSystemLocaleTag(): String? = try {
    intlLocaleTag().takeIf(String::isNotEmpty)
} catch (_: Throwable) {
    null
}
