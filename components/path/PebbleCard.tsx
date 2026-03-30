import Link from "next/link"
import type { Pebble, Emotion } from "@/lib/types"
import { IntensityDots, PositivenessIndicator } from "@/components/pebble/PebbleIndicators"
import { timeFormatter } from "@/lib/utils/formatters"

type PebbleCardProps = {
  pebble: Pebble
  emotion: Emotion | undefined
  soulNames: string[]
}

export function PebbleCard({ pebble, emotion, soulNames }: PebbleCardProps) {
  const time = timeFormatter.format(new Date(pebble.happened_at))

  return (
    <article>
      <Link
        href={`/pebble/${pebble.id}`}
        className="block rounded-lg border border-border px-4 py-3 transition-all duration-100 hover:bg-muted/50 active:scale-[0.98] focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
      >
        <h3 className="text-sm font-medium">{pebble.name}</h3>

        <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          {emotion && (
            <span
              className="rounded-full px-2 py-0.5 text-xs font-medium"
              style={{
                backgroundColor: `${emotion.color}20`,
                color: emotion.color,
              }}
            >
              {emotion.name}
            </span>
          )}

          <IntensityDots intensity={pebble.intensity} size="xs" />
          <PositivenessIndicator value={pebble.positiveness} size="xs" />

          <time dateTime={pebble.happened_at}>{time}</time>

          {soulNames.length > 0 && (
            <span>
              <span className="sr-only">With: </span>
              {soulNames.join(", ")}
            </span>
          )}
        </div>
      </Link>
    </article>
  )
}
