package com.snapbrain.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 24dp stroke icons drawn from the mockup's SVG paths, so the app needs no icon library. */
object SnapIcons {
    val All = icon("all", "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z")
    val Task = icon("task", "M9 11l3 3 8 -8M20 12v7H4V5h11")
    val Finance = icon("finance", "M3 7h18v12H3zM16 13h2M3 7l3 -3h12")
    val Shopping = icon("shopping", "M6 6h15l-2 9H8L6 3H3M9 20h0.01M18 20h0.01")
    val Event = icon("event", "M3 5h18v16H3zM3 10h18M8 3v4M16 3v4")
    val Reference = icon("reference", "M4 5a2 2 0 0 1 2 -2h14v16H6a2 2 0 0 0 -2 2zM4 5v16")
    val Other = icon("other", "M6 3h9l5 5v13H6zM14 3v6h6")
    val Search = icon("search", "M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0M20 20l-3.5 -3.5")
    val Back = icon("back", "M15 18l-6 -6 6 -6")
    val Share = icon(
        "share",
        "M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0M15 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0M8.6 13.5l6.8 4M15.4 6.5l-6.8 4",
    )
    val Delete = icon("delete", "M3 6h18M8 6V4h8v2M6 6l1 14h10l1 -14")
    val More = icon("more", "M5 12h0.01M12 12h0.01M19 12h0.01")
    val Image = icon("image", "M3 6a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3H6a3 3 0 0 1 -3 -3zM7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M21 15l-5 -5L5 21")
    val Link = icon("link", "M14 4h6v6M20 4l-9 9M18 14v6H4V6h6")
    val Copy = icon("copy", "M8 10a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2zM4 16V4h12")
    val Pin = icon("pin", "M12 21s-7 -6.2 -7 -11a7 7 0 0 1 14 0c0 4.8 -7 11 -7 11zM9.5 10a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0")
    val Chat = icon("chat", "M21 12a9 9 0 0 1 -13.5 7.8L3 21l1.3 -4.3A9 9 0 1 1 21 12z")
    val Phone = icon("phone", "M5 4h4l2 5 -2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1 -2 2A16 16 0 0 1 3 6a2 2 0 0 1 2 -2z")
    val Timer = icon("timer", "M4 13a8 8 0 1 0 16 0a8 8 0 1 0 -16 0M12 9v4l2 2M9 2h6")
    val Truck = icon("truck", "M3 5h11v11H3zM14 9h4l3 3v4h-7M5 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M15 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
}

private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
