package com.snapbrain.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class TaskItem(
    val id: Int,
    val description: String,
    @SerialName("is_completed") val isCompleted: Boolean = false,
)

@Serializable
data class ExtractData(
    val category: String,
    val title: String,
    @SerialName("extracted_info") val extractedInfo: Map<String, String> = emptyMap(),
    @SerialName("action_type") val actionType: String = "none",
    @SerialName("action_payload") val actionPayload: String = "",
    val tasks: List<TaskItem> = emptyList(),
)

@Serializable
data class Quota(val used: Int, val limit: Int)

@Serializable
data class ExtractResponse(
    val data: ExtractData,
    @SerialName("tasks_total") val tasksTotal: Int = 0,
    val quota: Quota,
)

val CATEGORIES = listOf("task", "finance", "shopping", "event", "reference", "unclassified")
private val ACTIONS = setOf("track_parcel", "add_calendar", "copy_text", "open_url", "none")
private val HTTP_URL = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

object ExtractJson {
    private val json = Json { ignoreUnknownKeys = true }
    private val infoSerializer = MapSerializer(String.serializer(), String.serializer())
    private val tasksSerializer = ListSerializer(TaskItem.serializer())

    fun parse(text: String): ExtractResponse = json.decodeFromString(ExtractResponse.serializer(), text)

    fun encodeInfo(info: Map<String, String>): String = json.encodeToString(infoSerializer, info)
    fun decodeInfo(text: String?): Map<String, String> =
        text?.let { runCatching { json.decodeFromString(infoSerializer, it) }.getOrNull() } ?: emptyMap()

    fun encodeTasks(tasks: List<TaskItem>): String = json.encodeToString(tasksSerializer, tasks)
    fun decodeTasks(text: String?): List<TaskItem> =
        text?.let { runCatching { json.decodeFromString(tasksSerializer, it) }.getOrNull() } ?: emptyList()
}

/** Defense in depth: the server already validates, but the app must never render a broken action. */
fun ExtractData.normalized(): ExtractData {
    var action = if (actionType in ACTIONS) actionType else "none"
    var payload = actionPayload.trim()
    if (action == "open_url" && !HTTP_URL.matches(payload)) action = "none"
    if (action == "none") payload = ""
    return copy(
        category = if (category in CATEGORIES) category else "unclassified",
        actionType = action,
        actionPayload = payload,
        tasks = tasks.filter { it.description.isNotBlank() },
    )
}

fun categoryLabel(category: String?): String = when (category) {
    "task" -> "✅ Tugas"
    "finance" -> "💰 Keuangan"
    "shopping" -> "🛒 Belanja"
    "event" -> "📅 Event"
    "reference" -> "📚 Referensi"
    else -> "📄 Lainnya"
}
