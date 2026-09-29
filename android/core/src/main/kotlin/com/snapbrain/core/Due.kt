package com.snapbrain.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

data class DueLabel(val text: String, val overdue: Boolean)

private val DAYS = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

/** Local date plus optional time, as stored from the server; null for blank or malformed values. */
internal fun parseDue(due: String?): Pair<LocalDate, LocalTime?>? {
    val s = due?.trim().orEmpty()
    if (s.isEmpty()) return null
    return try {
        if (s.length > 10) LocalDateTime.parse(s).let { it.toLocalDate() to it.toLocalTime() } else LocalDate.parse(s) to null
    } catch (e: DateTimeParseException) {
        null
    }
}

/** A date-only due lasts all day. */
internal fun isOverdue(date: LocalDate, time: LocalTime?, now: LocalDateTime): Boolean =
    if (time == null) date.isBefore(now.toLocalDate()) else LocalDateTime.of(date, time).isBefore(now)

/** "Hari ini", "Besok 23:59", "Kam, 1 Okt". Null for blank or malformed values. */
fun dueLabel(due: String?, now: LocalDateTime): DueLabel? {
    val (date, time) = parseDue(due) ?: return null
    val today = now.toLocalDate()
    val day = when (date) {
        today -> "Hari ini"
        today.plusDays(1) -> "Besok"
        else -> "${DAYS[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"
    }
    val text = if (time == null) day else "%s %02d:%02d".format(day, time.hour, time.minute)
    return DueLabel(text, isOverdue(date, time, now))
}
