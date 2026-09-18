# Glyph system marker — separating "first-party seed" from "ownerless" (M43)

**Date:** 2026-09-19
**Issue:** [#872](https://github.com/alexisbohns/pbbls/issues/872)
**Surface:** `packages/supabase` + `apps/android`. iOS and web need no change (see D6).
**Related:** [#870](https://github.com/alexisbohns/pbbls/issues/870) — the undecodable fixture rows that surfaced this. Fixed separately.

## 1. Why

`purge_account` deliberately keeps a glyph that has been sold and anonymizes it:

```sql
-- 20260729201326_account_deletion_purge.sql:93
update public.glyphs set user_id = null where id = any(v_kept);
```

Keeping the row is right — buyers hold entitlements, and artwork they paid karma for must not vanish. The defect is that `user_id is null` is simultaneously the definition of **system glyph** everywhere else in the schema:

| Reader | Behaviour on `user_id is null` |
|---|---|
| `can_use_glyph` (`20260712000000`) | `g.user_id = p_user or g.user_id is null` → **anyone may attach it**, no entitlement |
| `glyphs_select` RLS (`20260630003348`) | readable by every authenticated user |
| android `GlyphService.list()` | `it.userId == null \|\| it.userId == me` → free in the picker |
| android `GlyphMarketService.listMine()` | `rest.filter { it.userId == null }` → free in every user's Mine tab |

So the moment a seller deletes their account, the glyph other people paid for becomes free for everyone, and indistinguishable from a first-party seed. The buyers' entitlements still exist; they just stop meaning anything. It also grows the system set with third-party artwork no one curated.

`purge_account`'s stated intent ("the buyer's glyph still renders") is satisfied, and `verify-account-purge.ts` asserts exactly that and passes. Nothing anywhere asserts that an anonymized glyph stays *paid*. That missing assertion is why this shipped.

### What the issue's reader table got wrong, in our favour

The purged glyph does **not** leak into the Commu tab. `purge_account` sets `listed = false` on the kept submission, and `v_glyph_market` filters `status = 'approved' and listed`. The leak is exactly `can_use_glyph` plus the two Android client filters.

## 2. The shape

A dedicated marker column, with every reader re-keyed off it.

```sql
alter table public.glyphs add column is_system boolean not null default false;

update public.glyphs g set is_system = true
 where g.user_id is null
   and not exists (select 1 from public.glyph_submissions s where s.glyph_id = g.id)
   and not exists (select 1 from public.glyph_entitlements e where e.glyph_id = g.id);

create index glyphs_is_system_idx on public.glyphs (is_system) where is_system;
```

### D1 — A stored marker, not a derived predicate

Two alternatives were considered and rejected.

**Submission-driven** (`user_id is null and no glyph_submissions row`) needs no migration and no backfill, and the `glyphs_update` policy already blocks self-anonymizing (a policy with no `WITH CHECK` reuses its `USING` clause, so `user_id = null` fails the check). But it encodes "system" as "never listed", which is a coincidence of today's data rather than a fact about the row. A glyph published by an admin and later anonymized would read wrong, and the predicate has to be repeated verbatim in four places including a Kotlin client that would need an extra embed to evaluate it.

**A sentinel "retired creator" user** keeps every existing `user_id is null` reader correct untouched, but requires a real `auth.users` row that must never log in, and `buy_glyph` would credit karma to it on any future sale.

The column is explicit, greppable, cheap to index, and survives changes to purge semantics. Its cost is a backfill and a write guard, both addressed below.

### D2 — The backfill excludes marketplace history, and that is what makes it safe at apply time

The two `not exists` clauses look redundant against today's data. They are not: they are what keeps the migration correct if a seller purges between authoring and deploy.

Every sold glyph carries an approved `glyph_submissions` row by construction — `buy_glyph` requires `status = 'approved' and listed`, and `purge_account` keeps the row, flipping only `listed` and detaching `submitter_id`. `glyph_submissions.glyph_id` cascades only when the glyph itself is deleted. So "has a submission" is a reliable witness for "was in the market", and a purged seller's kept glyph is excluded automatically. The entitlement clause is belt-and-braces.

A blanket `where user_id is null` would have been correct against the data as probed and silently wrong for anyone who deleted their account in the deploy window.

**Probed state of the linked project, 2026-09-19** — 20 null-owner glyphs, all genuinely first-party:

- 18 × `domain:<slug>` seeds (`20260415000001`), each also a `domains.default_glyph_id`
- `4759c37c-68a6-46a6-b4fc-046bd0316752` — the default glyph, named "Hand"
- `2559e4b0-0240-4c92-a06a-45da8ee087de` — "Barking Dog", created 2026-04-23, two months before the marketplace existed; no submission, no entitlement, referenced by 1 soul and 2 pebbles

Zero null-owner glyphs carry a submission or an entitlement, so no seller has purged a sold glyph yet. The backfill marks exactly these 20.

"Barking Dog" is the reason the backfill is not a provenance allowlist (`id = default ∪ domains.default_glyph_id ∪ achievements.glyph_id ∪ name like 'domain:%'`). It matches none of those clauses, and excluding it would strip a glyph that three live rows already point at.

### D3 — `is_system` is not user-settable

`glyphs_insert` forces `user_id = auth.uid()`, so no client can create a null-owner row. Nothing stops `{user_id: me, is_system: true}`, and `glyphs_update` has no `WITH CHECK` — Postgres reuses `USING`, and a self-promotion satisfies `user_id = auth.uid() and not submitted and not entitled`. Left open, any user could donate their own glyph into the curated system set, bypassing the admin queue: the same class of defect this change exists to close.

```sql
create or replace function public.enforce_glyph_system_flag()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.is_system is distinct from coalesce(old.is_system, false)
     and auth.uid() is not null
     and not public.is_admin(auth.uid()) then
    raise exception 'is_system is not user-settable' using errcode = '42501';
  end if;
  return new;
end; $$;

create trigger glyphs_system_flag_guard
  before insert or update on public.glyphs
  for each row execute function public.enforce_glyph_system_flag();
```

`coalesce(old.is_system, false)` makes one predicate cover both `INSERT` (`old` is null) and `UPDATE`. `auth.uid() is null` admits migrations and the service role; `is_admin` admits `admin_set_domain_glyph` and `admin_set_achievement_glyph`, which run `security definer` but keep the caller's `auth.uid()`.

Column-level `REVOKE` was the first instinct and does not work: Postgres cannot revoke a single column out of a table-level grant, so it would mean revoking table `INSERT`/`UPDATE` and re-granting every other column by name — a list that silently rots as columns are added.

### D4 — New system glyphs are born system

`admin_set_domain_glyph` (`20260731090100`) and `admin_set_achievement_glyph` (`20260730150000`) each insert `user_id = null` on their first-glyph branch. Both gain `is_system` to their column list. Their replace-in-place branches touch only `strokes`/`view_box` and stay verbatim.

`publish_admin_glyph` is **not** one of these: it writes `user_id = v_user`, so an admin-published market glyph is owned, not system, and must stay that way — it is sold through `buy_glyph` like any other.

Both functions are re-emitted whole via `create or replace`. Neither carries in-body append markers, and neither is re-emitted anywhere else in this change, so the pairwise-diff rule in `CLAUDE.md` has nothing to union here.

### D5 — The two server-side readers

```sql
-- can_use_glyph
--   g.user_id = p_user or g.user_id is null
--   → g.user_id = p_user or g.is_system

-- glyphs_select
--   user_id = auth.uid() or user_id is null or approved-submission or entitled
--   → user_id = auth.uid() or is_system     or approved-submission or entitled
```

The RLS swap is safe for the purged glyph precisely because `purge_account` keeps `status = 'approved'`: the approved-submission clause still grants the read, so buyers' pebbles and souls keep rendering and the glyph stays browsable. Only the *attach* closes.

`can_use_glyph` is `stable security definer` and is called from `create_pebble`, `update_pebble` and the `souls_glyph_usable` trigger. Re-emitting it alone fixes all three call sites; none of those three functions is touched.

### D6 — iOS and web need no client change

Both already filter to `user_id = me` on the surfaces that offer attachable glyphs:

- iOS `GlyphMarketService.listMine()` → `.eq("user_id", userId)`; `GlyphPickerContent` offers Mine ∪ Owned and routes Commu through a buy (#547)
- web `supabase-provider.ts` → `.from("glyphs").select("*").eq("user_id", this.userId)` for `marks`, and `v_glyph_market` with `owned = true` for `entitledMarks`

Neither ever offered a null-owner glyph, which is the D7 deviation the M43 design already names. A purged seller's glyph is excluded from both by the same `eq` that excludes the seeds. The cross-surface rule is satisfied by checking, not by editing.

### D7 — Android keeps offering system glyphs; only the key moves

The D7 deviation stays a deviation. `GlyphService.list()` and `GlyphMarketService.listMine()` swap `userId == null` for `isSystem`, and nothing else about Android's behaviour changes. Resolving the iOS/Android divergence is a product decision that does not belong in a bug fix, and `can_use_glyph` has to be fixed server-side regardless.

## 3. Client changes (`apps/android` only)

- `Glyph` and `MineGlyphRow` gain `@SerialName("is_system") val isSystem: Boolean = false`. The default is load-bearing for the same reason `userId`'s is: column-restricted embeds such as the pebble-detail `glyphs(id, name, strokes, view_box)` omit the key entirely, and an absent key must still decode.
- `MineGlyphRow.toGlyph()` carries `isSystem` through.
- `GlyphService.list()` — `it.userId == null || it.userId == me` → `it.isSystem || it.userId == me`.
- `GlyphMarketService.listMine()` — `rest.filter { it.userId == null }` → `rest.filter { it.isSystem }`.
- Both `Columns.raw(...)` lists gain `is_system`.

`OwnedGlyphRow` embeds a `MineGlyphRow` and inherits the field; `MarketGlyphRow` reads `v_glyph_market`, which has no `is_system` and does not need one (the view is approved+listed listings, never seeds).

## 4. Proof

### `verify-account-purge.ts` (service-role, manual — `db:verify:purge`)

On the existing seller/buyer fixture, after the purge:

- `can_use_glyph(soldGlyph, stranger)` is `false` for a third throwaway user, and that user's `create_pebble` with the glyph raises `42501`
- `can_use_glyph(soldGlyph, buyer)` is still `true` — the entitlement still means something
- the kept row reads `is_system = false`

This is the assertion the issue names, and it belongs here because this harness owns the purge fixture.

### `verify-glyph-usability.ts` (new, anon-key — wired into `db:verify`, so CI gates it)

`verify-account-purge.ts` needs the service role and is not in CI. The guard itself deserves a gate that runs on every push. Modelled on `verify-profiles-privileged-guard.ts`: signs up two throwaway users, deletes them through the real `delete-account` edge function, cleans up on failure, needs no service-role key.

1. B cannot attach A's unsold glyph — `create_pebble` raises `42501`, and a direct `souls` insert is rejected by `souls_glyph_usable`
2. The guard did not over-block — B *can* attach `4759c37c` (system) and B's own carve
3. B cannot set `is_system = true` on their own glyph — the error is raised **and** the stored value re-reads as `false`. Both halves matter: a 0-row RLS filter would leave the value unchanged while proving nothing (the lesson `verify-profiles-privileged-guard.ts` already encodes)
4. B cannot read A's unsubmitted glyph

The purged-glyph scenario itself stays in `verify-account-purge.ts` — reaching that state needs the service role, and splitting it would give the CI harness a dependency it cannot satisfy.

### Types

`npm run db:types:remote --workspace=packages/supabase` after `db:push`, committing `packages/supabase/types/database.ts`. The `:remote` variant, not `db:types` — the latter targets `--local` and truncates the file on failure.

## 5. Deliberately out of scope

**`glyphs.is_custom` drifts the same way.** It is `generated always as (user_id is not null) stored` (`20260501000006`), so a purged seller's glyph flips `is_custom` true → false and the analytics "% of pebbles with a custom glyph" metric reclassifies bought artwork as system-seeded. Same root cause, different blast radius: redefining a generated column means `DROP COLUMN`, which cascades into `v_analytics_pebble_volume_daily`, `v_analytics_pebble_enrichment_daily` and their RPCs. Admin-facing analytics only, no user impact. Follow-up issue, referenced by number.

**`profiles.glyph_id` has no `can_use_glyph` guard.** `pebbles` is guarded through its RPCs and `souls` through the `souls_glyph_usable` trigger; `profiles` is guarded by neither, so a profile glyph can be set to any glyph the user can *read*, which under the browse-friendly `glyphs_select` includes unbought community glyphs. Pre-existing and orthogonal to this change — the RLS swap neither widens nor narrows it. Follow-up issue.

Both are referenced by GitHub issue number. Never by Kritik finding id: the Arkaik App resolves every `F-…` id it finds in a PR body and does not distinguish "closes" from "mentions" ([arkaik#440](https://github.com/alexisbohns/arkaik/issues/440)).

## 6. Risks

| Risk | Mitigation |
|---|---|
| Backfill marks a sold glyph as system | The two `not exists` clauses; verified against probed prod state (D2) |
| A legitimate system glyph is missed and becomes unattachable | Probe enumerated all 20 and all 20 match the predicate; "Barking Dog" is the one that a provenance allowlist would have missed |
| Android decode breaks on embeds lacking `is_system` | `Boolean = false` default, same pattern `userId` already uses |
| `create or replace` silently drops an append | Only `can_use_glyph`, `admin_set_domain_glyph` and `admin_set_achievement_glyph` are re-emitted; none has append markers, none is re-emitted twice here (D4) |
| Migration applied without regenerating types | `db:types:remote` is an explicit plan step; `supabase.yml` typechecks on `packages/supabase/**` |
