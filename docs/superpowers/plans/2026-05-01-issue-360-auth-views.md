# iOS Auth Views Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring iOS login and signup views to pixel-perfect parity with mockups in issue #360, factoring shared primitives into `Pebbles/Components/` so both Welcome and Auth flows consume them.

**Architecture:** Six new presentation components plus one ButtonStyle, all driven from `Pebbles/Components/` subfolders organized by primitive. `AuthView` rewrites its body to compose them; `WelcomeView` swaps three inline views for the shared components. `UISegmentedControl.appearance()` is configured once at app launch so the system segmented Picker picks up the design tokens. The only logic change is extracting the existing `canSubmit` predicate into a testable static method.

**Tech Stack:** SwiftUI (iOS 17+), Swift Testing, xcodegen, UIKit appearance proxy for `UISegmentedControl`. Spec: `docs/superpowers/specs/2026-05-01-issue-360-auth-views-design.md`. Branch: `feat/360-improve-login-signup-views`.

---

## Notes on conventions used throughout this plan

- All sizes in components use `@ScaledMetric(relativeTo: .body)` so they scale with Dynamic Type. The default values match the mockups at default Dynamic Type.
- New files live under `apps/ios/Pebbles/Components/<subfolder>/`. The xcodegen target globs `Pebbles/**/*.swift` (verified: `apps/ios/project.yml` line 43–44 has `sources: - path: Pebbles`), so no `project.yml` edits are required. After adding new files, run `npm run generate --workspace=@pbbls/ios` (which runs `xcodegen generate`) so any out-of-Xcode tooling (CI, fresh checkouts) picks them up.
- Swift Testing only (no XCTest). New tests go in `apps/ios/PebblesTests/` using `import Testing`, `@Suite`, `@Test`, `#expect`.
- All commits run from the repo root: `/Users/alexis/code/pbbls`. Use conventional commits per `CLAUDE.md`: `feat(ios)`, `refactor(ios)`, `test(ios)`, etc.

---

### Task 1: Add `PebblesPrimaryButtonStyle`

**Files:**
- Create: `apps/ios/Pebbles/Components/Buttons/PebblesPrimaryButtonStyle.swift`

- [ ] **Step 1: Create the file with the ButtonStyle**

```swift
// apps/ios/Pebbles/Components/Buttons/PebblesPrimaryButtonStyle.swift
import SwiftUI

/// Pill-shaped, full-width primary button. Reads `\.isEnabled` from the
/// environment to switch between the accent-filled enabled state and the
/// muted-filled disabled state. Pass `isLoading: true` while a Task is in
/// flight to swap the label for a `ProgressView`; callers should also apply
/// `.disabled(true)` so the press isn't re-fired.
struct PebblesPrimaryButtonStyle: ButtonStyle {
    var isLoading: Bool = false

    @Environment(\.isEnabled) private var isEnabled
    @ScaledMetric(relativeTo: .body) private var minHeight: CGFloat = 52

    func makeBody(configuration: Configuration) -> some View {
        ZStack {
            if isLoading {
                ProgressView().tint(.white)
            } else {
                configuration.label
                    .fontWeight(.medium)
                    .foregroundStyle(isEnabled ? Color.white : Color.pebblesBorder)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(minHeight: minHeight)
        .background(
            Capsule().fill(isEnabled ? Color.pebblesAccent : Color.pebblesMuted)
        )
        .opacity(configuration.isPressed ? 0.85 : 1.0)
        .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

#Preview("Enabled") {
    Button("Connect") {}
        .buttonStyle(PebblesPrimaryButtonStyle())
        .padding()
}

#Preview("Disabled") {
    Button("Connect") {}
        .buttonStyle(PebblesPrimaryButtonStyle())
        .disabled(true)
        .padding()
}

#Preview("Loading") {
    Button("Connect") {}
        .buttonStyle(PebblesPrimaryButtonStyle(isLoading: true))
        .disabled(true)
        .padding()
}
```

- [ ] **Step 2: Regenerate the Xcode project**

Run: `npm run generate --workspace=@pbbls/ios`
Expected: command exits 0; `apps/ios/Pebbles.xcodeproj/project.pbxproj` regenerated to include the new file.

- [ ] **Step 3: Build to confirm it compiles**

