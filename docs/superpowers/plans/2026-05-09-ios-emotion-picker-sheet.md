# iOS Emotion Picker Sheet — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat inline emotion `Picker` in `PebbleFormView` with a presented `EmotionPickerSheet` that groups the 38 emotions under their 7 categories, ordered by the form's selected `Valence`.

**Architecture:** One small DB migration (adds `emotions.emoji`, realigns legacy `emotions.color`, refreshes `v_emotions_with_palette`). Picker reads from the existing `EmotionPaletteService` cache; no new fetch. Section ordering is iOS-side static curation data driven by `(Valence.sizeGroup, Valence.polarity)`.

**Tech Stack:** SwiftUI (iOS 17+), Swift Testing, Supabase Postgres, `xcstrings` localization.

**Issue:** [#370](https://github.com/Bohns/pbbls/issues/370)
**Spec:** `docs/superpowers/specs/2026-05-09-ios-emotion-picker-sheet-design.md`
**Branch:** `feat/370-ios-emotion-picker-sheet` (already created off `main`)

**Operational notes (per project memory):**
- This user does NOT run local Supabase / Docker. Migrations are pushed directly to the linked remote project with `supabase db push`. Types are regenerated via `supabase gen types typescript --linked > types/database.ts`. Do NOT run `npm run db:reset`, `npm run db:start`, or anything that requires a local Supabase container.
- If `--linked` fails for any reason (CLI auth issue), fall back to the Supabase MCP tool `generate_typescript_types` and write the result to `packages/supabase/types/database.ts`.

---

## Task 1: Database migration — emoji column, legacy color realignment, refreshed view

**Files:**
- Create: `packages/supabase/supabase/migrations/20260509000002_emotions_picker_data.sql`
- Modify: `packages/supabase/types/database.ts` (regenerated)

- [ ] **Step 1: Verify branch and base**

```bash
git branch --show-current
```
Expected: `feat/370-ios-emotion-picker-sheet`

```bash
git status
```
Expected: `nothing to commit, working tree clean`

- [ ] **Step 2: Write the migration**

Create `packages/supabase/supabase/migrations/20260509000002_emotions_picker_data.sql` with:

```sql
-- Migration: Emotion picker data (#370)
-- Spec:  docs/superpowers/specs/2026-05-09-ios-emotion-picker-sheet-design.md
--
-- Three changes bundled — all derivable from existing rows, no manual data step:
--   A. Adds public.emotions.emoji (text not null) and seeds all 38 rows.
--   B. Realigns legacy public.emotions.color to category.primary_color (6-digit form),
--      so shipped iOS versions still reading emotions.color render acceptably.
--   C. Refreshes public.v_emotions_with_palette to expose emoji.
--
-- Note: emotions.color is left in place (soft-deprecated). Old iOS clients still
-- read it via from("emotions"); this migration aligns those colors to the new
-- palette so legacy installs don't show drift until users update.

begin;

-- ============================================================
-- A. emotions.emoji
-- ============================================================

alter table public.emotions add column emoji text;

update public.emotions set emoji = case slug
  when 'amazed'       then '🤩'
  when 'amused'       then '😂'
  when 'angry'        then '😡'
  when 'annoyed'      then '😒'
  when 'anxious'      then '😰'
  when 'ashamed'      then '🫣'
  when 'brave'        then '🫡'
  when 'calm'         then '🙂'
  when 'confident'    then '😌'
  when 'content'      then '😀'
  when 'disappointed' then '😕'
  when 'discouraged'  then '😫'
  when 'disgusted'    then '🤢'
  when 'drained'      then '😪'
  when 'embarrassed'  then '😬'
  when 'excited'      then '😇'
  when 'frustrated'   then '😤'
  when 'grateful'     then '🥰'
  when 'guilty'       then '😓'
  when 'happy'        then '🤗'
  when 'hopeful'      then '🥹'
  when 'hopeless'     then '😞'
  when 'indifferent'  then '😑'
  when 'irritated'    then '🙄'
  when 'jealous'      then '😠'
  when 'joyful'       then '🥳'
  when 'lonely'       then '🥺'
  when 'overwhelmed'  then '😣'
  when 'passionate'   then '😍'
  when 'peaceful'     then '☺️'
  when 'proud'        then '😎'
  when 'relieved'     then '😮‍💨'
  when 'sad'          then '😢'
  when 'satisfied'    then '😊'
  when 'scared'       then '😱'
  when 'stressed'     then '😖'
  when 'surprised'    then '😯'
  when 'worried'      then '😟'
end;

alter table public.emotions alter column emoji set not null;

-- ============================================================
-- B. legacy emotions.color realignment
-- ============================================================

update public.emotions e
set color = substr(c.primary_color, 1, 7)  -- '#RRGGBBAA' → '#RRGGBB'
from public.emotion_categories c
where e.category_id = c.id;

-- ============================================================
-- C. refresh view to include emoji
-- ============================================================

create or replace view public.v_emotions_with_palette as
select
  e.id, e.slug, e.name, e.color, e.emoji,
  c.id              as category_id,
  c.slug            as category_slug,
  c.name            as category_name,
  c.primary_color, c.secondary_color, c.light_color, c.surface_color
from public.emotions e
join public.emotion_categories c on c.id = e.category_id;

commit;
```

- [ ] **Step 3: Push the migration to remote Supabase**

```bash
cd packages/supabase && npx supabase db push && cd -
```
Expected: output reports the new migration was applied; no errors. If prompted for confirmation, accept.

- [ ] **Step 4: Regenerate database types**

```bash
cd packages/supabase && npx supabase gen types typescript --linked > types/database.ts && cd -
```
Expected: `packages/supabase/types/database.ts` updated. The `emotions.Row` type now includes `emoji: string`.

If `--linked` fails: use the Supabase MCP `generate_typescript_types` tool and write its output to `packages/supabase/types/database.ts`.

- [ ] **Step 5: Verify the type change**

```bash
grep -A8 'emotions: {' packages/supabase/types/database.ts | head -12
```
Expected: the `Row` block contains `emoji: string` alongside `category_id`, `color`, `id`, `name`, `slug`.

- [ ] **Step 6: Commit migration + regenerated types**

```bash
git add packages/supabase/supabase/migrations/20260509000002_emotions_picker_data.sql \
        packages/supabase/types/database.ts
git commit -m "$(cat <<'EOF'
feat(db): add emotions.emoji and refresh palette view (#370)

Bundles three additive changes for the iOS emotion picker:
- emotions.emoji (text not null), seeded from a slug→emoji case map.
- emotions.color realigned to category.primary_color so shipped iOS
  versions render acceptably until users update.
- v_emotions_with_palette refreshed to expose emoji.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Decode `emoji` on `EmotionWithPalette` (TDD)

**Files:**
- Modify: `apps/ios/PebblesTests/EmotionWithPaletteDecodingTests.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Models/EmotionWithPalette.swift`

- [ ] **Step 1: Extend the decoder test fixture and add an emoji-decoding test**

Edit `apps/ios/PebblesTests/EmotionWithPaletteDecodingTests.swift`. Update `validJson` to include emoji and add a new test + a null-emoji rejection test:

```swift
private let validJson = """
{
  "id": "11111111-1111-1111-1111-111111111111",
  "slug": "anxiety",
  "name": "Anxiety",
  "color": "#7B5E99",
  "emoji": "😰",
  "category_id": "22222222-2222-2222-2222-222222222222",
  "category_slug": "fear",
  "category_name": "Fear",
  "primary_color": "#7B5E99FF",
  "secondary_color": "#AE91CCFF",
  "light_color": "#F2EFF5FF",
  "surface_color": "#7B5E991A"
}
"""
```

Add inside the `EmotionWithPaletteDecodingTests` struct, after `decodesValid()`:

```swift
@Test("decodes the emoji field")
func decodesEmoji() throws {
    let row = try decode(validJson)
    #expect(row.emoji == "😰")
}

@Test("rejects null emoji")
func rejectsNullEmoji() {
    let json = validJson.replacingOccurrences(
        of: "\"emoji\": \"😰\"",
        with: "\"emoji\": null"
    )
    #expect(throws: DecodingError.self) { try decode(json) }
}

@Test("rejects missing emoji")
func rejectsMissingEmoji() {
    let json = validJson.replacingOccurrences(
        of: "\"emoji\": \"😰\",\n",
        with: ""
    )
    #expect(throws: DecodingError.self) { try decode(json) }
}
```

- [ ] **Step 2: Run the suite — expect failure**

The Pebbles test suite runs via Xcode (`xcodebuild test`) or via a local script. Run the relevant suite from the iOS workspace:

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/EmotionWithPaletteDecodingTests test 2>&1 | tail -30 && cd -
```
Expected: build OR runtime failure. Most likely runtime: the new tests fail because the decoder doesn't handle `emoji` yet (the current decoder doesn't decode it, so `decodesEmoji` will fail to compile against `row.emoji` — the property doesn't exist yet).

If compile fails before tests run, that's the expected "failing test" stage — proceed to step 3.

- [ ] **Step 3: Add `emoji` to the model and decoder**

Edit `apps/ios/Pebbles/Features/Path/Models/EmotionWithPalette.swift`. Add `let emoji: String` after `let name: String`, add `case emoji` to `CodingKeys`, decode it in `init(from:)`:

```swift
struct EmotionWithPalette: Identifiable, Decodable {
    let id: UUID
    let slug: String
    let name: String
    let emoji: String
    let categoryId: UUID
    let categorySlug: String
    let categoryName: String
    let palette: EmotionPalette

    private enum CodingKeys: String, CodingKey {
        case id, slug, name, emoji
        case categoryId    = "category_id"
        case categorySlug  = "category_slug"
        case categoryName  = "category_name"
        case primaryColor  = "primary_color"
        case secondaryColor = "secondary_color"
        case lightColor    = "light_color"
        case surfaceColor  = "surface_color"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(UUID.self, forKey: .id)
        self.slug = try container.decode(String.self, forKey: .slug)
        self.name = try container.decode(String.self, forKey: .name)
        self.emoji = try container.decode(String.self, forKey: .emoji)
        self.categoryId = try container.decode(UUID.self, forKey: .categoryId)
        self.categorySlug = try container.decode(String.self, forKey: .categorySlug)
        self.categoryName = try container.decode(String.self, forKey: .categoryName)

        let primary = try container.decode(String.self, forKey: .primaryColor)
        let secondary = try container.decode(String.self, forKey: .secondaryColor)
        let light = try container.decode(String.self, forKey: .lightColor)
        let surface = try container.decode(String.self, forKey: .surfaceColor)

        guard let palette = EmotionPalette(
            primaryHex: primary,
            secondaryHex: secondary,
            lightHex: light,
            surfaceHex: surface
        ) else {
            throw DecodingError.dataCorruptedError(
                forKey: .primaryColor,
                in: container,
                debugDescription: "Palette hex strings failed to parse"
            )
        }
        self.palette = palette
    }
}
```

Update the doc comment at the top of the file to mention emoji:

```swift
/// A decoded row from `public.v_emotions_with_palette`.
///
/// PostgREST types every view column as nullable in `database.ts`, but the
/// underlying invariants — `emotions.category_id NOT NULL` (shipped in #367),
/// `emotions.emoji NOT NULL` (shipped in #370), and the four palette `text NOT NULL`
/// columns on `emotion_categories` — guarantee non-null values in practice. This
/// decoder enforces that invariant at the boundary: rows with any null required
/// field throw `DecodingError`, which `EmotionPaletteService` logs and skips so
/// the bad row simply isn't cached.
```

- [ ] **Step 4: Run the test suite — expect pass**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/EmotionWithPaletteDecodingTests test 2>&1 | tail -30 && cd -
```
Expected: all decoding tests pass (including the 3 new ones).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/EmotionWithPalette.swift \
        apps/ios/PebblesTests/EmotionWithPaletteDecodingTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): decode emoji on EmotionWithPalette (#370)

Adds the new emoji field (NOT NULL invariant per migration 20260509000002)
to the view-row decoder, with rejection tests for null/missing.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `EmotionCategory` model

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/EmotionCategory.swift`
- Modify: `apps/ios/project.yml` (no — xcodegen picks up files automatically; the source path `Pebbles` is recursive)

- [ ] **Step 1: Create the file**

Write `apps/ios/Pebbles/Features/Path/Models/EmotionCategory.swift`:

```swift
import Foundation

/// One emotion category derived from `EmotionWithPalette.categoryId`.
///
/// Built by the picker sheet by deduplicating the cached
/// `EmotionPaletteService.byEmotionId` rows on `categoryId` — there is no
/// separate `emotion_categories` fetch on iOS. UI reads `localizedName`
/// (extension lives in `Emotion+Localized.swift`) for display; `name` is
/// the raw English DB column kept only as a fallback.
struct EmotionCategory: Identifiable, Hashable {
    let id: UUID
    let slug: String
    let name: String
    let palette: EmotionPalette
}
```

- [ ] **Step 2: Regenerate the Xcode project so the new file is included**

```bash
cd apps/ios && npm run generate && cd -
```
Expected: `xcodegen generate` runs cleanly.

- [ ] **Step 3: Verify the file compiles**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10 && cd -
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/EmotionCategory.swift apps/ios/project.yml apps/ios/Pebbles.xcodeproj 2>/dev/null || true
git add apps/ios/Pebbles/Features/Path/Models/EmotionCategory.swift
git commit -m "$(cat <<'EOF'
feat(ios): add EmotionCategory model (#370)

Plain value type built from EmotionWithPalette rows by deduplicating on
categoryId. No separate fetch — drives section grouping in the upcoming
EmotionPickerSheet.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

(The `.xcodeproj` is git-ignored per `apps/ios/CLAUDE.md`, so the second `git add` is the real one — first add is harmless if pbxproj happens to not be ignored locally.)

---

## Task 4: `EmotionCategoryOrdering` with structural tests (TDD)

**Files:**
- Create: `apps/ios/PebblesTests/EmotionCategoryOrderingTests.swift`
- Create: `apps/ios/Pebbles/Features/Path/Models/EmotionCategoryOrdering.swift`

- [ ] **Step 1: Write the failing test file**

Create `apps/ios/PebblesTests/EmotionCategoryOrderingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("EmotionCategoryOrdering")
struct EmotionCategoryOrderingTests {

    /// The seven category slugs that must appear (and only these) in every
    /// ordering. Mirrors public.emotion_categories.slug.
    private let allCategorySlugs: Set<String> = [
        "anger", "fear", "joy", "peace", "pride", "sadness", "shame"
    ]

    @Test("every cell has exactly 7 slugs")
    func everyCellHasSevenSlugs() {
        for (key, slugs) in EmotionCategoryOrdering.byValence {
            #expect(slugs.count == 7, "\(key) has \(slugs.count) slugs, expected 7")
        }
    }

    @Test("every cell contains the same 7 unique slugs")
    func everyCellMatchesCanonicalSet() {
        for (key, slugs) in EmotionCategoryOrdering.byValence {
            let asSet = Set(slugs)
            #expect(asSet == allCategorySlugs, "\(key) slug set mismatch: \(asSet)")
            #expect(asSet.count == slugs.count, "\(key) has duplicate slugs")
        }
    }

    @Test("default ordering has the same 7 unique slugs")
    func defaultMatchesCanonicalSet() {
        let asSet = Set(EmotionCategoryOrdering.default)
        #expect(asSet == allCategorySlugs)
        #expect(asSet.count == EmotionCategoryOrdering.default.count)
    }

    @Test("all 9 valence cells are populated")
    func allNineCellsPresent() {
        let expected: [(ValenceSizeGroup, ValencePolarity)] = [
            (.large, .highlight), (.medium, .highlight), (.small, .highlight),
            (.large, .neutral),   (.medium, .neutral),   (.small, .neutral),
            (.large, .lowlight),  (.medium, .lowlight),  (.small, .lowlight),
        ]
        for (size, polarity) in expected {
            let key = EmotionCategoryOrdering.Key(size, polarity)
            #expect(EmotionCategoryOrdering.byValence[key] != nil,
                    "missing ordering for (\(size), \(polarity))")
        }
    }

    @Test("order(for: nil) returns the default")
    func nilValenceUsesDefault() {
        #expect(EmotionCategoryOrdering.order(for: nil) == EmotionCategoryOrdering.default)
    }

    @Test("order(for: .highlightLarge) matches the user-anchored ordering")
    func largeHighlightAnchor() {
        let order = EmotionCategoryOrdering.order(for: .highlightLarge)
        #expect(order == ["pride", "joy", "peace", "fear", "anger", "shame", "sadness"])
    }

    @Test("order(for: .lowlightMedium) matches the user-anchored ordering")
    func mediumLowlightAnchor() {
        let order = EmotionCategoryOrdering.order(for: .lowlightMedium)
        #expect(order == ["anger", "fear", "shame", "sadness", "peace", "pride", "joy"])
    }

    @Test("order(for: .lowlightLarge) matches the user-anchored ordering")
    func largeLowlightAnchor() {
        let order = EmotionCategoryOrdering.order(for: .lowlightLarge)
        #expect(order == ["sadness", "fear", "anger", "shame", "peace", "joy", "pride"])
    }
}
```

- [ ] **Step 2: Run — expect compile failure (Ordering type doesn't exist yet)**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/EmotionCategoryOrderingTests test 2>&1 | tail -20 && cd -
```
Expected: compile errors referencing `EmotionCategoryOrdering` (unresolved identifier).

- [ ] **Step 3: Implement `EmotionCategoryOrdering`**

Create `apps/ios/Pebbles/Features/Path/Models/EmotionCategoryOrdering.swift`:

```swift
import Foundation

/// Static curation data: the order in which the 7 emotion categories
/// surface in `EmotionPickerSheet`, indexed by the form's currently-selected
/// `Valence`.
///
/// The valence enum lives only in iOS, so this ordering deliberately lives
/// in iOS-only code rather than in the database. If a second client picks
/// up this UX later, the table-form is sketched in the spec.
///
/// Ordering rule (informally): own polarity first, opposite polarity last.
/// Within each polarity, the "peak" member leads at LARGE, the most
/// "subtle" member leads at SMALL, balanced at MEDIUM.
enum EmotionCategoryOrdering {

    struct Key: Hashable {
        let size: ValenceSizeGroup
        let polarity: ValencePolarity

        init(_ size: ValenceSizeGroup, _ polarity: ValencePolarity) {
            self.size = size
            self.polarity = polarity
        }
    }

    static let byValence: [Key: [String]] = [
        // HIGHLIGHTS — pleasant first
        Key(.large,  .highlight): ["pride",  "joy",     "peace",   "fear",    "anger",   "shame",   "sadness"],
        Key(.medium, .highlight): ["joy",    "pride",   "peace",   "fear",    "anger",   "shame",   "sadness"],
        Key(.small,  .highlight): ["peace",  "joy",     "pride",   "shame",   "sadness", "fear",    "anger"],

        // NEUTRALS — balanced, peace leads
        Key(.large,  .neutral):   ["peace",  "joy",     "pride",   "fear",    "anger",   "shame",   "sadness"],
        Key(.medium, .neutral):   ["peace",  "fear",    "joy",     "anger",   "pride",   "shame",   "sadness"],
        Key(.small,  .neutral):   ["peace",  "anger",   "joy",     "fear",    "pride",   "sadness", "shame"],

        // LOWLIGHTS — unpleasant first
        Key(.large,  .lowlight):  ["sadness","fear",    "anger",   "shame",   "peace",   "joy",     "pride"],
        Key(.medium, .lowlight):  ["anger",  "fear",    "shame",   "sadness", "peace",   "pride",   "joy"],
        Key(.small,  .lowlight):  ["shame",  "sadness", "fear",    "anger",   "peace",   "pride",   "joy"],
    ]

    /// Used when no valence is selected on the draft yet. Equal to Medium Neutral.
    static let `default`: [String] = ["peace", "fear", "joy", "anger", "pride", "shame", "sadness"]

    static func order(for valence: Valence?) -> [String] {
        guard let v = valence else { return `default` }
        return byValence[Key(v.sizeGroup, v.polarity)] ?? `default`
    }
}
```

- [ ] **Step 4: Regenerate Xcode project (new files)**

```bash
cd apps/ios && npm run generate && cd -
```

- [ ] **Step 5: Run tests — expect pass**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/EmotionCategoryOrderingTests test 2>&1 | tail -20 && cd -
```
Expected: all 8 tests pass.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/EmotionCategoryOrdering.swift \
        apps/ios/PebblesTests/EmotionCategoryOrderingTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): add valence-aware category ordering (#370)

EmotionCategoryOrdering maps each (ValenceSizeGroup, ValencePolarity)
cell to the 7 category slugs in display order. Tests verify structural
invariants (count, set equality, completeness) plus the three
user-anchored cells (LargeHighlight, MediumLowlight, LargeLowlight).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Localized name helpers for `EmotionWithPalette` and `EmotionCategory`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/Emotion+Localized.swift`

- [ ] **Step 1: Extend the file with two new extensions**

Replace the contents of `apps/ios/Pebbles/Features/Path/Models/Emotion+Localized.swift` with:

```swift
import Foundation

extension Emotion {
    /// Localized display name, keyed by slug. Falls back to `name` (the DB
    /// value, English) if no catalog entry exists — safe for new emotions
    /// added server-side before iOS catches up.
    var localizedName: String {
        let key = "emotion.\(slug).name"
        // NSLocalizedString(key:value:) is used instead of String(localized:defaultValue:)
        // because the `localized:` overload requires a StaticString (compile-time constant)
        // while our keys are built at runtime from the DB slug. The `value:` parameter
        // provides the same fallback semantics: when no catalog entry exists for `key`,
        // the `value` (the DB `name`) is returned as-is.
        return NSLocalizedString(key, value: name, comment: "")
    }
}

extension EmotionWithPalette {
    /// Localized display name for an emotion row coming out of the palette
    /// view. Same key pattern as `Emotion.localizedName` (`emotion.<slug>.name`).
    var localizedName: String {
        NSLocalizedString("emotion.\(slug).name", value: name, comment: "")
    }
}

extension EmotionCategory {
    /// Localized display name keyed off the category slug
    /// (`emotionCategory.<slug>.name`). Falls back to the DB `name` when the
    /// catalog has no entry — same fallback contract as the emotion helpers.
    var localizedName: String {
        NSLocalizedString("emotionCategory.\(slug).name", value: name, comment: "")
    }
}
```

- [ ] **Step 2: Verify build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -8 && cd -
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/Emotion+Localized.swift
git commit -m "$(cat <<'EOF'
feat(ios): localized name helpers for palette row + category (#370)

Mirrors the existing Emotion.localizedName pattern (NSLocalizedString
with DB-name fallback) for EmotionWithPalette (emotion.<slug>.name)
and EmotionCategory (emotionCategory.<slug>.name).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Localization strings, ReferenceSlugs, coverage tests

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`
- Modify: `apps/ios/Pebbles/Features/Path/Models/ReferenceSlugs.swift`
- Modify: `apps/ios/PebblesTests/LocalizationTests.swift`

- [ ] **Step 1: Add 7 category slugs to `ReferenceSlugs`**

Edit `apps/ios/Pebbles/Features/Path/Models/ReferenceSlugs.swift`. Add a new static array `emotionCategories` after `domains`:

```swift
import Foundation

/// Compile-time mirror of the emotion, category, and domain slugs in the
/// live Supabase tables. Snapshot taken 2026-04-21; categories added 2026-05-09.
///
/// The seed migration at
/// `packages/supabase/supabase/migrations/20260411000000_reference_tables.sql`
/// is NOT the authoritative source — reference rows have been added /
/// renamed via the Supabase dashboard since the initial seed. When a new
/// reference row is added server-side, this list AND the corresponding
/// `emotion.<slug>.name` / `emotionCategory.<slug>.name` / `domain.<slug>.name`
/// catalog entries MUST be updated in the same change — otherwise the coverage
/// test in `LocalizationTests` fails.
enum ReferenceSlugs {
    static let emotions: [String] = [
        "amazed", "amused", "angry", "annoyed", "anxious", "ashamed",
        "brave", "calm", "confident", "content", "disappointed",
        "discouraged", "disgusted", "drained", "embarrassed", "excited",
        "frustrated", "grateful", "guilty", "happy", "hopeful", "hopeless",
        "indifferent", "irritated", "jealous", "joyful", "lonely",
        "overwhelmed", "passionate", "peaceful", "proud", "relieved",
        "sad", "satisfied", "scared", "stressed", "surprised", "worried"
    ]

    static let emotionCategories: [String] = [
        "anger", "fear", "joy", "peace", "pride", "sadness", "shame"
    ]

    static let domains: [String] = [
        "community", "currentevents", "dating", "education", "family",
        "fitness", "friends", "health", "hobbies", "identity", "money",
        "partner", "selfcare", "spirituality", "tasks", "travel",
        "weather", "work"
    ]
}
```

- [ ] **Step 2: Add coverage tests for the category slugs in `LocalizationTests.swift`**

Edit `apps/ios/PebblesTests/LocalizationTests.swift`. Inside `LocalizationPatternCCoverageTests`, after the `everyDomainHasFrenchEntry` test, add:

```swift
@Test("every emotion category slug has an EN catalog entry distinct from DB fallback")
func everyEmotionCategoryHasEnglishEntry() {
    for slug in ReferenceSlugs.emotionCategories {
        let resolved = resolveKey(
            "emotionCategory.\(slug).name",
            locale: Locale(identifier: "en"),
            fallback: "__FALLBACK__"
        )
        #expect(
            resolved != "__FALLBACK__",
            "emotionCategory.\(slug).name missing from catalog in 'en'"
        )
    }
}

@Test("every emotion category slug has a FR catalog entry distinct from DB fallback")
func everyEmotionCategoryHasFrenchEntry() {
    for slug in ReferenceSlugs.emotionCategories {
        let resolved = resolveKey(
            "emotionCategory.\(slug).name",
            locale: Locale(identifier: "fr"),
            fallback: "__FALLBACK__"
        )
        #expect(
            resolved != "__FALLBACK__",
            "emotionCategory.\(slug).name missing from catalog in 'fr'"
        )
    }
}
```

Also, in `LocalizationPatternCTests`, add a fallback test for `EmotionCategory`:

```swift
@Test("EmotionCategory.localizedName falls back to name when slug has no catalog entry")
func emotionCategoryFallsBackToName() {
    let category = EmotionCategory(
        id: UUID(),
        slug: "not-a-real-slug-xyz",
        name: "Fallback Name",
        palette: EmotionPalette(
            primaryHex: "#000000FF",
            secondaryHex: "#000000FF",
            lightHex: "#FFFFFFFF",
            surfaceHex: "#0000001A"
        )!
    )
    #expect(category.localizedName == "Fallback Name")
}
```

- [ ] **Step 3: Run coverage tests — expect failure**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/LocalizationPatternCCoverageTests/everyEmotionCategoryHasEnglishEntry test 2>&1 | tail -10 && cd -
```
Expected: failure — catalog entries don't exist yet.

- [ ] **Step 4: Add catalog entries for 7 category names + sheet title**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode (or edit the JSON directly). Add the following 8 keys, each with `en` and `fr` localizations.

The `xcstrings` JSON format for a simple key looks like (existing keys provide the template):

```json
"emotionCategory.anger.name": {
  "extractionState": "manual",
  "localizations": {
    "en": { "stringUnit": { "state": "translated", "value": "Anger" } },
    "fr": { "stringUnit": { "state": "translated", "value": "Colère" } }
  }
}
```

Add all 8 (insert alphabetically among the existing keys — exact location doesn't matter functionally, but keep it sorted to match the file's convention):

| key | en | fr |
|---|---|---|
| `emotionCategory.anger.name` | Anger | Colère |
| `emotionCategory.fear.name` | Fear | Peur |
| `emotionCategory.joy.name` | Joy | Joie |
| `emotionCategory.peace.name` | Peace | Paix |
| `emotionCategory.pride.name` | Pride | Fierté |
| `emotionCategory.sadness.name` | Sadness | Tristesse |
| `emotionCategory.shame.name` | Shame | Honte |
| `Emotions` (sheet title) | Emotions | Émotions |

The `Emotions` key uses the literal string itself as the key (this is how `Text("Emotions")` extracts) — same shape as the table above.

For the safest editing experience, use Xcode: opening the catalog gives a tabular UI that prevents JSON-format mistakes.

- [ ] **Step 5: Run all the new tests — expect pass**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/LocalizationPatternCCoverageTests \
  -only-testing:PebblesTests/LocalizationPatternCTests \
  -only-testing:PebblesTests/LocalizationCatalogFileTests test 2>&1 | tail -25 && cd -
```
Expected: all tests pass — including the existing `everyEntryHasBothLocales` (which now also checks the new keys).

- [ ] **Step 6: Open the catalog in Xcode and verify states**

Per `apps/ios/CLAUDE.md`, manually verify in Xcode's Localizable.xcstrings UI:
- No row for the new keys is in `New` or `Stale` state.
- Both `en` and `fr` columns are populated for the 8 new keys.

(This step is unautomated — the test above catches missing values, but the New/Stale state check is a Xcode UI affordance.)

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings \
        apps/ios/Pebbles/Features/Path/Models/ReferenceSlugs.swift \
        apps/ios/PebblesTests/LocalizationTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): localize emotion category names + sheet title (#370)

Adds 7 emotionCategory.<slug>.name keys (en + fr), the Emotions sheet
title, an EmotionCategory entry on ReferenceSlugs, and coverage +
fallback tests mirroring the existing emotion/domain pattern.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Build `EmotionPickerSheet`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/EmotionPickerSheet.swift`

- [ ] **Step 1: Create the file**

Write `apps/ios/Pebbles/Features/Path/EmotionPickerSheet.swift`:

```swift
import SwiftUI

/// Two-level emotion picker presented over the pebble form.
///
/// Categories are derived from the cached `EmotionPaletteService` rows by
/// deduping on `categoryId`; section order is `EmotionCategoryOrdering.order(for:)`
/// driven by the form's currently-selected `Valence`. Selection is staged
/// locally — `Done` commits via `onSelected`; `Cancel` discards.
///
/// Tapping the currently-staged chip clears the selection (sets staged to nil)
/// so the user can deselect inside the sheet without backing out.
struct EmotionPickerSheet: View {
    let currentEmotionId: UUID?
    let valence: Valence?
    let onSelected: (UUID?) -> Void

    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(\.dismiss) private var dismiss
    @State private var stagedEmotionId: UUID?

    init(
        currentEmotionId: UUID?,
        valence: Valence?,
        onSelected: @escaping (UUID?) -> Void
    ) {
        self.currentEmotionId = currentEmotionId
        self.valence = valence
        self.onSelected = onSelected
        self._stagedEmotionId = State(initialValue: currentEmotionId)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 24) {
                    if groups.isEmpty {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 32)
                    } else {
                        ForEach(groups) { group in
                            section(for: group)
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Emotions")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        onSelected(stagedEmotionId)
                        dismiss()
                    }
                }
            }
            .pebblesScreen()
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    // MARK: - sections

    private struct CategoryGroup: Identifiable {
        let category: EmotionCategory
        let emotions: [EmotionWithPalette]
        var id: UUID { category.id }
    }

    /// Categories in valence-derived order; emotions inside each category
    /// sorted by their localized name (locale-aware).
    private var groups: [CategoryGroup] {
        let allRows = Array(palettes.byEmotionId.values)
        guard !allRows.isEmpty else { return [] }

        // Build category index: slug -> EmotionCategory. First row per category wins.
        var categoryBySlug: [String: EmotionCategory] = [:]
        for row in allRows where categoryBySlug[row.categorySlug] == nil {
            categoryBySlug[row.categorySlug] = EmotionCategory(
                id: row.categoryId,
                slug: row.categorySlug,
                name: row.categoryName,
                palette: row.palette
            )
        }

        // Group emotions by category slug.
        let emotionsBySlug = Dictionary(grouping: allRows, by: { $0.categorySlug })

        let order = EmotionCategoryOrdering.order(for: valence)
        return order.compactMap { slug in
            guard let category = categoryBySlug[slug],
                  let rows = emotionsBySlug[slug], !rows.isEmpty else {
                return nil
            }
            let sorted = rows.sorted { $0.localizedName.localizedCompare($1.localizedName) == .orderedAscending }
            return CategoryGroup(category: category, emotions: sorted)
        }
    }

    @ViewBuilder
    private func section(for group: CategoryGroup) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            header(for: group.category)

            LazyVGrid(
                columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)],
                spacing: 12
            ) {
                ForEach(group.emotions) { row in
                    chip(for: row, in: group.category)
                }
            }
        }
    }

    @ViewBuilder
    private func header(for category: EmotionCategory) -> some View {
        HStack(spacing: 6) {
            Image(systemName: "waveform.path.ecg")
                .foregroundStyle(category.palette.primary)
            Text(category.localizedName)
                .font(.caption2)
                .fontWeight(.semibold)
                .tracking(1.5)
                .textCase(.uppercase)
                .foregroundStyle(Color.pebblesMutedForeground)
        }
    }

    @ViewBuilder
    private func chip(for row: EmotionWithPalette, in category: EmotionCategory) -> some View {
        let isSelected = (row.id == stagedEmotionId)
        Button {
            stagedEmotionId = isSelected ? nil : row.id
        } label: {
            HStack(spacing: 8) {
                Text(row.emoji)
                Text(row.localizedName)
                    .font(.subheadline)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? category.palette.primary : category.palette.surface)
            .foregroundStyle(isSelected ? category.palette.light : Color.pebblesForeground)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(row.localizedName))
        .accessibilityValue(Text(category.localizedName))
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}

