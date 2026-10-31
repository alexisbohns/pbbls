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
