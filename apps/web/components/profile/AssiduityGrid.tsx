import { cn } from "@/lib/utils"

type AssiduityGridProps = {
  /** 28-element window: index 0 = 27 days ago … index 27 = today. */
  data: boolean[]
}

/**
 * 7-column, 28-cell activity grid (last 28 days) — web port of the iOS
 * `AssiduityGrid`. A filled day reads in the accent color; an inactive day in
 * the border tone.
 */
export function AssiduityGrid({ data }: AssiduityGridProps) {
  const cells = Array.from({ length: 28 }, (_, i) => data[i] ?? false)

  return (
    <div className="grid shrink-0 grid-cols-7 gap-1" aria-hidden>
      {cells.map((active, i) => (
        <span
          key={i}
          className={cn(
            "size-1.5 rounded-[2px]",
            active ? "bg-primary" : "bg-border",
          )}
        />
      ))}
    </div>
  )
}
