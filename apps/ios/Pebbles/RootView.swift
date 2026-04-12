import SwiftUI

/// Auth gate — Task 5 rewrites this as a switch over `SupabaseService`.
/// For now it just forwards to `MainTabView` so the app still launches.
struct RootView: View {
    var body: some View {
        MainTabView()
    }
}

#Preview {
    RootView()
}
