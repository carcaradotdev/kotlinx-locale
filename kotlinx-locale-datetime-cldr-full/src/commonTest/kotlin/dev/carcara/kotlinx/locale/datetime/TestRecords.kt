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

@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.internal.data.localeDataRegistry
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.DateTimeRecord
import dev.carcara.kotlinx.locale.datetime.cldr.runtime.dateTimeRecordFor

/**
 * The decoded record for [locale] out of this module's own table.
 *
 * The pattern tests drive the engine directly rather than through whatever
 * pattern a locale happens to use, and the engine takes a record. Pairing it
 * with the table lives here so the tests read the same as before the format
 * logic moved into its own artifact.
 */
internal fun localeDataFor(locale: Locale): DateTimeRecord = dateTimeRecordFor(localeDataRegistry, locale)
