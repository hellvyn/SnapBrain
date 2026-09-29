package com.snapbrain.app.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** False when the system blocks SnapBrain's notifications; bells then show off and lead to settings (spec §7). */
val LocalNotificationsAllowed = staticCompositionLocalOf { true }

/** Per-row 🔔 (spec §7). Off when notifications are blocked, the global switch is off, or the row is muted. */
@Composable
fun ReminderBell(remind: Boolean, globalOn: Boolean, label: String, onToggle: () -> Unit) {
    val context = LocalContext.current
    val allowed = LocalNotificationsAllowed.current
    val on = allowed && globalOn && remind
    IconButton(
        onClick = {
            when {
                !allowed -> context.openNotificationSettings()
                !globalOn -> Toast.makeText(context, "Pengingat sedang dimatikan. Nyalakan lewat menu ⋮ di Inbox.", Toast.LENGTH_LONG).show()
                else -> onToggle()
            }
        },
    ) {
        Icon(
            if (on) SnapIcons.Bell else SnapIcons.BellOff,
            contentDescription = (if (on) "Pengingat nyala: " else "Pengingat mati: ") + label,
            tint = if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Spec §7: asks for POST_NOTIFICATIONS with a short reason once there is something to remind about.
 * The first "Izinkan" shows the system dialog (Android 13+); after that, or on older Android, it opens settings.
 */
@Composable
fun NotificationBanner(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    SnapCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nyalakan notifikasi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                "Supaya SnapBrain bisa mengingatkan tenggat dari screenshot kamu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Nanti") }
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && !asked) {
                            asked = true
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.openNotificationSettings()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = strongButtonColor, contentColor = Color.White),
                ) { Text("Izinkan", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
