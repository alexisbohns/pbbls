# iOS Auth Flow — Email Signup & Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add email signup/login + session gate + logout to the iOS app, mirroring the web auth scope minus Apple Sign-In.

**Architecture:** Auth state lives as `@Observable` properties on the existing `SupabaseService`. `RootView` becomes a three-way gate (`initializing` / `signedOut` → `AuthView` / `signedIn` → `MainTabView`). Auth state is driven by Supabase's `authStateChanges` async stream, subscribed once from `RootView.task`.

**Tech Stack:** SwiftUI (iOS 17), Swift 5.9, supabase-swift ≥ 2.0.0, Swift Testing, SFSafariViewController (system framework, no new SPM deps).

**Spec:** `docs/superpowers/specs/2026-04-12-ios-auth-email-design.md`

**Branch:** `feat/201-ios-auth-flow` (already created)

---

## Pre-flight

Before Task 1, verify the workspace is clean and the existing iOS project still builds. This catches any drift between the spec and reality before we touch code.

- [ ] **Pre-flight 1: Working tree clean on the feature branch**

Run:
```bash
git status && git rev-parse --abbrev-ref HEAD
```

Expected: `nothing to commit, working tree clean` and `feat/201-ios-auth-flow`.

- [ ] **Pre-flight 2: Regenerate Xcode project**

Run:
```bash
npm run generate --workspace=@pbbls/ios
```

Expected: XcodeGen prints `Created project at .../Pebbles.xcodeproj` with no errors.

- [ ] **Pre-flight 3: Baseline build + tests pass**

Run:
```bash
npm run build --workspace=@pbbls/ios && npm run test --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **` and `** TEST SUCCEEDED **` (the existing `PebblesSmokeTests` smoke test passes).

If either fails, STOP and diagnose — the plan assumes a green baseline.

- [ ] **Pre-flight 4: Note the real supabase-swift API**

The spec uses illustrative signatures for `client.auth.signIn`, `signUp`, `signOut`, and `authStateChanges`. The real supabase-swift v2 surface has been stable since 2.0.0 but we should verify once before writing code.

Run:
```bash
find apps/ios -type d -name "supabase-swift" 2>/dev/null
```

Expected: a path under `apps/ios/Pebbles.xcodeproj/.../SourcePackages/checkouts/supabase-swift` (XcodeGen resolved SPM during Pre-flight 2).

Then read the relevant files:

```bash
ls "$(find apps/ios -type d -name 'supabase-swift' 2>/dev/null | head -1)/Sources/Auth/"
```

Expected: includes `AuthClient.swift`, `Types.swift`, and others.

