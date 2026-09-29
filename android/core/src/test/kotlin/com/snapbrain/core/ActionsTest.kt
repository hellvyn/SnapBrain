package com.snapbrain.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActionsTest {
    private val wib = ZoneId.of("Asia/Jakarta")

    @Test
    fun buildsParcelSearch() {
        val a = actionOf("track_parcel", "JP123", wib) as Action.TrackParcel
        assertEquals("JP123", a.resi)
        assertTrue(a.searchUrl.startsWith("https://www.google.com/search?q="))
        assertTrue(a.searchUrl.contains("JP123"))
        assertEquals("🚚 Lacak Paket", a.label)
    }

    @Test
    fun parsesCalendarPayloadInJakartaTime() {
        val a = actionOf("add_calendar", "2026-10-01T19:30|Rapat RT", wib) as Action.AddCalendar
        assertEquals("Rapat RT", a.title)
        assertEquals(1790857800000L, a.beginMillis) // 2026-10-01 19:30 WIB = 12:30 UTC
    }

    @Test
    fun rejectsMalformedCalendarPayload() {
        assertNull(actionOf("add_calendar", "besok malam|Rapat", wib))
        assertNull(actionOf("add_calendar", "2026-10-01T19:30", wib))
    }

    @Test
    fun opensOnlyHttpUrls() {
        assertEquals(Action.OpenUrl("https://a.id"), actionOf("open_url", "https://a.id", wib))
        assertNull(actionOf("open_url", "intent://x", wib))
    }

    @Test
    fun returnsNullForEmptyPayloadOrNone() {
        assertNull(actionOf("copy_text", "  ", wib))
        assertNull(actionOf("none", "x", wib))
        assertNull(actionOf(null, null, wib))
        assertEquals(Action.CopyText("1234567890"), actionOf("copy_text", "1234567890", wib))
    }
}
