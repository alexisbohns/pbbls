#!/usr/bin/env node
// Builds Settings.bundle's Licenses pane from Pebbles/Resources/Licenses/*.txt (#1395, spec §4).
//   node Scripts/generate-licenses.mjs           write the plists
//   node Scripts/generate-licenses.mjs --check   fail if they are stale, or if a resolved
//                                                package has no Licenses/<identity>.txt
import { readFileSync, readdirSync, writeFileSync, existsSync, mkdirSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..")
const licensesDir = path.join(root, "Pebbles/Resources/Licenses")
const bundleDir = path.join(root, "Pebbles/Resources/Settings.bundle")
const resolved = path.join(root, "Pebbles.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved")
const check = process.argv.includes("--check")

const TITLES = { "InclusiveSans-OFL": "Inclusive Sans", "Ysabeau-OFL": "Ysabeau", "Caveat-OFL": "Caveat", "ReenieBeanie-OFL": "Reenie Beanie",
  "supabase-swift": "Supabase Swift", "rive-ios": "Rive", svgview: "SVGView", "swift-crypto": "Swift Crypto", "swift-asn1": "Swift ASN.1",
  "swift-http-types": "Swift HTTP Types", "swift-clocks": "Swift Clocks", "swift-concurrency-extras": "Swift Concurrency Extras",
  "xctest-dynamic-overlay": "XCTest Dynamic Overlay" }
const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
const plist = (body) => `<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n<plist version="1.0">\n<dict>\n${body}</dict>\n</plist>\n`

// Licence files are hard-wrapped near 80 columns; the Settings app wraps again, which breaks
// every line in mid-sentence. Join the lines of each paragraph (blank-line separated) and keep
// the paragraph breaks. Separator lines (----, ====) stay on their own.
const RULE = /^[-=_*]{4,}$/
const ITEM = /^([*\u2022-]\s|\(?[0-9]+[.)]\s|\(?[a-z][.)]\s)/ // bullets, 1. 2) (a) a)
const WRAPPED = 50 // hard-wrapped lines run near 80 columns with indentation; shorter ones end on purpose
let rawPrev = ""
const reflow = (raw) =>
  raw.replace(/\r\n?/g, "\n").trim().split(/\n\s*\n/)
    .map((para) => para.split("\n").filter((l) => l.trim() !== "")
      .reduce((out, rawLine) => {
        const line = rawLine.trim()
        const prev = out.length ? rawPrev : ""
        const keepBreak =
          out.length === 0 ||
          RULE.test(line) || RULE.test(prev) ||
          prev.length < WRAPPED || // a short line was meant to end there (titles, headings)
          ITEM.test(line) || /^Copyright (\(c\)|©|[0-9])/i.test(line) ||
          /^https?:\/\/\S+$/.test(line) || /^\S+\.(ttf|otf):\s/i.test(line)
        if (keepBreak) out.push(line)
        else out[out.length - 1] += " " + line
        rawPrev = rawLine.trimEnd() // indentation counts: an indented wrapped line is still full
        return out
      }, []).join("\n"))
    .join("\n\n")

const entries = readdirSync(licensesDir).filter((f) => f.endsWith(".txt")).sort((a, b) => a.localeCompare(b, "en", { sensitivity: "base" }))
  .map((file) => { const id = file.replace(/\.txt$/, ""); return { id, title: TITLES[id] ?? id, text: reflow(readFileSync(path.join(licensesDir, file), "utf8")) } })

const files = {
  "Root.plist": plist(`\t<key>StringsTable</key>\n\t<string>Root</string>\n\t<key>PreferenceSpecifiers</key>\n\t<array>\n\t\t<dict>\n\t\t\t<key>Type</key>\n\t\t\t<string>PSGroupSpecifier</string>\n\t\t\t<key>Title</key>\n\t\t\t<string>Pebbles</string>\n\t\t\t<key>FooterText</key>\n\t\t\t<string>Copyright © 2026 Pebbles</string>\n\t\t</dict>\n\t\t<dict>\n\t\t\t<key>Type</key>\n\t\t\t<string>PSChildPaneSpecifier</string>\n\t\t\t<key>Title</key>\n\t\t\t<string>Licenses</string>\n\t\t\t<key>File</key>\n\t\t\t<string>Licenses</string>\n\t\t</dict>\n\t</array>\n`),
  "Licenses.plist": plist(`\t<key>StringsTable</key>\n\t<string>Root</string>\n\t<key>PreferenceSpecifiers</key>\n\t<array>\n${entries.map((e) => `\t\t<dict>\n\t\t\t<key>Type</key>\n\t\t\t<string>PSChildPaneSpecifier</string>\n\t\t\t<key>Title</key>\n\t\t\t<string>${esc(e.title)}</string>\n\t\t\t<key>File</key>\n\t\t\t<string>License-${esc(e.id)}</string>\n\t\t</dict>\n`).join("")}\t</array>\n`),
  ...Object.fromEntries(entries.map((e) => [`License-${e.id}.plist`, plist(`\t<key>PreferenceSpecifiers</key>\n\t<array>\n\t\t<dict>\n\t\t\t<key>Type</key>\n\t\t\t<string>PSGroupSpecifier</string>\n\t\t\t<key>FooterText</key>\n\t\t\t<string>${esc(e.text)}</string>\n\t\t</dict>\n\t</array>\n`)])),
}

let problems = []
if (existsSync(resolved)) {
  const pins = JSON.parse(readFileSync(resolved, "utf8")).pins ?? []
  for (const pin of pins) if (!entries.some((e) => e.id === pin.identity)) problems.push(`no Licenses/${pin.identity}.txt for resolved package ${pin.identity}`)
} else if (check) {
  problems.push(`Package.resolved not found at ${resolved}: run xcodegen and resolve packages first`)
}

if (check) {
  for (const [name, content] of Object.entries(files)) {
    const p = path.join(bundleDir, name)
    if (!existsSync(p) || readFileSync(p, "utf8") !== content) problems.push(`Settings.bundle/${name} is stale: run npm run generate:licenses`)
  }
  if (problems.length) { console.error(problems.join("\n")); process.exit(1) }
  console.log(`licences OK (${entries.length} components)`)
} else {
  mkdirSync(bundleDir, { recursive: true })
  for (const [name, content] of Object.entries(files)) writeFileSync(path.join(bundleDir, name), content)
  for (const lang of ["en", "fr"]) {
    mkdirSync(path.join(bundleDir, `${lang}.lproj`), { recursive: true })
    writeFileSync(path.join(bundleDir, `${lang}.lproj/Root.strings`), lang === "fr" ? `"Licenses" = "Licences";\n` : `"Licenses" = "Licenses";\n`)
  }
  if (problems.length) console.warn(problems.join("\n"))
  console.log(`wrote Settings.bundle (${entries.length} components)`)
}