Confirm these symbols exist (grep for them):
- `client.auth.signIn(email:password:)` → `AuthClient.signIn`
- `client.auth.signUp(email:password:data:)` → `AuthClient.signUp`
- `client.auth.signOut()` → `AuthClient.signOut`
- `client.auth.authStateChanges` → property returning `AsyncStream<(AuthChangeEvent, Session?)>` (verify the tuple shape — in some versions it's a single `AuthStateChange` struct with `.event` and `.session` properties)

If any signature in the plan's code blocks doesn't match, update the plan's code to match the real API **before** proceeding. Do NOT guess — read the SDK source.

---

## File Structure

```
apps/ios/Pebbles/
  RootView.swift                         (MODIFIED — Task 5)
  Features/
    Main/
      MainTabView.swift                  (NEW — Task 4)
    Auth/
      AuthView.swift                     (NEW — Tasks 8, 9)
      ConsentCheckbox.swift              (NEW — Task 7)
      LegalDocumentSheet.swift           (NEW — Task 6)
    Profile/
      ProfileView.swift                  (MODIFIED — Task 10)
  Services/
    SupabaseService.swift                (MODIFIED — Tasks 1, 2, 3)
apps/ios/PebblesTests/
  SupabaseServiceTests.swift             (NEW — Task 1)
```

All files are under `apps/ios/Pebbles/` which is globbed by XcodeGen (`sources: - path: Pebbles`). New files are picked up automatically on `xcodegen generate`, so we regenerate exactly once at Task 11 — or sooner, if your edit-compile-run loop wants it.

---

## Task 1 — Extend `SupabaseService` with auth state (TDD)

Adds the three `@Observable` properties and writes a failing Swift Testing test that asserts their default values.

**Files:**
- Modify: `apps/ios/Pebbles/Services/SupabaseService.swift`
- Create: `apps/ios/PebblesTests/SupabaseServiceTests.swift`

- [ ] **Step 1.1: Write the failing test**

Create `apps/ios/PebblesTests/SupabaseServiceTests.swift`:

```swift
import Testing
@testable import Pebbles

@Suite("SupabaseService auth state")
struct SupabaseServiceTests {
    @Test("Service initializes with no session, initializing true, no error")
    func initialStateIsInitializingWithNoSession() {
        let service = SupabaseService()
        #expect(service.session == nil)
        #expect(service.isInitializing == true)
        #expect(service.authError == nil)
    }
}
```

- [ ] **Step 1.2: Regenerate Xcode project so the new test file is included**

Run:
```bash
npm run generate --workspace=@pbbls/ios
```

Expected: completes without errors. XcodeGen picks up the new file under the `PebblesTests` target.

- [ ] **Step 1.3: Run the test and verify it fails to compile**

Run:
```bash
npm run test --workspace=@pbbls/ios
```

Expected: compile error — `value of type 'SupabaseService' has no member 'session'` (or similar). This is the correct failure; it proves the test is actually exercising the types we're about to add.

- [ ] **Step 1.4: Add the properties to `SupabaseService`**

Replace the entire contents of `apps/ios/Pebbles/Services/SupabaseService.swift` with:

```swift
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
```

Note: `start()`, `signIn`, `signUp`, `signOut` come in later tasks. This task is only about the stored state.

- [ ] **Step 1.5: Run the test and verify it passes**

Run:
```bash
npm run test --workspace=@pbbls/ios
```

Expected: `** TEST SUCCEEDED **`. Both `PebblesSmokeTests` and `SupabaseServiceTests` should show as passing.

- [ ] **Step 1.6: Commit**

```bash
git add apps/ios/Pebbles/Services/SupabaseService.swift apps/ios/PebblesTests/SupabaseServiceTests.swift
git commit -m "feat(ios): add auth state properties to SupabaseService"
```

---

## Task 2 — Subscribe to `authStateChanges` in `start()`

Adds the long-lived async subscription that drives `session` and `isInitializing`. No view yet calls `start()`; we wire it from `RootView` in Task 5.

**Files:**
- Modify: `apps/ios/Pebbles/Services/SupabaseService.swift`

- [ ] **Step 2.1: Add `start()` to `SupabaseService`**

Append these methods inside the `SupabaseService` class, after the `init`:

```swift
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
```

**If Pre-flight 4 revealed a different tuple shape** (e.g. the stream yields a single `AuthStateChange` struct instead of a tuple), adapt the `for await` pattern accordingly. The important invariants are:
1. Mutate `self.session` from the event's session
2. Set `self.isInitializing = false` unconditionally after the first event
3. Clear `self.authError` on `.signedIn`
4. Never `await` another Supabase call inside the loop body

- [ ] **Step 2.2: Build and verify it compiles**

Run:
```bash
npm run build --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **`. No new tests to run — `start()` has no return value we can assert against without a real Supabase connection, and we're deliberately not mocking the client per CLAUDE.md.

- [ ] **Step 2.3: Commit**

```bash
git add apps/ios/Pebbles/Services/SupabaseService.swift
git commit -m "feat(ios): subscribe to Supabase authStateChanges in start()"
```

---

## Task 3 — Add `signIn`, `signUp`, `signOut` methods

Adds the three auth actions that `AuthView` and `ProfileView` will call. Each handles errors via `os.Logger` + `authError`, never silently.

**Files:**
- Modify: `apps/ios/Pebbles/Services/SupabaseService.swift`

- [ ] **Step 3.1: Add the three methods**

Append inside the `SupabaseService` class, after `start()`:

```swift
    /// Sign in with email + password. On success, the `.signedIn` event flows
    /// through `authStateChanges` and `session` becomes non-nil.
    func signIn(email: String, password: String) async {
        do {
            try await client.auth.signIn(email: email, password: password)
        } catch {
            logger.error("signIn failed: \(error.localizedDescription, privacy: .public)")
            self.authError = error.localizedDescription
        }
    }

    /// Sign up with email + password. Consent timestamps are captured now and
    /// passed through `auth.users.raw_user_meta_data`. Note: the current
    /// `handle_new_user` DB trigger does not copy them into `public.profiles`
    /// — this mirrors web behavior and will be fixed in a separate `fix(db)` issue.
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

    /// Sign out. Failures are logged but never surfaced as alerts — the local
    /// token is wiped regardless and the stream will emit `.signedOut`.
    func signOut() async {
        do {
            try await client.auth.signOut()
        } catch {
            logger.error("signOut failed: \(error.localizedDescription, privacy: .public)")
        }
    }
```

**Compatibility note:** supabase-swift v2's `signUp` accepts a `data` parameter typed `[String: AnyJSON]`. The `.string(...)` syntax uses `AnyJSON` enum cases. If the SDK in use exposes a different name (e.g. `AnyValue`, `JSONValue`), substitute accordingly — grep for `AnyJSON` in the `supabase-swift/Sources/Auth` checkout from Pre-flight 4. If the parameter is named differently (e.g. `userMetadata:` instead of `data:`), use the actual label.

- [ ] **Step 3.2: Build and verify it compiles**

Run:
```bash
npm run build --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3.3: Run the existing test to make sure nothing regressed**

Run:
```bash
npm run test --workspace=@pbbls/ios
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 3.4: Commit**

```bash
git add apps/ios/Pebbles/Services/SupabaseService.swift
git commit -m "feat(ios): add signIn, signUp, signOut methods to SupabaseService"
```

---

## Task 4 — Extract `MainTabView` from `RootView`

Pure refactor: the current `RootView` body moves verbatim into a new `MainTabView`. `RootView` is temporarily left as a trivial wrapper around `MainTabView` so the app still builds and launches the same way. Task 5 turns `RootView` into the actual auth gate.

**Files:**
- Create: `apps/ios/Pebbles/Features/Main/MainTabView.swift`
- Modify: `apps/ios/Pebbles/RootView.swift`

- [ ] **Step 4.1: Create `MainTabView.swift`**

Create `apps/ios/Pebbles/Features/Main/MainTabView.swift`:

```swift
import SwiftUI

/// The signed-in root of the app. Shown by `RootView` when a Supabase session
/// is present. Adds no behavior beyond the TabView — per-tab logic lives under
/// `Features/Path/` and `Features/Profile/`.
struct MainTabView: View {
    var body: some View {
        TabView {
            PathView()
                .tabItem {
                    Label("Path", systemImage: "point.topleft.down.to.point.bottomright.curvepath")
                }

            ProfileView()
                .tabItem {
                    Label("Profile", systemImage: "person.crop.circle")
                }
        }
    }
}

#Preview {
    MainTabView()
}
```

- [ ] **Step 4.2: Temporarily shrink `RootView` to wrap `MainTabView`**

Replace `apps/ios/Pebbles/RootView.swift` with:

```swift
import SwiftUI

/// Auth gate — Task 5 rewrites this as a switch over `SupabaseService`.
/// For now it just forwards to `MainTabView` so the app still launches.
struct RootView: View {
    var body: some View {
        MainTabView()
    }
}

#Preview {
    RootView()
}
```

- [ ] **Step 4.3: Regenerate Xcode project to pick up `MainTabView.swift`**

Run:
```bash
npm run generate --workspace=@pbbls/ios
```

Expected: completes without errors.

- [ ] **Step 4.4: Build**

Run:
```bash
npm run build --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4.5: Commit**

```bash
git add apps/ios/Pebbles/Features/Main/MainTabView.swift apps/ios/Pebbles/RootView.swift
git commit -m "refactor(ios): extract MainTabView from RootView"
```

---

## Task 5 — Convert `RootView` into the auth gate

`RootView` reads `SupabaseService`, calls `start()` in a `.task`, and switches between `Color.clear`, a placeholder "Not signed in" text, and `MainTabView`. We use a placeholder for the unauthenticated branch because `AuthView` doesn't exist yet — Task 8 replaces the placeholder.

**Files:**
- Modify: `apps/ios/Pebbles/RootView.swift`

- [ ] **Step 5.1: Rewrite `RootView.swift`**

Replace with:

```swift
import SwiftUI

/// Top-level auth gate. Reads `SupabaseService` from the environment and
/// decides whether to show the auth screen or the main tab bar.
///
/// - `isInitializing`: Supabase is still reading the persisted session from
///   the keychain. Render nothing so the user never sees a flash of the wrong UI.
/// - `session == nil`: signed out → show AuthView (currently a placeholder,
///   replaced in Task 8).
/// - `session != nil`: signed in → show MainTabView.
///
/// `.task { await supabase.start() }` subscribes to `authStateChanges` for the
/// lifetime of this view, which equals the lifetime of the app.
struct RootView: View {
    @Environment(SupabaseService.self) private var supabase

    var body: some View {
        ZStack {
            if supabase.isInitializing {
                Color.clear
            } else if supabase.session == nil {
                // Placeholder — replaced by AuthView() in Task 8.
                Text("Not signed in")
                    .foregroundStyle(.secondary)
            } else {
                MainTabView()
            }
        }
        .task {
            await supabase.start()
        }
    }
}

#Preview {
    RootView()
        .environment(SupabaseService())
}
```

- [ ] **Step 5.2: Build**

Run:
```bash
npm run build --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5.3: Run on simulator and verify the gate works with a real backend**

