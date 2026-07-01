# iOS Karma-Earned Flash Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a user earns karma on iOS (create/enrich a pebble), play a transient "+N karma" flash — a Dynamic Island Live Activity on capable hardware, an in-app capsule elsewhere — with haptic and sound.

**Architecture:** The karma amount is a server-computed delta read back inside the two iOS-only edge functions (`compose-pebble` / `compose-pebble-update`) and returned in the response iOS already awaits — no client diff, no race, no SQL migration. A feature-agnostic `KarmaNotificationService` receives `(amount, reason)`, fires haptic + sound, and routes presentation: a transient `ActivityKit` Live Activity when the device has a Dynamic Island and Live Activities are enabled, otherwise an in-app capsule presented from `RootView`. The balance stays backend truth via the existing background `PathStatsService.refresh()`; the flash never writes it.

**Tech Stack:** Deno/TypeScript edge functions (Supabase); SwiftUI + iOS 17 `@Observable`; ActivityKit + WidgetKit (new `PebblesWidget` app-extension target via xcodegen); Swift Testing; `AudioServicesPlaySystemSound`; `.sensoryFeedback`.

**Spec:** `docs/superpowers/specs/2026-07-01-issue-505-ios-karma-earned-flash-design.md`

**Branch:** `feat/505-ios-karma-earned-flash` (already created).

**Execution environment note:** Swift/Xcode tasks require a macOS + Xcode toolchain. iOS tests run via `npm run test --workspace=@pbbls/ios` (xcodebuild on a simulator). The edge functions have no Deno test harness in this repo, so their verification is by deploy + manual (documented per task). ActivityKit lifecycle/animation is device-manual only (Task 15).

---

## File Structure

**Backend (edge functions — iOS-only path, no migration):**
- Create: `packages/supabase/supabase/functions/_shared/karma-delta.ts` — `readKarmaDelta(client, refId, reason)` helper.
- Modify: `packages/supabase/supabase/functions/compose-pebble/index.ts` — add `karma_delta` to the 200 response.
- Modify: `packages/supabase/supabase/functions/compose-pebble-update/index.ts` — add `karma_delta` to the 200 response.

**iOS — model:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/ComposePebbleResponse.swift` — add `karmaDelta: Int?`.

**iOS — karma notification feature (new folder `Features/Karma/`):**
- Create: `apps/ios/Pebbles/Features/Karma/KarmaReason.swift`
- Create: `apps/ios/Pebbles/Features/Karma/KarmaEarnedContent.swift`
- Create: `apps/ios/Pebbles/Features/Karma/KarmaPresentationDecision.swift` — pure routing fn.
- Create: `apps/ios/Pebbles/Features/Karma/KarmaNotificationService.swift` — `@Observable @MainActor` orchestrator.
- Create: `apps/ios/Pebbles/Features/Karma/KarmaEarnedCapsule.swift` — in-app fallback view.
- Create: `apps/ios/Pebbles/Features/Karma/KarmaLiveActivityController.swift` — ActivityKit lifecycle.

**iOS — services & capabilities:**
- Create: `apps/ios/Pebbles/Services/AudioService.swift`
- Create: `apps/ios/Pebbles/Services/DeviceCapabilities.swift`

**iOS — shared with widget:**
- Create: `apps/ios/Shared/KarmaActivityAttributes.swift` — compiled into both app and widget.

**iOS — widget extension (new target):**
- Create: `apps/ios/PebblesWidget/PebblesWidgetBundle.swift`
- Create: `apps/ios/PebblesWidget/KarmaActivityWidget.swift`
- Create: `apps/ios/PebblesWidget/Info.plist`
- Create: `apps/ios/PebblesWidget/Assets.xcassets/AccentPrimary.colorset/Contents.json` (+ copy of the colorset)
- Create: `apps/ios/PebblesWidget/Localizable.xcstrings`
- Modify: `apps/ios/project.yml` — add `PebblesWidget` target, embed into `Pebbles`, add `Shared/` sources.

**iOS — wiring:**
- Modify: `apps/ios/Pebbles/PebblesApp.swift` — construct + inject `KarmaNotificationService`; attach `.sensoryFeedback`.
- Modify: `apps/ios/Pebbles/RootView.swift` — overlay the capsule + `.sensoryFeedback`.
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` — call `notifyEarned` on success.
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` — call `notifyEarned` on success.

**iOS — localization:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

**Tests:**
- Modify: `apps/ios/PebblesTests/ComposePebbleResponseDecodingTests.swift`
- Create: `apps/ios/PebblesTests/KarmaPresentationDecisionTests.swift`
- Create: `apps/ios/PebblesTests/DeviceCapabilitiesTests.swift`
- Create: `apps/ios/PebblesTests/KarmaNotificationServiceTests.swift`

---

## Phase 1 — Backend: server-computed delta in the edge functions

### Task 1: `readKarmaDelta` helper + wire into `compose-pebble` (create)

**Files:**
- Create: `packages/supabase/supabase/functions/_shared/karma-delta.ts`
- Modify: `packages/supabase/supabase/functions/compose-pebble/index.ts`

Context: `create_pebble` inserts exactly one `karma_events` row with `reason='pebble_created'`, `ref_id=<pebble_id>`, `delta=compute_karma_delta(...)` (`20260411000003_rpc_functions.sql:180-181`). We read that row back with the auth-forwarded client (RLS: the user owns the row it just wrote) and add it to the response.

- [ ] **Step 1: Write the helper**

Create `packages/supabase/supabase/functions/_shared/karma-delta.ts`:

```typescript
import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * Reads the karma delta a create/enrich RPC just recorded for a pebble.
 *
 * `create_pebble` writes one `pebble_created` event; `update_pebble` writes a
 * `pebble_enriched` event only when karma changed. We take the most recent row
 * matching (ref_id, reason) so a re-enrich reads its own delta. Returns null
 * when no matching row exists (e.g. an enrich that changed no karma) — the
 * client treats null as "nothing to celebrate".
 *
 * Best-effort: the karma flash is delight-only, so any read error resolves to
 * null rather than failing the whole create/enrich response.
 */
