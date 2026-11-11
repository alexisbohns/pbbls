# Icon forms, part 7: removal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the twin model now that iOS (#1616), Android (#1618) and web (#1617) draw every glyph from its forms: the twin sources, the iOS asset catalog, the Android VectorDrawables, the old enums, the old emitters and the static React twins.

**Architecture:** Three PRs. 7a (iOS) and 7b (Android) branch from `main` in parallel: each deletes one old emitter and its outputs, moves the two helpers the glyph emitter borrows from it, and renames the surface's size enum to `PbblsGlyphSize`. 7c (package and web) branches from `main` after both merge: it deletes the 905 twin files, the `variants` key and every line of code that reads twins, then folds the four rows the forms review retired.

**Tech Stack:** TypeScript generator (`packages/icons`, Vitest), SwiftUI + Swift Testing (`apps/ios`), Kotlin + Compose + JUnit (`apps/android`), Next.js (`apps/web`).

**Spec:** `docs/superpowers/specs/2026-11-08-icon-forms-design.md` §5 item 7 ("the twins, the asset catalog, the VectorDrawables, the old enums and the old emitters"); `packages/icons/waves/2026-11-forms/verdict.md`, "For part 7".

**Ground rules (every part):** never touch the untracked `apps/web/AGENTS.md`. Commits are conventional and end with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never hand-edit a generated file: change the emitter, then `npm run generate --workspace=@pbbls/icons`. Force-push is denied: integrate `main` by merge. Never commit a locally rendered reference image.

**Checks:**
- Package: `npm run lint --workspace=@pbbls/icons && npm run build --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons && npm run lint:replaces --workspace=@pbbls/icons`.
- iOS (from `apps/ios`): `swiftlint`; `xcodegen generate`, then `rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build` (stale objects), then `xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -skip-testing:PebblesTests/References`. Reference suites fail locally on untouched `main`; CI is their gate.
- Android (from `apps/android`, after `export ANDROID_HOME=$HOME/Library/Android/sdk`): `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`. Screenshots: CI is the gate.
- Web (from `apps/web`): `npx eslint . && npx tsc --noEmit -p . && npx vitest run`.

## Decisions taken in this plan

- **Generate does not delete an emitter's old files once the emitter is gone.** `write` clears only the paths a registered emitter owns, and `check` reports orphans only inside them. Each removal therefore `git rm`s the old outputs explicitly in the same commit that unregisters the emitter.
- **The helpers the glyph emitters borrow move with them.** `emit-ios-glyphs.ts` imports `docLines` and `swiftCase` from `emit-ios.ts`; `emit-android-glyphs.ts` imports `kdocLines` from `emit-android.ts`. They move verbatim, with their tests, into the glyph emitter that uses them, before the old file is deleted.
- **The native size enums become `PbblsGlyphSize`.** The iOS plan deferred this rename to part 7 ("`PbblsIconSize` keeps its name in this part… Part 7 renames it with the old enum's removal"). Android mirrors it 1:1. The cases and values stay. The web keeps `PBBLS_ICON_SIZE`: it is a map of Tailwind classes in 54 files, it never named the old enum, and the package is still `@pbbls/icons`.
- **The old sheet references go, and the form sheets take their place.** iOS `IconSheetReferences` and Android `PbblsIconsScreenshots` draw the twin assets. Their `regular` page is what `GlyphFormSheetReferences` / `GlyphFormSheet` already draw. Deleting a test and its own reference PNGs needs no re-baseline.
- **7a and 7b run in parallel.** They edit the same three lines (the `EMITTERS` array and its imports in `scripts/build.ts`, the "Never hand-edit" list in `packages/icons/CLAUDE.md`, `GENERATED` in `scripts/explore/rename.py`). The second to merge takes `main` in and keeps both removals. 7c waits for both, because the twins still feed the old iOS and Android emitters until they are gone.
- **Twin geometry is not kept anywhere.** The forms review (part 3) already turned every twin worth keeping into roles or an override (`verdict.md`); git history holds the rest. `scripts/explore/roles.py`, which guessed roles from twins, has done its one job and is deleted.
- **A leftover twin file is a lint error, not a silent skip.** After 7c a source is `<name>.svg`, `<name>.dotted.svg` or `<name>.<form>.svg` (an override). Anything else fails `lint` with that sentence.

---