#Preview("nothing selected, no valence") {
    Color.clear.sheet(isPresented: .constant(true)) {
        EmotionPickerSheet(
            currentEmotionId: nil,
            valence: nil,
            onSelected: { _ in }
        )
    }
}
```

- [ ] **Step 2: Regenerate Xcode project**

```bash
cd apps/ios && npm run generate && cd -
```

- [ ] **Step 3: Build to verify**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10 && cd -
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/EmotionPickerSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): add EmotionPickerSheet (#370)

Two-level picker: category sections (ordered by EmotionCategoryOrdering
driven by the form's Valence) with a 2-column grid of emoji-prefixed
emotion chips. Selected chip uses category.primary/light; unselected uses
the alpha-baked surface tint. Staged selection — Done commits, Cancel
discards.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Wire `EmotionPickerSheet` into `PebbleFormView`; drop the inline emotion fetch

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`

- [ ] **Step 1: Replace inline `Picker` in `PebbleFormView` with a Button + sheet**

Edit `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`:

a) **Drop the `emotions: [Emotion]` field, init parameter, and assignment.**

Remove `let emotions: [Emotion]` from the struct fields (line 13).

Remove `emotions: [Emotion],` from the `init(...)` parameter list (line 46).

Remove `self.emotions = emotions` from the init body (line 60).

