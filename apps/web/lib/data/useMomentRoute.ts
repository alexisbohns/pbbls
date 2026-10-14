"use client"

import { useEffect } from "react"
import { useRouter } from "next/navigation"
import { useMoment } from "@/lib/data/useMoment"

/**
 * The moment a `/pebble/[id]…` route names. The id is a MOMENT id; a link
 * minted with a pebble id (before the split, or a web pebble whose id differs
 * from its moment's) is redirected to the moment's route, keeping `suffix`
 * (`""`, `"/edit"`, `"/edit-pebble"`). `redirecting` holds while that happens.
 */
export function useMomentRoute(rawId: string, suffix: "" | "/edit" | "/edit-pebble" = "") {
  const id = rawId.toLowerCase()
  const router = useRouter()
  const { moment, loading, byPebbleId } = useMoment(id)
  const legacyTarget = !loading && !moment ? byPebbleId(id)?.id : undefined

  useEffect(() => {
    if (legacyTarget) router.replace(`/pebble/${legacyTarget}${suffix}`)
  }, [legacyTarget, router, suffix])

  return { id, moment, loading, redirecting: legacyTarget !== undefined }
}
