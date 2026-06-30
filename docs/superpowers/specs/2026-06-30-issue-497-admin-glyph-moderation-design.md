# Admin glyph moderation + SVG upload/adjust (#497)

**Sub-project D of 4** in **M36 · Pebblestore & Karma Economy**. The moderation +
authoring back-office for the glyph marketplace, living in `apps/admin`.

- **Depends on:** C (#496) — the `glyph_submissions` listing model, `glyph_entitlements`,
  the `glyphs` policy rewrite (D8), and the `v_glyph_market` view it moderates.
- **Independent of:** B.

---

## 1. Context & current state

The marketplace data model already shipped in C
(`20260630003348_glyph_marketplace.sql`):

- `glyph_submissions` — submission *and* market listing. `status` is
  `pending`→`approved`/`rejected`; `price integer` per-listing; `reviewed_at`/`reviewed_by`
  columns **already present but never written by C** (left for D). Partial unique index
  allows at most one active (`pending`|`approved`) submission per glyph.
- `glyph_entitlements`, `glyph_favourites` — use-rights + favourites.
- RPCs `submit_glyph(p_glyph_id)` and `buy_glyph(p_glyph_id)`.
- `v_glyph_market` (`security_invoker`) — **approved-only** listings with per-caller
  `owned`/`favourited` flags.
- The `glyphs` policy rewrite (D8): SELECT widened to own ∪ system seeds (`user_id is null`)
  ∪ **approved**-listed ∪ entitled; UPDATE/DELETE locked once a glyph is actively listed or
  bought — **`is_admin(auth.uid())` is exempt** (this issue relies on that exemption).

C explicitly ships **no** approve/reject UI (D5: "approve a row by hand to test").
This issue builds that UI, the curation controls, and the first-party SVG upload path.

`apps/admin` conventions this issue follows:

- `requireAdmin()` server gate (`app/(authed)/layout.tsx`); RLS defense-in-depth.
- **All admin data through `SECURITY DEFINER` RPCs gated on `is_admin(auth.uid())`**,
  revoked from `anon`/`authenticated`-without-admin (analytics precedent).
- **Server-Component-fetch → card pattern**: `XCard.tsx` (SC, awaits fetcher, renders
  skeleton/error/empty) → presentational → client child for interactive state.
  **Fetchers throw `new Error(error.message)`** so `ErrorBlock` can render it.
- Sidebar groups in `components/layout/Sidebar.tsx` ("Insights", "Logs").
- Admin app is **English-only** (analytics/logs chrome is not localized).

Glyphs are **stroke-only**: `Mark` ↔ DB `glyphs`; geometry is
`strokes: { d: string; width: number }[]` + `view_box: string`.
`renderGlyphPaths(glyph, zone)` (`apps/web/lib/engine/glyph.ts`) uniformly scales + centres
the glyph to fit a zone, **forces `stroke="black" fill="none"`**, and **normalizes every
stroke width** to `STROKE_WIDTH / scale` (the stored per-stroke `width` is *not* used in the
final market render — only the carve-editor preview uses it). The emotion colour is applied
downstream.

### Decisions locked in brainstorming

- **D-D1 — SVG import is stroke-only.** Each imported path renders as an *outline*
  (`fill=none`), consistent with the existing model. Filled icons import as outlines only;
  this limit is **documented** and made visible in the live preview. No new render mode
  (filled glyphs would change the core engine across web + iOS — out of scope, future issue).
- **D-D2 — Reject reasons are stored and shown to the submitter.** Add `review_note` to
  `glyph_submissions`; the reject RPC records it; C's "Mine" tab surfaces it on the rejected
  badge (the one cross-app `apps/web` touch).
- **D-D3 — First-party uploads are owned by the admin user.** Insert the glyph with
  `user_id = auth.uid()` (admin) + an **auto-approved** submission row. Behaves as a normal
  community listing: appears in Market, buyable by everyone else, `cannot_buy_own` protects
  the admin, and the D8 lock's admin exemption lets the admin re-edit/re-price it. System-seed
  (`user_id is null`) ownership is explicitly rejected — those are the free per-domain seeds,
  a different concept.
- **D-D4 — Adjust = metadata + basic transforms.** Name, karma price, shape association, plus
  uniform scale / recenter / flip. No per-stroke editing (that would rebuild `/carve`).
- **D-D5 — Admin reads pending geometry through a `SECURITY DEFINER` RPC.** Under the widened
  `glyphs_select`, an admin **cannot** read a *pending* submission's strokes via RLS (not
  owner, not approved, no entitlement). The moderation queue therefore reads geometry through
  an `is_admin`-gated RPC, not the `v_glyph_market` view.
