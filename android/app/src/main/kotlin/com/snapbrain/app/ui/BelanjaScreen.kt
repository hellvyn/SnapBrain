package com.snapbrain.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.snapbrain.app.data.ItemEntity
import com.snapbrain.app.data.ItemRepository
import com.snapbrain.app.data.SourcedRow
import com.snapbrain.core.BelanjaTotal
import com.snapbrain.core.IngredientGroup
import com.snapbrain.core.ItemStatus
import com.snapbrain.core.ShareList
import com.snapbrain.core.belanjaTotal
import com.snapbrain.core.budgetOf
import com.snapbrain.core.compareTable
import com.snapbrain.core.groupByIngredient
import com.snapbrain.core.monthStartMillis
import com.snapbrain.core.rupiah
import com.snapbrain.core.shareText
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** Spec §8 Belanja: shopping rows from every activated screenshot, with total, budget and price comparison. */
@Composable
fun BelanjaScreen(repository: ItemRepository, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows: List<SourcedRow>? by remember { repository.observeBelanja() }.collectAsState(initial = null)
    val monthStart = remember { monthStartMillis(ZonedDateTime.now()) }
    val spent by remember(monthStart) { repository.observeSpent(monthStart) }.collectAsState(initial = 0L)
    val budget by repository.budget.collectAsState()
    var byIngredient by rememberSaveable { mutableStateOf(true) }
    var hideChecked by rememberSaveable { mutableStateOf(false) }
    var editBudget by rememberSaveable { mutableStateOf(false) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val all = rows ?: return
    val visible = if (hideChecked) all.filter { !it.row.checked } else all
    val total = belanjaTotal(all.filter { !it.row.checked }.map { it.row.price })
    val toggle: (List<SourcedRow>) -> Unit = { group ->
        val check = !group.all { it.row.checked }
        scope.launch { repository.setChecked(group.map { it.row.id }, check) }
    }

    if (editBudget) BudgetDialog(budget, onSave = { repository.setBudget(it); editBudget = false }, onDismiss = { editBudget = false })
    if (comparing) CompareDialog(repository, onClose = { comparing = false })

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
                            "${all.size} barang dari ${all.distinctBy { it.row.itemId }.size} screenshot",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Belanja", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    }
                    if (all.isNotEmpty()) {
                        IconButton(onClick = { context.shareText(belanjaShareText(all)) }) { Icon(SnapIcons.Share, contentDescription = "Bagikan daftar") }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(SnapIcons.More, contentDescription = "Menu belanja") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Bandingkan harga") }, onClick = { menu = false; comparing = true })
                            DropdownMenuItem(text = { Text("Atur budget bulanan") }, onClick = { menu = false; editBudget = true })
                        }
                    }
                }
            }
            item { SummaryCard(total, budget, spent, onSetBudget = { editBudget = true }) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = byIngredient, onClick = { byIngredient = true }, label = { Text("Per bahan", fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(50))
                    FilterChip(selected = !byIngredient, onClick = { byIngredient = false }, label = { Text("Per asal", fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(50))
                    FilterChip(selected = hideChecked, onClick = { hideChecked = !hideChecked }, label = { Text("Sembunyikan yang dicentang", fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(50))
                }
            }
            if (all.isEmpty()) {
                item {
                    Text(
                        "Belanja masih kosong. Buka screenshot resep atau produk, lalu tekan \"Masak sekarang\" atau \"Mau beli\".",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 12.dp, end = 12.dp),
                    )
                }
            } else if (byIngredient) {
                item {
                    SnapCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            groupByIngredient(visible) { it.row.text }.forEach { group -> IngredientRow(group, onToggle = { toggle(group.rows) }) }
                        }
                    }
                }
            } else {
                items(visible.groupBy { it.row.itemId }.toList()) { (itemId, source) ->
                    SourceCard(source.first().itemTitle ?: "Screenshot", source, onOpen = { onOpen(itemId) }, onToggle = { toggle(listOf(it)) })
                }
            }
            if (all.any { it.row.checked }) {
                item {
                    Button(
                        onClick = { scope.launch { repository.finishShopping() } },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = strongButtonColor, contentColor = Color.White),
                    ) { Text("Selesai belanja", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}

/** One list per screenshot, with ☐/☑, for WhatsApp and friends. */
private fun belanjaShareText(rows: List<SourcedRow>): String = shareText(
    "Daftar belanja",
    emptyMap(),
    rows.groupBy { it.row.itemId }.values.map { r -> ShareList(r.first().itemTitle ?: "Screenshot", false, r.map { it.row.text to it.row.checked }) },
)

@Composable
private fun SummaryCard(total: BelanjaTotal, budget: Long, spent: Long, onSetBudget: () -> Unit) {
    SnapCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Total incaran", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${total.count} barang · ${rupiah(total.sum)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            if (total.noPrice > 0) {
                Text("${total.noPrice} tanpa harga", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (budget > 0) {
                val b = budgetOf(budget, spent, total.sum)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SummaryLine("Budget bulan ini", rupiah(budget))
                SummaryLine("Terbeli", rupiah(b.spent))
                SummaryLine("Sisa", rupiah(b.left))
                if (b.over) {
                    Text(
                        "Incaran lebih ${rupiah(b.planned - b.left)} dari sisa budget",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onSetBudget) { Text("Ubah budget", fontWeight = FontWeight.Bold) }
            } else {
                TextButton(onClick = onSetBudget) { Text("Atur budget bulanan", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** One checkbox for every row of the same ingredient; the amounts stay listed apart (spec S12). */
@Composable
private fun IngredientRow(group: IngredientGroup<SourcedRow>, onToggle: () -> Unit) {
    val checked = group.rows.all { it.row.checked }
    val price = group.rows.filter { !it.row.checked }.sumOf { it.row.price }
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }), verticalAlignment = Alignment.Top) {
        Checkbox(checked = checked, onCheckedChange = null, colors = snapCheckboxColors(), modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                group.name.replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            group.rows.forEach { source ->
                Text(
                    "${source.row.text} · ${source.itemTitle ?: "Screenshot"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (price > 0) {
            Text(rupiah(price), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, end = 8.dp))
        }
    }
}

@Composable
private fun SourceCard(title: String, rows: List<SourcedRow>, onOpen: () -> Unit, onToggle: (SourcedRow) -> Unit) {
    SnapCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 8.dp),
            )
            rows.forEach { source ->
                val row = source.row
                Row(
                    Modifier.fillMaxWidth().toggleable(value = row.checked, role = Role.Checkbox, onValueChange = { onToggle(source) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = row.checked, onCheckedChange = null, colors = snapCheckboxColors(), modifier = Modifier.padding(12.dp))
                    Text(
                        row.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (row.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (row.checked) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    )
                    if (row.price > 0) {
                        Text(rupiah(row.price), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetDialog(current: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (current > 0) current.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget belanja bulanan") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter(Char::isDigit).take(12) },
                prefix = { Text("Rp ") },
                supportingText = { Text("Kosongkan untuk mematikan budget.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.toLongOrNull() ?: 0L) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Spec §8 Bandingkan: pick 2–3 shopping screenshots, then see them side by side. */
@Composable
private fun CompareDialog(repository: ItemRepository, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val items: List<ItemEntity>? by remember { repository.observe("", "shopping") }.collectAsState(initial = null)
    var selected by remember { mutableStateOf(listOf<String>()) }
    var table by remember { mutableStateOf<Pair<List<String>, List<Pair<String, List<String>>>>?>(null) }
    val back: () -> Unit = { if (table != null) { table = null } else { onClose() } }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(SnapIcons.Back, contentDescription = "Kembali") }
                    Text("Bandingkan harga", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                }
                val shown = table
                if (shown == null) {
                    val choices = items.orEmpty().filter { it.status == ItemStatus.DONE.name }
                    Text(
                        if (choices.size < 2) "Butuh minimal 2 screenshot belanja (halaman produk atau keranjang)." else "Pilih 2–3 screenshot belanja.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(Modifier.weight(1f)) {
                        items(choices, key = { it.id }) { item ->
                            val on = item.id in selected
                            val enabled = on || selected.size < 3
                            Row(
                                Modifier.fillMaxWidth().toggleable(value = on, enabled = enabled, role = Role.Checkbox, onValueChange = {
                                    selected = if (on) selected - item.id else selected + item.id
                                }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = on, onCheckedChange = null, enabled = enabled, colors = snapCheckboxColors(), modifier = Modifier.padding(12.dp))
                                Text(item.title ?: "Screenshot", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val columns = repository.compareColumns(selected)
                                table = columns.map { it.title } to compareTable(columns)
                            }
                        },
                        enabled = selected.size in 2..3,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = strongButtonColor, contentColor = Color.White),
                    ) { Text("Bandingkan (${selected.size})", fontWeight = FontWeight.ExtraBold) }
                } else {
                    CompareTable(shown.first, shown.second)
                }
            }
        }
    }
}

@Composable
private fun CompareTable(titles: List<String>, rows: List<Pair<String, List<String>>>) {
    Column(Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
        TableRow("", titles, header = true)
        rows.forEach { (label, values) -> TableRow(label, values, header = false) }
    }
}

@Composable
private fun TableRow(label: String, values: List<String>, header: Boolean) {
    Row(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(104.dp))
        values.forEach { value ->
            Text(
                value,
                style = if (header) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.ExtraBold else FontWeight.SemiBold,
                modifier = Modifier.width(148.dp).padding(start = 8.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
