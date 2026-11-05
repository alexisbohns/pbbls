# Interactive onboarding: back-end, admin and Android — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the seven-step onboarding of `docs/superpowers/specs/2026-11-04-interactive-onboarding-design.md` on Android, with the back-end contract it needs (`promise_letters`, two `profiles` columns, `complete_onboarding`) and the admin pages that edit the letter and count the answers.

**Architecture:** One stack of six parts, bottom to top: (1) the database contract and its harness, (2) the admin Promise page and the two analytics counts, (3) Android's data layer and the gate that reads `onboarding_completed`, (4) the Android shell with the progress stones and steps 1–3, (5) the split-stone glyph grid with steps 4–5 and the two satellites, (6) the pace step, the hand-off into Home's quick composer, screenshots and the map. iOS and web get their own plans later and follow the same step ids and the same RPC payload.

**Tech Stack:** Postgres (plpgsql, RLS) via Supabase migrations and Deno harnesses; Next.js 16 admin (server actions, shadcn); Kotlin + Jetpack Compose (Material 3 Expressive 1.5.0-beta02, Hilt, Navigation 3), JUnit + Robolectric UI tests, Compose Preview screenshot tests.

**Spec deviations, decided here, each named in its task:**
- `souls.glyph_id` is `not null`, so a soul in the payload always carries a `glyph_id`; the Android client assigns the first system glyph when a person is named without a pick (D7 says "each with a glyph").
- Android's sign-up already records the four consent acts, so step 2 shows an already-recorded act as a switch that is on and locked; only missing acts are asked (D3 and D10, "already recorded are not re-asked").
- The glyph-drawing satellite is a content swap inside the onboarding entry (the way `GlyphPickerContent` hosts the carve studio), not a pushed `GlyphCarve` key: a pushed entry has no way to hand the carved glyph back.

- **Correction, found on the device pass (2026-11-05):** Task 6.2 below sends `soul_ids` through `save_moment`, which ignores them: only `complete_pebble` reads `soul_ids`, and there is no moment-to-soul table. The shipped hand-off opens the composer with the prompt and attaches nobody; D9's "first soul pre-attached" is an open question on #1510. Task 6.2's payload, prefill and name lookup were removed in the build.

---

## Stack and branches

Issue #1510 carries the spec; file one back-end issue and one Android issue before Part 1 (`[Feat] Interactive onboarding back-end: promise letter, answers and complete_onboarding` with labels `feat`, `db`, `api`, `supabase`; `[Feat] Android interactive onboarding` with labels `feat`, `ui`, `core`, `android`; both on milestone `M68 · V1 launch`). The admin work rides the back-end issue.

```bash
cd /Users/alexis/code/pbbls && git fetch origin && git checkout main && git pull --ff-only
gh issue create --title "[Feat] Interactive onboarding back-end: promise letter, answers and complete_onboarding" --label feat --label db --label api --label supabase --milestone "M68 · V1 launch" --body "Part of #1510. Spec: docs/superpowers/specs/2026-11-04-interactive-onboarding-design.md §2. Plan: docs/superpowers/plans/2026-11-05-interactive-onboarding-android.md parts 1–2."
gh issue create --title "[Feat] Android interactive onboarding" --label feat --label ui --label core --label android --milestone "M68 · V1 launch" --body "Part of #1510. Spec §1, §3 (Android). Plan parts 3–6. Mirrors to iOS and web follow."
```

Write the two numbers down as `BE` and `AN`. Branch names carry `-android` in the slug for the native parts (Vercel ignores them, see `feedback_native_branch_names_carry_surface`):

| Part | Branch | PR title |
|---|---|---|
| 1 | `feat/BE-onboarding-contract` | `feat(db): promise letters, onboarding answers and complete_onboarding` |
| 2 | `feat/BE-onboarding-admin` | `feat(admin): promise page and onboarding answer counts` |
| 3 | `feat/AN-onboarding-data-android` | `feat(android): onboarding data layer and the completed gate` |
| 4 | `feat/AN-onboarding-shell-android` | `feat(android): onboarding shell, promise, consent and reasons steps` |
| 5 | `feat/AN-onboarding-stones-android` | `feat(android): onboarding you and first people on the split stone` |
| 6 | `feat/AN-onboarding-handoff-android` | `feat(android): onboarding pace, hand-off to the quick composer, screenshots` |

```bash
git config rerere.enabled true
gh stack init feat/BE-onboarding-contract      # from main, after the issue numbers are known
# later, from the top of the stack each time:
gh stack add feat/BE-onboarding-admin
gh stack add feat/AN-onboarding-data-android
# … and so on. Submit with: gh stack submit --auto
```

Every PR body starts with `Resolves #BE` (parts 1–2) or `Resolves #AN` (parts 3–6; only the top part says `Resolves`, the lower ones say `Part of #AN`), lists key files, and carries no Kritik finding id. Parts 4–6 are user-facing: label them `feat` and author the `## Lab Note (EN/FR)` with the `lab-note` skill, or label `no-lab-note` if the Arkaik coverage replaces it (`feedback_arkaik_replaces_lab_note`). Parts 1–3 get `no-lab-note`.

**Local verification before each PR:**
- Part 1: `npm run lint --workspace=packages/supabase` (deno check) and the harness against the linked project after the maintainer pushes the migration.
- Part 2: `npm run lint --workspace=apps/admin && npm run test --workspace=apps/admin && npm run build --workspace=apps/admin`.
- Parts 3–6: `cd apps/android && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew :app:testDebugUnitTest :app:lintDebug ktlintCheck`. Screenshot references are CI-only (`project_android_screenshot_gate_ci_only`): never re-baseline locally; Part 6 adds the previews and the maintainer approves the held run.

---

## File structure

### Part 1 — back-end
- Create `packages/supabase/supabase/migrations/20261105090000_interactive_onboarding.sql`: the table, the two columns, `complete_onboarding`, `get_onboarding_answers`.
- Create `packages/supabase/scripts/verify-onboarding.ts`: the contract harness.
- Modify `packages/supabase/package.json`: `db:verify:onboarding` and the `db:verify` chain.
- Modify `.github/workflows/supabase.yml`: one step.
- Modify `packages/supabase/CLAUDE.md`: the harness table row and the orphan prefix.
- Modify `packages/supabase/types/database.ts`: regenerated (`db:types:remote`) after the push.

### Part 2 — admin
- Create `apps/admin/lib/promise-letters/types.ts`.
- Create `apps/admin/app/(authed)/promise/{page.tsx,actions.ts,new/page.tsx,[id]/page.tsx,_components/PromiseLetterForm.tsx,_components/PromiseLetterTable.tsx,_components/DeletePromiseLetterButton.tsx}`.
- Modify `apps/admin/components/layout/Sidebar.tsx`.
- Modify `apps/admin/lib/analytics/types.ts`, `apps/admin/lib/analytics/fetchers.ts`.
- Create `apps/admin/components/analytics/{OnboardingAnswersCard.tsx,OnboardingAnswers.tsx,__fixtures__/onboarding-answers.ts}`.
- Modify `apps/admin/app/(authed)/analytics/page.tsx`, `apps/admin/app/(authed)/playground/analytics/page.tsx`.

### Part 3 — Android data and gate (`apps/android/app/src/main/kotlin/app/pbbls/android/` unless said)
- Create `core/model/Onboarding.kt`: `PromiseLetter`, `OnboardingReason`, `OnboardingPace`, `OnboardingSoulDraft`, `OnboardingAnswers`, the payload encoder.
- Create `core/data/OnboardingService.kt`: `OnboardingServicing` and the Supabase implementation.
- Create `core/data/OnboardingHandoff.kt`: the one-shot hand-off from the flow to Home.
- Modify `core/data/ProfileService.kt`: `ProfileRow.onboardingCompleted`, the select list.
- Modify `di/ServiceBindings.kt`: the binding.
- Modify `RootViewModel.kt`, `RootScreen.kt`: `onboardingCompleted` in the state, the gate, the consent gate's deferral.
- Modify `features/onboarding/OnboardingGate.kt`, `features/onboarding/OnboardingSteps.kt`.
- Modify `features/consent/ConsentGateViewModel.kt`: `restart`.
- Tests: `app/src/test/kotlin/app/pbbls/android/core/model/OnboardingPayloadTest.kt`, `features/onboarding/OnboardingGateTest.kt`, `features/onboarding/OnboardingStepsTest.kt`, `RootViewModelTest.kt`, `testing/FakeOnboardingService.kt`, `testing/FakeServicesModule.kt`, `testing/FakeProfileService.kt`, `ui/RootGateTest.kt`.
- Fixture: `app/src/test/resources/onboarding/complete_onboarding_payloads.json` (the verbatim payload the harness dumps).

### Part 4 — shell and steps 1–3
- Rewrite `features/onboarding/OnboardingScreen.kt` (the shell), delete `features/onboarding/OnboardingPageView.kt`.
- Create `features/onboarding/OnboardingViewModel.kt`, `features/onboarding/OnboardingUiState.kt`.
- Create `features/onboarding/steps/{ProgressStones.kt,PromiseStep.kt,ConsentStep.kt,ReasonsStep.kt}`.
- Modify `res/values/strings.xml`, `res/values-fr/strings.xml`, `navigation/PebblesEntryProvider.kt`, `core/common/JourneyTags.kt`.
- Tests: `features/onboarding/OnboardingViewModelTest.kt`.

### Part 5 — stones and steps 4–5
- Create `core/designsystem/stone/StoneGrid.kt`: the 3×3 and 1×4 split stones.
- Create `features/onboarding/steps/{GlyphStoneGrid.kt,YouStep.kt,PeopleStep.kt,SoulGlyphSheet.kt}`.
- Modify `OnboardingScreen.kt`, `OnboardingViewModel.kt`, `OnboardingUiState.kt`, strings.
- Tests: `OnboardingViewModelTest.kt` (souls, glyphs), `core/designsystem/stone/StoneGridTest.kt`.

### Part 6 — pace, hand-off, screenshots, map
- Create `features/onboarding/steps/PaceStep.kt`.
- Modify `core/model/MomentPayloads.kt` (`soul_ids`), `features/home/quick/QuickMoment.kt` (prefill), `features/home/HomeScreen.kt`, `features/home/HomeMomentsViewModel.kt`, `RootScreen.kt`.
- Modify `app/src/screenshotTest/kotlin/app/pbbls/android/FunnelScreenshots.kt`.
- Arkaik nodes, decisions log entry, the build journal pebble.

---

# Part 1 — the database contract

### Task 1.1: The migration

**Files:**
- Create: `packages/supabase/supabase/migrations/20261105090000_interactive_onboarding.sql`

- [ ] **Step 1: Branch**

```bash
cd /Users/alexis/code/pbbls && git checkout main && git pull --ff-only
gh stack init feat/BE-onboarding-contract
```

- [ ] **Step 2: Write the migration**

```sql
-- Migration: interactive onboarding (#1510, spec 2026-11-04 §2)
-- 1. promise_letters: the letter step 1 shows, admin-edited (modelled on
--    settings_links 20261101120000: EN required, FR falls back to EN on the
--    client, public read gated on `published`, writes admin-only). Not user
--    data: no purge_account append. No emotion or domain: no
--    sync_achievement_catalog().
-- 2. profiles.onboarding_reasons / profiles.pace: the two answers every
--    surface reads on launch (D6). Nullable; null = skipped or pre-flow.
-- 3. complete_onboarding: one transaction for everything the flow collected
--    (D5). Consent is NOT in here: step 2 records acts as they happen through
--    record_consent.
-- 4. get_onboarding_answers: the admin's two distribution counts.

-- ---------------------------------------------------------------------------
-- 1. promise_letters
-- ---------------------------------------------------------------------------
create table public.promise_letters (
  id            uuid primary key default gen_random_uuid(),
  body_md_en    text not null check (btrim(body_md_en) <> ''),
  body_md_fr    text,
  signed_by_en  text not null default 'The Pebbles team',
  signed_by_fr  text,
  published     boolean not null default false,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.promise_letters is
  'The promise every new account reads before consenting (#1510 step 1). Edited in the admin; clients read the most recently updated published row and fall back to EN when FR is null.';

create trigger promise_letters_updated_at
  before update on public.promise_letters
  for each row execute function public.set_updated_at();

alter table public.promise_letters enable row level security;

create policy "promise_letters_select" on public.promise_letters
  for select using (published = true or public.is_admin(auth.uid()));
create policy "promise_letters_insert" on public.promise_letters
  for insert with check (public.is_admin(auth.uid()));
create policy "promise_letters_update" on public.promise_letters
  for update using (public.is_admin(auth.uid()));
create policy "promise_letters_delete" on public.promise_letters
  for delete using (public.is_admin(auth.uid()));

create index promise_letters_published_idx on public.promise_letters (published, updated_at desc);

insert into public.promise_letters (id, body_md_en, body_md_fr, signed_by_en, signed_by_fr, published) values (
  md5('promise_letters:v1')::uuid,
  E'There will never be a feed here.\n\nNothing you keep becomes an ad, a trend, or data to train a machine.\n\nIf you leave, your pebbles leave with you, in a file you can open anywhere.',
  E'Il n''y aura jamais de fil d''actualité ici.\n\nRien de ce que tu gardes ne devient une pub, une tendance ou des données pour entraîner une machine.\n\nSi tu pars, tes galets partent avec toi, dans un fichier que tu peux ouvrir n''importe où.',
  'The Pebbles team',
  'L''équipe Pebbles',
  true
);

-- ---------------------------------------------------------------------------
-- 2. profiles columns
-- ---------------------------------------------------------------------------
alter table public.profiles
  add column onboarding_reasons text[],
  add column pace text;

alter table public.profiles
  add constraint profiles_onboarding_reasons_check check (
    onboarding_reasons is null or (
      onboarding_reasons <@ array['notice-feelings','keep-people','hard-season','good-days','own-patterns','keep-record']::text[]
      and cardinality(onboarding_reasons) between 1 and 6
    )
  ),
  add constraint profiles_pace_check check (
    pace is null or pace in ('daily','few-a-week','when-it-matters','weekly-wrap')
  );

comment on column public.profiles.onboarding_reasons is 'Step 3 of the onboarding (#1510): the reasons picked, by slug. Null = skipped or pre-flow account.';
comment on column public.profiles.pace is 'Step 6 of the onboarding (#1510): the pace picked. Read by reminders (M69). Null = skipped or pre-flow account.';

-- ---------------------------------------------------------------------------
-- 3. complete_onboarding
-- ---------------------------------------------------------------------------
-- Owner-checked on auth.uid(). Idempotent: a second call on a completed
-- profile is a no-op, not an error (D5). Errors carry stable names, as
-- record_consent's do, so clients switch on them instead of parsing
-- constraint names. A soul always carries a glyph_id: souls.glyph_id is not
-- null, and D7 says each first person has a glyph.
create function public.complete_onboarding(
  p_display_name text    default null,
  p_glyph_id     uuid    default null,
  p_reasons      text[]  default null,
  p_pace         text    default null,
  p_souls        jsonb   default '[]'::jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_done    boolean;
  v_soul    jsonb;
  v_name    text;
  v_glyph   uuid;
  v_names   text[] := '{}';
begin
  if v_user_id is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  select onboarding_completed into v_done
    from public.profiles
   where user_id = v_user_id
     for update;
  if v_done is null then
    raise exception 'profile_missing';
  end if;
  if v_done then
    return;
  end if;

  if p_reasons is not null and (
    cardinality(p_reasons) = 0
    or not (p_reasons <@ array['notice-feelings','keep-people','hard-season','good-days','own-patterns','keep-record']::text[])
  ) then
    raise exception 'invalid_reason';
  end if;

  if p_pace is not null and p_pace not in ('daily','few-a-week','when-it-matters','weekly-wrap') then
    raise exception 'invalid_pace';
  end if;

  if p_display_name is not null and btrim(p_display_name) = '' then
    raise exception 'invalid_display_name';
  end if;

  if p_glyph_id is not null and not public.can_use_glyph(p_glyph_id, v_user_id) then
    raise exception 'glyph_not_usable' using errcode = '42501';
  end if;

  if p_souls is null or jsonb_typeof(p_souls) <> 'array' then
    raise exception 'invalid_souls';
  end if;
  if jsonb_array_length(p_souls) > 3 then
    raise exception 'too_many_souls';
  end if;

  for v_soul in select value from jsonb_array_elements(p_souls) loop
    if jsonb_typeof(v_soul) <> 'object' then
      raise exception 'invalid_souls';
    end if;
    v_name := btrim(v_soul->>'name');
    if v_name is null or v_name = '' then
      raise exception 'invalid_soul_name';
    end if;
    if lower(v_name) = any(v_names) then
      raise exception 'duplicate_soul';
    end if;
    v_names := v_names || lower(v_name);
    begin
      v_glyph := nullif(v_soul->>'glyph_id', '')::uuid;
    exception when invalid_text_representation then
      raise exception 'invalid_soul_glyph';
    end;
    if v_glyph is null then
      raise exception 'invalid_soul_glyph';
    end if;
    if not public.can_use_glyph(v_glyph, v_user_id) then
      raise exception 'glyph_not_usable' using errcode = '42501';
    end if;
    insert into public.souls (user_id, name, glyph_id) values (v_user_id, v_name, v_glyph);
  end loop;

  -- The profile's name and glyph go through update_profile, so the two share
  -- one write path (spec §2.3).
  if p_display_name is not null or p_glyph_id is not null then
    perform public.update_profile(nullif(btrim(p_display_name), ''), p_glyph_id);
  end if;

  update public.profiles
     set onboarding_reasons   = p_reasons,
         pace                 = p_pace,
         onboarding_completed = true,
         updated_at           = now()
   where user_id = v_user_id;
end;
$$;

revoke all on function public.complete_onboarding(text, uuid, text[], text, jsonb) from public, anon;
grant execute on function public.complete_onboarding(text, uuid, text[], text, jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- 4. get_onboarding_answers (admin): distribution only, never per user.
-- ---------------------------------------------------------------------------
create function public.get_onboarding_answers()
returns table (kind text, value text, users bigint)
language plpgsql
security definer
stable
set search_path = public
as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;
  return query
    select 'reason'::text, r.reason, count(*)::bigint
      from public.profiles p, unnest(p.onboarding_reasons) as r(reason)
     group by r.reason
    union all
    select 'pace'::text, p.pace, count(*)::bigint
      from public.profiles p
     where p.pace is not null
     group by p.pace
    union all
    select 'completed'::text, case when p.onboarding_completed then 'yes' else 'no' end, count(*)::bigint
      from public.profiles p
     group by p.onboarding_completed
     order by 1, 2;
end;
$$;

revoke all on function public.get_onboarding_answers() from public, anon;
grant execute on function public.get_onboarding_answers() to authenticated;
```

