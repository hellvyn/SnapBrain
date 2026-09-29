package com.snapbrain.app.reminder

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.snapbrain.app.data.ListItemEntity
import com.snapbrain.core.ReminderSlot
import com.snapbrain.core.reminderTimes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Spec §7: one WorkManager job per list row and slot, so each is replaced or cancelled on its own.
 * WorkManager keeps them across reboots. Delivery may slip a few minutes in Doze; exact alarms are not used.
 */
class ReminderScheduler(context: Context, private val prefs: SharedPreferences) {
    private val appContext = context.applicationContext
    private val work by lazy { WorkManager.getInstance(appContext) }
    private val _enabled = MutableStateFlow(prefs.getBoolean(ENABLED, true))

    /** The global switch in the Inbox menu. */
    val enabled: StateFlow<Boolean> = _enabled

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(ENABLED, on).apply()
        _enabled.value = on
        if (!on) work.cancelAllWorkByTag(TAG)
    }

    /** Brings the jobs for [row] in line with its current state: checked, bell off or switch off means none. */
    fun sync(row: ListItemEntity) {
        cancel(row.id)
        if (!_enabled.value || !row.remind || row.checked) return
        val now = ZonedDateTime.now()
        val nowMillis = now.toInstant().toEpochMilli()
        reminderTimes(row.due, now).forEach { time ->
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(time.atMillis - nowMillis, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(ReminderWorker.ROW_ID to row.id))
                .addTag(TAG)
                .build()
            work.enqueueUniqueWork(name(row.id, time.slot), ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun cancel(rowId: Long) {
        ReminderSlot.entries.forEach { work.cancelUniqueWork(name(rowId, it)) }
    }

    private fun name(rowId: Long, slot: ReminderSlot) = "reminder-$rowId-$slot"

    private companion object {
        const val TAG = "reminder"
        const val ENABLED = "reminders_enabled"
    }
}
