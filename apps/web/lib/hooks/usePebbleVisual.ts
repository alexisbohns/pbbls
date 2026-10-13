"use client"

import { useMemo } from "react"

import { renderPebble, toPebbleParams, type EngineOutput } from "@/lib/engine"
import { WOBBLE_ENABLED, wobblePebbleSvg } from "@/lib/wobble"

type PebbleVisualInput = {
  intensity: number
  positiveness: number
  happened_at: string
  created_at?: string
}

/**
 * The pebble's body (template, fossil, wobble) as a static SVG string, glyph
 * zone empty. Only the visual inputs are deps, so a name or soul edit does not
 * recompute it.
 */
export function usePebbleVisual({
  intensity,
  positiveness,
  happened_at,
  created_at,
}: PebbleVisualInput): EngineOutput {
  return useMemo(() => {
    const out = renderPebble(toPebbleParams({ intensity, positiveness, happened_at, created_at }))
    // Petroglyph wobble (#555): rewrites the body's stroked paths into leaky
    // filled ink. Content-cached.
    return WOBBLE_ENABLED ? { ...out, svg: wobblePebbleSvg(out.svg) } : out
  }, [intensity, positiveness, happened_at, created_at])
}
