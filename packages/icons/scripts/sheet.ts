/**
 * A contact sheet for reviewing drawings by eye: each named glyph in every
 * form it has, plus its roles (body ink, parts in hues, mark orange, steps
 * blue, hidden paths dashed). Reads the sources directly, no generate needed.
 *
 *   npm run sheet --workspace=@pbbls/icons -- <out.html> <name> [<name>…]
 */
import { writeFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { loadGlyphs } from "./lib/emit-glyphs"
import { formsOf, parseFormKey, renderForm, type GlyphData, type GlyphPath, type RenderedPath } from "./lib/forms"

const ROOT = fileURLToPath(new URL("..", import.meta.url))
const PART_HUES = ["#2f7d6d", "#7a4fb0", "#b0602f", "#2f5fb0"]

const esc = (s: string): string => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;")

function svg(inner: string): string {
  return `<svg viewBox="0 0 24 24" width="72" height="72" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`
}

function drawn(paths: readonly RenderedPath[]): string {
  return paths.map((p) => `<path d="${esc(p.d)}" fill="${p.fill ? "currentColor" : "none"}"${p.evenOdd ? ' fill-rule="evenodd"' : ""}${p.stroke ? "" : ' stroke="none"'}/>`).join("")
}

function roleColour(p: GlyphPath, parts: readonly string[]): string {
  if (p.role === "mark") return "#d0461e"
  if (p.role === "step") return "#2563eb"
  if (p.role === "body" && p.part !== undefined) return PART_HUES[Math.max(0, parts.indexOf(p.part)) % PART_HUES.length] ?? "#1d1b18"
  if (p.role === "body") return "#1d1b18"
  return "#9a948a"
}

function roles(g: GlyphData): string {
  return g.paths
    .map((p) => `<path d="${esc(p.d)}" stroke="${roleColour(p, g.parts)}"${p.hidden ? ' stroke-dasharray="1 1.4"' : ""}/>`)
    .join("")
}

function row(g: GlyphData): string {
  const cells = formsOf(g).flatMap((key) => {
    const form = parseFormKey(key)
    return form ? [`<figure>${svg(drawn(renderForm(g, form)))}<figcaption>${esc(key)}${key in g.overrides ? " ·override" : ""}</figcaption></figure>`] : []
  })
  return `<section><h2>${esc(g.name)}</h2><div><figure class="roles">${svg(roles(g))}<figcaption>roles</figcaption></figure>${cells.join("")}</div></section>`
}

const [out, ...names] = process.argv.slice(2)
if (!out || names.length === 0) {
  console.error("usage: npm run sheet --workspace=@pbbls/icons -- <out.html> <name> [<name>…]")
  process.exit(2)
}
const glyphs = new Map(loadGlyphs(ROOT).map((g) => [g.name, g]))
const missing = names.filter((n) => !glyphs.has(n))
if (missing.length > 0) {
  console.error(`no such glyph: ${missing.join(", ")}`)
  process.exit(1)
}
const rows = names.flatMap((n) => {
  const g = glyphs.get(n)
  return g ? [row(g)] : []
})
writeFileSync(
  out,
  `<!doctype html><meta charset="utf-8"><style>
body{font:12px system-ui;background:#f6f2ea;color:#1d1b18;margin:16px}
section{display:flex;align-items:center;gap:12px;margin:0 0 10px;background:#fff;border-radius:12px;padding:8px 12px}
h2{font:600 12px ui-monospace,monospace;width:150px;margin:0;flex:none}
section>div{display:flex;flex-wrap:wrap;gap:6px}
figure{margin:0;display:flex;flex-direction:column;align-items:center;gap:2px}
figure.roles{padding-right:8px;border-right:1px solid #e6dfd2}
figcaption{font:10px ui-monospace,monospace;color:#6b655c}
</style>${rows.join("")}`,
)
console.log(`${rows.length} glyphs → ${out}`)
