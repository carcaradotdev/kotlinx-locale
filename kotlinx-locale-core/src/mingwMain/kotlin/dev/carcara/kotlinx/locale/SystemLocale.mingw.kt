/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