Launch the app in the iPhone simulator from Xcode (⌘R). Since you likely have no session yet:

Expected behavior:
1. Brief blank screen (milliseconds) while `isInitializing` is true.
2. Then the placeholder "Not signed in" text in the middle of the screen.
3. No crash; the console shows no errors.

If there IS a persisted session from earlier manual testing:
- The app lands on `MainTabView` (Path tab). That's also a valid pass.

If neither works — if the app crashes or hangs on `Color.clear` — `start()` or the event stream wiring is wrong. Debug before proceeding.

- [ ] **Step 5.4: Commit**

```bash
git add apps/ios/Pebbles/RootView.swift
git commit -m "feat(ios): make RootView an auth gate driven by SupabaseService"
```

---

## Task 6 — `LegalDocumentSheet` (SFSafariViewController wrapper)

A tiny `UIViewControllerRepresentable` that presents an in-app Safari view for the Terms/Privacy URLs. Needed by `AuthView`'s consent checkboxes.

**Files:**
- Create: `apps/ios/Pebbles/Features/Auth/LegalDocumentSheet.swift`

- [ ] **Step 6.1: Create the file**

Create `apps/ios/Pebbles/Features/Auth/LegalDocumentSheet.swift`:

```swift
import SwiftUI
import SafariServices

/// Identifies which legal document is currently shown in the auth sheet.
/// `Identifiable` so it can drive `.sheet(item:)`.
enum LegalDoc: String, Identifiable {
    case terms
    case privacy

    var id: String { rawValue }

    var url: URL {
        switch self {
        case .terms:   return URL(string: "https://www.pbbls.app/docs/terms")!
        case .privacy: return URL(string: "https://www.pbbls.app/docs/privacy")!
        }
    }
}

/// Thin SwiftUI wrapper around `SFSafariViewController`. Presents a legal
/// document in the same in-app Safari used by Mail and Messages. The sheet
/// dismisses via Safari's built-in "Done" button — no custom chrome.
struct LegalDocumentSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {
        // SFSafariViewController is immutable after init — nothing to update.
    }
}
```

