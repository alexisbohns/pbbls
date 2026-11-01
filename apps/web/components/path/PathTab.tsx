"use client"

import { Suspense } from "react"
import { PathFocusParams } from "@/components/path/PathFocusParams"
import { PathScreen } from "@/components/path/PathScreen"
import { PathVerso } from "@/components/path/PathVerso"
import { PANES_QUERY } from "@/lib/config/app-routes"
import { useMediaQuery } from "@/lib/hooks/useMediaQuery"

/**
 * The Path tab (#1384, two-area spec D4). Below 1024 px it is the whole Path,
 * as it has always been. From 1024 px it is the verso alone (the week's
 * opening, persons and contents, the bounce and karma pill) at the reading
 * column's width, while the line runs in the right area on the same focus.
 * Nothing shows before auth resolves (`AuthGate`), by when the width is known.
 */
export function PathTab() {
  const wide = useMediaQuery(PANES_QUERY)
  return (
    <>
      <Suspense fallback={null}>
        <PathFocusParams />
      </Suspense>
      {wide ? <PathVerso /> : <PathScreen />}
    </>
  )
}
