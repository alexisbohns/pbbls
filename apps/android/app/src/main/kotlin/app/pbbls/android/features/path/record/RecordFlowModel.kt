package app.pbbls.android.features.path.record

import app.pbbls.android.features.path.models.ComposePebbleResponse
import app.pbbls.android.features.path.models.KnownDraftIds
import app.pbbls.android.features.path.models.PebbleDraft
import app.pbbls.android.features.path.models.PebbleDraftPayload
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.Visibility
import app.pbbls.android.features.path.models.toDraft
import app.pbbls.android.services.TapHaptic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.OffsetDateTime

/**
 * The record flow's state machine: the draft under construction, the step the
 * user is on, and every interaction that changes either — ports iOS
 * `RecordFlowModel` (M58 design D4).
 *
 * Views own no flow state. That is not tidiness for its own sake — the
 * requirement is a haptic on *every* tap, and implemented as a discipline
 * ("remember to buzz in each `onClick`") it is one forgotten lambda away from
 * being false, and untestable besides. Routing every interaction through this
 * type makes it structural: a tap that does not call a method here changes
 * nothing, and every method here buzzes.
 *
 * [haptic] is injected so JVM unit tests record the flavor per interaction
 * without a `View`. It has **no default**, deliberately: a defaulted no-op is
 * exactly the silent failure this design exists to prevent — the model would
 * still work, and the buzz would just be gone. State is Compose state, which the
 * runtime supports off device (same as `KarmaNotificationService`, which is
 * unit-tested the same way), so the whole machine is testable with no UI at all.
 */