b) **Add `@Environment(EmotionPaletteService.self) private var palettes`** alongside the existing `@Environment(SupabaseService.self) private var supabase` (around line 42):

```swift
@Environment(SupabaseService.self) private var supabase
@Environment(EmotionPaletteService.self) private var palettes
```

c) **Add a new `@State private var showEmotionPicker = false`** alongside the other `@State` flags (around lines 38–40):

```swift
@State private var showPicker = false
@State private var showValencePicker = false
@State private var showEmotionPicker = false
@State private var selectedGlyph: Glyph?
```

d) **Replace the inline emotion `Picker`** (currently at lines 125–130 inside `Section("Mood")`):

Old:
```swift
Picker("Emotion", selection: $draft.emotionId) {
    Text("Choose…").tag(UUID?.none)
    ForEach(emotions) { emotion in
        Text(emotion.localizedName).tag(UUID?.some(emotion.id))
    }
}
.listRowBackground(Color.pebblesListRow)
```

New:
```swift
Button {
    showEmotionPicker = true
} label: {
    HStack(spacing: 12) {
        if let id = draft.emotionId, let row = palettes.byEmotionId[id] {
            Text(row.emoji)
                .font(.system(size: 28))
                .frame(width: 32, height: 32)
                .accessibilityHidden(true)
        } else {
            RoundedRectangle(cornerRadius: 6)
                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                .frame(width: 32, height: 32)
                .foregroundStyle(Color.pebblesMutedForeground)
        }
        Text("Emotion")
            .foregroundStyle(Color.pebblesForeground)
        Spacer()
        Text(emotionRowLabel)
            .foregroundStyle(Color.pebblesMutedForeground)
        Image(systemName: "chevron.right")
            .font(.caption)
            .foregroundStyle(.tertiary)
            .accessibilityHidden(true)
    }
}
.buttonStyle(.plain)
.accessibilityLabel("Emotion")
.accessibilityValue(
    draft.emotionId.flatMap { palettes.byEmotionId[$0] }.map { Text($0.localizedName) }
        ?? Text("Choose")
)
.listRowBackground(Color.pebblesListRow)
```

