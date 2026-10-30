# The Pebbles icon package — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `packages/icons` (`@pbbls/icons`) as the single source of the hand-drawn icon set, with a TypeScript generator that lints the SVG sources and emits React components (web, admin), an asset catalog and `PbblsIcon` (iOS), VectorDrawables and `PbblsIcon` (Android), committed into each app and kept fresh by CI. Spec: `docs/superpowers/specs/2026-10-28-icon-package-design.md` (D1–D10). Issue 1311.

**Architecture:** `icons.json` + `src/*.svg` → `scripts/lib/` (parse, lint) → three pure emitters, each `(icons: EmittedIcon[]) => Map<repoRelativePath, contents>` plus the paths it owns → `write.ts` clears the owned paths and writes the map, or (`--check`) compares it with the disk. The precedent is `apps/web/scripts/generate-ios-colors.ts` over `apps/web/lib/theme/ios-colorsets.ts`.

**Tech stack:** tsx, TypeScript strict, `svgo` (exact pin, formatter only), Vitest 5 (node, threads). Swift Testing and the CI-recorded references on iOS. JUnit + Robolectric and Compose Preview Screenshot Testing on Android. Python 3 stdlib for the exploration tools.

**Stack (gh stack, five parts = five PRs, bottom first):**

| Part | Branch | PR title | Tasks |
|---|---|---|---|
| 1 | `chore/1311-icons-package` | `chore(ui): add the icons package, its manifest and its lint` | 1–6 |
| 2 | `chore/1311-icons-react` | `chore(web): generate the icons as react components for web and admin` | 7–8 |
| 3 | `chore/1311-icons-ios` | `chore(ios): generate the icon catalog and PbblsIcon` | 9–10 |
| 4 | `chore/1311-icons-android` | `chore(android): generate the icon drawables and PbblsIcon` | 11–12 |
| 5 | `chore/1311-icons-freshness` | `chore: fail ci when the generated icons are stale` (`Resolves #1311`) | 13 |

Labels: `chore`, `ui` plus the part's surface (`web`, `ios`, `android`); `no-lab-note` on every part (nothing user-visible). Milestone: the umbrella issue's. Each part lints, typechecks and tests alone. Parts 3 and 4 touch disjoint apps and could swap; keep the order so the stack tells React → native → gate.

**Before Part 1:** branch from an updated `main` (`git fetch && git switch -c chore/1311-icons-package origin/main`), never from a feature branch. In a worktree, copy `apps/android/secrets.properties` and `apps/web/.env.local` from the main checkout. iOS runs use a dedicated simulator and a private `-derivedDataPath` per worktree.

---

## Part 1 — package, manifest, DNA, lint, wave (`chore/1311-icons-package`)

### Task 1: The workspace skeleton

**Files:** create `packages/icons/{package.json,tsconfig.json,vitest.config.ts,turbo.json,.gitignore,CLAUDE.md}`; modify the root `CLAUDE.md` workspace table (one row) and `package-lock.json` (by `npm install`).

- [ ] **Step 1: `package.json`.** Copy the devDependency ranges of `tsx`, `vitest`, `typescript`, `@types/node`, `@types/react` from `apps/web/package.json` (they are nested under `apps/web/node_modules`, not hoisted, so the package declares its own). Pin `svgo` exactly (`npm view svgo version`).

```json
{
  "name": "@pbbls/icons",
  "version": "0.0.0",
  "private": true,
  "type": "module",
  "sideEffects": false,
  "main": "./react/index.ts",
  "types": "./react/index.ts",
  "exports": { ".": "./react/index.ts", "./icons.json": "./icons.json" },
  "scripts": {
    "generate": "tsx scripts/build.ts",
    "lint": "tsx scripts/build.ts --lint",
    "check": "tsx scripts/build.ts --check",
    "format": "tsx scripts/build.ts --format",
    "build": "tsc --noEmit",
    "test": "vitest run"
  },
  "peerDependencies": { "react": ">=19" },
  "devDependencies": { "svgo": "<exact>", "tsx": "…", "vitest": "…", "typescript": "…", "@types/node": "…", "@types/react": "…" }
}
```

Until Part 2 writes `react/index.ts`, commit a one-line `react/index.ts` (`export {}`) so `main` resolves; Part 2's generator owns and replaces it.

