"use client"

import { useCallback, useEffect, useId, useMemo, useRef } from "react"
import { useTranslations } from "next-intl"
import { useReducedMotion } from "framer-motion"
import type { Intensity, Valence } from "@/lib/config/pebble-geometry"
import { FAN_REFERENCE, stonePlacement } from "@/lib/valence/fan-layout"
import type { ValenceMeshIds } from "@/lib/valence/stone-style"
import {
  ALL_CELLS,
  cellAt,
  cellFrom,
  cellKey,
  cellsEqual,
  INTENSITY_BY_SIZE,
  polarityIndex,
  sizeIndex,
  VALENCE_BY_POLARITY,
  type ValenceCell,
} from "@/lib/valence/valence"
import { ValenceMeshDefs } from "./ValenceMeshDefs"
import { ValenceRoll } from "./ValenceRoll"
import { ValenceStone } from "./ValenceStone"

type ValenceFanPickerProps = {
  intensity: Intensity
  valence: Valence
  onSelect: (intensity: Intensity, valence: Valence) => void
}

/** Opacity of the eight stones that are not the chosen one. */
const DIMMED = 0.45
const SELECTED_SCALE = 1.14

/**
 * The valence fan: nine real pebble stones arranged bottom-up — small and near
 * at the bottom, large and spread at the top — over a lockup you can swipe.
 *
 * Polarity picks the horizontal spread, size picks the height off the bottom
 * edge, and each ring's neutral stone is lifted into an arc so the three do not
 * read as a grid. The thing you are choosing finally looks like the thing you
 * get: every other pebble surface on web draws a wobbled silhouette with the
 * artwork inked inside it, and now so does the picker.
 *
 * Two inputs, one value. Tap a stone, or roll the lockup underneath — swipe
 * left and right for polarity, up and down for size. Selection commits in place
 * and `Continue` advances (the flow's action table already treats this step
 * that way): the fan is a comparison, and a tap that left the screen would deny
 * the user the look at what they chose next to the eight they did not.
 *
 * The day/week/month wording that used to head each row is gone from the
 * screen — size carries it now — but it still forms every option's accessible
 * name, so a screen reader hears "Week event, Highlight" as before.
 *
 * Ported from iOS #728 (`ValencePickerContent`). The nine-tile `ValenceGrid`
 * stays where it is: the edit sheets behind `PebbleDetail`, `PebbleEdit` and
 * `QuickPebbleEditor` still host it, because a sheet that commits and closes on
 * pick cannot host a roll that changes the value at every detent — that is the
 * same wall iOS hit, and unpicking it is its own change.
 */
export function ValenceFanPicker({ intensity, valence, onSelect }: ValenceFanPickerProps) {
  const t = useTranslations("record.valencePicker")
  const reduceMotion = useReducedMotion()
  const cell = cellFrom(intensity, valence)
  const key = cellKey(cell)

  const rawId = useId().replace(/[^a-zA-Z0-9_-]/g, "")
  const mesh: ValenceMeshIds = useMemo(
    () => ({
      wash: `valence-wash-${rawId}`,
      ink: `valence-ink-${rawId}`,
      selected: `valence-selected-${rawId}`,
    }),
    [rawId],
  )

  const select = useCallback(
    (next: ValenceCell) =>
      onSelect(INTENSITY_BY_SIZE[next.size], VALENCE_BY_POLARITY[next.polarity]),
    [onSelect],
  )

  const groupRef = useRef<HTMLDivElement>(null)
  const stones = useRef(new Map<string, HTMLButtonElement | null>())

  // Selection follows arrow keys, so focus has to follow selection — but only
  // when the group already holds it. Otherwise picking a stone with the roll,
  // or simply mounting the step, would yank focus onto a radio nobody asked
  // for and scroll the page to it.
  useEffect(() => {
    const group = groupRef.current
    if (!group || !group.contains(document.activeElement)) return
    stones.current.get(key)?.focus()
  }, [key])

  /**
   * Arrow keys walk the fan the way it is drawn: left and right along the
   * polarity axis, up toward the bigger stones sitting higher up the canvas.
   * Clamped rather than wrapping, matching the roll — there is nothing past
   * either end, and a wrap would put "highlight" one key-press to the left of
   * "lowlight".
   */
  const move = (polarityStep: number, sizeStep: number) => {
    const next = cellAt(polarityIndex(cell) + polarityStep, sizeIndex(cell) + sizeStep)
    if (!cellsEqual(next, cell)) select(next)
  }

  const handleKeyDown = (event: React.KeyboardEvent) => {
    switch (event.key) {
      case "ArrowRight":
        event.preventDefault()
        move(1, 0)
        break
      case "ArrowLeft":
        event.preventDefault()
        move(-1, 0)
        break
      case "ArrowUp":
        event.preventDefault()
        move(0, -1)
        break
      case "ArrowDown":
        event.preventDefault()
        move(0, 1)
        break
    }
  }

  return (
    <div className="flex flex-col items-center gap-4">
      <ValenceMeshDefs ids={mesh} />

      <div
        ref={groupRef}
        role="radiogroup"
        aria-label={t("title")}
        className="relative w-full max-w-[341px]"
        style={{ aspectRatio: `${FAN_REFERENCE.width} / ${FAN_REFERENCE.height}` }}
      >
        {ALL_CELLS.map((stone) => {
          const active = cellsEqual(stone, cell)
          const placement = stonePlacement(stone)
          const scale = active && !reduceMotion ? SELECTED_SCALE : 1
          return (
            <button
              key={cellKey(stone)}
              ref={(el) => {
                stones.current.set(cellKey(stone), el)
              }}
              type="button"
              role="radio"
              aria-checked={active}
              tabIndex={active ? 0 : -1}
              onClick={() => select(stone)}
              onKeyDown={handleKeyDown}
              aria-label={t("optionAria", {
                section: t(`${stone.size}.name`),
                polarity: t(stone.polarity),
              })}
              className="absolute rounded-2xl outline-none transition-[opacity,transform] duration-200 ease-out focus-visible:ring-2 focus-visible:ring-ring motion-reduce:transition-[opacity]"
              style={{
                ...placement,
                transform: `translate(-50%, -50%) scale(${scale})`,
                opacity: active ? 1 : DIMMED,
                zIndex: active ? 1 : 0,
              }}
            >
              <ValenceStone cell={stone} mesh={mesh} isSelected={active} />
              {/* The small stones are under the comfortable minimum on both
                  axes. This transparent overflowing child is what the finger
                  actually lands on; the click bubbles to the button. */}
              <span
                aria-hidden
                className="absolute top-1/2 left-1/2 size-full min-h-11 min-w-11 -translate-x-1/2 -translate-y-1/2"
              />
            </button>
          )
        })}
      </div>

      <ValenceRoll cell={cell} onChange={select} />
    </div>
  )
}