Run: `xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build`
Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Components/Buttons/PebblesPrimaryButtonStyle.swift
git commit -m "feat(ios): add PebblesPrimaryButtonStyle"
```

---

### Task 2: Add `PebblesTextInput`

**Files:**
- Create: `apps/ios/Pebbles/Components/Inputs/PebblesTextInput.swift`

- [ ] **Step 1: Create the file**

```swift
// apps/ios/Pebbles/Components/Inputs/PebblesTextInput.swift
import SwiftUI
import UIKit

/// Rounded-rectangle text input with a 1pt border. White fill, muted-foreground
/// for both placeholder and typed content, per the design spec. SwiftUI's
/// default placeholder styling uses `.secondary` and isn't directly recolorable
/// on iOS 17, so we render a custom overlay that disappears once `text` is non-empty.
struct PebblesTextInput: View {
    let placeholder: LocalizedStringResource
    @Binding var text: String
    var isSecure: Bool = false
    var contentType: UITextContentType? = nil
    var keyboard: UIKeyboardType = .default
    var autocapitalization: TextInputAutocapitalization = .sentences
    var autocorrection: Bool = true

    @ScaledMetric(relativeTo: .body) private var minHeight: CGFloat = 52
    @ScaledMetric(relativeTo: .body) private var horizontalPadding: CGFloat = 16
    @ScaledMetric(relativeTo: .body) private var cornerRadius: CGFloat = 12

    var body: some View {
        ZStack(alignment: .leading) {
            if text.isEmpty {
                Text(placeholder)
                    .foregroundStyle(Color.pebblesMutedForeground)
                    .padding(.horizontal, horizontalPadding)
                    .allowsHitTesting(false)
            }

            field
                .font(.body)
                .foregroundStyle(Color.pebblesMutedForeground)
                .tint(Color.pebblesAccent)
                .textContentType(contentType)
                .keyboardType(keyboard)
                .textInputAutocapitalization(autocapitalization)
                .autocorrectionDisabled(!autocorrection)
                .padding(.horizontal, horizontalPadding)
        }
        .frame(minHeight: minHeight)
        .background(
            RoundedRectangle(cornerRadius: cornerRadius).fill(Color.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(Color.pebblesBorder, lineWidth: 1)
        )
    }

    @ViewBuilder
    private var field: some View {
        if isSecure {
            SecureField("", text: $text)
        } else {
            TextField("", text: $text)
        }
    }
}

#Preview("Empty") {
    @Previewable @State var text = ""
    return PebblesTextInput(placeholder: "Email", text: $text)
        .padding()
        .background(Color.pebblesBackground)
}

#Preview("Filled") {
    @Previewable @State var text = "hello@bohns.design"
    return PebblesTextInput(placeholder: "Email", text: $text)
        .padding()
        .background(Color.pebblesBackground)
}

#Preview("Secure") {
    @Previewable @State var text = "hunter22"
    return PebblesTextInput(placeholder: "Password", text: $text, isSecure: true)
        .padding()
        .background(Color.pebblesBackground)
}
```

- [ ] **Step 2: Regenerate and build**

Run: `npm run generate --workspace=@pbbls/ios && xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build`
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Components/Inputs/PebblesTextInput.swift
git commit -m "feat(ios): add PebblesTextInput component"
```

---

### Task 3: Add `PebblesCheckbox`

**Files:**
- Create: `apps/ios/Pebbles/Components/Checkboxes/PebblesCheckbox.swift`

- [ ] **Step 1: Create the file**