- [ ] **Step 6.2: Regenerate Xcode project + build**

Run:
```bash
npm run generate --workspace=@pbbls/ios && npm run build --workspace=@pbbls/ios
```

Expected: both succeed. `SafariServices` is a system framework and is auto-linked on first use — no `project.yml` change needed.

- [ ] **Step 6.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Auth/LegalDocumentSheet.swift
git commit -m "feat(ios): add LegalDocumentSheet SFSafariViewController wrapper"
```

---

## Task 7 — `ConsentCheckbox`

A small reusable row with a tappable checkbox + label text that includes a tappable link fragment. Used twice on `AuthView`'s signup mode (Terms, Privacy).

**Files:**
- Create: `apps/ios/Pebbles/Features/Auth/ConsentCheckbox.swift`

- [ ] **Step 7.1: Create the file**

Create `apps/ios/Pebbles/Features/Auth/ConsentCheckbox.swift`:

```swift
import SwiftUI

/// A checkbox row with a label like "I accept the Terms of Service", where the
/// "Terms of Service" fragment is visually styled as a link and tapping anywhere
/// on the label text invokes `onLinkTap`. The tap area covers the whole label
/// (not just the link fragment) because SwiftUI `Text` concatenation doesn't
/// expose per-range gestures cleanly — a deliberate simplification.
struct ConsentCheckbox: View {
    @Binding var isChecked: Bool
    let prefix: String
    let linkText: String
    let onLinkTap: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Button {
                isChecked.toggle()
            } label: {
                Image(systemName: isChecked ? "checkmark.square.fill" : "square")
                    .font(.title3)
                    .foregroundStyle(isChecked ? Color.accentColor : Color.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isChecked ? "Checked" : "Unchecked")

            (Text(prefix) + Text(linkText).underline().foregroundColor(.accentColor))
                .font(.subheadline)
                .onTapGesture {
                    onLinkTap()
                }
        }
    }
}

