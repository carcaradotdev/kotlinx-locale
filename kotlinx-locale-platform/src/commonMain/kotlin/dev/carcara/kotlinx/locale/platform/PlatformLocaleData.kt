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

package dev.carcara.kotlinx.locale.platform

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi

/**
 * What the host platform can say about locales.
 *
 * Two questions, because the answers are independent and both matter. A target
 * can have no locale data at all (Linux, Windows and WASI ship none that Kotlin
 * can reach), and a target can have plenty of data while being unable to list
 * it: `Intl` answers any lookup you make but exposes no way to enumerate what it
 * knows.
 *
 * That second case is why [availableLocaleTags] returning nothing does not mean
 * the platform supports nothing. Check [isAvailable] to tell the two apart.
 */
@InternalKotlinxLocaleApi
public expect object PlatformLocaleData {

    /** False when this target's platform exposes no locale data Kotlin can read. */
    public val isAvailable: Boolean

    /**
     * The locale tags the platform enumerates, or an empty set when it cannot
     * enumerate. Tags are raw platform identifiers, so they may use `_` rather
     * than `-`; [dev.carcara.kotlinx.locale.Locale.forLanguageTagOrNull] accepts
     * both.
     */
    public fun availableLocaleTags(): Set<String>
}
