# Web: a Material 3 theme and a design page to tune it — design

**Date:** 2026-09-28 · **Surface:** web · **Status:** approved in brainstorming, awaiting spec review

## Goal

Android adopted the M3-evo Material 3 Expressive theme (seed `#CE7E8A`, #853,
decision log 2026-09-24). The web should follow. Before any default changes, we
need a place to **see** the M3 theme on every web component, flip it against the
current theme, and tune components one at a time. This spec covers:

1. an M3 theme layer for the web, generated from Android's scheme file;
2. `/sandbox/design`, a page that renders the foundations, the UI primitives, a
   curated set of feature components and a few full views, with a theme switcher.

Real users see no change. `.m3` is only ever applied by the design page.

## Decisions (maintainer, 2026-09-28)

| # | Decision |
|---|---|
| D1 | **M3 tokens + bridge.** The web gets the full M3 role set *and* a bridge that maps the existing shadcn tokens onto M3 roles, so every component renders M3 at once. Tuning a component means moving it from the bridged tokens to explicit M3 roles. |
| D2 | **Scope:** foundations + every `components/ui` primitive + the most-used feature components + 4 views (Path, Pebble detail, Profile, Settings). The other feature components are added when they are tuned. |
| D3 | **Reach:** the design page only. No Settings toggle, no color world, no query flag in the real app. |
| D4 | **One canvas, global switcher.** Classes go on `<html>` so portaled overlays are themed too. Switcher state lives in the URL, so a two-iframe compare view can be added later without rework. |
| D5 | **Android is the source of truth for colour.** Web tokens are generated from `ColorSchemes.kt`, never hand-copied, and a test catches drift. |

## Part 1: the theme layer

### 1.1 Generation

- `apps/web/scripts/generate-m3-theme.ts` (run with `tsx`, exposed as
  `npm run generate:m3 --workspace=apps/web`) reads
  `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/ColorSchemes.kt`,
  parses the six `internal val <Name>Scheme: ColorScheme = light|darkColorScheme(...)`
  blocks (`LightScheme`, `LightMediumContrastScheme`, `LightHighContrastScheme`,
  and their `Dark*` counterparts), and converts each `role = Color(0xAARRGGBB)` to
  `--m3-<kebab-role>: #RRGGBB`.
- Output: `apps/web/app/m3-theme.css`, starting with a "GENERATED from
  ColorSchemes.kt, do not edit, run `npm run generate:m3`" header. It is
  imported from `app/globals.css`.
- The parser fails loudly (non-zero exit, message naming the scheme) if a scheme
  is missing, a role is missing from one scheme but present in another, or an
  alpha channel is not `FF`.
- Non-scheme literals in the Kotlin file (e.g. `GoogleCapsuleInk`) are ignored.

### 1.2 The `.m3` scope

The generated file contains the colour roles. A hand-written section in
`globals.css` (below the import) holds type, shape and the bridge, because those
don't come from the Kotlin file.

**Scheme selectors** (generated):

| Selector | Scheme |
|---|---|
| `.m3` | `LightScheme` |
| `.m3.m3-contrast-medium` | `LightMediumContrastScheme` |
| `.m3.m3-contrast-high` | `LightHighContrastScheme` |
| `.m3.dark` | `DarkScheme` |
| `.m3.dark.m3-contrast-medium` | `DarkMediumContrastScheme` |
| `.m3.dark.m3-contrast-high` | `DarkHighContrastScheme` |

Specificity rises with each class, so the order in the file doesn't matter.

**Roles.** All roles in the Kotlin schemes become `--m3-*` properties
(`--m3-primary`, `--m3-on-primary-container`, `--m3-surface-container-lowest`,
`--m3-outline-variant`, `--m3-inverse-primary`, `--m3-error` = amber, …). A
generated `@theme inline` block exposes them as Tailwind colours
(`bg-m3-surface-container`, `text-m3-on-surface-variant`, `border-m3-outline`).
Outside `.m3` these properties are undefined. A tuned component that renders
under Current keeps its old classes and adds the M3 ones with an `m3:` custom
variant (`@custom-variant m3 (&:is(.m3 *))`), for example
`bg-card m3:bg-m3-surface-container-low`, so Current is unaffected.

**Type.** Add `@fontsource-variable/inclusive-sans` (5.3.0 on npm). Under `.m3`:
`--font-sans` becomes `"Inclusive Sans Variable", system-ui, …`, and
`--font-heading` stays Ysabeau (matching Android). The M3 default type scale
(display / headline / title / body / label × large / medium / small, sizes and
line heights from the M3 spec, the same numbers `Typography.kt` uses) becomes
`--m3-type-<style>-<size>-{size,line,weight,tracking}` plus `text-m3-*`
utilities via `@utility`. The font import is loaded globally but only
referenced under `.m3`, so browsers don't download it for real users (fontsource
declares `@font-face` lazily per use).

