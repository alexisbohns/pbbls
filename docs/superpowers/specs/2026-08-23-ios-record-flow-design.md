# iOS step-by-step record flow — design (M58)

Design doc for a new iOS pebble composer: a full-screen, one-action-per-step
flow that replaces the all-at-once sheet as the default way to record a pebble.
Milestone **M58 · Dynamic and picture-first Path** — the flow opens on a photo,
which is the "picture-first" half of that milestone applied to capture rather
than display.

Recording a pebble on iOS today is `CreatePebbleSheet` (275 LOC) wrapping
`PebbleFormView` (361 LOC): every field visible at once in a scrolling `List`,
with four modal sub-pickers stacked on top of it and a `VisibilityChip` in the
bottom bar. It works, and it asks the user to hold ten decisions in their head
simultaneously. The flow asks one question per screen instead, and uses the
sequencing to do things a form cannot — seed the date from the photo's EXIF
before asking for it, order emotion categories by the valence just chosen,
and end on the composed render being drawn on.

**iOS only.** No schema, RPC, or payload change: `v_domains_with_glyph` already
exists and `PebbleCreatePayload` is untouched. Nothing crosses a surface
boundary, so Android and web are unaffected. Mirror it later only if it sticks.

## Shipped pieces

| Piece | Path |
|---|---|
| Flow container + chrome | `Features/Record/RecordFlowView.swift`, `RecordFlowChrome.swift`, `RecordStepScaffold.swift` |
| Flow state machine | `Features/Record/RecordFlowModel.swift`, `RecordStep.swift` |
| Step views (11) | `Features/Record/Steps/RecordPhotoStep.swift` … `RecordSuccessStep.swift` |
| Extracted picker contents | `Features/Path/ValencePickerContent.swift`, `EmotionPickerContent.swift`, `SoulPickerContent.swift`; `Features/Glyph/Views/GlyphPickerContent.swift` |
| New domain picker | `Features/Path/DomainPickerContent.swift` |
| Extracted publish path | `Features/Path/PebblePublisher.swift` |
| Extracted draft glue | `Features/Path/ComposerDraftCoordinator.swift` |
| Tap haptics | `Services/TapHaptics.swift` |
| EXIF capture date | `Features/PebbleMedia/ExifCaptureDate.swift` |
| Domain glyph + description | `Services/ReferenceDataService.swift`, `Features/Path/Models/Domain.swift`, `Domain+Localized.swift`, `Resources/Localizable.xcstrings` |

## D1 — The flow is the default composer; the sheet survives behind a long-press

The `+` button on `PathView` opens the flow. `CreatePebbleSheet` stays in the
tree and is reachable by **long-pressing** the same `+`.

Two composers is a cost, accepted deliberately and temporarily: this is an
experiment in interaction model, and the honest way to evaluate it is to be
able to fall back on device without a rebuild. Long-press was chosen over a
Settings toggle because it adds no chrome, no persisted state, and no
localized string — it is one `.simultaneousGesture` that deletes in one line
when the experiment resolves.

`EditPebbleSheet` is **not** in scope. Editing an existing pebble keeps
`PebbleFormView`: a form is the right shape for changing one field of ten, and
a wizard is the wrong one. This also means `PebbleFormView` keeps at least one
consumer no matter how the experiment resolves.

## D2 — Eleven steps, ten of them counted

```
0 photo · 1 when · 2 name · 3 valence · 4 emotion · 5 domain
6 souls · 7 collection · 8 glyph · 9 privacy        → 10 success
```

Steps 0–9 are the dots. `success` is terminal: it has no dot, no back, and no
close — only the exit button.

The order is not arbitrary. Three sequencing dependencies pay for themselves:

- **Photo before when.** The photo carries `DateTimeOriginal`, so by the time
  the date step renders it is already correct for the overwhelmingly common
  case (recording a moment from a picture taken earlier that day). A form
  cannot do this without the date field mutating under the user's cursor.
