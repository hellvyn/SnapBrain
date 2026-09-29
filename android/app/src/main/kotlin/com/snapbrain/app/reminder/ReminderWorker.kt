package com.snapbrain.app.reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.snapbrain.app.R
import com.snapbrain.app.SnapBrainApp
import com.snapbrain.app.data.SourcedRow
import com.snapbrain.app.ui.MainActivity
import com.snapbrain.core.reminderTitle
import java.time.LocalDateTime

internal const val CHANNEL_ID = "reminders"
internal const val NOTIFICATION_TAG = "reminder"

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as SnapBrainApp).container.repository
        val source = repository.reminderRow(inputData.getLong(ROW_ID, -1)) ?: return Result.success()
        // Re-check at fire time: the row may have been checked or muted after scheduling.
        if (source.row.checked || !source.row.remind || !repository.remindersEnabled.value) return Result.success()
        if (!applicationContext.remindersCanPost()) return Result.success()
        post(applicationContext, source)
        return Result.success()
    }

    companion object {
        const val ROW_ID = "row_id"
    }
}

/** False when notifications are off for the app (or the Android 13+ permission) or the "Pengingat" channel is blocked. */
fun Context.remindersCanPost(): Boolean {
    val manager = getSystemService(NotificationManager::class.java)
    return manager.areNotificationsEnabled() &&
        manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
}

/** Spec §7: "⏰ <teks> — besok", "dari: <judul>", tap opens Detail, "Selesai" checks the row. */
private fun post(context: Context, source: SourcedRow) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val row = source.row
    val code = row.id.toInt()
    val open = PendingIntent.getActivity(
        context,
        code,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_ITEM, row.itemId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val done = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DONE).putExtra(ReminderReceiver.ROW_ID, row.id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_snapbrain)
        .setContentTitle(reminderTitle(row.text, row.due, LocalDateTime.now()))
        .setContentText("dari: ${source.itemTitle ?: "Screenshot"}")
        .setContentIntent(open)
        .setAutoCancel(true)
        .addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_snapbrain), "Selesai", done).build())
        .build()
    try {
        manager.notify(NOTIFICATION_TAG, code, notification)
    } catch (e: SecurityException) {
        // Permission revoked between the check and the post; nothing to show.
    }
}
