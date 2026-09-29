package com.snapbrain.core

import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val UNITS = setOf(
    "kg", "g", "gr", "gram", "ons", "ml", "l", "liter", "cc", "sdm", "sdt", "butir", "btr", "siung", "buah", "bh",
    "batang", "btg", "lembar", "lbr", "ruas", "genggam", "bungkus", "bks", "ikat", "sachet", "sct", "pcs", "biji",
    "potong", "ekor", "gelas", "cangkir", "mangkuk",
)
private val AMOUNT_WORDS = setOf("secukupnya", "sedikit", "sejumput", "segenggam", "setengah", "seperempat")
private val NUMBER = Regex("""^[\d½¼¾⅓⅔]+([.,/\-][\d½¼¾⅓⅔]+)*(kg|g|gr|ml|l|cc)?$""")
private val SPACES = Regex("""\s+""")
private val PARENS = Regex("""\(.*?\)""")

/**
 * Spec §8: "9 butir bawang merah" and "Bawang merah 5 siung" both become "bawang merah". A unit word is
 * dropped only right after a number or amount word, so "buah naga" stays whole.
 */
fun ingredientKey(text: String): String {
    val lower = text.lowercase(Locale.ROOT)
    // "(opsional) garam": nothing before the bracket, so drop the bracketed part instead.
    val head = lower.split(',', '(').first().ifBlank { lower.replace(PARENS, " ").split(',').first() }
    val kept = mutableListOf<String>()
    var afterAmount = false
    for (word in head.split(SPACES).map { it.trimEnd('.', ',', ':', ';') }.filter { it.isNotEmpty() }) {
        val amount = NUMBER.matches(word) || word in AMOUNT_WORDS
        if (!amount && !(afterAmount && word in UNITS)) kept += word
        afterAmount = amount
    }
    return kept.joinToString(" ").ifEmpty { text.trim().lowercase(Locale.ROOT) }
}

data class IngredientGroup<T>(val name: String, val rows: List<T>)

/** Same-named rows sit side by side; their amounts are never added up (spec S12). */
fun <T> groupByIngredient(rows: List<T>, text: (T) -> String): List<IngredientGroup<T>> =
    rows.groupBy { ingredientKey(text(it)) }.map { (name, r) -> IngredientGroup(name, r) }

/** "Rp 734.000"; negative amounts (over budget) get a leading minus. */
fun rupiah(amount: Long): String {
    val digits = "%,d".format(Locale.US, abs(amount)).replace(',', '.')
    return if (amount < 0) "-Rp $digits" else "Rp $digits"
}

enum class SizeUnit(val per: String, val base: Double) { ML("100 ml", 100.0), G("100 g", 100.0), PCS("item", 1.0) }

data class PackSize(val amount: Double, val unit: SizeUnit)

private val SIZE = Regex("""(\d+(?:[.,]\d+)?)\s*(ml|liter|ltr|l|gram|gr|g|kg|pcs|pc|lembar|sachet|butir|buah)\b""")
private val ISI = Regex("""isi\s*(\d+)""")
private val THOUSANDS = Regex("""\d{1,3}\.\d{3}""")

/**
 * "500 ml", "1,5 L", "1.500 ml", "1kg", "isi 12", "10 sachet"; null when no known unit is found.
 * ponytail: "2x250ml" counts one 250 ml pack; parse the multiplier if multipacks show up in real screenshots.
 */
fun parseSize(size: String?): PackSize? {
    val s = size?.lowercase(Locale.ROOT) ?: return null
    ISI.find(s)?.let { return PackSize(it.groupValues[1].toDouble(), SizeUnit.PCS).takeIf { p -> p.amount > 0 } }
    val m = SIZE.find(s) ?: return null
    val raw = m.groupValues[1]
    val n = if (THOUSANDS.matches(raw)) raw.replace(".", "").toDouble() else raw.replace(',', '.').toDouble()
    val pack = when (m.groupValues[2]) {
        "ml" -> PackSize(n, SizeUnit.ML)
        "l", "ltr", "liter" -> PackSize(n * 1000, SizeUnit.ML)
        "g", "gr", "gram" -> PackSize(n, SizeUnit.G)
        "kg" -> PackSize(n * 1000, SizeUnit.G)
        else -> PackSize(n, SizeUnit.PCS)
    }
    return pack.takeIf { it.amount > 0 }
}

/** Spec §8 Bandingkan: per 100 ml, per 100 g, or per item; "–" without a price or a readable size. */
fun unitPriceText(price: Long, size: String?): String {
    val pack = parseSize(size) ?: return "–"
    if (price <= 0) return "–"
    return rupiah((price * pack.unit.base / pack.amount).roundToLong()) + " / " + pack.unit.per
}

data class BelanjaTotal(val count: Int, val sum: Long, val noPrice: Int)

/** Spec §8 "Total incaran": pass the prices of the rows still to buy. */
fun belanjaTotal(prices: List<Long>): BelanjaTotal =
    BelanjaTotal(prices.count { it > 0 }, prices.filter { it > 0 }.sum(), prices.count { it <= 0 })

data class Budget(val spent: Long, val left: Long, val planned: Long) {
    val over: Boolean get() = planned > left
}

fun budgetOf(monthly: Long, spent: Long, planned: Long): Budget = Budget(spent, monthly - spent, planned)

/** Start of the current month in the device zone; "Terbeli" counts rows checked since then. */
fun monthStartMillis(now: ZonedDateTime): Long =
    now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.zone).toInstant().toEpochMilli()

data class CompareColumn(val title: String, val price: Long, val size: String?, val info: Map<String, String>)

/** Rows of (label, one value per column): price, size and unit price first, then every info label any column has. */
fun compareTable(columns: List<CompareColumn>): List<Pair<String, List<String>>> {
    val fixed = listOf(
        "Harga" to columns.map { if (it.price > 0) rupiah(it.price) else "–" },
        "Ukuran" to columns.map { it.size?.ifBlank { null } ?: "–" },
        "Per satuan" to columns.map { unitPriceText(it.price, it.size) },
    )
    val labels = columns.flatMap { it.info.keys }.distinct()
    return fixed + labels.map { label -> label to columns.map { it.info[label] ?: "–" } }
}
