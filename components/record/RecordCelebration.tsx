"use client"

import { useEffect } from "react"
import Link from "next/link"
import { motion, useReducedMotion } from "framer-motion"
import { useRive } from "@rive-app/react-canvas"
import { usePebblesCount } from "@/lib/data/usePebblesCount"
import { useHaptics } from "@/lib/hooks/useHaptics"
import { Button } from "@/components/ui/button"

interface RecordCelebrationProps {
  pebbleId: string
  karmaDelta: number
  bounceBefore: number
  bounceAfter: number
}

export function RecordCelebration({
  pebbleId,
  karmaDelta,
  bounceBefore,
  bounceAfter,
}: RecordCelebrationProps) {
  const { pebblesCount } = usePebblesCount()
  const { vibrate } = useHaptics()
  const prefersReducedMotion = useReducedMotion()

  const leveledUp = bounceAfter > bounceBefore

  const { rive, RiveComponent } = useRive(
    prefersReducedMotion
      ? null
      : {
          src: "/animations/pebbles-stack.riv",
          autoplay: false,
        }
  )

  useEffect(() => {
    vibrate(leveledUp ? [50, 100, 50, 100, 50] : [50, 100, 50])
  }, [vibrate, leveledUp])

  useEffect(() => {
    if (rive) {
      rive.play()
    }
  }, [rive])

  return (
    <section aria-label="Celebration" className="flex flex-col items-center gap-8 py-12 text-center">
      {!prefersReducedMotion && (
        <div className="h-48 w-48" aria-hidden="true">
          <RiveComponent className="size-full" />
        </div>
      )}

      <div className="flex flex-col items-center gap-3">
        <motion.div
          initial={prefersReducedMotion ? false : { scale: 0.8, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: "spring", stiffness: 300, damping: 20 }}
        >
          <h1 className="text-4xl font-bold" aria-live="polite">
            Pebble #{pebblesCount}!
          </h1>
        </motion.div>

        <motion.p
          className="text-lg font-medium text-primary"
          initial={prefersReducedMotion ? false : { opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ type: "spring", stiffness: 260, damping: 20, delay: 0.3 }}
          aria-live="polite"
        >
          +{karmaDelta} karma
        </motion.p>

        <motion.p
          className="text-sm text-muted-foreground"
          initial={prefersReducedMotion ? false : { opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.3, ease: "easeOut", delay: 0.5 }}
        >
          Bounce level {bounceAfter}
        </motion.p>

        {leveledUp && (
          <motion.p
            className="text-sm font-semibold"
            initial={prefersReducedMotion ? false : { opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ type: "spring", stiffness: 400, damping: 15, delay: 0.7 }}
            aria-live="polite"
          >
            Bounce level up!
          </motion.p>
        )}
      </div>

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
