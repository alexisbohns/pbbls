import type { Size } from "@/lib/config/pebble-geometry"
import { outlineAspectRatio } from "@/lib/config/pebble-geometry"
import { STONE_WIDTH } from "@/lib/config/stone-geometry"

/** The stone's box width, phone then wide. Static strings, so Tailwind sees them. */
export const STONE_WIDTH_CLASS: Record<Size, string> = {
  small: "w-[34px] spread:w-[40px]",
  medium: "w-[44px] spread:w-[52px]",
  large: "w-[54px] spread:w-[58px]",
}

/** The stone's on-screen height in px, phone and wide. */
export function stoneHeight(size: Size): { phone: number; wide: number } {
  const ratio = outlineAspectRatio(size)
  return {
    phone: Math.round((STONE_WIDTH[size].phone / ratio) * 10) / 10,
    wide: Math.round((STONE_WIDTH[size].wide / ratio) * 10) / 10,
  }
}
