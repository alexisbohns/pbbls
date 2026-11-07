"use client"

import { useEffect, useState } from "react"

/** How far below the top of the viewport a heading counts as read. */
const READ_LINE = 140

/**
 * The id of the section being read: the last of `ids` whose heading has
 * crossed the read line. Listens to scrolls in the capture phase, so it
 * follows whichever element scrolls the page (<main> in the app shell).
 */
export function useActiveHeading(ids: readonly string[]): string | null {
  const [active, setActive] = useState<string | null>(null)
  const key = ids.join("|")

  useEffect(() => {
    const list = key ? key.split("|") : []
    let frame = 0
    const measure = () => {
      frame = 0
      let current: string | null = null
      for (const id of list) {
        const el = document.getElementById(id)
        if (el && el.getBoundingClientRect().top <= READ_LINE) current = id
      }
      setActive(current)
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(measure)
    }
    measure()
    document.addEventListener("scroll", onScroll, { capture: true, passive: true })
    return () => {
      document.removeEventListener("scroll", onScroll, { capture: true })
      if (frame) cancelAnimationFrame(frame)
    }
  }, [key])

  return active
}
