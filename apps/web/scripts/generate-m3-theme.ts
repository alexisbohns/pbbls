/**
 * Writes app/m3-theme.css from Android's ColorSchemes.kt (#986).
 * Run from the web workspace: `npm run generate:m3 --workspace=apps/web`.
 */
import { readFileSync, writeFileSync } from "node:fs"
import path from "node:path"
import {
  ANDROID_SCHEMES_FROM_WEB,
  M3_CSS_FROM_WEB,
  parseColorSchemes,
  renderM3ThemeCss,
} from "../lib/theme/m3-schemes"

const webRoot = process.cwd()
const source = path.resolve(webRoot, ANDROID_SCHEMES_FROM_WEB)
const target = path.resolve(webRoot, M3_CSS_FROM_WEB)

try {
  const schemes = parseColorSchemes(readFileSync(source, "utf8"))
  writeFileSync(target, renderM3ThemeCss(schemes))
  console.log(`[generate:m3] wrote ${schemes.length} schemes × ${schemes[0].roles.length} roles to ${M3_CSS_FROM_WEB}`)
} catch (err) {
  console.error("[generate:m3] failed:", err instanceof Error ? err.message : err)
  process.exit(1)
}
