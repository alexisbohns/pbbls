"use client"

import { useEffect, useState, useSyncExternalStore } from "react"
import { useAuth } from "@/lib/data/auth-context"
import { readerTimeZone } from "@/lib/home/timezone"
import { useLocale } from "@/lib/i18n"
import { HomeGreetingLines } from "./HomeGreetingLines"

const subscribeNever = () => () => {}

/**
 * Home's greeting in the reader's zone (#1384). The server does not know the
 * zone, so until mount it holds a blank block of the same height rather than
 * flash a wrong greeting. The clock is read again when the tab comes back,
 * since the band can change while it sleeps.
 */
export function HomeGreeting() {
  const { profile } = useAuth()
  const { locale } = useLocale()
  const mounted = useSyncExternalStore(subscribeNever, () => true, () => false)
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === "visible") setNow(new Date())
    }
    document.addEventListener("visibilitychange", onVisible)
    return () => document.removeEventListener("visibilitychange", onVisible)
  }, [])

  if (!mounted) return <div aria-hidden="true" className="h-[8.75rem]" />
  return <HomeGreetingLines now={now} name={profile?.display_name} locale={locale} timeZone={readerTimeZone()} />
}
