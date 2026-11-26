# Store-review benchmark — Report

- **Date:** 2026-11-25
- **Status:** discovery (no code, no schema change)
- **Full report:** [Journal Apps Review Benchmark](https://claude.ai/artifact/GRVx8uJRxk3ZjpiNx2oVxq). It is a private page: the maintainer opens it and shares it from its Share menu. It holds the 14 patterns with verified quotes, user archetypes, white space, FR vs EN and the full caveats.
- **Method as a reusable skill:** [arkaik#662](https://github.com/alexisbohns/arkaik/issues/662), with the collection, sampling, quote-check and orchestration scripts.
- **Scope:** what users of 12 journaling, emotion and memory apps love, hate and leave over, read through their store reviews, then mapped onto what Pebbles has shipped, planned and decided.

---

## 1. Corpus and method

- **Apps:** Stoic, Rosebud, Day One, Journey, Daylio, Bearable, How We Feel, Reflectly, Finch, Grid Diary, Polarsteps, 1 Second Everyday.
- **Collection:** 26,903 reviews, pulled on 25 Nov 2026 by script.
  - App Store: Apple's public review feed, about 500 most recent per country, US, UK and FR.
  - Google Play: the newest ~1,200 English-US and ~400 French reviews per app.
  - No author names were collected.
- **Close reading:** 4,112 reviews (314–360 per app, stratified by stars, weighted toward 1–3★ and long reviews), coded by six agents against five finding categories and ten fixed lenses.
- **Synthesis and checks:**
  - One agent synthesized the patterns across the 12 apps.
  - One agent mapped them onto this repo: specs, decision log, migrations, client code.
  - An adversarial agent checked every claim about Pebbles against the repo and corrected it where it was wrong.
  - All 628 quotes were verified verbatim against the raw reviews.
- **Not committed:** the raw reviews (third-party text) and the working files. Re-running the scripts in arkaik#662 reproduces them; results will differ slightly as new reviews arrive.

## 2. Theme map

Each cell shows the share of an app's reviews that mention a theme and the average rating of those reviews. These are keyword matches over every collected review: they show how loudly a theme comes up, not how many users feel it. "Since 2025" is the share of reviews dated 2025 or later, so Reflectly and Grid Diary describe older versions of their products.

| App | Reviews | Avg | Since 2025 | Paywall | Export | Bugs/sync | Privacy | AI | Streaks | Simple | Look back |
|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| Stoic | 1,385 | 4.17 | 41% | 18.1% · 3.38★ | 3.2% · 3.84★ | 6.9% · 3.34★ | 2.9% · 3.45★ | 7.6% · 3.64★ | 6.1% · 4.2★ | 12.9% · 4.41★ | 0.9% · 4.23★ |
| Rosebud | 1,423 | 4.46 | 91% | 19.4% · 3.64★ | 2.5% · 4.37★ | 2.2% · 2.39★ | 3.0% · 4.33★ | 19.7% · 4.37★ | 4.8% · 4.59★ | 6.7% · 4.6★ | 2.5% · 4.4★ |
| Day One | 2,712 | 4.04 | 68% | 15.5% · 3.11★ | 6.3% · 3.46★ | 11.0% · 3.18★ | 6.3% · 3.22★ | 5.0% · 3.0★ | 4.6% · 4.22★ | 17.1% · 4.53★ | 6.0% · 4.36★ |
| Journey | 2,492 | 4.06 | 34% | 17.2% · 2.61★ | 6.2% · 3.2★ | 10.4% · 3.1★ | 5.5% · 2.99★ | 3.2% · 3.32★ | 2.5% · 3.47★ | 18.5% · 4.6★ | 5.1% · 4.46★ |
| Daylio | 3,029 | 4.57 | 72% | 18.3% · 4.12★ | 4.7% · 4.2★ | 2.1% · 4.09★ | 4.3% · 4.31★ | 5.0% · 4.37★ | 6.4% · 4.49★ | 15.7% · 4.79★ | 6.3% · 4.77★ |
| Bearable | 1,897 | 4.25 | 61% | 21.9% · 3.82★ | 3.5% · 3.65★ | 6.9% · 3.2★ | 11.4% · 3.87★ | 0.6% · 4.17★ | 9.0% · 4.53★ | 19.9% · 4.69★ | 0.8% · 4.8★ |
| How We Feel | 1,920 | 4.56 | 71% | 5.5% · 4.77★ | 2.6% · 4.08★ | 4.2% · 3.73★ | 6.5% · 3.91★ | 6.2% · 4.33★ | 6.7% · 4.62★ | 15.4% · 4.67★ | 0.8% · 4.38★ |
| Reflectly | 2,599 | 4.2 | 6% | 17.3% · 3.01★ | 0.8% · 3.41★ | 3.4% · 2.76★ | 1.3% · 3.65★ | 1.6% · 2.85★ | 3.2% · 4.28★ | 5.4% · 4.43★ | 1.4% · 4.59★ |
| Finch | 2,890 | 4.58 | 96% | 9.6% · 3.82★ | 1.3% · 3.34★ | 2.7% · 3.21★ | 1.0% · 3.17★ | 3.6% · 4.35★ | 10.0% · 4.34★ | 5.6% · 4.67★ | 0.3% · 4.5★ |
| Grid Diary | 956 | 4.06 | 5% | 16.6% · 2.97★ | 4.6% · 3.32★ | 8.3% · 2.87★ | 5.3% · 2.49★ | 0.3% · 4.33★ | 3.1% · 4.27★ | 15.9% · 4.39★ | 1.4% · 3.46★ |
| Polarsteps | 3,100 | 4.72 | 97% | 1.8% · 4.53★ | 0.8% · 4.2★ | 1.9% · 3.22★ | 2.1% · 4.22★ | 1.5% · 4.35★ | 1.5% · 4.64★ | 22.4% · 4.85★ | 9.2% · 4.91★ |
| 1 Second Everyday | 2,500 | 3.29 | 19% | 22.7% · 2.35★ | 9.0% · 2.45★ | 12.8% · 2.51★ | 1.8% · 2.27★ | 2.5% · 2.67★ | 4.6% · 3.67★ | 12.9% · 4.16★ | 14.6% · 4.25★ |

## 3. In short

1. **The basics count more than features.** The angriest reviews in 9 of 12 apps are about losing something: an archive held for ransom, a lifetime licence revoked, a login that strands years of entries, a trial that silently bills a year. Being free spares Pebbles the money traps, but export, password reset and an app lock don't exist yet, and those are the failures reviewers don't forgive.
2. **Small entries keep the habit, and streaks only help when they forgive.** Backfilling a missed day is the most-loved streak feature; strict streaks cause uninstalls. "No streaks, no guilt" holds up. The decaying Ripple level and the month's bright-to-heavy tone verdict could undo it quietly.
3. **Looking back is why people stay for years**, and it is the best-rated theme wherever it appears. Apps resurface old entries well but rarely let people *find* something. The server-computed wraps put Pebbles on the right side; it has nothing yet for finding things.
4. **AI splits the category.** It works when it is the product and remembers you (Rosebud). Bolted onto a private journal it reads as a breach (Day One), and users ask for an off switch that really removes it. The "never data to train a machine" promise is an asset; the "LLM narration, later" line in the insights design works against it.
5. **Sharing works in small circles and fails as a feed.** Invite-only connections, secret by default, match the circles that work.

## 4. Opportunities, ranked

This is the ranking after the adversarial review. It weighs strength of evidence, fit with Pebbles' principles, and how much each item builds on what already exists. The reasoning and the evidence for each item are in the full report.

**Before launch traffic grows**

1. A self-service export, readable and complete. It keeps the onboarding promise letter's "a file you can open anywhere", and the DPIA (§2.3, §4.2) tracks it as open.
2. Password reset and account recovery (roadmap M55).
3. A free-forever and funding pledge in the promise letter, plus a decision-log entry settling the `max_media_per_pebble` "premium plans" hook.
4. A free app lock and app-switcher shield on iOS and Android.

**Next**

5. Make the forgiving rhythm explicit:
   - say that backfilling counts;
   - reframe Ripple;
   - resolve the tone verdict's conflict with the insights charter;
   - offer an optional "my day ends at 04:00" setting used only to group moments into wraps. Timestamps stay exact.
6. The first slice of the insights engine, deterministic and with no AI.
7. Filters now, text search next, "on this day" in 2027.
8. Guardrails for the M69 reminders: a cap per week, no loss or guilt copy, never after a lowlight.
9. A rehearsed restore, and a decision on a grace window for account deletion.

**Later, or test first:** pebble-derived prompts; presence in the small circle (M53); mixed feelings and personal emotion labels (test first); a yearly wrap (2027); import from Daylio and Day One.

**Parked:** voice notes. They would change the data-safety declaration and lead towards transcription and AI.

**Traps to avoid:**
- clawing back anything once free;
- charging for the lock, export or the past;
- bolting AI on;
- promotion inside the record-a-pebble flow;
- streaks coming back through ratios or reminder copy;
- gamification bloat;
- a growing required minimum in `complete_pebble`;
- redesigning the ritual after V1.

## 5. Caveats

- Store reviewers over-represent anger at billing and data loss, and in-app rating prompts inflate 5★.
- The apps' reviews come from different eras: compare Reflectly and Grid Diary (mostly pre-2025) with Finch, Polarsteps and Rosebud (almost all 2025–26) with care.
- French-store reviews are not always in French. French conclusions for Rosebud, Grid Diary, Bearable and How We Feel rest on fewer than 100 reviews each.
- Quote verification proves a quote exists, not that it is representative.
