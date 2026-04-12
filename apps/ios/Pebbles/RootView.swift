import SwiftUI

/// Top-level auth gate. Reads `SupabaseService` from the environment and
/// decides whether to show the auth screen or the main tab bar.
///
/// During `isInitializing`, renders `Color.clear` so the user never sees a
/// flash of the wrong screen while the keychain session is being read.
/// `.task { await supabase.start() }` subscribes to auth state changes for
/// the lifetime of this view (= the lifetime of the app).
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