```swift
// apps/ios/Pebbles/Components/Checkboxes/PebblesCheckbox.swift
import SwiftUI

/// Consent checkbox: a 28pt rounded square (white when empty, accent when
/// checked) followed by a label that contains a tappable link fragment.
/// Tap area on the box is at least 44×44pt regardless of the visual size,
/// per HIG. The label tap fires `onLinkTap` (whole-label, since per-range
/// gestures don't compose cleanly on `Text` concatenation).
struct PebblesCheckbox: View {
    @Binding var isChecked: Bool
    let prefix: LocalizedStringResource
    let linkText: LocalizedStringResource
    let onLinkTap: () -> Void

    @ScaledMetric(relativeTo: .body) private var boxSize: CGFloat = 28
    @ScaledMetric(relativeTo: .body) private var cornerRadius: CGFloat = 12

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            box
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
                .onTapGesture { isChecked.toggle() }

            label
                .onTapGesture { onLinkTap() }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityAddTraits(isChecked ? .isSelected : [])
        .accessibilityAction(named: Text(linkText), onLinkTap)
    }

    private var box: some View {
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius)
                .fill(isChecked ? Color.pebblesAccent : Color.white)
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(isChecked ? Color.pebblesAccent : Color.pebblesBorder, lineWidth: 1)

            Image(systemName: isChecked ? "checkmark.square" : "square")
                .font(.body)
                .foregroundStyle(isChecked ? Color.pebblesBackground : Color.pebblesMutedForeground)
        }
        .frame(width: boxSize, height: boxSize)
    }

    private var label: some View {
        (Text(prefix) + Text(linkText).underline().foregroundColor(Color.pebblesAccent))
            .font(.subheadline)
            .foregroundStyle(Color.pebblesMutedForeground)
    }
}

#Preview("Unchecked") {
    @Previewable @State var checked = false
    return PebblesCheckbox(
        isChecked: $checked,
        prefix: "I accept the ",
        linkText: "Terms of Service",
        onLinkTap: {}
    )
    .padding()
    .background(Color.pebblesBackground)
}

#Preview("Checked") {
    @Previewable @State var checked = true
    return PebblesCheckbox(
        isChecked: $checked,
        prefix: "I accept the ",
        linkText: "Privacy Policy",
        onLinkTap: {}
    )
    .padding()
    .background(Color.pebblesBackground)
}
```

- [ ] **Step 2: Regenerate and build**

Run: `npm run generate --workspace=@pbbls/ios && xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build`
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Components/Checkboxes/PebblesCheckbox.swift
git commit -m "feat(ios): add PebblesCheckbox component"
```

---

### Task 4: Add `AppleSignInButton`

**Files:**
- Create: `apps/ios/Pebbles/Components/Buttons/AppleSignInButton.swift`

- [ ] **Step 1: Create the file**

```swift
// apps/ios/Pebbles/Components/Buttons/AppleSignInButton.swift
import SwiftUI

/// Black capsule button with the Apple logo and "Continue with Apple" label.
/// Fixed visual treatment in light AND dark mode per Apple's brand
/// requirements. The action is the only consumer-supplied piece.
struct AppleSignInButton: View {
    var action: () -> Void

    @ScaledMetric(relativeTo: .body) private var minHeight: CGFloat = 52

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: "applelogo").font(.body)
                Text("Continue with Apple").fontWeight(.medium)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(minHeight: minHeight)
        }
        .background(Capsule().fill(Color.black))
        .buttonStyle(.plain)
    }
}

#Preview {
    AppleSignInButton(action: {})
        .padding()
        .background(Color.pebblesBackground)
}
```

- [ ] **Step 2: Regenerate and build**

Run: `npm run generate --workspace=@pbbls/ios && xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build`
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Components/Buttons/AppleSignInButton.swift
git commit -m "feat(ios): add AppleSignInButton component"
```

---

### Task 5: Add `GoogleSignInButton`

**Files:**
- Create: `apps/ios/Pebbles/Components/Buttons/GoogleSignInButton.swift`

- [ ] **Step 1: Create the file**

```swift
// apps/ios/Pebbles/Components/Buttons/GoogleSignInButton.swift
import SwiftUI

/// White capsule button with the multi-color G mark and "Continue with Google"
/// label. 1pt border in `pebblesBorder` so it reads against the page background.
struct GoogleSignInButton: View {
    var action: () -> Void

    @ScaledMetric(relativeTo: .body) private var minHeight: CGFloat = 52
    @ScaledMetric(relativeTo: .body) private var glyphSize: CGFloat = 18

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image("GoogleGMark")
                    .resizable()
                    .scaledToFit()
                    .frame(width: glyphSize, height: glyphSize)
                Text("Continue with Google").fontWeight(.medium)
            }
            .foregroundStyle(Color.pebblesForeground)
            .frame(maxWidth: .infinity)
            .frame(minHeight: minHeight)
        }
        .background(Capsule().fill(Color.white))
        .overlay(Capsule().stroke(Color.pebblesBorder, lineWidth: 1))
        .buttonStyle(.plain)
    }
}

#Preview {
    GoogleSignInButton(action: {})
        .padding()
        .background(Color.pebblesBackground)
}
```

