import type { Mark } from "@/lib/types"
import { cn } from "@/lib/utils"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"

type GlyphPickerGridProps = {
  marks: Mark[]
  selectedMarkId: string | undefined
  onSelect: (id: string | undefined) => void
}

export function GlyphPickerGrid({ marks, selectedMarkId, onSelect }: GlyphPickerGridProps) {
  if (marks.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No glyphs yet. Carve one from the Glyphs page.
      </p>
    )
  }

  return (
    <ul
      role="radiogroup"
      aria-label="Glyphs"
      className="grid grid-cols-4 gap-2"
    >
      {marks.map((mark) => {
        const selected = selectedMarkId === mark.id
        return (
          <li key={mark.id}>
            <button
              type="button"
              role="radio"
              aria-checked={selected}
              aria-label={mark.name ?? `Glyph ${mark.id.slice(0, 4)}`}
              onClick={() => onSelect(selected ? undefined : mark.id)}
              className={cn(
                "flex size-16 items-center justify-center rounded-lg border p-1 transition-all duration-100 outline-none focus-visible:ring-3 focus-visible:ring-ring/50 active:scale-95",
                selected
                  ? "border-primary bg-primary/10 ring-2 ring-primary"
                  : "border-input hover:bg-muted",
              )}
            >
              <GlyphPreview mark={mark} className="size-full" />
            </button>
          </li>
        )
      })}
    </ul>
  )
}
