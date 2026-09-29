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
    suspend fun capture(uri: Uri): ItemEntity {
        val id = UUID.randomUUID().toString()
        val file = images.save(uri, id)
        val text = try {
            ocr.read(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
        val item = if (needsAi(text)) {
            ItemEntity(id, System.currentTimeMillis(), file.path, text, ItemStatus.UNPROCESSED.name)
        } else {
            ItemEntity(id, System.currentTimeMillis(), file.path, text, ItemStatus.DONE.name, category = "unclassified")
        }
        dao.insert(item)
        return item
    }

    suspend fun process(item: ItemEntity): ItemEntity {
        val updated = when (val outcome = client.extract(item.id, item.ocrText)) {
            is ExtractOutcome.Success -> {
                val d = outcome.response.data.normalized()
                item.copy(
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
            ExtractOutcome.Retryable -> {
                val attempts = item.attempts + 1
                item.copy(status = statusAfter(outcome, attempts).name, attempts = attempts)
            }
            else -> item.copy(status = statusAfter(outcome, item.attempts).name)
        }
        dao.update(updated)
        return updated
    }

    suspend fun processPending() {
        dao.withStatus(ItemStatus.UNPROCESSED.name).forEach { process(it) }
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
