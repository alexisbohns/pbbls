import type { Mark } from "@/lib/types"
import { StoneGlyph } from "@/components/pebble/stone/StoneGlyph"
import { cn } from "@/lib/utils"

type SoulMarkProps = {
  mark: Mark | undefined
  /** Line weight in px of an engine-width stroke. */
  weight?: number
  className?: string
}

/**
 * A soul's glyph as a constant thin line, for the Spine's people lines. A soul
 * whose glyph is not in the mark map keeps the dashed placeholder
 * `SoulGlyphThumbnail` uses.
 */
export function SoulMark({ mark, weight = 1.1, className }: SoulMarkProps) {
  if (!mark) {
    return (
      <span
        aria-hidden
        className={cn("inline-block rounded-[3px] border border-dashed border-(--spine-ink-3)", className)}
      />
    )
  }
  return (
    <svg aria-hidden focusable="false" viewBox={mark.viewBox} className={cn("inline-block overflow-visible", className)}>
      <StoneGlyph strokes={mark.strokes} weight={weight} stroke="currentColor" />
    </svg>
  )
}
