# Web icon migration — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every lucide icon that carries meaning in `apps/web` and `apps/admin` (outside `components/ui/`) with the Pebbles drawing iOS draws at the same place, keep the closed chrome list and the wave-brief gaps on lucide, name five icon sizes in rem, and arm the replaces lint for web with a form that reads lucide import clauses. Spec: `docs/superpowers/specs/2026-10-30-web-icon-migration-design.md` (D1–D13). Issue #1313, milestone M66.

**Architecture:** `packages/icons/scripts/lib/replaces.ts` gains a web form that reads only `import … from` / `export … from` declarations whose source is `lucide-react` (any spelling of a listed name is a hit; a Pebbles `Heart` or a `label="Settings"` is not). `icons.json` lists 28 web names on 21 rows. `apps/web/lib/icons/pbbls-icon.ts` exports `PBBLS_ICON_SIZE` (five Tailwind `size-*` classes) and the `PbblsIconComponent` type; call sites import a generated component from `@pbbls/icons` and size it with a class. Screens migrate route by route; the manifest flips `migrated: ["ios", "web"]` in the same commit that deletes the last listed import, so the lint goes live with the code it polices.

**Tech stack:** tsx + TypeScript strict + Vitest in `packages/icons`; Next.js 16 (App Router, Turbopack), React 19, Tailwind CSS 4, shadcn base-nova, Vitest (node env, `lib/**/*.test.ts`) in `apps/web` and `apps/admin`.

**Stack (gh stack, six parts = six PRs, bottom first):**

| Part | Branch | PR title | Tasks |
|---|---|---|---|
| 1 | `chore/1313-web-replaces-lint` | `chore(ui): read lucide imports in the replaces lint, name the web icon sizes` | 1–7 |
| 2 | `chore/1313-web-shell` | `chore(web): draw the nav, the path bar, the landing and the sandbox with pebbles icons` | 8–13 |
| 3 | `chore/1313-web-moments` | `chore(web): draw the moment, the record flow and carve with pebbles icons` | 14–21 |
| 4 | `chore/1313-web-profile` | `chore(web): draw profile, achievements, wallet and settings with pebbles icons` | 22–29 |
| 5 | `chore/1313-web-people-lab` | `chore(web): draw souls, collections, glyphs, connections and the lab with pebbles icons` | 30–35 |
| 6 | `chore/1313-admin-arm-lint` | `chore(admin): draw the admin's product icons with pebbles icons and arm the web lint` (`Resolves #1313`) | 36–40 |

Labels on every part: `chore`, `ui`, `web`, `no-lab-note` (a visual re-skin with no new behaviour; #1313 is a `chore`). Milestone: `M66 · Brand icons`. PR bodies for Parts 1–5 start `Part N of #1313`, never `Resolves`; Part 6 starts `Resolves #1313`. Never write a Kritik finding id in a body. Every commit message ends with the attribution trailer your session's instructions give.

**Before Part 1.** From the main checkout (`/Users/alexis/code/pbbls`), make a worktree on an updated `main` and give it the gitignored config (`project_worktree_gitignored_config`):

```bash
git fetch origin
git worktree add .worktrees/web-icons -b chore/1313-web-replaces-lint origin/main
cp apps/web/.env.local .worktrees/web-icons/apps/web/.env.local
test -f apps/admin/.env.local && cp apps/admin/.env.local .worktrees/web-icons/apps/admin/.env.local
cd .worktrees/web-icons && npm ci
```

Every command below runs from `.worktrees/web-icons` unless it says otherwise. The spec and this plan live on `docs/1313-web-icon-migration-spec` (PR #1338); if that PR has not merged, read them with `git show origin/docs/1313-web-icon-migration-spec:<path>`. Then confirm the inventory has not moved since the spec was written:

```bash
grep -rlE "from ['\"]lucide-react" apps/web apps/admin --include='*.ts' --include='*.tsx' \
  | grep -v -e /components/ui/ -e node_modules -e /.next/ | wc -l
```

Expected: `98`. Any other number means `main` gained or lost a lucide file since `ffbc1aff`: stop and re-run the spec's inventory before touching code.

**The part gate, run at the end of every part** (copy it whole; Part 6 adds the admin lines, Task 40):

```bash
npm run lint --workspace=apps/web          # exit 0; the problem count printed last must not grow over the part's base
npm run test --workspace=apps/web          # every test file passes
npm run build --workspace=apps/web         # "✓ Compiled successfully" then the route table: this is the type gate (web.yml does not typecheck)
npm run check --workspace=@pbbls/icons     # "<n> generated files are fresh"
npm run lint:replaces --workspace=@pbbls/icons   # Parts 1–5: "replaces: 27 rows, 49 names, 0 hits"; Part 6: "replaces: 27 rows, 77 names, 0 hits"
```

To compare the lint problem count with the base, run `npm run lint --workspace=apps/web 2>&1 | tail -3` on the part's branch and on the branch below it (`git stash` is shared between worktrees: compare by checking out the lower branch in a throwaway `git worktree add`, or read the lower part's PR check log). A warning hidden behind a green exit is a regression.

**The lint preview (per-part verification).** The lint stays vacuous for web until Part 6. To see what it will report, run the real lint with every web row migrated in memory only. Set `SCRATCH` to your session's scratchpad directory, then:

```bash
REPO=$(git rev-parse --show-toplevel)
cat > "$SCRATCH/web-lint-preview.mts" <<'EOF'
// What Part 6's armed lint would report today: rows with web names, migrated for web in memory only.
const repo = process.env.REPO ?? ""
const { loadManifest } = await import(`${repo}/packages/icons/scripts/lib/manifest.ts`)
const { lintReplaces } = await import(`${repo}/packages/icons/scripts/lib/replaces.ts`)
const manifest = loadManifest(`${repo}/packages/icons`)
for (const row of manifest.icons) {
  if (row.replaces.web.length > 0 && !row.migrated.includes("web")) row.migrated.push("web")
}
const hits = lintReplaces(repo, manifest).filter((h: { surface: string }) => h.surface === "web")
for (const h of hits) console.log(`${h.path}:${h.line}  ${h.name} → ${h.icon}`)
console.log(`${hits.length} web hits`)
EOF
REPO="$REPO" npm exec --workspace=@pbbls/icons -- tsx "$SCRATCH/web-lint-preview.mts" | tail -1
```

Expected last line after each part (counted on `ffbc1aff`): Part 1 `89 web hits`, Part 2 `68`, Part 3 `51`, Part 4 `19`, Part 5 `5`, Part 6 `0`. The lucide file count (the `grep … | wc -l` above): Part 1 `98`, Part 2 `90`, Part 3 `84`, Part 4 `73`, Part 5 `62`, Part 6 `60`. A preview line naming a file of the part just finished is a missed site.

**Stack hygiene, before any `gh stack push` or `gh stack submit`.** Someone (a reviewer, a bot) may have pushed onto a lower branch. Fast-forward every local branch whose remote is a strict descendant, then rebase the stack:

```bash
git fetch origin
cur=$(git branch --show-current)
for b in chore/1313-web-replaces-lint chore/1313-web-shell chore/1313-web-moments chore/1313-web-profile chore/1313-web-people-lab chore/1313-admin-arm-lint; do
  git rev-parse -q --verify "refs/heads/$b" >/dev/null || continue
  git rev-parse -q --verify "refs/remotes/origin/$b" >/dev/null || continue
  [ "$(git rev-parse "$b")" = "$(git rev-parse "origin/$b")" ] && continue
  if git merge-base --is-ancestor "$b" "origin/$b"; then
    if [ "$b" = "$cur" ]; then git merge --ff-only "origin/$b"; else git branch -f "$b" "origin/$b"; fi
    echo "fast-forwarded $b"
  fi
done
```

If any line printed `fast-forwarded`, run `gh stack rebase` before pushing. Never force over a remote commit you have not fast-forwarded in. Every `gh stack` command is non-interactive: branch names as arguments, `submit` always with `--auto`.

**Sizes and imports (spec D7, D8, D10).** A migrated icon is imported from `@pbbls/icons` and sized with `PBBLS_ICON_SIZE` from `@/lib/icons/pbbls-icon` (`inline` 16, `row` 20, `toolbar` 22, `tile` 28, `hero` 48 px). Never the `size` prop, never `fill-*`, `stroke-*` or `strokeWidth`; colour only through `text-*`. Drop the `aria-hidden` a migrated lucide site carried (the generated component hides itself). An icon may sit in a shadcn slot without a class only where the slot gives 16 or more (`Button` default, `lg`, `icon`, `icon-sm`, `icon-lg`, menu items, `AlertDialogMedia`); under `xs`, `sm` and `icon-xs` it carries `PBBLS_ICON_SIZE.inline`. Dead files (spec D12: `layout/MobileHeader`, `layout/Sidebar`, `path/GamificationBlocks`, `record/SoulPicker`, plus `record/CollectionSheet`, see Deviations) take the name change only and keep their classes and props. Kept lucide lines in a touched file are not touched.

**Line numbers** in a task are those of the file before the task's first edit (an import line that becomes three shifts everything below it). Every quoted line is unique in its file: match on the text, not the number.

**Component names used** (all exported by `packages/icons/react/index.ts` today): `CheckCircle CheckmarkDot PlusCircle Remove Camera CameraFocus Path People Collection Stack Person Slider PebbleDottedGlyph DrawDotted PebbleRound PebbleRoundLit1 PebbleFlat KarmaDot Sparkle Sparkles CalendarDate Trash PebbleGlowMd PebbleGlowLg Heart HeartActive HourglassEnd`. `Collection` is imported as `Collection as CollectionIcon` in the three files that import the `Collection` type (`pebble/MomentReadTiles.tsx`, `pebble/PebbleDetailTiles.tsx`, `record/CollectionSheet.tsx`).

**Dev servers for the visual passes:** web `npm run dev --workspace=apps/web -- --port 3100` (the main checkout owns 3000); admin `cd apps/admin && npx next dev --port 3101`. Check each route in light and dark, once with the browser's default font size set to 20 px (rem scaling), and at a phone width (390 px) where the bottom nav shows.

---

## Part 1 — the lint reads the import, the manifest, the brief, the sizes (`chore/1313-web-replaces-lint`)

The worktree's branch is already `chore/1313-web-replaces-lint`.

### Task 1: The web lint reads lucide import clauses (D6)

**Files:**
- Modify: `packages/icons/scripts/lib/replaces.ts` (whole file)
- Modify: `packages/icons/scripts/lib/replaces.test.ts`

- [ ] **Step 1: Write the failing tests.** In `replaces.test.ts`, change the import line

```ts
import { coverage, lintReplaces, type Hit } from "./replaces"
```

to

```ts
import { coverage, lintReplaces, lucideName, type Hit } from "./replaces"
```

and append at the end of the file (the existing three tests, including the four web expectations of `"matches R.drawable.<name> and a lucide import outside components/ui"`, stay as they are):

```ts
describe("lintReplaces · web reads the lucide import (web spec D6)", () => {
  const manifest = parseManifest({
    icons: [
      row("trash", { migrated: ["web"], replaces: { ios: [], android: [], web: ["Trash2"] } }),
      row("heart", { migrated: ["web"], replaces: { ios: [], android: [], web: ["Heart"] } }),
      row("slider", { migrated: ["web"], replaces: { ios: [], android: [], web: ["Settings"] } }),
      row("people", { migrated: ["web"], replaces: { ios: [], android: [], web: ["Users"] } }),
    ],
  })

  it("leaves a Pebbles name, a string and a comment alone in a file that keeps lucide", () => {
    const root = repo({
      "apps/web/components/a.tsx":
        'import { Tag } from "lucide-react"\nimport { Heart } from "@pbbls/icons"\nexport const A = () => <Heart />\n',
      "apps/web/components/b.tsx":
        'import { Sparkles } from "lucide-react"\n// Settings "Public profile" section, the old Trash2\nexport const B = () => <Tile label="Settings" icon={Sparkles} />\n',
      "apps/web/components/c.tsx":
        'import { Lock, type LucideIcon } from "lucide-react"\n// import { Trash2 } from "lucide-react"\nexport const icons: LucideIcon[] = [Lock]\n',
    })
    expect(lintReplaces(root, manifest)).toEqual([])
  })

  it("hits every spelling of a listed name, a re-export, and an import whose names it cannot read", () => {
    const root = repo({
      "apps/web/components/d.tsx": 'import { Trash2Icon } from "lucide-react"\n',
      "apps/web/components/e.tsx": 'import { LucideTrash2 } from "lucide-react"\n',
      "apps/web/components/f.tsx": 'import {\n  ChevronLeft,\n  Trash2 as Bin,\n} from "lucide-react"\n',
      "apps/web/components/g.ts": 'export { Trash2 } from "lucide-react"\n',
      "apps/web/components/h.tsx": 'import Trash from "lucide-react/icons/trash-2"\n',
      "apps/web/components/i.tsx": 'import * as Lucide from "lucide-react"\n',
      "apps/web/components/j.tsx": 'export async function load() {\n  return import("lucide-react")\n}\n',
      "apps/admin/components/k.tsx": 'export function Row() {\n  return null\n}\nimport { Users } from "lucide-react"\n',
    })
    expect(where(lintReplaces(root, manifest))).toEqual([
      "apps/admin/components/k.tsx:4",
      "apps/web/components/d.tsx:1",
      "apps/web/components/e.tsx:1",
      "apps/web/components/f.tsx:3",
      "apps/web/components/g.ts:1",
      "apps/web/components/h.tsx:1",
      "apps/web/components/i.tsx:1",
      "apps/web/components/j.tsx:2",
    ])
  })

  it("reports the listed name and its row at the specifier's line", () => {
    const root = repo({ "apps/web/components/f.tsx": 'import {\n  ChevronLeft,\n  Trash2 as Bin,\n} from "lucide-react"\n' })
    expect(lintReplaces(root, manifest)).toEqual([
      { surface: "web", icon: "trash", name: "Trash2", path: "apps/web/components/f.tsx", line: 3, text: "Trash2 as Bin," },
    ])
  })

  it("strips a leading Lucide and a trailing Icon, and reads LucideIcon as no icon", () => {
    expect(lucideName("Trash2")).toBe("Trash2")
    expect(lucideName("Trash2Icon")).toBe("Trash2")
    expect(lucideName("LucideTrash2")).toBe("Trash2")
    expect(lucideName("LucideIcon")).toBe("")
  })
})
```

- [ ] **Step 2: Run them to see them fail**

Run: `npm run test --workspace=@pbbls/icons -- replaces`
Expected: the four new tests FAIL (today's form hits `a.tsx:2`, `a.tsx:3`, `b.tsx:2`, `b.tsx:3`, `c.tsx:2`, misses `d`, `e`, `h`, `i`, `j`, and `lucideName` is not a function); the three existing tests pass.

- [ ] **Step 3: Implement.** Replace the whole of `packages/icons/scripts/lib/replaces.ts` with:

```ts
import { readdirSync, readFileSync, statSync } from "node:fs"
import { join, relative, sep } from "node:path"
import { SURFACES, type Manifest, type Surface } from "./manifest"

/**
 * The migration lint (iOS spec D8, web spec D6): for every row and every
 * surface in its `migrated`, the row's `replaces.<surface>` names must be gone
 * from that surface's tree.
 *
 * iOS and Android read literals: a hit is a bare literal, whatever surrounds it
 * (a ternary, a `return`, a `case`, a comment). No parsing, so a false hit is
 * fixed by renaming the string (R5), never by weakening the pattern.
 *
 * Web reads lucide imports (`lucideImportHits`): a TypeScript identifier reaches
 * a file only through an import, so the import clause is the whole evidence, and
 * a Pebbles `Heart` or a `label="Settings"` beside a kept lucide import is clean.
 */

/** One replaced name still used on a migrated surface: where it is and which row replaces it. */
export type Hit = { surface: Surface; icon: string; name: string; path: string; line: number; text: string }

/** A hit inside one file, before the lint adds the surface, the path and the line's text. */
export type Found = { icon: string; name: string; line: number }

type Tree = {
  roots: string[]
  extensions: string[]
  /** Repo-relative paths (files or directories, no trailing slash) the lint never reads. */
  skip: string[]
  /**
   * How a file is read. `literal`: every line against each listed name's regex
   * (`escaped` is regex-safe). `lucide-imports`: the file's lucide declarations only.
   */
  read: { kind: "literal"; pattern: (escaped: string) => RegExp } | { kind: "lucide-imports" }
}

const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")

/** Every surface's tree and how it is read. */
export const TREES: Record<Surface, Tree> = {
  ios: {
    // Only the app target: `apps/ios/PebblesWidget` stays on SF Symbols for now.
    roots: ["apps/ios/Pebbles"],
    extensions: [".swift"],
    skip: ["apps/ios/Pebbles/Theme/PbblsIcon.swift"],
    read: { kind: "literal", pattern: (n: string) => new RegExp(`"${n}"`) },
  },
  android: {
    roots: ["apps/android/app/src/main"],
    extensions: [".kt", ".xml"],
    skip: [],
    read: { kind: "literal", pattern: (n: string) => new RegExp(`R\\.drawable\\.${n}\\b|@drawable/${n}\\b`) },
  },
  web: {
    roots: ["apps/web", "apps/admin"],
    extensions: [".ts", ".tsx"],
    // shadcn primitives import lucide by design (package spec D10).
    skip: [
      "apps/web/components/ui",
      "apps/admin/components/ui",
      "apps/web/node_modules",
      "apps/admin/node_modules",
      "apps/web/.next",
      "apps/admin/.next",
    ],
    read: { kind: "lucide-imports" },
  },
}

/** A lucide source after `from`: the package, or a path inside it (`lucide-react/icons/trash-2`). */
const LUCIDE_FROM = /\bfrom\s*(["'])(lucide-react(?:\/[^"']*)?)\1/g
/** A dynamic lucide import: `import("lucide-react")`. */
const LUCIDE_DYNAMIC = /\bimport\s*\(\s*(["'])(lucide-react(?:\/[^"']*)?)\1/g
/** An `import` or `export` keyword that starts its line: where a declaration's clause begins. */
const KEYWORD = /^[ \t]*(?:import|export)\b/gm
/** One specifier between the braces: `Trash2`, `type LucideIcon`, `Trash2 as Bin`. */
const SPECIFIER = /(?:\btype\s+)?([A-Za-z_$][\w$]*)(?:\s+as\s+[A-Za-z_$][\w$]*)?/g
/** The `icon` of a hit that names no single icon: a deep, dynamic, namespace or default lucide import. */
export const ANY_ICON = "*"

/** `LucideTrash2` and `Trash2Icon` are `Trash2`; the `LucideIcon` type names no icon at all. */
export const lucideName = (imported: string): string => imported.replace(/^Lucide/, "").replace(/Icon$/, "")

/**
 * The web form (web spec D6). Every `import … from` / `export … from` whose
 * source is `lucide-react` or starts with `lucide-react/` is read, over as many
 * lines as it takes:
 * - a deep source, a namespace (`* as L`, `export *`) or a default binding is a
 *   hit by itself, at the declaration's line: the names it brings are not in
 *   the clause;
 * - otherwise each specifier's imported name (left of `as`, `type` dropped),
 *   stripped of a leading `Lucide` and a trailing `Icon`, is a hit at its own
 *   line when `listed` (name → row) has it.
 * A dynamic `import("lucide-react…")` is a hit by itself. A declaration starts
 * at a line-leading keyword, so a commented-out import is not read. Hits come
 * in line order.
 */
export function lucideImportHits(text: string, listed: ReadonlyMap<string, string>): Found[] {
  const lineAt = (offset: number): number => text.slice(0, offset).split("\n").length
  const clauseStarts = [...text.matchAll(KEYWORD)].map((k) => (k.index ?? 0) + k[0].length)
  const found: Found[] = []
  for (const m of text.matchAll(LUCIDE_FROM)) {
    const from = m.index ?? 0
    const source = m[2] ?? ""
    const start = clauseStarts.filter((s) => s <= from).at(-1)
    if (start === undefined) continue
    const clause = text.slice(start, from)
    // A quote or a `;` means the nearest keyword ends another statement: this
    // `from` sits in a comment or a string, not in a declaration.
    if (/["'`;]/.test(clause)) continue
    const line = lineAt(start)
    if (source !== "lucide-react") {
      found.push({ icon: ANY_ICON, name: source, line })
      continue
    }
    const open = clause.indexOf("{")
    const close = clause.lastIndexOf("}")
    const outside = (open === -1 ? clause : clause.slice(0, open) + clause.slice(close + 1))
      .replace(/\btype\b|,/g, " ")
      .trim()
    if (outside !== "") found.push({ icon: ANY_ICON, name: outside, line })
    if (open === -1) continue
    for (const s of clause.slice(open + 1, close).matchAll(SPECIFIER)) {
      const imported = s[1] ?? ""
      const name = lucideName(imported)
      const icon = listed.get(name)
      if (icon === undefined) continue
      found.push({ icon, name, line: lineAt(start + open + 1 + (s.index ?? 0) + s[0].indexOf(imported)) })
    }
  }
  for (const m of text.matchAll(LUCIDE_DYNAMIC)) {
    found.push({ icon: ANY_ICON, name: m[2] ?? "", line: lineAt(m.index ?? 0) })
  }
  return found.sort((a, b) => a.line - b.line)
}

