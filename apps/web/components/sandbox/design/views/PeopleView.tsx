"use client"

import SoulsPage from "@/app/souls/page"
import { ShellFrame } from "./ShellFrame"

/** app/souls/page.tsx (People) in the shell, fed by the fixture store. */
export function PeopleView() {
  return (
    <ShellFrame at="/souls">
      <SoulsPage />
    </ShellFrame>
  )
}
