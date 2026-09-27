# Recent sign-in before high-harm account actions (step-up auth)

Issues: #976 (this design: server + Android, enforcement off), #977 (enforcement on, web + iOS).

## Problem

Three account actions are gated only by a live session:

- **Account deletion.** `delete-account` authenticates the JWT and purges, irreversibly. The only confirmation is a client dialog.
- **Password change.** `auth.updateUser { password }` needs no current password, so a hot session can lock the owner out.
- **Public profile on.** A direct owner-scoped `profiles.public_profile = true` update exposes the profile.

Whoever holds an unlocked phone or a copied session can therefore destroy or expose an account. The fix is a **recent sign-in** requirement: prove the credential again, within a short window, before these actions, and have the server check the proof.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Proof of recent sign-in | The JWT `amr` claim's timestamps | GoTrue stamps each real authentication (`password`, `oauth`, …) on the session; a token refresh keeps the original timestamps. No new table and no token plumbing, and SQL (`auth.jwt()`) and edge functions can both read it. |
| Window | 10 minutes | Long enough to re-auth and then confirm without being bounced; short enough that a borrowed phone an hour later is refused. |
| Gated actions | Account deletion, password change, public profile false→true | Irreversible, lockout, and account-wide exposure. |
| Not gated | A single pebble becoming `public` | Revocable, one pebble, and it sits in the everyday record flow; a re-auth prompt there costs more than it protects. Accepted scope decision. |
| Not gated | Turning the public profile off, releasing a handle, signing out everywhere | Protective or narrowing actions. |
| Rollout | Staged: check ships with enforcement **off** (#976), flipped on after web and iOS re-auth (#977) | Enforcing now would break account deletion and the public flip on web and on shipped iOS builds. |
| Alternatives rejected | Server-issued single-use step-up token; GoTrue `reauthenticate()` nonce | The token needs a table, issuance, expiry, and a new shape for every gated call (including a direct table update). The nonce is only consumed by GoTrue's `updateUser`, so deletion and the public flip could not verify it. |

## Part 1 — server (`packages/supabase`), enforcement off

### SQL (one migration)

- `public.recent_auth_ok(p_amr jsonb, p_max_age interval) returns boolean` — no table reads, `stable` (it reads `now()`). True when any element of `p_amr` has a numeric `timestamp` (epoch seconds) with `to_timestamp(ts) >= now() - p_max_age`. Null, non-array, empty, or elements without a numeric `timestamp` → false. Granted to `authenticated` so the harness can exercise it directly.
- `public.recent_auth_enforced() returns boolean` — constant `false` in this migration. #977 re-emits it as `true`. A function (not a settings row) so the flip is a reviewed migration and cannot be toggled by any client role.
- `public.assert_recent_auth() returns void` — `security invoker`. If `recent_auth_enforced()` and not `recent_auth_ok(auth.jwt()->'amr', interval '10 minutes')`, raise `reauth_required` (`errcode 'P0001'`, message exactly `reauth_required` so every client maps one stable code). Granted to `authenticated`.
- Trigger `profiles_public_profile_recent_auth` — `before update of public_profile on public.profiles`, `when (old.public_profile = false and new.public_profile = true)`. Calls `assert_recent_auth()` only for client roles (`current_user in ('authenticated', 'anon')` — the same test `profiles_privileged_guard` uses), so service-role and definer paths (moderation, consent withdrawal) are untouched.

### `delete-account` edge function

After `auth.getUser()` succeeds and **before** `purge_account`: call `authClient.rpc("assert_recent_auth")` on the auth-forwarded client, so `auth.jwt()` is the caller's token. A `reauth_required` error returns `403 { error: "reauth_required" }`; any other error returns 500 and logs, like the other steps. With enforcement off the call is a no-op, and it already proves the wiring.

### Password change

It stays with GoTrue, whose server-side lever is `secure_password_change` (a password update needs a session under 24h old, or an emailed nonce). Turning it on today breaks web and iOS password changes on sessions older than a day, so it belongs to #977, together with the password-changed notification. Until then, the Android client's 10-minute check is the gate for password change.

### Harness — `scripts/verify-recent-auth.ts`

Anon-key only, throwaway user, cleaned up through `delete-account` (the existing pattern). Asserts:

1. `recent_auth_ok` over crafted `amr` values: fresh `password`, fresh `oauth`, 11-minute-old, mixed old + fresh, `[]`, `null`, a non-array, an element missing `timestamp`, a string `timestamp`.
2. The fresh session's own token passes `assert_recent_auth`.
3. The trigger does not over-block: with a fresh session, claim a handle, flip `public_profile` true, then false, and re-read the stored value each time.
4. `delete-account` with a fresh session succeeds (this is the cleanup, asserted).

The stale-token attack case (sleep past the window, expect `reauth_required` from both gates) needs enforcement on, so it lands in #977 as a nightly-only case. Add the script to `db:verify` and the CI anon set.

Regenerate `types/database.ts` (`db:types:remote`).

## Part 2 — Android: sign out of all devices

A Settings entry, "Sign out of all devices", under the existing sign-out. `SupabaseServicing.signOut(everywhere: Boolean = false)` calls `client.auth.signOut(SignOutScope.GLOBAL)` when true, so every refresh token for the user is revoked. The local teardown is the same as a normal sign-out (cache clearing from #965 runs off the session change). Not gated: it's protective. A plain confirm dialog guards against a mis-tap.

## Part 3 — Android: re-auth before the three actions

### Freshness on the client

`RecentAuth.isFresh(accessToken, now)`, a pure function in `core/data`, base64url-decodes the JWT payload, reads `amr`, and applies the same rule and 10-minute window as the SQL. Unparseable → not fresh. It decides whether to prompt. The server stays the authority once #977 flips it.

### "Confirm it's you" dialog

One `ReauthDialog` in `core/designsystem`, driven by a small `ReauthState` owned by the calling ViewModel:

- **The account has an `email` identity** (`SettingsInitial.providers` contains `email`): a password field. Submit calls `auth.signInWith(Email) { email = currentEmail; password = entered }`. The email is fixed from the session, so this re-issues a session for the same user with a fresh `amr`. A wrong password shows an inline error and the dialog stays open.
- **Google-only account**: a "Continue with Google" button that re-runs the hosted OAuth flow with `login_hint = currentEmail` and `prompt = select_account`. When the session returns, compare the user id with the one captured before. On mismatch, sign out and surface an error rather than silently switching accounts.
- Cancel aborts the pending action and changes nothing.

`reauthenticate…` methods live on `SupabaseServicing` so ViewModels never touch supabase-kt directly (the #848 boundary).

### Wiring

- **Delete account.** `DeletionState` gains `REAUTHENTICATING`. Flow: confirm dialog → if not fresh, re-auth dialog → `DELETING`. A `reauth_required` 403 (after #977, or clock skew) re-enters `REAUTHENTICATING` instead of `FAILED`.
- **Settings save.** Before any write, if the form changes the password (non-empty) or turns `public_profile` false→true and the token isn't fresh, open the re-auth dialog, then run the unchanged save. Other field edits never prompt. A `reauth_required` error from the profile update re-opens the dialog.

### Risk to check during implementation

Re-signing in replaces the session in supabase-kt. `RootViewModel`'s session observers (consent gate, #965 cache clearing on user change) must treat a same-user session swap as a no-op. Verify on device, and add a test if the observer keys on the session rather than the user id.

## Residuals (stated, not fixed here)

- Google may complete OAuth without a password prompt if the browser is still signed in to Google (no reliable force-login parameter). This still proves control of the Google account, which is weaker than a password.
- Until #977: the server check is not enforced, and password change is server-gated only by GoTrue defaults.
- The original session is not revoked by re-auth. "Sign out of all devices" is the tool for that.

## Testing

- SQL + edge function: `verify-recent-auth.ts` against the linked project. Existing harnesses must stay green, in particular `verify-public-profile.ts` and `verify-account-purge.ts`, which flip `public_profile` and delete users with fresh sessions.
- Android unit tests: `RecentAuth.isFresh` against real GoTrue token payloads captured verbatim (password and OAuth `amr`, per the cross-surface payload rule), plus the stale / empty / malformed cases; `SettingsViewModel` transitions (prompt only when gated fields change; deletion `REAUTHENTICATING` path; `reauth_required` re-entry; cancel is a no-op).
- Device: the password re-auth path and a Google-only account on the emulator; same-user session swap leaves the app on the Settings screen.

## Stack

1. `feat/976-recent-auth-db` — migration, edge function, harness, types, this spec.
2. `feat/976-android-global-signout` — Part 2.
3. `feat/976-android-reauth` — Part 3.

The finding stays open after this stack. It closes with #977.