- [ ] **Step 2: `tsconfig.json`** strict, `module`/`moduleResolution` `bundler`, `jsx: react-jsx`, `noEmit`, `include: ["scripts/**/*.ts", "react/**/*.ts", "react/**/*.tsx", "vitest.config.ts"]`. **`vitest.config.ts`**: `environment: "node"`, `pool: "threads"`, `include: ["scripts/**/*.test.ts"]`, with the web config's one-line reason for threads.
- [ ] **Step 3: `turbo.json`** (`{ "extends": ["//"], "tasks": { "build": { "inputs": ["$TURBO_DEFAULT$", "!waves/**", "!scripts/explore/**"] } } }`) and **`.gitignore`** (`waves/*/gallery/`, `waves/*/sheets/`, `__pycache__/`).
- [ ] **Step 4: `CLAUDE.md`** for the workspace, under 60 lines: what the package is, the four commands, "never hand-edit a generated file: the owned paths are `react/`, `apps/ios/Pebbles/Resources/Icons.xcassets/`, `apps/ios/Pebbles/Theme/PbblsIcon.swift`, `apps/android/app/src/main/res/drawable/ic_pbbls_*.xml`, `…/core/designsystem/PbblsIcons.kt`" (list grows per part), adding an icon (file, row, `format`, `generate`, commit), and the exploration tools (Task 2). Add one row to the root `CLAUDE.md` workspace table: `packages/icons` | `@pbbls/icons` — the hand-drawn icon set and its generator (outputs committed per surface) | `packages/icons/CLAUDE.md`. This is inventory, not a promoted learning.
- [ ] **Step 5:** `npm install` at the root; `npm run build --workspace=@pbbls/icons` → exit 0.

### Task 2: Move the wave and the exploration tools

**Files:** create `packages/icons/waves/2026-10-foundation/` and `packages/icons/scripts/explore/{build.py,game-build.py,tally.py,extract.py}`.

- [ ] **Step 1: Copy the wave inputs** from the exploration working folder `/Users/alexis/code/pbbls-icons-wave-2026-10/`: every `BRIEF*.md`, `SEED.md`, `notes.json`, `results-picks.json`, `specials2.json`, `runnersup.json`, `alternates.json`, `pick3-results.json`, `names-export.json`, `library.json`, `library.svg`, `lit-families.json`, `lit-sources.svg`, `lit/L1`–`L5/`, `verdict.md`, `carve.py`, and `hands/h00`–`h23,h99/{sprite.svg,NOTES.md}`. `h20-tools/gen.py` and `h22-tools/gen.py` go to `hands/h20/gen.py` and `hands/h22/gen.py`. Skip `results.json` (empty), `gallery/`, `sheets/`. Read every file before committing: the repo is public.
- [ ] **Step 2: Move the builders** (`build.py`, `game-build.py`, `pick3-build.py`, `rename-build.py`) into `scripts/explore/`, with the smallest edits that make them repo tools: `build.py` already takes the wave folder as `argv[1]`; the others gain the same argument instead of `ROOT = Path(__file__).parent`. Hard-coded id lists (`IDS`/`ACT`, `BASE`/`PAIRS`, `LABELS`) become a read of the wave's `library.json` (labels come from the first clause of `meaning` in `icons.json` when a row exists). Outputs land in `<wave>/gallery/`.
- [ ] **Step 3: `tally.py <wave>`** prints, per id, the Borda score (3/2/1 for ranks 1–3) of each hand across contexts A/B/C and the `special` flags, sorted. **`extract.py <wave>`** writes `src/<name>.svg` from the wave's `library.svg` (`<name>` symbols), `<name>.active.svg` from `<name>-active` and `<name>.lit-N.svg` from `<name>-lit-N`, with the canonical root attributes, refusing a symbol that has anything but `<path>` children. `--only name,…` limits the run.
- [ ] **Step 4: Verify.** `python3 -I packages/icons/scripts/explore/build.py packages/icons/waves/2026-10-foundation` and the same for `game-build.py` write `gallery/index.html` and `gallery/game.html`; `git status` shows neither (ignored). `python3 -I …/tally.py …` prints 23 ids; `extract.py` writes 121 bases, 34 active twins and the lit series (count them against `library.json`).

### Task 3: DNA, verdict and manifest

**Files:** create `packages/icons/DNA.md`, `packages/icons/waves/2026-10-foundation/verdict.md`, `packages/icons/icons.json`.

