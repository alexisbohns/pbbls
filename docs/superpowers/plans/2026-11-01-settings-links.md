# Remote settings links and app licences — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Settings links of web, iOS and Android come from a `settings_links` table edited in the admin; iOS shows its bundled licences in the system Settings app; Android bundles the font licences; `/docs/credits` lists the apps' libraries.

**Architecture:** One typed table modelled on `guides` (public read of published rows, admin-only writes). Each client decodes the rows with a pure, tested decoder, renders a cached-or-built-in list at once and refreshes behind it. iOS licences are a generated `Settings.bundle`.

**Spec:** `docs/superpowers/specs/2026-11-01-settings-links-design.md` (D1–D6). **Milestone:** M68 · V1 launch.

**Tech stack:** Postgres/Supabase migrations + Deno harness; Next.js 16 admin (server actions); Next.js 16 web (DataProvider, next-intl, Vitest); SwiftUI + supabase-swift + Swift Testing; Kotlin/Compose + supabase-kt + Hilt + JUnit.

---

## Ground rules for every task

- Work in the stack worktree `.worktrees/settings-links` (Task 0). Copy the gitignored config the memory notes list: `apps/web/.env.local`, `apps/android/secrets.properties`, `packages/supabase/supabase/.temp`, and the root `.env`.
- Read the workspace `CLAUDE.md` before touching a surface (`apps/web`, `apps/admin`, `apps/ios`, `apps/android`, `packages/supabase`).
- Commit messages: `type(scope): description`, lowercase, no period, ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- The **canonical payload** (Task 1.4) is the only test input for decoders. Never hand-write a payload in a client test.
- `db push`, `gh stack` pushes and merges may be blocked by the permission classifier: when they are, stop and hand the maintainer the exact command.

## File map

| Part | Create | Modify |
|---|---|---|
| 1 db | `packages/supabase/supabase/migrations/20261101120000_settings_links.sql`, `packages/supabase/scripts/verify-settings-links.ts`, `packages/supabase/fixtures/settings-links.json` | `packages/supabase/types/database.ts`, `packages/supabase/package.json`, `.github/workflows/supabase.yml` |
| 2 admin | `apps/admin/app/(authed)/settings-links/{page.tsx,actions.ts,new/page.tsx,[id]/page.tsx,_components/*}`, `apps/admin/lib/settings-links/{types.ts,options.ts,options.test.ts}` | admin nav |
| 3 web | `apps/web/lib/settings/settings-links.ts`, `apps/web/lib/settings/settings-links.test.ts`, `apps/web/lib/data/useSettingsLinks.ts`, `apps/web/lib/settings/__fixtures__/settings-links.json` | `lib/data/data-provider.ts`, `lib/data/supabase-provider.ts`, `components/settings/LegalSection.tsx`, i18n `en.json`/`fr.json`, `docs/credits/{en,fr}.md` |
| 4 ios-links | `Pebbles/Features/Profile/Settings/SettingsLink.swift`, `Pebbles/Features/Profile/Settings/SettingsLinksService.swift`, `PebblesTests/SettingsLinkTests.swift`, `PebblesTests/Fixtures/settings-links.json` | `SettingsSheet.swift`, `Localizable.xcstrings` |
| 5 ios-licences | `apps/ios/Scripts/generate-licenses.mjs`, `Pebbles/Resources/Settings.bundle/{Root.plist,Licenses.plist,en.lproj/Root.strings,fr.lproj/Root.strings}`, `Pebbles/Resources/Licenses/*.txt` | `apps/ios/package.json`, `.github/workflows/ios.yml` |
| 6 android | `core/model/SettingsLink.kt`, `core/data/SettingsLinksService.kt`, test `core/data/SettingsLinksDecodingTest.kt`, `app/src/test/resources/settings-links.json`, `app/src/main/assets/licenses/*.txt` | `SettingsViewModel.kt`, `SettingsContent.kt`, `LegalDocs.kt`, `strings.xml` (+ `values-fr`) |

---

## Task 0: Worktree and stack

- [ ] **Step 1:** From the main checkout, update main and create the worktree on the first part's branch.

```bash
cd /Users/alexis/code/pbbls
git fetch origin main
git worktree add .worktrees/settings-links -b feat/1489-settings-links-db origin/main
cp apps/web/.env.local .worktrees/settings-links/apps/web/.env.local
cp apps/android/secrets.properties .worktrees/settings-links/apps/android/secrets.properties
mkdir -p .worktrees/settings-links/packages/supabase/supabase && cp -R packages/supabase/supabase/.temp .worktrees/settings-links/packages/supabase/supabase/.temp
cp .env .worktrees/settings-links/.env
cd .worktrees/settings-links && npm ci
```

- [ ] **Step 2:** File the three client issues (milestone `M68 · V1 launch`), and note their numbers for the branch names:

```bash
gh issue create --title "[Feat] Web: Settings links from the admin" --label feat --label web --label legal --milestone "M68 · V1 launch" --body "Web half of #1489: the Settings links come from settings_links. Spec: docs/superpowers/specs/2026-11-01-settings-links-design.md"
gh issue create --title "[Feat] iOS: Settings links from the admin and a Licenses pane" --label feat --label ios --label legal --milestone "M68 · V1 launch" --body "iOS half of #1489 and of #1395. Spec: docs/superpowers/specs/2026-11-01-settings-links-design.md"
gh issue create --title "[Feat] Android: Settings links from the admin and bundled font licences" --label feat --label android --label legal --milestone "M68 · V1 launch" --body "Android half of #1489 and of #1395. Spec: docs/superpowers/specs/2026-11-01-settings-links-design.md"
```

Branches (created as each part starts, stacked with `gh stack`): `feat/1489-settings-links-db`, `feat/1489-settings-links-admin`, `feat/<web>-settings-links-web`, `feat/<ios>-settings-links-ios`, `feat/<ios>-licences-ios`, `feat/<android>-settings-links-android`. Read the `gh-stack` skill before creating the stack.

---

## Part 1 — db (#1489)

### Task 1.1: Migration

**Files:** Create `packages/supabase/supabase/migrations/20261101120000_settings_links.sql`

- [ ] **Step 1: Write the migration**

