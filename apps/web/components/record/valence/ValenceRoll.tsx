"use client"

import { useRef, type PointerEvent as ReactPointerEvent } from "react"
import { animate, motion, useMotionValue, useReducedMotion } from "framer-motion"
import type { Size } from "@/lib/config/pebble-geometry"
import {
  cellAt,
  cellsEqual,
  polarityAfter,
  polarityBefore,
  polarityIndex,
  SIZE_LADDER,
  sizeIndex,
  type ValenceCell,
} from "@/lib/valence/valence"
import { ValenceSpan, ValenceWord } from "./ValenceHeadline"

/**
 * The lockup under the fan, as a two-axis roll: swipe left and right to change
 * polarity, up and down to change size.
 *
 * The roll is 1:1 with the finger — the content travels exactly as far as the
 * hand does — and detents at the half step, so the answer changes under the
 * thumb rather than on release. The ends clamp instead of wrapping, with
 * rubber-band resistance past the last step, so a hard swipe cannot loop the
 * user back where they started.
 *
 * Nothing moves that is not changing. The block is anchored to its bottom edge,
 * the word row reserves the tallest word's height, and the pyramid is always
 * three marks tall — so rolling between sizes never shifts the layout: the word
 * swaps size in place, growing upward, and the pyramid lights a different mark.
 * On the polarity axis only the word row travels, because the span reads the
 * same for all three polarities and sliding it would be motion that says
 * nothing.
 *
 * Content follows the finger, so the index moves *against* the travel: dragging
 * left brings the value on the right to centre. Because the offset drops by
 * exactly one step as each detent passes, the words never jump — what changes
 * at a detent is which one is the answer.
 *
 * Two mechanics carried over from iOS, both load-bearing:
 *
 * - **The axis is locked on the first movement of each drag.** Without the lock
 *   a diagonal swipe alternates axes frame to frame and the roll shakes.
 * - **The drag has to beat the scroll container.** iOS needs a
 *   `highPriorityGesture` or the step's ScrollView claims every vertical drag
 *   and the size axis is dead; `touch-action: none` is the web equivalent, and
 *   it is why this handles raw pointer events rather than a drag gesture that
 *   would negotiate with the page.
 *
 * No haptic at the detents: `RecordFlow` documents why the web flow is the
 * quiet one (the Vibration API is unsupported in Safari/iOS and inconsistent
 * elsewhere), and a substitute animation would be pretending.
 *
 * Hidden from assistive tech on purpose — the fan above is a real radiogroup
 * whose nine options carry the same wording ("Week event, Highlight"), and
 * announcing the lockup too would say everything twice.
 */
export function ValenceRoll({
  cell,
  onChange,
}: {
  cell: ValenceCell
  onChange: (next: ValenceCell) => void
}) {
  const reduceMotion = useReducedMotion()
  const x = useMotionValue(0)
  const drag = useRef<{
    origin: ValenceCell
    x: number
    y: number
    axis: Axis | null
    /** Last value handed up, so a move that lands on the same detent twice
     *  before the parent re-renders does not dispatch it again. */
    last: ValenceCell
  } | null>(null)

  const handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.pointerType === "mouse" && event.button !== 0) return
    x.stop()
    drag.current = { origin: cell, x: event.clientX, y: event.clientY, axis: null, last: cell }
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  const handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const state = drag.current
    if (!state) return

    const dx = event.clientX - state.x
    const dy = event.clientY - state.y

    // Decided once per drag and held.
    if (!state.axis) {
      if (Math.hypot(dx, dy) < MIN_DISTANCE) return
      state.axis = Math.abs(dx) > Math.abs(dy) ? "polarity" : "size"
    }

    const travel = state.axis === "polarity" ? dx : dy
    const step = state.axis === "polarity" ? POLARITY_STEP : SIZE_STEP
    const next = destination(state.origin, state.axis, roundAwayFromZero(-travel / step))
    if (!cellsEqual(next, state.last)) {
      state.last = next
      onChange(next)
    }

    // Whatever travel the steps did not consume is what the content is still
    // holding, so it eases back to centre as each detent passes and stretches
    // when there is nothing left to move to.
    const taken =
      state.axis === "polarity"
        ? polarityIndex(next) - polarityIndex(state.origin)
        : sizeIndex(next) - sizeIndex(state.origin)
    // The size axis deliberately does not translate: a block that slid
    // vertically would drag the whole lockup past its neighbours, and the
    // detent plus the pyramid already say what changed.
    x.set(state.axis === "polarity" ? rubberBanded(travel + taken * step, step / 2) : 0)
  }

  const endDrag = () => {
    if (!drag.current) return
    drag.current = null
    if (reduceMotion) x.set(0)
    else void animate(x, 0, { type: "spring", duration: 0.34, bounce: 0.3 })
  }

  const before = polarityBefore(cell)
  const after = polarityAfter(cell)

  return (
    <div
      className="flex w-full touch-none flex-col items-center justify-end gap-1 select-none"
      style={{ minHeight: LOCKUP_HEIGHT }}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={endDrag}
      onPointerCancel={endDrag}
      aria-hidden
    >
      {/* Clipped so the neighbour words bleed off the edge rather than widening
          the page. On a phone the container is the screen, which is where iOS
          lets them run out to. */}
      <div className="relative w-full overflow-hidden" style={{ height: WORD_ROW_HEIGHT }}>
        <motion.div className="absolute inset-0 flex items-end justify-center" style={{ x }}>
          {before && (
            <Neighbour offset={-POLARITY_STEP}>
              <ValenceWord polarity={before} size={cell.size} faded />
            </Neighbour>
          )}
          <ValenceWord polarity={cell.polarity} size={cell.size} />
          {after && (
            <Neighbour offset={POLARITY_STEP}>
              <ValenceWord polarity={after} size={cell.size} faded />
            </Neighbour>
          )}
        </motion.div>
      </div>

      <ValenceSpan size={cell.size} />
      <Pyramid size={cell.size} />
    </div>
  )
}

