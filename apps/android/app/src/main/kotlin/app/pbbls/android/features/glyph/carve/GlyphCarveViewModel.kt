package app.pbbls.android.features.glyph.carve

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.features.glyph.services.GlyphServicing
import app.pbbls.android.services.AchievementsServicing
import app.pbbls.android.ui.UiEffects
import app.pbbls.android.ui.runCatchingCancellable
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

private const val TAG = "glyph-carve"

data class GlyphCarveUiState(
    val name: String = "",
    val strokes: List<GlyphStroke> = emptyList(),
    val isSaving: Boolean = false,
    val didSaveFail: Boolean = false,
    val isConfirmingDiscard: Boolean = false,
) {
    /** Nothing drawn is nothing to save, whatever the name says. */
    val canSave: Boolean
        get() = strokes.isNotEmpty() && !isSaving

    /** Backing out of an empty canvas needs no confirmation. */
    val hasWork: Boolean
        get() = strokes.isNotEmpty()
}

sealed interface GlyphCarveEffect {
    data class Saved(
        val glyph: Glyph,
    ) : GlyphCarveEffect

    data object Cancelled : GlyphCarveEffect
}

/**
 * State holder for the carve surface (#849).
 *
 * **What rotation cost here was the drawing itself.** `strokes` lived in a
 * `remember`, so turning the phone mid-carve discarded every stroke with no
 * prompt — the discard confirmation this screen carries exists precisely
 * because those strokes are work, and a configuration change walked straight
 * past it. They live in the ViewModel now.
 *
 * The save has the familiar hole underneath it: `create` inserts the glyph and
 * `achievements.fireCheck()` follows it, so leaving between the two left a real
 * glyph whose achievement was never evaluated. Both are inside
 * `withContext(NonCancellable)`.
 *
 * **Not persisted to `SavedStateHandle`, and this is the closest call in the
 * migration.** Unsaved strokes are exactly what the contract says to persist —
 * they are work the server has never seen, and `GlyphStroke` is `@Serializable`.
 * But the cover's presentation is not persisted: both hosts keep their
 * `isPresentingCarve` in plain state, so after process death there is no canvas
 * for restored strokes to land on, and they would surface on some later,
 * unrelated tap of "+". Restoring a drawing properly means restoring the
 * surface that holds it, which is #852's back stack. Filed, not faked.
 */
@HiltViewModel
class GlyphCarveViewModel
    @Inject
    constructor(
        private val glyphService: GlyphServicing,
        private val achievements: AchievementsServicing,
    ) : ViewModel() {
        private val effectsOut = UiEffects<GlyphCarveEffect>(viewModelScope)
        val effects: Flow<GlyphCarveEffect> = effectsOut.flow

        private val _uiState = MutableStateFlow(GlyphCarveUiState())
        val uiState: StateFlow<GlyphCarveUiState> = _uiState.asStateFlow()

        fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }

        fun onStrokesChange(strokes: List<GlyphStroke>) = _uiState.update { it.copy(strokes = strokes) }

        // MARK: - Leaving

        /** Cancel: straight out on an empty canvas, a confirmation over a drawing. */
        fun onCancelRequested() {
            val state = _uiState.value
            if (state.isSaving) return
            if (state.hasWork) {
                _uiState.update { it.copy(isConfirmingDiscard = true) }
            } else {
                finish()
                effectsOut.emit(GlyphCarveEffect.Cancelled)
            }
        }

        fun dismissDiscard() = _uiState.update { it.copy(isConfirmingDiscard = false) }

        fun confirmDiscard() {
            finish()
            effectsOut.emit(GlyphCarveEffect.Cancelled)
        }

        // MARK: - Save

        fun save() {
            val state = _uiState.value
            if (!state.canSave) return
            _uiState.update { it.copy(isSaving = true, didSaveFail = false) }

            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        val glyph = glyphService.create(strokes = state.strokes, name = state.name)
                        // Inside the block: the glyph exists the moment the
                        // insert returns, and its achievement is not re-evaluated
                        // anywhere else.
                        achievements.fireCheck()
                        glyph
                    }.fold(
                        onSuccess = { glyph ->
                            finish()
                            effectsOut.emit(GlyphCarveEffect.Saved(glyph))
                        },
                        onFailure = {
                            Log.e(TAG, "glyph save failed", it)
                            _uiState.update { current -> current.copy(isSaving = false, didSaveFail = true) }
                        },
                    )
                }
            }
        }

        /**
         * Clear the canvas from a host that closed the carve surface without
         * going through save or discard.
         *
         * `GlyphPickerSheet` does exactly that: its `unwind()` flips `isCarving`
         * off for a dismiss gesture, a scrim tap or the record flow stepping
         * back, and never reaches [onCancelRequested]. That host lives under the
         * Path route, which is the start destination and never pops — so without
         * this the strokes would sit in the ViewModel for the rest of the
         * session and surface under someone's next "carve", armed with a
         * "Discard your glyph?" prompt about work that is not theirs.
         *
         * Idempotent: a save or a discard has already reset, so calling it again
         * costs nothing.
         */
        fun reset() = finish()

        /**
         * Clear the canvas for the next presentation.
         *
         * Unlike the form covers this one *does* reset its state: a drawing left
         * in memory would reappear under the next "+" as someone else's
         * half-finished glyph, which is worse than the one frame of empty canvas
         * the reset can cost on the way out.
         */
        private fun finish() {
            _uiState.value = GlyphCarveUiState()
        }
    }
