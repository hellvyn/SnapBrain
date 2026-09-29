package com.snapbrain.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.snapbrain.app.data.actionList
import com.snapbrain.app.data.ItemEntity
import com.snapbrain.app.data.ItemRepository
import com.snapbrain.app.process.ProcessWorker
import com.snapbrain.core.ExtractJson
import com.snapbrain.core.ItemStatus
import com.snapbrain.core.categoryLabel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(id: String, repository: ItemRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember(id) { repository.observe(id).map<ItemEntity?, DetailState> { DetailState.Loaded(it) } }
        .collectAsState(initial = DetailState.Loading)
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    val loaded = state as? DetailState.Loaded ?: return
    val current = loaded.item
    if (current == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val tasks = ExtractJson.decodeTasks(current.tasks)
    val locked = current.tasksTotal - tasks.size
    val info = ExtractJson.decodeInfo(current.extractedInfo).toList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.title ?: "Detail") },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
                actions = {
                    TextButton(onClick = { confirmDelete = true }) { Text("Hapus") }
                },
            )
        },
    ) { padding ->
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Hapus screenshot ini?") },
                text = { Text("Screenshot dan hasilnya akan dihapus dari SnapBrain.") },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; scope.launch { repository.discard(id); onBack() } }) { Text("Hapus") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
            )
        }
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ZoomableImage(current.imagePath) }
            item {
                Text(categoryLabel(current.category), style = MaterialTheme.typography.labelMedium)
                Text(current.title ?: "Screenshot tersimpan", style = MaterialTheme.typography.headlineSmall)
                statusText(current)?.let { Text(it) }
                if (current.status == ItemStatus.FAILED.name) {
                    Button(onClick = {
                        scope.launch { repository.retry(id); ProcessWorker.enqueue(context.applicationContext) }
                    }) { Text("Coba lagi") }
                }
            }
            items(info.size) { index ->
                val (key, value) = info[index]
                Row(Modifier.fillMaxWidth()) {
                    Text(key, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.4f))
                    Text(value, modifier = Modifier.weight(0.6f))
                }
            }
            current.actionList().firstOrNull()?.let { action ->
                item { OutlinedButton(onClick = { context.perform(action) }) { Text(action.label) } }
            }
            if (tasks.isNotEmpty()) {
                item { Text("Tugas", style = MaterialTheme.typography.titleMedium) }
                items(tasks.size) { index ->
                    val task = tasks[index]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = task.isCompleted, onCheckedChange = { scope.launch { repository.toggleTask(id, task.id) } })
                        Text(task.description)
                    }
                }
            }
            if (locked > 0) item { LockedTasks(locked) }
        }
    }
}

/** Free tier placeholder; Plan 3 turns the CTA into the paywall. */
@Composable
private fun LockedTasks(count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count.coerceAtMost(3)) {
            Box(
                Modifier.fillMaxWidth().height(20.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
            )
        }
        Text("🔒 $count tugas lain — Buka AI Task Planner (Pro)", style = MaterialTheme.typography.labelLarge)
    }
}

private sealed interface DetailState {
    data object Loading : DetailState
    data class Loaded(val item: ItemEntity?) : DetailState
}

@Composable
private fun ZoomableImage(path: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        val maxX = (scale - 1f) * size.width / 2f
        val maxY = (scale - 1f) * size.height / 2f
        offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
    }
    // Stored copies are <= 2048px; decode at full size so zoomed text stays sharp.
    val model = ImageRequest.Builder(LocalContext.current).data(File(path)).size(Size.ORIGINAL).build()
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().height(360.dp).clipToBounds().onSizeChanged { size = it }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            .transformable(state, canPan = { scale > 1f }),
    )
}
