# iOS icon migration — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 49 SF Symbols that carry meaning in the iOS app with `PbblsIcon` drawings, keep the 16 chrome and shape symbols, write the 19 symbols with no drawing into a wave brief, land the migration lint that keeps the replaced names out, and retire the three `PebblesIcon` font tokens. Spec: `docs/superpowers/specs/2026-10-30-ios-icon-migration-design.md` (D1–D10). Issue 1314, milestone M66.

**Architecture:** `icons.json` rows gain `migrated` (the surfaces whose code no longer names the row's `replaces`); `scripts/lint-replaces.ts` greps each migrated surface's tree for the bare literal and fails on a hit. On iOS, `PbblsIconSize` names the five sizes an icon draws at and `PbblsIconImage(_:_:)` takes one; every helper that used to carry a symbol string (`SurfaceTile`, `DataTile`, `PebblesMenuItem`, `HomeShortcuts.tile`, `Achievement.familySymbol`, `LogTimeline`, `GlyphTabBar`, `AppTab`) carries a `PbblsIcon` instead. Screens migrate area by area; the manifest flips `migrated: ["ios"]` in the same commit that deletes the last literal, so the lint goes live with the code it polices.

**Tech stack:** tsx + TypeScript strict + Vitest 5 in `packages/icons`; SwiftUI (iOS 17+), Swift Testing, swiftlint, the CI-recorded reference images in `apps/ios/PebblesTests/References/`.

**Stack (gh stack, seven parts = seven PRs, bottom first):**

| Part | Branch | PR title | Tasks |
|---|---|---|---|
| 1 | `chore/1314-replaces-lint` | `chore(ui): add the migration lint, the migrated marker and the iOS icon sizes` | 1–7 |
| 2 | `chore/1314-tab-bar` | `chore(ios): draw the tab bar and the toolbar buttons with pebbles icons` | 8–10 |
| 3 | `chore/1314-home-record` | `chore(ios): draw home, record and complete with pebbles icons` | 11–15 |
| 4 | `chore/1314-path` | `chore(ios): draw the path card, its menu and the pickers with pebbles icons` | 16–20 |
| 5 | `chore/1314-profile` | `chore(ios): draw profile, achievements, settings and the lists with pebbles icons` | 21–27 |
| 6 | `chore/1314-glyph-connections-lab` | `chore(ios): draw glyphs, connections and the lab with pebbles icons, and arm the lint` | 28–34 |
| 7 | `chore/1314-retire-tokens` | `chore(ios): retire the PebblesIcon font tokens` (`Resolves #1314`) | 35–37 |

Labels: `chore`, `ios`, plus `ui` on Parts 1 and 7 (they touch the package and the theme); `no-lab-note` on every part (the change is a visual re-skin with no new behaviour; the maintainer can flip that on the user-facing parts). Milestone: `M66 · Brand icons`. PR bodies for Parts 1–6 say `Part N of #1314`, never `Resolves`; Part 7 says `Resolves #1314`. Never write a Kritik finding id in a body.

**Before Part 1:** work in the `.worktrees/icons-package` worktree (it has the gitignored config). Branch from an updated `main`: `git fetch origin && git switch -c chore/1314-replaces-lint origin/main`. iOS builds use the worktree's own simulator and `-derivedDataPath` (`project_ios_parallel_simulators`); device installs follow `project_ios_device_deploy`.

**The iOS gate, run at the end of every part** (from `apps/ios`):

```bash
xcodegen generate
swiftlint 2>&1 | tail -5                   # zero errors; warnings are listed, not blocking
xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17 icons' \
  -derivedDataPath /private/tmp/claude-503/-Users-alexis-code-pbbls/ccea1ead-41c0-4786-ab6d-eb71e8b3b19f/scratchpad/icons-dd \
  -skip-testing:PebblesTests/References 2>&1 | grep -E "Test Suite|passed|failed|error:" | tail -20
```

Expected: `** TEST SUCCEEDED **` with zero `error:` lines. The `References` suite is skipped locally on purpose: 27 of them fail on an untouched `main` on this Mac (`project_ios_local_reference_baseline`). References are recorded on CI only (D9), see **Re-baselining a part** below.

**Re-baselining a part (D9):** after the part's last code commit is pushed and its iOS run is red only on `References`, add the label alone: `gh pr edit <n> --add-label rebaseline-ios-screenshots`. The bot pushes `test(ios): re-baseline reference images` onto the branch. Then, before any further stack command: `git fetch origin && git branch -f <branch> origin/<branch>` (the remote is a strict descendant), `gh stack rebase --no-trunk` from the part above, confirm `git diff --stat origin/<branch> <branch>` is empty for the re-baselined branch, and `gh stack push`. Approve any run the bot's commit left at `action_required` (`gh api -X POST repos/alexisbohns/pbbls/actions/runs/<id>/approve`). Part 3 of #1319 lost two PNGs by skipping the fast-forward; do not.

**Case names.** The generated `PbblsIcon` turns `pebble-round.lit-1` into `.pebbleRoundLit1`, `stack.lit-3` into `.stackLit3`, `home.active` into `.homeActive`, `check-circle-dot` into `.checkCircleDot`. The cases this plan uses, all present in `apps/ios/Pebbles/Theme/PbblsIcon.swift` today: `home path people collection person pebbleRound pebbleRoundLit1 pebbleFlat stack stackLit3 checkmarkDot checkCircle checkCircleDot remove plusCircle camera cameraFocus drawDotted slider trash pebbleDottedGlyph karmaDot sparkle sparkles calendarDate chartDoubleGrow pebbleGlowMd pebbleGlowLg heart hourglassEnd`.

---

## Part 1 — manifest, lint, sizes, wave brief (`chore/1314-replaces-lint`)

### Task 1: `migrated` on every manifest row

**Files:**
- Modify: `packages/icons/scripts/lib/manifest.ts`
- Modify: `packages/icons/scripts/lib/manifest.test.ts`
- Modify: every `scripts/lib/*.test.ts` whose `row()` helper builds a manifest row
- Modify: `packages/icons/icons.json` (121 rows)

- [ ] **Step 1: Write the failing tests** in `manifest.test.ts`, inside `describe("manifest")`:

```ts
  it("reads migrated and refuses an unknown or repeated surface", () => {
    const ok = parseManifest({ icons: [row("home", { migrated: ["ios"] })] })
    expect(ok.icons[0]?.migrated).toEqual(["ios"])
    expect(() => parseManifest({ icons: [{ ...row("home"), migrated: ["mac"] }] })).toThrow("icons[0] (home).migrated: mac")
    expect(() => parseManifest({ icons: [{ ...row("home"), migrated: ["ios", "ios"] }] })).toThrow("icons[0] (home).migrated: ios twice")
    expect(() => parseManifest({ icons: [{ ...row("home"), migrated: "ios" }] })).toThrow("icons[0] (home).migrated is not a string array")
  })

  it("refuses shipped while a surface is not migrated", () => {
    const all = { status: "shipped", migrated: ["ios", "android", "web"] }
    expect(parseManifest({ icons: [row("home", all)] }).icons).toHaveLength(1)
    expect(() => parseManifest({ icons: [row("home", { status: "shipped", migrated: ["ios"] })] }))
      .toThrow("icons[0] (home): shipped, but migrated lacks android, web")
  })
```

The `row()` helper at the top of the file gains `migrated: [],` after `status: "candidate",`.

- [ ] **Step 2: Run them to see them fail**

Run: `npm run test --workspace=@pbbls/icons -- manifest`
Expected: the two new tests FAIL (`migrated` is `undefined`; no throw).

- [ ] **Step 3: Implement** in `manifest.ts`. Add to `IconRow`, after `status: string`:

```ts
  /** The surfaces whose code no longer names this row's `replaces` (the migration lint reads it). */
  migrated: Surface[]
```

In `rowFrom`, destructure `migrated` too and, after the `status` check, add:

```ts
  if (!isStringArray(migrated)) throw new Error(`${where} (${name}).migrated is not a string array`)
  const migratedSurfaces: Surface[] = []
  for (const surface of migrated) {
    if (!isSurface(surface)) throw new Error(`${where} (${name}).migrated: ${surface} is not a surface`)
    if (migratedSurfaces.includes(surface)) throw new Error(`${where} (${name}).migrated: ${surface} twice`)
    migratedSurfaces.push(surface)
  }
  if (status === "shipped") {
    const missing = SURFACES.filter((s) => !migratedSurfaces.includes(s))
    if (missing.length > 0) throw new Error(`${where} (${name}): shipped, but migrated lacks ${missing.join(", ")}`)
  }
```

with, beside `isStringArray` at the top of the file, `const isSurface = (v: string): v is Surface => (SURFACES as readonly string[]).includes(v)` (the one widening the file already uses for `isVariant`), and return `{ name, meaning, variants, wave, status, migrated: migratedSurfaces, replaces: lists }`.

- [ ] **Step 4: Fix the other test helpers.** `grep -ln 'status: "candidate"' packages/icons/scripts/lib/*.test.ts` lists every file with a `row()` helper (`lint.test.ts`, `emit*.test.ts`, …). Add `migrated: [],` after `status` in each. Run `npm run test --workspace=@pbbls/icons`: everything green.

- [ ] **Step 5: Add the field to the 121 rows.** The file is 2-space JSON, so a round trip is byte-stable except for the added lines. From `packages/icons`:

```bash
node -e '
const fs = require("node:fs");
const m = JSON.parse(fs.readFileSync("icons.json", "utf8"));
m.icons = m.icons.map(({ name, meaning, variants, wave, status, replaces }) =>
  ({ name, meaning, variants, wave, status, migrated: [], replaces }));
fs.writeFileSync("icons.json", JSON.stringify(m, null, 2) + "\n");
'
git diff --stat icons.json
```

Expected: `1 file changed, 121 insertions(+)`. Anything else means the file was not 2-space JSON; revert and insert the line as text after each `"status"` line instead.

- [ ] **Step 6: Lint, typecheck, test, check**

Run: `npm run lint --workspace=@pbbls/icons && npm run build --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons`
Expected: all green; `check` says the generated files are fresh (`migrated` does not reach any emitter).

- [ ] **Step 7: Commit**

```bash
git add packages/icons/icons.json packages/icons/scripts/lib
git commit -m "chore(ui): mark which surfaces have migrated off each icon's replaces"
```

### Task 2: `replaces.ios` per the mapping (D8c)

**Files:**
- Modify: `packages/icons/icons.json` (28 rows)
- Modify: `packages/icons/scripts/lib/manifest.test.ts`

- [ ] **Step 1: Write the failing test** at the bottom of `manifest.test.ts`. It reads the real manifest:

```ts
import { fileURLToPath } from "node:url"

describe("icons.json replaces", () => {
  const manifest = loadManifest(fileURLToPath(new URL("../..", import.meta.url)))

  it("lists bare symbol names, each on one row", () => {
    const owner = new Map<string, string>()
    for (const row of manifest.icons) {
      for (const surface of ["ios", "android", "web"] as const) {
        for (const name of row.replaces[surface]) {
          expect(name, `${row.name}.replaces.${surface}`).toMatch(/^[A-Za-z0-9._]+$/)
          const key = `${surface}:${name}`
          expect(owner.get(key), `${name} listed by ${owner.get(key)} and ${row.name}`).toBeUndefined()
          owner.set(key, row.name)
        }
      }
    }
  })
})
```

- [ ] **Step 2: Run it**: `npm run test --workspace=@pbbls/icons -- manifest`. Expected: PASS today (nothing is double-listed yet); it guards Step 3.

- [ ] **Step 3: Edit the rows.** Set `replaces.ios` exactly as below (other surfaces untouched). Rows not listed keep `[]`.

| row | `replaces.ios` |
|---|---|
| `home` | `["house"]` (unchanged) |
| `path` | `["square.stack", "line.3.horizontal.decrease"]` |
| `people` | `["person.2", "person.3", "person.2.badge.plus", "person.3.fill", "bubble.left.and.bubble.right.fill"]` (drops `person.2.fill`, D2) |
| `collection` | `["circle.hexagongrid", "square.stack.3d.up"]` |
| `person` | `["person.crop.circle", "person.circle", "person.fill"]` |
| `pebble-round` | `["fossil.shell", "drop.fill", "circle.dashed", "circle.inset.filled", "square"]` |
| `pebble-flat` | `["clock"]` |
| `stack` | `["circle.grid.3x3"]` |
| `checkmark-dot` | `["checkmark"]` |
| `check-circle` | `["checkmark.circle.fill", "checkmark.circle", "checkmark.square"]` |
| `check-circle-dot` | `["checkmark.seal", "checkmark.seal.fill"]` |
| `remove` | `["xmark.circle.fill", "person.badge.minus"]` |
| `plus-circle` | `["plus", "plus.circle"]` |
| `camera` | `["photo.badge.plus", "camera"]` |
| `camera-focus` | `["photo", "photo.on.rectangle.angled"]` |
| `draw-dotted` | `["pencil"]` |
| `slider` | `["gear"]` (unchanged) |
| `trash` | `["trash"]` (unchanged) |
| `pebble-dotted-glyph` | `["scribble", "pencil.and.outline"]` |
| `karma-dot` | `["sparkle", "sparkles"]` |
| `sparkle` | `["lightbulb.max"]` |
| `sparkles` | `["square.grid.3x3"]` |
| `calendar-date` | `["calendar"]` (unchanged) |
| `chart-double-grow` | `["chart.bar.fill"]` |
| `pebble-glow-md` | `["trophy"]` |
| `pebble-glow-lg` | `[]` (used by site only: the celebration hero; `trophy` has one primary row) |
| `heart` | `["heart", "waveform.path.ecg"]` |
| `hourglass-end` | `["circle.grid.2x1"]` |

That is 49 names over 27 rows (`fossil.shell.fill` and `alternatingcurrent` become shapes, D4, and are listed nowhere). Count them: `node -e 'const m=require("./packages/icons/icons.json");console.log(m.icons.flatMap(r=>r.replaces.ios).length)'` prints `49`.

- [ ] **Step 4: Test and commit**

Run: `npm run lint --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons`
Expected: green.

```bash
git add packages/icons/icons.json packages/icons/scripts/lib/manifest.test.ts
git commit -m "chore(ui): record which sf symbol each pebbles icon replaces on ios"
```

### Task 3: The replaces lint

**Files:**
- Create: `packages/icons/scripts/lib/replaces.ts`
- Create: `packages/icons/scripts/lib/replaces.test.ts`
- Create: `packages/icons/scripts/lint-replaces.ts`
- Modify: `packages/icons/package.json` (one script)

- [ ] **Step 1: Write the failing tests** in `replaces.test.ts`. The fixture is a repo root in a temp dir:

```ts
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { dirname, join } from "node:path"
import { describe, expect, it } from "vitest"
import { parseManifest } from "./manifest"
import { lintReplaces, type Hit } from "./replaces"

const row = (name: string, extra: Record<string, unknown> = {}) => ({
  name, meaning: `${name}.`, variants: [], wave: "2026-10-foundation", status: "candidate",
  migrated: [], replaces: { ios: [], android: [], web: [] }, ...extra,
})

function repo(files: Record<string, string>): string {
  const root = mkdtempSync(join(tmpdir(), "icons-replaces-"))
  for (const [path, text] of Object.entries(files)) {
    mkdirSync(dirname(join(root, path)), { recursive: true })
    writeFileSync(join(root, path), text)
  }
  return root
}

const where = (hits: Hit[]) => hits.map((h) => `${h.path}:${h.line}`).sort()

describe("lintReplaces · ios", () => {
  const manifest = parseManifest({ icons: [
    row("trash", { migrated: ["ios"], replaces: { ios: ["trash", "lock.fill"], android: [], web: [] } }),
    row("home", { migrated: [], replaces: { ios: ["house"], android: [], web: [] } }),
  ] })

  it("hits every bare literal form and skips the generated file and unmigrated rows", () => {
    const root = repo({
      "apps/ios/Pebbles/A.swift": 'Image(systemName: "trash")\n',
      "apps/ios/Pebbles/B.swift": 'let x = done ? "trash" : "circle"\n',
      "apps/ios/Pebbles/C.swift": 'func f() -> String {\n  return "lock.fill"\n}\n',
      "apps/ios/Pebbles/D.swift": 'switch k {\ncase "x": "trash"\n}\n',
      "apps/ios/Pebbles/E.swift": '// the old "trash" symbol\n',
      "apps/ios/Pebbles/F.swift": 'Image(systemName: "house")\nImage(systemName: "trash.fill")\n',
      "apps/ios/Pebbles/Theme/PbblsIcon.swift": 'case trash = "trash"\n',
      "apps/ios/PebblesTests/T.swift": 'let s = "trash"\n',
    })
    expect(where(lintReplaces(root, manifest))).toEqual([
      "apps/ios/Pebbles/A.swift:1", "apps/ios/Pebbles/B.swift:1", "apps/ios/Pebbles/C.swift:2",
      "apps/ios/Pebbles/D.swift:2", "apps/ios/Pebbles/E.swift:1",
    ])
  })

  it("is clean on a clean tree and reports the counts", () => {
    const root = repo({ "apps/ios/Pebbles/A.swift": 'Image(systemName: "house")\n' })
    expect(lintReplaces(root, manifest)).toEqual([])
  })
})

describe("lintReplaces · android and web", () => {
  const manifest = parseManifest({ icons: [
    row("trash", { migrated: ["android", "web"], replaces: { ios: [], android: ["ic_trash"], web: ["Trash2"] } }),
  ] })

  it("matches R.drawable.<name> and a lucide import outside components/ui", () => {
    const root = repo({
      "apps/android/app/src/main/kotlin/X.kt": "val i = R.drawable.ic_trash\n",
      "apps/android/app/src/main/res/layout/x.xml": '<ImageView android:src="@drawable/ic_trash"/>\n',
      "apps/web/app/x.tsx": 'import { Trash2, Users } from "lucide-react"\n',
      "apps/web/components/ui/button.tsx": 'import { Trash2 } from "lucide-react"\n',
      "apps/admin/app/y.tsx": 'import {\n  Trash2,\n} from "lucide-react"\n',
    })
    expect(where(lintReplaces(root, manifest))).toEqual([
      "apps/admin/app/y.tsx:2", "apps/android/app/src/main/kotlin/X.kt:1",
      "apps/android/app/src/main/res/layout/x.xml:1", "apps/web/app/x.tsx:1",
    ])
  })
})
```

- [ ] **Step 2: Run them**: `npm run test --workspace=@pbbls/icons -- replaces`. Expected: FAIL, `./replaces` not found.

- [ ] **Step 3: Implement** `scripts/lib/replaces.ts`:

```ts
import { readdirSync, readFileSync, statSync } from "node:fs"
import { join, relative, sep } from "node:path"
import { SURFACES, type Manifest, type Surface } from "./manifest"

/**
 * The migration lint (iOS spec D8): for every row and every surface in its
 * `migrated`, the row's `replaces.<surface>` names must be gone from that
 * surface's tree. A hit is a bare literal, whatever surrounds it: a ternary, a
 * `return`, a `case`, a comment. Literals only, no parsing, so a false hit is
 * fixed by renaming the string (R5), never by weakening the pattern.
 */

export type Hit = { surface: Surface; icon: string; name: string; path: string; line: number; text: string }

type Tree = {
  roots: string[]
  extensions: string[]
  /** Repo-relative paths (files or directories) the lint never reads. */
  skip: string[]
  /** The regex a listed name becomes; `escaped` is regex-safe. */
  pattern: (escaped: string) => RegExp
}

const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")

/** Every surface's tree and literal form. Android and web keep the package spec's D10 forms. */
export const TREES: Record<Surface, Tree> = {
  ios: {
    roots: ["apps/ios/Pebbles"],
    extensions: [".swift"],
    skip: ["apps/ios/Pebbles/Theme/PbblsIcon.swift"],
    pattern: (n) => new RegExp(`"${n}"`),
  },
  android: {
    roots: ["apps/android/app/src/main"],
    extensions: [".kt", ".xml"],
    skip: [],
    pattern: (n) => new RegExp(`R\\.drawable\\.${n}\\b|@drawable/${n}\\b`),
  },
  web: {
    roots: ["apps/web", "apps/admin"],
    extensions: [".ts", ".tsx"],
    skip: ["apps/web/components/ui", "apps/admin/components/ui", "apps/web/node_modules", "apps/admin/node_modules", "apps/web/.next", "apps/admin/.next"],
    // A lucide import is `import { A, B } from "lucide-react"`, possibly over
    // several lines; the file is checked for the import first, then each line
    // for the bare identifier.
    pattern: (n) => new RegExp(`\\b${n}\\b`),
  },
}

function walk(dir: string, extensions: string[], skip: Set<string>, root: string, out: string[]): void {
  for (const entry of readdirSync(dir).sort()) {
    const path = join(dir, entry)
    if (skip.has(relative(root, path).split(sep).join("/"))) continue
    if (statSync(path).isDirectory()) walk(path, extensions, skip, root, out)
    else if (extensions.some((e) => entry.endsWith(e))) out.push(path)
  }
}

function files(root: string, tree: Tree): string[] {
  const out: string[] = []
  const skip = new Set(tree.skip)
  for (const r of tree.roots) {
    const dir = join(root, r)
    try { statSync(dir) } catch { continue }
    walk(dir, tree.extensions, skip, root, out)
  }
  return out
}

/** Every hit in `root` (the repository), in file order then line order. */
export function lintReplaces(root: string, manifest: Manifest): Hit[] {
  const hits: Hit[] = []
  for (const surface of SURFACES) {
    const names = manifest.icons.flatMap((row) =>
      row.migrated.includes(surface) ? row.replaces[surface].map((name) => ({ icon: row.name, name })) : [])
    if (names.length === 0) continue
    const tree = TREES[surface]
    const checks = names.map((n) => ({ ...n, re: tree.pattern(escape(n.name)) }))
    for (const path of files(root, tree)) {
      const text = readFileSync(path, "utf8")
      if (surface === "web" && !/from\s+["']lucide-react["']/.test(text)) continue
      const lines = text.split("\n")
      lines.forEach((line, i) => {
        for (const c of checks) {
          if (c.re.test(line)) {
            hits.push({ surface, icon: c.icon, name: c.name, path: relative(root, path).split(sep).join("/"), line: i + 1, text: line.trim() })
          }
        }
      })
    }
  }
  return hits
}

/** The rows and names the lint looked for, for the summary line. */
export function coverage(manifest: Manifest): { rows: number; names: number } {
  let rows = 0
  let names = 0
  for (const row of manifest.icons) {
    const n = row.migrated.flatMap((s) => row.replaces[s]).length
    if (n > 0) rows += 1
    names += n
  }
  return { rows, names }
}
```

The web check is coarse on purpose (an identifier anywhere in a file that imports lucide); `components/ui/` is skipped because shadcn primitives import lucide by design (package spec D10). Narrowing it is web's job in #1313.

- [ ] **Step 4: Run the tests**: `npm run test --workspace=@pbbls/icons -- replaces`. Expected: PASS (3 tests).

- [ ] **Step 5: The CLI** `scripts/lint-replaces.ts`:

```ts
/**
 * `npm run lint:replaces --workspace=@pbbls/icons`: fails when a migrated
 * surface still names a symbol a pebbles icon replaces. Fix the call site (or,
 * for a string that is not a symbol, rename the string); never the pattern.
 */
import { fileURLToPath } from "node:url"
import { loadManifest } from "./lib/manifest"
import { coverage, lintReplaces } from "./lib/replaces"

const REPO_ROOT = fileURLToPath(new URL("../../..", import.meta.url))
const PACKAGE_ROOT = fileURLToPath(new URL("..", import.meta.url))

const manifest = loadManifest(PACKAGE_ROOT)
const hits = lintReplaces(REPO_ROOT, manifest)
for (const h of hits) console.error(`${h.path}:${h.line}  ${h.surface} ${h.name} → ${h.icon}  ${h.text}`)
const { rows, names } = coverage(manifest)
console.log(`replaces: ${rows} rows, ${names} names, ${hits.length} hits`)
process.exitCode = hits.length > 0 ? 1 : 0
```

In `package.json` scripts, after `"check"`: `"lint:replaces": "tsx scripts/lint-replaces.ts",`.

- [ ] **Step 6: Run it on the repo**

Run: `npm run lint:replaces --workspace=@pbbls/icons && npm run build --workspace=@pbbls/icons`
Expected: `replaces: 0 rows, 0 names, 0 hits` (no row is migrated yet), exit 0; `tsc` clean.

- [ ] **Step 7: Commit**

```bash
git add packages/icons/scripts packages/icons/package.json
git commit -m "chore(ui): lint migrated surfaces for the symbols their icons replace"
```

### Task 4: CI step and package docs

**Files:**
- Modify: `.github/workflows/icons.yml`
- Modify: `packages/icons/CLAUDE.md`

- [ ] **Step 1: The fifth step**, after `Generated outputs are fresh`:

```yaml
      - name: Migrated surfaces name no replaced symbol
        if: '!cancelled()'
        run: npm run lint:replaces --workspace=@pbbls/icons
```

Change the job's `name:` to `lint · typecheck · vitest · fresh · replaces`. (The check is not required in branch protection yet; proposing it is a follow-up the maintainer owns.)

- [ ] **Step 2: `CLAUDE.md`.** Commands table, a row after `check`:

`| \`npm run lint:replaces --workspace=@pbbls/icons\` | fails when a surface listed in a row's \`migrated\` still names one of that row's \`replaces\` (iOS: the bare quoted literal anywhere under \`apps/ios/Pebbles/\`) |`

Step 3 of "Adding or redrawing an icon" becomes:

`3. Add (or edit) its row in \`icons.json\`: \`name\`, one-sentence \`meaning\`, \`variants\`, \`wave\`, \`status\` (\`exploring\` is linted but not emitted; \`candidate\` is emitted everywhere; \`shipped\` means every surface is in \`migrated\`, and the validator refuses it otherwise), \`migrated\` (the surfaces whose code no longer names the row's symbols; a surface is added in the same commit that deletes its last literal, so \`lint:replaces\` goes live with the code it polices) and \`replaces\` per surface (from the code, never from memory; one primary row per symbol, the per-site choice lives in the surface's migration spec).`

"When `Icons` is red" gains one sentence: `A red \`replaces\` step names a file and line that still draws a replaced symbol: migrate the call site to \`PbblsIcon\`, or rename the string if it is not a symbol at all; never widen the pattern.`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/icons.yml packages/icons/CLAUDE.md
git commit -m "chore(ui): run the replaces lint in ci and document the migrated marker"
```

### Task 5: `PbblsIconSize` and the sized initializer

**Files:**
- Modify: `apps/ios/Pebbles/Theme/Icon+Pebbles.swift`
- Create: `apps/ios/PebblesTests/Theme/PbblsIconSizeTests.swift`
- Modify: `apps/ios/Pebbles/Theme/Debug/IconLabView.swift`

- [ ] **Step 1: Write the failing test**

```swift
import SwiftUI
import Testing
@testable import Pebbles

@Suite("PbblsIconSize")
struct PbblsIconSizeTests {
    @Test("the five sizes, in order, from the 16pt floor (spec D6)")
    func rawValues() {
        #expect(PbblsIconSize.inline.rawValue == 16)
        #expect(PbblsIconSize.row.rawValue == 20)
        #expect(PbblsIconSize.toolbar.rawValue == 22)
        #expect(PbblsIconSize.tile.rawValue == 28)
        #expect(PbblsIconSize.hero.rawValue == 48)
        #expect(PbblsIconSize.allCases.map(\.rawValue) == [16, 20, 22, 28, 48])
        #expect(PbblsIconSize.allCases.allSatisfy { $0.rawValue >= 16 })
    }

    @Test("the sized initializer builds")
    @MainActor func sizedInitializer() {
        _ = PbblsIconImage(.home, .tile)
        _ = PbblsIconImage(.home, .inline, relativeTo: .caption2, style: AnyShapeStyle(Color.pebbles.primary), label: "Home")
    }
}
```

- [ ] **Step 2: Run it** (the iOS gate above, or `-only-testing:PebblesTests/PbblsIconSizeTests`). Expected: compile error, `PbblsIconSize` undefined.

- [ ] **Step 3: Implement.** Append to `Icon+Pebbles.swift` (the `PebblesIcon` enum stays until Part 7):

```swift
/// The sizes a pebbles icon draws at (spec D6). A 24-unit drawing with a 1.75
/// stroke draws a 1.1pt line at 16, the floor; nothing is drawn smaller except
/// `CollectionMark`'s 14pt band mark, noted there.
///
/// - `inline`: beside text (karma amounts, "taken from your photo"), relative to that text's style.
/// - `row`: a list row's leading icon, a segmented pill, a menu item.
/// - `toolbar`: toolbar and chrome buttons inside their 44×44 hit frame.
/// - `tile`: `SurfaceTile`, `DataTile`, the Home shortcuts, card leading icons.
/// - `hero`: `ContentUnavailableView` images, the achievement celebration, the invalid-invite page.
enum PbblsIconSize: CGFloat, CaseIterable {
    case inline = 16
    case row = 20
    case toolbar = 22
    case tile = 28
    case hero = 48
}

extension PbblsIconImage {
    /// `PbblsIconImage(.home, .tile)`: the generated initializer at a named size.
    init(_ icon: PbblsIcon, _ size: PbblsIconSize, relativeTo textStyle: Font.TextStyle = .body,
         style: AnyShapeStyle? = nil, label: LocalizedStringResource? = nil) {
        self.init(icon, size: size.rawValue, relativeTo: textStyle, style: style, label: label)
    }
}
```

- [ ] **Step 4: Run the test**: PASS.

- [ ] **Step 5: The lab's detents.** In `IconLabView.swift`, under the `HStack` holding the `Slider` (inside the `VStack(spacing: Spacing.xs)` of the bottom inset), add:

```swift
                    HStack(spacing: Spacing.xs) {
                        ForEach(PbblsIconSize.allCases, id: \.rawValue) { size in
                            Button {
                                side = size.rawValue
                            } label: {
                                Text(verbatim: "\(size)")
                                    .font(.caption2.monospaced())
                                    .padding(.horizontal, Spacing.xs)
                                    .padding(.vertical, 2)
                                    .background(side == size.rawValue ? Color.pebbles.primaryContainer : .clear,
                                                in: Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
```

`"\(size)"` prints the case name (`inline`, `row`, …). Build the Debug app on the phone and tap each detent: the grid redraws at that size.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Theme/Icon+Pebbles.swift apps/ios/PebblesTests/Theme/PbblsIconSizeTests.swift apps/ios/Pebbles/Theme/Debug/IconLabView.swift
git commit -m "chore(ios): name the five sizes a pebbles icon draws at"
```

### Task 6: The wave brief and the one string collision

**Files:**
- Create: `packages/icons/waves/2026-11-chrome/brief.md`
- Modify: `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineLabView.swift:157`

- [ ] **Step 1: The brief.** Figure-only, under fifty lines, the exploration format (`feedback_design_exploration_briefs`):

```markdown
# Pebbles icon set — the chrome wave

Twelve figures the iOS app still draws as SF Symbols because the foundation wave (`../2026-10-foundation/`) has nothing that means them (iOS migration spec D2, `docs/superpowers/specs/2026-10-30-ios-icon-migration-design.md`). Same hand, same pen, same canvas as the foundation brief (`../2026-10-foundation/BRIEF.md`, DNA and Canvas sections). Judge at 20 pt in a list row and at 16 pt beside caption text.

Sets travel together: the visibility trio and the error family are drawn as a set or not at all, so no picker ends up with two vocabularies.

## The figures (meaning only; you choose the subject)

1. again — try once more, replay (`arrow.clockwise.circle.fill`, `arrow.counterclockwise`)
2. swap — trade one thing for another (`arrow.left.arrow.right`)
3. upload — a photo on its way up, a status, not a button (`arrow.up.circle`)
4. upvote — I want this, in the Lab's backlog (`arrow.up.circle`, `arrow.up.circle.fill`); related to 3, must read apart from it
5. something went wrong — the error family, one figure in three strengths or three figures: a load that failed, a link that broke, a page that is gone (`exclamationmark.triangle`, `exclamationmark.triangle.fill`, `person.2.slash`, `link.badge.plus`, `book.closed`)
6. document — terms, a policy, a text to read (`doc.text`)
7. privacy — a hand held up, what we keep for ourselves (`hand.raised`)
8. visibility, secret — only me (`lock.fill`)
9. visibility, private — me and my circle (`person.2.fill`); with 8 and 10 a trio, three drawings made together
10. visibility, public — anyone (`globe`)
11. locked — an achievement not yet earned (`lock`); must read apart from 8
12. invite — bring someone into the circle (`person.badge.plus`)
13. life domain — a compass, a territory, an area of one's life (`safari`)
14. market — where glyphs are traded (`storefront`)
```

(Fourteen numbered lines for the twelve concepts the spec names: the visibility trio is three lines.) The icons lint only checks that each row's wave folder exists; an extra folder is fine. Run `npm run lint --workspace=@pbbls/icons` to be sure.

- [ ] **Step 2: Rename the colliding string.** `SpineLabView.swift:157`: `onCamera: { homeLog = "camera" },` becomes `onCamera: { homeLog = "camera shortcut" },`. Then confirm no other dotless name collides, from the repo root:

```bash
grep -rnE '"(camera|clock|plus|photo|pencil|checkmark|square|heart|trash|gear|calendar|scribble|sparkle|sparkles|trophy|house)"' apps/ios/Pebbles --include='*.swift' | grep -v 'Theme/PbblsIcon.swift' | grep -vE '(systemName|systemImage): *"'
```

Expected: only the known helper sites (`HomeShortcuts.swift:13-15`, `Achievement+Localized.swift`, `ProfileCountersRow.swift`, `DataTile.swift` preview, `InviteSheet.swift:70`), all of which later parts migrate. Any other line is a string that is not a symbol: rename it the same way.

- [ ] **Step 3: Commit**

```bash
git add packages/icons/waves/2026-11-chrome/brief.md apps/ios/Pebbles/Features/Path/Spine/Debug/SpineLabView.swift
git commit -m "chore(ui): brief the chrome wave and free the one string the lint would hit"
```

### Task 7: Part 1 gate and PR

- [ ] **Step 1: Package gate**: `npm run lint --workspace=@pbbls/icons && npm run build --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons && npm run lint:replaces --workspace=@pbbls/icons`. All green.
- [ ] **Step 2: iOS gate** (the block at the top). `** TEST SUCCEEDED **`.
- [ ] **Step 3: Stack and PR**: `gh stack init chore/1314-replaces-lint` (the branch exists; `init` adopts it), `gh stack submit --auto --open`, then `gh pr edit <n> --title "chore(ui): add the migration lint, the migrated marker and the iOS icon sizes" --add-label chore,ui,ios,no-lab-note --milestone "M66 · Brand icons"` and a body: `Part 1 of #1314`, the spec link, what the lint matches, that it is vacuous until Part 6, the five sizes, the brief.

---

## Part 2 — tab bar and toolbar buttons (`chore/1314-tab-bar`)

`gh stack add chore/1314-tab-bar` from Part 1's branch.

### Task 8: `AppTab.icon`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Tabs/AppTab.swift`
- Create: `apps/ios/PebblesTests/Features/AppTabTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
@testable import Pebbles

@Suite("AppTab icons")
struct AppTabTests {
    @Test("every tab has a distinct icon with an active twin (spec D5)")
    func icons() {
        let icons = AppTab.allCases.map(\.icon)
        #expect(Set(icons).count == AppTab.allCases.count)
        for tab in AppTab.allCases {
            #expect(tab.icon.activeTwin != nil, "\(tab) has no active twin")
        }
        #expect(AppTab.home.icon == .home)
        #expect(AppTab.path.icon == .path)
        #expect(AppTab.people.icon == .people)
        #expect(AppTab.collections.icon == .collection)
        #expect(AppTab.you.icon == .person)
    }
}
```

- [ ] **Step 2: Run it**: compile error, no `icon`.
- [ ] **Step 3: Implement**, appended to `AppTab.swift`:

```swift
extension AppTab {
    /// The tab's drawing; the bar lights its `activeTwin` when selected (spec D5).
    var icon: PbblsIcon {
        switch self {
        case .home: .home
        case .path: .path
        case .people: .people
        case .collections: .collection
        case .you: .person
        }
    }
}
```

- [ ] **Step 4: Run it**: PASS.
- [ ] **Step 5: Commit**: `git add apps/ios/Pebbles/Features/Tabs/AppTab.swift apps/ios/PebblesTests/Features/AppTabTests.swift && git commit -m "chore(ios): give each tab its pebbles icon"`

### Task 9: The tab bar lights its stone

**Files:**
- Modify: `apps/ios/Pebbles/Features/Tabs/PebblesTabView.swift:19-53`

- [ ] **Step 1: Replace the five `.tabItem` lines.** Each `.tabItem { Label("Home", systemImage: "house") }` becomes:

```swift
            .tabItem { Label { Text("Home") } icon: { tabImage(.home) } }
```

with `Text("Path")`, `Text("People")`, `Text("Collections")`, `Text("You")` and `.path`, `.people`, `.collections`, `.you`. Add, below `body`:

```swift
    /// The selected tab draws the lit seal (D5). UIKit renders a tab image as a
    /// template and never scales it; the asset is 24×24 with its vector kept,
    /// so no frame and no rendering mode are set here.
    private func tabImage(_ tab: AppTab) -> Image {
        let icon = nav.selected == tab ? (tab.icon.activeTwin ?? tab.icon) : tab.icon
        return Image(icon.assetName)
    }
```

- [ ] **Step 2: Build and run on the simulator**, then **on the iPhone 15** (`project_ios_device_deploy`). Check: five drawings in the bar, the selected one is the `active` twin, the bar's tint colours the selected one and `onSurfaceVariant` the others (R3). If the tint and the twin disagree visually, keep the twin and set the unselected colour in `PebblesApp`'s `UITabBarAppearance` shim.
- [ ] **Step 3: Commit**: `git add apps/ios/Pebbles/Features/Tabs/PebblesTabView.swift && git commit -m "chore(ios): light the selected tab's stone"`

### Task 10: Toolbar buttons, gate, PR

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift:79`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift:44,50`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift:27`
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift:35`

- [ ] **Step 1: Five images.** `Image(systemName: "gear")` → `PbblsIconImage(.slider, .toolbar)`; `Image(systemName: "person.2.badge.plus")` → `PbblsIconImage(.people, .toolbar)`; the three `Image(systemName: "plus")` inside `PebbleToolbarButton` → `PbblsIconImage(.plusCircle, .toolbar)`. The buttons keep their `.accessibilityLabel`; the image stays hidden from VoiceOver. `ConnectionsListView.swift:25` (`person.badge.plus`) stays: a wave symbol (D2).
- [ ] **Step 2: Device check.** The toolbar tint (`PebbleToolbarButton` sets `.tint(Color.pebbles.onSurfaceVariant)`) colours a template image as it did the symbol; confirm on the phone in Profile, People, Collections and Glyphs.
- [ ] **Step 3: Gate** (the iOS block). Then `gh stack push`, `gh stack submit --auto --open`, `gh pr edit` with title `chore(ios): draw the tab bar and the toolbar buttons with pebbles icons`, labels `chore,ios,no-lab-note`, milestone M66, body `Part 2 of #1314` plus the device-check note (no reference renders the tab bar, D9).
- [ ] **Step 4: Commit**: `git add -A apps/ios/Pebbles && git commit -m "chore(ios): draw the toolbar buttons with pebbles icons"`

---

## Part 3 — Home, Record, Complete, media (`chore/1314-home-record`)

`gh stack add chore/1314-home-record`.

### Task 11: Home checklist and shortcuts

**Files:**
- Modify: `apps/ios/Pebbles/Features/Home/Checklist/HomeChecklistView.swift:65-69, 91-95`
- Modify: `apps/ios/Pebbles/Features/Home/Shortcuts/HomeShortcuts.swift`
- Modify: `apps/ios/Pebbles/Features/Home/Shortcuts/MomentToast.swift:60`

- [ ] **Step 1: The claim row.** Lines 65–69 become:

```swift
                PbblsIconImage(.pebbleRoundLit1, .row, style: AnyShapeStyle(Color.pebbles.onPrimary))
                    .frame(width: 22, height: 22)
```

(the lit pebble is the one to claim, D4; `PbblsIconImage` is already accessibility-hidden).

- [ ] **Step 2: The task row.** Lines 91–95 become:

```swift
                PbblsIconImage(row.isDone ? .checkCircle : .pebbleRound, .row,
                               style: AnyShapeStyle(row.isDone ? Color.pebbles.onSurface : Color.pebbles.outline))
                    .frame(width: 22, height: 22)
```

- [ ] **Step 3: The shortcuts.** `tile("camera", …)` → `tile(.camera, …)`, `tile("clock", …)` → `tile(.pebbleFlat, …)`, `tile("plus.circle", …)` → `tile(.plusCircle, …)`. The signature becomes `private func tile(_ icon: PbblsIcon, _ key: LocalizedStringKey, …)` and lines 26–29 become:

```swift
                PbblsIconImage(icon, .tile, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
                    .frame(height: 28)
```

- [ ] **Step 4: The toast thumbnail.** `Image(systemName: "photo").foregroundStyle(Color.pebbles.onSurfaceVariant)` → `PbblsIconImage(.cameraFocus, .tile, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))`.
- [ ] **Step 5: Build, then commit**: `git commit -am "chore(ios): draw the home checklist, shortcuts and toast with pebbles icons"`. (`ChecklistIllustrations` and the guides keep `arrow.right`, D1.)

### Task 12: `PebblesCheckbox`

**Files:**
- Modify: `apps/ios/Pebbles/Components/Checkboxes/PebblesCheckbox.swift:42-44`

- [ ] **Step 1:** Lines 42–44 become:

```swift
            PbblsIconImage(isChecked ? .checkCircle : .pebbleRound, .tile,
                           style: AnyShapeStyle(isChecked ? Color.pebbles.onPrimary : Color.pebbles.onSurfaceVariant))
```

(`tile` = 28 inside the 44 box, the box frame's proportion; the spec's "the box frame" is read as the icon sized to the box, not filling it.) The two previews still render.

- [ ] **Step 2: Commit**: `git commit -am "chore(ios): tick the consent box with a stone"`

### Task 13: Record and Complete steps

**Files:**
- Modify: `apps/ios/Pebbles/Features/Record/Steps/RecordPhotoStep.swift:97, 125, 134`
- Modify: `apps/ios/Pebbles/Features/Record/Steps/RecordWhenStep.swift:23`
- Modify: `apps/ios/Pebbles/Features/Complete/Steps/CompleteCollectionStep.swift:35`
- Modify: `apps/ios/Pebbles/Features/Complete/Steps/CompleteSuccessStep.swift:36`

- [ ] **Step 1: RecordPhotoStep.** `Label("Ready", systemImage: "checkmark.circle.fill")` → `Label { Text("Ready") } icon: { PbblsIconImage(.checkCircle, .inline, relativeTo: .subheadline) }`. The remove button's `Image(systemName: "xmark.circle.fill")` → `PbblsIconImage(.remove, .row)` (the `.foregroundStyle(Color.pebbles.error)` below it stays and colours the template). The 40pt `Image(systemName: "photo")` → `PbblsIconImage(.cameraFocus, .hero)` (drop the `.font(.system(size: 40))` line).
- [ ] **Step 2: RecordWhenStep.** `Label("Taken from your photo", systemImage: "sparkles")` → `Label { Text("Taken from your photo") } icon: { PbblsIconImage(.sparkle, .inline, relativeTo: .subheadline) }` (the photo date is `sparkle`, not `karma-dot`; the mapping table).
- [ ] **Step 3: CompleteCollectionStep.** `Image(systemName: "square.stack")` → `PbblsIconImage(.collection, .row)`; its `.foregroundStyle` stays.
- [ ] **Step 4: CompleteSuccessStep.** `Image(systemName: "sparkle")` → `PbblsIconImage(.karmaDot, .inline, relativeTo: .headline)`; its `.foregroundStyle` stays.
- [ ] **Step 5: Build, commit**: `git commit -am "chore(ios): draw the record and complete steps with pebbles icons"`

### Task 14: Attached photo, existing snap, snap image

**Files:**
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/AttachedPhotoView.swift:50, 83`
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/ExistingSnapRow.swift:30, 77`
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/SnapImageView.swift:58`

- [ ] **Step 1:** Each `Label("Ready"/"Saved", systemImage: "checkmark.circle.fill")` → `Label { Text("Ready") } icon: { PbblsIconImage(.checkCircle, .inline, relativeTo: .caption) }` (and `Text("Saved")`); keep the `.labelStyle`, `.font`, `.foregroundStyle` lines. `arrow.up.circle`, `exclamationmark.triangle.fill` and `arrow.clockwise.circle.fill` stay (wave). Each remove button's `Image(systemName: "xmark.circle.fill")` → `PbblsIconImage(.remove, .row)`. `SnapImageView`'s placeholder `Image(systemName: "photo.on.rectangle.angled").foregroundStyle(.secondary)` → `PbblsIconImage(.cameraFocus, .tile, style: AnyShapeStyle(.secondary))`.
- [ ] **Step 2: Build, commit**: `git commit -am "chore(ios): draw the photo rows with pebbles icons"`

### Task 15: Part 3 gate, references, PR

- [ ] **Step 1: Gate**, then `gh stack push`, `gh stack submit --auto --open`, `gh pr edit` (title from the table, `chore,ios,no-lab-note`, M66, `Part 3 of #1314`).
- [ ] **Step 2: Re-baseline.** `HomeReferences` (`shortcuts`, `toast`, the checklist renders) go red; Record and Complete have no references and are checked in the simulator. Follow **Re-baselining a part**, then look at the bot's PNGs in the PR diff before moving on.
- [ ] **Step 3: Lint is still vacuous** (`npm run lint:replaces --workspace=@pbbls/icons` prints `0 rows`); the literals that remain in other areas are the next parts' work.

---

## Part 4 — Path card, menu, pickers (`chore/1314-path`)

`gh stack add chore/1314-path`.

### Task 16: `PebblesMenuItem` takes a `PbblsIcon`

**Files:**
- Modify: `apps/ios/Pebbles/Components/Menus/PebblesMenu.swift:7-25, 85, 98`
- Modify: `apps/ios/Pebbles/Features/Path/Card/HeldCardMenu.swift:10, 12`
- Modify: `apps/ios/Pebbles/Features/Connections/ConnectionsListView.swift:93` (type change only; the file's other symbols are Part 6)

- [ ] **Step 1: The item.** `let systemImage: String` → `let icon: PbblsIcon`; the initializer's `systemImage: String` → `icon: PbblsIcon` and `self.systemImage = systemImage` → `self.icon = icon`; `.delete` builds `PebblesMenuItem(title: "Delete", icon: .trash, isDestructive: true, action: action)`.
- [ ] **Step 2: The row.** `iconWidth: CGFloat = 24` → `20` (D6: a menu item is `row`), and line 98:

```swift
                    Label { Text(item.title) } icon: { PbblsIconImage(item.icon, .row).frame(width: iconWidth) }
```

- [ ] **Step 3: Callers.** `HeldCardMenu`: `icon: .drawDotted` for "Edit moment", `icon: .pebbleRound` for "Edit pebble". `ConnectionsListView:93`: `PebblesMenuItem(title: "Remove", icon: .remove, isDestructive: true)`. `grep -rn "systemImage:" apps/ios/Pebbles --include='*.swift' | grep PebblesMenuItem` must print nothing.
- [ ] **Step 4: Build, commit**: `git commit -am "chore(ios): menu items carry a pebbles icon"`

### Task 17: `CollectionMark`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Card/CollectionMark.swift:16-19`

- [ ] **Step 1:** Lines 16–19 become:

```swift
                // 14pt by default: the one place an icon draws under the 16 floor
                // (spec D6), a 1.0pt stroke accepted on the band.
                PbblsIconImage(.collection, size: side, style: AnyShapeStyle(ink))
```

- [ ] **Step 2: Build, commit**: `git commit -am "chore(ios): the band's collection mark is the pebbles collection"`

### Task 18: `SoulItem`, the emotion header, the edit sheet

**Files:**
- Modify: `apps/ios/Pebbles/Features/Shared/SoulItem.swift:53-55, 56-58`
- Modify: `apps/ios/Pebbles/Features/Path/EmotionPickerContent.swift:93-94`
- Modify: `apps/ios/Pebbles/Features/Path/EditMomentSheet.swift:110`

- [ ] **Step 1: SoulItem.** Lines 53–58 become:

```swift
                        PbblsIconImage(.pebbleRound, .inline, relativeTo: .caption2, style: AnyShapeStyle(fossilColor))
                        Text("\(count)")
                            .font(.caption2)
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
```

(R2: if the 16 stone reads heavy beside the count on the phone, `inline` becomes 14 in `PbblsIconSize`, one line, and the test in Task 5 follows.)

- [ ] **Step 2: EmotionPickerContent.** `Image(systemName: "waveform.path.ecg").foregroundStyle(category.palette.primary)` → `PbblsIconImage(.heart, .row, style: AnyShapeStyle(category.palette.primary))`.
- [ ] **Step 3: EditMomentSheet.** `Label("Add a photo", systemImage: "photo.badge.plus")` → `Label { Text("Add a photo") } icon: { PbblsIconImage(.camera, .row) }`.
- [ ] **Step 4: Build, commit**: `git commit -am "chore(ios): draw the soul count, the emotion header and the photo row with pebbles icons"`

`SoulPill`'s `AddSoulPill` keeps `person.badge.plus` (invite, a wave symbol, D2): the spec's part table lists the file, the mapping does not, and the mapping wins.

### Task 19: The bare moment page and the FAB

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Card/BareMomentPage.swift:156`
- Modify: `apps/ios/Pebbles/Features/Path/Components/NewPebbleFab.swift:17-19`

- [ ] **Step 1:** `Label("Make it a pebble", systemImage: "plus")` → `Label { Text("Make it a pebble") } icon: { PbblsIconImage(.plusCircle, .row) }`. The FAB's `Image(systemName: "plus").font(.title2.weight(.semibold)).foregroundStyle(Color.pebbles.onPrimary)` → `PbblsIconImage(.plusCircle, .tile, style: AnyShapeStyle(Color.pebbles.onPrimary))` (28 in the 56 circle; `toolbar` looked lost there, a judgment the maintainer's eye confirms on the phone, R1).
- [ ] **Step 2: Build, commit**: `git commit -am "chore(ios): the make-it-a-pebble button and the fab draw the pebbles plus"`

### Task 20: Part 4 gate, references, PR

- [ ] **Step 1: Gate**; `gh stack push`; `gh stack submit --auto --open`; `gh pr edit` (title from the table, `chore,ios,no-lab-note`, M66, `Part 4 of #1314`).
- [ ] **Step 2: Re-baseline.** `HeldCard*` (the band's mark), `PeopleReferences` (`SoulItem`) and any Spine or Wrap reference that draws a soul count go red. Follow **Re-baselining a part**; fast-forward Part 3's branch first if its bot commit landed after Part 4 was pushed.

---

## Part 5 — Profile, achievements, settings, lists (`chore/1314-profile`)

`gh stack add chore/1314-profile`.

### Task 21: `SurfaceTile`, `ProfileShortcutTile`, `DataTile` take a `PbblsIcon`

**Files:**
- Modify: `apps/ios/Pebbles/Theme/SurfaceTile.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutTile.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutsRow.swift:8`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/DataTile.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileCountersRow.swift:10-12`
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphDetailDrawer.swift:92-94` (type change; the drawer's other symbols are Part 6)

- [ ] **Step 1: SurfaceTile.** `let systemImage: String` → `let icon: PbblsIcon`; lines 25–31 become:

```swift
            PbblsIconImage(icon, .tile, style: AnyShapeStyle(muted ? Color.pebbles.outline : (iconTint ?? Color.pebbles.onPrimaryContainer)))
                // Fixed icon slot so tiles stay equal height (Figma tile spec, 30×30).
                .frame(width: 30, height: 30)
```

Update the doc comment's first line to "a pebbles icon above a short label". Preview: `SurfaceTile(icon: .calendarDate) { … }`, `SurfaceTile(icon: .chartDoubleGrow, muted: true) { … }`, `SurfaceTile(icon: .people, muted: true) { … }`.

- [ ] **Step 2: ProfileShortcutTile / Row.** `let systemImage: String` → `let icon: PbblsIcon`; `SurfaceTile(icon: icon)`; the row: `ProfileShortcutTile(title: "Glyphs", icon: .pebbleDottedGlyph, route: .glyphs)`.
- [ ] **Step 3: DataTile.** `let icon: String` → `let icon: PbblsIcon`; lines 18–20:

```swift
                PbblsIconImage(icon, .tile, relativeTo: .subheadline, style: AnyShapeStyle(Color.pebbles.primary))
```

Preview: `icon: .calendarDate`, `.pebbleRound`, `.karmaDot`. `ProfileCountersRow`: the same three.

- [ ] **Step 4: GlyphDetailDrawer** `statCards`: `SurfaceTile(icon: .calendarDate)`, `SurfaceTile(icon: .chartDoubleGrow, muted: true)`, `SurfaceTile(icon: .people, muted: true)` (owners is `people` by site, D2).
- [ ] **Step 5: Build, commit**: `git commit -am "chore(ios): tiles carry a pebbles icon"`

### Task 22: Achievements

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Models/Achievement+Localized.swift:99-115`
- Modify: `apps/ios/Pebbles/Features/Profile/AchievementsView.swift:131-133`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileAchievementsCard.swift:29-31, 71-73`
- Modify: `apps/ios/Pebbles/Features/Karma/AchievementMomentView.swift:34-36`
- Modify: `apps/ios/Pebbles/Features/Karma/KarmaEarnedCapsule.swift:19-20`
- Test: `apps/ios/PebblesTests/AchievementsDecodingTests.swift` (or a new `AchievementFamilyIconTests.swift`)

- [ ] **Step 1: Write the failing test** (new file `apps/ios/PebblesTests/AchievementFamilyIconTests.swift`; build an `AchievementRecord` the way `AchievementsDecodingTests` does, changing only `family`):

```swift
import Testing
@testable import Pebbles

@Suite("Achievement family icons")
struct AchievementFamilyIconTests {
    @Test("every family draws a pebbles icon; the wave families and unknowns take the glow (spec D7)",
          arguments: [
            ("pebble_count", PbblsIcon.stack), ("emotion_first", .heart), ("domain_first", .pebbleGlowMd),
            ("first_collection", .collection), ("first_glyph", .pebbleDottedGlyph), ("first_soul", .people),
            ("first_moment", .pebbleFlat), ("first_pebble", .pebbleRound), ("first_five", .stackLit3),
            ("glyph_count", .pebbleDottedGlyph), ("glyph_sales", .pebbleGlowMd), ("never_heard_of", .pebbleGlowMd),
          ])
    func familySymbol(family: String, icon: PbblsIcon) throws {
        #expect(AchievementsDecodingTests.record(family: family).familySymbol == icon)
    }
}
```

If `AchievementsDecodingTests` has no reusable `record(family:)`, add a `static func record(family: String) -> AchievementRecord` there that decodes its existing fixture JSON with the family substituted.

- [ ] **Step 2: Run it**: compile error (`familySymbol` is a `String`).
- [ ] **Step 3: Implement** `familySymbol`:

```swift
    /// The family's pebbles icon (spec D7). `domain_first` and `glyph_sales`
    /// wait for the chrome wave (life domain, market) and take the family default.
    var familySymbol: PbblsIcon {
        switch family {
        case "pebble_count":     .stack
        case "emotion_first":    .heart
        case "first_collection": .collection
        case "first_glyph":      .pebbleDottedGlyph
        case "first_soul":       .people
        case "first_moment":     .pebbleFlat
        case "first_pebble":     .pebbleRound
        case "first_five":       .stackLit3
        case "glyph_count":      .pebbleDottedGlyph
        default:                 .pebbleGlowMd
        }
    }
```

- [ ] **Step 4: Callers.** `AchievementsView:131-133`: `PbblsIconImage(record.familySymbol, .tile, style: AnyShapeStyle(isUnlocked ? Color.pebbles.primary : Color.pebbles.outline))` (the `lock` beside it keeps `.pebblesIcon(.medium)` until Part 7). `ProfileAchievementsCard:29-31`: `PbblsIconImage(.pebbleGlowMd, .tile, style: AnyShapeStyle(Color.pebbles.primary))`; the badge at 71–73: `PbblsIconImage(record.familySymbol, .row, style: AnyShapeStyle(Color.pebbles.primary))` keeping the `.frame(width: 32, height: 32)`, background and accessibility label (`PbblsIconImage` hides itself; the `.accessibilityLabel` on the outer modifier chain still applies to the badge view, verify with the Accessibility Inspector or keep the `label:` argument instead). `AchievementMomentView:34-36`: `PbblsIconImage(.pebbleGlowLg, .hero, style: AnyShapeStyle(Color.pebbles.primary))` (drop the `.font(.system(size: 44))` line). `KarmaEarnedCapsule:19-20`: `PbblsIconImage(.karmaDot, .inline, style: AnyShapeStyle(Color.pebbles.primary))`.
- [ ] **Step 5: Run the test**: PASS. Build. Commit: `git commit -am "chore(ios): achievements and karma draw pebbles icons"`

### Task 23: `AssiduityGrid` keeps a shape

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Components/AssiduityGrid.swift:35-38`

- [ ] **Step 1:** Lines 35–38 become:

```swift
                        // 7pt cells: no icon draws this small (spec D4); a lit
                        // stone is a filled dot, a day without one an outline.
                        Circle()
                            .fill(active ? Color.pebbles.primary : Color.clear)
                            .overlay(Circle().strokeBorder(Color.pebbles.surfaceContainerHighest, lineWidth: active ? 0 : 1))
                            .frame(width: cellSize, height: cellSize)
```

- [ ] **Step 2: Build; `AssiduityGridLayoutTests` still pass. Commit**: `git commit -am "chore(ios): the practice grid draws dots, not symbols"`

### Task 24: Profile cards and settings

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileLabCard.swift:7-9`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift:474, 485, 496, 514`

- [ ] **Step 1: ProfileLabCard.** `Image(systemName: "lightbulb.max").pebblesIcon(.large).foregroundStyle(Color.pebbles.primary)` → `PbblsIconImage(.sparkle, .tile, style: AnyShapeStyle(Color.pebbles.primary))`. The chevron keeps its token until Part 7.
- [ ] **Step 2: SettingsSheet.** Developer rows (D10): `Image(systemName: "sparkles")` → `PbblsIconImage(.pebbleRound, .row)`; `"line.3.horizontal.decrease"` → `PbblsIconImage(.path, .row)`; `"square.grid.3x3"` → `PbblsIconImage(.sparkles, .row)`. Delete account: `Label("Delete account", systemImage: "trash")` → `Label { Text("Delete account") } icon: { PbblsIconImage(.trash, .row) }` keeping `.foregroundStyle(Color.pebbles.error)`. `apple.logo`, `g.circle`, `square.and.arrow.up`, `doc.text`, `hand.raised`, `xmark` stay (D1, D2).
- [ ] **Step 3: Build, commit**: `git commit -am "chore(ios): the lab card, the developer rows and delete account draw pebbles icons"`

### Task 25: Souls and collections, the empty states

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift:171-175`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift:101-105`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift:112-116`

- [ ] **Step 1: Empty states.** Each `ContentUnavailableView("…", systemImage: "…", description: Text("…"))` becomes the closure form (D6):

```swift
                ContentUnavailableView {
                    Label { Text("No souls yet") } icon: { PbblsIconImage(.people, .hero) }
                } description: {
                    Text("People and beings you tag on your pebbles will appear here.")
                }
```

with `.hourglassEnd` for the two "No pebbles yet" views. The `exclamationmark.triangle` error views stay.

- [ ] **Step 2: Build, commit**: `git commit -am "chore(ios): the souls and collections empty states draw pebbles icons"`

### Task 26: The collections swipe delete

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift:120`

- [ ] **Step 1:** `Label("Delete", systemImage: "trash")` → `Label { Text("Delete") } icon: { PbblsIconImage(.trash, .row) }`.
- [ ] **Step 2: Check it on the simulator.** UIKit draws swipe-action labels itself and may drop a SwiftUI view: swipe a collection row left. If the icon is missing, use `Image(PbblsIcon.trash.assetName).renderingMode(.template)` in that one `Label` instead, with a comment saying why, and note it in the PR body and in Lessons learned.
- [ ] **Step 3: Build, commit**: `git commit -am "chore(ios): the collections swipe delete draws the pebbles trash"`

### Task 27: Part 5 gate, references, PR

- [ ] **Step 1: Gate**; `gh stack push`; `gh stack submit --auto --open`; `gh pr edit` (title from the table, `chore,ios,no-lab-note`, M66, `Part 5 of #1314`).
- [ ] **Step 2: Re-baseline.** `CollectionsReferences`, `PeopleReferences` and any reference that draws `DataTile`, `SurfaceTile` or the achievements shelf go red; Profile and Settings have no reference and are checked in the simulator and on the phone (the three `DataTile` icons at 28 beside subhead text are the sizing the spec chose; R1).

---

## Part 6 — Glyphs, connections, lab; the lint goes live (`chore/1314-glyph-connections-lab`)

`gh stack add chore/1314-glyph-connections-lab`.

### Task 28: `GlyphTabBar` and `GlyphView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphTabBar.swift:27-28, 55-61`
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphView.swift:64-69`

- [ ] **Step 1: GlyphTabBar.** `symbol(_:) -> String` becomes:

```swift
    private func icon(_ tab: GlyphTab) -> PbblsIcon {
        switch tab {
        case .mine: .person
        case .owned: .checkCircleDot
        case .commu: .people
        }
    }
```

and lines 27–28: `PbblsIconImage(icon(tab), .row)`.

- [ ] **Step 2: GlyphView.** The `.carve` case: `PbblsIconImage(.pebbleDottedGlyph, size: max(side * 0.4, 18), style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))`; the `.create` case: `PbblsIconImage(.plusCircle, size: max(side * 0.4, 18), style: AnyShapeStyle(Color.pebbles.outline))`.
- [ ] **Step 3: Build, commit**: `git commit -am "chore(ios): the glyph pill and cells draw pebbles icons"`

### Task 29: Glyph lists, picker, detail, carve

**Files:**
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift:106-117, 139`
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerContent.swift:115-127, 171`
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphDetailDrawer.swift:115, 121, 137`
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift:110`

- [ ] **Step 1: Empty states** (both files) in the closure form of Task 25: `.pebbleDottedGlyph` for "No glyphs yet", `.checkCircleDot` for "Nothing owned yet", `.people` for "No community glyphs", all `.hero`. `GlyphsListView.emptyState` is a `switch` that `return`s: give the three branches the same closure-form view so the return type stays one type (wrap in `AnyView` only if the compiler insists, and say so in a comment). `GlyphPickerContent:140` `chevron.right` and the two `exclamationmark.triangle` stay.
- [ ] **Step 2: Prices.** The three `Image(systemName: "sparkle")` beside a price (`GlyphsListView:139`, `GlyphPickerContent:171`, `GlyphDetailDrawer:121, 137`) → `PbblsIconImage(.karmaDot, .inline, relativeTo: .caption2)` in the `.caption2` rows and `relativeTo: .headline` / `.subheadline` in the drawer's two.
- [ ] **Step 3: Owned seal.** `GlyphDetailDrawer:115` `Image(systemName: "checkmark.seal.fill").foregroundStyle(Color.pebbles.primary)` → `PbblsIconImage(.checkCircleDot, .inline, style: AnyShapeStyle(Color.pebbles.primary))`. `arrow.left.arrow.right` (swap) stays.
- [ ] **Step 4: Carve.** `Label("Clear", systemImage: "trash")` → `Label { Text("Clear") } icon: { PbblsIconImage(.trash, .row) }`; `arrow.uturn.backward` stays.
- [ ] **Step 5: Build, commit**: `git commit -am "chore(ios): glyph lists, picker, detail and carve draw pebbles icons"`

### Task 30: Connections and invite

**Files:**
- Modify: `apps/ios/Pebbles/Features/Connections/ConnectionsListView.swift:82-86, 148-149`
- Modify: `apps/ios/Pebbles/Features/Connections/InviteSheet.swift:70`

- [ ] **Step 1:** The "No connections yet" `ContentUnavailableView` → closure form with `PbblsIconImage(.people, .hero)`; the avatar fallback `Image(systemName: "person.circle").foregroundStyle(Color.pebbles.onSurfaceVariant)` → `PbblsIconImage(.person, .row, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))` inside the 32 frame. `person.2.slash` and the toolbar's `person.badge.plus` stay (wave).
- [ ] **Step 2: InviteSheet** line 70 becomes a mixed label (D6):

```swift
                            Label {
                                Text(didCopy ? "Copied" : "Copy link")
                            } icon: {
                                if didCopy {
                                    PbblsIconImage(.checkmarkDot, .row)
                                } else {
                                    Image(systemName: "doc.on.doc")
                                }
                            }
```

- [ ] **Step 3: Build, commit**: `git commit -am "chore(ios): connections and the invite sheet draw pebbles icons"`

### Task 31: Lab timeline and community card

**Files:**
- Modify: `apps/ios/Pebbles/Features/Lab/Components/LogTimeline.swift:88-90, 97-103`
- Modify: `apps/ios/Pebbles/Features/Lab/Components/FeaturedCommunityCard.swift:12-14`

- [ ] **Step 1: LogTimeline.** `iconName: String` becomes:

```swift
    private var icon: PbblsIcon {
        switch mode {
        case .changelog:  .checkCircle
        case .inProgress: .pebbleRoundLit1
        case .backlog:    .pebbleRound
        }
    }
```

and lines 88–90: `PbblsIconImage(icon, .inline, relativeTo: .footnote, style: AnyShapeStyle(iconColor))` (the column is 16 wide; `inline` is 16). The Lab's `arrow.right` "See all" links and the upvote arrows stay.

- [ ] **Step 2: FeaturedCommunityCard.** `Image(systemName: "bubble.left.and.bubble.right.fill").font(.title2).foregroundStyle(Color.pebbles.primary)` → `PbblsIconImage(.people, .tile, style: AnyShapeStyle(Color.pebbles.primary))`.
- [ ] **Step 3: Build, commit**: `git commit -am "chore(ios): the lab timeline and community card draw pebbles icons"`

### Task 32: The last literals

- [ ] **Step 1: Find what is left.** From the repo root, with the 49 names:

```bash
node -e '
const m=require("./packages/icons/icons.json");
const names=m.icons.flatMap(r=>r.replaces.ios);
console.log(names.map(n=>`"${n}"`).join("\n"));' > /private/tmp/claude-503/-Users-alexis-code-pbbls/ccea1ead-41c0-4786-ab6d-eb71e8b3b19f/scratchpad/names.txt
grep -rnFf /private/tmp/claude-503/-Users-alexis-code-pbbls/ccea1ead-41c0-4786-ab6d-eb71e8b3b19f/scratchpad/names.txt apps/ios/Pebbles --include='*.swift' | grep -v 'Theme/PbblsIcon.swift'
```

Expected: nothing. Every line printed is a site a task above missed (or a `#Preview` still passing a string): migrate it with the same size rule as its neighbours and amend the matching commit's area in a new commit `chore(ios): migrate the last <area> literals`.

### Task 33: `migrated: ["ios"]` and the lint goes live

**Files:**
- Modify: `packages/icons/icons.json` (the 27 rows with a non-empty `replaces.ios`, plus `pebble-glow-lg`)

- [ ] **Step 1:** From `packages/icons`:

```bash
node -e '
const fs=require("node:fs");
const m=JSON.parse(fs.readFileSync("icons.json","utf8"));
const used=new Set(["pebble-glow-lg"]);
for(const r of m.icons){ if(r.replaces.ios.length>0||used.has(r.name)) r.migrated=["ios"]; }
fs.writeFileSync("icons.json", JSON.stringify(m,null,2)+"\n");
console.log(m.icons.filter(r=>r.migrated.includes("ios")).length);'
```

Expected: `28`.

- [ ] **Step 2: The lint, live**

Run: `npm run lint:replaces --workspace=@pbbls/icons`
Expected: `replaces: 27 rows, 49 names, 0 hits`, exit 0. A hit is a Task 32 miss: fix it, then re-run. Also `npm run lint --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons` (still fresh: `migrated` reaches no emitter).

- [ ] **Step 3: Commit, one commit with any last literal fix** (the spec: the marker flips in the same commit that deletes the last literal):

```bash
git add packages/icons/icons.json apps/ios/Pebbles
git commit -m "chore(ui): ios has migrated off every replaced symbol; the lint is live"
```

### Task 34: Part 6 gate, references, PR

- [ ] **Step 1: Gate** (iOS block, plus the package gate from Task 7 Step 1); `gh stack push`; `gh stack submit --auto --open`; `gh pr edit` (title from the table, `chore,ui,ios,no-lab-note`, M66, `Part 6 of #1314`).
- [ ] **Step 2: Re-baseline** whatever goes red (the glyph, connections and lab screens have no references today; expect none, and check the three screens on the phone).
- [ ] **Step 3: Device pass of the whole stack** from Part 6's head: every tab, the Icon lab's five detents, the held card's menu, a record flow, Profile → Achievements, Settings, Glyphs, Connections, Lab. List what reads wrong in the PR body for the maintainer's eye (R1); a size that reads wrong is a one-line `PbblsIconSize` change in Part 1, rebased up.

---

## Part 7 — retire the tokens (`chore/1314-retire-tokens`)

`gh stack add chore/1314-retire-tokens`.

### Task 35: Delete `PebblesIcon` and `.pebblesIcon(_:)`

**Files:**
- Modify: `apps/ios/Pebbles/Theme/Icon+Pebbles.swift` (delete lines 1–34: the enum, the `View` extension, the private extension; keep `import SwiftUI`, `PbblsIconSize` and the `PbblsIconImage` extension)
- Modify: `apps/ios/Pebbles/Features/Profile/AchievementsView.swift:136-138`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileAchievementsCard.swift:49-51`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileLabCard.swift:19-21`

- [ ] **Step 1: The three D6 inline fonts.** Each `.pebblesIcon(.medium)` on a kept symbol (`lock`, two `chevron.right`) becomes `.font(.system(size: 15, weight: .medium, design: .rounded))`.
- [ ] **Step 2: Delete the tokens.** `grep -rn "pebblesIcon(\|PebblesIcon\b" apps/ios --include='*.swift'` must print nothing (the doc comment in `Icon+Pebbles.swift` mentioning `PebblesFont` can stay if it still describes the file; rewrite its header to describe `PbblsIconSize`).
- [ ] **Step 3: Build, gate, commit**: `git commit -am "chore(ios): retire the PebblesIcon font tokens"`

### Task 36: Decisions log and lessons learned

**Files:**
- Modify: `docs/decisions/log.md` (append)
- Modify: `docs/superpowers/plans/2026-10-30-ios-icon-migration.md` (this file: a `## Lessons learned` section at the end)

- [ ] **Step 1: Append** one entry in the log's format (status, scope, context, decision, why, consequences, supersedes, refs), titled `## 2026-11-xx — iOS draws the pebbles icons; chrome stays SF; the migration lint reads a per-surface marker (#1314)`. Its Decision bullets: the closed D1 list (`chevron.backward/down/forward/left/right`, `xmark`, `ellipsis.circle`, `square.and.arrow.up`, `doc.on.doc`, `arrow.uturn.backward`, `arrow.right`, `applelogo`, `apple.logo`, `g.circle`), with the rule that a new SF Symbol outside it needs a drawing or a wave-brief line; the five `PbblsIconSize` values and the 16 floor with `CollectionMark`'s 14 as the one exception; `migrated` per row with the lint keyed on it and on bare literals, and `status: shipped` requiring every surface in `migrated`. **Supersedes:** the 2026-10-29 icons entry's status sentence and package spec D4/D10 (the `shipped` key and the `systemName:` form). **Refs:** #1314, the spec, this plan, `packages/icons/scripts/lib/replaces.ts`.
- [ ] **Step 2: Lessons learned** in this plan: what the device pass changed (sizes, the FAB, the checkbox, R2's verdict on `inline`), how many references each part re-baselined, whether the swipe-action Label took the template image, anything the lint false-hit.
- [ ] **Step 3: Commit**: `git add docs && git commit -m "docs: log the icon chrome list, the sizes and the migrated marker"`

### Task 37: Part 7 gate, PR, merge

- [ ] **Step 1: Gate**; `gh stack push`; `gh stack submit --auto --open`; `gh pr edit` (title from the table, `chore,ui,ios,no-lab-note`, M66, body `Resolves #1314` and the stack's summary: 49 replaced, 16 kept, 19 to the wave, the lint live).
- [ ] **Step 2: Before merging:** every bot commit fast-forwarded in, `gh stack rebase`, `git diff --stat origin/<b> <b>` empty on every branch, `gh stack push`, wait for every PR CLEAN (re-run any run the double push cancelled: `gh run rerun <id>`), then hand the maintainer `gh stack merge --yes --squash` (the classifier blocks it for the agent).

---

## Self-review against the spec

- **D1** chrome list: Tasks 10, 13, 14, 24, 29, 30, 31 name each kept symbol at its site. **D2** wave: Task 6 brief; the `person.2.fill` removal in Task 2. **D3** picks: the case names in every task match the mapping tables. **D4** moments flat / pebbles round / lit / grid shape: Tasks 11, 12, 22, 23, 31. **D5** tab bar: Tasks 8–9. **D6** sizes and the two initializers that change shape: Task 5, the closure-form `ContentUnavailableView` and `Label` in Tasks 13, 14, 24, 25, 29, 30; the three inline fonts in Task 35. **D7** helpers take `PbblsIcon`: Tasks 8, 11, 16, 21, 22, 28, 31; `Visibility` and `LinkedProvider` untouched. **D8** marker, lint, bare literals, primary rows, CI step: Tasks 1–4, 33. **D9** per-part re-baselines: Tasks 15, 20, 27, 34. **D10** debug rows: Task 24.
- **Deviations, stated:** `pebble-glow-lg` lists no symbol (the spec counts it among the 17 rows that gain one; `trophy` can have one primary row). The `PbblsIconSizeTests` do not assert `relativeTo` pass-through (the generated view keeps its text style private; the overload is one line that forwards it). `SoulPill` is not touched (its only symbol is a wave row). The FAB draws at `tile`, the checkbox at `tile`; both are called out for the device pass.
- **Types:** `PebblesMenuItem.icon`, `SurfaceTile.icon`, `ProfileShortcutTile.icon`, `DataTile.icon`, `HomeShortcuts.tile(_ icon:)`, `Achievement.familySymbol: PbblsIcon`, `LogTimeline.icon`, `GlyphTabBar.icon(_:)`, `AppTab.icon`, `PbblsIconImage(_:_:relativeTo:style:label:)`, `IconRow.migrated: Surface[]`, `lintReplaces(root, manifest) -> Hit[]`, `coverage(manifest)` are used with the same names throughout.

## Lessons learned

(Written in Part 7, Task 36.)

Executed 2026-10-30 by subagent-driven development, one implementer per task or part and a reviewer per part; the stack is #1332 (PRs #1330 to #1337).

- **A fixed slot takes a bare image.** `PbblsIconImage` scales with its text style through `@ScaledMetric`; the band mark (`CollectionMark`, 14pt) and the glyph cell marks (`GlyphView`, 40% of a fixed `side`) sit in frames that do not scale, so at accessibility sizes the icon would overflow a bordered slot. Both draw `Image(icon.assetName).renderingMode(.template).resizable().scaledToFit()` with a comment saying why. The rule belongs in the decisions log (it is there) and in any later migration: a fixed frame around an icon is a signal to use the bare image.
- **`icons.json` is not round-trip stable.** Each `replaces` object sits on one line, so `JSON.stringify(m, null, 2)` rewrites 1100 lines. Task 1's note ("insert the line as text") was the path every edit took: Tasks 2 and 33 rewrote lines by name.
- **The lint's walk must not follow symlinks.** `statSync` on a directory loop throws `ELOOP` and kills the run; `readdirSync(withFileTypes:)` branching on `isDirectory()` / `isFile()` skips links and costs one syscall less per entry. The reviewer's probe found it; the plan's code did not have it.
- **The test helpers that build rows carry `migrated: []`**, and the one that takes a `status` parameter has to answer `shipped` with all three surfaces or the new rule throws inside an unrelated test. Adding a required field to the manifest means visiting every `row()` helper, not only the file under test.
- **The simulator is signed out**, so nothing behind the auth gate (the tab bar, a swipe action) can be looked at there. A temporary `ImageRenderer` test renders a view to a PNG in two minutes and answered the tab bar, the soul count, the band mark and the FAB; it cannot render a `TabView`'s UIKit bar nor a swipe action. The collections swipe delete stays unverified until the device pass.
- **Re-baselines per part cost a macOS runner each**: Part 3 re-recorded 25 Home PNGs (claim, empty, three-task, page variants, shortcuts, toast); Part 4's run went red on those same 25 plus 7 held-card ones until Part 3's bot commit was fast-forwarded and rebased up, so the label goes on a part only after the part below has its bot commit in. Part 5 re-recorded nothing (the plan expected `CollectionsReferences` and `PeopleReferences` to move; the tiles and empty states it changed have no reference), and Part 6 re-recorded 6 `PeopleReferences` PNGs the plan expected none of (the People page's "New soul" cell is `GlyphView.create`, now the pebbles plus-circle). The `action_required` holds after a bot push were approved through the API five at a time: 40 PNGs over three parts, three bot commits, three rebases.
- **Three reviewer catches worth keeping as habits:** a swiftlint `line_length` regression hides behind "0 serious"; a stale doc comment on a touched file is a one-word fix that belongs in the same commit; iterate the exported `SURFACES` instead of re-declaring the tuple in a test.
- **Owed after the device pass:** the sizes that read wrong (the FAB's ring in a disc, `DataTile` at 28 beside a subhead, the glyph pill at 20 beside `caption2`), whether `inline` stays 16 beside the soul count (R2), the swipe-action label, VoiceOver on the achievement badge.
