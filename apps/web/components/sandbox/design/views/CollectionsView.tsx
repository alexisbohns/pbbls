"use client"

import { CollectionsScreen } from "@/components/collections/CollectionsScreen"
import { ShellFrame } from "./ShellFrame"

/** app/collections/page.tsx in the shell, fed by the fixture store. */
export function CollectionsView() {
  return (
    <ShellFrame at="/collections">
      <CollectionsScreen />
    </ShellFrame>
  )
}
