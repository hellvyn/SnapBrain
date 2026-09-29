package com.snapbrain.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.rememberLazyListState
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
        val freshStart = savedInstanceState == null
        setContent {
            SnapBrainTheme {
                // Recovery path for items stranded by a dismissed share sheet; skipped on rotation.
                if (freshStart) {
                    LaunchedEffect(Unit) {
                        repository.requeueQuotaBlocked()
                        ProcessWorker.enqueue(applicationContext)
                    }
                }
                var showSplash by rememberSaveable { mutableStateOf(freshStart) }
                var openId by rememberSaveable { mutableStateOf<String?>(null) }
                var query by rememberSaveable { mutableStateOf("") }
                var category by rememberSaveable { mutableStateOf<String?>(null) }
                val listState = rememberLazyListState()
                val id = openId
                when {
                    showSplash -> SplashScreen(onDone = { showSplash = false })
                    id == null -> InboxScreen(
                        repository,
                        query, { query = it },
                        category, { category = it },
                        listState,
                        onOpen = { openId = it },
                    )
                    else -> DetailScreen(id, repository, onBack = { openId = null })
                }
            }
        }
    }
}
