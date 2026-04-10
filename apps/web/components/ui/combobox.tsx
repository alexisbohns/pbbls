"use client"

import * as React from "react"
import { Combobox as ComboboxPrimitive } from "@base-ui/react/combobox"
import { Check, Search } from "lucide-react"

import { cn } from "@/lib/utils"

function Combobox<Value, Multiple extends boolean | undefined = false>({
  ...props
}: ComboboxPrimitive.Root.Props<Value, Multiple>) {
  return <ComboboxPrimitive.Root data-slot="combobox" {...props} />
}

function ComboboxInput({
  className,
  ...props
}: ComboboxPrimitive.Input.Props) {
  return (
    <div className="flex items-center gap-2 border-b border-border px-3 pb-2">
      <Search className="size-4 shrink-0 text-muted-foreground" aria-hidden />
      <ComboboxPrimitive.Input
        data-slot="combobox-input"
        className={cn(
          "h-8 w-full min-w-0 bg-transparent text-sm outline-none placeholder:text-muted-foreground",
          className,
        )}
        {...props}
      />
    </div>
  )
}

function ComboboxPortal({ ...props }: ComboboxPrimitive.Portal.Props) {
  return <ComboboxPrimitive.Portal data-slot="combobox-portal" {...props} />
}

function ComboboxContent({
  className,
  sideOffset = 6,
  align = "start",
  children,
  ...props
}: ComboboxPrimitive.Popup.Props & {
  sideOffset?: number
  align?: "start" | "center" | "end"
}) {
  return (
    <ComboboxPortal>
      <ComboboxPrimitive.Positioner
        data-slot="combobox-positioner"
        sideOffset={sideOffset}
        align={align}
      >
        <ComboboxPrimitive.Popup
          data-slot="combobox-content"
          className={cn(
            "z-50 w-[var(--anchor-width)] min-w-[12rem] overflow-hidden rounded-xl bg-popover text-popover-foreground ring-1 ring-foreground/10 shadow-lg outline-none",
            "origin-[var(--transform-origin)] transition-[transform,opacity] duration-100",
            "data-open:animate-in data-open:fade-in-0 data-open:zoom-in-95",
            "data-closed:animate-out data-closed:fade-out-0 data-closed:zoom-out-95",
            className,
          )}
          {...props}
        >
          <div className="p-2">
            {children}
          </div>
        </ComboboxPrimitive.Popup>
      </ComboboxPrimitive.Positioner>
    </ComboboxPortal>
  )
}

function ComboboxList({
  className,
  ...props
}: ComboboxPrimitive.List.Props) {
  return (
    <ComboboxPrimitive.List
      data-slot="combobox-list"
      className={cn("max-h-[200px] overflow-y-auto", className)}
      {...props}
    />
  )
}

function ComboboxItem({
  className,
  children,
  ...props
}: ComboboxPrimitive.Item.Props) {
  return (
    <ComboboxPrimitive.Item
      data-slot="combobox-item"
      className={cn(
        "flex cursor-default items-center gap-2 rounded-lg px-2 py-1.5 text-sm outline-none select-none",
        "data-highlighted:bg-muted data-highlighted:text-foreground",
        "data-selected:font-medium",
        className,
      )}
      {...props}
    >
      <ComboboxPrimitive.ItemIndicator
        data-slot="combobox-item-indicator"
        className="size-4 shrink-0"
        keepMounted
      >
        <Check className="size-4" />
      </ComboboxPrimitive.ItemIndicator>
      {children}
    </ComboboxPrimitive.Item>
  )
}

function ComboboxEmpty({
  className,
  ...props
}: ComboboxPrimitive.Empty.Props) {
  return (
    <ComboboxPrimitive.Empty
      data-slot="combobox-empty"
      className={cn("px-2 py-4 text-center text-sm text-muted-foreground", className)}
      {...props}
    />
  )
}

export {
  Combobox,
  ComboboxInput,
  ComboboxPortal,
  ComboboxContent,
  ComboboxList,
  ComboboxItem,
  ComboboxEmpty,
}