- **Valence before emotion.** `EmotionCategoryOrdering.order(for:)` already
  reorders emotion categories by valence and is already wired into
  `EmotionPickerSheet`. In the form the two rows sit side by side and the
  ordering is a lucky accident of which the user opens first; in the flow it
  is guaranteed.
- **Privacy last, next to publish.** The grade is the decision most coupled to
  "am I ready for other people to see this", so it belongs against the publish
  button, not in a toolbar chip eight fields away.

| # | Step | Renders | Advances on |
|---|---|---|---|
| 0 | Photo | Dashed tile → `PhotoPickerView`; thumbnail + upload state once picked | `Skip` / `Done` |
| 1 | When | Graphical `DatePicker` (`.date` + `.hourAndMinute`), EXIF-seeded | `Continue` |
| 2 | Name | Autofocused `TextField`, 40-char clamp + live counter | `Continue` |
| 3 | Valence | `ValencePickerContent` — 3 groups × 3 | tap |
| 4 | Emotion | `EmotionPickerContent`, categories ordered by step 3 | tap |
| 5 | Domain | `DomainPickerContent` — glyph, name, description | tap |
| 6 | Souls | `SoulPickerContent`, multi-select + create tile | `Skip` / `Done` |
| 7 | Collection | Collection list from `refs.collections` | tap, or `Skip` |
| 8 | Glyph | `GlyphPickerContent` grid + tabs | tap, or `Skip` |
| 9 | Privacy | Three grade tiles + snap state + error slot | `Publish` |
| 10 | Success | `PebbleReadPetroglyph` draw-on, `+N karma` | `Back to my path` |

Every step renders through `RecordStepScaffold`, which owns the shared
geometry: title, centered content slot, and the optional text-button slot
below it. Steps supply content and a button role, never their own layout.

`RecordStep` is a `CaseIterable` enum carrying `isOptional` and `dotIndex`.
No step is conditionally hidden, so back is index−1 and needs no history stack.

## D3 — Picking is the advance, except where there is nothing to pick

Tile steps (valence, emotion, domain, collection, glyph) commit and advance on
tap. That is the literal reading of "one action per step" and it is what makes
the flow feel fast.

Three steps have no tile to tap and therefore carry a primary button:

| Step | Why it needs a button | Button |
|---|---|---|
| `when` | Pre-filled from EXIF or now, so there is no tap that means "yes, that date" | `Continue` |
| `name` | Free text; the commit moment is not a tap | `Continue`, disabled while blank |
| `privacy` | Tap selects a grade but must not publish; the two actions are genuinely distinct | `Publish` |

Optional steps (photo, souls, collection, glyph) carry a text button below the
content that reads `Skip` while empty and `Done` once something is chosen.
Souls stays multi-select and uses that same button to advance — dropping
multi-soul tagging to make every step uniform would be a real capability loss
against the current composer.

Two clarifications the rule leaves open:

- **Single-select optional steps** (collection, glyph) still advance on tap, so
  `Done` is only ever seen after returning to the step via back with something
  already chosen. The button is not the primary path there; it is the way out
  of a step you re-entered and did not want to change.
- **The photo step does not auto-advance on pick.** The upload runs in the
  background and its state (uploading / failed / uploaded) belongs on screen
  while the user is still looking at the photo, so picking swaps `Skip` for
  `Done` and waits.

The 40-character name limit is front-end only — a `.onChange` clamp on the
binding. Neither `pebbles.name` nor `PebbleCreatePayload` constrains length,
and nothing server-side is added to enforce it.

Forward is gated on the step being answered; back is always available and
preserves answers.

## D4 — `RecordFlowModel` owns every interaction, which is what makes haptics structural

An `@Observable @MainActor` model holds the draft, the current step, and every
mutation: `select`, `advance`, `back`, `skip`, `publish`.

