"use client"

import type { Mark, Pebble } from "@/lib/types"
import type { RenderTier } from "@/lib/engine"
import { EMOTIONS } from "@/lib/config/emotions"
import { usePebbleVisual } from "@/lib/hooks/usePebbleVisual"
import { cn } from "@/lib/utils"

type PebbleVisualProps = {
  pebble: Pebble
  mark?: Mark | null
  tier?: RenderTier
  className?: string
}

// Split into two variants so the local engine (usePebbleVisual) only runs for
// pebbles without a server render — React hooks can't be called conditionally,
// so we branch at the component level instead of inside the hook.
export function PebbleVisual(props: PebbleVisualProps) {
  return props.pebble.render_svg
    ? <ServerRenderedVisual {...props} renderSvg={props.pebble.render_svg} />
    : <LocalRenderedVisual {...props} />
}

function ServerRenderedVisual({
  pebble,
  className,
  renderSvg,
}: PebbleVisualProps & { renderSvg: string }) {
  const emotion = EMOTIONS.find((e) => e.id === pebble.emotion_id)
  const svg = emotion?.color
    ? renderSvg.replaceAll("currentColor", emotion.color)
    : renderSvg
  return <SvgHost pebble={pebble} emotionName={emotion?.name} className={className} svg={svg} />
}

function LocalRenderedVisual({
  pebble,
  mark = null,
  tier = "thumbnail",
  className,
}: PebbleVisualProps) {
  const { svg } = usePebbleVisual(pebble, mark, tier)
  const emotion = EMOTIONS.find((e) => e.id === pebble.emotion_id)
  return <SvgHost pebble={pebble} emotionName={emotion?.name} className={className} svg={svg} />
}

function SvgHost({
  pebble,
  emotionName = "Unknown",
  className,
  svg,
}: {
  pebble: Pebble
  emotionName?: string
  className?: string
  svg: string
}) {
  return (
    <div
      data-slot="pebble-visual"
      role="img"
      aria-label={`Pebble: ${pebble.name}, ${emotionName}, intensity ${pebble.intensity}`}
      className={cn("pbbls-visual", className)}
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  )
}
