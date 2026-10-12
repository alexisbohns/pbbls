"use client"

import { useEffect, useState, type RefObject } from "react"

/**
 * The day being read (#1052, spec §Persons of the week, §Contents): the last
 * day section whose top has passed a line 35% down the viewport, as the mock's
 * scroll-spy reads it. The persons' day letter and the contents' rows for that
 * day turn ink.
 *
 * An IntersectionObserver on a one-percent band at that line, with the
 * viewport as root: ancestors clip the targets, so it reads the same whether
 * the page or the week's own column scrolls. Days are contiguous, so the band
 * crosses at most two and the last one entered wins; before the first day
 * reaches it, the first day is read.
 */
export function useReadingDay(hostRef: RefObject<HTMLElement | null>, days: readonly number[]): number | undefined {
  const [reading, setReading] = useState<number | undefined>(undefined)
  // The sections to observe change only when the week's days do.
  const daysKey = days.join()

  useEffect(() => {
    const host = hostRef.current
    if (!host || typeof IntersectionObserver === "undefined") return
    const sections = host.querySelectorAll<HTMLElement>("section[data-day]")
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) setReading(Number((entry.target as HTMLElement).dataset.day))
        }
      },
      { rootMargin: "-35% 0px -64% 0px" },
    )
    sections.forEach((s) => observer.observe(s))
    return () => observer.disconnect()
  }, [hostRef, daysKey])

  return reading !== undefined && days.includes(reading) ? reading : days[0]
}
