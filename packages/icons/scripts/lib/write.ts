/**
 * The one module that touches the generated outputs (spec D2, D8). `write`
 * clears the owned paths and writes the emitted map; `check` compares the map
 * with the disk in memory and reports what is missing, stale or orphaned.
 * Paths are repo-relative with `/`; `root` is the repository root.
 */
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { type OwnedPaths } from "./emit"

export type CheckResult = { missing: string[]; stale: string[]; orphaned: string[] }

const under = (path: string, dir: string): boolean => path === dir || path.startsWith(`${dir}/`)

/** Whether a repo-relative path falls inside the owned set. */
export function isOwned(path: string, owned: OwnedPaths): boolean {
  if (owned.files.includes(path)) return true
  if (owned.dirs.some((dir) => under(path, dir))) return true
  return owned.patterns.some(({ dir, prefix, suffix }) => {
    if (dirname(path) !== dir) return false
    const file = path.slice(dir.length + 1)
    return file.startsWith(prefix) && file.endsWith(suffix) && file.length >= prefix.length + suffix.length
  })
}

function walk(root: string, dir: string, out: string[]): void {
  const abs = join(root, dir)
  if (!existsSync(abs)) return
  for (const entry of readdirSync(abs, { withFileTypes: true })) {
    const path = `${dir}/${entry.name}`
    if (entry.isDirectory()) walk(root, path, out)
    else out.push(path)
  }
}

/** Every owned file present on disk, sorted. */
export function listOwned(root: string, owned: OwnedPaths): string[] {
  const out: string[] = []
  for (const dir of owned.dirs) walk(root, dir, out)
  for (const file of owned.files) if (existsSync(join(root, file))) out.push(file)
  for (const pattern of owned.patterns) {
    const abs = join(root, pattern.dir)
    if (!existsSync(abs)) continue
    for (const entry of readdirSync(abs, { withFileTypes: true })) {
      const path = `${pattern.dir}/${entry.name}`
      if (entry.isFile() && isOwned(path, owned)) out.push(path)
    }
  }
  return [...new Set(out)].sort()
}

function assertOwned(files: Map<string, string>, owned: OwnedPaths): void {
  for (const path of files.keys()) {
    if (!isOwned(path, owned)) throw new Error(`${path}: emitted outside the owned paths`)
  }
}

/** Removes every owned file the map does not name, then writes the map. */
export function write(root: string, owned: OwnedPaths, files: Map<string, string>): { written: number; removed: number } {
  assertOwned(files, owned)
  let removed = 0
  for (const path of listOwned(root, owned)) {
    if (files.has(path)) continue
    rmSync(join(root, path), { force: true })
    removed += 1
  }
  for (const [path, contents] of files) {
    const target = join(root, path)
    mkdirSync(dirname(target), { recursive: true })
    writeFileSync(target, contents)
  }
  return { written: files.size, removed }
}

/** Compares the map with the disk without writing. Fresh means three empty lists. */
export function check(root: string, owned: OwnedPaths, files: Map<string, string>): CheckResult {
  assertOwned(files, owned)
  const onDisk = new Set(listOwned(root, owned))
  const result: CheckResult = { missing: [], stale: [], orphaned: [] }
  for (const [path, contents] of files) {
    if (!onDisk.has(path)) result.missing.push(path)
    else if (readFileSync(join(root, path), "utf8") !== contents) result.stale.push(path)
  }
  for (const path of onDisk) if (!files.has(path)) result.orphaned.push(path)
  return result
}
