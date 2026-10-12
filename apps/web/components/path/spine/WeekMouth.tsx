"use client"

import Link from "next/link"
import { useTranslations } from "next-intl"
import { getOutline } from "@/lib/config/pebble-outlines"
import type { Size } from "@/lib/config/pebble-geometry"
import { stoneRingId } from "@/lib/utils/spine/stone-ids"
import type { WeekWords } from "@/lib/utils/spine/week-opening"
import { cn } from "@/lib/utils"

type WeekMouthProps = {
  words: WeekWords
  /** The week is still going: no ending yet, an invitation to carve instead. */
  current: boolean
  /** Absent on the last week there is. */
  onNextWeek?: () => void
}

const CAIRN: { size: Size; className: string }[] = [
  { size: "small", className: "-mb-px w-3.5" },
  { size: "medium", className: "-mb-0.5 w-[19px]" },
  { size: "large", className: "w-[25px]" },
]

const LINK = "inline-block py-2 font-spine-sc text-xs font-semibold tracking-[.18em] text-(--spine-ink-2) hover:text-(--spine-ink)"

/**
 * Where a week ends (#1052): the line runs into a small cairn of three river
 * pebbles, the week says it ends here and when the next begins, and a link
 * turns the page. The current week has no ending yet; it asks for a pebble.
 * Sits in `SpineFeed`'s footer, so it is on the line and the ground.
 */
export function WeekMouth({ words, current, onNextWeek }: WeekMouthProps) {
  const t = useTranslations("path.spine")
  return (
    <footer
      className={cn(
        "relative pr-(--spine-pad-r) pl-(--spine-col-x)",
        "before:absolute before:top-0 before:left-[calc(var(--spine-x)-.5px)] before:z-1 before:h-[26px] before:w-px before:bg-(--spine-line) before:content-['']",
      )}
    >
      <div aria-hidden className="absolute top-6 left-[calc(var(--spine-x)-14px)] flex w-7 flex-col items-center">
        {CAIRN.map(({ size, className }) => {
          const outline = getOutline(size, "neutral")
          return (
            <svg
              key={size}
              viewBox={`0 0 ${outline.width} ${outline.height}`}
              className={cn("block overflow-visible fill-(--spine-ground) stroke-(--spine-ink-2) stroke-[1.2px]", className)}
            >
              <use href={`#${stoneRingId(size, "neutral")}`} />
            </svg>
          )
        })}
      </div>
      {current ? (
        <Link href="/record" className={cn(LINK, "mt-[26px]")}>
          {t("carve")}
        </Link>
      ) : (
        <>
          <p className="pt-[26px]">
            <span className="block font-spine-sc text-xs font-semibold tracking-[.18em]">
              {t("weekEnds", { ordinal: words.ordinal })}
            </span>
            <i className="mt-1 block font-spine text-base font-[380] text-(--spine-ink-2)">
              {t("nextBegins", { ordinal: words.next.ordinal, day: words.next.monday })}
            </i>
          </p>
          {onNextWeek && (
            <button type="button" onClick={onNextWeek} className={cn(LINK, "mt-2.5 cursor-pointer")}>
              {t("nextWeek", { n: words.next.number })}
              <span aria-hidden> →</span>
            </button>
          )}
        </>
      )}
    </footer>
  )
}
