# iOS Spine Path (Stack D) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Each page of the iOS Path becomes a Spine week (#1130): an opening in words, the persons of the week, a contents list, then one line down the margin over raked paper, with each pebble's lit stone on it, a Deck card at the photo's own ratio or an entry set as type, a seam per day, and a stone press that dips, ripples, glints and asks to open the pebble.

**Architecture:** Pure Swift logic (`Features/Path/Spine/Logic/`, `Foundation` only, explicit `Calendar` and `Locale`) ports the web Vitest vectors verbatim. Views are value-driven rows (`SpineWeekRows`) laid out by a `LazyVStack` in the app and by a plain `VStack` for the B2 reference harness. One `@Observable` `PathModel` owns the week roll, the focused week and the per-week `SpinePage` values; a press request (`StonePressRequests`) and a card's frame (`CardFrameRegistry`) are state, not events. The pager is a paging horizontal `ScrollView` bound to `PathModel.focusedWeekStart`.

**Tech Stack:** SwiftUI (iOS 17 floor, no `#available`), `@Observable`, Swift Testing, XcodeGen, SwiftLint, the B2 `ImageRenderer` reference gate, `gh stack`, `arkaik-mcp`.

**Spec (the contract):** `docs/superpowers/specs/2026-10-20-ios-spine-path-design.md`. Siblings: `2026-10-20-ios-m3-colour-design.md` (stack A), `2026-10-20-ios-new-path-foundations-design.md` (stack B), `2026-10-20-ios-tab-navigation-design.md` (stack C), `2026-10-20-ios-pebble-card-design.md` (stack E, consumer of `CardFrameRegistry`, `StonePressRequests`, `SpineOpen`, `CardFace`, `PeopleLines`). Behaviour reference (never transliterated): `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/spine/` and `docs/superpowers/plans/2026-10-18-android-spine-path.md` (Lessons learned).

---

## Fixed decisions (given; not reopened)

- **Parts**, in order, one branch = one PR, chained with `gh stack` (non-interactive: `gh stack init <branch>` / `gh stack add <branch>`, `gh stack submit --auto`, `gh stack view --json`): **D1** pure logic + verbatim vectors; **D2a** the Spine stone (lit `StoneView` in the Spine slot, press state through `StonePressRequests`); **D2b** the card slot + `CardFrameRegistry` (`@ObservationIgnored frames`, `hidden`); **D3** the feed (`PathModel`, `@Observable`, `.task`, `path_moments` live, `MomentsRevision` reload); **D4** opening + pager (`SpineOpen` with `soulOrder: [String]`, week opening, `PeopleLines.peopleLine(_:order:locale:namedInFull:)` with `namedOnCard = 8`); **D5a** the switch (the Path tab root becomes the Spine); **D5b** removal of the old timeline.
- Branch names `type/1130-ios-<slug>`. PR titles in conventional-commit form. Bodies open `Resolves #1130` only on the part that completes it and `Part of #1130` otherwise. Labels `feat`, `ui`, `ios` + `no-lab-note`; milestone `M64 · New Path`.
- **Dependencies:** stack A whole (#1163 → #1174), stack B whole (#1164 → #1168) and stack C (#903's parts) are on `main` before D's stack is created from `main`. D's first task checks this and stops otherwise. D has its own simulator, "iPhone 17 stack-d", and its own DerivedData, `Pebbles-stack-d`, both created in the first task as stack C does.
- **Standards:** colours only via `Color.pebbles.*` (`ThemeLiteralsTests` fails otherwise); Spine text only via `.spineType(.rank)`; `@Observable` + `.task`; state as values (the press request and the completion are state, not events); `os.Logger` on every error path; pure logic outside views; iOS 17 floor, no `#available`; Swift 6 out of scope; EN/FR strings in `Localizable.xcstrings` with no `New`/`Stale` entry, "galet" never "caillou"; VoiceOver order per spec; 44 pt targets; Dynamic Type up to the accessibility sizes; reference images rendered on CI only through the B2 gate (record mode `PBBLS_RECORD_REFERENCES=1`, label `rebaseline-ios-screenshots`, threshold never widened), so each visual part adds its reference suite and its PR task applies the label and approves the held run; Arkaik through `arkaik-mcp` only, the Spine view and acceptances move to `development` at pickup, never `live`, and `update_node` replaces metadata wholesale.
- Tests run on the stack's simulator with `-derivedDataPath`. Delete that folder, never the global DerivedData. Run `xcodegen generate` after any `project.yml` change.

## Decisions taken by the maintainer (2026-10-21)

The plan was first written with these as recommendations; the maintainer decided each one. The task in brackets carries it.

1. **The three tones the stack A tokens lack mirror Android's `core/designsystem/SpineColors.kt`.** The rake's groove shadow and the card's shadow ink are blends computed once per scheme in `SpineColors.swift`: `rakeShadow` is `onSurface` @ 0.20 on Paper and black @ 0.34 at Night; the shadow ink is `onSurface` on Paper and black at Night, carried with Android's `shadowAlpha` (0.30 Paper, 0.62 Night). Black is `Color.pebbles.scrim`, which M3 keeps `#000000` in every scheme, because `Features/` may not name `.black`. The rake's lit line is **one** generated token, `rakeLight` (white @ 0.55 on Paper, `#DCE2EA` @ 0.075 at Night, the same in the high-contrast appearances), because `Features/` may not name a hex; the generator learns constant-hex sources for it. There are no `rakeShadow` or `cardShadow` tokens. [Tasks D2b.1, D2b.2, D3.3, D2b.8]
2. **The reading day reads seams and pebble rows**, as Android's `ReadingDay.kt` does: a `LazyVStack` drops a seam that has scrolled far above the viewport, and a long day is still read through its pebbles. [Task D4c.1]
3. **Ten parts, fewer and larger.** The brief's parts stand, with three kept apart because each is a reviewable unit of its own: D1c (the strings and the fixture week; folding it into D1 would make ~2,250 lines, past the ~1,700 the maintainer allowed), D3c (the feed) and D4c (the pager). One commit per task inside each part. [Parts table]
4. **Items placed by dependency:** `CardFace` in D2a (the press carries it; spec §Press-request state defines it beside `StonePressRequests`); `SpineColors` in D2b, extended in D4 (where its blends are first drawn); `SoulMark` and the people-line view in D2b (the card's strip draws them first); `StoneSeal` in D4 (the contents are its only user); `PathModel` in D3 (the brief's part; the spec puts it in D5a); `SpineOpen` in D4c (the brief's D4; the spec says D2b); `WeekNeighbourTests` in D3 (it tests `PathModel`). [Parts table]
5. **`Resolves #1130` is on D5a**, as the spec's table says; D5b says `Part of #1130`. The stack merges whole. [Task D5a.8]
6. **The glint's white** is stone data and lives in the exempt `Features/Path/Stone/StoneGlintInk.swift`, beside `StonePalette.blank`'s literal ramp. [Task D2a.7]
7. **The lab's photos.** The reference suites read the five sandbox JPGs through `#filePath`, which a device cannot reach, so the DEBUG lab draws fixture cards on their photo bed (ratio primed); real photos show in the app. [Task D2b.9]
8. **"No pebbles" is its own key**, `path.spine.summaryNoPebbles`, read by the explicit zero check the spec asks for, so every plural keeps to CLDR categories. [Task D1c.1]
9. **The verbatim souls row.** D1 tests rows written in the server's exact jsonb shape (from `20261018120000_path_moments_souls.sql`) and the captured pre-#1121 rows; D5a captures one real `path_moments` row with souls through a throwaway account on the linked project, adds it to `MomentFixtures.Captured`, then purges the account through the existing `delete-account` flow (`purge_account`, storage, auth user). [Tasks D1.1, D5a.6]
10. **The brief's "ring fit and valence → lockup" vectors** are stack E's (`RingFit`, `SpineType.Style.lockupWord`) and stay with stack E's plan. [none in D]

## Dependencies

- **Stack A whole** (#1163 → #1169 → #1171 → #1174): `Color.pebbles.*`, the nine Spine tokens, `PebblesMenu` / `.pebblesContextMenu`, `ThemeLiteralsTests` with an empty debt table.
- **Stack B whole** (#1164 → #1166 → #1168): `SpineType` and `.spineType(_:)` (`Theme/SpineType.swift`, `Theme/SpineTypeModifier.swift`), `SpineType.Cut`, the reference harness (`PebblesTests/References/Support/`), the soft silhouette (`WobbleRenderer.softBackdropArt(size:polarity:)`), `ios-references.yml` and the `rebaseline-ios-screenshots` label.
- **Stack C** (#903's two parts): `PebblesTabView`, `TabNavigation` (with `scrollToTop`), `MomentsRevision`, `NewPebbleFab`, `PathStatusCluster`, `PebbleDetailSheet(momentId:origin:onPebbleUpdated:)`, `PathBottomBar` deleted.
- Task D1.0 checks all three and stops with "waiting on <stack>" if one is missing.
- **Feeds stack E:** E1–E3 need `SpineCard` (D2b) and `PeopleLines` (D1); E4 needs `CardFrameRegistry` (D2b), `SpineOpen` (D4c) and the per-slot focus state (D3c).

## Re-grep before trusting line numbers

This plan was written on 2026-10-21 against `main` at `d53be9cf` (`chore(android): remove the old read view (#1154)`), before stacks A, B and C merged. Their code was read from their branches (`feat/921-ios-m3-roles-path`, `fix/1097-ios-lit-stone-materials`) and from `docs/superpowers/plans/2026-10-21-ios-tab-navigation.md`. Every edit below is anchored on quoted text, never on a line number. Before each Edit, `grep -n` the anchor; if it is missing or not unique, stop and report rather than guess.

## Before you start

- Work from the repo root of the checkout or worktree that holds the stack. All paths are relative to it. In a worktree, first copy `apps/ios/Config/Secrets.xcconfig` from the main checkout (git-ignored; `xcodegen generate` needs it).
- **Simulator and DerivedData.** Every iOS command block starts with these two lines, because shell state does not persist between calls:

  ```bash
  SIM=$(xcrun simctl list devices | grep "iPhone 17 stack-d" | grep -oE '[0-9A-F]{8}(-[0-9A-F]{4}){3}-[0-9A-F]{12}' | head -1)
  DD="$HOME/Library/Developer/Xcode/DerivedData/Pebbles-stack-d"
  ```

  Task D1.0 creates the simulator. Use `-destination "id=$SIM"` with **double** quotes. To clear stale objects, delete `$DD/Build` only, never `~/Library/Developer/Xcode/DerivedData` as a whole (another stack's simulator run lives there).
- **Named commands** (each starts with the two lines above):
  - **Suite command** for `<Suite>`:
    `cd apps/ios && xcodegen generate && TEST_RUNNER_TZ=Europe/Paris xcodebuild test -scheme Pebbles -destination "id=$SIM" -derivedDataPath "$DD" -only-testing:PebblesTests/<Suite> -quiet`
  - **Full suite** (trusted, clears stale objects first):
    `cd apps/ios && xcodegen generate && rm -rf "$DD/Build" && TEST_RUNNER_TZ=Europe/Paris xcodebuild test -scheme Pebbles -destination "id=$SIM" -derivedDataPath "$DD" -quiet`
    Locally, the `References` suites fail for any reference that CI has not recorded yet ("no reference at …"). Judge the full suite by everything else; the reference suites are judged on CI.
  - **Build**:
    `cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination "id=$SIM" -derivedDataPath "$DD" build -quiet`
  - **Lint**: `npm run lint --workspace=@pbbls/ios` (SwiftLint; errors fail CI, `line_length` warns at 120).
  - **Look** at `<Suite>` (a local render for your eyes only, never committed):
    `cd apps/ios && xcodegen generate && TEST_RUNNER_TZ=Europe/Paris TEST_RUNNER_PBBLS_RECORD_REFERENCES=1 xcodebuild test -scheme Pebbles -destination "id=$SIM" -derivedDataPath "$DD" -only-testing:PebblesTests/References/<Suite> -quiet`
    Then read the PNGs under `apps/ios/PebblesTests/References/__References__/<Suite>/` (the Read tool shows images), and throw them away:
    `cd apps/ios && git clean -fdq -- PebblesTests/References/__References__/<Suite> && (git checkout -q -- PebblesTests/References/__References__ 2>/dev/null || true)`
    After it, `git status --short apps/ios/PebblesTests/References/__References__` must print nothing.
- **Rebaseline on CI** (every visual part; per the stack memory notes, add the label alone and approve the held runs):

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

  Then open the bot commit (`test(ios): re-baseline reference images`) in the PR and look at every new PNG against the Android preview named in the task. Never re-run the failed run (a re-run replays the old SHA). If a lower branch of the stack is re-baselined after an upper one, the upper branch wins PNG conflicts on rebase.
- **Strings** are inserted into `apps/ios/Pebbles/Resources/Localizable.xcstrings` as text at an anchor, never by re-serialising the file (no `json.dump`, no `jq`). After each edit, `git diff --stat` on the catalogue shows only insertions. Before each PR that touches strings, open the catalogue in Xcode and confirm no entry is `New` or `Stale`, and that every `path.spine.*` row has both `en` and `fr`.
- **Colours:** only `Color.pebbles.<role>` in new code under `Features/` and `Components/`. The emotion palette (`StonePalette`, `EmotionPalette`) is pebble data, not chrome, and is read as `palette.primary` etc., always qualified.
- **Type:** Spine text only through `.spineType(.<rank>)`. Never `.font`, `.bold()` or `.fontWeight()` on a Spine `Text`.
- **PR metadata (every part):** labels `feat`, `ui`, `ios`, `no-lab-note` (D5b: `chore`, `ui`, `ios`, `no-lab-note`, as the spec's parts table says); milestone `M64 · New Path`. Body: `Part of #1130` (D5a: `Resolves #1130`). Never a Kritik `F-…` id. No `## Lab Note` section (Arkaik covers the change). Do not add `Co-Authored-By` lines from this plan; the executing session appends its own attribution.
- **Push** only after `git fetch origin` and a fast-forward of the branch (stack memory note), with `gh stack push` then `gh stack submit --auto`, then `gh stack view --json` to read the PR numbers.
- **Arkaik** is changed only through `arkaik-mcp`. Never touch `docs/arkaik/bundle.json` or `journal.jsonl`. If the tools do not load, say so and skip that step (record it in the PR body). Never set `live`.

## Parts

| # | Branch | PR title | Contents | Est. changed lines |
|---|---|---|---|---|
| D1 | `feat/1130-ios-spine-logic` | `feat(ios): spine logic, from days and people to words and card sizes` | `PathSoul` + `souls` decoding, `SpineMoment`, `LocalDays`, `SpineWeekBuilder`, `PersonsOfWeek`, `StableHash`, `NumberWords`, `SpineDates`, `WeekOpening` (logic), `PeopleLines`, `EntryTypes`, `CardSizes`, the verbatim vectors; Arkaik pickup | ~1,200 (vectors ~660) |
| D1c | `feat/1130-ios-spine-strings` | `feat(ios): spine strings in english and french, and the fixture week` | 33 `path.spine.*` keys, `SpineStrings`, `SpineCopy`, the fixture week and its marks | ~1,050 (catalogue JSON ~700, generated marks ~120) |
| D2a | `feat/1130-ios-spine-stone` | `feat(ios): the spine stone, its press, rings, ripple and glint` | `StoneMotion`, `StonePress`, `StonePressRequests`, `CardFace`, `StoneRingGeometry`, `SpineLayout`, the `seed:` parameter, `SpineStone`, `StoneRings`, `StoneRipple`, `StoneGlint`, `StoneGlintInk`, the lab (stones), stone references | ~1,030 |
| D2b | `feat/1130-ios-spine-card` | `feat(ios): the spine deck card, its photo ratio and its frame` | the `rakeLight` token, `SpineColors` (edge, spot, rake shadow, shadow ink), `SnapAspectCache`, `CardFrameRegistry`, `SpineSnapSource`, the people line's parts and view, `SoulMark`, `SpineCard`, card references, lab cards | ~900 |
| D3 | `feat/1130-ios-path-model` | `feat(ios): the path model, the raked ground and the day seams` | `PathModel`, `SpinePage`, `WeekNeighbour`, `SpineRakeTile`, `SpineGround`, `DaySeam`, seam references | ~800 |
| D3c | `feat/1130-ios-spine-feed` | `feat(ios): spine entries, pebbles and the days of a week` | `SpineStoneDescription`, `SpineEntry`, `SpinePebble`, `SpineWeekRows` (days), `SpineWeekStack`, day references, dense lab week | ~620 |
| D4 | `feat/1130-ios-spine-opening` | `feat(ios): the week opens with its persons and contents and closes at the mouth` | `WeekOpeningText`, `WeekMasthead`, `WeekOpeningView`, `DayDots`, `PersonsOfWeekView`, `WeekMouth`, `SpineEmptyWeek`, `SpineColors` deep ink, `StoneSeal`, `WeekContents`, rows, references | ~980 |
| D4c | `feat/1130-ios-spine-pager` | `feat(ios): the spine week pages, rolls and presses from its contents` | `ReadingDay`, `SpineOpen`, `SpineWeekView`, `SpinePager`, `SpineWeekRoll`, lab pager, the paging spike | ~500 |
| D5a | `feat/1130-ios-path-becomes-spine` | `feat(ios): the path becomes the spine` | `RootView` registry, `PathView` on `PathModel` + pager, full-week references, the captured souls row, decision log, `apps/ios/CLAUDE.md` | ~400 |
| D5b | `chore/1130-ios-remove-row-path` | `chore(ios): remove the row path` | The removal list, the `.riv`, five tests, orphaned strings, the literal allowlist entry | ~-1,400 (deletions) |

## File map

All app paths are under `apps/ios/Pebbles/`, test paths under `apps/ios/PebblesTests/`.

| File | Part | Responsibility |
|---|---|---|
| `Features/Path/Models/PathSoul.swift` (new) | D1 | A pebble's soul on `path_moments` |
| `Features/Path/Models/MomentSummary.swift` | D1 | `PebbleSummary.souls`, tolerant decode |
| `Features/Path/Spine/Logic/SpineMoment.swift` (new) | D1 | The logic's moment protocol; `MomentSummary` conformance |
| `Features/Path/Spine/Logic/LocalDays.swift` (new) | D1 | Calendar-day arithmetic, DST-safe |
| `Features/Path/Spine/Logic/SpineWeekBuilder.swift` (new) | D1 | Days of a week, reading order |
| `Features/Path/Spine/Logic/PersonsOfWeek.swift` (new) | D1 | Persons of the week, the fold |
| `Features/Path/Spine/Logic/StableHash.swift` (new) | D1 | FNV-1a seeds |
| `Features/Path/Spine/Logic/NumberWords.swift` (new) | D1 | Spelled-out numbers EN/FR |
| `Features/Path/Spine/Logic/SpineDates.swift` (new) | D1 | Weekdays, days, ranges, times, months |
| `Features/Path/Spine/Logic/WeekOpening.swift` (new) | D1 | `WeekWords`, summary words, letters, dot days |
| `Features/Path/Spine/Logic/PeopleLines.swift` (new) | D1 | The people line |
| `Features/Path/Spine/Logic/EntryTypes.swift` (new) | D1 | Name cuts for entry, contents, card |
| `Features/Path/Spine/Logic/CardSizes.swift` (new) | D1 | Card width and height |
| `Resources/Localizable.xcstrings` | D1c, D5b | `path.spine.*`; D5b drops orphaned keys |
| `Features/Path/Spine/SpineStrings.swift` (new) | D1c | Catalogue lookup for a pinned language |
| `Features/Path/Spine/SpineCopy.swift` (new) | D1c | Typed Spine copy |
| `Features/Path/Spine/Debug/SpineFixtureWeek.swift` (new) | D1c | The web fixture week, `#if DEBUG` |
| `Features/Path/Spine/Debug/SpineFixtureMarks.swift` (new, generated) | D1c | The fixture's six glyphs |
| `Features/Path/Spine/Stone/StoneMotion.swift` (new) | D2a | Press timings, piecewise tables, frames |
| `Features/Path/Spine/Stone/StonePress.swift` (new) | D2a | A live press value and its lifetime modifier |
| `Features/Path/Spine/StonePressRequests.swift` (new) | D2a | Press requests as state; `CardFace` |
| `Features/Path/Spine/Stone/StoneRingGeometry.swift` (new) | D2a | Rings and halo about the centroid |
| `Features/Path/Spine/SpineLayout.swift` (new) | D2a | Every point constant; stone rows |
| `Features/Path/Stone/StoneView.swift`, `PebbleStoneView.swift` | D2a | `seed:` |
| `Features/Path/Stone/StoneGlintInk.swift` (new) | D2a | The glint's light (exempt folder) |
| `Features/Path/Spine/Stone/SpineStone.swift`, `StoneRings.swift`, `StoneRipple.swift`, `StoneGlint.swift` (new) | D2a | The stone in the slot |
| `Features/Path/Spine/Debug/SpineLabView.swift` (new) | D2a → D4c | The lab, one section per part |
| `Features/Profile/Sheets/SettingsSheet.swift` | D2a | "Spine lab" row |
| `apps/web/lib/theme/ios-colorsets.ts` (+ test), generated colorsets, `Shared/Theme/PebblesColors.swift` | D2b | `rakeLight`, and constant-hex sources in the generator |
| `Features/Path/Spine/SpineColors.swift` (new) | D2b, D4 | Blends with the emotion palette; the rake shadow and shadow ink per scheme |
| `Features/Path/Spine/SnapAspectCache.swift` (new) | D2b | Photo ratios from headers |
| `Features/Path/Spine/CardFrameRegistry.swift` (new) | D2b | Slot frames for stack E |
| `Features/Path/Spine/SpineSnapSource.swift` (new) | D2b | Where a card's photo comes from |
| `Features/Path/Spine/SpineCalendar.swift`, `SpineTextSpan.swift` (new) | D2b | The views' calendar; rank spans in one `Text` |
| `Features/Path/Spine/Feed/PeopleLineParts.swift`, `PeopleLineView.swift`, `SoulMark.swift`, `SpineCardField.swift`, `SpineCard.swift` (new) | D2b | The people line, the marks, the card |
| `Features/Path/Spine/Debug/SpineStoneGallery.swift`, `SpineCardGallery.swift` (new) | D2a, D2b | Lab and reference galleries |
| `Features/Path/PathModel.swift`, `Features/Path/Spine/SpinePage.swift` (new) | D3 | Path state, pages, neighbours |
| `Features/Path/Spine/Feed/SpineRakeTile.swift`, `SpineGround.swift`, `DaySeam.swift` (new) | D3 | The ground and the seams |
| `Features/Path/Spine/Feed/SpineStoneDescription.swift`, `SpineEntry.swift`, `SpinePebble.swift` (new) | D3c | VoiceOver names, entries, pebbles |
| `Features/Path/Spine/SpineWeekRows.swift`, `SpineWeekStack.swift` (new) | D3c, D4, D4c | Row order; eager layout |
| `Features/Path/Spine/Opening/*.swift` (new) | D4 | Masthead, opening, persons, dots, contents, mouth, empty week |
| `Features/Path/Spine/Stone/StoneSeal.swift` (new) | D4 | The contents' seal |
| `Features/Path/Spine/ReadingDay.swift`, `SpineOpen.swift`, `SpineWeekView.swift`, `SpinePager.swift`, `Roll/SpineWeekRoll.swift` (new) | D4c | The page, the pager, the roll |
| `RootView.swift`, `Features/Path/PathView.swift` | D5a | Registry; the Spine as the Path |
| `docs/decisions/log.md`, `apps/ios/CLAUDE.md` | D5a | Decision entry; the Path line |
| `PebblesTests/Spine/*.swift` (new) | D1 → D4c | Logic and model tests |
| `PebblesTests/References/Spine*References.swift` (new) | D2a → D5a | Reference suites |
| `PebblesTests/Spine/Fixtures/*.jpg` (new) | D2b | The five sandbox photos |
| `PebblesTests/ThemeLiteralsTests.swift` | D5b | Drops `WeekPathView`'s allowlist entry |

---
## Part D1 — `feat/1130-ios-spine-logic`

PR title: `feat(ios): spine logic, from days and people to words and card sizes`. Body opens `Part of #1130`. No view changes. The brief's D1 whole: the days and people (Tasks D1.1–D1.4), then the words and sizes (Tasks D1.5–D1.10), one commit per task.

### Task D1.0: Preflight, simulator, stack init, Arkaik pickup

**Files:** none in the repo.

- [ ] **Step 1: Check that stacks A, B and C are on `main`**

```bash
git fetch origin
git checkout main
git pull --ff-only
git log --oneline -1 main
git log --oneline main | grep -m1 "(#1174)" || echo "MISSING stack A (#1174)"
git log --oneline main | grep -m1 "(#1168)" || echo "MISSING stack B (#1168)"
test -f apps/ios/Pebbles/Theme/SpineType.swift && echo "B1 present"
test -f apps/ios/PebblesTests/References/Support/ReferenceImage.swift && echo "B2 present"
grep -q "softBackdropArt" apps/ios/Pebbles/Features/Path/Render/Wobble/WobbleRenderer.swift && echo "B3 present"
test -f apps/ios/Pebbles/Features/Tabs/PebblesTabView.swift && test -f apps/ios/Pebbles/Services/MomentsRevision.swift && echo "C1 present"
test ! -f apps/ios/Pebbles/Features/Path/Components/PathBottomBar.swift && echo "C2 present"
grep -cE "^    case (halo|stock|rule|cardRule|line|seam|teeth|ring|ripple)$" apps/ios/Shared/Theme/PebblesColors.swift
grep -c "pebblesContextMenu" apps/ios/Pebbles/Components/Menus/PebblesMenu.swift
```

Expected: no `MISSING` line; "B1 present", "B2 present", "B3 present", "C1 present", "C2 present"; the token count is `9`; the menu count is at least `1`. **If any check fails, stop and report "waiting on stack A / B / C" with the failing line.** Do not start the stack.

- [ ] **Step 2: Create the simulator if it is missing**

```bash
xcrun simctl list devices | grep -q "iPhone 17 stack-d" || xcrun simctl create "iPhone 17 stack-d" "iPhone 17"
SIM=$(xcrun simctl list devices | grep "iPhone 17 stack-d" | grep -oE '[0-9A-F]{8}(-[0-9A-F]{4}){3}-[0-9A-F]{12}' | head -1)
echo "$SIM"
xcrun simctl boot "$SIM" 2>/dev/null || true
mkdir -p "$HOME/Library/Developer/Xcode/DerivedData/Pebbles-stack-d"
```

Expected: one UDID prints.

- [ ] **Step 3: Init the stack**

```bash
git config rerere.enabled true
git config remote.pushDefault origin
gh stack init feat/1130-ios-spine-logic
git branch --show-current
```

Expected: `feat/1130-ios-spine-logic`.

- [ ] **Step 4: Baseline**

Run the **Full suite** and **Lint**. Expected: green (all reference suites already on `main` pass, since CI recorded them). If `main` is red, stop and report; do not fix `main` inside this stack.

- [ ] **Step 5: Move the Spine's nodes to `ios: development` in Arkaik**

Load the tools: ToolSearch `select:mcp__arkaik-mcp__get_node,mcp__arkaik-mcp__update_node`. If they do not load, say so, skip this step and record it for the D1 PR body. Never fall back to the bundle file.

For each of `V-timeline`, `AC-the-week-opens-with-who-was-there-and-what-happened`, `AC-path-week-roll`, `AC-a-photo-keeps-its-own-shape`:

1. `get_node(node_id)`. Keep the returned `status`, `platforms` and the whole `metadata` object (gherkin, values, `platformStatuses`, anything else).
2. `update_node` with `patch.metadata` = that **whole** object with its `platformStatuses.ios` key set to `"development"`. `update_node` replaces metadata wholesale, so every other key goes back unchanged. If the metadata has no `platformStatuses` key, send `{"platformStatuses": {<each platform in platforms>: <the node's status>, "ios": "development"}}` merged into the returned metadata.
3. Do not patch the top-level `status`.
4. `get_node` again: `ios` reads `development`; the gherkin, the values and the other platforms are byte-identical to step 1.

If the validator refuses a patch, stop and report the refusal verbatim.

---

### Task D1.1: `PathSoul` and the pebble's souls

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/PathSoul.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Models/MomentSummary.swift`
- Test: `apps/ios/PebblesTests/Spine/PathMomentDecodingTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/Spine/PathMomentDecodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1130 D2 and the foreign-payload rule: `path_moments` rows in the exact
/// shape the server emits since `20261018120000_path_moments_souls.sql`
/// (jsonb objects with `"key": value` spacing, souls ordered by name), and the
/// captured pre-#1121 rows, which keep decoding with no souls.
@Suite("path_moments souls decoding")
struct PathMomentDecodingTests {
    // swiftlint:disable line_length
    static let rows = Data(#"""
[{"id":"3f1d2c4b-5a69-4788-9a0b-1c2d3e4f5a6b","name":"Picnic on the square","happened_at":"2026-10-08T11:10:00+00:00","created_at":"2026-10-08T11:12:41.376669+00:00","first_snap_path":null,"pebble":{"id": "3f1d2c4b-5a69-4788-9a0b-1c2d3e4f5a6b", "glyph": null, "souls": [{"id": "a1b2c3d4-0000-4000-8000-000000000001", "name": "Elio", "glyph_id": "b7c8d9e0-0000-4000-8000-0000000000e1"}, {"id": "a1b2c3d4-0000-4000-8000-000000000002", "name": "Mara", "glyph_id": "b7c8d9e0-0000-4000-8000-0000000000e2"}], "emotion": {"id": "50a68890-f6bb-e67a-6655-e84ff0b71f49", "name": "Joy", "slug": "joyful"}, "intensity": 2, "visibility": "private", "positiveness": 1}},
 {"id":"4a2e3d5c-6b7a-4899-8b1c-2d3e4f5a6b7c","name":null,"happened_at":"2026-10-10T17:05:00.123456+00:00","created_at":"2026-10-10T17:05:01+00:00","first_snap_path":"907e7d76-00d3-4e53-82a3-4849bf0a9614/d49168b5-b97a-4888-a787-43ec65bb416a","pebble":{"id": "5b3f4e6d-7c8b-49aa-9c2d-3e4f5a6b7c8d", "glyph": null, "souls": [], "emotion": null, "intensity": 1, "visibility": "private", "positiveness": 0}},
 {"id":"6c4a5f7e-8d9c-4abb-8d3e-4f5a6b7c8d9e","name":"Bread from the corner bakery","happened_at":"2026-10-07T16:20:00+00:00","created_at":"2026-10-07T16:20:00+00:00","first_snap_path":null,"pebble":null}]
"""#.utf8)
    // swiftlint:enable line_length

    private func decode(_ data: Data) throws -> [MomentSummary] {
        try JSONDecoder().decode([MomentSummary].self, from: data)
    }

    @Test("souls arrive in the server's order with their glyph ids")
    func soulsInOrder() throws {
        let pebble = try #require(try decode(Self.rows)[0].pebble)
        #expect(pebble.souls.map(\.name) == ["Elio", "Mara"])
        #expect(pebble.souls[1].glyphId == UUID(uuidString: "b7c8d9e0-0000-4000-8000-0000000000e2"))
        #expect(pebble.glyph == nil)
    }

    @Test("an empty souls array, a null name, a null emotion and microseconds decode")
    func emptySouls() throws {
        let row = try decode(Self.rows)[1]
        #expect(row.name == nil)
        #expect(row.pebble?.souls == [])
        #expect(row.pebble?.emotion == nil)
        #expect(row.happenedAt > Vectors.at("2026-10-10T17:05:00Z"))
        #expect(row.createdAt == Vectors.at("2026-10-10T17:05:01Z"))
    }

    @Test("a bare moment has no pebble and so no souls")
    func bare() throws {
        let row = try decode(Self.rows)[2]
        #expect(row.pebble == nil)
        #expect(row.soulIds == [])
        #expect(!row.isPebble)
    }

    @Test("the captured pre-#1121 rows decode with no souls")
    func capturedWithoutSouls() throws {
        let rows = try decode(MomentFixtures.Captured.pathMoments)
        #expect(rows[0].pebble?.souls == [])
        #expect(rows[1].pebble == nil)
    }

    @Test("a v_moments_full soul has no glyph_id and decodes with a nil glyph")
    func detailSoulWithoutGlyph() throws {
        let row = try JSONDecoder().decode(MomentSummary.self, from: MomentFixtures.Captured.detailCompleted)
        let soul = try #require(row.pebble?.souls.first)
        #expect(soul.name == "Ana")
        #expect(soul.glyphId == nil)
    }

    @Test("the logic's ids are lowercase, as web and Android hash and tie-break on them")
    func lowercaseIds() throws {
        let row = try decode(Self.rows)[0]
        #expect(row.spineId == "3f1d2c4b-5a69-4788-9a0b-1c2d3e4f5a6b")
        #expect(row.soulIds == ["a1b2c3d4-0000-4000-8000-000000000001", "a1b2c3d4-0000-4000-8000-000000000002"])
        #expect(row.isPebble)
    }
}
```

This file uses `Vectors.at`, `spineId`, `soulIds` and `isPebble`, which Task D1.2 adds; write the vectors helper now too, so the test compiles once the code exists. Create `apps/ios/PebblesTests/Spine/SpineVectors.swift`:

```swift
import Foundation
@testable import Pebbles

/// A web vector's moment (#1130 D10): string ids, so the Vitest files copy verbatim.
struct VectorMoment: SpineMoment, Equatable {
    let spineId: String
    let happenedAt: Date
    let isPebble: Bool
    let soulIds: [String]
}

/// The Vitest environment, pinned: Europe/Paris (`apps/web/vitest.config.ts`),
/// ISO weeks, the bare `en` and `fr` locales.
enum Vectors {
    static let paris: Calendar = {
        var calendar = Calendar(identifier: .iso8601)
        guard let zone = TimeZone(identifier: "Europe/Paris") else { preconditionFailure("no Europe/Paris") }
        calendar.timeZone = zone
        return calendar
    }()
    static let en = Locale(identifier: "en")
    static let fr = Locale(identifier: "fr")

    static func at(_ iso: String) -> Date {
        guard let date = ISO8601Flexible.parse(iso) else { preconditionFailure("unparseable \(iso)") }
        return date
    }

    /// JavaScript's `new Date(year, monthIndex, day, …)` in Paris: the month is
    /// 0-based, exactly as the web files write it.
    static func local(
        _ year: Int, _ monthIndex: Int, _ day: Int, _ hour: Int = 0, _ minute: Int = 0, _ second: Int = 0
    ) -> Date {
        let parts = DateComponents(year: year, month: monthIndex + 1, day: day, hour: hour, minute: minute, second: second)
        guard let date = paris.date(from: parts) else { preconditionFailure("no date for \(parts)") }
        return date
    }

    /// The web's `isoWeekStart`.
    static func weekStart(_ date: Date) -> Date {
        WeekRollBuilder.weekStart(for: date, calendar: paris)
    }

    static func pebble(_ id: String, _ iso: String, souls: [String] = []) -> VectorMoment {
        VectorMoment(spineId: id, happenedAt: at(iso), isPebble: true, soulIds: souls)
    }

    static func bare(_ id: String, _ iso: String) -> VectorMoment {
        VectorMoment(spineId: id, happenedAt: at(iso), isPebble: false, soulIds: [])
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `PathMomentDecodingTests`.
Expected: build FAIL with "cannot find type 'SpineMoment' in scope" (and "value of type 'PebbleSummary' has no member 'souls'").

- [ ] **Step 3: Add `PathSoul`**

Create `apps/ios/Pebbles/Features/Path/Models/PathSoul.swift`:

```swift
import Foundation

/// One soul on a `path_moments` pebble (#1121): what the Spine needs to name
/// and mark a person without another read. `glyph_id` is absent on a
/// `v_moments_full` soul, so it decodes as optional.
struct PathSoul: Decodable, Hashable, Identifiable {
    let id: UUID
    let name: String
    let glyphId: UUID?

    private enum CodingKeys: String, CodingKey {
        case id, name
        case glyphId = "glyph_id"
    }

    init(id: UUID, name: String, glyphId: UUID?) {
        self.id = id
        self.name = name
        self.glyphId = glyphId
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        glyphId = try container.decodeIfPresent(UUID.self, forKey: .glyphId)
    }
}
```

- [ ] **Step 4: Give `PebbleSummary` its souls**

In `apps/ios/Pebbles/Features/Path/Models/MomentSummary.swift`, replace

```swift
        let emotion: EmotionRef?
        let glyph: PebbleGlyph?

        var valence: Valence { Valence.resolved(positiveness: positiveness, intensity: intensity) }
    }
```

with

```swift
        let emotion: EmotionRef?
        let glyph: PebbleGlyph?
        /// Ordered by name, as the server sends them (#1121). `[]` on a row
        /// from before #1121, so an older payload still decodes.
        let souls: [PathSoul]

        var valence: Valence { Valence.resolved(positiveness: positiveness, intensity: intensity) }

        fileprivate enum CodingKeys: String, CodingKey {
            case id, intensity, positiveness, visibility, emotion, glyph, souls
        }
    }
```

At the end of the file, add:

```swift
// In an extension, so the struct keeps its memberwise initialiser (the Spine's
// fixture week builds pebbles with it).
extension MomentSummary.PebbleSummary {
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        intensity = try container.decode(Int.self, forKey: .intensity)
        positiveness = try container.decode(Int.self, forKey: .positiveness)
        visibility = try container.decode(Visibility.self, forKey: .visibility)
        emotion = try container.decodeIfPresent(EmotionRef.self, forKey: .emotion)
        glyph = try container.decodeIfPresent(PebbleGlyph.self, forKey: .glyph)
        souls = try container.decodeIfPresent([PathSoul].self, forKey: .souls) ?? []
    }
}
```

`PebbleSummary` is never built with its memberwise initialiser today (`grep -rn "PebbleSummary(" apps/ios` prints nothing), so the new required `souls:` argument breaks no caller.

- [ ] **Step 5: Add the logic's moment protocol**

Create `apps/ios/Pebbles/Features/Path/Spine/Logic/SpineMoment.swift`:

```swift
import Foundation

/// What the Spine's logic reads of a moment (#1130 D10). Ids are strings, so
/// the web's vectors (`"tue-late"`, `"s0"`) copy verbatim, and lowercase,
/// because web and Android hash and tie-break on lowercase ids.
protocol SpineMoment {
    var spineId: String { get }
    var happenedAt: Date { get }
    /// A bare moment (#1050) is not a pebble: it names no one.
    var isPebble: Bool { get }
    var soulIds: [String] { get }
}

extension MomentSummary: SpineMoment {
    /// `UUID.uuidString` is uppercase; web and Android use lowercase.
    var spineId: String { id.uuidString.lowercased() }
    var isPebble: Bool { pebble != nil }
    var soulIds: [String] { pebble?.souls.map { $0.id.uuidString.lowercased() } ?? [] }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run the **Suite command** with `PathMomentDecodingTests`, then with `MomentSummaryDecodingTests`.
Expected: PASS (6), then the existing suite still PASSES.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PathSoul.swift apps/ios/Pebbles/Features/Path/Models/MomentSummary.swift apps/ios/Pebbles/Features/Path/Spine/Logic/SpineMoment.swift apps/ios/PebblesTests/Spine/PathMomentDecodingTests.swift apps/ios/PebblesTests/Spine/SpineVectors.swift
git commit -m "feat(ios): path moments carry each pebble's souls"
```

---

### Task D1.2: `LocalDays` and `SpineWeekBuilder`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Logic/LocalDays.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/Logic/SpineWeekBuilder.swift`
- Test: `apps/ios/PebblesTests/Spine/LocalDaysTests.swift`, `apps/ios/PebblesTests/Spine/SpineWeekBuilderTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/Spine/LocalDaysTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

// The DST vectors of apps/web/lib/utils/spine/persons-of-week.test.ts at a04aeec5
// (local-day.ts has no test file of its own). Europe/Paris: summer time ends
// Sunday 25 Oct 2026 at 03:00 and starts Sunday 29 Mar 2026 at 02:00.
@Suite("LocalDays (web persons-of-week DST vectors)")
struct LocalDaysTests {
    typealias V = Vectors
    static let dstWeek = V.weekStart(V.local(2026, 9, 19))
    static let springWeek = V.weekStart(V.local(2026, 2, 23))

    private func index(_ iso: String, in week: Date) -> Int {
        LocalDays.dayIndexInWeek(weekStart: week, V.at(iso), calendar: V.paris)
    }

    @Test("Monday 10:00 summer time is day 0, Thursday 23:30 is still Thursday")
    func summer() {
        #expect(index("2026-10-19T08:00:00Z", in: Self.dstWeek) == 0)
        #expect(index("2026-10-22T21:30:00Z", in: Self.dstWeek) == 3)
    }

    @Test("Sunday 23:59 on the day the clocks go back is Sunday, whole-second or fractional")
    func autumnSunday() {
        #expect(index("2026-10-25T22:59:00Z", in: Self.dstWeek) == 6)
        #expect(index("2026-10-25T22:59:59.123456+00:00", in: Self.dstWeek) == 6)
    }

    @Test("the week before and after fall outside 0…6")
    func outside() {
        #expect(index("2026-10-18T21:59:00Z", in: Self.dstWeek) == -1)
        #expect(index("2026-10-25T23:00:00Z", in: Self.dstWeek) == 7)
    }

    @Test("a week that loses an hour keeps its Sunday and not the next Monday")
    func spring() {
        #expect(index("2026-03-29T21:59:00Z", in: Self.springWeek) == 6)
        #expect(index("2026-03-29T22:30:00Z", in: Self.springWeek) == 7)
    }
}
```

Create `apps/ios/PebblesTests/Spine/SpineWeekBuilderTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/spine-week.test.ts at a04aeec5, inputs and
// expectations verbatim. TZ is Europe/Paris. Week of Monday 5 Oct 2026, the
// brief's fixture week.
@Suite("SpineWeekBuilder (web spine-week.test.ts)")
struct SpineWeekBuilderTests {
    typealias V = Vectors

    // Deliberately out of order, two on Tuesday, whole-second and fractional.
    static let week: [VectorMoment] = [
        V.pebble("tue-late", "2026-10-06T19:00:00Z"),
        V.pebble("sat", "2026-10-10T09:30:00.250+00:00"),
        V.pebble("mon", "2026-10-05T08:15:00Z"),
        V.pebble("tue-early", "2026-10-06T06:45:00.123456+00:00"),
        V.pebble("sun", "2026-10-11T21:59:00Z") // Sun 23:59 Paris
    ]
    static let w41 = V.weekStart(V.local(2026, 9, 5))

    /// The web's `weekOf`: the moments of that ISO week, laid out.
    private func weekOf<M: SpineMoment>(_ moments: [M], today: Date, weekStart: Date = w41) -> SpineWeek<M> {
        let inWeek = moments.filter { V.weekStart($0.happenedAt) == weekStart }
        return SpineWeekBuilder.spineWeek(weekStart: weekStart, moments: inWeek, today: today, calendar: V.paris)
    }

    private func read<M: SpineMoment>(_ week: SpineWeek<M>) -> [String] {
        SpineWeekBuilder.readingOrder(week).map(\.spineId)
    }

    private func day(_ date: Date) -> Int { V.paris.component(.day, from: date) }

    @Test("lays a past week out as seven days, Monday first")
    func sevenDays() {
        let week = weekOf(Self.week, today: V.local(2026, 9, 20))
        #expect(week.days.map(\.index) == [0, 1, 2, 3, 4, 5, 6])
        #expect(week.days.map { day($0.date) } == [5, 6, 7, 8, 9, 10, 11])
        #expect(week.days.allSatisfy { V.paris.component(.hour, from: $0.date) == 0 })
    }

    @Test("reads oldest first in a past week")
    func pastOrder() {
        #expect(read(weekOf(Self.week, today: V.local(2026, 9, 20))) == ["mon", "tue-early", "tue-late", "sat", "sun"])
    }

    @Test("reads oldest first in the current week too, unlike the roll")
    func currentOrder() {
        let today = V.local(2026, 9, 11, 23, 59, 30)
        // The roll keeps its newest-first current week for its other consumers…
        let summaries = Self.week.enumerated().map { index, moment in
            MomentSummary(
                id: UUID(uuidString: String(format: "00000000-0000-4000-8000-%012ld", index)) ?? UUID(),
                name: moment.spineId, happenedAt: moment.happenedAt, createdAt: moment.happenedAt,
                firstSnapPath: nil, pebble: nil
            )
        }
        let entry = WeekRollBuilder.build(moments: summaries, calendar: V.paris, today: today)
            .first { $0.weekStart == Self.w41 }
        #expect(entry?.moments.first?.name == "sun")
        // …and the Spine re-sorts it as a book.
        #expect(read(weekOf(Self.week, today: today)) == ["mon", "tue-early", "tue-late", "sat", "sun"])
    }

    @Test("marks past days with no pebble still")
    func still() {
        let week = weekOf(Self.week, today: V.local(2026, 9, 20))
        #expect(week.days.filter(\.isStill).map(\.index) == [2, 3, 4])
        #expect(week.days.filter(\.isStill).allSatisfy { $0.moments.isEmpty })
    }

    @Test("omits the current week's future days, and keeps today")
    func future() {
        let today = V.local(2026, 9, 7, 12) // Wednesday
        let week = weekOf(Self.week.filter { ["mon", "tue-early"].contains($0.spineId) }, today: today)
        #expect(week.days.map(\.index) == [0, 1, 2])
        let wednesday = week.days[2]
        #expect(wednesday.isToday && wednesday.isStill && wednesday.moments.isEmpty)
        #expect(week.days.filter(\.isToday).count == 1)
    }

    @Test("never drops a future-dated pebble")
    func futurePebble() {
        let week = weekOf([V.pebble("fri", "2026-10-09T10:00:00Z")], today: V.local(2026, 9, 7, 12))
        #expect(week.days.map(\.index) == [0, 1, 2, 4])
        #expect(week.days.last?.isStill == false)
    }

    @Test("keeps a Sunday 23:59 pebble on the Sunday the clocks go back")
    func dstSunday() {
        let w43 = V.weekStart(V.local(2026, 9, 19))
        let week = weekOf([V.pebble("dst", "2026-10-25T22:59:00Z")], today: V.local(2026, 10, 2), weekStart: w43)
        #expect(week.days[6].moments.map(\.spineId) == ["dst"])
        #expect(week.days.map { day($0.date) } == [19, 20, 21, 22, 23, 24, 25])
    }

    @Test("returns the input in chronological order when the days are read in order")
    func chronological() {
        let read = SpineWeekBuilder.readingOrder(weekOf(Array(Self.week.reversed()), today: V.local(2026, 9, 20)))
        #expect(read == Self.week.sorted { $0.happenedAt < $1.happenedAt })
        #expect(V.paris.component(.weekOfYear, from: read[0].happenedAt) == 41)
    }

    @Test("gives a bare moment its day: the day is not still")
    func bareDay() {
        let week = weekOf(Self.week + [V.bare("wed-bare", "2026-10-07T12:00:00Z")], today: V.local(2026, 9, 20))
        #expect(!week.days[2].isStill)
        #expect(week.days[2].moments.map(\.spineId) == ["wed-bare"])
        #expect(week.days.filter(\.isStill).map(\.index) == [3, 4])
    }

    @Test("reads a bare moment in its place among the pebbles")
    func bareOrder() {
        let read = read(weekOf(Self.week + [V.bare("tue-noon", "2026-10-06T10:00:00Z")], today: V.local(2026, 9, 20)))
        #expect(read == ["mon", "tue-early", "tue-noon", "tue-late", "sat", "sun"])
    }

    @Test("counts only pebbles in a week of bare moments, and draws its days anyway")
    func bareWeek() {
        let moments = [V.bare("mon", "2026-10-05T08:15:00Z"), V.bare("thu", "2026-10-08T18:00:00Z")]
        let week = weekOf(moments, today: V.local(2026, 9, 20))
        #expect(week.days.flatMap(\.moments).count == 2)
        #expect(week.pebbleCount == 0)
        #expect(week.days.filter { !$0.isStill }.map(\.index) == [0, 3])
    }

    @Test("counts the completed moments of a mixed week")
    func mixedCount() {
        let week = weekOf(Self.week + [V.bare("wed-bare", "2026-10-07T12:00:00Z")], today: V.local(2026, 9, 20))
        #expect(week.days.flatMap(\.moments).count == 6)
        #expect(week.pebbleCount == 5)
    }

    @Test("lays out what iOS and Android wrote, verbatim")
    func verbatim() throws {
        // Sunday 11 Oct 21:04 Paris (iOS photo-only, bare), then Monday 12 Oct:
        // 08:40 (Android bare, named), 10:30 (iOS completed), 20:05 (Android completed).
        let decoder = JSONDecoder()
        let moments = [
            try decoder.decode(MomentSummary.self, from: MomentFixtures.Captured.detailPhoto),
            try decoder.decode(MomentSummary.self, from: MomentFixtures.Captured.detailCompleted)
        ] + (try [MomentFixtures.Android.detailBareNamed, MomentFixtures.Android.detailCompleted]
            .map { try decoder.decode([MomentSummary].self, from: $0)[0] })
        let today = V.local(2026, 9, 14, 9)
        let w41 = weekOf(moments, today: today)
        #expect(w41.days.flatMap(\.moments).map(\.name) == [nil])
        #expect(w41.pebbleCount == 0)
        #expect(w41.days[6].moments.count == 1)

        let w42 = weekOf(moments, today: today, weekStart: V.weekStart(V.local(2026, 9, 12)))
        #expect(w42.days[0].moments.map(\.name) == [
            "Morning coffee on the balcony", "Coffee", "Evening walk with Maya"
        ])
        #expect(w42.pebbleCount == 2)
    }
}
```

- [ ] **Step 2: Add the Android rows the verbatim case decodes**

The two Android bodies are long and must stay byte-identical, so generate the Swift fixture from the web file instead of retyping it. Write this script outside the repo and run it from the repo root:

```bash
SCRATCH="${TMPDIR:-/tmp}/spine-d1a"; mkdir -p "$SCRATCH"
cat > "$SCRATCH/port-android-rows.mjs" <<'EOF'
import { readFileSync, writeFileSync } from "node:fs"
const src = readFileSync("apps/web/lib/data/__fixtures__/moments-captured.ts", "utf8")
const pick = (name) => {
  const match = src.match(new RegExp("export const " + name + " = String\\.raw`([^`]*)`"))
  if (!match) throw new Error("missing " + name)
  return match[1]
}
const swift = [
  "import Foundation",
  "@testable import Pebbles",
  "",
  "// swiftlint:disable line_length",
  "",
  "extension MomentFixtures {",
  "",
  "    /// Android's captured v_moments_full bodies, byte for byte from",
  "    /// apps/web/lib/data/__fixtures__/moments-captured.ts (generated by #1130 D1; never reformat).",
  "    enum Android {",
  "        static let detailCompleted = Data(#\"\"\"",
  pick("ANDROID_DETAIL_COMPLETED"),
  "\"\"\"#.utf8)",
  "",
  "        static let detailBareNamed = Data(#\"\"\"",
  pick("ANDROID_DETAIL_BARE_NAMED"),
  "\"\"\"#.utf8)",
  "    }",
  "}",
  "",
  "// swiftlint:enable line_length",
  "",
].join("\n")
writeFileSync("apps/ios/PebblesTests/Moments/MomentFixtures+Android.swift", swift)
EOF
node "$SCRATCH/port-android-rows.mjs"
grep -c "Evening walk with Maya" apps/ios/PebblesTests/Moments/MomentFixtures+Android.swift
grep -c "Morning coffee on the balcony" apps/ios/PebblesTests/Moments/MomentFixtures+Android.swift
```

Expected: `1` and `1`.

- [ ] **Step 3: Run the tests to verify they fail**

Run the **Suite command** with `SpineWeekBuilderTests`.
Expected: build FAIL with "cannot find 'SpineWeekBuilder' in scope".

- [ ] **Step 4: Implement `LocalDays`**

Create `apps/ios/Pebbles/Features/Path/Spine/Logic/LocalDays.swift`:

```swift
import Foundation

/// Calendar-day arithmetic in the caller's calendar and zone (#1130 D10), a port
/// of `apps/web/lib/utils/spine/local-day.ts`. Never `.current`: tests pin
/// Europe/Paris as Vitest does.
enum LocalDays {
    /// Whole calendar days from `weekStart`'s day to `date`'s (0 = Monday for an
    /// ISO Monday). Counted by the calendar, so a 23- or 25-hour day is one day.
    static func dayIndexInWeek(weekStart: Date, _ date: Date, calendar: Calendar) -> Int {
        let from = calendar.startOfDay(for: weekStart)
        let to = calendar.startOfDay(for: date)
        return calendar.dateComponents([.day], from: from, to: to).day ?? 0
    }

    /// Midnight of the calendar day `days` after `date`'s. DST-safe.
    static func adding(_ days: Int, to date: Date, calendar: Calendar) -> Date {
        let start = calendar.startOfDay(for: date)
        return calendar.date(byAdding: .day, value: days, to: start) ?? start
    }
}
```

- [ ] **Step 5: Implement `SpineWeekBuilder`**

Create `apps/ios/Pebbles/Features/Path/Spine/Logic/SpineWeekBuilder.swift`:

```swift
import Foundation

/// One day of a Spine week (#1130). `date` is midnight local; `index` 0 is Monday.
struct SpineDay<M: SpineMoment> {
    let date: Date
    let index: Int
    let moments: [M]
    /// A past or current day with no moment: a seam with *still* under it.
    let isStill: Bool
    let isToday: Bool
}

struct SpineWeek<M: SpineMoment> {
    let weekStart: Date
    let days: [SpineDay<M>]
    /// Completed moments only; a bare moment takes its place but is not counted.
    var pebbleCount: Int
}

/// The week as the Spine reads it, a port of `apps/web/lib/utils/spine/spine-week.ts`:
/// a book, so days run oldest first in every week (the current one included) and
/// moments run by `(happenedAt, spineId)` within a day. A past empty day is kept
/// and still; a day after today is dropped unless it holds a moment.
enum SpineWeekBuilder {
    static func spineWeek<M: SpineMoment>(
        weekStart: Date, moments: [M], today: Date, calendar: Calendar
    ) -> SpineWeek<M> {
        var byDay = Array(repeating: [M](), count: 7)
        for moment in moments {
            let index = LocalDays.dayIndexInWeek(weekStart: weekStart, moment.happenedAt, calendar: calendar)
            if (0...6).contains(index) { byDay[index].append(moment) }
        }
        let todayStart = calendar.startOfDay(for: today)
        var days: [SpineDay<M>] = []
        for index in 0..<7 {
            let date = LocalDays.adding(index, to: weekStart, calendar: calendar)
            let sorted = byDay[index].sorted { lhs, rhs in
                lhs.happenedAt != rhs.happenedAt ? lhs.happenedAt < rhs.happenedAt : lhs.spineId < rhs.spineId
            }
            if date > todayStart && sorted.isEmpty { continue }
            days.append(SpineDay(date: date, index: index, moments: sorted, isStill: sorted.isEmpty,
                                 isToday: date == todayStart))
        }
        let pebbleCount = byDay.joined().filter(\.isPebble).count
        return SpineWeek(weekStart: weekStart, days: days, pebbleCount: pebbleCount)
    }

    /// The week's moments in reading order: every day, top to bottom.
    static func readingOrder<M: SpineMoment>(_ week: SpineWeek<M>) -> [M] {
        week.days.flatMap(\.moments)
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run the **Suite command** with `LocalDaysTests`, then `SpineWeekBuilderTests`.
Expected: PASS (4), then PASS (13).

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Logic/LocalDays.swift apps/ios/Pebbles/Features/Path/Spine/Logic/SpineWeekBuilder.swift apps/ios/PebblesTests/Spine/LocalDaysTests.swift apps/ios/PebblesTests/Spine/SpineWeekBuilderTests.swift apps/ios/PebblesTests/Moments/MomentFixtures+Android.swift
git commit -m "feat(ios): lay a spine week out as a book, oldest first"
```

---

### Task D1.3: `PersonsOfWeek`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Logic/PersonsOfWeek.swift`
- Test: `apps/ios/PebblesTests/Spine/PersonsOfWeekTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/Spine/PersonsOfWeekTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/persons-of-week.test.ts at a04aeec5, inputs
// and expectations verbatim. The web's `soul(id, name = id)` becomes a names map.
@Suite("PersonsOfWeek (web persons-of-week.test.ts)")
struct PersonsOfWeekTests {
    typealias V = Vectors
    static let dstWeek = V.weekStart(V.local(2026, 9, 19))

    private func persons(
        _ moments: [VectorMoment], _ names: [String: String], week: Date = dstWeek, locale: Locale = Vectors.en
    ) -> [PersonOfWeek] {
        PersonsOfWeek.personsOfWeek(moments, names: names, weekStart: week, calendar: V.paris, locale: locale)
    }

    private func named(_ ids: String...) -> [String: String] {
        Dictionary(uniqueKeysWithValues: ids.map { ($0, $0) })
    }

    @Test("marks the local day of each pebble, Monday first")
    func localDay() {
        let rows = persons([
            V.pebble("a", "2026-10-19T08:00:00Z", souls: ["mara"]),
            V.pebble("b", "2026-10-22T21:30:00Z", souls: ["mara"])
        ], named("mara"))
        #expect(rows.count == 1)
        #expect(rows[0].days == [true, false, false, true, false, false, false])
        #expect(rows[0].dayCount == 2)
        #expect(rows[0].pebbleCount == 2)
    }

    @Test("keeps a pebble at 23:59 on the Sunday DST ends on that Sunday")
    func dstSunday() {
        for at in ["2026-10-25T22:59:00Z", "2026-10-25T22:59:59.123456+00:00"] {
            let rows = persons([V.pebble("a", at, souls: ["mara"])], named("mara"))
            #expect(rows.first?.days == [false, false, false, false, false, false, true])
        }
    }

    @Test("counts no one for a bare moment")
    func bare() {
        let bare = VectorMoment(spineId: "b", happenedAt: V.at("2026-10-20T08:00:00Z"), isPebble: false,
                                soulIds: ["mara"])
        let rows = persons([bare, V.pebble("a", "2026-10-19T08:00:00Z", souls: ["mara"])], named("mara"))
        #expect(rows.count == 1)
        #expect(rows[0].dayCount == 1 && rows[0].pebbleCount == 1)
        #expect(rows[0].days == [true, false, false, false, false, false, false])
    }

    @Test("leaves out pebbles of the week before or after")
    func outside() {
        let rows = persons([
            V.pebble("before", "2026-10-18T21:59:00Z", souls: ["mara"]),
            V.pebble("after", "2026-10-25T23:00:00Z", souls: ["elio"])
        ], named("mara", "elio"))
        #expect(rows.isEmpty)
    }

    @Test("buckets correctly across the spring change too")
    func spring() {
        let week = V.weekStart(V.local(2026, 2, 23))
        let rows = persons([V.pebble("a", "2026-03-29T21:59:00Z", souls: ["mara"])], named("mara"), week: week)
        #expect(rows.first?.days[6] == true)
    }

    @Test("does not pull the next Monday into a week that lost an hour")
    func springMonday() {
        let week = V.weekStart(V.local(2026, 2, 23))
        #expect(persons([V.pebble("a", "2026-03-29T22:30:00Z", souls: ["mara"])], named("mara"), week: week).isEmpty)
    }

    @Test("orders by days present, then pebbles, then name")
    func order() {
        let rows = persons([
            V.pebble("1", "2026-10-19T08:00:00Z", souls: ["zoe", "ana", "bea", "cy"]),
            V.pebble("2", "2026-10-19T09:00:00Z", souls: ["ana"]),
            V.pebble("3", "2026-10-20T08:00:00Z", souls: ["zoe"])
        ], ["zoe": "Zoé", "ana": "Ana", "bea": "Béa", "cy": "Cy"], locale: V.fr)
        #expect(rows.map(\.soulId) == ["zoe", "ana", "bea", "cy"])
    }

    @Test("collates names by locale, accents included")
    func collation() {
        let rows = persons([V.pebble("1", "2026-10-19T08:00:00Z", souls: ["e2", "e1", "z"])],
                           ["e2": "Élio", "e1": "Eva", "z": "Zack"], locale: V.fr)
        #expect(rows.map(\.name) == ["Élio", "Eva", "Zack"])
    }

    @Test("breaks a full tie on id, so the order is deterministic")
    func tie() {
        let rows = persons([V.pebble("1", "2026-10-19T08:00:00Z", souls: ["b", "a"])], ["b": "Sam", "a": "Sam"])
        #expect(rows.map(\.soulId) == ["a", "b"])
    }

    @Test("skips souls it cannot name and counts a duplicated soul once")
    func unnamedAndDuplicated() {
        let rows = persons([V.pebble("1", "2026-10-19T08:00:00Z", souls: ["mara", "mara", "deleted"])], named("mara"))
        #expect(rows.count == 1)
        #expect(rows[0].pebbleCount == 1)
    }

    @Test("split shows eight rows and folds the rest")
    func split() {
        let ids = (0..<14).map { String(format: "s%02ld", $0) }
        let rows = persons([V.pebble("1", "2026-10-19T08:00:00Z", souls: ids)],
                           Dictionary(uniqueKeysWithValues: ids.map { ($0, $0) }))
        let (shown, folded) = PersonsOfWeek.split(rows)
        #expect(PersonsOfWeek.cap == 8)
        #expect(shown.count == 8)
        #expect(folded.count == 6)
        #expect(shown + folded == rows)
    }

    @Test("split folds nothing at or under the cap")
    func splitEmpty() {
        #expect(PersonsOfWeek.split([]).folded.isEmpty)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `PersonsOfWeekTests`.
Expected: build FAIL with "cannot find 'PersonsOfWeek' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Logic/PersonsOfWeek.swift`:

```swift
import Foundation

/// One soul of the week (#1130 R15): the days they appear on, Monday first.
struct PersonOfWeek: Equatable {
    let soulId: String
    let name: String
    let days: [Bool]
    let dayCount: Int
    let pebbleCount: Int
}

/// The persons of the week, a port of `apps/web/lib/utils/spine/persons-of-week.ts`.
/// Only a pebble names people, and a soul counts once per pebble. The order
/// (days desc, pebbles desc, name by `locale` collation, then id) is the one
/// rule every surface uses, here, in each people line and on the card.
enum PersonsOfWeek {
    /// Rows shown before the rest fold into "and N others".
    static let cap = 8

    /// - Parameter names: soul id → name. A soul missing here (deleted, still
    ///   on an old pebble) has no name to set and is skipped.
    static func personsOfWeek<M: SpineMoment>(
        _ moments: [M], names: [String: String], weekStart: Date, calendar: Calendar, locale: Locale
    ) -> [PersonOfWeek] {
        var days: [String: [Bool]] = [:]
        var pebbles: [String: Int] = [:]
        for moment in moments where moment.isPebble {
            let day = LocalDays.dayIndexInWeek(weekStart: weekStart, moment.happenedAt, calendar: calendar)
            guard (0...6).contains(day) else { continue }
            var seen = Set<String>()
            for soulId in moment.soulIds where seen.insert(soulId).inserted && names[soulId] != nil {
                days[soulId, default: Array(repeating: false, count: 7)][day] = true
                pebbles[soulId, default: 0] += 1
            }
        }
        let rows = days.map { soulId, marked in
            PersonOfWeek(soulId: soulId, name: names[soulId] ?? "", days: marked,
                         dayCount: marked.filter { $0 }.count, pebbleCount: pebbles[soulId] ?? 0)
        }
        return rows.sorted { lhs, rhs in
            if lhs.dayCount != rhs.dayCount { return lhs.dayCount > rhs.dayCount }
            if lhs.pebbleCount != rhs.pebbleCount { return lhs.pebbleCount > rhs.pebbleCount }
            let byName = lhs.name.compare(rhs.name, options: [], range: nil, locale: locale)
            if byName != .orderedSame { return byName == .orderedAscending }
            // Two souls can share a name; their ids keep the order stable anyway.
            return lhs.soulId < rhs.soulId
        }
    }

    /// The first `cap` rows, and the rest behind "and N others".
    static func split(_ rows: [PersonOfWeek], cap: Int = cap) -> (shown: [PersonOfWeek], folded: [PersonOfWeek]) {
        (Array(rows.prefix(cap)), Array(rows.dropFirst(cap)))
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `PersonsOfWeekTests`.
Expected: PASS (12). If `collation` fails (for example `["Eva", "Élio", "Zack"]`), do not change the vector: record the runtime's output in the PR body and stop. The order is the cross-surface contract, and choosing another comparison is a spec question.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Logic/PersonsOfWeek.swift apps/ios/PebblesTests/Spine/PersonsOfWeekTests.swift
git commit -m "feat(ios): the persons of a spine week, in the order every surface uses"
```

---

### Task D1.4: `StableHash`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Logic/StableHash.swift`
- Test: `apps/ios/PebblesTests/Spine/StableHashTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

// The seeds of apps/web/lib/utils/spine/stone-recipe.test.ts at a04aeec5 and of
// Android's core/common/UuidHashTest.kt: FNV-1a over the lowercase id.
@Suite("StableHash")
struct StableHashTests {
    @Test("FNV-1a matches web and Android")
    func vectors() {
        #expect(StableHash.fnv1a("0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b") == 3_118_864_542)
        #expect(StableHash.fnv1a("8d1f6c3e-2b7a-4c1e-9f0a-5e6d7c8b9a01") == 3_303_075_303)
    }

    @Test("a UUID parsed from the uppercase string seeds the same as the lowercase one")
    func uppercaseUUID() throws {
        let id = try #require(UUID(uuidString: "0E9D8C7B-6A5F-4E3D-8C2B-1A0F9E8D7C6B"))
        #expect(StableHash.seed(id) == 3_118_864_542)
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/StableHashTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `StableHashTests`. Expected: build FAIL with "cannot find 'StableHash' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Logic/StableHash.swift`:

```swift
import Foundation

/// FNV-1a, 32-bit, the web's `hashUUID` (`apps/web/lib/engine/seed.ts`) and
/// Android's `UuidHash`: each pebble's crust varies the same way everywhere.
enum StableHash {
    static func fnv1a(_ text: String) -> UInt32 {
        var hash: UInt32 = 0x811C_9DC5
        for unit in text.utf16 {
            hash ^= UInt32(unit)
            hash = hash &* 0x0100_0193
        }
        return hash
    }

    /// Over the lowercase id: `UUID.uuidString` is uppercase.
    static func seed(_ id: UUID) -> UInt32 {
        fnv1a(id.uuidString.lowercased())
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `StableHashTests`. Expected: PASS (2).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Logic/StableHash.swift apps/ios/PebblesTests/Spine/StableHashTests.swift
git commit -m "feat(ios): seed each pebble's stone from its id, as web and android do"
```

---

### Task D1.5: `NumberWords`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Logic/NumberWords.swift`
- Test: `apps/ios/PebblesTests/Spine/NumberWordsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/i18n/number-words.test.ts at a04aeec5, verbatim. The
// web's `cardinalWords(2.5)` case has no Swift counterpart: `cardinal` takes an Int.
@Suite("NumberWords (web number-words.test.ts)")
struct NumberWordsTests {
    typealias V = Vectors

    @Test(arguments: [
        (0, "zero", "zéro"), (1, "one", "un"), (12, "twelve", "douze"), (17, "seventeen", "dix-sept"),
        (20, "twenty", "vingt"), (21, "twenty-one", "vingt et un"), (22, "twenty-two", "vingt-deux"),
        (31, "thirty-one", "trente et un"), (41, "forty-one", "quarante et un"),
        (53, "fifty-three", "cinquante-trois"), (60, "sixty", "soixante")
    ])
    func cardinal(_ vector: (Int, String, String)) {
        #expect(NumberWords.cardinal(vector.0, locale: V.en) == vector.1)
        #expect(NumberWords.cardinal(vector.0, locale: V.fr) == vector.2)
    }

    @Test("agrees with a feminine noun in French on the ones")
    func feminine() {
        #expect(NumberWords.cardinal(1, locale: V.fr, gender: .feminine) == "une")
        #expect(NumberWords.cardinal(21, locale: V.fr, gender: .feminine) == "vingt et une")
        #expect(NumberWords.cardinal(51, locale: V.fr, gender: .feminine) == "cinquante et une")
        #expect(NumberWords.cardinal(2, locale: V.fr, gender: .feminine) == "deux")
        #expect(NumberWords.cardinal(1, locale: V.en, gender: .feminine) == "one")
    }

    @Test("falls back to digits outside 0–60")
    func cardinalFallback() {
        #expect(NumberWords.cardinal(61, locale: V.en) == "61")
        #expect(NumberWords.cardinal(-1, locale: V.fr) == "-1")
    }

    @Test(arguments: [
        (1, "first", "premier"), (2, "second", "deuxième"), (5, "fifth", "cinquième"), (9, "ninth", "neuvième"),
        (11, "eleventh", "onzième"), (12, "twelfth", "douzième"), (20, "twentieth", "vingtième"),
        (21, "twenty-first", "vingt et unième"), (25, "twenty-fifth", "vingt-cinquième"),
        (31, "thirty-first", "trente et unième"), (39, "thirty-ninth", "trente-neuvième"),
        (40, "fortieth", "quarantième"), (41, "forty-first", "quarante et unième"),
        (44, "forty-fourth", "quarante-quatrième"), (53, "fifty-third", "cinquante-troisième")
    ])
    func ordinal(_ vector: (Int, String, String)) {
        #expect(NumberWords.ordinal(vector.0, locale: V.en) == vector.1)
        #expect(NumberWords.ordinal(vector.0, locale: V.fr) == vector.2)
    }

    @Test("sets the first week as 'la première semaine'")
    func firstWeek() {
        #expect(NumberWords.ordinal(1, locale: V.fr, gender: .feminine) == "première")
        #expect(NumberWords.ordinal(21, locale: V.fr, gender: .feminine) == "vingt et unième")
    }

    @Test("falls back to digits outside 1–53")
    func ordinalFallback() {
        #expect(NumberWords.ordinal(0, locale: V.en) == "0")
        #expect(NumberWords.ordinal(54, locale: V.fr) == "54")
    }

    @Test("reads English days as ordinals and French days as cardinals, with 'premier' for the 1st")
    func dayOfMonth() {
        #expect(NumberWords.dayOfMonth(1, locale: V.en) == "first")
        #expect(NumberWords.dayOfMonth(21, locale: V.en) == "twenty-first")
        #expect(NumberWords.dayOfMonth(31, locale: V.en) == "thirty-first")
        #expect(NumberWords.dayOfMonth(1, locale: V.fr) == "premier")
        #expect(NumberWords.dayOfMonth(5, locale: V.fr) == "cinq")
        #expect(NumberWords.dayOfMonth(21, locale: V.fr) == "vingt et un")
        #expect(NumberWords.dayOfMonth(31, locale: V.fr) == "trente et un")
        #expect(NumberWords.dayOfMonth(32, locale: V.en) == "32")
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/NumberWordsTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `NumberWordsTests`. Expected: build FAIL with "cannot find 'NumberWords' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Logic/NumberWords.swift`:

```swift
import Foundation

/// Numbers spelled out for the Spine's opening ("the forty-first week", "& twelve
/// others"), a hand port of `apps/web/lib/i18n/number-words.ts`.
/// `NumberFormatter.spellOut` cannot say "quarante et unième" or "premier".
/// Cardinals 0–60, ordinals 1–53; outside, every function falls back to digits.
/// French gender shows on 1 only in this range ("un/une", "premier/première",
/// the "et un/et une" of 21, 31, 41, 51); English ignores it.
enum NumberWords {
    enum Gender { case masculine, feminine }

    private static let enUnits = [
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen"
    ]
    private static let enTens = ["", "", "twenty", "thirty", "forty", "fifty", "sixty"]
    private static let enOrdinalUnits = [
        "zeroth", "first", "second", "third", "fourth", "fifth", "sixth", "seventh",
        "eighth", "ninth", "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth",
        "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth"
    ]
    private static let enOrdinalTens = ["", "", "twentieth", "thirtieth", "fortieth", "fiftieth", "sixtieth"]
    private static let frUnits = [
        "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
        "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept",
        "dix-huit", "dix-neuf"
    ]
    private static let frTens = ["", "", "vingt", "trente", "quarante", "cinquante", "soixante"]

    static func isFrench(_ locale: Locale) -> Bool {
        locale.language.languageCode?.identifier == "fr"
    }

    /// "twelve" / "douze".
    static func cardinal(_ n: Int, locale: Locale, gender: Gender = .masculine) -> String {
        guard (0...60).contains(n) else { return String(n) }
        return isFrench(locale) ? frCardinal(n, gender) : enCardinal(n)
    }

    /// "forty-first" / "quarante et unième". Weeks are feminine in French.
    static func ordinal(_ n: Int, locale: Locale, gender: Gender = .masculine) -> String {
        guard (1...53).contains(n) else { return String(n) }
        return isFrench(locale) ? frOrdinal(n, gender) : enOrdinal(n)
    }

    /// A day of the month as a title sets it: English reads ordinals ("the
    /// fifth"), French cardinals with "premier" for the 1st ("du premier au sept").
    static func dayOfMonth(_ day: Int, locale: Locale) -> String {
        guard (1...31).contains(day) else { return String(day) }
        if isFrench(locale) { return day == 1 ? "premier" : frCardinal(day, .masculine) }
        return enOrdinal(day)
    }

    private static func enCardinal(_ n: Int) -> String {
        if n < 20 { return enUnits[n] }
        let unit = n % 10
        return unit == 0 ? enTens[n / 10] : "\(enTens[n / 10])-\(enUnits[unit])"
    }

    private static func enOrdinal(_ n: Int) -> String {
        if n < 20 { return enOrdinalUnits[n] }
        let unit = n % 10
        return unit == 0 ? enOrdinalTens[n / 10] : "\(enTens[n / 10])-\(enOrdinalUnits[unit])"
    }

    private static func frCardinal(_ n: Int, _ gender: Gender) -> String {
        let one = gender == .feminine ? "une" : "un"
        if n == 1 { return one }
        if n < 20 { return frUnits[n] }
        let unit = n % 10
        if unit == 0 { return frTens[n / 10] }
        if unit == 1 { return "\(frTens[n / 10]) et \(one)" }
        return "\(frTens[n / 10])-\(frUnits[unit])"
    }

    /// The cardinal plus "-ième", with the few spelling changes.
    private static func frOrdinal(_ n: Int, _ gender: Gender) -> String {
        if n == 1 { return gender == .feminine ? "première" : "premier" }
        let cardinal = frCardinal(n, .masculine)
        if cardinal.hasSuffix("cinq") { return cardinal + "uième" }
        if cardinal.hasSuffix("neuf") { return String(cardinal.dropLast()) + "vième" }
        if cardinal.hasSuffix("e") { return String(cardinal.dropLast()) + "ième" }
        return cardinal + "ième"
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `NumberWordsTests`. Expected: PASS (7 tests, 32 cases with the arguments).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Logic/NumberWords.swift apps/ios/PebblesTests/Spine/NumberWordsTests.swift
git commit -m "feat(ios): spell spine numbers out in english and french"
```

---

### Task D1.6: `SpineDates`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Logic/SpineDates.swift`
- Test: `apps/ios/PebblesTests/Spine/SpineDatesTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/spine-dates.test.ts at a04aeec5, with the
// same `\s` tolerance on the range's dash. TZ is Europe/Paris.
@Suite("SpineDates (web spine-dates.test.ts)")
struct SpineDatesTests {
    typealias V = Vectors
    static let monday = V.local(2026, 9, 5)
    static let sunday = V.local(2026, 9, 11)

    @Test("shortens the weekday, lowercase and without French's period")
    func shortWeekday() {
        #expect(SpineDates.shortWeekday(Self.monday, calendar: V.paris, locale: V.en) == "mon")
        #expect(SpineDates.shortWeekday(Self.monday, calendar: V.paris, locale: V.fr) == "lun")
    }

    /// The bare locale, as iOS uses `Locale.current`'s language: "en" writes the month first.
    @Test("writes a day out in full")
    func longDay() {
        #expect(SpineDates.longDay(Self.monday, calendar: V.paris, locale: V.en) == "Monday, October 5")
        #expect(SpineDates.longDay(Self.monday, calendar: V.paris, locale: V.fr) == "lundi 5 octobre")
    }

    @Test("spans a week")
    func dayRange() throws {
        let english = SpineDates.dayRange(Self.monday, Self.sunday, calendar: V.paris, locale: V.en)
        let french = SpineDates.dayRange(Self.monday, Self.sunday, calendar: V.paris, locale: V.fr)
        #expect(english.wholeMatch(of: try Regex(#"Monday, October 5\s–\sSunday, October 11"#)) != nil, "\(english)")
        #expect(french.wholeMatch(of: try Regex(#"lundi 5\s–\sdimanche 11 octobre"#)) != nil, "\(french)")
    }

    @Test("names the month and the weekday as the opening and the dots read them")
    func names() {
        #expect(SpineDates.month(Self.monday, calendar: V.paris, locale: V.en) == "October")
        #expect(SpineDates.month(Self.monday, calendar: V.paris, locale: V.fr) == "octobre")
        #expect(SpineDates.weekday(Self.sunday, calendar: V.paris, locale: V.fr) == "dimanche")
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/SpineDatesTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `SpineDatesTests`. Expected: build FAIL with "cannot find 'SpineDates' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Logic/SpineDates.swift`:

```swift
import Foundation

/// The Spine's dates (#1130), from the locale's own patterns so weekday and
/// month names need no keys: a port of `apps/web/lib/utils/spine/spine-dates.ts`
/// plus the month, weekday and time the opening, the dots and the entries print.
/// The calendar carries the zone and the locale the language; never `.current`.
enum SpineDates {
    /// "mon", "lun": short weekday, lowercase, trailing period stripped.
    static func shortWeekday(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        var text = format(date, "EEE", calendar, locale).lowercased(with: locale)
        if text.hasSuffix(".") { text.removeLast() }
        return text
    }

    /// "Monday, October 5", "lundi 5 octobre": a seam's heading and a stone's date.
    static func longDay(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        format(date, "EEEEMMMMd", calendar, locale)
    }

    /// "Monday, October 5 – Sunday, October 11": the span a week's feed covers.
    static func dayRange(_ from: Date, _ to: Date, calendar: Calendar, locale: Locale) -> String {
        let formatter = DateIntervalFormatter()
        formatter.locale = locale
        formatter.calendar = gregorian(calendar)
        formatter.timeZone = calendar.timeZone
        formatter.dateTemplate = "EEEEMMMMd"
        return formatter.string(from: from, to: to)
    }

    /// "Monday", "lundi".
    static func weekday(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        format(date, "EEEE", calendar, locale)
    }

    /// "M", "L": the persons' day header, before lowercasing.
    static func narrowWeekday(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        format(date, "EEEEE", calendar, locale)
    }

    /// "October", "octobre".
    static func month(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        format(date, "MMMM", calendar, locale)
    }

    /// "10:15 AM", "10:15": a moment's time on the locale's clock.
    static func time(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        format(date, "jmm", calendar, locale)
    }

    // MARK: - Formatters, one per template, locale and zone

    private static let lock = NSLock()
    private static var formatters: [String: DateFormatter] = [:]

    private static func format(_ date: Date, _ template: String, _ calendar: Calendar, _ locale: Locale) -> String {
        let key = "\(template)|\(locale.identifier)|\(calendar.timeZone.identifier)"
        lock.lock()
        defer { lock.unlock() }
        if let formatter = formatters[key] { return formatter.string(from: date) }
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.calendar = gregorian(calendar)
        formatter.timeZone = calendar.timeZone
        formatter.setLocalizedDateFormatFromTemplate(template)
        formatters[key] = formatter
        return formatter.string(from: date)
    }

    /// The ISO calendar the logic counts weeks in formats like the Gregorian one,
    /// but names are read from a Gregorian calendar in the same zone to be sure.
    private static func gregorian(_ calendar: Calendar) -> Calendar {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = calendar.timeZone
        return gregorian
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `SpineDatesTests`. Expected: PASS (4).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Logic/SpineDates.swift apps/ios/PebblesTests/Spine/SpineDatesTests.swift
git commit -m "feat(ios): spine dates in the reader's language and zone"
```

---

### Task D1.7: `WeekOpening` (the words a week opens and closes with)

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Logic/WeekOpening.swift`
- Test: `apps/ios/PebblesTests/Spine/WeekOpeningTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/week-opening.test.ts at a04aeec5, verbatim.
// The web's `previous: { number }` and `next: { number, ordinal, monday }` are
// WeekWords' flat `previousNumber`, `nextNumber`, `nextOrdinal`, `nextMonday`.
@Suite("WeekOpening (web week-opening.test.ts)")
struct WeekOpeningTests {
    typealias V = Vectors
    static let w41 = V.weekStart(V.local(2026, 9, 5))

    private func words(_ weekStart: Date, _ locale: Locale) -> WeekWords {
        WeekOpening.weekWords(weekStart: weekStart, calendar: V.paris, locale: locale)
    }

    @Test("sets the brief's week in words")
    func english() {
        #expect(words(Self.w41, V.en) == WeekWords(
            number: 41, year: 2026, ordinal: "forty-first",
            title: .sameMonth(from: "fifth", to: "eleventh", month: "October"),
            previousNumber: 40, nextNumber: 42, nextOrdinal: "forty-second", nextMonday: "twelfth"
        ))
    }

    @Test("reads a French week as feminine and its days as cardinals")
    func french() {
        let french = words(Self.w41, V.fr)
        #expect(french.ordinal == "quarante et unième")
        #expect(french.title == .sameMonth(from: "cinq", to: "onze", month: "octobre"))
        #expect(french.nextNumber == 42 && french.nextOrdinal == "quarante-deuxième" && french.nextMonday == "douze")
    }

    @Test("names both months when the week crosses one")
    func crossMonth() {
        let french = words(V.weekStart(V.local(2026, 8, 28)), V.fr)
        #expect(french.title == .crossMonth(from: "vingt-huit", fromMonth: "septembre", to: "quatre", toMonth: "octobre"))
        #expect(french.nextMonday == "cinq")
    }

    @Test("takes the ISO year and wraps the week numbers at the new year")
    func newYear() {
        let french = words(V.weekStart(V.local(2026, 11, 28)), V.fr)
        #expect(french.number == 53 && french.year == 2026 && french.previousNumber == 52)
        #expect(french.nextNumber == 1 && french.nextOrdinal == "première" && french.nextMonday == "quatre")
        let english = words(V.weekStart(V.local(2025, 11, 29)), V.en)
        #expect(english.number == 1 && english.year == 2026 && english.ordinal == "first")
    }

    @Test("says premier for the first of a month in French")
    func premier() {
        guard case let .sameMonth(from, to, _) = words(V.weekStart(V.local(2027, 2, 1)), V.fr).title else {
            Issue.record("1 March 2027 to 7 March 2027 is one month")
            return
        }
        #expect(from == "premier" && to == "sept")
    }

    @Test("agrees in gender in French")
    func summaryWords() {
        #expect(WeekOpening.summaryWords(pebbles: 1, people: 1, locale: V.fr) == ("un", "une"))
        #expect(WeekOpening.summaryWords(pebbles: 21, people: 31, locale: V.fr) == ("vingt et un", "trente et une"))
        #expect(WeekOpening.summaryWords(pebbles: 11, people: 5, locale: V.en) == ("eleven", "five"))
    }

    @Test("capitalises the first letter only")
    func sentenceCase() {
        #expect(WeekOpening.sentenceCase("eleven pebbles, five people.", locale: V.en) == "Eleven pebbles, five people.")
        #expect(WeekOpening.sentenceCase("onze galets.", locale: V.fr) == "Onze galets.")
    }

    @Test("runs the weekday letters Monday first, lowercase")
    func letters() {
        #expect(WeekOpening.weekdayLetters(weekStart: Self.w41, calendar: V.paris, locale: V.en)
            == ["m", "t", "w", "t", "f", "s", "s"])
        #expect(WeekOpening.weekdayLetters(weekStart: Self.w41, calendar: V.paris, locale: V.fr)
            == ["l", "m", "m", "j", "v", "s", "d"])
    }

    @Test("names the marked days with their dates")
    func dotDays() {
        let english = WeekOpening.dotDays([true, true, false, true, false, true, false], weekStart: Self.w41,
                                          calendar: V.paris, locale: V.en)
        #expect(english == (4, "Monday 5, Tuesday 6, Thursday 8, and Saturday 10"))
        let french = WeekOpening.dotDays([false, false, false, false, false, false, true], weekStart: Self.w41,
                                         calendar: V.paris, locale: V.fr)
        #expect(french == (1, "dimanche 11"))
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/WeekOpeningTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `WeekOpeningTests`. Expected: build FAIL with "cannot find 'WeekOpening' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Logic/WeekOpening.swift`:

```swift
import Foundation

/// A week's title in words (#1130 R18).
enum WeekTitle: Equatable {
    case sameMonth(from: String, to: String, month: String)
    case crossMonth(from: String, fromMonth: String, to: String, toMonth: String)
}

/// The words a week opens and closes with, a port of
/// `apps/web/lib/utils/spine/week-opening.ts`. A week is feminine in French
/// ("la quarante et unième semaine"). `previousNumber` and `nextNumber` are the
/// calendar's: the links name the pager's neighbours instead (D9, `WeekNeighbour`),
/// and only the mouth's "begins on Monday the D" reads `nextOrdinal` / `nextMonday`.
struct WeekWords: Equatable {
    let number: Int
    let year: Int
    let ordinal: String
    let title: WeekTitle
    let previousNumber: Int
    let nextNumber: Int
    let nextOrdinal: String
    let nextMonday: String
}

enum WeekOpening {
    /// - Parameter calendar: an ISO-8601 calendar in the reader's zone.
    static func weekWords(weekStart: Date, calendar: Calendar, locale: Locale) -> WeekWords {
        let sunday = LocalDays.adding(6, to: weekStart, calendar: calendar)
        let nextMonday = LocalDays.adding(7, to: weekStart, calendar: calendar)
        let previousMonday = LocalDays.adding(-7, to: weekStart, calendar: calendar)
        let number = calendar.component(.weekOfYear, from: weekStart)
        let nextNumber = calendar.component(.weekOfYear, from: nextMonday)
        let from = NumberWords.dayOfMonth(calendar.component(.day, from: weekStart), locale: locale)
        let to = NumberWords.dayOfMonth(calendar.component(.day, from: sunday), locale: locale)
        let title: WeekTitle
        if calendar.component(.month, from: weekStart) == calendar.component(.month, from: sunday) {
            title = .sameMonth(from: from, to: to, month: SpineDates.month(sunday, calendar: calendar, locale: locale))
        } else {
            title = .crossMonth(
                from: from, fromMonth: SpineDates.month(weekStart, calendar: calendar, locale: locale),
                to: to, toMonth: SpineDates.month(sunday, calendar: calendar, locale: locale)
            )
        }
        return WeekWords(
            number: number,
            year: calendar.component(.yearForWeekOfYear, from: weekStart),
            ordinal: NumberWords.ordinal(number, locale: locale, gender: .feminine),
            title: title,
            previousNumber: calendar.component(.weekOfYear, from: previousMonday),
            nextNumber: nextNumber,
            nextOrdinal: NumberWords.ordinal(nextNumber, locale: locale, gender: .feminine),
            nextMonday: NumberWords.dayOfMonth(calendar.component(.day, from: nextMonday), locale: locale)
        )
    }

    /// Pebbles are masculine in French ("un galet"), people feminine ("une personne").
    static func summaryWords(pebbles: Int, people: Int, locale: Locale) -> (pebblesWords: String, peopleWords: String) {
        (NumberWords.cardinal(pebbles, locale: locale, gender: .masculine),
         NumberWords.cardinal(people, locale: locale, gender: .feminine))
    }

    /// "eleven pebbles…" → "Eleven pebbles…": the summary opens a sentence.
    static func sentenceCase(_ text: String, locale: Locale) -> String {
        guard let first = text.first else { return text }
        return String(first).uppercased(with: locale) + text.dropFirst()
    }

    /// "m t w t f s s", "l m m j v s d": the persons' header, Monday first.
    static func weekdayLetters(weekStart: Date, calendar: Calendar, locale: Locale) -> [String] {
        (0..<7).map { index in
            let day = LocalDays.adding(index, to: weekStart, calendar: calendar)
            return SpineDates.narrowWeekday(day, calendar: calendar, locale: locale).lowercased(with: locale)
        }
    }

    /// The days a person's dots mark, as VoiceOver reads them: "Monday 5, Tuesday 6,
    /// and Thursday 8". Joined by hand (the web's `Intl.ListFormat` with the serial
    /// comma outside French): `ListFormatter` follows the region, not the language.
    static func dotDays(_ days: [Bool], weekStart: Date, calendar: Calendar, locale: Locale) -> (count: Int, days: String) {
        let named = days.enumerated().filter { $0.element }.map { index, _ in
            let day = LocalDays.adding(index, to: weekStart, calendar: calendar)
            return "\(SpineDates.weekday(day, calendar: calendar, locale: locale)) \(calendar.component(.day, from: day))"
        }
        return (named.count, join(named, locale: locale))
    }

    private static func join(_ items: [String], locale: Locale) -> String {
        let french = NumberWords.isFrench(locale)
        let and = french ? " et " : " and "
        switch items.count {
        case 0: return ""
        case 1: return items[0]
        case 2: return items[0] + and + items[1]
        default: return items.dropLast().joined(separator: ", ") + (french ? and : "," + and) + (items.last ?? "")
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `WeekOpeningTests`. Expected: PASS (9).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Logic/WeekOpening.swift apps/ios/PebblesTests/Spine/WeekOpeningTests.swift
git commit -m "feat(ios): the words a spine week opens and closes with"
```

---

### Task D1.8: `PeopleLines`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Logic/PeopleLines.swift`
- Test: `apps/ios/PebblesTests/Spine/PeopleLineTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/people-line.test.ts at a04aeec5, verbatim,
// plus the card's `namedInFull: PeopleLines.namedOnCard` (stack E's contract).
@Suite("PeopleLines (web people-line.test.ts)")
struct PeopleLineTests {
    typealias V = Vectors
    private func ids(_ n: Int) -> [String] { (0..<n).map { "s\($0)" } }

    @Test("draws no line for a solitary pebble")
    func solitary() {
        #expect(PeopleLines.peopleLine([], order: [], locale: V.en)
            == PeopleLine(named: [], others: 0, othersWords: "", marks: [], overflow: 0))
    }

    @Test(arguments: [1, 2, 3])
    func namesAll(_ n: Int) {
        let line = PeopleLines.peopleLine(ids(n), order: ids(n), locale: V.en)
        #expect(line.named == ids(n))
        #expect(line.others == 0 && line.marks.isEmpty && line.overflow == 0)
    }

    @Test("names two and folds the rest from four")
    func four() {
        #expect(PeopleLines.peopleLine(ids(4), order: ids(4), locale: V.en)
            == PeopleLine(named: ["s0", "s1"], others: 2, othersWords: "two", marks: ["s2", "s3"], overflow: 0))
    }

    @Test("draws at most five marks at seven")
    func seven() {
        let line = PeopleLines.peopleLine(ids(7), order: ids(7), locale: V.en)
        #expect(line.named.count == 2)
        #expect(line.others == 5 && line.othersWords == "five")
        #expect(line.marks == ["s2", "s3", "s4", "s5", "s6"])
        #expect(line.overflow == 0)
    }

    @Test("reads 'with A, B & twelve others [5 marks] +7' at fourteen")
    func fourteen() {
        let line = PeopleLines.peopleLine(ids(14), order: ids(14), locale: V.en)
        #expect(line.named == ["s0", "s1"])
        #expect(line.others == 12 && line.othersWords == "twelve")
        #expect(line.marks.count == 5 && line.overflow == 7)
        #expect(PeopleLines.peopleLine(ids(14), order: ids(14), locale: V.fr).othersWords == "douze")
    }

    @Test("follows the persons-of-the-week order, not the pebble's")
    func order() {
        let line = PeopleLines.peopleLine(["elio", "mara", "nadia", "ines"], order: ["nadia", "ines", "mara", "elio"],
                                          locale: V.en)
        #expect(line.named == ["nadia", "ines"])
        #expect(line.marks == ["mara", "elio"])
    }

    @Test("keeps unranked souls after the ranked ones, in their own order")
    func unranked() {
        #expect(PeopleLines.peopleLine(["x", "mara", "y"], order: ["mara"], locale: V.en).named == ["mara", "x", "y"])
    }

    @Test("counts a duplicated soul once")
    func duplicated() {
        #expect(PeopleLines.peopleLine(["a", "a", "b"], order: ["a", "b"], locale: V.en).named == ["a", "b"])
    }

    @Test("the card names up to eight in full, then folds as the line does")
    func card() {
        #expect(PeopleLines.namedOnCard == 8)
        let eight = PeopleLines.peopleLine(ids(8), order: ids(8), locale: V.en, namedInFull: PeopleLines.namedOnCard)
        #expect(eight.named == ids(8) && eight.others == 0)
        let nine = PeopleLines.peopleLine(ids(9), order: ids(9), locale: V.en, namedInFull: PeopleLines.namedOnCard)
        #expect(nine.named == ["s0", "s1"] && nine.others == 7 && nine.overflow == 2)
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/PeopleLineTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `PeopleLineTests`. Expected: build FAIL with "cannot find 'PeopleLines' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Logic/PeopleLines.swift`:

```swift
import Foundation

/// Who a pebble was with, as one line (#1130 R7): 0 → no line · 1 → A ·
/// 2 → A & B · 3 → A, B & C · n ≥ 4 → A, B "& {n − 2 in words} others", up to
/// five marks of the others, then "+{n − 2 − 5}" when positive.
struct PeopleLine: Equatable {
    /// Soul ids set by name; empty means the pebble is solitary: no line at all.
    let named: [String]
    /// How many souls fold into "& N others" (0 when everyone is named).
    let others: Int
    /// `others` in words ("twelve"); "" when 0.
    let othersWords: String
    /// Soul ids of the others drawn as marks, at most five.
    let marks: [String]
    /// Others left after the marks, shown as "+N"; 0 means no "+".
    let overflow: Int
}

/// A port of `apps/web/lib/utils/spine/people-line.ts`.
enum PeopleLines {
    static let marks = 5
    /// The held card names up to eight souls in full (stack E passes it as `namedInFull`).
    static let namedOnCard = 8

    /// - Parameters:
    ///   - soulIds: the pebble's souls.
    ///   - order: soul ids in persons-of-the-week order; ids missing from it keep
    ///     their relative order after the ranked ones.
    ///   - namedInFull: up to this many souls are all named (the line's 3, the card's 8).
    static func peopleLine(_ soulIds: [String], order: [String], locale: Locale, namedInFull: Int = 3) -> PeopleLine {
        var rank: [String: Int] = [:]
        for (index, id) in order.enumerated() where rank[id] == nil { rank[id] = index }
        var seen = Set<String>()
        let ranked = soulIds.filter { seen.insert($0).inserted }
            .enumerated()
            .sorted { lhs, rhs in
                let left = rank[lhs.element] ?? .max
                let right = rank[rhs.element] ?? .max
                return left != right ? left < right : lhs.offset < rhs.offset
            }
            .map(\.element)
        guard ranked.count > namedInFull else {
            return PeopleLine(named: ranked, others: 0, othersWords: "", marks: [], overflow: 0)
        }
        let rest = Array(ranked.dropFirst(2))
        let marked = Array(rest.prefix(marks))
        // "others" is "autres" in French, the same for either gender above one.
        return PeopleLine(named: Array(ranked.prefix(2)), others: rest.count,
                          othersWords: NumberWords.cardinal(rest.count, locale: locale),
                          marks: marked, overflow: rest.count - marked.count)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `PeopleLineTests`. Expected: PASS (11 cases).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Logic/PeopleLines.swift apps/ios/PebblesTests/Spine/PeopleLineTests.swift
git commit -m "feat(ios): the people line, named in the persons-of-the-week order"
```

---

### Task D1.9: `EntryTypes`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Logic/EntryTypes.swift`
- Test: `apps/ios/PebblesTests/Spine/EntryTypeTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/entry-type.test.ts at a04aeec5. The phone
// column only: the `wide: true` rows are skipped (no spread on iPhone). Sizes are
// SpineType.Cut's `size`; weights and italics are the cut.
@Suite("EntryTypes (web entry-type.test.ts)")
struct EntryTypeTests {
    static let sizes: [Int: CGFloat] = [1: 19, 2: 24, 3: 31]
    static let cuts: [Int: (weight: Int, italic: Bool, tracking: CGFloat)] = [
        1: (600, false, -0.012), 0: (460, false, -0.012), -1: (340, true, -0.004)
    ]

    @Test(arguments: [1, 2, 3], [-1, 0, 1])
    func entry(intensity: Int, valence: Int) throws {
        let size = try #require(Self.sizes[intensity])
        let cut = try #require(Self.cuts[valence])
        #expect(EntryTypes.entryType(intensity: intensity, valence: valence) == EntryType(
            name: SpineType.Cut(size: size, weight: cut.weight, italic: cut.italic, trackingEm: cut.tracking),
            lineHeight: 1.12, anchor: size * 0.6
        ))
    }

    @Test("grows strictly with intensity, so size alone tells it")
    func grows() {
        let sizes = [1, 2, 3].map { EntryTypes.entryType(intensity: $0, valence: 0).name.size }
        #expect(sizes == sizes.sorted())
        #expect(Set(sizes).count == 3)
    }

    @Test("weighs contents names by intensity and sets lowlights in a light italic")
    func contents() {
        #expect([1, 2, 3].map { EntryTypes.contentsNameType(intensity: $0, valence: 1).weight } == [400, 470, 620])
        #expect([1, 2, 3].map { EntryTypes.contentsNameType(intensity: $0, valence: 0).weight } == [400, 470, 620])
        for intensity in [1, 2, 3] {
            let cut = EntryTypes.contentsNameType(intensity: intensity, valence: -1)
            #expect(cut.weight == 350 && cut.italic)
        }
        #expect(EntryTypes.contentsNameType(intensity: 2, valence: 0).size == 16.5)
    }

    @Test("cuts a card title by valence")
    func cardTitle() {
        #expect(EntryTypes.cardTitleCut(valence: 1) == SpineType.Cut(size: 4.4, weight: 600, italic: false, trackingEm: 0))
        #expect(EntryTypes.cardTitleCut(valence: 0) == SpineType.Cut(size: 4.4, weight: 480, italic: false, trackingEm: 0))
        #expect(EntryTypes.cardTitleCut(valence: -1) == SpineType.Cut(size: 4.4, weight: 360, italic: true, trackingEm: 0))
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/EntryTypeTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `EntryTypeTests`. Expected: build FAIL with "cannot find 'EntryTypes' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Logic/EntryTypes.swift`:

```swift
import CoreGraphics

/// A no-photo entry's name (#1130 R6): its cut, its line height (× size) and the
/// stone's anchor, the centre of the name's first line in points from the top.
/// `SpineType.Cut` is stack B's; this type only adds the two numbers around it.
struct EntryType: Equatable {
    let name: SpineType.Cut
    let lineHeight: CGFloat
    let anchor: CGFloat
}

/// The type ranks of a pebble's name, a port of `apps/web/lib/utils/spine/entry-type.ts`
/// (phone sizes). Intensity sets the size and valence the cut, so neither rests on
/// colour alone (A7).
enum EntryTypes {
    private static let entrySize: [Int: CGFloat] = [1: 19, 2: 24, 3: 31]
    private static let entryWeight: [Int: Int] = [1: 600, 0: 460, -1: 340]
    private static let contentsWeight: [Int: Int] = [1: 400, 2: 470, 3: 620]
    private static let cardTitleWeight: [Int: Int] = [1: 600, 0: 480, -1: 360]

    static func entryType(intensity: Int, valence: Int) -> EntryType {
        let size = entrySize[intensity] ?? 24
        let lowlight = valence == -1
        let cut = SpineType.Cut(size: size, weight: entryWeight[valence] ?? 460, italic: lowlight,
                                trackingEm: lowlight ? -0.004 : -0.012)
        return EntryType(name: cut, lineHeight: 1.12, anchor: size * 0.6)
    }

    /// A contents row's name: weight by intensity, lowlights in a light italic.
    static func contentsNameType(intensity: Int, valence: Int) -> SpineType.Cut {
        let lowlight = valence == -1
        return SpineType.Cut(size: 16.5, weight: lowlight ? 350 : (contentsWeight[intensity] ?? 470),
                             italic: lowlight, trackingEm: 0)
    }

    /// A card's title cut. Its size is in card units (4.4u); `SpineType.Style.cardTitle(u:cut:)` scales it.
    static func cardTitleCut(valence: Int) -> SpineType.Cut {
        SpineType.Cut(size: 4.4, weight: cardTitleWeight[valence] ?? 480, italic: valence == -1, trackingEm: 0)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `EntryTypeTests`. Expected: PASS (9 argument cases + 3).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Logic/EntryTypes.swift apps/ios/PebblesTests/Spine/EntryTypeTests.swift
git commit -m "feat(ios): name cuts for the spine's entries, contents and cards"
```

---

### Task D1.10: `CardSizes`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Logic/CardSizes.swift`
- Test: `apps/ios/PebblesTests/Spine/CardSizeTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/card-size.test.ts at a04aeec5: the phone
// rows of the sizing table, ±1 pt, at the web's 290 pt column. The wide column and
// caps are skipped (no spread on iPhone); the cap and pending-ratio cases use the
// phone caps instead of the wide ones.
@Suite("CardSizes (web card-size.test.ts)")
struct CardSizeTests {
    static let phone: CGFloat = 290

    // ratio → intensity → (width, height)
    static let table: [(String, CGFloat, [Int: (CGFloat, CGFloat)])] = [
        ("16:9", 16 / 9, [1: (290, 233), 2: (290, 233), 3: (290, 233)]),
        ("3:2", 3 / 2, [1: (290, 253), 2: (290, 253), 3: (290, 253)]),
        ("1:1", 1, [1: (290, 318), 2: (290, 318), 3: (290, 318)]),
        ("2:3", 2 / 3, [1: (233, 330), 2: (260, 370), 3: (280, 400)]),
        ("9:16", 9 / 16, [1: (211, 330), 2: (234, 370), 3: (251, 400)])
    ]

    @Test("matches the table on a phone")
    func table() throws {
        for (label, ratio, rows) in Self.table {
            for intensity in [1, 2, 3] {
                let (width, height) = try #require(rows[intensity])
                let size = CardSizes.cardSize(intensity: intensity, ratio: ratio, column: Self.phone)
                #expect(abs(size.width - width) <= 1, "\(label) i\(intensity) width \(size.width)")
                #expect(abs(size.height - height) <= 1, "\(label) i\(intensity) height \(size.height)")
            }
        }
    }

    @Test("never lets a fitted card exceed its cap")
    func cap() {
        for (_, ratio, _) in Self.table where ratio >= 0.5 {
            for intensity in [1, 2, 3] {
                let size = CardSizes.cardSize(intensity: intensity, ratio: ratio, column: Self.phone)
                #expect(size.height <= size.cap + 1e-9)
            }
        }
    }

    @Test("floors a very tall photo at 180 pt and lets it exceed its cap")
    func floor() {
        let size = CardSizes.cardSize(intensity: 1, ratio: 0.4, column: Self.phone)
        #expect(size.width == CardSizes.minWidth)
        #expect(size.height > size.cap)
    }

    @Test("never floors past the column itself")
    func narrowColumn() {
        #expect(CardSizes.cardSize(intensity: 1, ratio: 0.3, column: 160).width == 160)
    }

    @Test("holds a square while the ratio is unknown")
    func pending() {
        let square = CardSizes.cardSize(intensity: 2, ratio: 1, column: Self.phone)
        #expect(CardSizes.cardSize(intensity: 2, ratio: nil, column: Self.phone) == square)
        #expect(CardSizes.cardSize(intensity: 2, ratio: .nan, column: Self.phone) == square)
        #expect(CardSizes.cardSize(intensity: 2, ratio: 0, column: Self.phone) == square)
    }

    @Test("selects the cap by intensity and thickens the painted edge with it")
    func capAndEdge() {
        #expect([1, 2, 3].map(CardSizes.cap) == [330, 370, 400])
        #expect([1, 2, 3].map(CardSizes.edge) == [2, 3.5, 5.5])
    }

    @Test("the 393 pt reference column: 16:9 fills it, 2:3 sits below it")
    func referenceColumn() {
        #expect(CardSizes.cardSize(intensity: 2, ratio: 16 / 9, column: 293).width == 293)
        #expect(CardSizes.cardSize(intensity: 2, ratio: 2 / 3, column: 293).width == 260)
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/CardSizeTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CardSizeTests`. Expected: build FAIL with "cannot find 'CardSizes' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Logic/CardSizes.swift`:

```swift
import CoreGraphics

/// A card's width, approximate height and height cap, in points.
struct CardSize: Equatable {
    let width: CGFloat
    let height: CGFloat
    let cap: CGFloat
}

/// Deck-card sizing (#1130 R4), a port of `apps/web/lib/utils/spine/card-size.ts`
/// (phone caps). The chrome counts as 123 pt vertical and 95 horizontal:
/// `W = min(column, (cap − 123) × ratio + 95)`, `H = 123 + (W − 95) / ratio`, with
/// a 180 pt floor a title needs. Exact only at u = 4.4; narrower cards drift by up
/// to about 10 pt, as the rule set accepts.
enum CardSizes {
    static let chromeVertical: CGFloat = 123
    static let chromeHorizontal: CGFloat = 95
    static let minWidth: CGFloat = 180
    /// The ratio a card holds until its photo's is known.
    static let pendingRatio: CGFloat = 1

    static func cap(_ intensity: Int) -> CGFloat {
        [1: 330, 2: 370, 3: 400][intensity] ?? 370
    }

    /// The painted bottom edge's thickness.
    static func edge(_ intensity: Int) -> CGFloat {
        [1: 2, 2: 3.5, 3: 5.5][intensity] ?? 3.5
    }

    static func cardSize(intensity: Int, ratio: CGFloat?, column: CGFloat) -> CardSize {
        let cap = Self.cap(intensity)
        let safe = ratio.flatMap { $0.isFinite && $0 > 0 ? $0 : nil } ?? pendingRatio
        let fitted = min(column, (cap - chromeVertical) * safe + chromeHorizontal)
        let width = max(fitted, min(minWidth, column))
        return CardSize(width: width, height: chromeVertical + (width - chromeHorizontal) / safe, cap: cap)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `CardSizeTests`. Expected: PASS (7).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Logic/CardSizes.swift apps/ios/PebblesTests/Spine/CardSizeTests.swift
git commit -m "feat(ios): size a spine card to its photo's own ratio"
```

---

### Task D1.11: D1 verification and PR

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**. Expected: green, and `grep -rn "import SwiftUI" apps/ios/Pebbles/Features/Path/Spine/Logic || echo "logic is Foundation-only"` prints `logic is Foundation-only`.

- [ ] **Step 2: Push and submit**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
```

- [ ] **Step 3: Title, body, labels, milestone**

Body (scratch file): `Part of #1130`; key files (the file map's D1 rows); the vector mapping (web file → Swift file, all verbatim; `LocalDaysTests` uses `persons-of-week.test.ts`' DST vectors; skipped: `number-words`' 2.5 case, the `wide` rows of `entry-type` and `card-size`); the decode cases and decision 9 (D5a captures a real souls row); `dotDays`' hand join (D1 rule); the Arkaik outcome from D1.0; no Lab Note, no `F-…` ids.

```bash
gh pr edit <D1-number> --title "feat(ios): spine logic, from days and people to words and card sizes" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

---
## Part D1c — `feat/1130-ios-spine-strings`

PR title: `feat(ios): spine strings in english and french, and the fixture week`. Body opens `Part of #1130`. No view changes. About 700 of its lines are catalogue JSON (decision 3).

### Task D1c.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1130-ios-spine-strings
git branch --show-current
```

Expected: `feat/1130-ios-spine-strings`.

---

### Task D1c.1: The `path.spine.*` catalogue entries

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

The 33 keys are the spec's §Strings table, plus `path.spine.summaryNoPebbles` (decision 8). Plurals are xcstrings substitutions on argument 1 (`%1$#@name@`, `argNum` 1, `lld`), so the words stay outside the plural and only the noun varies; French carries `one`, `many` and `other`, with `many` equal to `other`. Writing ~700 lines of JSON by hand invites a typo, so a script emits the block in Xcode's exact format and splices it **as text** at the anchor. It never parses and re-serialises the catalogue.

- [ ] **Step 1: Confirm the anchor and the collision check**

```bash
grep -n '^    "Path" : {$' apps/ios/Pebbles/Resources/Localizable.xcstrings
grep -n '^    "Pebble created" : {$' apps/ios/Pebbles/Resources/Localizable.xcstrings
grep -c '"path.spine' apps/ios/Pebbles/Resources/Localizable.xcstrings
```

Expected: one line each for `Path` and `Pebble created`, and `0`. The keys sort case-insensitively, as Xcode writes them, so every `path.spine.*` key falls between `Path` and `Pebble created`.

- [ ] **Step 2: Write and run the splice**

```bash
SCRATCH="${TMPDIR:-/tmp}/spine-d1c"; mkdir -p "$SCRATCH"
cat > "$SCRATCH/insert-spine-strings.mjs" <<'EOF'
import { readFileSync, writeFileSync } from "node:fs"

const path = "apps/ios/Pebbles/Resources/Localizable.xcstrings"
const text = readFileSync(path, "utf8")
const anchor = '    "Pebble created" : {\n'
if (text.split(anchor).length !== 2) throw new Error("the anchor must occur exactly once")
if (text.includes('"path.spine.')) throw new Error("path.spine keys are already in the catalogue")
const before = text.slice(0, text.indexOf(anchor)).trimEnd().split("\n")
const previousKey = [...before].reverse().find((line) => /^    ".*" : \{$/.test(line))
if (previousKey !== '    "Path" : {') throw new Error("unexpected key before the anchor: " + previousKey)

const plain = {
  "path.spine.kicker": ["the %1$@ week · %2$lld", "la %1$@ semaine · %2$lld"],
  "path.spine.titleSameMonth": ["The %1$@ to the %2$@ of %3$@", "Du %1$@ au %2$@ %3$@"],
  "path.spine.titleCrossMonth": ["The %1$@ of %2$@ to the %3$@ of %4$@", "Du %1$@ %2$@ au %3$@ %4$@"],
  "path.spine.summaryNoPebbles": ["no pebbles", "aucun galet"],
  "path.spine.persons": ["persons of the week", "les personnes de la semaine"],
  "path.spine.contents": ["contents", "sommaire"],
  "path.spine.with": ["with", "avec"],
  "path.spine.withNames": ["With %@.", "Avec %@."],
  "path.spine.still": ["still", "calme"],
  "path.spine.untitled": ["untitled", "sans titre"],
  "path.spine.photoUnavailable": ["photo unavailable", "photo indisponible"],
  "path.spine.weekEnds": ["the %@ week ends here", "la %@ semaine s'arrête ici"],
  "path.spine.nextBegins": ["The %1$@ begins on Monday the %2$@.", "La %1$@ commence le lundi %2$@."],
  "path.spine.weekLink": ["week %lld", "semaine %lld"],
  "path.spine.stoneA11y": [
    "%1$@. %2$@, %3$@. %4$@, %5$@. %6$@ Opens the pebble.",
    "%1$@. %2$@, %3$@. %4$@, %5$@. %6$@ Ouvre le galet.",
  ],
  "path.spine.blankA11y": [
    "%1$@. %2$@, %3$@. Not a pebble yet. %4$@ Opens the moment.",
    "%1$@. %2$@, %3$@. Pas encore un galet. %4$@ Ouvre le moment.",
  ],
  "path.spine.contentsHint": ["Scrolls to and presses the stone", "Fait défiler jusqu'au galet et le presse"],
  "path.spine.feedA11y": ["The week, %@", "La semaine, %@"],
  "path.spine.glanceA11y": ["The week at a glance", "La semaine en un coup d'œil"],
  "path.spine.stone.small.lowlight": ["Small lava stone", "Petite pierre de lave"],
  "path.spine.stone.small.neutral": ["Small river pebble", "Petit galet de rivière"],
  "path.spine.stone.small.highlight": ["Small gem", "Petite gemme"],
  "path.spine.stone.medium.lowlight": ["Medium lava stone", "Pierre de lave moyenne"],
  "path.spine.stone.medium.neutral": ["Medium river pebble", "Galet de rivière moyen"],
  "path.spine.stone.medium.highlight": ["Medium gem", "Gemme moyenne"],
  "path.spine.stone.large.lowlight": ["Large lava stone", "Grande pierre de lave"],
  "path.spine.stone.large.neutral": ["Large river pebble", "Grand galet de rivière"],
  "path.spine.stone.large.highlight": ["Large gem", "Grande gemme"],
}

// key → [substitution name, en value, en forms, fr value, fr forms]
const plural = {
  "path.spine.summaryPebbles": ["pebbles", "%2$@ %1$#@pebbles@", { one: "pebble", other: "pebbles" },
    "%2$@ %1$#@pebbles@", { one: "galet", many: "galets", other: "galets" }],
  "path.spine.summaryPeople": ["people", ", %2$@ %1$#@people@", { one: "person", other: "people" },
    ", %2$@ %1$#@people@", { one: "personne", many: "personnes", other: "personnes" }],
  "path.spine.personsMore": ["others", "and %2$@ %1$#@others@", { one: "other", other: "others" },
    "et %2$@ %1$#@others@", { one: "autre", many: "autres", other: "autres" }],
  "path.spine.others": ["others", "& %2$@ %1$#@others@", { one: "other", other: "others" },
    "& %2$@ %1$#@others@", { one: "autre", many: "autres", other: "autres" }],
  "path.spine.dayDots": ["days", "%1$#@days@ %2$@", { one: "On one day:", other: "On %arg days:" },
    "%1$#@days@ %2$@", { one: "Un jour :", many: "%arg jours :", other: "%arg jours :" }],
}

const unit = (value) => ({ stringUnit: { state: "translated", value } })
const entry = (en, fr) => ({ extractionState: "manual", localizations: { en: unit(en), fr: unit(fr) } })
const forms = (map) => Object.fromEntries(Object.entries(map).map(([k, v]) => [k, unit(v)]))
const substituted = (value, name, map) => ({
  ...unit(value),
  substitutions: { [name]: { argNum: 1, formatSpecifier: "lld", variations: { plural: forms(map) } } },
})
const pluralEntry = ([name, en, enForms, fr, frForms]) => ({
  extractionState: "manual",
  localizations: { en: substituted(en, name, enForms), fr: substituted(fr, name, frForms) },
})

// Xcode's layout: two-space indent, `"key" : value`, keys sorted within an object.
const emit = (value, indent) => {
  if (typeof value === "string") return JSON.stringify(value)
  if (typeof value === "number") return String(value)
  const pad = " ".repeat(indent + 2)
  const body = Object.keys(value).sort().map((k) => `${pad}${JSON.stringify(k)} : ${emit(value[k], indent + 2)}`)
  return "{\n" + body.join(",\n") + "\n" + " ".repeat(indent) + "}"
}

const entries = [
  ...Object.entries(plain).map(([k, [en, fr]]) => [k, entry(en, fr)]),
  ...Object.entries(plural).map(([k, spec]) => [k, pluralEntry(spec)]),
].sort(([a], [b]) => (a.toLowerCase() < b.toLowerCase() ? -1 : 1))

const block = entries.map(([key, value]) => `    ${JSON.stringify(key)} : ${emit(value, 4)},\n`).join("")
writeFileSync(path, text.replace(anchor, block + anchor))
console.log(entries.length + " keys inserted")
EOF
node "$SCRATCH/insert-spine-strings.mjs"
git diff --stat -- apps/ios/Pebbles/Resources/Localizable.xcstrings
git diff -- apps/ios/Pebbles/Resources/Localizable.xcstrings | grep -c '^-[^-]' || true
python3 -c "import json; json.load(open('apps/ios/Pebbles/Resources/Localizable.xcstrings')); print('valid json')"
```

Expected: `33 keys inserted`; the diff stat shows only insertions; the removed-line count prints `0`; `valid json`. The last check only reads the file (it never writes).

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): the spine's strings in english and french"
```

---

### Task D1c.2: `SpineStrings` and `SpineCopy`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/SpineStrings.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/SpineCopy.swift`
- Test: `apps/ios/PebblesTests/Spine/SpineStringsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1130 §Strings: every `path.spine.*` key resolves in English and French, the
/// plurals choose their noun, the zero branches read their own words, and the
/// stone's sentence closes the gap an empty "with" leaves.
@Suite("Spine strings")
struct SpineStringsTests {
    typealias V = Vectors

    @Test("the kicker and the titles read the web's sentences")
    func opening() {
        #expect(SpineCopy.kicker(ordinal: "forty-first", year: 2026, locale: V.en) == "the forty-first week · 2026")
        #expect(SpineCopy.kicker(ordinal: "quarante et unième", year: 2026, locale: V.fr)
            == "la quarante et unième semaine · 2026")
        #expect(SpineCopy.title(.sameMonth(from: "fifth", to: "eleventh", month: "October"), locale: V.en)
            == "The fifth to the eleventh of October")
        #expect(SpineCopy.title(.sameMonth(from: "cinq", to: "onze", month: "octobre"), locale: V.fr)
            == "Du cinq au onze octobre")
        #expect(SpineCopy.title(.crossMonth(from: "vingt-huit", fromMonth: "septembre", to: "quatre", toMonth: "octobre"),
                                locale: V.fr) == "Du vingt-huit septembre au quatre octobre")
    }

    @Test("the summary pluralises its nouns and reads zero as words")
    func summary() {
        #expect(SpineCopy.summary(pebbles: 11, people: 5, locale: V.en) == "Eleven pebbles, five people.")
        #expect(SpineCopy.summary(pebbles: 1, people: 0, locale: V.en) == "One pebble.")
        #expect(SpineCopy.summary(pebbles: 0, people: 0, locale: V.en) == "No pebbles.")
        #expect(SpineCopy.summary(pebbles: 1, people: 1, locale: V.fr) == "Un galet, une personne.")
        #expect(SpineCopy.summary(pebbles: 2, people: 0, locale: V.fr) == "Deux galets.")
        #expect(SpineCopy.summary(pebbles: 0, people: 3, locale: V.fr) == "Aucun galet, trois personnes.")
    }

    @Test("the folds and the others choose their plural")
    func folds() {
        #expect(SpineCopy.others(count: 12, words: "twelve", locale: V.en) == "& twelve others")
        #expect(SpineCopy.others(count: 1, words: "one", locale: V.en) == "& one other")
        #expect(SpineCopy.others(count: 12, words: "douze", locale: V.fr) == "& douze autres")
        #expect(SpineCopy.personsMore(count: 6, locale: V.en) == "and six others")
        #expect(SpineCopy.personsMore(count: 1, locale: V.fr) == "et un autre")
    }

    @Test("the day dots say how many days, then which")
    func dayDots() {
        #expect(SpineCopy.dayDots(count: 1, days: "Sunday 11", locale: V.en) == "On one day: Sunday 11")
        #expect(SpineCopy.dayDots(count: 4, days: "Monday 5, Tuesday 6, Thursday 8, and Saturday 10", locale: V.en)
            == "On 4 days: Monday 5, Tuesday 6, Thursday 8, and Saturday 10")
        #expect(SpineCopy.dayDots(count: 1, days: "dimanche 11", locale: V.fr) == "Un jour : dimanche 11")
        #expect(SpineCopy.dayDots(count: 3, days: "lundi 5, mardi 6 et jeudi 8", locale: V.fr)
            == "3 jours : lundi 5, mardi 6 et jeudi 8")
    }

    @Test("the links, the mouth and the still day")
    func links() {
        #expect(SpineCopy.weekLink(35, locale: V.en) == "week 35")
        #expect(SpineCopy.weekLink(35, locale: V.fr) == "semaine 35")
        #expect(SpineCopy.weekEnds(ordinal: "forty-first", locale: V.en) == "the forty-first week ends here")
        #expect(SpineCopy.nextBegins(ordinal: "forty-second", day: "twelfth", locale: V.en)
            == "The forty-second begins on Monday the twelfth.")
        #expect(SpineCopy.nextBegins(ordinal: "quarante-deuxième", day: "douze", locale: V.fr)
            == "La quarante-deuxième commence le lundi douze.")
        #expect(SpineCopy.still(locale: V.fr) == "calme")
    }

    @Test("the stone names itself, and an empty with-names leaves no double space")
    func stone() {
        #expect(SpineCopy.stoneA11y(name: "Flag march with Nadia", date: "Monday, October 5", time: "10:15 AM",
                                    kind: "Large gem", emotion: "Proud", withNames: "With Nadia.", locale: V.en)
            == "Flag march with Nadia. Monday, October 5, 10:15 AM. Large gem, Proud. With Nadia. Opens the pebble.")
        #expect(SpineCopy.stoneA11y(name: "Rent went up again", date: "Tuesday, October 6", time: "8:02 AM",
                                    kind: "Medium lava stone", emotion: "Angry", withNames: "", locale: V.en)
            == "Rent went up again. Tuesday, October 6, 8:02 AM. Medium lava stone, Angry. Opens the pebble.")
        #expect(SpineCopy.blankA11y(name: "Bread", date: "mercredi 7 octobre", time: "18:20", withNames: "",
                                    locale: V.fr) == "Bread. mercredi 7 octobre, 18:20. Pas encore un galet. Ouvre le moment.")
        #expect(SpineCopy.stoneKind(.highlightLarge, locale: V.en) == "Large gem")
        #expect(SpineCopy.stoneKind(.lowlightSmall, locale: V.fr) == "Petite pierre de lave")
    }

    @Test("every path.spine key has an English and a French value, and French says galet")
    func catalogue() throws {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("Pebbles/Resources/Localizable.xcstrings")
        let root = try #require(try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try #require(root["strings"] as? [String: Any])
        let keys = strings.keys.filter { $0.hasPrefix("path.spine.") }
        #expect(keys.count == 33)
        for key in keys {
            let entry = try #require(strings[key] as? [String: Any])
            let localizations = try #require(entry["localizations"] as? [String: Any], "\(key)")
            #expect(localizations["en"] != nil && localizations["fr"] != nil, "\(key) needs en and fr")
            let french = String(describing: localizations["fr"] ?? "").lowercased()
            #expect(!french.contains("caillou") && !french.contains("pebble"), "\(key)")
        }
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/SpineStringsTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `SpineStringsTests`. Expected: build FAIL with "cannot find 'SpineCopy' in scope".

- [ ] **Step 3: Implement `SpineStrings`**

Create `apps/ios/Pebbles/Features/Path/Spine/SpineStrings.swift`:

```swift
import Foundation
import os

/// Spine copy from `Localizable.xcstrings`, for one language (#1130 §Strings).
/// Keys are explicit and dotted (`path.spine.kicker`) and the language is
/// passed in, so tests pin `en` / `fr` as the logic does. Views pass
/// `appLocale`, the language `String(localized:)` resolves (B2's French pass
/// flips it).
enum SpineStrings {
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "spine")
    private static let missing = "\u{0}missing"

    static var appLocale: Locale {
        Locale(identifier: Bundle.main.preferredLocalizations.first ?? "en")
    }

    /// The raw format for `key`, or the key itself, logged, when the catalogue misses it.
    static func string(_ key: String, locale: Locale) -> String {
        let language = locale.language.languageCode?.identifier ?? "en"
        let bundle = Bundle.main.path(forResource: language, ofType: "lproj").flatMap(Bundle.init(path:)) ?? .main
        let value = bundle.localizedString(forKey: key, value: missing, table: nil)
        guard value != missing else {
            logger.error("spine string missing: \(key, privacy: .public) [\(language, privacy: .public)]")
            return key
        }
        return value
    }

    /// The format with its arguments, plurals resolved by the catalogue's substitutions.
    static func format(_ key: String, locale: Locale, _ arguments: CVarArg...) -> String {
        String(format: string(key, locale: locale), locale: locale, arguments: arguments)
    }
}
```

- [ ] **Step 4: Implement `SpineCopy`**

Create `apps/ios/Pebbles/Features/Path/Spine/SpineCopy.swift`:

```swift
import Foundation

/// The Spine's copy, typed: one function per `path.spine.*` key, so no view
/// spells a key or an argument order. Counts that drive a plural come first.
enum SpineCopy {
    static func kicker(ordinal: String, year: Int, locale: Locale) -> String {
        SpineStrings.format("path.spine.kicker", locale: locale, ordinal, year)
    }

    static func title(_ title: WeekTitle, locale: Locale) -> String {
        switch title {
        case let .sameMonth(from, to, month):
            return SpineStrings.format("path.spine.titleSameMonth", locale: locale, from, to, month)
        case let .crossMonth(from, fromMonth, to, toMonth):
            return SpineStrings.format("path.spine.titleCrossMonth", locale: locale, from, fromMonth, to, toMonth)
        }
    }

    /// "Eleven pebbles, five people." The web's ICU `=0` branches are explicit
    /// here: no pebbles reads its own words, no people drops the clause.
    static func summary(pebbles: Int, people: Int, locale: Locale) -> String {
        let words = WeekOpening.summaryWords(pebbles: pebbles, people: people, locale: locale)
        let pebblesPart = pebbles == 0
            ? SpineStrings.string("path.spine.summaryNoPebbles", locale: locale)
            : SpineStrings.format("path.spine.summaryPebbles", locale: locale, pebbles, words.pebblesWords)
        let peoplePart = people == 0
            ? ""
            : SpineStrings.format("path.spine.summaryPeople", locale: locale, people, words.peopleWords)
        return WeekOpening.sentenceCase(pebblesPart + peoplePart + ".", locale: locale)
    }

    static func persons(locale: Locale) -> String { SpineStrings.string("path.spine.persons", locale: locale) }

    /// "and six others": the persons' fold.
    static func personsMore(count: Int, locale: Locale) -> String {
        SpineStrings.format("path.spine.personsMore", locale: locale, count, NumberWords.cardinal(count, locale: locale))
    }

    static func contents(locale: Locale) -> String { SpineStrings.string("path.spine.contents", locale: locale) }
    static func withWord(locale: Locale) -> String { SpineStrings.string("path.spine.with", locale: locale) }

    /// "With Mara and Elio." for VoiceOver; `names` is already listed for the locale.
    static func withNames(_ names: String, locale: Locale) -> String {
        names.isEmpty ? "" : SpineStrings.format("path.spine.withNames", locale: locale, names)
    }

    /// "& twelve others" in a people line.
    static func others(count: Int, words: String, locale: Locale) -> String {
        SpineStrings.format("path.spine.others", locale: locale, count, words)
    }

    static func still(locale: Locale) -> String { SpineStrings.string("path.spine.still", locale: locale) }
    static func untitled(locale: Locale) -> String { SpineStrings.string("path.spine.untitled", locale: locale) }
    static func photoUnavailable(locale: Locale) -> String {
        SpineStrings.string("path.spine.photoUnavailable", locale: locale)
    }

    static func weekEnds(ordinal: String, locale: Locale) -> String {
        SpineStrings.format("path.spine.weekEnds", locale: locale, ordinal)
    }

    static func nextBegins(ordinal: String, day: String, locale: Locale) -> String {
        SpineStrings.format("path.spine.nextBegins", locale: locale, ordinal, day)
    }

    /// "week 35": the masthead's and the mouth's link, naming the pager's neighbour (D9).
    static func weekLink(_ number: Int, locale: Locale) -> String {
        SpineStrings.format("path.spine.weekLink", locale: locale, number)
    }

    static func dayDots(count: Int, days: String, locale: Locale) -> String {
        SpineStrings.format("path.spine.dayDots", locale: locale, count, days)
    }

    /// The stone's and the card's name (A2, A3). An empty `withNames` leaves two
    /// spaces in the template; they close to one.
    static func stoneA11y(
        name: String, date: String, time: String, kind: String, emotion: String, withNames: String, locale: Locale
    ) -> String {
        tidy(SpineStrings.format("path.spine.stoneA11y", locale: locale, name, date, time, kind, emotion, withNames))
    }

    static func blankA11y(name: String, date: String, time: String, withNames: String, locale: Locale) -> String {
        tidy(SpineStrings.format("path.spine.blankA11y", locale: locale, name, date, time, withNames))
    }

    static func contentsHint(locale: Locale) -> String { SpineStrings.string("path.spine.contentsHint", locale: locale) }

    static func feedA11y(range: String, locale: Locale) -> String {
        SpineStrings.format("path.spine.feedA11y", locale: locale, range)
    }

    static func glanceA11y(locale: Locale) -> String { SpineStrings.string("path.spine.glanceA11y", locale: locale) }

    /// "Large gem", "Petite pierre de lave": the stone's kind by its size and polarity.
    static func stoneKind(_ valence: Valence, locale: Locale) -> String {
        SpineStrings.string("path.spine.stone.\(valence.sizeGroup.rawValue).\(valence.polarity.rawValue)", locale: locale)
    }

    private static func tidy(_ text: String) -> String {
        text.replacingOccurrences(of: "  ", with: " ")
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run the **Suite command** with `SpineStringsTests`. Expected: PASS (7).

If only the plural cases fail (for example `"& twelve %1$#@others@"`, or `"On %arg days: …"`), Foundation did not resolve the substitution the way the catalogue is written. Do not edit the catalogue by hand: open it in Xcode, select `path.spine.others`, use "Vary by Plural" on argument 1 for `en` and `fr`, let Xcode rewrite the five plural entries, re-run, and record the shape Xcode chose in this plan's Lessons learned. Every other case must already pass.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/SpineStrings.swift apps/ios/Pebbles/Features/Path/Spine/SpineCopy.swift apps/ios/PebblesTests/Spine/SpineStringsTests.swift
git commit -m "feat(ios): typed spine copy over the catalogue, for a pinned language"
```

---

### Task D1c.3: The fixture week

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineFixtureMarks.swift` (generated)
- Create: `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineFixtureWeek.swift`
- Test: `apps/ios/PebblesTests/Spine/SpineFixtureWeekTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// The port of apps/web/lib/seed/spine-fixture-week.ts takes every branch the
/// product takes (the web file's own list), and reads like the web's week.
@Suite("Spine fixture week")
struct SpineFixtureWeekTests {
    typealias F = SpineFixtureWeek

    private var week: SpineWeek<MomentSummary> {
        SpineWeekBuilder.spineWeek(weekStart: F.weekStart, moments: F.moments, today: F.today, calendar: F.calendar)
    }

    @Test("Monday 5 to Sunday 11 October 2026, every day drawn, Friday still")
    func days() {
        #expect(F.calendar.component(.day, from: F.weekStart) == 5)
        #expect(week.days.map(\.index) == [0, 1, 2, 3, 4, 5, 6])
        #expect(week.days.filter(\.isStill).map(\.index) == [4])
        #expect(week.days[6].isToday)
    }

    @Test("fifteen moments, thirteen pebbles, two not pebbles yet")
    func counts() {
        #expect(F.moments.count == 15)
        #expect(week.pebbleCount == 13)
        #expect(F.moments.filter { $0.pebble == nil }.map(\.name) == ["Bread from the corner bakery", nil])
    }

    @Test("the picnic names fourteen souls, Bruno's stone is uncarved, one photo is untitled")
    func branches() throws {
        let picnic = try #require(F.moments.first { $0.name == "Picnic on the square, the whole street" })
        #expect(picnic.soulIds.count == 14)
        let bruno = try #require(F.moments.first { $0.name == "Bruno asleep on my feet" })
        #expect(bruno.pebble?.glyph == nil)
        let untitled = try #require(F.moments.first { $0.name == nil && $0.pebble != nil })
        #expect(untitled.firstSnapPath == "fixture/snap-fountain.jpg")
    }

    @Test("the persons of the week read as on the web")
    func persons() {
        let rows = PersonsOfWeek.personsOfWeek(F.moments, names: F.soulNames, weekStart: F.weekStart,
                                               calendar: F.calendar, locale: Vectors.en)
        #expect(rows.map(\.name) == [
            "Mara", "Bruno", "Elio", "Jules", "Nadia",
            "Ana", "Béa", "Cyril", "Dora", "Ezra", "Félix", "Gaby", "Hugo", "Inès"
        ])
        #expect(rows[0].dayCount == 4)
    }

    @Test("every photo has a primed ratio and every carved stone a palette and strokes")
    func data() {
        for path in F.moments.compactMap(\.firstSnapPath) { #expect(F.photos[path] != nil, "\(path)") }
        for moment in F.moments where moment.pebble != nil { #expect(F.palette(for: moment) != nil) }
        #expect(F.moments.compactMap { $0.pebble?.glyph?.strokes.isEmpty }.allSatisfy { !$0 })
        #expect(F.soulGlyphs.count == 14)
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/SpineFixtureWeekTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `SpineFixtureWeekTests`. Expected: build FAIL with "cannot find 'SpineFixtureWeek' in scope".

- [ ] **Step 3: Generate the six marks from the web fixture**

```bash
SCRATCH="${TMPDIR:-/tmp}/spine-d1c"; mkdir -p "$SCRATCH"
cat > "$SCRATCH/port-spine-marks.mjs" <<'EOF'
import { readFileSync, writeFileSync } from "node:fs"
const src = readFileSync("apps/web/lib/seed/spine-fixture-week.ts", "utf8")
const block = /id: "glyph-(\w+)",[\s\S]*?strokes: \[([\s\S]*?)\],\s*created_at/g
const stroke = /\{ d: "([^"]+)", width: ([\d.]+) \}/g
const marks = [...src.matchAll(block)].map(([, name, body]) => [name, [...body.matchAll(stroke)].map(([, d, w]) => [d, w])])
if (marks.length !== 6) throw new Error("expected 6 marks, found " + marks.length)
const lines = [
  "#if DEBUG",
  "// Generated from apps/web/lib/seed/spine-fixture-week.ts (SPINE_FIXTURE_MARKS) by #1130 D1c. Do not edit.",
  "// swiftlint:disable line_length",
  "",
  "/// The fixture week's six glyphs, 200 × 200, engine stroke widths.",
  "enum SpineFixtureMarks {",
  "    static let strokes: [String: [GlyphStroke]] = [",
  ...marks.map(([name, strokes]) => [
    `        "${name}": [`,
    ...strokes.map(([d, w]) => `            GlyphStroke(d: "${d}", width: ${w}),`),
    "        ],",
  ].join("\n")),
  "    ]",
  "}",
  "",
  "// swiftlint:enable line_length",
  "#endif",
  "",
]
writeFileSync("apps/ios/Pebbles/Features/Path/Spine/Debug/SpineFixtureMarks.swift", lines.join("\n"))
console.log(marks.map(([n, s]) => n + ":" + s.length).join(" "))
EOF
mkdir -p apps/ios/Pebbles/Features/Path/Spine/Debug
node "$SCRATCH/port-spine-marks.mjs"
```

Expected: `identity:12 travel:12 friends:5 family:9 hobbies:5 partner:5`.

- [ ] **Step 4: Write the fixture week**

Create `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineFixtureWeek.swift`:

```swift
#if DEBUG
import CoreGraphics
import Foundation

/// The Spine's fixture week (#1130), a port of `apps/web/lib/seed/spine-fixture-week.ts`:
/// Monday 5 to Sunday 11 October 2026 in Europe/Paris, today Sunday 11 at 23:00.
/// It takes every branch the product takes: a still Friday, Bruno's uncarved
/// stone, a fourteen-person picnic, an untitled photo, a named bare moment set
/// as type and an untitled bare photo. Ids are fixed UUIDs (moment n is
/// `…-8000-0000000000nn`), and each pebble keeps its moment's id, as backfilled
/// rows do. The palettes are the web sandbox's `CATEGORY_PALETTES`.
enum SpineFixtureWeek {
    static let calendar: Calendar = {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris") ?? .gmt
        return calendar
    }()
    static let today = date("2026-10-11T23:00:00+02:00")
    static var weekStart: Date { WeekRollBuilder.weekStart(for: today, calendar: calendar) }

    // MARK: - Souls

    private static let soulTable: [(key: String, name: String, mark: String)] = [
        ("mara", "Mara", "partner"), ("elio", "Elio", "family"), ("nadia", "Nadia", "friends"),
        ("jules", "Jules", "hobbies"), ("bruno", "Bruno", "travel"), ("ana", "Ana", "friends"),
        ("bea", "Béa", "hobbies"), ("cyril", "Cyril", "identity"), ("dora", "Dora", "travel"),
        ("ezra", "Ezra", "family"), ("felix", "Félix", "partner"), ("gaby", "Gaby", "friends"),
        ("hugo", "Hugo", "hobbies"), ("ines", "Inès", "travel")
    ]
    private static let markNames = ["identity", "travel", "friends", "family", "hobbies", "partner"]

    private static func soul(_ key: String) -> PathSoul {
        let index = soulTable.firstIndex { $0.key == key } ?? 0
        let mark = markNames.firstIndex(of: soulTable[index].mark) ?? 0
        return PathSoul(id: id(0x9000, index + 1), name: soulTable[index].name, glyphId: id(0xA000, mark + 1))
    }

    /// Lowercase soul id → name, as `PathModel` builds it from the reference cache.
    static var soulNames: [String: String] {
        Dictionary(uniqueKeysWithValues: soulTable.map { row in
            let entry = Self.soul(row.key)
            return (entry.id.uuidString.lowercased(), entry.name)
        })
    }

    /// Lowercase soul id → its glyph's strokes.
    static var soulGlyphs: [String: [GlyphStroke]] {
        Dictionary(uniqueKeysWithValues: soulTable.map { row in
            (Self.soul(row.key).id.uuidString.lowercased(), SpineFixtureMarks.strokes[row.mark] ?? [])
        })
    }

    // MARK: - Emotions and palettes

    /// primary, secondary, light, surface, shaded, dark: `CATEGORY_PALETTES`.
    private static let categories: [String: [String]] = [
        "fear": ["#7B5E99FF", "#AE91CCFF", "#F2EFF5FF", "#7B5E991A", "#56426BFF", "#19131FFF"],
        "sadness": ["#59658AFF", "#8C98BDFF", "#EEF0F3FF", "#59658A1A", "#3E4761FF", "#12141CFF"],
        "pride": ["#A9478AFF", "#EA91CEFF", "#F6EDF3FF", "#A9478A1A", "#763261FF", "#220E1CFF"],
        "joy": ["#A15C08FF", "#CF8C39FF", "#FAF6EAFF", "#A15C081A", "#714006FF", "#201202FF"],
        "peace": ["#487C5AFF", "#80BF96FF", "#EDF2EEFF", "#487C5A1A", "#32573FFF", "#0E1912FF"],
        "anger": ["#8E4242FF", "#C17575FF", "#F4ECECFF", "#8E42421A", "#632E2EFF", "#1C0D0DFF"]
    ]
    private static let emotionTable: [(slug: String, name: String, category: String)] = [
        ("proud", "Proud", "pride"), ("joyful", "Joyful", "joy"), ("angry", "Angry", "anger"),
        ("scared", "Scared", "fear"), ("peaceful", "Peaceful", "peace"), ("sad", "Sad", "sadness")
    ]

    private static func emotion(_ slug: String) -> EmotionRef {
        let index = emotionTable.firstIndex { $0.slug == slug } ?? 0
        return EmotionRef(id: id(0xB000, index + 1), slug: slug, name: emotionTable[index].name)
    }

    static let palettes: [UUID: EmotionPalette] = Dictionary(uniqueKeysWithValues: emotionTable.compactMap { row in
        guard let hex = categories[row.category], let palette = EmotionPalette(
            primaryHex: hex[0], secondaryHex: hex[1], lightHex: hex[2], surfaceHex: hex[3],
            darkHex: hex[5], shadedHex: hex[4]
        ) else { return nil }
        return (Self.emotion(row.slug).id, palette)
    })

    static func palette(for moment: MomentSummary) -> EmotionPalette? {
        moment.pebble?.emotion.flatMap { palettes[$0.id] }
    }

    // MARK: - Photos

    /// Snap path → the sandbox file and its ratio (1:1, 3:2, 3:2, 16:9, 2:3).
    static let photos: [String: (file: String, ratio: CGFloat)] = [
        "fixture/snap-flag-march.jpg": ("flag-march", 1),
        "fixture/snap-forest-crew.jpg": ("forest-crew", 3 / 2),
        "fixture/snap-fountain.jpg": ("fountain", 3 / 2),
        "fixture/snap-rooftop-group.jpg": ("rooftop-group", 16 / 9),
        "fixture/snap-seawall-portrait.jpg": ("seawall-portrait", 2 / 3)
    ]

    // MARK: - Moments

    private struct Spec {
        let n: Int
        let name: String
        let at: String
        var photo: String?
        var pebble: (intensity: Int, positiveness: Int, emotion: String, souls: [String], glyph: String?)?
    }

    private static let neighbours = ["ana", "bea", "cyril", "dora", "ezra", "felix", "gaby", "hugo", "ines"]

    private static let specs: [Spec] = [
        Spec(n: 1, name: "Flag march with Nadia", at: "05T10:15", photo: "flag-march.jpg",
             pebble: (3, 1, "proud", ["nadia"], "friends")),
        Spec(n: 2, name: "Elio lost his first tooth", at: "05T19:40", pebble: (2, 1, "joyful", ["elio", "mara"], "family")),
        Spec(n: 3, name: "Rent went up again", at: "06T08:02", pebble: (2, -1, "angry", [], "identity")),
        Spec(n: 4, name: "Forest walk, all of us", at: "06T16:30", photo: "forest-crew.jpg",
             pebble: (3, 1, "peaceful", ["mara", "elio", "bruno"], "travel")),
        Spec(n: 5, name: "Couldn’t sleep", at: "07T03:10", pebble: (1, -1, "scared", [], "identity")),
        Spec(n: 6, name: "Coffee by the fountain", at: "07T12:05", photo: "fountain.jpg",
             pebble: (1, 0, "peaceful", ["jules"], "friends")),
        Spec(n: 7, name: "Rooftop birthday", at: "08T21:00", photo: "rooftop-group.jpg",
             pebble: (3, 1, "joyful", ["nadia", "jules", "mara"], "hobbies")),
        Spec(n: 12, name: "Picnic on the square, the whole street", at: "08T13:10",
             pebble: (2, 1, "joyful", ["mara", "elio", "nadia", "jules", "bruno"] + neighbours, "friends")),
        Spec(n: 8, name: "Said no to the extra project", at: "10T09:20", pebble: (2, 0, "proud", [], "identity")),
        Spec(n: 9, name: "Mara on the seawall", at: "10T17:45", photo: "seawall-portrait.jpg",
             pebble: (2, 1, "joyful", ["mara"], "partner")),
        Spec(n: 13, name: "", at: "10T19:05", photo: "fountain.jpg", pebble: (1, 0, "peaceful", ["mara"], "partner")),
        Spec(n: 10, name: "Grandma’s call went quiet", at: "11T14:00", pebble: (2, -1, "sad", [], "family")),
        Spec(n: 11, name: "Bruno asleep on my feet", at: "11T22:30", pebble: (1, 0, "peaceful", ["bruno"], nil)),
        // Not pebbles yet (#1050): blank stones, one set as type, one on a card.
        Spec(n: 14, name: "Bread from the corner bakery", at: "07T18:20"),
        Spec(n: 15, name: "", at: "11T09:10", photo: "forest-crew.jpg")
    ]

    static let moments: [MomentSummary] = specs.map { Self.moment($0) }

    private static func moment(_ spec: Spec) -> MomentSummary {
        let momentId = id(0x8000, spec.n)
        let happened = date("2026-10-\(spec.at):00+02:00")
        let pebble = spec.pebble.map { p in
            MomentSummary.PebbleSummary(
                id: momentId, intensity: p.intensity, positiveness: p.positiveness, visibility: .private,
                emotion: Self.emotion(p.emotion), glyph: p.glyph.map { Self.glyph($0) },
                souls: p.souls.map { Self.soul($0) }
            )
        }
        return MomentSummary(id: momentId, name: spec.name.isEmpty ? nil : spec.name, happenedAt: happened,
                             createdAt: happened, firstSnapPath: spec.photo.map { "fixture/snap-\($0)" }, pebble: pebble)
    }

    private static func glyph(_ mark: String) -> PebbleGlyph {
        PebbleGlyph(id: id(0xA000, (markNames.firstIndex(of: mark) ?? 0) + 1), name: mark,
                    strokes: SpineFixtureMarks.strokes[mark] ?? [], viewBox: "0 0 200 200", source: .domain)
    }

    private static func id(_ group: Int, _ n: Int) -> UUID {
        UUID(uuidString: String(format: "00000000-0000-4000-%04lX-%012ld", group, n)) ?? UUID()
    }

    private static func date(_ iso: String) -> Date {
        ISO8601Flexible.parse(iso) ?? .distantPast
    }
}
#endif
```

`PebbleGlyph(id:name:strokes:viewBox:source:)` and `EmotionRef(id:slug:name:)` are the existing initialisers.

- [ ] **Step 5: Run the test to verify it passes**

Run the **Suite command** with `SpineFixtureWeekTests`, then `ThemeLiteralsTests`.
Expected: PASS (5), then PASS (the hex strings feed `EmotionPalette(primaryHex:…)`, which no rule flags).

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Debug apps/ios/PebblesTests/Spine/SpineFixtureWeekTests.swift
git commit -m "test(ios): the spine fixture week, ported from the web"
```

---

### Task D1c.4: D1c verification and PR

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** (including `LocalizationTests` from stack B) and **Build**. Open `Localizable.xcstrings` in Xcode: no `path.spine.*` entry is `New` or `Stale`, every one has `en` and `fr`. If Xcode rewrote the file on open, `git diff` it: a reorder of the inserted block only is acceptable and is committed as `chore(ios): let xcode order the spine strings`; anything else is reverted with `git checkout`.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <D1c-number> --title "feat(ios): spine strings in english and french, and the fixture week" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Part of #1130`; the key table (33 keys; `summaryNoPebbles` is decision 8; `cardAria` folded into `stoneA11y`, `prevWeek` / `nextWeek` into `weekLink`, `carve` not ported, `contentsHint` new); the plural encoding (substitutions on argument 1; French `many` = `other`); the fixture's generated marks and how to regenerate them; **new pattern for review:** typed copy over explicit dotted keys resolved for a pinned language (`SpineStrings` / `SpineCopy`). No Lab Note, no `F-…` ids.

---
## Part D2a — `feat/1130-ios-spine-stone`

PR title: `feat(ios): the spine stone, its press, rings, ripple and glint`. Body opens `Part of #1130`. Visible in the lab and the references only. The press as timed state comes first (Tasks D2a.1–D2a.5), then the stone's views (Tasks D2a.6–D2a.10).

### Task D2a.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1130-ios-spine-stone
git branch --show-current
```

Expected: `feat/1130-ios-spine-stone`.

---

### Task D2a.1: `StoneMotion`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Stone/StoneMotion.swift`
- Test: `apps/ios/PebblesTests/Spine/StoneMotionTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1130 R12–R14: the web's stone-motion table at its stops, the WAAPI reading
/// (linear between keyframes in eased progress, clamped outside), the totals,
/// and the reduced-motion touch. Mirrors Android's StoneMotionTest.
@Suite("StoneMotion")
struct StoneMotionTests {
    typealias M = StoneMotion

    private func near(_ a: Double, _ b: Double, _ tolerance: Double = 1e-6) -> Bool { abs(a - b) <= tolerance }

    @Test("the endpoints are the first and last frames")
    func endpoints() {
        #expect(M.piecewise(0, M.Dip.frames) == 1)
        #expect(M.piecewise(1, M.Dip.frames) == 1)
        #expect(M.piecewise(0, M.Spark.rotationFrames) == 0)
        #expect(M.piecewise(1, M.Spark.rotationFrames) == 90)
    }

    @Test("between two frames the value is linear, and exact at a frame")
    func linear() {
        #expect(near(M.piecewise(0.13, M.Dip.frames), 0.95))
        #expect(near(M.piecewise(0.26, M.Dip.frames), 0.9))
        #expect(near(M.piecewise(0.6, M.Dip.frames), 1.03))
        #expect(near(M.piecewise(0.675, M.Spark.rotationFrames), 65))
    }

    @Test("outside the table the value clamps")
    func clamps() {
        #expect(M.piecewise(-1, M.Dip.frames) == 1)
        #expect(M.piecewise(2, M.Spark.rotationFrames) == 90)
        #expect(M.piecewise(2, M.Spark.alphaFrames) == 0)
    }

    @Test("a wave rises, then reaches its size's distance less half a step per wave")
    func wave() {
        let frames = M.Wave.scaleFrames(.small, 1)
        #expect(M.piecewise(0, frames) == 1.15)
        #expect(near(M.piecewise(0.07, frames), 1.45))
        #expect(near(M.piecewise(1, frames), 7.2 - 0.5))
        #expect(near(M.piecewise(0.07, M.Wave.alphaFrames), 0.85))
        #expect(M.Wave.reach[.medium] == 5.6 && M.Wave.reach[.large] == 4.4)
    }

    @Test("every element ends where the table says, and the press lasts 2.57 s")
    func totals() {
        #expect(near(M.Dip.element.end, 0.52))
        #expect(near(M.Rings.element.end, 1.26))
        #expect(near(M.Sweep.element.end, 1.04))
        #expect(near(M.Spark.element.end, 1.06))
        #expect(near(M.Wave.element(0).end, 1.77))
        #expect(near(M.Wave.element(2).end, 2.57))
        #expect(near(M.total(still: false), 2.57))
        #expect(M.total(still: true) == 0.9)
        #expect(M.openDelay == 0.36 && M.goPressDelay == 0.62)
    }

    @Test("one easing runs the whole element: 0 at its start, 1 at its end, rising between")
    func easing() {
        let rings = M.Rings.element
        #expect(rings.progress(at: 0) == 0)
        #expect(rings.progress(at: rings.delay) == 0)
        #expect(near(rings.progress(at: rings.end), 1))
        let samples = stride(from: rings.delay, through: rings.end, by: 0.05).map { rings.progress(at: $0) }
        #expect(samples == samples.sorted())
    }

    @Test("at the press the stone rests, the sweep is parked and the waves are mounted but clear")
    func start() {
        let frame = M.frame(at: 0, still: false, size: .medium)
        #expect(frame.dip == 1 && frame.rings == 1)
        #expect(frame.sweep == nil)
        #expect(frame.sparkAlpha == 0)
        #expect(frame.waves.count == 3 && frame.waves.allSatisfy { $0.alpha == 0 })
    }

    @Test("mid-press the stone dips and the sweep crosses")
    func middle() throws {
        let frame = M.frame(at: 0.2, still: false, size: .medium)
        #expect(frame.dip < 1)
        let sweep = try #require(frame.sweep)
        #expect(sweep > 0 && sweep < 1)
    }

    @Test("after the last element everything is back at rest")
    func end() {
        let frame = M.frame(at: 2.6, still: false, size: .large)
        #expect(near(frame.dip, 1) && near(frame.rings, 1))
        #expect(frame.sweep == nil && frame.sparkAlpha == 0)
        #expect(frame.waves.allSatisfy { $0.alpha == 0 })
    }

    @Test("reduced motion holds a still sheen and dark rings for 900 ms, with no waves")
    func still() {
        let held = M.frame(at: 0.5, still: true, size: .small)
        #expect(held.stillSheen == 0.9 && held.stillRings)
        #expect(held.dip == 1 && held.waves.isEmpty && held.sweep == nil)
        let after = M.frame(at: 1, still: true, size: .small)
        #expect(after.stillSheen == 0 && !after.stillRings)
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/StoneMotionTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `StoneMotionTests`. Expected: build FAIL with "cannot find 'StoneMotion' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Stone/StoneMotion.swift`:

```swift
import SwiftUI

/// The stone press (#1130 R12–R14), the web's `stone-motion.ts` table verbatim
/// (glint strengths from `stone-tones.ts`, in `StoneGlintInk`). WAAPI semantics,
/// the Android lesson: one easing over each element's whole run, linear between
/// keyframes in that eased progress. Times are seconds from the press. Nothing
/// runs unless a stone is pressed, and every element is finite.
enum StoneMotion {
    typealias Frames = [(at: Double, value: Double)]

    /// One animated element: a delay, a duration, one easing over the run.
    struct Element {
        let delay: Double
        let duration: Double
        let curve: UnitCurve

        var end: Double { delay + duration }

        /// The eased progress at `elapsed`: 0 until the start, 1 from the end.
        func progress(at elapsed: Double) -> Double {
            curve.value(at: min(max((elapsed - delay) / duration, 0), 1))
        }

        func isRunning(at elapsed: Double) -> Bool { elapsed >= delay && elapsed < end }
    }

    struct WaveSample: Equatable {
        let scale: Double
        let alpha: Double
    }

    /// Every animated value at one instant of a press.
    struct Frame: Equatable {
        var dip = 1.0
        var rings = 1.0
        /// The sweep's travelled fraction while it runs; nil while parked off the stone.
        var sweep: Double?
        var sparkScale = 0.0
        var sparkRotation = 0.0
        var sparkAlpha = 0.0
        var waves: [WaveSample] = []
        /// Reduced motion (R13): the still sheen's opacity, and the rings in ink-2.
        var stillSheen = 0.0
        var stillRings = false
    }

    /// The value at `progress` through `frames`: linear between two keyframes,
    /// the end values outside them.
    static func piecewise(_ progress: Double, _ frames: Frames) -> Double {
        guard let first = frames.first, let last = frames.last else { return 0 }
        if progress <= first.at { return first.value }
        if progress >= last.at { return last.value }
        for index in 1..<frames.count where progress <= frames[index].at {
            let (t0, v0) = frames[index - 1]
            let (t1, v1) = frames[index]
            return t1 == t0 ? v1 : v0 + (v1 - v0) * (progress - t0) / (t1 - t0)
        }
        return last.value
    }

    private static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> UnitCurve {
        .bezier(startControlPoint: UnitPoint(x: x1, y: y1), endControlPoint: UnitPoint(x: x2, y: y2))
    }

    /// The stone dips under the finger and springs back: 1 → .9 at 26 % → 1.03 at 60 % → 1.
    enum Dip {
        static let element = Element(delay: 0, duration: 0.52, curve: bezier(0.3, 0.7, 0.2, 1))
        static let frames: Frames = [(0, 1), (0.26, 0.9), (0.6, 1.03), (1, 1)]
    }

    /// The raked rings swell once: 1 → 1.08 at 35 % → 1.
    enum Rings {
        static let element = Element(delay: 0.11, duration: 1.15, curve: bezier(0.2, 0.7, 0.2, 1))
        static let frames: Frames = [(0, 1), (0.35, 1.08), (1, 1)]
    }

    /// A sheen band crosses the stone from the light, over 1.6 stone widths.
    enum Sweep {
        static let element = Element(delay: 0.14, duration: 0.9, curve: bezier(0.37, 0, 0.63, 1))
        static let travel = 1.6
    }

    /// A four-point spark lands where the light is.
    enum Spark {
        static let element = Element(delay: 0.3, duration: 0.76, curve: bezier(0.3, 0.6, 0.3, 1))
        static let scaleFrames: Frames = [(0, 0), (0.35, 1), (1, 0)]
        static let rotationFrames: Frames = [(0, 0), (0.35, 40), (1, 90)]
        static let alphaFrames: Frames = [(0, 0), (0.35, 1), (1, 0)]
    }

    /// Three waves of the flat outline, k = 0, 1, 2: 1.15 at alpha 0 → 1.45 at .85
    /// (7 %) → `reach − 0.5k` stone widths at alpha 0.
    enum Wave {
        static let count = 3
        static let reach: [ValenceSizeGroup: Double] = [.small: 7.2, .medium: 5.6, .large: 4.4]

        static func element(_ k: Int) -> Element {
            Element(delay: 0.12 + 0.23 * Double(k), duration: 1.65 + 0.17 * Double(k),
                    curve: bezier(0.12, 0.55, 0.3, 1))
        }

        static func scaleFrames(_ size: ValenceSizeGroup, _ k: Int) -> Frames {
            [(0, 1.15), (0.07, 1.45), (1, (reach[size] ?? 5.6) - 0.5 * Double(k))]
        }

        static let alphaFrames: Frames = [(0, 0), (0.07, 0.85), (1, 0)]
    }

    /// Reduced motion: the rings darken and a still sheen at 0.9 holds this long.
    static let stillTouch = 0.9
    static let stillSheen = 0.9
    /// A pressed stone opens its pebble once the dip has landed (D8).
    static let openDelay = 0.36
    /// A contents row scrolls to its stone, then presses it after this, without opening.
    static let goPressDelay = 0.62

    /// When the last element ends: the third wave, or the still hold.
    static func total(still: Bool) -> Double {
        still ? stillTouch : Wave.element(Wave.count - 1).end
    }

    static func frame(at elapsed: Double, still: Bool, size: ValenceSizeGroup) -> Frame {
        var frame = Frame()
        if still {
            let holding = elapsed >= 0 && elapsed < stillTouch
            frame.stillSheen = holding ? stillSheen : 0
            frame.stillRings = holding
            return frame
        }
        frame.dip = piecewise(Dip.element.progress(at: elapsed), Dip.frames)
        frame.rings = piecewise(Rings.element.progress(at: elapsed), Rings.frames)
        frame.sweep = Sweep.element.isRunning(at: elapsed) ? Sweep.element.progress(at: elapsed) : nil
        let spark = Spark.element.progress(at: elapsed)
        frame.sparkScale = piecewise(spark, Spark.scaleFrames)
        frame.sparkRotation = piecewise(spark, Spark.rotationFrames)
        frame.sparkAlpha = piecewise(spark, Spark.alphaFrames)
        frame.waves = (0..<Wave.count).map { k in
            let progress = Wave.element(k).progress(at: elapsed)
            return WaveSample(scale: piecewise(progress, Wave.scaleFrames(size, k)),
                              alpha: piecewise(progress, Wave.alphaFrames))
        }
        return frame
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `StoneMotionTests`. Expected: PASS (10).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Stone/StoneMotion.swift apps/ios/PebblesTests/Spine/StoneMotionTests.swift
git commit -m "feat(ios): the stone press timeline, waapi keyframes in eased progress"
```

---

### Task D2a.2: `StonePress`, `StonePressRequests` and `CardFace`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Stone/StonePress.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/StonePressRequests.swift`
- Test: `apps/ios/PebblesTests/Spine/StonePressRequestsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1130 D8: a press request is state a stone claims when it appears, not an
/// event that fires into nothing. And a press is a value with a sequence.
@Suite("Stone press state")
@MainActor
struct StonePressRequestsTests {
    private let first = UUID(uuidString: "00000000-0000-4000-8000-000000000001") ?? UUID()
    private let second = UUID(uuidString: "00000000-0000-4000-8000-000000000002") ?? UUID()

    @Test("a request is pending with a rising sequence; a newer one replaces it")
    func request() {
        let requests = StonePressRequests()
        #expect(requests.pending == nil)
        requests.request(first)
        #expect(requests.pending == StonePressRequests.PressRequest(momentId: first, seq: 1))
        requests.request(second)
        #expect(requests.pending == StonePressRequests.PressRequest(momentId: second, seq: 2))
    }

    @Test("claiming a stale request leaves the newer one pending")
    func staleClaim() {
        let requests = StonePressRequests()
        requests.request(first)
        let stale = requests.pending
        requests.request(first)
        if let stale { requests.claim(stale) }
        #expect(requests.pending?.seq == 2)
    }

    @Test("claiming the pending request clears it, and cancel clears it too")
    func claimAndCancel() throws {
        let requests = StonePressRequests()
        requests.request(first)
        requests.claim(try #require(requests.pending))
        #expect(requests.pending == nil)
        requests.request(second)
        requests.cancel()
        #expect(requests.pending == nil)
    }

    @Test("a press follows the last one and measures from its start")
    func press() {
        let start = Date(timeIntervalSince1970: 100)
        let one = StonePress.next(after: nil, still: false, now: start)
        let two = StonePress.next(after: one, still: true, now: start)
        #expect(one.seq == 1 && two.seq == 2 && two.still)
        #expect(one.elapsed(at: start.addingTimeInterval(0.25)) == 0.25)
    }

    @Test("a card face turns to the other")
    func face() {
        #expect(CardFace.recto.other == .verso && CardFace.verso.other == .recto)
        #expect(CardFace(rawValue: "verso") == .verso)
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/StonePressRequestsTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `StonePressRequestsTests`. Expected: build FAIL with "cannot find 'StonePressRequests' in scope".

- [ ] **Step 3: Implement `StonePressRequests` and `CardFace`**

Create `apps/ios/Pebbles/Features/Path/Spine/StonePressRequests.swift`:

```swift
import Foundation
import Observation

/// The face a press asks the held card to open on (#1130 §Press-request state):
/// a stone press asks the verso, a card or entry press the recto. Defined here,
/// consumed by stack E.
enum CardFace: String, Codable {
    case recto, verso

    var other: CardFace { self == .recto ? .verso : .recto }
}

/// A press asked of a stone that may not exist yet (D8). A contents tap scrolls
/// first and presses later; the stone it targets is realised by the scroll and
/// finds the request in its `.task(id:)`, where a closure or a notification
/// would have fired into nothing. New pattern, named in the D2a PR.
@Observable @MainActor
final class StonePressRequests {
    struct PressRequest: Equatable {
        let momentId: UUID
        let seq: Int
    }

    private(set) var pending: PressRequest?
    private var seq = 0

    func request(_ momentId: UUID) {
        seq += 1
        pending = PressRequest(momentId: momentId, seq: seq)
    }

    /// Clears the request if it is still the pending one; a stale claim does nothing.
    func claim(_ request: PressRequest) {
        if pending == request { pending = nil }
    }

    /// A page change drops whatever was asked of the page before.
    func cancel() {
        pending = nil
    }
}
```

- [ ] **Step 4: Implement `StonePress`**

Create `apps/ios/Pebbles/Features/Path/Spine/Stone/StonePress.swift`:

```swift
import SwiftUI

/// A live press on one stone (#1130 §Press-request state): when it began, and
/// whether it is the reduced-motion touch. A value: a view holds it as `@State`
/// and the stone's timeline reads it; `seq` tells two presses apart.
struct StonePress: Equatable {
    let seq: Int
    let start: Date
    let still: Bool

    static func next(after current: StonePress?, still: Bool, now: Date) -> StonePress {
        StonePress(seq: (current?.seq ?? 0) + 1, start: now, still: still)
    }

    func elapsed(at date: Date) -> Double {
        date.timeIntervalSince(start)
    }
}

/// Clears a press once its last element has played, so the ripple unmounts
/// (R14) and the stone's timeline pauses. A newer press keeps its own lifetime:
/// its new `seq` cancels this task, and the guard leaves it alone.
private struct StonePressLifetime: ViewModifier {
    @Binding var press: StonePress?

    func body(content: Content) -> some View {
        content.task(id: press?.seq) {
            guard let current = press else { return }
            // Cancellation is the next press taking over, not a failure.
            try? await Task.sleep(for: .seconds(StoneMotion.total(still: current.still)))
            if press?.seq == current.seq { press = nil }
        }
    }
}

extension View {
    func stonePressLifetime(_ press: Binding<StonePress?>) -> some View {
        modifier(StonePressLifetime(press: press))
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run the **Suite command** with `StonePressRequestsTests`. Expected: PASS (5).

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/StonePressRequests.swift apps/ios/Pebbles/Features/Path/Spine/Stone/StonePress.swift apps/ios/PebblesTests/Spine/StonePressRequestsTests.swift
git commit -m "feat(ios): stone presses as state a stone claims, and the card faces"
```

---

### Task D2a.3: `StoneRingGeometry`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Stone/StoneRingGeometry.swift`
- Test: `apps/ios/PebblesTests/Spine/StoneRingGeometryTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Testing
@testable import Pebbles

/// #1130 R3: rings are the flat outline scaled about its area centroid, one
/// step further out each (4.8 / 4.4 / 4.2 pt), the halo 0.78 of a step past the
/// last; and every shipped outline yields a geometry.
@Suite("StoneRingGeometry")
struct StoneRingGeometryTests {
    private func near(_ a: CGFloat, _ b: CGFloat) -> Bool { abs(a - b) < 1e-6 }

    @Test("a rectangle: centroid at its middle, steps in points on each axis")
    func rectangle() {
        let path = CGPath(rect: CGRect(x: 0, y: 0, width: 50, height: 25), transform: nil)
        let geometry = StoneRingGeometry.make(outline: path, size: .medium)
        #expect(near(geometry.centroid.x, 25) && near(geometry.centroid.y, 12.5))
        #expect(geometry.rings.count == 2)
        #expect(near(geometry.rings[0].width, 1 + 2 * 4.4 / 50) && near(geometry.rings[0].height, 1 + 2 * 4.4 / 25))
        #expect(near(geometry.rings[1].width, 1 + 2 * 2 * 4.4 / 50))
        #expect(near(geometry.halo.width, 1 + 2 * 2.78 * 4.4 / 50))
    }

    @Test("a triangle's centroid is the mean of its corners")
    func triangle() {
        let path = CGMutablePath()
        path.addLines(between: [CGPoint(x: 0, y: 0), CGPoint(x: 30, y: 0), CGPoint(x: 0, y: 30)])
        path.closeSubpath()
        let geometry = StoneRingGeometry.make(outline: path, size: .small)
        #expect(near(geometry.centroid.x, 10) && near(geometry.centroid.y, 10))
        #expect(geometry.rings.count == 1)
    }

    @Test("one, two, three rings by size")
    func counts() {
        let path = CGPath(ellipseIn: CGRect(x: 0, y: 0, width: 44, height: 44), transform: nil)
        #expect(StoneRingGeometry.make(outline: path, size: .small).rings.count == 1)
        #expect(StoneRingGeometry.make(outline: path, size: .medium).rings.count == 2)
        #expect(StoneRingGeometry.make(outline: path, size: .large).rings.count == 3)
    }

    @Test("every bundled soft outline yields rings at its phone size")
    @MainActor
    func bundled() {
        for valence in Valence.allCases {
            #expect(StoneRingGeometry.forStone(valence) != nil, "\(valence)")
        }
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/StoneRingGeometryTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `StoneRingGeometryTests`. Expected: build FAIL with "cannot find 'StoneRingGeometry' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Stone/StoneRingGeometry.swift`:

```swift
import SwiftUI

/// The rings and the halo around a Spine stone (#1130 R3): the flat outline
/// scaled about its area centroid, each ring a step further out (4.8 / 4.4 /
/// 4.2 pt by size), the halo 0.78 of a step past the last ring. The steps are
/// points, not outline units, as the web's non-scaling strokes are.
struct StoneRingGeometry {
    /// The silhouette in the stone's own frame, in points.
    let outline: Path
    /// Its area centroid, in the same frame.
    let centroid: CGPoint
    /// Per ring, innermost first: the x and y scales about `centroid`.
    let rings: [CGSize]
    let halo: CGSize

    static let count: [ValenceSizeGroup: Int] = [.small: 1, .medium: 2, .large: 3]
    static let step: [ValenceSizeGroup: CGFloat] = [.small: 4.8, .medium: 4.4, .large: 4.2]
    static let haloStep: CGFloat = 0.78

    /// - Parameter outline: the silhouette already in points, in the stone's frame.
    static func make(outline: CGPath, size: ValenceSizeGroup) -> StoneRingGeometry {
        let polygon = WobblePathFlattener.flatten(outline, step: 2).first?.points ?? []
        let bounds = outline.boundingBoxOfPath
        let centroid = areaCentroid(polygon) ?? CGPoint(x: bounds.midX, y: bounds.midY)
        let step = Self.step[size] ?? 4.4
        let width = max(bounds.width, 1)
        let height = max(bounds.height, 1)
        func at(_ k: CGFloat) -> CGSize {
            CGSize(width: 1 + 2 * k * step / width, height: 1 + 2 * k * step / height)
        }
        let rings = Self.count[size] ?? 2
        return StoneRingGeometry(outline: Path(outline), centroid: centroid,
                                 rings: (1...rings).map { at(CGFloat($0)) }, halo: at(CGFloat(rings) + haloStep))
    }

    /// The geometry of `valence`'s soft outline at its phone width, cached: the
    /// flattening walks the whole path.
    @MainActor
    static func forStone(_ valence: Valence) -> StoneRingGeometry? {
        if let cached = cache[valence] { return cached }
        guard let art = WobbleRenderer.softBackdropArt(size: valence.sizeGroup, polarity: valence.polarity) else {
            return nil
        }
        let stone = SpineLayout.stoneSize(valence.sizeGroup)
        let outline = WobbledBackdropShape(art: art).path(in: CGRect(origin: .zero, size: stone)).cgPath
        let geometry = make(outline: outline, size: valence.sizeGroup)
        cache[valence] = geometry
        return geometry
    }

    @MainActor private static var cache: [Valence: StoneRingGeometry] = [:]

    /// Shoelace over the first contour; nil when it encloses no area.
    private static func areaCentroid(_ points: [CGPoint]) -> CGPoint? {
        guard points.count >= 3 else { return nil }
        var area: CGFloat = 0
        var cx: CGFloat = 0
        var cy: CGFloat = 0
        for index in points.indices {
            let p = points[index]
            let q = points[(index + 1) % points.count]
            let cross = p.x * q.y - q.x * p.y
            area += cross
            cx += (p.x + q.x) * cross
            cy += (p.y + q.y) * cross
        }
        guard abs(area) > 1e-9 else { return nil }
        return CGPoint(x: cx / (3 * area), y: cy / (3 * area))
    }
}
```

The shoelace's `area` here is twice the signed area, so the centroid divides by `3 × area` (that is, `6 × A`).

- [ ] **Step 4: Run the test to verify it fails on `SpineLayout`**

Run the **Suite command** with `StoneRingGeometryTests`. Expected: build FAIL with "cannot find 'SpineLayout' in scope". Task D2a.4 adds it; continue there and run this suite again at its Step 5.

---

### Task D2a.4: `SpineLayout` and the stone row

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/SpineLayout.swift`
- Test: `apps/ios/PebblesTests/Spine/SpineLayoutTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Testing
@testable import Pebbles

/// #1130 R1–R2 and §Geometry: the phone geometry, and where a row puts its stone,
/// its hit box and its floor. The web's geometry is kept: an entry's stone
/// overhangs the row's top (Android's StoneRowGeometryTest).
@Suite("SpineLayout")
struct SpineLayoutTests {
    private func near(_ a: CGFloat, _ b: CGFloat) -> Bool { abs(a - b) < 0.01 }

    @Test("stones are 34 / 44 / 54 wide and 27.2 / 44 / 64.5 tall")
    func stones() {
        #expect(SpineLayout.stoneSize(.small).width == 34 && near(SpineLayout.stoneSize(.small).height, 27.24))
        #expect(SpineLayout.stoneSize(.medium) == CGSize(width: 44, height: 44))
        #expect(SpineLayout.stoneSize(.large).width == 54 && near(SpineLayout.stoneSize(.large).height, 64.48))
    }

    @Test("the column runs from 82 to the width less 18: 293 at the 393 pt pin")
    func column() {
        #expect(SpineLayout.column(width: 393) == 293)
        #expect(SpineLayout.column(width: 390) == 290)
    }

    @Test("an entry's medium stone centres on the line at its anchor and overhangs the row")
    func entryRow() {
        let row = SpineLayout.stoneRow(anchor: 14.4, stone: SpineLayout.stoneSize(.medium))
        #expect(near(row.stone.minX, 18) && near(row.stone.minY, -7.6))
        #expect(near(row.hit.width, 64) && near(row.hit.height, 64))
        #expect(near(row.hit.midX, 40) && near(row.hit.midY, 14.4))
        #expect(near(row.minHeight, 14.4 + 22 + 14))
    }

    @Test("a small stone's hit box grows to 44 pt where stone + 10 falls short")
    func smallTarget() {
        let row = SpineLayout.stoneRow(anchor: 31, stone: SpineLayout.stoneSize(.small))
        #expect(near(row.hit.width, 54))
        #expect(near(row.hit.height, 47.24))
        let tiny = SpineLayout.stoneRow(anchor: 31, stone: CGSize(width: 10, height: 10))
        #expect(tiny.hit.width == 44 && tiny.hit.height == 44)
    }

    @Test("seals are 20 / 24 / 27")
    func seals() {
        #expect([ValenceSizeGroup.small, .medium, .large].map(SpineLayout.sealWidth) == [20, 24, 27])
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/SpineLayoutTests.swift`.

- [ ] **Step 2: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/SpineLayout.swift`:

```swift
import CoreGraphics

/// Where a pebble row puts its stone (#1130 R2), in the row's own coordinates
/// (origin at the row's top-left, x 0 on the page's left edge). The stone sits
/// on the line at the anchor whatever that puts above the row's top: an entry's
/// stone overhangs by up to 13.6 pt, and stays pressable because no view in a
/// day column clips (D4).
struct StoneRow: Equatable {
    let stone: CGRect
    /// The stone plus 10 all round, grown to 44 × 44 about its centre.
    let hit: CGRect
    /// The row is at least this tall, so the next pebble clears the stone.
    let minHeight: CGFloat
}

/// The Spine's phone geometry in points, from the web rule set (§Page geometry)
/// and Android's `SpineLayout`, in one place so the ground, the seams, the stones
/// and the cards agree. Text sizes are `SpineType`'s; these never scale (D11).
enum SpineLayout {
    // Page (R1)
    static let spineX: CGFloat = 40
    static let colX: CGFloat = 82
    static let padRight: CGFloat = 18
    static let dayPadTop: CGFloat = 58
    static let dayPadBottom: CGFloat = 34
    static let pebbleGap: CGFloat = 38
    static let line: CGFloat = 1
    static let feedTop: CGFloat = 48
    static let feedBottom: CGFloat = 96

    // Stones (R2)
    static let cardAnchor: CGFloat = 31
    static let stoneHitInset: CGFloat = 10
    static let minTarget: CGFloat = 44
    static let stoneClearance: CGFloat = 14

    // Seams and labels (R8–R10)
    static let seamHeight: CGFloat = 8
    /// The day label hangs this far above the hairline; `DaySeam` is overhang + seam tall.
    static let seamOverhang: CGFloat = 14
    static let labelWidth: CGFloat = 46
    static let stillPadTop: CGFloat = 14
    static let stillMinHeight: CGFloat = 48
    static let rakeTile = CGSize(width: 36, height: 240)

    // Opening (R18) and persons (R15)
    static let versoPadX: CGFloat = 20
    static let openingTop: CGFloat = 46
    static let openingTitleGap: CGFloat = 16
    static let openingSummaryGap: CGFloat = 12
    static let sectionTop: CGFloat = 40
    static let sectionHeaderGap: CGFloat = 10
    static let personRowMin: CGFloat = 44
    static let personGlyphColumn: CGFloat = 30
    static let personGlyph: CGFloat = 24
    static let personGap: CGFloat = 10
    static let dot: CGFloat = 7
    static let dotGap: CGFloat = 5
    static let foldIndent: CGFloat = 40

    // Contents (R16)
    static let contentsRowMin: CGFloat = 42
    static let contentsDayColumn: CGFloat = 40
    static let contentsSealColumn: CGFloat = 36
    static let readingDot: CGFloat = 5
    static let readingDotOffset: CGFloat = 12

    // Mouth and empty week
    static let mouthLine: CGFloat = 26
    static let emptyPadY: CGFloat = 48

    static func stoneWidth(_ size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small: return 34
        case .medium: return 44
        case .large: return 54
        }
    }

    /// The width by size; the height from the outline's own aspect (27.2 / 44 / 64.5).
    static func stoneSize(_ size: ValenceSizeGroup) -> CGSize {
        let width = stoneWidth(size)
        return CGSize(width: width, height: width / CGFloat(PebbleOutlineGeometry.aspectRatio(for: size)))
    }

    static func sealWidth(_ size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small: return 20
        case .medium: return 24
        case .large: return 27
        }
    }

    /// The content column: from `colX` to the width less `padRight`.
    static func column(width: CGFloat) -> CGFloat {
        width - colX - padRight
    }

    static func stoneRow(anchor: CGFloat, stone: CGSize) -> StoneRow {
        let hitWidth = max(stone.width + 2 * stoneHitInset, minTarget)
        let hitHeight = max(stone.height + 2 * stoneHitInset, minTarget)
        return StoneRow(
            stone: CGRect(x: spineX - stone.width / 2, y: anchor - stone.height / 2,
                          width: stone.width, height: stone.height),
            hit: CGRect(x: spineX - hitWidth / 2, y: anchor - hitHeight / 2, width: hitWidth, height: hitHeight),
            minHeight: anchor + stone.height / 2 + stoneClearance
        )
    }
}
```

- [ ] **Step 3: Run the tests to verify they pass**

Run the **Suite command** with `SpineLayoutTests`, then `StoneRingGeometryTests`. Expected: PASS (5), then PASS (4). If `bundled` fails for a valence, `WobbleRenderer.softBackdropArt` returned nil for that outline asset: check `Resources/Outlines/<size>-<polarity>.svg` is in the bundle (stack B3 added the soft variant) and report.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Stone/StoneRingGeometry.swift apps/ios/Pebbles/Features/Path/Spine/SpineLayout.swift apps/ios/PebblesTests/Spine/StoneRingGeometryTests.swift apps/ios/PebblesTests/Spine/SpineLayoutTests.swift
git commit -m "feat(ios): the spine's phone geometry and its rings about the centroid"
```

---

### Task D2a.5: Each pebble's own crust (`seed:`)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Stone/StoneView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Stone/PebbleStoneView.swift`
- Test: `apps/ios/PebblesTests/Stone/StoneSeedTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
@testable import Pebbles

/// #1130 D1: the Spine passes `StableHash.seed(pebble.id)`; the shader reads the
/// seed through a Float, so it is folded to 24 bits, where a Float is exact.
@Suite("Stone seed")
struct StoneSeedTests {
    @Test("a pebble's seed folds to 24 bits and survives the Float the shader reads")
    func fold() {
        let folded = StoneView.shaderSeed(3_118_864_542)
        #expect(folded == 15_079_582)
        #expect(Int(Float(folded)) == folded)
        #expect(StoneView.shaderSeed(.max) < 1 << 24)
    }
}
```

Save it as `apps/ios/PebblesTests/Stone/StoneSeedTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `StoneSeedTests`. Expected: build FAIL with "type 'StoneView' has no member 'shaderSeed'".

- [ ] **Step 3: Give `StoneView` a seed**

In `apps/ios/Pebbles/Features/Path/Stone/StoneView.swift`:

1. Replace

```swift
    /// What is carved. `.sample` (the default) is the picker and the lab.
    var carving: StoneCarving = .sample
```

with

```swift
    /// What is carved. `.sample` (the default) is the picker and the lab.
    var carving: StoneCarving = .sample
    /// A pebble's own crust (#1130 D1): `StableHash.seed(pebble.id)`, so each
    /// stone's grain varies as on web and Android. Nil keeps the per-shape seed.
    var seed: UInt32?
```

2. Replace

```swift
    /// A different crust per shape, stable across launches.
    private var seed: Int { Valence.allCases.firstIndex(of: valence).map { $0 * 31 + 7 } ?? 7 }
```

with

```swift
    /// The pebble's seed when given, else a different crust per shape, stable across launches.
    private var crustSeed: Int {
        seed.map(Self.shaderSeed) ?? Valence.allCases.firstIndex(of: valence).map { $0 * 31 + 7 } ?? 7
    }

    /// The shader takes the seed as a Float, exact only to 24 bits; fold a 32-bit hash into them.
    static func shaderSeed(_ seed: UInt32) -> Int {
        Int(seed & 0x00FF_FFFF)
    }
```

3. Replace `light: light, unit: unit, seed: seed` with `light: light, unit: unit, seed: crustSeed`.

```bash
grep -n "seed: seed\|crustSeed" apps/ios/Pebbles/Features/Path/Stone/StoneView.swift
```

Expected: no `seed: seed`; `crustSeed` appears twice (its declaration and the shader call).

- [ ] **Step 4: Pass it through `PebbleStoneView`**

In `apps/ios/Pebbles/Features/Path/Stone/PebbleStoneView.swift`:

1. Replace `    var followsTilt: Bool = false` (the stored property, the first occurrence, above `@Environment`) with

```swift
    var followsTilt: Bool = false
    /// `StableHash.seed(pebble.id)` on the Spine (#1130 D1); nil elsewhere keeps the per-shape crust.
    var seed: UInt32?
```

2. Replace the completed-pebble initialiser

```swift
    init(valence: Valence, glyph: PebbleGlyph?, palette: EmotionPalette?, followsTilt: Bool = false) {
        self.valence = valence
        self.carving = .pebble(glyph)
        self.stonePalette = palette.map(StonePalette.init(emotion:)) ?? .brand
        self.followsTilt = followsTilt
    }
```

with

```swift
    init(valence: Valence, glyph: PebbleGlyph?, palette: EmotionPalette?, followsTilt: Bool = false,
         seed: UInt32? = nil) {
        self.valence = valence
        self.carving = .pebble(glyph)
        self.stonePalette = palette.map(StonePalette.init(emotion:)) ?? .brand
        self.followsTilt = followsTilt
        self.seed = seed
    }
```

3. Replace

```swift
    static func blank(followsTilt: Bool = false) -> PebbleStoneView {
        PebbleStoneView(valence: .neutralMedium, carving: .blank, stonePalette: .blank, followsTilt: followsTilt)
    }

    private init(valence: Valence, carving: StoneCarving, stonePalette: StonePalette, followsTilt: Bool) {
        self.valence = valence
        self.carving = carving
        self.stonePalette = stonePalette
        self.followsTilt = followsTilt
    }
```

with

```swift
    static func blank(followsTilt: Bool = false, seed: UInt32? = nil) -> PebbleStoneView {
        PebbleStoneView(valence: .neutralMedium, carving: .blank, stonePalette: .blank, followsTilt: followsTilt,
                        seed: seed)
    }

    private init(valence: Valence, carving: StoneCarving, stonePalette: StonePalette, followsTilt: Bool,
                 seed: UInt32?) {
        self.valence = valence
        self.carving = carving
        self.stonePalette = stonePalette
        self.followsTilt = followsTilt
        self.seed = seed
    }
```

4. Replace `                    carving: carving\n                )` (inside the `StoneView(` call) with

```swift
                    carving: carving,
                    seed: seed
                )
```

If stack B or A changed the `StoneView(` call's argument list on `main`, add `seed: seed` as its last argument and keep the rest as `main` has it.

- [ ] **Step 5: Run the tests**

Run the **Suite command** with `StoneSeedTests`, then `References` (the stone references from B2 must not move: every existing caller passes no seed).
Expected: PASS (1); the `References` suite passes locally for `StoneReferences` and `WelcomeReferences` (recorded on CI by stack B with this runtime pinned). If they drift locally, that is the known local-render drift (B2's calibration note); confirm on CI instead.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Stone/StoneView.swift apps/ios/Pebbles/Features/Path/Stone/PebbleStoneView.swift apps/ios/PebblesTests/Stone/StoneSeedTests.swift
git commit -m "feat(ios): a stone can take its pebble's own seed"
```

---

### Task D2a.6: The rings and the ripple

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Stone/StoneRings.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/Stone/StoneRipple.swift`

These two are drawing only; the geometry and the timings are tested (D2a), the drawing by the references (D2a.10).

- [ ] **Step 1: `StoneRings.swift`**

```swift
import SwiftUI

extension StoneRingGeometry {
    /// The outline scaled by `scale` about the centroid, then moved by `offset`
    /// (the stone's origin inside a larger canvas).
    func path(scaled scale: CGSize, offset: CGPoint) -> Path {
        let transform = CGAffineTransform(translationX: offset.x + centroid.x, y: offset.y + centroid.y)
            .scaledBy(x: scale.width, y: scale.height)
            .translatedBy(x: -centroid.x, y: -centroid.y)
        return outline.applying(transform)
    }
}

/// The halo (the clearing, `Color.pebbles.halo`) and the raked rings (#1130 R3).
/// A press swells both together (R12); under Reduce Motion the rings take ink-2
/// instead (R13). Drawn on a canvas twice the stone's size, centred on it and
/// never clipped, so the halo spills onto the ground.
struct StoneRings: View {
    let geometry: StoneRingGeometry
    let stone: CGSize
    let scale: Double
    let still: Bool

    var body: some View {
        let canvas = CGSize(width: stone.width * 2, height: stone.height * 2)
        let origin = CGPoint(x: stone.width / 2, y: stone.height / 2)
        let ink = still ? Color.pebbles.onSurfaceVariant : Color.pebbles.ring
        Canvas { context, _ in
            context.fill(geometry.path(scaled: geometry.halo, offset: origin), with: .color(Color.pebbles.halo))
            for ring in geometry.rings {
                context.stroke(geometry.path(scaled: ring, offset: origin), with: .color(ink), lineWidth: 1)
            }
        }
        .frame(width: canvas.width, height: canvas.height)
        .scaleEffect(scale, anchor: UnitPoint(x: (origin.x + geometry.centroid.x) / canvas.width,
                                              y: (origin.y + geometry.centroid.y) / canvas.height))
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
```

- [ ] **Step 2: `StoneRipple.swift`**

```swift
import SwiftUI

/// Three stone-shaped waves across the ground (#1130 R12, R14). `SpineStone`
/// mounts it only while a press is live, and it is gone after its last frame.
/// The path is scaled, not the canvas, so every wave stays a 1 pt hairline
/// however far it travels (the web's non-scaling stroke).
struct StoneRipple: View {
    let geometry: StoneRingGeometry
    let stone: CGSize
    let waves: [StoneMotion.WaveSample]
    /// How far the first wave travels, in stone widths.
    let reach: Double

    var body: some View {
        let side = CGSize(width: stone.width * (reach + 1), height: stone.height * (reach + 1))
        let origin = CGPoint(x: (side.width - stone.width) / 2, y: (side.height - stone.height) / 2)
        Canvas { context, _ in
            for wave in waves where wave.alpha > 0 {
                let path = geometry.path(scaled: CGSize(width: wave.scale, height: wave.scale), offset: origin)
                context.stroke(path, with: .color(Color.pebbles.ripple.opacity(wave.alpha)), lineWidth: 1)
            }
        }
        .frame(width: side.width, height: side.height)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
```

- [ ] **Step 3: Build**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Stone/StoneRings.swift apps/ios/Pebbles/Features/Path/Spine/Stone/StoneRipple.swift
git commit -m "feat(ios): the stone's halo, rings and ripple"
```

---

### Task D2a.7: The glint and its light

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Stone/StoneGlintInk.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/Stone/StoneGlint.swift`
- Test: `apps/ios/PebblesTests/Spine/StoneGlintTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import SwiftUI
import Testing
@testable import Pebbles

/// #1130 R12: the glint's strength by material (web stone-tones.ts, which
/// supersedes the rule set's .62), and the four-point spark's outline.
@Suite("Stone glint")
struct StoneGlintTests {
    @Test("gem 1, river 0.30, lava 0.34")
    func strength() {
        #expect(StoneGlintInk.strength(.highlight) == 1)
        #expect(StoneGlintInk.strength(.neutral) == 0.3)
        #expect(StoneGlintInk.strength(.lowlight) == 0.34)
    }

    @Test("the spark is a four-point star of its radius, centred on the origin")
    func spark() {
        let bounds = StoneGlint.spark(radius: 10).boundingRect
        #expect(abs(bounds.minX + 10) < 0.01 && abs(bounds.maxX - 10) < 0.01)
        #expect(abs(bounds.minY + 10) < 0.01 && abs(bounds.maxY - 10) < 0.01)
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/StoneGlintTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `StoneGlintTests`. Expected: build FAIL with "cannot find 'StoneGlintInk' in scope".

- [ ] **Step 3: `StoneGlintInk.swift` (the exempt stone folder, decision 6)**

```swift
import SwiftUI

/// The glint's light (#1130 R12): white in both schemes. It is the light that
/// falls on the stone, not a chrome colour, so it lives with the stone's other
/// literal tones (`StonePalette.blank`) in `Stone/`, which `ThemeLiteralsTests`
/// does not scan.
enum StoneGlintInk {
    static let light = Color.white
    /// The web `StoneSprite`'s gradient peaks.
    static let sheenPeak = 0.75
    static let sweepPeak = 0.72

    /// Gem 1, river 0.30, lava 0.34 (web `stone-tones.ts`).
    static func strength(_ polarity: ValencePolarity) -> Double {
        switch polarity {
        case .highlight: return 1
        case .neutral: return 0.3
        case .lowlight: return 0.34
        }
    }
}
```

- [ ] **Step 4: `StoneGlint.swift`**

```swift
import SwiftUI

/// The glint (#1130 R12–R13), clipped to the stone at its material's strength:
/// the still sheen (Reduce Motion), the sweep band crossing from the light, and
/// the four-point spark. The geometry is the web mock's, as fractions of the
/// stone. Everything rests invisible, so the canvas draws nothing at rest.
struct StoneGlint: View {
    let outline: Path
    let stone: CGSize
    let frame: StoneMotion.Frame
    let strength: Double

    var body: some View {
        Canvas { context, size in
            guard frame.stillSheen > 0 || frame.sweep != nil || frame.sparkAlpha > 0 else { return }
            context.clip(to: outline)
            context.opacity = strength
            let width = size.width
            let height = size.height
            let ink = StoneGlintInk.light
            if frame.stillSheen > 0 {
                var sheen = context
                sheen.opacity = strength * frame.stillSheen
                sheen.translateBy(x: 0.38 * width, y: 0.3 * height)
                sheen.rotate(by: .degrees(-24))
                sheen.scaleBy(x: 1, y: 0.14 * height / (0.3 * width))
                let radius = 0.3 * width
                sheen.fill(Path(ellipseIn: CGRect(x: -radius, y: -radius, width: 2 * radius, height: 2 * radius)),
                           with: .radialGradient(Gradient(colors: [ink.opacity(StoneGlintInk.sheenPeak), ink.opacity(0)]),
                                                 center: .zero, startRadius: 0, endRadius: radius))
            }
            if let sweep = frame.sweep {
                var band = context
                band.translateBy(x: width / 2, y: height / 2)
                band.rotate(by: .degrees(22))
                band.translateBy(x: -width / 2 + sweep * StoneMotion.Sweep.travel * width, y: -height / 2)
                let rect = CGRect(x: -0.55 * width, y: -0.4 * height, width: 0.38 * width, height: 1.8 * height)
                let stops = Gradient(colors: [ink.opacity(0), ink.opacity(StoneGlintInk.sweepPeak), ink.opacity(0)])
                band.fill(Path(rect), with: .linearGradient(stops, startPoint: CGPoint(x: rect.minX, y: 0),
                                                            endPoint: CGPoint(x: rect.maxX, y: 0)))
            }
            if frame.sparkAlpha > 0, frame.sparkScale > 0 {
                var spark = context
                spark.translateBy(x: 0.33 * width, y: 0.25 * height)
                spark.rotate(by: .degrees(frame.sparkRotation))
                spark.scaleBy(x: frame.sparkScale, y: frame.sparkScale)
                spark.fill(Self.spark(radius: 0.085 * width), with: .color(ink.opacity(frame.sparkAlpha)))
            }
        }
        .frame(width: stone.width, height: stone.height)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// The mock's four-point star about the origin: quadratic arms pinched to 16 % of the radius.
    static func spark(radius r: CGFloat) -> Path {
        let k = r * 0.16
        var path = Path()
        path.move(to: CGPoint(x: 0, y: -r))
        path.addQuadCurve(to: CGPoint(x: r, y: 0), control: CGPoint(x: k, y: -k))
        path.addQuadCurve(to: CGPoint(x: 0, y: r), control: CGPoint(x: k, y: k))
        path.addQuadCurve(to: CGPoint(x: -r, y: 0), control: CGPoint(x: -k, y: k))
        path.addQuadCurve(to: CGPoint(x: 0, y: -r), control: CGPoint(x: -k, y: -k))
        path.closeSubpath()
        return path
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run the **Suite command** with `StoneGlintTests`, then `ThemeLiteralsTests`. Expected: PASS (2), then PASS (`StoneGlintInk.swift` is under the exempt `Features/Path/Stone/`, and `StoneGlint.swift` names no literal).

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Stone/StoneGlintInk.swift apps/ios/Pebbles/Features/Path/Spine/Stone/StoneGlint.swift apps/ios/PebblesTests/Spine/StoneGlintTests.swift
git commit -m "feat(ios): the stone's glint, sweep and spark"
```

---

### Task D2a.8: `SpineStone`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Stone/SpineStone.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI

/// The Spine's stone (#1130 D1, R2–R3, R12–R14), bottom to top: the halo and
/// the rings, the ripple (mounted only while a press is live), the existing lit
/// stone at rest light, then the glint. It lays out at the stone's phone size;
/// the halo, the rings and the ripple overflow it unclipped (D4).
///
/// The press is a value (`StonePress`) the caller owns as `@State` and clears
/// with `.stonePressLifetime`. The timeline runs only while there is one.
struct SpineStone: View {
    let valence: Valence
    let glyph: PebbleGlyph?
    let palette: EmotionPalette?
    let isBlank: Bool
    let seed: UInt32
    var press: StonePress?
    /// References and the lab: one instant of a press, without a timeline.
    var frozenAt: Double?

    init(valence: Valence, glyph: PebbleGlyph?, palette: EmotionPalette?, isBlank: Bool, seed: UInt32,
         press: StonePress? = nil, frozenAt: Double? = nil) {
        self.valence = valence
        self.glyph = glyph
        self.palette = palette
        self.isBlank = isBlank
        self.seed = seed
        self.press = press
        self.frozenAt = frozenAt
    }

    /// A moment's stone: its pebble's valence, glyph and seed, or the blank stone.
    init(moment: MomentSummary, palette: EmotionPalette?, press: StonePress? = nil, frozenAt: Double? = nil) {
        self.init(valence: moment.pebble?.valence ?? .neutralMedium, glyph: moment.pebble?.glyph, palette: palette,
                  isBlank: moment.pebble == nil, seed: StableHash.seed(moment.pebble?.id ?? moment.id),
                  press: press, frozenAt: frozenAt)
    }

    private var size: ValenceSizeGroup { valence.sizeGroup }

    var body: some View {
        // One TimelineView whatever the press, so the lit stone keeps its identity
        // (and its tilt state) when a press starts and ends.
        TimelineView(.animation(minimumInterval: nil, paused: press == nil || frozenAt != nil)) { context in
            layers(frame(at: context.date))
        }
    }

    private func frame(at date: Date) -> StoneMotion.Frame {
        if let frozenAt { return StoneMotion.frame(at: frozenAt, still: false, size: size) }
        guard let press else { return StoneMotion.Frame() }
        return StoneMotion.frame(at: press.elapsed(at: date), still: press.still, size: size)
    }

    private func layers(_ frame: StoneMotion.Frame) -> some View {
        let stone = SpineLayout.stoneSize(size)
        let geometry = StoneRingGeometry.forStone(valence)
        return ZStack {
            if let geometry {
                StoneRings(geometry: geometry, stone: stone, scale: frame.rings, still: frame.stillRings)
                if !frame.waves.isEmpty {
                    StoneRipple(geometry: geometry, stone: stone, waves: frame.waves,
                                reach: StoneMotion.Wave.reach[size] ?? 5.6)
                }
            }
            litStone
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

    @ViewBuilder
    private var litStone: some View {
        if isBlank {
            PebbleStoneView.blank(seed: seed)
        } else {
            PebbleStoneView(valence: valence, glyph: glyph, palette: palette, seed: seed)
        }
    }
}
```

- [ ] **Step 2: Build**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Stone/SpineStone.swift
git commit -m "feat(ios): the spine stone, the lit stone in its rings"
```

---

### Task D2a.9: The Spine lab and its stone gallery

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineLabView.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineStoneGallery.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`

- [ ] **Step 1: The gallery (shared with the references)**

Create `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineStoneGallery.swift`:

```swift
#if DEBUG
import SwiftUI

/// The nine Spine stones (size × polarity) on the brand palette, then the blank
/// stone, at their phone widths with their rings and halo: the lab's gallery and
/// the `gallery` / `midPress` references. `pressable` makes each one a button.
struct SpineStoneGallery: View {
    var pressable = false
    var frozenAt: Double?
    /// The same stone with six seeds: the crust varies by pebble (`seeds` reference).
    var seeds = false

    var body: some View {
        VStack(spacing: 28) {
            if seeds {
                HStack(spacing: 20) {
                    ForEach(0..<6, id: \.self) { index in
                        SpineStoneCell(valence: .neutralLarge, isBlank: false, seed: StableHash.fnv1a("seed-\(index)"),
                                       pressable: pressable, frozenAt: frozenAt)
                    }
                }
            } else {
                ForEach(ValenceSizeGroup.allCases) { size in
                    HStack(spacing: 28) {
                        ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                            if let valence = Valence.allCases.first(where: {
                                $0.sizeGroup == size && $0.polarity == polarity
                            }) {
                                SpineStoneCell(valence: valence, isBlank: false, seed: StableHash.fnv1a("\(valence)"),
                                               pressable: pressable, frozenAt: frozenAt)
                            }
                        }
                    }
                }
                SpineStoneCell(valence: .neutralMedium, isBlank: true, seed: StableHash.fnv1a("blank"),
                               pressable: pressable, frozenAt: frozenAt)
            }
        }
        .padding(.vertical, 56)
        .frame(maxWidth: .infinity)
        .background(Color.pebbles.surface)
    }
}

private struct SpineStoneCell: View {
    let valence: Valence
    let isBlank: Bool
    let seed: UInt32
    let pressable: Bool
    let frozenAt: Double?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var press: StonePress?

    var body: some View {
        let stone = SpineStone(valence: valence, glyph: nil, palette: nil, isBlank: isBlank, seed: seed,
                               press: press, frozenAt: frozenAt)
            .frame(width: 80, height: 80)
        if pressable {
            Button {
                TapHaptics.play(.selection)
                press = .next(after: press, still: reduceMotion, now: .now)
            } label: {
                stone
            }
            .buttonStyle(.plain)
            .stonePressLifetime($press)
            .accessibilityLabel(Text(verbatim: isBlank ? "blank" : "\(valence)"))
        } else {
            stone
        }
    }
}
#endif
```

- [ ] **Step 2: The lab**

Create `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineLabView.swift`:

```swift
#if DEBUG
import SwiftUI

/// The Spine's debug lab (#1130), Settings → Developer, beside the stone lab.
/// Until D5a the Spine shows only here and in the references; each part adds a
/// section. Verbatim text throughout, so nothing reaches the string catalogue.
struct SpineLabView: View {
    enum Section: String, CaseIterable, Identifiable {
        case stones
        var id: String { rawValue }
    }

    @Environment(\.dismiss) private var dismiss
    @State private var section: Section = .stones

    var body: some View {
        NavigationStack {
            ScrollView {
                switch section {
                case .stones:
                    SpineStoneGallery(pressable: true)
                }
            }
            .background(Color.pebbles.surface)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Picker(selection: $section) {
                        ForEach(Section.allCases) { Text(verbatim: $0.rawValue).tag($0) }
                    } label: {
                        Text(verbatim: "Section")
                    }
                    .pickerStyle(.segmented)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { dismiss() } label: { Text(verbatim: "Done") }
                }
            }
        }
    }
}
#endif
```

- [ ] **Step 3: The Settings row**

In `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`:

1. Replace

```swift
    #if DEBUG
    @State private var isPresentingStoneLab = false
    #endif
```

with

```swift
    #if DEBUG
    @State private var isPresentingStoneLab = false
    @State private var isPresentingSpineLab = false
    #endif
```

2. Replace

```swift
            .fullScreenCover(isPresented: $isPresentingStoneLab) {
                StoneLabView()
            }
```

with

```swift
            .fullScreenCover(isPresented: $isPresentingStoneLab) {
                StoneLabView()
            }
            .fullScreenCover(isPresented: $isPresentingSpineLab) {
                SpineLabView()
            }
```

3. Replace

```swift
                Label {
                    Text(verbatim: "Stone lab")
                } icon: {
                    Image(systemName: "sparkles")
                }
            }
            .buttonStyle(.plain)
            .pebblesListRow(position: .only)
```

with

```swift
                Label {
                    Text(verbatim: "Stone lab")
                } icon: {
                    Image(systemName: "sparkles")
                }
            }
            .buttonStyle(.plain)
            .pebblesListRow(position: .top)
            Button {
                isPresentingSpineLab = true
            } label: {
                Label {
                    Text(verbatim: "Spine lab")
                } icon: {
                    Image(systemName: "line.3.horizontal.decrease")
                }
            }
            .buttonStyle(.plain)
            .pebblesListRow(position: .bottom)
```

- [ ] **Step 4: Build and look on the simulator**

Run **Build**, then install and launch on the stack's simulator:

```bash
SIM=$(xcrun simctl list devices | grep "iPhone 17 stack-d" | grep -oE '[0-9A-F]{8}(-[0-9A-F]{4}){3}-[0-9A-F]{12}' | head -1)
DD="$HOME/Library/Developer/Xcode/DerivedData/Pebbles-stack-d"
xcrun simctl install "$SIM" "$DD/Build/Products/Debug-iphonesimulator/Pebbles.app"
xcrun simctl launch "$SIM" app.pbbls.ios
```

The simulator has no signed-in session (stack A lesson), so Settings is not reachable there; the lab is checked on the device in Task D2a.11. Here only confirm the build installs and launches.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Debug apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift
git commit -m "feat(ios): a spine lab with the pressable stone gallery"
```

---

### Task D2a.10: The stone references

**Files:**
- Create: `apps/ios/PebblesTests/References/SpineStoneReferences.swift`

- [ ] **Step 1: Write the suite**

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D2a: the nine Spine stones and the blank one, in their rings and
    /// halo, at rest and half a second into a press; and one stone under six
    /// seeds. Paper and Night, English (no text). Android: LitStoneGallery*,
    /// LitStoneSeeds* (`apps/android/app/src/screenshotTest/.../LitStoneScreenshots.kt`).
    @Suite("Spine stone references")
    @MainActor
    struct SpineStoneReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func gallery(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(SpineStoneGallery(), named: "gallery", config: ReferenceConfig(scheme: scheme))
        }

        /// The dip, the rings' swell, the sweep, the spark and two waves.
        @Test(arguments: ReferenceScheme.allCases)
        func midPress(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(SpineStoneGallery(frozenAt: 0.5), named: "midPress",
                                         config: ReferenceConfig(scheme: scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func seeds(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(SpineStoneGallery(seeds: true), named: "seeds",
                                         config: ReferenceConfig(scheme: scheme))
        }
    }
}
```

- [ ] **Step 2: Look locally (not committed)**

Run **Look** at `SpineStoneReferences`. Read the six PNGs: each stone sits in a filled halo with one, two or three hairline rings; `midPress` shows smaller, dipped stones, swollen rings, a bright band across each gem and faint waves; `seeds` shows six river stones with six different crusts. Night uses the dark ground. A blank (transparent or empty) stone means the shader drew nothing under `ImageRenderer`: B2's window fallback applies (stack B §D6), and it is B's harness to fix, not this part's. Then clean as the **Look** command says.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/PebblesTests/References/SpineStoneReferences.swift
git commit -m "test(ios): spine stone references at rest, mid-press and by seed"
```

---

### Task D2a.11: D2a verification, PR and CI references

**Files:** none (the bot commits the PNGs).

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** (the three new references fail locally with "no reference", expected) and **Build**.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <D2a-number> --title "feat(ios): the spine stone, its press, rings, ripple and glint" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Part of #1130`; key files; **new pattern for review:** `StonePressRequests`, observable state consumed as a request (D8), with `CardFace` beside it for stack E (decision 4); the 24-bit seed fold and why (the shader's Float); decision 6 (the glint's white in `Stone/`); the lab entry; the device check below; "no visible change outside the lab". No Lab Note, no `F-…` ids.

- [ ] **Step 3: Rebaseline on CI**

Run **Rebaseline on CI** with this PR's number. Compare the bot's `gallery-paper-en.png` / `gallery-night-en.png` with Android's `LitStoneGalleryLight` / `LitStoneGalleryDark` PNGs (`apps/android/app/src/screenshotTestDebug/reference/`): rings, halo and the ground tone must read alike; the lit stone itself is iOS's own (D1). The CI check is green after the approved re-run.

- [ ] **Step 4: Device check (iPhone 15; the orchestrator runs it with the maintainer)**

Settings → Developer → Spine lab:
- Press each of the ten stones: the stone dips and springs back, the rings swell once, three waves travel out and fade, a band crosses a gem brightly and a lava stone faintly, a spark lands; all of it is gone after about 2.6 s.
- With Reduce Motion on: no waves, no band; the rings darken and a still sheen holds for under a second.
- A second press during the first restarts it cleanly.
- Paper and Night.

Record pass/fail in the PR.

---
## Part D2b — `feat/1130-ios-spine-card`

PR title: `feat(ios): the spine deck card, its photo ratio and its frame`. Body opens `Part of #1130`. Visible in the lab and the references only. The card's data comes first (Tasks D2b.1–D2b.5), then the card (Tasks D2b.6–D2b.10).

### Task D2b.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1130-ios-spine-card
git branch --show-current
```

Expected: `feat/1130-ios-spine-card`.

---

### Task D2b.1: The `rakeLight` token (decision 1)

**Files:**
- Modify: `apps/web/lib/theme/ios-colorsets.ts`
- Modify: `apps/web/lib/theme/ios-colorsets.test.ts`
- Regenerated: `apps/ios/Pebbles/Resources/Assets.xcassets/M3/rakeLight.colorset/Contents.json`, the widget catalogue's copy, `apps/ios/Shared/Theme/PebblesColors.swift`

The rake's lit line is Android's `SpineColors.rakeLight`: white @ 0.55 on Paper, the cool `#DCE2EA` @ 0.075 at Night. Neither is a scheme role, and `Features/` may not name a hex, so it becomes one generated token, and the generator learns a constant-hex source beside its `[role, alpha]` sources. The high-contrast appearances take the same values (Paper's for light high, Night's for dark high). The rake's shadow and the card's shadow ink stay blends in `SpineColors.swift` (Task D2b.2), as Android's are.

- [ ] **Step 1: Make the generator test expect it**

In `apps/web/lib/theme/ios-colorsets.test.ts`:

1. Replace

```ts
  it("emits all nine Spine tokens", () => {
    for (const token of ["halo", "stock", "rule", "cardRule", "line", "seam", "teeth", "ring", "ripple"]) {
```

with

```ts
  it("emits all ten Spine tokens", () => {
    for (const token of ["halo", "stock", "rule", "cardRule", "line", "seam", "teeth", "ring", "ripple", "rakeLight"]) {
```

2. Replace

```ts
      "halo", "stock", "rule", "cardRule", "line", "seam", "teeth", "ring", "ripple",
      "googleCapsuleInk",
```

with

```ts
      "halo", "stock", "rule", "cardRule", "line", "seam", "teeth", "ring", "ripple", "rakeLight",
      "googleCapsuleInk",
```

3. Replace `  it("renders 62 roles: 48 M3 + 4 sand + 9 Spine + the pin", () => {` with `  it("renders 63 roles: 48 M3 + 4 sand + 10 Spine + the pin", () => {`, and the `toHaveLength(62)` on the next lines with `toHaveLength(63)`.

4. After the `it("takes ring from onSurfaceVariant Paper and onSurface Night", …)` case, add:

```ts
  it("writes the rake's lit line from constants, not roles (#1130)", () => {
    const rakeLight = colorset(files, "rakeLight")
    expect(hexes(rakeLight)).toEqual(["#FFFFFF", "#DCE2EA", "#FFFFFF", "#DCE2EA"])
    expect(alphas(rakeLight)).toEqual(["0.550", "0.075", "0.550", "0.075"])
  })
```

The appearance order of `hexes` / `alphas` is the file's existing one (light, dark, light high, dark high), as the `ring` case above shows.

- [ ] **Step 2: Run it to verify it fails**

```bash
npm run test --workspace=@pbbls/web -- lib/theme/ios-colorsets.test.ts
```

Expected: FAIL ("no colorset for rakeLight", and 62 ≠ 63).

- [ ] **Step 3: Teach the generator a constant source and add the row**

In `apps/web/lib/theme/ios-colorsets.ts`:

1. Replace

```ts
type Source = readonly [role: string, alpha: number]
```

with

```ts
/** A scheme role at an alpha, or a constant the Android scheme writes as a hex (#1130). */
type Source = readonly [role: string, alpha: number] | { readonly hex: string; readonly alpha: number }
```

2. Replace

```ts
      perAppearance((appearance) => {
        const [role, alpha] = appearance === "light" || appearance === "lightHigh" ? token.paper : token.night
        return { hex: hexFor(appearance, role), alpha }
      }),
```

with

```ts
      perAppearance((appearance) => {
        const source = appearance === "light" || appearance === "lightHigh" ? token.paper : token.night
        if ("hex" in source) return { hex: source.hex, alpha: source.alpha }
        const [role, alpha] = source
        return { hex: hexFor(appearance, role), alpha }
      }),
```

3. Replace

```ts
  { name: "ripple", paper: ["onSurface", 0.55], night: ["onSurface", 0.5] },
]
```

with

```ts
  { name: "ripple", paper: ["onSurface", 0.55], night: ["onSurface", 0.5] },
  // #1130: the rake's lit line, Android's SpineColors.rakeLight. White on Paper and
  // a cool #DCE2EA at Night are constants there, so they are constants here:
  // Features/ never names a hex.
  { name: "rakeLight", paper: { hex: "#FFFFFF", alpha: 0.55 }, night: { hex: "#DCE2EA", alpha: 0.075 } },
]
```

4. Replace the doc comment's `the nine scheme-only Spine tokens` with `the scheme-only Spine tokens (and one constant, rakeLight)`.

- [ ] **Step 4: Regenerate and run**

```bash
npm run generate:ios-colors --workspace=@pbbls/web
git status --short apps/ios
npm run test --workspace=@pbbls/web -- lib/theme/ios-colorsets.test.ts
grep -cE "^    case rakeLight$" apps/ios/Shared/Theme/PebblesColors.swift
```

Expected: two new `rakeLight.colorset` folders (app and widget) and a modified `PebblesColors.swift`, nothing else; the web test PASSES, the drift gate included; the case count is `1`. `renderColorset` already rejects anything but `#RRGGBB`, so a malformed constant fails the generator.

- [ ] **Step 5: Build iOS**

Run **Build**. Expected: `** BUILD SUCCEEDED **` (`Color.pebbles.rakeLight` now exists).

- [ ] **Step 6: Commit**

```bash
git add apps/web/lib/theme/ios-colorsets.ts apps/web/lib/theme/ios-colorsets.test.ts apps/ios/Pebbles/Resources/Assets.xcassets/M3 apps/ios/PebblesWidget apps/ios/Shared/Theme/PebblesColors.swift
git commit -m "feat(ios): generate the rake's lit line, a constant spine tone"
```

If `git status` in Step 4 showed the widget catalogue under another path, add that path instead of `apps/ios/PebblesWidget`.

---

### Task D2b.2: `SpineColors`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/SpineColors.swift`
- Test: `apps/ios/PebblesTests/Spine/SpineColorsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import SwiftUI
import Testing
@testable import Pebbles

/// #1130 R5: which palette slot paints the card's edge and its spot ink, by
/// valence and scheme, and how strong the field's tint and pattern are.
@Suite("SpineColors")
struct SpineColorsTests {
    @Test("the edge: Paper high primary, neutral secondary, low dark; Night secondary, primary, shaded")
    func edge() {
        #expect(SpineColors.edgeSlot(.highlight, scheme: .light) == .primary)
        #expect(SpineColors.edgeSlot(.neutral, scheme: .light) == .secondary)
        #expect(SpineColors.edgeSlot(.lowlight, scheme: .light) == .dark)
        #expect(SpineColors.edgeSlot(.highlight, scheme: .dark) == .secondary)
        #expect(SpineColors.edgeSlot(.neutral, scheme: .dark) == .primary)
        #expect(SpineColors.edgeSlot(.lowlight, scheme: .dark) == .shaded)
    }

    @Test("the spot is primary on Paper and secondary at Night")
    func spot() {
        #expect(SpineColors.spotSlot(scheme: .light) == .primary)
        #expect(SpineColors.spotSlot(scheme: .dark) == .secondary)
    }

    @Test("the field tints at 9 % / 13 % and patterns at 34 % / 40 %")
    func strengths() {
        #expect(SpineColors.tintK(scheme: .light) == 0.09 && SpineColors.tintK(scheme: .dark) == 0.13)
        #expect(SpineColors.patternK(scheme: .light) == 0.34 && SpineColors.patternK(scheme: .dark) == 0.40)
    }

    @Test("a bare moment wears the blank ramp, a pebble with no emotion the brand one")
    func palettes() {
        #expect(SpineColors.palette(nil, isBlank: true) == .blank)
        #expect(SpineColors.palette(nil, isBlank: false) == .brand)
    }

    /// Android's SpineColors: rakeShadow = onSurface @ .20 Paper, black @ .34 Night;
    /// shadow = onSurface Paper, black Night, at shadowAlpha .30 / .62.
    @Test("the rake shadow and the shadow ink mirror Android, black being scrim")
    func shadows() {
        #expect(SpineColors.rakeShadowSource(scheme: .light) == (PebblesColorRole.onSurface, 0.2))
        #expect(SpineColors.rakeShadowSource(scheme: .dark) == (PebblesColorRole.scrim, 0.34))
        #expect(SpineColors.shadowInkRole(scheme: .light) == .onSurface)
        #expect(SpineColors.shadowInkRole(scheme: .dark) == .scrim)
        #expect(SpineColors.shadowAlpha(scheme: .light) == 0.30 && SpineColors.shadowAlpha(scheme: .dark) == 0.62)
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/SpineColorsTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `SpineColorsTests`. Expected: build FAIL with "cannot find 'SpineColors' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/SpineColors.swift`:

```swift
import SwiftUI

/// The Spine's blends with a pebble's emotion palette (#1130 R5, R17). The
/// palette is runtime data (`v_emotions_with_palette`), so these are computed
/// here and are never colorsets (stack A §2.4). The scheme-only tones are
/// `Color.pebbles.*`, except the rake shadow and the shadow ink, which Android
/// also blends from the scheme in `SpineColors.kt` (decision 1). The slot and
/// role choices are pure and tested; the colour is the palette's or the role's.
enum SpineColors {
    /// The stone palette a moment wears: blank for a bare moment, the brand
    /// ramp for a pebble with no known emotion.
    static func palette(_ emotion: EmotionPalette?, isBlank: Bool) -> StonePalette {
        if isBlank { return .blank }
        return emotion.map(StonePalette.init(emotion:)) ?? .brand
    }

    /// The card's painted edge: Paper high `primary`, neutral `secondary`, low
    /// `dark`; Night high `secondary`, neutral `primary`, low `shaded`.
    static func edgeSlot(_ polarity: ValencePolarity, scheme: ColorScheme) -> StonePalette.Slot {
        let night = scheme == .dark
        switch polarity {
        case .highlight: return night ? .secondary : .primary
        case .neutral: return night ? .primary : .secondary
        case .lowlight: return night ? .shaded : .dark
        }
    }

    /// The spot ink: the field's tint and pattern, the rail's emotion. Paper `primary`, Night `secondary`.
    static func spotSlot(scheme: ColorScheme) -> StonePalette.Slot {
        scheme == .dark ? .secondary : .primary
    }

    /// The spot over the stock: `color-mix(spot 9 %, stock)`, 13 % at Night.
    static func tintK(scheme: ColorScheme) -> Double { scheme == .dark ? 0.13 : 0.09 }

    /// The valence pattern's strength over the field.
    static func patternK(scheme: ColorScheme) -> Double { scheme == .dark ? 0.40 : 0.34 }

    static func edge(_ palette: StonePalette, polarity: ValencePolarity, scheme: ColorScheme) -> Color {
        palette.color(edgeSlot(polarity, scheme: scheme))
    }

    static func spot(_ palette: StonePalette, scheme: ColorScheme) -> Color {
        palette.color(spotSlot(scheme: scheme))
    }

    // MARK: - Scheme blends Android computes in `SpineColors.kt` (decision 1)

    /// The rake's groove shadow: `onSurface` @ 0.20 on Paper, black @ 0.34 at
    /// Night. Black is `scrim`, which M3 keeps `#000000` in every scheme
    /// (`Features/` may not name `.black`). The lit line is the `rakeLight` token.
    static func rakeShadowSource(scheme: ColorScheme) -> (role: PebblesColorRole, alpha: Double) {
        scheme == .dark ? (.scrim, 0.34) : (.onSurface, 0.2)
    }

    static func rakeShadow(scheme: ColorScheme) -> Color {
        let source = rakeShadowSource(scheme: scheme)
        return role(source.role).opacity(source.alpha)
    }

    /// The shadow ink under the card: `onSurface` on Paper, black at Night,
    /// carried with Android's `shadowAlpha`.
    static func shadowInkRole(scheme: ColorScheme) -> PebblesColorRole {
        scheme == .dark ? .scrim : .onSurface
    }

    static func shadowInk(scheme: ColorScheme) -> Color {
        role(shadowInkRole(scheme: scheme))
    }

    /// Android's `shadowAlpha`: 0.30 on Paper, 0.62 at Night.
    static func shadowAlpha(scheme: ColorScheme) -> Double {
        scheme == .dark ? 0.62 : 0.30
    }

    /// The two roles the blends use, named through `Color.pebbles`.
    private static func role(_ role: PebblesColorRole) -> Color {
        role == .scrim ? Color.pebbles.scrim : Color.pebbles.onSurface
    }
}
```

`StonePalette` is `Equatable` and `.blank` / `.brand` are its statics (`Features/Path/Stone/StonePalette.swift`).

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `SpineColorsTests`. Expected: PASS (5).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/SpineColors.swift apps/ios/PebblesTests/Spine/SpineColorsTests.swift
git commit -m "feat(ios): the spine's palette blends and its shadow inks"
```

---

### Task D2b.3: `SnapAspectCache`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/SnapAspectCache.swift`
- Test: `apps/ios/PebblesTests/Spine/SnapAspectCacheTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Foundation
import ImageIO
import Testing
import UniformTypeIdentifiers
@testable import Pebbles

/// #1130 D3: a photo's ratio is read from its header, cached by storage path for
/// the session, and a probe that times out yields nil once and is never retried.
/// Signing goes through the existing `FakeSignedURLProvider` (SnapURLCacheTests).
@Suite("SnapAspectCache")
@MainActor
struct SnapAspectCacheTests {
    /// Counts loads; `@unchecked` because the loader is `@Sendable` and the tests are serial.
    final class Loads: @unchecked Sendable {
        var count = 0
    }

    private static func png(width: Int, height: Int) -> Data {
        let space = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
        let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                                space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        let data = NSMutableData()
        if let image = context?.makeImage(),
           let destination = CGImageDestinationCreateWithData(data, UTType.png.identifier as CFString, 1, nil) {
            CGImageDestinationAddImage(destination, image, nil)
            CGImageDestinationFinalize(destination)
        }
        return data as Data
    }

    private func cache(loads: Loads, timeout: Duration = .seconds(4),
                       load: @escaping @Sendable () async throws -> Data) -> (SnapAspectCache, FakeSignedURLProvider) {
        let provider = FakeSignedURLProvider()
        let urls = SnapURLCache(provider: provider, now: { Date() })
        let aspects = SnapAspectCache(urls: urls, load: { _ in
            loads.count += 1
            return try await load()
        }, timeout: timeout)
        return (aspects, provider)
    }

    @Test("reads the ratio from the header and caches it by path")
    func caches() async {
        let loads = Loads()
        let data = Self.png(width: 30, height: 20)
        let (aspects, provider) = cache(loads: loads) { data }
        #expect(await aspects.probe("a/b") == 1.5)
        #expect(await aspects.probe("a/b") == 1.5)
        #expect(aspects.ratio(for: "a/b") == 1.5)
        #expect(loads.count == 1)
        #expect(provider.callCount == 1)
    }

    @Test("a timeout yields nil once, and the path is not probed again this session")
    func timeout() async {
        let loads = Loads()
        let (aspects, _) = cache(loads: loads, timeout: .milliseconds(50)) {
            try await Task.sleep(for: .seconds(5))
            return Data()
        }
        #expect(await aspects.probe("slow") == nil)
        #expect(await aspects.probe("slow") == nil)
        #expect(loads.count == 1)
    }

    @Test("an unreadable body is a failure too, and is not retried")
    func unreadable() async {
        let loads = Loads()
        let (aspects, _) = cache(loads: loads) { Data("not an image".utf8) }
        #expect(await aspects.probe("bad") == nil)
        #expect(await aspects.probe("bad") == nil)
        #expect(loads.count == 1)
    }

    @Test("a primed ratio needs no probe")
    func primed() async {
        let loads = Loads()
        let (aspects, provider) = cache(loads: loads) { Data() }
        aspects.prime("fixture/x.jpg", ratio: 2 / 3)
        #expect(await aspects.probe("fixture/x.jpg") == 2 / 3)
        #expect(loads.count == 0 && provider.callCount == 0)
    }

    @Test("the header reader swaps the sides of a rotated photo")
    func header() {
        #expect(SnapAspectCache.ratio(ofImage: Self.png(width: 40, height: 10)) == 4)
        #expect(SnapAspectCache.ratio(ofImage: Data()) == nil)
        #expect(SnapAspectCache.ratio(width: 40, height: 10, orientation: 6) == 0.25)
        #expect(SnapAspectCache.ratio(width: 40, height: 10, orientation: 1) == 4)
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/SnapAspectCacheTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `SnapAspectCacheTests`. Expected: build FAIL with "cannot find 'SnapAspectCache' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/SnapAspectCache.swift`:

```swift
import CoreGraphics
import Foundation
import ImageIO
import Observation
import os

/// Photo ratios for the Spine's cards (#1130 D3). The thumb is signed through
/// `SnapURLCache`, downloaded, and its header read with
/// `CGImageSourceCopyPropertiesAtIndex`, which does not decode. Ratios are kept
/// by storage path for the session; a probe that fails or passes 4 s logs a
/// warning once and is not retried in the session. Neither `path_moments` nor
/// `MomentDetail.snaps` carries dimensions. New pattern, named in the D2b PR.
@Observable @MainActor
final class SnapAspectCache {
    typealias Loader = @Sendable (URL) async throws -> Data

    private(set) var ratios: [String: CGFloat] = [:]
    @ObservationIgnored private var failed: Set<String> = []
    @ObservationIgnored private var inflight: [String: Task<CGFloat?, Never>] = [:]
    @ObservationIgnored private let urls: SnapURLCache
    @ObservationIgnored private let load: Loader
    @ObservationIgnored private let timeout: Duration
    @ObservationIgnored private let logger = Logger(subsystem: "app.pbbls.ios", category: "snap-aspect")

    init(urls: SnapURLCache, load: @escaping Loader = SnapAspectCache.download, timeout: Duration = .seconds(4)) {
        self.urls = urls
        self.load = load
        self.timeout = timeout
    }

    nonisolated static let download: Loader = { url in
        try await URLSession.shared.data(from: url).0
    }

    func ratio(for path: String) -> CGFloat? {
        ratios[path]
    }

    /// References, the lab and the fixture week: a known ratio, no probe.
    func prime(_ path: String, ratio: CGFloat) {
        ratios[path] = ratio
    }

    /// `path`'s photo ratio, probed at most once per session; concurrent asks share one probe.
    @discardableResult
    func probe(_ path: String) async -> CGFloat? {
        if let known = ratios[path] { return known }
        if failed.contains(path) { return nil }
        if let running = inflight[path] { return await running.value }
        let task = Task { await self.fetch(path) }
        inflight[path] = task
        let ratio = await task.value
        inflight[path] = nil
        if let ratio { ratios[path] = ratio } else { failed.insert(path) }
        return ratio
    }

    /// The focused week's photos and its pager neighbours' (§Data flow).
    func prefetch(_ paths: [String]) async {
        for path in Set(paths) where ratios[path] == nil && !failed.contains(path) {
            await probe(path)
        }
    }

    private func fetch(_ path: String) async -> CGFloat? {
        do {
            let url = try await urls.signedURLs(storagePath: path).thumb
            let data = try await Self.withTimeout(timeout) { [load] in try await load(url) }
            guard let ratio = Self.ratio(ofImage: data) else {
                logger.warning("snap ratio unreadable for \(path, privacy: .public)")
                return nil
            }
            return ratio
        } catch {
            logger.warning(
                "snap ratio probe failed for \(path, privacy: .public): \(error.localizedDescription, privacy: .private)"
            )
            return nil
        }
    }

    private struct Timeout: Error {}

    private nonisolated static func withTimeout(
        _ limit: Duration, _ work: @escaping @Sendable () async throws -> Data
    ) async throws -> Data {
        try await withThrowingTaskGroup(of: Data.self) { group in
            group.addTask { try await work() }
            group.addTask {
                try await Task.sleep(for: limit)
                throw Timeout()
            }
            defer { group.cancelAll() }
            guard let first = try await group.next() else { throw Timeout() }
            return first
        }
    }

    /// Width / height from the header, without decoding the pixels.
    nonisolated static func ratio(ofImage data: Data) -> CGFloat? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = (properties[kCGImagePropertyPixelWidth] as? NSNumber)?.doubleValue,
              let height = (properties[kCGImagePropertyPixelHeight] as? NSNumber)?.doubleValue
        else { return nil }
        let orientation = (properties[kCGImagePropertyOrientation] as? NSNumber)?.intValue ?? 1
        return ratio(width: width, height: height, orientation: orientation)
    }

    /// EXIF orientations 5–8 are a quarter turn: the photo shows with its sides swapped.
    nonisolated static func ratio(width: Double, height: Double, orientation: Int) -> CGFloat? {
        guard width > 0, height > 0 else { return nil }
        return (5...8).contains(orientation) ? CGFloat(height / width) : CGFloat(width / height)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `SnapAspectCacheTests`. Expected: PASS (5).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/SnapAspectCache.swift apps/ios/PebblesTests/Spine/SnapAspectCacheTests.swift
git commit -m "feat(ios): read a photo's ratio from its header, once per session"
```

---

### Task D2b.4: `CardFrameRegistry` and `SpineSnapSource`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/CardFrameRegistry.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/SpineSnapSource.swift`
- Test: `apps/ios/PebblesTests/Spine/CardFrameRegistryTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Foundation
import Observation
import Testing
@testable import Pebbles

/// #1130 §Frame registry, stack E's canonical shape: frames are reported and
/// forgotten without invalidating anyone; `hidden` is observed.
@Suite("CardFrameRegistry")
@MainActor
struct CardFrameRegistryTests {
    final class Flag: @unchecked Sendable {
        var raised = false
    }

    private let id = UUID(uuidString: "00000000-0000-4000-8000-000000000001") ?? UUID()

    @Test("report keeps the latest frame, forget drops it")
    func reportAndForget() {
        let registry = CardFrameRegistry()
        registry.report(id, frame: CGRect(x: 0, y: 0, width: 10, height: 10))
        registry.report(id, frame: CGRect(x: 0, y: 40, width: 10, height: 10))
        #expect(registry.frames[id]?.minY == 40)
        registry.forget(id)
        #expect(registry.frames[id] == nil)
    }

    @Test("a reported frame does not invalidate a reader; hiding a slot does")
    func observation() {
        let registry = CardFrameRegistry()
        let framesChanged = Flag()
        withObservationTracking { _ = registry.frames } onChange: { framesChanged.raised = true }
        registry.report(id, frame: .zero)
        #expect(!framesChanged.raised)

        let hiddenChanged = Flag()
        withObservationTracking { _ = registry.hidden } onChange: { hiddenChanged.raised = true }
        registry.hidden = id
        #expect(hiddenChanged.raised)
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/CardFrameRegistryTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `CardFrameRegistryTests`. Expected: build FAIL with "cannot find 'CardFrameRegistry' in scope".

- [ ] **Step 3: Implement the registry**

Create `apps/ios/Pebbles/Features/Path/Spine/CardFrameRegistry.swift`:

```swift
import CoreGraphics
import Foundation
import Observation

/// Where each Spine card and entry sits on screen, for stack E's flight
/// (#1130 §Frame registry, the canonical shape stack E consumes as written).
/// `RootView` creates it and injects it; the slots report global rects.
///
/// `frames` is unobserved: E reads it imperatively at open and close, and an
/// observed dictionary written on every scroll frame would invalidate every
/// reader. `hidden` is observed: that slot hides while E flies. A scrolled-out
/// slot forgets its frame, so E falls back to its fade.
@Observable @MainActor
final class CardFrameRegistry {
    @ObservationIgnored private(set) var frames: [UUID: CGRect] = [:]
    var hidden: UUID?

    func report(_ momentId: UUID, frame: CGRect) {
        frames[momentId] = frame
    }

    func forget(_ momentId: UUID) {
        frames[momentId] = nil
    }
}
```

- [ ] **Step 4: Implement the snap source**

Create `apps/ios/Pebbles/Features/Path/Spine/SpineSnapSource.swift`:

```swift
import SwiftUI
import UIKit

/// Where a Spine card's photo comes from (#1130 §Testing). In the app, the snap
/// is signed and `AsyncImage` loads it; references and the lab hand decoded
/// `UIImage`s instead, because `AsyncImage` does not render under
/// `ImageRenderer`. New pattern, named in the D2b PR.
struct SpineSnapSource {
    /// A decoded photo for a storage path, at once; nil when this source has none in memory.
    var image: (String) -> UIImage?
    /// The signed original, for `AsyncImage`; nil when unsigned (the cache logs why).
    var url: (String) async -> URL?

    static let none = SpineSnapSource(image: { _ in nil }, url: { _ in nil })

    @MainActor
    static func signed(_ cache: SnapURLCache) -> SpineSnapSource {
        // `SnapURLCache.signedURLs` logs its own failure before it throws.
        SpineSnapSource(image: { _ in nil }, url: { path in try? await cache.signedURLs(storagePath: path).original })
    }

    /// Decoded images by storage path (references, the lab).
    static func images(_ byPath: [String: UIImage]) -> SpineSnapSource {
        SpineSnapSource(image: { byPath[$0] }, url: { _ in nil })
    }
}

private struct SpineSnapSourceKey: EnvironmentKey {
    static let defaultValue = SpineSnapSource.none
}

extension EnvironmentValues {
    var spineSnapSource: SpineSnapSource {
        get { self[SpineSnapSourceKey.self] }
        set { self[SpineSnapSourceKey.self] = newValue }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run the **Suite command** with `CardFrameRegistryTests`. Expected: PASS (2).

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/CardFrameRegistry.swift apps/ios/Pebbles/Features/Path/Spine/SpineSnapSource.swift apps/ios/PebblesTests/Spine/CardFrameRegistryTests.swift
git commit -m "feat(ios): the card frame registry for the held card, and the snap source"
```

---

### Task D2b.5: The people line's parts

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Feed/PeopleLineParts.swift`
- Test: `apps/ios/PebblesTests/Spine/PeopleLinePartsTests.swift`

The line and the card's strip are one `Text` each (so they wrap as type does), set from parts; the parts are pure and tested here, the `Text` is assembled in D2b. Port of Android's `peopleLineText` / `peopleStripItems` shape (`PeopleLineTextTest.kt`).

- [ ] **Step 1: Write the failing test**

```swift
import Testing
@testable import Pebbles

/// #1130 R7: "with [g] MARA, [g] ELIO & twelve others [g][g][g][g][g] +7" as
/// parts; the card's strip drops "with", the commas and the "&".
@Suite("People line parts")
struct PeopleLinePartsTests {
    typealias V = Vectors
    private let names = ["a": "Mara", "b": "Elio", "c": "Nadia"]

    private func line(_ ids: [String]) -> PeopleLine {
        PeopleLines.peopleLine(ids, order: ids, locale: V.en)
    }

    @Test("one, two and three names: A · A & B · A, B & C")
    func names3() {
        #expect(PeopleLineParts.parts(line(["a"]), names: names, with: "with", others: "", style: .line)
            == [.aside("with"), .mark("a"), .name("Mara")])
        #expect(PeopleLineParts.parts(line(["a", "b"]), names: names, with: "with", others: "", style: .line)
            == [.aside("with"), .mark("a"), .name("Mara"), .aside("&"), .mark("b"), .name("Elio")])
        #expect(PeopleLineParts.parts(line(["a", "b", "c"]), names: names, with: "with", others: "", style: .line)
            == [.aside("with"), .mark("a"), .name("Mara"), .separator(","), .mark("b"), .name("Elio"),
                .aside("&"), .mark("c"), .name("Nadia")])
    }

    @Test("fourteen: two named, the others in words, five marks and +7")
    func fourteen() {
        let ids = ["a", "b"] + (0..<12).map { "s\($0)" }
        let parts = PeopleLineParts.parts(line(ids), names: names, with: "with", others: "& twelve others", style: .line)
        #expect(parts.prefix(6) == [.aside("with"), .mark("a"), .name("Mara"), .separator(","), .mark("b"), .name("Elio")])
        #expect(parts[6] == .aside("& twelve others"))
        #expect(parts.filter { if case .mark = $0 { return true } else { return false } }.count == 7)
        #expect(parts.last == .more("+7"))
    }

    @Test("a solitary pebble has no line at all")
    func solitary() {
        #expect(PeopleLineParts.parts(line([]), names: names, with: "with", others: "", style: .line).isEmpty)
    }

    @Test("the card's strip keeps no with, no commas and no ampersand")
    func card() {
        #expect(PeopleLineParts.parts(line(["a", "b", "c"]), names: names, with: "with", others: "", style: .card)
            == [.mark("a"), .name("Mara"), .mark("b"), .name("Elio"), .mark("c"), .name("Nadia")])
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/PeopleLinePartsTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `PeopleLinePartsTests`. Expected: build FAIL with "cannot find 'PeopleLineParts' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Feed/PeopleLineParts.swift`:

```swift
import Foundation

/// One run of a people line (#1130 R7).
enum PeopleLinePart: Equatable {
    /// "with", "&", "& twelve others": the quiet italic.
    case aside(String)
    /// A soul's glyph, or the dashed mark of a soul without one.
    case mark(String)
    /// A named soul, in small caps.
    case name(String)
    /// ", " between names.
    case separator(String)
    /// "+7", lining tabular.
    case more(String)
}

/// The people line as parts, so the line and the card's strip are each one
/// wrapping `Text` (`PeopleLineView`). The line reads "with [g] MARA, [g] ELIO
/// & twelve others [g]×5 +7"; the card's strip is the same without "with", the
/// commas and the "&".
enum PeopleLineParts {
    enum Style { case line, card }

    /// - Parameters:
    ///   - with: the localised "with", spoken on the line only.
    ///   - others: the localised "& twelve others" (`SpineCopy.others`).
    static func parts(
        _ line: PeopleLine, names: [String: String], with: String, others: String, style: Style
    ) -> [PeopleLinePart] {
        guard !line.named.isEmpty else { return [] }
        var parts: [PeopleLinePart] = style == .line ? [.aside(with)] : []
        // "A", "A & B", "A, B & C", or "A, B" when "& N others" follows.
        let ampersandAt = line.others > 0 ? -1 : line.named.count - 1
        for (index, id) in line.named.enumerated() {
            if index > 0, style == .line {
                parts.append(index == ampersandAt ? .aside("&") : .separator(","))
            }
            parts.append(.mark(id))
            parts.append(.name(names[id] ?? ""))
        }
        if line.others > 0 { parts.append(.aside(others)) }
        parts += line.marks.map(PeopleLinePart.mark)
        if line.overflow > 0 { parts.append(.more("+\(line.overflow)")) }
        return parts
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `PeopleLinePartsTests`. Expected: PASS (4).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Feed/PeopleLineParts.swift apps/ios/PebblesTests/Spine/PeopleLinePartsTests.swift
git commit -m "feat(ios): the people line as parts for the entry and the card"
```

---

### Task D2b.6: The Spine's calendar, text spans and soul marks

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/SpineCalendar.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/SpineTextSpan.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/Feed/SoulMark.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/Feed/PeopleLineView.swift`

- [ ] **Step 1: `SpineCalendar.swift`**

```swift
import Foundation

/// The calendar the Spine's views pass to its logic (D10): ISO weeks, in the
/// device's zone. The logic never reads `.current` itself; the views do, here,
/// once. The B2 references pin the zone with `TEST_RUNNER_TZ=Europe/Paris`.
enum SpineCalendar {
    static var current: Calendar {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = .current
        return calendar
    }
}
```

- [ ] **Step 2: `SpineTextSpan.swift`**

```swift
import SwiftUI

extension Text {
    /// One run of a concatenated `Text` in a Spine rank (#1130). It applies the
    /// same resolution `.spineType(_:)` applies to a view (`SpineType.resolve`),
    /// for the runs of a people line, which must wrap as one text. The caller
    /// passes its environment's Dynamic Type and Bold Text.
    func spineSpan(
        _ style: SpineType.Style, dynamicTypeSize: DynamicTypeSize, legibilityWeight: LegibilityWeight?
    ) -> Text {
        let resolved = SpineType.resolve(style, dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
        return font(Font(resolved.uiFont)).tracking(resolved.tracking)
    }
}

extension SpineType.Style {
    /// The people line's quiet aside ("with", "& twelve others"): the `people`
    /// rank in its italic, as Android's `aside` span is.
    static var peopleAside: Self {
        var style = Self.people
        style.italic = true
        return style
    }
}
```

- [ ] **Step 3: `SoulMark.swift`**

```swift
import SwiftUI

/// A soul's mark (#1130 R7): its glyph in ink-2, or a dashed square when the
/// reference cache has no glyph for that soul (the web's `SoulMark`).
struct SoulMark: View {
    let strokes: [GlyphStroke]?
    let side: CGFloat

    var body: some View {
        Group {
            if let strokes, !strokes.isEmpty {
                GlyphThumbnail(strokes: strokes, side: side, strokeColor: Color.pebbles.onSurfaceVariant)
            } else {
                RoundedRectangle(cornerRadius: side * 0.2)
                    .strokeBorder(Color.pebbles.outline, style: StrokeStyle(lineWidth: 1, dash: [2, 2]))
            }
        }
        .frame(width: side, height: side)
        .accessibilityHidden(true)
    }

    @MainActor private static var images: [String: Image] = [:]

    /// The mark as an `Image` for a `Text` concatenation, rendered once per
    /// glyph, side, scheme and display scale.
    @MainActor
    static func image(strokes: [GlyphStroke]?, side: CGFloat, scheme: ColorScheme, scale: CGFloat) -> Image {
        var hasher = Hasher()
        hasher.combine(strokes)
        let key = "\(hasher.finalize())|\(side)|\(scheme)|\(scale)"
        if let cached = images[key] { return cached }
        let renderer = ImageRenderer(content: SoulMark(strokes: strokes, side: side).environment(\.colorScheme, scheme))
        renderer.scale = scale
        let image = renderer.uiImage.map { Image(uiImage: $0) } ?? Image(systemName: "square.dashed")
        images[key] = image
        return image
    }
}
```

- [ ] **Step 4: `PeopleLineView.swift`**

```swift
import SwiftUI

/// A people line set from its parts as one wrapping `Text` (#1130 R7): the
/// entry's line ("with [g] MARA, [g] ELIO & twelve others …") and the card's
/// strip. Names are ink, the rest ink-2; each run takes its rank as a span.
struct PeopleLineView: View {
    let parts: [PeopleLinePart]
    let glyphs: [String: [GlyphStroke]]
    let markSide: CGFloat
    let base: SpineType.Style
    let name: SpineType.Style
    let aside: SpineType.Style
    let more: SpineType.Style
    var alignment: TextAlignment = .leading

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.legibilityWeight) private var legibilityWeight
    @Environment(\.colorScheme) private var scheme
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        line
            .multilineTextAlignment(alignment)
            .spineType(base)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
    }

    private var line: Text {
        var text = Text(verbatim: "")
        for (index, part) in parts.enumerated() {
            let next = parts.indices.contains(index + 1) ? parts[index + 1] : nil
            text = text + run(part) + Text(verbatim: gap(after: part, before: next))
        }
        return text
    }

    private func run(_ part: PeopleLinePart) -> Text {
        switch part {
        case .aside(let words):
            return Text(verbatim: words).spineSpan(aside, dynamicTypeSize: dynamicTypeSize,
                                                   legibilityWeight: legibilityWeight)
        case .separator(let mark):
            return Text(verbatim: mark)
        case .mark(let id):
            let image = SoulMark.image(strokes: glyphs[id], side: markSide, scheme: scheme, scale: displayScale)
            return Text(image).baselineOffset(-markSide * 0.18)
        case .name(let words):
            return Text(verbatim: words)
                .spineSpan(name, dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
                .foregroundStyle(Color.pebbles.onSurface)
        case .more(let words):
            return Text(verbatim: words).spineSpan(more, dynamicTypeSize: dynamicTypeSize,
                                                   legibilityWeight: legibilityWeight)
        }
    }

    /// One space between runs; none before a comma; a thin space between a mark and its name.
    private func gap(after part: PeopleLinePart, before next: PeopleLinePart?) -> String {
        guard let next else { return "" }
        if case .separator = next { return "" }
        if case .mark = part, case .name = next { return "\u{2009}" }
        return " "
    }
}
```

- [ ] **Step 5: Build**

Run **Build**. Expected: `** BUILD SUCCEEDED **`. If `SpineType.resolve` has a different signature on `main`, `grep -n "static func resolve" apps/ios/Pebbles/Theme/SpineTypeModifier.swift` and call it as `main` declares it; the span must use the same function `.spineType` uses.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/SpineCalendar.swift apps/ios/Pebbles/Features/Path/Spine/SpineTextSpan.swift apps/ios/Pebbles/Features/Path/Spine/Feed/SoulMark.swift apps/ios/Pebbles/Features/Path/Spine/Feed/PeopleLineView.swift
git commit -m "feat(ios): soul marks and the people line as one wrapping text"
```

---

### Task D2b.7: The card's field

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCardField.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI

/// A card's field (#1130 R5): the stock, the spot tint over it, and the
/// valence pattern: rays from the stone's corner for a highlight (every 2.2°,
/// from −6 % −14 %), ripples for a neutral (every 6.3 pt, from −4 % −10 %),
/// contours from the far corner for a lowlight (150 % × 110 % ellipses at
/// 112 % 122 %, every 6.8 pt). A `Canvas` draws only inside its own bounds, so
/// nothing here needs a clip (D4).
struct SpineCardField: View {
    let polarity: ValencePolarity
    let spot: Color
    let tint: Double
    let pattern: Double
    let size: CGSize

    var body: some View {
        Canvas { context, size in
            let rect = CGRect(origin: .zero, size: size)
            context.fill(Path(rect), with: .color(Color.pebbles.stock))
            context.fill(Path(rect), with: .color(spot.opacity(tint)))
            let ink = GraphicsContext.Shading.color(spot.opacity(pattern))
            switch polarity {
            case .highlight: Self.rays(in: size, context: context, ink: ink)
            case .neutral: Self.ripples(in: size, context: context, ink: ink)
            case .lowlight: Self.contours(in: size, context: context, ink: ink)
            }
        }
        .frame(width: size.width, height: size.height)
        .accessibilityHidden(true)
    }

    private static func rays(in size: CGSize, context: GraphicsContext, ink: GraphicsContext.Shading) {
        let origin = CGPoint(x: -0.06 * size.width, y: -0.14 * size.height)
        let length = hypot(size.width, size.height) * 1.5
        var path = Path()
        for degrees in stride(from: 0.0, through: 90.0, by: 2.2) {
            let angle = degrees * .pi / 180
            path.move(to: origin)
            path.addLine(to: CGPoint(x: origin.x + length * cos(angle), y: origin.y + length * sin(angle)))
        }
        context.stroke(path, with: ink, lineWidth: 1)
    }

    private static func ripples(in size: CGSize, context: GraphicsContext, ink: GraphicsContext.Shading) {
        let centre = CGPoint(x: -0.04 * size.width, y: -0.10 * size.height)
        let farthest = hypot(size.width - centre.x, size.height - centre.y)
        var path = Path()
        for radius in stride(from: 5.9, through: farthest, by: 6.3) {
            path.addEllipse(in: CGRect(x: centre.x - radius, y: centre.y - radius, width: 2 * radius, height: 2 * radius))
        }
        context.stroke(path, with: ink, lineWidth: 0.8)
    }

    private static func contours(in size: CGSize, context: GraphicsContext, ink: GraphicsContext.Shading) {
        let centre = CGPoint(x: 1.12 * size.width, y: 1.22 * size.height)
        let squash = (1.1 * size.height) / max(1.5 * size.width, 1)
        var path = Path()
        for rx in stride(from: 6.4, through: 2.6 * size.width, by: 6.8) {
            let ry = rx * squash
            path.addEllipse(in: CGRect(x: centre.x - rx, y: centre.y - ry, width: 2 * rx, height: 2 * ry))
        }
        context.stroke(path, with: ink, lineWidth: 0.8)
    }
}
```

- [ ] **Step 2: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCardField.swift
git commit -m "feat(ios): the spine card's tinted field and valence pattern"
```

---

### Task D2b.8: `SpineCard`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCard.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI
import os

/// The Deck card (#1130 R4–R5): the photo uncropped at its own ratio in a tinted
/// field with a 1.8u mat, the index (day and weekday) and the rail (time |
/// emotion) in the gutters, the title in its valence cut, the souls strip, the
/// painted edge and the shadow, on the stock with an 11 pt radius. Chrome is in
/// card units, `u = min(width / 70, 4.4)`; card text scales through
/// `SpineType`'s `.card(cap: 1.3)` (D11). Until the ratio is known the field is
/// square, then it eases to the ratio over 240 ms (snaps under Reduce Motion).
///
/// No button of its own: `SpinePebble` wraps it. It reports its global frame to
/// `CardFrameRegistry` for stack E and hides while E flies it.
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
    /// Up to this many souls are named in full: 3 on the Spine (R7), 8 on the held card (E).
    var namedInFull = 3

    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.spineSnapSource) private var snaps
    @Environment(CardFrameRegistry.self) private var registry: CardFrameRegistry?
    @State private var shownRatio: CGFloat?
    @State private var photoURL: URL?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "spine")

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

    private var locale: Locale { SpineStrings.appLocale }
    private var calendar: Calendar { SpineCalendar.current }
    private var intensity: Int { moment.pebble?.intensity ?? 2 }
    private var valence: Valence { moment.pebble?.valence ?? .neutralMedium }
    private var spot: Color { SpineColors.spot(palette, scheme: scheme) }

    var body: some View {
        let size = CardSizes.cardSize(intensity: intensity, ratio: shownRatio, column: column)
        let u = min(size.width / 70, 4.4)
        VStack(spacing: 2.6 * u) {
            field(width: size.width, u: u)
            title(u: u)
            strip(u: u)
        }
        .padding(EdgeInsets(top: 4.6 * u, leading: 9 * u, bottom: 6.2 * u, trailing: 9 * u))
        .frame(width: size.width)
        .overlay(alignment: .topLeading) { index(u: u) }
        .overlay(alignment: .topTrailing) { rail(u: u) }
        .overlay {
            RoundedRectangle(cornerRadius: 1.3 * u)
                .strokeBorder(Color.pebbles.cardRule, lineWidth: 1)
                .padding(1.6 * u)
                .accessibilityHidden(true)
        }
        .background { ground }
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
        .task(id: moment.firstSnapPath) {
            guard let path = moment.firstSnapPath, snaps.image(path) == nil else { return }
            photoURL = await snaps.url(path)
        }
    }

    // MARK: - Field and photo

    private func field(width: CGFloat, u: CGFloat) -> some View {
        let photoWidth = width - 21.6 * u
        let photoHeight = photoWidth / (shownRatio ?? CardSizes.pendingRatio)
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

    // MARK: - Gutters and ground

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

    /// The stock with its painted edge (2 / 3.5 / 5.5 pt in the edge colour) and
    /// the rule set's two-layer shadow, on the ground only, so no text casts one.
    /// The ink and its alpha are Android's (`SpineColors.shadowInk` /
    /// `shadowAlpha`); SwiftUI has no shadow spread, so each layer takes a share
    /// of that alpha to read as the web's `0 1px 2px` and `0 18px 30px -22px`.
    private var ground: some View {
        let shape = RoundedRectangle(cornerRadius: 11, style: .continuous)
        let ink = SpineColors.shadowInk(scheme: scheme)
        let alpha = SpineColors.shadowAlpha(scheme: scheme)
        return shape.fill(Color.pebbles.stock)
            .background(
                shape.fill(SpineColors.edge(palette, polarity: valence.polarity, scheme: scheme))
                    .offset(y: CardSizes.edge(intensity))
            )
            .shadow(color: ink.opacity(alpha * 0.3), radius: 1, x: 0, y: 1)
            .shadow(color: ink.opacity(alpha * 0.55), radius: 15, x: 0, y: 18)
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

- [ ] **Step 2: Build and commit**

Run **Build** and the **Suite command** with `ThemeLiteralsTests`. Expected: `** BUILD SUCCEEDED **`, PASS.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Feed/SpineCard.swift
git commit -m "feat(ios): the spine deck card at its photo's own ratio"
```

---

### Task D2b.9: Card gallery, lab section and the fixture photos

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineCardGallery.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineLabView.swift`
- Create: `apps/ios/PebblesTests/Spine/Fixtures/*.jpg` (copied)
- Create: `apps/ios/PebblesTests/Spine/SpineFixturePhotos.swift`

- [ ] **Step 1: The gallery**

Create `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineCardGallery.swift`:

```swift
#if DEBUG
import SwiftUI

/// Fixture-week cards by moment number, in the content column of a 393 pt page,
/// each at its primed ratio: the lab's cards and the card references. The
/// photos come from the environment's `SpineSnapSource` (none in the lab, so
/// the cards show their photo bed: decision 7).
struct SpineCardGallery: View {
    let numbers: [Int]
    var width: CGFloat = 393

    private typealias F = SpineFixtureWeek

    private var moments: [MomentSummary] {
        numbers.compactMap { number in
            F.moments.first { $0.id.uuidString.hasSuffix(String(format: "%012ld", number)) }
        }
    }

    private var order: [String] {
        PersonsOfWeek.personsOfWeek(F.moments, names: F.soulNames, weekStart: F.weekStart, calendar: F.calendar,
                                    locale: SpineStrings.appLocale).map(\.soulId)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 48) {
            ForEach(moments) { moment in
                SpineCard(
                    moment: moment,
                    palette: SpineColors.palette(F.palette(for: moment), isBlank: moment.pebble == nil),
                    ratio: moment.firstSnapPath.flatMap { F.photos[$0]?.ratio },
                    column: SpineLayout.column(width: width),
                    soulNames: F.soulNames, soulGlyphs: F.soulGlyphs, soulOrder: order
                )
            }
        }
        .padding(.leading, SpineLayout.colX)
        .padding(.vertical, 48)
        .frame(width: width, alignment: .leading)
        .background(Color.pebbles.surface)
    }
}
#endif
```

- [ ] **Step 2: The lab's cards section**

In `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineLabView.swift`:

1. Replace

```swift
    enum Section: String, CaseIterable, Identifiable {
        case stones
```

with

```swift
    enum Section: String, CaseIterable, Identifiable {
        case stones, cards
```

2. Replace

```swift
                case .stones:
                    SpineStoneGallery(pressable: true)
                }
```

with

```swift
                case .stones:
                    SpineStoneGallery(pressable: true)
                case .cards:
                    SpineCardGallery(numbers: [1, 4, 7, 9, 13, 15])
                }
```

- [ ] **Step 3: Copy the five sandbox photos for the references**

```bash
mkdir -p apps/ios/PebblesTests/Spine/Fixtures
cp apps/web/public/sandbox/flag-march.jpg apps/web/public/sandbox/forest-crew.jpg apps/web/public/sandbox/fountain.jpg \
   apps/web/public/sandbox/rooftop-group.jpg apps/web/public/sandbox/seawall-portrait.jpg apps/ios/PebblesTests/Spine/Fixtures/
cmp apps/web/public/sandbox/fountain.jpg apps/ios/PebblesTests/Spine/Fixtures/fountain.jpg && echo identical
```

Expected: `identical`.

Create `apps/ios/PebblesTests/Spine/SpineFixturePhotos.swift`:

```swift
import UIKit
@testable import Pebbles

/// The fixture week's five photos (the web's `public/sandbox/*.jpg`, ratios 1:1,
/// 3:2, 3:2, 16:9, 2:3), decoded for `SpineSnapSource` so the references draw
/// real photos: `AsyncImage` does not render under `ImageRenderer`. Read through
/// `#filePath`, as the reference PNGs are; the simulator shares the host disk.
enum SpineFixturePhotos {
    static let source: SpineSnapSource = {
        let folder = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("Fixtures")
        var images: [String: UIImage] = [:]
        for (path, photo) in SpineFixtureWeek.photos {
            if let image = UIImage(contentsOfFile: folder.appendingPathComponent("\(photo.file).jpg").path) {
                images[path] = image
            }
        }
        precondition(images.count == SpineFixtureWeek.photos.count, "missing a fixture photo in \(folder.path)")
        return .images(images)
    }()
}
```

- [ ] **Step 4: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Debug apps/ios/PebblesTests/Spine/Fixtures apps/ios/PebblesTests/Spine/SpineFixturePhotos.swift
git commit -m "feat(ios): the spine lab's cards and the fixture photos"
```

---

### Task D2b.10: The card references

**Files:**
- Create: `apps/ios/PebblesTests/References/SpineCardReferences.swift`

- [ ] **Step 1: Write the suite**

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D2b: four cards at their photos' ratios in the 293 pt column of a
    /// 393 pt page (1:1 flag march, 3:2 forest walk, 16:9 rooftop, 2:3 seawall),
    /// then the untitled photo and the bare moment's card, in English and French.
    @Suite("Spine card references")
    @MainActor
    struct SpineCardReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func ratios(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(cards([1, 4, 7, 9]), named: "ratios", config: ReferenceConfig(scheme: scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func states(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(cards([13, 15]), named: "states",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        private func cards(_ numbers: [Int]) -> some View {
            SpineCardGallery(numbers: numbers)
                .environment(\.spineSnapSource, SpineFixturePhotos.source)
        }
    }
}
```

- [ ] **Step 2: Look locally (not committed)**

Run **Look** at `SpineCardReferences`. In `ratios`: the flag-march card is the full column wide and square-ish, the forest walk wider than tall, the rooftop widest, the seawall narrower than the column and taller (260 × ~370); every photo is whole, never cropped; each card has a day numeral top left, a rotated "time | EMOTION" rail top right, its title centred and a souls strip; the painted edge shows under each card in the emotion's colour. In `states`: "untitled" in italic under the fountain photo; the bare moment's card in the grey ramp with no emotion on its rail. French renders "sans titre". Clean afterwards.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/PebblesTests/References/SpineCardReferences.swift
git commit -m "test(ios): spine card references at four ratios and two states"
```

---

### Task D2b.11: D2b verification, PR and CI references

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** (new references fail locally, expected), **Build**, and `npm run lint --workspace=@pbbls/web && npm run test --workspace=@pbbls/web -- lib/theme`.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <D2b-number> --title "feat(ios): the spine deck card, its photo ratio and its frame" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Part of #1130`; key files; decision 1 (one generated token, `rakeLight`, through `apps/web/lib/theme/ios-colorsets.ts`, with constant-hex sources; the rake shadow and the shadow ink blended in `SpineColors.swift` as Android's are); **new patterns for review:** `SnapAspectCache` (header-only ratio probe, session cache, 4 s timeout, no retry) and `SpineSnapSource` (an environment value that hands decoded images to references and the lab); `CardFrameRegistry` as the spec's canonical block, for stack E; the `QuarterTurn` layout for the rail (measured in layout so it holds under `ImageRenderer`); the shadow approximation (no spread in SwiftUI); `Text.spineSpan` (a people line's runs take their ranks through `SpineType.resolve`, the function `.spineType` uses); decision 7 (the lab's photo bed). No Lab Note, no `F-…` ids.

- [ ] **Step 3: Rebaseline on CI**

Run **Rebaseline on CI**. Compare `ratios-paper-en.png` with Android's `SpineFeedPicnicLight` cards and the web `/sandbox/design` Spine cards: the same four shapes, nothing cropped. The check is green after the approved re-run.

- [ ] **Step 4: Device check**

On the iPhone 15, Spine lab → cards: the six fixture cards draw on their photo beds at their ratios in Paper and Night, with Dynamic Type at the largest standard size (card text grows up to ×1.3 only).

---
## Part D3 — `feat/1130-ios-path-model`

PR title: `feat(ios): the path model, the raked ground and the day seams`. Body opens `Part of #1130`. Visible in the references only: `PathView` adopts the model in D5a. The model comes first (Tasks D3.1–D3.2), then the ground and the seams (Tasks D3.3–D3.6).

### Task D3.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1130-ios-path-model
git branch --show-current
```

Expected: `feat/1130-ios-path-model`.

---

### Task D3.1: `SpinePage` and `WeekNeighbour`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/SpinePage.swift`

- [ ] **Step 1: Write it**

```swift
import Foundation

/// A week the Spine's links turn to (#1130 D9): the masthead's way back and the
/// mouth's way on, named by the ISO week of the page it reaches. The roll holds
/// only the weeks with moments plus the current one, so the calendar's
/// neighbour and the pager's differ whenever weeks are skipped (Android lesson,
/// Part 6), and a link must read as where it goes.
struct WeekNeighbour: Equatable {
    let number: Int
    let weekStart: Date
}

/// One page of the pager, as values (§Data flow), built once per load by `PathModel`.
struct SpinePage {
    let weekStart: Date
    let week: SpineWeek<MomentSummary>
    let words: WeekWords
    let persons: [PersonOfWeek]
    /// Every moment in reading order (`SpineWeekBuilder.readingOrder`).
    let contents: [MomentSummary]
    let previous: WeekNeighbour?
    let next: WeekNeighbour?
    let isCurrent: Bool
    /// Lowercase soul id → name: the reference cache, then the names the pebbles carry.
    let soulNames: [String: String]
    /// Lowercase soul id → glyph strokes, from the reference cache; a miss draws the dashed mark.
    let soulGlyphs: [String: [GlyphStroke]]

    /// The persons-of-the-week order every people line on this page follows.
    var soulOrder: [String] { persons.map(\.soulId) }
}
```

- [ ] **Step 2: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/SpinePage.swift
git commit -m "feat(ios): a spine page as values, with its pager neighbours"
```

---

### Task D3.2: `PathModel`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/PathModel.swift`
- Test: `apps/ios/PebblesTests/Spine/PathModelTests.swift`, `apps/ios/PebblesTests/Spine/WeekNeighbourTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/Spine/PathModelTests.swift`:

```swift
import Foundation
import Observation
import Testing
@testable import Pebbles

/// #1130 §Data flow: `PathModel` takes over `PathView`'s load, its refocus rule
/// and its delete path, unchanged in behaviour, and builds each week's page.
@Suite("PathModel")
@MainActor
struct PathModelTests {
    typealias F = SpineFixtureWeek
    struct Failure: Error {}
    final class Calls: @unchecked Sendable { var count = 0 }

    private func model() -> PathModel {
        PathModel(calendar: F.calendar, now: { F.today }, locale: { Vectors.en })
    }

    @Test("a first load lands on the current week and shows content")
    func firstLoad() async {
        let model = model()
        #expect(model.phase == .loading)
        await model.load { F.moments }
        #expect(model.phase == .content)
        #expect(model.focusedWeekStart == F.weekStart)
        #expect(model.entries.map(\.weekStart) == [F.weekStart])
    }

    @Test("a failed load logs and fails")
    func failedLoad() async {
        let model = model()
        await model.load { throw Failure() }
        #expect(model.phase == .failed)
    }

    @Test("a reload keeps the focused week while it is still there, else falls back to the current one")
    func refocus() async {
        let older = MomentSummary(id: UUID(), name: "Older", happenedAt: Vectors.at("2026-09-02T10:00:00Z"),
                                  createdAt: Vectors.at("2026-09-02T10:00:00Z"), firstSnapPath: nil, pebble: nil)
        let model = model()
        await model.load { F.moments + [older] }
        let olderWeek = WeekRollBuilder.weekStart(for: older.happenedAt, calendar: F.calendar)
        model.focus(olderWeek)
        await model.load { F.moments + [older] }
        #expect(model.focusedWeekStart == olderWeek)
        await model.load { F.moments }
        #expect(model.focusedWeekStart == F.weekStart)
    }

    @Test("focus ignores an equal week, and a page change cancels a pending press")
    func focus() async throws {
        let model = model()
        await model.load { F.moments }
        model.pressRequests.request(try #require(F.moments.first).id)
        model.focus(F.weekStart)
        #expect(model.pressRequests.pending != nil)
        model.focus(LocalDays.adding(-7, to: F.weekStart, calendar: F.calendar))
        #expect(model.pressRequests.pending == nil)
    }

    @Test("Capture's refocus finds the week that holds the moment")
    func focusContaining() async throws {
        let model = model()
        let older = MomentSummary(id: UUID(), name: nil, happenedAt: Vectors.at("2026-09-02T10:00:00Z"),
                                  createdAt: Vectors.at("2026-09-02T10:00:00Z"), firstSnapPath: nil, pebble: nil)
        await model.load { F.moments + [older] }
        model.focusWeek(containing: older.id)
        #expect(model.focusedWeekStart == WeekRollBuilder.weekStart(for: older.happenedAt, calendar: F.calendar))
        model.focusCurrentWeek()
        #expect(model.focusedWeekStart == F.weekStart)
    }

    @Test("a page carries its days, words, persons and contents in reading order")
    func page() async throws {
        let model = model()
        await model.load { F.moments }
        let page = try #require(model.page(for: F.weekStart))
        #expect(page.isCurrent)
        #expect(page.words.number == 41 && page.words.ordinal == "forty-first")
        #expect(page.week.pebbleCount == 13)
        #expect(page.contents.first?.name == "Flag march with Nadia")
        // Names come from the pebbles' own souls before the reference cache loads.
        #expect(page.persons.first?.name == "Mara")
        #expect(page.previous == nil && page.next == nil)
    }

    @Test("the reference cache's glyphs reach the pages")
    func souls() async throws {
        let model = model()
        await model.load { F.moments }
        _ = model.page(for: F.weekStart)
        model.updateSouls(names: F.soulNames, glyphs: F.soulGlyphs)
        let page = try #require(model.page(for: F.weekStart))
        #expect(page.soulGlyphs.count == 14)
    }

    @Test("the reading day is written only when it changes")
    func readingDay() async {
        let model = model()
        await model.load { F.moments }
        model.setReadingDay(2, for: F.weekStart)
        let changed = CardFrameRegistryTests.Flag()
        withObservationTracking { _ = model.readingDay } onChange: { changed.raised = true }
        model.setReadingDay(2, for: F.weekStart)
        #expect(!changed.raised)
        model.setReadingDay(3, for: F.weekStart)
        #expect(changed.raised)
        #expect(model.readingDay[F.weekStart] == 3)
    }

    @Test("a change made on another tab reloads; the Path's own does not")
    func revision() async {
        let model = model()
        let calls = Calls()
        let revision = MomentsRevision()
        revision.bump(from: .path)
        await model.reloadIfChangedOutside(revision) { calls.count += 1; return F.moments }
        #expect(calls.count == 0)
        revision.bump(from: .people)
        await model.reloadIfChangedOutside(revision) { calls.count += 1; return F.moments }
        #expect(calls.count == 1)
    }

    @Test("a delete reports its success and its failure")
    func delete() async {
        let model = model()
        #expect(await model.delete { })
        #expect(!(await model.delete { throw Failure() }))
    }
}
```

Create `apps/ios/PebblesTests/Spine/WeekNeighbourTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1130 D9: the links name the pager's neighbour, which skips the empty weeks
/// between (Android lesson, Part 6): week 35 ↔ week 41.
@Suite("WeekNeighbour")
@MainActor
struct WeekNeighbourTests {
    typealias F = SpineFixtureWeek

    @Test("week 41's way back is week 35, and week 35's way on is week 41")
    func skipsEmptyWeeks() async throws {
        let august = MomentSummary(id: UUID(), name: "Late August", happenedAt: Vectors.at("2026-08-26T10:00:00Z"),
                                   createdAt: Vectors.at("2026-08-26T10:00:00Z"), firstSnapPath: nil, pebble: nil)
        let model = PathModel(calendar: F.calendar, now: { F.today }, locale: { Vectors.en })
        await model.load { F.moments + [august] }
        let w35 = WeekRollBuilder.weekStart(for: august.happenedAt, calendar: F.calendar)
        let current = try #require(model.page(for: F.weekStart))
        #expect(current.previous == WeekNeighbour(number: 35, weekStart: w35))
        #expect(current.words.previousNumber == 40) // the calendar's, read only by nothing that links
        let older = try #require(model.page(for: w35))
        #expect(older.next == WeekNeighbour(number: 41, weekStart: F.weekStart))
        #expect(older.previous == nil)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run the **Suite command** with `PathModelTests`. Expected: build FAIL with "cannot find 'PathModel' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/PathModel.swift`:

```swift
import Foundation
import Observation
import os

/// The Path tab's state (#1130 §Data flow), the Path's first `@Observable` view
/// model (new pattern, named in the D3 PR). It takes over `PathView`'s inline
/// load, its refocus rule and its delete path, unchanged in behaviour, and
/// builds one `SpinePage` per week, memoised per load. `focusedWeekStart` is the
/// one source of truth for the roll, the pager and the links.
///
/// The fetch and the delete are passed in as closures, so the model needs no
/// Supabase client and the tests need no fake service.
@Observable @MainActor
final class PathModel {
    enum Phase: Equatable { case loading, failed, content }

    private(set) var phase: Phase = .loading
    private(set) var moments: [MomentSummary] = []
    private(set) var entries: [WeekRollEntry] = []
    private(set) var focusedWeekStart: Date
    /// weekStart → the reading day's index (D12), written only on a change.
    private(set) var readingDay: [Date: Int] = [:]
    let pressRequests = StonePressRequests()
    /// The slot VoiceOver returns to; stack E sets it when a held card closes.
    var returnFocus: UUID?
    private(set) var isDeleting = false

    @ObservationIgnored private var pages: [Date: SpinePage] = [:]
    @ObservationIgnored private var referenceNames: [String: String] = [:]
    @ObservationIgnored private var soulGlyphs: [String: [GlyphStroke]] = [:]
    @ObservationIgnored let calendar: Calendar
    @ObservationIgnored private let now: () -> Date
    @ObservationIgnored private let locale: () -> Locale
    @ObservationIgnored private let logger = Logger(subsystem: "app.pbbls.ios", category: "path")

    init(calendar: Calendar = SpineCalendar.current, now: @escaping () -> Date = { Date() },
         locale: @escaping () -> Locale = { SpineStrings.appLocale }) {
        self.calendar = calendar
        self.now = now
        self.locale = locale
        focusedWeekStart = WeekRollBuilder.weekStart(for: now(), calendar: calendar)
    }

    var currentWeekStart: Date {
        WeekRollBuilder.weekStart(for: now(), calendar: calendar)
    }

    // MARK: - Load

    /// A cancelled load (a newer one replaced it) changes nothing.
    func load(_ fetch: () async throws -> [MomentSummary]) async {
        do {
            let result = try await fetch()
            guard !Task.isCancelled else { return }
            apply(result)
            phase = .content
        } catch {
            guard !Task.isCancelled else { return }
            logger.error("path fetch failed: \(error.localizedDescription, privacy: .private)")
            phase = .failed
        }
    }

    /// Stack C's `MomentsRevision`: a moment changed on another tab or in a
    /// sheet. The Path's own changes reload through their own callbacks.
    func reloadIfChangedOutside(_ revision: MomentsRevision, _ fetch: () async throws -> [MomentSummary]) async {
        guard revision.changedOutside(.path) else { return }
        await load(fetch)
    }

    private func apply(_ result: [MomentSummary]) {
        moments = result
        entries = WeekRollBuilder.build(moments: result, calendar: calendar, today: now())
        pages = [:]
        // Keep the focused week if it is still there, else the current week, else the closest.
        guard !entries.contains(where: { $0.weekStart == focusedWeekStart }) else { return }
        let current = currentWeekStart
        if entries.contains(where: { $0.weekStart == current }) {
            focusedWeekStart = current
        } else if let closest = entries.min(by: {
            abs($0.weekStart.timeIntervalSince(focusedWeekStart)) < abs($1.weekStart.timeIntervalSince(focusedWeekStart))
        }) {
            focusedWeekStart = closest.weekStart
        }
    }

    /// The reference cache's souls (`ReferenceDataService.souls`), keyed by lowercase id.
    func updateSouls(names: [String: String], glyphs: [String: [GlyphStroke]]) {
        referenceNames = names
        soulGlyphs = glyphs
        pages = [:]
    }

    // MARK: - Focus

    /// A roll tap, a link, a settled swipe. An equal week is ignored, so the
    /// pager's binding cannot loop; a page change drops a pending press.
    func focus(_ weekStart: Date) {
        guard weekStart != focusedWeekStart else { return }
        pressRequests.cancel()
        focusedWeekStart = weekStart
    }

    /// After Capture: the week that holds the moment, asked of the roll's own
    /// bucketing rather than recomputed. A silent no-op until the reload has it.
    func focusWeek(containing momentId: UUID) {
        guard let entry = entries.first(where: { $0.moments.contains { $0.id == momentId } }) else { return }
        focus(entry.weekStart)
    }

    /// The Path tab reselected at its root: "top of Path" means now.
    func focusCurrentWeek() {
        let current = currentWeekStart
        guard entries.contains(where: { $0.weekStart == current }) else { return }
        focus(current)
    }

    func setReadingDay(_ index: Int, for weekStart: Date) {
        guard readingDay[weekStart] != index else { return }
        readingDay[weekStart] = index
    }

    // MARK: - Pages

    func page(for weekStart: Date) -> SpinePage? {
        if let cached = pages[weekStart] { return cached }
        guard let index = entries.firstIndex(where: { $0.weekStart == weekStart }) else { return nil }
        let entry = entries[index]
        let locale = locale()
        let names = names(for: entry.moments)
        let week = SpineWeekBuilder.spineWeek(weekStart: weekStart, moments: entry.moments, today: now(),
                                              calendar: calendar)
        let page = SpinePage(
            weekStart: weekStart,
            week: week,
            words: WeekOpening.weekWords(weekStart: weekStart, calendar: calendar, locale: locale),
            persons: PersonsOfWeek.personsOfWeek(entry.moments, names: names, weekStart: weekStart,
                                                 calendar: calendar, locale: locale),
            contents: SpineWeekBuilder.readingOrder(week),
            previous: index > 0 ? neighbour(entries[index - 1]) : nil,
            next: index + 1 < entries.count ? neighbour(entries[index + 1]) : nil,
            isCurrent: weekStart == currentWeekStart,
            soulNames: names,
            soulGlyphs: soulGlyphs
        )
        pages[weekStart] = page
        return page
    }

    /// The photos of the focused week and its pager neighbours, for the ratio prefetch.
    func photoPaths(around weekStart: Date) -> [String] {
        guard let index = entries.firstIndex(where: { $0.weekStart == weekStart }) else { return [] }
        return entries[max(0, index - 1)...min(entries.count - 1, index + 1)]
            .flatMap(\.moments).compactMap(\.firstSnapPath)
    }

    private func neighbour(_ entry: WeekRollEntry) -> WeekNeighbour {
        WeekNeighbour(number: calendar.component(.weekOfYear, from: entry.weekStart), weekStart: entry.weekStart)
    }

    /// The reference cache's names, then the names `path_moments` carries on each pebble.
    private func names(for moments: [MomentSummary]) -> [String: String] {
        var names = referenceNames
        for soul in moments.compactMap(\.pebble).flatMap(\.souls) {
            let key = soul.id.uuidString.lowercased()
            names[key] = names[key] ?? soul.name
        }
        return names
    }

    // MARK: - Delete

    /// Runs `perform` (today's `MomentDeletion.perform`); false, logged, on a
    /// failure. A second confirm while one runs is ignored, as today.
    func delete(_ perform: () async throws -> Void) async -> Bool {
        guard !isDeleting else { return true }
        isDeleting = true
        defer { isDeleting = false }
        do {
            try await perform()
            return true
        } catch {
            logger.error("delete failed: \(error.localizedDescription, privacy: .private)")
            return false
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run the **Suite command** with `PathModelTests`, then `WeekNeighbourTests`. Expected: PASS (10), then PASS (1).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PathModel.swift apps/ios/PebblesTests/Spine/PathModelTests.swift apps/ios/PebblesTests/Spine/WeekNeighbourTests.swift
git commit -m "feat(ios): the path model owns the load, the focus and the spine pages"
```

---

### Task D3.3: The rake tile

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineRakeTile.swift`
- Test: `apps/ios/PebblesTests/Spine/SpineRakeTileTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Testing
@testable import Pebbles

/// #1130 R11: the rake's two path sets, verbatim from the web's 36 × 240 tile
/// (Android `SpineRakePaths`), parse into six grooves that run the tile's height.
@Suite("Spine rake tile")
struct SpineRakeTileTests {
    @Test("both path sets parse, run the full 240 and stay inside the 36 wide tile")
    func paths() throws {
        for d in [SpineRakeTile.shadow, SpineRakeTile.lit] {
            let path = try #require(SVGPathParser.parse(d))
            let box = path.boundingBoxOfPath
            #expect(box.minY == 0 && box.maxY == 240)
            #expect(box.minX > 0 && box.maxX < 36)
        }
    }

    @Test("the lit set is the shadow set carried 0.9 right")
    func offset() throws {
        let shadow = try #require(SVGPathParser.parse(SpineRakeTile.shadow)).boundingBoxOfPath
        let lit = try #require(SVGPathParser.parse(SpineRakeTile.lit)).boundingBoxOfPath
        #expect(abs((lit.minX - shadow.minX) - 0.9) < 0.001)
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/SpineRakeTileTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `SpineRakeTileTests`. Expected: build FAIL with "cannot find 'SpineRakeTile' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineRakeTile.swift`:

```swift
import SwiftUI
import UIKit
import os

/// The rake tile (#1130 R11): the web's 36 × 240 `--spine-rake` SVG, six
/// grooves 6 pt apart meandering in phase by 1.15 pt, each a 0.75 pt shadow
/// line and a 0.6 pt lit line carried 0.9 pt right. The paths are verbatim
/// from Android's `SpineRakePaths`. Paper and Night share the geometry and
/// differ in `SpineColors.rakeShadow` and the `rakeLight` token. Rendered once per scheme
/// and display scale; the ground tiles the image.
enum SpineRakeTile {
    // swiftlint:disable line_length
    static let shadow = "M2.91 0L3.32 12L3.43 24L3.86 36L3.88 48L4.12 60L3.85 72L3.86 84L3.46 96L3.32 108L2.76 120L2.57 132L2.11 144L2.04 156L1.66 168L1.77 180L1.69 192L2.04 204L2.08 216L2.57 228L2.91 240M8.78 0L8.97 12L9.43 24L9.55 36L9.88 48L9.76 60L9.85 72L9.55 84L9.46 96L8.97 108L8.76 120L8.27 132L8.11 144L7.68 156L7.66 168L7.47 180L7.69 192L7.68 204L8.08 216L8.27 228L8.78 240M14.96 0L15.18 12L15.54 24L15.94 36L16.17 48L15.98 60L15.96 72L15.94 84L15.76 96L15.18 108L14.86 120L14.65 132L14.40 144L13.90 156L13.77 168L13.86 180L13.99 192L13.90 204L14.19 216L14.65 228L14.96 240M21.04 0L21.57 12L21.55 24L22.10 36L22.10 48L22.36 60L21.97 72L22.10 84L21.68 96L21.57 108L20.88 120L20.81 132L20.33 144L20.28 156L19.78 168L20.02 180L19.91 192L20.28 204L20.20 216L20.81 228L21.04 240M26.82 0L27.11 12L27.61 24L27.64 36L27.78 48L27.90 60L28.03 72L27.64 84L27.36 96L27.11 108L26.94 120L26.35 132L26.01 144L25.82 156L25.84 168L25.56 180L25.59 192L25.82 204L26.26 216L26.35 228L26.82 240M33.07 0L33.44 12L33.59 24L33.84 36L34.12 48L34.24 60L34.01 72L33.84 84L33.70 96L33.44 108L32.91 120L32.56 132L32.35 144L32.16 156L31.82 168L31.76 180L31.93 192L32.16 204L32.24 216L32.56 228L33.07 240"
    static let lit = "M3.81 0L4.22 12L4.33 24L4.76 36L4.78 48L5.02 60L4.75 72L4.76 84L4.36 96L4.22 108L3.66 120L3.47 132L3.01 144L2.94 156L2.56 168L2.67 180L2.59 192L2.94 204L2.98 216L3.47 228L3.81 240M9.68 0L9.87 12L10.33 24L10.45 36L10.78 48L10.66 60L10.75 72L10.45 84L10.36 96L9.87 108L9.66 120L9.17 132L9.01 144L8.58 156L8.56 168L8.37 180L8.59 192L8.58 204L8.98 216L9.17 228L9.68 240M15.86 0L16.08 12L16.44 24L16.84 36L17.07 48L16.88 60L16.86 72L16.84 84L16.66 96L16.08 108L15.76 120L15.55 132L15.30 144L14.80 156L14.67 168L14.76 180L14.89 192L14.80 204L15.09 216L15.55 228L15.86 240M21.94 0L22.47 12L22.45 24L23.00 36L23.00 48L23.26 60L22.87 72L23.00 84L22.58 96L22.47 108L21.78 120L21.71 132L21.23 144L21.18 156L20.68 168L20.92 180L20.81 192L21.18 204L21.10 216L21.71 228L21.94 240M27.72 0L28.01 12L28.51 24L28.54 36L28.68 48L28.80 60L28.93 72L28.54 84L28.26 96L28.01 108L27.84 120L27.25 132L26.91 144L26.72 156L26.74 168L26.46 180L26.49 192L26.72 204L27.16 216L27.25 228L27.72 240M33.97 0L34.34 12L34.49 24L34.74 36L35.02 48L35.14 60L34.91 72L34.74 84L34.60 96L34.34 108L33.81 120L33.46 132L33.25 144L33.06 156L32.72 168L32.66 180L32.83 192L33.06 204L33.14 216L33.46 228L33.97 240"
    // swiftlint:enable line_length

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "spine")
    @MainActor private static var images: [String: UIImage] = [:]

    @MainActor
    static func image(scheme: ColorScheme, scale: CGFloat) -> UIImage? {
        let key = "\(scheme)|\(scale)"
        if let cached = images[key] { return cached }
        let renderer = ImageRenderer(content: Tile().environment(\.colorScheme, scheme))
        renderer.scale = scale
        guard let image = renderer.uiImage else {
            logger.error("spine rake tile did not render (\(String(describing: scheme), privacy: .public))")
            return nil
        }
        images[key] = image
        return image
    }

    private struct Tile: View {
        @Environment(\.colorScheme) private var scheme

        var body: some View {
            Canvas { context, _ in
                if let shadow = SVGPathParser.parse(SpineRakeTile.shadow) {
                    context.stroke(Path(shadow), with: .color(SpineColors.rakeShadow(scheme: scheme)),
                                   style: StrokeStyle(lineWidth: 0.75, lineCap: .butt))
                }
                if let lit = SVGPathParser.parse(SpineRakeTile.lit) {
                    context.stroke(Path(lit), with: .color(Color.pebbles.rakeLight),
                                   style: StrokeStyle(lineWidth: 0.6, lineCap: .butt))
                }
            }
            .frame(width: SpineLayout.rakeTile.width, height: SpineLayout.rakeTile.height)
        }
    }
}
```

The two path constants are copied verbatim from `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/spine/SpineRakePaths.kt` (the Kotlin string concatenations joined). Check with:

```bash
python3 - <<'PY'
import re
kt = open("apps/android/app/src/main/kotlin/app/pbbls/android/features/path/spine/SpineRakePaths.kt").read()
def kotlin(name):
    block = re.search(r"const val " + name + r" =\s*((?:\s*\"[^\"]*\"\s*\+?)+)", kt).group(1)
    return "".join(re.findall(r"\"([^\"]*)\"", block))
swift = open("apps/ios/Pebbles/Features/Path/Spine/Feed/SpineRakeTile.swift").read()
for kt_name, sw_name in (("SHADOW", "shadow"), ("LIT", "lit")):
    sw = re.search(r"static let " + sw_name + r" = \"([^\"]*)\"", swift).group(1)
    print(sw_name, "identical" if sw == kotlin(kt_name) else "DIFFERENT")
PY
```

Expected: `shadow identical`, `lit identical`.

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `SpineRakeTileTests`. Expected: PASS (2).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Feed/SpineRakeTile.swift apps/ios/PebblesTests/Spine/SpineRakeTileTests.swift
git commit -m "feat(ios): the spine's rake tile, verbatim from the web"
```

---

### Task D3.4: `SpineGround`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineGround.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI

/// The raked ground behind a week's days (#1130 D6, R11). One background of the
/// days block, so the tile's phase is the content's own y: no heights are
/// summed and a far jump cannot re-phase it (Android's lesson). Masked full to
/// `colX − 6`, then down to 22 % by `colX + 110`. The line (1 pt at x 40, in
/// `Color.pebbles.line`) runs over it from the first hairline to the bottom;
/// the mouth carries its last 26 pt. No grain (D7).
struct SpineGround: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        GeometryReader { proxy in
            let width = max(proxy.size.width, 1)
            ZStack(alignment: .topLeading) {
                if let tile = SpineRakeTile.image(scheme: scheme, scale: displayScale) {
                    Image(uiImage: tile)
                        .resizable(resizingMode: .tile)
                        .mask(mask(width: width))
                }
                Rectangle()
                    .fill(Color.pebbles.line)
                    .frame(width: SpineLayout.line,
                           height: max(0, proxy.size.height - SpineLayout.seamOverhang))
                    .offset(x: SpineLayout.spineX - SpineLayout.line / 2, y: SpineLayout.seamOverhang)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// Alpha only: any opaque role draws the mask.
    private func mask(width: CGFloat) -> LinearGradient {
        let opaque = Color.pebbles.onSurface
        return LinearGradient(stops: [
            .init(color: opaque, location: 0),
            .init(color: opaque, location: min(1, (SpineLayout.colX - 6) / width)),
            .init(color: opaque.opacity(0.22), location: min(1, (SpineLayout.colX + 110) / width))
        ], startPoint: .leading, endPoint: .trailing)
    }
}
```

- [ ] **Step 2: Build and commit**

Run **Build** and the **Suite command** with `ThemeLiteralsTests`. Expected: `** BUILD SUCCEEDED **`, PASS.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Feed/SpineGround.swift
git commit -m "feat(ios): the raked ground, drawn once behind a week's days"
```

---

### Task D3.5: `DaySeam`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Feed/DaySeam.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI

/// A day's start (#1130 R8–R10, A1): the hairline with its teeth and the date in
/// the margin, read by VoiceOver as one heading ("Monday, October 5"). The seam
/// is 22 pt with the hairline at 14, so the label hung 14 pt above the hairline
/// has its own room (Android lesson). On a day with moments the label runs on
/// into the day's 58 pt top padding, which follows the seam; a still day sets
/// *still* 14 pt under the hairline and is never shorter than 48 pt below it,
/// nor than its label at the current Dynamic Type size.
struct DaySeam: View {
    let date: Date
    let isStill: Bool
    /// The week's last day keeps its full height to the mouth; any other gives
    /// the next seam's 14 pt overhang back.
    var isLast = true

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.legibilityWeight) private var legibilityWeight

    private var calendar: Calendar { SpineCalendar.current }
    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SeamRule()
                .frame(height: SpineLayout.seamOverhang + SpineLayout.seamHeight)
            if isStill {
                Text(verbatim: SpineCopy.still(locale: locale))
                    .spineType(.still)
                    .foregroundStyle(Color.pebbles.outline)
                    .padding(.leading, SpineLayout.colX)
                    .padding(.top, SpineLayout.stillPadTop - SpineLayout.seamHeight)
            }
        }
        .frame(maxWidth: .infinity, minHeight: isStill ? stillHeight : nil, alignment: .topLeading)
        .overlay(alignment: .topLeading) {
            // A zero-width anchor on the line: the label centres on x 40 at any width.
            Color.clear
                .frame(width: 0, height: 0)
                .overlay(alignment: .top) { DayLabel(date: date).fixedSize() }
                .padding(.leading, SpineLayout.spineX)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: SpineDates.longDay(date, calendar: calendar, locale: locale)))
        .accessibilityAddTraits(.isHeader)
    }

    /// 48 pt hairline to hairline (the next seam's overhang comes off it), the
    /// last day's 48 to the mouth, and never less than the label plus 4.
    private var stillHeight: CGFloat {
        let numeral = SpineType.resolve(.dayNumeral, dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
        let weekday = SpineType.resolve(.dayWeekday, dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
        let label = numeral.uiFont.lineHeight + weekday.uiFont.lineHeight + DayLabel.verticalPadding
        return max(SpineLayout.stillMinHeight + (isLast ? SpineLayout.seamOverhang : 0), label + 4)
    }
}

/// The hairline in `Color.pebbles.seam` at 14 pt, and the teeth under it in
/// `Color.pebbles.teeth`: one 3.5 pt tooth every 7 pt from 2, and a second
/// period, 6.5 pt every 19 pt from 4.
private struct SeamRule: View {
    var body: some View {
        Canvas { context, size in
            let top = SpineLayout.seamOverhang
            context.fill(Path(CGRect(x: 0, y: top, width: size.width, height: 1)), with: .color(Color.pebbles.seam))
            var teeth = Path()
            let periods: [(offset: CGFloat, period: CGFloat, height: CGFloat)] = [(2, 7, 3.5), (4, 19, 6.5)]
            for (offset, period, height) in periods {
                for x in stride(from: offset, to: size.width, by: period) {
                    teeth.addRect(CGRect(x: x, y: top + 1, width: 1, height: height))
                }
            }
            context.fill(teeth, with: .color(Color.pebbles.teeth))
        }
        .accessibilityHidden(true)
    }
}

/// The date in the margin: the numeral over the weekday on a tab of ground,
/// 46 pt wide (wider at accessibility sizes), its bottom corners rounded 10.
private struct DayLabel: View {
    static let verticalPadding: CGFloat = 9

    let date: Date

    private var calendar: Calendar { SpineCalendar.current }

    var body: some View {
        VStack(spacing: 3) {
            Text(verbatim: "\(calendar.component(.day, from: date))")
                .spineType(.dayNumeral)
                .foregroundStyle(Color.pebbles.onSurface)
            Text(verbatim: SpineDates.shortWeekday(date, calendar: calendar, locale: SpineStrings.appLocale))
                .spineType(.dayWeekday)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
        .padding(.top, 1)
        .padding(.bottom, 5)
        .frame(minWidth: SpineLayout.labelWidth)
        .background(
            UnevenRoundedRectangle(bottomLeadingRadius: 10, bottomTrailingRadius: 10)
                .fill(Color.pebbles.surface)
        )
        .accessibilityHidden(true)
    }
}
```

- [ ] **Step 2: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Feed/DaySeam.swift
git commit -m "feat(ios): the day seam, its teeth and the date in the margin"
```

---

### Task D3.6: The seam references

**Files:**
- Create: `apps/ios/PebblesTests/References/SpineSeamReferences.swift`

- [ ] **Step 1: Write the suite**

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D3: Monday's seam with the day's top padding under it, then the
    /// still Friday, on the raked ground with the line: Paper and Night, English
    /// and French (the weekday), plus the still day at `.accessibility2`, where
    /// it grows with its label.
    @Suite("Spine seam references")
    @MainActor
    struct SpineSeamReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func seams(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(SeamsFixture(), named: "seams",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        @Test
        func seamsAx2() {
            ReferenceImage.assertMatches(SeamsFixture(), named: "seams",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }
    }
}

private struct SeamsFixture: View {
    private typealias F = SpineFixtureWeek

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            DaySeam(date: F.weekStart, isStill: false)
                .padding(.bottom, SpineLayout.dayPadTop - SpineLayout.seamHeight)
            DaySeam(date: LocalDays.adding(4, to: F.weekStart, calendar: F.calendar), isStill: true)
        }
        .background(alignment: .top) { SpineGround() }
        .padding(.top, SpineLayout.feedTop - SpineLayout.seamOverhang)
        .padding(.bottom, 24)
        .background(Color.pebbles.surface)
    }
}
```

- [ ] **Step 2: Look locally (not committed)**

Run **Look** at `SpineSeamReferences`. Expected: the hairline runs the full width with fine teeth under it; "5 / mon" sits on a ground tab centred on the line, covering it; the rake grooves are strong on the left and fade past the column; Friday reads "9 / fri" and an italic *still* in the column. In `-ax2`, Friday's block is taller and nothing overlaps. Clean afterwards.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/PebblesTests/References/SpineSeamReferences.swift
git commit -m "test(ios): spine seam references on the raked ground"
```

---

### Task D3.7: D3 verification, PR and CI references

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <D3-number> --title "feat(ios): the path model, the raked ground and the day seams" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Part of #1130`; **new pattern for review:** `PathModel`, the Path's first `@Observable` view model (fetch and delete passed as closures), with its `MomentsRevision` reload rule (`reloadIfChangedOutside`); decision 4 (the brief puts `PathModel` in D3, the spec in D5a; `PathView` adopts it in D5a); `page(for:)` is optional; D6 (one ground in content coordinates; Android's re-phasing cannot happen); the 22 pt seam with its hairline at 14 (Android lesson); the still day's height from the label's resolved metrics; the rake's shadow from `SpineColors` and its lit line from the `rakeLight` token (decision 1). No Lab Note, no `F-…` ids.

- [ ] **Step 3: Rebaseline on CI**

Run **Rebaseline on CI**. Compare `seams-night-en.png` with Android's `SpineWeekDaysDark`: the same seam, label and rake. Green after the approved re-run.

---
## Part D3c — `feat/1130-ios-spine-feed`

PR title: `feat(ios): spine entries, pebbles and the days of a week`. Body opens `Part of #1130`. Visible in the lab and the references only.

### Task D3c.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1130-ios-spine-feed
git branch --show-current
```

Expected: `feat/1130-ios-spine-feed`.

---

### Task D3c.1: The stone's description

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineStoneDescription.swift`
- Test: `apps/ios/PebblesTests/Spine/SpineStoneDescriptionTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1130 A2–A3: the stone's and the slot's name, "Flag march with Nadia. Monday,
/// October 5, 10:15 AM. Large gem, Proud. With Nadia. Opens the pebble."
/// (Android SpineStoneDescriptionTest). Every soul is named, in the page's order.
@Suite("Spine stone description")
struct SpineStoneDescriptionTests {
    typealias F = SpineFixtureWeek

    private func moment(_ name: String?) throws -> MomentSummary {
        try #require(F.moments.first { $0.name == name })
    }

    private func text(_ moment: MomentSummary, _ locale: Locale) -> String {
        SpineStoneDescription.text(moment, names: F.soulNames, order: [], calendar: F.calendar, locale: locale)
    }

    private func when(_ moment: MomentSummary, _ locale: Locale) -> String {
        SpineDates.longDay(moment.happenedAt, calendar: F.calendar, locale: locale) + ", "
            + SpineDates.time(moment.happenedAt, calendar: F.calendar, locale: locale)
    }

    @Test("a pebble: name, when, stone kind, emotion, who, then what it opens")
    func pebble() throws {
        let flag = try moment("Flag march with Nadia")
        #expect(text(flag, Vectors.en)
            == "Flag march with Nadia. \(when(flag, Vectors.en)). Large gem, Proud. With Nadia. Opens the pebble.")
    }

    @Test("a solitary pebble names nobody and leaves no double space")
    func solitary() throws {
        let rent = try moment("Rent went up again")
        #expect(text(rent, Vectors.fr) == "Rent went up again. \(when(rent, Vectors.fr)). Pierre de lave moyenne, "
            + "\(rent.pebble?.emotion?.localizedName ?? ""). Ouvre le galet.")
    }

    @Test("a bare moment says it is not a pebble yet, an untitled one says untitled")
    func bare() throws {
        let bread = try moment("Bread from the corner bakery")
        #expect(text(bread, Vectors.en)
            == "Bread from the corner bakery. \(when(bread, Vectors.en)). Not a pebble yet. Opens the moment.")
        let photo = try #require(F.moments.first { $0.name == nil && $0.pebble == nil })
        #expect(text(photo, Vectors.en).hasPrefix("untitled. "))
    }

    @Test("several souls are listed in full, in the locale's list form")
    func several() throws {
        let forest = try moment("Forest walk, all of us")
        #expect(text(forest, Vectors.en).contains("With Mara, Elio, and Bruno."))
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/SpineStoneDescriptionTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `SpineStoneDescriptionTests`. Expected: build FAIL with "cannot find 'SpineStoneDescription' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineStoneDescription.swift`:

```swift
import Foundation

/// The name VoiceOver reads for a stone, a card and an entry (#1130 A2, A3):
/// `path.spine.stoneA11y` for a pebble, `path.spine.blankA11y` for a bare
/// moment. Every soul is named, in the page's persons order, joined as the
/// language lists ("Mara, Elio, and Bruno"); a solitary pebble names nobody.
enum SpineStoneDescription {
    static func text(
        _ moment: MomentSummary, names: [String: String], order: [String], calendar: Calendar, locale: Locale
    ) -> String {
        let title = moment.name.flatMap { $0.isEmpty ? nil : $0 } ?? SpineCopy.untitled(locale: locale)
        let date = SpineDates.longDay(moment.happenedAt, calendar: calendar, locale: locale)
        let time = SpineDates.time(moment.happenedAt, calendar: calendar, locale: locale)
        let line = PeopleLines.peopleLine(moment.soulIds, order: order, locale: locale, namedInFull: .max)
        var lookup = names
        for soul in moment.pebble?.souls ?? [] {
            lookup[soul.id.uuidString.lowercased()] = lookup[soul.id.uuidString.lowercased()] ?? soul.name
        }
        let formatter = ListFormatter()
        formatter.locale = locale
        let listed = formatter.string(from: line.named.compactMap { lookup[$0] }) ?? ""
        let withNames = SpineCopy.withNames(listed, locale: locale)
        guard let pebble = moment.pebble else {
            return SpineCopy.blankA11y(name: title, date: date, time: time, withNames: withNames, locale: locale)
        }
        return SpineCopy.stoneA11y(name: title, date: date, time: time, kind: SpineCopy.stoneKind(pebble.valence,
                                   locale: locale), emotion: pebble.emotion?.localizedName ?? "",
                                   withNames: withNames, locale: locale)
    }
}
```

`ListFormatter` is right here (it is a VoiceOver sentence, read in the language's own list form); `dotDays` hand-joins because its vector pins the web's exact output.

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `SpineStoneDescriptionTests`. Expected: PASS (4). If `several` fails on the serial comma ("With Mara, Elio and Bruno."), the runtime's English list follows the region; record the output and match it in the test, since VoiceOver reads whichever the system lists.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Feed/SpineStoneDescription.swift apps/ios/PebblesTests/Spine/SpineStoneDescriptionTests.swift
git commit -m "feat(ios): what voiceover reads for a spine stone"
```

---

### Task D3c.2: `SpineEntry`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Feed/SpineEntry.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI

/// A moment without a photo, set as type (#1130 R6): the name in its intensity
/// size and valence cut (at most 22 ch wide, no line limit), the meta 8 pt below
/// (time · EMOTION in the spot ink), the people line 6 pt below. A solitary
/// pebble has no people line, and is never marked "alone". `SpinePebble` wraps
/// it in the slot's button, which spans the column (Android lesson).
struct SpineEntry: View {
    let moment: MomentSummary
    let palette: StonePalette
    let soulNames: [String: String]
    let soulGlyphs: [String: [GlyphStroke]]
    let soulOrder: [String]

    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.legibilityWeight) private var legibilityWeight

    private var locale: Locale { SpineStrings.appLocale }
    private var calendar: Calendar { SpineCalendar.current }

    static func type(for moment: MomentSummary) -> EntryType {
        EntryTypes.entryType(intensity: moment.pebble?.intensity ?? 2, valence: moment.pebble?.valence.positiveness ?? 0)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            name
            meta.padding(.top, 8)
            people.padding(.top, 6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private var name: some View {
        let type = Self.type(for: moment)
        let size = SpineType.resolve(.entryName(type.name), dynamicTypeSize: dynamicTypeSize,
                                     legibilityWeight: legibilityWeight).size
        if let name = moment.name, !name.isEmpty {
            Text(verbatim: name)
                .spineType(.entryName(type.name))
                .foregroundStyle(Color.pebbles.onSurface)
                .frame(maxWidth: 22 * 0.55 * size, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        } else {
            Text(verbatim: SpineCopy.untitled(locale: locale))
                .spineType(.entryName(.untitled(size: type.name.size)))
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
    }

    private var meta: some View {
        HStack(spacing: 6) {
            Text(verbatim: SpineDates.time(moment.happenedAt, calendar: calendar, locale: locale))
                .spineType(.entryTime)
                .foregroundStyle(Color.pebbles.onSurface)
            if let emotion = moment.pebble?.emotion {
                Text(verbatim: "·")
                    .spineType(.entryTime)
                    .foregroundStyle(Color.pebbles.outline)
                Text(verbatim: emotion.localizedName)
                    .spineType(.entryEmotion)
                    .foregroundStyle(SpineColors.spot(palette, scheme: scheme))
            }
        }
    }

    @ViewBuilder
    private var people: some View {
        let line = PeopleLines.peopleLine(moment.soulIds, order: soulOrder, locale: locale)
        let others = SpineCopy.others(count: line.others, words: line.othersWords, locale: locale)
        let parts = PeopleLineParts.parts(line, names: soulNames, with: SpineCopy.withWord(locale: locale),
                                          others: others, style: .line)
        if !parts.isEmpty {
            PeopleLineView(parts: parts, glyphs: soulGlyphs, markSide: 14, base: .people, name: .soulName,
                           aside: .peopleAside, more: .entryTime)
        }
    }
}
```

The "max 22 ch" is 22 × 0.55 em of the resolved size (Ysabeau's zero is about 0.55 em wide).

- [ ] **Step 2: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Feed/SpineEntry.swift
git commit -m "feat(ios): the spine entry, a pebble set as type"
```

---

### Task D3c.3: `SpinePebble`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Feed/SpinePebble.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI

/// What a Spine row needs from its page and its host (#1130 §View tree).
struct SpineFeedContext {
    let page: SpinePage
    let column: CGFloat
    let palette: (MomentSummary) -> EmotionPalette?
    let ratio: (MomentSummary) -> CGFloat?
    let pressRequests: StonePressRequests
    /// A press asks to open the moment on a face; the host decides what opens (until stack E, the detail sheet).
    let onOpen: (MomentSummary, CardFace) -> Void
    let onDelete: (MomentSummary) -> Void
}

/// One moment on the Spine (#1130 §View tree, §Press-request state): the stone
/// on the line at its anchor, the 1 pt leader from the line to the column, then
/// the card (a photo) or the entry.
///
/// - A stone press plays the press and, 360 ms later (at once under Reduce
///   Motion), asks to open on the verso (the recto for a bare moment).
/// - A card or entry press asks for the recto at once, and the stone plays its press.
/// - A press request for this moment (a contents tap) is claimed when it
///   appears and plays the press without opening (D8).
///
/// The row never clips (D4): the stone overhangs its top by up to 13.6 pt and
/// stays pressable. VoiceOver reads the stone, then the slot (A2, A3).
struct SpinePebble: View {
    let moment: MomentSummary
    let context: SpineFeedContext
    let slotFocus: AccessibilityFocusState<UUID?>.Binding

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.legibilityWeight) private var legibilityWeight
    @Environment(CardFrameRegistry.self) private var registry: CardFrameRegistry?
    @State private var press: StonePress?
    @State private var pendingOpen: PendingOpen?

    private struct PendingOpen: Equatable {
        let seq: Int
        let face: CardFace
    }

    private var hasCard: Bool { moment.firstSnapPath != nil }
    private var size: ValenceSizeGroup { (moment.pebble?.valence ?? .neutralMedium).sizeGroup }
    private var stonePalette: StonePalette {
        SpineColors.palette(context.palette(moment), isBlank: moment.pebble == nil)
    }

    /// 31 for a card; 0.6 × the resolved name size for an entry, so the stone
    /// stays centred on the name's first line at any Dynamic Type size (D11).
    private var anchor: CGFloat {
        if hasCard { return SpineLayout.cardAnchor }
        let name = SpineEntry.type(for: moment).name
        return 0.6 * SpineType.resolve(.entryName(name), dynamicTypeSize: dynamicTypeSize,
                                       legibilityWeight: legibilityWeight).size
    }

    private var description: String {
        SpineStoneDescription.text(moment, names: context.page.soulNames, order: context.page.soulOrder,
                                   calendar: SpineCalendar.current, locale: SpineStrings.appLocale)
    }

    var body: some View {
        let row = SpineLayout.stoneRow(anchor: anchor, stone: SpineLayout.stoneSize(size))
        slot
            .padding(.leading, SpineLayout.colX)
            .padding(.trailing, SpineLayout.padRight)
            .frame(maxWidth: .infinity, minHeight: row.minHeight, alignment: .topLeading)
            .overlay(alignment: .topLeading) { leader }
            .overlay(alignment: .topLeading) { stone(row) }
            .overlay(alignment: .topLeading) {
                // The contents' scroll target, at the stone's centre in layout (offsets do not move ids).
                Color.clear.frame(width: 1, height: 1)
                    .padding(.leading, SpineLayout.spineX)
                    .padding(.top, max(0, anchor))
                    .id(Self.stoneAnchor(moment.id))
            }
            .accessibilityElement(children: .contain)
            .stonePressLifetime($press)
            .task(id: context.pressRequests.pending) { claim() }
            .task(id: pendingOpen) {
                guard let open = pendingOpen else { return }
                try? await Task.sleep(for: .seconds(StoneMotion.openDelay))
                guard pendingOpen == open, !Task.isCancelled else { return }
                pendingOpen = nil
                context.onOpen(moment, open.face)
            }
    }

    static func stoneAnchor(_ id: UUID) -> String { "stone-\(id.uuidString)" }

    // MARK: - Parts

    private var leader: some View {
        Rectangle()
            .fill(Color.pebbles.line)
            .frame(width: SpineLayout.colX - SpineLayout.spineX, height: SpineLayout.line)
            .offset(x: SpineLayout.spineX, y: anchor - SpineLayout.line / 2)
            .accessibilityHidden(true)
    }

    private func stone(_ row: StoneRow) -> some View {
        Button(action: pressStone) {
            SpineStone(moment: moment, palette: context.palette(moment), press: press)
                .frame(width: row.hit.width, height: row.hit.height)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .offset(x: row.hit.minX, y: row.hit.minY)
        .accessibilityLabel(Text(verbatim: description))
        .accessibilitySortPriority(2)
    }

    private var slot: some View {
        Button(action: pressSlot) {
            if hasCard {
                SpineCard(moment: moment, palette: stonePalette, ratio: context.ratio(moment), column: context.column,
                          soulNames: context.page.soulNames, soulGlyphs: context.page.soulGlyphs,
                          soulOrder: context.page.soulOrder)
            } else {
                SpineEntry(moment: moment, palette: stonePalette, soulNames: context.page.soulNames,
                           soulGlyphs: context.page.soulGlyphs, soulOrder: context.page.soulOrder)
                    .opacity(registry?.hidden == moment.id ? 0 : 1)
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { frame in
                        registry?.report(moment.id, frame: frame)
                    }
                    .onDisappear { registry?.forget(moment.id) }
            }
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: description))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(named: Text("Delete")) { context.onDelete(moment) }
        .accessibilityFocused(slotFocus, equals: moment.id)
        .accessibilitySortPriority(1)
        .pebblesContextMenu([.delete { context.onDelete(moment) }])
    }

    // MARK: - Presses

    private func pressStone() {
        TapHaptics.play(.selection)
        press = .next(after: press, still: reduceMotion, now: .now)
        let face: CardFace = moment.pebble == nil ? .recto : .verso
        if reduceMotion {
            pendingOpen = nil
            context.onOpen(moment, face)
        } else {
            pendingOpen = PendingOpen(seq: press?.seq ?? 0, face: face)
        }
    }

    private func pressSlot() {
        press = .next(after: press, still: reduceMotion, now: .now)
        pendingOpen = nil
        context.onOpen(moment, .recto)
    }

    /// A contents tap's press (D8): played, never opened.
    private func claim() {
        guard let request = context.pressRequests.pending, request.momentId == moment.id else { return }
        context.pressRequests.claim(request)
        press = .next(after: press, still: reduceMotion, now: .now)
    }
}
```

If stack A's `PebblesMenuItem.delete` is spelled differently on `main`, `grep -n "static func delete" apps/ios/Pebbles/Components/Menus/PebblesMenu.swift` and use it as declared.

- [ ] **Step 2: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Feed/SpinePebble.swift
git commit -m "feat(ios): a spine pebble, its stone on the line and its press"
```

---

### Task D3c.4: The days' rows and the eager stack

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/SpineWeekRows.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/SpineWeekStack.swift`
- Test: `apps/ios/PebblesTests/Spine/SpineWeekRowsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1130 §Geometry: per day a seam then its pebbles; 58 above a day's first
/// pebble (carried by the seam row), 38 between pebbles, 34 below the last,
/// less the next seam's 14 pt overhang, except under the week's last day.
@Suite("Spine week rows")
@MainActor
struct SpineWeekRowsTests {
    @Test("the fixture week: seven seams, fifteen pebbles, in reading order")
    func order() async throws {
        let model = await SpineFixtureWeek.model()
        let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
        let rows = SpineWeekRows.days(page)
        #expect(rows.filter { if case .seam = $0 { return true } else { return false } }.count == 7)
        let pebbles = rows.compactMap { row -> MomentSummary? in
            if case let .pebble(moment, _, _) = row { return moment } else { return nil }
        }
        #expect(pebbles.map(\.id) == page.contents.map(\.id))
    }

    @Test("bottom pads: 38 inside a day, 20 before the next seam, 34 under the last day")
    func pads() async throws {
        let model = await SpineFixtureWeek.model()
        let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
        let pads = SpineWeekRows.days(page).compactMap { row -> CGFloat? in
            if case let .pebble(_, _, bottom) = row { return bottom } else { return nil }
        }
        #expect(pads.first == SpineLayout.pebbleGap)
        #expect(pads.contains(SpineLayout.dayPadBottom - SpineLayout.seamOverhang))
        #expect(pads.last == SpineLayout.dayPadBottom)
    }

    @Test("a slice keeps only the days asked for")
    func slice() async throws {
        let model = await SpineFixtureWeek.model()
        let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
        let rows = SpineWeekRows.days(page, only: 4...4)
        #expect(rows.count == 1)
        guard case let .seam(day, _) = rows[0] else { Issue.record("Friday is a seam"); return }
        #expect(day.isStill)
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/SpineWeekRowsTests.swift`.

- [ ] **Step 2: Add the fixture model helper**

Append to `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineFixtureWeek.swift`, before the final `#endif`:

```swift
extension SpineFixtureWeek {
    /// A `PathModel` holding the fixture week, its souls in the reference cache,
    /// for the lab and the references.
    @MainActor
    static func model() async -> PathModel {
        let model = PathModel(calendar: calendar, now: { today }, locale: { SpineStrings.appLocale })
        await model.load { moments }
        model.updateSouls(names: soulNames, glyphs: soulGlyphs)
        return model
    }

    /// The feed context over the fixture: its palettes and its primed photo ratios.
    @MainActor
    static func context(_ model: PathModel, page: SpinePage, width: CGFloat = 393) -> SpineFeedContext {
        SpineFeedContext(
            page: page, column: SpineLayout.column(width: width),
            palette: { palette(for: $0) },
            ratio: { $0.firstSnapPath.flatMap { photos[$0]?.ratio } },
            pressRequests: model.pressRequests,
            onOpen: { _, _ in }, onDelete: { _ in }
        )
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run the **Suite command** with `SpineWeekRowsTests`. Expected: build FAIL with "cannot find 'SpineWeekRows' in scope".

- [ ] **Step 4: Implement the rows**

Create `apps/ios/Pebbles/Features/Path/Spine/SpineWeekRows.swift`:

```swift
import SwiftUI

/// One row of a Spine week. D4 adds the verso's rows and the mouth.
enum SpineRow: Identifiable {
    /// A day's seam; `isLast` keeps a still last day's full height to the mouth.
    case seam(SpineDay<MomentSummary>, isLast: Bool)
    /// A moment, the day it belongs to, and the padding under it.
    case pebble(MomentSummary, day: Int, bottom: CGFloat)

    var id: String {
        switch self {
        case let .seam(day, _): return "seam-\(day.index)"
        case let .pebble(moment, _, _): return moment.id.uuidString
        }
    }
}

/// The one place the row order lives (#1130 §View tree): per day a seam, then
/// its pebbles. `SpineWeekView` lays the rows out lazily and `SpineWeekStack`
/// eagerly, so the app and the B2 references cannot disagree.
enum SpineWeekRows {
    /// - Parameter only: the day indices to keep (a reference slice); nil keeps all.
    static func days(_ page: SpinePage, only: ClosedRange<Int>? = nil) -> [SpineRow] {
        let days = page.week.days.filter { only?.contains($0.index) ?? true }
        var rows: [SpineRow] = []
        for (dayOffset, day) in days.enumerated() {
            let lastDay = dayOffset == days.count - 1
            rows.append(.seam(day, isLast: lastDay))
            for (index, moment) in day.moments.enumerated() {
                let bottom: CGFloat
                if index < day.moments.count - 1 {
                    bottom = SpineLayout.pebbleGap
                } else {
                    // The next seam's label hangs 14 pt into this day's bottom padding.
                    bottom = lastDay ? SpineLayout.dayPadBottom : SpineLayout.dayPadBottom - SpineLayout.seamOverhang
                }
                rows.append(.pebble(moment, day: day.index, bottom: bottom))
            }
        }
        return rows
    }
}

/// A row drawn for a page.
struct SpineRowView: View {
    let row: SpineRow
    let context: SpineFeedContext
    let slotFocus: AccessibilityFocusState<UUID?>.Binding

    var body: some View {
        switch row {
        case let .seam(day, isLast):
            DaySeam(date: day.date, isStill: day.isStill, isLast: isLast)
                .padding(.bottom, day.isStill ? 0 : SpineLayout.dayPadTop - SpineLayout.seamHeight)
        case let .pebble(moment, _, bottom):
            SpinePebble(moment: moment, context: context, slotFocus: slotFocus)
                .padding(.bottom, bottom)
        }
    }
}
```

- [ ] **Step 5: Implement the eager stack**

Create `apps/ios/Pebbles/Features/Path/Spine/SpineWeekStack.swift`:

```swift
import SwiftUI

/// A Spine page as a plain `VStack` (#1130 D4): the B2 references (which cannot
/// render a `ScrollView`) and the lab lay the rows out here. The ground runs
/// behind the days block only. D4 adds the verso above and the mouth below.
struct SpineWeekStack: View {
    let context: SpineFeedContext
    /// A reference slice of the days; nil draws them all.
    var days: ClosedRange<Int>?
    var width: CGFloat = 393

    @AccessibilityFocusState private var slotFocus: UUID?

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 0) {
                ForEach(SpineWeekRows.days(context.page, only: days)) { row in
                    SpineRowView(row: row, context: context, slotFocus: $slotFocus)
                }
            }
            .background(alignment: .top) { SpineGround() }
            .padding(.top, SpineLayout.feedTop - SpineLayout.seamOverhang)
        }
        .padding(.bottom, SpineLayout.feedBottom)
        .frame(width: width)
        .background(Color.pebbles.surface)
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run the **Suite command** with `SpineWeekRowsTests`. Expected: PASS (3).

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/SpineWeekRows.swift apps/ios/Pebbles/Features/Path/Spine/SpineWeekStack.swift apps/ios/Pebbles/Features/Path/Spine/Debug/SpineFixtureWeek.swift apps/ios/PebblesTests/Spine/SpineWeekRowsTests.swift
git commit -m "feat(ios): a spine week's days as rows, laid out lazily or eagerly"
```

---

### Task D3c.5: The day references and the lab's feed

**Files:**
- Create: `apps/ios/PebblesTests/References/SpineFeedReferences.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineLabView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineFixtureWeek.swift`

- [ ] **Step 1: The references**

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D3: the fixture week's days in slices (PNGs stay under 1 MB, B2
    /// §Risks), every entry cut, the cards, the glyph-less stone, the bare and
    /// untitled moments and the still Friday. Android: SpineFeedPicnic*,
    /// SpineWeekDays*.
    @Suite("Spine feed references")
    @MainActor
    struct SpineFeedReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func monTue(scheme: ReferenceScheme) async throws {
            ReferenceImage.assertMatches(try await stack(0...1), named: "monTue", config: ReferenceConfig(scheme: scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func wednesday(scheme: ReferenceScheme) async throws {
            ReferenceImage.assertMatches(try await stack(2...2), named: "wednesday",
                                         config: ReferenceConfig(scheme: scheme))
        }

        /// Thursday: the fourteen-soul picnic's people line, and the rooftop card.
        @Test(arguments: ReferenceScheme.allCases)
        func picnic(scheme: ReferenceScheme) async throws {
            ReferenceImage.assertMatches(try await stack(3...3), named: "picnic",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func friSun(scheme: ReferenceScheme) async throws {
            ReferenceImage.assertMatches(try await stack(4...6), named: "friSun", config: ReferenceConfig(scheme: scheme))
        }

        private func stack(_ days: ClosedRange<Int>) async throws -> some View {
            let model = await SpineFixtureWeek.model()
            let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
            return SpineWeekStack(context: SpineFixtureWeek.context(model, page: page), days: days)
                .environment(\.spineSnapSource, SpineFixturePhotos.source)
        }
    }
}
```

- [ ] **Step 2: A dense week for the scroll budget**

Append to the `extension SpineFixtureWeek` added in Task D3c.4 Step 2 (inside it, before its closing brace):

```swift
    /// Twenty pebbles in one week: the fixture's thirteen and seven copies an
    /// hour later, for the 20-pebble scroll budget (§Risks).
    static var dense: [MomentSummary] {
        let pebbles = moments.filter { $0.pebble != nil }
        let copies = pebbles.prefix(7).enumerated().map { index, moment in
            let id = UUID(uuidString: String(format: "00000000-0000-4000-C000-%012ld", index + 1)) ?? UUID()
            return MomentSummary(id: id, name: moment.name, happenedAt: moment.happenedAt.addingTimeInterval(3600),
                                 createdAt: moment.createdAt, firstSnapPath: moment.firstSnapPath, pebble: moment.pebble)
        }
        return pebbles + copies
    }
```

- [ ] **Step 3: The lab's feed section**

In `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineLabView.swift`:

1. Replace `        case stones, cards` with `        case stones, cards, feed, dense`.
2. Replace

```swift
    @Environment(\.dismiss) private var dismiss
    @State private var section: Section = .stones
```

with

```swift
    @Environment(\.dismiss) private var dismiss
    @State private var section: Section = .stones
    @State private var model: PathModel?
    @State private var denseModel: PathModel?
    @AccessibilityFocusState private var slotFocus: UUID?
```

3. Replace

```swift
                case .cards:
                    SpineCardGallery(numbers: [1, 4, 7, 9, 13, 15])
                }
```

with

```swift
                case .cards:
                    SpineCardGallery(numbers: [1, 4, 7, 9, 13, 15])
                case .feed:
                    lazyDays(model)
                case .dense:
                    lazyDays(denseModel)
                }
```

4. After `.background(Color.pebbles.surface)` (the `ScrollView`'s), add:

```swift
            .task {
                model = await SpineFixtureWeek.model()
                let dense = PathModel(calendar: SpineFixtureWeek.calendar, now: { SpineFixtureWeek.today })
                await dense.load { SpineFixtureWeek.dense }
                dense.updateSouls(names: SpineFixtureWeek.soulNames, glyphs: SpineFixtureWeek.soulGlyphs)
                denseModel = dense
            }
```

5. Before the closing brace of `struct SpineLabView`, add:

```swift
    /// The days in a `LazyVStack`, as the page will lay them out: the stone budget's test bed.
    @ViewBuilder
    private func lazyDays(_ model: PathModel?) -> some View {
        if let model, let page = model.page(for: SpineFixtureWeek.weekStart) {
            let context = SpineFixtureWeek.context(model, page: page)
            LazyVStack(spacing: 0) {
                ForEach(SpineWeekRows.days(page)) { row in
                    SpineRowView(row: row, context: context, slotFocus: $slotFocus)
                }
            }
            .background(alignment: .top) { SpineGround() }
            .padding(.top, SpineLayout.feedTop - SpineLayout.seamOverhang)
        }
    }
```

- [ ] **Step 4: Look locally (not committed)**

Run **Look** at `SpineFeedReferences`. Expected, against the spec's §Geometry: each stone sits on the line with its leader into the column; Monday's flag-march card hangs off a large gem at 31 pt; Elio's tooth is an entry in the highlight cut with "with [g] ELIO & [g] MARA" (or Mara first, by the persons order); Tuesday's rent entry is a lowlight italic with no people line; Wednesday's bread is a blank stone with no emotion; Thursday's picnic reads "with [g] MARA, [g] BRUNO & twelve others [g][g][g][g][g] +7"; Friday is *still*; Sunday's Bruno stone is uncarved and the untitled photo card is grey. Clean afterwards.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/PebblesTests/References/SpineFeedReferences.swift apps/ios/Pebbles/Features/Path/Spine/Debug
git commit -m "test(ios): spine day references and a lazy feed in the lab"
```

---

### Task D3c.6: D3c verification, PR, CI references and the scroll budget

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <D3c-number> --title "feat(ios): spine entries, pebbles and the days of a week" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Part of #1130`; key files; the press choreography (stone → verso after 360 ms, slot → recto at once, a claimed request presses without opening) as state (`StonePress`, `PendingOpen`); the overhanging stone stays pressable because nothing in a row clips (D4); the scroll-budget numbers from Step 4. No Lab Note, no `F-…` ids.

- [ ] **Step 3: Rebaseline on CI**

Run **Rebaseline on CI**. Compare `picnic-paper-en.png` with Android's `SpineFeedPicnicLight` and `monTue-night-en.png` with `SpineWeekDaysDark`: the same states, the same order of names. Green after the approved re-run.

- [ ] **Step 4: Device checks (iPhone 15; the orchestrator runs them with the maintainer)**

- Spine lab → feed: tap above a Monday stone's row edge (the overhang): the stone presses. Tap the card: it asks for the recto (no visible open yet in the lab). Long-press a slot: the orange Delete menu.
- Spine lab → dense, Release build, Instruments › Animation Hitches: scroll the 20-pebble week top to bottom and back twice. Budget: 60 fps on scroll, the first screen within 2 frames. If it misses, apply the spec's first mitigation, `.drawingGroup()` on `SpineStone`'s `litStone`, re-measure, and record both numbers in the PR. If it still misses, stop and report: the snapshot cache (§Risks) is its own task.
- VoiceOver over Monday: the seam heading, the stone ("… Large gem, Proud. With Nadia. Opens the pebble."), then the card with the same name and a "Delete" action.

---
## Part D4 — `feat/1130-ios-spine-opening`

PR title: `feat(ios): the week opens with its persons and contents and closes at the mouth`. Body opens `Part of #1130`. Visible in the lab and the references only. The opening, persons, mouth and empty week come first (Tasks D4.1–D4.6), then the contents and their seals (Tasks D4.7–D4.10).

### Task D4.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1130-ios-spine-opening
git branch --show-current
```

Expected: `feat/1130-ios-spine-opening`.

---

### Task D4.1: `WeekOpeningText`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Opening/WeekOpeningText.swift`
- Test: `apps/ios/PebblesTests/Spine/WeekOpeningTextTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1130 R18: the fixture week opens "the forty-first week · 2026", "The fifth
/// to the eleventh of October", "Thirteen pebbles, fourteen people." (Android
/// WeekOpeningTextTest), and an empty week says so.
@Suite("Week opening text")
@MainActor
struct WeekOpeningTextTests {
    typealias F = SpineFixtureWeek

    private func page(_ locale: Locale, moments: [MomentSummary] = F.moments) async throws -> SpinePage {
        let model = PathModel(calendar: F.calendar, now: { F.today }, locale: { locale })
        await model.load { moments }
        return try #require(model.page(for: F.weekStart))
    }

    @Test("the fixture week in English")
    func english() async throws {
        let text = WeekOpeningText(page: try await page(Vectors.en), locale: Vectors.en)
        #expect(text.kicker == "the forty-first week · 2026")
        #expect(text.title == "The fifth to the eleventh of October")
        #expect(text.summary == "Thirteen pebbles, fourteen people.")
    }

    @Test("the fixture week in French")
    func french() async throws {
        let text = WeekOpeningText(page: try await page(Vectors.fr), locale: Vectors.fr)
        #expect(text.kicker == "la quarante et unième semaine · 2026")
        #expect(text.title == "Du cinq au onze octobre")
        #expect(text.summary == "Treize galets, quatorze personnes.")
    }

    @Test("an empty current week has no pebbles and names no one")
    func empty() async throws {
        let text = WeekOpeningText(page: try await page(Vectors.en, moments: []), locale: Vectors.en)
        #expect(text.summary == "No pebbles.")
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/WeekOpeningTextTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `WeekOpeningTextTests`. Expected: build FAIL with "cannot find 'WeekOpeningText' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/Opening/WeekOpeningText.swift`:

```swift
import Foundation

/// The opening's three lines for a page (#1130 R18): the kicker, the title in
/// words and the summary. Pure, so the sentences are tested without a view.
struct WeekOpeningText: Equatable {
    let kicker: String
    let title: String
    let summary: String

    init(page: SpinePage, locale: Locale) {
        kicker = SpineCopy.kicker(ordinal: page.words.ordinal, year: page.words.year, locale: locale)
        title = SpineCopy.title(page.words.title, locale: locale)
        summary = SpineCopy.summary(pebbles: page.week.pebbleCount, people: page.persons.count, locale: locale)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `WeekOpeningTextTests`. Expected: PASS (3).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Opening/WeekOpeningText.swift apps/ios/PebblesTests/Spine/WeekOpeningTextTests.swift
git commit -m "feat(ios): the words a spine page opens with"
```

---

### Task D4.2: The masthead and the opening

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Opening/WeekMasthead.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/Opening/WeekOpeningView.swift`

The logic's `WeekOpening` enum (D1) owns that name, so the view is `WeekOpeningView` (a rename from the spec's view tree, recorded in the PR).

- [ ] **Step 1: `WeekMasthead.swift`**

```swift
import SwiftUI

/// The page's masthead (#1130 §View tree, D9): the way back a week on the
/// leading side ("week 35", a 44 pt target) when the pager has a previous page,
/// naming that page's own number; the ISO week number in ink on the trailing side.
struct WeekMasthead: View {
    let page: SpinePage
    let onFocus: (Date) -> Void

    var body: some View {
        HStack(alignment: .center) {
            if let previous = page.previous {
                Button { onFocus(previous.weekStart) } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left").imageScale(.small)
                        Text(verbatim: SpineCopy.weekLink(previous.number, locale: SpineStrings.appLocale))
                            .spineType(.masthead)
                    }
                    .frame(minWidth: SpineLayout.minTarget, minHeight: SpineLayout.minTarget, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            Spacer(minLength: 0)
            Text(verbatim: "\(page.words.number)")
                .spineType(.mastheadNumber)
                .foregroundStyle(Color.pebbles.onSurface)
                .frame(minHeight: SpineLayout.minTarget)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, SpineLayout.versoPadX)
    }
}
```

- [ ] **Step 2: `WeekOpeningView.swift`**

```swift
import SwiftUI

/// The opening (#1130 R18, A1): the kicker, the title in words (the page's
/// heading; balanced by wrapping freely, `lineLimit(nil)`), and the summary.
/// Gaps 46 above, 16 to the title, 12 to the summary.
struct WeekOpeningView: View {
    let text: WeekOpeningText

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(verbatim: text.kicker)
                .spineType(.kicker)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            Text(verbatim: text.title)
                .spineType(.title)
                .foregroundStyle(Color.pebbles.onSurface)
                .multilineTextAlignment(.leading)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, SpineLayout.openingTitleGap)
                .accessibilityAddTraits(.isHeader)
            Text(verbatim: text.summary)
                .spineType(.summary)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .padding(.top, SpineLayout.openingSummaryGap)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, SpineLayout.openingTop)
        .padding(.horizontal, SpineLayout.versoPadX)
    }
}
```

- [ ] **Step 3: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Opening/WeekMasthead.swift apps/ios/Pebbles/Features/Path/Spine/Opening/WeekOpeningView.swift
git commit -m "feat(ios): the spine masthead and the week's opening"
```

---

### Task D4.3: The persons of the week and their day dots

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Opening/DayDots.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/Opening/PersonsOfWeekView.swift`

- [ ] **Step 1: `DayDots.swift`**

```swift
import SwiftUI

/// A person's seven days (#1130 R15, A5): a filled ink dot for a day they appear
/// on, a 1 pt inset ring in ink-3 at 0.7 for the others; 7 pt dots, 5 pt gaps,
/// 79 pt wide. One VoiceOver element: "On four days: Monday 5, …".
struct DayDots: View {
    let days: [Bool]
    let label: String

    var body: some View {
        HStack(spacing: SpineLayout.dotGap) {
            ForEach(days.indices, id: \.self) { index in
                Group {
                    if days[index] {
                        Circle().fill(Color.pebbles.onSurface)
                    } else {
                        Circle().inset(by: 0.5).stroke(Color.pebbles.outline.opacity(0.7), lineWidth: 1)
                    }
                }
                .frame(width: SpineLayout.dot, height: SpineLayout.dot)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: label))
    }
}
```

- [ ] **Step 2: `PersonsOfWeekView.swift`**

```swift
import SwiftUI

/// The persons of the week (#1130 R15): the section label with the weekday
/// letters right-aligned over the dots (the reading day's in ink), then one row
/// per person in the people-line order: the glyph (24 in a 30 column), the
/// small-caps name and the seven dots, each row at least 44 tall. Eight rows,
/// then "and N others", which unfolds the rest.
struct PersonsOfWeekView: View {
    let page: SpinePage
    let readingDay: Int?

    @State private var unfolded = false

    private var locale: Locale { SpineStrings.appLocale }
    private var calendar: Calendar { SpineCalendar.current }

    var body: some View {
        let split = PersonsOfWeek.split(page.persons)
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text(verbatim: SpineCopy.persons(locale: locale))
                    .spineType(.sectionLabel)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 8)
                letters
            }
            .padding(.bottom, SpineLayout.sectionHeaderGap)
            ForEach(unfolded ? split.shown + split.folded : split.shown, id: \.soulId) { person in
                row(person)
            }
            if !split.folded.isEmpty && !unfolded {
                Button { unfolded = true } label: {
                    Text(verbatim: SpineCopy.personsMore(count: split.folded.count, locale: locale))
                        .spineType(.personsFold)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .padding(.leading, SpineLayout.foldIndent)
                        .frame(maxWidth: .infinity, minHeight: SpineLayout.minTarget, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, SpineLayout.sectionTop)
        .padding(.horizontal, SpineLayout.versoPadX)
    }

    private var letters: some View {
        let letters = WeekOpening.weekdayLetters(weekStart: page.weekStart, calendar: calendar, locale: locale)
        // Before any day reaches the reading line, the first day is read (Android's fallback).
        let read = readingDay ?? page.week.days.first?.index
        return HStack(spacing: SpineLayout.dotGap) {
            ForEach(letters.indices, id: \.self) { index in
                Text(verbatim: letters[index])
                    .spineType(.dayLetter)
                    .foregroundStyle(index == read ? Color.pebbles.onSurface : Color.pebbles.outline)
                    .fixedSize()
                    .frame(width: SpineLayout.dot)
            }
        }
        .accessibilityHidden(true)
    }

    private func row(_ person: PersonOfWeek) -> some View {
        let dots = WeekOpening.dotDays(person.days, weekStart: page.weekStart, calendar: calendar, locale: locale)
        return HStack(spacing: SpineLayout.personGap) {
            SoulMark(strokes: page.soulGlyphs[person.soulId], side: SpineLayout.personGlyph)
                .frame(width: SpineLayout.personGlyphColumn)
            Text(verbatim: person.name)
                .spineType(.personName)
                .foregroundStyle(Color.pebbles.onSurface)
            Spacer(minLength: 8)
            DayDots(days: person.days, label: SpineCopy.dayDots(count: dots.count, days: dots.days, locale: locale))
        }
        .frame(minHeight: SpineLayout.personRowMin)
        .accessibilityElement(children: .combine)
    }
}
```

- [ ] **Step 3: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Opening/DayDots.swift apps/ios/Pebbles/Features/Path/Spine/Opening/PersonsOfWeekView.swift
git commit -m "feat(ios): the persons of the week with their day dots"
```

---

### Task D4.4: The mouth and the empty week

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Opening/WeekMouth.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/Opening/SpineEmptyWeek.swift`

- [ ] **Step 1: `WeekMouth.swift`**

```swift
import SwiftUI

/// Where the week ends (#1130 §View tree, D9): the line's last 26 pt into a
/// three-stone outline cairn (14 / 19 / 25 wide), "the forty-first week ends
/// here", "The forty-second begins on Monday the twelfth." (the calendar's next
/// week: it is about time), and the link to the pager's next page, named by
/// that page's own number, when there is one.
struct WeekMouth: View {
    let page: SpinePage
    let onFocus: (Date) -> Void

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Rectangle()
                .fill(Color.pebbles.line)
                .frame(width: SpineLayout.line, height: SpineLayout.mouthLine)
                .padding(.leading, SpineLayout.spineX - SpineLayout.line / 2)
                .accessibilityHidden(true)
            cairn
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: SpineCopy.weekEnds(ordinal: page.words.ordinal, locale: locale))
                    .spineType(.mouthLabel)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                Text(verbatim: SpineCopy.nextBegins(ordinal: page.words.nextOrdinal, day: page.words.nextMonday,
                                                    locale: locale))
                    .spineType(.mouthLine)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                if let next = page.next {
                    Button { onFocus(next.weekStart) } label: {
                        HStack(spacing: 4) {
                            Text(verbatim: SpineCopy.weekLink(next.number, locale: locale)).spineType(.masthead)
                            Image(systemName: "chevron.right").imageScale(.small)
                        }
                        .frame(minHeight: SpineLayout.minTarget)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .padding(.top, 6)
                }
            }
            .padding(.leading, SpineLayout.colX)
            .padding(.trailing, SpineLayout.padRight)
            .padding(.top, 12)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Three stone outlines stacked on the line, smallest on top.
    private var cairn: some View {
        VStack(spacing: 2) {
            ForEach([14.0, 19.0, 25.0], id: \.self) { width in
                Ellipse()
                    .stroke(Color.pebbles.line, lineWidth: 1)
                    .frame(width: width, height: width * 0.62)
            }
        }
        .frame(width: 25)
        .padding(.leading, SpineLayout.spineX - 12.5)
        .accessibilityHidden(true)
    }
}
```

- [ ] **Step 2: `SpineEmptyWeek.swift`**

```swift
import SwiftUI

/// The current week with nothing in it yet (#1130): "Fresh week", the call to
/// collect one and the button that opens Capture. Moved here from the old
/// `WeekPathView`, with the same catalogue keys.
struct SpineEmptyWeek: View {
    let onCreate: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Text("Fresh week")
                .spineType(.bareTitle)
                .foregroundStyle(Color.pebbles.onSurface)
            Text("Collect a new pebble")
                .spineType(.summary)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            Button(action: onCreate) {
                Image(systemName: "plus")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(Color.pebbles.onPrimaryContainer)
                    .frame(width: SpineLayout.minTarget + 12, height: SpineLayout.minTarget + 12)
                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Color.pebbles.primaryContainer))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("New pebble"))
            .padding(.top, 12)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, SpineLayout.emptyPadY)
    }
}
```

The `+` is an SF Symbol in system text, outside the Spine's type, like stack C's `NewPebbleFab`.

- [ ] **Step 3: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Opening/WeekMouth.swift apps/ios/Pebbles/Features/Path/Spine/Opening/SpineEmptyWeek.swift
git commit -m "feat(ios): the week's mouth and the fresh week"
```

---

### Task D4.5: The verso and the closing in the rows

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Spine/Feed/SpinePebble.swift` (the context)
- Modify: `apps/ios/Pebbles/Features/Path/Spine/SpineWeekRows.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Spine/SpineWeekStack.swift`
- Modify: `apps/ios/PebblesTests/References/SpineFeedReferences.swift`
- Test: `apps/ios/PebblesTests/Spine/SpineWeekRowsTests.swift`

- [ ] **Step 1: Extend the rows test**

Append to `struct SpineWeekRowsTests` in `apps/ios/PebblesTests/Spine/SpineWeekRowsTests.swift`:

```swift
    @Test("the verso opens with the masthead, the opening and the persons, and the page ends at the mouth")
    func versoAndClosing() async throws {
        let model = await SpineFixtureWeek.model()
        let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
        #expect(SpineWeekRows.verso(page).map(\.id) == ["masthead", "opening", "persons"])
        #expect(SpineWeekRows.closing(page).map(\.id) == ["mouth"])
    }

    @Test("an empty current week has no persons, no days and the fresh week instead of the mouth")
    func emptyWeek() async throws {
        let model = PathModel(calendar: SpineFixtureWeek.calendar, now: { SpineFixtureWeek.today })
        await model.load { [] }
        let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
        #expect(SpineWeekRows.verso(page).map(\.id) == ["masthead", "opening"])
        #expect(SpineWeekRows.days(page).isEmpty)
        #expect(SpineWeekRows.closing(page).map(\.id) == ["empty"])
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `SpineWeekRowsTests`. Expected: build FAIL with "type 'SpineWeekRows' has no member 'verso'".

- [ ] **Step 3: The context gains the page's actions**

In `apps/ios/Pebbles/Features/Path/Spine/Feed/SpinePebble.swift`, replace

```swift
    let onOpen: (MomentSummary, CardFace) -> Void
    let onDelete: (MomentSummary) -> Void
}
```

with

```swift
    let onOpen: (MomentSummary, CardFace) -> Void
    let onDelete: (MomentSummary) -> Void
    /// A masthead or mouth link, a contents tap's page change.
    var onFocus: (Date) -> Void = { _ in }
    /// The empty week's button: opens Capture.
    var onCreate: () -> Void = {}
    /// The day being read (D12): the persons' letter and the contents' dot follow it.
    var readingDay: Int?
}
```

- [ ] **Step 4: The rows**

In `apps/ios/Pebbles/Features/Path/Spine/SpineWeekRows.swift`:

1. Replace

```swift
/// One row of a Spine week. D4 adds the verso's rows and the mouth.
enum SpineRow: Identifiable {
```

with

```swift
/// One row of a Spine week.
enum SpineRow: Identifiable {
    case masthead
    case opening
    case persons
    case mouth
    case empty
```

2. Replace

```swift
    var id: String {
        switch self {
        case let .seam(day, _): return "seam-\(day.index)"
```

with

```swift
    var id: String {
        switch self {
        case .masthead: return "masthead"
        case .opening: return "opening"
        case .persons: return "persons"
        case .mouth: return "mouth"
        case .empty: return "empty"
        case let .seam(day, _): return "seam-\(day.index)"
```

3. Replace

```swift
enum SpineWeekRows {
```

with

```swift
enum SpineWeekRows {
    /// The verso: the masthead, the opening, then the persons when anyone was there.
    static func verso(_ page: SpinePage) -> [SpineRow] {
        [.masthead, .opening] + (page.persons.isEmpty ? [] : [.persons])
    }

    /// The mouth, or the fresh week when the page has no moment (only the current week can be empty).
    static func closing(_ page: SpinePage) -> [SpineRow] {
        page.contents.isEmpty ? [.empty] : [.mouth]
    }
```

4. In `days(_:only:)`, replace `        let days = page.week.days.filter { only?.contains($0.index) ?? true }` with

```swift
        // An empty week draws the fresh week, not seven still days.
        guard !page.contents.isEmpty else { return [] }
        let days = page.week.days.filter { only?.contains($0.index) ?? true }
```

5. In `SpineRowView.body`, replace `        switch row {` with

```swift
        switch row {
        case .masthead:
            WeekMasthead(page: context.page, onFocus: context.onFocus)
        case .opening:
            WeekOpeningView(text: WeekOpeningText(page: context.page, locale: SpineStrings.appLocale))
        case .persons:
            PersonsOfWeekView(page: context.page, readingDay: context.readingDay)
        case .mouth:
            WeekMouth(page: context.page, onFocus: context.onFocus)
        case .empty:
            SpineEmptyWeek(onCreate: context.onCreate)
```

- [ ] **Step 5: The stack draws the whole page**

Replace the contents of `apps/ios/Pebbles/Features/Path/Spine/SpineWeekStack.swift` with:

```swift
import SwiftUI

/// A Spine page as a plain `VStack` (#1130 D4): the B2 references (which cannot
/// render a `ScrollView`) and the lab lay the rows out here, in the order
/// `SpineWeekRows` gives. The ground runs behind the days block only.
struct SpineWeekStack: View {
    enum Part { case whole, verso, days, closing }

    let context: SpineFeedContext
    /// A reference slice of the days; nil draws them all.
    var days: ClosedRange<Int>?
    var part: Part = .whole
    var width: CGFloat = 393

    @AccessibilityFocusState private var slotFocus: UUID?

    var body: some View {
        VStack(spacing: 0) {
            if part == .whole || part == .verso {
                rows(SpineWeekRows.verso(context.page))
            }
            if part == .whole || part == .days {
                let dayRows = SpineWeekRows.days(context.page, only: days)
                if !dayRows.isEmpty {
                    rows(dayRows)
                        .background(alignment: .top) { SpineGround() }
                        .padding(.top, SpineLayout.feedTop - SpineLayout.seamOverhang)
                }
            }
            if part == .whole || part == .closing {
                rows(SpineWeekRows.closing(context.page))
            }
        }
        .padding(.bottom, SpineLayout.feedBottom)
        .frame(width: width)
        .background(Color.pebbles.surface)
    }

    private func rows(_ rows: [SpineRow]) -> some View {
        VStack(spacing: 0) {
            ForEach(rows) { row in
                SpineRowView(row: row, context: context, slotFocus: $slotFocus)
            }
        }
    }
}
```

- [ ] **Step 6: Keep the day references on the days only**

In `apps/ios/PebblesTests/References/SpineFeedReferences.swift`, replace

```swift
            return SpineWeekStack(context: SpineFixtureWeek.context(model, page: page), days: days)
```

with

```swift
            return SpineWeekStack(context: SpineFixtureWeek.context(model, page: page), days: days, part: .days)
```

- [ ] **Step 7: Run the tests**

Run the **Suite command** with `SpineWeekRowsTests`. Expected: PASS (5).

- [ ] **Step 8: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine apps/ios/PebblesTests/Spine/SpineWeekRowsTests.swift apps/ios/PebblesTests/References/SpineFeedReferences.swift
git commit -m "feat(ios): a spine page from its masthead to its mouth"
```

---

### Task D4.6: The opening references

**Files:**
- Create: `apps/ios/PebblesTests/References/SpineWeekReferences.swift`

- [ ] **Step 1: Write the suite**

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D4 (B2 §B2.6): the fixture week's verso (+ French Paper, + `-ax2`
    /// Paper), the current week's mouth against a past week's (which links on),
    /// and the empty current week. Android: SpineWeekVerso*, SpineWeekMouthDark,
    /// SpineWeekEmpty*.
    @Suite("Spine week references")
    @MainActor
    struct SpineWeekReferences {
        typealias F = SpineFixtureWeek

        @Test(arguments: ReferenceScheme.allCases)
        func verso(scheme: ReferenceScheme) async throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try await stack(.verso), named: "verso",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test
        func versoAx2() async throws {
            ReferenceImage.assertMatches(try await stack(.verso), named: "verso",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func mouth(scheme: ReferenceScheme) async throws {
            ReferenceImage.assertMatches(try await stack(.closing), named: "mouth",
                                         config: ReferenceConfig(scheme: scheme))
        }

        /// A past week: the mouth links on to the next page, "week 41".
        @Test(arguments: ReferenceScheme.allCases)
        func mouthPast(scheme: ReferenceScheme) async throws {
            let august = MomentSummary(id: UUID(), name: "Late August", happenedAt: Vectors.at("2026-08-26T10:00:00Z"),
                                       createdAt: Vectors.at("2026-08-26T10:00:00Z"), firstSnapPath: nil, pebble: nil)
            let week = WeekRollBuilder.weekStart(for: august.happenedAt, calendar: F.calendar)
            ReferenceImage.assertMatches(try await stack(.closing, extra: [august], week: week), named: "mouthPast",
                                         config: ReferenceConfig(scheme: scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func empty(scheme: ReferenceScheme) async throws {
            let model = PathModel(calendar: F.calendar, now: { F.today })
            await model.load { [] }
            let page = try #require(model.page(for: F.weekStart))
            ReferenceImage.assertMatches(SpineWeekStack(context: F.context(model, page: page)), named: "empty",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        private func stack(
            _ part: SpineWeekStack.Part, extra: [MomentSummary] = [], week: Date = F.weekStart
        ) async throws -> some View {
            let model = PathModel(calendar: F.calendar, now: { F.today })
            await model.load { F.moments + extra }
            model.updateSouls(names: F.soulNames, glyphs: F.soulGlyphs)
            let page = try #require(model.page(for: week))
            return SpineWeekStack(context: F.context(model, page: page), part: part)
                .environment(\.spineSnapSource, SpineFixturePhotos.source)
        }
    }
}
```

- [ ] **Step 2: Look locally (not committed)**

Run **Look** at `SpineWeekReferences`. Expected: `verso`: "41" top right, no back link (the fixture is the only week); "THE FORTY-FIRST WEEK · 2026" in small caps; "The fifth to the eleventh of October" in light 40 pt; "Thirteen pebbles, fourteen people." in italic; "PERSONS OF THE WEEK" with "m t w t f s s" over the dots (Monday in ink: no reading day in a reference, so the first); eight rows (Mara first, four filled dots) then "and six others". In French: "la quarante et unième semaine · 2026", "Du cinq au onze octobre", "les personnes de la semaine", "et six autres". In `-ax2` the title wraps and the rows grow. `mouth`: the line's end, the cairn, "THE FORTY-FIRST WEEK ENDS HERE", "The forty-second begins on Monday the twelfth.", no link. `mouthPast`: "week 41 ›" under week 35's lines. `empty`: "41", the opening "No pebbles.", "Fresh week" and the `+`. Clean afterwards.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/PebblesTests/References/SpineWeekReferences.swift
git commit -m "test(ios): spine week references for the verso, the mouth and the empty week"
```

---

### Task D4.7: The seal's deep ink

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Spine/SpineColors.swift`
- Modify: `apps/ios/PebblesTests/Spine/SpineColorsTests.swift`

- [ ] **Step 1: Write the failing test**

Append to `struct SpineColorsTests`:

```swift
    @Test("the seal's deep ink: shaded on Paper, secondary at 80 % over the ground at Night")
    func deep() {
        #expect(SpineColors.deepSlot(scheme: .light) == (StonePalette.Slot.shaded, 1.0))
        #expect(SpineColors.deepSlot(scheme: .dark) == (StonePalette.Slot.secondary, 0.8))
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `SpineColorsTests`. Expected: build FAIL with "type 'SpineColors' has no member 'deepSlot'".

- [ ] **Step 3: Implement**

In `apps/ios/Pebbles/Features/Path/Spine/SpineColors.swift`, replace

```swift
    static func spot(_ palette: StonePalette, scheme: ColorScheme) -> Color {
        palette.color(spotSlot(scheme: scheme))
    }
}
```

with

```swift
    static func spot(_ palette: StonePalette, scheme: ColorScheme) -> Color {
        palette.color(spotSlot(scheme: scheme))
    }

    /// The seal's deep ink (R17): `shaded` on Paper; at Night `secondary` mixed
    /// 80 % toward the ground, drawn as `secondary` at 0.8 over the page's
    /// `surface` (iOS 17 has no colour mix; the seal always sits on the ground).
    static func deepSlot(scheme: ColorScheme) -> (slot: StonePalette.Slot, opacity: Double) {
        scheme == .dark ? (.secondary, 0.8) : (.shaded, 1)
    }

    static func deep(_ palette: StonePalette, scheme: ColorScheme) -> Color {
        let deep = deepSlot(scheme: scheme)
        return palette.color(deep.slot).opacity(deep.opacity)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `SpineColorsTests`. Expected: PASS (6).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/SpineColors.swift apps/ios/PebblesTests/Spine/SpineColorsTests.swift
git commit -m "feat(ios): the seal's deep ink from the emotion palette"
```

---

### Task D4.8: `StoneSeal`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Stone/StoneSeal.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI

/// A contents row's seal (#1130 R17), new on iOS: the stone's soft outline at
/// 20 / 24 / 27 pt, by valence a relief (highlight: a solid spot, the glyph
/// knocked out to the ground), a line (neutral: a 15 % tint, the rim and the
/// glyph in the spot) or an engraving (lowlight: a 9 % wash, a hatch in the
/// deep ink clipped to the body, the glyph with a ground halo). A blank moment's
/// seal is the line recipe in the blank ramp, with no glyph. The glyph's carve
/// is a constant 1.25 pt.
struct StoneSeal: View {
    let valence: Valence
    let glyph: PebbleGlyph?
    let palette: StonePalette
    let isBlank: Bool

    @Environment(\.colorScheme) private var scheme

    init(moment: MomentSummary, palette: StonePalette) {
        valence = moment.pebble?.valence ?? .neutralMedium
        glyph = moment.pebble?.glyph
        self.palette = palette
        isBlank = moment.pebble == nil
    }

    init(valence: Valence, glyph: PebbleGlyph?, palette: StonePalette, isBlank: Bool) {
        self.valence = valence
        self.glyph = glyph
        self.palette = palette
        self.isBlank = isBlank
    }

    var body: some View {
        let width = SpineLayout.sealWidth(valence.sizeGroup)
        let size = CGSize(width: width, height: width / CGFloat(PebbleOutlineGeometry.aspectRatio(for: valence.sizeGroup)))
        Canvas { context, size in
            guard let art = WobbleRenderer.softBackdropArt(size: valence.sizeGroup, polarity: valence.polarity) else {
                return
            }
            let body = WobbledBackdropShape(art: art).path(in: CGRect(origin: .zero, size: size))
            let mark = glyphPath(in: size)
            let spot = SpineColors.spot(palette, scheme: scheme)
            let ground = Color.pebbles.surface
            switch isBlank ? .neutral : valence.polarity {
            case .highlight:
                context.fill(body, with: .color(spot))
                context.stroke(mark, with: .color(ground), style: Self.carve(1.25))
            case .neutral:
                context.fill(body, with: .color(spot.opacity(0.15)))
                context.stroke(body, with: .color(spot), lineWidth: 1)
                context.stroke(mark, with: .color(spot), style: Self.carve(1.25))
            case .lowlight:
                let deep = SpineColors.deep(palette, scheme: scheme)
                context.fill(body, with: .color(spot.opacity(0.09)))
                var hatch = context
                hatch.clip(to: body)
                hatch.stroke(Self.hatch(in: size), with: .color(deep), lineWidth: 0.6)
                context.stroke(mark, with: .color(ground), style: Self.carve(2.6))
                context.stroke(mark, with: .color(deep), style: Self.carve(1.25))
            }
        }
        .frame(width: size.width, height: size.height)
        .accessibilityHidden(true)
    }

    private static func carve(_ width: CGFloat) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    /// The glyph's strokes (a 200 × 200 drawing) in the middle 56 % of the seal.
    private func glyphPath(in size: CGSize) -> Path {
        guard !isBlank, let strokes = glyph?.strokes, !strokes.isEmpty else { return Path() }
        let side = 0.56 * min(size.width, size.height)
        let scale = side / 200
        let transform = CGAffineTransform(translationX: (size.width - side) / 2, y: (size.height - side) / 2)
            .scaledBy(x: scale, y: scale)
        var path = Path()
        for stroke in strokes {
            if let parsed = SVGPathParser.parse(stroke.d) { path.addPath(Path(parsed), transform: transform) }
        }
        return path
    }

    /// Diagonal lines every 2.2 pt across the seal.
    private static func hatch(in size: CGSize) -> Path {
        var path = Path()
        for x in stride(from: -size.height, through: size.width, by: 2.2) {
            path.move(to: CGPoint(x: x, y: size.height))
            path.addLine(to: CGPoint(x: x + size.height, y: 0))
        }
        return path
    }
}
```

- [ ] **Step 2: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Stone/StoneSeal.swift
git commit -m "feat(ios): the contents' seal in relief, line or engraving"
```

---

### Task D4.9: `WeekContents` and its row

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/Opening/WeekContents.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Spine/Feed/SpinePebble.swift` (the context's `onGo`)
- Modify: `apps/ios/Pebbles/Features/Path/Spine/SpineWeekRows.swift`
- Modify: `apps/ios/PebblesTests/Spine/SpineWeekRowsTests.swift`

- [ ] **Step 1: Update the rows test**

In `apps/ios/PebblesTests/Spine/SpineWeekRowsTests.swift`, replace

```swift
        #expect(SpineWeekRows.verso(page).map(\.id) == ["masthead", "opening", "persons"])
```

with

```swift
        #expect(SpineWeekRows.verso(page).map(\.id) == ["masthead", "opening", "persons", "contents"])
```

Run the **Suite command** with `SpineWeekRowsTests`. Expected: FAIL (`versoAndClosing`).

- [ ] **Step 2: `WeekContents.swift`**

```swift
import SwiftUI

/// The week's contents (#1130 R16, A4): one row per moment in reading order;
/// the day (short weekday, lowercase, period stripped) on a day's first row
/// only, under a rule; the seal; the name by intensity (lowlights in a light
/// italic); the time in ink-3. The reading day's rows carry a 5 pt ink dot
/// 12 pt left of the row. A row is a button named by the moment: it scrolls its
/// stone to the centre and presses it, without opening (D8; `onGo` is the page's).
struct WeekContents: View {
    let page: SpinePage
    let readingDay: Int?
    let palette: (MomentSummary) -> EmotionPalette?
    let onGo: (MomentSummary) -> Void

    private var locale: Locale { SpineStrings.appLocale }
    private var calendar: Calendar { SpineCalendar.current }

    private struct Row: Identifiable {
        let moment: MomentSummary
        let day: Int
        let firstOfDay: Bool
        var id: UUID { moment.id }
    }

    private var rows: [Row] {
        page.week.days.flatMap { day in
            day.moments.enumerated().map { index, moment in Row(moment: moment, day: day.index, firstOfDay: index == 0) }
        }
    }

    var body: some View {
        let read = readingDay ?? page.week.days.first?.index
        VStack(alignment: .leading, spacing: 0) {
            Text(verbatim: SpineCopy.contents(locale: locale))
                .spineType(.sectionLabel)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .accessibilityAddTraits(.isHeader)
                .padding(.bottom, SpineLayout.sectionHeaderGap)
            ForEach(rows) { row in
                rowView(row, isRead: row.day == read)
            }
        }
        .padding(.top, SpineLayout.sectionTop)
        .padding(.horizontal, SpineLayout.versoPadX)
    }

    private func rowView(_ row: Row, isRead: Bool) -> some View {
        let moment = row.moment
        let name = moment.name.flatMap { $0.isEmpty ? nil : $0 }
        let cut = name == nil
            ? SpineType.Cut.untitled(size: 16.5)
            : EntryTypes.contentsNameType(intensity: moment.pebble?.intensity ?? 2,
                                         valence: moment.pebble?.valence.positiveness ?? 0)
        return Button { onGo(moment) } label: {
            HStack(alignment: .center, spacing: 0) {
                Text(verbatim: row.firstOfDay ? SpineDates.shortWeekday(moment.happenedAt, calendar: calendar,
                                                                        locale: locale) : "")
                    .spineType(.contentsDay)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .frame(width: SpineLayout.contentsDayColumn, alignment: .leading)
                StoneSeal(moment: moment, palette: SpineColors.palette(palette(moment), isBlank: moment.pebble == nil))
                    .frame(width: SpineLayout.contentsSealColumn)
                Text(verbatim: name ?? SpineCopy.untitled(locale: locale))
                    .spineType(.contentsName(cut))
                    .foregroundStyle(name == nil ? Color.pebbles.onSurfaceVariant : Color.pebbles.onSurface)
                    .padding(.vertical, 6)
                    .padding(.trailing, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(verbatim: SpineDates.time(moment.happenedAt, calendar: calendar, locale: locale))
                    .spineType(.contentsTime)
                    .foregroundStyle(Color.pebbles.outline)
            }
            .frame(minHeight: SpineLayout.contentsRowMin)
            .padding(.vertical, 2)
            .overlay(alignment: .top) {
                if row.firstOfDay { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
            }
            .overlay(alignment: .leading) {
                if isRead {
                    Circle().fill(Color.pebbles.onSurface)
                        .frame(width: SpineLayout.readingDot, height: SpineLayout.readingDot)
                        .offset(x: -SpineLayout.readingDotOffset)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: name ?? SpineCopy.untitled(locale: locale)))
        .accessibilityHint(Text(verbatim: SpineCopy.contentsHint(locale: locale)))
        .accessibilityAddTraits(.isButton)
    }
}
```

- [ ] **Step 3: The context's `onGo` and the row**

1. In `apps/ios/Pebbles/Features/Path/Spine/Feed/SpinePebble.swift`, replace

```swift
    /// The empty week's button: opens Capture.
    var onCreate: () -> Void = {}
```

with

```swift
    /// The empty week's button: opens Capture.
    var onCreate: () -> Void = {}
    /// A contents tap: scroll to the stone and press it, without opening (D8).
    var onGo: (MomentSummary) -> Void = { _ in }
```

2. In `apps/ios/Pebbles/Features/Path/Spine/SpineWeekRows.swift`:
   - In `enum SpineRow`, replace

```swift
    case persons
    case mouth
```

with

```swift
    case persons
    case contents
    case mouth
```

   - Replace `        case .persons: return "persons"` with

```swift
        case .persons: return "persons"
        case .contents: return "contents"
```

   - Replace `        [.masthead, .opening] + (page.persons.isEmpty ? [] : [.persons])` with

```swift
        [.masthead, .opening] + (page.persons.isEmpty ? [] : [.persons]) + (page.contents.isEmpty ? [] : [.contents])
```

   - Replace

```swift
        case .persons:
            PersonsOfWeekView(page: context.page, readingDay: context.readingDay)
```

with

```swift
        case .persons:
            PersonsOfWeekView(page: context.page, readingDay: context.readingDay)
        case .contents:
            WeekContents(page: context.page, readingDay: context.readingDay, palette: context.palette,
                         onGo: context.onGo)
```

   Update the verso comment above `verso(_:)` to read `/// The verso: the masthead, the opening, the persons when anyone was there, and the contents.`

- [ ] **Step 4: Run the tests**

Run the **Suite command** with `SpineWeekRowsTests`. Expected: PASS (5).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine apps/ios/PebblesTests/Spine/SpineWeekRowsTests.swift
git commit -m "feat(ios): the week's contents, a row per moment with its seal"
```

---

### Task D4.10: The contents and seal references

**Files:**
- Create: `apps/ios/PebblesTests/References/SpineContentsReferences.swift`

- [ ] **Step 1: Write the suite**

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D4: the fixture week's contents (fifteen rows, days on their first
    /// rows, Monday's rows dotted as the reading day), English and French; and
    /// the nine seals plus the blank one, Paper and Night.
    @Suite("Spine contents references")
    @MainActor
    struct SpineContentsReferences {
        typealias F = SpineFixtureWeek

        @Test(arguments: ReferenceScheme.allCases)
        func contents(scheme: ReferenceScheme) async throws {
            let model = await F.model()
            let page = try #require(model.page(for: F.weekStart))
            let view = WeekContents(page: page, readingDay: nil, palette: { F.palette(for: $0) }, onGo: { _ in })
                .padding(.bottom, 24)
                .frame(width: ReferenceConfig.phoneWidth)
                .background(Color.pebbles.surface)
            ReferenceImage.assertMatches(view, named: "contents",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func seals(scheme: ReferenceScheme) {
            let glyph = F.moments.first { $0.name == "Flag march with Nadia" }?.pebble?.glyph
            let view = VStack(spacing: 16) {
                ForEach(ValenceSizeGroup.allCases) { size in
                    HStack(spacing: 24) {
                        ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                            if let valence = Valence.allCases.first(where: {
                                $0.sizeGroup == size && $0.polarity == polarity
                            }) {
                                StoneSeal(valence: valence, glyph: glyph, palette: .brand, isBlank: false)
                                    .scaleEffect(3)
                                    .frame(width: 96, height: 96)
                            }
                        }
                    }
                }
                StoneSeal(valence: .neutralMedium, glyph: nil, palette: .blank, isBlank: true)
                    .scaleEffect(3)
                    .frame(width: 96, height: 96)
            }
            .padding(24)
            .background(Color.pebbles.surface)
            ReferenceImage.assertMatches(view, named: "seals", config: ReferenceConfig(scheme: scheme, width: nil))
        }
    }
}
```

- [ ] **Step 2: Look locally (not committed)**

Run **Look** at `SpineContentsReferences`. Expected: "CONTENTS", then fifteen rows; "mon", "tue", "wed", "thu", "sat", "sun" each on its day's first row under a hairline (no Friday); Monday's rows carry a dot left of the day column; seals vary by valence (solid, outlined, hatched) and size; names weigh by intensity, lowlights in italic; times in ink-3. French: "lun", "mar", "sommaire", "sans titre". The seals grid shows three recipes by column. Re-run **Look** at `SpineWeekReferences` too: the verso now ends with the contents. Clean afterwards.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/PebblesTests/References/SpineContentsReferences.swift
git commit -m "test(ios): spine contents and seal references"
```

---

### Task D4.11: D4 verification, PR and CI references

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**. Before the PR, open `Localizable.xcstrings` in Xcode: "Fresh week", "Collect a new pebble" and "New pebble" are still `translated` in both languages and nothing is `New` or `Stale`.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <D4-number> --title "feat(ios): the week opens with its persons and contents and closes at the mouth" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Part of #1130`; key files; D9 (the links name the pager's neighbour; only "begins on Monday" is the calendar's); the `WeekOpeningView` rename (the logic owns `WeekOpening`); the empty week has no days; the seal is new on iOS (R17); the Night deep ink drawn as `secondary` at 0.8 over the ground (no colour mix on iOS 17); the verso ends with the contents. No Lab Note, no `F-…` ids.

- [ ] **Step 3: Rebaseline on CI**

Run **Rebaseline on CI**. Compare `verso-paper-en.png` with Android's `SpineWeekVersoLight` (contents included), `mouth-night-en.png` with `SpineWeekMouthDark` and `empty-paper-en.png` with `SpineWeekEmptyLight`. Green after the approved re-run.

---
## Part D4c — `feat/1130-ios-spine-pager`

PR title: `feat(ios): the spine week pages, rolls and presses from its contents`. Body opens `Part of #1130`. Visible in the lab only.

### Task D4c.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1130-ios-spine-pager
git branch --show-current
```

Expected: `feat/1130-ios-spine-pager`.

---

### Task D4c.1: `ReadingDay`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/ReadingDay.swift`
- Test: `apps/ios/PebblesTests/Spine/ReadingDayTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Testing
@testable import Pebbles

/// #1130 D12 and decision 2: of the realised marks (seams and pebble rows, each
/// with its day and its top in the page's space), the day of the last one at or
/// above 35 % of the viewport (Android ReadingDayTest).
@Suite("ReadingDay")
struct ReadingDayTests {
    typealias Mark = ReadingDay.Mark

    @Test("the last mark at or above the reading line wins")
    func line() {
        let marks = [Mark(day: 0, minY: -500), Mark(day: 0, minY: -100), Mark(day: 1, minY: 200), Mark(day: 1, minY: 400)]
        #expect(ReadingDay.index(marks, viewportHeight: 1000) == 1)
        #expect(ReadingDay.index(marks, viewportHeight: 400) == 0)
    }

    @Test("a mark exactly on the line counts")
    func onTheLine() {
        #expect(ReadingDay.index([Mark(day: 3, minY: 350)], viewportHeight: 1000) == 3)
    }

    @Test("nothing has reached the line yet: nil, and the page reads its first day")
    func none() {
        #expect(ReadingDay.index([Mark(day: 0, minY: 600)], viewportHeight: 1000) == nil)
        #expect(ReadingDay.index([], viewportHeight: 1000) == nil)
    }

    @Test("the marks' order of arrival does not matter")
    func unordered() {
        let marks = [Mark(day: 2, minY: 300), Mark(day: 1, minY: -50), Mark(day: 4, minY: 800)]
        #expect(ReadingDay.index(marks, viewportHeight: 1000) == 2)
    }

    @Test("a long day whose seam has scrolled away is still read through its pebbles")
    func longDay() {
        #expect(ReadingDay.index([Mark(day: 3, minY: -900), Mark(day: 3, minY: 120)], viewportHeight: 800) == 3)
    }
}
```

Save it as `apps/ios/PebblesTests/Spine/ReadingDayTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `ReadingDayTests`. Expected: build FAIL with "cannot find 'ReadingDay' in scope".

- [ ] **Step 3: Implement**

Create `apps/ios/Pebbles/Features/Path/Spine/ReadingDay.swift`:

```swift
import SwiftUI

/// The day being read (#1130 D12): each realised seam and pebble row reports
/// its day and its top in the page's named coordinate space (a `PreferenceKey`;
/// `onScrollGeometryChange` needs iOS 18). The reading day is the last mark at
/// or above 35 % of the viewport. Pebble rows report too (decision 2), so a long
/// day whose seam has left the `LazyVStack` is still read. The model writes it
/// only on a change.
enum ReadingDay {
    /// The web's `rootMargin: -35% …`.
    static let line: CGFloat = 0.35

    struct Mark: Equatable {
        let day: Int
        let minY: CGFloat
    }

    static func index(_ marks: [Mark], viewportHeight: CGFloat) -> Int? {
        let lineY = viewportHeight * line
        return marks.filter { $0.minY <= lineY }.max { $0.minY < $1.minY }?.day
    }
}

struct ReadingDayKey: PreferenceKey {
    static let defaultValue: [ReadingDay.Mark] = []

    static func reduce(value: inout [ReadingDay.Mark], nextValue: () -> [ReadingDay.Mark]) {
        value.append(contentsOf: nextValue())
    }
}

extension View {
    /// Reports this row's day and top for the reading day, in `space`.
    func spineDayMark(_ day: Int, in space: String) -> some View {
        background(GeometryReader { proxy in
            Color.clear.preference(key: ReadingDayKey.self,
                                   value: [ReadingDay.Mark(day: day, minY: proxy.frame(in: .named(space)).minY)])
        })
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `ReadingDayTests`. Expected: PASS (5).

- [ ] **Step 5: The rows report their marks**

In `apps/ios/Pebbles/Features/Path/Spine/SpineWeekRows.swift`, in `struct SpineRowView`:

1. Replace

```swift
    let slotFocus: AccessibilityFocusState<UUID?>.Binding

    var body: some View {
```

with

```swift
    let slotFocus: AccessibilityFocusState<UUID?>.Binding
    /// The page's coordinate space for the reading day; nil (the eager stack) reports nothing.
    var markSpace: String?

    var body: some View {
        content
            .modifier(DayMark(day: day, space: markSpace))
    }

    private var day: Int? {
        switch row {
        case let .seam(day, _): return day.index
        case let .pebble(_, day, _): return day
        default: return nil
        }
    }

    @ViewBuilder
    private var content: some View {
```

2. At the end of the file, add:

```swift
private struct DayMark: ViewModifier {
    let day: Int?
    let space: String?

    func body(content: Content) -> some View {
        if let day, let space {
            content.spineDayMark(day, in: space)
        } else {
            content
        }
    }
}
```

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/ReadingDay.swift apps/ios/Pebbles/Features/Path/Spine/SpineWeekRows.swift apps/ios/PebblesTests/Spine/ReadingDayTests.swift
git commit -m "feat(ios): the reading day from the seams and pebbles a page reports"
```

---

### Task D4c.2: `SpineOpen` and `SpineWeekView`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/SpineOpen.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/SpineWeekView.swift`

- [ ] **Step 1: `SpineOpen.swift`**

```swift
import Foundation

/// What a press asks to open (#1130 §Frame registry, the canonical shape stack E
/// consumes as written): the moment, the face, the summary to seed the card, and
/// the page's persons order, so the held card names people as the Spine does.
/// Ids are the logic's lowercase strings.
struct SpineOpen {
    let momentId: UUID
    let face: CardFace
    let summary: MomentSummary
    let soulOrder: [String]
}
```

- [ ] **Step 2: `SpineWeekView.swift`**

```swift
import SwiftUI

/// One page of the pager (#1130 D4, §Press-request state): a `ScrollViewReader`
/// › `ScrollView` of the page's rows, the days in a `LazyVStack` on the raked
/// ground. It owns the reading day (D12), the contents → press flow (D8), the
/// slot VoiceOver returns to when stack E closes a card, and the scroll to top.
struct SpineWeekView: View {
    let page: SpinePage
    let model: PathModel
    let palette: (MomentSummary) -> EmotionPalette?
    let ratio: (MomentSummary) -> CGFloat?
    let onOpen: (SpineOpen) -> Void
    let onDelete: (MomentSummary) -> Void
    let onCreate: () -> Void
    var scrollToTopToken = 0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @AccessibilityFocusState private var slotFocus: UUID?
    /// The phone's size until the first layout reports the page's own.
    @State private var viewport = CGSize(width: 393, height: 800)
    @State private var go: GoRequest?

    private struct GoRequest: Equatable {
        let momentId: UUID
        let seq: Int
    }

    private static let space = "spine-page"
    private static let top = "spine-top"

    private var context: SpineFeedContext {
        SpineFeedContext(
            page: page, column: SpineLayout.column(width: viewport.width), palette: palette, ratio: ratio,
            pressRequests: model.pressRequests,
            onOpen: { moment, face in
                onOpen(SpineOpen(momentId: moment.id, face: face, summary: moment, soulOrder: page.soulOrder))
            },
            onDelete: onDelete,
            onFocus: { week in
                if reduceMotion { model.focus(week) } else { withAnimation { model.focus(week) } }
            },
            onCreate: onCreate,
            onGo: { moment in go = GoRequest(momentId: moment.id, seq: (go?.seq ?? 0) + 1) },
            readingDay: model.readingDay[page.weekStart]
        )
    }

    var body: some View {
        let context = context
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    Color.clear.frame(height: 0).id(Self.top)
                    rows(SpineWeekRows.verso(page), context: context)
                    let days = SpineWeekRows.days(page)
                    if !days.isEmpty {
                        LazyVStack(spacing: 0) {
                            ForEach(days) { row in
                                SpineRowView(row: row, context: context, slotFocus: $slotFocus, markSpace: Self.space)
                                    .id(row.id)
                            }
                        }
                        .background(alignment: .top) { SpineGround() }
                        .padding(.top, SpineLayout.feedTop - SpineLayout.seamOverhang)
                    }
                    rows(SpineWeekRows.closing(page), context: context)
                }
                .padding(.bottom, SpineLayout.feedBottom)
            }
            .coordinateSpace(name: Self.space)
            .accessibilityElement(children: .contain)
            .accessibilityLabel(Text(verbatim: feedLabel))
            .onGeometryChange(for: CGSize.self) { $0.size } action: { viewport = $0 }
            .onPreferenceChange(ReadingDayKey.self) { marks in
                guard let day = ReadingDay.index(marks, viewportHeight: viewport.height) else { return }
                model.setReadingDay(day, for: page.weekStart)
            }
            .onChange(of: scrollToTopToken) {
                if reduceMotion { proxy.scrollTo(Self.top, anchor: .top) } else {
                    withAnimation { proxy.scrollTo(Self.top, anchor: .top) }
                }
            }
            .onChange(of: model.returnFocus) { _, id in slotFocus = id }
            .task(id: go) { await perform(go, proxy: proxy) }
        }
    }

    private func rows(_ rows: [SpineRow], context: SpineFeedContext) -> some View {
        VStack(spacing: 0) {
            ForEach(rows) { row in
                SpineRowView(row: row, context: context, slotFocus: $slotFocus)
            }
        }
    }

    private var feedLabel: String {
        let locale = SpineStrings.appLocale
        let calendar = SpineCalendar.current
        let sunday = LocalDays.adding(6, to: page.weekStart, calendar: calendar)
        return SpineCopy.feedA11y(range: SpineDates.dayRange(page.weekStart, sunday, calendar: calendar,
                                                             locale: locale), locale: locale)
    }

    /// A contents tap (D8): scroll the row in, which realises it; on the next
    /// frame scroll its stone to the centre; after 620 ms ask the stone to press.
    /// All of it instant under Reduce Motion.
    private func perform(_ request: GoRequest?, proxy: ScrollViewProxy) async {
        guard let request else { return }
        let animation: Animation? = reduceMotion ? nil : .easeInOut(duration: 0.35)
        withAnimation(animation) { proxy.scrollTo(request.momentId.uuidString, anchor: .center) }
        try? await Task.sleep(for: .milliseconds(16))
        withAnimation(animation) { proxy.scrollTo(SpinePebble.stoneAnchor(request.momentId), anchor: .center) }
        if !reduceMotion { try? await Task.sleep(for: .seconds(StoneMotion.goPressDelay)) }
        // A newer tap, or the page leaving, cancels this one.
        guard !Task.isCancelled else { return }
        model.pressRequests.request(request.momentId)
    }
}
```

- [ ] **Step 3: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/SpineOpen.swift apps/ios/Pebbles/Features/Path/Spine/SpineWeekView.swift
git commit -m "feat(ios): a spine week page that reads its day and presses from its contents"
```

---

### Task D4c.3: `SpinePager` and `SpineWeekRoll`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Spine/SpinePager.swift`
- Create: `apps/ios/Pebbles/Features/Path/Spine/Roll/SpineWeekRoll.swift`

- [ ] **Step 1: `SpinePager.swift`**

```swift
import SwiftUI

/// The week pager (#1130 D5): a horizontal paging `ScrollView` over a
/// `LazyHStack` of `SpineWeekView`s, its position bound to
/// `PathModel.focusedWeekStart`. A settled swipe writes through `focus(_:)`; a
/// roll tap, a link or a refocus moves the position; `focus` ignores an equal
/// week, so the two never loop. Only the visible page and its neighbour during
/// a swipe are realised. New pattern (it replaces `TabView(.page)`), named in
/// the D4c PR. A page change announces the week's title (A6) and prefetches the
/// photo ratios of the week and its neighbours.
struct SpinePager: View {
    let model: PathModel
    let palette: (MomentSummary) -> EmotionPalette?
    let onOpen: (SpineOpen) -> Void
    let onDelete: (MomentSummary) -> Void
    let onCreate: () -> Void
    var scrollToTopToken = 0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(SnapAspectCache.self) private var aspects: SnapAspectCache?
    @State private var position: Date?

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 0) {
                ForEach(model.entries) { entry in
                    Group {
                        if let page = model.page(for: entry.weekStart) {
                            SpineWeekView(page: page, model: model, palette: palette, ratio: ratio, onOpen: onOpen,
                                          onDelete: onDelete, onCreate: onCreate,
                                          scrollToTopToken: entry.weekStart == model.focusedWeekStart
                                              ? scrollToTopToken : 0)
                        }
                    }
                    .containerRelativeFrame(.horizontal)
                    .id(entry.weekStart)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollIndicators(.hidden)
        .scrollPosition(id: $position)
        // iOS 17 can report nil on first layout and drift when the entries
        // change under it (§Risks): seed it, and re-assert it after each load.
        .onAppear { position = model.focusedWeekStart }
        .onChange(of: model.entries.map(\.weekStart)) { position = model.focusedWeekStart }
        .onChange(of: position) { _, settled in
            if let settled { model.focus(settled) }
        }
        .onChange(of: model.focusedWeekStart) { _, week in
            guard position != week else { return }
            if reduceMotion { position = week } else { withAnimation { position = week } }
        }
        .onChange(of: model.focusedWeekStart) { _, week in announce(week) }
        .task(id: model.focusedWeekStart) {
            await aspects?.prefetch(model.photoPaths(around: model.focusedWeekStart))
        }
    }

    private func ratio(_ moment: MomentSummary) -> CGFloat? {
        moment.firstSnapPath.flatMap { aspects?.ratio(for: $0) }
    }

    private func announce(_ week: Date) {
        guard let page = model.page(for: week) else { return }
        AccessibilityNotification.Announcement(WeekOpeningText(page: page, locale: SpineStrings.appLocale).title).post()
    }
}
```

- [ ] **Step 2: `SpineWeekRoll.swift`**

```swift
import SwiftUI

/// The week roll (#1130 §View tree): the ISO week numerals in 56 × 44 cells, in
/// ink-2; the focused week's in ink over a 1 pt underline. A tap focuses that
/// week. It keeps the old roll's VoiceOver label ("Week 41, 15 pebbles").
struct SpineWeekRoll: View {
    let model: PathModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal) {
                LazyHStack(spacing: 0) {
                    ForEach(model.entries) { entry in
                        cell(entry).id(entry.weekStart)
                    }
                }
                .padding(.horizontal, 16)
            }
            .scrollIndicators(.hidden)
            .onAppear { proxy.scrollTo(model.focusedWeekStart, anchor: .center) }
            .onChange(of: model.focusedWeekStart) { _, week in
                if reduceMotion { proxy.scrollTo(week, anchor: .center) } else {
                    withAnimation { proxy.scrollTo(week, anchor: .center) }
                }
            }
        }
        .frame(height: SpineLayout.minTarget)
    }

    private func cell(_ entry: WeekRollEntry) -> some View {
        let number = model.calendar.component(.weekOfYear, from: entry.weekStart)
        let focused = entry.weekStart == model.focusedWeekStart
        return Button {
            if reduceMotion { model.focus(entry.weekStart) } else { withAnimation { model.focus(entry.weekStart) } }
        } label: {
            Text(verbatim: "\(number)")
                .spineType(.rollNumeral)
                .foregroundStyle(focused ? Color.pebbles.onSurface : Color.pebbles.onSurfaceVariant)
                .frame(width: 56, height: SpineLayout.minTarget)
                .overlay(alignment: .bottom) {
                    if focused {
                        Rectangle().fill(Color.pebbles.onSurface).frame(width: 20, height: 1).padding(.bottom, 8)
                    }
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Week \(number), \(entry.moments.count) pebbles")
        .accessibilityAddTraits(focused ? .isSelected : [])
    }
}
```

- [ ] **Step 3: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/SpinePager.swift apps/ios/Pebbles/Features/Path/Spine/Roll/SpineWeekRoll.swift
git commit -m "feat(ios): the spine pager and its week roll"
```

---

### Task D4c.4: The lab's pager

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineLabView.swift`

- [ ] **Step 1: Add the section**

In `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineLabView.swift`:

1. Replace `        case stones, cards, feed, dense` with `        case stones, cards, feed, dense, pager`.
2. Replace `    @State private var denseModel: PathModel?` with

```swift
    @State private var denseModel: PathModel?
    @State private var pagerModel: PathModel?
    @State private var lastOpen = ""
```

3. Replace

```swift
            ScrollView {
                switch section {
                case .stones:
                    SpineStoneGallery(pressable: true)
                case .cards:
                    SpineCardGallery(numbers: [1, 4, 7, 9, 13, 15])
                case .feed:
                    lazyDays(model)
                case .dense:
                    lazyDays(denseModel)
                }
            }
            .background(Color.pebbles.surface)
```

with

```swift
            Group {
                if section == .pager {
                    pager
                } else {
                    gallery
                }
            }
            .background(Color.pebbles.surface)
```

   The `.task { … }` that loads the models now follows the `Group`. After `body`, add:

```swift
    private var gallery: some View {
        ScrollView {
            switch section {
            case .stones:
                SpineStoneGallery(pressable: true)
            case .cards:
                SpineCardGallery(numbers: [1, 4, 7, 9, 13, 15])
            case .feed:
                lazyDays(model)
            case .dense:
                lazyDays(denseModel)
            case .pager:
                EmptyView()
            }
        }
    }
```

4. In that `.task`, after `denseModel = dense`, add:

```swift
                let august = MomentSummary(id: UUID(), name: "Late August",
                                           happenedAt: ISO8601Flexible.parse("2026-08-26T10:00:00Z") ?? .now,
                                           createdAt: .now, firstSnapPath: nil, pebble: nil)
                let paged = PathModel(calendar: SpineFixtureWeek.calendar, now: { SpineFixtureWeek.today })
                await paged.load { SpineFixtureWeek.moments + [august] }
                paged.updateSouls(names: SpineFixtureWeek.soulNames, glyphs: SpineFixtureWeek.soulGlyphs)
                pagerModel = paged
```

5. Before the closing brace of `struct SpineLabView`, add:

```swift
    /// The fixture week and a late-August week: the roll, the pager, the links
    /// (week 35 ↔ week 41) and the contents → press. Opens are logged on screen.
    @ViewBuilder
    private var pager: some View {
        if let pagerModel {
            VStack(spacing: 0) {
                SpineWeekRoll(model: pagerModel)
                Text(verbatim: lastOpen).font(.caption.monospaced()).foregroundStyle(Color.pebbles.outline)
                SpinePager(
                    model: pagerModel,
                    palette: { SpineFixtureWeek.palette(for: $0) },
                    onOpen: { lastOpen = "open \($0.summary.name ?? "untitled") on the \($0.face.rawValue)" },
                    onDelete: { lastOpen = "delete \($0.name ?? "untitled")" },
                    onCreate: { lastOpen = "create" }
                )
                .environment(\.spineSnapSource, .none)
            }
        }
    }
```

The lab draws the pager's cards square until a ratio is known: it passes no `SnapAspectCache`, and `SpinePager` reads ratios from it (decision 7).

- [ ] **Step 2: Build and commit**

Run **Build** and **Lint**. Expected: green.

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Debug/SpineLabView.swift
git commit -m "feat(ios): the spine lab pages two weeks"
```

---

### Task D4c.5: Device spike: the paging `ScrollView` (§Risks; the orchestrator runs it with the maintainer)

**Files:** none, unless Step 3 applies.

- [ ] **Step 1: On the iPhone 15, Spine lab → pager**

1. Swipe between the two weeks: each swipe settles on a page; the roll's underline follows; the masthead of week 41 reads "‹ week 35" and week 35's mouth reads "week 41 ›".
2. Tap "week 35", then the roll's "41": the pager moves; no double move, no loop.
3. Scroll week 41 vertically with a slightly diagonal finger: the page scrolls, the pager does not steal it.
4. Tap a contents row ("Rooftop birthday"): the page scrolls the stone to the middle, then the stone presses; no open is logged.
5. Scroll down: the persons' letter and the contents' dot follow the day crossing a third of the screen, including the long Thursday.
6. VoiceOver: each page change announces the week's title.
7. Reduce Motion on: every move is instant, the press is the still touch.

- [ ] **Step 2: Record**

Record pass/fail per item in the D4c PR body.

- [ ] **Step 3 (only if 1, 2 or 3 fails): fall back to `TabView(.page)`**

Replace `SpinePager`'s `body` with:

```swift
    var body: some View {
        TabView(selection: Binding(get: { model.focusedWeekStart }, set: { model.focus($0) })) {
            ForEach(model.entries) { entry in
                Group {
                    if let page = model.page(for: entry.weekStart) {
                        SpineWeekView(page: page, model: model, palette: palette, ratio: ratio, onOpen: onOpen,
                                      onDelete: onDelete, onCreate: onCreate,
                                      scrollToTopToken: entry.weekStart == model.focusedWeekStart
                                          ? scrollToTopToken : 0)
                    }
                }
                .tag(entry.weekStart)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .onChange(of: model.focusedWeekStart) { _, week in announce(week) }
        .task(id: model.focusedWeekStart) {
            await aspects?.prefetch(model.photoPaths(around: model.focusedWeekStart))
        }
    }
```

and delete the now-unused `@State private var position: Date?`. Repeat Step 1, record that the fallback is in use (the registry still works: its frames are global; ±1 pages stay live, so the stone budget is re-measured in D5a), and commit:

```bash
git add apps/ios/Pebbles/Features/Path/Spine/SpinePager.swift
git commit -m "fix(ios): page the spine weeks with a page tab view on ios 17"
```

---

### Task D4c.6: D4c verification and PR

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <D4c-number> --title "feat(ios): the spine week pages, rolls and presses from its contents" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Part of #1130`; key files; **new pattern for review:** the SwiftUI paging `ScrollView` (`scrollTargetBehavior(.paging)` + `scrollPosition(id:)`) replacing `TabView(.page)`, and the spike's results (or the fallback in use); decision 2 (pebble rows report the reading day too); `SpineOpen` as the spec's canonical block, for stack E; decision 4 (`SpineOpen` here). No Lab Note, no `F-…` ids.

---
## Part D5a — `feat/1130-ios-path-becomes-spine`

PR title: `feat(ios): the path becomes the spine`. Body opens `Resolves #1130` (decision 5). This is the part that changes what the app shows.

### Task D5a.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/1130-ios-path-becomes-spine
git branch --show-current
```

Expected: `feat/1130-ios-path-becomes-spine`.

---

### Task D5a.1: The app's ratio cache, card frames and snap source

**Files:**
- Modify: `apps/ios/Pebbles/PebblesApp.swift`
- Modify: `apps/ios/Pebbles/RootView.swift`

- [ ] **Step 1: `SnapAspectCache` beside `SnapURLCache`**

In `apps/ios/Pebbles/PebblesApp.swift`:

1. Replace `    @State private var snapURLs: SnapURLCache` with

```swift
    @State private var snapURLs: SnapURLCache
    @State private var snapAspects: SnapAspectCache
```

2. Replace `        self._snapURLs = State(initialValue: SnapURLCache(client: supabase.client))` with

```swift
        let snapURLs = SnapURLCache(client: supabase.client)
        self._snapURLs = State(initialValue: snapURLs)
        // The Spine's photo ratios (#1130 D3), signed through the same cache.
        self._snapAspects = State(initialValue: SnapAspectCache(urls: snapURLs))
```

3. Replace `                .environment(snapURLs)` with

```swift
                .environment(snapURLs)
                .environment(snapAspects)
```

- [ ] **Step 2: The registry and the snap source in `RootView`**

In `apps/ios/Pebbles/RootView.swift`:

1. After the `@SceneStorage("tabs.navigation") private var storedTabs: Data?` line (stack C), add:

```swift
    /// Where each Spine slot sits on screen, for stack E's held card (#1130
    /// §Frame registry). Created here, above the tabs, so the overlay E adds
    /// beside `PebblesTabView` reads the same one.
    @State private var cardFrames = CardFrameRegistry()
```

2. Replace

```swift
                    PebblesTabView(onPathFirstLoad: { pathFeedLoaded = true })
                        .environment(nav)
```

with

```swift
                    PebblesTabView(onPathFirstLoad: { pathFeedLoaded = true })
                        .environment(nav)
                        .environment(cardFrames)
                        .environment(\.spineSnapSource, .signed(snapURLs))
```

3. In the `#Preview`, after `.environment(MomentsRevision())`, add `.environment(SnapAspectCache(urls: SnapURLCache(client: SupabaseService().client)))`.

- [ ] **Step 3: Build and commit**

Run **Build**. Expected: `** BUILD SUCCEEDED **`.

```bash
git add apps/ios/Pebbles/PebblesApp.swift apps/ios/Pebbles/RootView.swift
git commit -m "feat(ios): the spine's ratio cache, card frames and signed photos in the app"
```

---

### Task D5a.2: `PathView` renders the Spine

**Files:**
- Modify (replace): `apps/ios/Pebbles/Features/Path/PathView.swift`

- [ ] **Step 1: Read what `main` has**

```bash
grep -n "safeAreaInset\|PathStatusCluster\|NewPebbleFab\|scrollToTop\|revision\.\|PebbleDetailSheet(" apps/ios/Pebbles/Features/Path/PathView.swift
```

Expected: stack C's FAB row (`PathStatusCluster` + `NewPebbleFab`), `nav.scrollToTop[.path, default: 0]`, `revision.bump(from: .path)` twice, `.onChange(of: revision.value)`, and `PebbleDetailSheet(momentId: id, origin: .path, onPebbleUpdated:`. If a later PR changed the FAB row (for example #446 giving `onRipple` its own sheet), copy `main`'s `safeAreaInset` block into Step 2's file in place of the one shown, unchanged.

- [ ] **Step 2: Replace the file**

Replace the contents of `apps/ios/Pebbles/Features/Path/PathView.swift` with:

```swift
import SwiftUI
import os

/// The Path tab's root (#1130): the Spine. The week roll over a pager of Spine
/// weeks (`SpinePager`); `PathModel` holds the weeks, the focused week and the
/// pages. This view hands the environment's services to the model, and owns
/// Capture, the detail sheet (until stack E's held card), the delete dialog and
/// stack C's FAB row. Its `NavigationStack` lives in `PebblesTabView`.
struct PathView: View {
    /// Fired when the first load settles (success or failure), so `RootView`
    /// keeps the launch loader up until the home feed is ready.
    var onFirstLoad: () -> Void = {}

    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(PathStatsService.self) private var stats
    @Environment(CompleteDraftStore.self) private var completeDrafts
    @Environment(ReferenceDataService.self) private var references
    @Environment(TabNavigation.self) private var nav
    @Environment(MomentsRevision.self) private var revision

    @State private var model = PathModel()
    /// The Capture flow, what `+` opens.
    @State private var isPresentingCapture = false
    @State private var selectedMomentId: UUID?
    @State private var pendingDeletion: MomentDeleteTarget?
    /// The latest reload asked for; a newer one cancels it, so a slow first
    /// fetch cannot land after the second.
    @State private var reloadTask: Task<Void, Never>?
    @State private var deleteError: String?

    private var service: MomentsService { MomentsService(client: supabase.client) }

    /// The server's `active_today` compares against UTC; a device-local check
    /// keeps "active today" honest past local midnight (M22 follow-up).
    private var rippleWithLocalActiveToday: RippleSummary? {
        guard let server = stats.ripple else { return nil }
        let activeToday = model.moments.contains { $0.isCompleted && Calendar.current.isDateInToday($0.createdAt) }
        return RippleSummary(rippleLevel: server.rippleLevel, pebbles28d: server.pebbles28d, activeToday: activeToday)
    }

    var body: some View {
        content
            .toolbar(.hidden, for: .navigationBar)
            .pebblesScreen()
            .task {
                await model.load { try await service.pathMoments() }
                onFirstLoad()
            }
            .task { await stats.load() }
            .task(id: references.souls) { syncSouls() }
            // Path reselected at its root (#903 §4): "top of Path" means now.
            .onChange(of: nav.scrollToTop[.path, default: 0]) { model.focusCurrentWeek() }
            // #903 D5: a moment changed from another tab or a sheet.
            .onChange(of: revision.value) {
                guard revision.changedOutside(.path) else { return }
                Task { await stats.refresh() }
                reloadTask?.cancel()
                reloadTask = Task { await model.reloadIfChangedOutside(revision) { try await service.pathMoments() } }
            }
            .fullScreenCover(isPresented: $isPresentingCapture) {
                // Fired after the save and again after a chained completion.
                CaptureFlowView(onMomentChanged: { momentId in
                    revision.bump(from: .path)
                    Task { await stats.refresh() }
                    reload(focusing: momentId)
                })
            }
            .sheet(item: $selectedMomentId) { id in
                PebbleDetailSheet(momentId: id, origin: .path, onPebbleUpdated: {
                    Task {
                        async let timeline: Void = model.load { try await service.pathMoments() }
                        async let statsReload: Void = stats.refresh()
                        _ = await (timeline, statsReload)
                    }
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
            // No spinner: the launch loader owns the loading visual (#598).
            Color.pebbles.surface
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .failed:
            // Unchanged from the row Path: an English line (follow-up in the D5a PR).
            Text(verbatim: "Couldn't load your pebbles.")
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .content:
            VStack(spacing: 0) {
                SpineWeekRoll(model: model)
                SpinePager(
                    model: model,
                    palette: palette,
                    onOpen: open,
                    onDelete: { pendingDeletion = MomentDeleteTarget(summary: $0) },
                    onCreate: { isPresentingCapture = true },
                    scrollToTopToken: nav.scrollToTop[.path, default: 0]
                )
            }
            // One row, clear background, 8 pt above the tab bar (#903 §5). The
            // page scrolls under it; the Spine's 96 pt feed bottom clears it.
            .safeAreaInset(edge: .bottom) {
                HStack(alignment: .center, spacing: 16) {
                    PathStatusCluster(
                        karma: stats.karma,
                        ripple: rippleWithLocalActiveToday,
                        onKarma: { nav.select(.you) },
                        onRipple: { nav.select(.you) }
                    )
                    Spacer(minLength: 0)
                    NewPebbleFab(onTap: { isPresentingCapture = true })
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
        }
    }

    private func palette(_ moment: MomentSummary) -> EmotionPalette? {
        moment.pebble?.emotion.flatMap { palettes.palette(for: $0.id) }
    }

    /// Until stack E's held card: every press opens today's detail sheet.
    private func open(_ open: SpineOpen) {
        selectedMomentId = open.momentId
    }

    private func syncSouls() {
        let souls = references.souls
        model.updateSouls(
            names: Dictionary(souls.map { ($0.id.uuidString.lowercased(), $0.name) }, uniquingKeysWith: { first, _ in first }),
            glyphs: Dictionary(souls.map { ($0.id.uuidString.lowercased(), $0.glyph.strokes) },
                               uniquingKeysWith: { first, _ in first })
        )
    }

    /// Focus the week the moment landed in once the reload has it.
    private func reload(focusing momentId: UUID? = nil) {
        reloadTask?.cancel()
        reloadTask = Task {
            await model.load { try await service.pathMoments() }
            guard !Task.isCancelled, let momentId else { return }
            model.focusWeek(containing: momentId)
        }
    }

    private func delete(_ option: MomentDeleteOption, on target: MomentDeleteTarget) async {
        let deleted = await model.delete {
            try await MomentDeletion.perform(option, on: target, client: supabase.client, drafts: completeDrafts)
        }
        guard deleted else {
            deleteError = String(localized: "Something went wrong. Please try again.")
            return
        }
        revision.bump(from: .path)
        async let timeline: Void = model.load { try await service.pathMoments() }
        async let statsReload: Void = stats.refresh()
        _ = await (timeline, statsReload)
    }
}

#Preview {
    let supabase = SupabaseService()
    return NavigationStack {
        PathView()
    }
    .environment(supabase)
    .environment(EmotionPaletteService(client: supabase.client))
    .environment(ReferenceDataService(client: supabase.client))
    .environment(PathStatsService(supabase: supabase))
    .environment(CompleteDraftStore())
    .environment(TabNavigation())
    .environment(MomentsRevision())
    .environment(SnapAspectCache(urls: SnapURLCache(client: supabase.client)))
}
```

`references.souls` is `[SoulWithGlyph]` (`Hashable`), so it can key the `.task`. `model.delete` logs a failure itself; this view only shows the alert.

- [ ] **Step 3: Build, suite, lint**

Run **Build**, the **Full suite** and **Lint**. Expected: green. `PathView` no longer names `WeekRollView`, `WeekHeaderView` or `WeekPathView` (`grep -n "WeekRollView\|WeekHeaderView\|WeekPathView" apps/ios/Pebbles/Features/Path/PathView.swift` prints nothing); D5b deletes them.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PathView.swift
git commit -m "feat(ios): the path tab renders the spine"
```

---

### Task D5a.3: The whole-week references

**Files:**
- Create: `apps/ios/PebblesTests/References/SpinePageReferences.swift`

- [ ] **Step 1: Write the suite**

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D5a: the whole fixture week at 393 pt, Paper and Night × English and
    /// French at `.large`, plus `-ax2` (`.accessibility2`, B2's cap) in English
    /// Paper. Android: SpineWeekScreenshots.
    @Suite("Spine page references")
    @MainActor
    struct SpinePageReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func week(scheme: ReferenceScheme) async throws {
            ReferenceImage.assertMatches(try await page(), named: "week",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        @Test
        func weekAx2() async throws {
            ReferenceImage.assertMatches(try await page(), named: "week",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        private func page() async throws -> some View {
            let model = await SpineFixtureWeek.model()
            let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
            return SpineWeekStack(context: SpineFixtureWeek.context(model, page: page))
                .environment(\.spineSnapSource, SpineFixturePhotos.source)
        }
    }
}
```

- [ ] **Step 2: Look locally, then commit**

Run **Look** at `SpinePageReferences`. Expected: the verso, the contents, the days on the raked ground, the mouth, in one column; the `-ax2` page is taller and nothing overlaps or clips. Note each PNG's size (`ls -la apps/ios/PebblesTests/References/__References__/SpinePageReferences`) for the PR body (B2 §Risks asks for it). Clean afterwards.

```bash
git add apps/ios/PebblesTests/References/SpinePageReferences.swift
git commit -m "test(ios): whole spine week references in both schemes and languages"
```

---

### Task D5a.4: Decision log

**Files:**
- Modify: `docs/decisions/log.md` (append only)

- [ ] **Step 1: Append**

Append this at the end of `docs/decisions/log.md`, after one blank line. Replace `<YYYY-MM-DD>` with the day D5a merges (it is dated at merge). Edit no earlier entry.

```markdown
## <YYYY-MM-DD> — The iOS Path becomes the Spine: the existing lit stone in the margin, a SwiftUI paging pager, souls from `path_moments` (#1130)

- **Status:** taken
- **Scope:** ios, ui
- **Context:** Web (#1052) and Android (#1122) draw the Path as the Spine; iOS still drew the row list (`WeekPathView`, `PathPebbleRow`, a Rive cairn roll) inside a `TabView(.page)`. Stacks A (M3 roles), B (`SpineType`, the reference gate, the soft stone) and C (the four tabs, `MomentsRevision`) had landed.
- **Decision:**
  - **D1** The margin stone is the existing lit `PebbleStoneView` at rest light, at 34 / 44 / 54 pt, with an additive `seed:` (`StableHash.seed(pebble.id)`, folded to 24 bits for the shader's Float). Rings, halo, ripple and glint are SwiftUI layers over it. No new renderer, no SVG port.
  - **D4** Each week page is a `ScrollView` with the days in a `LazyVStack`, never a `List`; no view in a day column clips, so an overhanging stone stays pressable. `SpineWeekRows` is the one row order; `SpineWeekStack` lays the same rows out eagerly for the reference harness.
  - **D5** The pager is a horizontal `ScrollView` + `LazyHStack` with `.scrollTargetBehavior(.paging)` and `.scrollPosition(id:)`, bound to `PathModel.focusedWeekStart`, which passed the D4c.5 device spike.
  - **D6** The rake ground is one tiled image behind the days block, in content coordinates, so its phase cannot drift.
  - **D11** Dynamic Type scales text, never geometry; card text through `SpineType`'s `.card(cap: 1.3)`; an entry's anchor is 0.6 × its resolved name size.
  - Souls come from `path_moments` (`PebbleSummary.souls`, tolerant of rows without them), glyphs from `ReferenceDataService`; photo ratios from image headers (`SnapAspectCache`); press requests and card frames are observable state (`StonePressRequests`, `CardFrameRegistry`) for stack E.
- **Why:** The 1:1 rule: iOS reads the same contract and shows the same week. The lit stone already exists on iOS and reads as stone at feed size; `List` clips cells and fights the overhang; `TabView(.page)` keeps ±1 pages live (three weeks of shader layers) in separate controllers; a summed-height ground re-phases after a far jump (Android's lesson).
- **Consequences:**
  - The row Path (`WeekPathView`, `PathPebbleRow`, `PathPebbleSnapThumb`, `WeekHeaderView`, `WeekRollView`, `WeekRollCairnCell`, `pbbls-cairn-states.riv`) is removed in D5b.
  - One generated token joins stack A's Spine table, `rakeLight` (a constant source: white / `#DCE2EA`); the rake shadow and the card's shadow ink are scheme blends in `SpineColors.swift`, as in Android's `SpineColors.kt`. Another scheme-only Spine tone is a row in that table or a blend there, never a literal in `Features/`.
  - The glint's white lives in the exempt `Features/Path/Stone/` folder with the stone's other literal tones.
  - A press opens `PebbleDetailSheet` until stack E's held card consumes `SpineOpen`.
- **Supersedes / Superseded-by:** Extends **2026-10-18 — The Android Path becomes the Spine** (#1122) to iOS, which closes its consequence "iOS still shows the row list; its Spine is a separate issue".
- **Refs:** #1130, #1161, #1122, #1052, `docs/superpowers/specs/2026-10-20-ios-spine-path-design.md`, `docs/superpowers/plans/2026-10-21-ios-spine-path.md`, `apps/ios/Pebbles/Features/Path/Spine/`, `apps/ios/Pebbles/Features/Path/PathModel.swift`.
```

If D4c.5 fell back to `TabView(.page)`, replace the D5 bullet with: "**D5** The pager is a `TabView(.page)` bound to `PathModel.focusedWeekStart`: the paging `ScrollView` failed the D4c.5 device spike (the failing item and the iOS version from the D4c PR). ±1 pages stay live, so the stone budget is measured with three weeks realised."

- [ ] **Step 2: Commit**

```bash
git add docs/decisions/log.md
git commit -m "docs(ios): record the ios spine path decision"
```

---

### Task D5a.5: `apps/ios/CLAUDE.md`

**Files:**
- Modify: `apps/ios/CLAUDE.md`

- [ ] **Step 1: Replace the Path description**

Replace

```
Features map roughly to the web app's navigation structure: Path (home/timeline), Profile (access to Collections, Glyphs, Souls).
```

with

```
Features map roughly to the web app's navigation structure: Path (the Spine: a week roll over a pager of Spine weeks, `Features/Path/Spine/`, its state in `PathModel`), Profile (access to Collections, Glyphs, Souls).
```

If stack C2 already rewrote that sentence on `main`, replace its "Path (…)" clause only, with the same words.

- [ ] **Step 2: Commit**

```bash
git add apps/ios/CLAUDE.md
git commit -m "docs(ios): describe the spine path in the ios agent context"
```

---

### Task D5a.6 (decision 9): Capture one real `path_moments` row with souls, then purge the account

**Files:**
- Modify: `apps/ios/PebblesTests/Moments/MomentFixtures+Captured.swift`
- Modify: `apps/ios/PebblesTests/Spine/PathMomentDecodingTests.swift`

The foreign-payload rule wants the server's own bytes. This runs against the **linked** project with a **throwaway** account the maintainer creates for it (never the maintainer's real account), and ends by purging that account through the existing `delete-account` flow, which runs `purge_account`, empties its storage prefix and deletes its auth user. The orchestrator runs it with the maintainer: the address and the password are typed by the maintainer, never written into the repo, the plan or a PR.

- [ ] **Step 1: Make the account and the row (simulator, Debug build)**

1. Launch the D5a build on the stack's simulator (Task D2a.9 Step 4's `simctl install` / `launch` lines).
2. Sign up with an email address the maintainer controls (no `+`: the app refuses it) and a fresh password. Confirm the address if the project asks for it.
3. People tab: create two souls, "Capture A" and "Capture B" (any glyphs).
4. Capture a moment named "Capture souls" with no photo, complete it into a pebble (any emotion and domain), and add both souls.
5. The Path shows it as a Spine entry naming "CAPTURE A & CAPTURE B". Leave the app signed in.

- [ ] **Step 2: Fetch the row as that user**

Steps 2 to 4 run in **one** terminal session from the repo root: they share its variables, and the session's token must still be fresh for the purge.

```bash
SCRATCH="${TMPDIR:-/tmp}/spine-d5a"; mkdir -p "$SCRATCH"
# xcconfig breaks "//" with "$()", so strip it; neither value is ever echoed.
URL=$(grep -E '^SUPABASE_URL' apps/ios/Config/Secrets.xcconfig | sed -E 's/^[^=]*= *//; s/\$\(\)//g')
ANON=$(grep -E '^SUPABASE_ANON_KEY' apps/ios/Config/Secrets.xcconfig | sed -E 's/^[^=]*= *//')
read -r -p "throwaway email: " EMAIL
read -r -s -p "throwaway password: " PASSWORD; echo
curl -s "$URL/auth/v1/token?grant_type=password" -H "apikey: $ANON" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" > "$SCRATCH/session.json"
JWT=$(python3 -c "import json; print(json.load(open('$SCRATCH/session.json'))['access_token'])")
curl -s "$URL/rest/v1/rpc/path_moments" -H "apikey: $ANON" -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" -d '{}' > "$SCRATCH/path_moments.json"
python3 -c "
import json
rows = json.load(open('$SCRATCH/path_moments.json'))
print(len(rows), [len((r.get('pebble') or {}).get('souls', [])) for r in rows])"
```

Expected: `1 [2]` (one moment, its pebble with two souls). If `session.json` holds an `error`, the password grant was refused: re-check the address is confirmed.

- [ ] **Step 3: Add the body verbatim and a test**

Insert the body as the server sent it, byte for byte, after the existing `pathMoments` fixture (the script splices text; it never reformats the JSON):

```bash
python3 - "$SCRATCH/path_moments.json" <<'PY'
import datetime, sys
body = open(sys.argv[1], encoding="utf-8").read().rstrip("\n")
path = "apps/ios/PebblesTests/Moments/MomentFixtures+Captured.swift"
text = open(path, encoding="utf-8").read()
anchor = '        static let detailCompleted = Data(#"""\n'
assert text.count(anchor) == 1, "anchor"
day = datetime.date.today().isoformat()
block = (
    "        /// `path_moments()` after #1121, captured from the linked project with a\n"
    f"        /// throwaway account on {day} (since purged): one pebble with two souls.\n"
    '        static let pathMomentsWithSouls = Data(#"""\n' + body + '\n"""#.utf8)\n\n'
)
open(path, "w", encoding="utf-8").write(text.replace(anchor, block + anchor))
PY
git diff --stat -- apps/ios/PebblesTests/Moments/MomentFixtures+Captured.swift
```

Expected: one file, about five lines inserted, none removed.

Then append to `struct PathMomentDecodingTests` in `apps/ios/PebblesTests/Spine/PathMomentDecodingTests.swift`:

```swift
    @Test("the captured row carries its souls with names and glyph ids, ordered by name")
    func captured() throws {
        let rows = try decode(MomentFixtures.Captured.pathMomentsWithSouls)
        let souls = try #require(rows.first?.pebble?.souls)
        #expect(souls.map(\.name) == ["Capture A", "Capture B"])
        #expect(souls.allSatisfy { $0.glyphId != nil })
        #expect(rows.first?.soulIds == souls.map { $0.id.uuidString.lowercased() })
    }
```

Run the **Suite command** with `PathMomentDecodingTests`. Expected: PASS (7).

- [ ] **Step 4: Purge the throwaway account**

Still within ten minutes of the sign-in (the function's recent-auth check), call the existing `delete-account` edge function as that user, the same call Settings → Delete account makes:

```bash
curl -s -X POST "$URL/functions/v1/delete-account" -H "apikey: $ANON" -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" -d '{}'; echo
curl -s "$URL/auth/v1/token?grant_type=password" -H "apikey: $ANON" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | python3 -c "import json,sys; print(json.load(sys.stdin).get('error', 'STILL SIGNS IN'))"
rm -f "$SCRATCH/session.json"
unset JWT PASSWORD EMAIL ANON URL
```

Expected: `{"ok":true,"purged":…}`, then `invalid_grant` (the auth user is gone). A `428 {"error":"reauth_required"}` means more than ten minutes passed: sign in again in the app and use Settings → Delete account instead, which re-authenticates and runs the same function. If the call fails after `purge_account` (a 500 naming storage or the auth delete), re-run it: the function is idempotent and resumes. Then, in the simulator, the app returns to the welcome screen on its next launch.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/PebblesTests/Moments/MomentFixtures+Captured.swift apps/ios/PebblesTests/Spine/PathMomentDecodingTests.swift
git commit -m "test(ios): a captured path moments row with its souls"
```

---

### Task D5a.7: Device checklist (iPhone 15; the orchestrator runs it with the maintainer)

**Files:** none.

Install the D5a build on the iPhone 15 (`project_ios_device_deploy.md`). Run every item in Paper and in Night; record pass/fail per item in the PR. Any failure is fixed on this branch before the PR leaves draft.

- [ ] 1. EN and FR (switch the app's language in Settings).
- [ ] 2. A real week with photos of three ratios and a 14-soul pebble: cards at their own shapes, square first then easing; the people line folds "& twelve others" with five marks and "+7".
- [ ] 3. A press on the stone (verso, after the dip), on the card and on an entry (at once), and on a contents row (scrolls, centres, presses, no sheet), each with and without Reduce Motion.
- [ ] 4. The largest Dynamic Type on the device (the references stop at `.accessibility2`): names wrap, the stone stays on the first line, nothing clips.
- [ ] 5. VoiceOver over a stone, a card (its "Delete" action), the contents (the hint) and a day-dot row ("On four days: …"); a page change announces the week.
- [ ] 6. Paging by swipe, by the roll and by both links; each link names the week it lands on (skip an empty week to prove it).
- [ ] 7. Low Power Mode: the flat stone, no shader.
- [ ] 8. Scroll a 20-pebble week (the lab's dense week if no real one) in Instruments: 60 fps, the first page within 2 frames.
- [ ] 9. Capture a pebble on another week: the Path refocuses on its week. Edit one from People › soul: Path shows it without a pull. Delete one from the Path: the dialog, then it is gone.
- [ ] 10. Reselect the Path tab on an old week: the current week comes back, scrolled to its masthead.

---

### Task D5a.8: D5a verification, PR and CI references

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**. Expected: green apart from the new `SpinePageReferences`, which CI records.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <D5a-number> --title "feat(ios): the path becomes the spine" --body-file <scratch-body-file> \
  --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Resolves #1130` (decision 5); what changes for a user (the Path tab is the Spine); key files; the captured `path_moments` row and the throwaway account's purge (D5a.6, no address or id in the body); the device checklist table; the PNG sizes; the decision-log entry; the `apps/ios/CLAUDE.md` line; **new pattern for review:** `PathModel` adopted by `PathView`; follow-ups: "Couldn't load your pebbles." is still an English-only line (unchanged), stack E consumes `SpineOpen`. Arkaik: nothing to set; the App marks the four nodes `releasing` at merge (never `live`). No Lab Note, no `F-…` ids.

- [ ] **Step 3: Rebaseline on CI**

Run **Rebaseline on CI**. Compare `week-paper-en.png` with Android's `SpineWeekVersoLight` + `SpineWeekDaysDark` pair and the web `/sandbox/design` Spine page. Green after the approved re-run.

---
## Part D5b — `chore/1130-ios-remove-row-path`

PR title: `chore(ios): remove the row path`. Body opens `Part of #1130`. Labels `chore`, `ui`, `ios`, `no-lab-note` (the spec's parts table).

### Task D5b.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add chore/1130-ios-remove-row-path
git branch --show-current
```

Expected: `chore/1130-ios-remove-row-path`.

---

### Task D5b.1: Collect the strings only the old views use

**Files:** none yet.

- [ ] **Step 1: List the candidates before deleting**

```bash
SCRATCH="${TMPDIR:-/tmp}/spine-d5b"; mkdir -p "$SCRATCH"
OLD="apps/ios/Pebbles/Features/Path/Components/WeekPathView.swift apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift apps/ios/Pebbles/Features/Path/Components/PathPebbleSnapThumb.swift apps/ios/Pebbles/Features/Path/Components/WeekHeaderView.swift apps/ios/Pebbles/Features/Path/Components/WeekRollView.swift apps/ios/Pebbles/Features/Path/Components/WeekRollCairnCell.swift apps/ios/Pebbles/Features/Path/GroupPebblesByISOWeek.swift"
grep -ohE '"[^"\\]+"' $OLD | sort -u > "$SCRATCH/literals.txt"
wc -l < "$SCRATCH/literals.txt"
```

The literals that are catalogue keys are judged after the deletion (Task D5b.3), because only then is "used nowhere else" true.

---

### Task D5b.2: Delete the row Path and its tests

**Files:**
- Delete: `apps/ios/Pebbles/Features/Path/Components/WeekPathView.swift`, `PathPebbleRow.swift`, `PathPebbleSnapThumb.swift`, `WeekHeaderView.swift`, `WeekRollView.swift`, `WeekRollCairnCell.swift`
- Delete: `apps/ios/Pebbles/Features/Path/GroupPebblesByISOWeek.swift`
- Delete: `apps/ios/Pebbles/Resources/pbbls-cairn-states.riv`
- Delete: `apps/ios/PebblesTests/PathPebbleRowGeometryTests.swift`, `PathPebbleRowNameColorTests.swift`, `WeekHeaderFormatTests.swift`, `WeekRollViewMarginTests.swift`, `GroupPebblesByISOWeekTests.swift`
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift`

- [ ] **Step 1: Confirm nothing else uses them**

```bash
for name in WeekPathView PathPebbleRow PathPebbleSnapThumb WeekHeaderView WeekRollView WeekRollCairnCell GroupPebblesByISOWeek "pbbls-cairn-states"; do
  echo "== $name"; grep -rln "$name" apps/ios/Pebbles apps/ios/PebblesTests apps/ios/PebblesWidget apps/ios/Shared apps/ios/project.yml 2>/dev/null
done
```

Expected: each name is found only in its own file, its own test, and (for `WeekPathView`) `ThemeLiteralsTests.swift`. `packages/rive` keeps its own copy of the `.riv`. If any other file lists one, stop and report it.

- [ ] **Step 2: Delete**

```bash
git rm -q apps/ios/Pebbles/Features/Path/Components/WeekPathView.swift \
  apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift \
  apps/ios/Pebbles/Features/Path/Components/PathPebbleSnapThumb.swift \
  apps/ios/Pebbles/Features/Path/Components/WeekHeaderView.swift \
  apps/ios/Pebbles/Features/Path/Components/WeekRollView.swift \
  apps/ios/Pebbles/Features/Path/Components/WeekRollCairnCell.swift \
  apps/ios/Pebbles/Features/Path/GroupPebblesByISOWeek.swift \
  apps/ios/Pebbles/Resources/pbbls-cairn-states.riv \
  apps/ios/PebblesTests/PathPebbleRowGeometryTests.swift \
  apps/ios/PebblesTests/PathPebbleRowNameColorTests.swift \
  apps/ios/PebblesTests/WeekHeaderFormatTests.swift \
  apps/ios/PebblesTests/WeekRollViewMarginTests.swift \
  apps/ios/PebblesTests/GroupPebblesByISOWeekTests.swift
```

- [ ] **Step 3: Drop the stale literal allowlist entry**

In `apps/ios/PebblesTests/ThemeLiteralsTests.swift`, delete the line

```swift
        "Features/Path/Components/WeekPathView.swift": (2, "fade mask stops (alpha only)"),
```

(The test fails on a stale entry, so it must go with the file.)

- [ ] **Step 4: Build and test**

Run **Build** and the **Suite command** with `ThemeLiteralsTests`. Expected: `** BUILD SUCCEEDED **`, PASS.

- [ ] **Step 5: Commit**

```bash
git add -A apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "chore(ios): remove the row path, its cairn and their tests"
```

---

### Task D5b.3: Remove the strings no view uses any more

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 1: Find the orphans among the old literals**

```bash
SCRATCH="${TMPDIR:-/tmp}/spine-d5b"
python3 - "$SCRATCH/literals.txt" <<'PY' > "$SCRATCH/orphans.txt"
import json, re, subprocess, sys
literals = [l.strip()[1:-1] for l in open(sys.argv[1]) if l.strip()]
catalogue = json.load(open("apps/ios/Pebbles/Resources/Localizable.xcstrings"))["strings"]
for literal in literals:
    # An interpolated literal ("Week \(n)") is the catalogue's "%lld" form.
    key = re.sub(r"\\\([^)]*\)", "%lld", literal)
    if key not in catalogue:
        continue
    used = subprocess.run(["grep", "-rlF", literal, "apps/ios/Pebbles", "apps/ios/PebblesWidget", "apps/ios/Shared"],
                          capture_output=True, text=True).stdout.strip()
    if not used:
        print(key)
PY
cat "$SCRATCH/orphans.txt"
```

The script reads the catalogue only; it never writes it. Review the list by hand: a key built at runtime (`emotion.\(slug).name`) or used through `LocalizedStringResource` in a model can look unused. Keep any key you can find in a `grep -rn` of the sources under another spelling. `Fresh week`, `Collect a new pebble`, `New pebble` and `Week %lld, %lld pebbles` must **not** be on the final list (the Spine uses them).

- [ ] **Step 2: Delete the reviewed keys as text**

```bash
SCRATCH="${TMPDIR:-/tmp}/spine-d5b"
python3 - "$SCRATCH/orphans.txt" <<'PY'
import sys
keys = [l.rstrip("\n") for l in open(sys.argv[1]) if l.strip()]
path = "apps/ios/Pebbles/Resources/Localizable.xcstrings"
lines = open(path, encoding="utf-8").read().split("\n")
out, skip = [], False
import json
for line in lines:
    if not skip and any(line == "    " + json.dumps(k, ensure_ascii=False) + " : {" for k in keys):
        skip = True
        continue
    if skip:
        if line in ("    },", "    }"):
            skip = False
        continue
    out.append(line)
open(path, "w", encoding="utf-8").write("\n".join(out))
PY
git diff --stat -- apps/ios/Pebbles/Resources/Localizable.xcstrings
python3 -c "import json; json.load(open('apps/ios/Pebbles/Resources/Localizable.xcstrings')); print('valid json')"
```

Expected: only deletions; `valid json`. If the deleted block was the catalogue's last entry, the previous entry now ends in `},` before the closing `}`: fix that one comma by hand and re-check. Then open the catalogue in Xcode: no entry is `Stale` (a stale entry is a key a view still uses that this step deleted: restore it with `git checkout -p`).

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "chore(ios): drop the strings only the row path used"
```

---

### Task D5b.4: D5b verification and PR

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**. Then:

```bash
grep -rnE "WeekPathView|PathPebbleRow|PathPebbleSnapThumb|WeekHeaderView|WeekRollView|WeekRollCairnCell|GroupPebblesByISOWeek|pbbls-cairn-states" apps/ios || echo "no reference to a removed type"
```

Expected: `no reference to a removed type`. `WeekRollBuilder`, `PebbleStoneView`, `Components/PebbleRow.swift`, `GroupPebblesByMonth`, `PebbleDetailSheet` and `Read/` stay.

- [ ] **Step 2: Push, submit, metadata**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
gh pr edit <D5b-number> --title "chore(ios): remove the row path" --body-file <scratch-body-file> \
  --add-label chore,ui,ios,no-lab-note --milestone "M64 · New Path"
```

Body: `Part of #1130`; the removal list and its replacements (spec §Removal); the five tests; the orphaned keys removed; the literal allowlist entry. No Lab Note, no `F-…` ids.

- [ ] **Step 3: Merge the stack**

Only once every part is green and D5a's checklist passed: `gh stack rebase`, wait for CI on the rebased heads (approve any held runs; a rebaseline bot commit breaks linearity, so rebase and re-run before merging, per the stack memory notes), then `gh stack merge --yes` from the bottom.

---
## Self-review notes (done while writing; re-run after the maintainer's decisions)

### Spec coverage

| Spec section | Tasks |
|---|---|
| Summary, Goal (Android parity for the fixture week in Paper/Night, EN/FR; vectors verbatim; Arkaik) | D5a.3, the reference suites of D2a–D4; D1 vector files; D1.0 Step 5 |
| Non-goals | Kept out: no new renderer (D2a.5 adds only `seed:`), no tilt or sweep on the feed (`PebbleStoneView` at rest light), no spread, no grain, no dock, no stored dimensions, no held card, no change to Capture, Complete, Edit, delete, `PebbleDetailSheet` or the soul and collection lists, no Swift 6, no `#available` |
| D1 margin stone | D2a.5 (`seed:`), D2a.8 (`SpineStone`), D2a.4 (sizes) |
| D2 souls from `path_moments` | D1.1, D3.2 (names merged from the pebbles), D5a.2 (`syncSouls`), D5a.6 (the captured row, then the account purged; decision 9) |
| D3 photo ratio on device | D2b.3, D2b.8 (square then 240 ms ease, snap under Reduce Motion), D4c.3 (prefetch of the week and its neighbours), D5a.1 |
| D4 `ScrollView` + `LazyVStack`, no clipping, standalone rows | D3c.4 (`SpineWeekRows`, `SpineWeekStack`), D4c.2 (`SpineWeekView`), D3c.3 (no clip in a row) |
| D5 paging `ScrollView` | D4c.3, D4c.5 (spike and fallback) |
| D6 ground once, in content coordinates | D3.3–D3.4 |
| D7 Android's form choices | D4.2 / D4.4 (masthead and mouth carry the links; no date header), D5a.2 (stack C's FAB row) |
| D8 press request as state | D2a.2, D3c.3 (claim), D4c.2 (go) |
| D9 links name the pager's neighbour | D3.1–D3.2 (`WeekNeighbour`, `WeekNeighbourTests`), D4.2, D4.4 |
| D10 explicit time and language; lowercase string ids | D1 (every function takes `calendar` and `locale`), D1.1 (`spineId`), D2b.6 (`SpineCalendar`) |
| D11 Dynamic Type scales text, not geometry | D3c.3 (anchor 0.6 × resolved size), D2b.8 (card text through `.card(cap: 1.3)`), D3.5 (still day from resolved metrics), `-ax2` references in D3.6, D4.6, D5a.3 |
| D12 reading day by preference | D4c.1 (seams and pebble rows report, decision 2), D3.2 (`setReadingDay` writes on change only) |
| R1 page geometry | D2a.4 |
| R2 stone sizes, anchor, hit area ≥ 44 | D2a.4, D3c.3 |
| R3 rings and halo | D2a.3, D2a.6 |
| R4 card sizing | D1.10, D2b.8 |
| R5 card anatomy, edge, tint and shadow | D2b.2 (edge, spot, shadow ink and `shadowAlpha`), D2b.7, D2b.8 |
| R6 no-photo entry | D1.9, D3c.2 |
| R7 many people | D1.8, D2b.5, D2b.6, D3c.2 |
| R8 seam | D3.5 |
| R9 day label | D3.5 |
| R10 empty day | D3.5, D3c.4 (48 hairline to hairline, the last day's 48 to the mouth) |
| R11 ground | D3.3, D3.4, D2b.1 (`rakeLight`), D2b.2 (`rakeShadow`) |
| R12 press | D2a.1, D2a.6–D2a.8, D3c.3 |
| R13 reduced motion | D2a.1 (still touch), D2a.6 (rings in ink-2), D3c.3 (open at once), D4c.2–D4c.3 (instant scroll and paging), D2b.8 (ratio snaps) |
| R14 ripple mounted only while live | D2a.8 (`waves` empty at rest), D2a.2 (`stonePressLifetime`) |
| R15 persons of the week | D1.3, D4.3 |
| R16 contents | D1.9, D4.9 |
| R17 seal recipe | D4.7, D4.8 |
| R18 opening type | D1.7, D4.1, D4.2 |
| §Data flow (`PathModel`, registry ownership, reload, source, palettes, photos) | D3.2, D5a.1, D5a.2, D1.1, D2b.4 |
| §Logic API | D1.1–D1.4, D1.5–D1.10 |
| §Strings (keys, plurals, collision check, parity) | D1c.1, D1c.2 (catalogue test), D1c.4 |
| §Vector mapping table | `SpineWeekBuilderTests`, `LocalDaysTests`, `PersonsOfWeekTests` (D1); `PeopleLineTests`, `EntryTypeTests`, `CardSizeTests`, `WeekOpeningTests`, `SpineDatesTests`, `NumberWordsTests` (D1); `StableHashTests` (D1.4); `PathMomentDecodingTests` (D1.1) |
| §View tree | Every file is created in the task the file map names; `WeekOpening.swift` (view) becomes `WeekOpeningView.swift` (deviation 3) |
| §Geometry | D2a.4, D3.5, D3c.3–D3c.4, D2b.8, D4.3, D4.9 |
| §Pager semantics (one source of truth, links, load, reselect, page change) | D3.2 (`focus`, refocus, cancel on page change), D4c.3, D5a.2 (reselect) |
| §Press-request state | D2a.2, D3c.3, D4c.2 |
| §Frame registry for stack E (reporting, entries, unobserved frames, ownership, focus return) | D2b.4, D2b.8, D3c.3, D5a.1, D4c.2 |
| §Removal (D5b) | D5b.1–D5b.4 |
| §Accessibility A1–A8, order, Reduce Motion, focus return | A1 D3.5 / D4.2; A2 D3c.1 / D3c.3; A3 D3c.3; A4 D4.9; A5 D4.3; A6 D4c.3; A7 D1.9; A8 D3c.2 / D4.9; order D3c.3 (`accessibilitySortPriority`); focus return D4c.2 |
| §Testing table | D1–D1c; D2a (`StoneMotionTests`, `StonePressRequestsTests`) + D2a.10; D2b (`SnapAspectCacheTests`, `CardFrameRegistryTests`) + D2b.10; D3c.5 + the budget in D3c.6; D4c.1 (`ReadingDayTests`), D3.2 (`WeekNeighbourTests`), D4.6; D5a.3; D5b.4 |
| §Device checklist | D5a.7 (and the per-part checks in D2a.11, D2b.11, D3c.6, D4c.5) |
| §Parts, Arkaik, decision log | Parts table, decision 3; D1.0 Step 5; D5a.4 |
| §New patterns, raised in the PR | `StonePressRequests` (D2a.11), `SnapAspectCache` and `SpineSnapSource` (D2b.11), `PathModel` (D3.7, D5a.8), the paging `ScrollView` (D4c.6) |
| §Risks | Paged `ScrollView`: D4c.5 with the `TabView(.page)` fallback; `ImageRenderer`: `SpineSnapSource`, `SpineWeekStack`, `frozenAt`, `QuarterTurn`, B2's window fallback named in D2a.10; shader stones in a lazy list: D3c.6 (budget, `.drawingGroup()`); frames while paging: stack E reads at the press (stated in `CardFrameRegistry`'s doc); small caps: B's; preference churn: D3.2 and D4c.1 |

### Deviations to surface in the PR bodies

1. Ten parts: the brief's seven plus D1c (strings and fixture), D3c (feed) and D4c (pager) kept apart (decision 3), and the moves of decision 4.
2. Branch names follow the brief (`feat/1130-ios-<slug>`), not the spec's table (`feat/1130-spine-logic`, …).
3. The opening view is `WeekOpeningView`: the logic's `WeekOpening` enum owns the name.
4. `path.spine.summaryNoPebbles` is its own key (decision 8); plurals are xcstrings substitutions on argument 1 (French `many` = `other`); 33 keys.
5. One generated token in stack A's table, `rakeLight`, from constant hexes (the generator gains a constant-hex source), through `apps/web/lib/theme/ios-colorsets.ts`; the rake shadow and the shadow ink are blends in `SpineColors.swift`, black being `Color.pebbles.scrim` (decision 1).
6. The glint's white lives in `Features/Path/Stone/StoneGlintInk.swift` (decision 6).
7. Pebble rows report the reading day as seams do, as on Android (decision 2; the spec's D12 names seams only).
8. `PathModel.page(for:)` returns an optional, `focusedWeekStart` is `private(set)` and moves through `focus(_:)`, and `SpinePage` also carries `soulNames`.
9. The card's shadow uses Android's ink and `shadowAlpha` in two layers, each a share of that alpha, because SwiftUI has no spread; Night's deep seal ink is `secondary` at 0.8 over the ground (no colour mix on iOS 17).
10. People-line runs take their ranks through `Text.spineSpan` (the same `SpineType.resolve` the modifier uses), and `SpineType.Style.peopleAside` is the `people` rank in italic.
11. Vectors not ported: `number-words`' 2.5 case (an `Int` API); `entry-type`'s and `card-size`'s wide rows; `card-size`'s cap and pending cases run at the phone caps.
12. The shader seed is folded to 24 bits (the shader reads a Float).
13. The lab's fixture cards show their photo bed (decision 7).
14. "Couldn't load your pebbles." stays English-only, unchanged from today.
15. The stone's VoiceOver name lists souls with `ListFormatter` (a sentence for the reader), while `dotDays` hand-joins (its vector pins the web's output).
16. No captured `path_moments` row with souls existed; D5a.6 captures one with a throwaway account and purges it through `delete-account` (decision 9).
17. D5b is labelled `chore` (the spec's table) where the brief lists `feat`.
18. `CardFace` gains `other` for stack E's turn.
19. The brief's ring-fit and lockup vectors are stack E's (decision 10).
20. A still day is 48 pt hairline to hairline (the last day 48 to the mouth), grown to its label's resolved height.

### Placeholder scan

Searched the plan for "TBD", "TODO", "implement later", "fill in", "similar to Task", "appropriate error handling", "handle edge cases" and bracketed angle placeholders. The only angle brackets left are the PR numbers `gh stack view --json` prints (`<D1-number>` … `<D5b-number>`), the scratch body path every PR task writes (`<scratch-body-file>`), the merge date in the decision-log entry (`<YYYY-MM-DD>`, filled at merge, as stack C's plan does) and `<Suite>` in the named commands. Every code step shows its code; every test step shows the test and its command.

### Type consistency

Checked across tasks: `SpineMoment` (`spineId`, `happenedAt`, `isPebble`, `soulIds`); `SpineWeekBuilder.spineWeek(weekStart:moments:today:calendar:)`, `readingOrder(_:)`; `SpineDay` (`date`, `index`, `moments`, `isStill`, `isToday`); `PersonsOfWeek.personsOfWeek(_:names:weekStart:calendar:locale:)`, `split(_:cap:)`; `PeopleLines.peopleLine(_:order:locale:namedInFull:)`, `namedOnCard`; `EntryTypes.entryType(intensity:valence:)` → `EntryType(name:lineHeight:anchor:)`; `CardSizes.cardSize(intensity:ratio:column:)`; `WeekOpening.weekWords(weekStart:calendar:locale:)`; `SpineCopy.*`; `StoneMotion.frame(at:still:size:)`, `total(still:)`, `openDelay`, `goPressDelay`; `StonePress.next(after:still:now:)`, `.stonePressLifetime(_:)`; `StonePressRequests.request(_:)` / `claim(_:)` / `cancel()`; `StoneRingGeometry.forStone(_:)`, `path(scaled:offset:)`; `SpineLayout.stoneRow(anchor:stone:)` → `StoneRow(stone:hit:minHeight:)`; `SpineColors.palette(_:isBlank:)`, `edge`, `spot`, `deep`, `rakeShadowSource` / `rakeShadow`, `shadowInkRole` / `shadowInk`, `shadowAlpha` (the rake tile and the card's ground call these, and no file names `Color.pebbles.rakeShadow` or `cardShadow`); `SnapAspectCache(urls:load:timeout:)`, `probe`, `prime`, `prefetch`, `ratio(for:)`; `CardFrameRegistry.report(_:frame:)` / `forget(_:)` / `hidden`; `SpineSnapSource.signed(_:)` / `.images(_:)`; `PeopleLineParts.parts(_:names:with:others:style:)`; `SpineCard(moment:palette:ratio:column:soulNames:soulGlyphs:soulOrder:namedInFull:)`; `PathModel.load(_:)`, `reloadIfChangedOutside(_:_:)`, `updateSouls(names:glyphs:)`, `focus(_:)`, `focusWeek(containing:)`, `focusCurrentWeek()`, `setReadingDay(_:for:)`, `page(for:)`, `photoPaths(around:)`, `delete(_:)`; `SpineFeedContext` (memberwise order `page, column, palette, ratio, pressRequests, onOpen, onDelete, onFocus, onCreate, onGo, readingDay`); `SpineRowView(row:context:slotFocus:markSpace:)`; `SpineWeekStack(context:days:part:width:)`; `SpineOpen(momentId:face:summary:soulOrder:)`; `SpineWeekView` and `SpinePager` initialisers as D5a calls them.
