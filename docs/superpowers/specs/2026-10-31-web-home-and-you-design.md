# Web Home and You — design

**Date:** 2026-10-31
**Issue:** #1384 (web, complete implementation). Siblings: #1382 (iOS redesign), #1383 (Android), #1385 (blog post).
**Milestone:** M67
**Approved design:** mock `c3` of the 2026-10-31 type exploration (Home Paper, You Paper, Home Night, You Night), private hub `https://claude.ai/artifact/7AjH6xYjeupn4SnFQEZV9u`. The decisions the maintainer took on 2026-10-31 (surfaces, type, data contract) are restated here only where the web has to act on them.
**Prior specs:** `2026-10-26-home-tab-and-empty-states-design.md` (Home's behaviour, D1–D14, iOS), `2026-10-29-achievements-page-design.md` (D6, D18), `2026-10-11-web-path-spine-design.md` (D13, fonts), `2026-10-30-web-icon-migration-design.md` (#1313, icons).
**Counted on:** `docs/1384-home-you-spec-web` at `05cf4926` (today's `origin/main`).

## Summary

The web has no Home: signed-in users land on the Path, and `/profile` is a stack of shadcn cards on the colour-world theme. This spec builds Home at `/home` (the current month as week columns, the greeting, the quick actions as one split stone, the first five, the monthly wraps, About Pebbles) and rebuilds `/profile` as You (the pentagon avatar, the name in the hand, Ripples, the counters as one split stone, Glyphs, Achievements, Lab). Both pages render on the brand Material 3 roles through a scoped `.m3` island, set Inclusive Sans from the maintainer's own font files through `next/font/local`, and draw their stones from a TypeScript port of the app's stone geometry. Every number they show is served; the client lays out and renders. The work ships as one stack of seven parts.

## Goal

After the stack merges:

- a signed-in user who opens the web app lands on `/home`, which shows what the 2026-10-31 mock shows, from live data, in Paper and Night, in English and French;
- `/profile` shows You as the mock shows it, and every destination it used to reach (souls, glyphs, connections, collections, the Lab, settings, logout) is still one or two taps away;
- Home and You read colour only through M3 roles and Inclusive Sans only from the committed `InclusiveSans-*.ttf` files; no Google request is made, now or at build;
- the stone shapes on web equal the iOS shapes for the same seed and size (one generator, golden-tested);
- the month grid, the first five and the wraps are operable with a keyboard and read sensibly with a screen reader.

## Non-goals

- iOS and Android (#1382, #1383). The bento layout of the quick actions (an iOS Settings preference) is not ported.
- The month page and its deck on web. A wrap card opens the Path at that month (D13); the web month deck is a follow-up issue.
- A slim month projection for Home. `path_month_wrap` is used as it is (D10); a projection is a separate back-end issue, never client aggregation.
- Flipping the whole web app to M3 or retiring the colour worlds (still a separate decision, log 2026-09-28). Only Home and You move.
- Moving Ysabeau, Reenie Beanie or Caveat off fontsource (D5).
- Deleting the old profile components (`StatsCard`, `ShortcutsRow`, `LabCard`, `CollectionsCard`, `AchievementsShelf`, `ProfileBanner`): `/sandbox/design` and the public profile still import them (D20).
- The People onboarding and Collections empty states of the 2026-10-26 spec.

## What exists and what is built

| Piece | Today on web | This stack |
|---|---|---|
| Home | none; `/` is the public landing, signed-in users are sent to `/path` (`LandingPage`, `login`, `auth/callback`, `useOnboarding`) | `/home`, the signed-in landing (D1) |
| Navigation | Path's dock pill (You, wallet); You's shortcuts row; `BottomNav` and `Sidebar` are dead | a five-destination hub bar (D2) |
| You | `/profile`: `PageHeader`, `ProfileBanner`, `ShortcutsRow`, `StatsCard`, `AchievementsShelf`, `CollectionsCard`, `LabCard`, `LogoutButton` | rebuilt in place (D15–D20) |
| M3 roles | `m3-theme.css`, applied only on `/sandbox/design` (`.m3` on `<html>`) | a scoped island on Home and You (D3, D4) |
| Fonts | `@fontsource-variable/inclusive-sans` in the root layout; Inclusive is used only by `m3-type-*` on the sandbox | `next/font/local` from the maintainer's files (D5) |
| Stones | none on web (the Spine's stones are a different, sprite-based drawing) | `lib/stones/` + `Stone`, `SplitStone`, `Pentagon` (D7) |
| Month week columns | none (`WeekRoll` cairns on the Path are a different drawing); `FlatSeal` + `StoneSprite` exist on the achievements page | `HomeMonth` (D9, D10) |
| `path_month_wrap`, `home_wraps`, `reveal_wrap`, `guides`, `claim_blue_pebble` | never called by web | provider methods + hooks (Part 4) |
| First five | `get_achievements` serves `explorer.firstFive`; `useAchievements` reads catalog + unlocks and fires the check | Home's box (D12) |
| Achievements shelf | `useAchievementsShelf()` (`shelf`, `shelfCount`) | You's cards (D18) |
| Lab | `logs-api.ts` + `useLab.ts`, `LAB_CONFIG.whatsappInviteURL`, `FeaturedCommunityCard` | You's Lab section (D19) |
| Ripples | `components/profile/ripples/RippleBadge` (44 px, digit inside), `useRipple` | reused, one line high (D15) |

## Decisions

**D1. Home is `/home`; You stays at `/profile`.** `/home` is a protected route (`AuthGate.PROTECTED_PREFIXES`) and the signed-in landing: `LandingPage`'s redirect, the `login` and `auth/callback` fallbacks (`next ?? "/home"`), `useOnboarding`'s two pushes and `offline`'s link move from `/path` to `/home`. An explicit `next` still wins. You keeps the `/profile` URL: `PageHeader`'s default `backHref`, the Path pill, `AchievementsPageView`, the sandbox and every bookmark point at it; the visible label becomes "You" (FR "Toi", the iOS tab's copy).
*Rejected:* Home at `/` (the public landing and the PWA start URL for signed-out visitors); renaming `/profile` to `/you` (churn with no reader benefit; a redirect can come later).

**D2. The web gains a hub bar: Home · Path · People · Collections · You.** The iOS order (2026-10-21, four-tab IA, plus Home first). It is a floating pill at the foot of the four hub roots that have no dock of their own (`/home`, `/souls`, `/collections`, `/profile`), built on the dead `layout/BottomNav` and `lib/config/navigation.ts` (whose six items become these five; `record`, `glyphs` and `lab` leave the bar because Home, You and the Path reach them). The Path keeps its dock; its pill gains a Home link before the You avatar. Items carry `aria-current="page"`, visible labels, and the Pebbles icons `home`, `path`, `people`, `collection`, `person` (no lit twin, #1313 D11). The People page gains a "Connections" row, because You's shortcuts row (the only link to `/connections` today) goes (iOS reaches connections from `SoulsListView`). **This is the one call the brief did not settle that changes the app outside Home and You: confirm it before Part 7.**
*Rejected:* a top link row on Home (the mock has none, and You would still need its own); the bar on the Path too (two docks on one page; the Path's dock is #1052's design).

**D3. Home and You are an M3 island; the rest of the app keeps its colour world.** Three changes, all in Part 2:
- `schemeSelector` (`lib/theme/m3-schemes.ts`) emits, for each dark scheme, the descendant form beside the compound one: `.m3.dark, .dark .m3` (and `.m3.dark.m3-contrast-medium, .dark .m3.m3-contrast-medium`, same for high). next-themes puts `.dark` on `<html>`, so an `.m3` element below it must still find the Night scheme. The sandbox, which puts both classes on `<html>`, is unaffected (a descendant selector never matches the root).
- The bridge block in `globals.css` widens from `:root.m3` to `:root.m3, :root .m3`, so shadcn tokens inside the island (`Button`, `Dialog`, `Card`) resolve to M3 roles. `--app-font-sans` and `--radius` follow.
- An island is `<div className="m3 bg-background text-foreground">` rendered by the route layout (server-side, no flash), and `MainContent` toggles `m3` on `<body>` for `/home` and `/profile` exactly as it toggles `path-route`, so the overscroll, the safe-area insets and portalled dialogs and toasts are on the same ground.
The Spine's seals keep their neutral `--spine-*` paper tokens (2026-10-13: the stone palettes were tuned on them).
*Rejected:* hand-written role values in a Home stylesheet (drifts on the next Android export; `m3-schemes.test.ts` exists to prevent exactly that); flipping `<html>` to `.m3` on these routes as the sandbox does (rewrites the colour world on every navigation and flashes).

**D4. Stock, rule and card rule are generated roles.** The card's white `stock` is `surfaceContainerLowest` on Paper and `surfaceContainerLow` at Night; `rule` is `onSurface` at 14 % / 12 %, `cardRule` at 22 % / 18 %. These blends are already a table, `SPINE_TOKENS` in `lib/theme/ios-colorsets.ts`, from which iOS's `stock.colorset` is generated. `renderM3ThemeCss` reads the same three rows and writes `--m3-stock`, `--m3-rule` (`color-mix(in srgb, var(--m3-on-surface) 14%, transparent)`) and `--m3-card-rule` into every scheme block, plus `--color-m3-stock|rule|card-rule` in `@theme inline`. One recipe, two surfaces.

**D5. Inclusive Sans comes from the maintainer's files through `next/font/local`; nothing else moves.**
- Files, committed verbatim: `apps/web/app/fonts/inclusive-sans/InclusiveSans-VariableFont_wght.ttf` (112 064 bytes), `InclusiveSans-Italic-VariableFont_wght.ttf` (114 308 bytes), `OFL.txt`. They are the brief's files, the ones iOS bundles, so the two surfaces share a checksum (Android's build differs; #1383 decides).
- `apps/web/app/fonts.ts` calls `localFont({ src: [{ path: "./fonts/inclusive-sans/InclusiveSans-VariableFont_wght.ttf", weight: "300 700", style: "normal" }, { path: "./fonts/inclusive-sans/InclusiveSans-Italic-VariableFont_wght.ttf", weight: "300 700", style: "italic" }], variable: "--font-inclusive-sans", display: "swap", preload: false, fallback: ["system-ui", "sans-serif"], adjustFontFallback: "Arial" })`. The root layout adds `inclusiveSans.variable` to `<html>`, and `--m3-font-plain` becomes `var(--font-inclusive-sans), system-ui, sans-serif`. `preload: false` because only Home, You and the sandbox set the face: a `@font-face` is fetched when a glyph needs it, so other routes pay nothing (Next preloads every file of a root-layout font otherwise). `@fontsource-variable/inclusive-sans` leaves `package.json`.
- Verified against `node_modules/next/dist/docs` (Next 16.3.8): `src` is relative to the calling file, `weight` takes a range for a variable font, `variable` exposes a CSS custom property, `adjustFontFallback` takes `"Arial"` for local fonts.
- Ysabeau, its SC and Office cuts, Reenie Beanie and Caveat stay on fontsource: they are self-hosted from npm (no request to Google at runtime or build), the Spine depends on the SC and Office cuts, and the brief supplies no Ysabeau files. The fontsource latin Ysabeau carries `lnum`, `pnum` and `tnum` (checked in its GSUB), and its default figures are old-style, which is what the dates want. Home and You layouts import `@fontsource-variable/ysabeau/wght-italic.css` for the italic lines, as `/path` and `/achievements` do.
- TTF, not WOFF2, to keep the bytes identical to iOS. If the transfer weight matters, a lossless `woff2_compress` of the same files is a one-line follow-up.

**D6. Type roles are named utilities in `globals.css`, in rem.** `@utility pb-type-<role>` (the `m3-type-*` precedent; `apps/web/CLAUDE.md` forbids new CSS files). Ysabeau is `var(--font-heading)`, Inclusive `var(--m3-font-plain)`. Sizes are px ÷ 16.

| Utility | Face, size / weight | Extra | Used by |
|---|---|---|---|
| `pb-type-greeting` | Ysabeau 2.5rem / 270 | lh 1.04, −0.024em | greeting |
| `pb-type-lead` | Ysabeau 1.3125rem / 360 italic | lh 1.25 | greeting line |
| `pb-type-date` | Ysabeau 1.0625rem / 540 | old-style (default) | Home date line |
| `pb-type-section` | Ysabeau 1.875rem / 300 | −0.018em, sentence case | every section head |
| `pb-type-figure` | Ysabeau 1.875rem / 300 | `lnum`, −0.01em | "2 of 5", "3", "12 earned" |
| `pb-type-figure-small` | Ysabeau 1.1875rem / 380 italic | | the "of" / "earned" |
| `pb-type-counter` | Ysabeau 2.25rem / 280 | `lnum`, −0.025em | You counters |
| `pb-type-day` | Ysabeau 1.5625rem / 330 | `lnum`, −0.02em | Monday's date |
| `pb-type-annotation` | Ysabeau 0.625rem / 650 | uppercase, 0.06em | month caps, weekday legend (aria-hidden, D-a11y) |
| `pb-type-card-title` | Ysabeau 1.625rem / 340 | −0.015em | announcement title |
| `pb-type-card-month` | Ysabeau 1.4375rem / 400 | | wrap card month |
| `pb-type-kicker` | Ysabeau 0.9375rem / 400 italic | old-style | "Lab notes · 31 October", wrap year, timeline dates (upright 500) |
| `pb-type-since` | Ysabeau 1.1875rem / 380 italic | old-style | "Member since March 2026" |
| `pb-type-hand` | Reenie Beanie 3.75rem | lh 0.9, −0.075em | the name |
| `pb-type-row-title` | Inclusive 1.0625rem / 600 | lh 1.25 | row titles |
| `pb-type-row-line` | Inclusive 0.9375rem / 400 | lh 1.4 | row lines, card lines |
| `pb-type-label` | Inclusive 0.9375rem / 600 | lh 1.15 | quick action labels, Ripples line, "All updates" |
| `pb-type-caption` | Inclusive 0.78125rem / 500 | | achievement card labels (12.5 px, ≥ 12) |

Figures are always Ysabeau (Inclusive's slashed zero never shows). The greeting is never forced onto one line: at default size "Bonsoir, Alexandrine" fits a 358 px column (the mock's measure); at a larger browser font size it wraps (`text-wrap: balance`) rather than clipping, which WCAG 1.4.10 would fail.

**D7. The stones are one shared TypeScript port, `lib/stones/`.** The source is the exploration kit's `stones.js`, which is itself the port of iOS `ShortcutStones` and `StoneRandom`; for the row (n = 3, seed `camera|fixed|rock|q11`) its `split` and iOS `splitRow` are the same algorithm line for line.
- `stone-random.ts`: `stoneRandom(seed: string): () => number`, the murmur-style hash and xorshift-multiply step with `Math.imul` and `>>> 0`. Golden: the first three draws for `camera|fixed|rock|q11` are `0.9302541588, 0.5075708053, 0.6537410403`.
- `stone-outline.ts`: `stoneOutline(w, h, seed, opt?)`, four unequal superelliptic corners (base corner 34 × [1.15, 0.95, 0.8, 1.05], ±10 %), bowed sides, fit exactly to `w × h`; `resample(points, step)`.
- `stone-split.ts`: `splitStone(w, h, n, seed = "camera|fixed|rock|q11", opt?)`: one rock, `n − 1` parallel leaning cuts (lean 0.11 ± 0.03 unless given), gutter 8, fillet 20, each piece traced by 180 rays with 16 bisections over the rounded-intersection distance field. Options `{ corner, bow, lean, gutter, fillet }`.
- `stone-path.ts`: `toPathData(points)`, resample at 4 then a closed Catmull-Rom spline as cubic Béziers, one decimal.
- `pentagon.ts`: `PENTAGON_PATH`, the squircle pentagon of the mock (128 × 122, centre (64, 66), radius 64, corners a cubic over 28 % of each side with handles at 4 %, sides bowed 2 units), computed once at module load. Golden prefix `M47.0 14.4C61.6 3.8 66.4 3.8 81.0 14.4Q95.6 22.5 107.8 33.8`.
- Components (`components/stones/`): `Stone` (one stone behind its children, `seed`, `fill` as a Tailwind fill class, `corner`, `bow`), `SplitStone` (n cells over one rock, each cell a slot), `Pentagon`. A stone takes its size from its box: `useElementSize` (a `ResizeObserver` hook in `lib/hooks/`) measures, a module cache keyed by `(kind, seed, w×4, h×4, opts)` (iOS's quarter-point key, 24 entries) memoises the path, and until the first measurement the box paints the same fill with a `rounded-[22px]` radius, so there is no layout shift and no empty frame on the server render. The SVG is `aria-hidden`, absolutely placed under the content (`isolate`, `-z-10`), `overflow-visible`.
- Seeds and options per site, from the mock: quick actions `splitStone(w, 100, 3)` (the shipping row); counters `splitStone(w, 88, 3, "days|pebbles|karma")`; first-five stepper `splitStone(w, 6, 5, "first-five", { corner: 3, gutter: 5, fillet: 2, lean: 0.9 })`; chips `stone(48, 46, "chip-<i>" | "chip-g<i>", { corner: 16 })`; settings button `stone(46, 44, "settings", { corner: 16 })`; WhatsApp `stone(w, h, "whatsapp", { corner: 30 })`; announcement cover `splitStone(286, 64, 3, undefined, { corner: 22, fillet: 14, gutter: 6 })`.

**D8. Surfaces follow the brief's vocabulary.** Stones for actions and side-by-side tiles (quick actions, counters, chips, settings button, WhatsApp). Cards for content you browse (wrap cards, achievement cards, the announcement): the shadcn `Card` primitive with one utility, `pb-card` (`bg-m3-stock`, radius 18 px, no border, padding reset; Paper shadow `0 1px 2px rgb(0 0 0/.05), 0 8px 22px rgb(0 0 0/.07)`; Night `0 0 0 1px var(--m3-rule), 0 8px 22px rgb(0 0 0/.35)`). One box for the first five: `bg-m3-surface-container`, radius 26 px. Secondary tiles are flat: no border, no shadow. Lists (About, Glyphs) have no separators inside the box and no box outside it.

**D9. A tap on a week column opens that week on the Path.** The brief's open question. Yes, as in the wrap: each column is one link, `/path?week=<Monday, YYYY-MM-DD>`. It gives the grid five or six focusable targets instead of thirty-five cells, one accessible name per week, and a destination that already exists. `PathScreen` gains `?week=` beside `?focus=` (the same consume-once mechanics, `closestEntry` on that Monday); `week` is parsed by a pure `parseWeekParam` that accepts only a valid ISO date and ignores anything else. A week after today still links (the Path settles on the closest week it has).
*Rejected:* a tap opening the day (seven times as many targets, most of them empty); no tap at all (the grid then reads as decoration to a screen reader, and the wrap sets the expectation).

**D10. The month grid reads `path_month_wrap` as served.** `p_month` is the first day of the current month in the reader's zone, `p_tz` the browser's IANA zone (`Intl.DateTimeFormat().resolvedOptions().timeZone`, as `get_achievements` is called). A column per entry of `weeks[]`, each day placed by date from `cover.days[]` (the iOS `WeekColumn.columns` rule): a date outside the month is blank, a day without a pebble is a socket, a day with pebbles shows up to three seals in served order. `path_month_wrap` omits `cover` and `weeks` when the month has no pebble yet, so then the columns are laid out from `days_in_month` and `first_weekday` alone (placement, not aggregation): every day a socket, every cairn dotted. The cairn is a port of iOS `CairnGeometry` (`9 + 4.2·min(moments, 3) + 2.4·(clamp(intensity, 1, 3) − 1)`, widest at the base, flattened 0.62, overlap 0.2, capped at 42) from the served `weeks[].days` pairs; an empty week (`count = 0`) draws one dotted stone. Seals are the existing `FlatSeal` over `StoneSprite` (mounted once by the Home layout), from `pebbleSeal()` and the emotion palette, at `pip × [0.85, 1, 1.15][intensity − 1]` with `pip = 15`: the same print iOS uses for its garland and week columns. A slim projection (`home_month`) is filed as a separate back-end issue; until it exists Home pays for the heavy sections it ignores.

**D11. Quick actions: Camera · Quick moment · New pebble, one split stone, in that order (2026-10-26 D3).** Fill `bg-m3-primary-container` (as `fill-m3-primary-container`), ink `text-m3-on-primary-container`, labels `pb-type-label`, icons `camera`, `pebble-flat`, `plus-circle` at 28 px. Each cell is a `<button>` or link the size of its cell (≥ 44 px), with a focus ring inset into the cell.
- *Camera:* a visually hidden `<input type="file" accept="image/*" capture="environment">` labelled by the cell. The picked photo goes through `useCapturePhoto` (pick, EXIF date, upload, cleanup rules) and `saveMoment(captureSavePayload({ name: "", happenedAt, snap }))`, `happenedAt` the EXIF date or now; then a toast "Moment saved · Name it" whose action opens `/pebble/<momentId>`. A failed upload or save says so in the toast and deletes the upload (`useCapturePhoto`'s rules). iOS D12, with the browser's camera.
- *Quick moment:* a shadcn `Dialog` (bottom-aligned under 640 px): name first, autofocused; date and time prefilled to now through the existing `InlineDatePicker` and `TimeStepPicker`; "Save" closes, "Save and complete" routes to `/record/complete/<id>?from=capture`. iOS D13.
- *New pebble:* a link to `/record` (Capture, whose last step already offers "Make it a pebble now"). iOS D14.

**D12. The first five read the unlocks; the claim stays; the box leaves after it.** Home calls the existing `useAchievements()` (catalog + unlocks, and it fires `check_achievements()` first: Home's "check on appear", 2026-10-26 D6). A pure `firstFiveState(unlockedSlugs)` ports iOS `HomeChecklist`: `unavailable` (catalog missing a slug), `inProgress` (n of 5), `claimable` (all five, no `blue-pebble`), `done` (`blue-pebble` unlocked). The box shows while the state is `inProgress` or `claimable`; "only shown until done" in the brief means until claimed, because the 2026-10-26 decision makes the claim the ceremony that ends the list. `claimable` adds a sixth filled row, "Claim the blue pebble", which calls `claim_blue_pebble()`; a returned row is presented through the same path `fireAchievementCheck` uses for a granted badge, and an empty result is "not granted", never success (log 2026-10-26). Rows: the blob mark (filled `primary` with an `on-primary` tick when done, outlined `outline` otherwise), title, line, a chevron only on an open task. Done rows are plain text, not links. Open rows link straight to the action: moment → the Quick moment dialog; pebble → `/record/complete/<latest bare moment>` or `/record`; collection → `/collections`; soul → `/souls`; glyph → `/carve`. The iOS explainer sheets (illustrations built from real components) are not ported.
The cut-stone stepper above the rows is `aria-hidden`; the figure "2 of 5" is the text that says it.

**D13. Monthly wraps: served, unfolded once, opened on the Path.** `home_wraps(p_tz)` (finished months with a pebble, newest first, twelve at most). A card (`pb-card`, 152 px wide, in a horizontal list): a ring of the month's days, a dot per day (`stroke-m3-outline-variant`), filled `fill-m3-primary` where `cover.days` has the day, the served `count` in the centre (`pb-type-counter` at 34 px), the month and year under it. The ring's day total is calendar arithmetic on the served `month`. A folded month (`revealed_at` null, 2026-10-26 D5) shows its name and a turned corner and no ring; tapping it opens a `Dialog` with the count and one filled "Unfold" button, which calls `reveal_wrap(p_month, p_tz)` and refetches. A revealed card links to `/path?week=<Monday of the week holding day 1>`. No finished month: one dashed card, "<Month> folds into your first wrap on the <last day>." (2026-10-26 D4). The section head carries the count of cards as its figure.

**D14. About Pebbles: the `guides` table and a page per guide.** A direct single-table select (published rows by `sort_order`; RLS already hides the rest), titles and summaries in the locale with EN fallback. Rows: a `surface-container` stone chip with the guide's icon, title, summary, chevron, linking to `/guides/<slug>`, a new protected route that renders the title, the italic summary and the Markdown body through the same `unified().use(remarkParse).use(remarkRehype).use(rehypeStringify)` processor as `AnnouncementDetail` (raw HTML dropped). Icons by `illustration`: `pebbles` → `pebble-round`, `stones` → `stones-aligned-bottom`, `emotions` → `stones-track`, `valence` → `stones-stair`, `domains` → `pebble-glow-md`, `collections` → `collection`, `souls` → `people`, `glyphs` → `pebble-dotted-glyph`, `karma` → `karma-dot`; an unknown value keeps the chip empty so titles align. A failed read shows nothing under the head (2026-10-26 §1.6).

**D15. You's header.** A settings button at the top right: a `surface-container-high` stone (46 × 44) holding `slider`, a link to `/settings` named "Settings". The avatar: the profile glyph (`GlyphPreview`) or `pebble-dotted-glyph` at 58 px, on the `Pentagon` filled `primary-container`, ink `on-primary-container`. The name: `<h1>` in `pb-type-hand`, wrapping (never truncated) for a long name. "Member since March 2026" (`{ month: "long", year: "numeric" }` in the locale) in `pb-type-since`. Then one line, "Ripples Level 3", `pb-type-label`, beside the existing `RippleBadge` at 24 px. The badge gains two optional props, `className` (default `size-11`, today's) and `showLevel` (default `true`); You passes `size-6` and `showLevel={false}`, because a digit at 24 px is unreadable and the line beside it states the level. No subline. Strokes, tones and the public profile's use are unchanged.

**D16. The counters are one split stone, right under the header, without a title.** `surface-container` pieces; each tile is the figure (`pb-type-counter`, `text-m3-on-surface`, `Intl.NumberFormat` in the locale: "1,240", "1 240") over an 18 px icon and its label on one line (`calendar-date` Days, `pebble-round` Pebbles, `karma-dot` Karma). Sources unchanged: `useProfileEngagement().daysPracticed`, `usePebblesCount()`, `useKarma()`. A value still loading reads "—". It is a `<dl>`: label as `<dt>`, figure as `<dd>`, read in that order.

**D17. Glyphs is a list of three rows to the existing tabs.** My glyphs → `/glyphs?tab=mine`, line "The {count} marks you carved." from `store.marks`; Owned glyphs → `/glyphs?tab=favourites`, "The {count} you collected from others." from `store.entitledMarks` (the `v_glyph_market` `owned` rows the store already loads); Community → `/glyphs?tab=market`, a static line. Icons `draw-dotted`, `pouch`, `petroglyph` on `surface-container` chips. Counts come from the store; no new query.

**D18. Achievements: cards from the served shelf.** `useAchievementsShelf()` (`get_achievements`' `shelf` and `shelf_count`, kept copy first). The head's figure is "{shelfCount} earned". Four cards on a phone (`grid-cols-4`), six from `sm`, each a `pb-card` link to `/achievements` holding the badge's icon (the family map `AchievementBadge` already uses, whatever #1313 has made of it) and its title from `useAchievementCopy`. The head is also a link to `/achievements`. An empty shelf hides the cards and keeps the head with "0 earned".

**D19. Lab: the latest announcement, three released features, the community.** A new `useLabPreview()` in `lib/data/useLab.ts`, on the Lab's established `logs-api` pattern (not the `DataProvider`, which the Lab has never used): `fetchAnnouncements(sb, { limit: 1 })` and `fetchChangelog(sb, { limit: 3 })` in parallel through `settledOr`, platform `webapp`/`all` as today.
- The announcement: a `pb-card` link to `/lab/announcements/<id>`; a cover band (the log's `cover_image_path` through `labAssetUrl` when it has one, otherwise the split stone in `primary-container` over `color-mix(in srgb, var(--m3-primary-container) 35%, var(--m3-stock))`), "Lab notes · <date>" (`pb-type-kicker`), the title (`pb-type-card-title`), one line (the summary, `line-clamp-2`).
- The timeline: an `<ol>` of three entries, each a date (`<time dateTime>`, `pb-type-kicker` upright 500, old-style), a dot (`primary` on the newest, outlined after), title and platform line, on a `rule` hairline. Under it an outline `Button` (`variant="outline"`, pill) "All updates" linking to `/lab/changelog`.
- The community callout: a `tertiary-container` stone (`on-tertiary-container` ink), the `whatsapp` icon at 32 px, "Join the community" (`pb-type-card-month` at 22 px), its line, a chevron; an external link to `LAB_CONFIG.whatsappInviteURL` with `target="_blank" rel="noreferrer noopener"` and a visually hidden "(opens in a new tab)".
- The `whatsapp` icon is on `feat/icon-whatsapp`, not merged: Part 6 starts after it lands, or ships the callout with `people` and swaps the icon in a one-line follow-up (D21).

**D20. You keeps logout and the old components stay.** The mock ends on the Lab; logout is not in it but must exist (iOS keeps `ProfileLogoutButton`). `LogoutButton` stays at the foot of You, quiet (`variant="ghost"`). `CollectionsCard` and the shortcuts row leave the page (Collections and People move to the hub bar, D2); the components themselves stay, because `/sandbox/design` and the public profile import them. Deleting what becomes dead is a follow-up issue (repo rule: no refactor without approval).

**D21. Icons are Pebbles icons, imported directly; chrome stays lucide.** Every new site imports from `@pbbls/icons` and sizes with a Tailwind `size-*` class (never the `size` prop, #1313 D7). Chevrons stay lucide `ChevronRight` (#1313 D1). If #1313 Part 1 has landed, sites use its `PBBLS_ICON_SIZE` names; if not, the raw classes, and #1313 adopts them. Overlap with #1313: this stack rewrites `app/profile/page.tsx` (which #1313 Part 2 touches for the settings icon) and `lab/FeaturedCommunityCard` is not touched. Whichever stack merges second rebases; a #1313 task on a line this stack removed becomes a no-op.

**D22. Layout.** One reading column, `max-w-md` (28rem) centred, `px-4`, sections `gap-11` (44 px, the mock), safe-area padding as `MainContent` gives other routes, plus room for the hub bar. No spread at 900 px: Home and You are short pages.

**D23. Loading and errors.** The greeting, the quick actions and every section head render at once. Each data section keeps its last value while it reloads (a module-level cache keyed by user, the `achievements-cache` pattern) and, on error, shows one quiet italic line with a Retry button ("Couldn't load your months."), except About (nothing) and the Lab (each half alone, `settledOr`). Every Supabase call is wrapped in `withTimeout(…, 10000, "<label>")` and logs `console.error("[home] …")` / `"[you] …"` on failure.

## Data contract

| Section | Call | Kind | Notes |
|---|---|---|---|
| Month grid | `path_month_wrap(p_month, p_tz)` | RPC, read | D10; captured payloads `scripts/oracles/path-wrap/payloads/month-{full,thin,quiet}.json` |
| First five | `check_achievements()` then `achievements` + `achievement_unlocks` | existing `useAchievements` | D12 |
| Claim | `claim_blue_pebble()` | RPC, write | empty result = not granted |
| Monthly wraps | `home_wraps(p_tz)` | RPC, read | payload `scripts/oracles/home/payloads/home-wraps.json` |
| Unfold | `reveal_wrap(p_month, p_tz)` | RPC, write | |
| About | `guides` select, `published = true` order `sort_order` | single-table read | no RPC (AGENTS.md) |
| Camera, Quick moment | `save_moment` via `useMoments().saveMoment` | existing | |
| Counters, Ripples | `get_profile_engagement`, `v_ripple`, store counts, `v_karma_summary` | existing hooks | |
| Achievements | `get_achievements(p_tz)` shelf | existing `useAchievementsShelf` | |
| Lab | `v_logs_with_counts` | existing `logs-api` | D19 |

No RPC is added or changed, so no cross-surface symmetry or harness work is owed by this stack. New decoders follow the cross-surface rules: they are tested against the captured payloads verbatim (explicit nulls included), and `revealed_at` is parsed tolerantly (fractional seconds, `+00:00`, `Z`); the only date the web emits is `p_month` as `YYYY-MM-01`.

## Accessibility (WCAG 2.1 AA)

- **Month grid.** `<nav aria-labelledby>` named by a visually hidden "October 2026"; an `<ol>` of week links. Each link's accessible name is the week's range and its line, from served values: "5 – 11 October, 6 pebbles" / "28 September – 4 October, no pebble yet". Dates, seals, sockets, the legend and the cairns are `aria-hidden`; the 10 px annotations are therefore not text a reader depends on. Focus ring on the whole column (`focus-visible:ring-2 ring-ring ring-offset-2 ring-offset-background`, rounded). Five or six columns at 320 px are ≥ 44 px wide.
- **Headings.** Home: a visually hidden `<h1>` "Home" is not needed; the greeting is the `<h1>`, section heads are `<h2>`. You: the name is the `<h1>`.
- **Figures** carry their words in the DOM ("2 of 5", "12 earned"); the stepper is decorative. State is never colour alone: a done task has its tick and drops its chevron and its link.
- **Lists** are `<ul>`/`<ol>`; row links take the whole row; chevrons, chips and stones are `aria-hidden`.
- **Dialogs** (Quick moment, Unfold) are shadcn `Dialog`: focus trap, Escape, labelled title.
- **Camera** input is reachable by keyboard through its labelled cell; the result is announced by the toast (`sonner` is a live region).
- **External link** says it opens a new tab.
- **Contrast.** Text pairs used on these pages are tested against the generated schemes (Part 2): `on-surface` and `on-surface-variant` on `surface`, `surface-container` and `stock`; `primary` on `surface` (figures, date); `on-primary-container` on `primary-container`; `on-tertiary-container` on `tertiary-container`; all ≥ 4.5:1 in Paper and Night. `outline` is used only for marks and `aria-hidden` annotations.
- **Zoom and reflow.** rem throughout; the greeting and the name wrap; the wraps row scrolls horizontally with its items focusable; nothing is clipped at 200 %.
- **Motion.** None is added.

## i18n

Two new top-level namespaces, `home` and `you`, inserted as text into `lib/i18n/messages/en.json` and `fr.json` (after `"nav"`), never by rewriting the file (`project_formatting_sensitive_catalogs`). Copy comes from iOS `Localizable.xcstrings` wherever iOS has it (greeting bands and overrides, shortcuts, the five tasks and their lines, wraps, About, the claim row) so both languages match iOS word for word; web-only strings (hub bar, Unfold dialog, Camera toast, "All updates", the external-link hint) are written in both. Plurals are ICU (`{count, plural, one {…} other {…}}`; FR `one` covers 0 and 1). `nav` gains `home`, `people`, `you`. The dateline is `Intl.DateTimeFormat(locale, { weekday: "long", day: "numeric", month: "long" })` with the comma removed for EN ("Saturday 31 October", "samedi 31 octobre"), as iOS's `EEEE d MMMM`.

## Parts (one stack, `feat/1384-…`)

| Part | Branch | Content | Check in the browser |
|---|---|---|---|
| 1 | `feat/1384-web-inclusive-local` | the font files, `app/fonts.ts`, root layout class, `--m3-font-plain`, drop the fontsource package (D5) | `/sandbox/design` type scale |
| 2 | `feat/1384-web-m3-island` | generator descendant selectors + stock/rule/card rule (D3, D4), bridge widening, `pb-type-*`, `pb-card`, `M3Island`, contrast test, a sandbox specimen | `/sandbox/design` |
| 3 | `feat/1384-web-stones` | `lib/stones/`, `useElementSize`, `Stone`, `SplitStone`, `Pentagon`, sandbox specimen (D7) | `/sandbox/design` |
| 4 | `feat/1384-web-home-data` | types, provider methods, decoders, hooks, pure modules (greeting, month columns, cairn, first five, week param) | none (tests) |
| 5 | `feat/1384-web-home` | `/home` and its layout, the month grid, the greeting, the quick actions, the first five, the wraps, About, `/guides/[slug]`, `?week=` on the Path | `/home`, `/guides/stones`, `/path?week=…` |
| 6 | `feat/1384-web-you` | You rebuilt (D15–D20), `RippleBadge` props, `useLabPreview` | `/profile`, `/u/<handle>` unchanged |
| 7 | `feat/1384-web-hub-nav` | landing redirects (D1), the hub bar and the Path pill's Home link (D2), Connections row on People, decisions log | sign in, every hub root, `/path` |

Each part passes alone: `npm run lint`, `npm run test` and `npm run build --workspace=apps/web` (the build is the type gate; `web.yml` does not typecheck). Parts 1–4 are invisible to users; 5–7 are `feat`. Every PR inherits #1384's labels and milestone (M67); Parts 1–6 start `Part N of #1384`, Part 7 `Resolves #1384`.

## Testing

All Vitest, node environment, `lib/**/*.test.ts`; components render through `react-dom/server` inside `NextIntlClientProvider`, the `lib/achievements/page-render.test.ts` pattern.

- `lib/fonts/inclusive-sans.test.ts`: the two TTFs and `OFL.txt` exist at their paths with the brief's byte sizes; `app/fonts.ts` declares both faces with `weight: "300 700"` and `preload: false` (read as text).
- `lib/theme/m3-schemes.test.ts`: every dark scheme's selector list carries the descendant form; each block has `--m3-stock|rule|card-rule` with the `SPINE_TOKENS` sources; the generator output still matches the committed CSS.
- `lib/theme/m3-bridge.test.ts`: the bridge block is found under `:root.m3, :root .m3`.
- `lib/theme/home-contrast.test.ts`: the pairs of the Accessibility section, Paper and Night standard schemes, ≥ 4.5:1 via `contrastRatio`.
- `lib/stones/*.test.ts`: the three `stoneRandom` goldens; `splitStone(358, 100, 3)` gives three pieces, the first starting `M1.5 28.0C1.8 26.7 2.1 25.4 2.5 24.1`; `stoneOutline` fits its box exactly; the stepper's first piece starts `M25.4 1.0C26.8 1.0 28.1 1.0 29.5 1.0`; the chip `stone(48, 46, "chip-0", { corner: 16 })` starts `M0.2 19.2C0.2 17.9`; `PENTAGON_PATH` starts with the D7 prefix; same inputs give the same string (cache hit), different seeds differ. The goldens were produced by the exploration kit, whose row equals iOS `splitRow`.
- `lib/home/greeting.test.ts`: the three bands at 11:59, 12:00, 17:59, 18:00; Monday morning and Sunday evening overrides; an empty or blank name drops ", name"; datelines "Saturday 31 October" / "samedi 31 octobre" in Europe/Paris.
- `lib/home/month-columns.test.ts`: the three captured month payloads verbatim give the right column count, blanks outside the month, sockets, at most three seals per day in served order; a month with no `cover`/`weeks` (October 2026: 31 days, `first_weekday` 3) gives five columns of sockets and dotted cairns; seal widths 12.75 / 15 / 17.25.
- `lib/home/cairn.test.ts`: widths match iOS `CairnGeometry` for (1, 1), (3, 3), (5, 2); a stack taller than 42 flattens; an empty week is one dotted stone.
- `lib/home/first-five.test.ts`: each state from slug sets; a missing catalog slug is `unavailable`; `blue-pebble` alone (data repair) is `done`.
- `lib/data/home-payloads.test.ts`: `home-wraps.json` decodes verbatim; `revealed_at` with fractional seconds, `+00:00` and an explicit null; an unknown extra key is ignored; a guide row with `title_fr` null falls back to EN; `claim_blue_pebble` `[]` decodes to "not granted".
- `lib/utils/week-param.test.ts`: `2026-10-05` accepted; `2026-13-01`, `x`, `""` rejected.
- Render tests: `lib/home/home-render.test.ts` (one link per week with its accessible name; the legend `aria-hidden`; a done task has no link and no chevron; the claim row only when `claimable`; the dashed wrap card when there is none), `lib/home/you-render.test.ts` (the name is the `<h1>`; counters as a `<dl>`; `RippleBadge` with `showLevel={false}` renders no digit; the WhatsApp link's new-tab hint).
- `lib/i18n/home-you-keys.test.ts`: every key under `home`, `you` and the new `nav` keys exists in both catalogues (scoped, so existing drift elsewhere does not fail it).
- Visual, by the maintainer, per part from Part 5: `npm run dev --workspace=apps/web -- --port 3100` from the worktree (after copying `apps/web/.env.local`), Paper and Night, EN and FR, 320 / 390 / 1280 px wide, once with the browser font size at 20 px, against `final/c3-full.png`.

## Risks

- **R1. Two themes in one app.** Moving from the Path (Spine paper) to Home (M3 surface) to Souls (colour world) changes the ground and the accent. Accepted until the separate "web default to M3" decision; the hub bar makes the switch frequent, which is an argument for taking that decision soon.
- **R2. Font weight.** 226 KB of TTF where fontsource served ~30 KB of latin WOFF2, fetched only on Home, You and the sandbox. Mitigation: WOFF2 of the same files (D5).
- **R3. `path_month_wrap` cost.** Home computes the gem, tone and hours it does not show, once per visit. Mitigation: the slim projection issue (D10).
- **R4. Measured stones.** Each stone needs a client measurement; a narrow window resize recomputes three ray-traced pieces (~1.7 M distance evaluations). Cached by size; a `ResizeObserver` callback is rAF-throttled.
- **R5. The hub bar is an IA change** (D2) beyond the two pages; it is isolated in Part 7 so it can be reshaped without touching Parts 1–6.
- **R6. `theme-color`.** `ThemeColorSync` keeps the colour world's meta colour on Home and You; the status bar may not match the M3 surface. Logged as a follow-up rather than special-cased here.

## The brief against the code

1. *"Colours only through the M3 roles."* On web the M3 roles exist only on `/sandbox/design`; every app route runs a colour world, and `m3-theme.css` only matches `.m3.dark` on one element. D3 and D4 are what make the brief possible.
2. *"Web loads its fonts … move Inclusive Sans to these local files, no Google API."* The web already makes no Google request: it self-hosts fontsource packages from npm. The move is for one set of bytes across iOS and web (D5), and Inclusive is today used only on the sandbox.
3. *"`path_month_wrap` already returns `days_in_month`, `first_weekday`, `cover.days[]` and `weeks[]`."* Only once the month has a pebble: `cover` and `weeks` are omitted below one (the function's own header). D10 lays out an empty month from the two always-present keys.
4. The You list drops the shortcuts row, which is the web's only link to `/connections`, and the web has no tab bar to stand in for iOS's (its `BottomNav` and `Sidebar` are dead code). D2.
5. *"Reuse `components/profile/ripples/` as is."* It is 44 px with the level digit inside; at one line's height the digit cannot be read. D15 adds two optional props and changes nothing else.
6. *"Owned glyphs … → the existing glyph screens/tabs."* The web's second tab is "Favourites", the union of owned and favourited glyphs; the count shown is owned only. D17 keeps the tab and labels the row as the mock does.
7. `stock` and `rule` are not M3 roles; on iOS they are generated blends (`SPINE_TOKENS`). D4 generates the same blends for web.
8. The icon-migration spec (#1313) treats `layout/BottomNav` as rendered; nothing imports it. Harmless for #1313; D2 revives it.
9. The `whatsapp` icon is not in `@pbbls/icons` on `main` (D19, D21).
