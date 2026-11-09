import type { GlyphSignSet, GlyphView } from "@/lib/design/glyph-forms"
import { cn } from "@/lib/utils"

const MAX_STEP_DOTS = 7

/**
 * The forms a glyph has beyond regular, as discreet signs under its name:
 * ● solid, ◉ mark, ◐ parts (with their count), a dot per step, a dotted ring.
 * The sign of the picker in use is primary. Decorative: the tile's label says
 * the same in words.
 */
export function GlyphSigns({ signs, view }: { signs: GlyphSignSet; view: GlyphView }) {
  const tone = (on: boolean) => (on ? "text-primary" : "text-muted-foreground/60")
  return (
    <span aria-hidden className="pointer-events-none absolute inset-x-0 bottom-1.5 flex h-2 items-center justify-center gap-1.5">
      {signs.solid && (
        <svg viewBox="0 0 6 6" className={cn("size-1.5", tone(view.fill === "solid"))}>
          <circle cx="3" cy="3" r="3" fill="currentColor" />
        </svg>
      )}
      {signs.mark && (
        <svg viewBox="0 0 6 6" className={cn("size-1.5", tone(view.mark === "marked"))}>
          <circle cx="3" cy="3" r="2.4" fill="none" stroke="currentColor" strokeWidth="1.2" />
          <circle cx="3" cy="3" r="1" fill="currentColor" />
        </svg>
      )}
      {signs.parts > 0 && (
        <span className={cn("flex items-center gap-0.5 text-[9px] leading-none tabular-nums", tone(view.fill === "parts"))}>
          <svg viewBox="0 0 6 6" className="size-1.5">
            <circle cx="3" cy="3" r="2.4" fill="none" stroke="currentColor" strokeWidth="1.2" />
            <path d="M3 0.6 A2.4 2.4 0 0 0 3 5.4 Z" fill="currentColor" />
          </svg>
          {signs.parts}
        </span>
      )}
      {signs.steps > 0 && (
        <span className={cn("flex items-center gap-px", tone(view.progress))}>
          {Array.from({ length: Math.min(signs.steps, MAX_STEP_DOTS) }, (_, i) => (
            <svg key={i} viewBox="0 0 2 2" className="size-[3px]">
              <circle cx="1" cy="1" r="1" fill="currentColor" />
            </svg>
          ))}
          {signs.steps > MAX_STEP_DOTS && <span className="text-[9px] leading-none">+</span>}
        </span>
      )}
      {signs.dotted && (
        <svg viewBox="0 0 6 6" className={cn("size-1.5", tone(view.fill === "dotted"))}>
          <circle cx="3" cy="3" r="2.4" fill="none" stroke="currentColor" strokeWidth="1.2" strokeDasharray="1.1 0.9" />
        </svg>
      )}
    </span>
  )
}
