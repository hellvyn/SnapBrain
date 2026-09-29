package com.snapbrain.app

import android.app.Application

class SnapBrainApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installAppCheck()
        container = AppContainer(this)
    }
}