## Part 7a — iOS (branch `chore/1607-icon-removal-ios` from updated `main`)

The `-ios` slug keeps Vercel from deploying it.

### Task 7a.1: Commit this plan

**Files:** Create `docs/superpowers/plans/2026-11-11-icon-forms-removal.md` (this file).

- [ ] **Step 1:** `git switch -c chore/1607-icon-removal-ios origin/main`, add this file, commit `docs(ui): plan the icon forms removal (part 7)`.

### Task 7a.2: Remove the iOS emitter and its outputs

**Files:**
- Modify: `packages/icons/scripts/lib/emit-ios-glyphs.ts`, `packages/icons/scripts/lib/emit-ios-glyphs.test.ts`, `packages/icons/scripts/build.ts`, `packages/icons/scripts/lib/names.ts`, `packages/icons/scripts/lib/names.test.ts`, `packages/icons/scripts/explore/rename.py`, `packages/icons/CLAUDE.md`
- Delete: `packages/icons/scripts/lib/emit-ios.ts`, `packages/icons/scripts/lib/emit-ios.test.ts`, `apps/ios/Pebbles/Resources/Icons.xcassets/`, `apps/ios/Pebbles/Theme/PbblsIcon.swift`

- [ ] **Step 1: Move the helpers.** Cut `docLines` and `swiftCase` from `emit-ios.ts` into `emit-ios-glyphs.ts`, verbatim, together with every constant they read (`DOC_PREFIX`, `LINE_WIDTH`, `SWIFT_KEYWORDS`) and the `camelName` import. Replace the line `import { docLines, swiftCase } from "./emit-ios"` with nothing, and export both from `emit-ios-glyphs.ts`. Move their `describe` blocks from `emit-ios.test.ts` into `emit-ios-glyphs.test.ts`, importing from `./emit-ios-glyphs`.

- [ ] **Step 2: Run** `npm run test --workspace=@pbbls/icons` — PASS (same count as before: the moved tests still run).

- [ ] **Step 3: Unregister the emitter.** In `scripts/build.ts`, delete `import { iosEmitter } from "./lib/emit-ios"` and remove `iosEmitter, ` from `EMITTERS`. Delete `scripts/lib/emit-ios.ts` and `scripts/lib/emit-ios.test.ts`. In `names.ts`, delete `assetName` (it served the asset catalog only: `grep -rn assetName packages/icons/scripts` must show nothing else) and its test in `names.test.ts`.

- [ ] **Step 4: Delete the old outputs.**

```bash
git rm -r -q apps/ios/Pebbles/Resources/Icons.xcassets apps/ios/Pebbles/Theme/PbblsIcon.swift
```

- [ ] **Step 5: Docs and tools.** In `packages/icons/CLAUDE.md`, "Never hand-edit a generated file", delete the line `` - `apps/ios/Pebbles/Resources/Icons.xcassets/`, `apps/ios/Pebbles/Theme/PbblsIcon.swift` ``. In `scripts/explore/rename.py`, replace the two iOS entries of `GENERATED` with the iOS glyph outputs:

```python
    "apps/ios/Pebbles/Resources/pbbls-glyphs.json",
    "apps/ios/Pebbles/Theme/PbblsGlyph.swift",
    "apps/ios/Pebbles/Theme/PbblsGlyphState.swift",
```

- [ ] **Step 6: Run** the package checks — PASS. `npm run generate --workspace=@pbbls/icons` reports one emitter fewer and `git status --short` shows no file it rewrote.

Do not commit yet: the iOS app no longer compiles until Task 7a.3.

### Task 7a.3: Move the app off the old enum and rename the size enum

**Files:**
- Rename: `apps/ios/Pebbles/Theme/Icon+Pebbles.swift` → `apps/ios/Pebbles/Theme/Glyphs/PbblsGlyphSize.swift`; `apps/ios/PebblesTests/Theme/PbblsIconSizeTests.swift` → `apps/ios/PebblesTests/Theme/PbblsGlyphSizeTests.swift`
- Modify: `apps/ios/Pebbles/Theme/Glyphs/PbblsGlyphView.swift`, `apps/ios/Pebbles/Theme/Debug/IconLabView.swift`
- Delete: `apps/ios/PebblesTests/Theme/PbblsIconTests.swift`, `apps/ios/PebblesTests/References/IconSheetReferences.swift`, `apps/ios/PebblesTests/References/__References__/IconSheetReferences/` (16 PNGs)

