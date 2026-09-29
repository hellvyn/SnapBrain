package com.snapbrain.core

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

sealed interface Action {
    val label: String

    data class OpenUrl(val url: String) : Action {
        override val label get() = "🔗 Buka Link"
    }

    data class TrackParcel(val resi: String, val searchUrl: String) : Action {
        override val label get() = "🚚 Lacak Paket"
    }

    data class AddCalendar(val title: String, val beginMillis: Long) : Action {
        override val label get() = "📅 Tambah ke Kalender"
    }

    data class CopyText(val text: String) : Action {
        override val label get() = "📋 Salin"
    }
}

private val HTTP_URL = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

/** Maps the stored action_type/action_payload to something the UI can run; null means "no button". */
fun actionOf(type: String?, payload: String?, zone: ZoneId = ZoneId.of("Asia/Jakarta")): Action? {
    val p = payload?.trim().orEmpty()
    if (p.isEmpty()) return null
    return when (type) {
        "open_url" -> if (HTTP_URL.matches(p)) Action.OpenUrl(p) else null
        "track_parcel" -> Action.TrackParcel(
            p,
            "https://www.google.com/search?q=" + URLEncoder.encode("cek resi $p", StandardCharsets.UTF_8),
        )
        "copy_text" -> Action.CopyText(p)
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
