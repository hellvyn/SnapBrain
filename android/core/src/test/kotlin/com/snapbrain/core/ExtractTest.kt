package com.snapbrain.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractTest {
    private val sample = """
        {"data":{"category":"shopping","title":"Paket Shopee dikirim",
         "extracted_info":{"No. Resi":"JP123"},"action_type":"track_parcel",
         "action_payload":"JP123","tasks":[{"id":1,"description":"Cek paket","is_completed":false}]},
         "tasks_total":3,"quota":{"used":2,"limit":15},"extra_field":true}
    """.trimIndent()

    @Test
    fun parsesServerResponseAndIgnoresUnknownFields() {
        val r = ExtractJson.parse(sample)
        assertEquals("shopping", r.data.category)
        assertEquals(mapOf("No. Resi" to "JP123"), r.data.extractedInfo)
        assertEquals(TaskItem(1, "Cek paket", false), r.data.tasks.single())
        assertEquals(3, r.tasksTotal)
        assertEquals(Quota(2, 15), r.quota)
    }

    @Test
    fun normalizesUnknownCategoryAndAction() {
        val d = ExtractData(category = "gossip", title = "x", actionType = "teleport", actionPayload = "p").normalized()
        assertEquals("unclassified", d.category)
        assertEquals("none", d.actionType)
        assertEquals("", d.actionPayload)
    }

    @Test
    fun downgradesNonHttpOpenUrl() {
        val bad = ExtractData(category = "reference", title = "x", actionType = "open_url", actionPayload = "javascript:alert(1)").normalized()
        assertEquals("none", bad.actionType)
        val ok = ExtractData(category = "reference", title = "x", actionType = "open_url", actionPayload = "https://a.id").normalized()
        assertEquals("open_url", ok.actionType)
    }

    @Test
    fun dropsBlankTasks() {
        val d = ExtractData(category = "task", title = "x", tasks = listOf(TaskItem(1, " "), TaskItem(2, "Kerjakan"))).normalized()
        assertEquals(listOf("Kerjakan"), d.tasks.map { it.description })
    }

    @Test
    fun roundTripsInfoAndTasksForStorage() {
        val info = mapOf("Total" to "Rp 50.000")
        assertEquals(info, ExtractJson.decodeInfo(ExtractJson.encodeInfo(info)))
        val tasks = listOf(TaskItem(1, "a", true))
        assertEquals(tasks, ExtractJson.decodeTasks(ExtractJson.encodeTasks(tasks)))
        assertTrue(ExtractJson.decodeInfo(null).isEmpty())
        assertTrue(ExtractJson.decodeTasks("not json").isEmpty())
    }

    @Test
    fun labelsCategoriesInIndonesian() {
        assertEquals("🛒 Belanja", categoryLabel("shopping"))
        assertEquals("📄 Lainnya", categoryLabel(null))
    }
}
