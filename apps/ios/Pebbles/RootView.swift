import SwiftUI

/// Top-level auth gate. Reads `SupabaseService` from the environment and
/// decides whether to show the auth screen or the main tab bar.
struct RootView: View {
    @Environment(SupabaseService.self) private var supabase

    var body: some View {
        ZStack {
            if supabase.isInitializing {
                Color.clear
            } else if supabase.session == nil {
                AuthView()
            } else {
                MainTabView()
            }
        }
        .task {
            await supabase.start()
        }
    }
}

#Preview {
    RootView()
        .environment(SupabaseService())
}
