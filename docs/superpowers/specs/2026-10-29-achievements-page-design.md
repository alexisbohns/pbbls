# The achievements page — the server decides, every client renders

**Date:** 2026-10-29 · **Surfaces:** supabase (the brain), then iOS, Android and web on the same RPCs · **Status:** draft for review

The You → Achievements page was explored over nine rounds of hosted boards. The maintainer approved its final view and its five sheets on 28–29 October. This spec turns that design into a data model and a contract.

It follows the rule set for the monthly wrap: **the front end computes nothing**. Every number, membership, order, level, colour family and "new" flag is served by Postgres. Each client decodes the payload, draws it, localises the words and keeps working on a stale copy when the network fails.

Design sources (private artifact, maintainer-approved): https://claude.ai/artifact/5MD93PUAXwpRjGGMimoKgF
- **The final view:** the page.
- **The sheets:** what each card opens.
- **The leveling exploration**, and the blog post that tells it: `/blog/counting-in-stone`.

The source of each board, including the JS that computes the year disc and becomes the harness's oracle (D13), lives with the boards. It is copied into `packages/supabase/scripts/oracles/achievements/` in part 2.

This spec extends `2026-07-29-achievements-design.md` (D1–D15 there still hold unless a decision below supersedes one) and the Home tab's first five (#1254).

---

## 1. The split

| Lives in Postgres (computed once, served to all clients) | Lives in each client |
|---|---|
| Which levels are reached, by which pebble, on which day | Decoding the payload into display models (a pure mapping) |
| The next level, what is left, the progress fraction | Geometry: wheel rings and seats, the rosace, the skyline, the disc's spokes |
| Contributions (emotion and domain firsts, the six firsts), their order and seats | Colour: family slug → palette, theme roles |
| Completion of a set, the ceremony's offer time | Copy: semantic values → localised words (plurals, number words) |
| "New since you last looked" for each card | Interaction: sheets, pushes, the pull of a ceremony, motion and haptics |
| The year disc: each month's day counts, full weeks, level, progress, family, state | Accessibility labels assembled from the payload |
| The domains' coverage tier, the months the table lists | A cached copy of the last payload, shown while a fresh one loads |
| Who sees what on a public profile | |

**The test:** if two platforms could disagree about a number, a membership or an order, it belongs on the server. If they can only disagree about pixels or wording, it belongs on the client. The server returns **semantic values, never sentences**: `coverage: "most"`, `state: "quiet"`, `needs: "pebbles"`.

---

