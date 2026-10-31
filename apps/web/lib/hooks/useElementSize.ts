"use client"

import { useCallback, useEffect, useRef, useState } from "react"

export type ElementSize = { width: number; height: number }

/** Quarter pixels: iOS's stone cache key, and enough to ignore sub-pixel jitter. */
function quarter(v: number): number {
  return Math.round(v * 4) / 4
}

/**
 * The element's border-box size, measured by a ResizeObserver (#1384, spec D7).
 * Updates are coalesced into one animation frame and rounded to quarter
 * pixels, so a window drag redraws a stone at most once a frame. `null` until
 * the first measurement (and on the server).
 */
export function useElementSize<T extends HTMLElement>(): [(node: T | null) => void, ElementSize | null] {
  const [node, setNode] = useState<T | null>(null)
  const [size, setSize] = useState<ElementSize | null>(null)
  const frame = useRef<number | null>(null)

  const ref = useCallback((el: T | null) => setNode(el), [])

  useEffect(() => {
    if (!node || typeof ResizeObserver === "undefined") return
    const measure = (width: number, height: number) => {
      const next = { width: quarter(width), height: quarter(height) }
      setSize((prev) => (prev && prev.width === next.width && prev.height === next.height ? prev : next))
    }
    const observer = new ResizeObserver((entries) => {
      const entry = entries[entries.length - 1]
      const box = entry.borderBoxSize?.[0]
      const width = box ? box.inlineSize : entry.contentRect.width
      const height = box ? box.blockSize : entry.contentRect.height
      if (frame.current !== null) cancelAnimationFrame(frame.current)
      frame.current = requestAnimationFrame(() => {
        frame.current = null
        measure(width, height)
      })
    })
    observer.observe(node, { box: "border-box" })
    return () => {
      observer.disconnect()
      if (frame.current !== null) cancelAnimationFrame(frame.current)
      frame.current = null
    }
  }, [node])

  return [ref, size]
}
