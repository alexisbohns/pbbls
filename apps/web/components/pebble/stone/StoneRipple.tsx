"use client"

import { useEffect, useRef } from "react"
import { getOutline } from "@/lib/config/pebble-outlines"
import type { Polarity, Size } from "@/lib/config/pebble-geometry"
import { RIPPLE_REACH, WAVE } from "@/lib/config/stone-motion"
import { stoneBodyId } from "@/lib/utils/spine/stone-ids"

type StoneRippleProps = {
  size: Size
  polarity: Polarity
  /** After the last wave finishes: unmount me. */
  onDone: () => void
  className?: string
}

/**
 * Three stone-shaped waves travelling out across the ground from a pressed
 * stone (#1052). Mounted on press and gone when the last wave finishes, so a
 * still page holds none of it. Lay it over the stone's own box.
 */
export function StoneRipple({ size, polarity, onDone, className }: StoneRippleProps) {
  const ref = useRef<SVGSVGElement>(null)
  const done = useRef(onDone)

  useEffect(() => {
    done.current = onDone
  }, [onDone])

  useEffect(() => {
    const waves = ref.current ? Array.from(ref.current.querySelectorAll<SVGElement>("[data-wave]")) : []
    const reach = RIPPLE_REACH[size]
    const animations = waves.map((wave, k) =>
      wave.animate(
        [
          { transform: "scale(1.15)", opacity: 0 },
          { transform: "scale(1.45)", opacity: 0.85, offset: 0.07 },
          { transform: `scale(${reach - k * 0.5})`, opacity: 0 },
        ],
        {
          duration: WAVE.duration + k * WAVE.durationStep,
          delay: WAVE.delay + k * WAVE.delayStep,
          easing: WAVE.easing,
        },
      ),
    )
    const last = animations.at(-1)
    if (!last) {
      done.current()
      return
    }
    last.onfinish = () => done.current()
    return () => animations.forEach((a) => a.cancel())
  }, [size])

  const outline = getOutline(size, polarity)
  return (
    <svg
      ref={ref}
      aria-hidden
      focusable="false"
      viewBox={`0 0 ${outline.width} ${outline.height}`}
      className={className}
      overflow="visible"
    >
      {[0, 1, 2].map((k) => (
        <use
          key={k}
          href={`#${stoneBodyId(size, polarity)}`}
          fill="none"
          stroke="var(--spine-ripple)"
          strokeWidth="1px"
          opacity={0}
          style={{ transformBox: "fill-box", transformOrigin: "center" }}
          data-wave=""
        />
      ))}
    </svg>
  )
}
