"use client"

import { useState } from "react"
import { AboutPebbles } from "./AboutPebbles"
import { FirstFive } from "./FirstFive"
import { HomeGreeting } from "./HomeGreeting"
import { HomeMonth } from "./HomeMonth"
import { MonthlyWraps } from "./MonthlyWraps"
import { QuickActions } from "./QuickActions"
import { QuickMomentDialog } from "./QuickMomentDialog"

/**
 * Home (#1384): the month, the greeting and the quick actions, then the first
 * five, the monthly wraps and About Pebbles, in one reading column (spec D22).
 * The Quick moment dialog is shared by its shortcut and the first-moment task.
 */
export function HomeScreen() {
  const [quickOpen, setQuickOpen] = useState(false)
  const openQuick = () => setQuickOpen(true)
  return (
    <div className="mx-auto flex max-w-md flex-col gap-11 px-4 pt-[1.375rem]">
      <header className="flex flex-col">
        <div className="mb-[22px]">
          <HomeMonth />
        </div>
        <div className="px-1">
          <HomeGreeting />
        </div>
        <div className="mt-[26px]">
          <QuickActions onQuickMoment={openQuick} />
        </div>
      </header>
      <FirstFive onQuickMoment={openQuick} />
      <MonthlyWraps />
      <AboutPebbles />
      <QuickMomentDialog open={quickOpen} onOpenChange={setQuickOpen} />
    </div>
  )
}
