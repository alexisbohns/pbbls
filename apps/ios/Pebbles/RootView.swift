import SwiftUI

/// Top-level auth gate. The loader and pre-login welcome are merged into
/// a single `WelcomeView`, which hosts `HandcraftedLogoView` as its splash:
/// the logo plays a draw-on, then boils in place until the app is ready.
///
/// At cold launch `WelcomeView` is rendered with `contentRevealed: false`
/// — only the logo is visible, centered, drawing on then boiling. Once
/// `canProceed` is true (auth resolved AND both reference-data services
/// settled — success or failure — OR the safety ceiling elapsed, AND the
/// logo's draw-on has completed), one of two things happens:
///   - if the user is unauthenticated, `contentRevealed` flips true and
///     `WelcomeView` slides the carousel + sign-in buttons + disclaimer
///     in from the bottom, pushing the logo up to its header position;
///   - if the user is authenticated, the whole view stack swaps to
///     `PathView`.
struct RootView: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var refs
    @Environment(SnapURLCache.self) private var snapURLs
    @AppStorage("hasSeenOnboarding") private var hasSeenOnboarding = false
    @State private var isPresentingOnboarding = false
    @State private var authPath = NavigationPath()
    @State private var logoDrawComplete = false
    @State private var loaderCeilingReached = false

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

    /// The loader dismisses only once the draw-on has played AND the app is
    /// ready — the handcrafted logo IS the loader (no spinner).
    private var canProceed: Bool { dataReady && logoDrawComplete }

    private var canShowAuthedTabs: Bool {
        supabase.session != nil && canProceed
    }

    private var welcomeContentRevealed: Bool {
        supabase.session == nil && canProceed
    }

    var body: some View {
        ZStack {
            if canShowAuthedTabs {
                PathView()
                    .fullScreenCover(isPresented: $isPresentingOnboarding) {
                        OnboardingView(steps: OnboardingSteps.all) {
                            hasSeenOnboarding = true
                            isPresentingOnboarding = false
                        }
                    }
            } else {
                NavigationStack(path: $authPath) {
                    WelcomeView(
                        contentRevealed: welcomeContentRevealed,
                        appReady: canProceed,
                        onLogoDrawComplete: { logoDrawComplete = true },
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
