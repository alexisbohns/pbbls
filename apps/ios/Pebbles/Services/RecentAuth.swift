import Foundation
import Supabase

/// The iOS half of the recent sign-in check (#976, #977).
///
/// GoTrue stamps every real authentication into the access token's `amr`
/// claim (`[{"method":"password","timestamp":<epoch s>}]`), and a refresh keeps
/// the stamps, so a fresh stamp means the credential was proven recently. The
/// server's `recent_auth_ok` (20260927120000) applies the same rule; this copy
/// only decides whether to PROMPT, and the server stays the authority.
///
/// `window` is duplicated in that migration, in Android's `RecentAuth.WINDOW`
/// and in the web's `RECENT_AUTH_WINDOW_SECONDS`: change all four together.
///
/// The payload is read without verifying the signature. That is fine for a UX
/// decision about our own token, and never for a security one.
enum RecentAuth {
    static let window: TimeInterval = 10 * 60

    /// The raised condition's text, shared by the SQL, the edge function and every client.
    static let reauthRequired = "reauth_required"

    /// The newest numeric `amr` timestamp (epoch seconds), or nil when there is
    /// none. "Any stamp within the window" is the same as "the newest one is".
    static func newestStamp(accessToken: String?) -> Double? {
        guard let accessToken, let payload = payload(of: accessToken),
              let amr = payload["amr"] as? [Any] else { return nil }
        return amr.compactMap { entry -> Double? in
            guard let stamp = (entry as? [String: Any])?["timestamp"] as? NSNumber,
                  // JSONSerialization hands booleans back as NSNumber too.
                  CFGetTypeID(stamp) != CFBooleanGetTypeID() else { return nil }
            return stamp.doubleValue
        }.max()
    }

    /// True when the token proves a sign-in no older than `window`. Unparseable → false.
    static func isFresh(accessToken: String?, now: Date = .now) -> Bool {
        guard let newest = newestStamp(accessToken: accessToken) else { return false }
        return newest >= now.timeIntervalSince1970 - window
    }

    /// True for every shape of "sign in again": the delete-account 428, the
    /// profiles trigger's `raise exception 'reauth_required'`, and GoTrue's
    /// `reauthentication_needed` from a password change (`secure_password_change`).
    static func isReauthRequired(_ error: Error) -> Bool {
        if case let .httpError(code, _)? = error as? FunctionsError { return code == 428 }
        if let postgrest = error as? PostgrestError { return postgrest.message == reauthRequired }
        if let auth = error as? AuthError { return auth.errorCode == .reauthenticationNeeded }
        return false
    }

    /// True when a password re-auth was refused for the password itself, as
    /// opposed to the network or a throttle.
    static func isWrongPassword(_ error: Error) -> Bool {
        (error as? AuthError)?.errorCode == .invalidCredentials
    }

    private static func payload(of token: String) -> [String: Any]? {
        let parts = token.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count >= 2 else { return nil }
        var b64 = parts[1].replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        b64 += String(repeating: "=", count: (4 - b64.count % 4) % 4)
        guard let data = Data(base64Encoded: b64),
              let object = try? JSONSerialization.jsonObject(with: data) else { return nil }
        return object as? [String: Any]
    }
}

/// How the signed-in person proves it's them again.
enum ReauthMethod: Equatable {
    case password, google, apple

    /// An email identity always means a password, even when a provider is
    /// linked too. No identities at all is an email account whose identities
    /// were not loaded.
    init(providers: [String]) {
        if providers.isEmpty || providers.contains("email") {
            self = .password
        } else if providers.contains("google") {
            self = .google
        } else if providers.contains("apple") {
            self = .apple
        } else {
            self = .password
        }
    }
}

/// A provider re-auth came back as a DIFFERENT user; that session was signed out.
struct ReauthAccountMismatchError: Error {}