export async function readKarmaDelta(
  client: SupabaseClient,
  refId: string,
  reason: "pebble_created" | "pebble_enriched",
): Promise<number | null> {
  const { data, error } = await client
    .from("karma_events")
    .select("delta")
    .eq("ref_id", refId)
    .eq("reason", reason)
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (error) {
    console.error("readKarmaDelta failed:", error);
    return null;
  }
  return data?.delta ?? null;
}
```

- [ ] **Step 2: Wire it into `compose-pebble`**

In `packages/supabase/supabase/functions/compose-pebble/index.ts`, add the import near the existing shared imports:

```typescript
import { readKarmaDelta } from "../_shared/karma-delta.ts";
```

Then replace the success return (the `return json({ pebble_id: pebbleId, ...rendered }, 200);` line) with:

```typescript
    const karmaDelta = await readKarmaDelta(authClient, pebbleId as string, "pebble_created");
    return json({ pebble_id: pebbleId, karma_delta: karmaDelta, ...rendered }, 200);
```

Leave the soft-success 500 branch unchanged — no `karma_delta` there (the client skips the flash when it's absent).

- [ ] **Step 3: Deploy + manual verify (no Deno test harness in repo)**

Run: `npx supabase functions deploy compose-pebble --project-ref <ref>`
Then create a pebble from the iOS app (or curl the function with a valid JWT) and confirm the 200 body includes `"karma_delta": <n>` where `n` matches the `compute_karma_delta` rule for the payload.
Expected: `karma_delta` present and correct on create; balance in `v_karma_summary` unchanged in behavior.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/functions/_shared/karma-delta.ts \
        packages/supabase/supabase/functions/compose-pebble/index.ts
git commit -m "feat(api): return karma_delta from compose-pebble (#505)"
```

### Task 2: Wire `readKarmaDelta` into `compose-pebble-update` (enrich)

**Files:**
- Modify: `packages/supabase/supabase/functions/compose-pebble-update/index.ts`

Context: `update_pebble` inserts a `pebble_enriched` event with `delta = new_karma - old_karma` **only when karma changed** (`20260426000002_pebble_media_edit.sql:224-226`). If nothing changed, no row → `readKarmaDelta` returns null → no flash. The delta can be negative (content removed); the iOS `amount > 0` guard drops it.

- [ ] **Step 1: Add the import**

In `packages/supabase/supabase/functions/compose-pebble-update/index.ts`:

```typescript
import { readKarmaDelta } from "../_shared/karma-delta.ts";
```

- [ ] **Step 2: Add the delta to the success response**

Replace `return json({ pebble_id: body.pebble_id, ...rendered }, 200);` with:

```typescript
    const karmaDelta = await readKarmaDelta(authClient, body.pebble_id, "pebble_enriched");
    return json({ pebble_id: body.pebble_id, karma_delta: karmaDelta, ...rendered }, 200);
```

Leave the soft-success 500 branch unchanged.

- [ ] **Step 3: Deploy + manual verify**

Run: `npx supabase functions deploy compose-pebble-update --project-ref <ref>`
Enrich a pebble (e.g. attach a glyph) and confirm the 200 body includes a positive `karma_delta`; enrich with no karma-affecting change and confirm `karma_delta` is `null`.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/functions/compose-pebble-update/index.ts
git commit -m "feat(api): return karma_delta from compose-pebble-update (#505)"
```

---

## Phase 2 — iOS response model

### Task 3: Add `karmaDelta` to `ComposePebbleResponse` (TDD)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/ComposePebbleResponse.swift`
- Test: `apps/ios/PebblesTests/ComposePebbleResponseDecodingTests.swift`

- [ ] **Step 1: Add a failing decode test**

Append to `apps/ios/PebblesTests/ComposePebbleResponseDecodingTests.swift` (inside the existing `@Suite`):

```swift
@Test("decodes karma_delta when present")
func decodesKarmaDelta() throws {
    let json = """
    { "pebble_id": "\(UUID().uuidString)", "render_svg": "<svg/>", "render_version": "v1", "karma_delta": 5 }
    """.data(using: .utf8)!
    let response = try JSONDecoder().decode(ComposePebbleResponse.self, from: json)
    #expect(response.karmaDelta == 5)
}

@Test("karma_delta is nil when absent or null")
func karmaDeltaNilWhenAbsent() throws {
    let json = """
    { "pebble_id": "\(UUID().uuidString)" }
    """.data(using: .utf8)!
    let response = try JSONDecoder().decode(ComposePebbleResponse.self, from: json)
    #expect(response.karmaDelta == nil)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test --workspace=@pbbls/ios`
Expected: FAIL — `ComposePebbleResponse` has no member `karmaDelta`.

- [ ] **Step 3: Add the field**

In `apps/ios/Pebbles/Features/Path/Models/ComposePebbleResponse.swift`, add the property and coding key:

```swift
struct ComposePebbleResponse: Decodable {
    let pebbleId: UUID
    let renderSvg: String?
    let renderVersion: String?
    let karmaDelta: Int?

    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
        case renderSvg = "render_svg"
        case renderVersion = "render_version"
        case karmaDelta = "karma_delta"
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm run test --workspace=@pbbls/ios`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/ComposePebbleResponse.swift \
        apps/ios/PebblesTests/ComposePebbleResponseDecodingTests.swift
git commit -m "feat(core): decode karma_delta in ComposePebbleResponse (#505)"
```

---

## Phase 3 — Domain types & pure routing (TDD)

### Task 4: `KarmaReason`, `KarmaEarnedContent`, and the pure presentation decision

**Files:**
- Create: `apps/ios/Pebbles/Features/Karma/KarmaReason.swift`
- Create: `apps/ios/Pebbles/Features/Karma/KarmaEarnedContent.swift`
- Create: `apps/ios/Pebbles/Features/Karma/KarmaPresentationDecision.swift`
- Test: `apps/ios/PebblesTests/KarmaPresentationDecisionTests.swift`

- [ ] **Step 1: Write the reason enum**

Create `apps/ios/Pebbles/Features/Karma/KarmaReason.swift`:

```swift
import Foundation

/// Why karma was earned. Only the two iOS call sites that exist today —
/// creating and enriching a pebble. Web has more (grant/purchase/refund);
/// add here when a real iOS caller lands (YAGNI).
enum KarmaReason: String, Sendable, Codable, CaseIterable {
    case pebbleCreated
    case pebbleEnriched

