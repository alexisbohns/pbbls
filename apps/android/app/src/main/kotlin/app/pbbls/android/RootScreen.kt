package app.pbbls.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.pbbls.android.features.auth.AuthMode
import app.pbbls.android.features.auth.AuthScreen
import app.pbbls.android.features.connections.AcceptInviteScreen
import app.pbbls.android.features.karma.AchievementMomentOverlay
import app.pbbls.android.features.karma.KarmaOverlayHost
import app.pbbls.android.features.karma.LocalAchievementNotificationService
import app.pbbls.android.features.karma.LocalKarmaNotificationService
import app.pbbls.android.features.onboarding.OnboardingGate
import app.pbbls.android.features.onboarding.OnboardingScreen
import app.pbbls.android.features.onboarding.OnboardingSteps
import app.pbbls.android.features.welcome.WelcomeScreen
import app.pbbls.android.navigation.NavTransitions
import app.pbbls.android.navigation.Navigator
import app.pbbls.android.navigation.PebblesKey
import app.pbbls.android.navigation.pebblesEntries
import app.pbbls.android.services.LocalConnectionsService
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSnapURLCache
import app.pbbls.android.services.LocalSupabaseService
import app.pbbls.android.services.OnboardingPreferences
import app.pbbls.android.theme.PebblesTheme
import kotlinx.coroutines.launch

/**
 * Top-level auth gate — the `RootView` analog (D5). The gate is conditional
 * composition, not navigation:
 *   - `canShowAuthedTabs` (session AND auth resolved) → [PathScreen], with
 *     [OnboardingScreen] as a full-screen overlay the first time a user id
 *     appears while `hasSeenOnboarding` is false.
 *   - otherwise → a NavDisplay (Welcome → Auth), with Welcome revealing its
 *     content once auth has settled to "no session".
 *
 * There is no fixed splash duration here (#846): the system splash owns the
 * launch hold and [MainActivity] releases it on the same `isInitializing`
 * signal this gate reads, so a warm signed-in launch lands on Path immediately.
 * The Rive logo is Welcome's hero, not a gate.
 */
