# Interactive onboarding: iOS — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the seven-step onboarding of `docs/superpowers/specs/2026-11-04-interactive-onboarding-design.md` on iOS, in the corrected state Android reached after its device pass, and matching the approved mock.

**Architecture:** One stack of five parts on the live back end (`promise_letters`, `complete_onboarding`, the two `profiles` columns; nothing server-side changes). (1) models, payload encoder, service and the pure gate, (2) the shell with steps 1–3, (3) the split-stone grid with steps 4–5 and the satellites, (4) step 6 and the reference renders, (5) the root gate, the hand-off into the quick moment, and the old slides deleted. Nothing is reachable from the app until Part 5 flips the gate, so every lower part is non-regressive.

**Tech stack:** SwiftUI, iOS 17 APIs only, `@Observable`, Swift Testing, `supabase-swift`, `ImageRenderer` reference renders.

**Issue:** #1565 (part of #1510, replaces #1466). Milestone `M68 · V1 launch`.

---

> **Read with "Lessons learned" at the end.** The build departed from this text in several places, on review and on the device pass. The code and the 2026-11-06 decision log entry are the record.

## Read this first: what went wrong on Android

The Android walk passed on logic while the design had drifted: "the implementation is contradicting the current implemented components or not complying with the approved designs." Plan text and two reviews never opened the mock. So:

**Every UI task renders its screen and compares it with the mock, side by side, before it reports done, and reviewers open both images.** Mock frames at 390×844:

```
/private/tmp/claude-503/-Users-alexis-code-pbbls/a0121ee8-1c71-45ad-90de-0e5e226c929f/scratchpad/onboarding/mock/
  1-promise.png  2-consent.png  3-reasons.png  4-you.png  4-you-keyboard.png
  5-people.png  5-people-typed.png  6-pace.png  s1-carve.png  s2-picker.png
```

Android's accepted result, for comparison: `apps/android/app/src/screenshotTestDebug/reference/app/pbbls/android/FunnelScreenshotsKt/Onboarding{Promise,Consent,Reasons,You,People,Pace}_611a7511_0.png`.

**When sources disagree:**

| Question | Winner |
|---|---|
| Behaviour, state rules, copy | Shipped Android (`apps/android/.../features/onboarding/`, `res/values*/strings.xml`), then the spec, then the mock |
| Look (layout, type rank, shape, colour role, spacing) | The mock, then Android |
| A control the app already has | The app's own iOS component, never a redraw: native `Toggle` with `.tint(Color.pebbles.primary)`, `PebbleToolbarButton` in a real `.toolbar` (Liquid Glass), native `.sheet`, `PebblesPrimaryButtonStyle` |

**The Android corrections, each already decided. Do not rediscover them:**

