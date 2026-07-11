package app.pbbls.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.pbbls.android.features.auth.AuthMode
import app.pbbls.android.features.auth.AuthScreen
import app.pbbls.android.features.onboarding.OnboardingGate
import app.pbbls.android.features.onboarding.OnboardingScreen
import app.pbbls.android.features.onboarding.OnboardingSteps
import app.pbbls.android.features.path.PathScreen
import app.pbbls.android.features.welcome.WelcomeScreen
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalSnapURLCache
import app.pbbls.android.services.LocalSupabaseService
import app.pbbls.android.services.OnboardingPreferences
import app.pbbls.android.theme.PebblesTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MIN_SPLASH_MILLIS = 2_500L
private const val ROUTE_WELCOME = "welcome"
private const val ROUTE_AUTH = "auth"

/**
 * Top-level auth gate — the `RootView` analog (D5). The gate is conditional
 * composition, not navigation:
 *   - `canShowAuthedTabs` (session AND resolved AND splash held ~2.5s) →
 *     [PathScreen], with [OnboardingScreen] as a full-screen overlay the first
 *     time a user id appears while `hasSeenOnboarding` is false.
 *   - otherwise → a NavHost (Welcome → Auth), with Welcome revealing its content
 *     only once auth has settled to "no session" and the splash hold elapsed.
 *
 * The Rive logo plays for at least the splash hold either way, satisfying the
 * "splash before Path" intent.
 */
@Composable
fun RootScreen() {
    val supabase = LocalSupabaseService.current
    val palettes = LocalEmotionPaletteService.current
    val snapUrls = LocalSnapURLCache.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasSeenOnboarding by rememberSaveable { mutableStateOf(OnboardingPreferences.hasSeenOnboarding(context)) }
    var isPresentingOnboarding by rememberSaveable { mutableStateOf(false) }
    var minSplashDone by rememberSaveable { mutableStateOf(false) }

    // supabase.start() collects the auth-status stream for the app's lifetime.
    LaunchedEffect(Unit) { supabase.start() }
    // Warm the emotion-palette cache concurrently with the splash hold — the
    // RootView `.task { await palettes.load() }` analog. Path renders with a
    // warm cache; misses fall back to accent.
    LaunchedEffect(Unit) { palettes.load() }
    LaunchedEffect(Unit) {
        delay(MIN_SPLASH_MILLIS)
        minSplashDone = true
    }

    val session = supabase.session
    val isInitializing = supabase.isInitializing
    // session?.user?.id is null when this composes, so the first authenticated
    // status delivers a real null→id transition even for already-signed-in users.
    val userId = session?.user?.id

    val canShowAuthedTabs = session != null && !isInitializing && minSplashDone
    val welcomeContentRevealed = session == null && !isInitializing && minSplashDone

    LaunchedEffect(userId) {
        if (OnboardingGate.shouldPresent(userId, hasSeenOnboarding)) {
            isPresentingOnboarding = true
        }
        // Sign-out flushes the signed-URL cache (the iOS RootView
        // `.onChange(of: session == nil)` analog). Firing on the initial null
        // is a harmless clear of an empty cache.
        if (userId == null) {
            snapUrls?.invalidateAll()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PebblesTheme.colors.system.background),
    ) {
        if (canShowAuthedTabs) {
            PathScreen(onSignOut = { scope.launch { supabase.signOut() } })
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
        } else {
            WelcomeAuthNavHost(
                contentRevealed = welcomeContentRevealed,
                onGoogleSignIn = { supabase.signInWithGoogle() },
                onSubmit = { mode, email, password ->
                    when (mode) {
                        AuthMode.LOGIN -> supabase.signIn(email, password)
                        AuthMode.SIGNUP -> supabase.signUp(email, password)
                    }
                },
            )
        }
    }
}

@Composable
private fun WelcomeAuthNavHost(
    contentRevealed: Boolean,
    onGoogleSignIn: suspend () -> Unit,
    onSubmit: suspend (AuthMode, String, String) -> Unit,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_WELCOME) {
        composable(ROUTE_WELCOME) {
            WelcomeScreen(
                contentRevealed = contentRevealed,
                onCreateAccount = { navController.navigate("$ROUTE_AUTH/${AuthMode.SIGNUP.route}") },
                onLogin = { navController.navigate("$ROUTE_AUTH/${AuthMode.LOGIN.route}") },
                onGoogleSignIn = onGoogleSignIn,
            )
        }
        composable(
            route = "$ROUTE_AUTH/{mode}",
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) { backStackEntry ->
            val mode = AuthMode.fromRoute(backStackEntry.arguments?.getString("mode"))
            AuthScreen(
                initialMode = mode,
                onSubmit = onSubmit,
                onGoogleSignIn = onGoogleSignIn,
            )
        }
    }
}
