package app.pbbls.android.features.path.record

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.path.create.pickers.GlyphPickerState
import app.pbbls.android.features.path.models.Domain
import app.pbbls.android.features.path.models.PebbleCollection
import app.pbbls.android.features.path.record.steps.RecordCollectionStep
import app.pbbls.android.features.path.record.steps.RecordDomainStep
import app.pbbls.android.features.path.record.steps.RecordEmotionStep
import app.pbbls.android.features.path.record.steps.RecordGlyphStep
import app.pbbls.android.features.path.record.steps.RecordNameStep
import app.pbbls.android.features.path.record.steps.RecordPhotoStep
import app.pbbls.android.features.path.record.steps.RecordPrivacyStep
import app.pbbls.android.features.path.record.steps.RecordSoulsStep
import app.pbbls.android.features.path.record.steps.RecordValenceStep
import app.pbbls.android.features.path.record.steps.RecordWhenStep
import app.pbbls.android.features.pebblemedia.models.FormSnap

/**
 * Renders the body of whichever step the flow is on — ports iOS
 * `RecordStepContent`.
 *
 * Split out of [RecordFlowScreen] so the container keeps to chrome, the action
 * table and orchestration. Holds no state of its own — every input arrives as a
 * parameter, so the whole flow's state stays in one place.
 *
 * The `when` is exhaustive with no `else`, deliberately: losing exhaustiveness
 * would mean a newly added step silently renders nothing.
 */
@Composable
fun RecordStepContent(
    step: RecordStep,
    model: RecordFlowModel,
    domains: List<Domain>,
    collections: List<PebbleCollection>,
    snap: FormSnap?,
    /** True when the `when` step's date came from the photo's EXIF (M58 D7). */
    seededFromPhoto: Boolean,
    /** Non-null while the attached photo blocks publishing. */
    snapBlockedMessage: String?,
    publishError: String?,
    glyphPickerState: GlyphPickerState,
    onPickPhoto: () -> Unit,
    onRetryPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onGlyphPicked: (Glyph) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (step) {
        RecordStep.PHOTO ->
            RecordPhotoStep(
                snap = snap,
                onPick = onPickPhoto,
                onRetry = onRetryPhoto,
                onRemove = onRemovePhoto,
                modifier = modifier,
            )

        RecordStep.WHEN ->
            RecordWhenStep(
                happenedAt = model.draft.happenedAt,
                onChange = { model.setHappenedAt(it) },
                seededFromPhoto = seededFromPhoto,
                modifier = modifier,
            )

        RecordStep.NAME ->
            RecordNameStep(
                name = model.draft.name,
                limit = RecordFlowModel.NAME_LIMIT,
                onChange = { model.setName(it) },
                modifier = modifier,
            )

        RecordStep.VALENCE ->
            RecordValenceStep(
                selected = model.draft.valence,
                onSelect = { model.selectValence(it) },
                onSeed = { model.seedValenceIfNeeded() },
                modifier = modifier,
            )

        RecordStep.EMOTION ->
            RecordEmotionStep(
                selectedId = model.draft.emotionId,
                valence = model.draft.valence,
                onSelect = { model.selectEmotion(it) },
                modifier = modifier,
            )

        RecordStep.DOMAIN ->
            RecordDomainStep(
                domains = domains,
                selectedId = model.draft.domainId,
                onSelect = { model.selectDomain(it) },
                modifier = modifier,
            )

        RecordStep.SOULS ->
            RecordSoulsStep(
                selectedIds = model.draft.soulIds,
                onToggle = { model.toggleSoul(it) },
                modifier = modifier,
            )

        RecordStep.COLLECTION ->
            RecordCollectionStep(
                collections = collections,
                selectedId = model.draft.collectionId,
                onSelect = { model.selectCollection(it) },
                modifier = modifier,
            )

        RecordStep.GLYPH ->
            RecordGlyphStep(
                selectedGlyphId = model.draft.glyphId,
                onSelect = onGlyphPicked,
                state = glyphPickerState,
                modifier = modifier,
            )

        RecordStep.PRIVACY ->
            RecordPrivacyStep(
                selected = model.draft.visibility,
                onSelect = { model.selectVisibility(it) },
                snapBlockedMessage = snapBlockedMessage,
                publishError = publishError,
                modifier = modifier,
            )

        // Handled by RecordFlowScreen, which renders the success step outside
        // the scaffold so it can own its full-screen layout.
        RecordStep.SUCCESS -> Unit
    }
}
