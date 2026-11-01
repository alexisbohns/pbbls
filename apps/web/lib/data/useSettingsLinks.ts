"use client"

import { useAuth } from "@/lib/data/auth-context"
import { useKeptRead } from "@/lib/data/useKeptRead"
import { DEFAULT_SETTINGS_LINKS, decodeSettingsLinks, groupSettingsLinks, type SettingsLinkGroup } from "@/lib/settings/settings-links"

const kept = new Map<string, unknown>()

/** Settings' links (#1489): the last fetched rows, else the built-in seed, refreshed on each visit. */
export function useSettingsLinks(locale: "en" | "fr"): SettingsLinkGroup[] {
  const { user } = useAuth()
  const { data } = useKeptRead(kept, user ? user.id : null, (p) => p.getSettingsLinks(), "settings_links")
  return groupSettingsLinks(decodeSettingsLinks(data ?? DEFAULT_SETTINGS_LINKS, locale))
}
