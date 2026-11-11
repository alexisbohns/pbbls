package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.pbbls.android.core.designsystem.glyph.GlyphForm

/**
 * What a detail pane shows before anything is picked (#940): a muted icon and
 * one line, centered. Only ever composed beside its list on large screens.
 */
@Composable
fun DetailPlaceholder(
    icon: PbblsGlyph,
    text: String,
    modifier: Modifier = Modifier,
    form: GlyphForm = GlyphForm.REGULAR,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(PebblesTheme.spacing.xl)
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PbblsGlyphImage(
            glyph = icon,
            size = PbblsGlyphSize.HERO,
            contentDescription = null,
            form = form,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
