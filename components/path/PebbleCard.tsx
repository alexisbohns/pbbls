import Link from "next/link"
import type { Pebble, Emotion } from "@/lib/types"

type PebbleCardProps = {
  pebble: Pebble
  emotion: Emotion | undefined
  soulNames: string[]
}

const timeFormatter = new Intl.DateTimeFormat("en-US", {
  hour: "numeric",
  minute: "numeric",
})

const positivenessSigns: Record<number, string> = {
  [-2]: "−−",
  [-1]: "−",
  [0]: "~",
  [1]: "+",
  [2]: "++",
}

function IntensityDots({ intensity }: { intensity: 1 | 2 | 3 }) {
  const filled = "●".repeat(intensity)
  const empty = "○".repeat(3 - intensity)
  return (
    <abbr
      title={`Intensity: ${intensity} of 3`}
      className="text-xs tracking-wide no-underline"
    >
      {filled}
      {empty}
    </abbr>
  )
}

function PositivenessIndicator({ value }: { value: number }) {
  const sign = positivenessSigns[value] ?? "~"
  return (
    <abbr
      title={`Positiveness: ${value}`}
      className="text-xs font-medium no-underline"
    >
      {sign}
    </abbr>
  )
}

export function PebbleCard({ pebble, emotion, soulNames }: PebbleCardProps) {
  const time = timeFormatter.format(new Date(pebble.happened_at))

  return (
    <article>
      <Link
        href={`/pebble/${pebble.id}`}
        className="block rounded-lg border border-border px-4 py-3 transition-colors hover:bg-muted/50 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
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
              aria-label={`Emotion: ${emotion.name}`}
            >
              {emotion.name}
            </span>
          )}

          <IntensityDots intensity={pebble.intensity} />
          <PositivenessIndicator value={pebble.positiveness} />

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
