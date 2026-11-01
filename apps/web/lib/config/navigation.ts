import { Collection, Path, PebbleDottedGlyph, People, PlusCircle, Sparkle } from "@pbbls/icons"
import type { PbblsIconComponent } from "@/lib/icons/pbbls-icon"

export type NavItemKey = "path" | "record" | "collections" | "souls" | "glyphs" | "lab"

export type NavItem = {
  key: NavItemKey
  href: string
  /** Drawn plain: the nav marks the current entry by colour and `aria-current`, never by an `active` twin (spec D11). */
  icon: PbblsIconComponent
}

/**
 * Nav slot configuration. Display labels are resolved via i18n
 * (`nav.<key>`) — see `useNavItems` for the localized form.
 */
export const NAV_ITEMS: ReadonlyArray<NavItem> = [
  { key: "path",        href: "/path",        icon: Path },
  { key: "record",      href: "/record",      icon: PlusCircle },
  { key: "collections", href: "/collections", icon: Collection },
  { key: "souls",       href: "/souls",       icon: People },
  { key: "glyphs",      href: "/glyphs",      icon: PebbleDottedGlyph },
  { key: "lab",         href: "/lab",         icon: Sparkle },
]
