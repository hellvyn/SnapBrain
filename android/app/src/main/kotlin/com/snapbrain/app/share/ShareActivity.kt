package com.snapbrain.app.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.snapbrain.app.SnapBrainApp
import com.snapbrain.app.data.ItemEntity
import com.snapbrain.app.data.ItemRepository
import com.snapbrain.app.process.ProcessWorker
import com.snapbrain.app.ui.SnapBrainTheme
import com.snapbrain.app.ui.perform
import com.snapbrain.core.ExtractJson
import com.snapbrain.core.ItemStatus
import com.snapbrain.core.actionOf
import com.snapbrain.core.canDeleteOriginal
import com.snapbrain.core.categoryLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val SYNC_TIMEOUT_MS = 6_000L

private sealed interface ShareState {
    data object Reading : ShareState
    data object Analyzing : ShareState
    data class Result(val item: ItemEntity) : ShareState
    data object Queued : ShareState
    data object QuotaBlocked : ShareState
    data object Failed : ShareState
    data object Unreadable : ShareState
}

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (uri == null) {
            finish()
            return
        }
        val repository = (application as SnapBrainApp).container.repository
        setContent { SnapBrainTheme { ShareSheet(uri, repository, onClose = ::finish) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(uri: Uri, repository: ItemRepository, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ShareState>(ShareState.Reading) }
    var itemId by remember { mutableStateOf<String?>(null) }
    val deleteOriginal = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { onClose() }

    LaunchedEffect(uri) {
        val item = try {
            // Dismissing mid-capture must not leave an orphan JPEG or a row without itemId.
            withContext(NonCancellable) { repository.capture(uri) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Foreign content uris may throw SecurityException/IllegalArgumentException, not only IOException.
            state = ShareState.Unreadable
            return@LaunchedEffect
        }
        itemId = item.id
        if (!isActive) {
            // Sheet dismissed during "Mengekstrak teks...": hand the item to the worker.
            if (item.status == ItemStatus.UNPROCESSED.name) ProcessWorker.enqueue(context.applicationContext)
            return@LaunchedEffect
        }
        if (item.status == ItemStatus.DONE.name) {
            state = ShareState.Result(item)
            return@LaunchedEffect
        }
        state = ShareState.Analyzing
        val processed = withTimeoutOrNull(SYNC_TIMEOUT_MS) { repository.process(item) }
        state = when (processed?.status) {
            ItemStatus.DONE.name -> ShareState.Result(processed)
            ItemStatus.QUOTA_BLOCKED.name -> ShareState.QuotaBlocked
            ItemStatus.FAILED.name -> ShareState.Failed
            else -> ShareState.Queued
        }
        // Enqueue now so process death while the sheet is open does not strand the item.
        if (state is ShareState.Queued) ProcessWorker.enqueue(context.applicationContext)
    }
    // Leaving before the AI answered (timeout, offline, sheet closed): the worker picks the item up.
    DisposableEffect(Unit) {
        onDispose {
            val done = state is ShareState.Result || state is ShareState.QuotaBlocked || state is ShareState.Failed || state is ShareState.Unreadable
            if (itemId != null && !done) ProcessWorker.enqueue(context.applicationContext)
        }
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                ShareState.Reading -> Loading("Mengekstrak teks...")
                ShareState.Analyzing -> Loading("AI sedang menganalisis konteks...")
                ShareState.Unreadable -> {
                    Text("Gambar tidak bisa dibaca.")
                    Button(onClick = onClose) { Text("Tutup") }
                }
                ShareState.Failed -> {
                    Text("Gagal diproses. Buka SnapBrain untuk coba lagi.")
                    Button(onClick = onClose) { Text("Tutup") }
                }
                ShareState.Queued -> {
                    Text("Tersimpan. Akan diproses otomatis saat online.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { scope.launch { itemId?.let { repository.discard(it) }; onClose() } }) { Text("Batal") }
                        Button(onClick = onClose) { Text("Tutup") }
                    }
                }
                ShareState.QuotaBlocked -> {
                    Text("Kuota AI bulan ini habis. Screenshot tetap tersimpan dan akan diproses saat kuota tersedia.")
                    Button(onClick = onClose) { Text("Tutup") }
                }
                is ShareState.Result -> {
                    val item = s.item
                    Text(categoryLabel(item.category), style = MaterialTheme.typography.labelMedium)
                    Text(item.title ?: "Screenshot tersimpan", style = MaterialTheme.typography.titleLarge)
                    ExtractJson.decodeInfo(item.extractedInfo).entries.take(2).forEach { (k, v) -> Text("$k: $v") }
                    actionOf(item.actionType, item.actionPayload)?.let { action ->
                        OutlinedButton(onClick = { context.perform(action) }) { Text(action.label) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { scope.launch { repository.discard(item.id); onClose() } }) { Text("Batal") }
                        Button(onClick = onClose) { Text("Simpan") }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && canDeleteOriginal(Build.VERSION.SDK_INT, uri.authority)) {
                            OutlinedButton(onClick = {
                                try {
                                    val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                                    deleteOriginal.launch(IntentSenderRequest.Builder(request.intentSender).build())
                                } catch (e: IllegalArgumentException) {
                                    onClose()
                                } catch (e: SecurityException) {
                                    onClose()
                                }
                            }) { Text("Simpan & Hapus Asli") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text(text)
    }
}
