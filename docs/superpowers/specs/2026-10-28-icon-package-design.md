# The Pebbles icon package — design

- **Issues:** #1311 (umbrella: the package and its generator, milestone M66 · Brand icons), with one follow-up issue per surface for the migration (#1314 iOS, #1315 Android, #1313 web) and one for the migration lint (#1312).
- **Date:** 2026-10-28
- **Surface:** `packages/icons` (new, `@pbbls/icons`), with generated outputs committed into `apps/web` / `apps/admin` (through the workspace), `apps/ios` and `apps/android`; one new workflow under `.github/workflows/`.
- **Size:** Large (cross-app, a new workspace). Five parts, one stack (plan `docs/superpowers/plans/2026-10-28-icon-package.md`).
- **PR kind:** this spec is docs, **no Lab Note**. No part of the stack changes what a user sees (nothing consumes the icons yet), so every part is `chore` with `no-lab-note`.
- **Arkaik:** no change. The package adds no view, route, data model or endpoint.
- **Exploration:** wave `2026-10-foundation`, three rounds of hands, a blind pick-three game and a round-3 harmonisation pass (hands h21–h23 in the Riverbed line). Its working folder moves into the repo in Part 1.

## Summary

The hand-drawn Pebbles icon set gets one home. `packages/icons` holds the SVG sources, a manifest, the style rules and the exploration waves that produced them. A generator lints the sources and emits three platform outputs: React components for web and admin, an asset catalog and a Swift enum for iOS, VectorDrawables and a Kotlin enum for Android. The outputs are committed into each app, the way `packages/supabase/types/database.ts` is, and CI fails when a committed output differs from what the generator emits.

## Goal

- One drawing per icon, the same geometry on every surface, edited in one place.
- Adding or redrawing an icon is one SVG file, one manifest row and one command.
- No surface needs node at build time: Xcode Cloud and Gradle read committed files.
- The set's rules are written down and machine-checked, so a new hand cannot drift from the line by accident.

## Non-goals

- **Migrating call sites.** The SF Symbols on iOS (the exploration counted 35; `systemName:` and `systemImage:` literals name 61 distinct symbols today), the hand-traced `ic_*` drawables on Android and the lucide imports on web stay as they are. Each surface gets its own follow-up issue; this spec only gives them the `replaces` column and the shape of the lint that will police them.
- **shadcn's own icons.** `components.json` keeps `iconLibrary: lucide` on web and admin; the chevrons and checks inside `components/ui/` primitives are not ours to replace.
- **The widget.** `PebblesWidget` has its own catalog; it takes no icon from this set until a widget needs one.
- **A runtime icon font, a sprite or a CDN.** Each surface gets native assets.

## Decisions

**D1. `packages/icons` is an npm workspace and the single source of truth.** `@pbbls/icons`, private, picked up by the root `packages/*` glob. Its `main`/`types`/`exports["."]` point at the generated `react/index.ts`, the `@pbbls/supabase` precedent of shipping TypeScript source; Turbopack transpiles workspace packages by itself (`node_modules/next/dist/docs/01-app/03-api-reference/05-config/01-next-config-js/transpilePackages.md`), so neither `next.config.ts` changes. `"sideEffects": false` keeps the barrel tree-shakeable; with 29 modules `optimizePackageImports` buys nothing.

**D2. Generated outputs are committed, never built on the fly.** The `database.ts` precedent, for the same reason: every consumer reads a file, and the native builds (Xcode Cloud, Gradle in `android.yml`) never run node. The generator owns its output paths outright and clears them before writing, so a removed icon leaves nothing behind (the `generate-ios-colors.ts` precedent).

**D3. The generator is TypeScript, run with tsx; the exploration tools stay Python.** `scripts/build.ts` is the contract every surface depends on, so it is typed, tested with Vitest and small: `svgo` is its one runtime dependency. The hub builder and the blind pick-three game are about 740 lines of stdlib-only Python that produce throwaway HTML; porting them buys nothing and risks the game's reproducibility. They move as they are into `scripts/explore/`, run with `python3 -I`, and stop hard-coding the ids (they read `icons.json`).

