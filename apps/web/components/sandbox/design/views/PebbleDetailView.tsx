"use client"

import { PageLayout } from "@/components/layout/PageLayout"
import { MomentRead } from "@/components/pebble/MomentRead"
import { useCollections } from "@/lib/data/useCollections"
import { useMoments } from "@/lib/data/useMoments"
import { useSouls } from "@/lib/data/useSouls"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"

const noop = () => {}

/**
 * app/pebble/[id]/page.tsx for the newest fixture moment. Navigation is a
 * no-op. Delete is not: the sandbox has no provider, so `deleteMoment` and
 * `deletePebble` throw "Not authenticated" and the dialog shows its
 * "Couldn't delete" toast.
 */
export function PebbleDetailView() {
  const { moments } = useMoments()
  const { souls } = useSouls()
  const { collections } = useCollections()
  const { glyphs: marks } = useUsableGlyphs()
  const moment = moments[0]
  if (!moment) return null

  return (
    <PageLayout>
      <section>
        <MomentRead
          moment={moment}
          souls={souls}
          collections={collections}
          marks={marks}
          onNavigate={noop}
          onDeleted={noop}
        />
      </section>
    </PageLayout>
  )
}
