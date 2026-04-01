"use client"

import { motion, useReducedMotion } from "framer-motion"
import { useBounce } from "@/lib/data/useBounce"
import { useKarma } from "@/lib/data/useKarma"

function bounceLabel(level: number): string {
  if (level === 0) return "Resting"
  return String(level)
}

export function GamificationStats() {
  const { bounce, loading: bounceLoading } = useBounce()
  const { karma, loading: karmaLoading } = useKarma()
  const prefersReducedMotion = useReducedMotion()

  if (bounceLoading || karmaLoading) return null

  return (
    <motion.dl
      className="mb-4 flex gap-4 text-xs text-muted-foreground"
      aria-label="Gamification stats"
      initial={prefersReducedMotion ? false : { opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3, ease: "easeOut" }}
    >
      <div className="flex gap-1">
        <dt>Bounce</dt>
        <dd>{bounceLabel(bounce)}</dd>
      </div>
      <div className="flex gap-1">
        <dt>Karma</dt>
        <dd>{karma}</dd>
      </div>
    </motion.dl>
  )
}