## 2. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | **The page is a header and five cards.** The header shows the days kept. The cards are Explorer, Emotions, Domains, Pebbles (with glyphs carved and glyph sales as two lines), and one card per year. Each card opens its sheet. The page has no legends, no captions and no "new since" lines; new items are marked on the figures only. | Final view, maintainer's edits of 28 October. |
| D2 | **Days kept is calendar age**, from the account's creation (`profiles.created_at`) to today in the reader's zone. It is never a count of days with entries, and it never resets, because either would make it a streak. | Round 2 note; streaks are out of the product. |
| D3 | **Counting stacks.** Pebbles: 1 · 10 · 25 · 50 · 100 · 250 · 500 · 1000, then one level every 500. Glyphs carved: 10 · 25 · 50 · 75 · 100. Glyph sales: 1 · 5 · 10 · 25 · 50. The pebble ladder is seeded in the catalog up to 10 000; a harness check fails if the ladder has a gap. Whether any user is within 500 of its top needs a cross-user read, so it is a manual SQL query (§3.1). | The maintainer's threshold rule (round 4). The catalog stays the one source of levels. |
| D4 | **The crossing pebble.** Every level, first and contribution records the pebble that earned it, as `achievement_unlocks.pebble_id`. For a counting level it is the threshold-th pebble by completion time (`pebbles.created_at`). For an emotion or domain first, and for each of the six firsts, it is the earliest pebble of that kind by `moments.happened_at`. Existing unlocks are backfilled with the same rule. A deleted pebble sets the reference to null. The unlock stays, because badges are permanent, and the sheet shows the row without its pebble. | Every board draws the stone that crossed. |
| D5 | **The six firsts are a new family, `first_stone`, with six catalog rows.** Each is an independent first: small, medium or large intensity (1, 2, 3), and lowlight, neutral or highlight (positiveness −1, 0, 1). They are never combinations, so three pebbles can earn all six. | Maintainer, 26 October. |
| D6 | **The Explorer wheel, from the centre out:** the user's **first pebble** (the crossing pebble of `pebble-count-1`, drawn as its own seal); the first five; the six firsts; 25 pebbles; the domains; the emotions. There is no blue pebble on the page. The Home checklist's claim (`claim_blue_pebble`) is unchanged. | Maintainer, 28 October. |
| D7 | **Contributions are silent.** An emotion or domain first, and each of the six firsts, is recorded quietly: it shows no unlock moment and pays no karma. Only the completion of its set is celebrated (D9). `check_achievements()` returns these rows with `celebrate = false`. Admin karma on these families is forced to 0, and existing unlocks keep what they were paid. | Safety: a first "sadness" or a first heavy stone must never be celebrated. All five round-1 boards agreed. |
| D8 | **Counting levels celebrate lightly, and never over a heavy pebble.** A new pebble, glyph or sales level, and the first pebble, returns `celebrate = true` unless its crossing pebble is heavy (positiveness −1). `pebble-count-1` never celebrates: `first-pebble` celebrates that same pebble, once. A level crossed by a heavy pebble is recorded silently and appears as a "new" mark the next time the card is seen. | Consensus of the round-1 boards. |
| D9 | **Completions are offered, then pulled.** When a set becomes whole (all emotions, all domains or the six firsts), the server records a completion. It is never shown at the moment it happens. It is offered from 06:00 the next day in the reader's zone, as a quiet "ready" mark on its card. The user starts the ceremony with `pull_ceremony(stack)`. An offer never expires. Completions pay karma; contributions never do. | Consensus; the user starts it. |
| D10 | **"New since you last looked" is per card.** `achievement_views(user, card, seen_at)` is set when the card's sheet opens (`mark_achievements_seen(card)`). An item is new when it was recorded (`unlocked_at`) after `seen_at`. With no `seen_at` row, nothing is new, so the first visit after this ships is the baseline and a veteran's history is not flagged. | Round 2 model. |
| D11 | **The domains card says what coverage means, never a count.** The server returns a tier: `none` (0), `few` (below ⅓), `third` (⅓ to below ½), `half` (½ to below ¾), `most` (¾ to below all) or `all`. Each client maps the tier to its sentence. The fractions keep the tiers valid if a domain is ever added. The order of the domains is the order they came: the contribution's pebble `happened_at`, then `unlocked_at`. | Maintainer, 28 October. |
| D12 | **The year disc, decided on the server.** There is one disc per year: the current year always, and a past year only if it holds at least one moment. Each month has three levels:
  - level 1 is 14 days with a moment, and a moment in every full week of the month;
  - level 2 is 14 days with a pebble, and a pebble in every full week;
  - a full week runs Monday to Sunday and lies entirely inside the month; in the current month, only full weeks that have ended count.

  Days are local days in `p_tz`. A pebble's day is its moment's `happened_at`.

  For each month the server sends the counts, the level and the progress toward the next level. Progress is the smaller of the days over 14 and the filled weeks over the full weeks.

  It also sends the month's **family** and its **state**:
  - the family is the one with the most pebbles that month; ties break on total intensity, then on the wheel order of the family slugs (`joy` · `pride` · `anger` · `fear` · `shame` · `sadness` · `peace`);
  - the state is `upcoming` (a future month), `quiet` (a past or current month with no pebble) or `kept`.

  The table lists the kept and quiet months from the year's first moment onward, newest first; the server sends that list. | Maintainer, 28–29 October: days not entries, docked counters, colour only for pebbles. |
