package com.snapbrain.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.snapbrain.app.reminder.CHANNEL_ID

class SnapBrainApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installAppCheck()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Pengingat", NotificationManager.IMPORTANCE_DEFAULT))
        container = AppContainer(this)
    }
}
