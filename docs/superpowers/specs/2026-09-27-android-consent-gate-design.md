# The Android consent gate, and version-bound terms and privacy — design

**Date:** 2026-09-27 · **Surfaces:** `packages/supabase`, `apps/android` · **Milestone:** M55 · Compliance Batch A

## Context

Android collects consent on one path out of three, and records none of it
against a document version.

- **Google sign-in records nothing.** `SupabaseService.signInWithGoogle()`
  calls `client.auth.signInWith(Google)` with no data block. The Google button is
  reachable from Welcome (no checkboxes at all), Login and Sign up (the two
  checkboxes render in email Sign up mode only, and gate only the email submit).
  `handle_new_user` NULL-safes the absent keys, so every Google-created Android
  account carries NULL `terms_accepted_at` / `privacy_accepted_at` forever.
- **Email sign-up records two unversioned timestamps.** `consentMetadata()`
  sends `terms_accepted_at` and `privacy_accepted_at` only: no document version,
  no Art. 9 health-data consent, no 16+ attestation, and no `signup_surface`.
- **Terms and privacy acceptance are version-less on every surface.** The
  `user_consents` ledger (`20260911090000`, `…090060`, `20260913090000`) binds
  `health_data`, `public_profile` and `age_assurance` to a document version, but
  terms and privacy still live as two `timestamptz` columns on `profiles`.

Three open findings converge on the same checkboxes and the same gate, and this
stack resolves all three:

