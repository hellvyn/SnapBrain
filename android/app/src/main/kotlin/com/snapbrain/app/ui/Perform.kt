package com.snapbrain.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import com.snapbrain.core.Action

fun Context.perform(action: Action) {
    when (action) {
        is Action.CopyText -> copyText(action.text)
        is Action.OpenUrl -> launch(view(action.url))
        is Action.TrackParcel -> launch(view(action.searchUrl))
        is Action.WhatsApp -> launch(view(action.url))
        is Action.SearchProduct -> launch(view(action.url))
        is Action.Call -> launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + action.number)))
        // Prefer a maps app; fall back to Google Maps on the web.
        is Action.OpenMaps -> if (!launch(view(action.geoUri), quiet = true)) launch(view(action.webUrl))
        is Action.AddCalendar -> launch(
            Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, action.title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.beginMillis),
        )
    }
}

fun Context.copyText(text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SnapBrain", text))
    Toast.makeText(this, "Disalin", Toast.LENGTH_SHORT).show()
}

fun Context.shareText(text: String) {
    launch(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

/** Opens the clock app's timer, filled in but not started (the user confirms). */
fun Context.startTimer(minutes: Int, label: String) {
    launch(
        Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, minutes.coerceIn(1, 1440) * 60)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label.take(60))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
    )
}

/** The system screen where the user can allow SnapBrain's notifications again. */
fun Context.openNotificationSettings() {
    launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
}

private fun view(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

/** Starts [intent]; returns false, telling the user unless [quiet], when no app can handle it. */
private fun Context.launch(intent: Intent, quiet: Boolean = false): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    if (!quiet) Toast.makeText(this, "Tidak ada aplikasi untuk membuka ini", Toast.LENGTH_SHORT).show()
    false
}
