package app.pbbls.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import app.pbbls.android.features.karma.AchievementMomentOverlay
import app.pbbls.android.features.karma.KarmaOverlayHost
import app.pbbls.android.features.onboarding.OnboardingGate
import app.pbbls.android.navigation.BarKey
import app.pbbls.android.navigation.NavigationState
import app.pbbls.android.navigation.Navigator
import app.pbbls.android.navigation.PebblesKey
import app.pbbls.android.navigation.PebblesNavigationBar
import app.pbbls.android.navigation.pebblesEntries
import app.pbbls.android.navigation.rememberNavigationState
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSnapURLCache
import app.pbbls.android.services.OnboardingPreferences
import app.pbbls.android.theme.PebblesTheme

/**
 * Top-level auth gate — the `RootView` analog (D5). Auth is now a condition,
 * not a composition branch (#852, design §5, D8): one [NavDisplay]
 * hosts every destination, and [RootViewModel] watches session state and
 * drives the stack (`replaceAll`) instead of `RootScreen` conditionally
 * composing a signed-in tree or a signed-out one. Sign-out therefore replaces
 * a stack; it never unmounts a tree — the acceptance criterion this migration
 * exists to satisfy.
 *
 * [RootDestination.Unresolved] leaves the initial `Welcome` seed on the back
 * stack, unrevealed (`welcomeContentRevealed = false`). That is harmless: the
 * system splash ([MainActivity]) covers the whole activity on exactly the
 * same `isInitializing` signal this gate reads, and releases at the same
 * moment [RootViewModel]'s own collector resolves the destination and the
 * effect below replaces the stack — both derive from the one snapshot state,
 * so they observe it in the same frame. There is no fixed splash duration
 * here (#846): a warm signed-in launch lands on Path immediately.
 */
