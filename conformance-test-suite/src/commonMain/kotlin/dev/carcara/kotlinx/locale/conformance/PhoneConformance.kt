package dev.carcara.kotlinx.locale.conformance

import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.forAlpha2OrNull
import dev.carcara.kotlinx.locale.phone.PhoneNumberFormat
import dev.carcara.kotlinx.locale.phone.PhoneNumberSource
import dev.carcara.kotlinx.locale.phone.PhoneParseResult
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
