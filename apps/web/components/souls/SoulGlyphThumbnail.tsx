import type { Mark } from "@/lib/types"
import { cn } from "@/lib/utils"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"

type SoulGlyphThumbnailProps = {
  mark: Mark | undefined
  className?: string
}

// Renders a soul's glyph thumbnail. When the soul still points at the system
// default glyph (or its row is not yet hydrated in `marks`), falls back to a
// dashed placeholder — same affordance iOS uses in `CreateSoulSheet.GlyphRow`.
export function SoulGlyphThumbnail({ mark, className }: SoulGlyphThumbnailProps) {
  if (mark) return <GlyphPreview mark={mark} className={className} />
  return (
    <span
      aria-hidden="true"
      className={cn(
        "block rounded-md border border-dashed border-muted-foreground/40",
        className,
      )}
    />
  )
}