- [ ] **Step 3: Replay it on the native local Postgres**

Per `project_local_postgres_for_migrations`: replay the chain on the Homebrew `postgresql@16` instance the maintainer keeps for this, then run these probes:

```sql
select complete_onboarding();            -- as anon: ERROR not_authenticated
select count(*) from promise_letters where published; -- 1
```

Expected: the file applies without error; the two probes behave as commented.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/migrations/20261105090000_interactive_onboarding.sql
git commit -m "feat(db): promise letters, onboarding answers and complete_onboarding"
```

### Task 1.2: The contract harness

**Files:**
- Create: `packages/supabase/scripts/verify-onboarding.ts`
- Modify: `packages/supabase/package.json`
- Modify: `.github/workflows/supabase.yml`
- Modify: `packages/supabase/CLAUDE.md`

- [ ] **Step 1: Write the harness**

```ts
#!/usr/bin/env -S deno run --allow-env --allow-net --allow-read --allow-write
/**
 * Acceptance test for the interactive onboarding contract (#1510 §2, §4) — runs against the REMOTE project, anon only.
 *   1. promise_letters: anon reads the published letter (EN body, FR body, signature); anon cannot write.
 *   2. complete_onboarding happy path: name, glyph, three reasons, a pace and three souls land in one call;
 *      onboarding_completed turns true; the souls are ordinary rows with the glyph given.
 *   3. Idempotent: a second call with other values changes nothing (no fourth soul, same name).
 *   4. Refusals on a fresh user, each proven by a re-read that shows nothing landed:
 *      a fourth soul, a soul without a glyph, another user's glyph, a duplicate name, an unknown reason,
 *      an unknown pace, a non-array souls payload.
 *   5. Anon is refused. The two profile columns are not in get_public_profile.
 *   6. ONBOARDING_DUMP=1 writes the verbatim payload sent in 2 to scripts/oracles/onboarding/payloads/complete_onboarding.json,
 *      which the clients' foreign-payload tests replay byte for byte.
 * Run: set -a; . ./.env; set +a; npm run db:verify:onboarding --workspace=packages/supabase
 * Signs up throwaway users (onboarding-verify-*@example.test) and deletes them through the real
 * `delete-account` edge function, even on failure.
 */
import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");
if (!SUPABASE_URL || !ANON_KEY) { console.error("SUPABASE_URL and SUPABASE_ANON_KEY must be set"); Deno.exit(2); }

let passed = 0; let failed = 0;
function check(name: string, ok: boolean, detail?: string) {
  if (ok) { passed += 1; console.log(`✓ ${name}`); } else { failed += 1; console.log(`✗ ${name}${detail ? ` — ${detail}` : ""}`); }
}

const runId = crypto.randomUUID().slice(0, 8);
const password = `Onb-${crypto.randomUUID()}`;
type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `onboarding-verify-${label}-${runId}@example.test`;
  const { data, error } = await client.auth.signUp({ email, password });
  if (error || !data.session || !data.user) throw new Error(`signUp ${label}: ${error?.message ?? "no session"}`);
  return { client, id: data.user.id, token: data.session.access_token };
}
async function deleteAccount(token: string): Promise<Response> {
  return await fetch(`${SUPABASE_URL}/functions/v1/delete-account`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, apikey: ANON_KEY!, "Content-Type": "application/json" },
  });
}

type Profile = { display_name: string; glyph_id: string | null; onboarding_reasons: string[] | null; pace: string | null; onboarding_completed: boolean };
async function profile(u: TestUser): Promise<Profile> {
  const { data, error } = await u.client.from("profiles").select("display_name, glyph_id, onboarding_reasons, pace, onboarding_completed").single();
  if (error) throw new Error(`profile: ${error.message}`);
  return data as Profile;
}
async function souls(u: TestUser): Promise<{ name: string; glyph_id: string }[]> {
  const { data, error } = await u.client.from("souls").select("name, glyph_id").order("name");
  if (error) throw new Error(`souls: ${error.message}`);
  return data as { name: string; glyph_id: string }[];
}
async function systemGlyphs(u: TestUser): Promise<string[]> {
  const { data, error } = await u.client.from("glyphs").select("id").eq("is_system", true).limit(3);
  if (error) throw new Error(`glyphs: ${error.message}`);
  return (data ?? []).map((g) => g.id as string);
}
/** A refused call must leave the profile incomplete and soulless. */
async function refused(u: TestUser, name: string, args: Record<string, unknown>, expect: string) {
  const { error } = await u.client.rpc("complete_onboarding", args);
  const p = await profile(u);
  const s = await souls(u);
  check(`${name} is refused (${expect})`, error !== null && error.message.includes(expect) && !p.onboarding_completed && s.length === 0,
    error?.message ?? "no error");
}

const DUMP = Deno.env.get("ONBOARDING_DUMP") === "1";
const anon = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
let alice: TestUser | undefined; let bob: TestUser | undefined; let carol: TestUser | undefined;

