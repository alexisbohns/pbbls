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