- [ ] **Step 2: Regenerate and build**

Run: `npm run generate --workspace=@pbbls/ios && xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build`
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Components/Buttons/GoogleSignInButton.swift
git commit -m "feat(ios): add GoogleSignInButton component"
```

---

### Task 6: Add `LegalDisclaimerText`

**Files:**
- Create: `apps/ios/Pebbles/Components/Auth/LegalDisclaimerText.swift`

- [ ] **Step 1: Create the file**

```swift
// apps/ios/Pebbles/Components/Auth/LegalDisclaimerText.swift
import SwiftUI

/// "Read our Terms and Privacy before creating an account…" disclaimer with
/// two tappable inline links. Lifted from the welcome screen so both Welcome
/// and Auth flows present the same legal copy.
struct LegalDisclaimerText: View {
    var onTermsTap: () -> Void
    var onPrivacyTap: () -> Void

    var body: some View {
        Text("Read our [Terms](pebbles://legal/terms) and [Privacy](pebbles://legal/privacy) before creating an account with Apple or Google.")
            .font(.caption)
            .foregroundStyle(Color.pebblesMutedForeground)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .tint(Color.pebblesAccent)
            .environment(\.openURL, OpenURLAction { url in
                switch url.absoluteString {
                case "pebbles://legal/terms":   onTermsTap()
                case "pebbles://legal/privacy": onPrivacyTap()
                default: return .systemAction
                }
                return .handled
            })
    }
}

#Preview {
    LegalDisclaimerText(onTermsTap: {}, onPrivacyTap: {})
        .padding()
        .background(Color.pebblesBackground)
}
```

- [ ] **Step 2: Regenerate and build**

Run: `npm run generate --workspace=@pbbls/ios && xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build`
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Components/Auth/LegalDisclaimerText.swift
git commit -m "feat(ios): add LegalDisclaimerText component"
```

---

### Task 7: Add `PebblesAuthSwitcher`

**Files:**
- Create: `apps/ios/Pebbles/Components/Auth/PebblesAuthSwitcher.swift`

- [ ] **Step 1: Create the file**

```swift
// apps/ios/Pebbles/Components/Auth/PebblesAuthSwitcher.swift
import SwiftUI

/// Login/Sign up segmented switcher. Renders a SwiftUI `Picker(.segmented)`
/// at full content width. Colors are configured globally on
/// `UISegmentedControl.appearance()` at app launch — see `PebblesApp.init`.
struct PebblesAuthSwitcher: View {
    @Binding var mode: AuthView.Mode

    var body: some View {
        Picker("Mode", selection: $mode) {
            ForEach(AuthView.Mode.allCases) { mode in
                Text(mode.label).tag(mode)
            }
        }
        .pickerStyle(.segmented)
    }
}

#Preview {
    @Previewable @State var mode: AuthView.Mode = .login
    return PebblesAuthSwitcher(mode: $mode)
        .padding()
        .background(Color.pebblesBackground)
}
```

- [ ] **Step 2: Regenerate and build**

Run: `npm run generate --workspace=@pbbls/ios && xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build`
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Components/Auth/PebblesAuthSwitcher.swift
git commit -m "feat(ios): add PebblesAuthSwitcher component"
```

---

### Task 8: Configure `UISegmentedControl.appearance()` at app launch

**Files:**
- Modify: `apps/ios/Pebbles/PebblesApp.swift`

- [ ] **Step 1: Replace the file contents**

```swift
// apps/ios/Pebbles/PebblesApp.swift
import SwiftUI
import UIKit

@main
struct PebblesApp: App {
    @State private var supabase = SupabaseService()

