# Drafts & local autosave — design (M47)

Design doc for milestone **M47 · Drafts and local autosave**. Parent spec:
`2026-07-28-store-launch-roadmap.md` §M47 (plus the convergence-map row
"`pebble_drafts` jsonb payload" and §5 item 6). The four issues cut from this
doc follow the house cadence: migration + types → web reference → iOS →
Android.

Recording a pebble is all-or-nothing today. All three composers gate the save
button on `name` **and** `emotion_id`, so a half-articulated thought cannot be
kept. And composer state is purely in-memory on every surface — web
`useState`, iOS `@State` in a sheet, Android `remember` (not
`rememberSaveable`) inside a conditionally-composed cover with no back-stack
entry — so a reload, a rotation or a process kill loses everything typed.

M47 ships two distinct things that share one payload shape:

- **Quick capture** — an intentional, server-side draft. "Just a name" is
  enough. Survives reinstalls and is visible from every surface.
- **Local autosave** — an unintentional, device-local snapshot of the *open*
  composer. Crash insurance, nothing more.

Pre-constrained by `docs/decisions/log.md` (2026-07-29, "Offline is a non-goal
on every surface", #620): the local snapshot carries no merge logic, no
cross-device sync, is cleared on publish or server-draft save, and `sw.ts`
keeps Supabase requests `NetworkOnly`.

## Shipped pieces

| Piece | Path |
|---|---|
| Migration (table + RLS + purge extension) | `packages/supabase/supabase/migrations/<ts>_pebble_drafts.sql` |
| Purge regression harness | `packages/supabase/scripts/verify-account-purge.ts` |
| Drafts acceptance harness | `packages/supabase/scripts/verify-pebble-drafts.ts` |
| Web data layer | `apps/web/lib/data/usePebbleDrafts.ts`, `supabase-provider.ts` |
| Web autosave | `apps/web/lib/hooks/useComposerAutosave.ts` |
| Web payload projection | `apps/web/components/record/draft-payload.ts` |
| Web drafts surface | `apps/web/app/drafts/page.tsx`, `components/drafts/` |
| iOS | `Features/Path/Models/PebbleDraftPayload.swift`, `Services/PebbleDraftsService.swift`, `Services/ComposerSnapshotStore.swift`, `Features/Path/DraftsListSheet.swift` |
| Android | `features/path/models/PebbleDraftPayload.kt`, `services/PebbleDraftsService.kt`, `services/ComposerSnapshotStore.kt`, `features/path/DraftsScreen.kt` |

## D1 — A separate `pebble_drafts` table, never a status column on `pebbles`

`pebbles` has **five NOT NULL semantic columns** — `name`, `happened_at`,
`intensity`, `positiveness`, `emotion_id` (`20260411000001_core_tables.sql`
:54-60) — and a draft by definition may have none of them. Six reasons the
status-column alternative was rejected:

1. All five NOT NULLs would need relaxing, weakening the constraint for every
   real pebble.
2. Every view and analytics migration would need a forever `status <> 'draft'`
   filter, and forgetting one silently leaks drafts into ripple, bounce,
   week groups and the KPI views.
3. Drafts must earn zero karma. `create_pebble` is the schema's **only**
   `pebble_created` emitter (`20260729140000_media_quota_profile_lookup.sql`
   :198-209) and it always emits, so any insert-a-draft-row path would have to
   grow a karma exception.
4. `update_pebble` is coalesce-based and **cannot null a scalar** (:329-338) —
   only `description` and `glyph_id` use the `payload ? key` form. Editing a
   draft down to fewer fields would be impossible; a wholesale jsonb replace
   is trivial.
5. No `render_svg` exists pre-publish, so a draft row would sit in `pebbles`
   with a null render that every read path has to special-case.
6. Autosave wants exactly the same partial payload, so one shape serves both.

The table is deliberately minimal — `id`, `user_id`, `payload jsonb`,
`created_at`, `updated_at`. The list surface reads `payload->>'name'` and
`payload->>'emotion_id'` directly; no generated or denormalized columns.

## D2 — The payload is a partial *wire* payload, not a per-platform draft shape

`payload` uses the exact key names `compose-pebble` already accepts, verified
identical across `apps/web/lib/data/supabase-provider.ts:361-382`,
`apps/ios/.../PebbleCreatePayload.swift:8-47` and
`apps/android/.../PebbleCreatePayload.kt:21-67`:

```jsonc
{
  "name": "string",
  "description": "string | null",
  "happened_at": "2026-07-29T14:03:00Z",
  "intensity": 2,
  "positiveness": 0,
  "visibility": "private",
  "emotion_id": "uuid",
  "domain_ids": ["uuid"],
  "soul_ids": ["uuid"],
  "collection_ids": ["uuid"],
  "glyph_id": "uuid | null",
  "snaps": [{ "id": "uuid", "storage_path": "…", "sort_order": 0 }]
}
```

Keys the user has not set are **omitted**. `cards` is never written (web sends
`cards: []` on create; drafts omit the key entirely).

Anchoring on the wire shape rather than on each platform's composer struct is
what makes publishing a pass-through and keeps three hand-written clients in
agreement. It matters because the in-memory composer structs are *not*
symmetric: iOS `PebbleDraft` uses `UUID` + `Date` and singular
`domainId`/`collectionId`; Android uses `String` + `OffsetDateTime`; web uses
flat `useState` with `mark_id` renamed to `glyph_id` only at the provider
boundary. Each surface therefore gets a dedicated payload model with a
projection to and from its composer struct.

Consequence worth stating: **`Valence` and `Visibility` need no new
`Codable`/`@Serializable` conformance on either native platform.** Valence is
stored decomposed as `intensity` + `positiveness` exactly as the wire does
(both keys omitted together when unset, rebuilt on hydration —
`Valence.fromOrDefault(positiveness, intensity)` already exists on Android),
and `visibility` is a string. The payload models hold primitives only.

## D3 — Server drafts carry the photo; local autosave does not

Snap bytes are uploaded **eagerly at pick time**, not at publish, on all three
surfaces — the composer holds a plain `{ id, storage_path, sort_order }`
descriptor whose bytes are already durable in the `pebbles-media` bucket. So a
server draft can carry `snaps` verbatim at zero extra cost, and resuming one
reuses machinery the edit screens already have: iOS
`snaps?.seedExisting(.existing(id:storagePath:))`, Android
`FormSnap.Existing`, web the signed-URL `displayUrl` selection in
`useSnapStaging` (`components/pebble/PebbleEditPicture.tsx:41-164`).

One contract change follows: **"Save as draft" must skip the cancel-time
`snaps.cancelAndCleanup`** on iOS (`CreatePebbleSheet.swift:98-103`) and
Android, which otherwise deletes from Storage the very object the draft
references.

Local autosave omits `snaps`. On web the preview is a `blob:` object URL that
cannot survive a reload anyway, and a restored descriptor would need a signed
URL fetch for a snapshot that is meant to be free. The restore prompt says the
photo needs re-attaching. This asymmetry is the honest one: an *intentional*
save is worth durable media, an *accidental* crash recovery is not.

## D4 — Drafts get their own surface, not an inline timeline section

Web `/drafts`; iOS a sheet, Android a full-screen cover, both reached from the
Path header with a count. The timeline is a `happened_at`-ordered history of
real pebbles whose week grouping, ripple, bounce and stats all assume exactly
that; drafts are not pebbles and would have to be filtered out of each. Draft
rows show a placeholder chip because a draft has no `render_svg`, a
"no name yet" fallback, and a relative saved-time from `updated_at`.

Resume hydrates the composer from the payload — the same pattern the edit
screens already prove (`EditPebbleSheet.swift:155-191`,
`EditPebbleScreen.kt:133-157`).

## D5 — Drafts relax *saving*, never *publishing*

The publish action keeps its existing `name` + `emotion_id` gate. Only the new
"Save as draft" action is ungated. On the native surfaces this means a **new
predicate** (`isSavableAsDraft`) rather than relaxing `draft.isValid`, because
`PebbleCreatePayload.init` `precondition`s / `require`s on validity and
force-unwraps `valence!`, `emotionId!`, `domainId!` — a partial draft must
never reach it.

Publishing fills the composer's client-side defaults (`intensity: 2`,
`positiveness: 0`, `visibility: "private"`, empty arrays), calls
`compose-pebble` **exactly once**, then deletes the draft row. Deletion is
keyed off the returned `pebble_id`, **not** HTTP status: all three clients
treat a 5xx carrying `pebble_id` as soft success (the row was inserted, only
the render write-back failed), and a soft-success publish must not leave an
orphan draft behind.