    /// User-facing label, localized. French copy matches web verbatim
    /// ("Caillou créé" / "Caillou enrichi").
    var label: LocalizedStringResource {
        switch self {
        case .pebbleCreated:  "Pebble created"
        case .pebbleEnriched: "Pebble enriched"
        }
    }
}
```

- [ ] **Step 2: Write the content model**

Create `apps/ios/Pebbles/Features/Karma/KarmaEarnedContent.swift`:

```swift
import Foundation

/// One karma-earned event to celebrate. `Equatable`/`Sendable` so it can flow
/// into an `@Observable` capsule slot and across the ActivityKit boundary.
struct KarmaEarnedContent: Equatable, Sendable {
    let amount: Int
    let reason: KarmaReason
}
```

- [ ] **Step 3: Write the failing decision test**

Create `apps/ios/PebblesTests/KarmaPresentationDecisionTests.swift`:

```swift
import Testing
@testable import Pebbles

@Suite("Karma presentation decision")
struct KarmaPresentationDecisionTests {
    @Test("non-positive amount presents nothing")
    func nonPositiveIsSilent() {
        #expect(karmaPresentationDecision(amount: 0, hasDynamicIsland: true, activitiesEnabled: true) == .none)
        #expect(karmaPresentationDecision(amount: -3, hasDynamicIsland: true, activitiesEnabled: true) == .none)
    }

    @Test("Dynamic Island + activities enabled → live activity")
    func dynamicIslandPrefersLiveActivity() {
        #expect(karmaPresentationDecision(amount: 5, hasDynamicIsland: true, activitiesEnabled: true) == .liveActivity)
    }

    @Test("no Dynamic Island → capsule even when activities enabled")
    func noIslandFallsBackToCapsule() {
        #expect(karmaPresentationDecision(amount: 5, hasDynamicIsland: false, activitiesEnabled: true) == .capsule)
    }

    @Test("Dynamic Island but activities disabled → capsule")
    func islandButDisabledFallsBackToCapsule() {
        #expect(karmaPresentationDecision(amount: 5, hasDynamicIsland: true, activitiesEnabled: false) == .capsule)
    }
}
```

- [ ] **Step 4: Run to verify it fails**

Run: `npm run test --workspace=@pbbls/ios`
Expected: FAIL — `karmaPresentationDecision` / `KarmaPresentation` not defined.

- [ ] **Step 5: Write the pure decision**

Create `apps/ios/Pebbles/Features/Karma/KarmaPresentationDecision.swift`:

```swift
import Foundation

/// How a karma-earned event should be surfaced.
enum KarmaPresentation: Equatable, Sendable {
    case none
    case capsule
    case liveActivity
}

/// Pure routing decision. The real fork is "does this device have a Dynamic
/// Island?" — NOT "are Live Activities enabled?" — because iPhone 13/SE/14/
/// 14 Plus/16e have Live Activities but render them only on the Lock Screen,
/// which is invisible during a foreground earn.
///
/// A `.liveActivity` result is still best-effort: if `Activity.request` throws
/// at runtime, the caller falls back to `.capsule`.
func karmaPresentationDecision(
    amount: Int,
    hasDynamicIsland: Bool,
    activitiesEnabled: Bool
) -> KarmaPresentation {
    guard amount > 0 else { return .none }
    if hasDynamicIsland && activitiesEnabled { return .liveActivity }
    return .capsule
}
```

- [ ] **Step 6: Run to verify it passes**

Run: `npm run test --workspace=@pbbls/ios`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Karma/KarmaReason.swift \
        apps/ios/Pebbles/Features/Karma/KarmaEarnedContent.swift \
        apps/ios/Pebbles/Features/Karma/KarmaPresentationDecision.swift \
        apps/ios/PebblesTests/KarmaPresentationDecisionTests.swift
git commit -m "feat(core): karma reason, content, and pure presentation decision (#505)"
```

### Task 5: `DeviceCapabilities.hasDynamicIsland` (TDD)

**Files:**
- Create: `apps/ios/Pebbles/Services/DeviceCapabilities.swift`
- Test: `apps/ios/PebblesTests/DeviceCapabilitiesTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/DeviceCapabilitiesTests.swift`:

```swift
import Testing
@testable import Pebbles

@Suite("Device capabilities")
struct DeviceCapabilitiesTests {
    @Test("known Dynamic Island identifiers are recognized")
    func knownIslandModels() {
        #expect(DeviceCapabilities.isDynamicIslandModel("iPhone15,2")) // 14 Pro
        #expect(DeviceCapabilities.isDynamicIslandModel("iPhone16,1")) // 15 Pro
        #expect(DeviceCapabilities.isDynamicIslandModel("iPhone17,3")) // 16
    }

    @Test("non-island and unknown identifiers default to false (capsule)")
    func nonIslandModels() {
        #expect(!DeviceCapabilities.isDynamicIslandModel("iPhone14,7"))  // 14 (no island)
        #expect(!DeviceCapabilities.isDynamicIslandModel("iPhone14,6"))  // SE 3rd gen
        #expect(!DeviceCapabilities.isDynamicIslandModel("iPhone99,9"))  // unknown/future
        #expect(!DeviceCapabilities.isDynamicIslandModel(""))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `npm run test --workspace=@pbbls/ios`
Expected: FAIL — `DeviceCapabilities` not defined.

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Services/DeviceCapabilities.swift`:

```swift
import Foundation

/// Static device-capability checks.
///
/// There is no public `hasDynamicIsland` API, so this is a model-identifier
/// allowlist. MAINTENANCE POINT: add new Dynamic Island hardware here as it
/// ships. Unknown/future identifiers deliberately default to `false` so the
/// worst case is a brand-new DI phone showing the in-app capsule (always-
/// visible feedback) instead of the Live Activity — graceful degradation,
/// never a silent miss.
enum DeviceCapabilities {
    /// Model identifiers (e.g. "iPhone15,2") with a Dynamic Island.
    static let dynamicIslandIdentifiers: Set<String> = [
        "iPhone15,2", "iPhone15,3",   // iPhone 14 Pro / Pro Max
        "iPhone15,4", "iPhone15,5",   // iPhone 15 / 15 Plus
        "iPhone16,1", "iPhone16,2",   // iPhone 15 Pro / Pro Max
        "iPhone17,3", "iPhone17,4",   // iPhone 16 / 16 Plus
        "iPhone17,1", "iPhone17,2",   // iPhone 16 Pro / Pro Max
    ]

    /// Pure, testable membership check.
    static func isDynamicIslandModel(_ identifier: String) -> Bool {
        dynamicIslandIdentifiers.contains(identifier)
    }

    /// This device's model identifier. On the simulator, reads
    /// `SIMULATOR_MODEL_IDENTIFIER`; on device, reads `uname().machine`.
    static var currentModelIdentifier: String {
        if let sim = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] {
            return sim
        }
        var systemInfo = utsname()
        uname(&systemInfo)
        let machine = withUnsafeBytes(of: &systemInfo.machine) { raw -> String in
            let bytes = raw.prefix { $0 != 0 }
            return String(decoding: bytes, as: UTF8.self)
        }
        return machine
    }

    static var hasDynamicIsland: Bool {
        isDynamicIslandModel(currentModelIdentifier)
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `npm run test --workspace=@pbbls/ios`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Services/DeviceCapabilities.swift \
        apps/ios/PebblesTests/DeviceCapabilitiesTests.swift
git commit -m "feat(core): DeviceCapabilities.hasDynamicIsland (#505)"
```

