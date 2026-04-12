import Foundation
import Supabase
import Observation
import os

/// Wraps the Supabase client and exposes auth state via the SwiftUI environment.
/// Views pull this out with `@Environment(SupabaseService.self)` and read `session`
/// to decide what to render. Actions (`signIn`, `signUp`, `signOut`) are called from
/// the views that drive them (AuthView, ProfileView).
///
/// The client initializer performs no network I/O, so creating this during app
/// launch is safe on the main thread.
@Observable
final class SupabaseService {
    let client: SupabaseClient

    /// The current Supabase session, or nil when signed out.
    /// Updated exclusively by the `authStateChanges` stream inside `start()`.
    var session: Session?

    /// True until the first `authStateChanges` event resolves the persisted session.
    /// `RootView` renders `Color.clear` while this is true so the user never sees
    /// AuthView flash before the tab bar.
    var isInitializing: Bool = true

    /// Last signIn/signUp error, displayed inline under the auth form.
    /// Cleared on successful auth, on mode toggle, and as the user edits the form.
    var authError: String?

    fileprivate let logger = Logger(subsystem: "app.pbbls.ios", category: "auth")

    init() {
        self.client = SupabaseClient(
            supabaseURL: AppEnvironment.supabaseURL,
            supabaseKey: AppEnvironment.supabaseAnonKey
        )
    }
}
