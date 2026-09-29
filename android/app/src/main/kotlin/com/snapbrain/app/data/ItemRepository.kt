package com.snapbrain.app.data

import android.net.Uri
import com.snapbrain.app.process.ExtractClient
import com.snapbrain.app.process.OcrEngine
import com.snapbrain.core.ExtractJson
import com.snapbrain.core.ExtractOutcome
import com.snapbrain.core.ItemStatus
import com.snapbrain.core.needsAi
import com.snapbrain.core.normalized
import com.snapbrain.core.statusAfter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ItemRepository(
    private val dao: ItemDao,
    private val images: ImageStore,
    private val ocr: OcrEngine,
    private val client: ExtractClient,
) {
    fun observe(query: String, category: String?) = dao.observe(query.trim(), category)
    fun observe(id: String) = dao.observeById(id)

    /** Saves the screenshot locally before any network call, so nothing is lost offline. */
    suspend fun capture(uri: Uri): ItemEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val file = images.save(uri, id)
        val now = System.currentTimeMillis()
        val item = try {
            val text = ocr.read(file)
            if (needsAi(text)) {
                ItemEntity(id, now, file.path, text, ItemStatus.UNPROCESSED.name)
            } else {
                ItemEntity(id, now, file.path, text, ItemStatus.DONE.name, category = "unclassified")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // OCR failed (not "no text"): keep the item UNPROCESSED with empty text so process() re-runs OCR.
            ItemEntity(id, now, file.path, "", ItemStatus.UNPROCESSED.name)
        }
        dao.insert(item)
        item
    }

    suspend fun process(item: ItemEntity): ItemEntity {
        var current = item
        if (item.ocrText.isEmpty()) {
            val text = try {
                withContext(Dispatchers.IO) { ocr.read(File(item.imagePath)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return retryLater(item)
            }
            current = item.copy(ocrText = text)
            if (!needsAi(text)) {
                return current.copy(status = ItemStatus.DONE.name, category = "unclassified").also { dao.update(it) }
            }
        }
        val updated = when (val outcome = client.extract(current.id, current.ocrText)) {
            is ExtractOutcome.Success -> {
                val d = outcome.response.data.normalized()
                current.copy(
                    status = ItemStatus.DONE.name,
                    category = d.category,
                    title = d.title,
                    extractedInfo = ExtractJson.encodeInfo(d.extractedInfo),
                    actionType = d.actionType,
                    actionPayload = d.actionPayload,
                    tasks = ExtractJson.encodeTasks(d.tasks),
                    tasksTotal = outcome.response.tasksTotal,
                )
            }
            ExtractOutcome.Retryable -> return retryLater(current)
            else -> current.copy(status = statusAfter(outcome, current.attempts).name)
        }
        dao.update(updated)
        return updated
    }

    private suspend fun retryLater(item: ItemEntity): ItemEntity {
        val attempts = item.attempts + 1
        return item.copy(status = statusAfter(ExtractOutcome.Retryable, attempts).name, attempts = attempts)
            .also { dao.update(it) }
    }

    suspend fun processPending() {
        dao.withStatus(ItemStatus.UNPROCESSED.name).forEach {
            try {
                process(it)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One bad item must not strand the rest of the queue.
                retryLater(it)
            }
        }
    }

    suspend fun hasPending(): Boolean = dao.countWithStatus(ItemStatus.UNPROCESSED.name) > 0

    /** Quota-blocked items get another try on app start; the server answers resource-exhausted cheaply. */
    suspend fun requeueQuotaBlocked() {
        dao.withStatus(ItemStatus.QUOTA_BLOCKED.name).forEach { dao.update(it.copy(status = ItemStatus.UNPROCESSED.name)) }
    }

    suspend fun retry(id: String) {
        dao.get(id)?.let { dao.update(it.copy(status = ItemStatus.UNPROCESSED.name, attempts = 0)) }
    }

    suspend fun discard(id: String) {
        dao.get(id)?.let {
            images.delete(it.imagePath)
            dao.delete(id)
        }
    }

    suspend fun toggleTask(id: String, taskId: Int) {
        val item = dao.get(id) ?: return
        val tasks = ExtractJson.decodeTasks(item.tasks).map {
            if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it
        }
        dao.update(item.copy(tasks = ExtractJson.encodeTasks(tasks)))
    }
}
