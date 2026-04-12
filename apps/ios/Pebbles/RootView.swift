import SwiftUI

/// Top-level auth gate. Reads `SupabaseService` from the environment and
/// decides whether to show the auth screen or the main tab bar.
///
/// - `isInitializing`: Supabase is still reading the persisted session from
///   the keychain. Render nothing so the user never sees a flash of the wrong UI.
/// - `session == nil`: signed out → show AuthView (currently a placeholder,
///   replaced in Task 8).
/// - `session != nil`: signed in → show MainTabView.
///
/// `.task { await supabase.start() }` subscribes to `authStateChanges` for the
/// lifetime of this view, which equals the lifetime of the app.
struct RootView: View {
    @Environment(SupabaseService.self) private var supabase

    var body: some View {
        ZStack {
            if supabase.isInitializing {
                Color.clear
            } else if supabase.session == nil {
                // Placeholder — replaced by AuthView() in Task 8.
                Text("Not signed in")
                    .foregroundStyle(.secondary)
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
