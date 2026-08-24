package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.path.create.pickers.DomainPickerContent
import app.pbbls.android.features.path.models.Domain
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.PebbleCollection
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.Visibility
import app.pbbls.android.features.path.record.CloseConfirmDialog
import app.pbbls.android.features.path.record.RecordFlowChrome
import app.pbbls.android.features.path.record.RecordStep
import app.pbbls.android.features.path.record.RecordStepAction
import app.pbbls.android.features.path.record.RecordStepScaffold
import app.pbbls.android.features.path.record.steps.RecordCollectionStep
import app.pbbls.android.features.path.record.steps.RecordNameStep
import app.pbbls.android.features.path.record.steps.RecordPhotoStep
import app.pbbls.android.features.path.record.steps.RecordPrivacyStep
import app.pbbls.android.features.path.record.steps.RecordSuccessStep
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * Previews for the step-by-step record flow (M58) — the chrome, the scaffold,
 * and the steps that are pure enough to drive with fixtures: photo, name,
 * domain, collection, privacy, and success. The picker-backed steps (valence,
 * emotion, souls, glyph) render bodies `CreateScreenshots` already previews, and
 * `RecordFlowScreen` itself is service-backed, so neither is duplicated here.
 *
 * Render-to-view, not a regression gate — see `apps/android/CLAUDE.md`.
 */
private val domainStrokes = listOf(GlyphStroke(d = "M 40 150 L 100 50 L 160 150", width = 6.0))
private val waveStrokes = listOf(GlyphStroke(d = "M 20 120 Q 100 40 180 120", width = 6.0))

private val previewDomains =
    listOf(
        Domain(
            id = "d-health",
            slug = "health",
            name = "Health",
            label = "Your body, energy, and physical well-being",
            strokes = domainStrokes,
            viewBox = "0 0 200 200",
        ),
        Domain(
            id = "d-work",
            slug = "work",
            name = "Work",
            label = "Your job, career, and professional life",
            strokes = waveStrokes,
            viewBox = "0 0 200 200",
        ),
        // No default glyph: the slot stays empty rather than inventing a mark.
        Domain(
            id = "d-travel",
            slug = "travel",
            name = "Travel",
            label = "Exploring new places and horizons",
        ),
    )

private val previewCollections =
    listOf(
        PebbleCollection(id = "c-wins", name = "Wins"),
        PebbleCollection(id = "c-travel", name = "Travel"),
    )

private val previewPalette: EmotionPalette? =
    EmotionPalette.fromHex(
        primaryHex = "#7B5E99FF",
        secondaryHex = "#AE91CCFF",
        lightHex = "#F2EFF5FF",
        surfaceHex = "#7B5E991A",
        darkHex = "#2A2138FF",
        shadedHex = "#4A3A5CFF",
    )

private const val PREVIEW_RENDER_SVG =
    """<svg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">""" +
        """<path d="M 40 150 L 100 50 L 160 150" stroke="currentColor" stroke-width="6" fill="none"/></svg>"""

/** The scaffold plus chrome, so a step preview shows the screen the user sees. */
@Composable
private fun StepPreview(
    step: RecordStep,
    action: RecordStepAction? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PebblesTheme.colors.system.background),
    ) {
        RecordFlowChrome(step = step, onBack = {}, onClose = {})
        RecordStepScaffold(
            title = stringResource(step.titleRes),
            subtitle = step.subtitleRes?.let { stringResource(it) },
            action = action,
            content = content,
        )
    }
}

@Composable
private fun PhotoStepPreview() {
    StepPreview(
        step = RecordStep.PHOTO,
        action = RecordStepAction.Text(stringResource(R.string.record_action_skip)) {},
    ) {
        RecordPhotoStep(snap = null, onPick = {}, onRetry = {}, onRemove = {})
    }
}

