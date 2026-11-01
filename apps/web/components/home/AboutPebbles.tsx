"use client"

import { useGuides } from "@/lib/data/useGuides"
import { useLocale } from "@/lib/i18n"
import { AboutPebblesList } from "./AboutPebblesList"

/** Home's About Pebbles (#1384, spec D14): a failed read shows nothing under the head (2026-10-26 §1.6). */
export function AboutPebbles() {
  const { locale } = useLocale()
  const { data } = useGuides(locale)
  return <AboutPebblesList guides={data ?? []} />
}
