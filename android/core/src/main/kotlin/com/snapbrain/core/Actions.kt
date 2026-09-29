package com.snapbrain.core

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

sealed interface Action {
    val label: String

    data class OpenUrl(val url: String) : Action {
        override val label get() = "Buka link"
    }

    data class TrackParcel(val resi: String, val searchUrl: String) : Action {
        override val label get() = "Lacak paket"
    }

    data class AddCalendar(val title: String, val beginMillis: Long) : Action {
        override val label get() = "Kalender"
    }

    data class CopyText(val text: String) : Action {
        override val label get() = "Salin"
    }

    data class OpenMaps(val query: String) : Action {
        override val label get() = "Buka Maps"
        val geoUri get() = "geo:0,0?q=" + encode(query).replace("+", "%20")
        val webUrl get() = "https://www.google.com/maps/search/?api=1&query=" + encode(query)
    }

    data class WhatsApp(val number: String) : Action {
        override val label get() = "Chat WA"
        val url get() = "https://wa.me/$number"
    }

    data class Call(val number: String) : Action {
        override val label get() = "Telepon"
    }

    data class SearchProduct(val marketplace: String, val query: String) : Action {
        override val label get() = when (marketplace) {
            "shopee" -> "Cari di Shopee"
            "tokopedia" -> "Cari di Tokopedia"
            else -> "Cari barang"
        }
        val url get() = when (marketplace) {
            "shopee" -> "https://shopee.co.id/search?keyword=" + encode(query)
            "tokopedia" -> "https://www.tokopedia.com/search?st=product&q=" + encode(query)
            else -> "https://www.google.com/search?tbm=shop&q=" + encode(query)
        }
    }
}

private fun encode(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

private val HTTP_URL = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
private val RESI = Regex("^[A-Za-z0-9-]{1,40}$")
private val MARKETPLACES = setOf("shopee", "tokopedia", "other")

/** Digits only, with Indonesian 08…/8… rewritten to 628…: wa.me needs the country code. */
fun waNumber(raw: String): String? {
    val d = raw.filter { it.isDigit() }
    val n = when {
        d.startsWith("0") -> "62" + d.drop(1)
        d.startsWith("8") -> "62$d"
        else -> d
    }
    return n.takeIf { it.length in 8..15 }
}

fun phoneNumber(raw: String): String? {
    val t = raw.trim()
    val d = t.filter { it.isDigit() }
    return if (d.length in 5..15) (if (t.startsWith("+")) "+" else "") + d else null
}

/**
 * Maps a stored action to something the UI can run; null means "no button".
 * Payloads come from OCR text, so they are validated again here. Calendar times are local to [zone].
 */
fun actionOf(type: String?, payload: String?, zone: ZoneId = ZoneId.of("Asia/Jakarta")): Action? {
    val p = payload?.trim().orEmpty()
    if (p.isEmpty()) return null
    return when (type) {
        "open_url" -> if (HTTP_URL.matches(p)) Action.OpenUrl(p) else null
        "track_parcel" -> if (RESI.matches(p)) Action.TrackParcel(p, "https://www.google.com/search?q=" + encode("cek resi $p")) else null
        "copy_text" -> if (p.length <= 200) Action.CopyText(p) else null
        "open_maps" -> if (p.length <= 200) Action.OpenMaps(p) else null
        "whatsapp" -> waNumber(p)?.let { Action.WhatsApp(it) }
        "call" -> phoneNumber(p)?.let { Action.Call(it) }
        "search_product" -> {
            val bar = p.indexOf('|')
            val market = if (bar < 0) "" else p.substring(0, bar).trim().lowercase()
            val name = (if (bar < 0) p else p.substring(bar + 1)).trim().take(100)
            if (name.isEmpty()) null else Action.SearchProduct(if (market in MARKETPLACES) market else "other", name)
        }
        "add_calendar" -> {
            val parts = p.split("|", limit = 2)
            if (parts.size != 2 || parts[1].isBlank()) return null
            try {
                val begin = LocalDateTime.parse(parts[0].trim()).atZone(zone).toInstant().toEpochMilli()
                Action.AddCalendar(parts[1].trim(), begin)
            } catch (e: DateTimeParseException) {
                null
            }
        }
        else -> null
    }
}
