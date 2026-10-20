# iOS New Path foundations (stack B): type, reference images, stone fixes

**Date:** 2026-10-20
**Issues:** #1158 (B1), #1159 (B2), #1143 and #1097 (B3), #1101 (rider on B1). Initiative #1161, milestone M64.
**Status:** draft for maintainer review
**Depends on:** nothing. Stack B is independent of stack A (#921): it reads no `Color.pebbles.*` role and adds none. Where a later stack needs a role in a reference, it names a stack A role, once both have landed.
**Unblocks:** the iOS Spine (#1130) and the held card (#1146).

## Goal

Give the iOS Spine and the held card three foundations before either is built:

1. **B1:** the same variable Ysabeau files Android ships, and one type-rank table (`SpineType`) that mirrors Android's `SpineTypography` rank for rank. Feature views never name a size, a weight or a feature.
2. **B2:** a repeatable visual gate. Swift Testing renders named views to PNGs, CI holds the references, a diff beyond a fixed threshold fails the PR, and a label re-renders the references.
3. **B3:** the lit stone matches web #1088 (a soft outline on gem and river, a matte river, a grittier lava), and the iOS valence tables lose their red stops. B3 is iOS-only.

## Non-goals

- The Spine, the card, and their fixtures and references. Each stack adds its own, from the catalogue in §B2.6.
- Swift 6, `#available` guards, and XCTest-style tests.
- Inclusive Sans, which is Android-only. Every text outside the Spine and the card keeps the system font and the `PebblesFont` tokens.
- An iOS twin of Android's `ThemeLiteralsTest`. The "no weight in a feature view" rule is held in review until that test exists.
- Deleting the legacy SVG renderers (`PebbleAnimatedRenderView`, `PebbleStaticRenderView`, `PebbleOutlineBackdropView`). That cleanup is owed and stays out of scope. B3 deletes only the dead `ValenceStoneStyle.style(for:)` branch (§B3.1).
- The Android half of #1143. See §Follow-ups outside this stack.
- Press glint values. iOS has no press glint until #1130 adds one. The web targets (gem 1, pebble 0.30, lava 0.34) are recorded here for that stack to use.

## Findings that shape the design (measured 2026-10-20)

- `Pebbles/Resources/Ysabeau Italic.ttf` is **byte-identical** to Android's `ysabeau_italic.ttf` (sha1 `942bee3a…`). Only the roman is new. `Ysabeau SemiBold.ttf` is a static cut from an older release (sha1 `ce2295fd…`). Android's `ysabeau.ttf` (sha1 `6adcb36b…`) is a different file.
- CoreText reads both variable files as one `wght` axis (`0x77676874`), range 1 to 1000, default 700. The default instances are `Ysabeau-Bold` and `Ysabeau-BoldItalic`.
- The **roman** has the Lower Case (`smcp`), Upper Case (`c2sc`), Number Case and Number Spacing features. The **italic has no Lower Case feature, so it has no `smcp`**. The rank table pairs italic with small caps nowhere, and a test keeps it that way.
- Ysabeau's natural line height is 1.343 em (ascent 1.054, descent 0.289, leading 0). Eight ranks ask for less: title 1.02, cardIndexNumeral 0.86, cardSlipName 1.08, cardTitle 1.10, bareTitle 1.12, entryName 1.12, contentsName 1.20 and dayLetter 1.26. See §Risks.
- On iOS, only `headlineInk(for:)` in `ValenceStoneStyle` is live, and it reads no hex table. `style(for:)` and the mesh tables, `inkHexes` included, have no caller since #975, when the picker started drawing `StoneView`. On Android, `ValenceMesh.inkHexes` is live, drawn by `ValenceStone.kt`. So the red is visible on Android and dormant on iOS.
- The selected wash (`selectedWashHexes`) starts at `#F64031`, a hue of 4.6°. That is red too, although #1143 names only the ink.

---

## B1: variable Ysabeau and `SpineType` (#1158)

### B1.1 Files and registration

- Copy `apps/android/app/src/main/res/font/ysabeau.ttf` to `apps/ios/Pebbles/Resources/Ysabeau.ttf`. Keep `Ysabeau Italic.ttf` as it is: it is already the Android file, and renaming it would only add churn.
- Delete `Ysabeau SemiBold.ttf`. In `Info.plist` `UIAppFonts`, replace `Ysabeau SemiBold.ttf` with `Ysabeau.ttf`. `project.yml` needs no change, because `Pebbles/` is already a source path and resources are inferred.
- In the `ios.yml` `verify` job (ubuntu), add a step that runs `cmp` on both pairs (iOS file against Android file). Add `apps/android/app/src/main/res/font/**` to the workflow's path filter, so a font bump on Android turns iOS red until the files match again.

### B1.2 Face and descriptor API (`Pebbles/Theme/YsabeauFace.swift`)

```swift
enum YsabeauFeature: Hashable { case smallCaps, lining, tabular }   // SC / LIN / TAB
enum YsabeauFace {
    /// One UIFont at an exact axis value. Memoised by (size, weight, italic, features).
    static func uiFont(size: CGFloat, weight: Int, italic: Bool, features: Set<YsabeauFeature>) -> UIFont
}
```

- **Base:** `UIFontDescriptor(name: italic ? "Ysabeau-BoldItalic" : "Ysabeau-Bold", size:)`.
- **Weight:** `UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [0x77676874: weight]`. Caveat Bold already ships this way.
- **Features:** `.featureSettings` entries keyed by the OpenType tag (`kCTFontOpenTypeFeatureTag`: value `1`), using the same tag strings Android passes:
  - `smallCaps` → `smcp`, `c2sc`
  - `lining` → `lnum`, `pnum`
  - `tabular` → `lnum`, `tnum`
- **Missing face:** if `UIFont(name:)` returns nil, log once per face with `Logger(subsystem: "app.pbbls.ios", category: "fonts")`, then fall back to `UIFont.systemFont(ofSize:weight:)` at the nearest `UIFont.Weight`. Never crash.
- **The existing helpers keep their names and signatures**, and are rebuilt on this face:
  - `Font.ysabeauSemibold(_:)` = weight 600, roman, `[.lining]` (the features it has today).
  - `Font.ysabeauSemiboldItalic(_:)` = weight 600, italic, `[.lining]`.

  Their eleven call sites stay as they are (§B1.6).

### B1.3 The rank table (`Pebbles/Theme/SpineType.swift`)

```swift
enum SpineType {
    struct Style: Equatable { size: CGFloat; weight: Int; trackingEm: CGFloat; italic: Bool
                              features: Set<YsabeauFeature>; lineHeight: CGFloat?; scaling: Scaling }
    enum Face: Equatable { case ysabeau, caveat }   // caveat only for lockupWord
    enum Scaling: Equatable { case text(Font.TextStyle), card(cap: CGFloat), none }
    /// A name's cut, produced by the Spine's EntryType logic (#1130), never written in a view.
    struct Cut: Equatable { size: CGFloat; weight: Int; italic: Bool; trackingEm: CGFloat }
    static let kicker: Style …                       // one static per fixed rank
    static func cardTitle(u: CGFloat, cut: Cut) -> Style …   // one func per parameterised rank
    static func lockupWord(_ size: ValenceSizeGroup) -> Style …
}
extension View { func spineType(_ style: SpineType.Style) -> some View }
```

The table follows Android's ranks one for one: same names, sizes in points (Android's sp), weights on the axis, and tracking in em. `lineHeight` is in points; a dash means the font's natural line height. `Text`: `SC` is `smcp, c2sc`, `LIN` is `lnum, pnum`, `TAB` is `lnum, tnum`, and `it` is italic. The iOS column gives the rank's `Scaling` (§B1.5): `text(style)` is written as the style alone, `card` means `.card(cap: 1.3)`, and `none` means `.none`.

| Rank | Size | wght | Track em | Style | Line | iOS scaling |
|---|---|---|---|---|---|---|
| kicker | 12 | 600 | 0.2 | SC | – | caption |
| title | 40 | 300 | −0.026 | LIN | 40.8 | largeTitle |
| summary | 18 | 380 | 0 | it | – | body |
| sectionLabel | 11.5 | 600 | 0.2 | SC | – | caption2 |
| masthead | 11.5 | 400 | 0.18 | SC | – | caption2 |
| mastheadNumber | 11.5 | 650 | 0.18 | SC | – | caption2 |
| personName | 15 | 600 | 0.1 | SC | – | subheadline |
| dayLetter | 9.5 | 600 | 0 | SC | 12 | caption2 |
| contentsDay | 10.5 | 600 | 0.18 | SC | – | caption2 |
| contentsTime | 12.5 | 400 | 0.04 | TAB | – | caption |
| personsFold | 16 | 380 | 0 | it | – | body |
| entryTime | 13 | 560 | 0.06 | TAB | – | footnote |
| entryEmotion | 12 | 600 | 0.12 | SC | – | caption |
| people | 15 | 400 | 0 | – | 22.5 | subheadline |
| soulName | 13 | 620 | 0.1 | SC | – | footnote |
| dayNumeral | 25 | 330 | −0.02 | LIN | – | title2 |
| dayWeekday | 10 | 600 | 0.2 | SC | – | caption2 |
| mouthLabel | 12 | 600 | 0.18 | SC | – | caption |
| mouthLine | 16 | 380 | 0 | it | – | body |
| rollNumeral | 14 | 600 | 0.1 | SC + LIN | – | footnote |
| still | 15 | 380 | 0 | it | – | subheadline |
| contentsName(cut) | 16.5 | cut | 0 | cut.italic | 19.8 | body |
| entryName(cut) | cut.size | cut | cut.trackingEm | cut.italic | 1.12 × size | by size band ¹ |
| cardIndexNumeral(u) | 5.3u | 600 | −0.045 | LIN | 0.86 × size | fixed |
| cardIndexDay(u) | 1.8u | 650 | 0.14 | SC | – | fixed |
| cardRail(u) | 1.95u | 560 | 0.07 | TAB | – | fixed |
| cardRailEmotion(u) | 1.95u | 650 | 0.15 | SC | – | fixed |
| cardTitle(u, cut) | 4.4u | cut | −0.012 | cut.italic | 1.1 × size | fixed |
| cardSoul(u) | 2.5u | 650 | 0.12 | SC | – | fixed |
| cardMore(u) | 2.5u | 560 | 0 | TAB | – | fixed |
| cardOthers(u) | 2.6u | 380 | 0 | it | – | fixed |
| cardSlipName(u, cut) | 6.4u | cut | −0.014 | cut.italic | 1.08 × size | fixed |
| cardFooterGrade | 10.5 | 650 | 0.16 | SC | – | caption2 |
| cardFooterNote | 12.5 | 380 | 0 | it | – | caption |
| cardTurnTarget | 13 | 380 | 0 | it | – | footnote |
| cardLeafDateline | 10.5 | 600 | 0.18 | SC + LIN | – | caption2 |
| cardLeafBody | 17 | 380 | 0 | – | 27 | body |
| cardLockupSpan | 10 | 650 | 0.22 | SC | – | caption2 |
| cardEmotion | 20 | 400 | 0 | – | – | title3 |
| cardBandName | 14 | 400 | 0 | it | – | footnote |
| bareTitle | 34 | 300 | −0.02 | – | 38 | largeTitle |
| placeBody | 16 | 380 | 0 | – | 24 | body |
| lockupWord(small / medium / large) | 34 / 44 / 56 | 700 (Caveat) | 0 | small: lowercase | – | card |
| postmarkRing(size) | size | 650 | 0 | LIN | – | none (RingFit sizes it) |

¹ **Size bands**, used for `entryName` and to derive the column above: ≤ 11.5 caption2 · 12–12.5 caption · 13–14 footnote · 15 subheadline · 16–19 body · 20–21 title3 · 22–26 title2 · 27–33 title · ≥ 34 largeTitle. One pure function, `SpineType.textStyle(forSize:)`, owns these bands, and the tests pin them.

**`SpineType.Cut { size, weight, italic, trackingEm }` is the only type that carries a name's cut.** Stack D's `EntryTypes` port returns `Cut`, and the rank functions (`contentsName`, `entryName`, `cardTitle`, `cardSlipName`) take it. No other struct, tuple or raw weight crosses from logic into a view. `Cut` has exactly two constructors. The Spine's `EntryType` port (#1130) builds the valence cuts (highlight 600, neutral 480, lowlight 360 italic). `Cut.untitled(size:)` covers the untitled italic, which Android writes inside `SpineEntry.kt` as `UNTITLED_WEIGHT`. On iOS that value lives in `SpineType`, so no view names it.

**`lockupWord`** is the valence word at the top of the card's verso. The Android card spec takes it from the record flow's lockup (`ValenceWord`): Caveat Bold at 34 / 44 / 56 by size group, lowercase for small (day) events. It is the one non-Ysabeau rank. It resolves through the same Caveat `wght` 700 descriptor as `PebblesFont.valenceWord*`, and it keeps their `inkOverhang` (6 / 8 / 10 pt). Its span below it is `cardLockupSpan`.

**`postmarkRing(size)`** is the rank the card's ring text uses. It resolves through `YsabeauFace` like every other Ysabeau rank, never through `Font.custom(_:fixedSize:)`, which cannot set the axis or the features.

### B1.4 The modifier

`spineType(_:)` reads `@Environment(\.dynamicTypeSize)` and `@Environment(\.legibilityWeight)`, then resolves:

1. **Size.** With `traits = UITraitCollection(preferredContentSizeCategory: min(env, .accessibility2).uiKit)`:
   - `.text(style)`: `UIFontMetrics(forTextStyle: style.uiKit).scaledValue(for: size, compatibleWith: traits)`.
   - `.card(cap:)`: `size × min(cap, UIFontMetrics(forTextStyle: .body).scaledValue(for: 1, compatibleWith: traits))`.
   - `.none`: the size as given.
2. **Weight.** `weight + 100`, capped at 800, when `legibilityWeight == .bold`. A custom face does not get Bold Text from the system, and the Spine is text-heavy.
3. **Font.** `Font(YsabeauFace.uiFont(…))` (or the Caveat descriptor for `lockupWord`) with `.tracking(trackingEm × resolvedSize)`.
4. **Line height.** If `lineHeight` is set, it is scaled by the same factor as the size, and `.lineSpacing(scaledLineHeight − uiFont.lineHeight)` is applied (see §Risks for negative values).

The environment drives the size, not `UITraitCollection.current`. That keeps the modifier reactive, and lets the B2 harness pin Dynamic Type through `.environment(\.dynamicTypeSize, …)`.

Two things are banned on a `spineType` text: `.bold()` and `.fontWeight()`. `Font(UIFont)` would ignore them or synthesise a weight.

### B1.5 Dynamic Type policy

**`SpineType` is the one owner of Spine and card text scaling.** Stacks D and E cite this section and do not scale text themselves. The rank model carries one of three policies:

- **`.text(style)`** for every fixed-size rank, including the held card's fixed ranks. It scales with its text style through `UIFontMetrics` and stops at `.accessibility2`. That is about 194% at body size, the iOS equivalent of Android's font-scale 2.0 ceiling (the `fs2` previews). Above `.accessibility2`, the layout stays readable instead of overflowing.
- **`.card(cap: 1.3)`** for the card-unit ranks (`u`) and `lockupWord`. They scale with the content size category up to ×1.3 and no further, as Android does, so the card's text grows a little without breaking the card's proportions.
- **`.none`** for `postmarkRing` only, because `RingFit` sizes it to the ring.
- **Everything else in the app keeps its current behaviour.** `PebblesFont` and the system font are untouched.

### B1.6 Replacing the static cut: call-site audit

| Call site | Today | After B1 |
|---|---|---|
| `PebblesFont.title` (RecordStepScaffold, CompleteSuccessStep, GlyphCarveSheet), `.buttonLabel` (ProfileLogoutButton) | `ysabeauSemibold(28 / 17)` | Same helper, now the variable face at 600. No edit. |
| PathBottomBar:54, WeekPathView:79, PathPebbleRow:54, NewPebbleButton:22, WeekRollCairnCell:42, WeekHeaderView:21, KarmaEarnedCapsule:22 | `ysabeauSemibold(n)` | No edit. The Path files leave with #1130. |
| CompleteSuccessStep:30, PebbleReadTitle:21, PathPebbleRow:52 | `ysabeauSemiboldItalic(n)` | No edit. |
| PebbleReadTitle:23 | `.custom("Ysabeau-SemiBold", 24)` | `.ysabeauSemibold(24)`. The PostScript name is gone. The file itself leaves with #1146. |
| WelcomeSlideView:14 | `.custom("Ysabeau-SemiBold", 22)` | `.ysabeauSemibold(22)`. Update the doc comment too. It gains `lnum, pnum`, which only changes digits, and the titles have none. |

Weight 600 of the variable face is the same design as the old static cut, but from a newer release, so small glyph differences are expected. The manual check covers them: the welcome carousel, the record title, and a Path row, each EN and FR, against the phone before the change.

### B1.7 Rider: #1101 on iOS

In `Localizable.xcstrings`, change three French values:

- line 5561: "Nouveau pebble" → "Nouveau galet" (key "New pebble", the new-pebble button)
- line 6111: "Caillou créé" → "Galet créé" (key "Pebble created")
- line 6127: "Caillou enrichi" → "Galet enrichi" (key "Pebble enriched")

All three nouns are masculine, so the adjective and participles stay. Also update the doc comment at `KarmaReason.swift:11`.

Edit the catalog as text at those anchors, never by re-serialising the whole file. The PR body says "Part of #1101", because web and Android close it.

### B1.8 Tests (Swift Testing)

- **`YsabeauItalicFontTests`**, extended (new tests in the same file):
  - `Ysabeau.ttf` is bundled, and `Ysabeau SemiBold.ttf` and `UIFont(name: "Ysabeau-SemiBold")` are both nil.
  - `Ysabeau-Bold` resolves.
  - The italic at 380 keeps `.traitItalic`, and its `CTFontCopyVariation` reads 380.
- **`YsabeauFaceTests`:**
  - Weights 300, 480 and 650 resolve to distinct faces. `CTFontCopyVariation[wght]` reads 300, 480 and 650, and the advance of "mmmm" strictly increases across the three, which proves the axis applied and was not only stored.
  - `[.smallCaps]`: the glyph IDs of "abc" differ from the plain face (`smcp`), and so do those of "ABC" (`c2sc`).
  - `[.tabular]`: "1" and "0" have equal advances, and `CTFontCopyFeatureSettings` contains `lnum` and `tnum`. `[.lining]`: their advances differ (`pnum`).
  - A missing face falls back to the system font without crashing (inject the name).
- **`SpineTypeTests`:**
  - Every rank matches the table above, transcribed as a vector the way Android's `SpineTypographyTest` does.
  - No italic rank carries `.smallCaps`.
  - `lockupWord` is the only `.caveat` rank, and `postmarkRing` the only `.none` rank.
  - `textStyle(forSize:)` holds the band edges.
  - Dynamic Type:
    - kicker resolves to 12 pt at `.large`, and to the `UIFontMetrics(.caption)` value at `.accessibility2`;
    - `.accessibility5` resolves to the same size as `.accessibility2`;
    - `cardTitle(u: 4, …)` at `.accessibility2` is exactly ×1.3 of its `.large` size, and the same at `.accessibility5`;
    - `lockupWord(.large)` follows the same ×1.3 cap;
    - `postmarkRing(8)` resolves to 8 pt at every size.
  - Bold Text adds 100 and caps at 800.
  - The line-height probe in §Risks.
- **`LocalizationTests`:** no `fr` value in the catalog contains "caillou" or "pebble", ignoring case. Brand strings use `Text(verbatim:)` and are not in the catalog.

---

## B2: reference images in CI (#1159)

### Decisions

#### D1: a hand-rolled harness, `ImageRenderer` plus a pixel diff

The harness is about 200 LOC under `PebblesTests/References/Support/`. `ReferenceImage.assertMatches(_ view:, named:, config:)` renders, then compares against or records the PNG.

Reasons:
- It adds no dependency. `project.yml` pins every package exactly and treats a bump as a deliberate PR. `swift-snapshot-testing` would bring `swift-custom-dump`, `xctest-dynamic-overlay` and the `swift-syntax` resolution into that list.
- It links nothing from XCTest. The target stays Swift Testing only, and failures go through `Issue.record` with the diff paths attached.
- The comparison metric is ours, and it can be exactly Android's: the fraction of pixels that differ.
- `ImageRenderer` is already how this repo checks iOS visuals.

Considered and rejected: `swift-snapshot-testing` 1.18 through its Swift Testing traits. It is mature, and its `UIHostingController` window strategy renders a `ScrollView`. But it costs three pinned packages, and its precision and perceptual-precision thresholds are not Android's metric, so the two gates would mean different things. Window hosting is kept as a fallback inside our harness (D6), not as a reason to take the library.

#### D2: references are rendered on CI only

The first stone render, and every later one, comes from the macOS runner. A local render is for looking at, never for committing. This is Android's rule, for the same reason: #793 shows two Xcode builds disagreeing on the same code.

#### D3: every reference is pinned

| Pin | Value | How |
|---|---|---|
| Width | **393 pt** (iPhone 15, the maintainer's device). This is the citable reference width. | `ProposedViewSize(width: 393, height: nil)` with the ideal height. Components use their own fixed frame. |
| Scale | 2× | `renderer.scale = 2`. It keeps PNGs small. Android's 1080 px renders are about 2.6×, and parity is judged by eye, not by pixel. |
| Colour scheme | paper = `.light`, Night = `.dark` | `.environment(\.colorScheme, …)`, plus rendering inside `UITraitCollection(userInterfaceStyle:).performAsCurrent { … }` so UIKit-resolved colours follow. |
| Locale | `en` or `fr` | A process-level pass: `xcodebuild -testLanguage fr -testRegion FR` (`String(localized:)` ignores the environment). The harness also sets `.environment(\.locale, …)`. A reference declares its locales and is skipped in the other pass. |
| Time zone | Europe/Paris | `TEST_RUNNER_TZ=Europe/Paris` on both passes, plus `.environment(\.timeZone, …)`. This is the fixture week's zone. |
| Dynamic Type | `.large`, plus `-ax2` variants at `.accessibility2` | `.environment(\.dynamicTypeSize, …)` |
| Toolchain | Xcode 26.3 and the iOS runtime it bundles, on device "iPhone 17" | Both are pinned in `ios.yml`. The job fails loudly if either is missing, like the Xcode pin does. Moving either one is a deliberate commit that re-baselines. |
| Motion and power | Rest light, `isFlat: false`, `followsTilt: false` | Fixture arguments. No `CMMotionManager`, no Low Power Mode read. |

The pinned device name replaces the "any available iPhone" resolution for the whole `test` job. The suite stays one run, and the references depend on the runtime.

Considered and rejected: keeping "any iPhone" and pinning only the runtime. The device does not change an `ImageRenderer` output at a fixed width, but leaving it free makes a drift impossible to bisect.

#### D4: threshold 0.05% of pixels, exact per pixel, never widened

A pixel counts as different if any RGBA channel differs at all. The test fails when the differing pixels exceed `ReferenceImage.threshold = 0.0005` of the image. A size mismatch always fails. The constant sits in one file, with a comment that it is never widened.

B2 calibrates the number the way Android did:
- Render the suite twice on the runner, which must be byte-identical.
- Re-word one two-word label on the tallest reference, and confirm it fails by at least 2×.

If the headroom is under 2×, the threshold goes **down**.

#### D5: label-driven rebaseline, `rebaseline-ios-screenshots`

This mirrors `android-screenshots.yml` step for step (§B2.4).

#### D6: harness rules

- No `ScrollView` and no `containerRelativeFrame` inside a rendered view. Under `ImageRenderer` both render empty or zero.
  - A "scrolled" state is a slice of the full column, rendered as a `VStack` and clipped with a frame and an offset.
  - Lazy stacks become eager ones in the fixture.
- No out-of-process or UIKit-backed content: `PhotosPicker`, keyboards, Rive views, `confirmationDialog`. A fixture that needs one draws a stand-in, or leaves that state to the device check.
- **The window fallback.** If a view draws empty under `ImageRenderer` (the check is the Metal `layerEffect` of the first stone render), the harness gains a `.window` mode:
  1. host the view in a `UIHostingController`;
  2. put it in an offscreen `UIWindow` at the pinned size and trait collection;
  3. capture it with `drawHierarchy(in:afterScreenUpdates: true)`.

  It is built only if the first stone reference needs it.

### B2.1 Folder layout

```
apps/ios/PebblesTests/References/
  Support/ReferenceImage.swift      render, compare, record, diff writer
  Support/ReferenceConfig.swift     ReferenceConfig { scheme, locales, dynamicType, width }
  StoneReferences.swift             @Suite, nested in `References`
  WelcomeReferences.swift
  __References__/<Suite>/<name>-<paper|night>-<en|fr>[-ax2].png
```

- **References** are read and written through `#filePath`. The simulator shares the host filesystem, so nothing is bundled.
- **On a mismatch**, the harness writes `<name>.actual.png` and `<name>.diff.png` (differing pixels in magenta over a 30% grey copy) to `apps/ios/build/reference-diffs/`.
- **A missing reference fails.** It is never recorded implicitly.
- **Recording** happens only when the process sees `PBBLS_RECORD_REFERENCES=1`, which xcodebuild passes in as `TEST_RUNNER_PBBLS_RECORD_REFERENCES=1`. In record mode a test writes its PNG and passes.

### B2.2 First references (the proof before the Spine)

- **`StoneReferences.nineStones`**, paper and Night, EN:
  - the nine `StoneView`s (size × polarity) on `StonePalette.brand`;
  - each at `light: .rest`, `isFlat: false` and height 110, laid out as the `StoneView` "Nine stones · brand" preview;
  - plus `nineStonesFlat` (the Low Power drawing).

  These exercise Metal, the wobbled silhouette, and both schemes, and B3 moves them on purpose.
- **`WelcomeReferences.slide`**, paper and Night, EN and FR, plus one `-ax2` (paper, FR):
  - `WelcomeSlideView(step: WelcomeSteps.all[1])` at width 393;
  - it covers a pure SwiftUI view, the variable Ysabeau from B1, the locale pass and Dynamic Type.

### B2.3 CI job (`ios.yml`)

The `test` job keeps one `xcodebuild test` run, now on the pinned device with `TEST_RUNNER_TZ=Europe/Paris`. That is the EN pass, and it runs the whole suite. Two steps follow:

- **French references.** The same command with `-testLanguage fr -testRegion FR -only-testing:PebblesTests/References`.
- **Upload reference diffs**, on failure only. It uploads `apps/ios/build/reference-diffs/**` as `ios-reference-diffs`, next to the existing xcresult upload.

### B2.4 Rebaseline workflow (`.github/workflows/ios-references.yml`)

- **Triggers:** `workflow_dispatch`, and `pull_request: [labeled]` gated on `label.name == 'rebaseline-ios-screenshots'` from a same-repo head. `permissions: contents: write`, with a concurrency group per ref.
- **Setup:** the same as `test`: macos-26, the Xcode 26.3 check, xcodegen, the placeholder `Secrets.xcconfig`, the pinned device.
- **Render:**
  1. `git rm -rq apps/ios/PebblesTests/References/__References__`, so the references of deleted tests go;
  2. run the EN pass and then the FR pass, both with `TEST_RUNNER_PBBLS_RECORD_REFERENCES=1` and `-only-testing:PebblesTests/References`.
- **Commit:** as `github-actions[bot]`, with the message `test(ios): re-baseline reference images`.
  - From a feature branch, push to its head.
  - From `main`, push `test/ios-reference-rebaseline-<run>` and say to open a PR.
- **Step summary:** the Android text, adapted. The runs queued on the bot's commit wait at `action_required`: approve them, or push a commit. Do not re-run the failed run, because a re-run replays the old SHA.
- **One-time setup:** create the `rebaseline-ios-screenshots` label with B2.

### B2.5 Tests and done-when for the harness

`ReferenceImageTests` covers the pure parts:
- the diff fraction for identical images, one pixel, and a size mismatch;
- the reference path naming, for every scheme, locale and Dynamic Type combination;
- a skip when the current locale is not in the reference's list.

**The scheme probe.** It is role-free, so B depends on no colour role. A test-only sRGB swatch is defined as a dynamic `UIColor` (`#FFFFFF` light, `#000000` dark) and drawn as `Color(uiColor:)`. Render it at 10 × 10 in paper and in Night. Assert that the centre pixel is `#FFFFFF` in paper and `#000000` in Night. Because the colour resolves through the trait collection, the probe checks both the SwiftUI environment and the `performAsCurrent` pin.

### B2.6 The catalogue later stacks must cover (from the Android previews)

Every entry comes in paper and Night unless it is marked.

- **Spine (#1130), the fixture week of Mon 5 to Sun 11 Oct 2026, Europe/Paris:**
  - Week: `verso` (+ `-ax2` paper, + `fr` paper), `days` (a slice at the first seam), `mouth` (the end of the week), `empty` (the empty current week).
  - Feed: `picnic` (the 14-soul row). Lit stone: `gallery` (9 stones), `seeds`, `verso`. Stone: `allValences` with and without a glyph, `blank`.
  - The states must include: a card per primed ratio; an entry per intensity × valence; the 14-soul people line; the glyph-less stone; the bare moment; the untitled photo; the still day; the empty week; the current-week mouth against a past-week mouth.
- **Held card (#1146):**
  - Faces: F1 recto and verso (+ FR recto paper, FR verso); F2 verso (public, so Share shows); F3 recto and verso; the stress verso (four long domains, two long collections); `midTurn` and `midTurnPastHalf`, paper only.
  - Bare moment: `bare` (+ FR), `barePhoto`, `bare-ax2` FR.
  - Parts: postmark rows (1, 4 FR, accents FR); collection bands (one, two FR); footer variants (recto connections, verso public, secret FR, public FR `-ax2` paper).
  - Detail: the completion phases (printing, press, rising) and the menu open. The delete dialog is system chrome and stays a device check.

---

## B3: lit-stone fixes (#1097, #1143)

### B3.1 Valence ink without red (#1143, the iOS half)

**What is live, by grep on 2026-10-20.** `ValenceStoneStyle` has one external caller: `ValenceHeadlineView.swift:19`, which calls `headlineInk(for:)`. `headlineInk` reads three old tokens (`Color.system.foreground`, `Color.accent.shaded`, `Color.accent.primary`) and **no hex table**. `inkHexes` feeds only `highlightInk`, which only the dead `style(for:)` uses (line 42). So every member except `headlineInk` is dead, `inkHexes` included:

- `style(for:)` and `selected(for:)`
- `restingWash` and `joySurface`
- `washHexes`, `inkHexes`, `selectedWashHexes` and `meshPoints`
- `highlightWash`, `highlightSelectedWash` and `highlightInk`
- `gradient(from:)`, with its `if #available(iOS 18, *)`
- the `backdrop`/`ink` struct fields

**Ownership.** Stack A3b migrates `headlineInk`'s three token reads. Whichever of A3b and B3 lands first deletes the dead members above, and for the other stack the deletion is a no-op. After the deletion the file holds only `headlineInk`, iOS has no red valence stop left, and #1143's iOS half is closed by deletion, not by retuning.

**The retuned tables are the cross-surface reference.** Android, where the ink is live, adopts them in its follow-up (§Follow-ups outside this stack). Any later iOS port of the mesh (for example a highlight lockup word in #1146) must start from these values, not from git history. Every hue is remapped linearly from [3.5°, 40.7°] to [20°, 45°]. The ink rule (one hue per sample, fixed saturation and lightness), the gradient's order and the corners do not change. The error orange `#EA6B00` sits at 27.4°, inside the new range.

- **ink** (HSL 0.90 / 0.52):
  - row 1: `#F36316 #F37816 #F37B16 #F36E16`
  - row 2: `#F37D16 #F38B16 #F37F16 #F36916`
  - row 3: `#F39F16 #F3AD16 #F38616 #F36616`
  - row 4: `#F3B016 #F3BC16 #F38716 #F36016`
- **selected wash** (HSL 0.92 / 0.58, red today at `#F64031`), same rows:
  - row 1: `#F67531 #F68931 #F68C31 #F67F31`
  - row 2: `#F68D31 #F69A31 #F68F31 #F67B31`
  - row 3: `#F6AC31 #F6B831 #F69631 #F67931`
  - row 4: `#F6BB31 #F6C531 #F69731 #F67331`
- **wash:** unchanged. It is a pastel salmon, not red.

**Test.** The no-red hue test (every hue between 18° and 50°) goes with the live tables: the JUnit twin in the Android follow-up, and on iOS in whichever stack first adds a mesh back. B3's iOS check is that `ValenceStoneStyle.swift` contains no hex table after the deletion. That check needs only a review, and `headlineInk`'s snapshot in the valence picker stays unchanged.

### B3.2 Soft silhouette on gem and river (#1097)

- `WobbleRenderer.backdropArt(fromAsset:)` gains `wobble: Bool = true`, so the parser is shared and only the displacement is skipped.
- A new `WobbleRenderer.softBackdropArt(size:polarity:)` reads the same `Outlines/<size>-<polarity>.svg` with `wobble: false`. It is memoised under `"<name>-soft"`.
- `StoneView` gets one private `silhouette`: `polarity == .lowlight ? backdropArt(…) : softBackdropArt(…)`. All four of its call sites use it: body, edge, shadow and sheen.
- The glyph stays wobbled (`StoneCarvingArt` is untouched), and so do the legacy renderers.

### B3.3 Material (#1097)

These are starting values. The final ones come from the Debug Stone lab (Profile → Settings → Developer): tune on the iPhone 15, paste the lab's **Copy** output into `StoneMaterial.starting`, then press **Reset** on the phone.

| Field | River today → start | Lava today → start | Intent (web ratio) |
|---|---|---|---|
| sheen | 0.18 → 0.06 | 0.05 (keep) | pebble specular 0.45 → 0.16 |
| lipOpacity | 0.70 → 0.46 | 0.84 (keep) | pebble edge shade 0.40 → 0.26 |
| contrast | 0.64 → 0.50 | 1 (keep) | a softer body shade |
| pits | 0 → 0.14 | 1 (keep) | grain: pebble 0.14 |
| relief / scale | keep | 3 → 3.6 / 0.05 → 0.07 | lava harsher, finer |

Two shader changes in `stone.metal`. Both use constants and the existing uniforms, so the uniform order, `StoneMaterial`'s `Codable` and the lab's saved values keep their shape:

- **River branch.**
  - The Blinn exponent goes 18 → 6, which gives a broad, dim light and no hot spot.
  - It gains a fine tooth: `col *= 1.0 - pits * (fractal(p, scale * 6.0, 1, seed + 11u) - 0.5)`.
  - River ignored `pits` until now, so the lab's "Pits" knob becomes its grain.
- **Lava branch.** The same fine-grain term at a fixed amplitude of 0.32 (the web lava grain), after the pits.

Gem is unchanged. The battery contract holds: no time input, Low Power Mode is still flat, and a still phone publishes no lean.

### B3.4 Checks

- **Unit tests:**
  - `softBackdropArt` matches the parsed asset path point for point, and is not equal to `backdropArt`;
  - `StoneView`'s silhouette choice by polarity (a pure `static func usesWobbledSilhouette(_:)`);
  - `StoneMaterialTests` updated to the pasted table, and the uniform order is unchanged.
- **References:** `nineStones` paper and Night move on purpose. Re-baseline with `rebaseline-ios-screenshots`, review the triptych in the PR, and compare it with web `/sandbox/design` LitStone.
- **Manual:**
  - the Stone lab on the iPhone 15, side by side with the web specimens;
  - the valence picker on a real pebble, in light and dark;
  - Low Power Mode on.

---

## Parts (`gh stack`, branched from updated `main`)

| Part | Branch | Labels | Done when |
|---|---|---|---|
| B1 | `feat/1158-ios-variable-ysabeau` | feat, ui, ios · M64 · `no-lab-note` | The variable roman is bundled and identical to Android's (`cmp` in CI), and the static cut is gone. `SpineType` holds every rank above. The three proofs pass: weights, small caps, lining-tabular. The welcome, record and Path titles look unchanged on the phone in EN and FR. The FR karma reasons say galet. |
| B2 | `quality/1159-ios-reference-images` | quality, ios · M64 · `no-lab-note` | The harness and its tests pass. The CI-rendered `nineStones` (+ flat) and `WelcomeReferences.slide` references are committed through the label. A deliberate one-label change turns the PR red with a diff artifact. The threshold is calibrated with at least 2× headroom, and two runs are byte-identical. The workflow and label exist. |
| B3 | `fix/1097-ios-lit-stone-materials` | fix, ui, ios · M64 · `no-lab-note` | The soft silhouette is on gem and river. The lab values are pasted. Every dead `ValenceStoneStyle` member is gone (by B3, or already by A3b), so iOS carries no red valence stop. The iOS references are re-baselined by `rebaseline-ios-screenshots`. Resolves #1097. "Part of #1143" for the iOS half. |

The order is B1 → B2 → B3. B2 renders the welcome slide with B1's face, and B3 is the gate's first real, intended diff.

Lint and test at workspace scope: `npm run lint --workspace=@pbbls/ios` and `npm run test --workspace=@pbbls/ios`.

Arkaik: none applies. No screen, route, model or endpoint moves. #1101 stays open for web and Android.

## Follow-ups outside this stack

- **Android half of #1143:** a standalone PR (labels fix, ui, android · M64 · `no-lab-note`) puts the §B3.1 tables into `ValenceMesh.inkHexes` and `ValenceMesh.selectedWashHexes`. It adds a JUnit twin of the no-red hue test and re-baselines the `ValenceStone` previews with `rebaseline-screenshots`. It resolves #1143 together with B3. On Android the red is visible today, so this PR does not wait for the stack.

## New patterns, raised in the PR

Each of these is new to `apps/ios`. Each part's PR body names its pattern for review, per "new patterns require discussion".

- **B1: the variable-font descriptor bridge (`YsabeauFace`).** One memoised `UIFont` per (size, `wght`, italic, features), built with `kCTFontVariationAttribute` and OpenType-tag feature settings, then bridged to SwiftUI as `Font(UIFont)`.
- **B1: a rank model that owns its own scaling (`SpineType.Style.scaling`).** The policies are `.text`, `.card(cap:)` and `.none`, and they are resolved from the SwiftUI environment, not from `UITraitCollection.current`.
- **B1: `Cut` as the logic-to-view handoff for type.** Logic returns a value, and views never name a weight.
- **B2: the hand-rolled reference harness.** `ImageRenderer` plus an exact-pixel diff, references read and written through `#filePath`, and record mode through `TEST_RUNNER_` environment variables.
- **B2: label-driven CI rebaseline on iOS** (`ios-references.yml`, `rebaseline-ios-screenshots`). It is the first iOS workflow that writes to the repository.
- **B2: a pinned simulator device and runtime** for the whole iOS test job.
- **B2: a two-pass locale run** (`-testLanguage fr`), where each reference declares its locales.

## Risks

- **Variable axis through `UIFontDescriptor` on iOS 17.**
  - What is known: `kCTFontVariationAttribute` with the numeric `wght` id is how Caveat Bold and the 600 italic already ship, so the path is proven.
  - What is not: whether SwiftUI keeps the variation when it bridges `Font(UIFont)` into `Text` concatenations, and how it caches across sizes.
  - Mitigation: the "mmmm" advance test checks that the axis applied, not only that it was stored. The B2 welcome reference shows any collapse to the 700 default as a diff.
  - If the bridge drops the axis, `YsabeauFace` builds a `CTFont` with `CTFontCreateCopyWithAttributes` and wraps that instead.
- **Features on the variable face.** The OpenType-tag form needs a CoreText that maps `smcp` and `c2sc` on a `gvar` font. The glyph-ID tests prove it on the CI runtime. If they fail, fall back to the AAT `type`/`selector` pairs: Lower Case 37/1, Upper Case 38/1, Number Case 21/1, Number Spacing 6/0 or 6/1.
- **Tight line heights.** Eight ranks ask for less than Ysabeau's 1.343 em, and iOS 17 `Text` has no line-height API.
  - B1 adds a probe test: a two-line `title` measured under `ImageRenderer` must come out within 1 pt of 40 × 1.343 + 40.8.
  - If negative `lineSpacing` is clamped to zero on the runtime, B1 records the measured gap in the plan's Lessons. #1130 then decides between natural spacing and a per-line layout. B1 does not guess.
- **CI simulator and Xcode drift (#793).** The image can drop 26.3 or the pinned runtime at any time. Pinning fails loudly instead of rendering on something new. Raising either pin is its own PR, which re-baselines every reference in one reviewed commit. Until #793 is diagnosed, a 26.6 move may also need its test fix.
- **`ImageRenderer` colour scheme.** The environment sets SwiftUI colours, but a `UIColor` resolved against `UITraitCollection.current` (for example `StonePalette.rgb(_:scheme:)` feeding the shader) can ignore it. `performAsCurrent` plus the scheme probe test catch this. A Night reference that comes out paper-coloured fails the probe before it is ever baselined.
- **Metal under `ImageRenderer`.** If `layerEffect` renders empty, the window fallback (D6) is built inside B2. B2 is not done until the stone reference shows material.
- **Repository weight.** At 2×, a 393-pt-wide week is about 786 × 4000 px. B2 records the PNG sizes of the first references. If one passes 1 MB, later stacks slice their tall states rather than lower the scale.
