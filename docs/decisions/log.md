# Decision log

Append-only ledger of **significant** product/engineering decisions. One terse entry per decision.

**Significance bar:** would a future agent or human waste real time rediscovering or wrongly reversing this?

**Rules:**
- Append-only. Newest entries at the bottom.
- **Supersede, don't edit.** Status changes are new appended entries that reference the prior one — never edits to past entries.
- Keep entries terse. If a decision needs prose, link to the PR/issue under **Refs**.
- Skip routine choices, style nits, and anything obvious from the code.

## Entry template

```markdown
## YYYY-MM-DD — <decision title>

- **Status:** taken | rejected | deprecated | superseded-by(YYYY-MM-DD title)
- **Scope:** ios | webapp | db | infra | docs (one or more, comma-separated)
- **Context:** the scope, issue, or challenge — a contextual *why now*.
- **Decision:** the "we will …" formula. What we decided, stated as an action.
- **Why:** why this option and not the alternatives.
- **Consequences:** the "how" — what follows from this decision (constraints, follow-ups, things to watch).
- **Supersedes / Superseded-by:** link to the entry this replaces, or `—`.
- **Refs:** #PR, #issue, file paths.
```

---

## 2026-05-26 — Ban `+` in email addresses across auth surfaces

- **Status:** taken
- **Scope:** ios, webapp
- **Context:** Gmail-alias sub-addressing (`user+anything@gmail.com`) lets one inbox spawn unlimited Pebbles accounts. RFC 5322 and Supabase both accept `+`, so the server is not the authority on this rule.
- **Decision:** We will strip `+` from email input on every keystroke/paste, reject any submitted address containing `+` with a `canSubmit`-style guard, and show a localized inline error explaining the restriction.
- **Why:** Prevents multi-account abuse via alias sub-addressing. Silent stripping alone would confuse users whose typed character disappears, so the inline error is mandatory. Lowercasing and whitespace trimming stay silent on purpose — only `+` warrants explanation.
- **Consequences:** Every new email input surface needs both real-time stripping AND a submit-time guard (autofill paths can bypass the binding's normalization). Relaxing the rule (e.g. allowing `+` for non-Gmail domains) is a product decision — ask before changing.
- **Supersedes / Superseded-by:** —
- **Refs:** `apps/ios/Pebbles/Features/Auth/AuthView.swift` (`normalizeEmailInput`).

## 2026-05-26 — Deploy to remote Supabase instead of local Docker

- **Status:** taken
- **Scope:** db, infra
- **Context:** The standard Supabase local dev workflow (`supabase start`, `supabase functions serve`, `db:reset`) depends on Docker, which is a constant source of friction on the maintainer's machine.
- **Decision:** We will test edge functions, migrations, and DB changes against the remote Supabase project rather than local containers. Use `supabase db push` for migrations and `supabase functions deploy` for edge functions.
- **Why:** Docker workflows fail often enough that they block work; the remote project is reliable and cheap to iterate against for this team size.
- **Consequences:** Plans, verification steps, and onboarding instructions are written for remote-first workflows. Anyone proposing a local-Docker step should first confirm it works end-to-end on the maintainer's setup.
- **Supersedes / Superseded-by:** —
- **Refs:** —

## 2026-05-26 — Prefer RPCs for multi-table or multi-statement Supabase writes

- **Status:** taken
- **Scope:** db, webapp, ios
- **Context:** Client-stitched multi-table writes are not atomic — PostgREST has no client-side transactions, so a partial failure leaves the database inconsistent. RLS-only ownership checks are also harder to keep symmetric across surfaces.
- **Decision:** We will check `packages/supabase/supabase/migrations/` for an existing RPC before writing any Supabase query that touches more than one table or does more than a single-row `select`/`insert`/`update`/`delete`. If none exists, create one rather than chaining client calls. Keep sibling RPCs symmetric (e.g. payload keys added to `update_pebble` should also land in `create_pebble`).
- **Why:** RPCs run in a single Postgres transaction and can enforce ownership checks via `security definer`. They give us atomicity, a single source of truth for business rules, and surface parity between web and iOS.
- **Consequences:** Single-table, single-statement reads/writes stay as direct client calls — no RPC needed. Extending an existing RPC is preferred over re-implementing logic client-side, even when the RPC is missing a small piece of what you need.
- **Supersedes / Superseded-by:** —
- **Refs:** `AGENTS.md` ("Supabase — prefer RPCs for multi-table writes"), `packages/supabase/supabase/migrations/`.

## 2026-05-26 — Track significant decisions in an in-repo log

- **Status:** taken
- **Scope:** docs
- **Context:** ADRs lived only in GitHub Issues — good for discussion, but not greppable from the repo, noisy, and invisible to agents at read time. Settled questions kept getting re-litigated because there was no durable, low-token home for them.
- **Decision:** We will keep an append-only ledger at `docs/decisions/log.md`, one terse entry per significant decision, supersede-don't-edit. The PR checklist in `CLAUDE.md` gains a gated micro-step (usually a no-op): if a PR established or reversed a significant decision, append one entry.
- **Why:** Greppable and cheap to read for agents and humans alike. The "supersede, don't edit" rule preserves history of *why* a decision changed without rewriting the past. The gated step keeps cost near zero on routine PRs while making the bar visible on the ones that matter. Significance test: "would a future agent or human waste real time rediscovering or wrongly reversing this?"
- **Consequences:** Routine choices, style nits, and anything obvious from the code stay out of the log. GitHub Issues remain the place for discussion; the log captures the outcome. If the log grows unwieldy, we'll revisit splitting it by scope — not yet.
- **Supersedes / Superseded-by:** —
- **Refs:** #477, #482, `CLAUDE.md` (PR checklist step 6).

## 2026-05-26 — Promote learnings into CLAUDE.md only on hardening, via a milestone grooming pass

- **Status:** taken
- **Scope:** docs
- **Context:** Learnings are living wisdom, captured cheaply in plans' "Lessons learned" sections. `CLAUDE.md`/`AGENTS.md` load into *every* agent context, so they are the most token-precious files in the repo and must hold only durable, action-guiding rules — not a junk drawer of observations. Per-PR CLAUDE.md edits for learnings bloat the file and dilute its signal.
- **Decision:** We will promote a learning into a CLAUDE.md/AGENTS.md rule only when it clears both bars — **durable** (outlives the next refactor) and **action-guiding** (tells a future agent what to do or avoid). Cadence is the periodic monorepo-audit grooming pass at **milestone boundaries**, folded into the audit's existing "Doc accuracy" domain. Never a per-PR CLAUDE.md edit for learnings. Promoted rules land at the right scope: root `CLAUDE.md`/`AGENTS.md` for cross-cutting, workspace `CLAUDE.md` for surface-specific.
- **Why:** Two-stage pipe — cheap capture, expensive promotion — keeps CLAUDE.md small and high-signal while losing nothing. Milestone cadence groups grooming with the audit work that already touches the same files, so there is no separate ritual to remember. Precedent: the "never await Supabase inside `onAuthStateChange`" rail is a learning that hardened into a rule exactly this way.
- **Consequences:** Agents do not edit CLAUDE.md to record per-PR learnings; they leave them in the plan's "Lessons learned". Reviewers can push back on CLAUDE.md edits that fail the durable + action-guiding bars. The monorepo-audit checklist now explicitly includes a grooming sweep; expect rules to be **demoted or deleted** during the same pass when they have gone stale.
- **Supersedes / Superseded-by:** —
- **Refs:** #479, `CLAUDE.md` ("Editing CLAUDE.md / AGENTS.md"), `docs/superpowers/specs/2026-04-11-monorepo-audit-design.md` (per-domain checklist item 6).

## 2026-06-29 — Karma wallet: one ledger, debt allowed, refunds server-only

- **Status:** taken
- **Scope:** db, webapp
- **Context:** Making karma spendable (M36 Pebblestore, #494) required turning the earn-only `karma_events` ledger into a currency with a balance that can go down — without breaking existing flows (notably pebble deletion, which claws back the karma a pebble earned).
- **Decision:** We will keep **one ledger** (`karma_events` gains a `type` credit/withdraw axis keyed off movement *category*, not sign) with a single balance = Σ delta, snapshotted in `wallet_balances`. The non-negative rule lives **only in the `spend_karma` RPC** (row-locked `balance ≥ amount` check); there is deliberately **no `CHECK(balance >= 0)`** on the snapshot, so earn-side clawbacks may drive the balance **negative (a debt)** the user clears by re-earning. `refund_karma` is **`service_role`-only**, never callable by `authenticated`.
- **Why:** Coupling pebble deletion to wallet state would be wrong UX, so clawbacks must always apply even into the negative — a column `CHECK` would roll back a legitimate delete. The purchase guard alone keeps the store safe (a negative balance can't buy anything). An unguarded `refund_karma` granted to `authenticated` is a karma-minting hole (`refund_karma(1_000_000, …)`); refunds are admin/server actions, and the buy flow needs no client refund because a failed grant rolls back the spend in the same transaction.
- **Consequences:** **Do not add `CHECK(balance >= 0)` to `wallet_balances`** — it would break pebble deletion. New spend-side reasons go through `spend_karma`; new earn-side reasons just insert credit events. The `bounces` admin snapshot trigger now folds **credits only** so "bounce karma distribution" keeps meaning *earned*. `delta` stays `smallint` (widening would force dropping/recreating dependent views on the live DB). Idempotency of a *purchase* is the caller's (sub-project C's) responsibility, enabled by `spend_karma` being callable inside the caller's transaction.
- **Supersedes / Superseded-by:** —
- **Refs:** #494, `docs/superpowers/specs/2026-06-29-issue-494-karma-wallet-design.md`, `packages/supabase/supabase/migrations/20260629192621_karma_events_type_axis.sql` … `20260629194621_wallet_summary_and_bounce_credit_only.sql`.

## 2026-06-29 — Web in-app notifications: Sonner + explicit-fire, no realtime

- **Status:** taken
- **Scope:** webapp
- **Context:** M36 sub-project B (#495) needed a non-invasive in-app activity primitive, first used for a "+N karma" pill on wallet credit. A web PWA cannot render into the iOS Dynamic Island, so the admired Opal effect had to be approximated within web limits, and we needed a way to know when a credit happened.
- **Decision:** We will build in-app activity on **Sonner** (ported from `apps/admin`), rendering a compact custom pill via `toast.custom()` bottom-center. The trigger is **explicit fire at the action site** via store-diff: the pebble mutation hooks read karma from provider truth before/after the action and call `notifyKarma(delta, reason)` on a positive delta. No Supabase realtime, no central balance-watcher.
- **Why:** Sonner gives queueing, timeout, swipe-dismiss, `aria-live`, and reduced-motion handling for free. Explicit fire is the cleanest feature-agnostic primitive (carries a reason label, no false-fire surface), needs no backend change, and suits a personal PWA where credits come from the user's own on-device actions. Realtime would be heavier and double-fire against the optimistic store reload.
- **Consequences:** New credit sources surface a pill by adding one `notifyKarma(...)` call at their action site — and must wire it on the hook the live surface actually uses (enrichment goes through the singular `usePebble(id)`, not `usePebbles()`). Credit-only by design: clawbacks (delta ≤ 0) stay silent. The pill uses a stable toast id so a new credit replaces the prior one. Reused by C (e.g. "glyph purchased") via the same primitive.
- **Supersedes / Superseded-by:** —
- **Refs:** #495, `docs/superpowers/specs/2026-06-29-issue-495-in-app-activity-design.md`, `apps/web/lib/activity/karma-activity.tsx`, `apps/web/components/activity/KarmaActivityPill.tsx`.

## 2026-06-30 — Glyph marketplace: use-rights entitlements, per-listing price, atomic buy

- **Status:** taken
- **Scope:** db, webapp
- **Context:** M36 sub-project C (#496) makes community glyphs the first thing the Pebblestore sells — buyable with karma over A's spend rails. We had to decide what a purchase grants, where price lives, and how to keep the spend+grant consistent.
- **Decision:** Buying grants a **use-rights entitlement** (`glyph_entitlements` row), **not a copy** — the original glyph stays single-source and the creator keeps authorship. **Price lives on the listing row** (`glyph_submissions.price`, flat-defaulted to 25 via a constant mirrored in `apps/web/lib/config/glyphs.ts`), read server-side by `buy_glyph` so the client can't set it. Each purchase snapshots **`price_paid`** on the entitlement. The **`buy_glyph` RPC** does spend (`spend_karma`) + entitlement insert in **one transaction**, idempotent via `unique(user_id, glyph_id)`.
- **Why:** Entitlements avoid stroke duplication and keep authorship/attribution clean, and make a glyph usable everywhere own glyphs are (picker + lookup map). Per-listing price (vs a single global constant) lets D re-price one glyph later with no schema change while staying flat now. `price_paid` preserves a per-glyph purchase ledger (buyers-per-month, revenue) that survives future price changes — so "glyph value" can be a derived aggregate, never a stored column (YAGNI: capture data, defer analytics). Single-transaction buy + the unique constraint make a concurrent double-buy roll back the loser's spend too, so a buyer is charged at most once.
- **Consequences:** New store goods (themes, pebbleskins) reuse the `buy_glyph` shape: validate listing → `spend_karma(price,'purchase',ref)` → grant, in one txn. Entitlement rows are insertable **only** via the `security definer` RPC (no INSERT policy). The purchase fires a dedicated **spend** pill (`notifyGlyphPurchased`), not credit-only `notifyKarma`. Market stays empty until a submission is `approved` (no moderation UI in C — that's D).
- **Supersedes / Superseded-by:** —
- **Refs:** #496, `docs/superpowers/specs/2026-06-30-issue-496-glyph-marketplace-design.md`, `packages/supabase/supabase/migrations/20260630003348_glyph_marketplace.sql`.

## 2026-06-30 — Glyphs become readable when listed/entitled; listed glyphs are creator-immutable (D8)

- **Status:** taken
- **Scope:** db
- **Context:** A community market means other users must read a creator's glyph rows (strokes/viewBox) to render and buy them — but glyphs were own-only readable. And once a glyph is listed/bought, letting the creator edit its strokes would be bait-and-switch on what buyers paid for.
- **Decision:** The `glyphs` SELECT policy widens to **own ∪ system-seeds (`user_id is null`) ∪ approved-listed ∪ own-entitled** — the single place community glyph rows become readable. The `glyphs` UPDATE/DELETE policies are rewritten to **lock** a glyph once it has an active submission (`pending`|`approved`) **or** any entitlement: only `is_admin(auth.uid())` may then modify it; the creator cannot. The market view `v_glyph_market` is `security_invoker` so the caller's RLS applies.
- **Why:** The widened SELECT exposes *only* glyphs that are genuinely listed or that the caller already bought — no broad leak. The lock is backend-enforced (RLS), not UI-only, because a frontend-only guard is bypassable via the raw update path; `on delete cascade` would otherwise let a creator wipe buyers' entitlements. Admins are exempt because curating/adjusting listed glyphs is D's domain.
- **Consequences:** **Do not narrow `glyphs_select` back to own-only** — the market and picker depend on the widened policy. Future market goods must keep their listed rows readable the same way. The web UI reflects the lock (`GlyphDetail` hides edit/delete, shows a "Listed — locked" badge) but RLS is the real enforcement. Admin moderation that re-prices or edits listed glyphs (D) relies on the `is_admin` exemption.
- **Supersedes / Superseded-by:** —
- **Refs:** #496, `packages/supabase/supabase/migrations/20260630003348_glyph_marketplace.sql` (glyphs policy rewrite, `v_glyph_market`).

## 2026-06-30 — Admin glyph moderation: is_admin RPCs, first-party admin-owned glyphs, stroke-only SVG import (#497)

- **Status:** taken
- **Scope:** db, admin, webapp
- **Context:** M36 sub-project D builds the back-office for the glyph market: moderating community submissions (approve/reject/re-price) and seeding the market with first-party glyphs uploaded from raw SVG. Two frictions had to be resolved: the widened `glyphs` SELECT policy (D8, #496) does **not** let an admin read a *pending* submission's strokes via RLS, and the glyph model is **stroke-only** (paths render as outlines recolored by emotion) while most real-world SVGs are fill-based.
- **Decision:** All admin mutations go through **`is_admin`-gated `SECURITY DEFINER` RPCs** (`approve_glyph`/`reject_glyph`/`set_glyph_price`/`publish_admin_glyph`), plus a read RPC **`admin_list_glyph_submissions`** that joins glyph geometry server-side to bypass the pending-read RLS gap. Reject requires a reason stored in a new `glyph_submissions.review_note` column, surfaced to the submitter on the web "Mine" tab. First-party uploads are **owned by the admin user** (a normal auto-approved listing), **not** `user_id IS NULL` system seeds — so they reuse all market plumbing, `cannot_buy_own` protects the admin, and the D8 admin-exemption lets them be re-edited/re-priced. SVG import is **stroke-only**: a documented subset (`<path>/<line>/<polyline>/<polygon>`, path commands `M L H V Q C Z`) is converted; fills and other elements are skipped-and-reported; filled icons import as outlines. Adjust transforms (scale/recenter/flip) are baked into path coordinates at publish.
- **Why:** A `SECURITY DEFINER` read RPC is the only way to give admins pending-submission previews without weakening the marketplace SELECT policy. Admin-owned (vs system-seed) first-party glyphs avoid special-casing the buy/lock paths. Stroke-only import keeps the engine unchanged (a filled-glyph render mode would ripple across web + iOS — deferred); making the limit visible in the live preview is honest UX. Baking transforms into geometry means downstream renderers need no awareness of the adjust step.
- **Consequences:** New admin-curated store goods reuse this RPC shape (gated read + gated mutations). `review_note` is now part of the submission contract (web reads it via the existing submitter RLS). A filled-glyph render mode remains a future, separate decision. The admin SVG path parser supports only the documented command subset; arcs/smooth curves are skipped, not approximated.
- **Supersedes / Superseded-by:** —
- **Refs:** #497, `docs/superpowers/specs/2026-06-30-issue-497-admin-glyph-moderation-design.md`, `docs/superpowers/plans/2026-06-30-admin-glyph-moderation.md`, `packages/supabase/supabase/migrations/20260630084718_admin_glyph_moderation.sql`.

## 2026-07-01 — Glyph sales pay the creator via a net-zero karma transfer; attribution transfers ownership (#497)

- **Status:** taken
- **Scope:** db, admin, webapp
- **Context:** M36 sub-project D gained curation needs: creators should earn from glyph sales, admins should be able to attribute a first-party (admin-uploaded) glyph to the real creator, delist a glyph without deleting it, and hard-delete glyphs.
- **Decision:** A glyph sale **credits the glyph owner (`glyphs.user_id`) the full price** as a `glyph_sale` karma credit inside `buy_glyph`, in the same transaction as the buyer's `spend_karma` withdraw — a **net-zero transfer** (no minting), consistent with the wallet rules (#494). **Attribution transfers ownership**: `admin_attribute_glyph` sets `glyphs.user_id` to a looked-up user, so they become the creator (glyph appears in their gallery, `cannot_buy_own` protects them, payouts route to them); admin-owned (unattributed) glyphs pay the admin account. **Delisting** is a new `glyph_submissions.listed` flag: `v_glyph_market` + `buy_glyph` require `approved AND listed`, so a delisted glyph leaves the market but existing owners keep it. **Delete** is a `security definer` admin RPC that cascades to the submission + entitlements.
- **Why:** Reusing `glyphs.user_id` as the payout target (rather than a separate `credited_to`) keeps ownership, gallery visibility, `cannot_buy_own`, and payouts consistent from one column. Crediting inside `buy_glyph` (vs a separate call) makes the transfer atomic with the spend. Net-zero keeps the economy closed. A `listed` flag (vs flipping `status`) preserves the `approved` audit + entitlements while removing buyability.
- **Consequences:** New `glyph_sale` reason on the `karma_events` CHECK. Payout amount is bounded by `delta`'s `smallint` (same as the existing spend path). Delisting is reversible; deletion is not (buyers lose access — the admin UI warns). Future royalty/revenue-share models would build on `glyphs.user_id` as the creator.
- **Supersedes / Superseded-by:** —
- **Refs:** #497, `packages/supabase/supabase/migrations/20260701102810_glyph_marketplace_curation.sql`.

## 2026-07-01 — Deprecated the glyph shape: dropped glyphs.shape_id and the pebble_shapes table (#503)

- **Status:** taken
- **Scope:** db, ui, ios
- **Context:** A glyph is strokes in a square viewBox scaled into the pebble slot at render time (issue #278). The `glyphs.shape_id` FK to `pebble_shapes` was long deprecated — every surface (web /carve, admin upload, iOS carve, the system seeds) already wrote `shape_id = null`, and pebble *outlines* are rendered from baked-in engine templates (`apps/web/lib/engine/templates.ts`), not from `pebble_shapes`.
- **Decision:** Dropped `glyphs.shape_id` and the now-orphaned `pebble_shapes` table system-wide. Updated the dependent RPCs/views to stop referencing the column: `create_pebble` no longer reads `new_glyph.shape_id`; `publish_admin_glyph` lost its `p_shape_id` parameter; `admin_list_glyph_submissions`, `v_pebbles_full`, and `v_glyph_market` no longer project `shape_id`. Removed all web/admin readers (`Mark.shape_id`, provider mappings, `PEBBLE_SHAPES` config, `useShapeName`, the dead `carve/PebbleOutline.tsx`, shape i18n strings) and tidied iOS comments/tests.
- **Why:** No live data flowed through `shape_id` — it was pure backward-compat cruft, and `pebble_shapes` had no reader after the FK went away. Removing both eliminates a misleading data model (glyphs are shape-agnostic).
- **Consequences:** Legacy glyph rows keep their original (sometimes non-square) `view_box`; they render fine because the engine fits by viewBox. Geometry was intentionally NOT normalized. Reintroducing a glyph→shape association is out of the question — glyphs are squares.
- **Supersedes / Superseded-by:** —
- **Refs:** #503, `docs/superpowers/specs/2026-07-01-remove-glyph-shape-design.md`, `docs/superpowers/plans/2026-07-01-remove-glyph-shape.md`, `packages/supabase/supabase/migrations/20260701114205_drop_glyph_shape.sql`.

## 2026-07-01 — iOS karma-earned flash is an in-app pastille, not a Dynamic Island Live Activity (#505)

- **Status:** taken
- **Scope:** ios
- **Context:** #505 set out to surface the "+N karma" earn (creating/enriching a pebble) as an ActivityKit **Live Activity in the Dynamic Island**, mirroring Opal. The spec (D3) treated the Live Activity as the primary presentation with an in-app capsule only as a non-Dynamic-Island fallback. On-device testing (iPhone 15, iOS 26) showed `Activity.request` succeeding (`state=active`) but **nothing rendering** in the notch, on the Lock Screen, or when backgrounded within the window.
- **Decision:** Abandon the Live Activity for the karma flash. The flash is now **always an in-app "liquid glass" pastille** popping bottom-center, hosted in a **pass-through `UIWindow`** (level `.alert + 1`) so it floats above the create/edit/detail sheets that are up at earn time. Presentation uses real `.glassEffect` on iOS 26 behind a localized `if #available` guard (an approved, documented exception to the workspace "iOS 17 APIs only / no `if #available`" rule), with a translucent-material capsule fallback below 26. A Core-Haptics vibration is derived from the ceramic sound's amplitude envelope and fired in lockstep with it; an accent-color countdown ring drains over the pastille's ~2.5s lifetime. The DI-detection + LA-vs-capsule routing (`DeviceCapabilities`, `karmaPresentationDecision`) was deleted. The `PebblesWidget` app-extension target + `KarmaLiveActivityController` + `KarmaActivityAttributes` are **retained, unused**, as the reference for a future Glyph-purchase Live Activity.
- **Why:** iOS does not render a foreground app's own Live Activity in the Dynamic Island, and karma is **only ever earned by a foreground in-app action** — so the real DI can never show this flash. The concern was raised during brainstorming (Challenge 2), wrongly walked back, and confirmed by device evidence. An in-app pastille is the honest, cross-version (iOS 17+), every-device way to reproduce the Opal-style moment; a pass-through overlay window is required because the earn happens inside a sheet that a normal RootView overlay sits behind.
- **Consequences:** No App Group / push / provisioning burden for the karma flash. The retained Live Activity stack is dead code until the Glyph feature adopts it (a future notification that *can* surface while the app is backgrounded, where the DI works). The `if #available(iOS 26, *)` exception is scoped to one view modifier; adopting Liquid Glass more broadly, or raising the iOS floor to 26, remains a separate decision. The pastille amount still comes from the server `karma_delta` (unchanged).
- **Supersedes / Superseded-by:** Revises presentation decision **D3** of the #505 spec (Live Activity → in-app pastille); the server-delta (D1) and fallback-fork (D2) decisions stand.
- **Refs:** #505, `docs/superpowers/specs/2026-07-01-issue-505-ios-karma-earned-flash-design.md`, `docs/superpowers/plans/2026-07-01-ios-karma-earned-flash.md`.
