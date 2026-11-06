# Android record flow and souls chooser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan part by part. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship, on Android, the souls chooser (picker sheet, edit half sheet, People tab) and the redesigned record flow (photo → moment page → valence → emotion → Life → Make it richer) as one stack of nine parts, souls first, mirroring what iOS shipped on `main` at `1faf15cdc`.

**Architecture:** One `@HiltViewModel` per flow with its pure state machine beside it (`CaptureFlowModel`, `CompleteFlowModel`), so every tap buzzes. The chooser lives in `core/ui/souls/` because People (`features/profile`) and the record flow (`features/path`) host it and a feature may not import a feature. No schema or RPC change: `save_moment` and `complete_pebble` are called as iOS calls them.

**Tech Stack:** Kotlin, Compose, stock Material 3 Expressive (`material3 1.5.0-alpha27`), Hilt, Navigation 3, JUnit + `runTest`, Robolectric whole-app tests, Compose Preview Screenshot Testing.

**Specs:** `docs/superpowers/specs/2026-11-05-souls-picker-and-people-design.md` (S1–S11), `docs/superpowers/specs/2026-11-04-record-flow-redesign-design.md` (D1–D16). Issues #1519 (souls) and #1515 (record flow), milestone M70 · Record flow.

---

## How to read this plan