#Preview {
    @Previewable @State var checked = false
    return ConsentCheckbox(
        isChecked: $checked,
        prefix: "I accept the ",
        linkText: "Terms of Service",
        onLinkTap: { print("link tapped") }
    )
    .padding()
}
```

Note: `@Previewable` is an iOS 17+ macro that lets you declare `@State` in a `#Preview` block. If the preview compiler complains, wrap the preview in a small helper struct instead.

- [ ] **Step 7.2: Regenerate + build**

Run:
```bash
npm run generate --workspace=@pbbls/ios && npm run build --workspace=@pbbls/ios
```

Expected: both succeed.

- [ ] **Step 7.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Auth/ConsentCheckbox.swift
git commit -m "feat(ios): add ConsentCheckbox reusable view"
```

---

## Task 8 — `AuthView` scaffold (segmented picker + form + submit, no consent yet)

Creates `AuthView` with the segmented Login/Signup picker, email/password fields, inline error display, Continue button, and submission wired to `SupabaseService`. Consent checkboxes and legal sheet come in Task 9.

**Files:**
- Create: `apps/ios/Pebbles/Features/Auth/AuthView.swift`
- Modify: `apps/ios/Pebbles/RootView.swift` (swap the placeholder for `AuthView()`)

- [ ] **Step 8.1: Create `AuthView.swift`**

Create `apps/ios/Pebbles/Features/Auth/AuthView.swift`:

```swift
import SwiftUI

/// Email + password auth screen. Segmented control toggles between Login and
/// Signup modes. Signup mode adds consent checkboxes (wired in Task 9).
///
/// State lives here (view-local) except for `authError`, which is read from
/// `SupabaseService` so errors from `signIn` / `signUp` surface inline.
struct AuthView: View {
    enum Mode: String, CaseIterable, Identifiable {
        case login = "Log In"
        case signup = "Sign Up"
        var id: String { rawValue }
    }

    @Environment(SupabaseService.self) private var supabase

    @State private var mode: Mode = .login
    @State private var email = ""
    @State private var password = ""
    @State private var isSubmitting = false

