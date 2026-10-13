package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.CollectionMode
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.features.profile.CollectionModePicker
import app.pbbls.android.features.profile.components.CollectionGlyphSection
import app.pbbls.android.features.profile.components.CollectionModeBadge
import com.android.tools.screenshot.PreviewTest

/**
 * Collections-management previews (#569): the mode badge in all three modes
 * (null renders nothing) and the form's mode picker in none-selected and
 * mode-selected states — light and dark. The collections screens themselves
 * read services, so the review surface is this pure-component gallery plus
 * the on-device pass.
 */
@Composable
private fun CollectionsGallery() {
    Column(
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
                .width(360.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CollectionModeBadge(mode = CollectionMode.STACK)
            CollectionModeBadge(mode = CollectionMode.PACK)
            CollectionModeBadge(mode = CollectionMode.TRACK)
            CollectionModeBadge(mode = null)
        }
        CollectionModePicker(selected = null, onSelect = {})
        CollectionModePicker(selected = CollectionMode.TRACK, onSelect = {})
    }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@Composable
fun CollectionsGalleryLight() {
    PebblesTheme { CollectionsGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CollectionsGalleryDark() {
    PebblesTheme { CollectionsGallery() }
}

private val previewGlyph =
    Glyph(
        id = "glyph-1",
        strokes = listOf(GlyphStroke(d = "M 20 100 Q 100 20 180 100", width = 6.0)),
        viewBox = "0 0 200 200",
    )

/** The collection form's glyph row (R11), with or without a glyph set. */
@Composable
private fun CollectionGlyphPreview(glyph: Glyph?) {
    CollectionGlyphSection(
        glyph = glyph,
        onChoose = {},
        onRemove = {},
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
                .width(360.dp),
    )
}

/** A glyph set: its row, and Remove glyph under it. */
@PreviewTest
@Preview(showBackground = true)
@Preview(name = "fr", showBackground = true, locale = "fr")
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CollectionForm_WithGlyph() {
    PebblesTheme { CollectionGlyphPreview(previewGlyph) }
}

/** No glyph: the dashed slot and no Remove row. */
@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@Composable
fun CollectionForm_NoGlyph() {
    PebblesTheme { CollectionGlyphPreview(null) }
}
