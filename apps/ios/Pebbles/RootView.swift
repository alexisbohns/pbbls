import SwiftUI

/// Top-level auth gate. Reads `SupabaseService` from the environment and
/// decides whether to show the pre-login welcome flow or the main tab bar.
///
/// During `isInitializing`, renders `Color.clear` so the user never sees a
/// flash of the wrong screen while the keychain session is being read.
/// `.task { await supabase.start() }` subscribes to auth state changes for
/// the lifetime of this view (= the lifetime of the app).
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

    private enum AuthRoute: Hashable {
        case auth(AuthView.Mode)
    }

    var body: some View {
        ZStack {
            if supabase.isInitializing {
                Color.clear
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