1. **Consent is recorded on Continue, not per flip** (the spec's D3 text is stale). A switch turns on and off freely and nothing reaches the ledger until Continue. An act already on record stays on and locked, reading "Already on record".
2. **The copy is the table below, not the spec or plan text.** The mock's "You can turn any of them off later in Settings" is false (only `health_data` is withdrawable, iOS has no withdrawal until #1009) and must not ship.
3. **RLS trap:** an anon read returns `200 []` with no error. The glyphs and the ledger must be read only once the session is live, or the stones draw blank and acts already on record are re-asked. iOS presents the flow only after `supabase.session` is set; still, an empty glyph list logs an error and retries on entering `you` and `people`, and once more before `complete_onboarding`.
4. **A person is never sent without a glyph** (`souls.glyph_id` is not null). A named person wears the `index`th system glyph until a pick (three people never look alike). If there are still no glyphs at completion, show the error; never send an empty id.
5. **System glyph names are internal keys ("domain:family") and are never spoken.** VoiceOver reads "Glyph N" for a system glyph (`userId == nil`), the name for one the user carved.
6. **Legal links open in the app's language:** `?lang=fr|en` on the URL.
7. **Dark mode:** the shell paints `Color.pebbles.surface` itself. Android's titles and Back were unreadable until the renders caught it. Render dark.
8. **The pace group is vertical**, with its own corner shapes (32 outer, 10 inner, the chosen row a capsule).
9. **Reasons keep pick order**, because the first one picked chooses the hand-off prompt. Store an array. **Never iterate a `Set` while drawing a reference's figure** (per-process hash seed breaks the re-baseline bot).
10. **The hand-off note is bound to its user** and consumed once, so a session ending between the two never opens B's composer with A's prompt.
11. **Chrome holds while completing:** Back and Skip/Later are disabled while `complete_onboarding` or the consent write is in flight.
12. **D9 is prompt only:** `save_moment` has no `soul_ids`. The hand-off attaches nobody and must not claim to.

**Two iOS decisions (maintainer, 2026-11-06):**
- D9: prompt only, the same as Android.
- **Step 2 keeps a quiet "Log out"** under Continue while any act is missing (a departure from the mock). It is the exit the iOS gate offers today (`ConsentGateView`: "Declining is Log out").

**Known differences from the mock, shared with Android, left as is and shown to the maintainer at the device pass:** no "Signed in November 2026" line on the promise (nothing stores it); the promise's mark is the lit pebble icon; the reasons use the nearest drawn icons (heart, people, abstract-drizzle, sunshine, path, case).

---

## Working setup

| | |
|---|---|
| Worktree | `/Users/alexis/code/pbbls/.worktrees/onboarding-ios` (`apps/ios/Config/Secrets.xcconfig` already copied) |
| Simulator | `iPhone 17 onboarding`, `id=4FC027C0-E526-4574-A6D1-C02594376DA5`. Never the shared "iPhone 17" |
| DerivedData | `/private/tmp/claude-503/-Users-alexis-code-pbbls/a0121ee8-1c71-45ad-90de-0e5e226c929f/scratchpad/dd-onboarding`. Delete that folder, not the global one, before a trusted run |

Gate commands, from `apps/ios`:

```bash
DD=/private/tmp/claude-503/-Users-alexis-code-pbbls/a0121ee8-1c71-45ad-90de-0e5e226c929f/scratchpad/dd-onboarding
xcodegen generate
swiftlint                     # zero new violations
xcodebuild test -scheme Pebbles -destination 'id=4FC027C0-E526-4574-A6D1-C02594376DA5' \
  -derivedDataPath "$DD" -skip-testing:PebblesTests/References -quiet   # the whole non-reference suite
```

Filter a single suite with `-only-testing:PebblesTests/<SuiteType>`, at suite level (a single Swift Testing test matches nothing and passes). `-quiet` hides which tests failed: read the `.xcresult` with `xcrun xcresulttool get test-results summary`.

**References fail locally on untouched main** (27 of them on this Mac) and are recorded on CI only (label `rebaseline-ios-screenshots`). Never commit a locally rendered reference PNG. A new reference fails locally with no baseline and still writes its actual image under `apps/ios/build/reference-diffs/`: that image is what you compare with the mock.

**`ImageRenderer` draws neither `ScrollView` content, nor `TextField`, nor `List`, nor a `NavigationStack` root.** So steps take a `scrolls: Bool = true` and draw static text in place of a field when `false`, as `RecordStepScaffold` and `RecordMomentStep(drawsStaticFields:)` do. To see the real screen, use a temporary held-window test: a `@MainActor` Swift Testing test sets `UIHostingController(rootView: harness)` on the first scene's window and sleeps 40 s, while `xcrun simctl io <sim> screenshot <scratchpad>/x.png` runs from outside. Start it with `run_in_background`, and poll its log for `Test "…" started` before capturing. Delete it before committing.

**String catalog:** `apps/ios/Pebbles/Resources/Localizable.xcstrings` is formatting-sensitive. Insert each entry as text at its sorted anchor. Never `json.dump` the whole file, and never let a diff reorder it. Keys are the English literal, matching the record flow. Reuse an existing key when one exists (`Back`, `Skip`, `Later`, `Continue`, `Log out`, `Terms of Service`, `Privacy Policy`, `Step %lld of %lld`) and check that its French matches the table below.

**Commits:** conventional, lowercase, no period, ending with:

```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

(use your own model's name). Never push: the controller pushes. Stay on the branch you are given, and fix a lower layer on its own branch only when told to.

---

## Copy (verbatim, EN → FR, from shipped Android)

| EN (catalog key) | FR |
|---|---|
| Before anything else, | Avant toute chose, |
| a promise. | une promesse. |
| A promise | Une promesse |
| I'll hold you to it | Je t'en tiendrai responsable |
| Our promise could not load. It will be here in Settings. | Notre promesse n'a pas pu se charger. Tu la retrouveras dans les réglages. |
| Your consent | Ton consentement |
| One switch for each thing you agree to. | Un interrupteur pour chaque chose que tu acceptes. |
| Store how I feel | Garder ce que je ressens |
| Moods, emotions and what I write. Sensitive data under GDPR art. 9. | Humeurs, émotions et ce que j'écris. Données sensibles au sens de l'art. 9 du RGPD. |
| Terms of Service | Conditions d'utilisation |
| How Pebbles works and what we owe you. | Comment Pebbles fonctionne et ce qu'on te doit. |
| Privacy Policy | Politique de confidentialité |
| What we keep, where, and for how long. | Ce que l'on garde, où, et combien de temps. |
| I am 16 or over | J'ai 16 ans ou plus |
| Pebbles is not made for younger people. | Pebbles n'est pas fait pour les plus jeunes. |
| Already on record | Déjà enregistré |
| All four are needed to continue. | Les quatre sont nécessaires pour continuer. |
| Read the terms | Lire les conditions |
| Read the policy | Lire la politique |
| Your consent could not be saved. Check your connection and try again. | Ton consentement n'a pas pu être enregistré. Vérifie ta connexion et réessaie. |
| What brings you here? | Qu'est-ce qui t'amène ? |
| Pick all that fit. | Choisis tout ce qui te correspond. |
| Notice how I really feel | Remarquer ce que je ressens vraiment |
| Keep the people who matter | Garder les gens qui comptent |
| Get through a hard season | Traverser une période difficile |
| Remember the good days | Me souvenir des bons jours |
| See my own patterns | Voir mes propres schémas |
| Keep a record for later | Garder une trace pour plus tard |
| This is you | C'est toi |
| Your name | Ton prénom |
| Your glyph | Ton glyphe |
| Draw my own | Dessiner le mien |
| Draw | Dessiner |
| Glyph %lld | Glyphe %lld |
| That's me | C'est moi |
| Three places | Trois places |
| Who would you call first with good news? | À qui annoncerais-tu une bonne nouvelle en premier ? |
| First names only. They are never contacted. | Les prénoms seulement. Ces personnes ne sont jamais contactées. |
| Add one | Ajouter |
| Their name | Son prénom |
| Change glyph | Changer le glyphe |
| You can add more people later. | Tu pourras en ajouter d'autres plus tard. |
| These are my people | Ce sont mes proches |
| Keep %@, %@ and %@ | Garder %1$@, %2$@ et %3$@ |
| A glyph for %@ | Un glyphe pour %@ |
| It marks %@ on every pebble. | Il marque %@ sur chaque galet. |
| Draw one | En dessiner un |
| Use for %@ | Utiliser pour %@ |
| What pace suits you? | Quel rythme te va ? |
| Every day | Tous les jours |
| A small habit, evening or morning. | Une petite habitude, le soir ou le matin. |
| A few times a week | Quelques fois par semaine |
| When there's something worth it. | Quand quelque chose en vaut la peine. |
| Only when it matters | Seulement quand ça compte |
| No rhythm at all. Also fine. | Aucun rythme. Ça va aussi. |
| Wrapping the end of the week | En bouclant la fin de semaine |
| One look back, each Sunday. | Un regard en arrière, chaque dimanche. |
| No streaks, no guilt. | Pas de série, pas de culpabilité. |
| Couldn't finish. Check your connection and try again. | Impossible de terminer. Vérifie ta connexion et réessaie. |
| What did you feel today? | Qu'as-tu ressenti aujourd'hui ? |
| Who were you with today? | Avec qui étais-tu aujourd'hui ? |
| What was hard today? | Qu'est-ce qui a été dur aujourd'hui ? |
| What was good today? | Qu'est-ce qui a été bien aujourd'hui ? |
| What happened today? | Que s'est-il passé aujourd'hui ? |
| What do you want to remember from today? | Que veux-tu retenir d'aujourd'hui ? |

Each part adds only the entries its screens use.

---

## Part 1 — data, service, the pure gate

Branch `feat/1565-onboarding-data-ios`. PR `feat(ios): onboarding answers, service and the completed gate`. Model: sonnet.

### Task 1.1: Models and the payload encoder, tested against the harness's bytes

**Files:**
- Create: `apps/ios/Pebbles/Features/Onboarding/OnboardingModels.swift`
- Create: `apps/ios/PebblesTests/Fixtures/complete-onboarding-payload.json` (byte copy of `packages/supabase/scripts/oracles/onboarding/payloads/complete_onboarding.json`)
- Test: `apps/ios/PebblesTests/Features/Onboarding/OnboardingPayloadTests.swift`

- [ ] **Step 1: Copy the fixture and write the failing test.** Read the fixture through `#filePath` as `PebblesTests/SettingsLinkTests.swift` does (`project.yml` already excludes `Fixtures/*.json` from the bundle). Cases:
  - Build `OnboardingAnswers` from the oracle's values (display name `"  Maya  "` as typed, reasons `.noticeFeelings, .keepPeople, .keepRecord`, pace `.weeklyWrap`, the three souls including `" Léa "`), encode `.params`, and assert the JSON equals the oracle as parsed objects. UUIDs are lowercase, as the oracle's are.
  - Everything skipped encodes to exactly `{"p_souls":[]}`: no null keys.
  - `OnboardingReason.allCases.map(\.rawValue)` and `OnboardingPace.allCases.map(\.rawValue)` equal the server's check lists, in grid order.
  - `PromiseLetter`: FR falls back to EN when nil or blank; `paragraphs(french:)` splits on blank lines, trims, and drops empties.

- [ ] **Step 2: Run it and watch it fail** (`-only-testing:PebblesTests/OnboardingPayloadTests`).

- [ ] **Step 3: Implement.**

```swift
import Foundation

/// The promise step's letter (#1510 D2), a `promise_letters` row. FR falls back to EN.
struct PromiseLetter: Decodable, Equatable, Sendable {
    let bodyEn: String
    let bodyFr: String?
    let signedByEn: String
    let signedByFr: String?

    enum CodingKeys: String, CodingKey {
        case bodyEn = "body_md_en", bodyFr = "body_md_fr"
        case signedByEn = "signed_by_en", signedByFr = "signed_by_fr"
    }

    func body(french: Bool) -> String { Self.pick(french: french, fr: bodyFr, en: bodyEn) }
    func signedBy(french: Bool) -> String { Self.pick(french: french, fr: signedByFr, en: signedByEn) }

    /// Plain paragraphs: the seeded letter uses no Markdown and the admin form says
    /// "one paragraph per blank line".
    func paragraphs(french: Bool) -> [String] {
        body(french: french).components(separatedBy: "\n\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    private static func pick(french: Bool, fr: String?, en: String) -> String {
        guard french, let fr, !fr.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return en }
        return fr
    }
}

/// Step 3's six tiles (D4), in grid order. The raw value is the wire value in
/// `profiles.onboarding_reasons`. Same icons as Android's `OnboardingReason`.
enum OnboardingReason: String, CaseIterable, Sendable {
    case noticeFeelings = "notice-feelings", keepPeople = "keep-people", hardSeason = "hard-season"
    case goodDays = "good-days", ownPatterns = "own-patterns", keepRecord = "keep-record"

    var label: LocalizedStringResource {
        switch self {
        case .noticeFeelings: return "Notice how I really feel"
        case .keepPeople: return "Keep the people who matter"
        case .hardSeason: return "Get through a hard season"
        case .goodDays: return "Remember the good days"
        case .ownPatterns: return "See my own patterns"
        case .keepRecord: return "Keep a record for later"
        }
    }

    var icon: PbblsIcon {
        switch self {
        case .noticeFeelings: return .heart
        case .keepPeople: return .people
        case .hardSeason: return .abstractDrizzle
        case .goodDays: return .sunshine
        case .ownPatterns: return .path
        case .keepRecord: return .case
        }
    }
}

/// Step 6's four paces (D11), in group order. The raw value is the wire value in `profiles.pace`.
enum OnboardingPace: String, CaseIterable, Sendable {
    case daily, fewAWeek = "few-a-week", whenItMatters = "when-it-matters", weeklyWrap = "weekly-wrap"

    var label: LocalizedStringResource {
        switch self {
        case .daily: return "Every day"
        case .fewAWeek: return "A few times a week"
        case .whenItMatters: return "Only when it matters"
        case .weeklyWrap: return "Wrapping the end of the week"
        }
    }

    var detail: LocalizedStringResource {
        switch self {
        case .daily: return "A small habit, evening or morning."
        case .fewAWeek: return "When there's something worth it."
        case .whenItMatters: return "No rhythm at all. Also fine."
        case .weeklyWrap: return "One look back, each Sunday."
        }
    }

    var icon: PbblsIcon {
        switch self {
        case .daily: return .sunshine
        case .fewAWeek: return .path
        case .whenItMatters: return .pebbleRound
        case .weeklyWrap: return .calendarDay
        }
    }
}

/// One first person (step 5). Always a glyph: `souls.glyph_id` is not null.
struct OnboardingSoulDraft: Equatable, Sendable {
    let name: String
    let glyphId: UUID
}

/// Everything the flow collected, as `complete_onboarding` takes it (D5). A skipped
/// step is nil and left out of the params, so the server sees its default; `souls` is
/// always sent, empty when step 5 was passed with "Later".
struct OnboardingAnswers: Equatable, Sendable {
    var displayName: String?
    var glyphId: UUID?
    var reasons: [OnboardingReason]?
    var pace: OnboardingPace?
    var souls: [OnboardingSoulDraft] = []

    var params: CompleteOnboardingParams { CompleteOnboardingParams(answers: self) }
}

struct CompleteOnboardingParams: Encodable, Sendable {
    let answers: OnboardingAnswers

    private enum CodingKeys: String, CodingKey {
        case displayName = "p_display_name", glyphId = "p_glyph_id", reasons = "p_reasons"
        case pace = "p_pace", souls = "p_souls"
    }

    private struct Soul: Encodable {
        let name: String
        let glyphId: String
        enum CodingKeys: String, CodingKey { case name, glyphId = "glyph_id" }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(answers.displayName, forKey: .displayName)
        try container.encodeIfPresent(answers.glyphId?.uuidString.lowercased(), forKey: .glyphId)
        try container.encodeIfPresent(answers.reasons?.map(\.rawValue), forKey: .reasons)
        try container.encodeIfPresent(answers.pace?.rawValue, forKey: .pace)
        try container.encode(
            answers.souls.map { Soul(name: $0.name, glyphId: $0.glyphId.uuidString.lowercased()) },
            forKey: .souls
        )
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes.** Add `Every day`-style strings to the catalog only when a screen uses them (Part 2 and later); a `LocalizedStringResource` literal compiles without an entry.

- [ ] **Step 5: Commit** `feat(ios): onboarding answers and the rpc payload against the harness bytes`.

### Task 1.2: Service, step config, the pure gate and its model

**Files:**
- Create: `apps/ios/Pebbles/Services/OnboardingService.swift`
- Create: `apps/ios/Pebbles/Features/Onboarding/OnboardingFlowSteps.swift`
- Create: `apps/ios/Pebbles/Features/Onboarding/OnboardingGate.swift`
- Create: `apps/ios/PebblesTests/Features/Onboarding/FakeOnboardingService.swift`
- Test: `apps/ios/PebblesTests/Features/Onboarding/OnboardingFlowStepsTests.swift`, `OnboardingGateTests.swift`

Old-slide types (`OnboardingStep`, `OnboardingSteps`, `OnboardingView`) stay until Part 5, so the new names carry `Flow`.

- [ ] **Step 1: Write the failing tests.**
  - `OnboardingFlowSteps`: seven steps, ids in order `promise, consent, reasons, you, people, pace, handoff`; skips `none, none, skip, skip, later, skip, none`; `visible` is every step but `handoff`.
  - `OnboardingGate.shouldPresent`: true only for a known user, not seen, `completed == false`; false while `completed` is nil.
  - `OnboardingGate.isPending`: true for a known user, not seen, `completed == nil`, no failed read; false once seen, once read, once the read failed, and with no user.
  - `OnboardingGateModel`: `start` reads the flag once per user id; a new user id forgets the last user's flag; `hasSeen == true` skips the read entirely; a thrown read sets `readFailed` (not pending, not presenting: the consent gate decides); `finish()` sets `hasSeen` and `completed = true`; `start(nil)` clears everything. Use a private `UserDefaults(suiteName:)` per test.

- [ ] **Step 2: Run them and watch them fail.**

- [ ] **Step 3: Implement.**

```swift
// OnboardingFlowSteps.swift
/// How a step can be passed without answering (#1510 D1).
enum OnboardingSkip: Equatable { case none, skip, later }

/// One step of the flow. A config so M69 can insert a reminders step after `pace`.
struct OnboardingFlowStep: Identifiable, Equatable {
    enum ID: String { case promise, consent, reasons, you, people, pace, handoff }
    let id: ID
    let skip: OnboardingSkip
}

enum OnboardingFlowSteps {
    static let all: [OnboardingFlowStep] = [
        .init(id: .promise, skip: .none), .init(id: .consent, skip: .none),
        .init(id: .reasons, skip: .skip), .init(id: .you, skip: .skip),
        .init(id: .people, skip: .later), .init(id: .pace, skip: .skip),
        .init(id: .handoff, skip: .none)
    ]
    /// The steps with a screen. `handoff` is the `complete_onboarding` call and Home (D9).
    static let visible = all.filter { $0.id != .handoff }
}
```

```swift
// OnboardingGate.swift
import Foundation
import Observation
import os

/// Pure gating for the flow (#1510 D10). `hasSeen` is the device flag the old slides already
/// wrote (`hasSeenOnboarding`): accounts from before the flow never wrote `onboarding_completed`,
/// and the flag is what keeps them from being re-onboarded on the same device.
enum OnboardingGate {
    static func shouldPresent(userId: String?, hasSeen: Bool, completed: Bool?) -> Bool {
        userId != nil && !hasSeen && completed == false
    }

    /// Still waiting to know. The app and the consent gate hold while this is true, so a new
    /// account never sees either before the promise (D2). A failed read is not pending: nothing
    /// is presented this launch, and the consent gate decides, failing closed.
    static func isPending(userId: String?, hasSeen: Bool, completed: Bool?, readFailed: Bool) -> Bool {
        userId != nil && !hasSeen && completed == nil && !readFailed
    }
}

/// Owned by `RootView`, like `ConsentGateModel`.
@Observable
@MainActor
final class OnboardingGateModel {
    private(set) var completed: Bool?
    private(set) var readFailed = false
    private(set) var hasSeen: Bool

    @ObservationIgnored var service: (any OnboardingServicing)?
    @ObservationIgnored private var userId: String?
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private(set) var work: Task<Void, Never>?
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "onboarding")

    static let hasSeenKey = "hasSeenOnboarding"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        hasSeen = defaults.bool(forKey: Self.hasSeenKey)
    }

    var shouldPresent: Bool { OnboardingGate.shouldPresent(userId: userId, hasSeen: hasSeen, completed: completed) }
    var isPending: Bool {
        OnboardingGate.isPending(userId: userId, hasSeen: hasSeen, completed: completed, readFailed: readFailed)
    }

    /// Idempotent for one user. A new id forgets the last user's answer.
    func start(userId: String?) {
        guard userId != self.userId else { return }
        work?.cancel()
        self.userId = userId
        completed = nil
        readFailed = false
        guard userId != nil, !hasSeen else { return }
        read(userId)
    }

    private func read(_ uid: String?) {
        guard let service else {
            logger.error("onboarding gate without a service")
            readFailed = true
            return
        }
        work = Task {
            do {
                let done = try await service.onboardingCompleted()
                guard uid == userId else { return }
                completed = done
            } catch {
                logger.error("onboarding flag read failed: \(error.localizedDescription, privacy: .private)")
                guard uid == userId else { return }
                readFailed = true
            }
        }
    }

    /// The flow ended: this device never re-presents, whatever the profile says next.
    func finish() {
        hasSeen = true
        defaults.set(true, forKey: Self.hasSeenKey)
        completed = true
    }
}
```

```swift
// OnboardingService.swift
import Foundation
import Supabase

