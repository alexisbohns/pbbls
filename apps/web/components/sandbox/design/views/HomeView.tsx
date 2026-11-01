"use client"

import { useMemo, useState } from "react"
import { AboutPebblesList } from "@/components/home/AboutPebblesList"
import { FirstFiveBox } from "@/components/home/FirstFiveBox"
import { HomeGreetingLines } from "@/components/home/HomeGreetingLines"
import { HomeMonthGrid } from "@/components/home/HomeMonthGrid"
import { MonthlyWrapsList } from "@/components/home/MonthlyWrapsList"
import { QuickActions } from "@/components/home/QuickActions"
import { QuickMomentDialog } from "@/components/home/QuickMomentDialog"
import { M3Island } from "@/components/layout/M3Island"
import { StoneSprite } from "@/components/pebble/stone/StoneSprite"
import { useAchievementPalettes } from "@/lib/data/useAchievementPalettes"
import { BLUE_PEBBLE, FIRST_FIVE_ORDER, firstFiveState } from "@/lib/home/first-five"
import { useLocale } from "@/lib/i18n"
import { DESIGN_PROFILE } from "@/lib/seed/design-fixtures"
import { DESIGN_GUIDES, DESIGN_HOME_MONTH, DESIGN_HOME_WRAPS } from "@/lib/seed/design-home"

/** app/home's tree (#1384), in its island, with fixtures where Home reads the network. */
export function HomeView() {
  const { locale } = useLocale()
  const palettes = useAchievementPalettes()
  const [quickOpen, setQuickOpen] = useState(false)
  const [now] = useState(() => new Date())
  const state = useMemo(
    () => firstFiveState(new Set([...FIRST_FIVE_ORDER, BLUE_PEBBLE]), new Set(["first-moment", "first-pebble"])),
    [],
  )
  return (
    <M3Island className="pb-12">
      <div className="mx-auto flex max-w-md flex-col gap-11 px-4 pt-[1.375rem]">
        <header className="flex flex-col">
          <div className="mb-[22px]">
            <HomeMonthGrid month={DESIGN_HOME_MONTH} locale={locale} paletteFor={palettes.emotion} />
          </div>
          <div className="px-1">
            <HomeGreetingLines now={now} name={DESIGN_PROFILE.display_name} locale={locale} timeZone="Europe/Paris" />
          </div>
          <div className="mt-[26px]">
            <QuickActions onQuickMoment={() => setQuickOpen(true)} />
          </div>
        </header>
        <FirstFiveBox state={state} hrefFor={() => "/collections"} onQuickMoment={() => setQuickOpen(true)} onClaim={() => {}} />
        <MonthlyWrapsList wraps={DESIGN_HOME_WRAPS} currentMonth="2026-10-01" locale={locale} onReveal={async () => {}} />
        <AboutPebblesList guides={DESIGN_GUIDES} />
      </div>
      <QuickMomentDialog open={quickOpen} onOpenChange={setQuickOpen} />
      <StoneSprite />
    </M3Island>
  )
}
