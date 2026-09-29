package com.snapbrain.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.snapbrain.app.data.ItemEntity
import com.snapbrain.app.data.ItemRepository
import com.snapbrain.app.data.ListItemEntity
import com.snapbrain.app.data.actionList
import com.snapbrain.app.process.ProcessWorker
import com.snapbrain.core.Action
import com.snapbrain.core.ExtractJson
import com.snapbrain.core.IS_PRO
import com.snapbrain.core.ItemStatus
import com.snapbrain.core.ShareList
import com.snapbrain.core.TaskItem
import com.snapbrain.core.activatesBelanja
import com.snapbrain.core.activationLabel
import com.snapbrain.core.dueLabel
import com.snapbrain.core.shareText
import com.snapbrain.core.todoEligible
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime

private const val COLLAPSED_ROWS = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(id: String, repository: ItemRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember(id) { repository.observe(id).map<ItemEntity?, DetailState> { DetailState.Loaded(it) } }
        .collectAsState(initial = DetailState.Loading)
    val rows by remember(id) { repository.observeLists(id) }.collectAsState(initial = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    var showImage by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    val loaded = state as? DetailState.Loaded ?: return
    val current = loaded.item
    if (current == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val style = categoryStyle(current.category)
    val title = current.title ?: "Screenshot tersimpan"
    val info = ExtractJson.decodeInfo(current.extractedInfo)
    val lists = rows.groupBy { it.listIndex }.toSortedMap().values.toList()
    val legacyTasks = if (rows.isEmpty()) ExtractJson.decodeTasks(current.tasks) else emptyList()
    val actions = current.actionList()
    val now = LocalDateTime.now()
    // Spec §6.3/S7: the button only shows when its rows have somewhere to land.
    val toBelanja = activatesBelanja(current.activation) && rows.any { it.role == "belanja" }
    val toTodo = rows.any { todoEligible(it.role, it.kind) }
    val activation = if (IS_PRO) activationLabel(current.activation, current.active, toBelanja, toTodo) else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(SnapIcons.Back, contentDescription = "Kembali") } },
                actions = {
                    IconButton(onClick = { context.shareText(shareText(title, info, lists.map { it.toShareList() })) }) {
                        Icon(SnapIcons.Share, contentDescription = "Bagikan")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(SnapIcons.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
        if (showImage) ImageDialog(current.imagePath, onClose = { showImage = false })
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HeroCard(
                    current, style, title, activation,
                    onActivate = { scope.launch { repository.setActive(id, !current.active) } },
                    onRetry = { scope.launch { repository.retry(id); ProcessWorker.enqueue(context.applicationContext) } },
                )
            }
            item {
                // Spec S15: the screenshot stays hidden until asked for.
                OutlinedButton(onClick = { showImage = true }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(SnapIcons.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Lihat screenshot asli", fontWeight = FontWeight.Bold)
                }
            }
            if (actions.isNotEmpty()) item { ActionTiles(actions) { context.perform(it) } }
            if (info.isNotEmpty()) item { InfoCard(info) }
            items(lists.size) { i ->
                val list = lists[i]
                val text = shareText(title, emptyMap(), listOf(list.toShareList()))
                ListCard(
                    list,
                    now,
                    onToggle = { row -> scope.launch { repository.toggleListItem(row.id) } },
                    onCopy = { context.copyText(text) },
                    onShare = { context.shareText(text) },
                )
            }
            if (legacyTasks.isNotEmpty()) item { LegacyTasks(legacyTasks) { taskId -> scope.launch { repository.toggleTask(id, taskId) } } }
        }
    }
}

private fun List<ListItemEntity>.toShareList() = ShareList(first().listTitle, first().kind == "steps", map { it.text to it.checked })

@Composable
private fun HeroCard(item: ItemEntity, style: CategoryStyle, title: String, activation: String?, onActivate: () -> Unit, onRetry: () -> Unit) {
    SnapCard(Modifier.fillMaxWidth(), color = style.tile) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(style.icon, contentDescription = null, tint = style.tint)
                }
                Spacer(Modifier.width(12.dp))
                Text(style.name.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = style.tint)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            statusText(item)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = statusColor(item)) }
            if (item.status == ItemStatus.FAILED.name) {
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = strongButtonColor, contentColor = Color.White)) { Text("Coba lagi") }
            }
            if (activation != null) {
                val shape = RoundedCornerShape(16.dp)
                val modifier = Modifier.fillMaxWidth().height(52.dp)
                if (item.active) {
                    OutlinedButton(
                        onClick = onActivate,
                        modifier = modifier,
                        shape = shape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    ) { Text(activation, fontWeight = FontWeight.ExtraBold) }
                } else {
                    Button(
                        onClick = onActivate,
                        modifier = modifier,
                        shape = shape,
                        colors = ButtonDefaults.buttonColors(containerColor = strongButtonColor, contentColor = Color.White),
                    ) { Text(activation, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}

@Composable
private fun ActionTiles(actions: List<Action>, onClick: (Action) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.forEach { action ->
            SnapCard(Modifier.weight(1f).height(76.dp), onClick = { onClick(action) }) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(actionIcon(action), contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(6.dp))
                    Text(action.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun actionIcon(action: Action): ImageVector = when (action) {
    is Action.OpenUrl -> SnapIcons.Link
    is Action.TrackParcel -> SnapIcons.Truck
    is Action.AddCalendar -> SnapIcons.Event
    is Action.CopyText -> SnapIcons.Copy
    is Action.OpenMaps -> SnapIcons.Pin
    is Action.WhatsApp -> SnapIcons.Chat
    is Action.Call -> SnapIcons.Phone
    is Action.SearchProduct -> SnapIcons.Shopping
}

@Composable
private fun InfoCard(info: Map<String, String>) {
    SnapCard(Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                info.forEach { (key, value) ->
                    Row {
                        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
                        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ListCard(
    rows: List<ListItemEntity>,
    now: LocalDateTime,
    onToggle: (ListItemEntity) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val steps = rows.first().kind == "steps"
    val done = rows.count { it.checked }
    SnapCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rows.first().listTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("$done/${rows.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(SnapIcons.More, contentDescription = "Menu daftar") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Salin daftar") }, onClick = { menu = false; onCopy() })
                        DropdownMenuItem(text = { Text("Bagikan daftar") }, onClick = { menu = false; onShare() })
                    }
                }
            }
            LinearProgressIndicator(
                progress = { done.toFloat() / rows.size },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
            val shown = if (expanded || rows.size <= COLLAPSED_ROWS) rows else rows.take(COLLAPSED_ROWS)
            shown.forEachIndexed { index, row -> ListRow(row, if (steps) index + 1 else null, now) { onToggle(row) } }
            if (!expanded && rows.size > COLLAPSED_ROWS) {
                TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Tampilkan semua (${rows.size})", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ListRow(row: ListItemEntity, number: Int?, now: LocalDateTime, onToggle: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().toggleable(value = row.checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = row.checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.secondary, checkmarkColor = MaterialTheme.colorScheme.onSecondary),
        )
        Text(
            (number?.let { "$it. " } ?: "") + row.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (row.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (row.checked) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        dueLabel(row.due, now)?.let { due -> Chip(due.text, if (due.overdue) MaterialTheme.colorScheme.error else soonColor) }
        if (row.minutes > 0) {
            Chip("${row.minutes} mnt", MaterialTheme.colorScheme.secondary, SnapIcons.Timer) { context.startTimer(row.minutes, row.text) }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val background = color.copy(alpha = 0.06f)
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = background, modifier = Modifier.padding(start = 6.dp).minimumInteractiveComponentSize(), content = content)
    } else {
        Surface(shape = shape, color = background, modifier = Modifier.padding(start = 6.dp), content = content)
    }
}

/** Items stored before v2 still carry their tasks as JSON. */
@Composable
private fun LegacyTasks(tasks: List<TaskItem>, onToggle: (Int) -> Unit) {
    SnapCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Tugas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            tasks.forEach { task ->
                Row(
                    Modifier.toggleable(value = task.isCompleted, role = Role.Checkbox, onValueChange = { onToggle(task.id) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = task.isCompleted, onCheckedChange = null)
                    Text(task.description)
                }
            }
        }
    }
}

private sealed interface DetailState {
    data object Loading : DetailState
    data class Loaded(val item: ItemEntity?) : DetailState
}

@Composable
private fun ImageDialog(path: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ZoomableImage(path, Modifier.fillMaxSize())
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(SnapIcons.Close, contentDescription = "Tutup", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ZoomableImage(path: String, modifier: Modifier) {
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
        contentDescription = "Screenshot asli",
        contentScale = ContentScale.Fit,
        modifier = modifier.clipToBounds().onSizeChanged { size = it }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            .transformable(state, canPan = { scale > 1f }),
    )
}
