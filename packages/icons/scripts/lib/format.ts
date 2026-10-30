import { optimize } from "svgo"

/**
 * The canonical form of a source (spec D7): `svgo` as a formatter, never a path
 * optimiser, then the root rewritten to the seven canonical attributes and each
 * path's attributes put in one order. Path data passes through verbatim except
 * that a number with more than two decimals is rounded to two.
 */

export const ROOT_ATTRIBUTES: readonly (readonly [string, string])[] = [
  ["xmlns", "http://www.w3.org/2000/svg"],
  ["viewBox", "0 0 24 24"],
  ["fill", "none"],
  ["stroke", "currentColor"],
  ["stroke-width", "1.75"],
  ["stroke-linecap", "round"],
  ["stroke-linejoin", "round"],
]

export const CANONICAL_ROOT = "<svg " + ROOT_ATTRIBUTES.map(([k, v]) => `${k}="${v}"`).join(" ") + ">"

/** Path attributes in canonical order; anything else follows in source order (the lint rejects it). */
const PATH_ORDER = ["d", "fill", "fill-rule", "stroke"]

/** Attributes that only repeat the root. */
const REDUNDANT: Record<string, string> = { fill: "none", stroke: "currentColor" }

const ATTR = /([A-Za-z_:][\w:.-]*)="([^"]*)"/g

function roundPathData(d: string): string {
  return d.replace(/-?\d*\.\d{3,}/g, (n) => Number(n).toFixed(2).replace(/\.?0+$/, ""))
}

function formatPath(attrText: string): string {
  const pairs: [string, string][] = []
  for (const m of attrText.matchAll(ATTR)) {
    const key = m[1]
    const value = m[2]
    if (key === undefined || value === undefined) continue
    if (REDUNDANT[key] === value) continue
    pairs.push([key, key === "d" ? roundPathData(value) : value])
  }
  const ordered = PATH_ORDER.flatMap((k) => pairs.filter(([key]) => key === k))
  const rest = pairs.filter(([key]) => !PATH_ORDER.includes(key))
  return "<path " + [...ordered, ...rest].map(([k, v]) => `${k}="${v}"`).join(" ") + "/>"
}

export function formatSvg(input: string): string {
  const { data } = optimize(input, {
    multipass: false,
    plugins: [
      "removeDoctype",
      "removeXMLProcInst",
      "removeComments",
      "removeMetadata",
      "removeEditorsNSData",
      "removeTitle",
      "removeDesc",
      "cleanupAttrs",
      { name: "cleanupNumericValues", params: { floatPrecision: 2 } },
    ],
    js2svg: { pretty: true, indent: 2, finalNewline: true },
  })
  return data
    .replace(/^\s*<svg\b[^>]*>/, CANONICAL_ROOT)
    .replace(/<path\b([^>]*?)\/>/g, (_m, attrs: string) => formatPath(attrs))
}
