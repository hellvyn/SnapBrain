package com.snapbrain.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.snapbrain.app.data.ItemEntity
import com.snapbrain.app.data.ItemProgress
import com.snapbrain.core.ItemStatus
import com.snapbrain.core.dueLabel
import java.text.DateFormat
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale

fun statusText(item: ItemEntity): String? = when (item.status) {
    ItemStatus.UNPROCESSED.name -> "Menunggu internet"
    ItemStatus.QUOTA_BLOCKED.name -> "Kuota habis"
    ItemStatus.FAILED.name -> "Gagal, coba lagi"
    else -> null
}

fun dateText(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag("id")).format(Date(millis))

@Composable
fun CategoryTile(style: CategoryStyle, size: Dp) {
    Box(Modifier.size(size).background(style.tile, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        Icon(style.icon, contentDescription = null, tint = style.tint, modifier = Modifier.size(size / 2))
    }
}

/** Inbox card: category icon instead of the screenshot (spec S15), next due, and checklist progress. */
@Composable
fun SmartCard(item: ItemEntity, progress: ItemProgress?, now: LocalDateTime, onClick: () -> Unit) {
    val style = categoryStyle(item.category)
    val status = statusText(item)
    val due = dueLabel(progress?.nextDue, now)
    SnapCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryTile(style, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(style.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = style.tint, modifier = Modifier.weight(1f))
                    when {
                        status != null -> Text(status, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        due != null -> Text(
                            due.text,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (due.overdue) MaterialTheme.colorScheme.error else soonColor,
                        )
                        else -> Text(dateText(item.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    item.title ?: "Screenshot tersimpan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null && progress.total > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { progress.done.toFloat() / progress.total },
                            modifier = Modifier.weight(1f).height(6.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("${progress.done}/${progress.total}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
