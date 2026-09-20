package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
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

data class CollectionFormUiState(
    /** Null means create — the title, the write and the save gate all key off it. */
    val original: Collection? = null,
    val name: String = "",
    val mode: CollectionMode? = null,
    val isSaving: Boolean = false,
    val didSaveFail: Boolean = false,
) {
    val isEditing: Boolean
        get() = original != null

    val canSave: Boolean
        get() =
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
 * State holder for the collection create/edit cover (#849).
 *
 * [SoulFormViewModel]'s twin: the same `rememberCoroutineScope` write hole (a
 * collection created on the server that neither the list nor the composer's
 * picker learned about), the same `NonCancellable` fix, and the same
 * [start]/[finish] pair standing in for the lifecycle a cover does not have.
 *
 * It has one more host than its twin — Profile's empty-carousel tile opens it
 * for a create — which makes the reset load-bearing in three places rather than
 * two. It persists nothing, for the reason spelled out on [SoulFormViewModel].
 */
@HiltViewModel
class CollectionFormViewModel
    @Inject
    constructor(
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

        /** See [SoulFormViewModel.start] — the guard is the same two fields. */
        fun start(original: Collection?) {
            if (hasStarted && startedFor == original?.id) return
            hasStarted = true
            startedFor = original?.id
            _uiState.value =
                CollectionFormUiState(
                    original = original,
                    name = original?.name.orEmpty(),
                    mode = original?.mode,
                )
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
