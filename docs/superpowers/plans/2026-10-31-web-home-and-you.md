# Web Home and You — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Home at `/home` and rebuild You at `/profile` as the approved 2026-10-31 mock (`c3`) shows them, on the brand M3 roles through a scoped `.m3` island, with Inclusive Sans from the maintainer's own files through `next/font/local`, stones from a TypeScript port of the app's stone geometry, and every figure served. Spec: `docs/superpowers/specs/2026-10-31-web-home-and-you-design.md` (D1–D23). Issue #1384, milestone M67.

**Architecture:** Part 1 moves one font. Part 2 makes M3 roles usable below `<html>` (descendant dark selectors, a widened bridge, three generated blends) and names the type roles and the card as utilities in `globals.css`. Part 3 ports the stones (`lib/stones/`, pure and golden-tested) and wraps them in measured components. Part 4 adds the reads and writes (provider methods, decoders, hooks) and the pure modules Home needs (greeting, month columns, cairn, first five, week parameter). Parts 5 and 6 build the two pages from those layers. Part 7 makes Home the landing and adds the hub bar.

**Tech stack:** Next.js 16.3 (App Router, Turbopack, `next/font/local`), React 19, Tailwind CSS 4, shadcn base-nova (`@base-ui/react`), next-intl, Vitest (node environment, `lib/**/*.test.ts`, render tests through `react-dom/server`), `@pbbls/icons`.

**Stack (gh stack, seven parts = seven PRs, bottom first):**

