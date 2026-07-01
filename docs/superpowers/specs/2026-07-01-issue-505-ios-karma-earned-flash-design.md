# iOS karma-earned flash — Dynamic Island + haptics + sound

**Issue:** #505 · **Milestone:** M36 · Pebblestore & Karma Economy · **Follows:** #494 (wallet rails), #495 (web activity primitive)

## Intent

When a user earns karma on iOS (creating or enriching a pebble), play a transient
"+N karma" flash with haptic and sound. The flash follows the **Opal grammar**: the
Dynamic Island momentarily **expands** (icon grows), **settles** to a compact `+N`,
then **collapses and vanishes** after ~2.5s — the same visual language the system
uses for Focus-mode / ringer state changes.

The flash is **delight only**. It is never authoritative over the karma balance. The
`PathBottomBar` karma number remains backend truth, reconciled by the existing
background `stats.refresh()`; the flash neither writes nor gates it.

## Key decisions (and why)

These resolve the open questions and challenges raised against #505 during
brainstorming. They override the corresponding parts of the issue body.

### D1 — Amount comes from a server-computed delta, read back in the edge functions

The issue proposed a client-side before/after diff of `PathStatsService.karma`,
"exactly like web." This is **unsound on iOS**: web diffs a synchronous localStorage
read (`provider.getStore().karma`), but iOS would diff a shared `@Observable` behind a
**coalescing** network refresh. `PathStatsService.refresh()` guards
`guard !isLoading else { return }` (`PathStatsService.swift:34`) — it silently no-ops
if a load is already in flight, so a fast earn during the initial `.task { await stats.load() }`
yields `before == after` → delta 0 → the flash is silently dropped. That directly
violates the "no user is left with silent confirmation" acceptance criterion.

`compute_karma_delta` (`packages/supabase/.../20260411000003_rpc_functions.sql:16-45`)
is a **pure, deterministic** function — base +1, +1 non-empty description, +N cards
(capped 4), +1 any soul, +1 any domain, +1 glyph, +1 any snap, total capped at 10 —
with **no hidden server modifiers** (no streak/first-pebble/anti-abuse adjustment).
`create_pebble` awards `compute_karma_delta(new state)`; `update_pebble` awards
`new − old`. The exact number is already recorded in `karma_events.delta`.

**Mechanism (contained to the edge functions, no migration):**

- `create_pebble` returns `uuid`; `update_pebble` returns `void`. **Web calls both RPCs
  directly** (`SupabaseProvider`), so changing their signatures would break web.
- **iOS calls them only through the `compose-pebble` / `compose-pebble-update` edge
  functions.** Each edge function reads the delta it just recorded —
  `select delta from karma_events where ref_id = <pebble_id> order by created_at desc limit 1` —
  and adds `karma_delta: number` to its JSON response.
- RPC signatures unchanged. Web untouched. No SQL migration.
- iOS `ComposePebbleResponse` gains `karmaDelta: Int?`. The flash reads the delta from
  the response it **already awaits** before dismissing the sheet — no `stats.refresh()`
  dependency, no race.

**Verification gate:** confirm the edge function's auth-forwarded client can `select`
its own `karma_events` rows under RLS (it writes them via the RPC under the same
identity, so it should). If not, read the delta with the `admin` client already used
for render composition.

### D2 — The real fallback fork is "has a Dynamic Island?", not "are Live Activities enabled?"

The issue falls back to the in-app path only when
`areActivitiesEnabled == false || Activity.request throws`. This **misses** iPhone 13 /
SE / 14 / 14 Plus / 16e — devices with Live Activities *enabled* but **no Dynamic
Island**. There, `Activity.request` *succeeds* and renders only on the Lock Screen —
invisible during a foreground earn.

The correct fork is **whether the device has a Dynamic Island**. There is no public
`hasDynamicIsland` API, so this is a small documented helper:

- `DeviceCapabilities.hasDynamicIsland: Bool` — a device-model-identifier check with a
  documented list. **Default for unknown/future models → `false` (capsule).** Rationale:
  the capsule is always-visible foreground feedback, so the worst case for a brand-new
  DI phone is that it shows the capsule instead of the DI until we add its identifier —
  graceful degradation, never a silent miss.
- Flagged as a **maintenance point** (update the list as new hardware ships) and a
  **device-verification gate**.

### D3 — Presentation is a transient Live Activity used the Opal way

