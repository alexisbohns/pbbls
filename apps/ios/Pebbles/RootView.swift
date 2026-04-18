import SwiftUI

/// Top-level auth gate. Reads `SupabaseService` from the environment and
/// decides whether to show the auth screen or the main tab bar.
///
/// During `isInitializing`, renders `Color.clear` so the user never sees a
/// flash of the wrong screen while the keychain session is being read.
/// `.task { await supabase.start() }` subscribes to auth state changes for
/// the lifetime of this view (= the lifetime of the app).
///
/// On the first transition from no-session to signed-in (per device),
/// presents `OnboardingView` as a `.fullScreenCover` over `MainTabView`.
struct RootView: View {
    @Environment(SupabaseService.self) private var supabase
    @AppStorage("hasSeenOnboarding") private var hasSeenOnboarding = false
    @State private var isPresentingOnboarding = false

    var body: some View {
        ZStack {
            if supabase.isInitializing {
                Color.clear
            } else if supabase.session == nil {
                AuthView()
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
