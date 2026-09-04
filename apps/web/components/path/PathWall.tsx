"use client"

import { motion, useReducedMotion } from "framer-motion"
import type { Mark, Pebble, Soul } from "@/lib/types"
import { groupPebbles } from "@/lib/utils/path-layout"
import { PathPolaroid } from "./PathPolaroid"
import type { StoneSize } from "./PathStone"

/** The wall's default stone scale, and the step down a small pebble takes. */
const STONE: StoneSize = "md"
const STONE_SMALL: StoneSize = "sm"

/**
 * A week laid out as a wall of polaroid prints.
 *
 * Ordinary pebbles are dealt round-robin into columns; a large one breaks the
 * wall and takes the full width. See `lib/utils/path-layout` for why the deal is
 * round-robin rather than height-balanced — it is what keeps left-to-right
 * reading order matching the order things happened.
 */
export function PathWall({
  pebbles,
  soulMap,
  markMap,
  isFocused,
  onSelectPebble,
}: {
  pebbles: Pebble[]
  soulMap: Map<string, Soul>
  markMap: Map<string, Mark>
  isFocused: boolean
  onSelectPebble: (id: string) => void
}) {
  const prefersReducedMotion = useReducedMotion()
  const blocks = groupPebbles(pebbles)

  const markFor = (pebble: Pebble) =>
    pebble.mark_id ? markMap.get(pebble.mark_id) : undefined

  const item = {
    hidden: { opacity: 0, y: -4 },
    visible: {
      opacity: 1,
      y: 0,
      transition: { duration: prefersReducedMotion ? 0 : 0.25, ease: "easeOut" as const },
    },
  }

  return (
    <motion.ol
      // overflow-x-clip, not hidden: a hovered card scales past the container's
      // padding and reads as a few px of horizontal scroll. `hidden` would make
      // this a scroll container and coerce the y axis with it.
      className="flex h-full flex-col gap-12 overflow-x-clip overflow-y-auto px-4 pt-10 pb-24"
      style={{
        maskImage: "linear-gradient(to bottom, black 0%, black 90%, transparent 100%)",
        WebkitMaskImage: "linear-gradient(to bottom, black 0%, black 90%, transparent 100%)",
      }}
      initial={isFocused ? "hidden" : "visible"}
      animate="visible"
      variants={{
        visible: { transition: { staggerChildren: prefersReducedMotion ? 0 : 0.08 } },
      }}
    >
      {blocks.map((block, blockIndex) => {
        if (block.kind === "large") {
          return (
            <motion.li key={block.pebble.id} id={`pebble-${block.pebble.id}`} variants={item}>
              <PathPolaroid
                pebble={block.pebble}
                mark={markFor(block.pebble)}
                soulMap={soulMap}
                markMap={markMap}
                stoneSize={STONE}
                size="lg"
                onSelect={onSelectPebble}
              />
            </motion.li>
          )
        }

        return (
          <li key={`grid-${blockIndex}`}>
            {/* Flex columns, not CSS `columns-*`: a multicol container fragments
                boxes at column boundaries, which slices each card's drop shadow
                and repaints the leftover slice at the top of the next column. The
                stone overhangs the card's top edge, so it would be cut too. */}
            <div className="flex items-start gap-5">
              {block.columns.map((column, columnIndex) => (
                <div key={columnIndex} className="flex min-w-0 flex-1 flex-col gap-12">
                  {column.map((pebble) => {
                    const isSmall = pebble.intensity === 1
                    return (
                      <motion.div key={pebble.id} id={`pebble-${pebble.id}`} variants={item}>
                        <PathPolaroid
                          pebble={pebble}
                          mark={markFor(pebble)}
                          soulMap={soulMap}
                          markMap={markMap}
                          stoneSize={isSmall ? STONE_SMALL : STONE}
                          size={isSmall ? "sm" : "md"}
                          onSelect={onSelectPebble}
                        />
                      </motion.div>
                    )
                  })}
                </div>
              ))}
            </div>
          </li>
        )
      })}
    </motion.ol>
  )
}
