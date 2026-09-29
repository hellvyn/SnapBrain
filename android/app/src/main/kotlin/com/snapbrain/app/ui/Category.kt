package com.snapbrain.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryStyle(val name: String, val icon: ImageVector, val tile: Color, val tint: Color)

fun categoryIcon(category: String?): ImageVector = when (category) {
    null -> SnapIcons.All
    "task" -> SnapIcons.Task
    "finance" -> SnapIcons.Finance
    "shopping" -> SnapIcons.Shopping
    "event" -> SnapIcons.Event
    "reference" -> SnapIcons.Reference
    else -> SnapIcons.Other
}

/** Spec §6.1 tile colors: light mockup row B, dark row A. */
@Composable
fun categoryStyle(category: String?): CategoryStyle {
    val dark = isSystemInDarkTheme()
    fun style(name: String, light: Pair<Long, Long>, night: Pair<Long, Long>): CategoryStyle {
        val (tile, tint) = if (dark) night else light
        return CategoryStyle(name, categoryIcon(category ?: "unclassified"), Color(tile), Color(tint))
    }
    return when (category) {
        "task" -> style("Tugas", 0xFFEDE8FF to 0xFF4128B8, 0xFF2A2344 to 0xFFB89CFF)
        "finance" -> style("Keuangan", 0xFFE4F6EE to 0xFF1F6B4A, 0xFF1A3325 to 0xFF6FD6A6)
        "shopping" -> style("Belanja", 0xFFFFF0E0 to 0xFF8A4B0F, 0xFF3A2A1A to 0xFFFFB86B)
        "event" -> style("Event", 0xFFE3ECFF to 0xFF1F4FC7, 0xFF1E2640 to 0xFF8FB0FF)
        "reference" -> style("Referensi", 0xFFDFF5F0 to 0xFF1B6B5C, 0xFF1A3330 to 0xFF6FD6C4)
        else -> style("Lainnya", 0xFFEFEEF3 to 0xFF4A4858, 0xFF252936 to 0xFFA3A9BA)
    }
}
