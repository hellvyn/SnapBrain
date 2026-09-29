package com.snapbrain.core

import java.time.LocalDateTime

enum class TodoBucket(val label: String) {
    TERLAMBAT("Terlambat"),
    HARI_INI("Hari ini"),
    MINGGU_INI("Minggu ini"),
    NANTI("Nanti"),
    TANPA("Tanpa tenggat"),
}

/** "Minggu ini" runs through today + 6. */
fun todoBucket(due: String?, now: LocalDateTime): TodoBucket {
    val (date, time) = parseDue(due) ?: return TodoBucket.TANPA
    val today = now.toLocalDate()
    return when {
        isOverdue(date, time, now) -> TodoBucket.TERLAMBAT
        date == today -> TodoBucket.HARI_INI
        !date.isAfter(today.plusDays(6)) -> TodoBucket.MINGGU_INI
        else -> TodoBucket.NANTI
    }
}

/** [bucket] is null for a group of undated steps from one screenshot. */
data class TodoGroup<T>(val title: String, val bucket: TodoBucket?, val rows: List<T>)

/**
 * Spec §8: dated rows go to their bucket (sorted by due), undated steps group under their screenshot
 * ([stepsTitle] non-null), and everything else lands in "Tanpa tenggat" at the end.
 */
fun <T> todoGroups(rows: List<T>, now: LocalDateTime, due: (T) -> String?, stepsTitle: (T) -> String?): List<TodoGroup<T>> {
    val byBucket = rows.groupBy { todoBucket(due(it), now) }
    val dated = listOf(TodoBucket.TERLAMBAT, TodoBucket.HARI_INI, TodoBucket.MINGGU_INI, TodoBucket.NANTI).mapNotNull { b ->
        byBucket[b]?.let { TodoGroup(b.label, b, it.sortedBy(due)) }
    }
    val undated = byBucket[TodoBucket.TANPA].orEmpty()
    val (steps, rest) = undated.partition { stepsTitle(it) != null }
    val stepGroups = steps.groupBy { stepsTitle(it)!! }.map { (title, r) -> TodoGroup(title, null, r) }
    val restGroup = if (rest.isEmpty()) emptyList() else listOf(TodoGroup(TodoBucket.TANPA.label, TodoBucket.TANPA, rest))
    return dated + stepGroups + restGroup
}
