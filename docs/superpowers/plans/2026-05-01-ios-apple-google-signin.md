# iOS Apple & Google Sign In Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add native "Continue with Apple" and "Continue with Google" sign-in to the iOS welcome screen, resolving #351 and #352.

**Architecture:** Two SwiftUI helper services wrap the platform SDKs (`AuthenticationServices` for Apple, `GoogleSignIn-iOS` for Google) and return id-tokens. `SupabaseService` exchanges those tokens via `signInWithIdToken`. Account linking by verified email is handled by Supabase server-side. After first OAuth sign-in we patch `profiles.display_name` only if it is still the default `'Pebbler'`, so users do not get stuck with that placeholder.

**Tech Stack:** Swift 5.9, SwiftUI, iOS 17+, `supabase-swift` 2.x, `GoogleSignIn-iOS` 7.x, `AuthenticationServices`.

**Note on TDD:** Per `apps/ios/CLAUDE.md` ("No UI tests for now"), this plan uses **build-and-manually-verify** instead of failing-test-first TDD. Each task ends with a build check; the final task documents a TestFlight verification matrix.

**Spec:** `docs/superpowers/specs/2026-05-01-ios-apple-google-signin-design.md`

---

## File map

**Created:**
- `apps/ios/Pebbles/Services/AppleSignIn.swift` — `ASAuthorizationController` wrapper that returns id-token, raw nonce, and (first-time-only) `PersonNameComponents`.
- `apps/ios/Pebbles/Services/GoogleSignIn.swift` — `GIDSignIn` wrapper that returns id-token + access-token.
- `apps/ios/Pebbles/Resources/GoogleGMark.imageset/` — Google "G" mark asset (light + dark).

**Modified:**
- `apps/ios/project.yml` — add `GoogleSignIn` SPM package and target dependency.
- `apps/ios/Config/Secrets.example.xcconfig` — document `GOOGLE_IOS_CLIENT_ID` and `GOOGLE_IOS_REVERSED_CLIENT_ID`.
- `apps/ios/Config/Secrets.xcconfig` — add `GOOGLE_IOS_CLIENT_ID` and `GOOGLE_IOS_REVERSED_CLIENT_ID` (untracked file; user fills in real values).
- `apps/ios/Pebbles/Resources/Info.plist` — add `GoogleIOSClientID` Info key and a `CFBundleURLTypes` entry for the reversed client ID URL scheme.
- `apps/ios/Pebbles/Services/AppEnvironment.swift` — expose `googleIOSClientID`.
- `apps/ios/Pebbles/Services/SupabaseService.swift` — add `signInWithApple()`, `signInWithGoogle()`, and a private `patchDisplayNameIfDefault(_:)` helper.
- `apps/ios/Pebbles/PebblesApp.swift` — initialise GoogleSignIn config and wire `.onOpenURL` to `GIDSignIn.sharedInstance.handle(_:)`.
- `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift` — accept `SupabaseService` from environment, add Apple button, Google button, disclosure caption, and an `isSubmitting` flag.
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — three new keys (`welcome.continue.apple`, `welcome.continue.google`, `welcome.legal.disclosure`) in en + fr.

---

## Task 1: Add GoogleSignIn-iOS SPM dependency

**Files:**
- Modify: `apps/ios/project.yml`

- [ ] **Step 1: Add the package and dependency in `project.yml`**

In the `packages:` block, after the `SVGView` entry, append:

```yaml
  GoogleSignIn:
    url: https://github.com/google/GoogleSignIn-iOS
    from: "7.1.0"
```

In the `targets.Pebbles.dependencies:` list, after `SVGView`, append:

```yaml
      - package: GoogleSignIn
        product: GoogleSignIn
```

- [ ] **Step 2: Regenerate the Xcode project**

Run: `npm run generate --workspace=@pbbls/ios`
Expected: xcodegen prints the project name and exits 0. `apps/ios/Pebbles.xcodeproj` is updated (it is git-ignored, so no diff in git).

- [ ] **Step 3: Build to confirm the package resolves**