e) **Add a `.sheet(isPresented: $showEmotionPicker)` modifier** alongside the existing `.sheet` modifiers at the bottom of `body` (lines ~271–282):

```swift
.sheet(isPresented: $showPicker) {
    GlyphPickerSheet(
        currentGlyphId: draft.glyphId,
        onSelected: { glyphId in draft.glyphId = glyphId }
    )
}
.sheet(isPresented: $showValencePicker) {
    ValencePickerSheet(
        currentValence: draft.valence,
        onSelected: { picked in draft.valence = picked }
    )
}
.sheet(isPresented: $showEmotionPicker) {
    EmotionPickerSheet(
        currentEmotionId: draft.emotionId,
        valence: draft.valence,
        onSelected: { picked in draft.emotionId = picked }
    )
}
```

f) **Add the `emotionRowLabel` computed property** alongside the existing `glyphRowLabel` (around line 286):

```swift
private var emotionRowLabel: LocalizedStringResource {
    if let id = draft.emotionId, let row = palettes.byEmotionId[id] {
        // Bridge through String for the runtime-built localized name —
        // localizedName uses NSLocalizedString so it returns a plain String,
        // which is fine here since the LocalizedStringResource is consumed by Text.
        return LocalizedStringResource(stringLiteral: row.localizedName)
    }
    return "Choose…"
}
```

