"use client"

import type { ReactNode } from "react"
import { M3Island } from "@/components/layout/M3Island"
import { PathTab } from "@/components/path/PathTab"
import { ShellFrame } from "./ShellFrame"

/** app/(path)'s tree (its layout's island, the Path tab) in the shell, fed by the fixture store; optionally with a detail. */
export function PathView({ at = "/path", detail }: { at?: string; detail?: ReactNode }) {
  return (
    <ShellFrame at={at} detail={detail}>
      <M3Island className="h-full">
        <PathTab />
      </M3Island>
    </ShellFrame>
  )
}
