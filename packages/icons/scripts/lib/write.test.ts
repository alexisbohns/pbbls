import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { dirname, join } from "node:path"
import { describe, expect, it } from "vitest"
import { type OwnedPaths } from "./emit"
import { check, listOwned, write } from "./write"

const DRAWABLE = "apps/android/app/src/main/res/drawable"
const OWNED: OwnedPaths = {
  dirs: ["packages/icons/react"],
  files: ["apps/ios/Pebbles/Theme/PbblsIcon.swift"],
  patterns: [{ dir: DRAWABLE, prefix: "ic_pbbls_", suffix: ".xml" }],
}

function seed(entries: Record<string, string>): string {
  const root = mkdtempSync(join(tmpdir(), "icons-write-"))
  for (const [path, contents] of Object.entries(entries)) {
    mkdirSync(dirname(join(root, path)), { recursive: true })
    writeFileSync(join(root, path), contents)
  }
  return root
}

const fresh = () =>
  new Map([
    ["packages/icons/react/Home.tsx", "home"],
    ["packages/icons/react/index.ts", "index"],
    ["apps/ios/Pebbles/Theme/PbblsIcon.swift", "swift"],
    [`${DRAWABLE}/ic_pbbls_home.xml`, "home"],
  ])

describe("write", () => {
  it("lists the owned files on disk, nothing else", () => {
    const root = seed({
      "packages/icons/react/Old.tsx": "x",
      "packages/icons/react/nested/Deep.tsx": "x",
      "packages/icons/other.ts": "x",
      "apps/ios/Pebbles/Theme/PbblsIcon.swift": "x",
      "apps/ios/Pebbles/Theme/Other.swift": "x",
      [`${DRAWABLE}/ic_pbbls_old.xml`]: "x",
      [`${DRAWABLE}/ic_trash.xml`]: "x",
      [`${DRAWABLE}/ic_pbbls_note.txt`]: "x",
    })
    expect(listOwned(root, OWNED)).toEqual([
      "apps/android/app/src/main/res/drawable/ic_pbbls_old.xml",
      "apps/ios/Pebbles/Theme/PbblsIcon.swift",
      "packages/icons/react/Old.tsx",
      "packages/icons/react/nested/Deep.tsx",
    ])
  })

  it("removes orphaned owned files and spares the siblings", () => {
    const root = seed({
      "packages/icons/react/Old.tsx": "x",
      "packages/icons/react/Home.tsx": "stale",
      [`${DRAWABLE}/ic_pbbls_old.xml`]: "x",
      [`${DRAWABLE}/ic_trash.xml`]: "keep",
    })
    const result = write(root, OWNED, fresh())
    expect(result).toEqual({ written: 4, removed: 2 })
    expect(existsSync(join(root, "packages/icons/react/Old.tsx"))).toBe(false)
    expect(existsSync(join(root, DRAWABLE, "ic_pbbls_old.xml"))).toBe(false)
    expect(readFileSync(join(root, DRAWABLE, "ic_trash.xml"), "utf8")).toBe("keep")
    expect(readFileSync(join(root, "packages/icons/react/Home.tsx"), "utf8")).toBe("home")
    expect(readFileSync(join(root, "apps/ios/Pebbles/Theme/PbblsIcon.swift"), "utf8")).toBe("swift")
  })

  it("is fresh after a write", () => {
    const root = seed({})
    write(root, OWNED, fresh())
    expect(check(root, OWNED, fresh())).toEqual({ missing: [], stale: [], orphaned: [] })
  })

  it("reports missing, stale and orphaned files separately", () => {
    const root = seed({
      "packages/icons/react/Home.tsx": "home",
      "packages/icons/react/index.ts": "old index",
      "packages/icons/react/Gone.tsx": "x",
      [`${DRAWABLE}/ic_pbbls_home.xml`]: "home",
      [`${DRAWABLE}/ic_trash.xml`]: "keep",
    })
    expect(check(root, OWNED, fresh())).toEqual({
      missing: ["apps/ios/Pebbles/Theme/PbblsIcon.swift"],
      stale: ["packages/icons/react/index.ts"],
      orphaned: ["packages/icons/react/Gone.tsx"],
    })
  })

  it("refuses a file outside the owned paths", () => {
    const root = seed({})
    expect(() => write(root, OWNED, new Map([["packages/icons/src/home.svg", "x"]]))).toThrow("packages/icons/src/home.svg")
    expect(() => check(root, OWNED, new Map([["packages/icons/src/home.svg", "x"]]))).toThrow("packages/icons/src/home.svg")
  })
})
