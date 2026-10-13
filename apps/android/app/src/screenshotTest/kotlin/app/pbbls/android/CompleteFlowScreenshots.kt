package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.PebbleCollection
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.features.path.complete.CompleteFlowContent
import app.pbbls.android.features.path.complete.CompleteFlowModel
import app.pbbls.android.features.path.complete.CompleteFlowState
import app.pbbls.android.features.path.complete.CompleteFlowUiState
import app.pbbls.android.features.path.complete.CompleteResult
import app.pbbls.android.features.path.complete.CompleteStep
import com.android.tools.screenshot.PreviewTest

/**
 * Previews for the Complete flow (#1048), one per step through the stateless
 * [CompleteFlowContent], plus the privacy error and the success screen with
 * and without karma and glyph. The emotion step is not here: its body reads
 * `LocalEmotionPaletteService`, which a preview cannot provide, and
 * `CreateScreenshots` already renders that body (`EmotionPicker*`).
 *
 * Render-to-view, not a regression gate — see `apps/android/CLAUDE.md`.
 */
private val waveStrokes = listOf(GlyphStroke(d = "M 20 120 Q 100 40 180 120", width = 6.0))

private val completeDomains =
    listOf(
        Domain(id = "d-health", slug = "health", name = "Health", label = "Your body, energy, and physical well-being"),
        Domain(id = "d-work", slug = "work", name = "Work", label = "Your job, career, and professional life"),
    )

private val completeCollections =
    listOf(
        PebbleCollection(id = "c-wins", name = "Wins"),
        PebbleCollection(id = "c-travel", name = "Travel"),
    )

private val completeSouls =
    listOf(
        SoulWithGlyph(
            id = "s1",
            name = "Alex",
            glyphId = "g-s1",
            glyph = Glyph(id = "g-s1", name = null, strokes = waveStrokes, viewBox = "0 0 200 200"),
        ),
        SoulWithGlyph(
            id = "s2",
            name = "Sam",
            glyphId = "g-s2",
            glyph = Glyph(id = "g-s2", name = null, strokes = waveStrokes, viewBox = "0 0 200 200"),
        ),
    )

private val completePalette: EmotionPalette? =
    EmotionPalette.fromHex(
        primaryHex = "#7B5E99FF",
        secondaryHex = "#AE91CCFF",
        lightHex = "#F2EFF5FF",
        surfaceHex = "#7B5E991A",
        darkHex = "#2A2138FF",
        shadedHex = "#4A3A5CFF",
    )

private val answered =
    PebbleAnswers(
        valence = Valence.HIGHLIGHT_MEDIUM,
        emotionId = "e-joy",
        domainId = "d-health",
        soulIds = listOf("s1"),
        collectionId = null,
        visibility = Visibility.SECRET,
    )

@Composable
private fun CompletePreview(
    step: CompleteStep,
    answers: PebbleAnswers = answered,
    @StringRes errorRes: Int? = null,
    completed: CompleteResult? = null,
    momentName: String? = "Swim at the lake",
) {
    val flow = CompleteFlowState(answers = answers, step = step, completed = completed, errorRes = errorRes)
    // Previews are not interactions: the machine is there for the callbacks only, so it never buzzes.
    val model = CompleteFlowModel(haptic = {}, initial = flow)
    CompositionLocalProvider(
        LocalReferenceDataService provides
            PreviewReferenceData(domains = completeDomains, souls = completeSouls, collections = completeCollections),
    ) {
        CompleteFlowContent(
            uiState = CompleteFlowUiState(flow = flow, momentName = momentName),
            model = model,
            domains = completeDomains,
            collections = completeCollections,
            palette = completePalette,
            onClose = {},
            onComplete = {},
            onExit = {},
        )
    }
}

private fun success(
    karma: Int?,
    glyph: PebbleGlyph?,
) = CompleteResult(pebbleId = "p-1", karmaDelta = karma, glyph = glyph)

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@PreviewLargeFontTall
@Composable
fun Complete_Valence() {
    PebblesTheme { CompletePreview(CompleteStep.VALENCE, answers = PebbleAnswers(valence = Valence.NEUTRAL_MEDIUM)) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@PreviewWideTall
@Composable
fun Complete_Domain() {
    PebblesTheme { CompletePreview(CompleteStep.DOMAIN) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Complete_DomainDark() {
    PebblesTheme { CompletePreview(CompleteStep.DOMAIN) }
}

/** One soul picked, so the optional step reads Done rather than Skip. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@PreviewLargeFontTall
@Composable
fun Complete_Souls() {
    PebblesTheme { CompletePreview(CompleteStep.SOULS) }
}

/** Nothing picked: Skip. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@Composable
fun Complete_Collection() {
    PebblesTheme { CompletePreview(CompleteStep.COLLECTION) }
}

/** The last step: "Make it a pebble" against the grade. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@PreviewLargeFontTall
@Composable
fun Complete_Privacy() {
    PebblesTheme { CompletePreview(CompleteStep.PRIVACY) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Complete_PrivacyDark() {
    PebblesTheme { CompletePreview(CompleteStep.PRIVACY) }
}

/** A refusal keeps the user on privacy with the R10 copy. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@PreviewLargeFontTall
@Composable
fun Complete_PrivacyError() {
    PebblesTheme { CompletePreview(CompleteStep.PRIVACY, errorRes = R.string.pebble_error_collection_gone) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@Composable
fun Complete_Success() {
    PebblesTheme { CompletePreview(CompleteStep.SUCCESS, completed = success(4, MomentPreviewFixtures.pebbleGlyph())) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Complete_SuccessDark() {
    PebblesTheme { CompletePreview(CompleteStep.SUCCESS, completed = success(4, MomentPreviewFixtures.pebbleGlyph())) }
}

/** No karma row and no derived glyph: no karma line, a bare stone, and a photo-only moment's "Untitled" (R3, R12). */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@PreviewLargeFontTall
@Composable
fun Complete_SuccessNoKarmaNoGlyph() {
    PebblesTheme { CompletePreview(CompleteStep.SUCCESS, completed = success(null, null), momentName = null) }
}
