package com.snapbrain.core

import com.snapbrain.core.ReminderSlot.DAY_BEFORE
import com.snapbrain.core.ReminderSlot.HOUR_BEFORE
import com.snapbrain.core.ReminderSlot.SAME_DAY
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RemindersTest {
    private val jakarta = ZoneId.of("Asia/Jakarta")
    private fun at(month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId = jakarta) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)
    private fun millis(z: ZonedDateTime) = z.toInstant().toEpochMilli()
    private val now = at(9, 29, 12, 0) // Selasa 12:00 WIB

    @Test
    fun dateOnlyRemindsTheDayBeforeAndOnTheDayAtEight() {
        assertEquals(
            listOf(ReminderTime(DAY_BEFORE, millis(at(10, 1, 8, 0))), ReminderTime(SAME_DAY, millis(at(10, 2, 8, 0)))),
            reminderTimes("2026-10-02", now),
        )
    }

    @Test
    fun timedRemindsOneHourBefore() {
        assertEquals(listOf(ReminderTime(HOUR_BEFORE, millis(at(9, 30, 22, 59)))), reminderTimes("2026-09-30T23:59", now))
    }

    @Test
    fun dropsPastTimes() {
        assertEquals(listOf(SAME_DAY), reminderTimes("2026-09-30", now).map { it.slot }) // today 08:00 already passed
        assertEquals(emptyList(), reminderTimes("2026-09-29", now)) // due today, both 08:00 slots passed
        assertEquals(emptyList(), reminderTimes("2026-09-29T12:30", now)) // due within the hour
        assertEquals(emptyList(), reminderTimes("2026-09-01", now))
        assertEquals(emptyList(), reminderTimes(null, now))
        assertEquals(emptyList(), reminderTimes("besok", now))
    }

    @Test
    fun extremeDatesNeverThrow() {
        assertEquals(emptyList(), reminderTimes("+999999999-12-31T09:00", now)) // epoch millis overflow
        assertEquals(emptyList(), reminderTimes("-999999999-01-01T00:30", now)) // minusHours below LocalDateTime.MIN
        assertEquals(emptyList(), reminderTimes("+999999999-12-31", now))
    }

    @Test
    fun usesTheDeviceZone() {
        val papua = ZoneId.of("Asia/Jayapura") // UTC+9, two hours ahead of Jakarta
        val times = reminderTimes("2026-10-02", at(9, 29, 12, 0, papua))
        assertEquals(millis(at(10, 2, 8, 0, papua)), times[1].atMillis)
        assertEquals(millis(at(10, 2, 6, 0)), times[1].atMillis)
    }

    @Test
    fun titlesSayWhen() {
        val local = now.toLocalDateTime()
        assertEquals("⏰ Bayar listrik — hari ini", reminderTitle("Bayar listrik", "2026-09-29", local))
        assertEquals("⏰ Bayar listrik — besok", reminderTitle("Bayar listrik", "2026-09-30", local))
        assertEquals("⏰ Kumpul laporan — jam 23:59", reminderTitle("Kumpul laporan", "2026-09-29T23:59", local))
        assertEquals("⏰ Daftar ulang — Jum, 2 Okt", reminderTitle("Daftar ulang", "2026-10-02", local))
        assertEquals("⏰ Catat", reminderTitle("Catat", null, local))
    }
}
