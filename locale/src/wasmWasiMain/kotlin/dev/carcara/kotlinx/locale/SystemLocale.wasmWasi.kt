package dev.carcara.kotlinx.locale

// WASI preview 1 exposes no locale concept; callers fall back to `en` via Locale.current.
internal actual fun platformSystemLocaleTag(): String? = null
