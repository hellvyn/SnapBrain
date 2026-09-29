package com.snapbrain.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractTest {
    private val sample = """
        {"data":{"category":"reference","title":"Resep Pepes Ayam",
         "info":{"Porsi":"20 orang"},
         "lists":[{"title":"Bumbu","kind":"checklist","role":"belanja",
           "items":[{"text":"9 butir bawang merah","due":"","minutes":0,"price":0,"size":""}]}],
         "actions":[{"type":"open_url","payload":"https://cookpad.com/id/resep/1"}],
         "activation":"masak","extra_field":true},
         "quota":{"used":2,"limit":1000}}
    """.trimIndent()

    @Test
    fun parsesServerResponseAndIgnoresUnknownFields() {
        val r = ExtractJson.parse(sample)
        assertEquals("reference", r.data.category)
        assertEquals(mapOf("Porsi" to "20 orang"), r.data.info)
        assertEquals(ItemList("Bumbu", "checklist", "belanja", listOf(ListItemData("9 butir bawang merah"))), r.data.lists.single())
        assertEquals(ActionData("open_url", "https://cookpad.com/id/resep/1"), r.data.actions.single())
        assertEquals("masak", r.data.activation)
        assertEquals(Quota(2, 1000), r.quota)
    }

    @Test
    fun normalizesUnknownEnums() {
        val d = ExtractData(
            category = "gossip",
            title = "x",
            lists = listOf(ItemList("A", kind = "table", role = "hobi", items = listOf(ListItemData("a")))),
            activation = "dance",
        ).normalized()
        assertEquals("unclassified", d.category)
        assertEquals("checklist", d.lists.single().kind)
        assertEquals("lainnya", d.lists.single().role)
        assertEquals("none", d.activation)
    }

    @Test
    fun dropsBlankItemsEmptyListsAndBadActions() {
        val d = ExtractData(
            category = "task",
            title = "x",
            lists = listOf(
                ItemList("A", items = listOf(ListItemData(" "), ListItemData("Kerjakan"))),
                ItemList("B", items = listOf(ListItemData(""))),
            ),
            actions = listOf(
                ActionData("open_url", "javascript:alert(1)"),
                ActionData("copy_text", "1"),
                ActionData("copy_text", "2"),
                ActionData("copy_text", "3"),
                ActionData("copy_text", "4"),
            ),
        ).normalized()
        assertEquals(listOf("Kerjakan"), d.lists.single().items.map { it.text })
        assertEquals(listOf("1", "2", "3"), d.actions.map { it.payload })
    }

    @Test
    fun clearsDueThatDoesNotParse() {
        val d = ExtractData(
            category = "task",
            title = "x",
            lists = listOf(ItemList("A", items = listOf(
                ListItemData("a", due = "besok"),
                ListItemData("b", due = "+999999999-01-01"),
                ListItemData("c", due = "2026-10-02T09:00"),
            ))),
        ).normalized()
        assertEquals(listOf("", "", "2026-10-02T09:00"), d.lists.single().items.map { it.due })
    }

    @Test
    fun roundTripsStoredJson() {
        val info = mapOf("Total" to "Rp 50.000")
        assertEquals(info, ExtractJson.decodeInfo(ExtractJson.encodeInfo(info)))
        val actions = listOf(ActionData("whatsapp", "6281519201166"))
        assertEquals(actions, ExtractJson.decodeActions(ExtractJson.encodeActions(actions)))
        assertTrue(ExtractJson.decodeActions(null).isEmpty())
        assertTrue(ExtractJson.decodeActions("not json").isEmpty())
    }

    @Test
    fun decodesLegacyTasks() {
        val tasks = listOf(TaskItem(1, "a", true))
        assertEquals(tasks, ExtractJson.decodeTasks(ExtractJson.encodeTasks(tasks)))
        assertEquals(TaskItem(2, "b"), ExtractJson.decodeTasks("""[{"id":2,"description":"b","is_completed":false}]""").single())
        assertTrue(ExtractJson.decodeTasks(null).isEmpty())
    }

    @Test
    fun labelsCategoriesInIndonesian() {
        assertEquals("🛒 Belanja", categoryLabel("shopping"))
        assertEquals("📄 Lainnya", categoryLabel(null))
    }
}
