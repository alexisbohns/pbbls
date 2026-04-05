"use client"

import { useEffect } from "react"
import { motion, useReducedMotion } from "framer-motion"
import { usePebble } from "@/lib/data/usePebble"
import { useMark } from "@/lib/data/useMark"
import { useHaptics } from "@/lib/hooks/useHaptics"
import { Button } from "@/components/ui/button"
import { PebbleVisual } from "@/components/pebble/PebbleVisual"

interface PebbleRevelationProps {
  pebbleId: string
  pebbleName: string
  onContinue: () => void
}

export function PebbleRevelation({
  pebbleId,
  pebbleName,
  onContinue,
}: PebbleRevelationProps) {
  const { pebble } = usePebble(pebbleId)
  const { mark } = useMark(pebble?.mark_id ?? "")
  const { vibrate } = useHaptics()
  const prefersReducedMotion = useReducedMotion()

  useEffect(() => {
    vibrate([10, 50, 20])
  }, [vibrate])

  if (!pebble) return null

  return (
    <section aria-label="Pebble revelation" className="flex flex-col items-center gap-8 py-12 text-center">
      <motion.div
        initial={prefersReducedMotion ? false : { scale: 0.5, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: "spring", stiffness: 300, damping: 20 }}
      >
        <PebbleVisual
          pebble={pebble}
          mark={mark}
          tier="detail"
          className="size-[200px]"
        />
      </motion.div>

      <motion.h1
        className="text-4xl font-bold"
        initial={prefersReducedMotion ? false : { opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: "spring", stiffness: 260, damping: 20, delay: 0.3 }}
        aria-live="polite"
      >
        {pebbleName}
      </motion.h1>

      <motion.div
        initial={prefersReducedMotion ? false : { opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.3, ease: "easeOut", delay: 0.5 }}
      >
        <Button className="h-11 px-6" onClick={onContinue}>
          Continue
        </Button>
      </motion.div>
    </section>
  )
}
