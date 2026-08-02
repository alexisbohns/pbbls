# Insights — Curation Design Propositions

- **Date:** 2026-07-30
- **Status:** design proposition (no code, no schema change in this doc)
- **Companion doc:** `2026-07-30-insights-discovery-report.md` (audit + scientific grounding; citations referenced as *DR §6.x*)
- **Problem:** Pebbles collects valence, intensity, emotions, domains, souls, cards, photos and two time axes on every recorded moment — and reflects almost none of it back. Surfacing *everything* would produce a quantophrenic, unusable wall of boring data; surfacing *nothing* breaks the mirror the product promises. We need digestible, curated layers that feel thoughtful to a newbie and substantial to an advanced user, reinforce the practice, and are AI-empowerable later without being AI-dependent now.

---

## 1. Design philosophy — a mirror, not a dashboard

A dashboard answers "how much?". A mirror answers "who am I becoming, and who is with me?". Every design choice below follows from treating insights as **the second half of the record loop**: you drop a pebble; later, the water gives something back.

### The charter (ten rules every insight must pass)

1. **Descriptive, never diagnostic.** We reflect the record; we do not assess the person. No scores of the self ("you are 73% happy" is banned), no clinical language (Terms §13.4).
2. **About the record, honestly.** Pebbles are self-chosen records, not life (*DR §6.E selection bias*). Copy says "your pebbles / tes galets", never "your life".
3. **No causal claims from co-occurrence.** Especially for souls: co-presence with lowlights is framed as support, never cause (*DR §6.D social baseline*).
4. **No prescriptive ratios or targets.** No critical positivity ratio (debunked — *DR §6.B*), no Gottman number, no "aim for X% highlights".
5. **Asymmetric gentleness.** Bad is stronger than good (*DR §6.E negativity bias*): lowlight-derived insights are rarer, sensitivity-gated, framed with agency and distance (*DR §6.E self-distancing*), and **nothing is ever ranked by negativity**.
6. **Scarce and rotating.** Few insights, refreshed, varied (*DR §6.C hedonic adaptation*; *DR §6.E measurement cost*). One brilliant card beats six mediocre tiles.
7. **Earned by data, or silent.** Every insight has a minimum-support gate; below it, we mirror and celebrate milestones instead of computing statistics.
8. **Explainable on demand.** Every card can answer "how is this made?" (the `V-mechanic-sheet` pattern). Trust now, AI-grounding later.
9. **Autonomy-supportive.** Celebrate, invite, never pressure (*DR §6.B SDT*; the shipped "no streak to protect" promise). Missing days are never a debt.
10. **Private by construction.** Computed from the user's own rows under RLS; no cross-user comparison ("people like you") in any layer of this design.

### Three altitudes (progressive disclosure)

| Altitude | What | Who it serves | Volume |
|---|---|---|---|
| **Featured** | 1–3 curated insight cards per surface, chosen by the engine | everyone, from day one | scarce |
| **Ritual** | weekly/monthly Cairn wraps — a paced, narrative sequence | the engaged | periodic |
| **Deep layer** | browsable granular data: filters, full distributions, time ranges | the advanced, opt-in | on demand |

The deep layer exists so granularity has a home that is *not* the default face of the feature — that inversion is the anti-quantophrenia move.

---

## 2. Approaches considered

### Approach A — Cairn-first (ritual only)

Build the weekly/monthly wrap flows (the nine reserved Arkaik nodes) and nothing else.

- **For:** flagship experience; matches the product definition ("get weekly and monthly wraps") and the shipped valence-picker promise; naturally scarce and narrative.
- **Against:** ROADMAP #345 already sizes cairns "Large — full product feature" (table + lifecycle + client surface ×3 platforms); all value is gated on period boundaries; soul pages and the deferred stats page stay empty; a wrap needs insight computations *anyway* — building them inside the wrap couples them to one surface.

### Approach B — Deep-layer-first (stats page)

Build the deferred Profile stats page with charts (emotion share, domain share, valence over time), reusing admin patterns per-user.

- **For:** cheapest path (admin metric semantics + chart library exist); satisfies advanced users immediately; fills the reserved chevron.
- **Against:** it is exactly the quantophrenic failure mode — a wall of well-engineered, boring, possibly harmful charts (raw negative trends, small-N noise) with no curation, no framing, no cold-start story. Etkin's measurement cost lands on the core ritual (*DR §6.E*). Rejected as the *first* move; demoted to the opt-in deep layer, last.

