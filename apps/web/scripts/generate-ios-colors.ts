/**
 * Writes the iOS colorsets and Shared/Theme/PebblesColors.swift from Android's
 * ColorSchemes.kt (#921). Run from the web workspace:
 * `npm run generate:ios-colors --workspace=@pbbls/web`
 * (or `npm run generate:colors --workspace=@pbbls/ios`).
 */
import { mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs"
import path from "node:path"
import {
  APP_CATALOG,
  IOS_ROOT_FROM_WEB,
  M3_FOLDER,
  WIDGET_CATALOG,
  parsePinnedColors,
  renderIosColorsets,
} from "../lib/theme/ios-colorsets"
import { ANDROID_SCHEMES_FROM_WEB, parseColorSchemes } from "../lib/theme/m3-schemes"

const webRoot = process.cwd()
const iosRoot = path.resolve(webRoot, IOS_ROOT_FROM_WEB)

try {
  const kotlin = readFileSync(path.resolve(webRoot, ANDROID_SCHEMES_FROM_WEB), "utf8")
  const files = renderIosColorsets(parseColorSchemes(kotlin), parsePinnedColors(kotlin))
  // The M3 folders are wholly generated. Clearing them first means a role
  // removed upstream leaves no stale colorset behind.
  for (const catalog of [APP_CATALOG, WIDGET_CATALOG]) {
    rmSync(path.join(iosRoot, catalog, M3_FOLDER), { recursive: true, force: true })
  }
  for (const [relative, contents] of files) {
    const target = path.join(iosRoot, relative)
    mkdirSync(path.dirname(target), { recursive: true })
    writeFileSync(target, contents)
  }
  console.log(`[generate:ios-colors] wrote ${files.size} files under apps/ios`)
} catch (err) {
  console.error("[generate:ios-colors] failed:", err instanceof Error ? err.message : err)
  process.exit(1)
}