- [ ] **Step 1: `DNA.md`**: the round-3 DNA paragraph verbatim from `BRIEF3.md` ("## DNA"), the Riverbed line description from its "## The line" section, the "one active rule" (a stone lights), and the canvas rules as the spec §2 table, each rule with one line of why (VectorDrawable has no dash support, relative commands hide coordinates, etc.).
- [ ] **Step 2: `verdict.md`.** The verdict is already written (2026-10-29) at `/Users/alexis/code/pbbls-icons-wave-2026-10/verdict.md`: per-icon picks among h21–h23 (h23 ×10, h22 ×8, h21 ×5). Copy it to `waves/2026-10-foundation/verdict.md` (it already lists the official names, the lit series and the carved stones) and append the `tally.py` output it was weighed against. Spec R1 (rows as `exploring` when no verdict exists) no longer applies to this wave.
- [ ] **Step 3: `icons.json`**: one row per entry of the wave's `library.json`, in its order (grouped by name prefix, the 23 chosen first within their group), `variants` from its `hasActive` and `lit` fields (`["active"]`, `["active", "lit-1", "lit-2", "lit-3"]`, …), `wave: "2026-10-foundation"`, status `candidate`, `meaning` one sentence from the hand's notes for the chosen 23 and from the drawing itself for the rest (what it shows, not where it came from). Fill `replaces` from the code, never from memory:

```bash
grep -rhoE '(systemName|systemImage): "[^"]+"' apps/ios/Pebbles | sort | uniq -c | sort -rn
grep -rhoE 'R\.drawable\.ic_[a-z_]+' apps/android/app/src/main/kotlin | sort | uniq -c | sort -rn
grep -rhoE 'import \{[^}]*\} from "lucide-react"' apps/web apps/admin --exclude-dir=node_modules --exclude-dir=ui
```

A name goes in `replaces` only where the drawing means the same thing at that call site; when unsure, leave it out and say so in the PR body.

### Task 4: Manifest, parse, format, names

**Files:** create `packages/icons/scripts/lib/{manifest.ts,parse.ts,format.ts,names.ts}` and `{manifest,parse,format,names}.test.ts`.

- [ ] **Step 1: Failing tests first.** `names.test.ts`: `check-circle` → `CheckCircle` / `checkCircle` / `CHECK_CIRCLE` / `ic_pbbls_check_circle` / `pbbls-check-circle`; `home.active` → `HomeActive` / `homeActive` / `HOME_ACTIVE` / `ic_pbbls_home_active` / `pbbls-home-active`; `path.lit-2` → `PathLit2` / `pathLit2` / `PATH_LIT_2` / `ic_pbbls_path_lit_2` / `pbbls-path-lit-2`. `parse.test.ts`: a canonical file parses to `{ root, paths: [{ d, filled, evenOdd }] }`; a `<circle>`, a nested `<g>`, a comment or a second root throws with the file name. `format.test.ts`: an Illustrator-style export (XML declaration, comment, `id`, three-decimal numbers, width/height) formats to the canonical form; formatting is idempotent; `d` survives unchanged at two decimals.
- [ ] **Step 2: Implement.** `manifest.ts` exports `type Status = "exploring" | "candidate" | "shipped"`, `type IconRow`, `type Manifest`, `loadManifest(root)` (shape-checked by hand, no `any`, no assertion), and `emitted(manifest)` (rows not `exploring`, expanded with their `variants`, in manifest order). `format.ts` runs `svgo.optimize` with an explicit plugin list (`removeDoctype`, `removeXMLProcInst`, `removeComments`, `removeMetadata`, `removeEditorsNSData`, `removeTitle`, `removeDesc`, `cleanupAttrs`, `cleanupNumericValues` at `floatPrecision: 2`) and **no** `preset-default`, then sets the root attributes in the canonical order (spec §2); `pretty: true`, `indent: 2`, `finalNewline: true`. `parse.ts` is a tokenizer of about 40 lines for exactly that grammar.
- [ ] **Step 3:** `npm run test --workspace=@pbbls/icons` green; `npm run build --workspace=@pbbls/icons` green.

### Task 5: Lint, the CLI, and the sources

**Files:** create `packages/icons/scripts/lib/lint.ts`, `lint.test.ts`, `packages/icons/scripts/build.ts`, `packages/icons/src/*.svg`.

