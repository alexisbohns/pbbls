# iOS Step-by-Step Record Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the all-at-once `CreatePebbleSheet` with a full-screen, one-action-per-step record flow as the default iOS composer, with a haptic on every tap.

**Architecture:** An `@Observable` `RecordFlowModel` owns the draft, the current step, and every interaction — which is what makes "haptic on every tap" structural rather than a discipline, and makes gating/back/skip/resume testable without rendering a view. Eleven step views render through one `RecordStepScaffold`. The existing picker sheets give up their bodies to shared `…PickerContent` views so the sheet and the flow render the same grids with different commit semantics. The publish path and the M47 draft glue are extracted into `PebblePublisher` and `ComposerDraftCoordinator` so the two composers cannot drift apart.

**Tech Stack:** SwiftUI (iOS 17, `@Observable`, no backports), Swift Testing (`@Suite`/`@Test`/`#expect`, never XCTest), Supabase Swift, `os.Logger`, UIKit feedback generators.

**Design:** `docs/superpowers/specs/2026-08-23-ios-record-flow-design.md` — decisions are referenced below as D1…D10.

**Issue:** #723 · Milestone M58 · Branch `feat/723-ios-record-flow`

---

## Conventions for every task

- **iOS 17 APIs only.** No `if #available`, no backports.
- **`@Observable`, never `ObservableObject`.**
- **Swift Testing, never XCTest.** New tests use `@Suite` / `@Test` / `#expect`.
- **`os.Logger`, never `print`.** Every error path logs.
- **User-facing strings** go through `Text`/`Button`/`LocalizedStringResource` so `SWIFT_EMIT_LOC_STRINGS` extracts them. Brand words use `Text(verbatim:)`.
- **No `any`-equivalent shortcuts:** no force-unwraps outside tests, no type erasure without a reason.
- **Never hand-edit `.xcodeproj`.** `project.yml` globs `Pebbles/`, so new files are picked up by `xcodegen generate` automatically — which `npm run build` and `npm run test` both run first.