**D4. Status gates emission.** `exploring` rows are linted but not emitted, so a wave in progress never churns the apps. `candidate` rows are emitted to every surface (available, not yet adopted). `shipped` means every surface has migrated and its `replaces` names are gone, which the migration lint (D10) enforces.

**D5. Names.** Manifest names are kebab-case (`check-circle`); a twin is `<name>.<variant>`, where the variant is `active` (the lit seal) or `lit-1`, `lit-2`, `lit-3` (the lit series: one, two, three stones lit, for the families that count). A name's prefix before the first `-` is its family (`pebble-round`, `pebble-flat`, `path-river`, …), which is how the hub and the library group them; the generator does not read it. Each surface derives its own spelling, and only these:

| | base `check-circle` | twin `home.active` |
|---|---|---|
| source | `src/check-circle.svg` | `src/home.active.svg` |
| React | `CheckCircle` in `react/CheckCircle.tsx` | `HomeActive` |
| iOS asset / case | `pbbls-check-circle` / `.checkCircle` | `pbbls-home-active` / `.homeActive` |
| Android drawable / entry | `ic_pbbls_check_circle` / `CHECK_CIRCLE` | `ic_pbbls_home_active` / `HOME_ACTIVE` |

A lit-series twin follows the same rule: `path.lit-2` → `src/path.lit-2.svg`, `PathLit2`, `pbbls-path-lit-2` / `.pathLit2`, `ic_pbbls_path_lit_2` / `PATH_LIT_2`.

React names are bare, as designed. Of the lucide names web imports today only `Settings` collides, and only in a file that imports both, which is the moment that file migrates. The `pbbls-` and `ic_pbbls_` prefixes keep the native names out of the existing `Assets.xcassets` and `res/drawable` namespaces.

**D6. No dashes.** `stroke-dasharray` is forbidden in `src/`: VectorDrawable has no dash support and CoreSVG's is partial, and a geometry splitter in the generator is more code than the one icon deserves. The glyphs ring (the carve placeholder) is drawn as short separate strokes by the hand in the final pass, so all three surfaces get the same geometry with no arithmetic. The lint rejects the attribute.

**D7. Path data passes through verbatim.** The hands' coordinates are the design. `svgo` is a formatter for the sources (strip editor cruft, canonical root attributes, two decimals), never a path optimiser: `convertPathData` and every geometry plugin stay off. The lint requires each source to already be in `svgo`'s canonical form, so emitters read a known grammar with a tiny parser.

**D8. Freshness is checked in memory, not with `git diff`.** `npm run check --workspace=@pbbls/icons` runs the emitters into memory and compares the result with the disk over the owned paths, failing on any missing, stale or orphaned file. This replaces the designed `git diff --exit-code`, which does not see an untracked new output and would need the owned paths listed a second time in YAML.

**D9. iOS and Android expose a mirrored enum and one view.** iOS `enum PbblsIcon` with `PbblsIconImage`, Android `enum class PbblsIcon` with `PbblsIconImage`; same cases, same `activeTwin` and `lit(n)` (the `lit-n` twin, or nil), same default 24 size. iOS ties the size to a text style through `@ScaledMetric`; Android keeps dp, the Material convention (icons do not follow font scale). Both are hidden from accessibility unless a label is passed.

**D10. The migration lint is specified now, built with the first migration.** `scripts/lint-replaces.ts` reads every `shipped` row and fails when a `replaces` name still appears: iOS `systemName: "<name>"` or `systemImage: "<name>"` under `apps/ios/Pebbles/`, Android `R.drawable.<name>` under `apps/android/app/src/main/`, web `<Name>` imported from `lucide-react` outside `components/ui/`. With no `shipped` row it is vacuous, so it lands with the first surface issue, not in this stack.

**D11. A lit stone may carve its mark.** The lit twin of a stone that carries a mark (a glyph, a shine) is one compound path, `fill="currentColor" fill-rule="evenodd"`: the stone outline plus the mark's stroke outlined at the 1.75 width with round caps, so the mark shows through in the surface colour. No second colour, no mask, no clip: SVG, CoreSVG and VectorDrawable (`fillType="evenOdd"`) all carry an even-odd fill. The carve is made once by the wave's `carve.py` (shapely buffer of the sampled curve); the generator passes the path through like any other. People stays a plain silhouette.

