package love.panyi.forever.ui.schedule

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** 界面用到的几个线条图标（24×24 描边），免去引入图标库；用 Icon 的 tint 上色。 */
object LineIcons {
    val Check = icon("Check", "M20 6 9 17l-5-5")
    val Plus = icon("Plus", "M12 5v14M5 12h14")
    val Calendar = icon("Calendar", "M6 5h12a2.5 2.5 0 0 1 2.5 2.5v10A2.5 2.5 0 0 1 18 20H6a2.5 2.5 0 0 1-2.5-2.5v-10A2.5 2.5 0 0 1 6 5ZM3.5 10h17M8 3v4M16 3v4")
    val Clock = icon("Clock", "M12 5a8 8 0 1 1 0 16 8 8 0 0 1 0-16ZM12 9v4l2.5 2M9 2h6")
    val ChevronUp = icon("ChevronUp", "m18 15-6-6-6 6")
    val ChevronDown = icon("ChevronDown", "m6 9 6 6 6-6")

    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}