**Shapes.** The M3 Expressive shape scale becomes `--m3-shape-*`: none 0,
extra-small 4px, small 8px, medium 12px, large 16px, large-increased 20px,
extra-large 28px, extra-large-increased 32px, extra-extra-large 48px, full
9999px. Values are checked against `Shapes.kt` during implementation. Under
`.m3`, `--radius` is set to the medium value (12px) so shadcn's derived
`rounded-sm…4xl` scale follows.

### 1.3 The bridge

Under `.m3`, every shadcn token points at a role. The table is repeated as a
comment in `globals.css` so each choice is on record:

| shadcn token | M3 role |
|---|---|
| `background` / `foreground` | `surface` / `on-surface` |
| `card` / `card-foreground` | `surface-container-low` / `on-surface` |
| `popover` / `popover-foreground` | `surface-container` / `on-surface` |
| `primary` / `primary-foreground` | `primary` / `on-primary` |
| `secondary` / `secondary-foreground` | `secondary-container` / `on-secondary-container` |
| `muted` / `muted-foreground` | `surface-container-high` / `on-surface-variant` |
| `accent` / `accent-foreground` | `surface-container-highest` / `on-surface` |
| `destructive` / `destructive-foreground` | `error` / `on-error` |
| `border` / `input` / `ring` | `outline-variant` / `outline` / `primary` |
| `surface` / `surface-alt` | `surface-container` / `surface-container-high` |
| `chart-1…5` | `primary`, `tertiary`, `secondary`, `error`, `outline` |
| `sidebar` / `-foreground` | `surface-container` / `on-surface` |
| `sidebar-primary` / `-foreground` | `primary` / `on-primary` |
| `sidebar-accent` / `-foreground` | `secondary-container` / `on-secondary-container` |
| `sidebar-border` / `sidebar-ring` | `outline-variant` / `primary` |

The page-specific classes in `globals.css` that hard-code colours
(`.pbbls-visual`, `.path-row`, `.path-stone-fill`, `.prose`) are left alone. The
design page will show whether they need a role.

`.m3` does not stack with a color world: the page removes the color-world class
when M3 is on.

## Part 2: the design page

### 2.1 Route and files

- `app/sandbox/design/page.tsx`: a thin shell. It inherits the sandbox layout's
  `noindex`, has no auth gate, and makes no network calls.
- `components/sandbox/design/`:
  - `DesignScreen.tsx`: the page, which renders the toolbar, the section nav and
    the sections.
  - `DesignToolbar.tsx`: the sticky switcher.
  - `useDesignTheme.ts`: reads and writes the URL state and applies classes to
    `<html>`.
  - `Specimen.tsx`: one frame per component, with its name, source path, an M3
    status chip (`bridged` / `tuned`) and the rendered states.
  - `sections/FoundationsSection.tsx`, `PrimitivesSection.tsx`,
    `ComponentsSection.tsx`, `ViewsSection.tsx`.
  - `FixtureDataProvider.tsx`: a `DataContext.Provider` fed a seeded `Store`
    (`provider: null`, no-op `setStore`/`refreshStore`) so data hooks resolve from
    fixtures.
  - `specimens.ts`: the registry of specimens and their M3 status. It's the one
    place to flip a component from `bridged` to `tuned`, and it gives a count
    ("12 / 58 tuned") in the toolbar.

### 2.2 Switcher

URL search params, updated with `router.replace` (no history spam):

| Param | Values | Default |
|---|---|---|
| `theme` | `current` · `m3` | `m3` |
| `world` | the five color worlds (only when `theme=current`) | `blush-quartz` |
| `mode` | `light` · `dark` | `light` |
| `contrast` | `standard` · `medium` · `high` (only when `theme=m3`) | `standard` |

- Keyboard: `T` flips Current ↔ M3, `D` flips light ↔ dark. Both are ignored
  while focus is in a text field.
- `useDesignTheme` snapshots `<html>`'s class list on mount, applies the page's
  classes, and restores the snapshot on unmount. It never writes to
  `localStorage`, `next-themes` or `ColorWorldProvider`, so the user's saved
  appearance is untouched (the `/sandbox/path` precedent).
- **Known race:** `ColorWorldProvider` and `next-themes` also write `<html>`
  classes on mount, and parent effects run after child effects. The hook must
  re-apply after them (for example, by also re-applying when a
  `MutationObserver` sees the class attribute change away from the target). This
  is verified in the browser, not assumed.
- `useSearchParams` in the App Router needs a `Suspense` boundary. Check the
  Next 16 docs in `node_modules/next/dist/docs/` before wiring it.

### 2.3 Sections

