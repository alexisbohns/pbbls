import CryptoKit
import Foundation

/// Nonce helpers shared by the Apple and Google native sign-in flows.
///
/// The raw nonce is what we pass to Supabase's `signInWithIdToken`; the
/// SHA256 hash is what we send to the IdP in the authorization request.
/// The IdP echoes it back inside the id_token's `nonce` claim, and
/// Supabase re-hashes the raw value to compare.
enum OAuthNonce {
    static func random(length: Int = 32) -> String {
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

    static func sha256(_ input: String) -> String {
        let data = Data(input.utf8)
        let hash = SHA256.hash(data: data)
        return hash.map { String(format: "%02x", $0) }.joined()
    }
}