---

## Phase 4 — Audio + notification service (capsule-only first)

### Task 6: `AudioService`

**Files:**
- Create: `apps/ios/Pebbles/Services/AudioService.swift`

- [ ] **Step 1: Implement**

Create `apps/ios/Pebbles/Services/AudioService.swift`:

```swift
import AudioToolbox
import os

/// Plays short UI sound effects. Uses `AudioServicesPlaySystemSound`, which
/// respects the ring/silent switch automatically. Structured so swapping to a
/// bundled `.caf` + `AVAudioPlayer` (category `.ambient`) later is a
/// same-signature, one-file change.
struct AudioService {
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "audio")

    /// System sound played on a karma earn. ID is a placeholder — AUDITION ON
    /// DEVICE and pick by ear before shipping (candidates: 1103/1013/1025/1057).
    private let karmaEarnedSoundID: SystemSoundID = 1103

    func playKarmaEarnedSound() {
        AudioServicesPlaySystemSound(karmaEarnedSoundID)
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `npm run build --workspace=@pbbls/ios`
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Services/AudioService.swift
git commit -m "feat(core): AudioService for karma-earned sound (#505)"
```

### Task 7: `KarmaNotificationService` — capsule routing + haptic + sound (TDD the guard)

**Files:**
- Create: `apps/ios/Pebbles/Features/Karma/KarmaNotificationService.swift`
- Test: `apps/ios/PebblesTests/KarmaNotificationServiceTests.swift`

Design: `@Observable @MainActor`. `notifyEarned(amount:reason:)` is the single entry point. It guards `amount > 0`, bumps a `hapticTrigger` (observed by `.sensoryFeedback`), plays the sound, then routes. A `KarmaLiveActivityPresenting?` seam is nil for now (capsule-only); Task 12 injects the real presenter. This keeps the service shippable and testable without ActivityKit.

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/KarmaNotificationServiceTests.swift`:

```swift
import Testing
@testable import Pebbles

@MainActor
@Suite("KarmaNotificationService")
struct KarmaNotificationServiceTests {
    @Test("positive earn bumps the haptic trigger and shows a capsule")
    func positiveEarnPresents() async {
        let service = KarmaNotificationService(hasDynamicIsland: false)
        service.notifyEarned(amount: 5, reason: .pebbleCreated)
        // Capsule routing is synchronous when there is no Dynamic Island.
        #expect(service.hapticTrigger == 1)
        #expect(service.activeCapsule == KarmaEarnedContent(amount: 5, reason: .pebbleCreated))
    }

    @Test("non-positive earn is a silent no-op")
    func nonPositiveIsSilent() {
        let service = KarmaNotificationService(hasDynamicIsland: false)
        service.notifyEarned(amount: 0, reason: .pebbleCreated)
        service.notifyEarned(amount: -2, reason: .pebbleEnriched)
        #expect(service.hapticTrigger == 0)
        #expect(service.activeCapsule == nil)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `npm run test --workspace=@pbbls/ios`
Expected: FAIL — `KarmaNotificationService` not defined.

- [ ] **Step 3: Implement (capsule-only; LA seam left nil)**

Create `apps/ios/Pebbles/Features/Karma/KarmaNotificationService.swift`:

```swift
import Foundation
import ActivityKit

/// Abstraction over the Live Activity path so the service is testable without
/// ActivityKit and so the controller can be injected after it exists (Task 12).
@MainActor
protocol KarmaLiveActivityPresenting: AnyObject {
    /// Presents/updates the Live Activity. Returns false if it could not be
    /// shown (disabled, throws) so the caller falls back to the capsule.
    func present(_ content: KarmaEarnedContent) async -> Bool
}

/// Feature-agnostic entry point for the "+N karma" flash. Mirrors web's
/// `notifyKarma`: any credit source calls `notifyEarned(amount:reason:)`.
/// Delight only — never authoritative over the karma balance.
@Observable
@MainActor
final class KarmaNotificationService {
    /// Content currently shown in the in-app capsule (nil = hidden). Observed
    /// by `RootView`.
    private(set) var activeCapsule: KarmaEarnedContent?

    /// Monotonic counter driving `.sensoryFeedback(.success, trigger:)`.
    private(set) var hapticTrigger: Int = 0

    /// Injected in Task 12; nil means capsule-only.
    weak var liveActivityPresenter: KarmaLiveActivityPresenting?

    private let hasDynamicIsland: Bool
    private let audio: AudioService
    private var capsuleDismissTask: Task<Void, Never>?

    /// Seconds the capsule stays up. Tune-on-device; mirrors the Live Activity
    /// dismiss window so both paths feel the same.
    private let capsuleDuration: Duration = .milliseconds(2500)

    init(hasDynamicIsland: Bool = DeviceCapabilities.hasDynamicIsland,
         audio: AudioService = AudioService()) {
        self.hasDynamicIsland = hasDynamicIsland
        self.audio = audio
    }

    func notifyEarned(amount: Int, reason: KarmaReason) {
        let decision = karmaPresentationDecision(
            amount: amount,
            hasDynamicIsland: hasDynamicIsland,
            activitiesEnabled: ActivityAuthorizationInfo().areActivitiesEnabled
        )
        guard decision != .none else { return }

        hapticTrigger &+= 1
        audio.playKarmaEarnedSound()

        let content = KarmaEarnedContent(amount: amount, reason: reason)

        if decision == .liveActivity, let presenter = liveActivityPresenter {
            // Try the Live Activity; fall back to the capsule if it can't show.
            Task {
                let shown = await presenter.present(content)
                if !shown { presentCapsule(content) }
            }
        } else {
            presentCapsule(content)
        }
    }

    private func presentCapsule(_ content: KarmaEarnedContent) {
        activeCapsule = content
        capsuleDismissTask?.cancel()
        capsuleDismissTask = Task { [weak self, capsuleDuration] in
            try? await Task.sleep(for: capsuleDuration)
            guard !Task.isCancelled else { return }
            self?.activeCapsule = nil
        }
    }

    /// Called when the user taps the capsule.
    func dismissCapsule() {
        capsuleDismissTask?.cancel()
        activeCapsule = nil
    }
}
```

Note: `ActivityAuthorizationInfo().areActivitiesEnabled` is safe to call on all iOS 17 devices. In the unit test the injected `hasDynamicIsland: false` forces the capsule branch, so `notifyEarned` completes synchronously and the assertions hold without awaiting a Task.

- [ ] **Step 4: Run to verify it passes**

Run: `npm run test --workspace=@pbbls/ios`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Karma/KarmaNotificationService.swift \
        apps/ios/PebblesTests/KarmaNotificationServiceTests.swift
git commit -m "feat(core): KarmaNotificationService with capsule routing (#505)"
```

---

## Phase 5 — In-app capsule + wiring the service into the app

### Task 8: `KarmaEarnedCapsule` view + present from `RootView` + haptics

**Files:**
- Create: `apps/ios/Pebbles/Features/Karma/KarmaEarnedCapsule.swift`
- Modify: `apps/ios/Pebbles/PebblesApp.swift`
- Modify: `apps/ios/Pebbles/RootView.swift`

- [ ] **Step 1: Write the capsule view**

Create `apps/ios/Pebbles/Features/Karma/KarmaEarnedCapsule.swift`:

```swift
import SwiftUI

/// In-app "+N karma" flash. Drops from the top to echo the Dynamic Island on
/// devices without one. Same visual language as `PathBottomBar`'s karma stat
/// (sparkle + accent). Tap to dismiss; VoiceOver announces the earn.
struct KarmaEarnedCapsule: View {
    let content: KarmaEarnedContent
    let onTap: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "sparkle")
                .foregroundStyle(Color.accent.primary)
            Text("+\(content.amount) karma")
                .font(.ysabeauSemibold(16))
                .foregroundStyle(Color.system.foreground)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.regularMaterial, in: Capsule())
        .overlay(Capsule().strokeBorder(Color.accent.primary.opacity(0.2)))
        .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
        .contentShape(Capsule())
        .onTapGesture(perform: onTap)
        .accessibilityElement()
        .accessibilityLabel("Earned \(content.amount) karma, \(content.reason.label)")
        .accessibilityAddTraits(.isStaticText)
    }
}

