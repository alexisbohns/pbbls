package app.pbbls.android.core.data

import android.util.Log
import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.common.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "account-deletion"

/** GoTrue's `error_code` for a password that does not match. */
private const val INVALID_CREDENTIALS = "invalid_credentials"

/**
 * The account-deletion flow as one value.
 *
 * Three independent booleans before (`showDeleteConfirm`, `isDeleting`,
 * `showDeleteError`) — eight combinations of which four are real, and
 * "confirming while deleting" was reachable.
 */
enum class DeletionState {
    IDLE,
    CONFIRMING,

    /** Withdrawing the health-data consent (#972): its own dialog, then the same deletion. */
    CONFIRMING_WITHDRAWAL,
    REAUTHENTICATING,
    DELETING,
    FAILED,
}

/** How the user proves it is them again (#976). */
enum class ReauthMethod { PASSWORD, GOOGLE }

/** What runs once the re-auth succeeds. */
enum class ReauthPurpose { DELETE, SAVE }

/**
 * The "Confirm it's you" dialog. [password] lives here and nowhere else — never
 * in a SavedStateHandle, and cleared after every attempt.
 */
data class ReauthUi(
    val purpose: ReauthPurpose,
    val method: ReauthMethod,
    val password: String = "",
    val isWorking: Boolean = false,
    @StringRes val errorRes: Int? = null,
)

/** The slice of a screen's state [AccountDeletionFlow] drives. */
data class AccountDeletionUi(
    val deletion: DeletionState = DeletionState.IDLE,
    val reauth: ReauthUi? = null,
)

/**
 * Account deletion behind a recent sign-in (#976): confirm, re-authenticate if
 * the sign-in is stale, erase through the delete-account edge function, then
 * sign out locally. Settings drives it, and so does the consent gate (#1030),
 * so erasure never depends on first agreeing to the processing.
 *
 * Not a ViewModel: each host owns one, runs it in its own scope, and keeps the
 * [AccountDeletionUi] slice in its own state through [read] and [write]. That
 * keeps the slice next to whatever else the host's dialogs read.
 *
 * Settings' save also asks for a recent sign-in, through [startReauth] with
 * [ReauthPurpose.SAVE]. The dialog is the same one, so it lives here, and every
 * purpose but [ReauthPurpose.DELETE] is handed back through [onReauthenticated].
 */
