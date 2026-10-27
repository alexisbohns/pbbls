# iOS People and Collections empty states — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the two `ContentUnavailableView`s of the People and Collections tabs with the onboarding designs E3 (People, "Start from what you have") and E2 (Collections, "The explainer card") of spec `docs/superpowers/specs/2026-10-26-home-tab-and-empty-states-design.md` §2–3, D10, D11 (issue #1256, M64).

**Architecture:** People's states are a pure projection of the `people_prompts` RPC payload plus one device flag; the view draws the Spine's contents rows for the prompted pebbles and opens the existing `EditPebbleSheet` on them. Collections' note is a pure function of the collections count plus one device flag. Both are built from the app's real parts (`StoneSeal`, `SoulItem`, `CollectionRow`, `CollectionModeBadge`), wear the Spine's type (`spineType`, `Color.pebbles.*`), and are pinned by references rendered on CI.

**Tech Stack:** SwiftUI iOS 17, `@Observable`, Swift Testing, `ImageRenderer` references (CI-recorded, `rebaseline-ios-screenshots`), Supabase Swift client, `WrapDecoding` for tolerant JSON.

**Stack (gh stack, two parts = two PRs):** `feat/1256-ios-people-empty` (Tasks 1–4, PR "feat(ios): start people from the pebbles you have") → `feat/1256-ios-collections-note` (Tasks 5–6, PR "feat(ios): explain collections on the empty tab", `Resolves #1256`). Labels `feat`, `ui`, `ios`, `no-lab-note`; milestone M64 · New Path. Each part lints, builds and tests alone.

**Worktree and tests:** `.worktrees/ios-empty`, simulator "iPhone 17 home", `apps/ios/build/empty-test.sh [--fresh] [Suite…]` (nested reference suites are passed as `References/<Suite>`). New references fail locally with "no reference … rendered on CI only"; never record PNGs locally. The 27 known local failures (plan `2026-10-27-ios-home-tab.md`, "As built") are unchanged.

## Deviations from the spec, decided while planning

- **D-plan-1. "Who?" opens `EditPebbleSheet` scrolled to its Souls section, not with a field focused.** `PebbleFormView` is a `Form`; a soul picker has no text field to focus. `EditPebbleSheet` gains `var scrollsToSouls = false`; the form scrolls to its Souls section once on appear. Same effect for the user, no new form.
- **D-plan-2. The prompt rows reuse the contents row's recipe, not `WeekContents`.** `WeekContents` draws a `SpinePage`; a prompt is one pebble with no page. `PromptRow` copies the row recipe (day column, `StoneSeal`, name in `contentsName`, time) from `WeekContents.rowView` and adds the "who?" affordance in the time column. Android will copy the same row.
- **D-plan-3. The People onboarding reads `people_prompts` only when the souls grid is short.** `SoulsListView` already knows `items.count`; the RPC is called when `items.count < 3` and the flag is off, so a veteran pays nothing. The RPC's own `souls` count is the truth for the state; the grid count only gates the call.

---

## Part 1 — People (`feat/1256-ios-people-empty`)

### Task 1: `PeoplePrompts` payload, service and state

**Files:**
- Create: `apps/ios/Pebbles/Features/People/PeoplePrompts.swift`, `apps/ios/Pebbles/Features/People/PeopleService.swift`, `apps/ios/Pebbles/Features/People/PeopleOnboardingState.swift`
- Test: `apps/ios/PebblesTests/Features/People/PeoplePromptsDecodingTests.swift`, `apps/ios/PebblesTests/Features/People/PeopleOnboardingStateTests.swift`, `apps/ios/PebblesTests/Features/People/PeoplePayloads.swift`

- [ ] **Step 1: The verbatim payload reader.** `PeoplePayloads` reads `packages/supabase/scripts/oracles/home/payloads/people-prompts.json` through `#filePath` exactly as `PebblesTests/Features/Home/HomePayloads.swift` does (copy its `folder` computation).

- [ ] **Step 2: Failing decoding tests.**

