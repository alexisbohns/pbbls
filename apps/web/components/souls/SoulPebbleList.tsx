"use client"

import { PebbleListItem } from "@/components/pebble/PebbleListItem"
import type { CompletedMoment } from "@/lib/types"

type SoulPebbleListProps = {
  /** Already filtered and ordered, newest first (R9). */
  moments: CompletedMoment[]
}

/** A soul's pebbles as one M3 list group (#1384, two-area spec D6), each a link to the pebble. */
export function SoulPebbleList({ moments }: SoulPebbleListProps) {
  return (
    <ul className="m3-list-group">
      {moments.map((moment) => (
        <PebbleListItem key={moment.id} moment={moment} />
      ))}
    </ul>
  )
}