- [ ] **Step 1: The size file.** `git mv apps/ios/Pebbles/Theme/Icon+Pebbles.swift apps/ios/Pebbles/Theme/Glyphs/PbblsGlyphSize.swift`, then make its whole content:

```swift
import CoreGraphics

/// The sizes a glyph draws at (icon spec D6). A 24-unit drawing with a 1.75
/// stroke draws a 1.2pt line at 16, the floor; nothing is drawn smaller except
/// `CollectionMark`'s 14pt band mark, noted there. The three SF Symbols the app
/// keeps as chrome (`lock`, the chevrons) size themselves with an inline
/// rounded font at their call sites (iOS migration spec D1, D6).
///
/// - `inline`: beside text (karma amounts, "taken from your photo"), relative to that text's style.
/// - `row`: a list row's leading icon, a segmented pill, a menu item.
/// - `toolbar`: toolbar and chrome buttons inside their 44×44 hit frame.
/// - `tile`: `SurfaceTile`, the Home shortcuts, card leading icons.
/// - `hero`: `ContentUnavailableView` images, the achievement celebration, the invalid-invite page.
enum PbblsGlyphSize: CGFloat, CaseIterable {
    case inline = 16
    case row = 20
    case toolbar = 22
    case tile = 28
    case hero = 48
}
```

- [ ] **Step 2: Rename the uses.**

```bash
cd apps/ios
grep -rl 'PbblsIconSize' Pebbles PebblesTests | xargs sed -i '' 's/PbblsIconSize/PbblsGlyphSize/g'
git mv PebblesTests/Theme/PbblsIconSizeTests.swift PebblesTests/Theme/PbblsGlyphSizeTests.swift
```

In `PbblsGlyphSizeTests.swift`, change `@Suite("PbblsIconSize")` to `@Suite("PbblsGlyphSize")` and `struct PbblsIconSizeTests` to `struct PbblsGlyphSizeTests`, and delete the whole `sizedInitializer` test (it built `PbblsIconImage`, which is gone). Keep `rawValues`.

- [ ] **Step 3: Delete the old tests.**

```bash
git rm -q PebblesTests/Theme/PbblsIconTests.swift PebblesTests/References/IconSheetReferences.swift
git rm -r -q PebblesTests/References/__References__/IconSheetReferences
```

- [ ] **Step 4: Nothing names the old model.**

```bash
grep -rnE '\bPbblsIcon(Image|Size)?\b|Icons\.xcassets|activeTwin|IconSheetReferences' Pebbles PebblesTests project.yml
```

Expected: no output.

- [ ] **Step 5: Build and test** (iOS checks above) — `swiftlint` clean, the suite PASSES. Then `cd ../.. && git status --short` and confirm no file outside `packages/icons`, `apps/ios` and this plan changed.

- [ ] **Step 6: Commit** everything from Tasks 7a.2 and 7a.3 as one commit: `chore(ios): remove the icon asset catalog and its emitter`. Body: the asset catalog, `PbblsIcon.swift` and its emitter are gone; `PbblsIconSize` is `PbblsGlyphSize`; the icon sheet references give way to the form sheets.

### Task 7a.4: Open the PR

- [ ] **Step 1:** Run every check (package and iOS). Push with `git -c credential.helper= -c credential.helper='!gh auth git-credential' push -u origin chore/1607-icon-removal-ios`.
- [ ] **Step 2:** `gh pr create --base main --title "chore(ios): remove the icon asset catalog and its emitter" --label chore --label ios --label ui --label no-lab-note --milestone "M66 · Brand icons"`. Body starts `Part of #1607`, lists the deletions and the rename, notes that the CI reference run drops 16 icon-sheet images and adds none, and ends with `Data safety: no change` and the Claude Code line.
- [ ] **Step 3:** When CI is green, the build-journal pebble waits for 7c (one pebble for the part).

---

## Part 7b — Android (branch `chore/1607-icon-removal-android` from updated `main`)

Run it in a worktree beside 7a: `git worktree add ../pbbls-removal-android -b chore/1607-icon-removal-android origin/main`, then copy `apps/android/secrets.properties` into it (gitignored; without it the APK crashes at launch).

### Task 7b.1: Remove the Android emitter and its outputs

