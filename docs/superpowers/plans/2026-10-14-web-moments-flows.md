# Web moments flows (#1050) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the web app (`apps/web`) onto the moments/pebbles split. Every read goes to `v_moments_full`. Every write goes to `save_moment` / `complete_pebble` / `delete_moment` / `delete_pebble` directly, with no edge function. Bare moments become visible on web: blank stones on the Path, a read page with a "Make it a pebble" call to action, and two edit screens. `/record` becomes **Capture** (photo · when · name). A new **Complete** flow (valence · emotion · domain · souls · collection · privacy → success) is added. Web adopts the mobile parity rules R1–R15. Once web is live, the last part drops the database compat layer and the two `compose-pebble*` edge functions.

**Architecture:** web keeps its eager-store model (`SupabaseProvider.loadFromSupabase()` → `Store` → hooks).
- The store's source of truth becomes `moments: Moment[]`, decoded from `v_moments_full`. Each `Moment` nests `pebble: MomentPebble | null`, the caller's pebble or null for a bare moment.
- Parts 1–5 keep consumers stable through a *derived* `store.pebbles` (the old flat `Pebble`, completed moments only, now carrying `moment_id`). Screens move to `Moment` one by one. Part 6 deletes the derived list and the flat type.
- Writers live on the provider. Pure payload builders and the PostgREST-slug mapper live in `lib/data/` and are unit-tested against verbatim server payloads.
- Capture and Complete reuse the M58 step scaffold (`RecordStepScaffold`, `RecordFlowChrome`, the step bodies under `components/record/flow/steps/`), each with its own pure reducer in `lib/record/`.

**Tech stack:** Next.js 16 App Router (client pages, `params`/`searchParams` as Promises read with `use()`), React 19, TypeScript strict, Tailwind 4, shadcn base-nova (`@base-ui/react`, no `asChild`), next-intl (EN/FR catalogs at `apps/web/lib/i18n/messages/{en,fr}.json`), supabase-js 2, Vitest (node env, pure `lib/` modules only).

**Inputs (binding):**
- Brief: `…/scratchpad/web-brief.md`. Contract: `…/scratchpad/mobile-brief.md` "The contract". Parity: `…/scratchpad/parity-reconciliation.md` (R1–R15). These live in the session scratchpad, not the repo.
- Spec: `docs/superpowers/specs/2026-10-11-moments-and-pebbles-split-design.md` (§3 writers, §4 readers, §5 compat, §6 clients).
- SQL (the contract): `packages/supabase/supabase/migrations/20261012090100_moments_split_writers.sql` (save_moment, complete_pebble, delete_pebble, delete_moment, set_collection_glyph), `20261012090200_moments_split_readers.sql` (v_moments_full, path_moments, resolve_pebble_glyph, get_shared_pebble), `20261012090400_moments_split_web_compat.sql` (what Part 7 drops).
- Reference implementation: the iOS plan `docs/superpowers/plans/2026-10-13-ios-moments-composer.md` and code on `main`:
  - `apps/ios/Pebbles/Features/{Capture,Complete,Path}/**`
  - `apps/ios/Pebbles/Services/{MomentsService,MomentWriteError,MomentDeletion,CompleteDraftStore}.swift`
  - Android `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/{capture,complete}/**`

---

## How to build and test

Run from the worktree root. **In a fresh worktree, copy `apps/web/.env.local` from the main checkout first.** Without it, `next build` cannot inline the Supabase URL and key.

```bash
npm run lint  --workspace=@pbbls/web     # eslint
npm run test  --workspace=@pbbls/web     # vitest run (TZ pinned to Europe/Paris in vitest.config)
npm run build --workspace=@pbbls/web     # next build = the typecheck gate
```

A part is green when all three pass. Manual check: `npm run dev --workspace=@pbbls/web -- -p 3100`. The main checkout owns :3000.

**Next.js 16:** before writing route code, read `node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/page.md` ("Reading `searchParams` and `params` in Client Components") and `dynamic-routes.md`. Client pages read `params` and `searchParams` with `use()`, as `app/pebble/[id]/page.tsx` already does.

**i18n:** every new key goes into both `en.json` and `fr.json`. Insert entries **as text at an anchor**, e.g. after the closing brace of an existing sibling block. Never load and re-dump the whole file with a JSON tool: it reflows the catalogs. `lib/i18n/messages.d.ts` types `useTranslations` from `en.json`, so a key missing in EN fails the build. Check FR parity with:

`node -e "const f=(o,p='')=>Object.entries(o).flatMap(([k,v])=>typeof v==='object'?f(v,p+k+'.'):[p+k]);const e=new Set(f(require('./apps/web/lib/i18n/messages/en.json'))),r=new Set(f(require('./apps/web/lib/i18n/messages/fr.json')));console.log([...e].filter(k=>!r.has(k)),[...r].filter(k=>!e.has(k)))"`

The output must be `[] []`.

**No red:** errors use the orange token (`text-destructive` maps to `--pbbls-error`). Never write a literal red.