/// The onboarding's reads and its one write (#1510), a seam so the flow model is tested with a fake.
protocol OnboardingServicing: Sendable {
    /// The most recently updated published letter, or nil when none is published.
    func promiseLetter() async throws -> PromiseLetter?
    /// The system glyphs for the starter grid (D7): eight are shown.
    func systemGlyphs() async throws -> [Glyph]
    /// `profiles.onboarding_completed` for the signed-in user. Throws rather than guessing (#784).
    func onboardingCompleted() async throws -> Bool
    /// `complete_onboarding`: one transaction for everything collected (D5). Throws on refusal.
    func complete(_ answers: OnboardingAnswers) async throws
}

struct OnboardingService: OnboardingServicing {
    let client: SupabaseClient

    func promiseLetter() async throws -> PromiseLetter? {
        let rows: [PromiseLetter] = try await client
            .from("promise_letters")
            .select("body_md_en, body_md_fr, signed_by_en, signed_by_fr")
            .eq("published", value: true)
            .order("updated_at", ascending: false)
            .limit(1)
            .execute()
            .value
        return rows.first
    }

    func systemGlyphs() async throws -> [Glyph] {
        try await client
            .from("glyphs")
            .select("id, name, strokes, view_box, user_id")
            .eq("is_system", value: true)
            .order("created_at", ascending: true)
            .limit(8)
            .execute()
            .value
    }

