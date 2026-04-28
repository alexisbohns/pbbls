# iOS — Single pebble read view

**Issue:** [#329](https://github.com/) — `[Feat] Single pebble read view`
**Milestone:** M25 · Improved core UX
**Labels:** `feat`, `ios`, `ui`
**Date:** 2026-04-28

## Context

Today, tapping an existing pebble in the path list opens `EditPebbleSheet` — the form. Read and edit are the same surface. After creating a new pebble, `PebbleDetailSheet` shows a minimal post-create reveal (render + name + description + date + domains) that does not match the design intent of a "beautiful reading view".

This spec replaces both surfaces with a single, designed read view used for path-tap *and* post-create reveal. The edit form (`EditPebbleSheet`) becomes accessible only via an Edit button inside the read view.

## Goals

- A single `PebbleDetailSheet` rendering a designed read view of a pebble.
- Path-list tap → read view (no longer the edit form).
- Post-create reveal → same read view.
- Edit button inside the read view stacks the existing `EditPebbleSheet`. Save refreshes the read view in place.
- Optional content (picture, description, souls, collections) hides when empty; emotion is always shown; domain falls back to a dashed-empty row when unset.
- Domains and collections render as a single comma-listed row each. Souls render as one row per soul, with the soul's glyph as the icon (fallback `person.fill`).

## Non-goals

- Photo tap-to-zoom viewer (separate follow-up issue).
- A delete button inside the read view (delete remains on the path-list swipe).
- Multi-select for domain or soul in the form (out of scope here).

## File layout

New folder: `apps/ios/Pebbles/Features/Path/Read/`

| File | Role |
| --- | --- |
| `PebbleReadView.swift` | Pure UI body of the read view: `ScrollView` of sections, takes a `PebbleDetail` + a fetched `[Soul: Glyph?]` lookup. No data calls. |
| `PebbleReadHeader.swift` | `PebbleRenderView` + Ysabeau title + uppercase tracked date label. |
| `PebbleReadPicture.swift` | Rounded full-width image respecting natural aspect ratio. Wraps `SnapImageView`. Hidden when no snap. |
| `PebbleMetadataRow.swift` | The boxed-icon + label row, with three style variants. Reused by emotion, domain, collections, and each soul. |
| `PebblePrivacyBadge.swift` | Custom rounded lock+label badge for the top bar (border, no fill). |

`apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift` is rewritten to:
- Keep the load/error/loading scaffolding it already has.
- Extend its SELECT to embed soul glyph strokes (see "Data" below).
- Render `PebbleReadView` instead of the current ad-hoc body.
- Own the `isPresentingEdit` state and the stacked `EditPebbleSheet` sheet.
- Take an optional `onPebbleUpdated: (() -> Void)?` callback so `PathView` can refetch the list after the user edits.

## Layout (`PebbleReadView`)

`ScrollView` on `Color.pebblesBackground` (already light/dark aware via `pebblesScreen()`). Vertical stack, ~24pt section spacing, 16pt horizontal padding.

1. **Top bar** — placed in the `NavigationStack` toolbar of `PebbleDetailSheet`, not in scroll content:
   - Leading: `PebblePrivacyBadge` rendering `lock.fill` + `Visibility.label` ("Private" / "Public").
   - Trailing: native `Button("Edit") { isPresentingEdit = true }`.
2. **Header** (`PebbleReadHeader`):
   - `PebbleRenderView(svg: detail.renderSvg!, strokeColor: detail.emotion.color)` (rendered when `renderSvg` is non-nil; create/edit flows guarantee population by the time the read view appears, so no placeholder needed).
   - Title: `detail.name` in `Font.custom("<PostScript name of Ysabeau SemiBold>", size: 34)`. Implementation step verifies the PostScript name and documents it next to the call site.
   - Date label: `detail.happenedAt` formatted via `Date.FormatStyle` (locale-aware), example output `MON, MAR 12, 2026 · 2:32 PM`. Rendered with `.textCase(.uppercase)`, `.tracking(1.2)`, `.font(.caption)`, `.foregroundStyle(.secondary)`.
3. **Picture** (`PebbleReadPicture`) — rendered only if `detail.snaps.first` exists. Rounded corners, full width, natural aspect ratio.
4. **Metadata block** — vertical stack of `PebbleMetadataRow`:
   - **Emotion** — always rendered. `style: .emotion(color: detail.emotion.color)`. Icon: a single shared SF Symbol for v1 — `heart.fill`. (A per-emotion icon mapping is deferred; the differentiation today comes from the emotion's color filling the icon box.) Label: `detail.emotion.localizedName`.
   - **Domain** — always rendered. If `detail.domains` is non-empty, `style: .set` and label is `detail.domains.map(\.localizedName).joined(separator: ", ")`. If empty, `style: .unset` and label is the localized "No domain" placeholder.
   - **Collections** — rendered only when `detail.collections` is non-empty. `style: .set`, label is `detail.collections.map(\.name).joined(separator: ", ")`.
   - **Souls** — rendered only when `detail.souls` is non-empty. One row per soul. `style: .set`. Icon is the soul's glyph (`Icon.glyph(...)`) when `glyph_id` is set and the embedded glyph is non-nil; otherwise `Icon.system("person.fill")`. Label is `soul.name`.
5. **Description** — rendered only when `detail.description` is non-empty. `Text(...)` styled `.system(size: 17, weight: .regular, design: .serif)`, primary foreground.

## `PebbleMetadataRow`

```swift
struct PebbleMetadataRow: View {
    enum Icon { case system(String); case glyph(Glyph) }
    enum Style { case unset; case set; case emotion(color: Color) }

    let icon: Icon
    let label: LocalizedStringResource
    let style: Style
}
```

Visual states:
- `.unset` — icon box: rounded square (radius 8pt, ~36×36pt), dashed border in muted foreground, no fill, muted icon. Label in muted foreground.
- `.set` — icon box: same shape, light blush fill, no border, primary-tinted icon. Label in primary foreground. Uses `Color.pebblesAccentSoft`; the implementation step checks `Color+Pebbles.swift` and adds the token (with light/dark values in the asset catalog) if it does not already exist.
- `.emotion(color)` — icon box: filled with the emotion's hex color, white icon. Label in primary foreground.

Icon size ~18pt. Layout: HStack(spacing: 12) of icon-box + label, leading-aligned, no row chrome (no border, no background on the row itself — the icon box is the only visible container).

Accessibility: `.accessibilityElement(children: .combine)`; `.accessibilityLabel` = the label; `.accessibilityValue` = a short description of the style ("not set" for `.unset`; nothing for `.set` and `.emotion`).

## Edit transition (stacked sheets)

In `PebbleDetailSheet`:

```swift
@State private var isPresentingEdit = false

.sheet(isPresented: $isPresentingEdit) {
    EditPebbleSheet(pebbleId: pebbleId, onSaved: {
        Task { await load() }       // refresh in-place
        onPebbleUpdated?()          // notify PathView
    })
}
```

Flow:
1. User taps Edit in read view → `EditPebbleSheet` stacks on top of `PebbleDetailSheet`.
2. User saves in edit → `EditPebbleSheet` calls `onSaved` and dismisses itself → `PebbleDetailSheet` re-runs `load()` and forwards the signal to `PathView` for list refetch.
3. User cancels → edit dismisses, read view unchanged.

## Path wiring change

`PathView` collapses two presentation states into one:

- Drop `presentedDetailPebbleId`. Both path-tap and post-create now write to `selectedPebbleId`.
- Replace the `selectedPebbleId` sheet from `EditPebbleSheet(...)` to:

```swift
.sheet(item: $selectedPebbleId) { id in
    PebbleDetailSheet(pebbleId: id, onPebbleUpdated: {
        Task { await load() }
    })
}
```

- `CreatePebbleSheet`'s `onCreated` writes the new id to `selectedPebbleId` (instead of `presentedDetailPebbleId`).

## Data

`PebbleDetailSheet.load()` extends its SELECT to embed each soul's glyph strokes so the read view can render the glyph thumbnail without per-soul lazy fetches:

```
pebble_souls(soul:souls(id, name, glyph_id, glyph:glyphs(id, name, strokes, view_box)))
```

This adds one nested embed per soul. `PebbleDetail.SoulWrapper` and `Soul` are extended to decode the optional `glyph: Glyph?`. The existing `Soul` model's `glyph_id` stays; the new field is purely additive. No new RPC, no new endpoint.

`PebbleReadView` itself is pure UI — it takes the already-loaded `PebbleDetail` and reads `soul.glyph` directly when rendering each soul row.

## Fonts

**Ysabeau SemiBold:**
- File already at `apps/ios/Pebbles/Resources/Ysabeau SemiBold.ttf`.
- Add the file to `apps/ios/project.yml` under the Pebbles target's resources block (mirror how `Localizable.xcstrings` and `Assets.xcassets` are wired).
- Add `UIAppFonts` array to `apps/ios/Pebbles/Resources/Info.plist` with entry `Ysabeau SemiBold.ttf`.
- Run `xcodegen generate` (or `npm run generate --workspace=@pbbls/ios`).
- Verify the PostScript name (`fc-scan` or Font Book — likely `Ysabeau-SemiBold`); use that exact name in `Font.custom(...)` and document the verified name in a one-line comment at the call site.

**Description (New York):** `.system(size: 17, weight: .regular, design: .serif)`. No registration. Optical-size axis is left at SwiftUI's default for the point size — exact "optical size 50" is not exposed by `Font` and is approximated. Revisit if the rendered weight looks off.

## Theming

- Background: `Color.pebblesBackground` via the existing `pebblesScreen()` modifier.
- Light-blush set-state fill: `Color.pebblesAccentSoft` (new token if missing — implementation step adds it to `Color+Pebbles.swift` and the asset catalog with light/dark values, mirroring existing accent-tint tokens).
- Privacy badge border + foreground: existing border / muted-foreground tokens.
- Emotion-set fill: the emotion's hex color (already on `EmotionRef.color`).

## Localization

Per `apps/ios/CLAUDE.md`:
- All new user-facing strings ("Edit", "No domain", any "Untitled" fallback) declared as `LocalizedStringKey` / `LocalizedStringResource`.
- Convert `Visibility.label` from `String` to `LocalizedStringResource` so "Private" / "Public" auto-extract on build (currently they are string literals).
- Date format uses `Date.FormatStyle` for locale-aware month/day/time output. `.textCase(.uppercase)` + `.tracking` are visual styling and do not need localization.
- Pre-PR: open `Localizable.xcstrings` in Xcode, confirm no `New` or `Stale` rows, confirm `en` and `fr` columns filled for every new entry.

## Accessibility

- Privacy badge: `Button` semantics (or `Label`-styled view with explicit `accessibilityLabel`). If purely informational (not interactive), use `Text` + `.accessibilityElement(children: .combine)`.
- Edit toolbar button: native; inherits accessible label.
- Picture: `accessibilityLabel` derived from snap or generic "Pebble photo".
- Each `PebbleMetadataRow`: combined into one element per row; label = visible label; value reflects the style as described above.
- Description: read as text.

## Arkaik map update

Per project `CLAUDE.md`, update `docs/arkaik/bundle.json` via the `arkaik` skill. Conceptually: split the previously-merged "pebble detail" and "edit pebble" into distinct surfaces, with an edge from "pebble detail" to "edit pebble".

## Out of scope (deferred)

- Photo tap-to-zoom (separate issue).
- Delete from read view (separate issue if desired; existing path-list swipe stays).
- Customizing optical-size axis for NY serif description (revisit only if v1 looks off).

## Verification

Manual QA on simulator:
- Tap an existing pebble → read view opens.
- Tap Edit → edit stacks; save → read refreshes in place; cancel → unchanged.
- Record a new pebble → read view opens with the same layout (post-create reveal).
- Pebbles with: no description; no picture; no soul; no collection; no domain — each renders correctly per the hide-vs-dash rules.
- Light theme + dark theme.
- French locale: "Private"/"Public", "No domain", date label localize correctly.

Tooling:
- `npm run build` and `npm run lint` pass.
- `Localizable.xcstrings` clean (no `New`/`Stale`, `en` + `fr` filled).
- `xcodegen generate` succeeds after `project.yml` change.

## Files touched (summary)

New:
- `apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift`
- `apps/ios/Pebbles/Features/Path/Read/PebbleReadHeader.swift`
- `apps/ios/Pebbles/Features/Path/Read/PebbleReadPicture.swift`
- `apps/ios/Pebbles/Features/Path/Read/PebbleMetadataRow.swift`
- `apps/ios/Pebbles/Features/Path/Read/PebblePrivacyBadge.swift`

Modified:
- `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift` (rewrite body, add SELECT embed for soul glyph, add stacked edit sheet, add `onPebbleUpdated` callback).
- `apps/ios/Pebbles/Features/Path/PathView.swift` (collapse `selectedPebbleId` + `presentedDetailPebbleId` into one; route both through `PebbleDetailSheet`).
- `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift` (extend `SoulWrapper` to decode embedded glyph).
- `apps/ios/Pebbles/Features/Path/Models/Soul.swift` (add optional `glyph: Glyph?` field).
- `apps/ios/Pebbles/Features/Path/Models/Visibility.swift` (convert `label` to `LocalizedStringResource`).
- `apps/ios/Pebbles/Theme/Color+Pebbles.swift` (+ asset catalog) — add `Color.pebblesAccentSoft` if missing.
- `apps/ios/project.yml` — register Ysabeau font.
- `apps/ios/Pebbles/Resources/Info.plist` — add `UIAppFonts`.
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — new strings.
- `docs/arkaik/bundle.json` — split detail vs edit.