class RecordFlowModel(
    private val haptic: (TapHaptic) -> Unit,
    initial: RecordFlowState = RecordFlowState(),
) {
    private val _state = MutableStateFlow(initial)

    /**
     * The whole machine, as one value. `RecordFlowViewModel` persists it
     * through `SavedStateHandle` and the screen collects it with
     * `collectAsStateWithLifecycle` (#849).
     */
    val state: StateFlow<RecordFlowState> = _state.asStateFlow()

    /**
     * The accessors below read and write [state] rather than holding anything.
     * They exist so the mutators and the tests read as they always did — the
     * storage changed, the machine did not.
     */
    var draft: PebbleDraft
        get() = _state.value.draft
        set(value) = _state.update { it.copy(draft = value) }

    val step: RecordStep
        get() = _state.value.step

    var hasSnap: Boolean
        get() = _state.value.hasSnap
        set(value) = _state.update { it.copy(hasSnap = value) }

    val published: ComposePebbleResponse?
        get() = _state.value.published

    val isPublishing: Boolean
        get() = _state.value.isPublishing

    val publishErrorRes: Int?
        get() = _state.value.publishErrorRes

    // MARK: - Answers
    //
    // Live on RecordFlowState, which is where the data is. Delegated here so
    // every caller keeps asking the model.

    fun hasAnswer(forStep: RecordStep): Boolean = _state.value.hasAnswer(forStep)

    val isAnswered: Boolean
        get() = _state.value.isAnswered

    val optionalButtonIsSkip: Boolean
        get() = _state.value.optionalButtonIsSkip

    // MARK: - Navigation

    fun advance() {
        if (step == RecordStep.SUCCESS) return
        if (!isAnswered) {
            haptic(TapHaptic.WARNING)
            return
        }
        val next = step.next ?: return
        haptic(TapHaptic.ADVANCE)
        _state.update { it.copy(step = next) }
    }

    fun back() {
        if (step == RecordStep.SUCCESS) return
        val previous = step.previous ?: return
        haptic(TapHaptic.ADVANCE)
        _state.update { it.copy(step = previous) }
    }

    /**
     * Jump without gating or feedback. For resume and for tests — never wired
     * to a control.
     */
    fun goTo(target: RecordStep) {
        _state.update { it.copy(step = target) }
    }

    // MARK: - Selection

    /**
     * A tile pick: commit the value and move on.
     *
     * Fires SELECTION and nothing else. The pick and the advance are one
     * gesture, and two buzzes for one tap reads as a stutter rather than as two
     * pieces of information.
     */
    private fun commitAndAdvance(mutate: (PebbleDraft) -> PebbleDraft) {
        haptic(TapHaptic.SELECTION)
        // One update, so the commit and the advance are a single emission: two
        // would briefly publish the new answer against the old step.
        _state.update { current ->
            current.copy(draft = mutate(current.draft), step = current.step.next ?: current.step)
        }
    }

    /**
     * The valence step arrives already parked on a value: the roll needs
     * something under the finger, and an empty roll has no affordances to read.
     * Seeds without a haptic — nothing happened yet that the user did — and
     * never overwrites an existing answer, which is what makes it safe on the
     * resume path.
     */
    fun seedValenceIfNeeded() {
        if (draft.valence != null) return
        draft = draft.copy(valence = Valence.NEUTRAL_MEDIUM)
    }

    /**
     * Valence commits in place instead of advancing: the fan is a comparison,
     * and a tap that leaves the screen denies the user the look at what they
     * just chose next to the eight they did not. The step's `Continue` button
     * does the advancing.
     *
     * The one exception to D3 ("tile steps commit on tap and advance"), and the
     * same exception iOS carved out in #728.
     */
    fun selectValence(valence: Valence) {
        haptic(TapHaptic.SELECTION)
        draft = draft.copy(valence = valence)
    }

    fun selectEmotion(emotionId: String) = commitAndAdvance { it.copy(emotionId = emotionId) }

    fun selectDomain(domainId: String) = commitAndAdvance { it.copy(domainId = domainId) }

    fun selectCollection(collectionId: String) = commitAndAdvance { it.copy(collectionId = collectionId) }

    fun selectGlyph(glyphId: String) = commitAndAdvance { it.copy(glyphId = glyphId) }

    /**
     * Souls are multi-select, so a toggle never advances — the step's Skip /
     * Done button does that. Dropping multi-soul tagging to make every step
     * uniform would be a real capability loss against the form.
     */
    fun toggleSoul(id: String) {
        haptic(TapHaptic.SELECTION)
        val current = draft.soulIds
        draft = draft.copy(soulIds = if (id in current) current - id else current + id)
    }

    /**
     * Privacy selects without advancing: Publish is the step's action, and
     * silently publishing on a grade tap would be a trap.
     */
    fun selectVisibility(visibility: Visibility) {
        haptic(TapHaptic.SELECTION)
        draft = draft.copy(visibility = visibility)
    }

    /**
     * Clamped write for the name field (design D3). Front-end only: neither
     * `pebbles.name` nor `PebbleCreatePayload` constrains length, and nothing
     * server-side is added to enforce it.
     */
    fun setName(raw: String) {
        draft = draft.copy(name = raw.take(NAME_LIMIT))
    }

    /**
     * Seed the date from the picked photo's EXIF (design D7). No-op for a null
     * date, so a photo without metadata leaves `happenedAt` at its default of
     * now.
     */
    fun applyCaptureDate(date: OffsetDateTime?) {
        if (date == null) return
        draft = draft.copy(happenedAt = date)
    }

    fun setHappenedAt(value: OffsetDateTime) {
        draft = draft.copy(happenedAt = value)
    }

    /** Clears a glyph the server said the user may no longer use. */
    fun clearGlyph() {
        draft = draft.copy(glyphId = null)
    }

    // MARK: - Resume

    /**
     * The first mandatory step this draft has not answered — where a resumed
     * draft lands (design D9).
     *
     * Optional steps never count as gaps: skipping one is a legitimate answer,
     * and re-asking would silently undo the user's decision. Falls through to
     * [RecordStep.PRIVACY] — a fully answered draft resumes against publish.
     */
    fun firstGap(): RecordStep = _state.value.firstGap()

    fun resume(
        payload: PebbleDraftPayload,
        known: KnownDraftIds,
    ) {
        _state.update { current ->
            val hydrated = current.copy(draft = payload.toDraft(known))
            hydrated.copy(step = hydrated.firstGap())
        }
    }

    // MARK: - Publish

    fun beginPublish() {
        _state.update { it.copy(isPublishing = true, publishErrorRes = null) }
    }

    fun succeed(response: ComposePebbleResponse) {
        haptic(TapHaptic.SUCCESS)
        _state.update {
            it.copy(isPublishing = false, published = response, step = RecordStep.SUCCESS)
        }
    }

    /**
     * A hard failure never reaches the success step (design D10): the flow
     * stays put so the draft — and the way out via ✕ → Save as draft — is
     * untouched.
     */
    fun fail(messageRes: Int) {
        haptic(TapHaptic.WARNING)
        _state.update { it.copy(isPublishing = false, publishErrorRes = messageRes) }
    }

    /**
     * Back to a blank flow.
     *
     * Was needed when `RecordFlowViewModel` was activity-scoped and outlived the
     * cover it drove (#849): without an explicit reset, reopening the composer
     * showed the pebble just published. Since #852 the flow is its own nav entry
     * and the ViewModel is cleared with it, so this runs before the pop rather
     * than instead of one.
     * Silent — a reset is not something the user did.
     */
    fun reset() {
        _state.value = RecordFlowState()
    }

    companion object {
        /** Longest name the flow accepts. Front-end only — see [setName]. */
        const val NAME_LIMIT = 40
    }
}
