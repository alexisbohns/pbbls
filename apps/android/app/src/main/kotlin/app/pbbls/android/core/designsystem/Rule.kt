package app.pbbls.android.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import app.pbbls.android.core.designsystem.SpineLayoutTokens.RULE

/**
 * A section's hairline rule along the top or the bottom edge. [alpha], read as it draws, fades it in (a row's
 * landing in the month intro, #1242); the default draws it as ever.
 */
fun Modifier.rule(
    color: Color,
    above: Boolean,
    alpha: () -> Float = { 1f },
): Modifier =
    drawBehind {
        val y = if (above) RULE.toPx() / 2 else size.height - RULE.toPx() / 2
        drawLine(color, Offset(0f, y), Offset(size.width, y), RULE.toPx(), alpha = alpha())
    }
