package dev.carcara.kotlinx.locale.phone

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.phone.conformance.assertConformsToLibPhoneNumber
import dev.carcara.kotlinx.locale.phone.conformance.assertParsesLikeLibPhoneNumber
import dev.carcara.kotlinx.locale.phone.metadata.PhoneNumbers

/** The bundled metadata is a second encoding of libphonenumber's, so it answers to it. */
val PhoneConformanceTest by matrixSuite {

    test("agreesWithLibPhoneNumber") {
        PhoneNumbers.assertConformsToLibPhoneNumber()
    }

    test("parsesTheAwkwardInputsTheSameWay") {
        PhoneNumbers.assertParsesLikeLibPhoneNumber()
    }
}
