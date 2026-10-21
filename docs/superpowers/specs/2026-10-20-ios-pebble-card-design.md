# iOS pebble detail: the held card — design

Date: 2026-10-20 · Surface: iOS · Issues: #1146 (the card), #1160 (the flight), #1099, #1145 · Stack E (E0a, E0b, E1–E5) · Milestone M64 · Status: design, awaiting plan

## Goal

Pressing a pebble on the iOS Spine opens **the card**: the Spine's card held in the hand, with a recto (the moment) and a verso (the pebble), turned by a button or a drag, flying out of its slot and back. A moment with no pebble opens as a plain page and prints its card into a "Not a pebble yet" place once completed. This mirrors Android stack #1156 (spec `2026-10-20-android-pebble-card-design.md`, the contract; plan `2026-10-20-android-pebble-card.md`, the lessons). Where this spec is silent, the Android spec governs.

**Non-goals.** No schema or RPC work: data comes from `v_moments_full` (via `MomentsService.moment(id:)`) and the reference caches. The card's design is final. The rejected ideas do not come back: gem words, I/II/III, seals or gauges; visible edit links; a folded corner; tilted or shadowed stamps; pills for domains or collections. No Layers (reflective strata). No web. No Swift 6 migration. No iOS 18 API, no `#available`. #1143 (red valence ink) is done in stack B.

