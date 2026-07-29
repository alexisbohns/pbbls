# Account deletion — backend design (M46, #631)

Design doc for milestone **M46 · Account deletion**, backend half. Parent spec:
`2026-07-28-store-launch-roadmap.md` §M46. Client entry points (web/iOS/Android
settings) are follow-up issues in the same milestone and consume the contract
in D6.

Account deletion is a store hard blocker (Play policy + Apple 5.1.1(v)). The
cascade is non-trivial: sold glyphs are referenced by other users'
entitlements, `glyph_entitlements.karma_event_id` is a no-cascade FK,
`souls.glyph_id` is `ON DELETE RESTRICT`, `glyph_submissions.submitter_id` was
`NOT NULL`, and media lives under per-user storage prefixes.

## Shipped pieces

| Piece | Path |
|---|---|
| Migration (purge RPC + submitter detach) | `packages/supabase/supabase/migrations/20260729201326_account_deletion_purge.sql` |
| Edge function (orchestrator) | `packages/supabase/supabase/functions/delete-account/index.ts` |
| Remote acceptance test | `packages/supabase/scripts/verify-account-purge.ts` |

## D1 — Anonymize predicate: "externally referenced", wider than "sold"

The roadmap says *anonymize sold glyphs*. The implemented predicate anonymizes
(`user_id = null`) any glyph of the deleting user that is referenced from
outside their account:

- entitlements from **other** users (= sold), or
- **other** users' souls (`souls.glyph_id` is `ON DELETE RESTRICT`), or
- **other** users' pebbles (`pebbles.glyph_id`, NO ACTION), or
- **other** users' profiles (`profiles.glyph_id`, SET NULL — included so a
  counterparty's avatar doesn't silently vanish), or
- a `domains.default_glyph_id` (NO ACTION).

Everything else is deleted. Why wider than "sold": `admin_attribute_glyph` can
hand a formerly-system glyph (usable by everyone while `user_id` was null) to
a user while other users' souls already reference it. A sold-only predicate
then hard-fails on the RESTRICT FK and the purge never converges.

`user_id = null` is the pre-existing system-glyph state — `can_use_glyph()`
and the `glyphs_select` policy already treat it as usable-by-all — so buyers'
entitlements keep rendering with no schema change. Corollary: an anonymized
glyph becomes usable by every user, which is why it is also **delisted**
(`listed = false`) in the same transaction (`buy_glyph` would otherwise sell
it with no payout — it skips the credit when the owner is null).

## D2 — Orchestration order and resume matrix

`delete-account` (identity from the forwarded JWT, never from the body) runs:

1. `purge_account(p_user_id)` — one transaction, every statement scoped to
   `p_user_id`, so re-runs match zero rows (idempotent by construction).
2. Empty storage prefix `pebbles-media/{user_id}/` — BFS walk (`list()` is
   non-recursive and paginated), batched `remove()`, re-sweep until an empty
   pass (removing while paginating can skip entries).
3. `auth.admin.deleteUser(user_id)` — **last**, because the auth row is the
   caller's ability to retry.

| Failure point | Re-run behaviour |
|---|---|
| during 1 | transaction rolled back; nothing changed |
| during 2 | purge no-ops; walk resumes where it stopped |
| during 3 | purge + walk no-op; deleteUser retried |
| after 3 | gateway accepts the signed JWT but `auth.getUser()` finds no user → 401 = converged |

If the user's JWT expires mid-failure, the service role finishes manually
(`purge_account` + `deleteUser`).

## D3 — Cascade tables are still deleted explicitly

`log_reactions`, `wallet_balances`, `bounces` cascade from `auth.users`, but
the purge deletes them anyway: RPC success must mean "all personal rows gone"
even when `deleteUser` is the step that failed. The wallet/bounce triggers are
`AFTER INSERT` only (`20260629193636`, `20260501000004`), so deleting
`karma_events` fires nothing.

## D4 — Submitter detach cases

`glyph_submissions.submitter_id` dropped `NOT NULL`. Three cases in the purge:

1. **Kept (anonymized) glyphs**: submission delisted + `submitter_id = null` —
   the approved row survives as audit trail. Delist runs **before** the detach
   because it filters on `submitter_id`.
2. **Reattributed glyphs** (`admin_attribute_glyph` moved ownership away):
   catch-all `submitter_id = null` without delisting — the listing legitimately
   belongs to the new owner.
3. **Reviewer** (admin self-deletion): `reviewed_by = null` everywhere.

`admin_list_glyph_submissions` left-joins `auth.users`, so detached rows stay
visible in the admin with a null submitter email.

## D5 — Standing extension rule

Every later milestone appends its new user-owned tables (drafts, unlocks,
connections, invites, blocks, seams, pairs, whispers, reports, …) to:

1. the numbered sections of `purge_account` (an `>>> APPEND HERE <<<` marker
   sits in section 4), and
2. the seed + zero-row assertions of `verify-account-purge.ts` — the script is
   the regression harness for this rule.

## D6 — Client contract (web/iOS/Android settings entries)

- `POST /functions/v1/delete-account`, no body, caller's JWT in
  `Authorization`. Responses: `200 { ok: true, purged }`,
  `401 { error: "not_authenticated" }`, `500 { error }` (retry is always safe).
- On 200 the client signs out **locally** (the server session is already
  gone — a global sign-out round-trip has nothing to revoke) and navigates to
  its signed-out root.
- Confirm copy must say: permanent, everything is deleted, glyphs other
  pebblers bought stay available to them without the user's name, cannot be
  undone. French UI copy is vous-form (app register; "Tu" is Lab-Note only).
- Destructive confirm + easy to find in Settings (Apple 5.1.1(v)).

## D7 — Verification

`packages/supabase/scripts/verify-account-purge.ts` (Deno) runs the roadmap §6
acceptance test against the **remote** project with two throwaway users it
mints and cleans up itself:

```
SUPABASE_URL=… SUPABASE_SERVICE_ROLE_KEY=… SUPABASE_ANON_KEY=… \
  deno run --allow-env --allow-net packages/supabase/scripts/verify-account-purge.ts
```

(Keys come from `npx supabase projects api-keys --project-ref <ref>`.) The
2026-07-29 run: **33/33 passed** — all seller rows gone across 14 tables +
4 cascade tables, sold glyph anonymized with strokes intact, submission
approved/delisted/detached, buyer entitlement + favourite survive, unsold
glyph deleted, storage prefix empty, auth user gone, `purge_account` re-run
all-zero, buyer deleted through the same path.

## D8 — Operational notes

- Types are regenerated with `npm run db:types:remote` (the plain `db:types`
  targets Docker `--local`, which this machine does not run, and truncates
  `database.ts` on failure). Do not "fix" it back.
- Deploys are manual: `supabase db push` + `npx supabase functions deploy
  delete-account` (remote-first testing; deploy precedes merge by design —
  the acceptance test can only run against live infra).
- Procedural: an admin should ensure at least one **other** admin exists
  before deleting their own account (`is_admin` gates the admin app; the purge
  handles the data either way).
- Known accepted race: a `buy_glyph` committing inside the millisecond window
  between the kept-set computation and the glyph delete can leave a seller
  `glyph_sale` event that blocks `deleteUser`; the re-run converges. The
  same-transaction delist closes the window for later buys. `FOR UPDATE` on
  the user's glyph rows is the future hardening if it ever bites.