    var body: some View {
        VStack(spacing: 24) {
            Text("Pebbles")
                .font(.largeTitle.weight(.semibold))
                .padding(.top, 48)

            Picker("Mode", selection: $mode) {
                ForEach(Mode.allCases) { mode in
                    Text(mode.rawValue).tag(mode)
                }
            }
            .pickerStyle(.segmented)

            VStack(spacing: 12) {
                TextField("Email", text: $email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)

                SecureField("Password", text: $password)
                    .textContentType(mode == .login ? .password : .newPassword)
                    .textFieldStyle(.roundedBorder)
            }

            if let error = supabase.authError {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button {
                submit()
            } label: {
                Text("Continue")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!canSubmit)

            Spacer()
        }
        .padding(.horizontal, 24)
        .onChange(of: mode) { _, _ in
            supabase.authError = nil
        }
        .onChange(of: email) { _, _ in
            if supabase.authError != nil { supabase.authError = nil }
        }
        .onChange(of: password) { _, _ in
            if supabase.authError != nil { supabase.authError = nil }
        }
    }

    private var canSubmit: Bool {
        guard !isSubmitting,
              email.contains("@"),
              password.count >= 6
        else { return false }
        return true
    }

    private func submit() {
        Task {
            isSubmitting = true
            switch mode {
            case .login:
                await supabase.signIn(email: email, password: password)
            case .signup:
                await supabase.signUp(email: email, password: password)
            }
            isSubmitting = false
        }
    }
}

#Preview {
    AuthView()
        .environment(SupabaseService())
}
```

- [ ] **Step 8.2: Swap the placeholder in `RootView`**

Replace the `Text("Not signed in")` line in `apps/ios/Pebbles/RootView.swift` with `AuthView()`. The full file becomes:

```swift
import SwiftUI

/// Top-level auth gate. Reads `SupabaseService` from the environment and
/// decides whether to show the auth screen or the main tab bar.
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
        .task {
            await supabase.start()
        }
    }
}

#Preview {
    RootView()
        .environment(SupabaseService())
}
```

- [ ] **Step 8.3: Regenerate + build**

Run:
```bash
npm run generate --workspace=@pbbls/ios && npm run build --workspace=@pbbls/ios
```

Expected: both succeed.

- [ ] **Step 8.4: Manually test login + signup (end-to-end, no consent yet)**

Launch the simulator (⌘R). Expected:

1. Brief blank screen → `AuthView` in Login mode.
2. Empty email/password → Continue is disabled.
3. Type any text in email (no `@`) → still disabled.
4. Type `foo@bar.com` + `12345` (5 chars) → still disabled (password too short).
5. Bump password to `123456` → Continue enables.
6. Toggle to Sign Up → fields preserved, button still enabled.
7. Tap Continue in Sign Up mode with a **new** email → app swaps to `MainTabView` (Path tab). Signup succeeds because email confirmation is disabled in the dashboard.
8. From Path tab, open Profile tab → placeholder "Profile" text is visible. You cannot log out yet (Task 10).

If signup lands on `MainTabView`, the full auth gate + service + stream wiring is correct.

If signup shows an inline error instead (e.g. "User already registered"), toggle to Log In, tap Continue → should land on `MainTabView`.

If nothing happens at all (Continue enabled but no state change after tap), check the Xcode console for `signUp failed:` or `signIn failed:` log lines.

- [ ] **Step 8.5: Commit**

```bash
git add apps/ios/Pebbles/Features/Auth/AuthView.swift apps/ios/Pebbles/RootView.swift
git commit -m "feat(ios): add AuthView with email login/signup form"
```

---

## Task 9 — Wire consent checkboxes + legal sheet into `AuthView`

Adds the two `ConsentCheckbox` rows (only visible in Signup mode), gates the Continue button on both being checked, and wires the legal sheet presentation.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Auth/AuthView.swift`

- [ ] **Step 9.1: Add consent state and sheet wiring**

Inside `AuthView`, add these new `@State` properties next to the existing ones:

```swift
    @State private var termsAccepted = false
    @State private var privacyAccepted = false
    @State private var presentedLegalDoc: LegalDoc?
```

- [ ] **Step 9.2: Insert the two checkboxes into the body**

Between the `VStack` containing the text fields and the `if let error` block, insert:

```swift
            if mode == .signup {
                VStack(alignment: .leading, spacing: 12) {
                    ConsentCheckbox(
                        isChecked: $termsAccepted,
                        prefix: "I accept the ",
                        linkText: "Terms of Service",
                        onLinkTap: { presentedLegalDoc = .terms }
                    )
                    ConsentCheckbox(
                        isChecked: $privacyAccepted,
                        prefix: "I accept the ",
                        linkText: "Privacy Policy",
                        onLinkTap: { presentedLegalDoc = .privacy }
                    )
                }
            }
```

- [ ] **Step 9.3: Update `canSubmit` to require consent in Signup mode**

Replace the existing `canSubmit` computed property with:

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

