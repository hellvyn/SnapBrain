package com.snapbrain.app

import android.content.Context
import android.provider.Settings
import com.snapbrain.app.data.AppDatabase
import com.snapbrain.app.data.ImageStore
import com.snapbrain.app.data.ItemRepository
import com.snapbrain.app.process.ExtractClient
import com.snapbrain.app.process.OcrEngine
import com.snapbrain.core.deviceIdOf

class AppContainer(context: Context) {
    private val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

    val repository = ItemRepository(
        dao = AppDatabase.create(context).itemDao(),
        images = ImageStore(context),
        ocr = OcrEngine(context),
        client = ExtractClient(deviceIdOf(androidId), BuildConfig.API_BASE_URL),
    )
}
