package com.snapbrain.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.snapbrain.app.SnapBrainApp
import com.snapbrain.app.process.ProcessWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as SnapBrainApp).container.repository
        setContent {
            SnapBrainTheme {
                LaunchedEffect(Unit) {
                    repository.requeueQuotaBlocked()
                    ProcessWorker.enqueue(applicationContext)
                }
                var openId by rememberSaveable { mutableStateOf<String?>(null) }
                val id = openId
                if (id == null) {
                    InboxScreen(repository, onOpen = { openId = it })
                } else {
                    DetailScreen(id, repository, onBack = { openId = null })
                }
            }
        }
    }
}