/// Presents the capsule as a top overlay driven by `KarmaNotificationService`.
struct KarmaCapsuleOverlay: ViewModifier {
    @Environment(KarmaNotificationService.self) private var karma

    func body(content: Content) -> some View {
        content.overlay(alignment: .top) {
            if let earned = karma.activeCapsule {
                KarmaEarnedCapsule(content: earned) { karma.dismissCapsule() }
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .zIndex(1)
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.75), value: karma.activeCapsule)
    }
}

extension View {
    func karmaCapsuleOverlay() -> some View { modifier(KarmaCapsuleOverlay()) }
}
```

If `.ysabeauSemibold(_:)` is not resolvable in this file's context, confirm the font extension is app-target-wide (it is used in `PathBottomBar.swift`); no import beyond `SwiftUI` should be needed.

- [ ] **Step 2: Construct + inject the service in `PebblesApp`**

In `apps/ios/Pebbles/PebblesApp.swift`, add a stored property and initialize it alongside the others:

```swift
    @State private var karma: KarmaNotificationService
```

In `init()`, after the existing service initializers:

```swift
        self._karma = State(initialValue: KarmaNotificationService())
```

In `body`, add the environment injection and the haptic feedback to `RootView`:

```swift
            RootView()
                .environment(supabase)
                .environment(palettes)
                .environment(refs)
                .environment(stats)
                .environment(snapURLs)
                .environment(karma)
                .sensoryFeedback(.success, trigger: karma.hapticTrigger)
```

- [ ] **Step 3: Overlay the capsule in `RootView`**

In `apps/ios/Pebbles/RootView.swift`, add the overlay to the top-level `ZStack`. Change the `ZStack { ... }` closing so the modifier applies to the whole stack — add `.karmaCapsuleOverlay()` immediately after the `ZStack { ... }` block (before or after `.task` modifiers is fine; place it right after the closing brace of `ZStack`):

```swift
        ZStack {
            // ...existing content unchanged...
        }
        .karmaCapsuleOverlay()
        .task {
            await supabase.start()
        }
        // ...remaining modifiers unchanged...
```

Update the `#Preview` to inject the service so previews compile:

```swift
        .environment(SnapURLCache(client: supabase.client))
        .environment(KarmaNotificationService())
```

- [ ] **Step 4: Build + test**

Run: `npm run build --workspace=@pbbls/ios`
Expected: BUILD SUCCEEDED.
Run: `npm run test --workspace=@pbbls/ios`
Expected: PASS (existing tests still green).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Karma/KarmaEarnedCapsule.swift \
        apps/ios/Pebbles/PebblesApp.swift apps/ios/Pebbles/RootView.swift
git commit -m "feat(ui): in-app karma capsule + haptic wiring (#505)"
```

### Task 9: Fire `notifyEarned` from the create + enrich sheets

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`

Rationale: both sheets already decode `ComposePebbleResponse` and hold the delta at the moment of success. Calling `notifyEarned` there (rather than threading the delta through `onCreated`/`onSaved`/`onPebbleUpdated`) avoids changing those closure signatures. The `amount > 0` guard lives in the service, so passing `karmaDelta ?? 0` is safe. The soft-success (5xx) paths carry no `karma_delta`, so they intentionally skip the flash — acceptable for a delight-only feature; the balance still reconciles via `stats.refresh()`.

- [ ] **Step 1: Inject the service + fire in `CreatePebbleSheet`**

In `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`, add the environment dependency near the other `@Environment` declarations:

```swift
    @Environment(KarmaNotificationService.self) private var karma
```