**Files:**
- Modify: `packages/icons/scripts/lib/emit-android-glyphs.ts`, `packages/icons/scripts/lib/emit-android-glyphs.test.ts`, `packages/icons/scripts/build.ts`, `packages/icons/scripts/lib/names.ts`, `packages/icons/scripts/lib/names.test.ts`, `packages/icons/scripts/explore/rename.py`, `packages/icons/CLAUDE.md`
- Delete: `packages/icons/scripts/lib/emit-android.ts`, `packages/icons/scripts/lib/emit-android.test.ts`, `apps/android/app/src/main/res/drawable/ic_pbbls_*.xml` (1,855 files), `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsIcons.kt`

- [ ] **Step 1: Move the helper.** Cut `kdocLines` from `emit-android.ts` into `emit-android-glyphs.ts`, verbatim, with the constants it reads (`INDENT`, `BODY_PREFIX`, `LINE_WIDTH`). Delete the line `import { kdocLines } from "./emit-android"`, export `kdocLines` from `emit-android-glyphs.ts`, and move its `describe` block into `emit-android-glyphs.test.ts`.

- [ ] **Step 2: Run** `npm run test --workspace=@pbbls/icons` — PASS.

- [ ] **Step 3: Unregister the emitter.** In `scripts/build.ts`, delete `import { androidEmitter } from "./lib/emit-android"` and remove `androidEmitter, ` from `EMITTERS`. Delete `emit-android.ts` and `emit-android.test.ts`. In `names.ts`, delete `drawableName` (`grep -rn drawableName packages/icons/scripts` must show nothing else) and its test. `constantName` stays: `emit-android-glyphs.ts` uses it.

- [ ] **Step 4: Delete the old outputs.**

```bash
git ls-files 'apps/android/app/src/main/res/drawable/ic_pbbls_*.xml' | xargs git rm -q
git rm -q apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsIcons.kt
```

- [ ] **Step 5: Docs and tools.** In `packages/icons/CLAUDE.md`, delete the line `` - `apps/android/app/src/main/res/drawable/ic_pbbls_*.xml`, `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsIcons.kt` ``. In `rename.py`, replace the `PbblsIcons.kt` entry of `GENERATED` with:

```python
    "apps/android/app/src/main/res/raw/pbbls_glyphs.json",
    "apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsGlyph.kt",
    "apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsGlyphState.kt",
```

- [ ] **Step 6: Run** the package checks — PASS, with no file rewritten by `generate`.

### Task 7b.2: Move the app off the old enum and rename the size enum

**Files:**
- Rename: `core/designsystem/PbblsIconSize.kt` → `core/designsystem/PbblsGlyphSize.kt`; `src/test/.../core/designsystem/PbblsIconSizeTest.kt` → `PbblsGlyphSizeTest.kt` (paths under `apps/android/app/src/{main,test}/kotlin/app/pbbls/android/`)
- Modify: every Kotlin file that names `PbblsIconSize` (38 today)
- Delete: `apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/PbblsIconsScreenshots.kt`, its 16 references under `apps/android/app/src/screenshotTestDebug/reference/app/pbbls/android/PbblsIconsScreenshotsKt/`, `apps/android/app/src/test/kotlin/app/pbbls/android/core/designsystem/PbblsIconsTest.kt`

- [ ] **Step 1: The size file.** `git mv` it to `PbblsGlyphSize.kt`. Delete the `PbblsIconImage(icon: PbblsIcon, size: PbblsIconSize, …)` composable at its end, and the imports only it used (`LocalContentColor`, `Composable`, `Modifier`, `Color`, `LocalDensity`). In the KDoc, replace "Hand-written: the generator owns `PbblsIcons.kt` only." with "Hand-written: the generator owns `PbblsGlyph.kt` and `PbblsGlyphState.kt` only.", and replace "passes its `Dp` through the generated `PbblsIconImage(size =)` instead." with "passes its `Dp` to `PbblsGlyphImage`'s `size: Dp` overload instead."

- [ ] **Step 2: Rename the uses.**

```bash
cd apps/android/app/src
grep -rl 'PbblsIconSize' . | xargs sed -i '' 's/PbblsIconSize/PbblsGlyphSize/g'
git mv test/kotlin/app/pbbls/android/core/designsystem/PbblsIconSizeTest.kt test/kotlin/app/pbbls/android/core/designsystem/PbblsGlyphSizeTest.kt
```

