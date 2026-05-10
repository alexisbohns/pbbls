"use client"

import type { ReactNode } from "react"
import { Layers, Image as ImageIcon, UserPlus } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Collection } from "@/lib/types"
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover"
import { SelectableItem } from "@/components/ui/SelectableItem"
import { cn } from "@/lib/utils"

type AddTileProps = {
  icon: ReactNode
  label: string
  ariaLabel: string
} & (
  | { kind: "button"; onClick: () => void }
  | { kind: "popoverTrigger" }
)

function AddTileButton(props: AddTileProps) {
  const className = cn(
    "flex items-center justify-center gap-2 rounded-2xl border border-dashed border-muted-foreground/40 bg-transparent px-3 py-3 transition-colors",
    "text-muted-foreground hover:bg-muted/40 hover:text-foreground active:scale-[0.98] outline-none focus-visible:ring-2 focus-visible:ring-ring",
  )
  const inner = (
    <>
      <span aria-hidden>{props.icon}</span>
      <span className="text-sm font-medium">{props.label}</span>
    </>
  )

  if (props.kind === "popoverTrigger") {
    return (
      <PopoverTrigger className={className} aria-label={props.ariaLabel}>
        {inner}
      </PopoverTrigger>
    )
  }

  return (
    <button
      type="button"
      onClick={props.onClick}
      className={className}
      aria-label={props.ariaLabel}
    >
      {inner}
    </button>
  )
}

type PebbleDetailAddToolbarProps = {
  showSoul: boolean
  showStack: boolean
  showPicture: boolean
  collections: Collection[]
  onAddCollection: (id: string) => void
  onOpenSoulsSheet: () => void
  onTriggerPhotoUpload: () => void
}

export function PebbleDetailAddToolbar({
  showSoul,
  showStack,
  showPicture,
  collections,
  onAddCollection,
  onOpenSoulsSheet,
  onTriggerPhotoUpload,
}: PebbleDetailAddToolbarProps) {
  const t = useTranslations("pebble.add")
  const tCollection = useTranslations("record.collection")

  if (!showSoul && !showStack && !showPicture) return null

  const count = [showSoul, showStack, showPicture].filter(Boolean).length
  const gridCols =
    count === 3 ? "grid-cols-3" : count === 2 ? "grid-cols-2" : "grid-cols-1"

  return (
    <div className={cn("grid gap-3", gridCols)}>
      {showSoul && (
        <AddTileButton
          kind="button"
          icon={<UserPlus className="size-4" />}
          label={t("soul")}
          ariaLabel={t("soulAria")}
          onClick={onOpenSoulsSheet}
        />
      )}
      {showStack && (
        <Popover>
          <AddTileButton
            kind="popoverTrigger"
            icon={<Layers className="size-4" />}
            label={t("stack")}
            ariaLabel={t("stackAria")}
          />
          <PopoverContent align="center" className="min-w-[200px]">
            {collections.length === 0 ? (
              <p className="px-2 py-4 text-center text-sm text-muted-foreground">
                {tCollection("empty")}
              </p>
            ) : (
              collections.map((coll) => (
                <SelectableItem
                  key={coll.id}
                  selected={false}
                  onSelect={() => onAddCollection(coll.id)}
                >
                  {coll.name}
                </SelectableItem>
              ))
            )}
          </PopoverContent>
        </Popover>
      )}
      {showPicture && (
        <AddTileButton
          kind="button"
          icon={<ImageIcon className="size-4" />}
          label={t("picture")}
          ariaLabel={t("pictureAria")}
          onClick={onTriggerPhotoUpload}
        />
      )}
    </div>
  )
}
