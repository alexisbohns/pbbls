package app.pbbls.android.features.achievements

import android.util.Log
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.AchievementMomentCard
import app.pbbls.android.core.data.AchievementNotificationService
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.DataError
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.CeremonyPull
import app.pbbls.android.core.model.achievements.Completion
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.features.achievements.copy.ceremonyBodyRes
import app.pbbls.android.features.achievements.copy.ceremonyTitleRes
import app.pbbls.android.features.achievements.sheets.isShownBy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "achievements-page"
private const val NOT_READY = "not_ready"

/** What the page shows (#1307): the house's three cases, nothing beside them. */
sealed interface AchievementsUiState {
    data object Loading : AchievementsUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : AchievementsUiState

    data class Content(
        val page: AchievementsPage,
    ) : AchievementsUiState
}

/** The open sheets, the last on top. [parked] hides them while a pebble opened from one is on screen. */
data class SheetStack(
    val sheets: List<AchievementSheet> = emptyList(),
    val parked: Boolean = false,
) {
    val visible: List<AchievementSheet> get() = if (parked) emptyList() else sheets
}

sealed interface AchievementsEffect {
    data class OpenMoment(
        val momentId: String,
    ) : AchievementsEffect

    data class Message(
        @StringRes val messageRes: Int,
    ) : AchievementsEffect
}

/** How a ceremony pull ended (iOS `AchievementsModel.PullOutcome`). */
sealed interface PullOutcome {
    /** A row came back: the ceremony, with the karma the pull paid. */
    data class Pulled(
        val karma: Int,
    ) : PullOutcome

    /**
     * No row, or no answer, but the read after it shows the completion pulled, when this session's
     * read saw it unpulled: this pull landed and its answer was lost. Plays without a karma line.
     */
    data object PulledWithoutKarma : PullOutcome

    /** Pulled before (the fresh read shows `pulled_at`, or this session pulled it), or no row. Never success. */
    data object AlreadyPulled : PullOutcome

    /** Not offered: the fresh read shows it not ready, or `not_ready`. */
    data object NotReady : PullOutcome

    /** Another pull is running; this one did nothing. */
    data object Busy : PullOutcome

    data object Failed : PullOutcome
}

/**
 * State holder for the achievements page (#1307), with iOS's `AchievementsModel` behaviour. The page
 * renders the server's payload and computes nothing; this holds what is shown, which sheets are
 * open, and the page's two writes (seen marks, the ceremony pull).
 *
 * - Stale-while-revalidate: the device's copy shows at once, then `check_achievements()` (the
 *   retroactive grant, D14, which never celebrates) and the read replace it. A failed read keeps
 *   what is shown and logs; only a first visit with nothing kept and no answer is the error.
 * - Reads can overlap (the load, a resume, a pull's re-read), so each takes a sequence number and
 *   only one newer than the last shown replaces the page. A cancelled read is not an error.
 * - The reader's zone is read when each load or pull starts, never frozen.
 * - A pull is decided on a page read in this session, never on the kept copy. One at a time, and
 *   every ready mark waits while it runs ([pulling]).
 */
