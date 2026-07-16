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
import app.pbbls.android.components.PebbleRow
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.EmotionRef
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/**
 * Shared-list-row previews (#565): the `Components/PebbleRow.swift` port the
 * souls/collections detail lists will consume — composed render, silhouette
 * fallback (no render), and the missing-palette accent fallback — light and
 * dark. Fed by the same engine-composed fixtures as the Path row gallery.
 */
private val previewPalette: EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = "#7B5E99FF",
            secondaryHex = "#AE91CCFF",
            lightHex = "#F2EFF5FF",
            surfaceHex = "#7B5E991A",
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
private fun PebbleRowGallery() {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .background(system.background)
                .padding(24.dp),
    ) {
        PebbleRow(
            pebble = pebble("Medium neutral", 2, 0, PebbleSvgFixtures.mediumNeutral),
            palette = previewPalette,
            onTap = {},
            onDelete = {},
            modifier = Modifier.fillMaxWidth(),
        )
        PebbleRow(
            pebble = pebble("Large highlight", 3, 1, PebbleSvgFixtures.largeHighlight),
            palette = previewPalette,
            onTap = {},
            onDelete = {},
            modifier = Modifier.fillMaxWidth(),
        )
        PebbleRow(
            pebble = pebble("No render yet", 2, 0, renderSvg = null),
            palette = previewPalette,
            onTap = {},
            onDelete = {},
            modifier = Modifier.fillMaxWidth(),
        )
        PebbleRow(
            pebble = pebble("No palette (accent fallback)", 1, -1, PebbleSvgFixtures.smallLowlight),
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
