package app.pbbls.android.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M3's 4 dp spacing grid (#853): 4 / 8 / 12 / 16 / 24 / 32. The old scale
 * (3 / 10 / 13 / 17 / 22 / 34) was rooted on the iOS 17 pt body size, which
 * the M3 type scale replaced. Use these for paddings and gaps; corner radii
 * come from `MaterialTheme.shapes`.
 */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}
