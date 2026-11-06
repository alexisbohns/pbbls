# Record flow redesign — design

**Date:** 2026-11-04
**Issues:** #1514 iOS (reference), #1515 Android (mirror), #1516 web. No back-end issue: every contract this flow needs already exists.
**Milestone:** M70 · Record flow
**Design:** the 2026-11-04 exploration. Synthesis (diagnosis, six proposals, the merge): https://claude.ai/artifact/E9GLg6LXthGf9gMdpid1wy. Visual variants gallery with the maintainer's picks: https://claude.ai/artifact/1jXYWFkevwjcyhLj48wVJS. Both private.
**Inventory:** counted on `main` at `b35488b18` (2026-11-04). iOS Capture is 3 counted steps (`Features/Capture/CaptureStep.swift`), Complete 6 plus success (`Features/Complete/CompleteStep.swift`). A moment costs 5 taps, a full pebble 12 taps over 10 screens.

## Summary

Recording becomes two short stages with a real page between them. Capture is the photo, then the moment page: name, when, a few words, and two buttons, "Just save" and "Save & make it a pebble". The pebble stage asks what `complete_pebble` requires in three screens, valence, emotion and Life (domains plus people), and ends on "Make it richer", where the stone is lit, karma is paid, and collection, visibility and words are one tap each. A moment is 3 taps, a full pebble 8 taps over 5 screens. The valence stones and the emotion picker keep their current controls; what changes around them is the help, the labels and the defaults. iOS is the reference, Android mirrors it 1:1 with its own chrome and picker, web follows on its existing routes.

## Goal

After the three client issues ship:

- from Path "+" or Home, a photo moment is saved in 3 taps, a full pebble in 8, and nothing on the way asks a question the server does not need;
- the description is written on the moment page, in the flow, and "Add a few words" is offered again on the end screen and on the bare moment page;
- the valence step never preselects, carries axis labels, opens a help sheet on the account's first pebble and a "(i)" callout afterwards;
- a pebble row still exists only when complete, written by `save_moment` then `complete_pebble`, with a defined partial-failure state;
- the distribution across the nine valence cells is measured against today's neutral-week skew after launch.

## Non-goals

