"use client"

import { cutIndices, drawnForm, formKey, formPaths, isLit, type Form, type Glyph } from "@/lib/design/glyph-forms"
import { cn } from "@/lib/utils"

const FADE = "transition-[fill-opacity,opacity] duration-300 ease-out motion-reduce:transition-none"

/**
 * One glyph in one form for the design page's review. The strokes are always
 * drawn and each path's fill fades in or out, so half and progress animate
 * mark by mark; a hidden path (not drawn at rest) fades in filled when its role
 * fills; the carved solid sits in its own layer and crossfades over the
 * strokes. An override is drawn as it is, without a transition.
 */
export function GlyphFormView({ glyph, form, className }: { glyph: Glyph; form: Form; className?: string }) {
  const drawn = drawnForm(glyph, form)
  const override = formKey(drawn) === "regular" ? undefined : glyph.overrides[formKey(drawn)]
  const cut = cutIndices(glyph, drawn)
  const showSolid = drawn.fill.kind === "solid" && !override
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round" aria-hidden className={className}>
      {override ? (
        override.map((p, i) => (
          <path key={i} d={p.d} fill={p.fill ? "currentColor" : "none"} fillRule={p.evenOdd ? "evenodd" : undefined} stroke={p.stroke ? "currentColor" : "none"} />
        ))
      ) : (
        <>
          <g className={cn(FADE, showSolid ? "opacity-0" : "opacity-100")}>
            {glyph.paths.map((p, i) => {
              const lit = isLit(p, drawn)
              const shown = (lit || !p.hidden) && !cut.has(i)
              const carved = p.fill !== undefined
              return (
                <g key={i}>
                  <path
                    d={p.d}
                    fill="currentColor"
                    fillOpacity={lit && !carved ? 1 : 0}
                    stroke={shown ? undefined : "none"}
                    className={cn(FADE, shown ? "opacity-100" : "opacity-0")}
                  />
                  {carved && <path d={p.fill} fill="currentColor" fillRule="evenodd" stroke="none" className={cn(FADE, lit ? "opacity-100" : "opacity-0")} />}
                </g>
              )
            })}
          </g>
          {glyph.carved.solid && (
            <g className={cn(FADE, showSolid ? "opacity-100" : "opacity-0")}>
              {formPaths(glyph, { fill: { kind: "solid" } }).map((p, i) => (
                <path key={i} d={p.d} fill={p.fill ? "currentColor" : "none"} fillRule={p.evenOdd ? "evenodd" : undefined} stroke={p.stroke ? "currentColor" : "none"} />
              ))}
            </g>
          )}
        </>
      )}
    </svg>
  )
}
