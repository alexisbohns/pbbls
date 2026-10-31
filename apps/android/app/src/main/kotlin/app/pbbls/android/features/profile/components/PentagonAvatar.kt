package app.pbbls.android.features.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.stone.SquirclePentagonShape
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.ui.render.GlyphImage

/** The pentagon's design box (spec D5): 128 × 122 dp. */
private val AVATAR_WIDTH = 128.dp
private val AVATAR_HEIGHT = 122.dp

/** The glyph inside it, and how far below the centre it sits (the mock's 8 px top margin, halved by centring). */
private val GLYPH = 58.dp
private val GLYPH_DROP = 8.dp

/** Glyph strokes are drawn in the canonical 200-unit square. */
private const val GLYPH_VIEW_BOX = "0 0 200 200"

/**
 * You's avatar (#1383, spec D14.2): the squircle pentagon in `primaryContainer`, the profile glyph
 * inside at 58 dp in `onPrimaryContainer`, or the dotted pebble when the profile has none (or its
 * strokes have not loaded). Decorative: the name below it is what TalkBack reads.
 *
 * The glyph is drawn through [GlyphImage] rather than `GlyphView`, whose profile case inks in
 * `primary`; the pentagon asks for the on-container role.
 */
@Composable
internal fun PentagonAvatar(
    glyphStrokes: List<GlyphStroke>?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .size(AVATAR_WIDTH, AVATAR_HEIGHT)
                .background(colors.primaryContainer, SquirclePentagonShape),
        contentAlignment = Alignment.Center,
    ) {
        val glyph = Modifier.padding(top = GLYPH_DROP).size(GLYPH)
        if (glyphStrokes.isNullOrEmpty()) {
            PbblsIconImage(
                icon = PbblsIcon.PEBBLE_DOTTED_GLYPH,
                contentDescription = null,
                size = GLYPH,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.padding(top = GLYPH_DROP),
            )
        } else {
            GlyphImage(
                strokes = glyphStrokes,
                viewBox = GLYPH_VIEW_BOX,
                strokeColor = colors.onPrimaryContainer,
                modifier = glyph,
            )
        }
    }
}
