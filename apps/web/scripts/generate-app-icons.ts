/**
 * Generates the web app's favicon and app icons from the master mark.
 *
 * Source: apps/ios/Pebbles/Resources/pbbls-mark.svg (single filled path, viewBox 0 0 1024 1024).
 * Palette: light ground, mark in the brand rose; the mark's bounding box fills 74% of the canvas
 * (61% for the maskable icon, which must stay inside the 80% safe-zone circle).
 *
 * Outputs: app/favicon.ico (16, 32, 48), public/icons/{icon-192,icon-512,apple-touch-icon,icon-maskable-512}.png
 * Run: npm run generate:icons --workspace=apps/web
 */

import sharp from "sharp"
import path from "node:path"
import fs from "node:fs"

const GROUND = "#F0E4E4"
const MARK = "#C07A7A"
const MASTER = path.resolve(process.cwd(), "../ios/Pebbles/Resources/pbbls-mark.svg")

const VIEWBOX = 1024
/** Resolution used once to measure the mark's bounding box. */
const MEASURE_PX = 4096

const ICONS = [
  { file: "public/icons/icon-192.png", size: 192, fill: 0.74 },
  { file: "public/icons/icon-512.png", size: 512, fill: 0.74 },
  { file: "public/icons/apple-touch-icon.png", size: 180, fill: 0.74 },
  { file: "public/icons/icon-maskable-512.png", size: 512, fill: 0.61 },
] as const

const FAVICON_SIZES = [16, 32, 48] as const
const FAVICON_FILL = 0.74

/** The master's drawing, recoloured: everything between the root <svg> tags. */
function markInner(master: string): string {
  const inner = master.replace(/^[\s\S]*?<svg[^>]*>/, "").replace(/<\/svg>\s*$/, "")
  return inner.replace(/fill="#000(?:000)?"/g, `fill="${MARK}"`)
}

type Box = { x: number; y: number; w: number; h: number }

/** Bounding box of the drawn mark, in viewBox units, measured by trimming a large render. */
async function measure(inner: string): Promise<Box> {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${MEASURE_PX}" height="${MEASURE_PX}" viewBox="0 0 ${VIEWBOX} ${VIEWBOX}">${inner}</svg>`
  const { info } = await sharp(Buffer.from(svg)).ensureAlpha().trim({ background: { r: 0, g: 0, b: 0, alpha: 0 }, threshold: 1 }).toBuffer({ resolveWithObject: true })
  const k = VIEWBOX / MEASURE_PX
  const x = -(info.trimOffsetLeft ?? 0) * k
  const y = -(info.trimOffsetTop ?? 0) * k
  return { x, y, w: info.width * k, h: info.height * k }
}

/** Renders the mark centred on the ground, its longest side taking `fill` of the canvas. */
async function render(inner: string, box: Box, size: number, fill: number): Promise<Buffer> {
  const scale = (size * fill) / Math.max(box.w, box.h)
  const tx = (size - box.w * scale) / 2 - box.x * scale
  const ty = (size - box.h * scale) / 2 - box.y * scale
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">` +
    `<rect width="${size}" height="${size}" fill="${GROUND}"/>` +
    `<g transform="translate(${tx} ${ty}) scale(${scale})">${inner}</g></svg>`
  return sharp(Buffer.from(svg)).png().toBuffer()
}

/** Packs PNG images into one .ico (PNG-compressed entries, supported since Vista). */
function packIco(images: { size: number; png: Buffer }[]): Buffer {
  const header = Buffer.alloc(6)
  header.writeUInt16LE(0, 0)
  header.writeUInt16LE(1, 2)
  header.writeUInt16LE(images.length, 4)

  let offset = 6 + 16 * images.length
  const entries = images.map(({ size, png }) => {
    const entry = Buffer.alloc(16)
    entry.writeUInt8(size >= 256 ? 0 : size, 0)
    entry.writeUInt8(size >= 256 ? 0 : size, 1)
    entry.writeUInt16LE(1, 4)
    entry.writeUInt16LE(32, 6)
    entry.writeUInt32LE(png.length, 8)
    entry.writeUInt32LE(offset, 12)
    offset += png.length
    return entry
  })

  return Buffer.concat([header, ...entries, ...images.map((i) => i.png)])
}

async function main() {
  const inner = markInner(fs.readFileSync(MASTER, "utf8"))
  const box = await measure(inner)
  console.log(`Mark bounding box: ${box.w.toFixed(1)} x ${box.h.toFixed(1)} at (${box.x.toFixed(1)}, ${box.y.toFixed(1)}) of ${VIEWBOX}`)

  for (const { file, size, fill } of ICONS) {
    fs.writeFileSync(path.resolve(process.cwd(), file), await render(inner, box, size, fill))
    console.log(`  ${file} (${size}px)`)
  }

  const favicons = await Promise.all(
    FAVICON_SIZES.map(async (size) => ({ size, png: await render(inner, box, size, FAVICON_FILL) })),
  )
  fs.writeFileSync(path.resolve(process.cwd(), "app/favicon.ico"), packIco(favicons))
  console.log(`  app/favicon.ico (${FAVICON_SIZES.join(", ")})`)
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