- [ ] **Step 1: Failing tests.** One passing fixture (a base and its twin) and one failing fixture per row of spec §2: wrong viewBox; extra root attribute; `<circle>`; `transform`; relative `c`; coordinate 0.9 and 23.1 (the message names the file and the number); an arc's end point out of range while its radius is not; `fill` in a base file; four filled paths in a twin; a filled area over 64 warns but passes; `fill-rule` on an unfilled path; `fill-rule="nonzero"`; a carved stone (`fill="currentColor" fill-rule="evenodd"`, two subpaths) passes; any `stroke-dasharray` fails; a non-canonical file; row without file; file without row; a listed variant without its file; a twin file not in `variants`; a variant outside the four; twin without base; duplicate and non-kebab names; missing wave folder; unknown status.
- [ ] **Step 2: Implement** `lint(root): { errors: LintIssue[], warnings: LintIssue[] }` with `type LintIssue = { file: string; rule: string; message: string }`, and `build.ts` with `--lint` (print issues, exit 1 on any error), `--format` (rewrite `src/` through `format.ts`), and, for now, a default that runs the lint and reports "no emitters yet". The repo root is `fileURLToPath(new URL("../../..", import.meta.url))`.
- [ ] **Step 3: Sources.** `python3 -I packages/icons/scripts/explore/extract.py packages/icons/waves/2026-10-foundation --hand <verdict hand> [--take …]`, then `npm run format --workspace=@pbbls/icons`, then `npm run lint --workspace=@pbbls/icons`. Fix every error **in the drawing** (nudge the point inside 1.5..22.5; redraw the glyphs ring as short strokes if the source still carries `stroke-dasharray`), never in the rule, and list each edit in `verdict.md`. Expected: 29 files, zero errors.

### Task 6: Decision log, then Part 1's gate

- [ ] **Step 1:** append one entry to `docs/decisions/log.md` (house format: Status, Scope `cross-surface`, Context, Decision, Why with the rejected alternatives — a shared icon font, per-surface hand-tracing, building outputs at build time — Consequences, Refs to the spec and this plan).
- [ ] **Step 2: Gate.** `npm run lint --workspace=@pbbls/icons && npm run build --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons`, all green. Commit as `chore(ui): …` commits (skeleton; wave and tools; DNA, verdict and manifest; lint and sources; decision). `gh stack init chore/1311-icons-package` on this branch.

---

## Part 2 — React emitter, web and admin (`chore/1311-icons-react`)

`gh stack add chore/1311-icons-react`.

### Task 7: React emitter, write, `--check`

**Files:** create `packages/icons/scripts/lib/{emit-react.ts,write.ts,emit.ts}` and tests; modify `scripts/build.ts`; generated `packages/icons/react/*`.

- [ ] **Step 1: Failing tests.** `emit-react.test.ts` on a two-row fixture (one with a twin and a lit shape): the map's keys are exactly `packages/icons/react/{Home,HomeActive,Trash,types,index}.ts(x)`; `Home.tsx` equals a committed expected string (header comment, JSDoc from `meaning`, `aria-hidden` logic, `d` verbatim, lit shape with `fill="currentColor"`, plus `fillRule="evenodd"` and `stroke="none"` when the source has them); `index.ts` re-exports in manifest order; an `exploring` row emits nothing. A render test builds the fixture into a temp dir, imports `Home`, and checks `renderToStaticMarkup(createElement(Home, {}))` has `aria-hidden="true"` and no `role`, and with `{ "aria-label": "Home", size: 20 }` has `role="img"`, `width="20"`, no `aria-hidden`. `write.test.ts` on a temp root: writing removes an orphaned owned file, spares a non-owned sibling (`ic_trash.xml` next to `ic_pbbls_*.xml`), and `check` reports `missing`, `stale` and `orphaned` separately and returns non-zero for each.
- [ ] **Step 2: Implement.** `emit.ts` holds `type EmittedIcon = { name: string; meaning: string; isActive: boolean; base: string; paths: ParsedPath[] }` and `type Emitter = { name: string; owned: OwnedPaths; emit(icons: EmittedIcon[]): Map<string, string> }`, `OwnedPaths = { dirs: string[]; files: string[]; patterns: { dir: string; prefix: string; suffix: string }[] }`. `build.ts` default: lint (abort on error) → parse → every registered emitter → `write`; `--check`: same, then `check` instead of `write`, printing each stale path and the fix (`npm run generate --workspace=@pbbls/icons`). Register only `emit-react` in this part.
- [ ] **Step 3:** `npm run generate --workspace=@pbbls/icons`, then `npm run check --workspace=@pbbls/icons` → exit 0; run `generate` twice and `git status` shows no change after the second.