```swift
@Suite("People prompts decoding") struct PeoplePromptsDecodingTests {
    @Test func decodesTheHarnessPayloadVerbatim() throws {
        let prompts = try WrapDecoding.decode(PeoplePromptsPayload.self, from: PeoplePayloads.data("people-prompts")).model
        #expect(prompts.pebbles == 7 && prompts.souls == 1)
        #expect(prompts.prompts.count == 3)
        #expect(prompts.prompts[0].name == "outward bright b7f92491")
        #expect(prompts.prompts[1].positiveness == 0)
        #expect(prompts.prompts[0].happenedAt == ISO8601Flexible.parse("2026-10-06T09:00:00Z"))
        #expect(prompts.skipped == ["outward low b7f92491", "inward bright b7f92491"])
    }
    @Test func nullsAndFractionalSecondsAreTolerated() throws {
        let json = #"{"souls":0,"pebbles":1,"prompts":[{"name":null,"intensity":2,"moment_id":"a6b3a0f7-b5a8-48d6-a266-2fb32ec54055","pebble_id":"85a50229-9d81-474e-b6e4-61dbb6a58d45","emotion_id":null,"happened_at":"2026-10-06T09:00:00.123456+00:00","positiveness":1}],"skipped":[]}"#
        let prompts = try WrapDecoding.decode(PeoplePromptsPayload.self, from: Data(json.utf8)).model
        #expect(prompts.prompts[0].name == nil && prompts.prompts[0].emotionId == nil)
    }
    @Test func aBadPromptRowIsDroppedNotFatal() throws {
        let json = #"{"souls":0,"pebbles":2,"prompts":[{"name":"x","intensity":"two","moment_id":"bad","pebble_id":"bad","emotion_id":null,"happened_at":"nope","positiveness":1},{"name":"ok","intensity":2,"moment_id":"a6b3a0f7-b5a8-48d6-a266-2fb32ec54055","pebble_id":"85a50229-9d81-474e-b6e4-61dbb6a58d45","emotion_id":null,"happened_at":"2026-10-06T09:00:00Z","positiveness":1}],"skipped":[]}"#
        let prompts = try WrapDecoding.decode(PeoplePromptsPayload.self, from: Data(json.utf8)).model
        #expect(prompts.prompts.map(\.name) == ["ok"])
    }
}
```

- [ ] **Step 3: The model and payload.** In `PeoplePrompts.swift`:

```swift
/// `people_prompts` (spec §4.4, D10): the server's totals and the pebbles it asks about.
struct PeoplePrompts: Hashable {
    struct Prompt: Hashable, Identifiable {
        let pebbleId: UUID
        let momentId: UUID
        let name: String?
        let happenedAt: Date
        let intensity: Int
        let positiveness: Int
        let emotionId: UUID?
        var id: UUID { pebbleId }
        var valence: Valence { Valence.resolved(positiveness: positiveness, intensity: intensity) }
    }
    let pebbles: Int
    let souls: Int
    let prompts: [Prompt]
    let skipped: [String]
}

/// Lossy on `prompts` as `HomeWrapsPayload` is on months; `skipped` names decode as `[{name}]`.
struct PeoplePromptsPayload: Decodable {
    let model: PeoplePrompts
    let dropped: Int
    // CodingKeys pebbles, souls, prompts, skipped; a prompt row decodes
    // happened_at with `decodeFlexibleDate(forKey:)`, name/emotion_id with decodeIfPresent;
    // a failing row is skipped with the SkippedElement pattern of Features/Home/Guides/Guide.swift
    // and counted in `dropped`.
}
```

Read `Features/Home/Wraps/HomeWrapsPayload.swift` first and mirror its shape exactly (same `WrapDecoding`, same `Lossy` loop).

- [ ] **Step 4: Run the decoding tests** with `apps/ios/build/empty-test.sh PeoplePromptsDecodingTests`. Expected: 3 pass.

- [ ] **Step 5: The service.** In `PeopleService.swift`:

