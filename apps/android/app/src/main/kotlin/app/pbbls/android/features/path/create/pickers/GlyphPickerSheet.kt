package app.pbbls.android.features.path.create.pickers

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.services.LocalGlyphService
import app.pbbls.android.features.path.render.GlyphImage
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

private const val TAG = "glyph-picker"

/**
 * The glyph picker (D13) — ports iOS `GlyphPickerSheet`, selection-only (no
 * "carve new glyph" row). A `ModalBottomSheet` that lazily lists the user's
 * glyphs via [LocalGlyphService], with loading/error/retry states. Pure
 * [GlyphPickerBody] renders the grid for screenshot previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlyphPickerSheet(
    currentGlyphId: String?,
    onDismiss: () -> Unit,
    onSelected: (Glyph) -> Unit,
) {
    val service = LocalGlyphService.current
    val accent = PebblesTheme.colors.accent
    var glyphs by remember { mutableStateOf<List<Glyph>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadToken) {
        isLoading = true
        loadError = false
        try {
            glyphs = service.list()
        } catch (e: Exception) {
            Log.e(TAG, "glyph list failed", e)
            loadError = true
        } finally {
            isLoading = false
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 200.dp)) {
            when {
                isLoading ->
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = accent.primary)
                    }
                loadError -> GlyphLoadError(onRetry = { reloadToken++ })
                else -> GlyphPickerBody(glyphs = glyphs, currentGlyphId = currentGlyphId, onSelect = onSelected)
            }
        }
    }
}

@Composable
fun GlyphPickerBody(
    glyphs: List<Glyph>,
    currentGlyphId: String?,
    onSelect: (Glyph) -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PebblesText(
            text = stringResource(R.string.create_glyph_title),
            style = PebblesTypography.cardHeading,
            color = system.secondary,
        )
        glyphs.chunked(3).forEach { rowGlyphs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowGlyphs.forEach { glyph ->
                    val tint = if (glyph.id == currentGlyphId) accent.primary else system.secondary
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelect(glyph) }
                                .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphImage(
                            strokes = glyph.strokes,
                            viewBox = glyph.viewBox,
                            strokeColor = tint,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                    }
                }
                repeat(3 - rowGlyphs.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GlyphLoadError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PebblesText(
            text = stringResource(R.string.create_glyph_load_error),
            style = PebblesTypography.callout,
            color = system.secondary,
        )
        TextButton(onClick = onRetry) {
            PebblesText(
                text = stringResource(R.string.pebble_detail_retry),
                style = PebblesTypography.buttonLabel,
                color = accent.primary,
            )
        }
    }
}
