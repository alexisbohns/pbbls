import SwiftUI

/// Top-level auth gate. Reads `SupabaseService` from the environment and
/// decides whether to show the splash, the pre-login welcome flow, or the
/// main tab bar.
///
/// On every cold launch a `SplashView` plays the animated drawing of the
/// Pebbles logo. `supabase.start()` runs in parallel; once the splash
/// animation has completed AND `isInitializing` has flipped, RootView
/// transitions to either `WelcomeView` (with a staged reveal) or
/// `MainTabView`. Eliminates the previous `Color.clear` flash during
/// keychain restore.
///
/// Unauth: `NavigationStack` rooted at `WelcomeView`. The two CTAs push
/// `AuthView(initialMode:)` with the correct tab preselected; the native
/// back button pops to welcome. On successful signup/login the whole
/// stack unmounts as `supabase.session` flips non-nil.
///
/// Auth (first transition from no-session to signed-in per device):
/// presents `OnboardingView` as a `.fullScreenCover` over `MainTabView`.
struct RootView: View {
    @Environment(SupabaseService.self) private var supabase
    @AppStorage("hasSeenOnboarding") private var hasSeenOnboarding = false
    @State private var isPresentingOnboarding = false
    @State private var authPath = NavigationPath()
    @State private var splashPhase: SplashPhase = .playing

    private enum AuthRoute: Hashable {
        case auth(AuthView.Mode)
    }

    /// Splash → main content lifecycle.
    /// `playing`: animation in progress.
    /// `ready`: animation done, but `supabase.isInitializing` may still be true.
    /// `finished`: both splash done AND auth resolved → show next screen.
    private enum SplashPhase {
        case playing
        case ready
        case finished
    }

    var body: some View {
        ZStack {
            if splashPhase != .finished {
                SplashView(onComplete: { splashPhase = .ready })
            } else if supabase.session == nil {
                NavigationStack(path: $authPath) {
                    WelcomeView(
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
            } else {
                MainTabView()
                    .fullScreenCover(isPresented: $isPresentingOnboarding) {
                        OnboardingView(steps: OnboardingSteps.all) {
                            hasSeenOnboarding = true
                            isPresentingOnboarding = false
                        }
                    }
            }
        }
        .task {
            await supabase.start()
        }
        // Two transitions feed `splashPhase = .finished`:
        //   - splash finishes after auth resolved (onChange of splashPhase)
        //   - auth resolves after splash finished (onChange of isInitializing)
        // We handle both because either order is possible.
        .onChange(of: splashPhase) { _, phase in
            if phase == .ready && !supabase.isInitializing {
                splashPhase = .finished
            }
        }
        .onChange(of: supabase.isInitializing) { _, isInit in
            if !isInit && splashPhase == .ready {
                splashPhase = .finished
            }
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
