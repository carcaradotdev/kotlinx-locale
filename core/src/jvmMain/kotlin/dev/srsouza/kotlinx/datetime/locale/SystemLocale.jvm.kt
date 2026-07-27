package dev.srsouza.kotlinx.datetime.locale

internal actual fun platformSystemLocaleTag(): String? =
    java.util.Locale.getDefault().toLanguageTag()