    func onboardingCompleted() async throws -> Bool {
        struct Row: Decodable {
            let completed: Bool
            enum CodingKeys: String, CodingKey { case completed = "onboarding_completed" }
        }
        let row: Row = try await client
            .from("profiles")
            .select("onboarding_completed")
            .single()
            .execute()
            .value
        return row.completed
    }

    func complete(_ answers: OnboardingAnswers) async throws {
        try await client.rpc("complete_onboarding", params: answers.params).execute()
    }
}
```

The fake conforms to `OnboardingServicing` with stubbed results and a recorded `completeCalls: [OnboardingAnswers]`. `Sendable` on a mutable fake: follow whatever the existing `ConsentServicing` fake under `PebblesTests/Features/Consent/` does.

- [ ] **Step 4: Run the part's gate commands** (lint, whole non-reference suite).

- [ ] **Step 5: Commit** `feat(ios): onboarding service, step config and the completed gate`.

---

## Part 2 — the shell, promise, consent, reasons

Branch `feat/1565-onboarding-shell-ios`. PR `feat(ios): onboarding shell, promise, consent and reasons steps`. Model: opus for 2.1 and 2.3 (consent ledger), sonnet for 2.2.

### Task 2.1: `OnboardingFlowModel`

**Files:**
- Create: `apps/ios/Pebbles/Features/Onboarding/OnboardingFlowModel.swift`
- Test: `apps/ios/PebblesTests/Features/Onboarding/OnboardingFlowModelTests.swift`

Port `apps/android/app/src/main/kotlin/app/pbbls/android/features/onboarding/OnboardingViewModel.kt` and `OnboardingUiState.kt` behaviour for behaviour, and port `apps/android/app/src/test/kotlin/app/pbbls/android/features/onboarding/OnboardingViewModelTest.kt` test for test (read both first). In this part implement stepping, loading, consent and reasons; the people, glyph, carve and completion members arrive in Parts 3 and 4, but declare the state now so the shape is stable.

```swift
/// Step 2's four switches (#1510 D3). `recorded` acts are on and locked; `on` are the ones switched on here.
struct OnboardingConsentState: Equatable {
    /// Display order, health first (the mock's and Android's). `ConsentGateLogic.required` keeps the gate's order.
    static let order: [ConsentKind] = [.healthData, .terms, .privacy, .ageAssurance]
    var recorded: Set<ConsentKind> = []
    var on: Set<ConsentKind> = []
    var isSaving = false
    var failed = false

    var canContinue: Bool { !isSaving && recorded.union(on).isSuperset(of: Self.order) }
    /// Switched on here and not yet in the ledger: what Continue records.
    var toRecord: [ConsentKind] { Self.order.filter { on.contains($0) && !recorded.contains($0) } }
    /// Drives the quiet Log out: shown while any act is not on record.
    var hasMissingActs: Bool { !recorded.isSuperset(of: Self.order) }
}

@Observable
@MainActor
final class OnboardingFlowModel {
    private(set) var step: OnboardingFlowStep = OnboardingFlowSteps.all[0]
    private(set) var letter: PromiseLetter?
    private(set) var letterUnavailable = false
    private(set) var systemGlyphs: [Glyph] = []
    private(set) var consent = OnboardingConsentState()
    private(set) var reasons: [OnboardingReason] = []   // pick order
    var name = ""                                        // Part 3 adds the 40-character limit
    private(set) var glyphId: UUID?
    private(set) var carved: [Glyph] = []
    private(set) var people: [OnboardingPerson] = []
    private(set) var glyphSheetFor: Int?
    private(set) var carvingFor: OnboardingCarveTarget?
    private(set) var pace: OnboardingPace?
    private(set) var isCompleting = false
    private(set) var completeFailed = false
    private(set) var isDone = false

    init(service: any OnboardingServicing, consents: any ConsentServicing,
         cache: ConsentPreferences = ConsentPreferences(), userId: String?,
         haptic: @escaping (TapHaptics.Kind) -> Void = { TapHaptics.play($0) })
    /// Index into `visible`; the progress row shows `all.count` stones.
    var stepIndex: Int { get }
    /// True while a write is in flight: Back and Skip/Later hold (correction 11).
    var chromeDisabled: Bool { get }
    func load() async       // the letter; then the glyphs and the ledger
    func next()
    func skip()
    func back()
    func toggleConsent(_ kind: ConsentKind, _ isOn: Bool)
    func toggleReason(_ reason: OnboardingReason)
    /// Test and reference seeding only.
    func seed(step: OnboardingFlowStep.ID, letter: PromiseLetter?, systemGlyphs: [Glyph], consent: OnboardingConsentState)
}

struct OnboardingPerson: Equatable {
    var name = ""
    /// The picked glyph, or nil until a pick: `defaultGlyphId(index)` is used until then.
    var glyphId: UUID?
    /// True while the name field is open under the piece (S3).
    var isEditing = true
}

enum OnboardingCarveTarget: Equatable { case you, person(Int) }
```

Match the haptics API to what `CaptureFlowModel` actually uses (read it): every interaction fires through the model, as the record flow does. If `TapHaptics`'s real signature differs from the sketch above, follow the real one.

Behaviour to pin with tests (each from the Android test unless marked iOS):
- [ ] `next()` on `promise` moves to `consent`. `back()` is a no-op on the first step.
- [ ] `consent`: `next()` does nothing until all four are on or recorded. With nothing to record, it moves on without a write.
- [ ] `toggleConsent` turns on and off freely and writes nothing to the ledger. It is ignored for a recorded kind and while saving.
- [ ] Continue records `toRecord` in order, through `ConsentServicing.record(kind:documentVersion:source:)` with `ConsentGateLogic.version(for:)` and `ConsentGateLogic.source` (`ios_settings`, per the 2026-10-07 decision; the spec's `ios_register` is stale). `isSaving` is true during the write.
- [ ] A failure sets `failed`, stays on the step, and adds each act that did succeed to `recorded`. A retry sends only the remainder.
- [ ] Success with all four recorded calls `cache.markSatisfied(userId:fingerprint:)` with `ConsentGateLogic.fingerprint()` and moves on.
- [ ] `load()`: the letter resolving nil or throwing sets `letterUnavailable`. The ledger read fills `recorded` as `Set(order) - Set(ConsentGateLogic.missing(active))`, and a read failure leaves it empty (every act is asked; `record_consent` is idempotent).
- [ ] An empty glyph list logs an error and retries on entering `you` or `people`.
- [ ] `skip()` is a no-op on `none` steps. It clears that step's answer (`reasons = []`, `name = ""` and `glyphId = nil`, `people = []`, `pace = nil`) and moves on.
- [ ] `toggleReason` keeps pick order: toggling keepPeople then noticeFeelings gives `[keepPeople, noticeFeelings]`.
- [ ] `answers.reasons` is nil when empty, `displayName` is trimmed and nil when blank.
- [ ] `move` clears `completeFailed`, `glyphSheetFor` and `carvingFor`.
- [ ] iOS: `chromeDisabled` is true while `consent.isSaving` or `isCompleting`.

Writes run in an unstructured `Task` stored on the model (`work`, as `ConsentGateModel` does) so tests can await it, and leaving the view does not cancel a request the server may have accepted. Every error path logs through `os.Logger` (category `onboarding`, `privacy: .private`).

- [ ] Run the suite until green. Commit `feat(ios): onboarding flow model stepping the steps and recording consent`.

### Task 2.2: Shell, scaffold, progress stones, promise, reasons

**Files:**
- Create: `apps/ios/Pebbles/Features/Onboarding/OnboardingFlowView.swift`
- Create: `apps/ios/Pebbles/Features/Onboarding/Steps/{OnboardingStepScaffold,ProgressStones,PromiseStep,ReasonsStep}.swift`
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

**Shell.** Mock top bar: Back, stones, Skip or Later.

```swift
struct OnboardingFlowView: View {
    let model: OnboardingFlowModel
    let onLogOut: () -> Void
    let onFinish: () -> Void
    // NavigationStack { Group { switch model.step.id … } .background(Color.pebbles.surface.ignoresSafeArea()) .toolbar { … } }
}
```
- A real `NavigationStack` with `.toolbar`, `.pebblesScreen()` and `.navigationBarTitleDisplayMode(.inline)`, and the bar's background hidden.
- Leading `PebbleToolbarButton` with `Image(systemName: "chevron.left")` and label "Back", absent on the first step, `.disabled(model.chromeDisabled)`.
- `.principal` `ProgressStones`. Trailing `PebbleToolbarButton("Skip")` or `("Later")` per `step.skip`, disabled the same way.
- Check on a held-window screenshot that the stones get no Liquid Glass capsule. If they do, tell the controller: `sharedBackgroundVisibility` is iOS 26 and the deployment target forbids `if #available`.
- Step changes animate with `.snappy(duration: 0.28)` and the record flow's asymmetric slide (read `CaptureFlowView.stepBody`), reversed on Back. `.task { await model.load() }`. `model.isDone` calls `onFinish` from `.onChange`.
- `case .handoff` draws `EmptyView()`: it never shows.

