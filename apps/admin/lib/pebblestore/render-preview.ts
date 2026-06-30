// apps/admin/lib/pebblestore/render-preview.ts
//
// DUPLICATED minimal port of apps/web/lib/engine/glyph.ts renderGlyphPaths fit
// math, so the admin app can preview a glyph inside a pebble shape without
// importing across workspaces. Consolidate into a shared package later if the
// engine is extracted (see spec §6.3) — do not refactor unprompted.

type Rect = { x: number; y: number; width: number; height: number }

function parseViewBox(vb: string): Rect {
  const [x, y, width, height] = vb.trim().split(/[\s,]+/).map(Number)
  return { x, y, width, height }
}

/** Uniform scale + centre translate fitting `glyphViewBox` into `zone`. */
export function fitTransform(glyphViewBox: string, zone: Rect): string {
  const vb = parseViewBox(glyphViewBox)
  const scale = Math.min(zone.width / vb.width, zone.height / vb.height)
  const offsetX = zone.x + (zone.width - vb.width * scale) / 2 - vb.x * scale
  const offsetY = zone.y + (zone.height - vb.height * scale) / 2 - vb.y * scale
  return `translate(${offsetX}, ${offsetY}) scale(${scale})`
}

export { parseViewBox }
export type { Rect }