const posix = (root: string, path: string): string => relative(root, path).split(sep).join("/")

function walk(dir: string, extensions: string[], skip: Set<string>, root: string, out: string[]): void {
  const entries = readdirSync(dir, { withFileTypes: true }).sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0))
  for (const entry of entries) {
    const path = join(dir, entry.name)
    if (skip.has(posix(root, path))) continue
    // Symlinks and anything that is neither a directory nor a file are skipped.
    if (entry.isDirectory()) walk(path, extensions, skip, root, out)
    else if (entry.isFile() && extensions.some((e) => entry.name.endsWith(e))) out.push(path)
  }
}

function files(root: string, tree: Tree): string[] {
  const out: string[] = []
  const skip = new Set(tree.skip)
  for (const r of tree.roots) {
    const dir = join(root, r)
    try {
      statSync(dir)
    } catch {
      continue
    }
    walk(dir, tree.extensions, skip, root, out)
  }
  return out
}

/** The literal form: each listed name's regex, tested on every line. */
function literalScan(names: { icon: string; name: string }[], pattern: (escaped: string) => RegExp): (text: string) => Found[] {
  const checks = names.map((n) => ({ ...n, re: pattern(escape(n.name)) }))
  return (text) => {
    const found: Found[] = []
    text.split("\n").forEach((line, i) => {
      for (const c of checks) {
        if (c.re.test(line)) found.push({ icon: c.icon, name: c.name, line: i + 1 })
      }
    })
    return found
  }
}