```swift
protocol PeoplePrompting: Sendable {
    /// `people_prompts(p_limit)`: the totals and up to `limit` pebbles with nobody on them.
    func prompts(limit: Int) async throws -> PeoplePrompts
}

struct PeopleService: PeoplePrompting {
    let client: SupabaseClient
    func prompts(limit: Int) async throws -> PeoplePrompts {
        let data = try await client.rpc("people_prompts", params: ["p_limit": limit]).execute().data
        let payload = try WrapDecoding.decode(PeoplePromptsPayload.self, from: data)
        if payload.dropped > 0 {
            Logger(subsystem: "app.pbbls.ios", category: "people.service")
                .warning("people_prompts: dropped \(payload.dropped) prompt(s) the client could not decode")
        }
        return payload.model
    }
}
```

- [ ] **Step 6: Failing state tests.**

```swift
@Suite("People onboarding state") struct PeopleOnboardingStateTests {
    static func prompts(pebbles: Int, souls: Int, n: Int, skipped: [String] = []) -> PeoplePrompts { … n fixture prompts … }
    @Test func noPebbles() { #expect(PeopleOnboardingState.make(Self.prompts(pebbles: 0, souls: 0, n: 0), dismissed: false) == .noPebbles) }
    @Test func pebblesWithoutSouls() {
        let p = Self.prompts(pebbles: 4, souls: 0, n: 3, skipped: ["x"])
        #expect(PeopleOnboardingState.make(p, dismissed: false) == .noSouls(prompts: p.prompts, skipped: ["x"]))
    }
    @Test func oneOrTwoSoulsKeepTwoPrompts() {
        let p = Self.prompts(pebbles: 9, souls: 2, n: 3)
        #expect(PeopleOnboardingState.make(p, dismissed: false) == .fewSouls(prompts: Array(p.prompts.prefix(2))))
    }
    @Test func threeSoulsOrNotNowEndIt() {
        #expect(PeopleOnboardingState.make(Self.prompts(pebbles: 9, souls: 3, n: 3), dismissed: false) == .done)
        #expect(PeopleOnboardingState.make(Self.prompts(pebbles: 9, souls: 1, n: 3), dismissed: true) == .done)
    }
    @Test func noSoulsWithNothingToAskStillExplains() {
        #expect(PeopleOnboardingState.make(Self.prompts(pebbles: 2, souls: 0, n: 0), dismissed: false) == .noSouls(prompts: [], skipped: []))
    }
}
```

- [ ] **Step 7: The state.** In `PeopleOnboardingState.swift`:

```swift
/// Spec §2: four states, decided by the server's totals and the device's "Not now" (D11).
enum PeopleOnboardingState: Equatable {
    case noPebbles
    case noSouls(prompts: [PeoplePrompts.Prompt], skipped: [String])
    case fewSouls(prompts: [PeoplePrompts.Prompt])
    case done

    static let storageKey = "people.prompts.dismissed"

    static func make(_ prompts: PeoplePrompts, dismissed: Bool) -> PeopleOnboardingState {
        if dismissed || prompts.souls >= 3 { return .done }
        if prompts.pebbles == 0 { return .noPebbles }
        if prompts.souls == 0 { return .noSouls(prompts: Array(prompts.prompts.prefix(3)), skipped: Array(prompts.skipped.prefix(2))) }
        return .fewSouls(prompts: Array(prompts.prompts.prefix(2)))
    }
}
```

- [ ] **Step 8: Run both suites**, expected green. **Commit** `feat(ios): decode people_prompts and derive the people onboarding state`.

### Task 2: `PromptRow`, `PeopleOnboardingView`, and the scrolled edit sheet