| D13 | **The year computation has an oracle.** The board's JS (`monthStats`, `monthLevels`) is committed under `packages/supabase/scripts/oracles/achievements/`. The harness compares the SQL against it month by month on fixed and seeded random years, following the gem precedent. | One implementation, kept honest. |
| D14 | **One read RPC for the page and every sheet:** `get_achievements(p_tz text) returns jsonb`. It is `security invoker`, computed on read and scoped to `auth.uid()`. The page opens with the existing `check_achievements()`, which is the retroactive grant, and then this read. Sheets read the same payload, so opening one costs no call. | One aggregation for three clients, as with the wraps (D13 there). |
| D15 | **Snapshots for resilience.** Unlike the wraps, which return ids that the Path feed resolves, this payload carries the crossing pebble's display fields: moment id, name, `happened_at`, emotion id, intensity and positiveness. Old pebbles are not in the feed's cache, and a sheet must render from the payload alone. | Clients render with resilience. |
| D16 | **Emotions, domains and the six firsts are never public.** `get_public_profile` drops `emotion_first`, `domain_first`, `first_stone` and `set_complete` from the shelf and from `achievements_count`. Public at most: pebbles, glyphs carved, glyph sales, the first five and the blue pebble. | Privacy consensus. Today's projection shows every family, so this is a fix as well as a rule. |
| D18 | **The owner's Profile shelf hides the private families too, and is served.** `get_achievements` returns `shelf`, the six most recent unlocks outside `emotion_first`, `domain_first`, `first_stone` and `set_complete` (newest first, then `sort_order`), and `shelf_count`. Each client's Profile shelf renders it instead of reading `achievement_unlocks` itself. | Maintainer, 29 October (option A): a first sadness never sits on a shelf of trophies. The membership is server logic. |
| D17 | **Timestamps cross the boundary at whole seconds**, and dates as `YYYY-MM-DD` local to `p_tz`. Absent sections omit their keys; they are never null. Unknown enum values decode to a safe fallback on every client. | Standing cross-surface rules. |

**Out of scope** (open questions in §8):
- karma or a ceremony for a month on the rim;
- challenges and collections, where the eye slice, the phantom rule and the soundings are reserved;
- project achievements, where the rank line-up is reserved;
- glyph ladders beyond 100 and 50.

---

## 3. Server

### 3.1 Schema (one migration, part 1)

- **`achievements.family`** gains `first_stone`. Six rows, with deterministic ids `md5('achievements:first-stone-<kind>')` for `small`, `medium`, `large`, `lowlight`, `neutral` and `highlight`, in the 460s block of `sort_order`.
- **The pebble ladder** gets rows for 1500 to 10 000 in steps of 500, with deterministic ids and the 170+ `sort_order` block.
- **`achievement_unlocks.pebble_id uuid null references pebbles(id) on delete set null`**, with the backfill of D4 in the same migration.
- **`achievement_views (user_id, card text check in ('explorer','emotions','domains','pebbles','year'), seen_at timestamptz)`**, with the primary key `(user_id, card)`. RLS allows owner-only select, with no client write policies; `mark_achievements_seen` is the writer.
- **`achievement_completions (user_id, stack text check in ('emotions','domains','six_firsts'), completed_at timestamptz, pebble_id uuid null on delete set null, pulled_at timestamptz null)`**, with the primary key `(user_id, stack)`. It is write-locked the same way. The offer time is computed on read (D9), not stored.
- **`purge_account`** gains `achievement_views` and `achievement_completions` at the append marker, and **`verify-account-purge.ts`** gains their seeds and zero-row assertions in the same change. That is a standing rule; then run `db:verify:purge` by hand.
- **`admin_create_achievement`** and the admin update RPC treat `first_stone` as one-shot. They refuse karma above 0 on `emotion_first`, `domain_first` and `first_stone` with `silent_family_karma` (D7), and a catalog check backs that up. The admin app learns the two new families.

Each of those three function bodies is re-emitted whole, so diff each pairwise against its current definition before applying. This is a standing rule: `create or replace` has no merge semantics.

### 3.2 `check_achievements()` (re-emitted)

- **New arms in the case:** `first_stone` qualifies on "a pebble of that intensity or positiveness exists".
- **Crossing pebble:** each insert records `pebble_id` by the rule of D4.
- **Completions:** a statement after the unlocks records a set the first time it becomes whole.
- **Karma:** none for the silent families.
- **Return type** becomes `table (slug text, karma_granted integer, celebrate boolean)`. Changing OUT columns needs `drop function` then `create`, and the grants are re-issued.

Clients that ignore unknown keys keep working. iOS `Decodable` ignores them, and Android's JSON configs set `ignoreUnknownKeys`; check the one `AchievementsService` uses. The web TypeScript decoder must be checked too.

### 3.3 `get_achievements(p_tz text) returns jsonb` (part 2)

An absent section omits its keys; it is never null.

