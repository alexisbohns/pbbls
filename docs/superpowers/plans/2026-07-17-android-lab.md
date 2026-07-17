# Android Lab — feeds, reactions, announcement detail, Profile entry

> Milestone **M44 · Android Lab**, issues #589 (A · data layer), #590 (B · Lab screen), #591 (C · detail/lists/Profile card); design doc `docs/superpowers/specs/2026-07-17-android-lab-design.md` (#588, D1–D11). One plan document — the sub-projects landed as one continuous merge-on-green chain.

## Deliverables (as landed)

- **A (#589):** `Log` + enums with the **D1 platform policy implemented as decided** (`project`/`infra` rows drop via the strict enum, commented in code, pinned by test), `UuidStringSerializer`, `LossyLogList` (per-element drop, non-array throws, no `coerceInputValues`), `LogsService` (four predicates verbatim; changelog `released_at DESC NULLS LAST` + defensive client re-sort per design risk 3; `myReactions` empty-set-without-session; direct `log_reactions` writes — the sanctioned no-RPC pattern), `ReactionToggle` pure transitions, `LabConfig`. Tests: the 8 ported iOS lossy tests + platform-drop + minimal-row absent-optionals, toggle exactness incl. the zero-clamp overshoot, localization fallbacks, cover-URL guards.
- **B (#590):** `LabScreen` (5 concurrent independent fetches, fullscreen error only when all four content feeds fail, optimistic toggle with revert-on-error), `LogTimeline` (three modes, 16dp icon column, 1dp muted connector geometry, changelog date `released_at ?? published_at` locale long date), `ReactionButton` (iOS catalog a11y values — "Remove rock"/"rocks"), `AnnouncementRow`, `FeaturedCommunityCard` (external `ACTION_VIEW` — D8), strings (D6: `lab_load_error` English in both locales, commented), 5 drawables, gallery.
- **C (#591):** `LabMarkdown` pure parser (D5 — the V1 subset exactly: blank-line blocks, plain-text headings, inline-only spans, literal fallbacks; JVM-tested) + `LabMarkdownBody` renderer (`LinkAnnotation.Url` external links), `AnnouncementDetailScreen` (200dp cover, no toolbar title — matched iOS quirk), `LogListScreen` (any failure errors — the D3 asymmetry; reactions in backlog mode only), the **D9 conversion** (detail + see-all as content swaps inside the single Lab route, `BackHandler` unwinds before the route pops, feed state stays resident), `ProfileLabCard` un-hidden (last M41 D11 reversal) + `ROUTE_LAB`, Arkaik (V-lab android, V-announcement-detail, V-log-list, DM-v-logs-with-counts, DM-log-reactions, F-follow-lab, V-profile note — validator green), strings, detail gallery.

## Verification

CI green (lossy suite + platform-drop, markdown suite, toggle suite, model tests; galleries: Lab body light/dark, announcement detail with the full markdown subset); on device (maintainer): Profile → Lab card → live feeds; announcement markdown renders identically to iOS; backlog upvote round-trip (instant, persists, reverts on airplane mode); WhatsApp opens externally; see-all lists scroll full feeds; predictive back unwinds detail → Lab → Profile; fr pass.

## Lessons learned

- (fill at review)
