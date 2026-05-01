import SwiftUI

/// Top-level auth gate. The splash and pre-login welcome are merged into
/// a single `WelcomeView` so the Rive logo plays continuously without a
/// view-swap glitch on the splash→welcome boundary.
///
/// At cold launch `WelcomeView` is rendered with `contentRevealed: false`
/// — only the Rive logo is visible, centered. After
/// `Self.minSplashSeconds` AND `supabase.isInitializing` flips false, one
/// of two things happens:
///   - if the user is unauthenticated, `contentRevealed` flips true and
///     `WelcomeView` slides the carousel + sign-in buttons + disclaimer
///     in from the bottom, pushing the logo up to its header position;
///   - if the user is authenticated, the whole view stack swaps to
///     `MainTabView`. The Rive will have played for at least
///     `minSplashSeconds`, satisfying the "splash before Path" intent.
struct RootView: View {
    @Environment(SupabaseService.self) private var supabase
    @AppStorage("hasSeenOnboarding") private var hasSeenOnboarding = false
    @State private var isPresentingOnboarding = false
    @State private var authPath = NavigationPath()
    @State private var minSplashDone = false

    /// Minimum time the Rive logo is held centered before the welcome
    /// content reveals (or, for authenticated users, before swapping to
    /// `MainTabView`). Tuned to roughly match the bundled `pbbls-logo.riv`
    /// timeline.
    private static let minSplashSeconds: TimeInterval = 2.5

    private enum AuthRoute: Hashable {
        case auth(AuthView.Mode)
    }

    /// True once the user is signed in AND auth resolution has settled
    /// AND the splash hold has elapsed. Until all three, we keep showing
    /// `WelcomeView` so the Rive plays out.
    private var canShowAuthedTabs: Bool {
        supabase.session != nil && !supabase.isInitializing && minSplashDone
    }

    /// True once the splash hold has elapsed AND auth resolution settled
    /// to "no session". Drives WelcomeView's reveal sequence.
    private var welcomeContentRevealed: Bool {
        supabase.session == nil && !supabase.isInitializing && minSplashDone
    }

    var body: some View {
        ZStack {
            if canShowAuthedTabs {
                MainTabView()
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
            try? await Task.sleep(for: .seconds(Self.minSplashSeconds))
            minSplashDone = true
        }
        // Relies on supabase.start() being kicked off in .task above.
        // session?.user.id is nil when this observer is registered, so the
        // first authStateChanges event delivers a real nil→id transition
        // even for users already signed in from a prior launch.
        .onChange(of: supabase.session?.user.id) { _, newUserId in
            if newUserId != nil && !hasSeenOnboarding {
                isPresentingOnboarding = true
            }
        }
    }
}

#Preview {
    RootView()
        .environment(SupabaseService())
}