## D6 — `happened_at` is stored verbatim and published verbatim

Web re-stamps a `happened_at` within 60 s of now at submit time (`isNow()`,
`QuickPebbleEditor.tsx:41-44`). That re-stamp is **not** applied to a resumed
draft. For a quick capture the moment you captured is the moment it happened;
publishing hours later must not silently move the pebble to publish time.

## D7 — Stale ids are sanitized on hydration, not left to fail at publish

The payload is jsonb, so nothing protects the ids inside it. A soul,
collection or glyph deleted after the draft was saved (or a glyph anonymized by
`purge_account`, or delisted) would make publish fail on an FK violation or a
`can_use_glyph` `42501` — an opaque error at the worst moment. Every composer
already loads souls, collections, usable glyphs and emotions, so hydration
drops ids absent from those sets. Publishing a hydrated draft then cannot fail
on a dangling reference.

## D8 — No RPC; direct single-table client calls

Owner-scoped single-table CRUD is the sanctioned direct-client case (root
`AGENTS.md`): no multi-table write, no karma, nothing to make atomic. One
`for all` policy following the `glyph_favourites` template
(`20260630003348_glyph_marketplace.sql:42-64`) — explicit `to authenticated`,
both `using` and `with check`. Deliberately **not** the older four-policy
core-table style, whose UPDATE policy omits `with check` and would let a row be
moved to another user's `user_id`. `updated_at` is maintained by the shared
`public.set_updated_at()` trigger rather than by clients, because the list
orders on it.

