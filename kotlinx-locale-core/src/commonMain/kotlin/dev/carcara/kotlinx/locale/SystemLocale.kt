package dev.carcara.kotlinx.locale

/**
 * The platform's current locale as a raw tag (BCP 47 or POSIX flavored), or
 * `null` when the platform does not expose one. This is the only expect/actual
 * surface in the library; all parsing and formatting happens in common code.
 */
internal expect fun platformSystemLocaleTag(): String?
