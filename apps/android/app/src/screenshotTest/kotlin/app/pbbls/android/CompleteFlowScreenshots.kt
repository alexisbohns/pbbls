package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.Spacing
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
import app.pbbls.android.features.path.create.pickers.EmotionPickerBody
import app.pbbls.android.features.path.flow.RecordFlowChrome
import app.pbbls.android.features.path.flow.RecordStepScaffold
import app.pbbls.android.features.path.flow.steps.RecordLifeStep
import app.pbbls.android.features.path.valence.ValenceHelpContent
import app.pbbls.android.navigation.CompleteOrigin
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDateTime
import java.time.ZoneId

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
        Domain(
            id = "d-health",
            slug = "health",
            name = "Health",
            label = "Your body, energy, and physical well-being",
            strokes = waveStrokes,
            viewBox = "0 0 200 200",
        ),
        Domain(id = "d-work", slug = "work", name = "Work", label = "Your job, career, and professional life"),
        Domain(
            id = "d-family",
            slug = "family",
            name = "Family",
            label = "The people you call home",
            strokes = waveStrokes,
            viewBox = "0 0 200 200",
        ),
        Domain(
            id = "d-friends",
            slug = "friends",
            name = "Friends",
            label = "The people you chose",
            strokes = waveStrokes,
            viewBox = "0 0 200 200",
        ),
        Domain(
            id = "d-learning",
            slug = "learning",
            name = "Learning",
            label = "What you are studying or practising",
            strokes = waveStrokes,
            viewBox = "0 0 200 200",
        ),
        Domain(id = "d-home", slug = "home", name = "Home", label = "Your place and what keeps it going"),
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
            pebblesCount = 7,
        ),
        SoulWithGlyph(
            id = "s2",
            name = "Sam",
            glyphId = "g-s2",
            glyph = Glyph(id = "g-s2", name = null, strokes = waveStrokes, viewBox = "0 0 200 200"),
            pebblesCount = 4,
        ),
        SoulWithGlyph(
            id = "s3",
            name = "Camille",
            glyphId = "g-s3",
            glyph = Glyph(id = "g-s3", name = null, strokes = waveStrokes, viewBox = "0 0 200 200"),
            pebblesCount = 3,
        ),
        SoulWithGlyph(
            id = "s4",
            name = "Jo",
            glyphId = "g-s4",
            glyph = Glyph(id = "g-s4", name = null, strokes = waveStrokes, viewBox = "0 0 200 200"),
            pebblesCount = 2,
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
        domainIds = listOf("d-health"),
        soulIds = listOf("s1"),
        collectionId = null,
        visibility = Visibility.SECRET,
    )

private val lifeAnswered = answered.copy(domainIds = listOf("d-health", "d-family"), soulIds = listOf("s1"))

/** The Life page under the flow's chrome. Not through [CompleteFlowContent]: the caption is the step's own state, seeded here. */
@Composable
private fun LifePreview(
    answers: PebbleAnswers,
    souls: List<SoulWithGlyph> = completeSouls,
    recent: List<String> = emptyList(),
    @StringRes errorRes: Int? = null,
    caption: String? = null,
    loaded: Boolean = true,
) {
    CompositionLocalProvider(LocalReferenceDataService provides PreviewReferenceData(domains = completeDomains, souls = souls)) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
            RecordFlowChrome(position = 3, total = 3, canGoBack = true, onBack = {}, onClose = {})
            RecordLifeStep(
                answers = answers,
                domains = completeDomains,
                recent = recent.mapNotNull { id -> completeDomains.firstOrNull { it.id == id } },
                souls = souls,
                isSoulsLoaded = loaded,
                error = errorRes?.let { stringResource(it) },
                isCompleting = false,
                hapticsOn = false,
                onToggleDomain = {},
                onToggleSoul = {},
                onSetSouls = {},
                onComplete = {},
                initialCaptionDomainId = caption,
            )
        }
    }
}

