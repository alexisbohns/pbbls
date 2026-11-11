"use client"

import Link from "next/link"
import type { ReactNode } from "react"
import { Badge } from "@/components/ui/badge"
import type { ArbitraryFamily, DesignAudit, FindingGroup } from "@/lib/design/audit"
import { DESIGN_DEVICES } from "@/lib/design/devices"
import { ICON_ROWS } from "@/lib/design/icon-catalog"
import { SPECIMEN_GROUPS, SPECIMENS, tuningProgress, type SpecimenEntry } from "@/lib/design/specimens"
import { cn } from "@/lib/utils"
import { directoryHref, useDesign } from "../DesignContext"
import { DesignSection } from "../DesignSection"

const ENTRIES: readonly SpecimenEntry[] = SPECIMENS

function occurrences(groups: readonly FindingGroup[]): number {
  return groups.reduce((n, g) => n + g.count, 0)
}

function CategoryCard({ href, title, figure, lines }: { href: string; title: string; figure: string; lines: string[] }) {
  return (
    <Link
      href={href}
      className="flex flex-col gap-2 rounded-2xl border border-border bg-card p-5 text-card-foreground transition-colors hover:bg-muted"
    >
      <span className="text-sm text-muted-foreground">{title}</span>
      <span className="font-heading text-4xl">{figure}</span>
      <ul className="text-sm text-muted-foreground">
        {lines.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
    </Link>
  )
}

type CheckProps = {
  title: string
  /** Omitted for a list that is not a count of problems. */
  count?: number
  unit: string
  why: string
  href?: string
  linkLabel?: string
  children?: ReactNode
}

/** One inconsistency check: how many, why it matters, the worst values, where to look. */
function Check({ title, count, unit, why, href, linkLabel, children }: CheckProps) {
  return (
    <li className="flex flex-col gap-3 rounded-2xl border border-border p-5">
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        {count !== undefined && (
          <span className={cn("font-heading text-3xl", count === 0 ? "text-muted-foreground" : "text-destructive")}>{count}</span>
        )}
        <h3 className="font-heading text-xl">{title}</h3>
        <span className="text-sm text-muted-foreground">{unit}</span>
        {href && (
          <Link href={href} className="ml-auto text-sm underline-offset-4 hover:underline">
            {linkLabel ?? "Open"} →
          </Link>
        )}
      </div>
      <p className="max-w-3xl text-sm text-muted-foreground">{why}</p>
      {children}
    </li>
  )
}

function TopValues({ groups, limit = 12 }: { groups: readonly FindingGroup[]; limit?: number }) {
  if (groups.length === 0) return null
  return (
    <ul className="flex flex-wrap gap-1.5">
      {groups.slice(0, limit).map((g) => (
        <li key={g.value}>
          <Badge variant="outline" className="font-mono" title={g.occurrences.map((o) => `${o.file}:${o.line}`).join("\n")}>
            {g.value} ×{g.count}
          </Badge>
        </li>
      ))}
      {groups.length > limit && <li className="text-xs text-muted-foreground">+{groups.length - limit} more</li>}
    </ul>
  )
}

/**
 * The directory's front page: what the system holds, and where the source
 * drifts from it. The scan runs over components/ and app/ at build time.
 */
export function OverviewPage({ audit }: { audit: DesignAudit }) {
  const { themeKey } = useDesign()
  const href = (path: string) => directoryHref(path, themeKey)
  const { tuned, total } = tuningProgress()
  const views = ENTRIES.filter((s) => s.section === "views")
  const parts = ENTRIES.filter((s) => s.section !== "views")
  const bridged = parts.filter((s) => s.m3 === "bridged")
  const bridgedViews = views.filter((s) => s.m3 === "bridged")
  const webMigrated = ICON_ROWS.filter((row) => row.migrated.includes("web")).length
  const literals = [...audit.hex, ...audit.colorFunctions]
  const byFamily = (f: ArbitraryFamily) => audit.arbitrary.filter((g) => g.family === f)
  const offScale = audit.arbitrary.filter((g) => g.family !== "other")
  const documented = audit.coverage.reduce((n, row) => n + row.documented, 0)
  const componentFiles = audit.coverage.reduce((n, row) => n + row.total, 0)

  return (
    <>
      <DesignSection
        id="overview"
        title="Overview"
        description="Every part of the Pebbles web design system in one place: foundations, the hand-drawn icons, components and whole views, the last two drawn as real-size mobile and desktop screens scaled to the page. Switch the theme above; every page and every screen follows."
      >
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <CategoryCard
            href={href("/colors")}
            title="Colors & type"
            figure="Foundations"
            lines={["App tokens, M3 roles and the bridge", "Emotion palettes, type, shape, stones"]}
          />
          <CategoryCard
            href={href("/icons")}
            title="Icons"
            figure={String(ICON_ROWS.length)}
            lines={["hand-drawn, each with its forms", `${webMigrated} migrated on web`, `${audit.lucide.length} Lucide icons still on web`]}
          />
          <CategoryCard
            href={href("/components")}
            title="Components"
            figure={String(parts.length)}
            lines={[`in ${SPECIMEN_GROUPS.length} groups`, `${parts.length - bridged.length} tuned to M3`, "on mobile and desktop"]}
          />
          <CategoryCard
            href={href("/views")}
            title="Views"
            figure={String(views.length)}
            lines={[`screens × ${DESIGN_DEVICES.length} devices`, DESIGN_DEVICES.map((d) => `${d.width}×${d.height}`).join(" · "), `${tuned}/${total} entries tuned`]}
          />
        </div>
      </DesignSection>

      <DesignSection
        id="inconsistencies"
        title="Inconsistencies"
        description={`What the source does outside the system, scanned over ${audit.scannedFiles} files of components/ and app/ (the design page itself excluded). A heuristic: each count is a prompt to look, not a verdict. Hover a value for its files.`}
      >
        <ul className="flex flex-col gap-4">
          <Check
            title="Lucide icons beside the hand-drawn set"
            count={audit.lucide.length}
            unit={`icons, ${occurrences(audit.lucide)} imports`}
            why="Two icon families draw side by side: Lucide's even 2 px line next to the hand-drawn 1.75 stroke. Each should move to its Pebbles drawing, or get one."
            href={href("/icons#lucide")}
            linkLabel="Icons"
          >
            <TopValues groups={audit.lucide} />
          </Check>
          <Check
            title="Literal colours"
            count={literals.length}
            unit={`values, ${occurrences(literals)} uses`}
            why="A hex or rgb() in a component ignores the theme, the colour world and dark mode."
            href={href("/colors#colors")}
            linkLabel="Colors"
          >
            <TopValues groups={literals} />
          </Check>
          <Check
            title="Default palette classes"
            count={audit.palette.length}
            unit={`classes, ${occurrences(audit.palette)} uses`}
            why="Tailwind's own amber, neutral or white sit outside the tokens, so no theme re-colours them."
            href={href("/colors#colors")}
            linkLabel="Colors"
          >
            <TopValues groups={audit.palette} />
          </Check>
          <Check
            title="Off-scale values"
            count={offScale.length}
            unit={`values, ${occurrences(offScale)} uses`}
            why="Arbitrary sizes (text-[13px], rounded-[11px], mb-[18px]) are steps the type, shape and spacing scales do not have. Many near-duplicates usually mean a missing token."
            href={href("/colors#type")}
            linkLabel="Type & shape"
          >
            <div className="grid gap-3 md:grid-cols-2">
              {(
                [
                  ["Type", "type"],
                  ["Radius", "radius"],
                  ["Spacing", "spacing"],
                  ["Size", "size"],
                ] as const
              ).map(([label, f]) => (
                <div key={f} className="flex flex-col gap-1.5">
                  <span className="text-xs text-muted-foreground">
                    {label} · {byFamily(f).length}
                  </span>
                  <TopValues groups={byFamily(f)} limit={10} />
                </div>
              ))}
            </div>
          </Check>
          <Check
            title="Not tuned to M3"
            count={bridged.length + bridgedViews.length}
            unit="entries on the bridge"
            why="These render M3 only through the shadcn-token bridge: they have not been reviewed against the M3 roles."
            href={href("/components")}
            linkLabel="Components"
          >
            <ul className="flex flex-wrap gap-1.5">
              {[...bridged, ...bridgedViews].map((s) => (
                <li key={s.id}>
                  <Badge variant="outline" title={s.source}>
                    {s.name}
                  </Badge>
                </li>
              ))}
            </ul>
          </Check>
          <Check
            title="Components without a specimen"
            count={componentFiles - documented}
            unit={`of ${componentFiles} component files`}
            why="Files the directory never draws on their own (views compose many of them). A component with no specimen can drift unseen."
          >
            <table className="w-full max-w-2xl text-sm">
              <thead>
                <tr className="text-left text-xs text-muted-foreground">
                  <th className="py-1 font-medium">components/</th>
                  <th className="py-1 font-medium">With a specimen</th>
                  <th className="py-1 font-medium">Missing</th>
                </tr>
              </thead>
              <tbody>
                {audit.coverage.map((row) => (
                  <tr key={row.domain} className="border-t border-border align-top">
                    <td className="py-1.5 font-mono text-xs">{row.domain}</td>
                    <td className="py-1.5">
                      {row.documented}/{row.total}
                    </td>
                    <td className="py-1.5">
                      {row.missing.length > 0 && (
                        <details>
                          <summary className="cursor-pointer text-muted-foreground">{row.missing.length} files</summary>
                          <ul className="mt-1 font-mono text-[11px] text-muted-foreground">
                            {row.missing.map((file) => (
                              <li key={file}>{file}</li>
                            ))}
                          </ul>
                        </details>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Check>
          <Check
            title="Where to start"
            unit="the files with the most findings"
            why="Every finding above, counted per file."
          >
            <ol className="flex flex-col gap-1 font-mono text-xs">
              {audit.hotspots.map((h) => (
                <li key={h.file} className="flex gap-3">
                  <span className="w-8 text-right text-muted-foreground">{h.count}</span>
                  <span>{h.file}</span>
                </li>
              ))}
            </ol>
          </Check>
        </ul>
      </DesignSection>
    </>
  )
}
