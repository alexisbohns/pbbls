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
import app.pbbls.android.features.glyph.store.GlyphsListScreen
import app.pbbls.android.features.karma.KarmaOverlayHost
import app.pbbls.android.features.karma.LocalKarmaNotificationService
import app.pbbls.android.features.onboarding.OnboardingGate
import app.pbbls.android.features.onboarding.OnboardingScreen
import app.pbbls.android.features.onboarding.OnboardingSteps
import app.pbbls.android.features.path.PathScreen
import app.pbbls.android.features.profile.CollectionDetailScreen
import app.pbbls.android.features.profile.CollectionsListScreen
import app.pbbls.android.features.profile.ProfileScreen
import app.pbbls.android.features.profile.SoulDetailScreen
import app.pbbls.android.features.profile.SoulsListScreen
import app.pbbls.android.features.welcome.WelcomeScreen
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSnapURLCache
import app.pbbls.android.services.LocalSupabaseService
import app.pbbls.android.services.OnboardingPreferences
import app.pbbls.android.theme.PebblesTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MIN_SPLASH_MILLIS = 2_500L
private const val ROUTE_WELCOME = "welcome"
private const val ROUTE_AUTH = "auth"
private const val ROUTE_PATH = "path"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_SOULS = "souls"
private const val ROUTE_SOUL_DETAIL = "souls/{soulId}"
private const val ROUTE_COLLECTIONS = "collections"
private const val ROUTE_COLLECTION_DETAIL = "collections/{collectionId}"
private const val ROUTE_GLYPHS = "glyphs"

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
    val referenceData = LocalReferenceDataService.current
    val karma = LocalKarmaNotificationService.current
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
            AuthedNavHost(onSignOut = { scope.launch { supabase.signOut() } })
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
            // Karma flash floats above the authed surfaces (create/detail live
            // inside PathScreen, so they're below it) — drawn last for z-order (D9).
            KarmaOverlayHost(service = karma, modifier = Modifier.fillMaxSize())
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

/**
 * Authed navigation (D1): pushes — Path → Profile → souls/collections lists →
 * details — are real NavHost routes so predictive back and the back stack
 * behave natively; modal surfaces (pebble create/detail/edit, soul/collection
 * create/edit) stay conditionally-composed covers inside their screens (the
 * M39 D5 pattern). Sign-out lives on the Profile screen now; the session
 * dropping to null flips RootScreen's gate and unmounts this host.
 */
@Composable
private fun AuthedNavHost(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_PATH) {
        composable(ROUTE_PATH) {
            PathScreen(onProfile = { navController.navigate(ROUTE_PROFILE) })
        }
        composable(ROUTE_PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onSignOut = onSignOut,
                onOpenSouls = { navController.navigate(ROUTE_SOULS) },
                onOpenCollections = { navController.navigate(ROUTE_COLLECTIONS) },
                onOpenCollection = { collection ->
                    navController.navigate("$ROUTE_COLLECTIONS/${collection.id}")
                },
                onOpenGlyphs = { navController.navigate(ROUTE_GLYPHS) },
            )
        }
        composable(ROUTE_GLYPHS) {
            GlyphsListScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_SOULS) {
            SoulsListScreen(
                onBack = { navController.popBackStack() },
                onOpenSoul = { soul -> navController.navigate("$ROUTE_SOULS/${soul.id}") },
            )
        }
        composable(
            route = ROUTE_SOUL_DETAIL,
            arguments = listOf(navArgument("soulId") { type = NavType.StringType }),
        ) { backStackEntry ->
            SoulDetailScreen(
                soulId = backStackEntry.arguments?.getString("soulId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_COLLECTIONS) {
            CollectionsListScreen(
                onBack = { navController.popBackStack() },
                onOpenCollection = { collection ->
                    navController.navigate("$ROUTE_COLLECTIONS/${collection.id}")
                },
            )
        }
        composable(
            route = ROUTE_COLLECTION_DETAIL,
            arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            CollectionDetailScreen(
                collectionId = backStackEntry.arguments?.getString("collectionId").orEmpty(),
                onBack = { navController.popBackStack() },
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
