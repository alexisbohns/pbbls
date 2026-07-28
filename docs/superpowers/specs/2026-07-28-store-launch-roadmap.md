# Store-launch roadmap — the road to v1.0 on the App Store & Play Store

> Planning spec for everything that ships before the public v1.0 store release: achievements, public profiles, mutual connections, secret notes, soul seaming, aggregated pebbles, per-pebble privacy grades, drafts + local autosave, and full Apple/Google compliance. Built from a three-way codebase audit (data layer, product surfaces, store-compliance state) with every headline claim verified against `packages/supabase/supabase/migrations/`, `docs/arkaik/bundle.json`, and the parity audit (`2026-07-16-android-parity-audit.md` §4–5). **Maintainer decision: all ten vision points gate v1.0 — nothing here is post-launch.** This doc is the planning input for milestones M1–M12 below; each milestone gets its own design doc/spec when it starts.

## 1. Product decisions already made (maintainer, 2026-07-28)

1. **Secret notes ("whispers")** — server-side application-layer encryption (Supabase Vault key + pgcrypto) with author-only RLS. Explicitly **not** E2E: honest product wording is "encrypted, never visible to anyone but you", and the privacy policy must say "encrypted at rest, readable by the service".
2. **Soul seaming** — private one-way mapping. The seamed user is never notified and never sees the seam; it lives only in the owner's data.
3. **Launch gate** — everything before v1.0. Consequence: UGC/social review requirements (report, block, moderation, EULA clause) must be satisfied in the launch build.
4. **Connection discovery** — invite link / QR only. No search, no directory, no follower graphs. Symmetric connections only: accepting an invite *is* the mutual consent.

## 2. Verified starting position

What the vision can build on:

- **Arkaik already names the gamification surface**: `V-achievements`, `DM-achievement`, `API-get-achievements`, `F-gamification` are `idea` nodes in `docs/arkaik/bundle.json` — zero code exists.
- **`pebbles.visibility` exists but is decorative** (`20260411000001_core_tables.sql:59`): `text not null default 'private'`, no CHECK constraint, threaded through `create_pebble`/`update_pebble`/`v_pebbles_full` and a UI badge, but **no RLS policy reads it**. The analytics POC already sketches the three-value model (`secret | private | public`).
- **The karma ledger has a reserved, never-emitted `grant` reason** in `karma_events_reason_check` — the natural hook if achievements ever pay karma.
- **Ripple/bounce/assiduity are the "evolutive rings" material**: `v_ripple` (level 0–6 from 28-day pebble count), `bounces` (0–7 from active days), `get_profile_engagement(p_tz)` (28-day boolean grid) — all correctly `auth.uid()`-scoped.

What blocks it:

- **No user↔user primitive exists anywhere.** All user tables RLS to `user_id = auth.uid()`; `profiles_select` is owner-only; souls are explicitly *not* users. Connections are pure greenfield and gate seaming, pairs, and the `private` grade.
- **`v_pebbles_full` is a definer-rights leak.** Defined without `security_invoker` and without an `auth.uid()` filter (`20260411000002_views.sql:10`, recreated unchanged in `20260701114205_drop_glyph_shape.sql:289`); only the client's `.eq("user_id", …)` scopes it. Same vulnerability class as the patched `v_ripple` (`20260516000001`). **Hard gate: fix before widening any pebble visibility.**
- **Store compliance blockers** (parity audit §4, confirmed): no account deletion on any surface (Play + Apple hard blocker; cascade is non-trivial — sold glyphs owned by other users' entitlements, `glyph_entitlements.karma_event_id` FK without cascade, storage prefixes); no `PrivacyInfo.xcprivacy`; no Play Data Safety artifacts; no age gate (GDPR-K France = 15); consent timestamps collected at signup but never persisted by `handle_new_user()`; privacy policy describes features that don't exist (Therapist, Decisions, Cairns) and omits the marketplace and karma; no in-app report/block for UGC (Apple 1.2); no password reset or email change; email confirmations disabled.
- **Known latent defects** that become dangerous under this roadmap: `create_pebble` media-quota lookup matches `profiles.id` instead of `profiles.user_id` (quota never fires); web `QuickPebbleEditor` falls back to `emotion_id || "serenity"` (a slug, fails the uuid cast); the TS karma mirror `apps/web/lib/data/karma.ts` drifted from SQL `compute_karma_delta` (missing the 4-card and 10-total caps).

House constraints the design honors throughout: no Postgres enums (text + CHECK); multi-table writes are `security definer` RPCs; new views use `with (security_invoker = true)`; no realtime (explicit-fire Sonner pattern); no push infrastructure; three hand-written native clients by design (no codegen bridge); every migration is followed by `npm run db:types --workspace=packages/supabase`.

## 3. Milestones

Sizing per repo triage: S ≤ ~150 LOC, M ≤ ~500, L = cross-app/schema. House cadence per L milestone: migration + types regen → web reference implementation → iOS → Android, with Arkaik updates and Lab Notes per user-facing PR.

| M | Title | Size | Gated by | Gates |
|---|---|---|---|---|
| M1 | Foundation & leak fixes | M | — | everything |
| M2 | Account deletion | L | M1 | store submission; extended by every later milestone |
| M3 | Drafts & local autosave | L | M1 | — |
| M4 | Achievements | L | M1 | M6 content |
| M5 | Mutual connections | L | M1 | M6b `private` tier, M7, M8 |
| M6 | Public profiles | L | M1 (full content: M4) | — |
| M6b | Privacy grades | L | M1 (UX coherence: M5) | M8 |
| M7 | Soul seaming | M | M5 | — |
| M8 | Aggregated pebbles ("pairs") | L | M5 + M6b | — |
| M9 | Secret notes ("whispers") | M/L | M1 | — |
| M10 | Compliance batch A | M each | — | — |
| M11 | Compliance batch B | M | feature freeze (M3–M9) | M12 |
| M12 | Store readiness | M | M11 | ship |

**Critical path: M1 → M5 → M8 → M11 → M12**, with M2 running alongside the whole program. Parallel lanes fill against it: {M3}, {M4 → M6}, {M6b}, {M7}, {M9}, {M10}.

### M1 — Foundation & leak fixes

- **F1** Recreate `v_pebbles_full` `with (security_invoker = true)` (pattern: `v_glyph_market`, `20260630003348`). Audit all remaining views for the definer-rights class.
- **F2** `handle_new_user()` persists `terms_accepted_at` / `privacy_accepted_at` from `raw_user_meta_data` (GDPR accountability bug).
- **F3** Fix `create_pebble` quota lookup (`profiles.id` → `profiles.user_id`).
- **F4** Fix the web `"serenity"` emotion fallback; give the editor a real "no emotion yet" state (drafts need it anyway).
- **F5** Decisions-log entry: offline is a non-goal (asked for by audit §4.6; prevents drafts/autosave being misread as offline mode).
- **F6** Re-align `apps/web/lib/data/karma.ts` with SQL `compute_karma_delta`.

### M2 — Account deletion

- `purge_account(p_user_id)` security-definer RPC (service-role only) + a `delete-account` edge function orchestrating SQL purge → storage prefix `pebbles-media/{user_id}/` → `auth.admin.deleteUser`. Idempotent and resumable — re-running after a partial failure converges.
- **Anonymize, don't delete: sold glyphs.** Set `user_id = null` (the existing system-seed state; buyers' entitlements keep rendering) and delist their submissions (`listed = false`, preserving the approved audit trail). Delete everything else in FK order — the user's own entitlements before their `karma_events` (no-cascade FK). Ledger rows are per-user; net-zero transfers survive in counterparties' rows.
- Settings entry point on all three surfaces + web, destructive confirm, easy to find (Apple requirement).
- **Standing rule: every later milestone appends its new tables to the purge** (drafts, unlocks, connections, invites, blocks, seams, pairs, whispers, reports).

### M3 — Drafts & local autosave

- **Separate `pebble_drafts` table with a jsonb `payload`** (the exact `create_pebble` payload shape, partial). Decisively *not* a status column on `pebbles`: five NOT NULL semantic columns would need relaxing; every view/analytics migration would need a forever `status` filter; drafts must earn zero karma (`create_pebble` is the only emitter and a draft never touches it); coalesce-based `update_pebble` can't null scalars but wholesale jsonb replace can; no `render_svg` exists pre-publish; and autosave wants the same partial payload.
- Owner-only CRUD RLS, direct client calls (single-table convention). Publish = the normal `compose-pebble` edge flow with the draft's payload, then delete the draft on success.
- Local autosave, same payload shape: web `localStorage` (color-world precedent), debounced, restore-on-mount prompt; iOS file/UserDefaults snapshot; Android symmetric. **Local snapshot = crash/offline insurance for the open composer; server `pebble_drafts` = intentional "save as draft". No merge logic, no cross-device local sync.** Cleared on publish or server-draft save. The service worker stays untouched (Supabase remains NetworkOnly — cached-401 precedent).
- Composer ×3: skippable mandatory fields in draft mode only, drafts list, resume-to-composer hydration, quick-capture ("just a name" → draft), placeholder chip for draft rows.