@Composable
private fun NameStepPreview(name: String) {
    StepPreview(
        step = RecordStep.NAME,
        action =
            RecordStepAction.Primary(
                label = stringResource(R.string.record_action_continue),
                enabled = name.isNotBlank(),
                isLoading = false,
                onClick = {},
            ),
    ) {
        RecordNameStep(name = name, limit = 40, onChange = {})
    }
}

@Composable
private fun DomainStepPreview() {
    StepPreview(step = RecordStep.DOMAIN) {
        DomainPickerContent(domains = previewDomains, selectedId = "d-health", onSelect = {})
    }
}

@Composable
private fun CollectionStepPreview(collections: List<PebbleCollection>) {
    StepPreview(
        step = RecordStep.COLLECTION,
        action = RecordStepAction.Text(stringResource(R.string.record_action_skip)) {},
    ) {
        RecordCollectionStep(collections = collections, selectedId = "c-wins", onSelect = {})
    }
}

@Composable
private fun PrivacyStepPreview(publishError: String?) {
    StepPreview(
        step = RecordStep.PRIVACY,
        action =
            RecordStepAction.Primary(
                label = stringResource(R.string.record_action_publish),
                enabled = true,
                isLoading = false,
                onClick = {},
            ),
    ) {
        RecordPrivacyStep(
            selected = Visibility.SECRET,
            onSelect = {},
            publishError = publishError,
        )
    }
}

@Composable
private fun SuccessStepPreview(renderSvg: String?) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PebblesTheme.colors.system.background),
    ) {
        RecordSuccessStep(
            name = "Shipped the Android record flow",
            renderSvg = renderSvg,
            karmaDelta = 12,
            valence = Valence.HIGHLIGHT_MEDIUM,
            palette = previewPalette,
            onExit = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun RecordChromeStepsLight() {
    PebblesTheme {
        Column(Modifier.background(PebblesTheme.colors.system.background)) {
            RecordStep.counted.forEach { step ->
                RecordFlowChrome(step = step, onBack = {}, onClose = {})
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun RecordPhotoStepLight() {
    PebblesTheme { PhotoStepPreview() }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun RecordPhotoStepDark() {
    PebblesTheme { PhotoStepPreview() }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun RecordNameStepEmptyLight() {
    PebblesTheme { NameStepPreview("") }
}

/** Past the countdown threshold, so the remaining-characters number is visible. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun RecordNameStepNearLimitLight() {
    PebblesTheme { NameStepPreview("Shipped the Android record flow at") }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun RecordNameStepNearLimitDark() {
    PebblesTheme { NameStepPreview("Shipped the Android record flow at") }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun RecordDomainStepLight() {
    PebblesTheme { DomainStepPreview() }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun RecordDomainStepDark() {
    PebblesTheme { DomainStepPreview() }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun RecordCollectionStepLight() {
    PebblesTheme { CollectionStepPreview(previewCollections) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun RecordCollectionStepEmptyLight() {
    PebblesTheme { CollectionStepPreview(emptyList()) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun RecordPrivacyStepLight() {
    PebblesTheme { PrivacyStepPreview(null) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun RecordPrivacyStepDark() {
    PebblesTheme { PrivacyStepPreview(null) }
}

/** A hard failure keeps the user here, with the mapped message in the step. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun RecordPrivacyStepErrorLight() {
    PebblesTheme { PrivacyStepPreview("Couldn't save your pebble. Please try again.") }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun RecordSuccessStepLight() {
    PebblesTheme { SuccessStepPreview(PREVIEW_RENDER_SVG) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun RecordSuccessStepDark() {
    PebblesTheme { SuccessStepPreview(PREVIEW_RENDER_SVG) }
}

/** Soft success: the pebble exists but there is no render to draw. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun RecordSuccessStepSoftLight() {
    PebblesTheme { SuccessStepPreview(null) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun RecordCloseConfirmLight() {
    PebblesTheme { CloseConfirmDialog(onSaveAsDraft = {}, onDiscard = {}, onKeepGoing = {}) }
}
