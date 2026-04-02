"use client"

import { CirclePile, Sparkle, Stone } from "lucide-react"
import { motion, useReducedMotion } from "framer-motion"
import { useBounce } from "@/lib/data/useBounce"
import { useKarma } from "@/lib/data/useKarma"
import { usePebblesCount } from "@/lib/data/usePebblesCount"
import { BOUNCE_THRESHOLDS } from "@/lib/data/bounce-levels"
import { GamificationBlock } from "@/components/path/GamificationBlock"

function bounceLabel(level: number): string {
  if (level === 0) return "Resting"
  return String(level)
}

export function GamificationBlocks() {
  const { pebblesCount, loading: countLoading } = usePebblesCount()
  const { bounce, bounceWindow, loading: bounceLoading } = useBounce()
  const { karma, loading: karmaLoading } = useKarma()
  const prefersReducedMotion = useReducedMotion()

  if (countLoading || bounceLoading || karmaLoading) return null

  const activeDays = bounceWindow.length

  return (
    <motion.div
      className="mb-4 flex gap-2"
      aria-label="Gamification stats"
      role="group"
      initial={prefersReducedMotion ? false : { opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3, ease: "easeOut" }}
    >
      <GamificationBlock
        icon={Stone}
        label={pebblesCount === 1 ? "pebble" : "pebbles"}
        value={pebblesCount}
        dialogTitle="Pebbles"
        dialogDescription="Pebbles are moments you record — thoughts, feelings, experiences captured as they happen. Each pebble you create adds to your collection. The count shows how many moments you've saved on your path so far."
      />

      <GamificationBlock
        icon={CirclePile}
        label="bounce"
        value={bounceLabel(bounce)}
        dialogTitle="Bounce"
        dialogDescription={
          <>
            <span>
              Bounce measures your regularity over the past 28 days. Each day
              you record at least one pebble counts as an active day. The more
              active days, the higher your bounce level.
            </span>
            <ul className="mt-2 list-inside list-disc space-y-0.5">
              {[...BOUNCE_THRESHOLDS].reverse().map(({ minDays, level }) => (
                <li key={level}>
                  Level {level}: {minDays}+ active{" "}
                  {minDays === 1 ? "day" : "days"}
                </li>
              ))}
            </ul>
            <span className="mt-2 block">
              Days older than 28 days fall off the window, so bounce naturally
              decays if you stop recording. Your current window has{" "}
              <strong>
                {activeDays} active {activeDays === 1 ? "day" : "days"}
              </strong>
              .
            </span>
          </>
        }
      />

      <GamificationBlock
        icon={Sparkle}
        label="karma"
        value={karma}
        dialogTitle="Karma"
        dialogDescription={
          <>
            <span>
              Karma reflects how richly you describe your moments. Each pebble
              earns karma based on the depth of detail you add:
            </span>
            <ul className="mt-2 list-inside list-disc space-y-0.5">
              <li>+1 for creating the pebble</li>
              <li>+1 for adding a description</li>
              <li>+1 for each reflective card</li>
              <li>+1 for tagging a soul</li>
              <li>+1 for choosing a domain</li>
              <li>+1 for drawing a glyph</li>
            </ul>
            <span className="mt-2 block">
              Your total karma is <strong>{karma} points</strong>.
            </span>
          </>
        }
      />
    </motion.div>
  )
}
