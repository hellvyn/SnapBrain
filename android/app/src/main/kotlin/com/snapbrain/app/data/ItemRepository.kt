package com.snapbrain.app.data

import android.content.SharedPreferences
import android.net.Uri
import androidx.room.withTransaction
import com.snapbrain.app.process.ExtractClient
import com.snapbrain.app.process.OcrEngine
import com.snapbrain.core.ExtractData
import com.snapbrain.core.ExtractJson
import com.snapbrain.core.ExtractOutcome
import com.snapbrain.core.ExtractResponse
import com.snapbrain.core.ItemStatus
import com.snapbrain.core.Quota
import com.snapbrain.core.needsAi
import com.snapbrain.core.normalized
import com.snapbrain.core.statusAfter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ItemRepository(
    private val db: AppDatabase,
    private val images: ImageStore,
    private val ocr: OcrEngine,
    private val client: ExtractClient,
    private val prefs: SharedPreferences,
) {
    private val dao = db.itemDao()
    private val lists = db.listItemDao()
    private val _quota = MutableStateFlow(savedQuota())

    /** Last quota the server reported; null until the first successful extract. */
    val quota: StateFlow<Quota?> = _quota

    fun observe(query: String, category: String?) = dao.observe(query.trim(), category)
    fun observe(id: String) = dao.observeById(id)
    fun observeLists(id: String) = lists.observe(id)
    fun observeProgress() = lists.observeProgress()

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
            is ExtractOutcome.Success -> return saveResult(current, outcome.response)
            ExtractOutcome.Retryable -> return retryLater(current)
            else -> current.copy(status = statusAfter(outcome, current.attempts).name)
        }
        dao.update(updated)
        return updated
    }

    private suspend fun saveResult(item: ItemEntity, response: ExtractResponse): ItemEntity {
        val d = response.data.normalized()
        val done = item.copy(
            status = ItemStatus.DONE.name,
            category = d.category,
            title = d.title,
            extractedInfo = ExtractJson.encodeInfo(d.info),
            actionType = null,
            actionPayload = null,
            tasks = null,
            tasksTotal = 0,
            actions = ExtractJson.encodeActions(d.actions),
            activation = d.activation,
        )
        db.withTransaction {
            dao.update(done)
            lists.deleteFor(done.id)
            lists.insertAll(listRowsOf(done.id, d))
        }
        prefs.edit().putInt(QUOTA_USED, response.quota.used).putInt(QUOTA_LIMIT, response.quota.limit).apply()
        _quota.value = response.quota
        return done
    }

    private fun savedQuota(): Quota? =
        if (prefs.contains(QUOTA_LIMIT)) Quota(prefs.getInt(QUOTA_USED, 0), prefs.getInt(QUOTA_LIMIT, 0)) else null

    suspend fun toggleListItem(id: Long) = lists.toggle(id, System.currentTimeMillis())

    private companion object {
        const val QUOTA_USED = "quota_used"
        const val QUOTA_LIMIT = "quota_limit"
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
            lists.deleteFor(id)
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

internal fun listRowsOf(itemId: String, d: ExtractData): List<ListItemEntity> =
    d.lists.flatMapIndexed { li, list ->
        list.items.mapIndexed { pi, it ->
            ListItemEntity(
                itemId = itemId,
                listIndex = li,
                listTitle = list.title.ifBlank { "Daftar" },
                kind = list.kind,
                role = list.role,
                position = pi,
                text = it.text,
                due = it.due.ifBlank { null },
                minutes = it.minutes,
                price = it.price,
                size = it.size.ifBlank { null },
            )
        }
    }