**`OnboardingStepScaffold`.** `RecordStepScaffold` centres its title and uses another font rank, so this is the mock's left-aligned sibling.

```swift
struct OnboardingStepScaffold<Content: View, Footer: View>: View {
    let title: LocalizedStringResource
    var lead: LocalizedStringResource?
    var scrolls = true                 // reference renders pass false
    @ViewBuilder let content: () -> Content
    @ViewBuilder let footer: () -> Footer
}
```
Title: `.spineType(.sectionTitle)`, `Color.pebbles.onSurface`, `.accessibilityAddTraits(.isHeader)`. Lead: `.spineType(.greetingLine)` in `onSurfaceVariant`. Gutters `Spacing.lg`, leading alignment. Copy the scroll treatment from `RecordStepScaffold` (`contentMargins`, `scrollBounceBehavior(.basedOnSize)`, the fade mask) rather than changing that file. The footer is pinned below the scroll region with the same `Spacing.lg` gutters and `Spacing.sm` bottom padding, so it rides above the keyboard.

Primary buttons everywhere: `Button { } label: { Text(…) }.buttonStyle(PebblesPrimaryButtonStyle(isLoading:))` plus `.disabled`, as `ConsentGateView` does.

**`ProgressStones(current:count:)`.** `count` is `OnboardingFlowSteps.all.count` (seven, the seventh never lit). Passed: `PbblsIconImage(.pebbleRoundLit1, size: 16)` in `onSurfaceVariant`. Current: `.pebbleRoundLit1` size 24 in `primary`. Ahead: `.pebbleRound` size 16 in `outline`. Spacing 6 (`4-you.png`). One accessibility element: "Step N of 7" (the existing key `RecordFlowChrome` uses).

**`PromiseStep(letter:unavailable:onContinue:scrolls:)`** against `1-promise.png`:
- A 44 pt rail runs from the top to just above the button: vertical uppercase "A PROMISE" (`rotationEffect(.degrees(-90))` with `fixedSize`, tracking 0.3 em, `onSurfaceVariant`) against a 1 pt `outlineVariant` hairline, `.accessibilityHidden(true)`.
- Right of the rail, scrolling alone: title `Text("Before anything else,") + " " + italic primary Text("a promise.")` in `.spineType(.sectionTitle)` as header (use a size-appropriate italic from the rank; do not `.fontWeight`).
- A 64 pt rule; the first paragraph in `.spineType(.greetingLine)`; the rest in the body rank; a full-width rule; `PbblsIconImage(.pebbleRoundLit1, .tile)` in `primary` beside `signedBy` in `.spineType(.communityTitle)`.
- French comes from `Locale.current.prefersFrench`.
- `unavailable` shows the error line and hides the signature, and the button still passes the step. No Back on this step (`shell`). The button is `I'll hold you to it`.

**`ReasonsStep(selected:onToggle:onContinue:scrolls:)`** against `3-reasons.png`:
- Scaffold with title and lead, then two columns of six equal tiles; `.fixedSize(horizontal: false, vertical: true)` per row so a pair shares its height.
- Tile: min height 112, radius 24, padding `Spacing.lg`. Icon top-left (`.tile`). Corner stone top-right (`.pebbleRoundLit1` `.row` in `primary` when on, `.pebbleRound` in `outline` when off). Label below in the body-medium rank.
- Off: `surfaceContainerLowest` fill with a 1 pt `outlineVariant` border, ink `onSurface`. On: `primaryContainer`, no border, ink `onPrimaryContainer`.
- A `Button`, with the `.isSelected` trait when on (multi-select), and the label as the element's label.
- Continue is disabled until one is picked; Skip is the top bar's.

- [ ] Add the copy entries for these screens. Write a temporary render test (or the Part 4 suite early) and compare with `1-promise.png` and `3-reasons.png`, Paper and Night, then the held-window screenshot of the shell with its toolbar. Attach the image paths to your report. Delete temporary tests. Commit `feat(ios): onboarding shell with progress stones, promise and reasons`.

### Task 2.3: Consent step and legal links

**Files:**
- Create: `apps/ios/Pebbles/Features/Onboarding/Steps/ConsentStep.swift`
- Modify: `apps/ios/Pebbles/Features/Auth/LegalDocumentSheet.swift`, the shell
- Test: add to `apps/ios/PebblesTests/SettingsLinkTests.swift`'s neighbour, a new `apps/ios/PebblesTests/Features/Onboarding/LegalDocURLTests.swift`

**`LegalDoc`.** `url` becomes the page in the app's language, for every caller (the site otherwise follows its own stored choice; `?lang=` overrides it, `LangParamSync`):

```swift
var url: URL { url(french: Locale.current.prefersFrench) }

func url(french: Bool) -> URL {
    let path: String
    switch self {
    case .terms: path = "terms"
    case .privacy: path = "privacy"
    }
    return URL(string: "https://www.pbbls.app/docs/\(path)?lang=\(french ? "fr" : "en")")!
}
```
Test: `.terms.url(french: true)` ends with `/docs/terms?lang=fr`, `.privacy.url(french: false)` with `/docs/privacy?lang=en`.

**`ConsentStep(state:onToggle:onContinue:onLogOut:scrolls:)`** against `2-consent.png`, with the rows in the app's settings language:
- Title `Your consent`, body line `One switch for each thing you agree to.` in the body rank, `onSurfaceVariant`.
- One bordered group, the same look as `.pebblesListRow` (radius `Spacing.lg`, 1 pt `Color.pebbles.rule` stroke, `rule` hairlines between rows, read `Theme/PebblesList.swift`). `ForEach(OnboardingConsentState.order)` so it is stable for the renders. It is a `VStack`, not a `List`, because a `List` nested in a scroll view misbehaves and `ImageRenderer` cannot draw one.
- Each row is a native `Toggle(isOn:) { VStack(alignment: .leading) { title; detail or "Already on record" } }.tint(Color.pebbles.primary)`, so VoiceOver reads the act and its state as one control.
- A recorded act: `isOn` true, `.disabled(true)`. Under `ImageRenderer` a `Toggle` may not draw: if so, add `drawsStaticSwitches: Bool = false` as `RecordMomentStep` does for fields, drawing a capsule and a thumb from `primary`/`outline` for renders only.
- Two outlined buttons side by side, `Read the terms` and `Read the policy`, opening `LegalDocumentSheet(url:)` in a sheet, as `ConsentGateView` does (`.ignoresSafeArea()`).
- Footer, top to bottom: the failed line in `Color.pebbles.error` when `state.failed`; `All four are needed to continue.` in `primary`, caption size, when `!canContinue && !isSaving`; `Continue` with `PebblesPrimaryButtonStyle(isLoading: state.isSaving)`, `.disabled(!canContinue)`.
- When `state.hasMissingActs`: a quiet `Log out` under Continue, styled as `ConsentGateView.signOutButton` (body medium, `primary`, 44 pt row).
- No Skip on this step.

