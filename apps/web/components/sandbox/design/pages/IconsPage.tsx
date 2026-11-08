"use client"

import { Fragment, useMemo } from "react"
import { Android, Apple, Globe } from "@pbbls/icons"
import type { FindingGroup } from "@/lib/design/audit"
import { ICON_GLYPHS, ICON_ROWS, glyphMatches, replacementsFor, type IconGlyph, type IconSurface } from "@/lib/design/icon-catalog"
import { PBBLS_ICON_SIZE, type PbblsIconComponent, type PbblsIconSize } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
import { useDesign } from "../DesignContext"
import { DesignSection } from "../DesignSection"
import { FindingList } from "../FindingList"

/** Each surface drawn with the set's own glyph: the web's globe, Apple's apple, Android's head. */
const SURFACES: readonly { surface: IconSurface; label: string; Icon: PbblsIconComponent }[] = [
  { surface: "web", label: "Web", Icon: Globe },
  { surface: "ios", label: "iOS", Icon: Apple },
  { surface: "android", label: "Android", Icon: Android },
]

const count = new Intl.NumberFormat("en")

/** Where the glyph's row has migrated, as an accessible sentence. */
function migrationSentence(glyph: IconGlyph): string {
  const on = SURFACES.filter((s) => glyph.row.migrated.includes(s.surface)).map((s) => s.label)
  return on.length === 0 ? "Not migrated on any surface." : `Migrated on ${on.join(", ")}.`
}

/** The name with a break opportunity before each dot (a browser already breaks after a dash). */
function GlyphName({ name }: { name: string }) {
  return name.split(".").map((part, i) => (
    <Fragment key={i}>
      {i > 0 && (
        <>
          <wbr />.
        </>
      )}
      {part}
    </Fragment>
  ))
}

function GlyphTile({ glyph, size }: { glyph: IconGlyph; size: PbblsIconSize }) {
  const { Icon } = glyph
  return (
    <li title={glyph.row.meaning} className="group relative flex aspect-square flex-col items-center gap-2 rounded-2xl bg-card px-3 pt-6 pb-3 text-card-foreground transition-colors hover:bg-muted motion-reduce:transition-none">
      <span className="flex min-h-12 flex-1 items-center justify-center">
        {Icon ? <Icon className={PBBLS_ICON_SIZE[size]} /> : <span className="text-xs text-destructive">missing</span>}
      </span>
      {/* Two fixed lines, so every icon in a row sits at one height; breaks at a dash or a dot. */}
      <code className="line-clamp-2 h-8 max-w-full text-center text-xs leading-4 break-words text-muted-foreground">
        <GlyphName name={glyph.name} />
      </code>
      <span className="sr-only">{migrationSentence(glyph)}</span>
      {/* Migration per surface, on hover: lit where the surface draws it, dimmed where it does not. */}
      <span
        aria-hidden
        className="pointer-events-none absolute top-2 right-2 flex gap-1 opacity-0 transition-opacity duration-150 group-hover:opacity-100 motion-reduce:transition-none"
      >
        {SURFACES.map(({ surface, Icon: Mark }) => (
          <Mark
            key={surface}
            className={cn(PBBLS_ICON_SIZE.inline, glyph.row.migrated.includes(surface) ? "text-primary" : "text-muted-foreground/35")}
          />
        ))}
      </span>
    </li>
  )
}

/**
 * The hand-drawn set at its real sizes (rem, so 1:1 with the app): one tile per
 * glyph, base and twin alike, searched and sized from the toolbar. Hovering a
 * tile shows where its row has migrated. Then what web still draws with Lucide.
 */
export function IconsPage({ lucide }: { lucide: readonly FindingGroup[] }) {
  const { iconQuery, iconSize } = useDesign()
  const glyphs = useMemo(() => ICON_GLYPHS.filter((glyph) => glyphMatches(glyph, iconQuery)), [iconQuery])
  const twins = ICON_GLYPHS.length - ICON_ROWS.length
  const webPending = ICON_ROWS.filter((row) => row.replacesWeb.length > 0 && !row.migrated.includes("web"))
  const lucideUses = lucide.reduce((n, g) => n + g.count, 0)

  return (
    <>
      <DesignSection
        id="icons"
        title="Icons"
        description={`${count.format(ICON_GLYPHS.length)} glyphs from @pbbls/icons: ${count.format(ICON_ROWS.length)} drawings and ${count.format(twins)} twins (active, lit), each a glyph of its own. Drawn at the PBBLS_ICON_SIZE classes (rem: real size, not scaled). Hover a tile for where it has migrated: web, iOS, Android.`}
      >
        <div className="flex flex-col gap-3">
          <p aria-live="polite" className="text-right text-xs text-muted-foreground tabular-nums">
            {iconQuery.trim() ? `${count.format(glyphs.length)} of ${count.format(ICON_GLYPHS.length)}` : count.format(ICON_GLYPHS.length)}
          </p>
          {glyphs.length === 0 ? (
            <p className="rounded-xl border border-border px-4 py-10 text-center text-sm text-muted-foreground">
              No glyph matches “{iconQuery.trim()}”.
            </p>
          ) : (
            <ul className="grid grid-cols-[repeat(auto-fill,minmax(8.5rem,1fr))] gap-2">
              {glyphs.map((glyph) => (
                <GlyphTile key={glyph.name} glyph={glyph} size={iconSize} />
              ))}
            </ul>
          )}
        </div>
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
