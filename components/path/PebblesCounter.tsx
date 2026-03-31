"use client"

import { motion, useReducedMotion } from "framer-motion"
import { usePebblesCount } from "@/lib/data/usePebblesCount"

export function PebblesCounter() {
  const { pebblesCount, loading } = usePebblesCount()
  const prefersReducedMotion = useReducedMotion()

  if (loading) return null

  return (
    <motion.p
      className="mb-4 text-sm text-muted-foreground"
      aria-label={`Pebbles count: ${pebblesCount}`}
      initial={prefersReducedMotion ? false : { opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.3, ease: "easeOut" }}
    >
      <span className="font-semibold text-foreground">{pebblesCount}</span>{" "}
      {pebblesCount === 1 ? "pebble" : "pebbles"} collected
    </motion.p>
  )
}
