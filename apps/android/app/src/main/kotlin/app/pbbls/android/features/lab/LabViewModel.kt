package app.pbbls.android.features.lab

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.features.lab.models.Log
import app.pbbls.android.features.lab.models.ReactionToggle
import app.pbbls.android.features.lab.services.LogsServicing
import app.pbbls.android.ui.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import android.util.Log as AndroidLog

private const val TAG = "lab"

/** Mirrors iOS `LabView.feedLimit` — the two surfaces show the same preview. */
private const val FEED_LIMIT = 5

/**
 * The Lab's four feeds (#849).
 *
 * The feeds fail **independently** — that is the design, ported as-is: one dead
 * feed leaves its section empty while the rest of the page renders. [Error] is
 * reached only when all four failed together, which is the honest signal that
 * the page as a whole is unavailable rather than one query being unlucky.
 */
sealed interface LabUiState {
    data object Loading : LabUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : LabUiState

    data class Content(
        val announcements: List<Log> = emptyList(),
        val changelog: List<Log> = emptyList(),
        val initiatives: List<Log> = emptyList(),
        val backlog: List<Log> = emptyList(),
        val reactedIds: Set<String> = emptySet(),
    ) : LabUiState
}

/** The two covers the Lab stacks over itself. */
data class LabCovers(
    val openAnnouncement: Log? = null,
    val seeAllMode: LogListMode? = null,
)

/**
 * State holder for the Lab (#849).
 *
 * **The write this moves is the reaction toggle.** It is optimistic: the heart
 * fills and the count moves before the request, and a failure reverts both.
 * That revert ran in `rememberCoroutineScope`, so leaving the Lab — or opening
 * the see-all cover over it — cancelled it and left a reaction showing as
 * registered when the server had rejected it, with a count one too high. The
 * lie survived until the next full reload, and re-tapping sent the *opposite*
 * request because the client believed its own optimism.
 *
 * The request and its revert are one `withContext(NonCancellable)` step now.
 */
@HiltViewModel
class LabViewModel
    @Inject
    constructor(
        private val logsService: LogsServicing,
    ) : ViewModel() {
        private var content = LabUiState.Content()
        private var hasFailed = false
        private var isLoaded = false

        private val _uiState = MutableStateFlow<LabUiState>(LabUiState.Loading)
        val uiState: StateFlow<LabUiState> = _uiState.asStateFlow()

        private val _covers = MutableStateFlow(LabCovers())
        val covers: StateFlow<LabCovers> = _covers.asStateFlow()

        private var loadJob: Job? = null
        private var resumeCount = 0

        /**
         * Bumped by every fetch that publishes. A reaction's revert carries the
         * generation it optimised over and does nothing if a reload has since
         * replaced `content` — otherwise the revert would subtract its +1 from
         * the server's own count, which never had it.
         */
        private var generation = 0

        init {
            load()
        }

        fun retry() = load()

        /**
         * Returning from the see-all cover does not need this — the cover is
         * composed over the Lab, so the destination never leaves RESUMED. It is
         * here for the trip back from Profile, which does.
         */
        fun onResumed() {
            resumeCount += 1
            if (resumeCount > 1) reload()
        }

        private fun load() {
            loadJob?.cancel()
            _uiState.value = LabUiState.Loading
            isLoaded = false
            loadJob = viewModelScope.launch { fetch() }
        }

        private fun reload() {
            loadJob?.cancel()
            loadJob = viewModelScope.launch { fetch() }
        }

        /** Cover-image URL for [log] — a pure projection, not a call. */
        fun coverImageUrl(log: Log): String? = logsService.coverImageUrl(log)

        private suspend fun fetch() {
            coroutineScope {
                val ann = async { feedOrNull("announcements") { logsService.announcements(null) } }
                val chg = async { feedOrNull("changelog") { logsService.changelog(FEED_LIMIT) } }
                val ini = async { feedOrNull("initiatives") { logsService.initiatives() } }
                val bck = async { feedOrNull("backlog") { logsService.backlog(FEED_LIMIT) } }
                val rea = async { feedOrNull("reactions") { logsService.myReactions() } }

                val annR = ann.await()
                val chgR = chg.await()
                val iniR = ini.await()
                val bckR = bck.await()
                val allDead = annR == null && chgR == null && iniR == null && bckR == null

                if (allDead) {
                    // Nothing came back. `content` is deliberately NOT written:
                    // overwriting it with four `orEmpty()` lists would render the
                    // page as just the community card — no error, no retry, and
                    // `isLoaded` already true so it never returns to Loading.
                    // A first load becomes the error state; a reload keeps what
                    // is on screen, which is the rule the rest of #849 follows.
                    hasFailed = !isLoaded
                } else {
                    content =
                        LabUiState.Content(
                            announcements = annR.orEmpty(),
                            changelog = chgR.orEmpty(),
                            initiatives = iniR.orEmpty(),
                            backlog = bckR.orEmpty(),
                            reactedIds = rea.await() ?: emptySet(),
                        )
                    hasFailed = false
                    isLoaded = true
                    generation += 1
                }
            }
            publish()
        }

        /** One feed's failure is its own: it logs, returns null and the page goes on. */
        private suspend fun <T> feedOrNull(
            label: String,
            block: suspend () -> T,
        ): T? =
            runCatchingCancellable { block() }
                .onFailure { AndroidLog.e(TAG, "$label feed failed", it) }
                .getOrNull()

        private fun publish() {
            _uiState.value =
                when {
                    hasFailed -> LabUiState.Error(R.string.lab_load_error)
                    !isLoaded -> LabUiState.Loading
                    else -> content
                }
        }

        // MARK: - Covers

        fun openAnnouncement(log: Log) = _covers.update { it.copy(openAnnouncement = log) }

        fun closeAnnouncement() = _covers.update { it.copy(openAnnouncement = null) }

        fun openSeeAll(mode: LogListMode) = _covers.update { it.copy(seeAllMode = mode) }

        fun closeSeeAll() = _covers.update { it.copy(seeAllMode = null) }

        // MARK: - Reactions

        fun toggleReaction(log: Log) {
            val current = _uiState.value as? LabUiState.Content ?: return
            val before = ReactionToggle.State(reactedIds = current.reactedIds, logs = current.backlog)
            val wasReacted = ReactionToggle.wasReacted(before, log.id)
            val next = ReactionToggle.toggle(before, log.id)

            content = content.copy(reactedIds = next.reactedIds, backlog = next.logs)
            publish()
            val optimisedOver = generation

            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        if (wasReacted) logsService.unreact(log.id) else logsService.react(log.id)
                    }.onFailure {
                        AndroidLog.e(TAG, "reaction toggle failed", it)
                        // A reload replaced the data this optimised over, so the
                        // +1 it would undo is not in there any more.
                        if (generation != optimisedOver) return@onFailure
                        val reverted =
                            ReactionToggle.revert(
                                ReactionToggle.State(reactedIds = content.reactedIds, logs = content.backlog),
                                log.id,
                                wasReacted,
                            )
                        content = content.copy(reactedIds = reverted.reactedIds, backlog = reverted.logs)
                        publish()
                    }
                }
            }
        }
    }
