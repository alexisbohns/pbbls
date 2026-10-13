package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.Valence
import app.pbbls.android.features.path.components.PathPebbleRow
import com.android.tools.screenshot.PreviewTest

/**
 * Row-level previews for the Path timeline (#531, #1048): every intensity as
 * its composed stone with the derived glyph, the missing-palette accent
 * fallback, and a photo-only moment with no name and no pebble yet (R3) —
 * light and dark.
 */
private val previewPalette: EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = "#7B5E99FF",
            secondaryHex = "#AE91CCFF",
            lightHex = "#F2EFF5FF",
            surfaceHex = "#7B5E991A",
            darkHex = "#2A2138FF",
            shadedHex = "#4A3A5CFF",
        ),
    )

private fun moment(
    name: String,
    valence: Valence,
): PathMoment = MomentPreviewFixtures.pathMoment(id = name, name = name, pebble = MomentPreviewFixtures.pathPebble(valence = valence))

@Composable
private fun RowGallery() {
    Column(
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
    ) {
        PathPebbleRow(
            moment = moment("Small lowlight", Valence.LOWLIGHT_SMALL),
            positionIndex = 0,
            palette = previewPalette,
            modifier = Modifier.fillMaxWidth(),
        )
        PathPebbleRow(
            moment = moment("Medium neutral", Valence.NEUTRAL_MEDIUM),
            positionIndex = 1,
            palette = previewPalette,
            modifier = Modifier.fillMaxWidth(),
        )
        PathPebbleRow(
            moment = moment("Large highlight", Valence.HIGHLIGHT_LARGE),
            positionIndex = 2,
            palette = previewPalette,
            modifier = Modifier.fillMaxWidth(),
        )
        PathPebbleRow(
            moment = moment("Medium highlight", Valence.HIGHLIGHT_MEDIUM),
            positionIndex = 3,
            palette = previewPalette,
            modifier = Modifier.fillMaxWidth(),
        )
        PathPebbleRow(
            moment = moment("No palette (accent fallback)", Valence.HIGHLIGHT_MEDIUM),
            positionIndex = 4,
            palette = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun PathRowsLight() {
    PebblesTheme { RowGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PathRowsDark() {
    PebblesTheme { RowGallery() }
}

/** A photo-only capture: no name ("Untitled", italic, R3), no pebble, a photo. */
@Composable
private fun UntitledPhotoOnlyRow() {
    Column(
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
    ) {
        PathPebbleRow(
            moment = MomentPreviewFixtures.pathMoment(name = null, pebble = null, firstSnapPath = "user-1/snap-1"),
            positionIndex = 0,
            palette = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun PathRow_UntitledPhotoOnly() {
    PebblesTheme { UntitledPhotoOnlyRow() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PathRow_UntitledPhotoOnlyDark() {
    PebblesTheme { UntitledPhotoOnlyRow() }
}
