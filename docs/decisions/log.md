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
