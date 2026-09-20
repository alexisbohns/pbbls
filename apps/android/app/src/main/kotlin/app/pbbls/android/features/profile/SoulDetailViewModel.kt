package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.services.PebbleWriteServicing
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.services.SoulsServicing
import app.pbbls.android.ui.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "soul-detail"

/**
 * One soul and the pebbles tagged with it (#849).
 *
 * The header and the list load together and fail together — a soul without its
 * pebbles is not a screen this surface can render — so they are one [Content],
 * not two independently-nullable fields with a shared spinner flag.
 */
sealed interface SoulDetailUiState {
    data object Loading : SoulDetailUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : SoulDetailUiState

    data class Content(
        val soul: SoulWithGlyph,
        val pebbles: List<Pebble>,
    ) : SoulDetailUiState
}

/** The edit cover, the pebble-edit cover, and the delete dialogs. */
data class SoulDetailCovers(
    val isPresentingEdit: Boolean = false,
    val editingPebbleId: String? = null,
    val pendingDeletion: Pebble? = null,
    val didDeleteFail: Boolean = false,
)

/**
 * State holder for the pushed soul detail (#849).
 *
 * Two things it fixes beyond moving code:
 *
 * - **Rotation.** The load hung off `LaunchedEffect(soulId, loadKey)` over nine
 *   `remember(soulId)` values, so turning the phone re-fetched the soul and its
 *   pebbles and flashed the spinner over a list the user was reading. [start]
 *   is guarded on the id, so a re-run after a configuration change is free.
 * - **The pebble delete.** It ran in `rememberCoroutineScope`; leaving between
 *   the request and the reload left a pebble on screen that the server had
 *   already dropped. It runs in [viewModelScope] under `NonCancellable` now.
 *
 * A real NavHost destination — its ViewModel dies with the back stack entry, so
 * there is nothing to reset. The form it hosts as a cover is a different story;
 * see [SoulFormViewModel].
 */
@HiltViewModel
class SoulDetailViewModel
    @Inject
    constructor(
        private val soulsService: SoulsServicing,
        private val writeService: PebbleWriteServicing,
        private val refs: ReferenceDataServicing,
    ) : ViewModel() {
        private var soulId: String? = null
        private var soul: SoulWithGlyph? = null
        private var pebbles: List<Pebble> = emptyList()
        private var hasFailed = false
        private var isLoaded = false

        private val _uiState = MutableStateFlow<SoulDetailUiState>(SoulDetailUiState.Loading)
        val uiState: StateFlow<SoulDetailUiState> = _uiState.asStateFlow()

        private val _covers = MutableStateFlow(SoulDetailCovers())
        val covers: StateFlow<SoulDetailCovers> = _covers.asStateFlow()

        private var loadJob: Job? = null

        /**
         * Load [id], unless it is the one already loaded.
         *
         * The NavHost hands the screen only an id, so the screen asks for its
         * soul here rather than receiving the row from the list (the named
         * deviation from iOS, kept).
         */
        fun start(id: String) {
            if (soulId == id) return
            soulId = id
            load()
        }

        fun retry() = load()

        private fun load() {
            val id = soulId ?: return
            loadJob?.cancel()
            _uiState.value = SoulDetailUiState.Loading
            isLoaded = false
            loadJob = viewModelScope.launch { fetch(id) }
        }

        /** Refresh after a write, keeping the content that is already on screen. */
        private fun reload() {
            val id = soulId ?: return
            loadJob?.cancel()
            loadJob = viewModelScope.launch { fetch(id) }
        }

        private suspend fun fetch(id: String) {
            runCatchingCancellable {
                soulsService.loadSoul(id) to soulsService.loadPebbles(id)
            }.fold(
                onSuccess = { (loadedSoul, loadedPebbles) ->
                    soul = loadedSoul
                    pebbles = loadedPebbles
                    hasFailed = false
                    isLoaded = true
                },
                onFailure = {
                    Log.e(TAG, "soul detail load failed", it)
                    // A failed post-write refresh keeps the detail that is
                    // already up, rather than replacing it with the error state.
                    if (!isLoaded) hasFailed = true
                },
            )
            publish()
        }

        private fun publish() {
            val current = soul
            _uiState.value =
                when {
                    hasFailed -> SoulDetailUiState.Error(R.string.soul_detail_load_error)
                    !isLoaded || current == null -> SoulDetailUiState.Loading
                    else -> SoulDetailUiState.Content(soul = current, pebbles = pebbles)
                }
        }

        // MARK: - Covers

        fun openEdit() = _covers.update { it.copy(isPresentingEdit = true) }

        fun closeEdit() = _covers.update { it.copy(isPresentingEdit = false) }

        /** The soul was edited: close the cover and refresh (see [SoulsListViewModel.onSoulSaved]). */
        fun onSoulSaved() {
            _covers.update { it.copy(isPresentingEdit = false) }
            reload()
        }

        fun openPebble(pebbleId: String) = _covers.update { it.copy(editingPebbleId = pebbleId) }

        fun closePebble() = _covers.update { it.copy(editingPebbleId = null) }

        fun onPebbleSaved() {
            _covers.update { it.copy(editingPebbleId = null) }
            reload()
        }

        // MARK: - Delete

        fun requestDelete(pebble: Pebble) = _covers.update { it.copy(pendingDeletion = pebble) }

        fun cancelDelete() = _covers.update { it.copy(pendingDeletion = null) }

        fun dismissDeleteError() = _covers.update { it.copy(didDeleteFail = false) }

        fun confirmDelete() {
            val target = _covers.value.pendingDeletion ?: return
            _covers.update { it.copy(pendingDeletion = null) }
            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        writeService.delete(target.id)
                        refs.refreshSouls()
                    }.fold(
                        onSuccess = { reload() },
                        onFailure = {
                            Log.e(TAG, "delete pebble failed", it)
                            _covers.update { covers -> covers.copy(didDeleteFail = true) }
                        },
                    )
                }
            }
        }
    }
