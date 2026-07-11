package app.pbbls.android.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Six-step spacing scale rooted on the 17dp body baseline. `lg` equals the
 * body font size (17), `xxl` equals `lg * 2` (34). Use these in place of
 * literal dp values for paddings, stack spacings, and corner radii so visual
 * rhythm stays consistent across screens. Mirrors
 * `apps/ios/Pebbles/Theme/Spacing.swift`.
 */
object Spacing {
    val xs: Dp = 3.dp
    val sm: Dp = 10.dp
    val md: Dp = 13.dp
    val lg: Dp = 17.dp // root, == body font size
    val xl: Dp = 22.dp
    val xxl: Dp = 34.dp // == lg * 2
}
