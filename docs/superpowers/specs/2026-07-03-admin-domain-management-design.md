# Admin domain management (name · description · glyph)

**Status:** Approved (design)
**Date:** 2026-07-03
**Surfaces:** `apps/admin` (new view), `packages/supabase` (RPCs + view), `apps/web` (glyph consumption)

## Problem

There is no way to manage domains from the back-office. An admin should be able to
edit a domain's **name** and **description**, and **upload a glyph** for the domain
(replacing the existing one). Today domains are effectively frozen: their glyphs were
seeded out-of-band and their display text lives in client i18n catalogs.

## Context (current state)

Discovered while scoping — this shapes the whole design:

- **`domains` table** already carries everything we need:
  `id, slug, name, label, default_glyph_id`. `default_glyph_id` references
  `glyphs(id)` and points at a **system glyph** (`glyphs.user_id = NULL`,
  `shape_id = NULL` — both nullable since the remote-pebble-engine migration).
  There is **no `description` column**; `label` is the short descriptor
  (e.g. "Health & body").
- **Glyph model:** glyphs are shape-agnostic square drawings stored as
  `strokes jsonb` + `view_box text`; stroke width is always 6 in glyph space.
  The admin app already has the full SVG→glyph pipeline:
  `svgToStrokes` → adjust (scale/offset/flip) → `bakeAdjust` → `GlyphPreview`
  (see `apps/admin/lib/pebblestore/` and the marketplace `UploadAdjust` flow).
- **Localization:** domain display name/label are resolved **client-side** by both
  web (`useDomainLocalized`, keyed by `domain.{slug}.name` / `.label`) and iOS
  (`Domain+Localized.swift`), each backed by per-locale catalogs (EN + FR, all 18
  domains present). The DB `name`/`label` columns are only a **fallback** for slugs
  missing from the catalog. **The glyph is language-neutral** and has no such split.
- **Consumption divergence:**
  - **Web** ignores the DB for domains — it renders from the hardcoded
    `apps/web/lib/config/domains.ts` (18 domains) and localizes text via catalogs.
    It does **not** render any domain glyph today.
  - **iOS** reads domains from the DB (`ReferenceDataService`, `Domain{id,slug,name,label}`)
    but its model has no glyph field; the domain glyph is used server-side by the
    compose-pebble edge function as a render fallback.
- **Legacy rows:** some databases carry 5 legacy Greek-slug domains
  (`zoe`, `philia`, …) coexisting with the 18 canonical ones. No pebbles reference
  them; they are harmless.

## Decisions

Locked during brainstorming:

1. **"Description" reuses the existing `label` column** — no new schema column. The
   admin UI labels it "Description".
2. **Edit existing only** — no add / delete / reorder of domains. The set stays fixed.
3. **Glyph is newly DB-sourced on web; name/description stay in catalogs.** Admin
   edits to `name`/`label` update the DB (which remains the client fallback); they do
   **not** override already-translated catalog text. This avoids any i18n regression
   and keeps scope small. The web "migration" is therefore limited to reading and
   rendering the **glyph** from the DB.
4. **Legacy Greek rows are listed as-is** — no cleanup migration in this issue.
5. **iOS UI is out of scope** — iOS already consumes the glyph server-side; surfacing
   it in the iOS domain UI is a follow-up.

## Glyph replacement semantics

**Update-in-place.** When a domain already has a `default_glyph_id`, overwrite that
glyph row's `strokes`/`view_box` (same `glyph_id`). This keeps exactly one glyph per
domain — no orphaned glyph rows — and every consumer (edge-function fallback, web
cache) automatically reflects the new drawing without repointing foreign keys. When a
domain has no glyph yet, insert a system glyph (`user_id = NULL`, `shape_id = NULL`)
and set `default_glyph_id`.

## Components

### 1. Database — `packages/supabase`

No new columns. One new migration adds:

- **View `v_domains_with_glyph`** — domain fields plus the linked glyph's
  `strokes` and `view_box` (left join on `default_glyph_id`). Read-only reference
  data, mirroring `v_emotions_with_palette`. Consumed by web (and available to iOS
  later).
- **RPCs** (all `security definer`, `set search_path = public`, guarded by
  `is_admin(auth.uid())` raising `not_admin` / errcode `42501`; granted to
  `authenticated`, revoked from `public`/`anon` — same contract as the glyph-moderation
  RPCs):
  - `admin_list_domains()` → returns every domain with its current glyph:
    `id, slug, name, label, default_glyph_id, strokes, view_box`. Ordered by `name`.
  - `admin_update_domain(p_domain_id uuid, p_name text, p_label text)` →
    updates name + description. Trims input; empty name → `bad_name`.
  - `admin_set_domain_glyph(p_domain_id uuid, p_strokes jsonb, p_view_box text)` →
    upsert-in-place per the semantics above. Two-table write (glyphs + domains), so it
    is a single RPC per `AGENTS.md`. Guards: `p_strokes` null/empty → `empty_glyph`;
    unknown domain → `not_found`.