### Task 8: Wire web and admin

**Files:** modify `apps/web/package.json`, `apps/admin/package.json`, `package-lock.json`; create `apps/web/lib/icons/pbbls-icons.test.ts`; modify `packages/icons/CLAUDE.md`.

- [ ] **Step 1:** add `"@pbbls/icons": "*"` to both apps' `dependencies` (the `@pbbls/supabase` precedent), `npm install`. No `next.config.ts` change (spec D1).
- [ ] **Step 2: Test.** `apps/web/lib/icons/pbbls-icons.test.ts` imports `{ CheckCircle }` from `@pbbls/icons` and asserts its `renderToStaticMarkup` has `viewBox="0 0 24 24"` and `stroke="currentColor"` (`createElement`, since the web suite includes `lib/**/*.test.ts` only, node environment).
- [ ] **Step 3: Gate.** `npm run lint --workspace=apps/web && npm run test --workspace=apps/web && npm run build --workspace=apps/web`, `npm run lint --workspace=apps/admin && npm run test --workspace=apps/admin && npm run build --workspace=apps/admin`, plus the package's `lint`, `build`, `test`, `check`. All green.
- [ ] **Step 4: Risk R2.** On a scratch branch, commit a whitespace change under `packages/icons/waves/` alone and run `npx turbo-ignore @pbbls/web --fallback=HEAD^` and the same for `@pbbls/admin`. Record the result in the PR body. If either says it would build, follow spec R2 and say so; do not change Vercel settings in this stack.

---

## Part 3 — iOS (`chore/1311-icons-ios`)

`gh stack add chore/1311-icons-ios`.

### Task 9: iOS emitter

**Files:** create `packages/icons/scripts/lib/emit-ios.ts`, `emit-ios.test.ts`; register it in `build.ts`; modify `packages/icons/CLAUDE.md` (owned paths).

- [ ] **Step 1: Failing tests** on the fixture: keys are `apps/ios/Pebbles/Resources/Icons.xcassets/Contents.json`, `…/pbbls-home.imageset/{Contents.json,pbbls-home.svg}` and the same for `pbbls-home-active` and `pbbls-trash`, plus `apps/ios/Pebbles/Theme/PbblsIcon.swift`. The imageset `Contents.json` equals, byte for byte:

```json
{
  "images" : [
    {
      "filename" : "pbbls-home.svg",
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  },
  "properties" : {
    "preserves-vector-representation" : true,
    "template-rendering-intent" : "template"
  }
}
```

