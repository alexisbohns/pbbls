package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.path.create.PebbleForm
import app.pbbls.android.features.path.models.Domain
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.EmotionWithPalette
import app.pbbls.android.features.path.models.PebbleCollection
import app.pbbls.android.features.path.models.PebbleDraft
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/**
 * Previews for the edit surface (#542): the pure [PebbleForm] (C) driven with a
 * populated [renderSvg] header at all three `sizeGroup` heights (small 180 /
 * medium 220 / large 260 dp) — the design's required pre-wire proof of the render
 * header at every height (risk 2). Driven with fixtures so no services are
 * needed; the service-backed [app.pbbls.android.features.path.EditPebbleScreen]
 * is deliberately not previewed. A non-null [strokeColor] makes the header render.
 */
private fun palette(
    primary: String,
    secondary: String,
    light: String,
    surface: String,
    dark: String = "#2A2138FF",
    shaded: String = "#4A3A5CFF",
): EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = primary,
            secondaryHex = secondary,
            lightHex = light,
            surfaceHex = surface,
            darkHex = dark,
            shadedHex = shaded,
        ),
    )

private val joyPalette = palette("#7B5E99FF", "#AE91CCFF", "#F2EFF5FF", "#7B5E991A")

// Any 6-digit hex renders the header stroke in preview (iOS parity uses the
// palette strokeHex; the light value is fine for both preview themes).
private val strokeColor = joyPalette.strokeHex(false)

// In-range (0..200) stroke so the glyph cell carves a distinct mark.
private val waveStroke = GlyphStroke(d = "M 20 120 Q 100 40 180 120", width = 6.0)

private fun glyphOf(
    id: String,
    name: String?,
    strokes: List<GlyphStroke>,
): Glyph = Glyph(id = id, name = name, strokes = strokes, viewBox = "0 0 200 200")

private val sampleGlyph = glyphOf("glyph-1", "Spiral", listOf(waveStroke))

private fun soul(
    id: String,
    name: String,
    strokes: List<GlyphStroke>,
    count: Int,
): SoulWithGlyph =
    SoulWithGlyph(
        id = id,
        name = name,
        glyphId = "g-$id",
        glyph = glyphOf("g-$id", null, strokes),
        pebblesCount = count,
    )

private val souls =
    listOf(
        soul("s1", "Alex", listOf(waveStroke), 12),
        soul("s2", "Sam", listOf(waveStroke), 5),
    )

private val domains =
    listOf(
        Domain(id = "d-work", slug = "work", name = "Work", label = "Work"),
        Domain(id = "d-health", slug = "health", name = "Health", label = "Health"),
    )

private val collections =
    listOf(
        PebbleCollection(id = "c-wins", name = "Wins"),
    )

private val joyEmotion =
    EmotionWithPalette(
        id = "e-joy",
        slug = "joyful",
        name = "Joyful",
        emoji = "😊",
        categoryId = "cat-joy",
        categorySlug = "joy",
        categoryName = "Joy",
        palette = joyPalette,
    )

private val fixedWhen: OffsetDateTime = OffsetDateTime.parse("2026-07-08T14:23:00Z")

private fun draftWith(valence: Valence): PebbleDraft =
    PebbleDraft(
        happenedAt = fixedWhen,
        name = "Shipped the Android edit flow",
        description = "Prefilled from the loaded pebble detail.",
        emotionId = "e-joy",
        domainId = "d-work",
        valence = valence,
        soulIds = listOf("s1", "s2"),
        collectionId = "c-wins",
        glyphId = "glyph-1",
    )

@Composable
private fun EditFormPreview(
    draft: PebbleDraft,
    renderSvg: String,
    renderHeight: Dp,
    saveError: String? = null,
) {
    val system = PebblesTheme.colors.system
    PebbleForm(
        draft = draft,
        onDraftChange = {},
        domains = domains,
        souls = souls,
        collections = collections,
        selectedEmotion = joyEmotion,
        selectedGlyph = sampleGlyph,
        onGlyphPicked = {},
        saveError = saveError,
        renderSvg = renderSvg,
        strokeColor = strokeColor,
        renderHeight = renderHeight,
        modifier = Modifier.fillMaxSize().background(system.background),
    )
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun EditFormSmallLight() {
    PebblesTheme {
        EditFormPreview(draftWith(Valence.LOWLIGHT_SMALL), PebbleSvgFixtures.smallLowlight, 180.dp)
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun EditFormSmallDark() {
    PebblesTheme {
        EditFormPreview(draftWith(Valence.LOWLIGHT_SMALL), PebbleSvgFixtures.smallLowlight, 180.dp)
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun EditFormMediumLight() {
    PebblesTheme {
        EditFormPreview(draftWith(Valence.NEUTRAL_MEDIUM), PebbleSvgFixtures.mediumNeutral, 220.dp)
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun EditFormMediumDark() {
    PebblesTheme {
        EditFormPreview(draftWith(Valence.NEUTRAL_MEDIUM), PebbleSvgFixtures.mediumNeutral, 220.dp)
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun EditFormLargeLight() {
    PebblesTheme {
        EditFormPreview(draftWith(Valence.HIGHLIGHT_LARGE), PebbleSvgFixtures.largeHighlight, 260.dp)
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun EditFormLargeDark() {
    PebblesTheme {
        EditFormPreview(draftWith(Valence.HIGHLIGHT_LARGE), PebbleSvgFixtures.largeHighlight, 260.dp)
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun EditFormErrorLight() {
    PebblesTheme {
        EditFormPreview(
            draftWith(Valence.NEUTRAL_MEDIUM),
            PebbleSvgFixtures.mediumNeutral,
            220.dp,
            "Couldn't save your pebble. Please try again.",
        )
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun EditFormErrorDark() {
    PebblesTheme {
        EditFormPreview(
            draftWith(Valence.NEUTRAL_MEDIUM),
            PebbleSvgFixtures.mediumNeutral,
            220.dp,
            "Couldn't save your pebble. Please try again.",
        )
    }
}
