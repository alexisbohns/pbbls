# Remote Pebble Engine — Slice 1: iOS fallback render

**PRD:** #260 · **Milestone:** M19 · iOS ShameVP · **Labels:** `feat`, `core`

## Intention

Issue #260 ("Remote pebble engine") proposes consolidating pebble visual composition on the server so both webapp and iOS receive the same composed SVG + animation manifest for display. Today, composition is 100 % client-side in the webapp (`apps/web/lib/engine/`) and iOS has no visual at all (`PathView.swift:54` shows text only).

The target architecture from #260 is large: a DB migration, a new Deno edge function, a new RPC orchestration, and E2E client wiring across two platforms. This spec does **not** try to deliver the full target in one pass. It describes **slice 1** — the smallest vertical that touches every layer and retires the largest unknowns (edge function tooling, JWT forwarding, the engine port, first-time SVG rendering on iOS).

Slice 1 is sequenced so a failure in any layer surfaces inside a single iteration rather than after weeks of siloed work.

## Acceptance criterion

> As an iOS user, when I submit a new pebble, the recorded pebble detail sheet opens with a server-composed render of my pebble.

## Scope

### In slice 1

- DB migration: nullable `glyphs.user_id` and `glyphs.shape_id`, `domains.default_glyph_id` FK, `pebbles.render_svg / render_manifest / render_version` columns, seed 18 system-owned fallback glyphs (one per domain).
- New edge function `compose-pebble` (client-facing): wraps the existing `create_pebble` RPC, calls the engine, writes the render columns, returns composed output to the caller.
- New edge function `backfill-pebble-render` (ops-only, service-role gated): takes `{ pebble_id }` and composes an existing pebble.
- Shared engine modules in `_shared/engine/` ported verbatim from the POC in #260, plus 9 shape SVGs supplied by the user as inline TS constants.
- One-off backfill script `packages/supabase/scripts/backfill-renders.ts` (Deno).
- Engine smoke-test script `packages/supabase/scripts/smoke-test-engine.ts` (Deno) — constructs synthetic input, runs `composePebble` directly, no DB/network.
- iOS: add a native SVG rendering dependency, add `PebbleRenderView`, add `PebbleDetailSheet` as a new post-create viewer (the file that existed briefly in PR #254 but was deleted in #258 — re-introduced here for a different purpose), migrate `CreatePebbleSheet` submit from direct RPC call to `compose-pebble` function invoke, and wire `PathView` to present `PebbleDetailSheet` after a successful save.
- Regeneration of `packages/supabase/types/database.ts`.

### Out of slice 1 (deliberately deferred)

- Webapp composition path — no change to `apps/web/`. Webapp continues to render client-side. Webapp-created pebbles will have `render_svg` populated by the backfill script but the webapp will not consume it until a later slice.
- iOS carve editor / user-drawn glyph flow. iOS pebbles always use the domain fallback glyph in slice 1.
- Animation manifest consumer on iOS. The manifest is generated, stored, and returned, but iOS renders the SVG statically.
- Fossil layer composition (retroactive pebbles >28 days). Not composed.
- Path-list (`PathView.swift:54`) visual rendering. Still text only in slice 1; `PebbleRenderView` appears only in the detail sheet.
- Retry logic and idempotency keys on `compose-pebble`. Failures heal via the backfill script.
- RPC-driven compose orchestration (`pg_net` calling the edge function from within Postgres). Slice 1 uses client-sequential plumbing inside the edge function instead.
- Render versioning / cache invalidation workflow. `render_version` is hardcoded `"0.1.0"`.
- Backfill concurrency. Script is sequential.
- Monitoring / alerting on null `render_svg` rates.

## Decisions

1. **`compose-pebble` edge function wraps the existing `create_pebble` RPC rather than reimplementing its logic in Deno.** Inside the edge function, an auth-forwarded supabase-js client calls `create_pebble(payload)` via `.rpc()`. This preserves the RPC's single-transaction insert (cards + souls + domains + collections + snaps + karma) and avoids porting ~200 lines of PL/pgSQL to TypeScript only to delete it later. Long-term consolidation is preserved: the RPC becomes an internal implementation detail of the edge function; iOS and eventually the webapp both hit the edge function as their single external API. The RPC can be inlined or retired at any later pace.

2. **Two edge functions, not one.** `compose-pebble` is client-facing and auth-forwarding; `backfill-pebble-render` is ops-only and service-role gated. They share a single helper (`_shared/compose-and-write.ts`) so the composition path is identical in both flows. Separation keeps authz concerns explicit per endpoint and makes debugging the first-ever edge function setup straightforward — each function does exactly one thing.

3. **Compose logic lives in `_shared/compose-and-write.ts` so both edge functions (and future ones) cannot diverge.** Given `pebble_id`, it loads the pebble, resolves the glyph, runs the engine, writes the render columns, returns the composed output. Both edge functions call it with different pebble-id sources.

4. **Two supabase-js clients per `compose-pebble` invocation: auth-forwarded for the RPC call, admin (service-role) for the render-column write-back.** The RPC must run as the end user so `auth.uid()` resolves for ownership checks and karma; the render columns are server-owned and written via the admin client, bypassing pebble RLS for the fields the user shouldn't be able to spoof.

5. **Domain fallback glyphs are real rows in `public.glyphs` with `user_id = NULL`, referenced by `domains.default_glyph_id`.** `glyphs.user_id` is made nullable, `glyphs.shape_id` is also made nullable (shape is now derived from pebble valence + size, not stored on the glyph). The glyphs table's SELECT policy is relaxed so system glyphs are globally readable; insert/update/delete remain user-scoped.

6. **Glyph resolution priority:** (1) `pebbles.glyph_id` if set **and** the referenced glyph has `view_box === "0 0 200 200"` (new-format badge) → use that glyph's strokes; (2) pebble's first domain's `default_glyph_id` → use that glyph's strokes; (3) empty strokes (blank glyph slot). The `view_box === "0 0 200 200"` gate ensures legacy webapp glyphs (drawn in shape-derived coordinate spaces) are silently skipped in favor of the domain fallback — no algorithmic weirdness, no new columns. When a future slice introduces the new carve flow, it writes glyphs with the 200×200 viewBox by construction and the step-1 branch becomes load-bearing.

7. **All 9 shape SVGs are bundled inline as TS constants in `_shared/engine/shapes/<variant>.ts`.** No Supabase Storage bucket, no DB rows for shapes. Shape assets are versioned in git with the engine code itself, so `render_version` tracks the full rendering contract in one place. The user supplies the 9 SVGs as a pre-implementation input.

8. **User provides the 18 domain fallback glyph stroke payloads.** Not placeholder-seeded by the implementation. The migration `INSERT`s the strokes verbatim into 18 glyph rows with deterministic UUIDs, then updates each `domains` row with its new `default_glyph_id`.

9. **Native SVG rendering on iOS via a Swift package (runtime parser).** Recommended: `SVGView` by exyte (SwiftUI-native, parses SVG strings at runtime, exposes a navigable node tree for later per-path animation). Alternatives: `SwiftDraw`, `SVGKit`. Final choice is deferred to the implementation phase — a ~half-day tryout on each of the 9 shape variants before committing.

10. **Pebbles with `render_svg = NULL` fall back to text-only display on iOS.** No crash, no blocking state. Legacy pebbles before slice 1 stay text-only until the backfill script runs, then show their composed render. Pebbles whose compose failed transiently (backend 5xx with `pebble_id`) also render text-only and heal on next backfill run.

11. **Soft-success on edge function 5xx when `pebble_id` is present in the response body.** `CreatePebbleSheet` calls `onCreated(pebbleId)` and dismisses; `PathView` presents `PebbleDetailSheet` for that id; the sheet loads `PebbleDetail` from DB where `render_svg` is NULL (because compose failed after insert); the sheet renders the metadata section only and suppresses `PebbleRenderView`. Rationale: the pebble exists in the DB, so hiding it would be worse UX than showing a metadata-only placeholder. A hard-fail UI option was considered and rejected.

12. **No retry logic in slice 1.** Every async failure is logged with an explicit label (`"compose-pebble: composeAndWrite failed"`, etc.) visible in the Supabase edge function logs panel. The backfill script is the recovery path for failed renders.

13. **`render_version` is the hardcoded string `"0.1.0"`.** Slice 1 establishes the column and the convention; version comparison, drift detection, and versioned re-renders are slice 2+ concerns.

14. **The `glyphs.shape_id` FK to `pebble_shapes` is retained but made nullable.** Existing data keeps its references; new system glyphs set it to NULL. The `pebble_shapes` reference table is not deprecated in slice 1 (it's still used by the legacy webapp rendering path). A later iteration decides whether to drop the table entirely.

15. **`PebbleDetailSheet` is re-created as a new viewer sheet, distinct from `EditPebbleSheet`.** A file of this name existed briefly (PR #254) but was deleted in PR #258 when tap-to-view was unified with tap-to-edit via `EditPebbleSheet`. Slice 1 re-introduces `PebbleDetailSheet.swift` **specifically as the post-create display** — when a user saves a new pebble, this is the sheet that opens showing the render. It is *not* the tap-to-view target for existing pebbles from the path list (that remains `EditPebbleSheet` in slice 1). The UX distinction matches the acceptance criterion language ("the recorded pebble sheet opens with a render"): this is a one-time reveal after recording, not the ongoing edit surface.

16. **`CreatePebbleSheet.onCreated` signature changes from `() -> Void` to `(UUID) -> Void`.** The callback now hands the new pebble's id up to `PathView`, which uses it to drive presentation of `PebbleDetailSheet`. Mirrors the existing `EditPebbleSheet.onSaved: () -> Void` pattern but adds the one piece of information the caller needs to show the reveal.

## File changes

### Created

| Path | Responsibility |
|---|---|
| `packages/supabase/supabase/migrations/20260415000001_remote_pebble_engine.sql` | Schema changes + seed of 18 system glyphs + link each domain to its fallback. |
| `packages/supabase/supabase/functions/_shared/engine/types.ts` | Shared types ported from POC. |
| `packages/supabase/supabase/functions/_shared/engine/glyph.ts` | `createGlyphArtwork(strokes)` — normalize strokes to 200×200. |
| `packages/supabase/supabase/functions/_shared/engine/layout.ts` | 9-variant layout config + `resolveLayout(size, valence)`. |
| `packages/supabase/supabase/functions/_shared/engine/compose.ts` | `composePebble(input)` — final SVG + animation manifest. |
| `packages/supabase/supabase/functions/_shared/engine/resolve.ts` | `intensityToSize()`, `positivenessToValence()`. |
| `packages/supabase/supabase/functions/_shared/engine/shapes/small-lowlight.ts` | Inline `export const shape: string = '<svg …>'`. |
| `packages/supabase/supabase/functions/_shared/engine/shapes/small-neutral.ts` | Same. |
| `packages/supabase/supabase/functions/_shared/engine/shapes/small-highlight.ts` | Same. |
| `packages/supabase/supabase/functions/_shared/engine/shapes/medium-lowlight.ts` | Same. |
| `packages/supabase/supabase/functions/_shared/engine/shapes/medium-neutral.ts` | Same. |
| `packages/supabase/supabase/functions/_shared/engine/shapes/medium-highlight.ts` | Same. |
| `packages/supabase/supabase/functions/_shared/engine/shapes/large-lowlight.ts` | Same. |
| `packages/supabase/supabase/functions/_shared/engine/shapes/large-neutral.ts` | Same. |
| `packages/supabase/supabase/functions/_shared/engine/shapes/large-highlight.ts` | Same. |
| `packages/supabase/supabase/functions/_shared/engine/shapes/index.ts` | `getShape(size, valence): string`. |
| `packages/supabase/supabase/functions/_shared/compose-and-write.ts` | Shared loader + engine runner + render write-back. |
| `packages/supabase/supabase/functions/_shared/supabase-client.ts` | `createAuthForwardedClient(req)`, `createAdminClient()`. |
| `packages/supabase/supabase/functions/compose-pebble/index.ts` | Client-facing HTTP handler. |
| `packages/supabase/supabase/functions/backfill-pebble-render/index.ts` | Ops-only HTTP handler, service-role bearer required. |
| `packages/supabase/scripts/backfill-renders.ts` | Deno script: iterate pebbles with null render, invoke backfill function per row. |
| `packages/supabase/scripts/smoke-test-engine.ts` | Deno script: synthetic input → `composePebble` → assertions. No DB/network. |
| `apps/ios/Pebbles/Features/Path/PebbleRenderView.swift` | SwiftUI wrapper around the chosen SVG package. Takes a single `svg: String` input and renders it. |
| `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift` | Post-create viewer sheet. Takes `pebbleId: UUID`, loads `PebbleDetail` from DB (now including `render_svg`), renders `PebbleRenderView(svg:)` at the top when `renderSvg != nil` (otherwise suppresses the render block and shows metadata only — used by the soft-success path in Decision #11), then metadata (name, emotion, domains, date) below, then a Done button. Distinct from `EditPebbleSheet`. |
| `apps/ios/Pebbles/Features/Path/Models/ComposePebbleResponse.swift` | Decodable wrapper for the `compose-pebble` edge function response: `pebble_id`, `render_svg`, `render_manifest`, `render_version`. |
| `apps/ios/Pebbles/project.yml` entry (if new file additions require it) | Only if `project.yml` needs to list the new Swift files explicitly. Verify via `xcodegen generate` after adding them. |

### Modified

| Path | Change |
|---|---|
| `packages/supabase/types/database.ts` | Regenerated after migration. |
| `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift` | Add three optional fields — `renderSvg: String?`, `renderManifest: JSONValue?` (opaque wrapper for slice 1), `renderVersion: String?` — decoded with `decodeIfPresent` from the snake_cased DB columns. New `CodingKeys` entries. The derived `valence` computed property is untouched. |
| `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` | `load()` select query extended to include `render_svg, render_manifest, render_version`. No behavioral change to the edit form — the new fields are decoded but not used. Required only so the shared `PebbleDetail` decoder doesn't have to distinguish edit vs detail queries. |
| `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` | `save()` replaces the direct `.rpc("create_pebble", …)` with `supabase.client.functions.invoke("compose-pebble", …)`; response decoded into `ComposePebbleResponse`; `onCreated` signature changes from `() -> Void` to `(UUID) -> Void` and is called with `response.pebbleId` before `dismiss()`. The private `CreatePebbleParams` struct is deleted (the RPC is no longer called from here). |
| `apps/ios/Pebbles/Features/Path/PathView.swift` | New `@State private var presentedDetailPebbleId: UUID?`. `CreatePebbleSheet(onCreated:)` closure is updated to `{ pebbleId in presentedDetailPebbleId = pebbleId; Task { await load() } }`. New `.sheet(item: $presentedDetailPebbleId)` branch presenting `PebbleDetailSheet(pebbleId: id)`. Existing `EditPebbleSheet` presentation on row tap is untouched. |
| `apps/ios/Pebbles.xcodeproj` (via `project.yml`) | Swift Package Manager dependency on the chosen SVG package. `project.yml` is the source of truth — edit it, then run `xcodegen generate`. |

### Unchanged

- `packages/supabase/supabase/migrations/20260415000000_pebble_rpc_collections.sql` — the `create_pebble` RPC at line 12 already handles payloads with no glyph (`v_glyph_id` stays NULL). No RPC changes needed in slice 1.
- `apps/web/**` — the entire webapp. Zero changes.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        iOS client                           │
│  ┌──────────────────┐      ┌──────────────────────────┐     │
│  │ CreatePebbleSheet│      │ PebbleDetailSheet        │     │
│  │ (submit)         │      │ + new PebbleRenderView   │     │
│  └────────┬─────────┘      └────────────▲─────────────┘     │
│           │                             │                   │
└───────────┼─────────────────────────────┼───────────────────┘
            │ functions.invoke            │ pebble.renderSvg
            │ ("compose-pebble", payload) │ (Swift model)
            ▼                             │
┌───────────────────────────────────────────────────────────┐
│                  Supabase Edge Functions                  │
│                                                           │
│  ┌───────────────────────┐   ┌──────────────────────────┐ │
│  │   compose-pebble      │   │ backfill-pebble-render   │ │
│  │   (client-facing)     │   │ (ops / service-role)     │ │
│  │                       │   │                          │ │
│  │ 1. auth-forward JWT   │   │ 1. verify service role   │ │
│  │ 2. rpc(create_pebble) │   │ 2. compose-and-write     │ │
│  │ 3. compose-and-write  │   │    (pebble_id)           │ │
│  │ 4. return composed    │   │ 3. return ack            │ │
│  └──────────┬────────────┘   └─────────┬────────────────┘ │
│             │                          │                  │
│             └────────┬─────────────────┘                  │
│                      │                                    │
│  ┌───────────────────▼────────────────────────────────┐   │
│  │         _shared/compose-and-write.ts               │   │
│  │                                                    │   │
│  │  load(pebble + first domain + glyph)               │   │
│  │  → createGlyphArtwork(strokes) → artworkSvg        │   │
│  │  → resolveLayout(intensity, positiveness)          │   │
│  │  → getShape(size, valence) → shapeSvg              │   │
│  │  → composePebble({ shape, glyph, layout })         │   │
│  │  → { svg, manifest }                               │   │
│  │  → update pebbles set render_svg/manifest/version  │   │
│  │  → return { svg, manifest, version }               │   │
│  └────────────────────────────────────────────────────┘   │
│                                                           │
│  _shared/engine/                                          │
│    types.ts  glyph.ts  layout.ts  compose.ts  resolve.ts  │
│    shapes/ × 9 (TS constants)                             │
└───────────────────────────────────────────────────────────┘
            │                              │
            ▼ .rpc(create_pebble)          ▼ .update(pebbles)
┌───────────────────────────────────────────────────────────┐
│                        Postgres                           │
│                                                           │
│  public.pebbles (+ render_svg, render_manifest,           │
│                    render_version)                        │
│  public.glyphs  (user_id NULL-able, shape_id NULL-able)   │
│  public.domains (+ default_glyph_id FK → glyphs.id)       │
│                                                           │
│  create_pebble() RPC — unchanged behavior                 │
└───────────────────────────────────────────────────────────┘

                       (one-off, local)
                       ┌─────────────────────────────────┐
                       │ scripts/backfill-renders.ts     │
                       │                                 │
                       │ SERVICE_ROLE → query pebbles    │
                       │ WHERE render_svg IS NULL        │
                       │ → invoke backfill-pebble-render │
                       │   per id                        │
                       └─────────────────────────────────┘
```

### Key architectural moves

- **Single external API for iOS.** The client makes one call to `compose-pebble` per pebble creation and receives `{ pebble_id, render_svg, render_manifest, render_version }`. No separate "compose" call after insert.
- **RPC as internal implementation detail.** `create_pebble` is unchanged and called from inside the edge function with the user's JWT forwarded. All existing ownership and karma logic continues to work.
- **Shared composition path.** Both edge functions call the same `compose-and-write.ts` helper, so the create flow and the backfill flow produce byte-identical output for the same `pebble_id`.
- **Two clients per `compose-pebble` invocation.** Auth-forwarded for the RPC call (user context); admin (service-role) for the render write-back (server-owned fields).
- **Shape assets versioned with engine code.** Nine TS constants in git. No Storage bucket, no DB rows for shapes. Changing a shape is a code change that bumps `render_version` atomically.
- **Webapp stays on its existing path.** No webapp code touched in slice 1. Backfill will populate `render_svg` for webapp pebbles, but the webapp will ignore it until a later slice.

## Data flow

### Create flow (slice 1 primary path)

```
User taps "Save" in CreatePebbleSheet
    │
    ▼
iOS builds payload — identical shape to today's create_pebble RPC payload:
  { name, description, happened_at, intensity, positiveness,
    visibility, emotion_id, domain_ids: [<single uuid>] }
    │
    ▼
iOS calls supabase.functions.invoke("compose-pebble", body: { payload })
  with Authorization: Bearer <user JWT>  ← supabase-swift sets this automatically
    │
    ▼
┌─── Edge function: compose-pebble/index.ts ─────────────────────┐
│                                                                │
│ 1. authForwarded = createAuthForwardedClient(req)              │
│    (passes through the user JWT)                               │
│                                                                │
│ 2. { data: pebble_id, error } =                                │
│      await authForwarded.rpc("create_pebble", { payload })     │
│                                                                │
│    ─ runs in a single Postgres transaction                     │
│    ─ auth.uid() resolves to the end user                       │
│    ─ inserts pebbles + cards + souls + domains + collections   │
│      + snaps + karma events                                    │
│    ─ payload has no glyph → v_glyph_id stays null              │
│    ─ on error → return 4xx/5xx with the PostgREST error        │
│                                                                │
│ 3. admin = createAdminClient()                                 │
│                                                                │
│ 4. rendered = await composeAndWriteRender(admin, pebble_id)    │
│    │                                                           │
│    │  see §compose-and-write algorithm below                   │
│    │                                                           │
│ 5. return 200 JSON:                                            │
│      { pebble_id, render_svg, render_manifest, render_version }│
└────────────────────────────────────────────────────────────────┘
    │
    ▼
iOS decodes response into ComposePebbleResponse
    │
    ▼
CreatePebbleSheet calls onCreated(response.pebbleId) and dismisses
    │
    ▼
PathView stores pebbleId in @State presentedDetailPebbleId,
kicks off list refresh, and presents PebbleDetailSheet via
.sheet(item: $presentedDetailPebbleId) { id in PebbleDetailSheet(pebbleId: id) }
    │
    ▼
PebbleDetailSheet .task → loads PebbleDetail from DB including the
render_svg column (which the edge function just wrote back)
    │
    ▼
Detail sheet renders: PebbleRenderView(svg: detail.renderSvg) at top,
                      metadata (name, emotion, domains, date) below
    │
    ▼
User sees their composed pebble
```

### Compose-and-write algorithm (shared by both edge functions)

```
composeAndWriteRender(admin, pebble_id) {

  // a. Load pebble + its domains' default glyph ids.
  //    The nested select returns pebble_domains in PostgREST's default row order
  //    (insertion order for the junction table, which lacks an explicit ordering
  //    column). For iOS-created pebbles this is a one-element array so "first"
  //    is unambiguous. For legacy webapp pebbles with multiple domains the first
  //    returned is used — good enough for slice 1. When determinism becomes
  //    load-bearing, add an explicit order column on pebble_domains.
  const { data: pebble } = await admin
    .from("pebbles")
    .select(`
      id, intensity, positiveness, glyph_id,
      pebble_domains(
        domains(default_glyph_id)
      )
    `)
    .eq("id", pebble_id)
    .single();

  // b. Resolve glyph source per the priority rule
  let strokes: Stroke[] = [];
  let viewBox: string = "0 0 200 200";

  // b.1 prefer pebbles.glyph_id only if new-format (200×200)
  if (pebble.glyph_id) {
    const { data: userGlyph } = await admin
      .from("glyphs")
      .select("strokes, view_box")
      .eq("id", pebble.glyph_id)
      .single();
    if (userGlyph && userGlyph.view_box === "0 0 200 200") {
      strokes = userGlyph.strokes ?? [];
      viewBox = userGlyph.view_box;
    }
  }

  // b.2 fallback to domain's default_glyph_id
  if (strokes.length === 0) {
    const defaultGlyphId =
      pebble.pebble_domains?.[0]?.domains?.default_glyph_id ?? null;
    if (defaultGlyphId) {
      const { data: domainGlyph } = await admin
        .from("glyphs")
        .select("strokes, view_box")
        .eq("id", defaultGlyphId)
        .single();
      if (domainGlyph) {
        strokes = domainGlyph.strokes ?? [];
        viewBox = domainGlyph.view_box;
      }
    }
  }

  // b.3 if still empty, engine handles it (empty 200×200 SVG)

  // c. Run the engine (pure, no side effects)
  const artwork = createGlyphArtwork(strokes);
  const size    = intensityToSize(pebble.intensity);
  const valence = positivenessToValence(pebble.positiveness);
  const shapeSvg = getShape(size, valence);

  const { svg, manifest } = composePebble({
    size, valence,
    shapeSvg,
    glyphSvg: artwork.svg,
  });

  // d. Write render columns (admin client — bypasses RLS)
  const { error: updateError } = await admin
    .from("pebbles")
    .update({
      render_svg: svg,
      render_manifest: manifest,
      render_version: "0.1.0",
    })
    .eq("id", pebble_id);
  if (updateError) throw updateError;

  // e. Return
  return { render_svg: svg, render_manifest: manifest, render_version: "0.1.0" };
}
```

### Backfill flow

```
Developer runs:
  SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... \
    deno run --allow-env --allow-net packages/supabase/scripts/backfill-renders.ts
    │
    ▼
Script: createAdminClient()
    │
    ▼
Script: SELECT id FROM pebbles WHERE render_svg IS NULL ORDER BY created_at ASC
    │
    ▼
For each row:
    │
    ▼
Script POSTs to backfill-pebble-render:
  body: { pebble_id }
  headers: Authorization: Bearer <SERVICE_ROLE_KEY>
    │
    ▼
┌─── Edge function: backfill-pebble-render/index.ts ─────────────┐
│ 1. Verify Authorization header equals SERVICE_ROLE_KEY         │
│    (constant-time compare). 401 on mismatch.                   │
│ 2. admin = createAdminClient()                                 │
│ 3. rendered = await composeAndWriteRender(admin, pebble_id)    │
│ 4. return 200 { pebble_id, render_svg, render_manifest,        │
│                render_version }                                │
└────────────────────────────────────────────────────────────────┘
    │
    ▼
Script logs: ✓ <pebble_id>  or  ✗ <pebble_id> <error>
    │
    ▼
After the loop: Summary: rendered=N failed=M total=N+M
```

**Idempotence.** The script only touches rows where `render_svg IS NULL`. A re-run after a partial failure only retries the still-null rows. The engine is pure and deterministic, so composing the same pebble twice yields identical output.

**Edge cases.** Pebbles with no `pebble_domains` row (or a domain without `default_glyph_id`) hit the empty-glyph branch: the shape renders, the glyph slot is blank. Pebbles with `pebbles.glyph_id` set to a legacy glyph (non-200×200 view_box) also fall to the domain fallback. Neither case errors.

## Error handling & observability

### Failure modes — `compose-pebble`

| Failure point | Status | Response body | Side effect | Recovery |
|---|---|---|---|---|
| Missing / invalid payload | 400 | `{ error: "invalid payload", details }` | None | Client fixes payload |
| RPC `create_pebble` raises | 400 | `{ error: "<rpc error>" }` | Transaction rolls back | Client fixes input |
| Compose loads fail (pebble exists) | 500 | `{ error: "compose failed: <reason>", pebble_id }` | Pebble exists, `render_svg = NULL` | Backfill script |
| Engine throws | 500 | `{ error: "engine error: <reason>", pebble_id }` | Same | Backfill script |
| Write-back fails | 500 | `{ error: "write failed: <reason>", pebble_id }` | Same | Backfill script |
| Function timeout | 504 (runtime) | n/a | Same | Backfill script |

Always include `pebble_id` in the body when the pebble was successfully inserted — enables the iOS soft-success path.

### Failure modes — `backfill-pebble-render`

| Failure point | Status | Response body |
|---|---|---|
| Missing or wrong service-role bearer | 401 | `{ error: "unauthorized" }` |
| Missing/invalid `pebble_id` | 400 | `{ error: "invalid pebble_id" }` |
| Pebble not found | 404 | `{ error: "pebble not found", pebble_id }` |
| Engine or DB error | 500 | `{ error: "<reason>", pebble_id }` |

### Logging

Every catch path in both edge functions and `compose-and-write.ts` writes `console.error("<label>:", error)` with an explicit label. These show up in the Supabase Dashboard → Edge Functions → Logs panel.

Labels (prefixed by function name):
- `"compose-pebble: create_pebble rpc failed"`
- `"compose-pebble: composeAndWrite failed"`
- `"compose-and-write: load pebble failed"`
- `"compose-and-write: load glyph failed"`
- `"compose-and-write: engine error"`
- `"compose-and-write: render write-back failed"`
- `"backfill-pebble-render: auth failed"`
- `"backfill-pebble-render: compose failed"`

The backfill script logs `✓ <id>` / `✗ <id> <error>` per iteration and a final `rendered=N failed=M total=N+M`.

### iOS client failure handling

| Failure point | Behavior |
|---|---|
| Network error invoking `compose-pebble` | Show existing error toast in `CreatePebbleSheet`. User can retry. |
| 4xx from edge function | Decode `{ error }`, show to user, stay on sheet. |
| 5xx with `pebble_id` in body | **Soft success.** `CreatePebbleSheet` calls `onCreated(pebbleId)`, dismisses, `PathView` presents `PebbleDetailSheet`, which loads the DB row (`renderSvg = nil`) and renders metadata-only. Log error client-side. |
| 5xx without `pebble_id` | Hard failure. Show error, stay on sheet. |
| Response decode error | Show generic error, log, treat as hard failure. |
| SVG package fails to parse returned SVG | `PebbleRenderView` catches and renders a placeholder; rest of detail sheet still displays. No crash. |

### Data integrity invariants

- `render_svg`, `render_manifest`, `render_version` are always either all-null or all-populated. Enforced by always updating them together in one statement inside `compose-and-write.ts`. No DB-level check constraint yet.
- `render_version = "0.1.0"` is the only value in slice 1. When the engine changes, we bump the string and re-run backfill.
- `domains.default_glyph_id` FK has no `ON DELETE` cascade. Deleting a system glyph row is blocked by the FK.

## Testing & verification

V1 has no automated test suite. Slice 1 adds none either — verification is manual, ordered, and gated by build + lint + `deno check`.

### Gates (must pass before merge)

- `npm run build` (webapp) — validates the webapp still type-checks against the regenerated `database.ts`.
- `npm run lint` (webapp).
- `deno check` on each of: `compose-pebble/index.ts`, `backfill-pebble-render/index.ts`, `scripts/backfill-renders.ts`, `scripts/smoke-test-engine.ts`.
- Xcode build of the iOS project.

### Manual verification order

**Step 1 — Migration applies cleanly.**
```
cd packages/supabase
npm run db:reset
```
Inspect via `supabase studio` or a DB client:
- `glyphs.user_id` nullable, `glyphs.shape_id` nullable.
- `domains.default_glyph_id` column exists, all 18 rows populated.
- `glyphs` has 18 rows with `user_id IS NULL`, `view_box = '0 0 200 200'`, non-empty strokes.
- `pebbles` has the three render columns, all NULL on existing rows.

**Step 2 — Types regenerate.**
```
npm run db:types --workspace=packages/supabase
git diff packages/supabase/types/database.ts
```
Confirm the diff matches the new columns. Webapp `npm run build` now passes against the new types.

**Step 3 — Engine smoke test.**
```
deno run packages/supabase/scripts/smoke-test-engine.ts
```
Runs synthetic inputs through `composePebble` for all 9 `(size, valence)` variants, asserts the output SVG strings start with `<svg`, contain `<g id="layer:shape">` and `<g id="layer:glyph">`, and the manifest has at least one `"type": "glyph"` layer. No DB, no network, pure function only. Fast feedback loop for future engine edits.

**Step 4 — Edge function compiles and serves locally.**
```
supabase functions serve compose-pebble --env-file supabase/.env.local
```
First-run friction is expected — `_shared/` import resolution is the most common gotcha.

**Step 5 — `compose-pebble` curl smoke test.**
Grab a user JWT from the webapp's session (browser devtools → local storage). Build a minimal payload matching iOS's.
```
curl -X POST http://localhost:54321/functions/v1/compose-pebble \
  -H "Authorization: Bearer <user JWT>" \
  -H "Content-Type: application/json" \
  -d '{"payload": { ... }}'
```
Expected: `200 { pebble_id, render_svg, render_manifest, render_version }`.

**Step 6 — Visual inspection.**
Paste `render_svg` into https://svgviewer.dev or a `data:image/svg+xml` URL. Verify: shape outline, glyph visible inside the glyph slot, positioning matches the layout spec for the `(intensity, positiveness)` sent. Repeat for 3 variants spanning the grid: `(1,-1)`, `(2,0)`, `(3,1)`.

**Step 7 — DB state check.**
```sql
SELECT id, render_version, length(render_svg), jsonb_array_length(render_manifest)
FROM pebbles ORDER BY created_at DESC LIMIT 5;
```
Confirms the write-back landed and the output shapes are sane.

**Step 8 — Backfill smoke test.**
```sql
UPDATE pebbles SET render_svg = NULL, render_manifest = NULL, render_version = NULL
WHERE id = '<test-uuid>';
```
Then:
```
SUPABASE_URL=http://localhost:54321 \
SUPABASE_SERVICE_ROLE_KEY=<local service role> \
  deno run --allow-env --allow-net packages/supabase/scripts/backfill-renders.ts
```
Expected: `✓ <uuid>`, final `rendered=1 failed=0 total=1`. Re-run immediately: `rendered=0 failed=0 total=0` (idempotence).

**Step 9 — iOS simulator E2E.**
- Build and run in the simulator.
- Log in as a test user.
- Open `CreatePebbleSheet`, fill in fields, tap Save.
- Expected: the create sheet dismisses, the detail sheet opens, the composed pebble is visible at the top of the sheet.
- Repeat for different `(intensity, positiveness)` combinations — shape and glyph slot should change per the layout config.
- Inspect previously-created pebbles after backfill — they also render.

**Step 10 — Webapp regression.**
- `cd apps/web && npm run dev`.
- Create a pebble via the webapp; confirm it still renders via the existing client-side path.
- Open an iOS-created pebble in the webapp; confirm existing rendering still works (no crashes, even if visually different from iOS).

### Negative-path checks

- **Bad payload:** missing `emotion_id` → `compose-pebble` returns 400 with the RPC error.
- **Unauthorized collection_id:** RPC's ownership check trips → 400.
- **Forced engine error:** temporarily write malformed strokes to one seed glyph, create a pebble in that domain → expect 500 with `pebble_id`, iOS shows text fallback, backfill heals after reverting.
- **Backfill auth:** curl `backfill-pebble-render` with no bearer → 401; with wrong bearer → 401.

## Assumptions & prerequisites

- **User provides the 18 domain fallback glyph stroke payloads** before the migration is written, with `view_box = "0 0 200 200"`.
- **User provides the 9 shape SVG files** (small / medium / large × lowlight / neutral / highlight) before the engine is ported. Format: complete `<svg …>…</svg>` strings, canvas size matches the POC layout config (250×200, 260×260, 260×310 per size).
- **Supabase CLI + local Docker** environment is in a working state. The project memory notes a prior "corrupted Docker state" issue — verify `npm run db:start` works before slice 1 implementation begins.
- **iOS uses supabase-swift v2.x** with `functions.invoke` support. Verify version in `Package.resolved`.
- **Deno ≥ 1.40** locally for running the backfill and smoke-test scripts (typically bundled with Supabase CLI).

## Anticipated future iterations (NOT planned, outlined only)

This list exists so slice 1 is designed with them in mind, not so they're scheduled. Ordering and scope of each will be decided after slice 1 ships.

- **Slice 2:** iOS animation manifest consumer. `PebbleRenderView` reads the stored manifest and animates strokes draw-on when the detail sheet opens. Dependent on the SVG package's per-path API.
- **Webapp migration to the edge function.** Webapp create flow switches from `.rpc("create_pebble", …)` to `.functions.invoke("compose-pebble", …)`. Webapp display reads `render_svg` instead of client-composing. The existing `apps/web/lib/engine/` is deleted.
- **Fossil layer.** Server-side composition of the retroactive overlay for pebbles where `happened_at` is >28 days before `created_at`. Adds a third layer to `composePebble`.
- **User carve flow (new format).** Either platform's carve editor writes glyphs with `view_box = "0 0 200 200"`, and the glyph resolution rule from §Decisions #6 starts honoring `pebbles.glyph_id`. The legacy webapp carve editor is either migrated or deleted.
- **iOS path-list rendering.** `PebbleRenderView` in each `PathView` row. Requires a performance budget discussion (SVG parsing per cell).
- **Engine versioning.** Real `render_version` comparison logic; a "re-render all stale" admin action; a versioned asset map for shapes.
- **Backfill concurrency.** Parallel invocation in the script with a small semaphore.
- **RPC-driven orchestration (`pg_net`).** If/when atomic compose + insert is desired, the RPC calls the edge function from within Postgres.
- **Community / shared glyphs.** New schema: shareable public glyphs, gift flow, library picker.
- **Backfill of old user glyphs into the new 200×200 format.** A script re-normalizes legacy strokes and writes them as new-format glyphs (or updates existing rows with new `view_box`).

## Open questions

- **SVG package selection for iOS.** SVGView (exyte) is the leading candidate for SwiftUI integration and per-path access, but final selection is deferred to implementation. A half-day tryout on all 9 shape variants should decide it.
- **Cold-start latency.** The first `compose-pebble` request after idle may take 1–2s under Supabase's edge function cold-start model. The iOS sheet should show a spinner during submission (already present in the existing create flow). Actual latency is not tuneable from slice 1.
- **`render_manifest` shape on iOS.** The manifest is returned and stored but not consumed. Slice 1 decodes it into an opaque `[String: Any]` or a thin typed wrapper. Final typed shape is decided when the animation consumer is built.
