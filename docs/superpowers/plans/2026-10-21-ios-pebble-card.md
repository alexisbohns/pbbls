# iOS Held Card (Stack E) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pressing a pebble on the iOS Spine opens **the held card** (#1146): the Spine's card in the hand, a recto (the moment) and a verso (the pebble: lockup, lit stone, domain postmarks, collection band), turned by a footer button or a drag, flying out of its slot and back (#1160). A moment with no pebble opens as a plain page whose "Not a pebble yet" place prints the card once the moment is completed. #1099 (Edit pebble keeps a pebble's sets) and #1145 (collection glyphs) are folded in first.

**Architecture:** Pure logic under `Features/Path/Card/Logic/` (`Foundation`/`CoreGraphics`, explicit `Locale` and `Calendar`) carries Android's vectors verbatim. The faces are value-driven views laid out by three small `Layout`s (`FaceStack`, `VersoStack`, `FooterLayout`) so every size is decided in one layout pass and holds under the B2 `ImageRenderer` gate. State is three `@Observable @MainActor` models (`HeldCardHostModel` in `RootView`, `HeldCardModel` and `CardTurnModel` per page), each change a value: the completion is published with the refreshed moment, the press request is a `CardFace` in the request, the print is `printingFor` mirrored to `@SceneStorage("heldCard")`. In E1–E3 the host presents the page in a `.large` sheet; E4 turns the host into a full-screen layer above `PebblesTabView` with a measured-frame FLIP (`scaleEffect` + `offset`) from stack D's `CardFrameRegistry`.

**Tech Stack:** SwiftUI (iOS 17 floor, no `#available`), `@Observable`, `Canvas` (`drawLayer`, `.destinationOut`, `resolve(Text)`), `rotation3DEffect`, `Layout`, `TimelineView`, Swift Testing, XcodeGen, SwiftLint, the B2 reference gate, `gh stack`, `arkaik-mcp`.

**Spec (the contract):** `docs/superpowers/specs/2026-10-20-ios-pebble-card-design.md`. Siblings: `2026-10-20-ios-spine-path-design.md` (stack D, which E builds on), `2026-10-20-ios-new-path-foundations-design.md` (stack B), `2026-10-20-ios-tab-navigation-design.md` (stack C), `2026-10-20-ios-m3-colour-design.md` (stack A). Names and files from stack D are those of `docs/superpowers/plans/2026-10-21-ios-spine-path.md`. Behaviour reference (never transliterated): `docs/superpowers/specs/2026-10-20-android-pebble-card-design.md`, `docs/superpowers/plans/2026-10-20-android-pebble-card.md` (Lessons learned) and `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/card/`.

---

## Fixed decisions (given; not reopened)

- **Parts**, in order, one branch = one PR, chained with `gh stack` (non-interactive: `gh stack init <branch>` / `gh stack add <branch>`, `gh stack push`, `gh stack submit --auto`, `gh stack view --json`): **E0a** #1099; **E0b** #1145; **E1** the still card (`HeldCardHost` / `HeldCardModel`, `@Observable`, `.task`, `v_moments_full` live, `@SceneStorage("heldCard")` restoring the face); **E2** the turn (Y rotation with perspective, both faces always built and laid out, the hidden face transparent, gesture-driven springs, a flick carries, a release past halfway completes, Reduce Motion = a 150 ms crossfade, the footer Turn button is VoiceOver's way to turn); **E3** the bare moment; **E4** the flight (single-hierarchy overlay with a measured-frame FLIP from `CardFrameRegistry`: `scaleEffect` + `offset`, not `matchedGeometryEffect`, not zoom, no `#available`; a device spike task comes first and its fallback is written out in full); **E5** remove the old read view.
- **Branches** `type/<issue>-ios-<slug>`: E0a uses 1099, E0b 1145, E1–E3 and E5 use 1146, E4 uses 1160. **PR bodies:** `Resolves #1099` (E0a), `Resolves #1145` (E0b), `Resolves #1160` (E4), `Resolves #1146` (E5, the part that completes it), `Part of #1146` otherwise. **Labels** from the issue plus `no-lab-note`; **milestone** `M64 · New Path`.
- **Dependencies:** stack D whole on `main` before E's stack is created from `main` (Task E0a.0 checks and stops). Own simulator "iPhone 17 stack-e" and DerivedData `Pebbles-stack-e`.
- **Standards:** colours via `Color.pebbles.*` only (`ThemeLiteralsTests`); card text via `.spineType(.rank)`; `@Observable` + `.task`; state as values (completion = state published with the refreshed moment, press request = state); `os.Logger` on every error path; the interactive back gesture respected; VoiceOver order exactly as the spec lists; 44 pt targets; Dynamic Type to the accessibility sizes without clipping; `SnapAspectCache` as the spec names it; `PeopleLines.peopleLine(_:order:locale:namedInFull:)` with `namedOnCard = 8`; EN/FR strings, no `New`/`Stale`, "galet" never "caillou"; iOS 17 floor; Swift 6 out of scope; reference images through the B2 gate, rendered on CI only (record env `PBBLS_RECORD_REFERENCES=1`, label `rebaseline-ios-screenshots`, approve held runs, threshold never widened); Arkaik through `arkaik-mcp` only, statuses to `development` at pickup, never `live`.
- **Size:** parts stay under ~1,700 changed lines; split only above that. E1 is above it (~3,800), so it ships as three parts, **E1a / E1b / E1c**; every other part fits.

## Decisions waiting on the maintainer

The spec leaves these open. Each has a recommendation, and **the plan is written to the recommendation**; the task in brackets carries it. A different answer changes only that task.

1. **The turn's fit scale.** The spec's `fitScale = 1 / (1 + sin(πt)·(w/2)/864)` was rendered on 2026-10-21 (macOS SwiftUI `ImageRenderer`, a 300 × 400 rectangle at t = 0.25): the projection assumption holds (`m34 = −perspective / max(w, h)`, near edge measured 456 pt against 455.98 predicted), but that scale leaves the near edge at **406 pt for a 400 pt card**, 1.5 % past the shell. The exact form `1 − sin(πt)·(w/2)/864` measured **400 pt** at t = 0.25 and 394 pt at t = 0.45. **Recommendation:** the exact form; `TurnPerspectiveProbeTests` (E2.1) holds the near edge to ≤ h + 1 on the simulator. [E2.1]
2. **The Highlight word's paper ramp.** The spec names "the stone's highlight body tones (`StoneTones.starting(for: .highlight)` resolved through `color(_:in:)`) top to bottom" but not which picks or stops. **Recommendation:** `lipLight`, `body`, `lipShadow` at 0, 0.4, 1 (Android's stop positions, the stone's own light-to-shadow order). [E1a.7, E1b.9]
3. **The restored card's caller.** `@SceneStorage("heldCard")` reopens a card after a background kill, but the list that opened it is gone, so its `onChanged` closure cannot be restored. **Recommendation:** a restored request's origin is `.you`, the one tab that lists no moments, so its saves, deletes and completion bump `MomentsRevision` as an outside change and Path, People and Collections all reload from it. [E1c.3, E5.1]
4. **The menu-open reference.** `PebblesMenu` is a popover, which `ImageRenderer` cannot draw. **Recommendation:** make stack A's `PebblesMenuList` `internal` (one word, `private struct` → `struct`) so the reference renders the card's items under the bar as they open. The alternative is to drop that reference and check the menu on the device only. [E1b.13]
5. **The divider before Delete.** `PebblesMenu` draws a rule between every row, so "Edit moment, Edit pebble, a divider, then Delete" already reads as rules between rows. **Recommendation:** use the shared list as it is, with no divider API and no card menu of its own (D3: "the card adds no menu of its own"). [E1b.12]
6. **#1099 and #1145 close for Android too.** `Resolves #1099` / `Resolves #1145` closes both issues, while the spec keeps their Android halves open. **Recommendation:** at each PR, file `[Bug] Android: Edit pebble drops a pebble's extra domains and collections` (labels `bug`, `android`, milestone M64) and `[Feat] Android: the collection's glyph on the card's band` (`feat`, `ui`, `android`, M64), each quoting the Android paragraph of the original, and name the new numbers in the PR body. [E0a.4, E0b.4]

## Dependencies

- **Stack D whole** (D1 → D5b, `feat/1130-ios-*` and `chore/1130-ios-remove-row-path`) on `main`. That also brings stacks A, B and C, which D required. E uses from D: `CardFace` and `StonePressRequests` (`Features/Path/Spine/StonePressRequests.swift`), `StonePress`, `StoneMotion`, `StoneRings`, `StoneRipple`, `StoneGlint`, `StoneGlintInk`, `StoneRingGeometry`, `SpineLayout`, `SpineColors`, `SnapAspectCache`, `CardFrameRegistry`, `SpineSnapSource` and `\.spineSnapSource`, `PeopleLines` / `PeopleLine` / `PeopleLineParts` / `PeopleLineView`, `SpineCard`, `SpineCardField`, `EntryTypes`, `CardSizes`, `SpineCopy`, `SpineStrings.appLocale`, `SpineCalendar.current`, `SpineDates`, `StableHash`, `SpineStoneDescription`, `SpineOpen`, `PathModel.returnFocus`, `PathSoul`, `MomentSummary.PebbleSummary.souls`, `SpineFixtureWeek`, `SpineFixturePhotos`, `Vectors`.
- From stack A: `Color.pebbles.*`, `PebblesColorRole`, `PebblesMenu` / `PebblesMenuItem` (`Components/Menus/PebblesMenu.swift`, outcome B), `ThemeLiteralsTests`. From stack B: `SpineType` (`lockupWord(_:)`, `postmarkRing(_:)`, `cardSlipName(u:cut:)`, `cardFooterGrade`, `cardFooterNote`, `cardTurnTarget`, `cardLeafDateline`, `cardLeafBody`, `cardLockupSpan`, `cardEmotion`, `cardBandName`, `bareTitle`, `placeBody`, `kicker`, `sectionLabel`), `.spineType(_:)`, `SpineType.resolve`, the reference harness (`References`, `ReferenceImage.assertMatches`, `ReferenceConfig`, `ReferenceScheme`). From stack C: `PebblesTabView`, `AppTab`, `MomentsRevision`, `RootView.resetAuthedSceneState()`, `PebbleDetailSheet(momentId:origin:onPebbleUpdated:)`.
- E0a and E0b need nothing from D, but the stack is created from a `main` that has D, so they wait with the rest.

## Re-grep before trusting line numbers

This plan was written on 2026-10-21 against `main` at `d53be9cf` (`chore(android): remove the old read view (#1154)`), before stacks A, B, C and D merged. Their code was read from `feat/921-ios-m3-roles-path` (stack A's `PebblesMenu`) and from their plans (`2026-10-20-ios-new-path-foundations.md`, `2026-10-21-ios-tab-navigation.md`, `2026-10-21-ios-spine-path.md`). Every edit below is anchored on quoted text, never on a line number. Before each Edit, `grep -n` the anchor; if it is missing or not unique, stop and report rather than guess. Where a stack's plan says it may have spelled something differently on `main` (`PebblesMenuItem.delete`, `SpineType.resolve`), the task says which `grep` decides.

## Before you start

- Work from the repo root of the checkout or worktree that holds the stack. All paths are relative to it. In a worktree, first copy `apps/ios/Config/Secrets.xcconfig` from the main checkout (git-ignored; `xcodegen generate` needs it).
- **Simulator and DerivedData.** Every iOS command block starts with these two lines, because shell state does not persist between calls:

  ```bash
  SIM=$(xcrun simctl list devices | grep "iPhone 17 stack-e" | grep -oE '[0-9A-F]{8}(-[0-9A-F]{4}){3}-[0-9A-F]{12}' | head -1)
  DD="$HOME/Library/Developer/Xcode/DerivedData/Pebbles-stack-e"
  ```

  Task E0a.0 creates the simulator. Use `-destination "id=$SIM"` with **double** quotes. To clear stale objects, delete `$DD/Build` only, never `~/Library/Developer/Xcode/DerivedData` as a whole (another stack's run lives there).
- **Named commands** (each starts with the two lines above):
  - **Suite command** for `<Suite>`:
    `cd apps/ios && xcodegen generate && TEST_RUNNER_TZ=Europe/Paris xcodebuild test -scheme Pebbles -destination "id=$SIM" -derivedDataPath "$DD" -only-testing:PebblesTests/<Suite> -quiet`
  - **Full suite** (trusted, clears stale objects first):
    `cd apps/ios && xcodegen generate && rm -rf "$DD/Build" && TEST_RUNNER_TZ=Europe/Paris xcodebuild test -scheme Pebbles -destination "id=$SIM" -derivedDataPath "$DD" -quiet`
    Locally, the `References` suites fail for any reference CI has not recorded yet ("no reference at …"). Judge the full suite by everything else; the reference suites are judged on CI.
  - **Build**:
    `cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination "id=$SIM" -derivedDataPath "$DD" build -quiet`
  - **Lint**: `npm run lint --workspace=@pbbls/ios` (SwiftLint; errors fail CI, `line_length` warns at 120: wrap a pasted long line).
  - **Look** at `<Suite>` (a local render for your eyes only, never committed):
    `cd apps/ios && xcodegen generate && TEST_RUNNER_TZ=Europe/Paris TEST_RUNNER_PBBLS_RECORD_REFERENCES=1 xcodebuild test -scheme Pebbles -destination "id=$SIM" -derivedDataPath "$DD" -only-testing:PebblesTests/References/<Suite> -quiet`
    Then read the PNGs under `apps/ios/PebblesTests/References/__References__/<Suite>/` (the Read tool shows images), and throw them away:
    `cd apps/ios && git clean -fdq -- PebblesTests/References/__References__/<Suite> && (git checkout -q -- PebblesTests/References/__References__ 2>/dev/null || true)`
    After it, `git status --short apps/ios/PebblesTests/References/__References__` must print nothing.
- **Rebaseline on CI** (every visual part; add the label alone and approve the held runs):

  ```bash
  git fetch origin && git merge --ff-only "origin/$(git branch --show-current)" 2>/dev/null || true
  gh pr edit <N> --add-label rebaseline-ios-screenshots
  RUN=$(gh run list --workflow ios-references.yml --branch "$(git branch --show-current)" --limit 1 --json databaseId -q '.[0].databaseId')
  gh run watch "$RUN" --exit-status
  git pull --ff-only
  for id in $(gh run list --branch "$(git branch --show-current)" --status action_required --json databaseId -q '.[].databaseId'); do
    gh api -X POST "repos/alexisbohns/pbbls/actions/runs/$id/approve" || echo "approve $id in the Actions UI"
  done
  gh pr view <N> --json labels -q '.labels[].name' | grep -q rebaseline-ios-screenshots && gh pr edit <N> --remove-label rebaseline-ios-screenshots
  ```

  Then open the bot commit (`test(ios): re-baseline reference images`) and look at every new PNG against the Android preview the task names (`apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/features/path/card/`). Never re-run the failed run (a re-run replays the old SHA). If a lower branch is re-baselined after an upper one, the upper branch wins PNG conflicts on rebase.
- **Strings** go into `apps/ios/Pebbles/Resources/Localizable.xcstrings` as text at an anchor, never by re-serialising the file (no `json.dump`, no `jq`). After each edit `git diff --stat` on the catalogue shows only insertions. Before each PR that touches strings, open the catalogue in Xcode and confirm no entry is `New` or `Stale` and that every `card.*` / `bare.place.*` row has both `en` and `fr`.
- **Colours:** only `Color.pebbles.<role>` in new code under `Features/` and `Components/`. The emotion palette (`StonePalette`, `EmotionPalette`) is pebble data, read as `palette.dark` etc., always qualified. Never `.white`, `.black`, `.red` or a hex: `ThemeLiteralsTests` fails.
- **Type:** card text only through `.spineType(.<rank>)`. Never `.font`, `.bold()` or `.fontWeight()` on card text. Images (SF Symbols) take a frame, not a font.
- **PR metadata:** see each part's PR task. Never a Kritik `F-…` id. No `## Lab Note` section (Arkaik coverage replaces it). Do not add `Co-Authored-By` lines from this plan; the executing session appends its own attribution.
- **Push** only after `git fetch origin` and a fast-forward of the branch, with `gh stack push` then `gh stack submit --auto`, then `gh stack view --json` to read the PR numbers.
- **Arkaik** is changed only through `arkaik-mcp`. Never touch `docs/arkaik/bundle.json` or `journal.jsonl`. If the tools do not load, say so and skip that step (record it in the PR body). Never set `live`.
- **The device** is the maintainer's iPhone 15 on their real account (`project_ios_device_deploy` memory note for the build/install/launch recipe). Ask before any write there: completing, editing or deleting a pebble. Reading, turning, opening and closing are fine.

## Parts

| # | Branch | PR title | Contents | Est. changed lines |
|---|---|---|---|---|
| E0a | `fix/1099-ios-edit-pebble-keeps-domains` | `fix(ios): edit pebble keeps a pebble's domains and collections` | D8: `CompleteDraft.Prefill`, the absent-key payload, tests; Arkaik pickup | ~230 |
| E0b | `feat/1145-ios-collection-glyph-band-data` | `feat(ios): read each collection's glyph into the reference data` | D9 data half: the select embeds `glyphs`, `PebbleCollection.glyph`, `CollectionMark` | ~180 |
| E1a | `feat/1146-ios-card-logic` | `feat(ios): the held card's logic and strings` | `CardLayout`, `RingFit`, `ValenceLockup`, `PostmarkNoise`, `BandLines`, `FooterArrangement`, `CardDateline`, `CardColors`, `MomentSummary(detail:)`, `Visibility.explanation`, the card's people line, 10 strings, `CardStrings`, tests | ~1,250 (catalogue JSON ~210, vectors ~520) |
| E1b | `feat/1146-ios-card-faces` | `feat(ios): the held card's recto, verso and footer` | `SpineCard` split, the face's slots, `CardLeaf`, `CardSlip`, `CardRecto`, `DomainPostmark`, `PostmarkRow`, `CollectionBand`, `FaceMiniature`, `CardFooter`, `VersoStone`, `CardVerso`, `FaceStack`, minimal `CardTurnModel`, `PebbleCard`, `CardTopBar`, `HeldCardMenu`, `PebblesMenuList` internal, the still, references | ~1,650 (the split moves ~250 lines) |
| E1c | `feat/1146-ios-held-card` | `feat(ios): the pebble detail becomes the held card` | `HeldCardRequest`, `HeldCardSnapshot`, `HeldCardModel`, `HeldCardHostModel`, `HeldCardPage`, the sheet wrapper and `HeldCardHost`, `RootView` scene storage, the three callers, device check | ~900 |
| E2 | `feat/1146-ios-card-turn` | `feat(ios): turn the held card by hand` | the perspective probe, `TurnSettle`, `TurnGeometry`, the full `CardTurnModel`, `CardTurnEffect`, the drag, Reduce Motion, `HeldCardRequest(_:origin:onChanged:)`, the pan fallback written out, references | ~1,050 (+~150 if E2.8 runs) |
| E3 | `feat/1146-ios-bare-moment` | `feat(ios): the bare moment and the card it prints` | `BareMomentPage`, `CompletionMotion`, `PrintPart`, `PrintStart`, completion state, `CardPrint`, restore cut short, the announcement, references | ~1,150 |
| E4 | `feat/1160-ios-card-flight` | `feat(ios): the held card flies out of its slot and back` | the spike, `CardFlight`, `FlightGeometry`, flight state, `FlightEffect`, the overlay host, the edge swipe, VoiceOver modality and return, the seeded first frame, the UIKit fallback written out, decision log | ~1,100 on a go (+~330 on a no-go) |
| E5 | `feat/1146-ios-remove-read-view` | `feat(ios): one held card, the read view gone` | lists reload from `MomentsRevision`, `PebbleDetailSheet` and `Features/Path/Read/` deleted with two tests and their strings, docs, lessons | ~-1,100 (deletions) / +60 |

## File map

App paths under `apps/ios/Pebbles/`, test paths under `apps/ios/PebblesTests/`. `Card/` is `Features/Path/Card/`.

| File | Part | Responsibility |
|---|---|---|
| `Features/Complete/CompleteDraft.swift` | E0a | `Prefill`, set by `init(from:)` |
| `Features/Path/Models/CompletePebblePayload.swift` | E0a | Untouched sets omitted (absent means keep) |
| `Features/Path/Models/PebbleCollection.swift` | E0b | `glyph: Glyph?`, tolerant decode |
| `Services/ReferenceDataService.swift` | E0b | `collectionsSelect` embeds `glyphs` |
| `Card/CollectionMark.swift` (new) | E0b | A collection's glyph, or the stack mark |
| `Card/Logic/CardLayout.swift` (new) | E1a | All of the card's geometry |
| `Card/Logic/RingFit.swift`, `ValenceLockup.swift`, `PostmarkNoise.swift`, `BandLines.swift`, `FooterArrangement.swift`, `CardDateline.swift` (new) | E1a | Pure logic, Android's vectors |
| `Card/CardColors.swift` (new) | E1a | Postmark ink slot, band paper, colour mix |
| `Card/CardStrings.swift` (new) | E1a | The 10 `card.*` / `bare.place.*` resources |
| `Features/Path/Models/MomentSummary+Detail.swift` (new) | E1a | A detail as the Spine's summary |
| `Features/Path/Models/Visibility.swift`, `Features/Complete/Steps/CompletePrivacyStep.swift` | E1a | `explanation`, one source |
| `Resources/Localizable.xcstrings` | E1a, E5 | The new keys; E5 drops orphans |
| `Features/Path/Spine/Feed/SpineCard.swift`, `SpineCardShell.swift`, `SpineCardFace.swift` (new) | E1b | The split, then the slots |
| `Card/CardLeaf.swift`, `CardSlip.swift`, `CardRecto.swift`, `DomainPostmark.swift`, `PostmarkRow.swift`, `CollectionBand.swift`, `FaceMiniature.swift`, `CardFooter.swift`, `VersoStone.swift`, `CardVerso.swift`, `FaceStack.swift`, `PebbleCard.swift`, `CardTopBar.swift`, `HeldCardMenu.swift` (new) | E1b | The faces, the footer, the card |
| `Card/CardTurnModel.swift` (new) | E1b, E2 | The committed face (E1b), then the turn (E2) |
| `Card/Debug/HeldCardStill.swift` (new) | E1b → E3 | The card without a `ScrollView`, for the references and the lab |
| `Components/Menus/PebblesMenu.swift` | E1b | `PebblesMenuList` internal (decision 4) |
| `Card/HeldCardRequest.swift`, `HeldCardSnapshot.swift`, `HeldCardModel.swift`, `HeldCardHostModel.swift`, `HeldCardPage.swift`, `HeldCardHost.swift` (new) | E1c → E4 | The page, its state, its host |
| `Features/Path/PebbleDetailSheet.swift` | E1c, E5 | The interim wrapper, then deleted |
| `RootView.swift` | E1c, E4 | Host, scene storage, reset; the overlay |
| `Features/Path/PathView.swift`, `Features/Profile/Views/SoulDetailView.swift`, `CollectionDetailView.swift` | E1c, E2, E4, E5 | Open through the host |
| `Services/MomentsRevision.swift` | E1c | Doc comment: `HeldCardModel` bumps |
| `Card/Logic/TurnSettle.swift`, `TurnGeometry.swift` (new) | E2 | The drag's arithmetic |
| `Card/CardTurnEffect.swift`, `Card/TurnPanFallback.swift` (new) | E2 | The rotation; the pan fallback |
| `Card/BareMomentPage.swift`, `Card/CardPrint.swift`, `Card/Logic/CompletionMotion.swift`, `Card/Logic/PrintStart.swift` (new) | E3 | The bare page and the print |
| `Card/Logic/CardFlight.swift`, `FlightGeometry.swift`, `Card/FlightEffect.swift`, `Card/CardPlaceholder.swift` (new) | E4 | The flight |
| `Card/Fallback/HeldCardTransition.swift` (new, only on a no-go) | E4 | Option 4, the UIKit animator |
| `docs/decisions/log.md`, `apps/ios/CLAUDE.md` | E4, E5 | Decision; the host in the agent context |
| `Features/Path/Read/*` (10 files), `PebblesTests/BannerAspectTests.swift`, `PebblesTests/PebblePageColorsTests.swift` | E5 | Deleted |
| `PebblesTests/ThemeLiteralsTests.swift` | E5 | Drops `PebbleSnapFrame.swift`'s allowlist entry |
| `PebblesTests/Card/*.swift` (new) | E0a → E5 | Logic and model tests, fixtures |
| `PebblesTests/References/HeldCard*References.swift` (new) | E1b → E3 | Reference suites |

---
## Part E0a — `fix/1099-ios-edit-pebble-keeps-domains`

PR title: `fix(ios): edit pebble keeps a pebble's domains and collections`. Body opens `Resolves #1099`. No view changes. Spec D8.

### Task E0a.0: Preflight, simulator, stack init, Arkaik pickup

**Files:** none in the repo.

- [ ] **Step 1: Check that stack D is on `main`**

```bash
git fetch origin
git checkout main
git pull --ff-only
git log --oneline -1 main
for f in Spine/CardFrameRegistry.swift Spine/SpineOpen.swift Spine/StonePressRequests.swift Spine/SnapAspectCache.swift \
         Spine/Feed/SpineCard.swift Spine/Feed/SpineCardField.swift Spine/Logic/PeopleLines.swift PathModel.swift; do
  test -f "apps/ios/Pebbles/Features/Path/$f" && echo "ok $f" || echo "MISSING $f"
done
test ! -f apps/ios/Pebbles/Features/Path/Components/WeekPathView.swift && echo "D5b present" || echo "MISSING D5b"
grep -q "var returnFocus: UUID?" apps/ios/Pebbles/Features/Path/PathModel.swift && echo "D3 focus present"
grep -q "static let namedOnCard = 8" apps/ios/Pebbles/Features/Path/Spine/Logic/PeopleLines.swift && echo "namedOnCard present"
grep -q "struct PebblesMenuList" apps/ios/Pebbles/Components/Menus/PebblesMenu.swift && echo "A2 menu present"
grep -q "static func lockupWord" apps/ios/Pebbles/Theme/SpineType.swift && echo "B1 ranks present"
grep -q "func resetAuthedSceneState" apps/ios/Pebbles/RootView.swift && echo "C reset present"
grep -n "PebbleDetailSheet(" apps/ios/Pebbles/Features/Path/PathView.swift
```

Expected: eight `ok` lines, "D5b present", "D3 focus present", "namedOnCard present", "A2 menu present", "B1 ranks present", "C reset present", and one `PebbleDetailSheet(momentId: id, origin: .path, onPebbleUpdated:` line. **If any check fails, stop and report "waiting on stack D" with the failing line.** Do not start the stack.

- [ ] **Step 2: Create the simulator if it is missing**

```bash
xcrun simctl list devices | grep -q "iPhone 17 stack-e" || xcrun simctl create "iPhone 17 stack-e" "iPhone 17"
SIM=$(xcrun simctl list devices | grep "iPhone 17 stack-e" | grep -oE '[0-9A-F]{8}(-[0-9A-F]{4}){3}-[0-9A-F]{12}' | head -1)
echo "$SIM"
xcrun simctl boot "$SIM" 2>/dev/null || true
mkdir -p "$HOME/Library/Developer/Xcode/DerivedData/Pebbles-stack-e"
```

Expected: one UDID prints.

- [ ] **Step 3: Init the stack**

```bash
git config rerere.enabled true
git config remote.pushDefault origin
gh stack init fix/1099-ios-edit-pebble-keeps-domains
git branch --show-current
```

Expected: `fix/1099-ios-edit-pebble-keeps-domains`.

- [ ] **Step 4: Baseline**

Run the **Full suite** and **Lint**. Expected: green (every reference suite on `main` passes, since CI recorded them). If `main` is red, stop and report; do not fix `main` inside this stack.

- [ ] **Step 5: Move the card's nodes to `ios: development` in Arkaik**

Load the tools: ToolSearch `select:mcp__arkaik-mcp__get_node,mcp__arkaik-mcp__update_node`. If they do not load, say so, skip this step and record it for the E0a PR body. Never fall back to the bundle file.

For each of `AC-hold-a-pebble-like-a-card-and-turn-it-over` and `V-pebble-detail`:

1. `get_node(node_id)`. Keep the returned `status`, `platforms` and the whole `metadata` object (gherkin, values, `platformStatuses`, anything else).
2. `update_node` with `patch.metadata` = that **whole** object with its `platformStatuses.ios` key set to `"development"`. `update_node` replaces metadata wholesale, so every other key goes back unchanged. If the metadata has no `platformStatuses` key, send `{"platformStatuses": {<each platform in platforms>: <the node's status>, "ios": "development"}}` merged into the returned metadata.
3. Do not patch the top-level `status`.
4. `get_node` again: `ios` reads `development`; the gherkin, the values and the other platforms are byte-identical to step 1.

If the validator refuses a patch, stop and report the refusal verbatim.

---

### Task E0a.1: `CompleteDraft.Prefill`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteDraft.swift`
- Test: `apps/ios/PebblesTests/Features/Complete/CompleteDraftTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1099 D8: Edit pebble remembers what it prefilled, so the payload can tell
/// an untouched field from a changed one. The Complete flow never sets it.
@Suite("CompleteDraft prefill")
struct CompleteDraftTests {
    @Test("init(from:) records the first domain and the first collection it prefilled")
    func prefilled() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        let draft = CompleteDraft(from: try #require(detail.pebble))
        #expect(draft.prefill == CompleteDraft.Prefill(
            domainId: MomentFixtures.uuid(MomentFixtures.domainId),
            collectionId: MomentFixtures.uuid(MomentFixtures.collectionId)
        ))
    }

    @Test("a legacy pebble with no domain and no collection prefills nils, not nothing")
    func legacy() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.legacyMoment)
        let draft = CompleteDraft(from: try #require(detail.pebble))
        #expect(draft.prefill == CompleteDraft.Prefill(domainId: nil, collectionId: nil))
    }

    @Test("a fresh draft, the Complete flow's, has no prefill")
    func fresh() {
        #expect(CompleteDraft().prefill == nil)
    }

    @Test("isEmpty ignores the prefill")
    func emptiness() {
        var draft = CompleteDraft()
        draft.prefill = CompleteDraft.Prefill(domainId: UUID(), collectionId: UUID())
        #expect(draft.isEmpty)
    }
}
```

Save it as `apps/ios/PebblesTests/Features/Complete/CompleteDraftTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CompleteDraftTests`. Expected: build FAIL with "type 'CompleteDraft' has no member 'Prefill'".

- [ ] **Step 3: Implement**

In `apps/ios/Pebbles/Features/Complete/CompleteDraft.swift`, replace

```swift
struct CompleteDraft: Equatable {
    var valence: Valence?
```

with

```swift
struct CompleteDraft: Equatable {
    /// What Edit pebble prefilled (#1099 D8): the pebble's first domain and its
    /// first collection. `CompletePebblePayload` omits a set the user left as
    /// prefilled, so `complete_pebble` keeps the pebble's whole set.
    struct Prefill: Equatable {
        let domainId: UUID?
        let collectionId: UUID?
    }

    var valence: Valence?
```

then replace

```swift
    var visibility: Visibility = .secret

    var isComplete: Bool { valence != nil && emotionId != nil && domainId != nil }
```

with

```swift
    var visibility: Visibility = .secret
    /// Set only by `init(from:)`; nil in the Complete flow, so a first
    /// completion sends both sets. Never counts toward `isEmpty`.
    var prefill: Prefill?

    var isComplete: Bool { valence != nil && emotionId != nil && domainId != nil }
```

then replace

```swift
        collectionId = pebble.collections.first?.id
        visibility = pebble.visibility
    }
```

with

```swift
        collectionId = pebble.collections.first?.id
        visibility = pebble.visibility
        prefill = Prefill(domainId: domainId, collectionId: collectionId)
    }
```

`isEmpty` lists its fields by name, so it already ignores `prefill`.

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `CompleteDraftTests`. Expected: PASS (4).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Complete/CompleteDraft.swift apps/ios/PebblesTests/Features/Complete/CompleteDraftTests.swift
git commit -m "fix(ios): remember what edit pebble prefilled"
```

---

### Task E0a.2: An untouched set is omitted

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/CompletePebblePayload.swift`
- Modify: `apps/ios/PebblesTests/Moments/CompleteDraftFromPebbleTests.swift`
- Test: `apps/ios/PebblesTests/Features/Complete/CompletePebblePayloadTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1099 D8: `complete_pebble` replaces `domain_ids` and `collection_ids` only
/// when the key is present (`private.jsonb_has`,
/// `20261012090100_moments_split_writers.sql`). An Edit pebble field left as
/// prefilled is omitted, so a pre-split pebble keeps its three domains and two
/// collections; a changed field collapses its set to the one pick.
@Suite("CompletePebblePayload keeps untouched sets")
struct CompletePebblePayloadTests {
    static let momentId = "3eaf6c81-5074-4db1-82e9-4f5a6b7c8d9e"
    static let domains = ["33333333-3333-4333-8333-333333333331", "33333333-3333-4333-8333-333333333332",
                          "33333333-3333-4333-8333-333333333333"]
    static let collections = ["55555555-5555-4555-8555-555555555551", "55555555-5555-4555-8555-555555555552"]

    /// A pre-split pebble: three domains, two collections, as `v_moments_full` sends it.
    static let multiSet = Data("""
    {
      "id": "\(momentId)", "user_id": "5f0e1d2c-3b4a-4958-8776-655443322110",
      "name": "Shipped the thing", "description": null,
      "happened_at": "2026-09-29T16:00:00+00:00", "hidden_at": null,
      "created_at": "2026-09-29T16:02:44.9+00:00", "updated_at": "2026-10-12T09:00:00+00:00",
      "snaps": [],
      "pebble": {
        "id": "\(momentId)", "intensity": 3, "positiveness": 1, "visibility": "private",
        "emotion": {"id": "22222222-2222-4222-8222-222222222222", "slug": "joy", "name": "Joy"},
        "glyph": null, "souls": [],
        "domains": [
          {"id": "\(domains[0])", "slug": "zoe", "name": "Work"},
          {"id": "\(domains[1])", "slug": "amis", "name": "Friends"},
          {"id": "\(domains[2])", "slug": "corps", "name": "Body"}
        ],
        "collections": [
          {"id": "\(collections[0])", "name": "Autumn", "glyph_id": null},
          {"id": "\(collections[1])", "name": "Wins", "glyph_id": null}
        ]
      }
    }
    """.utf8)

    private func editDraft() throws -> CompleteDraft {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: Self.multiSet)
        return CompleteDraft(from: try #require(detail.pebble))
    }

    private func json(_ payload: CompletePebblePayload) throws -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return String(decoding: try encoder.encode(payload), as: UTF8.self)
    }

    private var moment: UUID { MomentFixtures.uuid(Self.momentId) }

    @Test("an edit that touches neither set omits both keys")
    func untouched() throws {
        var draft = try editDraft()
        draft.emotionId = UUID()
        let payload = try #require(CompletePebblePayload(momentId: moment, draft: draft))
        #expect(payload.domainIds == nil && payload.collectionIds == nil)
        let text = try json(payload)
        #expect(!text.contains("domain_ids"))
        #expect(!text.contains("collection_ids"))
        #expect(text.contains("\"emotion_id\""))
    }

    @Test("another domain collapses the set to that one, and leaves the collections alone")
    func changedDomain() throws {
        var draft = try editDraft()
        let other = UUID()
        draft.domainId = other
        let payload = try #require(CompletePebblePayload(momentId: moment, draft: draft))
        #expect(payload.domainIds == [other])
        #expect(payload.collectionIds == nil)
        #expect(try json(payload).contains("\"domain_ids\":[\"\(other.uuidString.lowercased())\"]"))
    }

    @Test("a cleared collection sends an empty set")
    func clearedCollection() throws {
        var draft = try editDraft()
        draft.collectionId = nil
        let payload = try #require(CompletePebblePayload(momentId: moment, draft: draft))
        #expect(payload.collectionIds == [])
        #expect(try json(payload).contains("\"collection_ids\":[]"))
    }

    @Test("another collection replaces the set with that one")
    func changedCollection() throws {
        var draft = try editDraft()
        let other = UUID()
        draft.collectionId = other
        let payload = try #require(CompletePebblePayload(momentId: moment, draft: draft))
        #expect(payload.collectionIds == [other])
        #expect(payload.domainIds == nil)
    }

    @Test("a first completion, with no prefill, sends both keys")
    func firstCompletion() throws {
        var draft = CompleteDraft()
        draft.valence = .neutralMedium
        draft.emotionId = UUID()
        draft.domainId = UUID()
        let payload = try #require(CompletePebblePayload(momentId: moment, draft: draft))
        let text = try json(payload)
        #expect(text.contains("\"domain_ids\":["))
        #expect(text.contains("\"collection_ids\":[]"))
    }
}
```

Save it as `apps/ios/PebblesTests/Features/Complete/CompletePebblePayloadTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CompletePebblePayloadTests`. Expected: FAIL in `untouched` (`payload.domainIds == nil` is false: `[33333333-…31]`), `changedDomain` and `changedCollection`; `clearedCollection` and `firstCompletion` pass.

- [ ] **Step 3: Implement**

In `apps/ios/Pebbles/Features/Path/Models/CompletePebblePayload.swift`, replace

```swift
        self.domainIds = [domainId]
        self.soulIds = draft.soulIds
        self.collectionIds = draft.collectionId.map { [$0] } ?? []
    }
```

with

```swift
        self.soulIds = draft.soulIds
        // #1099 D8: a set Edit pebble left as prefilled is omitted, and absent
        // means keep: `complete_pebble` replaces a set only when its key is
        // present. A pre-split pebble keeps every domain and collection through
        // an edit of its emotion; touching the field collapses it to the pick.
        if let prefill = draft.prefill, prefill.domainId == domainId {
            self.domainIds = nil
        } else {
            self.domainIds = [domainId]
        }
        if let prefill = draft.prefill, prefill.collectionId == draft.collectionId {
            self.collectionIds = nil
        } else {
            self.collectionIds = draft.collectionId.map { [$0] } ?? []
        }
    }
```

Update the type's doc comment: replace

```swift
/// one pebble on `momentId`, inserted or updated. Keys other than
/// `moment_id` are omitted when nil (null means "keep"). iOS never sends
```

with

```swift
/// one pebble on `momentId`, inserted or updated. Keys other than
/// `moment_id` are omitted when nil (absent means "keep"; Edit pebble omits
/// the sets the user left as prefilled, #1099). iOS never sends
```

- [ ] **Step 4: Update the prefill payload test**

`CompleteDraftFromPebbleTests.payload` asserted the old collapse. In `apps/ios/PebblesTests/Moments/CompleteDraftFromPebbleTests.swift`, replace

```swift
    @Test("the prefilled draft becomes a complete_pebble payload keeping one domain and one collection (A2)")
    func payload() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        let draft = CompleteDraft(from: try #require(detail.pebble))
        let payload = try #require(CompletePebblePayload(momentId: detail.id, draft: draft))
        #expect(payload.momentId == detail.id)
        #expect(payload.domainIds == [MomentFixtures.uuid(MomentFixtures.domainId)])
        #expect(payload.collectionIds == [MomentFixtures.uuid(MomentFixtures.collectionId)])
        #expect(payload.visibility == .private)
    }
```

with

```swift
    @Test("the untouched prefilled draft keeps the pebble's sets by omitting them (#1099)")
    func payload() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        let draft = CompleteDraft(from: try #require(detail.pebble))
        let payload = try #require(CompletePebblePayload(momentId: detail.id, draft: draft))
        #expect(payload.momentId == detail.id)
        #expect(payload.domainIds == nil)
        #expect(payload.collectionIds == nil)
        #expect(payload.visibility == .private)
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run the **Suite command** with `CompletePebblePayloadTests`, then `CompleteDraftFromPebbleTests`, then `CompletePebblePayloadFromDraftTests`. Expected: PASS (5), PASS (3), PASS (4).

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/CompletePebblePayload.swift apps/ios/PebblesTests/Features/Complete/CompletePebblePayloadTests.swift apps/ios/PebblesTests/Moments/CompleteDraftFromPebbleTests.swift
git commit -m "fix(ios): edit pebble omits the sets it left untouched"
```

---

### Task E0a.3: Device check (the orchestrator runs it with the maintainer)

**Files:** none.

- [ ] **Step 1: Ask, then edit a pre-split pebble**

This writes to the maintainer's account: ask first. On the iPhone 15 (`project_ios_device_deploy` recipe), open a pebble recorded before the moments split that has three domains and two collections (the maintainer names one; web shows its sets). Edit pebble → change only its emotion → Save. Reopen it on web: the three domains and two collections are all there. Then Edit pebble → pick another domain → Save: web shows that one domain and both collections. Record the pebble's name (not its id) and the result for the PR body.

---

### Task E0a.4: E0a verification and PR

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**. Expected: green.

- [ ] **Step 2: Push and submit**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
```

- [ ] **Step 3: File the Android half (decision 6)**

```bash
gh issue create --title "[Bug] Android: Edit pebble drops a pebble's extra domains and collections" \
  --label bug,android --milestone "M64 · New Path" \
  --body "Split from #1099, which the iOS fix closes. Android \`core/model/MomentPayloads.kt\` still sends \`listOf(domainId)\` and \`listOfNotNull(collectionId)\` on every Edit pebble save, so \`complete_pebble\` replaces both sets. The iOS fix (stack E, part E0a) omits a set the user left as prefilled: absent means keep (\`private.jsonb_has\`)."
```

Note the new issue number.

- [ ] **Step 4: Title, body, labels, milestone**

Body (scratch file): `Resolves #1099`; key files (`CompleteDraft.swift`, `CompletePebblePayload.swift`, the two test files); D8 in two sentences (prefill recorded by `init(from:)`, an untouched set omitted, absent means keep); that `CompleteDraftFromPebbleTests.payload` now expects the omission; the device result from E0a.3; the Android follow-up by its new issue number; the Arkaik outcome from E0a.0. Labels: #1099 carries `bug`, `ios`, `android`; this PR is iOS-only, so `fix` (bug → fix), `ios` and `no-lab-note`. No Lab Note, no `F-…` ids.

```bash
gh pr edit <E0a-number> --title "fix(ios): edit pebble keeps a pebble's domains and collections" --body-file <scratch-body-file> \
  --add-label fix,ios,no-lab-note --milestone "M64 · New Path"
```

---
## Part E0b — `feat/1145-ios-collection-glyph-band-data`

PR title: `feat(ios): read each collection's glyph into the reference data`. Body opens `Resolves #1145`. Spec D9's data half; E1b's band draws it.

### Task E0b.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1145-ios-collection-glyph-band-data
git branch --show-current
```

Expected: `feat/1145-ios-collection-glyph-band-data`.

---

### Task E0b.1: `PebbleCollection.glyph`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleCollection.swift`
- Test: `apps/ios/PebblesTests/Card/PebbleCollectionDecodingTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1145 D9: the reference cache's collections carry their glyph, embedded as
/// the souls query embeds theirs; a collection without one decodes as before.
@Suite("PebbleCollection decoding")
struct PebbleCollectionDecodingTests {
    private func decode(_ json: String) throws -> PebbleCollection {
        try JSONDecoder().decode(PebbleCollection.self, from: Data(json.utf8))
    }

    @Test("the embedded glyph decodes with its strokes")
    func withGlyph() throws {
        let collection = try decode("""
        {"id": "55555555-5555-4555-8555-555555555555", "name": "Autumn",
         "glyph_id": "66666666-6666-4666-8666-666666666666",
         "glyphs": {"id": "66666666-6666-4666-8666-666666666666", "name": "Autumn",
                    "strokes": [{"d": "M 20,100 Q 100,20 180,100", "width": 6}], "view_box": "0 0 200 200"}}
        """)
        #expect(collection.name == "Autumn")
        #expect(collection.glyph?.strokes.count == 1)
        #expect(collection.glyph?.viewBox == "0 0 200 200")
    }

    @Test("a null glyph_id embeds null: no glyph")
    func nullGlyph() throws {
        let collection = try decode("""
        {"id": "99999999-9999-4999-8999-999999999999", "name": "Wins", "glyph_id": null, "glyphs": null}
        """)
        #expect(collection.glyph == nil)
    }

    @Test("a row from the old select, with no embed at all, still decodes")
    func oldSelect() throws {
        let collection = try decode(#"{"id": "99999999-9999-4999-8999-999999999999", "name": "Wins"}"#)
        #expect(collection.glyph == nil)
        #expect(collection.name == "Wins")
    }

    @Test("the memberwise initialiser defaults to no glyph")
    func memberwise() {
        #expect(PebbleCollection(id: UUID(), name: "Therapy").glyph == nil)
    }
}
```

Save it as `apps/ios/PebblesTests/Card/PebbleCollectionDecodingTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `PebbleCollectionDecodingTests`. Expected: build FAIL with "value of type 'PebbleCollection' has no member 'glyph'".

- [ ] **Step 3: Implement**

Replace the contents of `apps/ios/Pebbles/Features/Path/Models/PebbleCollection.swift` with:

```swift
import Foundation

/// A collection as the reference cache holds it. Its glyph is embedded the way
/// the souls query embeds a soul's (`glyphs(id, name, strokes, view_box)`,
/// #1145 D9), optional because `collections.glyph_id` is nullable and a row
/// from an older select has no embed. The held card's band draws it.
struct PebbleCollection: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let glyph: Glyph?

    init(id: UUID, name: String, glyph: Glyph? = nil) {
        self.id = id
        self.name = name
        self.glyph = glyph
    }

    private enum CodingKeys: String, CodingKey {
        case id, name
        case glyph = "glyphs"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        glyph = try container.decodeIfPresent(Glyph.self, forKey: .glyph)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `PebbleCollectionDecodingTests`, then `SignedOutPurgeTests` (it builds a `PebbleCollection(id:name:)`). Expected: PASS (4), PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleCollection.swift apps/ios/PebblesTests/Card/PebbleCollectionDecodingTests.swift
git commit -m "feat(ios): a reference collection carries its glyph"
```

---

### Task E0b.2: The collections select embeds the glyph

**Files:**
- Modify: `apps/ios/Pebbles/Services/ReferenceDataService.swift`
- Test: `apps/ios/PebblesTests/Card/ReferenceDataServiceTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
@testable import Pebbles

/// #1145 D9: both of the cache's collection reads ask for the glyph, in the
/// souls query's shape. A single-table read with an embed, so no RPC.
@Suite("ReferenceDataService collections select")
struct ReferenceDataServiceTests {
    @Test("the select embeds the glyph's strokes and view box")
    func select() {
        #expect(ReferenceDataService.collectionsSelect == "id, name, glyph_id, glyphs(id, name, strokes, view_box)")
    }
}
```

Save it as `apps/ios/PebblesTests/Card/ReferenceDataServiceTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `ReferenceDataServiceTests`. Expected: build FAIL with "type 'ReferenceDataService' has no member 'collectionsSelect'".

- [ ] **Step 3: Implement**

In `apps/ios/Pebbles/Services/ReferenceDataService.swift`:

1. Before `    private(set) var souls: [SoulWithGlyph] = []`, add:

```swift
    /// The collections' columns (#1145 D9): the glyph embedded as the souls
    /// query embeds a soul's, so the held card's band can draw it.
    nonisolated static let collectionsSelect = "id, name, glyph_id, glyphs(id, name, strokes, view_box)"

```

2. `grep -n '\.select("id, name")' apps/ios/Pebbles/Services/ReferenceDataService.swift` must print exactly two lines (the load and `refreshCollections`). Replace both (Edit with `replace_all`)

```swift
                .select("id, name")
```

with

```swift
                .select(Self.collectionsSelect)
```

If the class is not `@MainActor`, drop the `nonisolated` keyword (the build says "'nonisolated' can not be applied").

- [ ] **Step 4: Run the tests to verify they pass**

Run the **Suite command** with `ReferenceDataServiceTests`, then **Build**. Expected: PASS (1), `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Services/ReferenceDataService.swift apps/ios/PebblesTests/Card/ReferenceDataServiceTests.swift
git commit -m "feat(ios): the reference collections embed their glyphs"
```

---

### Task E0b.3: `CollectionMark`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/CollectionMark.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI

/// A collection's mark on the held card's band (#1145 D9): its glyph when the
/// reference cache has one, else the generic stack mark (Android's
/// `ic_pebble_collection`). Decorative: the band's label names the collection.
struct CollectionMark: View {
    let glyph: Glyph?
    let ink: Color
    var side: CGFloat = 14

    var body: some View {
        Group {
            if let strokes = glyph?.strokes, !strokes.isEmpty {
                GlyphThumbnail(strokes: strokes, side: side, strokeColor: ink)
            } else {
                Image(systemName: "square.stack.3d.up")
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(ink)
            }
        }
        .frame(width: side, height: side)
        .accessibilityHidden(true)
    }
}
```

- [ ] **Step 2: Build, check the literals, commit**

Run **Build** and the **Suite command** with `ThemeLiteralsTests`. Expected: `** BUILD SUCCEEDED **`, PASS.

```bash
git add apps/ios/Pebbles/Features/Path/Card/CollectionMark.swift
git commit -m "feat(ios): a collection's mark, its glyph or the stack"
```

---

### Task E0b.4: E0b verification and PR

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**. Expected: green.

- [ ] **Step 2: Push, submit, file the Android half (decision 6)**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh issue create --title "[Feat] Android: the collection's glyph on the card's band" \
  --label feat,ui,android --milestone "M64 · New Path" \
  --body "Split from #1145, which the iOS stack closes. The Android band (\`features/path/card/CollectionBand.kt\`) still leads each name with \`ic_pebble_collection\`. iOS reads \`collections.glyph_id\` through \`glyphs(id, name, strokes, view_box)\` in the reference cache and draws it (stack E, parts E0b and E1b)."
```

- [ ] **Step 3: Title, body, labels, milestone**

Body: `Resolves #1145`; key files; D9's data half (the embed, the tolerant decode, `CollectionMark`), and that E1b's band is its first reader (the stack merges whole); the Android follow-up by its new issue number. #1145 carries `feat`, `ui`, `ios`, `android`; this PR is iOS-only.

```bash
gh pr edit <E0b-number> --title "feat(ios): read each collection's glyph into the reference data" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

---
## Part E1a — `feat/1146-ios-card-logic`

PR title: `feat(ios): the held card's logic and strings`. Body opens `Part of #1146`. No view changes. The pure logic of the card with Android's vectors, the ten strings, and the two small model reads the faces need. About 210 lines are catalogue JSON and 520 are test vectors.

### Task E1a.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1146-ios-card-logic
git branch --show-current
```

Expected: `feat/1146-ios-card-logic`.

---

### Task E1a.1: `CardLayout`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/Logic/CardLayout.swift`
- Test: `apps/ios/PebblesTests/Card/CardLayoutTests.swift`

Android has no unit test for `CardLayout.cardWidth`; these vectors are its formula's (spec §Component tree: `cardWidth(available) = min(available − 32, 440)`). The Spine's `CardSizes` vectors were ported by D1.10.

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Testing
@testable import Pebbles

/// #1146 §CardLayout: the held card's geometry, in points (Android's dp).
@Suite("CardLayout")
struct CardLayoutTests {
    @Test("the card is the page less 16 pt a side, at most 440 pt, never negative",
          arguments: [(393.0, 361.0), (375.0, 343.0), (472.0, 440.0), (1024.0, 440.0), (20.0, 0.0)])
    func width(available: Double, expected: Double) {
        #expect(CardLayout.cardWidth(available: available) == expected)
    }

    @Test("card units are the width over 70, uncapped")
    func units() {
        #expect(CardLayout.u(cardWidth: 361) == 361 / 70)
        #expect(CardLayout.u(cardWidth: 440) > 4.4)
    }

    @Test("a postmark is 72 pt, or the row's share when four do not fit")
    func postmark() {
        #expect(CardLayout.postmarkDiameter(count: 1, rowWidth: 300) == 72)
        #expect(CardLayout.postmarkDiameter(count: 4, rowWidth: 250) == (250 - 18) / 4)
        #expect(CardLayout.postmarkDiameter(count: 0, rowWidth: 250) == 72)
    }

    @Test("the band's foot is the mat and its padding, in card units")
    func bandFoot() {
        #expect(abs(CardLayout.bandFoot(u: 5) - 19) < 1e-9)
    }

    @Test("the verso stone is 150 pt wide when large, the others in the Spine's proportions")
    func versoStone() {
        let large = CardLayout.versoStoneSize(.large)
        #expect(large.width == 150)
        let spine = SpineLayout.stoneSize(.large)
        #expect(abs(large.height / large.width - spine.height / spine.width) < 1e-9)
        let medium = CardLayout.versoStoneSize(.medium)
        #expect(abs(medium.width - 150 * SpineLayout.stoneWidth(.medium) / SpineLayout.stoneWidth(.large)) < 1e-9)
    }
}
```

Save it as `apps/ios/PebblesTests/Card/CardLayoutTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CardLayoutTests`. Expected: build FAIL with "cannot find 'CardLayout' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/Logic/CardLayout.swift`:

```swift
import CoreGraphics

/// The held card's geometry, in points, from Android's `CardLayout` (spec
/// §Component tree), as `SpineLayout` is the Spine's. Views never name a
/// number of their own: every inset, size and gap is here or in card units `u`.
enum CardLayout {
    static let maxWidth: CGFloat = 440
    static let pageMargin: CGFloat = 16
    /// Card units on the held card are not capped (the Spine caps them at 4.4).
    static let uDivisor: CGFloat = 70

    static let footerPaddingH: CGFloat = 16
    static let footerPaddingV: CGFloat = 12
    static let footerGap: CGFloat = 12
    /// The iOS minimum target (Android's 48 dp).
    static let turnTarget: CGFloat = 44
    static let miniature = CGSize(width: 20, height: 28)
    static let miniatureTilt: Double = -7

    static let versoStoneWidth: CGFloat = 150
    static let postmarkDiameter: CGFloat = 72
    static let postmarkGap: CGFloat = 6
    static let postmarkGlyph: CGFloat = 38
    static let postmarkOuterRing: CGFloat = 1.6
    /// The outer ring's inset at 72 pt; it scales with a shrunk stamp.
    static let postmarkOuterRingInset: CGFloat = 1.4
    static let postmarkInnerRing: CGFloat = 0.8
    /// Absolute, because the ring type does not scale: the baseline sits this far
    /// in from the stamp's edge, and the inner ring `postmarkInnerRingDrop` under it.
    static let postmarkTextInset: CGFloat = 10.5
    static let postmarkInnerRingDrop: CGFloat = 2.4
    static let postmarkRowToBand: CGFloat = 18

    static let bandMinHeight: CGFloat = 40
    /// How far the band reaches up over the stock panel's foot.
    static let bandOverPanel: CGFloat = 20
    static let versoMatUnits: CGFloat = 1.6
    static let versoMatPaddingUnits: CGFloat = 2.2

    /// The postmark noise texture's side, in pixels.
    static let noiseSize = 128

    /// The held card's width on a page `available` wide: the page margin each
    /// side, capped. The blank card a flight lands on takes the same.
    static func cardWidth(available: CGFloat) -> CGFloat {
        max(0, min(available - 2 * pageMargin, maxWidth))
    }

    static func u(cardWidth: CGFloat) -> CGFloat {
        cardWidth / uDivisor
    }

    /// From the face's foot to the band's: the mat and its padding.
    static func bandFoot(u: CGFloat) -> CGFloat {
        u * (versoMatUnits + versoMatPaddingUnits)
    }

    /// 72 pt, or the row's equal share when `count` stamps and their gaps do not fit.
    static func postmarkDiameter(count: Int, rowWidth: CGFloat) -> CGFloat {
        guard count > 0 else { return postmarkDiameter }
        let share = (rowWidth - postmarkGap * CGFloat(count - 1)) / CGFloat(count)
        return min(postmarkDiameter, share)
    }

    /// The verso's stone: 150 pt wide when large, the others in the Spine's proportions.
    static func versoStoneSize(_ size: ValenceSizeGroup) -> CGSize {
        let spine = SpineLayout.stoneSize(size)
        let scale = versoStoneWidth / SpineLayout.stoneWidth(.large)
        return CGSize(width: spine.width * scale, height: spine.height * scale)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `CardLayoutTests`. Expected: PASS (9: five arguments + four).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/Logic/CardLayout.swift apps/ios/PebblesTests/Card/CardLayoutTests.swift
git commit -m "feat(ios): the held card's geometry"
```

---

### Task E1a.2: `RingFit`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/Logic/RingFit.swift`
- Test: `apps/ios/PebblesTests/Card/RingFitTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

// Ported from apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/RingFitTest.kt
// at d53be9cf, verbatim, plus the spec's Turkish capitals (#1146 §Testing, E1).
@Suite("RingFit (Android RingFitTest.kt)")
struct RingFitTests {
    /// A monospaced fake: every character advances 0.6 em.
    private let measure: (String, CGFloat) -> CGFloat = { text, size in CGFloat(text.count) * size * 0.6 }

    private func seamError(_ fit: RingFit, _ circumference: CGFloat) -> CGFloat {
        abs(measure(fit.text, fit.size) + fit.tracking * CGFloat(fit.text.count) - circumference)
    }

    @Test("a short name repeats as often as the circle allows, closing exactly")
    func shortName() {
        let fit = RingFits.fit(name: "Santé", circumference: 180, locale: Locale(identifier: "fr"), measure: measure)
        #expect(fit.repetitions == 4)
        #expect(fit.size == 8.5)
        #expect(fit.text == String(repeating: "SANTÉ · ", count: 4))
        #expect(seamError(fit, 180) < 0.5)
    }

    @Test("a long name steps the size down before it falls under two repetitions")
    func longName() {
        let fit = RingFits.fit(name: "Current events", circumference: 170, locale: Locale(identifier: "en"),
                               measure: measure)
        #expect(fit.repetitions == 2)
        #expect(fit.size == 8)
        #expect(seamError(fit, 170) < 0.5)
    }

    @Test("a name that cannot ring twice even at the smallest size is set once")
    func setOnce() {
        let fit = RingFits.fit(name: "Spirituality", circumference: 60, locale: Locale(identifier: "en"),
                               measure: measure)
        #expect(fit.repetitions == 1)
        #expect(fit.size == 7.5)
        #expect(fit.text == "SPIRITUALITY · ")
        #expect(fit.tracking == 0)
    }

    @Test("tracking is never negative", arguments: [90.0, 120.0, 163.2, 200.0, 260.0])
    func neverNegative(circumference: Double) {
        let fit = RingFits.fit(name: "Friends", circumference: circumference, locale: Locale(identifier: "en"),
                               measure: measure)
        #expect(fit.tracking >= 0)
    }

    @Test("a measure that grows faster than its parts steps the repetitions down")
    func superadditive() {
        // +1 pt for every repetition after the first: three units overrun a circle
        // their single unit's multiple fits with half a point to spare.
        let superAdditive: (String, CGFloat) -> CGFloat = { text, size in
            CGFloat(text.count) * size * 0.6 + CGFloat(max(0, text.filter { $0 == "·" }.count - 1))
        }
        let fit = RingFits.fit(name: "Friends", circumference: 153.5, locale: Locale(identifier: "en"),
                               measure: superAdditive)
        #expect(fit.repetitions == 2)
        #expect(fit.size == 8.5)
        #expect(fit.tracking >= 0)
        let closed = superAdditive(fit.text, fit.size) + fit.tracking * CGFloat(fit.text.count)
        #expect(abs(closed - 153.5) < 0.01)
    }

    @Test("the name is set in the locale's capitals")
    func capitals() {
        let french = RingFits.fit(name: "Bien-être", circumference: 200, locale: Locale(identifier: "fr"),
                                  measure: measure)
        #expect(french.text.hasPrefix("BIEN-ÊTRE · "))
        let turkish = RingFits.fit(name: "istanbul", circumference: 200, locale: Locale(identifier: "tr"),
                                   measure: measure)
        #expect(turkish.text.hasPrefix("İSTANBUL · "))
    }
}
```

Save it as `apps/ios/PebblesTests/Card/RingFitTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `RingFitTests`. Expected: build FAIL with "cannot find 'RingFits' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/Logic/RingFit.swift`:

```swift
import CoreGraphics
import Foundation

/// How a domain's name rings a postmark (#1146 §Postmark): `text` is the name
/// in the locale's capitals, repeated with " · " after each, set at `size` with
/// `tracking` added after every character, so the ring closes exactly and the
/// seam is an ordinary gap. A port of Android's `RingFit.kt`.
struct RingFit: Equatable {
    let text: String
    let repetitions: Int
    let size: CGFloat
    let tracking: CGFloat
}

enum RingFits {
    /// Tried in order; a name that cannot ring twice at the last is set once.
    static let sizes: [CGFloat] = [8.5, 8, 7.5]
    static let separator = " · "
    private static let minRepetitions = 2

    /// - Parameters:
    ///   - circumference: the circle the text's baseline runs on, in points.
    ///   - measure: the advance of a string at a size, in points. On device the
    ///     sum of the per-character advances the draw loop steps by, so the ring
    ///     closes exactly; a fake in tests.
    static func fit(name: String, circumference: CGFloat, locale: Locale,
                    measure: (_ text: String, _ size: CGFloat) -> CGFloat) -> RingFit {
        let unit = name.uppercased(with: locale) + separator
        for size in sizes {
            var repetitions = Int((circumference / measure(unit, size)).rounded(.down))
            // A real measure is not additive (kerning, rounding), so the repeated
            // text can overrun where the unit's multiple did not: step down until it
            // fits, so the tracking can never go negative.
            while repetitions >= minRepetitions,
                  measure(String(repeating: unit, count: repetitions), size) > circumference {
                repetitions -= 1
            }
            if repetitions >= minRepetitions {
                let text = String(repeating: unit, count: repetitions)
                return RingFit(text: text, repetitions: repetitions, size: size,
                               tracking: (circumference - measure(text, size)) / CGFloat(text.count))
            }
        }
        let size = sizes[sizes.count - 1]
        return RingFit(text: unit, repetitions: 1, size: size,
                       tracking: max(0, (circumference - measure(unit, size)) / CGFloat(unit.count)))
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `RingFitTests`. Expected: PASS (10: five arguments + five). These vectors were run against this code on 2026-10-21 (macOS `swiftc`): `Santé` 4 × 8.5 with a 0.0 seam, `Current events` 2 × 8, `Spirituality` once at 7.5 with tracking 0, the superadditive ring 2 × 8.5 closing at 153.5, `İSTANBUL`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/Logic/RingFit.swift apps/ios/PebblesTests/Card/RingFitTests.swift
git commit -m "feat(ios): fit a domain's name round its postmark"
```

---

### Task E1a.3: `ValenceLockup`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/Logic/ValenceLockup.swift`
- Test: `apps/ios/PebblesTests/Card/ValenceLockupTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

// Ported from apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/ValenceLockupTest.kt
// at d53be9cf: Android's word scale (1, 44/56, 34/56) is iOS's rank (large, medium,
// small), stack B's `SpineType.lockupWord`; its string resources are `Valence.headline`.
@Suite("ValenceLockup (Android ValenceLockupTest.kt)")
struct ValenceLockupTests {
    private func english(_ resource: LocalizedStringResource) -> String {
        var copy = resource
        copy.locale = Locale(identifier: "en")
        return String(localized: copy)
    }

    static let expected: [(Valence, String, Bool, LockupRank, String, LockupInk)] = [
        (.highlightLarge, "Highlight", false, .large, "OF MY MONTH", .stoneRamp),
        (.highlightMedium, "Highlight", false, .medium, "OF MY WEEK", .stoneRamp),
        (.highlightSmall, "Highlight", true, .small, "OF MY DAY", .stoneRamp),
        (.neutralLarge, "Moment", false, .large, "OF MY MONTH", .primary),
        (.neutralMedium, "Moment", false, .medium, "OF MY WEEK", .primary),
        (.neutralSmall, "Moment", true, .small, "OF MY DAY", .primary),
        (.lowlightLarge, "Lowlight", false, .large, "OF MY MONTH", .ink),
        (.lowlightMedium, "Lowlight", false, .medium, "OF MY WEEK", .ink),
        (.lowlightSmall, "Lowlight", true, .small, "OF MY DAY", .ink)
    ]

    @Test("every valence maps to the record flow's lockup")
    func everyValence() {
        #expect(Self.expected.count == Valence.allCases.count)
        for (valence, word, lowercase, rank, span, ink) in Self.expected {
            let lockup = ValenceLockup.of(valence)
            #expect(english(lockup.word) == word, "\(valence) word")
            #expect(lockup.lowercase == lowercase, "\(valence) case")
            #expect(lockup.rank == rank, "\(valence) rank")
            #expect(english(lockup.span) == span, "\(valence) span")
            #expect(lockup.ink == ink, "\(valence) ink")
        }
    }

    @Test("the word and span are the record flow's own resources")
    func sameResources() {
        for valence in Valence.allCases {
            #expect(ValenceLockup.of(valence).word == valence.headline.word)
            #expect(ValenceLockup.of(valence).span == valence.headline.span)
        }
    }

    @Test("the rank is the size group stack B's lockup word takes")
    func rank() {
        #expect(LockupRank.small.sizeGroup == .small)
        #expect(LockupRank.medium.sizeGroup == .medium)
        #expect(LockupRank.large.sizeGroup == .large)
    }
}
```

Save it as `apps/ios/PebblesTests/Card/ValenceLockupTests.swift`. If `Valence` is not `CaseIterable` on `main` (`grep -n "enum Valence" apps/ios/Pebbles/Features/Path/Models/Valence.swift`), replace `Valence.allCases` with `Self.expected.map(\.0)` and drop the count line.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `ValenceLockupTests`. Expected: build FAIL with "cannot find 'ValenceLockup' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/Logic/ValenceLockup.swift`:

```swift
import Foundation

/// How the verso's lockup paints its word (#1146 §Verso): Highlight in the
/// emotion's lit-stone ramp, Moment in `primary`, Lowlight in ink.
enum LockupInk: Equatable {
    case stoneRamp, ink, primary
}

/// The lockup word's size, stack B's `SpineType.lockupWord(_:)` group.
enum LockupRank: Equatable {
    case small, medium, large

    init(_ group: ValenceSizeGroup) {
        switch group {
        case .small: self = .small
        case .medium: self = .medium
        case .large: self = .large
        }
    }

    var sizeGroup: ValenceSizeGroup {
        switch self {
        case .small: return .small
        case .medium: return .medium
        case .large: return .large
        }
    }
}

/// The verso's top as data (#1146 §Pure functions): the record flow's lockup,
/// the word and its span from `Valence.headline`, set in stack B's lockup rank.
/// A day event's word is lowercase (`lowercase`, which the rank's
/// `isLowercase` applies). A port of Android's `ValenceLockups.of`.
struct ValenceLockup: Equatable {
    let word: LocalizedStringResource
    let lowercase: Bool
    let rank: LockupRank
    let span: LocalizedStringResource
    let ink: LockupInk
}

extension ValenceLockup {
    static func of(_ valence: Valence) -> ValenceLockup {
        let headline = valence.headline
        let ink: LockupInk
        switch valence.polarity {
        case .highlight: ink = .stoneRamp
        case .neutral: ink = .primary
        case .lowlight: ink = .ink
        }
        return ValenceLockup(word: headline.word, lowercase: valence.sizeGroup == .small,
                             rank: LockupRank(valence.sizeGroup), span: headline.span, ink: ink)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `ValenceLockupTests`. Expected: PASS (3).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/Logic/ValenceLockup.swift apps/ios/PebblesTests/Card/ValenceLockupTests.swift
git commit -m "feat(ios): the verso's valence lockup as data"
```

---

### Task E1a.4: `PostmarkNoise`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/Logic/PostmarkNoise.swift`
- Test: `apps/ios/PebblesTests/Card/PostmarkNoiseTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
@testable import Pebbles

// Ported from apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/PostmarkNoiseTest.kt
// at d53be9cf, plus SplitMix64's published first outputs (Kotlin's `Random(seed)` is
// not portable, so iOS draws from SplitMix64; the bytes differ from Android's, the
// statistics do not).
@Suite("PostmarkNoise (Android PostmarkNoiseTest.kt)")
struct PostmarkNoiseTests {
    @Test("SplitMix64 draws its reference sequence")
    func generator() {
        var random = SplitMix64(seed: 0)
        #expect(random.next() == 0xE220_A839_7B1D_CDAF)
        #expect(random.next() == 0x6E78_9E6A_A1B9_65F4)
    }

    @Test("the same seed gives the same mask")
    func deterministic() {
        #expect(PostmarkNoise.alphas(size: 64, seed: 7) == PostmarkNoise.alphas(size: 64, seed: 7))
    }

    @Test("different seeds give different masks")
    func seeded() {
        #expect(PostmarkNoise.alphas(size: 64, seed: 7) != PostmarkNoise.alphas(size: 64, seed: 8))
    }

    @Test("the mask is restrained: rare pits, a drift under 17 %")
    func restrained() throws {
        let alphas = PostmarkNoise.alphas(size: 128, seed: 3)
        #expect(alphas.count == 128 * 128)
        let pits = alphas.filter { $0 >= PostmarkNoise.pitAlpha }.count
        let drift = alphas.filter { $0 < PostmarkNoise.pitAlpha }
        let pitShare = Double(pits) / Double(alphas.count)
        #expect((0.01...0.06).contains(pitShare), "pits \(pitShare)")
        let maxDrift = try #require(drift.max())
        #expect(Int(maxDrift) <= Int(255 * (1 - PostmarkNoise.minDensity)) + 1, "drift max \(maxDrift)")
        let mean = Double(drift.map(Int.init).reduce(0, +)) / Double(drift.count) / 255
        #expect(mean <= 0.17, "mean drift \(mean)")
    }
}
```

Save it as `apps/ios/PebblesTests/Card/PostmarkNoiseTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `PostmarkNoiseTests`. Expected: build FAIL with "cannot find 'SplitMix64' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/Logic/PostmarkNoise.swift`:

```swift
import Foundation

/// SplitMix64: a small deterministic generator, the same bytes on every device
/// and run (Swift's own generators are seeded per process).
struct SplitMix64 {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }

    /// A double in [0, 1), from the top 53 bits.
    mutating func nextUnit() -> Double {
        Double(next() >> 11) * 0x1.0p-53
    }
}

/// The postmark ink's texture as an alpha mask (#1146 §Postmark, D4): a smooth
/// drift on an 8 px lattice that thins the ink to at most 1 − `minDensity`,
/// plus rare pits (3 %) at `pitAlpha` and above, where the panel shows through.
/// Deterministic per seed, so each stamp keeps its own pattern. A port of
/// Android's `PostmarkNoise.kt` on SplitMix64.
enum PostmarkNoise {
    static let minDensity = 0.83
    static let pitAlpha: UInt8 = 200
    static let pitRate = 0.03
    static let cell = 8

    static func alphas(size: Int, seed: UInt64) -> [UInt8] {
        var random = SplitMix64(seed: seed)
        let grid = size / cell + 2
        let lattice = (0..<(grid * grid)).map { _ in random.nextUnit() }
        let maxDrift = 255 * (1 - minDensity)
        func at(_ x: Int, _ y: Int) -> Double { lattice[y * grid + x] }
        func smooth(_ t: Double) -> Double { t * t * (3 - 2 * t) }
        return (0..<(size * size)).map { index in
            if random.nextUnit() < pitRate {
                return pitAlpha + UInt8(random.next() % UInt64(256 - Int(pitAlpha)))
            }
            let x = Double(index % size) / Double(cell)
            let y = Double(index / size) / Double(cell)
            let x0 = Int(x.rounded(.down))
            let y0 = Int(y.rounded(.down))
            let tx = smooth(x - Double(x0))
            let ty = smooth(y - Double(y0))
            let top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * tx
            let bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * tx
            return UInt8((top + (bottom - top) * ty) * maxDrift)
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `PostmarkNoiseTests`. Expected: PASS (4). Run against this code on 2026-10-21 (macOS `swiftc`): pits 2.84 %, drift max 42, mean drift 21.1 / 255 (8.3 %).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/Logic/PostmarkNoise.swift apps/ios/PebblesTests/Card/PostmarkNoiseTests.swift
git commit -m "feat(ios): the postmark's restrained ink texture"
```

---

### Task E1a.5: `BandLines` and `FooterArrangement`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/Logic/BandLines.swift`
- Create: `apps/ios/Pebbles/Features/Path/Card/Logic/FooterArrangement.swift`
- Test: `apps/ios/PebblesTests/Card/CardFlowTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Testing
@testable import Pebbles

/// #1146 §Band: the band's names wrap greedily and a line never starts or ends
/// on a knot (Android's `BandFlow`). §Footer: the turn drops under the privacy
/// once it would take more than half the width.
@Suite("Card flows")
struct CardFlowTests {
    @Test("names that fit share one line")
    func oneLine() {
        #expect(BandLines.lines(widths: [50, 60, 70], knotRun: 25, maxWidth: 300) == [[0, 1, 2]])
    }

    @Test("a name that would overrun starts the next line, with no knot before it")
    func wraps() {
        #expect(BandLines.lines(widths: [50, 60, 70], knotRun: 25, maxWidth: 150) == [[0, 1], [2]])
    }

    @Test("a name wider than the band still gets a line of its own")
    func tooWide() {
        #expect(BandLines.lines(widths: [400, 40], knotRun: 25, maxWidth: 300) == [[0], [1]])
    }

    @Test("no collections, no lines")
    func empty() {
        #expect(BandLines.lines(widths: [], knotRun: 25, maxWidth: 300).isEmpty)
    }

    @Test("the line widths add the knots between names only")
    func widths() {
        #expect(BandLines.width(of: [0, 1], widths: [50, 60, 70], knotRun: 25) == 135)
        #expect(BandLines.width(of: [2], widths: [50, 60, 70], knotRun: 25) == 70)
    }

    @Test("the turn sits beside the privacy up to half the width, then drops under it")
    func footer() {
        #expect(!FooterArrangement.stacked(turnWidth: 150, available: 300))
        #expect(FooterArrangement.stacked(turnWidth: 151, available: 300))
    }
}
```

Save it as `apps/ios/PebblesTests/Card/CardFlowTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CardFlowTests`. Expected: build FAIL with "cannot find 'BandLines' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/Logic/BandLines.swift`:

```swift
import CoreGraphics

/// The collection band's lines (#1146 §Band): greedy, a knot (a small diamond)
/// between two names on the same line only, so a wrapped line never starts or
/// ends on one, which would read as a stray mark. Android's `BandFlow` measure.
enum BandLines {
    /// - Parameters:
    ///   - widths: each name's width (its mark, gap and italic name).
    ///   - knotRun: a knot and the gap each side of it.
    /// - Returns: the names' indices, line by line.
    static func lines(widths: [CGFloat], knotRun: CGFloat, maxWidth: CGFloat) -> [[Int]] {
        guard !widths.isEmpty else { return [] }
        var lines: [[Int]] = [[]]
        var lineWidth: CGFloat = 0
        for (index, width) in widths.enumerated() {
            let isLineEmpty = lines[lines.count - 1].isEmpty
            if !isLineEmpty, lineWidth + knotRun + width > maxWidth {
                lines.append([index])
                lineWidth = width
            } else {
                lineWidth += (isLineEmpty ? 0 : knotRun) + width
                lines[lines.count - 1].append(index)
            }
        }
        return lines
    }

    static func width(of line: [Int], widths: [CGFloat], knotRun: CGFloat) -> CGFloat {
        line.map { widths[$0] }.reduce(0, +) + knotRun * CGFloat(max(0, line.count - 1))
    }
}
```

Create `apps/ios/Pebbles/Features/Path/Card/Logic/FooterArrangement.swift`:

```swift
import CoreGraphics

/// The footer's two arrangements (#1146 §Footer): the turn beside the privacy,
/// or, once it would take more than half the width (large type), under it,
/// trailing-aligned, rather than squeezing the privacy to a column.
enum FooterArrangement {
    static func stacked(turnWidth: CGFloat, available: CGFloat) -> Bool {
        turnWidth > available / 2
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `CardFlowTests`. Expected: PASS (6).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/Logic/BandLines.swift apps/ios/Pebbles/Features/Path/Card/Logic/FooterArrangement.swift apps/ios/PebblesTests/Card/CardFlowTests.swift
git commit -m "feat(ios): the band's lines and the footer's arrangement"
```

---

### Task E1a.6: `CardDateline`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/Logic/CardDateline.swift`
- Test: `apps/ios/PebblesTests/Card/CardDatelineTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1146 §Recto: the leaf's full dateline, the locale's full weekday, day,
/// month and year, then " · " and the short time, in the device's zone.
/// Expected strings were formatted with these exact styles on 2026-10-21.
@Suite("CardDateline")
struct CardDatelineTests {
    private func paris() throws -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try #require(TimeZone(identifier: "Europe/Paris"))
        return calendar
    }

    private let thursday = ISO8601Flexible.parse("2026-10-08T19:00:00Z")

    @Test("British English")
    func english() throws {
        let date = try #require(thursday)
        #expect(CardDateline.text(date, calendar: try paris(), locale: Locale(identifier: "en_GB"))
            == "Thursday, 8 October 2026 · 21:00")
    }

    @Test("French")
    func french() throws {
        let date = try #require(thursday)
        #expect(CardDateline.text(date, calendar: try paris(), locale: Locale(identifier: "fr_FR"))
            == "jeudi 8 octobre 2026 · 21:00")
    }

    @Test("the zone decides the day: the same instant in New York is the afternoon")
    func zone() throws {
        let date = try #require(thursday)
        var newYork = Calendar(identifier: .gregorian)
        newYork.timeZone = try #require(TimeZone(identifier: "America/New_York"))
        #expect(CardDateline.text(date, calendar: newYork, locale: Locale(identifier: "fr_FR"))
            == "jeudi 8 octobre 2026 · 15:00")
    }
}
```

Save it as `apps/ios/PebblesTests/Card/CardDatelineTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CardDatelineTests`. Expected: build FAIL with "cannot find 'CardDateline' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/Logic/CardDateline.swift`:

```swift
import Foundation

/// The held card's dates (#1146 §Recto), formatted with an explicit calendar
/// and locale so tests pin them; views pass `SpineCalendar.current` and
/// `SpineStrings.appLocale`. iOS's `MomentDetail` has no zone of its own, so
/// "the moment's zone" is the device's, as on the Spine.
enum CardDateline {
    /// "Thursday, 8 October 2026 · 21:00": the full date, then the short time.
    static func text(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        var day = Date.FormatStyle.dateTime.weekday(.wide).day().month(.wide).year()
        day.calendar = calendar
        day.timeZone = calendar.timeZone
        day.locale = locale
        return date.formatted(day) + " · " + time(date, calendar: calendar, locale: locale)
    }

    /// The locale's short time.
    static func time(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        var style = Date.FormatStyle(date: .omitted, time: .shortened)
        style.calendar = calendar
        style.timeZone = calendar.timeZone
        style.locale = locale
        return date.formatted(style)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `CardDatelineTests`. Expected: PASS (3). If only the time part differs ("9:00 PM" style), the simulator's ICU resolves `.shortened` differently from the host's: keep the code and change the expected time to what `CardDateline.time` prints for that locale, never the code.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/Logic/CardDateline.swift apps/ios/PebblesTests/Card/CardDatelineTests.swift
git commit -m "feat(ios): the held card's full dateline"
```

---

### Task E1a.7: `CardColors`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/CardColors.swift`
- Test: `apps/ios/PebblesTests/Card/CardColorsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import SwiftUI
import Testing
@testable import Pebbles

/// #1146 §Verso: which palette slot inks a postmark, how strong the band's
/// paper is, and the Highlight word's ramp (decision 2), by scheme. Colours in
/// this test are fixtures (`ThemeLiteralsTests` scans `Features/` only).
@Suite("CardColors")
struct CardColorsTests {
    @Test("postmark ink: the emotion's dark on paper, its light at Night")
    func postmarkInk() {
        #expect(CardColors.postmarkInkSlot(scheme: .light) == .dark)
        #expect(CardColors.postmarkInkSlot(scheme: .dark) == .light)
    }

    @Test("the band's paper takes 19 % of the spot on paper, 24 % at Night")
    func bandPaper() {
        #expect(CardColors.bandPaperK(scheme: .light) == 0.19)
        #expect(CardColors.bandPaperK(scheme: .dark) == 0.24)
    }

    @Test("a mix moves the first colour toward the second by k")
    func mix() {
        let environment = EnvironmentValues()
        let a = Color(.sRGB, red: 0, green: 0, blue: 0)
        let b = Color(.sRGB, red: 1, green: 1, blue: 1)
        let half = CardColors.mix(a, toward: b, 0.5, in: environment).resolve(in: environment)
        let resolvedA = a.resolve(in: environment)
        let resolvedB = b.resolve(in: environment)
        #expect(abs(half.linearRed - (resolvedA.linearRed + resolvedB.linearRed) / 2) < 0.001)
        let none = CardColors.mix(a, toward: b, 0, in: environment).resolve(in: environment)
        let all = CardColors.mix(a, toward: b, 1, in: environment).resolve(in: environment)
        #expect(abs(none.linearRed - resolvedA.linearRed) < 0.001)
        #expect(abs(all.linearRed - resolvedB.linearRed) < 0.001)
    }

    @Test("the Highlight ramp has three stops at 0, 0.4 and 1 in both schemes")
    func ramp() {
        for scheme in [ColorScheme.light, .dark] {
            var environment = EnvironmentValues()
            environment.colorScheme = scheme
            let stops = CardColors.highlightRamp(.brand, scheme: scheme, in: environment)
            #expect(stops.map(\.location) == [0, 0.4, 1])
        }
    }
}
```

Save it as `apps/ios/PebblesTests/Card/CardColorsTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CardColorsTests`. Expected: build FAIL with "cannot find 'CardColors' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/CardColors.swift`:

```swift
import SwiftUI

/// The held card's blends with a pebble's emotion palette (#1146 §Verso): the
/// palette is runtime data, so these are computed here, never colorsets. The
/// scheme-only tones are `Color.pebbles.*`; the slot and strength choices are
/// pure and tested.
enum CardColors {
    /// The mat's and the panel's keylines: the spot at 30 % and 28 %.
    static let matRuleAlpha = 0.30
    static let panelRuleAlpha = 0.28
    /// The photo-less slip's foot line: the spot at 22 %.
    static let slipFootAlpha = 0.22
    /// The band's hairlines and its turned-under folds, in the spot.
    static let bandHairlineAlpha = 0.35
    static let bandFoldAlpha = 0.5

    /// Postmark ink: the emotion's `dark` on paper, its `light` at Night.
    static func postmarkInkSlot(scheme: ColorScheme) -> StonePalette.Slot {
        scheme == .dark ? .light : .dark
    }

    /// The band's paper: the spot laid over the stock at 19 %, 24 % at Night.
    static func bandPaperK(scheme: ColorScheme) -> Double {
        scheme == .dark ? 0.24 : 0.19
    }

    /// `color` moved `k` of the way toward `other`, on the environment's
    /// resolved components (`Color.mix` is iOS 18; `resolve(in:)` is 17).
    static func mix(_ color: Color, toward other: Color, _ k: Double, in environment: EnvironmentValues) -> Color {
        let from = color.resolve(in: environment)
        let to = other.resolve(in: environment)
        let t = Float(min(max(k, 0), 1))
        func lerp(_ a: Float, _ b: Float) -> Float { a + (b - a) * t }
        return Color(Color.Resolved(colorSpace: .sRGBLinear, red: lerp(from.linearRed, to.linearRed),
                                    green: lerp(from.linearGreen, to.linearGreen),
                                    blue: lerp(from.linearBlue, to.linearBlue),
                                    opacity: lerp(from.opacity, to.opacity)))
    }

    /// The Highlight word's ramp, top to bottom (decision 2). Paper: the lit
    /// stone's own tones, its light lip, its body, its shadow lip. Night: `light`,
    /// `secondary` at 0.4, then `secondary` moved 70 % toward `primary`.
    static func highlightRamp(_ palette: StonePalette, scheme: ColorScheme,
                              in environment: EnvironmentValues) -> [Gradient.Stop] {
        if scheme == .dark {
            return [
                Gradient.Stop(color: palette.light, location: 0),
                Gradient.Stop(color: palette.secondary, location: 0.4),
                Gradient.Stop(color: mix(palette.secondary, toward: palette.primary, 0.7, in: environment), location: 1)
            ]
        }
        let tones = StoneTones.starting(for: .highlight)
        return [
            Gradient.Stop(color: tones.color(tones.lipLight, in: palette), location: 0),
            Gradient.Stop(color: tones.color(tones.body, in: palette), location: 0.4),
            Gradient.Stop(color: tones.color(tones.lipShadow, in: palette), location: 1)
        ]
    }
}
```

`Color.Resolved` exposes `linearRed` / `linearGreen` / `linearBlue` and has `init(colorSpace:red:green:blue:opacity:)` on iOS 17. If the build reports either missing, mix on `red` / `green` / `blue` with `colorSpace: .sRGB` instead; the test holds either way.

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `CardColorsTests` and with `ThemeLiteralsTests`. Expected: PASS (4), PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/CardColors.swift apps/ios/PebblesTests/Card/CardColorsTests.swift
git commit -m "feat(ios): the held card's ink, paper and highlight ramp"
```

---

### Task E1a.8: A detail as the Spine's summary, and the card's people line

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/MomentSummary+Detail.swift`
- Test: `apps/ios/PebblesTests/Card/MomentSummaryFromDetailTests.swift`
- Test: `apps/ios/PebblesTests/Card/PeopleLineCardTests.swift`

The recto is the Spine card at the card's width, and `SpineCardFace` reads a `MomentSummary`. This is the one place a `MomentDetail` becomes one.

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/Card/MomentSummaryFromDetailTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1146 §Recto: the held card draws its recto from the detail as the Spine
/// draws a card from `path_moments`.
@Suite("MomentSummary from a detail")
struct MomentSummaryFromDetailTests {
    @Test("a completed moment keeps its name, date, first photo and pebble with its souls")
    func completed() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        let summary = MomentSummary(detail: detail)
        #expect(summary.id == detail.id)
        #expect(summary.name == "Coffee with Ana")
        #expect(summary.happenedAt == detail.happenedAt)
        #expect(summary.firstSnapPath == detail.snaps.first?.storagePath)
        let pebble = try #require(summary.pebble)
        #expect(pebble.valence == .highlightMedium)
        #expect(pebble.visibility == .private)
        #expect(pebble.emotion?.slug == "joy")
        #expect(pebble.glyph?.id == detail.pebble?.glyph?.id)
        #expect(summary.soulIds == [MomentFixtures.soulId])
        #expect(pebble.souls.first?.name == "Ana")
    }

    @Test("a bare photo moment has a first photo and no pebble")
    func bare() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.photoOnlyMoment)
        let summary = MomentSummary(detail: detail)
        #expect(summary.pebble == nil)
        #expect(summary.name == nil)
        #expect(summary.firstSnapPath != nil)
    }
}
```

Create `apps/ios/PebblesTests/Card/PeopleLineCardTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1146 §Souls: the card names up to eight souls in full; past eight, the
/// Spine's counting rule. Callers without a week pass `order: []`, which keeps
/// the pebble's own order (stack D's `PeopleLines`).
@Suite("The card's people line")
struct PeopleLineCardTests {
    private func ids(_ n: Int) -> [String] { (0..<n).map { "soul-\($0)" } }

    @Test("eight souls are all named, in the pebble's order when there is no week")
    func eight() {
        let line = PeopleLines.peopleLine(ids(8), order: [], locale: Vectors.en, namedInFull: PeopleLines.namedOnCard)
        #expect(line.named == ids(8))
        #expect(line.others == 0 && line.marks.isEmpty && line.overflow == 0)
    }

    @Test("nine fall back to two named, seven others in words, five marks and +2")
    func nine() {
        let line = PeopleLines.peopleLine(ids(9), order: [], locale: Vectors.en, namedInFull: PeopleLines.namedOnCard)
        #expect(line.named == ["soul-0", "soul-1"])
        #expect(line.others == 7 && line.othersWords == "seven")
        #expect(line.marks.count == 5 && line.overflow == 2)
        let french = PeopleLines.peopleLine(ids(9), order: [], locale: Vectors.fr, namedInFull: PeopleLines.namedOnCard)
        #expect(french.othersWords == "sept")
    }

    @Test("the Spine's persons order still leads when the Spine opened the card")
    func spineOrder() {
        let line = PeopleLines.peopleLine(["a", "b", "c"], order: ["c", "a"], locale: Vectors.en,
                                          namedInFull: PeopleLines.namedOnCard)
        #expect(line.named == ["c", "a", "b"])
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run the **Suite command** with `MomentSummaryFromDetailTests`. Expected: build FAIL with "incorrect argument label in call (have 'detail:'…)" or "extra argument 'detail'".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Models/MomentSummary+Detail.swift`:

```swift
import Foundation

extension MomentSummary {
    /// A moment's detail as the Spine reads it (#1146 §Recto): the held card's
    /// recto is the Spine card at the card's width, drawn from this. A
    /// `v_moments_full` soul carries no glyph id; the marks come from the
    /// reference cache by id, as on the Spine.
    init(detail: MomentDetail) {
        let pebble = detail.pebble.map { pebble in
            PebbleSummary(id: pebble.id, intensity: pebble.intensity, positiveness: pebble.positiveness,
                          visibility: pebble.visibility, emotion: pebble.emotion, glyph: pebble.glyph,
                          souls: pebble.souls.map { PathSoul(id: $0.id, name: $0.name, glyphId: nil) })
        }
        self.init(id: detail.id, name: detail.name, happenedAt: detail.happenedAt, createdAt: detail.createdAt,
                  firstSnapPath: detail.snaps.first?.storagePath, pebble: pebble)
    }
}
```

- [ ] **Step 4: Run them to verify they pass**

Run the **Suite command** with `MomentSummaryFromDetailTests`, then `PeopleLineCardTests`. Expected: PASS (2), PASS (3).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/MomentSummary+Detail.swift apps/ios/PebblesTests/Card/MomentSummaryFromDetailTests.swift apps/ios/PebblesTests/Card/PeopleLineCardTests.swift
git commit -m "feat(ios): read a moment's detail as the spine's summary"
```

---

### Task E1a.9: `Visibility.explanation`, one source

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/Visibility.swift`
- Modify: `apps/ios/Pebbles/Features/Complete/Steps/CompletePrivacyStep.swift`
- Test: `apps/ios/PebblesTests/Card/VisibilityExplanationTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1146 §Strings: the footer and the Complete flow's privacy step read the
/// same three sentences.
@Suite("Visibility explanation")
struct VisibilityExplanationTests {
    private func resolved(_ resource: LocalizedStringResource, _ language: String) -> String {
        var copy = resource
        copy.locale = Locale(identifier: language)
        return String(localized: copy)
    }

    @Test("each grade explains itself in English and French",
          arguments: [
              (Visibility.secret, "Only you can see this one."),
              (Visibility.private, "Your mutual connections can see it."),
              (Visibility.public, "Anyone with the link can see it.")
          ])
    func sentence(grade: Visibility, english: String) {
        #expect(resolved(grade.explanation, "en") == english)
        let french = resolved(grade.explanation, "fr")
        #expect(!french.isEmpty && french != english)
    }
}
```

Save it as `apps/ios/PebblesTests/Card/VisibilityExplanationTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `VisibilityExplanationTests`. Expected: build FAIL with "value of type 'Visibility' has no member 'explanation'".

- [ ] **Step 3: Implement**

In `apps/ios/Pebbles/Features/Path/Models/Visibility.swift`, replace

```swift
    /// SF Symbol per grade — mirrored by the Android badge/menu drawables.
```

with

```swift
    /// What the grade means, as the Complete flow's privacy step and the held
    /// card's footer say it (#1146): one source for both.
    var explanation: LocalizedStringResource {
        switch self {
        case .secret:  return "Only you can see this one."
        case .private: return "Your mutual connections can see it."
        case .public:  return "Anyone with the link can see it."
        }
    }

    /// SF Symbol per grade — mirrored by the Android badge/menu drawables.
```

In `apps/ios/Pebbles/Features/Complete/Steps/CompletePrivacyStep.swift`:

1. Replace (Edit with `replace_all`; `grep -c "explanation(for: grade)"` prints `2` first) `explanation(for: grade)` with `grade.explanation`.
2. Delete

```swift

    private func explanation(for grade: Visibility) -> LocalizedStringResource {
        switch grade {
        case .secret:  return "Only you can see this one."
        case .private: return "Your mutual connections can see it."
        case .public:  return "Anyone with the link can see it."
        }
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `VisibilityExplanationTests`, then `CompleteStepTests`. Expected: PASS (3), PASS. The three keys keep their catalogue entries (same literals).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/Visibility.swift apps/ios/Pebbles/Features/Complete/Steps/CompletePrivacyStep.swift apps/ios/PebblesTests/Card/VisibilityExplanationTests.swift
git commit -m "feat(ios): one source for what a privacy grade means"
```

---

### Task E1a.10: The card's strings

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`
- Create: `apps/ios/Pebbles/Features/Path/Card/CardStrings.swift`
- Test: `apps/ios/PebblesTests/Card/CardStringsTests.swift`

New pattern (spec §New patterns): dotted keys read through `LocalizedStringResource(_:defaultValue:comment:)`, because "Moment" and "Your pebble" are already literal keys with other uses.

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1146 §Strings: every new key in both languages; French says galet.
@Suite("Card strings")
struct CardStringsTests {
    private func resolved(_ resource: LocalizedStringResource, _ language: String) -> String {
        var copy = resource
        copy.locale = Locale(identifier: language)
        return String(localized: copy)
    }

    static let rows: [(LocalizedStringResource, String, String)] = [
        (CardStrings.turn, "Turn", "Retourner"),
        (CardStrings.turnToPebble, "Pebble", "Galet"),
        (CardStrings.turnToMoment, "Moment", "Moment"),
        (CardStrings.turnActionToPebble, "Turn over to your pebble", "Retourner vers ton galet"),
        (CardStrings.turnActionToMoment, "Turn over to the moment", "Retourner vers le moment"),
        (CardStrings.versoHeading, "Your pebble", "Ton galet"),
        (CardStrings.domainsA11y, "Life domains", "Domaines de vie"),
        (CardStrings.collectionsA11y, "Collections", "Collections"),
        (CardStrings.placeKicker, "Not a pebble yet", "Pas encore un galet"),
        (CardStrings.placeBody,
         "Its card prints here once you say what it meant: a feeling and its weight, the parts of your life it touched, who was there.",
         "Sa carte s'imprimera ici quand tu diras ce qu'il a compté : un sentiment et son poids, les parts de ta vie qu'il a touchées, qui était là.")
    ]

    @Test("every key resolves in English and French")
    func bothLanguages() {
        for (resource, english, french) in Self.rows {
            #expect(resolved(resource, "en") == english)
            #expect(resolved(resource, "fr") == french)
        }
    }

    @Test("French says galet, never caillou")
    func galet() {
        for (resource, _, _) in Self.rows {
            #expect(!resolved(resource, "fr").localizedCaseInsensitiveContains("caillou"))
        }
    }
}
```

Save it as `apps/ios/PebblesTests/Card/CardStringsTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CardStringsTests`. Expected: build FAIL with "cannot find 'CardStrings' in scope".

- [ ] **Step 3: The resources**

Create `apps/ios/Pebbles/Features/Path/Card/CardStrings.swift`:

```swift
import Foundation

/// The held card's own copy (#1146 §Strings). Dotted keys, as `domain.*.name`,
/// because "Moment" and "Your pebble" are already literal keys with other uses.
/// Reused copy (`Visibility.label` / `explanation`, `Valence.headline`, "Edit
/// moment", "Edit pebble", "Delete", "Make it a pebble", "Share", "Back",
/// "More") keeps its own keys. New pattern, named in the E1a PR.
enum CardStrings {
    static let turn = LocalizedStringResource(
        "card.turn", defaultValue: "Turn", comment: "Held card footer: small caps over where the turn leads")
    static let turnToPebble = LocalizedStringResource(
        "card.turn.toPebble", defaultValue: "Pebble", comment: "Held card footer: the turn leads to the pebble (verso)")
    static let turnToMoment = LocalizedStringResource(
        "card.turn.toMoment", defaultValue: "Moment", comment: "Held card footer: the turn leads to the moment (recto)")
    static let turnActionToPebble = LocalizedStringResource(
        "card.turn.action.toPebble", defaultValue: "Turn over to your pebble",
        comment: "VoiceOver: the held card's turn button on the recto")
    static let turnActionToMoment = LocalizedStringResource(
        "card.turn.action.toMoment", defaultValue: "Turn over to the moment",
        comment: "VoiceOver: the held card's turn button on the verso")
    static let versoHeading = LocalizedStringResource(
        "card.verso.heading", defaultValue: "Your pebble", comment: "VoiceOver: the verso's heading")
    static let domainsA11y = LocalizedStringResource(
        "card.domains.a11y", defaultValue: "Life domains", comment: "VoiceOver: before the verso's domain names")
    static let collectionsA11y = LocalizedStringResource(
        "card.collections.a11y", defaultValue: "Collections", comment: "VoiceOver: before the band's collection names")
    static let placeKicker = LocalizedStringResource(
        "bare.place.kicker", defaultValue: "Not a pebble yet", comment: "Bare moment: the kicker over its card's place")
    static let placeBody = LocalizedStringResource(
        "bare.place.body",
        defaultValue: "Its card prints here once you say what it meant: a feeling and its weight, the parts of your life it touched, who was there.",
        comment: "Bare moment: the sentence in its card's place")
}
```

Wrap the `placeBody` `defaultValue` line if SwiftLint warns on `line_length`.

- [ ] **Step 4: The catalogue entries**

In `apps/ios/Pebbles/Resources/Localizable.xcstrings` (case-insensitive key order):

1. `grep -c '^    "Blocking also stops them joining again through your invite link." : {$' apps/ios/Pebbles/Resources/Localizable.xcstrings` prints `1`. Insert immediately **before** that line:

```json
    "bare.place.body" : {
      "comment" : "Bare moment: the sentence in its card's place",
      "extractionState" : "manual",
      "localizations" : {
        "en" : {
          "stringUnit" : {
            "state" : "translated",
            "value" : "Its card prints here once you say what it meant: a feeling and its weight, the parts of your life it touched, who was there."
          }
        },
        "fr" : {
          "stringUnit" : {
            "state" : "translated",
            "value" : "Sa carte s'imprimera ici quand tu diras ce qu'il a compté : un sentiment et son poids, les parts de ta vie qu'il a touchées, qui était là."
          }
        }
      }
    },
    "bare.place.kicker" : {
      "comment" : "Bare moment: the kicker over its card's place",
      "extractionState" : "manual",
      "localizations" : {
        "en" : {
          "stringUnit" : {
            "state" : "translated",
            "value" : "Not a pebble yet"
          }
        },
        "fr" : {
          "stringUnit" : {
            "state" : "translated",
            "value" : "Pas encore un galet"
          }
        }
      }
    },
```

2. `grep -c '^    "Carve new glyph" : {$' apps/ios/Pebbles/Resources/Localizable.xcstrings` prints `1`. Insert immediately **before** that line the eight `card.*` entries, each in exactly the shape above (`"comment"` from `CardStrings.swift`, `"extractionState" : "manual"`, `en` and `fr` units `"state" : "translated"`), in this order and with these values:

| Key | `comment` | `en` | `fr` |
|---|---|---|---|
| `card.collections.a11y` | VoiceOver: before the band's collection names | Collections | Collections |
| `card.domains.a11y` | VoiceOver: before the verso's domain names | Life domains | Domaines de vie |
| `card.turn` | Held card footer: small caps over where the turn leads | Turn | Retourner |
| `card.turn.action.toMoment` | VoiceOver: the held card's turn button on the verso | Turn over to the moment | Retourner vers le moment |
| `card.turn.action.toPebble` | VoiceOver: the held card's turn button on the recto | Turn over to your pebble | Retourner vers ton galet |
| `card.turn.toMoment` | Held card footer: the turn leads to the moment (recto) | Moment | Moment |
| `card.turn.toPebble` | Held card footer: the turn leads to the pebble (verso) | Pebble | Galet |
| `card.verso.heading` | VoiceOver: the verso's heading | Your pebble | Ton galet |

The first of them, for the shape:

```json
    "card.collections.a11y" : {
      "comment" : "VoiceOver: before the band's collection names",
      "extractionState" : "manual",
      "localizations" : {
        "en" : {
          "stringUnit" : {
            "state" : "translated",
            "value" : "Collections"
          }
        },
        "fr" : {
          "stringUnit" : {
            "state" : "translated",
            "value" : "Collections"
          }
        }
      }
    },
```

3. Check the file is still JSON and only grew:

```bash
python3 -c "import json,sys; json.load(open('apps/ios/Pebbles/Resources/Localizable.xcstrings')); print('valid')"
git diff --stat apps/ios/Pebbles/Resources/Localizable.xcstrings
```

Expected: `valid`; about 190 insertions, 0 deletions.

- [ ] **Step 5: Run the test to verify it passes**

Run the **Suite command** with `CardStringsTests`. Expected: PASS (2).

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings apps/ios/Pebbles/Features/Path/Card/CardStrings.swift apps/ios/PebblesTests/Card/CardStringsTests.swift
git commit -m "feat(ios): the held card's strings in english and french"
```

---

### Task E1a.11: E1a verification and PR

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**. Open `Localizable.xcstrings` in Xcode: no `New` or `Stale` entry, every `card.*` and `bare.place.*` row has `en` and `fr`.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <E1a-number> --title "feat(ios): the held card's logic and strings" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Part of #1146`; key files (the file map's E1a rows); the vector mapping (Android `RingFitTest` → `RingFitTests` verbatim plus the Turkish capitals; `ValenceLockupTest` → `ValenceLockupTests`, scale read as rank; `PostmarkNoiseTest` → `PostmarkNoiseTests` with SplitMix64 for Kotlin's `Random`); that `CardLayout.cardWidth` has no Android test, so its vectors are its formula's (the Spine's `CardSizes` came from web in D1.10); decision 1's measurement and decision 2's ramp; **new pattern for review:** dotted catalogue keys through `LocalizedStringResource(_:defaultValue:comment:)`; `Visibility.explanation` moved out of `CompletePrivacyStep`. No Lab Note, no `F-…` ids.

---
## Part E1b — `feat/1146-ios-card-faces`

PR title: `feat(ios): the held card's recto, verso and footer`. Body opens `Part of #1146`. The card as views, proven by references; no screen shows it until E1c. The split of `SpineCard` comes first (a pure move), then the faces, the footer and the card.

### Task E1b.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1146-ios-card-faces
git branch --show-current
```

Expected: `feat/1146-ios-card-faces`.

---

### Task E1b.1: Split `SpineCard` into `SpineCardShell` and `SpineCardFace` (a pure move)

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCardShell.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCardFace.swift`
- Modify (replace): `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCard.swift`

**Move, do not retype.** The code below is stack D's `SpineCard` as its plan (Task D2b.8) wrote it, regrouped. Before writing, `diff` it against `main`'s file: every member here must be `main`'s member, byte for byte, with only `valence.polarity` → `polarity` in the shell and the `spot` property moved to the face. If `main`'s `SpineCard` differs from D2b.8 (a review changed a constant, a modifier), `main` wins: move `main`'s lines. The proof is that `SpineCardReferences`, `SpineDayReferences` and `SpinePageReferences` pass **unchanged** on CI (no rebaseline for this commit).

- [ ] **Step 1: The shell**

Create `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCardShell.swift`:

```swift
import SwiftUI

/// The card's body without its print (#1146 §Component tree, moved out of
/// `SpineCard`): the stock on its 11 pt radius, the painted edge (2 / 3.5 /
/// 5.5 pt in the edge colour) and the rule set's two-layer shadow, on the
/// ground only, so no text casts one. The Spine wraps one `SpineCardFace`; the
/// held card wraps a turning face and its still footer.
///
/// The ink and its alpha are Android's (`SpineColors.shadowInk` / `shadowAlpha`);
/// SwiftUI has no shadow spread, so each layer takes a share of that alpha to
/// read as the web's `0 1px 2px` and `0 18px 30px -22px`.
struct SpineCardShell<Content: View>: View {
    let palette: StonePalette
    let polarity: ValencePolarity
    let intensity: Int
    @ViewBuilder let content: () -> Content

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        content().background { ground }
    }

    private var ground: some View {
        let shape = RoundedRectangle(cornerRadius: 11, style: .continuous)
        let ink = SpineColors.shadowInk(scheme: scheme)
        let alpha = SpineColors.shadowAlpha(scheme: scheme)
        return shape.fill(Color.pebbles.stock)
            .background(
                shape.fill(SpineColors.edge(palette, polarity: polarity, scheme: scheme))
                    .offset(y: CardSizes.edge(intensity))
            )
            .shadow(color: ink.opacity(alpha * 0.3), radius: 1, x: 0, y: 1)
            .shadow(color: ink.opacity(alpha * 0.55), radius: 15, x: 0, y: 18)
    }
}
```

- [ ] **Step 2: The face**

Create `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCardFace.swift`:

```swift
import SwiftUI
import os

/// The card's print (#1130 R4–R5, moved out of `SpineCard` for #1146): the
/// photo uncropped at its ratio in a tinted field with a 1.8u mat, the index
/// (day and weekday) and the rail (time | emotion) in the gutters, the title in
/// its valence cut, the souls strip and the inset rule, in card units `u`.
/// `ratio` is the ratio to draw (nil holds the square); the caller eases it.
/// The Spine passes `u = min(width / 70, 4.4)`, the held card `width / 70`.
struct SpineCardFace: View {
    let moment: MomentSummary
    let palette: StonePalette
    let ratio: CGFloat?
    let width: CGFloat
    let u: CGFloat
    let soulNames: [String: String]
    let soulGlyphs: [String: [GlyphStroke]]
    let soulOrder: [String]
    /// Up to this many souls are named in full: 3 on the Spine (R7), 8 on the held card.
    var namedInFull = 3

    @Environment(\.colorScheme) private var scheme
    @Environment(\.spineSnapSource) private var snaps
    @State private var photoURL: URL?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "spine")

    private var locale: Locale { SpineStrings.appLocale }
    private var calendar: Calendar { SpineCalendar.current }
    private var valence: Valence { moment.pebble?.valence ?? .neutralMedium }
    private var spot: Color { SpineColors.spot(palette, scheme: scheme) }

    var body: some View {
        VStack(spacing: 2.6 * u) {
            field(width: width, u: u)
            title(u: u)
            strip(u: u)
        }
        .padding(EdgeInsets(top: 4.6 * u, leading: 9 * u, bottom: 6.2 * u, trailing: 9 * u))
        .frame(width: width)
        .overlay(alignment: .topLeading) { index(u: u) }
        .overlay(alignment: .topTrailing) { rail(u: u) }
        .overlay {
            RoundedRectangle(cornerRadius: 1.3 * u)
                .strokeBorder(Color.pebbles.cardRule, lineWidth: 1)
                .padding(1.6 * u)
                .accessibilityHidden(true)
        }
        .task(id: moment.firstSnapPath) {
            guard let path = moment.firstSnapPath, snaps.image(path) == nil else { return }
            photoURL = await snaps.url(path)
        }
    }

    // MARK: - Field and photo

    private func field(width: CGFloat, u: CGFloat) -> some View {
        let photoWidth = width - 21.6 * u
        let photoHeight = photoWidth / (ratio ?? CardSizes.pendingRatio)
        return ZStack {
            SpineCardField(polarity: valence.polarity, spot: spot, tint: SpineColors.tintK(scheme: scheme),
                           pattern: SpineColors.patternK(scheme: scheme),
                           size: CGSize(width: photoWidth + 3.6 * u, height: photoHeight + 3.6 * u))
            photo(u: u).frame(width: photoWidth, height: photoHeight)
        }
        .accessibilityHidden(true)
    }

    /// Uncropped: `scaledToFit`, so a photo whose ratio is still pending sits
    /// letterboxed in the square rather than cut.
    @ViewBuilder
    private func photo(u: CGFloat) -> some View {
        if let path = moment.firstSnapPath, let image = snaps.image(path) {
            Image(uiImage: image).resizable().scaledToFit()
        } else if let photoURL {
            AsyncImage(url: photoURL) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFit()
                case .failure(let error):
                    unavailable(u: u).onAppear {
                        logger.warning("spine card photo failed: \(error.localizedDescription, privacy: .private)")
                    }
                default:
                    bed
                }
            }
        } else {
            bed
        }
    }

    private var bed: some View {
        Rectangle().fill(spot.opacity(0.18))
    }

    private func unavailable(u: CGFloat) -> some View {
        bed.overlay {
            Text(verbatim: SpineCopy.photoUnavailable(locale: locale))
                .spineType(.cardOthers(u: u))
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
    }

    // MARK: - Title and strip

    @ViewBuilder
    private func title(u: CGFloat) -> some View {
        if let name = moment.name, !name.isEmpty {
            Text(verbatim: name)
                .spineType(.cardTitle(u: u, cut: EntryTypes.cardTitleCut(valence: valence.positiveness)))
                .foregroundStyle(Color.pebbles.onSurface)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        } else {
            Text(verbatim: SpineCopy.untitled(locale: locale))
                .spineType(.cardTitle(u: u, cut: .untitled(size: 4.4)))
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .frame(maxWidth: .infinity)
        }
    }

    @ViewBuilder
    private func strip(u: CGFloat) -> some View {
        let line = PeopleLines.peopleLine(moment.soulIds, order: soulOrder, locale: locale, namedInFull: namedInFull)
        let others = SpineCopy.others(count: line.others, words: line.othersWords, locale: locale)
        let parts = PeopleLineParts.parts(line, names: names, with: "", others: others, style: .card)
        if !parts.isEmpty {
            PeopleLineView(parts: parts, glyphs: soulGlyphs, markSide: 4 * u, base: .cardOthers(u: u),
                           name: .cardSoul(u: u), aside: .cardOthers(u: u), more: .cardMore(u: u), alignment: .center)
                .frame(maxWidth: .infinity)
        }
    }

    /// The page's names, falling back to the names `path_moments` carries on the pebble.
    private var names: [String: String] {
        var names = soulNames
        for soul in moment.pebble?.souls ?? [] {
            let key = soul.id.uuidString.lowercased()
            names[key] = names[key] ?? soul.name
        }
        return names
    }

    // MARK: - Gutters

    private func index(u: CGFloat) -> some View {
        VStack(spacing: 0.4 * u) {
            Text(verbatim: "\(calendar.component(.day, from: moment.happenedAt))")
                .spineType(.cardIndexNumeral(u: u))
                .foregroundStyle(Color.pebbles.onSurface)
            Text(verbatim: SpineDates.shortWeekday(moment.happenedAt, calendar: calendar, locale: locale))
                .spineType(.cardIndexDay(u: u))
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
        .frame(width: 9 * u)
        .padding(.top, 4.6 * u)
        .accessibilityHidden(true)
    }

    private func rail(u: CGFloat) -> some View {
        QuarterTurn {
            HStack(spacing: 1.2 * u) {
                Text(verbatim: SpineDates.time(moment.happenedAt, calendar: calendar, locale: locale))
                    .spineType(.cardRail(u: u))
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                if let emotion = moment.pebble?.emotion {
                    Rectangle().fill(Color.pebbles.cardRule).frame(width: 1, height: 1.8 * u)
                    Text(verbatim: emotion.localizedName)
                        .spineType(.cardRailEmotion(u: u))
                        .foregroundStyle(spot)
                }
            }
            .fixedSize()
            .rotationEffect(.degrees(90))
        }
        .frame(width: 9 * u)
        .padding(.top, 4.6 * u)
        .accessibilityHidden(true)
    }
}

/// Lays one child out a quarter turn: it reports the child's ideal size with
/// the sides swapped and centres the child, so the child's own
/// `.rotationEffect(.degrees(90))` lands inside the swapped box. Measured in
/// layout, so it holds under `ImageRenderer` too.
private struct QuarterTurn: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let child = subviews.first else { return .zero }
        let size = child.sizeThatFits(.unspecified)
        return CGSize(width: size.height, height: size.width)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard let child = subviews.first else { return }
        let size = child.sizeThatFits(.unspecified)
        child.place(at: CGPoint(x: bounds.midX, y: bounds.midY), anchor: .center, proposal: ProposedViewSize(size))
    }
}
```

- [ ] **Step 3: `SpineCard` keeps its frame, its registry and its ease**

Replace the contents of `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCard.swift` with:

```swift
import SwiftUI

/// The Deck card (#1130 R4–R5): a `SpineCardFace` on a `SpineCardShell`, sized
/// by `CardSizes` at the photo's ratio, `u = min(width / 70, 4.4)`. Until the
/// ratio is known the field is square, then it eases to the ratio over 240 ms
/// (snaps under Reduce Motion).
///
/// No button of its own: `SpinePebble` wraps it. It reports its global frame to
/// `CardFrameRegistry` for the held card and hides while the held card flies.
struct SpineCard: View {
    let moment: MomentSummary
    let palette: StonePalette
    /// The photo's ratio once probed; nil holds the square.
    let ratio: CGFloat?
    let column: CGFloat
    let soulNames: [String: String]
    let soulGlyphs: [String: [GlyphStroke]]
    /// The persons-of-the-week order, so the strip names people as the page does.
    let soulOrder: [String]
    /// Up to this many souls are named in full: 3 on the Spine (R7), 8 on the held card.
    var namedInFull = 3

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(CardFrameRegistry.self) private var registry: CardFrameRegistry?
    @State private var shownRatio: CGFloat?

    init(moment: MomentSummary, palette: StonePalette, ratio: CGFloat?, column: CGFloat,
         soulNames: [String: String], soulGlyphs: [String: [GlyphStroke]], soulOrder: [String], namedInFull: Int = 3) {
        self.moment = moment
        self.palette = palette
        self.ratio = ratio
        self.column = column
        self.soulNames = soulNames
        self.soulGlyphs = soulGlyphs
        self.soulOrder = soulOrder
        self.namedInFull = namedInFull
        // A ratio already known draws at once (and under ImageRenderer); a later one eases.
        _shownRatio = State(initialValue: ratio)
    }

    private var intensity: Int { moment.pebble?.intensity ?? 2 }
    private var valence: Valence { moment.pebble?.valence ?? .neutralMedium }

    var body: some View {
        let size = CardSizes.cardSize(intensity: intensity, ratio: shownRatio, column: column)
        let u = min(size.width / 70, 4.4)
        SpineCardShell(palette: palette, polarity: valence.polarity, intensity: intensity) {
            SpineCardFace(moment: moment, palette: palette, ratio: shownRatio, width: size.width, u: u,
                          soulNames: soulNames, soulGlyphs: soulGlyphs, soulOrder: soulOrder, namedInFull: namedInFull)
        }
        .opacity(registry?.hidden == moment.id ? 0 : 1)
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { frame in
            registry?.report(moment.id, frame: frame)
        }
        .onDisappear { registry?.forget(moment.id) }
        .task(id: ratio) {
            guard let ratio, ratio != shownRatio else { return }
            if reduceMotion {
                shownRatio = ratio
            } else {
                withAnimation(.easeOut(duration: 0.24)) { shownRatio = ratio }
            }
        }
    }
}
```

- [ ] **Step 4: Build, references, commit**

Run **Build**, **Lint** and the **Suite command** with `ThemeLiteralsTests`. Expected: `** BUILD SUCCEEDED **`, no SwiftLint error, PASS. Run **Look** at `SpineCardReferences` and compare `ratios-paper-en.png` with the committed one (`git stash` is not needed: Look writes over the committed PNGs, so `git diff --stat apps/ios/PebblesTests/References/__References__/SpineCardReferences` must print **nothing**, then clean as **Look** says). A diff means the move changed a pixel: find the line, do not re-baseline.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCardShell.swift apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCardFace.swift apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCard.swift
git commit -m "quality(ios): split the spine card into its shell and its face"
```

---

### Task E1b.2: The face's slots: a slip, a leaf, and what VoiceOver reads on the card

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCardFace.swift`

The held card adds three things the Spine never passes, so the Spine renders unchanged (its slot `Button` ignores its children for VoiceOver): a `slip` in place of the photo (F3), a `leaf` under the legend, and `accessible`, which gives the title its heading, the strip one label and the photo its description, in the card's reading order (spec §VoiceOver: title, date and time with the emotion, souls, photo, words).

- [ ] **Step 1: The new members**

In `SpineCardFace.swift`, replace

```swift
    /// Up to this many souls are named in full: 3 on the Spine (R7), 8 on the held card.
    var namedInFull = 3

    @Environment(\.colorScheme) private var scheme
```

with

```swift
    /// Up to this many souls are named in full: 3 on the Spine (R7), 8 on the held card.
    var namedInFull = 3
    /// The held card's recto: VoiceOver reads the face in the card's order (§VoiceOver).
    var accessible = false
    /// Stands in the field's mat in place of the photo (the held card's F3) and
    /// carries the name, so the legend does not repeat it.
    var slip: AnyView?
    /// Lays out under the legend, across the printed border (the held card's words).
    var leaf: AnyView?

    @Environment(\.colorScheme) private var scheme
```

- [ ] **Step 2: The body**

Replace

```swift
        VStack(spacing: 2.6 * u) {
            field(width: width, u: u)
            title(u: u)
            strip(u: u)
        }
        .padding(EdgeInsets(top: 4.6 * u, leading: 9 * u, bottom: 6.2 * u, trailing: 9 * u))
        .frame(width: width)
        .overlay(alignment: .topLeading) { index(u: u) }
        .overlay(alignment: .topTrailing) { rail(u: u) }
        .overlay {
```

with

```swift
        VStack(spacing: 0) {
            VStack(spacing: 2.6 * u) {
                field(width: width, u: u)
                if slip == nil { title(u: u) }
                strip(u: u)
            }
            // Over a leaf, the legend's foot is the score's head room (4.4u).
            .padding(EdgeInsets(top: 4.6 * u, leading: 9 * u, bottom: leaf == nil ? 6.2 * u : 4.4 * u, trailing: 9 * u))
            .frame(width: width)
            .overlay(alignment: .topLeading) { index(u: u) }
            .overlay(alignment: .topTrailing) { rail(u: u) }
            if let leaf {
                // Inside the printed border, 1.6u in, so the score runs from rule to rule.
                leaf.padding(.horizontal, 1.6 * u)
            }
        }
        .frame(width: width)
        .overlay {
```

- [ ] **Step 3: The field takes the slip, and names the photo on the card**

Replace

```swift
    private func field(width: CGFloat, u: CGFloat) -> some View {
        let photoWidth = width - 21.6 * u
```

with

```swift
    @ViewBuilder
    private func field(width: CGFloat, u: CGFloat) -> some View {
        if let slip {
            // The mat round a slip is wider than round a photo (2.6u).
            slip
                .padding(2.6 * u)
                .frame(width: width - 18 * u)
                .background {
                    GeometryReader { proxy in
                        SpineCardField(polarity: valence.polarity, spot: spot, tint: SpineColors.tintK(scheme: scheme),
                                       pattern: SpineColors.patternK(scheme: scheme), size: proxy.size)
                    }
                    .accessibilityHidden(true)
                }
        } else if accessible {
            photoField(width: width, u: u)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text("Photo"))
                .accessibilityAddTraits(.isImage)
                .accessibilitySortPriority(2)
        } else {
            photoField(width: width, u: u)
                .accessibilityHidden(true)
        }
    }

    private func photoField(width: CGFloat, u: CGFloat) -> some View {
        let photoWidth = width - 21.6 * u
```

Then, in the function this turns into `photoField`, delete its last modifier, `        .accessibilityHidden(true)` (the line after the `ZStack`'s closing brace): the branches above now decide whether the photo is read.

- [ ] **Step 4: The title is a heading, the strip one label**

Replace

```swift
                .spineType(.cardTitle(u: u, cut: EntryTypes.cardTitleCut(valence: valence.positiveness)))
                .foregroundStyle(Color.pebbles.onSurface)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
```

with

```swift
                .spineType(.cardTitle(u: u, cut: EntryTypes.cardTitleCut(valence: valence.positiveness)))
                .foregroundStyle(Color.pebbles.onSurface)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .accessibilityAddTraits(.isHeader)
                .accessibilitySortPriority(5)
```

Replace

```swift
            PeopleLineView(parts: parts, glyphs: soulGlyphs, markSide: 4 * u, base: .cardOthers(u: u),
                           name: .cardSoul(u: u), aside: .cardOthers(u: u), more: .cardMore(u: u), alignment: .center)
                .frame(maxWidth: .infinity)
        }
    }
```

with

```swift
            PeopleLineView(parts: parts, glyphs: soulGlyphs, markSide: 4 * u, base: .cardOthers(u: u),
                           name: .cardSoul(u: u), aside: .cardOthers(u: u), more: .cardMore(u: u), alignment: .center)
                .frame(maxWidth: .infinity)
                // Everyone, once, as the locale lists them: one stop, not name by name.
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: everyone))
                .accessibilitySortPriority(3)
        }
    }

    private var everyone: String {
        moment.soulIds.compactMap { names[$0] }.formatted(.list(type: .and).locale(locale))
    }
```

On the Spine these modifiers change nothing: `SpinePebble`'s slot is `.accessibilityElement(children: .ignore)` with its own label.

- [ ] **Step 5: Build, references, commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`. Run **Look** at `SpineCardReferences`: `git diff --stat` on its PNGs prints nothing (no slot is passed on the Spine). Clean.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCardFace.swift
git commit -m "feat(ios): the spine card's face takes a slip, a leaf and the card's reading order"
```

---

### Task E1b.3: `CardLeaf` and `CardSlip`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/CardLeaf.swift`
- Create: `apps/ios/Pebbles/Features/Path/Card/CardSlip.swift`

- [ ] **Step 1: The leaf**

Create `apps/ios/Pebbles/Features/Path/Card/CardLeaf.swift`:

```swift
import SwiftUI

/// The recto's leaf (#1146 §Recto): under a dashed score, where the card was
/// folded on the Path, the full dateline and the words; with no words, the
/// dateline alone. VoiceOver reads the dateline with the emotion straight
/// after the title, and the words last (§VoiceOver).
struct CardLeaf: View {
    let dateline: String
    /// What VoiceOver reads for the dateline: the date and time, then the emotion.
    let datelineLabel: String
    let words: String?
    let u: CGFloat

    var body: some View {
        VStack(spacing: 0) {
            Canvas { context, size in
                var path = Path()
                path.move(to: CGPoint(x: 0, y: size.height / 2))
                path.addLine(to: CGPoint(x: size.width, y: size.height / 2))
                context.stroke(path, with: .color(Color.pebbles.cardRule),
                               style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
            }
            .frame(height: 1)
            .accessibilityHidden(true)
            VStack(spacing: 14) {
                Text(verbatim: dateline)
                    .spineType(.cardLeafDateline)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel(Text(verbatim: datelineLabel))
                    .accessibilitySortPriority(4)
                if let words, !words.isEmpty {
                    Text(verbatim: words)
                        .spineType(.cardLeafBody)
                        .foregroundStyle(Color.pebbles.onSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilitySortPriority(1)
                }
            }
            .padding(EdgeInsets(top: 4.4 * u, leading: 3.6 * u, bottom: 6.2 * u, trailing: 3.6 * u))
        }
    }
}
```

- [ ] **Step 2: The slip**

Create `apps/ios/Pebbles/Features/Path/Card/CardSlip.swift`:

```swift
import SwiftUI

/// The photo-less recto's slip (#1146 §Recto, F3): a stock slip on the
/// emotion's mat carrying the name as the Spine's entry sets it, at 6.4 card
/// units, with the spot's foot line under it. It is the face's heading.
struct CardSlip: View {
    let name: String
    let cut: SpineType.Cut
    let spot: Color
    let u: CGFloat

    var body: some View {
        Text(verbatim: name)
            .spineType(.cardSlipName(u: u, cut: cut))
            .foregroundStyle(Color.pebbles.onSurface)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.vertical, 5 * u)
            .padding(.horizontal, 4 * u)
            .frame(maxWidth: .infinity, minHeight: 26 * u)
            .background(Color.pebbles.stock)
            .overlay(alignment: .bottom) {
                Rectangle()
                    .fill(spot.opacity(CardColors.slipFootAlpha))
                    .frame(height: 1)
                    .offset(y: 1)
                    .accessibilityHidden(true)
            }
            .accessibilityAddTraits(.isHeader)
            .accessibilitySortPriority(5)
    }
}
```

- [ ] **Step 3: Build and commit**

Run **Build** and the **Suite command** with `ThemeLiteralsTests`. Expected: `** BUILD SUCCEEDED **`, PASS.

```bash
git add apps/ios/Pebbles/Features/Path/Card/CardLeaf.swift apps/ios/Pebbles/Features/Path/Card/CardSlip.swift
git commit -m "feat(ios): the recto's leaf and the photo-less slip"
```

---

### Task E1b.4: `CardRecto`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/CardRecto.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI

/// The recto (#1146 §Recto): the Spine card at the held card's width (a uniform
/// scale of the Spine card, `u = width / 70`), every soul named up to eight, the
/// photo at its own ratio, and the leaf with the full dateline and the words. A
/// pebble with no photo shows `CardSlip` in place of the photo (F3). The field
/// eases to the photo's ratio once probed, as the Spine card's does.
struct CardRecto: View {
    let detail: MomentDetail
    let pebble: MomentDetail.Pebble
    let palette: EmotionPalette?
    let ratio: CGFloat?
    let width: CGFloat
    let soulGlyphs: [String: [GlyphStroke]]
    /// The Spine's persons-of-the-week order; `[]` keeps the pebble's own.
    let soulOrder: [String]

    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shownRatio: CGFloat?

    init(detail: MomentDetail, pebble: MomentDetail.Pebble, palette: EmotionPalette?, ratio: CGFloat?,
         width: CGFloat, soulGlyphs: [String: [GlyphStroke]], soulOrder: [String]) {
        self.detail = detail
        self.pebble = pebble
        self.palette = palette
        self.ratio = ratio
        self.width = width
        self.soulGlyphs = soulGlyphs
        self.soulOrder = soulOrder
        _shownRatio = State(initialValue: ratio)
    }

    private var locale: Locale { SpineStrings.appLocale }
    private var stone: StonePalette { SpineColors.palette(palette, isBlank: false) }

    private var soulNames: [String: String] {
        Dictionary(pebble.souls.map { ($0.id.uuidString.lowercased(), $0.name) }, uniquingKeysWith: { first, _ in first })
    }

    var body: some View {
        let u = CardLayout.u(cardWidth: width)
        let dateline = CardDateline.text(detail.happenedAt, calendar: SpineCalendar.current, locale: locale)
        SpineCardFace(moment: MomentSummary(detail: detail), palette: stone, ratio: shownRatio, width: width, u: u,
                      soulNames: soulNames, soulGlyphs: soulGlyphs, soulOrder: soulOrder,
                      namedInFull: PeopleLines.namedOnCard, accessible: true,
                      slip: detail.snaps.isEmpty ? AnyView(slip(u: u)) : nil,
                      leaf: AnyView(CardLeaf(dateline: dateline,
                                             datelineLabel: dateline + ", " + pebble.emotion.localizedName,
                                             words: detail.description, u: u)))
            .accessibilityElement(children: .contain)
            .task(id: ratio) {
                guard let ratio, ratio != shownRatio else { return }
                if reduceMotion {
                    shownRatio = ratio
                } else {
                    withAnimation(.easeOut(duration: 0.24)) { shownRatio = ratio }
                }
            }
    }

    private func slip(u: CGFloat) -> some View {
        let name = detail.name.flatMap { $0.isEmpty ? nil : $0 } ?? SpineCopy.untitled(locale: locale)
        let cut = EntryTypes.entryType(intensity: pebble.intensity, valence: pebble.valence.positiveness).name
        return CardSlip(name: name, cut: cut, spot: SpineColors.spot(stone, scheme: scheme), u: u)
    }
}
```

- [ ] **Step 2: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Card/CardRecto.swift
git commit -m "feat(ios): the held card's recto, the spine card in the hand"
```

---

### Task E1b.5: `DomainPostmark` and `PostmarkRow`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/DomainPostmark.swift`
- Create: `apps/ios/Pebbles/Features/Path/Card/PostmarkRow.swift`
- Test: `apps/ios/PebblesTests/Card/PostmarkRingTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// #1146 §Postmark: the ring is fitted with the advances the draw loop steps
/// by, so it closes exactly; the first repetition's name is centred upright at
/// the top; spaces are not drawn.
@Suite("Postmark ring")
@MainActor
struct PostmarkRingTests {
    @Test("the ring closes: the drawn advances and the tracking span the circle")
    func closes() {
        let ring = PostmarkRing.layout(name: "Friends", diameter: 72, locale: Locale(identifier: "en"))
        #expect(ring.baseline == 72 / 2 - CardLayout.postmarkTextInset)
        let circumference = 2 * CGFloat.pi * ring.baseline
        #expect(abs(ring.span - circumference) < 0.5)
    }

    @Test("the first repetition's name is centred on the top")
    func centred() throws {
        let ring = PostmarkRing.layout(name: "Friends", diameter: 72, locale: Locale(identifier: "en"))
        let first = try #require(ring.glyphs.first)
        let seventh = ring.glyphs[6]
        #expect(first.character == "F" && seventh.character == "S")
        #expect(abs(first.angle + seventh.angle) < 0.02)
    }

    @Test("spaces take room but are not drawn")
    func spaces() {
        let ring = PostmarkRing.layout(name: "Current events", diameter: 61.5, locale: Locale(identifier: "en"))
        #expect(!ring.glyphs.contains { $0.character == " " })
        #expect(ring.glyphs.contains { $0.character == "·" })
    }
}
```

Save it as `apps/ios/PebblesTests/Card/PostmarkRingTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `PostmarkRingTests`. Expected: build FAIL with "cannot find 'PostmarkRing' in scope".

- [ ] **Step 3: The stamp**

Create `apps/ios/Pebbles/Features/Path/Card/DomainPostmark.swift`:

```swift
import SwiftUI
import UIKit
import os

/// D4: how the ink's texture lands. `.cut` (the decision) draws the noise into
/// the ink's own layer with `.destinationOut`, so the pits are true holes and
/// the panel shows through. `.speckle` is Android's: the noise tinted with the
/// panel's colour, laid over the ink in `.normal` blend, clipped to the disc.
/// The E1c device check decides; a failure flips `current` and nothing else.
enum PostmarkInkMode {
    case cut, speckle

    static let current: PostmarkInkMode = .cut
}

/// One ring character: `angle` clockwise from the top to its middle, in radians.
struct PostmarkRingGlyph: Equatable {
    let character: Character
    let angle: Double
}

/// The ring's layout (Android's `RingLayout.of`, the mock's `ring.js`): every
/// character advances by its own measured width plus the fit's tracking, and
/// the fit measures a string as the sum of those same widths, so the ring
/// closes exactly. Measured at font scale 1 through stack B's `postmarkRing`
/// rank, a deliberate exception: decoration in a fixed stamp, the names read
/// from the verso's list.
struct PostmarkRing {
    let glyphs: [PostmarkRingGlyph]
    /// The baseline's radius, in absolute points (the type does not scale).
    let baseline: CGFloat
    let size: CGFloat
    /// The drawn advances plus the tracking, once round.
    let span: CGFloat
    let uiFont: UIFont

    static func font(size: CGFloat) -> UIFont {
        SpineType.resolve(SpineType.postmarkRing(size), dynamicTypeSize: .large, legibilityWeight: nil).uiFont
    }

    static func layout(name: String, diameter: CGFloat, locale: Locale) -> PostmarkRing {
        let baseline = diameter / 2 - CardLayout.postmarkTextInset
        var fonts: [CGFloat: UIFont] = [:]
        func font(_ size: CGFloat) -> UIFont {
            if let known = fonts[size] { return known }
            let made = Self.font(size: size)
            fonts[size] = made
            return made
        }
        func advance(_ character: Character, _ size: CGFloat) -> CGFloat {
            (String(character) as NSString).size(withAttributes: [.font: font(size)]).width
        }
        let fit = RingFits.fit(name: name, circumference: 2 * .pi * baseline, locale: locale) { text, size in
            text.reduce(0) { $0 + advance($1, size) }
        }
        let characters = Array(fit.text)
        let unitLength = characters.count / max(fit.repetitions, 1)
        let nameLength = unitLength - RingFits.separator.count
        var nameSpan: CGFloat = 0
        for index in 0..<max(nameLength, 0) {
            nameSpan += advance(characters[index], fit.size) + (index < nameLength - 1 ? fit.tracking : 0)
        }
        var arc = -nameSpan / 2
        var glyphs: [PostmarkRingGlyph] = []
        for character in characters {
            let width = advance(character, fit.size)
            if !character.isWhitespace {
                glyphs.append(PostmarkRingGlyph(character: character, angle: Double((arc + width / 2) / baseline)))
            }
            arc += width + fit.tracking
        }
        return PostmarkRing(glyphs: glyphs, baseline: baseline, size: fit.size, span: arc + nameSpan / 2,
                            uiFont: font(fit.size))
    }
}

/// The noise as an image whose alpha is `PostmarkNoise`'s, for the draw to cut
/// with or tint. Its colour channels are zero (premultiplied), so no literal.
@MainActor
enum PostmarkNoiseImage {
    private static var cache: [UInt64: Image] = [:]
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "held-card")

    static func image(seed: UInt64) -> Image? {
        if let cached = cache[seed] { return cached }
        let size = CardLayout.noiseSize
        let alphas = PostmarkNoise.alphas(size: size, seed: seed)
        var pixels = [UInt8](repeating: 0, count: size * size * 4)
        for (index, alpha) in alphas.enumerated() { pixels[index * 4 + 3] = alpha }
        guard let provider = CGDataProvider(data: Data(pixels) as CFData),
              let cgImage = CGImage(width: size, height: size, bitsPerComponent: 8, bitsPerPixel: 32,
                                    bytesPerRow: size * 4,
                                    space: CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB(),
                                    bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                                    provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
        else {
            logger.error("postmark noise image could not be made; the stamp draws without texture")
            return nil
        }
        let image = Image(decorative: cgImage, scale: 1)
        cache[seed] = image
        return image
    }
}

/// One life domain as a round ink stamp (#1146 §Postmark): the glyph in the
/// middle, the name ringing the edge per `RingFits`, an outer and an inner ring,
/// in `ink`, with the restrained texture of D4. Drawn in one `Canvas`, its ink
/// in one `drawLayer`, flattened once by `.drawingGroup()`. No rotation, no
/// shadow, no tap; hidden from VoiceOver (`PostmarkRow` reads the names).
struct DomainPostmark: View {
    let name: String
    let strokes: [GlyphStroke]?
    let ink: Color
    /// The panel's colour, which the speckle fallback paints with.
    let paper: Color
    let seed: UInt64
    let diameter: CGFloat
    var mode: PostmarkInkMode = .current

    private enum Symbol: Hashable { case glyph }

    var body: some View {
        let ring = PostmarkRing.layout(name: name, diameter: diameter, locale: SpineStrings.appLocale)
        let noise = PostmarkNoiseImage.image(seed: seed)
        let scale = diameter / CardLayout.postmarkDiameter
        Canvas { context, size in
            let centre = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = min(size.width, size.height) / 2
            let disc = CGRect(origin: .zero, size: size)
            func circle(_ r: CGFloat) -> Path {
                Path(ellipseIn: CGRect(x: centre.x - r, y: centre.y - r, width: 2 * r, height: 2 * r))
            }
            context.drawLayer { layer in
                layer.stroke(circle(radius - CardLayout.postmarkOuterRingInset * scale), with: .color(ink),
                             lineWidth: CardLayout.postmarkOuterRing * scale)
                layer.stroke(circle(ring.baseline - CardLayout.postmarkInnerRingDrop), with: .color(ink),
                             lineWidth: CardLayout.postmarkInnerRing * scale)
                let font = Font(ring.uiFont)
                for glyph in ring.glyphs {
                    var mark = layer
                    mark.translateBy(x: centre.x, y: centre.y)
                    mark.rotate(by: .radians(glyph.angle))
                    var text = mark.resolve(Text(verbatim: String(glyph.character)).font(font))
                    text.shading = .color(ink)
                    let measured = text.measure(in: CGSize(width: 100, height: 100))
                    let anchorY = measured.height > 0 ? text.firstBaseline(in: measured) / measured.height : 1
                    mark.draw(text, at: CGPoint(x: 0, y: -ring.baseline), anchor: UnitPoint(x: 0.5, y: anchorY))
                }
                if let symbol = layer.resolveSymbol(id: Symbol.glyph) {
                    layer.draw(symbol, at: centre)
                }
                guard let noise else { return }
                switch mode {
                case .cut:
                    layer.blendMode = .destinationOut
                    layer.draw(noise, in: disc)
                case .speckle:
                    layer.clip(to: Path(ellipseIn: disc))
                    layer.clipToLayer { mask in mask.draw(noise, in: disc) }
                    layer.fill(Path(disc), with: .color(paper))
                }
            }
        } symbols: {
            if let strokes, !strokes.isEmpty {
                GlyphThumbnail(strokes: strokes, side: CardLayout.postmarkGlyph * scale, strokeColor: ink)
                    .tag(Symbol.glyph)
            }
        }
        .frame(width: diameter, height: diameter)
        .drawingGroup()
        .accessibilityHidden(true)
    }
}
```

- [ ] **Step 4: The row**

Create `apps/ios/Pebbles/Features/Path/Card/PostmarkRow.swift`:

```swift
import SwiftUI

/// The verso's domains as one centred row of postmarks (#1146 §Verso): 72 pt,
/// or the row's share when four do not fit; the glyph from the reference
/// domains by id (none, the ring alone); the noise seeded by the domain's id,
/// so no two stamps share a pattern. VoiceOver reads one element, "Life
/// domains: Friends and Community"; the ring's repetitions are hidden.
struct PostmarkRow: View {
    let domains: [DomainRef]
    let references: [Domain]
    let ink: Color
    let paper: Color
    let rowWidth: CGFloat

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        if !domains.isEmpty {
            let diameter = CardLayout.postmarkDiameter(count: domains.count, rowWidth: rowWidth)
            HStack(spacing: CardLayout.postmarkGap) {
                ForEach(domains) { ref in
                    DomainPostmark(name: ref.localizedName,
                                   strokes: references.first { $0.id == ref.id }?.strokes,
                                   ink: ink, paper: paper, seed: UInt64(StableHash.seed(ref.id)), diameter: diameter)
                }
            }
            .frame(maxWidth: .infinity)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(CardStrings.domainsA11y)
                + Text(verbatim: ": " + domains.map(\.localizedName).formatted(.list(type: .and).locale(locale))))
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run the **Suite command** with `PostmarkRingTests`, then **Build**. Expected: PASS (3), `** BUILD SUCCEEDED **`. If `centred` fails by more than 0.02 rad, the first glyph is not "F": print `ring.glyphs.map(\.character)` and check that `RingFits.fit` kept `FRIENDS · ` as the unit.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/DomainPostmark.swift apps/ios/Pebbles/Features/Path/Card/PostmarkRow.swift apps/ios/PebblesTests/Card/PostmarkRingTests.swift
git commit -m "feat(ios): the domain postmarks, ink cut by their own texture"
```

---

### Task E1b.6: `CollectionBand`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/CollectionBand.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI

/// The pebble's collections as a paper band wrapped across the card's foot
/// (#1146 §Band): full bleed to the shell's edges, two hairlines in the spot
/// inside its long edges and a darker fold at each end where it turns under;
/// the names in italic, each led by its collection's glyph (#1145) or the stack
/// mark, a small diamond between two names on the same line only. No
/// collections, no band (the caller does not add it). One VoiceOver element.
struct CollectionBand: View {
    let collections: [MomentDetail.CollectionRef]
    let glyphs: [UUID: Glyph]
    let spot: Color

    @Environment(\.colorScheme) private var scheme

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        BandFlow(gap: 10, lineGap: 4) {
            ForEach(collections) { collection in
                HStack(spacing: 6) {
                    CollectionMark(glyph: glyphs[collection.id], ink: Color.pebbles.onSurface)
                    Text(verbatim: collection.name)
                        .spineType(.cardBandName)
                        .foregroundStyle(Color.pebbles.onSurface)
                        .multilineTextAlignment(.center)
                }
            }
            ForEach(collections.dropFirst()) { _ in
                Rectangle()
                    .fill(spot)
                    .frame(width: 5, height: 5)
                    .rotationEffect(.degrees(45))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, minHeight: CardLayout.bandMinHeight)
        .background { paper }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(CardStrings.collectionsA11y)
            + Text(verbatim: ": " + collections.map(\.name).formatted(.list(type: .and).locale(locale))))
    }

    private var paper: some View {
        ZStack {
            Color.pebbles.stock
            spot.opacity(CardColors.bandPaperK(scheme: scheme))
        }
        .overlay(alignment: .top) { hairline.padding(.top, 3) }
        .overlay(alignment: .bottom) { hairline.padding(.bottom, 3) }
        .overlay(alignment: .leading) { fold }
        .overlay(alignment: .trailing) { fold }
        .accessibilityHidden(true)
    }

    private var hairline: some View {
        Rectangle().fill(spot.opacity(CardColors.bandHairlineAlpha)).frame(height: 1)
    }

    private var fold: some View {
        Rectangle().fill(spot.opacity(CardColors.bandFoldAlpha)).frame(width: 3)
    }
}

/// A centred flow of the band's names (the first half of the subviews) with a
/// knot (the second half) between two names on the same line only
/// (`BandLines`). A name wider than the band wraps inside its own line.
struct BandFlow: Layout {
    var gap: CGFloat
    var lineGap: CGFloat

    private struct Arrangement {
        var lines: [[Int]]
        var names: [CGSize]
        var knot: CGSize
        var widths: [CGFloat]
        var heights: [CGFloat]
        var knotRun: CGFloat
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        let arrangement = arrange(maxWidth: maxWidth, subviews: subviews)
        let widest = arrangement.widths.max() ?? 0
        let height = arrangement.heights.reduce(0, +) + lineGap * CGFloat(max(0, arrangement.heights.count - 1))
        return CGSize(width: proposal.width ?? widest, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let arrangement = arrange(maxWidth: bounds.width, subviews: subviews)
        let count = arrangement.names.count
        var y = bounds.minY
        for (lineIndex, line) in arrangement.lines.enumerated() {
            let lineHeight = arrangement.heights[lineIndex]
            var x = bounds.minX + (bounds.width - arrangement.widths[lineIndex]) / 2
            for (position, index) in line.enumerated() {
                if position > 0 {
                    subviews[count + index - 1].place(
                        at: CGPoint(x: x + gap, y: y + (lineHeight - arrangement.knot.height) / 2),
                        anchor: .topLeading, proposal: ProposedViewSize(arrangement.knot))
                    x += arrangement.knotRun
                }
                let size = arrangement.names[index]
                subviews[index].place(at: CGPoint(x: x, y: y + (lineHeight - size.height) / 2),
                                      anchor: .topLeading, proposal: ProposedViewSize(size))
                x += size.width
            }
            y += lineHeight + lineGap
        }
    }

    private func arrange(maxWidth: CGFloat, subviews: Subviews) -> Arrangement {
        let count = (subviews.count + 1) / 2
        let nameProposal = ProposedViewSize(width: maxWidth.isFinite ? maxWidth : nil, height: nil)
        let names = (0..<count).map { subviews[$0].sizeThatFits(nameProposal) }
        let knot = subviews.count > count ? subviews[count].sizeThatFits(.unspecified) : .zero
        let knotRun = subviews.count > count ? gap * 2 + knot.width : 0
        let lines = BandLines.lines(widths: names.map(\.width), knotRun: knotRun, maxWidth: maxWidth)
        let widths = lines.map { BandLines.width(of: $0, widths: names.map(\.width), knotRun: knotRun) }
        let heights = lines.map { line in line.map { names[$0].height }.max() ?? 0 }
        return Arrangement(lines: lines, names: names, knot: knot, widths: widths, heights: heights, knotRun: knotRun)
    }
}
```


- [ ] **Step 2: Build and commit**

Run **Build** and the **Suite command** with `ThemeLiteralsTests`. Expected: `** BUILD SUCCEEDED **`, PASS.

```bash
git add apps/ios/Pebbles/Features/Path/Card/CollectionBand.swift
git commit -m "feat(ios): the collection band wrapped across the verso"
```

---

### Task E1b.7: `FaceMiniature` and `CardFooter`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/FaceMiniature.swift`
- Create: `apps/ios/Pebbles/Features/Path/Card/CardFooter.swift`

- [ ] **Step 1: The miniature**

Create `apps/ios/Pebbles/Features/Path/Card/FaceMiniature.swift`:

```swift
import SwiftUI

/// A thumbnail of the face the turn leads to (#1146 §Footer, the Label
/// exploration), tilted −7°: to the pebble, a 15 × 22 stock card with an inner
/// rule and a 9 × 7 stone in the emotion's spot at 45 % height; to the moment,
/// a photo block at 35 % opacity down to 40 % and three legend lines. 1 pt ink
/// outline. Decorative and unscaled: the turn button carries the label.
struct FaceMiniature: View {
    let to: CardFace
    let stone: Color

    var body: some View {
        Canvas { context, size in
            let card = CGSize(width: 15, height: 22)
            let origin = CGPoint(x: (size.width - card.width) / 2, y: (size.height - card.height) / 2)
            let rect = CGRect(origin: origin, size: card)
            let shape = RoundedRectangle(cornerRadius: 2, style: .continuous).path(in: rect)
            context.fill(shape, with: .color(Color.pebbles.stock))
            context.stroke(shape, with: .color(Color.pebbles.onSurface), lineWidth: 1)
            switch to {
            case .verso:
                let rule = RoundedRectangle(cornerRadius: 1).path(in: rect.insetBy(dx: 2, dy: 2))
                context.stroke(rule, with: .color(Color.pebbles.onSurface), lineWidth: 0.8)
                let centre = CGPoint(x: rect.midX, y: rect.minY + rect.height * 0.45)
                context.fill(Path(ellipseIn: CGRect(x: centre.x - 4.5, y: centre.y - 3.5, width: 9, height: 7)),
                             with: .color(stone))
            case .recto:
                let photo = CGRect(x: rect.minX + 2.3, y: rect.minY + 2.3, width: card.width - 4.6,
                                   height: card.height * 0.4 - 2.3)
                context.fill(Path(photo), with: .color(Color.pebbles.onSurface.opacity(0.35)))
                for (y, end) in [(13.0, 12.0), (15.8, 10.2), (18.6, 11.2)] {
                    var line = Path()
                    line.move(to: CGPoint(x: rect.minX + 3, y: rect.minY + y))
                    line.addLine(to: CGPoint(x: rect.minX + end, y: rect.minY + y))
                    context.stroke(line, with: .color(Color.pebbles.onSurface.opacity(0.6)),
                                   style: StrokeStyle(lineWidth: 0.8, lineCap: .round))
                }
            }
        }
        .frame(width: CardLayout.miniature.width, height: CardLayout.miniature.height)
        .rotationEffect(.degrees(CardLayout.miniatureTilt))
        .accessibilityHidden(true)
    }
}
```

- [ ] **Step 2: The footer**

Create `apps/ios/Pebbles/Features/Path/Card/CardFooter.swift`:

```swift
import SwiftUI

/// The card's still footer (#1146 §Footer), below the border inside the shell,
/// the same on both faces. Left: the privacy glyph, the grade in small caps and
/// the grade's sentence in italic, read as one element ("Secret. Only you can
/// see this one."). Right: the turn, no container, one target of at least
/// 44 pt from the label to the card's edge, never over the privacy sentence.
/// It names the face not in front (`turnTo`), and VoiceOver reaches it last.
struct CardFooter: View {
    let visibility: Visibility
    /// The face the button leads to: the one not in front.
    let turnTo: CardFace
    /// The emotion's spot, for the miniature's stone.
    let stone: Color
    let onTurn: () -> Void

    var body: some View {
        FooterLayout {
            privacy
            turn
        }
    }

    private var privacy: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 8) {
                Image(systemName: visibility.systemImageName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 16, height: 16)
                    .foregroundStyle(Color.pebbles.onSurface)
                Text(visibility.label)
                    .spineType(.cardFooterGrade)
                    .foregroundStyle(Color.pebbles.onSurface)
            }
            // The sentence hangs under the grade, past the glyph.
            Text(visibility.explanation)
                .spineType(.cardFooterNote)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.leading, 24)
        }
        .padding(.leading, CardLayout.footerPaddingH)
        .padding(.vertical, CardLayout.footerPaddingV)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(visibility.label) + Text(verbatim: ". ") + Text(visibility.explanation))
    }

    private var turn: some View {
        Button(action: onTurn) {
            HStack(spacing: 8) {
                VStack(alignment: .trailing, spacing: 0) {
                    Text(CardStrings.turn)
                        .spineType(.cardFooterGrade)
                        .foregroundStyle(Color.pebbles.onSurface)
                    Text(turnTo == .verso ? CardStrings.turnToPebble : CardStrings.turnToMoment)
                        .spineType(.cardTurnTarget)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
                FaceMiniature(to: turnTo, stone: stone)
            }
            .padding(.leading, 8)
            .padding(.trailing, CardLayout.footerPaddingH)
            .frame(minHeight: CardLayout.turnTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // The label is the action alone; no hint repeats it (§VoiceOver).
        .accessibilityLabel(Text(turnTo == .verso ? CardStrings.turnActionToPebble : CardStrings.turnActionToMoment))
    }
}

/// The footer's two arrangements (`FooterArrangement`): side by side while the
/// turn takes at most half the width, else the turn under the privacy,
/// trailing-aligned. Both rows are at least the turn target tall.
struct FooterLayout: Layout {
    private struct Sizes {
        var stacked: Bool
        var left: CGSize
        var leftWidth: CGFloat
        var turn: CGSize
        var height: CGFloat
    }

    private func sizes(width: CGFloat, subviews: Subviews) -> Sizes {
        let natural = subviews[1].sizeThatFits(.unspecified)
        let turn = CGSize(width: min(natural.width, width), height: natural.height)
        let stacked = FooterArrangement.stacked(turnWidth: turn.width, available: width)
        let leftWidth = stacked ? width : max(0, width - turn.width - CardLayout.footerGap)
        let left = subviews[0].sizeThatFits(ProposedViewSize(width: leftWidth, height: nil))
        let turnHeight = max(turn.height, CardLayout.turnTarget)
        let height = stacked ? left.height + turnHeight : max(left.height, turnHeight)
        return Sizes(stacked: stacked, left: left, leftWidth: leftWidth, turn: turn, height: height)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard subviews.count == 2 else { return .zero }
        let width = proposal.width ?? subviews[0].sizeThatFits(.unspecified).width
            + CardLayout.footerGap + subviews[1].sizeThatFits(.unspecified).width
        return CGSize(width: width, height: sizes(width: width, subviews: subviews).height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        let sizes = sizes(width: bounds.width, subviews: subviews)
        let leftProposal = ProposedViewSize(width: sizes.leftWidth, height: nil)
        if sizes.stacked {
            subviews[0].place(at: bounds.origin, anchor: .topLeading, proposal: leftProposal)
            subviews[1].place(at: CGPoint(x: bounds.maxX, y: bounds.minY + sizes.left.height), anchor: .topTrailing,
                              proposal: ProposedViewSize(sizes.turn))
        } else {
            subviews[0].place(at: CGPoint(x: bounds.minX, y: bounds.midY), anchor: .leading, proposal: leftProposal)
            subviews[1].place(at: CGPoint(x: bounds.maxX, y: bounds.midY), anchor: .trailing,
                              proposal: ProposedViewSize(sizes.turn))
        }
    }
}
```

- [ ] **Step 3: Build and commit**

Run **Build** and the **Suite command** with `ThemeLiteralsTests`. Expected: `** BUILD SUCCEEDED **`, PASS.

```bash
git add apps/ios/Pebbles/Features/Path/Card/FaceMiniature.swift apps/ios/Pebbles/Features/Path/Card/CardFooter.swift
git commit -m "feat(ios): the card's still footer, its privacy and its turn"
```

---

### Task E1b.8: `VersoStone`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/VersoStone.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI

/// The verso's lit stone (#1146 §Verso), 150 pt wide when large, in its rings:
/// stack D's `SpineStone` layers at the verso's size, so the print can press it
/// in with the Spine's dip, ripple and glint (E3). Drawn at its own width, never
/// scaled up (the lit stone is a shader layer). The tilt stays off on the card.
struct VersoStone: View {
    let valence: Valence
    let glyph: PebbleGlyph?
    let palette: EmotionPalette?
    let seed: UInt32
    var press: StonePress?

    private var size: ValenceSizeGroup { valence.sizeGroup }

    var body: some View {
        TimelineView(.animation(minimumInterval: nil, paused: press == nil)) { context in
            layers(frame(at: context.date))
        }
    }

    private func frame(at date: Date) -> StoneMotion.Frame {
        guard let press else { return StoneMotion.Frame() }
        return StoneMotion.frame(at: press.elapsed(at: date), still: press.still, size: size)
    }

    private func layers(_ frame: StoneMotion.Frame) -> some View {
        let stone = CardLayout.versoStoneSize(size)
        let geometry = StoneRingGeometry.forStone(valence)
        let reach = (StoneMotion.Wave.reach[size] ?? 5.6) * stone.width / SpineLayout.stoneWidth(size)
        return ZStack {
            if let geometry {
                StoneRings(geometry: geometry, stone: stone, scale: frame.rings, still: frame.stillRings)
                if !frame.waves.isEmpty {
                    StoneRipple(geometry: geometry, stone: stone, waves: frame.waves, reach: reach)
                }
            }
            PebbleStoneView(valence: valence, glyph: glyph, palette: palette, followsTilt: false, seed: seed)
                .frame(width: stone.width, height: stone.height)
                .scaleEffect(frame.dip)
            if let geometry {
                StoneGlint(outline: geometry.outline, stone: stone, frame: frame,
                           strength: StoneGlintInk.strength(valence.polarity))
                    .scaleEffect(frame.dip)
            }
        }
        .frame(width: stone.width, height: stone.height)
    }
}
```

If `StoneMotion.Wave.reach` is keyed or typed differently on `main` (`grep -n "static let reach" apps/ios/Pebbles/Features/Path/Spine/Stone/StoneMotion.swift`), read it as `SpineStone` does on `main` and keep the width scaling.

- [ ] **Step 2: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Card/VersoStone.swift
git commit -m "feat(ios): the verso's lit stone in its rings"
```

---

### Task E1b.9: `CardVerso` (decision 2)

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/CardVerso.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI

/// The verso (#1146 §Verso): the emotion's mat full-bleed round a stock panel,
/// and in it the valence lockup, the emotion once under a hairline, the lit
/// stone centred in the room left, and the domains as postmarks; the collection
/// band wraps across its foot. Given the face's height (`FaceStack` proposes
/// the taller face's), the stone centres between the emotion and the postmarks.
///
/// VoiceOver: "Your pebble" (a heading, whose value is the valence, "Highlight
/// of my month"), the emotion, the stone, the domains, the collections.
struct CardVerso: View {
    let pebble: MomentDetail.Pebble
    /// The moment as the Spine reads it, for the stone's description.
    let summary: MomentSummary
    let palette: EmotionPalette?
    let width: CGFloat
    let domains: [Domain]
    let collectionGlyphs: [UUID: Glyph]
    /// The print's timeline (E3); nil at rest.
    var print: Double?
    /// The verso stone's press, played by the print (E3).
    var stonePress: StonePress?

    @Environment(\.colorScheme) private var scheme
    @Environment(\.self) private var environment

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        let u = CardLayout.u(cardWidth: width)
        let stone = SpineColors.palette(palette, isBlank: false)
        let spot = SpineColors.spot(stone, scheme: scheme)
        let foot = CardLayout.bandFoot(u: u)
        // The panel's padding less the row's outset, so four stamps keep their size on a phone.
        let side = foot + 1.4 * u
        VersoStack(top: foot + 3.6 * u, side: side, foot: foot, bareFoot: 4.4 * u) {
            VStack(spacing: 0) {
                lockup(stone, u: u)
                Rectangle()
                    .fill(Color.pebbles.cardRule)
                    .frame(width: 24, height: 1)
                    .padding(.top, 2.2 * u)
                    .padding(.bottom, 2 * u)
                    .accessibilityHidden(true)
                Text(verbatim: pebble.emotion.localizedName)
                    .spineType(.cardEmotion)
                    .foregroundStyle(Color.pebbles.onSurface)
                Spacer(minLength: 0)
                clearing
                Spacer(minLength: 0)
                PostmarkRow(domains: pebble.domains, references: domains,
                            ink: stone.color(CardColors.postmarkInkSlot(scheme: scheme)),
                            paper: Color.pebbles.stock, rowWidth: width - 2 * side)
                    .opacity(print.map { PrintPart.postmarks.alpha(CompletionMotion.print($0)) } ?? 1)
            }
            if !pebble.collections.isEmpty {
                CollectionBand(collections: pebble.collections, glyphs: collectionGlyphs, spot: spot)
                    .opacity(print.map { PrintPart.band.alpha(CompletionMotion.print($0)) } ?? 1)
            }
        }
        .background { VersoFrame(polarity: pebble.valence.polarity, spot: spot, u: u) }
        .accessibilityElement(children: .contain)
    }

    private var soulNames: [String: String] {
        Dictionary(pebble.souls.map { ($0.id.uuidString.lowercased(), $0.name) }, uniquingKeysWith: { first, _ in first })
    }

    private func lockup(_ stone: StonePalette, u: CGFloat) -> some View {
        let lockup = ValenceLockup.of(pebble.valence)
        let word = SpineType.lockupWord(lockup.rank.sizeGroup)
        return VStack(spacing: 0) {
            Text(lockup.word)
                .spineType(word)
                .foregroundStyle(ink(lockup.ink, stone))
                .padding(.horizontal, word.inkOverhang)
            Text(lockup.span)
                .spineType(.cardLockupSpan)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(CardStrings.versoHeading))
        .accessibilityValue(Text(lockup.word) + Text(verbatim: " ") + Text(lockup.span))
        .accessibilityAddTraits(.isHeader)
        .opacity(print.map { PrintPart.lockup.alpha(CompletionMotion.print($0)) } ?? 1)
    }

    private func ink(_ ink: LockupInk, _ stone: StonePalette) -> AnyShapeStyle {
        switch ink {
        case .stoneRamp:
            return AnyShapeStyle(LinearGradient(stops: CardColors.highlightRamp(stone, scheme: scheme, in: environment),
                                                startPoint: .top, endPoint: .bottom))
        case .ink:
            return AnyShapeStyle(Color.pebbles.onSurface)
        case .primary:
            return AnyShapeStyle(Color.pebbles.primary)
        }
    }

    /// The stone's clearing: room above and below for the rings and the halo
    /// (0.3 of the stone's width), so the halo never covers the emotion.
    private var clearing: some View {
        let stone = CardLayout.versoStoneSize(pebble.valence.sizeGroup)
        return VersoStone(valence: pebble.valence, glyph: pebble.glyph, palette: palette,
                          seed: StableHash.seed(pebble.id), press: stonePress)
            .padding(.vertical, stone.width * 0.3)
            .opacity(print.map { CompletionMotion.litStoneAlpha(CompletionMotion.stone($0)) } ?? 1)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: SpineStoneDescription.text(
                summary, names: soulNames, order: [], calendar: SpineCalendar.current, locale: locale)))
            .anchorPreference(key: PrintAnchors.self, value: .bounds) { [.versoStone: $0] }
    }
}

/// The verso's frame, behind its content: the mat at 1.6u with its print and
/// keyline, and the stock panel `bandFoot` in with its keyline and inner rule.
/// The panel runs to the band's foot, so a band lies over it; with no band its
/// foot shows.
struct VersoFrame: View {
    let polarity: ValencePolarity
    let spot: Color
    let u: CGFloat

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let mat = RoundedRectangle(cornerRadius: 1.3 * u, style: .continuous)
        let panel = RoundedRectangle(cornerRadius: 1 * u, style: .continuous)
        ZStack {
            GeometryReader { proxy in
                SpineCardField(polarity: polarity, spot: spot, tint: SpineColors.tintK(scheme: scheme),
                               pattern: SpineColors.patternK(scheme: scheme), size: proxy.size)
            }
            .clipShape(mat)
            .overlay { mat.strokeBorder(spot.opacity(CardColors.matRuleAlpha), lineWidth: 1) }
            .padding(CardLayout.versoMatUnits * u)
            panel
                .fill(Color.pebbles.stock)
                .overlay { panel.strokeBorder(spot.opacity(CardColors.panelRuleAlpha), lineWidth: 1) }
                .overlay {
                    RoundedRectangle(cornerRadius: 0.8 * u, style: .continuous)
                        .strokeBorder(Color.pebbles.cardRule, lineWidth: 1)
                        .padding(1.1 * u)
                }
                .padding(CardLayout.bandFoot(u: u))
        }
        .accessibilityHidden(true)
    }
}

/// The verso's two parts in one pass: the content column (subview 0), proposed
/// the face's height less the panel's padding and the foot, so its spacers
/// centre the stone; the band (subview 1, optional) at full width, its foot on
/// the panel's foot line. With a band the postmarks keep 18 pt above it.
struct VersoStack: Layout {
    let top: CGFloat
    let side: CGFloat
    /// From the face's foot to the band's: `CardLayout.bandFoot(u:)`.
    let foot: CGFloat
    /// Below the content when there is no band.
    let bareFoot: CGFloat

    private func bandHeight(width: CGFloat, subviews: Subviews) -> CGFloat? {
        guard subviews.count > 1 else { return nil }
        return subviews[1].sizeThatFits(ProposedViewSize(width: width, height: nil)).height
    }

    private func contentFoot(_ band: CGFloat?) -> CGFloat {
        band.map { foot + $0 + CardLayout.postmarkRowToBand } ?? foot + bareFoot
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let content = subviews.first else { return .zero }
        let width = proposal.width ?? 0
        if let height = proposal.height, height.isFinite { return CGSize(width: width, height: height) }
        let natural = content.sizeThatFits(ProposedViewSize(width: max(0, width - 2 * side), height: nil))
        return CGSize(width: width, height: top + natural.height + contentFoot(bandHeight(width: width, subviews: subviews)))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard let content = subviews.first else { return }
        let band = bandHeight(width: bounds.width, subviews: subviews)
        content.place(at: CGPoint(x: bounds.minX + side, y: bounds.minY + top), anchor: .topLeading,
                      proposal: ProposedViewSize(width: max(0, bounds.width - 2 * side),
                                                 height: max(0, bounds.height - top - contentFoot(band))))
        if let band {
            subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.maxY - foot - band), anchor: .topLeading,
                              proposal: ProposedViewSize(width: bounds.width, height: band))
        }
    }
}
```

`PrintPart`, `CompletionMotion` and `PrintAnchors` arrive in E3. So that this part builds alone, add them now as the smallest stubs E3 replaces, at the end of `CardVerso.swift`:

```swift
// E3 replaces these with the print's timeline (`Card/Logic/CompletionMotion.swift`)
// and its anchors (`Card/CardPrint.swift`). Until then nothing prints: `print` is
// always nil, so none of them is read.
enum PrintPart {
    case card, lockup, postmarks, band, footer

    func alpha(_ print: Double) -> Double { 1 }
}

enum CompletionMotion {
    static func print(_ timeline: Double) -> Double { 1 }
    static func stone(_ timeline: Double) -> Double { 1 }
    static func litStoneAlpha(_ stone: Double) -> Double { 1 }
}

enum PrintAnchor: Hashable {
    case versoStone
}

struct PrintAnchors: PreferenceKey {
    static let defaultValue: [PrintAnchor: Anchor<CGRect>] = [:]

    static func reduce(value: inout [PrintAnchor: Anchor<CGRect>], nextValue: () -> [PrintAnchor: Anchor<CGRect>]) {
        value.merge(nextValue()) { $1 }
    }
}
```

- [ ] **Step 2: Build and commit**

Run **Build** and the **Suite command** with `ThemeLiteralsTests`. Expected: `** BUILD SUCCEEDED **`, PASS.

```bash
git add apps/ios/Pebbles/Features/Path/Card/CardVerso.swift
git commit -m "feat(ios): the held card's verso, the pebble in its frame"
```

---

### Task E1b.10: `CardTurnModel`, the committed face

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/CardTurnModel.swift`
- Test: `apps/ios/PebblesTests/Card/CardTurnModelTests.swift`

E1 turns by the button with no animation; E2 replaces this file with the whole turn and extends the test.

- [ ] **Step 1: Write the failing test**

```swift
import Testing
@testable import Pebbles

/// #1146 E1: the card opens on the face asked for, and the footer's button
/// turns it at once. `landing`, what is saved, is the face.
@Suite("CardTurnModel")
@MainActor
struct CardTurnModelTests {
    @Test("the card opens on the face asked for")
    func opens() {
        #expect(CardTurnModel(face: .verso).face == .verso)
        #expect(CardTurnModel(face: .recto).front == .recto)
    }

    @Test("the button turns at once, and landing follows")
    func turns() {
        let turn = CardTurnModel(face: .recto)
        turn.turnInstantly()
        #expect(turn.face == .verso && turn.front == .verso && turn.landing == .verso)
        turn.turnInstantly()
        #expect(turn.face == .recto)
    }
}
```

Save it as `apps/ios/PebblesTests/Card/CardTurnModelTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CardTurnModelTests`. Expected: build FAIL with "cannot find 'CardTurnModel' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/CardTurnModel.swift`:

```swift
import Observation

/// The held card's turn (#1146 §State model), the page's `@State`. In E1 the
/// footer's button turns it at once; E2 adds the progress, the drag and the
/// springs. `front` is what the footer and VoiceOver follow, `landing` what is
/// saved to `@SceneStorage("heldCard")`.
@Observable @MainActor
final class CardTurnModel {
    private(set) var face: CardFace

    init(face: CardFace) {
        self.face = face
    }

    var front: CardFace { face }
    var landing: CardFace { face }

    func turnInstantly() {
        face = face.other
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `CardTurnModelTests`. Expected: PASS (2).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/CardTurnModel.swift apps/ios/PebblesTests/Card/CardTurnModelTests.swift
git commit -m "feat(ios): the held card knows its face"
```

---

### Task E1b.11: `FaceStack` and `PebbleCard`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/FaceStack.swift`
- Create: `apps/ios/Pebbles/Features/Path/Card/PebbleCard.swift`

- [ ] **Step 1: The face stack**

Create `apps/ios/Pebbles/Features/Path/Card/FaceStack.swift`:

```swift
import SwiftUI

/// Both faces at one width, the area as tall as the taller (#1146 D5): each
/// face measured at the width with a free height, then both placed at the
/// taller height, so turning never changes the card's size and the verso
/// (flexible) fills the height its stone centres in. Neither face is ever
/// removed: the hidden one is laid out at opacity 0 (Android's lesson: an
/// unplaced face loses its display list).
struct FaceStack: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        let height = subviews.map { $0.sizeThatFits(ProposedViewSize(width: width, height: nil)).height }.max() ?? 0
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for subview in subviews {
            subview.place(at: bounds.origin, anchor: .topLeading,
                          proposal: ProposedViewSize(width: bounds.width, height: bounds.height))
        }
    }
}
```

- [ ] **Step 2: The card**

Create `apps/ios/Pebbles/Features/Path/Card/PebbleCard.swift`:

```swift
import SwiftUI

/// The held card (#1146): a still shell (`SpineCardShell`), a panel that shows
/// the recto or the verso, and the still footer. Both faces are built and laid
/// out in one `FaceStack`, each `.compositingGroup()`, the one not in front at
/// opacity 0 and hidden from VoiceOver. In E1 the footer's button turns the
/// card at once; E2 adds the rotation and the drag.
struct PebbleCard: View {
    let detail: MomentDetail
    let pebble: MomentDetail.Pebble
    let palette: EmotionPalette?
    /// The photo's ratio once probed; nil holds the square.
    let ratio: CGFloat?
    let width: CGFloat
    let domains: [Domain]
    let collectionGlyphs: [UUID: Glyph]
    let soulGlyphs: [String: [GlyphStroke]]
    let soulOrder: [String]
    let turn: CardTurnModel

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let stone = SpineColors.palette(palette, isBlank: false)
        SpineCardShell(palette: stone, polarity: pebble.valence.polarity, intensity: pebble.intensity) {
            VStack(spacing: 0) {
                panel
                    .frame(width: width)
                CardFooter(visibility: pebble.visibility, turnTo: turn.front.other,
                           stone: SpineColors.spot(stone, scheme: scheme), onTurn: { turn.turnInstantly() })
                    .frame(width: width)
            }
        }
        .accessibilityElement(children: .contain)
    }

    private var panel: some View {
        FaceStack {
            face(.recto) {
                CardRecto(detail: detail, pebble: pebble, palette: palette, ratio: ratio, width: width,
                          soulGlyphs: soulGlyphs, soulOrder: soulOrder)
            }
            face(.verso) {
                CardVerso(pebble: pebble, summary: MomentSummary(detail: detail), palette: palette, width: width,
                          domains: domains, collectionGlyphs: collectionGlyphs)
            }
        }
    }

    private func face<Content: View>(_ role: CardFace, @ViewBuilder _ content: () -> Content) -> some View {
        content()
            .compositingGroup()
            .opacity(turn.front == role ? 1 : 0)
            .accessibilityHidden(turn.front != role)
    }
}
```

- [ ] **Step 3: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Card/FaceStack.swift apps/ios/Pebbles/Features/Path/Card/PebbleCard.swift
git commit -m "feat(ios): the held card, both faces ready in one shell"
```

---

### Task E1b.12: `CardTopBar` and `HeldCardMenu`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/CardTopBar.swift`
- Create: `apps/ios/Pebbles/Features/Path/Card/HeldCardMenu.swift`
- Test: `apps/ios/PebblesTests/Card/HeldCardMenuTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1146 D3: the ⋮ holds Edit moment, Edit pebble (pebbles only), then Delete
/// in the error colour, through stack A2's shared menu.
@Suite("HeldCardMenu")
@MainActor
struct HeldCardMenuTests {
    final class Calls: @unchecked Sendable { var names: [String] = [] }

    private func english(_ item: PebblesMenuItem) -> String {
        var copy = item.title
        copy.locale = Locale(identifier: "en")
        return String(localized: copy)
    }

    @Test("a pebble's menu: edit moment, edit pebble, an orange delete last")
    func pebble() {
        let calls = Calls()
        let items = HeldCardMenu.items(hasPebble: true, editMoment: { calls.names.append("moment") },
                                       editPebble: { calls.names.append("pebble") },
                                       delete: { calls.names.append("delete") })
        #expect(items.map(english) == ["Edit moment", "Edit pebble", "Delete"])
        #expect(items.map(\.isDestructive) == [false, false, true])
        items.forEach { $0.action() }
        #expect(calls.names == ["moment", "pebble", "delete"])
    }

    @Test("a bare moment's menu: edit moment and delete")
    func bare() {
        let items = HeldCardMenu.items(hasPebble: false, editMoment: {}, editPebble: {}, delete: {})
        #expect(items.map(english) == ["Edit moment", "Delete"])
    }
}
```

Save it as `apps/ios/PebblesTests/Card/HeldCardMenuTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `HeldCardMenuTests`. Expected: build FAIL with "cannot find 'HeldCardMenu' in scope".

- [ ] **Step 3: The menu's items**

Create `apps/ios/Pebbles/Features/Path/Card/HeldCardMenu.swift`:

```swift
import Foundation

/// The held card's ⋮ (#1146 D3): stack A2's rule, so the items go through the
/// shared `PebblesMenu` and Delete is in `Color.pebbles.error` (orange, never
/// red). The shared list draws a rule between rows, the divider before Delete
/// included (decision 5). The card adds no menu of its own.
enum HeldCardMenu {
    static func items(hasPebble: Bool, editMoment: @escaping () -> Void, editPebble: @escaping () -> Void,
                      delete: @escaping () -> Void) -> [PebblesMenuItem] {
        var items = [PebblesMenuItem(title: "Edit moment", systemImage: "pencil", action: editMoment)]
        if hasPebble {
            items.append(PebblesMenuItem(title: "Edit pebble", systemImage: "circle.hexagongrid", action: editPebble))
        }
        items.append(.delete(delete))
        return items
    }
}
```

If `PebblesMenuItem.delete` is spelled differently on `main` (`grep -n "static func delete" apps/ios/Pebbles/Components/Menus/PebblesMenu.swift`), use it as declared.

- [ ] **Step 4: The top bar**

Create `apps/ios/Pebbles/Features/Path/Card/CardTopBar.swift`:

```swift
import SwiftUI

/// The held card's own top bar (#1146 §Top bar), not a toolbar, so the chrome
/// can fade on its own (E4): ← Back, Share for a public pebble only (it shares
/// the pebble, whose id differs from the moment's), and ⋮. No privacy badge,
/// no title. Every control is a 44 pt target; all are disabled while loading
/// and deleting.
struct CardTopBar: View {
    let shareURL: URL?
    let menuItems: [PebblesMenuItem]
    let isDisabled: Bool
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onBack) {
                Image(systemName: "chevron.backward")
                    .frame(width: CardLayout.turnTarget, height: CardLayout.turnTarget)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(Text("Back"))
            Spacer(minLength: 0)
            if let shareURL {
                ShareLink(item: shareURL) {
                    Image(systemName: "square.and.arrow.up")
                        .frame(width: CardLayout.turnTarget, height: CardLayout.turnTarget)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(Text("Share", comment: "Accessibility label for the public pebble share button"))
                .disabled(isDisabled)
            }
            PebblesMenu(items: menuItems) {
                Image(systemName: "ellipsis.circle")
                    .frame(width: CardLayout.turnTarget, height: CardLayout.turnTarget)
                    .contentShape(Rectangle())
            }
            .disabled(isDisabled)
            .accessibilityLabel(Text("More"))
        }
        .foregroundStyle(Color.pebbles.onSurface)
        .padding(.horizontal, 4)
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run the **Suite command** with `HeldCardMenuTests`, then **Build**. Expected: PASS (2), `** BUILD SUCCEEDED **`.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/CardTopBar.swift apps/ios/Pebbles/Features/Path/Card/HeldCardMenu.swift apps/ios/PebblesTests/Card/HeldCardMenuTests.swift
git commit -m "feat(ios): the held card's bar and its menu"
```

---

### Task E1b.13: Fixtures, the still and the references (decision 4)

**Files:**
- Modify: `apps/ios/Pebbles/Components/Menus/PebblesMenu.swift`
- Create: `apps/ios/PebblesTests/Card/HeldCardFixtures.swift`
- Create: `apps/ios/PebblesTests/Card/HeldCardStill.swift`
- Create: `apps/ios/PebblesTests/References/HeldCardReferences.swift`

- [ ] **Step 1: The menu list renders outside its popover (decision 4)**

In `apps/ios/Pebbles/Components/Menus/PebblesMenu.swift`, replace `private struct PebblesMenuList: View {` with

```swift
/// Internal (not private) so the held card's reference can draw the list as it
/// opens: `ImageRenderer` cannot draw a popover (#1146, decision 4).
struct PebblesMenuList: View {
```

- [ ] **Step 2: The fixtures**

Create `apps/ios/PebblesTests/Card/HeldCardFixtures.swift`:

```swift
import Foundation
import SwiftUI
@testable import Pebbles

/// The held card's fixtures (#1146 §Testing): F1 a photo card, F2 a public one,
/// F3 a card without a photo, F4 a bare moment (E3), and a stress verso with
/// four long domains and two long collections. Photos are the Spine's fixture
/// photos, drawn through `SpineFixturePhotos.source`.
enum HeldCardFixtures {
    static let joy = EmotionPalette(primaryHex: "#E8A23AFF", secondaryHex: "#F2C46DFF", lightHex: "#FBE7C2FF",
                                    surfaceHex: "#E8A23A1A", darkHex: "#5C3A0EFF", shadedHex: "#A4691FFF")
    static let blue = EmotionPalette(primaryHex: "#4A78B8FF", secondaryHex: "#86A7D6FF", lightHex: "#DCE7F5FF",
                                     surfaceHex: "#4A78B81A", darkHex: "#1B2F4DFF", shadedHex: "#33547FFF")

    static let fountain: String = SpineFixtureWeek.photos.first { $0.value.file == "fountain" }?.key ?? "missing"
    static let seawall: String = SpineFixtureWeek.photos.first { $0.value.file == "seawall-portrait" }?.key ?? "missing"
    static func ratio(_ path: String) -> CGFloat? { SpineFixtureWeek.photos[path]?.ratio }

    static let work = UUID(uuidString: "33333333-3333-4333-8333-333333333301") ?? UUID()
    static let friends = UUID(uuidString: "33333333-3333-4333-8333-333333333302") ?? UUID()
    static let growth = UUID(uuidString: "33333333-3333-4333-8333-333333333303") ?? UUID()
    static let spirit = UUID(uuidString: "33333333-3333-4333-8333-333333333304") ?? UUID()
    static let autumn = UUID(uuidString: "55555555-5555-4555-8555-555555555501") ?? UUID()

    static let domains: [Domain] = [
        Domain(id: work, slug: "zoe", name: "Work", label: "Your job and your craft",
               strokes: [GlyphStroke(d: "M 40,160 L 100,40 L 160,160 Z", width: 6)], viewBox: "0 0 200 200"),
        Domain(id: friends, slug: "amis", name: "Friends", label: "The people you choose",
               strokes: [GlyphStroke(d: "M 60,100 A 30,30 0 1 1 140,100", width: 6)], viewBox: "0 0 200 200"),
        Domain(id: growth, slug: "growth", name: "Personal growth", label: "Becoming", strokes: nil, viewBox: nil),
        Domain(id: spirit, slug: "spirit", name: "Spirituality", label: "What you believe", strokes: nil, viewBox: nil)
    ]

    static let collectionGlyphs: [UUID: Glyph] = [
        autumn: Glyph(id: UUID(), name: "Autumn", strokes: [GlyphStroke(d: "M 20,100 Q 100,20 180,100", width: 6)],
                      viewBox: "0 0 200 200", userId: nil)
    ]

    static let soulGlyphs: [String: [GlyphStroke]] = [
        "44444444-4444-4444-8444-444444444401": [GlyphStroke(d: "M 40,40 L 160,160", width: 6)]
    ]

    private static func decode(_ json: String) -> MomentDetail {
        do {
            return try JSONDecoder().decode(MomentDetail.self, from: Data(json.utf8))
        } catch {
            preconditionFailure("held card fixture does not decode: \(error)")
        }
    }

    private static func detail(id: String, name: String?, words: String?, snap: String?, intensity: Int,
                               positiveness: Int, visibility: String, souls: [(String, String)],
                               domains: [(UUID, String, String)], collections: [(UUID, String)]) -> MomentDetail {
        let snaps = snap.map { #"[{"id": "\#(UUID().uuidString.lowercased())", "storage_path": "\#($0)", "sort_order": 0}]"# } ?? "[]"
        let soulsJSON = souls.map { #"{"id": "\#($0.0)", "name": "\#($0.1)"}"# }.joined(separator: ",")
        let domainsJSON = domains.map { #"{"id": "\#($0.0.uuidString.lowercased())", "slug": "\#($0.1)", "name": "\#($0.2)"}"# }
            .joined(separator: ",")
        let collectionsJSON = collections
            .map { #"{"id": "\#($0.0.uuidString.lowercased())", "name": "\#($0.1)", "glyph_id": null}"# }
            .joined(separator: ",")
        return decode("""
        {
          "id": "\(id)", "user_id": "5f0e1d2c-3b4a-4958-8776-655443322110",
          "name": \(name.map { "\"\($0)\"" } ?? "null"), "description": \(words.map { "\"\($0)\"" } ?? "null"),
          "happened_at": "2026-10-08T19:00:00+00:00", "hidden_at": null,
          "created_at": "2026-10-08T19:10:00+00:00", "updated_at": "2026-10-08T19:10:00+00:00",
          "snaps": \(snaps),
          "pebble": {
            "id": "\(id)", "intensity": \(intensity), "positiveness": \(positiveness), "visibility": "\(visibility)",
            "emotion": {"id": "22222222-2222-4222-8222-222222222222", "slug": "joy", "name": "Joy"},
            "glyph": {"id": "66666666-6666-4666-8666-666666666666", "name": null,
                      "strokes": [{"d": "M 20,100 Q 100,20 180,100", "width": 6}], "view_box": "0 0 200 200",
                      "source": "collection"},
            "souls": [\(soulsJSON)], "domains": [\(domainsJSON)], "collections": [\(collectionsJSON)]
          }
        }
        """)
    }

    /// F1: a photo card, private, with three souls, two domains and one collection.
    static let f1 = detail(id: "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a01", name: "Fountain at dusk",
                           words: "We stayed until the lights came on.", snap: fountain, intensity: 2, positiveness: 1,
                           visibility: "private",
                           souls: [("44444444-4444-4444-8444-444444444401", "Ana"),
                                   ("44444444-4444-4444-8444-444444444402", "Elio"),
                                   ("44444444-4444-4444-8444-444444444403", "Mara")],
                           domains: [(friends, "amis", "Friends"), (work, "zoe", "Work")],
                           collections: [(autumn, "Autumn")])

    /// F2: a public portrait card, so Share shows.
    static let f2 = detail(id: "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a02", name: "Seawall", words: nil, snap: seawall,
                           intensity: 3, positiveness: 0, visibility: "public",
                           souls: [], domains: [(friends, "amis", "Friends")], collections: [])

    /// F3: a lowlight with no photo, secret: the slip carries the name.
    static let f3 = detail(id: "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a03", name: "The long wait at the clinic",
                           words: "Nobody called my name for two hours.", snap: nil, intensity: 1, positiveness: -1,
                           visibility: "secret", souls: [], domains: [(work, "zoe", "Work")], collections: [])

    /// The stress verso: four long domains, two long collections, a highlight of the month.
    static let stress = detail(id: "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a04", name: "Everything at once", words: nil,
                               snap: nil, intensity: 3, positiveness: 1, visibility: "private", souls: [],
                               domains: [(work, "zoe", "Work"), (friends, "amis", "Friends"),
                                         (growth, "growth", "Personal growth"), (spirit, "spirit", "Spirituality")],
                               collections: [(autumn, "Autumn evenings on the river"),
                                             (UUID(), "Things I want to remember next year")])
}
```

- [ ] **Step 3: The still**

Create `apps/ios/PebblesTests/Card/HeldCardStill.swift`:

```swift
import SwiftUI
@testable import Pebbles

/// The held card without a `ScrollView` (the B2 harness cannot render one), on
/// the page's ground at 393 pt: the card, optionally under its top bar, and
/// optionally with the ⋮ list drawn open under the bar (decision 4).
struct HeldCardStill: View {
    let detail: MomentDetail
    var face: CardFace = .recto
    var palette: EmotionPalette? = HeldCardFixtures.joy
    var showsBar = false
    var menuOpen = false
    var width: CGFloat = ReferenceConfig.phoneWidth

    var body: some View {
        VStack(alignment: .trailing, spacing: 8) {
            if showsBar {
                CardTopBar(shareURL: detail.pebble.flatMap { $0.visibility == .public ? SharedPebbleLink.url(for: $0.id) : nil },
                           menuItems: menu, isDisabled: false, onBack: {})
            }
            if menuOpen {
                PebblesMenuList(items: menu, dismiss: {})
                    .background(Color.pebbles.surfaceContainer)
                    .padding(.trailing, 16)
            }
            if let pebble = detail.pebble {
                PebbleCard(detail: detail, pebble: pebble, palette: palette,
                           ratio: detail.snaps.first.flatMap { HeldCardFixtures.ratio($0.storagePath) },
                           width: CardLayout.cardWidth(available: width), domains: HeldCardFixtures.domains,
                           collectionGlyphs: HeldCardFixtures.collectionGlyphs, soulGlyphs: HeldCardFixtures.soulGlyphs,
                           soulOrder: [], turn: CardTurnModel(face: face))
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.vertical, 24)
        .frame(width: width)
        .background(Color.pebbles.surface)
        .environment(\.spineSnapSource, SpineFixturePhotos.source)
    }

    private var menu: [PebblesMenuItem] {
        HeldCardMenu.items(hasPebble: detail.pebble != nil, editMoment: {}, editPebble: {}, delete: {})
    }
}
```

- [ ] **Step 4: The references**

Create `apps/ios/PebblesTests/References/HeldCardReferences.swift`:

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1146 E1: each face of a photo card (F1, with the French row), a public
    /// card under its bar (F2, Share showing), a card without a photo (F3), the
    /// stress verso, the menu open. Paper and Night. Android: PebbleCardScreenshots.
    @Suite("Held card references")
    @MainActor
    struct HeldCardReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func photoRecto(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f1), named: "f1-recto",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func photoVerso(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f1, face: .verso), named: "f1-verso",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func publicCard(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f2, showsBar: true), named: "f2-recto",
                                         config: ReferenceConfig(scheme: scheme))
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f2, face: .verso, showsBar: true),
                                         named: "f2-verso", config: ReferenceConfig(scheme: scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func noPhoto(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f3, palette: HeldCardFixtures.blue),
                                         named: "f3-recto", config: ReferenceConfig(scheme: scheme))
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f3, face: .verso,
                                                       palette: HeldCardFixtures.blue),
                                         named: "f3-verso", config: ReferenceConfig(scheme: scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func stressVerso(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.stress, face: .verso),
                                         named: "stress-verso", config: ReferenceConfig(scheme: scheme))
        }

        @Test
        func menuOpen() {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f1, showsBar: true, menuOpen: true),
                                         named: "menu-open", config: ReferenceConfig(scheme: .paper))
        }

        @Test
        func largestType() {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f1), named: "f1-recto",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }
    }
}
```

- [ ] **Step 5: Look locally (not committed)**

Run **Look** at `HeldCardReferences`. Check against the spec and Android's `PebbleCardScreenshots`:
- `f1-recto`: the Spine card at 361 pt, fountain photo whole at its ratio, day numeral and rotated rail, the title, three souls named, a dashed score, "Thursday, 8 October 2026 · 21:00" centred in small caps, the words; under the border, the lock-and-people glyph, "CONNECTIONS" and its sentence in italic, and right, "TURN" over *Pebble* with a tilted miniature card back.
- `f1-verso`: the mat printed in the valence pattern, the stock panel, "Highlight" in Caveat with the warm ramp, "OF MY WEEK", a hairline, "Joy", the lit stone centred, two postmarks with the names ringing in capitals and their glyphs, pitted ink; the band across the foot past the border with the Autumn glyph and its italic name; the footer reads *Moment*.
- `f2-*`: Back, Share and ⋮ in the bar; the portrait whole.
- `f3-recto`: no photo, the slip carries the name in the lowlight's light italic; the title is not repeated.
- `stress-verso`: four stamps at (rowWidth − 18) / 4, "PERSONAL GROWTH" stepped down, the band wrapping on two lines, no line starting or ending on a diamond.
- `menu-open`: Edit moment, Edit pebble, Delete in orange, rules between rows.
- `f1-recto-ax2`: the leaf and the footer grow, the footer's turn drops under the privacy, nothing clips.
- French: "RETOURNER" over *Galet*, the dateline "jeudi 8 octobre 2026 · 21:00", "Ton galet" nowhere visible (it is VoiceOver's).

Clean afterwards. If the verso's stone is blank under `ImageRenderer`, B2's `litStoneDrawsMaterial` probe decides (its fallback renders in a window); do not change the stone.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Components/Menus/PebblesMenu.swift apps/ios/PebblesTests/Card/HeldCardFixtures.swift apps/ios/PebblesTests/Card/HeldCardStill.swift apps/ios/PebblesTests/References/HeldCardReferences.swift
git commit -m "test(ios): held card references, both faces, the stress verso and the menu"
```

---

### Task E1b.14: E1b verification, PR and CI references

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** (the new references fail locally, expected) and **Build**.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <E1b-number> --title "feat(ios): the held card's recto, verso and footer" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Part of #1146`; key files; the split of `SpineCard` as a pure move proven by unchanged Spine references; **new patterns for review:** the Canvas `destinationOut` postmark (D4) with `PostmarkInkMode.speckle` written as the fallback, three `Layout`s (`FaceStack`, `VersoStack`, `FooterLayout`, plus `BandFlow`) so sizes are decided in one pass and hold under `ImageRenderer`; decision 4 (`PebblesMenuList` internal) and decision 5 (the shared list's rules); the verso's VoiceOver heading as one element with the valence as its value (Android's one stop, where the spec lists two); the E3 stubs at the end of `CardVerso.swift`. No Lab Note, no `F-…` ids.

- [ ] **Step 3: Rebaseline on CI**

Run **Rebaseline on CI**. Compare `f1-recto-paper-en.png` and `f1-verso-paper-en.png` with Android's `PebbleCardF1RectoLight` / `PebbleCardF1VersoLight`: the same parts in the same order; nothing cropped, no red. The Spine suites stay unchanged.

---
## Part E1c — `feat/1146-ios-held-card`

PR title: `feat(ios): the pebble detail becomes the held card`. Body opens `Part of #1146`. The part that changes what the app shows: every caller opens the held card through one host, in a `.large` sheet until E4 (D2); bare moments keep the old read view inside the new page until E3.

### Task E1c.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1146-ios-held-card
git branch --show-current
```

Expected: `feat/1146-ios-held-card`.

---

### Task E1c.1: `HeldCardRequest` and `HeldCardSnapshot`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/HeldCardRequest.swift`
- Create: `apps/ios/Pebbles/Features/Path/Card/HeldCardSnapshot.swift`
- Test: `apps/ios/PebblesTests/Card/HeldCardSnapshotTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1146 D7: the scene keeps {momentId, landing face, printingFor}; a restore
/// decodes tolerantly, and a value it cannot read is no card, not a crash.
@Suite("HeldCardSnapshot")
struct HeldCardSnapshotTests {
    private let id = UUID(uuidString: "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b") ?? UUID()

    @Test("a snapshot round-trips through the scene's data")
    func roundTrip() throws {
        let snapshot = HeldCardSnapshot(momentId: id, face: .verso, printingFor: id)
        let data = try #require(snapshot.encoded)
        #expect(HeldCardSnapshot.decoded(from: data) == snapshot)
    }

    @Test("an unknown face reopens on the recto; a missing print is none")
    func tolerant() throws {
        let data = Data(#"{"momentId": "0B7C3F5E-2D41-4A8E-9F6B-1C2D3E4F5A6B", "face": "edge"}"#.utf8)
        let snapshot = try #require(HeldCardSnapshot.decoded(from: data))
        #expect(snapshot.face == .recto)
        #expect(snapshot.printingFor == nil)
        #expect(snapshot.momentId == id)
    }

    @Test("data that is not a snapshot is no card")
    func garbage() {
        #expect(HeldCardSnapshot.decoded(from: Data("{}".utf8)) == nil)
        #expect(HeldCardSnapshot.decoded(from: Data("not json".utf8)) == nil)
    }

    @Test("a request is identified by its moment")
    func requestIdentity() {
        let request = HeldCardRequest(momentId: id, origin: .path)
        #expect(request.id == id)
        #expect(request.face == .recto && request.seed == nil && request.soulOrder.isEmpty)
    }
}
```

Save it as `apps/ios/PebblesTests/Card/HeldCardSnapshotTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `HeldCardSnapshotTests`. Expected: build FAIL with "cannot find 'HeldCardSnapshot' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/HeldCardRequest.swift`:

```swift
import Foundation

/// What opens the held card (#1146 §State model). The face is a value the
/// press carries and the page answers once, at open (D7: on iPhone the card
/// covers every list that could ask again, so there is no request counter).
/// `seed` is the Spine's summary, so the first frame of a flight is a card
/// (E4); `soulOrder` names people as the Spine's page does (`[]` elsewhere);
/// `origin` is the tab whose list opened it, so `MomentsRevision` can tell its
/// own changes from another tab's; `onChanged` reloads that list.
struct HeldCardRequest: Identifiable {
    let momentId: UUID
    let face: CardFace
    let seed: MomentSummary?
    let soulOrder: [String]
    let origin: AppTab
    let onChanged: @MainActor () -> Void

    var id: UUID { momentId }

    init(momentId: UUID, face: CardFace = .recto, seed: MomentSummary? = nil, soulOrder: [String] = [],
         origin: AppTab, onChanged: @escaping @MainActor () -> Void = {}) {
        self.momentId = momentId
        self.face = face
        self.seed = seed
        self.soulOrder = soulOrder
        self.origin = origin
        self.onChanged = onChanged
    }
}
```

Create `apps/ios/Pebbles/Features/Path/Card/HeldCardSnapshot.swift`:

```swift
import Foundation

/// The held card as the scene keeps it (#1146 D7), under
/// `@SceneStorage("heldCard")` in `RootView`: which moment, the face it will
/// land on, and the print under way (E3). A relaunch after a background kill
/// reopens the card at rest on that face. Decoding is tolerant: an unknown face
/// is the recto, a missing print is none, and data that is not a snapshot is
/// no card. New pattern, named in the E1c PR.
struct HeldCardSnapshot: Codable, Equatable {
    var momentId: UUID
    var face: CardFace
    var printingFor: UUID?

    init(momentId: UUID, face: CardFace, printingFor: UUID? = nil) {
        self.momentId = momentId
        self.face = face
        self.printingFor = printingFor
    }

    private enum CodingKeys: String, CodingKey {
        case momentId, face, printingFor
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        momentId = try container.decode(UUID.self, forKey: .momentId)
        face = (try? container.decode(CardFace.self, forKey: .face)) ?? .recto
        printingFor = (try? container.decodeIfPresent(UUID.self, forKey: .printingFor)) ?? nil
    }

    static func decoded(from data: Data) -> HeldCardSnapshot? {
        try? JSONDecoder().decode(HeldCardSnapshot.self, from: data)
    }

    var encoded: Data? {
        try? JSONEncoder().encode(self)
    }
}
```

`RootView` logs when `decoded(from:)` returns nil (Task E1c.6), as it does for the tabs' snapshot.

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `HeldCardSnapshotTests`. Expected: PASS (4).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/HeldCardRequest.swift apps/ios/Pebbles/Features/Path/Card/HeldCardSnapshot.swift apps/ios/PebblesTests/Card/HeldCardSnapshotTests.swift
git commit -m "feat(ios): the held card's request and what the scene keeps of it"
```

---

### Task E1c.2: `HeldCardModel`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/HeldCardModel.swift`
- Test: `apps/ios/PebblesTests/Card/HeldCardModelTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1146 §State model: the page's model loads `v_moments_full` and reloads in
/// place; every save, delete and completion calls the opener once and bumps
/// `MomentsRevision` once from the opener's tab; a delete that closes the page
/// sets `leaving` before it returns, so that close never flies (E4).
@Suite("HeldCardModel")
@MainActor
struct HeldCardModelTests {
    struct Failure: Error {}
    final class Calls: @unchecked Sendable { var count = 0 }

    private func detail(_ data: Data = MomentFixtures.completedMoment) throws -> MomentDetail {
        try JSONDecoder().decode(MomentDetail.self, from: data)
    }

    private func model(_ calls: Calls = Calls()) -> HeldCardModel {
        HeldCardModel(momentId: MomentFixtures.uuid("0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b"), origin: .people,
                      onChanged: { calls.count += 1 })
    }

    @Test("the first load publishes the detail")
    func firstLoad() async throws {
        let model = model()
        #expect(model.phase == .loading)
        let fresh = try detail()
        await model.load { fresh }
        #expect(model.phase == .loaded)
        #expect(model.detail?.id == fresh.id)
    }

    @Test("a failed first load fails; a failed reload keeps the page")
    func failures() async throws {
        let first = model()
        await first.load { throw Failure() }
        #expect(first.phase == .failed)
        let shown = model()
        let fresh = try detail()
        await shown.load { fresh }
        await shown.load { throw Failure() }
        #expect(shown.phase == .loaded && shown.detail != nil)
    }

    @Test("a change calls the opener once and bumps the revision once, from the opener's tab")
    func changed() {
        let calls = Calls()
        let model = model(calls)
        let revision = MomentsRevision()
        model.changed(revision)
        #expect(calls.count == 1)
        #expect(revision.value == 1 && revision.lastOrigin == .people)
    }

    @Test("deleting the moment closes the page, leaving first")
    func deleteMoment() async throws {
        let calls = Calls()
        let model = model(calls)
        let revision = MomentsRevision()
        let target = MomentDeleteTarget(detail: try detail())
        let outcome = await model.delete(.moment, target: target, revision: revision) { _, _ in }
        #expect(outcome == .closes)
        #expect(model.leaving)
        #expect(calls.count == 1 && revision.value == 1)
    }

    @Test("deleting only the pebble reloads the page in place")
    func deletePebble() async throws {
        let model = model()
        let revision = MomentsRevision()
        let target = MomentDeleteTarget(detail: try detail())
        let outcome = await model.delete(.pebbleKeepMoment, target: target, revision: revision) { _, _ in }
        #expect(outcome == .reloads)
        #expect(!model.leaving)
        #expect(revision.value == 1)
    }

    @Test("a failed delete changes nothing and says so")
    func deleteFails() async throws {
        let calls = Calls()
        let model = model(calls)
        let revision = MomentsRevision()
        let target = MomentDeleteTarget(detail: try detail())
        let outcome = await model.delete(.moment, target: target, revision: revision) { _, _ in throw Failure() }
        #expect(outcome == .failed)
        #expect(!model.leaving && !model.isDeleting)
        #expect(calls.count == 0 && revision.value == 0)
    }
}
```

Save it as `apps/ios/PebblesTests/Card/HeldCardModelTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `HeldCardModelTests`. Expected: build FAIL with "cannot find 'HeldCardModel' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/HeldCardModel.swift`:

```swift
import Foundation
import Observation
import os

/// The held card's page state (#1146 §State model), the page's `@State`. It
/// loads the moment from `v_moments_full` in the page's `.task` and reloads it
/// in place after an edit, a pebble delete or a completion, so the page never
/// swaps to a spinner and back. The fetch and the delete are passed in as
/// closures, as `PathModel`'s are, so the tests need no Supabase.
///
/// Every change made here calls the opener's `onChanged` and bumps
/// `MomentsRevision` from the opener's tab (stack C's contract, which this model
/// takes over from `PebbleDetailSheet`).
@Observable @MainActor
final class HeldCardModel {
    enum Phase: Equatable { case loading, failed, loaded }
    enum DeleteOutcome: Equatable { case closes, reloads, failed, ignored }

    private(set) var phase: Phase = .loading
    private(set) var detail: MomentDetail?
    private(set) var isDeleting = false
    /// Set before the close a delete triggers, so that close never flies (E4).
    private(set) var leaving = false

    @ObservationIgnored let momentId: UUID
    @ObservationIgnored private let origin: AppTab
    @ObservationIgnored private let onChanged: @MainActor () -> Void
    @ObservationIgnored private let logger = Logger(subsystem: "app.pbbls.ios", category: "held-card")

    init(momentId: UUID, origin: AppTab, onChanged: @escaping @MainActor () -> Void) {
        self.momentId = momentId
        self.origin = origin
        self.onChanged = onChanged
    }

    /// The first load and every reload in place. A failed reload keeps the page
    /// it has; a cancelled load (the page left) changes nothing.
    func load(_ fetch: () async throws -> MomentDetail) async {
        do {
            let fresh = try await fetch()
            guard !Task.isCancelled else { return }
            apply(fresh)
            phase = .loaded
        } catch {
            guard !Task.isCancelled else { return }
            logger.error("held card load failed: \(error.localizedDescription, privacy: .private)")
            if detail == nil { phase = .failed }
        }
    }

    private func apply(_ fresh: MomentDetail) {
        detail = fresh
    }

    /// A save, a delete or a completion: the opener reloads its list, and the
    /// other tabs catch up through `MomentsRevision`.
    func changed(_ revision: MomentsRevision) {
        onChanged()
        revision.bump(from: origin)
    }

    /// Runs `perform` (`MomentDeletion.perform`). Deleting the moment closes the
    /// page, `leaving` set first; deleting the pebble reloads it as a bare
    /// moment. A second confirm while one runs is ignored.
    func delete(_ option: MomentDeleteOption, target: MomentDeleteTarget, revision: MomentsRevision,
                perform: (MomentDeleteOption, MomentDeleteTarget) async throws -> Void) async -> DeleteOutcome {
        guard !isDeleting else { return .ignored }
        isDeleting = true
        defer { isDeleting = false }
        do {
            try await perform(option, target)
        } catch {
            logger.error("held card delete failed: \(error.localizedDescription, privacy: .private)")
            return .failed
        }
        if target.closesReadPage(after: option) {
            leaving = true
            changed(revision)
            return .closes
        }
        changed(revision)
        return .reloads
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `HeldCardModelTests`. Expected: PASS (6).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/HeldCardModel.swift apps/ios/PebblesTests/Card/HeldCardModelTests.swift
git commit -m "feat(ios): the held card's model loads, reloads and reports its changes"
```

---

### Task E1c.3: `HeldCardHostModel` (decision 3)

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/HeldCardHostModel.swift`
- Test: `apps/ios/PebblesTests/Card/HeldCardHostModelTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1146 D7: one host for every caller; the scene keeps the card's moment and
/// landing face; a restore reopens it at rest on that face, owned by no list
/// (decision 3); sign-out forgets it.
@Suite("HeldCardHostModel")
@MainActor
struct HeldCardHostModelTests {
    private let id = UUID(uuidString: "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b") ?? UUID()

    @Test("opening holds the request and snapshots its face")
    func open() {
        let host = HeldCardHostModel()
        host.open(HeldCardRequest(momentId: id, face: .verso, origin: .path))
        #expect(host.request?.momentId == id)
        #expect(host.snapshot == HeldCardSnapshot(momentId: id, face: .verso))
    }

    @Test("a landing face is written to the snapshot")
    func land() {
        let host = HeldCardHostModel()
        host.open(HeldCardRequest(momentId: id, origin: .path))
        host.land(.verso)
        #expect(host.snapshot?.face == .verso)
    }

    @Test("closing forgets the card")
    func close() {
        let host = HeldCardHostModel()
        host.open(HeldCardRequest(momentId: id, origin: .path))
        host.close()
        #expect(host.request == nil && host.snapshot == nil)
    }

    @Test("a restore reopens the card on its saved face, owned by no list")
    func restore() {
        let host = HeldCardHostModel()
        host.restore(HeldCardSnapshot(momentId: id, face: .verso))
        #expect(host.request?.momentId == id)
        #expect(host.request?.face == .verso)
        #expect(host.request?.origin == .you)
        #expect(host.request?.seed == nil)
    }

    @Test("a restore never replaces a card already open")
    func restoreOverOpen() {
        let host = HeldCardHostModel()
        let other = UUID()
        host.open(HeldCardRequest(momentId: other, origin: .path))
        host.restore(HeldCardSnapshot(momentId: id, face: .verso))
        #expect(host.request?.momentId == other)
    }

    @Test("sign-out resets it")
    func reset() {
        let host = HeldCardHostModel()
        host.open(HeldCardRequest(momentId: id, origin: .path))
        host.reset()
        #expect(host.request == nil && host.snapshot == nil)
    }
}
```

Save it as `apps/ios/PebblesTests/Card/HeldCardHostModelTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `HeldCardHostModelTests`. Expected: build FAIL with "cannot find 'HeldCardHostModel' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/HeldCardHostModel.swift`:

```swift
import Foundation
import Observation

/// The one host of the held card (#1146 D1, D7), created in `RootView` and
/// injected: every caller opens the card here. In E1–E3 `HeldCardHost`
/// presents its request in a `.large` sheet; E4 makes it the full-screen layer
/// with the flight. Its `snapshot` is mirrored to `@SceneStorage("heldCard")`
/// by `RootView` and cleared by the sign-out reset.
@Observable @MainActor
final class HeldCardHostModel {
    private(set) var request: HeldCardRequest?
    private(set) var snapshot: HeldCardSnapshot?

    func open(_ request: HeldCardRequest) {
        self.request = request
        snapshot = HeldCardSnapshot(momentId: request.momentId, face: request.face)
    }

    func close() {
        request = nil
        snapshot = nil
    }

    /// The face the card will rest on changed (`CardTurnModel.landing`).
    func land(_ face: CardFace) {
        guard var current = snapshot, current.face != face else { return }
        current.face = face
        snapshot = current
    }

    /// A relaunch after a background kill: the card reopens at rest on the
    /// saved face. The list that opened it is gone, so the restored card is
    /// owned by no list: it reports from `.you`, the tab that lists no moments,
    /// and Path, People and Collections all treat its changes as outside ones
    /// (decision 3).
    func restore(_ saved: HeldCardSnapshot) {
        guard request == nil else { return }
        request = HeldCardRequest(momentId: saved.momentId, face: saved.face, origin: .you)
        snapshot = saved
    }

    /// The sign-out reset (stack C §6): another account never reopens this card.
    func reset() {
        close()
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `HeldCardHostModelTests`. Expected: PASS (6).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/HeldCardHostModel.swift apps/ios/PebblesTests/Card/HeldCardHostModelTests.swift
git commit -m "feat(ios): one host for the held card, restored on its saved face"
```

---

### Task E1c.4: `HeldCardPage`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/HeldCardPage.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI
import os

/// The held card's page (#1146 D2): its own top bar over the card in a
/// vertical `ScrollView`. One view serves the interim sheet (E1–E3) and the
/// overlay (E4). It loads the moment in `.task`, reloads it in place after an
/// edit, a pebble delete or a completion, and presents the edit sheets, the
/// Complete flow and the delete dialog over the window as before. A moment with
/// no pebble still shows the old read view until E3's bare page.
struct HeldCardPage: View {
    let request: HeldCardRequest
    let onClose: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var refs
    @Environment(CompleteDraftStore.self) private var completeDrafts
    @Environment(MomentsRevision.self) private var revision
    @Environment(HeldCardHostModel.self) private var host
    @Environment(SnapAspectCache.self) private var aspects

    @State private var model: HeldCardModel
    @State private var turn: CardTurnModel
    @State private var pageWidth: CGFloat = ReferencePhone.width
    @State private var isPresentingEditMoment = false
    @State private var isPresentingEditPebble = false
    @State private var isPresentingComplete = false
    @State private var pendingDeletion: MomentDeleteTarget?
    @State private var deleteError: String?

    init(request: HeldCardRequest, onClose: @escaping () -> Void) {
        self.request = request
        self.onClose = onClose
        _model = State(initialValue: HeldCardModel(momentId: request.momentId, origin: request.origin,
                                                   onChanged: request.onChanged))
        _turn = State(initialValue: CardTurnModel(face: request.face))
    }

    private var service: MomentsService { MomentsService(client: supabase.client) }

    var body: some View {
        VStack(spacing: 0) {
            CardTopBar(shareURL: shareURL, menuItems: menuItems,
                       isDisabled: model.detail == nil || model.isDeleting, onBack: onClose)
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.pebbles.surface.ignoresSafeArea())
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { pageWidth = $0 }
        .task { await reload() }
        .task(id: model.detail?.snaps.first?.storagePath) {
            guard let path = model.detail?.snaps.first?.storagePath else { return }
            await aspects.probe(path)
        }
        .onChange(of: turn.landing) { _, face in host.land(face) }
        .interactiveDismissDisabled(model.isDeleting)
        .sheet(isPresented: $isPresentingEditPebble) {
            if let detail = model.detail, let pebble = detail.pebble {
                EditPebbleSheet(momentId: detail.id, pebble: pebble, onSaved: saved)
            }
        }
        .sheet(isPresented: $isPresentingEditMoment) {
            if let detail = model.detail {
                EditMomentSheet(detail: detail, onSaved: saved)
            }
        }
        .fullScreenCover(isPresented: $isPresentingComplete) {
            CompleteFlowView(momentId: request.momentId, momentName: model.detail?.name, onCompleted: { _ in
                // In place, behind the success screen.
                saved()
            })
        }
        .momentDeleteDialog(target: $pendingDeletion) { target, option in
            Task { await delete(option, on: target) }
        }
        .alert(
            "Couldn't delete",
            isPresented: Binding(get: { deleteError != nil }, set: { if !$0 { deleteError = nil } }),
            presenting: deleteError
        ) { _ in
            Button("OK", role: .cancel) { deleteError = nil }
        } message: { message in
            Text(message)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .loading:
            ProgressView()
        case .failed:
            VStack(spacing: 12) {
                Text("Couldn't load this pebble.")
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                Button("Retry") { Task { await reload() } }
            }
        case .loaded:
            if let detail = model.detail {
                if let pebble = detail.pebble {
                    card(detail, pebble)
                } else {
                    // Until E3's bare page.
                    PebbleReadView(detail: detail, palette: nil, onComplete: { isPresentingComplete = true })
                }
            }
        }
    }

    private func card(_ detail: MomentDetail, _ pebble: MomentDetail.Pebble) -> some View {
        ScrollView {
            PebbleCard(detail: detail, pebble: pebble, palette: palettes.palette(for: pebble.emotion.id),
                       ratio: detail.snaps.first.flatMap { aspects.ratio(for: $0.storagePath) },
                       width: CardLayout.cardWidth(available: pageWidth), domains: refs.domains,
                       collectionGlyphs: collectionGlyphs, soulGlyphs: soulGlyphs, soulOrder: request.soulOrder,
                       turn: turn)
                .frame(maxWidth: .infinity)
                .padding(.top, 8)
                .padding(.bottom, 32)
        }
    }

    // MARK: - Data the card reads

    /// Shares the pebble, whose id differs from the moment's; public pebbles only.
    private var shareURL: URL? {
        guard let pebble = model.detail?.pebble, pebble.visibility == .public else { return nil }
        return SharedPebbleLink.url(for: pebble.id)
    }

    private var menuItems: [PebblesMenuItem] {
        HeldCardMenu.items(hasPebble: model.detail?.pebble != nil,
                           editMoment: { isPresentingEditMoment = true },
                           editPebble: { isPresentingEditPebble = true },
                           delete: { if let detail = model.detail { pendingDeletion = MomentDeleteTarget(detail: detail) } })
    }

    private var collectionGlyphs: [UUID: Glyph] {
        Dictionary(refs.collections.compactMap { collection in collection.glyph.map { (collection.id, $0) } },
                   uniquingKeysWith: { first, _ in first })
    }

    private var soulGlyphs: [String: [GlyphStroke]] {
        Dictionary(refs.souls.map { ($0.id.uuidString.lowercased(), $0.glyph.strokes) },
                   uniquingKeysWith: { first, _ in first })
    }

    // MARK: - Changes

    private func reload() async {
        await model.load { try await service.moment(id: request.momentId) }
    }

    private func saved() {
        Task { await reload() }
        model.changed(revision)
    }

    private func delete(_ option: MomentDeleteOption, on target: MomentDeleteTarget) async {
        let outcome = await model.delete(option, target: target, revision: revision) { option, target in
            try await MomentDeletion.perform(option, on: target, client: supabase.client, drafts: completeDrafts)
        }
        switch outcome {
        case .closes:
            onClose()
        case .reloads:
            // In place: the page keeps showing until the fresh detail lands.
            await reload()
        case .failed:
            deleteError = String(localized: "Something went wrong. Please try again.")
        case .ignored:
            break
        }
    }
}

/// The phone width the page assumes until its first layout reports its own.
private enum ReferencePhone {
    static let width: CGFloat = 393
}
```

- [ ] **Step 2: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`. (Nothing presents it yet.)

```bash
git add apps/ios/Pebbles/Features/Path/Card/HeldCardPage.swift
git commit -m "feat(ios): the held card's page, its bar, its sheets and its dialog"
```

---

### Task E1c.5: The interim sheet and `HeldCardHost`

**Files:**
- Modify (replace): `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`
- Create: `apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift`

- [ ] **Step 1: The sheet becomes a thin wrapper**

Replace the contents of `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift` with:

```swift
import SwiftUI

/// E1–E3 interim (#1146 D2): the held card's page in a `.large` sheet, which
/// `HeldCardHost` presents for every caller. The page needs the whole height,
/// which E4's overlay gives it; E5 deletes this file.
struct PebbleDetailSheet: View {
    let request: HeldCardRequest
    let onClose: () -> Void

    var body: some View {
        HeldCardPage(request: request, onClose: onClose)
            .presentationDetents([.large])
    }
}
```

- [ ] **Step 2: The host**

Create `apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift`:

```swift
import SwiftUI

/// The one place the held card is presented (#1146 D1–D2), applied once to
/// `PebblesTabView` in `RootView`, so one presentation serves every tab. In
/// E1–E3 it is a `.large` sheet; a swipe down closes the card as Back does.
/// E4 replaces it with the full-screen layer and its flight.
struct HeldCardHost: ViewModifier {
    let host: HeldCardHostModel

    func body(content: Content) -> some View {
        content.sheet(item: Binding(get: { host.request }, set: { if $0 == nil { host.close() } })) { request in
            PebbleDetailSheet(request: request, onClose: { host.close() })
        }
    }
}

extension View {
    func heldCardHost(_ host: HeldCardHostModel) -> some View {
        modifier(HeldCardHost(host: host))
    }
}
```

- [ ] **Step 3: Build**

Run **Build**. Expected: errors only at the three callers ("missing arguments for parameters 'request', 'onClose'"), fixed in the next task.

---

### Task E1c.6: `RootView`: the host, its scene storage and the reset

**Files:**
- Modify: `apps/ios/Pebbles/RootView.swift`
- Modify: `apps/ios/Pebbles/Services/MomentsRevision.swift`

- [ ] **Step 1: Declare the host and its storage**

In `apps/ios/Pebbles/RootView.swift`, after

```swift
    @State private var cardFrames = CardFrameRegistry()
```

(stack D5a) add:

```swift
    /// The held card's one host (#1146 D1, D7), above the tabs.
    @State private var heldCard = HeldCardHostModel()
    /// Stack C §6: an authed scene key, declared here and cleared in
    /// `resetAuthedSceneState()`. Restored once the launch loader is down.
    @SceneStorage("heldCard") private var storedHeldCard: Data?
    @State private var heldCardRestored = false
```

- [ ] **Step 2: Present it from the tabs, and inject it**

Replace

```swift
                    PebblesTabView(onPathFirstLoad: { pathFeedLoaded = true })
                        .environment(nav)
                        .environment(cardFrames)
```

with

```swift
                    PebblesTabView(onPathFirstLoad: { pathFeedLoaded = true })
                        .heldCardHost(heldCard)
                        .environment(nav)
                        .environment(cardFrames)
                        .environment(heldCard)
```

The host modifier comes first, so the sheet's content inherits every environment value set after it.

- [ ] **Step 3: Mirror and restore**

After

```swift
        .task { restoreTabs() }
```

add:

```swift
        // Restore the held card once the launch loader is down (#1146 D7).
        .task(id: holdsLoader) {
            guard !holdsLoader, !heldCardRestored else { return }
            heldCardRestored = true
            restoreHeldCard()
        }
        .onChange(of: heldCard.snapshot) { _, snapshot in
            // Signed out, the state is being reset, not navigated.
            guard supabase.session != nil else { return }
            storedHeldCard = snapshot?.encoded
        }
```

- [ ] **Step 4: The reset and the restore**

Replace

```swift
    private func resetAuthedSceneState() {
        nav.reset()
        storedTabs = nil
    }
```

with

```swift
    private func resetAuthedSceneState() {
        nav.reset()
        storedTabs = nil
        heldCard.reset()
        storedHeldCard = nil
        heldCardRestored = false
    }

    private func restoreHeldCard() {
        guard let storedHeldCard else { return }
        guard let snapshot = HeldCardSnapshot.decoded(from: storedHeldCard) else {
            logger.error("heldCard scene storage did not decode; no card reopens")
            self.storedHeldCard = nil
            return
        }
        heldCard.restore(snapshot)
    }
```

If `resetAuthedSceneState` carries more lines on `main` (a later stack added a key), keep them and add the three `heldCard` lines after them.

- [ ] **Step 5: `MomentsRevision`'s doc comment**

In `apps/ios/Pebbles/Services/MomentsRevision.swift`, replace

```swift
/// When stack D lands, `PathModel` becomes the reader. When E5 deletes
/// `PebbleDetailSheet`, `HeldCardModel` takes over its bumps.
```

with

```swift
/// `PathModel` is the reader. `HeldCardModel` bumps on every save, delete and
/// completion made on the held card, from the tab that opened it (#1146).
```

If `grep -n "HeldCardModel" apps/ios/Pebbles/Services/MomentsRevision.swift` shows other wording on `main`, replace those two lines with the two above.

- [ ] **Step 6: Build**

Run **Build**. Expected: errors only at the three callers, fixed next.

---

### Task E1c.7: The three callers open through the host

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`

- [ ] **Step 1: Path**

In `apps/ios/Pebbles/Features/Path/PathView.swift`:

1. Delete `    @State private var selectedMomentId: UUID?`.
2. After `    @Environment(MomentsRevision.self) private var revision`, add `    @Environment(HeldCardHostModel.self) private var heldCard`.
3. Delete the sheet:

```swift
            .sheet(item: $selectedMomentId) { id in
                PebbleDetailSheet(momentId: id, origin: .path, onPebbleUpdated: {
                    Task {
                        async let timeline: Void = model.load { try await service.pathMoments() }
                        async let statsReload: Void = stats.refresh()
                        _ = await (timeline, statsReload)
                    }
                })
            }
```

4. Replace

```swift
    /// Until stack E's held card: every press opens today's detail sheet.
    private func open(_ open: SpineOpen) {
        selectedMomentId = open.momentId
    }
```

with

```swift
    /// A press opens the held card (#1146), seeded with the Spine's summary and
    /// its persons order. Every press asks for the recto until E2.
    private func open(_ open: SpineOpen) {
        heldCard.open(HeldCardRequest(momentId: open.momentId, seed: open.summary, soulOrder: open.soulOrder,
                                      origin: .path, onChanged: { reloadAfterCard() }))
    }

    /// The card changed a moment: the Path and the stats reload.
    private func reloadAfterCard() {
        Task {
            async let timeline: Void = model.load { try await service.pathMoments() }
            async let statsReload: Void = stats.refresh()
            _ = await (timeline, statsReload)
        }
    }
```

5. In the `#Preview`, after `.environment(MomentsRevision())`, add `.environment(HeldCardHostModel())`.

- [ ] **Step 2: People and Collections**

In each of `SoulDetailView.swift` (tab `.people`) and `CollectionDetailView.swift` (tab `.collections`):

1. Delete `    @State private var selectedMomentId: UUID?`.
2. After `    @Environment(CompleteDraftStore.self) private var completeDrafts`, add `    @Environment(HeldCardHostModel.self) private var heldCard`.
3. Delete the sheet (shown for People; Collections has `origin: .collections`):

```swift
            // R9: a row opens the moment's read page, not the edit form.
            .sheet(item: $selectedMomentId) { id in
                PebbleDetailSheet(momentId: id, origin: .people, onPebbleUpdated: {
                    Task { await load() }
                    onChanged()
                })
            }
```

4. Replace `onTap: { selectedMomentId = moment.id },` with `onTap: { open(moment) },`.
5. Before the view's `private func load()`, add (with `.collections` in `CollectionDetailView`):

```swift
    /// R9: a row opens the held card, not the edit form; no seed, so no flight (#1146).
    private func open(_ moment: MomentSummary) {
        heldCard.open(HeldCardRequest(momentId: moment.id, origin: .people, onChanged: {
            Task { await load() }
            onChanged()
        }))
    }
```

6. In the `#Preview`, add `.environment(HeldCardHostModel())`.

In `CollectionDetailView.swift` also replace the doc line `/// - Tapping a pebble opens its read page (`PebbleDetailSheet`).` with `/// - Tapping a pebble opens its held card (`HeldCardHostModel.open`).`.

- [ ] **Step 3: Build, suite, lint**

Run **Build**, the **Full suite** and **Lint**. Expected: green. `grep -rn "PebbleDetailSheet(momentId" apps/ios/Pebbles` prints nothing.

- [ ] **Step 4: Smoke on the simulator**

Signed out, the simulator shows nothing past welcome (stack A's lesson), so the smoke is the device's (next task). Here only check the launch: the app opens to welcome with no crash and no sheet.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift apps/ios/Pebbles/RootView.swift apps/ios/Pebbles/Services/MomentsRevision.swift apps/ios/Pebbles/Features/Path/PathView.swift apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift
git commit -m "feat(ios): every pebble opens as the held card"
```

---

### Task E1c.8: Device check (D4 decided here; the orchestrator runs it with the maintainer)

**Files:** possibly `apps/ios/Pebbles/Features/Path/Card/DomainPostmark.swift` (one line).

- [ ] **Step 1: Install on the iPhone 15**

Follow the `project_ios_device_deploy` recipe with this branch. Paper, then Night.

- [ ] **Step 2: The checks (reading only; no writes without asking)**

1. **Path → a photo pebble.** The sheet shows the held card: the recto as the Spine card, the leaf, the footer. Turn: the verso appears at once, same size.
2. **Postmark contrast (D4).** Screenshot the verso of a pebble with two domains, AirDrop it to the Mac, zoom on a stamp, and sample (Digital Color Meter, "sRGB") the faintest ink pixel that is not a pit and the panel next to it. Then:

```bash
python3 -c "
import sys
def lum(h):
    c=[int(h[i:i+2],16)/255 for i in (0,2,4)]
    c=[x/12.92 if x<=0.03928 else ((x+0.055)/1.055)**2.4 for x in c]
    return 0.2126*c[0]+0.7152*c[1]+0.0722*c[2]
a,b=sorted([lum(sys.argv[1]),lum(sys.argv[2])],reverse=True)
print(round((a+0.05)/(b+0.05),2))
" <INK_HEX> <PANEL_HEX>
```

   Expected: ≥ 4.5 on paper and at Night (mocks estimate 5.5). The pits are holes: the panel's colour shows in them, not a lighter ink.
3. **Ring legibility at 1×:** the names read round the edge at arm's length; no glyph overlaps the outer ring.
4. **⋮:** Edit moment, Edit pebble, Delete in orange (not red).
5. **Restore:** turn a card to its verso; press Home; from the Mac, `xcrun devicectl device process terminate --device <id> --pid <pid>` (or stop it from Xcode); relaunch: once the loader is down, the card reopens on its verso, at rest.
6. **People → a soul → a pebble** and **Collections → a collection → a pebble:** the same card opens.
7. **A bare moment** opens the old read view inside the new bar (until E3).

- [ ] **Step 3: If D4 fails**

If the contrast is under 4.5 or the pits read as lighter ink rather than holes, set the fallback (spec D4: Android's speckles) in `DomainPostmark.swift`:

```swift
    static let current: PostmarkInkMode = .speckle
```

then re-run step 2.2, and commit:

```bash
git add apps/ios/Pebbles/Features/Path/Card/DomainPostmark.swift
git commit -m "fix(ios): postmark ink takes the speckle texture, as android's"
```

Record the measured ratios, the mode kept and any failure for the PR body and the Lessons learned (E5).

---

### Task E1c.9: E1c verification and PR

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <E1c-number> --title "feat(ios): the pebble detail becomes the held card" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Part of #1146`; key files; **new patterns for review:** `HeldCardHostModel`, one host in `RootView` for every caller (a sheet until E4), and `@SceneStorage("heldCard")` restoring the card's face, registered in `resetAuthedSceneState()`; the deviations: the host lands in E1 (the fixed decision), so soul and collection rows open through it from here rather than E5, and `HeldCardModel` takes over `MomentsRevision`'s bumps now, from the request's `origin` (a field the spec's request did not list); decision 3 (a restored card reports from `.you`); the device results of E1c.8 with D4's ratios and the mode kept. No Lab Note, no `F-…` ids.

- [ ] **Step 3: Arkaik**

`V-pebble-detail` and `AC-hold-a-pebble-like-a-card-and-turn-it-over` stay `development` (set in E0a.0). Nothing to change; the App marks `releasing` at merge.

---
## Part E2 — `feat/1146-ios-card-turn`

PR title: `feat(ios): turn the held card by hand`. Body opens `Part of #1146`. The turn: a Y rotation with perspective on the panel alone, both faces ready, the swap on the frame that crosses the half, the footer's button and a horizontal drag with springs, a 150 ms crossfade under Reduce Motion; stone presses open the verso.

### Task E2.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1146-ios-card-turn
git branch --show-current
```

Expected: `feat/1146-ios-card-turn`.

---

### Task E2.1: `TurnGeometry` and the perspective probe (decision 1)

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/Logic/TurnGeometry.swift`
- Create: `apps/ios/Pebbles/Features/Path/Card/CardTurnEffect.swift`
- Test: `apps/ios/PebblesTests/Card/TurnGeometryTests.swift`
- Test: `apps/ios/PebblesTests/Card/TurnPerspectiveProbeTests.swift`

Spec §Turn: the first task renders t = 0.25 and measures the near edge before anything else is built. On 2026-10-21 the macOS renderer measured the spec's assumption true (`m34 = −perspective / max(w, h)`; near edge 456 pt, predicted 455.98) and the exact fit scale `1 − sin(πt)·(w/2)/864` holding the near edge at 400 pt for a 400 pt card. `CardTurnEffect` builds that projection itself as a `GeometryEffect`, from the size the effect is handed in the same pass (deviation: `rotation3DEffect(perspective:)` needs the panel's height before layout). The probe holds both the effect and `rotation3DEffect` to the same edge on the simulator.

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/Card/TurnGeometryTests.swift`:

```swift
import CoreGraphics
import SwiftUI
import Testing
@testable import Pebbles

/// #1146 §Turn: `(t > 0.5 ? t − 1 : t) × 180°`, negative in LTR, mirrored in
/// RTL, so text is never mirrored; the eye 864 pt away; the fit keeps the near
/// edge inside the shell (decision 1).
@Suite("TurnGeometry")
struct TurnGeometryTests {
    private func degrees(_ t: Double, rtl: Bool = false) -> Double {
        TurnGeometry.angle(progress: t, rightToLeft: rtl).degrees
    }

    @Test("the angle at 0, 0.49, 0.51 and 1")
    func angle() {
        #expect(abs(degrees(0)) < 1e-9)
        #expect(abs(degrees(0.49) + 88.2) < 1e-9)
        #expect(abs(degrees(0.51) - 88.2) < 1e-9)
        #expect(abs(degrees(1)) < 1e-9)
    }

    @Test("right to left mirrors the angle")
    func rtl() {
        #expect(abs(degrees(0.25, rtl: true) - 45) < 1e-9)
        #expect(abs(degrees(0.25) + 45) < 1e-9)
    }

    @Test("the perspective puts the eye 864 pt from the larger side")
    func perspective() {
        #expect(abs(TurnGeometry.perspective(width: 361, height: 640) - 640 / 864) < 1e-9)
        #expect(abs(TurnGeometry.perspective(width: 500, height: 300) - 500 / 864) < 1e-9)
    }

    @Test("the fit is 1 at rest and smallest edge-on")
    func fit() {
        #expect(abs(TurnGeometry.fitScale(progress: 0, width: 361) - 1) < 1e-9)
        #expect(abs(TurnGeometry.fitScale(progress: 1, width: 361) - 1) < 1e-9)
        let edgeOn = TurnGeometry.fitScale(progress: 0.5, width: 361)
        #expect(abs(edgeOn - (1 - 180.5 / 864)) < 1e-9)
        #expect(edgeOn < TurnGeometry.fitScale(progress: 0.3, width: 361))
    }
}
```

Create `apps/ios/PebblesTests/Card/TurnPerspectiveProbeTests.swift`:

```swift
import CoreGraphics
import SwiftUI
import Testing
@testable import Pebbles

/// #1146 §Turn, decision 1: render a 300 × 400 panel turned to t = 0.25 and
/// measure its two edges. The near edge must be `h·d / (d − (w/2)·sin θ)`
/// without the fit (the eye 864 pt away), and the card's height with it; the
/// end edge (right, in LTR) is the near one, as a page turned by its edge.
@Suite("Turn perspective probe")
@MainActor
struct TurnPerspectiveProbeTests {
    static let size = CGSize(width: 300, height: 400)

    /// The heights of the leftmost and rightmost painted columns.
    private func edges<V: View>(_ view: V) throws -> (left: CGFloat, right: CGFloat) {
        let renderer = ImageRenderer(content: view.frame(width: 600, height: 600))
        renderer.scale = 1
        let image = try #require(renderer.cgImage)
        let width = image.width, height = image.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8,
                                          bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        #expect(drawn)
        func column(_ x: Int) -> Int { (0..<height).filter { pixels[($0 * width + x) * 4 + 3] > 128 }.count }
        let painted = (0..<width).filter { column($0) > 0 }
        let first = try #require(painted.first), last = try #require(painted.last)
        return (CGFloat(column(first + 1)), CGFloat(column(last - 1)))
    }

    private var panel: some View {
        Rectangle().fill(Color.pebbles.onSurface).frame(width: Self.size.width, height: Self.size.height)
    }

    private func nearEdge(_ t: Double) -> CGFloat {
        let lean = Self.size.width / 2 * CGFloat(sin(Double.pi * t))
        return Self.size.height * TurnGeometry.eyeDistance / (TurnGeometry.eyeDistance - lean)
    }

    @Test("SwiftUI's rotation3DEffect puts the eye 864 pt away with this perspective")
    func swiftUIProjection() throws {
        let view = panel.rotation3DEffect(TurnGeometry.angle(progress: 0.25, rightToLeft: false),
                                          axis: (x: 0, y: 1, z: 0), anchor: .center,
                                          perspective: TurnGeometry.perspective(width: Self.size.width,
                                                                                height: Self.size.height))
        let measured = try edges(view)
        #expect(abs(measured.right - nearEdge(0.25)) <= 3, "near edge \(measured.right), predicted \(nearEdge(0.25))")
        #expect(measured.right > measured.left)
    }

    @Test("CardTurnEffect builds the same projection, and its fit holds the near edge to the card", arguments: [0.25, 0.45])
    func effect(t: Double) throws {
        let unfitted = try edges(panel.modifier(CardTurnEffect(progress: t, rightToLeft: false, fits: false)))
        #expect(abs(unfitted.right - nearEdge(t)) <= 6, "near edge \(unfitted.right), predicted \(nearEdge(t))")
        let fitted = try edges(panel.modifier(CardTurnEffect(progress: t, rightToLeft: false)))
        #expect(fitted.right <= Self.size.height + 1, "fitted near edge \(fitted.right)")
        #expect(fitted.right > fitted.left)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run the **Suite command** with `TurnGeometryTests`. Expected: build FAIL with "cannot find 'TurnGeometry' in scope".

- [ ] **Step 3: Implement the geometry**

Create `apps/ios/Pebbles/Features/Path/Card/Logic/TurnGeometry.swift`:

```swift
import CoreGraphics
import SwiftUI

/// The turn's geometry (#1146 §Turn). The panel rotates about Y by
/// `(t > 0.5 ? t − 1 : t) × 180°`, negative in LTR (the end edge lifts toward
/// the eye and swings over to the start edge, as a page turned by its edge
/// under a finger dragging toward the start), mirrored in RTL. Past the half
/// the other face runs back from 90° to 0°, so its text reads the right way.
/// The eye is 864 pt away, Android's `cameraDistance 12 × 72`.
enum TurnGeometry {
    static let eyeDistance: CGFloat = 864

    static func angle(progress: Double, rightToLeft: Bool) -> Angle {
        let t = progress > TurnSettle.half ? progress - 1 : progress
        return .degrees(t * 180 * (rightToLeft ? 1 : -1))
    }

    /// `rotation3DEffect`'s perspective for the eye at `eyeDistance`, its
    /// projection being `m34 = −perspective / max(w, h)` (measured, decision 1).
    static func perspective(width: CGFloat, height: CGFloat) -> CGFloat {
        max(width, height) / eyeDistance
    }

    /// Perspective makes the near edge taller by `d / (d − (w/2)·sin πt)`; this
    /// scale brings it back to the card's height, so the shell never cuts its
    /// corners and the far edge recedes as on a real card (decision 1: the
    /// exact form, where the spec's `1 / (1 + …)` measured 1.5 % over).
    static func fitScale(progress: Double, width: CGFloat) -> CGFloat {
        1 - CGFloat(sin(Double.pi * progress)) * (width / 2) / eyeDistance
    }
}
```

`TurnSettle.half` arrives in Task E2.2. So this task builds alone, add `Card/Logic/TurnSettle.swift` now with only:

```swift
import CoreGraphics

/// The card's drag-to-turn (#1146 §Turn). Task E2.2 completes it.
enum TurnSettle {
    static let half = 0.5
}
```

- [ ] **Step 4: Implement the effect**

Create `apps/ios/Pebbles/Features/Path/Card/CardTurnEffect.swift`:

```swift
import QuartzCore
import SwiftUI

/// D5: the panel's turn as one projection, built from the size the effect is
/// handed in the same pass: rotate about Y by `TurnGeometry.angle`, project
/// with the eye at 864 pt (`m34 = −1/864`), then scale by the fit about the
/// centre. The same transform `rotation3DEffect` makes (the probe holds them
/// together), without needing the panel's height before layout. Animatable on
/// `progress`, so a spring drives it frame by frame; the shell and the footer
/// never move.
struct CardTurnEffect: GeometryEffect {
    var progress: Double
    let rightToLeft: Bool
    /// The probe's unfitted render; always true on the card.
    var fits = true

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        let radians = CGFloat(TurnGeometry.angle(progress: progress, rightToLeft: rightToLeft).radians)
        let fit = fits ? TurnGeometry.fitScale(progress: progress, width: size.width) : 1
        var perspective = CATransform3DIdentity
        perspective.m34 = -1 / TurnGeometry.eyeDistance
        let turned = CATransform3DConcat(CATransform3DMakeRotation(radians, 0, 1, 0), perspective)
        let fitted = CATransform3DConcat(turned, CATransform3DMakeScale(fit, fit, 1))
        let toCentre = CATransform3DMakeTranslation(-size.width / 2, -size.height / 2, 0)
        let back = CATransform3DMakeTranslation(size.width / 2, size.height / 2, 0)
        return ProjectionTransform(CATransform3DConcat(CATransform3DConcat(toCentre, fitted), back))
    }
}

/// D5: which face is opaque on this frame. The committed face below the half,
/// the other from it, decided per frame from the animated progress, so the
/// swap lands exactly on the frame that crosses 0.5. Under Reduce Motion the
/// progress stays 0 and the swap of `isCommitted` crossfades in the caller's
/// animation.
struct CardFaceVisibility: ViewModifier, Animatable {
    var progress: Double
    let isCommitted: Bool

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let shown = isCommitted ? progress < TurnSettle.half : progress >= TurnSettle.half
        content.opacity(shown ? 1 : 0)
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run the **Suite command** with `TurnGeometryTests`, then `TurnPerspectiveProbeTests`. Expected: PASS (4), PASS (3). Measured on macOS on 2026-10-21 with this exact transform: unfitted 456 pt (predicted 455.98), fitted 400 pt at t = 0.25, 394 pt at t = 0.45; `rotation3DEffect` identical.

**If `swiftUIProjection` fails** on the simulator, the runtime's projection is not `−perspective / max(w, h)`: the card does not use `rotation3DEffect`, so nothing changes in the app; record the measured edge in the Lessons learned and delete that one test. **If `effect` fails** with the *left* edge taller, the rotation's sign is the other way on this runtime: negate `radians` in `effectValue` (`-CGFloat(…)`), re-run, and say so in the PR body. **If the fitted edge is over h + 1**, the fit is not exact on this runtime: re-derive it from the measured unfitted edge `E` as `fit = h / E` at that `t`, and replace `fitScale`'s formula with `1 − sin(πt)·(w/2)·k / 864`, where `k = (E − h)·864 / (E·(w/2)·sin πt)`; update `TurnGeometryTests.fit` to the new edge-on value.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/Logic/TurnGeometry.swift apps/ios/Pebbles/Features/Path/Card/Logic/TurnSettle.swift apps/ios/Pebbles/Features/Path/Card/CardTurnEffect.swift apps/ios/PebblesTests/Card/TurnGeometryTests.swift apps/ios/PebblesTests/Card/TurnPerspectiveProbeTests.swift
git commit -m "feat(ios): the turn's geometry, measured against the near edge"
```

---

### Task E2.2: `TurnSettle`

**Files:**
- Modify (replace): `apps/ios/Pebbles/Features/Path/Card/Logic/TurnSettle.swift`
- Test: `apps/ios/PebblesTests/Card/TurnSettleTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Testing
@testable import Pebbles

// Ported from apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/TurnSettleTest.kt
// at d53be9cf, verbatim, plus the spec's RTL sign and the finger's speed in turns a second.
@Suite("TurnSettle (Android TurnSettleTest.kt)")
struct TurnSettleTests {
    @Test("a release past the half completes the turn")
    func pastHalf() {
        #expect(TurnSettle.target(progress: 0.6, velocity: 0) == 1)
    }

    @Test("a release short of the half reverts")
    func shortOfHalf() {
        #expect(TurnSettle.target(progress: 0.4, velocity: 0) == 0)
    }

    @Test("a flick carries the turn whatever the progress")
    func flick() {
        #expect(TurnSettle.target(progress: 0.1, velocity: 2.5) == 1)
        #expect(TurnSettle.target(progress: 0.9, velocity: -2.5) == 0)
    }

    @Test("the flick threshold and the half are inclusive")
    func inclusive() {
        #expect(TurnSettle.target(progress: 0, velocity: 1.5) == 1)
        #expect(TurnSettle.target(progress: 1, velocity: -1.5) == 0)
        #expect(TurnSettle.target(progress: 0.5, velocity: 0) == 1)
    }

    @Test("a slow release never starts away from where it settles")
    func neverAway() {
        #expect(TurnSettle.launchVelocity(target: 1, velocity: -0.8) == 0)
        #expect(TurnSettle.launchVelocity(target: 0, velocity: 0.8) == 0)
    }

    @Test("a release toward where it settles keeps the finger's speed")
    func keepsSpeed() {
        #expect(TurnSettle.launchVelocity(target: 1, velocity: 0.8) == 0.8)
        #expect(TurnSettle.launchVelocity(target: 0, velocity: -2.5) == -2.5)
    }

    @Test("progress maps drag distance against the card width, clamped")
    func progress() {
        #expect(abs(TurnSettle.progress(drag: -150, width: 300, rightToLeft: false) - 0.5) < 0.001)
        #expect(abs(TurnSettle.progress(drag: -900, width: 300, rightToLeft: false) - 1) < 0.001)
        #expect(abs(TurnSettle.progress(drag: 120, width: 300, rightToLeft: false)) < 0.001)
    }

    @Test("a card with no width does not turn")
    func zeroWidth() {
        #expect(TurnSettle.progress(drag: -50, width: 0, rightToLeft: false) == 0)
        #expect(TurnSettle.turnsPerSecond(velocity: -500, width: 0, rightToLeft: false) == 0)
    }

    @Test("right to left turns toward the other edge")
    func rtl() {
        #expect(abs(TurnSettle.progress(drag: 150, width: 300, rightToLeft: true) - 0.5) < 0.001)
        #expect(TurnSettle.progress(drag: -150, width: 300, rightToLeft: true) == 0)
    }

    @Test("the finger's speed is in turns a second, positive toward the other face")
    func speed() {
        #expect(abs(TurnSettle.turnsPerSecond(velocity: -600, width: 300, rightToLeft: false) - 2) < 1e-9)
        #expect(abs(TurnSettle.turnsPerSecond(velocity: 600, width: 300, rightToLeft: true) - 2) < 1e-9)
    }
}
```

Save it as `apps/ios/PebblesTests/Card/TurnSettleTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `TurnSettleTests`. Expected: build FAIL with "type 'TurnSettle' has no member 'target'".

- [ ] **Step 3: Implement**

Replace the contents of `apps/ios/Pebbles/Features/Path/Card/Logic/TurnSettle.swift` with:

```swift
import CoreGraphics

/// The card's drag-to-turn (#1146 §Turn), a port of Android's `TurnSettle.kt`.
/// Progress 0 is the face that was up when the drag began, 1 the other.
/// Dragging toward the start edge (negative x in LTR, positive in RTL) turns it.
enum TurnSettle {
    /// Turns of the card a second past which a release carries in its direction.
    static let flick = 1.5
    /// Past this much of a turn the other face is in front: edge-on at 90°.
    static let half = 0.5

    /// The turn a drag of `drag` makes across a card `width` wide, 0 to 1; 0 while it has no width.
    static func progress(drag: CGFloat, width: CGFloat, rightToLeft: Bool) -> Double {
        guard width > 0 else { return 0 }
        let towardStart = rightToLeft ? drag : -drag
        return min(max(Double(towardStart / width), 0), 1)
    }

    /// `DragGesture.Value.velocity.width` (points a second) as turns a second toward the other face.
    static func turnsPerSecond(velocity: CGFloat, width: CGFloat, rightToLeft: Bool) -> Double {
        guard width > 0 else { return 0 }
        return Double((rightToLeft ? velocity : -velocity) / width)
    }

    /// Where a release settles: 1 completes the turn, 0 reverts it. A flick
    /// carries in its direction whatever the progress (inclusive); a slower
    /// release goes to the nearer face, the half completing.
    static func target(progress: Double, velocity: Double) -> Double {
        if abs(velocity) >= flick { return velocity > 0 ? 1 : 0 }
        return progress >= half ? 1 : 0
    }

    /// The speed the settle starts with: the finger's, unless it points away
    /// from the target, then 0, so a slow release never crosses the half twice.
    static func launchVelocity(target: Double, velocity: Double) -> Double {
        target >= 1 ? max(velocity, 0) : min(velocity, 0)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `TurnSettleTests`. Expected: PASS (10).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/Logic/TurnSettle.swift apps/ios/PebblesTests/Card/TurnSettleTests.swift
git commit -m "feat(ios): where a released turn settles"
```

---

### Task E2.3: `CardTurnModel`, the whole turn

**Files:**
- Modify (replace): `apps/ios/Pebbles/Features/Path/Card/CardTurnModel.swift`
- Modify (replace): `apps/ios/PebblesTests/Card/CardTurnModelTests.swift`

- [ ] **Step 1: Write the failing test**

Replace the contents of `apps/ios/PebblesTests/Card/CardTurnModelTests.swift` with:

```swift
import Testing
@testable import Pebbles

/// #1146 §State model, §Turn: one turn at a time; `front` swaps at the half;
/// `landing`, what is saved, is the face a running turn lands on, the face in
/// front while a finger holds the card, else the face. A landing on 1 commits
/// the other face at progress 0 in one change. The view animates `progress`.
@Suite("CardTurnModel")
@MainActor
struct CardTurnModelTests {
    @Test("the card opens on the face asked for, at rest")
    func opens() {
        let turn = CardTurnModel(face: .verso)
        #expect(turn.face == .verso && turn.progress == 0 && turn.front == .verso && !turn.isBusy)
    }

    @Test("the button turns, and the landing commits the other face at 0")
    func button() {
        let turn = CardTurnModel(face: .recto)
        #expect(turn.beginTurn(reduceMotion: false))
        #expect(turn.landing == .verso && turn.isBusy)
        turn.progress = 1
        turn.settled()
        #expect(turn.face == .verso && turn.progress == 0 && turn.landing == .verso && !turn.isBusy)
    }

    @Test("a turn during a turn is ignored")
    func oneAtATime() {
        let turn = CardTurnModel(face: .recto)
        #expect(turn.beginTurn(reduceMotion: false))
        #expect(!turn.beginTurn(reduceMotion: false))
    }

    @Test("a drag that starts mid-turn is not taken")
    func dragMidTurn() {
        let turn = CardTurnModel(face: .recto)
        _ = turn.beginTurn(reduceMotion: false)
        #expect(!turn.startDrag())
    }

    @Test("while held, the landing is the face in front, swapping at the half")
    func held() {
        let turn = CardTurnModel(face: .recto)
        #expect(turn.startDrag())
        turn.drag(to: 0.6, reduceMotion: false)
        #expect(turn.front == .verso && turn.landing == .verso)
        turn.drag(to: 0.3, reduceMotion: false)
        #expect(turn.front == .recto && turn.landing == .recto)
    }

    @Test("a release past the half settles on the other face and lands there")
    func releasePast() throws {
        let turn = CardTurnModel(face: .recto)
        _ = turn.startDrag()
        turn.drag(to: 0.7, reduceMotion: false)
        let settle = try #require(turn.release(at: 0.7, velocity: 0, reduceMotion: false))
        #expect(settle.target == 1 && settle.springVelocity == 0)
        #expect(turn.landing == .verso)
        turn.progress = settle.target
        turn.settled()
        #expect(turn.face == .verso && turn.progress == 0)
    }

    @Test("a release short of the half settles back")
    func releaseShort() throws {
        let turn = CardTurnModel(face: .recto)
        _ = turn.startDrag()
        turn.drag(to: 0.3, reduceMotion: false)
        let settle = try #require(turn.release(at: 0.3, velocity: 0, reduceMotion: false))
        #expect(settle.target == 0 && turn.landing == .recto)
        turn.progress = settle.target
        turn.settled()
        #expect(turn.face == .recto)
    }

    @Test("a flick carries, with the finger's speed over the distance left")
    func flick() throws {
        let turn = CardTurnModel(face: .recto)
        _ = turn.startDrag()
        turn.drag(to: 0.2, reduceMotion: false)
        let settle = try #require(turn.release(at: 0.2, velocity: 2.4, reduceMotion: false))
        #expect(settle.target == 1)
        #expect(abs(settle.springVelocity - 2.4 / 0.8) < 1e-9)
    }

    @Test("Reduce Motion: a drag moves nothing, a release past the half commits at once")
    func reduceMotion() {
        let turn = CardTurnModel(face: .recto)
        _ = turn.startDrag()
        turn.drag(to: 0.6, reduceMotion: true)
        #expect(turn.progress == 0)
        #expect(turn.release(at: 0.6, velocity: 0, reduceMotion: true) == nil)
        #expect(turn.face == .verso && !turn.isBusy)
        #expect(turn.beginTurn(reduceMotion: true))
        #expect(turn.face == .recto && !turn.isBusy)
    }

    @Test("a drag taken away settles back to the face that was up")
    func cancel() {
        let turn = CardTurnModel(face: .verso)
        _ = turn.startDrag()
        turn.drag(to: 0.4, reduceMotion: false)
        #expect(turn.cancelDrag())
        #expect(turn.landing == .verso)
        turn.progress = 0
        turn.settled()
        #expect(turn.face == .verso && !turn.isBusy)
        #expect(!turn.cancelDrag())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CardTurnModelTests`. Expected: build FAIL with "value of type 'CardTurnModel' has no member 'beginTurn'".

- [ ] **Step 3: Implement**

Replace the contents of `apps/ios/Pebbles/Features/Path/Card/CardTurnModel.swift` with:

```swift
import Observation

/// The held card's turn (#1146 §State model, §Turn), the page's `@State`.
/// `face` changes only when a turn lands; `progress` runs from it toward the
/// other, 0 to 1, and the view animates it (the button's spring, the drag's
/// settle). One turn at a time: the button is ignored while a turn or a drag
/// runs, and a drag that starts mid-turn is not taken.
///
/// `front` is what the footer and VoiceOver follow; `landing` is what is saved,
/// so a restore mid-turn comes back on the face the turn was heading to.
@Observable @MainActor
final class CardTurnModel {
    /// A settle for the view to animate: where to, and the spring's initial
    /// velocity relative to the distance left (`interpolatingSpring`'s unit).
    struct Settle: Equatable {
        let target: Double
        let springVelocity: Double
    }

    private(set) var face: CardFace
    /// 0 = the committed face, 1 = the other.
    var progress: Double
    private(set) var isDragging = false
    /// The face a running settle lands on; nil while none runs.
    private(set) var settlingTo: CardFace?

    init(face: CardFace, progress: Double = 0) {
        self.face = face
        self.progress = progress
    }

    var front: CardFace { progress < TurnSettle.half ? face : face.other }
    var landing: CardFace { settlingTo ?? (isDragging ? front : face) }
    var isBusy: Bool { isDragging || settlingTo != nil }

    /// The footer's button. Under Reduce Motion the face changes at once (the
    /// view crossfades); otherwise the view animates `progress` to 1, then calls
    /// `settled()`. False, and nothing happens, while a turn or a drag runs.
    func beginTurn(reduceMotion: Bool) -> Bool {
        guard !isBusy else { return false }
        if reduceMotion {
            face = face.other
        } else {
            settlingTo = face.other
        }
        return true
    }

    /// A horizontal drag begins on the panel; false while a turn runs.
    func startDrag() -> Bool {
        guard !isBusy else { return false }
        isDragging = true
        return true
    }

    /// The finger moved the turn to `target`. Under Reduce Motion nothing turns until the release.
    func drag(to target: Double, reduceMotion: Bool) {
        guard isDragging, !reduceMotion else { return }
        progress = min(max(target, 0), 1)
    }

    /// The finger lifts at `at`, moving `velocity` turns a second toward the
    /// other face. Under Reduce Motion a release that would complete commits at
    /// once and nil is returned; otherwise the settle for the view to animate.
    func release(at: Double, velocity: Double, reduceMotion: Bool) -> Settle? {
        guard isDragging else { return nil }
        isDragging = false
        let target = TurnSettle.target(progress: at, velocity: velocity)
        if reduceMotion {
            if target >= 1 { face = face.other }
            progress = 0
            return nil
        }
        settlingTo = target >= 1 ? face.other : face
        let launch = TurnSettle.launchVelocity(target: target, velocity: velocity)
        return Settle(target: target, springVelocity: launch / max(abs(target - progress), 0.05))
    }

    /// The gesture was taken away (the scroll took it, the page left): true when
    /// the view should spring back to the face that was up.
    func cancelDrag() -> Bool {
        guard isDragging else { return false }
        isDragging = false
        settlingTo = face
        return true
    }

    /// A settle's animation finished. A landing on 1 commits the other face at
    /// 0 in one change, so no frame shows the new face at 180°; the view calls
    /// it in a transaction without animation.
    func settled() {
        if progress >= 1 {
            face = face.other
            progress = 0
        }
        settlingTo = nil
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `CardTurnModelTests`. Expected: PASS (10). So this commit builds, in `apps/ios/Pebbles/Features/Path/Card/PebbleCard.swift` replace `onTurn: { turn.turnInstantly() })` with `onTurn: { _ = turn.beginTurn(reduceMotion: true) })` (an instant turn, as in E1, until the next task replaces the card); then **Build**: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/CardTurnModel.swift apps/ios/Pebbles/Features/Path/Card/PebbleCard.swift apps/ios/PebblesTests/Card/CardTurnModelTests.swift
git commit -m "feat(ios): the held card's turn as state, one at a time"
```

---

### Task E2.4: The card turns: the rotation, the button's spring, the drag and the crossfade

**Files:**
- Modify (replace): `apps/ios/Pebbles/Features/Path/Card/PebbleCard.swift`

- [ ] **Step 1: Replace the card**

Replace the contents of `apps/ios/Pebbles/Features/Path/Card/PebbleCard.swift` with:

```swift
import SwiftUI

/// The held card (#1146): a still shell (`SpineCardShell`), a panel that turns,
/// and the still footer. Both faces are built and laid out in one `FaceStack`,
/// each `.compositingGroup()`; which is opaque is decided per frame from the
/// animated progress (`CardFaceVisibility`), and only the one in front speaks
/// to VoiceOver. The panel alone rotates on Y (`CardTurnEffect`).
///
/// Ways to turn: the footer's button (a 0.42 s spring, no bounce), and a
/// horizontal drag on the panel measured in its unrotated parent (a flick
/// carries, a release past the half completes, `TurnSettle`). For VoiceOver the
/// footer's button is the turn; the card has no custom action. Under Reduce
/// Motion the faces crossfade in 0.15 s and a drag commits only on release.
struct PebbleCard: View {
    let detail: MomentDetail
    let pebble: MomentDetail.Pebble
    let palette: EmotionPalette?
    /// The photo's ratio once probed; nil holds the square.
    let ratio: CGFloat?
    let width: CGFloat
    let domains: [Domain]
    let collectionGlyphs: [UUID: Glyph]
    let soulGlyphs: [String: [GlyphStroke]]
    let soulOrder: [String]
    let turn: CardTurnModel

    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var layoutDirection
    @State private var lock: DragLock?
    @GestureState private var dragging = false

    /// The direction a drag committed to on its first change.
    private enum DragLock { case turning, ignored }

    private var rightToLeft: Bool { layoutDirection == .rightToLeft }
    /// Reduce Motion never rotates: the progress the faces see stays 0.
    private var shownProgress: Double { reduceMotion ? 0 : turn.progress }

    var body: some View {
        let stone = SpineColors.palette(palette, isBlank: false)
        SpineCardShell(palette: stone, polarity: pebble.valence.polarity, intensity: pebble.intensity) {
            VStack(spacing: 0) {
                panel
                    .frame(width: width)
                    .modifier(CardTurnEffect(progress: shownProgress, rightToLeft: rightToLeft))
                    .contentShape(Rectangle())
                    .simultaneousGesture(turnDrag)
                    .onChange(of: dragging) { _, isDragging in
                        guard !isDragging else { return }
                        lock = nil
                        if turn.cancelDrag() { settleBack() }
                    }
                TurnFooter(progress: shownProgress, face: turn.face, visibility: pebble.visibility,
                           stone: SpineColors.spot(stone, scheme: scheme), onTurn: turnByButton)
                    .frame(width: width)
            }
        }
        .accessibilityElement(children: .contain)
    }

    private var panel: some View {
        FaceStack {
            face(.recto) {
                CardRecto(detail: detail, pebble: pebble, palette: palette, ratio: ratio, width: width,
                          soulGlyphs: soulGlyphs, soulOrder: soulOrder)
            }
            face(.verso) {
                CardVerso(pebble: pebble, summary: MomentSummary(detail: detail), palette: palette, width: width,
                          domains: domains, collectionGlyphs: collectionGlyphs)
            }
        }
    }

    private func face<Content: View>(_ role: CardFace, @ViewBuilder _ content: () -> Content) -> some View {
        content()
            .compositingGroup()
            .modifier(CardFaceVisibility(progress: shownProgress, isCommitted: turn.face == role))
            .accessibilityHidden(turn.front != role)
    }

    // MARK: - The button

    private func turnByButton() {
        if reduceMotion {
            // The outgoing face is hidden from VoiceOver at once; the picture crossfades.
            withAnimation(.easeInOut(duration: 0.15)) { _ = turn.beginTurn(reduceMotion: true) }
            return
        }
        guard turn.beginTurn(reduceMotion: false) else { return }
        withAnimation(.spring(duration: 0.42, bounce: 0), completionCriteria: .logicallyComplete) {
            turn.progress = 1
        } completion: {
            land()
        }
    }

    /// The landing commits the other face at 0 with no animation, so no frame
    /// shows the new face at 180°.
    private func land() {
        withTransaction(Transaction(animation: nil)) { turn.settled() }
    }

    // MARK: - The drag

    private var turnDrag: some Gesture {
        DragGesture(minimumDistance: 12)
            .updating($dragging) { _, state, _ in state = true }
            .onChanged { value in
                if lock == nil {
                    // The direction lock: horizontal turns the card, anything else
                    // is left to the scroll until the finger lifts.
                    let horizontal = abs(value.translation.width) > abs(value.translation.height)
                    lock = horizontal && turn.startDrag() ? .turning : .ignored
                }
                guard lock == .turning else { return }
                turn.drag(to: TurnSettle.progress(drag: value.translation.width, width: width, rightToLeft: rightToLeft),
                          reduceMotion: reduceMotion)
            }
            .onEnded { value in
                defer { lock = nil }
                guard lock == .turning else { return }
                let at = TurnSettle.progress(drag: value.translation.width, width: width, rightToLeft: rightToLeft)
                let velocity = TurnSettle.turnsPerSecond(velocity: value.velocity.width, width: width,
                                                         rightToLeft: rightToLeft)
                if reduceMotion {
                    withAnimation(.easeInOut(duration: 0.15)) {
                        _ = turn.release(at: at, velocity: velocity, reduceMotion: true)
                    }
                    return
                }
                guard let settle = turn.release(at: at, velocity: velocity, reduceMotion: false) else { return }
                withAnimation(.interpolatingSpring(duration: 0.35, bounce: 0, initialVelocity: settle.springVelocity),
                              completionCriteria: .logicallyComplete) {
                    turn.progress = settle.target
                } completion: {
                    land()
                }
            }
    }

    /// A drag taken away (the scroll took it, the page left) reverts.
    private func settleBack() {
        withAnimation(.spring(duration: 0.21, bounce: 0), completionCriteria: .logicallyComplete) {
            turn.progress = 0
        } completion: {
            land()
        }
    }
}

/// The still footer for the turn: it names the face not in front and switches
/// at the half, with the picture, because it reads the animated progress
/// (`Animatable`), so a drag held past the half offers the turn back.
private struct TurnFooter: View, Animatable {
    var progress: Double
    let face: CardFace
    let visibility: Visibility
    let stone: Color
    let onTurn: () -> Void

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    var body: some View {
        let front = progress < TurnSettle.half ? face : face.other
        CardFooter(visibility: visibility, turnTo: front.other, stone: stone, onTurn: onTurn)
    }
}
```

- [ ] **Step 2: Build, suite, lint**

Run **Build**, the **Full suite** and **Lint**. Expected: green (the E1b references still pass: at rest the effect is the identity).

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/PebbleCard.swift
git commit -m "feat(ios): the held card turns on its axis, by its button or by hand"
```

---

### Task E2.5: The press opens its face

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Card/HeldCardRequest.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`
- Test: `apps/ios/PebblesTests/Card/CardFaceRequestTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1146 §Turn: a stone press asks for the verso, a card or entry press the
/// recto; a bare moment always opens its plain page, whatever was pressed.
@Suite("Card face requests")
@MainActor
struct CardFaceRequestTests {
    private func summary(pebble: Bool) -> MomentSummary {
        let id = UUID()
        return MomentSummary(
            id: id, name: "Picnic", happenedAt: Vectors.at("2026-10-08T19:00:00Z"),
            createdAt: Vectors.at("2026-10-08T19:00:00Z"), firstSnapPath: nil,
            pebble: pebble ? MomentSummary.PebbleSummary(id: id, intensity: 2, positiveness: 1, visibility: .private,
                                                         emotion: nil, glyph: nil, souls: []) : nil)
    }

    @Test("a stone press asks for the verso")
    func stone() {
        let moment = summary(pebble: true)
        let request = HeldCardRequest(SpineOpen(momentId: moment.id, face: .verso, summary: moment, soulOrder: ["a"]),
                                      origin: .path, onChanged: {})
        #expect(request.face == .verso)
        #expect(request.seed?.id == moment.id && request.soulOrder == ["a"] && request.origin == .path)
    }

    @Test("a card or entry press asks for the recto")
    func card() {
        let moment = summary(pebble: true)
        let request = HeldCardRequest(SpineOpen(momentId: moment.id, face: .recto, summary: moment, soulOrder: []),
                                      origin: .path, onChanged: {})
        #expect(request.face == .recto)
    }

    @Test("a bare moment opens its plain page whatever was pressed")
    func bare() {
        let moment = summary(pebble: false)
        let request = HeldCardRequest(SpineOpen(momentId: moment.id, face: .verso, summary: moment, soulOrder: []),
                                      origin: .path, onChanged: {})
        #expect(request.face == .recto)
    }
}
```

Save it as `apps/ios/PebblesTests/Card/CardFaceRequestTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CardFaceRequestTests`. Expected: build FAIL with "missing argument for parameter 'momentId'".

- [ ] **Step 3: Implement**

At the end of `apps/ios/Pebbles/Features/Path/Card/HeldCardRequest.swift`, add:

```swift
extension HeldCardRequest {
    /// A Spine press (#1146 §Turn): the face the press asked for, the summary
    /// that seeds the first frame and the page's persons order. A bare moment
    /// has no card to turn, so it always opens on its plain page.
    init(_ open: SpineOpen, origin: AppTab, onChanged: @escaping @MainActor () -> Void) {
        self.init(momentId: open.momentId, face: open.summary.pebble == nil ? .recto : open.face,
                  seed: open.summary, soulOrder: open.soulOrder, origin: origin, onChanged: onChanged)
    }
}
```

In `apps/ios/Pebbles/Features/Path/PathView.swift`, replace

```swift
    /// A press opens the held card (#1146), seeded with the Spine's summary and
    /// its persons order. Every press asks for the recto until E2.
    private func open(_ open: SpineOpen) {
        heldCard.open(HeldCardRequest(momentId: open.momentId, seed: open.summary, soulOrder: open.soulOrder,
                                      origin: .path, onChanged: { reloadAfterCard() }))
    }
```

with

```swift
    /// A press opens the held card on the face it asked for (#1146): the stone
    /// the verso, after its dip; the card or the entry the recto.
    private func open(_ open: SpineOpen) {
        heldCard.open(HeldCardRequest(open, origin: .path, onChanged: { reloadAfterCard() }))
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `CardFaceRequestTests`, then **Build**. Expected: PASS (3), `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/HeldCardRequest.swift apps/ios/Pebbles/Features/Path/PathView.swift apps/ios/PebblesTests/Card/CardFaceRequestTests.swift
git commit -m "feat(ios): a stone press opens the card on its verso"
```

---

### Task E2.6: The mid-turn references

**Files:**
- Modify: `apps/ios/PebblesTests/Card/HeldCardStill.swift`
- Create: `apps/ios/PebblesTests/References/HeldCardTurnReferences.swift`

- [ ] **Step 1: The still takes a frozen turn**

In `HeldCardStill.swift`, replace

```swift
    var face: CardFace = .recto
```

with

```swift
    var face: CardFace = .recto
    /// A turn frozen part way (E2's references).
    var progress: Double = 0
```

and replace `soulOrder: [], turn: CardTurnModel(face: face))` with `soulOrder: [], turn: CardTurnModel(face: face, progress: progress))`.

- [ ] **Step 2: The suite**

Create `apps/ios/PebblesTests/References/HeldCardTurnReferences.swift`:

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1146 E2: the photo card frozen at 0.3 (the recto leaning away, the end
    /// edge near) and 0.7 (the verso coming round), paper and Night. The shell
    /// and footer stay still; at 0.3 the footer offers the pebble, at 0.7 the
    /// moment. Android: PebbleCardTurnScreenshots.
    @Suite("Held card turn references")
    @MainActor
    struct HeldCardTurnReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func midTurn(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f1, progress: 0.3), named: "turn-30",
                                         config: ReferenceConfig(scheme: scheme))
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f1, progress: 0.7), named: "turn-70",
                                         config: ReferenceConfig(scheme: scheme))
        }
    }
}
```

- [ ] **Step 3: Look locally, then commit**

Run **Look** at `HeldCardTurnReferences`. At 0.3 the recto leans with its right edge nearer and taller than its left, inside the shell; at 0.7 the verso shows, its left edge nearer, its text the right way round; the footer reads *Pebble* then *Moment*; nothing pokes past the shell's corners. Clean.

```bash
git add apps/ios/PebblesTests/Card/HeldCardStill.swift apps/ios/PebblesTests/References/HeldCardTurnReferences.swift
git commit -m "test(ios): held card references at a third and two thirds of a turn"
```

---

### Task E2.7: Device check (the orchestrator runs it with the maintainer)

**Files:** none, unless the fallback applies (Task E2.8).

- [ ] **Step 1: On the iPhone 15 (current iOS) and on a simulator with an iOS 17 runtime**

Install from this branch. If no iOS 17 runtime is installed (`xcrun simctl list runtimes | grep "iOS 17"`), install it (`xcodebuild -downloadPlatform iOS -buildVersion 17.5`) or say so in the PR body. Then:

1. **The button:** the panel turns about its centre in ~0.4 s, the shell and footer still; the face swaps edge-on, never a mirrored frame; the footer's word switches at the half.
2. **The drag:** a slow horizontal drag toward the start edge follows the finger; released past the half it completes, short of it it reverts; a quick flick from 20 % carries; a drag that starts vertical scrolls the page and never turns it; a horizontal drag never scrolls it.
3. **Mid-turn taps:** pressing Turn during a turn does nothing; starting a drag during a button turn does nothing.
4. **Reduce Motion** on: the button crossfades in 0.15 s, no rotation; a drag moves nothing and commits on release past the half.
5. **Restore:** turn halfway by hand and hold, background, terminate, relaunch: the card reopens on the face it was in front.
6. **VoiceOver:** on the recto, swipe through to the end: the last element is "Turn over to your pebble, button"; double-tap turns; focus lands in the verso, whose first element is "Your pebble, heading, Highlight of my week". Nothing of the hidden face is reachable.
7. **D4, in the turn:** the postmarks' pits survive in a turning panel (pause a drag at 0.6: the stamps still show holes, not flattened ink).
8. **A stone press** on the Spine plays its dip and opens the card on the verso; a card press opens the recto at once.

- [ ] **Step 2: Decide the fallback**

If item 2 fails on either runtime (the scroll steals horizontal drags, or the drag blocks vertical scrolls), run Task E2.8. Otherwise skip it. Record the runtime versions and the result.

---

### Task E2.8 (only if E2.7's drag check failed): the pan recogniser

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/TurnPanFallback.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Card/PebbleCard.swift`

- [ ] **Step 1: The recogniser**

Create `apps/ios/Pebbles/Features/Path/Card/TurnPanFallback.swift`:

```swift
import SwiftUI
import UIKit

/// The drag's fallback (#1146 §Turn, gesture arbitration): a
/// `UIPanGestureRecognizer` over the panel that begins only when the pan is
/// more horizontal than vertical, so the page's scroll keeps every vertical
/// drag. It feeds the same `CardTurnModel` through the same callbacks as the
/// SwiftUI drag. Transparent to touches it does not take and to VoiceOver.
struct TurnPanFallback: UIViewRepresentable {
    /// A horizontal pan began; false when the card will not take it.
    let onBegan: () -> Bool
    /// The pan's horizontal translation, in points.
    let onChanged: (CGFloat) -> Void
    /// The release: translation and velocity, in points and points a second.
    let onEnded: (CGFloat, CGFloat) -> Void
    let onCancelled: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.isOpaque = false
        view.isAccessibilityElement = false
        view.accessibilityElementsHidden = true
        let pan = UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.pan(_:)))
        pan.delegate = context.coordinator
        view.addGestureRecognizer(pan)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.parent = self
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: TurnPanFallback
        private var taken = false

        init(parent: TurnPanFallback) {
            self.parent = parent
        }

        func gestureRecognizerShouldBegin(_ recognizer: UIGestureRecognizer) -> Bool {
            guard let pan = recognizer as? UIPanGestureRecognizer else { return false }
            let velocity = pan.velocity(in: pan.view)
            return abs(velocity.x) > abs(velocity.y)
        }

        @objc func pan(_ recognizer: UIPanGestureRecognizer) {
            let translation = recognizer.translation(in: recognizer.view).x
            switch recognizer.state {
            case .began:
                taken = parent.onBegan()
            case .changed:
                if taken { parent.onChanged(translation) }
            case .ended:
                if taken { parent.onEnded(translation, recognizer.velocity(in: recognizer.view).x) }
                taken = false
            case .cancelled, .failed:
                if taken { parent.onCancelled() }
                taken = false
            default:
                break
            }
        }
    }
}
```

- [ ] **Step 2: The card uses it in place of the SwiftUI drag**

In `PebbleCard.swift`, replace

```swift
                    .contentShape(Rectangle())
                    .simultaneousGesture(turnDrag)
                    .onChange(of: dragging) { _, isDragging in
                        guard !isDragging else { return }
                        lock = nil
                        if turn.cancelDrag() { settleBack() }
                    }
```

with

```swift
                    .overlay {
                        TurnPanFallback(
                            onBegan: { turn.startDrag() },
                            onChanged: { dx in
                                turn.drag(to: TurnSettle.progress(drag: dx, width: width, rightToLeft: rightToLeft),
                                          reduceMotion: reduceMotion)
                            },
                            onEnded: { dx, vx in release(translation: dx, velocity: vx) },
                            onCancelled: { if turn.cancelDrag() { settleBack() } }
                        )
                    }
```

Then move the body of `turnDrag`'s `.onEnded` into a function, and delete `turnDrag`, `lock`, `DragLock` and `@GestureState private var dragging`:

```swift
    private func release(translation: CGFloat, velocity: CGFloat) {
        let at = TurnSettle.progress(drag: translation, width: width, rightToLeft: rightToLeft)
        let turns = TurnSettle.turnsPerSecond(velocity: velocity, width: width, rightToLeft: rightToLeft)
        if reduceMotion {
            withAnimation(.easeInOut(duration: 0.15)) { _ = turn.release(at: at, velocity: turns, reduceMotion: true) }
            return
        }
        guard let settle = turn.release(at: at, velocity: turns, reduceMotion: false) else { return }
        withAnimation(.interpolatingSpring(duration: 0.35, bounce: 0, initialVelocity: settle.springVelocity),
                      completionCriteria: .logicallyComplete) {
            turn.progress = settle.target
        } completion: {
            land()
        }
    }
```

- [ ] **Step 3: Build, re-check, commit**

Run **Build** and the **Full suite**; repeat E2.7 items 2–4 on both runtimes.

```bash
git add apps/ios/Pebbles/Features/Path/Card/TurnPanFallback.swift apps/ios/Pebbles/Features/Path/Card/PebbleCard.swift
git commit -m "fix(ios): turn the card through a horizontal pan the scroll can refuse"
```

---

### Task E2.9: E2 verification, PR and CI references

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <E2-number> --title "feat(ios): turn the held card by hand" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Part of #1146`; key files; decision 1 with the probe's numbers (spec form 406 pt over a 400 pt card; exact form 400 pt); **deviation:** `CardTurnEffect` is a `GeometryEffect` building `rotation3DEffect`'s projection from its own size (the probe holds them equal), because `rotation3DEffect(perspective:)` needs the panel's height before layout; the footer switches at the half through an `Animatable` view; the device results of E2.7 with the runtime versions, and whether E2.8's pan fallback was needed. No Lab Note, no `F-…` ids.

- [ ] **Step 3: Rebaseline on CI**

Run **Rebaseline on CI**. Compare `turn-30-paper-en.png` with Android's turn preview at 0.3: the same lean, the near edge inside the shell.

---
## Part E3 — `feat/1146-ios-bare-moment`

PR title: `feat(ios): the bare moment and the card it prints`. Body opens `Part of #1146`. A moment with no pebble opens as a plain page; completing it prints its card into the place, as state (D6); a restore cuts a print short.

### Task E3.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1146-ios-bare-moment
git branch --show-current
```

Expected: `feat/1146-ios-bare-moment`.

---

### Task E3.1: `CompletionMotion` and `PrintPart`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/Logic/CompletionMotion.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Card/CardVerso.swift` (the E1b stubs go)
- Test: `apps/ios/PebblesTests/Card/CompletionMotionTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
@testable import Pebbles

// Ported from apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/CompletionMotionTest.kt
// at d53be9cf, verbatim: the print's one timeline through its three phases and the parts' stagger.
@Suite("CompletionMotion (Android CompletionMotionTest.kt)")
struct CompletionMotionTests {
    private let eps = 1e-4

    private func expect(_ expected: CompletionMotion.Progress, at t: Double) {
        let actual = CompletionMotion.at(t)
        #expect(abs(actual.print - expected.print) < eps, "print at \(t)")
        #expect(abs(actual.stone - expected.stone) < eps, "stone at \(t)")
        #expect(abs(actual.rise - expected.rise) < eps, "rise at \(t)")
    }

    @Test("the phases run over 0 to 0.35, 0.25 to 0.6 and 0.6 to 1")
    func phases() {
        expect(.init(print: 0, stone: 0, rise: 0), at: 0)
        expect(.init(print: 0.2 / 0.35, stone: 0, rise: 0), at: 0.2)
        expect(.init(print: 1, stone: 0.1 / 0.35, rise: 0), at: 0.35)
        expect(.init(print: 1, stone: 1, rise: 0), at: 0.6)
        expect(.init(print: 1, stone: 1, rise: 0.5), at: 0.8)
        expect(.rest, at: 1)
    }

    @Test("the dashed stone lands, and the lit stone is in, at the press")
    func press() {
        let stone = CompletionMotion.at(CompletionMotion.pressAt).stone
        #expect(abs(stone - CompletionMotion.landing) < eps)
        #expect(abs(CompletionMotion.litStoneAlpha(stone) - 1) < eps)
        #expect(abs(CompletionMotion.litStoneAlpha(CompletionMotion.at(0.3).stone)) < eps)
    }

    @Test("the parts print in turn, and all are printed when the print is")
    func parts() {
        let halfway = 0.5
        #expect(abs(PrintPart.card.alpha(halfway) - 1) < eps)
        let order = [PrintPart.lockup, .postmarks, .band, .footer].map { $0.alpha(halfway) }
        #expect(order == order.sorted(by: >))
        #expect(abs(PrintPart.footer.alpha(halfway)) < eps)
        for part in PrintPart.allCases {
            #expect(abs(part.alpha(1) - 1) < eps, "\(part) at 1")
            #expect(abs(part.alpha(0)) < eps, "\(part) at 0")
        }
    }

    @Test("the timeline runs over 1.6 s, clamped")
    func timeline() {
        #expect(CompletionMotion.timeline(elapsed: 0.8, duration: CompletionMotion.duration) == 0.5)
        #expect(CompletionMotion.timeline(elapsed: 3, duration: CompletionMotion.duration) == 1)
        #expect(CompletionMotion.timeline(elapsed: -1, duration: CompletionMotion.duration) == 0)
    }
}
```

Save it as `apps/ios/PebblesTests/Card/CompletionMotionTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CompletionMotionTests`. Expected: build FAIL with "type 'CompletionMotion' has no member 'at'".

- [ ] **Step 3: Remove the stubs**

In `apps/ios/Pebbles/Features/Path/Card/CardVerso.swift`, delete everything from `// E3 replaces these with the print's timeline` to the end of the file (the stub `PrintPart`, `CompletionMotion`, `PrintAnchor` and `PrintAnchors`). `PrintAnchor` and `PrintAnchors` come back in Task E3.5's `CardPrint.swift`; add them there now, in a new file `apps/ios/Pebbles/Features/Path/Card/CardPrint.swift`, so this task builds:

```swift
import SwiftUI

/// What the print reads its geometry from, in one layout pass (anchors, not
/// measured state, so it also holds under `ImageRenderer`): the bare page's
/// place and dashed stone, and the verso's stone.
enum PrintAnchor: Hashable {
    case place, ghost, versoStone
}

struct PrintAnchors: PreferenceKey {
    static let defaultValue: [PrintAnchor: Anchor<CGRect>] = [:]

    static func reduce(value: inout [PrintAnchor: Anchor<CGRect>], nextValue: () -> [PrintAnchor: Anchor<CGRect>]) {
        value.merge(nextValue()) { $1 }
    }
}
```

- [ ] **Step 4: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/Logic/CompletionMotion.swift`:

```swift
import SwiftUI

/// The print's one timeline (#1146 §The print, a port of Android's
/// `CompletionMotion`): a linear 0 to 1 over 1.6 s, mapped through
/// piecewise-linear phases, so every part reads the same clock and nothing
/// drifts. `print` runs over 0–0.35 (the card appears in the place), `stone`
/// over 0.25–0.6 (the dashed stone flies to the clearing; the lit stone presses
/// in at 0.45), `rise` over 0.6–1 (the page steps aside, the card rises home).
enum CompletionMotion {
    static let duration: TimeInterval = 1.6
    /// Reduced motion, or a place taller than the view: the page crossfades to the card.
    static let crossfade: TimeInterval = 0.25

    static let printEnd = 0.35
    static let stoneStart = 0.25
    static let stoneEnd = 0.6
    /// The verso stone presses in here, on the timeline.
    static let pressAt = 0.45
    /// `pressAt` in the stone phase: the dashed stone lands, the lit stone is in.
    static let landing = (pressAt - stoneStart) / (stoneEnd - stoneStart)
    static let pageScaleEnd = 0.97

    struct Progress: Equatable {
        let print: Double
        let stone: Double
        let rise: Double

        /// Printed and home: what a card that is not printing reads.
        static let rest = Progress(print: 1, stone: 1, rise: 1)
    }

    static func at(_ t: Double) -> Progress {
        Progress(print: print(t), stone: stone(t), rise: rise(t))
    }

    static func print(_ t: Double) -> Double { ramp(t, from: 0, to: printEnd) }
    static func stone(_ t: Double) -> Double { ramp(t, from: stoneStart, to: stoneEnd) }
    static func rise(_ t: Double) -> Double { ramp(t, from: stoneEnd, to: 1) }

    /// Within the stone phase: the flying outline's travel, its alpha, the lit stone's alpha.
    static func flight(_ stone: Double) -> Double { ramp(stone, from: 0, to: landing) }
    static func ghostAlpha(_ stone: Double) -> Double { 1 - ramp(stone, from: landing, to: landing + 0.2) }
    static func litStoneAlpha(_ stone: Double) -> Double { ramp(stone, from: landing - 0.1, to: landing) }

    /// Within the rise: the page fades as the card climbs.
    static func pageAlpha(_ rise: Double) -> Double { 1 - ramp(rise, from: 0, to: 0.6) }

    /// The timeline for `elapsed` seconds of a print (or a crossfade) `duration` long.
    static func timeline(elapsed: TimeInterval, duration: TimeInterval) -> Double {
        min(max(elapsed / duration, 0), 1)
    }

    /// Android's `FastOutSlowInEasing`, cubic-bezier(0.4, 0, 0.2, 1).
    static func ease(_ x: Double) -> Double {
        UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.4, y: 0), endControlPoint: UnitPoint(x: 0.2, y: 1))
            .value(at: min(max(x, 0), 1))
    }

    static func ramp(_ x: Double, from start: Double, to end: Double) -> Double {
        min(max((x - start) / (end - start), 0), 1)
    }
}

/// The parts that print in turn across `CompletionMotion.print` (#1146 §The
/// print): the card itself first, then the lockup, the postmarks, the band and
/// the footer.
enum PrintPart: CaseIterable {
    case card, lockup, postmarks, band, footer

    private var window: (start: Double, end: Double) {
        switch self {
        case .card: return (0, 0.4)
        case .lockup: return (0.25, 0.55)
        case .postmarks: return (0.45, 0.75)
        case .band: return (0.6, 0.9)
        case .footer: return (0.7, 1)
        }
    }

    func alpha(_ print: Double) -> Double {
        CompletionMotion.ramp(print, from: window.start, to: window.end)
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run the **Suite command** with `CompletionMotionTests`, then **Build**. Expected: PASS (4), `** BUILD SUCCEEDED **`.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/Logic/CompletionMotion.swift apps/ios/Pebbles/Features/Path/Card/CardPrint.swift apps/ios/Pebbles/Features/Path/Card/CardVerso.swift apps/ios/PebblesTests/Card/CompletionMotionTests.swift
git commit -m "feat(ios): the print's one timeline and its parts"
```

---

### Task E3.2: `PrintStart` and the bare kicker

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/Logic/PrintStart.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Card/Logic/CardDateline.swift`
- Test: `apps/ios/PebblesTests/Card/PrintStartTests.swift`
- Test: `apps/ios/PebblesTests/Card/BareKickerTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/Card/PrintStartTests.swift`:

```swift
import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

// The decisions of apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/CardCompletionPlaceTest.kt
// at d53be9cf, as a pure function over the place's and the view's rects (a 390 × 800 view).
@Suite("PrintStart (Android CardCompletionPlaceTest.kt)")
struct PrintStartTests {
    private let view = CGRect(x: 0, y: 0, width: 390, height: 800)

    private func place(top: CGFloat, height: CGFloat) -> CGRect {
        CGRect(x: 16, y: top, width: 358, height: height)
    }

    @Test("a place below the fold is scrolled into view, whole, and printed into")
    func belowFold() {
        #expect(PrintStart.decide(place: place(top: 1_500, height: 400), viewport: view, reduceMotion: false)
            == .scrollThenPrint)
    }

    @Test("a place cut by the screen's foot is scrolled up until it shows whole")
    func cutByFoot() {
        #expect(PrintStart.decide(place: place(top: 600, height: 400), viewport: view, reduceMotion: false)
            == .scrollThenPrint)
    }

    @Test("a place already on screen is printed into where it stands")
    func onScreen() {
        #expect(PrintStart.decide(place: place(top: 100, height: 400), viewport: view, reduceMotion: false) == .print)
    }

    @Test("a place taller than the view crossfades")
    func tooTall() {
        #expect(PrintStart.decide(place: place(top: 1_500, height: 1_000), viewport: view, reduceMotion: false)
            == .crossfade)
    }

    @Test("Reduce Motion, or a place not yet measured, crossfades")
    func crossfades() {
        #expect(PrintStart.decide(place: place(top: 100, height: 400), viewport: view, reduceMotion: true) == .crossfade)
        #expect(PrintStart.decide(place: nil, viewport: view, reduceMotion: false) == .crossfade)
    }

    @Test("a restore that finds a print under way for this moment cuts it short")
    func cutShort() {
        let id = UUID()
        #expect(PrintStart.cutsShort(printingFor: id, momentId: id, hasPebble: true))
        #expect(!PrintStart.cutsShort(printingFor: nil, momentId: id, hasPebble: true))
        #expect(!PrintStart.cutsShort(printingFor: UUID(), momentId: id, hasPebble: true))
        #expect(!PrintStart.cutsShort(printingFor: id, momentId: id, hasPebble: false))
    }
}
```

Create `apps/ios/PebblesTests/Card/BareKickerTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

// The kicker of apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/BareMomentPageTest.kt
// at d53be9cf: the full day and the time, in the zone. The day strings were
// formatted with this exact style on 2026-10-21; the time is the locale's short
// time, compared through the same formatter so ICU's leading zero cannot differ.
@Suite("Bare kicker (Android BareMomentPageTest.kt)")
struct BareKickerTests {
    private func calendar(_ zone: String) throws -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try #require(TimeZone(identifier: zone))
        return calendar
    }

    private let sunday = ISO8601Flexible.parse("2026-10-11T07:40:00Z")

    @Test("the kicker is the full day and the time, in the zone")
    func kicker() throws {
        let date = try #require(sunday)
        let paris = try calendar("Europe/Paris")
        let english = CardDateline.kicker(date, calendar: paris, locale: Locale(identifier: "en_GB"))
        #expect(english.day == "Sunday 11 October")
        #expect(english.time == CardDateline.time(date, calendar: paris, locale: Locale(identifier: "en_GB")))
        let french = CardDateline.kicker(date, calendar: paris, locale: Locale(identifier: "fr_FR"))
        #expect(french.day == "dimanche 11 octobre")
        #expect(french.time.contains("9:40"))
        let newYork = CardDateline.kicker(date, calendar: try calendar("America/New_York"),
                                          locale: Locale(identifier: "fr_FR"))
        #expect(newYork.time.contains("3:40"))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run the **Suite command** with `PrintStartTests`. Expected: build FAIL with "cannot find 'PrintStart' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/Logic/PrintStart.swift`:

```swift
import CoreGraphics
import Foundation

/// How a completion's print starts (#1146 §The print, Android's
/// `CardCompletionState.play`): the print happens in the place, so it plays
/// only with the whole place on screen. A place on screen is printed into
/// where it stands; one cut by the view's edge or off it is scrolled into view
/// first; one taller than the view, or one not measured yet, crossfades, as
/// Reduce Motion does.
enum PrintStart: Equatable {
    case print, scrollThenPrint, crossfade

    static func decide(place: CGRect?, viewport: CGRect?, reduceMotion: Bool) -> PrintStart {
        guard !reduceMotion, let place, let viewport else { return .crossfade }
        if place.height > viewport.height + 1 { return .crossfade }
        let whole = place.minY >= viewport.minY - 1 && place.maxY <= viewport.maxY + 1
        return whole ? .print : .scrollThenPrint
    }

    /// D7: a relaunch that finds `printingFor` set for this moment lands the
    /// card at rest, verso up, rather than replaying the print.
    static func cutsShort(printingFor: UUID?, momentId: UUID, hasPebble: Bool) -> Bool {
        hasPebble && printingFor == momentId
    }
}
```

In `apps/ios/Pebbles/Features/Path/Card/Logic/CardDateline.swift`, before `    /// The locale's short time.`, add:

```swift
    /// The bare page's kicker (#1146 §Bare page): the full weekday, the day
    /// and the month ("Sunday 11 October", "dimanche 11 octobre"), and the
    /// short time, set in small caps by the view with " · " between.
    static func kicker(_ date: Date, calendar: Calendar, locale: Locale) -> (day: String, time: String) {
        var day = Date.FormatStyle.dateTime.weekday(.wide).day().month(.wide)
        day.calendar = calendar
        day.timeZone = calendar.timeZone
        day.locale = locale
        return (date.formatted(day), time(date, calendar: calendar, locale: locale))
    }

```

- [ ] **Step 4: Run them to verify they pass**

Run the **Suite command** with `PrintStartTests`, then `BareKickerTests`. Expected: PASS (6), PASS (1).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/Logic/PrintStart.swift apps/ios/Pebbles/Features/Path/Card/Logic/CardDateline.swift apps/ios/PebblesTests/Card/PrintStartTests.swift apps/ios/PebblesTests/Card/BareKickerTests.swift
git commit -m "feat(ios): how a print starts, and the bare page's kicker"
```

---

### Task E3.3: The completion is state

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Card/HeldCardModel.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Card/HeldCardHostModel.swift`
- Modify: `apps/ios/PebblesTests/Card/HeldCardModelTests.swift`
- Test: `apps/ios/PebblesTests/Card/PrintRestoreTests.swift`

- [ ] **Step 1: Write the failing tests**

At the end of the `HeldCardModelTests` struct (before its closing brace), add:

```swift
    // MARK: - Completion (D6)

    @Test("a reload finding a pebble on a bare moment publishes the pebble and the completion together")
    func completion() async throws {
        let model = model()
        let bare = try detail(MomentFixtures.photoOnlyMoment)
        let completed = try detail()
        await model.load { bare }
        #expect(model.completion == nil)
        await model.load { completed }
        #expect(model.detail?.pebble != nil)
        #expect(model.completion == HeldCardModel.Completion(pebbleId: try #require(completed.pebble?.id)))
    }

    @Test("the completion holds through a later reload until it is shown")
    func holds() async throws {
        let model = model()
        let bare = try detail(MomentFixtures.photoOnlyMoment)
        let completed = try detail()
        await model.load { bare }
        await model.load { completed }
        await model.load { completed }
        #expect(model.completion != nil)
        model.printEnded()
        #expect(model.completion == nil && model.printedOver && model.announcing)
        await model.load { completed }
        #expect(model.completion == nil)
        model.announced()
        #expect(!model.announcing)
    }

    @Test("the completion clears when the pebble disappears")
    func clears() async throws {
        let model = model()
        let bare = try detail(MomentFixtures.photoOnlyMoment)
        await model.load { bare }
        await model.load { try detail() }
        await model.load { bare }
        #expect(model.completion == nil)
    }

    @Test("a completed moment opened as such never prints")
    func noPrintOnOpen() async throws {
        let model = model()
        await model.load { try detail() }
        #expect(model.completion == nil)
    }
```

Create `apps/ios/PebblesTests/Card/PrintRestoreTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1146 D6–D7: `printingFor` is set when a print starts and cleared only by the
/// frame where it ends; a restore that still finds it cuts the print short.
@Suite("Print restore")
@MainActor
struct PrintRestoreTests {
    private let id = UUID(uuidString: "1c8d4a6f-3e52-4b9f-a07c-2d3e4f5a6b7c") ?? UUID()

    @Test("a print under way is saved with the card, verso up")
    func saved() {
        let host = HeldCardHostModel()
        host.open(HeldCardRequest(momentId: id, origin: .path))
        host.startPrinting(id)
        #expect(host.printingFor == id)
        #expect(host.snapshot == HeldCardSnapshot(momentId: id, face: .verso, printingFor: id))
    }

    @Test("only the print's end clears it; a landing does not")
    func onlyTheEnd() {
        let host = HeldCardHostModel()
        host.open(HeldCardRequest(momentId: id, origin: .path))
        host.startPrinting(id)
        host.land(.recto)
        #expect(host.printingFor == id)
        host.endPrinting()
        #expect(host.printingFor == nil)
    }

    @Test("a restore with a print under way is cut short on load")
    func restore() {
        let host = HeldCardHostModel()
        host.restore(HeldCardSnapshot(momentId: id, face: .verso, printingFor: id))
        #expect(host.request?.face == .verso)
        #expect(PrintStart.cutsShort(printingFor: host.printingFor, momentId: id, hasPebble: true))
    }

    @Test("cutting short shows the completion and leaves the card verso up")
    func cutShort() async throws {
        let model = HeldCardModel(momentId: id, origin: .you, onChanged: {})
        let bare = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.photoOnlyMoment)
        let completed = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        await model.load { bare }
        await model.load { completed }
        model.cutShort()
        #expect(model.completion == nil && model.printedOver && !model.announcing)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run the **Suite command** with `HeldCardModelTests`. Expected: build FAIL with "type 'HeldCardModel' has no member 'Completion'".

- [ ] **Step 3: The model publishes the completion with the moment**

In `apps/ios/Pebbles/Features/Path/Card/HeldCardModel.swift`:

1. Replace

```swift
    enum Phase: Equatable { case loading, failed, loaded }
    enum DeleteOutcome: Equatable { case closes, reloads, failed, ignored }

    private(set) var phase: Phase = .loading
    private(set) var detail: MomentDetail?
```

with

```swift
    enum Phase: Equatable { case loading, failed, loaded }
    enum DeleteOutcome: Equatable { case closes, reloads, failed, ignored }

    /// D6: a pebble appeared on a moment that was bare.
    struct Completion: Equatable {
        let pebbleId: UUID
    }

    /// The moment and its completion as one value, so a refresh publishes the
    /// swap and the cue in one assignment (Android's lesson: an event raced the swap).
    struct Loaded: Equatable {
        let detail: MomentDetail
        let completion: Completion?
    }

    private(set) var phase: Phase = .loading
    private(set) var loaded: Loaded?
    /// Keeps the card verso up after a print, until the next request.
    private(set) var printedOver = false
    /// The verso heading is announced once after a print, then this goes false.
    private(set) var announcing = false

    var detail: MomentDetail? { loaded?.detail }
    var completion: Completion? { loaded?.completion }
```

2. Replace

```swift
    private func apply(_ fresh: MomentDetail) {
        detail = fresh
    }
```

with

```swift
    /// A pebble on a moment that was bare is a completion; it survives later
    /// refreshes until shown, and clears if the pebble disappears.
    private func apply(_ fresh: MomentDetail) {
        let wasBare = loaded.map { $0.detail.pebble == nil } ?? false
        var completion = loaded?.completion
        if let pebble = fresh.pebble {
            if wasBare { completion = Completion(pebbleId: pebble.id) }
        } else {
            completion = nil
        }
        loaded = Loaded(detail: fresh, completion: completion)
    }

    /// The frame where the print reached its end (D6): the card is at rest, verso up.
    func printEnded() {
        completionShown()
        announcing = true
    }

    /// A restore found a print under way: land at rest, verso up, without it.
    func cutShort() {
        completionShown()
    }

    func announced() {
        announcing = false
    }

    private func completionShown() {
        if let loaded, loaded.completion != nil {
            self.loaded = Loaded(detail: loaded.detail, completion: nil)
        }
        printedOver = true
    }
```

`MomentDetail` is `Hashable`, so `Loaded` is `Equatable`.

- [ ] **Step 4: The host keeps the print**

In `apps/ios/Pebbles/Features/Path/Card/HeldCardHostModel.swift`, before `    /// A relaunch after a background kill:`, add:

```swift
    /// The print under way, mirrored to the scene (D6–D7).
    var printingFor: UUID? { snapshot?.printingFor }

    /// A print starts: saved with the card, verso up, so a restore lands there.
    func startPrinting(_ momentId: UUID) {
        guard var current = snapshot else { return }
        current.printingFor = momentId
        current.face = .verso
        snapshot = current
    }

    /// Only the frame where the print ends calls this (D6).
    func endPrinting() {
        guard var current = snapshot, current.printingFor != nil else { return }
        current.printingFor = nil
        snapshot = current
    }

```

- [ ] **Step 5: Run the tests to verify they pass**

Run the **Suite command** with `HeldCardModelTests`, then `PrintRestoreTests`, then `HeldCardHostModelTests`. Expected: PASS (10), PASS (4), PASS (6).

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/HeldCardModel.swift apps/ios/Pebbles/Features/Path/Card/HeldCardHostModel.swift apps/ios/PebblesTests/Card/HeldCardModelTests.swift apps/ios/PebblesTests/Card/PrintRestoreTests.swift
git commit -m "feat(ios): a completion is state, published with the pebble it brought"
```

---

### Task E3.4: `BareMomentPage`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/BareMomentPage.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI
import os

/// A moment with no pebble has no card (#1146 §Bare page): the plain opening
/// (the kicker in small caps, the title, the photo on a stock mat, the words),
/// then the place its card will print into, a dashed card outline held by four
/// album corners, a dashed stone, "Not a pebble yet", one sentence, and **Make
/// it a pebble**, the page's one call to action. The place reports itself and
/// its dashed stone as `PrintAnchors` for the print, and its frame for the
/// scroll that shows it whole.
///
/// VoiceOver: the kicker, the title (a heading), the photo, the words, the
/// place's kicker and sentence, then the button.
struct BareMomentPage: View {
    let detail: MomentDetail
    let ratio: CGFloat?
    let onComplete: () -> Void
    var isCompleteDisabled = false
    /// The place's global frame, for `PrintStart`.
    var onPlaceFrame: (CGRect) -> Void = { _ in }

    static let placeID = "bare-place"

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        VStack(spacing: 0) {
            kicker
            Text(verbatim: detail.name.flatMap { $0.isEmpty ? nil : $0 } ?? SpineCopy.untitled(locale: locale))
                .spineType(.bareTitle)
                .foregroundStyle(Color.pebbles.onSurface)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 10)
                .accessibilityAddTraits(.isHeader)
            if let path = detail.snaps.first?.storagePath {
                BarePhoto(path: path, ratio: ratio ?? CardSizes.pendingRatio)
                    .padding(.top, 20)
            }
            if let words = detail.description, !words.isEmpty {
                Text(verbatim: words)
                    .spineType(.cardLeafBody)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 16)
            }
            BarePlace(onComplete: onComplete, isDisabled: isCompleteDisabled)
                .id(Self.placeID)
                .anchorPreference(key: PrintAnchors.self, value: .bounds) { [.place: $0] }
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { onPlaceFrame($0) }
                .padding(.top, 28)
        }
        .padding(.horizontal, CardLayout.pageMargin)
        .frame(maxWidth: CardLayout.maxWidth + 2 * CardLayout.pageMargin)
        .frame(maxWidth: .infinity)
    }

    private var kicker: some View {
        let parts = CardDateline.kicker(detail.happenedAt, calendar: SpineCalendar.current, locale: locale)
        return (Text(verbatim: parts.day)
            + Text(verbatim: " · ").foregroundStyle(Color.pebbles.outline)
            + Text(verbatim: parts.time).foregroundStyle(Color.pebbles.onSurface))
            .spineType(.kicker)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .multilineTextAlignment(.center)
            // A pause rather than the middle dot read out.
            .accessibilityLabel(Text(verbatim: "\(parts.day), \(parts.time)"))
    }
}

/// The photo on a stock mat, whole, at its ratio, no taller than the Spine's
/// tallest card less its chrome, so a portrait narrows rather than pushing the
/// place below the fold.
private struct BarePhoto: View {
    let path: String
    let ratio: CGFloat

    @Environment(\.spineSnapSource) private var snaps
    @State private var url: URL?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "held-card")

    var body: some View {
        let tallest = CardSizes.cap(3) - CardSizes.chromeVertical
        photo
            .aspectRatio(ratio, contentMode: .fit)
            .frame(maxWidth: tallest * ratio)
            .padding(10)
            .background(Color.pebbles.stock)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("Photo"))
            .accessibilityAddTraits(.isImage)
            .task(id: path) {
                guard snaps.image(path) == nil else { return }
                url = await snaps.url(path)
            }
    }

    @ViewBuilder
    private var photo: some View {
        if let image = snaps.image(path) {
            Image(uiImage: image).resizable().scaledToFit()
        } else if let url {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFit()
                case .failure(let error):
                    bed.onAppear {
                        logger.warning("bare page photo failed: \(error.localizedDescription, privacy: .private)")
                    }
                default:
                    bed
                }
            }
        } else {
            bed
        }
    }

    private var bed: some View {
        Rectangle().fill(Color.pebbles.halo)
    }
}

/// "Not a pebble yet": the place kept for the card, at least the card's
/// 1 : 1.12 and taller when its words need it (a fixed ratio would clip at a
/// large type size), and the one call to action.
private struct BarePlace: View {
    let onComplete: () -> Void
    let isDisabled: Bool

    var body: some View {
        MinimumRatio(ratio: 1.12) {
            VStack(spacing: 0) {
                GhostStone()
                    .anchorPreference(key: PrintAnchors.self, value: .bounds) { [.ghost: $0] }
                Text(CardStrings.placeKicker)
                    .spineType(.sectionLabel)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .padding(.top, 16)
                    .accessibilityAddTraits(.isHeader)
                Text(CardStrings.placeBody)
                    .spineType(.placeBody)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 8)
                    .padding(.horizontal, 24)
                Button(action: onComplete) {
                    Label("Make it a pebble", systemImage: "plus")
                }
                .buttonStyle(PebblesPrimaryButtonStyle())
                .disabled(isDisabled)
                .padding(.top, 22)
            }
            .padding(.vertical, 28)
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity)
        }
        .background { PlaceOutline() }
    }
}

/// The dashed card outline and its four album corners, clipped to the
/// outline's 16 pt rounding.
private struct PlaceOutline: View {
    var body: some View {
        Canvas { context, size in
            let rect = CGRect(origin: .zero, size: size)
            let rounded = RoundedRectangle(cornerRadius: 16, style: .continuous).path(in: rect)
            var corners = context
            corners.clip(to: rounded)
            let leg: CGFloat = 24
            let w = size.width, h = size.height
            for triangle in [[CGPoint(x: 0, y: 0), CGPoint(x: leg, y: 0), CGPoint(x: 0, y: leg)],
                             [CGPoint(x: w, y: 0), CGPoint(x: w, y: leg), CGPoint(x: w - leg, y: 0)],
                             [CGPoint(x: 0, y: h), CGPoint(x: 0, y: h - leg), CGPoint(x: leg, y: h)],
                             [CGPoint(x: w, y: h), CGPoint(x: w - leg, y: h), CGPoint(x: w, y: h - leg)]] {
                var path = Path()
                path.addLines(triangle)
                path.closeSubpath()
                corners.fill(path, with: .color(Color.pebbles.rule))
            }
            let inset = RoundedRectangle(cornerRadius: 15.5, style: .continuous).path(in: rect.insetBy(dx: 0.5, dy: 0.5))
            context.stroke(inset, with: .color(Color.pebbles.outline), style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
        }
        .accessibilityHidden(true)
    }
}

/// The stone its card will carry, as a dashed outline: a medium neutral pebble,
/// whatever comes. The print flies the same outline to the verso's clearing.
struct GhostStone: View {
    var width: CGFloat = 64

    var body: some View {
        let stone = CardLayout.versoStoneSize(.medium)
        Canvas { context, size in
            let style = StrokeStyle(lineWidth: 1.25, dash: [4, 4])
            guard let outline = StoneRingGeometry.forStone(.neutralMedium)?.outline, !outline.boundingRect.isEmpty else {
                context.stroke(Path(ellipseIn: CGRect(origin: .zero, size: size).insetBy(dx: 1, dy: 1)),
                               with: .color(Color.pebbles.outline), style: style)
                return
            }
            let box = outline.boundingRect
            let scale = min((size.width - 2) / box.width, (size.height - 2) / box.height)
            let fitted = outline.applying(CGAffineTransform(translationX: -box.minX, y: -box.minY)
                .concatenating(CGAffineTransform(scaleX: scale, y: scale))
                .concatenating(CGAffineTransform(translationX: 1, y: 1)))
            context.stroke(fitted, with: .color(Color.pebbles.outline), style: style)
        }
        .frame(width: width, height: width * stone.height / stone.width)
        .accessibilityHidden(true)
    }
}

/// At least `width / ratio` tall, and taller when the content needs it.
private struct MinimumRatio: Layout {
    let ratio: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let child = subviews.first else { return .zero }
        let width = proposal.width ?? child.sizeThatFits(.unspecified).width
        let natural = child.sizeThatFits(ProposedViewSize(width: width, height: nil))
        return CGSize(width: width, height: max(natural.height, width / ratio))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        subviews.first?.place(at: CGPoint(x: bounds.midX, y: bounds.midY), anchor: .center,
                              proposal: ProposedViewSize(width: bounds.width, height: nil))
    }
}
```

If `PebblesPrimaryButtonStyle` was renamed by stack A on `main` (`grep -rn "struct PebblesPrimaryButtonStyle" apps/ios/Pebbles`), use `main`'s name. If `Color.pebbles.halo` is not among the generated roles (`grep -n "halo" apps/ios/Shared/Theme/PebblesColors.swift`), use `Color.pebbles.surfaceContainer`.

- [ ] **Step 2: Build and commit**

Run **Build** and the **Suite command** with `ThemeLiteralsTests`. Expected: `** BUILD SUCCEEDED **`, PASS.

```bash
git add apps/ios/Pebbles/Features/Path/Card/BareMomentPage.swift
git commit -m "feat(ios): the bare moment's page and the place its card will print into"
```

---

### Task E3.5: `CardPrint`, the print as a function of time

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Card/CardPrint.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Card/PebbleCard.swift`

- [ ] **Step 1: The card takes the print and the stone's press**

In `PebbleCard.swift`:

1. Replace

```swift
    let soulOrder: [String]
    let turn: CardTurnModel

    @Environment(\.colorScheme) private var scheme
```

with

```swift
    let soulOrder: [String]
    let turn: CardTurnModel
    /// The print's timeline while the card prints into its place (E3); nil at rest.
    var print: Double?
    /// The verso stone's press, which the print plays at 0.45 (E3).
    var stonePress: StonePress?

    @Environment(\.colorScheme) private var scheme
```

2. Replace

```swift
                    .contentShape(Rectangle())
                    .simultaneousGesture(turnDrag)
```

with

```swift
                    .contentShape(Rectangle())
                    .simultaneousGesture(turnDrag)
                    // No turning while the card prints.
                    .allowsHitTesting(print == nil)
```

(After E2.8's fallback, put `.allowsHitTesting(print == nil)` after the `.overlay { TurnPanFallback … }` instead.)

3. Replace

```swift
                           stone: SpineColors.spot(stone, scheme: scheme), onTurn: turnByButton)
                    .frame(width: width)
```

with

```swift
                           stone: SpineColors.spot(stone, scheme: scheme), onTurn: turnByButton)
                    .frame(width: width)
                    .opacity(print.map { PrintPart.footer.alpha(CompletionMotion.print($0)) } ?? 1)
```

4. Replace

```swift
                CardVerso(pebble: pebble, summary: MomentSummary(detail: detail), palette: palette, width: width,
                          domains: domains, collectionGlyphs: collectionGlyphs)
```

with

```swift
                CardVerso(pebble: pebble, summary: MomentSummary(detail: detail), palette: palette, width: width,
                          domains: domains, collectionGlyphs: collectionGlyphs, print: print, stonePress: stonePress)
```

- [ ] **Step 2: The print scene**

Append to `apps/ios/Pebbles/Features/Path/Card/CardPrint.swift`:

```swift
/// The completion (#1146 §The print, D6): over the bare page, the card appears
/// verso up at the place, fed out of it top first (0–0.35); the dashed stone
/// flies to the verso's clearing and the lit stone presses in (0.25–0.6, the
/// press at 0.45); the lockup, the postmarks, the band and the footer print in
/// turn; then the page fades and steps back to 0.97 while the card rises to its
/// rest at the page's top (0.6–1). A pure function of `t`: the caller's
/// `TimelineView` drives it, and the frame that reaches 1 ends it. The place,
/// its dashed stone and the verso's stone are read as anchors in the same pass.
/// Under a crossfade the page fades out and the card in at rest.
///
/// The place is as wide as the card (both `CardLayout.cardWidth`), so the card
/// prints at its own width.
struct CardPrint<Card: View>: View {
    let t: Double
    let crossfade: Bool
    let page: BareMomentPage
    let card: Card

    var body: some View {
        let rise = CompletionMotion.rise(t)
        page
            .opacity(crossfade ? 1 - t : CompletionMotion.pageAlpha(rise))
            .scaleEffect(crossfade ? 1 : 1 - (1 - CompletionMotion.pageScaleEnd) * CompletionMotion.ease(rise),
                         anchor: .top)
            // Nothing on the page is pressed or read under the card.
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            .overlayPreferenceValue(PrintAnchors.self) { anchors in
                GeometryReader { proxy in
                    let place = anchors[.place].map { proxy[$0] }
                    let ghost = anchors[.ghost].map { proxy[$0] }
                    printed(place: place, ghost: ghost, width: proxy.size.width)
                }
            }
    }

    @ViewBuilder
    private func printed(place: CGRect?, ghost: CGRect?, width: CGFloat) -> some View {
        let print = crossfade ? 1 : CompletionMotion.print(t)
        let rise = crossfade ? 1 : CompletionMotion.rise(t)
        // From the place's top to the card's rest at the page's top.
        let top = (place?.minY ?? 0) * (1 - CompletionMotion.ease(rise))
        card
            .overlayPreferenceValue(PrintAnchors.self) { anchors in
                GeometryReader { proxy in
                    if !crossfade, let ghost, let stone = anchors[.versoStone].map({ proxy[$0] }) {
                        // Into the card's own space: its leading edge is the place's (the card
                        // is as wide as the place, both centred), its top `top` down the page.
                        flyingGhost(from: ghost.offsetBy(dx: -(place?.minX ?? 0), dy: -top), to: stone)
                    }
                }
            }
            .mask(alignment: .top) {
                GeometryReader { proxy in
                    let revealed = print >= 1 ? proxy.size.height
                        : max(place?.height ?? 0,
                              (place?.height ?? 0) + (proxy.size.height - (place?.height ?? 0)) * CompletionMotion.ease(print))
                    // 32 pt round the card for its shadow and painted edge.
                    Rectangle()
                        .frame(width: proxy.size.width + 64, height: revealed + 32 + 32 * CompletionMotion.ease(print))
                        .offset(x: -32, y: -32)
                }
            }
            .opacity(crossfade ? t : PrintPart.card.alpha(print))
            .frame(maxWidth: .infinity)
            .offset(y: top)
    }

    /// The dashed stone in flight from the page's ghost to the verso's clearing,
    /// both in the card's own space.
    @ViewBuilder
    private func flyingGhost(from: CGRect, to: CGRect) -> some View {
        let stone = CompletionMotion.stone(t)
        if stone > 0, stone < 1, CompletionMotion.ghostAlpha(stone) > 0 {
            let f = CompletionMotion.ease(CompletionMotion.flight(stone))
            let width = from.width + (to.width - from.width) * f
            let centre = CGPoint(x: from.midX + (to.midX - from.midX) * f, y: from.midY + (to.midY - from.midY) * f)
            GhostStone(width: width)
                .opacity(CompletionMotion.ghostAlpha(stone))
                .position(centre)
        }
    }
}
```


- [ ] **Step 3: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Card/CardPrint.swift apps/ios/Pebbles/Features/Path/Card/PebbleCard.swift
git commit -m "feat(ios): the card prints into its place as a function of time"
```

---

### Task E3.6: The page prints: the bare page, the start, the end, the restore and the announcement

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Card/HeldCardPage.swift`

- [ ] **Step 1: The print's state**

In `HeldCardPage.swift`, replace

```swift
    @State private var pendingDeletion: MomentDeleteTarget?
    @State private var deleteError: String?
```

with

```swift
    @State private var pendingDeletion: MomentDeleteTarget?
    @State private var deleteError: String?
    /// When the print started; nil when none runs (D6). Cleared only by its last frame.
    @State private var printStart: Date?
    @State private var printCrossfades = false
    @State private var placeFrame: CGRect?
    @State private var viewport: CGRect?
    /// The verso stone's press, played at 0.45 and kept for its own length.
    @State private var versoPress: StonePress?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
```

- [ ] **Step 2: The content**

Replace

```swift
        case .loaded:
            if let detail = model.detail {
                if let pebble = detail.pebble {
                    card(detail, pebble)
                } else {
                    // Until E3's bare page.
                    PebbleReadView(detail: detail, palette: nil, onComplete: { isPresentingComplete = true })
                }
            }
        }
    }

    private func card(_ detail: MomentDetail, _ pebble: MomentDetail.Pebble) -> some View {
        ScrollView {
            PebbleCard(detail: detail, pebble: pebble, palette: palettes.palette(for: pebble.emotion.id),
                       ratio: detail.snaps.first.flatMap { aspects.ratio(for: $0.storagePath) },
                       width: CardLayout.cardWidth(available: pageWidth), domains: refs.domains,
                       collectionGlyphs: collectionGlyphs, soulGlyphs: soulGlyphs, soulOrder: request.soulOrder,
                       turn: turn)
                .frame(maxWidth: .infinity)
                .padding(.top, 8)
                .padding(.bottom, 32)
        }
    }
```

with

```swift
        case .loaded:
            if let detail = model.detail {
                ScrollViewReader { proxy in
                    ScrollView {
                        page(detail)
                            .padding(.top, 8)
                            .padding(.bottom, 32)
                    }
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { viewport = $0 }
                    .task(id: printReady) {
                        guard printReady else { return }
                        await beginPrint(proxy)
                    }
                }
            }
        }
    }

    /// The bare page until a completion has printed; the print while it runs;
    /// the card at rest otherwise.
    @ViewBuilder
    private func page(_ detail: MomentDetail) -> some View {
        if let pebble = detail.pebble, let printStart {
            TimelineView(.animation) { context in
                let duration = printCrossfades ? CompletionMotion.crossfade : CompletionMotion.duration
                let t = CompletionMotion.timeline(elapsed: context.date.timeIntervalSince(printStart), duration: duration)
                CardPrint(t: t, crossfade: printCrossfades, page: bare(detail), card: card(detail, pebble, print: t))
                    .onChange(of: t >= CompletionMotion.pressAt) { _, pressed in
                        if pressed, !printCrossfades { versoPress = .next(after: nil, still: false, now: .now) }
                    }
                    // The frame that reaches the end ends it (D6).
                    .onChange(of: t >= 1) { _, done in
                        if done { endPrint() }
                    }
            }
        } else if let pebble = detail.pebble, model.completion == nil {
            card(detail, pebble, print: nil)
        } else {
            bare(detail)
        }
    }

    private func bare(_ detail: MomentDetail) -> BareMomentPage {
        BareMomentPage(detail: detail, ratio: detail.snaps.first.flatMap { aspects.ratio(for: $0.storagePath) },
                       onComplete: { isPresentingComplete = true },
                       isCompleteDisabled: model.completion != nil || printStart != nil,
                       onPlaceFrame: { placeFrame = $0 })
    }

    private func card(_ detail: MomentDetail, _ pebble: MomentDetail.Pebble, print: Double?) -> some View {
        PebbleCard(detail: detail, pebble: pebble, palette: palettes.palette(for: pebble.emotion.id),
                   ratio: detail.snaps.first.flatMap { aspects.ratio(for: $0.storagePath) },
                   width: CardLayout.cardWidth(available: pageWidth), domains: refs.domains,
                   collectionGlyphs: collectionGlyphs, soulGlyphs: soulGlyphs, soulOrder: request.soulOrder,
                   turn: turn, print: print, stonePress: versoPress)
            .frame(maxWidth: .infinity)
            .stonePressLifetime($versoPress)
    }

    // MARK: - The print (D6)

    /// A completion waits for the Complete flow's cover to go, so the print is
    /// seen; then it starts once.
    private var printReady: Bool {
        model.completion != nil && !isPresentingComplete && printStart == nil
    }

    private func beginPrint(_ proxy: ScrollViewProxy) async {
        let start = PrintStart.decide(place: placeFrame, viewport: viewport, reduceMotion: reduceMotion)
        if start == .scrollThenPrint {
            withAnimation(.easeInOut(duration: 0.3)) { proxy.scrollTo(BareMomentPage.placeID, anchor: .bottom) }
            try? await Task.sleep(for: .milliseconds(350))
            // The page left while it scrolled: nothing started, nothing to clear.
            guard !Task.isCancelled else { return }
        }
        turn = CardTurnModel(face: .verso)
        printCrossfades = start == .crossfade
        host.startPrinting(request.momentId)
        printStart = .now
    }

    /// The last frame: the card at rest, verso up; announced once.
    private func endPrint() {
        printStart = nil
        host.endPrinting()
        model.printEnded()
    }

    /// D7: a relaunch mid-print lands the card at rest, verso up.
    private func cutShortIfRestored() {
        guard let detail = model.detail,
              PrintStart.cutsShort(printingFor: host.printingFor, momentId: request.momentId,
                                   hasPebble: detail.pebble != nil) else { return }
        turn = CardTurnModel(face: .verso)
        model.cutShort()
        host.endPrinting()
    }
```

- [ ] **Step 3: The first load, the announcement and the bar**

1. Replace `        .task { await reload() }` with:

```swift
        .task {
            await reload()
            cutShortIfRestored()
        }
        .task(id: model.announcing) {
            guard model.announcing else { return }
            AccessibilityNotification.Announcement(String(localized: CardStrings.versoHeading)).post()
            try? await Task.sleep(for: .seconds(1))
            model.announced()
        }
```

2. Replace

```swift
            CardTopBar(shareURL: shareURL, menuItems: menuItems,
                       isDisabled: model.detail == nil || model.isDeleting, onBack: onClose)
```

with

```swift
            CardTopBar(shareURL: shareURL, menuItems: menuItems,
                       isDisabled: model.detail == nil || model.isDeleting || printStart != nil, onBack: onClose)
```

3. The completion's `onCompleted` already calls `saved()`, which reloads in place behind the success screen and bumps `MomentsRevision`; the reload publishes the pebble and its completion together (E3.3), and `printReady` turns true once the cover is gone.

- [ ] **Step 4: Build, suite, lint**

Run **Build**, the **Full suite** and **Lint**. Expected: green. `grep -n "PebbleReadView" apps/ios/Pebbles/Features/Path/Card/HeldCardPage.swift` prints nothing.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/HeldCardPage.swift
git commit -m "feat(ios): a completed moment prints its card into the page"
```

---

### Task E3.7: The bare page and print references

**Files:**
- Modify: `apps/ios/PebblesTests/Card/HeldCardFixtures.swift`
- Create: `apps/ios/PebblesTests/Card/HeldCardPrintStill.swift`
- Create: `apps/ios/PebblesTests/References/HeldCardBareReferences.swift`

- [ ] **Step 1: The bare fixtures**

In `HeldCardFixtures.swift`, before the final closing brace of `enum HeldCardFixtures`, add:

```swift
    /// F4: a bare moment with a photo and words.
    static let f4 = decode("""
    {
      "id": "1c8d4a6f-3e52-4b9f-a07c-2d3e4f5a6b01", "user_id": "5f0e1d2c-3b4a-4958-8776-655443322110",
      "name": "Sunday market", "description": "Elio wanted the yellow tomatoes.",
      "happened_at": "2026-10-11T07:40:00+00:00", "hidden_at": null,
      "created_at": "2026-10-11T07:41:00+00:00", "updated_at": "2026-10-11T07:41:00+00:00",
      "snaps": [{"id": "b2c3d4e5-f6a7-4890-9bcd-ef0123456701", "storage_path": "\(fountain)", "sort_order": 0}],
      "pebble": null
    }
    """)

    /// F4 without a photo.
    static let f4NoPhoto = decode("""
    {
      "id": "1c8d4a6f-3e52-4b9f-a07c-2d3e4f5a6b02", "user_id": "5f0e1d2c-3b4a-4958-8776-655443322110",
      "name": "Sunday market", "description": "Elio wanted the yellow tomatoes.",
      "happened_at": "2026-10-11T07:40:00+00:00", "hidden_at": null,
      "created_at": "2026-10-11T07:41:00+00:00", "updated_at": "2026-10-11T07:41:00+00:00",
      "snaps": [], "pebble": null
    }
    """)
```

- [ ] **Step 2: The print's still**

Create `apps/ios/PebblesTests/Card/HeldCardPrintStill.swift`:

```swift
import SwiftUI
@testable import Pebbles

/// The bare page alone, or the print frozen at `t`, at 393 pt without a
/// `ScrollView`: the page is F4's opening and place; the card F1's, verso up.
struct HeldCardPrintStill: View {
    var bare: MomentDetail = HeldCardFixtures.f4
    var t: Double?

    var body: some View {
        let width = CardLayout.cardWidth(available: ReferenceConfig.phoneWidth)
        let page = BareMomentPage(detail: bare, ratio: bare.snaps.first.flatMap { HeldCardFixtures.ratio($0.storagePath) },
                                  onComplete: {})
        Group {
            if let t, let pebble = HeldCardFixtures.f1.pebble {
                CardPrint(t: t, crossfade: false, page: page,
                          card: PebbleCard(detail: HeldCardFixtures.f1, pebble: pebble, palette: HeldCardFixtures.joy,
                                           ratio: HeldCardFixtures.ratio(HeldCardFixtures.fountain), width: width,
                                           domains: HeldCardFixtures.domains,
                                           collectionGlyphs: HeldCardFixtures.collectionGlyphs,
                                           soulGlyphs: HeldCardFixtures.soulGlyphs, soulOrder: [],
                                           turn: CardTurnModel(face: .verso), print: t))
            } else {
                page
            }
        }
        .padding(.vertical, 24)
        .frame(width: ReferenceConfig.phoneWidth)
        .background(Color.pebbles.surface)
        .environment(\.spineSnapSource, SpineFixturePhotos.source)
    }
}
```

- [ ] **Step 3: The suite**

Create `apps/ios/PebblesTests/References/HeldCardBareReferences.swift`:

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1146 E3: the bare page with and without a photo (French row, ax2), and
    /// the print at 0.2 (the card feeding out of the place), 0.5 (the stone
    /// pressed, the parts printing) and 0.9 (the card rising, the page stepping
    /// back). Android: BareMomentPageScreenshots, CardCompletionScreenshots.
    @Suite("Held card bare references")
    @MainActor
    struct HeldCardBareReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func bare(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardPrintStill(), named: "bare-photo",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
            ReferenceImage.assertMatches(HeldCardPrintStill(bare: HeldCardFixtures.f4NoPhoto), named: "bare-plain",
                                         config: ReferenceConfig(scheme: scheme))
        }

        @Test
        func bareLargestType() {
            ReferenceImage.assertMatches(HeldCardPrintStill(bare: HeldCardFixtures.f4NoPhoto), named: "bare-plain",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        @Test(arguments: [0.2, 0.5, 0.9])
        func print(t: Double) {
            ReferenceImage.assertMatches(HeldCardPrintStill(t: t), named: "print-\(Int(t * 100))",
                                         config: ReferenceConfig(scheme: .paper))
        }
    }
}
```

- [ ] **Step 4: Look locally, then commit**

Run **Look** at `HeldCardBareReferences`. `bare-photo`: "SUNDAY 11 OCTOBER · 09:40" in small caps, the title large and light, the fountain whole on a stock mat, the words, then the dashed place with album corners, the dashed stone, "NOT A PEBBLE YET", the sentence, **Make it a pebble**. French: "DIMANCHE 11 OCTOBRE", "PAS ENCORE UN GALET", "En faire un galet". `-ax2`: the place grows past 1 : 1.12, nothing clips. `print-20`: the verso's top fed out of the place's top, the rest masked; `print-50`: the dashed stone gone into the clearing, the lit stone in, the lockup and the postmarks part printed, no band yet; `print-90`: the card most of the way up, the page faded and slightly smaller behind it. Clean.

If the print stills show the card at rest with no page (the anchors did not resolve under `ImageRenderer` on this runtime), keep only `bare` and `bareLargestType`, delete `print`, and record it for the device check and the PR body.

```bash
git add apps/ios/PebblesTests/Card/HeldCardFixtures.swift apps/ios/PebblesTests/Card/HeldCardPrintStill.swift apps/ios/PebblesTests/References/HeldCardBareReferences.swift
git commit -m "test(ios): bare page references and the print at three instants"
```

---

### Task E3.8: Device check (the orchestrator runs it with the maintainer)

**Files:** none.

- [ ] **Step 1: Ask, then complete a moment**

This writes to the maintainer's account: ask first, and use a throwaway moment they create for it. On the iPhone 15:

1. Path → the bare moment: the plain page, "Not a pebble yet", **Make it a pebble**; ⋮ holds Edit moment and Delete only.
2. Complete it. On the success screen's close the place scrolls into view if it was cut, then the card prints: it feeds out of the place, the dashed stone flies and the lit stone presses in with its ripple and glint, the parts print in turn, the page steps aside, the card rises to rest, verso up. VoiceOver says "Your pebble" once.
3. Right after, Edit pebble → change the emotion → Save: the card reloads in place; the print does not replay.
4. Reduce Motion on, with a second throwaway moment: a 0.25 s crossfade to the card, verso up.
5. A third, killed mid-print (Home at ~1 s, terminate, relaunch): the card reopens at rest, verso up, no print.
6. Delete the throwaway moments.

Record the results for the PR body.

---

### Task E3.9: E3 verification, PR and CI references

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**. Open `Localizable.xcstrings`: no `New` or `Stale` entry.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <E3-number> --title "feat(ios): the bare moment and the card it prints" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Part of #1146`; key files; D6 (the completion published with the pebble in one value, `Loaded`; the print a function of `TimelineView` time; only its last frame clears `printingFor`); D7's cut short; the print reads its geometry through anchor preferences in one pass (no measured state), which also lets `ImageRenderer` draw it; the kicker in the device's zone (iOS's `MomentDetail` has no zone of its own); the device results of E3.8. No Lab Note, no `F-…` ids.

- [ ] **Step 3: Rebaseline on CI**

Run **Rebaseline on CI**. Compare `bare-photo-paper-en.png` with Android's `BareMomentPageF4Light`.

---
## Part E4 — `feat/1160-ios-card-flight`

PR title: `feat(ios): the held card flies out of its slot and back`. Body opens `Resolves #1160` and `Part of #1146`. D1: the held card becomes a full-screen layer in `RootView` above `PebblesTabView`, opened from the slot's measured rect with a FLIP (`scaleEffect` + `offset`), closed by the committed face. **The spike comes first** (Tasks E4.1–E4.5 build the overlay, the FLIP and the edge swipe; E4.5 runs it on the device and decides). On a no-go, Task E4.F replaces the overlay with Option 4; if that fails too, Task E4.G.

### Task E4.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1160-ios-card-flight
git branch --show-current
```

Expected: `feat/1160-ios-card-flight`.

---

### Task E4.1 (spike, 1/5): `CardFlight` and `FlightGeometry`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/Logic/CardFlight.swift`
- Create: `apps/ios/Pebbles/Features/Path/Card/Logic/FlightGeometry.swift`
- Test: `apps/ios/PebblesTests/Card/CardFlightTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

// Ported from apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/CardFlightTest.kt
// at d53be9cf (its placeholder rule is `opensFlying` here), plus Reduce Motion and the
// missing rect (#1146 §Open and close), and FlightGeometry's FLIP.
@Suite("CardFlight (Android CardFlightTest.kt)")
@MainActor
struct CardFlightTests {
    private let slot = CGRect(x: 52, y: 300, width: 293, height: 330)
    private let rest = CGRect(x: 16, y: 100, width: 361, height: 700)

    private func request(face: CardFace = .recto, photo: Bool = true, pebble: Bool = true,
                         seeded: Bool = true) -> HeldCardRequest {
        let id = UUID()
        let summary = MomentSummary(
            id: id, name: "Fountain", happenedAt: Vectors.at("2026-10-08T19:00:00Z"),
            createdAt: Vectors.at("2026-10-08T19:00:00Z"), firstSnapPath: photo ? "a/b.jpg" : nil,
            pebble: pebble ? MomentSummary.PebbleSummary(id: id, intensity: 2, positiveness: 1, visibility: .private,
                                                         emotion: nil, glyph: nil, souls: []) : nil)
        return HeldCardRequest(momentId: id, face: face, seed: seeded ? summary : nil, origin: .path)
    }

    @Test("a spine card flies only when its moment has a pebble")
    func spineCard() {
        #expect(CardFlight.spineCardFlies(hasPhotoCard: true, hasPebble: true))
        // A bare moment with a photo: a card on the Spine, the bare page in the hand.
        #expect(!CardFlight.spineCardFlies(hasPhotoCard: true, hasPebble: false))
        #expect(!CardFlight.spineCardFlies(hasPhotoCard: false, hasPebble: true))
    }

    @Test("the held card flies while its recto shows, and fades otherwise")
    func heldCard() {
        #expect(CardFlight.heldCardFlies(showing: .recto, printing: false, leaving: false, reduceMotion: false))
        #expect(!CardFlight.heldCardFlies(showing: .verso, printing: false, leaving: false, reduceMotion: false))
        #expect(!CardFlight.heldCardFlies(showing: .recto, printing: true, leaving: false, reduceMotion: false))
        #expect(!CardFlight.heldCardFlies(showing: .recto, printing: false, leaving: true, reduceMotion: false))
        #expect(!CardFlight.heldCardFlies(showing: .recto, printing: false, leaving: false, reduceMotion: true))
    }

    @Test("an open flies only for a seeded recto press on a photo card with a measured slot")
    func opens() {
        #expect(CardFlight.opensFlying(request(), sourceRect: slot, reduceMotion: false))
        #expect(!CardFlight.opensFlying(request(face: .verso), sourceRect: slot, reduceMotion: false))
        #expect(!CardFlight.opensFlying(request(photo: false), sourceRect: slot, reduceMotion: false))
        #expect(!CardFlight.opensFlying(request(pebble: false), sourceRect: slot, reduceMotion: false))
        #expect(!CardFlight.opensFlying(request(seeded: false), sourceRect: slot, reduceMotion: false))
        #expect(!CardFlight.opensFlying(request(), sourceRect: nil, reduceMotion: false))
        #expect(!CardFlight.opensFlying(request(), sourceRect: slot, reduceMotion: true))
    }

    @Test("the FLIP starts on the slot, fill-width and top-leading, and ends at rest")
    func flip() {
        let start = FlightGeometry.transform(from: slot, to: rest, progress: 0)
        #expect(abs(start.scale - 293.0 / 361.0) < 1e-9)
        #expect(start.offset == CGSize(width: 36, height: 200))
        let end = FlightGeometry.transform(from: slot, to: rest, progress: 1)
        #expect(end.scale == 1 && end.offset == .zero)
        let half = FlightGeometry.transform(from: slot, to: rest, progress: 0.5)
        #expect(abs(half.scale - (293.0 / 361.0 + 1) / 2) < 1e-9)
        #expect(half.offset == CGSize(width: 18, height: 100))
    }

    @Test("a rest with no width does not transform")
    func noRest() {
        let none = FlightGeometry.transform(from: slot, to: .zero, progress: 0)
        #expect(none.scale == 1 && none.offset == .zero)
    }
}
```

Save it as `apps/ios/PebblesTests/Card/CardFlightTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CardFlightTests`. Expected: build FAIL with "cannot find 'CardFlight' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Card/Logic/CardFlight.swift`:

```swift
import CoreGraphics

/// When the held card flies between its slot on the Spine and the hand
/// (#1146 §Open and close, #1160 D1), a port of Android's `CardFlight`.
enum CardFlight {
    /// The Spine's card takes part only when the hand will hold a card: a bare
    /// moment with a photo has a card on the Spine but opens as the bare page.
    static func spineCardFlies(hasPhotoCard: Bool, hasPebble: Bool) -> Bool {
        hasPhotoCard && hasPebble
    }

    /// The close flies only while the committed face is the recto; a card turned
    /// to its verso fades. Never while it prints, never once its moment is
    /// deleted (its slot has gone), never under Reduce Motion.
    static func heldCardFlies(showing: CardFace, printing: Bool, leaving: Bool, reduceMotion: Bool) -> Bool {
        showing == .recto && !printing && !leaving && !reduceMotion
    }

    /// An open flies for a seeded recto press on a Spine card whose slot was
    /// measured: a stone press (verso), a bare moment, a soul or collection row
    /// (no seed), a slot scrolled away (no rect) and Reduce Motion fade.
    static func opensFlying(_ request: HeldCardRequest, sourceRect: CGRect?, reduceMotion: Bool) -> Bool {
        guard !reduceMotion, sourceRect != nil, request.face == .recto, let seed = request.seed else { return false }
        return spineCardFlies(hasPhotoCard: seed.firstSnapPath != nil, hasPebble: seed.pebble != nil)
    }
}
```

Create `apps/ios/Pebbles/Features/Path/Card/Logic/FlightGeometry.swift`:

```swift
import CoreGraphics

/// The FLIP (#1160 D1): the card lays out at rest and is drawn from the slot,
/// scaled to the slot's width (fill-width) about its top-leading corner and
/// moved onto the slot's origin, then eased to the identity. Progress 0 is the
/// slot, 1 the rest.
enum FlightGeometry {
    static func transform(from slot: CGRect, to rest: CGRect, progress: Double) -> (scale: CGFloat, offset: CGSize) {
        guard rest.width > 0 else { return (1, .zero) }
        let p = CGFloat(min(max(progress, 0), 1))
        let start = slot.width / rest.width
        return (start + (1 - start) * p,
                CGSize(width: (slot.minX - rest.minX) * (1 - p), height: (slot.minY - rest.minY) * (1 - p)))
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `CardFlightTests`. Expected: PASS (5). The FLIP's numbers were run against this code on 2026-10-21 (macOS `swiftc`).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/Logic/CardFlight.swift apps/ios/Pebbles/Features/Path/Card/Logic/FlightGeometry.swift apps/ios/PebblesTests/Card/CardFlightTests.swift
git commit -m "feat(ios): when the held card flies, and its flip"
```

---

### Task E4.2 (spike, 2/5): the host's flight state

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Card/HeldCardHostModel.swift`
- Test: `apps/ios/PebblesTests/Card/HeldCardFlightStateTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// #1146 §State model: the flight is the host's state. An open decides once
/// whether it flies and keeps the slot's rect; the close re-reads the rect
/// (the Spine may have scrolled) and flies by the committed face; the edge
/// swipe scrubs it; the end returns focus to the slot.
@Suite("HeldCardHostModel flight")
@MainActor
struct HeldCardFlightStateTests {
    private let slot = CGRect(x: 52, y: 300, width: 293, height: 330)
    private let rest = CGRect(x: 16, y: 100, width: 361, height: 700)

    private func photoRequest(face: CardFace = .recto) -> HeldCardRequest {
        let id = UUID()
        let summary = MomentSummary(
            id: id, name: "Fountain", happenedAt: Vectors.at("2026-10-08T19:00:00Z"),
            createdAt: Vectors.at("2026-10-08T19:00:00Z"), firstSnapPath: "a/b.jpg",
            pebble: MomentSummary.PebbleSummary(id: id, intensity: 2, positiveness: 1, visibility: .private,
                                                emotion: nil, glyph: nil, souls: []))
        return HeldCardRequest(momentId: id, face: face, seed: summary, origin: .path)
    }

    @Test("a seeded recto open from a measured slot flies from it")
    func opensFlying() {
        let host = HeldCardHostModel()
        host.open(photoRequest(), sourceRect: slot, reduceMotion: false)
        #expect(host.flies && host.sourceRect == slot)
        #expect(host.phase == .opening && host.flight == 0 && host.chrome == 0 && host.pageOpacity == 1)
    }

    @Test("a stone press, a row without a seed and Reduce Motion fade in")
    func opensFading() {
        for (request, reduce) in [(photoRequest(face: .verso), false),
                                  (HeldCardRequest(momentId: UUID(), origin: .people), false),
                                  (photoRequest(), true)] {
            let host = HeldCardHostModel()
            host.open(request, sourceRect: slot, reduceMotion: reduce)
            #expect(!host.flies && host.sourceRect == nil)
            #expect(host.flight == 1 && host.pageOpacity == 0)
        }
    }

    @Test("the first rest of an open starts the flight; later ones only move the rest")
    func rest() {
        let host = HeldCardHostModel()
        host.open(photoRequest(), sourceRect: slot, reduceMotion: false)
        #expect(host.reportRest(rest))
        #expect(!host.reportRest(rest.offsetBy(dx: 0, dy: -40)))
        #expect(host.restRect == rest.offsetBy(dx: 0, dy: -40))
        host.opened()
        #expect(host.phase == .open)
    }

    @Test("the close flies back on the recto to the re-read rect, and fades otherwise")
    func closes() {
        let moved = slot.offsetBy(dx: 0, dy: 120)
        let host = HeldCardHostModel()
        host.open(photoRequest(), sourceRect: slot, reduceMotion: false)
        _ = host.reportRest(rest)
        #expect(host.beginClose(sourceRect: moved, reduceMotion: false))
        #expect(host.sourceRect == moved)

        host.cancelClose()
        host.faceCommitted(.verso)
        #expect(!host.beginClose(sourceRect: moved, reduceMotion: false))

        host.cancelClose()
        host.faceCommitted(.recto)
        #expect(!host.beginClose(sourceRect: nil, reduceMotion: false))
        host.cancelClose()
        #expect(!host.beginClose(sourceRect: moved, reduceMotion: true))
        host.cancelClose()
        host.markLeaving()
        #expect(!host.beginClose(sourceRect: moved, reduceMotion: false))
    }

    @Test("a print in progress closes without flying")
    func printingFades() {
        let host = HeldCardHostModel()
        let request = photoRequest()
        host.open(request, sourceRect: slot, reduceMotion: false)
        _ = host.reportRest(rest)
        host.startPrinting(request.momentId)
        host.faceCommitted(.recto)
        #expect(!host.beginClose(sourceRect: slot, reduceMotion: false))
    }

    @Test("the edge swipe scrubs a flying close, or slides a fading one")
    func scrub() {
        let host = HeldCardHostModel()
        host.open(photoRequest(), sourceRect: slot, reduceMotion: false)
        _ = host.reportRest(rest)
        _ = host.beginClose(sourceRect: slot, reduceMotion: false)
        host.scrub(0.3, width: 393)
        #expect(host.phase == .closing(progress: 0.3) && abs(host.flight - 0.7) < 1e-9 && host.pageShift == 0)
        host.cancelClose()
        #expect(host.phase == .open && host.flight == 1 && host.chrome == 1)

        host.faceCommitted(.verso)
        _ = host.beginClose(sourceRect: slot, reduceMotion: false)
        host.scrub(0.5, width: 400)
        #expect(host.pageShift == 200 && host.pageOpacity == 0.5 && host.flight == 1)
    }

    @Test("the end of a close forgets the card and returns focus to its slot")
    func finish() {
        let host = HeldCardHostModel()
        let request = photoRequest()
        host.open(request, sourceRect: slot, reduceMotion: false)
        _ = host.beginClose(sourceRect: slot, reduceMotion: false)
        host.finishClose()
        #expect(host.request == nil && host.snapshot == nil && host.phase == .idle)
        #expect(host.returnFocus == request.momentId)
    }

    @Test("a restored card opens at rest, without a flight")
    func restore() {
        let host = HeldCardHostModel()
        host.restore(HeldCardSnapshot(momentId: UUID(), face: .recto))
        #expect(!host.flies && host.phase == .open && host.pageOpacity == 1 && host.chrome == 1)
    }
}
```

Save it as `apps/ios/PebblesTests/Card/HeldCardFlightStateTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `HeldCardFlightStateTests`. Expected: build FAIL with "extra arguments at positions #2, #3 in call" (`open` takes one argument).

- [ ] **Step 3: Implement**

Replace the contents of `apps/ios/Pebbles/Features/Path/Card/HeldCardHostModel.swift` with:

```swift
import CoreGraphics
import Foundation
import Observation

/// The one host of the held card (#1146 D1, D7; #1160), created in `RootView`
/// and injected. Every caller opens the card here; `HeldCardHost`, a
/// full-screen layer above `PebblesTabView`, draws it. Its `snapshot` is
/// mirrored to `@SceneStorage("heldCard")` by `RootView` and cleared by the
/// sign-out reset.
///
/// The flight is state here; the host view animates it. `flight` is 0 at the
/// slot and 1 at rest, `chrome` the ground's and the top bar's opacity,
/// `pageShift` and `pageOpacity` a close that does not fly.
@Observable @MainActor
final class HeldCardHostModel {
    enum FlightPhase: Equatable {
        case idle, opening, open
        /// The edge swipe's scrub, 0 to 1.
        case closing(progress: Double)
    }

    private(set) var request: HeldCardRequest?
    private(set) var snapshot: HeldCardSnapshot?

    private(set) var phase: FlightPhase = .idle
    /// Whether the open, then the close, flies (decided at each).
    private(set) var flies = false
    /// The slot's global rect, read at the press and re-read at the close; nil fades.
    private(set) var sourceRect: CGRect?
    /// The card's resting global rect in the overlay, as the page reports it.
    private(set) var restRect: CGRect?
    var flight: Double = 1
    var chrome: Double = 1
    var pageShift: CGFloat = 0
    var pageOpacity: Double = 1
    /// The slot VoiceOver returns to; the Path hands it to stack D's focus state.
    private(set) var returnFocus: UUID?
    /// The committed face, mirrored by the page: the close flies by it.
    private(set) var committedFace: CardFace = .recto
    /// Set by the page before the close a delete triggers.
    private(set) var leaving = false

    // MARK: - Open

    func open(_ request: HeldCardRequest, sourceRect: CGRect? = nil, reduceMotion: Bool = false) {
        self.request = request
        snapshot = HeldCardSnapshot(momentId: request.momentId, face: request.face)
        flies = CardFlight.opensFlying(request, sourceRect: sourceRect, reduceMotion: reduceMotion)
        self.sourceRect = flies ? sourceRect : nil
        restRect = nil
        committedFace = request.face
        leaving = false
        returnFocus = nil
        phase = .opening
        flight = flies ? 0 : 1
        chrome = 0
        pageShift = 0
        pageOpacity = flies ? 1 : 0
    }

    /// The card's resting rect. True on an open's first report: the host view
    /// starts the flight then, so the first frame is measured, never guessed.
    func reportRest(_ rect: CGRect) -> Bool {
        let first = restRect == nil && phase == .opening
        restRect = rect
        return first
    }

    func opened() {
        if phase == .opening { phase = .open }
    }

    func faceCommitted(_ face: CardFace) {
        committedFace = face
    }

    func markLeaving() {
        leaving = true
    }

    /// The face the card will rest on changed (`CardTurnModel.landing`).
    func land(_ face: CardFace) {
        guard var current = snapshot, current.face != face else { return }
        current.face = face
        snapshot = current
    }

    // MARK: - Close

    /// Begins a close with the slot's rect read now. True when it flies back.
    func beginClose(sourceRect: CGRect?, reduceMotion: Bool) -> Bool {
        let back = CardFlight.heldCardFlies(showing: committedFace, printing: printingFor != nil, leaving: leaving,
                                            reduceMotion: reduceMotion)
        flies = back && sourceRect != nil && restRect != nil
        self.sourceRect = flies ? sourceRect : nil
        phase = .closing(progress: 0)
        return flies
    }

    /// The edge swipe's scrub: the FLIP in reverse when the close flies, else a
    /// trailing slide of the page with a fade.
    func scrub(_ progress: Double, width: CGFloat) {
        let p = min(max(progress, 0), 1)
        phase = .closing(progress: p)
        chrome = 1 - p
        if flies {
            flight = 1 - p
        } else {
            pageShift = CGFloat(p) * width
            pageOpacity = 1 - p
        }
    }

    func cancelClose() {
        phase = .open
        flight = 1
        chrome = 1
        pageShift = 0
        pageOpacity = 1
    }

    /// The close's last frame: the card is gone and focus returns to its slot.
    func finishClose() {
        let id = request?.momentId
        clear()
        returnFocus = id
    }

    // MARK: - Print, restore, reset

    /// The print under way, mirrored to the scene (D6–D7).
    var printingFor: UUID? { snapshot?.printingFor }

    func startPrinting(_ momentId: UUID) {
        guard var current = snapshot else { return }
        current.printingFor = momentId
        current.face = .verso
        snapshot = current
    }

    func endPrinting() {
        guard var current = snapshot, current.printingFor != nil else { return }
        current.printingFor = nil
        snapshot = current
    }

    /// A relaunch after a background kill: the card reopens at rest on the
    /// saved face, with no flight, owned by no list (decision 3).
    func restore(_ saved: HeldCardSnapshot) {
        guard request == nil else { return }
        let request = HeldCardRequest(momentId: saved.momentId, face: saved.face, origin: .you)
        self.request = request
        snapshot = saved
        flies = false
        sourceRect = nil
        committedFace = saved.face
        phase = .open
        flight = 1
        chrome = 1
        pageShift = 0
        pageOpacity = 1
    }

    /// The sign-out reset (stack C §6): another account never reopens this card.
    func reset() {
        clear()
        returnFocus = nil
    }

    private func clear() {
        request = nil
        snapshot = nil
        phase = .idle
        flies = false
        sourceRect = nil
        restRect = nil
        leaving = false
        flight = 1
        chrome = 1
        pageShift = 0
        pageOpacity = 1
    }
}
```

`close()` is gone: every close now goes through `HeldCardHost` (the next task), which animates and then calls `finishClose()`. So this commit builds, replace `host.close()` with `host.finishClose()` (Edit with `replace_all`) in the E1c sheet modifier `apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift` (two calls) and in `HeldCardHostModelTests.close()`.

- [ ] **Step 4: Run the tests to verify they pass**

Run the **Suite command** with `HeldCardFlightStateTests`, then `HeldCardHostModelTests`, then `PrintRestoreTests`, then **Build**. Expected: PASS (8), PASS (6), PASS (4), `** BUILD SUCCEEDED **` (the sheet still presents; the next task replaces it).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Card/HeldCardHostModel.swift apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift apps/ios/PebblesTests/Card/HeldCardFlightStateTests.swift apps/ios/PebblesTests/Card/HeldCardHostModelTests.swift
git commit -m "feat(ios): the held card's flight as the host's state"
```

---

### Task E4.3 (spike, 3/5): the overlay and the FLIP

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/FlightEffect.swift`
- Create: `apps/ios/Pebbles/Features/Path/Card/CardPlaceholder.swift`
- Modify (replace): `apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Card/HeldCardPage.swift`
- Modify: `apps/ios/Pebbles/RootView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`

- [ ] **Step 1: The FLIP effect**

Create `apps/ios/Pebbles/Features/Path/Card/FlightEffect.swift`:

```swift
import SwiftUI

/// The card's FLIP (#1160 D1): `scaleEffect` about its top-leading corner and
/// an `offset`, from the slot's rect to the card's resting one, animatable on
/// `progress`. Layout never moves: the card lays out at rest, and a reporter
/// placed outside this effect reads that rest. Without both rects, nothing.
struct FlightEffect: ViewModifier, Animatable {
    var progress: Double
    let slot: CGRect?
    let rest: CGRect?

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let transform = slot.flatMap { slot in rest.map { FlightGeometry.transform(from: slot, to: $0, progress: progress) } }
            ?? (scale: 1, offset: .zero)
        content
            .scaleEffect(transform.scale, anchor: .topLeading)
            .offset(transform.offset)
    }
}
```

- [ ] **Step 2: The placeholder**

Create `apps/ios/Pebbles/Features/Path/Card/CardPlaceholder.swift`:

```swift
import SwiftUI

/// The blank card a fading open shows while the moment loads (#1146 §Flight,
/// first frame): the stock at 1 : 1.3 for up to 400 ms, then a progress
/// indicator in it. A seeded open shows the seed's recto instead.
struct CardPlaceholder: View {
    let width: CGFloat

    @State private var waited = false

    var body: some View {
        RoundedRectangle(cornerRadius: 11, style: .continuous)
            .fill(Color.pebbles.stock)
            .frame(width: width, height: width * 1.3)
            .overlay {
                if waited { ProgressView() }
            }
            .task {
                try? await Task.sleep(for: .milliseconds(400))
                waited = true
            }
    }
}
```

- [ ] **Step 3: The overlay host**

Replace the contents of `apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift` with:

```swift
import SwiftUI

/// The held card's layer (#1160 D1), in `RootView`'s `ZStack` above
/// `PebblesTabView`: not a presentation, so it flies on every supported iOS with
/// no guard, and the Spine stays mounted beneath with its slot to return to.
/// It draws the ground (`Color.pebbles.surface`, fading with the chrome), the
/// page, and the leading 20 pt edge strip whose swipe scrubs the close.
///
/// It animates the host's flight state: the open from the slot's measured rect
/// once the page reports its rest (a 0.45 s spring, the chrome in over its last
/// 60 %), or a 0.2 s fade; the close back to the slot's rect read again when
/// the committed face is the recto, else a 0.2 s fade. The slot hides for the
/// flight and shows again on its last frame.
///
/// VoiceOver: a modal layer (the tabs beneath are hidden by `RootView`), the
/// escape gesture closes it. New pattern, named in the E4 PR.
struct HeldCardHost: View {
    @Environment(HeldCardHostModel.self) private var host
    @Environment(CardFrameRegistry.self) private var registry
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var layoutDirection

    private var rightToLeft: Bool { layoutDirection == .rightToLeft }

    var body: some View {
        if let request = host.request {
            GeometryReader { proxy in
                ZStack(alignment: .topLeading) {
                    Color.pebbles.surface
                        .opacity(host.chrome)
                        .ignoresSafeArea()
                    HeldCardPage(request: request, onRest: { rested($0, id: request.momentId) }, onClose: close)
                        .offset(x: rightToLeft ? -host.pageShift : host.pageShift)
                        .opacity(host.pageOpacity)
                    edgeStrip(width: proxy.size.width)
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
            .accessibilityAction(.escape) { close() }
            .task(id: request.momentId) {
                // A fading open does not wait for a rest: the page may be a spinner.
                guard host.phase == .opening, !host.flies else { return }
                withAnimation(.easeOut(duration: 0.2), completionCriteria: .logicallyComplete) {
                    host.pageOpacity = 1
                    host.chrome = 1
                } completion: {
                    host.opened()
                }
            }
        }
    }

    // MARK: - Open

    private func rested(_ rect: CGRect, id: UUID) {
        guard host.reportRest(rect), host.flies else { return }
        // The slot hides on the frame the card appears on it: never both, never neither.
        registry.hidden = id
        withAnimation(.spring(duration: 0.45, bounce: 0), completionCriteria: .logicallyComplete) {
            host.flight = 1
        } completion: {
            host.opened()
        }
        withAnimation(.easeOut(duration: 0.27).delay(0.18)) { host.chrome = 1 }
    }

    // MARK: - Close

    private func close() {
        guard let id = host.request?.momentId, host.phase != .idle else { return }
        if case .closing = host.phase {} else {
            _ = host.beginClose(sourceRect: registry.frames[id], reduceMotion: reduceMotion)
        }
        completeClose(width: nil)
    }

    private func completeClose(width: CGFloat?) {
        if host.flies {
            withAnimation(.spring(duration: 0.45, bounce: 0), completionCriteria: .logicallyComplete) {
                host.flight = 0
                host.chrome = 0
            } completion: {
                finish()
            }
        } else {
            withAnimation(.easeOut(duration: 0.2), completionCriteria: .logicallyComplete) {
                host.pageOpacity = 0
                host.chrome = 0
                if let width { host.pageShift = width }
            } completion: {
                finish()
            }
        }
    }

    private func finish() {
        registry.hidden = nil
        host.finishClose()
    }

    // MARK: - The edge swipe

    /// The leading 20 pt (trailing in RTL, by layout direction) has priority
    /// over the card's turn; the card starts 16 pt in, and they move in opposite
    /// directions. Release with the turn's rule: a flick of 1.5 screens a second
    /// carries, else past the half completes; a cancel springs back in 0.21 s.
    private func edgeStrip(width: CGFloat) -> some View {
        HStack(spacing: 0) {
            Color.clear
                .frame(width: 20)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0, coordinateSpace: .global)
                        .onChanged { value in
                            guard let id = host.request?.momentId, host.phase != .opening, host.phase != .idle else {
                                return
                            }
                            if case .closing = host.phase {} else {
                                _ = host.beginClose(sourceRect: registry.frames[id], reduceMotion: reduceMotion)
                            }
                            let dx = rightToLeft ? -value.translation.width : value.translation.width
                            host.scrub(Double(max(0, dx) / max(width, 1)), width: width)
                        }
                        .onEnded { value in
                            guard case .closing(let progress) = host.phase else { return }
                            let velocity = Double((rightToLeft ? -value.velocity.width : value.velocity.width) / max(width, 1))
                            if TurnSettle.target(progress: progress, velocity: velocity) >= 1 {
                                completeClose(width: width)
                            } else {
                                withAnimation(.spring(duration: 0.21, bounce: 0)) { host.cancelClose() }
                            }
                        }
                )
            Spacer(minLength: 0)
        }
        .accessibilityHidden(true)
    }
}
```

- [ ] **Step 4: The page flies its card**

In `apps/ios/Pebbles/Features/Path/Card/HeldCardPage.swift`:

1. Replace

```swift
    let request: HeldCardRequest
    let onClose: () -> Void
```

with

```swift
    let request: HeldCardRequest
    /// The card's resting global rect, for the host's FLIP; reported on every change.
    var onRest: (CGRect) -> Void = { _ in }
    let onClose: () -> Void
```

2. Replace

```swift
    init(request: HeldCardRequest, onClose: @escaping () -> Void) {
        self.request = request
        self.onClose = onClose
```

with

```swift
    init(request: HeldCardRequest, onRest: @escaping (CGRect) -> Void = { _ in }, onClose: @escaping () -> Void) {
        self.request = request
        self.onRest = onRest
        self.onClose = onClose
```

3. The ground is the host's now (it fades with the chrome): replace

```swift
        .background(Color.pebbles.surface.ignoresSafeArea())
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { pageWidth = $0 }
```

with

```swift
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { pageWidth = $0 }
        .onChange(of: turn.face, initial: true) { _, face in host.faceCommitted(face) }
```

4. The bar fades with the chrome: replace

```swift
                       isDisabled: model.detail == nil || model.isDeleting || printStart != nil, onBack: onClose)
```

with

```swift
                       isDisabled: model.detail == nil || model.isDeleting || printStart != nil, onBack: onClose)
                .opacity(host.chrome)
```

5. The loading state shows the seed's recto, or the blank card. Replace

```swift
        case .loading:
            ProgressView()
```

with

```swift
        case .loading:
            ScrollView {
                Group {
                    if let seed = request.seed, let pebble = seed.pebble {
                        seededCard(seed, pebble)
                    } else {
                        CardPlaceholder(width: CardLayout.cardWidth(available: pageWidth))
                    }
                }
                .modifier(Flying(host: host, onRest: onRest))
                .frame(maxWidth: .infinity)
                .padding(.top, 8)
            }
            .scrollClipDisabled()
```

6. The card at rest flies; the page's scroll does not clip it. Replace

```swift
                    ScrollView {
                        page(detail)
                            .padding(.top, 8)
                            .padding(.bottom, 32)
                    }
```

with

```swift
                    ScrollView {
                        page(detail)
                            .padding(.top, 8)
                            .padding(.bottom, 32)
                    }
                    .scrollClipDisabled()
```

and in `card(_:_:print:)`, replace

```swift
            .frame(maxWidth: .infinity)
            .stonePressLifetime($versoPress)
    }
```

with

```swift
            .modifier(Flying(host: host, onRest: onRest, active: print == nil))
            .frame(maxWidth: .infinity)
            .stonePressLifetime($versoPress)
    }

    /// The seed's recto (#1146 §Flight, first frame): the Spine card at the
    /// card's width, so the flight lands on a card, never a spinner; the leaf,
    /// the souls past three and the verso fill in when the moment lands.
    private func seededCard(_ seed: MomentSummary, _ pebble: MomentSummary.PebbleSummary) -> some View {
        let width = CardLayout.cardWidth(available: pageWidth)
        let stone = SpineColors.palette(pebble.emotion.flatMap { palettes.palette(for: $0.id) }, isBlank: false)
        return SpineCardShell(palette: stone, polarity: pebble.valence.polarity, intensity: pebble.intensity) {
            SpineCardFace(moment: seed, palette: stone, ratio: seed.firstSnapPath.flatMap { aspects.ratio(for: $0) },
                          width: width, u: CardLayout.u(cardWidth: width), soulNames: [:], soulGlyphs: soulGlyphs,
                          soulOrder: request.soulOrder, accessible: true)
        }
    }
```

7. The delete that closes marks the card leaving first: replace

```swift
        case .closes:
            onClose()
```

with

```swift
        case .closes:
            // Before the close, so it fades: the slot has gone from the Path.
            host.markLeaving()
            onClose()
```

8. At the end of the file, add:

```swift
/// The FLIP on whichever card the page shows (#1160 D1): the transform from the
/// host's state, the rest read outside it, and the card invisible until its
/// rest is known, so the first frame of an open is measured, not guessed.
private struct Flying: ViewModifier {
    let host: HeldCardHostModel
    let onRest: (CGRect) -> Void
    var active = true

    func body(content: Content) -> some View {
        content
            .modifier(FlightEffect(progress: active ? host.flight : 1, slot: active ? host.sourceRect : nil,
                                   rest: host.restRect))
            .opacity(host.phase == .opening && host.flies && host.restRect == nil ? 0 : 1)
            .background {
                Color.clear
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { onRest($0) }
            }
    }
}
```

- [ ] **Step 5: `RootView` draws the layer**

In `apps/ios/Pebbles/RootView.swift`:

1. Replace

```swift
                    PebblesTabView(onPathFirstLoad: { pathFeedLoaded = true })
                        .heldCardHost(heldCard)
                        .environment(nav)
                        .environment(cardFrames)
                        .environment(heldCard)
```

with

```swift
                    PebblesTabView(onPathFirstLoad: { pathFeedLoaded = true })
                        .environment(nav)
                        .environment(cardFrames)
                        .environment(heldCard)
                        // Nothing under the held card is reachable (stack C §7's slot).
                        .accessibilityHidden(heldCard.request != nil)
```

2. Replace the two comment lines stack C left for this layer,

```swift
                    // The card-stack overlay host (#903 §7, later stack) goes
                    // here, above the tabs, so one overlay serves every tab.
```

with

```swift
                    // The held card (#1160 D1): one layer above the tabs for every tab.
                    HeldCardHost()
                        .environment(heldCard)
                        .environment(cardFrames)
                        .environment(\.spineSnapSource, .signed(snapURLs))
```

If those comment lines are gone on `main`, insert the block right after the `PebblesTabView` chain (inside the same `ZStack`, before `if holdsLoader {`). If `PebblesTabView`'s chain also carries `.environment(\.spineSnapSource, .signed(snapURLs))` (D5a), keep it.

- [ ] **Step 6: The Path opens from the slot's rect**

In `apps/ios/Pebbles/Features/Path/PathView.swift`:

1. After `    @Environment(HeldCardHostModel.self) private var heldCard`, add:

```swift
    @Environment(CardFrameRegistry.self) private var cardFrames
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
```

2. Replace

```swift
    private func open(_ open: SpineOpen) {
        heldCard.open(HeldCardRequest(open, origin: .path, onChanged: { reloadAfterCard() }))
    }
```

with

```swift
    private func open(_ open: SpineOpen) {
        model.returnFocus = nil
        heldCard.open(HeldCardRequest(open, origin: .path, onChanged: { reloadAfterCard() }),
                      sourceRect: cardFrames.frames[open.momentId], reduceMotion: reduceMotion)
    }
```

3. In the `#Preview`, add `.environment(CardFrameRegistry())`.

- [ ] **Step 7: Build, suite, lint**

Run **Build**, the **Full suite** and **Lint**. Expected: green. `grep -rn "heldCardHost\|PebbleDetailSheet(request" apps/ios/Pebbles` prints only `PebbleDetailSheet.swift`'s own declaration (E5 deletes the file).

- [ ] **Step 8: Commit (on go only; see E4.5)**

Stage, do not commit yet:

```bash
git add apps/ios/Pebbles/Features/Path/Card/FlightEffect.swift apps/ios/Pebbles/Features/Path/Card/CardPlaceholder.swift apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift apps/ios/Pebbles/Features/Path/Card/HeldCardPage.swift apps/ios/Pebbles/RootView.swift apps/ios/Pebbles/Features/Path/PathView.swift
```

The commit is in Task E4.5, after the device decides.

---

### Task E4.4 (spike, 4/5): VoiceOver modality, focus in and focus home

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/CardFocus.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCardFace.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Card/CardRecto.swift`, `CardSlip.swift`, `CardVerso.swift`, `PebbleCard.swift`, `HeldCardPage.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`

- [ ] **Step 1: The focus values**

Create `apps/ios/Pebbles/Features/Path/Card/CardFocus.swift`:

```swift
import SwiftUI

/// Where VoiceOver lands when the held card opens (#1146 §Flight): the
/// recto's title, or the verso's "Your pebble".
enum CardFocus: Hashable {
    case title, versoHeading
}

extension View {
    /// `accessibilityFocused(_:equals:)` when the card passes a binding; nothing on the Spine.
    @ViewBuilder
    func cardFocus(_ binding: AccessibilityFocusState<CardFocus?>.Binding?, _ value: CardFocus) -> some View {
        if let binding {
            accessibilityFocused(binding, equals: value)
        } else {
            self
        }
    }
}
```

- [ ] **Step 2: Thread the binding to the title and the heading**

1. `SpineCardFace.swift`: after `    var leaf: AnyView?`, add `    var titleFocus: AccessibilityFocusState<CardFocus?>.Binding?`; after `                .accessibilitySortPriority(5)` in `title(u:)` (the named title only), add `                .cardFocus(titleFocus, .title)`.
2. `CardSlip.swift`: add `    var focus: AccessibilityFocusState<CardFocus?>.Binding?` after `let u: CGFloat`, and `.cardFocus(focus, .title)` after `.accessibilitySortPriority(5)`.
3. `CardRecto.swift`: add `    var focus: AccessibilityFocusState<CardFocus?>.Binding?` after `let soulOrder: [String]`; add `focus: AccessibilityFocusState<CardFocus?>.Binding? = nil` as the last `init` parameter with `self.focus = focus`; pass `titleFocus: focus` to `SpineCardFace(…)` (after `leaf:`) and `focus: focus` to `CardSlip(…)`.
4. `CardVerso.swift`: add `    var focus: AccessibilityFocusState<CardFocus?>.Binding?` after `var stonePress: StonePress?`, and `.cardFocus(focus, .versoHeading)` after `.accessibilityAddTraits(.isHeader)` in `lockup(_:u:)`.
5. `PebbleCard.swift`: add `    var focus: AccessibilityFocusState<CardFocus?>.Binding?` after `var stonePress: StonePress?`, and pass `focus: focus` to `CardRecto(…)` (last argument) and `CardVerso(…)` (last argument).

- [ ] **Step 3: The page moves focus in once the flight lands**

In `HeldCardPage.swift`:

1. After `    @State private var versoPress: StonePress?`, add `    @AccessibilityFocusState private var focus: CardFocus?`.
2. In `card(_:_:print:)`, pass `focus: $focus` as `PebbleCard`'s last argument.
3. After `        .onChange(of: turn.face, initial: true) { _, face in host.faceCommitted(face) }`, add:

```swift
        // After the open: the title on the recto, "Your pebble" on the verso.
        .onChange(of: host.phase == .open && model.detail?.pebble != nil, initial: true) { _, ready in
            guard ready else { return }
            focus = turn.face == .recto ? .title : .versoHeading
        }
```

- [ ] **Step 4: Focus returns to the slot**

In `PathView.swift`, after `.task(id: references.souls) { syncSouls() }`, add:

```swift
            // A held card closed: VoiceOver returns to its slot (stack D3's focus state).
            .onChange(of: heldCard.returnFocus) { _, id in
                if let id { model.returnFocus = id }
            }
```

- [ ] **Step 5: Build, suite**

Run **Build** and the **Full suite**. Expected: green. Stage with the rest of the spike:

```bash
git add apps/ios/Pebbles/Features/Path/Card/CardFocus.swift apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCardFace.swift apps/ios/Pebbles/Features/Path/Card/CardRecto.swift apps/ios/Pebbles/Features/Path/Card/CardSlip.swift apps/ios/Pebbles/Features/Path/Card/CardVerso.swift apps/ios/Pebbles/Features/Path/Card/PebbleCard.swift apps/ios/Pebbles/Features/Path/Card/HeldCardPage.swift apps/ios/Pebbles/Features/Path/PathView.swift
```

---

### Task E4.5 (spike, 5/5): the device decides (the orchestrator runs it with the maintainer)

**Files:** none; the staged code is committed on go.

- [ ] **Step 1: Install the staged build on the iPhone 15**

Follow the `project_ios_device_deploy` recipe from the working tree. Reading only.

- [ ] **Step 2: The protocol (spec §Spike)**

1. Open and close 5 times each from a photo card at the **top**, the **middle** and the **bottom** of a week, and once after swiping to another week. Watch frame by frame (screen recording at 60 fps, scrub it on the Mac): no frame shows both the slot and the flying card, or neither; the first frame is a card; the close lands on the slot within 1 pt (pause the recording on the last frame and the first frame after; compare the card's edges).
2. One edge swipe cancelled at about 30 % (the card springs back), one completed (it flies home).
3. The same under **VoiceOver**: after the open, focus is on the title; a two-finger Z closes; focus returns to the slot; swiping right through the card never reaches anything under it.
4. The same under **Reduce Motion**: crossfades only, no flight.
5. Hitches on runs 1–3:

```bash
xcrun xctrace list devices | grep -i iphone
xcrun xctrace record --template 'Animation Hitches' --device "<the iPhone's UDID>" --attach Pebbles \
  --time-limit 40s --output "$TMPDIR/flight.trace"
open "$TMPDIR/flight.trace"
```

   Perform the opens and closes while it records. In Instruments, read **Hitches → Hitch Time Ratio**: under 5 ms/s.

- [ ] **Step 3: Decide and record**

**Go** when every item of step 2 holds. Then commit the staged work:

```bash
git commit -m "feat(ios): the held card is a layer above the tabs that flies from its slot"
```

and write the run (device, iOS version, hitch ratio, any near-miss) for the PR body and the Lessons learned. Continue with E4.6.

**No-go:** the page's wiring (rest reporting, focus, the seeded first frame, `FlightEffect`, `CardPlaceholder`) is shared by the fallbacks, so commit the staged work as it is, recording the failing items:

```bash
git commit -m "feat(ios): the held card's layer, flip and focus (spike no-go, the host is replaced next)"
```

then run **Task E4.F**, which replaces `HeldCardHost.swift` only. If E4.F's own device run fails the same protocol, run **Task E4.G**.

---

### Task E4.6: The decision-log entry

**Files:**
- Modify: `docs/decisions/log.md` (append only)

- [ ] **Step 1: Append**

Append at the end of `docs/decisions/log.md`, after one blank line. Replace `<YYYY-MM-DD>` with the day E4 merges. Edit no earlier entry.

```markdown
## <YYYY-MM-DD> — The iOS held card opens from its slot as an overlay with a measured-frame FLIP (#1160, #1146)

- **Status:** taken
- **Scope:** ios, ui
- **Context:** The iOS Path became the Spine (#1130), where a pebble is a card, and the detail became the held card (#1146), first in a `.large` sheet. The card needs the whole height and should fly from its slot, on an iOS 17 floor where SwiftUI's zoom transition does not exist.
- **Decision:** On an iOS 17 floor the card is a full-screen layer in `RootView` above the tabs (`HeldCardHost`), not a presentation; it animates `scaleEffect` + `offset` from the Spine slot's measured global rect (a `[momentId: CGRect]` registry, `CardFrameRegistry`) to its resting rect, and back on close while the committed face is the recto. Stone presses, bare moments, printing, delete and Reduce Motion do not fly. The detail leaves the sheet. The device spike (iPhone 15: five opens and closes from the top, middle and bottom, cancelled and completed edge swipes, VoiceOver, Reduce Motion) came out go.
- **Why:** It flies on every supported iOS with no `#available` guard, and it is the only SwiftUI-native option in which every rule is exact (the close follows the committed face; stone presses, bare moments, printing and delete do not fly; Reduce Motion is a chosen crossfade). Rejected: zoom on 18 + push on 17 (a guard where an iOS 17 equivalent exists, #727; zoom cannot close by face), `matchedGeometryEffect` (remeasures both faces each frame, untrusted across the week pager), raising the floor to 18 (no adoption data; zoom still cannot close by face), a UIKit animator (environment re-injection; kept as the spike's fallback).
- **Consequences:**
  - Every caller opens the card through `HeldCardHostModel.open`; nothing presents a pebble detail as a sheet or a push.
  - The layer owns its modality: `.isModal`, the tabs `accessibilityHidden` while a card is held, the escape gesture, focus to the title on open and back to the slot on close, a hand-built leading-edge swipe.
  - `@SceneStorage("heldCard")` reopens the card on its face after a background kill; the sign-out reset clears it.
- **Supersedes / Superseded-by:** Extends **2026-10-20 — The Android pebble detail is the held card** (#1135) to iOS; its consequence "iOS still has the old read page" is closed by #1146.
- **Refs:** #1160, #1146, #1161, `docs/superpowers/specs/2026-10-20-ios-pebble-card-design.md`, `docs/superpowers/plans/2026-10-21-ios-pebble-card.md`, `apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift`.
```

If the spike came out no-go, replace the Decision's last sentence with "The spike came out no-go; the flight is a UIKit transitioning-delegate animator sharing the same predicates and geometry" (or, if E4.G ran, "…a fade with a 1.04 → 1 lift"), and move "a UIKit animator" out of the rejected list in **Why**.

- [ ] **Step 2: Commit**

```bash
git add docs/decisions/log.md
git commit -m "docs(ios): record the held card's overlay and flip decision"
```

---

### Task E4.7: Device checklist (iPhone 15, before E4 merges)

**Files:** none.

- [ ] **Step 1: The spec's checklist**

On the iPhone 15, reading only, record each:

1. Postmark contrast and ring legibility at 1× (E1c.8's numbers stand unless the colours changed).
2. The turn drag against vertical scroll (E2.7).
3. The edge swipe cancelled and completed, from a recto (flies) and from a verso (slides and fades).
4. VoiceOver order on both faces and the bare page (spec §VoiceOver: recto title, date and time with the emotion, souls, photo, words, privacy, turn; verso "Your pebble" with the valence, emotion, stone, life domains, collections, privacy, turn; bare kicker, title, photo, words, the place's kicker and sentence, Make it a pebble).
5. Reduce Motion on the turn, the print and the flight.
6. Night.
7. French.
8. The largest accessibility type size: the card's text stops at × 1.3, the footer stacks, the page scrolls, nothing clips.
9. A soul's and a collection's rows open the card with a fade (no seed) and close back to their list.
10. A delete from the card's ⋮ fades the card out (no flight to a slot that has gone).

---

### Task E4.8: E4 verification and PR

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <E4-number> --title "feat(ios): the held card flies out of its slot and back" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Resolves #1160` and `Part of #1146`; key files; the spike's record (device, iOS, the protocol's items, the hitch ratio; go, or which fallback ran); **new pattern for review:** `HeldCardHost`, an overlay above `PebblesTabView` in `RootView` with its own modality, edge swipe and focus return; the deviations: soul and collection rows reach the overlay here (they opened through the host since E1c), and the page reports its rest through a background reporter outside the FLIP; the decision-log entry; the E4.7 checklist. No Lab Note, no `F-…` ids.

---

### Task E4.F (only on a no-go): Option 4, the UIKit animator

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Card/Fallback/HeldCardTransition.swift`
- Create: `apps/ios/Pebbles/Features/Path/Card/Fallback/HeldCardEnvironment.swift`
- Modify (replace): `apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift`

The spike's fallback (spec D1, Option 4): a `UIViewControllerTransitioningDelegate` animator presenting the page's `UIHostingController` with `.custom` presentation, reusing `CardFlight` and `FlightGeometry`, every environment service re-injected.

- [ ] **Step 1: The environment, carried across the seam**

Create `apps/ios/Pebbles/Features/Path/Card/Fallback/HeldCardEnvironment.swift`:

```swift
import SwiftUI

/// Every service the held card's page and its sheets read (#1160 Option 4): a
/// `UIHostingController` starts a new SwiftUI root, which inherits nothing, so
/// the host captures them here and re-injects each one.
@MainActor
struct HeldCardEnvironment {
    let supabase: SupabaseService
    let palettes: EmotionPaletteService
    let refs: ReferenceDataService
    let stats: PathStatsService
    let snapURLs: SnapURLCache
    let snapAspects: SnapAspectCache
    let karma: KarmaNotificationService
    let achievementNotify: AchievementNotificationService
    let achievements: AchievementsService
    let completeDrafts: CompleteDraftStore
    let connections: ConnectionsService
    let revision: MomentsRevision
    let host: HeldCardHostModel
    let registry: CardFrameRegistry
    let colorScheme: ColorScheme
    let dynamicTypeSize: DynamicTypeSize
    let locale: Locale

    func apply<Content: View>(to content: Content) -> some View {
        content
            .environment(supabase)
            .environment(palettes)
            .environment(refs)
            .environment(stats)
            .environment(snapURLs)
            .environment(snapAspects)
            .environment(karma)
            .environment(achievementNotify)
            .environment(achievements)
            .environment(completeDrafts)
            .environment(connections)
            .environment(revision)
            .environment(host)
            .environment(registry)
            .environment(\.spineSnapSource, .signed(snapURLs))
            .environment(\.colorScheme, colorScheme)
            .environment(\.dynamicTypeSize, dynamicTypeSize)
            .environment(\.locale, locale)
    }
}
```

If `PebblesApp` injects a service this list lacks (`grep -n "\.environment(" apps/ios/Pebbles/PebblesApp.swift`), add it here the same way; a missing one crashes the page on first read.

- [ ] **Step 2: The animator and the interaction**

Create `apps/ios/Pebbles/Features/Path/Card/Fallback/HeldCardTransition.swift`:

```swift
import SwiftUI
import UIKit

/// Option 4 (#1160): the held card presented as a `UIHostingController` with a
/// custom transition. The whole page view is transformed so that the card's
/// resting rect maps onto the slot's (the same `FlightGeometry`), and the
/// ground fades through the host's `chrome`. A leading screen-edge pan drives
/// the dismissal interactively with the turn's release rule.
@MainActor
final class HeldCardTransition: NSObject, UIViewControllerTransitioningDelegate, UIViewControllerAnimatedTransitioning {
    private let host: HeldCardHostModel
    private let slot: () -> CGRect?
    private var presenting = true
    let interaction = UIPercentDrivenInteractiveTransition()
    var isInteractive = false

    init(host: HeldCardHostModel, slot: @escaping () -> CGRect?) {
        self.host = host
        self.slot = slot
    }

    func animationController(forPresented presented: UIViewController, presenting: UIViewController,
                             source: UIViewController) -> UIViewControllerAnimatedTransitioning? {
        self.presenting = true
        return self
    }

    func animationController(forDismissed dismissed: UIViewController) -> UIViewControllerAnimatedTransitioning? {
        presenting = false
        return self
    }

    func interactionControllerForDismissal(using animator: UIViewControllerAnimatedTransitioning)
        -> UIViewControllerInteractiveTransitioning? {
        isInteractive ? interaction : nil
    }

    func transitionDuration(using context: UIViewControllerContextTransitioning?) -> TimeInterval {
        0.45
    }

    func animateTransition(using context: UIViewControllerContextTransitioning) {
        let key: UITransitionContextViewKey = presenting ? .to : .from
        guard let view = context.view(forKey: key) else {
            context.completeTransition(false)
            return
        }
        if presenting {
            context.containerView.addSubview(view)
            if let controller = context.viewController(forKey: .to) {
                view.frame = context.finalFrame(for: controller)
            }
            // Lay the page out now, so the card reports its rest before the first frame.
            view.layoutIfNeeded()
        }
        let flies = host.flies
        let flown = flightTransform(in: view)
        view.transform = presenting ? (flies ? flown : .identity) : .identity
        view.alpha = presenting && !flies ? 0 : 1
        let animator = UIViewPropertyAnimator(duration: transitionDuration(using: context), dampingRatio: 1) {
            if self.presenting {
                view.transform = .identity
                view.alpha = 1
            } else {
                view.transform = flies ? flown : .identity
                if !flies { view.alpha = 0 }
            }
        }
        animator.addCompletion { position in
            context.completeTransition(position == .end && !context.transitionWasCancelled)
        }
        animator.startAnimation()
    }

    /// The page view's transform that puts the card's rest onto the slot.
    private func flightTransform(in view: UIView) -> CGAffineTransform {
        guard let slot = slot(), let rest = host.restRect, rest.width > 0 else { return .identity }
        let flip = FlightGeometry.transform(from: slot, to: rest, progress: 0)
        let anchor = view.convert(rest.origin, from: nil)
        return CGAffineTransform(translationX: anchor.x, y: anchor.y)
            .scaledBy(x: flip.scale, y: flip.scale)
            .translatedBy(x: -anchor.x, y: -anchor.y)
            .concatenating(CGAffineTransform(translationX: flip.offset.width, y: flip.offset.height))
    }
}

/// Presents and dismisses the page across the UIKit seam, for `HeldCardHost` on a no-go.
@MainActor
final class HeldCardPresenter {
    private weak var presented: UIViewController?
    private var transition: HeldCardTransition?

    func present(_ request: HeldCardRequest, environment: HeldCardEnvironment, slot: @escaping () -> CGRect?,
                 onClose: @escaping () -> Void) {
        guard presented == nil,
              let root = UIApplication.shared.connectedScenes.compactMap({ ($0 as? UIWindowScene)?.keyWindow })
                .first?.rootViewController else { return }
        var top = root
        while let next = top.presentedViewController { top = next }
        // UIKit animates the whole page; the page's own FLIP and fades stay at rest.
        environment.host.flight = 1
        environment.host.chrome = 1
        environment.host.pageOpacity = 1
        let page = environment.apply(to: HeldCardPage(request: request, onRest: { rect in
            _ = environment.host.reportRest(rect)
        }, onClose: onClose).background(Color.pebbles.surface.ignoresSafeArea()))
        let controller = UIHostingController(rootView: page)
        let transition = HeldCardTransition(host: environment.host, slot: slot)
        controller.modalPresentationStyle = .custom
        controller.transitioningDelegate = transition
        let edge = UIScreenEdgePanGestureRecognizer(target: self, action: #selector(edgePan(_:)))
        edge.edges = .left
        controller.view.addGestureRecognizer(edge)
        self.transition = transition
        presented = controller
        top.present(controller, animated: true) { environment.host.opened() }
    }

    func dismiss(completion: @escaping () -> Void) {
        presented?.dismiss(animated: true) {
            completion()
        }
    }

    @objc private func edgePan(_ recognizer: UIScreenEdgePanGestureRecognizer) {
        guard let view = recognizer.view, let transition else { return }
        let width = max(view.bounds.width, 1)
        let progress = Double(max(0, recognizer.translation(in: view).x) / width)
        switch recognizer.state {
        case .began:
            transition.isInteractive = true
            presented?.dismiss(animated: true)
        case .changed:
            transition.interaction.update(CGFloat(progress))
        case .ended:
            let velocity = Double(recognizer.velocity(in: view).x / width)
            if TurnSettle.target(progress: progress, velocity: velocity) >= 1 {
                transition.interaction.finish()
            } else {
                transition.interaction.cancel()
            }
            transition.isInteractive = false
        default:
            transition.interaction.cancel()
            transition.isInteractive = false
        }
    }
}
```

- [ ] **Step 3: The host presents across the seam**

Replace the contents of `apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift` with:

```swift
import SwiftUI

/// The held card's host on the spike's no-go (#1160 Option 4): it presents the
/// page through `HeldCardPresenter` when a request arrives, decides the close
/// with the same `CardFlight` rules, and calls `finishClose()` after the
/// dismissal. It draws nothing itself.
struct HeldCardHost: View {
    @Environment(HeldCardHostModel.self) private var host
    @Environment(CardFrameRegistry.self) private var registry
    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var refs
    @Environment(PathStatsService.self) private var stats
    @Environment(SnapURLCache.self) private var snapURLs
    @Environment(SnapAspectCache.self) private var snapAspects
    @Environment(KarmaNotificationService.self) private var karma
    @Environment(AchievementNotificationService.self) private var achievementNotify
    @Environment(AchievementsService.self) private var achievements
    @Environment(CompleteDraftStore.self) private var completeDrafts
    @Environment(ConnectionsService.self) private var connections
    @Environment(MomentsRevision.self) private var revision
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.locale) private var locale
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var presenter = HeldCardPresenter()

    var body: some View {
        Color.clear
            .allowsHitTesting(false)
            .task(id: host.request?.momentId) {
                guard let request = host.request else { return }
                let id = request.momentId
                if host.flies { registry.hidden = id }
                presenter.present(request, environment: environment, slot: { registry.frames[id] }, onClose: close)
            }
    }

    private var environment: HeldCardEnvironment {
        HeldCardEnvironment(supabase: supabase, palettes: palettes, refs: refs, stats: stats, snapURLs: snapURLs,
                            snapAspects: snapAspects, karma: karma, achievementNotify: achievementNotify,
                            achievements: achievements, completeDrafts: completeDrafts, connections: connections,
                            revision: revision, host: host, registry: registry, colorScheme: colorScheme,
                            dynamicTypeSize: dynamicTypeSize, locale: locale)
    }

    private func close() {
        guard let id = host.request?.momentId else { return }
        _ = host.beginClose(sourceRect: registry.frames[id], reduceMotion: reduceMotion)
        presenter.dismiss {
            registry.hidden = nil
            host.finishClose()
        }
    }
}
```

`HeldCardPresenter` is a class held in `@State`; it is not `@Observable` (nothing observes it). In `RootView`, the `HeldCardHost()` call of E4.3 stays as written. In `HeldCardPage`, the ground: the fallback page carries its own background (set in `present`).

- [ ] **Step 4: Build, the device protocol again, commit**

Run **Build** and the **Full suite**, then E4.5's protocol on the device with this build. On go:

```bash
git add apps/ios/Pebbles/Features/Path/Card/Fallback apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift
git commit -m "feat(ios): the held card flies through a uikit transition, the spike's fallback"
```

Then E4.6 with the no-go wording, E4.7 and E4.8. On a second no-go, run E4.G.

---

### Task E4.G (only if E4.F also fails): a fade with a lift

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Card/Logic/CardFlight.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift` (E4.3's overlay, restored from the spike's commit)
- Modify: `apps/ios/PebblesTests/Card/CardFlightTests.swift`

Android's recorded fallback: no flight; every open and close is a fade with the page lifting from 1.04 to 1.

- [ ] **Step 1: Nothing flies**

Restore E4.3's overlay host from the spike's commit (`git show <spike-commit>:apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift > apps/ios/Pebbles/Features/Path/Card/HeldCardHost.swift`) and delete `apps/ios/Pebbles/Features/Path/Card/Fallback/`. In `CardFlight.swift`, replace the bodies of `opensFlying` and `heldCardFlies` with `false` and keep their doc comments, adding: "E4.G: the spike and Option 4 both came out no-go; every open and close fades with a lift (Android's recorded fallback)." In `CardFlightTests`, replace `heldCard` and `opens` with:

```swift
    @Test("nothing flies: every open and close fades with a lift (E4.G)")
    func nothingFlies() {
        #expect(!CardFlight.heldCardFlies(showing: .recto, printing: false, leaving: false, reduceMotion: false))
        #expect(!CardFlight.opensFlying(request(), sourceRect: slot, reduceMotion: false))
    }
```

- [ ] **Step 2: The lift**

In `HeldCardHost.swift`, after `.opacity(host.pageOpacity)`, add:

```swift
                        // E4.G: the page lifts from 1.04 to 1 as it fades in, and back as it fades out.
                        .scaleEffect(reduceMotion ? 1 : 1 + 0.04 * (1 - host.pageOpacity))
```

- [ ] **Step 3: Build, the protocol, commit**

Run **Build**, the **Full suite** and `CardFlightTests`; on the device, opens and closes fade with a slight lift, VoiceOver and Reduce Motion as in E4.5.

```bash
git add -A apps/ios/Pebbles/Features/Path/Card apps/ios/PebblesTests/Card/CardFlightTests.swift
git commit -m "feat(ios): the held card fades in with a lift, the flight's last fallback"
```

Then E4.6 with the "fade with a 1.04 → 1 lift" wording, E4.7 and E4.8.

---
## Part E5 — `feat/1146-ios-remove-read-view`

PR title: `feat(ios): one held card, the read view gone`. Body opens `Resolves #1146`. The lists reload from `MomentsRevision` (for a restored card, decision 3); `PebbleDetailSheet` and the whole of `Features/Path/Read/` go with their two tests and their orphaned strings; the docs and the lessons.

### Task E5.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1146-ios-remove-read-view
git branch --show-current
```

Expected: `feat/1146-ios-remove-read-view`.

---

### Task E5.1: People and Collections reload from `MomentsRevision` (decision 3)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`

A card the list opened reloads it through `onChanged`. A restored card has no list to call back: it bumps `MomentsRevision` from `.you`, which Path already reads; these two now read it too.

- [ ] **Step 1: Read the revision**

In `SoulDetailView.swift` (tab `.people`) and `CollectionDetailView.swift` (tab `.collections`):

1. If the view has no `@Environment(MomentsRevision.self) private var revision` yet (`grep -n "MomentsRevision" <file>`; stack C added it for the delete bump), add it after `    @Environment(HeldCardHostModel.self) private var heldCard`.
2. After `            .task { await load() }` (the first one, unique per file), add (with `.collections` in `CollectionDetailView`):

```swift
            // A change made outside this list (a restored held card, another tab) reloads it (#1146).
            .onChange(of: revision.value) {
                guard revision.changedOutside(.people) else { return }
                Task { await load() }
            }
```

- [ ] **Step 2: Build, suite, commit**

Run **Build** and the **Full suite**. Expected: green (`HeldCardModelTests` already holds one bump per save, delete and completion).

```bash
git add apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift
git commit -m "feat(ios): people and collections reload when a moment changes elsewhere"
```

---

### Task E5.2: Collect the strings only the read view uses

**Files:** none (a scratch list).

- [ ] **Step 1: List the literals**

```bash
grep -ohE 'Text\("[^"]+"|LocalizedStringResource = "[^"]+"|return "[^"]+"|Label\("[^"]+"|Button\("[^"]+"|accessibilityLabel\("[^"]+"' \
  apps/ios/Pebbles/Features/Path/Read/*.swift apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift \
  | sed -E 's/.*"([^"]+)"$/\1/; s/^[^"]*"//' | sort -u > "$TMPDIR/read-strings.txt"
wc -l "$TMPDIR/read-strings.txt"
```

- [ ] **Step 2: Keep only the orphans**

For each line, count the uses outside the files being deleted:

```bash
while IFS= read -r key; do
  uses=$(grep -rlF "\"$key\"" apps/ios/Pebbles --include=*.swift \
    | grep -v "Features/Path/Read/" | grep -v "Features/Path/PebbleDetailSheet.swift" | wc -l | tr -d ' ')
  [ "$uses" = "0" ] && echo "$key"
done < "$TMPDIR/read-strings.txt" > "$TMPDIR/read-orphans.txt"
cat "$TMPDIR/read-orphans.txt"
```

Keep `$TMPDIR/read-orphans.txt` for Task E5.4. A key that appears in a `String(localized:)` or a model's `LocalizedStringResource` elsewhere is not an orphan; check each listed key once more with `grep -rn "<key>" apps/ios/Pebbles` before removing it.

---

### Task E5.3: Delete the read view and the sheet

**Files:**
- Delete: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`
- Delete: `apps/ios/Pebbles/Features/Path/Read/` (`BannerAspect.swift`, `PebbleMetaPill.swift`, `PebblePageColors.swift`, `PebblePillFlow.swift`, `PebblePrivacyBadge.swift`, `PebbleReadBanner.swift`, `PebbleReadPetroglyph.swift`, `PebbleReadTitle.swift`, `PebbleReadView.swift`, `PebbleSnapFrame.swift`)
- Delete: `apps/ios/PebblesTests/BannerAspectTests.swift`, `apps/ios/PebblesTests/PebblePageColorsTests.swift`
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift`
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphThumbnail.swift`
- Modify: `apps/ios/Pebbles/Features/Path/SoulPill.swift`

- [ ] **Step 1: Nothing else calls them**

```bash
grep -rn "PebbleReadView\|PebbleReadBanner\|PebbleReadTitle\|PebbleReadPetroglyph\|PebbleSnapFrame\|PebblePrivacyBadge\|PebbleMetaPill\|PebblePillFlow\|PebblePageColors\|pebblePageColors\|BannerAspect\|PebbleDetailSheet" \
  apps/ios/Pebbles apps/ios/PebblesTests | grep -v "Features/Path/Read/" | grep -v "PebbleDetailSheet.swift" \
  | grep -v "BannerAspectTests.swift\|PebblePageColorsTests.swift"
```

Expected: only the doc comments in `GlyphThumbnail.swift` and `SoulPill.swift` and the `ThemeLiteralsTests` allowlist line for `PebbleSnapFrame.swift`. Anything else is a caller the spec missed: stop and report.

- [ ] **Step 2: Delete**

```bash
git rm -r apps/ios/Pebbles/Features/Path/Read apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift \
  apps/ios/PebblesTests/BannerAspectTests.swift apps/ios/PebblesTests/PebblePageColorsTests.swift
```

- [ ] **Step 3: The allowlist, the doc comments**

1. `ThemeLiteralsTests.swift`: delete the line

```swift
        "Features/Path/Read/PebbleSnapFrame.swift": (1, "preview fixture image drawn above #Preview"),
```

2. `GlyphThumbnail.swift`: delete the line

```swift
/// - `PebbleMetaPill` (path)   — explicitly chrome-less inside a pill
```

3. `SoulPill.swift`: replace

```swift
/// Bordered pill used to render a soul in a 3-column grid. Shared between
/// `PebbleReadView.soulsRow` and `SelectedSoulsRow` (the edit form), so the
/// two surfaces stay visually identical. Pure UI: tap behavior is owned by
/// the parent.
```

with

```swift
/// Bordered pill used to render a soul in a 3-column grid, in
/// `SelectedSoulsRow` (the edit form). Pure UI: tap behavior is owned by the
/// parent.
```

and replace

```swift
/// Three-column adaptive grid spec used by both `PebbleReadView.soulsRow`
/// and `SelectedSoulsRow`. Lifted out so the two surfaces share the
/// exact same column geometry.
```

with

```swift
/// Three-column adaptive grid spec used by `SelectedSoulsRow`.
```

- [ ] **Step 4: What the deletion orphaned**

```bash
grep -rn "petroglyphColors(" apps/ios/Pebbles | grep -v "func petroglyphColors"
```

If it prints nothing, `EmotionPalette.petroglyphColors` and `PetroglyphColors` (in `Features/Path/Models/EmotionPalette.swift`, tested by `PetroglyphColorsTests`) lost their last caller. Do not delete them in this part (root `CLAUDE.md`: no refactor without approval); name them in the PR body as a follow-up.

- [ ] **Step 5: Build, suite, lint, commit**

Run **Build**, the **Full suite** (with `xcodegen generate`, so the deleted files leave the project) and **Lint**. Expected: green; the suite is two suites smaller.

```bash
git add -A apps/ios/Pebbles apps/ios/PebblesTests
git commit -m "chore(ios): remove the old read view and the detail sheet"
```

---

### Task E5.4: Remove the strings no view uses any more

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 1: Remove each orphan's entry as text**

For each key in `$TMPDIR/read-orphans.txt`, delete its whole entry from the catalogue: from the line `    "<key>" : {` to its matching closing `    },` (the next line at four spaces of indent that is `    },`). Do it with an editor or this script, which removes text blocks only and never re-serialises the file:

```bash
python3 - "$TMPDIR/read-orphans.txt" <<'EOF'
import sys
path = "apps/ios/Pebbles/Resources/Localizable.xcstrings"
keys = [line.rstrip("\n") for line in open(sys.argv[1]) if line.strip()]
lines = open(path, encoding="utf-8").read().split("\n")
out, skip = [], False
for line in lines:
    if not skip and any(line == '    "%s" : {' % key.replace('"', '\\"') for key in keys):
        skip = True
        continue
    if skip:
        if line == "    }," or line == "    }":
            skip = False
        continue
    out.append(line)
open(path, "w", encoding="utf-8").write("\n".join(out))
EOF
python3 -c "import json; json.load(open('apps/ios/Pebbles/Resources/Localizable.xcstrings')); print('valid')"
git diff --stat apps/ios/Pebbles/Resources/Localizable.xcstrings
```

Expected: `valid`; only deletions. If the last entry of the file was removed, the new last entry may now end in `},` before `  },`: fix that one comma by hand and re-run the check.

- [ ] **Step 2: Check in Xcode, commit**

Open the catalogue in Xcode: no `New` or `Stale` entry. Run **Build** and the **Suite command** with `LocalizationTests` and `CardStringsTests`.

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "chore(ios): drop the strings only the read view used"
```

---

### Task E5.5: `apps/ios/CLAUDE.md`

**Files:**
- Modify: `apps/ios/CLAUDE.md`

- [ ] **Step 1: The host in the agent context**

Replace

```
The authed root is `PathView` with a custom bottom bar (not a system `TabView`); sheets and full-screen covers do most secondary navigation.
```

with

```
The authed root is `PebblesTabView` (four tabs); sheets and full-screen covers do most secondary navigation, except the pebble detail: every caller opens the held card through `HeldCardHostModel.open`, and `HeldCardHost` draws it as a full-screen layer above the tabs in `RootView` (`Features/Path/Card/`).
```

If stack C already rewrote that sentence on `main` (`grep -n "secondary navigation" apps/ios/CLAUDE.md`), keep its first clause and replace only "sheets and full-screen covers do most secondary navigation." with the same words from "sheets" on.

- [ ] **Step 2: Commit**

```bash
git add apps/ios/CLAUDE.md
git commit -m "docs(ios): name the held card's host in the ios agent context"
```

---

### Task E5.6: Lessons learned

**Files:**
- Modify: `docs/superpowers/plans/2026-10-21-ios-pebble-card.md`

- [ ] **Step 1: Write them**

Append a `## Lessons learned (execution, Parts E0a–E5, <date>)` section at the end of this plan with what the execution taught, one bullet each, durable and action-guiding where possible: D4's measured contrast and the ink mode kept (E1c.8); the turn probe's measurements on the simulator and whether the sign or the fit needed re-deriving (E2.1); whether E2.8's pan fallback ran and on which runtime; whether the print's anchors drew under `ImageRenderer` (E3.7); the spike's outcome with its device, iOS and hitch ratio, and which of E4.F / E4.G ran; any review round's finding the plan should have prevented. Root `CLAUDE.md`: these stay here; promotion to an agent file happens at a milestone's grooming pass, never per PR.

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/2026-10-21-ios-pebble-card.md
git commit -m "docs(ios): lessons learned for the held card stack"
```

---

### Task E5.7: Device check (the orchestrator runs it with the maintainer)

**Files:** none.

- [ ] **Step 1**

On the iPhone 15, reading only: People → a soul → a pebble, and Collections → a collection → a pebble, open the card without a flight and close back to their list; a card restored after a background kill (turned to its verso) reopens on its verso, and an Edit pebble on it (ask first) reloads Path, People and Collections. Record for the PR body.

---

### Task E5.8: E5 verification and PR

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <E5-number> --title "feat(ios): one held card, the read view gone" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Resolves #1146`; the deleted files and tests, the orphaned strings removed (the list from E5.2), the `ThemeLiteralsTests` allowlist line, the doc comments; decision 3's reload in People and Collections; the `petroglyphColors` follow-up if E5.3 step 4 found it orphaned; the device results; the stack merges whole (`#1099`, `#1145`, `#1160` close at their own parts). No Lab Note, no `F-…` ids.

- [ ] **Step 3: Arkaik**

Nothing to set by hand: the Arkaik App marks `V-pebble-detail` and `AC-hold-a-pebble-like-a-card-and-turn-it-over` `releasing` for iOS at merge. Never `live`.

---
## Self-review notes (done while writing)

### Spec coverage

| Spec section / rule | Tasks |
|---|---|
| Goal, non-goals (no gem words, I/II/III, seals, gauges, edit links, folded corner, tilted or shadowed stamps, pills) | E1b (none of them is drawn; the postmark has no rotation, shadow or tap; the band is not a pill) |
| D1 overlay + measured FLIP, rejected options, Option 4 as the no-go fallback | E4.1–E4.5, E4.F, E4.G, E4.6 |
| D2 the page leaves the sheet; one page for the interim and the overlay | E1c.4–E1c.7 (sheet), E4.3 (overlay), E5.3 (sheet deleted) |
| D3 the ⋮ through A2's rule, orange Delete | E1b.12, E1c.4, E1c.8 (device), decision 5 |
| D4 `destinationOut` postmark, speckle fallback, device contrast | E1b.5, E1c.8, E2.7 (in the turn) |
| D5 the turn rotates a ready layer; swap at 90° | E1b.11 (both faces laid out), E2.1 (`CardTurnEffect`, `CardFaceVisibility`), E2.4 |
| D6 completion is state; the print ends where it ends | E3.3, E3.5, E3.6 |
| D7 `@SceneStorage("heldCard")`, no request counter, sign-out reset | E1c.1, E1c.3, E1c.6, E2 (landing writes), E3.3 (printingFor) |
| D8 #1099 absent means keep | E0a.1–E0a.3 |
| D9 #1145 collection glyphs | E0b.1–E0b.3, E1b.6 (band draws them) |
| Component tree and files | file map; E1b–E4 |
| `SpineCard` split, `u` unclamped, `namedSouls: 8` | E1b.1, E1b.2, E1b.4 |
| `CardLayout` numbers | E1a.1 |
| State model (request, rect, phase, return focus, detail, completion, leaving, revision, face, progress, front, landing, press request, printingFor, printedOver, announcing) | E1c.1–E1c.3, E2.3, E3.3, E4.2 |
| Recto (dateline, leaf, F3 slip, 8 souls, ratio) | E1a.6, E1a.8, E1b.2–E1b.4 |
| Verso (mat, lockup ranks and ink, emotion, stone 150, postmarks, ink by scheme, highlight ramp) | E1a.3, E1a.7, E1b.5, E1b.8, E1b.9 |
| Postmark (ring rank, per-character draw, noise seed, diameter rule) | E1a.2, E1a.4, E1b.5 |
| Band (paper, past the border, knots never at a line's end, glyph-led names) | E1a.5, E1b.6 |
| Footer (privacy element, turn target, miniature, stacking, names the face not in front, switches at the half) | E1a.5, E1b.7, E2.4 (`TurnFooter`) |
| Top bar (Back, Share public only, ⋮, disabled while loading and deleting) | E1b.12, E1c.4, E3.6 (and while printing) |
| Bare page (kicker, title, photo mat, words, place, Make it a pebble) | E3.2, E3.4 |
| Strings EN/FR, dotted keys, reused keys, `LocalizationTests` | E1a.9, E1a.10, E5.4 |
| VoiceOver reading order, hidden face, button last, no custom action | E1b.2, E1b.3, E1b.7, E1b.9, E1b.11, E2.4, E3.4, E4.4, E4.7 |
| Pure functions (`RingFit`, `ValenceLockup`, people line, `PostmarkNoise`, `TurnSettle`, `TurnGeometry`, `CardFlight`, `FlightGeometry`) | E1a.2–E1a.4, E1a.8, E2.1–E2.2, E4.1 |
| Turn: angle, perspective, fit (decision 1), button spring, one turn at a time, drag lock and settle, cancel, width 0, Reduce Motion, stone press verso, restoration | E2.1–E2.5, E2.7 |
| Gesture fallback (`UIPanGestureRecognizer`) | E2.8 |
| Edge swipe vs turn | E4.3 (strip), E2.4 |
| Bare moment and print states, `CompletionMotion`, scroll first, crossfade, Reduce Motion, no replay | E3.1–E3.6, E3.8 |
| Open and close: registry, first frame (seed, blank card + 400 ms), open spring and chrome, close by committed face, never-flies list, edge swipe, VoiceOver modality and focus, status bar | E4.2–E4.4, E4.3 (`CardPlaceholder`, seeded card) |
| Spike protocol, go/no-go, fallbacks | E4.5, E4.F, E4.G |
| Decision-log entry | E4.6 |
| Accessibility (Dynamic Type × 1.3 cap, footer stacks, contrast, 44 pt, Reduce Motion, tilt off) | E1b.7, E1b.8 (`followsTilt: false`), E1b.13 (ax2), E1c.8, E4.7 |
| Testing per part (suites, references, device) | each part's test and reference tasks; device tasks E0a.3, E1c.8, E2.7, E3.8, E4.5, E4.7, E5.7 |
| Parts table (branches, done-when, Arkaik, labels, `no-lab-note`) | Parts table; E0a.0 (Arkaik pickup); each PR task |
| E5 removal list, `GlyphThumbnail` doc, `CLAUDE.md` line, lessons | E5.2–E5.6 |
| Risks (perspective, arbitration, `destinationOut` in a turning panel, the hand-built close, a stale rect, stack D slips, the interim sheet) | E2.1, E2.7–E2.8, E2.7 item 7, E4.5, E4.2 (re-read rect), E0a.0, E1c |
| New patterns raised in PRs | E1a.11, E1b.14, E1c.9, E4.8 |

### Deviations to surface in the PR bodies

- **E1 is three parts** (E1a logic and strings, E1b the faces, E1c the page and host), because it measured ~3,800 changed lines against the ~1,700 the parts keep under.
- **The host lands in E1** (a fixed decision), so the soul and collection rows open the card through `HeldCardHostModel` from E1c, not E5, and reach the overlay in E4; `HeldCardModel` takes over `MomentsRevision`'s bumps in E1c, from a request `origin` the spec's request did not list. E5 keeps the deletions, the lists' revision reads (decision 3) and the docs.
- **`CardTurnEffect` is a `GeometryEffect`** that builds `rotation3DEffect`'s own projection from the size it is handed (the probe holds them equal), because `rotation3DEffect(perspective:)` needs the panel's height before layout; the fit scale is the exact form (decision 1).
- **The verso's VoiceOver heading is one element**, "Your pebble" with the valence as its value (Android's shape), where the spec lists two stops; the dateline's element carries the emotion after it.
- **The print reads its geometry through anchor preferences** in one layout pass rather than measured state; the place is the card's width, so the card prints at its own width.
- **`CardLayout.cardWidth` has no Android vectors** (Android has no unit test for it); its tests are its formula's. The Spine's `CardSizes` vectors came from web in D1.10.
- **`PostmarkNoise` draws from SplitMix64**, so its bytes differ from Android's (Kotlin's `Random`), with the same statistics, held by the ported test.
- **`PebblesMenuList` becomes internal** (decision 4) and the card uses the shared list's row rules as its divider (decision 5).
- **The kicker and dateline use the device's zone**: iOS's `MomentDetail` carries none.
- **E0a's labels** drop `android` (the PR is iOS-only) and its milestone moves to M64; #1099's and #1145's Android halves get their own issues (decision 6).
- **`SoulPill`'s doc comments** also named `PebbleReadView`; E5 rewrites them with `GlyphThumbnail`'s, and `ThemeLiteralsTests` loses `PebbleSnapFrame.swift`'s allowlist line, both missing from the spec's E5 list.

### Placeholder scan

Searched this plan for "TBD", "TODO", "implement later", "fill in", "add appropriate", "handle edge cases", "similar to Task", "write tests for the above". None. The `<…-number>` and `<scratch-body-file>` tokens in PR commands, `<YYYY-MM-DD>` in the decision-log entry, `<date>` in the lessons heading, `<spike-commit>` and the device ids are values the executor reads at run time, as stack D's plan does.

### Type consistency

`CardLayout.cardWidth(available:)`, `u(cardWidth:)`, `bandFoot(u:)`, `postmarkDiameter(count:rowWidth:)`, `versoStoneSize(_:)`; `RingFits.fit(name:circumference:locale:measure:)` → `RingFit(text:repetitions:size:tracking:)`; `ValenceLockup.of(_:)` with `LockupRank.sizeGroup` and `LockupInk`; `PostmarkNoise.alphas(size:seed:)` and `SplitMix64`; `BandLines.lines(widths:knotRun:maxWidth:)` / `width(of:widths:knotRun:)`; `FooterArrangement.stacked(turnWidth:available:)`; `CardDateline.text` / `time` / `kicker(_:calendar:locale:)`; `CardColors.postmarkInkSlot(scheme:)`, `bandPaperK(scheme:)`, `mix(_:toward:_:in:)`, `highlightRamp(_:scheme:in:)`; `MomentSummary(detail:)`; `Visibility.explanation`; `CardStrings.*`; `SpineCardShell(palette:polarity:intensity:content:)`; `SpineCardFace(…, accessible:, slip:, leaf:, titleFocus:)`; `CardRecto(…, focus:)`; `CardVerso(…, print:, stonePress:, focus:)`; `PebbleCard(…, turn:, print:, stonePress:, focus:)`; `CardTurnModel(face:progress:)` with `beginTurn(reduceMotion:)`, `startDrag()`, `drag(to:reduceMotion:)`, `release(at:velocity:reduceMotion:)` → `Settle(target:springVelocity:)`, `cancelDrag()`, `settled()`; `TurnSettle.progress(drag:width:rightToLeft:)`, `turnsPerSecond(velocity:width:rightToLeft:)`, `target(progress:velocity:)`, `launchVelocity(target:velocity:)`; `TurnGeometry.angle(progress:rightToLeft:)`, `perspective(width:height:)`, `fitScale(progress:width:)`; `CardTurnEffect(progress:rightToLeft:fits:)`, `CardFaceVisibility(progress:isCommitted:)`; `HeldCardRequest(momentId:face:seed:soulOrder:origin:onChanged:)` and `HeldCardRequest(_:origin:onChanged:)`; `HeldCardSnapshot(momentId:face:printingFor:)` with `decoded(from:)` / `encoded`; `HeldCardModel(momentId:origin:onChanged:)` with `load(_:)`, `changed(_:)`, `delete(_:target:revision:perform:)` → `DeleteOutcome`, `Completion`, `Loaded`, `printEnded()`, `cutShort()`, `announced()`; `HeldCardHostModel` with `open(_:sourceRect:reduceMotion:)`, `reportRest(_:)`, `opened()`, `faceCommitted(_:)`, `markLeaving()`, `land(_:)`, `beginClose(sourceRect:reduceMotion:)`, `scrub(_:width:)`, `cancelClose()`, `finishClose()`, `startPrinting(_:)`, `endPrinting()`, `printingFor`, `restore(_:)`, `reset()` (`close()` exists E1c–E4.1 only and is replaced in E4.2); `HeldCardMenu.items(hasPebble:editMoment:editPebble:delete:)`; `CompletionMotion` (`at`, `print`, `stone`, `rise`, `flight`, `ghostAlpha`, `litStoneAlpha`, `pageAlpha`, `timeline(elapsed:duration:)`, `ease`, `ramp`) and `PrintPart.alpha(_:)`; `PrintStart.decide(place:viewport:reduceMotion:)` / `cutsShort(printingFor:momentId:hasPebble:)`; `PrintAnchor` / `PrintAnchors`; `CardFlight.spineCardFlies` / `heldCardFlies(showing:printing:leaving:reduceMotion:)` / `opensFlying(_:sourceRect:reduceMotion:)`; `FlightGeometry.transform(from:to:progress:)`; `FlightEffect(progress:slot:rest:)`; `CardFocus` and `cardFocus(_:_:)`. Each use above matches its definition in the task that creates it.

## Lessons learned (stack E, 2026-10-22)

What executing Parts E0a–E5 taught, one bullet each. Root `CLAUDE.md`: these stay here; promotion to an agent file happens at a milestone's grooming pass, never per PR.

- **`gh stack init` branches from the local trunk, not `origin/main`.** The stack was cut while the main checkout's `main` was stale (before stack D merged), so E1a could not find the Spine it builds on. Fast-forward local `main` first (`git fetch origin && git merge --ff-only origin/main`), then `gh stack init`; when it has already happened, `gh stack rebase` after the fast-forward repairs it.
- **`ImageRenderer` draws a row carrying `.accessibilityFocused` at zero size.** The shared menu list (`PebblesMenuList`) renders empty under it, so decision 4's menu-open reference was dropped and the menu is a device check. Same family as stack D's slot focus: a reference never holds a focused row.
- **`anchorPreference` on a parent replaces what its children reported for the key.** The place's `.place` anchor silently dropped the ghost's `.ghost` anchor, and the print's flight with it. A parent that adds its own anchor to a key its children also report uses `transformAnchorPreference` and merges (E3).
- **A `GeometryEffect` node at rest pixel-snaps its subtree.** A fixture whose panel height is not integral shifts by a sub-pixel once the effect is in the hierarchy (stress-verso, f2-recto), so E1b's references were re-recorded with E2. It is not the shader re-phasing: look for the effect before re-tuning a texture.
- **A stroke is never thinner than a point.** `GlyphThumbnail` drew a 0.42 pt stroke at 14 pt beside a full-weight SF fallback; the maintainer floored the stroke at 1 pt in the shared component, and CI's re-render moved no Spine reference.
- **Format dates on a Gregorian calendar in the given zone, never on the Spine's ISO 8601 calendar.** `CardDateline` on `SpineCalendar.current` fell back to ICU's root order ("2026 October 8, Thursday"). Tests that build their calendar as Gregorian never see it: one test formats on the calendar the view actually hands over.
- **Cross-part signatures change in review.** `DeleteOutcome`, `setReadingDay(Int?)` and `SpineCardShell(emotion:)` were renamed or reshaped while their parts were reviewed, so later tasks that quote them verbatim fail to compile. The implementer fixes the call in place and names the drift in its report; the plan is not re-edited.
- **Reviews caught what the plan should have:** a load-ordering race in `HeldCardModel` (now a generation guard, only the latest load applies), the footer tappable mid-print, an `onChange` that needs `initial: true` when the first frame is already past the end, the edge strip swallowing vertical scrolls, and the flying card changing identity when the moment lands mid-spring. Each is a state-as-value rule the plan stated and the code first missed; a review round per part is worth its cost.
- **D4's contrast and the ink mode held (E1c.8)** and the Edit-pebble prefill (E0a.3): both device checks passed on the maintainer's iPhone 15 on 2026-10-22.
- **The turn's probe (E2.1) confirmed decision 1 on the simulator:** the projection assumption held and the exact fit form keeps the near edge within the card's height; neither the sign nor the fit needed re-deriving, and the pan fallback (E2.8) did not run, so `TurnPanFallback.swift` was never written.
- **The print's anchors drew under `ImageRenderer` (E3.7)** once the `.ghost` anchor was merged (above); the bare page and the print at three instants have references.
- **The device spike (E4) and the gesture arbitration, print and restore device checks (E2, E3, E1c) run with the maintainer once the stack is up**; the UIKit fallback (E4.F) is written only on a no-go. The decision-log entry records the spike as pending.
- **A deletion part greps for the helpers a deleted file hosts, not only its types.** `PebbleMetaPill.swift` carried `extension Color { init?(hex:) }`, used by `EmotionPalette`, `PebbleOutlineBackdropView` and `StoneLabPalettes`; E5.3's type-name grep missed it and the build caught it. It moved verbatim to `Shared/Theme/Color+Hex.swift`. Before `git rm`, `grep -n "^extension\|^func\|^enum\|^struct" <file>` lists what else the file gives away.
