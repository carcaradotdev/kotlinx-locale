package dev.carcara.kotlinx.locale.datetime

import at.asitplus.testballoon.matrix.matrixSuite
import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.format
import dev.carcara.kotlinx.locale.test.assertTrue
import kotlinx.datetime.LocalDate

val CurrentLocaleFormatTest by matrixSuite {

    test("currentLocaleFormatsEndToEnd") {
        // Whatever the platform reports must be formattable.
        val formatted = LocalDate(2026, 7, 27).format(FormatStyle.MEDIUM, Locale.current)
        assertTrue(formatted.isNotBlank())
    }
}
