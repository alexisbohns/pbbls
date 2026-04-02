import type { Mark } from "@/lib/types"
import { GlyphCard } from "@/components/glyphs/GlyphCard"

type GlyphListProps = {
  marks: Mark[]
}

export function GlyphList({ marks }: GlyphListProps) {
  return (
    <ul className="flex flex-col gap-2">
      {marks.map((mark) => (
        <li key={mark.id}>
          <GlyphCard mark={mark} />
        </li>
      ))}
    </ul>
  )
}
