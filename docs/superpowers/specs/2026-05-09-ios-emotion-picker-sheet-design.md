# iOS emotion picker sheet — design

**Issue:** [#370](https://github.com/Bohns/pbbls/issues/370) — `[Feat] iOS emotion picker sheet (category → emotion)`
**Date:** 2026-05-09
**Scope:** iOS-only picker UX + the small DB additions (emoji, legacy-color realignment) that back it. No web changes.

## Goal

Replace the flat inline `Picker("Emotion", selection: $draft.emotionId)` in `PebbleFormView` with a presented sheet that exposes the two-level structure shipped in #367 (`emotion_categories` × `emotions`). Each section is a category, each chip is an emotion; selecting a chip writes back to `pebbles.emotion_id`. Sections are ordered by the user's currently-selected `Valence` so the most contextually-relevant categories surface first.

## Scope decisions (settled in brainstorming)

- **One DB migration in this PR.** Three additive/idempotent changes bundled (emoji column + populate, legacy `emotions.color` realignment, view refresh). No two-phase rollout because every value is derivable from existing rows.
- **Section ordering is iOS-side static curation data.** A `[ValenceKey: [CategorySlug]]` map. Not a DB column. The valence enum lives only in iOS; modeling it server-side would cross a layer boundary that nothing else needs.
- **Picker reads from `EmotionPaletteService` (already in env).** The inline `from("emotions")` fetches in `CreatePebbleSheet` and `EditPebbleSheet` go away. Single source of truth.
- **Sheet uses staged selection.** `Cancel`/`Done` toolbar. Tapping a chip stages locally; `Done` commits. Different from the Valence sheet (live commit) because Emotion has 38 options and a "preview before committing" affordance helps.
- **Category section icon is `waveform.path.ecg`** tinted with the category's primary color. No custom asset work.
- **No multi-select.** One emotion per pebble. Schema unchanged.

## Schema

One migration: `packages/supabase/supabase/migrations/20260509000002_emotions_picker_data.sql`.

### Change A — `emotions.emoji text not null`

Three statements:

```sql
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
```

### Change B — Legacy `emotions.color` realignment

```sql
update public.emotions e
set color = substr(c.primary_color, 1, 7)  -- '#RRGGBBAA' → '#RRGGBB'
from public.emotion_categories c
where e.category_id = c.id;
```

Resolves the issue's open question for shipped iOS versions still reading `emotions.color` directly. New clients ignore the column.

### Change C — Refresh `v_emotions_with_palette` to include `emoji`

```sql
create or replace view public.v_emotions_with_palette as
select
  e.id, e.slug, e.name, e.color, e.emoji,
  c.id as category_id, c.slug as category_slug, c.name as category_name,
  c.primary_color, c.secondary_color, c.light_color, c.surface_color
from public.emotions e
join public.emotion_categories c on c.id = e.category_id;
```

### Type regeneration

After applying the migration:

```
npm run db:types --workspace=packages/supabase
git add packages/supabase/types/database.ts
```

## iOS architecture

### Data flow

```
splash (RootView.task)
  └── EmotionPaletteService.load()
        └── from("v_emotions_with_palette") → [EmotionWithPalette]
              cached in byEmotionId: [UUID: EmotionWithPalette]

PebbleFormView "Mood" section
  └── Button row → presents EmotionPickerSheet
        └── reads palettes.byEmotionId from @Environment
        └── groups + orders via EmotionCategoryOrdering.order(for: draft.valence)
```

The inline `from("emotions")` fetches in `CreatePebbleSheet.swift:219` and `EditPebbleSheet.swift:163` are removed. The `emotions:` parameter on `PebbleFormView` is removed. The `@State private var emotions: [Emotion]` declarations and their populate-on-load code paths are removed.

`EditPebbleSheet`'s detail-decode select `emotion:emotions(id, slug, name)` is left intact — it's a different code path (loading the pebble's existing emotion for the form's initial state).

### File layout

```
apps/ios/Pebbles/Features/Path/
  EmotionPickerSheet.swift                   ← new
  Models/
    EmotionCategory.swift                    ← new
    EmotionCategoryOrdering.swift            ← new
    Emotion+Localized.swift                  ← extend with category localization helper
    EmotionWithPalette.swift                 ← gains emoji: String
    Emotion.swift                            ← unchanged
  PebbleFormView.swift                       ← drop emotions param; replace Picker with Button row
  CreatePebbleSheet.swift                    ← drop emotions State + fetch
  EditPebbleSheet.swift                      ← drop emotions State + fetch
```

### `EmotionWithPalette` — add `emoji`

One additional decoded field:

```swift
let emoji: String
// CodingKeys gains `case emoji`
// init decodes: self.emoji = try container.decode(String.self, forKey: .emoji)
```

The non-null guarantee comes from the migration (Change A's final `SET NOT NULL`). The existing decoder pattern (throw `DecodingError` on missing required, log and skip in the service) handles the unlikely case of a pre-migration row.

### `EmotionCategory.swift`

Plain value type for grouping:

```swift
struct EmotionCategory: Identifiable, Hashable {
    let id: UUID
    let slug: String
    let name: String          // raw DB name; UI uses `localizedName`
    let palette: EmotionPalette
}

extension EmotionCategory {
    /// Reads `emotionCategory.<slug>.name` from the catalog with DB `name` fallback.
    var localizedName: LocalizedStringResource { ... }
}
```

Built from `EmotionWithPalette` rows by deduping on `categoryId` inside the picker sheet — no separate fetch.

### `EmotionCategoryOrdering.swift`

Static curation data:

```swift
enum EmotionCategoryOrdering {
    struct Key: Hashable {
        let size: ValenceSizeGroup
        let polarity: ValencePolarity
        init(_ s: ValenceSizeGroup, _ p: ValencePolarity) { size = s; polarity = p }
    }

    static let byValence: [Key: [String]] = [
        // HIGHLIGHTS — pleasant first
        Key(.large,  .highlight): ["pride", "joy", "peace",       "fear", "anger", "shame",   "sadness"],
        Key(.medium, .highlight): ["joy",   "pride", "peace",     "fear", "anger", "shame",   "sadness"],
        Key(.small,  .highlight): ["peace", "joy",   "pride",     "shame","sadness","fear",   "anger"],

        // NEUTRALS — balanced, peace leads
        Key(.large,  .neutral):   ["peace", "joy",   "pride",     "fear", "anger", "shame",   "sadness"],
        Key(.medium, .neutral):   ["peace", "fear",  "joy",       "anger","pride", "shame",   "sadness"],
        Key(.small,  .neutral):   ["peace", "anger", "joy",       "fear", "pride", "sadness", "shame"],

        // LOWLIGHTS — unpleasant first
        Key(.large,  .lowlight):  ["sadness","fear",  "anger",    "shame","peace", "joy",     "pride"],
        Key(.medium, .lowlight):  ["anger",  "fear",  "shame",    "sadness","peace","pride",  "joy"],
        Key(.small,  .lowlight):  ["shame",  "sadness","fear",    "anger","peace", "pride",   "joy"],
    ]

    static let `default`: [String] = ["peace", "fear", "joy", "anger", "pride", "shame", "sadness"]

    static func order(for valence: Valence?) -> [String] {
        guard let v = valence else { return `default` }
        return byValence[Key(v.sizeGroup, v.polarity)] ?? `default`
    }
}
```

The exact orderings for the user's three anchors (Large Highlight, Medium Lowlight, Large Lowlight) match what they specified during brainstorming. The other six were derived using the rule **"own polarity first, opposite polarity last; within polarity, peak member leads at LARGE, balanced at MEDIUM, subtle leads at SMALL"**. The default = Medium Neutral.

### `EmotionPickerSheet.swift`

```swift
struct EmotionPickerSheet: View {
    let currentEmotionId: UUID?
    let valence: Valence?
    let onSelected: (UUID?) -> Void

    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(\.dismiss) private var dismiss
    @State private var stagedEmotionId: UUID?

    init(currentEmotionId: UUID?, valence: Valence?, onSelected: @escaping (UUID?) -> Void) {
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
                        ProgressView().frame(maxWidth: .infinity).padding(.vertical, 32)
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

    /// Categories in valence-derived order, each with its emotions sorted alphabetically.
    private var groups: [CategoryGroup] { ... }
}
```

**Chip** (single emotion):

```swift
private func chip(for row: EmotionWithPalette, in category: EmotionCategory) -> some View {
    let isSelected = (row.id == stagedEmotionId)
    return Button {
        stagedEmotionId = isSelected ? nil : row.id
    } label: {
        HStack(spacing: 8) {
            Text(row.emoji)
            Text(row.localizedName)
                .font(.subheadline)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(isSelected ? category.palette.primary : category.palette.surface)
        .foregroundStyle(isSelected ? category.palette.light : Color.pebblesForeground)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
    .buttonStyle(.plain)
    .accessibilityLabel(Text(row.localizedName))
    .accessibilityValue(Text(category.localizedName))
    .accessibilityAddTraits(isSelected ? [.isSelected] : [])
}
```

The `localizedName` helpers on both `EmotionWithPalette` and `EmotionCategory` live in `Emotion+Localized.swift`, mirroring the existing `Emotion.localizedName` pattern (catalog hit → catalog string; miss → DB `name` fallback). Keys are `emotion.<slug>.name` and `emotionCategory.<slug>.name` respectively.

**Section header** (category):

```swift
private func header(for category: EmotionCategory) -> some View {
    HStack(spacing: 6) {
        Image(systemName: "waveform.path.ecg")
            .foregroundStyle(category.palette.primary)
        Text(category.localizedName)
            .font(.caption2).fontWeight(.semibold)
            .tracking(1.5).textCase(.uppercase)
            .foregroundStyle(Color.pebblesMutedForeground)
    }
}
```

### Form integration

`PebbleFormView`'s "Mood" section: replace the inline `Picker` with a `Button` row that mirrors the Valence row's pattern.

```swift
Button {
    showEmotionPicker = true
} label: {
    HStack(spacing: 12) {
        if let id = draft.emotionId, let row = palettes.byEmotionId[id] {
            Text(row.emoji)
        } else {
            RoundedRectangle(cornerRadius: 6)
                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                .frame(width: 32, height: 32)
                .foregroundStyle(Color.pebblesMutedForeground)
        }
        Text("Emotion")
            .foregroundStyle(Color.pebblesForeground)
        Spacer()
        Text(emotionRowLabel)            // localized name or "Choose…"
            .foregroundStyle(Color.pebblesMutedForeground)
        Image(systemName: "chevron.right")
            .font(.caption).foregroundStyle(.tertiary)
    }
}
.buttonStyle(.plain)
.listRowBackground(Color.pebblesListRow)
.sheet(isPresented: $showEmotionPicker) {
    EmotionPickerSheet(
        currentEmotionId: draft.emotionId,
        valence: draft.valence,
        onSelected: { picked in draft.emotionId = picked }
    )
}
```

The `emotions: [Emotion]` parameter on `PebbleFormView`'s init is removed. Both call sites (`CreatePebbleSheet`, `EditPebbleSheet`) drop the `@State private var emotions` and the corresponding fetch in their `.task` loaders.

## Localization

### New keys in `Pebbles/Resources/Localizable.xcstrings`

| key | en | fr |
|---|---|---|
| `emotionCategory.anger.name` | Anger | Colère |
| `emotionCategory.fear.name` | Fear | Peur |
| `emotionCategory.joy.name` | Joy | Joie |
| `emotionCategory.peace.name` | Peace | Paix |
| `emotionCategory.pride.name` | Pride | Fierté |
| `emotionCategory.sadness.name` | Sadness | Tristesse |
| `emotionCategory.shame.name` | Shame | Honte |
| sheet title `Emotions` | Emotions | Émotions |

`Cancel` and `Done` already exist in the catalog and are reused.

### `ReferenceSlugs`

Add the 7 category slugs (`anger`, `fear`, `joy`, `peace`, `pride`, `sadness`, `shame`). The 38 emotion slugs already exist.

### `Emotion+Localized.swift`

Extend with helpers for `EmotionCategory` and `EmotionWithPalette`, both following the same catalog-hit-with-DB-fallback pattern as the existing `Emotion.localizedName`:

```swift
extension EmotionWithPalette {
    /// Reads `emotion.<slug>.name`; falls back to DB `name`.
    var localizedName: LocalizedStringResource { ... }
}

extension EmotionCategory {
    /// Reads `emotionCategory.<slug>.name`; falls back to DB `name`.
    var localizedName: LocalizedStringResource { ... }
}
```

## Tests

`apps/ios/PebblesTests/EmotionCategoryOrderingTests.swift` (new) — Swift Testing:

- Every `Key` cell maps to exactly 7 slugs.
- Every `Key` cell contains the same 7 unique slugs (set equality).
- `default` has 7 unique slugs (same set).
- `order(for:)` returns `default` for nil valence.
- `order(for:)` returns the exact specified ordering for the three user-anchored cells.

~30 lines.

## Rollout

Single PR (`feat/370-ios-emotion-picker-sheet`). Order of work in the branch:

1. Migration `20260509000002_emotions_picker_data.sql`.
2. Regenerate `packages/supabase/types/database.ts`, commit.
3. iOS models: extend `EmotionWithPalette`; add `EmotionCategory.swift`, `EmotionCategoryOrdering.swift`, `Emotion+Localized.swift` extension.
4. `EmotionPickerSheet.swift`.
5. `PebbleFormView` integration; drop `emotions:` param + fetches in both pebble sheets.
6. Localization: add 7 category keys + sheet title (en + fr); add to `ReferenceSlugs`.
7. `EmotionCategoryOrderingTests`.
8. `xcodegen generate`.
9. Open `Localizable.xcstrings` in Xcode — confirm no `New`/`Stale`, both locales populated for new rows.

## Risks

- **Cache miss on sheet open.** If `EmotionPaletteService` hasn't loaded yet (network hiccup), the sheet renders a `ProgressView` row. Splash gates on the load so this is rare. No retry inside the sheet — it'll succeed on next app launch.
- **TypeScript ripple from `emotions.emoji`.** The generated type tightens to `string`. Any TS consumer that constructs an `emotions` row (web admin tooling, fixtures) needs to supply `emoji`. Pre-PR grep for `from("emotions").insert` and similar confirms scope.
- **Old iOS clients.** They keep reading `from("emotions")` and `emotions.color`. The legacy-color UPDATE realigns those colors to the category's primary, so old apps render acceptably until users update. New view column (`emoji`) is invisible to them.
- **Sheet ordering tests are static.** They don't catch designer-driven reordering as a regression — by design, since the test exists to validate structure (7 unique slugs per cell), not the specific order.

## Out of scope (explicit)

- Web emotion picker. Web continues using its current implementation (no `emoji` consumption, no valence-based ordering).
- Multi-select emotion. Schema unchanged.
- "Currently-selected emotion's category floats to top" override. Additive on top of `EmotionCategoryOrdering`; defer to a follow-up if desired.
- Per-emotion (within-category) custom ordering. Today: alphabetical via `localizedName`.
- Admin UI for editing emojis or palette colors. Editing happens in Supabase Studio.
- Removing `emotions.color`. Possible only after the last shipped iOS version reading the column is deprecated.
- Removing the `emotion:emotions(id, slug, name)` decode in `EditPebbleSheet` — different code path; requires a separate refactor.

## References

- Issue: [#370](https://github.com/Bohns/pbbls/issues/370)
- Parent issue: [#366](https://github.com/Bohns/pbbls/issues/366) (palette designs, mockups)
- Schema PR: [#367](https://github.com/Bohns/pbbls/pull/367)
- Schema spec: `docs/superpowers/specs/2026-05-06-emotion-categories-palettes-design.md`
- Existing Valence picker (sheet pattern reference): `apps/ios/Pebbles/Features/Path/ValencePickerSheet.swift`
- Palette service: `apps/ios/Pebbles/Services/EmotionPaletteService.swift`