**Stale-build hazard.** After `xcodegen generate`, `xcodebuild` can serve stale objects and report a green build for code that does not compile. Before trusting any build or test result in this plan:

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build
```

**`-only-testing:` takes the type name, not the `@Suite` display name.** A
suite declared `@Suite("TapHaptics") struct TapHapticsTests` is addressed as
`PebblesTests/TapHapticsTests`. Getting this wrong does not error — xcodebuild
runs zero tests and still prints `** TEST SUCCEEDED **`. Always confirm the
run reported the tests you expected:

```bash
… -only-testing:PebblesTests/<TypeName> 2>&1 | grep -E 'Test "|Suite "|✘|error:'
```

An empty grep means nothing ran, not that everything passed.

**Running one suite:**

```bash
cd apps/ios && xcodegen generate && xcodebuild test \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests/<SuiteName> 2>&1 | tail -30
```

---

## File Structure

**Created:**

| File | Responsibility |
|---|---|
| `Pebbles/Services/TapHaptics.swift` | Four UI tap flavors over the UIKit generators |
| `Pebbles/Features/PebbleMedia/ExifCaptureDate.swift` | Pure `Data` → capture `Date?` |
| `Pebbles/Features/Record/RecordStep.swift` | Step enum, optionality, dot index, neighbors |
| `Pebbles/Features/Record/RecordFlowModel.swift` | Draft + step + every interaction + haptics |
| `Pebbles/Features/Record/RecordFlowView.swift` | Cover, chrome host, dialogs, publish orchestration |
| `Pebbles/Features/Record/RecordFlowChrome.swift` | `‹ back · dots · ✕` |
| `Pebbles/Features/Record/RecordStepScaffold.swift` | Shared step geometry: title, content slot, button slot |
| `Pebbles/Features/Record/Steps/*.swift` (11 files) | One view per step, content only |
| `Pebbles/Features/Path/PebblePublisher.swift` | `compose-pebble` invoke, soft success, error mapping |
| `Pebbles/Features/Path/ComposerDraftCoordinator.swift` | Server draft + local snapshot lifecycle |
| `Pebbles/Features/Path/ValencePickerContent.swift` | Valence grid, presentation only |
| `Pebbles/Features/Path/EmotionPickerContent.swift` | Emotion category grid, presentation only |
| `Pebbles/Features/Path/SoulPickerContent.swift` | Soul grid, presentation only |
| `Pebbles/Features/Path/DomainPickerContent.swift` | Domain rows: glyph, name, description |
| `Pebbles/Features/Glyph/Views/GlyphPickerContent.swift` | Glyph grid + tabs, presentation only |

**Modified:**

| File | Change |
|---|---|
| `Pebbles/Features/Path/Models/Domain.swift` | `strokes` / `viewBox`, both optional and defaulted |
| `Pebbles/Features/Path/Models/Domain+Localized.swift` | `localizedLabel` |
| `Pebbles/Services/ReferenceDataService.swift` | `domains` reads `v_domains_with_glyph` |
| `Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift` | `pickedCaptureDate` |
| `Pebbles/Features/Path/CreatePebbleSheet.swift` | Delegates publish to `PebblePublisher` |
| `Pebbles/Features/Path/CreatePebbleSheet+Drafts.swift` | Delegates to `ComposerDraftCoordinator` |
| `Pebbles/Features/Path/ValencePickerSheet.swift`, `EmotionPickerSheet.swift`, `SoulPickerSheet.swift`, `Features/Glyph/Views/GlyphPickerSheet.swift` | Render their extracted content |
| `Pebbles/Features/Karma/KarmaNotificationService.swift` | `notifyEarned(presentsCapsule:)` |
| `Pebbles/Features/Path/PathView.swift` | `+` opens the flow, long-press opens the sheet |
| `Pebbles/Resources/Localizable.xcstrings` | 18 × 2 `domain.<slug>.label` + flow copy |
| `docs/arkaik/bundle.json` | View node + edge + journal event |

---

## Task 1: TapHaptics

**Files:**
- Create: `apps/ios/Pebbles/Services/TapHaptics.swift`
- Test: `apps/ios/PebblesTests/Features/Record/TapHapticsTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/Features/Record/TapHapticsTests.swift`:

```swift
import Testing
@testable import Pebbles

@Suite("TapHaptics", .serialized)
@MainActor
struct TapHapticsTests {

    @Test("play records the flavor it was asked for")
    func recordsFlavor() {
        TapHaptics.resetForTesting()
        TapHaptics.play(.selection)
        #expect(TapHaptics.playCount == 1)
        #expect(TapHaptics.lastPlayed == .selection)
    }

    @Test("every flavor is playable and tallied")
    func everyFlavorPlays() {
        TapHaptics.resetForTesting()
        for flavor in [TapHaptic.selection, .advance, .success, .warning] {
            TapHaptics.play(flavor)
        }
        #expect(TapHaptics.playCount == 4)
        #expect(TapHaptics.lastPlayed == .warning)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests/TapHapticsTests 2>&1 | tail -20
```

Expected: compile failure — `cannot find 'TapHaptics' in scope`.

- [ ] **Step 3: Write the implementation**

Create `apps/ios/Pebbles/Services/TapHaptics.swift`:

```swift
import UIKit

/// The four tap flavors the record flow uses.
enum TapHaptic {
    /// Picking a tile, toggling a soul — the most common tap in the flow.
    case selection
    /// Step changed, forward or back.
    case advance
    /// The pebble published.
    case success
    /// A blocked advance or a failed publish.
    case warning
}

/// Thin wrapper over the UIKit feedback generators, used for every tap in the
/// record flow.
///
/// Deliberately not `HapticsService`: that type owns a `CHHapticEngine` and
/// bespoke waveform-derived patterns for karma and the glyph slider. UI taps
/// want the system generators instead — they respect the user's haptic
/// settings, need no warm engine on every step, and carry the texture users
/// already know from the rest of iOS.
///
/// Generators are cached rather than constructed per call: `prepare()` warms
/// the Taptic Engine, and a generator that is created and released for every
/// tap never gets the benefit.
@MainActor
enum TapHaptics {

    private static let selectionGenerator = UISelectionFeedbackGenerator()
    private static let impactGenerator = UIImpactFeedbackGenerator(style: .light)
    private static let notificationGenerator = UINotificationFeedbackGenerator()

    #if DEBUG
    /// Test-only tally. Lets a suite assert that an interaction produced
    /// feedback without reaching into UIKit or a mock protocol.
    private(set) static var playCount: Int = 0
    private(set) static var lastPlayed: TapHaptic?

    static func resetForTesting() {
        playCount = 0
        lastPlayed = nil
    }
    #endif

    /// Warms the Taptic Engine. Called when the flow appears so the first tap
    /// is as sharp as the tenth.
    static func prepare() {
        selectionGenerator.prepare()
        impactGenerator.prepare()
        notificationGenerator.prepare()
    }

    static func play(_ haptic: TapHaptic) {
        #if DEBUG
        playCount += 1
        lastPlayed = haptic
        #endif

        switch haptic {
        case .selection:
            selectionGenerator.selectionChanged()
            selectionGenerator.prepare()
        case .advance:
            impactGenerator.impactOccurred()
            impactGenerator.prepare()
        case .success:
            notificationGenerator.notificationOccurred(.success)
        case .warning:
            notificationGenerator.notificationOccurred(.warning)
        }
    }
}
```

`TapHaptic` needs `Equatable` for the `#expect` comparisons — a payload-free enum gets it synthesized, so no annotation is required.

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests/TapHapticsTests 2>&1 | tail -20
```

Expected: `Test Suite 'TapHaptics' passed` — 2 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Services/TapHaptics.swift apps/ios/PebblesTests/Features/Record/TapHapticsTests.swift
git commit -m "feat(ios): tap haptics over the UIKit feedback generators (M58)

Refs #723"
```

---

## Task 2: RecordStep

**Files:**
- Create: `apps/ios/Pebbles/Features/Record/RecordStep.swift`
- Test: `apps/ios/PebblesTests/Features/Record/RecordStepTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/Features/Record/RecordStepTests.swift`:

```swift
import Testing
@testable import Pebbles

@Suite("RecordStep")
struct RecordStepTests {

    @Test("steps run photo → success in the designed order")
    func order() {
        #expect(RecordStep.allCases == [
            .photo, .when, .name, .valence, .emotion, .domain,
            .souls, .collection, .glyph, .privacy, .success
        ])
    }

    @Test("photo, souls, collection and glyph are the optional steps")
    func optionality() {
        let optional = RecordStep.allCases.filter(\.isOptional)
        #expect(optional == [.photo, .souls, .collection, .glyph])
    }

    @Test("ten steps are counted by the dots; success is not")
    func dotCount() {
        #expect(RecordStep.counted.count == 10)
        #expect(RecordStep.counted.contains(.success) == false)
        #expect(RecordStep.success.dotIndex == nil)
        #expect(RecordStep.photo.dotIndex == 0)
        #expect(RecordStep.privacy.dotIndex == 9)
    }

    @Test("neighbors chain the whole flow and terminate at both ends")
    func neighbors() {
        #expect(RecordStep.photo.previous == nil)
        #expect(RecordStep.photo.next == .when)
        #expect(RecordStep.privacy.next == .success)
        #expect(RecordStep.success.next == nil)
        #expect(RecordStep.when.previous == .photo)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests/RecordStepTests 2>&1 | tail -20
```

Expected: compile failure — `cannot find 'RecordStep' in scope`.

- [ ] **Step 3: Write the implementation**

Create `apps/ios/Pebbles/Features/Record/RecordStep.swift`:

```swift
import Foundation

/// The eleven screens of the record flow, in order (D2).
///
/// The order carries three deliberate dependencies:
/// - `photo` before `when`, so the date step can arrive pre-filled from the
///   photo's EXIF `DateTimeOriginal` instead of mutating under the user.
/// - `valence` before `emotion`, so `EmotionCategoryOrdering.order(for:)` has
///   a valence to order the categories by. In the old form the two rows sat
///   side by side and the ordering depended on which the user opened first.
/// - `privacy` last, against the publish button, because the grade is the
///   decision most coupled to "am I ready for other people to see this".
///
/// `success` is terminal: no dot, no back, no close — only the exit button.
enum RecordStep: Int, CaseIterable, Identifiable, Hashable {
    case photo
    case when
    case name
    case valence
    case emotion
    case domain
    case souls
    case collection
    case glyph
    case privacy
    case success

    var id: Int { rawValue }

    /// Steps the user may pass without answering. Everything else gates.
    var isOptional: Bool {
        switch self {
        case .photo, .souls, .collection, .glyph:
            return true
        case .when, .name, .valence, .emotion, .domain, .privacy, .success:
            return false
        }
    }

    /// The steps the progress dots represent.
    static var counted: [RecordStep] {
        allCases.filter { $0 != .success }
    }

    /// 0-based dot index, or nil for the uncounted terminal step.
    var dotIndex: Int? {
        self == .success ? nil : rawValue
    }

    var next: RecordStep? { RecordStep(rawValue: rawValue + 1) }

    var previous: RecordStep? {
        rawValue == 0 ? nil : RecordStep(rawValue: rawValue - 1)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests/RecordStepTests 2>&1 | tail -20
```

Expected: `Test Suite 'RecordStep' passed` — 4 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Record/RecordStep.swift apps/ios/PebblesTests/Features/Record/RecordStepTests.swift
git commit -m "feat(ios): record flow step enum (M58)

Refs #723"
```

---
## Task 3: RecordFlowModel

The state machine. Everything interesting about the flow — gating, back, skip labels, resume, the name clamp, and which haptic fires when — lives here so it is testable without rendering a view (D4).

**Files:**
- Create: `apps/ios/Pebbles/Features/Record/RecordFlowModel.swift`
- Test: `apps/ios/PebblesTests/Features/Record/RecordFlowModelTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/Features/Record/RecordFlowModelTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@MainActor
private func makeModel() -> (RecordFlowModel, HapticRecorder) {
    let recorder = HapticRecorder()
    let model = RecordFlowModel(haptic: { recorder.played.append($0) })
    return (model, recorder)
}

/// Collects the haptics a model fires so tests can assert the mapping without
/// touching UIKit.
@MainActor
final class HapticRecorder {
    var played: [TapHaptic] = []
}

@Suite("RecordFlowModel — gating")
@MainActor
struct RecordFlowModelGatingTests {

    @Test("a mandatory step does not advance while unanswered, and warns")
    func mandatoryStepGates() {
        let (model, recorder) = makeModel()
        model.go(to: .valence)
        recorder.played.removeAll()

        model.advance()

        #expect(model.step == .valence)
        #expect(recorder.played == [.warning])
    }

    @Test("an optional step advances with nothing chosen")
    func optionalStepPasses() {
        let (model, recorder) = makeModel()
        model.go(to: .souls)
        recorder.played.removeAll()

        model.advance()

        #expect(model.step == .collection)
        #expect(recorder.played == [.advance])
    }

    @Test("the name step gates on non-whitespace text")
    func nameGates() {
        let (model, _) = makeModel()
        model.go(to: .name)

        model.setName("   ")
        model.advance()
        #expect(model.step == .name)

        model.setName("Ferry to Ithaca")
        model.advance()
        #expect(model.step == .valence)
    }

    @Test("when and privacy are pre-seeded, so they never gate")
    func preSeededStepsPass() {
        let (model, _) = makeModel()
        model.go(to: .when)
        model.advance()
        #expect(model.step == .name)

        model.go(to: .privacy)
        #expect(model.isAnswered)
    }
}

@Suite("RecordFlowModel — navigation")
@MainActor
struct RecordFlowModelNavigationTests {

    @Test("back preserves the answer already given")
    func backPreservesAnswers() {
        let (model, _) = makeModel()
        model.go(to: .valence)
        model.select(valence: .highlightMedium)
        #expect(model.step == .emotion)

        model.back()

        #expect(model.step == .valence)
        #expect(model.draft.valence == .highlightMedium)
    }

    @Test("back stops at the first step")
    func backTerminates() {
        let (model, _) = makeModel()
        #expect(model.step == .photo)
        model.back()
        #expect(model.step == .photo)
    }

    @Test("success is terminal — neither advance nor back moves off it")
    func successIsTerminal() {
        let (model, _) = makeModel()
        model.succeed(with: ComposePebbleResponse(
            pebbleId: UUID(), renderSvg: nil, renderVersion: nil, karmaDelta: 3
        ))
        #expect(model.step == .success)

        model.advance()
        model.back()

        #expect(model.step == .success)
    }
}

@Suite("RecordFlowModel — selection")
@MainActor
struct RecordFlowModelSelectionTests {

    @Test("a tile pick commits and advances on one selection haptic")
    func tilePickAdvances() {
        let (model, recorder) = makeModel()
        model.go(to: .domain)
        recorder.played.removeAll()
        let domainId = UUID()

        model.select(domainId: domainId)

        #expect(model.draft.domainId == domainId)
        #expect(model.step == .souls)
        // One tap, one buzz: a pick that also advances must not fire twice.
        #expect(recorder.played == [.selection])
    }

    @Test("souls toggle without advancing, and toggle off again")
    func soulsToggle() {
        let (model, _) = makeModel()
        model.go(to: .souls)
        let first = UUID()
        let second = UUID()

        model.toggleSoul(first)
        model.toggleSoul(second)
        #expect(model.draft.soulIds == [first, second])
        #expect(model.step == .souls)

        model.toggleSoul(first)
        #expect(model.draft.soulIds == [second])
    }

    @Test("privacy selects without advancing — publish is the step's action")
    func privacyDoesNotAdvance() {
        let (model, _) = makeModel()
        model.go(to: .privacy)

        model.select(visibility: .public)

        #expect(model.draft.visibility == .public)
        #expect(model.step == .privacy)
    }

    @Test("the name is clamped to 40 characters")
    func nameIsClamped() {
        let (model, _) = makeModel()
        model.setName(String(repeating: "a", count: 55))
        #expect(model.draft.name.count == RecordFlowModel.nameLimit)
        #expect(model.draft.name.count == 40)
    }
}

@Suite("RecordFlowModel — optional button label")
@MainActor
struct RecordFlowModelOptionalButtonTests {

    @Test("an untouched optional step offers Skip; a filled one offers Done")
    func labelFollowsAnswer() {
        let (model, _) = makeModel()
        model.go(to: .collection)
        #expect(model.optionalButtonIsSkip)

        model.draft.collectionId = UUID()
        #expect(model.optionalButtonIsSkip == false)
    }
}

@Suite("RecordFlowModel — resume")
@MainActor
struct RecordFlowModelResumeTests {

    /// A draft with a name but no valence: the first gap is valence, and the
    /// steps before it must not be re-asked.
    @Test("resume lands on the first unanswered mandatory step")
    func resumeLandsOnFirstGap() {
        let (model, _) = makeModel()
        var payload = PebbleDraftPayload()
        payload.name = "Ferry to Ithaca"

        model.resume(from: payload, known: .init(soulIds: [], collectionIds: []))

        #expect(model.draft.name == "Ferry to Ithaca")
        #expect(model.step == .valence)
    }

    @Test("a fully answered draft resumes straight to privacy")
    func completeDraftResumesToPrivacy() {
        let (model, _) = makeModel()
        var payload = PebbleDraftPayload()
        payload.name = "Ferry to Ithaca"
        payload.emotionId = UUID()
        payload.domainIds = [UUID()]
        payload.positiveness = 1
        payload.intensity = 2

        model.resume(from: payload, known: .init(soulIds: [], collectionIds: []))

        #expect(model.step == .privacy)
    }

    @Test("skipped optional steps are never treated as gaps")
    func optionalStepsAreNotGaps() {
        let (model, _) = makeModel()
        var payload = PebbleDraftPayload()
        payload.name = "Ferry to Ithaca"
        payload.emotionId = UUID()
        payload.domainIds = [UUID()]
        payload.positiveness = 1
        payload.intensity = 2
        // No souls, no collection, no glyph, no snap — all legitimately skipped.

        model.resume(from: payload, known: .init(soulIds: [], collectionIds: []))

        #expect(model.step == .privacy)
    }
}

@Suite("RecordFlowModel — publish")
@MainActor
struct RecordFlowModelPublishTests {

    @Test("a successful publish celebrates and moves to the success step")
    func publishSucceeds() {
        let (model, recorder) = makeModel()
        model.go(to: .privacy)
        model.beginPublish()
        #expect(model.isPublishing)
        recorder.played.removeAll()

        let pebbleId = UUID()
        model.succeed(with: ComposePebbleResponse(
            pebbleId: pebbleId, renderSvg: "<svg/>", renderVersion: "v1", karmaDelta: 5
        ))

        #expect(model.isPublishing == false)
        #expect(model.step == .success)
        #expect(model.published?.pebbleId == pebbleId)
        #expect(model.published?.karmaDelta == 5)
        #expect(recorder.played == [.success])
    }

    @Test("a failed publish stays on privacy, warns, and keeps the draft")
    func publishFails() {
        let (model, recorder) = makeModel()
        model.go(to: .privacy)
        model.setName("Ferry to Ithaca")
        model.beginPublish()
        recorder.played.removeAll()

        model.fail("Photo upload failed. Retry or remove it.")

        #expect(model.step == .privacy)
        #expect(model.isPublishing == false)
        #expect(model.publishError == "Photo upload failed. Retry or remove it.")
        #expect(model.draft.name == "Ferry to Ithaca")
        #expect(recorder.played == [.warning])
    }

    @Test("beginning a publish clears the previous error")
    func retryClearsError() {
        let (model, _) = makeModel()
        model.fail("Couldn't save your pebble. Please try again.")
        model.beginPublish()
        #expect(model.publishError == nil)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests 2>&1 | grep -i "error:" | head -10
```

Expected: compile failure — `cannot find 'RecordFlowModel' in scope`.

- [ ] **Step 3: Write the implementation**

Create `apps/ios/Pebbles/Features/Record/RecordFlowModel.swift`:

```swift
import Foundation
import Observation

/// The record flow's state machine: the draft under construction, the step the
/// user is on, and every interaction that changes either (D4).
///
/// Views own no flow state. That is not tidiness for its own sake — the
/// requirement is a haptic on *every* tap, and implemented as a discipline
/// ("remember to buzz in each action closure") it is one forgotten closure
/// away from being false and untestable besides. Routing every interaction
/// through this type makes it structural: a tap that does not call a method
/// here changes nothing, and every method here buzzes.
///
/// The haptic is injected so tests can record it without touching UIKit.
@Observable
@MainActor
final class RecordFlowModel {

    /// Longest name the flow accepts. Front-end only (D3): neither
    /// `pebbles.name` nor `PebbleCreatePayload` constrains length, and nothing
    /// server-side is added to enforce it.
    static let nameLimit = 40

    var draft = PebbleDraft()
    private(set) var step: RecordStep = .photo

    /// Mirrored from the `SnapUploadCoordinator` by the view, so the photo
    /// step's button can read `Skip` or `Done` without the model owning media.
    var hasSnap: Bool = false

    /// Set once publish returns. Drives the success step.
    private(set) var published: ComposePebbleResponse?
    private(set) var isPublishing = false
    private(set) var publishError: String?

    private let haptic: @MainActor (TapHaptic) -> Void

    init(haptic: @escaping @MainActor (TapHaptic) -> Void = { TapHaptics.play($0) }) {
        self.haptic = haptic
    }

    // MARK: - Answers

    /// Whether a given step has been answered. Drives both the forward gate and
    /// the optional steps' `Skip` / `Done` button label.
    func hasAnswer(for step: RecordStep) -> Bool {
        switch step {
        case .photo:
            return hasSnap
        case .when:
            // Always seeded — from the photo's EXIF, or now.
            return true
        case .name:
            return !draft.name.trimmingCharacters(in: .whitespaces).isEmpty
        case .valence:
            return draft.valence != nil
        case .emotion:
            return draft.emotionId != nil
        case .domain:
            return draft.domainId != nil
        case .souls:
            return !draft.soulIds.isEmpty
        case .collection:
            return draft.collectionId != nil
        case .glyph:
            return draft.glyphId != nil
        case .privacy:
            // Always seeded — `.secret` until the user says otherwise.
            return true
        case .success:
            return true
        }
    }

    /// Whether the current step may be left. Optional steps are always
    /// satisfied: passing one is the user saying "not this one", not an error.
    var isAnswered: Bool {
        step.isOptional || hasAnswer(for: step)
    }

    /// `Skip` while the optional step is empty, `Done` once it holds something.
    /// Only meaningful on optional steps.
    var optionalButtonIsSkip: Bool {
        !hasAnswer(for: step)
    }

    // MARK: - Navigation

    func advance() {
        guard step != .success else { return }
        guard isAnswered else {
            haptic(.warning)
            return
        }
        guard let next = step.next else { return }
        haptic(.advance)
        step = next
    }

    func back() {
        guard step != .success, let previous = step.previous else { return }
        haptic(.advance)
        step = previous
    }

    /// Jump without gating or feedback. For resume and for tests — never wired
    /// to a control.
    func go(to step: RecordStep) {
        self.step = step
    }

    // MARK: - Selection

    /// A tile pick: commit the value and move on.
    ///
    /// Fires `.selection` and nothing else. The pick and the advance are one
    /// gesture, and two buzzes for one tap reads as a stutter rather than as
    /// two pieces of information.
    private func commitAndAdvance(_ mutate: (inout PebbleDraft) -> Void) {
        haptic(.selection)
        mutate(&draft)
        if let next = step.next { step = next }
    }

    func select(valence: Valence) { commitAndAdvance { $0.valence = valence } }
    func select(emotionId: UUID) { commitAndAdvance { $0.emotionId = emotionId } }
    func select(domainId: UUID) { commitAndAdvance { $0.domainId = domainId } }
    func select(collectionId: UUID) { commitAndAdvance { $0.collectionId = collectionId } }
    func select(glyphId: UUID) { commitAndAdvance { $0.glyphId = glyphId } }

    /// Souls are multi-select, so a toggle never advances — the step's
    /// `Skip` / `Done` button does that.
    func toggleSoul(_ id: UUID) {
        haptic(.selection)
        if let index = draft.soulIds.firstIndex(of: id) {
            draft.soulIds.remove(at: index)
        } else {
            draft.soulIds.append(id)
        }
    }

    /// Privacy selects without advancing: `Publish` is the step's action, and
    /// silently publishing on a grade tap would be a trap.
    func select(visibility: Visibility) {
        haptic(.selection)
        draft.visibility = visibility
    }

    /// Clamped write for the name field (D3).
    func setName(_ raw: String) {
        draft.name = String(raw.prefix(Self.nameLimit))
    }

    /// Seed the date from the picked photo's EXIF (D7). No-op for a nil date,
    /// so a photo without metadata leaves `happenedAt` at its default of now.
    func applyCaptureDate(_ date: Date?) {
        guard let date else { return }
        draft.happenedAt = date
    }

    // MARK: - Resume

    /// The first mandatory step this draft has not answered — where a resumed
    /// draft lands (D9).
    ///
    /// Optional steps never count as gaps: skipping one is a legitimate answer,
    /// and re-asking would silently undo the user's decision.
    func firstGap() -> RecordStep {
        for candidate in RecordStep.counted where !candidate.isOptional {
            if !hasAnswer(for: candidate) { return candidate }
        }
        return .privacy
    }

    func resume(from payload: PebbleDraftPayload, known: PebbleDraft.KnownIds) {
        draft = PebbleDraft(payload: payload, known: known)
        step = firstGap()
    }

    // MARK: - Publish

    func beginPublish() {
        isPublishing = true
        publishError = nil
    }

    func succeed(with response: ComposePebbleResponse) {
        haptic(.success)
        isPublishing = false
        published = response
        step = .success
    }

    /// A hard failure never reaches the success step (D10): the flow stays put
    /// so the draft — and the way out via `✕` → Save as draft — is untouched.
    func fail(_ message: String) {
        haptic(.warning)
        isPublishing = false
        publishError = message
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests 2>&1 | grep -E "Test Suite 'RecordFlowModel|failed|passed" | head -20
```

Expected: all six `RecordFlowModel — …` suites pass (16 tests).

If `PebbleDraftPayload()` does not compile with no arguments, check
`apps/ios/Pebbles/Features/Path/Models/PebbleDraftPayload.swift` — it is used
as `self.init()` inside its own convenience initializers, so the memberwise
default exists; make the properties `var` with `nil` defaults if the compiler
disagrees.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Record/RecordFlowModel.swift apps/ios/PebblesTests/Features/Record/RecordFlowModelTests.swift
git commit -m "feat(ios): record flow state machine (M58)

Gating, back, skip labels, resume-to-first-gap, the 40-char clamp and the
haptic per interaction all live on the model, so they are testable without
rendering a view.

Refs #723"
```

---
## Task 4: EXIF capture date

The photo carries `DateTimeOriginal`, and `ImagePipeline` strips it. Read it from the picked bytes *before* the pipeline runs, so the date step arrives pre-filled (D7).

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/ExifCaptureDate.swift`
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift`
- Test: `apps/ios/PebblesTests/Features/Record/ExifCaptureDateTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/Features/Record/ExifCaptureDateTests.swift`:

```swift
import CoreGraphics
import Foundation
import ImageIO
import Testing
import UniformTypeIdentifiers
@testable import Pebbles

/// Builds a real 4×4 JPEG, optionally carrying an EXIF `DateTimeOriginal`.
/// A real encoded image is used rather than a canned blob so the test exercises
/// the same `CGImageSource` path production does.
private func makeJPEG(dateTimeOriginal: String?) -> Data {
    let side = 4
    let context = CGContext(
        data: nil,
        width: side,
        height: side,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
    )!
    context.setFillColor(CGColor(red: 0.5, green: 0.5, blue: 0.5, alpha: 1))
    context.fill(CGRect(x: 0, y: 0, width: side, height: side))
    let image = context.makeImage()!

    let output = NSMutableData()
    let destination = CGImageDestinationCreateWithData(
        output, UTType.jpeg.identifier as CFString, 1, nil
    )!
    var properties: [CFString: Any] = [:]
    if let dateTimeOriginal {
        properties[kCGImagePropertyExifDictionary] = [
            kCGImagePropertyExifDateTimeOriginal: dateTimeOriginal
        ]
    }
    CGImageDestinationAddImage(destination, image, properties as CFDictionary)
    CGImageDestinationFinalize(destination)
    return output as Data
}

@Suite("ExifCaptureDate")
struct ExifCaptureDateTests {

    @Test("reads DateTimeOriginal as a local-time wall clock")
    func readsDateTimeOriginal() throws {
        let data = makeJPEG(dateTimeOriginal: "2026:08:14 17:32:05")

        let parsed = try #require(ExifCaptureDate.from(data))

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        let parts = calendar.dateComponents(
            [.year, .month, .day, .hour, .minute, .second], from: parsed
        )
        #expect(parts.year == 2026)
        #expect(parts.month == 8)
        #expect(parts.day == 14)
        #expect(parts.hour == 17)
        #expect(parts.minute == 32)
        #expect(parts.second == 5)
    }

    @Test("returns nil when the image carries no EXIF date")
    func nilWithoutExif() {
        #expect(ExifCaptureDate.from(makeJPEG(dateTimeOriginal: nil)) == nil)
    }

    @Test("returns nil for a malformed EXIF date rather than inventing one")
    func nilForMalformedDate() {
        #expect(ExifCaptureDate.from(makeJPEG(dateTimeOriginal: "not a date")) == nil)
    }

    @Test("returns nil for data that is not an image")
    func nilForNonImage() {
        #expect(ExifCaptureDate.from(Data("plainly not an image".utf8)) == nil)
    }

    @Test("returns nil for empty data")
    func nilForEmptyData() {
        #expect(ExifCaptureDate.from(Data()) == nil)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests/ExifCaptureDateTests 2>&1 | grep -i "error:" | head -5
```

Expected: `cannot find 'ExifCaptureDate' in scope`.

- [ ] **Step 3: Write the implementation**

Create `apps/ios/Pebbles/Features/PebbleMedia/ExifCaptureDate.swift`:

```swift
import Foundation
import ImageIO

/// Reads the capture timestamp out of picked image bytes (D7).
///
/// Must run *before* `ImagePipeline.process`, which deliberately produces
/// metadata-free JPEGs — by the time bytes reach Storage the capture date is
/// gone, which is right for what we upload and useless for what we want to ask.
///
/// Read from the picked bytes rather than `PHAsset.creationDate`: the latter
/// needs a photo-library authorization the app does not request, to learn
/// something already present in data we have loaded anyway.
///
/// Pure: no I/O, no logging, no global state.
enum ExifCaptureDate {

    /// EXIF `DateTimeOriginal` is `yyyy:MM:dd HH:mm:ss` with no zone — a wall
    /// clock in whatever timezone the camera was in. We interpret it as local
    /// time, which is right for the overwhelmingly common case of recording a
    /// moment from a photo taken nearby.
    ///
    /// `en_US_POSIX` is mandatory here and is *not* the locale-pinning the iOS
    /// guidelines forbid: this parses a fixed machine format, not a
    /// user-facing date. A device locale with a non-Gregorian calendar would
    /// otherwise misread it.
    private static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy:MM:dd HH:mm:ss"
        formatter.timeZone = .current
        return formatter
    }()

    /// The photo's capture date, or nil when the data is not an image, carries
    /// no EXIF date, or carries one we cannot parse. Every nil path means the
    /// caller falls back to now — never to a date we guessed.
    static func from(_ data: Data) -> Date? {
        guard !data.isEmpty,
              let source = CGImageSourceCreateWithData(data as CFData, nil),
              CGImageSourceGetCount(source) > 0,
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil)
                  as? [CFString: Any],
              let exif = properties[kCGImagePropertyExifDictionary] as? [CFString: Any],
              let raw = exif[kCGImagePropertyExifDateTimeOriginal] as? String
        else {
            return nil
        }
        return formatter.date(from: raw)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests/ExifCaptureDateTests 2>&1 | tail -20
```

Expected: `Test Suite 'ExifCaptureDate' passed` — 5 tests.

- [ ] **Step 5: Expose it from the snap coordinator**

In `apps/ios/Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift`, add a stored property beside `processedForRetry`:

```swift
    /// Capture date read from the picked photo's EXIF, before `ImagePipeline`
    /// strips it (D7). The record flow seeds the `when` step from this; nil
    /// means the step falls back to now.
    private(set) var pickedCaptureDate: Date?
```

Then in `handlePicked(_:userId:)`, immediately after the `data` is loaded and logged, and **before** the `ImagePipeline.process` call:

```swift
        // Read the capture date before the pipeline strips metadata (D7).
        pickedCaptureDate = ExifCaptureDate.from(data)
```

And in every place that clears `processedForRetry` (the `cancelAndCleanup` and `removePending` paths), clear the date alongside it:

```swift
        pickedCaptureDate = nil
```

Verify by grepping that the two properties are cleared together:

```bash
cd apps/ios && grep -n "processedForRetry = nil" -A 1 Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift
```

Expected: every `processedForRetry = nil` is followed by `pickedCaptureDate = nil`.

- [ ] **Step 6: Build and run the media suites to confirm nothing regressed**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests/SnapUploadCoordinatorTests \
  -only-testing:PebblesTests/ExifCaptureDateTests 2>&1 | tail -20
```

Expected: both suites pass.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/ExifCaptureDate.swift \
        apps/ios/Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift \
        apps/ios/PebblesTests/Features/Record/ExifCaptureDateTests.swift
git commit -m "feat(ios): read the photo capture date before the pipeline strips it (M58)

Refs #723"
```

---

## Task 5: Domain glyph and description

The domain step renders glyph + name + description. iOS fetches none of the three beyond the name (D6).

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/Domain.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Models/Domain+Localized.swift`
- Modify: `apps/ios/Pebbles/Services/ReferenceDataService.swift`
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`
- Modify: `apps/ios/PebblesTests/LocalizationTests.swift`
- Test: `apps/ios/PebblesTests/Features/Record/DomainWithGlyphDecodingTests.swift`

- [ ] **Step 1: Write the failing decoding test**

Create `apps/ios/PebblesTests/Features/Record/DomainWithGlyphDecodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("Domain — v_domains_with_glyph decoding")
struct DomainWithGlyphDecodingTests {

    private func decode(_ json: String) throws -> Domain {
        try JSONDecoder().decode(Domain.self, from: Data(json.utf8))
    }

    @Test("a view row decodes with its glyph strokes")
    func decodesWithGlyph() throws {
        let domain = try decode("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "slug": "health",
          "name": "Health",
          "label": "Your body, energy, and physical well-being",
          "strokes": [{"d": "M10 10 L 90 90", "width": 6}],
          "view_box": "0 0 200 200"
        }
        """)

        #expect(domain.slug == "health")
        #expect(domain.strokes?.count == 1)
        #expect(domain.strokes?.first?.d == "M10 10 L 90 90")
        #expect(domain.viewBox == "0 0 200 200")
    }

    @Test("a domain with no default glyph decodes with nil strokes")
    func decodesWithoutGlyph() throws {
        let domain = try decode("""
        {
          "id": "22222222-2222-2222-2222-222222222222",
          "slug": "weather",
          "name": "Weather",
          "label": "Sun or rain, seasons and skies",
          "strokes": null,
          "view_box": null
        }
        """)

        #expect(domain.strokes == nil)
        #expect(domain.viewBox == nil)
    }

    @Test("a plain domains-table row still decodes, keys absent entirely")
    func decodesTableRow() throws {
        let domain = try decode("""
        {
          "id": "33333333-3333-3333-3333-333333333333",
          "slug": "work",
          "name": "Work",
          "label": "Your job, career, and professional life"
        }
        """)

        #expect(domain.strokes == nil)
        #expect(domain.viewBox == nil)
    }

    @Test("localizedLabel falls back to the DB label for an unknown slug")
    func labelFallsBack() {
        let domain = Domain(
            id: UUID(),
            slug: "not-a-real-slug-xyz",
            name: "Fallback Name",
            label: "Fallback Label"
        )
        #expect(domain.localizedLabel == "Fallback Label")
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests 2>&1 | grep -i "error:" | head -5
```

Expected: `value of type 'Domain' has no member 'strokes'`.

- [ ] **Step 3: Widen the model**

Replace the whole of `apps/ios/Pebbles/Features/Path/Models/Domain.swift`:

```swift
import Foundation

/// A life domain. Decoded from `v_domains_with_glyph` (D6), which flattens the
/// domain's default glyph onto the row as `strokes` + `view_box`.
///
/// Both glyph fields are optional and defaulted so the type still decodes a
/// plain `domains` table row, and so the memberwise initializer stays source
/// compatible for tests and previews that never care about the glyph.
struct Domain: Identifiable, Decodable, Hashable {
    let id: UUID
    let slug: String
    let name: String
    /// The English description. Render `localizedLabel`, never this.
    let label: String
    var strokes: [GlyphStroke]?
    var viewBox: String?

    enum CodingKeys: String, CodingKey {
        case id
        case slug
        case name
        case label
        case strokes
        case viewBox = "view_box"
    }
}
```

- [ ] **Step 4: Add the localized label**

Append to `apps/ios/Pebbles/Features/Path/Models/Domain+Localized.swift`, inside the existing `extension Domain`:

```swift
    /// Localized description, keyed by slug, falling back to the DB `label`
    /// column (English) when the catalog has no entry. Same Pattern C shape as
    /// `localizedName` — see `Emotion+Localized.swift` for the
    /// `NSLocalizedString` vs `String(localized:)` rationale.
    var localizedLabel: String {
        let key = "domain.\(slug).label"
        return NSLocalizedString(key, value: label, comment: "")
    }
```

- [ ] **Step 5: Point the reference fetch at the view**

In `apps/ios/Pebbles/Services/ReferenceDataService.swift`, change the `domainsQuery` inside `load()`:

```swift
            // v_domains_with_glyph (security_invoker) flattens the default
            // glyph onto each row so the record flow's domain picker can draw
            // it without a second round-trip (D6).
            async let domainsQuery: [Domain] = client
                .from("v_domains_with_glyph")
                .select("id, slug, name, label, strokes, view_box")
                .order("name")
                .execute()
                .value
```

- [ ] **Step 6: Add the 36 catalog entries**

`Localizable.xcstrings` is Xcode-formatted JSON (`"key" : {`, two-space indent, keys sorted). A `json.dump` round-trip would reformat all 6,833 lines and bury the real change, so entries are inserted as text at anchors instead.

`domain.<slug>.label` sorts immediately before `domain.<slug>.name`, so the anchor for each is that `name` line. Run from the repo root:

```bash
python3 - <<'PY'
from pathlib import Path

LABELS = {
    "community":     ("People who share your values, culture, or beliefs",
                      "Celles et ceux qui partagent tes valeurs, ta culture ou tes croyances"),
    "currentevents": ("What's happening in the world around you",
                      "Ce qui se passe dans le monde autour de toi"),
    "dating":        ("Romantic relationships and connections",
                      "Relations amoureuses et nouvelles rencontres"),
    "education":     ("Learning, growth, and intellectual pursuits",
                      "Apprentissage, croissance et quête intellectuelle"),
    "family":        ("Those connected to you by blood or soul",
                      "Celles et ceux qui te sont liés par le sang ou par le cœur"),
    "fitness":       ("Physical activity, games, and competition",
                      "Activité physique, jeux et compétition"),
    "friends":       ("People you choose to walk alongside",
                      "Les personnes que tu choisis d'avoir à tes côtés"),
    "health":        ("Your body, energy, and physical well-being",
                      "Ton corps, ton énergie et ton bien-être physique"),
    "hobbies":       ("Creative pursuits and things you love doing",
                      "Activités créatives et choses que tu aimes faire"),
    "identity":      ("Who you are and who you're becoming",
                      "Qui tu es et qui tu deviens"),
    "money":         ("Money, stability, and material security",
                      "Argent, stabilité et sécurité matérielle"),
    "partner":       ("Your significant other or love relationship",
                      "Ta moitié ou ta relation amoureuse"),
    "selfcare":      ("Rituals and habits that restore you",
                      "Rituels et habitudes qui te ressourcent"),
    "spirituality":  ("Faith, meaning, and your inner life",
                      "Foi, sens et vie intérieure"),
    "tasks":         ("Things you need to get done",
                      "Les choses que tu dois accomplir"),
    "travel":        ("Exploring new places and horizons",
                      "Explorer de nouveaux lieux et horizons"),
    "weather":       ("Sun or rain, seasons and skies",
                      "Soleil ou pluie, saisons et ciels"),
    "work":          ("Your job, career, and professional life",
                      "Ton travail, ta carrière et ta vie professionnelle"),
}

def block(slug, en, fr):
    def esc(s):
        return s.replace("\\", "\\\\").replace('"', '\\"')
    return (
        f'    "domain.{slug}.label" : {{\n'
        f'      "extractionState" : "manual",\n'
        f'      "localizations" : {{\n'
        f'        "en" : {{\n'
        f'          "stringUnit" : {{\n'
        f'            "state" : "translated",\n'
        f'            "value" : "{esc(en)}"\n'
        f'          }}\n'
        f'        }},\n'
        f'        "fr" : {{\n'
        f'          "stringUnit" : {{\n'
        f'            "state" : "translated",\n'
        f'            "value" : "{esc(fr)}"\n'
        f'          }}\n'
        f'        }}\n'
        f'      }}\n'
        f'    }},\n'
    )

path = Path("apps/ios/Pebbles/Resources/Localizable.xcstrings")
text = path.read_text(encoding="utf-8")

inserted = 0
for slug, (en, fr) in LABELS.items():
    label_key = f'    "domain.{slug}.label" : {{\n'
    if label_key in text:
        print(f"skip {slug}: already present")
        continue
    anchor = f'    "domain.{slug}.name" : {{\n'
    if anchor not in text:
        raise SystemExit(f"anchor missing for {slug} — check the slug against ReferenceSlugs")
    text = text.replace(anchor, block(slug, en, fr) + anchor, 1)
    inserted += 1

path.write_text(text, encoding="utf-8")
print(f"inserted {inserted} label entries")
PY
```

Expected output: `inserted 18 label entries`.

Verify the file is still valid JSON and that only additions were made:

```bash
python3 -c "import json; d=json.load(open('apps/ios/Pebbles/Resources/Localizable.xcstrings')); print(len([k for k in d['strings'] if k.endswith('.label')]), 'label keys')"
git diff --numstat apps/ios/Pebbles/Resources/Localizable.xcstrings
```

Expected: `18 label keys`, and a numstat showing **306 insertions, 0 deletions** — a nonzero deletion count means the formatting was disturbed, so revert and retry.

- [ ] **Step 7: Add the coverage assertion**

Find the existing domain-name coverage test in `apps/ios/PebblesTests/LocalizationTests.swift` (it iterates `ReferenceSlugs.domains` asserting `domain.<slug>.name` resolves) and add its twin beneath it:

```swift
    @Test("every seeded domain slug has a catalog label")
    func everyDomainHasALabel() {
        for slug in ReferenceSlugs.domains {
            let key = "domain.\(slug).label"
            let sentinel = "__missing__"
            let resolved = NSLocalizedString(key, value: sentinel, comment: "")
            #expect(resolved != sentinel, "missing catalog entry for \(key)")
        }
    }
```

If the existing name-coverage test uses a different assertion shape, match it rather than this one — one style per file.

- [ ] **Step 8: Run the affected suites**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests/DomainWithGlyphDecodingTests \
  -only-testing:PebblesTests/LocalizationPatternCTests 2>&1 | tail -25
```

Expected: both pass. Then confirm nothing else broke on `Domain`:

```bash
cd apps/ios && xcodebuild build -scheme Pebbles \
  -destination 'generic/platform=iOS Simulator' 2>&1 | grep -E "error:|BUILD" | head -10
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 9: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/Domain.swift \
        apps/ios/Pebbles/Features/Path/Models/Domain+Localized.swift \
        apps/ios/Pebbles/Services/ReferenceDataService.swift \
        apps/ios/Pebbles/Resources/Localizable.xcstrings \
        apps/ios/PebblesTests/LocalizationTests.swift \
        apps/ios/PebblesTests/Features/Record/DomainWithGlyphDecodingTests.swift
git commit -m "feat(ios): domain glyphs and localized descriptions (M58)

Reference data reads v_domains_with_glyph so the record flow's domain picker
can draw the glyph, and the 18 domain descriptions land in the catalog in EN
and FR.

Refs #723"
```

---
## Task 6: PebblePublisher

Extract the publish path so the sheet and the flow cannot drift apart on it (D8). Behavior-preserving: `CreatePebbleSheet` must do exactly what it does today afterwards.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/PebblePublisher.swift`
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`
- Test: `apps/ios/PebblesTests/Features/Record/PebblePublisherTests.swift`

- [ ] **Step 1: Write the failing test**

The network call is not unit-testable without a protocol seam, and none is warranted yet (`apps/ios/CLAUDE.md`: extract one when a test needs it, not before). The two pure pieces are where the bugs live, so those are what get tested.

Create `apps/ios/PebblesTests/Features/Record/PebblePublisherTests.swift`:

```swift
import Foundation
import Supabase
import Testing
@testable import Pebbles

@Suite("PebblePublisher — soft success")
struct PebblePublisherSoftSuccessTests {

    @Test("a 5xx body carrying pebble_id is recovered as a published pebble")
    func recoversPebbleId() {
        let id = UUID()
        let body = Data(#"{"pebble_id":"\#(id.uuidString)","error":"compose failed"}"#.utf8)

        #expect(PebblePublisher.softSuccessPebbleId(from: .httpError(code: 500, data: body)) == id)
    }

    @Test("a 5xx body without pebble_id is a hard failure")
    func noPebbleIdIsHardFailure() {
        let body = Data(#"{"error":"boom"}"#.utf8)
        #expect(PebblePublisher.softSuccessPebbleId(from: .httpError(code: 500, data: body)) == nil)
    }

    @Test("an empty body is a hard failure")
    func emptyBodyIsHardFailure() {
        #expect(PebblePublisher.softSuccessPebbleId(from: .httpError(code: 500, data: Data())) == nil)
    }

    @Test("a relay error is a hard failure")
    func relayErrorIsHardFailure() {
        #expect(PebblePublisher.softSuccessPebbleId(from: .relayError) == nil)
    }
}

@Suite("PebblePublisher — user-facing messages")
struct PebblePublisherMessageTests {

    @Test("a media quota rejection names the real limit")
    func quotaMessage() {
        let body = Data(#"{"error":"media_quota_exceeded"}"#.utf8)
        let message = userMessageForPebbleSaveError(FunctionsError.httpError(code: 400, data: body))
        #expect(message == "Photo limit reached on this pebble.")
    }

    @Test("a raised P0001 maps to the same quota message")
    func p0001Message() {
        let body = Data(#"{"message":"P0001: media_quota"}"#.utf8)
        let message = userMessageForPebbleSaveError(FunctionsError.httpError(code: 400, data: body))
        #expect(message == "Photo limit reached on this pebble.")
    }

    @Test("every image pipeline failure has its own message")
    func pipelineMessages() {
        #expect(userMessageForPebbleSaveError(ImagePipelineError.unsupportedFormat)
                == "That image format isn't supported.")
        #expect(userMessageForPebbleSaveError(ImagePipelineError.decodeFailed)
                == "Couldn't read the image.")
        #expect(userMessageForPebbleSaveError(ImagePipelineError.encodeFailed)
                == "Couldn't process the image.")
        #expect(userMessageForPebbleSaveError(ImagePipelineError.tooLargeAfterResize)
                == "That image is too large to attach.")
    }

    @Test("an unrecognized error falls back to the generic message")
    func fallbackMessage() {
        struct Unknown: Error {}
        #expect(userMessageForPebbleSaveError(Unknown())
                == "Couldn't save your pebble. Please try again.")
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests 2>&1 | grep -i "error:" | head -5
```

Expected: `cannot find 'PebblePublisher' in scope`.

- [ ] **Step 3: Write the publisher**

Create `apps/ios/Pebbles/Features/Path/PebblePublisher.swift`:

```swift
import Foundation
import Supabase
import os

/// The publish half of the composer: invoke `compose-pebble`, recover the
/// soft-success case, and map failures to user-facing copy.
///
/// Extracted from `CreatePebbleSheet` (D8) so the sheet and the record flow
/// share one implementation. Every branch here is a bug that was found and
/// fixed once already; a second hand-rolled copy is how they come back.
struct PebblePublisher {
    let client: SupabaseClient

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-publisher")

    /// Publishes `draft`, returning the compose response.
    ///
    /// Folds in the soft-success case: `compose-pebble` can return 5xx after
    /// the pebble row was already inserted, and the body then carries
    /// `pebble_id`. That pebble exists, so treating it as a failure would
    /// strand the user's work and — worse — leave the draft in place to be
    /// published a second time.
    func publish(
        draft: PebbleDraft,
        formSnap: FormSnap?,
        userId: UUID
    ) async throws -> ComposePebbleResponse {
        let payload = PebbleCreatePayload(from: draft, formSnap: formSnap, userId: userId)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601

        do {
            return try await client.functions.invoke(
                "compose-pebble",
                options: FunctionInvokeOptions(body: ComposePebbleRequest(payload: payload)),
                decoder: decoder
            )
        } catch let functionsError as FunctionsError {
            guard let pebbleId = Self.softSuccessPebbleId(from: functionsError) else {
                Self.logger.error(
                    "compose-pebble failed: \(functionsError.localizedDescription, privacy: .private)"
                )
                throw functionsError
            }
            Self.logger.warning("compose-pebble returned 5xx but pebble_id found — treating as published")
            // No render and no karma delta: the compose step is what failed.
            // Callers degrade to a text-only presentation, and `notifyEarned`
            // no-ops on a zero amount.
            return ComposePebbleResponse(
                pebbleId: pebbleId, renderSvg: nil, renderVersion: nil, karmaDelta: nil
            )
        }
    }

    /// Tries to extract a `pebble_id` from a `FunctionsError.httpError` body.
    /// Returns nil when the body is absent, unparseable, or has no such key.
    ///
    /// Internal rather than private so the recovery rule is directly testable —
    /// it is the branch that decides whether a user's pebble is lost.
    static func softSuccessPebbleId(from error: FunctionsError) -> UUID? {
        guard case let .httpError(_, data) = error, !data.isEmpty else { return nil }
        return try? JSONDecoder().decode(PebbleIdPartial.self, from: data).pebbleId
    }
}

private struct ComposePebbleRequest: Encodable {
    let payload: PebbleCreatePayload
}

private struct PebbleIdPartial: Decodable {
    let pebbleId: UUID
    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
    }
}

/// Maps a thrown error to user-facing copy. Module-level so `CreatePebbleSheet`,
/// `EditPebbleSheet` and `RecordFlowView` share one mapping.
func userMessageForPebbleSaveError(_ error: Error) -> String {
    if let fnError = error as? FunctionsError, case let .httpError(_, data) = fnError,
       let body = try? JSONDecoder().decode([String: String].self, from: data) {
        let message = body["error"] ?? body["message"] ?? ""
        if message.contains("media_quota_exceeded") || message.contains("P0001") {
            return "Photo limit reached on this pebble."
        }
    }
    if let pipelineError = error as? ImagePipelineError {
        switch pipelineError {
        case .unsupportedFormat:    return "That image format isn't supported."
        case .decodeFailed:         return "Couldn't read the image."
        case .encodeFailed:         return "Couldn't process the image."
        case .tooLargeAfterResize:  return "That image is too large to attach."
        }
    }
    return "Couldn't save your pebble. Please try again."
}
```

- [ ] **Step 4: Rewire CreatePebbleSheet**

In `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`:

1. **Delete** the three module-level declarations that just moved: `private struct ComposePebbleRequest`, `private struct PebbleIdPartial`, and the whole `func userMessageForPebbleSaveError(_:)` at the bottom of the file.
2. **Delete** the `private func softSuccessPebbleId(from:)` method.
3. **Replace** the body of `save()` from `isSaving = true` to the end of the method with:

```swift
        isSaving = true
        saveError = nil

        do {
            let response = try await PebblePublisher(client: supabase.client)
                .publish(draft: draft, formSnap: snaps?.formSnap, userId: userId)
            // Soft success returns a nil delta; `notifyEarned` no-ops on zero,
            // which is exactly what the old inline branch did.
            karma.notifyEarned(amount: response.karmaDelta ?? 0, reason: .pebbleCreated)
            achievements.fireCheck()
            await consumeDraftAfterPublish()
            onCreated(response.pebbleId)
            dismiss()
        } catch {
            logger.error("create pebble failed: \(error.localizedDescription, privacy: .private)")
            await handleSaveFailure(error)
        }
```

Leave the guards above it (`draft.isValid`, `snaps?.isUploading`, `snaps?.hasFailed`, `currentUserId`) exactly as they are — those are view-level policy, not publishing.

4. Remove the now-unused `import Supabase` **only if** nothing else in the file uses it:

```bash
cd apps/ios && grep -n "Supabase\|FunctionsError\|SupabaseClient" Pebbles/Features/Path/CreatePebbleSheet.swift
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' 2>&1 | grep -E "error:|Executed|failed" | head -15
```

Expected: the two new `PebblePublisher — …` suites pass (9 tests), and **every pre-existing suite still passes** — this task must change no behavior.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebblePublisher.swift \
        apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift \
        apps/ios/PebblesTests/Features/Record/PebblePublisherTests.swift
git commit -m "quality(ios): extract PebblePublisher from CreatePebbleSheet (M58)

The compose-pebble invoke, the soft-success recovery and the error mapping
move behind one type so the record flow shares them rather than growing a
second copy. Behavior unchanged.

Refs #723"
```

---
## Task 7: ComposerDraftCoordinator

Extract the M47 draft glue that currently lives as an extension on the view (D8).

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/ComposerDraftCoordinator.swift`
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet+Drafts.swift`
- Test: `apps/ios/PebblesTests/Features/Record/ComposerDraftCoordinatorTests.swift`

- [ ] **Step 1: Write the failing test**

The hydration decision is the piece carrying the #647 bug, so it becomes a pure static and is tested directly.

Create `apps/ios/PebblesTests/Features/Record/ComposerDraftCoordinatorTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("ComposerDraftCoordinator — hydration decision")
struct ComposerDraftHydrationTests {

    private func record(name: String) -> PebbleDraftRecord {
        var payload = PebbleDraftPayload()
        payload.name = name
        return PebbleDraftRecord(
            id: UUID(), payload: payload, updatedAt: Date(timeIntervalSince1970: 0)
        )
    }

    /// #647: hydrating before reference data lands sanitizes the draft against
    /// empty sets and silently drops every soul and collection.
    @Test("nothing is decided while reference data is still loading")
    func waitsForReferenceData() {
        let decision = ComposerDraftCoordinator.hydration(
            resuming: record(name: "Ferry"), refsLoaded: false, snapshot: nil
        )
        #expect(decision == nil)
    }

    @Test("a resumed server draft wins over a local snapshot")
    func serverDraftWins() {
        var snapshot = PebbleDraftPayload()
        snapshot.name = "half-typed"
        let resuming = record(name: "Ferry")

        let decision = ComposerDraftCoordinator.hydration(
            resuming: resuming, refsLoaded: true, snapshot: snapshot
        )

        #expect(decision == .resume(resuming.payload))
    }

    @Test("a non-empty snapshot with no server draft offers a restore")
    func offersRestore() {
        var snapshot = PebbleDraftPayload()
        snapshot.name = "half-typed"

        let decision = ComposerDraftCoordinator.hydration(
            resuming: nil, refsLoaded: true, snapshot: snapshot
        )

        #expect(decision == .offerRestore(snapshot))
    }

    @Test("an empty snapshot is not worth prompting about")
    func emptySnapshotIsFresh() {
        let decision = ComposerDraftCoordinator.hydration(
            resuming: nil, refsLoaded: true, snapshot: PebbleDraftPayload()
        )
        #expect(decision == .fresh)
    }

    @Test("no draft and no snapshot starts fresh")
    func nothingToRestore() {
        let decision = ComposerDraftCoordinator.hydration(
            resuming: nil, refsLoaded: true, snapshot: nil
        )
        #expect(decision == .fresh)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests 2>&1 | grep -i "error:" | head -5
```

Expected: `cannot find 'ComposerDraftCoordinator' in scope`.

- [ ] **Step 3: Write the coordinator**

Create `apps/ios/Pebbles/Features/Path/ComposerDraftCoordinator.swift`:

```swift
import Foundation
import Observation
import Supabase
import os

/// Owns the composer's draft lifecycle for whichever composer is open — the
/// server draft (a deliberate "save as draft") and the local snapshot (crash
/// insurance). Extracted from `CreatePebbleSheet+Drafts` (D8).
///
/// The two are one payload shape and nothing else; see
/// `docs/superpowers/specs/2026-07-29-drafts-and-autosave-design.md`.
///
/// This type never owns the draft itself — the sheet holds it in `@State`, the
/// flow holds it on `RecordFlowModel`. It returns instructions and the caller
/// applies them, which is what lets one implementation serve both.
@Observable
@MainActor
final class ComposerDraftCoordinator {

    /// What the composer should do when it opens.
    enum Hydration: Equatable {
        /// Resuming a server draft — hydrate from this payload.
        case resume(PebbleDraftPayload)
        /// A local snapshot exists and is worth offering.
        case offerRestore(PebbleDraftPayload)
        /// Nothing to restore.
        case fresh
    }

    /// Whether a resumed draft's glyph is still usable.
    enum GlyphVerdict: Equatable {
        /// Keep it. Carries the glyph row when we could load it for the preview.
        case usable(Glyph?)
        /// Drop it — the user can no longer attach it.
        case unusable
        /// Verification itself failed; leave the draft alone.
        case unknown
    }

    /// The server draft this composer is bound to — the resumed one, or the one
    /// created by the first "Save as draft".
    private(set) var serverDraftId: UUID?
    private(set) var isSavingDraft = false
    private(set) var hasChecked = false

    /// Non-nil while a restore is on offer.
    private(set) var restorableSnapshot: PebbleDraftPayload?
    var isRestorePromptPresented = false

    private let client: SupabaseClient
    private let drafts: PebbleDraftsService
    private let snapshots: ComposerSnapshotStore
    private let autosave: ComposerAutosave
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "composer-drafts")

    init(client: SupabaseClient, drafts: PebbleDraftsService, snapshots: ComposerSnapshotStore) {
        self.client = client
        self.drafts = drafts
        self.snapshots = snapshots
        self.autosave = ComposerAutosave(store: snapshots)
    }

    // MARK: - Hydration

    /// The pure decision. A resumed server draft wins over a local snapshot —
    /// it is the more deliberate of the two, so we never prompt on top of it.
    ///
    /// Returns nil while `refsLoaded` is false: hydrating before reference data
    /// arrives sanitizes the draft against empty sets and silently drops every
    /// soul and collection (#647), the exact failure M47's D7 exists to prevent.
    /// `nonisolated` because it touches no instance state — being callable
    /// without hopping to the main actor is the point of extracting it, and
    /// without it the (non-`@MainActor`) test suite cannot call it at all.
    nonisolated static func hydration(
        resuming: PebbleDraftRecord?,
        refsLoaded: Bool,
        snapshot: PebbleDraftPayload?
    ) -> Hydration? {
        guard refsLoaded else { return nil }
        if let resuming { return .resume(resuming.payload) }
        if let snapshot, !snapshot.isEmpty { return .offerRestore(snapshot) }
        return .fresh
    }

    /// Applies `hydration(…)` once, recording its side effects. Returns nil
    /// while reference data is loading or when it has already run, so callers
    /// can drive it from both `.task` and an `onChange(of: refs.hasLoaded)`.
    func hydrate(resuming: PebbleDraftRecord?, refsLoaded: Bool) -> Hydration? {
        guard !hasChecked else { return nil }
        guard let decision = Self.hydration(
            resuming: resuming, refsLoaded: refsLoaded, snapshot: snapshots.load()
        ) else { return nil }

        hasChecked = true
        switch decision {
        case .resume:
            serverDraftId = resuming?.id
        case .offerRestore(let snapshot):
            restorableSnapshot = snapshot
            isRestorePromptPresented = true
        case .fresh:
            break
        }
        return decision
    }

    /// The user accepted the restore. Hands back the payload and clears the offer.
    func takeRestorableSnapshot() -> PebbleDraftPayload? {
        defer { restorableSnapshot = nil }
        return restorableSnapshot
    }

    /// The user chose to start fresh.
    func discardSnapshot() {
        restorableSnapshot = nil
        autosave.clear()
    }

    // MARK: - Autosave

    func schedule(_ payload: PebbleDraftPayload) { autosave.schedule(payload) }
    func flush() { autosave.flush() }

    // MARK: - Glyph verification

    /// `can_use_glyph` is what `create_pebble` enforces, so passing here means
    /// publish cannot 42501 on the glyph (M47 D7).
    ///
    /// A verification failure is not a reason to lose the user's glyph:
    /// `.unknown` leaves the draft alone, and publishing will surface a clear
    /// message if it really is unusable.
    func verifyGlyph(glyphId: UUID, userId: UUID) async -> GlyphVerdict {
        do {
            let usable: Bool = try await client
                .rpc(
                    "can_use_glyph",
                    params: ["p_glyph_id": glyphId.uuidString, "p_user": userId.uuidString]
                )
                .execute()
                .value
            guard usable else {
                logger.notice("resumed draft referenced an unusable glyph — dropping it")
                return .unusable
            }
            let glyphs: [Glyph] = try await client
                .from("glyphs")
                .select("id, name, strokes, view_box, user_id")
                .eq("id", value: glyphId)
                .limit(1)
                .execute()
                .value
            return .usable(glyphs.first)
        } catch {
            logger.error("glyph verification failed: \(error.localizedDescription, privacy: .private)")
            return .unknown
        }
    }

    // MARK: - Server draft

    /// Intentional "save as draft". Returns nil on success, or a user-facing
    /// message on failure.
    ///
    /// Deliberately does NOT run `snaps.cancelAndCleanup` — that would delete
    /// the snap the draft references.
    func saveAsDraft(payload: PebbleDraftPayload, userId: UUID) async -> String? {
        isSavingDraft = true
        defer { isSavingDraft = false }
        do {
            serverDraftId = try await drafts.save(payload: payload, id: serverDraftId, userId: userId)
            // Once the draft is on the server the local snapshot is redundant.
            autosave.clear()
            await drafts.refreshCount()
            return nil
        } catch {
            logger.error("save draft failed: \(error.localizedDescription, privacy: .private)")
            return "Couldn't save that draft. Please try again."
        }
    }

    /// Publishing consumed the draft. Runs on soft success too: a 5xx carrying
    /// a `pebble_id` still created the pebble, so a kept draft would duplicate it.
    func consumeAfterPublish() async {
        autosave.clear()
        guard let serverDraftId else { return }
        await drafts.deleteIgnoringFailure(id: serverDraftId)
        self.serverDraftId = nil
        await drafts.refreshCount()
    }
}
```

- [ ] **Step 4: Rewire CreatePebbleSheet onto the coordinator**

In `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`, replace these six `@State` properties:

```swift
    @State var isSavingDraft = false
    @State var serverDraftId: UUID?
    @State var autosave: ComposerAutosave?
    @State var restorableSnapshot: PebbleDraftPayload?
    @State var isRestorePromptPresented = false
    @State var hasCheckedSnapshot = false
```

with one:

```swift
    @State var drafts: ComposerDraftCoordinator?
```

Construct it in the existing `.task`, beside the `snaps` construction, replacing the `autosave` construction:

```swift
            if drafts == nil {
                drafts = ComposerDraftCoordinator(
                    client: supabase.client, drafts: draftsService, snapshots: snapshots
                )
            }
```

Then update the call sites in the same file:

| Was | Becomes |
|---|---|
| `if isSaving \|\| isSavingDraft {` | `if isSaving \|\| drafts?.isSavingDraft == true {` |
| `.disabled(!isSavableAsDraft \|\| isSaving \|\| isSavingDraft)` | `.disabled(!isSavableAsDraft \|\| isSaving \|\| drafts?.isSavingDraft == true)` |
| `autosave?.schedule(newValue)` | `drafts?.schedule(newValue)` |
| `if phase != .active { autosave?.flush() }` | `if phase != .active { drafts?.flush() }` |
| `guard !isRestorePromptPresented else { return }` | `guard drafts?.isRestorePromptPresented != true else { return }` |

And bind the restore alert through the coordinator:

```swift
        .alert("Pick up where you left off?", isPresented: Binding(
            get: { drafts?.isRestorePromptPresented ?? false },
            set: { drafts?.isRestorePromptPresented = $0 }
        )) {
            Button("Restore it") { restoreSnapshot() }
            Button("Start fresh", role: .destructive) { discardSnapshot() }
        } message: {
            Text("We kept what you were writing here. Add your photo again when you're ready.")
        }
```

- [ ] **Step 5: Rewrite CreatePebbleSheet+Drafts as thin delegation**

In `apps/ios/Pebbles/Features/Path/CreatePebbleSheet+Drafts.swift`, keep `draftPayload`, `isSavableAsDraft` and `knownIds` exactly as they are, and replace everything below them with:

```swift
    /// Resuming a server draft wins over the local snapshot. The `refs.hasLoaded`
    /// gate (#647) lives in the coordinator, so this is safe to call from both
    /// `.task` and the `refs.hasLoaded` change handler.
    func hydrateOrOfferRestore() {
        guard let drafts,
              let decision = drafts.hydrate(resuming: resuming, refsLoaded: refs.hasLoaded)
        else { return }

        switch decision {
        case .resume(let payload):
            draft = PebbleDraft(payload: payload, known: knownIds)
            if let existing = payload.existingSnap {
                snaps?.seedExisting(.existing(id: existing.id, storagePath: existing.storagePath))
            }
            Task { await verifyGlyph() }
        case .offerRestore, .fresh:
            break
        }
    }

    func restoreSnapshot() {
        guard let snapshot = drafts?.takeRestorableSnapshot() else { return }
        draft = PebbleDraft(payload: snapshot, known: knownIds)
        Task { await verifyGlyph() }
    }

    func discardSnapshot() {
        drafts?.discardSnapshot()
    }

    func verifyGlyph() async {
        guard let glyphId = draft.glyphId, let userId = currentUserId, let drafts else { return }
        switch await drafts.verifyGlyph(glyphId: glyphId, userId: userId) {
        case .usable(let glyph):
            selectedGlyph = glyph
        case .unusable:
            draft.glyphId = nil
            selectedGlyph = nil
        case .unknown:
            break
        }
    }

    func saveAsDraft() async {
        guard isSavableAsDraft, let drafts else { return }
        guard let userId = currentUserId else {
            logger.error("save draft: no current user id")
            saveError = "You must be signed in to save."
            return
        }
        if let message = await drafts.saveAsDraft(payload: draftPayload, userId: userId) {
            saveError = message
        } else {
            dismiss()
        }
    }

    func consumeDraftAfterPublish() async {
        await drafts?.consumeAfterPublish()
    }
```

- [ ] **Step 6: Run the full suite — this task must change no behavior**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' 2>&1 | grep -E "error:|Executed|failed" | head -20
```

Expected: the new hydration suite passes (5 tests) and every pre-existing suite still passes — in particular `ComposerSnapshotStoreTests`, `PebbleDraftPayloadTests`, `DraftCrossSurfaceDecodingTests` and `PebbleDraftFromDetailTests`. Those are the regression net for this extraction; a failure there means the refactor changed behavior and must be fixed, not accommodated.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/ComposerDraftCoordinator.swift \
        apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift \
        apps/ios/Pebbles/Features/Path/CreatePebbleSheet+Drafts.swift \
        apps/ios/PebblesTests/Features/Record/ComposerDraftCoordinatorTests.swift
git commit -m "quality(ios): extract ComposerDraftCoordinator from the create sheet (M58)

The M47 draft lifecycle moves off the view so the record flow shares it, and
the #647 hydration gate becomes a pure function with its own tests.

Refs #723"
```

---
## Task 8: Extract the picker contents

Each picker sheet gives up its body to a `…PickerContent` view (D5). The sheets keep their `NavigationStack`, toolbar, detents and staging; the flow steps will render the same content inline. Presentation only: no dismissal, no staging, no fetching inside a content view.

This is a pure move. If a content view's rendering differs from the sheet's before this task, something was mistranscribed.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/ValencePickerContent.swift`
- Create: `apps/ios/Pebbles/Features/Path/EmotionPickerContent.swift`
- Create: `apps/ios/Pebbles/Features/Path/SoulPickerContent.swift`
- Create: `apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerContent.swift`
- Modify: the four matching `…Sheet.swift` files

- [ ] **Step 1: Extract ValencePickerContent**

Create `apps/ios/Pebbles/Features/Path/ValencePickerContent.swift`:

```swift
import SwiftUI

/// The valence grid: three `ValenceSizeGroup` sections, each holding the three
/// polarity options.
///
/// Presentation only (D5). Shared by `ValencePickerSheet`, which wraps it with
/// a Cancel toolbar and dismisses on pick, and the record flow's valence step,
/// which commits on tap and advances. Same grid, different commit semantics.
struct ValencePickerContent: View {
    let selected: Valence?
    let onSelect: (Valence) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            ForEach(ValenceSizeGroup.allCases) { group in
                section(for: group)
            }
        }
    }

    @ViewBuilder
    private func section(for group: ValenceSizeGroup) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(group.name)
                .font(.headline)
                .foregroundStyle(Color.system.secondary)

            Text(group.description)
                .font(.subheadline)
                .foregroundStyle(Color.system.secondary)

            HStack(spacing: 12) {
                ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                    if let option = valence(in: group, polarity: polarity) {
                        optionButton(for: option, in: group)
                    }
                }
            }
        }
    }

    /// The single `Valence` case at a given (size, polarity) cell.
    /// Lookup uniqueness is guaranteed by `ValenceHelpersTests.lookupIsUnique`.
    private func valence(in group: ValenceSizeGroup, polarity: ValencePolarity) -> Valence? {
        Valence.allCases.first { $0.sizeGroup == group && $0.polarity == polarity }
    }

    @ViewBuilder
    private func optionButton(for option: Valence, in group: ValenceSizeGroup) -> some View {
        let isActive = (option == selected)

        Button {
            onSelect(option)
        } label: {
            VStack(spacing: 8) {
                Image(option.assetName)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 64, height: 64)
                    .foregroundStyle(isActive ? Color.system.background : Color.system.secondary)

                Text(option.shortLabel)
                    .font(.footnote)
                    .foregroundStyle(isActive ? Color.system.background : Color.system.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(isActive ? Color.accent.primary : Color.system.muted)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("\(String(localized: group.name)), \(String(localized: option.shortLabel))"))
        .accessibilityAddTraits(isActive ? [.isSelected] : [])
    }
}

#Preview("nothing selected") {
    ValencePickerContent(selected: nil, onSelect: { _ in }).padding()
}

#Preview("highlightMedium selected") {
    ValencePickerContent(selected: .highlightMedium, onSelect: { _ in }).padding()
}
```

Then replace the body of `apps/ios/Pebbles/Features/Path/ValencePickerSheet.swift`, deleting its `section`, `valence` and `optionButton` methods and both previews' bodies:

```swift
    var body: some View {
        NavigationStack {
            ScrollView {
                ValencePickerContent(selected: currentValence) { picked in
                    onSelected(picked)
                    dismiss()
                }
                .padding()
            }
            .pebblesToolbarTitle("Choose a valence")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") { dismiss() }
                }
            }
            .pebblesScreen()
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
```

- [ ] **Step 2: Extract EmotionPickerContent**

Create `apps/ios/Pebbles/Features/Path/EmotionPickerContent.swift` holding, moved **verbatim** from `EmotionPickerSheet`:

- the `CategoryGroup` struct
- the `groups` computed property
- `section(for:)`, `header(for:)` and `chip(for:in:)`
- the `ProgressView` empty state

with these three changes and no others:

1. The type takes `selected: UUID?`, `valence: Valence?`, `onSelect: (UUID) -> Void` — no `currentEmotionId`, no staging state, no `dismiss`.
2. `chip(for:in:)` compares against `selected` instead of `stagedEmotionId`, and its button action is `onSelect(row.id)` — the toggle-to-clear decision belongs to the caller.
3. The outer `LazyVStack` keeps its `alignment: .leading, spacing: 24` but loses the `ScrollView` and `.padding()`, which the callers supply.

```swift
import SwiftUI

/// The emotion grid, grouped into categories ordered by the composer's current
/// valence via `EmotionCategoryOrdering.order(for:)`.
///
/// Presentation only (D5). `EmotionPickerSheet` stages the selection and
/// commits on Done; the record flow's emotion step commits on tap and advances.
/// Reporting the tapped id and letting the caller decide is what allows both.
struct EmotionPickerContent: View {
    let selected: UUID?
    let valence: Valence?
    let onSelect: (UUID) -> Void

    @Environment(EmotionPaletteService.self) private var palettes

    var body: some View {
        LazyVStack(alignment: .leading, spacing: 24) {
            if groups.isEmpty {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 32)
            } else {
                ForEach(groups) { group in
                    section(for: group)
                }
            }
        }
    }

    // …CategoryGroup, groups, section(for:), header(for:), chip(for:in:)
    // moved verbatim from EmotionPickerSheet, with the two changes above.
}
```

Then in `apps/ios/Pebbles/Features/Path/EmotionPickerSheet.swift`, keep `stagedEmotionId`, the custom `init`, and the Cancel/Done toolbar, and replace the `ScrollView`'s contents:

```swift
            ScrollView {
                EmotionPickerContent(
                    selected: stagedEmotionId,
                    valence: valence
                ) { picked in
                    // Tapping the staged chip clears it, so the user can
                    // deselect inside the sheet without backing out.
                    stagedEmotionId = (stagedEmotionId == picked) ? nil : picked
                }
                .padding()
            }
```

- [ ] **Step 3: Extract SoulPickerContent**

Create `apps/ios/Pebbles/Features/Path/SoulPickerContent.swift`:

```swift
import SwiftUI

/// The soul grid: a `.create` tile followed by one `SoulItem` per soul.
///
/// Presentation only (D5) — it takes a list rather than fetching, so the sheet
/// can keep its own fetch (the form's cached list goes stale behind it) while
/// the record flow's souls step reads `ReferenceDataService.souls`, which is
/// already cached and already refreshed after Profile mutations.
///
/// Selection rule (issue #459): with nothing selected every row renders
/// `.default`; as soon as one soul is selected, selected rows render
/// `.selected` and every other renders `.unselected`. The `.create` tile is
/// unaffected by selection.
struct SoulPickerContent: View {
    let souls: [SoulWithGlyph]
    let selection: Set<UUID>
    let onToggle: (UUID) -> Void
    let onCreate: () -> Void

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: Spacing.lg)]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text("All my souls")
                .pebblesFont(.cardHeading)
                .foregroundStyle(Color.system.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            LazyVGrid(columns: columns, spacing: Spacing.lg) {
                SoulItem(case: .create, soul: nil, count: nil, onTap: onCreate)
                ForEach(souls) { soul in
                    SoulItem(
                        case: itemCase(for: soul.id),
                        soul: soul,
                        count: soul.pebblesCount
                    ) {
                        onToggle(soul.id)
                    }
                }
            }

            if souls.isEmpty {
                Text("Add the first soul to tag this pebble with")
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.system.secondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func itemCase(for id: UUID) -> SoulItem.Case {
        if selection.isEmpty { return .default }
        return selection.contains(id) ? .selected : .unselected
    }
}
```

Then in `apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift`, keep `load()`, `souls`, `selection`, `isLoading`, `loadError` and the toolbar, and replace the `else` branch of `content` with:

```swift
            ScrollView {
                SoulPickerContent(
                    souls: souls,
                    selection: selection,
                    onToggle: { toggle($0) },
                    onCreate: { isPresentingCreate = true }
                )
                .padding(Spacing.lg)
            }
```

Delete the sheet's now-unused `columns` and `itemCase(for:)`.

- [ ] **Step 4: Extract GlyphPickerContent**

The glyph picker carries loading, three tabs, and inline buy — so the content view takes all of it except the sheet chrome. Carve and buy stay presented sheets: carving is a full modal task with its own canvas, and flattening it into a step would be a second wizard nested inside the first (D5).

Create `apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerContent.swift` by moving, from `GlyphPickerSheet`:

- every `@State` except nothing (all of `tab`, `itemsByTab`, `isLoading`, `loadError`, `showCarveSheet`, `buying` move)
- the `logger`, `market`, `columns`, `items` members
- the whole `content` computed property and `carveNewRow`
- the `load(_:)` method
- the `.task { await stats.load() }`, `.task(id: tab)`, `.fullScreenCover` and `.sheet(item:)` modifiers
- the `.safeAreaInset(edge: .bottom) { GlyphTabBar(selection: $tab) }`

with this signature and no dismissal anywhere inside:

```swift
import SwiftUI
import os

/// The glyph picker's grid, tabs and inline buy flow.
///
/// Presentation plus its own fetching (D5): unlike the other content views this
/// one owns loading, because the three tabs each have their own query and
/// nothing outside the picker caches them.
///
/// `onSelected` fires for every way a glyph becomes the choice — picked from a
/// tab, carved fresh, or bought from Commu. The caller decides what happens
/// next: `GlyphPickerSheet` dismisses, the record flow's glyph step advances.
struct GlyphPickerContent: View {
    let currentGlyphId: UUID?
    let onSelected: (Glyph) -> Void

    // …moved state, members, content, load(_:) and modifiers…
}
```

Then `apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerSheet.swift` becomes the chrome only:

```swift
import SwiftUI

/// Sheet wrapper around `GlyphPickerContent`, used by the pebble form and the
/// soul sheets. The record flow renders the content directly instead.
struct GlyphPickerSheet: View {
    let currentGlyphId: UUID?
    let onSelected: (Glyph) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            GlyphPickerContent(currentGlyphId: currentGlyphId) { glyph in
                onSelected(glyph)
                dismiss()
            }
            .pebblesToolbarTitle("Choose a glyph")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Close") { dismiss() }
                }
            }
            .pebblesScreen()
        }
    }
}
```

- [ ] **Step 5: Build and confirm every existing caller still compiles**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build
cd apps/ios && xcodegen generate && xcodebuild build -scheme Pebbles \
  -destination 'generic/platform=iOS Simulator' 2>&1 | grep -E "error:|warning: unused|BUILD" | head -20
```

Expected: `BUILD SUCCEEDED`. `GlyphPickerSheet` has four callers (`PebbleFormView`, `CreateSoulSheet`, `EditSoulSheet`, `SettingsSheet`) and its signature is unchanged, so none of them should need edits. If one does, the extraction changed the interface and should be corrected rather than the caller.

- [ ] **Step 6: Verify the sheets still render as before**

Open each of the four sheets' `#Preview` in Xcode's canvas and confirm the grid, spacing and selected state are unchanged from `main`. This is a pure move; any visual difference is a transcription error.

```bash
cd apps/ios && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' 2>&1 | grep -E "Executed|failed" | head -5
```

Expected: the full suite still passes.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/ValencePickerContent.swift \
        apps/ios/Pebbles/Features/Path/EmotionPickerContent.swift \
        apps/ios/Pebbles/Features/Path/SoulPickerContent.swift \
        apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerContent.swift \
        apps/ios/Pebbles/Features/Path/ValencePickerSheet.swift \
        apps/ios/Pebbles/Features/Path/EmotionPickerSheet.swift \
        apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift \
        apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerSheet.swift
git commit -m "quality(ios): extract picker contents from the picker sheets (M58)

Each sheet keeps its chrome, detents and staging; the grid moves into a
content view the record flow's steps will render inline. Pure move.

Refs #723"
```

---

## Task 9: DomainPickerContent

The one picker with no existing sheet to extract from. Mirrors the web `DomainSheet` row: glyph, name, description (D6).

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/DomainPickerContent.swift`

- [ ] **Step 1: Write the view**

```swift
import SwiftUI

/// The domain picker: one row per domain carrying its glyph, localized name
/// and localized description. Mirrors the web `DomainSheet` row.
///
/// Single-select, presentation only. The record flow's domain step is the only
/// caller today — `PebbleFormView` keeps its menu `Picker`, which needs none of
/// this.
///
/// A domain with no default glyph (nil `strokes`) renders name and description
/// with the glyph slot left empty rather than substituting a placeholder mark:
/// an invented glyph would read as data.
struct DomainPickerContent: View {
    let domains: [Domain]
    let selected: UUID?
    let onSelect: (UUID) -> Void

    var body: some View {
        LazyVStack(spacing: Spacing.sm) {
            ForEach(domains) { domain in
                row(for: domain)
            }
        }
    }

    @ViewBuilder
    private func row(for domain: Domain) -> some View {
        let isSelected = (domain.id == selected)

        Button {
            onSelect(domain.id)
        } label: {
            HStack(spacing: Spacing.md) {
                GlyphView(
                    case: isSelected ? .selected : .default,
                    strokes: domain.strokes,
                    side: 36
                )
                .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: domain.localizedName)
                        .pebblesFont(.bodyEmphasized)
                        .foregroundStyle(
                            isSelected ? Color.accent.primary : Color.system.foreground
                        )
                    Text(verbatim: domain.localizedLabel)
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.system.secondary)
                        .multilineTextAlignment(.leading)
                }

                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? Color.accent.primary.opacity(0.12) : Color.system.muted)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        // Both strings are already resolved against the catalog, so they are
        // passed verbatim to avoid double-localization — same rule as
        // PebbleFormView's emotion row label.
        .accessibilityLabel(Text(verbatim: domain.localizedName))
        .accessibilityHint(Text(verbatim: domain.localizedLabel))
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}

#Preview {
    let strokes = [GlyphStroke(d: "M40 40 L 160 160 M160 40 L 40 160", width: 6)]
    let domains = [
        Domain(id: UUID(), slug: "health", name: "Health",
               label: "Your body, energy, and physical well-being",
               strokes: strokes, viewBox: "0 0 200 200"),
        Domain(id: UUID(), slug: "work", name: "Work",
               label: "Your job, career, and professional life",
               strokes: nil, viewBox: nil)
    ]
    return ScrollView {
        DomainPickerContent(domains: domains, selected: domains[0].id, onSelect: { _ in })
            .padding()
    }
    .background(Color.system.background)
}
```

- [ ] **Step 2: Build and check the preview**

```bash
cd apps/ios && xcodegen generate && xcodebuild build -scheme Pebbles \
  -destination 'generic/platform=iOS Simulator' 2>&1 | grep -E "error:|BUILD" | head -10
```

Expected: `BUILD SUCCEEDED`. Open the preview in Xcode and confirm: the first row is selected and accented, the second renders with an empty glyph slot rather than a placeholder mark, and both descriptions wrap rather than truncate.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/DomainPickerContent.swift
git commit -m "feat(ios): domain picker rows with glyph, name and description (M58)

Refs #723"
```

---
## Task 10: Step scaffold and flow chrome

One layout for all eleven steps, and the `‹ back · dots · ✕` bar above them (D2, D3).

**Files:**
- Create: `apps/ios/Pebbles/Features/Record/RecordStepScaffold.swift`
- Create: `apps/ios/Pebbles/Features/Record/RecordFlowChrome.swift`

- [ ] **Step 1: Write the scaffold**

Create `apps/ios/Pebbles/Features/Record/RecordStepScaffold.swift`:

```swift
import SwiftUI

/// The single action a step may offer beneath its content.
///
/// Top-level rather than nested inside the generic scaffold: `RecordFlowView`
/// builds these in a `switch` that never names a `Content` type, and
/// `RecordStepScaffold<AnyView>.Action` would be a placeholder standing in for
/// nothing.
enum RecordStepAction {
    /// Quiet text button — `Skip` / `Done` on the optional steps (D3).
    case text(LocalizedStringResource, () -> Void)
    /// Full-width prominent button — `Continue` on `when` / `name`,
    /// `Publish` on `privacy`.
    case primary(LocalizedStringResource, enabled: Bool, loading: Bool, () -> Void)
}

/// Shared geometry for every step: a title, a content slot, and one optional
/// button beneath it.
///
/// Steps supply content and a button role and never their own layout, so the
/// title baseline and button position do not drift between screens as the user
/// moves through the flow — which is the whole reason the flow reads as one
/// motion rather than eleven pages.
struct RecordStepScaffold<Content: View>: View {
    let title: LocalizedStringResource
    var subtitle: LocalizedStringResource?
    var action: RecordStepAction?
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: Spacing.lg) {
            VStack(spacing: Spacing.xs) {
                Text(title)
                    .pebblesFont(.title)
                    .foregroundStyle(Color.system.foreground)
                    .multilineTextAlignment(.center)

                if let subtitle {
                    Text(subtitle)
                        .pebblesFont(.callout)
                        .foregroundStyle(Color.system.secondary)
                        .multilineTextAlignment(.center)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.lg)

            ScrollView {
                content()
                    .padding(.horizontal, Spacing.lg)
                    .padding(.bottom, Spacing.lg)
            }
            .scrollBounceBehavior(.basedOnSize)

            if let action {
                actionView(action)
                    .padding(.horizontal, Spacing.lg)
                    .padding(.bottom, Spacing.sm)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @ViewBuilder
    private func actionView(_ action: RecordStepAction) -> some View {
        switch action {
        case let .text(label, perform):
            Button(action: perform) {
                Text(label)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.system.secondary)
            }
            .buttonStyle(.plain)

        case let .primary(label, enabled, loading, perform):
            Button(action: perform) {
                Text(label)
            }
            .buttonStyle(PebblesPrimaryButtonStyle(isLoading: loading))
            .disabled(!enabled || loading)
        }
    }
}

#Preview("text action") {
    RecordStepScaffold(
        title: "Anyone in this one?",
        subtitle: "Tag the souls who were there.",
        action: .text("Skip", {})
    ) {
        Color.system.muted.frame(height: 220).clipShape(.rect(cornerRadius: 12))
    }
    .background(Color.system.background)
}

#Preview("primary action") {
    RecordStepScaffold(
        title: "When did it happen?",
        action: .primary("Continue", enabled: true, loading: false, {})
    ) {
        Color.system.muted.frame(height: 320).clipShape(.rect(cornerRadius: 12))
    }
    .background(Color.system.background)
}
```

- [ ] **Step 2: Write the chrome**

Create `apps/ios/Pebbles/Features/Record/RecordFlowChrome.swift`:

```swift
import SwiftUI

/// The flow's top bar: back chevron, progress dots, close.
///
/// Minimal by design (D2) — picking is the advance, so there is no Next button
/// competing with the dots, and "Save as draft" lives in the close
/// confirmation rather than taking permanent residence here (D9).
struct RecordFlowChrome: View {
    let step: RecordStep
    let onBack: () -> Void
    let onClose: () -> Void

    private var canGoBack: Bool { step.previous != nil }

    var body: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.body.weight(.medium))
                    .foregroundStyle(Color.system.secondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .opacity(canGoBack ? 1 : 0)
            .disabled(!canGoBack)
            .accessibilityLabel("Back")
            .accessibilityHidden(!canGoBack)

            Spacer()

            dots

            Spacer()

            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.body.weight(.medium))
                    .foregroundStyle(Color.system.secondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close")
        }
        .padding(.horizontal, Spacing.sm)
    }

    /// One element to VoiceOver, not ten: "Step 4 of 10" is the useful reading,
    /// and ten unlabeled dots is not.
    private var dots: some View {
        HStack(spacing: Spacing.xs + 2) {
            ForEach(RecordStep.counted) { candidate in
                Circle()
                    .fill(fill(for: candidate))
                    .frame(width: 6, height: 6)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(stepAnnouncement)
    }

    private func fill(for candidate: RecordStep) -> Color {
        guard let current = step.dotIndex, let index = candidate.dotIndex else {
            return Color.system.muted
        }
        return index <= current ? Color.accent.primary : Color.system.muted
    }

    private var stepAnnouncement: Text {
        guard let index = step.dotIndex else { return Text("Done") }
        return Text("Step \(index + 1) of \(RecordStep.counted.count)")
    }
}

#Preview {
    VStack(spacing: 40) {
        RecordFlowChrome(step: .photo, onBack: {}, onClose: {})
        RecordFlowChrome(step: .domain, onBack: {}, onClose: {})
        RecordFlowChrome(step: .privacy, onBack: {}, onClose: {})
    }
    .padding(.vertical, 40)
    .background(Color.system.background)
}
```

- [ ] **Step 3: Build and check the previews**

```bash
cd apps/ios && xcodegen generate && xcodebuild build -scheme Pebbles \
  -destination 'generic/platform=iOS Simulator' 2>&1 | grep -E "error:|BUILD" | head -10
```

Expected: `BUILD SUCCEEDED`. In the canvas, confirm the chrome preview shows the back chevron hidden on `photo`, six filled dots on `domain`, and all ten filled on `privacy`.

Add the four new strings to the catalog by building once with `SWIFT_EMIT_LOC_STRINGS` (which the scheme already sets) and then opening `Localizable.xcstrings` in Xcode: `Back`, `Close`, `Step %lld of %lld` and `Done` will appear in the `New` state. Fill in French for each:

| Key | French |
|---|---|
| `Back` | `Retour` |
| `Close` | `Fermer` |
| `Step %lld of %lld` | `Étape %lld sur %lld` |
| `Done` | `Terminé` |

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Record/RecordStepScaffold.swift \
        apps/ios/Pebbles/Features/Record/RecordFlowChrome.swift \
        apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): record flow step scaffold and chrome (M58)

Refs #723"
```

---

## Task 11: Photo, when and name steps

The three steps that are not tile grids, and therefore the three that carry a button (D3).

**Files:**
- Create: `apps/ios/Pebbles/Features/Record/Steps/RecordPhotoStep.swift`
- Create: `apps/ios/Pebbles/Features/Record/Steps/RecordWhenStep.swift`
- Create: `apps/ios/Pebbles/Features/Record/Steps/RecordNameStep.swift`

- [ ] **Step 1: Write the photo step**

Create `apps/ios/Pebbles/Features/Record/Steps/RecordPhotoStep.swift`:

```swift
import SwiftUI

/// Step 0 — the picture the flow starts from (D2).
///
/// Does **not** auto-advance on pick (D3): the upload runs in the background
/// and its state belongs on screen while the user is still looking at the
/// photo. Picking swaps `Skip` for `Done` and waits.
struct RecordPhotoStep: View {
    let snap: FormSnap?
    let onPick: () -> Void
    let onRetry: () -> Void
    let onRemove: () -> Void

    var body: some View {
        VStack(spacing: Spacing.lg) {
            switch snap {
            case .none:
                addTile
            case .pending(let attached):
                picked(thumb: attached.localThumb, state: attached.state)
            case .existing:
                // Only reachable when resuming a draft that already carries a
                // snap; the bytes live in Storage, so the step just confirms
                // one is attached rather than re-rendering it.
                attachedWithoutThumb
            }
        }
    }

    private var addTile: some View {
        Button(action: onPick) {
            VStack(spacing: Spacing.sm) {
                Image(systemName: "photo.badge.plus")
                    .font(.system(size: 40))
                    .foregroundStyle(Color.system.secondary)
                Text("Add a photo")
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.system.secondary)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 280)
            .background(
                RoundedRectangle(cornerRadius: Spacing.xxl, style: .continuous)
                    .strokeBorder(Color.system.muted, style: StrokeStyle(lineWidth: 2, dash: [10, 10]))
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Add a photo")
    }

    @ViewBuilder
    private func picked(thumb: Data, state: AttachedSnap.State) -> some View {
        VStack(spacing: Spacing.md) {
            if let image = UIImage(data: thumb) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(maxWidth: .infinity)
                    .frame(height: 280)
                    .clipShape(RoundedRectangle(cornerRadius: Spacing.xxl, style: .continuous))
                    .accessibilityHidden(true)
            }

            switch state {
            case .uploading:
                HStack(spacing: Spacing.sm) {
                    ProgressView()
                    Text("Uploading…")
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.system.secondary)
                }
            case .uploaded:
                Button("Choose another", action: onPick)
                    .pebblesFont(.meta)
                    .foregroundStyle(Color.system.secondary)
            case .failed:
                VStack(spacing: Spacing.sm) {
                    Text("That photo didn't upload.")
                        .pebblesFont(.meta)
                        .foregroundStyle(.red)
                    HStack(spacing: Spacing.lg) {
                        Button("Retry", action: onRetry)
                        Button("Remove", role: .destructive, action: onRemove)
                    }
                    .pebblesFont(.meta)
                }
            }
        }
    }

    private var attachedWithoutThumb: some View {
        VStack(spacing: Spacing.sm) {
            Image(systemName: "photo")
                .font(.system(size: 40))
                .foregroundStyle(Color.system.secondary)
            Text("A photo is already attached.")
                .pebblesFont(.callout)
                .foregroundStyle(Color.system.secondary)
            Button("Remove", role: .destructive, action: onRemove)
                .pebblesFont(.meta)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 280)
    }
}
```

If `AttachedSnap.State` is spelled differently, match the real enum:

```bash
cd apps/ios && grep -n "enum State" -A 6 Pebbles/Features/PebbleMedia/Models/AttachedSnap.swift
```

- [ ] **Step 2: Write the when step**

Create `apps/ios/Pebbles/Features/Record/Steps/RecordWhenStep.swift`:

```swift
import SwiftUI

/// Step 1 — the moment. Seeded from the photo's EXIF when it had one (D7), so
/// the common case (recording from a picture taken earlier today) needs no
/// input at all beyond confirming.
struct RecordWhenStep: View {
    @Binding var happenedAt: Date
    /// True when the value on screen came from the photo rather than from now.
    let seededFromPhoto: Bool

    var body: some View {
        VStack(spacing: Spacing.md) {
            DatePicker(
                "When",
                selection: $happenedAt,
                displayedComponents: [.date, .hourAndMinute]
            )
            .datePickerStyle(.graphical)
            .labelsHidden()
            .tint(Color.accent.primary)

            if seededFromPhoto {
                Label("Taken from your photo", systemImage: "sparkles")
                    .pebblesFont(.meta)
                    .foregroundStyle(Color.system.secondary)
            }
        }
    }
}

#Preview {
    RecordWhenStep(happenedAt: .constant(Date()), seededFromPhoto: true)
        .padding()
        .background(Color.system.background)
}
```

- [ ] **Step 3: Write the name step**

Create `apps/ios/Pebbles/Features/Record/Steps/RecordNameStep.swift`:

```swift
import SwiftUI

/// Step 2 — what to call it. Clamped to 40 characters on the way in, so the
/// counter can never show an over-limit value and there is no error state to
/// design (D3). The limit is front-end only.
struct RecordNameStep: View {
    let name: String
    let limit: Int
    let onChange: (String) -> Void

    @FocusState private var isFocused: Bool

    var body: some View {
        VStack(alignment: .trailing, spacing: Spacing.sm) {
            TextField(
                "Name",
                text: Binding(get: { name }, set: onChange),
                axis: .vertical
            )
            .lineLimit(1...3)
            .pebblesFont(.title)
            .multilineTextAlignment(.center)
            .focused($isFocused)
            .submitLabel(.done)
            .padding(Spacing.md)
            .background(Color.system.muted)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            Text(verbatim: "\(name.count)/\(limit)")
                .pebblesFont(.meta)
                .foregroundStyle(
                    name.count == limit ? Color.accent.primary : Color.system.secondary
                )
                .accessibilityLabel(Text("\(name.count) of \(limit) characters"))
        }
        .onAppear { isFocused = true }
    }
}

#Preview {
    RecordNameStep(name: "Ferry to Ithaca", limit: 40, onChange: { _ in })
        .padding()
        .background(Color.system.background)
}
```

- [ ] **Step 4: Build**

```bash
cd apps/ios && xcodegen generate && xcodebuild build -scheme Pebbles \
  -destination 'generic/platform=iOS Simulator' 2>&1 | grep -E "error:|BUILD" | head -10
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Record/Steps/RecordPhotoStep.swift \
        apps/ios/Pebbles/Features/Record/Steps/RecordWhenStep.swift \
        apps/ios/Pebbles/Features/Record/Steps/RecordNameStep.swift
git commit -m "feat(ios): record flow photo, when and name steps (M58)

Refs #723"
```

---
## Task 12: Valence, emotion and domain steps

Three tile steps. Each file's job is to map the model onto its content view, so `RecordFlowView`'s switch stays a list of one-liners and never reaches into a content view's parameter list.

**Files:**
- Create: `apps/ios/Pebbles/Features/Record/Steps/RecordValenceStep.swift`
- Create: `apps/ios/Pebbles/Features/Record/Steps/RecordEmotionStep.swift`
- Create: `apps/ios/Pebbles/Features/Record/Steps/RecordDomainStep.swift`

- [ ] **Step 1: Write the three steps**

Create `apps/ios/Pebbles/Features/Record/Steps/RecordValenceStep.swift`:

```swift
import SwiftUI

/// Step 3 — how big and how bright. Commits on tap and advances (D3).
struct RecordValenceStep: View {
    let model: RecordFlowModel

    var body: some View {
        ValencePickerContent(selected: model.draft.valence) { picked in
            model.select(valence: picked)
        }
    }
}
```

Create `apps/ios/Pebbles/Features/Record/Steps/RecordEmotionStep.swift`:

```swift
import SwiftUI

/// Step 4 — the emotion. Categories arrive ordered by the valence chosen on
/// step 3, which is the reason valence comes first (D2).
///
/// Unlike `EmotionPickerSheet` there is no staging and no toggle-to-clear: a
/// step that advances on tap cannot be cancelled, so the tap is the commit.
struct RecordEmotionStep: View {
    let model: RecordFlowModel

    var body: some View {
        EmotionPickerContent(
            selected: model.draft.emotionId,
            valence: model.draft.valence
        ) { picked in
            model.select(emotionId: picked)
        }
    }
}
```

Create `apps/ios/Pebbles/Features/Record/Steps/RecordDomainStep.swift`:

```swift
import SwiftUI

/// Step 5 — the life domain, with its glyph and description (D6).
struct RecordDomainStep: View {
    let model: RecordFlowModel

    @Environment(ReferenceDataService.self) private var refs

    var body: some View {
        DomainPickerContent(
            domains: refs.domains,
            selected: model.draft.domainId
        ) { picked in
            model.select(domainId: picked)
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
cd apps/ios && xcodegen generate && xcodebuild build -scheme Pebbles \
  -destination 'generic/platform=iOS Simulator' 2>&1 | grep -E "error:|BUILD" | head -10
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Record/Steps/RecordValenceStep.swift \
        apps/ios/Pebbles/Features/Record/Steps/RecordEmotionStep.swift \
        apps/ios/Pebbles/Features/Record/Steps/RecordDomainStep.swift
git commit -m "feat(ios): record flow valence, emotion and domain steps (M58)

Refs #723"
```

---

## Task 13: Souls, collection and glyph steps

The three optional steps after the photo. Each carries the `Skip` / `Done` text button supplied by the scaffold in Task 16 (D3).

**Files:**
- Create: `apps/ios/Pebbles/Features/Record/Steps/RecordSoulsStep.swift`
- Create: `apps/ios/Pebbles/Features/Record/Steps/RecordCollectionStep.swift`
- Create: `apps/ios/Pebbles/Features/Record/Steps/RecordGlyphStep.swift`

- [ ] **Step 1: Write the souls step**

Create `apps/ios/Pebbles/Features/Record/Steps/RecordSoulsStep.swift`:

```swift
import SwiftUI

/// Step 6 — who was there. Multi-select, so a tap never advances; the step's
/// `Skip` / `Done` button does (D3).
///
/// Reads `ReferenceDataService.souls` rather than fetching its own list the way
/// `SoulPickerSheet` does: that cache is already refreshed after every Profile
/// mutation, and a freshly created soul is appended by refreshing it here.
struct RecordSoulsStep: View {
    let model: RecordFlowModel

    @Environment(ReferenceDataService.self) private var refs
    @State private var isPresentingCreate = false

    var body: some View {
        SoulPickerContent(
            souls: refs.souls,
            selection: Set(model.draft.soulIds),
            onToggle: { model.toggleSoul($0) },
            onCreate: { isPresentingCreate = true }
        )
        .sheet(isPresented: $isPresentingCreate) {
            CreateSoulSheet { inserted in
                // Select it immediately — the user created it *for* this
                // pebble, so making them tap it again is friction.
                model.toggleSoul(inserted.id)
                Task { await refs.refreshSouls() }
            }
        }
    }
}
```

- [ ] **Step 2: Write the collection step**

Create `apps/ios/Pebbles/Features/Record/Steps/RecordCollectionStep.swift`:

```swift
import SwiftUI

/// Step 7 — which collection, if any. Single-select, so a tap commits and
/// advances; `Skip` is how the user says none (D3).
///
/// No inline creation: `CreateCollectionSheet` lives in Profile, and adding a
/// second creation entry point here is out of scope for the flow.
struct RecordCollectionStep: View {
    let model: RecordFlowModel

    @Environment(ReferenceDataService.self) private var refs

    var body: some View {
        if refs.collections.isEmpty {
            Text("You don't have any collections yet.")
                .pebblesFont(.callout)
                .foregroundStyle(Color.system.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.xxl)
        } else {
            LazyVStack(spacing: Spacing.sm) {
                ForEach(refs.collections) { collection in
                    row(for: collection)
                }
            }
        }
    }

    @ViewBuilder
    private func row(for collection: PebbleCollection) -> some View {
        let isSelected = (collection.id == model.draft.collectionId)

        Button {
            model.select(collectionId: collection.id)
        } label: {
            HStack(spacing: Spacing.md) {
                Image(systemName: "square.stack")
                    .foregroundStyle(isSelected ? Color.accent.primary : Color.system.secondary)
                // Collection names are user-authored, so never localized.
                Text(verbatim: collection.name)
                    .pebblesFont(.body)
                    .foregroundStyle(isSelected ? Color.accent.primary : Color.system.foreground)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? Color.accent.primary.opacity(0.12) : Color.system.muted)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: collection.name))
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}
```

- [ ] **Step 3: Write the glyph step**

Create `apps/ios/Pebbles/Features/Record/Steps/RecordGlyphStep.swift`:

```swift
import SwiftUI

/// Step 8 — the glyph, skippable (D2).
///
/// Renders `GlyphPickerContent` inline, which brings its tabs, its inline buy
/// and its carve entry point with it. Carve and buy stay presented sheets:
/// carving is a full modal task with its own canvas, and flattening it into a
/// step would be a second wizard nested inside the first (D5).
struct RecordGlyphStep: View {
    let model: RecordFlowModel
    /// Kept by the flow so a resumed draft's glyph can be shown, and so the
    /// step can render what is currently chosen.
    @Binding var selectedGlyph: Glyph?

    var body: some View {
        GlyphPickerContent(currentGlyphId: model.draft.glyphId) { glyph in
            selectedGlyph = glyph
            model.select(glyphId: glyph.id)
        }
    }
}
```

- [ ] **Step 4: Build**

```bash
cd apps/ios && xcodegen generate && xcodebuild build -scheme Pebbles \
  -destination 'generic/platform=iOS Simulator' 2>&1 | grep -E "error:|BUILD" | head -10
```

Expected: `BUILD SUCCEEDED`. If `CreateSoulSheet`'s callback signature differs, check it:

```bash
cd apps/ios && grep -n "struct CreateSoulSheet" -A 8 Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift
```

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Record/Steps/RecordSoulsStep.swift \
        apps/ios/Pebbles/Features/Record/Steps/RecordCollectionStep.swift \
        apps/ios/Pebbles/Features/Record/Steps/RecordGlyphStep.swift
git commit -m "feat(ios): record flow souls, collection and glyph steps (M58)

Refs #723"
```

---

## Task 14: Privacy and publish step

The one step where a tap selects without advancing, because publishing on a grade tap would be a trap (D3). Also the step that carries the snap state and the publish error (D10).

**Files:**
- Create: `apps/ios/Pebbles/Features/Record/Steps/RecordPrivacyStep.swift`

- [ ] **Step 1: Write the step**

Create `apps/ios/Pebbles/Features/Record/Steps/RecordPrivacyStep.swift`:

```swift
import SwiftUI

/// Step 9 — who gets to see it, and the publish button.
///
/// The grade is the decision most coupled to "am I ready for other people to
/// see this", which is why it sits against publish rather than in a toolbar
/// chip eight fields away (D2).
///
/// A tap selects and does not advance (D3). The snap state and any publish
/// error live here too, because this is where the user is standing when
/// publishing is blocked or fails (D10).
struct RecordPrivacyStep: View {
    let model: RecordFlowModel
    /// Non-nil while the attached photo blocks publishing (uploading or failed).
    let snapBlockedMessage: String?

    var body: some View {
        VStack(spacing: Spacing.md) {
            ForEach(Visibility.allCases) { grade in
                row(for: grade)
            }

            if let snapBlockedMessage {
                Text(verbatim: snapBlockedMessage)
                    .pebblesFont(.meta)
                    .foregroundStyle(Color.system.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, Spacing.sm)
            }

            if let error = model.publishError {
                Text(verbatim: error)
                    .pebblesFont(.callout)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
                    .padding(.top, Spacing.sm)
            }
        }
    }

    @ViewBuilder
    private func row(for grade: Visibility) -> some View {
        let isSelected = (grade == model.draft.visibility)

        Button {
            model.select(visibility: grade)
        } label: {
            HStack(spacing: Spacing.md) {
                Image(systemName: grade.systemImageName)
                    .font(.title3)
                    .frame(width: 32)
                    .foregroundStyle(isSelected ? Color.accent.primary : Color.system.secondary)

                VStack(alignment: .leading, spacing: 2) {
                    Text(grade.label)
                        .pebblesFont(.bodyEmphasized)
                        .foregroundStyle(isSelected ? Color.accent.primary : Color.system.foreground)
                    Text(explanation(for: grade))
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.system.secondary)
                        .multilineTextAlignment(.leading)
                }

                Spacer(minLength: 0)
            }
            .padding(Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? Color.accent.primary.opacity(0.12) : Color.system.muted)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(grade.label)
        .accessibilityHint(explanation(for: grade))
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }

    /// One line per M51 grade. Deliberately not on `Visibility` itself: the
    /// chip and the badge want the bare label, and only this step has room for
    /// the explanation.
    private func explanation(for grade: Visibility) -> LocalizedStringResource {
        switch grade {
        case .secret:  return "Only you can see this one."
        case .private: return "Your mutual connections can see it."
        case .public:  return "Anyone with the link can see it."
        }
    }
}

#Preview {
    RecordPrivacyStep(model: RecordFlowModel(), snapBlockedMessage: nil)
        .padding()
        .background(Color.system.background)
}
```

- [ ] **Step 2: Build and fill in the new strings**

```bash
cd apps/ios && xcodegen generate && xcodebuild build -scheme Pebbles \
  -destination 'generic/platform=iOS Simulator' 2>&1 | grep -E "error:|BUILD" | head -10
```

Expected: `BUILD SUCCEEDED`. Then open `Localizable.xcstrings` in Xcode and give the three new keys their French, in the app's informal "Tu" register:

| Key | French |
|---|---|
| `Only you can see this one.` | `Toi seul peux voir celui-ci.` |
| `Your mutual connections can see it.` | `Tes connexions mutuelles peuvent le voir.` |
| `Anyone with the link can see it.` | `Toute personne avec le lien peut le voir.` |

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Record/Steps/RecordPrivacyStep.swift \
        apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): record flow privacy and publish step (M58)

Refs #723"
```

---

## Task 15: Success step

The pebble draws itself on (D10).

**Files:**
- Create: `apps/ios/Pebbles/Features/Record/Steps/RecordSuccessStep.swift`
- Modify: `apps/ios/Pebbles/Features/Karma/KarmaNotificationService.swift`

- [ ] **Step 1: Let the karma service celebrate without a pastille**

In `apps/ios/Pebbles/Features/Karma/KarmaNotificationService.swift`, change `notifyEarned` to take an opt-out:

```swift
    /// - Parameter presentsCapsule: pass `false` when the surface already shows
    ///   the amount — the record flow's success step does, and a pastille over
    ///   it is redundant. The sound and the haptic still fire either way: they
    ///   are the celebration, not the notification (D10).
    func notifyEarned(amount: Int, reason: KarmaReason, presentsCapsule: Bool = true) {
        // Only positive credits celebrate; deletions/clawbacks stay silent.
        guard amount > 0 else { return }

        // Fire haptic + sound together so the waveform-matched vibration lands
        // in sync with the ceramic sound.
        hapticTrigger &+= 1
        haptics.playKarmaEarned()
        audio.playKarmaEarnedSound()

        guard presentsCapsule else { return }
        presentCapsule(KarmaEarnedContent(amount: amount, reason: reason))
    }
```

The default keeps every existing caller unchanged.

- [ ] **Step 2: Write the success step**

Create `apps/ios/Pebbles/Features/Record/Steps/RecordSuccessStep.swift`:

```swift
import SwiftUI

/// Step 10 — the pebble, drawn on.
///
/// Reuses `PebbleReadPetroglyph`, the same component the read view uses: it
/// composites the outline backdrop and traces the composed render with the
/// native draw-on animation, and it already honors Reduce Motion.
///
/// On soft success (`renderSvg` nil) it degrades to name + karma with no
/// artwork rather than blocking — the pebble exists and the user should be
/// told so (D10).
struct RecordSuccessStep: View {
    let name: String
    let response: ComposePebbleResponse
    let valence: Valence
    let emotionId: UUID?
    let onExit: () -> Void

    @Environment(EmotionPaletteService.self) private var palettes

    var body: some View {
        VStack(spacing: Spacing.xl) {
            Spacer(minLength: 0)

            if response.renderSvg != nil {
                PebbleReadPetroglyph(
                    renderSvg: response.renderSvg,
                    renderVersion: response.renderVersion,
                    valence: valence,
                    palette: emotionId.flatMap { palettes.palette(for: $0) }
                )
                .frame(maxWidth: .infinity)
                .frame(height: 280)
            }

            VStack(spacing: Spacing.sm) {
                // User-authored, so never localized.
                Text(verbatim: name)
                    .pebblesFont(.title)
                    .foregroundStyle(Color.system.foreground)
                    .multilineTextAlignment(.center)

                if let karma = response.karmaDelta, karma > 0 {
                    HStack(spacing: 6) {
                        Image(systemName: "sparkle")
                            .foregroundStyle(Color.accent.primary)
                        Text("+\(karma) karma")
                            .pebblesFont(.headline)
                            .foregroundStyle(Color.system.foreground)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel(Text("Earned \(karma) karma"))
                }
            }

            Spacer(minLength: 0)

            Button("Back to my path", action: onExit)
                .buttonStyle(PebblesPrimaryButtonStyle())
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.bottom, Spacing.lg)
    }
}
```

- [ ] **Step 3: Build and fill in the new strings**

```bash
cd apps/ios && xcodegen generate && xcodebuild build -scheme Pebbles \
  -destination 'generic/platform=iOS Simulator' 2>&1 | grep -E "error:|BUILD" | head -10
```

Expected: `BUILD SUCCEEDED`. New keys and their French:

| Key | French |
|---|---|
| `Back to my path` | `Retour à mon chemin` |
| `Earned %lld karma` | `%lld karma gagnés` |

`+%lld karma` already exists in the catalog from `KarmaEarnedCapsule` — reuse it rather than adding a second key. Confirm:

```bash
cd apps/ios && grep -c '"+%lld karma"' Pebbles/Resources/Localizable.xcstrings
```

Expected: `1`.

- [ ] **Step 4: Run the karma suite to confirm the default argument changed nothing**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests/KarmaNotificationServiceTests 2>&1 | tail -15
```

Expected: passes unchanged.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Record/Steps/RecordSuccessStep.swift \
        apps/ios/Pebbles/Features/Karma/KarmaNotificationService.swift \
        apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): record flow success step with the pebble drawing on (M58)

Refs #723"
```

---
## Task 16: RecordFlowView

The container: chrome, the per-step title and action table, publish orchestration, the close confirmation and the restore prompt.

The scaffold is applied here rather than inside each step because the action logic — which steps offer `Skip` versus `Done`, when `Publish` is enabled, when it shows a spinner — is one thing, and scattering it across eleven files is how it stops agreeing with itself.

**Files:**
- Create: `apps/ios/Pebbles/Features/Record/RecordFlowView.swift`

- [ ] **Step 1: Write the view**

Create `apps/ios/Pebbles/Features/Record/RecordFlowView.swift`:

```swift
import SwiftUI
import os

/// The step-by-step pebble composer (M58) — the default way to record a pebble
/// on iOS. `CreatePebbleSheet` remains reachable by long-pressing the `+` (D1).
///
/// Presented as a `fullScreenCover`. Owns the coordinators the flow needs and
/// the orchestration between them; everything about *the flow itself* — gating,
/// back, skip labels, resume, haptics — lives on `RecordFlowModel`.
struct RecordFlowView: View {
    /// Fired as soon as the pebble publishes, while the success step is still
    /// up, so the Path is already reloaded by the time the user exits (D10).
    let onPublished: (UUID) -> Void

    /// Resuming a server draft: its payload hydrates the flow at the first
    /// unanswered step, and the row is deleted once the pebble publishes (D9).
    var resuming: PebbleDraftRecord?

    @Environment(SupabaseService.self) private var supabase
    @Environment(ReferenceDataService.self) private var refs
    @Environment(KarmaNotificationService.self) private var karma
    @Environment(AchievementsService.self) private var achievements
    @Environment(PebbleDraftsService.self) private var draftsService
    @Environment(ComposerSnapshotStore.self) private var snapshots
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss

    @State private var model = RecordFlowModel()
    @State private var selectedGlyph: Glyph?

    /// Lazily constructed in `.task` so they have `supabase.client`.
    @State private var snaps: SnapUploadCoordinator?
    @State private var drafts: ComposerDraftCoordinator?

    @State private var isPhotoPickerPresented = false
    @State private var isCloseConfirmPresented = false
    /// Drives the step transition direction so back slides back.
    @State private var isMovingBack = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "record-flow")

    private var currentUserId: UUID? { supabase.session?.user.id }
    private var hasSnapNow: Bool { snaps?.formSnap != nil }

    private var draftPayload: PebbleDraftPayload {
        PebbleDraftPayload(from: model.draft, formSnap: snaps?.formSnap, userId: currentUserId)
    }

    private var isSavableAsDraft: Bool {
        model.draft.isSavableAsDraft(formSnap: snaps?.formSnap, userId: currentUserId)
    }

    private var knownIds: PebbleDraft.KnownIds {
        PebbleDraft.KnownIds(
            soulIds: Set(refs.souls.map(\.id)),
            collectionIds: Set(refs.collections.map(\.id))
        )
    }

    /// Non-nil while the attached photo blocks publishing. Same two rules the
    /// sheet enforces — a snap must be neither in flight nor failed.
    private var snapBlockedMessage: String? {
        if snaps?.isUploading == true { return String(localized: "Photo is still uploading.") }
        if snaps?.hasFailed == true { return String(localized: "Photo upload failed. Retry or remove it.") }
        return nil
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            if model.step != .success {
                RecordFlowChrome(step: model.step, onBack: { model.back() }, onClose: handleClose)
            }
            stepBody
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.system.background)
        .pebblesScreen()
        .animation(.snappy(duration: 0.28), value: model.step)
        .onChange(of: model.step) { old, new in
            isMovingBack = new.rawValue < old.rawValue
        }
        .task {
            TapHaptics.prepare()
            if snaps == nil {
                snaps = SnapUploadCoordinator(repo: PebbleSnapRepository(client: supabase.client))
            }
            if drafts == nil {
                drafts = ComposerDraftCoordinator(
                    client: supabase.client, drafts: draftsService, snapshots: snapshots
                )
            }
            hydrateOrOfferRestore()
        }
        // `.task` may have run before the reference fetch settled.
        .onChange(of: refs.hasLoaded) { _, _ in hydrateOrOfferRestore() }
        .onChange(of: hasSnapNow) { _, newValue in model.hasSnap = newValue }
        .onChange(of: draftPayload) { _, newValue in
            // Held off while the restore prompt is up so the pending answer is
            // not overwritten first.
            guard drafts?.isRestorePromptPresented != true else { return }
            drafts?.schedule(newValue)
        }
        .onChange(of: scenePhase) { _, phase in
            // Last reliable moment before a process kill.
            if phase != .active { drafts?.flush() }
        }
        .sheet(isPresented: $isPhotoPickerPresented) {
            PhotoPickerView { picked in
                isPhotoPickerPresented = false
                guard let picked, let userId = currentUserId, let snaps else { return }
                Task {
                    await snaps.handlePicked(picked, userId: userId)
                    // Seed the date step from the photo before the user gets there (D7).
                    model.applyCaptureDate(snaps.pickedCaptureDate)
                }
            }
        }
        .alert("Pick up where you left off?", isPresented: Binding(
            get: { drafts?.isRestorePromptPresented ?? false },
            set: { drafts?.isRestorePromptPresented = $0 }
        )) {
            Button("Restore it") { restoreSnapshot() }
            Button("Start fresh", role: .destructive) { drafts?.discardSnapshot() }
        } message: {
            Text("We kept what you were writing here. Add your photo again when you're ready.")
        }
        .confirmationDialog(
            "Keep this pebble?",
            isPresented: $isCloseConfirmPresented,
            titleVisibility: .visible
        ) {
            Button("Save as draft") { Task { await saveAsDraftAndClose() } }
            Button("Discard", role: .destructive) { Task { await cancelAndCleanup() } }
            Button("Keep going", role: .cancel) {}
        }
    }

    // MARK: - Steps

    @ViewBuilder
    private var stepBody: some View {
        Group {
            if model.step == .success, let response = model.published {
                RecordSuccessStep(
                    name: model.draft.name,
                    response: response,
                    valence: model.draft.valence ?? .neutralMedium,
                    emotionId: model.draft.emotionId,
                    onExit: { dismiss() }
                )
            } else {
                RecordStepScaffold(
                    title: title(for: model.step),
                    subtitle: subtitle(for: model.step),
                    action: action(for: model.step)
                ) {
                    content(for: model.step)
                }
            }
        }
        .id(model.step)
        .transition(.asymmetric(
            insertion: .move(edge: isMovingBack ? .leading : .trailing).combined(with: .opacity),
            removal: .move(edge: isMovingBack ? .trailing : .leading).combined(with: .opacity)
        ))
    }

    @ViewBuilder
    private func content(for step: RecordStep) -> some View {
        switch step {
        case .photo:
            RecordPhotoStep(
                snap: snaps?.formSnap,
                onPick: { isPhotoPickerPresented = true },
                onRetry: {
                    if let userId = currentUserId, let snaps {
                        Task { await snaps.retryCurrent(userId: userId) }
                    }
                },
                onRemove: {
                    if let userId = currentUserId, let snaps {
                        Task { await snaps.removePending(userId: userId) }
                    }
                }
            )
        case .when:
            RecordWhenStep(
                happenedAt: Binding(
                    get: { model.draft.happenedAt },
                    set: { model.draft.happenedAt = $0 }
                ),
                seededFromPhoto: snaps?.pickedCaptureDate != nil
            )
        case .name:
            RecordNameStep(
                name: model.draft.name,
                limit: RecordFlowModel.nameLimit,
                onChange: { model.setName($0) }
            )
        case .valence:    RecordValenceStep(model: model)
        case .emotion:    RecordEmotionStep(model: model)
        case .domain:     RecordDomainStep(model: model)
        case .souls:      RecordSoulsStep(model: model)
        case .collection: RecordCollectionStep(model: model)
        case .glyph:      RecordGlyphStep(model: model, selectedGlyph: $selectedGlyph)
        case .privacy:    RecordPrivacyStep(model: model, snapBlockedMessage: snapBlockedMessage)
        case .success:    EmptyView()  // handled above; the success step owns its own layout
        }
    }

    private func title(for step: RecordStep) -> LocalizedStringResource {
        switch step {
        case .photo:      return "Start with a picture"
        case .when:       return "When did it happen?"
        case .name:       return "What do you call it?"
        case .valence:    return "How did it land?"
        case .emotion:    return "What did you feel?"
        case .domain:     return "What part of life?"
        case .souls:      return "Anyone in this one?"
        case .collection: return "Add it to a collection?"
        case .glyph:      return "Give it a glyph"
        case .privacy:    return "Who can see it?"
        case .success:    return "Your pebble"
        }
    }

    private func subtitle(for step: RecordStep) -> LocalizedStringResource? {
        switch step {
        case .photo:   return "Or skip it and write from memory."
        case .valence: return "How much of your life did this take up?"
        case .glyph:   return "A little mark, just for this one."
        default:       return nil
        }
    }

    /// The one action a step offers, if any. Tile steps offer none — the pick
    /// is the advance (D3).
    private func action(for step: RecordStep) -> RecordStepAction? {
        switch step {
        case .valence, .emotion, .domain, .success:
            return nil

        case .when:
            return .primary("Continue", enabled: true, loading: false) { model.advance() }

        case .name:
            return .primary("Continue", enabled: model.isAnswered, loading: false) { model.advance() }

        case .privacy:
            return .primary(
                "Publish",
                enabled: snapBlockedMessage == nil && model.draft.isValid,
                loading: model.isPublishing
            ) {
                Task { await publish() }
            }

        case .photo, .souls, .collection, .glyph:
            return .text(model.optionalButtonIsSkip ? "Skip" : "Done") { model.advance() }
        }
    }

    // MARK: - Drafts

    private func hydrateOrOfferRestore() {
        guard let drafts,
              let decision = drafts.hydrate(resuming: resuming, refsLoaded: refs.hasLoaded)
        else { return }

        switch decision {
        case .resume(let payload):
            model.resume(from: payload, known: knownIds)
            if let existing = payload.existingSnap {
                snaps?.seedExisting(.existing(id: existing.id, storagePath: existing.storagePath))
                model.hasSnap = true
            }
            Task { await verifyGlyph() }
        case .offerRestore, .fresh:
            break
        }
    }

    private func restoreSnapshot() {
        guard let snapshot = drafts?.takeRestorableSnapshot() else { return }
        model.resume(from: snapshot, known: knownIds)
        Task { await verifyGlyph() }
    }

    private func verifyGlyph() async {
        guard let glyphId = model.draft.glyphId, let userId = currentUserId, let drafts else { return }
        switch await drafts.verifyGlyph(glyphId: glyphId, userId: userId) {
        case .usable(let glyph):
            selectedGlyph = glyph
        case .unusable:
            model.draft.glyphId = nil
            selectedGlyph = nil
        case .unknown:
            break
        }
    }

    // MARK: - Leaving

    /// `✕` only asks when there is something to keep (D9).
    private func handleClose() {
        if isSavableAsDraft {
            isCloseConfirmPresented = true
        } else {
            Task { await cancelAndCleanup() }
        }
    }

    private func saveAsDraftAndClose() async {
        guard let drafts, let userId = currentUserId else {
            logger.error("save draft: no coordinator or no current user id")
            dismiss()
            return
        }
        // Deliberately no snap cleanup: the draft references that snap.
        if let message = await drafts.saveAsDraft(payload: draftPayload, userId: userId) {
            model.fail(message)
        } else {
            dismiss()
        }
    }

    private func cancelAndCleanup() async {
        if let userId = currentUserId, let snaps {
            await snaps.cancelAndCleanup(userId: userId)
        }
        drafts?.discardSnapshot()
        dismiss()
    }

    // MARK: - Publish

    private func publish() async {
        guard let userId = currentUserId else {
            logger.error("publish: no current user id")
            model.fail(String(localized: "You must be signed in to save."))
            return
        }
        if let blocked = snapBlockedMessage {
            logger.notice("publish blocked by snap state")
            model.fail(blocked)
            return
        }

        model.beginPublish()
        do {
            let response = try await PebblePublisher(client: supabase.client)
                .publish(draft: model.draft, formSnap: snaps?.formSnap, userId: userId)
            // The success step shows the amount, so the pastille would be
            // redundant — but the sound and haptic still fire (D10).
            karma.notifyEarned(
                amount: response.karmaDelta ?? 0, reason: .pebbleCreated, presentsCapsule: false
            )
            achievements.fireCheck()
            await drafts?.consumeAfterPublish()
            model.succeed(with: response)
            // Reload the Path behind the cover so it is fresh on exit.
            onPublished(response.pebbleId)
        } catch {
            logger.error("publish failed: \(error.localizedDescription, privacy: .private)")
            if let snaps { await snaps.handleSaveFailure(userId: userId) }
            model.fail(userMessageForPebbleSaveError(error))
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build
cd apps/ios && xcodegen generate && xcodebuild build -scheme Pebbles \
  -destination 'generic/platform=iOS Simulator' 2>&1 | grep -E "error:|BUILD" | head -20
```

Expected: `BUILD SUCCEEDED`.

If `.onChange(of: draftPayload)` fails to compile, `PebbleDraftPayload` needs `Equatable` — it already declares it, so a failure there means a nested type lost the conformance. Fix the nested type, not the call site.

- [ ] **Step 3: Fill in the new strings**

Open `Localizable.xcstrings` in Xcode. Every key below will be in the `New` state; give each its French and mark it translated:

| Key | French |
|---|---|
| `Start with a picture` | `Commence par une photo` |
| `Or skip it and write from memory.` | `Ou passe et écris de mémoire.` |
| `When did it happen?` | `C'était quand ?` |
| `What do you call it?` | `Tu l'appelles comment ?` |
| `How did it land?` | `Ça t'a fait quoi ?` |
| `How much of your life did this take up?` | `Ça a pris quelle place dans ta vie ?` |
| `What did you feel?` | `Tu as ressenti quoi ?` |
| `What part of life?` | `Quel domaine de ta vie ?` |
| `Anyone in this one?` | `Il y avait quelqu'un ?` |
| `Add it to a collection?` | `Dans une collection ?` |
| `Give it a glyph` | `Donne-lui un glyphe` |
| `A little mark, just for this one.` | `Une petite marque, rien que pour lui.` |
| `Who can see it?` | `Qui peut le voir ?` |
| `Your pebble` | `Ton galet` |
| `Continue` | `Continuer` |
| `Publish` | `Publier` |
| `Skip` | `Passer` |
| `Keep this pebble?` | `Tu gardes ce galet ?` |
| `Discard` | `Jeter` |
| `Keep going` | `Continuer l'édition` |
| `Uploading…` | `Envoi en cours…` |
| `That photo didn't upload.` | `Cette photo n'a pas été envoyée.` |
| `Choose another` | `En choisir une autre` |
| `A photo is already attached.` | `Une photo est déjà jointe.` |
| `Taken from your photo` | `Récupéré depuis ta photo` |
| `You don't have any collections yet.` | `Tu n'as pas encore de collection.` |
| `%lld of %lld characters` | `%lld caractères sur %lld` |

`Save as draft`, `Restore it`, `Start fresh`, `Pick up where you left off?`, `Retry`, `Remove`, `Name`, `Add a photo`, `Photo is still uploading.` and `Photo upload failed. Retry or remove it.` already exist from the sheet — reuse them rather than adding duplicates. Confirm none of them appear twice:

```bash
cd apps/ios && for key in "Save as draft" "Restore it" "Start fresh" "Add a photo" "Retry" "Remove"; do
  printf '%s: ' "$key"; grep -c "\"$key\" :" Pebbles/Resources/Localizable.xcstrings
done
```

Expected: `1` for each.

Then confirm nothing is left in `New` or `Stale`:

```bash
cd apps/ios && python3 -c "
import json
d = json.load(open('Pebbles/Resources/Localizable.xcstrings'))
bad = []
for key, entry in d['strings'].items():
    locs = entry.get('localizations', {})
    for lang in ('en', 'fr'):
        state = locs.get(lang, {}).get('stringUnit', {}).get('state')
        if state in (None, 'new', 'stale'):
            bad.append((key, lang, state))
print(f'{len(bad)} incomplete entries')
for row in bad[:20]: print(' ', row)
"
```

Expected: `0 incomplete entries`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Record/RecordFlowView.swift \
        apps/ios/Pebbles/Features/Record/RecordStepScaffold.swift \
        apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): record flow container (M58)

Chrome, the per-step title and action table, publish orchestration, the close
confirmation and the restore prompt.

Refs #723"
```

---
## Task 17: Wire the flow into the Path

The `+` opens the flow, a long-press opens the classic sheet, and draft resume enters the flow (D1, D9).

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Components/NewPebbleButton.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/DraftsListSheet.swift`

- [ ] **Step 1: Give the button its long-press escape hatch**

Replace `apps/ios/Pebbles/Features/Path/Components/NewPebbleButton.swift`'s `onTap` with two closures:

```swift
struct NewPebbleButton: View {
    let onTap: () -> Void
    /// Opens the classic all-at-once composer (D1). Deliberately undiscoverable:
    /// this is an escape hatch for the duration of the flow experiment, not a
    /// feature, and it deletes in one line when the experiment resolves.
    var onLongPress: () -> Void = {}
```

and add the gesture to the existing `Button`, after `.buttonStyle(.plain)`:

```swift
        .simultaneousGesture(
            LongPressGesture(minimumDuration: 0.6).onEnded { _ in onLongPress() }
        )
```

Leave the accessibility label alone — the long-press is not an advertised action.

- [ ] **Step 2: Rewire PathView**

In `apps/ios/Pebbles/Features/Path/PathView.swift`:

Add a second presentation flag beside `isPresentingCreate`:

```swift
    /// The record flow (M58) — what `+` opens.
    @State private var isPresentingFlow = false
    /// The classic composer, reachable by long-pressing `+` (D1).
    @State private var isPresentingCreate = false
```

Add the cover beside the existing sheet, keeping the sheet exactly as it is:

```swift
        .fullScreenCover(isPresented: $isPresentingFlow) {
            RecordFlowView(onPublished: { newPebbleId in
                // No detail sheet: the user just spent ten screens on this
                // pebble and the success step already showed it (D10).
                Task {
                    async let timeline: Void = load()
                    async let statsReload: Void = stats.refresh()
                    _ = await (timeline, statsReload)
                    focusWeek(containing: newPebbleId)
                }
            })
        }
```

Point the two `+` entry points at the flow, and the long-press at the sheet:

```swift
                            onCreate: { isPresentingFlow = true }
```

```swift
                    NewPebbleButton(
                        onTap: { isPresentingFlow = true },
                        onLongPress: { isPresentingCreate = true }
                    )
```

Add the week-focus helper beside `load()`:

```swift
    /// Focus the week the new pebble landed in, so exiting the flow puts the
    /// user where their pebble is rather than wherever they happened to be.
    /// Silent no-op if the timeline reload has not yet surfaced it.
    private func focusWeek(containing pebbleId: UUID) {
        guard let pebble = pebbles.first(where: { $0.id == pebbleId }),
              let week = isoCalendar.dateInterval(of: .weekOfYear, for: pebble.happenedAt)?.start,
              entries.contains(where: { $0.weekStart == week })
        else { return }
        focusedWeekStart = week
    }
```

Check the property name on `Pebble` before relying on it:

```bash
cd apps/ios && grep -n "happenedAt\|createdAt" Pebbles/Features/Path/Models/Pebble.swift
```

If the timeline model exposes only `createdAt`, use that instead — the week roll groups on whatever `WeekRollBuilder` groups on, so match it:

```bash
cd apps/ios && grep -n "weekStart\|happenedAt\|createdAt" Pebbles/Features/Path/WeekRollBuilder.swift | head
```

- [ ] **Step 3: Point draft resume at the flow**

In `apps/ios/Pebbles/Features/Path/DraftsListSheet.swift`, replace the `.sheet(item: $resuming)` presentation with a cover onto the flow, and update the doc comment on line 8:

```swift
/// Tapping a row resumes it in `RecordFlowView`, which lands on the first
/// unanswered step (D9).
```

```swift
        .fullScreenCover(item: $resuming) { draft in
            RecordFlowView(
                onPublished: { pebbleId in
                    resuming = nil
                    onPebbleCreated(pebbleId)
                },
                resuming: draft
            )
        }
```

Keep the `.onChange(of: resuming)` handler exactly as it is.

- [ ] **Step 4: Build and lint**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build
cd apps/ios && xcodegen generate && xcodebuild build -scheme Pebbles \
  -destination 'generic/platform=iOS Simulator' 2>&1 | grep -E "error:|BUILD" | head -20
npm run lint --workspace=@pbbls/ios
```

Expected: `BUILD SUCCEEDED` and zero SwiftLint violations.

- [ ] **Step 5: Drive the flow on the simulator**

Launch and walk the whole thing. This is the first moment the flow is reachable, so check the behaviors that no unit test covers:

1. `+` opens the flow; **long-press** `+` opens the old sheet.
2. Step 0 → pick a photo from the simulator's library → the thumbnail appears, the button changes `Skip` → `Done`, and the step does **not** auto-advance.
3. Step 1 shows the photo's date, not today, with the "Taken from your photo" note. (Seed the library with a dated image first: drag a photo with EXIF onto the simulator.)
4. Step 2 refuses to continue on whitespace; the counter stops at 40.
5. Steps 3–5 advance on tap; going back keeps the answer highlighted; the back transition slides the other way.
6. Step 4's categories are ordered by the valence picked on step 3.
7. Step 6 multi-selects and needs `Done`; step 7 and 8 advance on tap and offer `Skip`.
8. Step 9 selects a grade without publishing; `Publish` is disabled while a photo is uploading.
9. Step 10 draws the pebble on, shows `+N karma`, and there is **no** karma pastille over it — but the sound and haptic fire.
10. Exiting lands on the Path with the new pebble present and its week focused, and **no** detail sheet.
11. Haptics: every tap in 1–10 buzzes. Selection taps feel lighter than the publish confirmation.
12. `✕` mid-flow offers Save as draft / Discard / Keep going; `✕` on an untouched flow closes with no dialog.
13. A saved draft resumes from the drafts list into the flow, at the first unanswered step.

Fix anything that fails here before moving on — these are the behaviors the flow exists for.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/NewPebbleButton.swift \
        apps/ios/Pebbles/Features/Path/PathView.swift \
        apps/ios/Pebbles/Features/Path/DraftsListSheet.swift
git commit -m "feat(ios): the record flow is the default composer (M58)

+ opens the flow and draft resume enters it at the first unanswered step;
long-pressing + still opens the classic sheet.

Refs #723"
```

---

## Task 18: Arkaik map

A new user-visible screen means the product map changes in the same PR (`CLAUDE.md`, large-task triage).

**Files:**
- Modify: `docs/arkaik/bundle.json`

- [ ] **Step 1: Load the skill and read the current shape**

```bash
cd /Users/alexis/code/pbbls && python3 -c "
import json
b = json.load(open('docs/arkaik/bundle.json'))
print('top-level keys:', list(b))
views = [n for n in b.get('nodes', []) if n.get('type') == 'view']
print(f'{len(views)} view nodes; composer-ish ones:')
for n in views:
    label = json.dumps(n)[:200]
    if any(w in label.lower() for w in ('pebble', 'record', 'compose', 'create', 'path')):
        print(' ', n.get('id'), '|', n.get('name') or n.get('label'))
"
```

Invoke the repo-local `arkaik` skill and follow it — it is the source of truth for node shape, edge shape, and the journal event format. Do not hand-author against a guess at the schema.

- [ ] **Step 2: Add the node, the edge and the journal event**

Add one `view` node for the record flow, an edge from the Path view node to it, and the matching journal event. Keep the existing `CreatePebbleSheet` node — it is still reachable (D1) — and note in its description that it is now the long-press fallback.

`docs/arkaik/bundle.json` is a formatting-sensitive catalog: edit it as text at anchors, or through the skill's own tooling. A whole-file `json.dump` reformats it and the `arkaik.yml` CI job will flag the diff.

- [ ] **Step 3: Validate**

```bash
cd /Users/alexis/code/pbbls && git diff --numstat docs/arkaik/bundle.json
```

Expected: insertions only (plus the small edit to the sheet node's description). The `arkaik.yml` workflow validates the bundle and journal on push; if a local validator exists, run it now rather than waiting for CI.

- [ ] **Step 4: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "docs(arkaik): map the iOS record flow (M58)

Refs #723"
```

---

## Task 19: Verify and open the PR

- [ ] **Step 1: Full clean verification**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build
cd /Users/alexis/code/pbbls
npm run build --workspace=@pbbls/ios 2>&1 | tail -5
npm run test  --workspace=@pbbls/ios 2>&1 | grep -E "Executed|failed|passed" | tail -10
npm run lint  --workspace=@pbbls/ios
```

Expected: `BUILD SUCCEEDED`, every suite passing, zero SwiftLint violations. Do not proceed on a partial pass — record the actual output.

- [ ] **Step 2: Confirm the localization catalog is complete**

```bash
cd apps/ios && python3 -c "
import json
d = json.load(open('Pebbles/Resources/Localizable.xcstrings'))
bad = [(k, l, s) for k, e in d['strings'].items()
       for l in ('en', 'fr')
       for s in [e.get('localizations', {}).get(l, {}).get('stringUnit', {}).get('state')]
       if s in (None, 'new', 'stale')]
print(f'{len(bad)} incomplete entries')
for row in bad[:20]: print(' ', row)
"
```

Expected: `0 incomplete entries`. Open the catalog in Xcode and confirm visually as well — the state machine above does not catch an entry Xcode considers stale for a different reason.

- [ ] **Step 3: Check the decision log**

Read `docs/decisions/log.md`. This PR establishes one decision worth recording under its own bar ("would a future agent waste real time rediscovering or wrongly reversing it?"): **two composers coexist on iOS, the flow by default and the sheet behind a long-press, deliberately and temporarily.** A future agent finding two composers will otherwise assume one is dead code and delete it.

Append one entry — supersede-don't-edit, never modify a prior one.

- [ ] **Step 4: Push and open the PR**

```bash
cd /Users/alexis/code/pbbls
git push -u origin feat/723-ios-record-flow
```

Then open the PR with the title `feat(ios): step-by-step pebble record flow (M58)`, inheriting #723's labels (`feat`, `ui`, `ios`) and milestone (M58), with a body that starts `Resolves #723`, lists the key files, and carries the Lab Note below.

- [ ] **Step 5: Write the Lab Note**

The PR has the `feat` label and touches a user-visible Arkaik view node, so a Lab Note is required. Invoke the repo-local `lab-note` skill — it takes precedence over the plugin skill — and author from it. One `## Lab Note (EN/FR)` section, exactly one ```yaml fence, every title and summary double-quoted, no em dashes, French in the informal "Tu" and adapted rather than translated.

Starting point, to be reworked through the skill's tone guidance rather than pasted as-is:

```yaml
species: feature
platform: ios
status: in_progress
published: false
en:
  title: "Recording a pebble, one question at a time"
  summary: "Start from a photo and answer one thing per screen, all the way to watching your pebble draw itself. Your photo even fills in the date for you."
fr:
  title: "Créer un galet, une question à la fois"
  summary: "Pars d'une photo et réponds à une seule chose par écran, jusqu'à voir ton galet se dessiner. Ta photo remplit même la date à ta place."
suggested:
  molecule: pbbls
  type: feature
  tags: [changelog]
```

- [ ] **Step 6: Confirm the PR is complete**

```bash
gh pr view --json title,labels,milestone,body --jq '{title, labels: [.labels[].name], milestone: .milestone.title, hasLabNote: (.body | contains("## Lab Note"))}'
```

Expected: conventional-commit title, labels `feat`/`ui`/`ios`, milestone `M58 · Dynamic and picture-first Path`, and `hasLabNote: true`.

---

## Post-ship

Append a **Lessons learned** section to this plan once the flow has been used for a while. The questions worth answering: did the two-composer coexistence stay temporary, or has the sheet acquired its own users? Did resume-to-first-gap match what people expected, or did they want to see their earlier answers? Is a haptic on *every* tap right, or does the flow buzz too much?
