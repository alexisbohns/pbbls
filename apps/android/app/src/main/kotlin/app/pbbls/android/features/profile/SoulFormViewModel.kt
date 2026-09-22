package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.data.SoulsServicing
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.SystemGlyph
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

/** The [SavedStateHandle] key the nav key's argument lands under (#852). */
internal const val SOUL_FORM_ID_KEY = "soulId"

data class SoulFormUiState(
    /**
     * The soul id this form is for; null means create. Set by [SoulFormViewModel.start]
     * up front, independent of whether the by-id fetch below has resolved — the
     * title and [canSave]'s editing branch must not flip back to "create" while
     * an edit is loading or failed to load.
     */
    val soulId: String? = null,
    /** The loaded row, once the by-id fetch succeeds. Null during create, while loading, or on failure. */
    val original: SoulWithGlyph? = null,
    val name: String = "",
    val glyphId: String = SystemGlyph.DEFAULT,
    /** The picked (or fetched default, or loaded) glyph, for the row's thumbnail. */
    val glyph: Glyph? = null,
    val isSaving: Boolean = false,
    val didSaveFail: Boolean = false,
    val isPresentingPicker: Boolean = false,
    /** True while fetching an existing soul by id. Always false for create. */
    val isLoading: Boolean = false,
    /** Set when the by-id fetch fails — the screen shows this instead of an empty form. */
    @StringRes val loadErrorRes: Int? = null,
) {
    val isEditing: Boolean
        get() = soulId != null

    val canSave: Boolean
        get() =
            !isLoading &&
                loadErrorRes == null &&
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
 * State holder for the soul create/edit cover (#849, #852).
 *
 * **This ViewModel loads its own subject.** It used to receive the whole
 * [SoulWithGlyph] from whichever screen already had it loaded — but a
 * navigation key can only carry an id, so before this cover can become a real
 * Nav3 destination (#852) it has to fetch the row itself, the way
 * [SoulDetailViewModel] already fetches its soul from an id. [start] takes the
 * id instead of the object now, and a non-null id drives [loadSoul].
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
 * next "+" opens onto the soul just saved. #852 turns these covers into
 * real destinations and takes the pair away.
 *
 * **[SavedStateHandle] now carries the id being edited** — the one thing this
 * class holds that a nav key contract says to persist (root `AGENTS.md` /
 * `apps/android/CLAUDE.md`: "an id from a nav key IS appropriate to read from
 * `SavedStateHandle`"). [init] reads it eagerly, which is what will make a
 * future Nav3 entry work with zero changes to this file once the framework
 * populates the handle from the key's argument before construction. Today,
 * hosted as a cover, nothing populates it ahead of time — `hiltViewModel()`'s
 * Compose entry point has no hook for that outside of assisted injection or a
 * real back stack entry — so [start], called from the host's `LaunchedEffect`,
 * is what actually drives production loads, and it mirrors the id into the
 * handle for the same reason. See the PR description for the full note.
 */
@HiltViewModel
class SoulFormViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
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

        init {
            // Empty today (see class KDoc) — live once a real nav entry
            // populates the handle before this ViewModel is constructed.
            start(savedState[SOUL_FORM_ID_KEY])
        }

        /**
         * Seed from an id: load the row for a non-null one (edit), or start
         * clean and fetch the default glyph for null (create).
         *
         * Guarded on the pair "started at all" + "for which id", so a rotation
         * cannot re-seed over what the user has typed, while opening the form on
         * a *different* soul starts clean. Create mode has no id, which is why
         * the guard is two fields rather than a nullable one.
         */
        fun start(id: String?) {
            if (hasStarted && startedFor == id) return
            hasStarted = true
            startedFor = id
            savedState[SOUL_FORM_ID_KEY] = id
            if (id == null) {
                _uiState.value = SoulFormUiState()
                loadDefaultGlyph()
            } else {
                _uiState.value = SoulFormUiState(soulId = id, isLoading = true)
                loadSoul(id)
            }
        }

        private fun loadSoul(id: String) {
            viewModelScope.launch {
                runCatchingCancellable { soulsService.loadSoul(id) }
                    .fold(
                        onSuccess = { soul ->
                            // A newer `start` (a different id, or a reset) must
                            // not be clobbered by this stale response landing late.
                            if (startedFor != id) return@fold
                            _uiState.value =
                                SoulFormUiState(
                                    soulId = id,
                                    original = soul,
                                    name = soul.name,
                                    glyphId = soul.glyphId,
                                    glyph = soul.glyph,
                                )
                        },
                        onFailure = {
                            Log.e(TAG, "soul load failed", it)
                            if (startedFor != id) return@fold
                            _uiState.value =
                                SoulFormUiState(soulId = id, loadErrorRes = R.string.soul_form_load_error)
                        },
                    )
            }
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
                // `init` fires `start(null)` eagerly, before the host's
                // `LaunchedEffect` has a chance to call `start` with the real id
                // for an edit — skip the fetch entirely (not just the state
                // update below) once a later `start` has moved on, or an edit
                // form issues a default-glyph network call it will never use.
                if (startedFor != null) return@launch
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