### Approach C — Curated Insight Engine (recommended spine)

Build a small deterministic engine: a **catalog** of typed insights, each with eligibility gates, a server-computed **fact**, salience scoring, curation policy, and template copy. Surfaces (soul page, profile, wraps, deep layer) are thin consumers of the same engine.

- **For:** one computation layer serves every surface (soul strip today, cairn ritual tomorrow, LLM narration someday); curation and safety are first-class citizens, not afterthoughts; value ships incrementally from the first slice; deterministic and testable (fixtures like the admin playground).
- **Against:** more upfront design than B; the engine must resist becoming a framework (kept to a catalog + a scorer, both plain SQL/TS).

**Recommendation:** **C as the spine, A as its flagship consumer, B as its final opt-in layer.** The phasing in §8 delivers them in that order.

---

## 3. Architecture — the Insight Engine

No code here; shapes and responsibilities only.

```
 catalog (declarative)          facts (server)              curation (server)         rendering (client ×3)
┌──────────────────────┐   ┌─────────────────────────┐   ┌─────────────────────┐   ┌──────────────────────┐
│ insight definitions  │ → │ per-user fact queries   │ → │ eligibility gates   │ → │ template copy EN/FR  │
│ id, family, surfaces │   │ (security-invoker RPCs, │   │ salience scoring    │   │ card components      │
│ gates, salience      │   │  p_tz, RLS-scoped)      │   │ rotation memory     │   │ "how is this made?"  │
│ framing, templates   │   │ typed JSON + provenance │   │ valence-mix policy  │   │ sheet                │
└──────────────────────┘   └─────────────────────────┘   └─────────────────────┘   └──────────────────────┘
```

### 3.1 An insight instance is a typed fact

Every computed insight is a small structured payload, e.g.:

```json
{
  "key": "soul.gleam-share",
  "subject": { "soul_id": "…" },
  "period": { "kind": "trailing", "days": 90 },
  "metrics": { "co_pebbles": 11, "highlight_share": 0.72, "n_min": 8 },
  "provenance": { "axis": "happened_at", "computed_at": "…", "tz": "Europe/Paris" },
  "template": "soul.gleam-share.v1"
}
```

Facts are computed server-side (one implementation, three consumers — *DR §7.6*), carry their own provenance (the explainability sheet and the future AI layer read the same field), and are rendered client-side from i18n templates. **Numbers never originate on the client.**

### 3.2 Server shape

