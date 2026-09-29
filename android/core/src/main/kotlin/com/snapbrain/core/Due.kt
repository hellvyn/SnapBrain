package com.snapbrain.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

data class DueLabel(val text: String, val overdue: Boolean)

private val DAYS = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

/**
 * "Hari ini", "Besok 23:59", "Kam, 1 Okt". [due] is local time as stored from the server.
 * A date-only due lasts all day. Null for blank or malformed values.
 */
fun dueLabel(due: String?, now: LocalDateTime): DueLabel? {
    val s = due?.trim().orEmpty()
    if (s.isEmpty()) return null
    val parsed: Pair<LocalDate, LocalTime?> = try {
        if (s.length > 10) LocalDateTime.parse(s).let { it.toLocalDate() to it.toLocalTime() } else LocalDate.parse(s) to null
    } catch (e: DateTimeParseException) {
        return null
    }
    val (date, time) = parsed
    val today = now.toLocalDate()
    val day = when (date) {
        today -> "Hari ini"
        today.plusDays(1) -> "Besok"
        else -> "${DAYS[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"
    }
    val text = if (time == null) day else "%s %02d:%02d".format(day, time.hour, time.minute)
    val overdue = if (time == null) date.isBefore(today) else LocalDateTime.of(date, time).isBefore(now)
    return DueLabel(text, overdue)
}
