# Database Schema Design — Local to Supabase Migration

## Overview

Migrate the Pebbles data layer from client-side localStorage to a PostgreSQL-first Supabase backend. The schema is fully relational with proper join tables for N:N relationships, JSONB views for consolidated reads, and RPC functions for atomic multi-table writes.

## Principles

- **PostgreSQL-first:** normalized tables, join tables, referential constraints. No document-style storage.
- **UUID primary keys everywhere** — including reference tables. Human-readable identifiers are stored as `slug` columns, never used as FK targets.
- **`user_id` on every content table** for efficient RLS via `auth.uid()` equality checks.
- **Supabase Auth** handles authentication. Custom `profiles` table handles app-specific preferences.
- **Layered migrations** — tables first, then views, then RPCs. Each layer is independently reviewable and evolvable.

## Migration Strategy

Four sequential migrations:

| # | Migration | Content |
|---|-----------|---------|
| 1 | Reference tables | `emotions`, `domains`, `card_types`, `pebble_shapes` + seed data |
| 2 | Core tables | `profiles`, `pebbles`, `souls`, `glyphs`, `collections`, `pebble_cards`, `snaps`, join tables, RLS policies, indexes, `updated_at` trigger |
| 3 | Views | `v_pebbles_full`, `v_karma_summary`, `v_bounce` |
| 4 | RPCs | `create_pebble`, `update_pebble`, `delete_pebble` |

---

## Migration 1 — Reference Tables

Global read-only lookup tables. No `user_id`, no RLS. Access control: `SELECT` granted to `authenticated` role, no `INSERT`/`UPDATE`/`DELETE`.

### emotions

| Column | Type | Constraints |
|--------|------|-------------|
| id | uuid | PK, DEFAULT gen_random_uuid() |
| slug | text | UNIQUE, NOT NULL |
| name | text | NOT NULL |
| color | text | NOT NULL |

Seeded with 16 rows: joy, sadness, anger, fear, disgust, surprise, love, pride, shame, guilt, anxiety, nostalgia, gratitude, serenity, excitement, awe.

### domains

| Column | Type | Constraints |
|--------|------|-------------|
| id | uuid | PK, DEFAULT gen_random_uuid() |
| slug | text | UNIQUE, NOT NULL |
| name | text | NOT NULL |
| label | text | NOT NULL |

Seeded with 5 rows: zoe (Health & body), asphaleia (Security & comfort), philia (Relationships), time (Recognition & community), eudaimonia (Self-actualization).

### card_types

| Column | Type | Constraints |
|--------|------|-------------|
| id | uuid | PK, DEFAULT gen_random_uuid() |
| slug | text | UNIQUE, NOT NULL |
| name | text | NOT NULL |
| prompt | text | NOT NULL |

Seeded with 4 rows: free, feelings, thoughts, behaviour.

### pebble_shapes

| Column | Type | Constraints |
|--------|------|-------------|
| id | uuid | PK, DEFAULT gen_random_uuid() |
| slug | text | UNIQUE, NOT NULL |
| name | text | NOT NULL |
| path | text | NOT NULL |
| view_box | text | NOT NULL |

Seeded with 6 rows: river-smooth, creek-flat, moss-round, canyon-long, shore-wide, dusk-pebble.

---

## Migration 2 — Core Tables, Join Tables, RLS, Indexes

### Auth integration

Supabase `auth.users` replaces the custom `accounts` table. Custom `sessions` table is dropped — Supabase handles session management via JWTs.

### profiles

| Column | Type | Constraints |
|--------|------|-------------|
| id | uuid | PK, DEFAULT gen_random_uuid() |
| user_id | uuid | UNIQUE, NOT NULL, REFERENCES auth.users(id) |
| display_name | text | NOT NULL |
| onboarding_completed | boolean | NOT NULL, DEFAULT false |
| color_world | text | NOT NULL, DEFAULT 'blush-quartz' |
| terms_accepted_at | timestamptz | |
| privacy_accepted_at | timestamptz | |
| created_at | timestamptz | NOT NULL, DEFAULT now() |
| updated_at | timestamptz | NOT NULL, DEFAULT now() |

### pebbles