- One orchestrator RPC per surface family — `get_featured_insights(p_surface text, p_context uuid, p_tz text)` — plus focused fact RPCs where a surface needs a fixed module (e.g. `get_soul_insights(p_soul_id, p_tz)`). All `security invoker`, `set search_path = public`, following the `get_profile_engagement` precedent; new views (if any) `security_invoker = true` + revoke/grant (*DR §7.2*).
- **Time-axis principle (resolves drift #6):** insights about *your life* aggregate on `happened_at`; insights about *your practice* aggregate on `created_at`. Each catalog entry declares its axis; provenance carries it.
- **Rotation memory:** one new user-owned table, `insight_impressions` (`user_id, insight_key, subject_id, surface, shown_at, dismissed`), so curation can enforce novelty across devices. Must be appended to `purge_account` §4 and `verify-account-purge.ts` (*DR §7.5*). No other new tables in phases S1–S3.
- **Index work (prerequisite):** `pebbles(user_id, happened_at)`, `pebbles(user_id, created_at)`, reverse indexes `pebble_souls(soul_id)`, `pebble_domains(domain_id)` (*DR §7.10*).
- **Drafts stay invisible** by construction (separate table). Deleted pebbles disappear from facts (no soft delete) — wraps therefore snapshot *nothing*; they recompute on view, and a confirmed cairn stores only its confirmation (see §6).

### 3.3 AI-empowerable, not AI-first

The engine is deliberately the "perfectly modelized anything" that a future LLM can amplify:

- **Facts are the grounding substrate.** An LLM layer may later *narrate* (stitch several facts of a wrap into prose, adapt tone, translate register) but **never computes, estimates or invents a number** — facts pass through verbatim from provenance-carrying payloads.
- **Templates are the fallback and the benchmark.** Deterministic template copy ships first and remains the render path when no AI is configured; any AI narration is evaluated against the same fixtures.
- **The catalog is the contract.** Adding AI later means adding one renderer, not redesigning the pipeline.

---

## 4. The insight catalog v1

Seven families + the wraps that compose them. Each entry: **gate** (minimum support), **fact** (computation on the real schema), **framing** (charter rules applied), example copy (EN / FR in "Tu", benefit-first, no em dashes in user copy). Valence words in copy use the settled register: highlight / lowlight (EN); FR wording to be settled in the i18n pass (working: "moment lumineux" / "moment sombre" — never "positif/négatif").

### Family M — Mirror & milestones (the cold-start family)

| Key | Gate | Fact | Example copy |
|---|---|---|---|
| `mirror.first-pebble` | 1 pebble | the first pebble, mirrored back | EN: "Your path has begun. One pebble, placed." / FR: "Ton chemin commence. Un premier galet, posé." |
| `mirror.counting` | at 10/25/50/100/250… (created_at) | Nth pebble milestone; goal-gradient near next (*DR §6.F*) | EN: "That was your 50th pebble." / FR: "C'était ton 50e galet." |
| `mirror.first-with-soul` | 1st pebble linked to a given soul | firsts per soul/domain/emotion | EN: "A first moment with Léa is on your path." / FR: "Un premier moment avec Léa rejoint ton chemin." |
| `mirror.new-shade` | user records an emotion slug never used before | vocabulary growth (*DR §6.A granularity*) | EN: "A new shade in your palette: relieved." / FR: "Une nouvelle nuance dans ta palette : soulagé·e." |

### Family P — Peaks & moments (peak–end, savoring — *DR §6.C*)

| Key | Gate | Fact | Notes |
|---|---|---|---|
| `peak.of-week` | ≥3 pebbles in the week | the week's peak: max(`intensity`) among `positiveness = 1`, tie-break recency (`happened_at` axis) | anchor module of the weekly cairn |
| `peak.how-it-closed` | ≥3 pebbles in the week | the period's last pebble (the "end" of peak–end) | never selected when the end is a lowlight *and* sensitivity is off — falls back to `peak.of-week` |
| `peak.on-this-day` | ≥1 pebble ≥1 month old on this date (±1 day) | flashback; prefers highlight/neutral; lowlight flashbacks only in "full" sensitivity (*DR §6.C fading affect bias*) | EN: "A year ago today, you kept this moment." / FR: "Il y a un an jour pour jour, tu gardais ce moment." |
| `peak.snap-gleam` | ≥1 highlight with a snap in period | resurface one photo | savoring with an image beats savoring with a number |

### Family S — Souls (the crown jewels — *DR §6.D*)

| Key | Gate | Fact | Framing guard |
|---|---|---|---|
| `soul.constellation` | ≥3 souls each with ≥2 co-pebbles in 90 d | most-present souls of the period (count over `pebble_souls`) | presence, not ranking by quality |
| `soul.gleam-share` | ≥8 co-pebbles spanning ≥3 weeks | share of highlights among pebbles with this soul | EN: "Moments with Léa tend to gleam: 8 of the 11 pebbles she appears in are highlights." / FR: "Les moments avec Léa ont tendance à briller : 8 des 11 galets où elle apparaît sont des moments lumineux." **Never surfaced as a comparison between souls.** |
| `soul.anchor` | ≥5 lowlight co-pebbles spanning ≥4 weeks, sensitivity ≥ gentle | soul most present in lowlight moments — support framing only | EN: "Léa is often there in the hard moments you chose to keep." / FR: "Léa est souvent là dans les moments difficiles que tu as choisis de garder." (support, never cause) |
| `soul.reunion` | soul with ≥6 co-pebbles historically, none in >3× their median gap | gentle reconnection invitation, opt-in-able | EN: "It's been a while since a moment with Sam joined your path." / FR: "Ça fait un moment qu'aucun souvenir avec Sam n'a rejoint ton chemin." No guilt, no counter. |
| `soul.anniversary` | 1 year since first co-pebble | history with X: first moment + count since (*DR §6.C nostalgia*) | |
| `soul.shared-ground` | ≥6 co-pebbles, ≥50% in one domain | where your moments with X live | EN: "Your moments with Sam mostly live in Travel." / FR: "Tes moments avec Sam vivent surtout dans Voyage." |

### Family E — Emotional palette (*DR §6.A*)

| Key | Gate | Fact | Framing guard |
|---|---|---|---|
| `palette.signature` | ≥8 pebbles in period | modal emotion of the period | descriptive: "the shade you reached for most" |
| `palette.breadth` | ≥15 pebbles in 30 d | distinct emotions used vs the 38 (emodiversity, shown as breadth, never as an entropy score) | EN: "Your palette used 14 of 38 shades this month." / FR: "Ta palette a utilisé 14 nuances sur 38 ce mois-ci." |
| `palette.finer-shades` | ≥6 pebbles of one category with ≤2 distinct emotions, sensitivity ≥ gentle | granularity invitation (*Barrett*): the category has unexplored shades | invitation, never correction: EN "Joy has more shades if you ever want them." / FR "La joie a d'autres nuances si tu en veux un jour." |
| `palette.weather` | ≥8 pebbles this period and ≥8 prior | dominant category shift vs prior period | "more Peace than usual" — descriptive, no target |
| `palette.texture` | ≥10 pebbles in period | highlight/neutral/lowlight distribution (SPANE-flavored balance — *DR §6.B*) | both poles framed as normal; **no ratio target** (charter 4) |
| `palette.quiet-loud` | ≥10 pebbles | intensity profile: share of small vs large moments | ties to the shipped day/week/month framing of the valence picker |

### Family D — Domains (portfolio, not pyramid — *DR §6.F Maslow revised*)

| Key | Gate | Fact | Framing guard |
|---|---|---|---|
| `domain.portfolio` | ≥10 pebbles with ≥1 domain in period | top domains by share (multi-label; shares needn't sum to 100 — copy says "appears in") | portfolio framing |
| `domain.rising` | ≥8 domain-tagged pebbles this and prior period | biggest share gain vs prior period (the admin "movers" pattern, per-user) | |
| `domain.quiet` | domain with ≥6 pebbles historically, none in >6 weeks, sensitivity ≥ gentle | a once-alive domain gone quiet — invitation, not alarm | EN: "Fitness has been quiet on your path lately." / FR: "Le sport se fait discret sur ton chemin ces temps-ci." |
| `domain.gleams-most` | ≥8 domain-tagged pebbles | which domain carries the highest highlight share | positive direction only; the inverse ("your worst domain") is **never generated** (charter 5) |

### Family R — Rhythm & practice (`created_at` axis — *DR §6.F*)

| Key | Gate | Fact | Framing guard |
|---|---|---|---|
| `rhythm.gentle-cadence` | ≥4 weeks of history | days practiced this month vs personal norm | celebratory above norm; **silent** below it (never "you're behind") |
| `rhythm.personal-best` | new max of pebbles-per-week | soft record | "your fullest week yet" — practice, not obligation |
| `rhythm.fresh-start` | new month/year boundary | landmark invitation (*fresh start effect*) | composed into wraps, not pushed |
| `rhythm.life-texture` | ≥20 pebbles | when your moments happen (weekday/weekend, `happened_at`) | descriptive curiosity |

### Family C — Craft & depth

| Key | Gate | Fact | Notes |
|---|---|---|---|
| `craft.depth` | ≥10 pebbles across 2 periods | enrichment trend from `compute_karma_delta` components (souls/domains/snaps/description attach rates) | "your pebbles carry more detail lately" — affect labeling & expressive writing rationale (*DR §6.A*) |
| `craft.reflections` | cards shipped on web (drift #7) + ≥5 cards | card-writing depth across the feelings/thoughts/behaviour triad | **blocked on the web cards gap; do not gate the family on it** |
| `craft.carved` | ≥3 custom glyphs used | creativity mirror (`glyphs.is_custom`) | |

### Wraps — the Cairn ritual (Approach A, powered by the engine)

A wrap is a **composition of facts** presented as a paced sequence (intro → 3–5 insight screens → review/confirm), matching the six reserved Arkaik views.

- **Weekly Cairn** composes: `peak.of-week` → `palette.texture` (week) → `soul.constellation` (week) → `rhythm.gentle-cadence` → review. Review = the reflect-and-confirm screen already described by `V-weekly-cairn-review`; confirming stores only `{period, confirmed_at}` (facts recompute; no snapshot table until a real need emerges — YAGNI per the marketplace precedent "capture data, defer analytics").
- **Monthly Cairn** composes: month peak → `palette.breadth` → `domain.portfolio` + `domain.rising` → `soul.gleam-share` (best-supported soul) → review.
- **Yearly recap** (the Lab-backlog pitch + the valence picker's promised "yearly Cairn"): a later, richer composition — out of scope for v1 phases but the engine makes it a composition problem, not a new system.
- Wraps respect intensity semantics already shipped in the valence picker: small moments star in weekly, medium in monthly, large in yearly compositions (selection weight, not hard filter).

---

## 5. Surface mapping

| Surface | Module | Content (engine slots) |
|---|---|---|
| **Soul detail** (all 3 platforms) | header strip + 1 featured card | fixed strip: co-pebble count, signature emotion with this soul, valence texture; featured slot: best of `soul.*` for this soul |
| **Profile → Insights page** (the deferred chevron destination) | featured stack (3 slots) + practice module | featured: engine's top 3 transversal picks; practice: existing ripple + assiduity + `rhythm.*`; entry point to the deep layer |
| **Path week roll** | 1 slot under the focused week | that week's `peak.of-week` or valence texture; foreshadows the weekly cairn |
| **Cairn wraps** (F-weekly-wrap / F-monthly-wrap) | ritual sequence | compositions in §4; entered from the Path week roll cairn + a gentle Profile card when a period completes |
| **Pebble detail** | 1 contextual echo (later phase) | e.g. "this was your first highlight in Travel" — computed at read, no new plumbing |
| **Deep layer ("Strata")** | opt-in browsable views | full emotion/domain/valence distributions with time-range tabs (admin patterns, per-user RPCs); explicitly labeled as the advanced room |

Naming proposal (decision owed to the maintainer, not assumed): featured cards = **Gleams**; ritual = **Cairns** (already canon); deep layer = **Strata**. All three stay inside the mineral register (pebble, path, cairn, ripple, glyph). Fallbacks: "Echoes" for cards, plain "Insights" for the page. EN/FR naming resolved in the i18n pass with the "Tu" register question (*DR §9.6*).

---

## 6. Curation policy (deterministic)

**Per-surface slot counts:** profile 3, soul 1 (+fixed strip), week roll 1, wrap 3–5. Scarcity is enforced, not aspirational.

**Selection = filter → score → constrain:**

1. **Eligibility filter:** catalog gates (§4) + data freshness (subject has ≥1 new pebble since last shown, or a period boundary crossed).
2. **Salience score** (tunable weights, all inputs deterministic):
   - *novelty*: time since this `(key, subject)` last shown (from `insight_impressions`), never repeat within 14 days;
   - *magnitude*: normalized deviation from the user's own baseline (e.g. share vs trailing-90-day share) — self-referenced, never peer-referenced;
   - *support*: sample size above the gate (more data → more confidence);
   - *timeliness*: period boundaries and anniversaries score higher near their date.
3. **Constraints (the charter, mechanized):**
   - ≤1 insight per family per surface refresh;
   - care-framed insights (`soul.anchor`, `domain.quiet`, `palette.finer-shades`, lowlight flashbacks): **≤1 per refresh, never in the first slot, never two refreshes in a row, only at sensitivity ≥ gentle**;
   - every refresh contains ≥1 purely celebratory or mirroring insight;
   - ties break toward the family least recently shown (rotation across families, *hedonic adaptation*).

**Sensitivity setting** (new user preference; home decided in S0 — *DR §9.4*): `off` (celebratory + neutral only) / `gentle` (default; care-framed insights, softest wording) / `full` (adds lowlight flashbacks and lowlight-texture detail). The setting is explained in plain words at first insight contact.

**Explainability:** every card exposes "How is this made?" → a mechanic sheet stating the fact's inputs, period, axis and gate, in user words. (Pattern and copy precedent: the unwired `GamificationBlocks` dialogs + `V-mechanic-sheet`.)

---

## 7. Cold-start ladder

The feature must feel thoughtful at pebble #1 and substantial at pebble #1000.

| Tier | Data | What unlocks |
|---|---|---|
| 0 | 0 pebbles | nothing; onboarding already promises the mirror |
| 1 | 1–4 pebbles | Family M only (mirroring, firsts) — no statistics of any kind |
| 2 | ≥5 pebbles | + `peak.of-week`, `palette.signature` (descriptive, no comparisons) |
| 3 | ≥15 pebbles and ≥3 active weeks | + soul strip, `domain.portfolio`, `palette.breadth`; **weekly Cairn unlocks** |
| 4 | ≥40 pebbles and ≥6 active weeks | + deltas and trends (`palette.weather`, `domain.rising`, `rhythm.*` norms); **monthly Cairn unlocks** |
| 5 | opt-in, any time after tier 3 | Strata deep layer |

Per-insight gates (§4) still apply within tiers — the ladder bounds the *vocabulary*, gates bound each *utterance*. Newbies get reinforcement from the first pebble (positive reinforcement loop: record → be mirrored → record more); advanced users get compounding depth instead of a wall that was all visible on day two.

---

## 8. Phasing

- **S0 — Foundations (prerequisite, small):** resolve drift ledger items that insights would bake in (domain slug canon; time-axis principle; Bounce-vs-Ripple confirmation; sensitivity preference home; FR register decision). Add the four indexes. Decision-log entries for: time-axis principle, insights charter, Bounce legacy status, FR "Tu" register.
- **S1 — Soul strip (first visible value):** fixed 3-fact strip + `soul.anniversary` on soul detail, via one `get_soul_insights(p_soul_id, p_tz)` RPC. No engine yet, but facts already shaped as §3.1 payloads. Cheapest genuinely new insight in the repo (audit-ranked), on the surface with the strongest science (*DR §6.D*).
- **S2 — Insights page + engine core:** the deferred Profile chevron destination; catalog + eligibility + salience + `insight_impressions` + featured stack; families M, P, E(partial), R. Cold-start tiers live.
- **S3 — Weekly Cairn:** the ritual composition over existing facts; the six weekly Arkaik views become 3 (intro, sequence, review) or stay 3 as reserved; week-roll entry point.
- **S4 — Monthly Cairn + families D, S(full), E(full).**
- **S5 — Strata (opt-in deep layer):** per-user share/distribution RPCs + time-range tabs, admin visual patterns reused.
- **Later:** pebble-detail echoes, `craft.reflections` (after web cards ship), yearly recap, optional soul relationship-kind capture, LLM narration layer (§3.3).

Fits the roadmap reality: insights sit outside the M45–M57 v1.0 gate; S0 is the only piece worth landing pre-launch (it is cheap and prevents post-launch schema regret). Public profiles (M50) must **not** expose any engine output; everything here is private by construction (charter 10).

**Arkaik reconciliation (at each ship, per the arkaik skill):** add `V-insights` (profile destination), `V-soul-insights` (strip), engine endpoint nodes; flip the cairn/wrap nodes from `idea` as they materialize; resolve the `V-home` vs `V-timeline` overlap by re-homing `F-weekly-wrap`/`F-monthly-wrap` onto the shipped Path/Profile; mark `V-bounce-tempo` superseded by the Ripple-based practice module (decision-log entry).

---

## 9. Evaluation & acceptance heuristics (design-level)

- **The boredom test:** would a user screenshot this card? If a card is true but inert ("you have 47 pebbles" past tier 1), it doesn't ship in Featured — it belongs in Strata.
- **The reread test:** every copy template read aloud in both languages sounds like a warm friend, not a report ("Tu" register, benefit-first, no jargon, no em dashes).
- **The harm test:** for each care-framed insight, write the worst plausible misreading; if the copy can't survive it, the framing (or the insight) is cut. Standing examples: `soul.anchor` must not read as blame; `domain.quiet` must not read as failure.
- **The determinism test:** same rows + same tz + same date ⇒ same facts, same selection. Golden fixtures per catalog entry (admin playground precedent), including empty/small-N/timezone-boundary cases.
- **The silence test:** a brand-new user, a sparse user, and a user mid-hiatus each get something kind or nothing at all — never a broken chart, never an accusation.

---

## 10. Out of scope (explicit)

- Cross-user or normative comparisons of any kind; public exposure of any insight.
- Multi-emotion pebbles (V1 stays 1:1; the catalog reads `emotion_id` and survives a future join table).
- Text/NLP analysis of names, descriptions or cards (V1 counts presence/species only).
- Push notifications for insights (wraps are entered, not pushed, in v1).
- New telemetry of any kind (privacy §13.3).
- LLM-generated content (the engine is its prerequisite, not its first consumer).
- A composite well-being score (banned by charter 1, permanently).

## 11. Decisions owed to the decision log when this ships

1. Time-axis principle (`happened_at` = life, `created_at` = practice).
2. The insights charter (ten rules) as a standing product constraint.
3. Bounce = admin-only legacy; Ripple + assiduity are the practice signals.
4. FR register for intimate surfaces ("Tu") and the Galet/Caillou/pebble canon.
5. Naming: Gleams / Cairns / Strata (or the chosen alternatives).
6. Sensitivity preference location (+ purge_account addendum for `insight_impressions`).
