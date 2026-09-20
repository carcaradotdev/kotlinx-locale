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

import kotlin.concurrent.Volatile

/**
 * The locale this library answers with when a caller names none.
 *
 * Unset, [Locale.current] asks the platform. Set, it answers with this instead,
 * whole: an identifier set here replaces the platform's own hour cycle as well
 * as its language. An app that wants the device clock under a chosen language
 * composes the two itself.
 */
public object LocaleDefaults {

    @Volatile
    public var locale: Locale? = null
}
