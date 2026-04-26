# iOS — Soul Glyph (issue #298)

**Date:** 2026-04-25
**Issue:** #298 — Attribute a glyph to souls
**Milestone:** M25 · Improved core UX
**Labels:** `feat`, `ios`, `core`

## Goal

Souls today carry only a name and are listed as a flat text list on Profile → Souls. We want each soul to also carry a glyph — a small drawn mark — so it has a visual identity in the grid, on its detail page, and (later) anywhere else souls surface.

## Scope

**In scope**
- A `glyph_id` column on `souls`, FK to `glyphs`, defaulted to the existing system default glyph (`4759c37c-68a6-46a6-b4fc-046bd0316752`).
- Souls list rendered as a 3-column grid with a glyph thumbnail + name per cell, ordered alphabetically by name.
- Soul detail page gets a compact header: small glyph thumbnail beside the name + pebble count, then the existing pebbles list.
- Create-soul and edit-soul sheets get a "Glyph" row that opens the existing `GlyphPickerSheet`. Picker reuses today's behaviour: pick an existing glyph, or carve a new one via `GlyphCarveSheet`.

**Out of scope (deferred)**
- Reworking the soul selector inside the pebble record flow. The native dropdown stays as-is for this issue. Tracked separately.
- Sorting souls by most-recent-pebble. Likely needs a denormalised column or a join with `MAX(pebbles.created_at)`. Defer.
- Web-side soul→glyph linkage. iOS goes first; web mirrors later.

## Non-goals

- No new RPCs. Soul writes stay direct INSERT/UPDATE on the table — single-statement, single-table, per AGENTS.md.
- No `inline new_glyph` payload on a soul write. The carve flow inside `GlyphPickerSheet` already inserts the glyph client-side and returns it; the soul write only needs the resulting `glyph_id`.

## Data model

**Migration** — `packages/supabase/supabase/migrations/<ts>_add_glyph_to_souls.sql`:

1. Insert the system default glyph if it isn't already present:
   ```sql
   insert into public.glyphs (id, user_id, ...)
   values ('4759c37c-68a6-46a6-b4fc-046bd0316752', null, ...)
   on conflict (id) do nothing;
   ```
   This guarantees the FK target exists before the backfill, even if the seed has drifted between environments.
2. Add the column nullable, FK to glyphs:
   ```sql
   alter table public.souls
     add column glyph_id uuid references public.glyphs(id) on delete restrict;
   ```
3. Backfill existing rows:
   ```sql
   update public.souls
   set glyph_id = '4759c37c-68a6-46a6-b4fc-046bd0316752'
   where glyph_id is null;
   ```
4. Tighten:
   ```sql
   alter table public.souls
     alter column glyph_id set not null,
     alter column glyph_id set default '4759c37c-68a6-46a6-b4fc-046bd0316752';
   ```

`ON DELETE RESTRICT` — the default glyph must never be deletable while a soul references it; user-carved glyphs being deleted shouldn't silently strand a soul. Glyphs aren't user-deletable today; this future-proofs.

**RLS** — no change. `souls` already restricts by `user_id`. The join to `glyphs` succeeds because the existing `glyphs` SELECT policy already allows the user's own glyphs and system glyphs (`user_id IS NULL`).

**Type regeneration** — after applying the migration:
```
npm run db:types --workspace=packages/supabase
git add packages/supabase/types/database.ts
```

## iOS model

`apps/ios/Pebbles/Features/Path/Models/Soul.swift`
- Add `let glyphId: UUID` (non-optional). `CodingKeys.glyphId = "glyph_id"`.

`apps/ios/Pebbles/Features/Profile/Models/SoulDraft.swift` (new, mirrors `PebbleDraft`)
- `var name: String`
- `var glyphId: UUID` — defaults to the system default at construction.
- `var currentGlyph: Glyph?` — in-memory cache so the form's thumbnail can render without a glyph-by-id fetch.

`Profile/Models/SoulWithGlyph.swift` (new) — value used by the list/detail views. `Soul` + the joined `Glyph`, decoded from the PostgREST nested select.

## Components

**Reused unchanged**
- `Features/Glyph/Views/GlyphPickerSheet.swift` — already takes `currentGlyphId: UUID?` + `onSelected: (UUID) -> Void`.
- `Features/Glyph/Views/GlyphCarveSheet.swift` — opened from inside the picker, persists the new glyph, returns it.
- `Features/Glyph/Views/GlyphThumbnail.swift` — square stroke renderer used in the grid cell, the detail header, and the form row.

**Modified**

`Profile/Lists/SoulsListView.swift`
- Replace the text list with a `LazyVGrid` of three adaptive columns.
- Extract a small `SoulGridCell` view: a rounded-square `GlyphThumbnail` (~96 pt) above the soul's name (one-line, truncating).
- Order: `name` ascending.
- "+" stays in the toolbar, not in the grid — keeps cell layout uniform.
- Tap → push existing `SoulDetailView`.

