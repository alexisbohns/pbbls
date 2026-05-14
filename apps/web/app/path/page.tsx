"use client"

import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { PathScreen } from "@/components/path/PathScreen"

export default function PathPage() {
  const { pebbles, loading: pebblesLoading } = usePebbles()
  const { souls, loading: soulsLoading } = useSouls()
  const loading = pebblesLoading || soulsLoading

  return <PathScreen pebbles={pebbles} souls={souls} loading={loading} />
}
