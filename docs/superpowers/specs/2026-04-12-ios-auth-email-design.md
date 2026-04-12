# iOS Auth Flow — Email Signup & Login (Design Spec)

**Resolves:** #201 (partial — this PR covers email; Apple Sign-In is a follow-up)
**Date:** 2026-04-12
**Milestone:** M17 · iOS project bootstrap
**Scope decision:** Split from #201 — email flow only. Apple Sign-In will be a separate issue and PR that builds on this one.

## Context

The iOS app shell from PR #242 is running as a two-tab (Path, Profile) SwiftUI app with `SupabaseService` injected into the environment but unused. No sign-in UI exists, so the app is effectively a static placeholder.

Issue #201 asks for: email + Apple Sign-In auth, session persistence across launches, an auth gate that swaps the root UI based on session state, and profile creation on first login via the existing Supabase trigger.

This spec covers **only the email path**. Reasons for splitting:
- Apple Sign-In requires Apple Developer console + Supabase dashboard configuration (Services ID, key, provider enablement). Bundling that with the code introduces two categories of failure — code bugs and config bugs — into a single PR, which is poor for debuggability on a first iOS PR.
- The email flow exercises the entire plumbing (session state, auth gate, stream subscription, consent capture, logout) end-to-end. Once merged, Apple Sign-In becomes a purely additive change: a new button + a new branch in `SupabaseService`.

## Intent

After this PR ships:

- A fresh install opens on an `AuthView` with a segmented Log In / Sign Up control.
- A user can sign up with email + password, accept the two consent checkboxes, and land directly on the main tab bar (confirmation email is disabled in the Supabase dashboard).
- A user can log in with existing credentials and land on the main tab bar.
- The session persists across launches — force-quitting and relaunching keeps the user signed in.
- The user can log out from the Profile tab and is returned to `AuthView`.
- Signup errors and login errors surface as inline red text below the form.
- Tapping "Terms of Service" or "Privacy Policy" on the signup form opens the document in an in-app `SFSafariViewController` sheet that dismisses with "Done".

## Non-goals (explicit)

Each of these is out of scope and will become its own issue if needed:

1. **Apple Sign-In.** Follow-up PR.
2. **Fixing the `handle_new_user` trigger** to copy `terms_accepted_at` / `privacy_accepted_at` from `raw_user_meta_data` into `public.profiles`. Latent gap present on web today; iOS mirrors web behavior so the two stay consistent until the trigger fix lands separately.
3. **Password reset / forgot password flow.**
4. **Email confirmation flow** (currently disabled in the Supabase dashboard). Re-enabling it requires deep-link handling, which will be tackled alongside Apple Sign-In since that flow uses the same URL-scheme machinery.
5. **App icon / launch screen artwork.** Still deferred from #200.
6. **Client-side rate limiting or captcha.** Supabase's built-in rate limits are enough for V1.
7. **UI tests.** No `PebblesUITests` target is added.

## Architecture

One source of truth for auth state lives on the already-injected `SupabaseService`. The service subscribes to Supabase's `authStateChanges` async stream and mutates three observable properties. `RootView` reads those properties to decide which subtree to render.

```
PebblesApp
  └── creates SupabaseService, injects via .environment
  └── RootView
        └── .task { await supabase.start() }   // subscribes to authStateChanges once
        └── switch on (isInitializing, session):
              • isInitializing        → Color.clear
              • session == nil        → AuthView
              • session != nil        → MainTabView
```

### Why extend `SupabaseService` instead of creating a separate `AuthStore`

- `SupabaseService` is already `@Observable` and already in the environment. Adding state to it costs zero new injection wiring.
- Views that perform auth actions (`AuthView`, `ProfileView`) already have the service in scope.
- Our iOS CLAUDE.md is explicit that protocols/abstractions should be extracted when a test needs them, not before. A separate `AuthStore` is the "correct" long-term shape, but earning it with a concrete pain point is cheaper than predicting it.
- If the service grows uncomfortable later, extracting an `AuthStore` is a mechanical refactor — the call sites already read one observable source of truth.

### State model

```swift
@Observable
final class SupabaseService {
    let client: SupabaseClient
    var session: Session?
    var isInitializing: Bool = true
    var authError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "auth")

    init() { ... }                 // unchanged from PR #242

    func start() async             // subscribes to authStateChanges, never returns
    func signIn(email:password:) async
    func signUp(email:password:) async
    func signOut() async
}
```

