import type { Mark, Soul } from "@/lib/types"
import { SoulCard } from "@/components/souls/SoulCard"

type SoulListProps = {
  souls: Soul[]
  marks: Mark[]
  pebbleCounts: Map<string, number>
}

export function SoulList({ souls, marks, pebbleCounts }: SoulListProps) {
  return (
    <ul className="grid grid-cols-3 gap-3">
      {souls.map((soul) => (
        <li key={soul.id}>
          <SoulCard
            soul={soul}
            mark={marks.find((m) => m.id === soul.glyph_id)}
            pebbleCount={pebbleCounts.get(soul.id) ?? 0}
          />
        </li>
      ))}
    </ul>
  )
}
