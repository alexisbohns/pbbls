package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.path.components.PathPebbleRow
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.EmotionRef
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/**
 * Row-level previews for the read-only Path timeline (#531): every intensity
 * against the outline backdrop + authentic composed render, the no-render
 * pebble (silhouette only), and the missing-palette accent fallback — light
 * and dark. Fed by the same engine-composed fixtures as the fidelity grid.
 */
private val previewPalette: EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = "#7B5E99FF",
            secondaryHex = "#AE91CCFF",
            lightHex = "#F2EFF5FF",
            surfaceHex = "#7B5E991A",
            darkHex = "#2A2138FF",
        ),
    )

private fun pebble(
    name: String,
    intensity: Int,
    positiveness: Int,
    renderSvg: String?,
): Pebble =
    Pebble(
        id = name,
        name = name,
        happenedAt = OffsetDateTime.parse("2026-07-06T15:42:00+00:00"),
        createdAt = OffsetDateTime.parse("2026-07-06T15:45:00+00:00"),
        intensity = intensity,
        positiveness = positiveness,
        renderSvg = renderSvg,
        emotion = EmotionRef(id = "e1", slug = "joyful", name = "Joyful"),
    )

@Composable
private fun RowGallery() {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .background(system.background)
                .padding(24.dp),
    ) {
        PathPebbleRow(
            pebble = pebble("Small lowlight", 1, -1, PebbleSvgFixtures.smallLowlight),
            positionIndex = 0,
            palette = previewPalette,
            modifier = Modifier.fillMaxWidth(),
        )
        PathPebbleRow(
            pebble = pebble("Medium neutral", 2, 0, PebbleSvgFixtures.mediumNeutral),
            positionIndex = 1,
            palette = previewPalette,
            modifier = Modifier.fillMaxWidth(),
        )
        PathPebbleRow(
            pebble = pebble("Large highlight", 3, 1, PebbleSvgFixtures.largeHighlight),
            positionIndex = 2,
            palette = previewPalette,
            modifier = Modifier.fillMaxWidth(),
        )
        PathPebbleRow(
            pebble = pebble("No render yet", 2, 0, renderSvg = null),
            positionIndex = 3,
            palette = previewPalette,
            modifier = Modifier.fillMaxWidth(),
        )
        PathPebbleRow(
            pebble = pebble("No palette (accent fallback)", 2, 1, PebbleSvgFixtures.mediumHighlight),
            positionIndex = 4,
            palette = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
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
