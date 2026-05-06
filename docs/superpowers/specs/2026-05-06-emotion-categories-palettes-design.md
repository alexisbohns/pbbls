# Emotion categories and color palettes — design

**Issue:** [#366](https://github.com/Bohns/pbbls/issues/366) — `[Conception] Emotion categories and color palettes`
**Date:** 2026-05-06
**Scope:** Schema only. Data population and client wiring are out of scope for this spec.

## Goal

Group emotions explicitly under categories, and let each category carry a four-color palette (primary, secondary, light, surface). The current `public.emotions.color` column becomes a legacy artifact — color is now a property of the category, not the emotion.

## Scope decisions

These were settled in brainstorming and bound the design:

- **Categories + palettes only.** No theming in this iteration. The design must not preclude theming (a future `themes` + `theme_palettes` sibling structure remains possible) but does not implement it.
- **Palette inlined on `emotion_categories`.** No separate `palettes` table. Palette reuse across categories is not a near-term need; the future theming shape doesn't require palette to be its own table today.
- **All four palette colors stored as 8-digit hex (`#RRGGBBAA`).** Uniform format across `primary_color`, `secondary_color`, `light_color`, `surface_color`. Opaque colors are `FF`-padded (`#7B5E99FF`); surface is seeded by convention as primary + `1A` (10% alpha) → `#7B5E991A`. Single source of truth in the DB; clients do not compute alpha. Designers can override alpha on any field per category if needed.
- **Plain text columns for hex.** No JSON bag of pre-rendered formats. Legacy `emotions.color` stays 6-digit hex. iOS `Color(hex:)` will be extended to dispatch on string length (6 or 8) — about six lines.
- **Performance: fetch-on-mount, in-memory cache per session.** No persistence layer, no version key. Data is small (~5 KB) and changes rarely.
- **`emotions.color` is soft-deprecated, not dropped.** Shipped iOS apps query `from("emotions")` and read `.color` directly (`apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift:220`, `EditPebbleSheet.swift:162`, `PebbleReadView.swift:52`). Removing the column would break legacy installs.
- **Data ownership shifts to the user.** Palette rows and `emotions.category_id` values are populated manually in Supabase Studio, not in the seed file. The seed file is left untouched.

## Schema

### New table — `public.emotion_categories`

| column            | type | notes                              |
|-------------------|------|------------------------------------|
| `id`              | `uuid` primary key default `gen_random_uuid()` | |
| `slug`            | `text` unique not null | e.g. `fear`, `joy`, `peace`        |
| `name`            | `text` not null        | display label (`"Fear"`, `"Joy"`)  |
| `primary_color`   | `text` not null        | 8-digit hex, e.g. `#7B5E99FF`      |
| `secondary_color` | `text` not null        | 8-digit hex, e.g. `#AE91CCFF`      |
| `light_color`     | `text` not null        | 8-digit hex, e.g. `#F2EFF5FF`      |
| `surface_color`   | `text` not null        | 8-digit hex, alpha baked, e.g. `#7B5E991A` |

RLS enabled with `select using (true)` (matches existing reference-table pattern in `20260411000000_reference_tables.sql`).

No `description` column — YAGNI; can be added later if a designer needs it.

### Altered table — `public.emotions`

One additive column:

```
category_id uuid references public.emotion_categories(id)
```

Nullable in **Phase 1** (see Rollout below). Set to `NOT NULL` in **Phase 2** after the user has populated values for all 38 rows.

`emotions.color` is left untouched. New clients ignore it; old clients keep using it.

### Index

```
create index emotions_category_id_idx on public.emotions(category_id);
```

For the view's join.

### View — `public.v_emotions_with_palette`

INNER JOIN of `emotions` and `emotion_categories` on `category_id`. New clients fetch this once on app mount.

Columns returned:

- `id`, `slug`, `name`, `color` (legacy) — from `emotions`
- `category_id`, `category_slug`, `category_name`
- `primary_color`, `secondary_color`, `light_color`, `surface_color` — from `emotion_categories`

INNER JOIN means emotions with a null `category_id` are hidden from view results during the manual-population window between Phase 1 and Phase 2. This is intentional — clients are not wired to consume the view in this PR, so partial-list-during-rollout is not a concern, and post-Phase-2 the generated TypeScript types are non-null on every palette field (LEFT JOIN would force `string | null` forever, even after the underlying column is `NOT NULL`).

The view is a `select` join — no SECURITY DEFINER, RLS on underlying tables governs access. Both `emotions` and `emotion_categories` have `select using (true)` policies, so the view is readable by any authenticated user.

## Rollout

Two SQL phases with manual data work between them:

**Phase 1 — schema (one migration file):**
- `packages/supabase/supabase/migrations/20260506000000_emotion_categories.sql`
- Creates `emotion_categories`, RLS, alter `emotions` to add nullable `category_id`, index, view.
- Regenerate `packages/supabase/types/database.ts` and commit.

**Manual step (user, in Supabase Studio):**
- Insert 7 rows into `emotion_categories` (anger, fear, joy, peace, pride, sadness, shame) with palette values from issue #366.
- Update `category_id` for all 38 rows in `emotions`.

**Phase 2 — tighten constraint (separate migration file, after data population):**
- New migration file dated when Phase 2 is created (e.g. `20260520000000_emotions_category_not_null.sql`).
- `ALTER TABLE public.emotions ALTER COLUMN category_id SET NOT NULL;`
- Regenerate types.

Splitting into two migrations is deliberate: it lets the user run Phase 1, populate data at their own pace, and run Phase 2 only when they're ready. Bundling Phase 2's `NOT NULL` into Phase 1 would force the migration to fail until every row is populated, which couples DDL deployment to data work.

## Out of scope

The following are explicitly NOT part of this spec — each is its own future issue:

- **Theming** (multiple palette/category mappings, user-selectable theme).
- **Client wiring** (iOS service that fetches `v_emotions_with_palette` on mount; web equivalent; updating render code to consume category palette instead of `emotion.color`).
- **iOS 8-digit hex parser** (extend `Color(hex:)` at `apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift:101` to dispatch on string length 6 vs 8). Required before iOS can render any palette color; lives in the client-wiring follow-up.
- **Dropping `emotions.color`.** Possible only after every shipped iOS version reading the column has been deprecated. Not on the near-term roadmap.
- **Admin UI for editing palettes.** Editing happens in Supabase Studio for now.
- **Seed file changes.** Reference-data seed is no longer the source of truth for emotions/categories; data lives only in the remote DB.

## Risks

- **`db:reset` no longer reproduces production state** for emotion/category data. This is already true for the user's workflow (per project memory: prefer remote Supabase over local Docker), but worth flagging if a contributor tries to bring up a fresh local instance.
- **Type generation between Phase 1 and Phase 2** will type `category_id` as `string | null`. Code written against the Phase-1-generated types must handle the null case. After Phase 2 the type tightens to `string`. Anything coded against the view is unaffected — INNER JOIN guarantees a non-null category at query level.
- **`v_emotions_with_palette` is partial during the manual-population window.** Emotions whose `category_id` is null are excluded from view results until Phase 2 lands. No client consumes the view in this PR, so this is an internal-state risk only.
