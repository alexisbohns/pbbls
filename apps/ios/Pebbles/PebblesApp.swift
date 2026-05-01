import SwiftUI
import GoogleSignIn

@main
struct PebblesApp: App {
    @State private var supabase = SupabaseService()

    init() {
        let config = GIDConfiguration(clientID: AppEnvironment.googleIOSClientID)
        GIDSignIn.sharedInstance.configuration = config
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(supabase)
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