- **D-D6 — Glyph names stay a single (non-localized) string.** Matches the `glyphs.name`
  column and C's community glyphs. The issue's "bilingual" requirement is met by the PR's
  **Lab Note** proposal, not by per-glyph localized names.

---

## 2. Data model change

One migration in `packages/supabase/supabase/migrations/`, then regenerate
`packages/supabase/types/database.ts` (`npm run db:types --workspace=packages/supabase`)
and commit it.

```sql
alter table public.glyph_submissions
  add column review_note text;   -- reject reason; null unless rejected
```

`review_note` is readable by the submitter via the existing
`glyph_submissions_select` policy (`submitter_id = auth.uid()`), and by admins via the
read RPC (§3.1). No new RLS policy is needed.

---

## 3. RPCs

All in the same migration as §2. Each is
`language plpgsql security definer set search_path = public`, **opens with an
`if not public.is_admin(auth.uid()) then raise exception 'not_admin' using errcode='42501'; end if;`
guard**, is `grant execute … to authenticated`, and `revoke all … from public, anon`.
Multi-table / multi-statement → RPC per AGENTS.md (and the `is_admin` gate must survive
`security definer`, which only an RPC can enforce).

### 3.1 `admin_list_glyph_submissions(p_status text default null) returns jsonb`

The queue's read path — joins `glyph_submissions` → `glyphs` so the admin can preview
geometry that RLS would otherwise hide (D-D5). Returns a JSON array of rows:

```
submission_id, glyph_id, status, price, review_note,
created_at, reviewed_at, submitter_id, submitter_email,
name, shape_id, strokes, view_box
```

`submitter_email` via a join to `auth.users` (allowed inside `security definer`).
`p_status` filters when non-null (default returns all; the UI defaults the queue to
`pending`). Ordered `created_at` ascending (oldest pending first — review FIFO).

### 3.2 `approve_glyph(p_submission_id uuid, p_price integer default null) returns jsonb`