Draft saves must not route through the create path on any surface: web
`usePebbles.addPebble` diffs karma and fires `notifyKarma`, and the native
screens call `karma.notifyEarned` on success.

## D9 — `purge_account` extension (M46 standing rule)

`pebble_drafts` is the first customer of the standing rule in
`20260729201326_account_deletion_purge.sql:26-28`. The delete goes at the
explicit `>>> APPEND new per-user tables from later milestones HERE. <<<`
marker in section (4), and `verify-account-purge.ts` gains a seeded draft plus
a zero-row assertion. FK order imposes nothing here — a draft holds its
references as jsonb, so it can neither block nor be orphaned by sections
(1)–(3); the eager delete exists so "all personal rows gone" is true at RPC
success and re-runs stay meaningful.

The `delete-account` edge function needs **no change**: it is table-agnostic,
and a draft's snaps already live under the `pebbles-media/{user_id}/` prefix it
sweeps.

## D10 — Local persistence is new on both native platforms

Neither app has on-disk persistence today. The signed-URL caches are
in-memory only (`SnapURLCache.swift`, `SnapURLCache.kt`) and the sole
persisted value is a boolean (`@AppStorage("hasSeenOnboarding")` /
SharedPreferences `pebbles_prefs`). So:

- **iOS** — a JSON file in the caches directory, wrapped in an `@Observable`
  `@MainActor` store injected from `PebblesApp` (the `SnapURLCache` shape).
  Encode/decode stays pure so it is testable.
- **Android** — the JSON string in the existing `pebbles_prefs`
  SharedPreferences file. Deliberately **not** DataStore: that needs a
  `libs.versions.toml` entry, and version bumps are isolated commits per
  `apps/android/CLAUDE.md`.
- **Web** — `useSyncExternalStore` over `localStorage`, key
  `pbbls-composer-draft`, ~800 ms debounce, `getServerSnapshot()` for SSR,
  validate-or-discard on read. Follows the custom-event variant in
  `lib/i18n/useLocale.ts:13-48` rather than the synthetic-`StorageEvent` hack
  in `ColorWorldProvider`. It lives in `lib/hooks/` (UI state, not
  provider-backed data) because `docs/agents/data-and-async.md:8` forbids
  touching `localStorage` from a component.

## D11 — In-scope adjacent fix: web `handleSubmit` swallows failures

`QuickPebbleEditor.handleSubmit` has a `try`/`finally` and **no `catch`**
(`:122-152`): a failed create is silent, no toast, no error state, form not
reset. Publish-then-delete-draft cannot be built correctly on top of that, so
the web issue adds the `catch` and surfaces the failure with the inline
`role="alert"` banner precedent from `PebbleEdit.tsx:279-286`.

## D12 — Timestamp precision is a cross-surface hazard (#651)

The three surfaces do not agree on sub-second precision, and nothing in a single
app's test suite can notice:

| Writer | `happened_at` |
|---|---|
| web `new Date().toISOString()` | `2026-07-30T01:23:45.123Z` (milliseconds) |
| Postgres `timestamptz` via PostgREST | `…T01:23:45.123456+00:00` (microseconds) |
| iOS `.withInternetDateTime` | `2026-07-30T01:23:45Z` (whole seconds) |