Run from `apps/ios/`:
```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | tail -20
```
Expected: `** BUILD SUCCEEDED **`. SPM resolution may take a minute the first time.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/project.yml
git commit -m "chore(ios): add GoogleSignIn-iOS spm dependency"
```

---

## Task 2: xcconfig + Info.plist for Google iOS client

**Files:**
- Modify: `apps/ios/Config/Secrets.example.xcconfig`
- Modify: `apps/ios/Config/Secrets.xcconfig`
- Modify: `apps/ios/Pebbles/Resources/Info.plist`
- Modify: `apps/ios/Pebbles/Services/AppEnvironment.swift`

- [ ] **Step 1: Add the new keys to `Secrets.example.xcconfig`**

Append to `apps/ios/Config/Secrets.example.xcconfig`:

```
// Google iOS OAuth client (from Google Cloud Console).
// REVERSED form is used as a custom URL scheme — keep both in sync.
GOOGLE_IOS_CLIENT_ID =
GOOGLE_IOS_REVERSED_CLIENT_ID =
```

- [ ] **Step 2: Add the same keys to `Secrets.xcconfig` (untracked) so local builds work**

The implementer must paste in the actual iOS OAuth client ID from Google Cloud Console (project: Pebbles). If you do not yet have the iOS OAuth client, create one at https://console.cloud.google.com/apis/credentials with type "iOS" and bundle ID `app.pbbls.ios`. Format:

```
GOOGLE_IOS_CLIENT_ID = 1234567890-abcdef.apps.googleusercontent.com
GOOGLE_IOS_REVERSED_CLIENT_ID = com.googleusercontent.apps.1234567890-abcdef
```

`Secrets.xcconfig` is git-ignored.

- [ ] **Step 3: Wire the Info.plist entries**

In `apps/ios/Pebbles/Resources/Info.plist`, inside the top-level `<dict>`, add the Google client ID key alongside the existing Supabase keys:

```xml
	<key>GoogleIOSClientID</key>
	<string>$(GOOGLE_IOS_CLIENT_ID)</string>
```

And add a `CFBundleURLTypes` array (place it next to `CFBundleLocalizations`):

```xml
	<key>CFBundleURLTypes</key>
	<array>
		<dict>
			<key>CFBundleURLSchemes</key>
			<array>
				<string>$(GOOGLE_IOS_REVERSED_CLIENT_ID)</string>
			</array>
		</dict>
	</array>
```

- [ ] **Step 4: Expose the Google client ID via `AppEnvironment`**

Replace the contents of `apps/ios/Pebbles/Services/AppEnvironment.swift` with:

```swift
import Foundation

/// Typed access to build-time configuration values injected via
/// `Config/Secrets.xcconfig` → `Info.plist`. Fails loud and early if
/// a value is missing so setup bugs don't become runtime mysteries.
enum AppEnvironment {
    static let supabaseURL: URL = {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "SupabaseURL") as? String,
              !raw.isEmpty,
              let url = URL(string: raw) else {
            fatalError(
                "SupabaseURL missing or invalid in Info.plist. " +
                "Did you copy Config/Secrets.example.xcconfig → Config/Secrets.xcconfig?"
            )
        }
        return url
    }()

    static let supabaseAnonKey: String = {
        guard let key = Bundle.main.object(forInfoDictionaryKey: "SupabaseAnonKey") as? String,
              !key.isEmpty else {
            fatalError(
                "SupabaseAnonKey missing in Info.plist. " +
                "Did you copy Config/Secrets.example.xcconfig → Config/Secrets.xcconfig?"
            )
        }
        return key
    }()

    static let googleIOSClientID: String = {
        guard let id = Bundle.main.object(forInfoDictionaryKey: "GoogleIOSClientID") as? String,
              !id.isEmpty else {
            fatalError(
                "GoogleIOSClientID missing in Info.plist. " +
                "Did you set GOOGLE_IOS_CLIENT_ID in Config/Secrets.xcconfig?"
            )
        }
        return id
    }()
}
```

- [ ] **Step 5: Build**

Run from `apps/ios/`:
```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | tail -10
```
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Config/Secrets.example.xcconfig apps/ios/Pebbles/Resources/Info.plist apps/ios/Pebbles/Services/AppEnvironment.swift
git commit -m "chore(ios): wire google ios client id through xcconfig and appenvironment"
```

---

## Task 3: Add Google "G" mark asset