- [ ] **Step 9.4: Reset consent when leaving Signup mode**

Replace the existing `.onChange(of: mode)` block with:

```swift
        .onChange(of: mode) { _, newMode in
            supabase.authError = nil
            if newMode == .login {
                termsAccepted = false
                privacyAccepted = false
            }
        }
```

- [ ] **Step 9.5: Attach the legal document sheet**

Add this modifier on the outer `VStack`, right after `.padding(.horizontal, 24)`:

```swift
        .sheet(item: $presentedLegalDoc) { doc in
            LegalDocumentSheet(url: doc.url)
                .ignoresSafeArea()
        }
```

- [ ] **Step 9.6: Build**

Run:
```bash
npm run build --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 9.7: Manually verify consent flow**

Launch the simulator (⌘R). You may need to delete the app first to force back to `AuthView` (since Task 8 left you signed in, and Task 10 hasn't added logout yet). Long-press the Pebbles app in the simulator → Delete App.

Expected:
1. Launch → `AuthView` in Log In mode. No checkboxes visible.
2. Switch to Sign Up → both checkboxes appear, unchecked. Continue is disabled even with valid email + password.
3. Tap the Terms of Service label text (not just "Terms of Service" — anywhere on that row's label) → Safari sheet opens to `https://www.pbbls.app/docs/terms`. Tap "Done" → back to signup form with fields preserved.
4. Tap the Privacy Policy label → Safari sheet opens to `/docs/privacy`. Dismiss.
5. Check both boxes → Continue enables.
6. Toggle to Log In → checkboxes disappear, Continue state reverts to "login rules" (enabled if email + password are valid).
7. Toggle back to Sign Up → **checkboxes are unchecked** (reset rule from Step 9.4).

- [ ] **Step 9.8: Commit**

```bash
git add apps/ios/Pebbles/Features/Auth/AuthView.swift
git commit -m "feat(ios): add consent checkboxes and legal sheet to AuthView"
```

---

## Task 10 — Logout button in `ProfileView`

Adds a single "Log out" button to `ProfileView` that calls `supabase.signOut()`. This unblocks end-to-end manual testing (sign in → sign out → sign in).

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`

- [ ] **Step 10.1: Update `ProfileView`**

Replace the contents of `apps/ios/Pebbles/Features/Profile/ProfileView.swift` with:

```swift
import SwiftUI

struct ProfileView: View {
    @Environment(SupabaseService.self) private var supabase

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Text("Profile")
                    .foregroundStyle(.secondary)

                Spacer()

                Button(role: .destructive) {
                    Task {
                        await supabase.signOut()
                    }
                } label: {
                    Text("Log out")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle("Profile")
        }
    }
}

#Preview {
    ProfileView()
        .environment(SupabaseService())
}
```

- [ ] **Step 10.2: Build**

Run:
```bash
npm run build --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 10.3: Manually verify logout round-trip**

Launch the simulator (⌘R).

1. If you're still signed in from Task 9: Profile tab → "Log out" button at the bottom → tap it → app swaps to `AuthView`.
2. If you were signed out: Sign Up with a new email and both consent checkboxes → land on `MainTabView` → Profile tab → Log out → `AuthView`.
3. Sign back in with the same credentials → `MainTabView`.
4. Force-quit the app (⌘⇧H twice in simulator, swipe up on Pebbles) and relaunch → briefly blank → `MainTabView` directly (session persisted in keychain).

If any step fails (especially step 4 — session persistence), something is wrong in the `start()` subscription or the way the SDK reads the stored session. The keychain storage is default-on in supabase-swift v2; if it's not persisting, verify `Pebbles.entitlements` still has Sign in with Apple declared (the keychain sharing group comes in automatically with that entitlement).

