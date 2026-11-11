import Link from "next/link"
import type { ComponentType } from "react"
import type { Form } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"

type IconType = ComponentType<{ className?: string; "aria-hidden"?: boolean; form?: Form }>

type ShortcutTileProps = {
  href: string
  icon: IconType
  /** The form the icon draws, when it is a glyph and not its regular one. */
  iconForm?: Form
  label: string
}

/**
 * Icon-over-label tile on the branded accent surface — web port of the iOS
 * `ProfileShortcutTile` / `SurfaceTile`. The whole tile is the tap target.
 */
export function ShortcutTile({ href, icon: Icon, iconForm, label }: ShortcutTileProps) {
  return (
    <Link
      href={href}
      className="flex flex-1 flex-col items-center gap-2.5 rounded-2xl bg-accent px-3 py-4 text-center transition-colors hover:bg-accent/80"
    >
      <Icon className={cn(PBBLS_ICON_SIZE.row, "text-primary")} form={iconForm} aria-hidden />
      <span className="text-sm text-muted-foreground">{label}</span>
    </Link>
  )
}