| Part | Branch | PR title | Tasks |
|---|---|---|---|
| 1 | `feat/1384-web-inclusive-local` | `chore(web): set inclusive sans from the bundled font files` | 1–3 |
| 2 | `feat/1384-web-m3-island` | `feat(web): let a page wear the brand m3 roles below the root` | 4–9 |
| 3 | `feat/1384-web-stones` | `feat(web): port the app's stone geometry and draw stones` | 10–14 |
| 4 | `feat/1384-web-home-data` | `feat(web): read the month, the wraps, the guides and the first five for home` | 15–21 |
| 5 | `feat/1384-web-home` | `feat(web): build home` | 22–30 |
| 6 | `feat/1384-web-you` | `feat(web): rebuild you, and move log out to settings` (depends on #1386) | 31–37 |
| 7 | `feat/1384-web-hub-nav` | `feat(web): land on home and move between the hub pages` (`Resolves #1384`) | 38–42 |

Labels on every part: inherit #1384's (species `feat`, scope `ui`, `web`; confirm with the maintainer), milestone M67. Part 1 is a `chore` in substance; keep the issue's labels for the stack and add `no-lab-note` on Parts 1–4. Parts 5–7 are user-facing: the Lab Note rule applies (run the `lab-note` skill on Part 7 for the whole feature; on Parts 5 and 6 add `no-lab-note` and say in the body that Part 7 carries the note, or follow the maintainer's current Arkaik-coverage practice). PR bodies for Parts 1–6 start `Part N of #1384`, never `Resolves`. Never write a Kritik finding id in a body. Every commit message ends with the attribution trailer your session's instructions give.

**Before Part 1.** From the main checkout (`/Users/alexis/code/pbbls`), on an updated `main`:

```bash
git fetch origin
git worktree add .worktrees/home-you-build -b feat/1384-web-inclusive-local origin/main
cp apps/web/.env.local .worktrees/home-you-build/apps/web/.env.local
cd .worktrees/home-you-build && npm ci
```

Every command below runs from `.worktrees/home-you-build`. Then check the two external dependencies and note the answers in the Part 1 PR body:

```bash
git log --oneline origin/main -- packages/icons/react/Whatsapp.tsx | head -1   # empty: #1387 (chore/1387-whatsapp-icon) has not merged (Task 36 fallback)
git log --oneline origin/main -- apps/web/lib/icons/pbbls-icon.ts | head -1    # empty: #1313 Part 1 has not landed (use raw size-* classes, spec D21)
```

**Coordination with the web icon migration (#1313), decided 2026-10-31.** Both stacks touch two files: `app/profile/page.tsx` (#1313 Part 2, Task 11: the settings button's `Settings` → `Slider`) and `components/layout/BottomNav.tsx` (#1313 Part 2, Task 8 Step 2: the nav drawings at `PBBLS_ICON_SIZE`). The order is fixed rather than left to whichever merges second:

- **#1313's stack merges first.** It is a pure icon swap and its plan is already on `main`. Part 6 and Part 7 of this stack do not start until `git log --oneline origin/main -- apps/web/lib/icons/pbbls-icon.ts` is non-empty and #1313 Part 2 has merged; Parts 1–5 do not touch either file and run in parallel with #1313.
- **Part 6 rewrites `app/profile/page.tsx` from #1313's version**: the settings button keeps `Slider` from `@pbbls/icons`, sized through `PBBLS_ICON_SIZE`. Part 7 builds `BottomNav` on #1313's version and keeps its Pebbles nav drawings and sizes; the Home item uses `home` / `home.active`.
- **No lucide import is added by this stack.** Once #1313 Part 6 arms the `replaces` lint, a lucide symbol in Home or You fails CI; every icon here comes from `@pbbls/icons`.
- If #1313 stalls, its Task 11 Step 1 and Task 8 Step 2 move into this stack's Part 6 and Part 7 respectively (same edits, same commit messages' intent), and #1313's plan is amended in the same PR so the two never both apply.

**The part gate, run at the end of every part:**

```bash
npm run lint --workspace=apps/web     # exit 0; the problem count must not grow over the part's base
npm run test --workspace=apps/web     # every test file passes
npm run build --workspace=apps/web    # "✓ Compiled successfully" + the route table: the type gate (web.yml does not typecheck)
```

**Visual verification (Parts 2, 3, 5, 6, 7).** `npm run dev --workspace=apps/web -- --port 3100`, Chrome, Paper and Night (the theme toggle in Settings, or the OS), EN and FR (Settings → Language), at 320, 390 and 1280 px, once with the browser font size at 20 px. Compare with `final/c3-full.png` of the exploration (ask the maintainer for the hub link if the scratchpad copy is gone). Check the accessibility tree in DevTools on the elements each part names.

---

## Part 1 — Inclusive Sans from the bundled files (`feat/1384-web-inclusive-local`)

Spec D5. A move: the face is the same family, from different bytes, applied the same way.

### Task 1: Commit the font files

**Files:** create `apps/web/app/fonts/inclusive-sans/InclusiveSans-VariableFont_wght.ttf`, `InclusiveSans-Italic-VariableFont_wght.ttf`, `OFL.txt`.

- [ ] Copy the three files from the brief's folder (`fonts-inclusive/`; originals in `~/Downloads/Inclusive Sans (1)/`) without renaming. `ls -l` must print 112064 and 114308 bytes for the two TTFs.
- [ ] Write `apps/web/lib/fonts/inclusive-sans.test.ts`: reads the three files from `path.join(webRoot, "app/fonts/inclusive-sans/…")` (`webRoot` as in `lib/theme/m3-bridge.test.ts`), asserts the byte sizes, that each TTF starts with `0x00010000`, and that `OFL.txt` contains `SIL OPEN FONT LICENSE`.
- [ ] `npm run test --workspace=apps/web -- lib/fonts` passes.

### Task 2: Load it with `next/font/local`

**Files:** create `apps/web/app/fonts.ts`; modify `apps/web/app/layout.tsx`, `apps/web/app/globals.css`.

- [ ] Re-read `node_modules/next/dist/docs/01-app/03-api-reference/02-components/font.md` (`src`, `weight`, `variable`, `preload`, `adjustFontFallback`, "Using a font definitions file") before writing.
- [ ] `app/fonts.ts`:

```ts
import localFont from "next/font/local"

// Inclusive Sans from the maintainer's files (#1384, spec D5): the bytes iOS bundles.
// Not preloaded: only Home, You and /sandbox/design set it, and a @font-face is
// fetched when a glyph first needs it.
export const inclusiveSans = localFont({
  src: [
    { path: "./fonts/inclusive-sans/InclusiveSans-VariableFont_wght.ttf", weight: "300 700", style: "normal" },
    { path: "./fonts/inclusive-sans/InclusiveSans-Italic-VariableFont_wght.ttf", weight: "300 700", style: "italic" },
  ],
  variable: "--font-inclusive-sans",
  display: "swap",
  preload: false,
  fallback: ["system-ui", "sans-serif"],
  adjustFontFallback: "Arial",
})
```

- [ ] `layout.tsx`: drop `import "@fontsource-variable/inclusive-sans"`; import `inclusiveSans` and set `className={cn("h-full antialiased", inclusiveSans.variable)}` on `<html>` (import `cn` from `@/lib/utils`).
- [ ] `globals.css`: `--m3-font-plain: var(--font-inclusive-sans), system-ui, sans-serif;` (was `"Inclusive Sans Variable", …`). Edit that one line.
- [ ] Extend the Task 1 test: `app/fonts.ts` read as text contains both paths, `weight: "300 700"` twice, `preload: false` and `variable: "--font-inclusive-sans"`; `globals.css` contains `var(--font-inclusive-sans)` and no `"Inclusive Sans Variable"`.

### Task 3: Drop the package, gate, PR

**Files:** `apps/web/package.json`, `package-lock.json`.

- [ ] `npm uninstall @fontsource-variable/inclusive-sans --workspace=apps/web`; `grep -rn "inclusive-sans" apps/web --include='*.ts*' --include='*.css' | grep -v node_modules` prints only `app/fonts.ts` and the test.
- [ ] Part gate. In `next build` output, confirm no `fonts.googleapis.com` / `fonts.gstatic.com` appears (`grep -r googleapis apps/web/.next/static | wc -l` → 0).
- [ ] Visual: `/sandbox/design`, M3 theme, the type scale's body and label rows render Inclusive Sans (DevTools → Computed → Rendered Fonts: "Inclusive Sans" from a `/_next/static/media/*.ttf`). Any other route: the Network panel shows no Inclusive request.
- [ ] Commit `chore(web): set inclusive sans from the bundled font files`; `gh stack init` / submit per the `gh-stack` skill.

---

## Part 2 — the M3 island (`feat/1384-web-m3-island`)

Spec D3, D4, D6, D8.

### Task 4: Descendant dark selectors in the generator

**Files:** `apps/web/lib/theme/m3-schemes.ts`, `m3-schemes.test.ts`, regenerated `apps/web/app/m3-theme.css`.

- [ ] Test first in `m3-schemes.test.ts`: for the fixture schemes, `schemeSelector("dark", "standard")` is `.m3.dark, .dark .m3`; `("dark", "medium")` is `.m3.dark.m3-contrast-medium, .dark .m3.m3-contrast-medium`; light selectors are unchanged (`.m3`, `.m3.m3-contrast-medium`). Keep the existing uniqueness test passing (compare selector lists, not strings, if it needs it).
- [ ] Implement:

```ts
export function schemeSelector(mode: M3Mode, contrast: M3Contrast): string {
  const c = contrast === "standard" ? "" : `.m3-contrast-${contrast}`
  // .dark sits on <html> (next-themes): an .m3 island below it needs the descendant form (#1384 D3).
  return mode === "dark" ? `.m3.dark${c}, .dark .m3${c}` : `.m3${c}`
}
```

- [ ] Update `HEADER`'s comment ("…or an `.m3` element below `.dark`…").
- [ ] `npm run generate:m3 --workspace=apps/web`; the "matches the generator output" test passes; `git diff app/m3-theme.css` shows only the three dark selector lines and the header.

### Task 5: Stock, rule and card rule

**Files:** `apps/web/lib/theme/m3-schemes.ts`, `m3-schemes.test.ts`, `app/m3-theme.css`.

- [ ] Test: every scheme block contains `--m3-stock: var(--m3-surface-container-lowest)` (light) / `var(--m3-surface-container-low)` (dark), `--m3-rule: color-mix(in srgb, var(--m3-on-surface) 14%, transparent)` (12 % dark), `--m3-card-rule` 22 % / 18 %; `@theme inline` exposes `--color-m3-stock|rule|card-rule`.
- [ ] Implement in `renderM3ThemeCss`: import `SPINE_TOKENS` from `./ios-colorsets`, keep the rows named `stock`, `rule`, `cardRule`, and map each `Source` (`[role, alpha]`) to CSS: alpha 1 → `var(--m3-<kebab role>)`, else the `color-mix` above with `Math.round(alpha * 100)`. Light/high-contrast-light read `paper`, dark read `night` (the same split `renderIosColorsets` makes). A `{ hex }` source throws here (none of the three rows has one).
- [ ] Regenerate; test green.

### Task 6: Widen the bridge

**Files:** `apps/web/app/globals.css`, `apps/web/lib/theme/m3-bridge.ts` (comment), `m3-bridge.test.ts`.

- [ ] In `m3-bridge.test.ts`, the block is located by the selector `:root.m3, :root .m3`. Run: red.
- [ ] Change the one selector line `:root.m3 {` to `:root.m3, :root .m3 {` and its comment block ("Only /sandbox/design applies `.m3`" → "/sandbox/design applies `.m3` to `<html>`; Home and You render an `.m3` island (#1384)"). Green.

### Task 7: Type roles and the card as utilities

**Files:** `apps/web/app/globals.css` (append after the `m3-type-*` utilities).

- [ ] One `@utility pb-type-<role>` per row of spec D6, values in rem as written there, Ysabeau as `var(--font-heading)`, Inclusive as `var(--m3-font-plain)`, Reenie as `var(--font-script)`; lining figures as `font-variant-numeric: lining-nums proportional-nums`, old-style left to the face's default; `pb-type-greeting` adds `text-wrap: balance`.
- [ ] `@utility pb-card`: `background-color: var(--m3-stock); border-radius: 18px; border-width: 0; box-shadow: 0 1px 2px rgb(0 0 0 / .05), 0 8px 22px rgb(0 0 0 / .07); &:is(.dark *) { box-shadow: 0 0 0 1px var(--m3-rule), 0 8px 22px rgb(0 0 0 / .35); }`.
- [ ] No test: utilities are exercised by the render tests of Parts 5–6 (they assert class names) and by the visual pass.

### Task 8: `M3Island` and the contrast test

**Files:** create `apps/web/components/layout/M3Island.tsx`, `apps/web/lib/theme/home-contrast.test.ts`.

- [ ] `M3Island({ children, className })`: a server component, `<div className={cn("m3 min-h-full bg-background text-foreground font-sans", className)}>`. Doc comment: why it exists (D3) and that `MainContent` also toggles `m3` on `<body>` (Task 39 wires it for the routes).
- [ ] `home-contrast.test.ts`: parse the real `ColorSchemes.kt` with `parseColorSchemes` (as the drift test does), take `LightScheme` and `DarkScheme`, and assert `contrastRatio ≥ 4.5` for: `on-surface`/`surface`, `on-surface-variant`/`surface`, `on-surface-variant`/`surface-container`, `on-surface`/`surface-container-lowest` (light stock), `on-surface`/`surface-container-low` (dark stock), `primary`/`surface`, `on-primary-container`/`primary-container`, `on-tertiary-container`/`tertiary-container`, `on-surface`/`surface-container` (counters). If a pair fails, stop and report it to the maintainer; do not swap roles silently.

### Task 9: Sandbox specimen, gate, PR

**Files:** `apps/web/components/sandbox/design/` (a new `HomeYouSpecimen.tsx` rendered by the foundations section, inside an `M3Island`).

- [ ] The specimen shows each `pb-type-*` with its name, a `pb-card` with a line of text, and a `bg-m3-surface-container rounded-[26px]` box, in an island, so it can be checked with the sandbox switched to the **Current** theme (the island must stay M3 while the page around it is a colour world) in Paper and Night.
- [ ] Part gate; visual: `/sandbox/design?theme=current` in dark mode: the specimen's ground is `#131315`, text `#E7E2D9`; in light `#FEF9EF`. The rest of the page keeps its colour world.
- [ ] Commit per task (`feat(web): …`), push the part.

---

## Part 3 — stones (`feat/1384-web-stones`)

Spec D7. Pure geometry first, then components.

### Task 10: The seeded generator

**Files:** create `apps/web/lib/stones/stone-random.ts`, `stone-random.test.ts`.

- [ ] Test: `stoneRandom("camera|fixed|rock|q11")` yields `0.9302541588`, `0.5075708053`, `0.6537410403` (to 10 decimals); two generators from the same seed agree for 1 000 draws; the seed is hashed by UTF-16 code unit (a seed with `é` matches a hand-computed value from the kit, or at least differs from `e`).
- [ ] Implement exactly as iOS `StoneRandom` / the kit's `rng`: `h = (1779033703 ^ seed.length) >>> 0`; per code unit `h = Math.imul(h ^ c, 3432918353) >>> 0; h = ((h << 13) | (h >>> 19)) >>> 0`; per draw `h = Math.imul(h ^ (h >>> 16), 2246822507) >>> 0; h = Math.imul(h ^ (h >>> 13), 3266489909) >>> 0; h = (h ^ (h >>> 16)) >>> 0; return h / 4294967296`.

### Task 11: Outline, resample, path

**Files:** create `apps/web/lib/stones/stone-outline.ts`, `stone-path.ts`, `stone-outline.test.ts`.

- [ ] Types: `type Point = readonly [number, number]`; `type StoneOptions = { corner?: number; bow?: number; lean?: number; gutter?: number; fillet?: number }`.
- [ ] `stoneOutline(w, h, seed: string, opt?)` and an internal `outlineFrom(w, h, next: () => number, opt)` (the kit's `outlineWith`, shared by Task 12 so the split draws its rock from the same generator as iOS); `resample(points, step)`; `toPathData(points)` (resample at 4, closed Catmull-Rom, `toFixed(1)`).
- [ ] Tests: the outline's bounds are exactly `[0, w] × [0, h]`; `stonePath(48, 46, "chip-0", { corner: 16 })` (`toPathData(stoneOutline(…))`) starts `M0.2 19.2C0.2 17.9`; `resample` returns ≥ 24 points.

### Task 12: One rock, n pieces

**Files:** create `apps/web/lib/stones/stone-split.ts`, `stone-split.test.ts`.

- [ ] `splitStone(w, h, n, seed = "camera|fixed|rock|q11", opt?)` returns `string[]` (path data), the kit's `split` line for line: rock from the shared generator (nine draws), lean `opt.lean ?? 0.11 + jitter(next(), 0.03)`, cuts at `(k + 1)·wn + (k + 0.5)·gutter`, half-space normal `(h, lean·h)/hypot`, rounded intersection with the fillet, 180 rays from `π·1.12`, 16 bisections, then `toPathData`.
- [ ] Tests: `splitStone(358, 100, 3)` has three pieces, the first starting `M1.5 28.0C1.8 26.7 2.1 25.4 2.5 24.1`; `splitStone(324, 6, 5, "first-five", { corner: 3, gutter: 5, fillet: 2, lean: 0.9 })[0]` starts `M25.4 1.0C26.8 1.0 28.1 1.0 29.5 1.0`; `n < 2` or `w <= (n − 1)·gutter` returns `n` empty strings (iOS's guard); deterministic.
- [ ] Note in the file header that for `n = 3` with the default seed this is iOS `ShortcutStones.splitRow`, and that the goldens come from the exploration kit.

### Task 13: Pentagon and the cache

**Files:** create `apps/web/lib/stones/pentagon.ts`, `stone-cache.ts`, tests.

- [ ] `PENTAGON_PATH` (spec D7 geometry, computed at module load) and `PENTAGON_BOX = { width: 128, height: 122 }`. Test: starts `M47.0 14.4C61.6 3.8 66.4 3.8 81.0 14.4Q95.6 22.5 107.8 33.8`, ends with `Z`.
- [ ] `cachedStone(kind: "stone" | "split", key: string, make: () => string | string[])` with a 24-entry cap, cleared whole when full (iOS's `remember`); the key is built by the components from `seed`, `Math.round(w * 4)`, `Math.round(h * 4)`, `n` and the options. Test: a hit returns the identical array; the 25th insert clears.

### Task 14: Components, specimen, gate, PR

**Files:** create `apps/web/lib/hooks/useElementSize.ts`, `apps/web/components/stones/Stone.tsx`, `SplitStone.tsx`, `Pentagon.tsx`; sandbox specimen.

- [ ] `useElementSize<T extends HTMLElement>()` returns `[ref, size | null]`, a `ResizeObserver` on the border box, updates coalesced to one `requestAnimationFrame`, sizes rounded to quarter pixels.
- [ ] `Stone({ seed, fill, corner?, bow?, className, children })`: `relative isolate`; until measured, the box carries `rounded-[22px]` and the fill as a background class (`fill` is given as a pair, e.g. `{ fill: "fill-m3-surface-container", bg: "bg-m3-surface-container" }`); once measured, an `aria-hidden` `<svg>` `absolute inset-0 -z-10 overflow-visible` with one `<path className={fill}>`.
- [ ] `SplitStone({ n, height, seed?, options?, fill, cells: ReactNode[], className })`: a grid of `n` equal columns with `gap-2` (the 8 px gutter), the measured width, one `<path>` per piece; each cell is a child slot. Render the cells even before measurement (no shift).
- [ ] `Pentagon({ className, children })`: fixed 128 × 122 box, `PENTAGON_PATH` filled `fill-m3-primary-container`, children centred with an 8 px downward nudge.
- [ ] Sandbox specimen in an `M3Island`: the quick-actions row, the counters row, a chip, the settings button, the stepper with two pieces lit, the pentagon, in Paper and Night.
- [ ] Part gate; visual: the row's three pieces fit back into one rock with even gutters, at 320 and 390 px; resizing the window redraws without flicker.

---

## Part 4 — Home's data (`feat/1384-web-home-data`)

Spec D9–D14, data contract. No UI. Every decoder is tolerant: unknown keys ignored, a malformed item dropped with `console.warn`, never thrown.

### Task 15: Types

**Files:** `apps/web/lib/types.ts`.

- [ ] Add, with doc comments pointing at the spec:

```ts
/** path_month_wrap's slice Home reads (#1384 D10). `cover`/`weeks` are absent below one pebble. */
export type MonthWrapPebble = { momentId: string; emotionId: string | null; intensity: Intensity; positiveness: Positiveness }
export type MonthWrapDay = { day: number; pebbles: MonthWrapPebble[] }          // day: 0-based index in the month
export type MonthWrapWeek = { weekStart: string; from: number; to: number; count: number; soulCount: number; days: [moments: number, topIntensity: number][] }
export type HomeMonth = { month: string; daysInMonth: number; firstWeekday: number; count: number; days: MonthWrapDay[]; weeks: MonthWrapWeek[] | null }
export type HomeWrap = { month: string; count: number; revealedAt: Date | null; days: number[] }   // days: indices with a pebble
export type Guide = { slug: string; illustration: GuideIllustration | null; title: string; summary: string; bodyMd: string }
export type GuideIllustration = "pebbles" | "stones" | "emotions" | "valence" | "domains" | "collections" | "souls" | "glyphs" | "karma"
```

### Task 16: Decoders against the captured payloads

**Files:** create `apps/web/lib/data/home-payloads.ts`, `home-payloads.test.ts`.

- [ ] `decodeHomeMonth(raw: unknown): HomeMonth | null`, `decodeHomeWraps(raw: unknown): HomeWrap[]` (reads `months`), `decodeGuides(rows: unknown, locale: "en" | "fr"): Guide[]` (FR falls back to EN per field; unknown illustration → null), `decodeClaim(rows: unknown): { slug: string; karmaGranted: number } | null` (`[]` → null).
- [ ] Tests import the payloads verbatim: `../../../../packages/supabase/scripts/oracles/path-wrap/payloads/month-{full,thin,quiet}.json` and `…/home/payloads/home-wraps.json` (resolve with `path.join(webRoot, "../../packages/supabase/scripts/oracles/…")` and `readFileSync`, so the files are never copied). Assert: month counts, `weeks` present in `month-full`; `home-wraps.json`'s explicit `revealed_at: null` → `null`; a hand-written variant with `"2026-10-30T09:12:44.123456+00:00"` and one with `"2026-10-30T09:12:44Z"` both decode to the same whole second; an unknown key is ignored; a payload missing `cover` and `weeks` (`{ "month": "2026-10-01", "days_in_month": 31, "first_weekday": 3, "count": 0 }`) decodes with `days: []`, `weeks: null`; positiveness/intensity outside range clamp to the Path's neutral medium (as `CrossingPebble`).

### Task 17: Provider methods

**Files:** `apps/web/lib/data/data-provider.ts` (interface), `supabase-provider.ts`, `lib/data/supabase-provider-home.test.ts`.

- [ ] Interface: `getHomeMonth(month: string, tz: string): Promise<HomeMonth | null>`, `getHomeWraps(tz: string): Promise<HomeWrap[]>`, `revealWrap(month: string, tz: string): Promise<void>`, `getGuides(locale: "en" | "fr"): Promise<Guide[]>`, `claimBluePebble(): Promise<{ slug: string; karmaGranted: number } | null>`.
- [ ] Implementation: `rpc("path_month_wrap", { p_month, p_tz })`, `rpc("home_wraps", { p_tz })`, `rpc("reveal_wrap", { p_month, p_tz })`, `from("guides").select("slug, illustration, sort_order, title_en, title_fr, summary_en, summary_fr, body_md_en, body_md_fr").eq("published", true).order("sort_order")`, `rpc("claim_blue_pebble")`; each in `withTimeout(…, 10000, "<name>")`, each error thrown as `new Error("<name> failed: …")` (the file's pattern).
- [ ] Test with a fake Supabase client (the `supabase-provider-achievements.test.ts` pattern): the RPC names and parameter names are exactly those above; a `claim_blue_pebble` `[]` returns `null`.

### Task 18: Greeting

**Files:** create `apps/web/lib/home/greeting.ts`, `greeting.test.ts`.

- [ ] `greetingKey(date: Date, timeZone: string): "morning" | "afternoon" | "evening" | "monday-morning" | "sunday-evening"` (hour and weekday read through `Intl.DateTimeFormat(…, { timeZone, hour: "numeric", hourCycle: "h23", weekday: "short" })` parts, not `getHours`); `dateline(date, locale, timeZone)` per spec §i18n. The title and line strings come from `home.greeting.<key>.title|line`; joining ", name" when the trimmed name is non-empty is `greetingTitle(greeting, name)`.
- [ ] Tests: spec §Testing (bands at 11:59 / 12:00 / 17:59 / 18:00, both overrides, blank name, the two datelines in Europe/Paris).

### Task 19: Month columns and cairn

**Files:** create `apps/web/lib/home/month-columns.ts`, `cairn.ts`, tests.

- [ ] `monthColumns(m: HomeMonth): WeekColumn[]` where `WeekColumn = { weekStart: string; from: number; to: number; count: number; soulCount: number; days: ColumnDay[]; cairn: CairnDay[] | null }` and `ColumnDay = { kind: "outside" } | { kind: "empty" } | { kind: "pebbles"; pebbles: MonthWrapPebble[] }` (at most 3, served order). From `m.weeks` when present (iOS `WeekColumn.columns`); otherwise weeks from `firstWeekday` and `daysInMonth` (Monday of the week holding day 0, then every 7 days while it starts on or before the last day), every day `empty`, `count: 0`, `cairn: null`. Dates are computed in UTC from the served `month` string, never from the local clock.
- [ ] `sealWidth(intensity, pip = 15)` → `pip × [0.85, 1, 1.15][intensity − 1]`.
- [ ] `cairn.ts`: `cairnWidths(days)` (`9 + 4.2·min(m, 3) + 2.4·(clamp(i, 1, 3) − 1)`, days with `m > 0`, widest first) and `cairnStones(widths)` returning `{ x, y, w, h, dotted }[]` in a 30 × 42 box (height `w / 1.19 × 0.62`, overlap 0.2, squeezed to 42), plus `EMPTY_CAIRN` (one dotted stone of width `9 + 4.2`).
- [ ] Tests: spec §Testing (captured payloads, the empty October 2026 = five columns with `firstWeekday` 3, widths, flattening, the dotted stone).

### Task 20: First five and the week parameter

**Files:** create `apps/web/lib/home/first-five.ts`, `apps/web/lib/utils/week-param.ts`, tests.

- [ ] `FIRST_FIVE_ORDER: FirstFiveTask[]` (moment, pebble, collection, soul, glyph — the Home order, `FirstFiveTask` already exists), `BLUE_PEBBLE = "blue-pebble"`, `firstFiveState(catalogSlugs: ReadonlySet<string>, unlockedSlugs: ReadonlySet<string>): { state: "unavailable" | "inProgress" | "claimable" | "done"; done: Record<FirstFiveTask, boolean>; count: number }` (iOS `HomeChecklist`).
- [ ] `parseWeekParam(value: string | null): Date | null`: `^\d{4}-\d{2}-\d{2}$` and a real calendar date (round-trips through `Date.UTC`), else null.
- [ ] Tests: spec §Testing.

### Task 21: Hooks, gate, PR

**Files:** create `apps/web/lib/data/useHomeMonth.ts`, `useHomeWraps.ts`, `useGuides.ts`, `useClaimBluePebble.ts`; `apps/web/lib/home/timezone.ts` (`readerTimeZone()`, `currentMonthStart(tz)`).

- [ ] Each read hook follows `useRipple` (provider from `useDataProvider`, the deferred `await Promise.resolve()`, cancellation) and keeps the last value per user in a module-level `Map` so a revisit shows it at once and refreshes (spec D23); each exposes `{ data, loading, error, retry }`. `useHomeWraps` also exposes `reveal(month)` (calls `revealWrap`, then refetches). Errors: `console.error("[home] <name> failed", err)`.
- [ ] `useClaimBluePebble()` returns `claim(): Promise<boolean>`; a returned row is handed to the same presenter `fireAchievementCheck` uses for granted rows (read `lib/activity/fire-achievement-check.ts` and reuse its exported path; if none is exported, export the presenter there in this task, without changing its behaviour).
- [ ] Part gate (no visual).

---

## Part 5 — Home (`feat/1384-web-home`)

Spec D1 (route only), D3 (island), D8–D14, D22, D23, §Accessibility, §i18n. Strings first, then the route, then sections top to bottom.

### Task 22: Strings

**Files:** `apps/web/lib/i18n/messages/en.json`, `fr.json`; create `apps/web/lib/i18n/home-you-keys.test.ts`.

- [ ] Insert a `"home"` block after the `"nav"` block **as text** (Edit at the anchor `"nav": {` … its closing `},`; never `JSON.parse`/`stringify` the file). Keys: `greeting.{morning,afternoon,evening,monday-morning,sunday-evening}.{title,line}`; `month.{label,weekAria,weekAriaEmpty}`; `actions.{camera,quick,pebble,cameraSaved,nameIt,cameraFailed}`; `quick.{title,name,when,save,saveAndComplete}`; `firstFive.{title,count,tasks.<task>.{title,line},claim,claimLine,done}`; `wraps.{title,folded,unfold,unfoldTitle,unfoldBody,empty,error}`; `about.{title,error}`; `guide.back`; `retry`. EN and FR from iOS `apps/ios/Pebbles/Resources/Localizable.xcstrings` (`home.*` keys) wherever iOS has them; the rest written in both languages.
- [ ] `home-you-keys.test.ts`: flatten `en.home`, `fr.home` (and from Part 6 `you`, from Part 7 the new `nav` keys) and assert equal key sets. Run: green.

### Task 23: The route and its layout

**Files:** create `apps/web/app/home/layout.tsx`, `app/home/page.tsx`, `apps/web/components/home/HomeScreen.tsx`; modify `components/auth/AuthGate.tsx`, `components/layout/MainContent.tsx`.

- [ ] `layout.tsx` (server): imports `@fontsource-variable/ysabeau/wght-italic.css`; renders `<M3Island className="pb-[calc(6rem+var(--safe-area-bottom))]">{children}</M3Island>` and `<StoneSprite />` once (the seals, as `app/achievements/layout.tsx`).
- [ ] `page.tsx`: a thin shell rendering `HomeScreen` (repo rule: route pages are thin).
- [ ] `AuthGate`: add `"/home"` and `"/guides"` to `PROTECTED_PREFIXES`.
- [ ] `MainContent`: `const isM3 = pathname === "/home" || pathname === "/profile" || pathname.startsWith("/guides/")`; a `useEffect` toggles `document.body.classList.toggle("m3", isM3)` beside `path-route`.
- [ ] `HomeScreen`: the column (`mx-auto max-w-md px-4 pt-[calc(1.375rem+var(--safe-area-top))] flex flex-col gap-11`), sections in spec order, each its own component (Tasks 24–29).

### Task 24: The month grid, and `?week=` on the Path

**Files:** create `apps/web/components/home/HomeMonth.tsx`, `HomeWeekColumn.tsx`, `CairnMark.tsx`; modify `app/path/page.tsx`, `components/path/PathScreen.tsx`.

- [ ] `HomeMonth`: `useHomeMonth()` → `monthColumns`; markup per spec §Accessibility (`<nav aria-labelledby>`, sr-only month label, legend column `aria-hidden` with `Intl` narrow weekday letters Monday first, `<ol>` of columns). Rows 28 px (`--cell: 1.75rem`), pip 15.
- [ ] `HomeWeekColumn`: a `<Link href={`/path?week=${weekStart}`}>` with `aria-label` from `home.month.weekAria` (range "5 – 11 October" from `from`/`to` and the served `month`, `count`, `soulCount`) or `weekAriaEmpty`; inside, `aria-hidden`: Monday's date (`pb-type-day`), the month in `pb-type-annotation`, seven 28 px rows (socket: a 6.8 px circle `stroke-m3-outline` at 0.7 opacity; seals: an `<svg>` per day with up to three `FlatSeal`s at `sealWidth`, palette from `useEmotionPalettes` via `achievementPalettes`), the cairn (`CairnMark`, `fill-m3-on-surface-variant`, dotted `stroke-m3-outline` for an empty week). Focus ring per spec.
- [ ] `PathScreen` gains `focusWeek?: Date | null`; `app/path/page.tsx` reads `week` with `parseWeekParam` and passes it; on first entries, `closestEntry(entries, focusWeek)` sets the focused week, then the parameter is dropped with `router.replace("/path")` exactly as `focus` is. `focus` wins when both are present.
- [ ] Render test (`lib/home/home-render.test.ts`, start the file): from `month-full.json`, one `<a>` per week whose `aria-label` reads the range and the count, the legend has `aria-hidden="true"`, no seal is outside an `aria-hidden` subtree. Visual: `/home` grid at 320 and 390 px; Tab moves column to column with a visible ring; Enter opens the Path on that week.

### Task 25: Date line and greeting

**Files:** create `apps/web/components/home/HomeGreeting.tsx`.

- [ ] Reads `useAuth().profile.display_name`, `useLocale()`, `readerTimeZone()`; a clock state refreshed on `visibilitychange` (the band can change while the tab sleeps). `<p className="pb-type-date text-m3-primary">`, `<h1 className="pb-type-greeting mt-2">`, `<p className="pb-type-lead text-m3-on-surface-variant mt-2">`. Rendered on the client only after mount for the time-dependent parts (the server does not know the reader's zone): before mount, render the three lines with the date and band of `Date.now()` in UTC and `suppressHydrationWarning` on the three text nodes, or render an empty placeholder of the same height. Pick the placeholder (no wrong greeting flashes).
- [ ] Render test: an `<h1>` holds "Good evening, Alexis" for a fixed date passed through a prop (`now`) used by tests.

### Task 26: Quick actions

**Files:** create `apps/web/components/home/QuickActions.tsx`, `CameraAction.tsx`, `QuickMomentDialog.tsx`.

- [ ] `QuickActions`: `SplitStone n={3} height={100} fill={primary-container}` with three cells (spec D11): `CameraAction`, a button opening `QuickMomentDialog`, a `Link` to `/record`. Each cell `flex flex-col justify-between pt-[15px] pr-3 pb-[14px] pl-4 text-m3-on-primary-container pb-type-label` (later cells `pl-5`), icon 28 px (`size-7`) `aria-hidden`, label text visible, focus ring `focus-visible:outline-2 outline-offset-[-4px] outline-m3-on-primary-container rounded-[20px]`.
- [ ] `CameraAction`: `<label>` wrapping a `sr-only` `<input type="file" accept="image/*" capture="environment">`; on change, `useCapturePhoto` (pass `uploadSnap`, `removeSnapFiles` from `useMoments`, `onCaptureDate` storing the EXIF date) then `saveMoment(captureSavePayload({ name: "", happenedAt: exif ?? nowIso(), snap }))`, `photo.commit()`, `toast(t("cameraSaved"), { action: { label: t("nameIt"), onClick: () => router.push(`/pebble/${id}`) } })`; failures toast `cameraFailed` and leave cleanup to the hook. Read `CaptureFlow.tsx` first for the exact hook wiring; reuse, don't copy logic into the component (the hook is the logic).
- [ ] `QuickMomentDialog`: shadcn `Dialog` (`npx shadcn@latest add dialog` is already present: reuse `components/ui/dialog.tsx`), bottom-aligned under 640 px via the content's className; name `Input` (autofocus), `InlineDatePicker` + `TimeStepPicker` prefilled to now; "Save" → `saveMoment(captureSavePayload({ name, happenedAt, snap: undefined }))`, close, `toast`; "Save and complete" → `router.push(`/record/complete/${id}?from=capture`)`. Errors through `momentErrorKey` into the dialog, `console.error("[home] quick moment save failed", err)`. Save disabled while the name is blank (`canSave`'s rule without a photo).
- [ ] Render test: three controls with visible names "Camera", "Quick moment", "New pebble", the stone `aria-hidden`. Visual: the stone, the focus ring on each piece, the dialog in Paper and Night, keyboard-only save.

### Task 27: Your first five

**Files:** create `apps/web/components/home/FirstFive.tsx`, `FirstFiveStepper.tsx`, `TaskMark.tsx`.

- [ ] Data: `useAchievements()` → catalog slugs and unlocked slugs → `firstFiveState`. Hidden for `unavailable` and `done`.
- [ ] Box: `section` `bg-m3-surface-container rounded-[26px] pt-6 pr-4 pb-2.5 pl-[18px]`, head `<h2 className="pb-type-section">` + figure `<span className="pb-type-figure text-m3-primary">{count}<small className="pb-type-figure-small text-m3-on-surface-variant mx-1">{t("of")}</small>5</span>` (one ICU string `home.firstFive.count` with rich text for the "of"); `FirstFiveStepper` (`SplitStone n={5} height={6} seed="first-five"` with the D7 options, pieces `< count` `fill-m3-primary`, others `fill-m3-surface-container-highest`, `aria-hidden`); `<ol>` of rows (no separators): `TaskMark` (the blob path from the mock, 26 px), title `pb-type-row-title`, line `pb-type-row-line`; done rows `text-m3-on-surface-variant font-medium`, no link, no chevron, an sr-only "done"; open rows a `Link` (or the Quick moment trigger for `first-moment`; for `first-pebble`, `/record/complete/<id>` of the latest bare moment from `useMoments()`, else `/record`) with a lucide `ChevronRight` `size-[18px] text-m3-outline`.
- [ ] Claim row when `claimable`: a filled `Button` "Claim the blue pebble" + its line; on success the section unmounts (state becomes `done` on the next read; refetch `useAchievements` after the claim).
- [ ] Render tests: `inProgress` with two done → "2 of 5", two rows without `<a>` and without a chevron, three with; `claimable` → the claim button; `done` → nothing rendered.

### Task 28: Monthly wraps

**Files:** create `apps/web/components/home/MonthlyWraps.tsx`, `WrapCard.tsx`, `WrapRing.tsx`, `UnfoldDialog.tsx`.

- [ ] Head: `pb-type-section` "Monthly wraps" + `pb-type-figure` count. A `<ul>` scrolling horizontally (`-mx-4 px-4 flex gap-3 overflow-x-auto snap-x pb-[22px] -mb-[22px]` so the shadow is not clipped), each `<li>` a `WrapCard`.
- [ ] `WrapCard` (`Card` + `pb-card w-[152px] p-[18px_16px_18px_18px]`): revealed → `Link` to `/path?week=<Monday of day 0>`, `WrapRing` (116 px, one dot per day, `r = 1.9` `stroke-m3-outline-variant`, filled `r = 3.6` `fill-m3-primary` where `days` includes it, `aria-hidden`), the count centred (`pb-type-counter text-[2.125rem]`), month `pb-type-card-month`, year `pb-type-kicker`; accessible name "October 2026, 23 pebbles". Folded → a `button` opening `UnfoldDialog` (title, count, one filled "Unfold" → `reveal(month)`), showing the month name, `home.wraps.folded` in `pb-type-annotation`, and a turned corner (a 14 px triangle in `bg-m3-surface-container-high` at the top right).
- [ ] No wraps: one dashed card (`border border-dashed border-m3-outline-variant`, `bg-transparent`, no shadow) with an empty ring and `home.wraps.empty` (month name and last day of the current month).
- [ ] Error: the D23 italic line + Retry.
- [ ] Render tests: from `home-wraps.json` (folded) a `<button>`; a revealed variant a link with the accessible name; the empty card text.

### Task 29: About Pebbles and the guide page

**Files:** create `apps/web/lib/config/guides.ts` (`GUIDE_ICONS: Record<GuideIllustration, PbblsIconComponent>` per spec D14), `apps/web/components/home/AboutPebbles.tsx`, `apps/web/components/home/IconChip.tsx`, `apps/web/app/guides/[slug]/page.tsx`, `apps/web/components/home/GuidePage.tsx`.

- [ ] `IconChip({ seed, icon })`: `Stone` 48 × 46, `fill-m3-surface-container`, corner 16, icon 26 px `text-m3-primary`, `aria-hidden`. (Part 6 reuses it.)
- [ ] `AboutPebbles`: `useGuides(locale)`; head (`mb-2`); `<ul>` of `Link` rows to `/guides/<slug>`: chip (`seed="chip-<i>"`), title, summary, chevron; nothing under the head on error or empty.
- [ ] `/guides/[slug]`: a thin page in an `M3Island` reading the same hook (the list is small and cached), `PageHeader` with `backHref="/home"`, title `pb-type-section`, summary `pb-type-lead`, body through the `AnnouncementDetail` processor (copy its three-line `unified()` setup into `lib/home/guide-markdown.ts` as a pure `renderGuideMarkdown(md)` with a test that `<script>` and raw HTML are dropped). Unknown slug → `NotFoundCard`.
- [ ] Visual: `/home` About rows; `/guides/stones` in both languages.

### Task 30: Part gate and PR

- [ ] Part gate. Visual pass of all of `/home` against the mock in Paper and Night, EN and FR, 320/390/1280, font size 20 px; a brand-new account (sockets only, dotted cairns, first five at 0 of 5, the dashed wrap card); keyboard only from the top of the page to About; VoiceOver or the DevTools accessibility tree on one week column, one done task, one wrap card.
- [ ] Arkaik (hosted, `arkaik-mcp`): create the Home view node for web and the guide page view, edges to the RPCs; status `development`. If the tools are unavailable, say so in the PR and stop that step (never edit `docs/arkaik/bundle.json`).
- [ ] Commit and push the part.

---

## Part 6 — You (`feat/1384-web-you`)

Spec D15–D21.

**Depends on #1386** (back-end, shared part B: `get_profile_engagement` gains `glyphs_carved` and `glyphs_owned`, owned = `glyph_entitlements`). Before starting, check that #1386 is merged and applied to the linked project, that `packages/supabase/types/database.ts` on `main` shows the two fields in the function's return, and where its harness wrote the payload dump (read the #1386 PR; the oracles live under `packages/supabase/scripts/oracles/`). If #1386 is not merged, build Tasks 31–37 anyway (the decoder is null-tolerant) but do not open Part 6 for merge until it is. The `whatsapp` icon is #1387 (Task 36).

### Task 31: Strings

**Files:** `en.json`, `fr.json` (a `"you"` block inserted as text after `"home"`), extend `home-you-keys.test.ts`.

- [ ] Keys: `title` ("You" / "Toi"), `settings`, `memberSince` (`{date}`), `ripples` (`Ripples Level {level}`), `counters.{days,pebbles,karma,label}`, `glyphs.{title,mine,mineLine,owned,ownedLine,community,communityLine}` (ICU plurals for the two counts), `achievements.{title,earned}` (`{count, plural, …}` with the figure and the word as rich text), `lab.{title,kicker,allUpdates,communityTitle,communityLine,newTab,error}`. The two glyph lines have a numberless variant for a null count (`mineLineNoCount`, `ownedLineNoCount`). Under the existing `settings` namespace, add `signOut.title` (inserted as text inside `"settings"`); the button keeps `profile.logout` / `profile.loggingOut`.

### Task 32: `RippleBadge` one line high

**Files:** `apps/web/components/profile/ripples/RippleBadge.tsx`, `lib/home/you-render.test.ts` (start the file).

- [ ] Add `className?: string` (merged with `cn`, default `size-11`) and `showLevel?: boolean` (default `true`, the digit `<span>` only when true). Nothing else changes.
- [ ] Test: default render contains the digit and `size-11`; `showLevel={false}` contains no digit; `PublicProfileView` still compiles unchanged.

### Task 33: The header

**Files:** create `apps/web/components/you/YouHeader.tsx`, `SettingsStoneButton.tsx`, `RipplesLine.tsx`.

- [ ] `SettingsStoneButton`: a `Link` to `/settings`, `aria-label={t("settings")}`, `Stone` 46 × 44 `fill-m3-surface-container-high` corner 16, `Slider` icon `size-[22px] text-m3-on-surface`.
- [ ] `YouHeader`: top row `flex justify-end -mb-[18px]`; `Pentagon` holding `GlyphPreview` (`size-[58px]`, `strokeClassName="text-m3-on-primary-container"`) or `PebbleDottedGlyph` `size-[58px] text-m3-on-primary-container`; `<h1 className="pb-type-hand mt-3.5 text-center">`; since line; `RipplesLine` (`RippleBadge className="size-6" showLevel={false}` + `pb-type-label` text from `useRipple().ripple.level`).
- [ ] Render test: `<h1>` holds the name; the settings link is named; no digit next to the ripples text.

### Task 34: Counters and Glyphs

**Files:** create `apps/web/components/you/YouCounters.tsx`, `YouGlyphs.tsx`, `apps/web/components/home/LinkRow.tsx` (the chip + title + line + chevron row shared with About; move About onto it in this task, a three-line change).

- [ ] `YouCounters`: `<dl>` inside `SplitStone n={3} height={88} seed="days|pebbles|karma" fill={surface-container}`; each cell `flex flex-col justify-center py-3 pr-2.5 pl-4` (later `pl-5`), `<dd className="pb-type-counter text-m3-on-surface">` visually first via `order-first`, `<dt>` icon + label (`pb-type-row-line text-m3-on-surface-variant`, icon `size-[18px] text-m3-primary`). Values `Intl.NumberFormat(locale)`, "—" while loading.
- [ ] Engagement decoder (spec D17): in `lib/types.ts`, `ProfileEngagement` gains `glyphsCarved: number | null` and `glyphsOwned: number | null`. Extract the row mapping of `SupabaseProvider.getProfileEngagement` into a pure `decodeProfileEngagement(raw: unknown): ProfileEngagement` in `lib/data/profile-engagement.ts` (the provider calls it; `days_practiced`/`assiduity` behaviour unchanged), reading `glyphs_carved` / `glyphs_owned` as non-negative integers, anything else (absent, null, wrong type) → `null`. `useProfileEngagement`'s empty value gains the two nulls.
- [ ] `lib/data/profile-engagement.test.ts`: replay #1386's real payload dump verbatim (read with `readFileSync` from its path under `packages/supabase/scripts/oracles/`, never copied); assert the two counts and that `daysPracticed`/`assiduity` decode as before. Then the same object with both keys deleted and with both set to `null`: counts `null`, no throw. If #1386 committed no dump, capture one with its harness's anon user against the linked project and commit it beside its other payloads, in this task, naming the command in the PR.
- [ ] `YouGlyphs`: head; three `LinkRow`s (D17), counts from `useProfileEngagement().engagement.glyphsCarved` and `.glyphsOwned`; a `null` count uses the numberless line. Never count `store.marks` or `store.entitledMarks`.
- [ ] Render test: `<dt>` precedes `<dd>` in the DOM for each counter; "1,240" in EN and "1 240" (narrow no-break space) in FR; `YouGlyphs` with `glyphsCarved: 12` reads "The 12 marks you carved." and with `null` "The marks you carved.".

### Task 35: Achievements

**Files:** create `apps/web/components/you/YouAchievements.tsx`.

- [ ] `useAchievementsShelf()` + the catalog (`useAchievementCatalog`) → `shelfDisplays(shelf, catalog)`; head is a `Link` to `/achievements` with the figure; `<ul className="grid grid-cols-4 sm:grid-cols-6 gap-2.5">`, each `<li>` a `Card pb-card` `Link` (`flex flex-col items-center gap-2 pt-4 px-1 pb-3`), icon `size-[30px] text-m3-primary` from the badge family map, label `pb-type-caption text-m3-on-surface-variant text-center` from `useAchievementCopy`. Shelf trimmed to 4 under `sm` with `max-sm:[&>li:nth-child(n+5)]:hidden`.
- [ ] Render test: "12 earned" from a fixture shelf count of 12; four visible items.

### Task 36: Lab

**Files:** `apps/web/lib/data/useLab.ts` (add `useLabPreview`), create `apps/web/components/you/YouLab.tsx`, `LabAnnouncementCard.tsx`, `LabTimeline.tsx`, `CommunityStone.tsx`.

- [ ] `useLabPreview()`: `Promise.allSettled([fetchAnnouncements(sb, { limit: 1 }), fetchChangelog(sb, { limit: 3 })])` through `settledOr`, the hook's lazy singleton, `{ announcement, changelog, loading, error, retry }`.
- [ ] `LabAnnouncementCard`, `LabTimeline` (`<ol>`, `<time dateTime={released_at}>` formatted `{ day: "numeric", month: "short" }`, the rule line `bg-m3-rule`, newest dot `bg-m3-primary`, others `bg-background border-[1.5px] border-m3-outline`; the platform line from the log's platform label; the "All updates" outline pill `Button` rendered as `Link` via `render`), `CommunityStone` per spec D19.
- [ ] Icon: the `whatsapp` icon is #1387 (`chore/1387-whatsapp-icon`). If it has merged, import `Whatsapp` from `@pbbls/icons`; otherwise use `People` with `// TODO(#1387): whatsapp icon` and say so in the PR body (#1387 then swaps it).
- [ ] Render test: the external link has `target="_blank"`, `rel` with `noopener`, and the sr-only new-tab text; three `<li>` with `<time>`.

### Task 37: The page, gate, PR

**Files:** `apps/web/app/profile/page.tsx` (rewrite), create `apps/web/app/profile/layout.tsx`.

- [ ] `layout.tsx`: the italic import and `M3Island` (as Home's).
- [ ] `page.tsx`: the loading and signed-out states keep their copy, now inside the column; the signed-in page is `YouHeader`, `YouCounters`, `YouGlyphs`, `YouAchievements`, `YouLab` in the D22 column with `gap-11`. No `PageHeader`, no log out (spec D20). `CollectionsCard`, `ShortcutsRow` and `LogoutButton` are no longer imported here (they stay in the codebase, D20).
- [ ] Log out in Settings (spec D20): create `apps/web/components/settings/SignOutSection.tsx` (`{ onSignOut: () => Promise<void> }`; the section title as the other settings sections set theirs, read `AppearanceSection.tsx` first; then `components/profile/LogoutButton`, imported where it is). In `app/settings/page.tsx`, render it between `<AppearanceSection />` and `<DeleteAccountSection …/>`, with `onSignOut={async () => { await logout(); router.push("/") }}` (`logout` and `router` are already in scope there). Render test `lib/home/settings-render.test.ts`: the section renders the log-out button with the `profile.logout` label.
- [ ] Render test in `you-render.test.ts`: no element with the `profile.logout` label on You.
- [ ] Part gate; visual pass as Part 5 on `/profile`; `/settings`: log out sits above Delete account, signs out and lands on `/`; `/u/<handle>` (public profile) unchanged; `/sandbox/design` profile view unchanged.
- [ ] Arkaik: update the You (profile) view node's description and status `development`.

---

## Part 7 — landing and the hub bar (`feat/1384-web-hub-nav`)

Spec D1, D2 (the hub bar and `/home` as the signed-in landing: decided by the maintainer on 2026-10-31).

### Task 38: Home is the landing

**Files:** `components/landing/LandingPage.tsx`, `app/login/page.tsx`, `app/auth/callback/route.ts`, `lib/hooks/useOnboarding.ts`, `app/offline/page.tsx`, `components/pebble/PebbleNotFound.tsx` (only if its "back" means "the app", read it first), any test pinning `/path` there (`lib/auth/consent-gate.test.ts`, `lib/config/store-routes.test.ts`: read, update only what pins the landing).

- [ ] Replace the landing `/path` with `/home` at each site; an explicit `next` still wins. `grep -rn "\"/path\"" apps/web/app apps/web/components apps/web/lib` afterwards lists only genuine Path links.
- [ ] `store-routes.ts`: add `/home` and `/guides` where the protected/store route lists need them (read the file and its test first).

### Task 39: The hub bar

**Files:** `lib/config/navigation.ts`, `lib/hooks/useNavItems.ts`, `components/layout/BottomNav.tsx`, `components/layout/MainContent.tsx`, `en.json`/`fr.json` (`nav.home`, `nav.people`, `nav.you`, inserted as text in `"nav"`).

- [ ] `NAV_ITEMS`: `home` `/home` `Home`, `path` `/path` `Path`, `people` `/souls` `People`, `collections` `/collections` `Collection`, `you` `/profile` `Person` (Pebbles icons; `icon` typed `ComponentType<{ className?: string }>`). `NavItemKey` follows. Check `Sidebar` (dead) still compiles; do not revive it.
- [ ] `BottomNav`: a `<nav aria-label={t("common.aria.mainNavigation")}>` floating pill (`fixed inset-x-0 bottom-0 flex justify-center pb-[calc(12px+var(--safe-area-bottom))]`; the pill `rounded-full bg-[color-mix(in_srgb,var(--m3-stock)_88%,transparent)] backdrop-blur-[8px] shadow-…`), five `Link`s with icon `size-5` and a 12 px label, `aria-current="page"` on the active one (prefix match), active colour `text-m3-primary`, others `text-m3-on-surface-variant`, each ≥ 44 × 44. Inside an M3 island on Home and You; on `/souls` and `/collections` it uses the shadcn tokens (`bg-card`, `text-primary`) so it wears the colour world there.
- [ ] `MainContent`: render `<BottomNav />` when the path is exactly `/home`, `/souls`, `/collections` or `/profile`; those pages get bottom padding for it.
- [ ] Render test (`lib/home/nav-render.test.ts`): five links in order, `aria-current` on `/home` when active.

### Task 40: The Path's Home link and People's Connections row

**Files:** `components/path/PathBottomBar.tsx`, `app/souls/page.tsx` (or the souls list component it renders; read it first).

- [ ] Path pill: a `Link` to `/home`, `aria-label={t("bottomBar.homeAria")}` (new key in `path.bottomBar`), `Home` icon `size-6 text-(--spine-ink)`, before the avatar.
- [ ] Souls page: one row linking to `/connections` ("Connections" + a line), placed where iOS `SoulsListView` places it (read it).

### Task 41: Decisions log

**Files:** `docs/decisions/log.md` (append).

- [ ] One entry: `## 2026-10-31 — Web Home and You wear the brand M3 roles in an island; Inclusive Sans is bundled; Home is the web landing with a hub bar (#1384)`, with Context, Decision (D1, D2, D3–D5 in one line each; D20, log out lives in Settings on every surface; D17, glyph counts come from `get_profile_engagement` per #1386), Why, Consequences (two themes until the default flips; the island selectors; `preload: false`; the dead `Sidebar` stays dead), Supersedes (extends 2026-09-28 "The web follows Android's M3-evo theme": `.m3` is no longer sandbox-only), Refs (spec, plan, files).

### Task 42: Gate, Lab Note, PR

- [ ] Part gate. Visual: sign out, sign in (email and a provider): land on `/home`; onboarding a new account lands on `/home`; the bar on the four hub roots, not on `/path`, `/record`, `/carve`, a pebble, settings; the Path pill's Home link; `/souls` → Connections.
- [ ] Lab Note via the repo `lab-note` skill (or `no-lab-note` per the maintainer's Arkaik practice), body `Resolves #1384`.
- [ ] Arkaik: edges for the hub bar's navigation; acceptances to `development` (the App moves them on).
- [ ] Fill "Lessons learned" below. Drop the build-journal pebble (root CLAUDE.md) once the stack is merged.

---

## Self-review against the spec

- **D1** route and landing: Tasks 23, 38. **D2** hub bar, Path link, Connections: Tasks 39, 40. **D3** island: Tasks 4, 6, 8, 23, 37. **D4** blends: Task 5. **D5** fonts: Tasks 1–3, italic imports in 23, 37. **D6** type roles: Task 7. **D7** stones: Tasks 10–14. **D8** surfaces: Tasks 7, 14, 27, 28, 35. **D9** week links and `?week=`: Tasks 20, 24. **D10** month data: Tasks 16, 19, 24. **D11** quick actions: Task 26. **D12** first five and claim: Tasks 20, 21, 27. **D13** wraps: Tasks 16, 28. **D14** About and guides: Tasks 16, 29. **D15** header and ripples: Tasks 32, 33. **D16** counters: Task 34. **D17** glyphs, served counts (#1386): Tasks 31, 34. **D18** achievements: Task 35. **D19** Lab: Task 36. **D20** log out in Settings, components kept: Tasks 31, 37. **D21** icons: every UI task; whatsapp fallback Task 36. **D22** layout: Tasks 23, 37. **D23** loading and errors: Tasks 21, 28, 29, 36. **Accessibility:** Tasks 8 (contrast), 24, 26, 27, 28, 34, 36, 39 and each part's visual pass. **i18n:** Tasks 22, 31, 39.
- **Names used throughout:** `M3Island`, `pb-type-*`, `pb-card`, `stoneRandom`, `stoneOutline`, `splitStone`, `toPathData`, `PENTAGON_PATH`, `Stone`, `SplitStone`, `Pentagon`, `IconChip`, `LinkRow`, `useElementSize`, `HomeMonth`, `monthColumns`, `cairnWidths`, `firstFiveState`, `parseWeekParam`, `useHomeMonth`, `useHomeWraps`, `useGuides`, `useClaimBluePebble`, `useLabPreview`, `decodeProfileEngagement`, `SignOutSection`.

## Deviations from the spec

(Parts 1–5 recorded theirs in their PR bodies; #1411 lists Part 5's.)

- **Part 6, You's settings control is a standard icon button, not a stone** (D15, Task 33). The maintainer rejected custom-drawn navigation chrome on every surface after the plan was written: `SettingsButton` is shadcn `Button` (ghost, icon, 44 × 44) rendered as a `Link` to `/settings`, drawing `Slider` at `PBBLS_ICON_SIZE.toolbar`. Logged with the 2026-11-01 decision.
- **Part 6, the counters are one `<dl>` per piece**, not one `<dl>` around the split stone: `SplitStone` wraps each cell in a `div`, and a `div` holding a `div` is not a valid `<dl>` group. Each piece reads "Days, 19" in order.
- **Part 6, the counter figure fits its piece.** At 36 px "1 240" ran past the third piece below ~360 px and at a 20 px root font; the figure is `min(2.25rem, 100cqi / 2.4)` on a container-query piece, so it keeps its size on a 390 px phone and shrinks below that. The label drops under its icon when it has to.
- **Part 6, the Lab timeline's second line is the log's summary** (one line), as iOS ships it, not a platform label: on the web every log is already `webapp` or `all`.
- **Part 6, dates on You put the day first** ("Lab notes · 31 October", "29 Oct"), as Home's dateline does, through `lib/utils/day-month.ts`; plain `Intl` "en" would say "October 31".
- **Part 6, the glyph lines have a zero form** from iOS ("Carve your first mark.", "Glyphs you swap for karma land here.") beside the count and count-free forms.
- **Part 6, `FAMILY_ICONS` is exported from `AchievementBadge`** (one keyword) so You's cards draw the same family icons as the page, rather than a third copy of the map.
- **Part 7, the offline page's button says "Go to Home"** now that it links there ("Go to timeline" would be wrong). `/home` joins the store-backed routes; `/guides` does not read the store.
- **Part 7, one navigation and one ground instead of the hub bar** (D2, Tasks 39–40). The plan's floating pill on four hub roots, styled per route, beside the Path's own pill, was built and rejected by the maintainer on 2026-11-01. In its place: `AppNavigation`, Android's navigation suite on the web (a bar under 600 px, a rail from 600 px, CSS only), rendered once by the shell on the five tabs, the Path included; a route table (`lib/config/app-routes.ts`) the shell, the navigation and the sandbox read; `app-nav-room` as the one place the page leaves it room; and the Path, People, Collections and Settings on the M3 ground with Home and You, the Spine's paper pointed at `surface` by the #1118 rule set.
- **Part 7, the Path's pill keeps bounce and karma** (a link to the wallet) and loses its Profile link. Android's Path shows neither bounce nor karma; keeping them is the conservative call, since the pill is the web's only way to the wallet besides the karma toast.
- **Part 7, the navigation's current tab draws the plain icon**, not Android's lit `active` twin: web icon migration D11 (#1313) marks the current entry by colour and `aria-current`, and that logged decision stands until it is revisited.
- **Part 7, People's Connections row is a settings-style row** at the top of `/souls` (iOS has a toolbar button there), and on a tab root `PageHeader` takes `backHref={null}`: the navigation stands in for back.
- **Part 7, the sandbox draws the navigation** through `ShellFrame` (the shell's arrangement, `mainClass`) and an `at` prop on `AppNavigation`, and gains People and Collections views.
## Lessons learned

- **Chrome is not content.** The plan drew the settings control as a stone and the hub bar as a brand pill styled per page; both were rejected. Stones, cards and the brand faces carry what a page says; buttons, bars, rails and toolbars stay the platform's, and look the same on every page.
- **Mirror the surface that already solved it.** Android had answered "one ground or three" (#1118: the Spine on `surface`) and "bar or rail" (`NavigationSuiteScaffold`, 600 dp) before this plan; the plan re-decided both per page and got them wrong. Read the sibling surface's shell before designing a web one.
- **One table for route behaviour.** `MainContent` had grown a flag per route, and the first hub bar duplicated its route list; `routeShell` is the single answer the shell, the navigation and the sandbox share, and a test pins it.
- **A breakpoint, not a width read.** Bar ↔ rail by CSS needs no hydration-safe placeholder and cannot flash.
- **Wrap a split stone's cells, don't wrap the stone, in list semantics.** `SplitStone` owns its grid and a `div` per piece, so `<dl>`, `<ul>` or `<ol>` belongs inside each cell. The same applies to `<p>`: `RippleBadge` is a `div`, so a line holding it is a `div` too (React warns on `<p><div>` in dev, not in a server render test).
- **Measure figures against the piece, not the viewport.** A display figure inside a measured stone needs a container-query size (`100cqi` is the content box, padding already out); viewport breakpoints miss both the 20 px root font and the sandbox's frame.
- **`Intl` "en" is month-first.** Every day-and-month string on Home and You goes through a parts-based formatter to read "31 October" in both languages.
- **The sandbox frame does not move breakpoints.** A `max-sm:` or `rail:` rule follows the window's width, so check a phone layout at a phone-sized window and a rail at a wide one ("Fill"), and pass route-dependent chrome its route explicitly.
- **Next 16's `next dev` writes `apps/web/AGENTS.md`** in a fresh worktree; delete it before committing.
- **Render tests stand in for `next/link`, lucide and the shadcn button** (two React copies resolve); the button stand-in must honour `render` (clone the element) or every Base UI link-button test sees a `<button>`.

### Parts 8–14, the two-area layout (plan `2026-11-01-web-two-area-layout.md`, docs PR #1479)

- **A `default.tsx` does not close a parallel slot on a soft navigation.** Next keeps the slot's previous page; a null root page and a null catch-all are what close it. Next refuses an optional catch-all (`[[...rest]]`) beside the root page, so it is `page.tsx` plus `[...rest]/page.tsx`.
- **Put a slot's frame in a route group's layout inside the slot** (`@detail/(pane)/layout.tsx`): the null pages sit outside it, so a closed slot draws no frame, and the frame stays mounted from one detail to the next.
- **An intercepting page and a plain page for the same path coexist in one slot** (Next 16.4): soft navigations take the interception (the left area stays), hard loads the plain page. Prove it with a throwaway pair before building on it.
- **A route group's layout draws under every route below it.** Immersive flows (record, carve, the pebble edit flows) stay outside the groups, and `TabArea` steps aside on a group route that is not the tab or one of its details.
- **`useSelectedLayoutSegment("detail")` in a client component under the root layout tells the shell whether a detail is open**, which is simpler and more honest than CSS `:has()` for `inert`, the scroll lock and the landmark's name.
- **A shared-element morph needs its source to leave.** The Spine stays mounted under the pebble's pane, so a `<ViewTransition name>` pair would share a name at once and be skipped; the pane's own motion stands in.
- **Check the shell's route table in the same part as a moved page.** Part 12 shipped Settings in the slot without its `DETAILS` row (a command chain stopped before the edit); a part-level test that every slot page has a table row (`detail-slot-source.test.ts`) catches it, and the fix belongs in that part, not on top.
- **`shadcn add` can write a wrong import alias** (`import { cn } from "cn"`) and install that npm package; read its diff before keeping it. Here the M3 switch is built on Base UI directly.
