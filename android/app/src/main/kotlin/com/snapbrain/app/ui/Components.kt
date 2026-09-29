package com.snapbrain.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.snapbrain.app.data.ItemEntity
import com.snapbrain.core.ItemStatus
import com.snapbrain.core.actionOf
import com.snapbrain.core.categoryLabel
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun statusText(item: ItemEntity): String? = when (item.status) {
    ItemStatus.UNPROCESSED.name -> "⏳ Menunggu internet"
    ItemStatus.QUOTA_BLOCKED.name -> "⛔ Kuota habis"
    ItemStatus.FAILED.name -> "⚠️ Gagal, coba lagi"
    else -> null
}

fun dateText(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag("id")).format(Date(millis))

@Composable
fun SmartCard(item: ItemEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.padding(12.dp)) {
            AsyncImage(
                model = File(item.imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.15f), BlendMode.Darken),
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${categoryLabel(item.category)} · ${dateText(item.createdAt)}", style = MaterialTheme.typography.labelSmall)
                Text(
                    item.title ?: "Screenshot tersimpan",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                statusText(item)?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                actionOf(item.actionType, item.actionPayload)?.let { action ->
                    OutlinedButton(onClick = { context.perform(action) }) { Text(action.label) }
                }
            }
        }
    }
}
