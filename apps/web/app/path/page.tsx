"use client"

import { useMoments } from "@/lib/data/useMoments"
import { useSouls } from "@/lib/data/useSouls"
import { PathScreen } from "@/components/path/PathScreen"

export default function PathPage() {
  const { moments, loading: momentsLoading } = useMoments()
  const { souls, loading: soulsLoading } = useSouls()
  const loading = momentsLoading || soulsLoading

  return <PathScreen moments={moments} souls={souls} loading={loading} />
}