In `save()`, in the success branch, immediately before `onCreated(response.pebbleId)`:

```swift
            karma.notifyEarned(amount: response.karmaDelta ?? 0, reason: .pebbleCreated)
            onCreated(response.pebbleId)
            dismiss()
```

Leave the soft-success `onCreated(pebbleId)` branch unchanged (no delta available there).

- [ ] **Step 2: Inject the service + fire in `EditPebbleSheet`**

In `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`, add near the other `@Environment` declarations:

```swift
    @Environment(KarmaNotificationService.self) private var karma
```

In `save()`, in the success branch, immediately before `onSaved()`:

```swift
            self.renderSvg = response.renderSvg ?? self.renderSvg
            karma.notifyEarned(amount: response.karmaDelta ?? 0, reason: .pebbleEnriched)
            onSaved()
            dismiss()
```

Leave the soft-success branch unchanged.

- [ ] **Step 3: Fix previews if they construct these sheets without the service**

If `#Preview` blocks in either file (or in `PathView.swift`) render these sheets, add `.environment(KarmaNotificationService())` so previews compile. Build will tell you which.

- [ ] **Step 4: Build + test**

Run: `npm run build --workspace=@pbbls/ios`
Expected: BUILD SUCCEEDED.
Run: `npm run test --workspace=@pbbls/ios`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift \
        apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift
git commit -m "feat(core): fire karma flash on pebble create and enrich (#505)"
```

**Checkpoint:** At this point the feature works end-to-end on ALL hardware via the in-app capsule (haptic + sound + capsule + VoiceOver + correct server delta). Phases 6–7 add the native Dynamic Island on capable devices.

---

## Phase 6 — Live Activity (Dynamic Island)

### Task 10: Shared `KarmaActivityAttributes`

**Files:**
- Create: `apps/ios/Shared/KarmaActivityAttributes.swift`

- [ ] **Step 1: Implement**

Create `apps/ios/Shared/KarmaActivityAttributes.swift`:

```swift
import ActivityKit
import Foundation

/// Shared between the app and the widget extension (compiled into both). The
/// karma amount + reason live in the dynamic `ContentState`; there are no
/// static attributes. No App Group is needed — ActivityKit delivers
/// `ContentState` directly through request/update/end for this local,
/// non-push use case.
struct KarmaActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var amount: Int
        var reasonRawValue: String
    }
}
```

Note: `reason` is stored as its raw string (not the `KarmaReason` enum) so `ContentState` stays `Codable` without leaking app-target types into the shared file. The widget maps it back to a label with its own localized strings (Task 11/13).

- [ ] **Step 2: Commit** (target wiring happens in Task 11)

```bash
git add apps/ios/Shared/KarmaActivityAttributes.swift
git commit -m "feat(core): shared KarmaActivityAttributes for Live Activity (#505)"
```

### Task 11: `PebblesWidget` extension target (project.yml + bundle + assets)

**Files:**
- Create: `apps/ios/PebblesWidget/PebblesWidgetBundle.swift`
- Create: `apps/ios/PebblesWidget/KarmaActivityWidget.swift`
- Create: `apps/ios/PebblesWidget/Info.plist`
- Create: `apps/ios/PebblesWidget/Assets.xcassets/Contents.json`
- Create: `apps/ios/PebblesWidget/Assets.xcassets/AccentPrimary.colorset/Contents.json`
- Modify: `apps/ios/project.yml`

Per spec risk #5, duplicate the single `AccentPrimary` colorset into the widget's own asset catalog rather than sharing `.xcassets` across targets.

- [ ] **Step 1: Add the widget target + Shared sources to `project.yml`**

In `apps/ios/project.yml`, under `targets:`, add the `Shared` folder to the `Pebbles` target sources (so the attributes compile into the app too):

```yaml
  Pebbles:
    type: application
    platform: iOS
    deploymentTarget: "17.0"
    sources:
      - path: Pebbles
      - path: Shared
```

Then add the new target after `Pebbles` (before `PebblesTests`):

```yaml
  PebblesWidget:
    type: app-extension
    platform: iOS
    deploymentTarget: "17.0"
    sources:
      - path: PebblesWidget
      - path: Shared
    configFiles:
      Debug: Config/Secrets.xcconfig
      Release: Config/Secrets.xcconfig
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: app.pbbls.ios.widget
        PRODUCT_NAME: PebblesWidget
        INFOPLIST_FILE: PebblesWidget/Info.plist
        CODE_SIGN_STYLE: Automatic
        SWIFT_EMIT_LOC_STRINGS: YES
        LOCALIZATION_PREFERS_STRING_CATALOGS: YES
        TARGETED_DEVICE_FAMILY: "1"
```

Add the embed dependency to the `Pebbles` target's `dependencies:` list:

```yaml
    dependencies:
      - package: Supabase
        product: Supabase
      - package: SVGView
        product: SVGView
      - package: RiveRuntime
        product: RiveRuntime
      - target: PebblesWidget
        embed: true
```

- [ ] **Step 2: Widget Info.plist**

Create `apps/ios/PebblesWidget/Info.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDisplayName</key>
    <string>Pebbles</string>
    <key>NSExtension</key>
    <dict>
        <key>NSExtensionPointIdentifier</key>
        <string>com.apple.widgetkit-extension</string>
    </dict>
</dict>
</plist>
```

- [ ] **Step 3: Widget asset catalog + AccentPrimary colorset**

Create `apps/ios/PebblesWidget/Assets.xcassets/Contents.json`:

```json
{ "info": { "author": "xcode", "version": 1 } }
```

Copy the app's AccentPrimary colorset into the widget catalog:

Run: `cp -R apps/ios/Pebbles/Resources/Assets.xcassets/AccentPrimary.colorset apps/ios/PebblesWidget/Assets.xcassets/AccentPrimary.colorset`
Expected: the colorset (with its `Contents.json`) now exists under the widget catalog.

- [ ] **Step 4: Widget bundle entry point**

Create `apps/ios/PebblesWidget/PebblesWidgetBundle.swift`:

```swift
import SwiftUI
import WidgetKit