**Foundations**
- Colour: a swatch grid of every M3 role, grouped (primary, secondary, tertiary,
  error, surface tiers, outline, inverse, fixed). Under Current it shows the
  shadcn tokens instead. Each swatch reads its value live with
  `getComputedStyle`, so what's shown is what's applied, and pairs show the
  on-colour contrast ratio (flagged under 4.5:1).
- Bridge: the shadcn → M3 table rendered live, with both swatches side by side.
- Type: every M3 style with a sample line, plus the app's `font-heading`,
  `font-script` and `font-hand` faces.
- Shape: one tile per shape step.

**Primitives** (every file in `components/ui`, each in its meaningful states):
`button` (every variant × size, disabled, with icon), `badge` (variants),
`card`, `checkbox` (off/on/disabled), `input` (empty, filled, invalid,
disabled), `calendar`, `dialog`, `alert-dialog`, `sheet`, `popover`,
`dropdown-menu`, `sonner` (a trigger per toast type), `ConfirmDialog`,
`EmotionBadge`, `PickerSheet`, `SearchableList`, `SectionLabel`,
`SelectableItem`, `TagList`. Overlays render behind an "Open" trigger.

**Components.** Curated feature components that render from props or fixtures.
Candidates, confirmed during planning by reading each one's inputs: path
(`PebbleCard`, `PathPebbleRow`, `PathEmptyState`, `GamificationBlock`), pebble
(`PebbleVisual`, `PebbleIndicators`), souls (`SoulCard`, `SoulsEmptyState`),
collections (`CollectionCard`, `ModeBadge`), profile (`SectionCard`,
`StatsCard`, `DataTile`, `ShortcutTile`), settings (`SettingsGroup`,
`SettingsRow`), layout (`PageHeader`, `EmptyState`). **Rule:** a component that
reaches Supabase or Storage directly (not through a data hook) is left out and
listed in the plan, not faked.

**Views.** Each renders in a device frame (390px wide, with a toggle for
desktop width) inside `FixtureDataProvider`:
- **Path:** `PathScreen` with the `/sandbox/path` fixtures.
- **Pebble detail:** `PebbleDetail` with one seeded pebble.
- **Profile:** composed from `ProfileBanner`, `ShortcutsRow`, `StatsCard`,
  `CollectionsCard`, `AchievementsShelf` and `LabCard` with fixture props, as
  `app/profile/page.tsx` composes them. The page file itself calls network hooks.
- **Settings:** composed from the settings sections with fixture props, the same
  way.

A view whose components can't be fed without the network is swapped for another
view in the plan, not faked.

Fixtures reuse `lib/seed/sandbox-pebbles.ts` and `sandbox-palettes.ts`, extended
in a new `lib/seed/design-fixtures.ts` for anything they don't cover (a profile,
collections, a settings user).

## Out of scope

- Moving any component onto `m3-*` roles. That's the follow-up work, one small
  PR per component group, each flipping entries in `specimens.ts` to `tuned`.
- The two-iframe compare view (D4 leaves room for it).
- Any toggle in the real app, a new color world, or changing the default theme.
- Wallpaper / dynamic colour (Android-only).
- M3 motion (`MotionScheme.expressive()`) — revisit when components are tuned.
- iOS (#921 decides whether iOS follows).

## Testing

- **Vitest, `m3-theme.test.ts`:** parses `ColorSchemes.kt` with the generator's
  parser and asserts that (a) all six schemes are found with the same role set,
  and (b) the committed `m3-theme.css` equals what the generator would write now.
  This is the drift gate, and it runs in `web.yml` on every PR.
- **Vitest, `useDesignTheme`:** the pure URL-state → class-list mapping (every
  valid combination, plus fallbacks for invalid params).
- **Manual, in the browser:** every switcher combination (Current × 5 worlds ×
  2 modes, M3 × 3 contrasts × 2 modes). Check that overlays pick up the theme,
  leaving the page restores the user's classes, and a hard reload with params
  lands on the right theme. The screenshots go in the PR.
- `npm run lint --workspace=apps/web`, `npm run test --workspace=apps/web`,
  `npm run build --workspace=apps/web`.

## Delivery

A two-part `gh stack`, web only:

1. **`feat(web): generate the material 3 theme from the android schemes`:**
   generator, `m3-theme.css`, the type/shape/bridge section, Inclusive Sans,
   drift test. Nothing applies `.m3` yet, so it's zero visual change and
   reviewable on its own.
2. **`feat(web): add a design page to tune the material 3 theme`:** the
   `/sandbox/design` page, fixtures, specimens, views.

Labels `feat` + `web` + `ui`; no Lab Note (developer-only surface; label
`no-lab-note`). Arkaik: check during planning whether `/sandbox/path` is on the
hosted map; add `/sandbox/design` beside it only if it is.

A decision-log entry ("the web follows Android's M3-evo theme; colour is
generated from `ColorSchemes.kt`") is appended with Part 1.