```sql
-- Migration: settings_links
-- The links every client shows in its Settings (#1489, spec 2026-11-01).
-- Modelled on guides (20261027090000): EN required, FR falls back to EN on the
-- client, public read gated on `published`, writes admin-only. Not user data
-- (no purge_account append); no emotion/domain (no sync_achievement_catalog).

create table public.settings_links (
  id          uuid primary key default gen_random_uuid(),
  key         text not null unique check (key ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  section     text not null check (section in ('legal','about','support')),
  sort_order  smallint not null default 0,
  title_en    text not null check (btrim(title_en) <> ''),
  title_fr    text,
  url         text not null check (url ~ '^(https://|mailto:)'),
  published   boolean not null default false,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.settings_links is
  'Links shown in every client''s Settings (#1489). Edited in the admin; clients read published rows by section then sort_order, and fall back to EN when FR is null.';

create trigger settings_links_updated_at
  before update on public.settings_links
  for each row execute function public.set_updated_at();

alter table public.settings_links enable row level security;

create policy "settings_links_select" on public.settings_links
  for select using (published = true or public.is_admin(auth.uid()));
create policy "settings_links_insert" on public.settings_links
  for insert with check (public.is_admin(auth.uid()));
create policy "settings_links_update" on public.settings_links
  for update using (public.is_admin(auth.uid()));
create policy "settings_links_delete" on public.settings_links
  for delete using (public.is_admin(auth.uid()));

create index settings_links_published_idx on public.settings_links (published, section, sort_order);

insert into public.settings_links (id, key, section, sort_order, title_en, title_fr, url, published) values
  (md5('settings_links:terms')::uuid,        'terms',        'legal',   10, 'Terms of Service', 'Conditions générales d''utilisation', 'https://www.pbbls.app/docs/terms',        true),
  (md5('settings_links:privacy')::uuid,      'privacy',      'legal',   20, 'Privacy Policy',   'Politique de confidentialité',        'https://www.pbbls.app/docs/privacy',      true),
  (md5('settings_links:legal-notice')::uuid, 'legal-notice', 'legal',   30, 'Legal Notice',     'Mentions légales',                    'https://www.pbbls.app/docs/legal-notice', true),
  (md5('settings_links:credits')::uuid,      'credits',      'about',   10, 'Credits',          'Crédits',                             'https://www.pbbls.app/docs/credits',      true),
  (md5('settings_links:support')::uuid,      'support',      'support', 10, 'Contact support',  'Contacter le support',                'mailto:ciao@pbbls.app',                   true);
```

- [ ] **Step 2: Validate on a native local Postgres** (memory: Homebrew `postgresql@16`, no Docker). Replay the file in a scratch database that defines stubs for `set_updated_at`, `is_admin` and `auth.uid()`, then assert the checks:

```bash
createdb sl_check && psql -v ON_ERROR_STOP=1 sl_check <<'SQL'
create schema auth; create function auth.uid() returns uuid language sql as 'select null::uuid';
create function public.is_admin(uuid) returns boolean language sql as 'select false';
create function public.set_updated_at() returns trigger language plpgsql as 'begin new.updated_at = now(); return new; end';
SQL
psql -v ON_ERROR_STOP=1 sl_check -f packages/supabase/supabase/migrations/20261101120000_settings_links.sql
psql sl_check -c "insert into settings_links (key,section,title_en,url) values ('x','legal','X','javascript:alert(1)')"   # Expected: ERROR ... violates check constraint "settings_links_url_check"
psql sl_check -c "insert into settings_links (key,section,title_en,url) values ('y','misc','Y','https://a.b')"           # Expected: ERROR ... settings_links_section_check
psql sl_check -tc "select count(*) from settings_links where published"                                                     # Expected: 5
dropdb sl_check
```

- [ ] **Step 3: Apply to the linked project and regenerate types**

```bash
npm run db:push --workspace=packages/supabase
npm run db:types:remote --workspace=packages/supabase
git diff --stat packages/supabase/types/database.ts   # Expected: settings_links Row/Insert/Update added, file not truncated
```

If `db:push` is blocked by the classifier, stop and give the maintainer that command.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/migrations/20261101120000_settings_links.sql packages/supabase/types/database.ts
git commit -m "feat(db): add settings_links for remote settings rows"
```

### Task 1.2: Contract harness

**Files:** Create `packages/supabase/scripts/verify-settings-links.ts`; modify `packages/supabase/package.json`, `.github/workflows/supabase.yml`

- [ ] **Step 1: Write the harness** (anon-only, so it can join the CI gate; copy the `check()` shape from `verify-guides.ts`)

```ts
#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for settings_links (#1489) — runs against the REMOTE project, anon only.
 *   1. anon reads the published rows, ordered by section then sort_order; the five seeded keys are there.
 *   2. anon cannot insert, update or delete (admin-only writes); each refusal is proven by re-reading.
 *   3. every published url starts with https:// or mailto:.
 * Run: set -a; . ./.env; set +a; npm run db:verify:settings-links --workspace=packages/supabase
 * Expected: `Summary: passed=6 failed=0`
 */
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");
if (!SUPABASE_URL || !ANON_KEY) { console.error("SUPABASE_URL and SUPABASE_ANON_KEY must be set"); Deno.exit(2); }

let passed = 0; let failed = 0;
function check(name: string, ok: boolean, detail?: string) {
  if (ok) { passed += 1; console.log(`✓ ${name}`); } else { failed += 1; console.log(`✗ ${name}${detail ? ` — ${detail}` : ""}`); }
}

type Row = { key: string; section: string; sort_order: number; title_en: string; title_fr: string | null; url: string };
const SEEDED = ["terms", "privacy", "legal-notice", "credits", "support"];
const anon = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
const COLS = "key,section,sort_order,title_en,title_fr,url";

const read = await anon.from("settings_links").select(COLS).order("section").order("sort_order");
const rows = (read.data ?? []) as Row[];
check("anon reads the published links", !read.error && rows.length >= SEEDED.length, read.error?.message ?? `${rows.length} rows`);
check("the five seeded keys are published", SEEDED.every((k) => rows.some((r) => r.key === k)),
  SEEDED.filter((k) => !rows.some((r) => r.key === k)).join(", "));
check("every url is https or mailto", rows.every((r) => /^(https:\/\/|mailto:)/.test(r.url)),
  rows.filter((r) => !/^(https:\/\/|mailto:)/.test(r.url)).map((r) => r.key).join(", "));

const ins = await anon.from("settings_links").insert({ key: `verify-${crypto.randomUUID().slice(0, 8)}`, section: "about", title_en: "x", url: "https://example.test", published: true });
const after = await anon.from("settings_links").select("key").like("key", "verify-%");
check("anon insert is refused", ins.error !== null && (after.data ?? []).length === 0, ins.error?.message);

const before = rows.find((r) => r.key === "terms");
await anon.from("settings_links").update({ title_en: "hijacked" }).eq("key", "terms");
const terms = await anon.from("settings_links").select(COLS).eq("key", "terms").single();
check("anon update is refused", terms.data?.title_en === before?.title_en, terms.data?.title_en);

await anon.from("settings_links").delete().eq("key", "terms");
const still = await anon.from("settings_links").select("key").eq("key", "terms");
check("anon delete is refused", (still.data ?? []).length === 1);

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed === 0 ? 0 : 1);
```

- [ ] **Step 2: Wire the script.** In `packages/supabase/package.json` add `"db:verify:settings-links": "deno run --allow-env --allow-net scripts/verify-settings-links.ts"` and append `&& npm run db:verify:settings-links` to `db:verify`. In `.github/workflows/supabase.yml`, next to the guides step, add:

```yaml
      - name: settings-links
        run: .github/scripts/verify-harness.sh "settings-links" db:verify:settings-links
```

(Match the indentation and any `if:`/`env:` keys of the neighbouring `guides` step exactly.)

- [ ] **Step 3: Run it**

Run: `set -a; . ./.env; set +a; npm run db:verify:settings-links --workspace=packages/supabase`
Expected: `Summary: passed=6 failed=0`

- [ ] **Step 4:** `actionlint .github/workflows/supabase.yml` (expected: no output), then commit:

```bash
git add packages/supabase/scripts/verify-settings-links.ts packages/supabase/package.json .github/workflows/supabase.yml
git commit -m "test(db): verify settings_links read and write rules"
```

### Task 1.3: Canonical payload

**Files:** Create `packages/supabase/fixtures/settings-links.json`

- [ ] **Step 1:** Capture the real PostgREST response, verbatim, as every client will request it:

```bash
set -a; . ./.env; set +a
mkdir -p packages/supabase/fixtures
curl -s "$SUPABASE_URL/rest/v1/settings_links?select=key,section,sort_order,title_en,title_fr,url&published=eq.true&order=section.asc,sort_order.asc" \
  -H "apikey: $SUPABASE_ANON_KEY" | python3 -m json.tool --indent 2 > packages/supabase/fixtures/settings-links.json
