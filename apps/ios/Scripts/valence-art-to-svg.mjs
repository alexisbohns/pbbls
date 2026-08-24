#!/usr/bin/env node
// Converts the nine `Assets.xcassets/Valence/valence-*.pdf` artworks into SVGs
// the app can wobble.
//
// Why this exists: the artworks shipped as vector PDFs, which SwiftUI can only
// draw as an opaque template image. `WobbleRenderer` needs path data. The PDFs
// are almost entirely *stroked* paths (20 `S` operators against a single fill
// in the large highlight), i.e. centerlines — exactly the shape
// `PebbleSVGModel` + `WobbleRenderer.pebbleArt` expect, so the output is
// written as plain `<path>` elements carrying how the PDF painted them.
//
// Two things have to survive the conversion or the artwork degrades:
//   - the line width. These are drawn in a ~190-unit box with fine detail, so
//     inking them at the uniform `PebbleStroke.outlineWidth` a real pebble uses
//     is too heavy. Their own widths are the ones they were drawn for.
//   - stroked vs filled. The fossil's spiral is a *filled* region (`f*`);
//     tracing it as a centerline inks it solid and it reads as a blob. Filled
//     paths are marked so the app displaces their contours instead.
//
// Run from apps/ios:  node Scripts/valence-art-to-svg.mjs
// Rerun only when the source PDFs change; the generated SVGs are committed.

import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { inflateSync } from 'node:zlib'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const assetDir = join(root, 'Pebbles/Resources/Assets.xcassets/Valence')
const outDir = join(root, 'Pebbles/Resources/ValenceArt')

const VALENCES = [
  'lowlightSmall', 'lowlightMedium', 'lowlightLarge',
  'neutralSmall', 'neutralMedium', 'neutralLarge',
  'highlightSmall', 'highlightMedium', 'highlightLarge',
]

/** Every `stream … endstream` payload in the file, inflated when it is Flate. */
function inflatedStreams(bytes) {
  const streams = []
  const marker = Buffer.from('stream')
  const endMarker = Buffer.from('endstream')
  let at = 0
  while (true) {
    const start = bytes.indexOf(marker, at)
    if (start === -1) break
    const end = bytes.indexOf(endMarker, start)
    if (end === -1) break
    // Skip the EOL that must follow the `stream` keyword.
    let from = start + marker.length
    if (bytes[from] === 0x0d) from++
    if (bytes[from] === 0x0a) from++
    const raw = bytes.subarray(from, end)
    try {
      streams.push(inflateSync(raw).toString('latin1'))
    } catch {
      // Not a Flate stream (or not a content stream at all) — skip it.
    }
    at = end + endMarker.length
  }
  return streams
}

/**
 * MediaBox of the first page, as [x0, y0, x1, y1]. These files come out of
 * cairo with the page dictionary itself compressed, so the box has to be found
 * across the inflated streams rather than in the raw bytes.
 */
