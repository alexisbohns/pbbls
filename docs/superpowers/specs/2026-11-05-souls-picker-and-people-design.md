# Souls picker and People tab — design

**Date:** 2026-11-05
**Issues:** #1518 iOS (reference), #1519 Android (mirror), #1520 web, on M70 · Record flow. No back-end issue: counts already ride on the souls fetch.
**Design:** the 2026-11-04 souls exploration, canvas https://claude.ai/artifact/JYd7fitbmeoZRvtULQdh7o (private), page "Converged": screens 01–06 the iOS picker, 07 and 09 the iOS People tab, 10, 11 and 13 Android.
**Companion:** `2026-11-04-record-flow-redesign-design.md`, whose Life screen (D10) hosts this picker. The iOS build ships both in one stack (Plan below).
**Inventory:** counted on `main` at `b35488b18`. iOS has a grid sheet (`Features/Path/SoulPickerSheet.swift`, `SoulPickerContent.swift`) used by the edit pebble form (`SelectedSoulsRow.swift`) and the record flow's souls step (`Complete/Steps/CompleteSoulsStep.swift`), and a souls list on the People tab (`Profile/Lists/SoulsListView.swift`) with the "who?" prompts (`People/PeoplePrompts.swift`). Every picker sorts A–Z, selects by colour alone, and shows 96 pt tiles.

## Summary

One chooser, three hosts. A `SoulChooser` shows two rows of the user's most-tagged people as tiles, then everyone A to Z as rows with glyph, name, pebble count and a plus or a tick. A glass lens and plus sit at the bottom in thumb reach; the lens opens a field anchored on the keyboard, and a name nobody has becomes an Add row. Chosen people ride as pills beside Done. The picker sheet from the Life screen and the half sheet from the edit pebble form host it with selection on; the People tab hosts it with selection off, where tiles and rows open a person. Android mirrors the tiles and rows in M3 clothes with a top-right search and an add FAB; web stays list-only until drawn.

## Goal

- Tagging the usual people costs one tap each, with no scrolling, on the Life screen and in the picker.
- Finding anyone else costs a lens tap and a few letters; adding someone new costs the same letters plus one tap.
- The selection is always visible, as pills, however far the list is scrolled.
- The People tab reads as the same screen as the picker, so one layout is learned.
- No back-end change: ranking, grouping and search are client-side on the counts already fetched.

## Non-goals

- Recency ("the people from your last pebble", "last pebble" under a name): `pebble_souls` has no timestamp, so both need an RPC. Count alone won on 2026-11-04 (screen 08 not chosen).
- "Same as last pebble" card: rejected as too powerful.
- A soul ↔ connection link, a person page with relationship content (screens opus-16, sonnet-28): deferred.
- The glyph in the inline create: "A drawing can wait". The soul page keeps the glyph picker.
- Fixing the `remove` icon in `packages/icons` (it draws a plus): tracked separately; the pill close uses `plus` rotated 45°, as the mock does.

## Decisions

**S1. Ranking and grouping.** Usual = the souls with at least one pebble, ordered by count descending then name ascending, capped at eight, laid out four across. Fewer than four qualify: one row. None qualify: no Usual section, the list starts at the top. The A–Z list holds every soul, grouped under the diacritic-folded, upper-cased first letter of the name; names that do not start with a letter group under "#" at the end. Duplicate names are distinct rows told apart by their count. The count is the number of pebbles the soul is already on, read from the `pebbles_count:pebble_souls(count)` join the souls fetch already carries; it does not include the pebble being tagged.

**S2. Selection.** Tiles and rows toggle one set. A chosen tile takes the tonal fill (`primary` at 20 %) and a tick badge at its top right; a chosen row swaps its plus for a tick and its name goes semibold. Pills: one line beside the button, scrolling sideways with a right-edge fade, each pill shows the glyph, the name and a close that removes it. The button reads "Done · n"; with nothing chosen it reads "Skip" in the record flow and "Done" in the edit sheet. Done confirms; dragging the sheet down keeps the previous selection, as the current sheet does.

**S3. Search.** The lens orb opens a field in its place, anchored above the keyboard, with the close inside the field; there is no Cancel at the top. Matching is case- and diacritic-insensitive (`folding` with `.diacriticInsensitive, .caseInsensitive`) and matches anywhere in the name. The results replace the A–Z list; the Usual tiles stay below them (screen 04). When no soul's folded name equals the folded query, the first result slot is an Add row, "Add <query>" with the caption "A drawing can wait", and the plus orb lights up because it now does the same thing. The plus with no query opens the field focused. Closing the field clears the query.

**S4. Inline create.** The Add row and the lit plus insert one `souls` row with the trimmed query as the name and `SystemGlyph.default` as the glyph, the payload `CreateSoulSheet` sends, through the single-table insert that already exists, then `ReferenceDataService.refreshSouls()`. In a picker the new soul arrives chosen and the field closes. On the People tab it pushes the new soul's page so the drawing can be picked. A failed insert keeps the query and shows the error line in orange under the field.

