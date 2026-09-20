package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.SystemGlyph
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.services.AchievementsServicing
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.services.SoulsServicing
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

private const val TAG = "soul-form"

data class SoulFormUiState(
    /** Null means create — the title, the write and the save gate all key off it. */
    val original: SoulWithGlyph? = null,
    val name: String = "",
    val glyphId: String = SystemGlyph.DEFAULT,
    /** The picked (or fetched default) glyph, for the row's thumbnail. */
    val glyph: Glyph? = null,
    val isSaving: Boolean = false,
    val didSaveFail: Boolean = false,
    val isPresentingPicker: Boolean = false,
) {
    val isEditing: Boolean
        get() = original != null

    val canSave: Boolean
        get() =
            soulFormCanSave(
                originalName = original?.name,
                originalGlyphId = original?.glyphId,
                name = name,
                glyphId = glyphId,
            )

    @get:StringRes
    val titleRes: Int
        get() = if (isEditing) R.string.soul_edit_title else R.string.create_soul_title

    @get:StringRes
    val saveErrorRes: Int
        get() = if (isEditing) R.string.settings_save_error else R.string.soul_save_error
}

/** One-shot results the hosting screen acts on. */
sealed interface SoulFormEffect {
    data object Saved : SoulFormEffect

    data object Dismiss : SoulFormEffect
}

/**
 * State holder for the soul create/edit cover (#849).
 *
 * **The write this moves.** `save()` ran in `rememberCoroutineScope`, so leaving
 * the cover between the insert landing and `onSaved()` firing meant the row
 * existed on the server while the host never reloaded and never refreshed the
 * composer's soul cache — a soul the user had just created, invisible in both
 * the grid and the picker until the next cold start. The create path also fires
 * the achievement check, which is not repeated. The whole response section runs
 * under `withContext(NonCancellable)` inside [viewModelScope].
 *
 * **It is a cover, not a NavHost destination**, so `hiltViewModel()` scopes it
 * to whichever back stack entry hosts it — the souls list for a create, the
 * soul detail for an edit — and that entry outlives the cover many times over.
 * [start] is therefore guarded and [finish] is explicit: without the reset, the
 * next "+" opens onto the soul just saved. #852 turns these covers into real
 * destinations and takes the pair away.
 *
 * **No [androidx.lifecycle.SavedStateHandle].** A typed name is the kind of
 * thing the contract says to persist, but the *presentation* is not persisted:
 * the host keeps `isPresentingCreate` in a plain `MutableStateFlow`, so after
 * process death there is no cover for a restored draft to land in. The draft
 * could therefore only ever reappear on a later, unrelated presentation — a "+"
 * pre-filled with a name the user typed for a different soul ten minutes ago.
 * Restoring it properly means persisting the cover flag too, which is #852's
 * back stack, not this part. Its siblings `CreatePebbleViewModel` and
 * `EditPebbleViewModel` persist nothing for the same reason.
 */
