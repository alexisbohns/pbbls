"use client"

import type { ReactNode } from "react"
import { PeopleScreen } from "@/components/souls/PeopleScreen"
import { ShellFrame } from "./ShellFrame"

/** People's tab in the shell, fed by the fixture store; optionally with one of its details open. */
export function PeopleView({ at = "/souls", detail }: { at?: string; detail?: ReactNode }) {
  return (
    <ShellFrame at={at} detail={detail}>
      <PeopleScreen />
    </ShellFrame>
  )
}
