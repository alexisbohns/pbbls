# Android pebble detail: the card — design

Date: 2026-10-20 · Surface: Android · Status: approved design, awaiting plan

## Why

The Path is now the Spine, which shipped in stack #1125. When you press a pebble
on it, the page that opens is still the old read view: a banner, a title, three
meta tiles, the description and a grid of souls. It shares nothing with the
Spine's language.

This spec replaces that read view with **the card**: the same card the Spine
shows, now held in the hand. It has two faces. The **recto** is the moment and
the **verso** is the pebble. A moment with no pebble yet has no card, and opens
as a plain page.

## Where it came from

The design came out of six rounds of mocks, five wave-1 directions and their
refinements, judged by the maintainer. The hub is
https://claude.ai/artifact/DG1GnRv6V2sicesPu2y6t1 (private).

The parts were chosen as follows:

- **Recto:** *Held*, the Spine card lifted unchanged with a leaf for the words.
- **Footer:** *Suit*, with privacy below the card's border.
- **Bare moment:** *Gathering*'s opening and *Suit*'s "Not a pebble yet" place.
- **Verso:** *Held*'s frame, the shipped valence lockup, and *Token*'s round
  ink postmarks and collection band.
- **Turn button:** *Label*'s miniature of the face it leads to.

The maintainer explicitly rejected these, and they must not come back:

- Invented marks for size, polarity or emotion: gem words ("a large gem"),
  I/II/III, seals, gauges.
- Visible edit links on the card.
- The folded-corner (dog-ear) turn affordance.
- Tilted or shadowed domain stamps.
- Pills for domains or collections.

## The page

### Top bar

The bar holds ← Back, then **Share** (public pebbles only, as today), then ⋮.

- The ⋮ menu holds **Edit moment**, **Edit pebble** (only with a pebble), a
  divider, then **Delete** in the error colour (orange; never red).
- Delete keeps the shipped dialog that asks whether to delete the moment or
  the pebble.
- The privacy badge leaves the bar's title slot; privacy now lives in the
  card's footer.
- There is no other edit entry anywhere on the page.

The current `DetailTopBar` already has this menu. The change is removing the
title badge and adding the divider.

### The card (a pebble: F1, F2, F3)

The card is a still **shell** around a **panel** that turns.

- **The shell** is the Spine card's stock, shadow and coloured edge (the
  emotion's edge tone, as on the Spine). It never moves during a turn.
- **The panel** carries the printed border, the same border as the Spine
  card's inner rule, and shows one face at a time.
- **The footer** sits below the panel's border, inside the shell. It is the
  same on both faces and does not turn:
  - **Left:** the privacy glyph, the grade in small caps (`visibility_*`), and
    under it the shipped sentence in italic (`record_privacy_*_explanation`).
  - **Right:** the **turn button**. Small caps "TURN" sits over the
    destination in italic ("Pebble" on the recto, "Moment" on the verso). Next
    to them is a miniature of the face it leads to, tilted −7° as in the mock:
    - from the recto, a tiny card back with a stone tinted in the emotion;
    - from the verso, a tiny recto with a photo block and lines.
  - The button has no container. One 48 dp target covers the label and the
    miniature, out to the card's edge, and is clipped so it never covers the
    privacy sentence.
  - French: "RETOURNER" over "Galet" or "Moment". A card you flip is
    *retournée*; *tourner* means to rotate or turn a page.
- **Size:** card width is min(available − 32 dp, 440 dp). Both faces share one
  height, the taller of the two, so the card never changes size when turned.
  The page scrolls if needed.

### Recto

The recto is the Spine card at the card's width: all of `SpineCard`'s chrome
scales with `u = width / 70`, so opening the card is a uniform scale. It holds:

- **Index:** the day number and weekday, in the left gutter.
- **Rail:** the vertical "21:00 — joy", in the right gutter.
- **Field:** the photo at its own ratio on the emotion mat, never cropped.
- **Legend:** the title, then the souls line. The card names **up to 8 souls**
  with their marks (the Spine names 3, then counts). Past 8, the `PeopleLine`
  counting rule applies.
- **Leaf:** below the legend, behind a dashed score. It holds the full
  dateline ("Thursday 8 October 2026 · 21:00") and the description. With no
  description, the leaf shows only the dateline.