@Composable
private fun CompletePreview(
    step: CompleteStep,
    answers: PebbleAnswers = answered,
    @StringRes errorRes: Int? = null,
    completed: CompleteResult? = null,
    momentName: String? = "Swim at the lake",
    origin: CompleteOrigin = CompleteOrigin.CARD,
    ui: (CompleteFlowUiState) -> CompleteFlowUiState = { it },
    collections: List<PebbleCollection> = completeCollections,
    onRecordAnother: (() -> Unit)? = null,
) {
    val flow = CompleteFlowState(answers = answers, step = step, completed = completed, errorRes = errorRes)
    // Previews are not interactions: the machine is there for the callbacks only, so it never buzzes.
    val model = CompleteFlowModel(haptic = {}, initial = flow)
    CompositionLocalProvider(
        LocalReferenceDataService provides
            PreviewReferenceData(domains = completeDomains, souls = completeSouls, collections = completeCollections),
    ) {
        CompleteFlowContent(
            uiState = ui(CompleteFlowUiState(flow = flow, momentName = momentName, isMomentLoaded = true)),
            origin = origin,
            model = model,
            domains = completeDomains,
            souls = completeSouls,
            isSoulsLoaded = true,
            palette = completePalette,
            onClose = {},
            onComplete = {},
            onExit = {},
            hapticsOn = false,
            collections = collections,
            onRecordAnother = onRecordAnother,
        )
    }
}

/** Wednesday 14 October 2026 (checked with `date`), noon in the host's own zone, so the dot lands on W wherever it renders. */
private val richerDay = LocalDateTime.of(2026, 10, 14, 12, 0).atZone(ZoneId.systemDefault()).toOffsetDateTime()

private fun CompleteFlowUiState.richer(
    hasWords: Boolean = false,
    editErrorRes: Int? = null,
    isEditing: Boolean = false,
) = copy(happenedAt = richerDay, hasWords = hasWords, editErrorRes = editErrorRes, isEditing = isEditing)

@Composable
private fun RicherPreview(
    answers: PebbleAnswers = answered,
    karma: Int? = 3,
    momentName: String? = "Coffee with Léa",
    collections: List<PebbleCollection> = completeCollections,
    hasWords: Boolean = false,
    @StringRes editErrorRes: Int? = null,
    origin: CompleteOrigin = CompleteOrigin.CAPTURE,
) {
    PebblesTheme {
        CompletePreview(
            CompleteStep.SUCCESS,
            answers = answers,
            completed = success(karma, MomentPreviewFixtures.pebbleGlyph()),
            momentName = momentName,
            origin = origin,
            collections = collections,
            ui = { it.richer(hasWords = hasWords, editErrorRes = editErrorRes) },
            onRecordAnother = if (origin == CompleteOrigin.CARD) null else ({}),
        )
    }
}

private fun success(
    karma: Int?,
    glyph: PebbleGlyph?,
) = CompleteResult(pebbleId = "p-1", karmaDelta = karma, glyph = glyph)

/** The empty step (D7): all nine stones, the labels, the ghost stone and its prompt, Continue off. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@PreviewLargeFontTall
@Composable
fun Complete_Valence() {
    PebblesTheme { CompletePreview(CompleteStep.VALENCE, answers = PebbleAnswers()) }
}

/** The Pixel 8's height: the whole fan, the prompt and the "How to read this" callout above Continue. */
@PreviewTest
@Preview(showBackground = true, heightDp = 915)
@Composable
fun Complete_ValenceTall() {
    PebblesTheme { CompletePreview(CompleteStep.VALENCE, answers = PebbleAnswers()) }
}

@PreviewTest
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@Composable
fun Complete_ValenceFr() {
    PebblesTheme { CompletePreview(CompleteStep.VALENCE, answers = PebbleAnswers()) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Complete_ValenceDark() {
    PebblesTheme { CompletePreview(CompleteStep.VALENCE, answers = PebbleAnswers()) }
}

/** A stone chosen, after a save: the roll under the fan, the "Moment saved" subtitle, Continue on. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@Composable
fun Complete_ValencePicked() {
    PebblesTheme {
        CompletePreview(
            CompleteStep.VALENCE,
            answers = PebbleAnswers(valence = Valence.HIGHLIGHT_MEDIUM),
            origin = CompleteOrigin.CAPTURE,
        )
    }
}

/**
 * The help sheet's content in a sheet's place, at the Pixel 8's height: a sheet cannot render in a
 * preview, and its Got it must sit inside the content, not under the screen edge.
 */
@Composable
private fun HelpInSheet() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        ) {
            ValenceHelpContent(onGotIt = {}, modifier = Modifier.padding(top = Spacing.lg))
        }
    }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 915)
