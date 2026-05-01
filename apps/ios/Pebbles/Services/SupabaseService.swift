import AuthenticationServices
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
@MainActor
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

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "auth")

    init() {
        self.client = SupabaseClient(
            supabaseURL: AppEnvironment.supabaseURL,
            supabaseKey: AppEnvironment.supabaseAnonKey
        )
    }

    /// Subscribes to Supabase's auth state stream and keeps `session` +
    /// `isInitializing` in sync for the lifetime of the app.
    ///
    /// This function never returns under normal operation. Call it exactly
    /// once from `RootView.task { }`.
    ///
    /// CRITICAL: do not `await` any Supabase SDK call from inside this
    /// loop. The SDK holds an internal lock while delivering events, and
    /// awaiting a Supabase call from inside the callback deadlocks the
    /// client. Mutate state synchronously only.
    func start() async {
        for await (event, session) in client.auth.authStateChanges {
            self.session = session
            self.isInitializing = false
            if event == .signedIn {
                self.authError = nil
            }
        }
        logger.error("authStateChanges stream ended unexpectedly")
    }

    /// Sign in with email + password. On success, the `.signedIn` event flows
    /// through `authStateChanges` and `session` becomes non-nil.
    func signIn(email: String, password: String) async {
        self.authError = nil
        do {
            try await client.auth.signIn(email: email, password: password)
        } catch {
            logger.error("signIn failed: \(error.localizedDescription, privacy: .private)")
            self.authError = error.localizedDescription
        }
    }

    /// Sign up with email + password. Consent timestamps are captured now and
    /// passed through `auth.users.raw_user_meta_data`. Note: the current
    /// `handle_new_user` DB trigger does not copy them into `public.profiles`
    /// — this mirrors web behavior and will be fixed in a separate `fix(db)` issue.
    func signUp(email: String, password: String) async {
        self.authError = nil
        let now = ISO8601DateFormatter().string(from: Date())
        do {
            try await client.auth.signUp(
                email: email,
                password: password,
                data: [
                    "terms_accepted_at": .string(now),
                    "privacy_accepted_at": .string(now)
                ]
            )
        } catch {
            logger.error("signUp failed: \(error.localizedDescription, privacy: .private)")
            self.authError = error.localizedDescription
        }
    }

    /// Sign in with Apple via the native authorization sheet.
    ///
    /// On the user's first authorization, Apple returns their `fullName` —
    /// we use it to overwrite the trigger-seeded `'Pebbler'` display name.
    /// Subsequent sign-ins return no name, so the patch is a no-op.
    func signInWithApple() async {
        self.authError = nil
        do {
            let result = try await AppleSignInService.authorize()
            try await client.auth.signInWithIdToken(
                credentials: .init(
                    provider: .apple,
                    idToken: result.idToken,
                    nonce: result.rawNonce
                )
            )
            if let name = formatted(result.fullName) {
                await patchDisplayNameIfDefault(to: name)
            }
        } catch AppleSignInService.Failure.canceled {
            // User dismissed the sheet — silent.
        } catch {
            logger.error("signInWithApple failed: \(error.localizedDescription, privacy: .private)")
            self.authError = error.localizedDescription
        }
    }

    /// Sign in with Google via the native GoogleSignIn SDK.
    ///
    /// Google's id_token includes a `name` claim, exposed by the Supabase
    /// SDK on `session.user.userMetadata`. We mirror the Apple flow and
    /// patch the profile post-auth.
    func signInWithGoogle() async {
        self.authError = nil
        do {
            let result = try await GoogleSignInService.authorize()
            try await client.auth.signInWithIdToken(
                credentials: .init(
                    provider: .google,
                    idToken: result.idToken,
                    accessToken: result.accessToken
                )
            )
            if let name = googleNameFromMetadata() {
                await patchDisplayNameIfDefault(to: name)
            }
        } catch GoogleSignInService.Failure.canceled {
            // User dismissed the sheet — silent.
        } catch {
            logger.error("signInWithGoogle failed: \(error.localizedDescription, privacy: .private)")
            self.authError = error.localizedDescription
        }
    }

    private func formatted(_ name: PersonNameComponents?) -> String? {
        guard let name else { return nil }
        let formatter = PersonNameComponentsFormatter()
        formatter.style = .default
        let formatted = formatter.string(from: name).trimmingCharacters(in: .whitespaces)
        return formatted.isEmpty ? nil : formatted
    }

    private func googleNameFromMetadata() -> String? {
        guard let metadata = session?.user.userMetadata else { return nil }
        // OIDC `name` claim is preferred; `full_name` is a Supabase
        // alias some providers populate. Either is fine.
        for key in ["full_name", "name"] {
            if case let .string(value) = metadata[key],
               !value.isEmpty {
                return value
            }
        }
        return nil
    }

    /// Replaces `profiles.display_name` with `name` only if the row is
    /// still the trigger default (`'Pebbler'`). Idempotent — safe to call
    /// on every OAuth sign-in.
    private func patchDisplayNameIfDefault(to name: String) async {
        guard let userId = session?.user.id else { return }
        do {
            struct ProfileRow: Decodable { let display_name: String }
            let current: ProfileRow = try await client
                .from("profiles")
                .select("display_name")
                .eq("user_id", value: userId)
                .single()
                .execute()
                .value
            guard current.display_name == "Pebbler" else { return }
            try await client
                .from("profiles")
                .update(["display_name": name])
                .eq("user_id", value: userId)
                .execute()
        } catch {
            logger.error("patchDisplayName failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    /// Sign out. Failures are logged but never surfaced as alerts — the local
    /// token is wiped regardless and the stream will emit `.signedOut`.
    func signOut() async {
        do {
            try await client.auth.signOut()
        } catch {
            logger.error("signOut failed: \(error.localizedDescription, privacy: .private)")
        }
    }
}