```jsonc
{
  "since": "2026-03-01", "days_kept": 249,
  "explorer": {
    "first_pebble": {pebble},                      // the centre; absent before the first pebble
    "first_five":   [{"slug", "done", "on", "subject": {"kind", "id", "name"}, "new"}],   // Home order
    "six_firsts":   [{"kind": "small|medium|large|lowlight|neutral|highlight", "done", "pebble": {pebble}, "new"}],
    "twenty_five":  {"count": 25, "done": true, "pebble": {pebble}},   // the 25th; count is capped at 25
    "rings_done":   ["first_five", "six_firsts", "twenty_five"],   // up to five: also "domains", "emotions"
    "completion":   {"completed_at", "offered_from", "ready", "pulled_at"}   // the six firsts; absent until whole
  },
  "emotions": {
    "count": 23, "of": 38,
    "seats": [{"seat": 0, "emotion_id", "family", "met", "on", "pebble": {pebble}, "new"}],   // 38 seats, wheel order then slug
    "families": [{"family": "joy", "met": [seats by the day met], "not_yet": [seats]}],      // the sheet's order, wheel order
    "since": "2026-03-02",
    "completion": {"completed_at", "offered_from", "ready", "pulled_at"}  // absent until whole; pulled_at absent until pulled
  },
  "domains": {
    "count": 14, "of": 18, "coverage": "most",
    "entered": [{"domain_id", "on", "pebble": {pebble}, "new"}],      // newest first
    "not_yet": [domain ids],
    "completion": {}
  },
  "pebbles":       {"count": 137, "levels": [{"threshold", "on", "pebble": {pebble}, "new"}],
                    "next": {"threshold": 250, "left": 113, "progress": 0.25},
                    "ladder": [1, 10, 25, 50, 100, 250, 500, 1000], "then_every": 500, "since": "2026-03-02"},
  "glyphs_carved": {"count", "levels": [], "next", "ladder"},          // same shape, no then_every
  "glyph_sales":   {"count", "levels": [], "next", "ladder"},
  "years": [{
    "year": 2026, "current": true, "days_target": 14,
    "months": [{"month": 1, "state": "upcoming|quiet|kept", "family": "joy",
                "moment_days": 14, "pebble_days": 13, "full_weeks": 3, "weeks_filled": 3,
                "level": 1, "progress": 0.93,
                "done": {"moments": true, "pebbles": false, "weeks": true}}],   // 12 entries
    "listed": [11, 10, 9, 8, 7, 6, 5, 4, 3],                           // the table, newest first
    "next_step": {"month": 11, "needs": "moments|pebbles", "days_left": 12, "reachable": true}   // current year, below level 2
  }],
  "seen": {"explorer": "2026-10-02T08:14:05Z"},     // when each card was last looked at
  "shelf": [{"slug", "family", "threshold", "unlocked_on"}], "shelf_count": 9   // the Profile shelf (D18)
}
// {pebble} = {"moment_id", "name", "happened_at", "emotion_id", "intensity", "positiveness"}
// "on" on every dated row is the local day its pebble happened, or the day it was recorded when that pebble is gone.
// completion = {"completed_at", "offered_from", "ready", "pulled_at"?, "pebble"?}; first_five[].on is the day its subject was made.
```

- `weeks_filled` counts the full weeks that hold what the month's next level needs: a moment below level 1, a pebble from level 1 on. A month on the rim counts pebbles.
- `progress` is in [0, 1] and is 0 at level 2. `done` says which counters reached their target, so no client compares a count with `days_target`.
- The year has no "new" marks in v1: nothing about a month is stored to compare against (open question 3).
- `new` compares the item's `unlocked_at` with that card's `seen_at`.
- `offered_from` is 06:00 in `p_tz` on the day after `completed_at`, and `ready` says whether that time has passed, so no client compares clocks.
- `reachable` is false once a full week of the current month has passed without what the next level needs, because that level can no longer be reached this month.

### 3.4 Writes

- `mark_achievements_seen(p_card text) returns void`: an upsert of `seen_at = now()`.
- `pull_ceremony(p_stack text, p_tz text) returns table (stack text, karma_granted integer)`:
  - it is exactly-once (it sets `pulled_at` where it is null) and pays the stack's completion karma from the catalog: three seeded `set_complete` rows (`complete-emotions`, `complete-domains`, `complete-six-firsts`), never unlocked by the check, and private like the contributions;
  - it raises `not_ready` before `offered_from`;
  - a second call returns no row, which clients must not read as success.

### 3.5 Freshness

Everything is computed on read, as with the wraps, so no trigger and no cache table can go stale. The only stored facts are the unlocks, the views and the completions.

---

## 4. Clients (parts 4–6)

The same rule holds on iOS, Android and web.