/** Every hit in `root` (the repository), in surface order (`SURFACES`), then file order, then line order. */
export function lintReplaces(root: string, manifest: Manifest): Hit[] {
  const hits: Hit[] = []
  for (const surface of SURFACES) {
    const names = manifest.icons.flatMap((row) =>
      row.migrated.includes(surface) ? row.replaces[surface].map((name) => ({ icon: row.name, name })) : [],
    )
    if (names.length === 0) continue
    const tree = TREES[surface]
    const read = tree.read
    const listed = new Map(names.map((n): [string, string] => [n.name, n.icon]))
    const scan = read.kind === "literal" ? literalScan(names, read.pattern) : (text: string) => lucideImportHits(text, listed)
    for (const path of files(root, tree)) {
      const text = readFileSync(path, "utf8")
      const lines = text.split("\n")
      for (const f of scan(text)) {
        hits.push({ surface, icon: f.icon, name: f.name, path: posix(root, path), line: f.line, text: (lines[f.line - 1] ?? "").trim() })
      }
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

`walk`, `files` and `coverage` are unchanged from today; the iOS and Android forms read exactly as before (`literalScan` is the old per-line loop).

- [ ] **Step 4: Run the tests**

Run: `npm run test --workspace=@pbbls/icons -- replaces`
Expected: PASS, 7 tests.

- [ ] **Step 5: Typecheck and run it on the repo**

Run: `npm run build --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run lint:replaces --workspace=@pbbls/icons`
Expected: `tsc` clean; every test file green; `replaces: 27 rows, 49 names, 0 hits` (iOS is live, web has no listed name yet).

- [ ] **Step 6: Commit**

```bash
git add packages/icons/scripts/lib/replaces.ts packages/icons/scripts/lib/replaces.test.ts
git commit -m "chore(ui): read lucide import clauses in the web form of the replaces lint"
```

### Task 2: `replaces.web` per D5, edited as text

**Files:**
- Modify: `packages/icons/icons.json` (16 `replaces` lines)

`icons.json` is not round-trip stable (each `replaces` object sits on one line; `JSON.stringify(m, null, 2)` rewrites about 1100 lines). Edit the lines by row name, as text.

- [ ] **Step 1: Edit the 16 lines.** From `packages/icons`:

```bash
node -e '
const fs = require("node:fs")
const web = {
  "checkmark-dot": [],
  "plus-circle-dot": [],
  "plus-circle": ["Plus", "CirclePlus"],
  "remove": ["UserRoundMinus"],
  "path": ["Route"],
  "people": ["Users", "MessagesSquare"],
  "collection": ["Layers", "FolderOpen"],
  "stack": ["Gem"],
  "pebble-dotted-glyph": ["Fingerprint", "PenTool"],
  "draw-dotted": ["Pencil"],
  "pebble-round": ["Stone", "Shell", "CircleDashed", "CircleDot"],
  "pebble-flat": ["Clock"],
  "karma-dot": ["Sparkle"],
  "sparkles": ["Sparkles"],
  "pebble-glow-md": ["Trophy"],
  "sparkle": ["FlaskConical"],
}
const lines = fs.readFileSync("icons.json", "utf8").split("\n")
for (const [name, names] of Object.entries(web)) {
  const at = lines.findIndex((l) => l === `      "name": "${name}",`)
  const r = at + 6
  if (at < 0 || !/^      "replaces": \{ .*"web": \[[^\]]*\] \}$/.test(lines[r] ?? "")) throw new Error(`${name}: no replaces line at ${r + 1}`)
  lines[r] = lines[r].replace(/"web": \[[^\]]*\]/, `"web": [${names.map((n) => JSON.stringify(n)).join(", ")}]`)
}
fs.writeFileSync("icons.json", lines.join("\n"))
'
git diff --stat icons.json
```

Expected: `1 file changed, 16 insertions(+), 16 deletions(-)`. The 16 lines now read (check `git diff icons.json` against this list):

```
checkmark-dot        "replaces": { "ios": ["checkmark"], "android": ["ic_check"], "web": [] }
plus-circle-dot      "replaces": { "ios": [], "android": [], "web": [] }
plus-circle          "replaces": { "ios": ["plus", "plus.circle"], "android": [], "web": ["Plus", "CirclePlus"] }
remove               "replaces": { "ios": ["xmark.circle.fill", "person.badge.minus"], "android": [], "web": ["UserRoundMinus"] }
path                 "replaces": { "ios": ["square.stack", "line.3.horizontal.decrease"], "android": [], "web": ["Route"] }
people               "replaces": { "ios": ["person.2", "person.3", "person.2.badge.plus", "person.3.fill", "bubble.left.and.bubble.right.fill"], "android": ["ic_people", "ic_person_pair"], "web": ["Users", "MessagesSquare"] }
collection           "replaces": { "ios": ["circle.hexagongrid", "square.stack.3d.up"], "android": ["ic_pebble_collection"], "web": ["Layers", "FolderOpen"] }
stack                "replaces": { "ios": ["circle.grid.3x3"], "android": [], "web": ["Gem"] }
pebble-dotted-glyph  "replaces": { "ios": ["scribble", "pencil.and.outline"], "android": ["ic_scribble"], "web": ["Fingerprint", "PenTool"] }
draw-dotted          "replaces": { "ios": ["pencil"], "android": [], "web": ["Pencil"] }
pebble-round         "replaces": { "ios": ["fossil.shell", "drop.fill", "circle.dashed", "circle.inset.filled", "square"], "android": ["ic_fossil_shell"], "web": ["Stone", "Shell", "CircleDashed", "CircleDot"] }
pebble-flat          "replaces": { "ios": ["clock"], "android": [], "web": ["Clock"] }
karma-dot            "replaces": { "ios": ["sparkle", "sparkles"], "android": [], "web": ["Sparkle"] }
sparkles             "replaces": { "ios": ["square.grid.3x3"], "android": [], "web": ["Sparkles"] }
pebble-glow-md       "replaces": { "ios": ["trophy"], "android": [], "web": ["Trophy"] }
sparkle              "replaces": { "ios": ["lightbulb.max"], "android": [], "web": ["FlaskConical"] }
```

The seven rows whose web list does not change keep theirs, already right: `check-circle` `CircleCheckBig`, `camera` `ImagePlus`, `person` `CircleUser`, `slider` `Settings`, `calendar-date` `CalendarDays`, `trash` `Trash2`, `heart` `Heart`.

- [ ] **Step 2: Count**

Run: `node -e 'const m=require("./icons.json");console.log(m.icons.flatMap(r=>r.replaces.web).length, m.icons.filter(r=>r.replaces.web.length>0).length)'`
Expected: `28 21`.

- [ ] **Step 3: Lint, test, the lint stays vacuous for web**

Run: `npm run lint --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons && npm run lint:replaces --workspace=@pbbls/icons`
Expected: green (`manifest.test.ts`'s "lists bare symbol names, each on one row" guards against a double listing); `check` fresh (`replaces` reaches no emitter); `replaces: 27 rows, 49 names, 0 hits`.

- [ ] **Step 4: Commit**

```bash
git add packages/icons/icons.json
git commit -m "chore(ui): record which lucide icon each pebbles icon replaces on web"
```

### Task 3: The web lines in the package docs

**Files:**
- Modify: `packages/icons/CLAUDE.md`

- [ ] **Step 1: The commands row.** Replace

```
| `npm run lint:replaces --workspace=@pbbls/icons` | fails when a surface listed in a row's `migrated` still names one of that row's `replaces` (iOS: the bare quoted literal anywhere under `apps/ios/Pebbles/`) |
```

with

```
| `npm run lint:replaces --workspace=@pbbls/icons` | fails when a surface listed in a row's `migrated` still names one of that row's `replaces` (iOS: the bare quoted literal anywhere under `apps/ios/Pebbles/`; web: a listed name in a `lucide-react` import or re-export under `apps/web` or `apps/admin` outside `components/ui/`, in any spelling — `Trash2Icon`, `LucideTrash2`, `Trash2 as Bin` — and any deep, dynamic, namespace or default lucide import) |
```

- [ ] **Step 2: When `Icons` is red.** Replace

```
A red `replaces` step names a file and line that still draws a replaced symbol: migrate the call site to `PbblsIcon`, or rename the string if it is not a symbol at all; never widen the pattern.
```

with

```
A red `replaces` step names a file and line that still draws a replaced symbol: migrate the call site to `PbblsIcon`, or rename the string if it is not a symbol at all; never widen the pattern. On web the line is an import specifier: import the Pebbles component from `@pbbls/icons` instead and size it with `PBBLS_ICON_SIZE` (`apps/web/lib/icons/pbbls-icon.ts`); never edit the import scan to let it through.
```

- [ ] **Step 3: Commit**

```bash
git add packages/icons/CLAUDE.md
git commit -m "docs(ui): document the web form of the replaces lint"
```

### Task 4: The web gaps join the chrome wave brief (D2)

**Files:**
- Modify: `packages/icons/waves/2026-11-chrome/brief.md` (append)

- [ ] **Step 1: Append** at the end of the file (after line 14, `14. market — …`):

```markdown

## Web (#1313)

Web and admin still draw these as lucide icons (web migration spec D2, `docs/superpowers/specs/2026-10-30-web-icon-migration-design.md`). Judge them at 16 px beside 14 px text and at 20 px in a settings row.

Already briefed above, with the lucide names they also replace: 1 again (`RotateCcw`, `RefreshCw`), 4 upvote (`ArrowUpCircle`), 5 something went wrong (`AlertCircle`), 6 document (`FileText`, `Scale`), 7 privacy (`HeartHandshake`), 8 secret and 11 locked (`Lock`), 9 private (`UsersRound`), 10 public (`Globe` in the visibility menu), 12 invite (`UserRoundPlus`), 13 life domain (`Compass`, `Tag`).

15. block — stop someone from reaching you (`Ban`)
16. language — the tongue the app speaks (`Globe` in Appearance and the locale switcher); must read apart from 10
17. appearance — light, dark, follow the device (`Sun`, `Moon`, `Monitor`): a trio drawn together or not at all
18. colour world — the palette the app wears (`Palette`)
19. bounce — regularity over the last 28 days, a rhythm kept (`CirclePile`, `Waves`)
```

The brief stays under fifty lines (`wc -l` prints 32).

- [ ] **Step 2: Lint** (the icons lint checks each row's wave folder exists; an edited brief is not read): `npm run lint --workspace=@pbbls/icons`. Expected: `0 errors`.

- [ ] **Step 3: Commit**

```bash
git add packages/icons/waves/2026-11-chrome/brief.md
git commit -m "docs(ui): brief the web icons the chrome wave must draw"
```

### Task 5: `PBBLS_ICON_SIZE` and `PbblsIconComponent` (D7, D10)

**Files:**
- Create: `apps/web/lib/icons/pbbls-icon.ts`
- Create: `apps/web/lib/icons/pbbls-icon.test.ts`

- [ ] **Step 1: Write the failing test** `apps/web/lib/icons/pbbls-icon.test.ts`:

```ts
import { describe, expect, it } from "vitest"
import { PBBLS_ICON_SIZE } from "./pbbls-icon"

// A Tailwind `size-<n>` class is n × 4 px at the default 16 px root. The
// expected values are the iOS `PbblsIconSize` raw values
// (apps/ios/Pebbles/Theme/Icon+Pebbles.swift), so the two scales cannot drift.
const px = (cls: string): number => Number(/^size-(\d+(?:\.\d+)?)$/.exec(cls)?.[1]) * 4

describe("PBBLS_ICON_SIZE", () => {
  it("names the five sizes, smallest first", () => {
    expect(Object.keys(PBBLS_ICON_SIZE)).toEqual(["inline", "row", "toolbar", "tile", "hero"])
  })

  it("draws each at the iOS value, from the 16 px floor", () => {
    const sizes = Object.fromEntries(Object.entries(PBBLS_ICON_SIZE).map(([name, cls]) => [name, px(cls)]))
    expect(sizes).toEqual({ inline: 16, row: 20, toolbar: 22, tile: 28, hero: 48 })
    expect(Math.min(...Object.values(sizes))).toBe(16)
  })
})
```

- [ ] **Step 2: Run it**: `npm run test --workspace=apps/web -- pbbls-icon.test`. Expected: FAIL, `./pbbls-icon` cannot be resolved.

- [ ] **Step 3: Implement** `apps/web/lib/icons/pbbls-icon.ts`:

```ts
import type { ComponentType } from "react"
import type { PbblsIconProps } from "@pbbls/icons"

/**
 * The five sizes a Pebbles icon draws at (web icon migration spec D7), as
 * Tailwind classes in rem: 16, 20, 22, 28 and 48 px at the default root, the
 * iOS `PbblsIconSize` values. rem follows the reader's browser font size the
 * way Dynamic Type does on iOS; it does not follow the line, so `inline` is 16
 * beside `text-xs` and beside `text-base` alike.
 *
 * Size an icon with a class, never with the generated `size` prop: `size`
 * writes `width`/`height` attributes, which the shadcn slots'
 * `[&_svg:not([class*='size-'])]:size-*` rule overrides, while a `size-*`
 * class both sizes the icon and opts it out of the slot default.
 *
 * - `inline`: on a line of text (pills, counters, captions, labelled buttons,
 *   menu items, the Lab timeline), and in icon-only controls of 24–32 px.
 * - `row`: the lead of a list or settings row, a card header, a tile trigger,
 *   the bottom nav, a shortcut tile, icon-only controls of 36 px, and beside
 *   display text (`text-xl` and up).
 * - `toolbar`: icon-only controls of 40 px and more.
 * - `tile`: an icon in a disc or slot of 48 px and more that is not an empty state.
 * - `hero`: empty states and placeholders in a 96 px slot, the celebration,
 *   the Record photo hero.
 *
 * 16 is the floor. Colour only through `text-*`; never `fill-*`, `stroke-*` or
 * `strokeWidth` (the 1.75 stroke is the DNA, a filled state is a twin):
 * `<PebbleRound className={cn(PBBLS_ICON_SIZE.inline, "text-primary")} />`.
 */
export const PBBLS_ICON_SIZE = {
  inline: "size-4",
  row: "size-5",
  toolbar: "size-5.5",
  tile: "size-7",
  hero: "size-12",
} as const

export type PbblsIconSize = keyof typeof PBBLS_ICON_SIZE

/** A generated Pebbles icon, for maps and props whose every value is one (spec D10). */
export type PbblsIconComponent = ComponentType<PbblsIconProps>
```

- [ ] **Step 4: Run it**: `npm run test --workspace=apps/web -- pbbls-icon.test`. Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/icons/pbbls-icon.ts apps/web/lib/icons/pbbls-icon.test.ts
git commit -m "chore(web): name the five sizes a pebbles icon draws at"
```

### Task 6: The generated components' accessibility and class contract (D7, D8)

**Files:**
- Modify: `apps/web/lib/icons/pbbls-icons.test.ts`

- [ ] **Step 1: Add the tests.** Inside `describe("@pbbls/icons", …)`, after the existing `it("renders a generated icon", …)`, add:

```ts
  it("names itself an image only when labelled (spec D8)", () => {
    const markup = renderToStaticMarkup(createElement(CheckCircle, { "aria-label": "Done" }))
    expect(markup).toContain('role="img"')
    expect(markup).toContain('aria-label="Done"')
    expect(markup).not.toContain("aria-hidden")
    expect(markup).toContain('focusable="false"')
  })

  it("puts a className on the svg, so a size class opts out of the shadcn slot default (spec D7)", () => {
    const markup = renderToStaticMarkup(createElement(CheckCircle, { className: "size-4 text-primary" }))
    expect(markup).toContain('class="size-4 text-primary"')
  })
```

- [ ] **Step 2: Run them**: `npm run test --workspace=apps/web -- pbbls-icons.test`. Expected: PASS, 3 tests (they guard the generated contract in `packages/icons/react/*.tsx`; a red run means the emitter changed, fix the emitter, never this test).

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/icons/pbbls-icons.test.ts
git commit -m "test(web): pin the pebbles icons' label and class contract"
```

### Task 7: Part 1 gate and PR

- [ ] **Step 1: Package gate**: `npm run lint --workspace=@pbbls/icons && npm run build --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons && npm run lint:replaces --workspace=@pbbls/icons`. Expected: green; `replaces: 27 rows, 49 names, 0 hits`.
- [ ] **Step 2: Web gate**:

```bash
npm run lint --workspace=apps/web
npm run test --workspace=apps/web
npm run build --workspace=apps/web
```

Expected: eslint exit 0 with the same problem count as `main`; Vitest one test file more than `main` (`pbbls-icon.test.ts`), all passing; `next build` compiles (the helper imports only types from `@pbbls/icons`, so nothing is bundled yet).
- [ ] **Step 3: Preview**: the lint preview block (header) prints `89 web hits`; the lucide file count prints `98`.
- [ ] **Step 4: Stack and PR.** Stack hygiene (header), then:

```bash
gh stack init chore/1313-web-replaces-lint
gh stack submit --auto --open
cat > "$SCRATCH/pr-part1.md" <<'EOF'
Part 1 of #1313

Spec: `docs/superpowers/specs/2026-10-30-web-icon-migration-design.md` (D2, D5, D6, D7, D8). Plan: `docs/superpowers/plans/2026-10-30-web-icon-migration.md`, Tasks 1–7.

- **The web lint reads the lucide import** (`packages/icons/scripts/lib/replaces.ts`): only `import … from` / `export … from "lucide-react"` declarations are read. Each specifier's imported name, stripped of `Lucide` and `Icon`, is a hit when a migrated row lists it; a deep, dynamic, namespace or default lucide import is a hit by itself. A Pebbles `Heart` or a `label="Settings"` beside a kept lucide import is no longer a false hit. iOS and Android forms unchanged.
- **`icons.json`**: `replaces.web` now lists 28 names on 21 rows; `Check` leaves `checkmark-dot` (the selection mark stays chrome), `CirclePlus` moves to `plus-circle`. No row is migrated for web yet, so the lint still prints `replaces: 27 rows, 49 names, 0 hits`; Part 6 arms it.
- **Sizes**: `apps/web/lib/icons/pbbls-icon.ts` exports `PBBLS_ICON_SIZE` (`inline` 16, `row` 20, `toolbar` 22, `tile` 28, `hero` 48 px, in rem) and `PbblsIconComponent`.
- **Wave brief**: `## Web (#1313)` in `packages/icons/waves/2026-11-chrome/brief.md`, five new figures.

Nothing renders differently in this part.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
gh pr edit "$(gh pr view --json number -q .number)" \
  --title "chore(ui): read lucide imports in the replaces lint, name the web icon sizes" \
  --add-label chore,ui,web,no-lab-note --milestone "M66 · Brand icons" --body-file "$SCRATCH/pr-part1.md"
```

### Visual verification — Part 1

None: no route renders differently. The reviewer reads the lint tests and the 16-line `icons.json` diff.

---

## Part 2 — the shell: nav, path bar, landing, sandbox (`chore/1313-web-shell`)

`gh stack add chore/1313-web-shell` from Part 1's branch.

### Task 8: The nav carries Pebbles icons (D10, D11)

**Files:**
- Modify: `apps/web/lib/config/navigation.ts`
- Modify: `apps/web/components/layout/BottomNav.tsx:41`
- Modify: `apps/web/components/layout/Sidebar.tsx:5,66` (dead, D12)
- Modify: `apps/web/components/layout/MobileHeader.tsx:5,40` (dead, D12)

- [ ] **Step 1: `navigation.ts`.** Replace lines 1–9

```ts
import { Route, CirclePlus, FolderOpen, Users, Fingerprint, FlaskConical } from "lucide-react"

export type NavItemKey = "path" | "record" | "collections" | "souls" | "glyphs" | "lab"

export type NavItem = {
  key: NavItemKey
  href: string
  icon: typeof Route
}
```

with

```ts
import { Collection, Path, PebbleDottedGlyph, People, PlusCircle, Sparkle } from "@pbbls/icons"
import type { PbblsIconComponent } from "@/lib/icons/pbbls-icon"

export type NavItemKey = "path" | "record" | "collections" | "souls" | "glyphs" | "lab"

export type NavItem = {
  key: NavItemKey
  href: string
  /** Drawn plain: the nav marks the current entry by colour and `aria-current`, never by an `active` twin (spec D11). */
  icon: PbblsIconComponent
}
```

and lines 16–21

```ts
  { key: "path",        href: "/path",        icon: Route },
  { key: "record",      href: "/record",      icon: CirclePlus },
  { key: "collections", href: "/collections", icon: FolderOpen },
  { key: "souls",       href: "/souls",       icon: Users },
  { key: "glyphs",      href: "/glyphs",      icon: Fingerprint },
  { key: "lab",         href: "/lab",         icon: FlaskConical },
```

with

```ts
  { key: "path",        href: "/path",        icon: Path },
  { key: "record",      href: "/record",      icon: PlusCircle },
  { key: "collections", href: "/collections", icon: Collection },
  { key: "souls",       href: "/souls",       icon: People },
  { key: "glyphs",      href: "/glyphs",      icon: PebbleDottedGlyph },
  { key: "lab",         href: "/lab",         icon: Sparkle },
```

- [ ] **Step 2: `BottomNav.tsx`.** After line 7 (`import { useNavItems } from "@/lib/hooks/useNavItems"`) add `import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"`. Line 41 `<Icon className="size-5" />` becomes `<Icon className={PBBLS_ICON_SIZE.row} />`.

- [ ] **Step 3: The dead `Sidebar.tsx`.** Line 5 `import { CircleUser } from "lucide-react"` becomes `import { Person } from "@pbbls/icons"`; line 66 `<CircleUser className="size-4" />` becomes `<Person className="size-4" />`. Line 44 (`<Icon className="size-4" />`) stays: the nav items are Pebbles now and the dead sidebar keeps its class.

- [ ] **Step 4: The dead `MobileHeader.tsx`.** Line 5 `import { CircleUser } from "lucide-react"` becomes `import { Person } from "@pbbls/icons"`; line 40 `<CircleUser className="size-5" />` becomes `<Person className="size-5" />`.

- [ ] **Step 5: Typecheck and commit.** `npm run build --workspace=apps/web` compiles (the first build that bundles `@pbbls/icons`, D13; no `next.config.ts` change). Then:

```bash
git add apps/web/lib/config/navigation.ts apps/web/components/layout/BottomNav.tsx apps/web/components/layout/Sidebar.tsx apps/web/components/layout/MobileHeader.tsx
git commit -m "chore(web): draw the nav with pebbles icons"
```

### Task 9: The path bar

**Files:**
- Modify: `apps/web/components/path/PathBottomBar.tsx:4,24,61,70`

- [ ] **Step 1: Imports.** Line 4 `import { CircleUser, CirclePile, Sparkle } from "lucide-react"` becomes:

```ts
import { CirclePile } from "lucide-react"
import { KarmaDot, Person } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

- [ ] **Step 2: The stat icon.** Line 24

```tsx
      <Icon className="size-3.5 self-center text-(--spine-ink-2)" aria-hidden />
```

becomes

```tsx
      <Icon className={cn(PBBLS_ICON_SIZE.inline, "self-center text-(--spine-ink-2)")} />
```

(`cn` is already imported. The kept lucide `CirclePile` shares this class and moves from 14 to 16 with the karma dot, so the pair stays matched; lucide hides itself by default too.)

- [ ] **Step 3: The profile fallback** in the 40 px link. Line 61 `<CircleUser className="size-6" />` becomes `<Person className={PBBLS_ICON_SIZE.toolbar} />`.

- [ ] **Step 4: Karma.** Line 70 `<Stat icon={Sparkle} value={karmaLoading ? "—" : karma} label={t("stats.karma")} />` becomes `<Stat icon={KarmaDot} value={karmaLoading ? "—" : karma} label={t("stats.karma")} />`.

- [ ] **Step 5: Commit**

```bash
git add apps/web/components/path/PathBottomBar.tsx
git commit -m "chore(web): draw the path bar's profile and karma with pebbles icons"
```

### Task 10: `GamificationBlock` takes any icon; the dead blocks migrate (D10, D12)

**Files:**
- Modify: `apps/web/components/path/GamificationBlock.tsx:3-4,24`
- Modify: `apps/web/components/path/GamificationBlocks.tsx:3,36,76` (dead)

- [ ] **Step 1: The type.** In `GamificationBlock.tsx`, lines 3–4

```ts
import type { LucideIcon } from "lucide-react"
import type { ReactNode } from "react"
```

become

```ts
import type { ComponentType, ReactNode } from "react"
```

and line 24 `  icon: LucideIcon` becomes

```ts
  /** Structural, like `DataTile`'s: the sandbox hands it lucide `Flame`, the blocks Pebbles icons (spec D10). */
  icon: ComponentType<{ className?: string; "aria-hidden"?: boolean }>
```

- [ ] **Step 2: The dead blocks.** In `GamificationBlocks.tsx`, line 3 `import { CirclePile, Sparkle, Stone } from "lucide-react"` becomes:

```ts
import { CirclePile } from "lucide-react"
import { KarmaDot, PebbleRound } from "@pbbls/icons"
```

line 36 `        icon={Stone}` becomes `        icon={PebbleRound}`, line 76 `        icon={Sparkle}` becomes `        icon={KarmaDot}`.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/path/GamificationBlock.tsx apps/web/components/path/GamificationBlocks.tsx
git commit -m "chore(web): let the gamification block take a pebbles icon"
```

### Task 11: The profile's settings button and the landing

**Files:**
- Modify: `apps/web/app/profile/page.tsx:5,47`
- Modify: `apps/web/components/landing/LandingPage.tsx:6,14,28-32,85-88`

- [ ] **Step 1: Profile.** Line 5 `import { Settings } from "lucide-react"` becomes `import { Slider } from "@pbbls/icons"`; line 47 `      <Settings />` becomes `      <Slider />` (the `icon` button slot draws it at 16, `inline`; the link keeps its `aria-label`).

- [ ] **Step 2: Landing imports.** Line 6 `import { Clock, Sparkles, Route } from "lucide-react"` becomes `import { Path, PebbleFlat, Sparkles } from "@pbbls/icons"`. Line 14 `import type { LucideIcon } from "lucide-react"` becomes:

```ts
import { PBBLS_ICON_SIZE, type PbblsIconComponent } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

- [ ] **Step 3: The feature map.** Lines 28–32

```ts
const FEATURE_ICONS: Record<FeatureKey, LucideIcon> = {
  record: Clock,
  enrich: Sparkles,
  grow: Route,
}
```

become

```ts
const FEATURE_ICONS: Record<FeatureKey, PbblsIconComponent> = {
  record: PebbleFlat,
  enrich: Sparkles,
  grow: Path,
}
```

- [ ] **Step 4: The feature icon.** Lines 85–88

```tsx
              <Icon
                aria-hidden="true"
                className="mt-0.5 size-5 shrink-0 text-primary"
              />
```

become

```tsx
              <Icon className={cn(PBBLS_ICON_SIZE.row, "mt-0.5 shrink-0 text-primary")} />
```

- [ ] **Step 5: Commit**

```bash
git add apps/web/app/profile/page.tsx apps/web/components/landing/LandingPage.tsx
git commit -m "chore(web): draw the settings button and the landing features with pebbles icons"
```

### Task 12: The sandbox, and the sizes specimen

**Files:**
- Modify: `apps/web/lib/design/specimens.ts:64`
- Modify: `apps/web/components/sandbox/design/ComponentsSection.tsx`
- Modify: `apps/web/components/sandbox/design/primitives/PrimitivesSection.tsx:4,60,64`
- Modify: `apps/web/components/sandbox/design/views/ProfileView.tsx:4,42`

- [ ] **Step 1: Register the specimen.** In `specimens.ts`, after line 64 (`  { id: "empty-state", … },`) add:

```ts
  { id: "pbbls-icon-sizes", name: "Pebbles icon sizes", source: "lib/icons/pbbls-icon.ts", section: "components", m3: "bridged" },
```

(`specimens.test.ts` checks the source exists and the id is unique.)

- [ ] **Step 2: `ComponentsSection.tsx` imports.** Line 3 `import { Flame, Gem, Leaf, Settings, Sparkles, UserRound } from "lucide-react"` becomes:

```ts
import { Flame, Leaf, UserRound } from "lucide-react"
import { KarmaDot, PebbleRound, People, Slider, Sparkles, Stack, Trash } from "@pbbls/icons"
import { PBBLS_ICON_SIZE, type PbblsIconComponent } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

After line 34 (`import { StoneSpecimens } from "./StoneSpecimens"`) and its blank line, before `function markOf`, add:

```tsx
/** The four drawings the sizes specimen shows at every size (spec D7). */
const SIZE_SAMPLES: readonly { key: string; Icon: PbblsIconComponent }[] = [
  { key: "pebble-round", Icon: PebbleRound },
  { key: "people", Icon: People },
  { key: "karma-dot", Icon: KarmaDot },
  { key: "trash", Icon: Trash },
]
const SIZE_TEXT = ["text-xs", "text-sm", "text-base"] as const

```

- [ ] **Step 3: The listed names** (line numbers from before Step 2). Line 83 `          icon={Gem}` becomes `          icon={Stack}` (Flame at line 76 stays: a placeholder, D4). Line 167 `        <DataTile value={128} icon={Sparkles} label="Pebbles" />` stays textually the same (now the Pebbles `Sparkles`). Line 173 `        <ShortcutTile href="/settings" icon={Settings} label="Settings" />` becomes `        <ShortcutTile href="/settings" icon={Slider} label="Settings" />`. `Leaf` and `UserRound` stay.

- [ ] **Step 4: The sizes specimen.** After the `empty-state` specimen's closing `</Specimen>` (the last one, before `    </DesignSection>`), add:

```tsx

      <Specimen
        id="pbbls-icon-sizes"
        note="Four drawings at the five PBBLS_ICON_SIZE classes beside 12, 14 and 16 px text. The classes are rem: every size follows the browser's font size, none follows the line, so inline is 16 beside text-xs and text-base alike."
        className="flex-col"
      >
        {SIZE_TEXT.map((text) => (
          <div key={text} className={cn("flex flex-col gap-2", text)}>
            {Object.entries(PBBLS_ICON_SIZE).map(([size, sizeClass]) => (
              <div key={size} className="flex items-center gap-3">
                {SIZE_SAMPLES.map(({ key, Icon }) => (
                  <Icon key={key} className={sizeClass} />
                ))}
                <span>
                  {size} · {text}
                </span>
              </div>
            ))}
          </div>
        ))}
      </Specimen>
```

- [ ] **Step 5: `PrimitivesSection.tsx`.** Line 4 `import { Plus } from "lucide-react"` becomes `import { PlusCircle } from "@pbbls/icons"`; line 60 `                    <Plus />` becomes `                    <PlusCircle />`; line 64 `                  <Plus data-icon="inline-start" />` becomes `                  <PlusCircle data-icon="inline-start" />`. No class: the specimens show what each `Button` slot does to an unclassed icon (12 under `icon-xs` and `xs`, 14 under `sm`), which is the rule call sites follow (D7).

- [ ] **Step 6: `ProfileView.tsx`.** Line 4 `import { Settings } from "lucide-react"` becomes `import { Slider } from "@pbbls/icons"`; line 42 `              <Settings />` becomes `              <Slider />`.

- [ ] **Step 7: Test, commit.** `npm run test --workspace=apps/web -- specimens` passes. Then:

```bash
git add apps/web/lib/design/specimens.ts apps/web/components/sandbox/design
git commit -m "chore(web): draw the sandbox with pebbles icons and show the five sizes"
```

### Task 13: Part 2 gate, bundle check, PR

- [ ] **Step 1: Gate**

```bash
npm run lint --workspace=apps/web
npm run test --workspace=apps/web
npm run build --workspace=apps/web
npm run check --workspace=@pbbls/icons
npm run lint:replaces --workspace=@pbbls/icons
```

Expected: eslint exit 0, problem count unchanged from Part 1; Vitest green; `next build` compiles; `… generated files are fresh`; `replaces: 27 rows, 49 names, 0 hits`.

- [ ] **Step 2: Tree-shaking (D13).** After the build:

```bash
grep -rlF "M 7.0 3.2 C 9.6 5.8 14.0 6.4 17.4 3.0" apps/web/.next/static/chunks | wc -l
grep -rlF "M6.2 16.1 C8.2 16 9.4 16.8 9.4 18.4" apps/web/.next/static/chunks | wc -l
```

Expected: `0` (Watermelon, imported nowhere, is not bundled) then `1` or more (Path, drawn by the nav, is: the grep would have found Watermelon). A non-zero first line means the barrel is not tree-shaken: stop and report, do not add `optimizePackageImports` without the maintainer.

- [ ] **Step 3: Preview**: `68 web hits`; lucide file count `90`.
- [ ] **Step 4: Stack and PR.** Stack hygiene, then `gh stack submit --auto --open` and:

```bash
cat > "$SCRATCH/pr-part2.md" <<'EOF'
Part 2 of #1313

Spec: `docs/superpowers/specs/2026-10-30-web-icon-migration-design.md`. Plan: Tasks 8–13.

- Nav (`lib/config/navigation.ts`, `NavItem.icon: PbblsIconComponent`): path, plus-circle, collection, people, pebble-dotted-glyph, sparkle, at `row` in the bottom nav; no `active` twin (D11).
- Path bar: profile fallback `person` (toolbar), karma `karma-dot` (inline; the kept lucide bounce icon moves to 16 with it).
- `GamificationBlock.icon` is structural; the dead blocks take `pebble-round` and `karma-dot`.
- Profile settings button `slider`; landing features `pebble-flat`, `sparkles`, `path`.
- Sandbox: listed names migrate, placeholders stay; new **Pebbles icon sizes** specimen at `/sandbox/design#specimen-pbbls-icon-sizes`.
- First `next build` that bundles `@pbbls/icons`: an unimported icon's path data is absent from the chunks.

Visual check: `/sandbox/design`, `/path` (phone width), `/profile`, `/` signed out.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
gh pr edit "$(gh pr view --json number -q .number)" \
  --title "chore(web): draw the nav, the path bar, the landing and the sandbox with pebbles icons" \
  --add-label chore,ui,web,no-lab-note --milestone "M66 · Brand icons" --body-file "$SCRATCH/pr-part2.md"
```

### Visual verification — Part 2

- `/sandbox/design#specimen-pbbls-icon-sizes` first: four drawings at 16, 20, 22, 28, 48 beside 12, 14 and 16 px text; `inline` is the same 16 on all three text sizes. Then with the browser font size at 20 px: every icon grows by 1.25. R1: if `inline` reads faint (a 1.17 px line at 16), the maintainer may move it to `size-4.5`, one line in `pbbls-icon.ts` and its test.
- `/sandbox/design#specimen-button`: the plus-circle in each icon size (12 under `icon-xs`, 16 under `icon`, `icon-sm`, `icon-lg`) and in "With icon".
- `/sandbox/design#specimen-gamification-block` (stack beside the lucide flame), `#specimen-data-tile` (sparkles), `#specimen-shortcut-tile` (slider), and the Profile view's settings button.
- `/path` at 390 px wide: six nav drawings at 20, the current one in `text-primary` with no other change; the path bar's karma dot beside the bounce icon at the same height; with no glyph set, the person drawing in the 40 px profile circle.
- `/profile`: the settings button's slider at 16 in the 32 px button.
- `/` signed out: the three feature icons at 20 beside their titles.
- Accessibility: in Chrome DevTools' accessibility tree, the profile settings link is named by its label and its svg is ignored (hidden).

---

## Part 3 — the moment, the record flow, carve (`chore/1313-web-moments`)

`gh stack add chore/1313-web-moments` from Part 2's branch.

### Task 14: The moment's read and edit tiles

**Files:**
- Modify: `apps/web/components/pebble/MomentReadTiles.tsx:4,47,63`
- Modify: `apps/web/components/pebble/PebbleDetailTiles.tsx:4,103,276`

- [ ] **Step 1: `MomentReadTiles.tsx`.** Line 4 `import { Heart, Layers, Tag } from "lucide-react"` becomes:

```ts
import { Tag } from "lucide-react"
import { Collection as CollectionIcon, Heart } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

In line 47, `<Heart className="size-5" />` becomes `<Heart className={PBBLS_ICON_SIZE.row} />` (the rest of the line stays). Line 63

```tsx
        <ReadTile icon={<Layers className="size-5" />}>{inCollections.map((c) => c.name).join(", ")}</ReadTile>
```

becomes

```tsx
        <ReadTile icon={<CollectionIcon className={PBBLS_ICON_SIZE.row} />}>{inCollections.map((c) => c.name).join(", ")}</ReadTile>
```

Line 50 (`Tag`) stays: life domain is a wave figure (D2).

- [ ] **Step 2: `PebbleDetailTiles.tsx`.** Line 4 `import { Heart, Tag, Layers } from "lucide-react"` becomes:

```ts
import { Tag } from "lucide-react"
import { Collection as CollectionIcon, Heart } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 103 `            <Heart className="size-5" />` becomes `            <Heart className={PBBLS_ICON_SIZE.row} />`. Line 276 `        icon={<Layers className="size-5" />}` becomes `        icon={<CollectionIcon className={PBBLS_ICON_SIZE.row} />}`. Line 204 (`Tag`) stays.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/pebble/MomentReadTiles.tsx apps/web/components/pebble/PebbleDetailTiles.tsx
git commit -m "chore(web): draw the moment's emotion and collection tiles with pebbles icons"
```

### Task 15: The moment's menu

**Files:**
- Modify: `apps/web/components/pebble/MomentReadToolbar.tsx:4,85,90,99`

- [ ] **Step 1:** Line 4 `import { MoreHorizontal, Pencil, Share2, Shapes, Trash2, X } from "lucide-react"` becomes:

```ts
import { MoreHorizontal, Share2, X } from "lucide-react"
import { DrawDotted, PebbleRound, Trash } from "@pbbls/icons"
```

Line 85 `              <Pencil aria-hidden />` becomes `              <DrawDotted />` ("Edit moment"); line 90 `                <Shapes aria-hidden />` becomes `                <PebbleRound />` ("Edit pebble", iOS `HeldCardMenu`); line 99 `              <Trash2 aria-hidden />` becomes `              <Trash />`. No class: a `DropdownMenuItem` draws an unclassed svg at 16 (`inline`). Share, more and close stay lucide (D1).

- [ ] **Step 2: Commit**

```bash
git add apps/web/components/pebble/MomentReadToolbar.tsx
git commit -m "chore(web): draw the moment menu's edit and delete items with pebbles icons"
```

### Task 16: The edit picture slot and the souls grid

**Files:**
- Modify: `apps/web/components/pebble/PebbleEditPicture.tsx:4,190,298`
- Modify: `apps/web/components/pebble/PebbleEditSoulsGrid.tsx:4,85`

- [ ] **Step 1: `PebbleEditPicture.tsx`.** Line 4 `import { ImagePlus, Loader2, AlertCircle } from "lucide-react"` becomes:

```ts
import { Loader2, AlertCircle } from "lucide-react"
import { Camera } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 190 `// is staged the slot renders a dashed placeholder with a `ImagePlus` icon.` becomes `// is staged the slot renders a dashed placeholder with a `Camera` icon.` (a stale comment in a touched file is fixed in the same commit). Line 298 `          <ImagePlus className="size-7" aria-hidden />` becomes `          <Camera className={PBBLS_ICON_SIZE.tile} />` (the 150 px empty slot; iOS's edit photo slot is `camera`). The spinner and the alert stay (D1, D2).

- [ ] **Step 2: `PebbleEditSoulsGrid.tsx`.** Line 4 `import { Plus } from "lucide-react"` becomes:

```ts
import { PlusCircle } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 85 `              <Plus className="size-5" aria-hidden />` becomes `              <PlusCircle className={PBBLS_ICON_SIZE.row} />`.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/pebble/PebbleEditPicture.tsx apps/web/components/pebble/PebbleEditSoulsGrid.tsx
git commit -m "chore(web): draw the edit photo slot and the add-soul row with pebbles icons"
```

### Task 17: The record sheets and pickers

**Files:**
- Modify: `apps/web/components/record/CollectionSheet.tsx:3,48,66` (no importer, see Deviations)
- Modify: `apps/web/components/record/SoulsSheet.tsx:3,50`
- Modify: `apps/web/components/record/SoulPicker.tsx:4,177` (dead, D12)
- Modify: `apps/web/components/record/SoulPickerContent.tsx:4,150`
- Modify: `apps/web/components/record/TimeStepPicker.tsx:4,56,83`

- [ ] **Step 1: `CollectionSheet.tsx`.** Line 3 `import { Layers } from "lucide-react"` becomes:

```ts
import { Collection as CollectionIcon } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 48 `        <Layers className="size-3.5" aria-hidden />` becomes `        <CollectionIcon className={PBBLS_ICON_SIZE.inline} />` (the pill); line 66 `          <Layers className="size-5 text-muted-foreground/50" aria-hidden />` becomes `          <CollectionIcon className={cn(PBBLS_ICON_SIZE.row, "text-muted-foreground/50")} />` (the empty tile; `cn` is imported at line 8).

- [ ] **Step 2: `SoulsSheet.tsx`.** Line 3 `import { Check } from "lucide-react"` becomes:

```ts
import { CheckmarkDot } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 50 `            <Check className="size-5" aria-hidden />` becomes `            <CheckmarkDot className={PBBLS_ICON_SIZE.row} />` (the 36 px `icon-lg` done button, named by its `aria-label`).

- [ ] **Step 3: The dead `SoulPicker.tsx`.** Line 4 `import { Plus, Search, X } from "lucide-react"` becomes:

```ts
import { Search, X } from "lucide-react"
import { PlusCircle } from "@pbbls/icons"
```

Line 177 `              <Plus className="size-4 shrink-0" aria-hidden="true" />` becomes `              <PlusCircle className="size-4 shrink-0" aria-hidden="true" />` (D12: the name only).

- [ ] **Step 4: `SoulPickerContent.tsx`.** Line 4 `import { Plus, X } from "lucide-react"` becomes:

```ts
import { X } from "lucide-react"
import { PlusCircle } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 150 `          <Plus className="size-4 shrink-0" />` becomes `          <PlusCircle className={cn(PBBLS_ICON_SIZE.inline, "shrink-0")} />` (`cn` is already imported). The chip's `X` (line 122) stays (D1).

- [ ] **Step 5: `TimeStepPicker.tsx`.** Line 4 `import { Minus, Plus } from "lucide-react"` becomes `import { PlusCircle, Remove } from "@pbbls/icons"`; line 56 `        <Minus />` becomes `        <Remove />`; line 83 `        <Plus />` becomes `        <PlusCircle />`. No class: the `icon` button slot draws them at 16 (`inline`); each button keeps its `aria-label`.

- [ ] **Step 6: Commit**

```bash
git add apps/web/components/record/CollectionSheet.tsx apps/web/components/record/SoulsSheet.tsx apps/web/components/record/SoulPicker.tsx apps/web/components/record/SoulPickerContent.tsx apps/web/components/record/TimeStepPicker.tsx
git commit -m "chore(web): draw the record sheets, pickers and time stepper with pebbles icons"
```

### Task 18: The visibility trio's private grade (D3)

**Files:**
- Modify: `apps/web/components/record/VisibilityMenu.tsx:3,11`

- [ ] **Step 1:** Line 3 `import { Lock, Users, Globe } from "lucide-react"` becomes `import { Lock, UsersRound, Globe } from "lucide-react"`; line 11 `  private: Users,` becomes `  private: UsersRound,`. `LucideIcon` stays the map's type: every value is lucide until the wave draws the trio (D10).

- [ ] **Step 2: Commit**

```bash
git add apps/web/components/record/VisibilityMenu.tsx
git commit -m "chore(web): give the private grade lucide's rounded people, apart from the pebbles people"
```

### Task 19: Complete and the record steps

**Files:**
- Modify: `apps/web/components/record/complete/CompleteSuccessStep.tsx:3,57`
- Modify: `apps/web/components/record/flow/steps/RecordPhotoStep.tsx:3,86`
- Modify: `apps/web/components/record/flow/steps/RecordWhenStep.tsx:3,49`

- [ ] **Step 1: `CompleteSuccessStep.tsx`.** Line 3 `import { Sparkle } from "lucide-react"` becomes:

```ts
import { KarmaDot } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

Line 57 `            <Sparkle className="size-4 text-primary" aria-hidden />` becomes `            <KarmaDot className={cn(PBBLS_ICON_SIZE.inline, "text-primary")} />`.

- [ ] **Step 2: `RecordPhotoStep.tsx`.** Line 3 `import { ImagePlus, Loader2 } from "lucide-react"` becomes:

```ts
import { Loader2 } from "lucide-react"
import { CameraFocus } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 86 `          <ImagePlus className="size-10" aria-hidden />` becomes `          <CameraFocus className={PBBLS_ICON_SIZE.hero} />` (the hero, iOS `RecordPhotoStep`).

- [ ] **Step 3: `RecordWhenStep.tsx`.** Line 3 `import { Sparkles } from "lucide-react"` becomes:

```ts
import { Sparkle } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 49 `          <Sparkles className="size-4" aria-hidden />` becomes `          <Sparkle className={PBBLS_ICON_SIZE.inline} />` ("date from photo" is `sparkle`, not `karma-dot`, D4).

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/record/complete/CompleteSuccessStep.tsx apps/web/components/record/flow/steps/RecordPhotoStep.tsx apps/web/components/record/flow/steps/RecordWhenStep.tsx
git commit -m "chore(web): draw the record steps and the karma line with pebbles icons"
```

### Task 20: Carve

**Files:**
- Modify: `apps/web/components/carve/CarveToolbar.tsx:3,48,61`

- [ ] **Step 1:** Line 3 `import { Undo2, Trash2, Check } from "lucide-react"` becomes:

```ts
import { Undo2 } from "lucide-react"
import { CheckmarkDot, Trash } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

Line 48 `        <Trash2 className="h-4 w-4" />` becomes `        <Trash className={PBBLS_ICON_SIZE.inline} />` (clear, 32 px `icon` button). Line 61 `            <Check className="mr-1.5 h-4 w-4" />` becomes `            <CheckmarkDot className={cn(PBBLS_ICON_SIZE.inline, "mr-1.5")} />` (save, a labelled button). Undo (line 38) stays (D1).

- [ ] **Step 2: Commit**

```bash
git add apps/web/components/carve/CarveToolbar.tsx
git commit -m "chore(web): draw carve's clear and save with pebbles icons"
```

### Task 21: Part 3 gate and PR

- [ ] **Step 1: Gate**

```bash
npm run lint --workspace=apps/web
npm run test --workspace=apps/web
npm run build --workspace=apps/web
npm run check --workspace=@pbbls/icons
npm run lint:replaces --workspace=@pbbls/icons
```

Expected: eslint exit 0, problem count unchanged from Part 2; Vitest green; `next build` compiles; fresh; `replaces: 27 rows, 49 names, 0 hits`.

- [ ] **Step 2: Preview**: `51 web hits`; lucide file count `84`.
- [ ] **Step 3: Stack and PR.** Stack hygiene, then `gh stack submit --auto --open` and:

```bash
cat > "$SCRATCH/pr-part3.md" <<'EOF'
Part 3 of #1313

Spec: `docs/superpowers/specs/2026-10-30-web-icon-migration-design.md`. Plan: Tasks 14–21.

- Moment tiles: emotion-less `heart`, `collection` (imported as `CollectionIcon` beside the `Collection` type); `Tag` stays (wave).
- Moment menu: edit moment `draw-dotted`, edit pebble `pebble-round`, delete `trash`.
- Edit: empty photo slot `camera` (tile), add-soul row `plus-circle`.
- Record: collection pill and empty tile `collection`, souls done `checkmark-dot`, add soul `plus-circle`, time stepper `remove` / `plus-circle`, photo hero `camera-focus`, date from photo `sparkle`, complete karma line `karma-dot`.
- Visibility: private grade is lucide `UsersRound` so the trio stays one vocabulary (D3).
- Carve: clear `trash`, save `checkmark-dot`.

Visual check: a moment (`/pebble/<id>`, its menu, its edit and edit-pebble pages), `/record`, `/record/complete/<id>`, `/carve`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
gh pr edit "$(gh pr view --json number -q .number)" \
  --title "chore(web): draw the moment, the record flow and carve with pebbles icons" \
  --add-label chore,ui,web,no-lab-note --milestone "M66 · Brand icons" --body-file "$SCRATCH/pr-part3.md"
```

### Visual verification — Part 3

- `/pebble/<id>` of a moment with collections and of one with no emotion: the heart and collection tiles at 20 beside the lucide tag; the `⋯` menu's three items at 16 beside their labels.
- `/pebble/<id>/edit` with no photo: the camera at 28 in the dashed 150 px slot. `/pebble/<id>/edit-pebble`: the add-soul row's plus-circle at 20; the souls sheet's round done button with the checkmark-dot at 20.
- `/record`: the photo step's camera-focus at 48; pick a photo with EXIF date and see "date from photo" with the sparkle at 16; the time stepper's remove and plus-circle at 16 in their 32 px buttons; the privacy step's trio (lock, rounded people, globe, all lucide).
- `/record/complete/<id>` through to success: the karma dot at 16 beside the karma line.
- `/carve`: clear (trash at 16 in a 32 px button) and save (checkmark-dot beside "Save").
- `CollectionSheet` has no importer and cannot be seen (Deviations).
- Accessibility: the time stepper's minus button is named by its `aria-label`, its svg hidden.

---

## Part 4 — profile, achievements, wallet, settings (`chore/1313-web-profile`)

`gh stack add chore/1313-web-profile` from Part 3's branch.

### Task 22: `ShortcutTile` and `DataTile` draw on the scale (D7)

**Files:**
- Modify: `apps/web/components/profile/ShortcutTile.tsx:2,22`
- Modify: `apps/web/components/profile/DataTile.tsx:1,23`

- [ ] **Step 1: `ShortcutTile.tsx`.** After line 2 (`import type { ComponentType } from "react"`) add:

```ts
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

Line 22 `      <Icon className="size-[18px] text-primary" aria-hidden />` becomes `      <Icon className={cn(PBBLS_ICON_SIZE.row, "text-primary")} aria-hidden />` (the tile still takes lucide `UserRoundPlus`, so `aria-hidden` stays on the shared render).

- [ ] **Step 2: `DataTile.tsx`.** After line 1 (`import type { ComponentType } from "react"`) add the same two imports. Line 23 `        <Icon className="size-3.5 text-primary" aria-hidden />` becomes `        <Icon className={cn(PBBLS_ICON_SIZE.inline, "text-primary")} aria-hidden />` (R2: 16 beside 14 px text, accepted at the floor).

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/profile/ShortcutTile.tsx apps/web/components/profile/DataTile.tsx
git commit -m "chore(web): size the shortcut and data tile icons on the pebbles scale"
```

### Task 23: The achievement badges (D4, D10)

**Files:**
- Modify: `apps/web/components/achievements/AchievementBadge.tsx:4-5,14-23,38,56`
- Modify: `apps/web/components/profile/AchievementShelfBadge.tsx:3-4,8-19,28,36`

- [ ] **Step 1: `AchievementBadge.tsx` imports.** Lines 4–5

```ts
import { Compass, Gem, Heart, Layers, Lock, PenTool, Shapes, Store, Trophy, Users } from "lucide-react"
import type { LucideIcon } from "lucide-react"
```

become

```ts
import { Lock } from "lucide-react"
import { Collection, Heart, PebbleDottedGlyph, PebbleGlowMd, People, Stack } from "@pbbls/icons"
import { PBBLS_ICON_SIZE, type PbblsIconComponent } from "@/lib/icons/pbbls-icon"
```

- [ ] **Step 2: The family map.** Lines 14–23

```ts
const FAMILY_ICONS: Record<AchievementFamily, LucideIcon> = {
  pebble_count: Gem,
  emotion_first: Heart,
  domain_first: Compass,
  first_collection: Layers,
  first_glyph: PenTool,
  first_soul: Users,
  glyph_count: Shapes,
  glyph_sales: Store,
}
```

become

```ts
// `domain_first` and `glyph_sales` take the family default until the chrome
// wave draws life domain and market (iOS `familySymbol`).
const FAMILY_ICONS: Record<AchievementFamily, PbblsIconComponent> = {
  pebble_count: Stack,
  emotion_first: Heart,
  domain_first: PebbleGlowMd,
  first_collection: Collection,
  first_glyph: PebbleDottedGlyph,
  first_soul: People,
  glyph_count: PebbleDottedGlyph,
  glyph_sales: PebbleGlowMd,
}
```

Line 38 `  const Icon = FAMILY_ICONS[achievement.family] ?? Trophy` becomes `  const Icon = FAMILY_ICONS[achievement.family] ?? PebbleGlowMd`. Line 56 `        <Icon aria-hidden className="size-6" />` becomes `        <Icon className={PBBLS_ICON_SIZE.tile} />` (the 48 px disc). The locked caption's `Lock` (line 62) stays (wave).

- [ ] **Step 3: `AchievementShelfBadge.tsx`.** Lines 3–4

```ts
import { Compass, Gem, Heart, Layers, PenTool, Shapes, Store, Trophy, Users } from "lucide-react"
import type { LucideIcon } from "lucide-react"
```

become

```ts
import { Collection, Heart, PebbleDottedGlyph, PebbleGlowMd, People, Stack } from "@pbbls/icons"
import { PBBLS_ICON_SIZE, type PbblsIconComponent } from "@/lib/icons/pbbls-icon"
```

Lines 8–19

```ts
// Same family mapping as the grid's badge; unknown families degrade to the
// trophy so a server ahead of this build can't break the shelf.
const FAMILY_ICONS: Record<AchievementFamily, LucideIcon> = {
  pebble_count: Gem,
  emotion_first: Heart,
  domain_first: Compass,
  first_collection: Layers,
  first_glyph: PenTool,
  first_soul: Users,
  glyph_count: Shapes,
  glyph_sales: Store,
}
```

become

```ts
// Same family mapping as the grid's badge; unknown families degrade to the
// glow so a server ahead of this build can't break the shelf.
const FAMILY_ICONS: Record<AchievementFamily, PbblsIconComponent> = {
  pebble_count: Stack,
  emotion_first: Heart,
  domain_first: PebbleGlowMd,
  first_collection: Collection,
  first_glyph: PebbleDottedGlyph,
  first_soul: People,
  glyph_count: PebbleDottedGlyph,
  glyph_sales: PebbleGlowMd,
}
```

Line 28 `  const Icon = FAMILY_ICONS[achievement.family] ?? Trophy` becomes `  const Icon = FAMILY_ICONS[achievement.family] ?? PebbleGlowMd`. Line 36 `      <Icon className="size-4" aria-hidden />` becomes `      <Icon className={PBBLS_ICON_SIZE.inline} />`. The icon stays decorative: the `<li>` is already named by its `sr-only` title span (line 37), so labelling the icon would announce the title twice (Deviations).

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/achievements/AchievementBadge.tsx apps/web/components/profile/AchievementShelfBadge.tsx
git commit -m "chore(web): draw the achievement families with pebbles icons"
```

### Task 24: Card headers and the collection tile

**Files:**
- Modify: `apps/web/components/profile/AchievementsShelf.tsx:5,39`
- Modify: `apps/web/components/public-profile/PublicAchievementsShelf.tsx:3,35`
- Modify: `apps/web/components/profile/LabCard.tsx:5,18`
- Modify: `apps/web/components/profile/CollectionTile.tsx:4,31,33`

- [ ] **Step 1: `AchievementsShelf.tsx`.** Line 5 `import { ChevronRight, Trophy } from "lucide-react"` becomes:

```ts
import { ChevronRight } from "lucide-react"
import { PebbleGlowMd } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

Line 39 `            <Trophy className="size-[18px] shrink-0 text-primary" aria-hidden />` becomes `            <PebbleGlowMd className={cn(PBBLS_ICON_SIZE.row, "shrink-0 text-primary")} />`.

- [ ] **Step 2: `PublicAchievementsShelf.tsx`.** Line 3 `import { Trophy } from "lucide-react"` becomes:

```ts
import { PebbleGlowMd } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

Line 35 `        <Trophy className="size-[18px] shrink-0 text-primary" aria-hidden />` becomes `        <PebbleGlowMd className={cn(PBBLS_ICON_SIZE.row, "shrink-0 text-primary")} />`.

- [ ] **Step 3: `LabCard.tsx`.** Line 5 `import { ChevronRight, FlaskConical } from "lucide-react"` becomes:

```ts
import { ChevronRight } from "lucide-react"
import { Sparkle } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

Line 18 `          <FlaskConical className="size-[18px] shrink-0 text-primary" aria-hidden />` becomes `          <Sparkle className={cn(PBBLS_ICON_SIZE.row, "shrink-0 text-primary")} />`.

- [ ] **Step 4: `CollectionTile.tsx`.** Line 4 `import { Layers, Plus } from "lucide-react"` becomes:

```ts
import { Collection, PlusCircle } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 31 `          <Plus className="size-3.5 text-primary" aria-hidden />` becomes `          <PlusCircle className={cn(PBBLS_ICON_SIZE.inline, "text-primary")} />`; line 33 `          <Layers className="size-3.5 text-primary" aria-hidden />` becomes `          <Collection className={cn(PBBLS_ICON_SIZE.inline, "text-primary")} />` (`cn` is imported at line 5; nothing in this file is named `Collection`).

- [ ] **Step 5: Commit**

```bash
git add apps/web/components/profile/AchievementsShelf.tsx apps/web/components/public-profile/PublicAchievementsShelf.tsx apps/web/components/profile/LabCard.tsx apps/web/components/profile/CollectionTile.tsx
git commit -m "chore(web): draw the profile card headers and the collection tile with pebbles icons"
```

### Task 25: Counters and shortcuts

**Files:**
- Modify: `apps/web/components/profile/StatsCard.tsx:4,38-40`
- Modify: `apps/web/components/profile/ShortcutsRow.tsx:4,16-17`
- Modify: `apps/web/components/public-profile/PublicProfileView.tsx:4,57,94,99`

- [ ] **Step 1: `StatsCard.tsx`.** Line 4 `import { CalendarDays, Shell, Sparkles } from "lucide-react"` becomes `import { CalendarDate, KarmaDot, PebbleRound } from "@pbbls/icons"`. Lines 38–40

```tsx
        <DataTile value={daysPracticed} icon={CalendarDays} label={t("days")} />
        <DataTile value={pebbles} icon={Shell} label={t("pebbles")} />
        <DataTile value={karma} icon={Sparkles} label={t("karma")} />
```

become

```tsx
        <DataTile value={daysPracticed} icon={CalendarDate} label={t("days")} />
        <DataTile value={pebbles} icon={PebbleRound} label={t("pebbles")} />
        <DataTile value={karma} icon={KarmaDot} label={t("karma")} />
```

(the karma counter is `karma-dot`, D4.)

- [ ] **Step 2: `ShortcutsRow.tsx`.** Line 4 `import { Users, Fingerprint, UserRoundPlus } from "lucide-react"` becomes:

```ts
import { UserRoundPlus } from "lucide-react"
import { PebbleDottedGlyph, People } from "@pbbls/icons"
```

Lines 16–17 become:

```tsx
      <ShortcutTile href="/souls" icon={People} label={t("souls")} />
      <ShortcutTile href="/glyphs" icon={PebbleDottedGlyph} label={t("glyphs")} />
```

Line 18 (`UserRoundPlus`, invite) stays (wave).

- [ ] **Step 3: `PublicProfileView.tsx`.** Line 4 `import { CalendarDays, Fingerprint, Shell, Waves } from "lucide-react"` becomes:

```ts
import { Waves } from "lucide-react"
import { CalendarDate, PebbleDottedGlyph, PebbleRound } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

Line 57 `            <Fingerprint className="size-8 text-muted-foreground" aria-hidden />` becomes `            <PebbleDottedGlyph className={cn(PBBLS_ICON_SIZE.hero, "text-muted-foreground")} />` (the 96 px no-glyph slot). Line 94 `            icon={CalendarDays}` becomes `            icon={CalendarDate}`; line 99 `            icon={Shell}` becomes `            icon={PebbleRound}`. The bounce line's `Waves` (line 84) stays (wave).

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/profile/StatsCard.tsx apps/web/components/profile/ShortcutsRow.tsx apps/web/components/public-profile/PublicProfileView.tsx
git commit -m "chore(web): draw the profile counters and shortcuts with pebbles icons"
```

### Task 26: The no-glyph placeholders

**Files:**
- Modify: `apps/web/components/profile/ProfileBanner.tsx:3,30`
- Modify: `apps/web/components/settings/GlyphHeader.tsx:5,54`

- [ ] **Step 1: `ProfileBanner.tsx`.** Line 3 `import { Fingerprint } from "lucide-react"` becomes:

```ts
import { PebbleDottedGlyph } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

Line 30 `          <Fingerprint className="size-8 text-muted-foreground" aria-hidden />` becomes `          <PebbleDottedGlyph className={cn(PBBLS_ICON_SIZE.hero, "text-muted-foreground")} />`.

- [ ] **Step 2: `GlyphHeader.tsx`.** Line 5 `import { Fingerprint } from "lucide-react"` becomes the same three imports. Line 54 `            <Fingerprint className="size-8 text-muted-foreground" aria-hidden />` becomes `            <PebbleDottedGlyph className={cn(PBBLS_ICON_SIZE.hero, "text-muted-foreground")} />` (the 96 px trigger, named by its `aria-label`).

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/profile/ProfileBanner.tsx apps/web/components/settings/GlyphHeader.tsx
git commit -m "chore(web): draw the no-glyph placeholders with the pebbles glyph"
```

### Task 27: Karma and the celebration

**Files:**
- Modify: `apps/web/components/activity/GlyphPurchasePill.tsx:5,34`
- Modify: `apps/web/components/activity/KarmaActivityPill.tsx:5,35`
- Modify: `apps/web/components/wallet/WalletView.tsx:4,22`
- Modify: `apps/web/components/activity/AchievementMomentHost.tsx:6,97`

- [ ] **Step 1: The two pills.** In each of `GlyphPurchasePill.tsx` and `KarmaActivityPill.tsx`, line 5 `import { Sparkle } from "lucide-react"` becomes:

```ts
import { KarmaDot } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

and the icon line (`GlyphPurchasePill.tsx:34`, `KarmaActivityPill.tsx:35`) `      <Sparkle aria-hidden className="size-4 text-amber-300" />` becomes `      <KarmaDot className={cn(PBBLS_ICON_SIZE.inline, "text-amber-300")} />`.

- [ ] **Step 2: `WalletView.tsx`.** Line 4 `import { Sparkle } from "lucide-react"` becomes:

```ts
import { KarmaDot } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 22 `          <Sparkle className="size-5" aria-hidden />` becomes `          <KarmaDot className={PBBLS_ICON_SIZE.row} />` (beside `text-3xl`).

- [ ] **Step 3: `AchievementMomentHost.tsx`.** Line 6 `import { Trophy } from "lucide-react"` becomes:

```ts
import { PebbleGlowLg } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 97 `            <Trophy className="size-10" />` becomes `            <PebbleGlowLg className={PBBLS_ICON_SIZE.hero} />` (the 80 px disc of the celebration; the disc span is already `aria-hidden`).

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/activity/GlyphPurchasePill.tsx apps/web/components/activity/KarmaActivityPill.tsx apps/web/components/wallet/WalletView.tsx apps/web/components/activity/AchievementMomentHost.tsx
git commit -m "chore(web): draw karma and the achievement celebration with pebbles icons"
```

### Task 28: Delete account

**Files:**
- Modify: `apps/web/components/settings/DeleteAccountSection.tsx:4,94`

- [ ] **Step 1:** Line 4 `import { Trash2 } from "lucide-react"` becomes:

```ts
import { Trash } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

Line 94 `            <Trash2 className="size-5 shrink-0" aria-hidden />` becomes `            <Trash className={cn(PBBLS_ICON_SIZE.row, "shrink-0")} />` (a settings row lead).

- [ ] **Step 2: Commit**

```bash
git add apps/web/components/settings/DeleteAccountSection.tsx
git commit -m "chore(web): draw delete account with the pebbles trash"
```

### Task 29: Part 4 gate and PR

- [ ] **Step 1: Gate**

```bash
npm run lint --workspace=apps/web
npm run test --workspace=apps/web
npm run build --workspace=apps/web
npm run check --workspace=@pbbls/icons
npm run lint:replaces --workspace=@pbbls/icons
```

Expected: eslint exit 0, problem count unchanged from Part 3; Vitest green; `next build` compiles; fresh; `replaces: 27 rows, 49 names, 0 hits`.

- [ ] **Step 2: Preview**: `19 web hits`; lucide file count `73`.
- [ ] **Step 3: Stack and PR.** Stack hygiene, then `gh stack submit --auto --open` and:

```bash
cat > "$SCRATCH/pr-part4.md" <<'EOF'
Part 4 of #1313

Spec: `docs/superpowers/specs/2026-10-30-web-icon-migration-design.md`. Plan: Tasks 22–29.

- `ShortcutTile` at `row`, `DataTile` at `inline` (both still take lucide, so they keep a structural icon type).
- Achievement families, both badges: stack, heart, collection, pebble-dotted-glyph, people; `domain_first`, `glyph_sales` and unknown families take `pebble-glow-md` until the wave draws life domain and market. Grid badge at `tile`, shelf badge at `inline`; the locked caption's lock stays (wave).
- Card headers: achievements and public achievements `pebble-glow-md`, Lab `sparkle`; collection tile `plus-circle` / `collection`.
- Counters: days `calendar-date`, pebbles `pebble-round`, karma `karma-dot`; shortcuts `people`, `pebble-dotted-glyph`; no-glyph placeholders `pebble-dotted-glyph` at `hero`.
- Karma pills and wallet `karma-dot`; the celebration `pebble-glow-lg` at `hero`; delete account `trash`.

Visual check: `/profile`, `/u/<handle>`, `/achievements`, `/wallet`, `/settings`, a karma toast and the achievement celebration.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
gh pr edit "$(gh pr view --json number -q .number)" \
  --title "chore(web): draw profile, achievements, wallet and settings with pebbles icons" \
  --add-label chore,ui,web,no-lab-note --milestone "M66 · Brand icons" --body-file "$SCRATCH/pr-part4.md"
```

### Visual verification — Part 4

- `/profile`: the banner's no-glyph placeholder (an account with no glyph) at 48 in the 96 px slot; the three shortcut tiles (people, pebble-dotted-glyph at 20, the lucide invite beside them); the stats card's three counters at 16 beside 14 px labels (R2: the maintainer's eye decides whether 16 reads heavy); the achievements shelf header glow at 20 and its badges at 16 in the 36 px discs; the Lab card's sparkle; the collections card's tiles (plus-circle and collection at 16 in the 34 px squares).
- `/u/<handle>`: the same placeholder, counters and shelf header.
- `/achievements`: unlocked and locked badges at 28 in the 48 px discs; a `domain_first` or `glyph_sales` badge shows the glow.
- `/wallet`: the karma dot at 20 beside the balance.
- `/settings`: the glyph trigger's placeholder, the delete-account row's trash at 20.
- Record a pebble to see the karma pill (karma dot at 16 in amber); unlock an achievement (or trigger it from a test account) to see the celebration's glow at 48.
- Accessibility: in DevTools' accessibility tree, a shelf badge `<li>` is named by its title (from the `sr-only` span) and its svg is ignored.

---

## Part 5 — souls, collections, glyphs, connections, the Lab (`chore/1313-web-people-lab`)

`gh stack add chore/1313-web-people-lab` from Part 4's branch.

### Task 30: Souls

**Files:**
- Modify: `apps/web/components/souls/AddSoulForm.tsx:4,69`
- Modify: `apps/web/components/souls/SoulCard.tsx:4,30-33`
- Modify: `apps/web/components/souls/SoulDetailHeader.tsx:4,110`
- Modify: `apps/web/app/souls/[id]/page.tsx:6,86`

- [ ] **Step 1: `AddSoulForm.tsx`.** Line 4 `import { Plus } from "lucide-react"` becomes:

```ts
import { PlusCircle } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 69 `          <Plus className="size-4" />` becomes `          <PlusCircle className={PBBLS_ICON_SIZE.inline} />` (a `sm` button: the class keeps it at 16, not the slot's 14).

- [ ] **Step 2: `SoulCard.tsx`.** Line 4 `import { Stone } from "lucide-react"` becomes:

```ts
import { PebbleRound } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

Lines 30–33

```tsx
        <Stone
          className="size-3 text-primary"
          aria-hidden="true"
        />
```

become

```tsx
        <PebbleRound className={cn(PBBLS_ICON_SIZE.inline, "text-primary")} />
```

- [ ] **Step 3: `SoulDetailHeader.tsx`.** Line 4 `import { Pencil } from "lucide-react"` becomes:

```ts
import { DrawDotted } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 110 `              <Pencil className="size-3.5" />` becomes `              <DrawDotted className={PBBLS_ICON_SIZE.inline} />` (rename, `icon-xs`).

- [ ] **Step 4: `app/souls/[id]/page.tsx`.** Line 6 `import { Trash2 } from "lucide-react"` becomes:

```ts
import { Trash } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 86 `                  <Trash2 className="size-4" aria-hidden="true" />` becomes `                  <Trash className={PBBLS_ICON_SIZE.inline} />` (labelled `sm` button).

- [ ] **Step 5: Commit**

```bash
git add apps/web/components/souls/AddSoulForm.tsx apps/web/components/souls/SoulCard.tsx apps/web/components/souls/SoulDetailHeader.tsx "apps/web/app/souls/[id]/page.tsx"
git commit -m "chore(web): draw souls with pebbles icons"
```

### Task 31: Collections

**Files:**
- Modify: `apps/web/components/collections/CollectionDetailHeader.tsx:3,36`
- Modify: `apps/web/components/collections/CollectionPebbleList.tsx:3,53`
- Modify: `apps/web/app/collections/page.tsx:4,36`

- [ ] **Step 1: `CollectionDetailHeader.tsx`.** Line 3 `import { Pencil } from "lucide-react"` becomes:

```ts
import { DrawDotted } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 36 `              <Pencil />` becomes `              <DrawDotted className={PBBLS_ICON_SIZE.inline} />` (`icon-xs` would draw an unclassed icon at 12).

- [ ] **Step 2: `CollectionPebbleList.tsx`.** Line 3 `import { X } from "lucide-react"` becomes:

```ts
import { Remove } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 53 `              <X />` becomes `              <Remove className={PBBLS_ICON_SIZE.inline} />` (remove from collection, `icon-xs`; iOS "remove photo" is `remove`).

- [ ] **Step 3: `app/collections/page.tsx`.** Line 4 `import { Plus } from "lucide-react"` becomes:

```ts
import { PlusCircle } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 36 `                  <Plus data-icon="inline-start" />` becomes `                  <PlusCircle data-icon="inline-start" className={PBBLS_ICON_SIZE.inline} />` (`sm` button).

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/collections/CollectionDetailHeader.tsx apps/web/components/collections/CollectionPebbleList.tsx apps/web/app/collections/page.tsx
git commit -m "chore(web): draw collections with pebbles icons"
```

### Task 32: Glyphs

**Files:**
- Modify: `apps/web/components/glyphs/GlyphDetail.tsx:9,107,134`
- Modify: `apps/web/components/glyphs/MarketGlyphCard.tsx:4,38`

- [ ] **Step 1: `GlyphDetail.tsx`.** Line 9 `import { Pencil, Trash2 } from "lucide-react"` becomes:

```ts
import { DrawDotted, Trash } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
```

Line 107 `                <Pencil className="size-3.5" />` becomes `                <DrawDotted className={PBBLS_ICON_SIZE.inline} />`; line 134 `                  <Trash2 className="size-4" aria-hidden="true" />` becomes `                  <Trash className={PBBLS_ICON_SIZE.inline} />`.

- [ ] **Step 2: `MarketGlyphCard.tsx`.** Line 4 `import { Heart } from "lucide-react"` becomes:

```ts
import { Heart, HeartActive } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

Line 38

```tsx
        <Heart className={`size-4 ${glyph.favourited ? "fill-current text-rose-500" : ""}`} />
```

becomes

```tsx
        {glyph.favourited ? (
          <HeartActive className={cn(PBBLS_ICON_SIZE.inline, "text-rose-500")} />
        ) : (
          <Heart className={PBBLS_ICON_SIZE.inline} />
        )}
```

(the lit seal is the favourited twin; `fill-current` on `heart` would flood its open strokes, D4. State stays on the button's `aria-pressed`, D8.)

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/glyphs/GlyphDetail.tsx apps/web/components/glyphs/MarketGlyphCard.tsx
git commit -m "chore(web): draw glyph rename, delete and favourite with pebbles icons"
```

### Task 33: Remove a connection

**Files:**
- Modify: `apps/web/components/connections/ConnectionRow.tsx:5,97`

- [ ] **Step 1:** Line 5 `import { Ban, UserRoundMinus } from "lucide-react"` becomes:

```ts
import { Ban } from "lucide-react"
import { Remove } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

Line 97 `            <UserRoundMinus className="size-4 text-muted-foreground" aria-hidden />` becomes `            <Remove className={cn(PBBLS_ICON_SIZE.inline, "text-muted-foreground")} />`. Block (`Ban`, line 113) stays (wave).

- [ ] **Step 2: Commit**

```bash
git add apps/web/components/connections/ConnectionRow.tsx
git commit -m "chore(web): draw remove connection with the pebbles remove"
```

### Task 34: The Lab timeline and community card

**Files:**
- Modify: `apps/web/components/lab/LogTimeline.tsx:3,12-16,51-57`
- Modify: `apps/web/components/lab/FeaturedCommunityCard.tsx:3,18`

- [ ] **Step 1: `LogTimeline.tsx` imports.** Line 3 `import { CircleCheckBig, CircleDashed, CircleDot, type LucideIcon } from "lucide-react"` becomes:

```ts
import { CheckCircle, PebbleRound, PebbleRoundLit1 } from "@pbbls/icons"
import { PBBLS_ICON_SIZE, type PbblsIconComponent } from "@/lib/icons/pbbls-icon"
```

- [ ] **Step 2: The mode map.** Lines 12–16

```ts
const MODE_ICON: Record<TimelineMode, LucideIcon> = {
  changelog: CircleCheckBig,
  in_progress: CircleDot,
  backlog: CircleDashed,
}
```

become

```ts
// In progress is the lit stone, the backlog a plain one (iOS `LogTimeline`).
const MODE_ICON: Record<TimelineMode, PbblsIconComponent> = {
  changelog: CheckCircle,
  in_progress: PebbleRoundLit1,
  backlog: PebbleRound,
}
```

- [ ] **Step 3: The icon.** Lines 51–57

```tsx
              <Icon
                className={cn(
                  "size-3.5 shrink-0",
                  mode === "changelog" ? "text-primary" : "text-muted-foreground",
                )}
                aria-hidden
              />
```

become

```tsx
              <Icon
                className={cn(
                  PBBLS_ICON_SIZE.inline,
                  "shrink-0",
                  mode === "changelog" ? "text-primary" : "text-muted-foreground",
                )}
              />
```

- [ ] **Step 4: `FeaturedCommunityCard.tsx`.** Line 3 `import { MessagesSquare } from "lucide-react"` becomes:

```ts
import { People } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
```

Line 18 `          <MessagesSquare className="size-5 shrink-0 text-primary" aria-hidden />` becomes `          <People className={cn(PBBLS_ICON_SIZE.row, "shrink-0 text-primary")} />`.

- [ ] **Step 5: Commit**

```bash
git add apps/web/components/lab/LogTimeline.tsx apps/web/components/lab/FeaturedCommunityCard.tsx
git commit -m "chore(web): draw the lab timeline and community card with pebbles icons"
```

### Task 35: Part 5 gate and PR

- [ ] **Step 1: Gate**

```bash
npm run lint --workspace=apps/web
npm run test --workspace=apps/web
npm run build --workspace=apps/web
npm run check --workspace=@pbbls/icons
npm run lint:replaces --workspace=@pbbls/icons
```

Expected: eslint exit 0, problem count unchanged from Part 4; Vitest green; `next build` compiles; fresh; `replaces: 27 rows, 49 names, 0 hits`.

- [ ] **Step 2: Preview**: `5 web hits`, all five in the four admin files of Part 6 (`achievements/page.tsx` `Plus`, `NewLogButton.tsx` `Plus`, `Sidebar.tsx` `Sparkles` and `Trophy`, `LogSection.tsx` `CircleCheckBig`); lucide file count `62`. Web is done.
- [ ] **Step 3: Stack and PR.** Stack hygiene, then `gh stack submit --auto --open` and:

```bash
cat > "$SCRATCH/pr-part5.md" <<'EOF'
Part 5 of #1313

Spec: `docs/superpowers/specs/2026-10-30-web-icon-migration-design.md`. Plan: Tasks 30–35.

- Souls: add `plus-circle`, pebble count `pebble-round`, rename `draw-dotted`, delete `trash`.
- Collections: rename `draw-dotted`, remove from collection `remove`, new `plus-circle`; every icon under an `icon-xs` or `sm` button carries the `inline` class (16, not the slot's 12 or 14).
- Glyphs: rename `draw-dotted`, delete `trash`, favourite `heart` / `heart.active` (no more `fill-current`).
- Connections: remove `remove`; block stays (wave).
- Lab: shipped `check-circle`, in progress `pebble-round.lit-1`, backlog `pebble-round`; community card `people`.

After this part web imports no listed lucide name; the five left are admin's (Part 6).

Visual check: `/souls`, `/souls/<id>`, `/collections`, `/collections/<id>`, `/glyphs`, `/glyphs/<id>`, `/connections`, `/lab`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
gh pr edit "$(gh pr view --json number -q .number)" \
  --title "chore(web): draw souls, collections, glyphs, connections and the lab with pebbles icons" \
  --add-label chore,ui,web,no-lab-note --milestone "M66 · Brand icons" --body-file "$SCRATCH/pr-part5.md"
```

### Visual verification — Part 5

- `/souls`: each card's pebble count with the stone at 16 beside 12 px text (R2); the add form's plus-circle at 16 in the `sm` button. `/souls/<id>`: the rename button (draw-dotted at 16 in the 24 px button) and the delete button.
- `/collections`: "New" with the plus-circle at 16. `/collections/<id>`: rename, and the remove-from-collection buttons (remove at 16 in 24 px).
- `/glyphs`: favourite toggles, one unfavourited (heart outline) and one favourited (the lit seal in rose); toggling swaps the drawing. `/glyphs/<id>`: rename and delete.
- `/connections`: the remove button's drawing beside the lucide block.
- `/lab`: the changelog's check-circles in `text-primary`, the in-progress lit stone and the backlog stones in muted ink, at 16 in the timeline column; the community card's people at 20.
- Accessibility: the favourite toggle is named "Favourite"/"Unfavourite" with `aria-pressed`, its svg ignored.

---

## Part 6 — admin, and the lint goes live (`chore/1313-admin-arm-lint`)

`gh stack add chore/1313-admin-arm-lint` from Part 5's branch.

### Task 36: Admin's two "New" buttons and the sidebar (D9)

**Files:**
- Modify: `apps/admin/app/(authed)/achievements/page.tsx:2,49`
- Modify: `apps/admin/app/(authed)/logs/_components/NewLogButton.tsx:4,40`
- Modify: `apps/admin/components/layout/Sidebar.tsx:5-16,51`

- [ ] **Step 1: Achievements.** Line 2 `import { Plus } from "lucide-react"` becomes `import { PlusCircle } from "@pbbls/icons"`. Line 49 `          <Plus aria-hidden />` becomes `          <PlusCircle className="size-4" />` (a `sm` button whose slot draws 14: the class keeps the 16 floor, D7; the page is a Server Component and the generated component is a plain function, D13).

- [ ] **Step 2: New log.** Line 4 `import { Plus } from "lucide-react"` becomes `import { PlusCircle } from "@pbbls/icons"`. Line 40 `      <Plus className="size-4" aria-hidden />` becomes `      <PlusCircle className="size-4" />`.

- [ ] **Step 3: Sidebar.** Lines 5–16

```ts
import {
  BarChart3,
  BookOpen,
  Megaphone,
  Palette,
  Shapes,
  ShieldAlert,
  Smile,
  Sparkles,
  Store,
  Trophy,
} from "lucide-react"
```

become

```ts
import {
  BarChart3,
  BookOpen,
  Megaphone,
  Palette,
  Shapes,
  ShieldAlert,
  Smile,
  Store,
} from "lucide-react"
import { PebbleGlowMd, Sparkles } from "@pbbls/icons"
```

Line 51 `  { href: "/achievements", label: "Achievements", icon: Trophy },` becomes `  { href: "/achievements", label: "Achievements", icon: PebbleGlowMd },`. Line 34 (`label: "Features", icon: Sparkles`) is textually unchanged and now draws the Pebbles `Sparkles`. The menu renders `<Icon aria-hidden />` on the union of a Pebbles and a lucide component per array (`as const`); TypeScript resolves a union of single-signature components by intersecting their props, which the admin build below confirms. If it reports `JSX element type 'Icon' does not have any construct or call signatures`, add `import type { ComponentType } from "react"` and annotate the two mixed arrays as `const LOG_ITEMS: readonly { href: string; label: string; icon: ComponentType<{ "aria-hidden"?: boolean }> }[] = [ … ]` (same for `REFERENCE_ITEMS`), dropping their `as const`. The sidebar's `[&_svg]:size-4` keeps every entry at 16 (admin keeps its classes, D9).

- [ ] **Step 4: Typecheck, commit.** `npm run build --workspace=apps/admin` compiles. Then:

```bash
git add "apps/admin/app/(authed)/achievements/page.tsx" "apps/admin/app/(authed)/logs/_components/NewLogButton.tsx" apps/admin/components/layout/Sidebar.tsx
git commit -m "chore(admin): draw the new buttons and the product entries of the sidebar with pebbles icons"
```

### Task 37: Admin's log status set (D9)

**Files:**
- Modify: `apps/admin/app/(authed)/logs/_components/LogSection.tsx:1-14,35,43,49,55,61`

- [ ] **Step 1: Imports.** Lines 1–14

```ts
import Link from "next/link"
import {
  Apple,
  Bot,
  CircleCheckBig,
  CircleDotDashed,
  CircleFadingArrowUp,
  CirclePile,
  DatabaseZap,
  Monitor,
  MonitorSmartphone,
  Telescope,
  type LucideIcon,
} from "lucide-react"
```

become

```ts
import Link from "next/link"
import type { ComponentType } from "react"
import {
  Apple,
  Bot,
  DatabaseZap,
  Monitor,
  MonitorSmartphone,
  Telescope,
  type LucideIcon,
} from "lucide-react"
import { CheckCircle, HourglassEnd, PebbleRound, PebbleRoundLit1, type PbblsIconProps } from "@pbbls/icons"
```

- [ ] **Step 2: The variant type and icons.** Line 35 `  icon: LucideIcon | null` becomes `  icon: ComponentType<PbblsIconProps> | null`. Then `    icon: CircleDotDashed,` (line 43) becomes `    icon: PebbleRoundLit1,` (in progress, the lit stone); `    icon: CircleFadingArrowUp,` (line 49) becomes `    icon: HourglassEnd,` (planned, not yet); `    icon: CirclePile,` (line 55) becomes `    icon: PebbleRound,` (backlog); `    icon: CircleCheckBig,` (line 61) becomes `    icon: CheckCircle,` (shipped). The render line (`{Icon && <Icon className={cn("size-5", config.iconClass)} aria-hidden />}`) is unchanged: admin keeps its `size-5` (row) and its status colours. `PLATFORM_ICONS` stays `LucideIcon` (every value is a kept admin mark).

- [ ] **Step 3: Typecheck, commit.** `npm run build --workspace=apps/admin` compiles. Then:

```bash
git add "apps/admin/app/(authed)/logs/_components/LogSection.tsx"
git commit -m "chore(admin): draw the log status set with pebbles icons"
```

### Task 38: The last listed import, and `migrated: ["ios", "web"]` in the same commit

**Files:**
- Modify: `packages/icons/icons.json` (21 `migrated` lines)

The lint goes live with the code it polices: the marker flips in a commit that also carries any last listed import found here. Task 37's commit removed the last one the plan knows of; if Step 1 finds none, this commit carries the marker alone and the lint is green in it.

- [ ] **Step 1: Find what is left.** Run the lint preview (header). Expected: `0 web hits`. Every line printed is a site a task above missed: migrate it with the size rule of its neighbours, `git add` it, and carry it in this task's commit.

- [ ] **Step 2: The file count.**

```bash
grep -rlE "from ['\"]lucide-react" apps/web apps/admin --include='*.ts' --include='*.tsx' \
  | grep -v -e /components/ui/ -e node_modules -e /.next/ | wc -l
```

Expected: `60` (98 today minus the 38 files that no longer import lucide).

- [ ] **Step 3: Flip the 21 rows, as text.** From `packages/icons`:

```bash
node -e '
const fs = require("node:fs")
const rows = ["check-circle", "plus-circle", "remove", "camera", "path", "people", "collection", "stack", "person", "slider",
  "pebble-dotted-glyph", "draw-dotted", "pebble-round", "pebble-flat", "karma-dot", "sparkles", "calendar-date", "trash",
  "pebble-glow-md", "sparkle", "heart"]
const lines = fs.readFileSync("icons.json", "utf8").split("\n")
for (const name of rows) {
  const at = lines.findIndex((l) => l === `      "name": "${name}",`)
  const m = at + 5
  if (at < 0 || lines[m] !== "      \"migrated\": [\"ios\"],") throw new Error(`${name}: unexpected migrated line at ${m + 1}: ${lines[m]}`)
  lines[m] = "      \"migrated\": [\"ios\", \"web\"],"
}
fs.writeFileSync("icons.json", lines.join("\n"))
const m = JSON.parse(fs.readFileSync("icons.json", "utf8"))
console.log(m.icons.filter((r) => r.migrated.includes("web")).length, m.icons.filter((r) => r.replaces.web.length > 0 && !r.migrated.includes("web")).length)
'
git diff --stat icons.json
```

Expected: `21 0` (21 rows migrated for web, no row with web names left unmigrated) and `1 file changed, 21 insertions(+), 21 deletions(-)`.

- [ ] **Step 4: The lint, live**

Run: `npm run lint:replaces --workspace=@pbbls/icons`
Expected: `replaces: 27 rows, 77 names, 0 hits`, exit 0. A hit is a Step 1 miss: fix it, `git add` it, re-run. Then `npm run lint --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons` (still fresh: `migrated` reaches no emitter).

- [ ] **Step 5: Commit, one commit with any last import fix.** `git status --short` must list only `packages/icons/icons.json` and the files Step 1 fixed (if any); add those by path, never a whole app directory:

```bash
git add packages/icons/icons.json
git commit -m "chore(ui): web has migrated off every replaced lucide icon; the lint is live"
```

### Task 39: Decisions log and lessons learned

**Files:**
- Modify: `docs/decisions/log.md` (append)
- Modify: `docs/superpowers/plans/2026-10-30-web-icon-migration.md` (this file: fill `## Lessons learned` at the end)

- [ ] **Step 1: Append** to `docs/decisions/log.md`, with the heading's date set to the day of the commit (`date +%F`):

```markdown

## 2026-10-31 — Web and admin draw the pebbles icons; chrome stays lucide; the web lint reads the lucide import (#1313)

- **Status:** taken
- **Scope:** web, admin, cross-surface (the lint's web form)
- **Context:**
  - After #1311 and #1314, web and admin imported 85 lucide symbols at 204 sites in 98 files outside `components/ui/`. About half carry meaning the foundation wave draws (a pebble, a collection, a person, karma, a glyph, create, edit, delete); the rest are platform chrome, admin's own tools, or meanings no drawing has yet.
  - The lint's original web form (an identifier anywhere in a file that imports lucide) would have hit correct code once 60 files keep lucide: a Pebbles `Heart` beside a lucide `Tag`, a `label="Settings"`, a doc comment.
- **Decision:**
  - **Chrome stays lucide, closed list:** `ChevronLeft`, `ChevronRight`, `ChevronDown`, `X` (close, the chip ×), `MoreHorizontal`, `Share2`, `Copy`, `Undo2`, `ArrowRight`, `ArrowUpRight`, `ArrowDownLeft` (wallet out and in), `Check` (a menu's selection mark only, as shadcn's own menus draw it), `Search` (the field glyph), `Loader2` (spinners); brand slots `Link2` (a linked sign-in provider) and `Apple` (admin's platform mark). Admin's own tools (trend arrows, platform marks, sidebar tools, the error block) and the sandbox placeholders `Flame`, `Leaf`, `UserRound` stay too. Any other lucide icon needs a Pebbles drawing or a line in `packages/icons/waves/2026-11-chrome/brief.md` (`## Web (#1313)`).
  - **The visibility trio stays one vocabulary:** private is lucide `UsersRound` until the wave draws secret, private and public together.
  - **Five sizes, `PBBLS_ICON_SIZE`** (`apps/web/lib/icons/pbbls-icon.ts`): `inline` `size-4`, `row` `size-5`, `toolbar` `size-5.5`, `tile` `size-7`, `hero` `size-12` (16, 20, 22, 28, 48 px at the default root, the iOS values). Classes in rem, never the `size` prop (shadcn slots' CSS beats the attribute) and never em (it would drop below the floor beside `text-xs`). 16 is the floor: under a shadcn `xs`, `sm` or `icon-xs` slot the icon carries `inline`. Admin keeps its existing classes and honours the floor.
  - **Accessibility lives in the component:** a generated icon is `aria-hidden` unless it gets `aria-label`/`aria-labelledby` (then `role="img"`); an icon-only control is named on the control; colour only through `text-*`, never `fill-*`, `stroke-*` or `strokeWidth`; a favourited heart is the `heart.active` twin.
  - **The web lint reads lucide declarations:** for each `import … from` / `export … from` whose source is `lucide-react` or `lucide-react/…`, a deep, dynamic, namespace or default import is a hit by itself, and each specifier's imported name, stripped of `Lucide` and `Icon`, is a hit when a row migrated for web lists it. 21 rows list 28 web names; `migrated` is `["ios", "web"]` on them.
- **Why:**
  - **Rejected, migrating the selection tick:** a Pebbles tick on two menus beside shadcn's lucide tick on every other menu is two vocabularies in one component family.
  - **Rejected, ESLint `no-restricted-imports` with `importNames`:** a second copy of the manifest that drifts.
  - **Rejected, keeping the identifier grep:** a TypeScript identifier reaches a file only through an import, so the clause is the whole evidence; the grep fails on correct code and misses `Trash2Icon`, `LucideTrash2` and deep imports.
- **Consequences:**
  - A new web or admin icon is `import { Name } from "@pbbls/icons"` sized with `PBBLS_ICON_SIZE`; a listed lucide import fails the `Icons` job.
  - The web lint differs in kind from iOS's (imports, not literals); a future surface states which kind it needs.
  - Eight files nothing renders were migrated in place or left as they were (`layout/MobileHeader`, `layout/Sidebar`, `layout/ResetDataButton`, `layout/HapticsToggle`, `path/GamificationBlocks`, `record/CustomizationTile`, `record/SoulPicker`, `record/CollectionSheet`); deleting them is a follow-up issue.
  - Android (#1315) flips its own surface in `migrated` the same way.
- **Supersedes / Superseded-by:** supersedes the package spec's D10 web form (an identifier anywhere in a lucide-importing file) and, for web, the 2026-10-30 iOS icons entry's consequence that "the lint's web and Android forms are already in place".
- **Refs:** #1313, `docs/superpowers/specs/2026-10-30-web-icon-migration-design.md` (D1–D13), `docs/superpowers/plans/2026-10-30-web-icon-migration.md`, `packages/icons/scripts/lib/replaces.ts`, `apps/web/lib/icons/pbbls-icon.ts`.
```

- [ ] **Step 2: Lessons learned.** Replace the placeholder paragraph under `## Lessons learned` at the end of this plan with what the execution taught: what the maintainer's visual passes changed (R1's verdict on `inline`, R2's verdict on the 16 px counters, any size moved), whether the admin sidebar union needed the fallback annotation (Task 36), anything the import scan false-hit or missed, how many sites the preview caught that a task missed, and reviewer catches worth keeping as habits. If PR #1338 has merged, this file is on `main` and the edit rides in this part; if not, commit it on `docs/1313-web-icon-migration-spec` instead and push that branch.

- [ ] **Step 3: Commit**

```bash
git add docs/decisions/log.md docs/superpowers/plans/2026-10-30-web-icon-migration.md
git commit -m "docs: log the web icon chrome list, the sizes and the import-reading lint"
```

(Leave the plan path out of `git add` if Step 2 committed it on the docs branch.)

### Task 40: Part 6 gate, PR, merge

- [ ] **Step 1: Gate**

```bash
npm run lint --workspace=apps/web
npm run test --workspace=apps/web
npm run build --workspace=apps/web
npm run lint --workspace=apps/admin
npm run test --workspace=apps/admin
npm run build --workspace=apps/admin
npm run lint --workspace=@pbbls/icons && npm run build --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons
npm run check --workspace=@pbbls/icons
npm run lint:replaces --workspace=@pbbls/icons
```

Expected: web and admin eslint exit 0 with their problem counts unchanged from Part 5; both Vitest suites green; both `next build`s compile; icons green; fresh; `replaces: 27 rows, 77 names, 0 hits`.

- [ ] **Step 2: Stack and PR.** Stack hygiene, then `gh stack submit --auto --open` and:

```bash
cat > "$SCRATCH/pr-part6.md" <<'EOF'
Resolves #1313

Part 6 of 6. Spec: `docs/superpowers/specs/2026-10-30-web-icon-migration-design.md`. Plan: Tasks 36–40.

- Admin (D9): the two "New" buttons `plus-circle`; sidebar Features `sparkles`, Achievements `pebble-glow-md`; log status set in progress `pebble-round.lit-1`, planned `hourglass-end`, backlog `pebble-round`, shipped `check-circle`. Its tools, trend arrows and platform marks stay lucide.
- `icons.json`: `migrated: ["ios", "web"]` on the 21 rows that list web names, in the same commit as the last listed import. `npm run lint:replaces --workspace=@pbbls/icons` prints `replaces: 27 rows, 77 names, 0 hits`.
- Decisions log entry: the closed chrome list, the five sizes, the accessibility contract, the import-reading lint.

The stack: 102 sites draw a Pebbles icon (25 drawings plus `pebble-round.lit-1` and `heart.active`), 1 moves to lucide `UsersRound`, 101 stay lucide (43 chrome and brand, 29 in the wave brief, 26 admin tools, 3 sandbox placeholders). 60 files still import lucide, for those only.

Visual check: admin sidebar, `/logs/*`, `/achievements` on :3101.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
gh pr edit "$(gh pr view --json number -q .number)" \
  --title "chore(admin): draw the admin's product icons with pebbles icons and arm the web lint" \
  --add-label chore,ui,web,no-lab-note --milestone "M66 · Brand icons" --body-file "$SCRATCH/pr-part6.md"
```

- [ ] **Step 3: Before merging:** every PR of the stack green and visually passed by the maintainer; stack hygiene once more; `gh stack rebase`; `git diff --stat origin/<b> <b>` empty on every branch after `gh stack push`; re-run any run a double push cancelled (`gh run rerun <id>`, or push a new head if the run predates a fix on `main`, `project_pr_rerun_stale_merge_ref`). Then hand the maintainer `gh stack merge --yes --squash` (the classifier blocks it for the agent).

### Visual verification — Part 6

- Admin on :3101 (`cd apps/admin && npx next dev --port 3101`, signed in as an admin): the sidebar's Features sparkles and Achievements glow at 16 among the lucide tools, expanded and collapsed; `/logs/features` and the other log pages: each section header's status icon at 20 in its colour (blue lit stone, yellow hourglass, muted stone, green check-circle); the "New log" button; `/achievements`: the "New tier" button's plus-circle at 16 in the `sm` button.
- Web once more from the top of the stack: `/sandbox/design`, `/path`, `/profile`, one moment, `/lab`, to see nothing regressed in the rebases.
- Accessibility: the "New tier" button is named by its text, its svg ignored.

---

## Self-review against the spec

- **D1** chrome list: every kept chrome site is named as staying at its task (Tasks 9, 14–17, 20, 23–25, 33, 36, 37); the list lands in the log in Task 39. **D2** wave: Task 4 (five new figures, the reused eleven), kept sites named per task. **D3** `UsersRound`: Task 18. **D4** per-site picks: `ImagePlus` camera / camera-focus (Tasks 16, 19), `Sparkles` karma-dot / sparkle / sparkles (Tasks 25, 19, 11, 12, 36), `Trophy` glow-lg / glow-md (Tasks 27, 23, 24, 36), `CircleDot` lit stone (Task 34), `heart.active` (Task 32), done `Check` (Tasks 17, 20), `X` and `Minus` remove (Tasks 31, 17), `Shapes` (Tasks 15, 23), family defaults (Task 23), sandbox placeholders (Task 12). **D5** manifest: Task 2, flip in Task 38. **D6** lint: Task 1, docs Task 3. **D7** sizes: Task 5, applied in every migrating site, shared tiles Task 22, specimen Task 12, floor under `sm`/`icon-xs` (Tasks 30, 31, 36). **D8** accessibility: Task 6 tests; `aria-hidden` dropped at migrated sites; controls keep their names; shelf badge per Deviations. **D9** admin: Tasks 36–37. **D10** types: `NavItem.icon` (Task 8), `GamificationBlock` (Task 10), `FEATURE_ICONS` (Task 11), `FAMILY_ICONS` (Task 23), `MODE_ICON` (Task 34), admin `VARIANTS` (Task 37), `CollectionIcon` (Tasks 14, 17); `LucideIcon` stays in `VisibilityMenu`, `PLATFORM_ICONS`, `CustomizationTile`. **D11** no nav twin: Task 8. **D12** dead files: Tasks 8, 10, 17. **D13** runtime: Task 8's first bundling build, Task 13's tree-shaking check.
- **Counts verified on `ffbc1aff`:** 98 files, 204 sites, 85 symbols; 89 listed-name import sites (88 to a Pebbles icon through a listed name, 1 to `UsersRound`), 21/17/32/14/5 per Part 2–6; 58 files change plus `BottomNav`, `ShortcutTile`, `DataTile` and `specimens.ts`; 38 files stop importing lucide, leaving 60.
- **Names:** `PBBLS_ICON_SIZE`, `PbblsIconSize`, `PbblsIconComponent`, `lucideImportHits`, `lucideName`, `ANY_ICON`, `Found`, `TREES[...].read`, `lintReplaces`, `coverage` are used with the same names throughout.

## Deviations from the spec

1. **The size helper and the component contract tests land in Part 1, not Part 2** (spec parts table): the planning brief asked for the size helper in Part 1, mirroring iOS Part 1. Part 2 keeps the sizes specimen and remains the first `next build` that bundles `@pbbls/icons` (Part 1's helper imports only types).
2. **The shelf badge's icon stays decorative** (spec D8 says it takes `aria-label={title}`). The code already names the `<li>` with an `sr-only` span holding the title (`AchievementShelfBadge.tsx:37`), which the spec's description ("holds only the icon and a `title`") misses; labelling the icon too would announce the title twice, against D8's "one name". The maintainer may instead drop the span and label the icon.
3. **`record/CollectionSheet.tsx` is dead too.** Nothing imports it (only a comment in `CollectionPickerContent.tsx` names it); the spec does not mark it †. It migrates (the lint needs it clean) but cannot be checked in the browser, and joins D12's deletion follow-up.
4. **Admin's "New tier" icon gets `className="size-4"`.** D9 says every admin site is on the scale or in a slot, but that button is `size="sm"`, whose slot draws an unclassed icon at 14, under the 16 floor D9 keeps.
5. **The lint is a little stricter than D6 states:** a namespace import (`* as L`), `export * from "lucide-react"` and a default binding are hits by themselves (their names are not in the clause), and a declaration must start at a line-leading `import`/`export`, so a commented-out import is not read.
6. **`PathBottomBar`'s kept lucide `CirclePile` moves from 14 to 16:** it shares the `Stat` class with the karma dot, the one kept lucide line the migration touches.
7. **`TimeStepPicker`, the profile settings links, the sandbox `Slider`, the moment menu items and the button specimens stay unclassed**, sized by their 16 px slots, as D7 allows; the mapping table's "inline" for them is what the slot draws.

## Lessons learned

(Written in Part 6, Task 39, after the stack is executed.)