@HiltViewModel
class SoulFormViewModel
    @Inject
    constructor(
        private val soulsService: SoulsServicing,
        private val refs: ReferenceDataServicing,
        private val achievements: AchievementsServicing,
    ) : ViewModel() {
        private val effectsOut = UiEffects<SoulFormEffect>(viewModelScope)
        val effects: Flow<SoulFormEffect> = effectsOut.flow

        private val _uiState = MutableStateFlow(SoulFormUiState())
        val uiState: StateFlow<SoulFormUiState> = _uiState.asStateFlow()

        private var hasStarted = false
        private var startedFor: String? = null

        /**
         * Seed from the row the host already has (edit) or from nothing
         * (create).
         *
         * Guarded on the pair "started at all" + "for which soul", so a rotation
         * cannot re-seed over what the user has typed, while opening the form on
         * a *different* soul starts clean. Create mode has no id, which is why
         * the guard is two fields rather than a nullable one.
         */
        fun start(original: SoulWithGlyph?) {
            if (hasStarted && startedFor == original?.id) return
            hasStarted = true
            startedFor = original?.id
            _uiState.value =
                SoulFormUiState(
                    original = original,
                    name = original?.name.orEmpty(),
                    glyphId = original?.glyphId ?: SystemGlyph.DEFAULT,
                    glyph = original?.glyph,
                )
            if (original == null) loadDefaultGlyph()
        }

        /**
         * Create starts on the system default glyph; fetch its strokes so the row
         * shows a real thumbnail (the `CreateSoulSheet.loadDefaultGlyph` analog).
         *
         * Re-checked after the fetch: a picker selection made while it was in
         * flight must not be clobbered by the late default.
         */
        private fun loadDefaultGlyph() {
            viewModelScope.launch {
                runCatchingCancellable { soulsService.loadGlyph(SystemGlyph.DEFAULT) }
                    .fold(
                        onSuccess = { fetched ->
                            _uiState.update {
                                if (it.glyph == null && it.glyphId == SystemGlyph.DEFAULT) {
                                    it.copy(glyph = fetched)
                                } else {
                                    it
                                }
                            }
                        },
                        // The dashed placeholder still works as a tap target.
                        onFailure = { Log.e(TAG, "default glyph fetch failed", it) },
                    )
            }
        }

        // MARK: - Form edits

        fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }

        fun openPicker() = _uiState.update { it.copy(isPresentingPicker = true) }

        fun closePicker() = _uiState.update { it.copy(isPresentingPicker = false) }

        fun onGlyphPicked(glyph: Glyph) =
            _uiState.update {
                it.copy(glyphId = glyph.id, glyph = glyph, isPresentingPicker = false)
            }

        fun onDismissRequested() {
            if (_uiState.value.isSaving) return
            finish()
            effectsOut.emit(SoulFormEffect.Dismiss)
        }

        // MARK: - Save

        fun save() {
            val state = _uiState.value
            if (!state.canSave || state.isSaving) return
            _uiState.update { it.copy(isSaving = true, didSaveFail = false) }
            viewModelScope.launch {
                withContext(NonCancellable) { performSave(state) }
            }
        }

        private suspend fun performSave(state: SoulFormUiState) {
            val trimmed = state.name.trim()
            val original = state.original
            runCatchingCancellable {
                if (original == null) {
                    soulsService.create(name = trimmed, glyphId = state.glyphId)
                    achievements.fireCheck()
                } else {
                    soulsService.update(soulId = original.id, name = trimmed, glyphId = state.glyphId)
                }
                // Inside the uncancellable section, not in the host's `onSaved`
                // callback. The callback is reached through a [UiEffects] emit,
                // and an emit into a cancelled `viewModelScope` is silently
                // dropped — which is reachable, because the cover's
                // `BackHandler` is disabled while saving, so a back press during
                // the spinner falls through to the NavHost and pops the host
                // destination. The soul would exist on the server and stay
                // missing from the composer's picker for the rest of the
                // session: exactly the bug this class documents.
                refs.refreshSouls()
            }.fold(
                onSuccess = {
                    finish()
                    effectsOut.emit(SoulFormEffect.Saved)
                },
                onFailure = {
                    Log.e(TAG, "soul save failed", it)
                    _uiState.update { current -> current.copy(isSaving = false, didSaveFail = true) }
                },
            )
        }

        /**
         * Release the start guard, so the next presentation seeds from scratch.
         * Explicit because the ViewModel outlives the cover — see the class KDoc.
         *
         * It deliberately does **not** blank `_uiState`. The cover is still
         * composed when this runs — the host only drops it a hop later, when the
         * effect reaches it — so clearing the state here repaints the edit form
         * as an empty *create* form (title, name and glyph all flip) for a frame
         * on the way out. [start] overwrites the state wholesale anyway, so
         * there is nothing to gain by clearing it early.
         */
        private fun finish() {
            hasStarted = false
            startedFor = null
        }
    }
