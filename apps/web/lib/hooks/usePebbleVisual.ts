"use client"

import { useMemo } from "react"

import type { Mark, Pebble } from "@/lib/types"
import { hashUUID, renderPebble, toPebbleParams } from "@/lib/engine"
import type { RenderTier } from "@/lib/engine"

type UsePebbleVisualResult = {
  svg: string
  viewBox: string
}

export function usePebbleVisual(
  pebble: Pebble,
  mark: Mark | null,
  tier: RenderTier,
): UsePebbleVisualResult {
  const strokesKey = JSON.stringify(mark?.strokes ?? null)

  // Only visual-relevant scalar properties are listed as deps to avoid
  // recomputation when non-visual fields (e.g. updated_at, soul_ids) change.
  /* eslint-disable react-hooks/exhaustive-deps */
  return useMemo(() => {
    const params = toPebbleParams(pebble, mark)
    const seed = hashUUID(pebble.id)
    return renderPebble(params, seed, tier)
  }, [
    pebble.id,
    pebble.intensity,
    pebble.positiveness,
    pebble.emotion_id,
    pebble.mark_id,
    pebble.created_at,
    pebble.happened_at,
    mark?.viewBox,
    strokesKey,
    tier,
  ])
  /* eslint-enable react-hooks/exhaustive-deps */
}
