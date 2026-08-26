# Cross-surface contract — Kritik audit 2026-08

Commit `10181916ba9f56789e62c6351bb380682e5d90da` · The cross-surface lens carries no scored maturity cells (no assessments in `scores.json`, no column in `matrix.json`); it is a findings-only view holding 4 open findings, all Medium. There is no aggregate score or grade for this lens.

## Verdict

The worst finding is a live data-corruption path between two production clients: Android emits draft timestamps through `OffsetDateTime.toString()`, which drops the seconds field when they are zero (every picker-set date), and iOS's flexible ISO-8601 parser refuses a seconds-less time, so an Android-written draft resumed on iOS decodes `happened_at` to nil and silently falls back to "now" (F-2026-08-TST-cross-surface-01), corrupting the recorded moment that is the core datum of the product. The best structural strength is that the cross-surface contract is defended by real committed machinery at all: two runnable proof harnesses (`verify-pebble-drafts.ts`, `verify-account-purge.ts`) plus a codified standing rule (decision #651) that each surface test against the others' verbatim payloads. The recurring theme, though, is that the machinery has not kept pace with Android: the drafts harness never grew an Android-shaped fixture (F-2026-08-TST-cross-surface-02), so the timestamp break shipped unseen. Two more gaps sit in the same seam: the M51 privacy-grade reinterpretation backfilled `pebbles` but not `pebble_drafts`, so pre-M51 drafts publish as connections-visible without a fresh owner choice (F-2026-08-REL-cross-surface-01), and the account-purge harness has vacuous oracles for five tables (F-2026-08-TST-cross-surface-03). All four findings are Medium and open; none was verified. Because the lens produces no maturity roll-up, its health reads entirely from these four open items, and every one of them lives where no single-surface auditor could have caught it.

## Domain scores

The cross-surface lens has no scored criteria, so no domain carries a maturity score or grade here; its criteria are scored inside the per-surface columns. Only domains with cross-surface findings are listed. No caps apply (no Critical or High findings).

| Domain | Score | Grade | Open findings (C/H/M/L) | Note |
|---|---|---|---|---|
| Testing & Verification (TST) | N/A | N/A | 0 / 0 / 3 / 0 | Shared-shape and oracle gaps: no Android draft fixture, seconds-less timestamp break, vacuous purge oracles |
| Reliability & Observability (REL) | N/A | N/A | 0 / 0 / 1 / 0 | Migration blast-radius gap: M51 privacy backfill skipped `pebble_drafts` |

N/A (no applicable cross-surface criteria or findings): Security (SEC), Privacy & Data Protection (PRV), GDPR & Regulatory (GDP), Safety & Wellbeing (SAF), Code Quality & Architecture (ARC), Platform & Store Compliance (PLT), Accessibility & Inclusion (A11Y), Performance & Efficiency (PRF), Agentic Development Readiness (AGT).

## Findings

Four findings, all Medium severity, all open, none verified. Ordered within Medium by risk score (impact x likelihood), highest first.

### 🟡 Medium — F-2026-08-TST-cross-surface-02 — The drafts contract's proof machinery never grew an Android-shaped payload

- **Criterion:** TST-02, Shared shapes tested against real cross-surface payloads
- **Priority:** P1 (next milestone, scheduled)
- **Cost:** S (≤ half a day, single scope)
- **Impact x Likelihood:** 2 x 5 = 10 (Medium)
- **Where:**
  - `packages/supabase/scripts/verify-pebble-drafts.ts:186-240` (iosShaped + webShaped only; no Android shape) and `:28-29` (standing rule scoped to keys, not surfaces)
  - `apps/ios/PebblesTests/DraftCrossSurfaceDecodingTests.swift:12-32` (three fixtures, none Android-shaped)
  - `apps/android/app/src/main/kotlin/app/pbbls/android/services/PebbleDraftsService.kt:74-81` (Android is a real third producer)
  - `apps/ios/Pebbles/Features/Path/Models/PebbleDraftPayload.swift:61-79` (encodeIfPresent, so iOS drafts omit nulls, contradicting the fixture labeled iOS-shaped)
