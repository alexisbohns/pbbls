package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.common.UuidHash
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.ui.render.stone.LitStone
import app.pbbls.android.core.ui.render.stone.rememberStonePressState
import com.android.tools.screenshot.PreviewTest

/**
 * The lit stone and its seal (#1122 Part 3): the gallery and the four seeds from
 * `SpineLabStones` (debug sources, shared with the Spine lab), at rest: nothing is
 * pressed, so the glint and the ripple draw nothing.
 */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun LitStoneGalleryLight() {
    PebblesTheme { LitStoneGallery(pressable = false) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun LitStoneGalleryDark() {
    PebblesTheme { LitStoneGallery(pressable = false) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun LitStoneSeedsLight() {
    PebblesTheme { LitStoneSeeds(pressable = false) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun LitStoneSeedsDark() {
    PebblesTheme { LitStoneSeeds(pressable = false) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun LitStoneVersoLight() {
    PebblesTheme { LitStoneVerso() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun LitStoneVersoNight() {
    PebblesTheme { LitStoneVerso() }
}

/** The real joy and fear category palettes, as `SpineFixtureWeek` carries them. */
private val versoJoyPalette: EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = "#A15C08FF",
            secondaryHex = "#CF8C39FF",
            lightHex = "#FAF6EAFF",
            surfaceHex = "#A15C081A",
            darkHex = "#201202FF",
            shadedHex = "#714006FF",
        ),
    )

private val versoFearPalette: EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = "#7B5E99FF",
            secondaryHex = "#AE91CCFF",
            lightHex = "#F2EFF5FF",
            surfaceHex = "#7B5E991A",
            darkHex = "#19131FFF",
            shadedHex = "#56426BFF",
        ),
    )

/**
 * The lit stone at the card verso's width (pebble card Task 1.7): the largest
 * highlight in joy and the smallest lowlight in fear, both painted 150 dp wide.
 */
@Composable
private fun LitStoneVerso() {
    // Stacked: side by side, the two 150 dp stones and their halos overflow the preview's width.
    Column(
        modifier =
            Modifier
                .background(PebblesTheme.spine.colors.ground)
                // The halo reaches about a third of the stone's width past the box: room for it.
                .padding(56.dp),
        verticalArrangement = Arrangement.spacedBy(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LitStone(
            valence = Valence.HIGHLIGHT_LARGE,
            palette = versoJoyPalette,
            glyph = MomentPreviewFixtures.pebbleGlyph(),
            seed = UuidHash.fnv1a("0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b"),
            press = rememberStonePressState(),
            width = 150.dp,
        )
        LitStone(
            valence = Valence.LOWLIGHT_SMALL,
            palette = versoFearPalette,
            glyph = MomentPreviewFixtures.pebbleGlyph(),
            seed = UuidHash.fnv1a("5b1f3a2c-9d8e-4f7a-b6c5-d4e3f2a1b0c9"),
            press = rememberStonePressState(),
            width = 150.dp,
        )
    }
}
