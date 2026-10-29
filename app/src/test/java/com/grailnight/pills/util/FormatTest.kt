package com.grailnight.pills.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `amounts drop trailing zeros and use a decimal comma`() {
        assertEquals("1", formatNumber(1.0))
        assertEquals("0,5", formatNumber(0.5))
        assertEquals("2,25", formatNumber(2.25))
        assertEquals("10", formatNumber(10.0))
    }

    @Test
    fun `minutes of day are shown as 24-hour time`() {
        assertEquals("08:05", formatMinuteOfDay(8 * 60 + 5))
        assertEquals("00:00", formatMinuteOfDay(0))
        assertEquals("23:59", formatMinuteOfDay(23 * 60 + 59))
    }

    @Test
    fun `russian plural forms`() {
        assertEquals(listOf(2, 0, 1, 1, 1, 2, 2, 2, 2, 0, 1, 2), listOf(0, 1, 2, 3, 4, 5, 11, 12, 14, 21, 22, 111).map(::russianPluralForm))
    }
}
