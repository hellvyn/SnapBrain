package com.snapbrain.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snapbrain.app.data.ItemRepository

private val FILTERS = listOf(
    "Semua" to null,
    "Tugas" to "task",
    "Keuangan" to "finance",
    "Event" to "event",
    "Belanja" to "shopping",
    "Referensi" to "reference",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(repository: ItemRepository, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val items by remember(query, category) { repository.observe(query, category) }.collectAsState(initial = emptyList())

    Scaffold(topBar = { TopAppBar(title = { Text("SnapBrain") }) }) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari resep, resi, catatan...") },
                leadingIcon = { Text("🔍") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(FILTERS) { (label, key) ->
                    FilterChip(selected = category == key, onClick = { category = key }, label = { Text(label) })
                }
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank() && category == null) {
                            "Belum ada screenshot. Coba share screenshot ke aplikasi ini!"
                        } else {
                            "Tidak ada hasil."
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn {
                    items(items, key = { it.id }) { item -> SmartCard(item, onClick = { onOpen(item.id) }) }
                }
            }
        }
    }
}