cat packages/supabase/fixtures/settings-links.json   # Expected: 5 objects; section order about, legal, legal, legal, support
```

- [ ] **Step 2:** Append one row with an explicit `"title_fr": null` and one with an unknown section `"section": "beta"` and one with `"url": "ftp://x"` to a **second** file, `packages/supabase/fixtures/settings-links-edge.json`, built by copying the `credits` object three times and changing only those fields (these are the variants the decoders must tolerate; the five real rows stay verbatim in the first file).

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/fixtures
git commit -m "test(db): capture the settings_links payload for client tests"
```

Then `gh stack` push Part 1 and open its PR (`feat(db): settings links table`, `Resolves #1489` only on the admin PR — this one says `Part of #1489`; labels `feat`, `db`, `supabase`, `no-lab-note`; milestone M68).

---

## Part 2 — admin (#1489)

Branch `feat/1489-settings-links-admin` on top of Part 1. Read `apps/admin/CLAUDE.md` and copy the Guides admin (`apps/admin/app/(authed)/guides/`) file for file; only the fields change.

### Task 2.1: Field rules (TDD)

**Files:** Create `apps/admin/lib/settings-links/options.ts`, `apps/admin/lib/settings-links/options.test.ts`, `apps/admin/lib/settings-links/types.ts`

- [ ] **Step 1: Failing test**

```ts
import { describe, expect, it } from "vitest"
import { isSettingsLinkSection, isAllowedLinkUrl, SETTINGS_LINK_SECTIONS } from "./options"

describe("settings link options", () => {
  it("knows the three sections in display order", () => {
    expect(SETTINGS_LINK_SECTIONS).toEqual(["legal", "about", "support"])
    expect(isSettingsLinkSection("about")).toBe(true)
    expect(isSettingsLinkSection("beta")).toBe(false)
  })
  it("accepts https and mailto only", () => {
    expect(isAllowedLinkUrl("https://www.pbbls.app/docs/terms")).toBe(true)
    expect(isAllowedLinkUrl("mailto:ciao@pbbls.app")).toBe(true)
    expect(isAllowedLinkUrl("http://www.pbbls.app")).toBe(false)
    expect(isAllowedLinkUrl("javascript:alert(1)")).toBe(false)
    expect(isAllowedLinkUrl("")).toBe(false)
  })
})
```

Run: `npx vitest run lib/settings-links/options.test.ts` in `apps/admin` → FAIL (module not found). If the admin has no Vitest setup, check `apps/admin/package.json`; if absent, keep the test out and rely on the database check plus lint (note it in the PR).

- [ ] **Step 2: Implement**

```ts
// options.ts
export const SETTINGS_LINK_SECTIONS = ["legal", "about", "support"] as const
export type SettingsLinkSection = (typeof SETTINGS_LINK_SECTIONS)[number]
export const KEY_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/
export function isSettingsLinkSection(value: unknown): value is SettingsLinkSection {
  return typeof value === "string" && (SETTINGS_LINK_SECTIONS as readonly string[]).includes(value)
}
/** Mirrors the column CHECK: the database is the authority, this only spares a round trip. */
export function isAllowedLinkUrl(value: string): boolean {
  return /^(https:\/\/\S+|mailto:\S+)$/.test(value)
}
```

```ts
// types.ts
import type { Database } from "@pbbls/supabase"   // use the same import the guides types.ts uses
export type SettingsLinkRow = Database["public"]["Tables"]["settings_links"]["Row"]
export type SettingsLinkInsert = Database["public"]["Tables"]["settings_links"]["Insert"]
```

(Open `apps/admin/lib/guides/types.ts` and copy its import line exactly.)

- [ ] **Step 3:** Run the test → PASS. Commit `feat(admin): settings link field rules`.

### Task 2.2: Pages, actions and nav

**Files:** Create under `apps/admin/app/(authed)/settings-links/`: `actions.ts`, `page.tsx`, `new/page.tsx`, `[id]/page.tsx`, `_components/SettingsLinkForm.tsx`, `_components/SettingsLinkTable.tsx`, `_components/DeleteSettingsLinkButton.tsx`. Modify the admin nav (find it with `grep -rn '"/guides"' apps/admin/components apps/admin/app`).

- [ ] **Step 1:** `actions.ts` — copy `guides/actions.ts`, replacing `readGuideFields` with:

```ts
function readLinkFields(formData: FormData): { values: SettingsLinkInsert; error?: string } {
  const key = formData.get("key")?.toString().trim() ?? ""
  const sectionRaw = formData.get("section")?.toString()
  const sortRaw = formData.get("sort_order")?.toString().trim() ?? ""
  const sort_order = sortRaw === "" ? 0 : Number(sortRaw)
  const title_en = formData.get("title_en")?.toString().trim() ?? ""
  const title_fr = formData.get("title_fr")?.toString().trim() || null
  const url = formData.get("url")?.toString().trim() ?? ""
  const published = formData.get("published") === "on"
  const empty = {} as SettingsLinkInsert
  if (!KEY_PATTERN.test(key)) return { error: "Key is lowercase words joined by hyphens.", values: empty }
  if (!isSettingsLinkSection(sectionRaw)) return { error: "Pick a section.", values: empty }
  if (!Number.isInteger(sort_order) || sort_order < 0 || sort_order > 32767) return { error: "Sort order is a whole number from 0 to 32767.", values: empty }
  if (!title_en) return { error: "Title (EN) is required.", values: empty }
  if (!isAllowedLinkUrl(url)) return { error: "The URL starts with https:// or mailto:.", values: empty }
  return { values: { key, section: sectionRaw, sort_order, title_en, title_fr, url, published } }
}
```

and table `"settings_links"`, log prefix `[settings-links/…]`, paths `/settings-links`, duplicate message "A link with that key already exists."

- [ ] **Step 2:** `page.tsx`, `new/page.tsx`, `[id]/page.tsx` and the three components — copy their guides counterparts; the table shows columns Section, Order, Title (EN), URL (truncated, `title` attribute full), Published, ordered by `section` then `sort_order`. The form has: key (text), section (select of the three), sort order (number), title EN (required), title FR, URL (`type="text"`, `inputMode="url"`, placeholder `https://… or mailto:…`), published (checkbox). Page intro: "The links every app shows in Settings, by section. Changes reach the apps on their next Settings visit."

- [ ] **Step 3:** Add a nav entry "Settings links" → `/settings-links` right after Guides.

- [ ] **Step 4: Verify**

```bash
npm run lint --workspace=apps/admin && npm run build --workspace=apps/admin
```

Expected: no errors. Then run the admin (`npm run dev --workspace=apps/admin`, port 3001), sign in as admin, open `/settings-links`: five rows; create `test-link` → appears; edit it; delete it. Commit `feat(admin): edit the settings links`, push the part, PR `Resolves #1489`.

