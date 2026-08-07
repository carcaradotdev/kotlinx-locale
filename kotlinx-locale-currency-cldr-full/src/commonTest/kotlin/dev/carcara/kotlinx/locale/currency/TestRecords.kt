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

package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.currency.cldr.internal.data.currencyFormatsRegistry
import dev.carcara.kotlinx.locale.currency.cldr.runtime.CurrencyNumberFormat
import dev.carcara.kotlinx.locale.currency.cldr.runtime.currencyNumberFormatFor

/** The decoded number-format record for [locale] out of this module's own table. */
internal fun currencyFormatFor(locale: Locale): CurrencyNumberFormat = currencyNumberFormatFor(currencyFormatsRegistry, locale)
