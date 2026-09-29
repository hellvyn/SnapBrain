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
        assertTrue(a.searchUrl.startsWith("https://www.google.com/search?q="))
        assertTrue(a.searchUrl.contains("JP123"))
        assertEquals("Lacak paket", a.label)
        assertNull(actionOf("track_parcel", "JP 123; DROP", wib))
    }

    @Test
    fun parsesCalendarPayloadInTheGivenZone() {
        val a = actionOf("add_calendar", "2026-10-01T19:30|Rapat RT", wib) as Action.AddCalendar
        assertEquals("Rapat RT", a.title)
        assertEquals(1790857800000L, a.beginMillis) // 2026-10-01 19:30 WIB = 12:30 UTC
        val wita = actionOf("add_calendar", "2026-10-01T19:30|Rapat RT", ZoneId.of("Asia/Makassar")) as Action.AddCalendar
        assertEquals(1790857800000L - 3_600_000L, wita.beginMillis)
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
    fun buildsMapsLinks() {
        val a = actionOf("open_maps", "Sinarmas Land Sudirman", wib) as Action.OpenMaps
        assertEquals("geo:0,0?q=Sinarmas%20Land%20Sudirman", a.geoUri)
        assertEquals("https://www.google.com/maps/search/?api=1&query=Sinarmas+Land+Sudirman", a.webUrl)
        assertNull(actionOf("open_maps", "x".repeat(201), wib))
    }

    @Test
    fun normalizesPhoneNumbers() {
        assertEquals("6281519201166", waNumber("0815-1920-1166"))
        assertEquals("6281519201166", waNumber("81519201166"))
        assertEquals("6281519201166", waNumber("+62 815 1920 1166"))
        assertNull(waNumber("hubungi admin"))
        assertEquals(Action.WhatsApp("6281519201166"), actionOf("whatsapp", "+6281519201166", wib))
        assertEquals("https://wa.me/6281519201166", Action.WhatsApp("6281519201166").url)
        assertEquals("+62215551234", phoneNumber("+62 21 555 1234"))
        assertNull(phoneNumber("12"))
    }

    @Test
    fun searchesTheRightMarketplace() {
        val shopee = actionOf("search_product", "shopee|Gamis Katun", wib) as Action.SearchProduct
        assertEquals("https://shopee.co.id/search?keyword=Gamis+Katun", shopee.url)
        assertEquals("Cari di Shopee", shopee.label)
        val toko = actionOf("search_product", "tokopedia|Sabun 500 ml", wib) as Action.SearchProduct
        assertEquals("https://www.tokopedia.com/search?st=product&q=Sabun+500+ml", toko.url)
        val other = actionOf("search_product", "tiktok|Gamis", wib) as Action.SearchProduct
        assertEquals("other", other.marketplace)
        assertTrue(other.url.startsWith("https://www.google.com/search?tbm=shop&q="))
    }

    @Test
    fun returnsNullForEmptyPayloadOrUnknownType() {
        assertNull(actionOf("copy_text", "  ", wib))
        assertNull(actionOf("copy_text", "x".repeat(201), wib))
        assertNull(actionOf("none", "x", wib))
        assertNull(actionOf(null, null, wib))
        assertEquals(Action.CopyText("1234567890"), actionOf("copy_text", "1234567890", wib))
        assertEquals(Action.Call("+62215551234"), actionOf("call", "+62 21 555 1234", wib))
    }
}
