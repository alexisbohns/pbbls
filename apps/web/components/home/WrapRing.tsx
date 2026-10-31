/**
 * A month's ring (#1384, spec D13): a dot per day around the circle, filled
 * where the month holds a pebble. The day total is calendar arithmetic on the
 * served month. Decorative: the card's name says the month and its count.
 */
export function WrapRing({ daysInMonth, days, size = 116 }: { daysInMonth: number; days: readonly number[]; size?: number }) {
  const filled = new Set(days)
  const dotR = 1.9
  const fillR = 3.6
  const c = size / 2
  const r = size / 2 - fillR - 1
  return (
    <svg aria-hidden="true" width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      {Array.from({ length: daysInMonth }, (_, i) => {
        const a = -Math.PI / 2 + (i * 2 * Math.PI) / daysInMonth
        const x = (c + r * Math.cos(a)).toFixed(2)
        const y = (c + r * Math.sin(a)).toFixed(2)
        return filled.has(i) ? (
          <circle key={i} cx={x} cy={y} r={fillR} className="fill-m3-primary" />
        ) : (
          <circle key={i} cx={x} cy={y} r={dotR} fill="none" strokeWidth={1.1} className="stroke-m3-outline-variant" />
        )
      })}
    </svg>
  )
}
