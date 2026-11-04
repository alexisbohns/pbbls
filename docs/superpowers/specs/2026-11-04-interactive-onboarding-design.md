# Interactive onboarding — design

- **Date:** 2026-11-04
- **Issues:** #1510 (spec and plan). One back-end issue and one per client follow the plan. Supersedes #1466, #1469, #1470 (record a first pebble at the end of onboarding), which become step 7 of this flow.
- **Milestone:** M68 · V1 launch
- **Follows:** the strategy audit of 2026-10-08 (vision A, "a Duolingo of feelings"), the Art. 9 consent gate (2026-09-11), the Android consent gate (2026-09-27), Home and quick actions (M67).
- **Drawings:** four private rounds and the final flow, hosted as artifacts. Final flow: `https://claude.ai/artifact/Bbq5boAqZQ8EBofZ2EhAjk`. Round 3 (the variants the picks came from): `https://claude.ai/artifact/2SPZiCAs2tAGhaMwXaUsiw`. Round 4 (You and Your first people on the split stone): `https://claude.ai/artifact/K6AA7tijEF6AFBPkVh5USM`.

## Goal

The first minutes in Pebbles become a short, interactive onboarding that personalises the app and earns trust, in the spirit of Duolingo's opening questions but filtered by one test: **a question is asked only if its answer changes the product for this user.** Everything that does not pass the test (prior journaling habits, opinions on social networks, discovery channel, pricing, widgets, imports) is out of the flow for good.

The flow replaces the three- or four-slide "tips" onboarding each client shows after sign-up today. The pre-auth Welcome carousel is unchanged.

## Why these steps

Pebbles' value sits mostly in the emotional and life-changing tiers of Bain's elements of value: therapeutic value and self-actualisation (naming what you felt), belonging (memory indexed by people), nostalgia and hope (the Path and the wraps), reduced anxiety (private by constitution), heirloom (a record that outlives the app). The flow says the constitution first, collects only what the app will act on, and ends inside the product.

## Decisions

