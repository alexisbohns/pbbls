package app.pbbls.android.features.home.guides

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.HomeServicing
import app.pbbls.android.core.model.home.Guide
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "guide"

/** What a guide's page shows (#1383 D13; iOS `HomeView.guideResolution`). */
sealed interface GuideUiState {
    /** The readings are being read: a spinner. */
    data object Loading : GuideUiState

    /** The reading for the slug. */
    data class Found(
        val guide: Guide,
    ) : GuideUiState

    /**
     * "This reading isn't available": no published reading has the slug, or the read failed. iOS reads a failure
     * the same way, so a page restored on a dead network does not spin for ever. No Try again, as on iOS.
     */
    data object Missing : GuideUiState
}

/**
 * One About Pebbles reading, pushed from Home (#1383 D13). [start] reads the published readings and keeps the one
 * with the key's slug. A page of its own rather than Home's list handed over: the key alone survives process death,
 * and Home's ViewModel is another entry's.
 */
@HiltViewModel
class GuideViewModel
    @Inject
    constructor(
        private val home: HomeServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<GuideUiState>(GuideUiState.Loading)
        val uiState: StateFlow<GuideUiState> = _uiState.asStateFlow()

        private var slug: String? = null

        /** Opens the page on [slug]; a second call for the same slug (a recomposition, a rotation) does nothing. */
        fun start(slug: String) {
            if (this.slug == slug) return
            this.slug = slug
            _uiState.value = GuideUiState.Loading
            viewModelScope.launch {
                _uiState.value =
                    runCatchingCancellable { home.guides() }.fold(
                        onSuccess = { guides ->
                            guides.firstOrNull { it.slug == slug }?.let(GuideUiState::Found) ?: run {
                                Log.w(TAG, "no published guide '$slug'")
                                GuideUiState.Missing
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "guides read for '$slug' failed", error)
                            GuideUiState.Missing
                        },
                    )
            }
        }
    }
