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

package dev.carcara.kotlinx.locale.number

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.LocaleDataSource

/**
 * A source of ordinal forms written with digits: `1st`, `1.`, `1º`, `第1`.
 *
 * The rules come from CLDR's `digits-ordinal` rule set, which is rule-based
 * rather than table-based and lives in `common/rbnf` rather than in the locale
 * files. Roughly forty locales declare one; the rest inherit root's, which
 * appends a full stop, and that is the correct form for German, Czech, Slovak,
 * Croatian, Hungarian and Icelandic rather than a gap in the data.
 *
 * Note that the ordinal *plural category* is a different thing and rarely
 * enough on its own. Czech, German, Spanish, Croatian, Icelandic, Portuguese and
 * Slovak all have exactly one ordinal category, so knowing it tells you nothing
 * about the printed form. English is the outlier where it decides everything,
 * and CLDR's rule is literally
 * `$(ordinal,one{st}two{nd}few{rd}other{th})$`.
 *
 * CLDR also ships gendered and case-inflected ordinal rule sets, thirty-two of
 * them for Russian. UTS #35 says plainly that it supplies no data for choosing
 * between them, so exposing them would hand a caller a decision nothing in the
 * data can answer. Only the plain form is here.
 */
public interface OrdinalFormatSource : LocaleDataSource {

    /** [value] as an ordinal in [locale], or `null` when this build has no rules for it. */
    public fun ordinalOrNull(value: Long, locale: Locale): String?
}

/**
 * [value] as an ordinal in [locale]; falls back to the plain digits.
 *
 * The bare number is what an ordinal degrades to in a language whose rules are
 * missing, and it is what several languages write anyway.
 */
public fun OrdinalFormatSource.ordinal(value: Long, locale: Locale = Locale.current): String =
    ordinalOrNull(value, locale) ?: value.toString()

/** Answers from [primary], and from [fallback] wherever primary has nothing. */
public class FallbackOrdinalFormats(private val primary: OrdinalFormatSource, private val fallback: OrdinalFormatSource) :
    OrdinalFormatSource {

    override val supportedLocales: Set<Locale> get() = primary.supportedLocales + fallback.supportedLocales

    override fun ordinalOrNull(value: Long, locale: Locale): String? =
        primary.ordinalOrNull(value, locale) ?: fallback.ordinalOrNull(value, locale)
}
