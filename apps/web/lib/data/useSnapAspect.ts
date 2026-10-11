"use client"

import { useCallback, useEffect, useState } from "react"
import type { PebbleCover } from "@/lib/types"

// A snap's ratio (width / height), known once per session. `snaps` stores no
// dimensions yet (spec §Data: stored width/height is proposed to the moments
// split), so the card learns it from the thumb, which has the original's ratio
// at a fraction of its bytes, or from the photo itself, whichever lands first.
const ratioBySnap = new Map<string, number>()
const inflight = new Map<string, Promise<number | null>>()

const PROBE_TIMEOUT_MS = 4000

function probe(snapId: string, url: string): Promise<number | null> {
  const pending = inflight.get(snapId)
  if (pending) return pending
  const image = new Image()
  image.decoding = "async"
  image.src = url
  const timeout = new Promise<null>((resolve) => {
    setTimeout(() => resolve(null), PROBE_TIMEOUT_MS)
  })
  const decoded = image
    .decode()
    .then(() => (image.naturalHeight > 0 ? image.naturalWidth / image.naturalHeight : null))
    .catch(() => null)
  const result = Promise.race([decoded, timeout]).then((ratio) => {
    inflight.delete(snapId)
    if (ratio) ratioBySnap.set(snapId, ratio)
    else console.warn("[spine] snap ratio probe failed or timed out", { snapId })
    return ratio
  })
  inflight.set(snapId, result)
  return result
}

/** Seed a known ratio, as a fixture does for photos it ships with. */
export function primeSnapAspect(snapId: string, ratio: number): void {
  ratioBySnap.set(snapId, ratio)
}

/**
 * The cover's ratio, or null until it is known (the card is square until then,
 * spec D14). `learn` takes the ratio from the photo itself when it loads first.
 */
export function useSnapAspect(cover: PebbleCover | null): {
  ratio: number | null
  learn: (width: number, height: number) => void
} {
  const snapId = cover?.snapId ?? null
  const [learned, setLearned] = useState<{ snapId: string; ratio: number } | null>(null)
  const cached = snapId ? ratioBySnap.get(snapId) ?? null : null
  const ratio = cached ?? (learned && learned.snapId === snapId ? learned.ratio : null)

  const url = cover?.thumbUrl ?? cover?.url ?? null
  useEffect(() => {
    if (!snapId || !url || ratioBySnap.has(snapId)) return
    let cancelled = false
    probe(snapId, url).then((found) => {
      if (!cancelled && found) setLearned({ snapId, ratio: found })
    })
    return () => {
      cancelled = true
    }
  }, [snapId, url])

  const learn = useCallback(
    (width: number, height: number) => {
      if (!snapId || height <= 0 || ratioBySnap.has(snapId)) return
      const found = width / height
      ratioBySnap.set(snapId, found)
      setLearned({ snapId, ratio: found })
    },
    [snapId],
  )

  return { ratio, learn }
}
