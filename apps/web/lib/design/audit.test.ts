import { describe, expect, it } from "vitest"
import { arbitraryFamily, buildAudit, coverage, groupFindings, scanSource, stripComments } from "./audit"

const kinds = (source: string) => scanSource("components/x.tsx", source).map((f) => `${f.kind}:${f.value}`)

describe("stripComments", () => {
  it("blanks comments, keeps lines and URLs", () => {
    const text = `a // see #986\n/* #FFF\n */ b "https://x.test"`
    const stripped = stripComments(text)
    expect(stripped.split("\n")).toHaveLength(3)
    expect(stripped).not.toContain("#986")
    expect(stripped).not.toContain("#FFF")
    expect(stripped).toContain("https://x.test")
  })
})

describe("scanSource", () => {
  it("lists every Lucide import, aliases by their source name", () => {
    expect(kinds(`import { ChevronDown, Trash2 as Bin, type LucideIcon } from "lucide-react"`)).toEqual([
      "lucide:ChevronDown",
      "lucide:Trash2",
    ])
  })

  it("finds literal colours and ignores references", () => {
    expect(kinds(`const a = "#f8f0f0"; const b = "#FFF"; const c = "#000"; const ref = "(#986)"`)).toEqual([
      "hex:#F8F0F0",
      "hex:#FFF",
      "hex:#000",
    ])
    expect(kinds(`<a href="#section" />`)).toEqual([])
  })

  it("finds colour functions that are not tokens", () => {
    expect(kinds(`style={{ color: "rgb(0 0 0 / 0.5)", fill: "oklch(var(--x))" }}`)).toEqual([
      "color-function:rgb(0 0 0 / 0.5)",
    ])
  })

  it("finds default palette classes, with variants and opacity", () => {
    expect(kinds(`className="hover:bg-red-500 text-white/80 text-primary bg-surface"`)).toEqual([
      "palette:bg-red-500",
      "palette:text-white/80",
    ])
  })

  it("finds arbitrary values, but not tokens, colours or variants", () => {
    expect(
      kinds(`className="text-[13px] pt-[var(--safe)] bg-[#fff] data-[state=open]:bg-muted group-data-[size=sm]/btn:px-2 rounded-[10px] w-[390px]"`),
    ).toEqual(["hex:#FFF", "arbitrary:text-[13px]", "arbitrary:rounded-[10px]", "arbitrary:w-[390px]"])
  })

  it("reports the line", () => {
    const [finding] = scanSource("f.tsx", `\n\nconst c = "#ABCDEF"`)
    expect(finding.line).toBe(3)
  })
})

describe("arbitraryFamily", () => {
  it("sorts a value onto the scale it leaves", () => {
    expect(arbitraryFamily("text-[13px]")).toBe("type")
    expect(arbitraryFamily("rounded-tl-[6px]")).toBe("radius")
    expect(arbitraryFamily("px-[18px]")).toBe("spacing")
    expect(arbitraryFamily("max-w-[42rem]")).toBe("size")
    expect(arbitraryFamily("grid-cols-[1fr_2fr]")).toBe("other")
  })
})

describe("groupFindings", () => {
  it("groups by value, most frequent first", () => {
    const findings = [
      ...scanSource("a.tsx", `"#111111" "#222222"`),
      ...scanSource("b.tsx", `"#222222"`),
    ]
    const groups = groupFindings(findings, "hex")
    expect(groups.map((g) => [g.value, g.count])).toEqual([
      ["#222222", 2],
      ["#111111", 1],
    ])
  })
})

describe("coverage", () => {
  it("counts documented files per domain folder", () => {
    const rows = coverage(
      ["components/path/A.tsx", "components/path/B.tsx", "components/ui/button.tsx"],
      new Set(["components/path/A.tsx", "components/ui/button.tsx"]),
    )
    expect(rows).toEqual([
      { domain: "path", total: 2, documented: 1, missing: ["components/path/B.tsx"] },
      { domain: "ui", total: 1, documented: 1, missing: [] },
    ])
  })
})

describe("buildAudit", () => {
  it("summarises a set of sources", () => {
    const audit = buildAudit([{ file: "components/a.tsx", text: `"text-[13px] text-red-500"` }], [], new Set())
    expect(audit.scannedFiles).toBe(1)
    expect(audit.palette[0].value).toBe("text-red-500")
    expect(audit.arbitrary[0]).toMatchObject({ value: "text-[13px]", family: "type" })
    expect(audit.hotspots).toEqual([{ file: "components/a.tsx", count: 2 }])
  })
})