- **Why it matters:** Decision #651 (2026-07-30) makes `verify-pebble-drafts.ts` the committed proof that `pebble_drafts.payload` survives every producer, and requires each surface to test against real payloads produced by the others. Android became the third writer of that contract, but no androidShaped fixture exists, and iOS's cross-surface decoding tests cover web-milliseconds, Postgres-microseconds and whole-seconds but never Android's actual emissions. The harness's standing rule triggers on new keys, not new surfaces, so the machinery stayed silent while Android's emission drifted out of the parseable set. This is exactly how the incompatibility in F-2026-08-TST-cross-surface-01 shipped unseen. The iosShaped fixture has also drifted: it carries explicit nulls that the iOS encoder never writes, so it is the create-payload shape, not what iOS drafts actually produce.
- **Fix:** Add an androidShaped fixture to `verify-pebble-drafts.ts` capturing Android's real emissions verbatim (minute-precision seconds-less timestamp, non-UTC offset, sub-second variant), add the same strings to iOS `DraftCrossSurfaceDecodingTests` and Android `DraftCrossSurfaceDecodingTest`, and extend the harness's standing-rule comment to trigger on new producing surfaces as well as new keys.

### 🟡 Medium — F-2026-08-TST-cross-surface-01 — Android emits timestamps via `OffsetDateTime.toString()`, producing seconds-less ISO strings that iOS silently drops on draft resume

- **Criterion:** TST-02, Shared shapes tested against real cross-surface payloads
- **Priority:** P2 (planned backlog)
- **Cost:** M (≤ 2 days, single surface)
- **Impact x Likelihood:** 3 x 3 = 9 (Medium)
- **Where:**
  - `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/models/OffsetDateTimeSerializer.kt:29` (encodeString(value.toString()))
  - `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/WhenDateTime.kt:36` (date.atTime(hour, minute), so seconds/nanos are zero)
  - `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/models/PebbleDraftPayload.kt:32-34` (draft happened_at uses this serializer)
  - `apps/android/app/src/test/kotlin/app/pbbls/android/features/path/models/PebblePayloadTest.kt:118` ('toString() trims :00 seconds')
  - `apps/ios/Pebbles/Features/Path/Models/ISO8601Flexible.swift:27-43` (fractional-then-whole-second only; neither accepts a seconds-less time)
  - `apps/ios/Pebbles/Features/Path/Models/PebbleDraftPayload.swift:88-93` + `203` (nil happenedAt falls back to defaults 'now')
