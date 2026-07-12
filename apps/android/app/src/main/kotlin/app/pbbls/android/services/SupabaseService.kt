package app.pbbls.android.services

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.AppEnvironment
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Wraps the supabase-kt client and exposes auth state to Compose. Screens read
 * this from the [LocalSupabaseService] CompositionLocal (the
 * `@Environment(SupabaseService.self)` analog) and read [session] to decide what
 * to render. Actions (`signIn`, `signUp`, `signInWithGoogle`, `signOut`) are
 * called from the funnel that drives them. Ports
 * `apps/ios/Pebbles/Services/SupabaseService.swift`.
 *
 * The client initializer performs no network I/O, so constructing this during
 * app launch (`PebblesApp.onCreate`) is safe on the main thread.
 */
class SupabaseService {
    val client: SupabaseClient =
        createSupabaseClient(
            supabaseUrl = AppEnvironment.supabaseUrl,
            supabaseKey = AppEnvironment.supabaseAnonKey,
        ) {
            install(Auth) {
                // Google hosted OAuth returns via the pebbles://auth-callback deep
                // link; PKCE + this scheme/host are the D15/D16 contract. Shared
                // with iOS, so the dashboard allowlist is unchanged.
                flowType = FlowType.PKCE
                scheme = "pebbles"
                host = "auth-callback"
            }
            install(Postgrest)
            // Storage signs the private pebbles-media snap URLs (sub-project D).
            install(Storage)
            // Functions registers the compose-pebble / compose-pebble-update
            // edge-function surface (M39 sub-project A). PebbleWriteService posts
            // via raw Ktor to read the 5xx soft-success body (D2), but the plugin
            // is installed so the standard functions surface is available.
            install(Functions)
        }

    /** The current Supabase session, or null when signed out. */
    var session: UserSession? by mutableStateOf(null)
        private set

    /**
     * True until the first `sessionStatus` event resolves the persisted session.
     * `RootScreen` keeps showing the splash/Welcome while this is true so the
     * user never sees the auth screen flash before Path.
     */
    var isInitializing: Boolean by mutableStateOf(true)
        private set

    /** Guards the OAuth display-name patch so it runs at most once per process. */
    private var didAttemptNamePatch = false

    // Scope for work that REACTS to a status change (never inline in the
    // collector — see start()).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Collects supabase-kt's auth-status stream and keeps [session] +
     * [isInitializing] in sync for the lifetime of the app. Call exactly once
     * from `RootScreen`'s `LaunchedEffect`. Suspends forever under normal
     * operation.
     *
     * CRITICAL (ported verbatim from iOS): do NOT call back into supabase-kt
     * from inside this collector. The client holds an internal lock while
     * delivering status events; re-entering it from the callback deadlocks the
     * auth actor. Mutate state synchronously only — any network call reacting to
     * a status change is launched in a SEPARATE coroutine ([scope]), not inline.
     */
    suspend fun start() {
        client.auth.sessionStatus.collect { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    session = status.session
                    isInitializing = false
                    // A brand-new session (OAuth/sign-in/sign-up) may carry a
                    // provider name claim; patch the trigger-seeded 'Pebbler'
                    // display name from it — in a separate coroutine, never inline.
                    if (status.isNew && !didAttemptNamePatch) {
                        val name = nameFromUserMetadata(status.session.user?.userMetadata)
                        if (name != null) {
                            didAttemptNamePatch = true
                            scope.launch { patchDisplayNameIfDefault(name) }
                        }
                    }
                }

                is SessionStatus.NotAuthenticated -> {
                    session = null
                    isInitializing = false
                }

                is SessionStatus.RefreshFailure -> {
                    // Keep the last-known session; auth will retry. Initialization
                    // is nonetheless resolved.
                    isInitializing = false
                }

                SessionStatus.Initializing -> {
                    isInitializing = true
                }
            }
        }
        Log.e(TAG, "sessionStatus stream ended unexpectedly")
    }

    /** Sign in with email + password. Success flows back through [start]'s collector. */
    suspend fun signIn(
        email: String,
        password: String,
    ) {
        try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        } catch (e: Exception) {
            Log.e(TAG, "signIn failed", e)
            throw e
        }
    }

    /**
     * Sign up with email + password. Consent timestamps are captured now and
     * passed through `auth.users.raw_user_meta_data` via the sign-up data block.
     * Mirrors iOS: the current `handle_new_user` trigger does not copy them into
     * `public.profiles` — a separate `fix(db)` issue.
     */
    suspend fun signUp(
        email: String,
        password: String,
    ) {
        try {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                this.data = consentMetadata(Instant.now().toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "signUp failed", e)
            throw e
        }
    }

    /**
     * Sign in with Google via Supabase's hosted OAuth flow. supabase-kt opens a
     * Custom Tab that leaves the app and returns via the pebbles://auth-callback
     * deep link; the session lands asynchronously through [start]'s collector
     * (handled in `MainActivity.onNewIntent`). Mirrors the iOS reasoning
     * (`SupabaseService.swift`) — hosted web OAuth over the native SDK, no Google
     * Sign-In SDK in v1.
     */
    suspend fun signInWithGoogle() {
        try {
            client.auth.signInWith(Google)
        } catch (e: Exception) {
            Log.e(TAG, "signInWithGoogle failed", e)
            throw e
        }
    }

    /**
     * Sign out. Failures are logged but never surfaced — the local token is
     * wiped regardless and the collector emits `NotAuthenticated`.
     */
    suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "signOut failed", e)
        }
    }

    private fun nameFromUserMetadata(metadata: JsonObject?): String? {
        if (metadata == null) return null
        // OIDC `name` claim is preferred; `full_name` is a Supabase alias some
        // providers populate. Either is fine.
        for (key in listOf("full_name", "name")) {
            val value = metadata[key]?.jsonPrimitive?.contentOrNull
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    /**
     * Replaces `profiles.display_name` with [name] only if the row is still the
     * trigger default (`'Pebbler'`). Idempotent — safe to call on every new
     * OAuth session. Runs off the status collector (see [start]).
     */
    private suspend fun patchDisplayNameIfDefault(name: String) {
        val userId = session?.user?.id ?: return
        try {
            val row =
                client
                    .from("profiles")
                    .select(Columns.list("display_name")) {
                        filter { eq("user_id", userId) }
                    }.decodeSingleOrNull<JsonObject>()
            val current = row?.get("display_name")?.jsonPrimitive?.contentOrNull
            if (current != "Pebbler") return
            client
                .from("profiles")
                .update(buildJsonObject { put("display_name", name) }) {
                    filter { eq("user_id", userId) }
                }
        } catch (e: Exception) {
            Log.e(TAG, "patchDisplayName failed", e)
        }
    }

    companion object {
        private const val TAG = "auth"

        /**
         * Consent-metadata payload written into user metadata on sign-up. Both
         * timestamps are the moment of sign-up. Extracted as a pure function so
         * its shape is unit-tested without a live client.
         */
        fun consentMetadata(nowIso: String): JsonObject =
            buildJsonObject {
                put("terms_accepted_at", nowIso)
                put("privacy_accepted_at", nowIso)
            }
    }
}

/**
 * CompositionLocal for [SupabaseService] — the `@Environment(SupabaseService.self)`
 * analog (D4). `MainActivity` provides the single instance constructed in
 * `PebblesApp`; screens read it with `LocalSupabaseService.current`. One
 * `staticCompositionLocalOf` per service; more land with sub-project D.
 */
val LocalSupabaseService =
    staticCompositionLocalOf<SupabaseService> {
        error("LocalSupabaseService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
