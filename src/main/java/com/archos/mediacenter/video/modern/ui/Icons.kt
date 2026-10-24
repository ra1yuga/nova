// Nova Modern: the stroke icon set from the design canvas, drawn as vectors
package com.archos.mediacenter.video.modern.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun c(cx: Float, cy: Float, r: Float) =
    "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

private fun rr(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,${r}v${h - 2 * r}a$r,$r 0 0 1 ${-r},${r}h${-(w - 2 * r)}a$r,$r 0 0 1 ${-r},${-r}v${-(h - 2 * r)}a$r,$r 0 0 1 $r,${-r}z"

private fun icon(name: String, stroke: List<String> = emptyList(), fill: List<String> = emptyList(), width: Float = 2f): ImageVector {
    val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    for (d in fill) {
        b.addPath(pathData = addPathNodes(d), fill = SolidColor(Color.White))
    }
    for (d in stroke) {
        b.addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

object NovaIcons {
    val Home = icon("home", listOf("M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"))
    val Movies = icon("movies", listOf(rr(3f, 4f, 18f, 16f, 2f), "M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4"), width = 1.9f)
    val Shows = icon("shows", listOf(rr(3f, 6f, 18f, 13f, 2f), "M8 2l4 4 4-4"), width = 1.9f)
    val Folder = icon("folder", listOf("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"), width = 1.9f)
    val Search = icon("search", listOf(c(11f, 11f, 7f), "M20 20l-3.5-3.5"))
    val Play = icon("play", fill = listOf("M7 4.5v15l13-7.5z"))
    val Info = icon("info", listOf(c(12f, 12f, 9f), "M12 11v6M12 7.5v.5"))
    val Server = icon("server", listOf(rr(3f, 4f, 18f, 7f, 1.5f), rr(3f, 13f, 18f, 7f, 1.5f), "M7 7.5h.01M7 16.5h.01"), width = 1.9f)
    val Settings = icon("settings", listOf("M4 6h9M19 6h1M4 12h3M13 12h7M4 18h11M21 18h-1", c(16f, 6f, 2.5f), c(10f, 12f, 2.5f), c(18f, 18f, 2.5f)), width = 1.9f)
    val Back = icon("back", listOf("M15 5l-7 7 7 7"), width = 2.2f)
    val Chevron = icon("chevron", listOf("M9 5l7 7-7 7"))
    val ChevronDown = icon("chevron_down", listOf("M6 9l6 6 6-6"), width = 2.2f)
    val More = icon("more", fill = listOf(c(12f, 5f, 1.8f), c(12f, 12f, 1.8f), c(12f, 19f, 1.8f)))
    val Check = icon("check", listOf("M5 12l5 5 9-10"), width = 2.4f)
    val Plus = icon("plus", listOf("M12 5v14M5 12h14"))
    val Captions = icon("captions", listOf(rr(3f, 5f, 18f, 14f, 2f), "M10.5 10.2a2.2 2.2 0 1 0 0 3.6M17.5 10.2a2.2 2.2 0 1 0 0 3.6"), width = 1.9f)
    val External = icon("external", listOf("M14 4h6v6M20 4l-9 9", "M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"), width = 1.9f)
    val Pencil = icon("pencil", listOf("M4 20h4L19 9l-4-4L4 16z"), width = 1.9f)
    val Close = icon("close", listOf("M6 6l12 12M18 6 6 18"), width = 2.2f)
    val Clock = icon("clock", listOf(c(12f, 12f, 9f), "M12 7v5l3 2"))
    val Star = icon("star", fill = listOf("M12 3l2.8 5.8 6.2.9-4.5 4.4 1.1 6.3L12 17.4 6.4 20.4l1.1-6.3L3 9.7l6.2-.9z"))
    val Sort = icon("sort", listOf("M7 4v16M3 16l4 4 4-4M17 20V4M13 8l4-4 4 4"), width = 1.9f)
    val Grid = icon("grid", listOf(rr(4f, 4f, 7f, 7f, 1.5f), rr(13f, 4f, 7f, 7f, 1.5f), rr(4f, 13f, 7f, 7f, 1.5f), rr(13f, 13f, 7f, 7f, 1.5f)), width = 1.9f)
    val File = icon("file", listOf("M6 3h8l4 4v14H6z", "M14 3v4h4"), width = 1.9f)
    val Phone = icon("phone", listOf(rr(7f, 2.5f, 10f, 19f, 2f), "M11 18.5h2"), width = 1.9f)
    val Network = icon("network", listOf(c(12f, 5f, 2.5f), c(5f, 18f, 2.5f), c(19f, 18f, 2.5f), "M12 7.5v4M12 11.5l-5.5 4.5M12 11.5l5.5 4.5"), width = 1.9f)
    val Refresh = icon("refresh", listOf("M20 11a8 8 0 1 0-2.3 5.7", "M20 4v7h-7"), width = 1.9f)
    val Classic = icon("classic", listOf(rr(3f, 4f, 18f, 16f, 2f), "M3 9h18M8 9v11"), width = 1.9f)
}