On the physical Dynamic Island, **ActivityKit Live Activities are the only public API** —
the Focus-mode / ringer / charging pills are private system UI. The Opal grow-then-shrink
*is* the start/update animation of a Live Activity. So we keep the Widget Extension and
drive it transiently: request → auto-expand → `end(.immediate)` after ~2.5s.

The exact auto-expand behavior is **system-governed**, not frame-tunable. It is a
**device-verification gate**: if the "grow" reads too subtly on hardware, the lever is
an `AlertConfiguration` on the request. The ~2.5s dismiss timing is likewise a
tune-on-device value, not a guaranteed constant.

## Architecture

### Trigger layer (no new Xcode target)

- **`KarmaNotificationService`** (`@Observable @MainActor`, shaped like
  `PathStatsService`). Single entry point `notifyEarned(amount:reason:)`. Guards
  `amount > 0` — a silent no-op otherwise, so deletions and enrich-that-removes-content
  never flash. Owns the presentation dispatch (D2 path selection), the haptic trigger
  value (D4), and the sound call (D4). Feature-agnostic, mirroring web's `notifyKarma`.
- **`KarmaReason`** enum: `.pebbleCreated` / `.pebbleEnriched` only. Web's
  `grant`/`purchase`/`refund`/`pebble_deleted` are omitted — no iOS call sites exist yet
  (YAGNI; add when a real caller lands).
- **Wiring:** `PathView.swift:57-72` is the single choke point. `CreatePebbleSheet.onCreated`
  gains the `karmaDelta` from `ComposePebbleResponse`; `PebbleDetailSheet.onPebbleUpdated`
  likewise from the update response. Each calls `notifyEarned(amount:reason:)`
  fire-and-forget, off the `load()` / `stats.refresh()` path so it never blocks the
  timeline reload or the sheet dismiss.

`onCreated` currently has signature `(UUID) -> Void`. It becomes `(UUID, Int?) -> Void`
(pebble id + karma delta), or the response object is passed through — decided in the plan.
`onPebbleUpdated` (`() -> Void?`) similarly gains the delta.

### Presentation A — transient Live Activity (Dynamic Island hardware)

- **New `PebblesWidget` target** — `type: app-extension`,
  `NSExtensionPointIdentifier: com.apple.widgetkit-extension`, embedded via xcodegen
  `embed: true`. Added to `project.yml`; `xcodegen generate` regenerates the project
  (the `.xcodeproj` is a git-ignored artifact per `apps/ios/CLAUDE.md`).
- **Shared `KarmaActivityAttributes: ActivityAttributes`** in a new `apps/ios/Shared/`
  folder, compiled into **both** the app and widget targets. `amount` + `reason` live in
  the dynamic `ContentState`; no static attributes needed. **No App Group** — ActivityKit
  delivers `ContentState` directly through `request`/`update`/`end` for this purely local,
  non-push use case.
- **`ActivityConfiguration`**: a minimal Lock Screen/banner view + `DynamicIsland`
  (`expanded` / `compactLeading` / `compactTrailing` / `minimal`). Compact = `sparkle`
  + `+N`; expanded = same, larger. Reuses `Image(systemName: "sparkle")` tinted
  `Color.accent.primary` (per `PathBottomBar.swift`) — no new colors. The Lock Screen
  view is kept minimal because we `end(.immediate)` and it is rarely seen.