**Files:**
- Create: `apps/ios/Pebbles/Features/People/PromptRow.swift`, `apps/ios/Pebbles/Features/People/PeopleOnboardingView.swift`, `apps/ios/Pebbles/Features/People/PeopleOnboarding+Copy.swift`
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`, `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`, `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 1: `PromptRow(prompt:palette:onWho:)`.** Copy the row recipe of `Features/Path/Spine/Opening/WeekContents.swift` `rowView` (day column `SpineDates.shortWeekday` in `.contentsDay`, `StoneSeal(valence: prompt.valence, glyph: nil, palette: SpineColors.palette(palette, isBlank: false), isBlank: false, size: .contents)` in a `SpineLayout.contentsSealColumn` frame, the name in `.contentsName(EntryTypes.contentsNameType(intensity:valence:))` or `SpineCopy.untitled` in `onSurfaceVariant`, then instead of the time a `Button(onWho)` labelled with the kicker word `people.prompt.who` ("who?" / "qui ?") in `.spineType(.kicker)` and `Color.pebbles.primary`, `.accessibilityLabel(people.prompt.whoHint)`). Rules top and bottom in `Color.pebbles.rule`. The whole row is one accessibility element: name + hint. No `Date` formatter pinned to a locale: `SpineStrings.appLocale`/`dateLocale` and `SpineCalendar.current`.

- [ ] **Step 2: Copy.** `PeopleOnboarding+Copy.swift` holds the keys (all added to `Localizable.xcstrings` as text next to the `"home.firstFive.title"` entry, same JSON shape, EN + FR translated): `people.empty.kicker` "Nobody named yet" / "Personne encore", `people.empty.line1` "A soul is a person or a being in your life." / "Une âme, c'est une personne ou un être de ta vie.", `people.empty.line2` "You name them on a pebble." / "Tu la nommes sur un galet.", `people.empty.line3` "Every moment you share gathers on their page." / "Chaque moment partagé se rassemble sur sa page.", `people.empty.record` "Record a first moment" / "Noter un premier moment", `people.empty.question` "Who was there?" / "Qui était là ?", `people.empty.latest` "Your latest pebbles" / "Tes derniers galets", `people.empty.notAsked` "Not asked: %@. Nothing in it points outward." / "Pas demandé : %@. Rien n'y regarde vers les autres.", `people.empty.placeholder1` "A tag makes a soul." / "Une étiquette crée une âme.", `people.empty.placeholder2` "Their page gathers every moment you share." / "Sa page rassemble chaque moment partagé.", `people.few.title` "Was someone there too?" / "Quelqu'un d'autre était là ?", `people.few.mutual` "Souls who also use Pebbles can connect later; that part is optional." / "Les âmes qui utilisent Pebbles peuvent se connecter plus tard ; c'est facultatif.", `people.few.notNow` "Not now" / "Pas maintenant", `people.prompt.who` "who?" / "qui ?", `people.prompt.whoHint` "Name who was there" / "Dire qui était là".

- [ ] **Step 3: `PeopleOnboardingView`.** Inputs: `state: PeopleOnboardingState`, `palette: (UUID?) -> EmotionPalette?`, `onRecord: () -> Void`, `onWho: (PeoplePrompts.Prompt) -> Void`, `onNotNow: () -> Void`. Draws:
  - `.noPebbles`: `VStack(alignment: .leading, spacing: Spacing.md)` with the kicker (`.spineType(.kicker)`, `outline`), the three lines (`.spineType(.summary)`, `onSurfaceVariant`), a text-link `Button(people.empty.record, action: onRecord)` (`.pebblesFont(.callout)`, `primary`), then `SoulItem(case: .create, soul: nil, count: nil)` centred under a `Divider`-less gap, `.accessibilityHidden(true)` on the placeholder (it is decorative here; the create button is the toolbar `+`).
  - `.noSouls`: kicker, the italic question (`.spineType(.summary).italic()`), the three lines, then a card (`RoundedRectangle(cornerRadius: 16).stroke(Color.pebbles.rule)` background `surfaceContainerLow` as `HomeChecklistView` draws its card) titled `people.empty.latest` (`SpineSectionHeader`), one `PromptRow` per prompt; if `prompts.isEmpty` the card is not drawn. Under the card, for each `skipped` name, one line `Text(String(format: String(localized: "people.empty.notAsked"), name))` in `.spineType(.summary).italic()` `onSurfaceVariant`. Then the `SoulItem(case: .create…)` placeholder with the two placeholder sentences beneath (`.spineType(.summary)`).
  - `.fewSouls`: only the card, smaller: `SpineSectionHeader(people.few.title)`, up to two `PromptRow`s, `people.few.mutual` in `.spineType(.summary).italic()`, and a trailing text-link `Button(people.few.notNow, action: onNotNow)`.
  - `.done`: `EmptyView()`.
  All text in `Color.pebbles.*`; nothing coloured but the seals (D2).

