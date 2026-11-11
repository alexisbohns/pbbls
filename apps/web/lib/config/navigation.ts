import { Collection, Home, Path, People, Person, glyphForm, type Form } from "@pbbls/icons"
import type { PbblsGlyphComponent } from "@/lib/icons/pbbls-icon"

export type NavItemKey = "home" | "path" | "people" | "collections" | "you"

export type NavItem = {
  key: NavItemKey
  href: string
  /** Drawn regular; the current tab draws its `selected` form (icon forms spec §3), on top of the colour and `aria-current`. */
  icon: PbblsGlyphComponent
}

/** The form the current tab draws: the `selected` token's, except People, which keeps its mark lit on the solid (iOS `AppTab.selectedForm`). */
export function selectedForm(item: NavItem): Form {
  return item.key === "people" ? { fill: { kind: "solid" }, marked: true } : glyphForm(item.icon, "selected")
}

/**
 * The five tabs (#1384 D2): Home · Path · People · Collections · You, the
 * iOS and Android order. `AppNavigation` draws them as a bar on narrow
 * windows and a rail on wide ones; `routeShell` (app-routes.ts) reads the
 * same list to decide where the navigation shows. Display labels are
 * resolved via i18n (`nav.<key>`) — see `useNavItems`.
 */
export const NAV_ITEMS: ReadonlyArray<NavItem> = [
  { key: "home",        href: "/home",        icon: Home },
  { key: "path",        href: "/path",        icon: Path },
  { key: "people",      href: "/souls",       icon: People },
  { key: "collections", href: "/collections", icon: Collection },
  { key: "you",         href: "/profile",     icon: Person },
]
