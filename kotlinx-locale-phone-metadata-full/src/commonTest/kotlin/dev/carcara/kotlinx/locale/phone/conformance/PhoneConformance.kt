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

package dev.carcara.kotlinx.locale.phone.conformance

import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.forAlpha2OrNull
import dev.carcara.kotlinx.locale.phone.PhoneNumberFormat
import dev.carcara.kotlinx.locale.phone.PhoneNumberSource
import dev.carcara.kotlinx.locale.phone.PhoneParseResult
import dev.carcara.kotlinx.locale.test.assertEquals
import dev.carcara.kotlinx.locale.test.assertTrue

/**
 * Holds a phone source to libphonenumber's own answers.
 *
 * Every other fixture in this suite compares an implementation of a written
 * specification against the reference implementation of that specification.
 * This one has no specification to appeal to. ITU-T E.164 defines the shape of
 * a number and says nothing about which Brazilian mobile prefixes exist; that
 * is metadata, and the metadata is libphonenumber's. So the claim being made
 * here is narrower and more exacting than elsewhere: not "this follows the
 * standard" but "this is the same program".
 *
 * Which makes the fixture the whole argument for the module. A reimplementation
 * that quietly disagreed about validity would be worse than not having one,
 * because a signup form that rejects a real number is a lost customer and it
 * fails silently. Every territory libphonenumber describes is covered, using its
 * own example numbers, round-tripped through parse before being formatted so
 * that parsing is compared too.
 */
public fun PhoneNumberSource.assertConformsToLibPhoneNumber() {
    assertTrue(phoneGoldenData.size > 200, "expected the full golden set, got ${phoneGoldenData.size}")
    var checked = 0
    var skippedRegions = 0

    for ((territoryId, cases) in phoneGoldenData) {
        val region = Country.forAlpha2OrNull(territoryId)
        if (region == null || region !in supportedRegions) {
            skippedRegions++
            continue
        }
        for (case in cases) {
            val parsed = parse(case.e164, region)
            val number = (parsed as? PhoneParseResult.Parsed)?.number
            assertTrue(number != null, "$territoryId ${case.type} ${case.e164} did not parse")

            assertEquals(case.e164, format(number, PhoneNumberFormat.E164), "$territoryId ${case.type} E164")
            assertEquals(case.isValid, isValid(number), "$territoryId ${case.type} ${case.e164} validity")
            assertEquals(case.reportedType, typeOf(number).name, "$territoryId ${case.type} ${case.e164} type")
            assertEquals(
                case.region,
                regionOf(number)?.name.orEmpty(),
                "$territoryId ${case.type} ${case.e164} region",
            )
            assertEquals(
                case.national,
                format(number, PhoneNumberFormat.NATIONAL),
                "$territoryId ${case.type} ${case.e164} national",
            )
            assertEquals(
                case.international,
                format(number, PhoneNumberFormat.INTERNATIONAL),
                "$territoryId ${case.type} ${case.e164} international",
            )
            assertEquals(
                case.rfc3966,
                format(number, PhoneNumberFormat.RFC3966),
                "$territoryId ${case.type} ${case.e164} rfc3966",
            )
            checked++
        }
    }
    assertTrue(checked > 900, "expected to check the golden set, checked only $checked ($skippedRegions regions skipped)")
}

/**
 * Holds the parser to libphonenumber over inputs that are not well behaved.
 *
 * [assertConformsToLibPhoneNumber] asks about each territory's own example
 * number, which is by construction the easiest input that territory has. Passing
 * it says the tables were read correctly and little about the parser, because
 * almost none of its branches are reached.
 *
 * This reaches them. Every `parse` literal in libphonenumber's own test suite,
 * which is what its authors pinned after fifteen years of bug reports, plus each
 * territory's example number put through the mutations a real input goes
 * through: punctuation, the international dialling prefix instead of a plus,
 * the national prefix present and absent, an extension in each spelling that
 * means one, and the length boundary from both sides.
 *
 * A sixth of the cases are rejections, and they are the half that matters most.
 * A fixture carrying only the inputs that parse would pass for a parser that
 * accepted everything.
 */
public fun PhoneNumberSource.assertParsesLikeLibPhoneNumber() {
    assertTrue(phoneEdgeCases.size > 2000, "expected the full edge set, got ${phoneEdgeCases.size}")
    var checked = 0
    var rejections = 0
    var skipped = 0

    for (case in phoneEdgeCases) {
        val region = case.region.takeIf(String::isNotEmpty)?.let { Country.forAlpha2OrNull(it) }
        if (case.region.isNotEmpty() && (region == null || region !in supportedRegions)) {
            skipped++
            continue
        }
        val number = (parse(case.input, region) as? PhoneParseResult.Parsed)?.number
        val where = "'${case.input}' in ${case.region.ifEmpty { "no region" }}"

        if (case.e164 == null) {
            // libphonenumber refuses it, so this must too. Agreeing about what is
            // not a number is the half a success-only fixture cannot check.
            assertTrue(number == null, "$where should not parse, got ${number?.e164}")
            rejections++
            continue
        }

        assertTrue(number != null, "$where should parse to ${case.e164}")
        assertEquals(case.e164, format(number, PhoneNumberFormat.E164), "$where E164")
        assertEquals(case.isValid, isValid(number), "$where validity")
        assertEquals(case.type, typeOf(number).name, "$where type")
        // The raw code rather than the Country: libphonenumber names
        // territories ISO 3166-1 does not list, and comparing the narrower of
        // the two would be skipping the cases where they differ.
        assertEquals(case.numberRegion, regionCodeOf(number).orEmpty(), "$where region")
        checked++
    }
    assertTrue(checked > 1500, "expected to check the edge set, checked only $checked ($skipped skipped)")
    assertTrue(rejections > 200, "expected the rejections to be checked too, got $rejections")
}
