package dev.carcara.kotlinx.locale.country.platform

// This target's platform exposes no locale data Kotlin can read, so every
// lookup misses and a consumer composes with a bundled source.
internal actual fun platformCountryName(alpha2: String, localeTag: String): String? = null