- **Why it matters:** The standing cross-surface rule (CLAUDE.md; decision #651) requires boundary-crossing timestamps to be emitted at whole-second precision, never left to an ambient strategy. Android's shared serializer uses `value.toString()`, which omits the seconds field when seconds and nanos are zero (e.g. `2026-08-26T14:23+02:00`), and every picker-set Android date is built with seconds/nanos zero. iOS's `ISO8601Flexible` accepts only fractional or whole-second forms, so an Android-written draft resumed on iOS decodes `happened_at` to nil and hydration silently falls back to "now", breaking the drafts design's "restored verbatim" rule (D6) and corrupting the recorded moment, the core datum of the product. The Android suite even documents the hazard but works around it with a same-surface round-trip, the exact structurally-blind oracle decision #651 identified. Postgres and web tolerate the form, so the break is invisible everywhere except the Android to iOS resume path.
- **Fix:** Give `OffsetDateTimeSerializer.serialize` a deliberate emission: truncate to whole seconds and format ISO-8601 with seconds always present (e.g. `value.truncatedTo(ChronoUnit.SECONDS).format(ISO_OFFSET_DATE_TIME)`, or convert to UTC and emit the same internet-date-time form iOS uses), mirroring iOS `ISO8601Flexible.string`. Add the seconds-less and nanosecond variants as decode fixtures to iOS `DraftCrossSurfaceDecodingTests` and as a pinned-output assertion (not a same-surface round-trip) to the Android encode tests.

### 🟡 Medium — F-2026-08-REL-cross-surface-01 — The M51 privacy-grade reinterpretation backfilled pebbles but not pebble_drafts, so pre-M51 drafts publish as connections-visible without a fresh owner choice

- **Criterion:** REL-06, Contract-safe migrations with rollback story
- **Priority:** P1 (next milestone, scheduled)
- **Cost:** S (≤ half a day, single scope)
- **Impact x Likelihood:** 4 x 2 = 8 (Medium)
- **Where:**
  - `packages/supabase/supabase/migrations/20260817130000_pebble_visibility_grades.sql:30-36` (backfill updates public.pebbles only; no pebble_drafts statement anywhere in the migration)
  - `docs/decisions/log.md:386-389` (M51 entry: all three clients sent visibility 'private' explicitly; backfill rationale)
  - `apps/web/components/record/draft-payload.ts:44-46` + `155` ('private' restored verbatim)
  - `apps/ios/Pebbles/Features/Path/Models/PebbleDraftPayload.swift:207-209` (Visibility(rawValue:) keeps .private)
  - `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/models/PebbleDraftPayload.kt:168` (visibility ?: defaults keeps PRIVATE)
  - `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/record/RecordFlowModel.kt:238` (resume lands on PRIVACY step prefilled, not re-asked as a gap)
- **Why it matters:** M51 reinterpreted visibility "private" from owner-only to connections-visible and backfilled every existing `pebbles` row to "secret" precisely because letting old rows become connection-visible would be a silent privacy regression. But all three clients write visibility into `pebble_drafts.payload`, and between drafts shipping (2026-07-29) and grade activation (2026-08-17) every draft was saved with the then-default "private", chosen when it meant owner-only. The migration touched only `public.pebbles`; no sweep or hydration mapping exists for drafts, so web, iOS, and Android all restore "private" verbatim and publishing passes it straight to `create_pebble`, producing a pebble visible to every mutual connection. Resumed drafts land on the privacy step prefilled, but a user who remembers choosing owner-only gets no cue that its meaning changed underneath them. This recreates for the drafts store exactly the regression the pebbles backfill was written to prevent, on an intimate-content product where the exposed rows are emotional records.
- **Fix:** Ship one migration sweeping stale drafts: `update pebble_drafts set payload = jsonb_set(payload, '{visibility}', '"secret"') where updated_at < '2026-08-17' and payload->>'visibility' = 'private'` (same updated_at-trigger caution as the pebbles backfill if a trigger exists on the table). Optionally add the same guard for web localStorage autosave snapshots at hydration.

### 🟡 Medium — F-2026-08-TST-cross-surface-03 — verify-account-purge.ts oracles are vacuous for five purge tables

- **Criterion:** TST-05, Tests assert behavior with real oracles
- **Priority:** P1 (next milestone, scheduled)
- **Cost:** S (≤ half a day, single scope)
- **Impact x Likelihood:** 3 x 2 = 6 (Medium)
- **Where:**
  - `packages/supabase/scripts/verify-account-purge.ts:302-317` (only four tables pinned; comment admitting cascade makes later checks blind), `:336` (log_reactions assertion with no seed anywhere in the file, grep 'log_reactions' matches only this line), `:341-344` (zero-row loop runs after deleteUser at `:296`)
  - `packages/supabase/supabase/migrations/20260731090000_purge_account_union.sql:164-182` (the five section-(4) deletes at risk)
  - `docs/decisions/log.md:376-378` (standing rule: seed AND assertion in the same change; M52/M53 named as next likely collision)
- **Why it matters:** The harness's own comment states the section-(4) tables cascade from `auth.users`, so post-deleteUser zero-row assertions cannot catch a dropped purge delete line; only the RPC's returned counts can. Yet `expectedPurged` pins counts for just `pebble_drafts`, `connections`, `connection_invites` and `connection_blocks`. The deletes for `achievement_unlocks`, `wallet_balances`, `bounces` and `log_reactions` are count-unpinned, and `log_reactions` violates the standing rule outright: it appears only in the zero-row list and is never seeded, so that check passes unconditionally. If the next create-or-replace clobber of `purge_account` (M52/M53 forecast as the next collision) drops any of those five delete lines, the harness stays green. The dropped line matters whenever the delete-account flow stops before deleteUser (storage failure, expired JWT), leaving personal rows in place while the purge response reports success, and it falsifies the purge counts the edge function returns as erasure evidence.
- **Fix:** Seed one `log_reactions` row for the seller (requires a published `logs` fixture row or a service-role insert), and extend `expectedPurged` to pin the counts of every section-(4) table the seed creates (`achievement_unlocks` from `check_achievements`' return length, `wallet_balances` 1, `bounces` 1, `log_reactions` 1) so a dropped delete line fails loudly before the cascade hides it.

## Refuted during verification

None. No cross-surface finding carried a verification pass, and none was refuted.

## What is already strong

The cross-surface lens has no level-3 or level-4 maturity assessments (it carries no scored criteria at all), so these strengths are the existing guardrails the findings themselves credit, cited from the finding evidence.

- The cross-surface contract is defended by two runnable, committed proof harnesses rather than by convention alone: `verify-pebble-drafts.ts` for the draft payload and `verify-account-purge.ts` for erasure coverage (F-2026-08-TST-cross-surface-02 evidence at `verify-pebble-drafts.ts:186-240`; F-2026-08-TST-cross-surface-03 evidence at `verify-account-purge.ts:302-317`).
- The standing rule that each surface must test against verbatim payloads produced by the others is codified in a dated decision (#651, 2026-07-30) and promoted into CLAUDE.md, not left implicit (F-2026-08-TST-cross-surface-01 and -02 detail).
- iOS already parses tolerantly across precision variants: `DraftCrossSurfaceDecodingTests.swift:12-32` covers web-milliseconds, Postgres-microseconds and whole-seconds, and `ISO8601Flexible.swift:27-43` accepts both fractional and whole-second forms (F-2026-08-TST-cross-surface-01 and -02 evidence).
- The M51 privacy migration applied the correct instinct to the primary table: it backfilled every existing `pebbles` row to "secret" specifically to prevent a silent visibility regression (`20260817130000_pebble_visibility_grades.sql:30-36`; rationale at `docs/decisions/log.md:386-389`), the exact protection the drafts gap now needs (F-2026-08-REL-cross-surface-01).
- The Android test suite already documents the `toString()` seconds-trimming hazard in-line (`PebblePayloadTest.kt:118`, "toString() trims :00 seconds"), so the risk is known on the surface even though the emission was not yet fixed (F-2026-08-TST-cross-surface-01 evidence).
- The purge harness is honest about its own blind spot: its comment at `verify-account-purge.ts:302-306` records that section-(4) tables cascade from `auth.users` and that only pinned RPC counts (not zero-row checks) can catch a dropped delete, and four tables' counts are already pinned (F-2026-08-TST-cross-surface-03 detail).
- The next `purge_account` create-or-replace collision is forecast in advance (M52/M53 named at `docs/decisions/log.md:376-378`), so the collision risk that the harness must guard against is tracked, not latent (F-2026-08-TST-cross-surface-03 evidence).

## Scored criteria

No criteria were scored on the cross-surface lens. Its maturity is captured inside the per-surface columns (web, iOS, Android, admin, supabase); the cross-surface lens exists only to hold the four findings above, which land against criteria TST-02 (Shared shapes tested against real cross-surface payloads), TST-05 (Tests assert behavior with real oracles), and REL-06 (Contract-safe migrations with rollback story). See the per-surface reports for the maturity levels of those criteria.