---

## Part 3 — web

Branch `feat/<web>-settings-links-web`. Read `apps/web/CLAUDE.md` and `docs/agents/data-and-async.md`.

### Task 3.1: Decoder and defaults (TDD)

**Files:** Create `apps/web/lib/settings/settings-links.ts`, `apps/web/lib/settings/settings-links.test.ts`; copy `packages/supabase/fixtures/settings-links.json` and `settings-links-edge.json` into `apps/web/lib/settings/__fixtures__/`.

- [ ] **Step 1: Failing test**

```ts
import { describe, expect, it } from "vitest"
import payload from "./__fixtures__/settings-links.json"
import edge from "./__fixtures__/settings-links-edge.json"
import { DEFAULT_SETTINGS_LINKS, decodeSettingsLinks, groupSettingsLinks } from "./settings-links"

describe("decodeSettingsLinks", () => {
  it("reads the database payload in English", () => {
    const links = decodeSettingsLinks(payload, "en")
    expect(links.map((l) => l.key)).toEqual(["credits", "terms", "privacy", "legal-notice", "support"])
    expect(links.find((l) => l.key === "support")).toMatchObject({ url: "mailto:ciao@pbbls.app", external: false })
  })
  it("reads French and falls back to English on a null title_fr", () => {
    expect(decodeSettingsLinks(payload, "fr").find((l) => l.key === "privacy")?.title).toBe("Politique de confidentialité")
    const nullFr = decodeSettingsLinks(edge, "fr").find((l) => l.section === "about")
    expect(nullFr?.title).toBe("Credits")
  })
  it("drops unknown sections and schemes", () => {
    const keys = decodeSettingsLinks(edge, "en").map((l) => l.url)
    expect(keys.some((u) => u.startsWith("ftp:"))).toBe(false)
    expect(decodeSettingsLinks(edge, "en").every((l) => ["legal", "about", "support"].includes(l.section))).toBe(true)
  })
  it("ships a built-in list equal to the seed", () => {
    expect(DEFAULT_SETTINGS_LINKS).toEqual(payload)
  })
  it("groups in the fixed section order", () => {
    expect(groupSettingsLinks(decodeSettingsLinks(payload, "en")).map((g) => g.section)).toEqual(["legal", "about", "support"])
  })
})
```

Run: `npx vitest run lib/settings/settings-links.test.ts` (in `apps/web`) → FAIL.

- [ ] **Step 2: Implement**

```ts
// apps/web/lib/settings/settings-links.ts
import seed from "./__fixtures__/settings-links.json"

export const SETTINGS_LINK_SECTIONS = ["legal", "about", "support"] as const
export type SettingsLinkSection = (typeof SETTINGS_LINK_SECTIONS)[number]
export type SettingsLink = { key: string; section: SettingsLinkSection; title: string; url: string; external: boolean }
export type SettingsLinkGroup = { section: SettingsLinkSection; links: SettingsLink[] }

/** The seed rows, verbatim: shown before the first fetch and whenever it fails (spec §3). */
export const DEFAULT_SETTINGS_LINKS: unknown = seed

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v)
}
function text(v: unknown): string | null {
  return typeof v === "string" && v.trim() !== "" ? v : null
}

/** `settings_links` rows → links in the reader's language. A row this build cannot show is dropped. */
export function decodeSettingsLinks(rows: unknown, locale: "en" | "fr"): SettingsLink[] {
  if (!Array.isArray(rows)) return []
  return rows.flatMap((row) => {
    if (!isRecord(row)) return []
    const key = text(row.key)
    const section = SETTINGS_LINK_SECTIONS.find((s) => s === row.section)
    const titleEn = text(row.title_en)
    const url = text(row.url)
    if (!key || !section || !titleEn || !url || !/^(https:\/\/|mailto:)/.test(url)) {
      console.warn("[settings] link dropped", row)
      return []
    }
    const title = (locale === "fr" ? text(row.title_fr) : null) ?? titleEn
    return [{ key, section, title, url, external: url.startsWith("https://") }]
  })
}

/** Sections in the fixed order, each keeping the rows' order; empty sections are omitted. */
export function groupSettingsLinks(links: SettingsLink[]): SettingsLinkGroup[] {
  return SETTINGS_LINK_SECTIONS.map((section) => ({ section, links: links.filter((l) => l.section === section) }))
    .filter((g) => g.links.length > 0)
}
```

- [ ] **Step 3:** Run → PASS. Commit `feat(web): decode settings links`.

### Task 3.2: Read path

**Files:** Modify `lib/data/data-provider.ts`, `lib/data/supabase-provider.ts`; create `lib/data/useSettingsLinks.ts`

- [ ] **Step 1:** `data-provider.ts`, next to `getGuides`: `getSettingsLinks(): Promise<unknown>`. `supabase-provider.ts`, next to `getGuides`:

```ts
  async getSettingsLinks(): Promise<unknown> {
    // A single-table read (AGENTS.md: no RPC needed); RLS hides unpublished rows too.
    const { data, error } = await withTimeout(
      this.supabase
        .from("settings_links")
        .select("key, section, sort_order, title_en, title_fr, url")
        .eq("published", true)
        .order("section")
        .order("sort_order"),
      10000,
      "settings_links",
    )
    if (error) throw new Error(`settings_links failed: ${error.message}`)
    return data
  }
```

Fix any test double that implements `DataProvider` (`npx tsc --noEmit -p .` lists them) by adding `getSettingsLinks: async () => []`.

- [ ] **Step 2:** `useSettingsLinks.ts`

```ts
"use client"

import { useAuth } from "@/lib/data/auth-context"
import { useKeptRead } from "@/lib/data/useKeptRead"
import { DEFAULT_SETTINGS_LINKS, decodeSettingsLinks, groupSettingsLinks, type SettingsLinkGroup } from "@/lib/settings/settings-links"

const kept = new Map<string, unknown>()

/** Settings' links (#1489): the last fetched rows, else the built-in seed, refreshed on each visit. */
export function useSettingsLinks(locale: "en" | "fr"): SettingsLinkGroup[] {
  const { user } = useAuth()
  const { data } = useKeptRead(kept, user ? user.id : null, (p) => p.getSettingsLinks(), "settings_links")
  return groupSettingsLinks(decodeSettingsLinks(data ?? DEFAULT_SETTINGS_LINKS, locale))
}
```

- [ ] **Step 3:** `npx tsc --noEmit -p .` (only the known `@pbbls/icons` noise if that package is unbuilt) and commit `feat(web): read settings links`.

### Task 3.3: Settings section

**Files:** Modify `components/settings/LegalSection.tsx`, `lib/i18n/messages/en.json`, `lib/i18n/messages/fr.json`

- [ ] **Step 1:** Add strings under `settings`: EN `"linkSections": { "legal": "Legal", "about": "About", "support": "Support" }`, FR `{ "legal": "Légal", "about": "À propos", "support": "Aide" }` (insert as text at the `settings` object, never re-dump the JSON — memory: formatting-sensitive catalogs).

