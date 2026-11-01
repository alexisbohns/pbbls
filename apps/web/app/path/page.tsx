"use client"

import { Suspense } from "react"
import { useSearchParams } from "next/navigation"
import { useMoments } from "@/lib/data/useMoments"
import { useSouls } from "@/lib/data/useSouls"
import { PathScreen } from "@/components/path/PathScreen"
import { parseWeekParam } from "@/lib/utils/week-param"

/**
 * `?focus=<momentId>` (R14) opens the Path on the week that moment happened
 * in: Capture and Complete send the user here after a save. It is read with
 * `useSearchParams`, not the page's `searchParams` prop: /path is prerendered
 * as a static route, whose prop is the build's empty query. Reading search
 * params requires a Suspense boundary in the App Router, hence the split.
 *
 * `?week=<Monday, YYYY-MM-DD>` (#1384, spec D9) opens it on that week, or the
 * closest one it has: Home's week columns and wrap cards link here. A
 * malformed value is ignored; `focus` wins when both are present.
 */
function PathContent() {
  const params = useSearchParams()
  const focus = params.get("focus")
  const week = parseWeekParam(params.get("week"))
  const { moments, loading: momentsLoading } = useMoments()
  const { souls, loading: soulsLoading } = useSouls()
  const loading = momentsLoading || soulsLoading

  return (
    <PathScreen
      moments={moments}
      souls={souls}
      loading={loading}
      focusMomentId={focus !== null && focus !== "" ? focus : null}
      focusWeek={week}
    />
  )
}

export default function PathPage() {
  return (
    <Suspense fallback={null}>
      <PathContent />
    </Suspense>
  )
}
