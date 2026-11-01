"use client"

import { PeopleScreen } from "@/components/souls/PeopleScreen"
import { ShellFrame } from "./ShellFrame"

/** app/souls/page.tsx (People) in the shell, fed by the fixture store. */
export function PeopleView() {
  return (
    <ShellFrame at="/souls">
      <PeopleScreen />
    </ShellFrame>
  )
}
