package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.features.profile.models.CollectionMode
import app.pbbls.android.services.AchievementsServicing
import app.pbbls.android.services.CollectionsServicing
import app.pbbls.android.services.ReferenceDataServicing
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

private const val TAG = "collection-form"

/** The [SavedStateHandle] key a nav key's argument will land under once #852's Task 17 lands. */
internal const val COLLECTION_FORM_ID_KEY = "collectionId"

data class CollectionFormUiState(
    /**
     * The collection id this form is for; null means create. Set by
     * [CollectionFormViewModel.start] up front, independent of whether the
     * by-id fetch below has resolved — see [SoulFormUiState.soulId].
     */
    val collectionId: String? = null,
    /** The loaded row, once the by-id fetch succeeds. Null during create, while loading, or on failure. */
    val original: Collection? = null,
    val name: String = "",
    val mode: CollectionMode? = null,
    val isSaving: Boolean = false,
    val didSaveFail: Boolean = false,
    /** True while fetching an existing collection by id. Always false for create. */
    val isLoading: Boolean = false,
    /** Set when the by-id fetch fails — the screen shows this instead of an empty form. */
    @StringRes val loadErrorRes: Int? = null,
) {
    val isEditing: Boolean
        get() = collectionId != null

    val canSave: Boolean
        get() =
            !isLoading &&
                loadErrorRes == null &&
                collectionFormCanSave(
                    originalName = original?.name,
                    originalMode = original?.mode,
                    name = name,
                    mode = mode,
                )

    @get:StringRes
    val titleRes: Int
        get() = if (isEditing) R.string.collection_edit_title else R.string.profile_collection_new

    @get:StringRes
    val saveErrorRes: Int
        get() = if (isEditing) R.string.settings_save_error else R.string.collection_save_error
}

/** One-shot results the hosting screen acts on. */
sealed interface CollectionFormEffect {
    data object Saved : CollectionFormEffect

    data object Dismiss : CollectionFormEffect
}

/**
 * State holder for the collection create/edit cover (#849, #852 Task 15).
 *
 * [SoulFormViewModel]'s twin: the same `rememberCoroutineScope` write hole (a
 * collection created on the server that neither the list nor the composer's
 * picker learned about), the same `NonCancellable` fix, the same
 * [start]/[finish] pair standing in for the lifecycle a cover does not have,
 * and — as of Task 15 — the same move from receiving the whole [Collection]
 * to loading it from an id (see [SoulFormViewModel]'s KDoc for the
 * [SavedStateHandle] rationale, which applies here verbatim).
 *
 * It has one more host than its twin — Profile's empty-carousel tile opens it
 * for a create — which makes the reset load-bearing in three places rather than
 * two. It persists nothing but the id being edited, for the reason spelled out
 * on [SoulFormViewModel].
 */
@HiltViewModel
class CollectionFormViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val collectionsService: CollectionsServicing,
        private val refs: ReferenceDataServicing,
        private val achievements: AchievementsServicing,
    ) : ViewModel() {
        private val effectsOut = UiEffects<CollectionFormEffect>(viewModelScope)
        val effects: Flow<CollectionFormEffect> = effectsOut.flow

        private val _uiState = MutableStateFlow(CollectionFormUiState())
        val uiState: StateFlow<CollectionFormUiState> = _uiState.asStateFlow()

        private var hasStarted = false
        private var startedFor: String? = null

        init {
            // Empty today — live once a real nav entry populates the handle
            // before construction. See [SoulFormViewModel]'s KDoc.
            start(savedState[COLLECTION_FORM_ID_KEY])
        }

        /** See [SoulFormViewModel.start] — the guard and the id-vs-object move are the same. */
        fun start(id: String?) {
            if (hasStarted && startedFor == id) return
            hasStarted = true
            startedFor = id
            savedState[COLLECTION_FORM_ID_KEY] = id
            if (id == null) {
                _uiState.value = CollectionFormUiState()
            } else {
                _uiState.value = CollectionFormUiState(collectionId = id, isLoading = true)
                loadCollection(id)
            }
        }

        private fun loadCollection(id: String) {
            viewModelScope.launch {
                runCatchingCancellable { collectionsService.loadCollection(id) }
                    .fold(
                        onSuccess = { collection ->
                            // A newer `start` must not be clobbered by this stale
                            // response landing late — see [SoulFormViewModel.loadSoul].
                            if (startedFor != id) return@fold
                            _uiState.value =
                                CollectionFormUiState(
                                    collectionId = id,
                                    original = collection,
                                    name = collection.name,
                                    mode = collection.mode,
                                )
                        },
                        onFailure = {
                            Log.e(TAG, "collection load failed", it)
                            if (startedFor != id) return@fold
                            _uiState.value =
                                CollectionFormUiState(collectionId = id, loadErrorRes = R.string.collection_form_load_error)
                        },
                    )
            }
        }

        // MARK: - Form edits

        fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }

        fun onModeChange(value: CollectionMode?) = _uiState.update { it.copy(mode = value) }

        fun onDismissRequested() {
            if (_uiState.value.isSaving) return
            finish()
            effectsOut.emit(CollectionFormEffect.Dismiss)
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

        private suspend fun performSave(state: CollectionFormUiState) {
            val trimmed = state.name.trim()
            val original = state.original
            runCatchingCancellable {
                if (original == null) {
                    collectionsService.create(name = trimmed, mode = state.mode)
                    achievements.fireCheck()
                } else {
                    collectionsService.update(
                        collectionId = original.id,
                        name = trimmed,
                        mode = state.mode,
                    )
                }
                // In the uncancellable section for the reason spelled out in
                // [SoulFormViewModel.performSave].
                refs.refreshCollections()
            }.fold(
                onSuccess = {
                    finish()
                    effectsOut.emit(CollectionFormEffect.Saved)
                },
                onFailure = {
                    Log.e(TAG, "collection save failed", it)
                    _uiState.update { current -> current.copy(isSaving = false, didSaveFail = true) }
                },
            )
        }

        /**
         * Release the start guard — see [SoulFormViewModel.finish], including
         * why it leaves `_uiState` alone.
         */
        private fun finish() {
            hasStarted = false
            startedFor = null
        }
    }
