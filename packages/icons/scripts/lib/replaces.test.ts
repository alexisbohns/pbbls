import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { dirname, join } from "node:path"
import { afterEach, describe, expect, it } from "vitest"
import { parseManifest } from "./manifest"
import { coverage, lintReplaces, type Hit } from "./replaces"

const row = (name: string, extra: Record<string, unknown> = {}) => ({
  name,
  meaning: `${name}.`,
  variants: [],
  wave: "2026-10-foundation",
  status: "candidate",
  migrated: [],
  replaces: { ios: [], android: [], web: [] },
  ...extra,
})

const roots: string[] = []

afterEach(() => {
  for (const r of roots.splice(0)) rmSync(r, { recursive: true, force: true })
})

function repo(files: Record<string, string>): string {
  const root = mkdtempSync(join(tmpdir(), "icons-replaces-"))
  roots.push(root)
  for (const [path, text] of Object.entries(files)) {
    mkdirSync(dirname(join(root, path)), { recursive: true })
    writeFileSync(join(root, path), text)
  }
  return root
}

const where = (hits: Hit[]) => hits.map((h) => `${h.path}:${h.line}`).sort()

describe("lintReplaces · ios", () => {
  const manifest = parseManifest({
    icons: [
      row("trash", { migrated: ["ios"], replaces: { ios: ["trash", "lock.fill"], android: [], web: [] } }),
      row("home", { migrated: [], replaces: { ios: ["house"], android: [], web: [] } }),
    ],
  })

  it("hits every bare literal form and skips the generated file and unmigrated rows", () => {
    const root = repo({
      "apps/ios/Pebbles/A.swift": 'Image(systemName: "trash")\n',
      "apps/ios/Pebbles/B.swift": 'let x = done ? "trash" : "circle"\n',
      "apps/ios/Pebbles/C.swift": 'func f() -> String {\n  return "lock.fill"\n}\n',
      "apps/ios/Pebbles/D.swift": 'switch k {\ncase "x": "trash"\n}\n',
      "apps/ios/Pebbles/E.swift": '// the old "trash" symbol\n',
      "apps/ios/Pebbles/F.swift": 'Image(systemName: "house")\nImage(systemName: "trash.fill")\n',
      "apps/ios/Pebbles/Theme/PbblsIcon.swift": 'case trash = "trash"\n',
      "apps/ios/PebblesTests/T.swift": 'let s = "trash"\n',
    })
    expect(where(lintReplaces(root, manifest))).toEqual([
      "apps/ios/Pebbles/A.swift:1",
      "apps/ios/Pebbles/B.swift:1",
      "apps/ios/Pebbles/C.swift:2",
      "apps/ios/Pebbles/D.swift:2",
      "apps/ios/Pebbles/E.swift:1",
    ])
  })

  it("is clean on a clean tree and reports the counts", () => {
    const root = repo({ "apps/ios/Pebbles/A.swift": 'Image(systemName: "house")\n' })
    expect(lintReplaces(root, manifest)).toEqual([])
    expect(coverage(manifest)).toEqual({ rows: 1, names: 2 })
  })
})

describe("lintReplaces · android and web", () => {
  const manifest = parseManifest({
    icons: [row("trash", { migrated: ["android", "web"], replaces: { ios: [], android: ["ic_trash"], web: ["Trash2"] } })],
  })

  it("matches R.drawable.<name> and a lucide import outside components/ui", () => {
    const root = repo({
      "apps/android/app/src/main/kotlin/X.kt": "val i = R.drawable.ic_trash\n",
      "apps/android/app/src/main/res/layout/x.xml": '<ImageView android:src="@drawable/ic_trash"/>\n',
      "apps/web/app/x.tsx": 'import { Trash2, Users } from "lucide-react"\n',
      "apps/web/app/z.tsx": "const Trash2 = 1\n",
      "apps/web/components/ui/button.tsx": 'import { Trash2 } from "lucide-react"\n',
      "apps/admin/app/y.tsx": 'import {\n  Trash2,\n} from "lucide-react"\n',
    })
    expect(where(lintReplaces(root, manifest))).toEqual([
      "apps/admin/app/y.tsx:2",
      "apps/android/app/src/main/kotlin/X.kt:1",
      "apps/android/app/src/main/res/layout/x.xml:1",
      "apps/web/app/x.tsx:1",
    ])
  })
})
