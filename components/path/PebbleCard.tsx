import type { Pebble, Emotion, Mark } from "@/lib/types"
import { PebbleVisual } from "@/components/pebble/PebbleVisual"
import { IntensityDots, PositivenessIndicator } from "@/components/pebble/PebbleIndicators"
import { timeFormatter } from "@/lib/utils/formatters"

type PebbleCardProps = {
  pebble: Pebble
  emotion: Emotion | undefined
  mark?: Mark
  soulNames: string[]
  onSelect?: (id: string) => void
}

function EmotionBadge({ emotion }: { emotion: Emotion }) {
  return (
    <span
      className="rounded-full px-2 py-0.5 text-xs font-medium"
      style={{
        backgroundColor: `${emotion.color}20`,
        color: emotion.color,
      }}
    >
      {emotion.name}
    </span>
  )
}

function MetadataRow({
  emotion,
  pebble,
}: {
  emotion: Emotion | undefined
  pebble: Pebble
}) {
  return (
    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
      {emotion && <EmotionBadge emotion={emotion} />}
      <IntensityDots intensity={pebble.intensity} size="xs" />
      <PositivenessIndicator value={pebble.positiveness} size="xs" />
    </div>
  )
}

export function PebbleCard({ pebble, emotion, mark, soulNames, onSelect }: PebbleCardProps) {
  const time = timeFormatter.format(new Date(pebble.happened_at))
  const isLarge = pebble.intensity === 3
  const firstInstant = pebble.instants[0] ?? null

  return (
    <article>
      <button
        type="button"
        onClick={() => onSelect?.(pebble.id)}
        className={
          isLarge
            ? "flex w-full flex-col items-center gap-3 rounded-xl px-4 py-7 text-left transition-all duration-100 bg-muted/30 hover:bg-muted/50 active:scale-[0.98] focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
            : "flex w-full items-center gap-3 rounded-lg px-4 py-3 text-left transition-all duration-100 bg-muted/20 bg-muted/30 hover:bg-muted/50 active:scale-[0.98] focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        }
        aria-label={`${pebble.name}, ${time}`}
      >
        {isLarge ? (
          <>
            <PebbleVisual
              pebble={pebble}
              mark={mark}
              tier="thumbnail"
              className="size-16 shrink-0"
            />

            <h3 className="text-center font-heading text-base font-semibold">
              {pebble.name}
            </h3>

            <time
              dateTime={pebble.happened_at}
              className="text-xs text-muted-foreground"
            >
              {time}
            </time>

            {firstInstant && (
              <img
                src={firstInstant}
                alt={`Instant photo for ${pebble.name}`}
                className="max-h-40 w-full rounded-lg object-cover"
              />
            )}

            <MetadataRow emotion={emotion} pebble={pebble} />

            {soulNames.length > 0 && (
              <span className="text-xs text-muted-foreground">
                <span className="sr-only">With: </span>
                {soulNames.join(", ")}
              </span>
            )}
          </>
        ) : (
          <>
            <PebbleVisual
              pebble={pebble}
              mark={mark}
              tier="thumbnail"
              className="size-10 shrink-0"
            />

            <div className="min-w-0 flex-1">
              <h3 className="font-heading text-sm font-medium">{pebble.name}</h3>

              <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                {emotion && <EmotionBadge emotion={emotion} />}

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
            </div>

            {firstInstant && (
              <img
                src={firstInstant}
                alt={`Instant photo for ${pebble.name}`}
                className="size-8 shrink-0 rounded-md object-cover"
              />
            )}
          </>
        )}
      </button>
    </article>
  )
}
