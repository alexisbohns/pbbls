import type { PebbleEnrichmentRow } from "@/lib/analytics/types"

type PebbleEnrichmentProps = {
  row: PebbleEnrichmentRow | null
  rangeLabel?: string
}

type Donut = {
  label: string
  pct: number | null
}

type Ratio = {
  label: string
  pct: number | null
  hint?: string
}

const DONUT_SIZE = 80
const DONUT_STROKE = 10

function DonutSvg({ pct, label }: { pct: number; label: string }) {
  // Centered donut with stroke-dasharray to draw the filled arc.
  const radius = (DONUT_SIZE - DONUT_STROKE) / 2
  const circumference = 2 * Math.PI * radius
  const filled = (Math.max(0, Math.min(100, pct)) / 100) * circumference
  const remaining = circumference - filled

  return (
    <svg
      width={DONUT_SIZE}
      height={DONUT_SIZE}
      viewBox={`0 0 ${DONUT_SIZE} ${DONUT_SIZE}`}
      role="img"
      aria-label={`${label}: ${Math.round(pct)}%`}
    >
      <circle
        cx={DONUT_SIZE / 2}
        cy={DONUT_SIZE / 2}
        r={radius}
        fill="none"
        stroke="var(--muted)"
        strokeWidth={DONUT_STROKE}
      />
      <circle
        cx={DONUT_SIZE / 2}
        cy={DONUT_SIZE / 2}
        r={radius}
        fill="none"
        stroke="var(--foreground)"
        strokeWidth={DONUT_STROKE}
        strokeDasharray={`${filled} ${remaining}`}
        strokeDashoffset={circumference / 4}
        strokeLinecap="round"
        transform={`rotate(-90 ${DONUT_SIZE / 2} ${DONUT_SIZE / 2})`}
      />
      <text
        x="50%"
        y="50%"
        textAnchor="middle"
        dominantBaseline="central"
        className="fill-foreground text-[14px] font-medium tabular-nums"
      >
        {Math.round(pct)}%
      </text>
    </svg>
  )
}

function DonutTile({ donut }: { donut: Donut }) {
  return (
    <div className="flex flex-col items-center gap-2">
      {donut.pct === null ? (
        <div
          className="flex items-center justify-center rounded-full bg-muted text-xs text-muted-foreground"
          style={{ width: DONUT_SIZE, height: DONUT_SIZE }}
          aria-label={`${donut.label}: no data`}
        >
          —
        </div>
      ) : (
        <DonutSvg pct={donut.pct} label={donut.label} />
      )}
      <span className="text-center text-xs text-muted-foreground">
        {donut.label}
      </span>
    </div>
  )
}

function RatioRow({ ratio }: { ratio: Ratio }) {
  return (
    <li className="flex items-baseline justify-between gap-3 text-sm">
      <span className="text-muted-foreground">
        {ratio.label}
        {ratio.hint ? (
          <span className="ml-1 text-xs text-muted-foreground/70">
            {ratio.hint}
          </span>
        ) : null}
      </span>
      <span className="font-medium tabular-nums">
        {ratio.pct === null ? "—" : `${Math.round(ratio.pct)}%`}
      </span>
    </li>
  )
}

export function PebbleEnrichment({ row, rangeLabel }: PebbleEnrichmentProps) {
  if (!row || row.total_pebbles === null || row.total_pebbles === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No pebbles {rangeLabel ? `in the last ${rangeLabel.toLowerCase()}` : "yet"} — enrichment metrics appear once users start collecting.
      </p>
    )
  }

  // The original spec called for three donuts (picture / custom glyph /
  // collection); custom glyph is dropped (no `glyphs.is_custom` — see #347)
  // so only two donuts remain.
  const donuts: Donut[] = [
    { label: "With picture", pct: row.pct_with_picture },
    { label: "In collection", pct: row.pct_in_collection },
  ]

  const ratios: Ratio[] = [
    { label: "With thought", pct: row.pct_with_thought },
    { label: "Linked to ≥1 soul", pct: row.pct_with_soul },
    {
      label: "Intensity set",
      pct: row.pct_with_intensity,
      hint: "(sanity check)",
    },
  ]

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-3">
        {donuts.map((d) => (
          <DonutTile key={d.label} donut={d} />
        ))}
      </div>

      <ul className="space-y-2 border-t pt-4">
        {ratios.map((r) => (
          <RatioRow key={r.label} ratio={r} />
        ))}
      </ul>

      <p className="text-xs text-muted-foreground">
        Based on {row.total_pebbles.toLocaleString()} pebble
        {row.total_pebbles === 1 ? "" : "s"}
        {rangeLabel ? ` in the last ${rangeLabel.toLowerCase()}` : ""}.
      </p>
    </div>
  )
}
