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

package dev.carcara.kotlinx.locale.currency.platform

// Kotlin/Wasm does support parameters inside a js body, which is why these read
// as strings here and as external declarations on Kotlin/JS.

private fun intlCurrencySymbol(currencyCode: String, localeTag: String): String? = js(
    "(function(){try{" +
        "var p=new Intl.NumberFormat([localeTag],{style:'currency',currency:currencyCode}).formatToParts(0);" +
        "for(var i=0;i<p.length;i++){if(p[i].type==='currency')return p[i].value}return null" +
        "}catch(e){return null}})()",
)

private fun intlCurrencyName(currencyCode: String, localeTag: String): String? = js(
    "(function(){try{" +
        "return new Intl.DisplayNames([localeTag],{type:'currency',fallback:'none'}).of(currencyCode)||null" +
        "}catch(e){return null}})()",
)

private fun intlFormatCurrency(
    amount: String,
    currencyCode: String,
    localeTag: String,
    useIsoCode: Boolean,
    accounting: Boolean,
): String? = js(
    "(function(){try{" +
        "return new Intl.NumberFormat([localeTag],{style:'currency',currency:currencyCode," +
        "currencyDisplay:useIsoCode?'code':'symbol'," +
        "currencySign:accounting?'accounting':'standard'}).format(amount)" +
        "}catch(e){return null}})()",
)

internal actual fun platformCurrencySymbol(currencyCode: String, localeTag: String): String? = intlCurrencySymbol(currencyCode, localeTag)

internal actual fun platformCurrencyName(currencyCode: String, localeTag: String): String? = intlCurrencyName(currencyCode, localeTag)

internal actual fun platformFormatCurrency(
    amount: String,
    currencyCode: String,
    localeTag: String,
    useIsoCode: Boolean,
    accounting: Boolean,
): String? = intlFormatCurrency(amount, currencyCode, localeTag, useIsoCode, accounting)

/** ECMA-402 has no parser at all, so this is always a miss. */
internal actual fun platformParseCurrency(text: String, currencyCode: String, localeTag: String): String? = null
