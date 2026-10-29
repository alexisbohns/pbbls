# @pbbls/icons

The hand-drawn Pebbles icon set and its generator. `src/<name>.svg` (and `<name>.<variant>.svg` twins: `active`, `lit-1`, `lit-2`, `lit-3`) plus the manifest `icons.json` are the single source of truth; the generator lints them and emits native outputs that are **committed** into each surface (the `packages/supabase/types/database.ts` precedent: Xcode Cloud and Gradle never run node). Design: `docs/superpowers/specs/2026-10-28-icon-package-design.md`; style rules: `DNA.md`.

## Commands

| Command | Does |
|---|---|
| `npm run lint --workspace=@pbbls/icons` | lints `src/` and `icons.json` against the rules in `DNA.md`; exit 1 on any error |
| `npm run format --workspace=@pbbls/icons` | rewrites `src/` to the canonical form (`svgo` as a formatter only: path data passes through verbatim) |
| `npm run generate --workspace=@pbbls/icons` | lint, then emit every registered surface and write the owned paths |
| `npm run check --workspace=@pbbls/icons` | emit into memory and fail on any missing, stale or orphaned owned file (CI's freshness gate) |
| `npm run build` / `npm run test` | `tsc --noEmit` / Vitest over `scripts/**/*.test.ts` |

## Never hand-edit a generated file

The generator owns these paths outright and clears them before writing. Fix the source or the emitter, then `generate`:

- `packages/icons/react/` (`<Name>.tsx` per icon, `types.ts`, `index.ts`)
- `apps/ios/Pebbles/Resources/Icons.xcassets/`, `apps/ios/Pebbles/Theme/PbblsIcon.swift`
- `apps/android/app/src/main/res/drawable/ic_pbbls_*.xml`, `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsIcons.kt`

## Adding or redrawing an icon

1. Draw it on the 24-unit square in the DNA's line (`DNA.md`): paths only, absolute `M L C Q A Z`, every point inside `1.5..22.5`, no dashes, no transforms.
2. Save it as `src/<kebab-name>.svg`; a twin as `src/<name>.active.svg` or `src/<name>.lit-N.svg`.
3. Add (or edit) its row in `icons.json`: `name`, one-sentence `meaning`, `variants`, `wave`, `status` (`exploring` is linted but not emitted; `candidate` is emitted everywhere; `shipped` means every surface migrated), `replaces` per surface (from the code, never from memory).
4. `npm run format --workspace=@pbbls/icons && npm run lint --workspace=@pbbls/icons`, then fix every error **in the drawing**, never in the rule.
5. `npm run generate --workspace=@pbbls/icons` and commit the sources with the outputs.

## Exploration tools (`scripts/explore/`, Python 3 stdlib, run with `python3 -I`)

A wave (`waves/<yyyy-mm-slug>/`) records how a set was chosen: briefs, seed, each hand's `sprite.svg` + `NOTES.md`, the game's picks, `library.json` / `library.svg` (every kept drawing under its official name) and `verdict.md`. The generator never reads `waves/`.

- `build.py <wave>` builds the hub (`<wave>/gallery/index.html`, per-hand pages, the library and the lit series); `game-build.py <wave>` the blind pick-three game; `pick3-build.py <wave>` the round-3 picker; `rename-build.py <wave>` the rename sheet. `gallery/` is gitignored; share a hub as a private artifact.
- `tally.py <wave>` prints a Borda count (3/2/1) of `results-picks.json` per id and context: an input to the verdict, never a substitute for it.
- `extract.py <wave> [--only name,…]` writes `src/<name>.svg` and the twins from the wave's `library.svg` with the canonical root; run `format` after it.

## When `Icons` is red

`.github/workflows/icons.yml` runs lint, typecheck, Vitest and `check` on every PR. A red `fresh` step means a committed output no longer matches its source: run `npm run generate --workspace=@pbbls/icons` and commit the result. Never edit the workflow or a generated file by hand to make it pass.
