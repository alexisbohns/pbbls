"use client"

import { useEffect } from "react"
import Link from "next/link"
import { motion, useReducedMotion } from "framer-motion"
import { usePebblesCount } from "@/lib/data/usePebblesCount"
import { useHaptics } from "@/lib/hooks/useHaptics"
import { Button } from "@/components/ui/button"

interface RecordCelebrationProps {
  pebbleId: string
}

export function RecordCelebration({ pebbleId }: RecordCelebrationProps) {
  const { pebblesCount } = usePebblesCount()
  const { vibrate } = useHaptics()
  const prefersReducedMotion = useReducedMotion()

  useEffect(() => {
    vibrate([50, 100, 50])
  }, [vibrate])

  return (
    <section aria-label="Celebration" className="flex flex-col items-center gap-8 py-12 text-center">
      <motion.div
        initial={prefersReducedMotion ? false : { scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: "spring", stiffness: 300, damping: 20 }}
      >
        <h1 className="text-4xl font-bold" aria-live="polite">
          Pebble #{pebblesCount}!
        </h1>
      </motion.div>

      <nav className="flex flex-col gap-3" aria-label="Celebration actions">
        <Button className="h-11 px-6" render={<Link href={`/pebble/${pebbleId}`} />}>
          View pebble
        </Button>
        <Button variant="ghost" className="h-11 px-6" render={<Link href="/path" />}>
          Back to path
        </Button>
      </nav>
    </section>
  )
}