In `PbblsGlyphSizeTest.kt`, rename the class to `PbblsGlyphSizeTest`.

- [ ] **Step 3: Delete the old tests.**

```bash
git rm -q screenshotTest/kotlin/app/pbbls/android/PbblsIconsScreenshots.kt test/kotlin/app/pbbls/android/core/designsystem/PbblsIconsTest.kt
git rm -r -q screenshotTestDebug/reference/app/pbbls/android/PbblsIconsScreenshotsKt
```

- [ ] **Step 4: Nothing names the old model.**

```bash
cd ../../..   # apps/android
grep -rnE '\bPbblsIcons?\b|PbblsIconImage|PbblsIconSize|ic_pbbls_' app/src --include='*.kt' --include='*.kts' --include='*.xml'
node ../../scripts/arkaik/android-shots-manifest.mjs --check
```

Expected: no grep output; the shots check passes (no mapped stem pointed at the icon sheet).

- [ ] **Step 5: Build and test** (Android checks above) — PASS. `./gradlew lint` must not newly report anything; if the lint baseline lists a file this part deleted or renamed, regenerate it and diff by issue id, never by line. Install on the Pixel 7 AVD (`./gradlew installDebug`), open Home, Path, People and You: every glyph still draws.

- [ ] **Step 6: Commit** Tasks 7b.1 and 7b.2 together: `chore(android): remove the icon drawables and their emitter`.

### Task 7b.3: Open the PR

- [ ] **Step 1:** If 7a merged first, `git merge origin/main`: in `scripts/build.ts` keep both removals (`EMITTERS` lists `reactEmitter(PACKAGE_ROOT), glyphsEmitter(PACKAGE_ROOT), iosGlyphsEmitter(PACKAGE_ROOT), androidGlyphsEmitter(PACKAGE_ROOT)` and neither old import), in `CLAUDE.md` delete both lines, in `rename.py` keep both replacements; `npm run generate` — nothing rewritten.
- [ ] **Step 2:** Run every check, push, `gh pr create --base main --title "chore(android): remove the icon drawables and their emitter" --label chore --label android --label ui --label no-lab-note --milestone "M66 · Brand icons"`. Body as 7a's. If the screenshot check is red only because the deleted preview's references are reported missing, that is not expected: read the triptych before touching anything (skill `android-screenshot-gate`).

---

## Part 7c — package and web (branch `chore/1607-icon-removal` from `main` after 7a and 7b merge)

No surface slug: the web changes, so Vercel builds it.

### Task 7c.1: Web stops naming the twin props

**Files:** Modify `apps/web/lib/icons/pbbls-icon.ts`.

- [ ] **Step 1:** Replace `import type { PbblsIconProps } from "@pbbls/icons"` with `import type { PbblsGlyphProps } from "@pbbls/icons"`, and the type at the end with:

```ts
/** A generated Pebbles glyph, for maps and props whose every value is one (spec D10). */
export type PbblsIconComponent = ComponentType<PbblsGlyphProps>
```

