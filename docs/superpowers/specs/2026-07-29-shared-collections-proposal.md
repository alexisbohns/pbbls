# Shared collections ("memory walls") — proposal

**Status: proposal, not scheduled.** This document does not modify the store-launch roadmap (`2026-07-28-store-launch-roadmap.md`, M45–M57) and cuts no issues. It captures the intent and the pragmatic build paths for a post-roadmap candidate milestone — referred to below as **M58** — so the social milestones already in flight (M49–M53) can land the seams this feature will need, at zero extra cost to the launch.

Parent spec: `2026-07-28-store-launch-roadmap.md` §M49, §M50, §M51, §M53, §M56.

## 1. Intent

A **shared collection** is a collaborative wall of memories built by several users together:

- It is a collection with **membership** — more than one user belongs to it.
- Each member **pushes their own pebbles** onto the wall. Nobody writes anyone else's memories; the wall is a curated union of individually-owned pebbles.
- The wall can be **browsed** like a collection today, and eventually **staged** — presented as one long scrollable story ordered by `happened_at`, in the spirit of the wrap/cairn rituals (`DM-cairn`, `F-weekly-wrap` — both idea-stage).
- Visibility is layered: members-only first; link-shared or public walls are a later, deliberate step.

North-star use case: **two connected people building a birthday wall of their shared memories.** Two members, members-only visibility, both users mutually connected. Every design choice below is weighted toward shipping that case safely and small.

## 2. Position against the roadmap

This feature is intentionally **downstream of the social milestones**. It consumes exactly the primitives the roadmap already builds, and nothing it needs is missing from them:

| Roadmap foundation | What shared collections consume |
|---|---|
| M49 — Mutual connections | The user↔user primitive: `connections`, invite/QR flow, `connection_blocks`. Wall membership is offered to existing connections only (same rule as M53 pair invites). |
| M51 — Privacy grades | Real pebble visibility (`secret`/`private`/`public`) and the widened `pebbles_select`. A pebble pushed to a wall must be grade ≥ `private` — the M53 rule, verbatim. |
| M50 — Public profiles | The definer-RPC projection pattern for cross-user identity ("added by Alexis" needs a display name; `profiles` RLS is never widened). |
| M53 — Pebble pairs | The invite-requires-connection + grade-≥-private precedent, and `render_svg` as the cross-user visual (sidesteps glyph/snap RLS). |
| M56 — Compliance batch B | `content_reports` + moderation queue — the gate any *public* wall must sit behind. |

Consequence: **nothing about shared collections should be attempted before M49 and M51 land.** There is no pre-M49 shortcut worth building — a wall without connections and real privacy grades would either leak or be a throwaway.

Sequencing is the maintainer's call (see §7): the clean default is M58 as the first post-launch milestone; the aggressive option is a parallel lane after M49+M51, which would push the M56 feature freeze and is therefore not the recommendation of this document.

## 3. Background — what exists today

