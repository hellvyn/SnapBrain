package com.snapbrain.core

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BelanjaTest {
    @Test
    fun ingredientKeyDropsQuantitiesAndUnits() {
        assertEquals("bawang merah", ingredientKey("9 butir bawang merah"))
        assertEquals("bawang merah", ingredientKey("Bawang merah 5 siung"))
        assertEquals("garam", ingredientKey("½ sdt garam"))
        assertEquals("garam", ingredientKey("garam secukupnya"))
        assertEquals("gula pasir", ingredientKey("1 1/2 sdm gula pasir"))
        assertEquals("bawang putih", ingredientKey("3-4 siung bawang putih"))
        assertEquals("tepung terigu", ingredientKey("500g tepung terigu"))
        assertEquals("tomat", ingredientKey("2 buah tomat, potong dadu"))
        assertEquals("santan", ingredientKey("santan (dari 1 butir kelapa)"))
        assertEquals("garam", ingredientKey("setengah sdt garam."))
        assertEquals("gula", ingredientKey("1 sdm. gula"))
        assertEquals("garam", ingredientKey("(opsional) garam"))
    }

    @Test
    fun ingredientKeyKeepsWordsThatOnlyLookLikeUnits() {
        assertEquals("buah naga", ingredientKey("buah naga"))
        assertEquals("daun jeruk", ingredientKey("3 lembar daun jeruk"))
        assertEquals("200 ml", ingredientKey("200 ml")) // nothing left: fall back to the text
    }

    @Test
    fun groupsByIngredientInFirstSeenOrder() {
        val groups = groupByIngredient(listOf("9 butir bawang merah", "1 ekor ayam", "Bawang merah 5 siung")) { it }
        assertEquals(listOf("bawang merah", "ayam"), groups.map { it.name })
        assertEquals(listOf("9 butir bawang merah", "Bawang merah 5 siung"), groups[0].rows)
    }

    @Test
    fun formatsRupiah() {
        assertEquals("Rp 0", rupiah(0))
        assertEquals("Rp 734.000", rupiah(734_000))
        assertEquals("Rp 1.250.000", rupiah(1_250_000))
        assertEquals("-Rp 50.000", rupiah(-50_000))
    }

    @Test
    fun parsesPackSizes() {
        assertEquals(PackSize(500.0, SizeUnit.ML), parseSize("500 ml"))
        assertEquals(PackSize(1500.0, SizeUnit.ML), parseSize("1,5 L"))
        assertEquals(PackSize(1500.0, SizeUnit.ML), parseSize("1.500 ml"))
        assertEquals(PackSize(1000.0, SizeUnit.G), parseSize("1kg"))
        assertEquals(PackSize(250.0, SizeUnit.G), parseSize("250 gr"))
        assertEquals(PackSize(12.0, SizeUnit.PCS), parseSize("isi 12"))
        assertEquals(PackSize(10.0, SizeUnit.PCS), parseSize("10 sachet"))
        assertEquals(PackSize(250.0, SizeUnit.ML), parseSize("2x250ml")) // multipacks count one pack
        assertNull(parseSize("jumbo"))
        assertNull(parseSize(""))
        assertNull(parseSize(null))
        assertNull(parseSize("0 ml"))
    }

    @Test
    fun unitPriceOrDash() {
        assertEquals("Rp 5.000 / 100 ml", unitPriceText(25_000, "500 ml"))
        assertEquals("Rp 2.000 / 100 ml", unitPriceText(30_000, "1,5 L"))
        assertEquals("Rp 1.500 / 100 g", unitPriceText(15_000, "1kg"))
        assertEquals("Rp 4.000 / item", unitPriceText(48_000, "isi 12"))
        assertEquals("–", unitPriceText(0, "500 ml"))
        assertEquals("–", unitPriceText(25_000, "jumbo"))
        assertEquals("–", unitPriceText(25_000, null))
    }

    @Test
    fun totalsCountRowsWithoutPriceSeparately() {
        assertEquals(BelanjaTotal(count = 2, sum = 734_000, noPrice = 1), belanjaTotal(listOf(700_000, 0, 34_000)))
        assertEquals(BelanjaTotal(0, 0, 0), belanjaTotal(emptyList()))
    }

    @Test
    fun budgetWarnsWhenPlannedExceedsWhatIsLeft() {
        val b = budgetOf(monthly = 1_000_000, spent = 400_000, planned = 734_000)
        assertEquals(600_000, b.left)
        assertTrue(b.over)
        assertFalse(budgetOf(1_000_000, 0, 734_000).over)
        assertEquals(-50_000, budgetOf(100_000, 150_000, 0).left)
    }

    @Test
    fun monthStartIsLocalMidnightOnTheFirst() {
        val zone = ZoneId.of("Asia/Jakarta")
        val now = ZonedDateTime.of(2026, 9, 29, 12, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli(), monthStartMillis(now))
    }

    @Test
    fun compareTableUnionsInfoLabels() {
        val table = compareTable(
            listOf(
                CompareColumn("Susu A", 25_000, "500 ml", mapOf("Toko" to "Toko A", "Rating" to "4.9")),
                CompareColumn("Susu B", 0, null, mapOf("Toko" to "Toko B", "Terjual" to "1rb")),
            ),
        )
        assertEquals(listOf("Harga", "Ukuran", "Per satuan", "Toko", "Rating", "Terjual"), table.map { it.first })
        assertEquals(listOf("Rp 25.000", "–"), table[0].second)
        assertEquals(listOf("500 ml", "–"), table[1].second)
        assertEquals(listOf("Rp 5.000 / 100 ml", "–"), table[2].second)
        assertEquals(listOf("4.9", "–"), table[4].second)
    }
}
