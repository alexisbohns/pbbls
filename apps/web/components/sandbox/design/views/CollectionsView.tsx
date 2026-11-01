"use client"

import CollectionsPage from "@/app/collections/page"
import { ShellFrame } from "./ShellFrame"

/** app/collections/page.tsx in the shell, fed by the fixture store. */
export function CollectionsView() {
  return (
    <ShellFrame at="/collections">
      <CollectionsPage />
    </ShellFrame>
  )
}
