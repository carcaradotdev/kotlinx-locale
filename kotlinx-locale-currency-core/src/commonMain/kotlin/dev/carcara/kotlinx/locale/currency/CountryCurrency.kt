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

package dev.carcara.kotlinx.locale.currency

import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.currency.internal.currenciesOf

/**
 * The current legal-tender currencies of this country per CLDR, preferred first.
 * Empty for countries without a universal currency (e.g. Antarctica).
 */
public val Country.currencies: List<Currency>
    get() = currenciesOf(this)

/** The primary legal-tender currency of this country per CLDR, or `null`. */
public val Country.currency: Currency?
    get() = currenciesOf(this).firstOrNull()