### M4 — Achievements

- `achievements` reference table seeded by migration (deterministic-id convention): `slug`, `family` CHECK in (`pebble_count`, `emotion_first`, `domain_first`, `first_collection`, `first_glyph`, `first_soul`, `glyph_count`, `glyph_sales`), nullable `threshold` (pebble ranges; glyph 10/25/50/75/100; sales tiers), nullable `emotion_id`/`domain_id`, `sort_order`. Public-read RLS like emotions/domains.
- `achievement_unlocks` with `primary key (user_id, achievement_id)` (structural idempotency) and `unlocked_at`; select owner-only; writes only via RPC.
- **Evaluation: a client-callable, idempotent `check_achievements()` definer RPC** returning newly unlocked slugs (`insert … on conflict do nothing returning`). Fired after mutations and on profile/achievements screen open — the screen-open call *is* the retroactive grant for existing users. No triggers (they'd fire during admin ops/backfills, and web glyph carving is a direct insert no RPC wraps), no cron, and `create_pebble`'s uuid return type stays untouched (three-surface contract). Badges are permanent; deletion doesn't revoke.
- Glyph-sales counts read `glyph_entitlements` joined on `glyphs.user_id` — not the karma ledger.
- **Extend the admin add-emotion/add-domain RPCs to insert the paired `emotion_first`/`domain_first` achievement row**, or the badge set silently drifts.
- Karma payout via the reserved `grant` reason: deferred — badges are cosmetic in v1 (decision-log entry).
- UI ×3: achievements grid + unlock moment (karma-pastille / Sonner explicit-fire pattern). Arkaik: flip the four gamification nodes as they ship.

### M5 — Mutual connections

- **Single-row symmetric table**: `connections (user_a, user_b, check (user_a < user_b), unique (user_a, user_b))`. No status column — accepting the invite is the mutual consent; there is no pending state between two known users.
- `connection_invites`: `inviter_id`, unique server-generated `token` (32 random bytes, base64url), `expires_at` (default 7 days), `revoked_at`. Multi-use until revoked/expired (one QR at a dinner table serves several friends); one active invite per user.
- RPCs (definer, RPC-only writes): `create_connection_invite()`, `accept_connection_invite(p_token)` (validates live, rejects self, ordered-pair insert `on conflict do nothing`, returns an inviter display *projection* — never a profiles row), `remove_connection(p_connection_id, p_block boolean default false)` (severs dependent seams and pairs in the same transaction), `get_connections()`.
- `connection_blocks (blocker_id, blocked_id)` checked by accept — cheap now, painful retrofit, and needed for Apple UGC review.
- UI ×3: invite screen (link + QR), accept flow (web `/invite/[token]` including the sign-up-first path; universal/app links on mobile), connections list with remove/block. No push — accepted connections surface on next app open (no-realtime decision).

### M6 — Public profiles

- `profiles` additions: nullable `handle text unique` (`^[a-z0-9][a-z0-9_]{1,28}[a-z0-9]$`), `public_profile boolean not null default false`; a `reserved_handles` table seeded by migration (admin-extensible without a migration); `set_handle(p_handle)` invoker RPC — the unique index settles races.
- **Never widen `profiles` RLS.** All cross-user reads go through definer-RPC projections (`profiles` carries `is_admin`, consent timestamps, quotas). `get_public_profile(p_handle) returns jsonb`, granted to `anon` and `authenticated`, null unless opted in. Projects: display name, handle, avatar glyph geometry, pebble count, ripple + bounce levels (the evolutive rings), the 28-day assiduity grid (UTC in the public variant; the owner's tz-aware `get_profile_engagement` is untouched), achievements.
- Do **not** un-scope `v_ripple` / `v_bounce` / `get_profile_engagement` — the public RPC recomputes internally for the target user.
- Web `/u/[handle]` server-rendered route (anon key server-side; OG share cards). iOS/Android: handle claim + public toggle + share sheet in settings; viewing others' profiles in-app uses the same RPC authenticated.

### M6b — Privacy grades

- Add `check (visibility in ('secret','private','public'))`. **Backfill every existing pebble to `'secret'` and flip the column + `create_pebble` coalesce default from `'private'` to `'secret'`** — existing pebbles were created under owner-only expectations; letting them become connection-visible the day connections ship would be a privacy regression.
- New `pebbles_select` policy: owner, OR `visibility = 'private'` AND a `connections` row links viewer↔owner, OR `visibility = 'public'`. Writes stay owner-only. **Enrichment tables (cards, souls joins, snaps, whispers) keep owner-only RLS deliberately**: a shared pebble exposes core + `render_svg` (the glyph is already baked into the SVG — sidesteps cross-user glyph/snap RLS entirely), name, emotion, date. Post-F1, `v_pebbles_full` returns empty enrichments for non-owners automatically.
- Public-by-link for anonymous visitors: definer RPC `get_shared_pebble(p_pebble_id)` granted to `anon` (no anon-role RLS). Link = `/p/[id]` — the uuid is 122 unguessable bits; revocation = flip the grade back; no token table in v1.
- Snaps on public pebbles: **excluded from public shares in v1** (avoids service-role signed-URL plumbing); revisit later.
- UI ×3: three-state grade selector in composers (default `secret`), grade badge in read view, share sheet for public, connection-detail "shared pebbles" list (legal under the widened RLS).

### M7 — Soul seaming

- `alter table souls add column seamed_user_id uuid references auth.users(id) on delete set null`. Souls keep strictly owner-only RLS, so the private one-way mapping needs zero policy work — the seamed user structurally cannot see it.
- `seam_soul(p_soul_id, p_user_id)` definer RPC (verifies soul ownership + an existing connection — multi-table check ⇒ RPC), `unseam_soul`. `remove_connection` nulls seams both directions; peer account deletion degrades via `on delete set null`.
- UI ×3: "seam with a connection" picker on soul detail, seamed badge, unseam. Convergence: the pair composer (M8) pre-attaches the seamed soul for that connection.

### M8 — Aggregated pebbles ("pebble pairs")

- `pebble_pairs (pebble_a unique, pebble_b unique, both on delete cascade)` link table — exactly-two semantics, `pebbles` untouched (no ×3 model churn) — plus `pair_invites (from_user, to_user, pebble_id, status pending|accepted|declined)`.
- `invite_pebble_pair(p_pebble_id, p_to_user)`: owns pebble, connection exists, **grade ≥ `private` enforced** (a paired pebble your partner can't see is meaningless).
- Accept: the recipient composes **their own** pebble (own emotion, text, intensity) through the normal create path; `create_pebble` gains an optional `pair_invite_id` payload key — validates the invite, **forces `happened_at` to the inviter's**, inserts the pair row, marks the invite accepted, one transaction. The key must pass through the `compose-pebble` edge function, keeping `compose-pebble-update` symmetric.
- `update_pebble`: a `happened_at` change propagates to the twin in-statement — **the only cross-user write in the entire system; comment it loudly**. Only `happened_at` syncs. Disconnect or deletion severs the pair; both pebbles survive unlinked.
- Read via `get_pebble_pair(p_pebble_id)` definer projection (name, emotion color, `render_svg`, owner display) so "paired but private" still renders a stub. Karma: the normal per-side path; `compute_karma_delta` untouched.
- UI ×3 (heaviest of the social set): invite sheet from pebble detail, pending-invite inbox, accept flow opening the composer with locked date, paired badge on path rows, pair display in read view. No push — invites surface on app open.

### M9 — Secret notes ("whispers")

- **Separate `pebble_whispers` table** (`pebble_id pk → pebbles on delete cascade`, `user_id`, `body_enc bytea`, `key_id default 'whisper-key-v1'`) — never a column on `pebbles`, so ciphertext is structurally excluded from `v_pebbles_full`, `path_pebbles`, the compose pipeline, and all three model layers.
- One symmetric key in Supabase Vault; definer RPCs `set_pebble_whisper` (pgp_sym_encrypt upsert), `get_pebble_whisper` (owner-only decrypt — lazy, on tap, never in list payloads), `delete_pebble_whisper`. Select-only owner RLS (enables a `has_whisper` flag); **no insert/update policies**, so plaintext can never land via a client write path.
- Whispers stay author-only at every grade including `public` (structural + UI copy). **Never enters the compose payload or `render_svg`** — guard comments in both edge functions. Written after `create_pebble` returns (second call; retry acceptable). Draft notes ride in the draft jsonb until publish (accepted plaintext window). No karma for whispers. `key_id` future-proofs rotation.

### M10 — Compliance batch A (parallel from M1)

- Password reset (web + deep links into native), email change, enable email confirmations (`enable_confirmations = false` today).
- **Age gate**: date-of-birth at signup, block under-15 (GDPR-K France), declare ratings accordingly in both consoles.
- Consent: F2 plus a re-consent surface for the rewritten policy.

### M11 — Compliance batch B (after feature freeze — the policy must describe M3–M9)

- UGC safeguards (Apple 1.2, more binding with public profiles/pebbles + marketplace): `content_reports` table + report affordance on marketplace glyphs, public profiles, and public pebbles; admin moderation queue in `apps/admin`; blocks surfaced; EULA/terms zero-tolerance clause; moderation contact.
- Privacy-policy rewrite (EN/FR): drop the fictional sections (Therapist, Decisions, Cairns), add marketplace, karma, connections, public profiles, server-side-encrypted notes, drafts, and deletion.

### M12 — Store readiness

- iOS: `PrivacyInfo.xcprivacy` (+ third-party SDK manifests), App Store metadata/screenshots, version/build automation, privacy-strings audit, decide stripping the unused `PebblesWidget` target from the submitted build, App Review notes asserting karma is a closed earned-only economy (no IAP).
- Android: Play Data Safety form, Apple sign-in via supabase-kt (~100–150 LOC; iOS-created Apple accounts are locked out of Android today), real launcher icon (**needs maintainer design assets**), **flip `WOBBLE_ENABLED` to `false` in `android-release.yml` before any public track** (2026-07-14 decision), store listing, optionally enable R8.

## 4. Convergence map

| Foundation | Consumed by |
|---|---|
| F1 `v_pebbles_full` invoker fix | everything that widens any read (M5, M6, M6b, M8) |
| `connections` + invites + blocks | M6b `private` tier, M7, M8, M11 |
| Definer-RPC projection pattern (never widen `profiles`/enrichment RLS) | M5, M6, M6b, M8 |
| `handle` + `public_profile` | M6, M6b share links, M5 invite display |
| `pebble_drafts` jsonb payload | M3 server drafts and local autosave (same shape) |
| `achievements` + `check_achievements()` | M4, displayed by M6 |
| `render_svg` as the cross-user visual | M6b shared pebbles, M8 pair display (avoids glyph/snap RLS entanglement) |
| `purge_account` | must cover every table above — lands early, extended by each milestone |

## 5. Decision-log entries to write along the way

1. Offline is a non-goal (F5).
2. Backfill to `secret`; `private` reinterpreted as connections-visible; what each grade exposes (core + `render_svg` only).
3. Whispers are server-side encrypted (Vault + pgcrypto), explicitly not E2E; no karma for whispers.
4. Connections: single-row symmetric, invite/QR only, no search; blocks from day one.
5. Achievements: client-called idempotent RPC, no triggers/cron; badges permanent; karma `grant` deferred.
6. Drafts: separate jsonb table, never a status column on `pebbles`; local autosave is insurance only.
7. Pairs sync `happened_at` only; pairing requires grade ≥ `private`; sever on disconnect.
8. Cross-user reads only via definer-RPC projections; never widen `profiles` RLS.
9. Account deletion: anonymize sold glyphs via the `user_id = null` system state; delete personal ledger rows.
10. Age-gate approach (DOB at signup, 15+ France).
11. Public share = uuid link, no token table; revoke by grade flip.

## 6. Verification strategy

- Per migration: `npm run db:types --workspace=packages/supabase`, commit `types/database.ts`. RLS probes with a second test user: `secret` invisible; `private` visible only through a connection; `public` readable via the anon RPC; enrichments and whispers never cross users; `v_pebbles_full` no longer readable cross-user after F1.
- Karma invariants: drafts create zero `karma_events`; publishing a draft emits exactly one `pebble_created`; pair accept emits per-side only; a re-run of `check_achievements()` inserts nothing.
- Purge test: seed a user with every entity type including a sold glyph → delete account → the buyer's glyph still renders, all personal rows are gone, the storage prefix is empty, and a re-run converges.
- Per workspace: `npm run lint --workspace=<app>`; full `npm run build` when shared types change. Store checklists (Data Safety, `PrivacyInfo.xcprivacy`) reviewed against the final schema.