**S5. Hosts on iOS.**
- *Picker sheet* (screens 01–05): a `.large` sheet opened by the Life screen's More tile. Title "Who was there?". Selection seeds from the draft; Done writes back and dismisses.
- *Half sheet* (screen 06): the edit pebble form's `SelectedSoulsRow` opens the same chooser at `.medium` with `.large` available; the title is the pebble's name. At medium the tiles answer the usual case and the list peeks under them.
- *People tab* (screens 07, 09): the chooser with selection off; tiles and rows push `PeopleRoute.soul(id)`. Circle keeps its toolbar slot; the toolbar's add button goes, the plus orb replaces it. The "who?" prompts keep their place above Usual while the list is short, and a prompt's "who?" opens the half sheet on that pebble. The toolbar title stays "People".
- *Life screen* (record-flow D10): the four most-tagged souls as tiles plus a More tile, sticky above the button. Tiles toggle in place; More opens the picker sheet. With no souls at all the strip is a single "Add people" tile that opens the picker, where the plus is the only thing to do.

**S6. Chrome.** Lens, plus and field are glass on iOS 26 (`glassEffect`) and material on 17, the `Path/Components/NewPebbleFab.swift` precedent. The pills tray and the button sit above the orbs; both rise with the keyboard. The list's bottom inset clears the tray so the last row is reachable.

**S7. Loading and errors.** The chooser renders from `ReferenceDataService.souls` at once and refreshes the counts on appear, as the souls step does today. A failed refresh with a cached list keeps the list. A failed load with nothing cached shows "Couldn't load souls · Retry" in place of the list.

**S8. Large text.** From the accessibility sizes the tiles go three across and the pills tray wraps to the line above the button; names truncate to one line. The French strings are checked at XXL on an iPhone SE-size device in the device pass.

**S9. Copy, EN and FR.** Who was there?, Usual, Done · n, Skip, Search souls, Add <name>, A drawing can wait, Add people, Couldn't load souls, Retry, Remove <name>. FR with "tu". Existing "Choose souls" and "All my souls" go.

**S10. Android (mirror).** Screens 10, 11 and 13: the same tiles and rows in M3 shapes; search in the top app bar, add as a FAB that rides above a bottom bar holding the chips and Done; the search bar opens at the top, as Android expects. The chooser goes in `core/` (Konsist boundary) because the People tab and the picker live in different feature packages. The People tab keeps its prompts above Usual. Screenshot previews for the picker, the search, the empty state and the People tab, FR and large font variants.

**S11. Web.** Undrawn. The web picker keeps its search-or-create list; the souls page keeps its cards. A web issue carries the design to pick up when the web parity milestone reaches it.

## Screens

| # | Screen | Host | Selection | Bottom | Button |
|---|---|---|---|---|---|
| 01 | Picker | Life screen More | on, 2 chosen | lens + plus | Done · 2 |
| 02 | Picker, nobody chosen | Life screen More | on, empty | lens + plus | Skip |
| 03 | Search on the keyboard | any picker | on | field + plus | Done · n |
| 04 | Nobody by that name | any picker | on | field + lit plus | Done · n, Add row first |
| 05 | Seven chosen | any picker | on | lens + plus | Done · 7, pills fade |
| 06 | Half sheet | edit pebble | on | lens + plus | Done |
| 07 | People tab | tab root | off | lens + plus | none |

## Contract

Unchanged. `souls` select with `glyphs(...)` and `pebbles_count:pebble_souls(count)`; one-row `souls` insert with `name` and `glyph_id`; `complete_pebble(soul_ids[])` and the edit path carry the chosen ids as today.

## Clients

### iOS (reference)
- New `Features/Souls/`: `SoulChooserModel` (ranking, grouping, folded search, Add-row rule, button label; pure, tested), `SoulChooser` (tiles, rows, pills tray, lens, plus, field), `SoulTile`, `SoulRow`, `SoulPillTray`, `SoulChooserSheet` (the two picker hosts by detent), with previews.
- `Profile/Lists/SoulsListView.swift` hosts the chooser with selection off and keeps the prompts; `SoulPickerSheet`, `SoulPickerContent` and the `.create` case of `Shared/SoulItem.swift` are deleted once no host remains.
- `Path/SelectedSoulsRow.swift` opens `SoulChooserSheet` at medium.
- The Life screen (record-flow D10) renders the Usual strip from the same model's ranking.
- Tests: Swift Testing on the model (count ties, folded search with accents, Add row only without an exact folded match, "#" grouping, Skip vs Done label, cap of eight, one row under four); reference renders for screens 01, 02, 04, 06 and 07 in both schemes.

### Android (mirror)
- `core/ui/SoulChooser*` with a `SoulChooserState` mirroring the iOS model and its unit tests; `features/profile/SoulsListScreen` and the record flow's Life screen host it; the existing picker sheet is deleted.

### Web
- Issue only, see S11.

## Plan

iOS as one stack with the record flow, souls parts first because the Life screen hosts the picker:

1. `SoulChooserModel` + `SoulChooser` with previews and tests.
2. People tab on the chooser, prompts kept, old grid deleted from the tab.
3. Picker hosts: the edit pebble half sheet and the current souls step replace `SoulPickerSheet`; the old sheet and grid are deleted.
4. Capture draft persistence + moment page (record flow 1).
5. Photo step (record flow 2).
6. Valence labels, ghost stone, help sheet, callout, no seed, Neutral (record flow 3).
7. Life screen: domains + Usual strip + picker, summary pill (record flow 4).
8. Make it richer, entry points, bare moment page (record flow 5).
9. Copy pass EN/FR and reference renders, flow and souls screens (record flow 6).

Each part lints and tests alone. The device pass at XXL Dynamic Type in French on an iPhone SE-size device gates parts 3 and 7. Android as one stack mirroring parts 1–3 then the record flow parts; web as its issue.
