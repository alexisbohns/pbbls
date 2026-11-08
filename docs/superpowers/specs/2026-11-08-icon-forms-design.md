# Icon forms: roles, derived forms and state tokens — design

- **Supersedes:** in `2026-10-28-icon-package-design.md`, D5 (twin names `<name>.active`, `<name>.lit-N`), D9 (`activeTwin`, `lit(n)`), the asset-catalog and VectorDrawable outputs of §5, and the twin rows of the lint (§2). D1–D4, D6–D8 and D11's carving idea stand.
- **Date:** 2026-11-08
- **Surface:** `packages/icons`, `apps/web` and `apps/admin` (through the workspace), `apps/ios`, `apps/android`. Breaking on purpose: every call site of the set moves.
- **Size:** Large (cross-app). Seven parts, one stack (plan to follow).
- **Starts after:** PR #1606 (the 2026-11-everyday wave, 828 glyphs in the old twin model) merges. Its twins are the migration's input.
- **PR kind:** this spec is docs, no Lab Note. The parts change no screen's behaviour; the tab bars' selected look may change shape (solid instead of today's active drawing), which the surface parts call out.

## Summary

A glyph is one drawing, named for what it shows. Its paths carry roles (body, numbered marks, focus), and the generator works out what the drawing can show from those roles: regular, half, solid, and progress k of n. State leaves the library: a shared table of state tokens says which form a state shows, and components read it. The apps draw glyphs from generated data instead of asset images, so a form change can animate.

## Why

The twin model names states, not drawings. `.active` covers anything from one lit dot to a solid body; `.lit-1` does not say out of how many; `pouch.active` is nearly `pouch`; and the only generic consumer of `active` is the tab bar's `activeTwin`. Meanwhile `pebble-round.lit-1` is used as "on" in five places, which is a solid, not one stone of a series.

## Goals

- A name says what is drawn; a form says how much of it is filled; a state says why.
- Each drawing is made once; its forms stay consistent because they are computed.
- A form change animates: marks fill one by one, a solid reveals with its details carved.
- iOS, Android and web agree by construction on which form a state shows.
- Finding a glyph is a search over tags, not a memory of names.

## Non-goals

- Morphing one glyph into another (hand drawings have no matching points).
- Colour beyond the single tint, and multi-colour glyphs.
- Carving at runtime: carved solids are computed at build time.
- Complete tags: tags help search, they never identify a glyph.

## Decisions

**D1. A glyph is one drawing; its name is free kebab words for what it shows.** `path-river`, `pouch`, `users-circle`, `knot-sm`, `pebble-glyph-dotted`. Size and line style are part of the name because they are different drawings. The last segment of a name is never a form word (`regular`, `half`, `solid`, `of`), so a name can't be mistaken for a form. No variants, no twins.

**D2. Roles.** A path in a source may carry `data-role`:

| Role | Count | Meaning |
|---|---|---|
| `body` | 0 or 1 role (one or more paths, unioned) | the main shape, filled the way SVG fills a path: an open path closes with a straight segment (hand-drawn bodies rarely close) |
| `mark` with `data-mark="1"…"n"` | 0…n (a number may span several paths) | the countable parts, in fill order |
| `focus` | 0 or 1 role (one or more paths) | the part that carries the meaning |

An untagged path is a plain line. A glyph with no roles has only the regular form.

**D3. Forms are worked out from roles.**

| Form | Exists when | Drawn as |
|---|---|---|
| `regular` | always | every path stroked, nothing filled |
| `half` | the glyph has a focus | the focus filled (fill + its stroke) |
| `solid` | the glyph has a body | the body's fill (SVG-style) plus its stroke band; every other path inside the fill outlined at the 1.75 stroke with round caps and subtracted even-odd, so it shows through in the surface colour; paths outside it stay stroked |
| `progress(k)`, 0 ≤ k ≤ n | the glyph has n ≥ 1 marks | marks 1…k filled; `progress(0)` is `regular` |

The old `dot` is `progress(1)`. A dotted line is a separate glyph (`pebble-glyph-dotted`), never a form.

**D4. Overrides.** `src/<name>.<form>.svg` (`half`, `solid` or `progress-<k>`) replaces a worked-out form whose result looks wrong. It must keep the base's viewBox and the DNA rules, and may exist only for a form the glyph has. The design page flags every override.

**D5. Manifest.** `icons.json` rows: `name`, `meaning`, `family` (optional: groups versions of one idea, e.g. `knot` for `knot-sm` and `knot-lg`), `tags`, `wave`, `status`, `migrated`, `replaces`. `variants` is removed. `tags` is an object of optional arrays: `subject`, `wrapper`, `number`, `side`, `direction`, `size`, `keywords`. Code never reads `family` or `tags`; the design page searches and filters by them.

**D6. State tokens.** `packages/icons/states.json` maps interaction states to an ordered list of forms; a surface takes the first form the glyph has:

| State | Forms, first available wins |
|---|---|
| `rest` | `regular` |
| `hover` | `half`, `regular` |
| `pressed` | `half`, `regular` |
| `selected` | `solid`, `progress(n)`, `half`, `regular` |
| `disabled` | `regular` (the component dims it) |
| `progress` | `progress(k)` (k from the caller), `regular` |
| `done` | `progress(n)`, `solid`, `regular` |

The library holds no state. A component may pick a form directly, but a shared state goes through its token so the three surfaces agree.

**D7. The apps draw glyphs from data, not images.** The iOS asset catalog and the `ic_pbbls_*` VectorDrawables are removed. Each native surface gets one generated data resource (every glyph's paths: path data, role, mark number; plus its carved solid and its overrides) and a generated enum of names. The enum stays small (names only), so the 64 KB method limit that broke the Android previews in #1606 cannot recur. Path data is normalised to absolute `M L C Q Z` (arcs become cubics at build time), so each surface parses it with one small shared parser.

**D8. Carving runs in the generator.** The TypeScript generator outlines strokes and subtracts them with a polygon-offset library (a Clipper2 port, chosen in the plan), flattening curves at a tolerance fine enough for the hero size (48 px at 3×). The result is stored as path data like any other. `carve.py` stays as the exploration tool it was.

**D9. Animation.** A form change animates when the caller asks for it (SwiftUI `withAnimation`, a Compose `animate*AsState` inside the view, a CSS transition on web). Marks fade and scale in one after another; a solid crossfades in with a short reveal; nothing animates under reduce motion. Durations come from each surface's motion tokens.

## 1. Sources and lint

`src/<name>.svg` keeps the DNA: the 24 square, paths only, absolute commands, every point inside 1.5–22.5, 1.75 stroke, round caps and joins, no dashes or transforms. The `fill` rules for twins go away: a source is never filled; fills only exist in overrides and generated forms. The formatter (svgo) keeps `data-role` and `data-mark`.

The lint adds:

- `data-role` outside `body`, `mark`, `focus`; `data-mark` without `data-role="mark"`, or a mark without a number;
- mark numbers that do not run 1…n without gaps;
- a body that encloses no area (a straight line), or a carve that leaves less than 4 square units of filled area;
- an override for a form the glyph does not have, or with a different viewBox;
- a name whose last segment is a form word;
- tag keys outside D5.

## 2. Outputs and API

| Surface | Files (generated, committed) | API |
|---|---|---|
| Web and admin | `packages/icons/react/`: one component per glyph, its paths and forms inlined (tree-shaken), `states.ts` | `<PathRiver form={{ progress: 2 }} className={…} />`, `form` is `"regular" \| "half" \| "solid" \| { progress: k }`; `glyphForm(glyph, state, k?)` |
| iOS | `Pebbles/Theme/PbblsGlyph.swift` (enum of names), `Pebbles/Resources/pbbls-glyphs.json` (data), `PbblsGlyphState.swift` | `PbblsGlyphView(.pathRiver, form: .progress(2), size: .row)`, `GlyphForm` enum, `glyph.form(for: .selected)` |
| Android | `core/designsystem/PbblsGlyph.kt` (enum of names), `res/raw/pbbls_glyphs.json` (data), `PbblsGlyphState.kt` | `PbblsGlyph(glyph = PbblsGlyph.PATH_RIVER, form = GlyphForm.Progress(2), size = …, tint = …)` |

The native data loads once, lazily, and parsed paths are cached per glyph. Sizes keep the five tokens (inline, row, toolbar, tile, hero). Accessibility is unchanged: a label per use, hidden otherwise. iOS and Android mirror each other 1:1 (names, forms, tokens, fallbacks).

## 3. Migration

A wave tool (`scripts/explore/roles.py`) guesses each glyph's roles from its old twins:

- a path filled in `.active` and identical in the base: `body` if its area is above the focus threshold, else `focus`;
- each lit step's newly filled path: the next `mark`;
- twin geometry with no match in the base: flagged, kept as an override or dropped by the maintainer;
- an `.active` whose filled area differs from its base by almost nothing: flagged to drop.

A rename list, reviewed by the maintainer, applies the name changes (`pebble-outlined-glyph` → `pebble-glyph`, `pebble-dotted-glyph` → `pebble-glyph-dotted`, …). The design page is the review tool: each tile gets a form switcher (regular, half, solid, a progress slider) that animates, filters by tag and by "needs review", and a row previewing the state tokens. The maintainer validates the guessed roles glyph by glyph.

Call sites: the tab bars (iOS `PebblesTabView`, Android's mirror, web nav) take their selected form from the `selected` token; `pebbleRoundLit1` becomes `pebble-round` through the `selected` or `progress` token; `stackLit3` becomes `stack` at `progress(3)`; every other site is a rename.

## 4. Testing

- **Generator (Vitest):** role lint, arc normalisation, carve fixtures (a known body with known inner lines gives a known area), overrides, state-token fallbacks.
- **Path parser contract:** one fixture file of path strings with their expected points, parsed on web, iOS (Swift Testing) and Android (JUnit). Same input, same output, the cross-surface rule.
- **References:** per-form pages of the set on iOS and Android, recorded on CI (pages of at most 240 glyphs, the #1606 limit).
- **Web:** the design page and `glyphForm` in Vitest.

## 5. Order

One stack, each part green on its own:

1. This spec and a decision-log entry (the twin model is reversed).
2. Package: role sources, manifest, generator forms and carving, lint, `states.json`. New outputs are emitted beside the old ones.
3. Role guessing and renames, reviewed by the maintainer on the design page.
4. Web: the new components, call sites, the design page.
5. iOS: the renderer, the enum and data, call sites, references.
6. Android: the same.
7. Removal: the twins, the asset catalog, the VectorDrawables, the old enums and the old emitters.

## Risks

- **Carving quality on hand curves.** Flattening and offsetting can leave slivers. The carve fixtures and the visible-area lint catch the worst; an override fixes the rest.
- **Guessed roles are wrong.** Every glyph passes through the maintainer's review on the design page before part 3 merges.
- **Launch cost of parsing on native.** Lazy load, cached per glyph; the data for about 950 glyphs is estimated at a few hundred KB; the plan measures it.
- **The tab bars change shape.** The selected tab shows the solid form, not today's hand-drawn active. The surface parts call it out and the maintainer checks it on device.