- [ ] **Step 4: Edit sheet scroll (D-plan-1).** `EditPebbleSheet` gains `var scrollsToSouls = false` passed to `PebbleFormView(scrollsToSouls:)` (default false). In `PebbleFormView`, wrap the `Form` in `ScrollViewReader { proxy in … }`, give the Souls `Section` `.id(PebbleFormView.soulsAnchor)` (`static let soulsAnchor = "souls"`), and `.onAppear { if scrollsToSouls { proxy.scrollTo(Self.soulsAnchor, anchor: .top) } }`. No other change to the form; `HeldCardPage`'s call site is untouched (default false).

- [ ] **Step 5: Build** `apps/ios/build/empty-test.sh ThemeLiteralsTests` (compiles everything; theme rule green). **Commit** `feat(ios): draw the people onboarding from the spine's parts`.

### Task 3: Wire `SoulsListView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`

- [ ] **Step 1: State.** Add `@AppStorage(PeopleOnboardingState.storageKey) private var promptsDismissed = false`, `@State private var prompts: PeoplePrompts?`, `@State private var quickMoment = false`, `@State private var editing: EditTarget?` (`struct EditTarget: Identifiable { let detail: MomentDetail; var id: UUID { detail.id } }`), `@Environment(EmotionPaletteService.self) private var palettes`, `@Environment(MomentsRevision.self) private var revision`.

- [ ] **Step 2: Loading.** After `load()` succeeds: `if items.count < 3 && !promptsDismissed { await loadPrompts() } else { prompts = nil }` (D-plan-3). `loadPrompts()` calls `PeopleService(client: supabase.client).prompts(limit: 3)`; on error log and keep the last value (a failed read shows the plain grid or, with no souls, the old `ContentUnavailableView`-free state: with `items.isEmpty` and `prompts == nil`, draw `PeopleOnboardingView(state: .noPebbles…)`? No: draw nothing but the grid area and a `ProgressView` while the first read runs; after a failed first read fall back to the previous `ContentUnavailableView("No souls yet")` so the tab never goes blank).

- [ ] **Step 3: Content.** Replace the `items.isEmpty` branch: `let state = prompts.map { PeopleOnboardingState.make($0, dismissed: promptsDismissed) }`; when `items.isEmpty`, show `ScrollView { PeopleOnboardingView(state: state ?? .noPebbles …).padding(Spacing.lg) }` once prompts have loaded (state 1 or 2), the fallback above on failure. When items are not empty, keep the grid and append `PeopleOnboardingView(state:)` under the `LazyVGrid` inside the same `ScrollView` when the state is `.fewSouls` (it draws nothing otherwise).

- [ ] **Step 4: Actions.** `onRecord` → `quickMoment = true`; `.sheet(isPresented: $quickMoment) { QuickMomentSheet(onSaved: { _, _ in revision.bump(from: .people); Task { await load() } }) }`. `onWho(prompt)` → `Task { do { let detail = try await MomentsService(client: supabase.client).moment(id: prompt.momentId); if detail.pebble != nil { editing = EditTarget(detail: detail) } } catch { logger.error(…); } }`; `.sheet(item: $editing) { target in EditPebbleSheet(momentId: target.detail.id, pebble: target.detail.pebble!, onSaved: { revision.bump(from: .people); Task { await load(); await refs.refreshSouls() } }, scrollsToSouls: true) }` (guard the pebble before constructing the target, so no force unwrap: store `pebble` in the target). `onNotNow` → `promptsDismissed = true` (the state recomputes to `.done`).

- [ ] **Step 5: Simulator** on the maintainer's account is the maintainer's; locally run `apps/ios/build/empty-test.sh ThemeLiteralsTests PeopleOnboardingStateTests`. **Commit** `feat(ios): start people from the pebbles you have`.

### Task 4: References and ship Part 1