- [ ] Compare with `2-consent.png` and `OnboardingConsent_611a7511_0.png`, in three states: nothing on record, some recorded, all on. Check the French fits. Commit `feat(ios): onboarding consent step, recorded on continue, legal links in the app's language`.

---

## Part 3 — you and first people on the split stone

Branch `feat/1565-onboarding-stones-ios`. PR `feat(ios): onboarding you and first people on the split stone`. Model: sonnet.

### Task 3.1: The stone grid and the glyph stone

**Files:**
- Create: `apps/ios/Pebbles/Features/Shared/Stones/StoneGrid.swift`
- Create: `apps/ios/Pebbles/Features/Onboarding/Steps/GlyphStoneGrid.swift`
- Test: `apps/ios/PebblesTests/Stones/StoneGridTests.swift`

```swift
/// A rock cut rows × columns (#1510 D7): one `SplitStone` per row, seeded `<seed>#<row>` exactly as
/// Android's `StoneGrid.rowSeeds`, stacked `options.gutter` apart, so the pieces read as one stone cut
/// both ways. `index` is row-major.
enum StoneGridLayout {
    static func rowSeeds(seed: String, rows: Int) -> [String] { (0..<rows).map { "\(seed)#\($0)" } }
    static func cellIndex(row: Int, column: Int, columns: Int) -> Int { row * columns + column }
}

struct StoneGrid<Cell: View>: View {
    let rows: Int
    let columns: Int
    let seed: String
    let fills: [Color]     // one per cell
    var options = StoneOptions()
    var minCellHeight: CGFloat = 88
    @ViewBuilder let cell: (_ index: Int, _ column: Int) -> Cell
}
```
Tests: `rowSeeds("onboarding-glyphs", 3)` is `["onboarding-glyphs#0", "…#1", "…#2"]`; `cellIndex` row-major.

**`GlyphStoneGrid(glyphs:selectedId:onSelect:onDraw:folded:drawLabel:shortDrawLabel:)`**, port of `steps/GlyphStoneGrid.kt`:
- Seeds, the same on every surface (D7): `"onboarding-glyphs"` for 3×3, `"onboarding-glyphs-folded"` for 1×4.
- The chosen glyph first, so it is never cut off the stone once carved ones push past eight. Then the rest, padded with nil to 8 (or 3 folded) so the stone keeps its cut while glyphs load, and the Draw piece last.
- Fills: chosen `primaryContainer`, the rest `surfaceContainer`. No outlines.
- Glyph: `GlyphThumbnail(strokes:side:strokeColor:)` (ink `onPrimaryContainer` when chosen, `onSurface` otherwise, as `4-you.png`), side 56, or 44 folded. Min cell height 96, or 72 folded.
- Draw piece: a dashed `outline` inset (1.5 pt, dash 6, radius 20, inset `Spacing.sm`) with `plusCircle` and the label in the caption rank; the short label when folded.
- Each cell is a `Button` with `.contentShape(Rectangle())`, and the `.isSelected` trait when chosen. Label: `Glyph N` for a system glyph (`userId == nil`), the name for a carved one, the draw label for Draw. Never a system glyph's name (correction 5).

### Task 3.2: You, people, the sheet, the carve; the model's members

**Files:**
- Create: `apps/ios/Pebbles/Features/Onboarding/Steps/{YouStep,PeopleStep,SoulGlyphSheet}.swift`
- Modify: `OnboardingFlowModel.swift`, `OnboardingFlowView.swift`, the catalog
- Test: `OnboardingFlowModelTests.swift`

**Model additions, ported from `OnboardingViewModel.kt` steps 4–5, with tests:**
- [ ] `setName` caps at 40 (`nameLimit`, Settings' limit; a person's name shares it). `pickGlyph(_:)`, `drawForYou()`.
- [ ] `addPerson()`: no-op at three people, or while one is editing.
- [ ] `setPersonName(_:_:)` caps at 40.
- [ ] `commitPerson(_:)`: blank removes the place, otherwise trims and closes the field.
- [ ] `editPerson(_:)`: reopens, no-op while another is editing.
- [ ] Leaving the step (`next` and `skip`) commits an open field: a blank place is dropped, a name is kept. `skip` on people then clears all.
- [ ] `defaultGlyphId(_ index:)`: `systemGlyphs[index]`, else `systemGlyphs.first`.
- [ ] `openGlyphSheet(_:)`, `pickPersonGlyph(_:_:)` (closes the sheet), `drawForPerson(_:)` (closes the sheet, opens the carve).
- [ ] `carved(_:)` appends to `carved`, closes the carve, and assigns to `you` or `person(i)`. `carveCancelled()`.
- [ ] `answers.souls`: not-editing, non-blank people, trimmed, `glyphId ?? defaultGlyphId(index)`. A person with neither is what `complete()` refuses in Part 4 (correction 4).
- [ ] `canContinueYou`: name not blank or a glyph picked. `canContinuePeople`: any non-blank name.
- [ ] `peopleCTA`: three named → `Keep %@, %@ and %@`, otherwise `These are my people`.

**`YouStep`** against `4-you.png` and `4-you-keyboard.png`:
- Title, then a bare `TextField` in `.spineType(.greeting)` on a rule (1 pt `outline`, or 2 pt `primary` when focused), then `Your name` in caption (`primary` when focused).
- `Your glyph` uppercase in `.spineType(.sectionLabel)`, then `GlyphStoneGrid(folded: nameIsFocused)` animated with `.snappy`.
- `.textInputAutocapitalization(.words)`, `.submitLabel(.done)`, `@FocusState`. Footer `That's me`, `.disabled(!canContinueYou)`, riding above the keyboard.
- `scrolls: false` draws the name as static text.
- Grid glyphs: `systemGlyphs + carved`. Tapping Draw opens the carve.

**`PeopleStep`** against `5-people.png` and `5-people-typed.png`:
- Title, lead, the note `First names only. They are never contacted.` in the body rank, hidden while a field is open (`5-people-typed.png`).
- `SplitStone(count: 3, seed: "camera|fixed|rock|q11", fills:…, minHeight: 104)`: that seed is the Home quick actions row (`ShortcutStones.splitRow`). A named person's piece is `primaryContainer` with `GlyphThumbnail` side 56 in `onPrimaryContainer`.
- The empty place's fill is `Color.clear`, with a dashed `outline` stroke (1.5 pt, dash 6) **along the piece's own outline** (`StoneCache.split` with the same seed and size, in a `.background { GeometryReader … }`, as `5-people.png` draws it), plus `plusCircle` `.tile` in `onSurfaceVariant`. The whole place is a button labelled `Add one`, enabled when `canAdd`.
- Under the pieces, three equal columns:
  - empty → `Add one` in `primary`;
  - editing → a centred `TextField` with the placeholder `Their name`, `.words`, `.done`, focused as it appears, on a 2 pt `primary` rule;
  - named → the name (`.spineType(.communityTitle)`, one line; tap reopens) and `Change glyph` in caption `primary`.
- **Commit on return, and on focus loss only after focus was gained** (`wasFocused`), so the field's initial unfocused state never drops it before a keystroke.
- `You can add more people later.` in caption `onSurfaceVariant`, centred. Footer `peopleCTA`, `.disabled(!canContinuePeople)`. Later is the top bar's.

**`SoulGlyphSheet`** against `s2-picker.png`: a native `.sheet` with `.presentationDetents([.large])` and `.presentationDragIndicator(.visible)`. Header: the person's piece (48 pt `primaryContainer` stone, glyph in `onPrimaryContainer`) beside `A glyph for %@` and `It marks %@ on every pebble.`. Then `GlyphStoneGrid` with `Draw one`, holding the pick locally until `Use for %@` commits it. Android's `SoulGlyphSheet.kt` is the reference for behaviour.

**S1, the carve:** the existing `GlyphCarveSheet(onSaved:)` as a `.fullScreenCover` from the flow view, bound to `model.carvingFor`. Cancel calls `carveCancelled()`. It reads `AchievementsService` and `SupabaseService` from the environment, which the cover inherits. Do not rebuild it (D8). A glyph sheet closes before the carve opens: drive the cover from the sheet's `onDismiss`, because presenting a cover while a sheet is dismissing is dropped.