Pure logic is given as code and tests. **UI is not re-described in prose**: each screen names its mock, its iOS file and its Android components. Plan text that restated the design is exactly what drifted on the Android onboarding (#1510): "the implementation is contradicting the current implemented components or not complying with the approved designs". So:

- **Open the mock and the iOS reference render before writing a screen, and look at your own render next to them before reporting done.** The controller passes their absolute paths in your brief.
- **Read the named iOS file for what it does** (states, edge cases, validation, copy, server calls) and build it the Android way (`apps/android/CLAUDE.md`, "What mirror iOS means here"). The iOS commit of each part is named so you can read its whole diff with `git show <sha> -- apps/ios/Pebbles`.
- **Before drawing a control, find what the app already uses for it** and use that (`core/designsystem/ListGroup.kt`, `PebblesPrimaryButton`, `PebblesScreen`, `PebblesTopBar`, `PbblsIconImage`, `SegmentedListItem`). Chrome is stock Material 3 (#854). Never mimic iOS chrome on Android.

### Sources of truth, in order

1. The two specs' amendments and this plan's table below.
2. iOS on `main` and its reference renders under `apps/ios/PebblesTests/References/__References__/{RecordFlowReferences,SoulChooserReferences}/`. They passed the first device pass, so they beat the older mocks.
3. The mocks: gallery https://claude.ai/artifact/1jXYWFkevwjcyhLj48wVJS (moment page = Opus v3 Ledger, Life = Opus v1 Margin, richer = Opus v2 Riverbed), souls canvas https://claude.ai/artifact/JYd7fitbmeoZRvtULQdh7o page Converged, **screens 10, 11 and 13 are the approved Android screens and beat iOS for the chooser's layout**.
4. `apps/android/CLAUDE.md` for how it is built.

### Where the sources disagree, and what Android does

| Topic | Mock or spec says | iOS shipped | Android does |
|---|---|---|---|
| Summary pill | in the mocks | gone (D11 amended) | none |
| Life button | "Make it a pebble (n)" | no count, it read as n pebbles | no count |
| Souls on Life | five circles, six in the mock | four tiles + More in a tinted bar | four tiles + More in a tinted bar |
| Valence control | 3×3 matrix in the mock | fan + roll (maintainer's pick) | fan + roll |
| Emotion | shortlist in the mock | the 38-chip picker + Retry | the 38-chip picker + Retry |
| Words limit | 0 / 500 in the mock | 280, counter from 40 left | 280, counter from 40 left |
| End-screen week | stones in the mock | dots, larger on the day | dots, larger on the day |
| Just-save snackbar (D6) | "Moment saved · Make it a pebble" | not built, plain return | not built; stays one finding for both surfaces, do not add it on Android alone |
| "You started this · continue" (D14) | one line | kicker "You started this" + button "Continue" | as iOS |
| Emotion 0.3 s beat (D9) | "after a visible 0.3 s selection" | advances at once | advances at once |
| Search results (S3) | Usual tiles stay below on iOS screen 04 | tiles below | **mock 11: results only, no tiles, matched letters highlighted** |
| People prompts (S10) | "keeps its prompts" | has them | Android never had them and mock 13 has none: nothing to keep |
| Step dots | dots in every mock | dots | `LinearWavyProgressIndicator` in `RecordFlowChrome` (#854), unchanged |
| Photo grid (D3) | inline | inline `PhotosPicker` | modal `PickVisualMedia` + a Library tile (recorded exception) |

**Tap counts on Android**, because of the modal: a photo moment is + · Library · pick · Just save = **4**, a words-only moment + · Just write · Just save = **3**, a full pebble with a photo **10** with the fan's Continue. Report them honestly; do not reach for an auto-opening picker, which would cost Just write users a dismiss and which the spec does not ask for.

---

## Working rules for every part

- **Worktree:** `/Users/alexis/code/pbbls/.worktrees/record-flow-android`. One implementer at a time. The controller creates the next branch with `gh stack add <branch>` before dispatching; stay on it. Never push, never open a PR, never touch `main`: the controller ships the stack once the device pass is done.
- **Branches** (the `-android` suffix keeps Vercel off them): `feat/1519-souls-chooser-android` → `feat/1519-people-tab-android` → `feat/1519-picker-hosts-android` → `feat/1515-moment-page-android` → `feat/1515-photo-step-android` → `feat/1515-valence-android` → `feat/1515-life-screen-android` → `feat/1515-make-it-richer-android` → `feat/1515-copy-renders-android`.
- **Gate, from `apps/android`, on the whole part before you report:**
  ```bash
  export ANDROID_HOME=$HOME/Library/Android/sdk
  ./gradlew ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
  ```
  Without `ANDROID_HOME` the npm scripts silently no-op. `./gradlew :app:testDebugUnitTest --tests '*SoulChooserStateTest*'` while iterating. `ArchitectureBoundaryTest` (Konsist) and `ThemeLiteralsTest` run in that suite: fix the code, never the frozen lists.
- **Unused resources fail lint.** Deleting the last use of a string or drawable means deleting it in the same commit, from both `values/` and `values-fr/`. `LocalizationParityTest` asserts the two files have the same keys.
- **Copy is verbatim.** Every EN and FR value in the table below comes from iOS's `Localizable.xcstrings`. Do not paraphrase, do not re-translate. XML escapes `'` as `\'`; keep `’` where iOS has it. Formatting-sensitive: insert strings as text at a sensible anchor, never rewrite the whole file.
- **Looking at a render:** `./gradlew :app:validateDebugScreenshotTest` leaves PNGs under `app/build/outputs/screenshotTest-results/preview/debug/rendered/`. Failures there are expected (the gate is CI-only: dozens differ locally even on `main`). **Never run `updateDebugScreenshotTest` for a commit and never commit a PNG**: CI's bot re-baselines the tip. Add `@PreviewTest` functions; leave the references alone.
- **A mapped preview keeps its name:** `Capture_Photo` and `Complete_Valence` are mapped in `apps/android/arkaik-shots.json`. Keep those two function names on the same class and do not give them `name =`; check with `node scripts/arkaik/android-shots-manifest.mjs --check` (it needs a PNG, so it goes green only on the re-baselined tip).
- **Icons:** `PbblsIconImage(icon = PbblsIcon.X, size = PbblsIconSize.Y, contentDescription, tint)`. The set has no lens and no info mark. Chrome tracings are a closed list (decision log, icon migration): Part 1 adds `ic_search` and Part 6 adds `ic_info`, each with a figure line in `packages/icons/waves/2026-11-chrome/brief.md`. The `remove` icon draws a plus (known bug): pills close with `ic_x_circle`. `ic_chevron_right` and `ic_arrow_back` stay the auto-mirrored tracings.
- **No literal colours, corners or font sizes** in `features/` or `core/ui` (`ThemeLiteralsTest`): roles from `MaterialTheme`, `PebblesTheme.hand` for Caveat, `Spacing.*`. For a fade use `colors.surface.copy(alpha = 0f)`, never `Color.Transparent`-looking literals the test rejects; run it.
- **Commits:** conventional, lowercase, no period, `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` as the last line. One logical change per commit. Separate a move from a rewrite.
- **Do not refactor beyond the part.** If you see something to improve, say so in your report.

### Lessons carried over, binding on every part

From the iOS stack (the plan's "Lessons learned", two-stage review, first device pass):

- **Grep before you assert a capability exists elsewhere.** iOS's People tab lost soul deletion because a brief said the soul page had it. Android's delete is the long press on the list: it stays.
- **Never nest two vertical scrolls.** A `LazyColumn` inside `verticalScroll` throws at measure time. `RecordStepScaffold(contentScrolls = false)` for any step that scrolls itself.
- **Discard must not resurrect a draft.** Gate autosave on an explicit lifecycle state, not on "has restored".
- **A restored photo is a pending upload**, so Discard, Remove and a new pick delete its Storage files.
- **The photo step advances on a new snap id only.** An upload state change must not bounce someone who came back through Change photo.
- **A draft restore never waits on the network.**
- **Sheets are verified by a render.** iOS's help sheet overflowed its detent: the plan's scale was arithmetic.
- **No fixed heights under text.** `heightIn(min = …)`, not `height(…)`. Two-column grids go one column from `LocalDensity.current.fontScale >= 1.5f`; tiles go three across and the tray wraps at the same threshold (S8).
- **Never a hard clip at a scroll edge.** Content dissolves under the title and over the button. On Android draw it as an overlay gradient from `surface` to `surface.copy(alpha = 0f)` with matching `contentPadding`, never a `DstIn` mask: HWUI ignored one on this Pixel 8 while Robolectric honoured it (`apps/android/CLAUDE.md`, Path rendering).
- **Check a fixture's weekday with `date`**: the iOS plan labelled a Friday a Wednesday. 2026-10-14 is a Wednesday.

From the Android onboarding drift (#1510):

- **Read before the session is live returns `200 []` under RLS.** Empty data is not proof of emptiness. Anything that branches on "nothing there" (first pebble, "Add people") checks `supabase.session != null` and a settled load (`refs.hasLoaded`), and a failed read leaves the quiet branch.
- **Check every control against the component the app already uses.** Android's Settings idiom is `ListGroup` + `SegmentedListItem` + native `Switch`. `connected*ButtonShapes` are horizontal.
- **Copy, rows and shapes drifted while the logic passed.** The reviewer's first job is the side-by-side, not the diff.

---

## Copy (from iOS, verbatim)

Reuse an existing Android key when its EN and FR already equal these; otherwise add the key named here. Part numbers say who adds it.

| Part | Key | EN | FR |
|---|---|---|---|
| 1 | `souls_search` | Search souls | Chercher une âme |
| 1 | `souls_add_named` | Add %1$s | Ajouter %1$s |
| 1 | `souls_add_caption` | A drawing can wait | Le dessin peut attendre |
| 1 | `souls_done_count` | Done · %1$d | OK · %1$d |
| 1 | `souls_done` | Done | Terminé |
| 1 | `souls_remove_named` | Remove %1$s | Retirer %1$s |
| 1 | `souls_usual` | Usual | Habituels |
| 1 | `souls_who_was_there` | Who was there? | Qui était là ? |
| 1 | `souls_more` | More | Plus |
| 1 | `souls_add_people` | Add people | Ajouter des gens |
| 2 | (reuse `tab_people`) | People | Proches |
| 4 | `capture_moment_title` | Your moment | Ton moment |
| 4 | `capture_field_name` | NAME | NOM |
| 4 | `capture_field_when` | WHEN | QUAND |
| 4 | `capture_field_words` | A FEW WORDS | QUELQUES MOTS |
| 4 | `capture_words_placeholder` | What happened? | Que s’est-il passé ? |
| 4 | `capture_words_karma` | +1 karma as a pebble | +1 karma en galet |
| 4 | `capture_change_photo` | Change photo | Changer la photo |
| 4 | `capture_remove_photo_a11y` | Remove photo | Retirer la photo |
| 4 | `capture_photo_uploading` | Uploading… | Envoi en cours… |
| 4 | `capture_photo_ready` | Ready | Prête |
| 4 | `capture_photo_failed` | Upload failed | Échec de l\'envoi |
| 4 | `capture_just_save` | Just save | Juste enregistrer |
| 4 | `capture_save_and_pebble` | Save & make it a pebble (`&amp;` in XML) | Enregistrer et en faire un galet |
| 4 | `capture_when_sheet_title` | When did it happen? | C\'était quand ? |
| 4 | `capture_when_today` | Today | Aujourd’hui |
| 4 | `capture_when_yesterday` | Yesterday | Hier |
| 4 | `capture_when_from_photo` | From your photo · %1$s | D’après ta photo · %1$s |
| 5 | `record_photo_subtitle` (change) | Pick one, or write it from memory. | Choisis-en une, ou écris de mémoire. |
| 5 | `capture_just_write` | Just write | Juste écrire |
| 5 | `capture_take_photo_a11y` | Take a photo | Prendre une photo |
| 5 | `capture_library` | Library | Galerie |
| 6 | `complete_after_save` | Moment saved · 3 questions to make it a pebble | Moment enregistré · 3 questions pour en faire un galet |
| 6 | `valence_ghost_prompt` | Swipe or tap to choose | Glisse ou touche pour choisir |
| 6 | `valence_help_link` | How to read this | Comment lire ceci |
| 6 | `valence_help_line_1` | Two questions, one stone. | Deux questions, un galet. |
| 6 | `valence_help_line_2` | Left to right: lowlight, neutral, highlight. | De gauche à droite : creux, neutre, temps fort. |
| 6 | `valence_help_line_3` | Bottom to top: did it fill your day, your week, your month? Bigger moment, bigger stone. | De bas en haut : a-t-il rempli ta journée, ta semaine, ton mois ? Plus grand le moment, plus gros le galet. |
| 6 | `valence_help_line_4` | No wrong stone. You can change it later. | Pas de mauvais galet. Tu pourras le changer. |
| 6 | `valence_help_got_it` | Got it | Compris |
| 6 | `valence_axis_{lowlight,neutral,highlight}` | Lowlight · Neutral · Highlight | Creux · Neutre · Temps fort |
| 6 | `valence_axis_{month,week,day}` | Month · Week · Day | Mois · Semaine · Jour |
| 6 | `valence_headline_neutral` (change) | Neutral | Neutre |
| 6 | `emotion_load_error` | Couldn\'t load feelings | Impossible de charger les émotions |
| 7 | `record_life_title` | What part of life? | Quel domaine de ta vie ? |
| 7 | `record_life_recent` | RECENT | RÉCENTS |
| 7 | `record_life_all` | ALL | TOUS |
| 7 | `complete_partial_failure` | Moment saved, pebble not made yet · Try again | Moment enregistré, pas encore de galet · Réessayer |
| 8 | `richer_section` | Make it richer | Enrichis-le |
| 8 | `richer_add_collection` | Add to a collection | Ajouter à une collection |
| 8 | `richer_remove_collection` | Remove from collection | Retirer de la collection |
| 8 | `richer_who_can_see` | Who can see it | Qui peut le voir |
| 8 | `richer_add_words` | Add a few words | Ajouter quelques mots |
| 8 | `richer_words_karma` | +1 karma | +1 karma |
| 8 | `richer_done` | Done | Terminé |
| 8 | `richer_record_another` | Record another | En enregistrer un autre |
| 8 | `bare_place_started` | You started this | Tu avais commencé |
| 8 | `home_toast_name` (change) | Edit | Modifier |
| 9 | `domain_*` labels | the copy pass of `git show 1faf15cdc -- apps/ios/Pebbles/Resources/Localizable.xcstrings` | same diff |

`capture_library` has no iOS source (iOS draws the grid); "Galerie" is the word Android users know.

---

# Part 1 — the chooser (branch `feat/1519-souls-chooser-android`)

iOS reference: `8b5daf223`, `apps/ios/Pebbles/Features/Souls/`. Mocks: screens 10, 11. Part of #1519.

**Files:**
- Create: `core/ui/souls/SoulRanking.kt`, `SoulChooserState.kt`, `SoulTile.kt`, `SoulRow.kt`, `SoulChooserTray.kt`, `SoulChooserSearchField.kt`, `SoulChooserBody.kt` (paths under `apps/android/app/src/main/kotlin/app/pbbls/android/`)
- Create: `res/drawable/ic_search.xml`; Modify: `packages/icons/waves/2026-11-chrome/brief.md`, `res/values/strings.xml`, `res/values-fr/strings.xml`
- Test: `app/src/test/kotlin/app/pbbls/android/core/ui/souls/SoulChooserStateTest.kt`
- Preview: `app/src/screenshotTest/kotlin/app/pbbls/android/SoulChooserScreenshots.kt`

### Task 1.1: Ranking, grouping, search (S1, S3)

- [ ] **Write the failing test.** Port every case of `apps/ios/PebblesTests/Features/Souls/SoulChooserModelTests.swift` with the same names and the same expected lists: they are foreign vectors, so a disagreement is a bug in the port. At least:

```kotlin
private fun soul(name: String, count: Int) =
    SoulWithGlyph(id = "id-$name-$count", name = name, glyphId = "g", glyph = glyph, pebblesCount = count)

@Test fun `usual is count desc then name asc, capped at eight, zero counts excluded`() {
    val souls = listOf(
        soul("Zoé", 3), soul("Adèle", 3), soul("Ben", 0), soul("Camille", 29), soul("Alex", 24),
        soul("Léa", 5), soul("Papa", 5), soul("Inès", 4), soul("Sam", 4), soul("Nour", 2), soul("Mamie Jo", 2),
    )
    assertEquals(listOf("Camille", "Alex", "Léa", "Papa", "Inès", "Sam", "Adèle", "Zoé"), SoulRanking.usual(souls).map { it.name })
}

@Test fun `folded search matches anywhere, ignoring case and accents`() { /* "le" finds Léa, Adèle; "ADE" finds Adèle */ }
@Test fun `add row only without an exact folded match`() { /* "lea" with Léa present: false; "Le" : true */ }
@Test fun `names that do not start with a letter group under hash, last`() { /* "2 Pac", "Éa" -> sections E, # */ }
@Test fun `match range points at the original characters`() { assertEquals(0..1, SoulRanking.matchRange("Maman", "ma")); assertEquals(1..2, SoulRanking.matchRange("Léa", "ea")) }
```

- [ ] Run: `./gradlew :app:testDebugUnitTest --tests '*SoulChooserStateTest*'`. Expected: FAIL, `SoulRanking` unresolved.

- [ ] **Implement `SoulRanking`:**

```kotlin
package app.pbbls.android.core.ui.souls

import app.pbbls.android.core.model.SoulWithGlyph
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** One letter of the A–Z list. */
data class SoulSection(
    val letter: String,
    val souls: List<SoulWithGlyph>,
)

/**
 * Ranking, grouping and folded search over the souls already fetched (souls spec S1, S3). Pure:
 * no back-end, no state. Mirrors the statics of iOS `SoulChooserModel`; the Life screen's strip
 * reads [usual] too, so both show the same people.
 */
object SoulRanking {
    /** Two rows of four. */
    const val USUAL_CAP = 8

    /** How many of [usual] the Life screen's strip shows (record-flow D10). */
    const val STRIP_CAP = 4

    private const val OTHER = "#"
    private val combiningMarks = Regex("\\p{Mn}+")

    private fun collator(): Collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }

    fun folded(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(combiningMarks, "").lowercase(Locale.ROOT)

    fun usual(souls: List<SoulWithGlyph>): List<SoulWithGlyph> {
        val names = collator()
        return souls
            .filter { it.pebblesCount > 0 }
            .sortedWith(compareByDescending<SoulWithGlyph> { it.pebblesCount }.thenComparator { a, b -> names.compare(a.name, b.name) })
            .take(USUAL_CAP)
    }

    fun letter(name: String): String {
        val first = folded(name.trim()).firstOrNull()
        return if (first != null && first.isLetter()) first.uppercaseChar().toString() else OTHER
    }

    fun sections(souls: List<SoulWithGlyph>): List<SoulSection> {
        val names = collator()
        val grouped = souls.sortedWith { a, b -> names.compare(a.name, b.name) }.groupBy { letter(it.name) }
        val letters = grouped.keys.filter { it != OTHER }.sortedWith { a, b -> names.compare(a, b) }
        return (if (OTHER in grouped) letters + OTHER else letters).map { SoulSection(it, grouped.getValue(it)) }
    }

    fun results(
        souls: List<SoulWithGlyph>,
        query: String,
    ): List<SoulWithGlyph> {
        val needle = folded(query.trim())
        if (needle.isEmpty()) return emptyList()
        val names = collator()
        return souls.filter { folded(it.name).contains(needle) }.sortedWith { a, b -> names.compare(a.name, b.name) }
    }

    /** The Add row and the lit plus: a query nobody's folded name equals. */
    fun canAdd(
        souls: List<SoulWithGlyph>,
        query: String,
    ): Boolean {
        val needle = folded(query.trim())
        return needle.isNotEmpty() && souls.none { folded(it.name) == needle }
    }

    /** Where [query] matched in [name], in [name]'s own indices, for mock 11's highlight. */
    fun matchRange(
        name: String,
        query: String,
    ): IntRange? {
        val needle = folded(query.trim())
        if (needle.isEmpty()) return null
        val flat = StringBuilder()
        val origin = ArrayList<Int>(name.length)
        name.forEachIndexed { index, char ->
            folded(char.toString()).forEach {
                flat.append(it)
                origin.add(index)
            }
        }
        val start = flat.indexOf(needle)
        return if (start < 0) null else origin[start]..origin[start + needle.length - 1]
    }
}
```

- [ ] Run the test: PASS. Commit `feat(android): rank, group and search souls for the chooser`.

### Task 1.2: The state (S2)

- [ ] **Failing tests** (same file), porting iOS: selection keeps tap order; `toggle` is inert in browse mode; button label is `Skip` / `DoneEmpty` / `Done(n)` / null in browse; `replaceSouls` drops a gone soul from the selection; `didCreate` arrives chosen in a picker, not in browse, and closes the search.

- [ ] **Implement `SoulChooserState`:**

```kotlin
sealed interface SoulChooserMode {
    /** Tiles and rows toggle; the tray shows pills and a button. [allowsSkip]: "Skip" when empty (record flow), "Done" (edit sheet). */
    data class Pick(val allowsSkip: Boolean) : SoulChooserMode

    /** The People tab: tiles and rows open a person, no selection. */
    data object Browse : SoulChooserMode
}

sealed interface SoulChooserButton {
    data class Done(val count: Int) : SoulChooserButton
    data object DoneEmpty : SoulChooserButton
    data object Skip : SoulChooserButton
}

@Stable
class SoulChooserState(
    val mode: SoulChooserMode,
    souls: List<SoulWithGlyph>,
    selection: List<String> = emptyList(),
) {
    var souls: List<SoulWithGlyph> by mutableStateOf(souls)
        private set

    /** Ordered as chosen, so the pills keep their order. */
    var selection: List<String> by mutableStateOf(selection)
        private set
    var query: String by mutableStateOf("")
    var isSearching: Boolean by mutableStateOf(false)
    /** The failed insert's line under the field (S4). */
    @get:StringRes
    var createErrorRes: Int? by mutableStateOf(null)

    val usual: List<SoulWithGlyph> get() = SoulRanking.usual(souls)
    val sections: List<SoulSection> get() = SoulRanking.sections(souls)
    val results: List<SoulWithGlyph> get() = SoulRanking.results(souls, query)
    val canAdd: Boolean get() = SoulRanking.canAdd(souls, query)
    val trimmedQuery: String get() = query.trim()

    fun isSelected(id: String): Boolean = id in selection

    fun toggle(id: String) {
        if (mode !is SoulChooserMode.Pick) return
        selection = if (id in selection) selection - id else selection + id
    }

    fun remove(id: String) {
        if (mode is SoulChooserMode.Pick) selection = selection - id
    }

    val button: SoulChooserButton?
        get() = when (mode) {
            SoulChooserMode.Browse -> null
            is SoulChooserMode.Pick ->
                when {
                    selection.isNotEmpty() -> SoulChooserButton.Done(selection.size)
                    mode.allowsSkip -> SoulChooserButton.Skip
                    else -> SoulChooserButton.DoneEmpty
                }
        }

    /** A fresh fetch: a chosen soul that is gone costs a pill, not a `soul_not_owned`. */
    fun replaceSouls(fresh: List<SoulWithGlyph>) {
        souls = fresh
        val known = fresh.mapTo(HashSet()) { it.id }
        selection = selection.filter { it in known }
    }

    fun didCreate(soul: SoulWithGlyph) {
        if (souls.none { it.id == soul.id }) souls = souls + soul
        if (mode is SoulChooserMode.Pick && soul.id !in selection) selection = selection + soul.id
        closeSearch()
    }

    fun openSearch() { isSearching = true }

    /** Closing the field clears the query (S3). */
    fun closeSearch() {
        query = ""
        isSearching = false
        createErrorRes = null
    }
}
```
Add `rememberSoulChooserState(mode, souls, selection)` with `rememberSaveable` for `selection`, `query` and `isSearching` (a rotation keeps them) and a `LaunchedEffect(souls) { state.replaceSouls(souls) }`.

- [ ] Run: PASS. Commit `feat(android): the chooser's state, selection and button label`.

### Task 1.3: Tiles, rows, tray, search field, body

Look at `final-10-android-picker.png` and `final-11-android-search.png` first; `SoulTile.swift`, `SoulRow.swift`, `SoulPillTray.swift`, `SoulChooser.swift` for behaviour and accessibility.

- [ ] `SoulTile(soul, isSelected, showsSelection, onClick, onLongClick?)`: glyph through the existing `GlyphImage` at 44 dp over the name in one truncated line; `heightIn(min = 88.dp)`; `MaterialTheme.shapes.large`; chosen = `colors.primary.copy(alpha = 0.20f)` fill, tick badge top right (`PbblsIcon.CHECKMARK_DOT` on a `primary` circle), name bold, `selected` semantics.
- [ ] `SoulRow(soul, isSelected, showsSelection, highlight: IntRange?, onClick, onLongClick?)`: stock M3 `ListItem` (transparent container) with the glyph leading, the name in regular type (bold when chosen), and trailing count + `PLUS_CIRCLE` / `CHECK_CIRCLE` in `primary`; browse shows the count alone (mock 13). The count carries the small pebble mark the mock draws (`PbblsIcon.PEBBLE_*`: look at `PbblsIcons.kt`). `highlight` paints a `primaryContainer` `SpanStyle(background = …)`. A `HorizontalDivider(color = outlineVariant)` between rows.
- [ ] Add row: first result slot, "Add <query>" + caption "A drawing can wait", when `state.canAdd`.
- [ ] `SoulChooserTray(state, onDone)`: a `surfaceContainer` bar: M3 `InputChip`s in a horizontally scrolling row with a right-edge fade (glyph avatar, name, `ic_x_circle` trailing with `souls_remove_named` as its label) in `primaryContainer`, then a filled `Button` reading `souls_done_count` / `record_action_skip` / `souls_done`. At `fontScale >= 1.5f` the chips wrap on the line above the button (S8).
- [ ] `SoulChooserSearchField(state, focusRequester, onClose)`: the stock `SearchBar` with `SearchBarDefaults.InputField` (`expanded = false`), leading `ic_arrow_back` (closes), trailing `ic_x_circle` (clears), placeholder `souls_search`, as mock 11, requesting focus when it opens. Add `ic_search.xml`, traced as the other chrome icons at 24 dp, and figure 21 "search, a lens" in the wave brief. Failed create: `createErrorRes` in `colors.error` under the field, query kept (S4).
- [ ] `SoulChooserBody(state, isLoaded, loadFailed, onRetry, onOpenSoul, onCreate, modifier)`: a `Box` holding a `LazyColumn` and the FAB. Searching: Add row + results only (mock 11). Not searching: Usual tiles chunked in rows of four (three at large font; none when `usual` is empty), then letter headers + rows. `contentPadding(bottom)` clears the FAB. Stock `FloatingActionButton` with `PLUS_CIRCLE` at `BottomEnd`: no query → opens the field focused; `canAdd` → creates, and turns `primary` to say so. Pick mode toggles on tap; browse calls `onOpenSoul`. `loadFailed` with nothing cached: "Couldn't load souls" (`souls_load_error`) + `profile_retry`. `isLoaded` guards every "nobody" branch.
- [ ] Previews in `SoulChooserScreenshots.kt`: `SoulChooser_Picker` (eleven souls, two chosen), `SoulChooser_PickerEmpty`, `SoulChooser_Search`, `SoulChooser_NoResult`, `SoulChooser_Browse`, each Light; stack `@PreviewFrench` on the first; `@PreviewLargeFont` on the first; a dark render of the first. Render them, compare with the mocks and fix what differs.
- [ ] Gate. Commit `feat(android): the souls chooser's tiles, rows, tray and search`.

---

# Part 2 — People on the chooser (branch `feat/1519-people-tab-android`)

iOS reference: `0511a3f46`, `Features/Profile/Lists/SoulsListView.swift`. Mock: screen 13. Part of #1519.

**Files:** Modify `features/profile/SoulsListScreen.kt`, `SoulsListViewModel.kt`, `navigation/PebblesEntryProvider.kt`, screenshots that render `SoulsListContent` (`grep -rl SoulsListContent app/src/screenshotTest`). Test `SoulsListViewModelTest` if the load changes.

- [ ] `SoulsListContent` hosts `SoulChooserBody` in `SoulChooserMode.Browse` under `PebblesScreen`. Top bar as mock 13: `PebblesTopBar` titled `tab_people`, trailing lens + the existing Circle button; while searching the bar is `SoulChooserSearchField`. No back arrow on the tab root unless the current `onBack` wiring says one is needed on a pane: read the entry before removing it.
- [ ] **Keep, and test by reading the code after:** the long press → delete dialog (`requestDelete`, `ConfirmDeleteDialog`, `DeleteErrorDialog`) on tiles and rows; `createRequests` (Home's first five), which now opens the field focused; `LifecycleResumeEffect` refresh; `ProfileEmptyState` when the list is truly empty; the `Error` branch with Retry; `onDeleteConfirmed` for the detail pane; `readableWidth`.
- [ ] Inline create on People (S4): the new soul is inserted with the default glyph (`ReferenceDataServicing.createSoul`), the field closes and `onOpenSoul(new)` opens its page so the drawing can be picked. Refresh the list. Log failures, `createErrorRes` for the line.
- [ ] FAB rides above the navigation bar (`PebblesScreen` already pads `safeDrawing`): check it on the render against mock 13.
- [ ] Update previews. Gate. Commit `feat(android): people on the souls chooser`.

---

# Part 3 — picker hosts (branch `feat/1519-picker-hosts-android`)

iOS reference: `29dd5bedf`, `Features/Souls/SoulChooserSheet.swift`, `Path/SelectedSoulsRow.swift`. **Resolves #1519.**

**Files:** Create `core/ui/souls/SoulChooserSheet.kt`. Modify `features/path/create/PebbleForm.kt` (line ~201), `features/path/flow/steps/RecordSoulsStep.kt`. Delete `features/path/create/pickers/SoulPickerSheet.kt` and whatever becomes unused (`SoulItem`? grep first), with their strings.

- [ ] `SoulChooserSheet(title, currentSelection, allowsSkip, startsPartial, onDismiss, onConfirm, onAchievementCheck = {})`: stock `ModalBottomSheet`. A pushed entry cannot hand a result back (decision log 2026-11-05), so a sheet it is. `startsPartial = false` from Life (`skipPartiallyExpanded = true`, `dragHandle = null`, an inner top bar with `ic_arrow_back`, the title and the lens, so it reads as mock 10), `true` from the edit form (half sheet, screen 06; title = the pebble's name). Reads `LocalReferenceDataService`, calls `refs.refreshSouls()` once on open (#1370, guarded by `SoulCountsRefreshTest`), `refs.createSoul` for the Add row then `onAchievementCheck()`. Done calls `onConfirm(state.selection)`; drag-down, scrim and back keep the previous selection (S2). The tray and the FAB rise with the keyboard: `imePadding()` inside the sheet, and check it on the device (the `edge-to-edge` skill covers sheet insets; `QuickMomentSheet` is the in-app precedent for a field in a sheet).
- [ ] `PebbleForm`: its souls row opens `SoulChooserSheet(startsPartial = true, allowsSkip = false)`.
- [ ] `RecordSoulsStep` (interim until Part 7, as iOS's): hosts `SoulChooserBody` inline with the tray's button advancing; the scaffold's Text action goes for this step so there is one button; `bringsOwnScroll` stays true. Do not nest scrolls.
- [ ] Delete `SoulPickerSheet`, `SoulPickerBody`, `CreateSoulDialog`, and the strings they alone used (`create_souls_title`, `create_souls_empty`…): lint tells you. Fix `PickerScreenshots.kt`.
- [ ] Gate. Commit `feat(android): the souls chooser behind the edit sheet and the souls step`.

**Device pass (controller):** People tab, search, add, long-press delete dialog (cancel it), edit sheet half and full, keyboard over the tray, against screens 10, 11, 13.

---

# Part 4 — the moment page (branch `feat/1515-moment-page-android`)

iOS reference: `c99f0bca9`, `Features/Record/Steps/RecordMomentStep.swift`, `RecordWhenChip.swift`, `Capture/CaptureFlowModel.swift`. Render: `momentPage-paper-en.png`; mock `mock-moment.png`. Part of #1515.

**Files:** Modify `features/path/capture/{CaptureStep,CaptureFlowState,CaptureFlowModel,CaptureFlowViewModel,CaptureFlowScreen}.kt`, `core/model/MomentPayloads.kt`, `features/path/flow/RecordStepScaffold.kt`, `navigation/PebblesKeys.kt`, `navigation/PebblesEntryProvider.kt`. Create `features/path/flow/steps/RecordMomentStep.kt`, `RecordWhenChip.kt`. Delete `RecordNameStep.kt`, `RecordWhenStep.kt` once unused (keep `WhenDateTime`, `EditMomentScreen` uses it).

### Task 4.1: The state machine

- [ ] **Failing tests** in `CaptureFlowModelTest`, `CaptureStepTest`, `CaptureFlowViewModelTest`: two steps; description clamps at 280; a user edit of the date beats a later photo date; `whenSource` NOW → PHOTO → EDITED; `fail` lands on MOMENT; a words-only draft cannot save but does ask before closing.

- [ ] **Implement:**

```kotlin
enum class CaptureStep(@StringRes val titleRes: Int, @StringRes val subtitleRes: Int? = null) {
    PHOTO(R.string.record_photo_title, R.string.record_photo_subtitle),
    MOMENT(R.string.capture_moment_title),
    ;
    val next: CaptureStep? get() = entries.getOrNull(ordinal + 1)
    val previous: CaptureStep? get() = if (ordinal == 0) null else entries[ordinal - 1]
}

enum class WhenSource { NOW, PHOTO, EDITED }

data class CaptureDraft(
    val name: String = "",
    val description: String = "",
    val happenedAt: OffsetDateTime = OffsetDateTime.now(),
)

data class CaptureFlowState(
    val draft: CaptureDraft = CaptureDraft(),
    val step: CaptureStep = CaptureStep.PHOTO,
    val hasSnap: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /** A user edit always wins over the photo's date (D4). */
    val hasEditedDate: Boolean = false,
    val seededFromPhoto: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    val isFrozen: Boolean get() = isSaving || isSaved
    /** D3: a moment needs a name or a photo. */
    val canSave: Boolean get() = !isFrozen && (draft.name.isNotBlank() || hasSnap)
    /** Words count, unlike on iOS: leaving must never silently lose what was typed. */
    val hasInput: Boolean get() = draft.name.isNotBlank() || draft.description.isNotBlank() || hasSnap
    val whenSource: WhenSource get() = when {
        hasEditedDate -> WhenSource.EDITED
        seededFromPhoto -> WhenSource.PHOTO
        else -> WhenSource.NOW
    }
}
```
`CaptureFlowModel`: `advance()` and `back()` guard `isFrozen` and buzz `ADVANCE`; `DESCRIPTION_LIMIT = 280`; `setDescription`; `setHappenedAt` sets `hasEditedDate`; `applyCaptureDate(date)` no-ops when `hasEditedDate`, else sets the date and `seededFromPhoto = true`; `fail` lands on `CaptureStep.MOMENT`. Remove `isAnswered`, `photoButtonIsSkip`, `isOptional`.

- [ ] **Payload**, with an exact-bytes test in `MomentPayloadEncodingTest` matching iOS `SaveMomentPayload(capture:)` (blank description omitted, trimmed otherwise):

```kotlin
fun capture(name: String, description: String, happenedAt: OffsetDateTime, snap: PebbleSnapPayload?): MomentSavePayload =
    MomentSavePayload(
        name = name.trim().ifEmpty { null },
        description = description.trim().ifEmpty { null },
        happenedAt = happenedAt,
        snaps = snap?.let { listOf(it) },
    )
```

- [ ] Commit `feat(android): capture becomes a photo and a moment page`.

### Task 4.2: Persistence (D2)

`SavedStateHandle` is Android's draft store (spec D2); it covers process death, and the back stack that restores the entry survives with it.

- [ ] `CaptureFlowViewModel` saves and restores `capture-words`, `capture-when-edited`, `capture-when-seeded`, and `capture-snap-id` (only for an `UPLOADED` pending snap, mirroring `CaptureDraftAutosaver.savedSnap`). A saved step name from an older build resolves to `PHOTO` (`firstOrNull`). At init, with no network and no `await`: a saved snap id seeds `snaps.seedExisting(FormSnap.Pending(AttachedSnap(id, ByteArray(0), UPLOADED)))` and the step is `MOMENT`. An empty thumb draws an empty rounded square.
- [ ] Robolectric proof in `src/test/.../ui/`: type on the page, `restartAfterProcessDeath()`, the words are back and Discard deletes the restored snap's files through the coordinator (`FakeSnapWriteRepository`). `ActivityScenario.recreate()` proves nothing about `SavedStateHandle`.
- [ ] Commit `feat(android): the moment page survives process death`.

### Task 4.3: Keys

- [ ] In `PebblesKeys.kt`:

```kotlin
/** Where a Complete flow came from: says "Moment saved" and how Record another restarts (D1, D12). */
@Serializable
enum class CompleteOrigin { CARD, CAPTURE, HOME_NEW_PEBBLE, QUICK_MOMENT }

data class Capture(
    val chainsComplete: Boolean = false,
    /** Quick moment (D13): the page straight away, no photo screen and no way back to it. */
    val startsAtMomentPage: Boolean = false,
    /** The onboarding hand-off's reason, a stable slug: picks the name field's prompt (#1510 D9). */
    val promptReason: String? = null,
) : PebblesKey

data class CompletePebble(
    val momentId: String,
    val origin: CompleteOrigin = CompleteOrigin.CARD,
) : PebblesKey
```
Follow the file's existing annotations. `onSavedThenComplete` navigates `CompletePebble(momentId, origin)` with the origin from the Capture key. Part 8 uses `startsAtMomentPage` and `promptReason`; the VM's `start(...)` takes them now so the model has one entry path.

### Task 4.4: The page

- [ ] `RecordStepScaffold`: gains `fixedSlot: (@Composable () -> Unit)? = null` above the content (Part 5 uses it), the two-ended fade (overlay gradient, `contentPadding`-matched, see the binding lesson), and a `secondaryIsLoading` on `PrimaryWithSecondary`. The title stays where it is. Fix its callers.
- [ ] `RecordMomentStep`: mirror the iOS file field for field: photo row (72 dp thumb with `shapes.medium`, a static 270° ring while uploading, state label, "Change photo" in `primary`, Retry on failure, a 48 dp close with `capture_remove_photo_a11y`; or "Add a photo" with `CAMERA`), NAME (Caveat, `PebblesTheme.hand.nameInputHand`, the clamp writes into local `TextFieldValue` as `RecordNameStep` does, focus only when there is no photo, IME next moves to words), WHEN (the chip), A FEW WORDS (3–6 lines, `+1 karma as a pebble` with `KARMA_DOT`, counter from 40 left, always laid out). Each field: uppercase label in `onSurfaceVariant` above a `surfaceContainerLow` `shapes.medium` block, as the render. `BasicTextField` inside the block, since `RecordNameStep` already establishes that for this flow. Blocked and error lines underneath, the error in `colors.error`.
- [ ] `RecordWhenChip`: an `AssistChip` in `secondaryContainer` with `CALENDAR_DAY` or, from a photo, `SPARKLE`, the label per iOS (`Today`, `Yesterday`, else short weekday + date, then time; `From your photo · …`), a chevron. Opens a `ModalBottomSheet` titled `capture_when_sheet_title` holding M3 `DatePicker` and the time through the existing `TimePickerDialog` flow; reuse `WhenDateTime` so no offset shifts the day. Done writes `setHappenedAt`. Verify the sheet on a render and on the device: the year grid is a lazy list inside a sheet.
- [ ] `CaptureFlowContent`: title per step; primary `capture_save_and_pebble` → `save(thenComplete = true)`, secondary `capture_just_save` → `save(thenComplete = false)`; with `chainsComplete` (Home's New pebble) the secondary is hidden (D5 allows, iOS does). `canGoBack = step.previous != null && !frozen && !startsAtMomentPage`. System back on the first reachable step closes.
- [ ] Until Part 5 the photo step keeps its body and its Skip/Done. Retitle the previews (`Capture_MomentPage…`) and keep `Capture_Photo`. Add `Capture_MomentEmpty`, `Capture_MomentFilled` (+ dark, French, large font), `Capture_MomentUploading`, `Capture_MomentFailed`, `Capture_MomentChainsComplete`.
- [ ] Gate. Commit `feat(android): the moment page with name, when chip and a few words`.

**Device pass (controller):** both buttons' states, keyboard, chip sheet, close dialog, against `mock-moment.png` and `momentPage-paper-en.png`.

---

# Part 5 — the photo step (branch `feat/1515-photo-step-android`)

iOS reference: `96a36bf32`. Mock `mock-photo.png`, spec D3. Part of #1515.

**Files:** Move `features/home/camera/CameraPhotos.kt` to `core/data/CameraPhotos.kt`. Modify `di/ServiceBindings.kt` if it binds it, `features/path/capture/*`, `features/path/flow/steps/RecordPhotoStep.kt`, `apps/android/app/src/test/.../testing/FakeServicesModule` (bind the fake). Read `features/home/camera/CameraMoment.kt` for the shot lifecycle first.

- [ ] **A pure move, its own commit:** `CameraPhotos` + `AndroidCameraPhotos` to `core/data` (a feature may not import a feature). `chore(android): move the camera shot helper to core`. Gate: unit tests only.
- [ ] **Failing tests** in `CaptureFlowViewModelTest`: a new pick advances to MOMENT after the beat (virtual time, `advanceTimeBy(400)`); an upload state change does not advance; coming back through Change photo then a `FAILED → UPLOADED` retry stays put; a new pick from there advances; leaving the step during the beat cancels the advance.

```kotlin
private const val PHOTO_ADVANCE_BEAT_MS = 400L

// in init's snapshotFlow collector
var lastSnapId: String? = (snaps.formSnap as? FormSnap.Pending)?.snap?.id
...
val id = (snap as? FormSnap.Pending)?.snap?.id
if (id != null && id != lastSnapId && model.step == CaptureStep.PHOTO) {
    viewModelScope.launch {
        delay(PHOTO_ADVANCE_BEAT_MS)
        if (model.step == CaptureStep.PHOTO) model.advance()
    }
}
lastSnapId = id
```
Seed `lastSnapId` before the first collection so a restored snap never advances.

- [ ] In-flow camera: `TakePicture` launched from the screen, the shot path held in `SavedStateHandle`, like `CameraMoment`. On return: `model.applyCaptureDate(OffsetDateTime.now())` (the shutter is when, until the user says otherwise), `snaps.attach(photos.process(shot), id)`, `photos.delete(shot)` in a `finally`. Cancelled: delete the shot, stay. `photos.isAvailable()` false: no camera button (never a dead one).
- [ ] `RecordPhotoStep`: title and new subtitle stay. `fixedSlot` row: `FilledTonalButton` "Just write" (`weight(1f)`) and a `FilledTonalIconButton` with `PbblsIcon.CAMERA` and `capture_take_photo_a11y`. Content: one Library tile filling the height (`contentFillsHeight`): `surfaceContainerLow`, `shapes.extraLarge`, `CAMERA_FOCUS` at `HERO`, `capture_library`, whole tile the target with a `Role.Button`. While a pick is processing or during the beat it shows the thumb (`PickedPhoto`'s decode) with a `LoadingIndicator`. Its failure state stays on the page. Delete `AddTile`'s dashes unless the render argues for them. Controls disabled while frozen.
- [ ] Remove the photo step's Skip/Done (`actionFor` returns null for PHOTO). "Change photo" and "Add a photo" call `model.back()`.
- [ ] `Capture_Photo` keeps its name and renders the new step. Add `Capture_PhotoPicked` (dark + French on `Capture_Photo`).
- [ ] Gate. Commit `feat(android): the photo step's three choices, with auto-advance`.

**Device pass (controller):** cancel the picker, pick (real return, 0.4 s, page), upload ring, Change photo, camera shot, against `mock-photo.png`. Report the tile for the maintainer's eye: it has no mock of its own.

---

# Part 6 — valence and emotion (branch `feat/1515-valence-android`)

iOS reference: `a7ab04898`, `Features/Path/ValencePickerContent.swift`, `Path/Valence/ValenceHelpSheet.swift`, `Complete/Steps/CompleteValenceStep.swift`, `Path/Models/Valence.swift`, `Path/EmotionPickerContent.swift`. Renders `valenceEmpty-paper-en.png`, `valenceHelp-paper-en.png`. Part of #1515.

**Files:** Create `core/data/RecordFlowMemory.kt`, `features/path/valence/ValenceHelpSheet.kt`. Modify `features/path/valence/{ValenceFan,ValenceFanLayout,ValenceRoll,ValenceWord}.kt`, `features/path/flow/steps/{RecordValenceStep,RecordEmotionStep}.kt`, `features/path/complete/*`, `core/data/EmotionPaletteService.kt`, `core/model/CompleteSnapshot.kt`, `features/path/create/pickers/EmotionPickerSheet.kt`, `di/ServiceBindings.kt`, the test fakes.

- [ ] **Failing tests:** `CompleteFlowModelTest`: no seed exists, Continue stays disabled until a pick; `CompleteSnapshotTest`: a valence alone is now not empty (it is no longer seeded); `ValenceFanLayoutTest` port of `rowLabelBox` keeping labels clear of a selected stone; `CompleteFlowViewModelTest`: help opens only with a live session, a settled read with zero completed moments, and an unseen flag; a failed read keeps it closed; a null session keeps it closed and does not read; the seen flag is set on open.

- [ ] **Memory**, device-local in `pebbles_prefs` (the file the Complete autosaves already use), with `RecordFlowMemorying` for the fake:

```kotlin
interface RecordFlowMemorying {
    fun isValenceHelpSeen(ownerId: String): Boolean
    fun markValenceHelpSeen(ownerId: String)
    /** Newest first, [RECENT_DOMAIN_CAP] at most. */
    fun recentDomainIds(ownerId: String): List<String>
    fun recordDomains(picked: List<String>, ownerId: String)
}
```
Keys per owner, lowercase, as iOS's `valenceHelpSeen.<owner>` and `recentDomains.<owner>`. `RECENT_DOMAIN_CAP = 5`. Part 7 uses the domains half; give it its tests here.

- [ ] Delete `seedValenceIfNeeded`, `onSeed`, `record_valence_subtitle` if unused. `ValenceFan(selected, onSelect, showsCaption = true)`: nothing selected keeps all nine at full strength (it already does); the column and row labels (`ValenceFanLayout.rowLabelBox`, port) in the same small-caps treatment as the render, decorative to TalkBack (the stone's `contentDescription` already says both axes); ghost stone (the `DashedPlaceholder` rule: `outline`) plus `valence_ghost_prompt` in the reserved lockup height until a pick, then `ValenceRoll`. `ValencePickerSheet` still parks on neutral-medium: it edits a pebble that has a value.
- [ ] Neutral word: `valence_headline_neutral` becomes Neutral / Neutre. Check every reader (`ValenceWord`, `features/path/card/logic/ValenceLockup.kt`, achievements, wraps): grep the key and the word "Moment"; a same-file test may pin the old word (iOS had one outside the suites its parts ran). The lockup's one-sentence rule per D7 is iOS's `spanWord`: read it and mirror.
- [ ] `ValenceHelpSheet`: `ModalBottomSheet`, `verticalScroll`, the fan at scale 0.62 (`showsCaption = false`, `HIGHLIGHT_MEDIUM`, no input) keeping its column labels, the four lines, `PebblesPrimaryButton` "Got it". **Render it** at Pixel 8 height before reporting: its partial detent must not hide the button.
- [ ] `RecordValenceStep(selected, onSelect, isFirstPebble, onHelpSeen…)`: the fan + a `TextButton` with `ic_info` and `valence_help_link` in `onSurfaceVariant`. Add `ic_info.xml` and figure 22 "how to read this, an info mark" in the wave brief. Opens by itself once `isFirstPebble` turns true.
- [ ] `CompleteFlowViewModel.start`: on an unseen flag only (cheaper, and no read for most users), `reads.loadPath()`; `isFirstPebble = moments.none { it.isCompleted }` into `CompleteFlowUiState`. After `restore`, never before it.
- [ ] `CompleteFlowScreen(momentId, origin, …)`: the valence subtitle is `complete_after_save` when `origin != CARD`. Other steps have none.
- [ ] Emotion Retry (D9): `EmotionPaletteService` gains a `loadFailed` state and `RecordEmotionStep` draws `emotion_load_error` + `record_photo_retry` instead of a spinner when it is set and nothing is cached. Retry calls `load()`.
- [ ] Previews: `Complete_Valence` keeps its name and renders the empty state; add `Complete_ValencePicked`, `Complete_ValenceHelp`, `Complete_EmotionFailed`, French and dark on the first, large font on the help sheet.
- [ ] Gate. Commit `feat(android): valence without a preselection, with labels and help`.

**Device pass (controller):** empty fan, first touch, roll, help on a fresh seen flag (`adb shell run-as` or a clean install), labels clear of a selected stone at every cell, against the renders.

---

# Part 7 — the Life screen (branch `feat/1515-life-screen-android`)

iOS reference: `9413085e2`, `Complete/Steps/CompleteLifeStep.swift`, `Souls/UsualSoulsStrip.swift`, `Complete/CompleteFlowModel.swift`. Render `life-paper-en.png`, mock `mock-life.png`. Part of #1515. **Gate of the stack's French XXL pass.**

**Files:** Modify `core/model/{PebbleAnswers,PebbleDraft,MomentPayloads,CompleteSnapshot}.kt`, `features/path/complete/*`, `features/path/EditPebbleViewModel.kt`, `core/ui/MomentErrorMessage.kt`. Create `features/path/flow/steps/RecordLifeStep.kt`, `core/ui/souls/UsualSoulsStrip.kt`. Delete `RecordDomainStep`, `RecordSoulsStep`, `RecordCollectionStep` (collection and privacy move to Part 8's rows: keep `RecordPrivacyStep`'s strings only if Part 8 needs them), `DomainPickerContent` if unused.

### Task 7.1: Several domains, and an edit that keeps them

**This fixes real data loss, not just a shape.** `PebbleDraft.from` keeps the first domain and `PebbleCompletePayload.from` sends `domain_ids: [first]`, so editing a pebble with three domains (iOS already writes them) would collapse it to one on Android. iOS omits a set the user left as prefilled (#1099 D8), and `complete_pebble` replaces a set only when its key is present.

- [ ] **Failing tests** in `MomentPayloadEncodingTest` with exact bytes, foreign vectors from iOS `CompletePebblePayload`:

```kotlin
@Test fun `a first completion sends every key`() { /* domain_ids ["d1","d2"], soul_ids [], collection_ids [] */ }
@Test fun `an edit that left domains and collection alone omits both keys`() { /* no domain_ids, no collection_ids; soul_ids present */ }
@Test fun `an edit that changed the domain replaces the set with the pick`() { /* domain_ids ["d9"] */ }
@Test fun `an end-screen visibility edit sends only that key`() {
    assertEquals("""{"moment_id":"m","visibility":"private"}""",
        MomentWireJson.encodeToString(PebbleCompletePayload.serializer(), PebbleCompletePayload(momentId = "m", visibility = Visibility.PRIVATE)))
}
@Test fun `a removed collection sends an empty list`() { /* {"moment_id":"m","collection_ids":[]} */ }
```
`CompleteSnapshotTest`: a stored JSON with the old `"domain_id":"d1"` restores `domainIds == ["d1"]`.

- [ ] **Implement:**

```kotlin
data class PebbleAnswers(
    val valence: Valence? = null,
    val emotionId: String? = null,
    /** In tap order, [DOMAIN_CAP] at most (D10). */
    val domainIds: List<String> = emptyList(),
    val soulIds: List<String> = emptyList(),
    val collectionId: String? = null,
    val visibility: Visibility = Visibility.SECRET,
) {
    val isComplete: Boolean get() = valence != null && emotionId != null && domainIds.isNotEmpty()

    companion object {
        const val DOMAIN_CAP = 3
    }
}

@Serializable
data class PebbleCompletePayload(
    @SerialName("moment_id") val momentId: String,
    @SerialName("emotion_id") val emotionId: String? = null,
    val intensity: Int? = null,
    val positiveness: Int? = null,
    val visibility: Visibility? = null,
    @SerialName("domain_ids") val domainIds: List<String>? = null,
    @SerialName("soul_ids") val soulIds: List<String>? = null,
    @SerialName("collection_ids") val collectionIds: List<String>? = null,
) {
    companion object {
        /** A first completion: every key, so the join tables end up exactly as answered. */
        fun from(momentId: String, answers: PebbleAnswers): PebbleCompletePayload { /* requireNotNull as today; domainIds = answers.domainIds; collectionIds = listOfNotNull(answers.collectionId) */ }

        /**
         * Edit pebble (#1099 D8): a set left as prefilled is omitted, and absent means keep, so a
         * pebble with three domains survives an edit of its emotion. Touching the field replaces
         * the set with the pick.
         */
        fun edit(momentId: String, draft: PebbleDraft): PebbleCompletePayload { /* compares draft.domainId with draft.prefillDomainId, draft.collectionId with draft.prefillCollectionId */ }
    }
}
```
`PebbleDraft` keeps its single `domainId` (the form's dropdown) and gains `prefillDomainId`, `prefillCollectionId`, set by `from(moment)`. `EditPebbleViewModel` calls `edit`. `MomentWireJson` already omits nulls and encodes defaults. `CompleteSnapshot`: `domain_ids` list + a read-only legacy `domain_id`; `isEmpty` counts domains.

- [ ] Commit `fix(android): keep a pebble's domains and collections through an edit`.

### Task 7.2: The steps

- [ ] **Failing tests** ported from iOS `CompleteFlowModelTests`: `toggleDomain` tap order, cap of 3 refused with a WARNING haptic, un-toggle; `firstGap` lands on LIFE when answered; `advance` never reaches SUCCESS; `setSouls` silent; a failed complete keeps the step and shows `complete_partial_failure`, a known slug keeps its own copy. `CompleteFlowViewModelTest`: success records the domains in `RecordFlowMemory`; failure flushes the snapshot.

```kotlin
enum class CompleteStep(@StringRes val titleRes: Int, val bringsOwnScroll: Boolean = false) {
    VALENCE(R.string.record_valence_title),
    EMOTION(R.string.record_emotion_title, bringsOwnScroll = true),
    LIFE(R.string.record_life_title, bringsOwnScroll = true),
    SUCCESS(R.string.record_success_title),
    ;
    val dotIndex: Int? get() = if (this == SUCCESS) null else ordinal
    val next: CompleteStep? get() = entries.getOrNull(ordinal + 1)
    val previous: CompleteStep? get() = if (ordinal == 0) null else entries[ordinal - 1]
    companion object { val counted: List<CompleteStep> = entries.filter { it != SUCCESS } }
}
```
`CompleteFlowState.hasAnswer` exhaustive: `SUCCESS -> true`, `VALENCE`, `EMOTION`, `LIFE -> domainIds.isNotEmpty()`. `firstGap() = counted.firstOrNull { !hasAnswer(it) } ?: LIFE`. `CompleteFlowModel.toggleDomain`, `setSouls`, and `commitAndAdvance`/`advance` never entering SUCCESS. `momentWriteKnownErrorRes(error): Int?` returns null for what today falls to `moment_save_error_generic`; `momentWriteErrorRes` becomes `known ?: generic`; `complete()` shows `known ?: R.string.complete_partial_failure`.

- [ ] `UsualSoulsStrip(souls, selection, isLoaded, onToggle, onMore)` in `core/ui/souls`, mirroring the iOS file including the `shown` rule (someone chosen but outside the four takes the last slot). "Who was there?" label, four `SoulTile`s + a More tile (dashed `outline`, `PLUS_CIRCLE`). `souls.isEmpty() && isLoaded`: one `souls_add_people` tile. Not loaded: reserve the height, no tile claiming nobody exists. At large font, names truncate to one line and the row stays usable (iOS still owes this; do not repeat it).
- [ ] `RecordLifeStep(answers, domains, souls, isSoulsLoaded, recent, error, isCompleting, onToggleDomain, onToggleSoul, onMore, onComplete)`: stateless. A `LazyVerticalGrid` of 2 columns (1 at `fontScale >= 1.5f`) with full-span RECENT (when any) and ALL labels; tiles `heightIn(min = 48.dp)`, `shapes.medium`, `GlyphImage` 28 dp, `surfaceContainerLow` or, selected, `primaryContainer` with `onPrimaryContainer` and emphasized type, one-line name via `ReferenceStrings`, `selected` semantics with the description as supporting semantics. Hoisted `lastTapped` for the caption. Then the pinned bar: `surfaceContainerLow` under a `HorizontalDivider(outlineVariant)`: caption (one line, `ReferenceStrings.domainLabel`, absent until a tap), the strip, the orange error, `PebblesPrimaryButton(complete_cta)`, disabled until complete, loading while completing. **The bar's tint reaches the bottom edge of the screen:** `CompleteFlowContent`'s `safeDrawingPadding` would leave an untinted strip under it, so inset the column's top and sides and let each step consume the bottom. The list dissolves into the bar (overlay gradient, not a clip).
- [ ] More opens `SoulChooserSheet(title = souls_who_was_there, allowsSkip = true, startsPartial = false, onConfirm = model::setSouls)`. `LaunchedEffect(Unit) { refs.refreshSouls() }` on the step (#1370).
- [ ] `CompleteFlowScreen`: read `recent` once from `RecordFlowMemory` via the VM; `isSoulsLoaded = refs.hasLoaded`. `actionFor` keeps only VALENCE's Continue. Collection and visibility stay at their defaults until Part 8: answers carry them.
- [ ] Previews: `Complete_Life` (two domains, one soul, a caption), `Complete_LifeNoSouls`, `Complete_LifeError`, French, dark, `@PreviewLargeFontTall`. Update or remove the deleted steps' previews.
- [ ] Gate. Commit `feat(android): the life screen, domains and people on one page`.

**Device pass (controller):** cap of three, RECENT after a completion, strip toggle, More and back with the selection, the bar's edge, the failure line (airplane mode), French at font scale 2.0, against `life-paper-en.png` and `mock-life.png`. Writes land on the signed-in test account, Secret, deleted afterwards.

---

# Part 8 — Make it richer, entries, the bare page (branch `feat/1515-make-it-richer-android`)

iOS reference: `3004124b0`, `Complete/Steps/CompleteRicherStep.swift`, `Home/HomeView.swift`, `Path/Card/BareMomentPage.swift`, `Path/Card/HeldCardPage.swift`. Render `richer-paper-en.png`, mock `mock-richer.png`. Part of #1515.

**Files:** Create `features/path/flow/steps/RecordRicherStep.kt`, `core/ui/OnboardingPrompt.kt` (moved `onboardingHandoffPrompt`). Modify `features/path/complete/*`, `features/path/capture/*`, `navigation/PebblesEntryProvider.kt`, `features/home/HomeScreen.kt`, `HomeMomentsViewModel.kt`, `features/path/card/BareMomentPage.kt`, `features/path/PebbleDetail*`. Delete `RecordSuccessStep`, `features/home/quick/*`, `features/path/read/PebbleReadPetroglyph` if unused, and their strings.

- [ ] **Failing tests** in `CompleteFlowViewModelTest`: `editVisibility` sends only that key and updates the answers; `editCollection(null)` sends `[]`; a failed edit keeps the old value and sets `editErrorRes`; a second edit while one runs is ignored; `onResumed` re-reads `hasWords` after the first resume. `RicherWeekRowTest`: Monday-first week around Wednesday 2026-10-14, the marked day is index 2 (`date -j -f %F 2026-10-14 +%A` first).
- [ ] VM: `CompleteFlowUiState` gains `happenedAt`, `hasWords` (false only once loaded), `isEditing`, `@StringRes editErrorRes`. Read them from `reads.loadMoment` (already called in `start`). `editCollection(id: String?)`, `editVisibility(v)`: one write each through `PebbleCompletePayload(momentId, …)`, in `viewModelScope`, `Haptic(SELECTION)` on success, model updated silently. Keep the quiet path exactly: no karma notice, no success buzz, no achievement check.
- [ ] `RecordRicherStep`: scrolls. `LitStone(valence, palette, glyph, seed = momentId.hashCode().toLong(), press = rememberStonePress…, width = …)` at about 240 dp tall (read the Spine's call site for `press` and the seed); name (`MomentNameText`); `+N karma` with `KARMA_DOT`, or, quiet, `record_success_quiet`; the week row (dots, larger + `primary` on the day, narrow weekdays); `ListGroup(header = richer_section)` of `SegmentedListItem`s (M3 grouped items, not iOS rows): "Add to a collection" showing its name, hidden with no collections; "Who can see it" showing the grade; "Add a few words" with `+1 karma`, only while `!hasWords`. The two value rows open a stock `DropdownMenu` as `PebbleForm` does, the current one ticked with `CHECKMARK_DOT`, `richer_remove_collection` when one is set. Rows disabled while `isEditing`. `editErrorRes` in `colors.error`. Then, quiet only, `record_success_get_support`; `PebblesPrimaryButton(richer_done)`; `TextButton(richer_record_another)` when `onRecordAnother != null`.
- [ ] Entry provider: `CompleteFlowScreen(momentId, origin, onDismiss, onOpenSupport, onAddWords = { navigate(EditMoment(momentId)) }, onRecordAnother = origin.restart()?.let { { goBack(); navigate(it) } })`, with `CompleteOrigin.restart()`: CAPTURE → `Capture()`, HOME_NEW_PEBBLE → `Capture(chainsComplete = true)`, QUICK_MOMENT → `Capture(startsAtMomentPage = true)`, CARD → null. `LifecycleResumeEffect` → `onResumed` for `hasWords`.
- [ ] D13: Home's Quick moment tile navigates `Capture(startsAtMomentPage = true)`. Delete `QuickMomentSheet`, `QuickMoment`, its state in `HomeMomentsViewModel`. **Onboarding hand-off stays:** `onResumed` now emits an effect that navigates `Capture(startsAtMomentPage = true, promptReason = note.reason?.slug)`, the souls refresh stays, and the page's NAME placeholder is the prompt (`onboardingHandoffPrompt`, moved to `core/ui`, read by the page) where today the sheet shows `form.promptRes`. Move its test with it. `home_toast_name` becomes Edit / Modifier; it already opens `EditMoment`.
- [ ] D14: `BareMomentPage`: `hasDraft` → kicker `bare_place_started`, button `record_action_continue`; `onAddWords` → a `TextButton` with `ABSTRACT_PEN` under the button while the moment has no description. The detail ViewModel computes `hasDraft = snapshots.load(momentId, owner) != null` at load and on resume, never per recomposition. `PebbleDetailScreen` opens `EditMoment`.
- [ ] Previews: `Complete_Richer`, `Complete_RicherQuiet`, `Complete_RicherNoCollections`, `Complete_RicherWordsDone`, dark, French, large font; `Pebble…` previews for the bare page with a draft and with words offered.
- [ ] Baseline profile: `grep -rn` the removed `JourneyTags` in `:baselineprofile`; keep the tags alive or update the journey.
- [ ] Gate. Commit one per concern: `feat(android): make it richer, the end screen`, `feat(android): one entry model and the quick moment page`, `feat(android): the bare page says you started this`.

**Device pass (controller):** a whole pebble end to end, each row's edit, words (+1), Record another from each origin, Quick moment, camera toast, bare page with and without a draft, against `richer-paper-en.png` and `mock-richer.png`.

---

# Part 9 — copy and renders (branch `feat/1515-copy-renders-android`)

iOS reference: `1faf15cdc`. **Resolves #1515.**

- [ ] `git show 1faf15cdc -- apps/ios/Pebbles/Resources/Localizable.xcstrings`: apply the shortened domain labels to Android's `domain_*` strings, EN and FR, verbatim. Then a script diff (scratch, not committed): every copy-table value against `values/strings.xml` and `values-fr/strings.xml`; fix what differs.
- [ ] Large font and narrow: render every new preview with `@PreviewLargeFont`, and French; fix overflow. `Modifier.readableWidth()` on every new screen (`@PreviewWide` on the moment page and Life).
- [ ] Update `apps/android/CLAUDE.md`'s "Capture and Complete (#1048)" bullet: it now states a wrong flow. Only that bullet.
- [ ] Append to `docs/decisions/log.md` per PR checklist 7:

```markdown
## 2026-11-06 — Android's record flow keeps the modal photo picker, and its chooser follows the Android mocks (#1515, #1519)

- **Status:** taken
- **Scope:** android
- **Decision:** The record flow and the souls chooser mirror iOS 1:1 except where listed. The system Photo Picker stays modal (Play's photo policy), so a photo moment costs 4 taps, not 3. Search results stand alone with the match highlighted (mock 11), no Usual tiles below. The picker is a `ModalBottomSheet` because a pushed entry cannot hand a result back. Quick moment's sheet is gone; its Home tile and the onboarding hand-off open the moment page, the hand-off's prompt as the name's placeholder. Device-local recents and the help flag live in `pebbles_prefs` (`RecordFlowMemory`). Edit pebble omits a domain or collection set the user left as prefilled, as iOS has since #1099.
- **Why:** See the plan's table of where the sources disagree.
- **Consequences:** The D6 snackbar and the D9 0.3 s beat are on neither surface; they are one finding for both.
- **Refs:** #1515, #1519, `docs/superpowers/plans/2026-11-06-android-record-flow-and-souls.md`.
```
- [ ] Gate, plus `node scripts/arkaik/android-shots-manifest.mjs --check` (fails until the re-baseline; note it). Commit `feat(android): record flow copy and large text`.
- [ ] Append "Lessons learned" to this plan once the stack ships.

The controller then runs the full device pass, ships the stack (`gh stack submit --auto`, titles, bodies, labels `feat ui android`, milestone M70, Lab Note on the tip via the `lab-note` skill, `rebaseline-screenshots` on the tip alone, in its own `gh pr edit`), and drops the build-journal pebble. Merging is the maintainer's.

---

## Model routing

| Thread | Model |
|---|---|
| Implementer, per part | sonnet |
| Reviews (mock + spec, then code) | sonnet |
| A thread reporting BLOCKED or looping | opus |
| Orchestration, device pass, side-by-sides, shipping | this session |

Every review is run against the mocks: the first reviewer opens the mock, the iOS render and the implementer's render side by side before reading a line of the diff.

## Self-review against the specs

| Spec item | Where |
|---|---|
| S1 ranking, S2 selection, S3 search, S4 inline create | 1.1, 1.2, 1.3, 2, 3 |
| S5 hosts (picker, half sheet, People, Life strip) | 3, 2, 7.2 |
| S7 load and errors, S8 large text, S9 copy | 1.3, 1.3 + 7.2 + 9, copy table |
| S10 Android chrome | 1.3, 2 |
| D1 flow and counts, D2 preview and persistence | 4.1, 4.2, 5, 7.2 |
| D3 photo, D4 chip, D5 page, D6 saves and partial failure | 5, 4.4, 4.4, 4.1 + 7.2 |
| D7 no seed, labels, ghost, Neutral; D8 help + (i); D9 Retry | 6 |
| D10 Life, D11 no pill and fades, D12 richer, D13 entries, D14 bare page | 7.2, 4.4 + 7.2, 8, 8, 8 |
| D15 copy, D16 measurement (no client work) | copy table + 9 |

Known deviations, stated: the table at the top; the upload ring is static, as iOS's; `SavedStateHandle` covers process death but not a user swipe-kill, as the spec accepts; words count as something to keep on Android (a small improvement to raise for iOS).