@HiltViewModel
class AchievementsViewModel
    @Inject
    constructor(
        private val achievements: AchievementsServicing,
        private val notify: AchievementNotificationService,
    ) : ViewModel() {
        /** The reader's zone: the server cuts the days, the year and the 06:00 offer in it. */
        @VisibleForTesting
        internal var zone: () -> ZoneId = { ZoneId.systemDefault() }

        private val _uiState = MutableStateFlow<AchievementsUiState>(AchievementsUiState.Loading)
        val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

        private val _sheets = MutableStateFlow(SheetStack())
        val sheets: StateFlow<SheetStack> = _sheets.asStateFlow()

        /** The set whose ceremony is being pulled: while it is set, no ready mark can fire. */
        private val _pulling = MutableStateFlow<CompletionStack?>(null)
        val pulling: StateFlow<CompletionStack?> = _pulling.asStateFlow()

        private val _effects = UiEffects<AchievementsEffect>(viewModelScope)
        val effects: Flow<AchievementsEffect> = _effects.flow

        /** The sets this ViewModel has pulled: never asked again, so a ceremony plays once. */
        private val pulledThisSession = mutableSetOf<CompletionStack>()

        /** The last read asked for, and the newest one whose page is shown. */
        private var readsAsked = 0
        private var readShown = 0

        private var loadJob: Job? = null
        private var resumeCount = 0

        init {
            load()
        }

        fun retry() = load()

        /**
         * The entry leaves RESUMED on any trip to a pushed screen (a pebble's detail) and outlives it,
         * so the return reads the page again and brings back the sheets it parked. The first resume
         * is the screen's own start, which `init` already loaded.
         */
        fun onResumed() {
            resumeCount += 1
            if (resumeCount <= 1) return
            _sheets.update { it.copy(parked = false) }
            if (loadJob?.isActive == true) return
            loadJob = viewModelScope.launch { refresh(zone()) }
        }

        /** Pushes [sheet] (a second tap on the top one is a no-op) and marks its card seen (D10). */
        fun openSheet(sheet: AchievementSheet) {
            val stack = _sheets.value
            if (stack.sheets.lastOrNull() == sheet) return
            _sheets.value = stack.copy(sheets = stack.sheets + sheet, parked = false)
            achievements.markSeen(sheet.card)
        }

        /** [sheet] was dismissed: it goes, with every sheet above it. */
        fun closeSheet(sheet: AchievementSheet) {
            _sheets.update { stack ->
                val at = stack.sheets.lastIndexOf(sheet)
                if (at < 0) stack else stack.copy(sheets = stack.sheets.take(at))
            }
        }

        /** A sheet's row opens its pebble: park the sheets, so their windows never cover the detail. */
        fun openMoment(momentId: String) {
            _sheets.update { it.copy(parked = true) }
            _effects.emit(AchievementsEffect.OpenMoment(momentId))
        }

        /**
         * A ready mark: pulls the ceremony (D9). A pull presents the unlock moment with the karma it
         * paid, or none when its answer was lost; the sheets close first, since the moment's overlay
         * lives in the activity window, under any sheet's. A failure says so; anything else is quiet.
         */
        fun pullCeremony(stack: CompletionStack) {
            viewModelScope.launch {
                when (val outcome = pull(stack)) {
                    is PullOutcome.Pulled -> present(stack, outcome.karma)
                    PullOutcome.PulledWithoutKarma -> present(stack, karma = 0)
                    PullOutcome.Failed -> _effects.emit(AchievementsEffect.Message(R.string.achievements_ceremony_error))
                    PullOutcome.AlreadyPulled, PullOutcome.NotReady, PullOutcome.Busy -> Unit
                }
            }
        }

        /**
         * Pulls [stack]'s ceremony, then reads the page again so its mark goes. Only a completion a
         * read in this session shows ready and unpulled is pulled; before any read has landed it reads
         * first. An empty or lost answer is settled by the read after it (spec §3.4).
         */
        @VisibleForTesting
        internal suspend fun pull(stack: CompletionStack): PullOutcome {
            val name = stack.slug
            _pulling.value?.let { running ->
                Log.i(TAG, "pull_ceremony($name) asked while ${running.slug} is pulling")
                return PullOutcome.Busy
            }
            if (stack in pulledThisSession) {
                Log.i(TAG, "pull_ceremony($name) not asked: already pulled in this session")
                return PullOutcome.AlreadyPulled
            }
            _pulling.value = stack
            try {
                val zone = zone()
                if (readShown == 0) refresh(zone)
                if (readShown == 0) {
                    Log.e(TAG, "pull_ceremony($name) not asked: no page read to decide on")
                    return PullOutcome.Failed
                }
                val before = shown()?.completion(stack)
                when {
                    before == null -> {
                        Log.i(TAG, "pull_ceremony($name) not asked: no completion")
                        return PullOutcome.NotReady
                    }
                    before.pulledAt != null -> {
                        Log.i(TAG, "pull_ceremony($name) not asked: the read shows it pulled")
                        return PullOutcome.AlreadyPulled
                    }
                    !before.ready -> {
                        Log.i(TAG, "pull_ceremony($name) not asked: not offered yet")
                        return PullOutcome.NotReady
                    }
                }
                var outcome =
                    runCatchingCancellable { achievements.pullCeremony(stack, zone) }.fold(
                        onSuccess = { pull ->
                            when (pull) {
                                is CeremonyPull.Pulled -> PullOutcome.Pulled(pull.karmaGranted)
                                CeremonyPull.AlreadyPulled -> PullOutcome.AlreadyPulled
                            }
                        },
                        onFailure = { e ->
                            if (e.toDataError() == DataError.Conflict(NOT_READY)) {
                                Log.w(TAG, "pull_ceremony($name) is not ready", e)
                                PullOutcome.NotReady
                            } else {
                                Log.e(TAG, "pull_ceremony($name) failed", e)
                                PullOutcome.Failed
                            }
                        },
                    )
                val reread = refresh(zone)
                if (outcome == PullOutcome.AlreadyPulled || outcome == PullOutcome.Failed) {
                    if (reread && shown()?.completion(stack)?.pulledAt != null) {
                        Log.i(TAG, "pull_ceremony($name) answered nothing; the read shows it pulled")
                        outcome = PullOutcome.PulledWithoutKarma
                    } else if (outcome == PullOutcome.AlreadyPulled) {
                        Log.e(TAG, "pull_ceremony($name) returned no row")
                    }
                }
                if (outcome is PullOutcome.Pulled || outcome == PullOutcome.PulledWithoutKarma) {
                    pulledThisSession += stack
                    // The mark goes even when the read after the pull failed.
                    shown()?.takeIf { it.completion(stack)?.pulledAt == null }?.let { show(it.markPulled(stack, Instant.now())) }
                }
                return outcome
            } finally {
                _pulling.value = null
            }
        }

        private fun present(
            stack: CompletionStack,
            karma: Int,
        ) {
            _sheets.value = SheetStack()
            notify.present(
                listOf(
                    AchievementMomentCard(
                        slug = stack.catalogSlug,
                        record = null,
                        karmaGranted = karma,
                        titleRes = ceremonyTitleRes(stack),
                        bodyRes = ceremonyBodyRes(stack),
                    ),
                ),
            )
        }

        /** The kept copy when nothing is shown yet, then the retroactive grant, then the read. */
        private fun load() {
            if (loadJob?.isActive == true) return
            // Synchronously, before the launch: a retry tap must not leave the error on screen.
            if (_uiState.value !is AchievementsUiState.Content) _uiState.value = AchievementsUiState.Loading
            loadJob =
                viewModelScope.launch {
                    val zone = zone()
                    if (shown() == null && readShown == 0) {
                        runCatchingCancellable { achievements.cachedPage(zone) }
                            .onFailure { Log.w(TAG, "the device's copy could not be read", it) }
                            .getOrNull()
                            ?.let { kept -> if (shown() == null && readShown == 0) show(kept) }
                    }
                    // The retroactive grant first (D14), so a veteran's history is there on the read.
                    achievements.checkIgnoringFailure()
                    refresh(zone)
                }
        }

        /** Reads the page; true when the read landed. One that lands after a newer one shown is dropped. */
        private suspend fun refresh(zone: ZoneId): Boolean {
            readsAsked += 1
            val read = readsAsked
            return runCatchingCancellable { achievements.page(zone) }.fold(
                onSuccess = { fresh ->
                    if (read > readShown) {
                        readShown = read
                        show(fresh)
                    }
                    true
                },
                onFailure = {
                    Log.e(TAG, "get_achievements failed; keeping what is shown", it)
                    if (shown() == null) _uiState.value = AchievementsUiState.Error(R.string.achievements_load_error)
                    false
                },
            )
        }

        private fun shown(): AchievementsPage? = (_uiState.value as? AchievementsUiState.Content)?.page

        /** Shows [page], and lets go of any sheet whose section left it. */
        private fun show(page: AchievementsPage) {
            _uiState.value = AchievementsUiState.Content(page)
            _sheets.update { stack -> stack.copy(sheets = stack.sheets.filter { it.isShownBy(page) }) }
        }
    }

/** The completion a ceremony pulls from. */
internal fun AchievementsPage.completion(stack: CompletionStack): Completion? =
    when (stack) {
        CompletionStack.EMOTIONS -> emotions?.completion
        CompletionStack.DOMAINS -> domains?.completion
        CompletionStack.SIX_FIRSTS -> explorer?.completion
    }

/** The page as it stands after this client's pull: the completion pulled [at], no longer offered. */
internal fun AchievementsPage.markPulled(
    stack: CompletionStack,
    at: Instant,
): AchievementsPage {
    fun Completion.pulled() = copy(pulledAt = at, ready = false)
    return when (stack) {
        CompletionStack.EMOTIONS -> copy(emotions = emotions?.let { it.copy(completion = it.completion?.pulled()) })
        CompletionStack.DOMAINS -> copy(domains = domains?.let { it.copy(completion = it.completion?.pulled()) })
        CompletionStack.SIX_FIRSTS -> copy(explorer = explorer?.let { it.copy(completion = it.completion?.pulled()) })
    }
}
