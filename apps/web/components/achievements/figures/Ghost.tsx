/** "Not yet" (#1308, the boards' `ghost`): a dotted socket in secondary ink, 1 unit, on the stock. */
export function Ghost({ x, y, r = 3.2 }: { x: number; y: number; r?: number }) {
  return (
    <circle
      cx={x}
      cy={y}
      r={r}
      fill="var(--spine-stock)"
      stroke="var(--spine-ink-2)"
      strokeWidth={1}
      strokeDasharray="1.2 1.6"
      strokeLinecap="round"
    />
  )
}