1. `is_admin` guard.
2. Load the submission; `not_found` if missing.
3. `invalid_state` if `status <> 'pending'` (can't approve an already-resolved row).
4. Set `status='approved'`, `reviewed_at=now()`, `reviewed_by=auth.uid()`, and
   `price = coalesce(p_price, price)` (optional approve-time price override).
5. Return the updated row as `jsonb`.

The partial unique index (`one active per glyph`) is the backstop against a glyph already
having another active submission.

### 3.3 `reject_glyph(p_submission_id uuid, p_note text) returns jsonb`

1. `is_admin` guard.
2. `not_found` if missing; `invalid_state` if `status <> 'pending'`.
3. `missing_note` if `p_note` is null/blank (a reason is required — D-D2).
4. Set `status='rejected'`, `review_note=p_note`, `reviewed_at=now()`,
   `reviewed_by=auth.uid()`. Return the row.

Rejecting frees the glyph: the partial unique index only blocks `pending`|`approved`, so the
creator can fix and re-submit (existing `submit_glyph` re-check covers this).

### 3.4 `set_glyph_price(p_submission_id uuid, p_price integer) returns jsonb`

1. `is_admin` guard. `not_found` if missing.
2. `invalid_state` if `status <> 'approved'` (only live listings are re-priced).
3. `bad_price` if `p_price <= 0` (mirrors the column `check (price > 0)`).
4. Set `price = p_price`. Return the row. Re-pricing does **not** touch already-issued
   entitlements — their `price_paid` snapshot is immutable (C's D4).

### 3.5 `publish_admin_glyph(p_name text, p_shape_id <shape fk type>, p_strokes jsonb, p_view_box text, p_price integer) returns jsonb`

Atomic first-party publish (D-D3). One transaction:

1. `is_admin` guard.
2. `bad_price` if `p_price <= 0`; `empty_glyph` if `p_strokes` is an empty array.
3. Insert `glyphs (user_id = auth.uid(), name, shape_id, strokes, view_box)` → `v_glyph_id`.
4. Insert `glyph_submissions (glyph_id = v_glyph_id, submitter_id = auth.uid(),
   status='approved', price = p_price, reviewed_at = now(), reviewed_by = auth.uid())`.
5. Return `{ glyph_id, submission_id }`.

`shape_id`'s exact type/FK is read from the live `glyphs` schema during implementation
(matches the `glyphs.shape_id` column).

**Error contract** (surfaced in admin UI, English): `not_admin`, `not_found`,
`invalid_state`, `missing_note`, `bad_price`, `empty_glyph`.

---

## 4. SVG → stroke-model conversion

`apps/admin/lib/pebblestore/svg-to-strokes.ts` — pure, no DOM-mutation, runs in the admin
browser (uses `DOMParser`). The **documented** conversion + its limits live in this file's
header comment and are mirrored here (acceptance criterion: "document the supported subset").

**Input → output:** raw SVG string → `{ strokes: MarkStroke[]; viewBox: string; skipped: string[] }`.

**Supported:**
- `<path>` — `d` parsed for the command subset `M m L l H h V v Q q C c Z z`. (These cover
  freehand carve output and typical line-art exports.)
- `<line>`, `<polyline>`, `<polygon>` — converted to an equivalent `d`.
- `viewBox` — taken from the root `<svg viewBox>`; if absent, computed from the union of all
  parsed path bounds (with a small padding margin).
- Stroke width → `MarkStroke.width` (falls back to a default constant when the source has no
  `stroke-width`). Cosmetic only: `renderGlyphPaths` normalizes width at market-render time.

**Not supported (skipped, surfaced as a count + list in the preview):**
- `<rect>`, `<circle>`, `<ellipse>`, `<text>`, `<image>`, `<use>`, gradients, patterns,
  embedded `<style>`, CSS classes, arcs (`A/a`) and any other path command outside the subset.
- **Fills:** the model is stroke-only. A solid filled icon imports as its *outline* only.
  The live preview shows exactly this, so the admin sees the result before publishing.

Conversion **never throws** on unsupported content — it skips and reports. It throws only on
unparseable SVG (no root `<svg>`, malformed XML), surfaced as an inline error.

---

## 5. Adjust + transforms

`apps/admin/lib/pebblestore/transform-path.ts` — pure affine transform on a path `d` string
(same `M/L/H/V/Q/C/Z` subset), used to **bake** the adjust transforms into geometry at
publish time.

- **Transforms:** uniform `scale`, `translate` (recenter), `flipH`/`flipV` (negative scale
  about the glyph centre). Composed into a single 2×3 matrix.
- **Live preview** applies the matrix cheaply via an SVG `<g transform="matrix(...)">` wrapper.
- **At publish**, the matrix is applied to each stroke's `d` coordinates and the `view_box` is
  recomputed from the transformed bounds (+ padding). This keeps stored geometry
  transform-free so downstream renderers need no awareness of it.
- Flip is the reason geometry must be baked (it can't be expressed by adjusting `view_box`
  alone). If the path-rewrite proves costly, the fallback is scale+recenter-only (expressible
  via `view_box` framing) — but the spec targets all three.

---

## 6. UI (apps/admin)

New sidebar **group "Pebblestore"** in `components/layout/Sidebar.tsx`:
**"Glyph moderation"** → `/pebblestore/glyphs`, with an **"Upload glyph"** CTA →
`/pebblestore/glyphs/new`.

### 6.1 Moderation queue — `app/(authed)/pebblestore/glyphs/page.tsx`

Server Component → `ModerationQueueCard` (SC, awaits the fetcher, renders
skeleton/error/empty) → `ModerationQueue` (presentational list) → `SubmissionCard` (client,
owns approve/reject dialog state).

- Default filter `pending`; a status filter (`pending`/`approved`/`rejected`/all) via
  `Tabs` or a `Select`.
- Each `SubmissionCard`: glyph preview (§6.3), submitter email, submitted-at, current price.
  Actions: **Approve** (optional price field, defaults to the row price), **Reject** (note
  textarea — required). Approved cards expose **Re-price** (`set_glyph_price`).
- Mutations call the RPCs via the admin browser client; on success, refresh
  (`router.refresh()`); errors render inline (the §3 error contract, English) with a Sonner
  toast for confirmation (Sonner already mounted in admin).

### 6.2 Upload + adjust — `app/(authed)/pebblestore/glyphs/new/page.tsx`

Client flow:
1. `SvgUploadCard` — file input / drop zone. On select, read the file, run
   `svgToStrokes`. Show the skipped-elements readout and any parse error inline.
2. `GlyphAdjustForm` — name, price (default `GLYPH_PRICE_DEFAULT` mirror), shape dropdown
   (from the `shapes` reference table — fetched via a fetcher; confirm `shapes` is
   `authenticated`-readable during implementation), and transform controls
   (scale slider, recenter, flip H/V).
3. `GlyphPreview` (§6.3) — live, inside the selected shape's outline.
4. **Publish** → bake transforms (§5) → `publish_admin_glyph`. On success, toast + redirect
   to `/pebblestore/glyphs?status=approved`.

### 6.3 Glyph preview helper

The admin app cannot import `apps/web/lib/engine`. Duplicate a **minimal pure** fit/render
helper into `apps/admin/lib/pebblestore/render-preview.ts` (uniform-scale-to-fit + centre,
`stroke="currentColor" fill="none"`), rendering strokes as `<path>` inside an `<svg>` with the
chosen pebble shape's outline as a visual frame/clip. Package-extraction of the engine is a
**future option**, not done here ("never refactor without approval"). Note the duplication in
the file header so a later grooming pass can consolidate.

---

## 7. Cross-app touch (apps/web)

C's "Mine" tab already shows a status badge for submitted glyphs. Add the **rejected
`review_note`** to that badge's display (e.g. tooltip or sub-line: "Rejected: …"). The
submitter already reads `glyph_submissions` (incl. `review_note`) via RLS; this is purely a
display addition. EN + FR copy for the "Rejected — reason" affordance in both locale files.

---

## 8. Out of scope

Filled-glyph render mode (engine change, web + iOS); per-stroke editing in admin; moderation
of anything other than glyphs; creator payouts / revenue-share / resale; analytics on glyph
sales (C's `price_paid` ledger already captures the data — views are a future issue).

---

## 9. Verification

No admin test runner (V1) — lint + build + manual.

- `npm run lint --workspace=apps/admin` and `--workspace=apps/web` green; `next build` for
  both; `npm run db:types --workspace=packages/supabase` regenerated and committed.
- **Manual (authenticated admin session + a `pending` submission from C):**
  1. Pending submission appears in the queue with a faithful preview + submitter + price.
  2. **Approve** → glyph goes live in Market (visible via the web app's Market tab); a
     non-admin can buy it; `reviewed_by`/`reviewed_at` set.
  3. **Reject with a note** → status `rejected`, note stored; web "Mine" tab shows the reason;
     rejecting with a blank note is refused (`missing_note`).
  4. **Re-price** an approved listing → new buyers pay the new price; existing entitlements'
     `price_paid` unchanged.
  5. **Upload SVG**: a line-art SVG imports cleanly; an SVG with `<rect>`/fills shows the
     skipped readout and an outline-only preview; malformed SVG shows an inline error.
  6. **Adjust**: scale / recenter / flip update the preview; **Publish** bakes the transform
     into stored geometry and the published glyph matches the preview in the real pebble render.
  7. Published first-party glyph is owned by the admin user, is live in Market, is buyable by
     others, and `cannot_buy_own` blocks the admin from buying it.
  8. **Auth:** all five RPCs reject a non-admin caller (`not_admin`); the queue route
     redirects a non-admin to `/403`.

---

## 10. Arkaik & decision log

- **Arkaik:** add two admin view nodes (Glyph moderation, Upload glyph) under the back-office
  surface in `docs/arkaik/bundle.json` (run the `arkaik` skill during implementation).
- **Decision-log entries to append at PR time:**
  - Admin glyph moderation runs through `is_admin`-gated `SECURITY DEFINER` RPCs
    (`approve_glyph`/`reject_glyph`/`set_glyph_price`/`publish_admin_glyph` + a
    `admin_list_glyph_submissions` read path that bypasses the pending-read RLS gap).
  - First-party glyphs are **admin-owned** normal listings (auto-approved), not system seeds —
    reusing all market plumbing and the D8 admin exemption.
  - SVG import is **stroke-only** with a documented supported subset; filled icons import as
    outlines (filled-glyph render mode deferred).