- Any schema or RPC change. `save_moment` already takes `description`; `complete_pebble` already takes `domain_ids[]`. Suggested domains by soul (an RPC) and domain groups (a column) were proposed and are deferred.
- A new valence control. The fan and the roll stay (maintainer's pick, 2026-11-04). A 3×3 matrix is drawn in the gallery if the first-run data says the roll is still unread.
- A new emotion picker. The 38-chip grid stays; it gains a Retry state.
- Multi-photo (its own launch item), camera inside the iOS picker (the system picker has none), edit flows (Edit moment and Edit pebble keep their sheets and their one-write rule).
- The ceremony and achievements. They fire as today on the first `complete_pebble` insert.

## Decisions

**D1. The flow.** Photo → moment page → [valence → emotion → Life] → Make it richer. Capture counts two steps, the pebble stage three; the dots show the stage's own count, never a combined one. The pebble stage opens on a one-line header the first time it follows a save: "Moment saved · 3 questions to make it a pebble".

**D2. The moment page is an unsaved preview** (assumption to confirm, see Open questions). Nothing is written until "Just save" or "Save & make it a pebble". The ✕ dialog "Keep this moment?" stays. Consequence: iOS Capture gains draft persistence (name, when, words, the pending snap reference) so an app kill no longer loses the page; Android already has it through `SavedStateHandle`. *Rejected for now:* writing the moment on landing (Opus B), which needs a Delete affordance and creates moments people meant to abandon.

**D3. Photo: the system grid, the Instagram model.** On iOS the inline `PhotosPicker` keeps running out of process and re-enables `collectionNavigation` with the top accessory visible, so the user gets Apple's "Recents ▾ / Albums" switcher inside our screen, with no photo-library prompt. Above the grid sit two controls in the scaffold's fixed slot: **Just write** (primary, tonal) and a Camera button. A pick starts the upload and advances after a 0.4 s thumbnail beat; the upload continues as a ring on the moment page's thumbnail; tapping the thumbnail returns to the grid, with "Remove". Save still waits for the upload. On Android the system Photo Picker stays a modal launched from the same two controls plus a Library tile, and the return auto-advances; an inline MediaStore grid would need `READ_MEDIA_IMAGES`, which Play's photo-permission policy reserves for apps whose core feature is broad library access, a rejection risk one month before launch. This is a recorded 1:1 exception: the model mirrors, the grid does not.

**D4. The When step goes.** A chip under the name reads "Today, 21:10 ▾" or "From your photo · Sun 3 Nov ▾" and opens the graphical calendar in a sheet. The EXIF seed and its label stay; a user edit always wins.

**D5. The moment page (Ledger treatment).** Thumbnail with upload state and "Change photo", NAME field (Caveat, focused only when there is no photo), WHEN chip, A FEW WORDS (3 lines, counter, caption "+1 karma as a pebble", which `compute_karma_delta` makes true). Primary "Save & make it a pebble", text button "Just save". From Home "New pebble" the primary is the same; the Home tile copy decides whether the two entries read "Record" or "New moment" (copy pass, D15).

**D6. Save paths.** "Just save" calls `save_moment` and returns to the origin (Path week scrolled to the new stone, or Home) with a snackbar "Moment saved · Make it a pebble". "Save & make it a pebble" calls `save_moment`, then opens the pebble stage on the saved moment, so the existing per-moment autosave and restore keep working. The final "Make it a pebble" calls `complete_pebble`. If that second write fails, the moment stays saved and the Life screen shows, in orange, "Moment saved, pebble not made yet · Try again". One write per button, as the one-write rule requires.

**D7. Valence keeps its control and loses its defaults.** No preselection: Continue is disabled until a pick. The fan gets axis labels in the margins (columns Lowlight · Neutral · Highlight, rows Month · Week · Day) and a ghost stone with "Swipe or tap to choose" until the first touch. The neutral word "Moment" becomes **"Neutral"** (FR "Neutre"), which ends the collision with the new meaning of "moment" (confirmed 2026-11-05; "In-between / Entre-deux" was the first draft). The lockup becomes one sentence, "A highlight of my week", built from two localised strings, never concatenated, so French agreement holds.

**D8. The help sheet and the callout.** On the first valence step of an account with zero completed pebbles (the count Home already loads), a medium sheet opens over the step: an annotated fan with the lit stone and four lines: "Two questions, one stone." / "Left to right: lowlight, in-between, highlight." / "Bottom to top: did it fill your day, your week, your month? Bigger moment, bigger stone." / "No wrong stone. You can change it later.", then "Got it". A local seen-flag stops it reopening after a back-out. Afterwards a caption-size "(i) How to read this" sits under the lockup, permanently. iOS: a medium-detent sheet; Android: `ModalBottomSheet`.

**D9. Emotion keeps its picker.** Same 38 chips, categories ordered by valence. Tap commits and advances after a visible 0.3 s selection. A failed palette load shows "Couldn't load feelings · Retry" instead of a spinner. No subtitle: the title stands alone (D11, amended).

**D10. Life: domains and people on one screen (Margin treatment).** Title "What part of life?", a RECENT row of the user's last domains, then ALL 18 as a two-column list of glyph + name with the one-line description of the last tap in a caption bar. Multi-select, cap 3; the button always reads "Make it a pebble" (a "(2)" read as two pebbles on the device pass, 2026-11-05). Karma counts "any domain" once, so the cap cannot be farmed. Under the list, a pinned bar on `surfaceContainerLow` under a hairline, so the list's end reads as an end rather than a cut: the caption, "Who was there?" with the four most-tagged souls as tiles and a "More" tile, then the button; people are never scrolled out of reach. The list dissolves into the bar through a gradient rather than clipping at it. "Recent" was the first draft: recency needs an RPC (`pebble_souls` has no timestamp), the pebble count is already fetched, and the picker ranks by count too, so the strip and the picker's Usual rows show the same people. Tapping a tile toggles it; More opens the souls picker sheet of the souls spec (`2026-11-05-souls-picker-and-people-design.md`), whose Done returns here. Souls stay in the stage because of the north star ("life with five people"); the end-screen alternative is the A/B to run if attach rate drops (confirmed 2026-11-05). The glyph is derived by the server from the first domain by slug, not by tap order, so the end screen must visibly light the stone it got.

**D11. No summary pill (amended 2026-11-05).** The first draft carried a tonal pill under the headline with the answers so far and an Edit link. The device pass dropped it: it crowded the title and the back chevron already edits. Every scrolling step dissolves its content under the title and over the button through a gradient (iOS-standard), never a hard clip.

**D12. Make it richer (Riverbed treatment).** The success screen keeps the lit stone, large, with its derived glyph carved out, "+N karma", the name, and the week strip with the new stone settling on its day. Under MAKE IT RICHER: "Add to a collection" (hidden when the account has none), "Who can see it · Secret", "Add a few words · +1 karma" (only while the description is empty). Each row edits the finished pebble: collection and visibility through `complete_pebble` (it is keyed on moment and caller and performs edits), words through `save_moment` with the full snaps set. Then "Done", which returns to the origin (Path week, Home, or the card), and "Record another". The collection and privacy steps disappear from the stage.

**D13. Entry points become one model.** Path "+" and Home "New pebble" open the photo screen and read the same word. Quick moment keeps its Home tile and opens the moment page directly, without the photo screen (its job is that page now). Camera saves on the shot as today; its toast opens the saved moment page in edit instead of "Name it".

**D14. The bare moment page.** Keeps "Make it a pebble", which opens the pebble stage at the valence step, and gains "Add a few words" when the description is empty. When a Complete draft exists for that moment, the card says "You started this · continue" instead of restoring silently.

**D15. Copy, EN and FR.** All strings in `Localizable.xcstrings` and the Android resources, FR with "tu". New strings: Just write, Just save, Save & make it a pebble, Moment saved · Make it a pebble, Moment saved · 3 questions to make it a pebble, Neutral / Neutre, the four help lines and Got it, How to read this, Couldn't load feelings · Retry, Make it richer, Add a few words, Record another, You started this · continue, the partial-failure line. One copy pass on the 18 domain labels rides with this (Events, Weather and Tasks next to Identity and Spirituality).

**D16. Measurement.** Already-logged events give: moments completed within 24 h, share of pebbles edited within a day from the end screen, distribution across the nine valence cells, souls attach rate. Compare the four weeks before and after.

## Screens

| # | Screen | Asks | Control | Required | Advance | Default |
|---|---|---|---|---|---|---|
| 1 | Photo | "Start with a picture" / "Pick one, or write it from memory." | Just write (primary) + Camera in the fixed slot; inline system grid with albums (iOS), modal picker (Android) | no | pick = auto after 0.4 s; Just write = page | none |
| 2 | Moment page | name, when, words | thumbnail + Change, NAME, WHEN chip, A FEW WORDS | name or photo | Save & make it a pebble / Just save | now or EXIF |
| 3 | Valence (1/3) | "How did it land?" | current fan + roll, labelled, ghost stone, help sheet, (i) | yes | Continue (enabled on pick) | none |
| 4 | Emotion (2/3) | "What did you feel?" | current picker, pill under the headline | yes | tap, 0.3 s, auto | none |
| 5 | Life (3/3) | "What part of life?" + "Who was there?" | Recent + All 18 list, max 3; five souls sticky | domain ≥ 1 | Make it a pebble (n) | none |
| 6 | Make it richer | reward, then extras | lit stone, karma, week strip, three rows, Done / Record another | no | Done | Secret, no collection |

Taps, counted every tap and typing excluded: (a) + · pick · Just save = **3** (words-only: + · Just write · Just save = 3). (b) + · pick · Save & make it a pebble · stone · Continue · emotion · domain · Make it a pebble · Done = **9** with the fan's Continue, **8** if the roll's release commits. Today: 5 and 12.

## Contract

- `save_moment(name, description, happened_at, snaps[])`: refuses a moment with neither name nor snap (`moment_empty`), grants no karma. Unchanged.
- `complete_pebble(emotion_id, intensity, positiveness, domain_ids[], soul_ids[], collection_ids[], visibility, cards)`: non-empty `domain_ids` (`pebble_needs_domain`). Keyed on (moment, caller); the end-screen rows are edits through it. Unchanged.
- Pebble row only when complete; karma and achievements on the first insert; the glyph derived by `resolve_pebble_glyph`. Unchanged.
- Timestamps at whole seconds across surfaces. Unchanged.

## Clients

### iOS (reference)
- `Features/Capture`: steps become photo and moment page; `CaptureDraft` gains `description` and persists (D2); the When step and `RecordWhenStep` move into a chip + sheet; `RecordPhotoStep` re-enables `collectionNavigation` and the top accessory, adds the two fixed-slot controls, and auto-advances on selection.
- `Features/Complete`: steps become valence, emotion, life; souls merge into `CompleteDomainStep`'s screen as the Usual strip, with the souls spec's picker behind More (`CompleteSoulsStep` and the grid go); collection and privacy steps are deleted; `CompleteSuccessStep` becomes Make it richer; the pill (D11) lives in `RecordStepScaffold`'s subtitle slot.
- `Features/Path/Valence`: no seed (`seedValenceIfNeeded` goes), axis labels, ghost stone, the help sheet and the callout, "Neutral".
- `Features/Path/EmotionPickerContent`: Retry state.
- Home and Path entry points per D13; `BareMomentPage` per D14.
- Tests: Swift Testing on the flow models (tap counts as step transitions, no seed, partial-failure state, draft persistence round-trip, the pill's strings in both languages); reference renders for the six screens in both schemes.

### Android (mirror)
- `features/path/capture` and `complete` follow the same step models; the picker stays `PickVisualMedia` with auto-advance on return (D3); M3 chrome, `ModalBottomSheet` for the help and the calendar, native switches and list items on Make it richer. Konsist boundary: shared pieces (the pill, the souls strip) go in `core/`.
- Screenshot previews for the six screens, FR and large font variants.

### Web
- `/record` ends on the moment page; `/record/complete/[id]` keeps its route with the three steps and the end screen. The edit sheets keep the 9-tile valence grid until the web parity milestone picks it up.

## Plan

iOS as one stack, in dependency order: (1) Capture draft persistence + moment page, (2) photo step, (3) valence labels, help, no seed, rename, (4) Life screen + pill, (5) Make it richer + entry points + bare page, (6) copy pass and reference renders. Android as one stack mirroring the same parts. Web as one PR. Each part lints and tests alone; the device pass at XXL Dynamic Type in French on an iPhone SE-size device gates part 4.

## Open questions

All four assumptions were confirmed on 2026-11-05, with one change: the neutral word is "Neutral / Neutre", not "In-between / Entre-deux".

1. D2: unsaved preview after capture, not a saved page. Confirmed.
2. D7: "Neutral / Neutre" for the neutral word. Changed.
3. D10: souls inside the Life screen, not after the reward. Confirmed.
4. D13: Quick moment re-pointed at the moment page, not retired. Confirmed.

The iOS build ships this flow and the souls picker in one stack, souls parts first, because the Life screen hosts the picker: see the Plan of `2026-11-05-souls-picker-and-people-design.md`.