## 1. The package

```
packages/icons/
  package.json  tsconfig.json  vitest.config.ts  CLAUDE.md  .gitignore
  DNA.md                      the style paragraph and the canvas rules
  icons.json                  the manifest
  src/<name>.svg              sources (and <name>.<variant>.svg: active, lit-1, lit-2, lit-3)
  react/                      GENERATED: <Name>.tsx, types.ts, index.ts
  scripts/build.ts            CLI: --lint | --check | (default) generate
  scripts/lib/                manifest, parse, format, lint, dash, names, emit-react, emit-ios, emit-android, write (+ *.test.ts)
  scripts/explore/            build.py (hub), game-build.py (pick-three), tally.py, extract.py
  waves/<yyyy-mm-slug>/       BRIEF*.md, SEED.md, notes.json, results-picks.json, specials*.json, verdict.md, hands/hNN/{sprite.svg,NOTES.md}
```

Scripts: `generate` (`tsx scripts/build.ts`), `lint` (`--lint`), `check` (`--check`), `format` (rewrites `src/` to canonical form), `build` (`tsc --noEmit`, so turbo's `^build` typechecks the package before web and admin), `test` (`vitest run`, node environment, `pool: "threads"` for the web config's reason). `tsx`, `vitest`, `typescript`, `@types/node`, `@types/react` are devDependencies (they are nested under `apps/web`, not hoisted), `react` is a peer, `svgo` is pinned to an exact version. Generated `gallery/` and `sheets/` under a wave are gitignored; a hub is published as a private artifact when shared.

A `packages/icons/turbo.json` narrows the `build` inputs to exclude `waves/**` and `scripts/explore/**`, so a wave commit does not count as a change to the package web and admin depend on. Whether `turbo-ignore` honours task inputs is verified in Part 2 (risk R2).

## 2. Sources, DNA and lint

`DNA.md` carries the round-3 DNA paragraph verbatim ("Pebbles icons are small ink drawings, not geometry, drawn with a pen of constant width on a 24-unit square by a hand that knows the shape but never measures it…"), the Riverbed line description, and the canvas rules the lint enforces:

| Rule | Error when |
|---|---|
| Root | `<svg>` lacks exactly `xmlns`, `viewBox="0 0 24 24"`, `fill="none"`, `stroke="currentColor"`, `stroke-width="1.75"`, `stroke-linecap="round"`, `stroke-linejoin="round"`, or carries anything else |
| Elements | any child other than `<path d>`; any `transform`, `style`, `class`, `id`, `opacity`, text, mask, filter |
| Commands | a command other than absolute `M L C Q A Z` (relative commands would hide coordinates from the range check) |
| Range | any coordinate (end and control points; an arc's end point) outside 1.5..22.5 |
| Fill | `fill="currentColor"` on a path in a base file; any other fill value; more than three filled paths in a twin (a filled area above 64 square units warns, it does not fail); `fill-rule` on an unfilled path, or any value but `evenodd` (a carved stone: the stone outline and the knocked-out mark in one compound path, D11) |
| Dash | any `stroke-dasharray` (D6) |
| Canonical | `format` would change the file |
| Manifest | a row without `src/<name>.svg`; an `.svg` without a row; a listed variant without `src/<name>.<variant>.svg` or a twin file not listed in `variants`; a twin without its base; a variant outside `active`, `lit-1`, `lit-2`, `lit-3`; a duplicate or non-kebab name; a `wave` with no `waves/<wave>/` folder; an unknown `status` |

Every error names the file and, for geometry, the offending number, so a fix is a nudge to the drawing, never a relaxed rule.

## 3. The manifest

```json
{
  "icons": [
    {
      "name": "trash",
      "meaning": "Remove: take a thing out of your record for good.",
      "variants": [],
      "wave": "2026-10-foundation",
      "status": "candidate",
      "replaces": { "ios": ["trash"], "android": ["ic_trash"], "web": ["Trash2"] }
    }
  ]
}
```

- **Order is authored**: outputs follow the manifest's order, so a diff reads like the set.
- **`meaning`** is one sentence; it becomes the doc comment on the React component, the Swift case and the Kotlin entry, and the caption in the hub. It is not an accessibility label (labels are per use and localised).
- **`replaces`** is a list per surface (one drawing may replace two SF Symbols). **Android lists drawable resources, not Material icons**: the app ships no icon library and draws its icons as hand-traced `ic_*` VectorDrawables (`core/designsystem/PebblesIcon.kt`), so `ic_trash` is what `trash` replaces. Web lists lucide export names.
- **`variants`** lists the twins that exist: `[]`, `["active"]`, or `["active", "lit-1", "lit-2", "lit-3"]` (a lit series always comes whole, from the wave's `lit-families.json`).
- The first wave enters as `candidate` with the whole saved library, not only the 23 winners: 121 drawings under the maintainer's official names (`library.json` in the wave: the 23 chosen, the specials, the runners-up and 2 derived outlined glyphs), 34 `active` twins and the lit series (1–3 stones) for the path, pouches, stones, stacks, pebbles, calendar, plants, knots and users families. `library.svg` holds every symbol under its final name; `verdict.md` records how each got there.

## 4. Waves

A wave folder is the record of how a set of drawings was chosen: the briefs, the seed, each hand's sprite and notes, the game's raw picks and the verdict. `verdict.md` names the hand whose drawing moves into `src/` for each id (written 2026-10-29 from the round-3 picker: h23 ×10, h22 ×8, h21 ×5) and any per-id swap, and lists every hand edit made later to pass the lint. Two Python helpers join the moved tools: `tally.py` prints a Borda count (3/2/1) of `results-picks.json` per id and context, an input to the verdict and never a substitute for it; `extract.py <wave> --hand hNN [--take id=hMM …]` writes `src/<name>.svg` from the hand's `<symbol>`s with the canonical root. The generator never reads `waves/`.

## 5. Emitters

**React** (`packages/icons/react/`): one file per emitted icon plus `types.ts` and `index.ts`, each headed `// Generated by packages/icons — do not edit. Run npm run generate --workspace=@pbbls/icons.` A component is a plain function (no hooks, no `"use client"`, so it renders in a Server Component):

```tsx
export function Home({ size = 24, ...props }: PbblsIconProps) {
  const labelled = props["aria-label"] !== undefined || props["aria-labelledby"] !== undefined
  return (
    <svg xmlns="http://www.w3.org/2000/svg" width={size} height={size} viewBox="0 0 24 24"
      fill="none" stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round"
      aria-hidden={labelled ? undefined : true} role={labelled ? "img" : undefined} focusable="false" {...props}>
      <path d="M3.4 11.2 C…" />
    </svg>
  )
}
```

`PbblsIconProps = Omit<SVGProps<SVGSVGElement>, "children"> & { size?: number | string }`. A lit shape is `<path fill="currentColor" d="…" />`, with `fillRule="evenodd"` when the source carries it. Web and admin declare `"@pbbls/icons": "*"`.

**iOS**: `apps/ios/Pebbles/Resources/Icons.xcassets/` (wholly owned) with one `pbbls-<name>.imageset` per emitted icon, holding the SVG (with `width="24" height="24"` and `currentColor` replaced by `#000000`, since a template image keeps only alpha and CoreSVG does not resolve `currentColor`) and a `Contents.json` with `preserves-vector-representation: true` and `template-rendering-intent: "template"`, written in Xcode's own JSON style (`"key" : value`, two-space indent) so opening the catalog in Xcode does not rewrite it. `project.yml` needs no edit: `Pebbles/` is a source folder, and `xcodegen generate` picks up the new catalog. The Swift file goes to **`apps/ios/Pebbles/Theme/PbblsIcon.swift`**, not `DesignSystem/`: that folder does not exist, and `Theme/` already holds `Icon+Pebbles.swift` and the other tokens. It is `enum PbblsIcon: String, CaseIterable, Sendable` (raw value = manifest name), `assetName`, `activeTwin`, and

```swift
struct PbblsIconImage: View {
    init(_ icon: PbblsIcon, size: CGFloat = 24, relativeTo textStyle: Font.TextStyle = .body,
         style: AnyShapeStyle? = nil, label: LocalizedStringResource? = nil)
    // Image(icon.assetName).renderingMode(.template).resizable() at a @ScaledMetric side;
    // .foregroundStyle(style) when given, else the ambient foreground; accessibilityHidden when label is nil.
}
```

`PbblsIcon` sits next to the existing `PebblesIcon` (SF Symbol size tokens). The near-homonym is accepted for parity with the package name; the surface migration retires `PebblesIcon` as call sites move.

**Android**: `apps/android/app/src/main/res/drawable/ic_pbbls_<snake>.xml` (the generator owns `ic_pbbls_*.xml` only; the hand-traced drawables around them are untouched), 24dp on a 24 viewport, one `<path>` per source path with `strokeColor="#FFFFFFFF"`, `strokeWidth="1.75"`, round cap and join, `fillColor="#00000000"` (or `#FFFFFFFF` on a lit shape, plus `fillType="evenOdd"` when the source has `fill-rule="evenodd"`), tinted at the call site by `Icon`'s `LocalContentColor`. The Kotlin file is `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsIcons.kt`: `core/designsystem` because the icons would look at home in any app and import only androidx and `R`, which `ArchitectureBoundaryTest` allows. It holds `enum class PbblsIcon(val manifestName: String, @DrawableRes val drawableRes: Int)` with `activeTwin`, and `@Composable fun PbblsIconImage(icon, contentDescription: String?, modifier, size: Dp = 24.dp, tint: Color = LocalContentColor.current)` over `Icon(painterResource(…))`. Generated Kotlin passes stock ktlint.

## 6. CI

`.github/workflows/icons.yml`, on every pull request and on pushes to `main`, **no `paths:` filter** for the `web.yml` reason (a filtered workflow never reports, so it cannot be a required check, and the job is an `npm ci` and a few seconds of node). One job, four steps, each after the first `if: '!cancelled()'` so a red run names every problem: `lint` (sources), `build` (tsc), `test` (Vitest), `check` (freshness, D8). A red `check` is fixed by `npm run generate --workspace=@pbbls/icons` and a commit, never by an edit to the workflow.

## 7. Testing

| Where | What |
|---|---|
| `packages/icons` Vitest | lint (one passing fixture, one failing fixture per rule), names, each emitter's exact output for a two-icon fixture, `renderToStaticMarkup` of a component (aria-hidden by default, `role="img"` when labelled), write clears orphans and spares `ic_trash.xml`, `--check` fails on a missing, stale and orphaned file, emission is deterministic |
| `apps/web` Vitest | `lib/icons/pbbls-icons.test.ts` imports from `@pbbls/icons` (proves the workspace resolves from the app) |
| iOS Swift Testing | `PbblsIconTests`: every case loads a template image from the main bundle; the emitted manifest rows (read through `#filePath`, the `HomePayloads` precedent) equal `allCases`; twins match `variants`. A reference sheet of the set, recorded on CI only |
| Android JUnit | `PbblsIconsTest` (Robolectric): every entry inflates as a `VectorDrawable` at 24dp; manifest parity read from `packages/icons/icons.json`. A screenshot preview of the set, baselined by the CI bot only |

The parity tests are the cross-surface proof the standing rules ask for: each native suite reads the real manifest, not a copy.

## Risks

- **R1. The verdict is the maintainer's.** Part 1 cannot fill `src/` until `verdict.md` names a hand. If it is not written when Part 1 starts, the rows enter as `exploring` (linted, not emitted) and Parts 2–4 emit from a two-icon fixture until it is.
- **R2. Vercel deploys on wave commits.** web and admin depend on `@pbbls/icons`, and `turbo-ignore` may count any file under the package as a change. If the `turbo.json` inputs do not stop it, wave work moves to a branch that is merged once per wave (one deploy each), and a follow-up weighs moving `waves/` out of the package.
- **R3. CoreSVG.** Xcode's SVG support is a subset; the D7 grammar (paths only, round caps, no dashes, D6, no `currentColor` in the iOS copy) stays inside it, and the iOS reference sheet is the check.
- **R4. Thin strokes at small sizes.** A 24-unit drawing at 15pt draws a 1.1pt line. Sizing per call site is the migration issues' concern, not the package's.
