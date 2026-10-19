package app.pbbls.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.common.UuidHash
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.data.rememberTapHaptics
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.ui.render.stone.LitStone
import app.pbbls.android.core.ui.render.stone.StonePressState
import app.pbbls.android.core.ui.render.stone.StoneRipple
import app.pbbls.android.core.ui.render.stone.StoneSeal
import app.pbbls.android.core.ui.render.stone.rememberStonePressState
import kotlinx.coroutines.launch

/**
 * The lit stone gallery (#1122 Part 3), shared by `LitStoneScreenshots` and the
 * Spine lab: every valence in the pride palette with the fixture glyph, each
 * beside its seal, the blank moment, one gem held under reduced motion, and the
 * same medium gem under four seeds. In the lab ([pressable]) a tap presses a
 * stone and mounts its ripple; in a preview nothing is pressed, so the glint and
 * the ripple draw nothing.
 */
internal val stoneLabPridePalette: EmotionPalette =
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

/** The previews' fixture glyph (`MomentPreviewFixtures.pebbleGlyph()`, which debug sources cannot see). */
private val labGlyph =
    PebbleGlyph(
        id = "glyph-1",
        name = null,
        strokes = listOf(GlyphStroke(d = "M 20 100 Q 100 20 180 100", width = 6.0)),
        viewBox = "0 0 200 200",
        source = "domain",
    )

private const val FIXTURE_UUID = "0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b"

internal val stoneLabFixtureSeed: Long = UuidHash.fnv1a(FIXTURE_UUID)

private val seedUuids =
    listOf(
        FIXTURE_UUID,
        "5b1f3a2c-9d8e-4f7a-b6c5-d4e3f2a1b0c9",
        "a7c2e9f4-3b1d-4e8a-9f6c-2d5b8a1e4c7f",
        "f0e1d2c3-b4a5-4968-8776-655443322110",
    )

/** A lit stone that, when [pressable], plays its press and ripple on a tap. */
@Composable
internal fun LabStone(
    valence: Valence,
    palette: EmotionPalette?,
    seed: Long,
    pressable: Boolean,
    press: StonePressState = rememberStonePressState(),
) {
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val haptics = rememberTapHaptics()
    Box(
        if (pressable) {
            Modifier.clickable(interactionSource = null, indication = null) {
                haptics(TapHaptic.SELECTION)
                scope.launch { press.press(reduceMotion) }
            }
        } else {
            Modifier
        },
    ) {
        press.rippleToken?.let { token ->
            StoneRipple(valence = valence, blank = palette == null, token = token, onDone = press::rippleDone)
        }
        LitStone(
            valence = valence,
            palette = palette,
            glyph = labGlyph,
            seed = seed,
            press = press,
        )
    }
}

@Composable
private fun StoneAndSeal(
    valence: Valence,
    palette: EmotionPalette?,
    seed: Long,
    pressable: Boolean,
) {
    val ground = PebblesTheme.spine.colors.ground
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabStone(valence = valence, palette = palette, seed = seed, pressable = pressable)
        StoneSeal(
            valence = valence,
            palette = palette,
            glyph = labGlyph,
            knockout = ground,
            modifier = Modifier.width(24.dp),
        )
    }
}

@Composable
internal fun LitStoneGallery(pressable: Boolean) {
    Column(
        modifier =
            Modifier
                .background(PebblesTheme.spine.colors.ground)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Valence.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { valence ->
                    StoneAndSeal(valence = valence, palette = stoneLabPridePalette, seed = stoneLabFixtureSeed, pressable = pressable)
                }
            }
        }
        StoneAndSeal(valence = Valence.NEUTRAL_MEDIUM, palette = null, seed = stoneLabFixtureSeed, pressable = pressable)
        LabStone(
            valence = Valence.HIGHLIGHT_MEDIUM,
            palette = stoneLabPridePalette,
            seed = stoneLabFixtureSeed,
            pressable = pressable,
            press = remember { StonePressState.stillTouched() },
        )
    }
}

@Composable
internal fun LitStoneSeeds(pressable: Boolean) {
    Row(
        modifier =
            Modifier
                .background(PebblesTheme.spine.colors.ground)
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        seedUuids.forEach { uuid ->
            LabStone(valence = Valence.HIGHLIGHT_MEDIUM, palette = stoneLabPridePalette, seed = UuidHash.fnv1a(uuid), pressable = pressable)
        }
    }
}