**Workflow per part** (`gh-stack` skill):
- Branch the next part on top of the previous one, then follow the tasks in order, one commit per task (`type(web): …`, trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`).
- Get lint, test and build green, then open the PR. The body starts with `Part N of the #1050 stack`; only Part 6 says `Resolves #1050`.
- Parts 1–6: labels `feat`, `ui`, `web`, milestone M63. Part 7: `chore`, `db`, `supabase`, `web`, milestone M63.
- Never write a Kritik finding id in a PR body.
- Lab Note: Part 6 (the user-visible close of the feature) authors one with the `lab-note` skill, unless the maintainer covers it in Arkaik and labels the stack `no-lab-note`. Ask at PR time.

---

## Inventory — every Supabase name `apps/web` touches (main @ `2778f9aa`)

Produced with `grep -rhoE '\.from\("[a-z_]+"\)|\.rpc\("[a-z_]+"|functions\.invoke' apps/web/{app,components,lib}`, plus storage `pebbles-media`.

| Name | Where | Fate |
|---|---|---|
| `v_pebbles_full` (store load) | `supabase-provider.ts:95` | **P1** → `v_moments_full` |
| `v_pebbles_full` (a connection's shared pebbles) | `supabase-provider.ts:532` | **P1** → `pebbles` + `moments!inner` embed + `resolve_pebble_glyph` per row |
| `functions.invoke("compose-pebble")` | `createPebble` | **P5**: deleted with the old composer; Capture uses `save_moment` |
| `functions.invoke("compose-pebble-update")` | `updatePebble` | **P3**: deleted with `PebbleEdit`; the edit screens use `save_moment` / `complete_pebble` |
| `rpc("delete_pebble")` | `deletePebble` | kept, keyed by the pebble id (R8 "keep the moment") |
| `rpc("delete_moment_media")` | `deletePebbleMedia` | **P3**: removed. R6 detaches the photo through `save_moment` |
| `collection_pebbles` (load, collection create/update) | provider | kept (pebble-id keyed, owner writes) |
| `collections` | provider | **P6** adds `glyph_id` to the select, insert and update (R11) |
| `souls`, `glyphs`, `v_glyph_market`, `v_karma_summary`, `v_bounce`, `v_ripple`, `karma_events`, `achievements`, `achievement_unlocks`, `check_achievements`, `get_shared_pebble`, connections RPCs, consent, logs, profile | various | untouched |
| `path_pebbles`, `create_pebble`, `update_pebble` | not called by web | dropped in **P7** |
| new: `save_moment`, `complete_pebble`, `delete_moment`, `resolve_pebble_glyph`, `pebbles` (cross-user read), `moments` (embed) | — | P1 |

Embedded-select hazards, re-grepped in Part 6's gate: `render_svg`, `glyph_id` on pebbles, `happened_at` / `name` on `pebbles`, `snaps.pebble_id`, `pebble_drafts`. On `main` today none of them appear outside `get_shared_pebble`'s ignored `render_svg`.

---

## Design

### Data model

The model is chosen for the least consumer churn that is still honest about bare moments.

```ts
// lib/types.ts — added in Part 1
export type Intensity = 1 | 2 | 3
export type Positiveness = -1 | 0 | 1

/** The caller's pebble on a moment — `v_moments_full.pebble`. Exists only when complete (D2). */
export type MomentPebble = {
  id: string                // pebbles.id — share URL (/p/[id]), collection_pebbles, karma ref_id
  intensity: Intensity
  positiveness: Positiveness
  visibility: Visibility
  emotion_id: string
  soul_ids: string[]
  domain_ids: string[]
  collection_ids: string[]
  cards: PebbleCard[]
  glyph: PebbleGlyph | null // derived: oldest collection glyph, else first domain default (D5)
  created_at: string
  updated_at: string
}

/** What happened (D1). The route key everywhere: /pebble/[id] is a MOMENT id. */
export type Moment = {
  id: string
  name: string | null       // null = untitled (photo-only moment); never coerced to ""
  description: string | null
  happened_at: string
  snaps: PebbleSnap[]       // sort_order ascending
  instants: string[]        // signed original.jpg URLs, display only
  cover: PebbleCover | null // first snap, original + thumb signed
  created_at: string
  updated_at: string
  pebble: MomentPebble | null
}

export type CompletedMoment = Moment & { pebble: MomentPebble }
```

- `isCompleted(m): m is CompletedMoment` and `momentDisplayName(name: string | null): string | null` (trim, blank → null) live in `lib/data/moment-row.ts`.
- The old flat `Pebble` stays through Part 5 as a **derived, deprecated projection**: completed moments only, `id` = **pebble** id (unchanged semantics, so `collection.pebble_ids` lookups keep working), and a new `moment_id: string`.
- `SupabaseProvider` builds `store.pebbles = moments.filter(isCompleted).map(legacyPebbleFromMoment)` after every load.
- Part 6 deletes `Pebble`, `store.pebbles`, `usePebbles` and `legacyPebbleFromMoment`. Every remaining consumer takes `Moment` / `CompletedMoment` by then.

Why this shape:
- **Nesting mirrors the wire.** `v_moments_full.pebble` and the iOS/Android models (`MomentDetail.pebble`) are nested too, so a bare moment cannot pretend to have an intensity.
- **No silent semantics change.** A flat "`Moment` with optional pebble fields" would leave dozens of `pebble.intensity` reads compiling against `undefined`. Re-pointing `Pebble.id` at the moment id would silently break the pebble-id joins (`collection_pebbles`, share links, karma `ref_id`).
- **The compiler drives the migration.** Each part ports its own screens and the derived list keeps the rest compiling. When Part 6 deletes the type, `next build` lists every straggler.

Stone drawing takes a narrow look, not a whole entity. Part 4 adds `type StoneLook = Pick<MomentPebble, "intensity" | "positiveness" | "emotion_id" | "glyph"> & { seedId: string }`. `PebbleFramed` / `PebbleVisual` accept `look: StoneLook | null`, where null draws the blank stone, so lists, success, read page and sandbox share one input.

### Readers → screens

| Screen | Reader | Notes |
|---|---|---|
| Path (Spine, week roll, contents, peek) | `store.moments` ← `v_moments_full` | Web keeps the eager store. `path_moments` lacks `souls` and the snap list, both of which the Spine draws (PeopleLine, card thumb probe). Parity decision PD-W14. |
| Read page `/pebble/[id]` and `PebblePeek` | `store.moments.find(id)`; else by `pebble.id` → `router.replace` | The fallback covers links minted with a pebble id (post-split web pebbles have `pebble.id ≠ moment.id`, D9 only aligns backfilled rows) |
| Complete `/record/complete/[id]` | `store.moments` | not-found and already-completed guards |
| Edit moment, Edit pebble | `store.moments` | |
| Soul and collection detail (R9) | `store.moments` filtered by `pebble.soul_ids` / `collection.pebble_ids ∋ pebble.id`, newest `happened_at` first | "each platform's choice" (R9) |
| Souls page counts | `store.moments` completed | |
| Connection detail | `listConnectionSharedPebbles` → `pebbles` + embeds | v_moments_full nests only the caller's pebble, so it cannot serve this |
| Share `/p/[id]` | `get_shared_pebble` (unchanged) | |

Load query (Part 1). The `user_id` filter is load-bearing: `moments_select` also opens connections' moments.

```ts
this.supabase
  .from("v_moments_full")
  .select("id, user_id, name, description, happened_at, created_at, updated_at, snaps, pebble")
  .eq("user_id", this.userId)
```

### Write paths

All four are `security definer` RPCs, called directly. The provider never chains two writes behind one user action (R5).

| User action | Provider method | RPC | After success |
|---|---|---|---|
| Capture "Save moment" | `saveMoment(payload)` → moment id | `save_moment` | reload store; `/path?focus=<id>` |
| Capture "Make it a pebble now" | `saveMoment` | `save_moment` | reload; `router.replace("/record/complete/<id>?from=capture")` |
| Complete "Make it a pebble" | `completePebble(payload)` → pebble id | `complete_pebble` | reload; karma delta; `fireAchievementCheck`; clear autosave; success step |
| Edit moment Save | `saveMoment({id,…})` | `save_moment` | reload; best-effort remove detached files (R6) |
| Edit pebble Save | `completePebble` | `complete_pebble` | reload; karma pill (`pebble_enriched`) if delta > 0; `fireAchievementCheck` |
| Delete pebble, keep moment | `deletePebble(pebbleId)` | `delete_pebble` | reload; read page stays as a bare moment |
| Delete moment | `deleteMoment(momentId)` | `delete_moment` → `{storage_paths}` | best-effort remove `<p>/original.jpg`, `<p>/thumb.jpg`; clear that moment's autosave; reload; close read page |
| Photo upload (Capture, Edit moment) | `uploadSnap(file)` (unchanged) | storage only | |

- **Reload is not the write.** Each writer returns its id once the RPC succeeds, then reloads in its own `try/catch`. A failed reload logs `console.warn("[moments] reload after <fn> failed", err)` and leaves the store stale, and the write still counts as a success. Today's `createPebble` throws if the reload fails, which would make a user retry a `save_moment` create and duplicate the moment. `complete_pebble` is an upsert, but `save_moment` without an id is not.
- **`check_achievements`:** `fireAchievementCheck(provider)` runs once after every successful `completePebble` (Complete flow and Edit pebble) and never after `saveMoment` or a delete. The existing soul, collection and glyph call sites are unrelated and stay.
- **Karma delta (R13):** `store.karma` (from `v_karma_summary`) before the write, and after the reload. The delta is shown only when the reload succeeded and it is greater than 0. This is "sum after minus sum before", the iOS method.

Payload builders (`lib/data/moment-payloads.ts`, pure). Shared conventions:
- lowercase UUIDs;
- `happened_at` through the existing `wholeSecondIso(date)` from `lib/auth/signup-metadata.ts` (imported, not moved);
- absent keys omitted, never `null`.

```ts
export type SnapPayload = { id: string; storage_path: string; sort_order: number }
export type SaveMomentPayload = {
  id?: string; name?: string; description?: string; happened_at?: string; snaps?: SnapPayload[]
}
export type CompletePebblePayload = {
  moment_id: string; emotion_id: string; intensity: Intensity; positiveness: Positiveness
  visibility: Visibility; domain_ids: string[]; soul_ids: string[]; collection_ids: string[]
}

/** Capture create: no id, no description; name omitted when blank, snaps omitted without a photo. */
export function captureSavePayload(input: { name: string; happenedAt: string; snap: PebbleSnap | undefined }): SaveMomentPayload

/** Edit moment: every field, "" clears name/description, snaps is the whole set ([] = photo removed, R6). */
export function editMomentSavePayload(id: string, input: { name: string; description: string; happenedAt: string; snaps: readonly PebbleSnap[] }): SaveMomentPayload

/** Null unless the draft is complete. Every key always sent (R13 "already aligned"). */
export function completePebblePayload(momentId: string, draft: CompleteDraft): CompletePebblePayload | null
```

`CompleteDraft` is defined in `lib/record/complete.ts` (Part 2) and imported as a type here. To keep Part 1 self-contained, Part 1 defines `CompleteDraft` in `lib/data/moment-payloads.ts` and Part 2 re-exports it from `lib/record/complete.ts`:

```ts
export type CompleteDraft = {
  intensity: Intensity; positiveness: Positiveness; valenceConfirmed: boolean
  emotionId: string | null; domainIds: string[]; soulIds: string[]; collectionIds: string[]
  visibility: Visibility
}
```

- `domainIds` and `collectionIds` are arrays only so Edit pebble can carry an *untouched* legacy multi-set (PD-W11). The Complete flow always holds at most one of each.
- `valenceConfirmed` is iOS's "the seed is not autosaved until confirmed". It is set by a valence pick or by Continue.

### Error slug → copy (R10, R15)

`lib/data/moment-write-error.ts` replaces `compose-error.ts`, which is deleted once its last caller goes in Part 5. Matching follows iOS: `message.includes(slug)`. A `P0002` code with no known slug maps to `momentNotFound` (Android's rule).

```ts
export const MOMENT_WRITE_SLUGS = {
  moment_empty: "momentEmpty", media_quota_exceeded: "mediaQuotaExceeded",
  pebble_needs_emotion: "pebbleNeedsEmotion", pebble_needs_valence: "pebbleNeedsValence",
  pebble_needs_domain: "pebbleNeedsDomain", soul_not_owned: "soulNotOwned",
  collection_not_owned: "collectionNotOwned", moment_not_found: "momentNotFound",
  not_authenticated: "notAuthenticated",
} as const
export type MomentErrorKey = (typeof MOMENT_WRITE_SLUGS)[keyof typeof MOMENT_WRITE_SLUGS] | "generic"

export class MomentWriteError extends Error {
  readonly key: MomentErrorKey
  readonly code: string | null
  constructor(fn: "save_moment" | "complete_pebble", error: { message?: string; code?: string } | null)
}
/** i18n key under `moment.errors` for any thrown value; non-MomentWriteError → "generic". */
export function momentErrorKey(err: unknown): MomentErrorKey
```

`invalid_payload`, `invalid_snap` and anything unknown map to `generic`. Delete failures never use this table; they show R15's "Couldn't delete".

### Autosave (Complete only)

- **Storage.** `lib/record/complete-draft-store.ts` is pure: it takes a `Pick<Storage, "getItem" | "setItem" | "removeItem" | "key" | "length">`, so Vitest uses a fake. Key `pbbls-complete-draft:<momentId lowercased>`.
- **Body.** It uses `complete_pebble` key names with absent keys omitted, plus `owner_id` and `moment_id` (iOS `CompleteDraftSnapshot`):

  ```json
  {"owner_id":"907e7d76-00d3-4e53-82a3-4849bf0a9614","moment_id":"492238c2-e6c2-4cea-943b-26ea8e16b961","intensity":2,"positiveness":0,"emotion_id":"332cabe9-c76d-d737-bba6-f2d7024de808","domain_ids":["5b0c3d3e-1111-4c6a-9a3a-0d6c2f1e2a10"],"collection_ids":[],"visibility":"secret"}
  ```

- **Lifecycle.**
  - Writes are debounced by 800 ms and flushed on `visibilitychange` (hidden), on unmount and on ✕.
  - Restore is silent, once, after the store is ready. It sanitizes souls and collections against the store and lands on `firstGap()`.
  - A snapshot with another owner, a mismatched moment, an empty draft or corrupt JSON is deleted on read.
  - An empty draft is never written; the key is cleared instead.
  - The key is cleared on successful completion, after `delete_moment`, and by `clearAllCompleteDrafts()` in `useSupabaseAuth.logout` and `deleteAccount`.
- **Hook.** `lib/hooks/useCompleteAutosave.ts` wraps the store with `window.localStorage` in `try/catch`, logging `console.warn` on failure. It never touches `localStorage` from a component.
- **Capture has no autosave** (iOS parity, PD-W7). The old composer's `pbbls-composer-draft` key and `useComposerAutosave` / `useComposerDrafts` are deleted in Part 5. Capture removes the stale key once on mount.

### Routes

| Route | What | Why there |
|---|---|---|
| `/record` | **Capture** (photo · when · name) | Existing entry for the nav `+`, `PathEmptyState` and `WeekMouth`. `?composer=form` goes away with `QuickPebbleEditor`. |
| `/record/complete/[id]` | **Complete** for moment `id`; `?from=capture` changes where ✕ and success go | The `/record` prefix already gives immersive chrome (`MainContent.isRecord`, `BottomNav` / `MobileHeader` hidden), auth (`AuthGate`) and store gating (`store-routes.ts`) with no edits |
| `/pebble/[id]` | Read page, `id` = **moment** id | Existing route; unknown id → lookup by `pebble.id` → `router.replace("/pebble/<momentId>")` |
| `/pebble/[id]/edit` | **Edit moment** (when, name ≤ 40, description, photo) → `save_moment` | Existing "edit" route; it now edits the moment |
| `/pebble/[id]/edit-pebble` | **Edit pebble** (valence, emotion, domain, souls, collection, privacy) → `complete_pebble` | Sibling route; bare moment → redirect to Complete |
| `/path?focus=<momentId>` | R14 week focus | `PathPage` reads `use(searchParams).focus` and passes `focusMomentId` to `PathScreen`. That picks `isoWeekKey(happened_at)` once the moment is in the store, then `router.replace("/path")` |

### What happens to the existing composers and editors

- **`RecordFlow`** (ten steps, `compose-pebble`): Part 2 makes its chrome and history hook generic, and Part 5 deletes it. Its step bodies are kept: Capture uses photo, when and name; Complete uses valence, emotion, domain, souls and privacy. A new single-select collection step is added.
- **`QuickPebbleEditor`** (`/record?composer=form`): deleted in Part 5. The flow-vs-form experiment it served is over; there is no form composer on mobile either.
- **`PebbleDetail`** (inline-editing read page): replaced in Part 3 by a read-only `MomentRead`, plus the overflow menu and the CTA (PD-W2, Q1). `PebbleDetailTiles` pickers move into Edit pebble. `PebbleDetailSoulsGrid` becomes read-only (optional `onOpenSoulsSheet`).
- **`PebbleEdit`** (all fields, one `compose-pebble-update`): split in Part 3 into `MomentEdit` (title, date, description and picture sub-components reused) and `PebbleEnrich` (tiles, valence sheet, souls sheet, visibility). `usePebbleDraft`, `PebbleEditToolbar` (if unused after the split), `usePebble` and the `delete_moment_media` path are deleted.

### Parity decisions (web deviations from R1–R15, and choices where they are silent)

| # | Rule | Web does | Why |
|---|---|---|---|
| PD-W1 | R8 entry points (Path row long-press, soul/collection row long-press) | **No row-level delete.** Delete lives in the read page overflow (page and peek). Every row opens the read page (Spine stone/card, R9 rows), so Delete is one hop away. | Web has no long-press. A right-click `contextmenu` is invisible on touch and to keyboard users; a ⋯ button on every Spine row would clutter the book layout. |
| PD-W2 | R5/R7 (read page actions; editing is two screens) | The read page becomes **read-only**. Inline tile pickers, the inline privacy popover and inline photo add are removed; all edits go through Edit moment / Edit pebble. | R5 bans more than one write behind one Save. Inline tiles would be a third edit surface writing partial `complete_pebble` payloads, against R13's "every key always sent". See Q1. |
| PD-W3 | R7 | Path opens the read page in the existing `PebblePeek` dialog (web idiom); `/pebble/[id]` is the same component full-page. Overflow: **Edit moment**, **Edit pebble** (only with a pebble), **Delete**. Share stays a toolbar icon for public pebbles, `/p/<pebbleId>`. | Keeps the Spine's press → peek interaction. |
| PD-W4 | — | Routes as in **Routes** above. The read page id is the moment id, with a pebble-id fallback redirect. | Existing routing idiom; the `/record` prefix carries chrome, auth and store gating. |
| PD-W5 | R1 "Make it a pebble now" | Saves, then `router.replace` to Complete with `?from=capture`. iOS swaps content inside one cover. | URL-addressable flows. `replace` keeps Back from returning to an already-saved capture. |
| PD-W6 | Complete ✕ (iOS: never asks) | Never asks: flushes autosave, exits to `/pebble/<id>`, or to `/path?focus=<id>` when `from=capture`. | Parity. The destination is explicit because the flow pushes history entries per step. |
| PD-W7 | Autosave format "platform-native" | `localStorage` key per moment, owner-stamped body, silent restore (replaces web's M47 restore prompt). Capture has no autosave; the `pbbls-composer-draft` key is removed. | R13 "silent restore", iOS has no Capture autosave. |
| PD-W8 | R13 | Delta = the pebble's own `karma_events` (`ref_id` = pebble id) after the write − before (0 for a bare moment); a failed read shows nothing. | The iOS method. The store total also moves with achievement grants, which `fireAchievementCheck` never reloads, so a total-based delta double-counts them (Part 2 review). |
| PD-W9 | R4 (outline only, nothing carved) | **Spine:** `LitStone`'s existing blank mode (palette null → `BLANK_PALETTE`, neutral polarity, no veins, fossil or glyph) at **medium** size. Its lit material (grain, soft light) stays because every Spine stone is lit. **Elsewhere** (read banner, success, lists): outline-only blank, `PebbleOutlineBackdrop` silhouette `medium/neutral` with a `#868686` stroke on the `#8686861A` surface fill. A11y label "Not a pebble yet". | Nothing is carved on either; the Spine keeps one material language. |
| PD-W10 | R3 | Spine rows keep `path.spine.untitled` ("untitled" / "sans titre", italic, ink-3: the book's lowercase set). Every other surface uses `moment.untitled` ("Untitled" / "Sans titre", italic, `text-muted-foreground`). | The Spine (#1052) sets every label in its own lowercase typography. |
| PD-W11 | R13 "exactly one domain, zero or one collection" | **Complete:** one domain, single-select collection that commits and advances, dropping web's multi-select collection step. **Edit pebble:** an *untouched* legacy multi-domain or multi-collection set is sent back verbatim; touching the field collapses it to one. | Parity for new pebbles without silently deleting web users' existing links (mobile collapses to the first on save; see Q2). |
| PD-W12 | — | Week summary "N pebbles" counts completed moments only. Days, seams, day dots and the contents list include bare moments. | A bare moment is on the path but is not a pebble (R13 "active today counts completed moments"). |
| PD-W13 | R15 presentation | Delete failure = sonner `toast.error("Couldn't delete", { description: "Something went wrong. Please try again." })`. | Web's async-failure idiom (`toast` already used for share). The copy is R15 verbatim. |
| PD-W14 | — | Web reads `v_moments_full` only; `path_moments` is unused. | The Spine needs souls and both snap renditions per row. |
| PD-W15 | — | A connection's shared pebbles: `pebbles` + `emotions` + `moments!inner(name, happened_at)` embeds, plus `resolve_pebble_glyph` per row. | No mobile counterpart. `v_moments_full` nests only the caller's pebble. Existing direct-read exception (M51 D2), unchanged in kind. |
| PD-W16 | haptics | None. | Existing web decision (`RecordFlow` doc comment). |
| PD-W17 | step copy | Complete and Capture reuse web's `record.flow.step.*` EN/FR, with every FR "caillou" fixed to "galet". New keys use the iOS strings. | R-rules fix only R1, R2, R3, R4, R6, R8, R10, R11, R12 and R15 copy. |
| PD-W18 | R11 | The glyph row uses the souls' `GlyphPickerDialog`. Copy "Choose a glyph" / "Choisir un glyphe" replaces iOS "Tap to choose". | Pointer-neutral wording. |
| PD-W19 | R9 "drawn like Path rows" | Soul and collection rows use `PebbleCard` (list row) ported to `Moment`, newest first, opening `/pebble/<momentId>`. | The Spine entry is bound to the week layout. |
| PD-W20 | — | The collection detail keeps its web-only "remove from collection" ✕ (direct `collection_pebbles` write, unchanged). | Out of scope; flagged, not changed. |

---

## Part map (one part = one branch = one PR)

| # | Branch | Ships | Stands alone because |
|---|---|---|---|
| 1 | `feat/1050-web-moments-reads` | `Moment` types, `v_moments_full` decoder, derived flat pebbles, writers, payload builders, error mapping, connection reader off compat; verbatim-payload tests | No screen changes. The old composer and editor still write through `compose-pebble*` (live). Web stops reading `v_pebbles_full`. |
| 2 | `feat/1050-web-moments-complete` | `/record/complete/[id]`: reducer, per-moment autosave, success screen, `complete_pebble`, `check_achievements`; generic flow chrome and history | Reachable by URL only (Part 3 adds the CTA). Nothing links to it, so nothing is half-shown. |
| 3 | `feat/1050-web-moments-read-page` | Read page keyed by moment id (`MomentRead`), CTA → Complete, overflow (R7), delete dialog (R8), **Edit moment** and **Edit pebble**; removes `PebbleDetail`, `PebbleEdit`, `compose-pebble-update`, `delete_moment_media` | Consumers open with `moment_id`. The Path still lists completed moments only. |
| 4 | `feat/1050-web-moments-path` | Path over moments: blank stones (R4), untitled (R3), week focus (R14), `StoneLook` | Bare moments become visible; every target screen exists. |
| 5 | `feat/1050-web-moments-capture` | `/record` = Capture (R1, R2, R14); deletes `RecordFlow`, `QuickPebbleEditor`, composer autosave, `compose-pebble`, `ComposeError` | Creates bare moments, which Part 4 shows. |
| 6 | `feat/1050-web-moments-collections` | R9 rows, R11 collection glyph, removes flat `Pebble` / `store.pebbles` / `usePebbles`, final grep gate, Arkaik | Last web layer. Web is then off every compat object. |
| 7 | `chore/1050-drop-web-compat` | Migration dropping the four compat objects, deletes both edge functions and `_shared/karma-delta.ts`, harnesses, types, docs | **Pushed and undeployed only after Parts 1–6 are in production.** |

**Interim states a reviewer will see:**
- **Parts 1–3:** bare moments created on mobile exist but are not on the web Path, which is today's behaviour.
- **Parts 1–4:** the old ten-step composer still creates pebbles through `compose-pebble`.
- **Part 2:** the Complete route works only by URL.
- **Part 3:** `?focus=` is ignored until Part 4 (harmless).

---

## Part 1 — reads over moments, writers, error mapping (`feat/1050-web-moments-reads`)

**Files:**
- **Create:**
  - `lib/data/moment-row.ts`, `lib/data/moment-row.test.ts`
  - `lib/data/moment-payloads.ts`, `lib/data/moment-payloads.test.ts`
  - `lib/data/moment-write-error.ts`, `lib/data/moment-write-error.test.ts`
  - `lib/data/__fixtures__/moments-captured.ts`
  - `lib/data/connection-pebble-row.ts`, `lib/data/connection-pebble-row.test.ts`
  - `lib/data/useMoments.ts`, `lib/data/useMoment.ts`
- **Modify:** `lib/types.ts`, `lib/data/data-provider.ts`, `lib/data/supabase-provider.ts`, `lib/data/pebble-row.ts` (keep the snap, glyph and shared helpers; remove `pebbleFromViewRow`), `lib/data/pebble-row.test.ts`, `lib/i18n/messages/{en,fr}.json`.

### Task 1.1: Types
- [ ] In `lib/types.ts`, add `Intensity`, `Positiveness`, `MomentPebble`, `Moment` and `CompletedMoment` (code in **Design**).
- [ ] Add `moment_id: string` to `Pebble`, with the doc comment `/** @deprecated Derived from Moment for unported consumers; removed in #1050 Part 6. id is the PEBBLE id. */`.
- [ ] Fix the `PebbleGlyph` comment: `v_pebbles_full.glyph` → `v_moments_full.pebble.glyph`.
- [ ] In `data-provider.ts`, add `moments: Moment[]` to `Store` and `EMPTY_STORE`, and the interface methods:

```ts
saveMoment(payload: SaveMomentPayload): Promise<string>                 // moment id
completePebble(payload: CompletePebblePayload): Promise<string>         // pebble id
deleteMoment(momentId: string): Promise<void>                           // removes files best-effort
deletePebble(pebbleId: string): Promise<void>                           // existing; now documented "keeps the moment"
```

### Task 1.2: Fixtures copied verbatim from the other surfaces
- [ ] `lib/data/__fixtures__/moments-captured.ts` exports string constants (`JSON.parse`d in tests), copied **byte for byte**:
  - From `apps/ios/PebblesTests/Moments/MomentFixtures+Captured.swift`:
    - `IOS_DETAIL_COMPLETED` (line 21, `"Coffee"`, collection-sourced glyph, two collections `Autumn` / `Wins`, `description: null`)
    - `IOS_DETAIL_PHOTO` (line 25, `name: null`, one snap, `pebble: null`)
    - `IOS_DELETE_MOMENT`, `IOS_SAVE_MOMENT_RETURN`
    - `IOS_ERR_MOMENT_EMPTY`: `{"code":"P0001","details":null,"hint":null,"message":"moment_empty"}`
    - `IOS_ERR_NEEDS_DOMAIN`: `{"code":"P0001","details":null,"hint":null,"message":"pebble_needs_domain"}`
  - From `apps/android/app/src/test/kotlin/app/pbbls/android/core/model/MomentProductionPayloadTest.kt`:
    - `ANDROID_DETAIL_COMPLETED` (line 214, "Evening walk with Maya", domain-sourced glyph, a description)
    - `ANDROID_DETAIL_BARE_NAMED` (line 224, verbatim below)

  ```json
  [{"id":"ffd58f73-0339-4590-ad0e-84b894a266d8","name":"Morning coffee on the balcony","description":"First cold morning of the season.","happened_at":"2026-10-12T06:40:00+00:00","created_at":"2026-10-13T00:20:24.890253+00:00","snaps":[],"pebble":null}]
  ```

- [ ] Add one hand-made variant, labelled as such in a comment: `FRACTIONAL_WEB_ROW` = `IOS_DETAIL_PHOTO` with `"happened_at":"2026-10-06T17:42:09.123456+00:00"`, `"updated_at"` fractional, and `"description":""`. This is the precision web wrote before the split.

### Task 1.3: Decoder (`lib/data/moment-row.ts`)

```ts
export function momentFromViewRow(row: Record<string, unknown>, signedUrlByPath: ReadonlyMap<string, string>): Moment
export function momentPebbleFromRow(raw: unknown): MomentPebble | null   // null for null, non-object, missing id/emotion_id
export function legacyPebbleFromMoment(m: CompletedMoment): Pebble       // name null → "", id = pebble.id, moment_id = m.id
export function isCompleted(m: Moment): m is CompletedMoment
export function momentDisplayName(name: string | null): string | null
```

- `name` and `description` are kept as `string | null`; `""` reads as `null`.
- Snaps are sorted by `sort_order`. Signing reuses `snapOriginalPath`, `snapThumbPath` and `pebbleCover` from `pebble-row.ts`.
- The glyph goes through `pebbleGlyphFromRow`.
- `intensity` and `positiveness` are validated against `1|2|3` and `-1|0|1`. An out-of-range value logs `console.warn("[moments] unexpected valence", id)` and maps the pebble to `null`, so the stone draws blank instead of crashing a lookup table.
- Timestamps are kept as the server's strings. Consumers parse with `new Date()`, which accepts both precisions.

- [ ] Tests (`moment-row.test.ts`):

| Case | Expect |
|---|---|
| `IOS_DETAIL_COMPLETED` | `name === "Coffee"`, `description === null`, `pebble.id === "bdbc0b70-f8db-46ef-873f-f93a1ccd72dc"`, `pebble.collection_ids.length === 2`, `pebble.glyph.strokes.length === 5`, `new Date(happened_at).toISOString() === "2026-10-12T08:30:00.000Z"`, `instants` empty, `cover === null` |
| `IOS_DETAIL_PHOTO` + signed map for its `…/original.jpg` and `…/thumb.jpg` | `name === null`, `pebble === null`, one snap with `sort_order === 0`, `cover.url` and `cover.thumbUrl` set |
| `ANDROID_DETAIL_BARE_NAMED[0]` | `name === "Morning coffee on the balcony"`, `description === "First cold morning of the season."`, `pebble === null` |
| `ANDROID_DETAIL_COMPLETED[0]` | glyph `source` is not surfaced, `strokes` intact, domain id present |
| `FRACTIONAL_WEB_ROW` | `description === null`; `new Date(happened_at).getTime() === Date.UTC(2026, 9, 6, 17, 42, 9, 123)` |
| pebble with `"glyph": null` and `"glyph": {"id":"x"}` (malformed) | `glyph === null` both times |
| pebble with `"intensity": 4` | `pebble === null` |
| `legacyPebbleFromMoment(completed)` | `id === pebble.id`, `moment_id === moment.id`, `name === "Coffee"`; and for a null name, `name === ""` |
| `momentDisplayName("   ")` / `(null)` / `(" Coffee ")` | `null` / `null` / `"Coffee"` |

- [ ] In `pebble-row.test.ts`, delete the `pebbleFromViewRow` cases. Keep the `pebbleGlyphFromRow`, `pebbleNameFromRow` and `sharedPebbleFromRpc` cases.

### Task 1.4: Payload builders (`lib/data/moment-payloads.ts`)
- [ ] Implement the three builders from **Design**. The ids use `.toLowerCase()`, and `happened_at` uses `wholeSecondIso(new Date(input.happenedAt))`.
- [ ] Tests assert the **exact serialized JSON** (`JSON.stringify`), because key order and absence are the contract:

```ts
captureSavePayload({ name: "  Coffee ", happenedAt: "2026-10-12T08:30:00.789Z", snap: undefined })
// → {"name":"Coffee","happened_at":"2026-10-12T08:30:00Z"}

captureSavePayload({ name: "   ", happenedAt: "2026-10-11T19:04:51.000Z",
  snap: { id: "D49168B5-B97A-4888-A787-43EC65BB416A", storage_path: "907e7d76-00d3-4e53-82a3-4849bf0a9614/d49168b5-b97a-4888-a787-43ec65bb416a", sort_order: 0 } })
// → {"happened_at":"2026-10-11T19:04:51Z","snaps":[{"id":"d49168b5-b97a-4888-a787-43ec65bb416a","storage_path":"907e7d76-00d3-4e53-82a3-4849bf0a9614/d49168b5-b97a-4888-a787-43ec65bb416a","sort_order":0}]}

editMomentSavePayload("DAA2A992-16D1-459A-90D0-E3D42FB863A9", { name: "", description: " ", happenedAt: "2026-10-12T08:30:00+00:00", snaps: [] })
// → {"id":"daa2a992-16d1-459a-90d0-e3d42fb863a9","name":"","description":"","happened_at":"2026-10-12T08:30:00Z","snaps":[]}

completePebblePayload("DAA2A992-16D1-459A-90D0-E3D42FB863A9", {
  intensity: 2, positiveness: 1, valenceConfirmed: true, emotionId: "332CABE9-C76D-D737-BBA6-F2D7024DE808",
  domainIds: ["5B0C3D3E-1111-4C6A-9A3A-0D6C2F1E2A10"], soulIds: [], collectionIds: [], visibility: "private" })
// → {"moment_id":"daa2a992-16d1-459a-90d0-e3d42fb863a9","emotion_id":"332cabe9-c76d-d737-bba6-f2d7024de808","intensity":2,"positiveness":1,"visibility":"private","domain_ids":["5b0c3d3e-1111-4c6a-9a3a-0d6c2f1e2a10"],"soul_ids":[],"collection_ids":[]}
```

Also cover:
- `completePebblePayload` returns `null` for `emotionId: null`, for `domainIds: []` and for `valenceConfirmed: false`.
- No builder output contains the substring `null`.
- The whole-second truncation does not round: `"…:09.999Z"` → `"…:09Z"`.

### Task 1.5: Error mapping (`lib/data/moment-write-error.ts`)
- [ ] Implement it as in **Design**.
- [ ] Tests:

| Input | Expect |
|---|---|
| `IOS_ERR_MOMENT_EMPTY` | `momentEmpty` |
| `IOS_ERR_NEEDS_DOMAIN` | `pebbleNeedsDomain` |
| `{"code":"42501","details":"aaaaaaaa-0000-4000-8000-000000000001","hint":null,"message":"soul_not_owned"}` | `soulNotOwned` |
| `{"code":"P0002","details":null,"hint":null,"message":"moment_not_found"}` | `momentNotFound` |
| `{"code":"P0002","details":null,"hint":null,"message":"snap_not_found"}` | `momentNotFound` (P0002 rule) |
| `{"code":"22023","details":"snaps must be an array","hint":null,"message":"invalid_payload"}` | `generic` |
| `{"code":"P0001","details":null,"hint":null,"message":"media_quota_exceeded"}` | `mediaQuotaExceeded` |
| `new Error("Failed to fetch")` | `generic` |

### Task 1.6: Provider reads
- [ ] `loadFromSupabase`: replace the `v_pebbles_full` query with the `v_moments_full` query in **Design**. Snap signing iterates moments (every original plus the first snap's thumb, one batch, as today).
- [ ] Build `moments = rows.map((r) => momentFromViewRow(r, signed))`, sorted by `happened_at` descending, with `id` as tie-break.
- [ ] Set `pebbles = moments.filter(isCompleted).map(legacyPebbleFromMoment)`.
- [ ] Keep the error message `Failed to load moments: …`.
- [ ] `getPebble` keeps searching `store.pebbles` (by pebble id) for the unported `usePebble`. `PebblePeek` and the detail page pass the Spine's id, which is still the pebble id here, so nothing changes yet.

### Task 1.7: A connection's shared pebbles off compat
- [ ] Add `lib/data/connection-pebble-row.ts`:

```ts
export function sharedConnectionPebbleFromRow(row: Record<string, unknown>, glyph: PebbleGlyph | null): SharedConnectionPebble | null
```

It reads `row.moment.{name,happened_at}` and `row.emotion.{id,slug,name,color}`. It returns null (logged at the call site) on a broken embed, as today.
- [ ] Rewrite the provider query:

```ts
const { data, error } = await this.supabase
  .from("pebbles")
  .select("id, visibility, intensity, positiveness, emotion:emotions(id, slug, name, color), moment:moments!inner(name, happened_at)")
  .eq("user_id", peerId)
// glyphs: one resolve_pebble_glyph per row, in parallel; a failed call → null glyph + console.warn
const glyphs = await Promise.all(rows.map(async (r) => {
  const { data: g, error: gErr } = await this.supabase.rpc("resolve_pebble_glyph", { p_pebble_id: r.id as string })
  if (gErr) { console.warn("[connections] resolve_pebble_glyph failed", r.id, gErr); return null }
  return pebbleGlyphFromRow(g)
}))
// sort newest moment.happened_at first, client side
```

- [ ] **Capture the payload before writing the test.**
  1. Sign in as a test account that has a mutual connection with a `private` pebble.
  2. Run the query once with `curl` against the linked project: `GET /rest/v1/pebbles?select=…&user_id=eq.<peer>` with `apikey` and `Authorization: Bearer <jwt>`.
  3. Paste the response verbatim into `CONNECTION_PEBBLES_CAPTURED`, with a comment naming the date and the query.
- [ ] Tests:
  - the captured row with `glyph = null` maps every field;
  - a row with `"moment": null` → `null`;
  - a row with `"emotion": {"id":"x"}` → `null`;
  - `happened_at` with microseconds is kept.

### Task 1.8: Writers on the provider

```ts
async saveMoment(payload: SaveMomentPayload): Promise<string> {
  const { data, error } = await this.supabase.rpc("save_moment", { payload })
  if (error || typeof data !== "string") {
    console.error("[moments] save_moment failed", error ?? data)
    throw new MomentWriteError("save_moment", error)
  }
  await this.reloadAfter("save_moment")
  return data
}
async completePebble(payload: CompletePebblePayload): Promise<string> { /* same shape, "complete_pebble" */ }
async deleteMoment(momentId: string): Promise<void> {
  const { data, error } = await this.supabase.rpc("delete_moment", { p_moment_id: momentId })
  if (error) throw new Error(`delete_moment failed: ${error.message}`)
  const paths = deleteMomentPaths(data)              // pure, in moment-row.ts: string[] from {storage_paths}
  if (paths.length > 0) {
    const { error: rmErr } = await this.supabase.storage.from(PEBBLE_MEDIA_BUCKET)
      .remove(paths.flatMap((p) => [`${p}/original.jpg`, `${p}/thumb.jpg`]))
    if (rmErr) console.warn("[moments] snap file cleanup failed (orphan sweep is the backstop)", rmErr)
  }
  await this.reloadAfter("delete_moment")
}
private async reloadAfter(fn: string): Promise<void> {
  try { await this.loadFromSupabase() } catch (err) { console.warn(`[moments] reload after ${fn} failed`, err) }
}
```

- [ ] `deleteMomentPaths(IOS_DELETE_MOMENT)` returns one path. `deleteMomentPaths(null)` and `deleteMomentPaths({})` return `[]`.
- [ ] `deletePebble` keeps its RPC and also uses `reloadAfter`.
- [ ] The `supabase.rpc` names are typed from `@pbbls/supabase` only if web already imports `Database`; it does not, so the names stay string literals.

### Task 1.9: Hooks
- [ ] `useMoments()` returns `{ moments, loading, saveMoment, completePebble, deleteMoment, deletePebble, uploadSnap }`. `completePebble(payload, { onKarmaEarned?, reason })`:
  - reads `before = provider.getStore().karma`, then writes;
  - `setStore(after)`, computes the delta and calls `onKarmaEarned` or `notifyKarma(delta, reason)`;
  - calls `fireAchievementCheck(provider)`.
  `saveMoment`, `deleteMoment` and `deletePebble` set the store and fire nothing.
- [ ] `useMoment(id)` returns `{ moment, loading }` from `store.moments`, plus `byPebbleId(id)` for the redirect fallback.

### Task 1.10: i18n — the error table (R10, R15)

Add the `moment` namespace in `en.json` after `pebble`, and the same in `fr.json`.

| key | EN | FR |
|---|---|---|
| `moment.untitled` | Untitled | Sans titre |
| `moment.errors.momentEmpty` | Add a name or a photo first. | Ajoute d'abord un nom ou une photo. |
| `moment.errors.mediaQuotaExceeded` | Photo limit reached on this moment. | Limite de photos atteinte pour ce moment. |
| `moment.errors.pebbleNeedsEmotion` | Pick an emotion first. | Choisis d'abord une émotion. |
| `moment.errors.pebbleNeedsValence` | Pick how it landed first. | Choisis d'abord ce que ça t'a fait. |
| `moment.errors.pebbleNeedsDomain` | Pick a part of life first. | Choisis d'abord un domaine de ta vie. |
| `moment.errors.soulNotOwned` | One of those souls is gone. Remove it and try again. | Une de ces âmes n'existe plus. Retire-la et réessaie. |
| `moment.errors.collectionNotOwned` | That collection is gone. Pick another one. | Cette collection n'existe plus. Choisis-en une autre. |
| `moment.errors.momentNotFound` | This moment no longer exists. | Ce moment n'existe plus. |
| `moment.errors.notAuthenticated` | You must be signed in to save. | Tu dois être connecté pour enregistrer. |
| `moment.errors.generic` | Couldn't save. Please try again. | Impossible d'enregistrer. Réessaie. |
| `moment.errors.deleteTitle` | Couldn't delete | Impossible de supprimer |
| `moment.errors.deleteMessage` | Something went wrong. Please try again. | Quelque chose n'a pas fonctionné. Réessaie. |

### Task 1.11: Verify
- [ ] Lint, test and build green.
- [ ] Dev server: the Path, a pebble's peek, a soul page, a collection page and a connection's detail render as before.
- [ ] The network tab shows `v_moments_full` and no `v_pebbles_full`.
- [ ] Recording through the old flow still works (`compose-pebble`).

---

## Part 2 — the Complete flow (`feat/1050-web-moments-complete`)

**Files:**
- **Create:**
  - `lib/record/complete.ts`, `lib/record/complete.test.ts`
  - `lib/record/complete-draft-store.ts`, `lib/record/complete-draft-store.test.ts`
  - `lib/hooks/useCompleteAutosave.ts`
  - `components/record/complete/CompleteFlow.tsx`, `CompleteStepContent.tsx`, `CompleteCollectionStep.tsx`, `CompleteSuccessStep.tsx`, `complete-step-copy.ts`
  - `app/record/complete/[id]/page.tsx`
- **Modify:**
  - `lib/record/steps.ts` (add `COMPLETE_STEPS`, generic helpers)
  - `lib/hooks/useRecordFlowHistory.ts` (generic over a steps array)
  - `components/record/flow/RecordFlowChrome.tsx` (takes `steps` / `counted`)
  - `components/record/flow/RecordFlow.tsx` (passes `RECORD_STEPS`, no behaviour change)
  - `lib/data/useSupabaseAuth.ts` (clear Complete drafts on logout and deleteAccount)
  - i18n

### Task 2.1: Generic step helpers (a pure move of behaviour)
- [ ] `steps.ts` gains:

```ts
export const COMPLETE_STEPS = ["valence", "emotion", "domain", "souls", "collection", "privacy", "success"] as const
export type CompleteStep = (typeof COMPLETE_STEPS)[number]
export function stepIndexIn<S extends string>(steps: readonly S[], step: S): number
export function nextIn<S extends string>(steps: readonly S[], step: S): S | null
export function previousIn<S extends string>(steps: readonly S[], step: S): S | null
```

  The existing `RECORD_STEPS` helpers delegate to these.
- [ ] Make `useRecordFlowHistory<S extends string>(steps: readonly S[], step: S, onPopTo: (s: S) => void)` generic.
- [ ] `RecordFlowChrome` takes `steps` and `counted` props: dots = `counted.length`, back hidden on the first step.
- [ ] `RecordFlow` passes the record arrays.
- [ ] `flow.test.ts` passes unchanged. Commit this as its own task (pure move).

### Task 2.2: Reducer (`lib/record/complete.ts`)

```ts
export type CompleteFlowState = { draft: CompleteDraft; step: CompleteStep }
export type CompleteFlowAction =
  | { type: "advance" } | { type: "back" } | { type: "goTo"; step: CompleteStep }
  | { type: "selectValence"; intensity: Intensity; positiveness: Positiveness }   // commits in place, sets valenceConfirmed
  | { type: "selectEmotion"; id: string }                                         // commit + advance
  | { type: "selectDomain"; id: string }                                          // [id], commit + advance
  | { type: "toggleSoul"; id: string }                                            // no advance
  | { type: "selectCollection"; id: string | null }                               // [id] or [], commit + advance
  | { type: "selectVisibility"; value: Visibility }                               // no advance
  | { type: "hydrate"; draft: CompleteDraft }                                     // → firstGap
  | { type: "completed" }                                                         // → success
export function completeDefaults(): CompleteDraft   // intensity 2, positiveness 0, valenceConfirmed false, visibility "secret"
export function isDraftComplete(d: CompleteDraft): boolean // valenceConfirmed && emotionId && domainIds.length === 1
export function firstGap(state: CompleteFlowState): CompleteStep // valence → emotion → domain → else "privacy"
```

- `advance` on `valence` sets `valenceConfirmed` (Continue confirms the seed).
- Optional steps are `souls` and `collection`; their button reads Skip when empty and Done otherwise.
- [ ] Tests mirror `flow.test.ts`:
  - emotion and domain picks advance;
  - a valence pick does not advance; Continue confirms it;
  - collection pick advances, and `null` selects none;
  - souls toggle keeps the step;
  - privacy select keeps the step;
  - `back` from valence → no-op;
  - `hydrate` of a draft with emotion but no domain → `domain`;
  - `hydrate` of a complete draft → `privacy`;
  - `completed` → `success`, after which `back` is a no-op.

### Task 2.3: Autosave store and hook
- [ ] Implement `complete-draft-store.ts`:

```ts
export const COMPLETE_DRAFT_PREFIX = "pbbls-complete-draft:"
export type CompleteDraftSnapshot = {
  owner_id: string; moment_id: string; intensity?: Intensity; positiveness?: Positiveness
  emotion_id?: string; domain_ids?: string[]; soul_ids?: string[]; collection_ids?: string[]; visibility?: Visibility
}
export function snapshotFromDraft(ownerId: string, momentId: string, d: CompleteDraft): CompleteDraftSnapshot | null // null when empty
export function draftFromSnapshot(s: CompleteDraftSnapshot, known: { soulIds: ReadonlySet<string>; collectionIds: ReadonlySet<string> }): CompleteDraft
export function readCompleteDraft(store: DraftStorage, ownerId: string, momentId: string): CompleteDraftSnapshot | null
export function writeCompleteDraft(store: DraftStorage, snap: CompleteDraftSnapshot): void
export function clearCompleteDraft(store: DraftStorage, momentId: string): void
export function clearAllCompleteDrafts(store: DraftStorage): void
```

  - "Empty" means no emotion, no domain, no souls, no collection and valence not confirmed. Valence is only snapshotted once confirmed.
  - `soul_ids` is omitted when empty.
- [ ] Tests (fake storage backed by a `Map`):
  - a round trip keeps every key;
  - another `owner_id` → `null`, and the key is removed;
  - a body whose `moment_id` differs from the key → `null`, removed;
  - `"{not json"` → `null`, removed;
  - an empty draft → `snapshotFromDraft` returns `null`;
  - `clearAllCompleteDrafts` removes only prefixed keys (a seeded `pbbls-pending-invite` survives);
  - `draftFromSnapshot` drops a soul id missing from `known` and sets `valenceConfirmed` true when intensity is present.
- [ ] `useCompleteAutosave({ ownerId, momentId, draft, ready, onRestore })`:
  - 800 ms debounce, with a flush on `visibilitychange` (hidden) and on unmount;
  - a silent restore once when `ready`;
  - returns `{ flush, clear }`.
  Every `localStorage` access goes through a guarded accessor (`try/catch`, `console.warn`).
- [ ] `useSupabaseAuth.logout` and `deleteAccount` call `clearAllCompleteDrafts(window.localStorage)` after sign-out, inside a `try/catch` with a warn.

### Task 2.4: Flow container (`components/record/complete/CompleteFlow.tsx`)
- [ ] Props `{ momentId: string; from: "capture" | null }`. It reads `useMoment(momentId)`, `useMoments().completePebble`, `useSouls`, `useCollections` and `useAuth` (for the owner id).
- [ ] Guards, applied once the store has loaded:
  - unknown moment → `PebbleNotFound` with `moment.errors.momentNotFound`;
  - `moment.pebble !== null` and step ≠ success → `router.replace("/pebble/<id>")`. Part 3 changes this target to `/edit-pebble`.
- [ ] Steps render the existing bodies:
  - `RecordValenceStep`, `RecordEmotionStep` (categories ordered by valence), `RecordDomainStep` and `RecordSoulsStep` (inline soul creation stays: `addSoul`, then the new soul is selected);
  - `CompleteCollectionStep`: `CollectionPickerContent` with `selectedIds=draft.collectionIds`, where a tap selects one and advances, plus a "None" row and the empty state `record.complete.noCollections`;
  - `RecordPrivacyStep`.
- [ ] Privacy action: primary `record.complete.submit`, enabled by `isDraftComplete`, with a loading spinner. On error, `RecordPrivacyStep` shows `t("moment.errors." + momentErrorKey(err))` as its `publishError`.
- [ ] Submit:

```ts
const payload = completePebblePayload(momentId, flow.draft)
if (!payload) return
let karmaDelta = 0
await completePebble(payload, { reason: "pebble_created", onKarmaEarned: (d) => { karmaDelta = d } })
autosave.clear()
setResult({ karmaDelta }); dispatch({ type: "completed" })
```

- [ ] ✕ calls `autosave.flush()`, then `router.push(from === "capture" ? `/path?focus=${momentId}` : `/pebble/${momentId}`)`. It never asks.
- [ ] `useRecordFlowHistory(COMPLETE_STEPS, …)`. Back out of the success step exits, like `RecordFlow.goTo`.

### Task 2.5: Success screen (R12)
- [ ] `CompleteSuccessStep({ moment, karmaDelta, onExit })` reads the reloaded moment from the store. The derived glyph comes from `v_moments_full`, so no `resolve_pebble_glyph` call is needed.
- [ ] It draws `PebbleFramed` with `animateIn` via a `legacyPebbleFromMoment` adapter, until Part 4's `StoneLook`. A bare stone shows when `glyph` is null.
- [ ] Name or `moment.untitled` (italic, muted).
- [ ] `+N karma` only when N > 0, with an `aria-label` from `record.complete.karmaAria`.
- [ ] Button `record.flow.success.exit` → `/path?focus=<momentId>`.
- [ ] If the reload failed, the moment in the store is stale (no pebble). Draw from the draft's look (intensity, positiveness, emotion) with no glyph, and show no karma.

### Task 2.6: Route
- [ ] `app/record/complete/[id]/page.tsx` (client):

```tsx
"use client"
export default function CompletePage({ params, searchParams }: {
  params: Promise<{ id: string }>; searchParams: Promise<{ from?: string | string[] }>
}) {
  const { id } = use(params)
  const { from } = use(searchParams)
  return <CompleteFlow momentId={id.toLowerCase()} from={from === "capture" ? "capture" : null} />
}
```

### Task 2.7: i18n

| key | EN | FR |
|---|---|---|
| `record.complete.aria` | Make it a pebble, one question at a time | En faire un galet, une question à la fois |
| `record.complete.submit` | Make it a pebble | En faire un galet |
| `record.complete.noCollections` | You don't have any collections yet. | Tu n'as pas encore de collection. |
| `record.complete.noCollection` | None | Aucune |
| `record.complete.karmaAria` | Earned {count} karma | {count} karma gagnés |

- [ ] PD-W17 fixes to existing FR strings:
  - `record.flow.step.successTitle` "Ton caillou" → "Ton galet";
  - `record.flow.aria` "Capturer un caillou…" → "Capturer un galet, une question à la fois";
  - `record.flow.leaveTitle` "Tu quittes ce caillou ?" → "Tu quittes ce galet ?";
  - `record.flow.name.label` "Nom du caillou" → "Nom du galet";
  - `record.draft.*` "caillou" → "galet".
  After that, `grep -c caillou apps/web/lib/i18n/messages/fr.json` should be 0, or name what remains and why.

### Task 2.8: Verify
- [ ] Lint, test and build green.
- [ ] Manual check:
  1. Pick a bare moment's id (one made on mobile, or `select id from moments where … and not exists (pebble)`) and open `/record/complete/<id>`.
  2. Walk every step. Reload mid-flow: the draft restores silently.
  3. Complete it. The success screen shows the glyph and karma.
  4. The achievement check fires (network). The `localStorage` key is gone.
  5. Opening the route again redirects.

---

## Part 3 — read page, Edit moment, Edit pebble, delete (`feat/1050-web-moments-read-page`)

**Files:**
- **Create:**
  - `components/pebble/MomentRead.tsx`, `MomentReadToolbar.tsx`, `MomentName.tsx`, `MomentDeleteDialog.tsx`, `BlankStone.tsx`
  - `components/pebble/MomentEdit.tsx`, `components/pebble/PebbleEnrich.tsx`
  - `lib/hooks/useMomentEditDraft.ts`, `lib/hooks/usePebbleEnrichDraft.ts`
  - `lib/data/moment-edit.ts` + test (pure: dirty checks, `snapsAfterEdit`, `detachedPaths`)
  - `app/pebble/[id]/edit-pebble/page.tsx`
- **Modify:**
  - `app/pebble/[id]/page.tsx`, `app/pebble/[id]/edit/page.tsx`
  - `components/path/PebblePeek.tsx`
  - `components/path/spine/SpinePebble.tsx`, `WeekContents.tsx` (open with `moment_id`)
  - `components/pebble/PebbleDetailTiles.tsx` (`single` prop), `PebbleDetailSoulsGrid.tsx` (read-only when no handler)
  - `components/record/complete/CompleteFlow.tsx` (completed → `/edit-pebble`)
  - `components/sandbox/design/views/PebbleDetailView.tsx` (renders `MomentRead` from a fixture)
  - provider and data-provider (remove `updatePebble`, `deletePebbleMedia`, the `compose-pebble-update` branch of `invokeCompose`)
  - i18n
- **Delete:**
  - `components/pebble/PebbleDetail.tsx`, `PebbleDetailAddToolbar.tsx`
  - `components/pebble/PebbleEdit.tsx`, `PebbleEditToolbar.tsx` (if `MomentEdit` does not reuse it)
  - `lib/hooks/usePebbleDraft.ts`, `lib/data/usePebble.ts`

### Task 3.1: Name and blank stone
- [ ] `MomentName({ name, className, as })` renders `momentDisplayName(name)`, or `t("moment.untitled")` in italic with `text-muted-foreground` (R3).
- [ ] `BlankStone({ className })` draws the `PebbleOutlineBackdrop` medium/neutral silhouette:
  - `role="img"`, `aria-label={t("moment.notAPebble")}`;
  - fill `#8686861A` with a `#868686` stroke (add an optional `stroke` prop to the backdrop and default it off so current callers do not change);
  - fixed colours, not theme roles (R4).

### Task 3.2: Read page (`MomentRead`)
- [ ] Props: `{ moment: Moment; souls; collections; marks; onClose?: () => void; onDeleted: () => void }`.
- [ ] Banner: the photo (`instants[0]`) with the stone overlapping (pebble), or `BlankStone` (bare), or the stone alone.
- [ ] `MomentName` as `h1`; the date (`useFormatPeekDate`); the description if non-empty.
- [ ] **With a pebble:** a read-only privacy chip (icon and label, no popover), display tiles, then the souls grid (read-only). The tiles are emotion (always), domains (or "No domain" muted) and collections (only when any).
- [ ] **Bare:** the primary `Button` `moment.read.makeItAPebble` → `router.push("/record/complete/<id>")`. No chip, tiles or souls.
- [ ] `MomentReadToolbar`:
  - Share icon when `pebble?.visibility === "public"`. It shares `/p/<pebble.id>` with `navigator.share`, falling back to clipboard (moved from `PebbleDetail`).
  - A `DropdownMenu` (trigger `aria-label={t("moment.read.more")}`, `MoreHorizontal` icon) with `Edit moment`, `Edit pebble` (pebble only) and `Delete` (`variant="destructive"` item).
  - Close (peek only).
  - The menu is disabled while deleting.

### Task 3.3: Delete dialog (R8, R15)
- [ ] `MomentDeleteDialog({ moment, open, onOpenChange, onDeleted })` uses `AlertDialog`:
  - **bare:** `moment.delete.bareTitle`, `bareMessage`, one destructive action `deleteMoment`, and Cancel;
  - **with a pebble:** `pebbleTitle`, `pebbleMessage`, two destructive actions `deletePebbleKeepMoment` and `deleteMomentToo`, and Cancel.
- [ ] Actions:
  - `delete_pebble(moment.pebble.id)` → the dialog closes and the page stays, now bare.
  - `delete_moment(moment.id)` → `clearCompleteDraft(localStorage, id)` → `onDeleted()`. The page does `router.replace("/path")`; the peek calls `onClose()`.
  - On failure: `toast.error(t("moment.errors.deleteTitle"), { description: t("moment.errors.deleteMessage") })` (PD-W13), and the dialog stays open.

### Task 3.4: Edit moment (`/pebble/[id]/edit`, R5, R6)
- [ ] `MomentEdit` reuses `PebbleEditTitle` (clamped to 40 via `clampName` from `lib/record/flow.ts`; move `NAME_LIMIT` / `clampName` to `lib/record/name.ts` as a pure move commit), `PebbleEditDate`, `PebbleEditDescription` and `PebbleEditPicture`.
- [ ] Staging changes per R6:
  - removing the photo only detaches it in the form (no RPC);
  - a newly picked photo uploads at once (`uploadSnap`);
  - Cancel / Discard removes the *new* upload's files best-effort (existing `snap.cleanup`).
- [ ] Save is enabled when the form is dirty, `momentDisplayName(name) || snaps.length > 0`, and no upload is pending or failed. Otherwise show the hint `moment.editMoment.needsContent`.
- [ ] Save calls `saveMoment(editMomentSavePayload(id, draft))`. Then `detachedPaths(original.snaps, draft.snaps)` are removed from storage best-effort, followed by `router.replace("/pebble/<id>")`. An error shows `moment.errors.<key>` inline.
- [ ] Tests (`moment-edit.test.ts`):
  - `detachedPaths([a], [])` → `[a.storage_path]`;
  - `detachedPaths([a], [a])` → `[]`;
  - `detachedPaths([a], [b])` → `[a.storage_path]`;
  - dirty: whitespace-only name change on an untitled moment → not dirty.

### Task 3.5: Edit pebble (`/pebble/[id]/edit-pebble`, R5)
- [ ] `PebbleEnrich` is a form page:
  - **Mood:** `EmotionTile`, `DomainTile single`, and Valence (`PickerSheet` + `ValenceGrid`).
  - **Souls:** `SoulsSheet` with inline creation.
  - **Optional:** `CollectionTile single`, with a "None" row.
  - **Privacy:** `VisibilityMenu` in a popover.
  - Toolbar: Cancel and Save.
- [ ] `usePebbleEnrichDraft(pebble)` seeds `CompleteDraft` from the pebble: `valenceConfirmed: true`, and the full `domain_ids` / `collection_ids` arrays (PD-W11). The `single` tiles replace the array with `[id]` on the first touch.
- [ ] Save is enabled when dirty and `isDraftComplete`, where `domainIds.length >= 1` is allowed here. Relax `completePebblePayload` to require `domainIds.length >= 1`; its Part 1 test for `[]` → null still holds.
- [ ] On Save, `completePebble(payload, { reason: "pebble_enriched" })` shows the pill and fires the achievement check, then `router.replace("/pebble/<id>")`. Errors show inline.
- [ ] Route guard: a bare moment → `router.replace("/record/complete/<id>")`. The Part 2 guard now targets `/edit-pebble`.
- [ ] `PebbleDetailTiles`: `DomainTile` and `CollectionTile` gain `single?: boolean`. When set, a pick yields `[id]` and a pick of the selected id yields `[]` (only for collection; domain cannot be emptied).

### Task 3.6: Routes and peek
- [ ] `/pebble/[id]` uses `useMoment(id)`. If it is missing after load and `byPebbleId(id)` finds a moment, do `router.replace("/pebble/<moment.id>")`; otherwise show `PebbleNotFound`.
- [ ] `PebblePeek` takes `momentId` and renders `MomentRead`. Edit items do `onClose()` + `router.push(...)`, and delete-moment does `onClose()`.
- [ ] `SpinePebble` and `WeekContents` call `onOpen(pebble.moment_id)`. Nothing else in the Spine changes in this part.

### Task 3.7: Remove the old editor's writes
- [ ] Delete `updatePebble` from `DataProvider` and `SupabaseProvider`, as well as the `"compose-pebble-update"` member of `invokeCompose`'s union and `deletePebbleMedia` (`delete_moment_media`).
- [ ] Remove `updatePebble` from `usePebbles`.
- [ ] Grep for `updatePebble|deletePebbleMedia|compose-pebble-update|delete_moment_media` in `apps/web`: zero hits.

### Task 3.8: i18n

| key | EN | FR |
|---|---|---|
| `moment.notAPebble` | Not a pebble yet | Pas encore un galet |
| `moment.read.makeItAPebble` | Make it a pebble | En faire un galet |
| `moment.read.more` | More | Plus |
| `moment.read.editMoment` | Edit moment | Modifier le moment |
| `moment.read.editPebble` | Edit pebble | Modifier le galet |
| `moment.read.delete` | Delete | Supprimer |
| `moment.read.noDomain` | No domain | Aucun domaine |
| `moment.delete.bareTitle` | Delete this moment? | Supprimer ce moment ? |
| `moment.delete.bareMessage` | This can't be undone. | Cette action est irréversible. |
| `moment.delete.deleteMoment` | Delete moment | Supprimer le moment |
| `moment.delete.pebbleTitle` | Delete this pebble? | Supprimer ce galet ? |
| `moment.delete.pebbleMessage` | Keeping the moment leaves it on your path as a blank stone. Deleting the moment removes its photo too. This can't be undone. | Garder le moment le laisse sur ton chemin comme une pierre vierge. Supprimer le moment retire aussi sa photo. Cette action est irréversible. |
| `moment.delete.deletePebbleKeepMoment` | Delete the pebble, keep the moment | Supprimer le galet, garder le moment |
| `moment.delete.deleteMomentToo` | Delete the moment too | Supprimer aussi le moment |
| `moment.delete.cancel` | Cancel | Annuler |
| `moment.editMoment.title` | Edit moment | Modifier le moment |
| `moment.editMoment.needsContent` | A moment needs a name or a photo. | Un moment a besoin d'un nom ou d'une photo. |
| `moment.editPebble.title` | Edit pebble | Modifier le galet |
| `moment.editPebble.mood` | Mood | Humeur |
| `moment.editPebble.souls` | Souls | Âmes |
| `moment.editPebble.optional` | Optional | Facultatif |
| `moment.editPebble.collection` | Collection | Collection |
| `moment.editPebble.none` | None | Aucune |
| `moment.editPebble.privacy` | Who can see it? | Qui peut le voir ? |

Reuse `pebble.edit.{cancel,save,saving,discardTitle,discardDescription,discardConfirm,discardKeep}`. Remove keys left unused by the deleted components (`pebble.add.*`, `pebble.editIntensityAria`, `pebble.edit.toolbarTitle` if unused); the eslint/build will not flag them, so grep each one.

### Task 3.9: Verify
- [ ] Lint, test and build green.
- [ ] Manual: on a completed pebble, run each of these, then check the result:
  - Edit moment: rename, clear the description, remove the photo, save. The files are gone from storage.
  - Edit pebble: change the domain, check the karma pill and the achievement check.
  - Delete the pebble, keeping the moment. The page is now bare with the CTA, and the CTA leads to Complete.
  - Delete the moment. The page closes and the files are removed.
  - Open an old `/pebble/<pebbleId>` link: it redirects.
- [ ] No inline edits remain on the read page.

---

## Part 4 — the Path over moments (`feat/1050-web-moments-path`)

**Files:**
- **Modify:**
  - `app/path/page.tsx` (`useMoments`, `use(searchParams).focus`)
  - `components/path/PathScreen.tsx` (`moments`, `focusMomentId`)
  - `lib/utils/week-roll-entries.ts`, `lib/utils/spine/spine-week.ts`, `lib/utils/spine/persons-of-week.ts` and their tests (`Pebble` → `Moment`)
  - `components/path/spine/{SpineFeed,SpinePebble,WeekContents,SpineWeek,WeekOpening,PersonsOfWeek}.tsx`
  - `components/path/WeekPager.tsx`, `WeekRoll*.tsx` (types)
  - `components/pebble/{PebbleFramed,PebbleVisual}.tsx` + `lib/hooks/usePebbleVisual.ts` (`StoneLook`)
  - `lib/seed/spine-fixture-week.ts` and the sandbox `PathView` (fixtures to `Moment`, including one bare)
  - i18n

### Task 4.1: `StoneLook`
- [ ] `export type StoneLook = { seedId: string; intensity: Intensity; positiveness: Positiveness; emotion_id: string; glyph: PebbleGlyph | null }` and `stoneLookOf(m: Moment): StoneLook | null` (null when bare), in `moment-row.ts`.
- [ ] `PebbleFramed`, `PebbleVisual` and `usePebbleVisual` take `look: StoneLook`, and `PebbleFramed` takes `look | null`, where null draws `BlankStone`.
- [ ] Update the call sites: success (Part 2), read page (Part 3), `PebbleCard` (via the legacy adapter until Part 6), sandbox.
- [ ] `ConnectionPebbleTile` and `SharedPebbleView` already pass their own shape to `usePebbleVisual`; make them build a `StoneLook`.

### Task 4.2: Spine data over moments
- [ ] `buildWeekRollEntries(moments: Moment[], today)` and `spineWeek` place bare moments by `happened_at` like any entry.
- [ ] `personsOfWeek` reads `m.pebble?.soul_ids ?? []`.
- [ ] The week summary's pebble count is `moments.filter(isCompleted).length` (PD-W12).
- [ ] Tests:
  - `spine-week.test.ts` gains "a bare moment occupies its day (not still)" and "a week of only bare moments counts zero pebbles but draws its days";
  - existing cases are ported to `Moment` fixtures (a `momentFixture(overrides)` helper in the test file);
  - the Paris-DST cases are kept.

### Task 4.3: Spine rendering (R3, R4)
- [ ] `SpinePebble({ moment, … })`:
  - `size = pebble ? SIZE_BY_INTENSITY[pebble.intensity] : "medium"`;
  - `polarity = pebble ? POLARITY_BY_VALENCE[...] : "neutral"`;
  - `palette = pebble ? paletteByEmotionId.get(...) ?? null : null`;
  - `glyph = pebble?.glyph ?? null`;
  - `entryType(pebble?.intensity ?? 2, pebble?.positiveness ?? 0, …)`.
  The aria label for a bare moment uses `path.spine.blankAria`. The id is the moment id for `useRegisterSpinePress`, the `#pebble-` anchor and `onOpen`.
- [ ] `WeekContents` renders a bare row as `StoneSeal` with palette null at medium size.
- [ ] `PathScreen` and `PebblePeek` use moment ids throughout.

### Task 4.4: Week focus (R14)
- [ ] `PathPage({ searchParams })` reads `const { focus } = use(searchParams)` and passes `focusMomentId`.
- [ ] `PathScreen` has an effect: when `focusMomentId` is set, not yet applied, and the moment is in `moments`, call `setFocusedKey(isoWeekKey(new Date(m.happened_at)))`, mark it applied (ref), and `router.replace("/path")`. If the moment is absent, do nothing.
- [ ] Pure helper `focusKeyFor(moments, id): string | null`, tested with a moment on 2026-10-12 → `"2026-W42"` and an unknown id → `null`.

### Task 4.5: i18n

| key | EN | FR |
|---|---|---|
| `path.spine.blankAria` | {name}. {date}, {time}. Not a pebble yet. {with} Opens the moment. | {name}. {date}, {time}. Pas encore un galet. {with} Ouvre le moment. |

### Task 4.6: Verify
- [ ] Lint, test and build green.
- [ ] Manual: a mobile-made photo-only moment shows as a blank stone with its card photo and "untitled". A named bare moment shows as a blank entry. Open → read page → CTA → Complete → success → "Back to my path" lands on that week and the stone is now lit and carved. Arrow-key paging still works.

---

## Part 5 — Capture (`feat/1050-web-moments-capture`)

**Files:**
- **Create:** `lib/record/capture.ts`, `lib/record/capture.test.ts`, `components/record/capture/CaptureFlow.tsx`, `CaptureStepContent.tsx`, `capture-step-copy.ts`.
- **Modify:**
  - `app/record/page.tsx` (renders `CaptureFlow`, no search params)
  - `components/record/flow/RecordStepScaffold.tsx` (an optional `secondary` action)
  - `RecordNameStep.tsx` (subtitle-free, label prop)
  - i18n
- **Delete:**
  - `components/record/flow/RecordFlow.tsx`, `RecordStepContent.tsx`, `steps/RecordSuccessStep.tsx`, `step-copy.ts`
  - `components/record/flow/steps/RecordCollectionStep.tsx` (replaced by `CompleteCollectionStep`)
  - `lib/record/flow.ts`, `lib/record/flow.test.ts`
  - `components/record/draft-payload.ts`, `lib/hooks/useComposerAutosave.ts`, `lib/hooks/useComposerDrafts.ts`
  - `components/path/QuickPebbleEditor.tsx`
  - `lib/data/compose-error.ts` + test
  - from the provider and `DataProvider`: `createPebble`, `invokeCompose`, `CreatePebbleInput`, `UpdatePebbleInput`, `PebbleDraftPayload`, `DraftSnapPayload`
  - `addPebble` from `usePebbles`
  - `RECORD_STEPS` / `COUNTED_STEPS` and the record helpers in `steps.ts`, if nothing else uses them
- **Keep:** the step bodies, `RecordStepScaffold`, `RecordFlowChrome`, `exif-capture-date.ts`, `process-pebble-image.ts`.

### Task 5.1: Reducer (`lib/record/capture.ts`)

```ts
export const CAPTURE_STEPS = ["photo", "when", "name"] as const
export type CaptureStep = (typeof CAPTURE_STEPS)[number]
export type CaptureState = {
  step: CaptureStep; name: string; happenedAt: string; hasEditedDate: boolean; seededFromPhoto: boolean
  photo: "none" | "picking" | "uploading" | "uploaded" | "failed"; snap: PebbleSnap | undefined
}
export type CaptureAction =
  | { type: "advance" } | { type: "back" } | { type: "goTo"; step: CaptureStep }
  | { type: "setName"; value: string }                 // clampName (40)
  | { type: "setHappenedAt"; value: string }           // hasEditedDate = true, seededFromPhoto = false
  | { type: "applyCaptureDate"; value: string | null } // ignored when hasEditedDate or null
  | { type: "photo"; state: CaptureState["photo"]; snap?: PebbleSnap }
export function canSave(s: CaptureState, saving: boolean): boolean
//   (momentDisplayName(s.name) !== null || s.photo === "uploaded") && s.photo !== "uploading" && s.photo !== "picking" && s.photo !== "failed" && !saving
export function hasAnythingToKeep(s: CaptureState): boolean // a non-blank name, or any photo state but "none"
export function photoButtonIsSkip(s: CaptureState): boolean // photo === "none"
```

- [ ] Tests:
  - `photo` → `when` → `name` order, and `back` from `photo` is a no-op;
  - the name clamps at 40;
  - an EXIF date applies only before a manual date edit;
  - `canSave`:
    - false for a blank name and no photo;
    - true for a name and no photo;
    - true for a photo only once uploaded;
    - false while uploading, picking or failed;
    - false while saving;
  - `hasAnythingToKeep`: false initially and true with only a picked photo.

### Task 5.2: Container (`CaptureFlow`)
- [ ] Photo handling is ported from `RecordFlow`: pick → object URL → EXIF date → `uploadSnap`, plus Retry and Remove. Remove deletes the uploaded files best-effort through `supabase.storage` via a new `provider.discardUploadedSnap(snap)`, mirroring iOS `cancelAndCleanup`.
- [ ] Name step:
  - title `record.flow.step.nameTitle`, subtitle `record.capture.nameSubtitle` (R1);
  - primary `record.capture.save`;
  - secondary `record.capture.makePebbleNow` (text button below the primary, via the new `secondary` scaffold slot);
  - both enabled only when `canSave`, and only the clicked one spins;
  - blocked-photo messages come from `record.flow.photo.blocked*`.
- [ ] Save: `const id = await saveMoment(captureSavePayload({ name, happenedAt, snap }))`, then:
  - plain save → `router.push("/path?focus=" + id)`;
  - chain → `router.replace("/record/complete/" + id + "?from=capture")`.
  On error: keep the upload, `dispatch({ type: "goTo", step: "name" })`, and show `moment.errors.<key>`.
- [ ] ✕ (R2):
  - ignored while saving;
  - `!hasAnythingToKeep` → `router.push("/path")` silently;
  - otherwise an `AlertDialog` titled `record.capture.closeTitle` with: `record.capture.save` (only when `canSave`, does a plain save), `record.flow.discard` (destructive: `discardUploadedSnap`, then exit), and `record.capture.keepGoing` (cancel).
- [ ] On mount, run `try { localStorage.removeItem("pbbls-composer-draft") } catch {}` with a warn: the retired M47 snapshot (PD-W7, Q3).
- [ ] `useRecordFlowHistory(CAPTURE_STEPS, …)`.

### Task 5.3: i18n

| key | EN | FR |
|---|---|---|
| `record.capture.aria` | Capture a moment, one question at a time | Capturer un moment, une question à la fois |
| `record.capture.save` | Save moment | Garder le moment |
| `record.capture.makePebbleNow` | Make it a pebble now | En faire un galet maintenant |
| `record.capture.nameSubtitle` | A name, a photo, or both. | Un nom, une photo, ou les deux. |
| `record.capture.nameLabel` | Name | Nom |
| `record.capture.closeTitle` | Keep this moment? | Tu gardes ce moment ? |
| `record.capture.keepGoing` | Keep going | Continuer l'édition |
| `record.capture.charactersLeft` | {count} characters left | {count} caractères restants |

- [ ] Remove the keys owned only by the deleted composers: `record.flow.publish`, `leaveTitle`, `leaveDescription`, `keepGoing`, `record.draft.*`, and the `QuickPebbleEditor`-only keys. Grep each key before deleting it.

### Task 5.4: Verify
- [ ] Lint, test and build green.
- [ ] Grep `apps/web` for `compose-pebble|invokeCompose|ComposeError|createPebble|QuickPebbleEditor|pbbls-composer-draft"` (except the one cleanup line): zero hits.
- [ ] Manual:
  - name-only save lands on its week as a blank entry;
  - photo-only save works;
  - "Make it a pebble now" → Complete → success → Path focus;
  - ✕ with nothing closes silently;
  - ✕ with a photo shows three actions, and Discard removes the files (check storage);
  - browser Back walks the steps.

---

## Part 6 — soul and collection rows, collection glyph, legacy removal (`feat/1050-web-moments-collections`)

**Files:**
- **Modify:**
  - `lib/types.ts` (`Collection.glyph_id: string | null`; delete `Pebble`)
  - `data-provider.ts` (`Store.pebbles` removed; collection inputs gain `glyph_id`)
  - `supabase-provider.ts` (collections select, insert and update carry `glyph_id`; the derived list is removed)
  - `lib/data/useCollections.ts`
  - `components/collections/{CollectionFormDialog,CollectionDetailHeader,CollectionPebbleList}.tsx`
  - `app/collections/page.tsx` (create path)
  - `app/collections/[id]/page.tsx`, `app/souls/page.tsx`, `app/souls/[id]/page.tsx`
  - `components/souls/SoulPebbleList.tsx`, `components/path/PebbleCard.tsx`
  - `lib/data/karma.ts` + test, `lib/hooks/useGroupedPebbles*.ts`, `lib/utils/group-pebbles-*.ts`, `lib/engine/types.ts`
  - `lib/seed/*`, `components/sandbox/**`
  - i18n
- **Delete:** `lib/data/usePebbles.ts`, `legacyPebbleFromMoment`.

### Task 6.1: R9 rows
- [ ] Soul detail: `moments.filter(isCompleted).filter((m) => m.pebble.soul_ids.includes(id))`.
- [ ] Collection detail: `moments.filter(isCompleted).filter((m) => collection.pebble_ids.includes(m.pebble.id))`.
- [ ] Both sort by `happened_at` descending.
- [ ] `PebbleCard({ moment, emotion, soulNames, onSelect })` renders a `CompletedMoment` with `MomentName` and `onSelect` → `router.push("/pebble/<moment.id>")`. Rows are now buttons that open the read page.
- [ ] The collection ✕ "remove" stays and passes `m.pebble.id` (PD-W20).
- [ ] Souls page counts come from completed moments.

### Task 6.2: R11 collection glyph
- [ ] `Collection.glyph_id: string | null` is read from `collections.*`.
- [ ] `createCollection` inserts `{ user_id, name, mode, glyph_id }` in one statement, and `updateCollection` puts `glyph_id` in the same `update` as name and mode. Never call `set_collection_glyph` (R11).
- [ ] A 42501 from the usability trigger shows a toast with `moment.errors.generic` and logs `console.error`.
- [ ] `CollectionFormDialog` gains a glyph row:
  - a preview (`GlyphPreview`) or the `collections.form.glyphChoose` button opens the shared `GlyphPickerDialog` (the same one souls use, fed by `useUsableGlyphs`);
  - `collections.form.removeGlyph` clears it;
  - the footer reads `collections.form.glyphFooter`.
  `onSubmit` receives `{ name, mode, glyph_id }`. Both the create and edit dialogs use it.
- [ ] i18n:

| key | EN | FR |
|---|---|---|
| `collections.form.glyphLabel` | Glyph | Glyphe |
| `collections.form.glyphChoose` | Choose a glyph | Choisir un glyphe |
| `collections.form.removeGlyph` | Remove glyph | Retirer le glyphe |
| `collections.form.glyphFooter` | Pebbles in this collection carry its glyph. | Les galets de cette collection portent son glyphe. |

### Task 6.3: Remove the flat type
- [ ] Delete `Pebble`, `Store.pebbles`, `usePebbles` and `legacyPebbleFromMoment`. `next build` lists every remaining consumer. Port each one to `Moment`, `CompletedMoment` or `StoneLook`.
- [ ] `lib/engine/types.ts` uses `Intensity` and `Positiveness`.
- [ ] `computeKarmaDelta` and `useGroupedPebbles*` take `CompletedMoment`. They are kept, not deleted: removing dead code needs approval, so flag it in the PR body.
- [ ] Port the sandbox and seed fixtures, including one bare moment in each Path fixture.
- [ ] Verify the glyph library has no "use on pebble" affordance: `grep -rni "pebble" components/glyphs app/glyphs` shows only the `GlyphPreview` comment. It was already removed by #1094; record that in the PR body.

### Task 6.4: Final compat gate
- [ ] Each of these greps over `apps/web` must return nothing:

```bash
grep -rnE 'v_pebbles_full|path_pebbles|create_pebble|update_pebble|compose-pebble|delete_moment_media|pebble_drafts|render_svg' apps/web --include='*.ts' --include='*.tsx' | grep -v 'render_svg.*get_shared_pebble'
grep -rnE '\.from\("pebbles"\)\.select\("[^"]*(name|happened_at|glyph_id|description)' apps/web
```

  The second must match only the connection reader, whose `name` and `happened_at` sit inside the `moments!inner(…)` embed.
- [ ] Lint, test and build green.

### Task 6.5: Arkaik (hosted, `arkaik-mcp` tools only; if they are unavailable, say so and stop)
- [ ] Update the nodes listed in **Arkaik** below. Move the touched acceptances to `development` when the stack starts. Never set them to `live`.

---

## Part 7 — drop the compat layer (`chore/1050-drop-web-compat`)

**Precondition:** Parts 1–6 are merged and **deployed to production on Vercel**. Then let a soak window pass (Q4): wait until the Supabase dashboard's edge-function invocation logs show **zero** `compose-pebble` / `compose-pebble-update` calls for 24 h. Old PWA clients update on their next load (`skipWaiting` + `clientsClaim` in `app/sw.ts`); open tabs that are never reloaded are what the soak waits out.

**Files:**
- **Create:** `packages/supabase/supabase/migrations/<timestamp>_drop_web_compat.sql` (`npm run db:migration:new --workspace=packages/supabase -- drop_web_compat`).
- **Delete:**
  - `packages/supabase/supabase/functions/compose-pebble/`
  - `packages/supabase/supabase/functions/compose-pebble-update/`
  - `packages/supabase/supabase/functions/_shared/karma-delta.ts` (only those two import it)
- **Modify:**
  - `packages/supabase/supabase/functions/_shared/supabase-client.ts` (header comment → "delete-account and sweep-orphan-snaps")
  - `packages/supabase/scripts/verify-moments.ts`, `verify-pebble-visibility.ts`
  - `packages/supabase/types/database.ts`
  - `packages/supabase/CLAUDE.md`
  - `.github/workflows/supabase.yml` (comment "four function entrypoints" → "two")
  - Optional: stale "outlives #1050" comments in `verify-{orphan-snap-sweep,content-reports,public-profile,glyph-usability,account-purge,moderation-state}.ts`. Leave them; they stay true.

### Task 7.1: Migration — exactly what 20261012090400 created

20261012090400 created four objects (re-read the file before writing; nothing else in it is a definition):

| Object | Kind | Signature |
|---|---|---|
| `public.v_pebbles_full` | view (security_invoker) | — |
| `public.path_pebbles` | function, sql, invoker | `()` → table(id, name, happened_at, created_at, intensity, positiveness, render_svg, emotion, first_snap_path) |
| `public.create_pebble` | function, plpgsql, definer | `(payload jsonb) returns uuid` |
| `public.update_pebble` | function, plpgsql, definer | `(p_pebble_id uuid, payload jsonb) returns void` |

```sql
-- =============================================================================
-- Drop the moments-split web compatibility layer (#1050, spec §5)
--
-- 20261012090400 recreated v_pebbles_full, path_pebbles, create_pebble and
-- update_pebble over the moments schema so the web app kept working until its
-- Capture / Complete flows shipped. They have (#1050 Parts 1–6, deployed
-- <date>). Nothing in apps/, packages/ or a database function body references
-- them any more (grep in the PR body). The compose-pebble / compose-pebble-update
-- edge functions that called the two wrappers are deleted in the same change
-- and undeployed right after this push.
--
-- No `cascade`: an unexpected dependent must fail the push, not vanish.
-- Grants and comments go with the objects.
--
-- STANDING-RULE CHECK: no emotion or domain inserts, so
-- sync_achievement_catalog() is deliberately NOT re-run. Not a purge_account
-- or remove_connection re-emission.
-- =============================================================================

drop function public.create_pebble(jsonb);
drop function public.update_pebble(uuid, jsonb);
drop function public.path_pebbles();
drop view public.v_pebbles_full;
```

**Not dropped**, named so the reviewer does not ask:
- `get_shared_pebble`'s explicit `render_svg: null` key (20261012090200, a definer contract served to anon; removing a key is a contract change, so it gets a follow-up issue);
- `delete_moment_media` (a split writer, not compat);
- `private.jsonb_has` / `private.assert_jsonb_array`.

### Task 7.2: Rehearse locally (native Postgres, no Docker)
- [ ] Replay the chain on the Homebrew `postgresql@16` scratch database used for the split rehearsal (see `docs/superpowers/plans/2026-10-11-moments-split-database.md` "Rehearsal").
- [ ] Apply the new file. `\df public.create_pebble`, `\df public.update_pebble`, `\df public.path_pebbles` and `\dv public.v_pebbles_full` all return no rows.
- [ ] `select proname from pg_proc where prosrc ~ '(create_pebble|update_pebble|path_pebbles|v_pebbles_full)'` returns no rows.

### Task 7.3: Harnesses

**`verify-moments.ts`:**
- [ ] Delete §5 "compat (web, until #1050)" (the `create_pebble` call through the "delete_moment removes the compat moment" branch, today lines ~194–250).
- [ ] Delete header item 5, and rewrite item 6 as "happened_at is truncated to whole seconds by save_moment". The direct check at line ~108 stays.
- [ ] Recount and update `Expected: Summary: passed=N failed=0`. Seven executed checks go away, so today's 35 becomes 28; verify by running it.
- [ ] Remove `compose-pebble` from the comments.

**`verify-pebble-visibility.ts`:** replace the `v_pebbles_full` enrichment fence (the two checks at ~232–241) with the reader web now uses, so the fence and web's query are proven together:

```ts
const { data: sharedRow } = await f
  .from("pebbles")
  .select("id, visibility, moment:moments!inner(name, happened_at)")
  .eq("id", privateId).maybeSingle()
check("a connection reads the shared pebble with its moment's name (web's connection reader)",
  !!sharedRow && (sharedRow.moment as { name?: string } | null)?.name === `private ${runId}`, JSON.stringify(sharedRow))
const [{ data: cards }, { data: souls }, { data: snaps }] = await Promise.all([
  f.from("pebble_cards").select("id").eq("pebble_id", privateId),
  f.from("pebble_souls").select("soul_id").eq("pebble_id", privateId),
  f.from("snaps").select("id").eq("moment_id", priv.momentId),
])
check("…with no enrichments (cards/souls/snaps RLS stays owner-only)",
  (cards ?? []).length === 0 && (souls ?? []).length === 0 && (snaps ?? []).length === 0,
  JSON.stringify({ cards, souls, snaps }))
```

  The check count is unchanged (two for two). Header item 4 becomes "Enrichment fence — a connection reads the pebble row and its moment's name but none of the enrichments; v_moments_full shows them the moment with no pebble body". Make sure the seed gives the private pebble at least one soul or card, so the empty assertion is not vacuous; add one if it has none.

**`_shared/supabase-client.ts`:** update the comment only; the code is unchanged.

### Task 7.4: Types
- [ ] After the push (Task 7.6, step 2), run `npm run db:types:remote --workspace=packages/supabase`. Do not use `db:types`, which targets Docker and truncates the file on failure. Check that `git diff packages/supabase/types/database.ts` removes:
  - the `v_pebbles_full` view block and its four `referencedRelation: "v_pebbles_full"` relationship entries;
  - the `create_pebble`, `path_pebbles` and `update_pebble` function entries;
  and nothing else.
- [ ] If the remote generation adds unrelated drift, stop and report it.

### Task 7.5: Docs
- [ ] `packages/supabase/CLAUDE.md`: the `db:verify:moments` row drops "web compat wrappers"; "the four edge-function entrypoints" becomes "two".
- [ ] Append one `docs/decisions/log.md` entry superseding the spec's §5 status: "2026-10-xx — The web compat layer is gone; every surface is on moments", one paragraph, linking #1050. It clears the significance bar: a future reader of §5 would otherwise look for objects that no longer exist.

### Task 7.6: Deploy order (strict)
1. Confirm that web production runs Part 6, and that the soak window has passed with zero `compose-pebble*` invocations in 24 h.
2. Run `set -a; . ./.env; set +a; npm run db:push --workspace=packages/supabase` to apply the drop migration to the linked project. Then regenerate the types (Task 7.4) and commit them.
3. Undeploy the functions:
   ```bash
   cd packages/supabase
   npx supabase functions delete compose-pebble
   npx supabase functions delete compose-pebble-update
   ```
   Then `npx supabase functions list` shows only `delete-account` and `sweep-orphan-snaps`.
4. Run `npm run db:verify --workspace=packages/supabase`: all anon harnesses green, `verify-moments` reporting the new count.
5. Smoke-test production web: capture, complete, edit both, delete both.
6. Open the PR (`chore(supabase): drop the moments-split web compat layer`), then merge. CI's `schema` job replays the chain and diffs the committed types.

Rollback: re-apply `20261012090400_moments_split_web_compat.sql`'s bodies in a new migration and redeploy the two functions from git history. Nothing else depends on them.

**Verification for this part:**
- `npm run build --workspace=packages/supabase` (tsc) and `npm run lint --workspace=packages/supabase` (`deno check` over the remaining entrypoints and scripts) pass.
- `npm run test --workspace=packages/supabase` passes.
- Both greps below return nothing:
  - `grep -rnE 'v_pebbles_full|path_pebbles|create_pebble|update_pebble|compose-pebble' apps packages .github --exclude-dir=node_modules | grep -v '/migrations/'`
  - the same pattern over `packages/supabase/supabase/migrations`, restricted to files newer than the drop migration.

---

## Arkaik

Node names only; there was no Arkaik access while planning. Confirm the exact titles with `list_nodes` at implementation time.

- **Views (web):**
  - **Path** (web)
  - **Pebble detail / read page** (web) → becomes the moment read page
  - **Pebble edit** (web) → renamed **Edit moment**
  - new **Edit pebble** (web)
  - **Record flow** (web) → renamed **Capture** (web)
  - new **Complete** (web)
  - **Pebble peek** (web), if it is its own node
  - **Collection detail**, **Collections** (collection editor: glyph row), **Soul detail**, **Connection detail** (web)
  - the **Quick pebble editor / form composer** (web) node, if present, retired
- **Data models:** **Moment**, **Pebble**, **Collection** (gains `glyph_id`). Add edges from the web views.
- **Endpoints, retire:** `compose-pebble`, `compose-pebble-update`, `v_pebbles_full`, `path_pebbles`, `create_pebble`, `update_pebble`.
- **Endpoints, add web edges to:** `save_moment`, `complete_pebble`, `delete_moment`, `delete_pebble`, `v_moments_full`, `resolve_pebble_glyph`, `check_achievements`.
- **Acceptances:** the web acceptances under the views above go to `development` at stack start. The GitHub App moves them to `releasing` at merge, and `mark-live.yml` marks them `live` on production deploy. Never set `live` by hand.

---

## Open questions (product decisions, each with a recommendation)

1. **Drop inline editing on the web read page?** Today a web user changes emotion, domain, collection, souls, valence, privacy or the photo by tapping tiles on the pebble page. R5 and R7 give mobile a read-only page with two edit screens. **Recommend: drop it (PD-W2).** Each inline tap would be a partial `complete_pebble` write. That is a third edit surface parity does not have, and it breaks "every key always sent". The overflow keeps editing one tap away.

2. **Pebbles with several domains or collections** (web-made before the split). Mobile's Edit pebble prefills the first one and its save drops the rest. **Recommend for web:** send an untouched legacy set back verbatim and collapse to one only when the user changes that field (PD-W11). **Also file an iOS and an Android issue** so Edit pebble on mobile stops silently dropping the extra links; that is a data-loss path on mobile today.

3. **The old composer's local snapshot** (`pbbls-composer-draft`, M47). It has no moment id, so it cannot become a Complete draft. **Recommend:** silently remove it on the first Capture mount (PD-W7), rather than converting it into a bare moment or prompting. It was crash insurance for a composer that no longer exists.

4. **The soak window before Part 7.** `skipWaiting` + `clientsClaim` update PWA clients on their next load, but a tab left open on the old bundle still calls `compose-pebble`. **Recommend:** gate the drop on 24 h of zero edge-function invocations after the Part 6 deploy (usually reached within a day or two), not on a fixed date.

5. **`get_shared_pebble` still returns `render_svg: null`.** The comment in 20261012090200 says the key stays "until the web share page reads glyph"; web has read `glyph` since #1093. **Recommend:** leave it out of #1050. Removing a key from an anon-served definer RPC is its own small contract change, so file a follow-up issue.
