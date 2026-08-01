package dev.carcara.kotlinx.locale.phone

import dev.carcara.kotlinx.locale.phone.conformance.assertConformsToLibPhoneNumber
import dev.carcara.kotlinx.locale.phone.conformance.assertParsesLikeLibPhoneNumber
import dev.carcara.kotlinx.locale.phone.metadata.PhoneNumbers
import kotlin.test.Test

/** The bundled metadata is a second encoding of libphonenumber's, so it answers to it. */
class PhoneConformanceTest {

    @Test
    fun agreesWithLibPhoneNumber() = PhoneNumbers.assertConformsToLibPhoneNumber()

    @Test
    fun parsesTheAwkwardInputsTheSameWay() = PhoneNumbers.assertParsesLikeLibPhoneNumber()
}
