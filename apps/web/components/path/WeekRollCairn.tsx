"use client"

import { useEffect } from "react"
import {
  useRive,
  useStateMachineInput,
  useViewModel,
  useViewModelInstance,
  useViewModelInstanceColor,
} from "@rive-app/react-canvas"
import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"
import type { WeekRollEntry } from "@/lib/utils/week-roll-entries"

// Stroke colors mirror iOS (pebblesAccent for focused, pebblesMutedForeground
// for non-focused — same shade in both light and dark themes so the cairn
// stays visible on both the white path background and the dark brand bg).
const STROKE_FOCUSED = { r: 0xC0, g: 0x7A, b: 0x7A } // --primary
const STROKE_MUTED = { r: 0x7A, g: 0x5E, b: 0x64 }   // --muted-foreground (light)

const STATE_MACHINE_NAME = "State Machine 1"

type WeekRollCairnProps = {
  entry: WeekRollEntry
  isFocused: boolean
  opacity: number
  onClick: () => void
}

export function WeekRollCairn({ entry, isFocused, opacity, onClick }: WeekRollCairnProps) {
  const t = useTranslations("path")

  // State-machine-driven Rive cairn. `pbbls-cairn-states.riv` exposes:
  //   - `isSelected` (state-machine bool input): switches between idle and
  //     active states.
  //   - `strokeColor` (Data Binding color property): tints the cairn outline.
  // autoBind hooks the default view-model instance to the Rive instance so the
  // useViewModelInstanceColor hook below can mutate it.
  const { rive, RiveComponent } = useRive({
    src: "/animations/pbbls-cairn-states.riv",
    stateMachines: STATE_MACHINE_NAME,
    autoplay: true,
    autoBind: true,
  })

  const isSelectedInput = useStateMachineInput(rive, STATE_MACHINE_NAME, "isSelected")
  const viewModel = useViewModel(rive, { useDefault: true })
  const viewModelInstance = useViewModelInstance(viewModel, { useDefault: true, rive })
  const { setRgb } = useViewModelInstanceColor("strokeColor", viewModelInstance)

  useEffect(() => {
    // Rive state-machine inputs are mutated via assignment to `.value` — this
    // is the documented SDK API even though the lint rule treats hook returns
    // as immutable.
    // eslint-disable-next-line react-hooks/immutability
    if (isSelectedInput) isSelectedInput.value = isFocused
  }, [isFocused, isSelectedInput])

  useEffect(() => {
    const color = isFocused ? STROKE_FOCUSED : STROKE_MUTED
    setRgb(color.r, color.g, color.b)
  }, [isFocused, setRgb])

  return (
    <li className="shrink-0">
      <button
        type="button"
        data-week={entry.weekStartIso}
        onClick={onClick}
        aria-pressed={isFocused}
        aria-label={t("weekHeader.weekAria", {
          iso: entry.isoWeek,
          count: entry.pebbles.length,
        })}
        className="flex w-[72px] shrink-0 flex-col items-center gap-1 transition-opacity"
        style={{ opacity }}
      >
        <div className="size-14"><RiveComponent /></div>
        <span
          className={cn(
            "font-heading text-xs font-semibold",
            isFocused ? "text-primary" : "text-muted-foreground",
          )}
        >
          {entry.isoWeek}
        </span>
      </button>
    </li>
  )
}
