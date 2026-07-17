import SwiftUI

/// Top-level auth gate. The loader and pre-login welcome are merged into
/// a single `WelcomeView`, which hosts `HandcraftedLogoView` as its splash:
/// the logo plays a draw-on, then boils in place until the app is ready.
///
/// At cold launch `WelcomeView` is rendered with `contentRevealed: false`
/// — only the logo is visible, centered, drawing on then boiling. It boils
/// against `dataReady` (auth resolved AND both reference-data services
/// settled — success or failure — OR the safety ceiling elapsed) and, once it
/// has also boiled its minimum, emits `onLoaderSettled`. That event sets
/// `loaderSettled`, making `canProceed` true, and one of two things happens:
///   - if the user is unauthenticated, `contentRevealed` flips true and
///     `WelcomeView` slides the carousel + sign-in buttons + disclaimer
///     in from the bottom, pushing the logo up to its header position;
///   - if the user is authenticated, the stack swaps to `PathView`, with the
///     loader held over it (via `startSettled`) until the first timeline load
///     settles — so `PathView`'s own spinner never flashes.
struct RootView: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var refs
    @Environment(SnapURLCache.self) private var snapURLs
    @AppStorage("hasSeenOnboarding") private var hasSeenOnboarding = false
    @State private var isPresentingOnboarding = false
    @State private var authPath = NavigationPath()
    @State private var loaderSettled = false
    @State private var loaderCeilingReached = false
    /// For authed launches: the loader stays over `PathView` until its first
    /// timeline load settles, so the home feed's own spinner never shows.
    @State private var pathFeedLoaded = false

    /// Safety ceiling: if reference data never settles (e.g. a wedged
    /// network beyond the client's own timeout), open the app anyway rather
    /// than boiling indefinitely. Normal launches settle in well under this.
    private static let loaderCeilingSeconds: TimeInterval = 8

    private enum AuthRoute: Hashable {
        case auth(AuthView.Mode)
    }

    /// Auth resolved AND both reference-data load attempts settled (success or
    /// failure), OR the safety ceiling elapsed.
    private var dataReady: Bool {
        (!supabase.isInitializing && palettes.didFinishLoading && refs.didFinishLoading)
            || loaderCeilingReached
    }

    /// The app is shown only once the loader has fully settled — drawn on AND
    /// boiled the minimum AND `dataReady`. The handcrafted logo IS the loader,
    /// so its own settle event (not a computed time) drives the transition.
    private var canProceed: Bool { loaderSettled }

    private var canShowAuthedTabs: Bool {
        supabase.session != nil && canProceed
    }

    private var welcomeContentRevealed: Bool {
        supabase.session == nil && canProceed
    }

    var body: some View {
        ZStack {
            if canShowAuthedTabs {
                ZStack {
                    PathView(onFirstLoad: { pathFeedLoaded = true })
                        .fullScreenCover(isPresented: $isPresentingOnboarding) {
                            OnboardingView(steps: OnboardingSteps.all) {
                                hasSeenOnboarding = true
                                isPresentingOnboarding = false
                            }
                        }

                    if !pathFeedLoaded {
                        // Hold the loader over the home feed load so its own
                        // spinner never flashes. `startSettled` skips a second
                        // draw-on — the logo just boils, then fades out.
                        HandcraftedLogoView(shouldSettle: false, startSettled: true)
                            .containerRelativeFrame(.horizontal) { width, _ in width * 0.33 }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(Color.system.background.ignoresSafeArea())
                            .transition(.opacity)
                    }
                }
                .animation(.easeInOut(duration: 0.35), value: pathFeedLoaded)
            } else {
                NavigationStack(path: $authPath) {
                    WelcomeView(
                        contentRevealed: welcomeContentRevealed,
                        appReady: dataReady,
                        onLoaderSettled: { loaderSettled = true },
                        onCreateAccount: { authPath.append(AuthRoute.auth(.signup)) },
                        onLogin: { authPath.append(AuthRoute.auth(.login)) }
                    )
                    .navigationDestination(for: AuthRoute.self) { route in
                        switch route {
                        case .auth(let mode):
                            AuthView(initialMode: mode)
                        }
                    }
                }
            }
        }
        .task {
            await supabase.start()
        }
        .task {
            try? await Task.sleep(for: .seconds(Self.loaderCeilingSeconds))
            loaderCeilingReached = true
        }
        .task { await palettes.load() }
        .task { await refs.load() }
        // Relies on supabase.start() being kicked off in .task above.
        // session?.user.id is nil when this observer is registered, so the
        // first authStateChanges event delivers a real nil→id transition
        // even for users already signed in from a prior launch.
        .onChange(of: supabase.session?.user.id) { _, newUserId in
            if newUserId != nil && !hasSeenOnboarding {
                isPresentingOnboarding = true
            }
        }
        .onChange(of: supabase.session == nil) { wasSignedOut, isSignedOut in
            if !wasSignedOut && isSignedOut {
                snapURLs.invalidateAll()
                // Re-arm the home-feed loader cover for the next sign-in.
                pathFeedLoaded = false
            }
        }
    }
}

#Preview {
    let supabase = SupabaseService()
    return RootView()
        .environment(supabase)
        .environment(EmotionPaletteService(client: supabase.client))
        .environment(ReferenceDataService(client: supabase.client))
        .environment(SnapURLCache(client: supabase.client))
        .environment(KarmaNotificationService())
}
