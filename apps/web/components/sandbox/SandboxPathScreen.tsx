"use client"

import type { Mark, Pebble } from "@/lib/types"
import { groupPebbles } from "@/lib/utils/path-layout"
import { SANDBOX_MARK_MAP } from "@/lib/seed/sandbox-pebbles"
import { PathPebbleRow } from "@/components/path/PathPebbleRow"
import { SandboxPolaroid } from "./SandboxPolaroid"
import type { StoneSize } from "./PolaroidStone"

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
}: {
  pebbles: Pebble[]
  stoneSize: StoneSize
}) {
  const blocks = groupPebbles(pebbles)

  return (
    // overflow-x-clip, not hidden: the cards' rotation and translate push past the
    // container's padding and showed up as a few px of horizontal scroll. `hidden`
    // would make this a scroll container and coerce the y axis with it.
    <ol className="flex flex-col gap-6 overflow-x-clip px-4 pt-6 pb-24">
      {blocks.map((block, blockIndex) => {
        if (block.kind === "small") {
          return (
            <li key={blockIndex} className="flex flex-col gap-1">
              {block.pebbles.map((pebble, i) => (
                <PathPebbleRow
                  key={pebble.id}
                  pebble={pebble}
                  mark={markFor(pebble)}
                  positionIndex={i}
                />
              ))}
            </li>
          )
        }

        if (block.kind === "large") {
          return (
            <li key={blockIndex}>
              <SandboxPolaroid
                pebble={block.pebble}
                mark={markFor(block.pebble)}
                stoneSize={stoneSize}
                size="lg"
                tilt={false}
                timeLabel={timeLabel(block.pebble.happened_at)}
              />
            </li>
          )
        }

        return (
          <li key={blockIndex} className="flex flex-col gap-6">
            {block.rows.map((row, rowIndex) => (
              // A row of one is half width and centred; a row of two fills the grid.
              // `justify-center` does both — with two children it is a no-op.
              //
              // items-center rather than the default stretch: a card with no picture
              // is much shorter than one with, and top-aligning the pair leaves the
              // short one hanging over a tall column of empty paper.
              <div key={rowIndex} className="flex items-center justify-center gap-4">
                {row.map((pebble) => (
                  <div key={pebble.id} className="w-[calc(50%-0.5rem)]">
                    <SandboxPolaroid
                      pebble={pebble}
                      mark={markFor(pebble)}
                      stoneSize={stoneSize}
                      timeLabel={timeLabel(pebble.happened_at)}
                    />
                  </div>
                ))}
              </div>
            ))}
          </li>
        )
      })}
    </ol>
  )
}
