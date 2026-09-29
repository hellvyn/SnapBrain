package com.snapbrain.app

import android.content.Context
import android.provider.Settings
import com.snapbrain.app.data.AppDatabase
import com.snapbrain.app.data.ImageStore
import com.snapbrain.app.data.ItemRepository
import com.snapbrain.app.process.ExtractClient
import com.snapbrain.app.process.OcrEngine
import com.snapbrain.app.reminder.ReminderScheduler
import com.snapbrain.core.deviceIdOf

class AppContainer(context: Context) {
    private val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    private val prefs = context.getSharedPreferences("snapbrain", Context.MODE_PRIVATE)

    val repository = ItemRepository(
        db = AppDatabase.create(context),
        images = ImageStore(context),
        ocr = OcrEngine(context),
        client = ExtractClient(deviceIdOf(androidId), BuildConfig.API_BASE_URL),
        prefs = prefs,
        reminders = ReminderScheduler(context, prefs),
    )
}