- [ ] **Step 2:** Rewrite `LegalSection.tsx` to render one `SectionLabel` + `SettingsGroup` per group from `useSettingsLinks(locale)` (locale from `useLocale()` of next-intl, narrowed to `"en" | "fr"`). Each row: `SettingsRow` with `icon` `Scale` for legal, `Info` for about, `LifeBuoy` for support, trailing `ChevronRight`. For `external` links pass `href` and make `SettingsRow` open them in a new tab: add an optional `external?: boolean` prop to `SettingsRow` that renders `<a href target="_blank" rel="noopener noreferrer">` instead of `Link`; `mailto:` rows render a plain `<a href>`. Keep the component name and its two call sites (`app/settings/page.tsx`, `SettingsView.tsx`) unchanged. Section label ids: `settings-links-${section}`.

- [ ] **Step 3: Verify**

```bash
npm run lint --workspace=apps/web && (cd apps/web && npx vitest run)
```

Expected: lint clean, all tests pass. Run the dev server on :3100 (worktree rule), open `/settings`: Legal (3 rows), About (Credits), Support (Contact support). Toggle a row's `published` off in the admin, reload Settings: it disappears after the refresh. Commit `feat(web): settings links from the admin`.

### Task 3.4: Credits libraries

**Files:** Modify `apps/web/docs/credits/en.md`, `apps/web/docs/credits/fr.md`

- [ ] **Step 1:** Collect the real lists: iOS `cat apps/ios/Pebbles.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` (identities + their LICENSE from DerivedData `SourcePackages/checkouts/*/LICENSE*`); Android `apps/android/gradle/libs.versions.toml` (libraries actually in `implementation(...)` of `app/build.gradle.kts`); web `apps/web/package.json` `dependencies`. For each, record the licence name from its LICENSE file or package metadata — never guess.
- [ ] **Step 2:** Insert a `## Libraries` (FR `## Bibliothèques`) section before `## Intellectual inspirations` / `## Inspirations intellectuelles`: one paragraph per surface (Web, iOS, Android), each a list `**name** — holder · licence` with a link to the project. Bump frontmatter to `version: 1.3.0`, `last_updated:` today.
- [ ] **Step 3:** Open `/docs/credits` on :3100 and check both locales. Commit `docs(legal): list the libraries the apps ship`. Push the part, PR `Resolves #<web>`, labels `feat`, `web`, `legal`, `no-lab-note`, M68.

---

## Part 4 — ios-links

Branch `feat/<ios>-settings-links-ios`. Read `apps/ios/CLAUDE.md`; use a dedicated simulator and `-derivedDataPath` for this worktree (memory: parallel simulators). Tests are Swift Testing, never XCTest.

### Task 4.1: Model and decoding (TDD)

**Files:** Create `apps/ios/Pebbles/Features/Profile/Settings/SettingsLink.swift`, `apps/ios/PebblesTests/SettingsLinkTests.swift`; copy both fixture JSONs into `apps/ios/PebblesTests/Fixtures/` and the main one also into `apps/ios/Pebbles/Resources/settings-links-default.json` (the built-in list).

- [ ] **Step 1: Failing tests**

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("SettingsLink")
struct SettingsLinkTests {
    private func fixture(_ name: String) throws -> Data {
        let url = try #require(Bundle(for: BundleToken.self).url(forResource: name, withExtension: "json"))
        return try Data(contentsOf: url)
    }

    @Test func decodesTheDatabasePayload() throws {
        let links = try SettingsLink.decodeList(fixture("settings-links"))
        #expect(links.map(\.key) == ["credits", "terms", "privacy", "legal-notice", "support"])
        #expect(links.first { $0.key == "support" }?.url.scheme == "mailto")
    }

    @Test func frenchFallsBackToEnglish() throws {
        let links = try SettingsLink.decodeList(fixture("settings-links-edge"))
        let credits = try #require(links.first { $0.section == .about })
        #expect(credits.title(for: Locale(identifier: "fr_FR")) == "Credits")
    }

    @Test func dropsUnknownSectionsAndSchemes() throws {
        let links = try SettingsLink.decodeList(fixture("settings-links-edge"))
        #expect(links.allSatisfy { ["https", "mailto"].contains($0.url.scheme ?? "") })
    }

    @Test func builtInListIsTheSeed() throws {
        #expect(SettingsLink.builtIn.map(\.key) == ["credits", "terms", "privacy", "legal-notice", "support"])
    }

    @Test func groupsInFixedOrder() throws {
        let groups = SettingsLink.grouped(try SettingsLink.decodeList(fixture("settings-links")))
        #expect(groups.map(\.section) == [.legal, .about, .support])
    }
}

private final class BundleToken {}
```

(If the test target already has a fixture-loading helper — `grep -rn "forResource" apps/ios/PebblesTests | head` — use it instead of `BundleToken`.)

- [ ] **Step 2: Implement**

```swift
import Foundation
import os

/// One Settings row from `settings_links` (#1489): admin-edited, read directly
/// (a single-table select). FR falls back to EN, like `Guide`.
struct SettingsLink: Identifiable, Hashable {
    enum Section: String, CaseIterable { case legal, about, support }

    let key: String
    let section: Section
    let titleEn: String
    let titleFr: String?
    let url: URL

    var id: String { key }
    var opensInApp: Bool { url.scheme == "https" }

    func title(for locale: Locale) -> String { locale.prefersFrench ? (titleFr ?? titleEn) : titleEn }

    private struct Row: Decodable {
        let key: String?
        let section: String?
        let title_en: String?
        let title_fr: String?
        let url: String?
    }

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "settings-links")

    /// Rows this build cannot show (unknown section, other scheme, missing EN title) are dropped.
    static func decodeList(_ data: Data) throws -> [SettingsLink] {
        try JSONDecoder().decode([Row].self, from: data).compactMap { row in
            guard let key = row.key, let section = row.section.flatMap(Section.init(rawValue:)),
                  let titleEn = row.title_en, !titleEn.trimmingCharacters(in: .whitespaces).isEmpty,
                  let raw = row.url, let url = URL(string: raw), ["https", "mailto"].contains(url.scheme ?? "")
            else {
                logger.warning("settings link dropped: \(row.key ?? "nil", privacy: .public)")
                return nil
            }
            return SettingsLink(key: key, section: section, titleEn: titleEn, titleFr: row.title_fr, url: url)
        }
    }

    /// The seed, bundled: shown before the first fetch and whenever it fails.
    static let builtIn: [SettingsLink] = {
        guard let url = Bundle.main.url(forResource: "settings-links-default", withExtension: "json"),
              let data = try? Data(contentsOf: url), let links = try? decodeList(data) else { return [] }
        return links
    }()

    static func grouped(_ links: [SettingsLink]) -> [(section: Section, links: [SettingsLink])] {
        Section.allCases.compactMap { section in
            let rows = links.filter { $0.section == section }
            return rows.isEmpty ? nil : (section, rows)
        }
    }
}
```

(`Locale.prefersFrench` already exists — `Guide` uses it.)

- [ ] **Step 3:** `xcodegen generate` (in `apps/ios`), then run the suite:

```bash
xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination "platform=iOS Simulator,id=<this worktree's simulator>" -derivedDataPath build/dd-settings-links -only-testing:PebblesTests/SettingsLinkTests
```

Expected: 5 tests pass. Commit `feat(ios): decode settings links`.

### Task 4.2: Service with disk copy

**Files:** Create `apps/ios/Pebbles/Features/Profile/Settings/SettingsLinksService.swift`; add a test to `SettingsLinkTests.swift`.

- [ ] **Step 1: Failing test** — the cache round-trip:

```swift
    @Test func keepsTheLastPayloadOnDisk() throws {
        let defaults = try #require(UserDefaults(suiteName: "settings-links-test"))
        defaults.removePersistentDomain(forName: "settings-links-test")
        let store = SettingsLinksStore(defaults: defaults)
        #expect(store.current().map(\.key) == SettingsLink.builtIn.map(\.key))
        try store.save(fixture("settings-links-edge"))
        #expect(store.current().count == 1)   // only the credits copy with title_fr null survives
    }