try {
  // 1. promise_letters
  const letters = await anon.from("promise_letters").select("body_md_en, body_md_fr, signed_by_en, signed_by_fr, updated_at").order("updated_at", { ascending: false });
  const letter = letters.data?.[0] as { body_md_en: string; body_md_fr: string | null; signed_by_en: string } | undefined;
  check("anon reads a published promise letter", !letters.error && !!letter && letter.body_md_en.includes("never be a feed"), letters.error?.message ?? letter?.body_md_en);
  check("the letter is signed", letter?.signed_by_en === "The Pebbles team", letter?.signed_by_en);
  const ins = await anon.from("promise_letters").insert({ body_md_en: `verify-${runId}`, published: true });
  const after = await anon.from("promise_letters").select("id").like("body_md_en", "verify-%");
  check("anon insert is refused", ins.error !== null && (after.data ?? []).length === 0, ins.error?.message);
  await anon.from("promise_letters").update({ body_md_en: "hijacked" }).eq("published", true);
  const still = await anon.from("promise_letters").select("body_md_en").order("updated_at", { ascending: false });
  check("anon update is refused", still.data?.[0]?.body_md_en === letter?.body_md_en);

  // 2. happy path
  alice = await signUp("a");
  bob = await signUp("b");
  carol = await signUp("c");
  const [g1, g2, g3] = await systemGlyphs(alice);
  check("three system glyphs exist for the starter grid", !!g1 && !!g2 && !!g3);
  const payload = {
    p_display_name: "  Maya  ",
    p_glyph_id: g1,
    p_reasons: ["notice-feelings", "keep-people", "keep-record"],
    p_pace: "weekly-wrap",
    p_souls: [{ name: "Noé", glyph_id: g1 }, { name: " Léa ", glyph_id: g2 }, { name: "Jo", glyph_id: g3 }],
  };
  if (DUMP) {
    await Deno.mkdir(new URL("./oracles/onboarding/payloads/", import.meta.url), { recursive: true });
    await Deno.writeTextFile(new URL("./oracles/onboarding/payloads/complete_onboarding.json", import.meta.url), JSON.stringify(payload, null, 2) + "\n");
  }
  const happy = await alice.client.rpc("complete_onboarding", payload);
  check("complete_onboarding succeeds", happy.error === null, happy.error?.message);
  const pa = await profile(alice);
  check("display name is trimmed and the glyph set", pa.display_name === "Maya" && pa.glyph_id === g1, JSON.stringify(pa));
  check("reasons and pace land", JSON.stringify(pa.onboarding_reasons) === JSON.stringify(payload.p_reasons) && pa.pace === "weekly-wrap", JSON.stringify(pa));
  check("onboarding_completed is true", pa.onboarding_completed);
  const sa = await souls(alice);
  check("three ordinary souls with their glyphs, names trimmed",
    sa.length === 3 && sa.map((s) => s.name).join(",") === "Jo,Léa,Noé" && sa.find((s) => s.name === "Léa")?.glyph_id === g2, JSON.stringify(sa));

  // 3. idempotent
  const again = await alice.client.rpc("complete_onboarding", { p_display_name: "Other", p_souls: [{ name: "Zed", glyph_id: g1 }] });
  const pa2 = await profile(alice);
  check("a second call is a no-op", again.error === null && pa2.display_name === "Maya" && (await souls(alice)).length === 3, again.error?.message);

  // 4. refusals (bob stays fresh between them: each refusal rolls back)
  const { data: carolGlyph, error: carveError } = await carol.client.from("glyphs")
    .insert({ user_id: carol.id, strokes: [[{ x: 10, y: 10 }, { x: 190, y: 190 }]], view_box: "0 0 200 200", name: `verify-${runId}` })
    .select("id").single();
  check("carol carves a glyph of her own", carveError === null && !!carolGlyph?.id, carveError?.message);
  await refused(bob, "a fourth soul", { p_souls: [{ name: "A", glyph_id: g1 }, { name: "B", glyph_id: g1 }, { name: "C", glyph_id: g1 }, { name: "D", glyph_id: g1 }] }, "too_many_souls");
  await refused(bob, "a soul without a glyph", { p_souls: [{ name: "A", glyph_id: null }] }, "invalid_soul_glyph");
  await refused(bob, "another user's glyph on a soul", { p_souls: [{ name: "A", glyph_id: carolGlyph!.id }] }, "glyph_not_usable");
  await refused(bob, "another user's glyph on the profile", { p_glyph_id: carolGlyph!.id }, "glyph_not_usable");
  await refused(bob, "a duplicate name", { p_souls: [{ name: "Ana", glyph_id: g1 }, { name: " ana ", glyph_id: g2 }] }, "duplicate_soul");
  await refused(bob, "a blank name", { p_souls: [{ name: "   ", glyph_id: g1 }] }, "invalid_soul_name");
  await refused(bob, "an unknown reason", { p_reasons: ["notice-feelings", "get-rich"] }, "invalid_reason");
  await refused(bob, "an empty reasons array", { p_reasons: [] }, "invalid_reason");
  await refused(bob, "an unknown pace", { p_pace: "hourly" }, "invalid_pace");
  await refused(bob, "a non-array souls payload", { p_souls: { name: "A" } }, "invalid_souls");
  const skipped = await bob.client.rpc("complete_onboarding", {});
  const pb = await profile(bob);
  check("everything skipped still completes", skipped.error === null && pb.onboarding_completed && pb.onboarding_reasons === null && pb.pace === null, skipped.error?.message);

  // 5. anon and the public projection
  const anonCall = await anon.rpc("complete_onboarding", {});
  check("anon is refused", anonCall.error !== null, "anon call succeeded");
  const handle = `onb${runId}`;
  await alice.client.rpc("set_handle", { p_handle: handle });
  await alice.client.rpc("set_public_profile", { p_public: true }).catch(() => undefined);
  const pub = await anon.rpc("get_public_profile", { p_handle: handle });
  const body = JSON.stringify(pub.data ?? {});
  check("get_public_profile does not project the answers", !body.includes("onboarding_reasons") && !body.includes("\"pace\""), body.slice(0, 200));
} finally {
  for (const u of [alice, bob, carol]) {
    if (!u) continue;
    const res = await deleteAccount(u.token).catch(() => undefined);
    if (!res?.ok) console.error(`cleanup: delete-account returned ${res?.status ?? "no response"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed === 0 ? 0 : 1);
```

If `set_public_profile` does not exist under that name, read `grep -rn "public_profile" packages/supabase/supabase/migrations/20260730120000_public_profiles.sql` and call the RPC that flips it; the check only needs a readable public profile.

- [ ] **Step 2: Wire the script, the workflow step and the docs**

`packages/supabase/package.json`: append `&& npm run db:verify:onboarding` to `db:verify` and add

```json
"db:verify:onboarding": "deno run --allow-env --allow-net --allow-read --allow-write scripts/verify-onboarding.ts",
```

`.github/workflows/supabase.yml`, after the settings-links step:

```yaml
      - name: Interactive onboarding (promise letter, complete_onboarding)
        if: '!cancelled()'
        run: .github/scripts/verify-harness.sh "onboarding" db:verify:onboarding
```

`packages/supabase/CLAUDE.md`: one row in the harness table (`npm run db:verify:onboarding` · `verify-onboarding.ts` · the promise letter's read and admin-only writes, `complete_onboarding`'s happy path, idempotence and refusals, the answers off the public profile; `ONBOARDING_DUMP=1` writes the payload to `scripts/oracles/onboarding/payloads/` · anon) and `onboarding-verify-` in the orphan prefixes.

- [ ] **Step 3: Lint**

```bash
npm run lint --workspace=packages/supabase
```

Expected: `deno check` passes with no errors.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/scripts/verify-onboarding.ts packages/supabase/package.json .github/workflows/supabase.yml packages/supabase/CLAUDE.md
git commit -m "test(db): contract harness for the onboarding letter and complete_onboarding"
```

### Task 1.3: Push, run the harness, regenerate the types

- [ ] **Step 1: Hand the maintainer the push** (the classifier blocks `db push` from the agent; see `project_moments_split`):

```bash
set -a; . ./.env; set +a
npm run db:push --workspace=packages/supabase
```

- [ ] **Step 2: Run the harness and dump the payload**

```bash
set -a; . ./.env; set +a
ONBOARDING_DUMP=1 npm run db:verify:onboarding --workspace=packages/supabase
```

Expected: `Summary: passed=25 failed=0` (count the `check` calls if the number drifts) and `packages/supabase/scripts/oracles/onboarding/payloads/complete_onboarding.json` written.

- [ ] **Step 3: Regenerate the types** (`project_db_types_remote`: never `db:types`, it targets Docker and truncates the file on failure)

```bash
npm run db:types:remote --workspace=packages/supabase
git diff --stat packages/supabase/types/database.ts   # promise_letters, the two columns, the two functions
```

- [ ] **Step 4: Commit and submit**

```bash
git add packages/supabase/types/database.ts packages/supabase/scripts/oracles/onboarding/payloads/complete_onboarding.json
git commit -m "chore(db): regenerate types and dump the complete_onboarding payload"
gh stack submit --auto
gh pr edit <n> --title "feat(db): promise letters, onboarding answers and complete_onboarding" --add-label feat --add-label db --add-label api --add-label supabase --add-label no-lab-note --milestone "M68 · V1 launch"
```

PR body: `Resolves #BE` is wrong here, the admin part also resolves it: write `Part of #BE` on this PR and `Resolves #BE` on Part 2.

---

# Part 2 — the admin

### Task 2.1: The Promise page

**Files:**
- Create: `apps/admin/lib/promise-letters/types.ts`
- Create: `apps/admin/app/(authed)/promise/actions.ts`
- Create: `apps/admin/app/(authed)/promise/page.tsx`
- Create: `apps/admin/app/(authed)/promise/new/page.tsx`
- Create: `apps/admin/app/(authed)/promise/[id]/page.tsx`
- Create: `apps/admin/app/(authed)/promise/_components/PromiseLetterForm.tsx`
- Create: `apps/admin/app/(authed)/promise/_components/PromiseLetterTable.tsx`
- Create: `apps/admin/app/(authed)/promise/_components/DeletePromiseLetterButton.tsx`
- Modify: `apps/admin/components/layout/Sidebar.tsx`

- [ ] **Step 1: Branch**

```bash
gh stack add feat/BE-onboarding-admin
```

- [ ] **Step 2: Types**

```ts
import type { Database } from "@pbbls/supabase"

export type PromiseLetterRow = Database["public"]["Tables"]["promise_letters"]["Row"]
export type PromiseLetterInsert = Database["public"]["Tables"]["promise_letters"]["Insert"]
```

- [ ] **Step 3: Actions** (`actions.ts`, the `settings-links/actions.ts` shape)

```ts
"use server"

import { redirect } from "next/navigation"
import { revalidatePath } from "next/cache"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { PromiseLetterInsert } from "@/lib/promise-letters/types"

export type PromiseLetterFormResult = { error: string } | undefined

function readFields(formData: FormData): { values: PromiseLetterInsert; error?: string } {
  const body_md_en = formData.get("body_md_en")?.toString().trim() ?? ""
  const body_md_fr = formData.get("body_md_fr")?.toString().trim() || null
  const signed_by_en = formData.get("signed_by_en")?.toString().trim() || "The Pebbles team"
  const signed_by_fr = formData.get("signed_by_fr")?.toString().trim() || null
  const published = formData.get("published") === "on"
  const empty = {} as PromiseLetterInsert
  if (!body_md_en) return { error: "The letter (EN) is required.", values: empty }
  return { values: { body_md_en, body_md_fr, signed_by_en, signed_by_fr, published } }
}

export async function createPromiseLetter(_state: PromiseLetterFormResult, formData: FormData): Promise<PromiseLetterFormResult> {
  const parsed = readFields(formData)
  if (parsed.error) return { error: parsed.error }
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("promise_letters").insert(parsed.values).select("id").single()
  if (error) {
    console.error("[promise/createPromiseLetter] insert failed:", error.message)
    return { error: "Could not save the letter." }
  }
  revalidatePath("/promise")
  redirect(`/promise/${data.id}`)
}

export async function updatePromiseLetter(id: string, _state: PromiseLetterFormResult, formData: FormData): Promise<PromiseLetterFormResult> {
  const parsed = readFields(formData)
  if (parsed.error) return { error: parsed.error }
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.from("promise_letters").update(parsed.values).eq("id", id)
  if (error) {
    console.error("[promise/updatePromiseLetter] update failed:", error.message)
    return { error: "Could not save the letter." }
  }
  revalidatePath("/promise")
  revalidatePath(`/promise/${id}`)
  return undefined
}

export async function deletePromiseLetter(id: string): Promise<void> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.from("promise_letters").delete().eq("id", id)
  if (error) {
    console.error("[promise/deletePromiseLetter] delete failed:", error.message)
    throw new Error("Could not delete the letter.")
  }
  revalidatePath("/promise")
  redirect("/promise")
}
```

- [ ] **Step 4: The form** (`_components/PromiseLetterForm.tsx`)

```tsx
"use client"

import { useActionState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import type { PromiseLetterRow } from "@/lib/promise-letters/types"
import type { PromiseLetterFormResult } from "../actions"

export type PromiseLetterFormAction = (state: PromiseLetterFormResult, formData: FormData) => Promise<PromiseLetterFormResult>

export function PromiseLetterForm({
  letter,
  action,
  submitLabel,
  extraActions,
}: {
  letter: PromiseLetterRow | null
  action: PromiseLetterFormAction
  submitLabel: string
  extraActions?: React.ReactNode
}) {
  const [state, formAction, pending] = useActionState<PromiseLetterFormResult, FormData>(action, undefined)

  return (
    <form action={formAction} className="space-y-8">
      <div className="space-y-2 text-sm">
        <Label htmlFor="published" className="text-foreground font-medium">
          Published
        </Label>
        <Switch id="published" name="published" defaultChecked={letter?.published ?? false} />
        <p className="text-muted-foreground">The apps show the most recently updated published letter.</p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <div className="space-y-4">
          <h2 className="text-lg font-semibold">English</h2>
          <div className="space-y-2">
            <Label htmlFor="body_md_en">Letter (Markdown, one paragraph per blank line)</Label>
            <Textarea id="body_md_en" name="body_md_en" rows={10} defaultValue={letter?.body_md_en ?? ""} required />
          </div>
          <div className="space-y-2">
            <Label htmlFor="signed_by_en">Signed by</Label>
            <Input id="signed_by_en" name="signed_by_en" defaultValue={letter?.signed_by_en ?? "The Pebbles team"} />
          </div>
        </div>
        <div className="space-y-4">
          <h2 className="text-lg font-semibold">Français (optional)</h2>
          <div className="space-y-2">
            <Label htmlFor="body_md_fr">Lettre</Label>
            <Textarea id="body_md_fr" name="body_md_fr" rows={10} defaultValue={letter?.body_md_fr ?? ""} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="signed_by_fr">Signée par</Label>
            <Input id="signed_by_fr" name="signed_by_fr" defaultValue={letter?.signed_by_fr ?? ""} />
          </div>
        </div>
      </div>

      {state?.error ? (
        <p role="alert" className="text-destructive text-sm">
          {state.error}
        </p>
      ) : null}

      <div className="flex items-center gap-3">
        <Button type="submit" disabled={pending}>
          {pending ? "Saving…" : submitLabel}
        </Button>
        {extraActions}
      </div>
    </form>
  )
}
```

- [ ] **Step 5: The table, the delete button, the three pages**

`_components/PromiseLetterTable.tsx`:

```tsx
import Link from "next/link"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import type { PromiseLetterRow } from "@/lib/promise-letters/types"

export function PromiseLetterTable({ letters }: { letters: PromiseLetterRow[] }) {
  if (letters.length === 0) return <p className="text-muted-foreground text-sm">No letter yet.</p>
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Opening line</TableHead>
          <TableHead>Signed by</TableHead>
          <TableHead>Updated</TableHead>
          <TableHead>Status</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {letters.map((l) => (
          <TableRow key={l.id}>
            <TableCell>
              <Link href={`/promise/${l.id}`} className="font-medium hover:underline">
                {l.body_md_en.split("\n")[0].slice(0, 80)}
              </Link>
            </TableCell>
            <TableCell>{l.signed_by_en}</TableCell>
            <TableCell className="tabular-nums">{new Date(l.updated_at).toISOString().slice(0, 10)}</TableCell>
            <TableCell><Badge variant={l.published ? "default" : "outline"}>{l.published ? "Published" : "Draft"}</Badge></TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
```

`_components/DeletePromiseLetterButton.tsx`: copy `settings-links/_components/DeleteSettingsLinkButton.tsx`, rename the component and import `deletePromiseLetter` from `../actions`; the dialog text is "Delete this letter?" / "New accounts will see the next published letter, or none."

`page.tsx`:

```tsx
import Link from "next/link"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { Button } from "@/components/ui/button"
import type { PromiseLetterRow } from "@/lib/promise-letters/types"
import { PromiseLetterTable } from "./_components/PromiseLetterTable"

export default async function PromisePage() {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("promise_letters").select("*").order("updated_at", { ascending: false })
  if (error) console.error("[promise] select failed:", error.message)
  const letters: PromiseLetterRow[] = data ?? []
  return (
    <section className="space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Promise</h1>
          <p className="text-muted-foreground text-sm">
            The letter every new account reads before consenting. The apps show the most recently updated published one.
          </p>
        </div>
        <Button render={<Link href="/promise/new" />}>New letter</Button>
      </header>
      {error ? (
        <p role="alert" className="text-destructive text-sm">Couldn&rsquo;t load the letters: {error.message}</p>
      ) : (
        <PromiseLetterTable letters={letters} />
      )}
    </section>
  )
}
```

`new/page.tsx` and `[id]/page.tsx`: copy the settings-links pair, swapping `SettingsLinkForm` → `PromiseLetterForm`, `createSettingsLink` → `createPromiseLetter`, `updateSettingsLink` → `updatePromiseLetter`, the breadcrumb root to `/promise` "Promise", the headings to "New letter" and the letter's first line, and the `[id]` select to `from("promise_letters")`.

`Sidebar.tsx`: add `{ href: "/promise", label: "Promise", icon: Feather }` to `LOG_ITEMS` after Guides and import `Feather` from `lucide-react`.

- [ ] **Step 6: Lint, test, build**

```bash
npm run lint --workspace=apps/admin && npm run test --workspace=apps/admin && npm run build --workspace=apps/admin
```

Expected: all three green. Open `http://localhost:3001/promise` with `npm run dev --workspace=apps/admin`: the seeded letter lists as Published; editing and saving it round-trips.

- [ ] **Step 7: Commit**

```bash
git add apps/admin/lib/promise-letters "apps/admin/app/(authed)/promise" apps/admin/components/layout/Sidebar.tsx
git commit -m "feat(admin): promise page editing the onboarding letter"
```

### Task 2.2: The two distribution counts on Analytics

**Files:**
- Modify: `apps/admin/lib/analytics/types.ts`, `apps/admin/lib/analytics/fetchers.ts`
- Create: `apps/admin/components/analytics/OnboardingAnswersCard.tsx`, `OnboardingAnswers.tsx`, `__fixtures__/onboarding-answers.ts`
- Modify: `apps/admin/app/(authed)/analytics/page.tsx`, `apps/admin/app/(authed)/playground/analytics/page.tsx`

- [ ] **Step 1: Type and fetcher**

`types.ts`, appended:

```ts
/** One row of get_onboarding_answers: a distribution bucket, never a user. */
export interface OnboardingAnswerRow {
  kind: "reason" | "pace" | "completed"
  value: string
  users: number
}
```

`fetchers.ts`, appended:

```ts
/** The onboarding answers' distribution (#1510). The RPC enforces `is_admin(auth.uid())`. */
export async function getOnboardingAnswers(): Promise<OnboardingAnswerRow[]> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("get_onboarding_answers")
  if (error) {
    console.error("[analytics] getOnboardingAnswers failed:", error.message)
    throw new Error(error.message)
  }
  return (data ?? []) as OnboardingAnswerRow[]
}
```

Add `OnboardingAnswerRow` to the `types` import at the top of `fetchers.ts`.

- [ ] **Step 2: Fixture**

`__fixtures__/onboarding-answers.ts`:

```ts
import type { OnboardingAnswerRow } from "@/lib/analytics/types"

export const onboardingAnswersDense: OnboardingAnswerRow[] = [
  { kind: "completed", value: "no", users: 14 },
  { kind: "completed", value: "yes", users: 86 },
  { kind: "pace", value: "daily", users: 21 },
  { kind: "pace", value: "few-a-week", users: 30 },
  { kind: "pace", value: "weekly-wrap", users: 19 },
  { kind: "pace", value: "when-it-matters", users: 10 },
  { kind: "reason", value: "good-days", users: 40 },
  { kind: "reason", value: "hard-season", users: 25 },
  { kind: "reason", value: "keep-people", users: 61 },
  { kind: "reason", value: "keep-record", users: 18 },
  { kind: "reason", value: "notice-feelings", users: 70 },
  { kind: "reason", value: "own-patterns", users: 33 },
]
export const onboardingAnswersSparse: OnboardingAnswerRow[] = [
  { kind: "completed", value: "yes", users: 2 },
  { kind: "reason", value: "keep-people", users: 1 },
]
export const onboardingAnswersEmpty: OnboardingAnswerRow[] = []
```

- [ ] **Step 3: Presentational and card**

`OnboardingAnswers.tsx` (server component, two lists of bars with the sentence tooltip the admin `CLAUDE.md` requires):

```tsx
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import type { OnboardingAnswerRow } from "@/lib/analytics/types"

const REASON_LABELS: Record<string, string> = {
  "notice-feelings": "Notice what I feel",
  "keep-people": "Keep the people",
  "hard-season": "Get through a hard season",
  "good-days": "Keep the good days",
  "own-patterns": "See my own patterns",
  "keep-record": "Keep a record for later",
}
const PACE_LABELS: Record<string, string> = {
  daily: "Every day",
  "few-a-week": "A few times a week",
  "when-it-matters": "Only when it matters",
  "weekly-wrap": "Wrapping the end of the week",
}

function Bars({ title, rows, labels, completed }: { title: string; rows: OnboardingAnswerRow[]; labels: Record<string, string>; completed: number }) {
  const max = Math.max(1, ...rows.map((r) => r.users))
  if (rows.length === 0) return <p className="text-muted-foreground text-sm">{title}: no answer yet.</p>
  return (
    <div className="space-y-2">
      <h3 className="text-sm font-medium">{title}</h3>
      <ul className="space-y-1">
        {[...rows].sort((a, b) => b.users - a.users).map((r) => (
          <li key={r.value} className="grid grid-cols-[1fr_auto] items-center gap-3 text-sm">
            <div>
              <div className="flex justify-between">
                <span>{labels[r.value] ?? r.value}</span>
                <Tooltip>
                  <TooltipTrigger className="cursor-help tabular-nums underline decoration-dotted">{r.users}</TooltipTrigger>
                  <TooltipContent>
                    {r.users} of the {completed} accounts that completed the onboarding picked &ldquo;{labels[r.value] ?? r.value}&rdquo;.
                  </TooltipContent>
                </Tooltip>
              </div>
              <div className="bg-muted h-1.5 rounded">
                <div className="bg-primary h-1.5 rounded" style={{ width: `${(100 * r.users) / max}%` }} />
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

export function OnboardingAnswers({ rows }: { rows: OnboardingAnswerRow[] }) {
  const completed = rows.find((r) => r.kind === "completed" && r.value === "yes")?.users ?? 0
  const notYet = rows.find((r) => r.kind === "completed" && r.value === "no")?.users ?? 0
  return (
    <div className="space-y-6">
      <p className="text-muted-foreground text-sm">
        {completed} accounts completed the onboarding, {notYet} have not. Distribution only, never per user.
      </p>
      <Bars title="What brings you here" rows={rows.filter((r) => r.kind === "reason")} labels={REASON_LABELS} completed={completed} />
      <Bars title="Pace" rows={rows.filter((r) => r.kind === "pace")} labels={PACE_LABELS} completed={completed} />
    </div>
  )
}
```

`OnboardingAnswersCard.tsx`: the `PebbleEnrichmentCard` shape, awaiting `getOnboardingAnswers()`, rendering `ErrorBlock` with label "Failed to load onboarding answers" on throw, title "Onboarding answers".

- [ ] **Step 4: Place the card and the playground section**

`analytics/page.tsx`: a `lg:col-span-6` cell after the domain share with `<Suspense fallback={<ChartCardSkeleton />}><OnboardingAnswersCard /></Suspense>`. `playground/analytics/page.tsx`: a `<section>` titled "Onboarding answers" rendering `<OnboardingAnswers rows={onboardingAnswersDense} />`, `…Sparse`, `…Empty` in three cards, using the existing section markup at line 85.

- [ ] **Step 5: Lint, test, build; commit**

```bash
npm run lint --workspace=apps/admin && npm run test --workspace=apps/admin && npm run build --workspace=apps/admin
git add apps/admin
git commit -m "feat(admin): onboarding reasons and pace distributions on analytics"
gh stack submit --auto
gh pr edit <n> --title "feat(admin): promise page and onboarding answer counts" --add-label feat --add-label admin --add-label no-lab-note --milestone "M68 · V1 launch"
```

PR body: `Resolves #BE`.

---

# Part 3 — Android data layer and the gate

Read `apps/android/CLAUDE.md` first (the "mirror iOS" table, folder layout, the sessionStatus-collector rule, lint and test). Load `android-skills:android-dev` and `android-skills:compose` before writing Kotlin.

### Task 3.1: Models and the payload encoder, tested against the harness dump

**Files:**
- Create: `core/model/Onboarding.kt`
- Create: `app/src/test/resources/onboarding/complete_onboarding_payload.json` (copy of `packages/supabase/scripts/oracles/onboarding/payloads/complete_onboarding.json`)
- Test: `app/src/test/kotlin/app/pbbls/android/core/model/OnboardingPayloadTest.kt`

- [ ] **Step 1: Branch and copy the fixture**

```bash
gh stack add feat/AN-onboarding-data-android
mkdir -p apps/android/app/src/test/resources/onboarding
cp packages/supabase/scripts/oracles/onboarding/payloads/complete_onboarding.json apps/android/app/src/test/resources/onboarding/complete_onboarding_payload.json
```

- [ ] **Step 2: Write the failing test**

```kotlin
package app.pbbls.android.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The complete_onboarding payload against the bytes the harness sent and the server accepted
 * (`scripts/oracles/onboarding/payloads/complete_onboarding.json`): a same-surface round-trip
 * cannot catch a same-surface encoder bug (the standing cross-surface rule).
 */
class OnboardingPayloadTest {
    private val oracle: JsonObject =
        Json.parseToJsonElement(
            checkNotNull(javaClass.getResourceAsStream("/onboarding/complete_onboarding_payload.json")).bufferedReader().readText(),
        ).jsonObject

    private val answers =
        OnboardingAnswers(
            displayName = "  Maya  ",
            glyphId = oracle["p_glyph_id"]!!.jsonPrimitive.content,
            reasons = listOf(OnboardingReason.NOTICE_FEELINGS, OnboardingReason.KEEP_PEOPLE, OnboardingReason.KEEP_RECORD),
            pace = OnboardingPace.WEEKLY_WRAP,
            souls =
                oracle["p_souls"]!!.jsonArray.map {
                    OnboardingSoulDraft(name = it.jsonObject["name"]!!.jsonPrimitive.content, glyphId = it.jsonObject["glyph_id"]!!.jsonPrimitive.content)
                },
        )

    @Test
    fun `encodes the harness payload verbatim`() {
        assertEquals(oracle, answers.toRpcParams())
    }

    @Test
    fun `a skipped step is an absent key, not null`() {
        val params = OnboardingAnswers().toRpcParams()
        assertNull(params["p_display_name"])
        assertNull(params["p_reasons"])
        assertNull(params["p_pace"])
        assertEquals("[]", params["p_souls"].toString())
    }

    @Test
    fun `reason and pace slugs are the spec's`() {
        assertEquals(
            listOf("notice-feelings", "keep-people", "hard-season", "good-days", "own-patterns", "keep-record"),
            OnboardingReason.entries.map { it.slug },
        )
        assertEquals(listOf("daily", "few-a-week", "when-it-matters", "weekly-wrap"), OnboardingPace.entries.map { it.slug })
    }

    @Test
    fun `the letter falls back to english`() {
        val letter = PromiseLetter(bodyEn = "en", bodyFr = null, signedByEn = "The Pebbles team", signedByFr = null)
        assertEquals("en", letter.body(french = true))
        assertEquals("The Pebbles team", letter.signedBy(french = true))
        val both = letter.copy(bodyFr = "fr", signedByFr = "L'équipe")
        assertEquals("fr", both.body(french = true))
        assertEquals("L'équipe", both.signedBy(french = true))
    }
}
```

- [ ] **Step 3: Run it to see it fail**

```bash
cd apps/android && ./gradlew :app:testDebugUnitTest --tests "app.pbbls.android.core.model.OnboardingPayloadTest"
```

Expected: compilation fails (`OnboardingAnswers` unresolved).

- [ ] **Step 4: Write the models**

```kotlin
package app.pbbls.android.core.model

import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The promise step's letter (#1510 D2), a `promise_letters` row; FR falls back to EN. */
@Serializable
data class PromiseLetter(
    @SerialName("body_md_en") val bodyEn: String,
    @SerialName("body_md_fr") val bodyFr: String? = null,
    @SerialName("signed_by_en") val signedByEn: String,
    @SerialName("signed_by_fr") val signedByFr: String? = null,
) {
    fun body(french: Boolean): String = if (french) bodyFr?.takeIf { it.isNotBlank() } ?: bodyEn else bodyEn

    fun signedBy(french: Boolean): String = if (french) signedByFr?.takeIf { it.isNotBlank() } ?: signedByEn else signedByEn
}

/** Step 3's six tiles (D4), in grid order. [slug] is the wire value in `profiles.onboarding_reasons`. */
enum class OnboardingReason(
    val slug: String,
    @StringRes val labelRes: Int,
    val icon: PbblsIcon,
) {
    NOTICE_FEELINGS("notice-feelings", R.string.onboarding_reason_notice_feelings, PbblsIcon.HEART),
    KEEP_PEOPLE("keep-people", R.string.onboarding_reason_keep_people, PbblsIcon.PEOPLE),
    HARD_SEASON("hard-season", R.string.onboarding_reason_hard_season, PbblsIcon.CLOUD),
    GOOD_DAYS("good-days", R.string.onboarding_reason_good_days, PbblsIcon.SUN),
    OWN_PATTERNS("own-patterns", R.string.onboarding_reason_own_patterns, PbblsIcon.PATH),
    KEEP_RECORD("keep-record", R.string.onboarding_reason_keep_record, PbblsIcon.BOOK),
}

/** Step 6's four paces (D11), in group order. [slug] is the wire value in `profiles.pace`. */
enum class OnboardingPace(
    val slug: String,
    @StringRes val labelRes: Int,
) {
    DAILY("daily", R.string.onboarding_pace_daily),
    FEW_A_WEEK("few-a-week", R.string.onboarding_pace_few_a_week),
    WHEN_IT_MATTERS("when-it-matters", R.string.onboarding_pace_when_it_matters),
    WEEKLY_WRAP("weekly-wrap", R.string.onboarding_pace_weekly_wrap),
}

/** One first person (step 5): a name as typed and the glyph on their piece. Always a glyph: `souls.glyph_id` is not null. */
data class OnboardingSoulDraft(
    val name: String,
    val glyphId: String,
)

/**
 * Everything the flow collected, as `complete_onboarding` takes it (D5). A skipped step is null and
 * is left out of the params, so the server sees its default; [souls] is always sent, empty when
 * step 5 was passed with "Later".
 */
data class OnboardingAnswers(
    val displayName: String? = null,
    val glyphId: String? = null,
    val reasons: List<OnboardingReason>? = null,
    val pace: OnboardingPace? = null,
    val souls: List<OnboardingSoulDraft> = emptyList(),
) {
    fun toRpcParams(): JsonObject =
        buildJsonObject {
            displayName?.let { put("p_display_name", it) }
            glyphId?.let { put("p_glyph_id", it) }
            reasons?.let { list -> putJsonArray("p_reasons") { list.forEach { add(it.slug) } } }
            pace?.let { put("p_pace", it.slug) }
            putJsonArray("p_souls") {
                souls.forEach { soul ->
                    addJsonObject {
                        put("name", soul.name)
                        put("glyph_id", soul.glyphId)
                    }
                }
            }
        }
}
```

The icon names: open `core/designsystem/PbblsIcons.kt` and pick six existing entries that read as the labels (the exploration used a heart, two people, a cloud, a sun, a winding path and a book; substitute the nearest drawn icon, never add one here). The string ids are added in Task 3.4.

- [ ] **Step 5: Run the test**

```bash
./gradlew :app:testDebugUnitTest --tests "app.pbbls.android.core.model.OnboardingPayloadTest"
```

Expected: 4 tests pass. If `encodes the harness payload verbatim` fails on key order only, compare with `assertEquals(oracle.toSortedMap(), params.toSortedMap())`: PostgREST reads an object, order is not part of the contract.

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/model/Onboarding.kt apps/android/app/src/test
git commit -m "feat(android): onboarding answers model and its rpc payload against the harness dump"
```

### Task 3.2: `OnboardingServicing`, the hand-off holder and the profile flag

**Files:**
- Create: `core/data/OnboardingService.kt`
- Create: `core/data/OnboardingHandoff.kt`
- Modify: `core/data/ProfileService.kt:60-64, 171-182`
- Modify: `di/ServiceBindings.kt`
- Create: `app/src/test/kotlin/app/pbbls/android/testing/FakeOnboardingService.kt`
- Modify: `app/src/test/kotlin/app/pbbls/android/testing/FakeServicesModule.kt`

- [ ] **Step 1: The service**

```kotlin
package app.pbbls.android.core.data

import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.OnboardingAnswers
import app.pbbls.android.core.model.PromiseLetter
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/** The onboarding's reads and its one write (#1510), a seam so `OnboardingViewModelTest` fakes it. */
interface OnboardingServicing {
    /** The most recently updated published letter, or null when none is published. */
    suspend fun promiseLetter(): PromiseLetter?

    /** The system glyphs, for the starter grid (D7): eight are shown. */
    suspend fun systemGlyphs(): List<Glyph>

    /** `complete_onboarding`: one transaction for everything collected (D5). Throws on refusal. */
    suspend fun complete(answers: OnboardingAnswers)
}

@Singleton
class OnboardingService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : OnboardingServicing {
        override suspend fun promiseLetter(): PromiseLetter? =
            supabase.client
                .from("promise_letters")
                .select(Columns.raw("body_md_en, body_md_fr, signed_by_en, signed_by_fr")) {
                    filter { eq("published", true) }
                    order("updated_at", Order.DESCENDING)
                    limit(1)
                }.decodeList<PromiseLetter>()
                .firstOrNull()

        override suspend fun systemGlyphs(): List<Glyph> =
            supabase.client
                .from("glyphs")
                .select(Columns.raw("id, name, strokes, view_box, user_id, is_system")) {
                    filter { eq("is_system", true) }
                    order("created_at", Order.ASCENDING)
                    limit(8)
                }.decodeList<Glyph>()

        override suspend fun complete(answers: OnboardingAnswers) {
            supabase.client.postgrest.rpc("complete_onboarding", answers.toRpcParams())
        }
    }
```

- [ ] **Step 2: The hand-off holder** (D9: the flow ends inside Home's composer, which is Home's; the flow leaves it a note)

```kotlin
package app.pbbls.android.core.data

import app.pbbls.android.core.model.OnboardingReason
import javax.inject.Inject
import javax.inject.Singleton

/** What the onboarding leaves for Home's quick composer (#1510 D9), read once. */
data class OnboardingHandoffNote(
    /** The first person named in step 5, pre-attached to the moment; null when none. */
    val soulId: String?,
    /** The first reason picked in step 3, which chooses the composer's prompt; null when skipped. */
    val reason: OnboardingReason?,
)

/**
 * A one-shot mailbox between the onboarding entry and Home: the flow [leave]s its note before
 * popping itself, and Home [take]s it on its next resume. Process memory only: a process death in
 * between loses the hand-off, which is fine (D9: dismissing the composer is fine too).
 */
@Singleton
class OnboardingHandoff
    @Inject
    constructor() {
        private var note: OnboardingHandoffNote? = null

        fun leave(value: OnboardingHandoffNote) {
            note = value
        }

        fun take(): OnboardingHandoffNote? = note.also { note = null }
    }
```

- [ ] **Step 3: `ProfileRow.onboardingCompleted`**

In `ProfileService.kt` the select becomes `Columns.raw("display_name, created_at, glyph_id, handle, public_profile, onboarding_completed")` and `ProfileRow` gains

```kotlin
    /** Server truth for the onboarding gate (#1510 D10); false on a pre-flow account too. */
    @SerialName("onboarding_completed")
    val onboardingCompleted: Boolean = false,
```

- [ ] **Step 4: Bindings and the fake**

`di/ServiceBindings.kt`, beside `bindSettingsLinksServicing`:

```kotlin
    @Binds
    @Singleton
    fun bindOnboardingServicing(impl: OnboardingService): OnboardingServicing
```

`testing/FakeOnboardingService.kt`:

```kotlin
package app.pbbls.android.testing

import app.pbbls.android.core.data.OnboardingServicing
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.OnboardingAnswers
import app.pbbls.android.core.model.PromiseLetter

/** In-memory [OnboardingServicing]: the letter, eight system glyphs, and every [complete] call recorded. */
class FakeOnboardingService(
    var letter: PromiseLetter? = PromiseLetter(bodyEn = "There will never be a feed here.", signedByEn = "The Pebbles team"),
    var glyphs: List<Glyph> = List(8) { Glyph(id = "sys-$it", strokes = emptyList(), viewBox = "0 0 200 200", isSystem = true) },
) : OnboardingServicing {
    val completeCalls = mutableListOf<OnboardingAnswers>()
    var letterFailure: Exception? = null
    var glyphsFailure: Exception? = null
    var completeFailure: Exception? = null

    override suspend fun promiseLetter(): PromiseLetter? {
        letterFailure?.let { throw it }
        return letter
    }

    override suspend fun systemGlyphs(): List<Glyph> {
        glyphsFailure?.let { throw it }
        return glyphs
    }

    override suspend fun complete(answers: OnboardingAnswers) {
        completeFailure?.let { throw it }
        completeCalls += answers
    }
}
```

`FakeServicesModule.kt`: `@Provides @Singleton fun fakeOnboarding() = FakeOnboardingService()` and a `@Provides @Singleton fun onboardingServicing(fake: FakeOnboardingService): OnboardingServicing = fake` in the same shape the module uses for `FakeSettingsLinksService` (read how that one is exposed as its interface and copy it). Also `@Provides @Singleton fun onboardingHandoff() = OnboardingHandoff()` only if the module provides concrete singletons; otherwise Hilt constructs it.

- [ ] **Step 5: Compile the unit tests and run the service fakes test**

```bash
./gradlew :app:testDebugUnitTest --tests "app.pbbls.android.testing.ServiceFakesTest"
```

Expected: green. If `ServiceFakesTest` enumerates every fake, add `FakeOnboardingService` to it the way the others are listed.

- [ ] **Step 6: Commit**

```bash
git add apps/android
git commit -m "feat(android): onboarding service, hand-off note and the profile's completed flag"
```

### Task 3.3: The gate reads `onboarding_completed`; the consent gate defers to the flow

**Files:**
- Modify: `features/onboarding/OnboardingGate.kt`
- Modify: `RootViewModel.kt:51-60, 155-180`
- Modify: `RootScreen.kt:103, 134-137, 160, 228-232`
- Modify: `features/consent/ConsentGateViewModel.kt`
- Test: `features/onboarding/OnboardingGateTest.kt`, `RootViewModelTest.kt`

The rule (D10): present when a user is signed in, this device has not seen the flow complete, and the server says `onboarding_completed = false`. Unknown server state (profile not loaded yet) presents nothing. The consent gate overlay stands down while the flow is due: step 2 collects the acts (promise before consent, D2), and the flow's end re-runs the gate's check.

- [ ] **Step 1: Rewrite the gate test**

```kotlin
package app.pbbls.android.features.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Onboarding gating (#1510 D10): a signed-in user whose profile says not completed, once per device. */
class OnboardingGateTest {
    @Test
    fun presentsWhenServerSaysNotCompleted() {
        assertTrue(OnboardingGate.shouldPresent(userId = "user-1", hasSeenOnboarding = false, onboardingCompleted = false))
    }

    @Test
    fun doesNotPresentWhenServerSaysCompleted() {
        assertFalse(OnboardingGate.shouldPresent(userId = "user-1", hasSeenOnboarding = false, onboardingCompleted = true))
    }

    @Test
    fun doesNotPresentWhileTheProfileIsUnknown() {
        assertFalse(OnboardingGate.shouldPresent(userId = "user-1", hasSeenOnboarding = false, onboardingCompleted = null))
    }

    @Test
    fun doesNotPresentTwiceOnOneDevice() {
        assertFalse(OnboardingGate.shouldPresent(userId = "user-1", hasSeenOnboarding = true, onboardingCompleted = false))
    }

    @Test
    fun doesNotPresentWithoutAUser() {
        assertFalse(OnboardingGate.shouldPresent(userId = null, hasSeenOnboarding = false, onboardingCompleted = false))
    }
}
```

- [ ] **Step 2: Run, see it fail to compile; then the gate**

```kotlin
package app.pbbls.android.features.onboarding

/**
 * Pure gating for the onboarding entry (#1510 D10). Present once a user id is known, this device
 * has not completed the flow (`hasSeenOnboarding`, kept so a device that just finished never
 * re-pushes while the profile re-reads), and the profile says `onboarding_completed = false`.
 * While the profile is unknown (null) nothing is presented: Home shows and the push follows the
 * read, as on web, where the auth callback reads the flag before routing.
 */
object OnboardingGate {
    fun shouldPresent(
        userId: String?,
        hasSeenOnboarding: Boolean,
        onboardingCompleted: Boolean?,
    ): Boolean = userId != null && !hasSeenOnboarding && onboardingCompleted == false
}
```

- [ ] **Step 3: `RootViewModel` reads the profile on sign-in**

Add `profile: ProfileServicing` to the constructor (after `supabase`), `val onboardingCompleted: Boolean? = null` to `RootUiState` with the doc `/** `profiles.onboarding_completed` for [userId]; null until read, and after a sign-out. */`, and in `onAuthState`, after the destination is computed:

```kotlin
            if (userId != null && userId != _uiState.value.userId) {
                _uiState.update { it.copy(onboardingCompleted = null) }
                viewModelScope.launch {
                    runCatchingCancellable { profile.loadProfile().onboardingCompleted }
                        .onSuccess { done -> _uiState.update { s -> if (s.userId == userId) s.copy(onboardingCompleted = done) else s } }
                        .onFailure { Log.e(TAG, "profile read for the onboarding gate failed", it) }
                }
            }
            if (userId == null) _uiState.update { it.copy(onboardingCompleted = null) }
```

Add a public `fun onOnboardingCompleted() = _uiState.update { it.copy(onboardingCompleted = true) }` so the flow's end does not wait for a re-read. Check how `_uiState` is updated with the destination in that function (it may build a new state once); fold these in so the user id and destination still land in one `update`. Add `import app.pbbls.android.core.data.ProfileServicing`, `runCatchingCancellable`, `Log` and a `private const val TAG = "root"` if the file has none.

`RootViewModelTest.kt`: every `RootViewModel(` construction gains `FakeProfileService()` as the second argument. Add one test:

```kotlin
    @Test
    fun `a sign-in reads the profile's onboarding flag`() =
        runTest {
            val profile = FakeProfileService(profile = ProfileRow(displayName = "P", createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"), onboardingCompleted = false))
            val vm = RootViewModel(supabase, profile, karma(), achievements(), FakeCompleteSnapshotStore(), FakeWrapDiskCache(), haptics(), SavedStateHandle())
            supabase.emitResolved(session = sessionFor("user-1"))
            advanceUntilIdle()
            assertEquals(false, vm.uiState.value.onboardingCompleted)
            supabase.emitResolved(session = null)
            assertNull(vm.uiState.value.onboardingCompleted)
        }
```

Use the file's own helpers for emitting a resolved session (read how the existing tests do `emitResolved`).

- [ ] **Step 4: `RootScreen`**

Replace `OnboardingGate.shouldPresent(userId, hasSeenOnboarding)` with `OnboardingGate.shouldPresent(userId, hasSeenOnboarding, root.onboardingCompleted)`. The push effect is keyed on `root.destination, settingsLoaded`; the gate's value now arrives after the destination, so add `shouldPresentOnboarding` to that effect's keys and guard the push with `navigator.rootKey == PebblesKey.Home && !navigator.isShowing(PebblesKey.Onboarding)`: read `Navigator.kt` for the back-stack accessor the class exposes and use that one (do not add a second Onboarding entry on a recomposition). Keep the `!wasAlreadyAuthed` reasoning for the restore case by checking the entry is not already on the stack rather than by the root key alone.

The consent gate stands down while the flow is due:

```kotlin
    val isConsentGated =
        root.destination == RootDestination.SignedIn &&
            !shouldPresentOnboarding &&
            consent != ConsentGateUiState.Satisfied(userId ?: "")
```

and `onOnboardingFinished` becomes

```kotlin
                    onOnboardingFinished = {
                        onboarding.setHasSeenOnboarding(true)
                        viewModel.onOnboardingCompleted()
                        consentViewModel.restart(userId)
                        navigator.goBack()
                    },
```

- [ ] **Step 5: `ConsentGateViewModel.restart`**

```kotlin
        /**
         * The onboarding's step 2 recorded acts behind this gate's back (#1510 D2): forget the verdict
         * and run [start] again, so a satisfied cache (marked by the flow) or a fresh ledger read decides.
         */
        fun restart(userId: String?) {
            this.userId = null
            _uiState.value = ConsentGateUiState.Idle
            start(userId)
        }
```

- [ ] **Step 6: `RootGateTest`**

`a first sign-in shows onboarding over Home` now has to walk the flow; until Part 4 ships the screens, make the test assert the entry is presented (the promise step's title appears, `R.string.onboarding_promise_title` added in Task 3.4) and leave the walk-through to Task 4.4. Set `profile.profile = profile.profile.copy(onboardingCompleted = false)` in that test and `skipOnboarding()` stays as is for the others.

- [ ] **Step 7: Run the unit tests**

```bash
./gradlew :app:testDebugUnitTest --tests "app.pbbls.android.features.onboarding.*" --tests "app.pbbls.android.RootViewModelTest" --tests "app.pbbls.android.ui.RootGateTest"
```

Expected: green except `RootGateTest.a first sign-in shows onboarding over Home`, which needs the string from the next task; finish Task 3.4 and re-run.

- [ ] **Step 8: Commit**

```bash
git add apps/android
git commit -m "feat(android): onboarding gate on the profile's completed flag, consent gate defers to the flow"
```

### Task 3.4: The seven-step config and the strings

**Files:**
- Modify: `features/onboarding/OnboardingSteps.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`
- Test: `features/onboarding/OnboardingStepsTest.kt`

- [ ] **Step 1: Rewrite the steps test**

```kotlin
package app.pbbls.android.features.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

/** The step config (#1510 §3): count, ids, order and which steps can be passed. M69 inserts reminders after pace. */
class OnboardingStepsTest {
    @Test
    fun sevenStepsInSpecOrder() {
        assertEquals(
            listOf("promise", "consent", "reasons", "you", "people", "pace", "handoff"),
            OnboardingSteps.all.map { it.id },
        )
    }

    @Test
    fun idsAreUnique() {
        assertEquals(OnboardingSteps.all.size, OnboardingSteps.all.map { it.id }.toSet().size)
    }

    @Test
    fun skipsMatchD1() {
        assertEquals(
            mapOf(
                "promise" to OnboardingSkip.NONE, "consent" to OnboardingSkip.NONE, "reasons" to OnboardingSkip.SKIP,
                "you" to OnboardingSkip.SKIP, "people" to OnboardingSkip.LATER, "pace" to OnboardingSkip.SKIP, "handoff" to OnboardingSkip.NONE,
            ),
            OnboardingSteps.all.associate { it.id to it.skip },
        )
    }

    @Test
    fun sixVisibleSteps() {
        assertEquals(6, OnboardingSteps.visible.size)
        assertEquals("pace", OnboardingSteps.visible.last().id)
    }
}
```

- [ ] **Step 2: The config**

```kotlin
package app.pbbls.android.features.onboarding

/** How a step can be passed without answering (#1510 D1). */
enum class OnboardingSkip { NONE, SKIP, LATER }

/** One step of the flow. The screen branches on [id] to pick its content; copy lives in `strings.xml`. */
data class OnboardingStep(
    val id: String,
    val skip: OnboardingSkip,
)

/**
 * The seven steps (#1510 D1), in order. A config so M69 can insert a reminders step after `pace`
 * without rework; `OnboardingStepsTest` pins count, ids, order and skips. `handoff` has no screen
 * of its own (D9): it is the `complete_onboarding` call and Home.
 */
object OnboardingSteps {
    val all: List<OnboardingStep> =
        listOf(
            OnboardingStep("promise", OnboardingSkip.NONE),
            OnboardingStep("consent", OnboardingSkip.NONE),
            OnboardingStep("reasons", OnboardingSkip.SKIP),
            OnboardingStep("you", OnboardingSkip.SKIP),
            OnboardingStep("people", OnboardingSkip.LATER),
            OnboardingStep("pace", OnboardingSkip.SKIP),
            OnboardingStep("handoff", OnboardingSkip.NONE),
        )

    /** The steps with a screen: every one but the hand-off. The progress row counts these. */
    val visible: List<OnboardingStep> = all.filter { it.id != "handoff" }
}
```

Delete `OnboardingImage` and the old step fields; `OnboardingScreen.kt` and `OnboardingPageView.kt` stop compiling until Part 4. To keep this part green on its own, replace `OnboardingScreen`'s body with a temporary shell that shows `stringResource(R.string.onboarding_promise_title)` and one `PebblesPrimaryButton(text = stringResource(R.string.onboarding_promise_cta), onClick = onFinish)`, keep its signature `OnboardingScreen(steps: List<OnboardingStep>, onFinish: () -> Unit, modifier)`, and delete `OnboardingPageView.kt`. `FunnelScreenshots.OnboardingScreenLight` keeps compiling; its reference PNGs change in Part 6 anyway.

- [ ] **Step 3: Strings** (insert as text at anchors, `project_formatting_sensitive_catalogs`)

Replace the eight `onboarding_*` strings at `values/strings.xml:63-73` with:

```xml
    <!-- Onboarding (#1510): the seven steps -->
    <string name="onboarding_back">Back</string>
    <string name="onboarding_skip">Skip</string>
    <string name="onboarding_later">Later</string>
    <string name="onboarding_continue">Continue</string>
    <string name="onboarding_progress">Step %1$d of %2$d</string>
    <string name="onboarding_promise_title">A promise, written once</string>
    <string name="onboarding_promise_cta">I\'ll hold you to it</string>
    <string name="onboarding_promise_unavailable">Our promise could not load. It will be here in Settings.</string>
    <string name="onboarding_consent_title">Your consent</string>
    <string name="onboarding_consent_body">Four acts, each its own switch. Nothing is pre-ticked.</string>
    <string name="onboarding_consent_health">Pebbles may keep what I feel (health data under the GDPR).</string>
    <string name="onboarding_consent_health_hint">Emotions and intensity are special-category data. This is the one switch that makes the app possible.</string>
    <string name="onboarding_consent_terms">I agree to the Terms of Service.</string>
    <string name="onboarding_consent_privacy">I have read the Privacy Policy.</string>
    <string name="onboarding_consent_age">I am 16 or older.</string>
    <string name="onboarding_consent_read_terms">Read the terms</string>
    <string name="onboarding_consent_read_policy">Read the policy</string>
    <string name="onboarding_consent_recorded">Already on record</string>
    <string name="onboarding_consent_error">Your consent could not be saved. Check your connection and try again.</string>
    <string name="onboarding_reasons_title">What brings you here?</string>
    <string name="onboarding_reasons_body">Pick any. It changes what Pebbles shows you first.</string>
    <string name="onboarding_reason_notice_feelings">Notice what I feel</string>
    <string name="onboarding_reason_keep_people">Keep the people</string>
    <string name="onboarding_reason_hard_season">Get through a hard season</string>
    <string name="onboarding_reason_good_days">Keep the good days</string>
    <string name="onboarding_reason_own_patterns">See my own patterns</string>
    <string name="onboarding_reason_keep_record">Keep a record for later</string>
    <string name="onboarding_you_title">This is you</string>
    <string name="onboarding_you_name_hint">Your name</string>
    <string name="onboarding_you_glyph_body">Pick a glyph, or draw your own.</string>
    <string name="onboarding_you_draw">Draw my own</string>
    <string name="onboarding_people_title">Three places</string>
    <string name="onboarding_people_body">Who would you call first with good news?</string>
    <string name="onboarding_people_add">Add one</string>
    <string name="onboarding_people_name_hint">Their name</string>
    <string name="onboarding_people_change_glyph">Change glyph</string>
    <string name="onboarding_people_remove">Remove %1$s</string>
    <string name="onboarding_people_glyph_sheet_title">A glyph for %1$s</string>
    <string name="onboarding_people_glyph_use">Use for %1$s</string>
    <string name="onboarding_people_glyph_draw">Draw one</string>
    <string name="onboarding_pace_title">What pace suits you?</string>
    <string name="onboarding_pace_body">We\'ll read this when reminders arrive. No streaks, no guilt.</string>
    <string name="onboarding_pace_daily">Every day</string>
    <string name="onboarding_pace_few_a_week">A few times a week</string>
    <string name="onboarding_pace_when_it_matters">Only when it matters</string>
    <string name="onboarding_pace_weekly_wrap">Wrapping the end of the week</string>
    <string name="onboarding_pace_footer">No streaks, no guilt.</string>
    <string name="onboarding_finish">Start</string>
    <string name="onboarding_complete_error">Couldn\'t finish. Check your connection and try again.</string>
    <string name="onboarding_handoff_prompt_notice_feelings">What did you feel today?</string>
    <string name="onboarding_handoff_prompt_keep_people">Who were you with today?</string>
    <string name="onboarding_handoff_prompt_hard_season">What was hard today?</string>
    <string name="onboarding_handoff_prompt_good_days">What was good today?</string>
    <string name="onboarding_handoff_prompt_own_patterns">What happened today?</string>
    <string name="onboarding_handoff_prompt_keep_record">What do you want to remember from today?</string>
    <string name="onboarding_handoff_prompt_default">What happened today?</string>
```

French, at `values-fr/strings.xml` replacing the matching block (anchor `onboarding_intro_title` … `onboarding_start`):

```xml
    <!-- Onboarding (#1510) : les sept étapes -->
    <string name="onboarding_back">Retour</string>
    <string name="onboarding_skip">Passer</string>
    <string name="onboarding_later">Plus tard</string>
    <string name="onboarding_continue">Continuer</string>
    <string name="onboarding_progress">Étape %1$d sur %2$d</string>
    <string name="onboarding_promise_title">Une promesse, écrite une fois</string>
    <string name="onboarding_promise_cta">Je t\'en tiendrai responsable</string>
    <string name="onboarding_promise_unavailable">Notre promesse n\'a pas pu se charger. Tu la retrouveras dans les réglages.</string>
    <string name="onboarding_consent_title">Ton consentement</string>
    <string name="onboarding_consent_body">Quatre actes, chacun son interrupteur. Rien n\'est pré-coché.</string>
    <string name="onboarding_consent_health">Pebbles peut garder ce que je ressens (données de santé au sens du RGPD).</string>
    <string name="onboarding_consent_health_hint">Les émotions et l\'intensité sont des données sensibles. C\'est l\'interrupteur qui rend l\'app possible.</string>
    <string name="onboarding_consent_terms">J\'accepte les conditions d\'utilisation.</string>
    <string name="onboarding_consent_privacy">J\'ai lu la politique de confidentialité.</string>
    <string name="onboarding_consent_age">J\'ai 16 ans ou plus.</string>
    <string name="onboarding_consent_read_terms">Lire les conditions</string>
    <string name="onboarding_consent_read_policy">Lire la politique</string>
    <string name="onboarding_consent_recorded">Déjà enregistré</string>
    <string name="onboarding_consent_error">Ton consentement n\'a pas pu être enregistré. Vérifie ta connexion et réessaie.</string>
    <string name="onboarding_reasons_title">Qu\'est-ce qui t\'amène ?</string>
    <string name="onboarding_reasons_body">Choisis ce que tu veux. Ça change ce que Pebbles te montre en premier.</string>
    <string name="onboarding_reason_notice_feelings">Remarquer ce que je ressens</string>
    <string name="onboarding_reason_keep_people">Garder les gens</string>
    <string name="onboarding_reason_hard_season">Traverser une période difficile</string>
    <string name="onboarding_reason_good_days">Garder les bons jours</string>
    <string name="onboarding_reason_own_patterns">Voir mes propres schémas</string>
    <string name="onboarding_reason_keep_record">Garder une trace pour plus tard</string>
    <string name="onboarding_you_title">C\'est toi</string>
    <string name="onboarding_you_name_hint">Ton prénom</string>
    <string name="onboarding_you_glyph_body">Choisis un glyphe, ou dessine le tien.</string>
    <string name="onboarding_you_draw">Dessiner le mien</string>
    <string name="onboarding_people_title">Trois places</string>
    <string name="onboarding_people_body">À qui annoncerais-tu une bonne nouvelle en premier ?</string>
    <string name="onboarding_people_add">Ajouter</string>
    <string name="onboarding_people_name_hint">Son prénom</string>
    <string name="onboarding_people_change_glyph">Changer le glyphe</string>
    <string name="onboarding_people_remove">Retirer %1$s</string>
    <string name="onboarding_people_glyph_sheet_title">Un glyphe pour %1$s</string>
    <string name="onboarding_people_glyph_use">Utiliser pour %1$s</string>
    <string name="onboarding_people_glyph_draw">En dessiner un</string>
    <string name="onboarding_pace_title">Quel rythme te va ?</string>
    <string name="onboarding_pace_body">On le lira quand les rappels arriveront. Pas de série, pas de culpabilité.</string>
    <string name="onboarding_pace_daily">Tous les jours</string>
    <string name="onboarding_pace_few_a_week">Quelques fois par semaine</string>
    <string name="onboarding_pace_when_it_matters">Seulement quand ça compte</string>
    <string name="onboarding_pace_weekly_wrap">En bouclant la fin de semaine</string>
    <string name="onboarding_pace_footer">Pas de série, pas de culpabilité.</string>
    <string name="onboarding_finish">Commencer</string>
    <string name="onboarding_complete_error">Impossible de terminer. Vérifie ta connexion et réessaie.</string>
    <string name="onboarding_handoff_prompt_notice_feelings">Qu\'as-tu ressenti aujourd\'hui ?</string>
    <string name="onboarding_handoff_prompt_keep_people">Avec qui étais-tu aujourd\'hui ?</string>
    <string name="onboarding_handoff_prompt_hard_season">Qu\'est-ce qui a été dur aujourd\'hui ?</string>
    <string name="onboarding_handoff_prompt_good_days">Qu\'est-ce qui a été bien aujourd\'hui ?</string>
    <string name="onboarding_handoff_prompt_own_patterns">Que s\'est-il passé aujourd\'hui ?</string>
    <string name="onboarding_handoff_prompt_keep_record">Que veux-tu retenir d\'aujourd\'hui ?</string>
    <string name="onboarding_handoff_prompt_default">Que s\'est-il passé aujourd\'hui ?</string>
```

`onboarding_close` and `onboarding_start` go away; grep for other users first (`grep -rn "onboarding_close\|onboarding_start\|ONBOARDING_SKIP" apps/android/app/src`) and update `RootGateTest` to `onboarding_promise_cta`.

- [ ] **Step 4: Run everything in the part, lint**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug ktlintCheck
```

Expected: green (`lintDebug` may flag the unused new strings until Part 4; an `UnusedResources` warning is not a failure under this repo's lint config, check the report before suppressing anything).

- [ ] **Step 5: Commit and submit**

```bash
git add apps/android
git commit -m "feat(android): seven-step onboarding config and its copy"
gh stack submit --auto
gh pr edit <n> --title "feat(android): onboarding data layer and the completed gate" --add-label feat --add-label android --add-label core --add-label no-lab-note --milestone "M68 · V1 launch"
```

PR body: `Part of #AN`.

---

# Part 4 — the shell, promise, consent, reasons

### Task 4.1: UI state and ViewModel

**Files:**
- Create: `features/onboarding/OnboardingUiState.kt`
- Create: `features/onboarding/OnboardingViewModel.kt`
- Test: `app/src/test/kotlin/app/pbbls/android/features/onboarding/OnboardingViewModelTest.kt`

- [ ] **Step 1: Branch**

```bash
gh stack add feat/AN-onboarding-shell-android
```

- [ ] **Step 2: The failing tests** (the ViewModel test shape of `ConsentGateViewModelTest`; read it for the dispatcher rule and the fake wiring)

```kotlin
package app.pbbls.android.features.onboarding

import app.pbbls.android.core.data.OnboardingHandoff
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.core.model.OnboardingPace
import app.pbbls.android.core.model.OnboardingReason
import app.pbbls.android.features.consent.ConsentGateLogic
import app.pbbls.android.testing.FakeConsentService
import app.pbbls.android.testing.FakeOnboardingService
import app.pbbls.android.testing.InMemoryPrefs
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class OnboardingViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val service = FakeOnboardingService()
    private val consents = FakeConsentService(rows = mutableListOf()) // nothing on record: every act is asked
    private val handoff = OnboardingHandoff()

    private fun vm() = OnboardingViewModel(service, consents, consentCache(), handoff, userId = "user-1")

    @Test
    fun `starts on the promise with the letter`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            assertEquals("promise", vm.uiState.value.step.id)
            assertEquals("There will never be a feed here.", vm.uiState.value.letter?.bodyEn)
            assertEquals(8, vm.uiState.value.systemGlyphs.size)
        }

    @Test
    fun `a letter that fails to load still lets the flow start`() =
        runTest {
            service.letterFailure = IOException("offline")
            val vm = vm()
            advanceUntilIdle()
            assertNull(vm.uiState.value.letter)
            assertTrue(vm.uiState.value.letterUnavailable)
        }

    @Test
    fun `consent asks only the missing acts and records each as it is switched on`() =
        runTest {
            consents.rows.add(FakeConsentService.active(ConsentKind.TERMS, ConsentGateLogic.versionFor(ConsentKind.TERMS)))
            val vm = vm()
            advanceUntilIdle()
            vm.next() // promise → consent
            advanceUntilIdle()
            val consent = vm.uiState.value.consent
            assertEquals(setOf(ConsentKind.TERMS), consent.recorded)
            assertFalse(consent.canContinue)
            vm.onConsentToggle(ConsentKind.HEALTH_DATA, true)
            advanceUntilIdle()
            assertEquals(listOf(Triple(ConsentKind.HEALTH_DATA, ConsentGateLogic.versionFor(ConsentKind.HEALTH_DATA), ConsentGateLogic.SOURCE)), consents.recordCalls)
            vm.onConsentToggle(ConsentKind.PRIVACY, true)
            vm.onConsentToggle(ConsentKind.AGE_ASSURANCE, true)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.consent.canContinue)
        }

    @Test
    fun `a failed record keeps the switch off with an error`() =
        runTest {
            consents.recordFailure = IOException("offline")
            val vm = vm()
            advanceUntilIdle()
            vm.next()
            vm.onConsentToggle(ConsentKind.HEALTH_DATA, true)
            advanceUntilIdle()
            assertFalse(ConsentKind.HEALTH_DATA in vm.uiState.value.consent.on)
            assertEquals(app.pbbls.android.R.string.onboarding_consent_error, vm.uiState.value.consent.errorRes)
        }

    @Test
    fun `skip leaves the answer null and back returns`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.next()
            ConsentKind.entries.forEach { vm.onConsentToggle(it, true) }
            advanceUntilIdle()
            vm.next() // consent → reasons
            vm.onReasonToggle(OnboardingReason.KEEP_PEOPLE)
            vm.skip() // reasons → you, reasons dropped
            assertEquals("you", vm.uiState.value.step.id)
            assertNull(vm.uiState.value.answers.reasons)
            vm.back()
            assertEquals("reasons", vm.uiState.value.step.id)
            assertTrue(vm.uiState.value.reasons.isEmpty())
        }

    @Test
    fun `back from the promise does nothing and consent cannot be skipped`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.back()
            assertEquals("promise", vm.uiState.value.step.id)
            vm.next()
            vm.skip()
            assertEquals("consent", vm.uiState.value.step.id)
        }

    @Test
    fun `completing sends every answer once and leaves the hand-off note`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.next()
            ConsentKind.entries.forEach { vm.onConsentToggle(it, true) }
            advanceUntilIdle()
            vm.next()
            vm.onReasonToggle(OnboardingReason.GOOD_DAYS)
            vm.onReasonToggle(OnboardingReason.KEEP_PEOPLE)
            vm.next()
            vm.onNameChange("Maya")
            vm.onGlyphPick("sys-2")
            vm.next()
            vm.onPersonAdd()
            vm.onPersonNameChange(0, "Noé")
            vm.onPersonCommit(0)
            vm.next()
            vm.onPaceSelect(OnboardingPace.DAILY)
            vm.next() // pace → handoff: complete
            advanceUntilIdle()
            val sent = service.completeCalls.single()
            assertEquals("Maya", sent.displayName)
            assertEquals("sys-2", sent.glyphId)
            assertEquals(listOf(OnboardingReason.GOOD_DAYS, OnboardingReason.KEEP_PEOPLE), sent.reasons)
            assertEquals(OnboardingPace.DAILY, sent.pace)
            assertEquals("Noé", sent.souls.single().name)
            assertEquals("sys-0", sent.souls.single().glyphId) // the first system glyph when none was picked
            assertEquals(OnboardingReason.GOOD_DAYS, handoff.take()?.reason)
            assertTrue(vm.uiState.value.isDone)
        }

    @Test
    fun `a refused completion shows the error and stays on pace`() =
        runTest {
            service.completeFailure = IOException("offline")
            val vm = vm()
            advanceUntilIdle()
            vm.next()
            ConsentKind.entries.forEach { vm.onConsentToggle(it, true) }
            advanceUntilIdle()
            repeat(4) { vm.skip() } // reasons, you, people (Later), pace
            advanceUntilIdle()
            assertEquals("pace", vm.uiState.value.step.id)
            assertEquals(app.pbbls.android.R.string.onboarding_complete_error, vm.uiState.value.errorRes)
            assertFalse(vm.uiState.value.isDone)
        }
}
```

`consentCache()` builds a `ConsentPreferences` over `InMemoryPrefs` the way `ConsentGateViewModelTest` does; copy that helper. `FakeConsentService.active(kind, version)` is whatever factory the fake exposes for a row; read it and adapt the call.

- [ ] **Step 3: Run, see them fail to compile; then the state**

`OnboardingUiState.kt`:

```kotlin
package app.pbbls.android.features.onboarding

import androidx.annotation.StringRes
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.OnboardingAnswers
import app.pbbls.android.core.model.OnboardingPace
import app.pbbls.android.core.model.OnboardingReason
import app.pbbls.android.core.model.PromiseLetter

/** Step 2's four switches (#1510 D3). [recorded] acts are on and locked; [on] are the ones switched on here. */
data class OnboardingConsentState(
    val recorded: Set<ConsentKind> = emptySet(),
    val on: Set<ConsentKind> = emptySet(),
    val saving: Set<ConsentKind> = emptySet(),
    @StringRes val errorRes: Int? = null,
) {
    val canContinue: Boolean get() = saving.isEmpty() && (recorded + on).containsAll(ConsentKind.entries)
}

/** One piece of step 5's stone: a named person, or the empty place being typed into. */
data class OnboardingPerson(
    val name: String = "",
    /** The picked glyph, or null until a pick: the first system glyph is used at completion. */
    val glyphId: String? = null,
    /** True while the name field is open under the piece (S3). */
    val editing: Boolean = true,
)

/** The onboarding flow's whole state: the current step, every answer, and the satellites' state. */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingSteps.all.first(),
    val letter: PromiseLetter? = null,
    val letterUnavailable: Boolean = false,
    val systemGlyphs: List<Glyph> = emptyList(),
    val consent: OnboardingConsentState = OnboardingConsentState(),
    val reasons: Set<OnboardingReason> = emptySet(),
    val name: String = "",
    val glyphId: String? = null,
    /** Glyphs carved in the flow (S1), shown on the stone beside the system ones. */
    val carved: List<Glyph> = emptyList(),
    val people: List<OnboardingPerson> = emptyList(),
    /** Which person's glyph sheet is open (S2), or null. */
    val glyphSheetFor: Int? = null,
    /** The carve studio is open (S1), for the profile (-1) or a person's index. */
    val carvingFor: Int? = null,
    val pace: OnboardingPace? = null,
    val isCompleting: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val isDone: Boolean = false,
) {
    val stepIndex: Int get() = OnboardingSteps.visible.indexOf(step)

    /** The answers as they would be sent now; null where a step was skipped or left blank. */
    val answers: OnboardingAnswers
        get() =
            OnboardingAnswers(
                displayName = name.trim().takeIf { it.isNotEmpty() },
                glyphId = glyphId,
                reasons = reasons.takeIf { it.isNotEmpty() }?.sortedBy { it.ordinal },
                pace = pace,
                souls =
                    people
                        .filter { !it.editing && it.name.isNotBlank() }
                        .map { app.pbbls.android.core.model.OnboardingSoulDraft(it.name.trim(), it.glyphId ?: systemGlyphs.firstOrNull()?.id ?: "") },
            )
}
```

- [ ] **Step 4: The ViewModel**

```kotlin
package app.pbbls.android.features.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.ConsentPreferences
import app.pbbls.android.core.data.ConsentServicing
import app.pbbls.android.core.data.OnboardingHandoff
import app.pbbls.android.core.data.OnboardingHandoffNote
import app.pbbls.android.core.data.OnboardingServicing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.OnboardingPace
import app.pbbls.android.core.model.OnboardingReason
import app.pbbls.android.features.consent.ConsentGateLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "onboarding"

/**
 * The seven-step flow (#1510), one ViewModel scoped to the `Onboarding` entry. Answers live here
 * until the hand-off sends them in one `complete_onboarding` (D5); consent acts are the exception,
 * recorded as each switch turns on (D3), so a user who quits at step 3 still has a ledger.
 * Quitting the entry loses the typed answers: the flow restarts next launch (D10).
 */
@HiltViewModel
class OnboardingViewModel internal constructor(
    private val service: OnboardingServicing,
    private val consents: ConsentServicing,
    private val consentCache: ConsentPreferences,
    private val handoff: OnboardingHandoff,
    private val userId: String?,
) : ViewModel() {
    @Inject
    constructor(
        service: OnboardingServicing,
        consents: ConsentServicing,
        consentCache: ConsentPreferences,
        handoff: OnboardingHandoff,
        supabase: SupabaseServicing,
    ) : this(service, consents, consentCache, handoff, supabase.session?.user?.id)

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatchingCancellable { service.promiseLetter() }
                .onSuccess { letter -> _uiState.update { it.copy(letter = letter, letterUnavailable = letter == null) } }
                .onFailure { e ->
                    Log.e(TAG, "promise letter failed", e)
                    _uiState.update { it.copy(letterUnavailable = true) }
                }
        }
        viewModelScope.launch {
            runCatchingCancellable { service.systemGlyphs() }
                .onSuccess { glyphs -> _uiState.update { it.copy(systemGlyphs = glyphs) } }
                .onFailure { Log.e(TAG, "system glyphs failed", it) }
        }
        viewModelScope.launch {
            runCatchingCancellable { consents.active() }
                .onSuccess { active ->
                    val missing = ConsentGateLogic.missing(active).toSet()
                    _uiState.update { it.copy(consent = it.consent.copy(recorded = ConsentKind.entries.toSet() - missing)) }
                }.onFailure { Log.e(TAG, "consent read failed; every act will be asked", it) }
        }
    }

    // MARK: - Stepping

    fun next() {
        val state = _uiState.value
        when (state.step.id) {
            "consent" -> if (!state.consent.canContinue) return
            "pace" -> {
                complete()
                return
            }
        }
        move(+1)
    }

    fun skip() {
        val state = _uiState.value
        when (state.step.skip) {
            OnboardingSkip.NONE -> return
            OnboardingSkip.SKIP, OnboardingSkip.LATER -> Unit
        }
        _uiState.update {
            when (state.step.id) {
                "reasons" -> it.copy(reasons = emptySet())
                "you" -> it.copy(name = "", glyphId = null)
                "people" -> it.copy(people = emptyList())
                "pace" -> it.copy(pace = null)
                else -> it
            }
        }
        if (state.step.id == "pace") complete() else move(+1)
    }

    fun back() {
        if (_uiState.value.stepIndex <= 0) return
        move(-1)
    }

    private fun move(delta: Int) {
        _uiState.update { state ->
            val steps = OnboardingSteps.visible
            val index = (state.stepIndex + delta).coerceIn(0, steps.lastIndex)
            state.copy(step = steps[index], errorRes = null, glyphSheetFor = null, carvingFor = null)
        }
    }

    // MARK: - Step 2

    fun onConsentToggle(
        kind: ConsentKind,
        checked: Boolean,
    ) {
        val state = _uiState.value.consent
        if (kind in state.recorded || kind in state.saving) return
        if (!checked) {
            _uiState.update { it.copy(consent = it.consent.copy(on = it.consent.on - kind, errorRes = null)) }
            return
        }
        _uiState.update { it.copy(consent = it.consent.copy(saving = it.consent.saving + kind, errorRes = null)) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatchingCancellable { consents.record(kind, ConsentGateLogic.versionFor(kind), ConsentGateLogic.SOURCE) }
                    .onSuccess {
                        _uiState.update { it.copy(consent = it.consent.copy(on = it.consent.on + kind, saving = it.consent.saving - kind)) }
                        markGateSatisfiedIfComplete()
                    }.onFailure { e ->
                        Log.e(TAG, "record consent failed", e)
                        _uiState.update { it.copy(consent = it.consent.copy(saving = it.consent.saving - kind, errorRes = R.string.onboarding_consent_error)) }
                    }
            }
        }
    }

    /** Every act on record: the gate's cache says so, so `restart` after the flow never re-asks. */
    private fun markGateSatisfiedIfComplete() {
        val uid = userId ?: return
        val consent = _uiState.value.consent
        if ((consent.recorded + consent.on).containsAll(ConsentKind.entries)) consentCache.markSatisfied(uid, ConsentGateLogic.fingerprint())
    }

    // MARK: - Step 3

    fun onReasonToggle(reason: OnboardingReason) =
        _uiState.update { it.copy(reasons = if (reason in it.reasons) it.reasons - reason else it.reasons + reason) }

    // MARK: - Step 4

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value.take(NAME_LIMIT)) }

    fun onGlyphPick(glyphId: String) = _uiState.update { it.copy(glyphId = glyphId) }

    fun onDrawForYou() = _uiState.update { it.copy(carvingFor = -1) }

    // MARK: - Step 5

    fun onPersonAdd() =
        _uiState.update {
            if (it.people.size >= 3 || it.people.any { p -> p.editing }) it else it.copy(people = it.people + OnboardingPerson())
        }

    fun onPersonNameChange(
        index: Int,
        value: String,
    ) = _uiState.update { it.copy(people = it.people.mapIndexed { i, p -> if (i == index) p.copy(name = value.take(NAME_LIMIT)) else p }) }

    /** The keyboard's done: a blank name removes the place, a name closes the field. */
    fun onPersonCommit(index: Int) =
        _uiState.update {
            val person = it.people.getOrNull(index) ?: return@update it
            if (person.name.isBlank()) {
                it.copy(people = it.people.filterIndexed { i, _ -> i != index })
            } else {
                it.copy(people = it.people.mapIndexed { i, p -> if (i == index) p.copy(name = p.name.trim(), editing = false) else p })
            }
        }

    fun onPersonRemove(index: Int) = _uiState.update { it.copy(people = it.people.filterIndexed { i, _ -> i != index }) }

    fun onPersonGlyphSheet(index: Int?) = _uiState.update { it.copy(glyphSheetFor = index) }

    fun onPersonGlyphPick(
        index: Int,
        glyphId: String,
    ) = _uiState.update {
        it.copy(people = it.people.mapIndexed { i, p -> if (i == index) p.copy(glyphId = glyphId) else p }, glyphSheetFor = null)
    }

    fun onDrawForPerson(index: Int) = _uiState.update { it.copy(carvingFor = index, glyphSheetFor = null) }

    // MARK: - S1, the carve studio

    fun onCarved(glyph: Glyph) =
        _uiState.update { state ->
            val target = state.carvingFor ?: return@update state
            val withGlyph = state.copy(carved = state.carved + glyph, carvingFor = null)
            if (target < 0) {
                withGlyph.copy(glyphId = glyph.id)
            } else {
                withGlyph.copy(people = withGlyph.people.mapIndexed { i, p -> if (i == target) p.copy(glyphId = glyph.id) else p })
            }
        }

    fun onCarveCancelled() = _uiState.update { it.copy(carvingFor = null) }

    // MARK: - Step 6 and the hand-off

    fun onPaceSelect(pace: OnboardingPace) = _uiState.update { it.copy(pace = pace) }

    private fun complete() {
        val state = _uiState.value
        if (state.isCompleting || state.isDone) return
        _uiState.update { it.copy(isCompleting = true, errorRes = null) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatchingCancellable { service.complete(state.answers) }
                    .onSuccess {
                        handoff.leave(
                            OnboardingHandoffNote(
                                soulId = null, // the souls' ids are the server's; Home attaches by name (Task 6.2)
                                reason = state.answers.reasons?.firstOrNull(),
                            ),
                        )
                        _uiState.update { it.copy(isCompleting = false, isDone = true) }
                    }.onFailure { e ->
                        Log.e(TAG, "complete_onboarding failed", e)
                        _uiState.update { it.copy(isCompleting = false, errorRes = R.string.onboarding_complete_error) }
                    }
            }
        }
    }

    companion object {
        /** Settings' display-name limit; a person's name shares it. */
        const val NAME_LIMIT = 40
    }
}
```

`OnboardingHandoffNote.soulId` stays null here; Task 6.2 replaces it with the first person's name (`firstPersonName: String?`) and Home resolves the soul by name after its reference lists reload. Change the note's field now to `firstPersonName: String?` and set it to `state.answers.souls.firstOrNull()?.name`; update `OnboardingHandoff.kt` accordingly and the test's `handoff.take()?.reason` assertion stays valid.

- [ ] **Step 5: Run the tests**

```bash
./gradlew :app:testDebugUnitTest --tests "app.pbbls.android.features.onboarding.OnboardingViewModelTest"
```

Expected: 8 tests pass.

- [ ] **Step 6: Commit**

```bash
git add apps/android
git commit -m "feat(android): onboarding view model stepping the seven steps and recording consent as it happens"
```

### Task 4.2: The shell and the progress stones

**Files:**
- Create: `features/onboarding/steps/ProgressStones.kt`
- Rewrite: `features/onboarding/OnboardingScreen.kt`
- Modify: `navigation/PebblesEntryProvider.kt:369-371`
- Modify: `core/common/JourneyTags.kt:17`

- [ ] **Step 1: The progress row** (the icon set's `pebble-round`: passed and current lit, ahead outlined; current larger)

```kotlin
package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage

/**
 * The seven stones in the top bar (#1510 §1): passed stones lit, the current one lit and larger,
 * the ones ahead outlined. One accessible node reading "Step n of m".
 */
@Composable
fun ProgressStones(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.onboarding_progress, current + 1, count)
    Row(
        modifier = modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val lit = index <= current
            PbblsIconImage(
                icon = if (lit) PbblsIcon.PEBBLE_ROUND_LIT_1 else PbblsIcon.PEBBLE_ROUND,
                contentDescription = null,
                tint = if (lit) colors.primary else colors.outline,
                modifier = Modifier.size(if (index == current) 18.dp else 12.dp),
            )
        }
    }
}
```

Check `PbblsIconImage`'s parameter order at `core/designsystem/PbblsIcons.kt:718` (`icon, contentDescription, modifier, size?, tint`) and match it.

- [ ] **Step 2: The shell**

```kotlin
package app.pbbls.android.features.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.features.onboarding.steps.ConsentStep
import app.pbbls.android.features.onboarding.steps.ProgressStones
import app.pbbls.android.features.onboarding.steps.PromiseStep
import app.pbbls.android.features.onboarding.steps.ReasonsStep

/**
 * The onboarding entry (#1510): a top bar with Back, the progress stones and Skip or Later, and the
 * current step below. System back steps back inside the flow and is consumed on the first step: the
 * flow is left only by completing it (D1, D10). Pushed over Home by `RootScreen`; [onFinish] pops it.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDone) { if (state.isDone) onFinish() }
    if (!LocalInspectionMode.current) {
        val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(state = backState) { viewModel.back() }
    }
    OnboardingContent(
        state = state,
        onBack = viewModel::back,
        onSkip = viewModel::skip,
        onNext = viewModel::next,
        onConsentToggle = viewModel::onConsentToggle,
        onReasonToggle = viewModel::onReasonToggle,
        modifier = modifier,
    )
}

/** Stateless, what the screenshots render. */
@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onConsentToggle: (app.pbbls.android.core.model.ConsentKind, Boolean) -> Unit,
    onReasonToggle: (app.pbbls.android.core.model.OnboardingReason) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().readableWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, enabled = state.stepIndex > 0, modifier = Modifier.testTag(JourneyTags.ONBOARDING_BACK)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.onboarding_back))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ProgressStones(count = OnboardingSteps.visible.size, current = state.stepIndex)
                }
                when (state.step.skip) {
                    OnboardingSkip.NONE -> Box(Modifier.padding(horizontal = 24.dp))
                    OnboardingSkip.SKIP, OnboardingSkip.LATER ->
                        TextButton(onClick = onSkip, modifier = Modifier.testTag(JourneyTags.ONBOARDING_SKIP)) {
                            Text(stringResource(if (state.step.skip == OnboardingSkip.SKIP) R.string.onboarding_skip else R.string.onboarding_later))
                        }
                }
            }
            AnimatedContent(
                targetState = state.step.id,
                transitionSpec = {
                    val forward = OnboardingSteps.visible.indexOfFirst { it.id == targetState } >= OnboardingSteps.visible.indexOfFirst { it.id == initialState }
                    val sign = if (forward) 1 else -1
                    (slideInHorizontally { sign * it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -sign * it / 4 } + fadeOut())
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "onboarding-step",
            ) { id ->
                when (id) {
                    "promise" -> PromiseStep(state.letter, state.letterUnavailable, onNext)
                    "consent" -> ConsentStep(state.consent, onConsentToggle, onNext)
                    "reasons" -> ReasonsStep(state.reasons, onReasonToggle, onNext)
                    else -> Box(Modifier.fillMaxSize()) // steps 4–6 land in parts 5 and 6
                }
            }
        }
    }
}
```

`JourneyTags`: add `const val ONBOARDING_BACK = "onboarding_back"`. `PebblesEntryProvider.kt:369-371` becomes `OnboardingScreen(onFinish = onOnboardingFinished)`.

- [ ] **Step 3: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: fails only on the three missing step composables; write them in the next task.

### Task 4.3: Promise, consent and reasons steps

**Files:**
- Create: `features/onboarding/steps/PromiseStep.kt`, `ConsentStep.kt`, `ReasonsStep.kt`

- [ ] **Step 1: Promise** (the letter, the glyph as signature, one button)

```kotlin
package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.PromiseLetter

/** Step 1 (#1510 D2): the letter as paragraphs, the round pebble as its signature, one button. */
@Composable
fun PromiseStep(
    letter: PromiseLetter?,
    unavailable: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val french = LocalConfiguration.current.locales[0].language == "fr"
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            stringResource(R.string.onboarding_promise_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        when {
            letter != null ->
                letter.body(french).split("\n\n").filter { it.isNotBlank() }.forEach { paragraph ->
                    Text(paragraph.trim(), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                }
            unavailable ->
                Text(stringResource(R.string.onboarding_promise_unavailable), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        }
        if (letter != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PbblsIconImage(PbblsIcon.PEBBLE_ROUND_LIT_1, PbblsIconSize.TILE, contentDescription = null, tint = colors.primary)
                Text(letter.signedBy(french), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1f).height(Spacing.lg))
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_promise_cta),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}
```

Add `const val ONBOARDING_NEXT = "onboarding_next"` to `JourneyTags`. The letter is Markdown by column name but shown as plain paragraphs: the seeded letter uses none of the syntax, and the admin form says "one paragraph per blank line".

- [ ] **Step 2: Consent** (M3 switches, one per act, recorded acts on and locked, chips to the documents)

```kotlin
package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.LegalDoc
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.designsystem.openLegalDoc
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.features.onboarding.OnboardingConsentState

/**
 * Step 2 (#1510 D3): four switches, one per act, in the gate's order; an act already on record is on
 * and locked ("Already on record"), the rest are off until the user flips them, and Continue waits
 * for all four. Each flip records through `record_consent` before the switch settles.
 */
@Composable
fun ConsentStep(
    state: OnboardingConsentState,
    onToggle: (ConsentKind, Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(stringResource(R.string.onboarding_consent_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.onboarding_consent_body), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ConsentSwitchRow(ConsentKind.HEALTH_DATA, R.string.onboarding_consent_health, R.string.onboarding_consent_health_hint, state, onToggle)
            ConsentSwitchRow(ConsentKind.TERMS, R.string.onboarding_consent_terms, null, state, onToggle)
            ConsentSwitchRow(ConsentKind.PRIVACY, R.string.onboarding_consent_privacy, null, state, onToggle)
            ConsentSwitchRow(ConsentKind.AGE_ASSURANCE, R.string.onboarding_consent_age, null, state, onToggle)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AssistChip(onClick = { openLegalDoc(context, LegalDoc.TERMS) }, label = { Text(stringResource(R.string.onboarding_consent_read_terms)) })
            AssistChip(onClick = { openLegalDoc(context, LegalDoc.PRIVACY) }, label = { Text(stringResource(R.string.onboarding_consent_read_policy)) })
        }
        state.errorRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = colors.error) }
        Spacer(Modifier.weight(1f).height(Spacing.lg))
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = onContinue,
            enabled = state.canContinue,
            modifier = Modifier.fillMaxWidth().testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}

@Composable
private fun ConsentSwitchRow(
    kind: ConsentKind,
    labelRes: Int,
    hintRes: Int?,
    state: OnboardingConsentState,
    onToggle: (ConsentKind, Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val recorded = kind in state.recorded
    val checked = recorded || kind in state.on
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
            val hint = if (recorded) stringResource(R.string.onboarding_consent_recorded) else hintRes?.let { stringResource(it) }
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(kind, it) },
            enabled = !recorded && kind !in state.saving,
            modifier = Modifier.testTag("onboarding_consent_${kind.wire}"),
        )
    }
}
```

- [ ] **Step 3: Reasons** (six equal tiles, multi-select, a lit stone in the corner when selected)

```kotlin
package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.OnboardingReason

/** Step 3 (#1510 D4): six equal tiles in two columns, multi-select; a selected tile is `primaryContainer` with a lit stone in its corner. */
@Composable
fun ReasonsStep(
    selected: Set<OnboardingReason>,
    onToggle: (OnboardingReason) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(stringResource(R.string.onboarding_reasons_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.onboarding_reasons_body), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        OnboardingReason.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                pair.forEach { reason ->
                    val on = reason in selected
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.15f)
                            .clip(MaterialTheme.shapes.large)
                            .background(if (on) colors.primaryContainer else colors.surfaceContainer)
                            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(reason) })
                            .padding(Spacing.lg)
                            .testTag("onboarding_reason_${reason.slug}"),
                    ) {
                        if (on) {
                            PbblsIconImage(
                                PbblsIcon.PEBBLE_ROUND_LIT_1, PbblsIconSize.SMALL, contentDescription = null,
                                tint = colors.primary, modifier = Modifier.align(Alignment.TopEnd),
                            )
                        }
                        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            PbblsIconImage(reason.icon, PbblsIconSize.TILE, contentDescription = null, tint = if (on) colors.onPrimaryContainer else colors.onSurface)
                            Text(stringResource(reason.labelRes), style = MaterialTheme.typography.titleSmall, color = if (on) colors.onPrimaryContainer else colors.onSurface)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f).height(Spacing.lg))
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = onContinue,
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}
```

`PbblsIconSize` names: read `core/designsystem/PbblsIconSize.kt` and use the two nearest to 20 dp and 28 dp.

- [ ] **Step 4: Build, lint**

```bash
./gradlew :app:compileDebugKotlin :app:lintDebug ktlintCheck
```

Expected: green.

- [ ] **Step 5: Commit**

```bash
git add apps/android
git commit -m "feat(android): onboarding shell with progress stones, promise, consent and reasons steps"
```

### Task 4.4: The walk-through UI test

**Files:**
- Modify: `app/src/test/kotlin/app/pbbls/android/ui/RootGateTest.kt`

- [ ] **Step 1: Replace `a first sign-in shows onboarding over Home`**

```kotlin
    @Test
    fun `a first sign-in walks the onboarding over Home and lands in Home`() {
        profile.profile = profile.profile.copy(onboardingCompleted = false)
        launch()
        signIn()

        // 1. promise
        onText(R.string.onboarding_promise_title).assertIsDisplayed()
        compose.onNodeWithTag(JourneyTags.ONBOARDING_NEXT).performClick()
        // 2. consent: the fake ledger is satisfied by default, so every switch is on and locked
        onText(R.string.onboarding_consent_title).assertIsDisplayed()
        compose.onNodeWithTag(JourneyTags.ONBOARDING_NEXT).performScrollTo().performClick()
        // 3. reasons: one tile, then Continue
        compose.onNodeWithTag("onboarding_reason_keep_people").performClick()
        compose.onNodeWithTag(JourneyTags.ONBOARDING_NEXT).performScrollTo().performClick()
        // 4–6: skipped, Later, skipped (the skip on pace completes)
        compose.onNodeWithTag(JourneyTags.ONBOARDING_SKIP).performClick()
        compose.onNodeWithTag(JourneyTags.ONBOARDING_SKIP).performClick()
        compose.onNodeWithTag(JourneyTags.ONBOARDING_SKIP).performClick()

        compose.onNodeWithTag(JourneyTags.HOME).assertIsDisplayed()
        assertEquals(listOf(OnboardingReason.KEEP_PEOPLE), onboardingService.completeCalls.single().reasons)
    }
```

Inject `@Inject lateinit var onboardingService: FakeOnboardingService`. Steps 4 and 5 render an empty box until Part 5; the Skip and Later buttons are in the shell, so the test passes now and stays valid after.

- [ ] **Step 2: Run**

```bash
./gradlew :app:testDebugUnitTest --tests "app.pbbls.android.ui.RootGateTest"
```

Expected: green. If `a first sign-in` fails on the consent step because `FakeConsentService`'s default rows carry older versions than `LegalVersions`, use `ConsentGateLogic.versionFor` when building them (the fake's `satisfied()` factory should already).

- [ ] **Step 3: Commit and submit**

```bash
git add apps/android
git commit -m "test(android): the onboarding walk-through from a first sign-in to Home"
./gradlew :app:testDebugUnitTest :app:lintDebug ktlintCheck
gh stack submit --auto
gh pr edit <n> --title "feat(android): onboarding shell, promise, consent and reasons steps" --add-label feat --add-label android --add-label ui --milestone "M68 · V1 launch"
```

PR body: `Part of #AN`, plus the Lab Note or `no-lab-note` (see the stack notes).

---

# Part 5 — the split-stone grid, You and Three places

### Task 5.1: `StoneGrid`

**Files:**
- Create: `core/designsystem/stone/StoneGrid.kt`
- Test: `app/src/test/kotlin/app/pbbls/android/core/designsystem/stone/StoneGridTest.kt`

The spec's "one split stone cut 3×3" has no 3×3 geometry on any surface yet; three `SplitStone` rows stacked with the row gutter, seeded `<seed>#0..2`, is the Android reading (iOS mirrors it when it builds #1255's successor). The folded keyboard-up state is one `SplitStone(n = 4)`.

- [ ] **Step 1: Branch and the failing test**

```bash
gh stack add feat/AN-onboarding-stones-android
```

```kotlin
package app.pbbls.android.core.designsystem.stone

import org.junit.Assert.assertEquals
import org.junit.Test

/** The grid's seeds are stable and distinct per row, so the stone is the same on every launch. */
class StoneGridTest {
    @Test
    fun rowSeedsAreStableAndDistinct() {
        val seeds = StoneGrid.rowSeeds("onboarding-glyphs", rows = 3)
        assertEquals(listOf("onboarding-glyphs#0", "onboarding-glyphs#1", "onboarding-glyphs#2"), seeds)
    }

    @Test
    fun cellsAreNumberedRowMajor() {
        assertEquals(7, StoneGrid.cellIndex(row = 2, column = 1, columns = 3))
    }
}
```

- [ ] **Step 2: The composable**

```kotlin
package app.pbbls.android.core.designsystem.stone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A rock cut into `rows × columns` pieces (#1510 D7): one [SplitStone] per row, seeded `<seed>#row`,
 * stacked [StoneOptions.gutter] apart, so the pieces read as one stone cut both ways. Cell `k` is
 * row-major ([cellIndex]); `fills[k]` is its colour; [content] draws into it with its piece's clip.
 */
object StoneGrid {
    fun rowSeeds(
        seed: String,
        rows: Int,
    ): List<String> = List(rows) { "$seed#$it" }

    fun cellIndex(
        row: Int,
        column: Int,
        columns: Int,
    ): Int = row * columns + column
}

@Composable
fun StoneGridLayout(
    rows: Int,
    columns: Int,
    seed: String,
    fills: List<Color>,
    modifier: Modifier = Modifier,
    options: StoneOptions = StoneOptions(),
    minCellHeight: Dp = 88.dp,
    content: @Composable SplitStoneScope.(index: Int) -> Unit,
) {
    require(fills.size >= rows * columns) { "StoneGrid needs one fill per cell" }
    val seeds = StoneGrid.rowSeeds(seed, rows)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(options.gutter.dp)) {
        for (row in 0 until rows) {
            SplitStone(
                n = columns,
                seed = seeds[row],
                fills = fills.subList(row * columns, (row + 1) * columns),
                options = options,
                minHeight = minCellHeight,
            ) { column -> content(StoneGrid.cellIndex(row, column, columns)) }
        }
    }
}
```

- [ ] **Step 3: Run the test, commit**

```bash
./gradlew :app:testDebugUnitTest --tests "app.pbbls.android.core.designsystem.stone.StoneGridTest"
git add apps/android && git commit -m "feat(android): stone grid, one rock cut rows by columns"
```

### Task 5.2: `GlyphStoneGrid`, the starter grid both steps share

**Files:**
- Create: `features/onboarding/steps/GlyphStoneGrid.kt`

- [ ] **Step 1: Write it**

```kotlin
package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.stone.StoneGridLayout
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase

/** The starter grid's seed, the same on every surface (D7). */
const val GLYPH_STONE_SEED = "onboarding-glyphs"

/**
 * The 3×3 split stone of glyphs (#1510 D7): eight glyphs (system ones first, then any carved in
 * the flow, the last eight) and a "Draw" piece marked by a dashed inset. The chosen piece is
 * `primaryContainer`, the rest `surfaceContainer`, no outlines. [folded] is the keyboard-up state
 * of step 4: one row of four, the chosen glyph, two others and Draw.
 */
@Composable
fun GlyphStoneGrid(
    glyphs: List<Glyph>,
    selectedId: String?,
    onSelect: (Glyph) -> Unit,
    onDraw: () -> Unit,
    modifier: Modifier = Modifier,
    folded: Boolean = false,
    drawLabelRes: Int = R.string.onboarding_you_draw,
) {
    val colors = MaterialTheme.colorScheme
    val shown: List<Glyph?> =
        if (folded) {
            val chosen = glyphs.firstOrNull { it.id == selectedId }
            val others = glyphs.filter { it.id != selectedId }.take(if (chosen == null) 3 else 2)
            (listOfNotNull(chosen) + others).take(3) + null
        } else {
            glyphs.takeLast(8).let { it + List(8 - it.size) { null } }.take(8) + null
        }
    val rows = if (folded) 1 else 3
    val columns = if (folded) 4 else 3
    val fills =
        shown.map { glyph ->
            if (glyph != null && glyph.id == selectedId) colors.primaryContainer else colors.surfaceContainer
        }
    StoneGridLayout(rows = rows, columns = columns, seed = if (folded) "$GLYPH_STONE_SEED-folded" else GLYPH_STONE_SEED, fills = fills, modifier = modifier) { index ->
        val glyph = shown[index]
        val isDraw = index == shown.lastIndex
        Box(
            Modifier
                .fillMaxSize()
                .pieceClip(index)
                .selectable(
                    selected = glyph != null && glyph.id == selectedId,
                    role = Role.RadioButton,
                    enabled = isDraw || glyph != null,
                    onClick = { if (isDraw) onDraw() else glyph?.let(onSelect) },
                ).testTag(if (isDraw) "onboarding_glyph_draw" else "onboarding_glyph_${glyph?.id ?: "empty"}"),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isDraw ->
                    Column(
                        Modifier
                            .padding(10.dp)
                            .fillMaxSize()
                            .drawBehind {
                                drawRoundRect(
                                    color = colors.outline,
                                    cornerRadius = CornerRadius(16.dp.toPx()),
                                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                                )
                            }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        PbblsIconImage(PbblsIcon.PLUS_CIRCLE, PbblsIconSize.TILE, contentDescription = null, tint = colors.onSurfaceVariant)
                        Text(stringResource(drawLabelRes), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                glyph != null ->
                    GlyphView(
                        case = GlyphViewCase.GLYPH,
                        strokes = glyph.strokes,
                        viewBox = glyph.viewBox,
                        side = 56.dp,
                    )
            }
        }
    }
}
```

Read `core/ui/GlyphView.kt` for the `GlyphViewCase` value that draws strokes with no frame, and `PbblsIcons.kt` for the plus icon's name; fix both. `pieceClip` comes from `SplitStoneScope`.

- [ ] **Step 2: Build and commit**

```bash
./gradlew :app:compileDebugKotlin ktlintCheck
git add apps/android && git commit -m "feat(android): the starter glyphs as one split stone, with a draw piece"
```

### Task 5.3: Step 4, This is you (name on a rule, the stone, the carve satellite)

**Files:**
- Create: `features/onboarding/steps/YouStep.kt`
- Modify: `OnboardingScreen.kt` (`"you"` branch, the carve swap)

- [ ] **Step 1: The step**

```kotlin
package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.Glyph

/**
 * Step 4 (#1510 §1): a large bare name field on a rule (a `TextField` with transparent containers
 * and only its indicator line), the 3×3 glyph stone under it; with the keyboard up the stone folds
 * into one row of four.
 */
@Composable
fun YouStep(
    name: String,
    glyphs: List<Glyph>,
    selectedGlyphId: String?,
    onNameChange: (String) -> Unit,
    onGlyphSelect: (Glyph) -> Unit,
    onDraw: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(stringResource(R.string.onboarding_you_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        TextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text(stringResource(R.string.onboarding_you_name_hint), style = MaterialTheme.typography.headlineMedium) },
            textStyle = MaterialTheme.typography.headlineMedium,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = colors.primary,
                    unfocusedIndicatorColor = colors.outlineVariant,
                ),
            modifier = Modifier.fillMaxWidth().testTag("onboarding_you_name"),
        )
        Text(stringResource(R.string.onboarding_you_glyph_body), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        GlyphStoneGrid(glyphs = glyphs, selectedId = selectedGlyphId, onSelect = onGlyphSelect, onDraw = onDraw, folded = imeUp)
        Spacer(Modifier.weight(1f).height(Spacing.lg))
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = onContinue,
            enabled = name.isNotBlank() || selectedGlyphId != null,
            modifier = Modifier.fillMaxWidth().testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}
```

- [ ] **Step 2: Wire the step and the carve swap in the shell**

`OnboardingContent` gains `onNameChange`, `onGlyphSelect: (Glyph) -> Unit`, `onDraw: () -> Unit`, `onCarved: (Glyph) -> Unit`, `onCarveCancelled: () -> Unit` and the branch

```kotlin
                    "you" -> YouStep(state.name, state.systemGlyphs + state.carved, state.glyphId, onNameChange, onGlyphSelect, onDraw, onNext)
```

The carve satellite (S1) is a content swap over the whole shell: in `OnboardingScreen`, before `OnboardingContent`,

```kotlin
    if (state.carvingFor != null) {
        // S1: the existing carve flow, full screen over the step (D8). Its ViewModel is this entry's,
        // released on cancel the way GlyphPickerContent releases it (abandoned strokes never resurface).
        val carveViewModel: GlyphCarveViewModel = hiltViewModel()
        GlyphCarveScreen(
            onSaved = { glyph -> viewModel.onCarved(glyph); carveViewModel.reset() },
            onCancel = { viewModel.onCarveCancelled(); carveViewModel.reset() },
            viewModel = carveViewModel,
            modifier = modifier,
        )
        return
    }
```

`features/onboarding` may import `features/glyph/carve`? Check the Konsist rule in `app/src/test/kotlin/app/pbbls/android/architecture/` (`project_android_core_boundary`): core→features is forbidden; features→features is what `GlyphPickerSlot` exists to avoid. If the architecture test forbids it, add a `GlyphCarveSlot` typealias in `core/ui/GlyphPickerSheet.kt` (`@Composable (onSaved: (Glyph) -> Unit, onCancel: () -> Unit, modifier: Modifier) -> Unit`), fill it in `PebblesEntryProvider.kt` beside `glyphPicker`, and pass it to `OnboardingScreen(carve = glyphCarve, onFinish = …)`.

- [ ] **Step 3: ViewModel wiring, build, lint, commit**

Pass `viewModel::onNameChange`, `{ viewModel.onGlyphPick(it.id) }`, `viewModel::onDrawForYou` from `OnboardingScreen`.

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "app.pbbls.android.architecture.*" ktlintCheck
git add apps/android && git commit -m "feat(android): onboarding you step, the name on a rule and the glyph stone, drawing as a satellite"
```

### Task 5.4: Step 5, Three places, and the soul glyph sheet

**Files:**
- Create: `features/onboarding/steps/PeopleStep.kt`, `features/onboarding/steps/SoulGlyphSheet.kt`
- Modify: `OnboardingScreen.kt`

- [ ] **Step 1: The step** (one `SplitStone(n = 3)` on the quick-actions seed, pink pieces for named people, a dashed empty place; the name is typed in place under the piece)

```kotlin
package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.designsystem.stone.SplitStone
import app.pbbls.android.core.designsystem.stone.StoneGeometry
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase
import app.pbbls.android.features.onboarding.OnboardingPerson
import androidx.compose.foundation.clickable

/**
 * Step 5 (#1510 §1, D7): "Who would you call first with good news?" One split stone of three
 * pieces (the Home quick actions seed), no shelf line, no outlines. A named person's piece is
 * `primaryContainer` with their glyph; the name and "Change glyph" sit under it. The empty place is
 * a dashed outline with a plus and "Add one"; tapping it opens the name field in place under the
 * piece, keyboard up (S3). The glyph sheet is S2.
 */
@Composable
fun PeopleStep(
    people: List<OnboardingPerson>,
    glyphs: List<Glyph>,
    onAdd: () -> Unit,
    onNameChange: (Int, String) -> Unit,
    onCommit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onChangeGlyph: (Int) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val fills = List(3) { index -> people.getOrNull(index)?.takeIf { !it.editing }?.let { colors.primaryContainer } ?: Color.Transparent }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(stringResource(R.string.onboarding_people_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.onboarding_people_body), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        SplitStone(n = 3, seed = StoneGeometry.QUICK_ACTIONS_SEED, fills = fills, minHeight = 104.dp) { index ->
            val person = people.getOrNull(index)
            val glyph = person?.glyphId?.let { id -> glyphs.firstOrNull { it.id == id } } ?: glyphs.firstOrNull()
            Box(Modifier.fillMaxSize().pieceClip(index), contentAlignment = Alignment.Center) {
                when {
                    person == null ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                                .drawBehind {
                                    drawRoundRect(
                                        color = colors.outline, cornerRadius = CornerRadius(22.dp.toPx()),
                                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                                    )
                                }.clickable(enabled = people.none { it.editing } && people.size < 3, onClick = onAdd)
                                .testTag("onboarding_person_add_$index"),
                            contentAlignment = Alignment.Center,
                        ) { PbblsIconImage(PbblsIcon.PLUS_CIRCLE, PbblsIconSize.TILE, contentDescription = stringResource(R.string.onboarding_people_add), tint = colors.onSurfaceVariant) }
                    person.editing ->
                        PbblsIconImage(PbblsIcon.PEBBLE_ROUND, PbblsIconSize.TILE, contentDescription = null, tint = colors.outline)
                    else ->
                        if (glyph != null) GlyphView(case = GlyphViewCase.GLYPH, strokes = glyph.strokes, viewBox = glyph.viewBox, side = 56.dp)
                }
            }
        }
        // Under the pieces: the name (or the field being typed), and "Change glyph" / "Add one".
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { index ->
                val person = people.getOrNull(index)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    when {
                        person == null ->
                            Text(stringResource(R.string.onboarding_people_add), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        person.editing -> {
                            val focus = remember { FocusRequester() }
                            LaunchedEffect(Unit) { focus.requestFocus() }
                            BasicTextField(
                                value = person.name,
                                onValueChange = { onNameChange(index, it) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleSmall.copy(color = colors.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                                cursorBrush = SolidColor(colors.primary),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { onCommit(index) }),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.Center) {
                                        if (person.name.isEmpty()) Text(stringResource(R.string.onboarding_people_name_hint), style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                                        inner()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("onboarding_person_name_$index"),
                            )
                        }
                        else -> {
                            Text(person.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            TextButton(onClick = { onChangeGlyph(index) }, modifier = Modifier.testTag("onboarding_person_glyph_$index")) {
                                Text(stringResource(R.string.onboarding_people_change_glyph), style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = { onRemove(index) }) {
                                Text(stringResource(R.string.onboarding_people_remove, person.name), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f).height(Spacing.lg))
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = onContinue,
            enabled = people.any { !it.editing },
            modifier = Modifier.fillMaxWidth().testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}
```

The "pink pieces" of the drawing are `primaryContainer` in this theme (D12: the drawings are intent). An empty place's fill is transparent because the dashed outline is drawn on the paper, not on a piece.

- [ ] **Step 2: The glyph sheet** (S2: a `ModalBottomSheet`, the person's piece and name in the header, the same stone grid, "Draw one" as its last piece, full-width "Use for <name>")

```kotlin
package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.Glyph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoulGlyphSheet(
    personName: String,
    glyphs: List<Glyph>,
    currentGlyphId: String?,
    onUse: (String) -> Unit,
    onDraw: () -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by rememberSaveable { mutableStateOf(currentGlyphId) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Text(stringResource(R.string.onboarding_people_glyph_sheet_title, personName), style = MaterialTheme.typography.titleLarge)
            GlyphStoneGrid(
                glyphs = glyphs,
                selectedId = picked,
                onSelect = { picked = it.id },
                onDraw = onDraw,
                drawLabelRes = R.string.onboarding_people_glyph_draw,
            )
            PebblesPrimaryButton(
                text = stringResource(R.string.onboarding_people_glyph_use, personName),
                onClick = { picked?.let(onUse) },
                enabled = picked != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

- [ ] **Step 3: Wire in the shell**

In `OnboardingContent`: the `"people"` branch calls `PeopleStep(state.people, state.systemGlyphs + state.carved, onPersonAdd, onPersonNameChange, onPersonCommit, onPersonRemove, onPersonGlyphSheet, onNext)` and, after `AnimatedContent`, inside the `Box`:

```kotlin
        state.glyphSheetFor?.let { index ->
            val person = state.people.getOrNull(index) ?: return@let
            SoulGlyphSheet(
                personName = person.name,
                glyphs = state.systemGlyphs + state.carved,
                currentGlyphId = person.glyphId,
                onUse = { onPersonGlyphPick(index, it) },
                onDraw = { onDrawForPerson(index) },
                onDismiss = { onPersonGlyphSheet(null) },
            )
        }
```

Add the matching parameters to `OnboardingContent` and pass the ViewModel's methods from `OnboardingScreen`. `onPersonGlyphSheet(index)` opens, `(null)` closes.

- [ ] **Step 4: ViewModel tests for the people step**

Append to `OnboardingViewModelTest`:

```kotlin
    @Test
    fun `a blank person is dropped on commit and at most three places exist`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            repeat(4) { vm.next() } // to people, consent passes because nothing is recorded? No: record first.
        }
```

Replace that stub with a real walk: record the four acts, `next()` twice more to reach `people`, then

```kotlin
            vm.onPersonAdd()
            vm.onPersonCommit(0)
            assertTrue(vm.uiState.value.people.isEmpty())
            repeat(3) { i ->
                vm.onPersonAdd()
                vm.onPersonNameChange(i, "P$i")
                vm.onPersonCommit(i)
            }
            vm.onPersonAdd()
            assertEquals(3, vm.uiState.value.people.size)
            vm.onPersonGlyphSheet(1)
            vm.onPersonGlyphPick(1, "sys-5")
            assertEquals("sys-5", vm.uiState.value.people[1].glyphId)
            assertNull(vm.uiState.value.glyphSheetFor)
            vm.onDrawForPerson(2)
            vm.onCarved(Glyph(id = "mine", strokes = emptyList(), viewBox = "0 0 200 200"))
            assertEquals("mine", vm.uiState.value.people[2].glyphId)
            assertEquals(listOf("mine"), vm.uiState.value.carved.map { it.id })
```

- [ ] **Step 5: Run, lint, commit, submit**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug ktlintCheck
git add apps/android
git commit -m "feat(android): onboarding three places on the split stone, name in place, glyph sheet"
gh stack submit --auto
gh pr edit <n> --title "feat(android): onboarding you and first people on the split stone" --add-label feat --add-label android --add-label ui --milestone "M68 · V1 launch"
```

PR body: `Part of #AN`.

---

# Part 6 — pace, hand-off, screenshots, map

### Task 6.1: Step 6, the pace as a connected button group

**Files:**
- Create: `features/onboarding/steps/PaceStep.kt`
- Modify: `OnboardingScreen.kt`

- [ ] **Step 1: Branch and the step**

```bash
gh stack add feat/AN-onboarding-handoff-android
```

```kotlin
package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.OnboardingPace

/**
 * Step 6 (#1510 D11, D12): the four paces as one vertical M3 connected button group (`ToggleButton`s
 * with the connected leading / middle / trailing shapes, the chosen one filled), "No streaks, no
 * guilt." under it. The last button is "Start": it sends everything (`complete_onboarding`).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PaceStep(
    selected: OnboardingPace?,
    isCompleting: Boolean,
    errorRes: Int?,
    onSelect: (OnboardingPace) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(stringResource(R.string.onboarding_pace_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.onboarding_pace_body), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            val paces = OnboardingPace.entries
            paces.forEachIndexed { index, pace ->
                ToggleButton(
                    checked = pace == selected,
                    onCheckedChange = { onSelect(pace) },
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            paces.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton }.testTag("onboarding_pace_${pace.slug}"),
                ) { Text(stringResource(pace.labelRes)) }
            }
        }
        Text(stringResource(R.string.onboarding_pace_footer), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        errorRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = colors.error) }
        Spacer(Modifier.weight(1f).height(Spacing.lg))
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_finish),
            onClick = onFinish,
            enabled = !isCompleting,
            isLoading = isCompleting,
            modifier = Modifier.fillMaxWidth().testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}
```

Shell: `"pace" -> PaceStep(state.pace, state.isCompleting, state.errorRes, onPaceSelect, onNext)`; the `else` branch stays for `handoff` (never shown: `isDone` pops the entry).

- [ ] **Step 2: Build, commit**

```bash
./gradlew :app:compileDebugKotlin ktlintCheck
git add apps/android && git commit -m "feat(android): onboarding pace step as a connected button group"
```

### Task 6.2: The hand-off into Home's quick composer

**Files:**
- Modify: `core/model/MomentPayloads.kt:24-32`
- Modify: `features/home/quick/QuickMoment.kt`
- Modify: `features/home/quick/QuickMomentSheet.kt` (placeholder)
- Modify: `features/home/HomeMomentsViewModel.kt`, `features/home/HomeScreen.kt`
- Test: `features/home/quick/QuickMomentTest.kt` (exists? `ls app/src/test/kotlin/app/pbbls/android/features/home/quick/`; create it if not)

- [ ] **Step 1: The failing test**

```kotlin
    @Test
    fun `a prefilled open attaches the soul and shows the prompt`() =
        runTest {
            val quick = quick()
            quick.open(QuickMomentPrefill(soulId = "soul-1", promptRes = R.string.onboarding_handoff_prompt_good_days))
            assertEquals(listOf("soul-1"), quick.sheet.value?.soulIds)
            assertEquals(R.string.onboarding_handoff_prompt_good_days, quick.sheet.value?.promptRes)
            quick.setName("Coffee with Noé")
            quick.save(QuickMomentExit.CLOSE)
            advanceUntilIdle()
            assertEquals(listOf("soul-1"), writes.saveMomentCalls.single().soulIds)
        }
```

`quick()` and `writes` follow the file's existing fixtures (a `FakeMomentWriteService` recording `saveMoment` payloads; check its field name).

- [ ] **Step 2: Payload, prefill, placeholder**

`MomentSavePayload` gains

```kotlin
    /** Souls to attach, by id (`save_moment`'s `soul_ids`); null leaves the key out. */
    @SerialName("soul_ids")
    val soulIds: List<String>? = null,
```

`QuickMoment.kt`:

```kotlin
/** What the onboarding hands the composer (#1510 D9): a person to attach and the prompt the reasons chose. */
data class QuickMomentPrefill(
    val soulId: String? = null,
    @StringRes val promptRes: Int? = null,
)
```

`QuickMomentUiState` gains `val soulIds: List<String> = emptyList()` and `@StringRes val promptRes: Int? = null`; `open()` becomes

```kotlin
    fun open(prefill: QuickMomentPrefill? = null) {
        if (_sheet.value == null) {
            _sheet.value = QuickMomentUiState(happenedAt = now(), soulIds = listOfNotNull(prefill?.soulId), promptRes = prefill?.promptRes)
        }
    }
```

and `save` builds `MomentSavePayload(name = …, happenedAt = …, soulIds = form.soulIds.takeIf { it.isNotEmpty() })`. `QuickMomentForm`: the name field's placeholder is `stringResource(form.promptRes ?: R.string.home_quick_name_hint)` (read the current placeholder string's id and keep it as the default).

- [ ] **Step 3: Home takes the note**

`HomeMomentsViewModel`: inject `private val handoff: OnboardingHandoff` and `private val reference: ReferenceDataServicing` (whatever service `RootScreen`'s `referenceData.load()` reads souls from; read `core/data/ReferenceDataService.kt` for the souls accessor) and add

```kotlin
        /** The onboarding's note, once (#1510 D9): the first person by name, the prompt from the first reason. */
        fun onResumed() {
            camera.onResumed()
            val note = handoff.take() ?: return
            viewModelScope.launch {
                val soulId =
                    note.firstPersonName?.let { name ->
                        runCatchingCancellable { reference.souls() }.getOrNull()?.firstOrNull { it.name == name }?.id
                    }
                quick.open(QuickMomentPrefill(soulId = soulId, promptRes = note.reason?.let { promptFor(it) }))
            }
        }
```

keeping whatever `onResumed()` already did. `promptFor` maps `OnboardingReason` → the six `onboarding_handoff_prompt_*` ids. If `ReferenceDataServicing` has no `souls()` read, use `SoulsServicing.list()` instead and inject that.

`HomeScreen.kt` already calls `moments.onResumed()` in `LifecycleResumeEffect`, so the sheet opens the first time Home resumes after the onboarding entry pops.

- [ ] **Step 4: Run the home tests, commit**

```bash
./gradlew :app:testDebugUnitTest --tests "app.pbbls.android.features.home.*"
git add apps/android && git commit -m "feat(android): the onboarding hands Home's quick composer its first person and a prompt"
```

### Task 6.3: Screenshots

**Files:**
- Modify: `app/src/screenshotTest/kotlin/app/pbbls/android/FunnelScreenshots.kt`

- [ ] **Step 1: One preview per step, light, dark (`UI_MODE_NIGHT_YES`), FR and large font**

Replace `OnboardingScreenLight` with six previews over `OnboardingContent` and fixed states; `UiFixtures.kt` gets `onboardingGlyphs(): List<Glyph>` (eight glyphs with two or three strokes each, as the other fixtures draw them) and `promiseLetterFixture`. Each preview:

```kotlin
@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "dark", showBackground = true, widthDp = 390, heightDp = 844, uiMode = UI_MODE_NIGHT_YES)
@PreviewFrench
@PreviewLargeFontTall
@Composable
fun OnboardingPromise() {
    PebblesTheme { OnboardingContent(state = OnboardingUiState(letter = promiseLetterFixture), onBack = {}, onSkip = {}, onNext = {}, /* every other lambda a no-op */) }
}
```

and `OnboardingConsent` (two acts recorded, one on), `OnboardingReasons` (two selected), `OnboardingYou` (a name, a glyph), `OnboardingPeople` (two named, one empty place), `OnboardingPace` (the fourth pace). States set `step = OnboardingSteps.all[n]`.

- [ ] **Step 2: Do not run the screenshot task locally against references** (`project_android_screenshot_gate_ci_only`). Render once to check they compose:

```bash
./gradlew :app:updateDebugScreenshotTest -Pandroid.experimental.enableScreenshotTest=true 2>&1 | tail -5
git status --short app/src/screenshotTestDebug/reference | head
```

Keep only the new `Onboarding*` PNGs and the removed `OnboardingScreenLight*` ones in the commit; discard every other changed reference (`git checkout -- <path>`) because those diffs are the local renderer's, not yours. CI re-renders and the maintainer approves the held run (see `android-screenshot-gate`).

- [ ] **Step 3: Commit**

```bash
git add apps/android/app/src/screenshotTest apps/android/app/src/screenshotTestDebug/reference/app/pbbls/android/FunnelScreenshotsKt
git commit -m "test(android): one screenshot per onboarding step, light, dark, french and large font"
```

### Task 6.4: Device pass, Arkaik, the decisions log, the journal, submit

- [ ] **Step 1: Device pass on the Pixel 7 AVD** (`project_android_ci_only_builds`): sign up a throwaway account, walk all six steps including a carve and a soul glyph change, land on Home with the composer open and the person attached. Kill the app at step 3 and relaunch: the flow restarts at the promise with the consent switches locked.

- [ ] **Step 2: Arkaik** (hosted, through `arkaik-mcp`; `ARKAIK_TOKEN` exported, `project_arkaik_hosted_writes`). Find the Android onboarding journey and the existing Android onboarding view with `list_nodes`, then `create_node` one view per step (`Android · Onboarding · Promise`, `… Consent`, `… Reasons`, `… You`, `… Three places`, `… Pace`) and the two satellites (`… Draw a glyph`, `… Soul glyph sheet`), status `development`, each edged from the journey; `create_node` the `complete_onboarding` endpoint and the `promise_letters` model if Part 1 did not (Part 1 should: do it there with status `development` and set `releasing` is the App's job). Replace the old four-slide view node's description rather than deleting it, so its history stays. If the tools are unavailable, say so and stop; never touch `docs/arkaik/bundle.json`.

- [ ] **Step 3: Decisions log** — append one entry to `docs/decisions/log.md`:

```markdown
## 2026-11-05 — The onboarding gate reads the profile, and consent moves into the flow on Android (#1510)

- **Status:** taken
- **Scope:** cross-surface contract (database), Android
- **Context:** Android gated its four-slide onboarding on a device flag alone and never wrote `onboarding_completed`; the consent gate overlay stood above everything, so a new OAuth account met the consent before any promise.
- **Decision:** `complete_onboarding` is the only client write path to `onboarding_completed`, `onboarding_reasons` and `pace`. Android presents the flow when the profile says not completed (and once per device), and the consent gate overlay stands down while the flow is due: step 2 records the acts through `record_consent`, an act already on record is shown on and locked, and the gate re-checks when the flow ends. A soul created in the flow always carries a glyph (`souls.glyph_id` is not null); the client assigns the first system glyph when none was picked. The drawing satellite is a content swap inside the onboarding entry, not a pushed key, because a pushed entry cannot hand its result back.
- **Why:** D2 (promise before consent) is impossible with the overlay above the flow; the device flag alone re-onboards nobody on a reinstall and cannot restart an abandoned flow (D10).
- **Consequences:** iOS and web follow the same gate and the same step-2 rule in their own plans. A pre-flow Android account that reinstalls sees the new flow once.
- **Refs:** #1510, #BE, #AN, the spec and this plan.
```

- [ ] **Step 4: Full gate, submit**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug ktlintCheck
git add docs/decisions/log.md apps/android
git commit -m "docs(core): record the onboarding gate and consent-in-flow decisions"
gh stack submit --auto
gh pr edit <n> --title "feat(android): onboarding pace, hand-off to the quick composer, screenshots" --add-label feat --add-label android --add-label ui --milestone "M68 · V1 launch"
```

PR body: `Resolves #AN`, the Lab Note (EN/FR) via the `lab-note` skill, the list of key files, and the device-pass result.

- [ ] **Step 5: Journal pebble**

```bash
node scripts/journal/drop-pebble.mjs --size medium --valence highlight --emotion <honest slug> --collection Android --collection "Back-end & data" --title "The first minutes, rebuilt on Android" --journal "A promise before anything else, then four switches, six tiles, your name on a stone and three places for your people. The server keeps it all in one write."
```

---

## Self-review

**Spec coverage.** D1 steps and skips: Task 3.4 config, 4.2 shell. D2 letter table and admin: 1.1, 2.1. D3 four switches via `record_consent`: 4.1, 4.3 (recorded acts locked, the named deviation). D4 six tiles: 4.3. D5 one RPC: 1.1, 3.1, 4.1 `complete()`. D6 columns: 1.1. D7 ordinary souls, the split stone, `is_system`: 1.1, 3.2 `systemGlyphs`, 5.1–5.4. D8 carve reuse: 5.3. D9 hand-off into the composer with the first soul and a prompt: 6.2. D10 restart on relaunch, consent not re-asked: 3.3, 4.1. D11 pace stored, no reminders: 6.1. D12 M3 chrome: switches 4.3, `ToggleButton` group 6.1, `ModalBottomSheet` 5.4. §1 progress stones: 4.2. §2.4 consent versions from constants: `ConsentGateLogic.versionFor`. §3 Android: all parts; admin: Part 2. §4 harness, foreign payload test, step config test, screenshots: 1.2, 3.1, 3.4, 6.3. §5 Arkaik: 6.4. Not in this plan, by the spec's own split: iOS and web.

**Known open points for the executor:** the `set_public_profile` RPC name in Task 1.2 (one grep), `PbblsIconSize` and `GlyphViewCase` names (5.2, 5.3), the `FakeConsentService` row factory (4.1), the Konsist feature→feature rule for the carve import (5.3), and `ReferenceDataServicing`'s souls accessor (6.2). Each is a one-line lookup named at the step where it matters.