- [ ] **Step 10.4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/ProfileView.swift
git commit -m "feat(ios): add logout button to ProfileView"
```

---

## Task 11 — Full verification + SwiftLint + test run

Final pass: full test suite, SwiftLint, and the complete manual test plan from the spec.

- [ ] **Step 11.1: Run the full test suite**

Run:
```bash
npm run test --workspace=@pbbls/ios
```

Expected: `** TEST SUCCEEDED **`. Both `PebblesSmokeTests` and `SupabaseServiceTests` pass.

- [ ] **Step 11.2: Run SwiftLint**

Run:
```bash
npm run lint --workspace=@pbbls/ios
```

Expected: 0 errors. Warnings on the pre-existing `AppEnvironment.swift` line-length rules are acceptable (documented in PR #242). New warnings on any file touched in this PR should be fixed — if they're line-length violations, reformat; if they're something else, evaluate case-by-case.

- [ ] **Step 11.3: Full manual test plan**

Launch the simulator. Delete the Pebbles app first for a clean state (long-press → Delete App).

Walk through every step from the spec's "Manual test plan":

1. Fresh install → launch → `AuthView` in Login mode.
2. Toggle to Signup. Valid email + valid password. Continue disabled (both checkboxes unchecked).
3. Check both boxes → Continue enables. Tap → land on `MainTabView`.
4. Profile tab → Log out → `AuthView` in Login mode.
5. Re-enter same credentials → Continue → `MainTabView`.
6. Force-quit, relaunch → brief blank → `MainTabView` without re-auth.
7. Log out → enter wrong password → inline red error appears.
8. Switch to Signup → tap "Terms of Service" → Safari sheet opens `https://www.pbbls.app/docs/terms` → Done.
9. Tap "Privacy Policy" → Safari sheet opens `https://www.pbbls.app/docs/privacy` → Done.
10. Form state preserved after dismissing each sheet.

All ten must pass. Record any failures and STOP — do not create the PR until they're resolved.

- [ ] **Step 11.4: Clean final commit check**

Run:
```bash
git status && git log --oneline feat/201-ios-auth-flow ^main
```

Expected: working tree clean. The log should show approximately 10 commits (one per task that modified code, plus the spec commit). Each commit message starts with `feat(ios):` or `refactor(ios):` or `docs(ios):`. No WIP, fixup, or "oops" commits.

If you see messy history, now is the time to clean it up with `git rebase -i main` — but only if you're comfortable. Squashing is not required; ten focused commits is better than one giant one.

- [ ] **Step 11.5: Push the branch**

Run:
```bash
git push -u origin feat/201-ios-auth-flow
```

Expected: branch is pushed and tracking is set.

- [ ] **Step 11.6: Open PR**

Create the PR with:
- **Title:** `feat(ios): email signup and login flow with consent`
- **Body must start with:** `Resolves #201 (email path)` — since Apple Sign-In is split out, also open a new follow-up issue and note its number in the PR body.
- **Labels:** `feat`, `auth`. The project label set is `core | ui | db | api | auth | facility` — there is no `ios` scope label today. `auth` is the closest match. Ask the user to confirm the exact label set before finalizing — per project CLAUDE.md, PRs inherit labels from the issue they resolve, and #201 may not perfectly match since we split scope.
- **Milestone:** M17 · iOS project bootstrap (inherit from #201).
- **Body content:** Summary (3 bullets), key files changed, the full manual test plan from Step 11.3 as a checklist, and links to both the spec and this plan.

Before running `gh pr create`, re-read the project CLAUDE.md PR Workflow Checklist to make sure nothing's missed (branch name format, PR title format, labels, milestone).

---

## What to watch for during execution

- **`authStateChanges` tuple shape mismatch**: if supabase-swift's API differs from the code blocks, Task 2 is where it surfaces as a compile error. Read the SDK's `AuthClient.swift` to learn the real shape. Do not guess.
- **`signUp` data parameter type**: `AnyJSON` vs `AnyValue` vs `JSONValue`. Same remedy — read the SDK source.
- **`@Previewable` macro**: if the preview in Task 7 fails to compile, replace it with a manual wrapper view. The previews are nice-to-have and not on the critical path.
- **Keychain persistence**: if sessions don't survive relaunch (Task 10, Step 10.3), investigate before continuing. This is the one hidden dependency on iOS config (keychain access groups). Do not patch by forcing a manual session reload.
- **SFSafariViewController sheet height**: on iOS 17 simulators the sheet sometimes opens at half-height. Tap and drag up if needed. This is cosmetic; don't fix unless the real device reproduces it too.
