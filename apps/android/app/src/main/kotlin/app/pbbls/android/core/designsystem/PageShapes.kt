package app.pbbls.android.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The corners of Home and You (spec 2026-10-31-android-home-and-you-design.md, D19), so no
 * feature writes a radius (`ThemeLiteralsTest`): [card] for content you browse
 * ([PebblesCard]: month wraps, achievement badges, the announcement), [box] for the one
 * list (Your first five).
 */
object PageShapes {
    val card = RoundedCornerShape(18.dp)
    val box = RoundedCornerShape(26.dp)
}
