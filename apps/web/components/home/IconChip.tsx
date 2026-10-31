import type { ComponentType } from "react"
import { Stone } from "@/components/stones/Stone"
import { SURFACE_CONTAINER_FILL } from "./home-fills"

/**
 * A row's icon on a small stone (#1384, spec D7, D14): 48 × 46, surface
 * container, the icon in primary. No icon keeps the chip, so titles align.
 * Decorative.
 */
export function IconChip({ seed, icon: Icon }: { seed: string; icon: ComponentType<{ className?: string }> | null }) {
  return (
    <span aria-hidden="true" className="shrink-0">
      <Stone seed={seed} corner={16} fill={SURFACE_CONTAINER_FILL} className="grid h-[46px] w-12 place-items-center">
        {Icon && <Icon className="size-[26px] text-m3-primary" />}
      </Stone>
    </span>
  )
}
