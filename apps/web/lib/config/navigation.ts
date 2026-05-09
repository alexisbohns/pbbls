import { Route, CirclePlus, FolderOpen, Users, Fingerprint, FlaskConical } from "lucide-react"

export type NavItemKey = "path" | "record" | "collections" | "souls" | "glyphs" | "lab"

export type NavItem = {
  key: NavItemKey
  href: string
  icon: typeof Route
}

/**
 * Nav slot configuration. Display labels are resolved via i18n
 * (`nav.<key>`) — see `useNavItems` for the localized form.
 */
export const NAV_ITEMS: ReadonlyArray<NavItem> = [
  { key: "path",        href: "/path",        icon: Route },
  { key: "record",      href: "/record",      icon: CirclePlus },
  { key: "collections", href: "/collections", icon: FolderOpen },
  { key: "souls",       href: "/souls",       icon: Users },
  { key: "glyphs",      href: "/glyphs",      icon: Fingerprint },
  { key: "lab",         href: "/lab",         icon: FlaskConical },
]