- [ ] **Step 2: Drop the `emotions` fetch + state from `CreatePebbleSheet`**

Edit `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`:

a) Remove `@State private var emotions: [Emotion] = []` (line 12).

b) Remove the `emotions:` argument from the `PebbleFormView(...)` invocation in `content` (line 113):

```swift
PebbleFormView(
    draft: $draft,
    domains: domains,
    souls: souls,
    collections: collections,
    saveError: saveError,
    showsPhotoSection: true,
    photoPickerPresented: $isPhotoPickerPresented
)
```

c) In `loadReferences()` (lines 215–257), remove the `emotionsQuery` async let, drop it from the await tuple, and stop assigning `self.emotions`. Result:

```swift
private func loadReferences() async {
    isLoadingReferences = true
    loadError = nil
    do {
        async let domainsQuery: [Domain] = supabase.client
            .from("domains")
            .select()
            .order("name")
            .execute()
            .value
        async let soulsQuery: [SoulWithGlyph] = supabase.client
            .from("souls")
            .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
            .order("name")
            .execute()
            .value
        async let collectionsQuery: [PebbleCollection] = supabase.client
            .from("collections")
            .select("id, name")
            .order("name")
            .execute()
            .value

        let (loadedDomains, loadedSouls, loadedCollections) =
            try await (domainsQuery, soulsQuery, collectionsQuery)

        self.domains = loadedDomains
        self.souls = loadedSouls
        self.collections = loadedCollections
        self.isLoadingReferences = false
    } catch {
        logger.error("reference load failed: \(error.localizedDescription, privacy: .private)")
        self.loadError = "Couldn't load the form data."
        self.isLoadingReferences = false
    }
}
```