@Composable
fun RootScreen() {
    val palettes = LocalEmotionPaletteService.current
    val referenceData = LocalReferenceDataService.current
    val snapUrls = LocalSnapURLCache.current
    val context = LocalContext.current

    val viewModel: RootViewModel = hiltViewModel()
    val root by viewModel.uiState.collectAsStateWithLifecycle()

    var hasSeenOnboarding by rememberSaveable { mutableStateOf(OnboardingPreferences.hasSeenOnboarding(context)) }

    // Four per-tab back stacks (#852, D4). Welcome is the initial seed of the
    // START tab for both real sign-outs and the Unresolved splash hold — see the
    // class doc above for why that is safe, and `rememberNavigationState`'s
    // `seed` doc for why it must not be Path.
    val navState = rememberNavigationState(seed = PebblesKey.Welcome)
    val navigator = remember(navState) { Navigator(navState) }

    // Warm the emotion-palette cache concurrently with the launch — the
    // RootView `.task { await palettes.load() }` analog. Path renders with a
    // warm cache; misses fall back to accent. `supabase.start()` itself now
    // lives in RootViewModel's init, not here.
    LaunchedEffect(Unit) { palettes.load() }

    val userId = root.userId
    val welcomeContentRevealed = root.destination == RootDestination.SignedOut

    // Sign-out flushes the signed-URL cache (the iOS RootView
    // `.onChange(of: session == nil)` analog). Firing on the initial null is a
    // harmless clear of an empty cache.
    LaunchedEffect(userId) {
        if (userId == null) snapUrls?.invalidateAll()
    }

    // Warm the create/edit reference lists (domains, souls, collections) once a
    // user id resolves — souls/collections are RLS-scoped, so this waits for the
    // session rather than firing on Unit like the palette cache (D11). Kept in
    // its own effect so its network suspension never delays the onboarding gate.
    LaunchedEffect(userId) {
        if (userId != null) referenceData.load()
    }

    // Onboarding gate stays pure composition — it needs OnboardingPreferences'
    // Context-backed flag, which has no place in a plain-JVM-tested ViewModel.
    val shouldPresentOnboarding = OnboardingGate.shouldPresent(userId, hasSeenOnboarding)

    // Drives the stack off RootViewModel's destination (§5, D8) — replaces the
    // `if (canShowAuthedTabs) … else …` composition branch this task deletes.
    // `when` over the sealed type with no `else`, so a new case is a compile
    // error.
    LaunchedEffect(root.destination) {
        when (root.destination) {
            RootDestination.Unresolved -> Unit // the splash still owns the screen
            RootDestination.SignedOut -> navigator.rootAt(PebblesKey.Welcome)
            RootDestination.SignedIn -> {
                // rootAt, NOT replaceAll: this effect also runs on a cold
                // restore, by which point the saveable back stack has already
                // restored where the user was. Re-rooting unconditionally would
                // throw that away and land everyone back on Path.
                val wasAlreadyAuthed = navigator.rootKey == PebblesKey.Path
                navigator.rootAt(PebblesKey.Path)
                // First-run gate: push Onboarding on top of the freshly-seeded
                // Path so a pending invite (below) waits behind it. Skipped on a
                // restore that was already authed — the restored stack is
                // authoritative there, and re-pushing would stack a second
                // Onboarding over whatever the user was doing.
                if (shouldPresentOnboarding && !wasAlreadyAuthed) {
                    navigator.navigate(PebblesKey.Onboarding)
                }
            }
        }
    }

    // A parked invite opens only once signed in AND past onboarding. This
    // ordering is the point of the whole task: the old parked-token field
    // composed the accept surface above onboarding, so a first-run user met a
    // stranger's invite before the app had introduced itself.
    LaunchedEffect(root.pendingInvite, root.destination, shouldPresentOnboarding) {
        val token = root.pendingInvite ?: return@LaunchedEffect
        if (root.destination != RootDestination.SignedIn) return@LaunchedEffect
        if (shouldPresentOnboarding) return@LaunchedEffect
        navigator.navigate(PebblesKey.AcceptInvite(token))
        viewModel.onInviteConsumed()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PebblesTheme.colors.system.background),
    ) {
        PebblesNavDisplay(
            navigator = navigator,
            state = navState,
            onSignOut = viewModel::onSignOut,
            welcomeContentRevealed = welcomeContentRevealed,
            onOnboardingFinished = {
                OnboardingPreferences.setHasSeenOnboarding(context, true)
                hasSeenOnboarding = true
                navigator.goBack()
            },
        )
        if (root.destination == RootDestination.SignedIn) {
            // Karma + achievement flashes only ever fire from signed-in
            // actions — floats above the nav host, drawn last for z-order (D9).
            KarmaOverlayHost(
                flash = root.karmaFlash,
                onDismiss = viewModel::onKarmaDismissed,
                modifier = Modifier.fillMaxSize(),
            )
            AchievementMomentOverlay(
                moment = root.achievementMoment,
                onAdvance = viewModel::onAchievementAdvanced,
                onDismiss = viewModel::onAchievementDismissed,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * One [NavDisplay] over the per-tab back stacks, for every destination — signed
 * in or out (#852). Renamed from `AuthedNavDisplay`: it is no longer
 * authed-only, since `RootScreen`'s effects above are what decide which key
 * sits at the bottom of the start tab's stack, not which tree gets composed.
 *
 * Part 6 adds the four-tab bar (D2) and moves the decorators inside
 * [NavigationState.toDecoratedEntries] — they have to run once per tab stack
 * rather than once over a single flat stack, which is also why this calls the
 * [NavDisplay] overload taking pre-decorated `entries` rather than a raw
 * `backStack`. `rememberViewModelStoreNavEntryDecorator` is still what scopes a
 * `hiltViewModel()` to its entry rather than to the composition that happens to
 * host it, so a popped entry takes its ViewModel with it.
 *
 * The FAB is deliberately NOT here. It lives inside `PathScreen`, because
 * `PathViewModel` is scoped to the Path entry and a Scaffold-level FAB sits
 * outside that entry's ViewModel store — it could not reach it.
 */
@Composable
private fun PebblesNavDisplay(
    navigator: Navigator,
    state: NavigationState,
    onSignOut: () -> Unit,
    welcomeContentRevealed: Boolean,
    onOnboardingFinished: () -> Unit,
) {
    val topKey = state.topKey

    Scaffold(
        bottomBar = {
            // D5: bar visibility is read straight off the stack, so it cannot
            // drift out of sync with what is on screen. Every cover became an
            // entry in Parts 2-4, which is what makes this check sufficient —
            // and why Part 6 runs last. Welcome and Auth are not BarKey, so the
            // signed-out funnel gets no bar without a second condition.
            if (topKey is BarKey) {
                PebblesNavigationBar(
                    current = state.topLevelRoute,
                    onSelect = navigator::navigate,
                    onReselect = navigator::onReselect,
                )
            }
        },
        containerColor = PebblesTheme.colors.system.background,
    ) { padding ->
        NavDisplay(
            entries =
                state.toDecoratedEntries(
                    entryProvider =
                        entryProvider {
                            pebblesEntries(
                                navigator = navigator,
                                onSignOut = onSignOut,
                                welcomeContentRevealed = welcomeContentRevealed,
                                onOnboardingFinished = onOnboardingFinished,
                            )
                        },
                ),
            onBack = { navigator.goBack() },
            modifier = Modifier.padding(padding),
        )
    }
}