    init() {
        Self.configureSegmentedControlAppearance()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(supabase)
        }
    }

    /// Restyles the system segmented control to match the Pebbles design tokens.
    /// Applied globally because the app currently has only one segmented Picker
    /// (`PebblesAuthSwitcher`); if a second variant is added later, scope this
    /// via `appearance(whenContainedInInstancesOf:)`.
    private static func configureSegmentedControlAppearance() {
        let muted = UIColor(named: "Muted") ?? .systemGray5
        let mutedForeground = UIColor(named: "MutedForeground") ?? .systemGray

        let proxy = UISegmentedControl.appearance()
        proxy.backgroundColor = muted
        proxy.selectedSegmentTintColor = mutedForeground

        proxy.setTitleTextAttributes([
            .foregroundColor: UIColor.white,
            .font: UIFont.systemFont(ofSize: UIFont.systemFontSize, weight: .medium)
        ], for: .selected)

        proxy.setTitleTextAttributes([
            .foregroundColor: mutedForeground,
            .font: UIFont.systemFont(ofSize: UIFont.systemFontSize, weight: .regular)
        ], for: .normal)
    }
}
```

- [ ] **Step 2: Build**

Run: `xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build`
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/PebblesApp.swift
git commit -m "feat(ios): configure UISegmentedControl appearance at launch"
```

---

### Task 9: Extract `canSubmit` to a static method and add unit tests (TDD)

**Files:**
- Create: `apps/ios/PebblesTests/Features/Auth/AuthViewLogicTests.swift`
- Modify: `apps/ios/Pebbles/Features/Auth/AuthView.swift` (only the `canSubmit` predicate, body unchanged in this task)

- [ ] **Step 1: Write the failing test file**

```swift
// apps/ios/PebblesTests/Features/Auth/AuthViewLogicTests.swift
import Testing
@testable import Pebbles

@Suite("AuthView.canSubmit")
struct AuthViewLogicTests {
    @Test("login: invalid email returns false")
    func loginInvalidEmail() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "not-an-email", password: "password",
            termsAccepted: false, privacyAccepted: false, isSubmitting: false
        ) == false)
    }

    @Test("login: short password returns false")
    func loginShortPassword() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "hello@bohns.design", password: "abc",
            termsAccepted: false, privacyAccepted: false, isSubmitting: false
        ) == false)
    }

    @Test("login: valid credentials return true")
    func loginValid() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: false, privacyAccepted: false, isSubmitting: false
        ) == true)
    }

    @Test("login: ignores consent flags")
    func loginIgnoresConsent() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: true, privacyAccepted: true, isSubmitting: false
        ) == true)
    }

    @Test("signup: missing terms returns false")
    func signupMissingTerms() {
        #expect(AuthView.canSubmit(
            mode: .signup, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: false, privacyAccepted: true, isSubmitting: false
        ) == false)
    }

    @Test("signup: missing privacy returns false")
    func signupMissingPrivacy() {
        #expect(AuthView.canSubmit(
            mode: .signup, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: true, privacyAccepted: false, isSubmitting: false
        ) == false)
    }

    @Test("signup: all four conditions met returns true")
    func signupAllMet() {
        #expect(AuthView.canSubmit(
            mode: .signup, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: true, privacyAccepted: true, isSubmitting: false
        ) == true)
    }

    @Test("isSubmitting=true returns false in login")
    func loginSubmitting() {
        #expect(AuthView.canSubmit(
            mode: .login, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: false, privacyAccepted: false, isSubmitting: true
        ) == false)
    }

    @Test("isSubmitting=true returns false in signup")
    func signupSubmitting() {
        #expect(AuthView.canSubmit(
            mode: .signup, email: "hello@bohns.design", password: "abcdef",
            termsAccepted: true, privacyAccepted: true, isSubmitting: true
        ) == false)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run generate --workspace=@pbbls/ios && xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -quiet test -only-testing:PebblesTests/AuthViewLogicTests`
Expected: BUILD FAILED — `static method 'canSubmit' is not a member type of 'AuthView'` (or similar).

- [ ] **Step 3: Refactor `AuthView` to expose `canSubmit` as a static method**

Open `apps/ios/Pebbles/Features/Auth/AuthView.swift`. Replace the existing `private var canSubmit: Bool { ... }` block (currently lines 121–131) with:

