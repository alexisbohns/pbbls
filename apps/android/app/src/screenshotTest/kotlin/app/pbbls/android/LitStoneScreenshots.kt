package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.common.UuidHash
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.ui.render.stone.LitStone
import app.pbbls.android.core.ui.render.stone.StonePressState
import app.pbbls.android.core.ui.render.stone.StoneSeal
import app.pbbls.android.core.ui.render.stone.rememberStonePressState
import com.android.tools.screenshot.PreviewTest

/**
 * The lit stone and its seal (#1122 Part 3): every valence in the pride
 * palette with the fixture glyph, each beside its seal, the blank moment, and
 * the same medium gem under four seeds, and one gem held under reduced motion
 * (dark rings, a still sheen). At rest otherwise: nothing is pressed, so the
 * glint and the ripple draw nothing.
 */
private val pridePalette: EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = "#B5703CFF",
            secondaryHex = "#D8A878FF",
            lightHex = "#F7EFE6FF",
            surfaceHex = "#B5703C1A",
            darkHex = "#2A2138FF",
            shadedHex = "#4A3A5CFF",
        ),
    )

private val fixtureSeed = UuidHash.fnv1a("0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b")

private val seedUuids =
    listOf(
        "0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b",
        "5b1f3a2c-9d8e-4f7a-b6c5-d4e3f2a1b0c9",
        "a7c2e9f4-3b1d-4e8a-9f6c-2d5b8a1e4c7f",
        "f0e1d2c3-b4a5-4968-8776-655443322110",
    )

@Composable
private fun StoneAndSeal(
    valence: Valence,
    palette: EmotionPalette?,
    seed: Long,
) {
    val ground = PebblesTheme.spine.colors.ground
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LitStone(
            valence = valence,
            palette = palette,
            glyph = MomentPreviewFixtures.pebbleGlyph(),
            seed = seed,
            press = rememberStonePressState(),
        )
        StoneSeal(
            valence = valence,
            palette = palette,
            glyph = MomentPreviewFixtures.pebbleGlyph(),
            knockout = ground,
            modifier = Modifier.width(24.dp),
        )
    }
}

@Composable
private fun LitStoneGallery() {
    Column(
        modifier =
            Modifier
                .background(PebblesTheme.spine.colors.ground)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Valence.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { valence -> StoneAndSeal(valence = valence, palette = pridePalette, seed = fixtureSeed) }
            }
        }
        StoneAndSeal(valence = Valence.NEUTRAL_MEDIUM, palette = null, seed = fixtureSeed)
        LitStone(
            valence = Valence.HIGHLIGHT_MEDIUM,
            palette = pridePalette,
            glyph = MomentPreviewFixtures.pebbleGlyph(),
            seed = fixtureSeed,
            press = remember { StonePressState.stillTouched() },
        )
    }
}

@Composable
private fun LitStoneSeeds() {
    Row(
        modifier =
            Modifier
                .background(PebblesTheme.spine.colors.ground)
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        seedUuids.forEach { uuid ->
            LitStone(
                valence = Valence.HIGHLIGHT_MEDIUM,
                palette = pridePalette,
                glyph = MomentPreviewFixtures.pebbleGlyph(),
                seed = UuidHash.fnv1a(uuid),
                press = rememberStonePressState(),
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun LitStoneGalleryLight() {
    PebblesTheme { LitStoneGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun LitStoneGalleryDark() {
    PebblesTheme { LitStoneGallery() }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun LitStoneSeedsLight() {
    PebblesTheme { LitStoneSeeds() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun LitStoneSeedsDark() {
    PebblesTheme { LitStoneSeeds() }
}
