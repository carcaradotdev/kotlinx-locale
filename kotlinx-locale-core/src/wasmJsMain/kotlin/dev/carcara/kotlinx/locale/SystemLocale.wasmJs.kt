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

private fun intlResolvedOptions(): JsAny = js("Intl.DateTimeFormat(undefined, { hour: 'numeric' }).resolvedOptions()")

private fun localeOf(options: JsAny): String = js("options.locale || ''")

private fun hourCycleOf(options: JsAny): String = js("options.hourCycle || ''")

/**
 * What ECMA-402 resolves for the default locale, read once and held for the
 * process.
 *
 * One formatter answers both the tag and the cycle: `hourCycle` is filled in
 * only when an hour field is asked for, and asking for one leaves `locale`
 * alone. `Locale.current` is the default argument of every locale-taking
 * function in this library, so reading afresh would build two formatters on
 * every format call. Nothing in a browser or in Node moves the `Intl` default
 * inside a process, so there is nothing to invalidate on.
 */
private val resolvedOptions: JsAny? = try {
    intlResolvedOptions()
} catch (_: Throwable) {
    null
}

internal actual fun platformSystemLocaleTag(): String? = resolvedOptions?.let(::localeOf)?.takeIf(String::isNotEmpty)

internal actual fun platformHourCycleKeyword(): String? = resolvedOptions?.let(::hourCycleOf)?.lowercase()?.takeIf(String::isNotEmpty)