A single `ISO8601DateFormatter` parses exactly one of those. M47 first shipped
iOS decoding with the non-fractional formatter, so **every web- and
Postgres-written timestamp silently became `nil`** and hydration fell back to
"now" — quietly breaking D6 in the one case D6 exists for. The same bare
`JSONDecoder` also failed `PebbleDraftRecord` outright (`typeMismatch(Double)` on
`updated_at`), which would have broken the iOS drafts list entirely.

Rules that follow:

- iOS parses through `ISO8601Flexible` (fractional first, then whole-second) and
  **emits whole seconds**, matching `PebbleCreatePayload` byte-for-byte so the
  publish path is unchanged and both other surfaces parse it happily.
- `PebbleDraftRecord.updatedAt` decodes from its **string** form rather than
  trusting whatever date strategy the ambient decoder carries. A model that
  depends on its decoder's configuration is a model that breaks when the SDK
  changes it.
- Android needed no change (`OffsetDateTime.parse` accepts all three) but gets
  the same cross-surface tests, because "it happens to work" is not a contract.
- **Every surface has a decoding test fed real foreign payloads verbatim**, not
  its own output. An iOS-only round-trip cannot catch an iOS-only formatter bug —
  that is precisely how this shipped.

## D13 — The badge count does not fetch payloads

The Path entry point renders one integer, on the app's home screen, on every
appearance. All three surfaces first implemented it by reusing the full list,
pulling every draft's jsonb to count rows. They now select `id` alone (web uses a
`head: true` exact count). Deliberately not a new count API on the native SDKs:
there is no precedent for one in this repo and Android cannot be compiled
locally, so the cheap projection buys the same win with no new surface.

## D14 — Verification

Per surface, beyond lint/build:

- **Migration** — RLS probe with a second test user: B's
  `select`/`update`/`delete` against A's draft returns zero rows, and an
  insert with a foreign `user_id` is rejected by `with check`. Karma
  invariant: creating, updating and deleting drafts leaves
  `count(*) from karma_events` unchanged. `verify-account-purge.ts` passes
  with the draft seeded and asserted at zero.
- **Publish path** — publishing a draft emits **exactly one**
  `pebble_created` event and removes the draft row, including on the
  soft-success (5xx-with-`pebble_id`) path.
- **Media** — attach a photo, save as draft, resume: the photo still renders
  (signed URL) and was not swept by cancel cleanup.
- **Stale refs** — delete a soul referenced by a saved draft, resume it: the
  chip is dropped and publish succeeds.
- **Crash insurance** — web hard-reload mid-compose; iOS background then
  force-quit; Android rotate and process-death. Each shows the restore prompt.
- **Cross-surface** — a draft saved on web appears in the iOS and Android
  drafts lists and publishes correctly from each. This is the real test of D2.

Android cannot be verified locally (no SDK; `scripts/gradle-if-sdk.sh` exits 0
with a warning), so `android.yml` — `ktlintCheck testDebugUnitTest
assembleDebug`, all blocking, plus `LocalizationParityTest` — is the gate, and
new logic stays in pure JVM-testable functions.

## Lessons learned

Kept here rather than promoted into `CLAUDE.md` — per the repo's own cadence,
promotion happens during the milestone-boundary monorepo-audit grooming pass, not
per-PR. The first item below clears both bars (durable + action-guiding) and is
the candidate to promote.

1. **A same-surface round-trip proves nothing about a cross-surface contract.**
   M47 shipped with every iOS decoding test passing while iOS could not read a
   single web- or Postgres-written timestamp (D12). The tests encoded with the
   same formatter they decoded with, so the bug was invisible by construction. Any
   shape shared by the three clients needs tests fed **real foreign payloads
   verbatim**, including precision variants and explicit nulls. `#651`.
2. **A model that depends on its decoder's configuration is a model that breaks
   when the SDK changes it.** `PebbleDraftRecord` originally left `updated_at` to
   whatever date strategy supabase-swift happened to hand it, and a dead
   `let decoder = JSONDecoder()` sat unused in `list()` as evidence of the
   half-finished fix. Decode timestamps from their string form explicitly.
3. **Compare `jsonb` canonically.** Postgres does not preserve key order at any
   depth, so a `JSON.stringify` equality check reports an intact payload as
   changed — twice, in this milestone's own harness, before the assertion was
   fixed rather than the product.
4. **Silent `str.replace` no-ops are a real failure mode when patching code by
   script.** The Android composer shipped to CI missing both new parameters
   because a search string was one backtick off and the replace quietly did
   nothing. Assert the match count.
5. **Two rounds of shaving comments to fit a line-count limit is the limit
   telling you to split the file.** `CreatePebbleSheet` needed
   `+Drafts.swift`, not shorter prose (#648, #652).
