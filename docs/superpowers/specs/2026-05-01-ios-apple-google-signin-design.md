# iOS Apple & Google Sign In — Design

**Issues:** Resolves #351 (Apple), Resolves #352 (Google)
**Milestone:** M29 · Extended login and signup on iOS
**Date:** 2026-05-01

## Context

The iOS app currently supports only email + password authentication (`AuthView` + `SupabaseService.signIn` / `signUp`). The web app already supports Apple, Google, and email auth. We want to bring iOS to parity by adding native "Continue with Apple" and "Continue with Google" buttons on the welcome screen.

The Sign in with Apple entitlement is already declared in `Pebbles.entitlements`; no UI consumes it yet.

## Goals

- Add native Apple Sign In to iOS (`ASAuthorizationController` + Supabase `signInWithIdToken`).
- Add native Google Sign In to iOS (GoogleSignIn-iOS SDK + Supabase `signInWithIdToken`).
- Place both buttons on `WelcomeView`, alongside the existing "Create an account" / "Log in" pair, with a passive consent disclosure beneath.
- Preserve cross-provider account linking by email when the existing email is confirmed (Supabase default behavior).
- Capture the user's full name when the provider returns it on first sign-in, so `profiles.display_name` is not stuck at `"Pebbler"`.

## Non-goals

- Changing the existing email + password flow (`AuthView` is unchanged).
- Adding analytics / event tracking (none exists yet).
- Automated UI tests (no test target is wired on iOS yet — per `apps/ios/CLAUDE.md`).
- Server-side merging of orphaned accounts (see "Accepted edge cases" below).

## Architecture

### iOS code structure

- **`Services/AppleSignIn.swift`** (new) — wraps `ASAuthorizationController`. Generates a cryptographically random raw nonce + SHA256-hashed nonce, kicks off `ASAuthorizationAppleIDRequest` with the hashed nonce in `request.nonce`, and returns the result of the system sheet:

  ```swift
  struct AppleSignInResult {
      let idToken: String
      let rawNonce: String
      let fullName: PersonNameComponents?  // non-nil only on first authorization
  }
  ```

- **`Services/GoogleSignIn.swift`** (new) — wraps the `GoogleSignIn-iOS` SPM dependency. Reads the iOS OAuth client ID from `AppEnvironment` (sourced from xcconfig). Returns:

  ```swift
  struct GoogleSignInResult {
      let idToken: String
      let accessToken: String
  }
  ```

- **`SupabaseService` — two new methods:**

  - `func signInWithApple() async` — invokes the Apple helper, then `client.auth.signInWithIdToken(credentials: .init(provider: .apple, idToken: idToken, nonce: rawNonce))`. On success, if the helper returned a `fullName` and the user's `profiles.display_name` is still the default `"Pebbler"`, updates the profile row with the formatted name.
  - `func signInWithGoogle() async` — invokes the Google helper, then `client.auth.signInWithIdToken(credentials: .init(provider: .google, idToken: idToken, accessToken: accessToken))`. On success, if `session.user.userMetadata["name"]` is set and `profiles.display_name` is still `"Pebbler"`, updates the profile row.

  Both methods set `authError = nil` at entry, log on failure via `os.Logger`, and surface failures via `authError` (same pattern as `signIn` / `signUp`). User cancellation of the system sheet is treated as silent (no error displayed).

- **`WelcomeView`** — adds two buttons + disclosure caption beneath the existing entry buttons. Local `isSubmitting` state disables both new buttons (and the existing "Create an account" / "Log in" buttons) while an OAuth call is in flight. Errors render via the existing `supabase.authError` pattern.

- **`AuthView`** — unchanged.

### Configuration / non-code work

- Add SPM dependency: `https://github.com/google/GoogleSignIn-iOS` (pinned major version) in `project.yml`. Run `xcodegen generate`.
- Add reversed Google iOS client ID to `Info.plist` as a registered URL scheme (so the OAuth callback returns to the app).
- Add `GOOGLE_IOS_CLIENT_ID` to xcconfig and surface it via `AppEnvironment` (alongside `supabaseURL` / `supabaseAnonKey`).
- Supabase dashboard:
  - **Apple provider** — add the iOS Bundle ID to "Authorized Client IDs". Web's existing Service ID stays.
  - **Google provider** — add the iOS OAuth client ID to "Authorized Client IDs". Web's existing OAuth client ID stays.
- Apple Developer portal: confirm Sign in with Apple capability is enabled on the App ID (entitlement is already declared, but capability registration is a separate dashboard step).

### No DB migrations

`handle_new_user` already handles OAuth users by reading `raw_user_meta_data->>'full_name'` and falling back to `'Pebbler'`. We patch the profile post-sign-in only when the trigger's fallback fired *and* the provider gave us a name to use. Web behavior is untouched.

## UX

### Welcome screen layout

```
[Create an account]   ← existing primary
[Log in]              ← existing secondary
[ Apple Continue ]    ← black background, Apple glyph, white label
[ Google Continue ]   ← white background, Google "G" mark, dark label
"Read our Terms and Privacy before creating an
 account with Apple or Google."
```

