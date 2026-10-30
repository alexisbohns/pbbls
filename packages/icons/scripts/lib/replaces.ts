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

/** One literal still named on a migrated surface: where it is and which row replaces it. */
export type Hit = { surface: Surface; icon: string; name: string; path: string; line: number; text: string }

type Tree = {
  roots: string[]
  extensions: string[]
  /** Repo-relative paths (files or directories, no trailing slash) the lint never reads. */
  skip: string[]
  /** The regex a listed name becomes; `escaped` is regex-safe. */
  pattern: (escaped: string) => RegExp
}

const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")

/** Every surface's tree and literal form. Android and web keep the package spec's D10 forms. */
export const TREES: Record<Surface, Tree> = {
  ios: {
    // Only the app target: `apps/ios/PebblesWidget` stays on SF Symbols for now.
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
    skip: [
      "apps/web/components/ui",
      "apps/admin/components/ui",
      "apps/web/node_modules",
      "apps/admin/node_modules",
      "apps/web/.next",
      "apps/admin/.next",
    ],
    // A lucide import is `import { A, B } from "lucide-react"`, possibly over
    // several lines; the file is checked for the import first, then each line
    // for the bare identifier.
    pattern: (n) => new RegExp(`\\b${n}\\b`),
  },
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

/** Every hit in `root` (the repository), in surface order (`SURFACES`), then file order, then line order. */
export function lintReplaces(root: string, manifest: Manifest): Hit[] {
  const hits: Hit[] = []
  for (const surface of SURFACES) {
    const names = manifest.icons.flatMap((row) =>
      row.migrated.includes(surface) ? row.replaces[surface].map((name) => ({ icon: row.name, name })) : [],
    )
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
            hits.push({ surface, icon: c.icon, name: c.name, path: posix(root, path), line: i + 1, text: line.trim() })
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
