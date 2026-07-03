import Link from "next/link"
import type { ComponentType } from "react"

type IconType = ComponentType<{ className?: string; "aria-hidden"?: boolean }>

type ShortcutTileProps = {
  href: string
  icon: IconType
  label: string
}

/**
 * Icon-over-label tile on the branded accent surface — web port of the iOS
 * `ProfileShortcutTile` / `SurfaceTile`. The whole tile is the tap target.
 */
export function ShortcutTile({ href, icon: Icon, label }: ShortcutTileProps) {
  return (
    <Link
      href={href}
      className="flex flex-1 flex-col items-center gap-2.5 rounded-2xl bg-accent px-3 py-4 text-center transition-colors hover:bg-accent/80"
    >
      <Icon className="size-[18px] text-primary" aria-hidden />
      <span className="text-sm text-muted-foreground">{label}</span>
    </Link>
  )
}