- `public.collections (id, user_id, name, mode check in ('stack','pack','track'))` and `public.collection_pebbles (collection_id, pebble_id)` — single-owner, no visibility column, no membership, no `added_by`/`added_at` on the junction (`packages/supabase/supabase/migrations/20260411000001_core_tables.sql:66` and `:117`).
- Collection CRUD is live on all three surfaces (web `apps/web/app/collections/`, iOS `apps/ios/Pebbles/Features/Profile/`, Android `apps/android/.../services/CollectionsService.kt`) as direct RLS-scoped single-table calls; pebble↔collection attachment flows through `create_pebble`/`update_pebble`, which enforce collection ownership explicitly because `security definer` bypasses RLS (`packages/supabase/supabase/migrations/20260415000000_pebble_rpc_collections.sql:94`).
- `pebbles.visibility` exists (`20260411000001_core_tables.sql:59`) but is **decorative until M51** — no policy, view, or RPC reads it.
- There is no user↔user read path anywhere today. The only cross-user precedents are the glyph marketplace's widened `glyphs_select` union (`20260630003348_glyph_marketplace.sql`) and the published-`logs` Lab feed.
- Standing rules that bind this design: multi-table writes are definer RPCs (`AGENTS.md`); every new user-owned table extends `purge_account` **and** `packages/supabase/scripts/verify-account-purge.ts` (decision 2026-07-29, #631); new/recreated views are `security_invoker = true` + anon revoked (decision 2026-07-29, #616); no realtime — social events surface on next app open; offline is a non-goal (#620).

## 4. Approaches considered

### A — Membership grants read ("the wall is a privacy channel")

Add `collection_members`; add a new OR-branch to `pebbles_select`: *viewer is a member of a collection containing this pebble AND the pebble is grade ≥ `private`*. Pushing a pebble onto a wall becomes, by itself, an act of sharing with the current member set.

- **For:** matches the mental model exactly ("I push it to the wall, wall members see it"). Works for any member set — no requirement that all members be mutually connected. Public walls are a natural extension.
- **Against:** widens the most sensitive policy in the app with a two-join EXISTS, on the heels of the #616 leak post-mortem. Introduces a **third consent channel** (owner grade, connection, *and now* wall membership) that users must understand — "grade `private` + in a wall" reaches people the owner never connected with. Public walls drag in Apple UGC obligations (M56 reporting surface) immediately.

### B — Ride M49+M51 wholesale ("connections-scoped wall")

Membership is invite-only among **existing connections** (M53 precedent). Pebble reads are governed *entirely* by the M51 policy — this feature adds **zero branches** to `pebbles_select`. New RLS covers only the collection row, the membership table, and the junction (member-join instead of owner-join). A wall renders whatever M51 already lets each viewer see: for a two-person wall, both members are connected by construction, so every pushed pebble (grade ≥ `private` enforced at push time) is visible to both.

- **For:** smallest possible trust delta — no new pebble read path, no new consent semantics, nothing new to explain in the privacy policy beyond "walls show you pebbles you could already see". Reuses M49 invites, blocks, and M51 grades untouched. Perfect fit for the north-star case.
- **Against:** in a wall with 3+ members who are not all mutually connected, a member sees holes where non-connected members' `private` pebbles sit. Acceptable if documented and surfaced in UI ("N pebbles from people you're not connected with"), but it is a real comprehension cost — the reason approach A exists as a phase 2.

### C — Snapshot on push ("publish a copy")

Pushing writes a frozen, denormalized entry (name, emotion color, date, `render_svg`) into a wall-entries table — the `logs`/Lab pattern. No live read on `pebbles` at all.

- **For:** zero pebble-RLS exposure; revocation = delete the row; public and anonymous walls are trivial; immune to grade changes after the fact.
- **Against:** duplicates pebble data and goes stale (rename, emotion edit, re-render); diverges from the `v_pebbles_full` read shape all three surfaces speak; "frozen copy" contradicts the product's living-diary feel; and a *silent* divergence between a pebble and its wall copy is exactly the kind of asymmetry `AGENTS.md` warns against.

## 5. Recommendation — phased, B then A

### Phase 1 (candidate M58, size M) — two-person walls on approach B

Additive schema, nothing existing is altered destructively:

- `collection_members (collection_id references collections on delete cascade, user_id references auth.users on delete cascade, role text check (role in ('owner','member')), invited_by uuid, created_at, primary key (collection_id, user_id))`. Sharing an existing collection seeds the owner row; `collections.user_id` remains the canonical owner (no ownership transfer in v1).
- `collection_pebbles` gains `added_by uuid` (backfilled to the collection owner) and `added_at timestamptz` — provenance for "added by X" and for the staged wall's ordering fallback.
- RLS: single `for all to authenticated` policies with both `using` and `with check` (the sanctioned `pebble_drafts` pattern, not the flawed four-policy style — see `20260729213348_pebble_drafts.sql`). `collections` and `collection_pebbles` move from owner-checks to member-join checks; writes stay RPC-only where multi-table.
- Definer RPCs (all multi-table, per house rule): `share_collection(p_collection_id, p_user_id)` (owner-only; requires an existing `connections` row; checks `connection_blocks` both directions; seeds membership), `leave_collection`, `remove_collection_member` (owner-only), `push_pebbles_to_collection(p_collection_id, p_pebble_ids uuid[])` (caller owns each pebble, is a member, **grade ≥ `private` enforced** — M53 rule), `remove_collection_pebble` (adder or wall owner), `get_collection_members(p_collection_id)` (member-scoped projection of display name + avatar glyph geometry — the M50 pattern; `profiles` RLS untouched).
- `create_pebble`/`update_pebble` `collection_ids` handling extends from "collections you own" to "collections you own or are a member of" — same guard style as `20260415000000_pebble_rpc_collections.sql:94`, kept symmetric across both RPCs.
- `remove_connection` (M49) gains one statement: eject each party's membership from walls owned by the other (mirrors how it already severs seams and pairs). Blocks eject likewise.
- Account deletion: `collection_members` rows deleted; walls owned by the deleted user — see open question in §7. Both `purge_account` and `packages/supabase/scripts/verify-account-purge.ts` extended in the same PR (#631 standing rule).
- Wall read: existing M51-governed reads (`v_pebbles_full` post-F1 returns empty enrichments for non-owners automatically), ordered by `happened_at`. Cross-user visual is `render_svg` + name + emotion + date — exactly the M51 shared-pebble projection. Snaps stay out (M51 excludes them from shares in v1).
- UI ×3, deliberately thin: "share this collection" picker (connections list), member chips on collection detail, "pushed by X" attribution, and the collection detail view as-is for browsing. No new staging view yet.

### Phase 2 (later, separate spec) — bigger walls and the stage

- Approach A's membership-grants-read branch, making 3+ member walls hole-free — shipped together with its consent UX ("pushing here shares with all current and future members") and a privacy-policy paragraph.
- Public / link-shared walls, gated behind the M56 `content_reports` + moderation surface, with an anonymous definer RPC in the mold of M51's `get_shared_pebble`.
- The **staged wall**: a long-scroll story view of the wall's pebbles. This is where the wrap/cairn aesthetic belongs — one presentation engine for "a sequence of pebbles told as a story", consumed by weekly/monthly cairns *and* shared walls, rather than two parallel implementations. Spec it once, against both consumers.

## 6. What this proposal deliberately defers

- Arkaik: no nodes yet. When M58 is scheduled, add `DM-collection-member`, flow + view nodes for share/membership, and flip statuses per the arkaik skill (the map is served from the hosted project — decision 2026-07-28).
- Decision log: no entry now. The entries fall out of the M58 design doc when the milestone starts (visibility semantics, orphaned-wall policy).
- Lab Note: none — nothing user-facing ships from this document.

## 7. Open questions for the maintainer

1. **Sequencing.** First post-launch milestone (default), or pulled into a parallel lane after M49+M51 at the cost of pushing the M56 feature freeze? The birthday matters; so does the store date.
2. **Orphaned walls.** When a wall owner deletes their account: transfer ownership to the oldest member, or dissolve the wall (memberships die, each member's pebbles simply return to being theirs)? Dissolution is simpler and loses nothing irreplaceable — pebbles never belonged to the wall.
3. **Naming.** "Shared collection" (continuity) vs "wall" (evocative, matches the staged presentation). The `mode` check (`stack`/`pack`/`track`) also needs a stance: is a shared wall a fourth mode, or orthogonal to mode?
4. **Snaps on walls** — M51 excludes snaps from shares in v1; walls inherit that. Revisit only with a real signed-URL design.

## 8. Non-goals

- Realtime presence, live co-editing cursors, or push notifications — the no-realtime decision stands; walls update on next app open.
- Comments or reactions on wall pebbles (that is a different feature with its own moderation surface).
- Follower-style discovery of walls — membership is invitation through connections, full stop.
- Any pre-M49 interim mechanism (email exports, shared accounts, public-by-default collections). Building the wall on the real privacy primitives is the whole point.
