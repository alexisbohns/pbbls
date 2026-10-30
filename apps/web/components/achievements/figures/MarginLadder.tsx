import type { LadderBar } from "@/lib/achievements/ladders"
import { cn } from "@/lib/utils"

/**
 * A card's margin ladder (#1308, the boards' `.lad`): one 4 px bar per step. A
 * done bar (a served fact) is solid ink; any other is a secondary-ink ring
 * filled from the top by its fraction, so a fill of 1 alone never reads done.
 * `tall` is the domains' single long bar, in px. Decorative: the card's label
 * says the same in words. Android `CardLadder`.
 */
export function MarginLadder({ bars, tall }: { bars: LadderBar[]; tall?: number }) {
  return (
    <div aria-hidden className="flex flex-col items-center gap-1">
      {bars.map((bar, i) => (
        <span
          key={i}
          className={cn(
            "block w-1 rounded-sm",
            bar.done ? "bg-spine-ink" : "shadow-[inset_0_0_0_1px_var(--spine-ink-2)]",
          )}
          style={{
            height: tall ?? 11,
            ...(!bar.done && bar.fill > 0
              ? { background: `linear-gradient(var(--spine-ink) ${(bar.fill * 100).toFixed(1)}%, transparent 0)` }
              : {}),
          }}
        />
      ))}
    </div>
  )
}
