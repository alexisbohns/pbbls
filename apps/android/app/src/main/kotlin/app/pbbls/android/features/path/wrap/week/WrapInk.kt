package app.pbbls.android.features.path.wrap.week

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup

/** A family's ink as the seals print it: `primary` on Paper, `secondary` at Night; grey ink without one. */
@Composable
internal fun spotInk(palette: EmotionPalette?): Color {
    val colors = PebblesTheme.spine.colors
    return palette?.let { if (colors.isNight) it.secondary else it.primary } ?: colors.ink3
}

internal fun valenceOf(
    size: ValenceSizeGroup,
    polarity: ValencePolarity,
): Valence = Valence.entries.first { it.sizeGroup == size && it.polarity == polarity }
