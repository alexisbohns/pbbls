"use client"

import type { Mark, Pebble } from "@/lib/types"
import { groupPebbles } from "@/lib/utils/path-layout"
import { SANDBOX_MARK_MAP } from "@/lib/seed/sandbox-pebbles"
import { SandboxPolaroid } from "./SandboxPolaroid"
import { STEP_DOWN, type StoneSize } from "./PolaroidStone"

/** UTC so the fixture renders identically on every machine — the times are made up,
 *  and a locale-shifted fixture would make two screenshots disagree for no reason. */
function timeLabel(iso: string): string {
  const d = new Date(iso)
  return `${String(d.getUTCHours()).padStart(2, "0")}:${String(d.getUTCMinutes()).padStart(2, "0")}`
}

function markFor(pebble: Pebble): Mark | undefined {
  return pebble.mark_id ? SANDBOX_MARK_MAP.get(pebble.mark_id) : undefined
}

export function SandboxPathScreen({
  pebbles,
  stoneSize,
  dark,
}: {
  pebbles: Pebble[]
  stoneSize: StoneSize
  dark: boolean
}) {
  const blocks = groupPebbles(pebbles)

  return (
    // overflow-x-clip, not hidden: a hovered card scales past the container's
    // padding and showed up as a few px of horizontal scroll. `hidden` would make
    // this a scroll container and coerce the y axis with it.
    <ol className="flex flex-col gap-12 overflow-x-clip px-4 pt-10 pb-24">
      {blocks.map((block, blockIndex) => {
        if (block.kind === "large") {
          return (
            <li key={blockIndex}>
              <SandboxPolaroid
                pebble={block.pebble}
                mark={markFor(block.pebble)}
                stoneSize={stoneSize}
                size="lg"
                timeLabel={timeLabel(block.pebble.happened_at)}
                dark={dark}
              />
            </li>
          )
        }

        return (
          <li key={blockIndex}>
            {/* Flex columns, not CSS `columns-*`: a multicol container fragments
                boxes at column boundaries, which slices each card's drop shadow and
                repaints the leftover slice at the top of the next column. Our stone
                overhangs the card's top edge, so it would be cut in half too. */}
            <div className="flex items-start gap-5">
              {block.columns.map((column, columnIndex) => (
                <div key={columnIndex} className="flex min-w-0 flex-1 flex-col gap-12">
                  {column.map((pebble) => {
                    const isSmall = pebble.intensity === 1
                    return (
                      <SandboxPolaroid
                        key={pebble.id}
                        pebble={pebble}
                        mark={markFor(pebble)}
                        stoneSize={isSmall ? STEP_DOWN[stoneSize] : stoneSize}
                        size={isSmall ? "sm" : "md"}
                        timeLabel={timeLabel(pebble.happened_at)}
                        dark={dark}
                      />
                    )
                  })}
                </div>
              ))}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
