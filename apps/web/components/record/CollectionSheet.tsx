"use client"

import { Layers } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Collection } from "@/lib/types"
import { SelectableItem } from "@/components/ui/SelectableItem"
import { PickerSheet } from "@/components/ui/PickerSheet"
import { SheetTrigger } from "@/components/ui/sheet"
import { cn } from "@/lib/utils"

type CollectionSheetProps = {
  value: string[]
  onChange: (ids: string[]) => void
  collections: Collection[]
  variant?: "tile" | "chip"
}

/**
 * Multi-select collection picker presented in the shared drawer. Toggling a
 * row keeps the sheet open; the header `X` (or a backdrop tap) dismisses it.
 */
export function CollectionSheet({ value, onChange, collections, variant = "tile" }: CollectionSheetProps) {
  const t = useTranslations("record.collection")
  const selectedNames = collections
    .filter((c) => value.includes(c.id))
    .map((c) => c.name)

  const toggle = (id: string) => {
    onChange(
      value.includes(id) ? value.filter((c) => c !== id) : [...value, id],
    )
  }

  const trigger =
    variant === "chip" ? (
      <SheetTrigger
        className={cn(
          "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
          value.length > 0
            ? "border border-border bg-background text-foreground"
            : "border border-dashed border-muted-foreground/30 text-muted-foreground hover:border-muted-foreground/50",
        )}
        aria-label={value.length > 0 ? t("selectedAria", { names: selectedNames.join(", ") }) : t("pickAria")}
      >
        <Layers className="size-3.5" aria-hidden />
        {selectedNames.length > 0 ? selectedNames.join(", ") : t("label")}
      </SheetTrigger>
    ) : (
      <SheetTrigger
        className={cn(
          "relative flex aspect-square items-center justify-center rounded-xl transition-all duration-100 outline-none focus-visible:ring-2 focus-visible:ring-ring active:scale-95 overflow-hidden",
          value.length > 0
            ? "border border-border bg-muted/50"
            : "border border-dashed border-muted-foreground/30 hover:border-muted-foreground/50 hover:bg-muted/30",
        )}
        aria-label={value.length > 0 ? t("tileSelectedAria", { count: value.length }) : t("tilePickAria")}
      >
        {value.length > 0 ? (
          <span className="text-xs font-medium text-muted-foreground">
            {value.length}
          </span>
        ) : (
          <Layers className="size-5 text-muted-foreground/50" aria-hidden />
        )}
      </SheetTrigger>
    )

  return (
    <PickerSheet title={t("title")} closeLabel={t("close")} trigger={trigger}>
      {collections.length === 0 ? (
        <p className="px-2 py-4 text-center text-sm text-muted-foreground">
          {t("empty")}
        </p>
      ) : (
        <div className="flex flex-col gap-0.5">
          {collections.map((coll) => (
            <SelectableItem
              key={coll.id}
              selected={value.includes(coll.id)}
              onSelect={() => toggle(coll.id)}
              className="py-2"
            >
              {coll.name}
            </SelectableItem>
          ))}
        </div>
      )}
    </PickerSheet>
  )
}