**Files:**
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/GoogleGMark.imageset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/GoogleGMark.imageset/google_g_mark.svg` (or `.png`)

- [ ] **Step 1: Download the official Google "G" mark**

From Google's brand guidelines (https://developers.google.com/identity/branding-guidelines), download the SVG of the multi-colour "G". Save it as `apps/ios/Pebbles/Resources/Assets.xcassets/GoogleGMark.imageset/google_g_mark.svg`.

(If the implementer has trouble obtaining the asset, the `.png` 1x/2x/3x variants from the same brand page are an acceptable substitute — adjust `Contents.json` accordingly.)

- [ ] **Step 2: Write `Contents.json` for the imageset**

Create `apps/ios/Pebbles/Resources/Assets.xcassets/GoogleGMark.imageset/Contents.json`:

```json
{
  "images" : [
    {
      "filename" : "google_g_mark.svg",
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  },
  "properties" : {
    "preserves-vector-representation" : true
  }
}
```

The "G" mark works on both light and dark backgrounds because the colours are baked into the SVG, so a single universal image is sufficient.

- [ ] **Step 3: Build to verify the asset compiles**

Run from `apps/ios/`:
```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | tail -10
```
Expected: `** BUILD SUCCEEDED **`. No "could not find image" warning.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Resources/Assets.xcassets/GoogleGMark.imageset
git commit -m "chore(ios): add google g mark asset"
```

---

## Task 4: AppleSignIn helper

**Files:**
- Create: `apps/ios/Pebbles/Services/AppleSignIn.swift`

- [ ] **Step 1: Write the helper**

Create `apps/ios/Pebbles/Services/AppleSignIn.swift`:

```swift
import AuthenticationServices
import CryptoKit
import Foundation

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
enum AppleSignIn {
    enum Failure: Error, LocalizedError {
        case canceled
        case missingIdentityToken
        case unknown(String)

        var errorDescription: String? {
            switch self {
            case .canceled: return nil // surfaced as silent cancel
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
            controller.performRequests()
            // Retain the delegate for the lifetime of the request.
            // ASAuthorizationController only holds a weak reference.
            objc_setAssociatedObject(
                controller, &AppleAuthDelegate.assocKey,
                delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC
            )
        }
    }

    // MARK: Nonce helpers

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
            continuation?.resume(throwing: AppleSignIn.Failure.missingIdentityToken)
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
            continuation?.resume(throwing: AppleSignIn.Failure.canceled)
        } else {
            continuation?.resume(throwing: AppleSignIn.Failure.unknown(error.localizedDescription))
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
```

- [ ] **Step 2: Build**

Run from `apps/ios/`:
```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | tail -10
```
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Services/AppleSignIn.swift
git commit -m "feat(ios): add apple sign in helper with nonce + delegate"
```

---

## Task 5: GoogleSignIn helper

**Files:**
- Create: `apps/ios/Pebbles/Services/GoogleSignIn.swift`

- [ ] **Step 1: Write the helper**

Create `apps/ios/Pebbles/Services/GoogleSignIn.swift`:

```swift
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
enum GoogleSignIn {
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
        } catch let error as GIDSignInError where error.code == .canceled {
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
```

- [ ] **Step 2: Configure the SDK at app launch**

Replace the contents of `apps/ios/Pebbles/PebblesApp.swift` with:

```swift
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
```

- [ ] **Step 3: Build**

Run from `apps/ios/`:
```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | tail -10
```
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Services/GoogleSignIn.swift apps/ios/Pebbles/PebblesApp.swift
git commit -m "feat(ios): add google sign in helper and configure sdk"
```

---

## Task 6: SupabaseService.signInWithApple + display-name patching

**Files:**
- Modify: `apps/ios/Pebbles/Services/SupabaseService.swift`

- [ ] **Step 1: Add `signInWithApple` and the patcher**

In `apps/ios/Pebbles/Services/SupabaseService.swift`, after the existing `signUp(...)` method (line 93) and before `signOut()` (line 97), insert:

```swift
    /// Sign in with Apple via the native authorization sheet.
    ///
    /// On the user's first authorization, Apple returns their `fullName` —
    /// we use it to overwrite the trigger-seeded `'Pebbler'` display name.
    /// Subsequent sign-ins return no name, so the patch is a no-op.
    func signInWithApple() async {
        self.authError = nil
        do {
            let result = try await AppleSignIn.authorize()
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
        } catch AppleSignIn.Failure.canceled {
            // User dismissed the sheet — silent.
        } catch {
            logger.error("signInWithApple failed: \(error.localizedDescription, privacy: .private)")
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
```

Also add the import at the top of the file (next to the existing `import Foundation` etc.):

```swift
import AuthenticationServices
```

- [ ] **Step 2: Build**

Run from `apps/ios/`:
```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | tail -10
```
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Services/SupabaseService.swift
git commit -m "feat(ios): add signInWithApple and display name patcher"
```

---

## Task 7: SupabaseService.signInWithGoogle

**Files:**
- Modify: `apps/ios/Pebbles/Services/SupabaseService.swift`

- [ ] **Step 1: Add `signInWithGoogle`**

In `apps/ios/Pebbles/Services/SupabaseService.swift`, immediately after the new `signInWithApple()` method, insert:

```swift
    /// Sign in with Google via the native GoogleSignIn SDK.
    ///
    /// Google's id_token includes a `name` claim, which the trigger does
    /// not currently consume — so we mirror the Apple flow and patch the
    /// profile post-auth from `session.user.userMetadata`.
    func signInWithGoogle() async {
        self.authError = nil
        do {
            let result = try await GoogleSignIn.authorize()
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
        } catch GoogleSignIn.Failure.canceled {
            // User dismissed the sheet — silent.
        } catch {
            logger.error("signInWithGoogle failed: \(error.localizedDescription, privacy: .private)")
            self.authError = error.localizedDescription
        }
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
```

- [ ] **Step 2: Build**

Run from `apps/ios/`:
```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | tail -10
```
Expected: `** BUILD SUCCEEDED **`. If the implementer hits a compile error around `metadata[key]` (Supabase Swift's `AnyJSON` enum varies by version), use the equivalent pattern from `metadata[key]?.stringValue` or `String(describing:)`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Services/SupabaseService.swift
git commit -m "feat(ios): add signInWithGoogle"
```

---

## Task 8: Localized strings

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 1: Add three new keys via Xcode's String Catalog editor**

Open `Pebbles/Resources/Localizable.xcstrings` in Xcode. For each key below, add an entry, then fill in the English and French values:

| Key | English | French |
|-----|---------|--------|
| `welcome.continue.apple` | Continue with Apple | Continuer avec Apple |
| `welcome.continue.google` | Continue with Google | Continuer avec Google |
| `welcome.legal.disclosure` | Read our %@ and %@ before creating an account with Apple or Google. | Consultez nos %@ et notre %@ avant de créer un compte avec Apple ou Google. |

The two `%@` substitutions in `welcome.legal.disclosure` are filled at runtime with the localized words "Terms" / "Conditions" and "Privacy" / "Confidentialité". The literal substituted strings are added separately:

| Key | English | French |
|-----|---------|--------|
| `welcome.legal.terms` | Terms | Conditions |
| `welcome.legal.privacy` | Privacy | Confidentialité |

- [ ] **Step 2: Verify all rows are filled (no `New` / `Stale` state)**

In Xcode's String Catalog editor, confirm every new row has values in both `en` and `fr` and is in the `Translated` state.

- [ ] **Step 3: Build**

Run from `apps/ios/`:
```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | tail -10
```
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): add welcome screen apple google and disclosure strings"
```

---

## Task 9: WelcomeView UI

**Files:**
- Modify: `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift`
- Modify: `apps/ios/Pebbles/RootView.swift` (no changes expected — verify environment propagation)

- [ ] **Step 1: Replace `WelcomeView.swift`**

Replace the entire contents of `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift` with:

```swift
import SwiftUI
import UIKit

/// Pre-login landing. Persistent logo header, paged carousel of
/// `WelcomeSteps.all`, and the four entry buttons:
/// "Create an account", "Log in", "Continue with Apple", "Continue with Google".
/// A passive consent disclosure sits beneath the OAuth buttons.
///
/// Email entry buttons (`onCreateAccount` / `onLogin`) push `AuthView` via
/// the parent's NavigationPath. OAuth buttons call `SupabaseService` directly
/// — a successful sign-in flips `supabase.session` and `RootView` swaps to
/// the tab bar.
struct WelcomeView: View {
    let onCreateAccount: () -> Void
    let onLogin: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @State private var currentIndex: Int = 0
    @State private var autoAdvanceTick: Int = 0
    @State private var isSubmitting: Bool = false
    @State private var presentedLegalDoc: LegalDoc?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.colorScheme) private var colorScheme

    init(onCreateAccount: @escaping () -> Void, onLogin: @escaping () -> Void) {
        self.onCreateAccount = onCreateAccount
        self.onLogin = onLogin

        UIPageControl.appearance().currentPageIndicatorTintColor = UIColor(named: "AccentColor")
        UIPageControl.appearance().pageIndicatorTintColor = UIColor(named: "MutedForeground")
    }

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 48)

            Image("WelcomeLogo")
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .foregroundStyle(Color.pebblesForeground)
                .frame(maxWidth: 220, maxHeight: 220)

            Spacer()

            TabView(selection: $currentIndex) {
                ForEach(Array(WelcomeSteps.all.enumerated()), id: \.element.id) { index, step in
                    WelcomeSlideView(step: step)
                        .accessibilityElement(children: .combine)
                        .accessibilityLabel(
                            "Welcome step \(index + 1) of \(WelcomeSteps.all.count): "
                            + "\(String(localized: step.title)). "
                            + "\(String(localized: step.description))"
                        )
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .frame(height: 160)

            VStack(spacing: 12) {
                Button {
                    onCreateAccount()
                } label: {
                    Text("Create an account")
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .disabled(isSubmitting)

                Button {
                    onLogin()
                } label: {
                    Text("Log in")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.bordered)
                .disabled(isSubmitting)

                Button {
                    Task { await runApple() }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "applelogo")
                            .font(.body)
                        Text("welcome.continue.apple")
                            .foregroundStyle(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .tint(.black)
                .disabled(isSubmitting)

                Button {
                    Task { await runGoogle() }
                } label: {
                    HStack(spacing: 8) {
                        Image("GoogleGMark")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 18, height: 18)
                        Text("welcome.continue.google")
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.bordered)
                .tint(Color.pebblesForeground)
                .disabled(isSubmitting)

                if let error = supabase.authError {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .padding(.top, 4)
                }

                disclosureView
                    .padding(.top, 8)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
        .task(id: autoAdvanceTick) {
            guard !reduceMotion else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(4))
                if Task.isCancelled { break }
                withAnimation {
                    currentIndex = (currentIndex + 1) % WelcomeSteps.all.count
                }
            }
        }
        .onChange(of: currentIndex) { _, _ in
            autoAdvanceTick &+= 1
        }
        .sheet(item: $presentedLegalDoc) { doc in
            LegalDocumentSheet(url: doc.url)
                .ignoresSafeArea()
        }
        .pebblesScreen()
    }

    @ViewBuilder
    private var disclosureView: some View {
        let terms = AttributedString(String(localized: "welcome.legal.terms"))
        let privacy = AttributedString(String(localized: "welcome.legal.privacy"))
        var termsAttr = terms
        termsAttr.underlineStyle = .single
        termsAttr.foregroundColor = .accentColor
        termsAttr.link = URL(string: "pebbles://legal/terms")
        var privacyAttr = privacy
        privacyAttr.underlineStyle = .single
        privacyAttr.foregroundColor = .accentColor
        privacyAttr.link = URL(string: "pebbles://legal/privacy")

        let template = String(localized: "welcome.legal.disclosure")
        // Substitute %@ markers manually so we can use AttributedString.
        let parts = template.components(separatedBy: "%@")
        var combined = AttributedString("")
        if parts.count == 3 {
            combined += AttributedString(parts[0])
            combined += termsAttr
            combined += AttributedString(parts[1])
            combined += privacyAttr
            combined += AttributedString(parts[2])
        } else {
            combined = AttributedString(template)
        }

        Text(combined)
            .font(.caption)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
            .environment(\.openURL, OpenURLAction { url in
                switch url.absoluteString {
                case "pebbles://legal/terms":
                    presentedLegalDoc = .terms
                case "pebbles://legal/privacy":
                    presentedLegalDoc = .privacy
                default:
                    break
                }
                return .handled
            })
    }

    private func runApple() async {
        guard !isSubmitting else { return }
        isSubmitting = true
        await supabase.signInWithApple()
        isSubmitting = false
    }

    private func runGoogle() async {
        guard !isSubmitting else { return }
        isSubmitting = true
        await supabase.signInWithGoogle()
        isSubmitting = false
    }
}

#Preview {
    WelcomeView(
        onCreateAccount: {},
        onLogin: {}
    )
    .environment(SupabaseService())
}
```

- [ ] **Step 2: Verify `RootView` already injects `SupabaseService` into the environment**

`RootView.swift` already has `@Environment(SupabaseService.self) private var supabase` at line 21 and the environment is set on the app's root in `PebblesApp.swift`. No change required — `WelcomeView` will pick it up via the same environment.

- [ ] **Step 3: Build**

Run from `apps/ios/`:
```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | tail -10
```
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Quick simulator smoke check**

Launch the simulator from Xcode. Confirm:
- WelcomeView shows four buttons stacked + disclosure caption.
- Tapping `Terms` opens the existing legal sheet.
- Tapping `Privacy` opens the existing legal sheet.
- Tapping the disabled Google button while another auth is in flight does nothing (visual disabled state).

(Apple / Google interactive flows can only be exercised on a real device — see Task 11.)

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Welcome/WelcomeView.swift
git commit -m "feat(ios): add apple and google sign in buttons to welcome view"
```

---

## Task 10: Configure Supabase dashboard providers

**Files:** none (dashboard work)

- [ ] **Step 1: Add iOS Bundle ID to Apple provider**

In Supabase dashboard → Authentication → Providers → Apple:
- Confirm "Apple" is enabled.
- In **Authorized Client IDs**, add `app.pbbls.ios` (the iOS Bundle ID). The web Service ID stays as-is.
- Save.

- [ ] **Step 2: Add iOS OAuth client to Google provider**

In Supabase dashboard → Authentication → Providers → Google:
- Confirm "Google" is enabled.
- In **Authorized Client IDs**, add the iOS OAuth client ID (e.g. `1234567890-abcdef.apps.googleusercontent.com`). The web client ID stays as-is.
- Save.

- [ ] **Step 3: Document the change in the PR description**

Note in the PR body that these dashboard changes are required to deploy. Reviewer can mirror in staging if applicable.

(No commit — this task is dashboard-only.)

---

## Task 11: Manual TestFlight verification + lint + PR

**Files:** none (verification + PR)

- [ ] **Step 1: Build a TestFlight build**

A real device is required because:
- Sign in with Apple is unreliable on the simulator.
- Google native sign-in launches Safari and the simulator's keychain behavior diverges.

```bash
xcodebuild archive -project Pebbles.xcodeproj -scheme Pebbles -archivePath build/Pebbles.xcarchive
# Then submit via Xcode Organizer or fastlane.
```

- [ ] **Step 2: Run the verification matrix on device**

For each scenario, record pass/fail:

1. **Apple — new email** → tap "Continue with Apple", complete the system sheet with a fresh email. Expected: lands on Onboarding. `profiles.display_name` reflects Apple-provided name (verifiable via Supabase dashboard).
2. **Google — new email** → tap "Continue with Google", complete the Safari sheet with a fresh Google account. Expected: lands on Onboarding. `profiles.display_name` reflects the Google `name` claim.
3. **Apple after Google (linking)** → sign out. Tap "Continue with Apple" with the SAME email used in scenario 2. Expected: lands on `/path` directly (no Onboarding, because `hasSeenOnboarding` is `true` and the existing profile is intact). Confirm in Supabase that the same `auth.users.id` is reused and a new `auth.identities` row was added for the Apple provider.
4. **Apple "Hide my email"** → sign out. Tap "Continue with Apple" with a fresh Apple ID, choose "Hide My Email". Expected: a *new* `auth.users` row (different email — `*@privaterelay.appleid.com`). Documented behavior, not a bug.
5. **Cancel** → tap "Continue with Apple", dismiss the sheet. Expected: no error, still on WelcomeView.
6. **Network offline** → enable airplane mode, tap "Continue with Google". Expected: error caption renders under the buttons; buttons re-enable.

- [ ] **Step 3: Lint the iOS workspace**

The repo's lint pipeline does not currently include Swift, but the project guidelines call for a workspace-scoped lint at the scope of the change. Run:

```bash
npm run build --workspace=@pbbls/ios 2>&1 | tail -20
```

Expected: build succeeds. (If `@pbbls/ios` does not have a `build` script, skip this step — the xcodebuild Debug build from Task 9 covers compile-time validation.)

- [ ] **Step 4: Push the branch and open the PR**

```bash
git push -u origin feat/351-ios-apple-google-signin
```

Then:

```bash
gh pr create --title "feat(ios): apple and google sign in" --body "$(cat <<'EOF'
Resolves #351
Resolves #352

## Summary

- Adds native "Continue with Apple" (`SignInWithAppleButton` + `signInWithIdToken`) and "Continue with Google" (`GoogleSignIn-iOS` SDK + `signInWithIdToken`) to `WelcomeView`.
- Adds a passive consent disclosure beneath the OAuth buttons, with `Terms` and `Privacy` opening the existing `LegalDocumentSheet`.
- Patches `profiles.display_name` only when the trigger seeded the default `'Pebbler'` and the provider returned a name (Apple's first-time `fullName`, Google's id_token `name` claim). Idempotent.
- Account linking by verified email is left to Supabase defaults: Apple after Google with the same confirmed email returns the same `auth.users` row.

## Files

- `apps/ios/Pebbles/Services/AppleSignIn.swift` (new)
- `apps/ios/Pebbles/Services/GoogleSignIn.swift` (new)
- `apps/ios/Pebbles/Services/SupabaseService.swift` — `signInWithApple()`, `signInWithGoogle()`, display-name patcher
- `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift` — two new buttons + disclosure
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — 5 new keys (en + fr)
- `apps/ios/Pebbles/Resources/Info.plist` + `Config/Secrets.example.xcconfig` + `AppEnvironment.swift` — Google iOS client wiring
- `apps/ios/project.yml` — `GoogleSignIn-iOS` SPM dependency
- `apps/ios/Pebbles/PebblesApp.swift` — SDK config + `.onOpenURL` URL handler

## Required dashboard changes (deploy gate)

- Supabase Auth → Apple provider: add `app.pbbls.ios` to Authorized Client IDs.
- Supabase Auth → Google provider: add the iOS OAuth client ID to Authorized Client IDs.
- Google Cloud Console: ensure an iOS OAuth client exists for bundle ID `app.pbbls.ios`. Copy the client ID and reversed client ID into local `Config/Secrets.xcconfig`.

## Test plan

- [x] New email via Apple → new account
- [x] New email via Google → new account
- [x] Apple sign-in after a Google account exists with the same confirmed email → same `auth.users` row, new identity attached
- [x] Apple "Hide my email" → separate account (documented; relay address ≠ original email)
- [x] Cancel either system sheet → silent, stays on WelcomeView
- [x] Network offline → error surfaces under buttons
EOF
)" --label feat --label core --label ios --milestone "M29 · Extended login and signup on iOS"
```

If the user prefers to confirm labels/milestone interactively, run `gh pr create` without `--label`/`--milestone` and apply them after.

- [ ] **Step 5: Update the spec/plan with any deviations**

If any task required a non-trivial deviation (e.g. `AnyJSON` API differences in Supabase Swift), edit the spec or plan file with a short note so future readers see what actually shipped.

```bash
git commit --allow-empty -m "docs(ios): note deviations from apple/google signin plan"
git push
```

(Skip this commit if no deviations.)

---

## Self-review checklist (run before handing back to the user)

- All five spec sections (Architecture, UX, Account linking, Auth state flow, Display-name patching) have at least one task. ✓
- Open dashboard work from the spec is captured as Task 10. ✓
- Manual verification matrix covers all five spec test scenarios. ✓
- No `TODO` / `TBD` / "implement later" anywhere in the plan. ✓
- Type names consistent: `AppleSignInResult.fullName`, `AppleSignInResult.rawNonce`, `AppleSignInResult.idToken`; `GoogleSignInResult.idToken`, `GoogleSignInResult.accessToken`. ✓
- Branch name `feat/351-ios-apple-google-signin` matches repo convention. ✓