| # | Decision |
|---|---|
| D1 | **Seven steps, in this order:** promise · consent · reasons · you · first people · rhythm · hand-off to the composer. Promise and consent cannot be skipped; reasons, you and rhythm show **Skip**; first people shows **Later**; the hand-off has no skip. Back is always available. |
| D2 | **The promise comes before the consent** and its text is **admin-editable**, stored in a dedicated typed table (`promise_letters`, modelled on `guides` and `settings_links`: EN required, FR falls back to EN, `published` gates public read, writes admin-only). The consent, by contrast, is bound to document versions and never reads admin data (settings-links D2 still holds). |
| D3 | **Consent is one switch per act**: `health_data`, `terms`, `privacy`, `age_assurance`, the four non-withdrawable-or-withdrawable kinds the ledger already records. Continue is disabled until all four are on. Each act is written through the existing `record_consent` RPC with its own document version; no new consent code path. |
| D4 | **Reasons are multi-select, six tiles of equal size.** Slugs: `notice-feelings`, `keep-people`, `hard-season`, `good-days`, `own-patterns`, `keep-record`. The sixth tile exists for the grid and for the heirloom proposition. |
| D5 | **One RPC, `complete_onboarding`,** writes everything the flow collected in one transaction: display name, glyph, reasons, pace, up to three souls, and `onboarding_completed = true`. Clients never stitch these writes. Consent acts are the exception: they are recorded at step 2, as they happen, so a user who quits at step 3 still has a valid ledger. |
| D6 | **Answers live on `profiles`** as two nullable columns (`onboarding_reasons text[]`, `pace text`) rather than a new table: they are per-user, single-valued, and read by every surface on every launch. Skipped steps leave null. |
| D7 | **Souls created in the flow are ordinary souls** (`souls` rows with `name` and `glyph_id`), nothing marks them as "onboarding souls". The glyph picker is the same picker the app already has; the starter grid is **one split stone** (the Home and You rock-cut geometry, `StoneGeometry.splitBento` on iOS and Android, the same seeds everywhere) cut 3×3: eight system glyphs (`glyphs.is_system`) plus a "Draw" piece, the chosen piece in `primaryContainer`, the rest in `surfaceContainer`, no outlines. The same component serves the You step and the soul picker. |
| D8 | **Draw-your-own glyph reuses the existing carve flow** on each surface as a satellite; it is not rebuilt for onboarding. |
| D9 | **The hand-off is not a screen.** After `complete_onboarding`, the client lands on Home and opens the quick-moment composer once, with the first soul pre-attached and a prompt chosen from the reasons. Dismissing it is fine. |
| D10 | **Quitting mid-flow restarts the flow next launch** (profile stays `onboarding_completed = false`); each client may keep the typed answers locally between launches but is not required to. Consent acts already recorded are not re-asked (the gate's existing rule). |
| D11 | **Reminders are not in this flow.** The pace answer is stored now and consumed by M69 (reminders) later. The notification permission is asked there, never here. |
| D12 | **Platform-native chrome.** Android draws the flow with M3 (connected button group for the pace, switches, bottom sheets); iOS with its own controls; web with shadcn. The drawings are the shared intent, not pixel targets. |

## 1. The flow

| Step | Screen | Collects | Skippable |
|---|---|---|---|
| 1 | **A promise, written once.** The letter from `promise_letters`, the glyph as signature, one button ("I'll hold you to it"). | nothing | no |
| 2 | **Your consent.** Four switches with one-line explanations, "Read the terms" and "Read the policy" chips, Continue disabled until all four are on. | four consent acts | no |
| 3 | **What brings you here?** Six equal tiles, multi-select, icon and label, a lit stone in the corner when selected. | `onboarding_reasons` | Skip |
| 4 | **This is you.** A large bare name field (the name on a rule, not an outlined field) above the 3×3 split stone of glyphs: eight system glyphs and a "Draw my own" piece marked by a dashed inset, no outline. Keyboard up: the stone folds into one row of four (the chosen glyph, two others, Draw). | `display_name`, `glyph_id` | Skip |
| 5 | **Three places.** "Who would you call first with good news?" One split stone of three pieces (the Home quick-actions row seed), no shelf line, no outlines: a named person's piece in `primaryContainer` carrying the glyph, the name and "Change glyph" under the piece; the empty place is a dashed outline on the paper with a plus and "Add one" under it. Tapping the empty place puts the name field **in place under the piece** with the keyboard up (no sheet, no page); the glyph picker is a bottom sheet with the person's piece in its header. | up to three souls | Later |
| 6 | **What pace suits you?** Four paces as one M3 connected group, the chosen one a filled pill: every day · a few times a week · only when it matters · wrapping the end of the week. Footer: "No streaks, no guilt." | `pace` | Skip |
| 7 | **Hand-off.** `complete_onboarding`, then Home with the quick-moment composer open. | nothing | n/a |

**Progress device.** A row of seven stones in the top bar on every step (the icon set's `pebble-round`): passed stones lit, the current stone lit and larger, stones ahead outlined. Back on the left, Skip or Later on the right.

**Satellites.** S1 glyph drawing (from step 4): the existing carve flow, presented full screen with a strip showing the glyph at path, card and detail sizes. S2 glyph picker for a soul (from step 5): bottom sheet, the soul's stone and name in the header, the same 3×3 split stone as step 4 with "Draw one" as its last piece, full-width "Use for <name>". S3 add a person (from step 5): no satellite; the name is typed in place under the piece.

**Copy consumers.** The reasons pick the composer's first prompt on the hand-off and the line Home shows while the Path is empty. The pace is read by M69 and names the look-back the app promises ("your week, each Sunday" for the fourth pace). Both are first-party aggregate counts in the admin (distribution only, never per user).

## 2. Data

### 2.1 `promise_letters`

```sql
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
```

RLS as `settings_links`: public read where `published`, writes admin-only. Clients read the most recently updated published row and fall back to EN. One row is seeded, published, with the letter as drawn ("There will never be a feed here. Nothing you keep becomes an ad, a trend, or data to train a machine. If you leave, your pebbles leave with you, in a file you can open anywhere."). Not user data: no `purge_account` append. No emotion or domain: no `sync_achievement_catalog()`.

### 2.2 `profiles` columns

```sql
alter table public.profiles
  add column onboarding_reasons text[] check (
    onboarding_reasons <@ array['notice-feelings','keep-people','hard-season','good-days','own-patterns','keep-record']::text[]
    and cardinality(onboarding_reasons) between 1 and 6),
  add column pace text check (pace in ('daily','few-a-week','when-it-matters','weekly-wrap'));
```

Both nullable; null means skipped or pre-flow account. `profiles` is already purged and already projected through `get_public_profile`'s allowlist, which does **not** gain these keys.

### 2.3 `complete_onboarding`

```sql
create function public.complete_onboarding(
  p_display_name text default null,
  p_glyph_id     uuid default null,
  p_reasons      text[] default null,
  p_pace         text default null,
  p_souls        jsonb default '[]'::jsonb   -- [{"name": "Maya", "glyph_id": "<uuid>"}, …], at most 3
) returns void
language plpgsql security definer set search_path = public
```

Owner-checked on `auth.uid()`. In one transaction: updates `display_name` and `glyph_id` when given, by calling `update_profile` so the two share one validation, sets the two new columns, inserts each soul (`name` trimmed, non-empty, `glyph_id` must be a system glyph or one owned by the caller), and sets `onboarding_completed = true`. Rejects more than three souls, duplicate names within the payload, and a second call on an already-completed profile with a no-op rather than an error (idempotent). Consent is **not** in this RPC.

The web's current `updateProfile({ onboarding_completed: true })` call is replaced by the RPC; `onboarding_completed` stays writable only through it and the admin.

### 2.4 Consent acts

Step 2 calls `record_consent(kind, document_version, source)` four times, once per switch turned on, with the surface's register source (`web_register`, `ios_register`, `android_register`), the values already used for acts collected in the sign-up funnel. Versions come from the clients' built-in constants (`CONSENT_DOCUMENT_VERSION`, `TERMS_DOCUMENT_VERSION`, the privacy version), never from the promise table.

## 3. Surfaces

All three clients replace their current post-signup slides with this flow. The step list is a config on each surface, so M69 can insert a reminders step after the pace without rework.

- **Web:** `ONBOARDING_STEPS` grows from three ids to seven; `OnboardingStepper` becomes the shell, the existing `ConsentGate` is folded into step 2 (one component, four switches). Satellites are dialogs. The quick-moment composer already exists on Home (`QuickMomentDialog`) and is opened once on the hand-off.
- **iOS:** `Features/Onboarding` is rebuilt around the seven steps; the glyph picker and the carve flow are the existing ones; the hand-off opens the Home quick-moment sheet.
- **Android:** `features/onboarding` likewise; the pace is a `ButtonGroup` (connected), consent rows are M3 `Switch`es, the picker a `ModalBottomSheet`. The onboarding entry stays pushed over Home as Nav 3 does today.
- **Admin:** a "Promise" page edits `promise_letters` (EN/FR, publish toggle), and the analytics page gains two distribution counts (reasons, pace).

iOS and Android mirror each other 1:1; web follows the same step ids and the same RPC payload.

## 4. Testing

- **Contract harness** for `complete_onboarding`: happy path with three souls, idempotent second call, rejection of a fourth soul, of a foreign `glyph_id`, of an unknown reason or pace, and the zero-row guarantee for an anon caller. Runs with the anon harnesses in CI.
- **Foreign payloads:** each client's souls payload encoder is tested against the verbatim jsonb the other two surfaces produce (names with accents, a null glyph on a skipped pick).
- **Per surface:** the step config test (count, ids, order, which steps skip), one screenshot per step on Android (light and dark, FR), one reference render per step on iOS, Vitest on the web stepper's step transitions and on the consent switches gating Continue.
- **`verify-account-purge.ts`:** no new table carries user data, so no new seed; the two profile columns are covered by the existing profile purge.

## 5. Arkaik

One view node per step on each surface, plus the two satellites, parented to the existing onboarding journey; the back-end gains the `complete_onboarding` endpoint and the `promise_letters` model. Statuses move to `development` when each part is picked up.

## 6. Out of scope, by decision

Dropped from the onboarding for good, each having been drawn and judged: prior journaling habits, opinion on Instagram, colour world (a Settings choice), widget prompt, importing memories, pricing at sign-up, "find your level". Parked for later, not in this spec: a feelings-level question (only if the emotion picker gains tiers), "who walked you here" asked after the first wrap, the maker's note (About screen), "the path to your first wrap" (a month-ahead promise screen), and reminders (M69).