- [ ] **Step 3: Drop the `emotions` fetch + state from `EditPebbleSheet`**

Edit `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`:

a) Remove `@State private var emotions: [Emotion] = []` (line 25).

b) Remove the `emotions:` argument from the `PebbleFormView(...)` invocation in `content` (line 123).

c) In `load()` (lines 143–~210), remove the `emotionsQuery` async let, drop it from the await tuple, and stop assigning `self.emotions`. The `emotion:emotions(id, slug, name)` projection inside `detailQuery` MUST stay — it loads the pebble's existing emotion for the form's initial state and is unrelated to the picker fetch.

Updated `load()` body (focused changes — keep the rest of the method intact):

```swift
async let detailQuery: PebbleDetail = supabase.client
    .from("pebbles")
    .select("""
        id, name, description, happened_at, intensity, positiveness, visibility,
        render_svg, render_version, glyph_id,
        emotion:emotions(id, slug, name),
        pebble_domains(domain:domains(id, slug, name)),
        pebble_souls(soul:souls(id, name, glyph_id, glyphs(id, name, strokes, view_box))),
        collection_pebbles(collection:collections(id, name)),
        snaps(id, storage_path, sort_order)
    """)
    .eq("id", value: pebbleId)
    .single()
    .execute()
    .value

async let domainsQuery: [Domain] = supabase.client
    .from("domains")
    .select()
    .order("name")
    .execute()
    .value
async let soulsQuery: [SoulWithGlyph] = supabase.client
    .from("souls")
    .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
    .order("name")
    .execute()
    .value
async let collectionsQuery: [PebbleCollection] = supabase.client
    .from("collections")
    .select("id, name")
    .order("name")
    .execute()
    .value

let (detail, loadedDomains, loadedSouls, loadedCollections) =
    try await (detailQuery, domainsQuery, soulsQuery, collectionsQuery)

self.domains = loadedDomains
self.souls = loadedSouls
self.collections = loadedCollections
self.draft = PebbleDraft(from: detail)
self.renderSvg = detail.renderSvg
self.strokeColor = palettes.palette(for: detail.emotion.id)?
    .strokeHex(for: colorScheme) ?? Color.pebblesAccentHex
self.sizeGroup = detail.valence.sizeGroup
```