| Column | Type | Constraints |
|--------|------|-------------|
| id | uuid | PK, DEFAULT gen_random_uuid() |
| user_id | uuid | NOT NULL, REFERENCES auth.users(id) |
| name | text | NOT NULL |
| description | text | |
| happened_at | timestamptz | NOT NULL |
| intensity | smallint | NOT NULL, CHECK (intensity BETWEEN 1 AND 3) |
| positiveness | smallint | NOT NULL, CHECK (positiveness BETWEEN -1 AND 1) |
| visibility | text | NOT NULL, DEFAULT 'private' |
| emotion_id | uuid | NOT NULL, REFERENCES emotions(id) |
| glyph_id | uuid | REFERENCES glyphs(id) |
| created_at | timestamptz | NOT NULL, DEFAULT now() |
| updated_at | timestamptz | NOT NULL, DEFAULT now() |

### souls

| Column | Type | Constraints |
|--------|------|-------------|
| id | uuid | PK, DEFAULT gen_random_uuid() |
| user_id | uuid | NOT NULL, REFERENCES auth.users(id) |
| name | text | NOT NULL |
| created_at | timestamptz | NOT NULL, DEFAULT now() |
| updated_at | timestamptz | NOT NULL, DEFAULT now() |

### glyphs

| Column | Type | Constraints |
|--------|------|-------------|
| id | uuid | PK, DEFAULT gen_random_uuid() |
| user_id | uuid | NOT NULL, REFERENCES auth.users(id) |
| name | text | |
| shape_id | uuid | NOT NULL, REFERENCES pebble_shapes(id) |
| strokes | jsonb | NOT NULL, DEFAULT '[]' |
| view_box | text | NOT NULL |
| created_at | timestamptz | NOT NULL, DEFAULT now() |
| updated_at | timestamptz | NOT NULL, DEFAULT now() |

### collections

| Column | Type | Constraints |
|--------|------|-------------|
| id | uuid | PK, DEFAULT gen_random_uuid() |
| user_id | uuid | NOT NULL, REFERENCES auth.users(id) |
| name | text | NOT NULL |
| mode | text | CHECK (mode IN ('stack', 'pack', 'track')) |
| created_at | timestamptz | NOT NULL, DEFAULT now() |
| updated_at | timestamptz | NOT NULL, DEFAULT now() |

### pebble_cards

| Column | Type | Constraints |
|--------|------|-------------|
| id | uuid | PK, DEFAULT gen_random_uuid() |
| pebble_id | uuid | NOT NULL, REFERENCES pebbles(id) ON DELETE CASCADE |
| species_id | uuid | NOT NULL, REFERENCES card_types(id) |
| value | text | NOT NULL |
| sort_order | smallint | NOT NULL, DEFAULT 0 |

### snaps

| Column | Type | Constraints |
|--------|------|-------------|
| id | uuid | PK, DEFAULT gen_random_uuid() |
| pebble_id | uuid | NOT NULL, REFERENCES pebbles(id) ON DELETE CASCADE |
| user_id | uuid | NOT NULL, REFERENCES auth.users(id) |
| storage_path | text | NOT NULL |
| sort_order | smallint | NOT NULL, DEFAULT 0 |
| created_at | timestamptz | NOT NULL, DEFAULT now() |

### karma_events

| Column | Type | Constraints |
|--------|------|-------------|
| id | uuid | PK, DEFAULT gen_random_uuid() |
| user_id | uuid | NOT NULL, REFERENCES auth.users(id) |
| delta | smallint | NOT NULL |
| reason | text | NOT NULL |
| ref_id | uuid | |
| created_at | timestamptz | NOT NULL, DEFAULT now() |

No `updated_at` — karma events are immutable (append-only log).

### Join tables

All join tables use composite primary keys. Both FK columns have `ON DELETE CASCADE` (except `pebble_domains.domain_id` — domains are reference data that should never be deleted, so no CASCADE).

**pebble_souls**

| Column | Type | Constraints |
|--------|------|-------------|
| pebble_id | uuid | NOT NULL, REFERENCES pebbles(id) ON DELETE CASCADE |
| soul_id | uuid | NOT NULL, REFERENCES souls(id) ON DELETE CASCADE |

PK: (pebble_id, soul_id)

**pebble_domains**

| Column | Type | Constraints |
|--------|------|-------------|
| pebble_id | uuid | NOT NULL, REFERENCES pebbles(id) ON DELETE CASCADE |
| domain_id | uuid | NOT NULL, REFERENCES domains(id) |