**Photo-less pebble (F3).** On the Spine this is an `.entry` set as type. On the
card, the field becomes a slip on the emotion mat that carries the name, set as
the entry sets it. Everything else is the same.

### Verso

The verso keeps Held's frame: the emotion's mat print runs full-bleed around an
inner stock panel. From top to bottom:

1. **The valence lockup.** It reuses the record flow's lockup
   (`ValenceWord` + `valenceSpanRes`):
   - The word is Caveat Bold at 34/44/56 sp by size group: "Highlight",
     "Lowlight", or "Moment" for a neutral pebble. Small (day) events drop the
     word to lowercase.
   - Under it, the span in small caps: "OF MY DAY", "OF MY WEEK" or "OF MY
     MONTH".
   - Highlight is painted with **the emotion's lit-stone ramp**, not
     `ValenceStoneStyles.headlineInk`: its `ValenceMesh.inkHexes` starts at
     #F32716, which is red (see Follow-ups). Lowlight is in ink and Moment in
     `primary`. Night uses the stone's night ramp.
2. **The emotion,** named once in Ysabeau under a hairline ("Joy").
3. **The lit stone** (`LitStone`), large, in its raked clearing with rings.
   It takes the place a photo takes on the recto.
4. **Domains as round ink postmarks,** in one centred row above the band:
   - The domain's stroke glyph sits in the centre. The name repeats round the
     edge in capitals, separated by " · ", with the first repetition upright
     at the top.
   - Diameter is 72 dp, or `(innerWidth − gaps) / n` when that is smaller
     (four stamps). The gap is 6 dp.
   - There is an outer ring (1.6 dp) and a hairline inner ring (0.8 dp); the
     glyph field is 38 dp.
   - **Ring fit:** repetitions = ⌊C / w(NAME + " · ")⌋, at least 2, where C is
     the circumference of the text circle. The leftover space is spread as
     equal tracking after every character, so the seam is an ordinary gap.
     Never squash glyphs. If 2 do not fit at 8.5 sp, step down to 8, then 7.5;
     if 2 still do not fit at 7.5, set the name once plus a dot.
   - **Ink:** the emotion's dark tone on paper and its light tone at Night.
     Its restrained texture (fine pits, density drifting 0.83–1) is a
     deterministic alpha mask generated in code (`PostmarkNoise`). It is
     drawn **over** the stamp as paper-coloured speckles, stretched to the
     disc and clipped to it, in plain SrcOver. Over ink it reads as pits and
     thinning; over the bare panel it is invisible, because it is the panel's
     own colour. There is no `DstIn` anywhere: HWUI ignored one inside an
     offscreen layer on a Pixel 8 while Robolectric honoured it (see
     `apps/android/CLAUDE.md`, Path rendering). Each stamp seeds the noise
     with a hash of its domain id, so no two stamps share a pattern.
   - **Ring type and font scale:** the ring is measured at font scale 1. It is
     decoration inside a fixed stamp, and the domain names are also read by
     TalkBack from the verso's list. This is a deliberate exception to
     resizable text.
   - There is no rotation, no shadow and no click.
   - Contrast stays WCAG AA at the faintest density (the mocks estimate 5.5:1
     on paper; this must be measured on a device).
5. **Collections as a band:** a paper band wraps across the foot of the card.
   - It runs past the panel's border to both edges of the shell and turns
     under.
   - Collection names are in italic, separated by a small diamond, each led by
     the collection mark (`ic_pebble_collection`). The names wrap inside the
     band.
   - No collections means no band.
6. The footer, as above.

**Why domains and collections read as different without a legend:** a stamp
is pressed *into* this card, while a band is wrapped *around* it.

### Bare moment (F4): no card

A bare moment opens as a plain page:

- A kicker with the day and time ("SUNDAY 11 OCTOBER · 09:40"), the title, the
  photo on a white mat if there is one, then the description.
- Below that is the **"Not a pebble yet" place**: a dashed card outline with
  album corners, a dashed stone outline, the kicker "NOT A PEBBLE YET", one
  sentence, and **Make it a pebble** (`complete_cta`), the page's one call to
  action.
- The ⋮ menu holds Edit moment and Delete.

### Completion

When the Complete flow returns and the resume refresh finds a pebble, the card
**prints into the place**:

1. The card appears verso-up at the place's width.
2. The dashed stone outline flies to the clearing, and the lit stone presses in
   with a ripple and a glint (the Spine's press).
3. The lockup, postmarks, band and footer print in turn.
4. The plain page steps aside, and the card rises to its resting position,
   verso showing.

Under reduced motion, this becomes a crossfade to the card.

## Behaviour

- **Which face opens:** the face you pressed.
  - Pressing a Spine card or entry opens the recto.
  - Pressing a Spine stone opens the verso.
  - `PebblesKey.PebbleDetail` gains `face: CardFace = RECTO`, and the Spine
    passes `VERSO` from the stone's press.
- **The turn:**
  - Ways to turn: the turn button; and a horizontal drag on the panel (a flick
    carries it, release past the half reverts or completes).
  - For TalkBack the footer's turn button is the turn ("Turn over to your
    pebble" / "Turn over to the moment"), the last stop in the reading order.
    A custom action on the card would never be offered: TalkBack lists the
    actions of the focused node, and the card's root is never focused.
  - The panel alone rotates on Y (`graphicsLayer.rotationY` with camera
    distance), and the content swaps at 90°. The shell and footer stay still.
  - Reduced motion: a 150 ms crossfade.
  - The current face survives configuration change (`rememberSaveable`).
- **Open and close:** the card opens with a container transform from its slot
  on the Spine (`sharedBounds` keyed by moment id). System back and predictive
  back return it to the slot, seekable with the gesture.
- **Container on phones:** today `PebbleDetail` is a docked bottom sheet
  (`BottomSheetSceneStrategy`). The card needs the whole height, and a
  container transform from the Spine, so on phones the detail **leaves the
  sheet scene** and becomes a plain `BarKey` destination: full height above
  the navigation bar, and still not a modal. Concretely, `PanePairs.metadataFor`
  stops passing `sheetWhenCompact` for `PebbleDetail`, and the key's KDoc is
  updated to match.
- **Wide (≥ 840 dp):** the card is centred in the detail pane beside the Spine
  (today's list-detail scene), with no change to that strategy. The
  cross-pane flight is part of the transitions part and may fall back to the
  existing fade-through if it proves unreliable.

**TalkBack reading order.**

- **Recto:** the title (heading), the date and time, the emotion, the souls
  (each name once), the photo's description, the words, privacy, then the turn
  action.
- **Verso:** "Your pebble" (heading), the valence ("Highlight of my month"),
  the emotion, the stone (`SpineStoneDescription`), "Life domains" with each
  name once (the ring repetition is hidden), "Collections" with each name,
  privacy, then the turn action.
- Stamps and the band are not focusable as buttons.

## Data

There is **no schema change**. Everything is already in `v_moments_full`, which
`PebbleDetailViewModel` reads.

- **Valence:** `MomentPebble.valence` gives polarity and size group.
- **Domains:** `MomentPebble.domains` (`DomainRef{id, slug, name}`). The stroke
  glyph comes from the cached reference `Domain` (`strokes`, `viewBox`),
  looked up by id; with no strokes, the stamp shows the name ring only.
  Names resolve through `ReferenceStrings.referenceName(domain, slug, name)`,
  never `.name` directly.
- **Collections:** `MomentPebble.collections`. The view also returns
  `glyph_id`, which this design does not use yet (see Follow-ups).
- **Souls:** names come from `MomentPebble.souls`, and marks from the souls
  cache by id (A-15), as the Spine does.
- **Photo ratio:** the Spine's existing probe and cache.

## Components

Every colour, size and font goes through `PebblesTheme.spine` /
`PebblesTheme.hand` tokens (`ThemeLiteralsTest`); geometry in dp goes in a
`CardLayout` object, as `SpineLayout` does for the Spine.

New code lives in `features/path/card/`. Anything shared with the Spine stays
in `features/path/spine/`, or moves to `core/` only if Konsist requires it.

| Unit | Does | Depends on |
|---|---|---|
| `PebbleCard` | The shell, the panel slot, the footer; owns the turn state and gesture | `CardRecto`, `CardVerso`, `CardFooter` |
| `CardRecto` | The Spine card at card width, plus the leaf | `SpineCard` (extended: `namedSouls`, leaf slot), `PeopleLine` |
| `CardVerso` | The frame, lockup, emotion, stone, postmarks, band | `ValenceWord`, `LitStone`, `DomainPostmark`, `CollectionBand` |
| `DomainPostmark` | One round ink stamp | `ringFit()` (pure), `GlyphImage`, the noise texture |
| `ringFit(name, circumference, measure)` | Repetitions, tracking and size step: a pure function | none |
| `CollectionBand` | The full-bleed band with wrapped names | none |
| `CardFooter` | The privacy note and `TurnButton` with `FaceMiniature` | strings |
| `BareMomentPage` | The plain opening and the "Not a pebble yet" place | `complete_cta` |
| `CardCompletion` | The print-into-place choreography | `PebbleCard`, the Spine press |

`PebbleReadView`, `PebbleReadBanner`, `PebbleReadTitle` and
`PebblePrivacyBadge` lose their only caller. They are removed in the same stack
once nothing references them (`PebbleReadPetroglyph` stays: the record flow
uses it).

## Strings (EN / FR)

New strings:

- `card_turn`: "Turn" / "Retourner"
- `card_turn_to_pebble`: "Pebble" / "Galet"
- `card_turn_to_moment`: "Moment" / "Moment"
- `card_turn_action_to_pebble`: "Turn over to your pebble" / "Retourner vers
  ton galet"
- `card_turn_action_to_moment`: "Turn over to the moment" / "Retourner vers le
  moment"
- `card_verso_heading`: "Your pebble" / "Ton galet"
- `card_domains_a11y`: "Life domains" / "Domaines de vie"
- `card_collections_a11y`: "Collections" / "Collections"
- `bare_place_kicker`: "Not a pebble yet" / "Pas encore un galet"
- `bare_place_body`: "Its card prints here once you say what it meant: a
  feeling and its weight, the parts of your life it touched, who was there." /
  "Sa carte s'imprimera ici quand tu diras ce qu'il a compté : un sentiment et
  son poids, les parts de ta vie qu'il a touchées, qui était là."

Reused strings: `visibility_*`, `record_privacy_*_explanation`, `valence_*`,
`edit_moment`, `edit_pebble_title`, `action_delete`, `complete_cta` and
`pebble_share_a11y`.

## Testing

- **Unit tests (JUnit):**
  - `ringFit`: at least 2 repetitions; the seam's error is under 0.5 px over
    the circle; the size steps 8.5 → 8 → 7.5 → single.
  - Souls naming: 8 named, then counted.
  - The valence-to-lockup mapping for all 9 valences.
  - The face key from a stone press versus a card press.
- **Screenshot previews**, each in paper and Night, with one French row:
  - F1 recto and verso;
  - F2 recto and verso (public, so Share shows);
  - F3 recto and verso;
  - F4 bare;
  - a stress verso with four long domains and two long collections;
  - the menu open.
- **Robolectric NATIVE pixel test:** the postmark's paint mask leaves pits. It
  is a guard, not proof of HWUI.
- **Device (Pixel 8):** the postmark ink's contrast and the ring's legibility
  at 1×; the paint mask under HWUI; the turn gesture against the vertical
  scroll; predictive back.

## Out of scope

- **Layers:** the dormant reflective cards shown as strata under the card
  (Beck's columns). This is a later, separate design. Nothing here may depend
  on it.
- **iOS:** the 1:1 mirror rule applies. iOS gets its own issue once Android
  ships, after the iOS Spine (#1130).
- Web.
- Showing a collection's own glyph on the band.

## Follow-ups found during the exploration

- `ValenceMesh.inkHexes` starts at #F32716. That is red, so the record flow's
  Highlight word breaks the no-red rule.
- In the seeded domain glyphs, Travel's strokes are identical to Identity's.
- Show `collections.glyph_id` on the band in place of the generic mark.
- French uses "Modifier le caillou" where every other string says *galet*.

## Suggested parts (one stack)

1. **The card, static.** `PebbleCard` with both faces, the footer, postmarks
   and band, replacing `PebbleReadView` for pebbles. The top bar loses the
   badge. The turn is button only, with no animation. On phones the detail
   leaves the sheet scene.
2. **The turn.** The rotation, the drag, reduced motion,
   and the `face` key from the Spine's stone press.
3. **The bare moment.** `BareMomentPage` and the completion choreography.
4. **Open and close.** The container transform from the Spine, predictive
   back, and the wide-pane flight.
5. **Cleanup.** Remove the orphaned read-view files.

The Arkaik map is updated with the plan: the pebble-detail view node moves to
`development`.
