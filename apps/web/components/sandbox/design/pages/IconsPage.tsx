"use client"

import { useMemo, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import type { FindingGroup } from "@/lib/design/audit"
import { ICON_ROWS, replacementsFor, type IconRow, type IconSurface } from "@/lib/design/icon-catalog"
import { PBBLS_ICON_SIZE, type PbblsIconSize } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
import { DesignSection } from "../DesignSection"
import { FindingList } from "../FindingList"

const SIZES: readonly PbblsIconSize[] = ["inline", "row", "toolbar", "tile", "hero"]
const SURFACES: readonly IconSurface[] = ["web", "ios", "android"]

function matches(row: IconRow, query: string): boolean {
  const q = query.trim().toLowerCase()
  return !q || row.name.includes(q) || row.meaning.toLowerCase().includes(q) || row.replacesWeb.some((r) => r.toLowerCase().includes(q))
}

function IconTile({ row, size }: { row: IconRow; size: PbblsIconSize }) {
  const { Icon } = row
  return (
    <li className="flex flex-col gap-2 rounded-xl border border-border bg-card p-3 text-card-foreground">
      <div className="flex min-h-14 flex-wrap items-center gap-3">
        {Icon ? <Icon className={PBBLS_ICON_SIZE[size]} /> : <span className="text-xs text-destructive">missing</span>}
        {row.twins.map(({ variant, Icon: Twin }) =>
          Twin ? <Twin key={variant} className={cn(PBBLS_ICON_SIZE[size], "text-primary")} aria-label={`${row.name}.${variant}`} /> : null,
        )}
      </div>
      <div className="flex flex-col gap-0.5">
        <code className="text-xs">{row.name}</code>
        {row.twins.length > 0 && (
          <span className="font-mono text-[11px] text-muted-foreground">+ {row.twins.map((t) => t.variant).join(", ")}</span>
        )}
        <p className="line-clamp-2 text-xs text-muted-foreground" title={row.meaning}>
          {row.meaning}
        </p>
      </div>
      <div className="mt-auto flex flex-wrap gap-1">
        {SURFACES.map((surface) => (
          <Badge key={surface} variant={row.migrated.includes(surface) ? "secondary" : "outline"} title={row.migrated.includes(surface) ? `${surface}: migrated` : `${surface}: not migrated`}>
            {row.migrated.includes(surface) ? "✓ " : ""}
            {surface}
          </Badge>
        ))}
      </div>
    </li>
  )
}

/**
 * The hand-drawn set at its real sizes (rem, so 1:1 with the app), with each
 * drawing's migration per surface, then what web still draws with Lucide.
 */
export function IconsPage({ lucide }: { lucide: readonly FindingGroup[] }) {
  const [query, setQuery] = useState("")
  const [size, setSize] = useState<PbblsIconSize>("tile")
  const rows = useMemo(() => ICON_ROWS.filter((row) => matches(row, query)), [query])
  const twins = ICON_ROWS.reduce((n, row) => n + row.twins.length, 0)
  const webPending = ICON_ROWS.filter((row) => row.replacesWeb.length > 0 && !row.migrated.includes("web"))
  const lucideUses = lucide.reduce((n, g) => n + g.count, 0)

  return (
    <>
      <DesignSection
        id="icons"
        title="Icons"
        description={`${ICON_ROWS.length} hand-drawn drawings and ${twins} twins from @pbbls/icons, drawn at the PBBLS_ICON_SIZE classes (rem: real size, not scaled). Twins (active, lit) are tinted primary. The chips say which surfaces have migrated to the drawing.`}
      >
        <div className="flex flex-wrap items-center gap-4">
          <Input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search a name, a meaning or a Lucide name"
            aria-label="Search icons"
            className="w-80 max-w-full"
          />
          <div role="group" aria-label="Size" className="flex items-center gap-1">
            <span className="mr-1 text-xs text-muted-foreground">Size</span>
            {SIZES.map((s) => (
              <Button key={s} size="xs" variant={s === size ? "default" : "outline"} aria-pressed={s === size} onClick={() => setSize(s)}>
                {s}
              </Button>
            ))}
          </div>
          <span className="text-xs text-muted-foreground">
            {rows.length} of {ICON_ROWS.length}
          </span>
        </div>
        <ul className="grid grid-cols-[repeat(auto-fill,minmax(11rem,1fr))] gap-3">
          {rows.map((row) => (
            <IconTile key={row.name} row={row} size={size} />
          ))}
        </ul>
      </DesignSection>

      <DesignSection
        id="lucide"
        title="Still Lucide on web"
        description={`${lucide.length} Lucide icons in ${lucideUses} imports across the web's source (components/ui included: shadcn draws its chevrons and checks with them). Each row opens onto its files. The drawing beside it is the one the manifest says replaces it; “no drawing” means the set has none yet.`}
      >
        <FindingList
          groups={lucide}
          empty="No Lucide import left on web."
          limit={60}
          lead={(group) => {
            const replacements = replacementsFor(group.value)
            return (
              <span className="flex w-16 shrink-0 items-center gap-1" title={replacements.map((r) => r.name).join(", ") || "no replacement in the manifest"}>
                {replacements.length === 0 ? (
                  <span className="text-[11px] text-muted-foreground">no drawing</span>
                ) : (
                  replacements.slice(0, 2).map((r) => (r.Icon ? <r.Icon key={r.name} className={PBBLS_ICON_SIZE.inline} /> : null))
                )}
              </span>
            )
          }}
        />
        {webPending.length > 0 && (
          <div className="flex flex-col gap-2">
            <h3 className="font-heading text-xl">Waiting for web</h3>
            <p className="max-w-2xl text-sm text-muted-foreground">
              Drawings whose manifest row names a Lucide icon to replace on web, with web not yet migrated.
            </p>
            <ul className="flex flex-wrap gap-2">
              {webPending.map((row) => (
                <li key={row.name} className="flex items-center gap-2 rounded-lg border border-border px-2 py-1 text-xs">
                  {row.Icon && <row.Icon className={PBBLS_ICON_SIZE.inline} />}
                  <code>{row.name}</code>
                  <span className="text-muted-foreground">← {row.replacesWeb.join(", ")}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </DesignSection>
    </>
  )
}