- **Lifecycle** (in `KarmaNotificationService` or a dedicated `KarmaLiveActivityController`):
  - Check `ActivityAuthorizationInfo().areActivitiesEnabled` fresh each call.
  - `Activity.request(attributes:content:pushType: nil)` — `pushType` is an optional
    `PushType?`, pass `nil` (verify against installed ActivityKit SDK; there is no
    `.none` case).
  - A second earn within the window **updates** the running activity in place
    (replace-not-stack, mirroring web's stable-toast-id) and **resets** the dismiss task.
  - Auto-end after ~2.5s via a cancellable `Task`; `Activity.end(dismissalPolicy: .immediate)`
    so nothing lingers on the Lock Screen.
  - If `request` throws or `areActivitiesEnabled == false` → fall through to Presentation B.

### Presentation B — in-app capsule (non-DI hardware, or LA disabled/throws)

- **`KarmaEarnedCapsule`** presented from `RootView.swift`. Drops from the top
  (notch/status-bar region) to echo the Dynamic Island, spring in/out, auto-dismiss
  ~2.5s, tap-to-dismiss. Same `sparkle` + `Color.accent.primary` language.
- Emits `AccessibilityNotification.Announcement` for VoiceOver.

### Path selection

```
if DeviceCapabilities.hasDynamicIsland
   && ActivityAuthorizationInfo().areActivitiesEnabled
   && (Activity.request succeeds)   → Presentation A (Live Activity)
else                                → Presentation B (in-app capsule)
```

Both paths always fire the haptic and the sound.

### Haptics & sound

- **Haptics:** SwiftUI-native `.sensoryFeedback(.success, trigger:)` (the pattern
  preferred in #410). `KarmaNotificationService` exposes an incrementing trigger value;
  `.sensoryFeedback` is applied on `RootView` (and/or `PathView`). Chosen over imperative
  `UINotificationFeedbackGenerator` for the declarative fit with both presentation paths.
- **Sound:** `AudioService.playKarmaEarnedSound()` using `AudioServicesPlaySystemSound`
  (respects the ring/silent switch automatically). Candidate IDs `1103`/`1013`/`1025`/`1057`
  — **audition on-device**, do not assume from this doc. Structured so swapping to a
  bundled `.caf` + `AVAudioPlayer` (category `.ambient`) later is a same-signature,
  one-file change.

### Localization

- New `Localizable.xcstrings` entries (en/fr). French copy verbatim from web
  (`apps/web/lib/i18n/messages/fr.json` → `"Caillou créé"` / `"Caillou enrichi"`).
- The **widget extension carries its own minimal String Catalog** — String Catalogs are
  not shared across the app/extension boundary.
- Follow `apps/ios/CLAUDE.md`: no `New`/`Stale` entries, both `en` and `fr` filled before PR.

## Testing

- **Unit-testable (Swift Testing):**
  - The `amount > 0` guard in `KarmaNotificationService` (deletions/clawbacks/negative
    enrich deltas never flash).
  - `DeviceCapabilities.hasDynamicIsland` for known identifiers and the unknown-default.
  - The `karmaDelta` decode from `ComposePebbleResponse` (including `nil`/absent field).
- **Device-manual only (explicitly not unit-testable):** Live Activity lifecycle, the
  auto-expand animation, replace-not-stack on re-earn, `end(.immediate)` leaving no
  Lock Screen residue, capsule presentation on non-DI hardware, sound honoring the silent
  switch. These are the device-verification gates.

## Out of scope

- A dedicated iOS wallet/karma-history screen (mirrors web `/wallet`; doesn't exist on
  iOS). The flash is dismiss-only; `PathBottomBar`'s karma stat (already one tap from
  `ProfileView`) is the "go see your karma" affordance.
- Reasons other than `pebbleCreated` / `pebbleEnriched` (`grant`, `purchase`, `refund`) —
  no iOS call sites.
- Any SQL migration or RPC signature change; App Groups; push-driven Live Activities.

## Open risks / device-verification gates

1. **Auto-expand reads too subtly** (D3) → add `AlertConfiguration` to the request. Verify
   the grow-then-shrink on real 14 Pro+ hardware.
2. **`hasDynamicIsland` device list** (D2) is a maintenance point; verify the identifier
   list and the unknown-default on device.
3. **Edge-function RLS read-back** (D1) — confirm the auth-forwarded client can select its
   own `karma_events`; fall back to the `admin` client if not.
4. **`Activity.request` `pushType: nil`** — verify against the installed ActivityKit SDK
   (optional `PushType?`, no `.none` case).
5. **Cross-target color tokens** — sharing `.xcassets` across targets via xcodegen is less
   common than sharing a `.swift` file; duplicating the one colorset into the widget target
   is the safe fallback.
6. **`project.yml` widget-extension shape** and first `xcodegen generate` + build are only
   truly validated on a real Xcode toolchain.
7. **~2.5s dismiss timing** is tune-on-device, not a guaranteed constant.
8. **Rapid re-earn** update/dismiss-task cancellation must be race-free; and repeated
   short-lived Live Activity start/end should be checked for any system throttling.

## Acceptance

- Creating or enriching a pebble on a Dynamic Island device shows a "+N karma" Live
  Activity that briefly expands then auto-dismisses (~2.5s); a second earn within the
  window updates the existing activity rather than stacking.
- On non-DI hardware (or with Live Activities disabled / `request` throws), the same event
  shows the in-app capsule — no user is left with silent confirmation.
- The "+N" amount matches the karma actually awarded by the transaction (server delta),
  never a guessed diff; the `PathBottomBar` balance remains backend truth.
- Haptic + sound fire on both paths; sound respects the ring/silent switch.
- VoiceOver announces the earn; French renders correctly throughout (including inside the
  widget extension).
- Deletions / clawbacks / negative-delta enriches never flash.