`Profile/Views/SoulDetailView.swift`
- New compact header above the existing pebbles section: 56 pt `GlyphThumbnail` on the left, name + "<n> pebbles" on the right.
- "Edit" toolbar action stays.

`Profile/Sheets/CreateSoulSheet.swift`
- New "Glyph" row in the form. Shows the current `GlyphThumbnail` + label + chevron. Tap presents `GlyphPickerSheet`.
- On creation, `draft.glyphId` is initialised to the system default. On `.task`, the sheet eagerly fetches the default glyph (`select id, strokes, view_box from glyphs where id = $default`) and stores it in `draft.currentGlyph` so the row renders the correct thumbnail immediately. If the fetch fails, log via `os.Logger` and fall back to an empty thumbnail; the user can still tap to pick.
- Save: direct `INSERT INTO souls (name, glyph_id)` via the existing service path, plus `glyph_id` field.

`Profile/Sheets/EditSoulSheet.swift`
- Same "Glyph" row pattern, pre-filled with the soul's current `glyph_id` and the already-fetched glyph passed in from `SoulDetailView`.
- Save: direct `UPDATE souls SET name=$1, glyph_id=$2 WHERE id=$3`.

**New**
- `Profile/Lists/SoulGridCell.swift` — pure presentational, ~30 LoC.
- `Profile/Models/SoulDraft.swift` — described above.

## Data flow

**Souls list load**
```
supabase.from("souls")
  .select("id, name, glyph_id, glyphs(id, strokes, view_box)")
  .order("name", ascending: true)
```
One round-trip. Decode into `[SoulWithGlyph]`. Render via `SoulGridCell`.

**Soul detail load**
The existing soul + pebbles fetch gets `glyph_id, glyphs(id, strokes, view_box)` appended to the soul select. Pebble count for the header subtitle comes from the already-loaded pebbles array — no extra query.

**Create / edit flow**
1. Sheet opens with `draft.glyphId` populated (system default for create, current value for edit) and `draft.currentGlyph` populated where possible (passed in from the detail view on edit).
2. User taps the Glyph row → `GlyphPickerSheet` presents with `currentGlyphId: draft.glyphId`.
3. User picks an existing glyph (or carves one inside `GlyphCarveSheet`, which inserts the row in `glyphs` and returns the persisted `Glyph`).
4. `onSelected(glyph)` updates `draft.glyphId` AND `draft.currentGlyph` so the form thumbnail re-renders without a re-fetch.
5. Save: direct INSERT or UPDATE on `souls`. Single-statement, single-table.
6. On dismissal, the parent view (`SoulsListView` or `SoulDetailView`) refetches via the existing `.task(id:)` refresh-token pattern.

## Error handling & logging

- `SoulsListView` and `SoulDetailView` log + surface errors today; the only change is the joined select. The same error path catches join failures.
- `CreateSoulSheet` / `EditSoulSheet` save: log via `os.Logger` with a label (`"create soul"`, `"update soul"`) on `catch`, present the existing inline error state. No timeout wrapper — the call is button-driven, not render-blocking.
- The migration's `INSERT … ON CONFLICT DO NOTHING` of the system default glyph guards against a missing FK target before backfill.
- Picker dismissed without selection: `draft.glyphId` stays at its prior value. No error case.
- Carve failure inside the picker: handled by existing `GlyphService` paths. Unchanged.

## Localization

Strings touched:
- "Souls" (page title) — already exists.
- "Glyph" (form row label) — new key.
- "Tap to choose" (form row hint) — new key, or reuse an existing equivalent if there is one in the pebble form.
- "<n> pebbles" / "<n> pebble" — pluralised. Use `LocalizedStringResource` with a stringsdict-style plural in `Localizable.xcstrings`.

Add `en` and `fr` values for any new key. Open `Localizable.xcstrings` in Xcode before opening the PR and confirm no row is in `New` / `Stale` state.

## Testing

Swift Testing where it earns its keep:
- Decoding test: the joined `souls + glyphs(...)` payload decodes into `SoulWithGlyph` correctly, including a row pointing at the system default.
- `SoulDraft` payload encoding: emits `glyph_id` snake-cased, both for create (no `id`) and update (with `id`).

Manual verification before opening the PR:
- Build + run on iPhone simulator.
- Existing soul (pre-migration) shows the default glyph in the grid and detail.
- Create a soul without tapping the glyph row → soul saved with default `glyph_id`.
- Create a soul, open picker, carve a fresh glyph → soul saved with the new `glyph_id`.
- Edit a soul, change the glyph → grid reflects the change after dismissal.
- Run with French locale: every new string renders in French.
- `npm run lint` and a clean iOS build.

## Open follow-ups (not part of this issue)

- Pebble record flow soul selector: replace the native dropdown with a valence-style picker showing each soul's name + glyph. Mentioned in the issue's "Intention" but explicitly out of scope here. Track in a new issue.
- Sort souls by most-recent-pebble.
- Web-side soul→glyph rendering parity.