The requirement is a haptic on *every* tap. Implemented as a discipline —
"remember to call `TapHaptics.play` in each action closure" — that is one
forgotten closure away from being false, and untestable besides. Routing every
interaction through the model makes it structural: a tap that does not go
through a model method does not change anything, and the model fires the
haptic. The haptic is injected as a `(TapHaptic) -> Void` closure defaulting to
`TapHaptics.play`, so tests substitute a recorder and assert the mapping
without touching CoreHaptics.

`TapHaptics` is a small enum over the UIKit generators with four flavors:

| Flavor | Generator | Fires on |
|---|---|---|
| `selection` | `UISelectionFeedbackGenerator` | picking a tile, toggling a soul |
| `advance` | `UIImpactFeedbackGenerator(.light)` | step change, forward or back |
| `success` | `UINotificationFeedbackGenerator(.success)` | publish succeeded |
| `warning` | `UINotificationFeedbackGenerator(.warning)` | blocked advance, publish failed |

UIKit generators over the existing CoreHaptics `HapticsService`: the system
generators respect the user's haptic settings, need no warm engine on every
step, and are the right texture for UI taps. `HapticsService` stays what it is
— bespoke waveform-derived patterns for karma and the glyph slider.

The model also makes the interesting behavior testable without rendering:
gating, back-preserves-answers, skip on optional steps only, first-gap resume,
and the 40-character clamp.

## D5 — Picker sheets give up their bodies; the sheets keep their toolbars

Each of `ValencePickerSheet`, `EmotionPickerSheet`, `SoulPickerSheet` and
`GlyphPickerSheet` has its body lifted into a `…PickerContent` view taking a
selection and an `onSelect` closure. The sheet renders that content inside its
existing `NavigationStack` + toolbar; the flow step renders the same content
inline under its own chrome.

The content views are **presentation only** — no staging, no dismissal, no
fetching. That is what lets the two callers differ where they should:
`EmotionPickerSheet` keeps its Cancel/Done staging (a sheet that can be
cancelled needs it); the flow step commits on tap and advances (a step that
advances cannot be cancelled). Same grid, different commit semantics, one
implementation of the grouping and ordering logic.

Two carve-outs:

- **Souls fetching.** `SoulPickerSheet` fetches its own souls because the
  form's cached list can go stale behind it. The flow step reads
  `refs.souls` instead — already cached, already refreshed after Profile
  mutations — and appends an inline-created soul locally. Fetching stays in
  the sheet; the content view takes a list.
- **Glyph carve and buy.** `GlyphPickerSheet`'s grid and tab bar extract;
  `GlyphCarveSheet` and `GlyphDetailDrawer` stay presented sheets from the
  step. Carving is a full modal task with its own canvas — flattening it into
  a step in this flow would be a second wizard nested inside the first.

## D6 — The domain step needs data iOS does not currently fetch

The web `DomainSheet` renders each domain as glyph + name + description. iOS
`Domain` is `id/slug/name/label` with no glyph, and `ReferenceDataService`
selects from the `domains` table.

Two additions, both to existing infrastructure rather than new:

1. **Glyph.** The query moves to the existing `v_domains_with_glyph` view
   (`security_invoker = true`, already granted to `authenticated`, already
   used by admin). It returns `strokes` and `view_box` as flat columns, so
   `Domain` gains `var strokes: [GlyphStroke]?` and `var viewBox: String?`,
   both defaulted to `nil` — the memberwise initializer stays source
   compatible for the existing tests, and a missing key decodes to `nil`.
   `PebbleFormView`'s menu picker ignores both and is untouched.
2. **Description.** The `label` column is the description, and it is English
   only. Web already carries `domain.<slug>.label` for all 18 domains in EN and
   FR; iOS carries only `domain.<slug>.name`. `Domain.localizedLabel` follows
   the established Pattern C fallback (`NSLocalizedString(key, value: label)`),
   and 36 entries are appended to `Localizable.xcstrings`.

