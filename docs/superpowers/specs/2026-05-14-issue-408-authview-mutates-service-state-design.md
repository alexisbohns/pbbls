# Issue #408 — AuthView mutates SupabaseService state from the View layer

## Problem

`AuthView` assigns `supabase.authError = nil` directly from `.onChange` handlers and from the form's `submit` paths. `SupabaseService` is a shared `@Observable` service, not a screen-scoped ViewModel, so any view holding a reference can silently clear another view's error state. `authError` is transient UI state belonging to one screen; it should not live on a shared service.

## Goal

Remove `authError` from `SupabaseService` entirely. Move it into `AuthView` as local `@State`. Have the service's auth methods throw on failure; the view catches and renders.

## Non-goals

- No changes to error logging discipline — the service still logs every error path with `os.Logger` before rethrowing.
- No new `AuthViewModel`. `AuthView`'s existing `@State` fields are sufficient.
- No changes to Apple / Google cancel handling (still silent).

## Design

### `SupabaseService` (`apps/ios/Pebbles/Services/SupabaseService.swift`)

Delete:

- `var authError: String?` property.
- The `if event == .signedIn { self.authError = nil }` branch inside `start()`.

Change signatures from `async` to `async throws`:

- `signIn(email:password:)`
- `signUp(email:password:)`
- `signInWithApple()`
- `signInWithGoogle()`

`signIn` / `signUp`: drop `self.authError = nil` at the top and `self.authError = error.localizedDescription` in the catch. Keep `logger.error(...)` then `throw error`.

`signInWithApple`: keep the existing `catch AppleSignInService.Failure.canceled { /* silent */ }` clause (returns normally). Other errors: log then rethrow.

`signInWithGoogle`: keep the existing `catch let error as ASWebAuthenticationSessionError where error.code == .canceledLogin { /* silent */ }` clause (returns normally). Other errors: log then rethrow.

`signOut()` is unchanged — failures stay logged-only.

### `AuthView` (`apps/ios/Pebbles/Features/Auth/AuthView.swift`)

Add `@State private var authError: String?`.

Update the error display:

```swift
if let error = authError {
    Text(error)
        // …unchanged…
}
```

Update the three `.onChange` handlers to clear local state:

```swift
.onChange(of: mode) { _, newMode in
    authError = nil
    if newMode == .login {
        termsAccepted = false
        privacyAccepted = false
    }
}
.onChange(of: email) { _, _ in
    if authError != nil { authError = nil }
}
.onChange(of: password) { _, _ in
    if authError != nil { authError = nil }
}
```

Update each action to clear the local error, call the throwing service method, and catch:

```swift
private func submit() {
    isSubmitting = true
    authError = nil
    Task {
        do {
            switch mode {
            case .login:
                try await supabase.signIn(email: email, password: password)
            case .signup:
                try await supabase.signUp(email: email, password: password)
            }
        } catch {
            authError = error.localizedDescription
        }
        isSubmitting = false
    }
}

private func runApple() async {
    guard !isSubmitting else { return }
    isSubmitting = true
    authError = nil
    do {
        try await supabase.signInWithApple()
    } catch {
        authError = error.localizedDescription
    }
    isSubmitting = false
}

private func runGoogle() async {
    guard !isSubmitting else { return }
    isSubmitting = true
    authError = nil
    do {
        try await supabase.signInWithGoogle()
    } catch {
        authError = error.localizedDescription
    }
    isSubmitting = false
}
```

The doc comment at the top of `AuthView` currently mentions reading `authError` from `SupabaseService`; update it to reflect that the error is now view-local.

## Behavioral notes

- On successful sign-in, `client.auth.authStateChanges` emits `.signedIn`, `session` becomes non-nil, and the root view swaps `AuthView` out of the tree. The view's `authError` is destroyed with it. No explicit "clear on success" is needed.
- The Apple / Google cancel paths still return normally from the service, so the view's `do` block falls through without entering `catch`. `authError` stays `nil` — same UX as today.

## Why not the other approaches considered

- **`clearAuthError()` method on the service.** Encapsulates the assignment but not the ownership — any other view could still call it and clear `AuthView`'s state. Doesn't address the root cause.
- **`AuthViewModel`.** Heavier than needed. `AuthView` has no cross-cutting state to coordinate beyond the existing `@State` fields. Introducing a ViewModel for one screen would add indirection without buying anything.

## Test plan

No unit tests exist for `AuthView` or `SupabaseService` and none are added here (no `SupabaseServicing` protocol yet — per `apps/ios/CLAUDE.md`, that's introduced only when a test actually needs it).

Manual verification on a device or simulator after the change:

1. Email login with a wrong password → red error appears under the form.
2. Edit the email field → error clears.
3. Edit the password field → error clears.
4. Toggle the Login/Signup switcher → error clears, signup checkboxes reset when switching to Login.
5. Tap Connect again with the right password → sign-in succeeds, view swaps to the tab bar.
6. Sign out, return to AuthView → no stale error.
7. Tap Sign in with Apple, dismiss the system sheet → no error shown (cancel is silent).
8. Tap Sign in with Google, dismiss the in-app Safari sheet → no error shown (cancel is silent).

## Out of scope

- Renaming or reorganizing the auth surface beyond what this fix requires.
- Adding a `SupabaseServicing` protocol for testability.
- Any change to web-app auth handling.

## Files touched

- `apps/ios/Pebbles/Services/SupabaseService.swift`
- `apps/ios/Pebbles/Features/Auth/AuthView.swift`
- `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift` — also read `supabase.authError` and called `signInWithApple` / `signInWithGoogle`; gets the same view-local `authError` + `do/catch` treatment.