**Files:**
- Create: `apps/ios/PebblesTests/References/PeopleReferences.swift`
- Test fixture: `PeoplePayloads` from Task 1

- [ ] **Step 1: References** following `HomeReferences.swift`'s pattern exactly (`@Suite("People references") @MainActor struct PeopleReferences` inside `extension References`; `@Test(arguments: ReferenceScheme.allCases)`, `locales = scheme == .paper ? [.en, .fr] : [.en]`; `.frame(width: 393, alignment: .top).background(Color.pebbles.surface)`, padding `Spacing.lg`): `peopleNoPebbles` (state `.noPebbles`), `peopleNoSouls` (state from the verbatim payload with `souls` forced to 0: build `PeoplePrompts(pebbles: 7, souls: 0, prompts: payload.prompts, skipped: payload.skipped)`), `peopleFewSouls` (the verbatim payload as is: souls 1). Palette closure returns `nil` (default stone palette), so the references do not depend on the palette service.
- [ ] **Step 2: Run** `References/PeopleReferences`; every failure must be the missing-reference kind. Inspect the `.actual.png`s under `apps/ios/build/reference-diffs/PeopleReferences/`.
- [ ] **Step 3: Ship.** Arkaik: `update_node` the People view (`list_nodes query:"souls"` / "people", species view, platform ios) to `development` if not already, and `create_node` an acceptance "People starts from the pebbles you have" (`informs`, `connects`) covering it. PR `feat(ios): start people from the pebbles you have`, body "Part 1 of #1256", labels feat/ui/ios/no-lab-note, M64, `gh stack submit --auto --open` then `gh pr edit`.

## Part 2 — Collections (`feat/1256-ios-collections-note`)

### Task 5: `CollectionsNote` and its state

**Files:**
- Create: `apps/ios/Pebbles/Features/Collections/CollectionsNote.swift`, `apps/ios/Pebbles/Features/Collections/CollectionsNoteState.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`, `apps/ios/Pebbles/Resources/Localizable.xcstrings`
- Test: `apps/ios/PebblesTests/Features/Collections/CollectionsNoteStateTests.swift`

- [ ] **Step 1: Failing state tests.**

```swift
@Suite("Collections note state") struct CollectionsNoteStateTests {
    @Test func theCardShowsWithNoCollection() { #expect(CollectionsNoteState.make(count: 0, dismissed: false) == .card) }
    @Test func theFootnoteShowsAfterTheFirst() { #expect(CollectionsNoteState.make(count: 1, dismissed: false) == .footnote) }
    @Test func nothingAfterTheSecondOrADismissal() {
        #expect(CollectionsNoteState.make(count: 2, dismissed: false) == .none)
        #expect(CollectionsNoteState.make(count: 1, dismissed: true) == .none)
        #expect(CollectionsNoteState.make(count: 0, dismissed: true) == .none)
    }
}
```

- [ ] **Step 2: The state.** `enum CollectionsNoteState: Equatable { case card, footnote, none; static let storageKey = "collections.note.dismissed"; static func make(count: Int, dismissed: Bool) -> Self { if dismissed || count >= 2 { return .none }; return count == 0 ? .card : .footnote } }`.

- [ ] **Step 3: Copy** (xcstrings, text-inserted beside `"home.firstFive.title"`, EN + FR): `collections.note.kicker` "collections · a note" / "collections · une note", `collections.note.sentence` "A collection gathers pebbles along a thread: a trip, a project, a year with someone." / "Une collection rassemble des galets le long d'un fil : un voyage, un projet, une année avec quelqu'un.", `collections.note.step1` "Pick pebbles that belong together." / "Choisis des galets qui vont ensemble.", `collections.note.step2` "Give it a shape." / "Donne-lui une forme.", `collections.note.step2.stack` "stack · a pile" / "stack · une pile", `collections.note.step2.pack` "pack · a set with one place still empty" / "pack · un ensemble avec une place encore vide", `collections.note.step2.track` "track · stones along a line" / "track · des pierres le long d'une ligne", `collections.note.step3` "Name it, and it lives here." / "Nomme-la, elle vit ici.", `collections.note.aside` "A pebble can be in several collections. Deleting a collection never deletes its pebbles." / "Un galet peut être dans plusieurs collections. Supprimer une collection ne supprime jamais ses galets.", `collections.note.start` "Start a collection" / "Commencer une collection", `collections.note.sample` "Autumn, slowly" / "L'automne, doucement", `collections.footnote` "Try a track for something that unfolds in time." / "Essaie un track pour ce qui se déroule dans le temps.", `collections.footnote.dismiss` "Dismiss" / "Fermer".

