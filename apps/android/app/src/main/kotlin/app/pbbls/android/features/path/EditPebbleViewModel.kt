package app.pbbls.android.features.path

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.data.KarmaNotificationService
import app.pbbls.android.core.data.MomentReadServicing
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.KarmaReason
import app.pbbls.android.core.model.PebbleCompletePayload
import app.pbbls.android.core.model.PebbleDraft
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.ui.momentWriteErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "edit-pebble"

sealed interface EditPebbleUiState {
    data object Loading : EditPebbleUiState

    /** The load failed, or the moment carries no pebble of yours to edit. */
    data object Error : EditPebbleUiState

    data class Content(
        /** Only the pebble half is read here; the moment half is Edit moment's. */
        val draft: PebbleDraft,
        /** The derived glyph the header stone draws; it follows the saved pebble, not the draft. */
        val glyph: PebbleGlyph?,
        val isSaving: Boolean = false,
        @StringRes val saveErrorRes: Int? = null,
    ) : EditPebbleUiState {
        /** A complete pebble (D2): valence, emotion, one domain. */
        val canSave: Boolean by lazy { !isSaving && draft.answers.isComplete }
    }
}

sealed interface EditPebbleEffect {
    data object Saved : EditPebbleEffect

    data object Dismissed : EditPebbleEffect
}

/**
 * Edit pebble (R5): emotion, domain, valence, souls, collection and privacy,
 * written with `complete_pebble` and nothing else. The photo and the moment's
 * own fields are Edit moment's.
 *
 * The write and its follow-ups run under `NonCancellable`: popping the entry
 * must not abort a request the server may already have accepted (#852), and
 * the karma notify and the achievement check hang off it and are not repeated.
 */
@HiltViewModel
class EditPebbleViewModel
    @Inject
    constructor(
        private val moments: MomentReadServicing,
        private val writes: MomentWriteServicing,
        private val karma: KarmaNotificationService,
        private val achievements: AchievementsServicing,
        private val haptics: HapticsPreference,
    ) : ViewModel() {
        private val effectsOut = UiEffects<EditPebbleEffect>(viewModelScope)
        val effects: Flow<EditPebbleEffect> = effectsOut.flow

        /** Pebbles' haptics switch (#1242 D23): the screen asks it at each buzz, so Settings applies at once. */
        val hapticsOn: Boolean get() = haptics.isOn

        private val _uiState = MutableStateFlow<EditPebbleUiState>(EditPebbleUiState.Loading)
        val uiState: StateFlow<EditPebbleUiState> = _uiState.asStateFlow()

        private var loadedMomentId: String? = null
        private var loadedPebbleId: String? = null

        private val content: EditPebbleUiState.Content?
            get() = _uiState.value as? EditPebbleUiState.Content

        /** Guarded: a rotation re-runs the screen's `LaunchedEffect` and must not discard edits. */
        fun start(momentId: String) {
            if (loadedMomentId == momentId) return
            loadedMomentId = momentId
            load(momentId)
        }

        fun retry() = loadedMomentId?.let { load(it) }

        private fun load(momentId: String) {
            _uiState.value = EditPebbleUiState.Loading
            viewModelScope.launch {
                runCatchingCancellable { moments.loadMoment(momentId) }
                    .fold(
                        onSuccess = { detail ->
                            _uiState.value =
                                if (detail.pebble == null) {
                                    // A stale menu or key: the read page offers Edit pebble only with a pebble.
                                    Log.e(TAG, "moment $momentId has no pebble to edit")
                                    EditPebbleUiState.Error
                                } else {
                                    loadedPebbleId = detail.pebble.id
                                    EditPebbleUiState.Content(draft = PebbleDraft.from(detail), glyph = detail.pebble.glyph)
                                }
                        },
                        onFailure = {
                            Log.e(TAG, "edit pebble load failed", it)
                            _uiState.value = EditPebbleUiState.Error
                        },
                    )
            }
        }

        fun onDraftChange(draft: PebbleDraft) = updateContent { it.copy(draft = draft, saveErrorRes = null) }

        /** Wired into [app.pbbls.android.features.path.create.PebbleForm]'s soul picker — an inline soul creation fires the check (#852). */
        fun onAchievementCheck() = achievements.fireCheck()

        fun dismiss() {
            if (content?.isSaving == true) return
            viewModelScope.launch { effectsOut.emit(EditPebbleEffect.Dismissed) }
        }

        fun save() {
            val state = content ?: return
            if (!state.canSave) return
            val momentId = loadedMomentId ?: return
            val pebbleId = loadedPebbleId ?: return
            updateContent { it.copy(isSaving = true, saveErrorRes = null) }
            viewModelScope.launch {
                withContext(NonCancellable) {
                    // R13, iOS's method: `complete_pebble` records a row only when the total
                    // moves, so the newest row may be an older save's. The difference is this one's.
                    val karmaBefore = writes.karmaTotal(pebbleId)
                    runCatchingCancellable { writes.completePebble(PebbleCompletePayload.from(momentId, state.draft.answers)) }
                        .fold(
                            onSuccess = {
                                val karmaAfter = writes.karmaTotal(pebbleId)
                                if (karmaBefore != null && karmaAfter != null && karmaAfter > karmaBefore) {
                                    karma.notifyEarned(karmaAfter - karmaBefore, KarmaReason.PEBBLE_ENRICHED)
                                }
                                // An edit can newly qualify an emotion_first badge; once per complete_pebble (A-7).
                                achievements.fireCheck()
                                effectsOut.emit(EditPebbleEffect.Saved)
                            },
                            onFailure = { failure ->
                                Log.e(TAG, "complete_pebble failed", failure)
                                updateContent { it.copy(isSaving = false, saveErrorRes = momentWriteErrorRes(failure.toDataError())) }
                            },
                        )
                }
            }
        }

        private inline fun updateContent(crossinline block: (EditPebbleUiState.Content) -> EditPebbleUiState.Content) =
            _uiState.update { if (it is EditPebbleUiState.Content) block(it) else it }
    }
