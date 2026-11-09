"use client"

import { cutIndices, drawnForm, formKey, formPaths, isCarved, isLit, type Form, type Glyph, type RenderedPath } from "@/lib/design/glyph-forms"
import { cn } from "@/lib/utils"

const FADE = "transition-[fill-opacity,opacity] duration-300 ease-out motion-reduce:transition-none"

function Drawn({ paths }: { paths: RenderedPath[] }) {
  return paths.map((p, i) => (
    <path key={i} d={p.d} fill={p.fill ? "currentColor" : "none"} fillRule={p.evenOdd ? "evenodd" : undefined} stroke={p.stroke ? "currentColor" : "none"} />
  ))
}

/**
 * One glyph in one form for the design page's review. The strokes are always
 * drawn and each path's fill fades in or out, so marked and progress animate
 * step by step; a hidden path fades in filled when its role fills. A carved
 * form (solid or parts) and the dotted drawing sit in their own layers and
 * crossfade over the strokes. An override is drawn as it is, without a
 * transition. A form the glyph lacks draws regular.
 */
export function GlyphFormView({ glyph, form, className }: { glyph: Glyph; form: Form; className?: string }) {
  const drawn = drawnForm(glyph, form)
  const key = formKey(drawn)
  const override = key === "regular" ? undefined : glyph.overrides[key]
  const layer = override ? "override" : drawn.fill.kind === "dotted" ? "dotted" : isCarved(drawn) ? "carved" : "paths"
  const cut = cutIndices(glyph, drawn)
  // The carved layer keeps the last carved form it showed while it fades out: the solid when the glyph has one.
  const carvedForm: Form | null = layer === "carved" ? drawn : glyph.carved.solid ? { fill: { kind: "solid" } } : null
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round" aria-hidden className={className}>
      {override ? (
        <Drawn paths={override} />
      ) : (
        <>
          <g className={cn(FADE, layer === "paths" ? "opacity-100" : "opacity-0")}>
            {glyph.paths.map((p, i) => {
              const lit = layer === "paths" && isLit(p, drawn)
              const shown = (lit || !p.hidden) && !cut.has(i)
              const carved = p.fill !== undefined
              return (
                <g key={i}>
                  <path d={p.d} fill="currentColor" fillOpacity={lit && !carved ? 1 : 0} stroke={shown ? undefined : "none"} className={cn(FADE, shown ? "opacity-100" : "opacity-0")} />
                  {carved && <path d={p.fill} fill="currentColor" fillRule="evenodd" stroke="none" className={cn(FADE, lit ? "opacity-100" : "opacity-0")} />}
                </g>
              )
            })}
          </g>
          {carvedForm && (
            <g className={cn(FADE, layer === "carved" ? "opacity-100" : "opacity-0")}>
              <Drawn paths={formPaths(glyph, carvedForm)} />
            </g>
          )}
          {glyph.dotted && (
            <g className={cn(FADE, layer === "dotted" ? "opacity-100" : "opacity-0")}>
              <Drawn paths={glyph.dotted} />
            </g>
          )}
        </>
      )}
    </svg>
  )
}