- [ ] Compare the two `you` states, the two `people` states and the sheet with their mock frames. Held-window screenshots are needed for the fields and keyboards (a hardware-keyboard simulator shows no soft keyboard: `xcrun simctl` cannot toggle it, so use `I/O → Keyboard → Toggle Software Keyboard` or test the folded state by focusing). Commit in two: `feat(ios): the stone grid and the starter glyphs as one split stone`, `feat(ios): onboarding you and three places, with the glyph sheet and the carve`.

---

## Part 4 — pace, completion, reference renders

Branch `feat/1565-onboarding-pace-ios`. PR `feat(ios): onboarding pace and one reference render per step`. Model: sonnet.

**Files:**
- Create: `apps/ios/Pebbles/Features/Onboarding/Steps/PaceStep.swift`
- Create: `apps/ios/PebblesTests/References/OnboardingReferences.swift`
- Modify: `OnboardingFlowModel.swift`, `OnboardingFlowView.swift`, tests, the catalog

**Model:**
- [ ] `selectPace(_:)`. `next()` on `pace`, and `skip()` on `pace` (after clearing), call `complete()`.
- [ ] `complete()` is a no-op while `isCompleting` or `isDone`. If souls need a glyph and `systemGlyphs` is empty, reload once. If a soul still has no glyph, set `completeFailed` and never call the service (correction 4; test it). On success store `doneNote = OnboardingHandoffNote(userId:, reason: answers.reasons?.first)` and set `isDone`; on failure log and set `completeFailed`, keeping every answer so a retry resends. Define `OnboardingHandoffNote` here (`struct OnboardingHandoffNote: Equatable, Sendable { let userId: String?; let reason: OnboardingReason? }`); Part 5 consumes it.

**`PaceStep(selected:isCompleting:failed:onSelect:onFinish:scrolls:)`** against `6-pace.png`:
- Title, no lead.
- One vertical group, 4 pt apart, at least 88 pt per row: `surfaceContainer` fill, `UnevenRoundedRectangle` with 32 outer and 10 inner radii per position.
- The chosen row: a `Capsule` in `primary`, ink `onPrimary`, inset −6 pt each side as the mock draws it, animated with `.snappy`.
- A 52 pt disc (`surfaceContainerHighest`, or `onPrimary` at 18 % when chosen) holding the icon at `.tile` size; the name in `.spineType(.wrapMonth)`, the detail in caption.
- Each row is a `Button` with the `.isSelected` trait and combined accessibility (a radio set by role: exactly one chosen).
- Footer: the failed line in `error`; `No streaks, no guilt.` centred in `onSurfaceVariant`; `Continue` with `PebblesPrimaryButtonStyle(isLoading: isCompleting)`, disabled while completing. Skip is the top bar's and also sends. There is no minimum: Continue with no pace sends, as on Android.

**`OnboardingReferences`:** one `@Test` per screen in the shape of `RecordFlowReferences` (`ReferenceConfig(scheme:locales:)`, `ReferenceImage.assertMatches`), Paper EN and FR, Night EN, and accessibility2 for consent, reasons and pace. Fixture glyphs are built in the test (no network), with fixed ids, and rendered in a stable order. Screens: promise; consent with nothing on record; consent with two recorded; reasons with two on; you; you folded; people with two; people with three and the third typed; pace; the glyph sheet.

- [ ] Run the suite, then open every render under `apps/ios/build/reference-diffs/` next to its mock frame, in Paper, Night and French. Report what still differs. Do not commit PNGs: the top PR gets `rebaseline-ios-screenshots` from the controller. Commit `feat(ios): onboarding pace step and completion`, `test(ios): one reference render per onboarding step`.

---

## Part 5 — the gate, the hand-off, the old slides gone

