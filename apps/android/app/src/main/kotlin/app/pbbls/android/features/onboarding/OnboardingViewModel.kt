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

/** The steps that draw the system glyphs. */
private val GLYPH_STEPS = setOf("you", "people")

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
            // The letter is public; these two are not. Read before the session is live they come back
            // empty with no error, which shows blank stones and re-asks acts already on record.
            service.awaitSession()
            loadSystemGlyphs()
            runCatchingCancellable { consents.active() }
                .onSuccess { active ->
                    val missing = ConsentGateLogic.missing(active).toSet()
                    _uiState.update { it.copy(consent = it.consent.copy(recorded = ConsentKind.entries.toSet() - missing)) }
                }.onFailure { Log.e(TAG, "consent read failed; every act will be asked", it) }
        }
    }

    private suspend fun loadSystemGlyphs() {
        runCatchingCancellable { service.systemGlyphs() }
            .onSuccess { glyphs ->
                if (glyphs.isEmpty()) Log.e(TAG, "system glyphs came back empty")
                _uiState.update { it.copy(systemGlyphs = glyphs) }
            }.onFailure { Log.e(TAG, "system glyphs failed", it) }
    }

    // MARK: - Stepping

    fun next() {
        val state = _uiState.value
        when (state.step.id) {
            "consent" -> {
                if (!state.consent.canContinue) return
                if (state.consent.toRecord.isNotEmpty()) {
                    recordConsents()
                    return
                }
            }
            "people" -> commitEditingPeople()
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
        if (state.step.id == "people") commitEditingPeople()
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
        val state = _uiState.value
        if (state.systemGlyphs.isEmpty() && state.step.id in GLYPH_STEPS) viewModelScope.launch { loadSystemGlyphs() }
    }

    // MARK: - Step 2

    /** A switch is the user's until Continue: nothing reaches the ledger, so nothing has to be undone. */
    fun onConsentToggle(
        kind: ConsentKind,
        checked: Boolean,
    ) = _uiState.update {
        val consent = it.consent
        // An act already on record is withdrawn in Settings, not by leaving this step.
        if (kind in consent.recorded || consent.isSaving) return@update it
        it.copy(consent = consent.copy(on = if (checked) consent.on + kind else consent.on - kind, errorRes = null))
    }

    /** Continue: every act switched on here goes in the ledger, then the flow moves on. */
    private fun recordConsents() {
        val toRecord = _uiState.value.consent.toRecord
        _uiState.update { it.copy(consent = it.consent.copy(isSaving = true, errorRes = null)) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                var failed = false
                for (kind in toRecord) {
                    runCatchingCancellable { consents.record(kind, ConsentGateLogic.versionFor(kind), ConsentGateLogic.SOURCE) }
                        .onSuccess { _uiState.update { it.copy(consent = it.consent.copy(recorded = it.consent.recorded + kind)) } }
                        .onFailure { e ->
                            Log.e(TAG, "record consent failed", e)
                            failed = true
                        }
                }
                _uiState.update {
                    it.copy(
                        consent =
                            it.consent.copy(
                                isSaving = false,
                                errorRes = if (failed) R.string.onboarding_consent_error else null,
                            ),
                    )
                }
                if (!failed) {
                    markGateSatisfiedIfComplete()
                    move(+1)
                }
            }
        }
    }

    /** Every act on record: the gate's cache says so, so `restart` after the flow never re-asks. */
    private fun markGateSatisfiedIfComplete() {
        val uid = userId ?: return
        if (_uiState.value.consent.recorded
                .containsAll(ConsentKind.entries)
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

    /** Leaving the step never strands a half-typed name: a blank place is dropped, a name is kept. */
    private fun commitEditingPeople() =
        _uiState.update {
            it.copy(
                people =
                    it.people
                        .filter { p -> !p.editing || p.name.isNotBlank() }
                        .map { p -> if (p.editing) p.copy(name = p.name.trim(), editing = false) else p },
            )
        }

    /** Tapping a name reopens its field; emptying it removes the place. */
    fun onPersonEdit(index: Int) =
        _uiState.update {
            if (it.people.any { p -> p.editing }) {
                it
            } else {
                it.copy(people = it.people.mapIndexed { i, p -> if (i == index) p.copy(editing = true) else p })
            }
        }

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
                // A person with no pick wears a system glyph, and `souls.glyph_id` is not null: never send
                // an empty one. A failed first load gets one more try here.
                if (_uiState.value.systemGlyphs.isEmpty() && state.answers.souls.any { it.glyphId.isEmpty() }) {
                    loadSystemGlyphs()
                }
                val answers = _uiState.value.answers
                runCatchingCancellable {
                    check(answers.souls.none { it.glyphId.isEmpty() }) { "a person has no glyph: the system glyphs did not load" }
                    service.complete(answers)
                }.onSuccess {
                    handoff.leave(
                        OnboardingHandoffNote(
                            userId = userId,
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
