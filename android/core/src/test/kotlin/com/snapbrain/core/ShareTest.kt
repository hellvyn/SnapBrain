package com.snapbrain.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ShareTest {
    @Test
    fun formatsInfoChecklistsAndSteps() {
        val text = shareText(
            "Resep Pepes Ayam",
            mapOf("Porsi" to "20 orang"),
            listOf(
                ShareList("Bumbu", steps = false, items = listOf("9 butir bawang merah" to true, "4 siung bawang putih" to false)),
                ShareList("Langkah", steps = true, items = listOf("Haluskan bumbu" to false, "Kukus 30 menit" to false)),
            ),
        )
        assertEquals(
            "Resep Pepes Ayam\nPorsi: 20 orang\n\nBumbu\n☑ 9 butir bawang merah\n☐ 4 siung bawang putih\n\nLangkah\n☐ 1. Haluskan bumbu\n☐ 2. Kukus 30 menit",
            text,
        )
    }
}
