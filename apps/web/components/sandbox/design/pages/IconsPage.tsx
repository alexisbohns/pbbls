"use client"

import { Fragment, useMemo, useState } from "react"
import { Android, Apple, Globe } from "@pbbls/icons"
import type { FindingGroup } from "@/lib/design/audit"
import { GLYPHS, glyphMatchesQuery, needsReview, showsGlyph, viewForm, viewMatches, type Form, type Glyph } from "@/lib/design/glyph-forms"
import { ICON_ROWS, replacementsFor, type IconSurface } from "@/lib/design/icon-catalog"
import { PBBLS_ICON_SIZE, type PbblsIconComponent, type PbblsIconSize } from "@/lib/icons/pbbls-icon"
import { useReviewVerdicts, type Verdict } from "@/lib/design/useReviewVerdicts"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { useDesign } from "../DesignContext"
import { DesignSection } from "../DesignSection"
import { FindingList } from "../FindingList"
import { GlyphFormView } from "../GlyphFormView"
import { GlyphReviewDialog } from "../GlyphReviewDialog"

/** Each surface drawn with the set's own glyph: the web's globe, Apple's apple, Android's head. */
const SURFACES: readonly { surface: IconSurface; label: string; Icon: PbblsIconComponent }[] = [
  { surface: "web", label: "Web", Icon: Globe },
  { surface: "ios", label: "iOS", Icon: Apple },
  { surface: "android", label: "Android", Icon: Android },
]

const MIGRATED: ReadonlyMap<string, readonly string[]> = new Map(ICON_ROWS.map((row) => [row.name, row.migrated]))

