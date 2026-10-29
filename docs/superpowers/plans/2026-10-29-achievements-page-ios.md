# Achievements page — iOS implementation plan (#1306)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** replace the You → Achievements grid with the approved page (a header and five cards) and its five sheets, drawn entirely from `get_achievements`, with the seen marks, the ceremony pull and a celebration that only shows what the server says to celebrate.

**Architecture:**
- **Data (part 1):** `AchievementsPayload` decodes the server's jsonb tolerantly, `AchievementsMapping` maps it to `AchievementsPage` (Swift types, enum fallbacks, nothing computed), `AchievementsPageService` calls the three RPCs and keeps the last body in the wraps' disk cache, `AchievementsModel` runs stale-while-revalidate, the seen marks and the pull. `AchievementsCopy` turns semantic values into EN/FR sentences. `check_achievements()`' `celebrate` filters the unlock moment.
- **Figures (part 2):** pure geometry enums (wheel, rosace, band, skyline, year disc), each ported from the board's JS and unit-tested, drawn by `Canvas` views on the Spine's flat seal and the app's colour roles, so Paper and Night come from the scheme.
- **Page and sheets (parts 3–4):** the card shell (margin ladder, band, figure), the header, five cards, then five sheets of 44 pt register rows built by pure row builders. Explorer's Domains and Emotions rows present their own sheet on top. Rows open their pebble through the held card after the sheets close.
- **Contract replay (part 5):** once #1305 parts 2–3 are merged, the decoding tests replay the harness's verbatim dumps.

**Tech Stack:** SwiftUI (iOS 17), `@Observable`, Swift Testing, supabase-swift RPCs, `Localizable.xcstrings`, XcodeGen, the iOS reference-image gate (`PebblesTests/References`, recorded on CI only).

**Sources:**
- Spec: `docs/superpowers/specs/2026-10-29-achievements-page-design.md` (§4 is the client rule: decode, draw, localise, resilience).
- Contract: `docs/superpowers/plans/2026-10-29-achievements-page-backend.md`: `get_achievements` (Task 9), `check_achievements`' `celebrate` (Task 3), `mark_achievements_seen` and `pull_ceremony` (Task 12), the dumps (Task 10 step 4, Task 14).
- Design: the approved boards (`final/index.html`, `final/sheets.html`, `NOTES.md`; drawing code in `final/_build/app.js` and `sheets.js`). Only the geometry is ported; every number, order, level, family and flag comes from the payload.

**Stack:** five branches with `gh stack`, one PR each, in this order. Each part builds, lints and passes its tests alone.

| Part | Branch | Story |
|---|---|---|
| 1 | `feat/1306-ios-achievements-data` | decode, map, copy, cache, model, celebration filter |
| 2 | `feat/1306-ios-achievements-figures` | the five figures and the flat seal, Paper and Night |
| 3 | `feat/1306-ios-achievements-page` | the header, the five cards, accessibility, the ceremony pull; replaces the grid |
| 4 | `feat/1306-ios-achievements-sheets` | the five sheets, seen marks, pebble rows, Explorer's pushes |
| 5 | `test/1306-ios-achievements-payloads` | replay the harness dumps (after #1305 parts 2–3 merge) |

**Ground rules for every task:**
- **Swift Testing only** (`@Suite`, `@Test`, `#expect`, `#require`), never XCTest.
- **Colours are `Color.pebbles.<role>` only** in feature code, or a palette from `v_emotions_with_palette` through `SpineColors`. `ThemeLiteralsTests` fails on anything else (no `.black`, `.white`, `.gray`, `Color(red:)`).
- **Strings live in `Pebbles/Resources/Localizable.xcstrings`, EN and FR.** Add them only with `apps/ios/build/xcstrings-add.py` (Task 1): the catalog is formatting-sensitive, so it is edited as text at an anchor, never re-serialised. Copy is read through `SpineStrings` with an explicit locale, so the tests pin `en` and `fr`.
- **XcodeGen is the project.** New files and `project.yml` edits need `xcodegen generate`; `apps/ios/build/ach-test.sh` (Task 1) regenerates and, when the project changed, clears this worktree's build products, because a build right after xcodegen can serve stale objects.
- **One dedicated simulator and a private derived-data folder for this worktree** (Task 1). Never the shared "iPhone 17", never the global `DerivedData`.
- **References are rendered on CI only.** A new reference test fails locally with "no reference at …"; that is expected. Each part that adds one gets the `rebaseline-ios-screenshots` label on its PR, and the bot commits the PNGs. Locally the gate is: the References suite fails on the baseline's list (Task 1) plus the new tests' "no reference" lines, nothing else. Never iterate a `Set` or a `Dictionary` while drawing a figure (the second recording must be byte-identical).
- **Never compare a count with a threshold, never sort, never pick a family.** If a view needs a number, an order, a level or a flag the payload does not carry, stop and raise it on #1305 rather than computing it. The two places this plan draws a number the payload lacks are named in "Contract gaps" at the end.
- **Commits:** conventional, lowercase, no period, ending with the attribution trailer the session gives.

**Commands used throughout** (all from the repo root):
- One suite: `apps/ios/build/ach-test.sh <SuiteStructName>`. Expected on green: `** TEST SUCCEEDED **`.
- Every unit test but the references: `apps/ios/build/ach-test.sh "" References`.
- The references: `apps/ios/build/ach-test.sh References`.
- Lint: `npm run lint --workspace=@pbbls/ios`. Expected: no `error:` line (warnings are not a gate).


> **Contract resolutions (2026-10-29, after the three client plans):** the back-end plan now serves what the client planners found missing. Where this plan hard-codes or tolerates one of these, switch to the served value:
> - **No explicit nulls:** `explorer.first_five` and `six_firsts` are `[]` when empty, and `twenty_five` is omitted when it can't be built.
> - **The year serves `days_target` (14) and per-month `done: {moments, pebbles, weeks}`.** Draw the done style from `done`; never compare a count with 14.
> - **Every dated row carries `on`,** by one rule: the local day its pebble happened, or the recorded day when that pebble is gone. That covers seats, domain entries, the six firsts, the 25th and ladder levels.
> - **`emotions.families`** gives the sheet's order: families in wheel order, each with its met seats by the day met and its `not_yet` seats.
> - **`since`** on `emotions` and on each ladder (pebbles, glyphs carved, glyph sales) is the card's "since" date.
> - **`seen`** is a top-level map of card → last-look time, at whole seconds.
> - **A completion carries its `pebble`.**
> - **Celebration:** `pebble-count-1` never celebrates, because `first-pebble` celebrates that pebble once. A heavy first pebble celebrates neither.
> - **No year "new" marks** in v1.
> - **The dumps are verbatim response bodies,** and a keyed run adds `get-achievements-pulled.json` (a pulled completion).
> - **The Profile shelf is served** (D18, the maintainer's call on 29 October). `get_achievements` returns `shelf` (up to six `{slug, family, threshold, unlocked_on}`, private families already removed) and `shelf_count`. Point the Profile shelf at it instead of reading `achievement_unlocks`, and keep resolving titles and glyphs from the cached catalog by slug.
> - **The ceremony reuses the existing unlock moment** (maintainer, 29 October).
> - **Lab Note:** the page parts carry one (maintainer, 29 October).

---

## File map

All paths are under `apps/ios/` unless they start with `docs/` or `packages/`.

| File | Part | Responsibility |
|---|---|---|
| `project.yml` | 1 | exclude `PebblesTests/Achievements/Fixtures/**` from the test bundle (read through `#filePath`) |
| `Pebbles/Features/Achievements/Data/AchievementsPayload.swift` | 1 | the wire shape of `get_achievements`, tolerant decoding, dropped sections named |
| `Pebbles/Features/Achievements/Data/AchievementsPage.swift` | 1 | the display model the views draw |
| `Pebbles/Features/Achievements/Data/AchievementsMapping.swift` | 1 | payload → page, enum fallbacks, clamps |
| `Pebbles/Features/Achievements/Data/AchievementsPageService.swift` | 1 | the three RPCs, the kept copy, the pull outcome |
| `Pebbles/Features/Achievements/Data/AchievementsModel.swift` | 1 | stale-while-revalidate, seen marks, the pull |
| `Pebbles/Features/Achievements/Copy/AchievementsCopy.swift` | 1 | header, cards, coverage, pebbles line, next step, ceremony |
| `Pebbles/Features/Path/Spine/Logic/SpineDates.swift` | 1 | + `fullDate`, `shortDayMonth` |
| `Pebbles/Services/AchievementsService.swift` | 1 | `AchievementCheckResult.celebrate`, the filter in `fireCheck` |
| `Pebbles/Features/Profile/Models/Achievement+Localized.swift` | 1 | shelf copy and symbols for `first_stone` and `set_complete` |
| `Pebbles/Features/Achievements/Figures/AchievementsType.swift` | 2 | the page's type ranks on `SpineType.Style` |
| `Pebbles/Features/Achievements/Figures/AchievementInks.swift` | 2 | the boards' inks on the app's roles, family hues |
| `Pebbles/Features/Achievements/Figures/FlatSeal.swift` | 2 | the flat seal's four prints, ghosts, new marks, `Path.polygon` |
| `Pebbles/Features/Achievements/Figures/FirstFiveMarks.swift` | 2 | each first-five task drawn as the part it makes |
| `Pebbles/Features/Achievements/Figures/FamilyRun.swift` | 2 | runs of seats of one family |
| `Pebbles/Features/Achievements/Figures/ExplorerWheelGeometry.swift`, `ExplorerWheel.swift` | 2 | the wheel |
| `Pebbles/Features/Achievements/Figures/RosaceGeometry.swift`, `EmotionRosace.swift` | 2 | the rosace |
| `Pebbles/Features/Achievements/Figures/ShardBandGeometry.swift`, `DomainBand.swift` | 2 | the mended band |
| `Pebbles/Features/Achievements/Figures/SkylineGeometry.swift`, `PebbleSkyline.swift` | 2 | the skyline and a stage alone |
| `Pebbles/Features/Achievements/Figures/YearDiscGeometry.swift`, `YearDisc.swift` | 2 | the docked year disc and a month's counter |
| `Pebbles/Features/Achievements/Page/AchievementsA11y.swift` | 3 | VoiceOver labels assembled from the payload |
| `Pebbles/Features/Achievements/Page/MarginLadder.swift` | 3 | the margin ladders' fills and their view |
| `Pebbles/Features/Achievements/Page/AchievementCardShell.swift` | 3 | the card shell, band, vertical title, ready mark |
| `Pebbles/Features/Achievements/Page/AchievementsHeader.swift` | 3 | since / days kept |
| `Pebbles/Features/Achievements/Page/AchievementsCards.swift` | 3 | the five cards |
| `Pebbles/Features/Achievements/Page/AchievementsPageView.swift` | 3, 4 | the scroll, the cards, the sheets |
| `Pebbles/Features/Profile/AchievementsView.swift` | 3 | the route's screen: model wiring, check then read, error state (the grid goes) |
| `Pebbles/Features/Achievements/Copy/AchievementsCopy+Sheets.swift` | 4 | the sheets' words |
| `Pebbles/Features/Achievements/Sheets/AchievementsSheetRows.swift` | 4 | pure row builders for the five sheets |
| `Pebbles/Features/Achievements/Sheets/SheetParts.swift` | 4 | section head, register row, row marks, ghost names |
| `Pebbles/Features/Achievements/Sheets/AchievementSheets.swift` | 4 | the five sheets and their routing |
| `PebblesTests/Achievements/…` | 1–5 | fixtures, fakes and the unit suites |
| `PebblesTests/References/Achievements*References.swift` | 2–4 | the figures, the page and the sheets, Paper and Night |

---

# Part 1 — the data (branch `feat/1306-ios-achievements-data`)

### Task 1: Worktree, simulator, scripts, Arkaik status, baselines

**Files:**
- Create (gitignored, never committed): `apps/ios/build/achievements.env`, `apps/ios/build/ach-test.sh`, `apps/ios/build/xcstrings-add.py`, `apps/ios/build/references-baseline.txt`

- [ ] **Step 1: Create the worktree from an updated main and start the stack**

```bash
git fetch origin
git worktree add .worktrees/ios-achievements -b feat/1306-ios-achievements-data origin/main
cd .worktrees/ios-achievements
gh stack init feat/1306-ios-achievements-data
cp /Users/alexis/code/pbbls/apps/ios/Config/Secrets.xcconfig apps/ios/Config/Secrets.xcconfig
```

Every later command runs from this worktree's root. `Secrets.xcconfig` is gitignored; without it `xcodegen generate` fails spec validation in a worktree.

- [ ] **Step 2: Give the worktree its own simulator and derived data**

```bash
mkdir -p apps/ios/build
SIM=$(xcrun simctl create "iPhone 17 achievements" "iPhone 17")
printf 'export ACH_SIM=%s\nexport ACH_DD=%s\n' "$SIM" "$PWD/apps/ios/build/dd" > apps/ios/build/achievements.env
cat apps/ios/build/achievements.env
```

Expected: two `export` lines, the first a UUID.

- [ ] **Step 3: Write the test runner**

Create `apps/ios/build/ach-test.sh`:

```sh
#!/bin/sh
# Runs the iOS tests on this worktree's own simulator (#1306).
#   ach-test.sh <Suite>            one Swift Testing suite (its struct name)
#   ach-test.sh "" <SkippedSuite>  everything but one suite
# Regenerates the project; when that changed it, clears this worktree's build
# products first, because a build right after xcodegen can serve stale objects.
set -e
cd "$(dirname "$0")/.."
. build/achievements.env
before=$(stat -f %m Pebbles.xcodeproj/project.pbxproj 2>/dev/null || echo 0)
xcodegen generate --use-cache --quiet
after=$(stat -f %m Pebbles.xcodeproj/project.pbxproj)
[ "$before" = "$after" ] || rm -rf "$ACH_DD/Build"
set +e
xcodebuild test -scheme Pebbles -destination "id=$ACH_SIM" -derivedDataPath "$ACH_DD" \
  ${1:+-only-testing:PebblesTests/$1} ${2:+-skip-testing:PebblesTests/$2} 2>&1 \
  | grep -E "✘|error:|Expectation failed|recorded an issue|no reference at|Test run with|TEST SUCCEEDED|TEST FAILED" \
  | tail -60
```

Run: `chmod +x apps/ios/build/ach-test.sh`

- [ ] **Step 4: Write the catalog helper**

Create `apps/ios/build/xcstrings-add.py`:

```python
#!/usr/bin/env python3
"""Adds entries to Localizable.xcstrings as text, in Xcode's own layout, without
re-serialising the file (the catalog is formatting-sensitive). Usage:
python3 -I xcstrings-add.py <catalog> <entries.json>
An entry is {"en": …, "fr": …} where each value is a string, or a dict of plural
forms. "word": true makes the plural a substitution (`body`) over argument 1,
set in "template" ({"en": …, "fr": …}, default "%1$#@body@"), so a sentence can
carry the formatted number as argument 2 and pick its noun by argument 1."""
import json
import sys

catalog, entries_path = sys.argv[1], sys.argv[2]
raw = open(catalog, encoding="utf-8").read()
existing = json.loads(raw)["strings"]
entries = json.load(open(entries_path, encoding="utf-8"))


def unit(value):
    return {"stringUnit": {"state": "translated", "value": value}}


def plural(forms):
    return {"plural": {form: unit(text) for form, text in forms.items()}}


def localization(value, word, template):
    if isinstance(value, str):
        return unit(value)
    if word:
        return {"stringUnit": {"state": "translated", "value": template},
                "substitutions": {"body": {"argNum": 1, "formatSpecifier": "lld", "variations": plural(value)}}}
    return {"variations": plural(value)}


blocks = []
for key, spec in entries.items():
    if key in existing:
        sys.exit(f"already in the catalog: {key}")
    if not ({"en", "fr"} <= spec.keys()):
        sys.exit(f"{key} needs both en and fr")
    word = spec.get("word", False)
    entry = {"extractionState": "manual",
             "localizations": {lang: localization(spec[lang], word, spec.get("template", {}).get(lang, "%1$#@body@"))
                               for lang in ("en", "fr")}}
    lines = json.dumps(entry, indent=2, sort_keys=True, ensure_ascii=False, separators=(",", " : ")).split("\n")
    blocks.append("    " + json.dumps(key, ensure_ascii=False) + " : " + lines[0]
                  + "".join("\n    " + line for line in lines[1:]))
anchor = "\n  },\n  \"version\" : "
at = raw.rindex(anchor)
raw = raw[:at] + ",\n" + ",\n".join(blocks) + raw[at:]
json.loads(raw)
open(catalog, "w", encoding="utf-8").write(raw)
print(f"added {len(blocks)} entries")
```

Each task that adds strings writes its entries to `apps/ios/build/strings-<task>.json` and runs `python3 -I apps/ios/build/xcstrings-add.py apps/ios/Pebbles/Resources/Localizable.xcstrings apps/ios/build/strings-<task>.json`. Expected: `added N entries`. French plurals always carry `one`, `many` and `other` (the #1121 lesson).

- [ ] **Step 5: Move the view node to `development` on the hosted map**

With the `arkaik-mcp` tools: `get_node` on `V-achievements`, then `update_node` with `status: "development"` and the node's `metadata` sent back whole (`update_node` replaces metadata wholesale). If the `arkaik-mcp` tools are not available, say so and stop. Never edit `docs/arkaik/bundle.json`.

- [ ] **Step 6: Record the baselines on the untouched branch**

```bash
apps/ios/build/ach-test.sh "" References | tail -5
apps/ios/build/ach-test.sh References > apps/ios/build/references-baseline.txt; grep -c "recorded an issue" apps/ios/build/references-baseline.txt
```

Expected: the unit run ends with `** TEST SUCCEEDED **` (if not, note the failing tests: they are main's, not this stack's). The references count is whatever this Mac fails on main (27 on 2026-10-25); that list is the local gate for every later references run.

---

### Task 2: The payload decoder

**Files:**
- Modify: `project.yml` (the `PebblesTests` excludes)
- Create: `PebblesTests/Achievements/Fixtures/design-fixture.json`
- Create: `PebblesTests/Achievements/AchievementsFixtures.swift`
- Create: `PebblesTests/Achievements/AchievementsPayloadTests.swift`
- Create: `Pebbles/Features/Achievements/Data/AchievementsPayload.swift`

- [ ] **Step 1: Keep the fixtures out of the test bundle**

In `project.yml`, under `PebblesTests` → `sources` → `excludes`, after `- "Spine/Fixtures/**"`, add:

```yaml
          # The achievements payload fixtures are read through #filePath too (AchievementsFixtures).
          - "Achievements/Fixtures/**"
```

- [ ] **Step 2: Write the design fixture**

The approved board's fixture (137 pebbles, 23 of 38 emotions, 14 of 18 domains, 2026 from March, today 4 November) in the exact shape of backend plan Task 9: snake_case keys, `{pebble}` snapshots at whole-second UTC, local `YYYY-MM-DD` dates, absent sections and values omitted (`domains.completion`, `months[].family`, glyph levels' `pebble`). Part 5 adds the harness's verbatim dumps beside it; this file stays the references' fixture.

Create `PebblesTests/Achievements/Fixtures/design-fixture.json`:

```json
{
  "since": "2026-03-01",
  "days_kept": 249,
  "explorer": {
    "first_pebble": {"moment_id": "a0000000-0000-4000-8000-000000000001", "name": "Mara moves in", "happened_at": "2026-03-02T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000006", "intensity": 2, "positiveness": 1},
    "first_five": [
      {"slug": "first-moment", "done": true, "new": false, "on": "2026-03-01", "subject": {"kind": "moment", "id": "a0000000-0000-4000-8000-000000000064", "name": "Unpacking boxes"}},
      {"slug": "first-pebble", "done": true, "new": false, "on": "2026-03-02", "subject": {"kind": "pebble", "id": "a0000000-0000-4000-8000-000000000001", "name": "Mara moves in"}},
      {"slug": "first-collection", "done": true, "new": false, "on": "2026-03-14", "subject": {"kind": "collection", "id": "c0000000-0000-4000-8000-000000000001", "name": "Summer"}},
      {"slug": "first-soul", "done": true, "new": false, "on": "2026-03-03", "subject": {"kind": "soul", "id": "50000000-0000-4000-8000-000000000001", "name": "Mara"}},
      {"slug": "first-glyph", "done": true, "new": false, "on": "2026-03-20", "subject": {"kind": "glyph", "id": "90000000-0000-4000-8000-000000000001", "name": "Wave"}}
    ],
    "six_firsts": [
      {"kind": "small", "done": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000025", "name": "Coffee by the fountain", "happened_at": "2026-03-04T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000022", "intensity": 1, "positiveness": 0}},
      {"kind": "medium", "done": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000001", "name": "Mara moves in", "happened_at": "2026-03-02T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000006", "intensity": 2, "positiveness": 1}},
      {"kind": "large", "done": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000026", "name": "Argument about money", "happened_at": "2026-03-09T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-00000000000c", "intensity": 3, "positiveness": -1}},
      {"kind": "lowlight", "done": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000027", "name": "Argument about money", "happened_at": "2026-03-09T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-00000000000c", "intensity": 3, "positiveness": -1}},
      {"kind": "neutral", "done": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000028", "name": "Train to Lyon", "happened_at": "2026-03-05T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000022", "intensity": 2, "positiveness": 0}},
      {"kind": "highlight", "done": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000001", "name": "Mara moves in", "happened_at": "2026-03-02T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000006", "intensity": 2, "positiveness": 1}}
    ],
    "twenty_five": {
      "count": 25,
      "done": true,
      "new": false,
      "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000029", "name": "Rooftop birthday", "happened_at": "2026-04-02T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000004", "intensity": 2, "positiveness": 1}
    },
    "rings_done": ["first_five", "six_firsts", "twenty_five"],
    "completion": {"completed_at": "2026-03-09T18:00:00Z", "offered_from": "2026-03-10T05:00:00Z", "ready": true}
  },
  "emotions": {
    "count": 23,
    "of": 38,
    "seats": [
      {"seat": 0, "emotion_id": "e0000000-0000-4000-8000-000000000001", "family": "joy", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000002", "name": "Amused day", "happened_at": "2026-03-11T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000001", "intensity": 2, "positiveness": 1}},
      {"seat": 1, "emotion_id": "e0000000-0000-4000-8000-000000000002", "family": "joy", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000003", "name": "Cheerful day", "happened_at": "2026-04-03T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000002", "intensity": 1, "positiveness": 1}},
      {"seat": 2, "emotion_id": "e0000000-0000-4000-8000-000000000003", "family": "joy", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000004", "name": "Content day", "happened_at": "2026-03-08T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 0}},
      {"seat": 3, "emotion_id": "e0000000-0000-4000-8000-000000000004", "family": "joy", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000005", "name": "Excited day", "happened_at": "2026-05-17T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000004", "intensity": 3, "positiveness": 1}},
      {"seat": 4, "emotion_id": "e0000000-0000-4000-8000-000000000005", "family": "joy", "met": false, "new": false},
      {"seat": 5, "emotion_id": "e0000000-0000-4000-8000-000000000006", "family": "joy", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000001", "name": "Mara moves in", "happened_at": "2026-03-02T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000006", "intensity": 2, "positiveness": 1}},
      {"seat": 6, "emotion_id": "e0000000-0000-4000-8000-000000000007", "family": "pride", "met": false, "new": false},
      {"seat": 7, "emotion_id": "e0000000-0000-4000-8000-000000000008", "family": "pride", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000006", "name": "Confident day", "happened_at": "2026-03-21T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000008", "intensity": 2, "positiveness": 1}},
      {"seat": 8, "emotion_id": "e0000000-0000-4000-8000-000000000009", "family": "pride", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000007", "name": "Proud day", "happened_at": "2026-04-10T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000009", "intensity": 3, "positiveness": 1}},
      {"seat": 9, "emotion_id": "e0000000-0000-4000-8000-00000000000a", "family": "pride", "met": false, "new": false},
      {"seat": 10, "emotion_id": "e0000000-0000-4000-8000-00000000000b", "family": "pride", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000008", "name": "Worthy day", "happened_at": "2026-06-02T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-00000000000b", "intensity": 1, "positiveness": 0}},
      {
        "seat": 11,
        "emotion_id": "e0000000-0000-4000-8000-00000000000c",
        "family": "anger",
        "met": true,
        "new": false,
        "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000009", "name": "Argument about money", "happened_at": "2026-03-09T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-00000000000c", "intensity": 3, "positiveness": -1}
      },
      {"seat": 12, "emotion_id": "e0000000-0000-4000-8000-00000000000d", "family": "anger", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000000a", "name": "Annoyed day", "happened_at": "2026-03-15T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-00000000000d", "intensity": 1, "positiveness": -1}},
      {"seat": 13, "emotion_id": "e0000000-0000-4000-8000-00000000000e", "family": "anger", "met": false, "new": false},
      {
        "seat": 14,
        "emotion_id": "e0000000-0000-4000-8000-00000000000f",
        "family": "anger",
        "met": true,
        "new": false,
        "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000000b", "name": "Frustrated day", "happened_at": "2026-05-04T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-00000000000f", "intensity": 2, "positiveness": -1}
      },
      {"seat": 15, "emotion_id": "e0000000-0000-4000-8000-000000000010", "family": "anger", "met": false, "new": false},
      {"seat": 16, "emotion_id": "e0000000-0000-4000-8000-000000000011", "family": "anger", "met": false, "new": false},
      {"seat": 17, "emotion_id": "e0000000-0000-4000-8000-000000000012", "family": "fear", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000000c", "name": "Anxious day", "happened_at": "2026-03-19T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000012", "intensity": 2, "positiveness": -1}},
      {"seat": 18, "emotion_id": "e0000000-0000-4000-8000-000000000013", "family": "fear", "met": false, "new": false},
      {"seat": 19, "emotion_id": "e0000000-0000-4000-8000-000000000014", "family": "fear", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000000d", "name": "Nervous day", "happened_at": "2026-04-22T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000014", "intensity": 1, "positiveness": 0}},
      {"seat": 20, "emotion_id": "e0000000-0000-4000-8000-000000000015", "family": "fear", "met": false, "new": false},
      {"seat": 21, "emotion_id": "e0000000-0000-4000-8000-000000000016", "family": "fear", "met": false, "new": false},
      {"seat": 22, "emotion_id": "e0000000-0000-4000-8000-000000000017", "family": "fear", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000000e", "name": "Worried day", "happened_at": "2026-07-07T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000017", "intensity": 2, "positiveness": -1}},
      {"seat": 23, "emotion_id": "e0000000-0000-4000-8000-000000000018", "family": "shame", "met": false, "new": false},
      {
        "seat": 24,
        "emotion_id": "e0000000-0000-4000-8000-000000000019",
        "family": "shame",
        "met": true,
        "new": false,
        "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000000f", "name": "Embarrassed day", "happened_at": "2026-06-18T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000019", "intensity": 1, "positiveness": -1}
      },
      {"seat": 25, "emotion_id": "e0000000-0000-4000-8000-00000000001a", "family": "shame", "met": false, "new": false},
      {"seat": 26, "emotion_id": "e0000000-0000-4000-8000-00000000001b", "family": "shame", "met": false, "new": false},
      {
        "seat": 27,
        "emotion_id": "e0000000-0000-4000-8000-00000000001c",
        "family": "sadness",
        "met": true,
        "new": false,
        "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000010", "name": "Disappointed day", "happened_at": "2026-04-14T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-00000000001c", "intensity": 2, "positiveness": -1}
      },
      {"seat": 28, "emotion_id": "e0000000-0000-4000-8000-00000000001d", "family": "sadness", "met": false, "new": false},
      {
        "seat": 29,
        "emotion_id": "e0000000-0000-4000-8000-00000000001e",
        "family": "sadness",
        "met": true,
        "new": false,
        "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000011", "name": "Lonely day", "happened_at": "2026-08-03T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-00000000001e", "intensity": 2, "positiveness": -1}
      },
      {"seat": 30, "emotion_id": "e0000000-0000-4000-8000-00000000001f", "family": "sadness", "met": false, "new": false},
      {
        "seat": 31,
        "emotion_id": "e0000000-0000-4000-8000-000000000020",
        "family": "sadness",
        "met": true,
        "new": true,
        "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000012", "name": "Grandma's call went quiet", "happened_at": "2026-10-12T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000020", "intensity": 3, "positiveness": -1}
      },
      {"seat": 32, "emotion_id": "e0000000-0000-4000-8000-000000000021", "family": "sadness", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000013", "name": "Tired day", "happened_at": "2026-03-24T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000021", "intensity": 1, "positiveness": 0}},
      {
        "seat": 33,
        "emotion_id": "e0000000-0000-4000-8000-000000000022",
        "family": "peace",
        "met": true,
        "new": false,
        "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000014", "name": "Train to Lyon", "happened_at": "2026-03-05T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000022", "intensity": 1, "positiveness": 0}
      },
      {"seat": 34, "emotion_id": "e0000000-0000-4000-8000-000000000023", "family": "peace", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000015", "name": "Grounded day", "happened_at": "2026-05-25T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000023", "intensity": 2, "positiveness": 1}},
      {
        "seat": 35,
        "emotion_id": "e0000000-0000-4000-8000-000000000024",
        "family": "peace",
        "met": true,
        "new": true,
        "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000016", "name": "Forest walk, all of us", "happened_at": "2026-10-20T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000024", "intensity": 2, "positiveness": 1}
      },
      {"seat": 36, "emotion_id": "e0000000-0000-4000-8000-000000000025", "family": "peace", "met": false, "new": false},
      {"seat": 37, "emotion_id": "e0000000-0000-4000-8000-000000000026", "family": "peace", "met": true, "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000017", "name": "Relieved day", "happened_at": "2026-07-30T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000026", "intensity": 2, "positiveness": 1}}
    ]
  },
  "domains": {
    "count": 14,
    "of": 18,
    "coverage": "most",
    "entered": [
      {"domain_id": "d0000000-0000-4000-8000-00000000000e", "new": true, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000024", "name": "Events moment", "happened_at": "2026-10-17T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"domain_id": "d0000000-0000-4000-8000-00000000000d", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000023", "name": "Body moment", "happened_at": "2026-08-21T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"domain_id": "d0000000-0000-4000-8000-00000000000c", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000022", "name": "Money moment", "happened_at": "2026-07-09T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"domain_id": "d0000000-0000-4000-8000-00000000000b", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000021", "name": "Nature moment", "happened_at": "2026-06-14T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"domain_id": "d0000000-0000-4000-8000-00000000000a", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000020", "name": "Identity moment", "happened_at": "2026-05-23T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"domain_id": "d0000000-0000-4000-8000-000000000009", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000001f", "name": "Learning moment", "happened_at": "2026-05-02T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"domain_id": "d0000000-0000-4000-8000-000000000008", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000001e", "name": "Hobbies moment", "happened_at": "2026-04-18T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"domain_id": "d0000000-0000-4000-8000-000000000007", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000001d", "name": "Travel moment", "happened_at": "2026-04-04T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"domain_id": "d0000000-0000-4000-8000-000000000006", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000001c", "name": "Partner moment", "happened_at": "2026-03-20T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"domain_id": "d0000000-0000-4000-8000-000000000005", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000001b", "name": "Health moment", "happened_at": "2026-03-12T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"domain_id": "d0000000-0000-4000-8000-000000000004", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000001a", "name": "Work moment", "happened_at": "2026-03-09T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"domain_id": "d0000000-0000-4000-8000-000000000003", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000019", "name": "Home moment", "happened_at": "2026-03-05T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"domain_id": "d0000000-0000-4000-8000-000000000002", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000018", "name": "Friends moment", "happened_at": "2026-03-03T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"domain_id": "d0000000-0000-4000-8000-000000000001", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000001", "name": "Mara moves in", "happened_at": "2026-03-02T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000006", "intensity": 2, "positiveness": 1}}
    ],
    "not_yet": ["d0000000-0000-4000-8000-00000000000f", "d0000000-0000-4000-8000-000000000010", "d0000000-0000-4000-8000-000000000011", "d0000000-0000-4000-8000-000000000012"]
  },
  "pebbles": {
    "count": 137,
    "levels": [
      {"threshold": 1, "on": "2026-03-02", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000001", "name": "Mara moves in", "happened_at": "2026-03-02T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000006", "intensity": 2, "positiveness": 1}},
      {"threshold": 10, "on": "2026-03-13", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000002a", "name": "Picnic on the square", "happened_at": "2026-03-13T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000003", "intensity": 2, "positiveness": 1}},
      {"threshold": 25, "on": "2026-04-02", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000002b", "name": "Rooftop birthday", "happened_at": "2026-04-02T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000004", "intensity": 2, "positiveness": 1}},
      {"threshold": 50, "on": "2026-05-09", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000002c", "name": "Flag march with Nadia", "happened_at": "2026-05-09T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000009", "intensity": 3, "positiveness": 1}},
      {"threshold": 100, "on": "2026-08-01", "new": false, "pebble": {"moment_id": "a0000000-0000-4000-8000-00000000002d", "name": "Mara on the seawall", "happened_at": "2026-08-01T09:30:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000006", "intensity": 2, "positiveness": 1}}
    ],
    "next": {"threshold": 250, "left": 113, "progress": 0.247},
    "ladder": [1, 10, 25, 50, 100, 250, 500, 1000],
    "then_every": 500
  },
  "glyphs_carved": {
    "count": 12,
    "levels": [
      {"threshold": 10, "on": "2026-06-12", "new": false}
    ],
    "next": {"threshold": 25, "left": 13, "progress": 0.133},
    "ladder": [10, 25, 50, 75, 100]
  },
  "glyph_sales": {
    "count": 3,
    "levels": [
      {"threshold": 1, "on": "2026-05-30", "new": false}
    ],
    "next": {"threshold": 5, "left": 2, "progress": 0.5},
    "ladder": [1, 5, 10, 25, 50]
  },
  "years": [
    {
      "year": 2026,
      "current": true,
      "months": [
        {"month": 1, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0},
        {"month": 2, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0},
        {"month": 3, "state": "kept", "moment_days": 18, "pebble_days": 13, "full_weeks": 4, "weeks_filled": 4, "level": 1, "progress": 0.929, "family": "joy"},
        {"month": 4, "state": "kept", "moment_days": 16, "pebble_days": 12, "full_weeks": 3, "weeks_filled": 3, "level": 1, "progress": 0.857, "family": "pride"},
        {"month": 5, "state": "kept", "moment_days": 15, "pebble_days": 11, "full_weeks": 4, "weeks_filled": 4, "level": 1, "progress": 0.786, "family": "joy"},
        {"month": 6, "state": "kept", "moment_days": 14, "pebble_days": 12, "full_weeks": 4, "weeks_filled": 4, "level": 1, "progress": 0.857, "family": "peace"},
        {"month": 7, "state": "kept", "moment_days": 17, "pebble_days": 14, "full_weeks": 3, "weeks_filled": 2, "level": 1, "progress": 0.667, "family": "joy"},
        {"month": 8, "state": "kept", "moment_days": 20, "pebble_days": 18, "full_weeks": 4, "weeks_filled": 4, "level": 2, "progress": 0, "family": "joy"},
        {"month": 9, "state": "kept", "moment_days": 16, "pebble_days": 10, "full_weeks": 3, "weeks_filled": 3, "level": 1, "progress": 0.714, "family": "sadness"},
        {"month": 10, "state": "kept", "moment_days": 19, "pebble_days": 13, "full_weeks": 3, "weeks_filled": 3, "level": 1, "progress": 0.929, "family": "joy"},
        {"month": 11, "state": "kept", "moment_days": 2, "pebble_days": 1, "full_weeks": 0, "weeks_filled": 0, "level": 0, "progress": 0.143, "family": "joy"},
        {"month": 12, "state": "upcoming", "moment_days": 0, "pebble_days": 0, "full_weeks": 0, "weeks_filled": 0, "level": 0, "progress": 0}
      ],
      "listed": [11, 10, 9, 8, 7, 6, 5, 4, 3],
      "next_step": {"month": 11, "needs": "moments", "days_left": 12, "reachable": true}
    }
  ]
}
```

The seats run joy (0–5), pride (6–10), anger (11–16), fear (17–22), shame (23–26), sadness (27–32), peace (33–37); seat `k`'s emotion id ends in `k + 1` in hex. Seats 31 (sad) and 35 (peaceful) and the domain `…0e` (the newest) are new.

- [ ] **Step 3: Write the fixture loader**

Create `PebblesTests/Achievements/AchievementsFixtures.swift`:

```swift
import Foundation
@testable import Pebbles

/// The achievements payloads the tests replay (#1306), read in place through
/// `#filePath` as the wraps' payloads are (the simulator shares the host disk).
/// `design-fixture.json` is the approved board's fixture in `get_achievements`'
/// exact shape (backend plan Task 9); part 5 adds the harness's verbatim dumps.
enum AchievementsFixtures {
    static let folder = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()
        .appendingPathComponent("Fixtures")

    static func data(_ name: String) throws -> Data {
        try Data(contentsOf: folder.appendingPathComponent("\(name).json"))
    }

    static func payload(_ name: String) throws -> AchievementsPayload {
        try WrapDecoding.decode(AchievementsPayload.self, from: data(name))
    }

    /// A payload written inline, for the tolerance cases.
    static func json(_ text: String) throws -> AchievementsPayload {
        try WrapDecoding.decode(AchievementsPayload.self, from: Data(text.utf8))
    }
}
```

- [ ] **Step 4: Write the failing tests**

Create `PebblesTests/Achievements/AchievementsPayloadTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// `get_achievements` decoded as the server sends it (#1306, the foreign-payload
/// rule): the design fixture in Task 9's shape, the timestamp precisions every
/// surface emits, omitted sections, and a section that does not decode.
@Suite("Achievements payload decoding")
struct AchievementsPayloadTests {
    @Test("the design fixture decodes every section")
    func designFixture() throws {
        let payload = try AchievementsFixtures.payload("design-fixture")
        #expect(payload.skipped.isEmpty)
        #expect(payload.since == CalendarDay(year: 2026, month: 3, day: 1))
        #expect(payload.daysKept == 249)

        let explorer = try #require(payload.explorer)
        #expect(explorer.firstFive.map(\.slug)
            == ["first-moment", "first-pebble", "first-collection", "first-soul", "first-glyph"])
        #expect(explorer.firstFive[2].on == CalendarDay(year: 2026, month: 3, day: 14))
        #expect(explorer.firstFive[2].subject?.kind == "collection")
        #expect(explorer.firstFive[2].subject?.name == "Summer")
        #expect(explorer.sixFirsts.map(\.kind) == ["small", "medium", "large", "lowlight", "neutral", "highlight"])
        #expect(explorer.sixFirsts[2].pebble?.positiveness == -1)
        #expect(explorer.twentyFive?.count == 25)
        #expect(explorer.ringsDone == ["first_five", "six_firsts", "twenty_five"])
        #expect(explorer.completion?.ready == true)
        #expect(explorer.completion?.pulledAt == nil)
        #expect(explorer.completion?.offeredFrom == ISO8601Flexible.parse("2026-03-10T05:00:00Z"))
        #expect(explorer.firstPebble?.name == "Mara moves in")
        #expect(explorer.firstPebble?.happenedAt == ISO8601Flexible.parse("2026-03-02T09:30:00Z"))

        let emotions = try #require(payload.emotions)
        #expect(emotions.count == 23 && emotions.of == 38)
        #expect(emotions.seats.count == 38)
        #expect(emotions.seats.filter(\.met).count == 23)
        #expect(emotions.seats.filter(\.isNew).map(\.seat) == [31, 35])
        #expect(emotions.seats[5].pebble?.momentId == UUID(uuidString: "a0000000-0000-4000-8000-000000000001"))
        #expect(emotions.completion == nil)

        let domains = try #require(payload.domains)
        #expect(domains.coverage == "most")
        #expect(domains.entered.count == 14 && domains.notYet.count == 4)
        #expect(domains.entered.first?.isNew == true)
        #expect(domains.completion == nil)

        #expect(payload.pebbles?.count == 137)
        #expect(payload.pebbles?.levels.map(\.threshold) == [1, 10, 25, 50, 100])
        #expect(payload.pebbles?.next == AchievementsPayload.Next(threshold: 250, left: 113, progress: 0.247))
        #expect(payload.pebbles?.thenEvery == 500)
        #expect(payload.glyphsCarved?.thenEvery == nil)
        #expect(payload.glyphSales?.levels.first?.pebble == nil)
        #expect(payload.glyphSales?.levels.first?.on == CalendarDay(year: 2026, month: 5, day: 30))

        let year = try #require(payload.years?.first)
        #expect(year.year == 2026 && year.current)
        #expect(year.months.count == 12)
        #expect(year.months[7].level == 2 && year.months[7].family == "joy")
        #expect(year.months[11].state == "upcoming" && year.months[11].family == nil)
        #expect(year.listed == [11, 10, 9, 8, 7, 6, 5, 4, 3])
        #expect(year.nextStep == AchievementsPayload.NextStep(month: 11, needs: "moments", daysLeft: 12, reachable: true))
    }

    @Test("a pebble's happened_at reads at every precision the surfaces emit", arguments: [
        "2026-03-02T09:30:00Z", "2026-03-02T09:30:00.123Z", "2026-03-02T09:30:00.123456+00:00"
    ])
    func precision(raw: String) throws {
        let payload = try AchievementsFixtures.json("""
        {"explorer": {"first_five": [], "six_firsts": [], "rings_done": [],
         "first_pebble": {"moment_id": "a0000000-0000-4000-8000-000000000001", "name": null,
                          "happened_at": "\(raw)", "emotion_id": null, "intensity": 2, "positiveness": 1}}}
        """)
        let pebble = try #require(payload.explorer?.firstPebble)
        let seconds = try #require(pebble.happenedAt?.timeIntervalSince1970)
        #expect(abs(seconds - 1_772_443_800) < 1)
        #expect(pebble.name == nil && pebble.emotionId == nil)
    }

    @Test("absent sections stay absent and are not errors")
    func absentSections() throws {
        let payload = try AchievementsFixtures.json(#"{"years": []}"#)
        #expect(payload.explorer == nil && payload.emotions == nil && payload.domains == nil)
        #expect(payload.pebbles == nil && payload.glyphsCarved == nil && payload.glyphSales == nil)
        #expect(payload.since == nil && payload.daysKept == nil)
        #expect(payload.skipped.isEmpty)
    }

    @Test("a section that does not decode is dropped and named; the others survive")
    func brokenSection() throws {
        let payload = try AchievementsFixtures.json("""
        {"since": "2026-03-01", "days_kept": 249, "domains": {"count": "fourteen"},
         "pebbles": {"count": 3, "levels": [], "ladder": [1, 10],
                     "next": {"threshold": 10, "left": 7, "progress": 0.222}}}
        """)
        #expect(payload.domains == nil)
        #expect(payload.skipped == ["domains"])
        #expect(payload.pebbles?.count == 3)
        #expect(payload.daysKept == 249)
    }

    @Test("missing scalars take their defaults; a malformed day or id reads as absent")
    func defaults() throws {
        let payload = try AchievementsFixtures.json("""
        {"pebbles": {"levels": [{"threshold": 1, "on": "2 March"}]},
         "explorer": {"first_five": [{"slug": "first-moment", "subject": {"kind": "moment", "id": "not-a-uuid"}}]}}
        """)
        #expect(payload.pebbles?.count == 0 && payload.pebbles?.ladder == [] && payload.pebbles?.next == nil)
        #expect(payload.pebbles?.levels.first?.on == nil)
        #expect(payload.pebbles?.levels.first?.isNew == false)
        #expect(payload.explorer?.firstFive.first?.done == false)
        #expect(payload.explorer?.firstFive.first?.subject?.id == nil)
    }
}
```

- [ ] **Step 5: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh AchievementsPayloadTests`
Expected: the build fails with `error: cannot find type 'AchievementsPayload' in scope`.

- [ ] **Step 6: Write the payload**

Create `Pebbles/Features/Achievements/Data/AchievementsPayload.swift`:

```swift
import Foundation

/// `get_achievements(p_tz)` (#1306; backend plan Task 9, spec §3.3) as the
/// server sends it. An absent section omits its keys (D17), so each is optional
/// here. A section present but undecodable is dropped and its key kept in
/// `skipped` for the service's log, so one bad card never blanks the page
/// (spec §4). Inside a section a missing scalar takes its default, a malformed
/// day or id reads as absent, and a timestamp is parsed from its string through
/// `ISO8601Flexible`, never by a decoder's date strategy. `AchievementsMapping`
/// turns it into `AchievementsPage`. Android: `AchievementsPayload.kt`.
struct AchievementsPayload: Decodable, Equatable {
    let since: CalendarDay?
    let daysKept: Int?
    let explorer: Explorer?
    let emotions: Emotions?
    let domains: Domains?
    let pebbles: Ladder?
    let glyphsCarved: Ladder?
    let glyphSales: Ladder?
    let years: [Year]?
    /// The keys present but undecodable.
    let skipped: [String]

    private enum CodingKeys: String, CodingKey {
        case since, explorer, emotions, domains, pebbles, years
        case daysKept = "days_kept"
        case glyphsCarved = "glyphs_carved"
        case glyphSales = "glyph_sales"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        var skipped: [String] = []
        func section<T: Decodable>(_ key: CodingKeys) -> T? {
            guard c.contains(key) else { return nil }
            do {
                return try c.decodeIfPresent(T.self, forKey: key)
            } catch {
                skipped.append(key.stringValue)
                return nil
            }
        }
        since = section(.since)
        daysKept = section(.daysKept)
        explorer = section(.explorer)
        emotions = section(.emotions)
        domains = section(.domains)
        pebbles = section(.pebbles)
        glyphsCarved = section(.glyphsCarved)
        glyphSales = section(.glyphSales)
        years = section(.years)
        self.skipped = skipped
    }
}

fileprivate extension KeyedDecodingContainer {
    /// A `YYYY-MM-DD` day, or nil when absent, null or malformed.
    func day(_ key: Key) throws -> CalendarDay? {
        try decodeIfPresent(String.self, forKey: key).flatMap(CalendarDay.init(iso:))
    }

    /// A timestamp from its string form at any precision, or nil.
    func instant(_ key: Key) throws -> Date? {
        try decodeIfPresent(String.self, forKey: key).flatMap(ISO8601Flexible.parse)
    }

    /// A UUID from its string form, or nil when absent or malformed.
    func uuid(_ key: Key) throws -> UUID? {
        try decodeIfPresent(String.self, forKey: key).flatMap(UUID.init(uuidString:))
    }
}

extension AchievementsPayload {
    /// A crossing pebble's display snapshot (D15). A deleted moment's row still draws.
    struct Pebble: Decodable, Equatable {
        let momentId: UUID?
        let name: String?
        let happenedAt: Date?
        let emotionId: String?
        let intensity: Int
        let positiveness: Int

        private enum CodingKeys: String, CodingKey {
            case name, intensity, positiveness
            case momentId = "moment_id"
            case happenedAt = "happened_at"
            case emotionId = "emotion_id"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            momentId = try c.uuid(.momentId)
            name = try c.decodeIfPresent(String.self, forKey: .name)
            happenedAt = try c.instant(.happenedAt)
            emotionId = try c.decodeIfPresent(String.self, forKey: .emotionId)
            intensity = try c.decode(.intensity, or: 2)
            positiveness = try c.decode(.positiveness, or: 0)
        }
    }

    /// A set made whole (D9); `ready` is computed by the server, `pulled_at` absent until pulled.
    struct Completion: Decodable, Equatable {
        let completedAt: Date?
        let offeredFrom: Date?
        let ready: Bool
        let pulledAt: Date?

        private enum CodingKeys: String, CodingKey {
            case ready
            case completedAt = "completed_at"
            case offeredFrom = "offered_from"
            case pulledAt = "pulled_at"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            completedAt = try c.instant(.completedAt)
            offeredFrom = try c.instant(.offeredFrom)
            ready = try c.decode(.ready, or: false)
            pulledAt = try c.instant(.pulledAt)
        }
    }

    struct Explorer: Decodable, Equatable {
        let firstPebble: Pebble?
        let firstFive: [FirstFive]
        let sixFirsts: [SixFirst]
        let twentyFive: TwentyFive?
        let ringsDone: [String]
        let completion: Completion?

        private enum CodingKeys: String, CodingKey {
            case completion
            case firstPebble = "first_pebble"
            case firstFive = "first_five"
            case sixFirsts = "six_firsts"
            case twentyFive = "twenty_five"
            case ringsDone = "rings_done"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            firstPebble = try c.decodeIfPresent(Pebble.self, forKey: .firstPebble)
            firstFive = try c.decode(.firstFive, or: [])
            sixFirsts = try c.decode(.sixFirsts, or: [])
            twentyFive = try c.decodeIfPresent(TwentyFive.self, forKey: .twentyFive)
            ringsDone = try c.decode(.ringsDone, or: [])
            completion = try c.decodeIfPresent(Completion.self, forKey: .completion)
        }
    }

    struct FirstFive: Decodable, Equatable {
        let slug: String
        let done: Bool
        let on: CalendarDay?
        let subject: Subject?
        let isNew: Bool

        private enum CodingKeys: String, CodingKey {
            case slug, done, on, subject
            case isNew = "new"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            slug = try c.decode(.slug, or: "")
            done = try c.decode(.done, or: false)
            on = try c.day(.on)
            subject = try c.decodeIfPresent(Subject.self, forKey: .subject)
            isNew = try c.decode(.isNew, or: false)
        }
    }

    /// What a first-five task made: a moment, a pebble (its moment's id), a collection, a soul or a glyph.
    struct Subject: Decodable, Equatable {
        let kind: String
        let id: UUID?
        let name: String?

        private enum CodingKeys: String, CodingKey { case kind, id, name }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            kind = try c.decode(.kind, or: "")
            id = try c.uuid(.id)
            name = try c.decodeIfPresent(String.self, forKey: .name)
        }
    }

    struct SixFirst: Decodable, Equatable {
        let kind: String
        let done: Bool
        let pebble: Pebble?
        let isNew: Bool

        private enum CodingKeys: String, CodingKey {
            case kind, done, pebble
            case isNew = "new"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            kind = try c.decode(.kind, or: "")
            done = try c.decode(.done, or: false)
            pebble = try c.decodeIfPresent(Pebble.self, forKey: .pebble)
            isNew = try c.decode(.isNew, or: false)
        }
    }

    struct TwentyFive: Decodable, Equatable {
        let count: Int
        let done: Bool
        let pebble: Pebble?
        let isNew: Bool

        private enum CodingKeys: String, CodingKey {
            case count, done, pebble
            case isNew = "new"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            count = try c.decode(.count, or: 0)
            done = try c.decode(.done, or: false)
            pebble = try c.decodeIfPresent(Pebble.self, forKey: .pebble)
            isNew = try c.decode(.isNew, or: false)
        }
    }

    struct Emotions: Decodable, Equatable {
        let count: Int
        let of: Int
        let seats: [Seat]
        let completion: Completion?

        private enum CodingKeys: String, CodingKey { case count, of, seats, completion }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            count = try c.decode(.count, or: 0)
            of = try c.decode(.of, or: 0)
            seats = try c.decode(.seats, or: [])
            completion = try c.decodeIfPresent(Completion.self, forKey: .completion)
        }
    }

    struct Seat: Decodable, Equatable {
        let seat: Int
        let emotionId: String
        let family: String
        let met: Bool
        let pebble: Pebble?
        let isNew: Bool

        private enum CodingKeys: String, CodingKey {
            case seat, family, met, pebble
            case emotionId = "emotion_id"
            case isNew = "new"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            seat = try c.decode(.seat, or: 0)
            emotionId = try c.decode(.emotionId, or: "")
            family = try c.decode(.family, or: "")
            met = try c.decode(.met, or: false)
            pebble = try c.decodeIfPresent(Pebble.self, forKey: .pebble)
            isNew = try c.decode(.isNew, or: false)
        }
    }

    struct Domains: Decodable, Equatable {
        let count: Int
        let of: Int
        let coverage: String
        let entered: [Entered]
        let notYet: [String]
        let completion: Completion?

        private enum CodingKeys: String, CodingKey {
            case count, of, coverage, entered, completion
            case notYet = "not_yet"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            count = try c.decode(.count, or: 0)
            of = try c.decode(.of, or: 0)
            coverage = try c.decode(.coverage, or: "")
            entered = try c.decode(.entered, or: [])
            notYet = try c.decode(.notYet, or: [])
            completion = try c.decodeIfPresent(Completion.self, forKey: .completion)
        }
    }

    struct Entered: Decodable, Equatable {
        let domainId: String
        let pebble: Pebble?
        let isNew: Bool

        private enum CodingKeys: String, CodingKey {
            case pebble
            case domainId = "domain_id"
            case isNew = "new"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            domainId = try c.decode(.domainId, or: "")
            pebble = try c.decodeIfPresent(Pebble.self, forKey: .pebble)
            isNew = try c.decode(.isNew, or: false)
        }
    }

    /// One counting stack (D3): pebbles, glyphs carved or glyph sales.
    struct Ladder: Decodable, Equatable {
        let count: Int
        let levels: [Level]
        let next: Next?
        let ladder: [Int]
        let thenEvery: Int?

        private enum CodingKeys: String, CodingKey {
            case count, levels, next, ladder
            case thenEvery = "then_every"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            count = try c.decode(.count, or: 0)
            levels = try c.decode(.levels, or: [])
            next = try c.decodeIfPresent(Next.self, forKey: .next)
            ladder = try c.decode(.ladder, or: [])
            thenEvery = try c.decodeIfPresent(Int.self, forKey: .thenEvery)
        }
    }

    struct Level: Decodable, Equatable {
        let threshold: Int
        let on: CalendarDay?
        let pebble: Pebble?
        let isNew: Bool

        private enum CodingKeys: String, CodingKey {
            case threshold, on, pebble
            case isNew = "new"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            threshold = try c.decode(.threshold, or: 0)
            on = try c.day(.on)
            pebble = try c.decodeIfPresent(Pebble.self, forKey: .pebble)
            isNew = try c.decode(.isNew, or: false)
        }
    }

    struct Next: Decodable, Equatable {
        let threshold: Int
        let left: Int
        let progress: Double

        init(threshold: Int, left: Int, progress: Double) {
            self.threshold = threshold
            self.left = left
            self.progress = progress
        }

        private enum CodingKeys: String, CodingKey { case threshold, left, progress }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            threshold = try c.decode(.threshold, or: 0)
            left = try c.decode(.left, or: 0)
            progress = try c.decode(.progress, or: 0)
        }
    }

    /// One year disc (D12).
    struct Year: Decodable, Equatable {
        let year: Int
        let current: Bool
        let months: [Month]
        let listed: [Int]
        let nextStep: NextStep?

        private enum CodingKeys: String, CodingKey {
            case year, current, months, listed
            case nextStep = "next_step"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            year = try c.decode(.year, or: 0)
            current = try c.decode(.current, or: false)
            months = try c.decode(.months, or: [])
            listed = try c.decode(.listed, or: [])
            nextStep = try c.decodeIfPresent(NextStep.self, forKey: .nextStep)
        }
    }

    struct Month: Decodable, Equatable {
        let month: Int
        let state: String
        let family: String?
        let momentDays: Int
        let pebbleDays: Int
        let fullWeeks: Int
        let weeksFilled: Int
        let level: Int
        let progress: Double

        private enum CodingKeys: String, CodingKey {
            case month, state, family, level, progress
            case momentDays = "moment_days"
            case pebbleDays = "pebble_days"
            case fullWeeks = "full_weeks"
            case weeksFilled = "weeks_filled"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            month = try c.decode(.month, or: 0)
            state = try c.decode(.state, or: "")
            family = try c.decodeIfPresent(String.self, forKey: .family)
            momentDays = try c.decode(.momentDays, or: 0)
            pebbleDays = try c.decode(.pebbleDays, or: 0)
            fullWeeks = try c.decode(.fullWeeks, or: 0)
            weeksFilled = try c.decode(.weeksFilled, or: 0)
            level = try c.decode(.level, or: 0)
            progress = try c.decode(.progress, or: 0)
        }
    }

    struct NextStep: Decodable, Equatable {
        let month: Int
        let needs: String
        let daysLeft: Int
        let reachable: Bool

        init(month: Int, needs: String, daysLeft: Int, reachable: Bool) {
            self.month = month
            self.needs = needs
            self.daysLeft = daysLeft
            self.reachable = reachable
        }

        private enum CodingKeys: String, CodingKey {
            case month, needs, reachable
            case daysLeft = "days_left"
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            month = try c.decode(.month, or: 0)
            needs = try c.decode(.needs, or: "")
            daysLeft = try c.decode(.daysLeft, or: 0)
            reachable = try c.decode(.reachable, or: true)
        }
    }
}
```

`decode(_:or:)` is `WrapDecoding.swift`'s: a missing key or an explicit null takes the default, kotlinx's `coerceInputValues`, which Android decodes with.

- [ ] **Step 7: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh AchievementsPayloadTests`
Expected: `** TEST SUCCEEDED **`, 7 tests (the precision test runs three arguments).

- [ ] **Step 8: Commit**

```bash
git add apps/ios/project.yml apps/ios/PebblesTests/Achievements apps/ios/Pebbles/Features/Achievements/Data/AchievementsPayload.swift
git commit -m "feat(ios): decode the achievements page payload"
```

---

### Task 3: The display model and its mapping

**Files:**
- Create: `Pebbles/Features/Achievements/Data/AchievementsPage.swift`
- Create: `Pebbles/Features/Achievements/Data/AchievementsMapping.swift`
- Create: `PebblesTests/Achievements/AchievementsMappingTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `PebblesTests/Achievements/AchievementsMappingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// Payload → page (#1306): one-to-one, typed, clamped, every unknown enum on its
/// fallback (D17). Nothing is counted, compared or ordered by the mapping.
@Suite("Achievements page mapping")
struct AchievementsMappingTests {
    @Test("the design fixture maps to the page the views draw")
    func design() throws {
        let page = try AchievementsFixtures.payload("design-fixture").model
        #expect(page.since == CalendarDay(year: 2026, month: 3, day: 1) && page.daysKept == 249)

        let explorer = try #require(page.explorer)
        #expect(explorer.ringsDone == [.firstFive, .sixFirsts, .twentyFive])
        #expect(explorer.firstFive.compactMap(\.task) == HomeChecklist.Task.allCases)
        #expect(explorer.firstFive[1].subjectId == UUID(uuidString: "a0000000-0000-4000-8000-000000000001"))
        #expect(explorer.firstFive[1].subjectKind == "pebble")
        #expect(explorer.sixFirsts.compactMap(\.kind) == AchievementsPage.StoneKind.allCases)
        #expect(explorer.sixFirsts[2].pebble?.valence == .lowlightLarge)
        #expect(explorer.firstPebble?.valence == .highlightMedium)
        #expect(explorer.completion?.stack == .sixFirsts && explorer.completion?.isReady == true)

        let domains = try #require(page.domains)
        #expect(domains.coverage == .most)
        #expect(domains.entered.first?.domainId == "d0000000-0000-4000-8000-00000000000e")
        #expect(domains.arrival.first?.domainId == "d0000000-0000-4000-8000-000000000001")
        #expect(domains.arrival.last?.isNew == true)

        let pebbles = try #require(page.pebbles)
        #expect(pebbles.reachedSteps == [1, 10, 25, 50, 100])
        #expect(pebbles.isReached(100) && !pebbles.isReached(250))

        let year = try #require(page.years.first)
        #expect(year.isCurrent && year.id == 2026)
        #expect(year.month(8)?.level == 2 && year.month(8)?.isMuted == false)
        #expect(year.month(1)?.state == .quiet && year.month(1)?.isMuted == true)
        #expect(year.month(12)?.state == .upcoming && year.month(12)?.isMuted == true)
        #expect(year.nextStep == AchievementsPage.NextStep(month: 11, needs: .moments, daysLeft: 12, reachable: true))
    }

    @Test("values this build does not know fall back safely (D17)")
    func fallbacks() throws {
        let page = try AchievementsFixtures.json("""
        {"explorer": {"first_five": [{"slug": "first-dance", "done": true}],
                      "six_firsts": [{"kind": "tiny", "done": true}], "rings_done": ["constellation", "six_firsts"]},
         "domains": {"count": 1, "of": 18, "coverage": "plenty", "entered": [], "not_yet": []},
         "emotions": {"count": 0, "of": 1, "seats": [{"seat": 0, "emotion_id": "x", "family": "awe", "met": false}]},
         "years": [{"year": 2026, "current": true, "listed": [],
                    "months": [{"month": 3, "state": "dormant", "level": 5, "progress": 1.4},
                               {"month": 4, "state": "kept", "level": -1, "progress": -0.2}],
                    "next_step": {"month": 3, "needs": "photos", "days_left": 2, "reachable": true}}]}
        """).model
        #expect(page.explorer?.firstFive.first?.task == nil)
        #expect(page.explorer?.sixFirsts.first?.kind == nil)
        #expect(page.explorer?.ringsDone == [.sixFirsts])
        #expect(page.domains?.coverage == .unknown)
        #expect(page.emotions?.seats.first?.family == "awe")
        let march = try #require(page.years.first?.month(3))
        #expect(march.state == .quiet && march.level == 2 && march.progress == 1)
        let april = try #require(page.years.first?.month(4))
        #expect(april.level == 0 && april.progress == 0)
        #expect(april.isMuted, "a kept month without a family has no hue to draw")
        #expect(page.years.first?.nextStep == nil)
    }

    @Test("a pebble outside the 3 × 3 grid draws as the even medium stone")
    func offGridPebble() throws {
        let page = try AchievementsFixtures.json(#"{"explorer": {"first_pebble": {"intensity": 4, "positiveness": 2}}}"#).model
        #expect(page.explorer?.firstPebble?.valence == .neutralMedium)
    }

    @Test("each completion carries its stack, and pulled_at when pulled")
    func completions() throws {
        let page = try AchievementsFixtures.json("""
        {"emotions": {"count": 38, "of": 38, "seats": [],
                      "completion": {"completed_at": "2026-10-01T10:00:00Z", "offered_from": "2026-10-02T04:00:00Z",
                                     "ready": false, "pulled_at": "2026-10-03T08:00:00.5+00:00"}},
         "domains": {"count": 18, "of": 18, "coverage": "all", "entered": [], "not_yet": [],
                     "completion": {"completed_at": "2026-10-01T10:00:00Z", "offered_from": "2026-10-02T04:00:00Z",
                                    "ready": true}}}
        """).model
        #expect(page.emotions?.completion?.stack == .emotions)
        #expect(page.emotions?.completion?.pulledAt != nil && page.emotions?.completion?.isReady == false)
        #expect(page.domains?.completion?.stack == .domains && page.domains?.completion?.isReady == true)
        #expect(page.domains?.completion?.pulledAt == nil)
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh AchievementsMappingTests`
Expected: the build fails with `error: value of type 'AchievementsPayload' has no member 'model'`.

- [ ] **Step 3: Write the display model**

Create `Pebbles/Features/Achievements/Data/AchievementsPage.swift`:

```swift
import Foundation

/// The cards whose "seen" the server keeps (D10); the raw values are `mark_achievements_seen`'s.
enum AchievementCard: String, CaseIterable, Sendable {
    case explorer, emotions, domains, pebbles, year
}

/// The achievements page as the views draw it (#1306, spec §4): the payload's
/// values in Swift types, each unknown enum on its safe fallback (D17). Nothing
/// here is counted, compared, ordered or picked: `AchievementsMapping` is the
/// one mapping, and it is one-to-one. Android: `AchievementsPage.kt`.
struct AchievementsPage: Equatable {
    var since: CalendarDay?
    var daysKept: Int?
    var explorer: Explorer?
    var emotions: Emotions?
    var domains: Domains?
    var pebbles: Ladder?
    var glyphsCarved: Ladder?
    var glyphSales: Ladder?
    var years: [Year]
}

extension AchievementsPage {
    /// A crossing pebble (D15).
    struct Pebble: Equatable {
        let momentId: UUID?
        let name: String?
        let happenedAt: Date?
        let emotionId: String?
        let valence: Valence
    }

    /// The Explorer wheel's rings, centre out (D6). The raw values are `rings_done`'s.
    enum Ring: String, CaseIterable {
        case firstFive = "first_five"
        case sixFirsts = "six_firsts"
        case twentyFive = "twenty_five"
        case domains, emotions
    }

    /// The six firsts (D5), in the server's order.
    enum StoneKind: String, CaseIterable {
        case small, medium, large, lowlight, neutral, highlight
    }

    /// A set whose completion can be pulled (D9); the raw values are `pull_ceremony`'s.
    enum CeremonyStack: String, CaseIterable, Sendable {
        case emotions, domains
        case sixFirsts = "six_firsts"
    }

    struct FirstFive: Equatable {
        /// Nil for a slug this build does not know: its seat draws as a ghost and its row is left out.
        let task: HomeChecklist.Task?
        let done: Bool
        let on: CalendarDay?
        let subjectName: String?
        let subjectKind: String?
        let subjectId: UUID?
        let isNew: Bool
    }

    struct SixFirst: Equatable {
        /// Nil for a kind this build does not know: its seat draws, its row is left out.
        let kind: StoneKind?
        let done: Bool
        let pebble: Pebble?
        let isNew: Bool
    }

    struct TwentyFive: Equatable {
        /// Capped at 25 by the server.
        let count: Int
        let done: Bool
        let pebble: Pebble?
        let isNew: Bool
    }

    /// A set made whole (D9). `isReady` is the server's: no clock is compared here.
    struct Completion: Equatable {
        let stack: CeremonyStack
        let completedAt: Date?
        let offeredFrom: Date?
        let isReady: Bool
        let pulledAt: Date?
    }

    struct Explorer: Equatable {
        let firstPebble: Pebble?
        let firstFive: [FirstFive]
        let sixFirsts: [SixFirst]
        let twentyFive: TwentyFive?
        let ringsDone: Set<Ring>
        let completion: Completion?
    }

    struct Seat: Equatable {
        let seat: Int
        let emotionId: String
        /// The family's slug; one this build cannot resolve draws in ink.
        let family: String
        let met: Bool
        let pebble: Pebble?
        let isNew: Bool
    }

    struct Emotions: Equatable {
        let count: Int
        let of: Int
        /// In seat order (wheel order, then slug), as the server seats them.
        let seats: [Seat]
        let completion: Completion?
    }

    /// D11's tiers. `unknown` is a tier this build does not know, which says nothing.
    enum Coverage: String {
        case none, few, third, half, most, all, unknown
    }

    struct DomainEntry: Equatable {
        let domainId: String
        let pebble: Pebble?
        let isNew: Bool
    }

    struct Domains: Equatable {
        let count: Int
        let of: Int
        let coverage: Coverage
        /// Newest first, as served: the register's order.
        let entered: [DomainEntry]
        let notYet: [String]
        let completion: Completion?

        /// The order they came (the wheel's ring and the band): the served list read backwards.
        var arrival: [DomainEntry] { entered.reversed() }
    }

    struct Level: Equatable {
        let threshold: Int
        let on: CalendarDay?
        let pebble: Pebble?
        let isNew: Bool
    }

    struct Next: Equatable {
        let threshold: Int
        let left: Int
        /// Toward `threshold` from the level before it, in [0, 1].
        let progress: Double
    }

    struct Ladder: Equatable {
        let count: Int
        let levels: [Level]
        let next: Next?
        /// The steps the figure draws, one stage each.
        let ladder: [Int]
        let thenEvery: Int?

        /// Whether the server lists `threshold` among the levels reached.
        func isReached(_ threshold: Int) -> Bool {
            levels.contains { $0.threshold == threshold }
        }

        /// The ladder's steps the server lists as reached, in ladder order.
        var reachedSteps: [Int] { ladder.filter(isReached) }
    }

    enum MonthState: String {
        case upcoming, quiet, kept
    }

    struct Month: Equatable {
        let month: Int
        let state: MonthState
        let family: String?
        let momentDays: Int
        let pebbleDays: Int
        let fullWeeks: Int
        let weeksFilled: Int
        /// 0 at the centre, 1 on the inner disc, 2 on the rim.
        let level: Int
        /// Toward the next level, in [0, 1]; 0 on the rim.
        let progress: Double

        /// Drawn quietly: a month with no pebble to colour it (NOTES, "quieter disc").
        var isMuted: Bool { state != .kept || family == nil }
    }

    enum Needs: String {
        case moments, pebbles
    }

    struct NextStep: Equatable {
        let month: Int
        let needs: Needs
        let daysLeft: Int
        let reachable: Bool
    }

    struct Year: Equatable, Identifiable {
        var id: Int { year }
        let year: Int
        let isCurrent: Bool
        let months: [Month]
        /// The table's months, newest first, as served.
        let listed: [Int]
        let nextStep: NextStep?

        func month(_ number: Int) -> Month? {
            months.first { $0.month == number }
        }
    }
}
```

- [ ] **Step 4: Write the mapping**

Create `Pebbles/Features/Achievements/Data/AchievementsMapping.swift`:

```swift
import Foundation

/// `get_achievements` → `AchievementsPage` (#1306): pure and one-to-one. It
/// renames, types and clamps (a level to 0…2 and a progress to 0…1, the
/// drawing's bounds) and puts each unknown enum on its fallback (D17): an unknown
/// month state is `quiet`, an unknown tier says nothing, an unknown step, kind,
/// ring or task is left out. It never counts, compares, sorts or picks.
enum AchievementsMapping {
    typealias Wire = AchievementsPayload
    typealias Page = AchievementsPage

    static func page(_ payload: Wire) -> Page {
        Page(since: payload.since,
             daysKept: payload.daysKept,
             explorer: payload.explorer.map(explorer),
             emotions: payload.emotions.map(emotions),
             domains: payload.domains.map(domains),
             pebbles: payload.pebbles.map(ladder),
             glyphsCarved: payload.glyphsCarved.map(ladder),
             glyphSales: payload.glyphSales.map(ladder),
             years: (payload.years ?? []).map(year))
    }

    static func pebble(_ pebble: Wire.Pebble) -> Page.Pebble {
        Page.Pebble(momentId: pebble.momentId, name: pebble.name, happenedAt: pebble.happenedAt,
                    emotionId: pebble.emotionId,
                    valence: Valence.from(positiveness: pebble.positiveness, intensity: pebble.intensity)
                        ?? .neutralMedium)
    }

    static func completion(_ completion: Wire.Completion?, _ stack: Page.CeremonyStack) -> Page.Completion? {
        completion.map {
            Page.Completion(stack: stack, completedAt: $0.completedAt, offeredFrom: $0.offeredFrom,
                            isReady: $0.ready, pulledAt: $0.pulledAt)
        }
    }

    static func explorer(_ wire: Wire.Explorer) -> Page.Explorer {
        Page.Explorer(
            firstPebble: wire.firstPebble.map(pebble),
            firstFive: wire.firstFive.map { item in
                Page.FirstFive(task: HomeChecklist.Task.allCases.first { $0.slug == item.slug },
                               done: item.done, on: item.on, subjectName: item.subject?.name,
                               subjectKind: item.subject?.kind, subjectId: item.subject?.id, isNew: item.isNew)
            },
            sixFirsts: wire.sixFirsts.map {
                Page.SixFirst(kind: Page.StoneKind(rawValue: $0.kind), done: $0.done,
                              pebble: $0.pebble.map(pebble), isNew: $0.isNew)
            },
            twentyFive: wire.twentyFive.map {
                Page.TwentyFive(count: $0.count, done: $0.done, pebble: $0.pebble.map(pebble), isNew: $0.isNew)
            },
            ringsDone: Set(wire.ringsDone.compactMap(Page.Ring.init(rawValue:))),
            completion: completion(wire.completion, .sixFirsts))
    }

    static func emotions(_ wire: Wire.Emotions) -> Page.Emotions {
        Page.Emotions(
            count: wire.count, of: wire.of,
            seats: wire.seats.map {
                Page.Seat(seat: $0.seat, emotionId: $0.emotionId, family: $0.family, met: $0.met,
                          pebble: $0.pebble.map(pebble), isNew: $0.isNew)
            },
            completion: completion(wire.completion, .emotions))
    }

    static func domains(_ wire: Wire.Domains) -> Page.Domains {
        Page.Domains(
            count: wire.count, of: wire.of,
            coverage: Page.Coverage(rawValue: wire.coverage) ?? .unknown,
            entered: wire.entered.map {
                Page.DomainEntry(domainId: $0.domainId, pebble: $0.pebble.map(pebble), isNew: $0.isNew)
            },
            notYet: wire.notYet,
            completion: completion(wire.completion, .domains))
    }

    static func ladder(_ wire: Wire.Ladder) -> Page.Ladder {
        Page.Ladder(
            count: wire.count,
            levels: wire.levels.map {
                Page.Level(threshold: $0.threshold, on: $0.on, pebble: $0.pebble.map(pebble), isNew: $0.isNew)
            },
            next: wire.next.map { Page.Next(threshold: $0.threshold, left: $0.left, progress: unit($0.progress)) },
            ladder: wire.ladder,
            thenEvery: wire.thenEvery)
    }

    static func month(_ wire: Wire.Month) -> Page.Month {
        Page.Month(month: wire.month, state: Page.MonthState(rawValue: wire.state) ?? .quiet, family: wire.family,
                   momentDays: wire.momentDays, pebbleDays: wire.pebbleDays, fullWeeks: wire.fullWeeks,
                   weeksFilled: wire.weeksFilled, level: min(max(wire.level, 0), 2), progress: unit(wire.progress))
    }

    static func year(_ wire: Wire.Year) -> Page.Year {
        Page.Year(
            year: wire.year, isCurrent: wire.current, months: wire.months.map(month), listed: wire.listed,
            nextStep: wire.nextStep.flatMap { step in
                Page.Needs(rawValue: step.needs).map {
                    Page.NextStep(month: step.month, needs: $0, daysLeft: step.daysLeft, reachable: step.reachable)
                }
            })
    }

    /// Clamped to [0, 1]: a fraction the drawing can place.
    private static func unit(_ value: Double) -> Double {
        min(max(value, 0), 1)
    }
}

extension AchievementsPayload {
    /// The page the views draw.
    var model: AchievementsPage { AchievementsMapping.page(self) }
}
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh AchievementsMappingTests`
Expected: `** TEST SUCCEEDED **`, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements/Data apps/ios/PebblesTests/Achievements/AchievementsMappingTests.swift
git commit -m "feat(ios): map the achievements payload to the page model"
```

---

### Task 4: The page's words

**Files:**
- Modify: `Pebbles/Features/Path/Spine/Logic/SpineDates.swift` (two templates)
- Modify: `Pebbles/Resources/Localizable.xcstrings` (through the helper)
- Create: `Pebbles/Features/Achievements/Copy/AchievementsCopy.swift`
- Create: `PebblesTests/Achievements/AchievementsCopyTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `PebblesTests/Achievements/AchievementsCopyTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// The page's words in English and French (#1306, spec §5): semantic values in,
/// sentences out, plurals and number words by the catalog and `NumberWords`.
@Suite("Achievements copy")
struct AchievementsCopyTests {
    static let en = Locale(identifier: "en")
    static let fr = Locale(identifier: "fr")
    static var paris: Calendar {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris")!
        return calendar
    }

    typealias Page = AchievementsPage

    static func ladder(count: Int, reached: [Int], next: Page.Next?) -> Page.Ladder {
        Page.Ladder(count: count, levels: reached.map { Page.Level(threshold: $0, on: nil, pebble: nil, isNew: false) },
                    next: next, ladder: [1, 10, 25, 50, 100, 250, 500, 1000], thenEvery: 500)
    }

    @Test("the header: since a full date, then days, singular on the first")
    func header() {
        let day = CalendarDay(year: 2026, month: 3, day: 1)
        #expect(AchievementsCopy.since(day, calendar: Self.paris, locale: Self.en, dateLocale: Self.en)
            == "since Sunday, March 1, 2026")
        #expect(AchievementsCopy.since(day, calendar: Self.paris, locale: Self.fr, dateLocale: Self.fr)
            == "depuis le dimanche 1 mars 2026")
        #expect(AchievementsCopy.daysUnit(249, locale: Self.en) == "days")
        #expect(AchievementsCopy.daysUnit(1, locale: Self.en) == "day")
        #expect(AchievementsCopy.daysUnit(249, locale: Self.fr) == "jours")
        #expect(AchievementsCopy.daysUnit(1, locale: Self.fr) == "jour")
    }

    @Test("the cards' titles and hints")
    func cards() throws {
        #expect(AchievementCard.allCases.map { AchievementsCopy.title($0, locale: Self.en) }
            == ["Explorer", "Emotions", "Domains", "Pebbles", "The year"])
        #expect(AchievementCard.allCases.map { AchievementsCopy.title($0, locale: Self.fr) }
            == ["Explorateur", "Émotions", "Domaines", "Galets", "L'année"])
        let first = try #require(ISO8601Flexible.parse("2026-03-02T09:30:00Z"))
        #expect(AchievementsCopy.sinceHint(first, calendar: Self.paris, locale: Self.en, dateLocale: Self.en)
            == "since March 2")
        #expect(AchievementsCopy.sinceHint(first, calendar: Self.paris, locale: Self.fr, dateLocale: Self.fr)
            == "depuis le 2 mars")
        #expect(AchievementsCopy.domainsHint(locale: Self.fr) == "dans l'ordre d'arrivée")
        let page = try AchievementsFixtures.payload("design-fixture").model
        #expect(AchievementsCopy.yearHint(try #require(page.years.first), locale: Self.en) == "in progress")
    }

    @Test("a register date is the locale's short day and month")
    func shortDate() throws {
        let first = try #require(ISO8601Flexible.parse("2026-03-02T09:30:00Z"))
        #expect(SpineDates.shortDayMonth(first, calendar: Self.paris, locale: Self.en) == "Mar 2")
        #expect(SpineDates.shortDayMonth(first, calendar: Self.paris, locale: Self.fr) == "2 mars")
    }

    @Test("the coverage tier says what it means; an unknown tier says nothing")
    func coverage() {
        #expect(AchievementsCopy.coverage(.most, locale: Self.en)
            == "Your pebbles reach most parts of your life. Your insights have plenty to read.")
        #expect(AchievementsCopy.coverage(.most, locale: Self.fr)
            == "Tes galets touchent la plupart des parts de ta vie. Tes aperçus ont de quoi lire.")
        #expect(AchievementsCopy.coverage(.none, locale: Self.en)?.hasPrefix("Link your pebbles") == true)
        #expect(AchievementsCopy.coverage(.unknown, locale: Self.en) == nil)
    }

    @Test("the pebbles line counts the stones the server lists and names the one that rises")
    func pebblesLine() throws {
        let design = try #require(try AchievementsFixtures.payload("design-fixture").model.pebbles)
        #expect(AchievementsCopy.pebblesLine(design, locale: Self.en)
            == "137 pebbles. Five stones stand; the sixth rises toward 250.")
        #expect(AchievementsCopy.pebblesLine(design, locale: Self.fr)
            == "137 galets. Cinq pierres se dressent ; la sixième s'élève vers 250.")
        let one = Self.ladder(count: 1, reached: [1], next: Page.Next(threshold: 10, left: 9, progress: 0))
        #expect(AchievementsCopy.pebblesLine(one, locale: Self.en) == "1 pebble. One stone stands; the second rises toward 10.")
        #expect(AchievementsCopy.pebblesLine(one, locale: Self.fr)
            == "1 galet. Une pierre se dresse ; la deuxième s'élève vers 10.")
        let none = Self.ladder(count: 0, reached: [], next: Page.Next(threshold: 1, left: 1, progress: 0))
        #expect(AchievementsCopy.pebblesLine(none, locale: Self.en)
            == "0 pebbles. No stone stands yet; the first rises toward 1.")
        #expect(AchievementsCopy.pebblesLine(none, locale: Self.fr)
            == "0 galet. Aucune pierre ne se dresse encore ; la première s'élève vers 1.")
        let all = Self.ladder(count: 1137, reached: [1, 10, 25, 50, 100, 250, 500, 1000],
                              next: Page.Next(threshold: 1500, left: 363, progress: 0.274))
        #expect(AchievementsCopy.pebblesLine(all, locale: Self.en)
            == "1,137 pebbles. Every stone stands; the next mark is 1,500.")
        let n = { AchievementsCopy.number($0, locale: Self.fr) }
        #expect(AchievementsCopy.pebblesLine(all, locale: Self.fr)
            == "\(n(1137)) galets. Toutes les pierres se dressent ; le prochain cap est \(n(1500)).")
        #expect(AchievementsCopy.pebblesLead(design, locale: Self.en) == "137 pebbles kept.")
        #expect(AchievementsCopy.pebblesLead(design, locale: Self.fr) == "137 galets gardés.")
    }

    @Test("the year's next step, by what it needs, how many days are left and whether it can still be reached")
    func nextStep() {
        let moments = Page.NextStep(month: 11, needs: .moments, daysLeft: 12, reachable: true)
        #expect(AchievementsCopy.nextStep(moments, locale: Self.en)
            == "Twelve more days with a moment, one in each full week, bring November to the inner disc.")
        #expect(AchievementsCopy.nextStep(moments, locale: Self.fr)
            == "Douze jours de plus avec un moment, un dans chaque semaine complète : novembre rejoindra le disque intérieur.")
        let one = Page.NextStep(month: 4, needs: .pebbles, daysLeft: 1, reachable: true)
        #expect(AchievementsCopy.nextStep(one, locale: Self.en)
            == "One more day with a pebble, and one in each full week, bring April to the rim.")
        #expect(AchievementsCopy.nextStep(one, locale: Self.fr)
            == "Un jour de plus avec un galet, et un dans chaque semaine complète : avril atteindra le bord.")
        let keep = Page.NextStep(month: 4, needs: .pebbles, daysLeft: 0, reachable: true)
        #expect(AchievementsCopy.nextStep(keep, locale: Self.en) == "Keep a pebble in every full week and April reaches the rim.")
        let late = Page.NextStep(month: 11, needs: .moments, daysLeft: 3, reachable: false)
        #expect(AchievementsCopy.nextStep(late, locale: Self.en)
            == "In November, a full week passed without a moment, so the month stays at the centre.")
        #expect(AchievementsCopy.nextStep(late, locale: Self.fr)
            == "En novembre, une semaine complète est passée sans moment : le mois reste au centre.")
        #expect(AchievementsCopy.monthHeading(11, locale: Self.fr) == "Novembre")
        #expect(AchievementsCopy.monthInSentence(13, locale: Self.en) == "13")
    }

    @Test("a pulled ceremony's card names its stack and the karma it paid")
    func ceremony() {
        let card = AchievementsCopy.ceremonyCard(.emotions, karma: 40, locale: Self.en)
        #expect(card == AchievementMomentCard(id: "complete-emotions", title: "Every emotion met",
                                              description: "Your pebbles have carried every feeling on the wheel.",
                                              karmaGranted: 40))
        #expect(AchievementsCopy.ceremonyTitle(.sixFirsts, locale: Self.fr) == "Les six premières fois")
        #expect(AchievementsCopy.ceremonySlug(.sixFirsts) == "complete-six-firsts")
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh AchievementsCopyTests`
Expected: the build fails with `error: cannot find 'AchievementsCopy' in scope`.

- [ ] **Step 3: Add the two date templates**

In `Pebbles/Features/Path/Spine/Logic/SpineDates.swift`, after `dayMonth(_:calendar:locale:)`, add:

```swift
    /// "Sunday, March 1, 2026", "dimanche 1 mars 2026": the achievements header (#1306).
    static func fullDate(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        format(date, "EEEEdMMMMy", calendar, locale)
    }

    /// "Mar 2", "2 mars": a register row's date (#1306).
    static func shortDayMonth(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        format(date, "dMMM", calendar, locale)
    }
```

- [ ] **Step 4: Add the strings**

Create `apps/ios/build/strings-task4.json`:

```json
{
  "achievements.header.since": {"en": "since %@", "fr": "depuis le %@"},
  "achievements.header.days": {"word": true, "en": {"one": "day", "other": "days"}, "fr": {"one": "jour", "many": "jours", "other": "jours"}},
  "achievements.card.explorer": {"en": "Explorer", "fr": "Explorateur"},
  "achievements.card.emotions": {"en": "Emotions", "fr": "Émotions"},
  "achievements.card.domains": {"en": "Domains", "fr": "Domaines"},
  "achievements.card.pebbles": {"en": "Pebbles", "fr": "Galets"},
  "achievements.card.year": {"en": "The year", "fr": "L'année"},
  "achievements.hint.explorer": {"en": "five rings", "fr": "cinq anneaux"},
  "achievements.hint.since": {"en": "since %@", "fr": "depuis le %@"},
  "achievements.hint.domains": {"en": "in the order they came", "fr": "dans l'ordre d'arrivée"},
  "achievements.hint.inProgress": {"en": "in progress", "fr": "en cours"},
  "achievements.coverage.none": {"en": "Link your pebbles to the parts of your life they touch, and your insights will start reading them.", "fr": "Relie tes galets aux parts de ta vie qu'ils touchent, et tes aperçus commenceront à les lire."},
  "achievements.coverage.few": {"en": "You haven't linked many pebbles to domains yet, so your insights have little to go on.", "fr": "Tu as encore relié peu de galets à des domaines : tes aperçus ont peu de matière."},
  "achievements.coverage.third": {"en": "Your pebbles touch a third of your life. Link a few more domains and your insights will see further.", "fr": "Tes galets touchent un tiers de ta vie. Relie quelques domaines de plus et tes aperçus verront plus loin."},
  "achievements.coverage.half": {"en": "You've already linked pebbles with half of the domains. What a rich life!", "fr": "Tu as déjà relié des galets à la moitié des domaines. Quelle vie riche !"},
  "achievements.coverage.most": {"en": "Your pebbles reach most parts of your life. Your insights have plenty to read.", "fr": "Tes galets touchent la plupart des parts de ta vie. Tes aperçus ont de quoi lire."},
  "achievements.coverage.all": {"en": "Every part of your life has a pebble in it. Your insights see the whole picture.", "fr": "Chaque part de ta vie a son galet. Tes aperçus voient tout le tableau."},
  "achievements.pebbles.kept": {"word": true, "template": {"en": "%2$@ %1$#@body@.", "fr": "%2$@ %1$#@body@."}, "en": {"one": "pebble", "other": "pebbles"}, "fr": {"one": "galet", "many": "galets", "other": "galets"}},
  "achievements.pebbles.keptLead": {"word": true, "template": {"en": "%2$@ %1$#@body@.", "fr": "%2$@ %1$#@body@."}, "en": {"one": "pebble kept", "other": "pebbles kept"}, "fr": {"one": "galet gardé", "many": "galets gardés", "other": "galets gardés"}},
  "achievements.pebbles.none": {"en": "No stone stands yet; the first rises toward %@.", "fr": "Aucune pierre ne se dresse encore ; la première s'élève vers %@."},
  "achievements.pebbles.one": {"en": "One stone stands; the %1$@ rises toward %2$@.", "fr": "Une pierre se dresse ; la %1$@ s'élève vers %2$@."},
  "achievements.pebbles.some": {"en": "%1$@ stones stand; the %2$@ rises toward %3$@.", "fr": "%1$@ pierres se dressent ; la %2$@ s'élève vers %3$@."},
  "achievements.pebbles.all": {"en": "Every stone stands; the next mark is %@.", "fr": "Toutes les pierres se dressent ; le prochain cap est %@."},
  "achievements.pebbles.top": {"en": "Every stone stands.", "fr": "Toutes les pierres se dressent."},
  "achievements.year.moments.some": {"en": "%1$@ more days with a moment, one in each full week, bring %2$@ to the inner disc.", "fr": "%1$@ jours de plus avec un moment, un dans chaque semaine complète : %2$@ rejoindra le disque intérieur."},
  "achievements.year.moments.one": {"en": "One more day with a moment, and one in each full week, bring %@ to the inner disc.", "fr": "Un jour de plus avec un moment, et un dans chaque semaine complète : %@ rejoindra le disque intérieur."},
  "achievements.year.moments.keep": {"en": "Keep a moment in every full week and %@ reaches the inner disc.", "fr": "Garde un moment dans chaque semaine complète et %@ rejoindra le disque intérieur."},
  "achievements.year.moments.unreachable": {"en": "In %@, a full week passed without a moment, so the month stays at the centre.", "fr": "En %@, une semaine complète est passée sans moment : le mois reste au centre."},
  "achievements.year.pebbles.some": {"en": "%1$@ more days with a pebble, one in each full week, bring %2$@ to the rim.", "fr": "%1$@ jours de plus avec un galet, un dans chaque semaine complète : %2$@ atteindra le bord."},
  "achievements.year.pebbles.one": {"en": "One more day with a pebble, and one in each full week, bring %@ to the rim.", "fr": "Un jour de plus avec un galet, et un dans chaque semaine complète : %@ atteindra le bord."},
  "achievements.year.pebbles.keep": {"en": "Keep a pebble in every full week and %@ reaches the rim.", "fr": "Garde un galet dans chaque semaine complète et %@ atteindra le bord."},
  "achievements.year.pebbles.unreachable": {"en": "In %@, a full week passed without a pebble, so the month stays on the inner disc.", "fr": "En %@, une semaine complète est passée sans galet : le mois reste sur le disque intérieur."},
  "achievements.ceremony.emotions.title": {"en": "Every emotion met", "fr": "Toutes les émotions rencontrées"},
  "achievements.ceremony.emotions.body": {"en": "Your pebbles have carried every feeling on the wheel.", "fr": "Tes galets ont porté chaque émotion de la roue."},
  "achievements.ceremony.domains.title": {"en": "Every domain entered", "fr": "Tous les domaines explorés"},
  "achievements.ceremony.domains.body": {"en": "Every part of your life has a pebble in it.", "fr": "Chaque part de ta vie a son galet."},
  "achievements.ceremony.six_firsts.title": {"en": "The six firsts", "fr": "Les six premières fois"},
  "achievements.ceremony.six_firsts.body": {"en": "Small, medium and large; lowlight, neutral and highlight.", "fr": "Petit, moyen et grand ; creux, neutre et temps fort."},
  "achievements.error.title": {"en": "Couldn't load your achievements", "fr": "Impossible de charger tes succès"},
  "achievements.error.body": {"en": "Check your connection and try again.", "fr": "Vérifie ta connexion et réessaie."},
  "achievements.error.retry": {"en": "Try again", "fr": "Réessayer"}
}
```

Run: `python3 -I apps/ios/build/xcstrings-add.py apps/ios/Pebbles/Resources/Localizable.xcstrings apps/ios/build/strings-task4.json`
Expected: `added 41 entries`.

- [ ] **Step 5: Write the copy**

Create `Pebbles/Features/Achievements/Copy/AchievementsCopy.swift`:

```swift
import Foundation

/// The achievements page's words (#1306, spec §5): the one place the payload's
/// numbers, tiers and steps become sentences, over `Localizable.xcstrings`'
/// `achievements.*` keys. The language is passed in (views pass
/// `SpineStrings.appLocale`, dates `SpineStrings.dateLocale`), so the tests pin
/// `en` and `fr`. Plurals are the catalog's, number words `NumberWords`'.
/// Choosing a sentence by how many stages stand is wording, not counting: the
/// stages are the ones the server lists. Android: `AchievementsCopy.kt`.
enum AchievementsCopy {
    typealias Page = AchievementsPage

    // MARK: - The header

    /// "since Sunday, March 1, 2026" (the header's small caps set it).
    static func since(_ day: CalendarDay, calendar: Calendar, locale: Locale, dateLocale: Locale) -> String {
        let date = day.date(in: calendar.timeZone) ?? .distantPast
        return format("achievements.header.since", locale,
                      SpineDates.fullDate(date, calendar: calendar, locale: dateLocale))
    }

    /// "days", or "day" on the first one.
    static func daysUnit(_ days: Int, locale: Locale) -> String {
        format("achievements.header.days", locale, days)
    }

    // MARK: - The cards

    static func title(_ card: AchievementCard, locale: Locale) -> String {
        SpineStrings.string("achievements.card.\(card.rawValue)", locale: locale)
    }

    static func explorerHint(locale: Locale) -> String {
        SpineStrings.string("achievements.hint.explorer", locale: locale)
    }

    /// "since March 2": the first pebble's day, which opened the emotions and the pebbles.
    static func sinceHint(_ date: Date, calendar: Calendar, locale: Locale, dateLocale: Locale) -> String {
        format("achievements.hint.since", locale, SpineDates.dayMonth(date, calendar: calendar, locale: dateLocale))
    }

    static func domainsHint(locale: Locale) -> String {
        SpineStrings.string("achievements.hint.domains", locale: locale)
    }

    /// "in progress" on the current year; nothing on a past one.
    static func yearHint(_ year: Page.Year, locale: Locale) -> String? {
        year.isCurrent ? SpineStrings.string("achievements.hint.inProgress", locale: locale) : nil
    }

    /// What the coverage tier means for the insights (D11); nil for a tier this build does not know.
    static func coverage(_ tier: Page.Coverage, locale: Locale) -> String? {
        tier == .unknown ? nil : SpineStrings.string("achievements.coverage.\(tier.rawValue)", locale: locale)
    }

    // MARK: - Pebbles

    /// "137 pebbles. Five stones stand; the sixth rises toward 250."
    static func pebblesLine(_ ladder: Page.Ladder, locale: Locale) -> String {
        format("achievements.pebbles.kept", locale, ladder.count, number(ladder.count, locale: locale))
            + " " + stones(ladder, locale: locale)
    }

    /// "137 pebbles kept.": the Pebbles sheet's lead.
    static func pebblesLead(_ ladder: Page.Ladder, locale: Locale) -> String {
        format("achievements.pebbles.keptLead", locale, ladder.count, number(ladder.count, locale: locale))
    }

    /// The skyline in words: how many of its stages the server lists as reached,
    /// and the one rising toward `next`.
    static func stones(_ ladder: Page.Ladder, locale: Locale) -> String {
        guard let next = ladder.next else { return SpineStrings.string("achievements.pebbles.top", locale: locale) }
        let target = number(next.threshold, locale: locale)
        let standing = ladder.reachedSteps.count
        if standing >= ladder.ladder.count { return format("achievements.pebbles.all", locale, target) }
        let rising = NumberWords.ordinal(standing + 1, locale: locale, gender: .feminine)
        switch standing {
        case 0:
            return format("achievements.pebbles.none", locale, target)
        case 1:
            return format("achievements.pebbles.one", locale, rising, target)
        default:
            let words = NumberWords.cardinal(standing, locale: locale, gender: .feminine)
            return WeekOpening.sentenceCase(format("achievements.pebbles.some", locale, words, rising, target),
                                            locale: locale)
        }
    }

    /// "1,500", "1 500": a number with the locale's grouping.
    static func number(_ value: Int, locale: Locale) -> String {
        value.formatted(.number.locale(locale))
    }

    // MARK: - The year

    /// "Twelve more days with a moment, one in each full week, bring November to the inner disc."
    static func nextStep(_ step: Page.NextStep, locale: Locale) -> String {
        let month = monthInSentence(step.month, locale: locale)
        let key = "achievements.year.\(step.needs.rawValue)"
        guard step.reachable else { return format("\(key).unreachable", locale, month) }
        switch step.daysLeft {
        case ...0:
            return format("\(key).keep", locale, month)
        case 1:
            return format("\(key).one", locale, month)
        default:
            let words = NumberWords.cardinal(step.daysLeft, locale: locale)
            return WeekOpening.sentenceCase(format("\(key).some", locale, words, month), locale: locale)
        }
    }

    /// "November", "novembre": a month inside a sentence.
    static func monthInSentence(_ month: Int, locale: Locale) -> String {
        let names = gregorian(locale).monthSymbols
        return names.indices.contains(month - 1) ? names[month - 1] : String(month)
    }

    /// "November", "Novembre": a month heading a row.
    static func monthHeading(_ month: Int, locale: Locale) -> String {
        let names = gregorian(locale).standaloneMonthSymbols
        guard names.indices.contains(month - 1) else { return String(month) }
        return WeekOpening.sentenceCase(names[month - 1], locale: locale)
    }

    // MARK: - The ceremony

    static func ceremonyTitle(_ stack: Page.CeremonyStack, locale: Locale) -> String {
        SpineStrings.string("achievements.ceremony.\(stack.rawValue).title", locale: locale)
    }

    static func ceremonyBody(_ stack: Page.CeremonyStack, locale: Locale) -> String {
        SpineStrings.string("achievements.ceremony.\(stack.rawValue).body", locale: locale)
    }

    /// The unlock moment's card for a pulled ceremony, with the karma the pull actually paid.
    static func ceremonyCard(_ stack: Page.CeremonyStack, karma: Int, locale: Locale) -> AchievementMomentCard {
        AchievementMomentCard(id: ceremonySlug(stack), title: ceremonyTitle(stack, locale: locale),
                              description: ceremonyBody(stack, locale: locale), karmaGranted: karma)
    }

    /// The `set_complete` catalog row a stack pays from (backend plan Task 1).
    static func ceremonySlug(_ stack: Page.CeremonyStack) -> String {
        switch stack {
        case .emotions: "complete-emotions"
        case .domains: "complete-domains"
        case .sixFirsts: "complete-six-firsts"
        }
    }

    // MARK: - The error

    static func errorTitle(locale: Locale) -> String { SpineStrings.string("achievements.error.title", locale: locale) }
    static func errorBody(locale: Locale) -> String { SpineStrings.string("achievements.error.body", locale: locale) }
    static func retry(locale: Locale) -> String { SpineStrings.string("achievements.error.retry", locale: locale) }

    // MARK: - Helpers

    /// The format for `key` in `locale`, its plurals resolved by the catalog's variations.
    static func format(_ key: String, _ locale: Locale, _ arguments: CVarArg...) -> String {
        String(format: SpineStrings.string(key, locale: locale), locale: locale, arguments: arguments)
    }

    private static func gregorian(_ locale: Locale) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = locale
        return calendar
    }
}
```

- [ ] **Step 6: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh AchievementsCopyTests`
Expected: `** TEST SUCCEEDED **`, 7 tests. If a French expectation differs only by a space before `;`, `:` or `!`, the catalog value is wrong (the app writes a plain space there), not the test.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Spine/Logic/SpineDates.swift apps/ios/Pebbles/Resources/Localizable.xcstrings \
  apps/ios/Pebbles/Features/Achievements/Copy apps/ios/PebblesTests/Achievements/AchievementsCopyTests.swift
git commit -m "feat(ios): word the achievements page in english and french"
```

---

### Task 5: The service and the kept copy

**Files:**
- Create: `Pebbles/Features/Achievements/Data/AchievementsPageService.swift`
- Create: `PebblesTests/Achievements/AchievementsPageServiceTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `PebblesTests/Achievements/AchievementsPageServiceTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// The achievements page's service (#1306, spec §4): the last body that decoded
/// is kept per owner and zone in the wraps' disk cache, which `SignedOutPurge`
/// already wipes; a pull's empty answer is never success.
@Suite("Achievements page service")
@MainActor
struct AchievementsPageServiceTests {
    private let root = FileManager.default.temporaryDirectory.appendingPathComponent("achievements-\(UUID().uuidString)")

    @Test("a body that decodes is kept for its owner and zone, and reads back as the same page")
    func keeps() async throws {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        let paris = try #require(TimeZone(identifier: "Europe/Paris"))
        let data = try AchievementsFixtures.data("design-fixture")
        let service = AchievementsPageService(client: SupabaseService().client, disk: cache, owner: "mara")
        let page = try service.decodeAndKeep(data, zone: paris, epoch: cache.epoch)
        #expect(page.daysKept == 249)
        #expect(cache.read(AchievementsPageService.cachePage, owner: "mara", zone: paris.identifier) == data)
        #expect(await service.cachedPage(zone: paris) == page)
        let tokyo = try #require(TimeZone(identifier: "Asia/Tokyo"))
        #expect(await service.cachedPage(zone: tokyo) == nil)
    }

    @Test("nothing is kept without an owner, from a body that does not decode, or across a purge")
    func keepsNothingElse() async throws {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        let paris = try #require(TimeZone(identifier: "Europe/Paris"))
        let data = try AchievementsFixtures.data("design-fixture")
        let stranger = AchievementsPageService(client: SupabaseService().client, disk: cache, owner: nil)
        _ = try stranger.decodeAndKeep(data, zone: paris, epoch: cache.epoch)
        #expect(await stranger.cachedPage(zone: paris) == nil)

        let service = AchievementsPageService(client: SupabaseService().client, disk: cache, owner: "mara")
        #expect(throws: DecodingError.self) {
            try service.decodeAndKeep(Data("[]".utf8), zone: paris, epoch: cache.epoch)
        }
        #expect(cache.read(AchievementsPageService.cachePage, owner: "mara", zone: paris.identifier) == nil)

        let asked = cache.epoch
        cache.clear()
        _ = try service.decodeAndKeep(data, zone: paris, epoch: asked)
        #expect(cache.read(AchievementsPageService.cachePage, owner: "mara", zone: paris.identifier) == nil)
    }

    @Test("a kept body this build can no longer decode is a miss")
    func undecodableKept() async throws {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        let paris = try #require(TimeZone(identifier: "Europe/Paris"))
        cache.write(Data("not json".utf8), page: AchievementsPageService.cachePage, owner: "mara", zone: paris.identifier)
        let service = AchievementsPageService(client: SupabaseService().client, disk: cache, owner: "mara")
        #expect(await service.cachedPage(zone: paris) == nil)
    }

    @Test("a pull's row is the karma it paid; no row is never success; not_ready is recognised")
    func pullOutcome() throws {
        let rows = try JSONDecoder().decode([AchievementsPageService.PullRow].self,
                                            from: Data(#"[{"stack": "six_firsts", "karma_granted": 30}]"#.utf8))
        #expect(AchievementsPageService.outcome(rows) == .pulled(karma: 30))
        #expect(AchievementsPageService.outcome([]) == .alreadyPulled)
        #expect(AchievementsPageService.isNotReady("not_ready"))
        #expect(!AchievementsPageService.isNotReady("invalid_stack"))
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh AchievementsPageServiceTests`
Expected: the build fails with `error: cannot find 'AchievementsPageService' in scope`.

- [ ] **Step 3: Write the service**

Create `Pebbles/Features/Achievements/Data/AchievementsPageService.swift`:

```swift
import Foundation
import os
import Supabase

/// What a ceremony pull returned (spec §3.4).
enum CeremonyPull: Equatable, Sendable {
    case pulled(karma: Int)
    /// No row: the ceremony was already pulled. Never shown as success.
    case alreadyPulled
}

enum CeremonyPullError: Error, Equatable {
    /// `not_ready`: before `offered_from`, or a set never completed.
    case notReady
}

/// The seam `AchievementsModel`'s tests fake (#1306).
protocol AchievementsPageServicing: Sendable {
    func page(zone: TimeZone) async throws -> AchievementsPage
    /// The device's last copy, or nil: display only, never instead of a fetch.
    func cachedPage(zone: TimeZone) async -> AchievementsPage?
    func markSeen(_ card: AchievementCard) async throws
    func pull(_ stack: AchievementsPage.CeremonyStack, zone: TimeZone) async throws -> CeremonyPull
}

/// The achievements page's reads and writes (#1306): `get_achievements`,
/// `mark_achievements_seen` and `pull_ceremony` (backend plan Tasks 9 and 12).
/// It calls, decodes and maps; the server computes. Each body that decodes is
/// kept for its owner and zone in the wraps' disk cache (`WrapDiskCache`,
/// private journal content in Caches, wiped by `SignedOutPurge`), so the next
/// visit draws at once while the read runs (stale-while-revalidate, spec §4).
/// The purge epoch is taken before the request, so a body landing after a
/// sign-out is not kept. Errors propagate; the model owns the page's state.
struct AchievementsPageService: AchievementsPageServicing {
    /// The kept body's page name in the cache.
    static let cachePage = "achievements"

    let client: SupabaseClient
    var disk: WrapDiskCache?
    /// The signed-in user's id; nothing is kept or read without one.
    var owner: String?

    func page(zone: TimeZone) async throws -> AchievementsPage {
        let epoch = disk?.epoch
        let data = try await client.rpc("get_achievements", params: ["p_tz": zone.identifier]).execute().data
        return try decodeAndKeep(data, zone: zone, epoch: epoch)
    }

    func cachedPage(zone: TimeZone) async -> AchievementsPage? {
        guard let disk, let owner, let data = disk.read(Self.cachePage, owner: owner, zone: zone.identifier) else {
            return nil
        }
        do {
            return try WrapDecoding.decode(AchievementsPayload.self, from: data).model
        } catch {
            Self.logger.error("a kept achievements page no longer decodes: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }

    func markSeen(_ card: AchievementCard) async throws {
        _ = try await client.rpc("mark_achievements_seen", params: ["p_card": card.rawValue]).execute()
    }

    func pull(_ stack: AchievementsPage.CeremonyStack, zone: TimeZone) async throws -> CeremonyPull {
        do {
            let rows: [PullRow] = try await client
                .rpc("pull_ceremony", params: ["p_stack": stack.rawValue, "p_tz": zone.identifier])
                .execute()
                .value
            return Self.outcome(rows)
        } catch let error as PostgrestError where Self.isNotReady(error.message) {
            throw CeremonyPullError.notReady
        }
    }

    /// Decodes `data`, logs any section it had to drop, and keeps the body once it decoded.
    func decodeAndKeep(_ data: Data, zone: TimeZone, epoch: Int?) throws -> AchievementsPage {
        let payload = try WrapDecoding.decode(AchievementsPayload.self, from: data)
        if !payload.skipped.isEmpty {
            Self.logger.error(
                "get_achievements sections dropped: \(payload.skipped.joined(separator: ","), privacy: .public)")
        }
        if let disk, let owner {
            disk.write(data, page: Self.cachePage, owner: owner, zone: zone.identifier, epoch: epoch)
        }
        return payload.model
    }

    /// One row of `pull_ceremony`.
    struct PullRow: Decodable, Equatable {
        let stack: String
        let karmaGranted: Int

        enum CodingKeys: String, CodingKey {
            case stack
            case karmaGranted = "karma_granted"
        }
    }

    static func outcome(_ rows: [PullRow]) -> CeremonyPull {
        rows.first.map { .pulled(karma: $0.karmaGranted) } ?? .alreadyPulled
    }

    static func isNotReady(_ message: String) -> Bool {
        message.contains("not_ready")
    }

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "achievements-page")
}
```

- [ ] **Step 4: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh AchievementsPageServiceTests`
Expected: `** TEST SUCCEEDED **`, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements/Data/AchievementsPageService.swift \
  apps/ios/PebblesTests/Achievements/AchievementsPageServiceTests.swift
git commit -m "feat(ios): read, keep and write the achievements page through its rpcs"
```

---

### Task 6: The model: stale-while-revalidate, seen marks, the pull

**Files:**
- Create: `Pebbles/Features/Achievements/Data/AchievementsModel.swift`
- Create: `PebblesTests/Achievements/FakeAchievementsPageService.swift`
- Create: `PebblesTests/Achievements/AchievementsModelTests.swift`

- [ ] **Step 1: Write the fake**

Create `PebblesTests/Achievements/FakeAchievementsPageService.swift`:

```swift
import Foundation
@testable import Pebbles

/// `AchievementsPageServicing` for `AchievementsModel`'s tests (#1306). The page
/// answers are consumed in order, the last one repeating; every call is logged.
final class FakeAchievementsPageService: AchievementsPageServicing, @unchecked Sendable {
    struct Failure: Error {}

    private let lock = NSLock()
    private var answers: [Result<AchievementsPage, Error>]
    private let keptPage: AchievementsPage?
    private var pullAnswer: Result<CeremonyPull, Error> = .failure(Failure())
    private var log: [String] = []
    private var seenCards: [AchievementCard] = []

    init(kept: AchievementsPage? = nil, answers: [Result<AchievementsPage, Error>] = []) {
        keptPage = kept
        self.answers = answers
    }

    var calls: [String] { lock.withLock { log } }
    var seen: [AchievementCard] { lock.withLock { seenCards } }

    func record(_ entry: String) {
        lock.withLock { log.append(entry) }
    }

    func answer(pull: Result<CeremonyPull, Error>) {
        lock.withLock { pullAnswer = pull }
    }

    func page(zone: TimeZone) async throws -> AchievementsPage {
        try lock.withLock {
            log.append("read")
            let next = answers.count > 1 ? answers.removeFirst() : (answers.first ?? .failure(Failure()))
            return try next.get()
        }
    }

    func cachedPage(zone: TimeZone) async -> AchievementsPage? {
        lock.withLock {
            log.append("kept")
            return keptPage
        }
    }

    func markSeen(_ card: AchievementCard) async throws {
        lock.withLock { seenCards.append(card) }
    }

    func pull(_ stack: AchievementsPage.CeremonyStack, zone: TimeZone) async throws -> CeremonyPull {
        try lock.withLock {
            log.append("pull \(stack.rawValue)")
            return try pullAnswer.get()
        }
    }
}
```

- [ ] **Step 2: Write the failing tests**

Create `PebblesTests/Achievements/AchievementsModelTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// The page's state (#1306, spec §4): the kept copy at once, the grant, then the
/// read; a failed read keeps what is shown; seen marks fire and forget; a pull
/// is success only when a row came back.
@Suite("Achievements model")
@MainActor
struct AchievementsModelTests {
    static func page(daysKept: Int) throws -> AchievementsPage {
        var page = try AchievementsFixtures.payload("design-fixture").model
        page.daysKept = daysKept
        return page
    }

    static func model(_ fake: FakeAchievementsPageService) -> AchievementsModel {
        let model = AchievementsModel(zone: TimeZone(identifier: "Europe/Paris")!)
        model.service = fake
        return model
    }

    @Test("the kept page shows at once, the grant runs, then the read replaces it")
    func staleWhileRevalidate() async throws {
        let kept = try Self.page(daysKept: 248)
        let fresh = try Self.page(daysKept: 249)
        let fake = FakeAchievementsPageService(kept: kept, answers: [.success(fresh)])
        let model = Self.model(fake)
        var shownDuringGrant: AchievementsPage?
        await model.load {
            shownDuringGrant = model.page
            fake.record("grant")
        }
        #expect(shownDuringGrant == kept)
        #expect(model.page == fresh)
        #expect(fake.calls == ["kept", "grant", "read"])
        #expect(!model.failed && !model.isLoading)
    }

    @Test("a failed read keeps the page on screen and is not an error")
    func failedReadKeepsKept() async throws {
        let kept = try Self.page(daysKept: 248)
        let fake = FakeAchievementsPageService(kept: kept, answers: [.failure(FakeAchievementsPageService.Failure())])
        let model = Self.model(fake)
        await model.load {}
        #expect(model.page == kept)
        #expect(!model.failed)
    }

    @Test("with nothing kept a failed read is the error state, and the next read clears it")
    func failedFirstVisit() async throws {
        let fresh = try Self.page(daysKept: 249)
        let fake = FakeAchievementsPageService(answers: [.failure(FakeAchievementsPageService.Failure()), .success(fresh)])
        let model = Self.model(fake)
        await model.load {}
        #expect(model.page == nil && model.failed)
        await model.load {}
        #expect(model.page == fresh && !model.failed)
    }

    @Test("a page already shown is not replaced by the kept copy on the next visit")
    func secondVisit() async throws {
        let fresh = try Self.page(daysKept: 249)
        let fake = FakeAchievementsPageService(kept: try Self.page(daysKept: 200), answers: [.success(fresh)])
        let model = Self.model(fake)
        await model.load {}
        await model.load {}
        #expect(fake.calls == ["kept", "read", "read"])
        #expect(model.page == fresh)
    }

    @Test("opening a sheet marks its card seen")
    func seen() async {
        let fake = FakeAchievementsPageService()
        let model = Self.model(fake)
        await model.opened(.emotions)?.value
        await model.opened(.year)?.value
        #expect(fake.seen == [.emotions, .year])
    }

    @Test("a ready ceremony pulls once, reports the karma it paid and reads the page again")
    func pull() async throws {
        let fresh = try Self.page(daysKept: 249)
        let fake = FakeAchievementsPageService(answers: [.success(fresh)])
        fake.answer(pull: .success(.pulled(karma: 30)))
        let model = Self.model(fake)
        #expect(await model.pull(.sixFirsts) == .pulled(karma: 30))
        #expect(fake.calls == ["pull six_firsts", "read"])
        #expect(model.page == fresh && model.pulling == nil)
    }

    @Test("no row, not ready and a failure are never success")
    func pullFailures() async {
        let fake = FakeAchievementsPageService()
        let model = Self.model(fake)
        fake.answer(pull: .success(.alreadyPulled))
        #expect(await model.pull(.emotions) == .alreadyPulled)
        fake.answer(pull: .failure(CeremonyPullError.notReady))
        #expect(await model.pull(.emotions) == .notReady)
        fake.answer(pull: .failure(FakeAchievementsPageService.Failure()))
        #expect(await model.pull(.emotions) == .failed)
    }

    @Test("without a service nothing runs")
    func noService() async {
        let model = AchievementsModel(zone: TimeZone(identifier: "Europe/Paris")!)
        await model.load {}
        #expect(model.page == nil && !model.failed)
        #expect(model.opened(.explorer) == nil)
    }
}
```

- [ ] **Step 3: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh AchievementsModelTests`
Expected: the build fails with `error: cannot find 'AchievementsModel' in scope`.

- [ ] **Step 4: Write the model**

Create `Pebbles/Features/Achievements/Data/AchievementsModel.swift`:

```swift
import Foundation
import Observation
import os

/// The achievements page's state (#1306, spec §4). The device's last page shows
/// at once and the read replaces it when it lands (stale-while-revalidate, no
/// blink). A failed read keeps what is shown and logs; only a first visit with
/// nothing kept and no answer shows the error. Opening a sheet marks its card
/// seen, fire-and-forget; a ready mark pulls its ceremony once. The view sets
/// `service`; the tests fake it. Android: `AchievementsViewModel`.
@Observable
@MainActor
final class AchievementsModel {
    enum PullOutcome: Equatable {
        case pulled(karma: Int)
        /// No row came back: the ceremony was already pulled (spec §3.4).
        case alreadyPulled
        case notReady
        case failed
    }

    private(set) var page: AchievementsPage?
    private(set) var isLoading = false
    /// Nothing to show: no kept copy, and the read failed.
    private(set) var failed = false
    /// The stack whose ceremony is being pulled, so its mark cannot fire twice.
    private(set) var pulling: AchievementsPage.CeremonyStack?

    @ObservationIgnored var service: (any AchievementsPageServicing)?
    let zone: TimeZone

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "achievements-page")

    init(zone: TimeZone = SpineCalendar.current.timeZone) {
        self.zone = zone
    }

    /// The kept copy when nothing is shown yet, then `grant` (the screen-open
    /// `check_achievements()`, the retroactive grant), then the read.
    func load(grant: @MainActor () async -> Void) async {
        guard let service, !isLoading else { return }
        isLoading = true
        defer { isLoading = false }
        if page == nil, let kept = await service.cachedPage(zone: zone) { page = kept }
        await grant()
        await refresh(service)
    }

    /// Marks `card` seen (spec §4). The marks on screen stay until the next
    /// read. Returned so the tests can wait for it.
    @discardableResult
    func opened(_ card: AchievementCard) -> Task<Void, Never>? {
        guard let service else { return nil }
        let logger = logger
        return Task {
            do {
                try await service.markSeen(card)
            } catch {
                logger.warning(
                    "mark_achievements_seen(\(card.rawValue, privacy: .public)) failed: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    /// Pulls a ready ceremony (D9), then reads the page again so its mark goes.
    func pull(_ stack: AchievementsPage.CeremonyStack) async -> PullOutcome {
        guard let service, pulling == nil else { return .failed }
        pulling = stack
        defer { pulling = nil }
        let outcome: PullOutcome
        do {
            switch try await service.pull(stack, zone: zone) {
            case .pulled(let karma):
                outcome = .pulled(karma: karma)
            case .alreadyPulled:
                logger.error("pull_ceremony(\(stack.rawValue, privacy: .public)) returned no row")
                outcome = .alreadyPulled
            }
        } catch CeremonyPullError.notReady {
            logger.warning("pull_ceremony(\(stack.rawValue, privacy: .public)) is not ready")
            outcome = .notReady
        } catch {
            logger.error(
                "pull_ceremony(\(stack.rawValue, privacy: .public)) failed: \(error.localizedDescription, privacy: .public)")
            outcome = .failed
        }
        await refresh(service)
        return outcome
    }

    private func refresh(_ service: any AchievementsPageServicing) async {
        do {
            page = try await service.page(zone: zone)
            failed = false
        } catch {
            logger.error("get_achievements failed: \(error.localizedDescription, privacy: .public)")
            failed = page == nil
        }
    }
}
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh AchievementsModelTests`
Expected: `** TEST SUCCEEDED **`, 8 tests.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements/Data/AchievementsModel.swift \
  apps/ios/PebblesTests/Achievements/FakeAchievementsPageService.swift \
  apps/ios/PebblesTests/Achievements/AchievementsModelTests.swift
git commit -m "feat(ios): keep the achievements page, mark cards seen and pull ceremonies"
```

---

### Task 7: Celebrate only what the server says to celebrate

**Files:**
- Modify: `Pebbles/Services/AchievementsService.swift` (`AchievementCheckResult`, `fireCheck`)
- Create: `PebblesTests/Achievements/AchievementCelebrationTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `PebblesTests/Achievements/AchievementCelebrationTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// `check_achievements()` returns `celebrate` (#1305 D7, D8): contributions and a
/// level crossed by a heavy pebble are recorded silently, so the unlock moment
/// (achievements design D13) shows only the rows the server marks true.
@Suite("Achievement celebration filter")
struct AchievementCelebrationTests {
    static func rows(_ json: String) throws -> [AchievementCheckResult] {
        try JSONDecoder().decode([AchievementCheckResult].self, from: Data(json.utf8))
    }

    @Test("only the rows the server marks celebrate reach the unlock moment")
    func filter() throws {
        let rows = try Self.rows("""
        [{"slug": "first-stone-medium", "karma_granted": 0, "celebrate": false},
         {"slug": "pebble-count-1", "karma_granted": 5, "celebrate": true},
         {"slug": "emotion-first-joyful", "karma_granted": 0, "celebrate": false},
         {"slug": "domain-first-family", "karma_granted": 0, "celebrate": false},
         {"slug": "first-pebble", "karma_granted": 3, "celebrate": true}]
        """)
        #expect(AchievementCheckResult.celebrated(rows).map(\.slug) == ["pebble-count-1", "first-pebble"])
    }

    @Test("a first pebble that is heavy is recorded and never celebrated")
    func heavyFirstPebble() throws {
        let rows = try Self.rows("""
        [{"slug": "pebble-count-1", "karma_granted": 5, "celebrate": false},
         {"slug": "first-pebble", "karma_granted": 3, "celebrate": false},
         {"slug": "first-stone-lowlight", "karma_granted": 0, "celebrate": false}]
        """)
        #expect(AchievementCheckResult.celebrated(rows).isEmpty)
    }

    @Test("a row without celebrate (claim_blue_pebble, an older server) still celebrates")
    func absentFlag() throws {
        let rows = try Self.rows(#"[{"slug": "blue-pebble", "karma_granted": 50}]"#)
        #expect(rows.first?.celebrate == true)
        #expect(AchievementCheckResult(slug: "blue-pebble", karmaGranted: 50).celebrate)
        #expect(AchievementCheckResult.celebrated(rows).count == 1)
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh AchievementCelebrationTests`
Expected: the build fails with `error: value of type 'AchievementCheckResult' has no member 'celebrate'`.

- [ ] **Step 3: Add the flag**

In `Pebbles/Services/AchievementsService.swift`, replace the whole `AchievementCheckResult` struct with:

```swift
/// One newly unlocked badge as `check_achievements()` reports it: the karma is
/// what the server actually emitted at unlock time, not the catalog's current
/// value — the capsule can never show a number the ledger doesn't hold.
/// `celebrate` (#1305 D7, D8) is false for the silent families (emotion and
/// domain firsts, the six firsts) and for a level crossed by a heavy pebble;
/// absent, as from `claim_blue_pebble` or an older server, it means celebrate.
struct AchievementCheckResult: Decodable, Equatable {
    let slug: String
    let karmaGranted: Int
    let celebrate: Bool

    enum CodingKeys: String, CodingKey {
        case slug
        case karmaGranted = "karma_granted"
        case celebrate
    }

    init(slug: String, karmaGranted: Int, celebrate: Bool = true) {
        self.slug = slug
        self.karmaGranted = karmaGranted
        self.celebrate = celebrate
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        slug = try container.decode(String.self, forKey: .slug)
        karmaGranted = try container.decode(Int.self, forKey: .karmaGranted)
        celebrate = try container.decodeIfPresent(Bool.self, forKey: .celebrate) ?? true
    }

    /// The rows the unlock moment shows (spec §4): only those the server says to celebrate.
    static func celebrated(_ rows: [AchievementCheckResult]) -> [AchievementCheckResult] {
        rows.filter(\.celebrate)
    }
}
```

- [ ] **Step 4: Filter the mutation path**

In `fireCheck()`, replace:

```swift
                let results = try await self.check()
                guard !results.isEmpty else { return }
```

with:

```swift
                // Silent rows are recorded; they show as "new" on the page, never as a moment.
                let results = AchievementCheckResult.celebrated(try await self.check())
                guard !results.isEmpty else { return }
```

`claimBluePebble()` keeps presenting what it gets: its rows carry no `celebrate` and decode as `true`. `checkIgnoringFailure()` (the screen-open grant) never presents anything.

- [ ] **Step 5: Run the new tests and the suites that build check results**

Run: `apps/ios/build/ach-test.sh AchievementCelebrationTests`, then `apps/ios/build/ach-test.sh AchievementsDecodingTests`, then `apps/ios/build/ach-test.sh HomeModelTests`
Expected: `** TEST SUCCEEDED **` three times.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Services/AchievementsService.swift apps/ios/PebblesTests/Achievements/AchievementCelebrationTests.swift
git commit -m "feat(ios): celebrate only the unlocks check_achievements marks to celebrate"
```

---

### Task 8: Name the new families on the Profile shelf

The shelf (`ProfileAchievementsCard`) and the old grid list every unlock. Without this task the six firsts and the completions would show their slug.

**Files:**
- Modify: `Pebbles/Features/Profile/Models/Achievement+Localized.swift`
- Modify: `Pebbles/Resources/Localizable.xcstrings` (through the helper)
- Create: `PebblesTests/Achievements/AchievementNewFamiliesCopyTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `PebblesTests/Achievements/AchievementNewFamiliesCopyTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// The six firsts (`first_stone`) and the completions (`set_complete`) named
/// on the Profile shelf (#1305 D5, D9), never by their slug. The test process
/// runs in English, as `AchievementFamilyCopyTests` does.
@Suite("Achievement copy for the new families")
struct AchievementNewFamiliesCopyTests {
    static func record(_ slug: String, _ family: String) throws -> AchievementRecord {
        let json = """
        [{"id": "3c2b1a0d-9e8f-4765-8493-a2b1c0d9e8f7", "slug": "\(slug)", "family": "\(family)", "threshold": null,
          "emotion_id": null, "domain_id": null, "sort_order": 460, "glyph_id": null, "karma_reward": 0,
          "is_active": true, "title_en": null, "title_fr": null, "description_en": null, "description_fr": null}]
        """
        return try #require(try JSONDecoder().decode([AchievementRecord].self, from: Data(json.utf8)).first)
    }

    @Test("the six firsts and the completions have titles, descriptions, symbols and groups")
    func names() throws {
        let large = try Self.record("first-stone-large", "first_stone")
        #expect(large.localizedTitle(emotionName: nil, domainName: nil) == "First large pebble")
        #expect(large.localizedDescription(emotionName: nil, domainName: nil) == "One of the six firsts.")
        #expect(large.familySymbol == "circle.circle")
        let low = try Self.record("first-stone-lowlight", "first_stone")
        #expect(low.localizedTitle(emotionName: nil, domainName: nil) == "First lowlight")
        let six = try Self.record("complete-six-firsts", "set_complete")
        #expect(six.localizedTitle(emotionName: nil, domainName: nil) == "The six firsts")
        #expect(six.familySymbol == "seal")
        let emotions = try Self.record("complete-emotions", "set_complete")
        #expect(emotions.localizedDescription(emotionName: nil, domainName: nil)
            == "Your pebbles have carried every feeling on the wheel.")
        #expect(AchievementRecord.localizedGroupName(family: "first_stone") == "The six firsts")
        #expect(AchievementRecord.localizedGroupName(family: "set_complete") == "Completions")
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh AchievementNewFamiliesCopyTests`
Expected: FAIL with `Expectation failed: (large.localizedTitle(…) → "first-stone-large") == "First large pebble"`.

- [ ] **Step 3: Add the strings**

Create `apps/ios/build/strings-task8.json`:

```json
{
  "achievement.family.first_stone.title.small": {"en": "First small pebble", "fr": "Premier petit galet"},
  "achievement.family.first_stone.title.medium": {"en": "First medium pebble", "fr": "Premier galet moyen"},
  "achievement.family.first_stone.title.large": {"en": "First large pebble", "fr": "Premier grand galet"},
  "achievement.family.first_stone.title.lowlight": {"en": "First lowlight", "fr": "Premier creux"},
  "achievement.family.first_stone.title.neutral": {"en": "First neutral pebble", "fr": "Premier galet neutre"},
  "achievement.family.first_stone.title.highlight": {"en": "First highlight", "fr": "Premier temps fort"},
  "achievement.family.first_stone.description": {"en": "One of the six firsts.", "fr": "L'une des six premières fois."},
  "achievement.group.first_stone": {"en": "The six firsts", "fr": "Les six premières fois"},
  "achievement.group.set_complete": {"en": "Completions", "fr": "Ensembles complets"}
}
```

Run: `python3 -I apps/ios/build/xcstrings-add.py apps/ios/Pebbles/Resources/Localizable.xcstrings apps/ios/build/strings-task8.json`
Expected: `added 9 entries`.

- [ ] **Step 4: Add the two families**

In `Pebbles/Features/Profile/Models/Achievement+Localized.swift`:

In `localizedTitle`, before `default:`, add:

```swift
        case "first_stone":
            return catalogString("achievement.family.first_stone.title.\(stoneKind)", fallback: slug)
        case "set_complete":
            return catalogString("achievements.ceremony.\(completionStack).title", fallback: slug)
```

In `localizedDescription`, before `default:`, add:

```swift
        case "first_stone":
            return catalogString("achievement.family.first_stone.description", fallback: "One of the six firsts.")
        case "set_complete":
            return catalogString("achievements.ceremony.\(completionStack).body", fallback: "")
```

In `familySymbol`, before `default:`, add:

```swift
        case "first_stone":      "circle.circle"
        case "set_complete":     "seal"
```

After `familySymbol`, add:

```swift
    /// A `first_stone` row's kind, from its seeded slug (`first-stone-<kind>`, #1305 Task 1).
    private var stoneKind: String {
        slug.replacingOccurrences(of: "first-stone-", with: "")
    }

    /// A `set_complete` row's stack, as `pull_ceremony` names it (`complete-six-firsts` → `six_firsts`).
    private var completionStack: String {
        slug.replacingOccurrences(of: "complete-", with: "").replacingOccurrences(of: "-", with: "_")
    }
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh AchievementNewFamiliesCopyTests`, then `apps/ios/build/ach-test.sh AchievementFamilyCopyTests`
Expected: `** TEST SUCCEEDED **` twice.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Models/Achievement+Localized.swift apps/ios/Pebbles/Resources/Localizable.xcstrings \
  apps/ios/PebblesTests/Achievements/AchievementNewFamiliesCopyTests.swift
git commit -m "feat(ios): name the six firsts and the completions on the profile shelf"
```

---

### Task 9: Part 1 gate and PR

- [ ] **Step 1: Run every unit test but the references, and the lint**

Run: `apps/ios/build/ach-test.sh "" References`, then `npm run lint --workspace=@pbbls/ios`
Expected: `** TEST SUCCEEDED **` (or exactly the failures Task 1 step 6 recorded on main), and no `error:` from swiftlint. `ThemeLiteralsTests` and `LocalizationTests` are part of that run.

- [ ] **Step 2: Check the catalog**

Open `Localizable.xcstrings` in Xcode: no entry in the `New` or `Stale` state, every new row has `en` and `fr`.

- [ ] **Step 3: Open the PR**

```bash
gh stack submit --auto
```

Title: `feat(ios): decode, keep and word the achievements page`. Body opens with `Part of #1306`, lists the key files above and notes that nothing user-visible changes yet except the silent rows no longer opening an unlock moment. Propose inheriting #1306's labels and milestone (M65 · Achievements) and confirm with the maintainer; add `no-lab-note` (no user-facing view yet). Never write a Kritik finding id in the body. If the auto-mode classifier blocks the push, hand the maintainer the command.

---

# Part 2 — the figures (branch `feat/1306-ios-achievements-figures`, stacked on part 1)

- [ ] **Start the branch:** `gh stack add feat/1306-ios-achievements-figures`

Every figure is a `Canvas` over a pure geometry enum ported from the board's JS (`final/_build/app.js`, `sheets.js`). The geometry places seats, radii and spokes; the payload says which are done, met, new, reached, which level and which family. The figures are hidden from VoiceOver: the card that holds one carries its label (part 3).

### Task 10: Type ranks, inks and the flat seal

**Files:**
- Create: `Pebbles/Features/Achievements/Figures/AchievementsType.swift`
- Create: `Pebbles/Features/Achievements/Figures/AchievementInks.swift`
- Create: `Pebbles/Features/Achievements/Figures/FlatSeal.swift`
- Create: `PebblesTests/Achievements/AchievementAtomsTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `PebblesTests/Achievements/AchievementAtomsTests.swift`:

```swift
import SwiftUI
import Testing
@testable import Pebbles

/// The figures' atoms (#1306, NOTES "Seals", "Ghosts"): the flat seal's prints
/// and sizes, and the inks that make Paper and Night.
@Suite("Achievements atoms")
struct AchievementAtomsTests {
    typealias Kind = AchievementsPage.StoneKind

    @Test("a pebble prints as the Spine prints it: solid bright, tinted even, washed heavy")
    func prints() {
        #expect(FlatSeal(valence: .highlightLarge, width: 10).print == .solid)
        #expect(FlatSeal(valence: .neutralSmall, width: 10).print == .tint)
        let heavy = FlatSeal(valence: .lowlightMedium, width: 10)
        #expect(heavy.print == .wash && heavy.outline == .lowlight && heavy.size == .medium)
    }

    @Test("a seat on the emotions ring grows 1.6 pt per step of intensity from 7.4")
    func seatWidths() {
        #expect(abs(FlatSeal.seat(.neutralSmall).width - 9) < 0.0001)
        #expect(abs(FlatSeal.seat(.highlightLarge).width - 12.2) < 0.0001)
    }

    @Test("the six firsts: three sizes of the solid seal, then the three prints at medium")
    func sixFirsts() {
        #expect(Kind.allCases.map { FlatSeal.sixFirst($0).width } == [9, 12, 14, 12.5, 12.5, 12.5])
        #expect(Kind.allCases.map { FlatSeal.sixFirst($0).print } == [.solid, .solid, .solid, .wash, .tint, .solid])
        #expect(Kind.allCases.map { FlatSeal.sixFirst($0).size } == [.small, .medium, .large, .medium, .medium, .medium])
    }

    @Test("a seal's height follows its outline's aspect, and its hatch its width")
    func proportions() {
        let seal = FlatSeal(valence: .neutralMedium, width: 13)
        #expect(abs(seal.height - 13 / CGFloat(PebbleOutlineGeometry.aspectRatio(for: .medium))) < 0.0001)
        #expect(abs(seal.hatchStep - 1.5) < 0.0001)
        let hatch = FlatSeal.hatch(in: CGRect(x: 0, y: 0, width: 12, height: 12), step: 3)
        #expect(!hatch.isEmpty && hatch.boundingRect.minX <= -11)
    }

    @Test("inks: a heavy pebble takes its family's deep ink; an unknown family draws in ink")
    func inks() throws {
        let palette = try #require(SpineFixtureWeek.families.first?.palette)
        let stone = StonePalette(emotion: palette)
        let paper = AchievementInks(scheme: .light)
        let night = AchievementInks(scheme: .dark)
        #expect(paper.sealInk(palette, polarity: .lowlight) == SpineColors.deep(stone, scheme: .light))
        #expect(night.sealInk(palette, polarity: .highlight) == SpineColors.spot(stone, scheme: .dark))
        #expect(paper.sealInk(nil, polarity: .highlight) == Color.pebbles.onSurface)
        #expect(paper.hue(nil) == nil)
        #expect(paper.hue(palette) == SpineColors.spot(stone, scheme: .light))
        #expect(night.labelInk(palette, family: "joy") == palette.secondary)
        #expect(paper.labelInk(palette, family: "shame") == palette.shaded)
        #expect(paper.labelInk(palette, family: "joy") == palette.primary)
        #expect(paper.tintK == 0.15 && night.tintK == 0.18 && paper.washK == 0.09 && night.washK == 0.12)
    }

    @Test("a polygon closes on its first point")
    func polygon() {
        let triangle = Path.polygon([.zero, CGPoint(x: 4, y: 0), CGPoint(x: 0, y: 3)])
        #expect(triangle.boundingRect == CGRect(x: 0, y: 0, width: 4, height: 3))
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh AchievementAtomsTests`
Expected: the build fails with `error: cannot find 'FlatSeal' in scope`.

- [ ] **Step 3: Write the type ranks**

Create `Pebbles/Features/Achievements/Figures/AchievementsType.swift`:

```swift
import CoreGraphics

/// The achievements page's type ranks (#1306), from the boards' `style.css` and
/// `sheets.css`, on the Spine's Ysabeau so Dynamic Type and Bold Text apply.
/// Ysabeau Office's lining figures are the `.lining` feature. A figure's labels
/// do not scale (`.none`): they sit at fixed places in a drawing.
extension SpineType.Style {
    // MARK: The header

    static let achievementsSince = Self(size: 11.5, weight: 600, trackingEm: 0.2, features: [.smallCaps],
                                        scaling: .text(.caption2))
    static let achievementsDays = Self(size: 78, weight: 230, trackingEm: -0.025, features: [.lining],
                                       scaling: .text(.largeTitle))
    static let achievementsDaysUnit = Self(size: 15, weight: 560, trackingEm: 0.16, features: [.smallCaps],
                                           scaling: .text(.subheadline))

    // MARK: The cards

    static let achievementsMarginTitle = Self(size: 11, weight: 600, trackingEm: 0.26, features: [.smallCaps],
                                              scaling: .text(.caption2))
    static let achievementsBand = Self(size: 11.5, weight: 600, trackingEm: 0.22, features: [.smallCaps],
                                       scaling: .text(.caption2))
    static let achievementsBandHint = Self(size: 14, weight: 380, italic: true, scaling: .text(.footnote))
    static let achievementsLine = Self(size: 16, weight: 380, italic: true, lineHeight: 21.6, scaling: .text(.body))
    static let achievementsSubLabel = Self(size: 11, weight: 600, trackingEm: 0.14, features: [.smallCaps],
                                           scaling: .text(.caption2))
    static let achievementsSubCount = Self(size: 20, weight: 300, features: [.lining], scaling: .text(.title3))

    // MARK: The sheets

    static let achievementsLead = Self(size: 16.5, weight: 380, italic: true, lineHeight: 22.3, scaling: .text(.body))
    static let achievementsSectionTitle = Self(size: 11.5, weight: 600, trackingEm: 0.22, features: [.smallCaps],
                                               scaling: .text(.caption2))
    static let achievementsSectionNote = Self(size: 13.5, weight: 380, italic: true, scaling: .text(.footnote))
    static let achievementsRowName = Self(size: 13, weight: 600, trackingEm: 0.1, features: [.smallCaps],
                                          scaling: .text(.footnote))
    static let achievementsRowSub = Self(size: 14.5, weight: 420, scaling: .text(.subheadline))
    static let achievementsRowNext = Self(size: 14.5, weight: 400, italic: true, scaling: .text(.subheadline))
    static let achievementsRowDate = Self(size: 12.5, weight: 400, features: [.lining], scaling: .text(.caption))
    static let achievementsRowNumber = Self(size: 21, weight: 300, features: [.lining], scaling: .text(.title3))
    static let achievementsRowNumberSmall = Self(size: 17, weight: 300, features: [.lining], scaling: .text(.body))
    static let achievementsRegisterNumber = Self(size: 12.5, weight: 400, trackingEm: 0.03, features: [.lining],
                                                 scaling: .text(.caption))
    static let achievementsGhostName = Self(size: 15, weight: 400, scaling: .text(.subheadline))
    static let achievementsQuiet = Self(size: 14.5, weight: 380, italic: true, scaling: .text(.subheadline))
    static let achievementsColumn = Self(size: 10.5, weight: 600, trackingEm: 0.14, features: [.smallCaps],
                                         scaling: .text(.caption2))
    static let achievementsCell = Self(size: 14, weight: 400, features: [.tabular], scaling: .text(.footnote))
    static let achievementsCellDone = Self(size: 14, weight: 560, features: [.tabular], scaling: .text(.footnote))

    // MARK: The figures

    static let achievementsRosaceFamily = Self(size: 11, weight: 600, trackingEm: 0.14, features: [.smallCaps],
                                               scaling: .none)
    static let achievementsRosaceCount = Self(size: 28, weight: 300, features: [.lining], scaling: .none)
    static let achievementsRosaceOf = Self(size: 11, weight: 600, trackingEm: 0.2, features: [.smallCaps],
                                           scaling: .none)

    /// The year disc's numerals, sized with the disc; a rim month's is emphasised.
    static func achievementsNumeral(_ size: CGFloat, emphasized: Bool) -> Self {
        Self(size: size, weight: emphasized ? 600 : 400, scaling: .none)
    }
}
```

- [ ] **Step 4: Write the inks**

Create `Pebbles/Features/Achievements/Figures/AchievementInks.swift`:

```swift
import SwiftUI

/// The boards' inks (#1306, NOTES "Ghosts", "Muted structure") on the app's
/// roles, so Paper and Night come from the colour scheme: `stock` is the card,
/// `ink` the text, `ink2` every "not yet", `rule` and `cardRule` the hairlines.
/// An emotion's hue is the Spine's (`SpineColors`): its family's spot, or its
/// deep ink under a heavy pebble. A family this build cannot resolve draws in
/// ink (spec §4). Android: `AchievementInks`.
struct AchievementInks {
    let stock = Color.pebbles.stock
    let ink = Color.pebbles.onSurface
    let ink2 = Color.pebbles.onSurfaceVariant
    let rule = Color.pebbles.rule
    let cardRule = Color.pebbles.cardRule
    let scheme: ColorScheme

    init(scheme: ColorScheme) {
        self.scheme = scheme
    }

    /// A family's hue (the board's `famHue`): Paper `primary`, Night `secondary`; nil when unknown.
    func hue(_ palette: EmotionPalette?) -> Color? {
        palette.map { SpineColors.spot(StonePalette(emotion: $0), scheme: scheme) }
    }

    /// A pebble's seal ink (the board's `emoColor`): the family's deep ink under a heavy pebble.
    func sealInk(_ palette: EmotionPalette?, polarity: ValencePolarity) -> Color {
        guard let palette else { return ink }
        let stone = StonePalette(emotion: palette)
        return polarity == .lowlight ? SpineColors.deep(stone, scheme: scheme) : SpineColors.spot(stone, scheme: scheme)
    }

    /// A family's name ink (the board's `tx`): Night `secondary`; Paper `primary`, shame's `shaded`.
    func labelInk(_ palette: EmotionPalette?, family: String) -> Color {
        guard let palette else { return ink }
        if scheme == .dark { return palette.secondary }
        return family == "shame" ? palette.shaded : palette.primary
    }

    /// The tint print's strength over the stock (`--tint-k`).
    var tintK: Float { scheme == .dark ? 0.18 : 0.15 }

    /// The wash print's strength over the stock (`--wash-k`).
    var washK: Float { scheme == .dark ? 0.12 : 0.09 }
}
```

- [ ] **Step 5: Write the flat seal, the ghost and the new mark**

Create `Pebbles/Features/Achievements/Figures/FlatSeal.swift`:

```swift
import SwiftUI

/// The boards' flat seal (#1306, NOTES "Seals"): the app's stone outline for a
/// size and a polarity, in one of four prints. Solid fills it; tint lays the ink
/// over the stock at `tintK` inside a rim; wash lays it at `washK` with a hatch
/// and a rim; blank is the stock inside a rim. It prints at any width into a
/// `Canvas`, its strokes at fixed points (the board's non-scaling strokes).
/// Android: `FlatSeal`.
struct FlatSeal: Equatable {
    enum Print: Equatable {
        case solid, tint, wash, blank
    }

    let size: ValenceSizeGroup
    let outline: ValencePolarity
    let print: Print
    /// The seal's width in points; its height follows the outline's aspect.
    let width: CGFloat

    init(size: ValenceSizeGroup, outline: ValencePolarity, print: Print, width: CGFloat) {
        self.size = size
        self.outline = outline
        self.print = print
        self.width = width
    }

    /// A pebble as the Spine prints it: its size, its polarity's outline and print.
    init(valence: Valence, width: CGFloat) {
        self.init(size: valence.sizeGroup, outline: valence.polarity, print: Self.print(for: valence.polarity),
                  width: width)
    }

    static func print(for polarity: ValencePolarity) -> Print {
        switch polarity {
        case .highlight: .solid
        case .neutral: .tint
        case .lowlight: .wash
        }
    }

    var height: CGFloat {
        width / CGFloat(PebbleOutlineGeometry.aspectRatio(for: size))
    }

    /// The board's hatch pitch: 30 units of the outline's 250- or 260-unit view box.
    var hatchStep: CGFloat {
        30 * width / (size == .small ? 250 : 260)
    }

    /// A met emotion on the Explorer wheel: 7.4 pt plus 1.6 per step of intensity.
    static func seat(_ valence: Valence) -> FlatSeal {
        FlatSeal(valence: valence, width: 7.4 + CGFloat(valence.intensity) * 1.6)
    }

    /// The six firsts (D5): three sizes of the solid seal, then the three prints at medium.
    static func sixFirst(_ kind: AchievementsPage.StoneKind) -> FlatSeal {
        switch kind {
        case .small: FlatSeal(size: .small, outline: .neutral, print: .solid, width: 9)
        case .medium: FlatSeal(size: .medium, outline: .neutral, print: .solid, width: 12)
        case .large: FlatSeal(size: .large, outline: .neutral, print: .solid, width: 14)
        case .lowlight: FlatSeal(size: .medium, outline: .lowlight, print: .wash, width: 12.5)
        case .neutral: FlatSeal(size: .medium, outline: .neutral, print: .tint, width: 12.5)
        case .highlight: FlatSeal(size: .medium, outline: .highlight, print: .solid, width: 12.5)
        }
    }

    /// 45° lines `step` apart, falling left to right, over `rect`.
    static func hatch(in rect: CGRect, step: CGFloat) -> Path {
        var path = Path()
        for x in stride(from: rect.minX - rect.height, through: rect.maxX, by: max(step, 0.5)) {
            path.move(to: CGPoint(x: x, y: rect.minY))
            path.addLine(to: CGPoint(x: x + rect.height, y: rect.maxY))
        }
        return path
    }
}

extension Path {
    /// A closed polygon through `points`.
    static func polygon(_ points: [CGPoint]) -> Path {
        var path = Path()
        path.addLines(points)
        path.closeSubpath()
        return path
    }

    /// A circle of `radius` about `center`.
    static func circle(_ center: CGPoint, _ radius: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
    }

    /// The segment from `start` to `end`.
    static func segment(_ start: CGPoint, _ end: CGPoint) -> Path {
        var path = Path()
        path.move(to: start)
        path.addLine(to: end)
        return path
    }
}

extension GraphicsContext {
    /// Prints `seal` in `color`, centred on `center`. `dashed` dots the rim (a month still to come).
    func draw(_ seal: FlatSeal, at center: CGPoint, color: Color, inks: AchievementInks, dashed: Bool = false) {
        guard let art = WobbleRenderer.softBackdropArt(size: seal.size, polarity: seal.outline) else { return }
        let rect = CGRect(x: center.x - seal.width / 2, y: center.y - seal.height / 2,
                          width: seal.width, height: seal.height)
        // The outline draws from its rect's size alone (as `drawSeal` assumes), so it is moved into place.
        let body = WobbledBackdropShape(art: art).path(in: CGRect(origin: .zero, size: rect.size))
            .offsetBy(dx: rect.minX, dy: rect.minY)
        let dash: [CGFloat] = dashed ? [1.6, 1.7] : []
        let rim = StrokeStyle(lineWidth: 1.1, lineJoin: .round, dash: dash)
        switch seal.print {
        case .solid:
            fill(body, with: .color(color))
            stroke(body, with: .color(color), style: StrokeStyle(lineWidth: 1, lineJoin: .round, dash: dash))
        case .tint:
            fill(body, with: .color(mixed(color, inks.tintK, over: inks.stock)))
            stroke(body, with: .color(color), style: rim)
        case .wash:
            fill(body, with: .color(mixed(color, inks.washK, over: inks.stock)))
            var hatch = self
            hatch.clip(to: body)
            hatch.stroke(FlatSeal.hatch(in: rect, step: seal.hatchStep), with: .color(color.opacity(0.85)),
                         lineWidth: 0.8)
            stroke(body, with: .color(color), style: rim)
        case .blank:
            fill(body, with: .color(inks.stock))
            stroke(body, with: .color(color), style: rim)
        }
    }

    /// `color` laid over `ground` at `k`, in OKLab (the board's `color-mix`).
    func mixed(_ color: Color, _ k: Float, over ground: Color) -> Color {
        Color(OKLab.mix(color.resolve(in: environment), k, ground.resolve(in: environment)))
    }

    /// "Not yet" (NOTES "Ghosts"): a dotted socket in secondary ink at 1 pt.
    func drawGhost(at center: CGPoint, radius: CGFloat = 3.2, inks: AchievementInks) {
        let socket = Path.circle(center, radius)
        fill(socket, with: .color(inks.stock))
        stroke(socket, with: .color(inks.ink2), style: StrokeStyle(lineWidth: 1, lineCap: .round, dash: [1.2, 1.6]))
    }

    /// New since the card was last opened: a ring round the seal and a dot at its shoulder.
    func drawNewMark(at center: CGPoint, sealWidth width: CGFloat, inks: AchievementInks) {
        stroke(Path.circle(center, width * 0.86), with: .color(inks.ink), lineWidth: 1)
        fill(Path.circle(CGPoint(x: center.x + width * 0.66, y: center.y - width * 0.66), 1.9), with: .color(inks.ink))
    }
}
```

- [ ] **Step 6: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh AchievementAtomsTests`
Expected: `** TEST SUCCEEDED **`, 6 tests.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements/Figures apps/ios/PebblesTests/Achievements/AchievementAtomsTests.swift
git commit -m "feat(ios): add the achievements type ranks, inks and flat seal"
```

---

### Task 11: The Explorer wheel

**Files:**
- Create: `Pebbles/Features/Achievements/Figures/FamilyRun.swift`
- Create: `Pebbles/Features/Achievements/Figures/ExplorerWheelGeometry.swift`
- Create: `Pebbles/Features/Achievements/Figures/FirstFiveMarks.swift`
- Create: `Pebbles/Features/Achievements/Figures/ExplorerWheel.swift`
- Create: `PebblesTests/Achievements/GeometryExpectations.swift`
- Create: `PebblesTests/Achievements/ExplorerWheelGeometryTests.swift`

- [ ] **Step 1: Write the test helper and the failing tests**

Create `PebblesTests/Achievements/GeometryExpectations.swift`:

```swift
import CoreGraphics

/// Points equal to a millionth of a point: the geometry's tests compare places, not floats.
func nearly(_ a: CGPoint, _ b: CGPoint, _ tolerance: CGFloat = 0.000_001) -> Bool {
    abs(a.x - b.x) < tolerance && abs(a.y - b.y) < tolerance
}

func distance(_ a: CGPoint, _ b: CGPoint) -> CGFloat {
    ((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)).squareRoot()
}
```

Create `PebblesTests/Achievements/ExplorerWheelGeometryTests.swift`:

```swift
import CoreGraphics
import Testing
@testable import Pebbles

/// The wheel's places (#1306, the board's `wheel`): angles clockwise from twelve,
/// one radius per ring, seats from their index.
@Suite("Explorer wheel geometry")
struct ExplorerWheelGeometryTests {
    typealias G = ExplorerWheelGeometry

    @Test("the board's polar: degrees clockwise from twelve o'clock")
    func polar() {
        #expect(nearly(G.point(10, 0), CGPoint(x: 0, y: -10)))
        #expect(nearly(G.point(10, 90), CGPoint(x: 10, y: 0)))
        #expect(nearly(G.point(10, 180), CGPoint(x: 0, y: 10)))
    }

    @Test("the rings sit at the board's radii, centre out")
    func radii() {
        #expect([G.firstFive, G.sixFirsts, G.twentyFive, G.domains, G.emotions] == [44, 68, 90, 112, 134])
    }

    @Test("seats: the first five every 72°, the six firsts from 30° every 60°, a ring of n half a step past twelve")
    func seats() {
        #expect((0..<5).map(G.firstFiveAngle) == [0, 72, 144, 216, 288])
        #expect((0..<6).map(G.sixFirstAngle) == [30, 90, 150, 210, 270, 330])
        #expect(abs(G.seatAngle(0, of: 25) - 7.2) < 0.000_001)
        #expect(abs(G.seatAngle(17, of: 18) - 350) < 0.000_001)
    }

    @Test("the emotions sit on the rosace's seats: seat 2.5 at twelve")
    func emotionSeats() {
        #expect(abs(G.emotionAngle(2.5, of: 38)) < 0.000_001)
        #expect(abs(G.emotionAngle(0, of: 38) + 2.5 * 360 / 38) < 0.000_001)
    }

    @Test("a family's arc runs half a step outside its seats, 1.2° in from each end; its tick at its start")
    func familyArc() {
        let step = 360.0 / 38
        let arc = G.familyArc(start: 6, length: 5, of: 38)
        #expect(abs(arc.from - (G.emotionAngle(6, of: 38) - step / 2 + 1.2)) < 0.000_001)
        #expect(abs(arc.to - (G.emotionAngle(10, of: 38) + step / 2 - 1.2)) < 0.000_001)
        let points = G.arc(G.emotions, from: arc.from, to: arc.to)
        #expect(nearly(points.first ?? .zero, G.point(G.emotions, arc.from)))
        #expect(nearly(points.last ?? .zero, G.point(G.emotions, arc.to)))
        let tick = G.familyTick(start: 6, of: 38)
        #expect(abs(distance(tick.0, .zero) - (G.emotions + 6)) < 0.000_001)
        #expect(abs(distance(tick.1, .zero) - (G.emotions + 11)) < 0.000_001)
    }

    @Test("families run in seat order; the design's seven runs")
    func runs() throws {
        let page = try AchievementsFixtures.payload("design-fixture").model
        let runs = FamilyRun.runs(try #require(page.emotions).seats.map(\.family))
        #expect(runs == [
            FamilyRun(family: "joy", start: 0, length: 6), FamilyRun(family: "pride", start: 6, length: 5),
            FamilyRun(family: "anger", start: 11, length: 6), FamilyRun(family: "fear", start: 17, length: 6),
            FamilyRun(family: "shame", start: 23, length: 4), FamilyRun(family: "sadness", start: 27, length: 6),
            FamilyRun(family: "peace", start: 33, length: 5)
        ])
        #expect(runs[1].contains(10) && !runs[1].contains(11))
        #expect(FamilyRun.runs([]) == [])
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh ExplorerWheelGeometryTests`
Expected: the build fails with `error: cannot find 'ExplorerWheelGeometry' in scope`.

- [ ] **Step 3: Write the runs and the geometry**

Create `Pebbles/Features/Achievements/Figures/FamilyRun.swift`:

```swift
/// Consecutive seats of one family, as the server seats them (wheel order, then
/// slug): where the wheel draws a family's arc and tick, the rosace its label,
/// the margin ladder its bar and the Emotions sheet its section.
struct FamilyRun: Equatable, Identifiable {
    var id: Int { start }
    let family: String
    let start: Int
    let length: Int

    func contains(_ seat: Int) -> Bool {
        seat >= start && seat < start + length
    }

    static func runs(_ families: [String]) -> [FamilyRun] {
        var runs: [FamilyRun] = []
        for (index, family) in families.enumerated() {
            if let last = runs.last, last.family == family {
                runs[runs.count - 1] = FamilyRun(family: family, start: last.start, length: last.length + 1)
            } else {
                runs.append(FamilyRun(family: family, start: index, length: 1))
            }
        }
        return runs
    }
}
```

Create `Pebbles/Features/Achievements/Figures/ExplorerWheelGeometry.swift`:

```swift
import CoreGraphics
import Foundation

/// The Explorer wheel's places (#1306, spec D6; the board's `wheel`): a 300-unit
/// square on the origin, angles clockwise from twelve, one radius per ring from
/// the centre out. Seat index → angle and ring → radius; nothing here reads
/// whether a seat is done. Android: `ExplorerWheelGeometry`.
enum ExplorerWheelGeometry {
    static let side: CGFloat = 300
    static let firstFive: CGFloat = 44
    static let sixFirsts: CGFloat = 68
    static let twentyFive: CGFloat = 90
    static let domains: CGFloat = 112
    static let emotions: CGFloat = 134
    /// The ring of 25 seats: the Explorer's "25 pebbles" milestone (D6).
    static let twentyFiveSeats = 25

    /// The board's `polar`: `radius` at `degrees` clockwise from twelve o'clock.
    static func point(_ radius: CGFloat, _ degrees: Double) -> CGPoint {
        let radians = degrees * .pi / 180
        return CGPoint(x: radius * CGFloat(sin(radians)), y: -radius * CGFloat(cos(radians)))
    }

    /// The first five, one every 72°, the first at twelve (the Home checklist's order).
    static func firstFiveAngle(_ index: Int) -> Double {
        Double(index) * 72
    }

    /// The six firsts, from 30° every 60°.
    static func sixFirstAngle(_ index: Int) -> Double {
        30 + Double(index) * 60
    }

    /// `count` seats round a ring, the first half a step past twelve.
    static func seatAngle(_ index: Int, of count: Int) -> Double {
        (Double(index) + 0.5) * 360 / Double(max(count, 1))
    }

    /// The emotions' seats, as the rosace seats them: seat 2.5 at twelve.
    static func emotionAngle(_ seat: Double, of count: Int) -> Double {
        (seat - 2.5) * 360 / Double(max(count, 1))
    }

    /// A family's arc on the emotions ring: half a step outside its first and
    /// last seats, 1.2° in from each end.
    static func familyArc(start: Int, length: Int, of count: Int) -> (from: Double, to: Double) {
        let step = 360 / Double(max(count, 1))
        return (emotionAngle(Double(start), of: count) - step / 2 + 1.2,
                emotionAngle(Double(start + length - 1), of: count) + step / 2 - 1.2)
    }

    /// The points of an arc of `radius`, one per degree, ending exactly on `to`.
    static func arc(_ radius: CGFloat, from: Double, to: Double) -> [CGPoint] {
        guard to > from else { return [point(radius, from)] }
        return stride(from: from, to: to, by: 1).map { point(radius, $0) } + [point(radius, to)]
    }

    /// A family's boundary tick: half a step before its first seat, from 6 to 11 beyond the ring.
    static func familyTick(start: Int, of count: Int) -> (CGPoint, CGPoint) {
        let angle = emotionAngle(Double(start), of: count) - 180 / Double(max(count, 1))
        return (point(emotions + 6, angle), point(emotions + 11, angle))
    }
}
```

- [ ] **Step 4: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh ExplorerWheelGeometryTests`
Expected: `** TEST SUCCEEDED **`, 6 tests.

- [ ] **Step 5: Write the first five's marks**

Create `Pebbles/Features/Achievements/Figures/FirstFiveMarks.swift`:

```swift
import SwiftUI

/// Each first-five task drawn as the part it makes (NOTES "Aligned with main",
/// as `ChecklistIllustrations` pictures them): a blank seal, the seal lit, three
/// seals together, a soul's mark, a glyph in a dashed circle. Shared by the
/// wheel and the Explorer sheet's rows.
enum FirstFiveMarks {
    static let soul = ["M30,110 Q65,55 100,110 Q135,165 170,110"]
    static let glyph = ["M100,40 Q160,40 160,100 Q160,160 100,160 Q40,160 40,100 Q40,65 95,70", "M100,100 L100,101"]
}

extension GraphicsContext {
    /// `scale` grows the mark for a sheet's row (1.7 there); its strokes keep their weight.
    func drawFirstFiveMark(_ task: HomeChecklist.Task, at center: CGPoint, scale: CGFloat = 1, inks: AchievementInks) {
        func at(_ dx: CGFloat, _ dy: CGFloat) -> CGPoint { CGPoint(x: center.x + dx * scale, y: center.y + dy * scale) }
        switch task {
        case .moment:
            draw(FlatSeal(size: .medium, outline: .neutral, print: .blank, width: 10 * scale), at: center,
                 color: inks.ink, inks: inks)
        case .pebble:
            draw(FlatSeal(size: .medium, outline: .highlight, print: .solid, width: 10 * scale), at: center,
                 color: inks.ink, inks: inks)
        case .collection:
            draw(FlatSeal(size: .small, outline: .highlight, print: .solid, width: 6 * scale), at: at(-4.6, 2.4),
                 color: inks.ink, inks: inks)
            draw(FlatSeal(size: .medium, outline: .neutral, print: .tint, width: 6.6 * scale), at: at(0, -3),
                 color: inks.ink, inks: inks)
            draw(FlatSeal(size: .small, outline: .lowlight, print: .wash, width: 6 * scale), at: at(4.6, 2.4),
                 color: inks.ink, inks: inks)
        case .soul:
            strokeMark(FirstFiveMarks.soul, side: 14 * scale, at: center, color: inks.ink)
        case .glyph:
            stroke(Path.circle(center, 7.2 * scale), with: .color(inks.ink2),
                   style: StrokeStyle(lineWidth: 1, dash: [1.6, 1.6]))
            strokeMark(FirstFiveMarks.glyph, side: 9.5 * scale, at: center, color: inks.ink)
        }
    }

    /// A mark drawn in a 200-unit box, set `side` wide on `center`, at the glyph's 1.14 pt carve.
    private func strokeMark(_ strokes: [String], side: CGFloat, at center: CGPoint, color: Color) {
        let transform = CGAffineTransform(translationX: center.x - side / 2, y: center.y - side / 2)
            .scaledBy(x: side / 200, y: side / 200)
        for stroke in strokes {
            guard let path = SVGPathParser.parse(stroke) else { continue }
            self.stroke(Path(path).applying(transform), with: .color(color),
                        style: StrokeStyle(lineWidth: 1.14, lineCap: .round, lineJoin: .round))
        }
    }
}
```

- [ ] **Step 6: Write the wheel**

Create `Pebbles/Features/Achievements/Figures/ExplorerWheel.swift`:

```swift
import SwiftUI

/// The Explorer wheel (#1306, spec D6; the board's `wheel`): the user's first
/// pebble at the centre, then the first five, the six firsts, 25 pebbles, the
/// domains in the order they came and the emotions on the rosace's seats. A seat
/// the payload marks done or met draws its seal, any other a ghost, a new one its
/// mark; a ring the server lists in `rings_done` gets the darker track.
struct ExplorerWheel: View {
    let explorer: AchievementsPage.Explorer
    let emotions: AchievementsPage.Emotions?
    let domains: AchievementsPage.Domains?
    let lookups: WrapLookups
    var side: CGFloat = 300

    @Environment(\.colorScheme) private var scheme

    private typealias G = ExplorerWheelGeometry

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        Canvas { context, size in
            var c = context
            c.translateBy(x: size.width / 2, y: size.height / 2)
            c.scaleBy(x: size.width / G.side, y: size.width / G.side)
            drawEmotions(c, inks)
            drawDomains(c, inks)
            drawTwentyFive(c, inks)
            drawSixFirsts(c, inks)
            drawFirstFive(c, inks)
            drawCentre(c, inks)
        }
        .frame(width: side, height: side)
        .accessibilityHidden(true)
    }

    private func track(_ c: GraphicsContext, _ radius: CGFloat, _ ring: AchievementsPage.Ring, _ inks: AchievementInks) {
        c.stroke(Path.circle(.zero, radius), with: .color(explorer.ringsDone.contains(ring) ? inks.ink2 : inks.rule),
                 lineWidth: 1)
    }

    private func drawEmotions(_ c: GraphicsContext, _ inks: AchievementInks) {
        guard let emotions else { return }
        let count = emotions.seats.count
        for run in FamilyRun.runs(emotions.seats.map(\.family)) {
            let span = G.familyArc(start: run.start, length: run.length, of: count)
            var arc = Path()
            arc.addLines(G.arc(G.emotions, from: span.from, to: span.to))
            let hue = inks.hue(lookups.family(run.family)?.palette) ?? inks.ink
            c.stroke(arc, with: .color(hue.opacity(0.5)), lineWidth: 0.8)
            let tick = G.familyTick(start: run.start, of: count)
            c.stroke(Path.segment(tick.0, tick.1), with: .color(inks.ink2), style: StrokeStyle(lineWidth: 1, lineCap: .round))
        }
        for (index, seat) in emotions.seats.enumerated() {
            let place = G.point(G.emotions, G.emotionAngle(Double(index), of: count))
            guard seat.met else {
                c.drawGhost(at: place, inks: inks)
                continue
            }
            let valence = seat.pebble?.valence ?? .neutralMedium
            let seal = FlatSeal.seat(valence)
            c.draw(seal, at: place, color: inks.sealInk(lookups.family(seat.family)?.palette, polarity: valence.polarity),
                   inks: inks)
            if seat.isNew { c.drawNewMark(at: place, sealWidth: seal.width, inks: inks) }
        }
    }

    private func drawDomains(_ c: GraphicsContext, _ inks: AchievementInks) {
        guard let domains else { return }
        track(c, G.domains, .domains, inks)
        let entered = domains.arrival
        let count = entered.count + domains.notYet.count
        let seal = FlatSeal(size: .small, outline: .neutral, print: .solid, width: 9)
        for index in 0..<count {
            let place = G.point(G.domains, G.seatAngle(index, of: count))
            guard index < entered.count else {
                c.drawGhost(at: place, inks: inks)
                continue
            }
            c.draw(seal, at: place, color: inks.ink, inks: inks)
            if entered[index].isNew { c.drawNewMark(at: place, sealWidth: seal.width, inks: inks) }
        }
    }

    private func drawTwentyFive(_ c: GraphicsContext, _ inks: AchievementInks) {
        guard let twentyFive = explorer.twentyFive else { return }
        track(c, G.twentyFive, .twentyFive, inks)
        let seal = FlatSeal(size: .small, outline: .highlight, print: .solid, width: 6.2)
        for index in 0..<G.twentyFiveSeats {
            let place = G.point(G.twentyFive, G.seatAngle(index, of: G.twentyFiveSeats))
            if index < twentyFive.count {
                c.draw(seal, at: place, color: inks.ink, inks: inks)
            } else {
                c.drawGhost(at: place, radius: 2.6, inks: inks)
            }
        }
    }

    private func drawSixFirsts(_ c: GraphicsContext, _ inks: AchievementInks) {
        track(c, G.sixFirsts, .sixFirsts, inks)
        for (index, first) in explorer.sixFirsts.enumerated() {
            let place = G.point(G.sixFirsts, G.sixFirstAngle(index))
            guard first.done, let kind = first.kind else {
                c.drawGhost(at: place, inks: inks)
                continue
            }
            let seal = FlatSeal.sixFirst(kind)
            c.draw(seal, at: place, color: inks.ink, inks: inks)
            if first.isNew { c.drawNewMark(at: place, sealWidth: seal.width, inks: inks) }
        }
    }

    private func drawFirstFive(_ c: GraphicsContext, _ inks: AchievementInks) {
        track(c, G.firstFive, .firstFive, inks)
        for (index, item) in explorer.firstFive.enumerated() {
            let place = G.point(G.firstFive, G.firstFiveAngle(index))
            guard item.done, let task = item.task else {
                c.drawGhost(at: place, inks: inks)
                continue
            }
            c.drawFirstFiveMark(task, at: place, inks: inks)
            if item.isNew { c.drawNewMark(at: place, sealWidth: 10, inks: inks) }
        }
    }

    /// The user's first pebble, printed as its own seal in its emotion's hue (D6); a ghost before it.
    private func drawCentre(_ c: GraphicsContext, _ inks: AchievementInks) {
        guard let first = explorer.firstPebble else {
            c.drawGhost(at: .zero, radius: 4, inks: inks)
            return
        }
        let palette = first.emotionId.flatMap(lookups.emotion)?.palette
        c.draw(FlatSeal(valence: first.valence, width: 25), at: .zero,
               color: inks.sealInk(palette, polarity: first.valence.polarity), inks: inks)
    }
}
```

Two seat positions compare an index with a served count: the domains (`index < entered.count`, the entered come first) and the 25 (`index < twentyFive.count`, the server's capped count). Both say how many seats to ink, not whether anything was earned.

- [ ] **Step 7: Build and commit**

Run: `apps/ios/build/ach-test.sh ExplorerWheelGeometryTests`
Expected: `** TEST SUCCEEDED **` (the wheel compiles; it is drawn by the references in Task 16).

```bash
git add apps/ios/Pebbles/Features/Achievements/Figures apps/ios/PebblesTests/Achievements/GeometryExpectations.swift \
  apps/ios/PebblesTests/Achievements/ExplorerWheelGeometryTests.swift
git commit -m "feat(ios): draw the explorer wheel"
```

---

### Task 12: The emotions rosace

**Files:**
- Create: `Pebbles/Features/Achievements/Figures/RosaceGeometry.swift`
- Create: `Pebbles/Features/Achievements/Figures/EmotionRosace.swift`
- Modify: `Pebbles/Features/Achievements/Copy/AchievementsCopy.swift` (`rosaceOf`)
- Modify: `Pebbles/Resources/Localizable.xcstrings` (through the helper)
- Create: `PebblesTests/Achievements/RosaceGeometryTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `PebblesTests/Achievements/RosaceGeometryTests.swift`:

```swift
import SwiftUI
import Testing
@testable import Pebbles

/// The rosace's facets (#1306, the board's `gem`): a kite per seat between the
/// outer, middle and inner rings, a star between two kites, seat 2.5 at twelve.
@Suite("Rosace geometry")
struct RosaceGeometryTests {
    let g = RosaceGeometry(side: 270, count: 38, labelled: true)
    var centre: CGPoint { CGPoint(x: g.centre, y: g.centre) }

    @Test("radii: 0.345 of the side when labelled, 0.44 bare, then 85 % and 47 % of it")
    func radii() {
        #expect(abs(g.outer - 93.15) < 1e-9)
        #expect(abs(g.mid - 93.15 * 0.85) < 1e-9 && abs(g.inner - 93.15 * 0.47) < 1e-9)
        #expect(abs(RosaceGeometry(side: 28, count: 38, labelled: false).outer - 12.32) < 1e-9)
    }

    @Test("seat 2.5 points at twelve; a kite's tip lies on the outer ring, its heel on the inner")
    func axes() {
        #expect(abs(g.angle(2.5) + .pi / 2) < 1e-9)
        #expect(abs(distance(g.kite(0)[1], centre) - g.outer) < 1e-6)
        #expect(abs(distance(g.kite(0)[3], centre) - g.inner) < 1e-6)
    }

    @Test("a star joins two neighbouring kites at their heels")
    func stars() {
        #expect(nearly(g.star(4)[0], g.kite(4)[3]))
        #expect(nearly(g.star(4)[1], g.kite(4)[2]))
        #expect(nearly(g.star(4)[2], g.kite(5)[3]))
    }

    @Test("the silhouette has two points a seat; labels sit 22 out, fresh dots 6.5, ticks 3 to 9")
    func marks() {
        #expect(g.silhouette.count == 76)
        #expect(abs(distance(g.label(start: 0, length: 6), centre) - (g.outer + 22)) < 1e-6)
        #expect(abs(distance(g.fresh(31), centre) - (g.outer + 6.5)) < 1e-6)
        let tick = g.tick(start: 6)
        #expect(abs(distance(tick.0, centre) - (g.outer + 3)) < 1e-6 && abs(distance(tick.1, centre) - (g.outer + 9)) < 1e-6)
    }

    @Test("a seat's place in its family's ramp, and the ramp's ends")
    func tones() throws {
        #expect(RosaceGeometry.tone(index: 0, of: 6) == 0)
        #expect(RosaceGeometry.tone(index: 5, of: 6) == 1)
        #expect(RosaceGeometry.tone(index: 0, of: 1) == 0.5)
        let palette = try #require(SpineFixtureWeek.families.first?.palette)
        var night = EnvironmentValues()
        night.colorScheme = .dark
        #expect(RosaceColors.tone(palette, t: 0, scheme: .dark, environment: night) == palette.secondary.resolve(in: night))
    }

    @Test("the count's caption")
    func caption() {
        #expect(AchievementsCopy.rosaceOf(38, locale: Locale(identifier: "en")) == "of 38")
        #expect(AchievementsCopy.rosaceOf(38, locale: Locale(identifier: "fr")) == "sur 38")
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh RosaceGeometryTests`
Expected: the build fails with `error: cannot find 'RosaceGeometry' in scope`.

- [ ] **Step 3: Add the caption's string and copy**

Create `apps/ios/build/strings-task12.json`:

```json
{
  "achievements.rosace.of": {"en": "of %@", "fr": "sur %@"}
}
```

Run: `python3 -I apps/ios/build/xcstrings-add.py apps/ios/Pebbles/Resources/Localizable.xcstrings apps/ios/build/strings-task12.json`
Expected: `added 1 entries`.

In `AchievementsCopy.swift`, after `coverage(_:locale:)`, add:

```swift
    /// "of 38", "sur 38": under the rosace's count.
    static func rosaceOf(_ of: Int, locale: Locale) -> String {
        format("achievements.rosace.of", locale, number(of, locale: locale))
    }
```

- [ ] **Step 4: Write the geometry and its colours**

Create `Pebbles/Features/Achievements/Figures/RosaceGeometry.swift`:

```swift
import SwiftUI

/// The emotions rosace's facets (#1306, the board's `gem`): in a `side` square,
/// one kite per seat between the outer, middle and inner rings and one star
/// between two kites, seat 2.5 at twelve. Angles are radians from +x with y
/// down. Android: `RosaceGeometry`.
struct RosaceGeometry {
    let side: CGFloat
    let count: Int
    /// Labelled leaves room for the families' names around it.
    let labelled: Bool

    var centre: CGFloat { side / 2 }
    var outer: CGFloat { side * (labelled ? 0.345 : 0.44) }
    var mid: CGFloat { outer * 0.85 }
    var inner: CGFloat { outer * 0.47 }
    var step: Double { 2 * .pi / Double(max(count, 1)) }

    /// Seat `k`'s axis.
    func angle(_ k: Double) -> Double {
        -.pi / 2 + (k - 2.5) * step
    }

    func point(_ radius: CGFloat, _ angle: Double) -> CGPoint {
        CGPoint(x: centre + CGFloat(cos(angle)) * radius, y: centre + CGFloat(sin(angle)) * radius)
    }

    /// Seat `k`'s kite: left shoulder, tip, right shoulder, heel.
    func kite(_ k: Int) -> [CGPoint] {
        let a = angle(Double(k))
        return [point(mid, a - step / 2), point(outer, a), point(mid, a + step / 2), point(inner, a)]
    }

    /// The star between seats `k` and `k + 1`: heel, shared shoulder, next heel.
    func star(_ k: Int) -> [CGPoint] {
        [point(inner, angle(Double(k))), point(mid, angle(Double(k)) + step / 2), point(inner, angle(Double(k + 1)))]
    }

    /// The outline round every kite.
    var silhouette: [CGPoint] {
        (0..<count).flatMap { k in [point(mid, angle(Double(k)) - step / 2), point(outer, angle(Double(k)))] }
    }

    /// A family's boundary tick, half a step before its first seat, from 3 to 9 outside.
    func tick(start: Int) -> (CGPoint, CGPoint) {
        let a = angle(Double(start)) - step / 2
        return (point(outer + 3, a), point(outer + 9, a))
    }

    /// A family's name: 22 outside, at its middle seat.
    func label(start: Int, length: Int) -> CGPoint {
        point(outer + 22, angle(Double(start) + Double(length - 1) / 2))
    }

    /// A new emotion's dot, 6.5 outside its seat.
    func fresh(_ k: Int) -> CGPoint {
        point(outer + 6.5, angle(Double(k)))
    }

    /// A seat's place in its family's ramp, from 0 to 1 (the board's `t`).
    static func tone(index: Int, of length: Int) -> Double {
        length > 1 ? Double(index) / Double(length - 1) : 0.5
    }
}

/// The rosace's facet colours (the board's `tone`), mixed in OKLab.
enum RosaceColors {
    /// Paper: the family's secondary moved toward its shaded ink by 8 % plus 72 % of
    /// the seat's place. Night: toward its primary by 62 % of it.
    static func tone(_ palette: EmotionPalette, t: Double, scheme: ColorScheme,
                     environment: EnvironmentValues) -> Color.Resolved {
        let secondary = palette.secondary.resolve(in: environment)
        if scheme == .dark {
            return OKLab.mix(palette.primary.resolve(in: environment), Float((t * 62).rounded() / 100), secondary)
        }
        return OKLab.mix(palette.shaded.resolve(in: environment), Float((8 + t * 72).rounded() / 100), secondary)
    }

    /// The star between two met seats of one family: the next seat's tone, 38 % toward the stock.
    static func soft(_ tone: Color.Resolved, stock: Color.Resolved) -> Color.Resolved {
        OKLab.mix(stock, 0.38, tone)
    }
}
```

- [ ] **Step 5: Write the rosace**

Create `Pebbles/Features/Achievements/Figures/EmotionRosace.swift`:

```swift
import SwiftUI

/// The emotions rosace (#1306, the board's `gem`): 38 facets by family, a met
/// seat filled at its place in its family's ramp, the star between two met seats
/// of one family softened toward the stock, a new seat's dot outside it. The
/// count and "of 38" are the payload's. A family this build cannot resolve fills in ink.
struct EmotionRosace: View {
    let emotions: AchievementsPage.Emotions
    let lookups: WrapLookups
    var side: CGFloat = 270
    /// The families' names round it (the card and the sheet), not on a row's small mark.
    var labelled = true
    var showsCentre = true
    /// The family ticks and the new dots.
    var showsMarks = true

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let geometry = RosaceGeometry(side: side, count: emotions.seats.count, labelled: labelled)
        let runs = FamilyRun.runs(emotions.seats.map(\.family))
        let inks = AchievementInks(scheme: scheme)
        let locale = SpineStrings.appLocale
        ZStack {
            Canvas { context, _ in draw(context, geometry, runs, inks) }
            if labelled {
                ForEach(runs) { run in
                    Text(verbatim: lookups.familyName(run.family))
                        .spineType(.achievementsRosaceFamily)
                        .foregroundStyle(inks.labelInk(lookups.family(run.family)?.palette, family: run.family))
                        .fixedSize()
                        .position(geometry.label(start: run.start, length: run.length))
                }
            }
            if showsCentre {
                VStack(spacing: 0) {
                    Text(verbatim: AchievementsCopy.number(emotions.count, locale: locale))
                        .spineType(.achievementsRosaceCount)
                        .foregroundStyle(inks.ink)
                    Text(verbatim: AchievementsCopy.rosaceOf(emotions.of, locale: locale))
                        .spineType(.achievementsRosaceOf)
                        .foregroundStyle(inks.ink2)
                }
                .fixedSize()
                .position(x: geometry.centre, y: geometry.centre + 4)
            }
        }
        .frame(width: side, height: side)
        .accessibilityHidden(true)
    }

    private func draw(_ c: GraphicsContext, _ g: RosaceGeometry, _ runs: [FamilyRun], _ inks: AchievementInks) {
        let stock = inks.stock.resolve(in: c.environment)
        let silhouette = Path.polygon(g.silhouette)
        c.fill(silhouette, with: .color(inks.stock))
        c.stroke(silhouette, with: .color(inks.ink2), style: StrokeStyle(lineWidth: 1, lineJoin: .round))
        var outlines = Path()
        for k in 0..<g.count {
            outlines.addPath(Path.polygon(g.kite(k)))
            outlines.addPath(Path.polygon(g.star(k)))
        }
        c.stroke(outlines, with: .color(inks.ink2), style: StrokeStyle(lineWidth: 1, lineJoin: .round))
        let seats = emotions.seats
        let tones = seats.indices.map { tone($0, runs, inks, c.environment) }
        for k in seats.indices where seats[k].met {
            let kite = Path.polygon(g.kite(k))
            c.fill(kite, with: .color(Color(tones[k])))
            c.stroke(kite, with: .color(inks.stock), lineWidth: 0.9)
            let next = (k + 1) % seats.count
            if seats[next].met, seats[next].family == seats[k].family {
                let star = Path.polygon(g.star(k))
                c.fill(star, with: .color(Color(RosaceColors.soft(tones[next], stock: stock))))
                c.stroke(star, with: .color(inks.stock), lineWidth: 0.9)
            }
        }
        guard showsMarks else { return }
        for run in runs {
            let tick = g.tick(start: run.start)
            c.stroke(Path.segment(tick.0, tick.1), with: .color(inks.ink2), style: StrokeStyle(lineWidth: 1, lineCap: .round))
        }
        for k in seats.indices where seats[k].isNew {
            c.fill(Path.circle(g.fresh(k), 2.6), with: .color(inks.ink))
        }
    }

    private func tone(_ k: Int, _ runs: [FamilyRun], _ inks: AchievementInks,
                      _ environment: EnvironmentValues) -> Color.Resolved {
        guard let run = runs.first(where: { $0.contains(k) }), let palette = lookups.family(run.family)?.palette else {
            return inks.ink.resolve(in: environment)
        }
        return RosaceColors.tone(palette, t: RosaceGeometry.tone(index: k - run.start, of: run.length),
                                 scheme: scheme, environment: environment)
    }
}
```

- [ ] **Step 6: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh RosaceGeometryTests`
Expected: `** TEST SUCCEEDED **`, 6 tests.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements apps/ios/Pebbles/Resources/Localizable.xcstrings \
  apps/ios/PebblesTests/Achievements/RosaceGeometryTests.swift
git commit -m "feat(ios): draw the emotions rosace"
```

---

### Task 13: The domains' mended band

**Files:**
- Create: `Pebbles/Features/Achievements/Figures/ShardBandGeometry.swift`
- Create: `Pebbles/Features/Achievements/Figures/DomainBand.swift`
- Create: `PebblesTests/Achievements/ShardBandGeometryTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `PebblesTests/Achievements/ShardBandGeometryTests.swift`:

```swift
import CoreGraphics
import Testing
@testable import Pebbles

/// The mended band (#1306, the board's `shardStrip`): one shard a domain, the
/// seams leaning by a stable hash, so every device mends it the same way.
@Suite("Shard band geometry")
struct ShardBandGeometryTests {
    let g = ShardBandGeometry(width: 300, height: 30, count: 18)

    @Test("the band starts and ends square, and neighbours share their seam")
    func seams() {
        #expect(nearly(g.shard(0)[0], CGPoint(x: 0, y: 0)) && nearly(g.shard(0)[3], CGPoint(x: 0, y: 30)))
        #expect(nearly(g.shard(17)[1], CGPoint(x: 300, y: 0)) && nearly(g.shard(17)[2], CGPoint(x: 300, y: 30)))
        for i in 0..<17 {
            #expect(nearly(g.shard(i)[1], g.shard(i + 1)[0]))
            #expect(nearly(g.shard(i)[2], g.shard(i + 1)[3]))
        }
    }

    @Test("a seam leans at most 35 % of a cell, the same way every time")
    func jitter() {
        for i in 0..<17 {
            #expect(abs(g.jitter(i, "t")) <= g.cell * 0.35)
            #expect(g.jitter(i, "b") == ShardBandGeometry(width: 300, height: 30, count: 18).jitter(i, "b"))
        }
        #expect(ShardBandGeometry.unit("R0t") == CGFloat(StableHash.fnv1a("R0t")) / 4_294_967_296)
    }

    @Test("a new domain's dot sits under its shard's middle")
    func dot() {
        #expect(nearly(g.dot(13), CGPoint(x: 13.5 * 300 / 18, y: 36)))
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh ShardBandGeometryTests`
Expected: the build fails with `error: cannot find 'ShardBandGeometry' in scope`.

- [ ] **Step 3: Write the geometry**

Create `Pebbles/Features/Achievements/Figures/ShardBandGeometry.swift`:

```swift
import CoreGraphics

/// The domains' mended band (#1306, the board's `shardStrip`): `count` shards
/// across `width`, each seam leaning by up to 35 % of a cell at its top and its
/// bottom, by a stable hash of its index (FNV-1a, as the stones' crusts are),
/// so the band mends the same way on every device. Android: `ShardBandGeometry`.
struct ShardBandGeometry {
    let width: CGFloat
    let height: CGFloat
    let count: Int

    var cell: CGFloat { width / CGFloat(max(count, 1)) }

    /// A number in [0, 1) from `key`.
    static func unit(_ key: String) -> CGFloat {
        CGFloat(StableHash.fnv1a(key)) / 4_294_967_296
    }

    /// The lean of shard `index`'s right seam at its top (`"t"`) or bottom (`"b"`).
    func jitter(_ index: Int, _ end: String) -> CGFloat {
        (Self.unit("R\(index)\(end)") - 0.5) * cell * 0.7
    }

    /// Shard `index`'s corners: top-left, top-right, bottom-right, bottom-left.
    func shard(_ index: Int) -> [CGPoint] {
        let x = CGFloat(index) * cell
        let last = index == count - 1
        let rightTop = last ? 0 : jitter(index, "t")
        let rightBottom = last ? 0 : jitter(index, "b")
        let leftTop = index == 0 ? 0 : jitter(index - 1, "t")
        let leftBottom = index == 0 ? 0 : jitter(index - 1, "b")
        return [CGPoint(x: x + leftTop, y: 0), CGPoint(x: x + cell + rightTop, y: 0),
                CGPoint(x: x + cell + rightBottom, y: height), CGPoint(x: x + leftBottom, y: height)]
    }

    /// A new domain's dot, 6 below its shard's middle.
    func dot(_ index: Int) -> CGPoint {
        CGPoint(x: (CGFloat(index) + 0.5) * cell, y: height + 6)
    }
}
```

- [ ] **Step 4: Write the band**

Create `Pebbles/Features/Achievements/Figures/DomainBand.swift`:

```swift
import SwiftUI

/// The domains' mended band (#1306, the board's `shardStrip`): the domains
/// entered in the order they came, inked, then those not yet, dotted; a dot
/// under each new one. It fills the width it is given.
struct DomainBand: View {
    let domains: AchievementsPage.Domains
    var height: CGFloat = 30

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        let entered = domains.arrival
        let count = entered.count + domains.notYet.count
        Canvas { c, size in
            let g = ShardBandGeometry(width: size.width, height: height, count: count)
            let paper = c.mixed(inks.ink, 0.08, over: inks.stock)
            for index in 0..<count {
                let shard = Path.polygon(g.shard(index))
                if index < entered.count {
                    c.fill(shard, with: .color(paper))
                    c.stroke(shard, with: .color(inks.ink), style: StrokeStyle(lineWidth: 1.3, lineJoin: .round))
                } else {
                    c.stroke(shard, with: .color(inks.ink2),
                             style: StrokeStyle(lineWidth: 1, lineCap: .round, dash: [1.2, 2.2]))
                }
            }
            for (index, entry) in entered.enumerated() where entry.isNew {
                let dot = Path.circle(g.dot(index), 2.4)
                c.stroke(dot, with: .color(inks.stock), lineWidth: 1.6)
                c.fill(dot, with: .color(inks.ink))
            }
        }
        .frame(height: height + 10)
        .accessibilityHidden(true)
    }
}
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh ShardBandGeometryTests`
Expected: `** TEST SUCCEEDED **`, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements/Figures apps/ios/PebblesTests/Achievements/ShardBandGeometryTests.swift
git commit -m "feat(ios): draw the domains' mended band"
```

---

### Task 14: The pebbles skyline and a stage alone

**Files:**
- Create: `Pebbles/Features/Achievements/Figures/SkylineGeometry.swift`
- Create: `Pebbles/Features/Achievements/Figures/PebbleSkyline.swift`
- Create: `PebblesTests/Achievements/SkylineGeometryTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `PebblesTests/Achievements/SkylineGeometryTests.swift`:

```swift
import CoreGraphics
import Testing
@testable import Pebbles

/// The skyline (#1306, the board's One skyline, g/92): eight stages on a ground
/// line at y 148; the next stage fills from the ground by the server's progress.
@Suite("Skyline geometry")
struct SkylineGeometryTests {
    typealias G = SkylineGeometry

    @Test("eight stages, each parsed, standing on the ground inside its span")
    func stages() {
        #expect(G.stages.count == 8 && G.spans.count == 8 && G.tops.count == 8)
        for i in 0..<8 {
            let box = G.stage(i).boundingRect
            #expect(abs(box.maxY - G.ground) < 0.01, "stage \(i) stands on the ground")
            #expect(abs(box.minX - G.spans[i].lowerBound) < 0.5 && abs(box.maxX - G.spans[i].upperBound) < 0.5)
        }
    }

    @Test("a fill rises from the ground by its fraction, a point past the ground to close the seam")
    func fill() {
        let rect = G.fill(4, fraction: 0.25)
        #expect(abs(rect.minY - (148 - (148 - 121.8) * 0.25)) < 1e-9)
        #expect(abs(rect.maxY - 149) < 1e-9)
        #expect(rect.minX == 97 && rect.width == 27)
        #expect(G.fill(4, fraction: 2).minY == G.fill(4, fraction: 1).minY)
    }

    @Test("the contour runs from x 16 to its end on the ground")
    func contour() {
        let box = G.contour(stages: 8, end: 224).boundingRect
        #expect(abs(box.minX - 16) < 0.01 && abs(box.maxX - 224) < 0.01 && abs(box.maxY - 148) < 0.01)
        #expect(abs(G.contour(stages: 5, end: 126).boundingRect.maxX - 126) < 0.01)
    }

    @Test("a stage's index on the ladder: the step itself, or the last stage for a level past the ladder")
    func stageIndex() {
        let ladder = [1, 10, 25, 50, 100, 250, 500, 1000]
        #expect(G.stageIndex(of: 100, in: ladder) == 4)
        #expect(G.stageIndex(of: 1500, in: ladder) == 7)
        #expect(G.stageIndex(of: 25, in: [10, 25, 50, 75, 100]) == 1)
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh SkylineGeometryTests`
Expected: the build fails with `error: cannot find 'SkylineGeometry' in scope`.

- [ ] **Step 3: Write the geometry**

Create `Pebbles/Features/Achievements/Figures/SkylineGeometry.swift`:

```swift
import SwiftUI

/// The Pebbles card's skyline (#1306, the board's One skyline, g/92): eight
/// stages, one per step of a ladder, standing on a ground line at y 148, in the
/// board's units. Android: `SkylineGeometry`.
enum SkylineGeometry {
    static let ground: CGFloat = 148

    static let stages = [
        "M23 148 C23 143.3 29 143.3 29 148 Z",
        "M32.5 148 C32.1 136.9 34.9 133.2 40.7 133.2 C46.1 133.2 49.9 138.8 49.5 148 Z",
        "M53 148 C52.6 133.5 56.8 128.7 63.3 128.7 C68.8 128.7 72.4 136 72 148 Z",
        "M75.5 148 L76.7 135.5 L83.1 125.2 L89.9 127.5 L95.5 138.4 L95.5 148 Z",
        "M99 148 C98.6 128.4 105 121.8 112.8 121.8 C118.8 121.8 122.4 131.8 122 148 Z",
        "M125.5 148 L125.9 123.5 C126.3 116.7 133.4 116.1 134 122.8 L134 148 Z",
        "M141 148 L141 120.7 L137.5 120.7 C137.2 114.5 145.9 113.9 151.5 113.9 C157.1 113.9 165.8 114.5 165.5 120.7 "
            + "L162 120.7 L162 148 L158 148 L158 120.7 L145 120.7 L145 148 Z",
        "M169 148 C180.5 147.2 182 110.5 194.9 110.5 C208.4 110.5 207.4 145.8 217 148 Z"
    ]

    static let spans: [ClosedRange<CGFloat>] = [23...29, 32.5...49.5, 53...72, 75.5...95.5, 99...122, 125.5...134,
                                                 137.5...165.5, 169...217]
    static let tops: [CGFloat] = [144.5, 133.2, 128.7, 125.2, 121.8, 117.4, 113.9, 110.5]

    /// The card's and the sheet's view box, and the glyph rows' smaller one.
    static let wide = CGRect(x: 12, y: 104, width: 216, height: 48)
    static let narrow = CGRect(x: 18, y: 118, width: 110, height: 32)

    static func stage(_ index: Int) -> Path {
        SVGPathParser.parse(stages[index]).map(Path.init) ?? Path()
    }

    /// The part of stage `index` a fill of `fraction` covers, from the ground up (the board's clip).
    static func fill(_ index: Int, fraction: Double) -> CGRect {
        let span = spans[index]
        let height = (ground - tops[index]) * CGFloat(min(max(fraction, 0), 1))
        return CGRect(x: span.lowerBound - 2, y: ground - height,
                      width: span.upperBound - span.lowerBound + 4, height: height + 1)
    }

    /// The contour over the first `count` stages, from x 16 to `end` on the ground.
    static func contour(stages count: Int, end: CGFloat) -> Path {
        let body = stages.prefix(count)
            .map { "L" + String($0.dropFirst()).replacingOccurrences(of: " Z", with: "") }
            .joined(separator: " ")
        return SVGPathParser.parse("M16 148 \(body) L\(end) 148").map(Path.init) ?? Path()
    }

    /// The stage a step stands on: its place on the ladder, or the last stage for
    /// a level past the drawn ladder (the pebbles' every-500 levels).
    static func stageIndex(of threshold: Int, in ladder: [Int]) -> Int {
        let last = min(ladder.count, stages.count) - 1
        return min(ladder.firstIndex(of: threshold) ?? last, last)
    }
}
```

- [ ] **Step 4: Write the skyline and the stage**

Create `Pebbles/Features/Achievements/Figures/PebbleSkyline.swift`:

```swift
import SwiftUI

/// One counting stack's skyline (#1306): a stage per ladder step, in ink when
/// the server lists it reached, the next one filled from the ground by the
/// server's progress; the contour in ink once every stage stands. The card
/// draws the pebbles wide and the glyph stacks narrow.
struct PebbleSkyline: View {
    let ladder: AchievementsPage.Ladder
    var box: CGRect = SkylineGeometry.wide
    /// Where the contour comes back to the ground.
    var end: CGFloat = 224
    var width: CGFloat = 300

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        let count = min(ladder.ladder.count, SkylineGeometry.stages.count)
        Canvas { context, size in
            var c = context
            let k = size.width / box.width
            c.scaleBy(x: k, y: k)
            c.translateBy(x: -box.minX, y: -box.minY)
            for index in 0..<count {
                let step = ladder.ladder[index]
                if ladder.isReached(step) {
                    c.fill(SkylineGeometry.stage(index), with: .color(inks.ink))
                } else if let next = ladder.next, next.threshold == step, next.progress > 0 {
                    var rising = c
                    rising.clip(to: Path(SkylineGeometry.fill(index, fraction: next.progress)))
                    rising.fill(SkylineGeometry.stage(index), with: .color(inks.ink))
                }
            }
            let standing = (0..<count).allSatisfy { ladder.isReached(ladder.ladder[$0]) }
            c.stroke(SkylineGeometry.contour(stages: count, end: end), with: .color(standing ? inks.ink : inks.ink2),
                     style: StrokeStyle(lineWidth: 1 / k, lineCap: .round, lineJoin: .round))
        }
        .frame(width: width, height: width * box.height / box.width)
        .accessibilityHidden(true)
    }
}

/// One stage alone (the Pebbles sheet's row mark, the board's `stage`): solid
/// when reached, its contour with the fill it has when next, its contour alone
/// when still ahead, on its own ground line, in a 28 pt box.
struct SkylineStage: View {
    let index: Int
    /// 1 reached, the server's progress for the next, 0 ahead.
    let fill: Double
    var scale: CGFloat = 1

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        Canvas { context, size in
            var c = context
            let span = SkylineGeometry.spans[index]
            let top = SkylineGeometry.tops[index]
            let k = min(28 / (span.upperBound - span.lowerBound + 4), 26 / (SkylineGeometry.ground - top + 2)) * scale
            c.translateBy(x: size.width / 2, y: size.height / 2)
            c.scaleBy(x: k, y: k)
            c.translateBy(x: -(span.lowerBound + span.upperBound) / 2, y: -(SkylineGeometry.ground + top) / 2)
            let stage = SkylineGeometry.stage(index)
            if fill >= 1 {
                c.fill(stage, with: .color(inks.ink))
            } else {
                if fill > 0 {
                    var rising = c
                    rising.clip(to: Path(SkylineGeometry.fill(index, fraction: fill)))
                    rising.fill(stage, with: .color(inks.ink))
                }
                c.stroke(stage, with: .color(inks.ink2), style: StrokeStyle(lineWidth: 1 / k, lineJoin: .round))
            }
            c.stroke(Path.segment(CGPoint(x: span.lowerBound - 3, y: SkylineGeometry.ground),
                                  CGPoint(x: span.upperBound + 3, y: SkylineGeometry.ground)),
                     with: .color(inks.ink2), lineWidth: 1 / k)
        }
        .frame(width: 28, height: 28)
        .accessibilityHidden(true)
    }
}
```

`next.threshold == step` finds which stage the server's `next` is; it compares two served thresholds to place a drawing, never a count with a threshold.

- [ ] **Step 5: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh SkylineGeometryTests`
Expected: `** TEST SUCCEEDED **`, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements/Figures apps/ios/PebblesTests/Achievements/SkylineGeometryTests.swift
git commit -m "feat(ios): draw the pebbles skyline"
```

---

### Task 15: The year disc

**Files:**
- Create: `Pebbles/Features/Achievements/Figures/YearDiscGeometry.swift`
- Create: `Pebbles/Features/Achievements/Figures/YearDisc.swift`
- Create: `PebblesTests/Achievements/YearDiscGeometryTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `PebblesTests/Achievements/YearDiscGeometryTests.swift`:

```swift
import CoreGraphics
import Testing
@testable import Pebbles

/// The docked year disc (#1306, the board's g/112 and `yearDiscDocked`): a spoke
/// a month from twelve, a counter docked at its level's radius, its progress
/// along the spoke toward the next.
@Suite("Year disc geometry")
struct YearDiscGeometryTests {
    typealias G = YearDiscGeometry
    typealias Month = AchievementsPage.Month

    static func month(_ number: Int, level: Int, progress: Double) -> Month {
        Month(month: number, state: .kept, family: "joy", momentDays: 0, pebbleDays: 0, fullWeeks: 0, weeksFilled: 0,
              level: level, progress: progress)
    }

    @Test("January points at twelve, then a month every 30° clockwise")
    func spokes() {
        #expect(nearly(G.point(80, month: 1), CGPoint(x: 120, y: 40)))
        #expect(nearly(G.point(80, month: 4), CGPoint(x: 200, y: 120)))
        #expect(nearly(G.point(80, month: 7), CGPoint(x: 120, y: 200)))
    }

    @Test("a counter docks at its level's radius: centre 30, inner disc 50, rim 80")
    func counters() {
        for (level, radius) in [(0, CGFloat(30)), (1, 50), (2, 80)] {
            #expect(abs(distance(G.counter(Self.month(5, level: level, progress: 0)), G.centre) - radius) < 1e-6)
        }
    }

    @Test("the climb runs from 6 past the docked radius by the progress of the gap, 12 short of it")
    func climb() throws {
        let november = try #require(G.climb(Self.month(11, level: 0, progress: 0.5)))
        #expect(abs(distance(november.0, G.centre) - 36) < 1e-6)
        #expect(abs(distance(november.1, G.centre) - 40) < 1e-6)
        let april = try #require(G.climb(Self.month(4, level: 1, progress: 6.0 / 7)))
        #expect(abs(distance(april.1, G.centre) - (56 + 6.0 / 7 * 18)) < 1e-6)
        #expect(G.climb(Self.month(8, level: 2, progress: 0)) == nil)
        #expect(G.climb(Self.month(3, level: 1, progress: 0)) == nil)
        let rim = G.rimClimb(8)
        #expect(abs(distance(rim.0, G.centre) - 56) < 1e-6 && abs(distance(rim.1, G.centre) - 73) < 1e-6)
    }

    @Test("notches cross their spoke at right angles, 6 long; the goal spans ±4.6° on the inner disc")
    func notches() {
        for number in 1...12 {
            let notch = G.notch(80, month: number)
            let tip = G.point(88, month: number)
            let spoke = (x: tip.x - G.centre.x, y: tip.y - G.centre.y)
            let across = (x: notch.1.x - notch.0.x, y: notch.1.y - notch.0.y)
            #expect(abs(spoke.x * across.x + spoke.y * across.y) < 1e-6)
            #expect(abs(distance(notch.0, notch.1) - 6) < 1e-6)
        }
        #expect(nearly(G.notch(50, month: 1).0, CGPoint(x: 117, y: 70)))
        let goal = G.goal(month: 11)
        #expect(abs(distance(goal.0, G.centre) - 50) < 1e-6 && abs(distance(goal.1, G.centre) - 50) < 1e-6)
    }

    @Test("the stone has the board's forty points; twelve numerals, the long ones a point smaller")
    func base() {
        #expect(G.outline.count == 40)
        #expect(G.numerals == ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"])
        #expect(G.numeralSize(3) == 6 && G.numeralSize(1) == 7 && G.numeralSize(12) == 6)
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh YearDiscGeometryTests`
Expected: the build fails with `error: cannot find 'YearDiscGeometry' in scope`.

- [ ] **Step 3: Write the geometry**

Create `Pebbles/Features/Achievements/Figures/YearDiscGeometry.swift`:

```swift
import CoreGraphics
import Foundation

/// The docked year disc (#1306, spec D12; the board's g/112 and
/// `yearDiscDocked`): a stone 220 units across on (120, 120), a spoke a month at
/// 30° × (month − 1) from twelve, notches on the inner disc (50) and the rim
/// (80). A month's counter docks at the radius of the level the server gives
/// (centre 30, inner disc 50, rim 80) and its progress runs along the spoke.
/// Android: `YearDiscGeometry`.
enum YearDiscGeometry {
    static let box = CGRect(x: 10, y: 10, width: 220, height: 220)
    static let centre = CGPoint(x: 120, y: 120)
    /// Level → radius.
    static let radii: [CGFloat] = [30, 50, 80]
    static let spoke: ClosedRange<CGFloat> = 14...88
    static let numeralRadius: CGFloat = 96
    static let numerals = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"]

    /// The stone's edge (the board's g/112 outline).
    static let outline: [CGPoint] = [
        CGPoint(x: 225.1, y: 120.0), CGPoint(x: 223.0, y: 136.3), CGPoint(x: 217.4, y: 151.6), CGPoint(x: 211.9, y: 166.8),
        CGPoint(x: 205.0, y: 181.7), CGPoint(x: 192.4, y: 192.4), CGPoint(x: 181.0, y: 203.9), CGPoint(x: 167.6, y: 213.5),
        CGPoint(x: 151.9, y: 218.2), CGPoint(x: 136.1, y: 221.4), CGPoint(x: 120.0, y: 225.2), CGPoint(x: 103.6, y: 223.5),
        CGPoint(x: 87.6, y: 219.6), CGPoint(x: 72.9, y: 212.5), CGPoint(x: 58.7, y: 204.4), CGPoint(x: 46.3, y: 193.7),
        CGPoint(x: 34.7, y: 182.0), CGPoint(x: 27.8, y: 167.0), CGPoint(x: 20.4, y: 152.4), CGPoint(x: 17.4, y: 136.3),
        CGPoint(x: 15.6, y: 120.0), CGPoint(x: 16.7, y: 103.6), CGPoint(x: 20.4, y: 87.6), CGPoint(x: 27.0, y: 72.6),
        CGPoint(x: 36.8, y: 59.6), CGPoint(x: 45.4, y: 45.4), CGPoint(x: 58.2, y: 35.0), CGPoint(x: 73.4, y: 28.6),
        CGPoint(x: 88.0, y: 21.5), CGPoint(x: 103.9, y: 18.3), CGPoint(x: 120.0, y: 14.9), CGPoint(x: 136.0, y: 18.8),
        CGPoint(x: 152.0, y: 21.7), CGPoint(x: 167.3, y: 27.1), CGPoint(x: 180.7, y: 36.5), CGPoint(x: 192.8, y: 47.2),
        CGPoint(x: 203.3, y: 59.5), CGPoint(x: 213.5, y: 72.4), CGPoint(x: 220.4, y: 87.4), CGPoint(x: 222.5, y: 103.8),
    ]

    static func angle(_ month: Int) -> Double {
        Double(month - 1) * 30
    }

    /// `radius` on month `month`'s spoke, turned `offset` degrees.
    static func point(_ radius: CGFloat, month: Int, offset: Double = 0) -> CGPoint {
        let p = ExplorerWheelGeometry.point(radius, angle(month) + offset)
        return CGPoint(x: p.x + centre.x, y: p.y + centre.y)
    }

    /// Where a month's counter docks: its level's radius.
    static func counter(_ month: AchievementsPage.Month) -> CGPoint {
        point(radii[month.level], month: month.month)
    }

    /// The way travelled toward the next level: from 6 past the docked radius,
    /// `progress` of the gap to the next radius less 12. Nil on the rim or before any.
    static func climb(_ month: AchievementsPage.Month) -> (CGPoint, CGPoint)? {
        guard month.level < 2, month.progress > 0 else { return nil }
        let from = radii[month.level]
        let gap = radii[month.level + 1] - from - 12
        return (point(from + 6, month: month.month), point(from + 6 + CGFloat(month.progress) * gap, month: month.month))
    }

    /// A rim month's whole climb, from the inner disc to the rim.
    static func rimClimb(_ month: Int) -> (CGPoint, CGPoint) {
        (point(radii[1] + 6, month: month), point(radii[2] - 7, month: month))
    }

    /// The notch across month `month`'s spoke at `radius`, 6 long.
    static func notch(_ radius: CGFloat, month: Int) -> (CGPoint, CGPoint) {
        let radians = angle(month) * .pi / 180
        let middle = point(radius, month: month)
        let dx = 3 * CGFloat(cos(radians))
        let dy = 3 * CGFloat(sin(radians))
        return (CGPoint(x: middle.x - dx, y: middle.y - dy), CGPoint(x: middle.x + dx, y: middle.y + dy))
    }

    /// The goal on the inner disc of a month that has begun: ±4.6° about its spoke.
    static func goal(month: Int) -> (CGPoint, CGPoint) {
        (point(radii[1], month: month, offset: 4.6), point(radii[1], month: month, offset: -4.6))
    }

    /// A numeral's size in the disc's units: 7, or 6 for the three- and four-letter ones.
    static func numeralSize(_ month: Int) -> CGFloat {
        numerals[month - 1].count >= 3 ? 6 : 7
    }
}
```

- [ ] **Step 4: Write the disc and the month counter**

Create `Pebbles/Features/Achievements/Figures/YearDisc.swift`:

```swift
import SwiftUI

/// The docked year disc (#1306, NOTES "Year colour and counters", "Quieter
/// disc"): the stone, its two inner rings, a spoke and two notches a month,
/// then each month's counter at its level, its climb and, for a month that has
/// begun at the centre, its goal notch. A month with pebbles wears its family's
/// hue (the server's `family`); a rim month adds the hairline ring, a hued spoke
/// and a hued numeral; a month without pebbles is muted.
struct YearDisc: View {
    let year: AchievementsPage.Year
    let lookups: WrapLookups
    var side: CGFloat = 280

    @Environment(\.colorScheme) private var scheme

    private typealias G = YearDiscGeometry

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        let k = side / G.box.width
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                var c = context
                c.scaleBy(x: k, y: k)
                c.translateBy(x: -G.box.minX, y: -G.box.minY)
                drawBase(c, inks)
                for number in 1...12 {
                    if let month = year.month(number) { drawMonth(c, month, inks) }
                }
                c.stroke(Path.segment(CGPoint(x: 116, y: 120), CGPoint(x: 124, y: 120)), with: .color(inks.ink2),
                         lineWidth: 1.1)
                c.stroke(Path.segment(CGPoint(x: 120, y: 116), CGPoint(x: 120, y: 124)), with: .color(inks.ink2),
                         lineWidth: 1.1)
            }
            ForEach(1...12, id: \.self) { number in
                numeral(number, inks: inks, k: k)
            }
        }
        .frame(width: side, height: side)
        .accessibilityHidden(true)
    }

    /// The month's hue: its family's, once it holds pebbles; nil while muted or unknown.
    private func hue(_ month: AchievementsPage.Month?, _ inks: AchievementInks) -> Color? {
        guard let month, !month.isMuted else { return nil }
        return month.family.flatMap(lookups.family).flatMap { inks.hue($0.palette) }
    }

    private func onRim(_ month: AchievementsPage.Month?) -> Bool {
        month.map { $0.level == 2 && !$0.isMuted } ?? false
    }

    private func numeral(_ number: Int, inks: AchievementInks, k: CGFloat) -> some View {
        let month = year.month(number)
        let rim = onRim(month)
        let place = G.point(G.numeralRadius, month: number)
        return Text(verbatim: G.numerals[number - 1])
            .spineType(.achievementsNumeral(G.numeralSize(number) * k, emphasized: rim))
            .foregroundStyle(rim ? (hue(month, inks) ?? inks.ink2) : inks.ink2)
            .fixedSize()
            .position(x: (place.x - G.box.minX) * k, y: (place.y - G.box.minY) * k)
    }

    private func drawBase(_ c: GraphicsContext, _ inks: AchievementInks) {
        let stone = Path.polygon(G.outline)
        c.fill(stone, with: .color(inks.stock))
        c.stroke(stone, with: .color(inks.ink2.opacity(0.85)), style: StrokeStyle(lineWidth: 1.2, lineJoin: .round))
        c.stroke(Path.circle(G.centre, 52), with: .color(inks.ink2.opacity(0.5)), lineWidth: 1)
        c.stroke(Path.circle(G.centre, 48.5), with: .color(inks.ink2.opacity(0.45)), lineWidth: 0.5)
        for number in 1...12 {
            let month = year.month(number)
            let muted = month?.isMuted ?? true
            let spoke = Path.segment(G.point(G.spoke.lowerBound, month: number), G.point(G.spoke.upperBound, month: number))
            if onRim(month), let hue = self.hue(month, inks) {
                c.stroke(spoke, with: .color(hue), lineWidth: 1)
            } else {
                c.stroke(spoke, with: .color(inks.ink2), lineWidth: muted ? 0.4 : 0.7)
            }
            let inner = G.notch(G.radii[1], month: number)
            c.stroke(Path.segment(inner.0, inner.1), with: .color(inks.ink2), lineWidth: muted ? 0.6 : 1)
            let rim = G.notch(G.radii[2], month: number)
            let quietRim = muted || (month?.level ?? 0) < 2
            c.stroke(Path.segment(rim.0, rim.1), with: .color(inks.ink2), lineWidth: quietRim ? 0.6 : 1)
        }
    }

    private func drawMonth(_ c: GraphicsContext, _ month: AchievementsPage.Month, _ inks: AchievementInks) {
        let hue = self.hue(month, inks)
        let line = StrokeStyle(lineWidth: 1.8, lineCap: .round)
        if month.level == 0, month.progress > 0, !month.isMuted {
            let goal = G.goal(month: month.month)
            c.stroke(Path.segment(goal.0, goal.1), with: .color(inks.ink), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
        }
        if month.level == 2 {
            let climb = G.rimClimb(month.month)
            c.stroke(Path.segment(climb.0, climb.1), with: .color(hue ?? inks.ink), style: line)
        }
        if let climb = G.climb(month) {
            // Toward the inner disc in ink (moments have no colour); from it on, in the month's hue.
            let ink = month.level == 1 ? (hue ?? inks.ink) : inks.ink
            c.stroke(Path.segment(climb.0, climb.1), with: .color(ink), style: line)
        }
        c.drawMonthCounter(month, at: G.counter(month), hue: hue, width: 12.5, inks: inks)
    }
}

extension GraphicsContext {
    /// A month's counter (NOTES "Decided (B)"): with pebbles, the solid seal in
    /// its hue, ringed once on the rim; without, the blank seal in secondary ink,
    /// dotted while the month is still to come.
    func drawMonthCounter(_ month: AchievementsPage.Month?, at center: CGPoint, hue: Color?, width: CGFloat,
                          inks: AchievementInks) {
        if let hue {
            if month?.level == 2 { stroke(Path.circle(center, width * 0.78), with: .color(inks.ink), lineWidth: 0.8) }
            draw(FlatSeal(size: .medium, outline: .neutral, print: .solid, width: width), at: center, color: hue,
                 inks: inks)
        } else {
            draw(FlatSeal(size: .medium, outline: .neutral, print: .blank, width: width), at: center, color: inks.ink2,
                 inks: inks, dashed: month?.state == .upcoming)
        }
    }
}
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh YearDiscGeometryTests`
Expected: `** TEST SUCCEEDED **`, 5 tests.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements/Figures apps/ios/PebblesTests/Achievements/YearDiscGeometryTests.swift
git commit -m "feat(ios): draw the docked year disc"
```

---

### Task 16: The figures' references, Paper and Night; part 2 gate and PR

**Files:**
- Create: `PebblesTests/Achievements/AchievementsLookups.swift`
- Create: `PebblesTests/References/AchievementsFigureReferences.swift`

- [ ] **Step 1: Write the fixture's lookups**

Create `PebblesTests/Achievements/AchievementsLookups.swift`:

```swift
import Foundation
@testable import Pebbles

/// The design fixture's ids given faces (#1306): each seat's emotion takes its
/// family's fixture palette (`SpineFixtureWeek.families`, which has no shame
/// family, so shame draws in ink: the unknown-family fallback, on purpose) and
/// the board's name for that seat; the domains are named in the order they came.
@MainActor
enum AchievementsLookups {
    static let emotionNames = [
        "Amused", "Cheerful", "Content", "Excited", "Grateful", "Joyful",
        "Accomplished", "Confident", "Proud", "Respected", "Worthy",
        "Angry", "Annoyed", "Bitter", "Frustrated", "Irritated", "Resentful",
        "Anxious", "Insecure", "Nervous", "Overwhelmed", "Scared", "Worried",
        "Ashamed", "Embarrassed", "Guilty", "Humiliated",
        "Disappointed", "Hurt", "Lonely", "Melancholic", "Sad", "Tired",
        "Calm", "Grounded", "Peaceful", "Relaxed", "Relieved"
    ]

    static let domainNames = ["Family", "Friends", "Home", "Work", "Health", "Partner", "Travel", "Hobbies", "Learning",
                              "Identity", "Nature", "Money", "Body", "Events", "Spirituality", "Community",
                              "Creativity", "Rest"]

    static func fixture(_ page: AchievementsPage) -> WrapLookups {
        let families = Dictionary(uniqueKeysWithValues: SpineFixtureWeek.families.map { ($0.slug, $0) })
        var emotions: [String: EmotionWithPalette] = [:]
        for seat in page.emotions?.seats ?? [] {
            guard let category = families[seat.family], emotionNames.indices.contains(seat.seat) else { continue }
            emotions[seat.emotionId] = row(seat.emotionId, name: emotionNames[seat.seat], category: category)
        }
        let ids = (page.domains?.arrival.map(\.domainId) ?? []) + (page.domains?.notYet ?? [])
        var domains: [String: Domain] = [:]
        for (index, id) in ids.enumerated() {
            let name = domainNames[index % domainNames.count]
            // A slug no catalog entry has, so `localizedName` reads the fixture's name in every language.
            domains[id] = Domain(id: UUID(uuidString: id) ?? UUID(), slug: "fixture-\(name.lowercased())", name: name,
                                 label: name)
        }
        return WrapLookups(family: { families[$0] }, domain: { domains[$0] }, emotion: { emotions[$0] })
    }

    private static func row(_ id: String, name: String, category: EmotionCategory) -> EmotionWithPalette? {
        let palette = category.palette
        let json = """
        {"id":"\(id)","slug":"fixture-\(name.lowercased())","name":"\(name)","emoji":"🙂",
         "category_id":"\(category.id.uuidString)","category_slug":"\(category.slug)","category_name":"\(category.name)",
         "primary_color":"\(palette.primaryHex)","secondary_color":"\(palette.secondaryHex)",
         "light_color":"\(palette.lightHex)","surface_color":"\(palette.surfaceHex)",
         "dark_color":"\(palette.darkHex)","shaded_color":"\(palette.shadedHex)"}
        """
        return try? JSONDecoder().decode(EmotionWithPalette.self, from: Data(json.utf8))
    }
}
```

The `try?` here is a fixture: a palette the fixture cannot decode leaves the seat unresolved, which draws in ink, the very fallback the references show for shame.

- [ ] **Step 2: Write the references**

Create `PebblesTests/References/AchievementsFigureReferences.swift`:

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1306: the achievements page's five figures on the design fixture, Paper
    /// and Night: the wheel, the rosace, the band, the three skylines, the disc.
    @Suite("Achievements figure references")
    @MainActor
    struct AchievementsFigureReferences {
        static func figures() throws -> some View {
            let page = try AchievementsFixtures.payload("design-fixture").model
            let lookups = AchievementsLookups.fixture(page)
            let explorer = try #require(page.explorer)
            let emotions = try #require(page.emotions)
            let domains = try #require(page.domains)
            let pebbles = try #require(page.pebbles)
            let carved = try #require(page.glyphsCarved)
            let sales = try #require(page.glyphSales)
            let year = try #require(page.years.first)
            return VStack(spacing: 24) {
                ExplorerWheel(explorer: explorer, emotions: emotions, domains: domains, lookups: lookups)
                EmotionRosace(emotions: emotions, lookups: lookups)
                DomainBand(domains: domains).frame(width: 300)
                PebbleSkyline(ladder: pebbles)
                HStack(spacing: 24) {
                    PebbleSkyline(ladder: carved, box: SkylineGeometry.narrow, end: 126, width: 103)
                    PebbleSkyline(ladder: sales, box: SkylineGeometry.narrow, end: 126, width: 103)
                }
                YearDisc(year: year, lookups: lookups)
                HStack(spacing: 8) {
                    ForEach(0..<8, id: \.self) { index in
                        SkylineStage(index: index, fill: index < 5 ? 1 : index == 5 ? 0.247 : 0)
                    }
                }
            }
            .padding(24)
            .background(Color.pebbles.stock)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func figures(scheme: ReferenceScheme) throws {
            ReferenceImage.assertMatches(try Self.figures(), named: "figures",
                                         config: ReferenceConfig(scheme: scheme, width: nil))
        }
    }
}
```

- [ ] **Step 3: Run the references and look at the renders**

Run: `apps/ios/build/ach-test.sh References`
Expected: the baseline's failures (Task 1 step 6) plus two new lines, `figures: no reference at …/AchievementsFigureReferences/figures-paper-en.png` and the same for `-night-en`, each naming an `Actual at …` path. Nothing else.

Open both actual PNGs (written to `apps/ios/build/reference-diffs/AchievementsFigureReferences/`; the Read tool shows images) beside the boards' renders in the design folder (`final/_shots/`, or `final/index.html` and `final/sheets.html` in a browser) and compare: the wheel's five rings and its 23 coloured seats with the two new marks on sad and peaceful, shame's four seats in ink (the fixture's missing family), the band's fourteen inked shards with a dot under the last, five stages standing and the sixth a quarter up, August alone on the rim with its ring, November just off the centre with its goal notch, December dotted. Paper and Night must both read: no stroke may vanish against the stock at Night.

- [ ] **Step 4: Run every unit test but the references, and the lint**

Run: `apps/ios/build/ach-test.sh "" References`, then `npm run lint --workspace=@pbbls/ios`
Expected: `** TEST SUCCEEDED **`; no `error:`. `ThemeLiteralsTests` confirms no literal colour crept into `Features/Achievements/`.

- [ ] **Step 5: Open the PR**

```bash
gh stack submit --auto
```

Title: `feat(ios): draw the achievements figures`. Body opens with `Part of #1306`; same labels and milestone as part 1, plus the `rebaseline-ios-screenshots` label, added alone (the bot records the two PNGs; approve its held runs). `no-lab-note`: nothing reaches a screen yet. Check out the bot's commit before stacking the next part (`git fetch` and fast-forward), since the upper branch must carry the PNGs.

---

# Part 3 — the page (branch `feat/1306-ios-achievements-page`, stacked on part 2)

- [ ] **Start the branch:** `gh stack add feat/1306-ios-achievements-page`

### Task 17: Accessibility labels assembled from the payload

**Files:**
- Create: `PebblesTests/Achievements/Fixtures/new-user.json`
- Create: `Pebbles/Features/Achievements/Page/AchievementsA11y.swift`
- Modify: `Pebbles/Features/Achievements/Copy/AchievementsCopy.swift` (three labels)
- Modify: `Pebbles/Resources/Localizable.xcstrings` (through the helper)
- Create: `PebblesTests/Achievements/AchievementsA11yTests.swift`

- [ ] **Step 1: Write the new user's fixture**

A user one day in: one moment, nothing else, in the same shape. Create `PebblesTests/Achievements/Fixtures/new-user.json`:

```json
{
  "since": "2026-10-29",
  "days_kept": 1,
  "explorer": {
    "first_five": [
      {"slug": "first-moment", "done": true, "new": false, "on": "2026-10-29", "subject": {"kind": "moment", "id": "a0000000-0000-4000-8000-0000000000c8", "name": "First light"}},
      {"slug": "first-pebble", "done": false, "new": false},
      {"slug": "first-collection", "done": false, "new": false},
      {"slug": "first-soul", "done": false, "new": false},
      {"slug": "first-glyph", "done": false, "new": false}
    ],
    "six_firsts": [
      {"kind": "small", "done": false, "new": false},
      {"kind": "medium", "done": false, "new": false},
      {"kind": "large", "done": false, "new": false},
      {"kind": "lowlight", "done": false, "new": false},
      {"kind": "neutral", "done": false, "new": false},
      {"kind": "highlight", "done": false, "new": false}
    ],
    "twenty_five": {"count": 0, "done": false, "new": false},
    "rings_done": []
  },
  "emotions": {
    "count": 0,
    "of": 38,
    "seats": [
      {"seat": 0, "emotion_id": "e0000000-0000-4000-8000-000000000001", "family": "joy", "met": false, "new": false},
      {"seat": 1, "emotion_id": "e0000000-0000-4000-8000-000000000002", "family": "joy", "met": false, "new": false},
      {"seat": 2, "emotion_id": "e0000000-0000-4000-8000-000000000003", "family": "joy", "met": false, "new": false},
      {"seat": 3, "emotion_id": "e0000000-0000-4000-8000-000000000004", "family": "joy", "met": false, "new": false},
      {"seat": 4, "emotion_id": "e0000000-0000-4000-8000-000000000005", "family": "joy", "met": false, "new": false},
      {"seat": 5, "emotion_id": "e0000000-0000-4000-8000-000000000006", "family": "joy", "met": false, "new": false},
      {"seat": 6, "emotion_id": "e0000000-0000-4000-8000-000000000007", "family": "pride", "met": false, "new": false},
      {"seat": 7, "emotion_id": "e0000000-0000-4000-8000-000000000008", "family": "pride", "met": false, "new": false},
      {"seat": 8, "emotion_id": "e0000000-0000-4000-8000-000000000009", "family": "pride", "met": false, "new": false},
      {"seat": 9, "emotion_id": "e0000000-0000-4000-8000-00000000000a", "family": "pride", "met": false, "new": false},
      {"seat": 10, "emotion_id": "e0000000-0000-4000-8000-00000000000b", "family": "pride", "met": false, "new": false},
      {"seat": 11, "emotion_id": "e0000000-0000-4000-8000-00000000000c", "family": "anger", "met": false, "new": false},
      {"seat": 12, "emotion_id": "e0000000-0000-4000-8000-00000000000d", "family": "anger", "met": false, "new": false},
      {"seat": 13, "emotion_id": "e0000000-0000-4000-8000-00000000000e", "family": "anger", "met": false, "new": false},
      {"seat": 14, "emotion_id": "e0000000-0000-4000-8000-00000000000f", "family": "anger", "met": false, "new": false},
      {"seat": 15, "emotion_id": "e0000000-0000-4000-8000-000000000010", "family": "anger", "met": false, "new": false},
      {"seat": 16, "emotion_id": "e0000000-0000-4000-8000-000000000011", "family": "anger", "met": false, "new": false},
      {"seat": 17, "emotion_id": "e0000000-0000-4000-8000-000000000012", "family": "fear", "met": false, "new": false},
      {"seat": 18, "emotion_id": "e0000000-0000-4000-8000-000000000013", "family": "fear", "met": false, "new": false},
      {"seat": 19, "emotion_id": "e0000000-0000-4000-8000-000000000014", "family": "fear", "met": false, "new": false},
      {"seat": 20, "emotion_id": "e0000000-0000-4000-8000-000000000015", "family": "fear", "met": false, "new": false},
      {"seat": 21, "emotion_id": "e0000000-0000-4000-8000-000000000016", "family": "fear", "met": false, "new": false},
      {"seat": 22, "emotion_id": "e0000000-0000-4000-8000-000000000017", "family": "fear", "met": false, "new": false},
      {"seat": 23, "emotion_id": "e0000000-0000-4000-8000-000000000018", "family": "shame", "met": false, "new": false},
      {"seat": 24, "emotion_id": "e0000000-0000-4000-8000-000000000019", "family": "shame", "met": false, "new": false},
      {"seat": 25, "emotion_id": "e0000000-0000-4000-8000-00000000001a", "family": "shame", "met": false, "new": false},
      {"seat": 26, "emotion_id": "e0000000-0000-4000-8000-00000000001b", "family": "shame", "met": false, "new": false},
      {"seat": 27, "emotion_id": "e0000000-0000-4000-8000-00000000001c", "family": "sadness", "met": false, "new": false},
      {"seat": 28, "emotion_id": "e0000000-0000-4000-8000-00000000001d", "family": "sadness", "met": false, "new": false},
      {"seat": 29, "emotion_id": "e0000000-0000-4000-8000-00000000001e", "family": "sadness", "met": false, "new": false},
      {"seat": 30, "emotion_id": "e0000000-0000-4000-8000-00000000001f", "family": "sadness", "met": false, "new": false},
      {"seat": 31, "emotion_id": "e0000000-0000-4000-8000-000000000020", "family": "sadness", "met": false, "new": false},
      {"seat": 32, "emotion_id": "e0000000-0000-4000-8000-000000000021", "family": "sadness", "met": false, "new": false},
      {"seat": 33, "emotion_id": "e0000000-0000-4000-8000-000000000022", "family": "peace", "met": false, "new": false},
      {"seat": 34, "emotion_id": "e0000000-0000-4000-8000-000000000023", "family": "peace", "met": false, "new": false},
      {"seat": 35, "emotion_id": "e0000000-0000-4000-8000-000000000024", "family": "peace", "met": false, "new": false},
      {"seat": 36, "emotion_id": "e0000000-0000-4000-8000-000000000025", "family": "peace", "met": false, "new": false},
      {"seat": 37, "emotion_id": "e0000000-0000-4000-8000-000000000026", "family": "peace", "met": false, "new": false}
    ]
  },
  "domains": {
    "count": 0,
    "of": 18,
    "coverage": "none",
    "entered": [],
    "not_yet": ["d0000000-0000-4000-8000-000000000001", "d0000000-0000-4000-8000-000000000002", "d0000000-0000-4000-8000-000000000003", "d0000000-0000-4000-8000-000000000004", "d0000000-0000-4000-8000-000000000005", "d0000000-0000-4000-8000-000000000006", "d0000000-0000-4000-8000-000000000007", "d0000000-0000-4000-8000-000000000008", "d0000000-0000-4000-8000-000000000009", "d0000000-0000-4000-8000-00000000000a", "d0000000-0000-4000-8000-00000000000b", "d0000000-0000-4000-8000-00000000000c", "d0000000-0000-4000-8000-00000000000d", "d0000000-0000-4000-8000-00000000000e", "d0000000-0000-4000-8000-00000000000f", "d0000000-0000-4000-8000-000000000010", "d0000000-0000-4000-8000-000000000011", "d0000000-0000-4000-8000-000000000012"]
  },
  "pebbles": {
    "count": 0,
    "levels": [],
    "next": {"threshold": 1, "left": 1, "progress": 0},
    "ladder": [1, 10, 25, 50, 100, 250, 500, 1000],
    "then_every": 500
  },
  "glyphs_carved": {
    "count": 0,
    "levels": [],
    "next": {"threshold": 10, "left": 10, "progress": 0},
    "ladder": [10, 25, 50, 75, 100]
  },
  "glyph_sales": {
    "count": 0,
    "levels": [],
    "next": {"threshold": 1, "left": 1, "progress": 0},
    "ladder": [1, 5, 10, 25, 50]
  },
  "years": [
    {
      "year": 2026,
      "current": true,
      "months": [
        {"month": 1, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0},
        {"month": 2, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0},
        {"month": 3, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0},
        {"month": 4, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0},
        {"month": 5, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0},
        {"month": 6, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0},
        {"month": 7, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0},
        {"month": 8, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0},
        {"month": 9, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0},
        {"month": 10, "state": "quiet", "moment_days": 1, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0},
        {"month": 11, "state": "upcoming", "moment_days": 0, "pebble_days": 0, "full_weeks": 0, "weeks_filled": 0, "level": 0, "progress": 0},
        {"month": 12, "state": "upcoming", "moment_days": 0, "pebble_days": 0, "full_weeks": 0, "weeks_filled": 0, "level": 0, "progress": 0}
      ],
      "listed": [10],
      "next_step": {"month": 10, "needs": "moments", "days_left": 13, "reachable": false}
    }
  ]
}
```

- [ ] **Step 2: Write the failing tests**

Create `PebblesTests/Achievements/AchievementsA11yTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// VoiceOver labels assembled from the payload (#1306, spec §5): each card reads
/// as the server's values, the disc as the server's lists, never as a drawing.
@Suite("Achievements accessibility labels")
struct AchievementsA11yTests {
    static let en = Locale(identifier: "en")
    static let fr = Locale(identifier: "fr")
    static var paris: Calendar {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris")!
        return calendar
    }

    static func design() throws -> AchievementsPage { try AchievementsFixtures.payload("design-fixture").model }
    static func newUser() throws -> AchievementsPage { try AchievementsFixtures.payload("new-user").model }

    @Test("the header: the days kept, then since when")
    func header() {
        let since = CalendarDay(year: 2026, month: 3, day: 1)
        #expect(AchievementsA11y.header(daysKept: 249, since: since, calendar: Self.paris, locale: Self.en, dateLocale: Self.en)
            == "249 days, since Sunday, March 1, 2026")
        #expect(AchievementsA11y.header(daysKept: 249, since: since, calendar: Self.paris, locale: Self.fr, dateLocale: Self.fr)
            == "249 jours, depuis le dimanche 1 mars 2026")
    }

    @Test("Explorer: each ring done or how far, then what is new")
    func explorer() throws {
        let page = try Self.design()
        let explorer = try #require(page.explorer)
        #expect(AchievementsA11y.explorer(explorer, emotions: page.emotions, domains: page.domains, locale: Self.en)
            == "Explorer: the first five, done; the six firsts, done; 25 pebbles, done; domains, 14 of 18; emotions, 23 of 38. 3 new.")
        #expect(AchievementsA11y.explorer(explorer, emotions: page.emotions, domains: page.domains, locale: Self.fr)
            == "Explorateur : les cinq premiers, c'est fait ; les six premières fois, c'est fait ; 25 galets, c'est fait ; domaines, 14 sur 18 ; émotions, 23 sur 38. 3 nouveautés.")
        let fresh = try Self.newUser()
        #expect(AchievementsA11y.explorer(try #require(fresh.explorer), emotions: fresh.emotions, domains: fresh.domains,
                                          locale: Self.en)
            == "Explorer: the first five, 1 of 5; the six firsts, 0 of 6; 25 pebbles, 0 of 25; domains, 0 of 18; emotions, 0 of 38.")
    }

    @Test("Emotions and Domains: the count, the coverage's meaning, what is new")
    func emotionsAndDomains() throws {
        let page = try Self.design()
        #expect(AchievementsA11y.emotions(try #require(page.emotions), locale: Self.en) == "Emotions, 23 of 38. 2 new.")
        #expect(AchievementsA11y.emotions(try #require(page.emotions), locale: Self.fr) == "Émotions, 23 sur 38. 2 nouveautés.")
        #expect(AchievementsA11y.domains(try #require(page.domains), locale: Self.en)
            == "Domains, 14 of 18. Your pebbles reach most parts of your life. Your insights have plenty to read. 1 new.")
    }

    @Test("Pebbles: the line, then the glyph stacks")
    func pebbles() throws {
        let page = try Self.design()
        #expect(AchievementsA11y.pebbles(try #require(page.pebbles), carved: page.glyphsCarved, sales: page.glyphSales,
                                         locale: Self.en)
            == "Pebbles. 137 pebbles. Five stones stand; the sixth rises toward 250. Glyphs carved: 12. Glyph sales: 3.")
        let fresh = try Self.newUser()
        #expect(AchievementsA11y.pebbles(try #require(fresh.pebbles), carved: fresh.glyphsCarved, sales: fresh.glyphSales,
                                         locale: Self.fr)
            == "Galets. 0 galet. Aucune pierre ne se dresse encore ; la première s'élève vers 1. Glyphes gravés : 0. Ventes de glyphes : 0.")
    }

    @Test("the year reads as the server's lists, then the next step")
    func year() throws {
        let year = try #require(try Self.design().years.first)
        #expect(AchievementsA11y.year(year, locale: Self.en)
            == "The year 2026. On the rim: August. On the inner disc: March, April, May, June, July, September, and October. "
            + "On their way: November. Twelve more days with a moment, one in each full week, bring November to the inner disc.")
        #expect(AchievementsA11y.year(year, locale: Self.fr)
            == "L'année 2026. Au bord : août. Sur le disque intérieur : mars, avril, mai, juin, juillet, septembre et octobre. "
            + "En chemin : novembre. Douze jours de plus avec un moment, un dans chaque semaine complète : novembre rejoindra le disque intérieur.")
        let quiet = try #require(try Self.newUser().years.first)
        #expect(AchievementsA11y.year(quiet, locale: Self.en)
            == "The year 2026. On the rim: none. On the inner disc: none. On their way: none. "
            + "In October, a full week passed without a moment, so the month stays at the centre.")
    }

    @Test("a ready mark says which ceremony it opens")
    func ready() {
        #expect(AchievementsA11y.ready(.sixFirsts, locale: Self.en) == "Ready to open: The six firsts")
        #expect(AchievementsA11y.ready(.emotions, locale: Self.fr) == "Prêt à ouvrir : Toutes les émotions rencontrées")
        #expect(AchievementsCopy.ready(locale: Self.fr) == "prêt")
        #expect(AchievementsCopy.glyphsCarved(locale: Self.en) == "Glyphs carved")
    }
}
```

- [ ] **Step 3: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh AchievementsA11yTests`
Expected: the build fails with `error: cannot find 'AchievementsA11y' in scope`.

- [ ] **Step 4: Add the strings**

Create `apps/ios/build/strings-task17.json`:

```json
{
  "achievements.a11y.header": {"en": "%1$@, %2$@", "fr": "%1$@, %2$@"},
  "achievements.a11y.explorer": {"en": "Explorer: %@.", "fr": "Explorateur : %@."},
  "achievements.a11y.join": {"en": "; ", "fr": " ; "},
  "achievements.a11y.ring.first_five": {"en": "the first five", "fr": "les cinq premiers"},
  "achievements.a11y.ring.six_firsts": {"en": "the six firsts", "fr": "les six premières fois"},
  "achievements.a11y.ring.twenty_five": {"en": "25 pebbles", "fr": "25 galets"},
  "achievements.a11y.ring.domains": {"en": "domains", "fr": "domaines"},
  "achievements.a11y.ring.emotions": {"en": "emotions", "fr": "émotions"},
  "achievements.a11y.ringDone": {"en": "%@, done", "fr": "%@, c'est fait"},
  "achievements.a11y.ringPart": {"en": "%1$@, %2$@ of %3$@", "fr": "%1$@, %2$@ sur %3$@"},
  "achievements.a11y.new": {"word": true, "template": {"en": "%2$@ %1$#@body@.", "fr": "%2$@ %1$#@body@."}, "en": {"one": "new", "other": "new"}, "fr": {"one": "nouveauté", "many": "nouveautés", "other": "nouveautés"}},
  "achievements.a11y.emotions": {"en": "Emotions, %1$@ of %2$@.", "fr": "Émotions, %1$@ sur %2$@."},
  "achievements.a11y.domains": {"en": "Domains, %1$@ of %2$@.", "fr": "Domaines, %1$@ sur %2$@."},
  "achievements.a11y.pebbles": {"en": "Pebbles. %@", "fr": "Galets. %@"},
  "achievements.a11y.glyphsCarved": {"en": "Glyphs carved: %@.", "fr": "Glyphes gravés : %@."},
  "achievements.a11y.glyphSales": {"en": "Glyph sales: %@.", "fr": "Ventes de glyphes : %@."},
  "achievements.a11y.year": {"en": "The year %1$@. On the rim: %2$@. On the inner disc: %3$@. On their way: %4$@.", "fr": "L'année %1$@. Au bord : %2$@. Sur le disque intérieur : %3$@. En chemin : %4$@."},
  "achievements.a11y.none": {"en": "none", "fr": "aucun"},
  "achievements.a11y.ready": {"en": "Ready to open: %@", "fr": "Prêt à ouvrir : %@"},
  "achievements.ready": {"en": "ready", "fr": "prêt"},
  "achievements.card.glyphsCarved": {"en": "Glyphs carved", "fr": "Glyphes gravés"},
  "achievements.card.glyphSales": {"en": "Glyph sales", "fr": "Ventes de glyphes"}
}
```

Run: `python3 -I apps/ios/build/xcstrings-add.py apps/ios/Pebbles/Resources/Localizable.xcstrings apps/ios/build/strings-task17.json`
Expected: `added 22 entries`.

- [ ] **Step 5: Add the three labels to the copy**

In `AchievementsCopy.swift`, after `retry(locale:)`, add:

```swift
    // MARK: - The page's small labels

    /// "ready": a set whose ceremony can be pulled.
    static func ready(locale: Locale) -> String { SpineStrings.string("achievements.ready", locale: locale) }
    static func glyphsCarved(locale: Locale) -> String { SpineStrings.string("achievements.card.glyphsCarved", locale: locale) }
    static func glyphSales(locale: Locale) -> String { SpineStrings.string("achievements.card.glyphSales", locale: locale) }
```

- [ ] **Step 6: Write the labels**

Create `Pebbles/Features/Achievements/Page/AchievementsA11y.swift`:

```swift
import Foundation

/// The page's VoiceOver labels (#1306, spec §5), assembled from the payload:
/// each card reads as its values (a ring done, or how many of how many; the
/// coverage's meaning; the pebbles line), the disc as the server's lists (on
/// the rim, on the inner disc, on their way) and what is new as a count of the
/// server's `new` flags. The figures themselves are hidden from VoiceOver.
/// Android: `AchievementsA11y`.
enum AchievementsA11y {
    typealias Page = AchievementsPage

    /// "249 days, since Sunday, March 1, 2026".
    static func header(daysKept: Int, since: CalendarDay?, calendar: Calendar, locale: Locale,
                       dateLocale: Locale) -> String {
        let days = AchievementsCopy.number(daysKept, locale: locale) + " "
            + AchievementsCopy.daysUnit(daysKept, locale: locale)
        guard let since else { return days }
        return AchievementsCopy.format("achievements.a11y.header", locale, days,
                                       AchievementsCopy.since(since, calendar: calendar, locale: locale,
                                                              dateLocale: dateLocale))
    }

    /// "Explorer: the first five, done; …; emotions, 23 of 38. 3 new."
    static func explorer(_ explorer: Page.Explorer, emotions: Page.Emotions?, domains: Page.Domains?,
                         locale: Locale) -> String {
        var rings = [
            ring(.firstFive, explorer, count: explorer.firstFive.filter(\.done).count, of: explorer.firstFive.count, locale),
            ring(.sixFirsts, explorer, count: explorer.sixFirsts.filter(\.done).count, of: explorer.sixFirsts.count, locale)
        ]
        if let twentyFive = explorer.twentyFive {
            rings.append(ring(.twentyFive, explorer, count: twentyFive.count,
                              of: ExplorerWheelGeometry.twentyFiveSeats, locale))
        }
        if let domains { rings.append(ring(.domains, explorer, count: domains.count, of: domains.of, locale)) }
        if let emotions { rings.append(ring(.emotions, explorer, count: emotions.count, of: emotions.of, locale)) }
        // Summed in steps: one long expression defeats the type checker.
        let ownNew = explorer.firstFive.filter(\.isNew).count + explorer.sixFirsts.filter(\.isNew).count
        let quarterNew = explorer.twentyFive?.isNew == true ? 1 : 0
        let seatsNew = emotions?.seats.filter(\.isNew).count ?? 0
        let domainsNew = domains?.entered.filter(\.isNew).count ?? 0
        let fresh = ownNew + quarterNew + seatsNew + domainsNew
        let body = rings.joined(separator: SpineStrings.string("achievements.a11y.join", locale: locale))
        return AchievementsCopy.format("achievements.a11y.explorer", locale, body) + newSuffix(fresh, locale)
    }

    /// "Emotions, 23 of 38. 2 new."
    static func emotions(_ emotions: Page.Emotions, locale: Locale) -> String {
        AchievementsCopy.format("achievements.a11y.emotions", locale, number(emotions.count, locale),
                                number(emotions.of, locale))
            + newSuffix(emotions.seats.filter(\.isNew).count, locale)
    }

    /// "Domains, 14 of 18. <what the coverage means>. 1 new."
    static func domains(_ domains: Page.Domains, locale: Locale) -> String {
        var label = AchievementsCopy.format("achievements.a11y.domains", locale, number(domains.count, locale),
                                            number(domains.of, locale))
        if let meaning = AchievementsCopy.coverage(domains.coverage, locale: locale) { label += " " + meaning }
        return label + newSuffix(domains.entered.filter(\.isNew).count, locale)
    }

    /// "Pebbles. 137 pebbles. Five stones stand; … Glyphs carved: 12. Glyph sales: 3."
    static func pebbles(_ pebbles: Page.Ladder, carved: Page.Ladder?, sales: Page.Ladder?, locale: Locale) -> String {
        var parts = [AchievementsCopy.format("achievements.a11y.pebbles", locale,
                                             AchievementsCopy.pebblesLine(pebbles, locale: locale))]
        if let carved {
            parts.append(AchievementsCopy.format("achievements.a11y.glyphsCarved", locale, number(carved.count, locale)))
        }
        if let sales {
            parts.append(AchievementsCopy.format("achievements.a11y.glyphSales", locale, number(sales.count, locale)))
        }
        let fresh = [pebbles, carved, sales].compactMap { $0 }.map { $0.levels.filter(\.isNew).count }.reduce(0, +)
        return parts.joined(separator: " ") + newSuffix(fresh, locale)
    }

    /// "The year 2026. On the rim: August. On the inner disc: …. On their way: November. <next step>"
    static func year(_ year: Page.Year, locale: Locale) -> String {
        let begun = year.months.filter { $0.state != .upcoming }
        var label = AchievementsCopy.format(
            "achievements.a11y.year", locale, String(year.year),
            names(begun.filter { $0.level == 2 }, locale),
            names(begun.filter { $0.level == 1 }, locale),
            names(begun.filter { $0.level == 0 && $0.progress > 0 }, locale))
        if let step = year.nextStep { label += " " + AchievementsCopy.nextStep(step, locale: locale) }
        return label
    }

    /// "Ready to open: The six firsts".
    static func ready(_ stack: Page.CeremonyStack, locale: Locale) -> String {
        AchievementsCopy.format("achievements.a11y.ready", locale, AchievementsCopy.ceremonyTitle(stack, locale: locale))
    }

    // MARK: - Helpers

    private static func ring(_ ring: Page.Ring, _ explorer: Page.Explorer, count: Int, of: Int,
                             _ locale: Locale) -> String {
        let name = SpineStrings.string("achievements.a11y.ring.\(ring.rawValue)", locale: locale)
        if explorer.ringsDone.contains(ring) { return AchievementsCopy.format("achievements.a11y.ringDone", locale, name) }
        return AchievementsCopy.format("achievements.a11y.ringPart", locale, name, number(count, locale), number(of, locale))
    }

    /// " 3 new." after a label; nothing when nothing is new.
    private static func newSuffix(_ count: Int, _ locale: Locale) -> String {
        guard count > 0 else { return "" }
        return " " + AchievementsCopy.format("achievements.a11y.new", locale, count, number(count, locale))
    }

    private static func names(_ months: [Page.Month], _ locale: Locale) -> String {
        guard !months.isEmpty else { return SpineStrings.string("achievements.a11y.none", locale: locale) }
        return months.map { AchievementsCopy.monthInSentence($0.month, locale: locale) }
            .formatted(.list(type: .and).locale(locale))
    }

    private static func number(_ value: Int, _ locale: Locale) -> String {
        AchievementsCopy.number(value, locale: locale)
    }
}
```

- [ ] **Step 7: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh AchievementsA11yTests`
Expected: `** TEST SUCCEEDED **`, 6 tests. The list joiner is Foundation's (`, and` in English, ` et ` in French); if the French expectation differs by a narrow no-break space, the catalog value carries it, not the test: the app writes plain spaces.

- [ ] **Step 8: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements apps/ios/Pebbles/Resources/Localizable.xcstrings \
  apps/ios/PebblesTests/Achievements/Fixtures/new-user.json apps/ios/PebblesTests/Achievements/AchievementsA11yTests.swift
git commit -m "feat(ios): label the achievements cards for voiceover from the payload"
```

---

### Task 18: The margin ladders

**Files:**
- Create: `Pebbles/Features/Achievements/Page/MarginLadder.swift`
- Create: `PebblesTests/Achievements/MarginLadderTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `PebblesTests/Achievements/MarginLadderTests.swift`:

```swift
import Testing
@testable import Pebbles

/// Each card's margin ladder (#1306, pick 6): one bar per ring, family, step or
/// month, full when the server says done or reached, else the share drawn.
@Suite("Achievements margin ladders")
struct MarginLadderTests {
    static func design() throws -> AchievementsPage { try AchievementsFixtures.payload("design-fixture").model }

    @Test("Explorer: a bar per ring, full when the server lists the ring done")
    func explorer() throws {
        let page = try Self.design()
        let bars = MarginLadders.explorer(try #require(page.explorer), emotions: page.emotions, domains: page.domains)
        #expect(bars == [1, 1, 1, 14.0 / 18, 23.0 / 38])
        let fresh = try AchievementsFixtures.payload("new-user").model
        #expect(MarginLadders.explorer(try #require(fresh.explorer), emotions: fresh.emotions, domains: fresh.domains)
            == [0.2, 0, 0, 0, 0])
    }

    @Test("Emotions: a bar per family, its seats met over its seats; Domains: one long bar")
    func emotionsAndDomains() throws {
        let page = try Self.design()
        #expect(MarginLadders.emotions(try #require(page.emotions)) == [5.0 / 6, 3.0 / 5, 3.0 / 6, 3.0 / 6, 1.0 / 4, 4.0 / 6, 4.0 / 5])
        #expect(MarginLadders.domains(try #require(page.domains)) == 14.0 / 18)
    }

    @Test("a counting stack: reached steps full, the next at the server's progress, the rest empty")
    func ladder() throws {
        let page = try Self.design()
        #expect(MarginLadders.ladder(try #require(page.pebbles)) == [1, 1, 1, 1, 1, 0.247, 0, 0])
        #expect(MarginLadders.ladder(try #require(page.glyphsCarved)) == [1, 0.133, 0, 0, 0])
    }

    @Test("the year: a bar per month, its place on the spoke over two; a month to come is empty")
    func year() throws {
        let bars = MarginLadders.year(try #require(try Self.design().years.first))
        #expect(bars.count == 12)
        #expect(bars[0] == 0 && bars[7] == 1 && bars[11] == 0)
        #expect(abs(bars[2] - (1 + 0.929) / 2) < 1e-9 && abs(bars[10] - 0.143 / 2) < 1e-9)
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh MarginLadderTests`
Expected: the build fails with `error: cannot find 'MarginLadders' in scope`.

- [ ] **Step 3: Write the ladders and their view**

Create `Pebbles/Features/Achievements/Page/MarginLadder.swift`:

```swift
import SwiftUI

/// Each card's margin ladder (#1306, the board's pick-6 shell): one bar per
/// ring, family, ladder step or month, each a fill from 0 to 1. A bar is full
/// when the server says its ring is done or its step reached; otherwise it is
/// the share of seats the figure draws (a drawing's proportion, not a verdict).
enum MarginLadders {
    typealias Page = AchievementsPage

    /// Explorer: the five rings, centre out.
    static func explorer(_ explorer: Page.Explorer, emotions: Page.Emotions?, domains: Page.Domains?) -> [Double] {
        [ring(explorer.ringsDone.contains(.firstFive), explorer.firstFive.filter(\.done).count, explorer.firstFive.count),
         ring(explorer.ringsDone.contains(.sixFirsts), explorer.sixFirsts.filter(\.done).count, explorer.sixFirsts.count),
         ring(explorer.ringsDone.contains(.twentyFive), explorer.twentyFive?.count ?? 0,
              ExplorerWheelGeometry.twentyFiveSeats),
         ring(explorer.ringsDone.contains(.domains), domains?.count ?? 0, domains?.of ?? 0),
         ring(explorer.ringsDone.contains(.emotions), emotions?.count ?? 0, emotions?.of ?? 0)]
    }

    /// Emotions: one bar per family, in seat order.
    static func emotions(_ emotions: Page.Emotions) -> [Double] {
        FamilyRun.runs(emotions.seats.map(\.family)).map { run in
            share(emotions.seats[run.start..<(run.start + run.length)].filter(\.met).count, run.length)
        }
    }

    /// Domains: one long bar.
    static func domains(_ domains: Page.Domains) -> Double {
        share(domains.count, domains.of)
    }

    /// A counting stack: one bar per ladder step.
    static func ladder(_ ladder: Page.Ladder) -> [Double] {
        ladder.ladder.map { step in
            if ladder.isReached(step) { return 1 }
            guard let next = ladder.next, next.threshold == step else { return 0 }
            return next.progress
        }
    }

    /// The year: one bar per month, its level and progress over two.
    static func year(_ year: Page.Year) -> [Double] {
        (1...12).map { number in
            guard let month = year.month(number), month.state != .upcoming else { return 0 }
            return (Double(month.level) + month.progress) / 2
        }
    }

    private static func ring(_ done: Bool, _ drawn: Int, _ seats: Int) -> Double {
        done ? 1 : share(drawn, seats)
    }

    private static func share(_ drawn: Int, _ seats: Int) -> Double {
        seats > 0 ? min(Double(drawn) / Double(seats), 1) : 0
    }
}

/// The bars themselves: 4 × 11 pt (one long 120 pt bar for Domains), ink filling
/// from the top, a ghost outline in secondary ink until full.
struct MarginLadder: View {
    let fills: [Double]
    var barHeight: CGFloat = 11

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        VStack(spacing: 4) {
            ForEach(Array(fills.enumerated()), id: \.offset) { _, fill in
                bar(min(max(fill, 0), 1), inks)
            }
        }
        .accessibilityHidden(true)
    }

    private func bar(_ fill: Double, _ inks: AchievementInks) -> some View {
        let shape = RoundedRectangle(cornerRadius: 2, style: .continuous)
        return ZStack(alignment: .top) {
            shape.strokeBorder(inks.ink2, lineWidth: fill >= 1 ? 0 : 1)
            Rectangle().fill(inks.ink).frame(height: barHeight * CGFloat(fill))
        }
        .frame(width: 4, height: barHeight)
        .clipShape(shape)
    }
}
```

- [ ] **Step 4: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh MarginLadderTests`
Expected: `** TEST SUCCEEDED **`, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements/Page/MarginLadder.swift apps/ios/PebblesTests/Achievements/MarginLadderTests.swift
git commit -m "feat(ios): add the achievements cards' margin ladders"
```

---

### Task 19: The card shell, the band and the ready mark

**Files:**
- Create: `Pebbles/Features/Achievements/Page/AchievementCardShell.swift`

The shell is a view with no logic of its own; Task 22's references draw it.

- [ ] **Step 1: Write the shell**

Create `Pebbles/Features/Achievements/Page/AchievementCardShell.swift`:

```swift
import SwiftUI

/// A card whose set is whole and offered (D9): its ceremony can be pulled.
struct ReadyMark {
    let stack: AchievementsPage.CeremonyStack
    let isPulling: Bool
    let pull: () -> Void
}

/// The achievements card (#1306, the board's pick-6 `gal`): the Spine's card
/// ground, a 34 pt margin with the card's title set bottom to top over its
/// ladder, then a band (small-caps title, italic hint, a rule) over the figure.
/// With an action the card is one button whose label is assembled from the
/// payload; a ready mark is a separate 44 pt button in the band, so VoiceOver
/// reaches both.
struct AchievementCardShell<Figure: View>: View {
    /// The margin's title, and the band's unless `band` names it.
    let title: String
    var band: String?
    var hint: String?
    var ladder: [Double] = []
    /// One long bar instead of the ladder (Domains).
    var longLadder: Double?
    let accessibilityLabel: String
    var ready: ReadyMark?
    var action: (() -> Void)?
    @ViewBuilder let figure: () -> Figure

    var body: some View {
        SpineCardShell(emotion: nil, polarity: .neutral, intensity: 1) {
            ZStack(alignment: .topTrailing) {
                main
                if let ready { ReadyButton(mark: ready).padding(.trailing, 4) }
            }
        }
        .padding(.horizontal, 14)
    }

    @ViewBuilder
    private var main: some View {
        if let action {
            Button(action: action) { content }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: accessibilityLabel))
        } else {
            content
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: accessibilityLabel))
        }
    }

    private var content: some View {
        HStack(alignment: .top, spacing: 0) {
            VStack(spacing: 14) {
                Upright {
                    Text(verbatim: title)
                        .spineType(.achievementsMarginTitle)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .lineLimit(1)
                        .fixedSize()
                        .rotationEffect(.degrees(-90))
                }
                Spacer(minLength: 0)
                if let longLadder {
                    MarginLadder(fills: [longLadder], barHeight: 120)
                } else {
                    MarginLadder(fills: ladder)
                }
            }
            .padding(.top, 16)
            .padding(.bottom, 14)
            .frame(width: 34)
            .frame(maxHeight: .infinity)
            .overlay(alignment: .trailing) {
                Rectangle().fill(Color.pebbles.cardRule).frame(width: 1)
            }
            VStack(alignment: .leading, spacing: 0) {
                AchievementBand(label: band ?? title, hint: ready == nil ? hint : nil)
                figure()
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 16)
        }
        .fixedSize(horizontal: false, vertical: true)
        .frame(minHeight: SpineLayout.minTarget)
        .contentShape(Rectangle())
    }
}

/// A band (the board's `.band`): the small-caps title, the italic hint at the far end, a rule under both.
struct AchievementBand: View {
    let label: String
    var hint: String?

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(verbatim: label)
                .spineType(.achievementsBand)
                .foregroundStyle(Color.pebbles.onSurface)
                .lineLimit(1)
                .layoutPriority(1)
            Spacer(minLength: 0)
            if let hint {
                Text(verbatim: hint)
                    .spineType(.achievementsBandHint)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
        .padding(.top, 14)
        .padding(.bottom, 9)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Color.pebbles.cardRule).frame(height: 1)
        }
    }
}

/// The ready mark (D9): a quiet ring with its dot and "ready". It pulls the
/// ceremony; while the pull runs it cannot be tapped again.
struct ReadyButton: View {
    let mark: ReadyMark

    var body: some View {
        let locale = SpineStrings.appLocale
        Button(action: mark.pull) {
            HStack(spacing: 6) {
                ZStack {
                    Circle().strokeBorder(Color.pebbles.onSurface, lineWidth: 1).frame(width: 12, height: 12)
                    Circle().fill(Color.pebbles.onSurface).frame(width: 4, height: 4)
                }
                Text(verbatim: AchievementsCopy.ready(locale: locale))
                    .spineType(.achievementsBandHint)
                    .foregroundStyle(Color.pebbles.onSurface)
            }
            .padding(.horizontal, 10)
            .frame(minWidth: SpineLayout.minTarget, minHeight: SpineLayout.minTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(mark.isPulling)
        .opacity(mark.isPulling ? 0.5 : 1)
        .accessibilityLabel(Text(verbatim: AchievementsA11y.ready(mark.stack, locale: locale)))
    }
}

/// Lays its one subview out a quarter turn round: as wide as it is tall and as
/// tall as it is wide, centred, so a `rotationEffect` sits inside its bounds.
struct Upright: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let first = subviews.first else { return .zero }
        let size = first.sizeThatFits(.unspecified)
        return CGSize(width: size.height, height: size.width)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard let first = subviews.first else { return }
        first.place(at: CGPoint(x: bounds.midX, y: bounds.midY), anchor: .center,
                    proposal: ProposedViewSize(first.sizeThatFits(.unspecified)))
    }
}
```

- [ ] **Step 2: Build and commit**

Run: `apps/ios/build/ach-test.sh MarginLadderTests`
Expected: `** TEST SUCCEEDED **` (the shell compiles).

```bash
git add apps/ios/Pebbles/Features/Achievements/Page/AchievementCardShell.swift
git commit -m "feat(ios): add the achievements card shell and its ready mark"
```

---

### Task 20: The header and the five cards

**Files:**
- Create: `Pebbles/Features/Achievements/Page/AchievementsHeader.swift`
- Create: `Pebbles/Features/Achievements/Page/AchievementsCards.swift`
- Modify: `Pebbles/Features/Achievements/Figures/AchievementsType.swift` (`achievementsSince` lowercases)

- [ ] **Step 1: Set the header's date line lowercase, as the board does**

In `AchievementsType.swift`, replace `achievementsSince` with:

```swift
    static let achievementsSince = Self(size: 11.5, weight: 600, trackingEm: 0.2, features: [.smallCaps],
                                        scaling: .text(.caption2), isLowercase: true)
```

- [ ] **Step 2: Write the header**

Create `Pebbles/Features/Achievements/Page/AchievementsHeader.swift`:

```swift
import SwiftUI

/// The page's header (#1306, spec D1–D2): since the account began, and the days
/// kept, which is calendar age (the server's `days_kept`), never a streak. It
/// hides when the server sent no count.
struct AchievementsHeader: View {
    let since: CalendarDay?
    let daysKept: Int?

    var body: some View {
        if let daysKept {
            let locale = SpineStrings.appLocale
            let calendar = SpineCalendar.current
            VStack(alignment: .leading, spacing: 4) {
                if let since {
                    Text(verbatim: AchievementsCopy.since(since, calendar: calendar, locale: locale,
                                                          dateLocale: SpineStrings.dateLocale))
                        .spineType(.achievementsSince)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
                HStack(alignment: .lastTextBaseline, spacing: 14) {
                    Text(verbatim: AchievementsCopy.number(daysKept, locale: locale))
                        .spineType(.achievementsDays)
                        .foregroundStyle(Color.pebbles.onSurface)
                    Text(verbatim: AchievementsCopy.daysUnit(daysKept, locale: locale))
                        .spineType(.achievementsDaysUnit)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 22)
            .padding(.top, 6)
            .padding(.bottom, 22)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: AchievementsA11y.header(
                daysKept: daysKept, since: since, calendar: calendar, locale: locale,
                dateLocale: SpineStrings.dateLocale)))
            .accessibilityAddTraits(.isHeader)
        }
    }
}
```

- [ ] **Step 3: Write the cards**

Create `Pebbles/Features/Achievements/Page/AchievementsCards.swift`:

```swift
import SwiftUI

/// The page's five cards (#1306, spec D1), each a shell over its figure. No
/// legends and no "new since" lines (the maintainer's edit of 28 October): new
/// items are marked on the figures only. A card's label, ladder and hint come
/// from the payload through `AchievementsA11y`, `MarginLadders` and `AchievementsCopy`.
struct ExplorerCard: View {
    let explorer: AchievementsPage.Explorer
    let emotions: AchievementsPage.Emotions?
    let domains: AchievementsPage.Domains?
    let lookups: WrapLookups
    var ready: ReadyMark?
    var open: (() -> Void)?

    var body: some View {
        let locale = SpineStrings.appLocale
        AchievementCardShell(
            title: AchievementsCopy.title(.explorer, locale: locale),
            hint: AchievementsCopy.explorerHint(locale: locale),
            ladder: MarginLadders.explorer(explorer, emotions: emotions, domains: domains),
            accessibilityLabel: AchievementsA11y.explorer(explorer, emotions: emotions, domains: domains, locale: locale),
            ready: ready, action: open
        ) {
            ExplorerWheel(explorer: explorer, emotions: emotions, domains: domains, lookups: lookups)
                .padding(.top, 12)
        }
    }
}

struct EmotionsCard: View {
    let emotions: AchievementsPage.Emotions
    /// The first pebble's day, the hint's "since".
    let since: Date?
    let lookups: WrapLookups
    var ready: ReadyMark?
    var open: (() -> Void)?

    var body: some View {
        let locale = SpineStrings.appLocale
        AchievementCardShell(
            title: AchievementsCopy.title(.emotions, locale: locale),
            hint: since.map { AchievementsCopy.sinceHint($0, calendar: SpineCalendar.current, locale: locale,
                                                          dateLocale: SpineStrings.dateLocale) },
            ladder: MarginLadders.emotions(emotions),
            accessibilityLabel: AchievementsA11y.emotions(emotions, locale: locale),
            ready: ready, action: open
        ) {
            EmotionRosace(emotions: emotions, lookups: lookups)
                .padding(.top, 12)
        }
    }
}

struct DomainsCard: View {
    let domains: AchievementsPage.Domains
    var ready: ReadyMark?
    var open: (() -> Void)?

    var body: some View {
        let locale = SpineStrings.appLocale
        AchievementCardShell(
            title: AchievementsCopy.title(.domains, locale: locale),
            hint: AchievementsCopy.domainsHint(locale: locale),
            longLadder: MarginLadders.domains(domains),
            accessibilityLabel: AchievementsA11y.domains(domains, locale: locale),
            ready: ready, action: open
        ) {
            VStack(spacing: 10) {
                DomainBand(domains: domains)
                    .padding(.top, 16)
                if let meaning = AchievementsCopy.coverage(domains.coverage, locale: locale) {
                    Text(verbatim: meaning)
                        .spineType(.achievementsLine)
                        .foregroundStyle(Color.pebbles.onSurface)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}

struct PebblesCard: View {
    let pebbles: AchievementsPage.Ladder
    let carved: AchievementsPage.Ladder?
    let sales: AchievementsPage.Ladder?
    let since: Date?
    var open: (() -> Void)?

    var body: some View {
        let locale = SpineStrings.appLocale
        AchievementCardShell(
            title: AchievementsCopy.title(.pebbles, locale: locale),
            hint: since.map { AchievementsCopy.sinceHint($0, calendar: SpineCalendar.current, locale: locale,
                                                          dateLocale: SpineStrings.dateLocale) },
            ladder: MarginLadders.ladder(pebbles),
            accessibilityLabel: AchievementsA11y.pebbles(pebbles, carved: carved, sales: sales, locale: locale),
            action: open
        ) {
            VStack(spacing: 0) {
                PebbleSkyline(ladder: pebbles)
                    .padding(.top, 22)
                Text(verbatim: AchievementsCopy.pebblesLine(pebbles, locale: locale))
                    .spineType(.achievementsLine)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 10)
                if carved != nil || sales != nil {
                    VStack(spacing: 0) {
                        if let carved { subRow(AchievementsCopy.glyphsCarved(locale: locale), carved, locale) }
                        if let sales { subRow(AchievementsCopy.glyphSales(locale: locale), sales, locale) }
                    }
                    .overlay(alignment: .top) { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
                    .padding(.top, 14)
                }
            }
        }
    }

    /// A glyph stack's line (the board's `.srow`): its label, its small skyline, its count.
    private func subRow(_ label: String, _ ladder: AchievementsPage.Ladder, _ locale: Locale) -> some View {
        HStack(spacing: 8) {
            Text(verbatim: label)
                .spineType(.achievementsSubLabel)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .lineLimit(1)
                .frame(width: 104, alignment: .leading)
            PebbleSkyline(ladder: ladder, box: SkylineGeometry.narrow, end: 126, width: 103)
                .frame(maxWidth: .infinity)
            Text(verbatim: AchievementsCopy.number(ladder.count, locale: locale))
                .spineType(.achievementsSubCount)
                .foregroundStyle(Color.pebbles.onSurface)
                .frame(minWidth: 34, alignment: .trailing)
        }
        .frame(minHeight: SpineLayout.minTarget)
        .overlay(alignment: .bottom) { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
    }
}

struct YearCard: View {
    let year: AchievementsPage.Year
    let lookups: WrapLookups
    var open: (() -> Void)?

    var body: some View {
        let locale = SpineStrings.appLocale
        AchievementCardShell(
            title: AchievementsCopy.title(.year, locale: locale),
            band: String(year.year),
            hint: AchievementsCopy.yearHint(year, locale: locale),
            ladder: MarginLadders.year(year),
            accessibilityLabel: AchievementsA11y.year(year, locale: locale),
            action: open
        ) {
            VStack(spacing: 10) {
                YearDisc(year: year, lookups: lookups)
                    .padding(.top, 12)
                if let step = year.nextStep {
                    Text(verbatim: AchievementsCopy.nextStep(step, locale: locale))
                        .spineType(.achievementsLine)
                        .foregroundStyle(Color.pebbles.onSurface)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}
```

The year card's margin reads "The year" and its band the year itself (`2026`), as on the board.

- [ ] **Step 4: Build and commit**

Run: `apps/ios/build/ach-test.sh AchievementsA11yTests`
Expected: `** TEST SUCCEEDED **` (the cards compile).

```bash
git add apps/ios/Pebbles/Features/Achievements
git commit -m "feat(ios): add the achievements header and its five cards"
```

---

### Task 21: The page replaces the grid

**Files:**
- Create: `Pebbles/Features/Achievements/Page/AchievementsPageView.swift`
- Modify: `Pebbles/Features/Profile/AchievementsView.swift` (whole file)

- [ ] **Step 1: Write the page's content**

Create `Pebbles/Features/Achievements/Page/AchievementsPageView.swift`:

```swift
import SwiftUI

/// What a card opens (part 4 presents it). The year sheet is one per year.
enum AchievementSheet: Hashable, Identifiable {
    case explorer, emotions, domains, pebbles
    case year(Int)

    var id: String {
        switch self {
        case .explorer: "explorer"
        case .emotions: "emotions"
        case .domains: "domains"
        case .pebbles: "pebbles"
        case .year(let year): "year-\(year)"
        }
    }

    /// The card whose "seen" opening it marks (D10).
    var card: AchievementCard {
        switch self {
        case .explorer: .explorer
        case .emotions: .emotions
        case .domains: .domains
        case .pebbles: .pebbles
        case .year: .year
        }
    }
}

/// The page (#1306, spec D1): the header, then a card per section the payload
/// carries, in the board's order; a section the payload omits hides its card
/// (spec §4). A completion the server marks ready puts its mark on its card.
/// Without its scroll, so the references draw it whole.
struct AchievementsPageContent: View {
    let page: AchievementsPage
    let lookups: WrapLookups
    var pulling: AchievementsPage.CeremonyStack?
    var onPull: (AchievementsPage.CeremonyStack) -> Void = { _ in }
    /// Nil leaves the cards still (part 3); part 4 opens their sheets.
    var onOpen: ((AchievementSheet) -> Void)?

    var body: some View {
        let firstPebbleDay = page.explorer?.firstPebble?.happenedAt
        VStack(spacing: 14) {
            AchievementsHeader(since: page.since, daysKept: page.daysKept)
            if let explorer = page.explorer {
                ExplorerCard(explorer: explorer, emotions: page.emotions, domains: page.domains, lookups: lookups,
                             ready: ready(explorer.completion), open: open(.explorer))
            }
            if let emotions = page.emotions {
                EmotionsCard(emotions: emotions, since: firstPebbleDay, lookups: lookups,
                             ready: ready(emotions.completion), open: open(.emotions))
            }
            if let domains = page.domains {
                DomainsCard(domains: domains, ready: ready(domains.completion), open: open(.domains))
            }
            if let pebbles = page.pebbles {
                PebblesCard(pebbles: pebbles, carved: page.glyphsCarved, sales: page.glyphSales, since: firstPebbleDay,
                            open: open(.pebbles))
            }
            ForEach(page.years) { year in
                YearCard(year: year, lookups: lookups, open: open(.year(year.year)))
            }
        }
        .padding(.bottom, Spacing.xxl)
    }

    private func ready(_ completion: AchievementsPage.Completion?) -> ReadyMark? {
        guard let completion, completion.isReady else { return nil }
        return ReadyMark(stack: completion.stack, isPulling: pulling == completion.stack,
                         pull: { onPull(completion.stack) })
    }

    private func open(_ sheet: AchievementSheet) -> (() -> Void)? {
        guard let onOpen else { return nil }
        return { onOpen(sheet) }
    }
}
```

- [ ] **Step 2: Replace the grid with the page**

Replace the whole of `Pebbles/Features/Profile/AchievementsView.swift` with:

```swift
import SwiftUI

/// You → Achievements (#1306, spec D1): the days kept and five cards, drawn
/// from `get_achievements`. Opening the screen fires `check_achievements()`
/// first: that call is the retroactive grant, so a veteran's history is there
/// on the first visit, and it never celebrates. The device's last page shows at
/// once while the read runs (stale-while-revalidate); a failed read keeps it and
/// logs, and only a first visit with nothing kept shows the error.
struct AchievementsView: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(AchievementsService.self) private var achievements
    @Environment(AchievementNotificationService.self) private var notify
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var refs

    @State private var model = AchievementsModel()

    var body: some View {
        content
            .pebblesToolbarTitle("Achievements")
            .task { await load() }
            .pebblesScreen()
    }

    @ViewBuilder
    private var content: some View {
        let locale = SpineStrings.appLocale
        if let page = model.page {
            ScrollView {
                AchievementsPageContent(page: page, lookups: HomeLookups.make(palettes: palettes, refs: refs),
                                        pulling: model.pulling, onPull: pull)
                    .padding(.top, Spacing.sm)
            }
        } else if model.failed {
            ContentUnavailableView {
                Label {
                    Text(verbatim: AchievementsCopy.errorTitle(locale: locale))
                } icon: {
                    Image(systemName: "exclamationmark.triangle")
                }
            } description: {
                Text(verbatim: AchievementsCopy.errorBody(locale: locale))
            } actions: {
                Button {
                    Task { await load() }
                } label: {
                    Text(verbatim: AchievementsCopy.retry(locale: locale))
                }
            }
        } else {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func load() async {
        if model.service == nil {
            model.service = AchievementsPageService(client: supabase.client, disk: .standard,
                                                    owner: supabase.session?.user.id.uuidString.lowercased())
        }
        await model.load { await achievements.checkIgnoringFailure() }
    }

    /// A ready mark pulls its ceremony; only a pull that came back with a row
    /// opens the moment, with the karma it paid.
    private func pull(_ stack: AchievementsPage.CeremonyStack) {
        Task {
            if case .pulled(let karma) = await model.pull(stack) {
                notify.present(cards: [AchievementsCopy.ceremonyCard(stack, karma: karma, locale: SpineStrings.appLocale)])
            }
        }
    }
}
```

`AchievementBadgeCell` and the grid's preview go with the old body; nothing else used them. `AchievementRecord.familySymbol` and the localized titles stay: the Profile shelf reads them.

- [ ] **Step 3: Build and run the suites that touch the Profile**

Run: `apps/ios/build/ach-test.sh "" References`
Expected: `** TEST SUCCEEDED **` (or exactly main's failures from Task 1 step 6).

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements/Page/AchievementsPageView.swift apps/ios/Pebbles/Features/Profile/AchievementsView.swift
git commit -m "feat(ios): replace the achievements grid with the page served by get_achievements"
```

---

### Task 22: The page's references; part 3 gate and PR

**Files:**
- Create: `PebblesTests/References/AchievementsPageReferences.swift`

- [ ] **Step 1: Write the references**

Create `PebblesTests/References/AchievementsPageReferences.swift`:

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1306: the achievements page on the design fixture (the Explorer card
    /// carries a ready mark) and on a new user's first day, Paper and Night,
    /// English and French, and at accessibility text size.
    @Suite("Achievements page references")
    @MainActor
    struct AchievementsPageReferences {
        static func page(_ fixture: String) throws -> some View {
            let page = try AchievementsFixtures.payload(fixture).model
            return AchievementsPageContent(page: page, lookups: AchievementsLookups.fixture(page))
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func page(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try Self.page("design-fixture"), named: "page",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test
        func pageAx2() throws {
            ReferenceImage.assertMatches(try Self.page("design-fixture"), named: "page",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func newUser(scheme: ReferenceScheme) throws {
            ReferenceImage.assertMatches(try Self.page("new-user"), named: "new-user",
                                         config: ReferenceConfig(scheme: scheme))
        }
    }
}
```

- [ ] **Step 2: Run the references and look at the renders**

Run: `apps/ios/build/ach-test.sh References`
Expected: the baseline's failures plus `no reference at` lines for `page-paper-en`, `page-night-en`, `page-paper-en-ax2`, `new-user-paper-en`, `new-user-night-en` (the French pass runs on CI with `-testLanguage fr`). Nothing else.

Open the actual PNGs and check against the board (`final/index.html`):
- the header: the since line in lowercase small caps (its date in the device's date format), 249, "days";
- five cards in order, each with its margin title and ladder;
- the Explorer band shows "ready" in place of "five rings";
- the Domains sentence sits under the band;
- the Pebbles card has its two glyph lines;
- the year card ends with "Twelve more days with a moment…";
- at ax2 nothing is clipped, and every row stays at least 44 pt;
- the new user's page shows ghosts everywhere but the first moment's mark.

- [ ] **Step 3: Check it on the simulator against the real back-end**

This needs #1305 parts 1–3 on the linked project and a test account (never the maintainer's real account without asking).

```bash
cd apps/ios && . build/achievements.env
xcrun simctl boot "$ACH_SIM" 2>/dev/null; open -a Simulator
xcodebuild build -scheme Pebbles -destination "id=$ACH_SIM" -derivedDataPath "$ACH_DD" | tail -3
xcrun simctl install "$ACH_SIM" "$ACH_DD/Build/Products/Debug-iphonesimulator/Pebbles.app"
xcrun simctl launch "$ACH_SIM" app.pbbls.ios
```

Sign in, open You → Achievements. Expected:
- the page draws;
- leaving and coming back shows it at once with no spinner (the kept copy);
- with the Mac's network off, going back to You and into Achievements again keeps the page on screen, and Console shows `get_achievements failed`;
- recording a heavy first pebble on a fresh account shows no unlock moment.

- [ ] **Step 4: Run every unit test but the references, and the lint**

Run: `apps/ios/build/ach-test.sh "" References`, then `npm run lint --workspace=@pbbls/ios`
Expected: `** TEST SUCCEEDED **`; no `error:`.

- [ ] **Step 5: Open the PR**

```bash
gh stack submit --auto
```

Title: `feat(ios): show the achievements page from get_achievements`. Body opens with `Part of #1306`; same labels and milestone, plus `rebaseline-ios-screenshots` (added alone). This part changes a user-visible view (`V-achievements`): follow the root CLAUDE.md "Lab Note requirement", and ask the maintainer whether the hosted map's coverage of `V-achievements` replaces the note (their standing rule is `no-lab-note` when it does); if a note is wanted, author it with the repo's `lab-note` skill.

---

# Part 4 — the sheets (branch `feat/1306-ios-achievements-sheets`, stacked on part 3)

- [ ] **Start the branch:** `gh stack add feat/1306-ios-achievements-sheets`

Every sheet follows the board's pick-3 register (NOTES "The sheets"): a band, the figure large, dated sections (a small-caps title with an italic note), rows of at least 44 pt that open their pebble, then what is "not yet" as dotted names. Colour only in emotion hues. The rows are built by pure functions (Task 24) so the views hold no logic.

### Task 23: The sheets' words

**Files:**
- Create: `Pebbles/Features/Achievements/Copy/AchievementsCopy+Sheets.swift`
- Modify: `Pebbles/Resources/Localizable.xcstrings` (through the helper)
- Create: `PebblesTests/Achievements/AchievementsSheetCopyTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `PebblesTests/Achievements/AchievementsSheetCopyTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// The sheets' words (#1306), English and French.
@Suite("Achievements sheet copy")
struct AchievementsSheetCopyTests {
    static let en = Locale(identifier: "en")
    static let fr = Locale(identifier: "fr")
    static var paris: Calendar {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris")!
        return calendar
    }

    @Test("titles, kinds and counts")
    func labels() {
        #expect(AchievementsCopy.sheet("sixFirsts", locale: Self.fr) == "Les six premières fois")
        #expect(AchievementsCopy.taskTitle(.collection, locale: Self.fr) == "Commence ta première collection")
        #expect(AchievementsCopy.taskTitle(.moment, locale: Self.en) == "Record your first moment")
        #expect(AchievementsCopy.kind(.lowlight, locale: Self.fr) == "Creux")
        #expect(AchievementsCopy.kind(.large, locale: Self.en) == "Large")
        #expect(AchievementsCopy.of(14, 18, locale: Self.fr) == "14 sur 18")
        #expect(AchievementsCopy.registerNumber(3, locale: Self.en) == "No. 03")
        #expect(AchievementsCopy.registerNumber(14, locale: Self.fr) == "N° 14")
        #expect(AchievementsCopy.toGo(113, locale: Self.en) == "113 to go")
        #expect(AchievementsCopy.toGo(113, locale: Self.fr) == "encore 113")
        #expect(AchievementsCopy.thenEvery(500, locale: Self.en) == "then every 500")
        #expect(AchievementsCopy.firstYear(2026, locale: Self.fr) == "Aucune pour l'instant. 2026 est ta première année ici.")
        #expect(AchievementsCopy.newMark(locale: Self.en) == "new since you last looked")
    }

    @Test("a register date is the short day and month in the reader's zone")
    func dates() throws {
        #expect(AchievementsCopy.day(CalendarDay(year: 2026, month: 5, day: 30), calendar: Self.paris, dateLocale: Self.en)
            == "May 30")
        let late = try #require(ISO8601Flexible.parse("2026-03-01T23:30:00Z"))
        #expect(AchievementsCopy.day(late, calendar: Self.paris, dateLocale: Self.fr) == "2 mars")
    }

    @Test("a month's row reads its three counters")
    func monthRow() throws {
        let year = try #require(try AchievementsFixtures.payload("design-fixture").model.years.first)
        #expect(AchievementsCopy.monthRow(try #require(year.month(11)), locale: Self.en)
            == "November: 2 of 14 days with moments, 1 of 14 with pebbles, no full week yet.")
        #expect(AchievementsCopy.monthRow(try #require(year.month(8)), locale: Self.fr)
            == "Août : 14 jours sur 14 avec un moment, 14 sur 14 avec un galet, 4 semaines complètes sur 4 remplies.")
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh AchievementsSheetCopyTests`
Expected: the build fails with `error: type 'AchievementsCopy' has no member 'sheet'`.

- [ ] **Step 3: Add the strings**

Create `apps/ios/build/strings-task23.json`:

```json
{
  "achievements.sheet.firstFive": {"en": "The first five", "fr": "Les cinq premiers"},
  "achievements.sheet.sixFirsts": {"en": "The six firsts", "fr": "Les six premières fois"},
  "achievements.sheet.twentyFive": {"en": "25 pebbles", "fr": "25 galets"},
  "achievements.sheet.outer": {"en": "Domains and emotions", "fr": "Domaines et émotions"},
  "achievements.sheet.of": {"en": "%1$@ of %2$@", "fr": "%1$@ sur %2$@"},
  "achievements.sheet.closed": {"en": "closed", "fr": "bouclé"},
  "achievements.sheet.the25th": {"en": "The 25th", "fr": "Le 25e"},
  "achievements.kind.small": {"en": "Small", "fr": "Petit"},
  "achievements.kind.medium": {"en": "Medium", "fr": "Moyen"},
  "achievements.kind.large": {"en": "Large", "fr": "Grand"},
  "achievements.kind.lowlight": {"en": "Lowlight", "fr": "Creux"},
  "achievements.kind.neutral": {"en": "Neutral", "fr": "Neutre"},
  "achievements.kind.highlight": {"en": "Highlight", "fr": "Temps fort"},
  "achievements.sheet.entered": {"en": "Entered", "fr": "Entrés"},
  "achievements.sheet.newestFirst": {"en": "newest first", "fr": "du plus récent au plus ancien"},
  "achievements.sheet.number": {"en": "No. %@", "fr": "N° %@"},
  "achievements.sheet.notYet": {"en": "Not yet in these pages", "fr": "Pas encore dans ces pages"},
  "achievements.sheet.milestones": {"en": "Milestones", "fr": "Paliers"},
  "achievements.sheet.toGo": {"en": "%@ to go", "fr": "encore %@"},
  "achievements.sheet.thenEvery": {"en": "then every %@", "fr": "puis tous les %@"},
  "achievements.sheet.months": {"en": "The months", "fr": "Les mois"},
  "achievements.sheet.column.moments": {"en": "moments", "fr": "moments"},
  "achievements.sheet.column.pebbles": {"en": "pebbles", "fr": "galets"},
  "achievements.sheet.column.weeks": {"en": "weeks", "fr": "semaines"},
  "achievements.sheet.earlier": {"en": "Earlier years", "fr": "Années précédentes"},
  "achievements.sheet.firstYear": {"en": "None yet. %@ is your first year here.", "fr": "Aucune pour l'instant. %@ est ta première année ici."},
  "achievements.sheet.new": {"en": "new since you last looked", "fr": "nouveau depuis ta dernière visite"},
  "achievements.a11y.monthRow": {"en": "%1$@: %2$@ of %3$@ days with moments, %4$@ of %3$@ with pebbles, %5$@.", "fr": "%1$@ : %2$@ jours sur %3$@ avec un moment, %4$@ sur %3$@ avec un galet, %5$@."},
  "achievements.a11y.weeksFilled": {"en": "%1$@ of %2$@ full weeks filled", "fr": "%1$@ semaines complètes sur %2$@ remplies"},
  "achievements.a11y.noFullWeek": {"en": "no full week yet", "fr": "pas encore de semaine complète"}
}
```

Run: `python3 -I apps/ios/build/xcstrings-add.py apps/ios/Pebbles/Resources/Localizable.xcstrings apps/ios/build/strings-task23.json`
Expected: `added 30 entries`.

- [ ] **Step 4: Write the sheets' copy**

Create `Pebbles/Features/Achievements/Copy/AchievementsCopy+Sheets.swift`:

```swift
import Foundation

/// The sheets' words (#1306, NOTES "The sheets"): section titles and notes, row
/// names and dates, over `achievements.sheet.*`, `achievements.kind.*` and the
/// Home checklist's task titles (the first five read as Home names them).
extension AchievementsCopy {
    /// D12's level rule: 14 days. The payload does not carry it (see the plan's
    /// contract gaps); this is the one place the client names it, for the year
    /// table's "n/14" and its gauges, and it moves to the payload when #1305 serves it.
    static let daysPerLevel = 14

    static func sheet(_ key: String, locale: Locale) -> String {
        SpineStrings.string("achievements.sheet.\(key)", locale: locale)
    }

    /// A first-five task's row: the Home checklist's title.
    static func taskTitle(_ task: HomeChecklist.Task, locale: Locale) -> String {
        let key: String
        switch task {
        case .moment: key = "moment"
        case .pebble: key = "pebble"
        case .collection: key = "collection"
        case .soul: key = "soul"
        case .glyph: key = "glyph"
        }
        return SpineStrings.string("home.task.\(key).title", locale: locale)
    }

    /// "Small" … "Highlight": the app's words for the six firsts.
    static func kind(_ kind: Page.StoneKind, locale: Locale) -> String {
        SpineStrings.string("achievements.kind.\(kind.rawValue)", locale: locale)
    }

    /// "14 of 18".
    static func of(_ count: Int, _ total: Int, locale: Locale) -> String {
        format("achievements.sheet.of", locale, number(count, locale: locale), number(total, locale: locale))
    }

    /// "No. 03": a domain's place in the order they came.
    static func registerNumber(_ place: Int, locale: Locale) -> String {
        format("achievements.sheet.number", locale, String(format: "%02ld", place))
    }

    /// "113 to go": the next milestone, never a date.
    static func toGo(_ left: Int, locale: Locale) -> String {
        format("achievements.sheet.toGo", locale, number(left, locale: locale))
    }

    /// "then every 500": the pebble ladder's tail.
    static func thenEvery(_ step: Int, locale: Locale) -> String {
        format("achievements.sheet.thenEvery", locale, number(step, locale: locale))
    }

    /// "None yet. 2026 is your first year here."
    static func firstYear(_ year: Int, locale: Locale) -> String {
        format("achievements.sheet.firstYear", locale, String(year))
    }

    static func newMark(locale: Locale) -> String {
        sheet("new", locale: locale)
    }

    /// "May 30", "30 mai": a served local day.
    static func day(_ day: CalendarDay, calendar: Calendar, dateLocale: Locale) -> String {
        SpineDates.shortDayMonth(day.date(in: calendar.timeZone) ?? .distantPast, calendar: calendar, locale: dateLocale)
    }

    /// A crossing pebble's day, in the reader's zone.
    static func day(_ date: Date, calendar: Calendar, dateLocale: Locale) -> String {
        SpineDates.shortDayMonth(date, calendar: calendar, locale: dateLocale)
    }

    /// A year-table row for VoiceOver: "November: 2 of 14 days with moments, …".
    static func monthRow(_ month: Page.Month, locale: Locale) -> String {
        let target = daysPerLevel
        let weeks = month.fullWeeks == 0
            ? SpineStrings.string("achievements.a11y.noFullWeek", locale: locale)
            : format("achievements.a11y.weeksFilled", locale, number(month.weeksFilled, locale: locale),
                     number(month.fullWeeks, locale: locale))
        return format("achievements.a11y.monthRow", locale, monthHeading(month.month, locale: locale),
                      number(min(month.momentDays, target), locale: locale), number(target, locale: locale),
                      number(min(month.pebbleDays, target), locale: locale), weeks)
    }
}
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh AchievementsSheetCopyTests`
Expected: `** TEST SUCCEEDED **`, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements/Copy apps/ios/Pebbles/Resources/Localizable.xcstrings \
  apps/ios/PebblesTests/Achievements/AchievementsSheetCopyTests.swift
git commit -m "feat(ios): word the achievements sheets in english and french"
```

---

### Task 24: The sheets' rows, built from the payload

**Files:**
- Create: `Pebbles/Features/Achievements/Sheets/AchievementsSheetRows.swift`
- Create: `PebblesTests/Achievements/AchievementsSheetRowsTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `PebblesTests/Achievements/AchievementsSheetRowsTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// The sheets' rows (#1306): each a served item in the server's order, named,
/// dated in the reader's zone, opening its pebble when it has one. Nothing is
/// sorted or counted toward a verdict here.
@Suite("Achievements sheet rows")
@MainActor
struct AchievementsSheetRowsTests {
    typealias Rows = AchievementsSheetRows

    static func context(_ page: AchievementsPage) -> Rows.Context {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris")!
        let en = Locale(identifier: "en")
        return Rows.Context(lookups: AchievementsLookups.fixture(page), calendar: calendar, locale: en, dateLocale: en)
    }

    static func design() throws -> AchievementsPage { try AchievementsFixtures.payload("design-fixture").model }

    @Test("Explorer: the first five, the six firsts, the 25th, then the rows that open Domains and Emotions")
    func explorer() throws {
        let page = try Self.design()
        let sections = Rows.explorer(try #require(page.explorer), emotions: page.emotions, domains: page.domains,
                                     Self.context(page))
        #expect(sections.map(\.id) == ["first-five", "six-firsts", "twenty-five", "outer"])
        let five = sections[0]
        #expect(five.note == "5 of 5")
        #expect(five.rows.map(\.name) == ["Record your first moment", "Complete your first pebble",
                                          "Start your first collection", "Add your first soul", "Carve your first glyph"])
        #expect(five.rows[1].sub == "Mara moves in" && five.rows[1].date == "Mar 2")
        #expect(five.rows[1].opens == .moment(try #require(UUID(uuidString: "a0000000-0000-4000-8000-000000000001"))))
        #expect(five.rows[2].opens == nil && five.rows[2].mark == .firstFive(.collection))
        let six = sections[1]
        #expect(six.rows.map(\.name) == ["Small", "Medium", "Large", "Lowlight", "Neutral", "Highlight"])
        #expect(six.rows[2].sub == "Argument about money" && six.rows[2].date == "Mar 9")
        #expect(sections[2].note == "closed" && sections[2].rows.first?.name == "The 25th")
        let outer = sections[3].rows
        #expect(outer.map(\.opens) == [.sheet(.domains), .sheet(.emotions)])
        #expect(outer.map(\.sub) == ["14 of 18", "23 of 38"])
        #expect(outer.map(\.mark) == [.domain, .rosace])
    }

    @Test("a first day: what is not done is a ghost row, undated, opening nothing")
    func newUser() throws {
        let page = try AchievementsFixtures.payload("new-user").model
        let sections = Rows.explorer(try #require(page.explorer), emotions: page.emotions, domains: page.domains,
                                     Self.context(page))
        #expect(sections[0].note == "1 of 5")
        #expect(sections[0].rows[0].mark == .firstFive(.moment) && sections[0].rows[0].sub == "First light")
        #expect(sections[0].rows.dropFirst().allSatisfy { $0.mark == .ghost && $0.date == nil && $0.opens == nil })
        #expect(sections[1].rows.allSatisfy { $0.mark == .ghost })
        #expect(sections[2].note == "0 of 25")
    }

    @Test("Emotions: a section per family in seat order, what was met as rows, the rest as names")
    func emotions() throws {
        let page = try Self.design()
        let sections = Rows.emotions(try #require(page.emotions), Self.context(page))
        #expect(sections.count == 7)
        let joy = sections[0]
        #expect(joy.family == "joy" && joy.note == "5 of 6")
        #expect(joy.rows.map(\.name) == ["Amused", "Cheerful", "Content", "Excited", "Joyful"])
        #expect(joy.notYet == ["Grateful"])
        #expect(joy.rows[4].sub == "Mara moves in" && joy.rows[4].mark == .pebble(.highlightMedium, family: "joy"))
        let sadness = sections[5]
        #expect(sadness.rows.first { $0.name == "Sad" }?.isNew == true)
        // The fixture's lookups know no shame family: its emotions are unknown here and skipped, never shown wrong.
        #expect(sections[4].rows.isEmpty && sections[4].note == "1 of 4")
    }

    @Test("Domains: the register newest first, numbered in the order they came; then what is not yet")
    func domains() throws {
        let page = try Self.design()
        let sections = Rows.domains(try #require(page.domains), Self.context(page))
        let entered = sections[0].rows
        #expect(entered.count == 14)
        #expect(entered.first?.number == "No. 14" && entered.first?.name == "Events" && entered.first?.isNew == true)
        #expect(entered.last?.number == "No. 01" && entered.last?.name == "Family")
        #expect(sections[1].notYet == ["Spirituality", "Community", "Creativity", "Rest"])
    }

    @Test("a counting stack: the levels reached, the next with what is left, the steps ahead, the tail")
    func ladder() throws {
        let page = try Self.design()
        let ctx = Self.context(page)
        let pebbles = Rows.ladder(try #require(page.pebbles), title: "Milestones", note: "5 of 8", withPebbles: true, ctx)
        #expect(pebbles.rows.map(\.name) == ["1", "10", "25", "50", "100", "250", "500", "1,000"])
        #expect(pebbles.rows.map(\.style) == [.number, .number, .number, .number, .number, .next, .ghostNumber, .ghostNumber])
        #expect(pebbles.rows[0].sub == "Mara moves in" && pebbles.rows[0].date == "Mar 2")
        #expect(pebbles.rows[5].next == "113 to go" && pebbles.rows[5].mark == .stage(index: 5, fill: 0.247))
        #expect(pebbles.trailing == "then every 500")
        let carved = Rows.ladder(try #require(page.glyphsCarved), title: "Glyphs carved", note: "12", withPebbles: false, ctx)
        #expect(carved.rows.map(\.name) == ["10", "25", "50", "75", "100"])
        #expect(carved.rows[0].sub == nil && carved.rows[0].date == "Jun 12" && carved.rows[0].opens == nil)
        #expect(carved.trailing == nil)
    }

    @Test("the year: the listed months newest first, three counters each; earlier years or the first-year line")
    func year() throws {
        let page = try Self.design()
        let year = try #require(page.years.first)
        let rows = Rows.months(year, Self.context(page))
        #expect(rows.map(\.month.month) == [11, 10, 9, 8, 7, 6, 5, 4, 3])
        let november = rows[0]
        #expect(november.name == "November")
        #expect(november.moments == YearCell(value: "2", target: nil, fill: 2.0 / 14, isDone: false, usesHue: false))
        #expect(november.pebbles.value == "1" && november.pebbles.usesHue)
        #expect(november.weeks == YearCell(value: "—", target: nil, fill: 0, isDone: false, usesHue: false))
        let august = rows[3]
        #expect(august.pebbles == YearCell(value: "14", target: nil, fill: 1, isDone: true, usesHue: true))
        #expect(august.weeks == YearCell(value: "4", target: "/4", fill: 1, isDone: true, usesHue: false))
        #expect(november.accessibilityLabel == "November: 2 of 14 days with moments, 1 of 14 with pebbles, no full week yet.")
        let earlier = Rows.earlierYears(year, all: page.years, Self.context(page))
        #expect(earlier.rows.isEmpty && earlier.quiet == "None yet. 2026 is your first year here.")
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `apps/ios/build/ach-test.sh AchievementsSheetRowsTests`
Expected: the build fails with `error: cannot find 'AchievementsSheetRows' in scope`.

- [ ] **Step 3: Write the row builders**

Create `Pebbles/Features/Achievements/Sheets/AchievementsSheetRows.swift`:

```swift
import Foundation

/// One register row (the board's `.r`): a mark, a name, a line under it, a
/// date, the new dot. A row that opens something is a button of at least 44 pt.
struct RegisterRowModel: Equatable, Identifiable {
    enum Mark: Equatable {
        case firstFive(HomeChecklist.Task)
        case sixFirst(AchievementsPage.StoneKind)
        case twentyFive
        /// A crossing pebble's seal, in its family's ink.
        case pebble(Valence, family: String?)
        case domain
        case stage(index: Int, fill: Double)
        case rosace
        case ghost
    }

    enum Opens: Equatable {
        case moment(UUID)
        case sheet(AchievementSheet)
    }

    enum Style: Equatable {
        /// Small-caps name.
        case name
        /// A milestone's number, reached.
        case number
        /// The next milestone's number, in secondary ink.
        case next
        /// A milestone still ahead: its number alone, light.
        case ghostNumber
    }

    let id: String
    let mark: Mark
    let name: String
    var sub: String?
    /// The italic line of the next milestone ("113 to go").
    var next: String?
    var date: String?
    var isNew = false
    /// A domain's place in the order they came ("No. 14").
    var number: String?
    var style: Style = .name
    var opens: Opens?
}

/// A dated section (the board's `.lh` and its rows).
struct RegisterSection: Equatable, Identifiable {
    let id: String
    let title: String
    /// The family whose hue the title takes (the Emotions sheet).
    var family: String?
    var note: String?
    var rows: [RegisterRowModel] = []
    /// What is not yet, as dotted names.
    var notYet: [String] = []
    /// A closing line ("then every 500").
    var trailing: String?
    /// A quiet line when the section has nothing ("None yet. …").
    var quiet: String?
}

/// One gauge of the year table (the board's `cell`): its number, an "/n" target
/// for the weeks, its fill, and whether it is set as done.
struct YearCell: Equatable {
    let value: String
    let target: String?
    let fill: Double
    let isDone: Bool
    /// The pebbles gauge wears the month's hue; moments and weeks stay ink.
    let usesHue: Bool
}

struct YearRowModel: Equatable, Identifiable {
    var id: Int { month.month }
    let month: AchievementsPage.Month
    let name: String
    let moments: YearCell
    let pebbles: YearCell
    let weeks: YearCell
    let accessibilityLabel: String
}

/// The five sheets' rows (#1306), built from the payload in its own order:
/// names from the reference data (an id this device does not know is skipped,
/// never shown wrong), dates in the reader's zone, a row opening its pebble
/// when the payload carries one. Android: `AchievementsSheetRows`.
enum AchievementsSheetRows {
    typealias Page = AchievementsPage
    typealias Copy = AchievementsCopy

    struct Context {
        let lookups: WrapLookups
        let calendar: Calendar
        let locale: Locale
        let dateLocale: Locale
    }

    // MARK: - Explorer

    static func explorer(_ explorer: Page.Explorer, emotions: Page.Emotions?, domains: Page.Domains?,
                         _ ctx: Context) -> [RegisterSection] {
        let five = explorer.firstFive.compactMap { item -> RegisterRowModel? in
            guard let task = item.task else { return nil }
            let opensMoment = item.subjectKind == "moment" || item.subjectKind == "pebble"
            return RegisterRowModel(
                id: "five-\(task.slug)", mark: item.done ? .firstFive(task) : .ghost,
                name: Copy.taskTitle(task, locale: ctx.locale),
                sub: item.done ? item.subjectName : nil,
                date: item.done ? item.on.map { Copy.day($0, calendar: ctx.calendar, dateLocale: ctx.dateLocale) } : nil,
                isNew: item.isNew,
                opens: item.done && opensMoment ? item.subjectId.map { .moment($0) } : nil)
        }
        let six = explorer.sixFirsts.compactMap { first -> RegisterRowModel? in
            guard let kind = first.kind else { return nil }
            return RegisterRowModel(
                id: "six-\(kind.rawValue)", mark: first.done ? .sixFirst(kind) : .ghost,
                name: Copy.kind(kind, locale: ctx.locale), sub: first.pebble?.name,
                date: date(first.pebble, ctx), isNew: first.isNew, opens: opens(first.pebble))
        }
        var sections = [
            RegisterSection(id: "first-five", title: Copy.sheet("firstFive", locale: ctx.locale),
                            note: Copy.of(explorer.firstFive.filter(\.done).count, explorer.firstFive.count,
                                          locale: ctx.locale),
                            rows: five),
            RegisterSection(id: "six-firsts", title: Copy.sheet("sixFirsts", locale: ctx.locale),
                            note: Copy.of(explorer.sixFirsts.filter(\.done).count, explorer.sixFirsts.count,
                                          locale: ctx.locale),
                            rows: six)
        ]
        if let twentyFive = explorer.twentyFive {
            let note = explorer.ringsDone.contains(.twentyFive)
                ? Copy.sheet("closed", locale: ctx.locale)
                : Copy.of(twentyFive.count, ExplorerWheelGeometry.twentyFiveSeats, locale: ctx.locale)
            sections.append(RegisterSection(
                id: "twenty-five", title: Copy.sheet("twentyFive", locale: ctx.locale), note: note,
                rows: [RegisterRowModel(id: "the-25th", mark: twentyFive.done ? .twentyFive : .ghost,
                                        name: Copy.sheet("the25th", locale: ctx.locale), sub: twentyFive.pebble?.name,
                                        date: date(twentyFive.pebble, ctx), isNew: twentyFive.isNew,
                                        opens: opens(twentyFive.pebble))]))
        }
        var outer: [RegisterRowModel] = []
        if let domains {
            outer.append(RegisterRowModel(id: "open-domains", mark: .domain, name: Copy.title(.domains, locale: ctx.locale),
                                          sub: Copy.of(domains.count, domains.of, locale: ctx.locale),
                                          opens: .sheet(.domains)))
        }
        if let emotions {
            outer.append(RegisterRowModel(id: "open-emotions", mark: .rosace,
                                          name: Copy.title(.emotions, locale: ctx.locale),
                                          sub: Copy.of(emotions.count, emotions.of, locale: ctx.locale),
                                          opens: .sheet(.emotions)))
        }
        if !outer.isEmpty {
            sections.append(RegisterSection(id: "outer", title: Copy.sheet("outer", locale: ctx.locale), rows: outer))
        }
        return sections
    }

    // MARK: - Emotions

    /// A section per family in seat order; its met seats as rows, in seat order (see the plan's contract gaps).
    static func emotions(_ emotions: Page.Emotions, _ ctx: Context) -> [RegisterSection] {
        FamilyRun.runs(emotions.seats.map(\.family)).map { run in
            let seats = Array(emotions.seats[run.start..<(run.start + run.length)])
            let rows = seats.filter(\.met).compactMap { seat -> RegisterRowModel? in
                guard let name = ctx.lookups.emotion(seat.emotionId)?.localizedName else { return nil }
                return RegisterRowModel(id: "emotion-\(seat.emotionId)",
                                        mark: .pebble(seat.pebble?.valence ?? .neutralMedium, family: seat.family),
                                        name: name, sub: seat.pebble?.name, date: date(seat.pebble, ctx),
                                        isNew: seat.isNew, opens: opens(seat.pebble))
            }
            return RegisterSection(
                id: "family-\(run.family)", title: ctx.lookups.familyName(run.family), family: run.family,
                note: Copy.of(seats.filter(\.met).count, seats.count, locale: ctx.locale), rows: rows,
                notYet: seats.filter { !$0.met }.compactMap { ctx.lookups.emotion($0.emotionId)?.localizedName })
        }
    }

    // MARK: - Domains

    static func domains(_ domains: Page.Domains, _ ctx: Context) -> [RegisterSection] {
        let total = domains.entered.count
        let rows = domains.entered.enumerated().compactMap { index, entry -> RegisterRowModel? in
            guard let name = ctx.lookups.domain(entry.domainId)?.localizedName else { return nil }
            return RegisterRowModel(id: "domain-\(entry.domainId)", mark: .domain, name: name, sub: entry.pebble?.name,
                                    date: date(entry.pebble, ctx), isNew: entry.isNew,
                                    number: Copy.registerNumber(total - index, locale: ctx.locale),
                                    opens: opens(entry.pebble))
        }
        return [
            RegisterSection(id: "entered", title: Copy.sheet("entered", locale: ctx.locale),
                            note: Copy.sheet("newestFirst", locale: ctx.locale), rows: rows),
            RegisterSection(id: "not-yet", title: Copy.sheet("notYet", locale: ctx.locale),
                            notYet: domains.notYet.compactMap { ctx.lookups.domain($0)?.localizedName })
        ]
    }

    // MARK: - Pebbles

    /// A counting stack's milestones: the levels reached (each on its stage, with
    /// its pebble for the pebbles), the next with what is left, the ladder's
    /// steps still ahead as their number alone, then the ladder's tail.
    static func ladder(_ ladder: Page.Ladder, title: String, note: String?, withPebbles: Bool,
                       _ ctx: Context) -> RegisterSection {
        func stage(_ threshold: Int) -> Int { SkylineGeometry.stageIndex(of: threshold, in: ladder.ladder) }
        var rows = ladder.levels.map { level in
            RegisterRowModel(id: "level-\(level.threshold)", mark: .stage(index: stage(level.threshold), fill: 1),
                             name: Copy.number(level.threshold, locale: ctx.locale),
                             sub: withPebbles ? level.pebble?.name : nil,
                             date: level.on.map { Copy.day($0, calendar: ctx.calendar, dateLocale: ctx.dateLocale) },
                             isNew: level.isNew, style: .number, opens: withPebbles ? opens(level.pebble) : nil)
        }
        if let next = ladder.next {
            rows.append(RegisterRowModel(id: "next-\(next.threshold)",
                                         mark: .stage(index: stage(next.threshold), fill: next.progress),
                                         name: Copy.number(next.threshold, locale: ctx.locale),
                                         next: Copy.toGo(next.left, locale: ctx.locale), style: .next))
        }
        for step in ladder.ladder where !ladder.isReached(step) && step != ladder.next?.threshold {
            rows.append(RegisterRowModel(id: "ahead-\(step)", mark: .stage(index: stage(step), fill: 0),
                                         name: Copy.number(step, locale: ctx.locale), style: .ghostNumber))
        }
        return RegisterSection(id: "ladder-\(title)", title: title, note: note, rows: rows,
                               trailing: ladder.thenEvery.map { Copy.thenEvery($0, locale: ctx.locale) })
    }

    // MARK: - The year

    /// The table: the months the server lists, in its order (newest first).
    static func months(_ year: Page.Year, _ ctx: Context) -> [YearRowModel] {
        year.listed.compactMap(year.month).map { month in
            let target = Copy.daysPerLevel
            return YearRowModel(
                month: month, name: Copy.monthHeading(month.month, locale: ctx.locale),
                moments: cell(month.momentDays, of: target, showsTarget: false, usesHue: false, ctx),
                pebbles: cell(month.pebbleDays, of: target, showsTarget: false, usesHue: true, ctx),
                weeks: cell(month.weeksFilled, of: month.fullWeeks, showsTarget: true, usesHue: false, ctx),
                accessibilityLabel: Copy.monthRow(month, locale: ctx.locale))
        }
    }

    /// The other years the payload carries, newest first as served; on the current
    /// year's sheet only, with the first-year line when there are none.
    static func earlierYears(_ year: Page.Year, all years: [Page.Year], _ ctx: Context) -> RegisterSection {
        let others = years.filter { $0.year != year.year && !$0.isCurrent }
        return RegisterSection(
            id: "earlier", title: Copy.sheet("earlier", locale: ctx.locale),
            rows: others.map { other in
                RegisterRowModel(id: "year-\(other.year)", mark: .ghost, name: String(other.year),
                                 opens: .sheet(.year(other.year)))
            },
            quiet: others.isEmpty ? Copy.firstYear(year.year, locale: ctx.locale) : nil)
    }

    // MARK: - Helpers

    private static func date(_ pebble: Page.Pebble?, _ ctx: Context) -> String? {
        pebble?.happenedAt.map { Copy.day($0, calendar: ctx.calendar, dateLocale: ctx.dateLocale) }
    }

    private static func opens(_ pebble: Page.Pebble?) -> RegisterRowModel.Opens? {
        pebble?.momentId.map { .moment($0) }
    }

    /// The board's `cell`: "—" over an empty gauge without a target, else the
    /// count capped at its target over a gauge filled by their ratio.
    private static func cell(_ value: Int, of target: Int, showsTarget: Bool, usesHue: Bool,
                             _ ctx: Context) -> YearCell {
        guard target > 0 else { return YearCell(value: "—", target: nil, fill: 0, isDone: false, usesHue: usesHue) }
        let fill = min(Double(value) / Double(target), 1)
        return YearCell(value: Copy.number(min(value, target), locale: ctx.locale),
                        target: showsTarget ? "/" + Copy.number(target, locale: ctx.locale) : nil,
                        fill: fill, isDone: fill >= 1, usesHue: usesHue)
    }
}
```

The year table's `isDone` is the one count-against-target this plan draws (a gauge set in the done style once full). Its target is `AchievementsCopy.daysPerLevel` until the payload serves one (contract gaps, item 1).

- [ ] **Step 4: Run the tests and watch them pass**

Run: `apps/ios/build/ach-test.sh AchievementsSheetRowsTests`
Expected: `** TEST SUCCEEDED **`, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements/Sheets/AchievementsSheetRows.swift \
  apps/ios/PebblesTests/Achievements/AchievementsSheetRowsTests.swift
git commit -m "feat(ios): build the achievements sheets' rows from the payload"
```

---

### Task 25: The sheets' parts

**Files:**
- Modify: `Pebbles/Features/Achievements/Figures/AchievementsType.swift` (one rank)
- Create: `Pebbles/Features/Achievements/Sheets/SheetParts.swift`

These are views over Task 24's models; Task 28's references draw them.

- [ ] **Step 1: Add the ghost milestone's rank**

In `AchievementsType.swift`, after `achievementsRowNumberSmall`, add:

```swift
    static let achievementsRowNumberGhost = Self(size: 21, weight: 250, features: [.lining], scaling: .text(.title3))
```

- [ ] **Step 2: Write the parts**

Create `Pebbles/Features/Achievements/Sheets/SheetParts.swift`:

```swift
import SwiftUI

/// A sheet's frame (the board's pick-3 register): the band, the figure large,
/// an optional lead, then its sections, on the stock. Without its scroll, so
/// the references draw it whole; `AchievementSheetHost` scrolls it.
struct SheetBody<Figure: View, Sections: View>: View {
    let band: String
    var hint: String?
    var lead: String?
    @ViewBuilder let figure: () -> Figure
    @ViewBuilder let sections: () -> Sections

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            AchievementBand(label: band, hint: hint)
            figure()
                .frame(maxWidth: .infinity)
                .padding(.top, 16)
            if let lead {
                Text(verbatim: lead)
                    .spineType(.achievementsLead)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 14)
            }
            sections()
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 34)
        .background(Color.pebbles.stock)
    }
}

/// A section's head (the board's `.lh`): the small-caps title, its italic note, a rule.
struct SheetSectionHead: View {
    let title: String
    var note: String?
    /// A family's name ink on the Emotions sheet.
    var tint: Color?

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(verbatim: title)
                .spineType(.achievementsSectionTitle)
                .foregroundStyle(tint ?? Color.pebbles.onSurface)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            if let note {
                Text(verbatim: note)
                    .spineType(.achievementsSectionNote)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
        }
        .padding(.bottom, 7)
        .overlay(alignment: .bottom) { Rectangle().fill(Color.pebbles.cardRule).frame(height: 1) }
    }
}

/// A section: its head, its rows, its "not yet" names, its tail or its quiet line.
struct SheetSectionView: View {
    let section: RegisterSection
    let lookups: WrapLookups
    var emotions: AchievementsPage.Emotions?
    var compact = false
    let open: (RegisterRowModel.Opens) -> Void

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        VStack(alignment: .leading, spacing: 0) {
            SheetSectionHead(title: section.title, note: section.note,
                             tint: section.family.map { inks.labelInk(lookups.family($0)?.palette, family: $0) })
            ForEach(section.rows) { row in
                RegisterRow(row: row, lookups: lookups, emotions: emotions, compact: compact, open: open)
            }
            if !section.notYet.isEmpty { GhostNames(names: section.notYet) }
            if let trailing = section.trailing {
                Text(verbatim: trailing)
                    .spineType(.achievementsQuiet)
                    .foregroundStyle(inks.ink2)
                    .padding(.leading, 44)
                    .frame(maxWidth: .infinity, minHeight: SpineLayout.minTarget, alignment: .leading)
                    .overlay(alignment: .bottom) { Rectangle().fill(inks.rule).frame(height: 1) }
            }
            if let quiet = section.quiet {
                Text(verbatim: quiet)
                    .spineType(.achievementsQuiet)
                    .foregroundStyle(inks.ink2)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 10)
            }
        }
        .padding(.top, 28)
    }
}

/// A register row (the board's `.r`): 50 pt (44 in the glyph stacks), a button
/// when it opens a pebble or a sheet, read by VoiceOver as one element.
struct RegisterRow: View {
    let row: RegisterRowModel
    let lookups: WrapLookups
    var emotions: AchievementsPage.Emotions?
    var compact = false
    let open: (RegisterRowModel.Opens) -> Void

    var body: some View {
        if let opens = row.opens {
            Button { open(opens) } label: { content }
                .buttonStyle(.plain)
                .accessibilityElement(children: .combine)
        } else {
            content
                .accessibilityElement(children: .combine)
        }
    }

    private var content: some View {
        HStack(spacing: row.number == nil ? 10 : 8) {
            if let number = row.number {
                Text(verbatim: number)
                    .spineType(.achievementsRegisterNumber)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .lineLimit(1)
                    .frame(width: 42, alignment: .leading)
            }
            RowMark(mark: row.mark, lookups: lookups, emotions: emotions)
                .frame(width: row.number == nil ? 34 : 30)
            VStack(alignment: .leading, spacing: 2) {
                name
                if let sub = row.sub {
                    Text(verbatim: sub)
                        .spineType(.achievementsRowSub)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let next = row.next {
                    Text(verbatim: next)
                        .spineType(.achievementsRowNext)
                        .foregroundStyle(Color.pebbles.onSurface)
                }
            }
            Spacer(minLength: 8)
            HStack(spacing: 6) {
                if row.isNew { NewDot() }
                if let date = row.date {
                    Text(verbatim: date)
                        .spineType(.achievementsRowDate)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .lineLimit(1)
                }
            }
            if case .sheet = row.opens {
                Image(systemName: "chevron.right")
                    .pebblesIcon(.medium)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .accessibilityHidden(true)
            }
        }
        .padding(.vertical, 5)
        .frame(minHeight: compact ? SpineLayout.minTarget : 50)
        .overlay(alignment: .bottom) { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var name: some View {
        switch row.style {
        case .name:
            Text(verbatim: row.name).spineType(.achievementsRowName).foregroundStyle(Color.pebbles.onSurface)
        case .number:
            Text(verbatim: row.name)
                .spineType(compact ? .achievementsRowNumberSmall : .achievementsRowNumber)
                .foregroundStyle(Color.pebbles.onSurface)
        case .next:
            Text(verbatim: row.name)
                .spineType(compact ? .achievementsRowNumberSmall : .achievementsRowNumber)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        case .ghostNumber:
            Text(verbatim: row.name)
                .spineType(compact ? .achievementsRowNumberSmall : .achievementsRowNumberGhost)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
    }
}

/// A row's mark in its 28 pt box: the figures' own pieces, larger.
struct RowMark: View {
    let mark: RegisterRowModel.Mark
    let lookups: WrapLookups
    var emotions: AchievementsPage.Emotions?

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        switch mark {
        case .rosace:
            if let emotions {
                EmotionRosace(emotions: emotions, lookups: lookups, side: 28, labelled: false, showsCentre: false,
                              showsMarks: false)
            }
        case .stage(let index, let fill):
            SkylineStage(index: index, fill: fill)
        default:
            Canvas { context, size in
                draw(context, at: CGPoint(x: size.width / 2, y: size.height / 2))
            }
            .frame(width: 28, height: 28)
            .accessibilityHidden(true)
        }
    }

    private func draw(_ c: GraphicsContext, at centre: CGPoint) {
        let inks = AchievementInks(scheme: scheme)
        switch mark {
        case .firstFive(let task):
            c.drawFirstFiveMark(task, at: centre, scale: 1.7, inks: inks)
        case .sixFirst(let kind):
            let seal = FlatSeal.sixFirst(kind)
            c.draw(FlatSeal(size: seal.size, outline: seal.outline, print: seal.print, width: seal.width * 1.55),
                   at: centre, color: inks.ink, inks: inks)
        case .twentyFive:
            let seats = ExplorerWheelGeometry.twentyFiveSeats
            c.stroke(Path.circle(centre, 11), with: .color(inks.ink2), lineWidth: 1)
            for index in 0..<seats {
                let place = ExplorerWheelGeometry.point(11, ExplorerWheelGeometry.seatAngle(index, of: seats))
                c.fill(Path.circle(CGPoint(x: centre.x + place.x, y: centre.y + place.y), 1.1), with: .color(inks.ink))
            }
        case .pebble(let valence, let family):
            let palette = family.flatMap(lookups.family)?.palette
            c.draw(FlatSeal(valence: valence, width: 9 + CGFloat(valence.intensity) * 2.4), at: centre,
                   color: inks.sealInk(palette, polarity: valence.polarity), inks: inks)
        case .domain:
            c.draw(FlatSeal(size: .small, outline: .neutral, print: .solid, width: 17), at: centre, color: inks.ink,
                   inks: inks)
        case .ghost:
            c.drawGhost(at: centre, radius: 3.6, inks: inks)
        case .rosace, .stage:
            break
        }
    }
}

/// The new dot (D10): 5 pt of ink, named for VoiceOver.
struct NewDot: View {
    var body: some View {
        Circle()
            .fill(Color.pebbles.onSurface)
            .frame(width: 5, height: 5)
            .accessibilityLabel(Text(verbatim: AchievementsCopy.newMark(locale: SpineStrings.appLocale)))
    }
}

/// "Not yet" as dotted names (the board's `.rest`), wrapping.
struct GhostNames: View {
    let names: [String]

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        GhostFlow(spacing: 14, lineSpacing: 6) {
            ForEach(Array(names.enumerated()), id: \.offset) { _, name in
                HStack(spacing: 6) {
                    Canvas { c, size in
                        c.drawGhost(at: CGPoint(x: size.width / 2, y: size.height / 2), radius: 3.6, inks: inks)
                    }
                    .frame(width: 10, height: 10)
                    .accessibilityHidden(true)
                    Text(verbatim: name)
                        .spineType(.achievementsGhostName)
                        .foregroundStyle(inks.ink2)
                }
            }
        }
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(alignment: .bottom) { Rectangle().fill(inks.rule).frame(height: 1) }
    }
}

/// Leading-aligned lines of subviews, wrapping at the width offered.
struct GhostFlow: Layout {
    var spacing: CGFloat
    var lineSpacing: CGFloat

    private struct Item {
        let index: Int
        let x: CGFloat
        let size: CGSize
    }

    private struct Line {
        var items: [Item] = []
        var y: CGFloat = 0
        var height: CGFloat = 0
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let lines = arrange(proposal.width ?? .infinity, subviews)
        let widest = lines.map { line in line.items.last.map { $0.x + $0.size.width } ?? 0 }.max() ?? 0
        return CGSize(width: proposal.width ?? widest, height: lines.last.map { $0.y + $0.height } ?? 0)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for line in arrange(bounds.width, subviews) {
            for item in line.items {
                subviews[item.index].place(at: CGPoint(x: bounds.minX + item.x, y: bounds.minY + line.y),
                                           proposal: ProposedViewSize(item.size))
            }
        }
    }

    private func arrange(_ maxWidth: CGFloat, _ subviews: Subviews) -> [Line] {
        var lines: [Line] = []
        var line = Line()
        var x: CGFloat = 0
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            if x > 0, x + size.width > maxWidth {
                lines.append(line)
                line = Line(y: line.y + line.height + lineSpacing)
                x = 0
            }
            line.items.append(Item(index: index, x: x, size: size))
            line.height = max(line.height, size.height)
            x += size.width + spacing
        }
        if !line.items.isEmpty { lines.append(line) }
        return lines
    }
}

/// The year table's column heads (the board's `.yth`).
struct YearColumnsHead: View {
    var body: some View {
        let locale = SpineStrings.appLocale
        HStack(spacing: 6) {
            Spacer(minLength: 0)
            ForEach(["moments", "pebbles", "weeks"], id: \.self) { key in
                Text(verbatim: AchievementsCopy.sheet("column.\(key)", locale: locale))
                    .spineType(.achievementsColumn)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(width: 58)
            }
        }
        .padding(.top, 8)
        .padding(.bottom, 4)
        .overlay(alignment: .bottom) { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
        .accessibilityHidden(true)
    }
}

/// A month of the year table (the board's `.r.yt`): its counter, its name and
/// three gauges; a month without pebbles dims its name and gauges.
struct YearRow: View {
    let row: YearRowModel
    let lookups: WrapLookups

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        let hue = row.month.isMuted ? nil : row.month.family.flatMap(lookups.family).flatMap { inks.hue($0.palette) }
        HStack(spacing: 6) {
            Canvas { c, size in
                c.drawMonthCounter(row.month, at: CGPoint(x: size.width / 2, y: size.height / 2), hue: hue, width: 14,
                                   inks: inks)
            }
            .frame(width: 30, height: 28)
            Group {
                Text(verbatim: row.name)
                    .spineType(.achievementsRowName)
                    .foregroundStyle(inks.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Spacer(minLength: 4)
                YearCellView(cell: row.moments, hue: nil, inks: inks)
                YearCellView(cell: row.pebbles, hue: hue, inks: inks)
                YearCellView(cell: row.weeks, hue: nil, inks: inks)
            }
            .opacity(row.month.isMuted ? 0.55 : 1)
        }
        .frame(minHeight: 48)
        .overlay(alignment: .bottom) { Rectangle().fill(inks.rule).frame(height: 1) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: row.accessibilityLabel))
    }
}

/// One gauge (the board's `cell`): the number (the done style once full) over a hairline and its fill.
struct YearCellView: View {
    let cell: YearCell
    let hue: Color?
    let inks: AchievementInks

    var body: some View {
        let tone = cell.usesHue ? (hue ?? inks.ink) : inks.ink
        VStack(spacing: 4) {
            HStack(spacing: 0) {
                Text(verbatim: cell.value)
                    .spineType(cell.isDone ? .achievementsCellDone : .achievementsCell)
                    .foregroundStyle(cell.isDone ? tone : inks.ink2)
                if let target = cell.target {
                    Text(verbatim: target)
                        .spineType(.achievementsCell)
                        .foregroundStyle(inks.ink2)
                }
            }
            ZStack(alignment: .leading) {
                Rectangle().fill(inks.ink2.opacity(0.55)).frame(height: 1)
                if cell.fill > 0 {
                    Capsule().fill(tone).frame(width: 52 * CGFloat(cell.fill), height: 2.5)
                }
            }
            .frame(width: 52, height: 4)
        }
        .frame(width: 58)
    }
}
```

- [ ] **Step 3: Build and commit**

Run: `apps/ios/build/ach-test.sh AchievementsSheetRowsTests`
Expected: `** TEST SUCCEEDED **` (the parts compile).

```bash
git add apps/ios/Pebbles/Features/Achievements
git commit -m "feat(ios): add the achievements sheets' register parts"
```

---

### Task 26: The five sheets and their host

**Files:**
- Create: `Pebbles/Features/Achievements/Sheets/AchievementSheets.swift`
- Create: `PebblesTests/Achievements/AchievementSheetRoutingTests.swift`

- [ ] **Step 1: Write the failing test**

Create `PebblesTests/Achievements/AchievementSheetRoutingTests.swift`:

```swift
import Testing
@testable import Pebbles

/// What each sheet marks seen when it opens (D10), and its identity for `.sheet(item:)`.
@Suite("Achievement sheet routing")
struct AchievementSheetRoutingTests {
    @Test("each sheet marks its own card, a year sheet the year card")
    func cards() {
        let sheets: [AchievementSheet] = [.explorer, .emotions, .domains, .pebbles, .year(2026), .year(2025)]
        #expect(sheets.map(\.card) == [.explorer, .emotions, .domains, .pebbles, .year, .year])
        #expect(Set(sheets.map(\.id)).count == sheets.count)
    }

    @Test("a sheet's rows open in the app's calendar and language")
    @MainActor
    func context() throws {
        let page = try AchievementsFixtures.payload("design-fixture").model
        let ctx = AchievementsSheetRows.Context.current(AchievementsLookups.fixture(page))
        #expect(ctx.calendar.timeZone == SpineCalendar.current.timeZone)
        #expect(ctx.locale == SpineStrings.appLocale)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `apps/ios/build/ach-test.sh AchievementSheetRoutingTests`
Expected: the build fails with `error: type 'AchievementsSheetRows.Context' has no member 'current'`.

- [ ] **Step 3: Write the sheets**

Create `Pebbles/Features/Achievements/Sheets/AchievementSheets.swift`:

```swift
import SwiftUI

extension AchievementsSheetRows.Context {
    /// The app's: the Spine's calendar, the app's language, the device's date locale.
    @MainActor
    static func current(_ lookups: WrapLookups) -> Self {
        Self(lookups: lookups, calendar: SpineCalendar.current, locale: SpineStrings.appLocale,
             dateLocale: SpineStrings.dateLocale)
    }
}

/// Explorer (NOTES "The sheets"): the wheel large, a section per ring inside
/// out, then the rows that open Domains and Emotions on top of this sheet.
struct ExplorerSheetContent: View {
    let explorer: AchievementsPage.Explorer
    let emotions: AchievementsPage.Emotions?
    let domains: AchievementsPage.Domains?
    let lookups: WrapLookups
    let open: (RegisterRowModel.Opens) -> Void

    var body: some View {
        let ctx = AchievementsSheetRows.Context.current(lookups)
        SheetBody(band: AchievementsCopy.title(.explorer, locale: ctx.locale),
                  hint: AchievementsCopy.explorerHint(locale: ctx.locale)) {
            ExplorerWheel(explorer: explorer, emotions: emotions, domains: domains, lookups: lookups, side: 340)
        } sections: {
            ForEach(AchievementsSheetRows.explorer(explorer, emotions: emotions, domains: domains, ctx)) { section in
                SheetSectionView(section: section, lookups: lookups, emotions: emotions, open: open)
            }
        }
    }
}

/// Emotions: the rosace large, then each family in seat order, what was met
/// with its pebble's seal and date, what was not set quietly.
struct EmotionsSheetContent: View {
    let emotions: AchievementsPage.Emotions
    let since: Date?
    let lookups: WrapLookups
    let open: (RegisterRowModel.Opens) -> Void

    var body: some View {
        let ctx = AchievementsSheetRows.Context.current(lookups)
        SheetBody(band: AchievementsCopy.title(.emotions, locale: ctx.locale),
                  hint: since.map { AchievementsCopy.sinceHint($0, calendar: ctx.calendar, locale: ctx.locale,
                                                                dateLocale: ctx.dateLocale) }) {
            EmotionRosace(emotions: emotions, lookups: lookups, side: 330)
        } sections: {
            ForEach(AchievementsSheetRows.emotions(emotions, ctx)) { section in
                SheetSectionView(section: section, lookups: lookups, emotions: emotions, open: open)
            }
        }
    }
}

/// Domains: the band, what the coverage means, the register newest first, then not yet.
struct DomainsSheetContent: View {
    let domains: AchievementsPage.Domains
    let lookups: WrapLookups
    let open: (RegisterRowModel.Opens) -> Void

    var body: some View {
        let ctx = AchievementsSheetRows.Context.current(lookups)
        SheetBody(band: AchievementsCopy.title(.domains, locale: ctx.locale),
                  hint: AchievementsCopy.domainsHint(locale: ctx.locale),
                  lead: AchievementsCopy.coverage(domains.coverage, locale: ctx.locale)) {
            DomainBand(domains: domains)
        } sections: {
            ForEach(AchievementsSheetRows.domains(domains, ctx)) { section in
                SheetSectionView(section: section, lookups: lookups, open: open)
            }
        }
    }
}

/// Pebbles: the skyline, the milestones reached and the next, then the glyph
/// stacks in the same grammar, smaller. The next milestone shows what is left, never a date.
struct PebblesSheetContent: View {
    let pebbles: AchievementsPage.Ladder
    let carved: AchievementsPage.Ladder?
    let sales: AchievementsPage.Ladder?
    let since: Date?
    let lookups: WrapLookups
    let open: (RegisterRowModel.Opens) -> Void

    var body: some View {
        let ctx = AchievementsSheetRows.Context.current(lookups)
        let locale = ctx.locale
        SheetBody(band: AchievementsCopy.title(.pebbles, locale: locale),
                  hint: since.map { AchievementsCopy.sinceHint($0, calendar: ctx.calendar, locale: locale,
                                                                dateLocale: ctx.dateLocale) },
                  lead: AchievementsCopy.pebblesLead(pebbles, locale: locale)) {
            PebbleSkyline(ladder: pebbles, width: 350)
        } sections: {
            SheetSectionView(
                section: AchievementsSheetRows.ladder(
                    pebbles, title: AchievementsCopy.sheet("milestones", locale: locale),
                    note: AchievementsCopy.of(pebbles.reachedSteps.count, pebbles.ladder.count, locale: locale),
                    withPebbles: true, ctx),
                lookups: lookups, open: open)
            if let carved {
                SheetSectionView(
                    section: AchievementsSheetRows.ladder(
                        carved, title: AchievementsCopy.glyphsCarved(locale: locale),
                        note: AchievementsCopy.number(carved.count, locale: locale), withPebbles: false, ctx),
                    lookups: lookups, compact: true, open: open)
            }
            if let sales {
                SheetSectionView(
                    section: AchievementsSheetRows.ladder(
                        sales, title: AchievementsCopy.glyphSales(locale: locale),
                        note: AchievementsCopy.number(sales.count, locale: locale), withPebbles: false, ctx),
                    lookups: lookups, compact: true, open: open)
            }
        }
    }
}

/// The year: the docked disc large, the next step, the months newest first
/// with their three counters, then the earlier years (the current year's sheet only).
struct YearSheetContent: View {
    let year: AchievementsPage.Year
    let years: [AchievementsPage.Year]
    let lookups: WrapLookups
    let open: (RegisterRowModel.Opens) -> Void

    var body: some View {
        let ctx = AchievementsSheetRows.Context.current(lookups)
        SheetBody(band: String(year.year), hint: AchievementsCopy.yearHint(year, locale: ctx.locale),
                  lead: year.nextStep.map { AchievementsCopy.nextStep($0, locale: ctx.locale) }) {
            YearDisc(year: year, lookups: lookups, side: 330)
        } sections: {
            VStack(alignment: .leading, spacing: 0) {
                SheetSectionHead(title: AchievementsCopy.sheet("months", locale: ctx.locale),
                                 note: AchievementsCopy.sheet("newestFirst", locale: ctx.locale))
                YearColumnsHead()
                ForEach(AchievementsSheetRows.months(year, ctx)) { row in
                    YearRow(row: row, lookups: lookups)
                }
            }
            .padding(.top, 28)
            if year.isCurrent {
                SheetSectionView(section: AchievementsSheetRows.earlierYears(year, all: years, ctx), lookups: lookups,
                                 open: open)
            }
        }
    }
}

/// Presents one sheet (#1306, spec §4): it scrolls, sits at the large detent,
/// marks its card seen when it appears, and presents a sheet a row opens on top
/// of itself (Explorer's Domains and Emotions, an earlier year). A row that
/// opens a pebble hands its moment to `openMoment`, which closes the sheets
/// first: the held card is drawn above the tabs, under any sheet.
struct AchievementSheetHost: View {
    let sheet: AchievementSheet
    let page: AchievementsPage
    let lookups: WrapLookups
    let seen: (AchievementCard) -> Void
    let openMoment: (UUID) -> Void

    @State private var pushed: AchievementSheet?

    var body: some View {
        ScrollView {
            content
        }
        .background(Color.pebbles.stock)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .onAppear { seen(sheet.card) }
        .sheet(item: $pushed) { inner in
            AchievementSheetHost(sheet: inner, page: page, lookups: lookups, seen: seen, openMoment: openMoment)
        }
    }

    @ViewBuilder
    private var content: some View {
        let firstPebbleDay = page.explorer?.firstPebble?.happenedAt
        switch sheet {
        case .explorer:
            if let explorer = page.explorer {
                ExplorerSheetContent(explorer: explorer, emotions: page.emotions, domains: page.domains,
                                     lookups: lookups, open: route)
            }
        case .emotions:
            if let emotions = page.emotions {
                EmotionsSheetContent(emotions: emotions, since: firstPebbleDay, lookups: lookups, open: route)
            }
        case .domains:
            if let domains = page.domains {
                DomainsSheetContent(domains: domains, lookups: lookups, open: route)
            }
        case .pebbles:
            if let pebbles = page.pebbles {
                PebblesSheetContent(pebbles: pebbles, carved: page.glyphsCarved, sales: page.glyphSales,
                                    since: firstPebbleDay, lookups: lookups, open: route)
            }
        case .year(let number):
            if let year = page.years.first(where: { $0.year == number }) {
                YearSheetContent(year: year, years: page.years, lookups: lookups, open: route)
            }
        }
    }

    private func route(_ opens: RegisterRowModel.Opens) {
        switch opens {
        case .moment(let id): openMoment(id)
        case .sheet(let next): pushed = next
        }
    }
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `apps/ios/build/ach-test.sh AchievementSheetRoutingTests`
Expected: `** TEST SUCCEEDED **`, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Achievements/Sheets/AchievementSheets.swift \
  apps/ios/PebblesTests/Achievements/AchievementSheetRoutingTests.swift
git commit -m "feat(ios): add the five achievements sheets"
```

---

### Task 27: Open the sheets from the cards

**Files:**
- Modify: `Pebbles/Features/Profile/AchievementsView.swift`

- [ ] **Step 1: Present the sheets, mark them seen, open a pebble after they close**

In `AchievementsView`:

Add, after the `refs` environment line:

```swift
    @Environment(HeldCardHostModel.self) private var heldCard
```

Add, after `@State private var model = AchievementsModel()`:

```swift
    @State private var sheet: AchievementSheet?
    /// A row's moment, opened once the sheets have closed.
    @State private var pendingMoment: UUID?
```

In `content`, replace the `ScrollView { … }` branch with:

```swift
        if let page = model.page {
            let lookups = HomeLookups.make(palettes: palettes, refs: refs)
            ScrollView {
                AchievementsPageContent(page: page, lookups: lookups, pulling: model.pulling, onPull: pull,
                                        onOpen: { sheet = $0 })
                    .padding(.top, Spacing.sm)
            }
            .sheet(item: $sheet, onDismiss: openPendingMoment) { opened in
                AchievementSheetHost(sheet: opened, page: page, lookups: lookups,
                                     seen: { model.opened($0) },
                                     openMoment: { id in
                                         pendingMoment = id
                                         sheet = nil
                                     })
            }
```

(the `else if model.failed` and `else` branches stay as they are).

Add, after `pull(_:)`:

```swift
    /// The held card is drawn above the tabs, under any sheet: a row's pebble
    /// opens once the sheets are down, and a change on the card reads the page again.
    private func openPendingMoment() {
        guard let id = pendingMoment else { return }
        pendingMoment = nil
        heldCard.open(HeldCardRequest(momentId: id, origin: .you, onChanged: { Task { await load() } }))
    }
```

- [ ] **Step 2: Build and run the achievements suites**

Run: `apps/ios/build/ach-test.sh "" References`
Expected: `** TEST SUCCEEDED **` (or exactly main's failures).

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/AchievementsView.swift
git commit -m "feat(ios): open the achievements sheets from their cards and mark them seen"
```

---

### Task 28: The sheets' references; part 4 gate and PR

**Files:**
- Create: `PebblesTests/References/AchievementsSheetReferences.swift`

- [ ] **Step 1: Write the references**

Create `PebblesTests/References/AchievementsSheetReferences.swift`:

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1306: the five sheets on the design fixture, unrolled, Paper and Night;
    /// the Emotions and the year sheets in French too.
    @Suite("Achievements sheet references")
    @MainActor
    struct AchievementsSheetReferences {
        /// One sheet's content, unwrapped before it is built: a result builder cannot `try`.
        static func sheet(_ sheet: AchievementSheet) throws -> AnyView {
            let page = try AchievementsFixtures.payload("design-fixture").model
            let lookups = AchievementsLookups.fixture(page)
            let first = page.explorer?.firstPebble?.happenedAt
            let open: (RegisterRowModel.Opens) -> Void = { _ in }
            switch sheet {
            case .explorer:
                return AnyView(ExplorerSheetContent(explorer: try #require(page.explorer), emotions: page.emotions,
                                                    domains: page.domains, lookups: lookups, open: open))
            case .emotions:
                return AnyView(EmotionsSheetContent(emotions: try #require(page.emotions), since: first,
                                                    lookups: lookups, open: open))
            case .domains:
                return AnyView(DomainsSheetContent(domains: try #require(page.domains), lookups: lookups, open: open))
            case .pebbles:
                return AnyView(PebblesSheetContent(pebbles: try #require(page.pebbles), carved: page.glyphsCarved,
                                                   sales: page.glyphSales, since: first, lookups: lookups, open: open))
            case .year:
                return AnyView(YearSheetContent(year: try #require(page.years.first), years: page.years,
                                                lookups: lookups, open: open))
            }
        }

        static let sheets: [AchievementSheet] = [.explorer, .emotions, .domains, .pebbles, .year(2026)]

        @Test(arguments: ReferenceScheme.allCases)
        func sheets(scheme: ReferenceScheme) throws {
            for sheet in Self.sheets {
                let french = sheet == .emotions || sheet == .year(2026)
                let locales: Set<ReferenceLocale> = scheme == .paper && french ? [.en, .fr] : [.en]
                ReferenceImage.assertMatches(try Self.sheet(sheet), named: "sheet-\(sheet.id)",
                                             config: ReferenceConfig(scheme: scheme, locales: locales))
            }
        }
    }
}
```

- [ ] **Step 2: Run the references and look at the renders**

Run: `apps/ios/build/ach-test.sh References`
Expected: the baseline's failures plus `no reference at` lines for `sheet-explorer`, `sheet-emotions`, `sheet-domains`, `sheet-pebbles` and `sheet-year-2026`, each in `-paper-en` and `-night-en`. Nothing else.

Open the actual PNGs beside the board's sheets (`final/sheets.html`):
- **Explorer:** five dated first-five rows with their marks, the six firsts named Small to Highlight, "The 25th", then Domains and Emotions rows with a chevron.
- **Emotions:** joy's five rows and "Grateful" dotted; shame's section empty (the fixture has no shame family).
- **Domains:** No. 14 Events with its dot down to No. 01 Family, then four dotted names.
- **Pebbles:** five solid stages, 250 part-filled with "113 to go", 500 and 1,000 as ghost numbers, "then every 500", then the glyph stacks in 44 pt rows.
- **Year:** November to March, August's pebbles gauge in its hue, November's weeks "—", "None yet. 2026 is your first year here."

Every row is at least 44 pt.

- [ ] **Step 3: Check it on the simulator**

With the build from Task 22 step 3 (rebuild and reinstall after this part):
1. Open each card: its sheet rises at the large detent.
2. In Explorer, the Domains row puts the Domains sheet on top, and dismissing it returns to Explorer.
3. A pebble row closes the sheets and opens the held card.
4. Reopen the page later: the new marks on a card whose sheet was opened are gone. This is the server's `new` after `mark_achievements_seen`.
5. A ready mark (on a test account whose six firsts completed before 06:00 today, seeded by the harness's service-role path) plays the unlock moment with the stack's karma, then the mark is gone.

- [ ] **Step 4: Run every unit test but the references, and the lint**

Run: `apps/ios/build/ach-test.sh "" References`, then `npm run lint --workspace=@pbbls/ios`
Expected: `** TEST SUCCEEDED **`; no `error:`.

- [ ] **Step 5: Open the PR**

```bash
gh stack submit --auto
```

Title: `feat(ios): open the achievements sheets`. Body opens with `Resolves #1306` if part 5 has already merged, else `Part of #1306`; same labels and milestone, plus `rebaseline-ios-screenshots` (added alone). Lab Note as in Task 22 step 5. Note in the body that Android (#1307) mirrors this 1:1, so the contract gaps below are raised on #1305 for both clients rather than solved here.

---

# Part 5 — replay the harness's payloads (branch `test/1306-ios-achievements-payloads`, stacked on part 4)

Start this part once #1305 parts 2 and 3 are merged and `packages/supabase/scripts/oracles/achievements/payloads/` holds the four dumps (`check-achievements`, `get-achievements`, `get-achievements-year`, `get-achievements-seen`), committed by backend Tasks 10 and 14 with `ACHIEVEMENTS_DUMP=1`. If the folder is missing on `main`, stop: ask for the dumps on #1305 rather than writing them by hand.

- [ ] **Start the branch:** `git fetch origin && gh stack rebase && gh stack add test/1306-ios-achievements-payloads`

### Task 29: The contract on the server's own payloads

**Files:**
- Create: `PebblesTests/Achievements/AchievementsHarnessPayloads.swift`
- Create: `PebblesTests/Achievements/AchievementsHarnessPayloadTests.swift`
- Modify: `PebblesTests/Achievements/AchievementsPayloadTests.swift` (its doc comment)

- [ ] **Step 1: Check the dumps are there**

Run: `ls packages/supabase/scripts/oracles/achievements/payloads/`
Expected: `check-achievements.json  get-achievements-seen.json  get-achievements-year.json  get-achievements.json`.

- [ ] **Step 2: Write the loader**

Create `PebblesTests/Achievements/AchievementsHarnessPayloads.swift`:

```swift
import Foundation
@testable import Pebbles

/// The server's own achievements payloads (#1305 Tasks 10 and 14), captured
/// verbatim by `verify-achievements.ts` (`ACHIEVEMENTS_DUMP=1`) and read in place
/// through `#filePath`: one copy, owned by the harness, as the wraps' are.
enum AchievementsHarnessPayloads {
    static let folder = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // Achievements
        .deletingLastPathComponent() // PebblesTests
        .deletingLastPathComponent() // apps/ios
        .deletingLastPathComponent() // apps
        .deletingLastPathComponent() // the repository
        .appendingPathComponent("packages/supabase/scripts/oracles/achievements/payloads")

    static func data(_ name: String) throws -> Data {
        try Data(contentsOf: folder.appendingPathComponent("\(name).json"))
    }

    static func payload(_ name: String) throws -> AchievementsPayload {
        try WrapDecoding.decode(AchievementsPayload.self, from: data(name))
    }

    /// Every key path in a JSON body, arrays collapsed to `[]`: the shape, not the values.
    static func keyPaths(_ data: Data) throws -> Set<String> {
        var paths: Set<String> = []
        func walk(_ value: Any, _ path: String) {
            if let object = value as? [String: Any] {
                for (key, child) in object {
                    let next = path.isEmpty ? key : "\(path).\(key)"
                    paths.insert(next)
                    walk(child, next)
                }
            } else if let array = value as? [Any] {
                for child in array { walk(child, "\(path)[]") }
            }
        }
        walk(try JSONSerialization.jsonObject(with: data), "")
        return paths
    }
}
```

- [ ] **Step 3: Write the tests**

Create `PebblesTests/Achievements/AchievementsHarnessPayloadTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// The contract on the server's own bodies (#1306, the foreign-payload rule):
/// each dump decodes with nothing dropped, carries what the harness asserted on
/// the server, and has no key the design fixture lacks, so every key the
/// server sends is one this decoder was written against.
@Suite("Achievements harness payloads")
struct AchievementsHarnessPayloadTests {
    typealias H = AchievementsHarnessPayloads

    @Test("get_achievements for three pebbles: the six firsts done, one emotion and one domain, 3 of 10")
    func threePebbles() throws {
        let payload = try H.payload("get-achievements")
        #expect(payload.skipped.isEmpty)
        let page = payload.model
        let explorer = try #require(page.explorer)
        #expect(explorer.firstPebble != nil)
        #expect(explorer.sixFirsts.compactMap(\.kind) == AchievementsPage.StoneKind.allCases)
        #expect(explorer.sixFirsts.allSatisfy { $0.done })
        #expect(explorer.firstFive.compactMap(\.task) == HomeChecklist.Task.allCases)
        #expect(explorer.ringsDone.contains(.sixFirsts))
        #expect(explorer.completion?.isReady == false && explorer.completion?.pulledAt == nil)
        #expect(page.emotions?.seats.count == 38 && page.emotions?.count == 1)
        #expect(page.emotions?.seats.filter(\.met).count == 1)
        #expect(page.domains?.count == 1 && page.domains?.coverage == .few && page.domains?.entered.count == 1)
        #expect(page.domains.map { $0.notYet.count == $0.of - 1 } == true)
        #expect(page.pebbles?.count == 3)
        #expect(page.pebbles?.next == AchievementsPage.Next(threshold: 10, left: 7, progress: 0.222))
        #expect(page.pebbles?.thenEvery == 500)
        #expect(explorer.sixFirsts.allSatisfy { !$0.isNew } && (page.pebbles?.levels.allSatisfy { !$0.isNew } ?? false))
        #expect(page.years.first?.isCurrent == true && page.years.first?.months.count == 12)
    }

    @Test("get_achievements with a past year: the current year then 2025, each a whole, bounded disc")
    func years() throws {
        let payload = try H.payload("get-achievements-year")
        #expect(payload.skipped.isEmpty)
        let years = payload.model.years
        #expect(years.count == 2 && years[0].isCurrent && years[1].year == 2025 && !years[1].isCurrent)
        #expect(years[1].nextStep == nil)
        for year in years {
            #expect(year.months.map(\.month) == Array(1...12))
            #expect(year.listed == year.listed.sorted(by: >))
            #expect(year.months.allSatisfy { (0...2).contains($0.level) && (0...1).contains($0.progress) })
            #expect(year.months.allSatisfy { $0.state != .kept || $0.family != nil })
            #expect(year.months.allSatisfy { $0.state != .upcoming || $0.family == nil })
        }
    }

    @Test("after the emotions card was seen, the one emotion first recorded since is new")
    func seen() throws {
        let page = try H.payload("get-achievements-seen").model
        #expect(page.emotions?.seats.filter(\.isNew).count == 1)
    }

    @Test("check_achievements' first rows over a heavy first pebble: only first-moment is celebrated")
    func check() throws {
        let rows = try JSONDecoder().decode([AchievementCheckResult].self, from: H.data("check-achievements"))
        let silent = rows.filter {
            $0.slug.hasPrefix("first-stone-") || $0.slug.hasPrefix("emotion-first-") || $0.slug.hasPrefix("domain-first-")
        }
        #expect(!silent.isEmpty && silent.allSatisfy { !$0.celebrate && $0.karmaGranted == 0 })
        #expect(Set(AchievementCheckResult.celebrated(rows).map(\.slug)) == ["first-moment"])
    }

    @Test("every key the server sends is one the design fixture carries")
    func shape() throws {
        let design = try H.keyPaths(AchievementsFixtures.data("design-fixture"))
        for dump in ["get-achievements", "get-achievements-year", "get-achievements-seen"] {
            let unknown = try H.keyPaths(H.data(dump)).subtracting(design)
            #expect(unknown.isEmpty, "\(dump) sends keys the fixture lacks: \(unknown.sorted())")
        }
    }
}
```

If `shape` fails, the server has added a key: decode it (Task 2's payload), add it to `design-fixture.json`, and map it, in this part.

- [ ] **Step 4: Run them**

Run: `apps/ios/build/ach-test.sh AchievementsHarnessPayloadTests`
Expected: `** TEST SUCCEEDED **`, 5 tests. A failure here is a contract difference between this plan's reading of Task 9 and the server: fix the decoder or the mapping, never the dump.

- [ ] **Step 5: Point the hand-written fixture at its new role**

In `PebblesTests/Achievements/AchievementsPayloadTests.swift`, replace the suite's doc comment with:

```swift
/// `get_achievements` decoded from the board's fixture (#1306): the design's values
/// in Task 9's shape, the precisions every surface emits, omitted sections and a
/// section that does not decode. The contract itself is proved on the server's
/// own bodies in `AchievementsHarnessPayloadTests`; this fixture stays the
/// mapping's, the copy's and the references' (the dumps hold three pebbles, the
/// board 137).
```

- [ ] **Step 6: Run everything but the references, lint, commit, open the PR**

Run: `apps/ios/build/ach-test.sh "" References`, then `npm run lint --workspace=@pbbls/ios`
Expected: `** TEST SUCCEEDED **`; no `error:`.

```bash
git add apps/ios/PebblesTests/Achievements
git commit -m "test(ios): replay the achievements harness payloads"
gh stack submit --auto
```

Title: `test(ios): replay the achievements payloads the harness dumps`. Body opens with `Resolves #1306`; same labels (species `test`) and milestone; `no-lab-note`. Merge the stack whole with `gh stack merge --yes` once every part is green and the reference bot's PNGs are in (rebase and re-run CI first if the bot's commits broke linearity). The Arkaik App moves `V-achievements` to `releasing` at merge; never set `live`.

---

## Contract gaps found in the back-end plan

Raise these on #1305; Android (#1307) and web (#1308) meet the same ones.

1. **The year's 14-day target is not served.** The table prints `n/14` and fills its gauges toward 14, but `achievements_year` sends `moment_days`, `pebble_days`, `days_left` and `level`, never the target. This plan names it once, as `AchievementsCopy.daysPerLevel`. The "done" style of a full gauge is the only count-against-target the client draws. **Ask:** a year-level `days_per_level: 14`, or per-month `days_needed`, and a `done` flag per counter.
2. **The year card has no "new" flag.** `achievement_views` accepts `'year'` and the board marks October reaching the rim as new, but `achievements_year` compares nothing with `seen->>'year'`. iOS marks the year card seen when its sheet opens, and draws no new mark on the disc. **Ask:** `months[].new`, true when the month reached its current level after the year card's `seen_at`. That needs a stored high-water mark, which is open question 3 of the spec.
3. **The Emotions sheet's order "by date" is not served.** NOTES ask for each family's met emotions by date. The payload only gives seat order, so iOS lists them in seat order rather than sort.
   - Seats, `entered` domains, `six_firsts` and `twenty_five` carry no `on` date of their own. A row's date is its pebble's `happened_at`, so a row whose pebble was deleted (D4: `pebble_id` set null) shows no date.
   - **Ask:** `on` on seats, entries, six firsts and the 25th, as the ladder levels and the first five have; a met order inside each family is optional.
4. **The ceremony has no design and no copy key.** `pull_ceremony` returns the stack and the karma. iOS plays the existing unlock moment with three stack titles and bodies it adds (`achievements.ceremony.*`). **Ask:** a board for the ceremony, or a confirmation that the unlock moment is it.
5. **"New since 2 October" has no date.** The board's card labels name the last visit. The payload has the flags but not `seen_at`, so VoiceOver says "3 new" without "since …". **Ask:** a `seen_on` local date per card, if the date is wanted.
6. **The spec and the SQL name a family differently.** D12's tie-break lists `peaceful`; the SQL, the oracle and `EmotionFamilies.wheel` use `peace`, the real slug. Fix the spec's word.
7. **`rings_done` can hold five values.** The spec's example shows three (`first_five`, `six_firsts`, `twenty_five`); Task 9 also appends `domains` and `emotions`. iOS reads all five. Make the spec's example match.
8. **The Profile shelf will show the new families.** It reads the catalog and every unlock, so `first_stone` and `set_complete` badges reach the owner's own shelf. Task 8 names them. Only the public projection drops them (D16). If the owner's shelf should hide them too, that is a product call to make on #1305.

## Self-review notes

**Spec coverage (§4, §5):**
- Decode and map: Tasks 2, 3. Replay of verbatim payloads: Task 29. Precision variants and omissions: Tasks 2, 3.
- Draw with geometry only: Tasks 10–15. Colour by family slug: `AchievementInks`, with ink for an unknown family. Paper and Night: the roles, plus the references in Tasks 16, 22 and 28.
- Localise: Tasks 4, 17 and 23, in EN and FR. Plurals use `one`/`many`/`other`, and months and numerals follow the locale.
- Resilience:
  - the cached page shows at once (Tasks 5, 6);
  - a failed read keeps it and logs (Task 6);
  - a missing section hides its card (Task 21);
  - an unknown enum falls back (Task 3);
  - an undecodable section is dropped and logged (Tasks 2, 5).
- Interaction:
  - seen marks are fire-and-forget (Tasks 6, 26–27);
  - the ready mark pulls the ceremony (Tasks 19, 21);
  - Explorer's rows push their own sheets (Task 26);
  - a pebble row opens the held card (Task 27).
- Celebration filtered on `celebrate`: Task 7.
- Accessibility:
  - labels are assembled from the payload (Task 17);
  - cards are buttons and the ready mark is its own button;
  - rows are at least 44 pt (Tasks 19, 25);
  - colour is never the only signal: level is position, the rim is a ring, new is a mark;
  - there is an ax2 reference (Task 22).

**Checked while writing:** every Swift and JSON block of Tasks 1–29 was applied to a scratch copy of `apps/ios` (no git), the strings added with the helper, and the app and test bundle built for the simulator. Then all twenty achievements unit suites ran green on a throwaway simulator, `ThemeLiteralsTests` included: it flagged nothing under `Features/Achievements/`. The three reference suites also rendered. Those renders caught two things now fixed above: the seal outline ignores its rect's origin, so `FlatSeal` moves it into place, and a long sum in `AchievementsA11y` timed out the type checker.

**Open to the maintainer before part 3 ships:**
- The Lab Note or `no-lab-note` (Task 22).
- The ceremony's look (gap 4).

