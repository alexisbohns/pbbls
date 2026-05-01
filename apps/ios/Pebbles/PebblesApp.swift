import SwiftUI

@main
struct PebblesApp: App {
    @State private var supabase = SupabaseService()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(supabase)
        }
    }
}
