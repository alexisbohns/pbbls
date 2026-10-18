# Android: the brand scheme re-seeded on the Spine paper — design

- **Issue:** [#1118 — Android brand scheme re-seeded on the Spine paper](https://github.com/alexisbohns/pbbls/issues/1118)
- **Date:** 2026-10-18
- **Scope:** `apps/android` (theme export, generated schemes, splash colours, docs) and the regenerated `apps/web/app/m3-theme.css`. No schema, RPC or payload changes. No screen changes.
- **Size:** Medium (one PR; every screenshot reference re-baselines).
- **Lineage / references:**
  - The web Spine: `2026-10-11-web-path-spine-design.md` (#1052), §Theming, the `--spine-*` tokens.
  - The Android theme: `2026-09-24-android-m3-expressive-theme-design.md` (#853) and the error/Sand revision (#990, decision log 2026-09-28).
  - Visual reference: the comparison mock, a private hosted artifact (https://claude.ai/artifact/VQivrndQRd9oeqrceEcuC3). The approved state is palette "Spine brand · cool Night" with the paper mapped on `surface`.
  - Next: the Android Spine Path, its own issue and spec, builds on §The rule set below.

## Summary

Android will draw the Spine on Material 3 roles, because that is the only way its app code may decide a colour (`ThemeLiteralsTest`). For the Spine's paper to look like the web's when wallpaper colours are off, the brand scheme's **neutral roles** move onto the Spine palette: warm paper in light, a cool night ground with warm ink in dark. The change is app-wide by decision (one scheme, no Path-specific override). Primary, secondary, tertiary, error and Sand do not move. With wallpaper colours on (the default), nothing changes: the dynamic scheme's roles apply, and the Spine takes the wallpaper's tint like every other screen.

This spec also records the **Spine token → M3 role rule set**, the contract the Android Spine Path will build on.

## Decisions (maintainer, 2026-10-18)

| # | Decision | Rationale |
|---|---|---|
| D1 | **The Spine palette becomes the Android brand's neutrals, app-wide.** No Path-only neutral override. | One scheme is M3-orthodox and needs no new mechanism; two neutral worlds in one app would be drift. |
| D2 | **The paper is `surface`.** Ground = `surface`, card stock = `surfaceContainerLowest` (Paper) / `surfaceContainerLow` (Night). The alternative, ground on `surfaceContainerHigh` (the web's exact tone 92), was shown and rejected. | Stock M3 elevation; cards rise from the ground the way every other Material surface does. |
| D3 | **Night keeps the web's cool ground.** The dark schemes' eight surface tiers come from a cool neutral palette (hue 275, chroma 3, the web's `#141416`); `onSurface`, `onSurfaceVariant`, `outline` and the inverse pair stay on the warm palettes. | The maintainer: "sad to lose the blue tint". Contrast is a function of tone and the tones are M3's, so mixing the hue of ground and ink changes no ratio. |
| D4 | **v3 of the export is v2 with the neutral-derived roles recomputed**, by a `@material/material-color-utilities` 0.3.0 script. Every other role is byte-identical to v2. | The #990 precedent (v2 = v1 with the error roles recomputed). A Tonal Spot recipe on the rose seed reproduces v2's primary, secondary and neutral roles within 1/255, so splicing is exact. A full regeneration was rejected: Theme Builder's tertiary (`#98A39A` → `#266A4A`) is not the plain recipe's, and reverse-engineering it buys nothing. Hand-editing was rejected: the file's rule is "never hand-edit", and there would be no contrast levels. |
| D5 | **Wallpaper colours are not pinned.** The Spine, like the rest of the app, reads whatever scheme `PebblesTheme` resolves. | "Still an adaptation with the For You theming." The brand scheme is what the Settings switch, every preview and every screenshot reference shows. |
| D6 | **The rule set uses the web's alphas on `onSurface`** for rules, line, seam, teeth and ripple, not `outline`/`outlineVariant`. | The web draws them as ink at an alpha; keeping that keeps the hairlines translucent over the raked ground and identical in construction across surfaces. |

## Non-goals

- The Android Spine Path (opening, persons, contents, feed, stones, cards, ripple). Own issue and spec; it consumes §The rule set.
- iOS (#921 still decides whether iOS follows the M3-evo theme at all).
- Flipping the web's default theme to M3, or pointing the web's `--spine-*` tokens at `.m3` roles. The rule set is written so that is a mechanical change later.
- Re-tuning the emotion palettes or the stone tones. They are server data (#853: "never scheme roles").

## The Spine brand

### Palettes

Material tones are CIE L*. The web's Spine tokens sit almost exactly on M3 tone steps, which is what makes a role mapping honest:

| Web token | Hex | HCT hue / chroma / tone | Nearest role (Paper) |
|---|---|---|---|
| ground | `#ECE8DF` | 109 / 4.1 / 92 | `surfaceContainerHigh` (92); the approved mapping uses `surface` (98) instead, D2 |
| stock | `#FBF9F4` | 134 / 2.6 / 98 | `surface` (98) |
| ink | `#1F1D1A` | 87 / 2.3 / 11 | `onSurface` (10) |
| ink-2 | `#686255` | 92 / 7.2 / 42 | `onSurfaceVariant` (30) |
| ink-3 | `#9C9588` | 91 / 6.5 / 62 | `outline` (50) |
| ground (Night) | `#141416` | 275 / 2.7 / 6 | `surface` (6) |
| stock (Night) | `#1E1D1B` | 101 / 1.6 / 11 | `surfaceContainerLow` (10) |
| ink (Night) | `#ECE6DA` | 100 / 5.3 / 92 | `onSurface` (90) |

The recipe, per scheme (`isDark`, `contrastLevel` ∈ {0, 0.5, 1}):

| Palette | Light | Dark |
|---|---|---|
| neutral | hue 100, chroma 4 | **surface tiers**: hue 275, chroma 3; **everything else**: hue 100, chroma 4 |
| neutral-variant | hue 92, chroma 8 | hue 92, chroma 8 |
| primary / secondary / tertiary / error | v2, verbatim | v2, verbatim |

"Surface tiers" means exactly: `surface`, `surfaceDim`, `surfaceBright`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`. In the dark schemes these eight read from the cool palette; `background` follows `surface` (it is the same role in M3 and the same value in the export).

Expected standard-contrast anchors (from the mock; the script's output is authoritative and may differ by 1/255):

| Role | Paper | Night |
|---|---|---|
| `surface` | `#FEF9EF` | `#131315` |
| `surfaceContainerLow` | tone 96 of the warm palette | `#1B1B1E` |
| `surfaceContainerHigh` | `#EDE7DE` | tone 17 of the cool palette |
| `onSurface` | `#1D1B16` | `#E7E2D9` |
| `onSurfaceVariant` | `#4C4639` | `#CFC6B4` |
| `outline` | `#7D7667` | tone 60 of the neutral-variant palette |

### Roles recomputed (and nothing else)

`background`, `onBackground`, `surface`, `surfaceDim`, `surfaceBright`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`, `outline`, `outlineVariant`, `inverseSurface`, `inverseOnSurface`, in all six schemes. `surfaceTint` stays `primary`; `shadow` and `scrim` stay black; `extendedSchemes` (Sand) stays v1's values verbatim.

## Pipeline

```
design/pbbls-m3_evo-theme.json  (v2, kept as the input of the recipe)
        │  scripts/generate-theme-export.mjs            ← new (node, material-color-utilities 0.3.0)
        ▼
design/pbbls-m3_evo-theme.json  (v3: description updated, neutral roles recomputed)
        │  scripts/generate-color-schemes.py            ← existing, unchanged
        ▼
core/designsystem/ColorSchemes.kt
        │  npm run generate:m3 --workspace=apps/web      ← existing, unchanged
        ▼
apps/web/app/m3-theme.css
```

- **`apps/android/scripts/generate-theme-export.mjs`.** Reads the export, builds the six `DynamicScheme`s (Tonal Spot variant, rose seed, the palettes above; the dark ones twice, warm and cool), reads the seventeen roles through `MaterialDynamicColors`, writes them back over the export's values, keeps `palettes.neutral` / `palettes.neutral-variant` consistent with them (the dark surface tiers' cool palette has no slot there, and the description says so), and rewrites `description` to name v3, the date, the recipe, the downstream generators and the issue. The script is idempotent: running it on v3 yields v3. It carries one guard: every role it does *not* own must be unchanged between input and output, or it aborts, which is what makes "v2 verbatim" a checked claim.
- **Dependency.** `@material/material-color-utilities@0.3.0` as a devDependency of `@pbbls/android` (`apps/android/package.json`, which has none today), with a `generate:theme` script. The root workspace install picks it up. Pinned exactly; the recipe's output is committed, so a version drift would show as a diff, not a surprise.
- **`res/values/colors.xml` / `values-night/colors.xml`.** `splash_background` becomes the new `LightScheme.surface` / `DarkScheme.surface`. `splash_icon_background` (`LightScheme.primary`) and the launcher ground are untouched.
- **Docs.** `apps/android/CLAUDE.md`, Theme section, "Colours are generated" bullet: the chain gains its first step. One decision-log entry (below). The export JSON's own description is the record of the recipe.

## The rule set: Spine tokens → M3 roles

The contract for the Android Spine Path. "Web alpha" means the value the web's `globals.css` carries for the same token in the same edition.

| Spine token | Role | Paper | Night |
|---|---|---|---|
| ground (page, raked) | `surface` | | |
| stock (card) | `surfaceContainerLowest` | | `surfaceContainerLow` |
| halo (the clearing, day label ground) | `surfaceContainerLow` | | |
| ink | `onSurface` | | |
| ink-2 | `onSurfaceVariant` | | |
| ink-3 | `outline` | | |
| rule | `onSurface` @ web alpha | .14 | .12 |
| card-rule | `onSurface` @ web alpha | .22 | .18 |
| line (the spine) | `onSurface` @ web alpha | .34 | .30 |
| seam | `onSurface` @ web alpha | .30 | .24 |
| teeth | `onSurface` @ web alpha | .20 | .14 |
| ring | `onSurfaceVariant` @ .30 | | `onSurface` @ .17 |
| ripple | `onSurface` @ web alpha | .55 | .50 |
| stone shadow | `onSurface` @ .30 | | black @ .62 |
| spot, edge, tint, seal and carving inks | the emotion palette (`primary`, `secondary`, `light`, `shaded`, `dark`), as the web spec's §Lit stone and §Card anatomy | | |

Rules of use:

- Android reads these through `MaterialTheme.colorScheme`; the Path never names a hex. A `SpineColors` helper (the Path spec decides its shape) may compute the alpha variants once per scheme.
- Under a wallpaper scheme the same roles apply. The paper, hairlines and inks take the wallpaper's neutral tint; the stones and card edges do not, because the emotion palette is server data.
- The grain and rake textures (data URIs on web) carry no scheme colour; their Android form is the Path spec's business.
- If the web later points `--spine-*` at `.m3` roles, this table is the mapping.

## Testing and gates

- `ColorSchemeContrastTest` (every on/container pair ≥ 4.5:1, every ink on every surface step ≥ 4.5:1, `outline` ≥ 3:1, all six schemes) runs unchanged and must stay green. It is the proof that D3's hue mixing costs no contrast. `ExtendedColorsTest` is unaffected.
- `apps/web`: `npm run test --workspace=@pbbls/web` passes once `m3-theme.css` is regenerated (`m3-schemes.test.ts` is the drift gate).
- The recipe script's guard (roles it does not own are unchanged) runs every time it runs; CI does not run the script.
- **Screenshot references:** every reference changes, since every screen's surfaces move. Re-baseline through the `rebaseline-screenshots` label on the PR, then approve the held runs. Never from a local render.
- Manual: a debug build on the Pixel 7 AVD, wallpaper colours off, Paper and Night, contrast standard and raised, next to the mock. Wallpaper colours on: unchanged from today, confirm nothing regressed.

## Delivery

One PR on `feat/1118-android-spine-brand-scheme`, `Resolves #1118`, labels `feat`, `ui`, `android`, `web`, milestone M64 · New Path.

Commits, in order:

1. `chore(android): add material-color-utilities and the theme export recipe`
2. `feat(android): spine brand scheme, neutrals re-seeded on the paper` (v3 JSON, `ColorSchemes.kt`, splash colours)
3. `chore(web): regenerate m3-theme.css from the spine brand scheme`
4. `docs: android theme recipe in CLAUDE.md, decision-log entry`

Lab Note: the change is user-visible only with wallpaper colours off. Label the PR `no-lab-note` unless the maintainer wants one.

Arkaik: no view or data model changes. Before opening the PR, list the acceptances for an appearance/theme one (`AC-app-follows-system-appearance` or similar); if one covers the brand scheme, move it to `development` over MCP, else nothing to move.

## Decision-log entry (to append in the PR)

"2026-10-18 — Android's brand neutrals are the Spine paper: warm in light, a cool night ground with warm ink in dark; the export is a recipe over v2 (#1118)". Supersedes nothing; extends 2026-09-24 (#853) and 2026-09-28 (#990).

## Risks

- **Everything re-baselines.** Reviewing a hundred changed PNGs by eye is not possible; the review is the contrast test plus the AVD pass, and the references are accepted as a set.
- **The warm `surface` at tone 98 is yellower than the web's stock** (`#FEF9EF` vs `#FBF9F4`). The mock showed it and the maintainer accepted it. If it jars on a device, lower the neutral chroma to 3 in the recipe; nothing else moves.
- **A future Theme Builder re-export** would overwrite v3's neutrals with the seed's. The description in the JSON and the CLAUDE.md bullet both say to run the recipe after any re-export; the recipe's guard does not catch this case (it only protects the roles it does not own).
