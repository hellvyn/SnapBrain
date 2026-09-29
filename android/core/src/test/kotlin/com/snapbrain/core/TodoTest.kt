package com.snapbrain.core

import com.snapbrain.core.TodoBucket.HARI_INI
import com.snapbrain.core.TodoBucket.MINGGU_INI
import com.snapbrain.core.TodoBucket.NANTI
import com.snapbrain.core.TodoBucket.TANPA
import com.snapbrain.core.TodoBucket.TERLAMBAT
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TodoTest {
    private val now = LocalDateTime.of(2026, 9, 29, 12, 0) // Selasa

    @Test
    fun bucketsByDue() {
        assertEquals(TERLAMBAT, todoBucket("2026-09-28", now))
        assertEquals(TERLAMBAT, todoBucket("2026-09-29T09:00", now))
        assertEquals(HARI_INI, todoBucket("2026-09-29", now)) // a date-only due lasts all day
        assertEquals(HARI_INI, todoBucket("2026-09-29T23:59", now))
        assertEquals(MINGGU_INI, todoBucket("2026-09-30", now))
        assertEquals(MINGGU_INI, todoBucket("2026-10-05", now)) // today + 6
        assertEquals(NANTI, todoBucket("2026-10-06", now))
        assertEquals(TANPA, todoBucket(null, now))
        assertEquals(TANPA, todoBucket("", now))
        assertEquals(TANPA, todoBucket("besok", now))
    }

    private data class Row(val text: String, val due: String?, val steps: String? = null)

    @Test
    fun groupsBucketsThenStepsThenUndated() {
        val rows = listOf(
            Row("laptop", "2026-10-02"),
            Row("haluskan bumbu", null, "Pepes Ayam"),
            Row("bayar listrik", "2026-09-28"),
            Row("catat", null),
            Row("kukus", null, "Pepes Ayam"),
            Row("laporan", "2026-09-29T23:59"),
            Row("isi formulir", "2026-10-01"),
            Row("rendam beras", "2026-09-30", "Nasi Liwet"), // a dated step goes to its bucket
        )
        val groups = todoGroups(rows, now, due = { it.due }, stepsTitle = { it.steps })
        assertEquals(listOf("Terlambat", "Hari ini", "Minggu ini", "Pepes Ayam", "Tanpa tenggat"), groups.map { it.title })
        assertEquals(listOf("rendam beras", "isi formulir", "laptop"), groups[2].rows.map { it.text })
        assertEquals(listOf("haluskan bumbu", "kukus"), groups[3].rows.map { it.text })
        assertEquals(null, groups[3].bucket)
        assertEquals(listOf("catat"), groups[4].rows.map { it.text })
    }

    @Test
    fun emptyInputHasNoGroups() {
        assertEquals(emptyList(), todoGroups(emptyList<Row>(), now, { it.due }, { it.steps }))
    }
}
