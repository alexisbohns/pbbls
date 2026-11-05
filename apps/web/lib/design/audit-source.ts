import { existsSync, readdirSync, readFileSync } from "node:fs"
import path from "node:path"
import { buildAudit, type DesignAudit } from "./audit"
import { SPECIMENS } from "./specimens"

/**
 * Runs the design audit over the web's own source. Server only: the
 * /sandbox/design pages are prerendered, so this reads the files once at build
 * time (and per request under `next dev`, where an edit shows on reload).
 */

const SCANNED_ROOTS = ["components", "app"] as const
/** The design page and its fixtures draw literals on purpose. */
const SKIPPED = ["components/sandbox", "app/sandbox"]

function webRoot(): string {
  const cwd = process.cwd()
  return existsSync(path.join(cwd, "components")) ? cwd : path.join(cwd, "apps/web")
}

function walk(root: string, dir: string): string[] {
  const absolute = path.join(root, dir)
  if (!existsSync(absolute)) return []
  return readdirSync(absolute, { withFileTypes: true }).flatMap((entry) => {
    const relative = `${dir}/${entry.name}`
    if (SKIPPED.includes(relative)) return []
    if (entry.isDirectory()) return entry.name === "__tests__" ? [] : walk(root, relative)
    return /\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name) ? [relative] : []
  })
}

let cached: DesignAudit | null = null

export function loadDesignAudit(): DesignAudit {
  if (cached && process.env.NODE_ENV === "production") return cached
  const root = webRoot()
  const files = SCANNED_ROOTS.flatMap((dir) => walk(root, dir))
  const sources = files.map((file) => ({ file, text: readFileSync(path.join(root, file), "utf8") }))
  const componentFiles = files.filter((f) => f.startsWith("components/") && f.endsWith(".tsx"))
  cached = buildAudit(sources, componentFiles, new Set(SPECIMENS.map((s) => s.source)))
  return cached
}