PK: (pebble_id, domain_id)

**collection_pebbles**

| Column | Type | Constraints |
|--------|------|-------------|
| collection_id | uuid | NOT NULL, REFERENCES collections(id) ON DELETE CASCADE |
| pebble_id | uuid | NOT NULL, REFERENCES pebbles(id) ON DELETE CASCADE |

PK: (collection_id, pebble_id)

### updated_at trigger

A reusable trigger function applied to all tables with `updated_at`:

```sql
CREATE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

Applied to: `profiles`, `pebbles`, `souls`, `glyphs`, `collections`.

### RLS policies

RLS enabled on all user-owned tables. Same pattern everywhere:

```
SELECT: user_id = auth.uid()
INSERT: user_id = auth.uid()
UPDATE: user_id = auth.uid()
DELETE: user_id = auth.uid()
```

For join tables without a direct `user_id`, RLS checks ownership by joining to the parent table:
- `pebble_souls`, `pebble_domains`, `pebble_cards`: ownership verified via `pebble_id → pebbles.user_id`
- `collection_pebbles`: ownership verified via `collection_id → collections.user_id`

### Indexes

| Table | Column(s) | Rationale |
|-------|-----------|-----------|
| profiles | user_id | RLS, unique lookup |
| pebbles | user_id | RLS |
| pebbles | happened_at | Timeline ordering |
| pebbles | emotion_id | Filter by emotion |
| souls | user_id | RLS |
| glyphs | user_id | RLS |
| collections | user_id | RLS |
| pebble_cards | pebble_id | Join to parent |
| snaps | pebble_id | Join to parent |
| karma_events | user_id | RLS, aggregation |

---

## Migration 3 — Views

### v_pebbles_full

Consolidated view returning one row per pebble with all related data as JSONB:

| Column | Type | Source |
|--------|------|--------|
| *(all pebble columns)* | | pebbles |
| emotion | jsonb | `{ id, slug, name, color }` from emotions |
| glyph | jsonb or null | `{ id, name, shape_id, strokes, view_box }` from glyphs |
| cards | jsonb | `[{ id, species_id, value, sort_order }]` from pebble_cards |
| souls | jsonb | `[{ id, name }]` from pebble_souls + souls |
| domains | jsonb | `[{ id, slug, name, label }]` from pebble_domains + domains |
| snaps | jsonb | `[{ id, storage_path, sort_order }]` from snaps |
| collections | jsonb | `[{ id, name, mode }]` from collection_pebbles + collections |

Built with LEFT JOINs and `COALESCE(jsonb_agg(...) FILTER (WHERE ... IS NOT NULL), '[]')` to return empty arrays instead of `[null]`.

RLS applies transparently — the view reads from underlying tables that have RLS enabled.

### v_karma_summary

| Column | Type | Source |
|--------|------|--------|
| user_id | uuid | karma_events |
| total_karma | bigint | `SUM(delta)` from karma_events |
| pebbles_count | bigint | `COUNT(*)` from pebbles |

### v_bounce

| Column | Type | Source |
|--------|------|--------|
| user_id | uuid | pebbles |
| active_days | bigint | `COUNT(DISTINCT DATE(happened_at))` from pebbles WHERE happened_at >= now() - interval '28 days' |
| bounce_level | smallint | Computed from active_days using CASE expression matching the 0-7 scale (0 days→0, 1-5→1, 6-9→2, 10-13→3, 14-17→4, 18-20→5, 21-24→6, 25+→7) |

---

## Migration 4 — RPC Functions

### create_pebble(payload jsonb) → uuid

Accepts a full pebble payload and atomically creates the pebble and all related entities in a single transaction.

**Input shape:**

```jsonc
{
  // Pebble fields (required)
  "name": "string",
  "happened_at": "timestamptz",
  "intensity": 1,          // 1-3
  "positiveness": 0,       // -1, 0, 1
  "emotion_id": "uuid",

  // Pebble fields (optional)
  "description": "string",
  "visibility": "private", // default
  "glyph_id": "uuid",      // existing glyph

  // Inline creation (optional)
  "new_glyph": {
    "name": "string?",
    "shape_id": "uuid",
    "strokes": [{ "d": "string", "width": 2 }],
    "view_box": "string"
  },
  "new_souls": [{ "name": "string" }],

  // Relations (optional)
  "soul_ids": ["uuid"],       // existing souls
  "domain_ids": ["uuid"],
  "cards": [{ "species_id": "uuid", "value": "string", "sort_order": 0 }],
  "snaps": [{ "storage_path": "string", "sort_order": 0 }]
}
```

**Transaction steps:**

1. If `new_glyph` provided: INSERT into `glyphs`, use returned ID as `glyph_id`
2. If `new_souls` provided: INSERT into `souls`, collect returned IDs, merge with `soul_ids`
3. INSERT into `pebbles` with scalar fields → get `pebble_id`
4. INSERT into `pebble_cards` (batch from `cards` array)
5. INSERT into `pebble_souls` (batch from merged `soul_ids`)
6. INSERT into `pebble_domains` (batch from `domain_ids`)
7. INSERT into `snaps` (batch from `snaps` array)
8. Compute karma delta using same formula as client (base +1, +1 description, +N cards, +1 souls, +1 domains, +1 glyph, +1 snaps), INSERT into `karma_events`
9. RETURN `pebble_id`

All steps use `auth.uid()` as `user_id`. If any step fails, the entire transaction rolls back.

### update_pebble(pebble_id uuid, payload jsonb) → void

Updates a pebble and its related data atomically. Only provided fields are updated; omitted fields are left untouched. Array fields (cards, soul_ids, domain_ids, snaps) are replaced wholesale when present.

**Input shape:**

```jsonc
{
  // Scalar updates (all optional)
  "name": "string",
  "description": "string",
  "happened_at": "timestamptz",
  "intensity": 1,
  "positiveness": 0,
  "visibility": "private",
  "emotion_id": "uuid",
  "mark_id": "uuid",

  // Inline creation (optional)
  "new_glyph": { "shape_id": "uuid", "strokes": [...], "view_box": "string" },
  "new_souls": [{ "name": "string" }],

  // Relation replacements (optional — omit to leave unchanged)
  "soul_ids": ["uuid"],
  "domain_ids": ["uuid"],
  "cards": [{ "species_id": "uuid", "value": "string", "sort_order": 0 }],
  "snaps": [{ "storage_path": "string", "sort_order": 0 }]
}
```

**Transaction steps:**

1. Verify pebble exists and belongs to `auth.uid()`
2. If `new_glyph`: INSERT into `glyphs`, set `glyph_id`
3. If `new_souls`: INSERT into `souls`, merge IDs into `soul_ids`
4. UPDATE `pebbles` with provided scalar fields
5. If `cards` present: DELETE existing `pebble_cards` for this pebble, INSERT new ones
6. If `soul_ids` present: DELETE existing `pebble_souls`, INSERT new ones
7. If `domain_ids` present: DELETE existing `pebble_domains`, INSERT new ones
8. If `snaps` present: DELETE existing `snaps`, INSERT new ones
9. Recompute karma: calculate new delta from current pebble state, compare with previous karma_event for this pebble, INSERT adjustment event if delta changed

### delete_pebble(pebble_id uuid) → void

1. Verify pebble exists and belongs to `auth.uid()`
2. Calculate negative karma adjustment (reverse the karma earned by this pebble)
3. INSERT negative `karma_event`
4. DELETE from `pebbles` (cascades handle join tables, cards, snaps)

---

## Entities Not Migrated

- **accounts** — replaced by `auth.users` (Supabase Auth)
- **sessions** — replaced by Supabase JWT-based session management
- **pebbles_count** — computed from `COUNT(*)` on `pebbles` in `v_karma_summary`
- **karma** (running total) — computed from `SUM(delta)` on `karma_events` in `v_karma_summary`
- **bounce / bounce_window** — computed from pebble dates in `v_bounce`

## Naming Conventions

- Table names: plural snake_case (`pebbles`, `karma_events`)
- Join tables: `parent_children` (`pebble_souls`, `collection_pebbles`)
- Column names: snake_case (`happened_at`, `sort_order`)
- FK columns: `entity_id` matching the referenced table (`pebble_id`, `soul_id`)
- Views: prefixed with `v_` (`v_pebbles_full`)
- Functions: verb_noun (`create_pebble`, `set_updated_at`)
- Reference table identifiers: UUID PKs, `slug` column for human-readable lookup
