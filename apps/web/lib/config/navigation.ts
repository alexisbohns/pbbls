import { Collection, Home, Path, People, Person } from "@pbbls/icons"
import type { PbblsIconComponent } from "@/lib/icons/pbbls-icon"

export type NavItemKey = "home" | "path" | "people" | "collections" | "you"

export type NavItem = {
  key: NavItemKey
  href: string
  /** Drawn plain: the nav marks the current entry by colour and `aria-current`, never by an `active` twin (spec D11). */
  icon: PbblsIconComponent
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