The SVG has `width="24" height="24"`, no `currentColor` (`#000000` instead). The Swift file equals an expected string: header comment, `enum PbblsIcon: String, CaseIterable, Sendable` with `case home = "home"`, `case homeActive = "home.active"`, `case trash = "trash"` each under a `///` meaning line, `assetName`, `activeTwin`, `lit(_:)`, and `PbblsIconImage` (spec §5).
- [ ] **Step 2: Implement.** The Swift template lives in `emit-ios.ts` as one literal. `PbblsIconImage` stores `@ScaledMetric private var side: CGFloat` initialised with `ScaledMetric(wrappedValue: size, relativeTo: textStyle)`, draws `Image(icon.assetName).renderingMode(.template).resizable().scaledToFit().frame(width: side, height: side)`, applies `.foregroundStyle(style)` only when `style` is non-nil (an inner `.foregroundStyle` would beat the caller's outer one, so the default inherits the ambient style), and `.accessibilityLabel(Text(label))` or `.accessibilityHidden(true)`. Owned paths: the whole `Icons.xcassets` dir and the one Swift file.

### Task 10: Generate, test, build on iOS

**Files:** generated catalog and Swift file; create `apps/ios/PebblesTests/Theme/PbblsIconTests.swift`, `apps/ios/PebblesTests/References/IconSheetReferences.swift`.

- [ ] **Step 1:** `npm run generate --workspace=@pbbls/icons`; `cd apps/ios && xcodegen generate`. `project.yml` stays untouched (spec §5); if the catalog is missing from the target after generation, stop and report rather than adding a source entry blindly.
- [ ] **Step 2: Tests.**

```swift
import Testing
import UIKit
@testable import Pebbles

@Suite("Pbbls icons") struct PbblsIconTests {
    static let manifest = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().appendingPathComponent("../../../../packages/icons/icons.json").standardized

    @Test func everyCaseIsATemplateImageInTheBundle() throws {
        for icon in PbblsIcon.allCases {
            let image = try #require(UIImage(named: icon.assetName, in: .main, with: nil), "\(icon.assetName)")
            #expect(image.renderingMode == .alwaysTemplate)
        }
    }
    @Test func casesMatchTheManifest() throws { /* decode rows; emitted = status != "exploring", plus "<name>.<variant>" per variants; #expect(emitted == PbblsIcon.allCases.map(\.rawValue)) */ }
    @Test func twinsMatchVariants() throws { /* activeTwin != nil ⇔ "active" in variants; lit(n) != nil ⇔ "lit-n" in variants */ }
}
```

`IconSheetReferences.swift` follows `PebblesTests/References/HomeReferences.swift`: one sheet of every base and twin at 24pt on the Spine paper, light and dark. The PNGs are recorded on CI by labelling the PR `rebaseline-ios-screenshots`; never record locally.
- [ ] **Step 3: Gate.** `cd apps/ios && swiftlint` (no new error); clear the worktree's DerivedData `Build/` after `xcodegen` (stale objects otherwise); `xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=<this worktree's simulator>' -derivedDataPath build/dd -only-testing:PebblesTests/PbblsIconTests` green; `xcrun assetutil --info build/dd/Build/Products/Debug-iphonesimulator/Pebbles.app/Assets.car | grep -c '"pbbls-'` prints the emitted count. The full suite's local reference failures must be byte-identical to a baseline run on `main` (27 known), plus the new sheet's "rendered on CI only". The package's `check` is green.

---

## Part 4 — Android (`chore/1311-icons-android`)

`gh stack add chore/1311-icons-android`. `export ANDROID_HOME=$HOME/Library/Android/sdk` before Gradle.

### Task 11: Android emitter

**Files:** create `packages/icons/scripts/lib/emit-android.ts`, `emit-android.test.ts`; register it; update `packages/icons/CLAUDE.md`.

- [ ] **Step 1: Failing tests.** Keys: `apps/android/app/src/main/res/drawable/ic_pbbls_{home,home_active,trash}.xml` and `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsIcons.kt`. A drawable equals an expected string: XML declaration, a `<!-- Generated by packages/icons (<name>): <meaning> Do not edit. -->` comment, `<vector>` 24dp/24 viewport, one `<path>` per source path with `android:pathData` verbatim, `strokeColor="#FFFFFFFF"`, `strokeWidth="1.75"`, `strokeLineCap="round"`, `strokeLineJoin="round"`, `fillColor="#00000000"`; the lit shape has `fillColor="#FFFFFFFF"`, keeps its stroke unless the source has `stroke="none"` (then no `strokeColor`/`strokeWidth`), and a carved one adds `android:fillType="evenOdd"`. The Kotlin file: header, `package app.pbbls.android.core.designsystem`, imports, `enum class PbblsIcon(val manifestName: String, @DrawableRes val drawableRes: Int)` with `HOME("home", R.drawable.ic_pbbls_home),` … a trailing `;`, `val activeTwin: PbblsIcon?` and `fun lit(n: Int): PbblsIcon?` as `when`s, and `PbblsIconImage` (spec §5). Owned: pattern `{ dir: "apps/android/app/src/main/res/drawable", prefix: "ic_pbbls_", suffix: ".xml" }` and the one Kotlin file.
- [ ] **Step 2: Implement**, formatting the Kotlin so stock ktlint passes (trailing commas, four-space indent, no wildcard imports, lines under 140).

### Task 12: Generate, test, build on Android

**Files:** generated drawables and Kotlin; create `apps/android/app/src/test/kotlin/app/pbbls/android/core/designsystem/PbblsIconsTest.kt`, `apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/PbblsIconsScreenshots.kt`.

- [ ] **Step 1:** `npm run generate --workspace=@pbbls/icons`.
- [ ] **Step 2: Tests.** `PbblsIconsTest` runs under `RobolectricTestRunner` (the #857 setup): every `PbblsIcon.entries` inflates through `AppCompatResources.getDrawable(context, it.drawableRes)` as a `VectorDrawable` whose intrinsic width equals 24dp at the test density; the emitted manifest rows read from `File("../../../packages/icons/icons.json")` (the `StoneComposerTest` precedent) with `Json.parseToJsonElement` equal `entries.map { it.manifestName }`; the twins match `variants`. `PbblsIconsScreenshots.kt` follows `DesignSystemScreenshots.kt`: one preview of the whole set in light and dark. Its reference is recorded by the CI bot (`rebaseline-screenshots` label); never re-baseline locally.
- [ ] **Step 3: Gate.** `cd apps/android && ./gradlew ktlintCheck testDebugUnitTest --tests 'app.pbbls.android.core.designsystem.PbblsIconsTest' --tests '*ArchitectureBoundaryTest' lint assembleDebug`. `lint` must add no error; `VectorPath` warnings on long hand-drawn paths are accepted as warnings, not baselined. The package's `check` is green.

---

## Part 5 — CI freshness check (`chore/1311-icons-freshness`)

`gh stack add chore/1311-icons-freshness`.

### Task 13: `icons.yml`

**Files:** create `.github/workflows/icons.yml`; modify `packages/icons/CLAUDE.md` ("When `icons` is red").

- [ ] **Step 1: Workflow**, commented in the `web.yml` voice (why no path filter, why `!cancelled()`):

```yaml
name: Icons
on:
  pull_request:
  push:
    branches: [main]
concurrency:
  group: icons-${{ github.ref }}
  cancel-in-progress: true
permissions:
  contents: read
jobs:
  check:
    name: lint · typecheck · vitest · fresh
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-node@v7
        with: { node-version: 24, cache: npm }
      - run: npm ci
      - name: Lint the sources
        run: npm run lint --workspace=@pbbls/icons
      - name: Typecheck
        if: '!cancelled()'
        run: npm run build --workspace=@pbbls/icons
      - name: Vitest
        if: '!cancelled()'
        run: npm run test --workspace=@pbbls/icons
      - name: Generated outputs are fresh
        if: '!cancelled()'
        run: npm run check --workspace=@pbbls/icons
```

- [ ] **Step 2: Prove it fails.** Locally, change one coordinate in `src/trash.svg` without regenerating: `npm run check --workspace=@pbbls/icons` exits 1 and names `react/Trash.tsx`, the iOS SVG and `ic_pbbls_trash.xml`; delete `react/Home.tsx`: it reports `missing`; add `react/Stray.tsx`: `orphaned`. Revert. Push and confirm the run is green; ask the maintainer whether to add `Icons / check` to the required checks.
- [ ] **Step 3:** `packages/icons/CLAUDE.md` gains: a red `check` is fixed by `npm run generate --workspace=@pbbls/icons` and a commit, never by editing the workflow or the owned paths by hand.

---

## Opening the stack

- [ ] Each part's PR body starts with `Refs #1311` (Part 5: `Resolves #1311`), lists key files, the gate commands run and their results, carries the `no-lab-note` label and no `## Lab Note` section. No Kritik finding id anywhere.
- [ ] `git fetch && git merge --ff-only origin/main` on the local `main` before pushing; `gh stack push`, then `gh stack submit --auto`; fix the titles to the table above with `gh pr edit`. Label Part 3 `rebaseline-ios-screenshots` and Part 4 `rebaseline-screenshots` once each, alone, and approve the held runs; the upper branch wins PNG conflicts.
- [ ] Merge is the maintainer's: `gh stack merge <top PR> --yes --squash`.

## Follow-up issues (filed 2026-10-29, milestone M66 · Brand icons)

- #1314 `[Chore] Migrate iOS to the Pebbles icons` (`ios`, `ui`): replace the `replaces.ios` symbols, retire `PebblesIcon` size tokens where unused, size per call site (spec R4).
- #1315 `[Chore] Migrate Android to the Pebbles icons` (`android`, `ui`): replace the `replaces.android` drawables, delete them.
- #1313 `[Chore] Migrate web to the Pebbles icons` (`web`, `ui`): replace lucide outside `components/ui/`.
- #1312 `[Chore] Lint shipped icons' replaced names across surfaces` (`ui`): `scripts/lint-replaces.ts` (spec D10), wired into `icons.yml`, landing with the first migration.