function mediaBox(streams) {
  for (const stream of streams) {
    const match = stream.match(/\/MediaBox\s*\[\s*([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\s*\]/)
    if (match) return match.slice(1, 5).map(Number)
  }
  throw new Error('no MediaBox')
}

function tokenize(stream) {
  // Numbers, names (/a0), operators, and array delimiters. Strings and inline
  // images do not appear in these artworks.
  return stream.match(/\/[^\s/[\]<>()]+|-?\d*\.?\d+|[[\]]|[A-Za-z*'"]+/g) ?? []
}

const mul = (m, n) => [
  m[0] * n[0] + m[1] * n[2],
  m[0] * n[1] + m[1] * n[3],
  m[2] * n[0] + m[3] * n[2],
  m[2] * n[1] + m[3] * n[3],
  m[4] * n[0] + m[5] * n[2] + n[4],
  m[4] * n[1] + m[5] * n[3] + n[5],
]
const apply = (m, x, y) => [m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5]]
const round = (value) => (Math.round(value * 1000) / 1000).toString()

/**
 * Walks the content stream and returns `{ d, width, filled, evenOdd }` per
 * painted subpath set, in device space (the stream's own top-level flip is just
 * another `cm`, so the result is already y-down like SVG). `width` is the PDF
 * line width carried through the CTM's scale.
 */
function extractPaths(stream) {
  const tokens = tokenize(stream)
  const out = []

  let ctm = [1, 0, 0, 1, 0, 0]
  let lineWidth = 1
  const stack = []
  let operands = []
  let commands = []          // SVG commands for the current path
  let start = null           // subpath start, user space
  let current = null         // current point, user space

  const num = (index) => Number(operands[operands.length - index])
  const moveTo = (x, y) => {
    const [dx, dy] = apply(ctm, x, y)
    commands.push(`M ${round(dx)} ${round(dy)}`)
    start = [x, y]
    current = [x, y]
  }
  const lineTo = (x, y) => {
    const [dx, dy] = apply(ctm, x, y)
    commands.push(`L ${round(dx)} ${round(dy)}`)
    current = [x, y]
  }
  const curveTo = (x1, y1, x2, y2, x3, y3) => {
    const c1 = apply(ctm, x1, y1)
    const c2 = apply(ctm, x2, y2)
    const to = apply(ctm, x3, y3)
    commands.push(
      `C ${round(c1[0])} ${round(c1[1])} ${round(c2[0])} ${round(c2[1])} ${round(to[0])} ${round(to[1])}`
    )
    current = [x3, y3]
  }

  for (const token of tokens) {
    if (/^-?\d*\.?\d+$/.test(token)) { operands.push(token); continue }
    if (token.startsWith('/') || token === '[' || token === ']') { continue }

    switch (token) {
      case 'q': stack.push({ ctm: ctm.slice(), lineWidth }); break
      case 'Q': {
        const restored = stack.pop()
        ctm = restored?.ctm ?? [1, 0, 0, 1, 0, 0]
        lineWidth = restored?.lineWidth ?? 1
        break
      }
      case 'w': lineWidth = num(1); break
      case 'cm':
        ctm = mul([num(6), num(5), num(4), num(3), num(2), num(1)], ctm)
        break

      case 'm': moveTo(num(2), num(1)); break
      case 'l': lineTo(num(2), num(1)); break
      case 'c': curveTo(num(6), num(5), num(4), num(3), num(2), num(1)); break
      // `v` reuses the current point as the first control point, `y` reuses the
      // endpoint as the second.
      case 'v': curveTo(current[0], current[1], num(4), num(3), num(2), num(1)); break
      case 'y': curveTo(num(4), num(3), num(2), num(1), num(2), num(1)); break
      case 're': {
        const [x, y, w, h] = [num(4), num(3), num(2), num(1)]
        moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h); lineTo(x, y + h)
        commands.push('Z')
        current = start
        break
      }
      case 'h':
        commands.push('Z')
        if (start) current = start
        break

      // Every painting operator ends the path; which one it was decides how
      // the app wobbles it.
      case 'S': case 's': case 'f': case 'F': case 'f*':
      case 'B': case 'B*': case 'b': case 'b*': case 'n':
        if (token === 's' || token === 'b' || token === 'b*') commands.push('Z')
        if (commands.length && token !== 'n') {
          // Line width is a user-space quantity; the CTM's uniform scale is
          // what carries it into the coordinates we just emitted.
          const scale = Math.sqrt(Math.abs(ctm[0] * ctm[3] - ctm[1] * ctm[2]))
          out.push({
            d: commands.join(' '),
            width: lineWidth * scale,
            filled: 'fF'.includes(token[0]) || 'bB'.includes(token[0]),
            evenOdd: token.endsWith('*'),
          })
        }
        commands = []
        break

      default: break
    }
    operands = []
  }
  return out
}

function toSvg(paths, width, height) {
  const body = paths
    .map((p) => (p.filled
      ? `  <path d="${p.d}" fill="currentColor"${p.evenOdd ? ' fill-rule="evenodd"' : ''}/>`
      : `  <path d="${p.d}" fill="none" stroke="currentColor" stroke-width="${round(p.width)}"/>`))
    .join('\n')
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${round(width)} ${round(height)}">
${body}
</svg>
`
}

if (!existsSync(outDir)) mkdirSync(outDir, { recursive: true })

let total = 0
for (const valence of VALENCES) {
  const pdfPath = join(assetDir, `valence-${valence}.imageset`, `valence-${valence}.pdf`)
  const bytes = readFileSync(pdfPath)
  const streams = inflatedStreams(bytes)
  const [x0, y0, x1, y1] = mediaBox(streams)

  // Only streams that actually paint something; the rest are object streams
  // carrying the page dictionary.
  const paths = streams.filter((s) => / [SfF]\*? /.test(s)).flatMap(extractPaths)
  if (!paths.length) throw new Error(`no paths extracted from ${pdfPath}`)

  const svg = toSvg(paths, x1 - x0, y1 - y0)
  writeFileSync(join(outDir, `valence-${valence}.svg`), svg)
  console.log(`valence-${valence}: ${paths.length} paths, ${round(x1 - x0)}x${round(y1 - y0)}`)
  total += paths.length
}
console.log(`\n${VALENCES.length} artworks, ${total} paths → Pebbles/Resources/ValenceArt/`)
