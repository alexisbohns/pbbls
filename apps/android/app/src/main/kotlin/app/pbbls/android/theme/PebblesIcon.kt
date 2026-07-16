package app.pbbls.android.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icon-sizing tokens — the `pebblesIcon(_:)` analog (iOS `Theme/Icon+Pebbles.swift`).
 * iOS sizes SF Symbols via the font system and carries a weight per token;
 * Android icons are hand-traced vector drawables (no icon-library dependency —
 * established convention), so weight is baked into each drawable's stroke and
 * the token carries size only.
 */
enum class PebblesIconToken(
    val size: Dp,
) {
    SMALL(13.dp), // iOS 13pt semibold
    MEDIUM(15.dp), // iOS 15pt medium
    LARGE(17.dp), // iOS 17pt semibold
}

/**
 * Applies a size token to an icon drawable, tinted from [LocalContentColor]
 * by default so it inherits the ambient foreground like an SF Symbol.
 */
@Composable
fun PebblesIcon(
    painter: Painter,
    token: PebblesIconToken,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(token.size),
    )
}
