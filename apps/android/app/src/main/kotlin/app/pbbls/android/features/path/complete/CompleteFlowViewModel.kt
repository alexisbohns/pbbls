package app.pbbls.android.features.path.complete

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.CompleteSnapshotStoring
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.data.KarmaNotificationService
import app.pbbls.android.core.data.MomentReadServicing
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.data.RecordFlowMemorying
import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.CompleteSnapshot
import app.pbbls.android.core.model.KarmaReason
import app.pbbls.android.core.model.KnownDraftIds
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.PebbleCompletePayload
import app.pbbls.android.core.ui.momentWriteErrorRes
import app.pbbls.android.features.path.ComposerMedia
import app.pbbls.android.features.path.PathFocusRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "complete-flow"

/** Long enough that tapping through tiles does not thrash SharedPreferences. */
internal const val AUTOSAVE_DEBOUNCE_MS = 800L

sealed interface CompleteFlowEffect {
    data class Haptic(
        val flavor: TapHaptic,
    ) : CompleteFlowEffect

    data object Dismiss : CompleteFlowEffect
}

data class CompleteFlowUiState(
    val flow: CompleteFlowState = CompleteFlowState(),
    /** For the success screen. Null until loaded, and on a photo-only moment. */
    val momentName: String? = null,
    /**
     * No completed pebble on the account, read once after the restore (D8). False until that read
     * settles, and false for good when there is no session, the flag says the sheet was seen, or the
     * read failed: a quiet branch must never rest on an empty read (`200 []` under RLS).
     */
    val isFirstPebble: Boolean = false,
    /** The valence help sheet is up: opened by itself over a first pebble, or by the callout. */
    val valenceHelpOpen: Boolean = false,
)

/**
 * The Complete flow's orchestration: restore, autosave, `complete_pebble`, and
 * what follows a completion (A-7). Entry-scoped (#852), so it is cleared with
 * its entry. The write and everything after it run in `NonCancellable`:
 * leaving after the request left the device must not skip clearing the
 * autosave, or the next visit would resurrect answers already completed.
 */
