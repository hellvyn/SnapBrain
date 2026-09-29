package com.snapbrain.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snapbrain.app.data.ItemEntity
import com.snapbrain.app.data.ItemRepository
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class Filter(val label: String, val key: String?)

private val FILTERS = listOf(
    Filter("Semua", null),
    Filter("Tugas", "task"),
    Filter("Keuangan", "finance"),
    Filter("Belanja", "shopping"),
    Filter("Event", "event"),
    Filter("Referensi", "reference"),
    Filter("Lainnya", "unclassified"),
)

private val HEADER_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("id"))

@Composable
fun InboxScreen(
    repository: ItemRepository,
    query: String,
    onQueryChange: (String) -> Unit,
    category: String?,
    onCategoryChange: (String?) -> Unit,
    listState: LazyListState,
    headerHeightState: MutableIntState,
    onOpen: (String) -> Unit,
) {
    val items: List<ItemEntity>? by remember(query, category) { repository.observe(query, category) }.collectAsState(initial = null)
    val progress by remember { repository.observeProgress().map { rows -> rows.associateBy { it.itemId } } }.collectAsState(initial = emptyMap())
    val quota by repository.quota.collectAsState()
    // Search and chips collapse as an overlay while scrolling down; the list viewport never resizes (spec S16).
    var headerHeightPx by headerHeightState
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                headerOffsetPx = (headerOffsetPx + available.y).coerceIn(-headerHeightPx.toFloat(), 0f)
                return Offset.Zero
            }
        }
    }
    val headerHeight = with(LocalDensity.current) { headerHeightPx.toDp() }
    val now = remember(items, progress) { LocalDateTime.now() }

    Scaffold { padding ->
        Column(Modifier.padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        LocalDate.now().format(HEADER_DATE),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Screenshot kamu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                }
                quota?.let { q ->
                    Text(
                        "Kuota ${q.used}/${q.limit}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                if (headerOffsetPx < -headerHeightPx / 2f) {
                    IconButton(onClick = { headerOffsetPx = 0f }) {
                        Icon(SnapIcons.Search, contentDescription = "Cari")
                    }
                }
            }
            Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(connection)) {
                val list = items
                if (list != null && list.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(top = headerHeight).padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isBlank() && category == null) "Belum ada screenshot. Coba share screenshot ke aplikasi ini!" else "Tidak ada hasil.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (list != null) {
                    LazyColumn(state = listState, contentPadding = PaddingValues(top = headerHeight, bottom = 16.dp)) {
                        items(list, key = { it.id }) { item -> SmartCard(item, progress[item.id], now, onClick = { onOpen(item.id) }) }
                    }
                }
                Column(
                    Modifier
                        .onSizeChanged { headerHeightPx = it.height }
                        .offset { IntOffset(0, headerOffsetPx.roundToInt()) }
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Cari screenshot, bahan, tugas…") },
                        leadingIcon = { Icon(SnapIcons.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(FILTERS) { f ->
                            FilterChip(
                                selected = category == f.key,
                                onClick = { onCategoryChange(f.key) },
                                label = { Text(f.label, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(categoryIcon(f.key), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