@Preview(name = "fr", showBackground = true, heightDp = 915, locale = "fr")
@Preview(name = "dark", showBackground = true, heightDp = 915, uiMode = UI_MODE_NIGHT_YES)
@PreviewLargeFontTall
@Composable
fun Complete_ValenceHelp() {
    PebblesTheme { HelpInSheet() }
}

/** The palette fetch failed and nothing is cached: the message and Retry in place of the grid (D9). */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@PreviewLargeFontTall
@Composable
fun Complete_EmotionFailed() {
    PebblesTheme {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            RecordStepScaffold(title = stringResource(R.string.record_emotion_title), contentScrolls = false) {
                EmotionPickerBody(groups = emptyList(), stagedId = null, onToggle = {}, loadFailed = true, onRetry = {})
            }
        }
    }
}

/** What part of life, and who was there: two domains and one person picked, the last tap's description in the bar. */
@PreviewTest
@Preview(showBackground = true, heightDp = 780)
@Preview(name = "fr", showBackground = true, heightDp = 780, locale = "fr")
@Preview(name = "dark", showBackground = true, heightDp = 780, uiMode = UI_MODE_NIGHT_YES)
@PreviewLargeFontTall
@PreviewWideTall
@Composable
fun Complete_Life() {
    PebblesTheme { LifePreview(lifeAnswered, caption = "d-family") }
}

/** RECENT above ALL, a domain in both: the same selection on each copy. */
@PreviewTest
@Preview(showBackground = true, heightDp = 780)
@Composable
fun Complete_LifeRecent() {
    PebblesTheme { LifePreview(lifeAnswered, recent = listOf("d-family", "d-learning"), caption = "d-family") }
}

/** Nobody on the account yet: one Add people tile, no caption before a tap. */
@PreviewTest
@Preview(showBackground = true, heightDp = 780)
@PreviewLargeFontTall
@Composable
fun Complete_LifeNoSouls() {
    PebblesTheme { LifePreview(lifeAnswered.copy(soulIds = emptyList()), souls = emptyList()) }
}

/** The moment is saved and the pebble is not: the line in the bar, the button ready to retry. */
@PreviewTest
@Preview(showBackground = true, heightDp = 780)
@Preview(name = "fr", showBackground = true, heightDp = 780, locale = "fr")
@PreviewLargeFontTall
@Composable
fun Complete_LifeError() {
    PebblesTheme { LifePreview(lifeAnswered, errorRes = R.string.complete_partial_failure, caption = "d-family") }
}

/** Souls still loading: the strip keeps its height and claims nobody is missing; nothing is picked, so the button waits. */
@PreviewTest
@Preview(showBackground = true, heightDp = 780)
@Composable
fun Complete_LifeLoading() {
    PebblesTheme { LifePreview(PebbleAnswers(), souls = emptyList(), loaded = false) }
}

/** Make it richer (D12): the stone, the name and karma, the week, three rows, Done and Record another. */
@PreviewTest
@Preview(showBackground = true, heightDp = 915)
@Preview(name = "fr", showBackground = true, heightDp = 915, locale = "fr")
@Preview(name = "dark", showBackground = true, heightDp = 915, uiMode = UI_MODE_NIGHT_YES)
@PreviewLargeFontTall
@Composable
fun Complete_Richer() {
    RicherPreview()
}

/** A high-distress pebble: the calm sentence, no karma, Get support above Done; the rows still show. */
@PreviewTest
@Preview(showBackground = true, heightDp = 915)
@Preview(name = "fr", showBackground = true, heightDp = 915, locale = "fr")
@Composable
fun Complete_RicherQuiet() {
    RicherPreview(answers = answered.copy(valence = Valence.LOWLIGHT_LARGE), karma = 5)
}

/** An account with no collections: that row is gone. A pebble from the card offers no Record another. */
@PreviewTest
@Preview(showBackground = true, heightDp = 915)
@Composable
fun Complete_RicherNoCollections() {
    RicherPreview(collections = emptyList(), origin = CompleteOrigin.CARD)
}

/** The moment already has words (or just got them): that row is gone; a collection is set; the last edit failed. */
@PreviewTest
@Preview(showBackground = true, heightDp = 915)
@PreviewLargeFontTall
@Composable
fun Complete_RicherWordsDone() {
    RicherPreview(
        answers = answered.copy(collectionId = "c-wins", visibility = Visibility.PUBLIC),
        hasWords = true,
        karma = null,
        momentName = null,
        editErrorRes = R.string.moment_save_error_generic,
    )
}
