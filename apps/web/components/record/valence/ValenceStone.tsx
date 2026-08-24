"use client"

import { useId } from "react"
import { pebbleScale } from "@/lib/config/pebble-geometry"
import { stoneArt, type StoneArtPart } from "@/lib/valence/stone-art"
import { stonePaint, type Fill, type ValenceMeshIds } from "@/lib/valence/stone-style"
import type { ValenceCell } from "@/lib/valence/valence"
import { cn } from "@/lib/utils"

/**
 * One valence stone, composed the way the Path and the read sheet compose a
 * real pebble: a soft-filled silhouette behind, the artwork inked inside it.
 *
 * The backdrop is the wobbled `pebble-outlines` shape, filled and never
 * stroked. The artwork on top is the wobbled valence ink (the pebble's own
 * outline plus its creature and fossil), tinted and scaled down by
 * `pebbleScale` so the backdrop frames it with the same ~12% margin a real
 * stone gets — instead of the backdrop's edge and the artwork's edge landing on
 * top of each other. A stroked silhouette is what the first iOS cut did, and it
 * is not how a pebble is drawn anywhere else in the app.
 *
 * Selection crossfades two fixed layers rather than transitioning one changing
 * fill. Highlight's resting fill is a gradient and its selected fill is a
 * different gradient — in dark mode a flat colour gives way to one — and no
 * browser interpolates paint servers. Each layer here keeps one fill for its
 * whole life and only its opacity moves. The geometry is shared through a
 * `<use>` pair, so two layers cost one copy of the path data (the nine wobbled
 * artworks are ~286KB of it).
 *
 * Deliberately does not extend `PebbleOutlineBackdrop`: that component's
 * contract is a single flat `#RRGGBB` fill, which cannot express either the
 * `url(#…)` paint the highlight stones need or the two-layer crossfade.
 *
 * Knows nothing about selection semantics or placement: the picker owns both.
 */
export function ValenceStone({
  cell,
  mesh,
  isSelected,
  className,
}: {
  cell: ValenceCell
  mesh: ValenceMeshIds
  isSelected: boolean
  className?: string
}) {
  const art = stoneArt(cell.size, cell.polarity)
  const resting = stonePaint(cell.polarity, false, mesh)
  const selected = stonePaint(cell.polarity, true, mesh)
  // `useId` is not a valid URL fragment on its own (React wraps it in
  // punctuation), and this id is referenced by `href="#…"`.
  const artId = `valence-art-${useId().replace(/[^a-zA-Z0-9_-]/g, "")}`

  const fade = "transition-opacity duration-200 ease-out"

  return (
    <div className={cn("relative size-full", className)} aria-hidden>
      <svg
        className="absolute inset-0 size-full"
        viewBox={`0 0 ${art.backdrop.width} ${art.backdrop.height}`}
        preserveAspectRatio="xMidYMid meet"
      >
        {/* Resting wash. Highlight is the only polarity whose two schemes need
            different materials, and it draws both — the `.dark` cascade picks,
            never a theme read in JS. */}
        <path
          d={art.backdrop.d}
          fillRule={art.backdrop.fillRule}
          className={cn(fade, resting.backdropDark && "dark:hidden")}
          style={{ opacity: isSelected ? 0 : 1 }}
          {...fillProps(resting.backdrop)}
        />
        {resting.backdropDark && (
          <path
            d={art.backdrop.d}
            fillRule={art.backdrop.fillRule}
            className={cn(fade, "hidden dark:block")}
            style={{ opacity: isSelected ? 0 : 1 }}
            {...fillProps(resting.backdropDark)}
          />
        )}
        <path
          d={art.backdrop.d}
          fillRule={art.backdrop.fillRule}
          className={fade}
          style={{ opacity: isSelected ? 1 : 0 }}
          {...fillProps(selected.backdrop)}
        />
      </svg>

      <svg
        className="absolute inset-0 size-full"
        viewBox={`0 0 ${art.artwork.width} ${art.artwork.height}`}
        preserveAspectRatio="xMidYMid meet"
        style={{ transform: `scale(${pebbleScale(cell.size)})` }}
      >
        <defs>
          <g id={artId}>
            {art.artwork.parts.map((part, i) => (
              <ArtPart key={i} part={part} />
            ))}
          </g>
        </defs>
        {/* `fill` and `stroke` both, because the parts are a mix of the two and
            a gradient has to reach either kind. */}
        <use
          href={`#${artId}`}
          fill={resting.ink.paint}
          stroke={resting.ink.paint}
          className={fade}
          style={{ opacity: isSelected ? 0 : 1 }}
        />
        <use
          href={`#${artId}`}
          fill={selected.ink.paint}
          stroke={selected.ink.paint}
          className={fade}
          style={{ opacity: isSelected ? 1 : 0 }}
        />
      </svg>
    </div>
  )
}

/** Parts name no colour of their own — the `<use>` above them supplies it. */
function ArtPart({ part }: { part: StoneArtPart }) {
  if (part.kind === "fill") {
    return <path d={part.d} fillRule={part.fillRule} strokeWidth={0} />
  }
  return (
    <path d={part.d} fill="none" strokeWidth={part.width} strokeLinecap="round" strokeLinejoin="round" />
  )
}

function fillProps(fill: Fill) {
  return { fill: fill.paint, fillOpacity: fill.opacity }
}
