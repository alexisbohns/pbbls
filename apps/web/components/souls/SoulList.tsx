import type { Soul } from "@/lib/types"
import { SoulCard } from "@/components/souls/SoulCard"

type SoulListProps = {
  souls: Soul[]
  pebbleCounts: Map<string, number>
  onDelete: (id: string) => void
}

export function SoulList({ souls, pebbleCounts, onDelete }: SoulListProps) {
  return (
    <ul className="flex flex-col gap-2">
      {souls.map((soul) => (
        <li key={soul.id}>
          <SoulCard
            soul={soul}
            pebbleCount={pebbleCounts.get(soul.id) ?? 0}
            onDelete={onDelete}
          />
        </li>
      ))}
    </ul>
  )
}
