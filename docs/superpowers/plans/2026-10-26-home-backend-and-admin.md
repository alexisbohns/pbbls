# Home tab back-end and admin (stack 1, #1254) — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the database and admin half of the iOS Home tab: the `guides` table with its admin editor, the three achievements that back the beginner checklist (two auto-unlocked firsts and the claimed blue pebble), the `people_prompts` heuristic, and the `wrap_reveals` table with its `reveal_wrap` and `home_wraps` RPCs.

**Architecture:** Four stacked PRs on `gh stack`, each one migration batch plus its Deno contract harness, run against the linked project (there is no local Docker stack; migrations are validated by `db push` and the harness). The admin part reuses the Logs pages' shape (server components, server actions, `LogForm`-style form). Clients only render what these RPCs return (brain in the back-end).

**Tech Stack:** Postgres/PLpgSQL on Supabase (RLS, `security definer` RPCs), Deno harnesses in `packages/supabase/scripts/`, Next.js 16 App Router + shadcn (base-ui) in `apps/admin`, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-26-home-tab-and-empty-states-design.md` §4 and §5. One deviation, recorded in Task 10: `home_wraps` obtains each month's cover by calling `path_month_wrap(month, p_tz)->'cover'` instead of factoring a shared function. Same SQL, same equality, and no re-emission of a 270-line function.

**Read before starting:** `packages/supabase/CLAUDE.md` (harness rules, `db:types:remote`, what CI runs), the root `CLAUDE.md` "Standing cross-surface rules" (the `purge_account` re-emission rule, the purge harness rule), and the `gh-stack` skill.

---

## Conventions that apply to every task

- **Branches** (from an up-to-date `main`, stacked in this order): `feat/1254-guides` → `feat/1254-first-five` → `feat/1254-wrap-reveals` → `feat/1254-admin-guides`. Create each with `gh stack create <name>` on top of the previous one (read the `gh-stack` skill). Never branch from a stale local main: `git fetch origin && git checkout -b <name> origin/main` for the first.
- **Migrations** go in `packages/supabase/supabase/migrations/` named `2026102709MM00_<slug>.sql` as given per task. Every migration is idempotent-safe to replay on a fresh database (`create table` without `if not exists` is fine, since the file runs once; seeds use deterministic ids).
- **Applying a migration**: this environment's classifier blocks `supabase db push`. Hand the maintainer the exact command and wait:
  ```bash
  npm run db:push --workspace=packages/supabase
  ```
  Then regenerate types with the **remote** variant (the local one needs Docker and truncates the file on failure):
  ```bash
  npm run db:types:remote --workspace=packages/supabase
  git add packages/supabase/types/database.ts
  ```
- **Running a harness**: `set -a; . ./.env; set +a; npm run db:verify:<name> --workspace=packages/supabase`. Expected output ends with `Summary: passed=N failed=0`.
- **Lint for the supabase package**: `npm run lint --workspace=packages/supabase` (`deno check` over `scripts/*.ts`) and `npm run build --workspace=packages/supabase` (tsc over `types/`).
- **Commits**: conventional, lowercase, no period, e.g. `feat(db): add the guides table`. Each commit ends with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- **PR bodies** start with `Part N of #1254` (never `Resolves`, the last part resolves), list key files, carry labels `feat`, `db`, `supabase` (part 4: `feat`, `admin`, `ui`) and milestone `M64 · New Path`, label `no-lab-note` (nothing user-visible ships here; iOS does). Never write a Kritik finding id in a body.
- **Arkaik**: each part updates the hosted map through the `arkaik-mcp` tools (export `ARKAIK_TOKEN` first; see the `arkaik` skill). If the tools are unavailable, say so and stop that step; never edit `docs/arkaik/bundle.json`.

---

## Part 1 · The `guides` table (branch `feat/1254-guides`)

### Task 1: Migration `20261027090000_guides.sql`

**Files:**
- Create: `packages/supabase/supabase/migrations/20261027090000_guides.sql`

- [ ] **Step 1: Write the migration**

```sql
-- Migration: guides
-- The "About Pebbles" readings shown at the bottom of the iOS Home tab (#1254,
-- spec 2026-10-26 §4.1). Modelled on `logs` (20260421000001): bilingual with
-- EN required and FR falling back to EN on the client, public read gated on
-- `published`, writes admin-only. `illustration` is an enum the clients draw
-- from their own components; there is no image.

create table public.guides (
  id            uuid primary key default gen_random_uuid(),
  slug          text not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  sort_order    smallint not null default 0,
  illustration  text check (illustration in (
                  'pebbles','stones','emotions','valence','domains',
                  'collections','souls','glyphs','karma')),
  title_en      text not null,
  title_fr      text,
  summary_en    text not null,
  summary_fr    text,
  body_md_en    text not null,
  body_md_fr    text,
  published     boolean not null default false,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.guides is
  'Short readings about Pebbles (the Home tab''s About section, #1254). Edited in the admin; clients read published rows ordered by sort_order and fall back to EN when FR is null.';

create trigger guides_updated_at
  before update on public.guides
  for each row execute function public.set_updated_at();

alter table public.guides enable row level security;

create policy "guides_select" on public.guides
  for select using (published = true or public.is_admin(auth.uid()));

create policy "guides_insert" on public.guides
  for insert with check (public.is_admin(auth.uid()));

create policy "guides_update" on public.guides
  for update using (public.is_admin(auth.uid()));

create policy "guides_delete" on public.guides
  for delete using (public.is_admin(auth.uid()));

create index guides_published_sort_idx on public.guides (published, sort_order);

-- Seed: the nine topics, published, with a short EN body the maintainer
-- rewrites in the admin. Deterministic ids so a fresh database and the
-- project agree.
insert into public.guides (id, slug, sort_order, illustration, title_en, title_fr, summary_en, summary_fr, body_md_en, published) values
  (md5('guides:pebbles')::uuid,     'pebbles',     10, 'pebbles',     'About Pebbles',      'À propos de Pebbles',      'The private memory of your life with the people in it.',         'La mémoire privée de ta vie avec les gens qui la peuplent.',  'Pebbles is a private journal of moments. A moment is a time and a name. A pebble is that moment completed with how it felt. Nothing here is shared unless you choose to.', true),
  (md5('guides:stones')::uuid,      'stones',      20, 'stones',      'About the stones',   'À propos des pierres',     'How a stone is built from intensity, positiveness and emotion.',  'Comment une pierre naît de l''intensité, de la valence et de l''émotion.', 'Every pebble is drawn as a stone. Its size is the intensity: small, medium or large. Its kind is the positiveness: a lava stone for a low moment, a river pebble for an even one, a gem for a bright one. Its colour is the emotion.', true),
  (md5('guides:emotions')::uuid,    'emotions',    30, 'emotions',    'About emotions',     'À propos des émotions',    'Seven families, and the finer emotions inside them.',             'Sept familles, et les émotions plus fines qu''elles abritent.', 'Each pebble carries one emotion. Emotions belong to seven families: joy, pride, anger, fear, shame, sadness and peace. The family gives the pebble its palette; the finer emotion gives it its name.', true),
  (md5('guides:valence')::uuid,     'valence',     40, 'valence',     'About valence',      'À propos de la valence',   'Bright, even or heavy: what a moment did to you.',                'Lumineux, égal ou lourd : ce qu''un moment t''a fait.', 'Positiveness says whether a moment lifted you, left you even, or weighed on you. It is not a judgement of the moment, only of how it landed. Heavy moments are as worth keeping as bright ones.', true),
  (md5('guides:domains')::uuid,     'domains',     50, 'domains',     'About domains',      'À propos des domaines',    'The areas of life a moment touches.',                             'Les domaines de la vie qu''un moment touche.', 'A domain is an area of your life: family, work, health, friends, travel, and more. A pebble can touch several. Over the months, domains show where your life is happening.', true),
  (md5('guides:collections')::uuid, 'collections', 60, 'collections', 'About collections',  'À propos des collections', 'Stacks, packs and tracks: pebbles gathered along a thread.',      'Piles, séries et parcours : des galets réunis par un fil.', 'A collection gathers pebbles along a thread. A stack is a pile, unordered. A pack is a set you complete. A track is a sequence in time. A pebble can be in several collections, and deleting a collection never deletes its pebbles.', true),
  (md5('guides:souls')::uuid,       'souls',       70, 'souls',       'About souls',        'À propos des âmes',        'The people and beings in your life.',                             'Les personnes et les êtres de ta vie.', 'A soul is someone in your life: a person, or a being like your dog. You name souls on your pebbles. Each soul''s page gathers every moment you shared. A soul can be linked to another Pebbles user if you both want to.', true),
  (md5('guides:glyphs')::uuid,      'glyphs',      80, 'glyphs',      'About glyphs',       'À propos des glyphes',     'Marks you carve, instead of photos of people.',                   'Des marques que tu graves, plutôt que des photos de gens.', 'A glyph is a small mark you carve by hand. Glyphs stand for your souls and your collections; there are no photos of people in Pebbles. You can carve as many as you like.', true),
  (md5('guides:karma')::uuid,       'karma',       90, 'karma',       'About karma',        'À propos du karma',        'A quiet count of what you keep.',                                 'Un compte discret de ce que tu gardes.', 'Karma grows as you record and complete moments and reach milestones. It is never shown to anyone else. Today it can be spent on glyphs; later it will count toward a printed book of your year.', true);
```

- [ ] **Step 2: Hand the push to the maintainer and wait**

```bash
npm run db:push --workspace=packages/supabase
```
Expected: the CLI lists `20261027090000_guides.sql` and applies it without error.

- [ ] **Step 3: Regenerate the types from the linked project**

```bash
npm run db:types:remote --workspace=packages/supabase
git diff --stat packages/supabase/types/database.ts
```
Expected: `database.ts` gains a `guides` entry under `Tables` with `Row`, `Insert`, `Update`. If the file shrinks to a few lines, the generation failed: `git checkout packages/supabase/types/database.ts` and retry with the dashboard or the MCP `generate_typescript_types` fallback named in `packages/supabase/CLAUDE.md`.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/migrations/20261027090000_guides.sql packages/supabase/types/database.ts
git commit -m "feat(db): add the guides table and its nine topics"
```

### Task 2: Harness `verify-guides.ts`

**Files:**
- Create: `packages/supabase/scripts/verify-guides.ts`
- Modify: `packages/supabase/package.json` (scripts)
- Modify: `.github/workflows/supabase.yml` (one step before "Write the run log")
- Modify: `packages/supabase/CLAUDE.md` (harness table, orphan prefixes)

- [ ] **Step 1: Write the harness**

```ts
#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for the guides table (#1254) — runs against the REMOTE project.
 *
 *   1. anon and a signed-in user read the published guides, ordered by
 *      sort_order, and the nine seeded slugs are all there.
 *   2. a signed-in user cannot insert, update or delete a guide (admin-only
 *      writes): the error is reported AND the row is re-read unchanged, because
 *      a 0-row RLS filter would also leave the value untouched while proving
 *      nothing.
 *   3. the FR fallback contract: every seeded row has title_fr and summary_fr,
 *      and body_md_fr may be null (clients fall back to EN).
 *
 * Run: set -a; . ./.env; set +a; npm run db:verify:guides --workspace=packages/supabase
 * Expected: `Summary: passed=8 failed=0`
 * Needs NO service-role key: it signs up one throwaway user (guides-verify-*@example.test)
 * and deletes it through the real `delete-account` edge function, even on failure.
 */

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");
if (!SUPABASE_URL || !ANON_KEY) {
  console.error("SUPABASE_URL and SUPABASE_ANON_KEY must be set");
  Deno.exit(2);
}

