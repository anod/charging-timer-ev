package info.anodsplace.evtimer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

internal val DragHandleIcon: ImageVector by lazy {
    ImageVector.Builder("DragHandle", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes(
            "M7 3h3v3H7zM14 3h3v3h-3zM7 10.5h3v3H7zM14 10.5h3v3h-3zM7 18h3v3H7zM14 18h3v3h-3z"
        ),
        fill = SolidColor(Color.Black)
    ).build()
}

internal val DeletePresetIcon: ImageVector by lazy {
    ImageVector.Builder("DeletePreset", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes(
            "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM8 9h8v10H8V9zM15.5 4l-1-1h-5l-1 1H5v2h14V4z"
        ),
        fill = SolidColor(Color.Black)
    ).build()
}

internal val DoneIcon: ImageVector by lazy {
    ImageVector.Builder("Done", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"),
        fill = SolidColor(Color.Black)
    ).build()
}