The `// swiftlint:disable:next function_body_length` comment on `load()` may no longer be needed (the function got shorter). Remove it if SwiftLint accepts the shorter body.

- [ ] **Step 4: Build to verify**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -15 && cd -
```
Expected: BUILD SUCCEEDED. If you see `extra argument 'emotions' in call`, you missed dropping it from one of the call sites. If you see `cannot find 'emotions'`, you left a reference to the deleted state property somewhere.

- [ ] **Step 5: Run the full test suite**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test 2>&1 | tail -25 && cd -
```
Expected: all tests pass (catalog coverage, decoding, ordering, etc.).

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleFormView.swift \
        apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift \
        apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): wire EmotionPickerSheet into pebble form (#370)

Replaces the flat emotion Picker in PebbleFormView with a Button row
that presents EmotionPickerSheet. The row reads selected-emotion emoji
and localized name from EmotionPaletteService cache. Drops the inline
from("emotions") fetch in CreatePebbleSheet and EditPebbleSheet — the
picker reads from the palette cache instead.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Manual smoke test in the simulator

**Files:** none

This step verifies UI correctness, which the test suite cannot. Run it before opening the PR.

- [ ] **Step 1: Boot the simulator and run the app**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' \
  -configuration Debug build && cd -
```

Then in Xcode: open `apps/ios/Pebbles.xcodeproj`, select the iPhone 15 simulator, press ⌘R.

- [ ] **Step 2: Verify the create flow**

1. From the home tab, tap to create a new pebble.
2. **Confirm**: the "Mood" section's Emotion row shows a dashed placeholder square + "Choose…" before any selection.
3. Tap the Emotion row. **Confirm**: `EmotionPickerSheet` opens with sections in the **default order** (peace, fear, joy, anger, pride, shame, sadness — Medium Neutral) since no valence is set.
4. Tap an emotion (e.g. `Anxious` under Fear). **Confirm**: the chip fills with the Fear primary color and the text adopts the light tint.
5. Tap `Done`. **Confirm**: sheet dismisses; the form's Emotion row now shows the 😰 emoji + "Anxious" on the right.
6. Tap the Valence row, pick `Highlight — large`, save it. **Confirm**: the form's valence shows `Highlight — large`.
7. Tap the Emotion row again. **Confirm**: sections reorder to **pride, joy, peace, fear, anger, shame, sadness** (Large Highlight). The previously-staged "Anxious" is highlighted under Fear.
8. Tap `Cancel`. **Confirm**: sheet dismisses; the form still shows "Anxious".

- [ ] **Step 3: Verify deselect-by-retap**

1. Open the picker, tap the currently-staged emotion. **Confirm**: it deselects (no chip is highlighted).
2. Tap `Done`. **Confirm**: the form Emotion row reverts to dashed-square + "Choose…".

- [ ] **Step 4: Verify the edit flow**

1. Open an existing pebble for editing.
2. Tap the Emotion row. **Confirm**: the picker opens with that pebble's emotion staged (highlighted chip in its category section).
3. Pick a different emotion under a different category. Tap `Done`. **Confirm**: the row updates to the new emotion's emoji + name.
4. Save the pebble. Re-open it. **Confirm**: the new emotion persisted.

- [ ] **Step 5: Verify dark mode**

In Simulator: Features → Toggle Appearance (or ⌘⇧A). Open the picker. **Confirm**: chips render with the dark-mode surface tint, selected chip uses the dark-mode primary, section labels render in the muted-foreground gray. Compare against the dark-mode mockup in #370.

- [ ] **Step 6: Verify French locale**

Stop the app, change the simulator language to French (Settings → General → Language & Region), relaunch. Open the picker. **Confirm**: section headers say `COLÈRE`, `PEUR`, `JOIE`, etc.; emotion names render in French (`Anxieux`, `Joyeux`, etc.); Cancel/Done localized; title says "Émotions".

- [ ] **Step 7: Note any regressions**

If anything in the smoke test fails, fix it and re-run the relevant steps. Do NOT proceed to the PR step until all six checks pass.

---

## Task 10: Lint, sanity-check uses of `emotions`, prepare PR

**Files:** none

- [ ] **Step 1: Lint the iOS workspace**

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | tail -20
```
Expected: no errors. If SwiftLint warns about `function_body_length` on `EditPebbleSheet.load()` after the simplification, the disable comment may need re-adding (or the function may now fit under the threshold — leave the comment off in that case).

- [ ] **Step 2: Grep for stale references**

```bash
grep -rn "emotions: \[Emotion\]" apps/ios/Pebbles 2>/dev/null
```
Expected: no matches.

```bash
grep -rn "from(\"emotions\")" apps/ios/Pebbles 2>/dev/null
```
Expected: zero or one match (only `EditPebbleSheet`'s `emotion:emotions(id, slug, name)` projection is allowed; no top-level `from("emotions").select(...)` chains should remain).

- [ ] **Step 3: Confirm web/admin TS usage didn't break**

```bash
grep -rn 'from("emotions")\|emotions:' apps/web apps/admin packages 2>/dev/null | grep -v node_modules | grep -v "types/database.ts" | head -30
```
Review the matches — any TypeScript that constructs a literal `emotions` row would now need an `emoji` field. Read-only consumers (`from("emotions").select(...)`) are unaffected. If a write site shows up, flag it before opening the PR; the spec does not commit to fixing web usage in this PR.

- [ ] **Step 4: Push the branch**

```bash
git push -u origin feat/370-ios-emotion-picker-sheet
```

- [ ] **Step 5: Open the PR**

Determine labels (per project memory: PR resolving an issue inherits the issue's labels with `bug` → `fix`):

```bash
gh issue view 370 --json labels,milestone -q '{labels: [.labels[].name], milestone: .milestone.title}'
```

Confirm with the user before opening the PR. Then:

```bash
gh pr create --title "feat(ios): emotion picker sheet (#370)" --body "$(cat <<'EOF'
Resolves #370.

## Summary
- Replaces the flat emotion `Picker` in `PebbleFormView` with `EmotionPickerSheet`: 7 category sections, each a 2-column grid of emoji-prefixed chips. Selected chip uses the category's primary color; unselected uses the alpha-baked surface tint.
- Section order is driven by the form's selected `Valence` via `EmotionCategoryOrdering` (iOS-side static curation; 9 valence cells + a default).
- Picker reads from the existing `EmotionPaletteService` cache. The inline `from("emotions")` fetches in `CreatePebbleSheet` and `EditPebbleSheet` are removed.
- DB migration `20260509000002_emotions_picker_data.sql` adds `emotions.emoji` (NOT NULL, seeded), realigns legacy `emotions.color` to category primary so shipped iOS versions render acceptably, and refreshes `v_emotions_with_palette` to expose `emoji`.

## Key files
- `packages/supabase/supabase/migrations/20260509000002_emotions_picker_data.sql`
- `apps/ios/Pebbles/Features/Path/EmotionPickerSheet.swift` (new)
- `apps/ios/Pebbles/Features/Path/Models/EmotionCategory.swift` (new)
- `apps/ios/Pebbles/Features/Path/Models/EmotionCategoryOrdering.swift` (new)
- `apps/ios/Pebbles/Features/Path/Models/EmotionWithPalette.swift` (+ emoji)
- `apps/ios/Pebbles/Features/Path/Models/Emotion+Localized.swift` (+ category/palette helpers)
- `apps/ios/Pebbles/Features/Path/PebbleFormView.swift` (Picker → Button + sheet)
- `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` and `EditPebbleSheet.swift` (drop inline fetch)
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` (+ 7 category names + sheet title; en + fr)
- `apps/ios/PebblesTests/EmotionCategoryOrderingTests.swift` (new)

## Test plan
- [ ] Unit tests pass: `xcodebuild test` (decoder, ordering, localization coverage)
- [ ] Create flow: pick emotion → save pebble → emotion persists
- [ ] Edit flow: existing pebble's emotion is staged in the picker
- [ ] Section order responds to selected valence
- [ ] Tap-twice deselects inside the sheet
- [ ] Dark mode renders correctly
- [ ] French locale renders category + emotion names

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 6: Apply labels and milestone**

After the PR is created, apply labels and milestone matching the issue (per project memory). Use `gh pr edit <PR#> --add-label … --milestone …`. Confirm with the user before applying if anything is ambiguous.

---

## Self-review notes

- **Spec coverage:** Each spec section maps to at least one task above. Migration → Task 1. iOS architecture (file layout, `EmotionWithPalette` extension, `EmotionCategory`, `EmotionCategoryOrdering`, picker sheet, form integration) → Tasks 2–8. Localization (xcstrings keys, ReferenceSlugs, helpers) → Tasks 5–6. Tests → embedded in Tasks 2, 4, 6. Rollout → Tasks 1, 8–10. Risks (cache miss, TS ripple, old clients, static tests) → mitigated by the empty-cache `ProgressView` branch in Task 7, the TS sanity grep in Task 10, the legacy color UPDATE in Task 1, and the structural-only test design in Task 4.
- **No placeholders.** Every step has the literal SQL/Swift/CLI invocation.
- **TDD discipline.** Tasks 2 and 4 follow the failing-test-first cycle. Task 6 tests catalog coverage. The picker UI itself (Task 7) and form integration (Task 8) are validated by the manual smoke test in Task 9 — UI correctness here can't be unit-tested without a snapshot or UI-test target the project doesn't have yet (per `apps/ios/CLAUDE.md`: "No UI tests for now").
- **Type consistency.** `EmotionCategoryOrdering.Key(_:_:)` initializer matches both the test (Step 1 of Task 4) and the implementation (Step 3). `EmotionWithPalette.localizedName`, `EmotionCategory.localizedName`, and `Emotion.localizedName` all return `String` and use `NSLocalizedString` (consistent fallback). Picker reads `palettes.byEmotionId` — the actual property name on `EmotionPaletteService`.