`Localizable.xcstrings` is a formatting-sensitive catalog: entries are inserted
as text at anchors, never re-serialized wholesale. `LocalizationTests` gains a
`domain.<slug>.label` coverage assertion mirroring the existing `.name` one,
so a future domain added server-side fails the build in both dimensions.

## D7 — EXIF is read before the pipeline strips it

`ImagePipeline.process` deliberately produces metadata-free JPEGs, and
`kCGImageSourceCreateThumbnailWithTransform` bakes orientation. By the time
bytes reach Storage the capture date is gone, which is correct for what we
upload and useless for what we want to ask.

`ExifCaptureDate.from(_ data: Data) -> Date?` is a pure function reading
`kCGImagePropertyExifDictionary` → `DateTimeOriginal` from the loaded data,
called in `SnapUploadCoordinator.handlePicked` *before* `ImagePipeline.process`
and exposed as `pickedCaptureDate`. The flow reads it when advancing to the
`when` step and seeds `draft.happenedAt`, showing a quiet "from your photo"
note; absent EXIF, a skipped photo, or an unparseable timestamp all fall back
to now.

Read from the picked bytes rather than `PHAsset.creationDate`: the latter
needs a photo-library authorization the app does not currently request, to
learn something already present in the data we have loaded anyway.

## D8 — Publish and draft glue are extracted, not duplicated

The riskiest logic in `CreatePebbleSheet` is not the form — it is the ~120 LOC
around it:

- **Publish.** `compose-pebble` invoke, the soft-success path that mines a
  `pebble_id` out of a 5xx body, the compensating snap delete on failure, and
  `userMessageForPebbleSaveError`'s mapping of quota and pipeline errors.
- **Drafts.** `CreatePebbleSheet+Drafts.swift`: hydrate-or-offer-restore gated
  on `refs.hasLoaded` (#647), `verifyGlyph`'s `can_use_glyph` check (D7 of the
  M47 design), `saveAsDraft`, and `consumeDraftAfterPublish` running on
  soft-success so a kept draft cannot duplicate a published pebble.

Every one of those is a bug already found and fixed once. Copying them into a
second composer is how they drift apart, and the failure mode is silent — the
flow would keep working while quietly losing the M47 fixes.

Two extractions, both used by the sheet and the flow:

- `PebblePublisher` — a struct over the Supabase client:
  `publish(draft:formSnap:userId:) async throws -> ComposePebbleResponse`,
  soft-success folded in, error mapping alongside.
- `ComposerDraftCoordinator` — an `@Observable` owning `serverDraftId`,
  `ComposerAutosave`, the restorable snapshot, and the hydrate/restore/
  save/consume/verify methods that currently live as computed properties and
  methods on the view.

`CreatePebbleSheet` becomes a consumer of both. Its behavior does not change;
the M47 tests (`PebbleDraftPayloadTests`, `DraftCrossSurfaceDecodingTests`,
`ComposerSnapshotStoreTests`, `PebbleCreatePayloadEncodingTests`) are the
regression net and must stay green untouched.

## D9 — Drafts keep both halves, and `✕` is where the deliberate one lives

The minimal chrome has no bottom bar, so "Save as draft" has nowhere to sit
permanently — and it does not need to. Tapping `✕` raises a confirmation:
**Save as draft / Discard / Keep going**. When the draft is not savable
(`isSavableAsDraft == false`, i.e. nothing entered), `✕` closes immediately
without asking.

This is a better location than the toolbar it replaces: the moment a user
wants to keep a half-finished pebble is precisely the moment they try to
leave, and putting the choice there converts an accidental discard into a
deliberate one.

Local autosave is unchanged and invisible — `ComposerAutosave` schedules on
payload change, flushes on scene-phase change, and the restore prompt fires on
entry exactly as in the sheet. Media is still excluded from the snapshot (M47
D3).

Resuming a server draft from `DraftsListSheet` enters the flow at the **first
unanswered step**, with prior answers pre-filled and reachable via back. The
alternative — replaying from step 0 — would make a user tap through six
answers they already gave. `firstGap()` is a pure function of the hydrated
draft and is unit tested.

