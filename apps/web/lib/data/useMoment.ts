"use client"

import { useDataProvider } from "@/lib/data/provider-context"
import type { Moment } from "@/lib/types"

/**
 * One moment from the shared store, by MOMENT id. `byPebbleId` finds the
 * moment a pebble completes, for links minted with a pebble id: after the
 * split a web pebble's id differs from its moment's, so a read page that does
 * not know the id redirects through it.
 */
export function useMoment(id: string) {
  const { store, loading } = useDataProvider()

  const moment: Moment | undefined = store.moments.find((m) => m.id === id)

  const byPebbleId = (pebbleId: string): Moment | undefined =>
    store.moments.find((m) => m.pebble?.id === pebbleId)

  return { moment, loading, byPebbleId }
}
