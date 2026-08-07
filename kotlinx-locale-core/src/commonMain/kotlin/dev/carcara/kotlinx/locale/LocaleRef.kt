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

/**
 * A compile-time name for one locale, as opposed to a string that is only
 * checked when it is parsed.
 *
 * `kotlinx-locale-types` generates one implementation per language, so
 * `PT.BR.tag` cannot be misspelled and autocompletes, and `PT` alone is the bare
 * `pt`. That matters most in the Gradle plugin, whose configuration is a locale
 * set: a typo there does not throw, it quietly generates data for one locale
 * fewer than intended.
 *
 * Nothing requires it in application code. [Locale.forLanguageTag] stays the
 * zero-cost path for tags built at runtime.
 */
public interface LocaleRef {

    /** The canonical BCP 47 tag, e.g. `pt-BR` or `zh-Hans-CN`. */
    public val tag: String

    public companion object
}

/** The locale this reference names. */
public fun LocaleRef.toLocale(): Locale = Locale.forLanguageTag(tag)