```

- [ ] **Step 2: Implement**

```swift
import Foundation
import os
import Supabase

/// Settings' links (#1489): the last good payload on disk, else the bundled seed;
/// refreshed from `settings_links` each time Settings opens.
struct SettingsLinksStore {
    private static let key = "settingsLinks.payload"
    let defaults: UserDefaults

    func current() -> [SettingsLink] {
        guard let data = defaults.data(forKey: Self.key), let links = try? SettingsLink.decodeList(data) else {
            return SettingsLink.builtIn
        }
        return links
    }

    func save(_ data: Data) throws {
        _ = try SettingsLink.decodeList(data)   // never keep a payload this build cannot read
        defaults.set(data, forKey: Self.key)
    }
}

struct SettingsLinksService {
    let client: SupabaseClient
    var store = SettingsLinksStore(defaults: .standard)
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "settings-links")

    /// Fetches, keeps and returns the published links; on failure returns what is kept.
    func refresh() async -> [SettingsLink] {
        do {
            let data = try await client
                .from("settings_links")
                .select("key,section,sort_order,title_en,title_fr,url")
                .eq("published", value: true)
                .order("section")
                .order("sort_order")
                .execute()
                .data
            try store.save(data)
        } catch {
            logger.error("settings links refresh failed: \(error.localizedDescription, privacy: .public)")
        }
        return store.current()
    }
}
```

The edge fixture holds three rows (null `title_fr`, unknown section, `ftp:` URL); only the first survives decoding, hence `count == 1`.

- [ ] **Step 3:** Run the suite → PASS. Commit `feat(ios): keep and refresh settings links`.

### Task 4.3: Settings section

**Files:** Modify `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`, `apps/ios/Pebbles/Resources/Localizable.xcstrings` (path: `grep -rl '"Legal"' apps/ios/Pebbles --include=*.xcstrings`)

- [ ] **Step 1:** In `SettingsSheet`: `@State private var links: [SettingsLink] = SettingsLinksStore(defaults: .standard).current()` and `@State private var presentedLink: SettingsLink?`. Find how the sheet reaches the Supabase client (grep `supabase` in the file / its environment) and add `.task { links = await SettingsLinksService(client: <that client>).refresh() }`.
- [ ] **Step 2:** Replace `legalSection` with `linkSections`: `ForEach(SettingsLink.grouped(links), id: \.section)` → a `Section` per group, header `Text(sectionTitle(group.section)).pebblesSectionHeader()` with `LocalizedStringKey`s "Legal", "About", "Support"; rows are `Button` with `Label(link.title(for: locale), systemImage: icon(group.section))` (`doc.text`, `info.circle`, `lifepreserver`), `.buttonStyle(.plain)`, `.pebblesListRow(position:)` computed `.only` / `.top` / `.middle` / `.bottom` from the index (check `pebblesListRow`'s enum for the exact cases). Tap: `opensInApp` → `presentedLink = link`; otherwise `openURL(link.url)` (`@Environment(\.openURL)`).
- [ ] **Step 3:** Replace `.sheet(item: $presentedLegalDoc)` with `.sheet(item: $presentedLink) { LegalDocumentSheet(url: $0.url).ignoresSafeArea() }` and delete `presentedLegalDoc` from this file only (`LegalDoc` stays for `AuthView`/`WelcomeView`).
- [ ] **Step 4:** Add FR for "About" ("À propos") and "Support" ("Aide") in the xcstrings, inserted as text (memory: never whole-file re-dump).
- [ ] **Step 5: Verify:** `swiftlint` clean; full test suite (gate by byte-identity with a baseline run for references, memory: 27 local reference failures on untouched main); run on the simulator and screenshot Settings in EN and FR. Commit `feat(ios): settings links from the admin`; push the part; PR `Part of #<ios>`, labels `feat`, `ios`, `legal`, `no-lab-note`, M68.

---

## Part 5 — ios-licences

Branch `feat/<ios>-licences-ios` on top of Part 4.

### Task 5.1: Licence texts

**Files:** Create `apps/ios/Pebbles/Resources/Licenses/` entries.

- [ ] **Step 1:** Fonts: keep `InclusiveSans-OFL.txt`; add `Ysabeau-OFL.txt` (from `node_modules/@fontsource-variable/ysabeau/LICENSE`), `Caveat-OFL.txt` (`node_modules/@fontsource-variable/caveat/LICENSE`), `ReenieBeanie-OFL.txt` (`node_modules/@fontsource/reenie-beanie/LICENSE`) — copied byte for byte.
- [ ] **Step 2:** Packages: for every identity in `Pebbles.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`, copy the checkout's licence file (`build/dd-settings-links/SourcePackages/checkouts/<repo>/LICENSE*`) to `Licenses/<identity>.txt`. Commit `chore(ios): bundle the licence texts`.

### Task 5.2: Generator (TDD on the check)

**Files:** Create `apps/ios/Scripts/generate-licenses.mjs`; modify `apps/ios/package.json`

- [ ] **Step 1: Write the generator**

