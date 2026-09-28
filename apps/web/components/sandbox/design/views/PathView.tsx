"use client"

import { PathScreen } from "@/components/path/PathScreen"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"

/** app/path/page.tsx, fed by the fixture store. */
export function PathView() {
  const { pebbles } = usePebbles()
  const { souls } = useSouls()
  return <PathScreen pebbles={pebbles} souls={souls} loading={false} />
}
