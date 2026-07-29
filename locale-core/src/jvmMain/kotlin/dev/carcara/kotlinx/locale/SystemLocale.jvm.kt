package dev.carcara.kotlinx.locale

internal actual fun platformSystemLocaleTag(): String? = java.util.Locale.getDefault().toLanguageTag()