- [ ] **Step 2:** Web checks — PASS (`LandingPage`'s `FEATURE_ICONS` still type-checks: every generated component takes `PbblsGlyphProps`). Commit `chore(web): type icon maps by the glyph props`.

### Task 7c.2: Delete the twins and the code that reads them

**Files:**
- Modify: `packages/icons/scripts/lib/manifest.ts`, `emit.ts`, `lint.ts`, `emit-glyphs.ts`, `emit-react.ts`, `names.ts` and their tests (`manifest.test.ts`, `emit.test.ts`, `lint.test.ts`, `emit-react.test.ts`, `names.test.ts`), `packages/icons/icons.json`
- Delete: `packages/icons/src/*.active.svg` (839), `packages/icons/src/*.lit-{1,2,3}.svg` (66)
- Generated (by `generate`): `packages/icons/react/` loses every `*Active.tsx` and `*Lit{1,2,3}.tsx`; `types.ts` and `index.ts` lose `PbblsIconProps`

- [ ] **Step 1: Write the failing lint test.** In `lint.test.ts`, in the manifest `describe`, with the file's own `run`, `row` and `BASE` helpers:

```ts
it("refuses a source that is neither the base, its dotted drawing nor an override", () => {
  const r = run({ manifest: { icons: [row("home")] }, files: { "home.svg": BASE, "home.active.svg": BASE } })
  expect(r.errors).toContainEqual(
    expect.objectContaining({ file: "home.active.svg", rule: "manifest", message: '"active" is not a form: a source is <name>.svg, <name>.dotted.svg or <name>.<form>.svg' }),
  )
})
```

Run `npx vitest run scripts/lib/lint.test.ts` from `packages/icons` — FAIL (today the message speaks of variants).

- [ ] **Step 2: The manifest.** In `manifest.ts`: delete `Variant`, `VARIANTS`, `isVariant`, the `variants` field of `IconRow`, its destructuring, its `isStringArray` check and its place in the returned row. `EmittedRow` becomes `{ name: string; meaning: string; row: IconRow }`, and `emitted` becomes:

```ts
/** The rows the generator emits, in manifest order: every row that is not `exploring`. */
export function emitted(manifest: Manifest): EmittedRow[] {
  return manifest.icons.filter((row) => row.status !== "exploring").map((row) => ({ name: row.name, meaning: row.meaning, row }))
}
```

- [ ] **Step 3: The emitted icon.** In `emit.ts`, `EmittedIcon` loses `base` and `variant` (and the `Variant` import); `loadIcons` returns `{ name: row.name, meaning: row.meaning, paths }`. Fix the doc comments that say "or one of its twins". Then `grep -n 'paths' scripts/lib/emit-*.ts`: if no emitter reads `icon.paths` any more, delete the field and the source read in `loadIcons` too (the lint has already parsed every source).

- [ ] **Step 4: The lint.** In `lint.ts`:
  - `splitSourceName` returns `{ base: string; form: string | null }` (doc: `` `home.svg` → base `home`; `home.solid.svg` → base `home`, form `solid` ``); update its callers here and in `emit-glyphs.ts` (`split.variant` → `split.form`).
  - In `lintManifest`, delete the `for (const variant of row.variants)` loop; in the file loop, change the "not a … file" message to `"not a <name>.svg or <name>.<form>.svg file"` and replace the three lines after the override branch (the `VARIANTS` check and the two twin checks) with:

```ts
    errors.push({ file: f, rule: "manifest", message: `"${split.form}" is not a form: a source is <name>.svg, <name>.dotted.svg or <name>.<form>.svg` })
```

  - Today `isTwin` is true for every suffixed file (twins, dotted drawings, overrides), so it already means "not the base". Invert it: `lintSource(root, file, isBase, isOverride, result)` and `lintPath(path, index, file, isBase, isOverride, result)`, with `!isTwin` → `isBase` everywhere. The filled-path error becomes `if (path.filled && isBase) errors.push({ file, rule: "fill", message: \`${where} is filled in a base file; only an override carries a fill\` })` (a filled dotted drawing is still refused by `lintDotted`).
  - Two rules only ever fired on twins, because a base fill and a dotted fill are already errors and overrides were exempt: the "fills N square units, above 64: is the whole icon lit?" warning and the "N filled paths; a twin lights at most 3" error. Delete both, with `WARN_AREA` and `MAX_FILLED` (`grep -rn 'WARN_AREA\|MAX_FILLED' packages/icons` first: only `lint.ts` and its test should name them; delete those test cases too).
  - In `lint`, the loop becomes:

```ts
  for (const f of files) {
    const form = splitSourceName(f)?.form ?? null
    lintSource(root, f, form === null, form !== null && isOverrideForm(form), result)
    if (form === DOTTED_FORM) lintDotted(root, f, result)
  }
```

  Every other rule keeps its behaviour for base files, overrides and dotted drawings exactly as it is today; the existing tests for those three kinds must pass unchanged.

- [ ] **Step 5: The React emitter.** In `emit-react.ts`, delete `componentFile` and the twin branch of `reactFiles` (every icon now goes through `glyphComponentFile`), `PbblsIconProps` from `TYPES` and from `indexFile`'s type export, and any import left unused (`ParsedPath`). `reactFiles`'s doc: "Every file of `react/`: a component per glyph, the types, the states and the index." `PbblsGlyphProps`'s doc in `TYPES`: "A glyph's props: every SVG prop but `children`, plus `size`, `form` and `animate`."

- [ ] **Step 6: Names.** In `names.ts`, the header becomes "A manifest name is kebab-case (`check-circle`); the dashes are the only separators, so every spelling is a fold of the same words." Change the `pascalName` example to `` `check-circle` → `CheckCircle` ``. Splitting on `.` can stay (a dotted form name never reaches these helpers).

- [ ] **Step 7: The tests.** In `lint.test.ts`: `row` loses its `variants` parameter (`row(name, extra)`); `run`'s defaults become `{ icons: [row("home"), row("trash")] }` and `{ "home.svg": BASE, "trash.svg": BASE }`; `TWIN` goes. Delete the cases that only exist for twins: the lit-area warning, "four filled paths in a twin", "a listed variant without its file", and the twin half of "does not apply the twin fill limits to override files". The two fill-value cases ("a fill-rule other than evenodd", "a fill other than currentColor") still guard real rules: move them onto an override, the way the "roles and overrides" `describe` builds one (a `ring.svg` with a body and a `ring.solid.svg` carrying the bad fill), and assert with `toContain` on the messages. In `manifest.test.ts`, `emit.test.ts`, `emit-react.test.ts` and `names.test.ts`, delete the cases that build a twin, a `variants` list or a `*.active`/`*.lit-N` name, and drop `variants` from the fixtures that only carry it. Never delete a case that tests something else.

- [ ] **Step 8: The data.**

```bash
cd packages/icons
git ls-files 'src/*.active.svg' 'src/*.lit-1.svg' 'src/*.lit-2.svg' 'src/*.lit-3.svg' | xargs git rm -q
sed -i '' '/^      "variants": \[.*\],$/d' icons.json
grep -c '"variants"' icons.json     # 0
ls src/*.svg | wc -l                # 959
```

`icons.json` is formatting-sensitive: never rewrite it with a JSON dump.

- [ ] **Step 9: Run** the package checks — all PASS, and `lint` reports 0 errors (the 129 "is the whole icon lit?" warnings are gone with the twins). `npm run generate --workspace=@pbbls/icons`, then `ls packages/icons/react | grep -cE '(Active|Lit[123])\.tsx$'` prints 0. `git diff --stat -- apps/ios apps/android packages/icons/glyphs` is empty: the forms never read twins, so the glyph data, both native resources and the forms contract do not move. If any of them moved, stop and report the diff.

- [ ] **Step 10:** Web checks — PASS. Commit `chore(ui): remove the icon twins`.

### Task 7c.3: Fold the rows the forms review retired

**Files:** Modify `packages/icons/icons.json`. Delete `packages/icons/src/pebble-glyph-dotted.svg`, `pebble-glyph-dotted-open.svg`, `palette-1.svg`. Rename `palette-3.svg` → `palette.svg` (with any `palette-3.*.svg` override).

From `waves/2026-11-forms/verdict.md`: `pebble-glyph-dotted` and `pebble-glyph-dotted-open` are now the dotted forms of `pebble-glyph` and `pebble-glyph-open`; `palette-1` merges with `palette-3` into one `palette`, which keeps `palette-3`'s drawing (its steps and solid).

- [ ] **Step 1: Check the call sites first.**

```bash
grep -rnE 'PebbleGlyphDotted|pebbleGlyphDotted|PEBBLE_GLYPH_DOTTED|pebble-glyph-dotted|Palette[13]\b|palette[13]\b|PALETTE_[13]\b|palette-[13]\b' apps --include='*.swift' --include='*.kt' --include='*.ts' --include='*.tsx' --exclude-dir=node_modules \
  | grep -vE 'Theme/PbblsGlyph\.swift|designsystem/PbblsGlyph\.kt|res/raw/|Resources/pbbls-glyphs'
```

Expected: no output (true on 2026-11-11). If a site appears, move it to `PebbleGlyph` with the `DOTTED` form, or to `Palette`, in this task.

- [ ] **Step 2: The dotted rows.** In `icons.json`, give `pebble-glyph` the `replaces` and `migrated` of `pebble-glyph-dotted` (today `migrated: ["ios", "android", "web"]`, `replaces: { "ios": ["scribble", "pencil.and.outline"], "android": ["ic_scribble"], "web": ["Fingerprint", "PenTool"] }`), unioned with its own, so `lint:replaces` keeps policing those symbols. Do the same from `pebble-glyph-dotted-open` to `pebble-glyph-open` (both lists are empty today). Delete the two `-dotted` rows as whole text blocks (from their `{` to their `},`), and `git rm` their two sources.

- [ ] **Step 3: The palette.** `python3 -I packages/icons/scripts/explore/rename.py . <file>` with `{"palette-3": "palette"}` (write the JSON to the scratchpad, not the repo). Then delete the `palette-1` row as a text block, keep `palette`'s `tags` and add `palette-1`'s keywords to them if it had any, and `git rm packages/icons/src/palette-1.svg`.

- [ ] **Step 4: Run** the package checks — PASS; `npm run generate`, then the web checks — PASS. iOS and Android pick up the renamed `palette` case through their generated enums; nothing hand-written names it (Step 1). Commit `chore(ui): fold the dotted pebble-glyph rows and merge the palettes`.

### Task 7c.4: Tools and docs

**Files:** Modify `packages/icons/scripts/explore/rename.py`, `packages/icons/scripts/explore/extract.py`, `packages/icons/DNA.md`, `packages/icons/CLAUDE.md`. Delete `packages/icons/scripts/explore/roles.py`.

- [ ] **Step 1: `rename.py`** moves every source of a glyph, whatever its suffix. Replace `SUFFIXES = (...)` and the loop that reads it with a glob of `src/<old>.svg` plus `src/<old>.*.svg`, and update the docstring ("Moves src/<old>.svg and its dotted drawing and overrides…; rewrites hand-written call sites…").

- [ ] **Step 2: `extract.py`** writes the base drawing only: delete the `hasActive` and `lit` branches and the twin counts; the summary line becomes `wrote N drawings to src/`. Update its docstring.

- [ ] **Step 3:** `git rm packages/icons/scripts/explore/roles.py` (and its test, if any).

- [ ] **Step 4: `DNA.md`.** Replace the twin paragraph (the one beginning "A twin (`<name>.active`) is the base drawing…") with: "A source is never filled. Forms that fill (solid, parts, progress, the filled mark) are worked out by the generator from the roles (Roles and forms); an override (`<name>.<form>.svg`) is the only drawn file that carries a fill, and it keeps the DNA." In the rules table, the Fill row's first cell becomes "`fill="currentColor"` outside an override; any other fill value; `fill-rule` on an unfilled path, or any value but `evenodd`; `stroke="none"` on an unfilled path", its second cell drops "only a lit stone is solid, and"; the Manifest row drops every variant and twin clause and gains "a source other than `<name>.svg`, `<name>.dotted.svg` or `<name>.<form>.svg`".

- [ ] **Step 5: `CLAUDE.md`.** The opening sentence becomes "`src/<name>.svg` (with an optional `<name>.dotted.svg` and `<name>.<form>.svg` overrides) plus the manifest `icons.json` are the single source of truth…". In "Adding or redrawing an icon", step 2 drops "a twin as `src/<name>.active.svg` or `src/<name>.lit-N.svg`" and step 3 drops `variants`. In the `lint:replaces` row and "When `Icons` is red", replace "migrate the call site to `PbblsIcon`" with "migrate the call site to the glyph (`PbblsGlyph` on iOS and Android, the `@pbbls/icons` component on web)". In "Exploration tools", `extract.py` "writes `src/<name>.svg` from the wave's `library.svg`".

- [ ] **Step 6:** `grep -rnE 'twin|variants|lit-[123]|\.active\b|PbblsIcon\b|activeTwin' packages/icons --include='*.md' --include='*.ts' --include='*.py' --exclude-dir=node_modules --exclude-dir=waves` — only historical mentions remain (a test name that says a twin is refused is fine). Package checks — PASS. Commit `docs(ui): describe icons without twins`.

### Task 7c.5: Open the PR, then close the part

- [ ] **Step 1:** Run every check (package, web). Push, `gh pr create --base main --title "chore(ui): remove the icon twins" --label chore --label web --label ui --label no-lab-note --milestone "M66 · Brand icons"`. Body starts `Resolves #1607` (the last part), lists the counts (905 twin files, 4 rows folded, 1 lint rule added), the retired tools, and ends with `Data safety: no change` and the Claude Code line.
- [ ] **Step 2:** After merge: fill "Lessons learned" below, update the project memory, drop one build-journal pebble for part 7 (`--collection "Brand & design"`, `--valence neutral`).

## Lessons learned

(Filled in after the PRs merge.)