- **Decode and map** into display models with pure functions. Tests replay the verbatim payloads the harness dumps (`ACHIEVEMENTS_DUMP=1`), including the precision variants and explicit omissions the standing rule requires.
- **Draw** with geometry only: seat index → angle, level → radius, progress → spoke length, family → palette. Never compare a count with a threshold.
- **Localise** the semantic values: coverage tier → sentence; `next_step` → "Twelve more days with a moment…"; first-five slugs → task titles.
- **Resilience:**
  - The last payload is cached per user and shown at once (stale-while-revalidate, no blink).
  - A failed read keeps the cached page and logs it.
  - A missing section hides its card; it never shows an error.
  - An unknown enum falls back: an unknown family draws in ink, an unknown state draws `quiet`.
- **Interaction:**
  - Opening a sheet calls `mark_achievements_seen`, fire-and-forget.
  - A "ready" mark opens the ceremony, which calls `pull_ceremony`.
  - Explorer's Domains and Emotions rows push their own sheet on top.
- **Celebration:** the unlock moment (achievements design D13) shows only the rows where `celebrate` is true.

Components are factored once per platform:
- **Atoms:** the flat seal, the gauge, the skyline stage, the count cell.
- **Molecules:** the card shell (title band, margin ladder, figure), a register row, a table row.
- **Organisms:** the wheel, the rosace, the band, the skyline, the year disc.

---

## 5. Accessibility and i18n

- **Cards** are buttons; their labels are assembled from the payload, for example "Explorer: first five done, six firsts done, …".
- **The disc** reads as the server's lists: "On the rim: August. On the inner circle: …".
- **Copy:**
  - Every sentence lives in each surface's catalog, in English and French.
  - Months and numerals are localised.
  - Coverage tiers and next steps are plural-aware; French "many" forms follow the Android lesson in #1121.
- **Targets:** rows are at least 44 pt. Colour is never the only signal: the level is a position, and success is a ring plus position.

---

## 6. Verification

There is a new anon harness, `verify-achievements.ts`, with its `db:verify:achievements` script and a workflow step in the same change.

It proves:
- **Six firsts:** each `first_stone` row unlocks independently, so three pebbles earn all six.
- **Crossing pebbles:** pebble ids follow D4, and so does the backfill.
- **Silent families:** `celebrate` is false for them and for a heavy crossing.
- **Karma:** none for contributions.
- **Completions:** recorded once; `pull_ceremony` is gated by `offered_from` and runs exactly once.
- **"New" flags:** the baseline holds with no `seen_at`, and items count as new after `mark_achievements_seen`.
- **Coverage tiers** sit at their boundaries.
- **The year disc** matches the oracle on fixed and seeded random years, including full weeks, the current month and tie-breaks.
- **Public profile:** it no longer shows the private families.

Plus:
- `db:verify:purge` covers the two new tables and is run by hand.
- The client unit tests replay the dumped payloads.

---

## 7. Parts (one branch and PR each, `gh stack`)

1. **db: the model.** The family, the ladder seed, `pebble_id` and its backfill, the views and completions tables, the purge append and its harness, the admin one-shot list and forced karma, and the `check_achievements()` re-emission. Then regenerate types.
2. **db: the read.** `get_achievements`, the oracle, the harness and the payload dump.
3. **db: the writes and the public profile.** `mark_achievements_seen`, `pull_ceremony`, and `get_public_profile` without the private families. This part's harness covers them.
4. **iOS:** the page, the five sheets, the ceremony pull, and the celebration filter on `celebrate`.
5. **Android:** the same, mirrored 1:1.
6. **Web:** the same.

Issues: #1305 back-end (parts 1–3), #1306 iOS (4), #1307 Android (5), #1308 web (6), all in M65 · Achievements. On the Arkaik map, `V-achievements`, `DM-achievement` and `API-get-achievements` move to `development` as each part starts.

---

## 8. Open questions

1. **Days kept** starts at the account's creation (D2). Should it start at the first moment instead? A backdated memory would then stretch it.
2. **A month on the rim:** does it pay karma or offer a ceremony? This spec says no for v1.
3. **The current month can lose its level** if a full week ends empty. This is computed on read, so it does not stick. Accept that, or store the month's high-water mark?
4. **Hidden moments** (moderation `hidden_at`) count toward the year disc, as they do in the wraps. Confirm.
5. **Glyph ladders** stop at 100 carved and 50 sales. Should they continue, as the pebbles do every 500?
6. **The emotions' seat order inside a family** is by slug, because the table has no order column. Is a curated order wanted?