```swift
    private var canSubmit: Bool {
        Self.canSubmit(
            mode: mode,
            email: email,
            password: password,
            termsAccepted: termsAccepted,
            privacyAccepted: privacyAccepted,
            isSubmitting: isSubmitting
        )
    }

    static func canSubmit(
        mode: Mode,
        email: String,
        password: String,
        termsAccepted: Bool,
        privacyAccepted: Bool,
        isSubmitting: Bool
    ) -> Bool {
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

The body of `AuthView` and everything else in the file stay unchanged in this task.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -quiet test -only-testing:PebblesTests/AuthViewLogicTests`
Expected: TEST SUCCEEDED, 9 tests pass.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/PebblesTests/Features/Auth/AuthViewLogicTests.swift apps/ios/Pebbles/Features/Auth/AuthView.swift
git commit -m "test(ios): extract AuthView.canSubmit and cover with unit tests"
```

---

### Task 10: Rewrite `AuthView.body` to compose the new components

**Files:**
- Modify: `apps/ios/Pebbles/Features/Auth/AuthView.swift` (full body rewrite; state, predicates, lifecycle hooks unchanged)

- [ ] **Step 1: Replace the file**

```swift
// apps/ios/Pebbles/Features/Auth/AuthView.swift
import SwiftUI

/// Email + password auth screen. Switcher toggles between Login and Signup
/// modes. Signup mode adds two consent checkboxes. State lives view-locally
/// except for `authError`, which is read from `SupabaseService` so failures
/// from `signIn` / `signUp` surface inline.
struct AuthView: View {
    enum Mode: String, CaseIterable, Identifiable {
        case login
        case signup
        var id: String { rawValue }

        var label: LocalizedStringResource {
            switch self {
            case .login:  return "Log In"
            case .signup: return "Sign Up"
            }
        }
    }

    @Environment(SupabaseService.self) private var supabase

    @State private var mode: Mode
    @State private var email = ""
    @State private var password = ""
    @State private var isSubmitting = false
    @State private var termsAccepted = false
    @State private var privacyAccepted = false
    @State private var presentedLegalDoc: LegalDoc?

    init(initialMode: Mode = .login) {
        _mode = State(initialValue: initialMode)
    }

