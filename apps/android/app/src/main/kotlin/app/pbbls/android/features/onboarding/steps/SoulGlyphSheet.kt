package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase

/** The sheet's "Use for <name>" button, for the tests. */
const val ONBOARDING_GLYPH_USE_TAG = "onboarding_glyph_use"

/**
 * S2 (#1510 §1): the glyph picker for a soul, a `ModalBottomSheet` with the person's glyph and
 * name in the header, the same split stone as step 4 with "Draw one" as its last piece, and a
 * full-width "Use for <name>". The pick is the sheet's until Use; dismissing keeps the old glyph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoulGlyphSheet(
    personName: String,
    glyphs: List<Glyph>,
    currentGlyphId: String?,
    onUse: (String) -> Unit,
    onDraw: () -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by rememberSaveable { mutableStateOf(currentGlyphId) }
    val shown = glyphs.firstOrNull { it.id == picked } ?: glyphs.firstOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (shown != null) {
                    GlyphView(case = GlyphViewCase.PROFILE, strokes = shown.strokes, viewBox = shown.viewBox, side = HEADER_GLYPH_SIDE)
                }
                Column {
                    Text(
                        stringResource(R.string.onboarding_people_glyph_sheet_title, personName),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.onboarding_people_glyph_sheet_body, personName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            GlyphStoneGrid(
                glyphs = glyphs,
                selectedId = picked,
                onSelect = { picked = it.id },
                onDraw = onDraw,
                drawLabelRes = R.string.onboarding_people_glyph_draw,
            )
            PebblesPrimaryButton(
                text = stringResource(R.string.onboarding_people_glyph_use, personName),
                onClick = { picked?.let(onUse) },
                enabled = picked != null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(ONBOARDING_GLYPH_USE_TAG),
            )
        }
    }
}

private val HEADER_GLYPH_SIDE = 40.dp
