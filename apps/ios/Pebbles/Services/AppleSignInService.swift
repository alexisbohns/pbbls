import AuthenticationServices
import CryptoKit
import Foundation
import ObjectiveC
import UIKit

/// Result of a successful Sign in with Apple authorization.
///
/// `fullName` is non-nil only on the user's *first* authorization for our
/// app. On every subsequent sign-in Apple omits it — matching the behavior
/// they document at https://developer.apple.com/documentation/sign_in_with_apple .
struct AppleSignInResult {
    let idToken: String
    let rawNonce: String
    let fullName: PersonNameComponents?
}

/// Wraps `ASAuthorizationController` with an async / throwing API.
///
/// The hashed nonce is sent to Apple in the request; the *raw* nonce is
/// passed to Supabase's `signInWithIdToken` so it can verify the JWT.
enum AppleSignInService {
    enum Failure: Error, LocalizedError {
        case canceled
        case missingIdentityToken
        case unknown(String)

        var errorDescription: String? {
            switch self {
            case .canceled: return nil
            case .missingIdentityToken: return "Apple did not return an identity token."
            case .unknown(let msg): return msg
            }
        }
    }

    @MainActor
    static func authorize() async throws -> AppleSignInResult {
        let rawNonce = randomNonce()
        let hashedNonce = sha256(rawNonce)

        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = hashedNonce

        let controller = ASAuthorizationController(authorizationRequests: [request])
        let delegate = AppleAuthDelegate()
        controller.delegate = delegate
        controller.presentationContextProvider = delegate

        return try await withCheckedThrowingContinuation { continuation in
            delegate.continuation = continuation
            delegate.rawNonce = rawNonce
            // ASAuthorizationController only holds weak references to the
            // delegate / presentation provider. Pin the delegate to the
            // controller's lifetime so it survives until completion.
            objc_setAssociatedObject(
                controller, &AppleAuthDelegate.assocKey,
                delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC
            )
            controller.performRequests()
        }
    }

    private static func randomNonce(length: Int = 32) -> String {
        precondition(length > 0)
        let charset: [Character] =
            Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remaining = length
        while remaining > 0 {
            var random: UInt8 = 0
            let status = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
            if status != errSecSuccess {
                fatalError("SecRandomCopyBytes failed with status \(status)")
            }
            if random < charset.count {
                result.append(charset[Int(random)])
                remaining -= 1
            }
        }
        return result
    }

    private static func sha256(_ input: String) -> String {
        let data = Data(input.utf8)
        let hash = SHA256.hash(data: data)
        return hash.map { String(format: "%02x", $0) }.joined()
    }
}

private final class AppleAuthDelegate: NSObject,
    ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {

    static var assocKey: UInt8 = 0

    var continuation: CheckedContinuation<AppleSignInResult, Error>?
    var rawNonce: String = ""

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        defer { continuation = nil }
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let idToken = String(data: tokenData, encoding: .utf8) else {
            continuation?.resume(throwing: AppleSignInService.Failure.missingIdentityToken)
            return
        }
        continuation?.resume(returning: AppleSignInResult(
            idToken: idToken,
            rawNonce: rawNonce,
            fullName: credential.fullName
        ))
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        defer { continuation = nil }
        let nsErr = error as NSError
        if nsErr.domain == ASAuthorizationErrorDomain,
           nsErr.code == ASAuthorizationError.canceled.rawValue {
            continuation?.resume(throwing: AppleSignInService.Failure.canceled)
        } else {
            continuation?.resume(throwing: AppleSignInService.Failure.unknown(error.localizedDescription))
        }
    }

    @MainActor
    func presentationAnchor(for controller: ASAuthorizationController)
        -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
}