@HiltViewModel
class CompleteFlowViewModel
    @Inject
    constructor(
        private val reads: MomentReadServicing,
        private val writes: MomentWriteServicing,
        private val refs: ReferenceDataServicing,
        private val snapshots: CompleteSnapshotStoring,
        private val karma: KarmaNotificationService,
        private val achievements: AchievementsServicing,
        private val supabase: SupabaseServicing,
        private val media: ComposerMedia,
        private val focusRequests: PathFocusRequests,
        private val haptics: HapticsPreference,
        private val memory: RecordFlowMemorying,
    ) : ViewModel() {
        private val effectsOut = UiEffects<CompleteFlowEffect>(viewModelScope)
        val effects: Flow<CompleteFlowEffect> = effectsOut.flow

        /** Pebbles' haptics switch (#1242 D23): the screen asks it at each buzz, so Settings applies at once. */
        val hapticsOn: Boolean get() = haptics.isOn

        private val model = CompleteFlowModel(haptic = { effectsOut.emit(CompleteFlowEffect.Haptic(it)) })

        private val _uiState = MutableStateFlow(CompleteFlowUiState())
        val uiState: StateFlow<CompleteFlowUiState> = _uiState.asStateFlow()

        private var momentId: String? = null

        private val userId: String?
            get() = supabase.session?.user?.id

        init {
            viewModelScope.launch { model.state.collect { flow -> _uiState.update { it.copy(flow = flow) } } }
            viewModelScope.launch { media.prewarmValence() }
        }

        /** Every tap goes through the machine (M58 D4). */
        fun machine(): CompleteFlowModel = model

        fun onAchievementCheck() = achievements.fireCheck()

        /** Guarded: a rotation re-runs the screen's `LaunchedEffect` and must not restore over live answers. */
        fun start(id: String) {
            if (momentId == id) return
            momentId = id
            viewModelScope.launch {
                runCatchingCancellable { reads.loadMoment(id) }
                    .onSuccess { detail -> _uiState.update { it.copy(momentName = detail.name) } }
                    .onFailure { Log.w(TAG, "moment load failed; success will say untitled", it) }
            }
            viewModelScope.launch {
                restore(id)
                // After the restore, and on its own: a slow read must never hold a draft up.
                launch { readFirstPebble() }
                autosave(id)
            }
        }

        /**
         * Only for an account that has not seen the help yet, so most users never pay the read. No
         * session means no read at all: it would answer `[]`, which reads as "nothing completed".
         */
        private suspend fun readFirstPebble() {
            val owner = userId ?: return
            if (memory.isValenceHelpSeen(owner)) return
            runCatchingCancellable { reads.loadPath() }
                .onSuccess { moments -> _uiState.update { it.copy(isFirstPebble = moments.none { m -> m.isCompleted }) } }
                .onFailure { Log.w(TAG, "first-pebble read failed; the help stays closed", it) }
        }

        /**
         * The valence step calls this when it is on screen and [CompleteFlowUiState.isFirstPebble] is
         * true, so the sheet opens only on that step, also when the read lands after the step. Marking
         * seen happens on open: a back-out, or a trip forward and back, never reopens it.
         */
        fun showFirstPebbleHelp() {
            val owner = userId ?: return
            if (!_uiState.value.isFirstPebble || memory.isValenceHelpSeen(owner)) return
            memory.markValenceHelpSeen(owner)
            _uiState.update { it.copy(valenceHelpOpen = true) }
        }

        /** The "How to read this" callout: always opens, marks nothing. */
        fun openValenceHelp() = _uiState.update { it.copy(valenceHelpOpen = true) }

        fun closeValenceHelp() = _uiState.update { it.copy(valenceHelpOpen = false) }

        /**
         * Silent restore (A-5). Souls and collections are sanitized only once
         * reference data has loaded: sanitizing against empty caches would drop
         * every one of them (#647).
         */
        private suspend fun restore(id: String) {
            val owner = userId ?: return
            val snapshot = snapshots.load(id, owner) ?: return
            if (!refs.hasLoaded) {
                runCatchingCancellable { refs.load() }.onFailure { Log.w(TAG, "reference data load failed; restoring unsanitized", it) }
            }
            val known =
                if (refs.hasLoaded) {
                    KnownDraftIds(soulIds = refs.souls.map { it.id }.toSet(), collectionIds = refs.collections.map { it.id }.toSet())
                } else {
                    null
                }
            // Checked after the wait on reference data: a pick made meanwhile must not be overwritten (as iOS).
            if (model.step != CompleteStep.VALENCE || model.answers != PebbleAnswers()) return
            model.resume(snapshot.toAnswers(known))
        }

        private suspend fun autosave(id: String) {
            model.state
                .map { it.answers }
                .distinctUntilChanged()
                .collectLatest { answers ->
                    // collectLatest cancels this on the next change: the delay is a debounce, not a queue.
                    delay(AUTOSAVE_DEBOUNCE_MS)
                    // Checked after the wait: a completion that landed during it already cleared the entry.
                    if (model.completed != null || model.isCompleting) return@collectLatest
                    val owner = userId ?: return@collectLatest
                    snapshots.save(id, owner, CompleteSnapshot.from(answers))
                }
        }

        private fun flushNow() {
            val id = momentId ?: return
            val owner = userId ?: return
            if (model.completed == null) snapshots.save(id, owner, CompleteSnapshot.from(model.answers))
        }

        /** ✕ never asks (A-4): the answers stay on the device and resume next time. */
        fun onClose() {
            if (model.isCompleting) return
            flushNow()
            effectsOut.emit(CompleteFlowEffect.Dismiss)
        }

        fun onExit() = effectsOut.emit(CompleteFlowEffect.Dismiss)

        fun onSystemBack() {
            when {
                model.isCompleting -> Unit
                model.step == CompleteStep.SUCCESS -> onExit()
                model.step.previous != null -> model.back()
                else -> onClose()
            }
        }

        fun complete() {
            val id = momentId ?: return
            if (userId == null) {
                Log.e(TAG, "complete: no current user")
                model.fail(R.string.record_signed_out_error)
                return
            }
            val answers = model.answers
            if (!answers.isComplete || model.isCompleting) return
            model.beginComplete()
            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable { writes.completePebble(PebbleCompletePayload.from(id, answers)) }
                        .fold(
                            onSuccess = { pebbleId ->
                                snapshots.clear(id)
                                // Path shows the moment's week when it is next on screen (R14).
                                focusRequests.request(id)
                                val (delta, glyph) =
                                    coroutineScope {
                                        val delta = async { writes.karmaDelta(pebbleId, KarmaReason.PEBBLE_CREATED) }
                                        val glyph =
                                            async {
                                                runCatchingCancellable { reads.resolveGlyph(pebbleId) }
                                                    .onFailure { Log.w(TAG, "glyph resolve failed", it) }
                                                    .getOrNull()
                                            }
                                        delta.await() to glyph.await()
                                    }
                                // The success screen shows the amount, so no capsule (M58 D10).
                                // A high-distress pebble gets no client celebration; the server still grants the karma.
                                val quiet = answers.valence?.quietSuccess == true
                                if (!quiet) {
                                    karma.notifyEarned(delta ?: 0, KarmaReason.PEBBLE_CREATED, presentsCapsule = false)
                                    achievements.fireCheck()
                                }
                                model.succeed(CompleteResult(pebbleId, delta, glyph), quiet)
                            },
                            onFailure = {
                                Log.e(TAG, "complete_pebble failed", it)
                                model.fail(momentWriteErrorRes(it.toDataError()))
                                // The debounce skipped saves while completing; keep the last
                                // answers on the device in case the app dies on this error.
                                flushNow()
                            },
                        )
                }
            }
        }
    }
