# Insights — Discovery Report

- **Date:** 2026-07-30
- **Status:** discovery (no code, no schema change)
- **Companion doc:** `2026-07-30-insights-curation-design.md` (design propositions)
- **Scope:** full audit of what Pebbles collects, where it is (and is not) reflected back to users, what the repo has already decided or reserved on the topic, and the scientific grounding a user-facing insights system should stand on.

---

## 1. Executive summary

Pebbles already collects a remarkably well-shaped corpus for personal insights: every pebble is a point in a valence × arousal space (`positiveness` −1/0/+1, `intensity` 1–3) with exactly one emotion drawn from a 38-emotion / 7-category vocabulary, zero or more of 18 life domains, zero or more souls, optional CBT-shaped reflective cards, a photo, and two time axes. Almost none of this is reflected back to the user: the only shipped insight surface is the Profile stats card (ripple badge, 28-day assiduity grid, three counters). Valence, emotions, domains and souls are captured on every pebble and **never aggregated for the user anywhere**.

The product has been promising this mirror since day one. "Get weekly and monthly wraps" is in the Arkaik top-level product description; nine `idea`-status Arkaik nodes (two wrap flows, six cairn views, `DM-cairn`) reserve the territory; the valence picker ships copy promising weekly/monthly/yearly Cairns; the privacy policy already names "Cairns" as a data category; the deferred "stats page" behind the Profile chevron is a named open gap; the Lab backlog fixture pitches a "Yearly recap". The legal basis (Terms §6.1, §13.4) already exists, including the non-advice disclaimer.

The main risks are equally well-documented in the literature: quantification can kill the joy of the practice (Etkin 2016 — the scientific core of the "quantophrenia" worry), raw negative aggregates fuel rumination, co-occurrence invites false causal readings ("X makes you sad"), and small-N statistics lie. The companion design doc proposes a curated, deterministic, explainable insight engine that is AI-empowerable later but fully valuable without it.

---

## 2. Product vision anchor

Everything below should be read against the product's own words:

