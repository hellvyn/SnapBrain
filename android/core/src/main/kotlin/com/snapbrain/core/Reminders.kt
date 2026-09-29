package com.snapbrain.core

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

enum class ReminderSlot { DAY_BEFORE, SAME_DAY, HOUR_BEFORE }

data class ReminderTime(val slot: ReminderSlot, val atMillis: Long)

private val MORNING = LocalTime.of(8, 0)

/**
 * Spec §7: a date-only due reminds at 08:00 the day before and 08:00 on the day; a due with a time reminds
 * one hour before. Times already past are dropped, so saving an old screenshot never fires a late alert.
 */
fun reminderTimes(due: String?, now: ZonedDateTime): List<ReminderTime> {
    val (date, time) = parseDue(due) ?: return emptyList()
    val nowMillis = now.toInstant().toEpochMilli()
    // A stored due like "+999999999-12-31T09:00" overflows the date math; such a row simply never reminds.
    val times = try {
        val local = if (time == null) {
            listOf(ReminderSlot.DAY_BEFORE to date.minusDays(1).atTime(MORNING), ReminderSlot.SAME_DAY to date.atTime(MORNING))
        } else {
            listOf(ReminderSlot.HOUR_BEFORE to LocalDateTime.of(date, time).minusHours(1))
        }
        local.map { (slot, at) -> ReminderTime(slot, at.atZone(now.zone).toInstant().toEpochMilli()) }
    } catch (e: DateTimeException) {
        emptyList()
    } catch (e: ArithmeticException) {
        emptyList()
    }
    return times.filter { it.atMillis > nowMillis }
}

/** Notification title: "⏰ Bayar listrik — besok", "⏰ Kumpul laporan — jam 23:59". */
fun reminderTitle(text: String, due: String?, now: LocalDateTime): String {
    val (date, time) = parseDue(due) ?: return "⏰ $text"
    val today = now.toLocalDate()
    val whenText = when {
        time != null -> "jam %02d:%02d".format(time.hour, time.minute)
        date == today -> "hari ini"
        date == today.plusDays(1) -> "besok"
        else -> dueLabel(due, now)?.text.orEmpty()
    }
    return "⏰ $text — $whenText"
}
