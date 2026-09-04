"use client"

import { PathBottomBar } from "@/components/path/PathBottomBar"

export function PathBottomDock() {
  return (
    <div className="sticky inset-x-0 bottom-0">
      <PathBottomBar />
    </div>
  )
}
