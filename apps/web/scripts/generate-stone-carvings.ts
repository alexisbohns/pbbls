/**
 * Writes lib/config/stone-carvings.ts from docs/seeds/shape-seeds/*.svg (#1052).
 * Run from the web workspace: `npm run generate:stone-carvings --workspace=apps/web`.
 */
import { readFileSync, writeFileSync } from "node:fs"
import path from "node:path"
import {
  SHAPE_SEEDS_FROM_WEB,
  STONE_CARVINGS_FROM_WEB,
  renderStoneCarvings,
} from "../lib/utils/spine/shape-seed"

const webRoot = process.cwd()

try {
  const source = renderStoneCarvings((file) => readFileSync(path.resolve(webRoot, SHAPE_SEEDS_FROM_WEB, file), "utf8"))
  writeFileSync(path.resolve(webRoot, STONE_CARVINGS_FROM_WEB), source)
  console.log(`[generate:stone-carvings] wrote ${STONE_CARVINGS_FROM_WEB}`)
} catch (err) {
  console.error("[generate:stone-carvings] failed:", err instanceof Error ? err.message : err)
  process.exit(1)
}
