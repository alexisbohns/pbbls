# Issue #461 — Wire per-soul pebbles count into `SoulsList` and `SoulPicker`

**Status:** Design approved
**Issue:** [#461](https://github.com/alexisbohns/pbbls/issues/461)
**Milestone:** M32 · iOS Quality
**Surface:** iOS only

## 1. Problem

PR #460 introduced `SoulItem`, which already renders a `fossil.shell` + numeric count row below the soul name when its `count: Int?` parameter is non-nil. But every call site currently passes `count: nil`, so the row is hidden in production.

This issue wires up the data so a reader looking at the souls list or the soul picker sees, next to each soul, the number of pebbles currently linked to that soul.

**Semantics.** `pebblesCount` for a soul = `count(distinct pebble_id) from public.pebble_souls where soul_id = <soul>`, naturally scoped to the current user because `souls` is already RLS-scoped to the owner.

## 2. Approach

**Option A — PostgREST nested aggregate** (chosen). Extend the existing `.select(...)` with `pebbles_count:pebble_souls(count)`. No migration, no RPC, no type regen. Matches the existing PostgREST join pattern already used for `glyphs(...)` in the same queries.

Option B (a `list_souls_with_counts` RPC) was considered and rejected for this iteration: the precedent for souls reads is already a PostgREST join, the rule in `AGENTS.md` is primarily aimed at atomicity for multi-table *writes*, and Option A is a one-line shape change in two call sites plus a small decoding tweak. If a third caller appears or decoding turns out painful, an RPC can replace this without a model change.

## 3. Data shape

PostgREST returns the aggregate as a single-element array of `{count: Int}` objects:

```json
{
  "id": "…",
  "name": "Molly",
  "glyph_id": "…",
  "glyphs":   { "id": "…", "name": null, "strokes": […], "view_box": "0 0 200 200" },
  "pebbles_count": [{ "count": 12 }]
}
```

A soul with zero linked pebbles still returns `[{ "count": 0 }]`, not `[]` — but we treat an empty array as 0 defensively (see §4).

## 4. Swift model — `SoulWithGlyph`

Extend `apps/ios/Pebbles/Features/Profile/Models/SoulWithGlyph.swift`:

```swift
struct SoulWithGlyph: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let glyphId: UUID
    let glyph: Glyph
    let pebblesCount: Int

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case glyphId      = "glyph_id"
        case glyph        = "glyphs"
        case pebblesCount = "pebbles_count"
    }

    private struct CountWrapper: Decodable { let count: Int }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id      = try c.decode(UUID.self,   forKey: .id)
        name    = try c.decode(String.self, forKey: .name)
        glyphId = try c.decode(UUID.self,   forKey: .glyphId)
        glyph   = try c.decode(Glyph.self,  forKey: .glyph)
        let wraps = try c.decodeIfPresent([CountWrapper].self, forKey: .pebblesCount) ?? []
        pebblesCount = wraps.first?.count ?? 0
    }

    // Required: providing any initializer in the struct body suppresses
    // the synthesized memberwise init. Preview and test code need to
    // construct instances without a network response, so we re-declare it
    // explicitly here.
    init(id: UUID, name: String, glyphId: UUID, glyph: Glyph, pebblesCount: Int) {
        self.id = id
        self.name = name
        self.glyphId = glyphId
        self.glyph = glyph
        self.pebblesCount = pebblesCount
    }

    var soul: Soul {
        Soul(id: id, name: name, glyphId: glyphId)
    }
}
```

`decodeIfPresent ?? 0` keeps the model decodable from older queries that don't request the aggregate (none ship today, but cheap insurance against future callers).

## 5. Call-site changes

### 5.1 `SoulsListView.swift`

- **Line ~103:** `SoulItem(case: .default, soul: item, count: item.pebblesCount)` (was `count: nil`).
- **Line ~126 (`load()`):** select string becomes
  `"id, name, glyph_id, glyphs(id, name, strokes, view_box), pebbles_count:pebble_souls(count)"`.

### 5.2 `SoulPickerSheet.swift`

- **Line ~88:** `SoulItem(case: itemCase(for: soul.id), soul: soul, count: soul.pebblesCount)` (was `count: nil`).
- **Line ~128 (`load()`):** same select string as above.

The `.create` tile (line ~81) stays `count: nil` — it never had a count and has no soul.

### 5.3 `CreateSoulSheet.swift`

The `.insert(payload).select(...)` chain in `save()` returns the inserted `SoulWithGlyph` to its parent via `onCreated(inserted)`. To keep the inserted value the same shape as the list query, append the aggregate to its select:

```swift
let inserted: SoulWithGlyph = try await supabase.client
    .from("souls")
    .insert(payload)
    .select("id, name, glyph_id, glyphs(id, name, strokes, view_box), pebbles_count:pebble_souls(count)")
    .single()
    .execute()
    .value
```

A freshly inserted soul has no `pebble_souls` rows yet, so the server returns `pebbles_count: [{ "count": 0 }]` and `inserted.pebblesCount == 0`. The downstream `souls.append(inserted)` in `SoulPickerSheet` and the `await load()` in `SoulsListView`'s `onCreated` continue to work unchanged.

### 5.4 Preview literal in `SoulItem.swift`

The `#Preview` constructs a `SoulWithGlyph` positionally. Update it to use the explicit memberwise init:

```swift
SoulWithGlyph(
    id: UUID(),
    name: "Molly",
    glyphId: SystemGlyph.default,
    glyph: Glyph(/* … unchanged … */),
    pebblesCount: 12
)
```

Pick a single non-zero count for the preview literal (e.g. 12) so all three non-create previews render the meta row.

## 6. Reactivity

No new plumbing. Both consuming views already call `.task { await load() }` on appear, which refreshes counts whenever the user navigates back from any create/edit/delete flow. Counts are read-only from the iOS perspective; the source of truth is `pebble_souls`, mutated by the pebble create/edit/delete RPCs which the user already exercises elsewhere.

Live Realtime updates are explicitly out of scope (issue § "Out of scope").

## 7. Acceptance criteria (from the issue)

- Open the souls list → each cell shows `🐚 N` below the name. Souls with zero pebbles show `🐚 0`.
- Open the soul picker → same. The count is visible in `.default`, `.selected`, `.unselected`; the `.create` tile shows no count.
- Create a new soul → it appears with `🐚 0`.
- Create a pebble linking 2 souls → next time you open the souls list, both counts are +1.
- Delete a pebble → next time you open the souls list, the linked souls' counts are decremented.
- No regression on `SoulPill` (path) or `PebbleMetaPill` — they don't consume the count.
- Build green; `Localizable.xcstrings` unchanged (no new copy needed).

## 8. Out of scope

- Web app parity (file separately if needed).
- Live Realtime subscriptions for count updates.
- "Frequently linked" section in `SoulPickerSheet` (already deferred per #459).
- Ripples count or activity-recency badges — pebbles count only.
- Migrating to an RPC. Tracked as a follow-up if a third caller appears or if PostgREST aggregate decoding proves fragile.

## 9. Risks & mitigations

- **PostgREST aggregate shape surprise.** If the server returns `null` or omits the key for souls with zero linked pebbles (unlikely but historically inconsistent across PostgREST versions), `decodeIfPresent ?? []` plus `.first?.count ?? 0` falls back to 0 cleanly.
- **RLS scoping of the aggregate.** `pebble_souls` is RLS-policied; the aggregate runs under the user's session, so cross-user pebble links cannot inflate the count. No additional `where` clause required on the client.
- **Performance.** For a per-user `souls` table that realistically holds tens of rows, an N+1-style nested aggregate is fine. If souls grow to hundreds, revisit with an RPC that aggregates in one pass.
