"use client"

import type { CSSProperties } from "react"
import type { Mark, Pebble } from "@/lib/types"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { PebbleFramed } from "@/components/pebble/PebbleFramed"
import { cn } from "@/lib/utils"

/**
 * The compact row, forked from `components/path/PathPebbleRow` for the sandbox.
 *
 * Forked rather than given a prop: the experiment changes what this row *is*
 * (Caveat name, no timestamp), and threading that through the shipped component
 * would put sandbox-only branches in the code that renders the real Path.
 */

export function rotation(positionIndex: number): number {
  return positionIndex % 2 === 0 ? -7 : 4
}

export function rowHeight(intensity: 1 | 2 | 3, hasPhoto: boolean, positionIndex: number): number {
  if (intensity === 3) return 100
  if (!hasPhoto) return 60
  return positionIndex % 2 === 0 ? 71 : 68
}

export function SandboxPebbleRow({
  pebble,
  mark,
  positionIndex,
  onSelect,
}: {
  pebble: Pebble
  mark?: Mark
  positionIndex: number
  onSelect?: (id: string) => void
}) {
  const { paletteByEmotionId } = useEmotionPalettes()
  const palette = paletteByEmotionId.get(pebble.emotion_id)

  const photoUrl = pebble.instants[0]
  const heightPx = rowHeight(pebble.intensity, Boolean(photoUrl), positionIndex)

  // Palette goes in as CSS custom properties so the dark-mode swap happens via
  // the `.dark` cascade in globals.css rather than a JS-read theme (which would
  // cause an SSR/CSR mismatch).
  const rowStyle: CSSProperties = palette
    ? ({
        height: heightPx,
        ["--path-row-name-light"]: palette.primary_color,
        ["--path-row-name-dark"]: palette.light_color,
      } as CSSProperties)
    : { height: heightPx }

  return (
    <button
      type="button"
      onClick={() => onSelect?.(pebble.id)}
      className={cn(
        "path-row flex w-full items-center gap-3 rounded-xl p-2 text-left transition-all duration-100",
        "hover:bg-background active:scale-[0.98] focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
      )}
      style={rowStyle}
      aria-label={pebble.name}
    >
      <PebbleFramed pebble={pebble} mark={mark} tier="thumbnail" className="size-14 shrink-0" />

      <h3
        className={cn(
          "path-row-name min-w-0 flex-1 truncate font-hand font-bold",
          // Leading after the size — Tailwind's text-* utilities also set
          // line-height, so tailwind-merge drops an earlier `leading-*`.
          "text-[1.125rem] leading-[1.1]",
          !palette && "text-foreground",
        )}
      >
        {pebble.name}
      </h3>

      {photoUrl && (
        /* eslint-disable-next-line @next/next/no-img-element -- local fixture asset, next/image not applicable */
        <img
          src={photoUrl}
          alt=""
          loading="lazy"
          className="size-16 shrink-0 rounded-lg object-cover shadow-sm ring-4 ring-background"
          style={{ transform: `rotate(${rotation(positionIndex)}deg)` }}
        />
      )}
    </button>
  )
}
