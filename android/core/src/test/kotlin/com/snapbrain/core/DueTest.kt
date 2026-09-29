package com.snapbrain.core

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DueTest {
    private val now = LocalDateTime.of(2026, 9, 29, 12, 0) // Selasa

    @Test
    fun namesTodayTomorrowAndOtherDays() {
        assertEquals(DueLabel("Hari ini", false), dueLabel("2026-09-29", now))
        assertEquals(DueLabel("Besok 23:59", false), dueLabel("2026-09-30T23:59", now))
        assertEquals(DueLabel("Jum, 2 Okt", false), dueLabel("2026-10-02", now))
    }

    @Test
    fun marksOverdue() {
        assertEquals(DueLabel("Sen, 28 Sep", true), dueLabel("2026-09-28", now))
        assertEquals(DueLabel("Hari ini 09:00", true), dueLabel("2026-09-29T09:00", now))
        assertEquals(DueLabel("Hari ini", false), dueLabel("2026-09-29", now)) // a date-only due lasts all day
    }

    @Test
    fun ignoresBlankOrMalformed() {
        assertNull(dueLabel(null, now))
        assertNull(dueLabel(" ", now))
        assertNull(dueLabel("besok", now))
    }
}
