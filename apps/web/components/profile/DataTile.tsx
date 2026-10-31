import type { ComponentType } from "react"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"

type IconType = ComponentType<{ className?: string; "aria-hidden"?: boolean }>

type DataTileProps = {
  value: number | null
  icon: IconType
  label: string
}

/**
 * A single value/icon/label counter — web port of the iOS `DataTile`. Big
 * number over a row of a small accent icon and a muted label. Used inside the
 * Profile Stats card (Days / Pebbles / Karma).
 */
export function DataTile({ value, icon: Icon, label }: DataTileProps) {
  return (
    <div className="flex flex-1 flex-col gap-1">
      <span className="text-[17px] font-semibold tabular-nums text-foreground">
        {value ?? "—"}
      </span>
      <span className="flex items-center gap-1 text-sm text-muted-foreground">
        <Icon className={cn(PBBLS_ICON_SIZE.inline, "text-primary")} aria-hidden />
        {label}
      </span>
    </div>
  )
}
