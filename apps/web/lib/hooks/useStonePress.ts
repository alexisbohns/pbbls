"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { DIP, PRESS_VIBRATION_MS, RINGS, SPARK, STILL_TOUCH_MS, SWEEP } from "@/lib/config/stone-motion"
import { useHaptics } from "./useHaptics"

function prefersReducedMotion(): boolean {
  return typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches
}

/**
 * The press of a lit stone (#1052): the stone dips, its rings swell, a glint
 * crosses it and three waves travel out (`ripple` mounts a `StoneRipple`;
 * `endRipple` unmounts it). Every animation is a finite WAAPI one on the
 * elements `LitStone` marks with `data-stone-*`, found under `hostRef`.
 *
 * With reduced motion nothing moves: `touched` is true for a moment, which
 * darkens the rings and holds a still sheen.
 */
export function useStonePress() {
  const hostRef = useRef<HTMLElement>(null)
  const [ripple, setRipple] = useState<number | null>(null)
  const [touched, setTouched] = useState(false)
  const stillTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const { vibrate } = useHaptics()

  useEffect(
    () => () => {
      if (stillTimer.current) clearTimeout(stillTimer.current)
    },
    [],
  )

  const press = useCallback(() => {
    const host = hostRef.current
    if (!host) return
    vibrate(PRESS_VIBRATION_MS)

    const stone = host.querySelector<HTMLElement>("[data-stone-press]")
    if (prefersReducedMotion() || !stone?.animate) {
      setTouched(true)
      if (stillTimer.current) clearTimeout(stillTimer.current)
      stillTimer.current = setTimeout(() => setTouched(false), STILL_TOUCH_MS)
      return
    }

    stone.animate(DIP.keyframes, DIP.options)
    host.querySelector<SVGElement>("[data-stone-rings]")?.animate(RINGS.keyframes, RINGS.options)
    const sweep = host.querySelector<SVGElement>("[data-stone-sweep]")
    if (sweep) {
      // In the sweep's own (canvas) units: CSS px on an SVG element are user units.
      const travel = Number(sweep.dataset.stoneSweep) || 0
      sweep.animate([{ transform: "translateX(0)" }, { transform: `translateX(${travel}px)` }], SWEEP)
    }
    host.querySelector<SVGElement>("[data-stone-spark]")?.animate(SPARK.keyframes, SPARK.options)
    setRipple((n) => (n ?? 0) + 1)
  }, [vibrate])

  const endRipple = useCallback(() => setRipple(null), [])

  return { hostRef, press, ripple, endRipple, touched }
}
