package com.snapbrain.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snapbrain.app.data.ItemRepository
import com.snapbrain.app.data.SourcedRow
import com.snapbrain.core.TodoBucket
import com.snapbrain.core.TodoGroup
import com.snapbrain.core.dueLabel
import com.snapbrain.core.todoGroups
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Spec §8 To-do: every open task across screenshots, grouped by due. */
@Composable
fun TodoScreen(repository: ItemRepository, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val rows: List<SourcedRow>? by remember { repository.observeTodo() }.collectAsState(initial = null)
    val remindersOn by repository.remindersEnabled.collectAsState()
    var showDone by rememberSaveable { mutableStateOf(false) }
    val all = rows ?: return
    val now = remember(all) { LocalDateTime.now() }
    val visible = if (showDone) all else all.filter { !it.row.checked }
    val groups = todoGroups(
        visible,
        now,
        due = { it.row.due },
        stepsTitle = { if (it.row.kind == "steps") it.itemTitle ?: "Langkah" else null },
    )
    Scaffold { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${all.count { !it.row.checked }} tugas aktif",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("To-do", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    }
                    TextButton(onClick = { showDone = !showDone }) {
                        Text(if (showDone) "Sembunyikan selesai" else "Tampilkan selesai", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (groups.isEmpty()) {
                item {
                    Text(
                        "Belum ada tugas. Tekan tombol di kartu screenshot (misalnya \"Kerjakan\" atau \"Masak sekarang\"), " +
                            "atau simpan screenshot yang punya tenggat.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 12.dp, end = 12.dp),
                    )
                }
            }
            groups.forEach { group ->
                item { GroupHeader(group) }
                item {
                    SnapCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            group.rows.forEach { source ->
                                TodoRow(
                                    source,
                                    now,
                                    remindersOn,
                                    onToggle = { scope.launch { repository.toggleListItem(source.row.id) } },
                                    onBell = { scope.launch { repository.toggleRemind(source.row.id) } },
                                    onOpen = { onOpen(source.row.itemId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: TodoGroup<SourcedRow>) {
    val color = when (group.bucket) {
        TodoBucket.TERLAMBAT -> MaterialTheme.colorScheme.error
        TodoBucket.HARI_INI -> soonColor
        TodoBucket.MINGGU_INI -> MaterialTheme.colorScheme.secondary
        null -> categoryStyle("reference").tint
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // TalkBack reads the title and count as one heading-like item.
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(
            group.title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(vertical = 4.dp),
        )
        Text(
            if (group.bucket == null) "${group.rows.size} langkah" else "${group.rows.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun TodoRow(source: SourcedRow, now: LocalDateTime, remindersOn: Boolean, onToggle: () -> Unit, onBell: () -> Unit, onOpen: () -> Unit) {
    val row = source.row
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = row.checked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.semantics { contentDescription = row.text },
            colors = snapCheckboxColors(),
        )
        Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 10.dp)) {
            Text(
                row.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (row.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (row.checked) TextDecoration.LineThrough else null,
            )
            Text(
                source.itemTitle ?: "Screenshot",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        dueLabel(row.due, now)?.let { due ->
            Text(
                due.text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (due.overdue) MaterialTheme.colorScheme.error else soonColor,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (!row.due.isNullOrBlank()) ReminderBell(row.remind, remindersOn, row.text, onToggle = onBell)
    }
}