type Axis = "polarity" | "size"

/**
 * Finger travel per step. The polarity step is also the distance the neighbour
 * words sit out at, because the two have to agree for the roll to feel 1:1.
 * Eye-tuned on iOS; CSS pixels and iOS points are close enough on a phone that
 * the feel carries over.
 */
const POLARITY_STEP = 220
const SIZE_STEP = 90
/** How far past the last step the content will stretch. */
const OVERSCROLL = 34
/** Movement before the axis is decided. */
const MIN_DISTANCE = 6

/** Reserves the tallest lockup, so picking a stone never shoves the fan up. */
const LOCKUP_HEIGHT = 144
/** Reserves the tallest word, so a size step grows upward into space that is
 *  already accounted for rather than moving the span and the pyramid. */
const WORD_ROW_HEIGHT = 64

function Neighbour({ offset, children }: { offset: number; children: React.ReactNode }) {
  return (
    <span
      className="pointer-events-none absolute bottom-0"
      style={{ transform: `translateX(${offset}px)` }}
    >
      {children}
    </span>
  )
}

/**
 * Three marks, widest at the top, with the current size lit. Always all three:
 * a pyramid that changed height would move everything above it, which is the
 * shift this layout exists to avoid.
 */
function Pyramid({ size }: { size: Size }) {
  return (
    <div className="mt-2 flex flex-col items-center gap-1.5">
      {SIZE_LADDER.map((step) => (
        <span
          key={step}
          className="block h-1.5 rounded-full bg-primary transition-opacity duration-200 motion-reduce:transition-none"
          style={{ width: MARK_WIDTH[step], opacity: step === size ? 1 : 0.25 }}
        />
      ))}
    </div>
  )
}

const MARK_WIDTH: Record<Size, number> = { small: 8, medium: 26, large: 44 }

/** Where `steps` along `axis` lands, clamped to the grid. */
function destination(origin: ValenceCell, axis: Axis, steps: number): ValenceCell {
  return axis === "polarity"
    ? cellAt(polarityIndex(origin) + steps, sizeIndex(origin))
    : cellAt(polarityIndex(origin), sizeIndex(origin) + steps)
}

/**
 * Travel past `limit` keeps moving, but at a quarter rate and capped, so the end
 * of the grid feels like a wall with give rather than a stop.
 */
function rubberBanded(value: number, limit: number): number {
  if (Math.abs(value) <= limit) return value
  const damped = Math.min(OVERSCROLL, (Math.abs(value) - limit) * 0.25)
  return value < 0 ? -(limit + damped) : limit + damped
}

/** `Math.round` breaks the tie toward +∞; the detent has to break it the same
 *  way on both sides of centre or a half-step left and a half-step right do
 *  different things. */
function roundAwayFromZero(value: number): number {
  return Math.sign(value) * Math.round(Math.abs(value))
}
