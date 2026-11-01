"use client"

import { PathScreen } from "@/components/path/PathScreen"
import { M3Island } from "@/components/layout/M3Island"
import { useMoments } from "@/lib/data/useMoments"
import { useSouls } from "@/lib/data/useSouls"
import { ShellFrame } from "./ShellFrame"

/** app/path's tree (its layout's island, the page), in the shell, fed by the fixture store. */
export function PathView() {
  const { moments } = useMoments()
  const { souls } = useSouls()
  return (
    <ShellFrame at="/path">
      <M3Island className="h-full">
        <PathScreen moments={moments} souls={souls} loading={false} />
      </M3Island>
    </ShellFrame>
  )
}
