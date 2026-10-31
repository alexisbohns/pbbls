/** New since the card was last opened (#1308, D10; the boards' `newMark`): a ring round the mark and a dot at its shoulder. */
export function NewMark({ x, y, width }: { x: number; y: number; width: number }) {
  return (
    <>
      <circle cx={x} cy={y} r={width * 0.86} fill="none" stroke="var(--spine-ink)" strokeWidth={1} />
      <circle cx={x + width * 0.66} cy={y - width * 0.66} r={1.9} fill="var(--spine-ink)" />
    </>
  )
}
