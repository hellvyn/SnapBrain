package com.snapbrain.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import com.snapbrain.core.Action

fun Context.perform(action: Action) {
    val intent = when (action) {
        is Action.OpenUrl -> Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
        is Action.TrackParcel -> Intent(Intent.ACTION_VIEW, Uri.parse(action.searchUrl))
        is Action.AddCalendar -> Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, action.title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.beginMillis)
        is Action.OpenMaps -> Intent(Intent.ACTION_VIEW, Uri.parse(action.geoUri))
        is Action.WhatsApp -> Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
        is Action.Call -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${action.number}"))
        is Action.SearchProduct -> Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
        is Action.CopyText -> {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SnapBrain", action.text))
            Toast.makeText(this, "Disalin", Toast.LENGTH_SHORT).show()
            return
        }
    }
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "Tidak ada aplikasi untuk membuka ini", Toast.LENGTH_SHORT).show()
    }
}
