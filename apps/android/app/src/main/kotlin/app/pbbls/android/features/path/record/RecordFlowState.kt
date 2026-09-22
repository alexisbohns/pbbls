package app.pbbls.android.features.path.record

import androidx.annotation.StringRes
import app.pbbls.android.core.model.ComposePebbleResponse
import app.pbbls.android.core.model.PebbleDraft

/**
 * Everything the record flow's state machine owns, as one immutable value
 * (#849).
 *
 * It was six separate `mutableStateOf` fields on [RecordFlowModel]. Collapsing
 * them buys three things: the whole machine is one `StateFlow` the screen
 * collects with `collectAsStateWithLifecycle`; it is a plain value a
 * `SavedStateHandle` can round-trip through process death; and the questions
 * the flow keeps asking of it ("is this step answered?", "where does a resumed
 * draft land?") live on the value rather than on the mutator that happens to
 * need them.
 *
 * The derived members are functions and getters, never fields — a stored
 * `isAnswered` would have to be recomputed at every one of the fourteen places
 * that change the draft, and would be wrong at the first one anybody forgot.
 */
data class RecordFlowState(
    val draft: PebbleDraft = PebbleDraft(),
    val step: RecordStep = RecordStep.PHOTO,
    /**
     * Mirrored from the `SnapUploadCoordinator`, so the photo step's button can
     * read Skip or Done without the flow owning media.
     */
    val hasSnap: Boolean = false,
    /** Set once publish returns. Drives the success step. */
    val published: ComposePebbleResponse? = null,
    val isPublishing: Boolean = false,
    /**
     * Publish failure text, as a string resource id. Cleared when a publish
     * begins so a retry never renders the previous attempt's message.
     */
    @StringRes val publishErrorRes: Int? = null,
) {
    /**
     * Whether a given step has been answered. Drives both the forward gate and
     * the optional steps' Skip / Done button label.
     *
     * Exhaustive with no `else`, deliberately: this is the single place that
     * says what "answered" means, and an `else` would silently treat a newly
     * added step as already answered.
     */
    fun hasAnswer(forStep: RecordStep): Boolean =
        when (forStep) {
            // Nothing for the user to supply: WHEN arrives seeded from the
            // photo's EXIF or from now, PRIVACY from SECRET, and SUCCESS is
            // terminal.
            RecordStep.WHEN, RecordStep.PRIVACY, RecordStep.SUCCESS -> true
            RecordStep.PHOTO -> hasSnap
            RecordStep.NAME -> draft.name.trim().isNotEmpty()
            RecordStep.VALENCE -> draft.valence != null
            RecordStep.EMOTION -> draft.emotionId != null
            RecordStep.DOMAIN -> draft.domainId != null
            RecordStep.SOULS -> draft.soulIds.isNotEmpty()
            RecordStep.COLLECTION -> draft.collectionId != null
            RecordStep.GLYPH -> draft.glyphId != null
        }

    /**
     * Whether the current step may be left. Optional steps are always
     * satisfied: passing one is the user saying "not this one", not an error.
     */
    val isAnswered: Boolean
        get() = step.isOptional || hasAnswer(step)

    /**
     * Skip while the optional step is empty, Done once it holds something.
     * Only meaningful on optional steps.
     */
    val optionalButtonIsSkip: Boolean
        get() = !hasAnswer(step)

    /**
     * The first mandatory step this draft has not answered — where a resumed
     * draft lands (design D9).
     *
     * Optional steps never count as gaps: skipping one is a legitimate answer,
     * and re-asking would silently undo the user's decision. Falls through to
     * [RecordStep.PRIVACY] — a fully answered draft resumes against publish.
     */
    fun firstGap(): RecordStep = RecordStep.counted.firstOrNull { !it.isOptional && !hasAnswer(it) } ?: RecordStep.PRIVACY
}