@main
struct PebblesWidgetBundle: WidgetBundle {
    var body: some Widget {
        KarmaActivityWidget()
    }
}
```

- [ ] **Step 5: Generate + build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: BUILD SUCCEEDED with two app-side targets embedding the extension. (This is the first real-toolchain validation of the widget target shape — spec risk #6. If xcodegen or the build rejects the target shape, fix here before proceeding.)

Note: `KarmaActivityWidget` does not exist yet — do Step 4/5 together with Task 12, or stub `KarmaActivityWidget` as an empty `Widget` first. To keep the build green, create the real widget in Task 12 and run the build there.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/project.yml apps/ios/PebblesWidget/
git commit -m "feat(core): add PebblesWidget app-extension target (#505)"
```

### Task 12: `KarmaActivityWidget` UI + `KarmaLiveActivityController` + routing wire-up

**Files:**
- Create: `apps/ios/PebblesWidget/KarmaActivityWidget.swift`
- Create: `apps/ios/PebblesWidget/Localizable.xcstrings`
- Create: `apps/ios/Pebbles/Features/Karma/KarmaLiveActivityController.swift`
- Modify: `apps/ios/Pebbles/PebblesApp.swift`

- [ ] **Step 1: Widget UI (Lock Screen + Dynamic Island)**

Create `apps/ios/PebblesWidget/KarmaActivityWidget.swift`:

```swift
import ActivityKit
import SwiftUI
import WidgetKit

/// Live Activity for the "+N karma" flash. On Dynamic Island hardware the
/// system momentarily expands on request/update (the Opal grow), then settles
/// to the compact `+N`; the controller ends it after ~2.5s. The Lock Screen
/// view is kept minimal because we end immediately and it is rarely seen.
struct KarmaActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: KarmaActivityAttributes.self) { context in
            // Lock Screen / banner presentation.
            HStack(spacing: 8) {
                Image(systemName: "sparkle").foregroundStyle(Color("AccentPrimary"))
                Text("+\(context.state.amount) karma").font(.headline)
                Spacer()
            }
            .padding()
            .activityBackgroundTint(Color.black.opacity(0.2))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: "sparkle").foregroundStyle(Color("AccentPrimary"))
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("+\(context.state.amount)").font(.title3.bold())
                }
                DynamicIslandExpandedRegion(.center) {
                    Text("karma").font(.caption).foregroundStyle(.secondary)
                }
            } compactLeading: {
                Image(systemName: "sparkle").foregroundStyle(Color("AccentPrimary"))
            } compactTrailing: {
                Text("+\(context.state.amount)").font(.caption.bold())
            } minimal: {
                Image(systemName: "sparkle").foregroundStyle(Color("AccentPrimary"))
            }
        }
    }
}
```

- [ ] **Step 2: Widget's own String Catalog**

Create `apps/ios/PebblesWidget/Localizable.xcstrings` with en/fr for the widget's literal strings (String Catalogs are not shared across the app/extension boundary). Minimal seed (Xcode will manage the format; ensure `en` and `fr` values exist for "karma"):

```json
{
  "sourceLanguage" : "en",
  "strings" : {
    "karma" : {
      "localizations" : {
        "en" : { "stringUnit" : { "state" : "translated", "value" : "karma" } },
        "fr" : { "stringUnit" : { "state" : "translated", "value" : "karma" } }
      }
    }
  },
  "version" : "1.0"
}
```

Add it to the widget's `sources` implicitly (it lives under `PebblesWidget/`, already globbed). Confirm no `New`/`Stale` states in Xcode.

- [ ] **Step 3: Live Activity controller**

Create `apps/ios/Pebbles/Features/Karma/KarmaLiveActivityController.swift`:

```swift
import ActivityKit
import Foundation
import os

/// Drives the transient karma Live Activity: request → (system auto-expands)
/// → end(.immediate) after ~2.5s. A second earn within the window UPDATES the
/// running activity in place (replace-not-stack) and resets the dismiss timer.
@MainActor
final class KarmaLiveActivityController: KarmaLiveActivityPresenting {
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "karma-activity")
    private var current: Activity<KarmaActivityAttributes>?
    private var dismissTask: Task<Void, Never>?

    /// Tune-on-device (spec risk #7).
    private let visibleDuration: Duration = .milliseconds(2500)

    func present(_ content: KarmaEarnedContent) async -> Bool {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return false }

        let state = KarmaActivityAttributes.ContentState(
            amount: content.amount,
            reasonRawValue: content.reason.rawValue
        )

        if let activity = current {
            // Replace-not-stack: update the running activity + reset the timer.
            await activity.update(ActivityContent(state: state, staleDate: nil))
            scheduleDismiss()
            return true
        }

        do {
            current = try Activity.request(
                attributes: KarmaActivityAttributes(),
                content: ActivityContent(state: state, staleDate: nil),
                pushType: nil   // local only; PushType? optional, no `.none` case
            )
            scheduleDismiss()
            return true
        } catch {
            logger.error("Activity.request failed: \(error.localizedDescription, privacy: .public)")
            return false
        }
    }

    private func scheduleDismiss() {
        dismissTask?.cancel()
        dismissTask = Task { [weak self, visibleDuration] in
            try? await Task.sleep(for: visibleDuration)
            guard !Task.isCancelled else { return }
            await self?.end()
        }
    }

    private func end() async {
        guard let activity = current else { return }
        await activity.end(nil, dismissalPolicy: .immediate)
        current = nil
    }
}
```

- [ ] **Step 4: Inject the controller into the service**

In `apps/ios/Pebbles/PebblesApp.swift`, wire the controller as the service's presenter after both are constructed. In `init()`, after `self._karma = State(...)`:

```swift
        let liveActivity = KarmaLiveActivityController()
        karma.liveActivityPresenter = liveActivity
```

Note: `karma` here refers to the local created before assigning to `_karma`. Restructure the `init` so a local `let karma = KarmaNotificationService()` is created first, its `liveActivityPresenter` is set, then `self._karma = State(initialValue: karma)` — mirroring how `supabase` is created as a local first. The controller is retained by the service via a strong reference; change `weak var liveActivityPresenter` to `var liveActivityPresenter` in `KarmaNotificationService` since nothing else owns the controller.

- [ ] **Step 5: Build + test**

Run: `npm run build --workspace=@pbbls/ios`
Expected: BUILD SUCCEEDED (widget + app).
Run: `npm run test --workspace=@pbbls/ios`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/PebblesWidget/KarmaActivityWidget.swift \
        apps/ios/PebblesWidget/Localizable.xcstrings \
        apps/ios/Pebbles/Features/Karma/KarmaLiveActivityController.swift \
        apps/ios/Pebbles/Features/Karma/KarmaNotificationService.swift \
        apps/ios/Pebbles/PebblesApp.swift
