import Link from "next/link"
import type { Soul } from "@/lib/types"
import { DeleteSoulDialog } from "@/components/souls/DeleteSoulDialog"

type SoulCardProps = {
  soul: Soul
  pebbleCount: number
  onDelete: (id: string) => void
}

export function SoulCard({ soul, pebbleCount, onDelete }: SoulCardProps) {
  return (
    <article className="flex items-center gap-2">
      <Link
        href={`/souls/${soul.id}`}
        className="flex-1 rounded-lg border border-border px-4 py-3 transition-all duration-100 hover:bg-muted/50 active:scale-[0.98] focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
      >
        <h3 className="text-sm font-medium">{soul.name}</h3>

        <p className="mt-1.5 text-xs text-muted-foreground">
          {pebbleCount} {pebbleCount === 1 ? "pebble" : "pebbles"}
        </p>
      </Link>

      <DeleteSoulDialog
        soulName={soul.name}
        onConfirm={() => onDelete(soul.id)}
      />
    </article>
  )
}
