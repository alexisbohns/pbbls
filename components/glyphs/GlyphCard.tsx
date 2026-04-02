import Link from "next/link"
import { PEBBLE_SHAPES } from "@/lib/config"
import type { Mark } from "@/lib/types"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"

type GlyphCardProps = {
  mark: Mark
}

export function GlyphCard({ mark }: GlyphCardProps) {
  const shape = PEBBLE_SHAPES.find((s) => s.id === mark.shape_id)
  const created = new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
  }).format(new Date(mark.created_at))

  return (
    <article>
      <Link
        href={`/glyphs/${mark.id}`}
        className="flex items-center gap-4 rounded-lg border border-border px-4 py-3 transition-all duration-100 hover:bg-muted/50 active:scale-[0.98] focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
      >
        <GlyphPreview
          mark={mark}
          className="w-14 shrink-0 aspect-square"
        />

        <div className="min-w-0">
          <h3 className="text-sm font-medium truncate">
            {mark.name || "Untitled glyph"}
          </h3>
          <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            {shape && <span>{shape.name}</span>}
            <span>{created}</span>
          </div>
        </div>
      </Link>
    </article>
  )
}