Branch `feat/1565-onboarding-handoff-ios`. PR `feat(ios): the onboarding gate, hand-off to the quick moment, old slides gone`, body `Resolves #1565`, `Closes #1466`. Model: opus (the consent gate's standing rule).

**Files:**
- Create: `apps/ios/Pebbles/Features/Onboarding/OnboardingHandoff.swift`
- Modify: `apps/ios/Pebbles/RootView.swift`, `Features/Consent/ConsentGateModel.swift`, `Features/Home/HomeView.swift`, `Features/Home/HomeModel.swift`, `Features/Capture/CaptureFlowView.swift`, `Features/Profile/Sheets/SettingsSheet.swift`, `Services/OnboardingService.swift`, `docs/decisions/log.md`, the catalog
- Delete: `Features/Onboarding/{OnboardingImage,OnboardingPageView,OnboardingStep,OnboardingSteps,OnboardingView}.swift`, `PebblesTests/Features/Onboarding/OnboardingStepsTests.swift`, the two `OnboardingStep` tests in `PebblesTests/LocalizationTests.swift`, the slides' image assets when nothing else uses them, and their catalog entries (`Your life is a path.` and the rest, `Start your path`, `Close onboarding`) by targeted text edits
- Test: `OnboardingHandoffTests.swift`, `ConsentGateModelTests.swift`

**`OnboardingHandoff`:**
```swift
/// A one-shot mailbox between the flow and Home (#1510 D9). Process memory only: dying in between
/// loses the hand-off, which is fine. Bound to its user (correction 10).
@Observable
@MainActor
final class OnboardingHandoff {
    private(set) var revision = 0
    @ObservationIgnored private var note: OnboardingHandoffNote?
    func leave(_ value: OnboardingHandoffNote) { note = value; revision += 1 }
    /// The note left for `userId`, or nil; either way the mailbox is empty afterwards.
    func take(userId: String?) -> OnboardingHandoffNote? {
        let taken = note
        note = nil
        guard let userId, taken?.userId == userId else { return nil }
        return taken
    }
}

extension OnboardingHandoffNote {
    /// The composer's first prompt, chosen by the first reason picked.
    var prompt: LocalizedStringResource {
        switch reason {
        case .noticeFeelings: return "What did you feel today?"
        case .keepPeople: return "Who were you with today?"
        case .hardSeason: return "What was hard today?"
        case .goodDays: return "What was good today?"
        case .keepRecord: return "What do you want to remember from today?"
        case .ownPatterns, .none: return "What happened today?"
        }
    }
}
```
Tests: same user takes once; another user gets nil and the note is gone; nil user gets nil; each prompt per reason.

**`ConsentGateModel.recheck()`** (Android's `restart`): if the cache is satisfied for the current user, go straight to `.satisfied`; otherwise `check`. Tests for both. Unlike `start`, it runs with a verdict already reached.

**`RootView`:**
- Replace `@AppStorage("hasSeenOnboarding")` with `@State private var onboardingGate = OnboardingGateModel()` (same defaults key) and `@State private var onboardingHandoff = OnboardingHandoff()`. Keep `isPresentingOnboarding`. Add `@State private var pendingNote: OnboardingHandoffNote?`.
- Keyed on the user, beside the consent gate's task: `onboardingGate.service = OnboardingService(client: supabase.client); onboardingGate.start(userId:)`.
- The cover:
```swift
.fullScreenCover(isPresented: $isPresentingOnboarding, onDismiss: handOffAfterOnboarding) {
    OnboardingFlowHost(onLogOut: { Task { await supabase.signOut() } }) { note in
        pendingNote = note
        consentGate.recheck()
        onboardingGate.finish()
        isPresentingOnboarding = false
    }
}
```
  `OnboardingFlowHost` owns `@State var model` built from the environment's `supabase` (`ConsentService`, `OnboardingService`, lowercased `userId`), so a new sign-in gets a fresh flow (D10: quitting restarts).
- Present on `.onChange(of: canShowAuthedTabs && onboardingGate.shouldPresent, initial: true)`.
- **Consent layer**, above everything as now, fail-closed:
```swift
if !consentGate.isPassed {
    if onboardingGate.isPending || onboardingGate.shouldPresent || isPresentingOnboarding {
        Color.pebbles.surface.ignoresSafeArea()   // nothing un-gated shows for a frame
    } else {
        ConsentGateView(model: consentGate, onSignOut: …)
    }
}
```
  The promise comes before the consent (D2). The gate is unchanged for everyone else: a pre-flow account with a missing act still meets `ConsentGateView`.
- The loader overlay holds on `holdsLoader || onboardingGate.isPending`.
- **Invites wait for the flow's end:** the `consentGate.isPassed` handler flushes a parked invite only when `!isPresentingOnboarding && !onboardingGate.shouldPresent && !onboardingGate.isPending`; `.onOpenURL` parks under the same condition; `handOffAfterOnboarding` flushes.
- `handOffAfterOnboarding` (runs after the cover is gone, so a second cover is never dropped): `nav.go(.home)`; `Task { await refs.load() }`, so the people named appear in Complete; if a parked invite exists, open it and drop the note (one presentation; D9 says missing the composer is fine); otherwise `onboardingHandoff.leave(pendingNote)`. Clear `pendingNote`.
- Sign-out while presenting: `isResolvedSignedOut` resets `isPresentingOnboarding` and `pendingNote` in `resetAuthedSceneState()`. `hasSeenOnboarding` stays as it is: a flow abandoned by logging out restarts next time.
- Inject `.environment(onboardingHandoff)` on `PebblesTabView`, and `.environment(onboardingGate)` under `#if DEBUG` for the replay row. Update the `#Preview` and any other host that builds `HomeView`.

**`HomeView`:** `@Environment(OnboardingHandoff.self)`, `@State private var quickPrompt: LocalizedStringResource?`, and a `.task(id: handoff.revision)` that takes the note for the current user; when one arrives, reload the name (a greeting loaded under the cover is stale: expose a narrow `reloadName()` on `HomeModel`), set `quickPrompt = note.prompt` and `showsQuick = true`. `.task(id:)` also runs on first appearance, so a Home built late still sees it. The quick cover passes `prompt: quickPrompt` and clears it on dismiss.

**`CaptureFlowView`:** a new `prompt: LocalizedStringResource? = nil` init parameter. On the moment step the scaffold's title is `prompt ?? model.step.title`. Nothing else changes. Attaches nobody (D9). Test on `CaptureFlowModel` is unnecessary; a reference for the prompted moment page goes in `RecordFlowReferences`.

**Debug replay (`#if DEBUG`):** `OnboardingServicing` is not widened. `OnboardingGateModel.replay(client:)` resets `onboarding_completed` to false on the caller's `profiles` row (still owner-writable, the follow-up on the Android PR), clears the device flag and `completed`, then re-reads. `SettingsSheet.developerSection` gains `Replay onboarding` as a verbatim row (`.pebbleRound`; Icon lab becomes `.middle`, the new row `.bottom`), which dismisses Settings first and replays from a task that outlives it.

**Decision log:** one appended entry, `## 2026-11-06 — iOS onboarding gate and the Log out at step 2 (#1565)`: the gate reads the profile with the old device flag kept; the consent gate stands down while the flow is presented or pending, opaque underneath; consent recorded on Continue; a parked invite wins over the composer; Step 2 keeps a quiet Log out, departing from the mock and from Android, with the mirror owed; D9 stays open. Follow the log's existing entry format.

- [ ] Gate commands, and `xcodebuild build` for a Release-style compile if `#if DEBUG` guards moved anything. Commit in logical pieces: `feat(ios): the onboarding gate, consent standing down while the flow runs`, `feat(ios): hand off to the quick moment with the prompt the first reason chose`, `chore(ios): remove the old onboarding slides`, `chore(ios): debug row that replays the onboarding`, `docs(core): record the ios onboarding gate decisions`.

---

## Shipping (controller)

1. `gh stack submit --auto` after each part. Edit titles and bodies per part; labels `feat`, `ui`, `core`, `ios`, `no-lab-note` (Arkaik covers it); milestone `M68 · V1 launch`. Bodies carry no Kritik finding id.
2. Label the top PR `rebaseline-ios-screenshots`; approve held runs.
3. Device pass against the mock (below). The merge (`gh stack merge`) is the maintainer's.
4. Arkaik: the onboarding flow and step views gain iOS, at `development` when picked up. Never `live`.
5. File: the Android mirror of the step 2 Log out. Mention: #1009, #1533 and the once-per-device flag (a new account on a device that already finished the flow is not onboarded; the same on Android; fixing it needs a backfill of pre-flow accounts).
6. Drop the build journal pebble. Append "Lessons learned" here.

## Device pass, against the mock, every screen side by side

Fresh throwaway account with an empty ledger on the simulator, then the maintainer's iPhone 15:
- [ ] Promise; quit at step 3 and relaunch: back at the promise, acts locked "Already on record".
- [ ] Consent with unset switches: off and on freely, nothing in `user_consents` until Continue; Log out works, and the row disappears when all four are on record.
- [ ] Legal links open in French under a French device.
- [ ] Reasons pick order drives the prompt; Skip gives the default prompt.
- [ ] You: keyboard folds the stone; Draw round-trip; carved glyph becomes the pick.
- [ ] People: one, two, three (CTA names them), blank commit removes, Change glyph sheet, Draw one, Later.
- [ ] Pace: selected capsule; failure line offline; retry resends once.
- [ ] Server read-back: name, glyph, reasons, pace, souls.
- [ ] Home greets by the new name, composer open with the prompt, the people appear in Complete.
- [ ] A pre-flow account: no flow; a missing act still meets the gate. Parked invite waits for the end.
- [ ] Dark, French, largest accessibility size, iPhone SE width.

---

## Lessons learned

**What the device pass found that nothing else did.** Five parts, five reviews and 1,600 green tests never caught that an achievement card could not be dismissed, because no test taps through a real window and no reference render has a window at all. It took one real first carve. A pass on a real account against the mock is not a formality: book it before the stack is called done, and read the server back afterwards (`status`), because reasons order, pace and the ledger are invisible on screen.

**Corrections to this plan's text, in the order they surfaced:**
- `OnboardingFlowStep.ID` became `StepID`, for lint.
- The model grew `sessionEnded()` and a loop that checks before every act, because an immutable `userId` does not stop a write loop: the next `record` goes out under whoever is signed in by then.
- Consent's body line is upright body text, not the scaffold's italic lead. The mock, Android and this plan agreed; the first build still used the lead.
- Cell heights follow the mock (112 and 84 for the glyph grid, 116 for people), not Android's 96, 72 and 104, which this plan had copied.
- `Your name`'s French is shared with Settings ("Votre nom"), so the flow uses a dedicated `onboarding.you.name` ("Ton prénom"). Check each key before reusing it.
- A case-insensitive duplicate person is refused at commit. The plan did not cover it, and `duplicate_soul` would have failed the whole send at step 6 with a message blaming the connection.
- `OnboardingPresentation` and `ConsentLayerView` are new: `RootView` went over the lint limits, and the fail-closed layer is safer as a pure function tested on every combination.

**SwiftUI facts that cost time:**
- A modifier outside `pebblesFont` or `spineType` loses to the rank's own value: `.tracking` and `.lineSpacing` did nothing until they were set on the rank or inside it.
- SwiftUI clamps negative `lineSpacing` to zero, so a rank's line height below the font's natural height never shows on iOS 17 APIs.
- `ImageRenderer` draws a yellow no-entry placeholder for `Toggle`, which is why `drawsStaticSwitches` exists. Neither it nor a reference render proves the real control.
- A `.frame(minHeight:)` outside a `Button` does not enlarge its target. The label owns the frame and the `contentShape`.
- Removing a cover's presenting branch fires neither its `onDismiss` nor an `.onChange` inside it, so a host cannot be relied on to notice a sign-out.
- SwiftUI content hits a `UIHostingController`'s root view, so a passthrough window cannot tell content from empty space by the hit view.

**Reviewing.** The review that found the most was the last and most adversarial: it read the pinned SDK and turned "perhaps 60 s" into "about 4 minutes" of blank screen, because every GET is retried. Give the final reviewer sequences to trace, not a diff to read. A reviewer that opens the mock frames finds different things from one that reads the code.

**Driving the simulator.** Instant synthetic clicks are dropped on native controls such as `Toggle`: press, wait about 0.12 s, release. Typing through a French Mac layout loses characters: put text on the simulator's pasteboard with `simctl pbcopy` and paste. Testing on a production backend means a throwaway account the maintainer creates and signs in to; the agent never types a password. `onboarding-verify-*@example.test` is already in the orphan sweep.

**Pushing.** Holding the first push until after the device pass meant fixes landed in their owning layer with no force-push.

**Owed:** #1566 (Android), #1567 (the gate's own read has no ceiling), #1568 (a parked invite survives sign-out), the once-per-device flag and its backfill, dark, French and small screens on a live build, a real iOS 17 run, and a dropped cover on a real Google sign-in, which has only unit tests.
