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
import app.pbbls.android.core.ui.PebbleRow
import com.android.tools.screenshot.PreviewTest

/**
 * Shared-list-row previews (#565): the `Components/PebbleRow.swift` port the
 * souls/collections detail lists consume — the outline silhouette per
 * valence, an untitled moment (R3), and the missing-palette accent fallback —
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
    name: String?,
    valence: Valence,
): PathMoment =
    MomentPreviewFixtures.pathMoment(id = name ?: "untitled", name = name, pebble = MomentPreviewFixtures.pathPebble(valence = valence))

@Composable
private fun PebbleRowGallery() {
    Column(
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
    ) {
        PebbleRow(
            haptics = true,
            moment = moment("Medium neutral", Valence.NEUTRAL_MEDIUM),
            palette = previewPalette,
            onTap = {},
            onDelete = {},
            modifier = Modifier.fillMaxWidth(),
        )
        PebbleRow(
            haptics = true,
            moment = moment("Large highlight", Valence.HIGHLIGHT_LARGE),
            palette = previewPalette,
            onTap = {},
            onDelete = {},
            modifier = Modifier.fillMaxWidth(),
        )
        PebbleRow(
            haptics = true,
            moment = moment(null, Valence.NEUTRAL_MEDIUM),
            palette = previewPalette,
            onTap = {},
            onDelete = {},
            modifier = Modifier.fillMaxWidth(),
        )
        PebbleRow(
            haptics = true,
            moment = moment("No palette (accent fallback)", Valence.LOWLIGHT_SMALL),
            palette = null,
            onTap = {},
            onDelete = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun SharedPebbleRowsLight() {
    PebblesTheme { PebbleRowGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SharedPebbleRowsDark() {
    PebblesTheme { PebbleRowGallery() }
}
