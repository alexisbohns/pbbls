"use client"

import type { CSSProperties } from "react"
import type { Mark, Pebble, Soul } from "@/lib/types"
import { polaroidChaos } from "@/lib/utils/polaroid-chaos"
import { SANDBOX_MARK_MAP, SANDBOX_SOUL_MAP } from "@/lib/seed/sandbox-pebbles"
import { SoulGlyphThumbnail } from "@/components/souls/SoulGlyphThumbnail"
import { PolaroidStone, type StoneSize } from "./PolaroidStone"
import { cn } from "@/lib/utils"

/** The stock itself — paper, corners, and what lifts it off the page. In dark mode
 *  a drop shadow cannot read against a near-black background, so the card swaps to
 *  a faint inset top highlight instead. */
const STOCK =
  "bg-card rounded-sm " +
  "shadow-[0_1px_0_rgba(0,0,0,0.02),0_4px_8px_-4px_rgba(0,0,0,0.06),0_14px_28px_-16px_rgba(0,0,0,0.09)] " +
  "dark:shadow-[inset_0_1px_0_rgba(255,255,255,0.05),0_4px_8px_-4px_rgba(0,0,0,0.45),0_14px_28px_-16px_rgba(0,0,0,0.6)]"

/** How a card answers the pointer. Written as `group-*` so it fires from the
 *  wrapper — the wrapper carries the static chaos rotation, the card carries the
 *  interaction transform, and the two compose. On one element the hover transform
 *  would replace the rotation instead of adding to it. */
const HOVER =
  "transition-[transform,box-shadow] duration-300 ease-out " +
  "group-hover:-rotate-3 group-hover:scale-105 " +
  "group-has-[:focus-visible]:-rotate-3 group-has-[:focus-visible]:scale-105 " +
  "group-hover:shadow-[0_2px_0_rgba(0,0,0,0.03),0_8px_16px_-6px_rgba(0,0,0,0.10),0_24px_40px_-16px_rgba(0,0,0,0.16)] " +
  "dark:group-hover:shadow-[inset_0_1px_0_rgba(255,255,255,0.06),0_8px_16px_-6px_rgba(0,0,0,0.55),0_24px_40px_-16px_rgba(0,0,0,0.75)] " +
  "group-active:rotate-2 group-active:scale-95"

/** Top padding on the paper, and how far the stone is pulled above the edge. The
 *  two are paired: the stone hangs by roughly half its height, and the paper opens
 *  up enough that the meta row still clears it. */
const HEAD: Record<StoneSize, { pad: string; lift: string }> = {
  sm: { pad: "pt-7", lift: "-top-5" },
  md: { pad: "pt-9", lift: "-top-7" },
  lg: { pad: "pt-12", lift: "-top-9" },
}

function SoulAvatars({ soulIds, max = 3 }: { soulIds: string[]; max?: number }) {
  if (soulIds.length === 0) return null
  const shown = soulIds.slice(0, max)
  const overflow = soulIds.length - shown.length

  return (
    <div className="flex items-center -space-x-1.5">
      {shown.map((id) => {
        const soul: Soul | undefined = SANDBOX_SOUL_MAP.get(id)
        const mark: Mark | undefined = soul ? SANDBOX_MARK_MAP.get(soul.glyph_id) : undefined
        return (
          <span
            key={id}
            title={soul?.name}
            className="grid size-5 place-items-center rounded-full bg-background p-0.5 ring-2 ring-card"
          >
            <SoulGlyphThumbnail mark={mark} className="size-full" strokeClassName="text-foreground/70" />
          </span>
        )
      })}
      {overflow > 0 && (
        <span className="grid size-5 place-items-center rounded-full bg-background text-[9px] font-semibold text-muted-foreground ring-2 ring-card">
          +{overflow}
        </span>
      )}
    </div>
  )
}

type Props = {
  pebble: Pebble
  mark?: Mark
  stoneSize: StoneSize
  size?: "md" | "lg"
  /** Full-width cards do not tilt — a full-bleed card that rotates reads as broken. */
  tilt?: boolean
  onSelect?: (id: string) => void
}

/**
 * One polaroid, read top to bottom: who and when, then what it was called, then
 * the picture. The stone sits over the top edge and splits the meta row — the
 * space-between gap it falls into is what the row exists to open up.
 */
export function SandboxPolaroid({
  pebble,
  mark,
  stoneSize,
  size = "md",
  tilt = true,
  onSelect,
}: Props) {
  const picture = pebble.instants[0]
  const chaos = polaroidChaos(pebble.id)
  const head = HEAD[stoneSize]

  // motion-reduce:* strips the chaos too, not just the animation: a static tilt is
  // decoration, and the same preference that asks for no movement is asking for
  // less visual noise.
  const wrapperStyle: CSSProperties = tilt
    ? { rotate: `${chaos.rotate}deg`, translate: `${chaos.shiftX}px`, zIndex: chaos.z }
    : {}

  return (
    <div
      className={cn(
        "group relative",
        tilt && "motion-reduce:!rotate-0 motion-reduce:!translate-x-0",
        // The stone breaks the top edge, so a hovered card has to rise above its
        // neighbours or the one above clips it.
        "hover:z-30",
      )}
      style={wrapperStyle}
    >
      <figure
        className={cn(
          STOCK,
          HOVER,
          "motion-reduce:transition-none motion-reduce:group-hover:rotate-0 motion-reduce:group-hover:scale-100",
          "relative",
          size === "lg" ? "px-4 pb-4" : "px-3 pb-3",
          head.pad,
        )}
      >
        <PolaroidStone pebble={pebble} mark={mark} size={stoneSize} className={head.lift} />

        {/* Who. Keeps its height with no souls on the card, so the stone always has
            the same amount of paper to sit against. */}
        <div className="flex min-h-5 items-center gap-2">
          <SoulAvatars soulIds={pebble.soul_ids} />
        </div>

        <h3
          className={cn(
            "pt-1.5 text-center font-hand font-bold text-balance text-foreground",
            // The leading must come AFTER the text-* size: Tailwind's font-size
            // utilities also set line-height, so tailwind-merge treats them as
            // conflicting and silently drops an earlier `leading-*`.
            "text-[1.125rem] leading-[1.05]",
          )}
        >
          {pebble.name}
        </h3>

        {picture && (
          /* eslint-disable-next-line @next/next/no-img-element -- local fixture asset, next/image not applicable */
          <img
            src={picture}
            alt=""
            loading="lazy"
            // h-auto is load-bearing: the picture has to take its height from its
            // own width, so portrait and landscape fixtures both sit edge to edge.
            className="mt-2.5 h-auto w-full rounded-xs"
          />
        )}
      </figure>

      {/* An invisible button over the card rather than a clickable wrapper: <button>
          takes phrasing content, so it may not contain the <figure>. */}
      {onSelect && (
        <button
          type="button"
          onClick={() => onSelect(pebble.id)}
          aria-label={pebble.name}
          className="absolute inset-0 cursor-pointer rounded-sm outline-offset-4"
        />
      )}
    </div>
  )
}
