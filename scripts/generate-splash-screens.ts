/**
 * Generates solid-color Apple splash screen PNGs for iOS PWA launch.
 *
 * Uses the default color world (blush-quartz) in light and dark mode.
 * Run: npx tsx scripts/generate-splash-screens.ts
 */

import sharp from "sharp"
import path from "node:path"
import fs from "node:fs"

const LIGHT_COLOR = "#F8F0F0"
const DARK_COLOR = "#2B1F21"

/** Portrait-only device specs: pixel width, pixel height, CSS device-width, CSS device-height, pixel ratio */
const DEVICES = [
  { w: 750, h: 1334, dw: 375, dh: 667, r: 2, name: "iPhone SE" },
  { w: 1242, h: 2208, dw: 414, dh: 736, r: 3, name: "iPhone 8 Plus" },
  { w: 1125, h: 2436, dw: 375, dh: 812, r: 3, name: "iPhone X/XS/11 Pro" },
  { w: 828, h: 1792, dw: 414, dh: 896, r: 2, name: "iPhone XR/11" },
  { w: 1242, h: 2688, dw: 414, dh: 896, r: 3, name: "iPhone XS Max/11 Pro Max" },
  { w: 1080, h: 2340, dw: 360, dh: 780, r: 3, name: "iPhone 12 mini/13 mini" },
  { w: 1170, h: 2532, dw: 390, dh: 844, r: 3, name: "iPhone 12/13/14" },
  { w: 1284, h: 2778, dw: 428, dh: 926, r: 3, name: "iPhone 12/13 Pro Max/14 Plus" },
  { w: 1179, h: 2556, dw: 393, dh: 852, r: 3, name: "iPhone 14 Pro/15/16" },
  { w: 1290, h: 2796, dw: 430, dh: 932, r: 3, name: "iPhone 14 Pro Max/15 Plus/16 Pro Max" },
] as const

async function main() {
  const outDir = path.resolve(process.cwd(), "public/splash")
  fs.mkdirSync(outDir, { recursive: true })

  const tasks: Promise<void>[] = []

  for (const device of DEVICES) {
    for (const [mode, color] of [["light", LIGHT_COLOR], ["dark", DARK_COLOR]] as const) {
      const filename = `${device.w}x${device.h}-${mode}.png`
      const filepath = path.join(outDir, filename)

      tasks.push(
        sharp({
          create: {
            width: device.w,
            height: device.h,
            channels: 3,
            background: color,
          },
        })
          .png()
          .toFile(filepath)
          .then(() => console.log(`  ${filename} (${device.name} ${mode})`))
      )
    }
  }

  console.log("Generating splash screens...")
  await Promise.all(tasks)
  console.log(`Done — ${tasks.length} files in public/splash/`)
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