### State transitions

| Event | Mutation | Visible effect |
|---|---|---|
| App launch | `isInitializing = true`, `session = nil` | `RootView` renders `Color.clear` |
| First `authStateChanges` event (`.initialSession`) | `isInitializing = false`, `session = <persisted or nil>` | Swap to `AuthView` or `MainTabView` |
| Successful `signIn` / `signUp` | `.signedIn` event fires → `session` populated, `authError` cleared | Swap to `MainTabView` |
| Failed `signIn` / `signUp` | `authError = error.localizedDescription`, logged | Red inline text under form |
| `signOut()` called | `.signedOut` event fires → `session = nil` | Swap to `AuthView` |
| Background token refresh | `.tokenRefreshed` event, `session` updated | No UI change |
| Session revoked / refresh failure | `.signedOut` event | Swap to `AuthView` |

### Critical implementation rule: no `await` inside the stream handler

The web-side CLAUDE.md is explicit: awaiting a Supabase call from inside `onAuthStateChange` deadlocks the client's internal lock. The Swift SDK has the same invariant. `start()` must mutate state synchronously and never `await` another SDK call inside the `for await`:

```swift
func start() async {
    for await (event, session) in client.auth.authStateChanges {
        self.session = session
        self.isInitializing = false
        if event == .signedIn { self.authError = nil }
    }
    logger.error("authStateChanges stream ended unexpectedly")
}
```

