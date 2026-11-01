"use client"

import { MomentRead } from "@/components/pebble/MomentRead"
import { useCollections } from "@/lib/data/useCollections"
import { useMoments } from "@/lib/data/useMoments"
import { useSouls } from "@/lib/data/useSouls"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { PathView } from "./PathView"

const noop = () => {}

/** The read page's tree for the newest fixture moment. Navigation is a no-op; delete fails (no provider) with its toast. */
function NewestMoment() {
  const { moments } = useMoments()
  const { souls } = useSouls()
  const { collections } = useCollections()
  const { glyphs: marks } = useUsableGlyphs()
  const moment = moments[0]
  if (!moment) return null
  return <MomentRead moment={moment} souls={souls} collections={collections} marks={marks} onNavigate={noop} onDeleted={noop} />
}

/**
 * A pebble open over the Path (#1384, two-area spec D5): over the whole right
 * area from 1024 px, as the overlay below. The read view is
 * app/@detail/(pane)/pebble/[id]/page.tsx's, for the newest fixture moment.
 */
export function PebbleDetailView() {
  return <PathView at="/pebble/design" detail={<NewestMoment />} />
}