- [ ] **Step 4: `CollectionsNote(onStart:)`** (the card, spec §3): one `VStack(alignment: .leading, spacing: Spacing.md)` inside the same card chrome as the checklist (`RoundedRectangle(cornerRadius: 16)` stroked in `rule`, fill `surfaceContainerLow`), `.frame(maxWidth: 393)` centred in the page with `Spacing.lg` padding: the kicker (`.spineType(.kicker)`, `outline`), the sentence (`.spineType(.summary).italic()`), three numbered steps as `HStack(alignment: .top)` of a number in `.spineType(.kicker)` and a `VStack` {caption in `.pebblesFont(.callout)`, the drawing}: (1) three lit `StoneSeal`s (`.highlightSmall`, `.neutralLarge`, `.lowlightMedium`, default palette, `size: .contents`); (2) the three mode marks, each `CollectionModeBadge(mode:)` followed by its description line (`.spineType(.summary)`, `onSurfaceVariant`), one per row; (3) one real `CollectionRow(collection: Collection(id: fixed, name: String(localized: "collections.note.sample"), mode: .track, pebbleCount: 3))`; the aside (`.spineType(.summary).italic()`, `onSurfaceVariant`); one text link `Button("collections.note.start", action: onStart)` (`.pebblesFont(.callout)`, `primary`). The drawings are `.accessibilityHidden(true)`; the card reads top to bottom. `CollectionsFootnote(onDismiss:)`: one `HStack` with the sentence in `.spineType(.summary).italic()` and an `xmark` button labelled `collections.footnote.dismiss`.

- [ ] **Step 5: Wire `CollectionsListView`.** `@AppStorage(CollectionsNoteState.storageKey) private var noteDismissed = false`; `private var note: CollectionsNoteState { CollectionsNoteState.make(count: items.count, dismissed: noteDismissed) }`. Replace the `items.isEmpty` `ContentUnavailableView` with `ScrollView { CollectionsNote(onStart: { isPresentingCreate = true }).padding(Spacing.lg) }` when `note == .card`; keep the `List` branch and, when `note == .footnote`, add a final `Section { CollectionsFootnote(onDismiss: { noteDismissed = true }) }` with `.listRowBackground(Color.clear)` and `.listRowSeparator(.hidden)`. A failed load keeps its error view.

- [ ] **Step 6: Run** `apps/ios/build/empty-test.sh CollectionsNoteStateTests ThemeLiteralsTests`, green. **Commit** `feat(ios): explain collections on the empty tab`.

### Task 6: References and ship Part 2

**Files:**
- Create: `apps/ios/PebblesTests/References/CollectionsReferences.swift`

- [ ] **Step 1: References** `collectionsNote` (the card at width 393), `collectionsNoteAx2` (paper, `dynamicType: .accessibility2`, proves the word budget scrolls rather than clips), `collectionsFootnote` (the footnote row at width 393). Same suite shape as `HomeReferences`.
- [ ] **Step 2: Run** `References/CollectionsReferences`; failures only of the missing-reference kind; inspect the actual PNGs.
- [ ] **Step 3: Ship.** Arkaik: the Collections view node to `development`, `create_node` acceptance "Collections explains itself until the second one" (`informs`) covering it. PR `feat(ios): explain collections on the empty tab`, body `Resolves #1256` (Part 2 of 2, stacked on Part 1), labels feat/ui/ios/no-lab-note, M64.

### End of stack