| Finding | Priority | What it says |
|---|---|---|
| `F-2026-08-GDP-android-01` | P1 | Google signup records no consent; stored consent is version-less |
| `F-2026-08-GDP-android-02` | P1 | No Art. 9 explicit-consent step on Android; no DPIA |
| `F-2026-08-SAF-android-02` | P1 | No age gate at Android signup (also issue #822) |

### Verified at HEAD (`main` @ `e2d75ada`, 2026-09-27)

- Every citation in `GDP-android-01` holds; `SupabaseService.kt` moved from
  `services/` to `core/data/` in the M61 restructure, nothing else changed.
- `docs/compliance/dpia.md` exists (Art. 9 stack, Part 1) and describes all four
  surfaces, Android included (§1, "Four client surfaces…"). The DPIA half of
  `GDP-android-02` is therefore already satisfied; only the consent step is not.
- The Terms (`apps/web/docs/terms/en.md` §3.1, `version: 1.1.0`) now state a
  16+ minimum with no parental-consent machinery, so the "13+ and French
  parental consent" contradiction in `SAF-android-02` is gone on the document
  side; only the Android gate is missing.
- The backend already accepts every Android source value
  (`android_register`, `android_oauth`, `android_settings`, widened in
  `20260911090060` §1). What it lacks is a `terms` and a `privacy` kind.

## 1. Decisions

| # | Decision | Rejected alternative |
|---|---|---|
| D1 | **One post-auth gate** on Android. After any session lands, the app checks the ledger for the four required acts at the current document versions and blocks behind a consent screen if any is missing. The Google buttons stay as they are. | A web-style pre-gate on the Google buttons with a stashed consent flushed after the Custom Tab returns. Login-mode Google would still need a post-auth gate (web's D1a), so that is two mechanisms, and it never reaches existing accounts. |
| D2 | **Terms and privacy become ledger kinds** `terms` and `privacy`, non-withdrawable like `age_assurance`. | Version columns on `profiles` (no history, a re-acceptance overwrites the previous act, and the privileged-column trigger needs a new RPC anyway), or a `terms` kind alone that leans on the `health_data` row for the privacy version (conflates acknowledging a notice with an Art. 9 consent). |
| D3 | **One stack for all three findings.** The gate asks for terms, privacy, health data and 16+ together. | Three passes over the same checkboxes and the same gate. |
| D4 | The gate **fails closed**: if the ledger read fails, the screen offers Retry and Sign out rather than letting the user through. | Fail open and re-check next launch. An OAuth user would then use the app without any consent on record for a session, which is the defect itself. |
| D5 | The gate asks only for what is **missing or outdated**. | Always showing all four. A terms-only version bump would then re-ask for Art. 9 consent nobody changed. |
| D6 | The legacy `profiles.terms_accepted_at` / `privacy_accepted_at` columns keep being written by `handle_new_user` and are otherwise left alone. The ledger is the record. | Dropping or backfilling them. They are write-only (see the Art. 9 spec §1.2), and removing them is a separate cleanup. |
| D7 | *(Added while planning.)* A passed check is **cached on the device** as one `"<userId>\|<fingerprint>"` string, where the fingerprint is every required kind at its current version. A launch with a matching cache entry skips the network check. | A network round trip behind a blocking screen on every launch, which also locks a consented user out of the app whenever they launch offline (D4 would fail closed on them). A version bump changes the fingerprint, so the cache cannot hide a re-acceptance. |
| D8 | *(Added while planning.)* An active row satisfies the gate when its version is **the same or newer** (semver) than the one the app ships. | Exact-match. A web deploy that bumps the policy ahead of an Android release would make the older app ask again, and `record_consent` would then supersede the newer acceptance with an older version, a downgrade in an accountability record. |
| D9 | *(Added while planning.)* The gate is an **overlay drawn above `NavDisplay`** in `RootScreen`, like the celebration overlays, not a back-stack entry. It hides the content below from accessibility services and consumes Back. | A `PebblesKey` entry. Anything that navigates (an invite App Link, a restored stack) can push above an entry, so an entry would be a gate the app routes around. |

## 2. Stack

One part is one branch is one PR, chained with `gh stack`.

| Part | Branch | Contents | User-facing |
|---|---|---|---|
| 1 | `feat/966-consent-ledger-terms-privacy` (#966) | Migration, types, purge harness | no |
| 2 | `feat/822-android-signup-consent-payload` (#822) | Versions, four checkboxes, full signup metadata | yes |
| 3 | `feat/967-android-consent-gate` (#967) | `ConsentService`, gate logic, gate screen, RootScreen wiring | yes |

Part 1 must be pushed to the linked project (`db:push`) before Part 2 or 3
reaches a device: Part 2's metadata keys are read only by the new trigger body,
and Part 3 calls `record_consent` with the new kinds.

## 3. Part 1 — `terms` and `privacy` in the ledger

One migration, `20260927090000_consent_terms_privacy.sql`.

### 3.1 Widen the `kind` CHECK

Same discover-by-definition pattern as `20260913090000` §1: find the single
CHECK whose definition mentions `health_data`, `public_profile` and
`age_assurance` with `into strict`, drop it, and re-add
`user_consents_kind_check` as
`kind in ('health_data', 'public_profile', 'age_assurance', 'terms', 'privacy')`.
Follow it with the same FK-reaching probe, once per new kind.

### 3.2 Make them non-withdrawable, structurally

Replace `user_consents_age_not_withdrawable` with
`user_consents_not_withdrawable`:
`check (not (kind in ('age_assurance', 'terms', 'privacy') and withdrawn_at is not null))`.
Accepting the Terms is not revoked by a toggle; it ends with the account, which
`purge_account` already erases. `withdraw_consent`'s allowlist is **not**
widened, so it keeps refusing these with `invalid_kind` (the first guard); the
CHECK is the structural one.

### 3.3 `record_consent` allowlist

Re-emit the body from `20260913090000` §5 verbatim, with `'terms', 'privacy'`
added to the `p_kind` allowlist. `create or replace` has no merge semantics:
diff against the latest emission before writing.

### 3.4 `handle_new_user`

Re-emit the body from `20260913090000` §6 verbatim, and at the append marker add
two inserts, each guarded like the existing ones (both keys non-null):

| Kind | Timestamp key | Version key |
|---|---|---|
| `terms` | `terms_accepted_at` | `terms_version` |
| `privacy` | `privacy_accepted_at` | `privacy_version` |

`source` is the existing mapped `v_surface`. Reusing the timestamp keys every
client already sends means a client that adds the version key starts recording
with no other change.

**Collision warning.** Issue #823 (malformed signup metadata aborts signup)
also re-emits `handle_new_user`. Whichever lands second must diff the two
bodies and union them in a new migration (root `CLAUDE.md`, "Two migrations
that re-emit the same whole function body…").

### 3.5 Types and harness

- `npm run db:types:remote --workspace=packages/supabase`, commit `types/database.ts`.
- `verify-account-purge.ts`: seed a `terms` and a `privacy` row through
  `record_consent` (the zero-row assertion's expected count goes from 3 to 5);
  assert `withdraw_consent('terms')` and `withdraw_consent('privacy')` fail with
  `invalid_kind`.
- Replay the migration chain on the native local Postgres before `db:push`, then
  run `npm run db:verify:purge --workspace=packages/supabase` against the linked
  project (CI does not run that harness).

## 4. Part 2 — the Android email signup payload

### 4.1 Document versions

`core/model/LegalVersions.kt`:

```kotlin
object LegalVersions {
    const val TERMS = "1.1.0"
    const val PRIVACY = "1.3.0"
}
```

`health_data` and `age_assurance` cite `PRIVACY`, as web does
(`CONSENT_DOCUMENT_VERSION`). A JVM unit test reads the `version:` frontmatter
of `apps/web/docs/terms/en.md` and `apps/web/docs/privacy/en.md` (relative to
the repo root) and asserts equality, so a policy bump that forgets Android fails
the Android build.

### 4.2 `consentMetadata()`

Extended to the full key set `handle_new_user` reads, all stamped with one
`now` (whole seconds, per the cross-surface timestamp rule):

```
terms_accepted_at, terms_version,
privacy_accepted_at, privacy_version,
health_data_consent_at, health_data_consent_version,
age_attested_at, age_attestation_version,
signup_surface = "android"
```

The unit test asserts the exact key set against a fixture listing the keys the
trigger reads, so a renamed key fails a test rather than silently recording
nothing. **`signup_surface` is load-bearing:** without it the trigger stamps the
rows `web_register`, silently and uncorrectably.

### 4.3 Four checkboxes

Sign up mode renders terms, privacy, the Art. 9 statement, and 16+. Copy is
adapted from web (`register.healthConsent`, `register.ageAttestation`) in
`values/strings.xml` and `values-fr/strings.xml`, inserted at anchors as text.
`AuthLogic.canSubmit` takes all four booleans and requires them in Sign up mode.
The Google button is unchanged (D1).

## 5. Part 3 — the consent gate

### 5.1 `ConsentService` (`core/data`)

- `activeConsents(): Set<ActiveConsent>` — `select kind, document_version from
  user_consents where withdrawn_at is null and superseded_at is null`. RLS scopes
  it to the owner. It deliberately **does not select timestamps**: rows written by
  web carry microsecond `granted_at`, and a column the gate does not need is a
  cross-surface decoder it does not need either.
- `record(kind, version, source)` — `rpc("record_consent", …)`.

### 5.2 `ConsentGateLogic` (pure, `features/consent`)

```kotlin
enum class ConsentKind(val wire: String) { TERMS("terms"), PRIVACY("privacy"), HEALTH_DATA("health_data"), AGE("age_assurance") }

fun required(): Map<ConsentKind, String>   // kind → current version, from LegalVersions
fun missing(active: List<ActiveConsent>): List<ConsentKind>  // absent, or active only at an OLDER version (D8)
fun source(provider: String?): String       // "google" → android_oauth, else android_settings
```

The source mapping follows web's re-consent precedent (age spec §8:
`web_settings` for acts collected outside signup), so no source value needs
adding. A Google account re-asked after a version bump records `android_oauth`,
which is where the act was collected from.

### 5.3 `ConsentGateScreen` + ViewModel

States: `Checking` → `Satisfied` | `Required(missing)` | `Failed`.

- `Required`: a title, a short explanation, one checkbox per missing kind (the
  Terms and Privacy rows link to the in-app legal sheet as the auth screen does),
  Continue (enabled when all shown boxes are ticked), and Sign out.
- Continue calls `record` once per ticked kind, then re-runs the check. Each call
  is one idempotent `record_consent` — the same shape as web's OAuth callback.
  The acts are independent, so there is no cross-row invariant for a batch RPC to
  protect; a partial failure leaves the gate showing only what is still missing.
- `Failed` (read or record error): the error, Retry, and Sign out (D4). Errors
  are logged with `Log.e` under the `consent` tag.

### 5.4 `RootScreen` wiring

The gate sits between "authenticated" and everything after it, onboarding
included. It is keyed on the user id so a sign-out/sign-in as someone else
re-checks. Declining is Sign out; a brand-new Google account that declines keeps
an empty account, which the user can delete from Settings like any other.

### 5.5 What the gate covers

| Path | Before | After |
|---|---|---|
| Email sign up | 2 unversioned timestamps | 4 ledger rows from the trigger; gate passes |
| Google from Welcome / Login / Sign up, new account | nothing | gate, 4 rows (`android_oauth`) |
| Existing Android account, any provider | whatever it had | gate once, for what is missing |
| Any account after a policy version bump | nothing | gate, for the bumped kinds |

## 6. Testing

| What | How |
|---|---|
| Migration | Local Postgres replay; the probes inside the migration; purge harness against the linked project |
| Version parity | JVM test reading the web frontmatter (§4.1) |
| Signup payload | `consentMetadata` key-set test against the trigger's key list (§4.2) |
| Signup gating | `AuthLogic.canSubmit` cases for each of the four boxes |
| Gate logic | `missing()` for: empty ledger, all current, one outdated version, withdrawn-only rows (never returned by the query, so absent), a web-written row set |
| Gate UI | Screenshot previews for `Required` (all four / one), `Failed` — the screenshot gate is CI-only; diff against main, never re-baseline locally |
| End to end | Pixel 7 AVD: new Google account hits the gate and lands in onboarding after Continue; email signup skips the gate; `user_consents` shows four rows with `android_*` sources |

## 7. Out of scope — follow-on issues

| Work | Why not here |
|---|---|
| Web sends `terms_version` / `privacy_version` and records the two kinds on OAuth | Separate surface; the trigger already reads the keys once Part 1 lands |
| iOS: the same, folded into #821 (age attestation on iOS) | iOS mirrors Android 1:1; its signup payload is being reopened by #821 anyway |
| Health-data consent withdrawal in Android Settings (`AC-you-can-take-that-permission-back`) | A settings feature, not a signup gap; none of the three findings asks for it |
| #788 gains a note that Android pre-existing accounts are covered by this gate | Scope clarification only |
| Dropping the legacy `profiles` consent columns | D6 |

## 8. Arkaik

At the start of work:

- `DM-user-consents` platforms gain `android`.
- `AC-you-knowingly-agree-to-pebbles-recording-how-you-feel` and
  `AC-signup-consent-is-kept-on-record` gain `android` → `development`.
- New view `V-consent-gate` (android), composed into `F-legal-consent`.

Only the Part 3 PR body names the three finding ids, because only that PR closes
them. After merge, resolve each with `kritik_resolve_finding`, citing the Part 3
PR (plus Part 1 for the version half of `GDP-android-01`).

## 9. Risks

| Risk | Mitigation |
|---|---|
| A gate bug locks every Android user out (D4 fails closed) | `missing()` is pure and unit-tested; the emulator smoke covers both providers; Sign out is always available |
| Android ships before `db:push` of Part 1 | `record_consent` would reject `terms` / `privacy` with `invalid_kind` and the gate shows `Failed`. Push Part 1 first; the release workflow publishes on every main push, so merge order matters |
| #823 and Part 1 both re-emit `handle_new_user` | §3.4 collision warning; diff before applying |
| Android cohort today is Play internal testing only | Not a mitigation, a scale: the gate still runs for every one of them on the next launch |