- Arkaik `project.description`: *"Pebbles is an atomic diary app allowing to record memories and enrich them with emotions, instants, thoughts, related to people and **get weekly and monthly wraps**."*
- Admin analytics POC (`docs/poc/admin-analytics/admin-analytics.md`): success depends on whether *"the product gives them back **the mirror it promises** (emotional shape, who matters, what domains of their life are full or starved)"*.
- Onboarding copy: *"No streak to protect, no feed to scroll. Just a calm ritual that grows with you."* / *"no blank page, no pressure, no audience."*
- Settled naming decision (2026-05-27, #487): avoid value-stigmatizing language. User-facing valence labels are **Lowlight / Neutral / Highlight**, never "negative/positive". Internal jargon (souls, glyph slugs) must not leak into end-user copy.
- Privacy policy §13.3: *"No analytics tools… no tracking pixels."* User insights must be computed from the user's own records, not from telemetry — and indeed there is no telemetry to compute from (see §3.7).

**Implication:** an insights feature is not a "stats" feature. It is the second half of the core loop — record, then be mirrored — and it must inherit the calm, no-pressure, no-audience register.

---

## 3. What we collect — data audit

Ground truth: 55 migrations in `packages/supabase/supabase/migrations/`, `packages/supabase/types/database.ts`.

### 3.1 The pebble record

| Field | Type / constraint | Insight relevance |
|---|---|---|
| `name` | text NOT NULL | title; text mining out of scope for V1 |
| `description` | text NULL | free "pearl"; presence = `pct_with_thought` quality signal |
| `happened_at` | timestamptz NOT NULL | **life time** — when the moment occurred (user-set) |
| `created_at` | timestamptz NOT NULL | **practice time** — when it was recorded |
| `intensity` | smallint, `1..3` | arousal / magnitude axis: small / medium / large |
| `positiveness` | smallint, `-1..+1` | valence axis: lowlight / neutral / highlight (code name: polarity) |
| `emotion_id` | uuid NOT NULL FK | exactly **one** emotion per pebble → emotion shares sum to 100% |
| `glyph_id` | uuid NULL FK | personal mark; `glyphs.is_custom` = creativity proxy |
| `visibility` | text, default `'private'` | only `'private'` ever written today |
| joins | `pebble_souls`, `pebble_domains`, `collection_pebbles`, `pebble_cards`, `snaps` | the relational richness |

⚠️ The README still says positiveness is "-2 to +2" and describes 5 Maslow domains — both stale (see drift ledger §3.8).

**The two time axes are used inconsistently today**: `v_bounce` counts distinct `happened_at` days, while `v_ripple`, `get_profile_engagement` and every `v_analytics_*` view use `created_at`. Any insight design must adopt an explicit principle (proposed in the design doc: *insights about your life use `happened_at`; insights about your practice use `created_at`*).

**A pebble is a circumplex point.** Russell's circumplex model (valence × arousal, §6.A) is literally the shape of the record: `(positiveness, intensity)` is a 3×3 grid, and the emotion picker already curates which of the 7 emotion categories to surface first *per grid cell* (`emotion-category-ordering`). The data model is insight-ready without any schema change.

### 3.2 Reference axes

- **Emotions:** 38 rows in 7 categories (`joy, pride, peace, fear, anger, shame, sadness`), each category carrying a full color palette (`v_emotions_with_palette`). Category + palette data was hand-entered in Supabase Studio, not migrated — production is the source of truth, `db:reset` does not reproduce it.
- **Domains:** 18 canonical slugs (DB + i18n + seed): `community, currentevents, dating, education, family, fitness, friends, health, hobbies, identity, money, partner, selfcare, spirituality, tasks, travel, weather, work`. There is **no level/order column**; the "revisited Maslow pyramid" framing survives only in stale copy and in `v_analytics_domain_share_weekly.domain_level`, which resolves to NULL for every live domain (its `array_position` list still contains the five legacy Greek slugs). A pebble may carry many domains → domain shares do **not** sum to 100%.
- **Cards:** 4 species — `free` ("Write anything…"), `feelings` ("What did I feel?"), `thoughts` ("What did I think?"), `behaviour` ("What did I do?"). This is the CBT triangle (§6.A) sitting latent in the schema. **The web composer currently always sends `cards: []`** — the card path is unreachable on web; the `V-cards-thoughts` Arkaik node is archived.
- **Collections:** optional mode `stack` (goal) / `pack` (time-bound) / `track` (recurring) — three declared intents with **zero computed progress anywhere**.
- **Souls:** name + glyph only. **No relationship-kind column** (partner/friend/pet/…). Any soul-typed insight either works without it or motivates a (later, optional) enrichment.

### 3.3 Engagement plumbing (user-scoped, exists today)

| Primitive | Grain | Notes |
|---|---|---|
| `v_ripple` | `ripple_level` 0–6, `pebbles_28d`, `active_today` | by `created_at`; thresholds `[1,5,9,13,17,21]` duplicated in 3 clients; `active_today` is UTC and locally patched on iOS/Android, not on web |
| `v_bounce` / `bounces.score` | level 0–7 from distinct active days | by `happened_at`; web client thresholds (`bounce-levels.ts`) **disagree with the SQL view**; retired from iOS/Android display |
| `get_profile_engagement(p_tz)` | `days_practiced`, `assiduity bool[28]` | the **only timezone-aware aggregate** in the product; the sanctioned pattern for user-scoped RPCs (`security invoker`, `set search_path`, client passes IANA tz) |
| `v_karma_summary` | `total_karma`, `pebbles_count` | total = **all** ledger types = spendable, not earned |
| `v_wallet_summary` | balance / earned / spent | |
| `path_pebbles()` | every pebble, unpaginated | the bulk read used by the timeline |

### 3.4 The karma ledger as a richness signal

`compute_karma_delta` is a pure enrichment score per pebble ∈ [1,10]: +1 base, +1 description, +1 per card (cap 4), +1 souls, +1 domains, +1 glyph, +1 snap. It is a ready-made "depth of capture" metric — reconstructible per pebble, no new data needed. Two caveats: deleted pebbles leave net-zero ledger rows behind, and "karma" now means two different numbers (earned vs spendable) since the marketplace.

### 3.5 Admin analytics — patterns, not surfaces

Ten `v_analytics_*` views + `security definer` RPCs gated on `is_admin()`: KPI daily, active users, weekly retention cohorts, pebble volume, enrichment percentages, per-active-user averages, **emotion share weekly**, **domain share weekly**, bounce distribution, quality signals. All are **cross-user with no per-user grain** — nothing is reusable as-is, but the metric semantics (point-in-time counts, gap-filled series, share definitions, "movers" deltas) and the admin UI pattern library (sparklines, donuts, stacked areas, heatmaps, fixtures playground, `TimeRangeTabs`) are directly transferable.

### 3.6 What the user already sees (complete list)

Profile stats card (ripple badge + "X more pebbles to level Y" + 28-cell assiduity grid + Days/Pebbles/Karma tiles) on all three platforms; per-soul and per-collection pebble counts; wallet aggregates; week-roll cairn presence; Lab reaction counts. **That is the entire user-facing derived-data surface.** No charts ship to users on any platform.

### 3.7 What is NOT collected

- **No telemetry at all**: no sessions table, no `pebble_views`, no `analytics_events` (4 of 8 admin quality signals are stubbed `available = false`). "You revisited this pebble N times" has no data source — and adding one would contradict privacy policy §13.3.
- **No analytics/insights consent column** — only `terms_accepted_at` / `privacy_accepted_at`. If insights ship with a sensitivity setting or opt-out, that preference has no home yet.
- **No soul relationship type, no domain ordering, no cairn table** (ROADMAP issue #345: "Large — full product feature").

### 3.8 Drift ledger (do not build on these without resolving)

| # | Drift | Where |
|---|---|---|
| 1 | README: positiveness "-2..+2", 5 Maslow domains | `README.md` vs `pebbles` check constraint, live domains |
| 2 | Two 18-domain slug sets on web: `lib/config/domains.ts` (`finance, sport, passions, event, self-care`) vs DB/i18n canon (`money, fitness, hobbies, currentevents, selfcare`) — composer reads the DB view, config feeds only seed data | `apps/web/lib/config/domains.ts` |
| 3 | `v_analytics_domain_share_weekly.domain_level` NULL for all live domains (legacy Greek `array_position`) | migration `20260501000003` |
| 4 | Bounce thresholds: web `bounce-levels.ts` ≠ SQL `v_bounce` | two threshold tables |
| 5 | Ripple thresholds `[1,5,9,13,17,21]` hand-copied in 3 clients + SQL | drift risk, `packages/shared` is empty |
| 6 | `happened_at` vs `created_at` inconsistently chosen across views | §3.1 |
| 7 | Cards karma-rewarded and admin-measured (`pct_with_thought`) but uncreatable on web | `QuickPebbleEditor.tsx` |
| 8 | FR renders "pebble" three ways (Galets / Caillou / pebble); web app copy is "Vous" while the Lab voice mandates "Tu" | `fr.json` vs lab-note skill |
| 9 | `v_ripple.active_today` UTC bug patched on iOS/Android, not web | `PathView` / `PathScreen` vs web |
| 10 | Privacy policy documents Cairns/therapist stats that don't exist (M56 rewrite planned) | `apps/web/docs/privacy/*` |

---

## 4. Where insights could live — surface audit

Ranked by fit (full inventory in the audit annex of this report's PR):

1. **Behind the Profile stats card.** The chevron to a stats page was explicitly deferred ("Issue 4", spec 2026-05-16) and all three platforms share the same card, props and `PathStatsService`. This is the reserved front door.
2. **Soul detail** — today: name, glyph, `pebbles.length`, flat list. The joined rows already carry emotion + valence + domains. Highest-leverage *new* insight for the least plumbing.
3. **Collection detail** — `stack`/`pack`/`track` declare goal/period/frequency intent; nothing is computed. Mode-aware progress is a self-chosen goal, the ethically safe kind (§6.F).
4. **Path week roll** — each cairn cell already receives its week's full pebble array; a valence/volume texture belongs here and foreshadows the weekly wrap.
5. **The wraps ritual** — the six reserved cairn views (see §5).
6. **`V-mechanic-sheet` + dead `GamificationBlocks` code** — a fully built "explain this metric" dialog pattern with copy, unreferenced on web. The explainability affordance every insight card needs already has a precedent.

Cross-cutting: `packages/shared` is an empty placeholder; every derived-data formula today is hand-ported three times with measurable drift already (bounce, ripple). **Any insight math must live server-side (RPC/view), not in three clients.** This aligns with the RPC-first decision (2026-05-26) and the `security_invoker` decision (2026-07-29, #616): new views are `with (security_invoker = true)` + explicit revoke/grant; the bolt-on `where auth.uid()` view pattern is explicitly not-to-copy.

---

## 5. Prior art & reserved territory

- **Arkaik latent design (all `idea`):** `F-weekly-wrap`, `F-monthly-wrap`, `F-gamification` flows; `V-weekly-cairn-intro/-cairn/-cairn-review` and the monthly triplet; `V-home`, `V-bounce-tempo`, `V-collection-rise`, `V-achievements`; `DM-cairn` ("Weekly or monthly wrap stacking pebbles into a summary"), `DM-achievement`, `DM-thought`; endpoints `GET /cairns/weekly`, `GET /cairns/monthly`, `GET /bounce`, `GET /achievements`. Note the map inconsistency: wrap flows hang off `V-home` (`idea`) while the shipped root is `V-timeline`.
- **Shipped promises:** the valence picker copy ties intensity to wrap cadence — small → "wrapped in my weekly Cairn", medium → monthly, large → **yearly** (a yearly Cairn exists nowhere else). The Path renders one decorative Rive cairn per week already.
- **Legal:** Terms §6.1 already licenses "generate anonymized statistics and insights"; §13.4 mandates the non-advice disclaimer; §13.1–13.2 "not a medical device… does not provide psychotherapy". Privacy policy already names Cairns as a data category (flagged as fiction to fix in M56 — an insights ship would *un-fiction* it).
- **Adjacent milestones:** M48 Achievements (`check_achievements()` idempotent RPC, badges cosmetic in v1) and M50 Public profiles (`get_public_profile` projects rings + assiduity; hard rule: never un-scope the private views). Insights sit **outside** the ten v1.0-gating milestones (M45–M57) — this is post-launch surface, which buys design time but demands the design be launch-compatible (no schema landmines).
- **User appetite signal:** the Lab backlog fixture "Yearly recap — A look back over your path, one year at a time" is the repo's closest authored insights pitch.

---

## 6. Scientific foundations

The design should be groundable claim-by-claim. Summary of the load-bearing literature, with the design implication each carries.

### A. Structure of affect — what a pebble already encodes

- **Circumplex model of affect** (Russell 1980; Posner, Russell & Peterson 2005): affective states organize along valence × arousal. `positiveness` is a 3-step valence scale, `intensity` a 3-step arousal proxy: every pebble is a circumplex point plus a discrete label. → Insight families can be built on the existing schema, no new capture needed.
- **Emotional granularity** (Barrett 2006, 2017; Kashdan, Barrett & McKnight 2015): people who differentiate emotions finely regulate them better. → An insight reflecting the breadth/precision of a user's emotion vocabulary nudges a scientifically supported skill; the 38-emotion picker is the instrument.
- **Emodiversity** (Quoidbach et al. 2014, *JEP: General*, N≈37k): variety and evenness of experienced emotions predicts health beyond mean affect — for positive *and* negative emotions. → "Richness of your palette" is a legitimate, computable insight (`emotion_id` frequencies).
- **Affect labeling** (Lieberman et al. 2007; Torre & Lieberman 2018): naming feelings dampens amygdala response — implicit emotion regulation. → Recording a pebble *is* the intervention; insights can honestly reinforce the practice itself.
- **Expressive writing** (Pennebaker 1986; Pennebaker & Chung 2011) and the CBT triangle (Beck 1979): structured writing about feelings/thoughts/behaviour has reliable benefits. → The `feelings/thoughts/behaviour` card species are a latent micro-intervention; depth-of-reflection insights are supported (once cards ship on web — drift #7).

### B. Well-being frameworks — what "good" looks like

- **Affect balance / SPANE** (Diener et al. 2010): well-being tracks the balance of positive to negative experience, not the absence of the negative. → Valence-texture insights present both poles as normal and informative; a lowlight is data, not failure.
- **Broaden-and-build** (Fredrickson 1998, 2001) — with the mandatory caveat: the "critical positivity ratio" (Fredrickson & Losada 2005) was mathematically debunked (Brown, Sokal & Friedman 2013). → **No insight may present a numeric positivity ratio as a threshold or target. Ever.**
- **PERMA** (Seligman 2011) / **psychological well-being** (Ryff 1989): well-being is a multi-dimensional portfolio. → Prefer a portfolio view (domains, souls, emotions, practice) over any single score. **No composite "happiness score".**
- **Three Good Things / gratitude** (Seligman, Steen, Park & Peterson 2005 RCT; Emmons & McCullough 2003): noticing positive events produces lasting, modest well-being gains. → Recording highlights is structurally this exercise; celebrate the practice, keep claims modest.
- **Self-determination theory** (Ryan & Deci 2000; Deci, Koestner & Ryan 1999): intrinsic motivation thrives on autonomy/competence/relatedness and is undermined by controlling rewards. → Insights inform and celebrate; never pressure. Matches the shipped "No streak to protect" promise.

### C. Memory, retrospection, savoring — why wraps and resurfacing work

- **Peak–end rule** (Kahneman et al. 1993; Redelmeier & Kahneman 1996; Kahneman 2011): retrospective evaluation is dominated by the peak and the end, not the mean. → Wraps lead with the peak moment and how the period closed; averages are demoted to the advanced layer.
- **Day Reconstruction Method** (Kahneman, Krueger, Schkade, Schwarz & Stone 2004, *Science*): structured episodic reconstruction is a validated retrospection instrument; Pebbles sits between ESM and DRM.
- **Fading affect bias** (Walker, Skowronski & Thompson 2003): negative affect fades from memory faster than positive. → Resurfacing old pebbles disproportionately re-delivers positive affect; "on this day" is emotionally safer than it looks (still valence-gated, §E).
- **Savoring / positive reminiscence** (Bryant 2003; Bryant & Veroff 2007): deliberately attending to past positives amplifies their benefit. → Flashbacks are an intervention, not just retrieval.
- **Nostalgia** (Sedikides & Wildschut research program): increases social connectedness, meaning, self-continuity. → Soul anniversaries and "your history with X" have this backing.
- **Hedonic adaptation** (Brickman & Campbell 1971; Lyubomirsky 2011): identical stimuli fade; variety and appreciation slow adaptation. → Rotate insight types; scarcity is a feature. A permanently identical dashboard adapts into invisibility.

### D. Relationships — why soul insights are the crown jewels

- **Harvard Study of Adult Development** (Vaillant 2012; Waldinger & Schulz 2023): relationship quality is the strongest predictor of long-term health and happiness. **Holt-Lunstad, Smith & Layton 2010**: social integration's mortality effect rivals quitting smoking. → A per-soul insight surface aligns the product with the most robust finding in the field.
- **Social baseline theory** (Coan, Schaefer & Davidson 2006; Coan & Sbarra 2015): the brain treats social proximity as its default risk-regulation resource. → A soul who co-occurs with hard moments is plausibly **support, not cause**. Framing must honor this: "who's there in your hardest moments", never "who brings you down".
- **Capitalization** (Gable, Reis, Impett & Asher 2004): sharing good news with responsive others amplifies it beyond the event itself. → Recording a highlight *with* a soul is a capitalization act worth reflecting back.
- **Gottman's interaction ratios** (Gottman & Levenson 1992): descriptive lab science about observed couple conflict — **never a prescriptive app metric**; must not appear as a target number in copy.

### E. Risks the design must engineer against

- **Rumination** (Nolen-Hoeksema 1991, 2008): repetitive passive focus on negative affect worsens outcomes. Raw feeds of one's own negative data are rumination fuel.
- **Self-distancing** (Kross & Ayduk 2011, 2017): adaptive reflection needs a distanced perspective; immersed replay of negatives harms, distanced appraisal helps. → Lowlight-tinted insights are framed at pattern/trajectory/care altitude, never as immersive replay.
- **The hidden cost of measurement** (Etkin 2016, *Journal of Consumer Research*): quantifying an enjoyable activity can reduce intrinsic motivation and enjoyment. This is the scientific core of the quantophrenia worry (the term: Sorokin 1956). → Insights are scarce, curated, narrative-first; the granular layer is opt-in, never the default face.
- **Negativity bias** (Baumeister et al. 2001; Rozin & Royzman 2001): bad is stronger than good; one harsh insight outweighs five kind ones. → Asymmetric caution on anything lowlight-derived: sensitivity gates, agency framing, and **no ranking of souls or domains by negativity, ever**.
- **Selection bias** (vs. ESM: Csikszentmihalyi & Larson 1987; EMA: Shiffman, Stone & Hufford 2008): pebbles are self-initiated, event-contingent records — the corpus is what the user *chose to record*, not their life. → Every insight is an insight about the record. Copy says "your pebbles", never "your life is X". This honesty is also the legal shield (Terms §13.4).
- **Small-N instability**: percentages and trends on tiny samples mislead. → Per-insight minimum-support gates; below them, degrade to milestones and mirroring, not statistics.
- **Reward-loop caution** (Schultz 1997 reward-prediction error; SDT undermining): variable-reward mechanics tip reflection into compulsion. → No push-notification slot machines; insights reinforce meaning, not checking.

### F. Habits & momentum — reinforcement that respects the user

- **Habit formation** (Lally et al. 2010): ~66 days median to automaticity; missing one day costs nothing measurable. → Rhythm framing, forgiving by construction — which the assiduity grid already embodies (it shows presence, not chains).
- **Fresh start effect** (Dai, Milkman & Riis 2014): temporal landmarks boost aspirational behavior. → Wraps timed to week/month/year boundaries double as fresh-start springboards.
- **Goal-gradient** (Kivetz, Urminsky & Zheng 2006): progress accelerates near a self-chosen goal. → Milestones and collection `stack` progress tap this ethically because the goal is the user's own.
- **Maslow, revised** (Kenrick et al. 2010; Tay & Diener 2011, N≈61k across 123 countries): need fulfillment contributes to well-being in parallel, not as a strict ladder. → The 18 domains form a **portfolio, not a pyramid**; imbalance insights suggest attention, never a required order. (Convenient, since the Maslow ordering is already dead in the data — drift #3.)

---

## 7. Constraints any design must honor (non-negotiable inventory)

1. **RPC-first** for anything multi-table (decision 2026-05-26); aggregate math lives server-side, once.
2. **New views are `security_invoker = true`** + explicit `revoke`/`grant` (decision 2026-07-29, #616). Do not copy the `where auth.uid()` view pattern.
3. **Timezone**: follow `get_profile_engagement(p_tz)` — client passes IANA tz, server buckets.
4. **Drafts are invisible** to all aggregates by construction (separate table, decision 2026-07-29 #639) — keep it that way.
5. **Any new user-owned table** must be appended to `purge_account` section 4 **and** to `scripts/verify-account-purge.ts` (decision 2026-07-29 #631).
6. **Three hand-written clients**, database as the only contract (decision 2026-07-10); `packages/shared` is empty. Client-side insight math would be written three times — don't.
7. **EN/FR mandatory** everywhere; user copy warm, benefit-first; FR is an adaptation, "Tu" register per the Lab voice (the app's "Vous" catalog is drift #8 to resolve, not to imitate blindly).
8. **No telemetry** may be introduced (privacy §13.3); insights compute from records only.
9. **No offline** framing (decision 2026-07-29 #620).
10. **Missing indexes**: no `pebbles(user_id, created_at)` composite, no reverse indexes on `pebble_souls(soul_id)` / `pebble_domains(domain_id)` — per-user aggregate queries need index work.
11. **Terms §13.4 disclaimer** applies to any generated insight; nothing diagnostic, nothing prescriptive-medical.
12. **Arkaik**: an insights ship adds view/flow/model/endpoint nodes and reconciles the nine reserved `idea` nodes (and the `V-home` vs `V-timeline` overlap) — via the hosted project per decision 2026-07-28.

---

## 8. Risk register

| Risk | Severity | Mitigation lever (design doc §) |
|---|---|---|
| Quantophrenia: dashboard sprawl kills the calm ritual | High | Curation-first architecture; scarcity; advanced layer opt-in |
| Rumination fuel from negative aggregates | High | Valence-mix policy, sensitivity setting, distanced framing |
| False causality on souls ("X makes me sad") | High | Framing charter: support-not-cause; no negativity rankings |
| Small-N nonsense insights | Medium | Eligibility gates + cold-start ladder |
| Streak anxiety / SDT undermining | Medium | Forgiving rhythm framing; no loss mechanics |
| Hedonic adaptation of the surface itself | Medium | Rotation memory; seasonal wraps |
| Metric drift across 3 clients | Medium | Server-computed facts only |
| Legal/clinical overreach | Medium | Descriptive copy, "your pebbles" honesty, §13.4 disclaimer surfaced |
| Building on stale semantics (domains, bounce, time axes) | Medium | Drift ledger resolved in phase S0 |

---

## 9. Open product questions

1. **One emotion per pebble is V1** — a multi-emotion model is an explicit admin-analytics non-goal today; insights should assume 1:1 but not paint the schema into a corner.
2. **Souls have no relationship kind** — several strong insight types survive without it; is a light, optional "kind" worth capturing later?
3. **Bounce vs Ripple** — two parallel 28-day mechanics, one retired from display. Insights should build on Ripple + assiduity and treat Bounce as admin-only legacy; confirm and log the decision.
4. **Where does the sensitivity/insight preference live** — `profiles` column vs a new preferences table (purge_account implications either way)?
5. **The yearly Cairn** — shipped copy promises it; is it in scope for the insights roadmap or should the valence picker copy be softened?
6. **FR register** — app copy is "Vous", Lab voice is "Tu". Insights copy is intimate by nature; recommend "Tu" and a harmonization pass (needs its own decision-log entry).