    var body: some View {
        VStack(spacing: 24) {
            PebblesAuthSwitcher(mode: $mode)
                .padding(.top, 24)

            VStack(spacing: 12) {
                PebblesTextInput(
                    placeholder: "Email",
                    text: $email,
                    contentType: .emailAddress,
                    keyboard: .emailAddress,
                    autocapitalization: .never,
                    autocorrection: false
                )

                PebblesTextInput(
                    placeholder: "Password",
                    text: $password,
                    isSecure: true,
                    contentType: mode == .login ? .password : .newPassword,
                    autocapitalization: .never,
                    autocorrection: false
                )
            }

            if mode == .signup {
                VStack(alignment: .leading, spacing: 12) {
                    PebblesCheckbox(
                        isChecked: $termsAccepted,
                        prefix: "I accept the ",
                        linkText: "Terms of Service",
                        onLinkTap: { presentedLegalDoc = .terms }
                    )
                    PebblesCheckbox(
                        isChecked: $privacyAccepted,
                        prefix: "I accept the ",
                        linkText: "Privacy Policy",
                        onLinkTap: { presentedLegalDoc = .privacy }
                    )
                }
            }

            if let error = supabase.authError {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button(action: submit) {
                Text(mode == .login ? "Connect" : "Create an account")
            }
            .buttonStyle(PebblesPrimaryButtonStyle(isLoading: isSubmitting))
            .disabled(!canSubmit)

            Spacer()

            VStack(spacing: 12) {
                AppleSignInButton(action: { Task { await runApple() } })
                    .disabled(isSubmitting)

                GoogleSignInButton(action: { Task { await runGoogle() } })
                    .disabled(isSubmitting)

                LegalDisclaimerText(
                    onTermsTap: { presentedLegalDoc = .terms },
                    onPrivacyTap: { presentedLegalDoc = .privacy }
                )
                .padding(.top, 8)
            }
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 32)
        .sheet(item: $presentedLegalDoc) { doc in
            LegalDocumentSheet(url: doc.url)
                .ignoresSafeArea()
        }
        .onChange(of: mode) { _, newMode in
            supabase.authError = nil
            if newMode == .login {
                termsAccepted = false
                privacyAccepted = false
            }
        }
        .onChange(of: email) { _, _ in
            if supabase.authError != nil { supabase.authError = nil }
        }
        .onChange(of: password) { _, _ in
            if supabase.authError != nil { supabase.authError = nil }
        }
        .pebblesScreen()
    }

    private var canSubmit: Bool {
        Self.canSubmit(
            mode: mode,
            email: email,
            password: password,
            termsAccepted: termsAccepted,
            privacyAccepted: privacyAccepted,
            isSubmitting: isSubmitting
        )
    }

    static func canSubmit(
        mode: Mode,
        email: String,
        password: String,
        termsAccepted: Bool,
        privacyAccepted: Bool,
        isSubmitting: Bool
    ) -> Bool {
        guard !isSubmitting,
              email.contains("@"),
              password.count >= 6
        else { return false }

        if mode == .signup {
            return termsAccepted && privacyAccepted
        }
        return true
    }

    private func submit() {
        isSubmitting = true
        Task {
            switch mode {
            case .login:
                await supabase.signIn(email: email, password: password)
            case .signup:
                await supabase.signUp(email: email, password: password)
            }
            isSubmitting = false
        }
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

#Preview("Login") {
    AuthView(initialMode: .login)
        .environment(SupabaseService())
}

#Preview("Signup") {
    AuthView(initialMode: .signup)
        .environment(SupabaseService())
}
```

- [ ] **Step 2: Build and run the auth tests to confirm logic still passes**

Run: `xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -quiet test -only-testing:PebblesTests/AuthViewLogicTests`
Expected: TEST SUCCEEDED, 9 tests pass.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Auth/AuthView.swift
git commit -m "feat(ios): rebuild AuthView from shared components"
```

---

### Task 11: Update `WelcomeView` to use shared components

**Files:**
- Modify: `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift` (only the `revealedContent` body — replaces the inline Apple button, Google button, and disclaimer Text with the shared components)

- [ ] **Step 1: Open the file and replace `revealedContent`**

In `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift`, replace the entire `revealedContent` computed property (currently lines 107–216) with:

```swift
    private var revealedContent: some View {
        VStack(spacing: 0) {
            WelcomeCarousel(currentIndex: $currentIndex)
                .opacity(revealStep >= 2 ? 1 : 0)
                .padding(.bottom, 24)

            VStack(spacing: 12) {
                Button {
                    onCreateAccount()
                } label: {
                    Text("Create an account")
                        .fontWeight(.medium)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.capsule)
                .disabled(isSubmitting)
                .opacity(revealStep >= 3 ? 1 : 0)

                Button {
                    onLogin()
                } label: {
                    Text("Log in")
                        .fontWeight(.medium)
                        .foregroundStyle(Color.pebblesAccent)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .overlay(
                            Capsule()
                                .stroke(Color.pebblesAccent, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .disabled(isSubmitting)
                .opacity(revealStep >= 4 ? 1 : 0)

                AppleSignInButton(action: { Task { await runApple() } })
                    .disabled(isSubmitting)
                    .opacity(revealStep >= 5 ? 1 : 0)

                GoogleSignInButton(action: { Task { await runGoogle() } })
                    .disabled(isSubmitting)
                    .opacity(revealStep >= 6 ? 1 : 0)

                if let error = supabase.authError {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .padding(.top, 4)
                }

                LegalDisclaimerText(
                    onTermsTap: { presentedLegalDoc = .terms },
                    onPrivacyTap: { presentedLegalDoc = .privacy }
                )
                .padding(.top, 8)
                .opacity(revealStep >= 7 ? 1 : 0)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
    }
```

The two top buttons (`Create an account` and `Log in`) intentionally stay as-is — they're a different visual variant only used here. The reveal-step opacity gates and the auto-advance logic in the rest of `WelcomeView` are untouched.

- [ ] **Step 2: Build to confirm**

Run: `xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build`
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Welcome/WelcomeView.swift
git commit -m "refactor(ios): consume shared auth components in WelcomeView"
```

---

### Task 12: Delete the old `ConsentCheckbox`

**Files:**
- Delete: `apps/ios/Pebbles/Features/Auth/ConsentCheckbox.swift`

- [ ] **Step 1: Confirm no remaining references**

Run: `grep -rn "ConsentCheckbox" apps/ios/Pebbles apps/ios/PebblesTests || echo "clean"`
Expected: `clean` (no matches).

- [ ] **Step 2: Delete the file and regenerate the project**

```bash
rm apps/ios/Pebbles/Features/Auth/ConsentCheckbox.swift
npm run generate --workspace=@pbbls/ios
```

- [ ] **Step 3: Build to confirm**

Run: `xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build`
Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Commit**

```bash
git add -A apps/ios/Pebbles/Features/Auth/
git commit -m "chore(ios): remove ConsentCheckbox replaced by PebblesCheckbox"
```

---

### Task 13: Final verification

**Files:** none modified.

- [ ] **Step 1: Full build + tests**

Run: `xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -quiet test`
Expected: BUILD SUCCEEDED, all tests pass.

- [ ] **Step 2: Manual UI walkthrough on iPhone 15 simulator (390pt)**

Boot the app in Xcode. Run through every item in the spec's "Manual verification checklist":

  1. Welcome → `Log in` opens AuthView with switcher set to Login.
  2. Welcome → `Create an account` opens AuthView with switcher set to Sign up.
  3. Switching modes inside AuthView resets consent checkboxes when going back to Login.
  4. Connect button is disabled (muted bg, border-color label) until email contains `@` AND password ≥ 6 chars.
  5. Create-an-account button additionally requires both checkboxes ticked.
  6. Tapping a checkbox label opens the corresponding legal sheet; tapping the box toggles the check.
  7. Tapping Connect with valid credentials shows a spinner inside the button; on failure the inline red error renders; on success the tab bar appears.
  8. Tapping Apple / Google fires the correct OAuth path and disables the email submit during.
  9. Welcome screen reveals in the same order with the same animation cadence as before.
  10. Repeat 1–9 with Dynamic Type at XL (Settings → Display & Brightness → Text Size). Then at AX3 (Settings → Accessibility → Display & Text Size → Larger Text → On → drag to AX3). Verify nothing clips at XL; scrolling at AX3 is acceptable.
  11. Localization: switch device language to French. Switcher labels, input placeholders, button labels, and disclaimer all render in French. Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode and confirm no `New` or `Stale` rows.
  12. Light mode AND dark mode (Settings → Display & Brightness): confirm border, background, and accent tokens read correctly in both.

- [ ] **Step 3: Final lint pass**

Run: `npm run lint --workspace=@pbbls/ios`
Expected: command exits 0.

- [ ] **Step 4: Push the branch**

```bash
git push -u origin feat/360-improve-login-signup-views
```

The PR itself is opened by the user (or via the `superpowers:finishing-a-development-branch` skill) with title `feat(ios): improve login and signup views`, body starting with `Resolves #360`, labels `feat` + `ios` + `ui`, and milestone `M29 · Extended login and signup on iOS` per `CLAUDE.md`.

---

## Self-review

**Spec coverage check:**

- Architecture & file layout (Task 1–7, 12)
- `PebblesTextInput` contract (Task 2)
- `PebblesCheckbox` contract (Task 3)
- `PebblesPrimaryButtonStyle` contract (Task 1)
- `AppleSignInButton` / `GoogleSignInButton` contracts (Tasks 4, 5)
- `PebblesAuthSwitcher` contract + UISegmentedControl appearance (Tasks 7, 8)
- `LegalDisclaimerText` contract (Task 6)
- `AuthView` data flow + body rewrite (Tasks 9, 10)
- `WelcomeView` swap (Task 11)
- `canSubmit` extraction + unit tests (Task 9)
- Manual verification checklist (Task 13)
- Build verification (Tasks 1–13 build steps + Task 13)
- Accessibility / Dynamic Type (`@ScaledMetric` + 44pt tap target threaded through Tasks 1–7)

All four spec acceptance criteria flow from Tasks 9–11. No gaps.

**Placeholder scan:** no TBDs, no "implement later", no naked "add validation" lines. All code blocks contain runnable code.

**Type-name consistency:** `AuthView.Mode` referenced in Tasks 7, 9, 10 — same shape across all three. `canSubmit` signature is identical in Tasks 9 (definition) and the test file (call sites). `pebblesBackground`, `pebblesAccent`, `pebblesBorder`, `pebblesMuted`, `pebblesMutedForeground`, `pebblesForeground` color tokens consistent across all tasks. `PebblesPrimaryButtonStyle(isLoading:)` parameter name matches between Tasks 1 (definition) and 10 (consumption).