```js
#!/usr/bin/env node
// Builds Settings.bundle's Licenses pane from Pebbles/Resources/Licenses/*.txt (#1395, spec §4).
//   node Scripts/generate-licenses.mjs           write the plists
//   node Scripts/generate-licenses.mjs --check   fail if they are stale, or if a resolved
//                                                package has no Licenses/<identity>.txt
import { readFileSync, readdirSync, writeFileSync, existsSync, mkdirSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const licensesDir = path.join(root, "Pebbles/Resources/Licenses")
const bundleDir = path.join(root, "Pebbles/Resources/Settings.bundle")
const resolved = path.join(root, "Pebbles.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved")
const check = process.argv.includes("--check")

const TITLES = { "InclusiveSans-OFL": "Inclusive Sans", "Ysabeau-OFL": "Ysabeau", "Caveat-OFL": "Caveat", "ReenieBeanie-OFL": "Reenie Beanie" }
const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
const plist = (body) => `<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n<plist version="1.0">\n<dict>\n${body}</dict>\n</plist>\n`

const entries = readdirSync(licensesDir).filter((f) => f.endsWith(".txt")).sort((a, b) => a.localeCompare(b, "en", { sensitivity: "base" }))
  .map((file) => { const id = file.replace(/\.txt$/, ""); return { id, title: TITLES[id] ?? id, text: readFileSync(path.join(licensesDir, file), "utf8").trim() } })

const files = {
  "Root.plist": plist(`\t<key>StringsTable</key>\n\t<string>Root</string>\n\t<key>PreferenceSpecifiers</key>\n\t<array>\n\t\t<dict>\n\t\t\t<key>Type</key>\n\t\t\t<string>PSGroupSpecifier</string>\n\t\t\t<key>Title</key>\n\t\t\t<string>Pebbles</string>\n\t\t\t<key>FooterText</key>\n\t\t\t<string>Copyright © 2026 Pebbles</string>\n\t\t</dict>\n\t\t<dict>\n\t\t\t<key>Type</key>\n\t\t\t<string>PSChildPaneSpecifier</string>\n\t\t\t<key>Title</key>\n\t\t\t<string>Licenses</string>\n\t\t\t<key>File</key>\n\t\t\t<string>Licenses</string>\n\t\t</dict>\n\t</array>\n`),
  "Licenses.plist": plist(`\t<key>StringsTable</key>\n\t<string>Root</string>\n\t<key>PreferenceSpecifiers</key>\n\t<array>\n${entries.map((e) => `\t\t<dict>\n\t\t\t<key>Type</key>\n\t\t\t<string>PSChildPaneSpecifier</string>\n\t\t\t<key>Title</key>\n\t\t\t<string>${esc(e.title)}</string>\n\t\t\t<key>File</key>\n\t\t\t<string>License-${esc(e.id)}</string>\n\t\t</dict>\n`).join("")}\t</array>\n`),
  ...Object.fromEntries(entries.map((e) => [`License-${e.id}.plist`, plist(`\t<key>PreferenceSpecifiers</key>\n\t<array>\n\t\t<dict>\n\t\t\t<key>Type</key>\n\t\t\t<string>PSGroupSpecifier</string>\n\t\t\t<key>FooterText</key>\n\t\t\t<string>${esc(e.text)}</string>\n\t\t</dict>\n\t</array>\n`)])),
}

let problems = []
if (existsSync(resolved)) {
  const pins = JSON.parse(readFileSync(resolved, "utf8")).pins ?? []
  for (const pin of pins) if (!entries.some((e) => e.id === pin.identity)) problems.push(`no Licenses/${pin.identity}.txt for resolved package ${pin.identity}`)
} else if (check) {
  problems.push(`Package.resolved not found at ${resolved}: run xcodegen and resolve packages first`)
}

if (check) {
  for (const [name, content] of Object.entries(files)) {
    const p = path.join(bundleDir, name)
    if (!existsSync(p) || readFileSync(p, "utf8") !== content) problems.push(`Settings.bundle/${name} is stale: run npm run generate:licenses`)
  }
  if (problems.length) { console.error(problems.join("\n")); process.exit(1) }
  console.log(`licences OK (${entries.length} components)`)
} else {
  mkdirSync(bundleDir, { recursive: true })
  for (const [name, content] of Object.entries(files)) writeFileSync(path.join(bundleDir, name), content)
  for (const lang of ["en", "fr"]) {
    mkdirSync(path.join(bundleDir, `${lang}.lproj`), { recursive: true })
    writeFileSync(path.join(bundleDir, `${lang}.lproj/Root.strings`), lang === "fr" ? `"Licenses" = "Licences";\n` : `"Licenses" = "Licenses";\n`)
  }
  if (problems.length) console.warn(problems.join("\n"))
  console.log(`wrote Settings.bundle (${entries.length} components)`)
}
```

- [ ] **Step 2:** `apps/ios/package.json` scripts: `"generate:licenses": "node Scripts/generate-licenses.mjs"`, `"verify:licenses": "node Scripts/generate-licenses.mjs --check"`.
- [ ] **Step 3: See the check fail, then pass**

```bash
cd apps/ios && npm run verify:licenses    # Expected: FAIL — Settings.bundle/Root.plist is stale
npm run generate:licenses && npm run verify:licenses   # Expected: licences OK (N components)
plutil -lint Pebbles/Resources/Settings.bundle/*.plist  # Expected: every file OK
```

- [ ] **Step 4:** Commit `feat(ios): generate the settings licences pane`.

### Task 5.3: Ship it in the build and CI