import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

let passed = 0;
let failed = 0;
function check(name: string, ok: boolean, detail?: string) {
  if (ok) { passed += 1; console.log(`✓ ${name}`); }
  else { failed += 1; console.log(`✗ ${name}${detail ? ` — ${detail}` : ""}`); }
}

const SEEDED = ["pebbles", "stones", "emotions", "valence", "domains", "collections", "souls", "glyphs", "karma"];

type Guide = {
  id: string; slug: string; sort_order: number; illustration: string | null;
  title_en: string; title_fr: string | null; summary_en: string; summary_fr: string | null;
  body_md_en: string; body_md_fr: string | null; published: boolean;
};

const runId = crypto.randomUUID().slice(0, 8);
const password = `Guides-${crypto.randomUUID()}`;
type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `guides-verify-${label}-${runId}@example.test`;
  const { data, error } = await client.auth.signUp({ email, password });
  if (error || !data.session || !data.user) {
    throw new Error(`signUp ${label}: ${error?.message ?? "no session (email confirmations on?)"}`);
  }
  return { client, id: data.user.id, token: data.session.access_token };
}

async function deleteAccount(token: string): Promise<Response> {
  return await fetch(`${SUPABASE_URL}/functions/v1/delete-account`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, apikey: ANON_KEY!, "Content-Type": "application/json" },
  });
}

const anon = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
let user: TestUser | null = null;