If we ever need to fetch the profile row after sign-in (we don't in this PR — the trigger creates it), we launch a detached `Task { }` from the view layer, not from inside this loop.

## File layout

```
apps/ios/Pebbles/
  PebblesApp.swift              (unchanged)
  RootView.swift                (MODIFIED — becomes the auth gate)
  Features/
    Main/
      MainTabView.swift         (NEW — what RootView used to be)
    Auth/
      AuthView.swift             (NEW)
      ConsentCheckbox.swift      (NEW)
      LegalDocumentSheet.swift   (NEW — SFSafariViewController wrapper)
    Profile/
      ProfileView.swift          (MODIFIED — adds logout button)
  Services/
    SupabaseService.swift        (MODIFIED — adds session, auth methods, stream subscription)
PebblesTests/
  SupabaseServiceTests.swift     (NEW — initial-state smoke test)
```

### XcodeGen note

`project.yml` needs to regenerate to pick up the new Swift files, but no new frameworks or SPM dependencies are required. `SafariServices` is part of the system SDK. Apple Sign-In's `AuthenticationServices` is deferred with the feature.

## `RootView` — the auth gate

```swift
struct RootView: View {
    @Environment(SupabaseService.self) private var supabase

    var body: some View {
        ZStack {
            if supabase.isInitializing {
                Color.clear
            } else if supabase.session == nil {
                AuthView()
            } else {
                MainTabView()
            }
        }
        .task { await supabase.start() }
    }
}
```

`.task` is view-scoped: it starts when `RootView` appears and is cancelled if the view ever disappears. For the root view that is never — which is exactly the lifetime we want.

## `MainTabView`

A verbatim move of the current `RootView` body:

```swift
struct MainTabView: View {
    var body: some View {
        TabView {
            PathView()
                .tabItem { Label("Path", systemImage: "point.topleft.down.to.point.bottomright.curvepath") }
            ProfileView()
                .tabItem { Label("Profile", systemImage: "person.crop.circle") }
        }
    }
}
```

## `AuthView` — detailed behavior

### View-local state

```swift
enum Mode { case login, signup }

@State private var mode: Mode = .login
@State private var email = ""
@State private var password = ""
@State private var termsAccepted = false
@State private var privacyAccepted = false
@State private var presentedLegalDoc: LegalDoc?
@State private var isSubmitting = false
@Environment(SupabaseService.self) private var supabase
```

### Layout (top to bottom)

1. "Pebbles" wordmark (`Text`, `.font(.largeTitle)`)
2. Segmented `Picker` bound to `mode` with "Log In" / "Sign Up"
3. `TextField` for email with `.keyboardType(.emailAddress)`, `.textContentType(.emailAddress)`, `.textInputAutocapitalization(.never)`, `.autocorrectionDisabled()`
4. `SecureField` for password with `.textContentType(mode == .login ? .password : .newPassword)`
5. (Signup only) `ConsentCheckbox` for Terms
6. (Signup only) `ConsentCheckbox` for Privacy
7. Inline error `Text` (hidden when `supabase.authError == nil`), `.foregroundStyle(.red)`
8. "Continue" `Button` — disabled when `!canSubmit`

### `canSubmit` computed property

```swift
private var canSubmit: Bool {
    guard !isSubmitting,
          email.contains("@"),
          password.count >= 6
    else { return false }

    if mode == .signup {
        return termsAccepted && privacyAccepted
    }
    return true
}
```

No validation framework. A plain `Bool` drives `.disabled(!canSubmit)`.

### Mode toggle side effects

When `mode` changes (via `.onChange(of: mode)`):
- `supabase.authError = nil`
- If leaving Signup: `termsAccepted = false`, `privacyAccepted = false` (legal consent must be fresh per submission)

### Submit action

```swift
Task {
    isSubmitting = true
    switch mode {
    case .login:  await supabase.signIn(email: email, password: password)
    case .signup: await supabase.signUp(email: email, password: password)
    }
    isSubmitting = false
}
```

On success, `session` becomes non-nil, `RootView` re-renders, `AuthView` disappears. The submit `Task` does not navigate.

### Clearing errors as the user types

`.onChange(of: email)` and `.onChange(of: password)` set `supabase.authError = nil` so a stale error doesn't hang around after the user starts correcting their input.

## `ConsentCheckbox`

```swift
struct ConsentCheckbox: View {
    @Binding var isChecked: Bool
    let prefix: String
    let linkText: String
    let onLinkTap: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Button { isChecked.toggle() } label: {
                Image(systemName: isChecked ? "checkmark.square.fill" : "square")
                    .font(.title3)
            }
            .buttonStyle(.plain)

            (Text(prefix) + Text(linkText).underline().foregroundColor(.accentColor))
                .onTapGesture { onLinkTap() }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}
```

Tapping anywhere on the label text opens the legal sheet. This is a deliberate simplification over per-range link handling (which `Text` concatenation doesn't support without AttributedString gymnastics). If users find it awkward in practice, the fallback is two separate `Text`s in an `HStack`.

## `LegalDocumentSheet`

`UIViewControllerRepresentable` wrapping `SFSafariViewController`. `SFSafariViewController` is deliberately chosen over a `WKWebView`:

- It's the same in-app Safari used by Mail and Messages, so users recognize it.
- Reader mode, share, dismiss button are all free.
- SwiftUI's native `WebView` doesn't exist until iOS 18; our target is 17.0.

```swift
enum LegalDoc: String, Identifiable {
    case terms, privacy
    var id: String { rawValue }
    var url: URL {
        switch self {
        case .terms:   URL(string: "https://www.pbbls.app/docs/terms")!
        case .privacy: URL(string: "https://www.pbbls.app/docs/privacy")!
        }
    }
}

struct LegalDocumentSheet: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }
    func updateUIViewController(_ vc: SFSafariViewController, context: Context) {}
}
```

Bound from `AuthView` via `.sheet(item: $presentedLegalDoc) { LegalDocumentSheet(url: $0.url) }`. Dismissal uses Safari's built-in "Done" button — we don't wire a dismiss callback.

## `ProfileView` — logout button

The current placeholder body is augmented with a single `Button("Log out")` at the bottom of the view, inside a `VStack`. Tapping it launches a `Task { await supabase.signOut() }`. No confirmation dialog.

This is the first place in the codebase where a view calls a `SupabaseService` method directly, which also serves as a real sanity check on the injection wiring.

## `SupabaseService` — full method bodies

### `signIn`

```swift
func signIn(email: String, password: String) async {
    do {
        try await client.auth.signIn(email: email, password: password)
        // .signedIn event will propagate via authStateChanges
    } catch {
        logger.error("signIn failed: \(error.localizedDescription, privacy: .public)")
        self.authError = error.localizedDescription
    }
}
```

### `signUp`

```swift
func signUp(email: String, password: String) async {
    let now = ISO8601DateFormatter().string(from: Date())
    do {
        try await client.auth.signUp(
            email: email,
            password: password,
            data: [
                "terms_accepted_at":   .string(now),
                "privacy_accepted_at": .string(now),
            ]
        )
    } catch {
        logger.error("signUp failed: \(error.localizedDescription, privacy: .public)")
        self.authError = error.localizedDescription
    }
}
```

The timestamps land in `auth.users.raw_user_meta_data`. The `handle_new_user` trigger does not currently copy them to `public.profiles` — this matches web behavior and is acknowledged as a separate issue.

### `signOut`

```swift
func signOut() async {
    do {
        try await client.auth.signOut()
        // .signedOut event will propagate via authStateChanges
    } catch {
        logger.error("signOut failed: \(error.localizedDescription, privacy: .public)")
        // No user-facing alert — signOut should never fail-visibly.
        // The local token is wiped regardless; the stream will still emit .signedOut.
    }
}
```

## Error handling summary

| Source | Handling |
|---|---|
| `signIn` / `signUp` failures | `logger.error`, set `authError`, inline red text in `AuthView` |
| `signOut` failure | `logger.error` only. Never alerts the user. |
| `authStateChanges` stream terminating | `logger.error` after the `for await` loop exits |
| Missing/invalid Supabase config | `AppEnvironment.fatalError` at launch (unchanged from PR #242) |

All errors are logged with `os.Logger` (category: `auth`). No empty catch blocks. Mirrors the web discipline from our main CLAUDE.md: silent failures are bugs.

## Testing

Per iOS CLAUDE.md:
- Swift Testing, not XCTest
- No mocking of `SupabaseClient` yet (no `SupabaseServicing` protocol — extract only when a test needs it)
- No UI tests

One new test file:

```swift
// PebblesTests/SupabaseServiceTests.swift
import Testing
@testable import Pebbles

@Suite struct SupabaseServiceTests {
    @Test func initialStateIsInitializingWithNoSession() {
        let service = SupabaseService()
        #expect(service.session == nil)
        #expect(service.isInitializing == true)
        #expect(service.authError == nil)
    }
}
```

This proves the service compiles, the new properties have the expected defaults, and the type is constructible without hitting the network.

## Manual test plan (for PR description)

1. Fresh simulator install → launch → `AuthView` appears in Login mode.
2. Toggle to Signup. Enter valid email + password. Continue is disabled until both checkboxes are checked.
3. Check both. Continue enables. Tap → land on `MainTabView` (Path tab).
4. Profile tab → Log out → `AuthView` appears in Login mode.
5. Enter the same credentials → Continue → `MainTabView`.
6. Force-quit, relaunch → brief `Color.clear` → lands on `MainTabView` without re-auth.
7. Log out → enter wrong password → inline red error under form.
8. Switch to Signup, tap "Terms of Service" → `SFSafariViewController` sheet opens `https://www.pbbls.app/docs/terms`.
9. Tap "Done" → returns to signup form with field state preserved.
10. Same check for Privacy Policy.

## Acceptance checklist

- [ ] Fresh install shows `AuthView` in Login mode
- [ ] Signup with valid email/password + both checkboxes lands on `MainTabView`
- [ ] Login with valid credentials lands on `MainTabView`
- [ ] Session persists across force-quit/relaunch
- [ ] Logout from Profile tab returns to `AuthView`
- [ ] Invalid credentials surface as inline red text under the form
- [ ] Signup button is disabled until both consent checkboxes are checked
- [ ] Tapping "Terms of Service" opens pbbls.app/docs/terms in an in-app Safari sheet
- [ ] Tapping "Privacy Policy" opens pbbls.app/docs/privacy in an in-app Safari sheet
- [ ] `SupabaseServiceTests` passes
- [ ] `swiftlint` reports 0 errors
- [ ] `npm run build --workspace=@pbbls/ios` succeeds
- [ ] `npm run test --workspace=@pbbls/ios` passes

## Follow-up issues to file after this ships

1. **`[Feat] iOS Apple Sign-In`** (scope: `feat`, `auth`; milestone: M17) — adds the `SignInWithAppleButton`, new branch in `SupabaseService`, Supabase dashboard provider config, deep-link URL scheme for OAuth callback.
2. **`[Fix] handle_new_user trigger should copy consent timestamps into profiles`** (scope: `fix`, `db`) — fixes the latent gap on both web and iOS.
3. **`[Feat] iOS password reset flow`** (scope: `feat`, `auth`) — lower priority.
4. **`[Feat] Re-enable email confirmation + deep-link handling`** (scope: `feat`, `auth`) — bundled with whichever PR first needs URL-scheme handling.
