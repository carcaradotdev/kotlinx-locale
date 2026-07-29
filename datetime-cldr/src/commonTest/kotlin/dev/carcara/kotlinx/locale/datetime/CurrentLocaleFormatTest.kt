package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.format
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

class CurrentLocaleFormatTest {

    @Test
    fun currentLocaleFormatsEndToEnd() {
        // Whatever the platform reports must be formattable.
        val formatted = LocalDate(2026, 7, 27).format(FormatStyle.MEDIUM, Locale.current)
        assertTrue(formatted.isNotBlank())
    }
}