## D10 — The success screen draws the pebble on

Step 10 renders `PebbleReadPetroglyph(renderSvg:renderVersion:valence:palette:)`
— the same component the read view uses, which composites the outline backdrop
and traces the composed render on with the native draw-on animation, honoring
Reduce Motion. Beneath it: the pebble's name, `+N karma` from
`ComposePebbleResponse.karmaDelta`, and a single `Back to my path` button.

On soft success (`render_svg` nil), the screen degrades to name + karma with no
artwork rather than blocking — the pebble exists and the user should be told so.

A **hard** failure never reaches step 10. The flow stays on the privacy step,
fires the `warning` haptic, and renders the mapped message from
`PebblePublisher` in the step's error slot — including the two snap-blocked
cases the sheet already guards (`Photo is still uploading.`,
`Photo upload failed. Retry or remove it.`), which is why the privacy step
carries the snap state as well as the grade tiles. The draft is untouched, so
`✕` → Save as draft remains a way out of a failing publish.

The karma pastille is **suppressed** on this path: the count is already on
screen and a capsule over it is redundant. The sound and haptic are not
suppressed — they are the celebration. `KarmaNotificationService.notifyEarned`
gains `presentsCapsule: Bool = true`, and the flow passes `false`.
`achievements.fireCheck()` still runs, so an achievement moment can present
over the success screen as it does today.

Exiting dismisses the cover and returns to `PathView`, which reloads the
timeline and stats and sets `focusedWeekStart` to the new pebble's ISO week —
the flow's reading of "the new pebble is where you land". It does **not**
auto-open `PebbleDetailSheet` the way the sheet does today: the user has just
spent ten screens on this pebble and the success screen already showed it.

## Testing

Swift Testing, in `PebblesTests/Features/Record/`:

| Suite | Covers |
|---|---|
| `RecordStepTests` | step order, `isOptional` set, dot count is 10, `success` is uncounted |
| `RecordFlowModelTests` | forward gating per step, back preserves answers, skip only on optional steps, privacy does not auto-advance, name clamped to 40, `firstGap()` from a hydrated draft, haptic flavor per interaction via an injected recorder |
| `ExifCaptureDateTests` | `DateTimeOriginal` extracted from a fixture; nil for absent, malformed, and non-image data |
| `DomainWithGlyphDecodingTests` | `v_domains_with_glyph` row decodes; missing `strokes`/`view_box` decode to nil |
| `LocalizationTests` (extended) | every `ReferenceSlugs.domains` slug has a `domain.<slug>.label`; `localizedLabel` falls back to `label` |

Existing M47 and payload suites are the regression net for D8 and stay green
without modification.

Verification: `npm run build/test/lint --workspace=@pbbls/ios`, clearing
`DerivedData/Pebbles-*/Build` first — xcodegen serves stale objects otherwise
and a green build means nothing until it is cleared.

## Landing

One `[Feat]` issue on **M58 · Dynamic and picture-first Path**, labeled
`feat`, `ui`, `ios`. The flow adds a user-visible screen, so
`docs/arkaik/bundle.json` gains a view node for it (and an edge from the Path)
with the matching journal event, in the same change. User-facing → the PR
carries a bilingual Lab Note.

## Out of scope

- **Android and web.** No contract change, so no mirror obligation. Revisit
  once the experiment resolves.
- **`EditPebbleSheet`.** Keeps `PebbleFormView` (D1).
- **Description.** The flow captures name only; `description` stays in the
  model, the payload, and the edit sheet. Adding it would mean either two
  actions on the name step or a twelfth step for a field most pebbles leave
  empty.
- **Camera capture.** Step 0 opens the library picker only, as the sheet does
  today.
- **Inline collection creation.** The collection step lists existing
  collections and skips; `CreateCollectionSheet` stays in Profile.