**Built on.** Colours from stack A (`Color.pebbles.*`, including the orange `error` role), type from stack B (`SpineType`, `YsabeauFace`, the lockup-word rank, `Scaling.card(cap: 1.3)`, the reference-image harness of B2, #1159), the destructive-menu rule from stack A2, the tab root, `MomentsRevision` and the sign-out reset from stack C (`PebblesTabView` in `RootView`), and the Spine, `SpineCard`, `PeopleLines`, the stone press, the per-slot focus state and the frame registry from stack D.

## Decisions

### D1 — The flight is a single-hierarchy overlay with a measured-frame FLIP (#1160, Option 2)

The held card is a full-screen layer, `HeldCardHost`, in a `ZStack` in `RootView` above stack C's `PebblesTabView`. It is not a presentation. Opening animates a transform (`scaleEffect`, fill-width and top-anchored, plus `offset`) from the slot's global rect, read from stack D's registry, to the card's resting rect; the slot is hidden for the flight; the page chrome fades in. Only iOS 13–17 APIs.

Considered and rejected (the four options of #1160 and the research brief):
- **Option 1, zoom on 18 and a push on 17 behind `#available`.** Two presentation paths; 17 users get no flight; the guard fails the #727 test ("no guards around APIs that have an iOS 17 equivalent"), and an equivalent exists. SwiftUI zoom also cannot make the close follow the face: the source is bound at push time.
- **Option 2 as #1160 words it, with `matchedGeometryEffect`.** Kept the overlay, dropped the matcher: `matchedGeometryEffect(properties: .frame)` proposes a new size each frame, which re-lays out both faces (Android's `ScaleToBounds` lesson), and its source sits in stack D's lazy paging `ScrollView`, where a slot scrolled out of the lazy container stops being a match source mid-close. It also cannot express the close-by-face rule (a verso fades). A FLIP of a measured rect scales a ready layer and falls back to a fade when the rect is missing.
- **Option 3, raise the floor to 18.** Needs adoption numbers nobody has; even then, zoom cannot fade a verso close or a delete close.
- **Option 4, a UIKit transitioning-delegate animator.** Real interactive transitions, but every `@Environment` service must be re-injected into a new hosting root, the source rect and face cross the SwiftUI/UIKit seam, and it is device-test only. It is the **no-go fallback** of the E4 spike (§Flight, Spike), sharing `CardFlight` and `FlightGeometry`.

Why Option 2: it flies on every supported iOS with no guard; it is the only SwiftUI-native option in which every rule is exact (the close follows the committed face; stone presses, bare moments, printing and delete do not fly; Reduce Motion is a chosen crossfade); it sits above the `TabView` without caring what is under it; the Spine stays mounted beneath, so the slot is there to return to. The costs (a hand-built edge swipe, VoiceOver modality and focus return, a seeded first frame) are specified below.

### D2 — The detail leaves the sheet; one page serves the interim and the overlay

The card needs the whole height. The page is one view, `HeldCardPage`, with its own top bar (no `NavigationStack` toolbar, so the chrome can fade on its own). In E1–E3 the existing `PebbleDetailSheet` becomes a thin wrapper that presents `HeldCardPage` at the `.large` detent, from all three callers. In E4 the Path opens it in `HeldCardHost`; in E5 the soul and collection callers do too (no flight) and the sheet is deleted.

Considered and rejected: building the overlay in E1 (the flight would land before the faces are settled, and the spike would gate the whole stack); keeping a sheet for the soul and collection callers (two containers for one page, and sheets lose the full height).

### D3 — The ⋮ uses the destructive-menu rule stack A2 settles

The card's ⋮ holds Edit moment, Edit pebble (pebbles only), a divider, then Delete in `Color.pebbles.error` (orange, never red, #990). There is one rule for every stack, decided in A2: A2 tests `.tint(Color.pebbles.error)` on a system `Menu` item on device. If it is honoured, the card's ⋮ is a system `Menu` with that tint on Delete. If it is ignored, A2 ships one shared hand-built `PebblesMenu` in `Components/`, and the card's ⋮ uses it. The card adds no menu of its own (there is no `CardMenu`). Considered and rejected: `role: .destructive` without A2's rule (system red); a `confirmationDialog` as the menu (a sheet of actions is not a menu, and Delete already opens one).

### D4 — Postmark ink is a real mask, with Android's speckles as the fallback

Each stamp is drawn in one `Canvas`. Inside `context.drawLayer`, the ink (rings, ring text, glyph) is drawn, then the noise image is drawn over it with `context.blendMode = .destinationOut`, so the pits are true holes and the panel shows through. The canvas sits in a `.drawingGroup()` so the layer is flattened once. The E1 device check (iPhone 15, paper and Night) measures contrast at the faintest density (≥ 4.5:1, mocks estimate 5.5:1) and confirms the pits survive inside the turning panel. **If it fails**, the fallback is Android's: the same noise tinted with the panel colour, drawn over the ink with `.normal` blend, clipped to the disc. Considered and rejected: `.mask` on a SwiftUI view stack (one mask per stamp, alpha-inverted noise, and an extra offscreen pass per stamp in a turning layer).

### D5 — The turn rotates a ready layer; the faces swap at 90°

`rotation3DEffect` about Y, on the panel only; the shell and footer never move. Both faces are always built and laid out in one `ZStack` (the taller sets the height), each `.compositingGroup()`, the hidden one at `opacity(0)` and `.accessibilityHidden(true)`, never removed with `if`. An `Animatable` modifier, `CardTurnEffect`, receives the animated progress and decides per frame which face is opaque, so the swap lands exactly on the frame that crosses 0.5. Considered and rejected: building the back on demand (Android's "an unplaced face loses its display list"); `.drawingGroup()` on the faces (rasterises the Metal `layerEffect` stone and the photo at layer resolution).

### D6 — Completion is state; the print ends where it ends

`HeldCardModel` publishes the refreshed moment and its `completion` in one assignment. The print is a function of elapsed time read in a `TimelineView(.animation)`; the frame that reaches t = 1 ends it. Nothing in `onDisappear`, `defer`, a `.task` cancellation or a `withAnimation` completion clears print state. Considered and rejected: an event (`onCompleted` → "play"), which Android proved races the swap; clearing on disappear, which Android proved replays the print after Edit pebble and back.

### D7 — The face survives restoration through `@SceneStorage`; there is no request counter

The host keeps `{momentId, landing face, printingFor}` in `@SceneStorage("heldCard")`. A relaunch after a background kill reopens the card at rest (no flight) on the saved face. `RootView`'s sign-out reset (stack C's contract) clears `heldCard` with the tab state, so another account never reopens this card. Android's `faceRequest` counter exists because a second Spine press can reach an open detail; on iPhone the overlay covers the Spine, the soul list and the collection list, so a request is answered once, at open. Considered and rejected: porting the counter (dead code on iPhone).

### D8 — #1099: an untouched field is omitted, and absent means keep

`complete_pebble` replaces `domain_ids` and `collection_ids` only when the key is present (`private.jsonb_has`, `20261012090100_moments_split_writers.sql:185`, `:260`). `CompleteDraft` gains `prefill: Prefill?` (`struct Prefill: Equatable { let domainId: UUID?; let collectionId: UUID? }`), set only by `init(from: MomentDetail.Pebble)`. `CompletePebblePayload.init?(momentId:draft:)` then sends `domainIds = nil` when `draft.prefill?.domainId == draft.domainId`, else `[domainId]`; and `collectionIds = nil` when `draft.prefill?.collectionId == draft.collectionId`, else `draft.collectionId.map { [$0] } ?? []`. A pebble with three domains keeps all three through an edit of its emotion; picking another domain collapses the set to that one, as #1099 asks. `isEmpty` ignores `prefill`. The Complete flow never sets `prefill`, so a first completion always sends both keys. Android's mirror (`MomentPayloads.kt`) stays on #1099 for its own PR. Considered and rejected: sending the full original set back (a read-modify-write that races another device's edit); a multi-select (product scope).

### D9 — The band draws each collection's glyph (#1145)

`ReferenceDataService`'s collection select becomes `id, name, glyph_id, glyphs(id, name, strokes, view_box)` (the souls query's shape; a single-table read with an embed), and `PebbleCollection` gains `glyph: Glyph?` decoded optional. The band leads each name with `GlyphThumbnail(strokes:side: 14, strokeColor:)` when the collection has a glyph, else the `square.stack.3d.up` mark. Android shows the generic mark; #1145 stays open for Android.

## Component tree and files

New code lives in `apps/ios/Pebbles/Features/Path/Card/` (pure logic in `Card/Logic/`). Shared Spine pieces stay in stack D's folder.

```
RootView                                         RootView.swift (E4: adds the host)
└ ZStack
  ├ PebblesTabView (stack C) … PathView → Spine (stack D) ─ reports slot rects to CardFrameRegistry
  └ HeldCardHost                                 Card/HeldCardHost.swift (E4)
    └ HeldCardPage(request:)                     Card/HeldCardPage.swift
      ├ CardTopBar: ← Back · Share (public) · ⋮  Card/CardTopBar.swift
      │  └ ⋮ menu: system `Menu` or A2's `PebblesMenu` (D3)
      └ ScrollView (vertical)
        ├ PebbleCard            (pebble)         Card/PebbleCard.swift
        │  ├ SpineCardShell     stock, shadow, emotion edge (stack D, split in E1)
        │  ├ panel · CardTurnEffect              Card/CardTurnEffect.swift
        │  │  ├ CardRecto                        Card/CardRecto.swift
        │  │  │  ├ SpineCardFace(u: width/70, namedSouls: 8)  (stack D, split in E1)
        │  │  │  ├ CardSlip         F3: the name on the emotion mat
        │  │  │  └ CardLeaf         dashed score, full dateline, words   Card/CardLeaf.swift
        │  │  └ CardVerso                        Card/CardVerso.swift
        │  │     ├ VersoLockup      ValenceLockup → word + span
        │  │     ├ emotion name     Ysabeau, under a hairline
        │  │     ├ PebbleStoneView  large, in its raked clearing with rings
        │  │     └ PostmarkRow → DomainPostmark   Card/DomainPostmark.swift
        │  ├ CollectionBand     over the verso's foot, to the shell's edges   Card/CollectionBand.swift
        │  └ CardFooter         still, below the border                       Card/CardFooter.swift
        │     ├ privacy glyph · grade (small caps) · explanation (italic)
        │     └ TurnButton: "TURN" / "Pebble"|"Moment" + FaceMiniature        Card/FaceMiniature.swift
        └ BareMomentPage        (no pebble)      Card/BareMomentPage.swift
           └ NotAPebbleYetPlace → CardCompletion Card/CardCompletion.swift
Models:  HeldCardModel.swift · CardTurnModel.swift · HeldCardHostModel.swift (Card/)
Logic:   RingFit · ValenceLockup · PostmarkNoise · TurnSettle · TurnGeometry · CardFlight ·
         FlightGeometry · CompletionMotion (Card/Logic/*.swift) · CardLayout.swift (all geometry)
```

`SpineCard` (stack D) is split into `SpineCardShell` and `SpineCardFace` as a pure move in its own E1 commit; `SpineCardFace` takes `u` unclamped (the Spine caps it) and `namedSouls: PeopleLines.namedOnCard` (8, defined in D). Card text scales through `SpineType`'s `Scaling.card(cap: 1.3)`.

**`CardLayout`** (points, from the Android `CardLayout`): `cardWidth(available) = min(available − 32, 440)`; `uDivisor 70`; footer padding 16 h / 12 v, gap 12; turn target 44 pt (the iOS minimum, Android's 48 dp); miniature 20 × 28 at −7°; verso stone width 150; postmark diameter 72, gap 6, glyph field 38, outer ring 1.6 (inset 1.4), inner ring 0.8, text baseline inset 10.5 (absolute), inner ring 2.4 under it; postmark row to band 18; band min height 40, reaching 20 over the panel's foot; verso mat 1.6 u, mat padding 2.2 u; noise 128 px.

## State model

All `@Observable @MainActor final class` or value types, logged through `Logger(subsystem: "app.pbbls.ios", category: "held-card")`.

| Value | Type | Owner | Notes |
|---|---|---|---|
| request | `HeldCardRequest?` | `HeldCardHostModel` (RootView, environment) | `momentId`, `face: CardFace`, `seed: MomentSummary?`, `soulOrder: [String]`, `onChanged: @MainActor () -> Void`. Nil = no card. |
| flight source rect | `CGRect?` | `HeldCardHostModel` | Captured from the registry at press; re-read at close (the Spine may have scrolled); nil → fade. |
| flight phase | `enum FlightPhase { idle, opening, open, closing(progress: Double) }` | `HeldCardHostModel` | `closing` progress is the edge swipe's scrub. |
| return focus | `UUID?` | `HeldCardHostModel` | Set on close; consumed by stack D3's per-slot `@AccessibilityFocusState` on `SpineEntry` (exposed through the registry / `PathModel`). |
| detail | `MomentDetail?` | `HeldCardModel` (page) | Loaded in `.task`; reloaded in place after edits and completion. |
| completion | `Completion?` (`struct Completion: Equatable { let pebbleId: UUID }`) | `HeldCardModel` | Set in the same assignment as a refreshed `detail` whose pebble appeared on a moment that was bare; survives later refreshes until `completionShown()`; cleared if the pebble disappears. |
| leaving | `Bool` | `HeldCardModel` | Set **before** the close that a delete triggers, so that close never flies. |
| moments revision | `MomentsRevision` (stack C, environment) | `HeldCardModel` bumps it | From E5, on every save (Edit moment, Edit pebble), delete and completion, so the Path, soul and collection lists reload; until E5 the sheet's `onPebbleUpdated` does that. |
| committed face | `CardFace` | `CardTurnModel` (page `@State`) | Changes only when a turn lands. |
| progress | `Double` 0…1 | `CardTurnModel` | 0 = the committed face, 1 = the other. |
| front | derived: `progress < 0.5 ? face : face.other` | `CardTurnModel` | What the footer and VoiceOver follow. |
| landing | derived: settle target, else `front` while held, else `face` | `CardTurnModel` | What is saved. |
| press request | `CardFace` in `HeldCardRequest` | Spine → host | Card or entry press: `.recto`; stone press: `.verso`; a bare moment: always the plain page. |
| print in progress | `printingFor: UUID?` | `HeldCardHostModel`, mirrored to `@SceneStorage("heldCard")` | Set when the print starts; cleared only by the frame where t reaches 1. Still set on restore → `cutShort`: land verso up at rest. |
| printed over | `Bool` | `HeldCardModel` | Keeps the card verso up after a print, until the next request. |
| announcing | `Bool` | `HeldCardModel` | The verso heading is announced once after a print (`AccessibilityNotification.Announcement`), then false after 1 s. |

`CardFace: String, Codable` (with `other`) comes from stack D, since the press carries it.

## The faces and the footer

As the Android spec's Recto, Verso and Bare moment sections, with these iOS specifics.

- **Recto.** `SpineCardFace` at the card's width (a uniform scale of the Spine card), the photo at its own ratio from stack D's ratio cache, the legend naming up to 8 souls with their marks, then `CardLeaf`: the full dateline `happenedAt.formatted(.dateTime.weekday(.wide).day().month(.wide).year())` + " · " + `.formatted(date: .omitted, time: .shortened)`, then the description, or the dateline alone. F3 (no photo): `CardSlip` carries the name as the Spine's entry sets it.
- **Verso.** The emotion's mat full-bleed around a stock panel; the lockup (word in stack B's `SpineType` lockup-word rank, small / medium / large); the emotion's `localizedName` once under a hairline; `PebbleStoneView(valence:glyph:palette:)` at width 150 in its clearing (`followsTilt: false`); the postmark row; the band; the footer. Postmark ink: the emotion palette's `dark` on paper, `light` at Night. Highlight ramp: paper, the stone's highlight body tones (`StoneTones.starting(for: .highlight)` resolved through `color(_:in:)`) top to bottom; Night, `light` → `secondary` at 0.4 → `secondary` mixed 70 % toward `primary` at 1.
- **Postmark.** Ring text through `SpineType.postmarkRing(size)` (`YsabeauFace`, small caps, never `Font.custom`; font scale 1, a deliberate exception: decoration inside a fixed stamp, the names are read from the verso's list); each character resolved once (`context.resolve(Text)`) and drawn rotated about the centre by `mid / r`, the first repetition centred upright at the top; glyph from `refs.domains` by id (`strokes`, `viewBox`), none → ring only; noise seeded by the domain id; no rotation, shadow or tap. Diameter `min(72, (innerWidth − 6·(n−1)) / n)`.
- **Band.** A paper band across the foot, past the border to the shell's edges, turning under; italic names separated by a small diamond, never starting or ending a line on one (a `Layout` that wraps), each led by its glyph (D9). No collections, no band.
- **Footer.** Left: the visibility glyph (`Visibility.systemImageName`, 16 pt), `Visibility.label` in small caps, the explanation in italic. One a11y element: "<label>. <explanation>". Right: `TurnButton`, no container, one target from the label to the card's edge, clipped off the privacy sentence; "TURN" in small caps over "Pebble"/"Moment" in italic, then `FaceMiniature` (a `Canvas`: to the pebble, a 15 × 22 stock card with an inner rule and a 9 × 7 stone in the emotion spot at 45 % height; to the moment, a photo block at 35 % opacity down to 40 % and three legend lines; 1 pt ink outline; −7°). Side by side unless the button would take over half the width (large type), then it drops under the privacy, trailing-aligned. The footer names the face **not in front**; it switches at the half.
- **Top bar.** ← Back (`Back`), Share as a `ShareLink(item: SharedPebbleLink.url(for: pebble.id))` for public pebbles only, ⋮ (`More`) opening the menu of D3. Disabled while loading and deleting. No privacy badge, no title.
- **Bare page.** Kicker `happenedAt` as weekday, day and month in small caps + " · " + time, in the moment's zone; the title (a heading); the photo on a `Color.pebbles.stock` mat if any; the words; then the place: a dashed card outline with album corners, a dashed stone outline, `bare.place.kicker`, `bare.place.body`, and `Make it a pebble`, which presents `CompleteFlowView(momentId:momentName:onCompleted:)` as today. The menu holds Edit moment and Delete.

## Strings (EN / FR)

New keys in `Pebbles/Resources/Localizable.xcstrings` (dotted keys, as `domain.*.name`, because "Moment" and "Your pebble" already exist as literal keys with other uses), read as `LocalizedStringResource("card.turn", defaultValue: "Turn", comment: …)`. "Galet", never "caillou".

| Key | EN | FR | Android key |
|---|---|---|---|
| `card.turn` | Turn | Retourner | `card_turn` |
| `card.turn.toPebble` | Pebble | Galet | `card_turn_to_pebble` |
| `card.turn.toMoment` | Moment | Moment | `card_turn_to_moment` |
| `card.turn.action.toPebble` | Turn over to your pebble | Retourner vers ton galet | `card_turn_action_to_pebble` |
| `card.turn.action.toMoment` | Turn over to the moment | Retourner vers le moment | `card_turn_action_to_moment` |
| `card.verso.heading` | Your pebble | Ton galet | `card_verso_heading` |
| `card.domains.a11y` | Life domains | Domaines de vie | `card_domains_a11y` |
| `card.collections.a11y` | Collections | Collections | `card_collections_a11y` |
| `bare.place.kicker` | Not a pebble yet | Pas encore un galet | `bare_place_kicker` |
| `bare.place.body` | Its card prints here once you say what it meant: a feeling and its weight, the parts of your life it touched, who was there. | Sa carte s'imprimera ici quand tu diras ce qu'il a compté : un sentiment et son poids, les parts de ta vie qu'il a touchées, qui était là. | `bare_place_body` |

Reused: `Visibility.label` (Secret / Private / Public), the three explanations of `CompletePrivacyStep` ("Only you can see this one.", "Your mutual connections can see it.", "Anyone with the link can see it." — moved to `Visibility.explanation` in E1 so both read one source), `Valence.headline` (Highlight / Lowlight / Moment, OF MY DAY / WEEK / MONTH), `Edit moment` (Modifier le moment), `Edit pebble` (Modifier le galet), `Delete` (Supprimer), `Make it a pebble` (En faire un galet), `Share`, `Back`, `More`. `LocalizationTests` gains the new keys' both-language check.

## VoiceOver reading order

Exactly as the Android spec lists it. The hidden face is `.accessibilityHidden(true)`.

- **Recto:** the title (`.isHeader`), the date and time, the emotion, the souls (each name once), the photo's description, the words, privacy, then the turn button ("Turn over to your pebble").
- **Verso:** "Your pebble" (`.isHeader`), the valence ("Highlight of my month": word + span combined), the emotion, the stone (stack D's stone description), "Life domains" with each name once (the ring repetition hidden), "Collections" with each name, privacy, then the turn button ("Turn over to the moment").
- Stamps and the band are not buttons. The turn is the footer's visible button, last; no custom action on the card (the lesson: an action on an unfocused container is unreachable). The button's label is the action string alone, no hint repeating it.
- **Bare page:** the kicker, the title (`.isHeader`), the photo's description, the words, the place's kicker and sentence, then `Make it a pebble`.

## Pure functions to test

```swift
// Card/Logic/RingFit.swift
struct RingFit: Equatable { let text: String; let repetitions: Int; let size: CGFloat; let tracking: CGFloat }
enum RingFits {
    static let sizes: [CGFloat] = [8.5, 8, 7.5]; static let separator = " · "
    static func fit(name: String, circumference: CGFloat, locale: Locale,
                    measure: (_ text: String, _ size: CGFloat) -> CGFloat) -> RingFit
}
// Card/Logic/ValenceLockup.swift
enum LockupInk: Equatable { case stoneRamp, ink, primary }
enum LockupRank: Equatable { case small, medium, large }        // maps to stack B's SpineType lockup-word rank
struct ValenceLockup: Equatable { let word: LocalizedStringResource; let lowercase: Bool
    let rank: LockupRank; let span: LocalizedStringResource; let ink: LockupInk }
extension ValenceLockup { static func of(_ valence: Valence) -> ValenceLockup }
// The souls rule: stack D's function, ids as String in the logic layer
// PeopleLines.peopleLine(_ soulIds: [String], order: [String], locale: Locale, namedInFull: Int) -> PeopleLine
// called with namedInFull: PeopleLines.namedOnCard (= 8, defined in D)
// Card/Logic/PostmarkNoise.swift
enum PostmarkNoise { static let minDensity = 0.83; static let pitAlpha: UInt8 = 200
    static func alphas(size: Int, seed: UInt64) -> [UInt8] }      // SplitMix64, 8-px value noise, 3 % pits
// Card/Logic/TurnSettle.swift
enum TurnSettle { static let flick = 1.5; static let half = 0.5
    static func progress(drag: CGFloat, width: CGFloat, rightToLeft: Bool) -> Double
    static func target(progress: Double, velocity: Double) -> Double
    static func launchVelocity(target: Double, velocity: Double) -> Double }
// Card/Logic/TurnGeometry.swift
enum TurnGeometry { static let eyeDistance: CGFloat = 864
    static func angle(progress: Double, rightToLeft: Bool) -> Angle
    static func perspective(width: CGFloat, height: CGFloat) -> CGFloat
    static func fitScale(progress: Double, width: CGFloat) -> CGFloat }
// Card/Logic/CardFlight.swift
enum CardFlight {
    static func spineCardFlies(hasPhotoCard: Bool, hasPebble: Bool) -> Bool       // both
    static func heldCardFlies(showing: CardFace, printing: Bool, leaving: Bool, reduceMotion: Bool) -> Bool
    static func opensFlying(_ request: HeldCardRequest, sourceRect: CGRect?, reduceMotion: Bool) -> Bool }
// Card/Logic/FlightGeometry.swift
enum FlightGeometry { static func transform(from slot: CGRect, to rest: CGRect, progress: Double) -> (scale: CGFloat, offset: CGSize) }
```

**Valence → lockup** (word and span from `Valence.headline`; font by size group):

| Valence | Word | Case | Rank (`SpineType` lockup word) | Span | Ink |
|---|---|---|---|---|---|
| highlightSmall / Medium / Large | Highlight | lower / as authored / as authored | small / medium / large | OF MY DAY / WEEK / MONTH | `stoneRamp` |
| neutralSmall / Medium / Large | Moment | lower / as / as | small / medium / large | DAY / WEEK / MONTH | `primary` |
| lowlightSmall / Medium / Large | Lowlight | lower / as / as | small / medium / large | DAY / WEEK / MONTH | `ink` |

**Ring fit.** `unit = name.uppercased(with: locale) + " · "`. For each size in 8.5, 8, 7.5: `n = ⌊C / measure(unit, size)⌋`; while `n ≥ 2` and `measure(unit × n, size) > C`, `n −= 1`; if `n ≥ 2`, return `unit × n` with `tracking = (C − measure(text)) / text.count`. Else the name once with its dot at 7.5, tracking clamped at 0. On device `measure` is the sum of per-character advances at that fixed size, the same advances the draw loop steps by.

**Souls.** Up to 8 souls named in full in `order` (the Spine's persons-of-the-week order, `[]` from other callers, which keeps the pebble's order); past 8, the counting rule: two named, "& N others" in words, up to 5 marks, then "+N".

## The turn (E2)

- **Angle.** `(t > 0.5 ? t − 1 : t) × 180°` about Y, negative in LTR, mirrored in RTL; past the half the other face runs back from 90° to 0°, so text is never mirrored. `rotation3DEffect(angle, axis: (0, 1, 0), anchor: .center, perspective: TurnGeometry.perspective(...))` with `perspective = max(w, h) / 864`, which puts the eye 864 pt away, as Android's `cameraDistance 12 × 72`, **assuming** SwiftUI's projection is `m34 = −perspective / max(w, h)`. The panel is scaled by `fitScale = 1 / (1 + sin(πt) · (w/2) / 864)` so the near edge never outgrows the shell. E2's first task renders t = 0.25 and measures the near edge against the shell; if the assumption is wrong, both functions are re-derived from the measured edge before anything else is built.
- **Button.** `withAnimation(.spring(duration: 0.42, bounce: 0), completionCriteria: .logicallyComplete) { progress = 1 } completion: { land() }`. `land()` sets `face = face.other` and `progress = 0` in one `withTransaction(Transaction(animation: nil))`, so no frame shows the new face at 180°. One turn at a time: a press while a turn or a drag is busy is ignored.
- **Drag.** `.simultaneousGesture(DragGesture(minimumDistance: 12))` on the panel, measured in the panel's unrotated parent. Direction lock on the first change: horizontal when `|dx| > |dy|`, else the gesture is ignored to its end and the `ScrollView` keeps it. `progress = TurnSettle.progress(drag: dx, width:, rightToLeft:)` = `clamp(−dx / width, 0, 1)` (sign flipped in RTL). On end, `velocity = −value.velocity.width / width` turns/s (iOS 17's `DragGesture.Value.velocity`); `target`: `|v| ≥ 1.5` carries in its direction, else `progress ≥ 0.5` completes (both inclusive). Settle `.interpolatingSpring(duration: 0.35, bounce: 0, initialVelocity: launch / max(|target − progress|, 0.05))`, with `launch = TurnSettle.launchVelocity` (the finger's speed unless it points away, then 0, so a slow release never crosses the half twice). A cancelled drag (the scroll took it, the page left) reverts in `.spring(duration: 0.21, bounce: 0)`. A drag starting mid-turn is not taken. Width 0 turns nothing.
- **Fallback for the lock.** If the device check shows the `ScrollView` stealing horizontal drags or the drag blocking vertical scroll (iOS 18 and 26 changed gesture arbitration), the panel takes a `UIViewRepresentable` hosting a `UIPanGestureRecognizer` whose `gestureRecognizerShouldBegin` requires `|vx| > |vy|`, feeding the same `CardTurnModel`.
- **Edge swipe vs turn.** The close strip is the leading 20 pt of the screen and has priority; the card starts 16 pt in, so a turn drag is any drag starting on the panel outside that strip. They move in opposite directions in LTR.
- **Reduce Motion.** No rotation. A button turn crossfades the faces in 0.15 s (`.easeInOut(duration: 0.15)`), the outgoing face hidden from VoiceOver at once. A drag moves nothing; its release commits instantly if `target == 1`.
- **Stone press.** Stack D's stone press plays its dip and opens with `face: .verso` after its open delay (360 ms, `StoneMotion.openDelay`); card and entry presses open `.recto` at once. A bare moment ignores the face.
- **Restoration.** `landing` is written to `@SceneStorage("heldCard")` on every change of `landing` (D7).

## The bare moment and the print (E3)

States of the page, as values of `HeldCardModel` + `printingFor`:

```
bare ──(Complete flow saves; onCompleted → reload in place, behind the success screen)──▶
bare + detail.pebble != nil + completion != nil        (one assignment: the swap and the cue arrive together)
   ──(the cover is gone: printReady = completion != nil && !isPresentingComplete)──▶
printing(start: Date)  printingFor = moment id         (a `TimelineView(.animation)` drives t = elapsed / 1.6 s)
   ──(the frame where t ≥ 1)──▶  printingFor = nil, completionShown(), printedOver = true, announce
card at rest, verso up
restore with printingFor set ──▶ cutShort: card at rest, verso up, completionShown(), printingFor = nil
```

`CompletionMotion` (ported): 1.6 s; the card prints over 0–0.35 at the place's width, verso up; the dashed stone flies to the clearing over 0.25–0.6 and the lit stone presses in at 0.45 with stack D's ripple and glint; the lockup, postmarks, band and footer print in turn over 0.6–1; the plain page scales to 0.97 and steps aside as the card rises to rest. The place is scrolled into view first (`ScrollViewReader.scrollTo`, the place's id); a place taller than the viewport crossfades. Reduce Motion: a 0.25 s crossfade to the card at rest, verso up. A print never flies; Edit pebble during or after it must not replay it (`printingFor` is the guard).

## Open and close (E4)

### Registry and press (shared with stack D)

The registry is stack D's canonical block, not restated here: `CardFrameRegistry` (`@ObservationIgnored private(set) var frames` keyed by moment id, `hidden`), `CardFace: String, Codable`, and `SpineOpen` (moment id, face, summary, `soulOrder: [String]`). It is created in `RootView` and injected into the environment; each Spine slot reports through `onGeometryChange(for:of:action:)`, which already ships on the 17.0 target (`WeekRollView.swift:56`). The Spine's `onOpen(SpineOpen)` reaches `HeldCardHostModel.open(_:)` through `PathView`.

`HeldCardHostModel.open(_ request: HeldCardRequest)` reads `registry.frames[id]` when `CardFlight.opensFlying` holds; `close()` re-reads it.

### Flight

- **First frame.** `HeldCardPage` renders the recto from `request.seed` (a `MomentSummary`: name, date, first photo with its cached ratio, emotion, glyph, souls) before `MomentDetail` arrives, so the flight lands on a card, never a spinner. The leaf, the souls past three and the verso fill in when the detail lands. Without a seed (soul and collection callers): a blank card (aspect 1.3) for up to 400 ms, then a `ProgressView` in it.
- **Open.** Measure the resting rect (`onGeometryChange` on the card in the overlay) on the first layout pass with the card invisible, then animate `FlightGeometry.transform(from: slot, to: rest, progress:)` 0 → 1 with `.spring(duration: 0.45, bounce: 0)`: `scale = slot.width / rest.width` (fill width), anchored top-leading on the slot's origin. The overlay's ground and the top bar fade in over the last 60 %. `registry.hidden = id` for the flight.
- **Close.** `CardFlight.heldCardFlies(showing: turn.face, printing:, leaving:, reduceMotion:)` at the moment of closing, with the **committed** face: recto flies back to the re-read rect; verso, printing, leaving, a missing rect or Reduce Motion fade out in 0.2 s. `registry.hidden` clears on the last frame. Then `onChanged()` if anything was edited, `returnFocus = id`.
- **Never flies.** Stone presses (verso), bare moments (plain page fades), a print, a delete (`leaving` set before the close), any open or close under Reduce Motion (0.2 s crossfade).
- **Edge swipe.** A leading 20 pt strip (trailing in RTL) with a `DragGesture(minimumDistance: 0)`; `progress = dx / screenWidth` scrubs the close: the FLIP in reverse when the card would fly, else a trailing translation of the page with a fade. Release with the turn's rule: `|v| ≥ 1.5` screens/s carries, else `≥ 0.5` completes; cancel springs back in 0.21 s. The visible ← Back closes the same way.
- **VoiceOver.** The overlay root carries `.accessibilityAddTraits(.isModal)`; `PebblesTabView` beneath is `.accessibilityHidden(true)` while a card is held (the slot stack C §7 reserves); `@AccessibilityFocusState` moves focus to the title (recto) or "Your pebble" (verso) after the open; `.accessibilityAction(.escape) { close() }`; on close `returnFocus` drives stack D3's per-slot `@AccessibilityFocusState` on `SpineEntry`, so focus lands on the slot. Sheets from the page (edit, Complete, delete dialog) present over the window as today.
- **Status bar.** The overlay sets no style of its own; it lies on `Color.pebbles.surface` and follows the scheme.

### Spike (the first task of E4)

On the iPhone 15 (`project_ios_device_deploy` recipe), with the overlay and FLIP only:
1. Open and close 5 times each from a card at the top, the middle and the bottom of a week, and once after swiping to another week.
2. One edge swipe cancelled at about 30 %, one completed.
3. The same under VoiceOver: focus on the title after open, Z-scrub closes, focus returns to the slot; nothing under the overlay is reachable.
4. The same under Reduce Motion: crossfades only.

**Go** when: no frame shows both the slot and the flying card, or neither; the first frame is a card; the close lands on the slot within 1 pt; Instruments' hitch rate stays under 5 ms/s on the 1–3 runs; VoiceOver passes 3. **No-go fallback:** Option 4, a `UIViewControllerTransitioningDelegate` animator presenting the page's `UIHostingController` with `.custom` presentation, reusing `CardFlight` and `FlightGeometry`, with every environment service re-injected. **If both fail:** a fade with a 1.04 → 1 lift, Android's recorded fallback. The result is written into the plan's Lessons learned.

### Decision-log entry (lands with E4)

> **The iOS held card opens from its slot as an overlay with a measured-frame FLIP (#1160, #1146).** On an iOS 17 floor the card is a full-screen layer in `RootView` above the tabs, not a presentation; it animates `scaleEffect` + `offset` from the Spine slot's measured global rect (a `[momentId: CGRect]` registry) to its resting rect, and back on close while the committed face is the recto. Stone presses, bare moments, printing, delete and Reduce Motion do not fly. Rejected: zoom on 18 + push on 17 (a guard where an iOS 17 equivalent exists, #727; zoom cannot close by face), `matchedGeometryEffect` (remeasures both faces each frame, untrusted across the week pager), raising the floor to 18 (no adoption data; zoom still cannot close by face), a UIKit animator (environment re-injection; kept as the spike's fallback). The detail leaves the sheet. The device spike (iPhone 15: five opens and closes from the top, middle and bottom, cancelled and completed edge swipes, VoiceOver, Reduce Motion) came out go.

E4 prefixes the heading with its merge date. If the spike comes out no-go, E4 replaces the last sentence with "The spike came out no-go; the flight is a UIKit transitioning-delegate animator sharing the same predicates and geometry" (or, if that fails too, "…a fade with a 1.04 → 1 lift"), and moves the UIKit animator out of the rejected list.

## Accessibility

- Dynamic Type: the legend, leaf, lockup span, footer and bare page scale through `SpineType`'s `Scaling.card(cap: 1.3)`; the footer stacks at large sizes; the page scrolls. The ring text and the miniature do not scale (decoration; the names are in the list).
- Contrast AA on paper and Night: postmark ink at its faintest density, the band's italic, the footer's italic. Measured on the device in E1.
- Targets: the turn button and every top bar control ≥ 44 × 44 pt.
- Reduce Motion: §Turn, §Print, §Flight. The stone's tilt stays off on the card (`followsTilt: false`).
- VoiceOver: §Reading order, §Flight. Exactly one element per stamp row ("Life domains: Friends, Community"), one per band.

## Testing per part

Swift Testing (`@Suite`, `@Test`, `#expect`) in `apps/ios/PebblesTests/Card/`. Reference images through stack B2's harness (#1159), each in paper and Night with one French row: **each face of a photo card (F1), a public card (F2, Share showing), a card without photo (F3), the bare page (F4), a stress verso with four long domains and two long collections, the menu open.** Fixtures in `PebblesTests/Card/HeldCardFixtures.swift`; no `ScrollView` inside the renderer.

- **E0a.** `CompletePebblePayloadTests` (#1099): untouched edit omits `domain_ids` and `collection_ids`; a changed domain sends `[new]`; a cleared collection sends `[]`; a first completion sends both; encoded JSON checked as text. `CompleteDraftTests`: `prefill` set only by `init(from:)`, ignored by `isEmpty`. Device: edit the emotion of a pre-split pebble with three domains and two collections; all survive.
- **E0b.** `PebbleCollectionDecodingTests`: with and without `glyphs`, `glyph_id` null. `ReferenceDataServiceTests` (if the select is asserted there): the embed is requested.
- **E1.** `RingFitTests`: a short name repeats and closes within 0.5 pt; a long name steps 8.5 → 8 → 7.5 before falling under 2; set once at 7.5; tracking never negative; a superadditive measure steps repetitions down; capitals follow the locale (Turkish i). `ValenceLockupTests`: all 9 valences. `PeopleLineCardTests`: 8 named; 9 falls back to two named + counted. `PostmarkNoiseTests`: same seed same bytes, different seeds differ, mean alpha restrained (≤ 17 % drift, ~3 % pits). `VisibilityExplanationTests`. Reference images: F1–F3 recto (verso via the button), stress verso, menu open. Device: postmark contrast and pits (D4), ring legibility at 1×, menu Delete orange.
- **E2.** `TurnSettleTests`: past the half completes; short of it reverts; a flick carries; thresholds inclusive; a slow release never starts away; keeps the finger's speed; progress clamped; zero width; RTL sign. `TurnGeometryTests`: angle at 0, 0.49, 0.51, 1; fitScale 1 at 0 and 1, minimum at 0.5. `CardTurnModelTests`: the button turns and lands with progress 0; a turn during a turn is ignored; a drag mid-turn is not taken; landing while held and while settling; Reduce Motion commits on release ≥ 0.5; restore reopens on the saved face. `CardFaceRequestTests`: a stone press asks verso, a card or entry press recto, a bare moment the plain page. Reference images: mid-turn at 0.3 and 0.7. Device: drag vs vertical scroll on iOS 17 and the current iOS, flick feel, VoiceOver turn button last.
- **E3.** `HeldCardModelTests`: a reload finding a pebble on a bare moment publishes `detail` and `completion` together; `completion` holds through a later reload until shown; clears when the pebble disappears. `CompletionMotionTests`: phases 0–0.35, 0.25–0.6, 0.6–1; the stone is in at 0.45; parts print in order. `PrintRestoreTests`: `printingFor` set on restore → cut short at rest, verso up; ending clears it; nothing else clears it. Reference images: bare page (with and without photo, French), the print at 0.2, 0.5 and 0.9. Device: the print after a real completion; Edit pebble right after the print does not replay it.
- **E4.** `CardFlightTests`: the Spine card flies only with a photo card and a pebble; the held card flies on recto, fades on verso; printing stays; leaving fades; Reduce Motion fades; no rect fades. `FlightGeometryTests`: progress 0 is the slot, 1 the rest, fill-width scale. Device: the spike protocol, then the full checklist below.
- **E5.** `HeldCardModelTests`: save, delete and completion each bump `MomentsRevision` once. The suite stays green with `BannerAspectTests` and `PebblePageColorsTests` removed with their types. Device: soul and collection rows open the card without a flight and close back to their list.

**Device checklist (iPhone 15, before E4 and E5 merge):** postmark contrast and ring legibility at 1×; the turn drag against vertical scroll; the edge swipe cancelled and completed; VoiceOver order on both faces and the bare page; Reduce Motion on turn, print and flight; Night; French; largest accessibility type size.

## Parts

| Part | Branch / PR | Done when |
|---|---|---|
| **E0a — Edit pebble keeps its sets** | `fix/1099-ios-edit-pebble-keeps-domains` | D8: `CompleteDraft.prefill` and the absent-key payload; E0a tests green; resolves #1099 for iOS (Android stays open on it). Arkaik `AC-hold-a-pebble-like-a-card-and-turn-it-over` and `V-pebble-detail` move to `development` for iOS at this pickup. |
| **E0b — collection glyphs in reference data** | `feat/1145-ios-collection-glyph-band-data` | D9's data half: the collections select embeds `glyphs`, `PebbleCollection.glyph`, the `GlyphThumbnail` / `square.stack.3d.up` fallback helper the band consumes in E1; E0b tests green. |
| **E1 — the card, still** | `feat/1146-ios-held-card` | `HeldCardPage` replaces `PebbleReadView` for pebbles inside the `PebbleDetailSheet` wrapper, from all three callers; both faces, footer, postmarks (D4 decided on device), band with E0b's glyphs, turn by the button with no animation; the ⋮ menu per A2 with orange Delete; bare moments still on the old read view; E1 tests and reference images green. |
| **E2 — the turn** | `feat/1146-ios-card-turn` | Rotation, drag, flick, settle, Reduce Motion crossfade, `@SceneStorage` face; stone presses open the verso; E2 tests green; drag vs scroll checked on device. |
| **E3 — the bare moment** | `feat/1146-ios-bare-moment` | Bare moments open `BareMomentPage`; completion prints into the place as state; restore cuts a print short; E3 tests green. |
| **E4 — open and close** | `feat/1160-ios-card-flight` | Spike recorded go (or the fallback built); Path opens the card in `HeldCardHost` with the FLIP; close by committed face; edge swipe; VoiceOver modality and focus return; the decision-log entry lands; E4 tests green; device checklist passed. |
| **E5 — one overlay, the read view gone** | `feat/1146-ios-remove-read-view` | `SoulDetailView` and `CollectionDetailView` call `HeldCardHostModel.open` (no seed, no flight); `HeldCardModel` bumps `MomentsRevision` on save, delete and completion, and the lists reload from it; `PebbleDetailSheet.swift` and the whole of `Features/Path/Read/` deleted (`PebbleReadView`, `PebbleReadBanner`, `PebbleReadTitle`, `PebbleReadPetroglyph`, `PebbleSnapFrame`, `PebblePrivacyBadge`, `PebbleMetaPill`, `PebblePillFlow`, `PebblePageColors`, `BannerAspect`: none has a caller outside the folder and the sheet) with their tests (`BannerAspectTests`, `PebblePageColorsTests`), and `GlyphThumbnail`'s doc comment loses its `PebbleMetaPill` line; `apps/ios/CLAUDE.md`'s "sheets and full-screen covers do most secondary navigation" gains the held-card host; lessons written into the plan; resolves #1146, #1160, #1145. Arkaik moves to `releasing` through the App at merge, never `live`. |

Each part lints (`npm run lint --workspace=@pbbls/ios`) and tests (`npm run test --workspace=@pbbls/ios`) alone. Labels `feat`, `ios`, `ui` (E0a `fix`), milestone M64, `no-lab-note` on every part (Arkaik coverage replaces the Lab Note). Stack E sits on stack D; E4 also needs stack C's `PebblesTabView` in `RootView`.

## Risks

- **SwiftUI's perspective model** may not be `m34 = −p / max(w, h)`; E2's first render measures it before the rest is tuned.
- **Gesture arbitration** between the drag, the `ScrollView` and the edge strip differs across iOS 17, 18 and 26; the `UIPanGestureRecognizer` fallback is specified.
- **`destinationOut` inside a rotating, composited panel** may flatten wrong on device; the speckle fallback is specified.
- **The hand-built close** may feel "not quite system"; the spike judges it, and Option 4 is the fallback.
- **The registry's rect may be stale** after a week swipe under the overlay or a Spine reload; the close re-reads it and fades when missing.
- **Stack D slips:** E0a and E0b need nothing from it; E1–E3 need `SpineCard` and `PeopleLines`; E4 waits for the registry and D3's focus state.
- **The sheet in E1–E3** is a `.large` detent, not the full height; acceptable for three parts, gone in E4/E5.

## New patterns, raised in the PR

Each is new to `apps/ios` and is named in its part's PR body for discussion (root `CLAUDE.md`: new patterns require discussion first).

- **`HeldCardHost`, an overlay above `PebblesTabView` in `RootView`** instead of a sheet or a push (E4): a full-screen layer with its own modality for VoiceOver, edge swipe and focus return.
- **`@SceneStorage` restore of the held card's face and print** (`heldCard`, E2/E3), cleared by the sign-out reset.
- **Dotted xcstrings keys through `LocalizedStringResource(_:defaultValue:)`** (`card.*`, `bare.place.*`, E1) where the catalog's convention is the English literal as key.
- **The Canvas `destinationOut` postmark** (E1): a real alpha cut inside `drawLayer` + `drawingGroup`, with the speckle fallback if the device check fails.
