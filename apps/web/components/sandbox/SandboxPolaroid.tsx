"use client"

import type { CSSProperties } from "react"
import type { Mark, Pebble, Soul } from "@/lib/types"
import { polaroidChaos } from "@/lib/utils/polaroid-chaos"
import { SANDBOX_MARK_MAP, SANDBOX_SOUL_MAP } from "@/lib/seed/sandbox-pebbles"
import { SoulGlyphThumbnail } from "@/components/souls/SoulGlyphThumbnail"
import { PolaroidGlyph, type GlyphVariant } from "./PolaroidGlyph"
import { cn } from "@/lib/utils"

/** The stock itself — paper, corners, and what lifts it off the page. In dark mode
 *  a drop shadow cannot read against a near-black background, so the card swaps to
 *  a faint inset top highlight instead. */
const STOCK =
  "bg-card rounded-sm " +
  "shadow-[0_1px_0_rgba(0,0,0,0.03),0_6px_12px_-4px_rgba(0,0,0,0.10),0_20px_40px_-16px_rgba(0,0,0,0.14)] " +
  "dark:shadow-[inset_0_1px_0_rgba(255,255,255,0.06),0_6px_12px_-4px_rgba(0,0,0,0.6),0_20px_40px_-16px_rgba(0,0,0,0.8)]"

/** How a card answers the pointer. Written as `group-*` so it fires from the
 *  wrapper — the wrapper carries the static chaos rotation, the card carries the
 *  interaction transform, and the two compose. On one element the hover transform
 *  would replace the rotation instead of adding to it. */
const HOVER =
  "transition-[transform,box-shadow] duration-300 ease-out " +
  "group-hover:-rotate-3 group-hover:scale-105 " +
  "group-has-[:focus-visible]:-rotate-3 group-has-[:focus-visible]:scale-105 " +
  "group-hover:shadow-[0_2px_0_rgba(0,0,0,0.04),0_10px_20px_-6px_rgba(0,0,0,0.16),0_32px_56px_-16px_rgba(0,0,0,0.24)] " +
  "dark:group-hover:shadow-[inset_0_1px_0_rgba(255,255,255,0.08),0_10px_20px_-6px_rgba(0,0,0,0.7),0_32px_56px_-16px_rgba(0,0,0,0.9)] " +
  "group-active:rotate-2 group-active:scale-95"

function SoulAvatars({ soulIds, max = 4 }: { soulIds: string[]; max?: number }) {
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
            className="grid size-6 place-items-center rounded-full bg-background p-1 ring-2 ring-card"
          >
            <SoulGlyphThumbnail mark={mark} className="size-full" strokeClassName="text-foreground/70" />
          </span>
        )
      })}
      {overflow > 0 && (
        <span className="grid size-6 place-items-center rounded-full bg-background text-[10px] font-semibold text-muted-foreground ring-2 ring-card">
          +{overflow}
        </span>
      )}
    </div>
  )
}

type Props = {
  pebble: Pebble
  mark?: Mark
  glyphVariant: GlyphVariant
  size?: "md" | "lg"
  /** Full-width cards do not tilt — a full-bleed card that rotates reads as broken. */
  tilt?: boolean
  timeLabel: string
  onSelect?: (id: string) => void
}

export function SandboxPolaroid({
  pebble,
  mark,
  glyphVariant,
  size = "md",
  tilt = true,
  timeLabel,
  onSelect,
}: Props) {
  const picture = pebble.instants[0]
  const hasPicture = Boolean(picture)
  const chaos = polaroidChaos(pebble.id)

  // motion-reduce:* strips the chaos too, not just the animation: a static tilt is
  // decoration, and the same preference that asks for no movement is asking for
  // less visual noise.
  const wrapperStyle: CSSProperties = tilt
    ? { rotate: `${chaos.rotate}deg`, translate: `${chaos.shiftX}px`, zIndex: chaos.z }
    : {}

  return (
    <div
      className={cn("group relative", tilt && "motion-reduce:!rotate-0 motion-reduce:!translate-x-0 hover:z-30")}
      style={wrapperStyle}
    >
      <figure
        className={cn(
          STOCK,
          HOVER,
          "motion-reduce:transition-none motion-reduce:group-hover:rotate-0 motion-reduce:group-hover:scale-100",
          size === "lg" ? "p-4" : "p-3",
        )}
      >
        <div className="relative">
          {hasPicture ? (
            /* eslint-disable-next-line @next/next/no-img-element -- local fixture asset, next/image not applicable */
            <img
              src={picture}
              alt=""
              loading="lazy"
              // h-auto is load-bearing: the picture has to take its height from its
              // own width, so portrait and landscape fixtures both sit edge to edge.
              className="h-auto w-full rounded-xs"
            />
          ) : null}
          {glyphVariant !== "margin" && (
            <PolaroidGlyph
              pebble={pebble}
              mark={mark}
              variant={glyphVariant}
              hasPicture={hasPicture}
              size={size}
            />
          )}
        </div>

        <figcaption
          className={cn(
            "flex select-none flex-col gap-1.5 text-center",
            hasPicture || glyphVariant === "adaptive" ? "pt-2" : "pt-0",
            // `adaptive` hangs its mark below the picture's edge; the caption has to
            // clear it or the title runs straight into the pebble.
            hasPicture && glyphVariant === "adaptive" && "pt-6",
          )}
        >
          <div className={cn("flex items-center gap-2", glyphVariant === "margin" ? "justify-start text-left" : "justify-center")}>
            {glyphVariant === "margin" && (
              <PolaroidGlyph
                pebble={pebble}
                mark={mark}
                variant="margin"
                hasPicture={hasPicture}
                size={size}
              />
            )}
            <h3
              className={cn(
                "min-w-0 font-hand leading-tight font-bold text-balance text-foreground",
                size === "lg" ? "text-3xl" : "text-2xl",
              )}
            >
              {pebble.name}
            </h3>
          </div>

          <div className="flex items-center justify-between gap-2 pt-0.5">
            <SoulAvatars soulIds={pebble.soul_ids} />
            <time
              dateTime={pebble.happened_at}
              className="ml-auto text-[10px] uppercase tracking-[0.12em] text-muted-foreground"
            >
              {timeLabel}
            </time>
          </div>
        </figcaption>
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