- [ ] Re-baseline bottom-up: label Part 1 with `rebaseline-ios-screenshots`, wait, `git branch -f` over the bot commit, `gh stack rebase --no-trunk`, push, then Part 2. A bot-pushed head leaves the PR's runs in `action_required`: approve them (`gh api -X POST repos/<owner>/<repo>/actions/runs/<id>/approve`).
- [ ] Record "As built" and "Lessons learned" below. The maintainer merges with `gh stack sync && gh stack merge --yes --squash`, then removes `.worktrees/ios-empty`.

## Self-review against the spec

- §2 states 1–4 → Task 1 (`make`), Task 2 (views), Task 3 (wiring, `people.prompts.dismissed`); "who?" → `EditPebbleSheet` (D-plan-1); the `+` stays in the toolbar (untouched). D10 lives on the server; the client never filters prompts itself (only truncates to the spec's counts).
- §3 → Tasks 5–6: the card, the three steps with real parts, the aside, the text link, the footnote after one, nothing after two or on dismissal (`collections.note.dismissed`), word budget pinned by the ax2 reference.
- §8 tests → the verbatim `people-prompts.json` with explicit nulls (Task 1), both empty states in Paper and Night (Tasks 4, 6).
- Android mirror: `PromptRow`, the four states and the note's states are the shapes Android copies (#1257 is copy only; the Android empty states are a later issue).

## As built

- **Shipped 2026-10-27** as stack #1295: PR #1291 (People, Tasks 1–4) and PR #1294 (Collections, Tasks 5–6, `Resolves #1256`), squash-merged into main at 60ac456a. References recorded by the CI bot bottom-up (People, then Collections); the bot-pushed heads left every workflow run in `action_required`, approved through the API.
- **Part 1 review fixes taken in-part:** the prompts read is gated on the grid count only (a "Not now" with no souls used to leave a spinner); the edit sheet scrolls to the `SelectedSoulsRow` from `.task` after a yield, not to a `Section` from `onAppear`; a failed "who?" shows a "Couldn't open" alert with `people.prompt.openFailed`; the onboarding scrolls to top on tab re-tap; `Spacing.lg` (17 pt) for the card radius, as the checklist.
- **Part 2 review fixes taken in-part:** no `.italic()` after `.spineType(.summary)` (the rank is already italic); each step reads as one VoiceOver line; the footnote's dismiss has a 44 pt target. `.fixedSize(horizontal: false, vertical: true)` on the mode descriptions is what lets them wrap in the baseline-aligned row.
- **Owed:** the "Not asked" line names one reason while `people_prompts.skipped` is produced by two rules (valence, outward domain) and carries no reason; a shared `SpineNoteCard` (kicker, summary, card chrome, text link are now in `HomeChecklistView`, `PeopleOnboardingView` and `CollectionsNote`); `CollectionModeBadge`'s emoji inside a D2 card and its caption truncating at accessibility sizes (#1275 adjacent); the step captions and text links do not scale (#1274); the zero-collection spinner on every tab return; Android mirrors of both states.

## Lessons learned

- **Gate a server read on what the screen already knows, never on the dismissal flag.** The flag belongs to the projection (`make(_:dismissed:)`); using it to skip the read too created a state with no data and no fallback. One source of truth per decision.
- **`ScrollViewReader.scrollTo` in a `List`: id a row, scroll from `.task` after a `Task.yield()`.** A `Section` id from `onAppear` fires before the collection view has laid out and is honoured inconsistently across iOS 17 point releases.
- **`SpineType.summary` is italic by definition.** Layering `.italic()` on it is a no-op that reads like a design choice; the reviewers flagged it in both parts. The rank carries the style.
- **A baseline-aligned `HStack` truncates a wrapping `Text`.** `.fixedSize(horizontal: false, vertical: true)` on the text is the fix, and the ax2 reference is what proves the word budget grows instead of clipping.
- **Three copies of a card is the signal.** The kicker + summary + hairline card chrome now live in three features; the next one gets a shared view first.
- **The worktree needs `apps/ios/Config/Secrets.xcconfig` too** (gitignored): without it `xcodegen generate` fails spec validation. Added to the worktree-config note.
