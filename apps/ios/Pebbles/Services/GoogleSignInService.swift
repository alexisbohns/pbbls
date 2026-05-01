import Foundation
import GoogleSignIn
import UIKit

/// Result of a successful Google sign-in.
struct GoogleSignInResult {
    let idToken: String
    let accessToken: String
}

/// Wraps the `GoogleSignIn-iOS` SDK with an async / throwing API.
///
/// The SDK is configured once at app launch (see `PebblesApp.swift`).
/// This helper just kicks off the interactive sign-in.
enum GoogleSignInService {
    enum Failure: Error, LocalizedError {
        case canceled
        case missingIdentityToken
        case noPresentingViewController
        case unknown(String)

        var errorDescription: String? {
            switch self {
            case .canceled: return nil
            case .missingIdentityToken: return "Google did not return an identity token."
            case .noPresentingViewController: return "Could not find a window to present sign-in."
            case .unknown(let msg): return msg
            }
        }
    }

    @MainActor
    static func authorize() async throws -> GoogleSignInResult {
        guard let presenter = topViewController() else {
            throw Failure.noPresentingViewController
        }

        do {
            let result = try await GIDSignIn.sharedInstance.signIn(
                withPresenting: presenter
            )
            guard let idToken = result.user.idToken?.tokenString else {
                throw Failure.missingIdentityToken
            }
            return GoogleSignInResult(
                idToken: idToken,
                accessToken: result.user.accessToken.tokenString
            )
        } catch let error as NSError where error.domain == kGIDSignInErrorDomain
                && error.code == GIDSignInError.canceled.rawValue {
            throw Failure.canceled
        } catch {
            throw Failure.unknown(error.localizedDescription)
        }
    }

    @MainActor
    private static func topViewController() -> UIViewController? {
        let key = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
        var top = key?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
