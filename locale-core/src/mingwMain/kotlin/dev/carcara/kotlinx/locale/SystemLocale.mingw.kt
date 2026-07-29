package dev.carcara.kotlinx.locale

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKStringFromUtf16
import platform.windows.GetUserDefaultLocaleName
import platform.windows.LOCALE_NAME_MAX_LENGTH
import platform.windows.WCHARVar

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformSystemLocaleTag(): String? = memScoped {
    val buffer = allocArray<WCHARVar>(LOCALE_NAME_MAX_LENGTH)
    val length = GetUserDefaultLocaleName(buffer, LOCALE_NAME_MAX_LENGTH)
    if (length > 0) buffer.toKStringFromUtf16() else null
}