- **Apple button** uses `SignInWithAppleButton` from `AuthenticationServices`, style `.black`. Required by Apple HIG; gives localization, dynamic type, and dark-mode handling for free.
- **Google button** uses a custom shadcn-equivalent `Button` with the official Google "G" mark added to `Assets.xcassets` (light and dark variants). Styling matches the mockup.
- **Disclosure caption** is small caption text. The words `Terms` and `Privacy` are individually tappable and open the existing `LegalDocumentSheet`.

### Localization

New keys in `Pebbles/Resources/Localizable.xcstrings` (en + fr):

- `welcome.continue.apple` — "Continue with Apple"
- `welcome.continue.google` — "Continue with Google"
- `welcome.legal.disclosure` — "Read our %@ and %@ before creating an account with Apple or Google."
  (with `Terms` and `Privacy` as substituted links)

### Error handling

- User cancels the system sheet → silent. No error shown.
- Network / Supabase failure → existing `authError` red caption renders under the buttons.
- Provider misconfiguration (wrong client ID, missing capability) → `os.Logger.error` and a generic "Sign-in failed. Please try again." in `authError`.
- `signInWithIdToken` returning a linking-failure error (rare; e.g. the email already belongs to a confirmed account that for some reason cannot be linked) → surfaced via `authError`.

### Loading state

While an OAuth call is in flight, all four entry buttons disable. A small `ProgressView` replaces the tapped button's label. State lives in `WelcomeView` as `@State private var isSubmitting`.

## Account linking

Behavior is governed by Supabase, not by code we write. Documented for reference:

- **New email** → new `auth.users` row, identity attached to the corresponding provider.
- **Existing confirmed email** + new provider returning the same verified email → Supabase auto-links the new identity to the existing `auth.users` row. This is the "Google first, then Apple" case in the issue.
- **Existing unconfirmed email** + new provider → new `auth.users` row created. Old account orphaned. Accepted (rare; users either confirm immediately after signup or never).
- **Apple "Hide My Email"** → relay address used; behaves as a separate user. Apple requires offering this option, so we accept it.
- **Apple subsequent sign-in (no JWT email claim)** → handled internally by Supabase via the Apple `sub` claim. No special handling on our side.

## Auth state flow

`signInWithIdToken` succeeds → Supabase emits `.signedIn` on `authStateChanges` → existing `start()` loop in `SupabaseService` updates `session` → `RootView` swaps from `WelcomeView` to the tab bar. Same path as today's email flow.

## Display-name patching

The `handle_new_user` trigger seeds `profiles.display_name` from `raw_user_meta_data->>'full_name'`, falling back to `'Pebbler'`. To avoid stuck "Pebbler" names for OAuth users:

1. After `signInWithIdToken` succeeds, check whether the provider returned a name:
   - Apple: `AppleSignInResult.fullName` (only on first authorization for our app).
   - Google: `session.user.userMetadata["name"]` (the id_token's `name` claim).
2. If a name is available, query `profiles.display_name` for the user.
3. If it equals `'Pebbler'` (default unchanged), update the row. Otherwise leave it (the user has customized).

This logic is idempotent: re-runs are a no-op once the user has any non-default name.

## Sign-out

Unchanged. `client.auth.signOut()` clears the local session, the auth state stream emits `.signedOut`, `RootView` returns to `WelcomeView`. The Apple and Google SDKs do not need explicit sign-out calls — we are not maintaining provider-specific state separately from the Supabase session.

## Testing

- **Manual via TestFlight build:**
  - New email via Apple → new account, lands on onboarding.
  - New email via Google → new account, lands on onboarding.
  - Sign in with Apple using an email that already has a confirmed Google identity → same `auth.users` row, lands on `/path` (existing data preserved).
  - Sign in with Apple using "Hide My Email" → new account (documented behavior).
  - Cancel the system sheet → no error, returns to welcome screen.
  - Network offline during sign-in → error surfaces under buttons.
- **Automated:** none. The iOS app has no test target wired (per `apps/ios/CLAUDE.md`).

## Files touched

- `apps/ios/project.yml` — add GoogleSignIn-iOS SPM dependency.
- `apps/ios/Pebbles/Services/AppleSignIn.swift` — new.
- `apps/ios/Pebbles/Services/GoogleSignIn.swift` — new.
- `apps/ios/Pebbles/Services/SupabaseService.swift` — add `signInWithApple()` and `signInWithGoogle()`.
- `apps/ios/Pebbles/Services/AppEnvironment.swift` — add `googleIOSClientID`.
- `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift` — add Apple + Google buttons and disclosure.
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — three new strings, en + fr.
- `apps/ios/Pebbles/Resources/Info.plist` — register reversed Google iOS client ID URL scheme.
- `apps/ios/Pebbles/Resources/Assets.xcassets` — Google "G" mark asset (light + dark).
- xcconfig (release + debug) — add `GOOGLE_IOS_CLIENT_ID`.

## Open dashboard work (not in PR)

- Supabase Auth → Apple provider: add iOS Bundle ID to Authorized Client IDs.
- Supabase Auth → Google provider: add iOS OAuth client ID to Authorized Client IDs.
- Apple Developer portal: confirm Sign in with Apple capability on the App ID.
- Google Cloud Console: create the iOS OAuth client (if not already created) and copy its client ID + reversed client ID into xcconfig and Info.plist respectively.
