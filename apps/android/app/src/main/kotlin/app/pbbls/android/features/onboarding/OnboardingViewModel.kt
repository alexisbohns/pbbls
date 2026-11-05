package app.pbbls.android.features.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.ConsentPreferences
import app.pbbls.android.core.data.ConsentServicing
import app.pbbls.android.core.data.OnboardingHandoff
import app.pbbls.android.core.data.OnboardingHandoffNote
import app.pbbls.android.core.data.OnboardingServicing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.model.ConsentGateLogic
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.OnboardingPace
import app.pbbls.android.core.model.OnboardingReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "onboarding"

/**
 * The seven-step flow (#1510), one ViewModel scoped to the `Onboarding` entry. Answers live here
 * until the hand-off sends them in one `complete_onboarding` (D5); consent acts are the exception,
 * recorded as each switch turns on (D3), so a user who quits at step 3 still has a ledger.
 * Quitting the entry loses the typed answers: the flow restarts next launch (D10).
 */
@HiltViewModel
class OnboardingViewModel internal constructor(
    private val service: OnboardingServicing,
    private val consents: ConsentServicing,
    private val consentCache: ConsentPreferences,
    private val handoff: OnboardingHandoff,
    private val userId: String?,
) : ViewModel() {
    @Inject
    constructor(
        service: OnboardingServicing,
        consents: ConsentServicing,
        consentCache: ConsentPreferences,
        handoff: OnboardingHandoff,
        supabase: SupabaseServicing,
    ) : this(service, consents, consentCache, handoff, supabase.session?.user?.id)

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatchingCancellable { service.promiseLetter() }
                .onSuccess { letter -> _uiState.update { it.copy(letter = letter, letterUnavailable = letter == null) } }
                .onFailure { e ->
                    Log.e(TAG, "promise letter failed", e)
                    _uiState.update { it.copy(letterUnavailable = true) }
                }
        }
        viewModelScope.launch {
            runCatchingCancellable { service.systemGlyphs() }
                .onSuccess { glyphs -> _uiState.update { it.copy(systemGlyphs = glyphs) } }
                .onFailure { Log.e(TAG, "system glyphs failed", it) }
        }
        viewModelScope.launch {
            runCatchingCancellable { consents.active() }
                .onSuccess { active ->
                    val missing = ConsentGateLogic.missing(active).toSet()
                    _uiState.update { it.copy(consent = it.consent.copy(recorded = ConsentKind.entries.toSet() - missing)) }
                }.onFailure { Log.e(TAG, "consent read failed; every act will be asked", it) }
        }
    }

    // MARK: - Stepping

    fun next() {
        val state = _uiState.value
        when (state.step.id) {
            "consent" -> if (!state.consent.canContinue) return
            "pace" -> {
                complete()
                return
            }
        }
        move(+1)
    }

    fun skip() {
        val state = _uiState.value
        when (state.step.skip) {
            OnboardingSkip.NONE -> return
            OnboardingSkip.SKIP, OnboardingSkip.LATER -> Unit
        }
        _uiState.update {
            when (state.step.id) {
                "reasons" -> it.copy(reasons = emptySet())
                "you" -> it.copy(name = "", glyphId = null)
                "people" -> it.copy(people = emptyList())
                "pace" -> it.copy(pace = null)
                else -> it
            }
        }
        if (state.step.id == "pace") complete() else move(+1)
    }

    fun back() {
        if (_uiState.value.stepIndex <= 0) return
        move(-1)
    }

    private fun move(delta: Int) {
        _uiState.update { state ->
            val steps = OnboardingSteps.visible
            val index = (state.stepIndex + delta).coerceIn(0, steps.lastIndex)
            state.copy(step = steps[index], errorRes = null, glyphSheetFor = null, carvingFor = null)
        }
    }

    // MARK: - Step 2

    fun onConsentToggle(
        kind: ConsentKind,
        checked: Boolean,
    ) {
        val state = _uiState.value.consent
        // An act on record, recorded here, or being recorded is locked on: nothing is withdrawn in
        // this flow, and a second write at the same version is pointless.
        if (!checked || kind in state.recorded || kind in state.on || kind in state.saving) return
        _uiState.update { it.copy(consent = it.consent.copy(saving = it.consent.saving + kind, errorRes = null)) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatchingCancellable { consents.record(kind, ConsentGateLogic.versionFor(kind), ConsentGateLogic.SOURCE) }
                    .onSuccess {
                        _uiState.update { it.copy(consent = it.consent.copy(on = it.consent.on + kind, saving = it.consent.saving - kind)) }
                        markGateSatisfiedIfComplete()
                    }.onFailure { e ->
                        Log.e(TAG, "record consent failed", e)
                        _uiState.update {
                            it.copy(
                                consent = it.consent.copy(saving = it.consent.saving - kind, errorRes = R.string.onboarding_consent_error),
                            )
                        }
                    }
            }
        }
    }

    /** Every act on record: the gate's cache says so, so `restart` after the flow never re-asks. */
    private fun markGateSatisfiedIfComplete() {
        val uid = userId ?: return
        val consent = _uiState.value.consent
        if ((consent.recorded + consent.on).containsAll(
                ConsentKind.entries,
            )
        ) {
            consentCache.markSatisfied(uid, ConsentGateLogic.fingerprint())
        }
    }

    // MARK: - Step 3

    fun onReasonToggle(reason: OnboardingReason) =
        _uiState.update { it.copy(reasons = if (reason in it.reasons) it.reasons - reason else it.reasons + reason) }

    // MARK: - Step 4

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value.take(NAME_LIMIT)) }

    fun onGlyphPick(glyphId: String) = _uiState.update { it.copy(glyphId = glyphId) }

    fun onDrawForYou() = _uiState.update { it.copy(carvingFor = -1) }

    // MARK: - Step 5

    fun onPersonAdd() =
        _uiState.update {
            if (it.people.size >= 3 || it.people.any { p -> p.editing }) it else it.copy(people = it.people + OnboardingPerson())
        }

    fun onPersonNameChange(
        index: Int,
        value: String,
    ) = _uiState.update { it.copy(people = it.people.mapIndexed { i, p -> if (i == index) p.copy(name = value.take(NAME_LIMIT)) else p }) }

    /** The keyboard's done: a blank name removes the place, a name closes the field. */
    fun onPersonCommit(index: Int) =
        _uiState.update {
            val person = it.people.getOrNull(index) ?: return@update it
            if (person.name.isBlank()) {
                it.copy(people = it.people.filterIndexed { i, _ -> i != index })
            } else {
                it.copy(people = it.people.mapIndexed { i, p -> if (i == index) p.copy(name = p.name.trim(), editing = false) else p })
            }
        }

    fun onPersonRemove(index: Int) = _uiState.update { it.copy(people = it.people.filterIndexed { i, _ -> i != index }) }

    fun onPersonGlyphSheet(index: Int?) = _uiState.update { it.copy(glyphSheetFor = index) }

    fun onPersonGlyphPick(
        index: Int,
        glyphId: String,
    ) = _uiState.update {
        it.copy(people = it.people.mapIndexed { i, p -> if (i == index) p.copy(glyphId = glyphId) else p }, glyphSheetFor = null)
    }

    fun onDrawForPerson(index: Int) = _uiState.update { it.copy(carvingFor = index, glyphSheetFor = null) }

    // MARK: - S1, the carve studio

    fun onCarved(glyph: Glyph) =
        _uiState.update { state ->
            val target = state.carvingFor ?: return@update state
            val withGlyph = state.copy(carved = state.carved + glyph, carvingFor = null)
            if (target < 0) {
                withGlyph.copy(glyphId = glyph.id)
            } else {
                withGlyph.copy(people = withGlyph.people.mapIndexed { i, p -> if (i == target) p.copy(glyphId = glyph.id) else p })
            }
        }

    fun onCarveCancelled() = _uiState.update { it.copy(carvingFor = null) }

    // MARK: - Step 6 and the hand-off

    fun onPaceSelect(pace: OnboardingPace) = _uiState.update { it.copy(pace = pace) }

    private fun complete() {
        val state = _uiState.value
        if (state.isCompleting || state.isDone) return
        _uiState.update { it.copy(isCompleting = true, errorRes = null) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                val answers = state.answers
                runCatchingCancellable { service.complete(answers) }
                    .onSuccess {
                        handoff.leave(
                            OnboardingHandoffNote(
                                // The souls' ids are the server's; Home resolves the soul by name (Task 6.2).
                                firstPersonName = answers.souls.firstOrNull()?.name,
                                reason = answers.reasons?.firstOrNull(),
                            ),
                        )
                        _uiState.update { it.copy(isCompleting = false, isDone = true) }
                    }.onFailure { e ->
                        Log.e(TAG, "complete_onboarding failed", e)
                        _uiState.update { it.copy(isCompleting = false, errorRes = R.string.onboarding_complete_error) }
                    }
            }
        }
    }

    companion object {
        /** Settings' display-name limit; a person's name shares it. */
        const val NAME_LIMIT = 40
    }
}
