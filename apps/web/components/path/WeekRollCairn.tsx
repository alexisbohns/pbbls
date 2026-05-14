"use client"

import { useEffect } from "react"
import { useRive } from "@rive-app/react-canvas"
import { useReducedMotion } from "framer-motion"
import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"
import type { WeekRollEntry } from "@/lib/utils/week-roll-entries"

type WeekRollCairnProps = {
  entry: WeekRollEntry
  isFocused: boolean
  opacity: number
  onClick: () => void
}

export function WeekRollCairn({ entry, isFocused, opacity, onClick }: WeekRollCairnProps) {
  const t = useTranslations("path")
  const prefersReducedMotion = useReducedMotion()
  const { rive, RiveComponent } = useRive({
    src: "/animations/pbbls-cairn.riv",
    autoplay: false,
  })

  useEffect(() => {
    if (!rive) return
    if (isFocused && !prefersReducedMotion) rive.play()
    else rive.stop()
  }, [rive, isFocused, prefersReducedMotion])

  return (
    <li>
      <button
        type="button"
        data-week={entry.weekStartIso}
        onClick={onClick}
        aria-pressed={isFocused}
        aria-label={t("weekHeader.weekAria", {
          iso: entry.isoWeek,
          count: entry.pebbles.length,
        })}
        className="flex w-[72px] flex-col items-center gap-1 transition-opacity scroll-mx-[50%]"
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