git commit -m "feat(ui): karma Live Activity in the Dynamic Island (#505)"
```

---

## Phase 7 — Localization, verification, and map

### Task 13: App-side localization (en/fr)

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 1: Add/confirm strings**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode. The build auto-extracts these literals used in this feature: `"Pebble created"`, `"Pebble enriched"` (from `KarmaReason.label`), `"+%lld karma"` / `"karma"` and the accessibility label `"Earned %lld karma, %@"` (from `KarmaEarnedCapsule`). For each, fill the French column verbatim from web (`apps/web/lib/i18n/messages/fr.json` → `wallet.reason`):
- `Pebble created` → `Caillou créé`
- `Pebble enriched` → `Caillou enrichi`
- `karma` → `karma`
- `+%lld karma` → `+%lld karma`
- accessibility label → French equivalent, e.g. `%1$lld karma gagné, %2$@`

- [ ] **Step 2: Verify catalog state**

Per `apps/ios/CLAUDE.md`: confirm no entry is in `New` or `Stale`, and every row has both `en` and `fr` values.

- [ ] **Step 3: Build + test**

Run: `npm run build --workspace=@pbbls/ios` then `npm run test --workspace=@pbbls/ios`
Expected: BUILD SUCCEEDED, tests PASS (including the existing `LocalizationTests`).

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ui): en/fr strings for karma flash (#505)"
```

### Task 14: Lint + Arkaik map

**Files:**
- Modify: `docs/arkaik/bundle.json`

- [ ] **Step 1: Lint the workspace**

Run: `npm run lint --workspace=@pbbls/ios`
Expected: no new violations. (`EditPebbleSheet.save` already has a `swiftlint:disable function_body_length`; if the added line trips any length rule elsewhere, extract per existing patterns — do not blanket-disable.)

- [ ] **Step 2: Update Arkaik**

This adds a new client capability (a Live Activity / widget extension surface) but no new user-navigable screen or route. Use the `arkaik` skill to add the widget extension + the karma-flash interaction as a node/edge in `docs/arkaik/bundle.json`, and note the `compose-pebble*` endpoints now return `karma_delta`. Keep it minimal — this is a delight overlay, not a new view.

- [ ] **Step 3: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "docs: map karma flash + widget extension in Arkaik (#505)"
```

### Task 15: On-device verification (manual — cannot be unit-tested)

These are the spec's device-verification gates. Do them on real hardware before opening the PR for review; record results in the PR body.

- [ ] Create a pebble on an iPhone 14 Pro+ → the Dynamic Island momentarily expands (the "grow"), settles to compact `+N`, and auto-dismisses after ~2.5s. If the grow is too subtle, add an `AlertConfiguration` to the `Activity.request` (spec risk #1) and re-verify.
- [ ] Earn twice within the window → the running activity UPDATES in place (no second activity stacks) and the dismiss timer resets.
- [ ] After dismissal, confirm nothing lingers on the Lock Screen (`end(.immediate)` worked).
- [ ] Create/enrich on a non-DI device (or Simulator iPhone SE) or with Live Activities disabled in Settings → the in-app capsule shows instead; no silent confirmation.
- [ ] Haptic fires on both paths; sound plays and RESPECTS the ring/silent switch (toggle the hardware switch and re-test). Audition the system sound ID and pick the best of `1103`/`1013`/`1025`/`1057` by ear; update `AudioService.karmaEarnedSoundID`.
- [ ] VoiceOver announces the earn (capsule path).
- [ ] Switch device language to French → capsule and widget render French copy correctly.
- [ ] Delete a pebble and enrich-with-no-karma-change → NO flash (negative/null delta stays silent).
- [ ] Verify `+N` matches the karma actually awarded (compare against `PathBottomBar` before/after).

- [ ] **Commit** any tweaks from verification (e.g. sound ID, `AlertConfiguration`, timing):

```bash
git add -A
git commit -m "chore(ios): device-verification tweaks for karma flash (#505)"
```

---

## Self-Review (completed by plan author)

**Spec coverage:**
- D1 server delta / no migration → Tasks 1–3. ✓
- D2 `hasDynamicIsland` fork → Tasks 5, 7, and routing in 4. ✓
- D3 transient Live Activity, Opal way → Tasks 10–12, verify in 15. ✓
- Trigger layer (`KarmaNotificationService`, `KarmaReason`, `amount>0` guard) → Tasks 4, 7, 9. ✓
- Presentation A (Live Activity, shared attributes, no App Group, replace-not-stack, end(.immediate)) → Tasks 10–12. ✓
- Presentation B (capsule from RootView, spring, tap-dismiss, VoiceOver) → Task 8. ✓
- Haptics `.sensoryFeedback` + sound respecting silent switch → Tasks 6, 8, 15. ✓
- Localization en/fr + widget catalog → Tasks 12 (widget), 13 (app). ✓
- Testing (pure logic unit-tested; lifecycle device-manual) → Tasks 3–5, 7, 15. ✓
- Out of scope respected (no wallet screen, no extra reasons, no App Group). ✓

**Placeholder scan:** No TBD/TODO; every code step shows concrete code. Sound ID and dismiss timing are intentionally flagged tune-on-device values (spec risks), not placeholders.

**Type consistency:** `KarmaEarnedContent(amount:reason:)`, `KarmaReason` (`.pebbleCreated`/`.pebbleEnriched`), `karmaPresentationDecision(amount:hasDynamicIsland:activitiesEnabled:)`, `KarmaPresentation` (`.none`/`.capsule`/`.liveActivity`), `KarmaNotificationService.notifyEarned/activeCapsule/hapticTrigger/dismissCapsule/liveActivityPresenter`, `KarmaLiveActivityPresenting.present`, `KarmaActivityAttributes.ContentState(amount:reasonRawValue:)`, `ComposePebbleResponse.karmaDelta`, `DeviceCapabilities.isDynamicIslandModel/hasDynamicIsland` — used consistently across tasks.

**Note on Task 11/12 build ordering:** Task 11 Step 5 will not build until `KarmaActivityWidget` exists (Task 12 Step 1). Execute Task 11 and Task 12 as a pair, running the build at Task 12 Step 5.
