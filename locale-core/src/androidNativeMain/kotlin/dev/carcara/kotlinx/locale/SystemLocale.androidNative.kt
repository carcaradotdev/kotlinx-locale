package dev.carcara.kotlinx.locale

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformSystemLocaleTag(): String? {
    val raw = getenv("LC_ALL")?.toKString()?.takeIf(String::isNotEmpty)
        ?: getenv("LC_TIME")?.toKString()?.takeIf(String::isNotEmpty)
        ?: getenv("LANG")?.toKString()?.takeIf(String::isNotEmpty)
        ?: return null
    return raw.takeUnless { it == "C" || it == "POSIX" }
}