try {
  // 1. reads --------------------------------------------------------------------------------
  const anonRead = await anon.from("guides").select("*").order("sort_order");
  const anonRows = (anonRead.data ?? []) as Guide[];
  check("anon reads the published guides in sort_order",
    !anonRead.error && anonRows.length >= SEEDED.length
      && anonRows.every((g, i) => i === 0 || g.sort_order >= anonRows[i - 1].sort_order)
      && anonRows.every((g) => g.published),
    anonRead.error?.message ?? `${anonRows.length} rows`);
  check("the nine seeded slugs are published",
    SEEDED.every((s) => anonRows.some((g) => g.slug === s)),
    SEEDED.filter((s) => !anonRows.some((g) => g.slug === s)).join(", "));
  check("every seeded row carries title_fr and summary_fr (FR falls back to EN only for the body)",
    anonRows.filter((g) => SEEDED.includes(g.slug)).every((g) => g.title_fr && g.summary_fr && g.body_md_en),
    JSON.stringify(anonRows.filter((g) => SEEDED.includes(g.slug) && !(g.title_fr && g.summary_fr)).map((g) => g.slug)));

  user = await signUp("a");
  const u = user.client;
  const userRead = await u.from("guides").select("slug").order("sort_order");
  check("a signed-in user reads the same published set",
    !userRead.error && (userRead.data ?? []).length === anonRows.length,
    userRead.error?.message ?? `${(userRead.data ?? []).length} vs ${anonRows.length}`);

  // 2. writes are admin-only ----------------------------------------------------------------
  const { error: insErr } = await u.from("guides").insert({
    slug: `verify-${runId}`, title_en: "x", summary_en: "x", body_md_en: "x", published: true,
  });
  const { data: inserted } = await anon.from("guides").select("id").eq("slug", `verify-${runId}`);
  check("a user cannot insert a guide, and no row appears",
    insErr !== null && (inserted ?? []).length === 0,
    insErr?.message ?? "the insert was accepted");

  const target = anonRows.find((g) => g.slug === "karma")!;
  const { error: updErr } = await u.from("guides").update({ title_en: `hacked ${runId}` }).eq("id", target.id);
  const { data: after } = await anon.from("guides").select("title_en").eq("id", target.id).maybeSingle();
  check("a user cannot update a guide (error or 0 rows), and the stored title is unchanged",
    after?.title_en === target.title_en,
    `${updErr?.message ?? "no error"}; title now ${after?.title_en}`);

  const { error: delErr } = await u.from("guides").delete().eq("id", target.id);
  const { data: still } = await anon.from("guides").select("id").eq("id", target.id).maybeSingle();
  check("a user cannot delete a guide, and the row is still there",
    still?.id === target.id,
    `${delErr?.message ?? "no error"}; row ${still ? "present" : "gone"}`);

  // 3. unpublished rows are invisible to anon (an admin draft stays a draft) ---------------
  const { data: drafts } = await anon.from("guides").select("id").eq("published", false);
  check("anon sees no unpublished guide", (drafts ?? []).length === 0, `${(drafts ?? []).length} drafts visible`);
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  if (user) {
    const res = await deleteAccount(user.token);
    if (!res.ok) console.error(`cleanup: delete-account returned ${res.status}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
```

- [ ] **Step 2: Add the npm script**

In `packages/supabase/package.json`, add after `"db:verify:path-wraps"`:
```json
    "db:verify:guides": "deno run --allow-env --allow-net scripts/verify-guides.ts",
```
and append ` && npm run db:verify:guides` to the end of the `"db:verify"` chain.

- [ ] **Step 3: Run it**

```bash
set -a; . ./.env; set +a; npm run db:verify:guides --workspace=packages/supabase
```
Expected: `Summary: passed=8 failed=0`.

- [ ] **Step 4: Add the CI step**

In `.github/workflows/supabase.yml`, after the "Path wraps (#1207)" step and before "Write the run log":
```yaml
      # The guides table (#1254): published rows readable by anon and users in
      # sort_order, the nine seeded topics present, writes admin-only (error
      # AND unchanged row), drafts invisible to anon.
      - name: Guides (#1254)
        if: '!cancelled()'
        run: .github/scripts/verify-harness.sh "guides" db:verify:guides
```

- [ ] **Step 5: Document the harness**

In `packages/supabase/CLAUDE.md`, add a row to the harness table before the `db:verify` row:
```
| `npm run db:verify:guides` | `verify-guides.ts` | the `guides` table (#1254): published rows readable by anon in `sort_order`, the nine seeded topics, admin-only writes, drafts invisible | anon |
```
and add `guides-verify-` to the orphan prefixes sentence.

- [ ] **Step 6: Lint and commit**

```bash
npm run lint --workspace=packages/supabase
git add packages/supabase/scripts/verify-guides.ts packages/supabase/package.json .github/workflows/supabase.yml packages/supabase/CLAUDE.md
git commit -m "test(db): prove the guides contract in verify-guides.ts"
```

### Task 3: Arkaik and the PR for part 1

- [ ] **Step 1: Arkaik** — with `ARKAIK_TOKEN` exported, `create_node` a data model `DM-guides` (status `development`, description: the About topics, bilingual, admin-edited) and an edge from the existing admin surface node to it. If the tools are unavailable, say so and skip.
- [ ] **Step 2: Push and open the PR** with `gh stack push` (read the skill), title `feat(db): add the guides table and its contract harness`, body starting `Part 1 of #1254`, labels `feat`, `db`, `supabase`, `no-lab-note`, milestone `M64 · New Path`.

---

## Part 2 · The first five, the blue pebble and `people_prompts` (branch `feat/1254-first-five`)

### Task 4: Migration `20261027090100_first_five_achievements.sql`

**Files:**
- Create: `packages/supabase/supabase/migrations/20261027090100_first_five_achievements.sql`

The migration widens the family check, re-emits `check_achievements` with two new arms (the body below is the 20260730090000 body plus the two arms, checked by pairwise diff), seeds three rows, and adds `claim_blue_pebble()`. It also re-emits the one-shot branch of `admin_create_achievement`; since that function's body is long, do it with a targeted replace of the one `elsif` list rather than a re-emission: read `20260730150000_admin_achievement_management.sql`, copy the whole `create or replace function public.admin_create_achievement(...)` statement verbatim into this migration, and change only the line
`elsif p_family in ('first_collection', 'first_glyph', 'first_soul') then`
to
`elsif p_family in ('first_collection', 'first_glyph', 'first_soul', 'first_moment', 'first_pebble', 'first_five') then`.
Diff the two bodies before committing: `diff <(sed -n '/admin_create_achievement/,/^\$\$;/p' packages/supabase/supabase/migrations/20260730150000_admin_achievement_management.sql) <(sed -n '/admin_create_achievement/,/^\$\$;/p' packages/supabase/supabase/migrations/20261027090100_first_five_achievements.sql)` must show exactly that one line.

- [ ] **Step 1: Write the migration**

```sql
-- Migration: the first five and the blue pebble (#1254, spec 2026-10-26 §4.2)
--
-- The Home tab's beginner checklist is six achievements. Two new one-shot
-- families, first_moment and first_pebble, join first_collection, first_glyph
-- and first_soul: check_achievements() unlocks them once and they survive any
-- later deletion, so the five tasks rest on one truth. The sixth, blue-pebble
-- (family first_five), is NEVER unlocked by the check: the user claims it
-- through claim_blue_pebble() once the five are unlocked. The claim is the
-- ceremony; after it the checklist leaves Home.
--
-- Three pieces are re-emitted here and were diffed pairwise against their
-- previous definition before this file was committed:
--   * achievements.family check: + first_moment, first_pebble, first_five
--   * check_achievements(): body from 20260730090000 + two case arms
--   * admin_create_achievement(): body from 20260730150000 + the one-shot list

-- 1. the family check ---------------------------------------------------------
alter table public.achievements drop constraint achievements_family_check;
alter table public.achievements add constraint achievements_family_check check (family in (
  'pebble_count','emotion_first','domain_first','first_collection',
  'first_glyph','first_soul','glyph_count','glyph_sales',
  'first_moment','first_pebble','first_five'));

-- 2. the seed ----------------------------------------------------------------------
-- Deterministic ids like the rest of the catalog; sort_order sits in the
-- one-shot block (400s) after first-soul. karma_reward is set by the
-- maintainer in the admin afterwards (0 until then, so nothing is paid by
-- accident).
insert into public.achievements (id, slug, family, threshold, sort_order) values
  (md5('achievements:first-moment')::uuid, 'first-moment', 'first_moment', null, 430),
  (md5('achievements:first-pebble')::uuid, 'first-pebble', 'first_pebble', null, 440),
  (md5('achievements:blue-pebble')::uuid,  'blue-pebble',  'first_five',   null, 450);

-- 3. check_achievements() ------------------------------------------------------------
-- Body verbatim from 20260730090000 with two additions in the stats CTE and
-- the case: has_moment / first_moment and pebble_count >= 1 / first_pebble.
-- first_five is deliberately absent: it falls to `else false`.
create or replace function public.check_achievements()
returns table (slug text, karma_granted integer)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  return query
  with stats as (
    -- Counts may regress (deletions, a buyer's purge); permanence + the PK
    -- make that harmless. Drafts never count: pebble_drafts is a separate
    -- table, so exclusion is structural, not filtered.
    select
      (select count(*) from public.pebbles p
        where p.user_id = v_uid)                       as pebble_count,
      -- Ownership IS the custom flag: is_custom is generated as
      -- user_id is not null, so this counts carved glyphs only.
      (select count(*) from public.glyphs g
        where g.user_id = v_uid)                       as glyph_count,
      -- Sales come from entitlements, not the karma ledger: an entitlement
      -- held by another user is the sale fact and survives price changes.
      -- The <> guard is belt-and-braces (buy_glyph rejects self-purchase).
      (select count(*)
         from public.glyph_entitlements e
         join public.glyphs g on g.id = e.glyph_id
        where g.user_id = v_uid
          and e.user_id <> g.user_id)                  as glyph_sales,
      exists (select 1 from public.collections c
               where c.user_id = v_uid)                as has_collection,
      exists (select 1 from public.souls s
               where s.user_id = v_uid)                as has_soul,
      -- #1254: a moment is a thing before it is a pebble.
      exists (select 1 from public.moments m
               where m.user_id = v_uid)                as has_moment
  ),
  qualified as (
    select a.id, a.slug, a.karma_reward
    from public.achievements a, stats st
    where a.is_active
      and case a.family
        when 'pebble_count'     then st.pebble_count >= a.threshold
        when 'emotion_first'    then exists (
          select 1 from public.pebbles p
           where p.user_id = v_uid and p.emotion_id = a.emotion_id)
        when 'domain_first'     then exists (
          select 1 from public.pebble_domains pd
            join public.pebbles p on p.id = pd.pebble_id
           where p.user_id = v_uid and pd.domain_id = a.domain_id)
        when 'first_collection' then st.has_collection
        when 'first_glyph'      then st.glyph_count >= 1
        when 'first_soul'       then st.has_soul
        when 'first_moment'     then st.has_moment            -- #1254
        when 'first_pebble'     then st.pebble_count >= 1     -- #1254
        when 'glyph_count'      then st.glyph_count >= a.threshold
        when 'glyph_sales'      then st.glyph_sales >= a.threshold
        else false   -- first_five is claimed, never checked (claim_blue_pebble)
      end
  ),
  unlocked as (
    insert into public.achievement_unlocks (user_id, achievement_id)
    select v_uid, q.id from qualified q
    on conflict (user_id, achievement_id) do nothing
    returning achievement_id
  ),
  paid as (
    insert into public.karma_events (user_id, delta, reason, type, ref_id)
    select v_uid, q.karma_reward, 'grant', 'credit', q.id
    from unlocked u
    join qualified q on q.id = u.achievement_id
    where q.karma_reward > 0
    returning ref_id, delta
  )
  select q.slug, coalesce(p.delta::integer, 0)
  from unlocked u
  join qualified q on q.id = u.achievement_id
  left join paid p on p.ref_id = u.achievement_id;
end;
$$;

-- 4. claim_blue_pebble() --------------------------------------------------------------
-- The same exactly-once mechanics as the check (the unlocks PK + on conflict
-- do nothing feeding the karma insert), gated on the five one-shot unlocks.
-- A second call inserts nothing and returns no row. Raises 'not_ready' when a
-- prerequisite is missing, so a client cannot claim ahead of the data.
create or replace function public.claim_blue_pebble()
returns table (slug text, karma_granted integer)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_missing int;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  select count(*) into v_missing
  from unnest(array['first-moment','first-pebble','first-collection','first-soul','first-glyph']) req(s)
  where not exists (
    select 1 from public.achievement_unlocks u
    join public.achievements a on a.id = u.achievement_id
    where u.user_id = v_uid and a.slug = req.s);
  if v_missing > 0 then
    raise exception 'not_ready' using errcode = 'P0001';
  end if;

  return query
  with target as (
    select a.id, a.slug, a.karma_reward
    from public.achievements a
    where a.slug = 'blue-pebble' and a.is_active
  ),
  unlocked as (
    insert into public.achievement_unlocks (user_id, achievement_id)
    select v_uid, t.id from target t
    on conflict (user_id, achievement_id) do nothing
    returning achievement_id
  ),
  paid as (
    insert into public.karma_events (user_id, delta, reason, type, ref_id)
    select v_uid, t.karma_reward, 'grant', 'credit', t.id
    from unlocked u join target t on t.id = u.achievement_id
    where t.karma_reward > 0
    returning ref_id, delta
  )
  select t.slug, coalesce(p.delta::integer, 0)
  from unlocked u
  join target t on t.id = u.achievement_id
  left join paid p on p.ref_id = u.achievement_id;
end;
$$;

revoke all on function public.claim_blue_pebble() from public, anon;
grant execute on function public.claim_blue_pebble() to authenticated;

comment on function public.claim_blue_pebble() is
  'Claims the blue-pebble achievement once the five firsts are unlocked (#1254). Idempotent: a second call returns no row. Raises not_ready before the five.';

-- 5. admin_create_achievement(): the one-shot list ------------------------------------
-- >>> paste the whole statement from 20260730150000 here, changing only the
-- elsif line to:
--   elsif p_family in ('first_collection', 'first_glyph', 'first_soul', 'first_moment', 'first_pebble', 'first_five') then
```

- [ ] **Step 2: Paste `admin_create_achievement` and diff it**

```bash
diff <(sed -n '/create or replace function public.admin_create_achievement/,/^\$\$;/p' packages/supabase/supabase/migrations/20260730150000_admin_achievement_management.sql) \
     <(sed -n '/create or replace function public.admin_create_achievement/,/^\$\$;/p' packages/supabase/supabase/migrations/20261027090100_first_five_achievements.sql)
```
Expected: a single changed line, the `elsif p_family in (...)` one. Also diff the `check_achievements` statement against 20260730090000 the same way: the only changes are the `has_moment` column, the two `when` arms and the `else` comment.

- [ ] **Step 3: Hand the push to the maintainer, regenerate types, commit**

```bash
npm run db:push --workspace=packages/supabase
npm run db:types:remote --workspace=packages/supabase
git add packages/supabase/supabase/migrations/20261027090100_first_five_achievements.sql packages/supabase/types/database.ts
git commit -m "feat(db): add the first-moment, first-pebble and claimed blue-pebble achievements"
```

### Task 5: Migration `20261027090200_people_prompts.sql`

**Files:**
- Create: `packages/supabase/supabase/migrations/20261027090200_people_prompts.sql`

- [ ] **Step 1: Write the migration**

```sql
-- Migration: people_prompts (#1254, spec 2026-10-26 §4.4, D10)
-- The People tab's onboarding asks "who was there?" about the user's own
-- pebbles. The rule lives here so no client re-implements it:
--   * the pebble has no soul,
--   * at least one of its domains is OUTWARD (the constant below),
--   * its positiveness is high (1) or neutral (0): a heavy moment is never
--     asked about, whatever its domain,
--   * its moment is not hidden by moderation.
-- Latest first by happened_at. `skipped` names up to two more recent soul-less
-- pebbles the rule left out, so the client can say so in one line.
-- Totals let the client pick its state (no pebbles / prompts / small card).

create or replace function public.people_prompts(p_limit int default 3)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  -- The outward set (spec D10). A change here is a product decision, not a
  -- tuning: record it in docs/decisions/log.md.
  outward constant text[] := array['family','friends','partner','community','currentevents','dating','hobbies','fitness','travel'];
  result jsonb;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if p_limit is null or p_limit < 1 or p_limit > 10 then
    raise exception 'invalid_limit' using errcode = '22023';
  end if;

  with mine as (
    select p.id as pebble_id, m.id as moment_id, m.name, m.happened_at,
           p.intensity, p.positiveness, p.emotion_id,
           exists (select 1 from public.pebble_souls ps where ps.pebble_id = p.id) as has_soul,
           exists (select 1 from public.pebble_domains pd
                     join public.domains d on d.id = pd.domain_id
                    where pd.pebble_id = p.id and d.slug = any (outward)) as is_outward
    from public.pebbles p
    join public.moments m on m.id = p.moment_id
    where p.user_id = v_uid and m.hidden_at is null
  ),
  soulless as (select * from mine where not has_soul),
  eligible as (
    select * from soulless where is_outward and positiveness >= 0
    order by happened_at desc, pebble_id limit p_limit
  ),
  skipped as (
    select name from soulless s
    where not (s.is_outward and s.positiveness >= 0)
      and s.happened_at >= coalesce((select min(happened_at) from eligible), s.happened_at)
    order by happened_at desc limit 2
  )
  select jsonb_build_object(
    'pebbles', (select count(*) from mine),
    'souls', (select count(*) from public.souls s where s.user_id = v_uid),
    'prompts', (select coalesce(jsonb_agg(jsonb_build_object(
                  'pebble_id', pebble_id, 'moment_id', moment_id, 'name', name,
                  'happened_at', to_char(happened_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                  'intensity', intensity, 'positiveness', positiveness, 'emotion_id', emotion_id)
                  order by happened_at desc, pebble_id), '[]'::jsonb) from eligible),
    'skipped', (select coalesce(jsonb_agg(jsonb_build_object('name', name)), '[]'::jsonb) from skipped))
  into result;
  return result;
end;
$$;

revoke all on function public.people_prompts(int) from public, anon;
grant execute on function public.people_prompts(int) to authenticated;

comment on function public.people_prompts(int) is
  'The People tab''s onboarding prompts (#1254): the caller''s latest soul-less pebbles with an outward domain and a non-heavy positiveness, plus totals and the names the rule skipped. Whole-second UTC timestamps.';
```

Note the timestamp: emitted at whole seconds in UTC (the cross-surface timestamp rule). `happened_at` on `moments` is already truncated to the second by `save_moment`.

- [ ] **Step 2: Hand the push to the maintainer, regenerate types, commit**

```bash
npm run db:push --workspace=packages/supabase
npm run db:types:remote --workspace=packages/supabase
git add packages/supabase/supabase/migrations/20261027090200_people_prompts.sql packages/supabase/types/database.ts
git commit -m "feat(db): add people_prompts for the people tab's onboarding"
```

### Task 6: Harness `verify-home.ts` (achievements, claim, prompts)

**Files:**
- Create: `packages/supabase/scripts/verify-home.ts`
- Modify: `packages/supabase/package.json`, `.github/workflows/supabase.yml`, `packages/supabase/CLAUDE.md`

Part 3 extends this same file with the wraps assertions (Task 11), so it is written with sections.

- [ ] **Step 1: Write the harness**

```ts
#!/usr/bin/env -S deno run --allow-env --allow-net --allow-read --allow-write
/**
 * Acceptance test for the Home tab's back-end (#1254) — runs against the REMOTE project.
 *
 *   1. The first five: first-moment unlocks on the first save_moment and
 *      first-pebble on the first complete_pebble; both SURVIVE the deletion of
 *      the only moment (the point of backing the checklist with achievements).
 *      check_achievements never grants blue-pebble.
 *   2. claim_blue_pebble raises not_ready before the five, unlocks once
 *      (returns the row), returns nothing on a second call, and the unlock
 *      count stays one. anon is refused.
 *   3. people_prompts: totals; the no-soul filter; the outward-domain rule; the
 *      valence guard (a low pebble is never asked, whatever its domain); latest
 *      first; the skipped names; the limit; anon refused.
 *   4. (part 3) wrap_reveals / reveal_wrap / home_wraps — see the section below.
 *
 * HOME_DUMP=1 writes the verbatim people_prompts payload to
 * scripts/oracles/home/payloads/people-prompts.json for the clients' decoding tests.
 *
 * Run: set -a; . ./.env; set +a; npm run db:verify:home --workspace=packages/supabase
 * Expected: `Summary: passed=19 failed=0` (part 2); part 3 raises it.
 * Needs NO service-role key: signs up throwaway users (home-verify-*@example.test)
 * and deletes them through the real `delete-account` edge function, even on failure.
 */

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");
if (!SUPABASE_URL || !ANON_KEY) {
  console.error("SUPABASE_URL and SUPABASE_ANON_KEY must be set");
  Deno.exit(2);
}

import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

let passed = 0;
let failed = 0;
function check(name: string, ok: boolean, detail?: string) {
  if (ok) { passed += 1; console.log(`✓ ${name}`); }
  else { failed += 1; console.log(`✗ ${name}${detail ? ` — ${detail}` : ""}`); }
}

const DUMP = Deno.env.get("HOME_DUMP") === "1";
const ORACLE_DIR = new URL("./oracles/home/", import.meta.url);
async function dump(name: string, payload: unknown) {
  if (!DUMP) return;
  await Deno.mkdir(new URL("payloads/", ORACLE_DIR), { recursive: true });
  await Deno.writeTextFile(new URL(`payloads/${name}.json`, ORACLE_DIR), JSON.stringify(payload, null, 2) + "\n");
}

const runId = crypto.randomUUID().slice(0, 8);
const password = `Home-${crypto.randomUUID()}`;
type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `home-verify-${label}-${runId}@example.test`;
  const { data, error } = await client.auth.signUp({ email, password });
  if (error || !data.session || !data.user) {
    throw new Error(`signUp ${label}: ${error?.message ?? "no session (email confirmations on?)"}`);
  }
  return { client, id: data.user.id, token: data.session.access_token };
}
async function deleteAccount(token: string): Promise<Response> {
  return await fetch(`${SUPABASE_URL}/functions/v1/delete-account`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, apikey: ANON_KEY!, "Content-Type": "application/json" },
  });
}

type Unlock = { slug: string; karma_granted: number };
type Prompts = {
  pebbles: number; souls: number;
  prompts: { pebble_id: string; moment_id: string; name: string | null; happened_at: string; intensity: number; positiveness: number; emotion_id: string }[];
  skipped: { name: string | null }[];
};

/** Reference rows by slug, read as the user (reference tables are public). */
async function refs(c: SupabaseClient) {
  const { data: emotion } = await c.from("emotions").select("id").limit(1).single();
  const { data: domains } = await c.from("domains").select("id, slug").in("slug", ["family", "work", "health"]);
  const by = Object.fromEntries((domains ?? []).map((d: { id: string; slug: string }) => [d.slug, d.id]));
  if (!emotion || !by.family || !by.work || !by.health) throw new Error("reference data missing (emotion, family/work/health domains)");
  return { emotionId: emotion.id as string, family: by.family as string, work: by.work as string, health: by.health as string };
}

async function saveMoment(c: SupabaseClient, name: string, at: string): Promise<string> {
  const { data, error } = await c.rpc("save_moment", { payload: { name, happened_at: at } });
  if (error || typeof data !== "string") throw new Error(`save_moment ${name}: ${error?.message}`);
  return data;
}
async function completePebble(c: SupabaseClient, momentId: string, emotionId: string, positiveness: number, domainIds: string[], soulIds: string[] = []): Promise<string> {
  const { data, error } = await c.rpc("complete_pebble", {
    payload: { moment_id: momentId, emotion_id: emotionId, intensity: 2, positiveness, domain_ids: domainIds, soul_ids: soulIds },
  });
  if (error || typeof data !== "string") throw new Error(`complete_pebble: ${error?.message}`);
  return data;
}
async function unlockedSlugs(c: SupabaseClient): Promise<string[]> {
  const { data, error } = await c.from("achievement_unlocks").select("achievements(slug)");
  if (error) throw new Error(`unlocks: ${error.message}`);
  return (data ?? []).map((r: { achievements: { slug: string } | null }) => r.achievements?.slug ?? "?").sort();
}

const anon = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
let alice: TestUser | null = null;
let bob: TestUser | null = null;

try {
  // 1. the first five ---------------------------------------------------------------------
  alice = await signUp("a");
  const a = alice.client;
  const r = await refs(a);

  const fresh = await a.rpc("check_achievements");
  check("a fresh account unlocks nothing", !fresh.error && (fresh.data as Unlock[]).length === 0, fresh.error?.message ?? JSON.stringify(fresh.data));

  const m1 = await saveMoment(a, `first ${runId}`, "2026-10-01T10:00:00Z");
  const afterMoment = await a.rpc("check_achievements");
  const momentSlugs = ((afterMoment.data ?? []) as Unlock[]).map((u) => u.slug);
  check("the first save_moment unlocks first-moment and nothing else",
    !afterMoment.error && momentSlugs.length === 1 && momentSlugs[0] === "first-moment",
    afterMoment.error?.message ?? JSON.stringify(momentSlugs));

  await completePebble(a, m1, r.emotionId, 1, [r.family]);
  const afterPebble = await a.rpc("check_achievements");
  const pebbleSlugs = ((afterPebble.data ?? []) as Unlock[]).map((u) => u.slug).sort();
  check("the first complete_pebble unlocks first-pebble (and the catalog's pebble-count-1 tier), never blue-pebble",
    !afterPebble.error && pebbleSlugs.includes("first-pebble") && !pebbleSlugs.includes("blue-pebble") && !pebbleSlugs.includes("first-moment"),
    afterPebble.error?.message ?? JSON.stringify(pebbleSlugs));

  const del = await a.rpc("delete_moment", { p_moment_id: m1 });
  if (del.error) throw new Error(`delete_moment: ${del.error.message}`);
  const afterDelete = await unlockedSlugs(a);
  check("deleting the only moment keeps first-moment and first-pebble unlocked",
    afterDelete.includes("first-moment") && afterDelete.includes("first-pebble"), JSON.stringify(afterDelete));
  const recheck = await a.rpc("check_achievements");
  check("a re-check after the deletion grants nothing new and revokes nothing",
    !recheck.error && (recheck.data as Unlock[]).length === 0 && (await unlockedSlugs(a)).length === afterDelete.length,
    recheck.error?.message ?? JSON.stringify(recheck.data));

  // 2. the claim ---------------------------------------------------------------------------
  const early = await a.rpc("claim_blue_pebble");
  check("claim_blue_pebble raises not_ready before the five",
    !!early.error && early.error.message.includes("not_ready"), early.error?.message ?? "accepted");

  const { data: soul, error: soulErr } = await a.from("souls").insert({ user_id: alice.id, name: `Mara ${runId}` }).select("id").single();
  if (soulErr || !soul) throw new Error(`soul: ${soulErr?.message}`);
  const { error: colErr } = await a.from("collections").insert({ user_id: alice.id, name: `Autumn ${runId}`, mode: "stack" });
  if (colErr) throw new Error(`collection: ${colErr.message}`);
  const { error: glyphErr } = await a.from("glyphs").insert({
    user_id: alice.id, name: `glyph ${runId}`, view_box: "0 0 200 200",
    strokes: [{ d: "M20,20 L180,180", width: 6 }],
  });
  if (glyphErr) throw new Error(`glyph: ${glyphErr.message}`);
  const five = await a.rpc("check_achievements");
  const fiveSlugs = ((five.data ?? []) as Unlock[]).map((u) => u.slug);
  check("the soul, collection and glyph unlock their firsts, still never blue-pebble",
    !five.error && ["first-soul", "first-collection", "first-glyph"].every((s) => fiveSlugs.includes(s)) && !fiveSlugs.includes("blue-pebble"),
    five.error?.message ?? JSON.stringify(fiveSlugs));

  const claim = await a.rpc("claim_blue_pebble");
  const claimed = (claim.data ?? []) as Unlock[];
  check("claim_blue_pebble unlocks blue-pebble once the five are there",
    !claim.error && claimed.length === 1 && claimed[0].slug === "blue-pebble", claim.error?.message ?? JSON.stringify(claim.data));
  const again = await a.rpc("claim_blue_pebble");
  check("a second claim returns no row", !again.error && (again.data as Unlock[]).length === 0, again.error?.message ?? JSON.stringify(again.data));
  const unlocks = await unlockedSlugs(a);
  check("exactly one blue-pebble unlock row", unlocks.filter((s) => s === "blue-pebble").length === 1, JSON.stringify(unlocks));
  const anonClaim = await anon.rpc("claim_blue_pebble");
  check("claim_blue_pebble is refused to anon", !!anonClaim.error, JSON.stringify(anonClaim.data));

  // 3. people_prompts ----------------------------------------------------------------------
  bob = await signUp("b");
  const b = bob.client;
  const empty = await b.rpc("people_prompts");
  const e = empty.data as Prompts;
  check("people_prompts on a fresh account: zero totals and no prompts",
    !empty.error && e.pebbles === 0 && e.souls === 0 && e.prompts.length === 0 && e.skipped.length === 0,
    empty.error?.message ?? JSON.stringify(e));

  // Seed, latest first in the names: outward+bright, outward+even, outward+low (skipped),
  // inward+bright (skipped), outward+bright with a soul (not soul-less), oldest outward+bright.
  const { data: bobSoul } = await b.from("souls").insert({ user_id: bob.id, name: `Elio ${runId}` }).select("id").single();
  const seed: [string, string, number, string[], string[]][] = [
    ["oldest outward bright", "2026-10-01T09:00:00Z", 1, [r.family], []],
    ["with a soul",           "2026-10-02T09:00:00Z", 1, [r.family], [bobSoul!.id]],
    ["inward bright",         "2026-10-03T09:00:00Z", 1, [r.health], []],
    ["outward low",           "2026-10-04T09:00:00Z", -1, [r.family], []],
    ["outward even",          "2026-10-05T09:00:00Z", 0, [r.family, r.work], []],
    ["outward bright",        "2026-10-06T09:00:00Z", 1, [r.family], []],
  ];
  for (const [name, at, pos, doms, souls] of seed) {
    const mid = await saveMoment(b, `${name} ${runId}`, at);
    await completePebble(b, mid, r.emotionId, pos, doms, souls);
  }
  const got = await b.rpc("people_prompts");
  const p = got.data as Prompts;
  await dump("people-prompts", got.data);
  const names = p.prompts.map((x) => (x.name ?? "").replace(` ${runId}`, ""));
  check("totals count every pebble and every soul", p.pebbles === 6 && p.souls === 1, JSON.stringify([p.pebbles, p.souls]));
  check("prompts are the soul-less, outward, non-heavy pebbles, latest first, three at most",
    JSON.stringify(names) === JSON.stringify(["outward bright", "outward even", "oldest outward bright"]), JSON.stringify(names));
  check("a low pebble is never asked about, and an inward one neither: both are named as skipped",
    p.skipped.map((s) => (s.name ?? "").replace(` ${runId}`, "")).sort().join("|") === "inward bright|outward low",
    JSON.stringify(p.skipped));
  check("timestamps are whole-second UTC", p.prompts.every((x) => /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(x.happened_at)),
    JSON.stringify(p.prompts.map((x) => x.happened_at)));
  const limited = await b.rpc("people_prompts", { p_limit: 1 });
  check("p_limit caps the prompts", !limited.error && (limited.data as Prompts).prompts.length === 1, limited.error?.message);
  const bad = await b.rpc("people_prompts", { p_limit: 0 });
  check("p_limit 0 is refused", !!bad.error && bad.error.message.includes("invalid_limit"), bad.error?.message ?? "accepted");
  const anonPrompts = await anon.rpc("people_prompts");
  check("people_prompts is refused to anon", !!anonPrompts.error, JSON.stringify(anonPrompts.data));

  // 4. wraps (part 3) ----------------------------------------------------------------------
  // (added in Task 11)
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  for (const u of [alice, bob]) {
    if (!u) continue;
    const res = await deleteAccount(u.token);
    if (!res.ok) console.error(`cleanup: delete-account returned ${res.status}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
```

The glyph insert uses the shape `verify-glyph-usability.ts` inserts (`strokes: [{ d, width }]`, `view_box`, `name`). `delete_moment` takes `p_moment_id` (checked in `20261012090100_moments_split_writers.sql`). The soul and collection inserts are direct single-row writes under the owner RLS, the same path the iOS create sheets use.

- [ ] **Step 2: Scripts, CI, docs**

`packages/supabase/package.json`: add `"db:verify:home": "deno run --allow-env --allow-net --allow-read --allow-write scripts/verify-home.ts",` and append ` && npm run db:verify:home` to `"db:verify"`.

`.github/workflows/supabase.yml`, after the Guides step:
```yaml
      # The Home tab's back-end (#1254): the first-moment and first-pebble
      # achievements and their permanence, claim_blue_pebble's gate and
      # exactly-once, people_prompts' filters and guard; (part 3) the wrap
      # reveals and home_wraps' cover equality with path_month_wrap.
      - name: Home (#1254)
        if: '!cancelled()'
        run: .github/scripts/verify-harness.sh "home" db:verify:home
```

`packages/supabase/CLAUDE.md`: add the table row
```
| `npm run db:verify:home` | `verify-home.ts` | the Home tab's back-end (#1254): first-moment / first-pebble unlock and survive deletion, `claim_blue_pebble` gate and exactly-once, `people_prompts` filters and valence guard, `wrap_reveals` and `home_wraps`' cover equality with `path_month_wrap`. `HOME_DUMP=1` writes the payload fixtures to `scripts/oracles/home/payloads/` | anon |
```
and `home-verify-` to the orphan prefixes.

- [ ] **Step 3: Run, dump the payload, lint, commit**

```bash
set -a; . ./.env; set +a; HOME_DUMP=1 npm run db:verify:home --workspace=packages/supabase
npm run lint --workspace=packages/supabase
git add packages/supabase/scripts/verify-home.ts packages/supabase/scripts/oracles/home/payloads/people-prompts.json packages/supabase/package.json .github/workflows/supabase.yml packages/supabase/CLAUDE.md
git commit -m "test(db): prove the first five, the blue pebble claim and people_prompts"
```
Expected: `Summary: passed=19 failed=0`.

### Task 7: Admin family mirror

**Files:**
- Modify: `apps/admin/lib/achievements/types.ts`

- [ ] **Step 1: Extend the families**

In `ACHIEVEMENT_FAMILIES` add `"first_moment", "first_pebble", "first_five"` after `"first_soul"`. In `FAMILY_LABELS` add:
```ts
  first_moment: "First moment",
  first_pebble: "First pebble",
  first_five: "The blue pebble (claimed)",
```
Update the doc comment: "The eleven families … `first_five` is never evaluated by the check; `claim_blue_pebble()` grants it."

- [ ] **Step 2: Lint, test, commit**

```bash
npm run lint --workspace=apps/admin && npm run test --workspace=apps/admin
git add apps/admin/lib/achievements/types.ts
git commit -m "feat(admin): list the first-moment, first-pebble and first-five families"
```

### Task 8: Arkaik and the PR for part 2

- [ ] Arkaik: `create_node` `API-rpc-claim-blue-pebble` and `API-rpc-people-prompts` (status `development`), edges from the People view (`V-people` or the existing souls list node, find it with `list_nodes`) to `API-rpc-people-prompts`.
- [ ] `gh stack push`; PR title `feat(db): back the first five with achievements and add people_prompts`, body `Part 2 of #1254`, labels `feat`, `db`, `supabase`, `admin`, `no-lab-note`, milestone M64. Mention #1257 and #1258 by number as the copy follow-ups.

---

## Part 3 · `wrap_reveals`, `reveal_wrap`, `home_wraps` (branch `feat/1254-wrap-reveals`)

### Task 9: Spec patch for the cover source

**Files:**
- Modify: `docs/superpowers/specs/2026-10-26-home-tab-and-empty-states-design.md` §4.3

- [ ] **Step 1: Replace the sentence** "The cover is produced by the same SQL as `path_month_wrap`, factored into a shared internal function so the two cannot drift; the harness asserts byte equality for the same month." with "The cover is `path_month_wrap(month, p_tz)->'cover'`, called per month, so the two cannot drift; the harness asserts equality for the same month. The list is capped at the twelve most recent finished months with a pebble."
- [ ] **Step 2: Commit** `docs(db): home_wraps reads its cover through path_month_wrap`.

### Task 10: Migration `20261027090300_wrap_reveals.sql`

**Files:**
- Create: `packages/supabase/supabase/migrations/20261027090300_wrap_reveals.sql`

- [ ] **Step 1: Write the migration**

```sql
-- Migration: wrap_reveals, reveal_wrap, home_wraps (#1254, spec 2026-10-26 §4.3, D5)
-- A month's wrap stays folded on the Home tab until the user unfolds it. The
-- state is per account (iOS and Android must agree), so it is a row here and
-- not a device preference. Writes go only through reveal_wrap.

-- 1. the table ------------------------------------------------------------------------
create table public.wrap_reveals (
  user_id     uuid not null references auth.users(id) on delete cascade,
  month       date not null check (month = date_trunc('month', month)::date),
  revealed_at timestamptz not null default now(),
  primary key (user_id, month)
);

alter table public.wrap_reveals enable row level security;

create policy wrap_reveals_select_self on public.wrap_reveals
  for select to authenticated using (user_id = auth.uid());
-- No INSERT/UPDATE/DELETE policies: written exclusively by reveal_wrap().

-- 2. reveal_wrap --------------------------------------------------------------------------
-- Upserts for the caller. A month that is not finished in p_tz is ignored (the
-- row is not written) and the call still returns the current state, so a
-- client cannot unfold the live month early. invalid_tz / invalid_month as in
-- path_month_wrap.
create or replace function public.reveal_wrap(p_month date, p_tz text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  today date;
  v_at timestamptz;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  begin
    today := (now() at time zone p_tz)::date;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;
  if p_month is null or extract(day from p_month) <> 1 then
    raise exception 'invalid_month' using errcode = '22023';
  end if;
  if p_month < date_trunc('month', today)::date then
    insert into public.wrap_reveals (user_id, month) values (v_uid, p_month)
    on conflict (user_id, month) do nothing;
  end if;
  select revealed_at into v_at from public.wrap_reveals where user_id = v_uid and month = p_month;
  return jsonb_build_object('month', p_month, 'revealed_at',
    case when v_at is null then null else to_jsonb(to_char(v_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')) end);
end;
$$;

revoke all on function public.reveal_wrap(date, text) from public, anon;
grant execute on function public.reveal_wrap(date, text) to authenticated;

-- 3. home_wraps ---------------------------------------------------------------------------
-- The finished months (in p_tz) holding at least one pebble, newest first,
-- twelve at most, each with its pebble count, its revealed_at (null = folded)
-- and the SAME cover object path_month_wrap returns, obtained by calling it.
-- security invoker like path_month_wrap, which reads as auth.uid().
create or replace function public.home_wraps(p_tz text)
returns jsonb
language plpgsql
stable
security invoker
set search_path = public
as $$
declare
  v_uid uuid := (select auth.uid());
  today date;
  result jsonb;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  begin
    today := (now() at time zone p_tz)::date;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;

  with months as (
    select date_trunc('month', (m.happened_at at time zone p_tz)::date)::date as month, count(*)::int as n
    from public.moments m
    join public.pebbles p on p.moment_id = m.id and p.user_id = v_uid
    where m.user_id = v_uid
    group by 1
    having date_trunc('month', (m.happened_at at time zone p_tz)::date)::date < date_trunc('month', today)::date
    order by 1 desc
    limit 12
  )
  select jsonb_build_object('months', coalesce(jsonb_agg(jsonb_build_object(
           'month', mo.month,
           'count', mo.n,
           'revealed_at', case when r.revealed_at is null then null
                               else to_jsonb(to_char(r.revealed_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')) end,
           'cover', public.path_month_wrap(mo.month, p_tz) -> 'cover')
         order by mo.month desc), '[]'::jsonb))
  into result
  from months mo
  left join public.wrap_reveals r on r.user_id = v_uid and r.month = mo.month;
  return result;
end;
$$;

revoke all on function public.home_wraps(text) from public, anon;
grant execute on function public.home_wraps(text) to authenticated;

comment on function public.home_wraps(text) is
  'The Home tab''s month cards (#1254): finished months with a pebble, newest first (12 at most), each with count, revealed_at (null = folded) and path_month_wrap''s cover.';

-- 4. purge_account: wrap_reveals ------------------------------------------------------------
-- STANDING RULE: do not re-emit the function here. The table cascades from
-- auth.users, so the purge is belt-and-braces like its section-(4) siblings;
-- the explicit delete keeps "all personal rows gone" true at RPC success.
-- This append is applied to the CURRENT body (20261012090300) in the next
-- migration file of this batch only if another re-emission is pending; with
-- none pending, the append is this statement block, inserted at the marker in
-- a verbatim copy of the body. See the task text for the diff discipline.
```

- [ ] **Step 2: Append `wrap_reveals` to `purge_account`**

`create or replace` cannot append; the body must be re-emitted. In the same migration file, after section 4's comment, paste the whole `create or replace function public.purge_account(p_user_id uuid) … $$;` statement from `20261012090300_moments_split_moderation_purge.sql` verbatim, and insert these three lines right after the `achievement_unlocks` block inside section (4):

```sql
  delete from public.wrap_reveals where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('wrap_reveals', v_n);
```

Then run the diff discipline:
```bash
diff <(sed -n '/create or replace function public.purge_account/,/^\$\$;/p' packages/supabase/supabase/migrations/20261012090300_moments_split_moderation_purge.sql) \
     <(sed -n '/create or replace function public.purge_account/,/^\$\$;/p' packages/supabase/supabase/migrations/20261027090300_wrap_reveals.sql)
```
Expected: exactly the three added lines. Also confirm no other migration later than 20261012090300 re-emits it: `grep -l "function public.purge_account" packages/supabase/supabase/migrations/*.sql | sort | tail -1` prints `20261027090300_wrap_reveals.sql`.

- [ ] **Step 3: Hand the push to the maintainer, regenerate types, commit**

```bash
npm run db:push --workspace=packages/supabase
npm run db:types:remote --workspace=packages/supabase
git add packages/supabase/supabase/migrations/20261027090300_wrap_reveals.sql packages/supabase/types/database.ts
git commit -m "feat(db): add wrap_reveals with reveal_wrap and home_wraps"
```

### Task 11: Harness section 4 and the purge harness

**Files:**
- Modify: `packages/supabase/scripts/verify-home.ts` (section 4)
- Modify: `packages/supabase/scripts/verify-account-purge.ts` (seed + zero-row)

- [ ] **Step 1: Add section 4 to `verify-home.ts`**, replacing the `// (added in Task 11)` line. Bob already holds six October 2026 pebbles from section 3 (October is finished once today is November 2026 or later; the harness runs after 2026-11-01, and the seed dates are fixed, so the month is always in the past from then on).

```ts
  type Wraps = { months: { month: string; count: number; revealed_at: string | null; cover: unknown }[] };
  const none = await a.rpc("home_wraps", { p_tz: "Europe/Paris" });
  check("home_wraps on an account with no pebble lists no month",
    !none.error && (none.data as Wraps).months.length === 0, none.error?.message ?? JSON.stringify(none.data));

  const wraps = await b.rpc("home_wraps", { p_tz: "Europe/Paris" });
  const w = wraps.data as Wraps;
  await dump("home-wraps", wraps.data);
  const oct = w.months.find((m) => m.month === "2026-10-01");
  check("home_wraps lists October 2026 with its six pebbles, folded",
    !wraps.error && !!oct && oct.count === 6 && oct.revealed_at === null, wraps.error?.message ?? JSON.stringify(w.months));
  const monthWrap = await b.rpc("path_month_wrap", { p_month: "2026-10-01", p_tz: "Europe/Paris" });
  check("home_wraps' cover equals path_month_wrap's cover for the same month",
    JSON.stringify(oct?.cover) === JSON.stringify((monthWrap.data as { cover: unknown }).cover),
    JSON.stringify(oct?.cover).slice(0, 120));
  check("home_wraps is newest first", w.months.every((m, i) => i === 0 || m.month < w.months[i - 1].month), JSON.stringify(w.months.map((m) => m.month)));

  const live = new Date();
  const liveMonth = `${live.getUTCFullYear()}-${String(live.getUTCMonth() + 1).padStart(2, "0")}-01`;
  const early2 = await b.rpc("reveal_wrap", { p_month: liveMonth, p_tz: "Europe/Paris" });
  check("reveal_wrap ignores the live month (no row, revealed_at null)",
    !early2.error && (early2.data as { revealed_at: string | null }).revealed_at === null, early2.error?.message ?? JSON.stringify(early2.data));

  const reveal = await b.rpc("reveal_wrap", { p_month: "2026-10-01", p_tz: "Europe/Paris" });
  const at1 = (reveal.data as { revealed_at: string | null }).revealed_at;
  check("reveal_wrap unfolds a finished month with a whole-second UTC timestamp",
    !reveal.error && !!at1 && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(at1), reveal.error?.message ?? JSON.stringify(reveal.data));
  const revealAgain = await b.rpc("reveal_wrap", { p_month: "2026-10-01", p_tz: "Europe/Paris" });
  check("a second reveal keeps the first timestamp", (revealAgain.data as { revealed_at: string | null }).revealed_at === at1, JSON.stringify(revealAgain.data));
  const after = await b.rpc("home_wraps", { p_tz: "Europe/Paris" });
  check("home_wraps now reports October as revealed",
    (after.data as Wraps).months.find((m) => m.month === "2026-10-01")?.revealed_at === at1, JSON.stringify((after.data as Wraps).months));

  const badMonth = await b.rpc("reveal_wrap", { p_month: "2026-10-15", p_tz: "Europe/Paris" });
  check("reveal_wrap refuses a non-first-of-month date", !!badMonth.error && badMonth.error.message.includes("invalid_month"), badMonth.error?.message ?? "accepted");
  const badTz = await b.rpc("home_wraps", { p_tz: "Mars/Olympus" });
  check("home_wraps refuses an unknown zone", !!badTz.error && badTz.error.message.includes("invalid_tz"), badTz.error?.message ?? "accepted");
  const { error: directErr } = await b.from("wrap_reveals").insert({ user_id: bob.id, month: "2026-09-01" });
  const { data: direct } = await b.from("wrap_reveals").select("month").eq("month", "2026-09-01");
  check("a client cannot write wrap_reveals directly", directErr !== null && (direct ?? []).length === 0, directErr?.message ?? "accepted");
  const peek = await a.from("wrap_reveals").select("*");
  check("a user sees only their own reveals", !peek.error && (peek.data ?? []).length === 0, JSON.stringify(peek.data));
  const anonWraps = await anon.rpc("home_wraps", { p_tz: "Europe/Paris" });
  check("home_wraps is refused to anon", !!anonWraps.error, JSON.stringify(anonWraps.data));
```

Update the header comment's expected summary to `passed=32 failed=0` and the CI step comment already mentions part 3.

- [ ] **Step 2: Extend `verify-account-purge.ts`**

After the seller's `complete_pebble` seed (around the `pebbleId` line), add a reveal through the real RPC:
```ts
  // #1254: a revealed wrap is a per-user row the purge must remove.
  const { error: revealErr } = await seller.rpc("reveal_wrap", { p_month: "2026-01-01", p_tz: "UTC" });
  if (revealErr) throw new Error(`reveal_wrap (seller): ${revealErr.message}`);
```
In the `sellerScoped` array add `["wrap_reveals", "user_id"],` after `["achievement_unlocks", "user_id"],`. Where the harness asserts the purge's `counts` keys (search for `achievement_unlocks` in that part of the file), add `wrap_reveals` the same way.

- [ ] **Step 3: Run both harnesses**

```bash
set -a; . ./.env; set +a
HOME_DUMP=1 npm run db:verify:home --workspace=packages/supabase     # Summary: passed=32 failed=0
npm run db:verify:purge --workspace=packages/supabase                # needs SUPABASE_SERVICE_ROLE_KEY; all green, wrap_reveals rows 0
npm run lint --workspace=packages/supabase
```

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/scripts/verify-home.ts packages/supabase/scripts/verify-account-purge.ts packages/supabase/scripts/oracles/home/payloads/home-wraps.json
git commit -m "test(db): prove wrap reveals, home_wraps' cover and the purge of wrap_reveals"
```

### Task 12: Arkaik and the PR for part 3

- [ ] Arkaik: `create_node` `API-rpc-home-wraps` and `API-rpc-reveal-wrap` (`development`), `DM-wrap-reveals`; edges from the Home view node (`create_node` `V-home` here if part 2 did not, status `development`, five acceptances named from the spec: greeting adapts to the hour and the weekday; a shortcut records in one tap; a folded month unfolds once; the first five end in the blue pebble claim; About topics open as pages).
- [ ] `gh stack push`; PR `feat(db): fold the monthly wraps until the user unfolds them`, body `Part 3 of #1254` noting the purge harness was run by hand and green, labels `feat`, `db`, `supabase`, `no-lab-note`, milestone M64.

---

## Part 4 · Admin `/guides` (branch `feat/1254-admin-guides`)

### Task 13: Types and options

**Files:**
- Create: `apps/admin/lib/guides/types.ts`, `apps/admin/lib/guides/options.ts`, `apps/admin/lib/guides/options.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
// apps/admin/lib/guides/options.test.ts
import { describe, expect, it } from "vitest"
import { ILLUSTRATION_OPTIONS, isGuideIllustration } from "./options"

describe("guide option guards", () => {
  it("accepts every listed illustration", () => {
    for (const { value } of ILLUSTRATION_OPTIONS) expect(isGuideIllustration(value)).toBe(true)
  })
  it("rejects anything else, including the empty string and undefined", () => {
    expect(isGuideIllustration("photo")).toBe(false)
    expect(isGuideIllustration("")).toBe(false)
    expect(isGuideIllustration(undefined)).toBe(false)
  })
  it("lists the nine illustrations in the table's order", () => {
    expect(ILLUSTRATION_OPTIONS.map((o) => o.value)).toEqual([
      "pebbles", "stones", "emotions", "valence", "domains", "collections", "souls", "glyphs", "karma",
    ])
  })
})
```

- [ ] **Step 2: Run it** — `npm run test --workspace=apps/admin -- lib/guides` — Expected: FAIL, module not found.

- [ ] **Step 3: Write types and options**

```ts
// apps/admin/lib/guides/types.ts
import type { Database } from "@pbbls/supabase"

export type GuideRow = Database["public"]["Tables"]["guides"]["Row"]
export type GuideInsert = Database["public"]["Tables"]["guides"]["Insert"]
export type GuideUpdate = Database["public"]["Tables"]["guides"]["Update"]

// The column is a text CHECK, which the generator types as `string | null`;
// the union lives here and ILLUSTRATION_OPTIONS mirrors the CHECK list.
export type GuideIllustration =
  | "pebbles" | "stones" | "emotions" | "valence" | "domains"
  | "collections" | "souls" | "glyphs" | "karma"
```

```ts
// apps/admin/lib/guides/options.ts
import type { GuideIllustration } from "./types"

export const ILLUSTRATION_OPTIONS: ReadonlyArray<{ value: GuideIllustration; label: string }> = [
  { value: "pebbles", label: "Pebbles (a moment and its stone)" },
  { value: "stones", label: "Stones (3 × 3 seals by size and valence)" },
  { value: "emotions", label: "Emotions (the seven families)" },
  { value: "valence", label: "Valence (lava, river, gem)" },
  { value: "domains", label: "Domains" },
  { value: "collections", label: "Collections (stack, pack, track)" },
  { value: "souls", label: "Souls (glyph faces)" },
  { value: "glyphs", label: "Glyphs (a carved ring)" },
  { value: "karma", label: "Karma" },
]

const ILLUSTRATION_VALUES = ILLUSTRATION_OPTIONS.map((o) => o.value)

export function isGuideIllustration(value: string | undefined): value is GuideIllustration {
  return value !== undefined && (ILLUSTRATION_VALUES as readonly string[]).includes(value)
}
```

- [ ] **Step 4: Run the test** — Expected: PASS (3 tests).
- [ ] **Step 5: Commit** — `git add apps/admin/lib/guides && git commit -m "feat(admin): add the guide types and illustration options"`.

### Task 14: Server actions

**Files:**
- Create: `apps/admin/app/(authed)/guides/actions.ts`

- [ ] **Step 1: Write the actions**

```ts
"use server"

import { redirect } from "next/navigation"
import { revalidatePath } from "next/cache"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { isGuideIllustration } from "@/lib/guides/options"
import type { GuideInsert } from "@/lib/guides/types"

export type GuideFormResult = { error: string } | undefined

const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/

function readGuideFields(formData: FormData): { values: GuideInsert; error?: string } {
  const slug = formData.get("slug")?.toString().trim() ?? ""
  const sortRaw = formData.get("sort_order")?.toString().trim() ?? ""
  const sort_order = sortRaw === "" ? 0 : Number(sortRaw)
  const illustrationRaw = formData.get("illustration")?.toString() || undefined
  const illustration = isGuideIllustration(illustrationRaw) ? illustrationRaw : null
  const title_en = formData.get("title_en")?.toString().trim() ?? ""
  const title_fr = formData.get("title_fr")?.toString().trim() || null
  const summary_en = formData.get("summary_en")?.toString().trim() ?? ""
  const summary_fr = formData.get("summary_fr")?.toString().trim() || null
  const body_md_en = formData.get("body_md_en")?.toString().trim() ?? ""
  const body_md_fr = formData.get("body_md_fr")?.toString().trim() || null
  const published = formData.get("published") === "on"

  const empty = {} as GuideInsert
  if (!SLUG.test(slug)) return { error: "Slug is lowercase words joined by hyphens.", values: empty }
  if (!Number.isInteger(sort_order) || sort_order < 0 || sort_order > 32767) return { error: "Sort order is a whole number from 0 to 32767.", values: empty }
  if (!title_en) return { error: "Title (EN) is required.", values: empty }
  if (!summary_en) return { error: "Summary (EN) is required.", values: empty }
  if (!body_md_en) return { error: "Body (EN) is required.", values: empty }

  return { values: { slug, sort_order, illustration, title_en, title_fr, summary_en, summary_fr, body_md_en, body_md_fr, published } }
}

export async function createGuide(_state: GuideFormResult, formData: FormData): Promise<GuideFormResult> {
  const parsed = readGuideFields(formData)
  if (parsed.error) return { error: parsed.error }
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("guides").insert(parsed.values).select("id").single()
  if (error) {
    console.error("[guides/createGuide] insert failed:", error.message)
    return { error: error.code === "23505" ? "A guide with that slug already exists." : "Could not save the guide." }
  }
  revalidatePath("/guides")
  redirect(`/guides/${data.id}`)
}

export async function updateGuide(id: string, _state: GuideFormResult, formData: FormData): Promise<GuideFormResult> {
  const parsed = readGuideFields(formData)
  if (parsed.error) return { error: parsed.error }
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.from("guides").update(parsed.values).eq("id", id)
  if (error) {
    console.error("[guides/updateGuide] update failed:", error.message)
    return { error: error.code === "23505" ? "A guide with that slug already exists." : "Could not save the guide." }
  }
  revalidatePath("/guides")
  revalidatePath(`/guides/${id}`)
  return undefined
}

export async function deleteGuide(id: string): Promise<void> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.from("guides").delete().eq("id", id)
  if (error) {
    console.error("[guides/deleteGuide] delete failed:", error.message)
    throw new Error("Could not delete the guide.")
  }
  revalidatePath("/guides")
  redirect("/guides")
}
```

- [ ] **Step 2: Typecheck** — `npm run lint --workspace=apps/admin` (ESLint runs tsc-aware rules; a wrong column name fails here against `database.ts`).
- [ ] **Step 3: Commit** — `feat(admin): add the guide server actions`.

### Task 15: `GuideForm` and `DeleteGuideButton`

**Files:**
- Create: `apps/admin/app/(authed)/guides/_components/GuideForm.tsx`, `apps/admin/app/(authed)/guides/_components/DeleteGuideButton.tsx`

- [ ] **Step 1: Write the form** (reusing `MarkdownField` from the logs folder by relative import; do not move it)

```tsx
"use client"

import { useActionState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Switch } from "@/components/ui/switch"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ILLUSTRATION_OPTIONS } from "@/lib/guides/options"
import type { GuideRow } from "@/lib/guides/types"
import { MarkdownField } from "../../logs/_components/MarkdownField"
import type { GuideFormResult } from "../actions"

export type GuideFormAction = (state: GuideFormResult, formData: FormData) => Promise<GuideFormResult>

export function GuideForm({ guide, action, submitLabel, extraActions }: {
  guide: GuideRow | null
  action: GuideFormAction
  submitLabel: string
  extraActions?: React.ReactNode
}) {
  const [state, formAction, pending] = useActionState<GuideFormResult, FormData>(action, undefined)

  return (
    <form action={formAction} className="space-y-8">
      <div className="flex flex-wrap gap-4">
        <div className="space-y-2">
          <Label htmlFor="slug">Slug</Label>
          <Input id="slug" name="slug" defaultValue={guide?.slug ?? ""} required pattern="^[a-z0-9]+(-[a-z0-9]+)*$" />
        </div>
        <div className="space-y-2">
          <Label htmlFor="sort_order">Sort order</Label>
          <Input id="sort_order" name="sort_order" type="number" min={0} max={32767} defaultValue={guide?.sort_order ?? 0} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="illustration">Illustration</Label>
          <Select name="illustration" defaultValue={guide?.illustration ?? undefined}>
            <SelectTrigger id="illustration" className="w-72"><SelectValue placeholder="None" /></SelectTrigger>
            <SelectContent>
              {ILLUSTRATION_OPTIONS.map((o) => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2 text-sm">
          <Label htmlFor="published" className="text-foreground font-medium">Published</Label>
          <Switch id="published" name="published" defaultChecked={guide?.published ?? false} />
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <div className="space-y-4">
          <h2 className="text-lg font-semibold">English</h2>
          <div className="space-y-2">
            <Label htmlFor="title_en">Title</Label>
            <Input id="title_en" name="title_en" defaultValue={guide?.title_en ?? ""} required />
          </div>
          <div className="space-y-2">
            <Label htmlFor="summary_en">Summary</Label>
            <Textarea id="summary_en" name="summary_en" defaultValue={guide?.summary_en ?? ""} required rows={2} />
          </div>
          <div className="space-y-2">
            <Label>Body</Label>
            <MarkdownField name="body_md_en" defaultValue={guide?.body_md_en} ariaLabel="Body (EN)" />
          </div>
        </div>
        <div className="space-y-4">
          <h2 className="text-lg font-semibold">French</h2>
          <div className="space-y-2">
            <Label htmlFor="title_fr">Titre</Label>
            <Input id="title_fr" name="title_fr" defaultValue={guide?.title_fr ?? ""} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="summary_fr">Résumé</Label>
            <Textarea id="summary_fr" name="summary_fr" defaultValue={guide?.summary_fr ?? ""} rows={2} />
          </div>
          <div className="space-y-2">
            <Label>Corps</Label>
            <MarkdownField name="body_md_fr" defaultValue={guide?.body_md_fr} ariaLabel="Body (FR)" />
          </div>
        </div>
      </div>

      {state?.error ? <p role="alert" className="text-destructive text-sm">{state.error}</p> : null}

      <div className="flex items-center gap-3">
        <Button type="submit" disabled={pending}>{pending ? "Saving…" : submitLabel}</Button>
        {extraActions}
      </div>
    </form>
  )
}
```

The error paragraph (`role="alert"`, `text-destructive text-sm`) and the `extraActions` slot copy `LogForm.tsx` lines 128–138 exactly, so the two forms look the same.

- [ ] **Step 2: Write the delete button** by copying `DeleteLogButton.tsx` whole, renaming to `DeleteGuideButton`, importing `deleteGuide` from `../actions`, and changing the copy to "Delete this guide?" / "“{title}” will be removed from the Home tab's About section."

- [ ] **Step 3: Lint and commit** — `feat(admin): add the guide form and delete button`.

### Task 16: Pages

**Files:**
- Create: `apps/admin/app/(authed)/guides/page.tsx`, `apps/admin/app/(authed)/guides/new/page.tsx`, `apps/admin/app/(authed)/guides/[id]/page.tsx`, `apps/admin/app/(authed)/guides/_components/GuideTable.tsx`

- [ ] **Step 1: The table**

```tsx
import Link from "next/link"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import type { GuideRow } from "@/lib/guides/types"

export function GuideTable({ guides }: { guides: GuideRow[] }) {
  if (guides.length === 0) return <p className="text-muted-foreground text-sm">No guides yet.</p>
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-16">Order</TableHead>
          <TableHead>Title</TableHead>
          <TableHead>Slug</TableHead>
          <TableHead>Illustration</TableHead>
          <TableHead>French</TableHead>
          <TableHead>Status</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {guides.map((g) => (
          <TableRow key={g.id}>
            <TableCell className="tabular-nums">{g.sort_order}</TableCell>
            <TableCell><Link href={`/guides/${g.id}`} className="font-medium hover:underline">{g.title_en}</Link></TableCell>
            <TableCell className="font-mono text-xs">{g.slug}</TableCell>
            <TableCell>{g.illustration ?? "—"}</TableCell>
            <TableCell>{g.title_fr && g.summary_fr ? (g.body_md_fr ? "full" : "no body") : "missing"}</TableCell>
            <TableCell><Badge variant={g.published ? "default" : "outline"}>{g.published ? "Published" : "Draft"}</Badge></TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
```

- [ ] **Step 2: The list page**

```tsx
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { GuideRow } from "@/lib/guides/types"
import { GuideTable } from "./_components/GuideTable"

export default async function GuidesPage() {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("guides").select("*").order("sort_order").order("slug")
  if (error) console.error("[guides] select failed:", error.message)
  const guides: GuideRow[] = data ?? []
  return (
    <section className="space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Guides</h1>
          <p className="text-muted-foreground text-sm">The About Pebbles readings on the Home tab, in sort order.</p>
        </div>
        <Button render={<Link href="/guides/new" />}>New guide</Button>
      </header>
      <GuideTable guides={guides} />
    </section>
  )
}
```

- [ ] **Step 3: The new page** — copy `logs/new/page.tsx`'s breadcrumb shape with `Guides` → `New guide`, heading "New guide", subtitle "A short reading for the Home tab's About section.", and render `<GuideForm guide={null} action={createGuide} submitLabel="Save" />` (import `createGuide` from `../actions`; the form is a client component, the page a server component, which is the same split as the logs pages).

- [ ] **Step 4: The edit page** — copy `logs/[id]/page.tsx`: select the guide by id (`notFound()` on error or null), breadcrumb `Guides` → the EN title, heading = the EN title, `<GuideForm guide={guide} action={updateGuide.bind(null, guide.id)} submitLabel="Save changes" extraActions={<DeleteGuideButton id={guide.id} title={guide.title_en} />} />`.

- [ ] **Step 5: Lint, run the app, click through**

```bash
npm run lint --workspace=apps/admin
npm run dev --workspace=apps/admin   # http://localhost:3001/guides
```
Create a draft guide, edit it, publish it, delete it. The `verify-guides.ts` harness keeps passing because drafts are invisible to anon and the delete removes the row.

- [ ] **Step 6: Commit** — `feat(admin): add the guides list, new and edit pages`.

### Task 17: Sidebar and playground

**Files:**
- Modify: `apps/admin/components/layout/Sidebar.tsx` (LOG_ITEMS)
- Create: `apps/admin/app/(authed)/playground/guides/page.tsx`, `apps/admin/app/(authed)/guides/__fixtures__/guides.ts`

- [ ] **Step 1: Sidebar** — add `{ href: "/guides", label: "Guides", icon: BookOpen },` to `LOG_ITEMS` and import `BookOpen` from `lucide-react`.

- [ ] **Step 2: Fixtures and the playground page**

```ts
// apps/admin/app/(authed)/guides/__fixtures__/guides.ts
import type { GuideRow } from "@/lib/guides/types"

const base = { created_at: "2026-10-27T09:00:00Z", updated_at: "2026-10-27T09:00:00Z" }

export const denseGuidesFixture: GuideRow[] = [
  { id: "g1", slug: "pebbles", sort_order: 10, illustration: "pebbles", title_en: "About Pebbles", title_fr: "À propos de Pebbles", summary_en: "The private memory of your life.", summary_fr: "La mémoire privée de ta vie.", body_md_en: "# Pebbles", body_md_fr: "# Pebbles", published: true, ...base },
  { id: "g2", slug: "stones", sort_order: 20, illustration: "stones", title_en: "About the stones", title_fr: "À propos des pierres", summary_en: "How a stone is built.", summary_fr: "Comment une pierre naît.", body_md_en: "# Stones", body_md_fr: null, published: true, ...base },
  { id: "g3", slug: "karma", sort_order: 90, illustration: null, title_en: "About karma", title_fr: null, summary_en: "A quiet count.", summary_fr: null, body_md_en: "# Karma", body_md_fr: null, published: false, ...base },
]

export const emptyGuidesFixture: GuideRow[] = []
```

```tsx
// apps/admin/app/(authed)/playground/guides/page.tsx
import { GuideTable } from "../../guides/_components/GuideTable"
import { denseGuidesFixture, emptyGuidesFixture } from "../../guides/__fixtures__/guides"

export default function GuidesPlaygroundPage() {
  return (
    <section className="space-y-10">
      <h1 className="text-2xl font-semibold">Playground · Guides</h1>
      <div className="space-y-3"><h2 className="font-medium">Dense (published, FR without body, draft without FR)</h2><GuideTable guides={denseGuidesFixture} /></div>
      <div className="space-y-3"><h2 className="font-medium">Empty</h2><GuideTable guides={emptyGuidesFixture} /></div>
    </section>
  )
}
```

- [ ] **Step 3: Lint, test, build, commit**

```bash
npm run lint --workspace=apps/admin && npm run test --workspace=apps/admin && npm run build --workspace=apps/admin
git add apps/admin/components/layout/Sidebar.tsx "apps/admin/app/(authed)/playground/guides" "apps/admin/app/(authed)/guides/__fixtures__"
git commit -m "feat(admin): link guides in the sidebar and add their playground"
```

### Task 18: Arkaik and the PR for part 4

- [ ] Arkaik: `create_node` an admin view `V-admin-guides` (`development`, one acceptance: an admin creates, edits, publishes and deletes a guide), edge to `DM-guides`.
- [ ] `gh stack push`; PR `feat(admin): edit the home tab's guides`, body `Part 4 of #1254` and `Resolves #1254` (the last part), labels `feat`, `admin`, `ui`, `no-lab-note`, milestone M64.

---

## Self-review against the spec

- §4.1 guides table, RLS, seed, single-table client read → Tasks 1–2. The client select needs no RPC (documented in Task 1's comment).
- §4.2 three achievements, the widened check, the admin branch, `claim_blue_pebble`, copy on every surface → Tasks 4, 6, 7; iOS copy in stack 2; Android/web in #1257/#1258.
- §4.3 `wrap_reveals`, `reveal_wrap`, `home_wraps`, purge append, purge harness → Tasks 9–11 (cover via `path_month_wrap`, spec patched in Task 9).
- §4.4 `people_prompts` with the outward set, the valence guard, hidden moments, `skipped`, totals → Tasks 5–6.
- §5 admin `/guides`: list, new, edit, `GuideForm`, actions, sidebar, playground → Tasks 13–17.
- §8 harnesses: `verify-guides.ts`, `verify-home.ts` (+ `HOME_DUMP` payloads), `verify-account-purge.ts` manual run → Tasks 2, 6, 11.
- Stacks: four parts, each one PR, each lints and tests alone → the four branches.

Resolved while writing: `delete_moment(p_moment_id)`; the glyph insert shape (`{ d, width }` strokes); `LogForm`'s error classes. One check left to the implementer: `verify-account-purge.ts` asserts the purge's returned `counts` only through the zero-row loop (there is no per-key assertion), so Task 11 Step 2's last sentence is a no-op unless such an assertion exists at the time.