/** Where the glyph's row has migrated, as an accessible sentence. */
function migrationSentence(migrated: readonly string[]): string {
  const on = SURFACES.filter((s) => migrated.includes(s.surface)).map((s) => s.label)
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

type GlyphTileProps = {
  glyph: Glyph
  size: PbblsIconSize
  form: Form
  verdict?: Verdict
  /** Whether a fix verdict carries a note yet. */
  noted: boolean
  onVerdict: () => void
  onOpen: () => void
}

function GlyphTile({ glyph, size, form, verdict, noted, onVerdict, onOpen }: GlyphTileProps) {
  const migrated = MIGRATED.get(glyph.name) ?? []
  return (
    <li
      title={glyph.meaning}
      className={cn(
        "group relative flex aspect-square flex-col items-center gap-2 rounded-2xl bg-card px-3 pt-6 pb-3 text-card-foreground transition-colors hover:bg-muted motion-reduce:transition-none",
      )}
    >
      {/* The whole tile opens the review dialog; the verdict pill sits above it. */}
      <button
        type="button"
        onClick={onOpen}
        aria-label={`Open ${glyph.name}: every form, its twins and its roles`}
        className="absolute inset-0 rounded-2xl outline-none focus-visible:ring-2 focus-visible:ring-ring"
      />
      {needsReview(glyph.name) && (
        <button
          type="button"
          onClick={onVerdict}
          aria-label={`Review ${glyph.name}: ${verdict ?? "no verdict"}${verdict === "fix" && !noted ? ", no note yet" : ""}`}
          // px-1.5 keeps the longest label ("drop-twin") clear of the hover platform marks at the 8.5rem tile.
          className={cn(
            "absolute top-2 left-2 z-10 rounded-full px-1.5 py-0.5 text-[10px] font-medium outline-none focus-visible:ring-2 focus-visible:ring-ring",
            verdict === "ok" && "bg-primary text-primary-foreground",
            verdict === "fix" && "bg-secondary text-secondary-foreground",
            verdict === "drop-twin" && "bg-muted text-muted-foreground line-through",
            verdict === undefined && "bg-primary/15 text-primary",
          )}
        >
          {verdict ?? "review"}
          {verdict === "fix" && (noted ? " ✎" : " · no note")}
        </button>
      )}
      <span className="flex min-h-12 flex-1 items-center justify-center">
        <GlyphFormView glyph={glyph} form={form} className={PBBLS_ICON_SIZE[size]} />
      </span>
      {/* Two fixed lines, so every icon in a row sits at one height; breaks at a dash or a dot. */}
      <code className="line-clamp-2 h-8 max-w-full text-center text-xs leading-4 break-words text-muted-foreground">
        <GlyphName name={glyph.name} />
      </code>
      <span className="sr-only">{migrationSentence(migrated)}</span>
      {/* Migration per surface, on hover: lit where the surface draws it, dimmed where it does not. */}
      <span
        aria-hidden
        className="pointer-events-none absolute top-2 right-2 flex gap-1 opacity-0 transition-opacity duration-150 group-hover:opacity-100 motion-reduce:transition-none"
      >
        {SURFACES.map(({ surface, Icon: Mark }) => (
          <Mark key={surface} className={cn(PBBLS_ICON_SIZE.inline, migrated.includes(surface) ? "text-primary" : "text-muted-foreground/35")} />
        ))}
      </span>
    </li>
  )
}

/**
 * The hand-drawn set at its real sizes (rem, so 1:1 with the app): one tile per
 * glyph, base and twin alike, drawn in the chosen form, searched, counted and sized from the side area. Hovering a
 * tile shows where its row has migrated. Then what web still draws with Lucide.
 */
export function IconsPage({ lucide }: { lucide: readonly FindingGroup[] }) {
  const { iconQuery, iconSize, glyphView, glyphShow } = useDesign()
  const { verdicts, notes, cycle, set, setNote, draft, copy } = useReviewVerdicts()
  const [openName, setOpenName] = useState<string | null>(null)
  const glyphs = useMemo(
    () => GLYPHS.filter((g) => glyphMatchesQuery(g, iconQuery) && showsGlyph(g.name, glyphShow, verdicts) && viewMatches(g, glyphView)),
    // Verdicts are read once per list: changing one in the dialog must not drop the glyph from a "Marked fix" walk.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [iconQuery, glyphShow, glyphView],
  )
  const openIndex = openName === null ? -1 : glyphs.findIndex((g) => g.name === openName)
  const step = (by: number) => {
    if (openIndex < 0 || glyphs.length === 0) return
    setOpenName(glyphs[(openIndex + by + glyphs.length) % glyphs.length].name)
  }
  const reviewed = glyphs.filter((g) => verdicts[g.name] !== undefined).length
  const fixes = glyphs.filter((g) => verdicts[g.name] === "fix")
  const noted = fixes.filter((g) => notes[g.name] !== undefined).length
  const webPending = ICON_ROWS.filter((row) => row.replacesWeb.length > 0 && !row.migrated.includes("web"))
  const lucideUses = lucide.reduce((n, g) => n + g.count, 0)

  return (
    <>
      {/* The page's title, its counts and its tools live in the side area (DesignToolbar). */}
      <section id="icons" aria-label="Icons" className="scroll-mt-32">
        {glyphShow !== "all" && (
          <div className="mb-4 flex items-center justify-between gap-4 text-sm text-muted-foreground">
            <span>
              {reviewed} of {glyphs.length} reviewed · {noted} of {fixes.length} fixes noted · open a tile to see its forms and twins, set the verdict and say what a fix needs
            </span>
            <Button size="sm" variant="secondary" onClick={() => void copy()}>
              Copy verdicts
            </Button>
          </div>
        )}
        {glyphs.length === 0 ? (
          <p className="rounded-2xl bg-card px-4 py-10 text-center text-sm text-muted-foreground">
            No glyph matches “{iconQuery.trim()}”.
          </p>
        ) : (
          <ul className="grid grid-cols-[repeat(auto-fill,minmax(8.5rem,1fr))] gap-2">
            {glyphs.map((glyph) => (
              <GlyphTile
                key={glyph.name}
                glyph={glyph}
                size={iconSize}
                form={viewForm(glyph, glyphView)}
                verdict={verdicts[glyph.name]}
                noted={notes[glyph.name] !== undefined}
                onVerdict={() => cycle(glyph.name)}
                onOpen={() => setOpenName(glyph.name)}
              />
            ))}
          </ul>
        )}
        <GlyphReviewDialog
          glyph={openIndex >= 0 ? glyphs[openIndex] : null}
          position={`${openIndex + 1} of ${glyphs.length}`}
          verdict={openName ? verdicts[openName] : undefined}
          onVerdict={(v) => openName && set(openName, v)}
          note={openName ? draft(openName) : ""}
          onNote={(text) => openName && setNote(openName, text)}
          onPrev={() => step(-1)}
          onNext={() => step(1)}
          onClose={() => setOpenName(null)}
        />
      </section>

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
