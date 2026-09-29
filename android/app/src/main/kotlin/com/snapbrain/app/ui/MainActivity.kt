package com.snapbrain.app.ui

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.snapbrain.app.SnapBrainApp
import com.snapbrain.app.process.ProcessWorker
import com.snapbrain.core.IS_PRO

/** Spec §8 bottom navigation; Belanja joins in Task 5. */
private enum class Tab(val label: String, val icon: ImageVector) {
    INBOX("Inbox", SnapIcons.Inbox),
    TODO("To-do", SnapIcons.Task),
}

class MainActivity : ComponentActivity() {
    /** Item to open, set by a reminder notification (cold start or [onNewIntent]). */
    private val openRequest = mutableStateOf<String?>(null)
    private val notificationsAllowed = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as SnapBrainApp).container.repository
        val freshStart = savedInstanceState == null
        if (freshStart) openRequest.value = intent.getStringExtra(EXTRA_OPEN_ITEM)
        val fromNotification = openRequest.value != null
        setContent {
            SnapBrainTheme {
                CompositionLocalProvider(LocalNotificationsAllowed provides notificationsAllowed.value) {
                    // Recovery path for items stranded by a dismissed share sheet; skipped on rotation.
                    if (freshStart) {
                        LaunchedEffect(Unit) {
                            repository.requeueQuotaBlocked()
                            ProcessWorker.enqueue(applicationContext)
                        }
                    }
                    // Spec §6.5: splash on a launcher cold start only, not when a reminder opens the app.
                    var showSplash by rememberSaveable { mutableStateOf(freshStart && !fromNotification) }
                    var openId by rememberSaveable { mutableStateOf<String?>(null) }
                    var tab by rememberSaveable { mutableStateOf(Tab.INBOX) }
                    var query by rememberSaveable { mutableStateOf("") }
                    var category by rememberSaveable { mutableStateOf<String?>(null) }
                    val listState = rememberLazyListState()
                    // Measured once; kept here so the Inbox does not draw a frame under the header after returning from Detail.
                    val headerHeightPx = remember { mutableIntStateOf(0) }
                    val request = openRequest.value
                    LaunchedEffect(request) {
                        if (request != null) {
                            openId = request
                            openRequest.value = null
                        }
                    }
                    val remindersOn by repository.remindersEnabled.collectAsState()
                    val hasDue by remember { repository.observeHasDue() }.collectAsState(initial = false)
                    var bannerDismissed by rememberSaveable { mutableStateOf(false) }
                    val showBanner = !notificationsAllowed.value && remindersOn && hasDue && !bannerDismissed
                    val id = openId
                    when {
                        showSplash -> SplashScreen(onDone = { showSplash = false })
                        id != null -> DetailScreen(id, repository, onBack = { openId = null })
                        else -> {
                            BackHandler(enabled = tab != Tab.INBOX) { tab = Tab.INBOX }
                            Scaffold(
                                bottomBar = {
                                    Column {
                                        if (showBanner) NotificationBanner(onDismiss = { bannerDismissed = true })
                                        if (IS_PRO) NavBar(tab, onSelect = { tab = it })
                                    }
                                },
                            ) { padding ->
                                // Each tab has its own Scaffold; consuming the insets here keeps them from padding twice.
                                Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                                    when (tab) {
                                        Tab.INBOX -> InboxScreen(
                                            repository,
                                            query, { query = it },
                                            category, { category = it },
                                            listState,
                                            headerHeightPx,
                                            onOpen = { openId = it },
                                        )
                                        Tab.TODO -> TodoScreen(repository, onOpen = { openId = it })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_ITEM)?.let { openRequest.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Covers both the Android 13+ permission and notifications switched off in system settings.
        notificationsAllowed.value = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    companion object {
        const val EXTRA_OPEN_ITEM = "open_item_id"
    }
}

@Composable
private fun NavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
