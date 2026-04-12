import Foundation
import Supabase
import Observation

/// Wraps the Supabase client and exposes it via the SwiftUI environment.
/// Views pull this out with `@Environment(SupabaseService.self)`.
///
/// The client initializer performs no network I/O, so creating this
/// during app launch is safe on the main thread.
@Observable
final class SupabaseService {
    let client: SupabaseClient

    init() {
        self.client = SupabaseClient(
            supabaseURL: AppEnvironment.supabaseURL,
            supabaseKey: AppEnvironment.supabaseAnonKey
        )
    }
}
