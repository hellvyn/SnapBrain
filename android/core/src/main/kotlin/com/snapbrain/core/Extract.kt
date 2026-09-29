package com.snapbrain.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** v1 task, still read from items stored before the v2 contract. */
@Serializable
data class TaskItem(
    val id: Int,
    val description: String,
    @SerialName("is_completed") val isCompleted: Boolean = false,
)

@Serializable
data class ListItemData(
    val text: String,
    val due: String = "",
    val minutes: Int = 0,
    val price: Long = 0,
    val size: String = "",
)

@Serializable
data class ItemList(
    val title: String = "",
    val kind: String = "checklist",
    val role: String = "lainnya",
    val items: List<ListItemData> = emptyList(),
)

@Serializable
data class ActionData(val type: String, val payload: String)

@Serializable
data class ExtractData(
    val category: String,
    val title: String,
    val info: Map<String, String> = emptyMap(),
    val lists: List<ItemList> = emptyList(),
    val actions: List<ActionData> = emptyList(),
    val activation: String = "none",
)

@Serializable
data class Quota(val used: Int, val limit: Int)

@Serializable
data class ExtractResponse(val data: ExtractData, val quota: Quota)

val CATEGORIES = listOf("task", "finance", "shopping", "event", "reference", "unclassified")
private val KINDS = setOf("checklist", "steps")
private val ROLES = setOf("belanja", "todo", "bawa", "lainnya")
private val ACTIVATIONS = setOf("masak", "beli", "kerjakan", "bayar", "ikut", "coba", "none")

object ExtractJson {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val infoSerializer = MapSerializer(String.serializer(), String.serializer())
    private val tasksSerializer = ListSerializer(TaskItem.serializer())
    private val actionsSerializer = ListSerializer(ActionData.serializer())

    fun parse(text: String): ExtractResponse = json.decodeFromString(ExtractResponse.serializer(), text)

    fun encodeInfo(info: Map<String, String>): String = json.encodeToString(infoSerializer, info)
    fun decodeInfo(text: String?): Map<String, String> =
        text?.let { runCatching { json.decodeFromString(infoSerializer, it) }.getOrNull() } ?: emptyMap()

    fun encodeTasks(tasks: List<TaskItem>): String = json.encodeToString(tasksSerializer, tasks)
    fun decodeTasks(text: String?): List<TaskItem> =
        text?.let { runCatching { json.decodeFromString(tasksSerializer, it) }.getOrNull() } ?: emptyList()

    fun encodeActions(actions: List<ActionData>): String = json.encodeToString(actionsSerializer, actions)
    fun decodeActions(text: String?): List<ActionData> =
        text?.let { runCatching { json.decodeFromString(actionsSerializer, it) }.getOrNull() } ?: emptyList()
}

/** Defense in depth: the server already validates, but the app must never store a list or action it cannot show. */
fun ExtractData.normalized(): ExtractData = copy(
    category = if (category in CATEGORIES) category else "unclassified",
    lists = lists.map { l ->
        l.copy(
            kind = if (l.kind in KINDS) l.kind else "checklist",
            role = if (l.role in ROLES) l.role else "lainnya",
            items = l.items.filter { it.text.isNotBlank() }.map { if (parseDue(it.due) == null) it.copy(due = "") else it },
        )
    }.filter { it.items.isNotEmpty() },
    actions = actions.filter { actionOf(it.type, it.payload) != null }.take(3),
    activation = if (activation in ACTIVATIONS) activation else "none",
)

fun categoryLabel(category: String?): String = when (category) {
    "task" -> "✅ Tugas"
    "finance" -> "💰 Keuangan"
    "shopping" -> "🛒 Belanja"
    "event" -> "📅 Event"
    "reference" -> "📚 Referensi"
    else -> "📄 Lainnya"
}