Then `npm run db:types --workspace=packages/supabase` and commit
`packages/supabase/types/database.ts`.

### 2. Admin view — `apps/admin`

- **Sidebar** (`components/layout/Sidebar.tsx`): new "Reference" group with a
  **Domains** item → `/domains` (lucide icon, e.g. `Shapes`/`Grid`).
- **List page** `app/(authed)/domains/page.tsx` (server component): calls
  `admin_list_domains` via the server supabase client; renders each domain as a row —
  current glyph thumbnail (admin `GlyphPreview`) + name + description + slug —
  linking to the editor. Placeholder thumbnail when no glyph.
- **Editor** `app/(authed)/domains/[id]/page.tsx` + a client
  `_components/DomainEditor.tsx`:
  - Name + Description inputs (pre-filled from the row).
  - SVG upload → adjust (scale / offset X-Y / flip H-V) → live preview, reusing
    `svgToStrokes`, `bakeAdjust`, and the admin `GlyphPreview`. Shows the current
    glyph until a new SVG is chosen. Skipped-element / parse-error messaging as in the
    marketplace uploader.
  - Save: `admin_update_domain` for text; `admin_set_domain_glyph` for the glyph
    (only when a new drawing was staged).
- **Server actions** `app/(authed)/domains/actions.ts`: mirror the pebblestore actions
  — call RPCs, map SQL error codes to English admin copy, `revalidatePath("/domains")`.

The marketplace `UploadAdjust` is **not** refactored (it carries price/publish logic
specific to the store). The domain editor reuses the shared **lib functions**
(`svgToStrokes`, `bakeAdjust`, types) and the `GlyphPreview` component; the adjust
controls are re-implemented in the domain editor (small, contained). If future reuse
grows, extract a shared `<GlyphUploadAdjust>` then.

### 3. Web consumption — `apps/web`

- New hook `lib/data/useDomainGlyphs.ts`: module-level cached + `withTimeout`, reads
  `v_domains_with_glyph`, returns a `Map<domainId, Mark>` (`Mark = { strokes, viewBox }`).
  Mirrors `useEmotionsWithPalette` (nullable-column guard at the boundary; drop rows
  with no glyph).
- Render the glyph via the existing web `components/glyphs/GlyphPreview` in the three
  domain surfaces: `DomainPicker`, `DomainPopover`, `PebbleDetailTiles`. Text-only
  fallback when a domain has no glyph (current behavior).
- **No text/i18n changes.** `lib/config/domains.ts` stays the authoritative list
  (id / slug / order); catalogs stay authoritative for name/label.

## Error handling & edge cases

- **Non-admin** caller → `not_admin` (`42501`) → "You are not authorized…".
- **Invalid / unsupported SVG** → existing `svgToStrokes` parse error + skipped-element
  report surfaced in the editor.
- **Empty glyph** (no usable strokes) → `empty_glyph`, save blocked client-side too.
- **Domain with no glyph yet** → editor allows a first upload; list shows a placeholder.
- **Legacy Greek domains** → listed and editable; harmless.
- **Web cache** → domain glyphs are reference data cached module-level; a refresh picks
  up an admin change. Acceptable for this data.

## Testing

No test framework yet (V1). Keep logic test-ready: the SVG pipeline is already pure
(`svgToStrokes`, `bakeAdjust`); RPC guards are exercised manually via the admin view.
Verify: upload replaces the glyph in place (same `glyph_id`), name/description persist,
non-admin is rejected, and the new glyph renders in the web domain surfaces.

## Docs & process

- **Arkaik** (`docs/arkaik/bundle.json`): add the new admin **Domains** view node
  (via the `arkaik` skill).
- **Lab Note:** user-facing (web now shows domain glyphs) → bilingual (EN/FR) blurb in
  the PR body.
- **Decision log:** only if a significant, reversible decision emerges (likely a no-op).

## Out of scope / follow-ups

- iOS: add a glyph field to the `Domain` Swift model and render domain glyphs in the
  iOS domain UI.
- Migrating web domain **text** to the DB (would require localized DB storage) — not
  wanted; catalogs remain the source.
- Cleanup migration for the 5 legacy Greek domains.
- Add / delete / reorder domains.
