import type { Polarity, Size } from "@/lib/config/pebble-geometry"

// The ids `StoneSprite` defines and every lit stone and seal references.
// Fixed, not `useId`: `<use>` and `url(#…)` reach across SVGs by document id,
// and the sprite is rendered once per page.

export const stoneBodyId = (size: Size, polarity: Polarity) => `stone-body-${size}-${polarity}`
/** The flat silhouette the rings and halo are raked from (no wobble to magnify). */
export const stoneRingId = (size: Size, polarity: Polarity) => `stone-ring-${size}-${polarity}`
export const stoneClipId = (size: Size, polarity: Polarity) => `stone-clip-${size}-${polarity}`
export const stoneHatchId = (size: Size) => `stone-hatch-${size}`
export const stoneBlurId = (name: "near" | "far" | "ao", size: Size) => `stone-${name}-${size}`
export const STONE_SOFT_ID = "stone-soft"
export const STONE_GLOW_ID = "stone-glow"
export const STONE_GRAIN_ID = "stone-grain"
export const STONE_GLINT_SWEEP_ID = "stone-glint-sweep"
export const STONE_GLINT_SHEEN_ID = "stone-glint-sheen"
