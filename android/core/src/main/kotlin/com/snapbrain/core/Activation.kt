package com.snapbrain.core

private val VERBS = mapOf(
    "masak" to "Masak sekarang",
    "beli" to "Mau beli",
    "kerjakan" to "Kerjakan",
    "bayar" to "Bayar",
    "ikut" to "Ikut acara",
    "coba" to "Coba sekarang",
)

/** masak/beli also put the screenshot's shopping rows in Belanja (spec §8). */
fun activatesBelanja(activation: String?): Boolean = activation == "masak" || activation == "beli"

/** Rows that can appear in To-do (spec §8). */
fun todoEligible(role: String, kind: String): Boolean = role == "todo" || role == "bawa" || kind == "steps"

/**
 * The hero button (spec §6.3, S7). [toBelanja]/[toTodo] say where this screenshot's rows would land; with
 * nowhere to land the button is hidden instead of promising a list that stays empty.
 */
fun activationLabel(activation: String?, active: Boolean, toBelanja: Boolean, toTodo: Boolean): String? {
    val verb = VERBS[activation] ?: return null
    if (!toBelanja && !toTodo) return null
    if (!active) return verb
    return "✓ Ada di " + listOfNotNull("Belanja".takeIf { toBelanja }, "To-do".takeIf { toTodo }).joinToString(" & ")
}
