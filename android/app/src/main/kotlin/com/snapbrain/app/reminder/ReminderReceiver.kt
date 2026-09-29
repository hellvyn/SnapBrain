package com.snapbrain.app.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.snapbrain.app.SnapBrainApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The notification's "Selesai" button: checks the row (which also cancels its other slot) and clears the alert. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return
        val id = intent.getLongExtra(ROW_ID, -1)
        val repository = (context.applicationContext as SnapBrainApp).container.repository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.setChecked(listOf(id), true)
                context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_TAG, id.toInt())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.snapbrain.app.REMINDER_DONE"
        const val ROW_ID = "row_id"
    }
}
