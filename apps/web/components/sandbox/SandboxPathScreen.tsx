"use client"

import type { Mark, Pebble } from "@/lib/types"
import { groupPebbles } from "@/lib/utils/path-layout"
import { SANDBOX_MARK_MAP, SANDBOX_SOUL_MAP } from "@/lib/seed/sandbox-pebbles"
import { PathPolaroid } from "@/components/path/PathPolaroid"
import { STEP_DOWN, type StoneSize } from "./stone-sizes"

/**
 * The sandbox wall. Renders the same `PathPolaroid` the real Path renders, over
 * fixture pebbles — the point of the page is to try changes to the shipped card,
 * so a second copy of it here would defeat that.
 *
 * What it does not share is `PathWall`: the wall hard-codes one stone scale, and
 * the sandbox exists to compare them.
 */
export function SandboxPathScreen({
  pebbles,
  stoneSize,
}: {
  pebbles: Pebble[]
  stoneSize: StoneSize
}) {
  const blocks = groupPebbles(pebbles)

  const markFor = (pebble: Pebble): Mark | undefined =>
    pebble.mark_id ? SANDBOX_MARK_MAP.get(pebble.mark_id) : undefined

  const shared = { soulMap: SANDBOX_SOUL_MAP, markMap: SANDBOX_MARK_MAP }

  return (
    <ol className="flex flex-col gap-12 overflow-x-clip px-4 pt-10 pb-24">
      {blocks.map((block, blockIndex) => {
        if (block.kind === "large") {
          return (
            <li key={blockIndex}>
              <PathPolaroid
                pebble={block.pebble}
                mark={markFor(block.pebble)}
                {...shared}
                stoneSize={stoneSize}
                size="lg"
              />
            </li>
          )
        }

        return (
          <li key={blockIndex}>
            <div className="flex items-start gap-5">
              {block.columns.map((column, columnIndex) => (
                <div key={columnIndex} className="flex min-w-0 flex-1 flex-col gap-12">
                  {column.map((pebble) => {
                    const isSmall = pebble.intensity === 1
                    return (
                      <PathPolaroid
                        key={pebble.id}
                        pebble={pebble}
                        mark={markFor(pebble)}
                        {...shared}
                        stoneSize={isSmall ? STEP_DOWN[stoneSize] : stoneSize}
                        size={isSmall ? "sm" : "md"}
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