- [ ] **Step 1:** `xcodegen generate`; confirm `Settings.bundle` is in the app's Copy Bundle Resources (`grep -n "Settings.bundle" Pebbles.xcodeproj/project.pbxproj`). If not, add to the `Pebbles` target's `sources` in `project.yml`: `- path: Pebbles/Resources/Settings.bundle` with `buildPhase: resources` and `type: folder`.
- [ ] **Step 2:** In `.github/workflows/ios.yml`, test job, after "Run tests": a step `Verify licences` with `working-directory` matching the neighbouring steps and `run: npm run verify:licenses --workspace=@pbbls/ios` (the job already resolved packages by then; if `npm ci` is not run in that job, use `node apps/ios/Scripts/generate-licenses.mjs --check`). `actionlint` the file.
- [ ] **Step 3:** Install on the simulator, open **Settings → Pebbles → Licenses**: one row per component, each opening its text; footer "Copyright © 2026 Pebbles"; FR shows "Licences". Screenshot it. Commit `chore(ios): check the licences pane in ci`; push; PR `Resolves #<ios>` (and comment on #1395 that iOS is done), labels `feat`, `ios`, `legal`, `no-lab-note`, M68.

---

## Part 6 — android

Branch `feat/<android>-settings-links-android`. Read `apps/android/CLAUDE.md`; `export ANDROID_HOME` first; the gate is `./gradlew lint` + unit tests; screenshot references are CI-only (memory).

### Task 6.1: Model and decoding (TDD)

**Files:** Create `app/src/main/kotlin/app/pbbls/android/core/model/SettingsLink.kt`, `app/src/test/kotlin/app/pbbls/android/core/data/SettingsLinksDecodingTest.kt`; copy both fixtures to `app/src/test/resources/` and the main one to `app/src/main/assets/settings-links-default.json`.

- [ ] **Step 1: Failing test**

```kotlin
package app.pbbls.android.core.data

import app.pbbls.android.core.model.SettingsLink
import app.pbbls.android.core.model.SettingsLinkSection
import app.pbbls.android.core.model.decodeSettingsLinks
import app.pbbls.android.core.model.groupSettingsLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SettingsLinksDecodingTest {
    private fun fixture(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)).readText()

    @Test fun `reads the database payload`() {
        val links = decodeSettingsLinks(fixture("settings-links.json"))
        assertEquals(listOf("credits", "terms", "privacy", "legal-notice", "support"), links.map { it.key })
        assertEquals("mailto:ciao@pbbls.app", links.single { it.key == "support" }.url)
    }

    @Test fun `french falls back to english`() {
        val credits = decodeSettingsLinks(fixture("settings-links-edge.json")).first { it.section == SettingsLinkSection.ABOUT }
        assertEquals("Credits", credits.title(Locale.FRENCH))
    }

    @Test fun `drops unknown sections and schemes`() {
        val links = decodeSettingsLinks(fixture("settings-links-edge.json"))
        assertTrue(links.all { it.url.startsWith("https://") || it.url.startsWith("mailto:") })
    }

    @Test fun `groups in the fixed order`() {
        val groups = groupSettingsLinks(decodeSettingsLinks(fixture("settings-links.json")))
        assertEquals(listOf(SettingsLinkSection.LEGAL, SettingsLinkSection.ABOUT, SettingsLinkSection.SUPPORT), groups.map { it.first })
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests '*SettingsLinksDecodingTest*'` → FAIL.

- [ ] **Step 2: Implement**

```kotlin
package app.pbbls.android.core.model

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/** The three sections, in display order; mirrors the column CHECK (#1489). */
enum class SettingsLinkSection(val wire: String) {
    LEGAL("legal"), ABOUT("about"), SUPPORT("support");

    companion object {
        fun fromWire(wire: String?): SettingsLinkSection? = entries.firstOrNull { it.wire == wire }
    }
}

/** One Settings row from `settings_links`: admin-edited; FR falls back to EN, like [home.Guide]. */
data class SettingsLink(
    val key: String,
    val section: SettingsLinkSection,
    val titleEn: String,
    val titleFr: String?,
    val url: String,
) {
    val opensInBrowser: Boolean get() = url.startsWith("https://")

    fun title(locale: Locale): String = if (locale.language == "fr") titleFr ?: titleEn else titleEn
}

@Serializable
private data class SettingsLinkDto(
    val key: String? = null,
    val section: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("title_fr") val titleFr: String? = null,
    val url: String? = null,
)

private val json = Json { ignoreUnknownKeys = true }
private val ALLOWED = Regex("^(https://|mailto:).+")

/** Rows this build cannot show (unknown section, other scheme, blank EN title) are dropped. */
fun decodeSettingsLinks(body: String): List<SettingsLink> =
    json.decodeFromString<List<SettingsLinkDto>>(body).mapNotNull { row ->
        val section = SettingsLinkSection.fromWire(row.section)
        val url = row.url
        if (row.key == null || section == null || row.titleEn.isNullOrBlank() || url == null || !ALLOWED.matches(url)) {
            runCatching { Log.w("settings-links", "dropped ${row.key}") }
            null
        } else {
            SettingsLink(row.key, section, row.titleEn, row.titleFr, url)
        }
    }

fun groupSettingsLinks(links: List<SettingsLink>): List<Pair<SettingsLinkSection, List<SettingsLink>>> =
    SettingsLinkSection.entries.mapNotNull { section ->
        links.filter { it.section == section }.takeIf { it.isNotEmpty() }?.let { section to it }
    }
```

(`runCatching` around `Log` because plain JVM unit tests have no `android.util.Log` unless `unitTests.isReturnDefaultValues` is on — check `app/build.gradle.kts` and drop the wrapper if it is.) Run → PASS. `./gradlew ktlintCheck` (or `npm run lint --workspace=@pbbls/android`). Commit `feat(android): decode settings links`.

### Task 6.2: Service with disk copy

**Files:** Create `app/src/main/kotlin/app/pbbls/android/core/data/SettingsLinksService.kt`

- [ ] **Step 1:** Implement, following `HomeService`'s injection and `SettingsStore`'s DataStore (reuse the app's Preferences DataStore via `SettingsDataStores.of`, a new `stringPreferencesKey("settings_links_payload")`):

```kotlin
@Singleton
class SettingsLinksService
    @Inject
    constructor(
        private val supabase: SupabaseService,
        @ApplicationContext private val context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        private val store by lazy { SettingsDataStores.of(context, io) }

        /** The kept payload, else the bundled seed. */
        suspend fun current(): List<SettingsLink> {
            val kept = store.data.first()[PAYLOAD]
            return kept?.let { runCatching { decodeSettingsLinks(it) }.getOrNull() } ?: builtIn()
        }

        /** Fetches, keeps and returns the published links; on failure returns [current]. */
        suspend fun refresh(): List<SettingsLink> {
            runCatchingCancellable {
                val body =
                    supabase.client.from("settings_links").select(Columns.raw("key,section,sort_order,title_en,title_fr,url")) {
                        filter { eq("published", true) }
                        order("section", Order.ASCENDING)
                        order("sort_order", Order.ASCENDING)
                    }.data
                decodeSettingsLinks(body) // never keep a payload this build cannot read
                store.edit { it[PAYLOAD] = body }
            }.onFailure { Log.e(TAG, "settings links refresh failed", it) }
            return current()
        }

        private fun builtIn(): List<SettingsLink> =
            decodeSettingsLinks(context.assets.open("settings-links-default.json").bufferedReader().use { it.readText() })

        private companion object {
            const val TAG = "settings-links"
            val PAYLOAD = stringPreferencesKey("settings_links_payload")
        }
    }
```

Check `SettingsDataStores.of` returns one shared instance per process (two DataStores on one file crash); if it constructs a new one per call, inject the existing `DataStore<Preferences>` the way `SettingsStore` gets it instead. Commit `feat(android): keep and refresh settings links`.

### Task 6.3: Settings section and opening links

**Files:** Modify `features/profile/SettingsViewModel.kt`, `features/profile/SettingsContent.kt`, `core/designsystem/LegalDocs.kt`, `res/values/strings.xml`, `res/values-fr/strings.xml`

- [ ] **Step 1:** `SettingsUiState` gains `val links: List<SettingsLink> = emptyList()`. The ViewModel injects `SettingsLinksService`; in its existing init/load: `_state.update { it.copy(links = linksService.current()) }` then `_state.update { it.copy(links = linksService.refresh()) }` in `viewModelScope`.
- [ ] **Step 2:** `LegalDocs.kt`: add `fun openSettingsLink(context: Context, link: SettingsLink)` — `https` → `CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(link.url))`; `mailto:` → `context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(link.url)))` wrapped in `try { … } catch (e: ActivityNotFoundException) { Log.w(...) }`. Keep `LegalDoc`/`openLegalDoc` for the funnel.
- [ ] **Step 3:** `SettingsContent.kt`: replace the Legal `ListGroup` with one `ListGroup` per `groupSettingsLinks(state.links)`, header from `R.string.settings_links_legal|about|support`, items `SettingsLinkItem(text = link.title(Locale.getDefault()), onClick = { openSettingsLink(context, link) })`. Strings: EN "Legal" (reuse `settings_legal_header` value), "About", "Support"; FR "Légal", "À propos", "Aide".
- [ ] **Step 4:** Bundle the font licences: copy the four OFL texts (same sources as Task 5.1) to `app/src/main/assets/licenses/` (`Ysabeau-OFL.txt`, `InclusiveSans-OFL.txt`, `Caveat-OFL.txt`, `ReenieBeanie-OFL.txt`).
- [ ] **Step 5: Verify:** `./gradlew lint :app:testDebugUnitTest` green (diff lint against main by issue id, not line — memory); install on the Pixel 7 AVD, Settings shows Legal/About/Support, Credits opens a Custom Tab, Contact support opens the mail chooser. Don't touch the maintainer's physical device account without asking. Commit `feat(android): settings links from the admin`; push; PR `Resolves #<android>` (comment on #1395 that Android is done and close it), labels `feat`, `android`, `legal`, `no-lab-note`, M68. Re-baselining screenshots, if the Settings preview changed, follows the `android-screenshot-gate` skill.

---

## Finish

- [ ] Arkaik: update the Settings view nodes of web/iOS/Android to `development` when their part starts (`update_node`, send existing metadata back — memory) and add a data-model node `DM-settings-links` + api-endpoint node for the table read, with edges to the three Settings views and the admin view. Never set `live`.
- [ ] Append to `docs/decisions/log.md`: "Settings links are remote (settings_links, admin-edited); consent links stay built in (spec D2)."
- [ ] Build journal: one pebble for the whole stack (`--size medium --valence highlight`).