@Composable
fun RootScreen() {
    val supabase = LocalSupabaseService.current
    val palettes = LocalEmotionPaletteService.current
    val referenceData = LocalReferenceDataService.current
    val karma = LocalKarmaNotificationService.current
    val achievementNotify = LocalAchievementNotificationService.current
    val snapUrls = LocalSnapURLCache.current
    val connections = LocalConnectionsService.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasSeenOnboarding by rememberSaveable { mutableStateOf(OnboardingPreferences.hasSeenOnboarding(context)) }
    var isPresentingOnboarding by rememberSaveable { mutableStateOf(false) }

    // Distinguishes "signed out" from "auth has not resolved yet", which are the
    // same `userId == null` to everything above.
    var hasHadSession by rememberSaveable { mutableStateOf(false) }

    // supabase.start() collects the auth-status stream for the app's lifetime.
    LaunchedEffect(Unit) { supabase.start() }
    // Warm the emotion-palette cache concurrently with the launch — the
    // RootView `.task { await palettes.load() }` analog. Path renders with a
    // warm cache; misses fall back to accent.
    LaunchedEffect(Unit) { palettes.load() }

    val session = supabase.session
    val isInitializing = supabase.isInitializing
    // session?.user?.id is null when this composes, so the first authenticated
    // status delivers a real null→id transition even for already-signed-in users.
    val userId = session?.user?.id

    val canShowAuthedTabs = session != null && !isInitializing
    val welcomeContentRevealed = session == null && !isInitializing

    LaunchedEffect(userId) {
        if (OnboardingGate.shouldPresent(userId, hasSeenOnboarding)) {
            isPresentingOnboarding = true
        }
        // Sign-out flushes the signed-URL cache (the iOS RootView
        // `.onChange(of: session == nil)` analog). Firing on the initial null
        // is a harmless clear of an empty cache.
        if (userId == null) {
            snapUrls?.invalidateAll()
            // A parked invite belongs to the session it arrived in. Dropping the
            // session removes the accept surface from the composition WITHOUT
            // dismissing it, so nothing calls the ViewModel's reset — and the
            // token would re-present it on the next sign-in, guard satisfied,
            // still showing the previous session's result. Only on a REAL
            // sign-out: clearing on the initial null would throw away a token
            // parked by a cold-start App Link before auth resolves (D12).
            if (hasHadSession) connections.pendingInviteToken = null
        }
        hasHadSession = userId != null
    }

    // Warm the create/edit reference lists (domains, souls, collections) once a
    // user id resolves — souls/collections are RLS-scoped, so this waits for the
    // session rather than firing on Unit like the palette cache (D11). Kept in
    // its own effect so its network suspension never delays the onboarding gate.
    LaunchedEffect(userId) {
        if (userId != null) {
            referenceData.load()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PebblesTheme.colors.system.background),
    ) {
        if (canShowAuthedTabs) {
            AuthedNavDisplay(onSignOut = { scope.launch { supabase.signOut() } })
            if (isPresentingOnboarding) {
                OnboardingScreen(
                    steps = OnboardingSteps.all,
                    onFinish = {
                        OnboardingPreferences.setHasSeenOnboarding(context, true)
                        hasSeenOnboarding = true
                        isPresentingOnboarding = false
                    },
                )
            }
            // Karma + achievement flashes float above the authed surfaces
            // (create/detail live inside PathScreen, so they're below) —
            // drawn last for z-order (D9).
            KarmaOverlayHost(service = karma, modifier = Modifier.fillMaxSize())
            AchievementMomentOverlay(
                service = achievementNotify,
                modifier = Modifier.fillMaxSize(),
            )
            // An invite link can land at any moment; the accept surface sits
            // above the nav host so it is reachable from any screen, and only
            // once there is a session to accept with (M49, design D12).
            connections.pendingInviteToken?.let { token ->
                AcceptInviteScreen(
                    token = token,
                    onDismiss = { connections.pendingInviteToken = null },
                )
            }
        } else {
            // The funnel calls supabase-kt through its own ViewModels now, so
            // this gate only decides WHICH tree is up, not how it signs in.
            WelcomeAuthNavDisplay(contentRevealed = welcomeContentRevealed)
        }
    }
}

/**
 * Authed navigation (#852). One [NavDisplay] over one saveable back stack,
 * replacing the NavHost. The IA is unchanged from the NavHost it replaces —
 * Part 2 introduces the four-tab bar (D11).
 *
 * `rememberViewModelStoreNavEntryDecorator` is what scopes a `hiltViewModel()`
 * to its entry rather than to the composition that happens to host it, so a
 * popped entry takes its ViewModel with it.
 */
@Composable
private fun AuthedNavDisplay(onSignOut: () -> Unit) {
    val backStack = rememberNavBackStack(PebblesKey.Path)
    val navigator = remember(backStack) { Navigator(backStack) }
    NavDisplay(
        backStack = backStack,
        onBack = { navigator.goBack() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider = entryProvider { pebblesEntries(navigator = navigator, onSignOut = onSignOut) },
    )
}

@Composable
private fun WelcomeAuthNavDisplay(contentRevealed: Boolean) {
    val backStack = rememberNavBackStack(PebblesKey.Welcome)
    val navigator = remember(backStack) { Navigator(backStack) }
    NavDisplay(
        backStack = backStack,
        onBack = { navigator.goBack() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<PebblesKey.Welcome>(metadata = NavTransitions.forKey(PebblesKey.Welcome)) {
                    WelcomeScreen(
                        contentRevealed = contentRevealed,
                        onCreateAccount = { navigator.navigate(PebblesKey.Auth(AuthMode.SIGNUP)) },
                        onLogin = { navigator.navigate(PebblesKey.Auth(AuthMode.LOGIN)) },
                    )
                }
                entry<PebblesKey.Auth>(metadata = NavTransitions.forKey(PebblesKey.Auth(AuthMode.LOGIN))) { key ->
                    AuthScreen(initialMode = key.mode)
                }
            },
    )
}
