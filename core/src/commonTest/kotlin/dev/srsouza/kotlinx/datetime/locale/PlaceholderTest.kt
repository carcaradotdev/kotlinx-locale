package dev.srsouza.kotlinx.datetime.locale

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholderTest {

    @Test
    fun placeholderFormatUsesIsoDate() {
        val date = LocalDate(2026, 7, 27)
        assertEquals("kotlinx-datetime-locale: 2026-07-27", date.placeholderFormat())
    }
}
