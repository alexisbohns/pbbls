package app.pbbls.android.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.glyph.GlyphCatalog
import app.pbbls.android.core.designsystem.glyph.GlyphForm

/** A form change (spec D9): `RevealMotion`'s pop length. */
private const val FORM_CHANGE_MS = 220

/**
 * A [PbblsGlyph] in one of its forms (spec §2; iOS `PbblsGlyphView`), drawn
 * through [Icon] on a square [size] side (24dp by default: icons keep dp and
 * do not follow the font scale, the Material convention). It takes
 * [LocalContentColor] unless [tint] is given, and is hidden from accessibility
 * unless [contentDescription] is. A form change crossfades with a slight
 * scale, none under reduce motion (spec D9).
 */
@Composable
fun PbblsGlyphImage(
    glyph: PbblsGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    form: GlyphForm = GlyphForm.REGULAR,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    GlyphCatalog.install(LocalContext.current)
    if (rememberReduceMotion()) {
        Icon(
            painter = rememberVectorPainter(GlyphCatalog.vector(glyph, form)),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.size(size),
        )
        return
    }
    val labelled =
        if (contentDescription == null) {
            modifier
        } else {
            modifier.semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            }
        }
    AnimatedContent(
        targetState = form,
        transitionSpec = {
            (fadeIn(tween(FORM_CHANGE_MS)) + scaleIn(tween(FORM_CHANGE_MS), initialScale = 0.9f)) togetherWith
                fadeOut(tween(FORM_CHANGE_MS))
        },
        label = "glyph form",
        modifier = labelled.size(size),
    ) { shown ->
        Icon(
            painter = rememberVectorPainter(GlyphCatalog.vector(glyph, shown)),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * [PbblsGlyphImage] at a named [size]. Pass the size here (or a `Dp` to the
 * other overload's `size`), never through [modifier]: the size is applied
 * after yours, and the first size wins.
 */
@Composable
fun PbblsGlyphImage(
    glyph: PbblsGlyph,
    size: PbblsIconSize,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    form: GlyphForm = GlyphForm.REGULAR,
    tint: Color = LocalContentColor.current,
) {
    PbblsGlyphImage(
        glyph = glyph,
        contentDescription = contentDescription,
        modifier = modifier,
        form = form,
        size = size.toDp(LocalDensity.current),
        tint = tint,
    )
}

/** A form as a [Painter], for the slots that take one instead of a composable. */
@Composable
fun rememberPbblsGlyphPainter(
    glyph: PbblsGlyph,
    form: GlyphForm = GlyphForm.REGULAR,
): Painter {
    GlyphCatalog.install(LocalContext.current)
    return rememberVectorPainter(GlyphCatalog.vector(glyph, form))
}