class AccountDeletionFlow(
    private val scope: CoroutineScope,
    private val profileService: ProfileServicing,
    private val supabase: SupabaseServicing,
    private val read: () -> AccountDeletionUi,
    private val write: ((AccountDeletionUi) -> AccountDeletionUi) -> Unit,
    private val onReauthenticated: (ReauthPurpose) -> Unit = {},
) {
    private var reauthJob: Job? = null

    fun request() = write { it.copy(deletion = DeletionState.CONFIRMING) }

    fun requestWithdrawal() = write { it.copy(deletion = DeletionState.CONFIRMING_WITHDRAWAL) }

    fun cancel() = write { it.copy(deletion = DeletionState.IDLE) }

    fun dismissError() = write { it.copy(deletion = DeletionState.IDLE) }

    fun confirm() {
        val deletion = read().deletion
        if (deletion == DeletionState.DELETING || deletion == DeletionState.REAUTHENTICATING) return
        if (!isSignInFresh()) {
            startReauth(ReauthPurpose.DELETE)
            return
        }
        runDelete()
    }

    /**
     * Drops any dialog and abandons a re-auth in flight. For a host that
     * outlives its user, such as the consent gate across a sign-out.
     */
    fun reset() {
        reauthJob?.cancel()
        reauthJob = null
        write { AccountDeletionUi() }
    }

    /**
     * Full erasure via the delete-account edge function (purge + storage +
     * auth user), then a local sign-out: the server session is already gone,
     * and `sessionStatus` dropping unmounts the authed NavHost to Welcome —
     * no navigation code needed here.
     *
     * `NonCancellable`: once the account is purged server-side, a client that
     * skipped its sign-out is left holding a session token for a user that no
     * longer exists.
     */
    private fun runDelete() {
        write { it.copy(deletion = DeletionState.DELETING) }
        scope.launch {
            withContext(NonCancellable) {
                runCatchingCancellable {
                    profileService.deleteAccount()
                    supabase.signOut()
                }.onFailure {
                    Log.e(TAG, "account deletion failed", it)
                    if (it.isReauthRequired()) {
                        startReauth(ReauthPurpose.DELETE)
                    } else {
                        write { state -> state.copy(deletion = DeletionState.FAILED) }
                    }
                }
            }
        }
    }

    // MARK: - Recent sign-in (#976)

    fun isSignInFresh(): Boolean = RecentAuth.isFresh(supabase.session?.accessToken)

    fun startReauth(purpose: ReauthPurpose) {
        // From the RAW identities: an email identity means a password to ask for.
        val hasPassword =
            supabase.session
                ?.user
                ?.identities
                .orEmpty()
                .any { it.provider == "email" }
        val method = if (hasPassword) ReauthMethod.PASSWORD else ReauthMethod.GOOGLE
        write {
            it.copy(
                reauth = ReauthUi(purpose = purpose, method = method),
                deletion = if (purpose == ReauthPurpose.DELETE) DeletionState.REAUTHENTICATING else it.deletion,
            )
        }
    }

    fun onReauthPasswordChange(value: String) = write { it.copy(reauth = it.reauth?.copy(password = value, errorRes = null)) }

    fun cancelReauth() {
        reauthJob?.cancel()
        reauthJob = null
        write {
            it.copy(
                reauth = null,
                deletion = if (it.deletion == DeletionState.REAUTHENTICATING) DeletionState.IDLE else it.deletion,
            )
        }
    }

    /**
     * Password: signs in again as the same user. Google: runs OAuth and
     * suspends until the fresh session lands, so the dialog stays in its
     * working state while the Custom Tab is up; Cancel abandons it.
     */
    fun submitReauth() {
        val reauth = read().reauth ?: return
        if (reauth.isWorking) return
        if (reauth.method == ReauthMethod.PASSWORD && reauth.password.isEmpty()) return
        write { it.copy(reauth = reauth.copy(isWorking = true, errorRes = null)) }
        reauthJob =
            scope.launch {
                runCatchingCancellable {
                    when (reauth.method) {
                        ReauthMethod.PASSWORD -> supabase.reauthenticate(reauth.password)
                        ReauthMethod.GOOGLE -> supabase.reauthenticateWithGoogle()
                    }
                }.fold(
                    onSuccess = { onReauthSucceeded(reauth.purpose) },
                    onFailure = { onReauthFailed(reauth, it) },
                )
            }
    }

    private fun onReauthSucceeded(purpose: ReauthPurpose) {
        // Cancel already closed the dialog: never run the action it was
        // abandoning (deletion is irreversible), however late the sign-in lands.
        if (read().reauth == null) return
        write { it.copy(reauth = null) }
        when (purpose) {
            ReauthPurpose.DELETE -> runDelete()
            else -> onReauthenticated(purpose)
        }
    }

    private fun onReauthFailed(
        reauth: ReauthUi,
        error: Throwable,
    ) {
        Log.e(TAG, "re-auth failed", error)
        if (error is ReauthAccountMismatchException) {
            // That other account was signed out; the session is gone and the
            // authed NavHost unmounts. Nothing further to run.
            cancelReauth()
            return
        }
        // GoTrue's wrong password is 400 `invalid_credentials`, which
        // supabase-kt carries as `AuthRestException.error` and toDataError
        // reads as Conflict. Matched exactly: every other 4xx (a throttle,
        // say) is also a Conflict, and is not the password's fault.
        val wrongPassword =
            reauth.method == ReauthMethod.PASSWORD &&
                error.toDataError().let { it == DataError.Conflict(INVALID_CREDENTIALS) || it is DataError.Unauthorized }
        write {
            it.copy(
                reauth =
                    it.reauth?.copy(
                        isWorking = false,
                        password = "",
                        errorRes = if (wrongPassword) R.string.reauth_wrong_password else R.string.reauth_error,
                    ),
            )
        }
    }
}
