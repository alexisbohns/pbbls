"use client"

import type { ReactNode } from "react"
import { CollectionsScreen } from "@/components/collections/CollectionsScreen"
import { ShellFrame } from "./ShellFrame"

/** Collections's tab in the shell, fed by the fixture store; optionally with one of its details open. */
export function CollectionsView({ at = "/collections", detail }: { at?: string; detail?: ReactNode }) {
  return (
    <ShellFrame at={at} detail={detail}>
      <CollectionsScreen />
    </ShellFrame>
  )
}
