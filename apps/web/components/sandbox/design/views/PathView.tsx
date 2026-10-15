"use client"

import { PathScreen } from "@/components/path/PathScreen"
import { useMoments } from "@/lib/data/useMoments"
import { useSouls } from "@/lib/data/useSouls"

/** app/path/page.tsx, fed by the fixture store. */
export function PathView() {
  const { moments } = useMoments()
  const { souls } = useSouls()
  return <PathScreen moments={moments} souls={souls} loading={false} />
}
