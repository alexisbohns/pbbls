import { getOutline } from "@/lib/config/pebble-outlines"
import { canvasTransform } from "@/lib/config/stone-geometry"
import type { StonePalette } from "@/lib/config/stone-tones"
import type { SealSpec } from "@/lib/achievements/seals"
import { stoneBodyId, stoneClipId, stoneHatchId } from "@/lib/utils/spine/stone-ids"
import { cn } from "@/lib/utils"
import { HueGroup } from "./HueGroup"

type FlatSealProps = {
  seal: SealSpec
  x: number
  y: number
  /** The seal's width in the figure's units; its height follows its outline. */
  width: number
  /** The family's palette: its hue, or its deep ink on a lowlight outline. Without one the seal prints in `ink`. */
  palette?: StonePalette | null
  /** The ink without a palette: `ink`, or `ink2` for a month with no pebble. */
  ink?: "ink" | "ink2"
  /** A dotted rim: a month still to come. */
  dashed?: boolean
}

/**
 * The boards' flat seal (#1308, `sealAt`): the Spine's stone outline for a
 * size and an outline polarity, in one of four prints. Solid fills it; tint
 * lays the ink over the stock (15 % Paper, 18 % Night) inside a rim; wash lays
 * it at 9 % / 12 % with a hatch and a rim; blank is the stock inside a rim. Its
 * strokes stay at the boards' screen widths however the figure scales (solid
 * edge 1, rim 1.1, hatch 0.8: the sprite's bodies are non-scaling). Needs
 * `StoneSprite` once on the page. iOS `FlatSeal`, Android `drawSeal`.
 */
export function FlatSeal({ seal, x, y, width, palette = null, ink = "ink", dashed = false }: FlatSealProps) {
  const { size, outline: cut, print } = seal
  const outline = getOutline(size, cut)
  const scale = width / outline.width
  const body = `#${stoneBodyId(size, cut)}`
  const tone = palette
    ? cut === "lowlight"
      ? "text-(--spine-deep)"
      : "text-(--spine-spot)"
    : ink === "ink2"
      ? "text-spine-ink-2"
      : "text-spine-ink"
  const dash = dashed ? "1.6 1.7" : undefined
  return (
    <HueGroup palette={palette}>
      <g
        transform={`translate(${(x - width / 2).toFixed(2)} ${(y - (outline.height * scale) / 2).toFixed(2)}) scale(${scale.toFixed(4)})`}
        className={cn(tone, "[--seal-tint:15%] [--seal-wash:9%] dark:[--seal-tint:18%] dark:[--seal-wash:12%]")}
      >
        {print === "solid" && <use href={body} fill="currentColor" stroke="currentColor" strokeWidth="1px" strokeDasharray={dash} strokeLinejoin="round" />}
        {print === "blank" && <use href={body} fill="var(--spine-stock)" stroke="currentColor" strokeWidth="1.1px" strokeDasharray={dash} strokeLinejoin="round" />}
        {print === "tint" && (
          <>
            <use href={body} fill="color-mix(in oklab, currentColor var(--seal-tint), var(--spine-stock))" />
            <use href={body} fill="none" stroke="currentColor" strokeWidth="1.1px" strokeDasharray={dash} strokeLinejoin="round" />
          </>
        )}
        {print === "wash" && (
          <>
            <use href={body} fill="color-mix(in oklab, currentColor var(--seal-wash), var(--spine-stock))" />
            <g clipPath={`url(#${stoneClipId(size, cut)})`}>
              <g transform={canvasTransform(size, cut)}>
                <use href={`#${stoneHatchId(size)}`} fill="none" stroke="currentColor" strokeWidth="0.8px" opacity={0.85} />
              </g>
            </g>
            <use href={body} fill="none" stroke="currentColor" strokeWidth="1.1px" strokeDasharray={dash} strokeLinejoin="round" />
          </>
        )}
      </g>
    </HueGroup>
  )
}
