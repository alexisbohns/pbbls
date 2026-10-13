package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.EmotionWithPalette
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.PebbleCollection
import app.pbbls.android.core.model.PebbleDraft
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.features.path.EditMomentContent
import app.pbbls.android.features.path.EditMomentUiState
import app.pbbls.android.features.path.EditPebbleContent
import app.pbbls.android.features.path.EditPebbleUiState
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/**
 * Previews for the two edit screens (#542, #1048 R5): Edit pebble's stateless
 * [EditPebbleContent] with its stone header at all three `sizeGroup` heights
 * (small 180 / medium 220 / large 260 dp) and its error, and Edit moment's
 * [EditMomentContent] with and without content. Driven with fixtures and a
 * fixed reference-data cache, so no services are needed.
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

// In-range (0..200) stroke so the glyph cell carves a distinct mark.
private val waveStroke = GlyphStroke(d = "M 20 120 Q 100 40 180 120", width = 6.0)

private fun glyphOf(
    id: String,
    name: String?,
    strokes: List<GlyphStroke>,
): Glyph = Glyph(id = id, name = name, strokes = strokes, viewBox = "0 0 200 200")

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
        description = "Prefilled from the loaded moment.",
        emotionId = "e-joy",
        domainId = "d-work",
        valence = valence,
        soulIds = listOf("s1", "s2"),
        collectionId = "c-wins",
    )

private val editRefs = PreviewReferenceData(domains = domains, souls = souls, collections = collections)

@Composable
private fun EditPebblePreview(
    draft: PebbleDraft,
    @StringRes saveErrorRes: Int? = null,
) {
    CompositionLocalProvider(LocalReferenceDataService provides editRefs) {
        EditPebbleContent(
            uiState = EditPebbleUiState.Content(draft = draft, saveErrorRes = saveErrorRes),
            palette = joyPalette,
            selectedEmotion = joyEmotion,
            onDraftChange = {},
            onCancel = {},
            onSave = {},
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@Composable
fun EditFormSmallLight() {
    PebblesTheme { EditPebblePreview(draftWith(Valence.LOWLIGHT_SMALL)) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun EditFormSmallDark() {
    PebblesTheme { EditPebblePreview(draftWith(Valence.LOWLIGHT_SMALL)) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun EditFormMediumLight() {
    PebblesTheme { EditPebblePreview(draftWith(Valence.NEUTRAL_MEDIUM)) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun EditFormMediumDark() {
    PebblesTheme { EditPebblePreview(draftWith(Valence.NEUTRAL_MEDIUM)) }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@Composable
fun EditFormLargeLight() {
    PebblesTheme { EditPebblePreview(draftWith(Valence.HIGHLIGHT_LARGE)) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun EditFormLargeDark() {
    PebblesTheme { EditPebblePreview(draftWith(Valence.HIGHLIGHT_LARGE)) }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@Composable
fun EditFormErrorLight() {
    PebblesTheme { EditPebblePreview(draftWith(Valence.NEUTRAL_MEDIUM), R.string.moment_save_error_generic) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun EditFormErrorDark() {
    PebblesTheme { EditPebblePreview(draftWith(Valence.NEUTRAL_MEDIUM), R.string.moment_save_error_generic) }
}

/** Edit pebble (R5): the stone, then mood, souls and the optional section; no moment fields, no photo. */
@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun EditPebble_Content() {
    PebblesTheme { EditPebblePreview(draftWith(Valence.HIGHLIGHT_MEDIUM)) }
}

/** Edit moment (R5): when, name, description and the photo section only. */
@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun EditMoment_Content() {
    PebblesTheme {
        EditMomentContent(
            uiState = EditMomentUiState.Content(draft = draftWith(Valence.HIGHLIGHT_MEDIUM)),
            onDraftChange = {},
            onCancel = {},
            onSave = {},
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** A blank name and no photo: Save disabled, the hint shown (R6). */
@PreviewTest
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun EditMoment_NeedsContent() {
    PebblesTheme {
        EditMomentContent(
            uiState = EditMomentUiState.Content(draft = draftWith(Valence.HIGHLIGHT_MEDIUM).copy(name = "")),
            onDraftChange = {},
            onCancel = {},
            onSave = {},
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
